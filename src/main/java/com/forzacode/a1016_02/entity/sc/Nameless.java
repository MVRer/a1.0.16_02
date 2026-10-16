package com.forzacode.a1016_02.entity.sc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.entity.ScreenshotRules;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * 02 on the server (D-082, D-091): {@code sc_02} arms the target's client ({@link NamelessPayload}) for
 * {@code armMinutes} of their play, or until their client has left names undrawn for {@code hideSecondsTotal}
 * ({@link NamelessSpentPayload}); the client keeps its own rules ({@code entity.client.NamelessClient}). Arms live in
 * memory by UUID, one per player, and end when they run out, on PARTICIPATION false, when the player turns spectator,
 * once the story holds it ({@link ScGates#storyRefusal}) and when the layer is switched off. Nothing the friend sees
 * changes. Server thread only. {@link #LIVE} is the game's; tests make their own with their own {@link Hooks}.
 */
public final class Nameless {
	public static final String CARD_ID = "sc_02";
	/** The arms are looked at this often (ticks). */
	public static final int TICK_EVERY = 20;

	/** What the arms read and send, so tests can stand in for the live world. */
	public interface Hooks {
		/** That player's own play clock ({@link GameClock#playTicks(MinecraftServer, UUID)}). */
		long playTicks(MinecraftServer server, UUID player);

		Optional<ServerPlayer> online(MinecraftServer server, UUID player);

		/** Sends the arm to that player's client; false if it cannot take it. */
		boolean send(ServerPlayer player, NamelessPayload payload);

		/** The switch and the story flags ({@link ScGates#storyRefusal}). */
		Optional<String> storyRefusal(MinecraftServer server);

		/** The common gates for the target ({@link ScGates#refusal(ServerPlayer)}). */
		Optional<String> commonRefusal(ServerPlayer target);

		/** Every other participant (no spectator) in the target's level. */
		List<ServerPlayer> others(ServerPlayer target);

		/** The raw sky light at the target's eyes. */
		int skyLight(ServerPlayer target);

		/** The dusk fog and fog spell at the target ({@link Nameless#fogAmount}). */
		double fogAmount(ServerPlayer target);

		/** Where the target's own drawn fog lets a band start ({@link NamelessReachPayload#fresh}), NaN without a fresh report. */
		double reach(ServerPlayer target);

		/** True if anyone shares the target's moment now ({@code PlayerWatch.sharingWith}: someone within the shared radius). */
		boolean shared(ServerPlayer target);
	}

	static final Hooks LIVE_HOOKS = new Hooks() {
		@Override
		public long playTicks(MinecraftServer server, UUID player) {
			return GameClock.playTicks(server, player);
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(server.getPlayerList().getPlayer(player));
		}

		@Override
		public boolean send(ServerPlayer player, NamelessPayload payload) {
			if (!ServerPlayNetworking.canSend(player, NamelessPayload.TYPE)) {
				return false;
			}
			ServerPlayNetworking.send(player, payload);
			return true;
		}

		@Override
		public Optional<String> storyRefusal(MinecraftServer server) {
			return ScGates.storyRefusal(server);
		}

		@Override
		public Optional<String> commonRefusal(ServerPlayer target) {
			return ScGates.refusal(target);
		}

		@Override
		public List<ServerPlayer> others(ServerPlayer target) {
			List<ServerPlayer> others = new ArrayList<>();
			for (ServerPlayer player : Services.watch().participants(target.level().getServer())) {
				if (!player.getUUID().equals(target.getUUID()) && player.level() == target.level() && !player.isSpectator()) {
					others.add(player);
				}
			}
			return others;
		}

		@Override
		public int skyLight(ServerPlayer target) {
			return target.level().getBrightness(LightLayer.SKY, BlockPos.containing(target.getEyePosition()));
		}

		@Override
		public double fogAmount(ServerPlayer target) {
			return Nameless.fogAmount(target);
		}

		@Override
		public double reach(ServerPlayer target) {
			return NamelessReachPayload.fresh(target);
		}

		@Override
		public boolean shared(ServerPlayer target) {
			return Services.watch().sharingWith(target).size() > 1;
		}
	};

	/** The game's arms. */
	public static final Nameless LIVE = new Nameless(LIVE_HOOKS);

	/** One player's arm: the play tick it runs out at, the hide time it gave the client, and whether a debug command made it. */
	public record Arm(long untilPlayTicks, int hideTicks, boolean forced) {
	}

	private final Hooks hooks;
	private final Map<UUID, Arm> arms = new LinkedHashMap<>();

	public Nameless(Hooks hooks) {
		this.hooks = hooks;
	}

	/**
	 * Why {@code sc_02} does not fit this target now, or empty: {@link ScGates}, then {@link NamelessRules#fit}, then nobody
	 * sharing the moment (whoever does would feel it and still see the name), then the band the target's own drawn fog
	 * leaves ({@link NamelessRules#bandRefusal}, from their client's report).
	 */
	public Optional<String> fit(ServerPlayer target) {
		Optional<String> common = hooks.commonRefusal(target);
		if (common.isPresent()) {
			return common;
		}
		List<Double> distances = new ArrayList<>();
		for (ServerPlayer other : hooks.others(target)) {
			distances.add(other.position().distanceTo(target.position()));
		}
		NamelessConfig config = NamelessConfig.get();
		Optional<String> rules = NamelessRules.fit(distances, hooks.skyLight(target), hooks.fogAmount(target), config);
		if (rules.isPresent()) {
			return rules;
		}
		if (hooks.shared(target)) {
			return Optional.of("someone shares the moment");
		}
		return NamelessRules.bandRefusal(hooks.reach(target), config);
	}

	/**
	 * Arms the player's client for {@code ticks} of their play (the configured hide time): {@link FireResult#FIRED}, or
	 * {@link FireResult#SKIPPED} without arming while the story holds it, the layer is off, or their client cannot take
	 * it. A new arm replaces theirs.
	 */
	public FireResult arm(ServerPlayer player, long ticks, boolean forced) {
		MinecraftServer server = player.level().getServer();
		if (hooks.storyRefusal(server).isPresent() || ticks <= 0) {
			return FireResult.SKIPPED;
		}
		int hide = NamelessConfig.get().hideTicks();
		NamelessPayload payload = new NamelessPayload((int) Math.min(Integer.MAX_VALUE, ticks), hide);
		if (!payload.armed() || !hooks.send(player, payload)) {
			return FireResult.SKIPPED;
		}
		arms.put(player.getUUID(), new Arm(hooks.playTicks(server, player.getUUID()) + ticks, hide, forced));
		A1016_02.LOGGER.info("[a1016] {}: {} armed for {} min{}", CARD_ID, player.getName().getString(), Math.round(ticks / 1200.0),
				forced ? " (debug)" : "");
		return FireResult.FIRED;
	}

	/** The client says it has hidden names for its whole time ({@link NamelessSpentPayload}): that arm ends. */
	public void spent(ServerPlayer player) {
		if (arms.remove(player.getUUID()) != null) {
			A1016_02.LOGGER.info("[a1016] {}: {}'s arm is spent", CARD_ID, player.getName().getString());
		}
	}

	public boolean isArmed(UUID player) {
		return arms.containsKey(player);
	}

	/** Everyone armed now, in the order they were armed. */
	public List<UUID> armed() {
		return List.copyOf(arms.keySet());
	}

	public Optional<Arm> arm(UUID player) {
		return Optional.ofNullable(arms.get(player));
	}

	/** Play ticks left on that player's arm, or -1 if they are not armed. */
	public long ticksLeft(MinecraftServer server, UUID player) {
		Arm arm = arms.get(player);
		return arm == null ? -1L : Math.max(0L, arm.untilPlayTicks() - hooks.playTicks(server, player));
	}

	/** Ends that player's arm; their client is told if they are online. */
	public void disarm(MinecraftServer server, UUID player) {
		if (arms.remove(player) != null) {
			hooks.online(server, player).ifPresent(p -> hooks.send(p, NamelessPayload.OFF));
		}
	}

	/** Ends every arm (the story holds it, the layer is off, or debug). Idempotent. */
	public void disarmAll(MinecraftServer server) {
		for (UUID player : new ArrayList<>(arms.keySet())) {
			disarm(server, player);
		}
	}

	/** PARTICIPATION: a player who stops taking part loses their arm. */
	public void onParticipation(MinecraftServer server, UUID player, boolean participating) {
		if (!participating) {
			disarm(server, player);
		}
	}

	/** Every server tick; looks at the arms every {@link #TICK_EVERY} ticks. */
	public void tick(MinecraftServer server) {
		if (!arms.isEmpty() && server.getTickCount() % TICK_EVERY == 0) {
			check(server);
		}
	}

	/** Looks at every arm now: all end once the story holds it; one whose player is gone, spectating or out of time ends. */
	public void check(MinecraftServer server) {
		if (arms.isEmpty()) {
			return;
		}
		if (hooks.storyRefusal(server).isPresent()) {
			disarmAll(server);
			return;
		}
		for (UUID uuid : new ArrayList<>(arms.keySet())) {
			Arm arm = arms.get(uuid);
			Optional<ServerPlayer> player = hooks.online(server, uuid);
			if (player.isEmpty() || player.get().isSpectator() || hooks.playTicks(server, uuid) >= arm.untilPlayTicks()) {
				disarm(server, uuid);
			}
		}
	}

	/** Forgets every arm without telling anyone (server stop). */
	public void clear() {
		arms.clear();
	}

	/** One line for debug: who is armed and for how long. */
	public String line(MinecraftServer server) {
		if (arms.isEmpty()) {
			return "02: not armed";
		}
		StringBuilder line = new StringBuilder("02: armed");
		for (UUID uuid : arms.keySet()) {
			String name = hooks.online(server, uuid).map(p -> p.getName().getString()).orElse(uuid.toString());
			line.append(String.format(Locale.ROOT, " %s %.1f min", name, ticksLeft(server, uuid) / 1200.0));
		}
		return line.toString();
	}

	/**
	 * Live: the dusk fog and fog spell at this player now, as the screenshot's fog flag reads them
	 * ({@link ScreenshotRules#fogAmount}: the world's dusk fog level and spell on their level's clock, the spell weighted by
	 * the sky light at their eyes); 0 where time stands still.
	 */
	public static double fogAmount(ServerPlayer player) {
		Level level = player.level();
		if (level.dimensionType().hasFixedTime()) {
			return 0.0;
		}
		HerobrineState.Effects effects = HerobrineState.get(level.getServer()).effects();
		long clock = level.getDefaultClockTime();
		int sky = level.getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition()));
		return ScreenshotRules.fogAmount(effects.duskFogLevel(), clock, FogLimits.shape(), FogLimits.spellAmount(effects.fogSpell(), clock), sky);
	}
}
