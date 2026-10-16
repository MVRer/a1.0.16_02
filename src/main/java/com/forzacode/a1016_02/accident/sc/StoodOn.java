package com.forzacode.a1016_02.accident.sc;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.AccidentData;
import com.forzacode.a1016_02.accident.AccidentInit;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.RouteBook;
import com.forzacode.a1016_02.accident.RouteSampler;
import com.forzacode.a1016_02.accident.Scan;
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
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The block you stood on (docs/SELF_CREATED.md, 15): card {@code sc_15} arms a player for {@code armMinutes} of their
 * play. While armed, every {@link #TICK_EVERY} ticks the route sampler's standing ring ({@code RouteSampler.Stand}: where
 * their feet stood on the ground, the last 10 s) is looked at, newest first, for a stand {@code delaySecondsMin} to
 * {@code delaySecondsMax} ago whose ground block may go: they are now at least {@code behindMin} from it and it is out
 * of every player's view; the cell is on one of their routes ({@code RouteBook}, at least {@code minPasses} passes; a
 * forced arm drops that) and not a bridge, ladder or water spot of theirs; the block is a plain natural full block
 * ({@code Scan.takeable}, no player placed it), not protected and outside every armed trap's ground; right under it a
 * natural cavity (no player dug it): lava or water within {@code cavityDepthMax}, or air onto a floor at most
 * {@code airDropMax} down (never a deeper drop). The player is alone ({@code SoundCues.alone}, 48), nobody else within
 * {@code nobodyWithin} of the block, no combat for 30 s. One {@code TraceService.remove} ({@value #CAUSE}); the arm is
 * spent, the day and the world's count are saved at once. At most once per in-game day per player, {@code maxPerWorld}
 * in a world. The common gates hold; nothing while the layer is off. Arms live in memory by UUID and end when the player
 * stops taking part.
 */
public final class StoodOn {
	public static final String CARD_ID = "sc_15";
	public static final String CAUSE = "accident:sc15";
	/** How often the armed players' rings are looked at (ticks). */
	public static final int TICK_EVERY = 10;
	/** The shared rule's radius: nobody else within this of the player. */
	public static final double ALONE_RADIUS = 48;

	/** An arm: until which of the player's own play ticks, whether it was forced (no passes needed), and the op told. */
	record Arm(long untilPlay, boolean forced, @Nullable UUID op) {
	}

	private static final Map<UUID, Arm> ARMS = new LinkedHashMap<>();

	/** Whether this block is out of every player's view. */
	@FunctionalInterface
	public interface View {
		boolean outOfView(ServerLevel level, BlockPos pos);
	}

	/**
	 * What the rule reads: the watch (footprints, sharers, combat), each player's standing ring and routes, every armed
	 * trap, who could be near ({@code listeners}: every player in a level, spectators included), the view, the trace
	 * service, the saved days and count, the numbers, the trap ground's slack, the server tick, the in-game day, each
	 * player's own play ticks and ticks since combat, the switch and the story's state.
	 */
	public record Env(PlayerWatch watch, Function<UUID, List<RouteSampler.Stand>> ring, Function<UUID, RouteBook> routes,
			Supplier<List<ArmedTrap>> traps, Function<ServerLevel, List<ServerPlayer>> listeners, View view, TraceService traces, StoodOnData memory,
			Sc15Config config, int zoneSlack, LongSupplier now, LongSupplier day, ToLongFunction<UUID> playTicks, ToLongFunction<ServerPlayer> sinceCombat,
			BooleanSupplier enabled, Supplier<HerobrineState> state) {
		public static Env live(MinecraftServer server) {
			AccidentData data = AccidentData.get(server);
			RouteSampler sampler = AccidentInit.planner().sampler();
			return new Env(Services.watch(), sampler::standing, data::routes, () -> {
				List<ArmedTrap> all = new ArrayList<>(data.traps().values());
				all.addAll(data.restoring());
				return all;
			}, ServerLevel::players, (level, pos) -> Services.traces().isOutOfView(level, pos), Services.traces(), StoodOnData.get(server),
					Sc15Config.get(), AccidentConfig.get().zoneSlack, server::getTickCount, () -> GameClock.day(server),
					uuid -> GameClock.playTicks(server, uuid), Services.watch()::ticksSinceCombat, SelfCreated::enabled, () -> HerobrineState.get(server));
		}
	}

	/** What is under the block: lava, water or a drop of air onto a floor, and how deep. */
	public enum Cavity { LAVA, WATER, AIR }

	/** The cavity under a ground block, or why there is none that may open. */
	public record Under(@Nullable Cavity cavity, int depth, @Nullable String why) {
	}

	private StoodOn() {
	}

	// --- arms ---

	/** True while this player is armed (the route sampler keeps their standing ring then). */
	public static boolean armed(UUID player) {
		return ARMS.containsKey(player);
	}

	/** Arms this player until {@code untilPlay} of their own clock; a forced arm needs no passes. */
	public static void arm(UUID player, long untilPlay, boolean forced) {
		ARMS.put(player, new Arm(untilPlay, forced, null));
	}

	/** Debug: the op who armed this player is told when it happens or runs out. */
	public static void tellOp(UUID player, @Nullable UUID op) {
		Arm arm = ARMS.get(player);
		if (arm != null) {
			ARMS.put(player, new Arm(arm.untilPlay(), arm.forced(), op));
		}
	}

	/** Play ticks of the player's own clock this arm has left, or -1. */
	public static long ticksLeft(UUID player, long playTicks) {
		Arm arm = ARMS.get(player);
		return arm == null ? -1 : Math.max(0, arm.untilPlay() - playTicks);
	}

	/** A player stopped taking part: their arm goes. */
	public static void disarm(UUID player) {
		ARMS.remove(player);
	}

	/** Server stop, and the switch going off: every arm goes. */
	public static void clear() {
		ARMS.clear();
	}

	// --- the rule ---

	/** Why nothing may happen for this player now, before any stand is looked at, or null. */
	public static @Nullable String gate(ServerPlayer player, Env env) {
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
		if (env.memory().count() >= env.config().maxPerWorld) {
			return "the world had its " + env.config().maxPerWorld;
		}
		if (env.memory().lastDay(player.getUUID()) == env.day().getAsLong()) {
			return "already today (day " + env.day().getAsLong() + ")";
		}
		if (!ScGates.noCombat(env.sinceCombat().applyAsLong(player))) {
			return "combat in the last 30 s";
		}
		if (!SoundCues.alone(player, ALONE_RADIUS, env.watch(), env.listeners().apply(player.level()))) {
			return "not alone";
		}
		return null;
	}

	/**
	 * What is right under this ground block: lava or water within {@code cavityDepthMax}, or air onto a floor at most
	 * {@code airDropMax} down; none when the block under it is solid, a player dug it, or the drop is deeper.
	 */
	public static Under under(ServerLevel level, PlayerWatch watch, BlockPos ground, Sc15Config config) {
		BlockPos first = ground.below();
		if (watch.wasDugByPlayer(level, first)) {
			return new Under(null, 0, "a player dug under it");
		}
		int reach = Math.max(config.cavityDepthMax, config.airDropMax + 1);
		BlockPos.MutableBlockPos cursor = first.mutable();
		for (int depth = 1; depth <= reach; depth++) {
			if (!level.isLoaded(cursor)) {
				return new Under(null, depth, "not loaded");
			}
			FluidState fluid = level.getFluidState(cursor);
			if (!fluid.isEmpty() && depth <= config.cavityDepthMax) {
				if (fluid.is(FluidTags.LAVA)) {
					return new Under(Cavity.LAVA, depth, null);
				}
				if (fluid.is(FluidTags.WATER)) {
					return new Under(Cavity.WATER, depth, null);
				}
			}
			BlockState state = level.getBlockState(cursor);
			if (!state.getCollisionShape(level, cursor).isEmpty() || !fluid.isEmpty()) {
				if (depth == 1) {
					return new Under(null, 0, "no cavity under it");
				}
				int drop = depth - 1;
				return drop <= config.airDropMax ? new Under(Cavity.AIR, drop, null) : new Under(null, drop, "a drop deeper than " + config.airDropMax);
			}
			cursor.move(0, -1, 0);
		}
		return new Under(null, reach, "a drop deeper than " + config.airDropMax);
	}

	/** Why this stand's ground block may not go now, or null. */
	public static @Nullable String check(ServerPlayer player, RouteSampler.Stand stand, boolean forced, Env env) {
		Sc15Config config = env.config();
		long age = env.now().getAsLong() - stand.tick();
		if (age < ModConfig.realTicks(config.delaySecondsMin) || age > ModConfig.realTicks(config.delaySecondsMax)) {
			return "not " + fmt(config.delaySecondsMin) + " to " + fmt(config.delaySecondsMax) + " s ago";
		}
		ServerLevel level = player.level();
		if (!stand.dimension().equals(level.dimension())) {
			return "another dimension";
		}
		BlockPos ground = stand.feet().below();
		if (!level.isLoaded(ground)) {
			return "not loaded";
		}
		if (player.position().distanceToSqr(Vec3.atCenterOf(ground)) < (double) config.behindMin * config.behindMin) {
			return "they are not " + config.behindMin + " past it yet";
		}
		RouteBook.Point point = env.routes().apply(player.getUUID()).get(level.dimension(), stand.feet());
		if (point == null || !forced && point.passes < config.minPasses) {
			return "not one of their routes (" + (point == null ? 0 : point.passes) + " passes)";
		}
		if (point.has(RouteBook.ON_PLACED) || point.has(RouteBook.LADDER) || point.has(RouteBook.WATER)) {
			return "a bridge, a ladder or water of theirs";
		}
		if (!Scan.takeable(level, ground) || env.watch().wasPlacedByPlayer(level, ground)) {
			return "not a natural full block";
		}
		if (Services.protectedAreas().isProtected(level.dimension(), ground)) {
			return "a protected area";
		}
		Vec3 centre = Vec3.atCenterOf(ground);
		for (ArmedTrap trap : env.traps().get()) {
			if (trap.dimension().equals(level.dimension()) && trap.zone(env.zoneSlack()).contains(centre)) {
				return "an armed trap's ground";
			}
		}
		Under under = under(level, env.watch(), ground, config);
		if (under.cavity() == null) {
			return under.why();
		}
		for (ServerPlayer other : env.listeners().apply(level)) {
			if (!other.getUUID().equals(player.getUUID()) && other.level() == level
					&& other.position().distanceToSqr(centre) < (double) config.nobodyWithin * config.nobodyWithin) {
				return "someone within " + config.nobodyWithin;
			}
		}
		if (!env.view().outOfView(level, ground)) {
			return "in view";
		}
		return null;
	}

	/**
	 * One look for this armed player: the newest stand in the window whose ground may go is removed. True if it went
	 * (the arm is spent, the day and the count are saved).
	 */
	public static boolean tryNow(ServerPlayer player, boolean forced, Env env) {
		if (gate(player, env) != null) {
			return false;
		}
		List<RouteSampler.Stand> ring = env.ring().apply(player.getUUID());
		for (int i = ring.size() - 1; i >= 0; i--) {
			RouteSampler.Stand stand = ring.get(i);
			if (check(player, stand, forced, env) != null) {
				continue;
			}
			BlockPos ground = stand.feet().below();
			if (env.traces().remove(player.level(), ground, CAUSE)) {
				env.memory().record(player.getUUID(), env.day().getAsLong());
				A1016_02.LOGGER.info("[a1016] {}: fired for {}", CARD_ID, player.getName().getString());
				return true;
			}
		}
		return false;
	}

	/** Why each stand of this player's ring may not go now, newest first ({@code /a1016 sc 15}). */
	public static List<String> describe(ServerPlayer player, Env env) {
		List<String> lines = new ArrayList<>();
		Arm arm = ARMS.get(player.getUUID());
		String gate = gate(player, env);
		lines.add("sc 15: " + player.getName().getString() + ": " + (arm == null ? "not armed" : String.format(java.util.Locale.ROOT,
				"armed, %.1f min left%s", Math.max(0, arm.untilPlay() - env.playTicks().applyAsLong(player.getUUID())) / 1200.0,
				arm.forced() ? " (forced: no passes needed)" : "")) + " | " + (gate == null ? "the gates are open" : gate)
				+ String.format(java.util.Locale.ROOT, " | this world %d of %d, last day %d", env.memory().count(), env.config().maxPerWorld,
						env.memory().lastDay(player.getUUID())));
		List<RouteSampler.Stand> ring = env.ring().apply(player.getUUID());
		for (int i = ring.size() - 1; i >= 0 && lines.size() < 9; i--) {
			RouteSampler.Stand stand = ring.get(i);
			String why = check(player, stand, arm != null && arm.forced(), env);
			lines.add(String.format(java.util.Locale.ROOT, "sc 15: %s, %.1f s ago: %s", stand.feet().below().toShortString(),
					(env.now().getAsLong() - stand.tick()) / 20.0, why == null ? "it may go" : why));
		}
		if (ring.isEmpty()) {
			lines.add("sc 15: no stands kept (only while armed, on the ground)");
		}
		return lines;
	}

	/** Every {@link #TICK_EVERY} ticks: each armed participant's look; arms that ran out go (the op told). */
	public static void tick(MinecraftServer server) {
		if (ARMS.isEmpty() || server.getTickCount() % TICK_EVERY != 0 || !SelfCreated.enabled()) {
			return;
		}
		Env env = Env.live(server);
		PlayerWatch watch = Services.watch();
		Iterator<Map.Entry<UUID, Arm>> it = ARMS.entrySet().iterator();
		List<Map.Entry<UUID, Arm>> looks = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, Arm> entry = it.next();
			if (env.playTicks().applyAsLong(entry.getKey()) >= entry.getValue().untilPlay()) {
				it.remove();
				tell(server, entry.getValue().op(), "[a1016] sc 15: the arm ran out");
			} else {
				looks.add(entry);
			}
		}
		for (Map.Entry<UUID, Arm> entry : looks) {
			Optional<ServerPlayer> player = watch.participant(server, entry.getKey()).filter(watch::isTargetable);
			if (player.isPresent() && tryNow(player.get(), entry.getValue().forced(), env)) {
				ARMS.remove(entry.getKey());
				tell(server, entry.getValue().op(), "[a1016] sc 15: fired for " + player.get().getName().getString());
			}
		}
	}

	private static void tell(MinecraftServer server, @Nullable UUID op, String line) {
		A1016_02.LOGGER.debug(line);
		if (op != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(op);
			if (player != null) {
				player.sendSystemMessage(Component.literal(line));
			}
		}
	}

	private static String fmt(double seconds) {
		return seconds == Math.rint(seconds) ? String.valueOf((long) seconds) : String.valueOf(seconds);
	}

	/** The moment of the card: the gates, no arm yet, and the player standing on one of their routes now. */
	public static boolean fits(ServerPlayer player, Env env) {
		if (gate(player, env) != null || armed(player.getUUID()) || !player.onGround()) {
			return false;
		}
		RouteBook.Point point = env.routes().apply(player.getUUID()).get(player.level().dimension(), player.blockPosition());
		return point != null && point.passes >= env.config().minPasses;
	}

	/**
	 * {@code sc_15}: MINOR, Proximity, DIG and PERSONAL, CARVER and STRIPPER, no fake. Its fire arms (D-091: it counts
	 * when it arms); a forced fire's arm needs no passes.
	 */
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
			return EnumSet.of(Habit.CARVER, Habit.STRIPPER);
		}

		@Override
		public Set<CardTag> tags() {
			return EnumSet.of(CardTag.DIG, CardTag.PERSONAL);
		}

		@Override
		public boolean hasFake() {
			return false;
		}

		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			return fits(player, Env.live(world.getServer()));
		}

		@Override
		public FireResult fire(FireContext ctx) {
			if (ctx.fake()) {
				return FireResult.SKIPPED;
			}
			Env env = Env.live(ctx.level().getServer());
			ServerPlayer player = ctx.player();
			if (gate(player, env) != null || armed(player.getUUID())) {
				return ctx.forced() ? FireResult.SKIPPED : FireResult.NO_SPOT;
			}
			arm(player.getUUID(), env.playTicks().applyAsLong(player.getUUID()) + ModConfig.realTicks(env.config().armMinutes * 60), ctx.forced());
			A1016_02.LOGGER.info("[a1016] {}: armed for {}", CARD_ID, player.getName().getString());
			return FireResult.FIRED;
		}
	}
}
