package com.forzacode.a1016_02.dig.sc;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.dig.DigData;
import com.forzacode.a1016_02.dig.Network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Not safe to rest (docs/SELF_CREATED.md, 12; D-082, D-089): card {@code sc_12} arms a player whose respawn point is
 * a bed in the overworld with the Under-you network's shaft done under it (its top cell 1 to {@code shaftBelowMax}
 * blocks under the bed, in its column) for {@code armNights} in-game nights. While armed, their next attempt to sleep in
 * that bed that vanilla would allow (it is night or thundering, the bed is near and free, they are not in creative, and
 * vanilla's own monster check found none) reads vanilla's monster list as not empty, so vanilla refuses with its own
 * {@code NOT_SAFE} and its own message ({@code dig.mixin.ServerPlayerSleepMixin}). The arm is spent there; the next
 * attempt is vanilla's. A real monster refuses as vanilla does and keeps the arm. The arm holds only while the shaft
 * still stands under that bed (a debug arm with {@code noShaft} skips it), and at most {@code maxPerWorld} refusals ever
 * happen in a world. For {@code sharedSeconds} after a refusal, every other player's attempt in a bed within
 * {@code sharedBedRadius} of it is refused the same way (one monster would refuse them all). The common gates hold;
 * nothing while the layer is off.
 */
public final class RestRefusal {
	public static final String CARD_ID = "sc_12";

	/** The refusals' windows: beds near a refusal refused for a while. In memory only (seconds). */
	public static final class Windows {
		/** A refusal: its bed, until which server tick beds near it are refused, and whose arm it spent. */
		public record Window(ResourceKey<Level> dimension, BlockPos bed, long untilTick, UUID armed) {
		}

		private final List<Window> windows = new ArrayList<>();

		synchronized void add(Window window) {
			windows.add(window);
		}

		/** A window still open at {@code now} for someone other than {@code player}, within {@code radius} of this bed. */
		synchronized @Nullable Window near(ResourceKey<Level> dimension, BlockPos bed, int radius, long now, UUID player) {
			windows.removeIf(w -> w.untilTick() <= now);
			for (Window window : windows) {
				if (window.dimension().equals(dimension) && !window.armed().equals(player) && window.bed().distSqr(bed) <= (double) radius * radius) {
					return window;
				}
			}
			return null;
		}

		public synchronized List<Window> all() {
			return List.copyOf(windows);
		}

		public synchronized void clear() {
			windows.clear();
		}
	}

	/** The live windows. */
	static final Windows LIVE_WINDOWS = new Windows();
	/** Tests: their own env for their own players (random UUIDs), so the mixin's real path reads their world. */
	private static final Map<UUID, Env> TEST_ENVS = new ConcurrentHashMap<>();

	/**
	 * What the rule reads: the dig data, each player's ticks since combat, the numbers, the day clock, the server tick, the
	 * switch, the story's state and the refusals' windows.
	 */
	public record Env(DigData data, ToLongFunction<ServerPlayer> sinceCombat, Sc12Config config, LongSupplier dayTicks, LongSupplier now,
			BooleanSupplier enabled, Supplier<HerobrineState> state, Windows windows) {
		public static Env live(MinecraftServer server) {
			PlayerWatch watch = Services.watch();
			return new Env(DigData.get(server), watch::ticksSinceCombat, Sc12Config.get(), () -> GameClock.dayTicks(server), server::getTickCount,
					SelfCreated::enabled, () -> HerobrineState.get(server), LIVE_WINDOWS);
		}
	}

	private RestRefusal() {
	}

	/** Tests: this player's attempts read this env (null: the live one again). */
	public static void useFor(UUID player, @Nullable Env env) {
		if (env == null) {
			TEST_ENVS.remove(player);
		} else {
			TEST_ENVS.put(player, env);
		}
	}

	private static Env envFor(ServerPlayer player) {
		Env env = TEST_ENVS.get(player.getUUID());
		return env != null ? env : Env.live(player.level().getServer());
	}

	/**
	 * The network whose shaft is done under this bed, its top cell 1 to {@code shaftBelowMax} under it and in its column
	 * (the shaft's 2x2 covers both halves of the bed), or empty.
	 */
	public static Optional<Network> shaftUnder(DigData data, GlobalPos bed, Sc12Config config) {
		for (Network net : data.networks()) {
			if (!net.dimension().equals(bed.dimension()) || !net.shaftDone() || !net.isBed(bed.pos())) {
				continue;
			}
			Optional<BlockPos> top = net.shaftTop();
			if (top.isEmpty()) {
				continue;
			}
			int below = bed.pos().getY() - top.get().getY();
			if (below >= 1 && below <= config.shaftBelowMax && Math.abs(top.get().getX() - bed.pos().getX()) <= 1
					&& Math.abs(top.get().getZ() - bed.pos().getZ()) <= 1) {
				return Optional.of(net);
			}
		}
		return Optional.empty();
	}

	/** The player's respawn bed in the overworld, or why there is none. */
	static Optional<GlobalPos> respawnBed(ServerPlayer player) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn == null) {
			return Optional.empty();
		}
		GlobalPos pos = respawn.respawnData().globalPos();
		if (!pos.dimension().equals(Level.OVERWORLD)) {
			return Optional.empty();
		}
		ServerLevel level = player.level().getServer().getLevel(Level.OVERWORLD);
		if (level == null || !level.isLoaded(pos.pos()) || !(level.getBlockState(pos.pos()).getBlock() instanceof AbstractBedBlock)) {
			return Optional.empty();
		}
		return Optional.of(pos);
	}

	/**
	 * Why {@code sc_12} does not fit for this player now, or null: the switch, the story, the overworld, a respawn bed
	 * in the overworld, no arm of theirs yet, the world's refusals left, no combat for 30 s, the shaft under that bed
	 * (unless {@code noShaft}, debug).
	 */
	public static @Nullable String why(ServerPlayer player, Env env, boolean noShaft) {
		if (!env.enabled().getAsBoolean()) {
			return "the layer is switched off";
		}
		String story = ScGates.storyBlock(env.state().get());
		if (story != null) {
			return story;
		}
		if (!player.level().dimension().equals(Level.OVERWORLD)) {
			return "not in the overworld";
		}
		Optional<GlobalPos> bed = respawnBed(player);
		if (bed.isEmpty()) {
			return "their respawn point is not a bed in the overworld";
		}
		DigScMemory sc = env.data().sc();
		if (sc.restArm(player.getUUID()).isPresent()) {
			return "already armed";
		}
		if (sc.restRefusals() >= env.config().maxPerWorld) {
			return "the world had its " + env.config().maxPerWorld;
		}
		if (!ScGates.noCombat(env.sinceCombat().applyAsLong(player))) {
			return "combat in the last 30 s";
		}
		if (!noShaft && shaftUnder(env.data(), bed.get(), env.config()).isEmpty()) {
			return "no finished shaft under " + bed.get().pos().toShortString();
		}
		return null;
	}

	/** Arms the player for their respawn bed, for {@code armNights}; null if it does not fit (and why, from {@link #why}). */
	public static DigScMemory.@Nullable RestArm arm(ServerPlayer player, Env env, boolean noShaft) {
		if (why(player, env, noShaft) != null) {
			return null;
		}
		GlobalPos bed = respawnBed(player).orElseThrow();
		DigScMemory.RestArm arm = new DigScMemory.RestArm(bed, env.dayTicks().getAsLong() + env.config().armNights * 24000L, noShaft);
		env.data().sc().setRestArm(player.getUUID(), arm);
		env.data().setDirty();
		A1016_02.LOGGER.info("[a1016] {}: armed for {}", CARD_ID, player.getName().getString());
		return arm;
	}

	/**
	 * One bed, by either half: the same cell, or {@code at} is a half of a bed whose other half is {@code armed} (the arm
	 * keeps the respawn point's half, which need not be the half vanilla's attempt names).
	 */
	static boolean sameBed(BlockGetter level, BlockPos armed, BlockPos at) {
		if (armed.equals(at)) {
			return true;
		}
		BlockState state = level.getBlockState(at);
		return state.getBlock() instanceof AbstractBedBlock && state.hasProperty(AbstractBedBlock.PART) && state.hasProperty(HorizontalDirectionalBlock.FACING)
				&& at.relative(AbstractBedBlock.getConnectedDirection(state)).equals(armed);
	}

	/** The mixin's question, once vanilla found no monster near this bed: true refuses as vanilla would (NOT_SAFE). */
	public static boolean refuse(ServerPlayer player, BlockPos bed) {
		try {
			return refuse(player, bed, envFor(player));
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] {}: the check failed; vanilla decides", CARD_ID, e);
			return false;
		}
	}

	/** {@link #refuse(ServerPlayer, BlockPos)} with these seams. */
	public static boolean refuse(ServerPlayer player, BlockPos bed, Env env) {
		if (!env.enabled().getAsBoolean() || ScGates.storyBlock(env.state().get()) != null) {
			return false;
		}
		ResourceKey<Level> dimension = player.level().dimension();
		long now = env.now().getAsLong();
		UUID uuid = player.getUUID();
		Sc12Config config = env.config();
		DigScMemory sc = env.data().sc();
		Optional<DigScMemory.RestArm> arm = sc.restArm(uuid);
		if (arm.isEmpty() || !arm.get().bed().dimension().equals(dimension) || !sameBed(player.level(), arm.get().bed().pos(), bed)) {
			Windows.Window window = env.windows().near(dimension, bed, config.sharedBedRadius, now, uuid);
			if (window != null) {
				A1016_02.LOGGER.info("[a1016] {}: fired (shared) for {}", CARD_ID, player.getName().getString());
				return true;
			}
			return false;
		}
		DigScMemory.RestArm a = arm.get();
		if (env.dayTicks().getAsLong() >= a.untilDayTicks() || sc.restRefusals() >= config.maxPerWorld
				|| !a.noShaft() && shaftUnder(env.data(), a.bed(), config).isEmpty()) {
			// Ran out, the world had its refusals, or the shaft no longer stands under that bed (D-089): vanilla's answer.
			sc.dropRestArm(uuid);
			env.data().setDirty();
			return false;
		}
		if (!ScGates.noCombat(env.sinceCombat().applyAsLong(player))) {
			return false;
		}
		sc.dropRestArm(uuid);
		sc.countRestRefusal();
		env.data().setDirty();
		env.windows().add(new Windows.Window(dimension, bed.immutable(), now + ModConfig.realTicks(config.sharedSeconds), uuid));
		A1016_02.LOGGER.info("[a1016] {}: fired for {}", CARD_ID, player.getName().getString());
		return true;
	}

	/** Drops every arm that ran out (the live data; cheap). */
	public static void expire(MinecraftServer server) {
		DigData data = DigData.get(server);
		long day = GameClock.dayTicks(server);
		List<UUID> done = new ArrayList<>();
		data.sc().restArms().forEach((player, arm) -> {
			if (day >= arm.untilDayTicks()) {
				done.add(player);
			}
		});
		if (!done.isEmpty()) {
			done.forEach(data.sc()::dropRestArm);
			data.setDirty();
		}
	}

	/** The switch went off: no arm and no window is left, so no bed lies any more (idempotent, no chunk loads). */
	public static void revert(MinecraftServer server) {
		revert(DigData.get(server), LIVE_WINDOWS);
	}

	/** {@link #revert(MinecraftServer)} over this data and these windows. */
	public static void revert(DigData data, Windows windows) {
		windows.clear();
		if (!data.sc().restArms().isEmpty()) {
			List.copyOf(data.sc().restArms().keySet()).forEach(data.sc()::dropRestArm);
			data.setDirty();
		}
	}

	/** Server stop: the windows are memory. */
	public static void clear() {
		LIVE_WINDOWS.clear();
	}

	/** {@code sc_12}: MINOR, Proximity, DIG, CARVER and WATCHER, no fake. Its fire arms (D-091: it counts when it arms). */
	public static final class Card implements EventCard {
		@Override
		public String id() {
			return CARD_ID;
		}

		@Override
		public Tier tier() {
			return Tier.MINOR;
		}

		@Override
		public Stage earliestStage() {
			return Stage.PROXIMITY;
		}

		@Override
		public Set<Habit> habits() {
			return EnumSet.of(Habit.CARVER, Habit.WATCHER);
		}

		@Override
		public Set<CardTag> tags() {
			return EnumSet.of(CardTag.DIG);
		}

		@Override
		public boolean hasFake() {
			return false;
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return why(player, Env.live(world.getServer()), false) == null;
		}

		@Override
		public FireResult fire(FireContext ctx) {
			if (ctx.fake()) {
				return FireResult.SKIPPED;
			}
			return arm(ctx.player(), Env.live(ctx.level().getServer()), false) != null ? FireResult.FIRED : FireResult.NO_SPOT;
		}
	}
}
