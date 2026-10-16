package com.forzacode.a1016_02.world.sc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceBatch;
import com.google.gson.annotations.SerializedName;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * 09 ({@code docs/SELF_CREATED.md}): a line of footprints comes in from the open to the target's door. The base needs a
 * hand door (one that opens by hand) a player placed within {@value #DOOR_RADIUS} of it; the path ends on the ground
 * cell outside it. It starts {@code lengthMin} to {@code lengthMax} blocks out, away from the house (the door's own
 * outward side, turned at most 50 degrees toward the most open ground), and comes straight in with a wander of one
 * block every {@value #WANDER_EVERY}: a print every {@code stride} cells, alternating between the line and the block
 * beside it ({@link Prints}; stone, sand and gravel take none and the stride goes on). From Proximity, at
 * {@code circleChance}, the prints also go once round the house 1 to 2 blocks from its walls and back out parallel to
 * the way in, 2 blocks to its side. Fewer than {@code minPrints}: no spot. One batch ({@value #CAUSE}), every cell
 * out of view, nobody within {@code nobodyWithin} of a print (the target too, unless asleep or inside the house), the
 * target {@code awayDistance} from the door, asleep or inside the house; per base {@code minDaysApart} days apart, at most {@code maxPerBase}, saved in the tick it
 * happens. The fake: three to five prints in a curve near an animal by the base, like a cow's (not counted for the
 * base). Server thread.
 */
public final class Tracks {
	public static final String ID = "sc_09";
	public static final String CAUSE = "world:sc09";
	public static final String SECTION = "sc09";
	/** The door is a hand door a player placed this close to the base. */
	public static final int DOOR_RADIUS = 8;
	/** The line wanders by one block at most once every this many cells (never in the last ones before the door). */
	public static final int WANDER_EVERY = 4;
	/** The wander never takes the line more than this far off its straight course. */
	private static final int WANDER_MAX = 2;
	/** The house is the covered floor found from inside the door, at most this far from the base. */
	private static final int HOUSE_REACH = 8;
	private static final int HOUSE_MAX_CELLS = 400;
	/** A roof is a solid block at most this far above. */
	private static final int ROOF_REACH = 6;
	/** The fake looks for an animal this close to the base. */
	private static final int FAKE_ANIMAL_RADIUS = 32;
	/** Turns tried away from the door's outward side, degrees. */
	private static final int[] TURNS = {0, 25, -25, 50, -50};

	/** {@code sections.sc09}. Version 2. */
	public static final class Config implements ModConfig.Versioned {
		public static final int CURRENT = 2;

		public int version = 0;
		@SerializedName(value = "a", alternate = "lengthMin")
		public int lengthMin = 14;
		@SerializedName(value = "b", alternate = "lengthMax")
		public int lengthMax = 28;
		@SerializedName(value = "c", alternate = "stride")
		public int stride = 2;
		@SerializedName(value = "d", alternate = "minPrints")
		public int minPrints = 5;
		@SerializedName(value = "e", alternate = "circleChance")
		public double circleChance = 0.4;
		@SerializedName(value = "f", alternate = "nobodyWithin")
		public double nobodyWithin = 16.0;
		@SerializedName(value = "g", alternate = "awayDistance")
		public double awayDistance = 32.0;
		@SerializedName(value = "h", alternate = "minDaysApart")
		public int minDaysApart = 2;
		@SerializedName(value = "i", alternate = "maxPerBase")
		public int maxPerBase = 4;

		@Override
		public boolean upgrade() {
			if (version >= CURRENT) {
				return false;
			}
			// 1 to 2 (D-080): the keys are opaque letters; a value under an old key is read into its field (its
			// @SerializedName alternate) and only the new key is written back.
			version = CURRENT;
			return true;
		}
	}

	/**
	 * A hand door by the base.
	 *
	 * @param lower   its lower half
	 * @param outside the cell outside it (the side open to the sky, else the one farther from the base)
	 * @param inside  the cell inside it
	 */
	public record Door(BlockPos lower, BlockPos outside, BlockPos inside) {
		/** From the inside cell to the outside one. */
		public Direction out() {
			return Direction.getApproximateNearest(outside.getX() - inside.getX(), 0.0, outside.getZ() - inside.getZ());
		}
	}

	/**
	 * A plan of prints.
	 *
	 * @param prints  the ground cells that take a print, in order (the way in from far out, then round, then out)
	 * @param edits   every edit, in batch order
	 * @param circled the prints go round the house
	 * @param reason  why there is no plan (empty prints), or a short summary
	 */
	public record Plan(List<BlockPos> prints, List<Prints.Edit> edits, boolean circled, String reason) {
		static Plan none(String reason) {
			return new Plan(List.of(), List.of(), false, reason);
		}

		public boolean ok() {
			return !prints.isEmpty();
		}
	}

	/** What a fire did. */
	public record Outcome(FireResult result, Plan plan, String reason) {
	}

	private Tracks() {
	}

	public static Config config() {
		return ModConfig.section(SECTION, Config.class, Config::new);
	}

	// --- the door and the house ---

	/** True if a hand door's lower half stands here. */
	public static boolean isHandDoor(BlockState state) {
		return state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
	}

	/** The hand door a player placed nearest the base (within {@value #DOOR_RADIUS}), if any. */
	public static Optional<Door> door(ScEnv env, ServerLevel level, BlockPos base) {
		return env.watch().placedNear(level, base, DOOR_RADIUS, Tracks::isHandDoor).stream()
				.filter(p -> ScEnv.loaded(level, List.of(p.north(), p.south(), p.east(), p.west())))
				.min(Comparator.comparingDouble(p -> p.distSqr(base)))
				.map(p -> door(level, p, base));
	}

	/** The door at {@code lower}: its outside is the side open to the sky, else the one farther from the base. */
	public static Door door(BlockGetter level, BlockPos lower, BlockPos base) {
		Direction facing = level.getBlockState(lower).getValue(DoorBlock.FACING);
		BlockPos a = lower.relative(facing);
		BlockPos b = lower.relative(facing.getOpposite());
		boolean aCovered = covered(level, a);
		boolean bCovered = covered(level, b);
		boolean aOut = aCovered != bCovered ? !aCovered : horizontal(a, base) >= horizontal(b, base);
		return aOut ? new Door(lower.immutable(), a, b) : new Door(lower.immutable(), b, a);
	}

	/** True if something solid stands over this cell within {@value #ROOF_REACH} blocks (the air under a roof). */
	public static boolean covered(BlockGetter level, BlockPos cell) {
		BlockPos.MutableBlockPos pos = cell.mutable();
		for (int dy = 1; dy <= ROOF_REACH; dy++) {
			pos.setY(cell.getY() + dy);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** A cell one walks through (nothing to bump into but a carpet or a slab's worth). */
	private static boolean walkable(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.5;
	}

	/**
	 * The house: the covered cells one can walk through, found from {@code inside} at its height, no farther than
	 * {@value #HOUSE_REACH} (horizontally) from the base, loaded ones only; empty if {@code inside} is not under a roof.
	 */
	public static Set<BlockPos> interior(ServerLevel level, BlockPos inside, BlockPos base) {
		Set<BlockPos> found = new LinkedHashSet<>();
		if (!level.isLoaded(inside) || !walkable(level, inside) || !covered(level, inside)) {
			return found;
		}
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(inside.immutable());
		found.add(inside.immutable());
		while (!queue.isEmpty() && found.size() < HOUSE_MAX_CELLS) {
			BlockPos at = queue.poll();
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(dir);
				if (found.contains(next) || Math.abs(next.getX() - base.getX()) > HOUSE_REACH || Math.abs(next.getZ() - base.getZ()) > HOUSE_REACH) {
					continue;
				}
				if (level.isLoaded(next) && walkable(level, next) && covered(level, next)) {
					found.add(next);
					queue.add(next);
				}
			}
		}
		return found;
	}

	/** True if the player stands in the house (its cells, or right above one of them). */
	public static boolean inside(ServerPlayer player, Set<BlockPos> interior) {
		BlockPos feet = player.blockPosition();
		return interior.contains(feet) || interior.contains(feet.below());
	}

	// --- the path (pure geometry) ---

	/**
	 * The line's cells from the outside cell (index 0) out to {@code length} cells away along {@code yawDegrees} (the
	 * way out, Minecraft's yaw: 0 is south, 90 west): straight for the last {@value #WANDER_EVERY} cells before the
	 * door, and from there out a wander of at most one block every {@value #WANDER_EVERY} cells, never more than
	 * {@value #WANDER_MAX} off. All at the outside cell's height (the ground is found per column later).
	 */
	public static List<BlockPos> line(BlockPos outside, float yawDegrees, int length, RandomSource random) {
		double ox = -Mth.sin(yawDegrees * Mth.DEG_TO_RAD);
		double oz = Mth.cos(yawDegrees * Mth.DEG_TO_RAD);
		double px = -oz;
		double pz = ox;
		List<BlockPos> cells = new ArrayList<>(length + 1);
		int wander = 0;
		for (int t = 0; t <= length; t++) {
			if (t >= WANDER_EVERY && t % WANDER_EVERY == 0) {
				wander = Mth.clamp(wander + random.nextInt(3) - 1, -WANDER_MAX, WANDER_MAX);
			}
			double x = outside.getX() + 0.5 + ox * t + px * wander;
			double z = outside.getZ() + 0.5 + oz * t + pz * wander;
			cells.add(new BlockPos(Mth.floor(x), outside.getY(), Mth.floor(z)));
		}
		return cells;
	}

	/** The cell one block beside the line going out along {@code yawDegrees} (to its right). */
	public static BlockPos beside(BlockPos cell, float yawDegrees) {
		double ox = -Mth.sin(yawDegrees * Mth.DEG_TO_RAD);
		double oz = Mth.cos(yawDegrees * Mth.DEG_TO_RAD);
		return cell.offset((int) Math.round(-oz), 0, (int) Math.round(ox));
	}

	/**
	 * The print columns along a line: one every {@code stride} cells from index 0 (the door) out, alternating between
	 * the line's cell and the one beside it, starting on the line.
	 */
	public static List<BlockPos> steps(List<BlockPos> line, float yawDegrees, int stride) {
		List<BlockPos> steps = new ArrayList<>();
		int k = 0;
		for (int t = 0; t < line.size(); t += Math.max(1, stride), k++) {
			steps.add(k % 2 == 0 ? line.get(t) : beside(line.get(t), yawDegrees));
		}
		return steps;
	}

	/**
	 * Once round the house: the ring {@code 2} cells out from its cells' box (1 block from its walls), every
	 * {@code stride} cells along it from the corner nearest {@code start}, alternating with the cell one further out
	 * (2 blocks from the walls). Empty for no house.
	 */
	public static List<BlockPos> ring(Set<BlockPos> interior, BlockPos start, int stride) {
		if (interior.isEmpty()) {
			return List.of();
		}
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos p : interior) {
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minZ = Math.min(minZ, p.getZ());
			maxZ = Math.max(maxZ, p.getZ());
		}
		int x0 = minX - 2;
		int x1 = maxX + 2;
		int z0 = minZ - 2;
		int z1 = maxZ + 2;
		int y = start.getY();
		// The perimeter, clockwise seen from above (north edge west to east, then east, south, west).
		List<BlockPos> loop = new ArrayList<>();
		List<int[]> out = new ArrayList<>();
		for (int x = x0; x < x1; x++) {
			loop.add(new BlockPos(x, y, z0));
			out.add(new int[] {0, -1});
		}
		for (int z = z0; z < z1; z++) {
			loop.add(new BlockPos(x1, y, z));
			out.add(new int[] {1, 0});
		}
		for (int x = x1; x > x0; x--) {
			loop.add(new BlockPos(x, y, z1));
			out.add(new int[] {0, 1});
		}
		for (int z = z1; z > z0; z--) {
			loop.add(new BlockPos(x0, y, z));
			out.add(new int[] {-1, 0});
		}
		int first = 0;
		for (int i = 1; i < loop.size(); i++) {
			if (horizontal(loop.get(i), start) < horizontal(loop.get(first), start)) {
				first = i;
			}
		}
		List<BlockPos> steps = new ArrayList<>();
		int k = 0;
		for (int n = 0; n < loop.size(); n += Math.max(1, stride), k++) {
			int i = (first + n) % loop.size();
			BlockPos cell = loop.get(i);
			steps.add(k % 2 == 0 ? cell : cell.offset(out.get(i)[0], 0, out.get(i)[1]));
		}
		return steps;
	}

	/**
	 * The fake's columns: {@code count} prints {@code 2} blocks apart along a circle of {@code radius} round
	 * {@code center}, from {@code startDegrees}: a short curve, single file.
	 */
	public static List<BlockPos> curve(BlockPos center, double radius, double startDegrees, int count) {
		List<BlockPos> steps = new ArrayList<>();
		double step = 2.0 / Math.max(1.0, radius);
		for (int i = 0; i < count; i++) {
			double a = Math.toRadians(startDegrees) + i * step;
			BlockPos cell = new BlockPos(Mth.floor(center.getX() + 0.5 + Math.cos(a) * radius), center.getY(), Mth.floor(center.getZ() + 0.5 + Math.sin(a) * radius));
			if (!steps.contains(cell)) {
				steps.add(cell);
			}
		}
		return steps;
	}

	// --- planning over the level ---

	/**
	 * The prints for these columns: each column's ground near the last one found (starting at {@code groundY}), its
	 * print if it takes one. Null if a column is not loaded (nothing here loads one).
	 */
	static @Nullable List<BlockPos> grounds(ServerLevel level, List<BlockPos> columns, int groundY, List<Prints.Edit> edits) {
		List<BlockPos> prints = new ArrayList<>();
		int y = groundY;
		for (BlockPos column : columns) {
			if (!level.isLoaded(column)) {
				return null;
			}
			BlockPos ground = Prints.ground(level, column.getX(), column.getZ(), y);
			if (ground == null) {
				continue;
			}
			y = ground.getY();
			List<Prints.Edit> print = Prints.at(level, ground);
			if (!print.isEmpty() && !Prints.cells(edits).contains(ground)) {
				prints.add(ground);
				Prints.addAll(edits, print);
			}
		}
		return prints;
	}

	/**
	 * The tracks to this door: the way in (every turn of {@link #TURNS} tried, the one with the most prints kept), and
	 * with {@code circle} round the house and back out. No plan when the outside cell has no ground, a column is not
	 * loaded, or fewer than {@code minPrints} prints.
	 */
	public static Plan plan(ServerLevel level, Door door, BlockPos base, Config config, RandomSource random, boolean circle) {
		BlockPos outsideGround = Prints.ground(level, door.outside().getX(), door.outside().getZ(), door.lower().getY() - 1);
		if (outsideGround == null) {
			return Plan.none("no ground outside the door");
		}
		int length = Mth.nextInt(random, Math.max(1, config.lengthMin), Math.max(config.lengthMin, config.lengthMax));
		float outYaw = door.out().toYRot();
		Plan best = null;
		boolean unloaded = false;
		for (int turn : TURNS) {
			float yaw = outYaw + turn;
			List<BlockPos> in = line(door.outside(), yaw, length, random);
			List<Prints.Edit> edits = new ArrayList<>();
			List<BlockPos> way = grounds(level, steps(in, yaw, config.stride), outsideGround.getY(), edits);
			if (way == null) {
				unloaded = true;
				continue;
			}
			List<BlockPos> prints = new ArrayList<>(way.reversed());
			boolean circled = false;
			if (circle) {
				Set<BlockPos> house = interior(level, door.inside(), base);
				List<BlockPos> round = grounds(level, ring(house, door.outside(), config.stride), outsideGround.getY(), edits);
				List<BlockPos> back = grounds(level, steps(shifted(in, yaw, 2), yaw, config.stride), outsideGround.getY(), edits);
				if (round != null && back != null && !round.isEmpty()) {
					prints.addAll(round);
					prints.addAll(back);
					circled = true;
				}
			}
			if (best == null || prints.size() > best.prints().size()) {
				best = new Plan(List.copyOf(prints), List.copyOf(edits), circled, prints.size() + " prints" + (circled ? ", round the house" : ""));
			}
		}
		if (best == null) {
			return Plan.none(unloaded ? "the way in is not loaded" : "no way in");
		}
		if (best.prints().size() < config.minPrints) {
			return Plan.none("only " + best.prints().size() + " prints would show (at least " + config.minPrints + ")");
		}
		return best;
	}

	/** The fake's plan: a curve of three to five prints round {@code center}; none if fewer than three would show. */
	public static Plan fakePlan(ServerLevel level, BlockPos center, RandomSource random) {
		int count = Mth.nextInt(random, 3, 5);
		List<Prints.Edit> edits = new ArrayList<>();
		List<BlockPos> prints = grounds(level, curve(center, Mth.nextDouble(random, 3.0, 5.0), random.nextDouble() * 360.0, count), center.getY() - 1, edits);
		if (prints == null || prints.size() < 3) {
			return Plan.none("fewer than three prints would show near the animal");
		}
		return new Plan(List.copyOf(prints), List.copyOf(edits), false, prints.size() + " prints, curved");
	}

	private static List<BlockPos> shifted(List<BlockPos> line, float yawDegrees, int by) {
		List<BlockPos> moved = new ArrayList<>(line.size());
		for (BlockPos cell : line) {
			BlockPos at = cell;
			for (int i = 0; i < by; i++) {
				at = beside(at, yawDegrees);
			}
			moved.add(at);
		}
		return moved;
	}

	/**
	 * Why a plan may not happen now, or null: protected, someone within {@code nobodyWithin} ({@code exempt}, the target
	 * asleep or inside the house, does not count), or in view.
	 */
	static @Nullable String planRefusal(ScEnv env, ServerLevel level, Plan plan, Config config, @Nullable ServerPlayer exempt) {
		Set<BlockPos> cells = Prints.cells(plan.edits());
		if (env.anyProtected(level, cells)) {
			return "a print in a protected area";
		}
		List<ServerPlayer> people = exempt == null ? env.people().in(level) : ScGates.without(env.people().in(level), exempt);
		ServerPlayer near = ScGates.within(people, plan.prints(), config.nobodyWithin);
		if (near != null) {
			return ScGates.name(near) + " is within " + (int) config.nobodyWithin + " of a print";
		}
		if (!env.view().outOfView(level, cells)) {
			return "a print is in view";
		}
		return null;
	}

	/** Why this base takes no tracks now, or null: its cap, then (unless forced) the days since the last. */
	public static @Nullable String baseRefusal(ScMarks.Base marks, long today, Config config, boolean forced) {
		if (marks.tracks() >= config.maxPerBase) {
			return marks.tracks() + " tracks at this base already (at most " + config.maxPerBase + ")";
		}
		if (!forced && marks.tracksDay() != ScMarks.NEVER && today - marks.tracksDay() < config.minDaysApart) {
			return "the last tracks here were on day " + marks.tracksDay() + " (at least " + config.minDaysApart + " days apart)";
		}
		return null;
	}

	/** Why the target is in the way (unless forced): within {@code awayDistance} of the door, awake and outside. */
	static @Nullable String targetNear(ScEnv env, ServerLevel level, ServerPlayer target, Door door, BlockPos base, Config config) {
		if (ScGates.horizontalSqr(target, door.outside()) >= config.awayDistance * config.awayDistance || sheltered(env, level, target, door, base)) {
			return null;
		}
		return "the target is within " + (int) config.awayDistance + " of the door";
	}

	/** The target is asleep or inside the house: their own rule lets the tracks come, and they are no witness. */
	static boolean sheltered(ScEnv env, ServerLevel level, ServerPlayer target, Door door, BlockPos base) {
		return target.level() == level && (env.watch().isSleeping(target) || inside(target, interior(level, door.inside(), base)));
	}

	// --- live ---

	/**
	 * The card's work for {@code target}: the real tracks to their door (round the house from Proximity at
	 * {@code circleChance}, or always with {@code forceCircle}), or the fake near an animal.
	 */
	public static Outcome walk(ScEnv env, ServerPlayer target, Stage stage, RandomSource random, boolean fake, boolean forced, boolean forceCircle) {
		return walk(env, target, stage, random, fake, forced, forceCircle, config());
	}

	/** {@link #walk} with these numbers (tests). */
	public static Outcome walk(ScEnv env, ServerPlayer target, Stage stage, RandomSource random, boolean fake, boolean forced, boolean forceCircle,
			Config config) {
		ServerLevel level = target.level();
		String refusal = ScGates.targetRefusal(env.state(), env.watch(), target, level, forced);
		if (refusal != null) {
			return new Outcome(FireResult.SKIPPED, Plan.none(refusal), refusal);
		}
		Optional<GlobalPos> base = env.watch().base(target).filter(b -> b.dimension().equals(level.dimension()));
		if (base.isEmpty()) {
			return new Outcome(FireResult.SKIPPED, Plan.none("no base"), "no base in this level");
		}
		BlockPos home = base.get().pos();
		if (fake) {
			return fake(env, level, home, config, random);
		}
		long today = GameClock.day(env.server());
		String capped = baseRefusal(env.marks().base(base.get()), today, config, forced);
		if (capped != null) {
			return new Outcome(FireResult.SKIPPED, Plan.none(capped), capped);
		}
		Optional<Door> door = door(env, level, home);
		if (door.isEmpty()) {
			return new Outcome(FireResult.NO_SPOT, Plan.none("no door"), "no hand door a player placed within " + DOOR_RADIUS + " of the base");
		}
		if (!forced) {
			String near = targetNear(env, level, target, door.get(), home, config);
			if (near != null) {
				return new Outcome(FireResult.NO_SPOT, Plan.none(near), near);
			}
		}
		boolean circle = forceCircle || stage.atLeast(Stage.PROXIMITY) && random.nextDouble() < config.circleChance;
		Plan plan = plan(level, door.get(), home, config, random, circle);
		if (!plan.ok()) {
			return new Outcome(FireResult.NO_SPOT, plan, plan.reason());
		}
		String blocked = planRefusal(env, level, plan, config, sheltered(env, level, target, door.get(), home) ? target : null);
		if (blocked != null) {
			return new Outcome(FireResult.NO_SPOT, plan, blocked);
		}
		TraceBatch batch = env.traces().batch(level, CAUSE);
		Prints.queue(batch, plan.edits());
		if (!batch.commit()) {
			return new Outcome(FireResult.NO_SPOT, plan, "the tracks were refused (in view)");
		}
		// In this tick: another player's card for this base sees the cap and the day.
		env.marks().tracked(base.get(), today);
		A1016_02.LOGGER.info("[a1016] world: {} at {} ({} edits)", ID, door.get().outside().toShortString(), plan.edits().size());
		return new Outcome(FireResult.FIRED, plan, plan.reason());
	}

	private static Outcome fake(ScEnv env, ServerLevel level, BlockPos home, Config config, RandomSource random) {
		List<Animal> animals = level.getEntitiesOfClass(Animal.class, new AABB(home).inflate(FAKE_ANIMAL_RADIUS), Animal::isAlive);
		if (animals.isEmpty()) {
			return new Outcome(FireResult.NO_SPOT, Plan.none("no animal"), "no animal within " + FAKE_ANIMAL_RADIUS + " of the base");
		}
		Animal animal = animals.get(random.nextInt(animals.size()));
		Plan plan = fakePlan(level, animal.blockPosition(), random);
		if (!plan.ok()) {
			return new Outcome(FireResult.NO_SPOT, plan, plan.reason());
		}
		String blocked = planRefusal(env, level, plan, config, null);
		if (blocked != null) {
			return new Outcome(FireResult.NO_SPOT, plan, blocked);
		}
		TraceBatch batch = env.traces().batch(level, CAUSE);
		Prints.queue(batch, plan.edits());
		if (!batch.commit()) {
			return new Outcome(FireResult.NO_SPOT, plan, "the prints were refused (in view)");
		}
		A1016_02.LOGGER.info("[a1016] world: {} (fake) at {}", ID, animal.blockPosition().toShortString());
		return new Outcome(FireResult.FIRED, plan, plan.reason());
	}

	/** Debug lines: the base, the door, the marks and why the tracks would not come now. */
	public static List<String> describe(ScEnv env, ServerPlayer target) {
		ServerLevel level = target.level();
		Config config = config();
		List<String> lines = new ArrayList<>();
		String gate = ScGates.targetRefusal(env.state(), env.watch(), target, level, false);
		lines.add("09: " + (gate == null ? "open" : gate));
		Optional<GlobalPos> base = env.watch().base(target).filter(b -> b.dimension().equals(level.dimension()));
		if (base.isEmpty()) {
			lines.add("09: no base in this level");
			return lines;
		}
		ScMarks.Base marks = env.marks().base(base.get());
		String capped = baseRefusal(marks, GameClock.day(env.server()), config, false);
		lines.add("09: base " + base.get().pos().toShortString() + ", tracks " + marks.tracks() + " of " + config.maxPerBase
				+ (marks.tracksDay() == ScMarks.NEVER ? "" : ", last on day " + marks.tracksDay()) + (capped == null ? "" : "; " + capped));
		Optional<Door> door = door(env, level, base.get().pos());
		if (door.isEmpty()) {
			lines.add("09: no hand door a player placed within " + DOOR_RADIUS + " of the base");
			return lines;
		}
		Set<BlockPos> house = interior(level, door.get().inside(), base.get().pos());
		String near = targetNear(env, level, target, door.get(), base.get().pos(), config);
		lines.add("09: door " + door.get().lower().toShortString() + ", outside " + door.get().outside().toShortString() + ", house " + house.size()
				+ " cells" + (near == null ? "" : "; " + near));
		return lines;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** The card ({@value #ID}): MINOR, Traces, SCAR, VISITOR and WATCHER, with a fake. */
	public static final class Card implements EventCard {
		@Override
		public String id() {
			return ID;
		}

		@Override
		public Tier tier() {
			return Tier.MINOR;
		}

		@Override
		public Stage earliestStage() {
			return Stage.TRACES;
		}

		@Override
		public Set<Habit> habits() {
			return Set.of(Habit.VISITOR, Habit.WATCHER);
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.SCAR);
		}

		@Override
		public boolean hasFake() {
			return true;
		}

		/** The gates, a base here with a hand door, its caps, and the target away from the door, asleep or inside. */
		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			ScEnv env = ScEnv.live(world.getServer());
			if (ScGates.targetRefusal(env.state(), env.watch(), player, world, false) != null) {
				return false;
			}
			Config config = config();
			Optional<GlobalPos> base = env.watch().base(player).filter(b -> b.dimension().equals(world.dimension()));
			if (base.isEmpty() || baseRefusal(env.marks().base(base.get()), GameClock.day(world.getServer()), config, false) != null) {
				return false;
			}
			Optional<Door> door = door(env, world, base.get().pos());
			return door.isPresent() && targetNear(env, world, player, door.get(), base.get().pos(), config) == null;
		}

		@Override
		public FireResult fire(FireContext ctx) {
			ScEnv env = ScEnv.live(ctx.level().getServer());
			return walk(env, ctx.player(), env.state().stage(), ctx.random(), ctx.fake(), ctx.forced(), false).result();
		}
	}
}
