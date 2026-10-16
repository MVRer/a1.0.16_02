package com.forzacode.a1016_02.dig.sc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
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
import com.forzacode.a1016_02.dig.DigData;
import com.forzacode.a1016_02.dig.DigTicker;
import com.forzacode.a1016_02.dig.Tunnels;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The way back (docs/SELF_CREATED.md, 13): card {@code sc_13} closes the passage behind a player deep in a natural cave
 * they lit on the way in, with two blocks of the cave's own stone moved out of its wall, which leaves a hollow there.
 *
 * <p>The trail: every {@code trailSampleTicks} each underground participant's feet cell is kept when it moved a block
 * or more, at most {@code trailLength} (oldest out); a jump of more than {@link #TRAIL_BREAK} blocks or another
 * dimension starts it again. The point is a cell on it (between two samples too) at least {@code pastBlocks} of path
 * before the player now, in natural cave (no cell of it dug by a player), a passage exactly 1 wide and 2 high there and
 * one cell further in ({@code deep}, the way the player went), with a wall beside that next cell of two blocks of the
 * same natural stone ({@code #base_stone_overworld}, not placed by a player) with solid blocks behind, above, below and
 * further in, so the hollow they leave opens only onto the passage. A torch or lantern a player placed lies along the
 * trail between the point and the player, and another within {@code nextTorchWithin} of the point on the way out. The
 * player is underground, carries a pickaxe, has had no combat for 30 s and is alone (no sharer, nobody within the
 * shared moment radius, spectators included); nobody at all, the player too, is within {@code nobodyWithin} of the
 * point; the four cells are out of every player's view and outside protected areas. One {@code TraceService.batch}
 * ({@value #CAUSE}) of two moves: the wall's two blocks into the passage's two cells. At most {@code maxPerWorld}.
 * The common gates hold; nothing while the layer is off.
 */
public final class WayBack {
	public static final String CARD_ID = "sc_13";
	public static final String CAUSE = "dig:sc13";
	/** A debug arm looks for a point for this long (10 minutes). */
	public static final long ARM_TICKS = 12_000;
	/** Two samples farther apart than this are not one walk: the trail starts again. */
	public static final double TRAIL_BREAK = 12;
	/** How far from a later sample a torch counts as on the trail. */
	static final int TRAIL_TORCH_REACH = 4;

	/** One sample of a trail: the feet cell and the server tick. */
	public record Sample(BlockPos feet, long tick) {
	}

	private static final class Trail {
		final ResourceKey<Level> dimension;
		final ArrayDeque<Sample> samples = new ArrayDeque<>();

		Trail(ResourceKey<Level> dimension) {
			this.dimension = dimension;
		}
	}

	/** A debug arm: until which server tick, who asked (told what happens), and the last reason it did not fit. */
	private static final class Arm {
		final long until;
		final @Nullable UUID op;
		String last = "nothing looked at yet";

		Arm(long until, @Nullable UUID op) {
			this.until = until;
			this.op = op;
		}
	}

	private static final Map<UUID, Trail> TRAILS = new HashMap<>();
	private static final Map<UUID, Arm> ARMS = new LinkedHashMap<>();

	/** Whether these cells are out of every player's view. */
	@FunctionalInterface
	public interface View {
		boolean outOfView(ServerLevel level, Collection<BlockPos> cells);
	}

	/**
	 * What the rule reads: the watch (footprints, sharers, combat), the dig data (the count), the numbers, who could be
	 * near ({@code listeners}: every player in a level, spectators included), the view, the trace service, what counts
	 * as underground, the switch and the story's state.
	 */
	public record Env(PlayerWatch watch, DigData data, Sc13Config config, Function<ServerLevel, List<ServerPlayer>> listeners, View view,
			TraceService traces, Predicate<ServerPlayer> underground, ToLongFunction<ServerPlayer> sinceCombat, BooleanSupplier enabled,
			Supplier<HerobrineState> state) {
		public static Env live(MinecraftServer server) {
			PlayerWatch watch = Services.watch();
			return new Env(watch, DigData.get(server), Sc13Config.get(), ServerLevel::players, (level, cells) -> Services.traces().isOutOfView(level, cells),
					Services.traces(), player -> DigTicker.underground(player.level(), player.blockPosition()), watch::ticksSinceCombat,
					SelfCreated::enabled, () -> HerobrineState.get(server));
		}
	}

	/**
	 * Where it closes: the passage's cell {@code at} (and the one above), the way further in ({@code deep}), the wall's
	 * side, and the path blocks walked since.
	 */
	public record Point(BlockPos at, Direction deep, Direction side, double past) {
		/** The lower of the wall's two blocks (beside the next cell in). */
		public BlockPos from() {
			return at.relative(deep).relative(side);
		}

		/** The passage's two cells, then the wall's two blocks. */
		public List<BlockPos> cells() {
			return List.of(at, at.above(), from(), from().above());
		}
	}

	/** The answer: the point (nearest first along the path), or a gate, and why the other candidates failed (counts). */
	public record Plan(@Nullable Point point, @Nullable String gate, Map<String, Integer> refusals, int candidates) {
		public boolean fits() {
			return point != null;
		}

		/** One line: the gate, or the point, or the reasons most often first. */
		public String summary() {
			if (gate != null) {
				return gate;
			}
			String why = refusals.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).map(e -> e.getKey() + " x" + e.getValue())
					.reduce((a, b) -> a + ", " + b).orElse("no candidate");
			if (point != null) {
				return String.format(Locale.ROOT, "point %s (%.0f path blocks back, further in %s, wall %s); %d candidates, the others: %s",
						point.at().toShortString(), point.past(), point.deep().getName(), point.side().getName(), candidates, why);
			}
			return String.format(Locale.ROOT, "no point among %d candidates: %s", candidates, why);
		}
	}

	private WayBack() {
	}

	// --- the trail ---

	/** Every {@code trailSampleTicks}: each underground participant's feet, when they moved a block or more. */
	public static void sample(List<ServerPlayer> players, long now, Sc13Config config) {
		for (ServerPlayer player : players) {
			ServerLevel level = player.level();
			BlockPos feet = player.blockPosition();
			if (level.isLoaded(feet) && DigTicker.underground(level, feet)) {
				add(player.getUUID(), level.dimension(), feet, now, config);
			}
		}
	}

	/** One sample of this player's trail (the trail starts again in another dimension or after a jump). */
	public static void add(UUID player, ResourceKey<Level> dimension, BlockPos feet, long now, Sc13Config config) {
		Trail trail = TRAILS.get(player);
		if (trail != null && trail.dimension.equals(dimension) && !trail.samples.isEmpty()) {
			BlockPos last = trail.samples.getLast().feet();
			if (last.equals(feet)) {
				return;
			}
			if (Math.sqrt(last.distSqr(feet)) > TRAIL_BREAK) {
				trail = null;
			}
		} else if (trail != null && !trail.dimension.equals(dimension)) {
			trail = null;
		}
		if (trail == null) {
			trail = new Trail(dimension);
			TRAILS.put(player, trail);
		}
		trail.samples.addLast(new Sample(feet.immutable(), now));
		while (trail.samples.size() > Math.max(2, config.trailLength)) {
			trail.samples.removeFirst();
		}
	}

	/** Tests and debug: this player's trail is these feet cells, oldest first. */
	public static void samples(UUID player, ResourceKey<Level> dimension, List<BlockPos> feet) {
		Trail trail = new Trail(dimension);
		long tick = 0;
		for (BlockPos pos : feet) {
			trail.samples.addLast(new Sample(pos.immutable(), tick++));
		}
		TRAILS.put(player, trail);
	}

	/** This player's trail in this dimension, oldest first (empty if none). */
	public static List<Sample> trail(UUID player, ResourceKey<Level> dimension) {
		Trail trail = TRAILS.get(player);
		return trail == null || !trail.dimension.equals(dimension) ? List.of() : List.copyOf(trail.samples);
	}

	/** A player stopped taking part: their trail and arm go. */
	public static void forget(UUID player) {
		TRAILS.remove(player);
		ARMS.remove(player);
	}

	/** Server stop, and the switch going off: every trail and arm goes. */
	public static void clear() {
		TRAILS.clear();
		ARMS.clear();
	}

	// --- the rule ---

	/** Why nothing may close for this player now, before any point is looked at, or null. */
	static @Nullable String gate(ServerPlayer player, Env env) {
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
		if (env.data().sc().wayBacks() >= env.config().maxPerWorld) {
			return "the world had its " + env.config().maxPerWorld;
		}
		if (!env.underground().test(player)) {
			return "not underground";
		}
		if (!ScGates.noCombat(env.sinceCombat().applyAsLong(player))) {
			return "combat in the last 30 s";
		}
		if (!player.getInventory().contains(ItemTags.PICKAXES)) {
			return "no pickaxe on them";
		}
		if (!SoundCues.alone(player, ModConfig.pacing().sharedMomentRadius, env.watch(), env.listeners().apply(player.level()))) {
			return "not alone";
		}
		return null;
	}

	/** The point that closes now for this player, nearest first along their path back, or the gate and every refusal. */
	public static Plan plan(ServerPlayer player, Env env) {
		String gate = gate(player, env);
		if (gate != null) {
			return new Plan(null, gate, Map.of(), 0);
		}
		ServerLevel level = player.level();
		List<BlockPos> path = new ArrayList<>();
		trail(player.getUUID(), level.dimension()).forEach(sample -> path.add(sample.feet()));
		BlockPos here = player.blockPosition();
		if (path.isEmpty() || !path.getLast().equals(here)) {
			path.add(here);
		}
		Map<String, Integer> refusals = new LinkedHashMap<>();
		if (path.size() < 2) {
			return new Plan(null, "no trail yet", refusals, 0);
		}
		Sc13Config config = env.config();
		int n = path.size();
		double[] after = new double[n];
		for (int i = n - 2; i >= 0; i--) {
			after[i] = after[i + 1] + Math.sqrt(path.get(i).distSqr(path.get(i + 1)));
		}
		Map<BlockPos, Integer> torches = null;
		Set<BlockPos> seen = new HashSet<>();
		int candidates = 0;
		for (int i = n - 2; i >= 0; i--) {
			BlockPos a = path.get(i);
			BlockPos b = path.get(i + 1);
			Direction deep = axis(a, b);
			if (deep == null) {
				continue;
			}
			double length = Math.sqrt(a.distSqr(b));
			int steps = Math.max(1, (int) Math.ceil(length * 2));
			for (int k = steps - 1; k >= 0; k--) {
				double t = (double) k / steps;
				BlockPos cell = BlockPos.containing(a.getX() + 0.5 + (b.getX() - a.getX()) * t, a.getY() + 0.5 + (b.getY() - a.getY()) * t,
						a.getZ() + 0.5 + (b.getZ() - a.getZ()) * t);
				double past = after[i + 1] + Math.sqrt(cell.distSqr(b));
				if (past < config.pastBlocks || !seen.add(cell)) {
					continue;
				}
				candidates++;
				for (Direction side : List.of(deep.getClockWise(), deep.getCounterClockWise())) {
					String why = shape(level, env.watch(), cell, deep, side);
					if (why == null) {
						Point point = new Point(cell, deep, side, past);
						if (torches == null) {
							torches = trailTorches(level, env.watch(), path);
						}
						why = around(player, point, i, torches, env);
						if (why == null) {
							return new Plan(point, null, refusals, candidates);
						}
					}
					refusals.merge(why, 1, Integer::sum);
				}
			}
		}
		return new Plan(null, null, refusals, candidates);
	}

	/** The way from {@code a} to {@code b} along the axis it moves most on, horizontally; null if it does not move. */
	static @Nullable Direction axis(BlockPos a, BlockPos b) {
		int dx = b.getX() - a.getX();
		int dz = b.getZ() - a.getZ();
		if (dx == 0 && dz == 0) {
			return null;
		}
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/**
	 * Why the passage at {@code at} cannot close with this wall, or null: open, natural and exactly 1 wide and 2 high
	 * there and one cell further in; the wall beside that next cell two blocks of the same natural stone with solid
	 * blocks around them, so the hollow opens only onto the passage.
	 */
	public static @Nullable String shape(ServerLevel level, PlayerWatch watch, BlockPos at, Direction deep, Direction side) {
		if (!Tunnels.chunksLoaded(level, at, 4)) {
			return "not loaded";
		}
		String here = passage(level, watch, at, side);
		if (here != null) {
			return here;
		}
		BlockPos next = at.relative(deep);
		String beyond = passage(level, watch, next, side);
		if (beyond != null) {
			return beyond + " one further in";
		}
		BlockPos from = next.relative(side);
		BlockPos fromUp = from.above();
		BlockState lower = level.getBlockState(from);
		if (!natural(level, watch, from, lower) || !natural(level, watch, fromUp, level.getBlockState(fromUp))
				|| lower.getBlock() != level.getBlockState(fromUp).getBlock()) {
			return "no natural stone pair in the wall";
		}
		if (!solid(level, from.relative(side)) || !solid(level, fromUp.relative(side)) || !solid(level, from.below()) || !solid(level, fromUp.above())
				|| !solid(level, from.relative(deep)) || !solid(level, fromUp.relative(deep))) {
			return "the hollow would open";
		}
		return null;
	}

	/** Why this cell is not a natural 1-wide, 2-high passage across {@code side}, or null. */
	private static @Nullable String passage(ServerLevel level, PlayerWatch watch, BlockPos at, Direction side) {
		BlockPos up = at.above();
		if (!level.getBlockState(at).isAir() || !level.getBlockState(up).isAir()) {
			return "not open";
		}
		if (watch.wasDugByPlayer(level, at) || watch.wasDugByPlayer(level, up)) {
			return "a dug tunnel";
		}
		if (!solid(level, at.below()) || !solid(level, up.above())) {
			return "not 2 high";
		}
		for (Direction wall : List.of(side, side.getOpposite())) {
			if (!solid(level, at.relative(wall)) || !solid(level, up.relative(wall))) {
				return "wider than 1";
			}
		}
		return null;
	}

	/** A full, dry block. */
	static boolean solid(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isCollisionShapeFullBlock(level, pos) && state.getFluidState().isEmpty();
	}

	/** The cave's own stone: {@code #base_stone_overworld}, full, no block entity, nothing that falls, no player placed it. */
	static boolean natural(ServerLevel level, PlayerWatch watch, BlockPos pos, BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) && state.isCollisionShapeFullBlock(level, pos) && !state.hasBlockEntity()
				&& !(state.getBlock() instanceof FallingBlock) && !watch.wasPlacedByPlayer(level, pos);
	}

	/** A light a player leaves in a cave: a torch or wall torch (not redstone) or a lantern, of any kind. */
	public static boolean light(BlockState state) {
		return Tunnels.isTorch(state) || state.getBlock() instanceof LanternBlock;
	}

	/** Every light a player placed near the trail, with the last sample index it is near. */
	private static Map<BlockPos, Integer> trailTorches(ServerLevel level, PlayerWatch watch, List<BlockPos> path) {
		Map<BlockPos, Integer> torches = new HashMap<>();
		for (int k = 0; k < path.size(); k++) {
			BlockPos sample = path.get(k);
			if (!level.isLoaded(sample)) {
				continue;
			}
			for (BlockPos torch : watch.placedNear(level, sample, TRAIL_TORCH_REACH, WayBack::light)) {
				torches.merge(torch, k, Math::max);
			}
		}
		return torches;
	}

	/**
	 * Why this point that has the shape may not close now, or null: a protected area, someone within
	 * {@code nobodyWithin}, no torch of theirs along the trail further in, none within {@code nextTorchWithin} on the
	 * way out, the cells in view.
	 */
	private static @Nullable String around(ServerPlayer player, Point point, int segment, Map<BlockPos, Integer> torches, Env env) {
		ServerLevel level = player.level();
		for (BlockPos cell : point.cells()) {
			if (Services.protectedAreas().isProtected(level.dimension(), cell)) {
				return "a protected area";
			}
		}
		double within = env.config().nobodyWithin;
		for (ServerPlayer other : env.listeners().apply(level)) {
			if (other.level() == level && other.position().distanceToSqr(Vec3.atCenterOf(point.at())) < within * within) {
				return other.getUUID().equals(player.getUUID()) ? "they are within " + env.config().nobodyWithin : "someone within " + env.config().nobodyWithin;
			}
		}
		boolean between = false;
		for (Map.Entry<BlockPos, Integer> torch : torches.entrySet()) {
			if (torch.getValue() > segment && ahead(torch.getKey(), point) > 0) {
				between = true;
				break;
			}
		}
		if (!between) {
			return "no torch of theirs on the way further in";
		}
		boolean beyond = false;
		int reach = env.config().nextTorchWithin;
		for (BlockPos torch : env.watch().placedNear(level, point.at(), reach, WayBack::light)) {
			if (torch.distSqr(point.at()) <= (double) reach * reach && ahead(torch, point) < 0 && !point.cells().contains(torch)) {
				beyond = true;
				break;
			}
		}
		if (!beyond) {
			return "no torch of theirs within " + reach + " on the way out";
		}
		if (!env.view().outOfView(level, point.cells())) {
			return "in view";
		}
		return null;
	}

	/** How far {@code pos} lies further in than the point, along its way in. */
	private static int ahead(BlockPos pos, Point point) {
		BlockPos d = pos.subtract(point.at());
		return d.getX() * point.deep().getStepX() + d.getZ() * point.deep().getStepZ();
	}

	/** Closes the way back at the plan's point: one batch of two moves. {@code NO_SPOT} when nothing fits or the batch is refused. */
	public static FireResult fire(ServerPlayer player, Env env) {
		Plan plan = plan(player, env);
		if (!plan.fits()) {
			return FireResult.NO_SPOT;
		}
		Point point = plan.point();
		boolean done = env.traces().batch(player.level(), CAUSE).move(point.from(), point.at()).move(point.from().above(), point.at().above()).commit();
		if (!done) {
			return FireResult.NO_SPOT;
		}
		env.data().sc().countWayBack();
		env.data().setDirty();
		A1016_02.LOGGER.info("[a1016] {}: fired for {}", CARD_ID, player.getName().getString());
		return FireResult.FIRED;
	}

	// --- the debug arm ---

	/** Arms this player for {@link #ARM_TICKS}: the first point that fits fires the card through the director (forced). */
	public static void arm(ServerPlayer player, long now, @Nullable UUID op) {
		ARMS.put(player.getUUID(), new Arm(now + ARM_TICKS, op));
	}

	/** True while this player has a debug arm. */
	public static boolean armed(UUID player) {
		return ARMS.containsKey(player);
	}

	/** Server ticks this player's debug arm has left at server tick {@code now}, or -1 if they have none. */
	public static long armTicksLeft(UUID player, long now) {
		Arm arm = ARMS.get(player);
		return arm == null ? -1L : Math.max(0L, arm.until - now);
	}

	/** Every 20 ticks: each armed participant's plan; the first that fits fires (forced), and the op is told. */
	public static void tickArms(MinecraftServer server, long now) {
		if (ARMS.isEmpty()) {
			return;
		}
		List<Runnable> fires = new ArrayList<>();
		Iterator<Map.Entry<UUID, Arm>> it = ARMS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Arm> entry = it.next();
			Arm arm = entry.getValue();
			Optional<ServerPlayer> player = Services.watch().participant(server, entry.getKey()).filter(Services.watch()::isTargetable);
			if (now >= arm.until) {
				it.remove();
				tell(server, arm.op, "[a1016] sc 13: the arm ran out: " + arm.last);
				continue;
			}
			if (player.isEmpty()) {
				continue;
			}
			Plan plan = plan(player.get(), Env.live(server));
			if (!plan.fits()) {
				arm.last = plan.summary();
				continue;
			}
			it.remove();
			ServerPlayer target = player.get();
			fires.add(() -> {
				FireResult result = Services.director().fire(server, target, CARD_ID, false);
				tell(server, arm.op, "[a1016] sc 13: fired for " + target.getName().getString() + " -> " + result
						+ (result == FireResult.FIRED ? ": the way back is closed at " + plan.point().at().toShortString() : ""));
			});
		}
		fires.forEach(Runnable::run);
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

	/** {@code sc_13}: MAJOR, Proximity, DIG and PERSONAL, CARVER and STRIPPER, no fake. */
	public static final class Card implements EventCard {
		@Override
		public String id() {
			return CARD_ID;
		}

		@Override
		public Tier tier() {
			return Tier.MAJOR;
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
			return plan(player, Env.live(world.getServer())).fits();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			return ctx.fake() ? FireResult.SKIPPED : WayBack.fire(ctx.player(), Env.live(ctx.level().getServer()));
		}
	}
}
