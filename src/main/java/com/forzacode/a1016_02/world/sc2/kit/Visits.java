package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Who is inside which place, looked at once a second for every participant through the remains' chunk-grid index (O(1)
 * a player): {@code REMAINS_ENTERED} and {@code REMAINS_LEFT} (one leave for every enter), {@code visited} and
 * {@code trespassedAt} kept in {@link Sc2Data}, and {@code ENTERED_TUNNEL} once per visit that came in through the open
 * way in (the player stood within {@value #MOUTH_REACH} of its mouth at most {@value #MOUTH_WINDOW_TICKS} ticks before).
 * A spectator is never inside (and a participant who becomes one leaves). While the layer is switched off nothing is
 * looked at and every visit is forgotten without events. Session only (nothing kept across a restart but the data).
 * Server thread.
 */
public final class Visits {
	/** How often every participant is looked at (ticks). */
	public static final int EVERY_TICKS = 20;
	/** How near the mouth a player must have stood (blocks) for the way-in rule. */
	public static final int MOUTH_REACH = 3;
	/** And at most this long before they were inside (ticks). */
	public static final int MOUTH_WINDOW_TICKS = 600;

	/** What a visit does: the events and the attention trigger (a test's own records them). */
	public interface Sink {
		void entered(ServerLevel level, Remains.Place place, ServerPlayer player);

		void left(ServerLevel level, Remains.Place place, ServerPlayer player);

		void cameInThroughTheWay(MinecraftServer server, Remains.Place place, ServerPlayer player);
	}

	/** Everyone online, spectators included (the live one: the server's player list). */
	@FunctionalInterface
	public interface People {
		List<ServerPlayer> online(MinecraftServer server);
	}

	/** The live sink: the events, and {@code Attention.trigger(ENTERED_TUNNEL)}. */
	public static final Sink EVENTS = new Sink() {
		@Override
		public void entered(ServerLevel level, Remains.Place place, ServerPlayer player) {
			HerobrineEvents.REMAINS_ENTERED.invoker().onVisit(level, place, player);
		}

		@Override
		public void left(ServerLevel level, Remains.Place place, ServerPlayer player) {
			HerobrineEvents.REMAINS_LEFT.invoker().onVisit(level, place, player);
		}

		@Override
		public void cameInThroughTheWay(MinecraftServer server, Remains.Place place, ServerPlayer player) {
			Attention.trigger(server, AttentionTrigger.ENTERED_TUNNEL);
		}
	};

	/** The live visits. */
	public static final Visits LIVE = new Visits(RemainsImpl.LIVE, EVENTS, server -> List.copyOf(server.getPlayerList().getPlayers()),
			SelfCreated::enabled);

	private record Mouth(Remains.Place place, long tick) {
	}

	private final RemainsImpl remains;
	private final Sink sink;
	private final People people;
	private final BooleanSupplier enabled;
	private final Map<UUID, Remains.Place> inside = new HashMap<>();
	private final Map<UUID, Mouth> mouths = new HashMap<>();

	public Visits(RemainsImpl remains, Sink sink, People people, BooleanSupplier enabled) {
		this.remains = remains;
		this.sink = sink;
		this.people = people;
		this.enabled = enabled;
	}

	/** The live tick: a look every {@value #EVERY_TICKS} ticks. */
	public void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY_TICKS == 3) {
			scan(server, server.getTickCount(), server.overworld().getGameTime());
		}
	}

	/**
	 * One look at everyone: who entered, who left. {@code tick} is the server's tick count (for the way-in window),
	 * {@code gameTime} the overworld's game time (kept as the trespass time).
	 */
	public void scan(MinecraftServer server, long tick, long gameTime) {
		if (!enabled.getAsBoolean()) {
			inside.clear();
			mouths.clear();
			return;
		}
		Sc2Data data = remains.data();
		Map<UUID, ServerPlayer> seen = new HashMap<>();
		for (ServerPlayer player : people.online(server)) {
			if (player.isSpectator()) {
				continue;
			}
			seen.put(player.getUUID(), player);
			ServerLevel level = player.level();
			UUID uuid = player.getUUID();
			remains.atEntrance(level, player.blockPosition(), MOUTH_REACH).ifPresent(place -> mouths.put(uuid, new Mouth(place, tick)));
			Optional<Remains.Place> now = remains.containing(level, player.blockPosition());
			Remains.Place before = inside.get(uuid);
			Remains.Place after = now.orElse(null);
			if (before != null && !before.equals(after)) {
				inside.remove(uuid);
				left(server, before, player);
			}
			if (after != null) {
				if (!after.equals(before)) {
					inside.put(uuid, after);
					data.visit(uuid, after);
					sink.entered(level, after, player);
					Mouth mouth = mouths.get(uuid);
					if (mouth != null && mouth.place().equals(after) && tick - mouth.tick() <= MOUTH_WINDOW_TICKS) {
						sink.cameInThroughTheWay(server, after, player);
					}
					mouths.remove(uuid);
				}
				data.trespass(uuid, gameTime);
			}
		}
		// Gone from the participants without a stop (a spectator now is still online: their leave; offline: forgotten).
		Iterator<Map.Entry<UUID, Remains.Place>> it = inside.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Remains.Place> entry = it.next();
			if (seen.containsKey(entry.getKey())) {
				continue;
			}
			it.remove();
			ServerPlayer player = find(server, entry.getKey());
			if (player != null) {
				left(server, entry.getValue(), player);
			}
		}
		mouths.keySet().removeIf(uuid -> !seen.containsKey(uuid) || tick - mouths.get(uuid).tick() > MOUTH_WINDOW_TICKS);
	}

	/**
	 * A participant stops taking part ({@code PARTICIPATION} false: still listed): their visit ends now. Call before they
	 * are gone from the list.
	 */
	public void stopped(MinecraftServer server, UUID uuid) {
		mouths.remove(uuid);
		Remains.Place place = inside.remove(uuid);
		if (place == null) {
			return;
		}
		ServerPlayer player = find(server, uuid);
		if (player != null) {
			left(server, place, player);
		}
	}

	private void left(MinecraftServer server, Remains.Place place, ServerPlayer player) {
		ServerLevel level = server.getLevel(place.dimension());
		if (level != null) {
			sink.left(level, place, player);
		}
	}

	private @Nullable ServerPlayer find(MinecraftServer server, UUID uuid) {
		for (ServerPlayer player : people.online(server)) {
			if (player.getUUID().equals(uuid)) {
				return player;
			}
		}
		return null;
	}

	/** The place this player is inside now (as of the last look), if any. */
	public Optional<Remains.Place> placeOf(UUID player) {
		return Optional.ofNullable(inside.get(player));
	}

	/** Everyone inside this place now (as of the last look), online and taking part, looked up by UUID. */
	public List<ServerPlayer> inside(MinecraftServer server, Remains.Place place) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : people.online(server)) {
			if (!player.isSpectator() && place.equals(inside.get(player.getUUID()))) {
				out.add(player);
			}
		}
		return out;
	}

	/** Forgets every visit without events (switch off, server stop). */
	public void clear() {
		inside.clear();
		mouths.clear();
	}
}
