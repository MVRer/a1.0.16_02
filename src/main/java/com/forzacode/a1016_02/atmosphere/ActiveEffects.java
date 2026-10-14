package com.forzacode.a1016_02.atmosphere;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Sends the transient client effects (fog surge, silence, compass drift) and remembers them per player (by UUID), so
 * a player who relogs while one is running gets the rest of theirs back, and one who never got it gets nothing.
 *
 * <p>Fog surges and silences are his, so they are shared moments (D-060): they go to the target and everyone sharing
 * the moment with them ({@code ClientEffects.fogSurgeShared/silenceShared}, within {@code sharedMomentRadius} in the
 * same dimension), at the same moment, and each player who got one is remembered. Compass drift is about one
 * player's own compass, so it stays theirs alone.
 *
 * <p>Silence and compass drift are kept in {@link AtmosphereData} on the world play clock, so they also carry on
 * after a singleplayer world reload; a fog surge (seconds long) only in memory. Persistent effects (music off, dusk
 * fog) are core's {@code Sync}. The re-send happens once per connection, when the player starts taking part
 * ({@code HerobrineEvents.PARTICIPATION}), never again when they only leave spectator mode (their client still has
 * it). Server thread only.
 */
public final class ActiveEffects {
	private record Surge(long start, ClientEffects.FogSurge effect) {
		long holdLeft(long now) {
			return effect.rampTicks() + effect.holdTicks() - (now - start);
		}
	}

	/**
	 * What a re-sync sends one player's client: the rest of their fog surge, silence and compass drift (null or 0 for
	 * what they have none of).
	 */
	public record Resync(ClientEffects.@Nullable FogSurge surge, int silenceLeft, int silenceFade, @Nullable CompassDriftPayload compass) {
		public static final Resync NONE = new Resync(null, 0, 0, null);

		public boolean any() {
			return surge != null || silenceLeft > 0 || compass != null;
		}
	}

	private static final Map<UUID, Surge> SURGES = new HashMap<>();
	/** Players whose client connected and has not had its re-sync yet (its effects start empty on every connection). */
	private static final Set<UUID> FRESH = new HashSet<>();

	private ActiveEffects() {
	}

	/**
	 * A fog surge for {@code target} and everyone sharing the moment with them (D-060), remembered for each of them.
	 * Nothing, and an empty list, once the world is quiet for good ({@link Gates#quietForGood}).
	 *
	 * @return who it went to, target first
	 */
	public static List<ServerPlayer> fogSurge(ServerPlayer target, float strength, int rampTicks, int holdTicks, int fadeTicks) {
		return fogSurge(target, strength, rampTicks, holdTicks, fadeTicks, t -> ClientEffects.fogSurgeShared(t, strength, rampTicks, holdTicks, fadeTicks));
	}

	/**
	 * {@link #fogSurge(ServerPlayer, float, int, int, int)} with {@code share} sending it and returning who got it:
	 * core's {@code fogSurgeShared}, or for a game test a {@code PlayerWatch}'s {@code sharingWith} over its own players
	 * (who have no client to send to).
	 */
	static List<ServerPlayer> fogSurge(ServerPlayer target, float strength, int rampTicks, int holdTicks, int fadeTicks,
			Function<ServerPlayer, List<ServerPlayer>> share) {
		MinecraftServer server = target.level().getServer();
		if (Gates.quietForGood(server)) {
			SURGES.remove(target.getUUID());
			return List.of();
		}
		List<ServerPlayer> got = share.apply(target);
		long now = server.getTickCount();
		SURGES.values().removeIf(s -> s.holdLeft(now) <= 0);
		ClientEffects.FogSurge effect = new ClientEffects.FogSurge(strength, rampTicks, holdTicks, fadeTicks);
		got.forEach(player -> SURGES.put(player.getUUID(), new Surge(now, effect)));
		return got;
	}

	/**
	 * A silence for {@code target} and everyone sharing the moment with them (D-060), remembered for each of them.
	 *
	 * @return who it went to, target first
	 */
	public static List<ServerPlayer> silence(ServerPlayer target, int ticks, int fadeTicks) {
		return silence(target, ticks, fadeTicks, t -> ClientEffects.silenceShared(t, ticks, fadeTicks));
	}

	/** {@link #silence(ServerPlayer, int, int)} with {@code share} sending it and returning who got it (see {@link #fogSurge}). */
	static List<ServerPlayer> silence(ServerPlayer target, int ticks, int fadeTicks, Function<ServerPlayer, List<ServerPlayer>> share) {
		List<ServerPlayer> got = share.apply(target);
		MinecraftServer server = target.level().getServer();
		AtmosphereData data = AtmosphereData.get(server);
		long now = GameClock.playTicks(server);
		got.forEach(player -> data.setSilence(player.getUUID(), now, ticks, fadeTicks));
		return got;
	}

	/**
	 * Sends a compass drift to this player only (their own compass) and remembers it. False, and nothing sent, once
	 * the world is quiet for good.
	 */
	public static boolean compassDrift(ServerPlayer player, int x, int z, int ticks, int settleBlocks) {
		MinecraftServer server = player.level().getServer();
		if (Gates.quietForGood(server)) {
			return false;
		}
		CompassDriftPayload.send(player, new CompassDriftPayload(x, z, Math.max(0, ticks), settleBlocks));
		AtmosphereData.get(server).setCompass(player.getUUID(), GameClock.playTicks(server), Math.max(0, ticks), x, z, settleBlocks);
		return true;
	}

	public static boolean compassActive(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return AtmosphereData.get(server).transientOf(player.getUUID()).map(t -> t.compassLeft(GameClock.playTicks(server)) > 0).orElse(false);
	}

	/** The fog surge hold still running for this player, in server ticks (0 if none). For status and tests. */
	public static long surgeLeft(MinecraftServer server, UUID player) {
		Surge surge = SURGES.get(player);
		return surge == null ? 0 : Math.max(0, surge.holdLeft(server.getTickCount()));
	}

	/**
	 * What of this player's own is still running, for a client that starts empty: the rest of their fog surge (eased
	 * back in over at most 10 ticks), silence and compass drift. Nothing for someone who never got one, and no surge or
	 * drift once the world is quiet for good. Sends nothing.
	 */
	static Resync pending(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		boolean quiet = Gates.quietForGood(server);
		ClientEffects.FogSurge rest = null;
		Surge surge = SURGES.get(player.getUUID());
		if (surge != null && !quiet) {
			ClientEffects.FogSurge s = surge.effect();
			long holdLeft = surge.holdLeft(server.getTickCount());
			if (holdLeft > 0) {
				rest = new ClientEffects.FogSurge(s.strength(), Math.min(10, s.rampTicks()), (int) holdLeft, s.fadeTicks());
			}
		}
		Optional<AtmosphereData.Transient> active = AtmosphereData.get(server).transientOf(player.getUUID());
		if (active.isEmpty()) {
			return rest == null ? Resync.NONE : new Resync(rest, 0, 0, null);
		}
		AtmosphereData.Transient t = active.get();
		long now = GameClock.playTicks(server);
		int silenceLeft = (int) t.silenceLeft(now);
		long compassLeft = quiet ? 0 : t.compassLeft(now);
		CompassDriftPayload compass = compassLeft > 0 ? new CompassDriftPayload(t.compassX(), t.compassZ(), (int) compassLeft, t.compassSettle()) : null;
		return new Resync(rest, silenceLeft, t.silenceFade(), compass);
	}

	/**
	 * Re-sends to this player's client (to them alone: only their client lost it) whatever of theirs is still running
	 * ({@link #pending}).
	 */
	static Resync resync(ServerPlayer player) {
		Resync r = pending(player);
		SURGES.remove(player.getUUID());
		if (r.surge() != null) {
			ClientEffects.FogSurge s = r.surge();
			ClientEffects.fogSurge(player, s.strength(), s.rampTicks(), s.holdTicks(), s.fadeTicks());
			SURGES.put(player.getUUID(), new Surge(player.level().getServer().getTickCount(), s));
		}
		if (r.silenceLeft() > 0) {
			ClientEffects.silence(player, r.silenceLeft(), r.silenceFade());
		}
		if (r.compass() != null) {
			CompassDriftPayload.send(player, r.compass());
		}
		return r;
	}

	/** Fabric's JOIN: this client starts with no effects; the re-sync waits until the player takes part. */
	static void onConnect(UUID player) {
		FRESH.add(player);
	}

	/** Fabric's DISCONNECT: the connection is gone; what is still running stays remembered for a relog. */
	static void onDisconnect(UUID player) {
		FRESH.remove(player);
	}

	/**
	 * {@code HerobrineEvents.PARTICIPATION} true: the first time a player takes part on this connection, their client
	 * gets back whatever of theirs is still running (after core's {@code Sync}). Leaving spectator mode later sends
	 * nothing again: the client kept it.
	 */
	static void onParticipating(MinecraftServer server, UUID player) {
		if (FRESH.remove(player)) {
			Services.watch().participant(server, player).ifPresent(ActiveEffects::resync);
		}
	}

	static void clear() {
		SURGES.clear();
		FRESH.clear();
	}
}
