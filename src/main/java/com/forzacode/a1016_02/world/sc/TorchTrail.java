package com.forzacode.a1016_02.world.sc;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.world.Bases;
import com.forzacode.a1016_02.world.SignatureData;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import org.jspecify.annotations.Nullable;

/**
 * 11 ({@code docs/SELF_CREATED.md}, D-088): someone's torches go deeper into a cave branch nobody visited, on the
 * right-hand wall going in, and end at a 1x1 hole straight down toward lava, a torch on the floor before it and a sign
 * on the wall a few cells before that, never read from the hole's lip ({@link #sign}; "brb", from {@value #TEXT_FILE},
 * not waxed). From a cave spot a participant stood in (world's cave
 * spots, {@code LoneTorch.trackCaves}): a walk over natural cave air (not player-dug, a sturdy natural floor, two
 * cells high, at least {@code minDepthBelowSurface} under the surface, at most {@value #MAX_CELLS} cells looked at)
 * {@code pathMin} to {@code pathMax} cells to a floor cell under which natural stone runs straight down
 * {@code shaftMin} to {@code shaftMax} blocks (its sides solid and dry) to lava, a source within {@value #LAVA_REACH}
 * below. Never an unmarked death (DESIGN guardrail 1, FIX-SC-FINAL): the hole is at a dead end that only the trail
 * reaches, the walk's last cells from the sign's on walled in on every side but the walk's and roofed
 * ({@link #enclosedFrom}), so nobody comes on it from anywhere else; and the fall is survived: the stone column's last
 * block stays as the shaft's floor over the lava and the lowest dug cell holds a water source (t0rch's own landing;
 * from above, the same dark 1x1 drop). From the branch's mouth (the walk's first cell in a chunk nobody ever visited;
 * every cell on from there too) a wall torch every {@code spacingMin} to {@code spacingMax} cells, the first within
 * {@value #MOUTH_TORCH} of it. Never within {@code baseClearance} of any known base, nobody within
 * {@code nobodyWithin}, every cell loaded, unprotected and out of view. A search that finds nothing says why: the
 * reason most ends gave among those that got furthest (a shaft, then a dead end, then a branch nobody visited, then the
 * rest). Left by others ({@code world:left/t0rch/...}): one batch whose only ledgered edits are the shaft's
 * ({@value #CAUSE}, never undone; the water is left like the torches); never a site. At most {@code maxPerWorld} per
 * world, saved in the tick it is left. Server thread.
 */
public final class TorchTrail {
	public static final String ID = "sc_11";
	public static final String SECTION = "sc11";
	/** The batch's cause: left by others. Only the shaft's removals are ledgered; the torches and the sign are left. */
	public static final String CAUSE = "world:left/t0rch/shaft";
	/** The sign's text, word for word. */
	public static final String TEXT_FILE = "data/a1016_02/world/sc/t0rch.json";
	static final Identifier TEXT_ID = A1016_02.id("world/sc/t0rch.json");
	/** If the file cannot be read. */
	static final List<String> FALLBACK_LINES = List.of("brb", "", "", "");
	/** The walk looks at no more cells than this. */
	public static final int MAX_CELLS = 6000;
	/** A lava source this far below the shaft's last stone, through open cells. */
	public static final int LAVA_REACH = 3;
	/** The first torch is at most this many cells past the branch's mouth. */
	public static final int MOUTH_TORCH = 6;
	/** A wall torch goes on the first wall at most this far to the right of the walk. */
	private static final int WALL_REACH = 4;
	/** The torches stop this many cells before the hole. */
	private static final int END_CLEAR = 2;
	/** The sign hangs over a walk cell this many cells before the hole at the least, and at the most. */
	public static final int SIGN_BACK_MIN = 2;
	public static final int SIGN_BACK_MAX = 5;
	/** The sign's column and the one it faces are at least this many cells (either way) from the hole's. */
	public static final int SIGN_CLEAR = 2;
	/** The stone column under the hole is at least this long whatever the config: dug cells, the water's, and the floor. */
	public static final int MIN_COLUMN = 3;
	/** Ends looked at per walk. */
	private static final int MAX_ENDS = 256;
	/** Cave spots tried per fire, nearest the target first. */
	private static final int SPOTS_PER_FIRE = 4;
	/** A cave spot this far from the target is not tried (it would not be loaded). */
	private static final int SPOT_REACH = 160;

	/** {@code sections.sc11}. Version 2. */
	public static final class Config implements ModConfig.Versioned {
		public static final int CURRENT = 2;

		public int version = 0;
		@SerializedName(value = "a", alternate = "pathMin")
		public int pathMin = 30;
		@SerializedName(value = "b", alternate = "pathMax")
		public int pathMax = 90;
		@SerializedName(value = "c", alternate = "spacingMin")
		public int spacingMin = 8;
		@SerializedName(value = "d", alternate = "spacingMax")
		public int spacingMax = 12;
		@SerializedName(value = "e", alternate = "shaftMin")
		public int shaftMin = 8;
		@SerializedName(value = "f", alternate = "shaftMax")
		public int shaftMax = 30;
		@SerializedName(value = "g", alternate = "minDepthBelowSurface")
		public int minDepthBelowSurface = 12;
		@SerializedName(value = "h", alternate = "nobodyWithin")
		public double nobodyWithin = 32.0;
		@SerializedName(value = "i", alternate = "baseClearance")
		public double baseClearance = 64.0;
		@SerializedName(value = "j", alternate = "maxPerWorld")
		public int maxPerWorld = 1;

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

	/** A block to leave. */
	public record Left(BlockPos pos, BlockState state) {
	}

	/**
	 * What would be left.
	 *
	 * @param path      the walk, from the known spot to the hole's floor cell
	 * @param mouth     the index of the branch's mouth in it
	 * @param torches   the wall torches, going in
	 * @param shaft     the shaft's dug cells, top first: the stone column under the hole but its last block (the floor)
	 * @param lava      the lava under the column
	 * @param floorTorch the torch beside the hole (the walk's cell before it at a dead end)
	 * @param sign      the wall sign a few cells before the hole
	 */
	public record Plan(List<BlockPos> path, int mouth, List<Left> torches, List<BlockPos> shaft, BlockPos lava, Left floorTorch, Left sign) {
		public BlockPos hole() {
			return path.getLast();
		}

		/** The shaft's lowest dug cell: a water source, so a fall into it is survived. */
		public BlockPos water() {
			return shaft.getLast();
		}

		/** The column's last stone, left as the shaft's floor between the water and the lava. */
		public BlockPos floor() {
			return shaft.getLast().below();
		}

		/** Every cell it changes. */
		public Set<BlockPos> cells() {
			Set<BlockPos> cells = new LinkedHashSet<>(shaft);
			torches.forEach(t -> cells.add(t.pos()));
			cells.add(floorTorch.pos());
			cells.add(sign.pos());
			return cells;
		}

		public String summary() {
			return torches.size() + " torches over " + (path.size() - 1 - mouth) + " cells from the mouth, a shaft of " + shaft.size()
					+ " with water at its foot over lava at " + lava.toShortString();
		}
	}

	/** A search's answer: the plan, or why there is none. */
	public record Result(@Nullable Plan plan, String reason) {
		static Result none(String reason) {
			return new Result(null, reason);
		}
	}

	private TorchTrail() {
	}

	public static Config config() {
		return ModConfig.section(SECTION, Config.class, Config::new);
	}

	// --- the sign's text ---

	/** The sign's four lines from {@value #TEXT_FILE} ({@code {"front": [...]}}), else {@link #FALLBACK_LINES}. */
	public static List<String> lines(MinecraftServer server) {
		Optional<Resource> resource = server.getResourceManager().getResource(TEXT_ID);
		if (resource.isEmpty()) {
			A1016_02.LOGGER.warn("[a1016] world: {}'s text is missing", ID);
			return FALLBACK_LINES;
		}
		try (Reader reader = resource.get().openAsReader()) {
			JsonElement json = JsonParser.parseReader(reader);
			List<String> lines = new ArrayList<>();
			json.getAsJsonObject().getAsJsonArray("front").forEach(e -> lines.add(e.getAsString()));
			while (lines.size() < SignText.LINES) {
				lines.add("");
			}
			return List.copyOf(lines.subList(0, SignText.LINES));
		} catch (Exception e) {
			A1016_02.LOGGER.warn("[a1016] world: {}'s text could not be read", ID, e);
			return FALLBACK_LINES;
		}
	}

	/** A wall sign's block entity data with these front lines, not waxed. */
	static CompoundTag signData(ServerLevel level, List<String> lines) {
		List<Component> messages = new ArrayList<>(SignText.LINES);
		for (int i = 0; i < SignText.LINES; i++) {
			messages.add(Component.literal(i < lines.size() ? lines.get(i) : ""));
		}
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag tag = new CompoundTag();
		tag.put("front_text", SignText.CODEC.encodeStart(ops, new SignText(messages, messages, DyeColor.BLACK, false)).getOrThrow());
		tag.put("back_text", SignText.CODEC.encodeStart(ops, SignText.EMPTY).getOrThrow());
		tag.putBoolean("is_waxed", false);
		return tag;
	}

	// --- the walk ---

	/** Two cells of open air, no fluid. */
	private static boolean open(BlockState state) {
		return state.isAir() && state.getFluidState().isEmpty();
	}

	/**
	 * A cell one stands in on the walk: it and the one above are open air, neither dug by a player, over a sturdy dry
	 * floor no player placed, at least {@code minDepth} under the surface, all loaded.
	 */
	static boolean walkable(ServerLevel level, PlayerWatch watch, BlockPos feet, int minDepth) {
		BlockPos head = feet.above();
		BlockPos floor = feet.below();
		if (!level.isLoaded(floor) || !level.isLoaded(head)) {
			return false;
		}
		if (!open(level.getBlockState(feet)) || !open(level.getBlockState(head))) {
			return false;
		}
		BlockState ground = level.getBlockState(floor);
		if (!ground.isFaceSturdy(level, floor, Direction.UP) || !ground.getFluidState().isEmpty()) {
			return false;
		}
		if (watch.wasDugByPlayer(level, feet) || watch.wasDugByPlayer(level, head) || watch.wasPlacedByPlayer(level, floor)) {
			return false;
		}
		return level.getHeight(Heightmap.Types.WORLD_SURFACE, feet.getX(), feet.getZ()) - feet.getY() >= minDepth;
	}

	/** Natural stone a shaft goes through: the overworld's base stones (stone, deepslate, granite, tuff...), never an ore. */
	static boolean naturalStone(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
	}

	/**
	 * The stone column under a floor cell: its cells (top first) and the lava under it; null if there is none. All but
	 * its last cell are dug ({@link #dug}); the last stays as the floor.
	 */
	record Shaft(List<BlockPos> cells, BlockPos lava) {
		/** The cells dug, top first: the column but its last block. */
		List<BlockPos> dug() {
			return cells.subList(0, cells.size() - 1);
		}
	}

	/**
	 * Under {@code hole}: natural stone straight down, not placed or dug by a player, its four sides solid and dry,
	 * {@code shaftMin} (at least {@value #MIN_COLUMN}) to {@code shaftMax} blocks, then open cells (air or lava) down to a
	 * lava source within {@value #LAVA_REACH}. Null if not.
	 */
	static @Nullable Shaft shaft(ServerLevel level, PlayerWatch watch, BlockPos hole, Config config) {
		List<BlockPos> cells = new ArrayList<>();
		BlockPos.MutableBlockPos at = hole.mutable();
		for (int k = 1; k <= config.shaftMax + 1; k++) {
			at.setY(hole.getY() - k);
			if (!level.isLoaded(at)) {
				return null;
			}
			BlockState state = level.getBlockState(at);
			if (!naturalStone(state) || watch.wasPlacedByPlayer(level, at) || watch.wasDugByPlayer(level, at)) {
				break;
			}
			if (k > config.shaftMax) {
				return null;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos beside = at.relative(side);
				if (!level.isLoaded(beside)) {
					return null;
				}
				BlockState next = level.getBlockState(beside);
				if (!next.getFluidState().isEmpty() || next.getCollisionShape(level, beside).isEmpty()) {
					return null;
				}
			}
			cells.add(at.immutable());
		}
		if (cells.size() < Math.max(config.shaftMin, MIN_COLUMN)) {
			return null;
		}
		for (int k = 1; k <= LAVA_REACH; k++) {
			BlockPos below = cells.getLast().below(k);
			BlockState state = level.getBlockState(below);
			if (state.getFluidState().is(Fluids.LAVA) && state.getFluidState().isSource()) {
				return new Shaft(List.copyOf(cells), below.immutable());
			}
			if (!state.isAir() && !state.getFluidState().is(Fluids.LAVA)) {
				return null;
			}
		}
		return null;
	}

	/** The horizontal direction from one walk cell to the next. */
	static Direction heading(BlockPos from, BlockPos to) {
		return Direction.getApproximateNearest(to.getX() - from.getX(), 0.0, to.getZ() - from.getZ());
	}

	/**
	 * A wall torch on the right-hand wall of the walk at {@code at} going {@code heading}: at head height (else at the
	 * feet), on the first sturdy wall at most {@value #WALL_REACH} cells to the right, in the open cell before it.
	 */
	static @Nullable Left rightWallTorch(ServerLevel level, BlockPos at, Direction heading, Set<BlockPos> taken) {
		Direction right = heading.getClockWise();
		for (int dy : new int[] {1, 0}) {
			BlockPos cell = at.above(dy);
			for (int j = 1; j <= WALL_REACH; j++) {
				BlockPos wall = cell.relative(right, j);
				if (!level.isLoaded(wall)) {
					break;
				}
				BlockState state = level.getBlockState(wall);
				if (open(state)) {
					continue;
				}
				BlockPos torchAt = cell.relative(right, j - 1);
				BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, right.getOpposite());
				if (!taken.contains(torchAt) && open(level.getBlockState(torchAt)) && torch.canSurvive(level, torchAt)) {
					return new Left(torchAt.immutable(), torch);
				}
				break;
			}
		}
		return null;
	}

	/**
	 * The torches going in from the mouth: the first within {@value #MOUTH_TORCH} cells of it, then one every
	 * {@code spacingMin} to {@code spacingMax} cells, up to {@value #END_CLEAR} cells before the hole (the floor torch
	 * lights the rest). Null if a whole window before that has no right-hand wall, or there is no torch at all.
	 */
	static @Nullable List<Left> torches(ServerLevel level, List<BlockPos> path, int mouth, Config config, Set<BlockPos> taken) {
		List<Left> torches = new ArrayList<>();
		int last = path.size() - 1 - END_CLEAR;
		int from = mouth;
		int to = mouth + MOUTH_TORCH;
		while (from <= last) {
			Left placed = null;
			int at = -1;
			for (int i = from; i <= Math.min(to, last) && placed == null; i++) {
				placed = rightWallTorch(level, path.get(i), heading(path.get(i), path.get(i + 1)), taken);
				at = i;
			}
			if (placed == null) {
				// A window that runs past the last cell may stay dark: the floor torch is next.
				return to > last && !torches.isEmpty() ? torches : null;
			}
			torches.add(placed);
			taken.add(placed.pos());
			from = at + Math.max(1, config.spacingMin);
			to = at + Math.max(config.spacingMin, config.spacingMax);
		}
		return torches.isEmpty() ? null : torches;
	}

	/**
	 * Searches from {@code start} (a remembered cave spot, or near it) for a plan: the walk, then its ends in a random
	 * order (at most {@value #MAX_ENDS}), the first that holds every rule. {@code visits} null skips the visit rule
	 * (debug): the mouth is then the walk's start. With none, the answer is the reason most ends gave among those that
	 * got furthest.
	 */
	public static Result search(ScEnv env, ServerLevel level, BlockPos start, Config config, NewScarPlacer.@Nullable VisitLookup visits, RandomSource random) {
		PlayerWatch watch = env.watch();
		BlockPos first = null;
		for (BlockPos at : List.of(start, start.below(), start.above())) {
			if (walkable(level, watch, at, config.minDepthBelowSurface)) {
				first = at.immutable();
				break;
			}
		}
		if (first == null) {
			return Result.none("the cave spot " + start.toShortString() + " is not natural cave floor deep enough (or not loaded)");
		}
		Map<BlockPos, BlockPos> parent = new HashMap<>();
		Map<BlockPos, Integer> dist = new HashMap<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		parent.put(first, first);
		dist.put(first, 0);
		queue.add(first);
		List<BlockPos> ends = new ArrayList<>();
		while (!queue.isEmpty() && dist.size() < MAX_CELLS) {
			BlockPos at = queue.poll();
			int d = dist.get(at);
			if (d >= config.pathMin) {
				ends.add(at);
			}
			if (d >= config.pathMax) {
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos side = at.relative(dir);
				for (BlockPos next : List.of(side, side.above(), side.below())) {
					if (dist.containsKey(next)) {
						continue;
					}
					if (next.getY() > at.getY() && !open(level.getBlockState(at.above(2)))) {
						continue;
					}
					if (walkable(level, watch, next, config.minDepthBelowSurface)) {
						parent.put(next, at);
						dist.put(next, d + 1);
						queue.add(next);
					}
				}
			}
		}
		if (ends.isEmpty()) {
			return Result.none("no cave " + config.pathMin + " to " + config.pathMax + " cells from " + first.toShortString() + " (" + dist.size()
					+ " cells walked)");
		}
		Util.shuffle(ends, random);
		List<BlockPos> bases = env.bases().all(level.dimension());
		List<ServerPlayer> people = env.people().in(level);
		// Why each end failed, by how far it got: 0 no shaft, 1 not a dead end, 2 not a branch nobody visited, 3 the torches,
		// sign or a rule.
		List<Map<String, Integer>> why = List.of(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
		int looked = 0;
		for (BlockPos end : ends) {
			if (looked++ >= MAX_ENDS) {
				break;
			}
			Shaft shaft = shaft(level, watch, end, config);
			if (shaft == null) {
				why.get(0).merge("no shaft to lava under it", 1, Integer::sum);
				continue;
			}
			List<BlockPos> path = new ArrayList<>();
			for (BlockPos at = end; ; at = parent.get(at)) {
				path.add(at);
				if (at.equals(first)) {
					break;
				}
			}
			Collections.reverse(path);
			// Guardrail 1: only the trail leads to the hole, through the sign's cell at the latest.
			int enclosed = enclosedFrom(level, path);
			if (enclosed > path.size() - SIGN_BACK_MIN) {
				why.get(1).merge("the end can be walked into from beside the trail", 1, Integer::sum);
				continue;
			}
			int mouth = mouth(level, path, visits);
			if (mouth < 0) {
				why.get(2).merge("the way there is not a branch nobody visited", 1, Integer::sum);
				continue;
			}
			Result built = build(env, level, path, mouth, enclosed, shaft, config, bases, people);
			if (built.plan() != null) {
				return built;
			}
			why.get(3).merge(built.reason(), 1, Integer::sum);
		}
		int tried = Math.min(looked, ends.size());
		for (int stage = why.size() - 1; stage >= 0; stage--) {
			Optional<Map.Entry<String, Integer>> most = why.get(stage).entrySet().stream().max(Map.Entry.comparingByValue());
			if (most.isPresent()) {
				return Result.none(most.get().getKey() + " (" + most.get().getValue() + " of " + tried + " ends)");
			}
		}
		return Result.none("nothing");
	}

	/**
	 * The branch's mouth: the walk's first cell in a chunk nobody ever visited, every cell on from it in such chunks too,
	 * at least {@value #MOUTH_TORCH} cells before the end; -1 if there is none. With no visit rule, the walk's start.
	 */
	static int mouth(ServerLevel level, List<BlockPos> path, NewScarPlacer.@Nullable VisitLookup visits) {
		if (visits == null) {
			return 0;
		}
		int mouth = -1;
		for (int i = 0; i < path.size(); i++) {
			boolean never = visits.lastVisitDay(level, ChunkPos.containing(path.get(i))) < 0;
			if (never && mouth < 0) {
				mouth = i;
			} else if (!never && mouth >= 0) {
				return -1;
			}
		}
		return mouth > 0 && mouth < path.size() - 1 - END_CLEAR ? mouth : -1;
	}

	/** Horizontal distance in cells, either way (the larger of x and z), between two columns. */
	static int columns(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
	}

	/** A cell nobody passes through: loaded, a full block, no fluid. */
	static boolean closed(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, pos);
	}

	/**
	 * The walk cell at {@code i} is walled in but for the walk: at its feet and head, every side but the ones toward the
	 * walk's cells before and after it is closed, and so is the cell over its head (no way in from above, nor up from it).
	 */
	static boolean walledIn(ServerLevel level, List<BlockPos> path, int i) {
		BlockPos at = path.get(i);
		Set<Direction> along = new LinkedHashSet<>();
		if (i > 0) {
			along.add(heading(at, path.get(i - 1)));
		}
		if (i < path.size() - 1) {
			along.add(heading(at, path.get(i + 1)));
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!along.contains(side) && (!closed(level, at.relative(side)) || !closed(level, at.relative(side).above()))) {
				return false;
			}
		}
		return closed(level, at.above(2));
	}

	/**
	 * Where the dead end at the walk's end begins (FIX-SC-FINAL, guardrail 1): the first index from which every cell on to
	 * the hole is {@link #walledIn}, so the hole is reached only along the walk through that cell; the walk's size if the
	 * hole's own cell is open on a side other than the way in (or over its head).
	 */
	static int enclosedFrom(ServerLevel level, List<BlockPos> path) {
		int from = path.size();
		while (from > 1 && walledIn(level, path, from - 1)) {
			from--;
		}
		return from;
	}

	/**
	 * The sign (review R9: reading it never puts you at the shaft's lip): at head height over a walk cell
	 * {@value #SIGN_BACK_MIN} to {@value #SIGN_BACK_MAX} cells before the hole, past the mouth, on a sturdy wall, the
	 * left-hand one going in first (the torches are on the right), else the right, else one ahead, facing the walk; its
	 * own column and the one it faces at least {@value #SIGN_CLEAR} cells from the hole's, so whoever walks up to read it
	 * stands on the walk's floor, never beside the hole. Every cell after the sign's is walled in ({@code enclosed}, from
	 * {@link #enclosedFrom}): whoever reaches the hole passed the sign. Null if there is none.
	 */
	static @Nullable Left sign(ServerLevel level, List<BlockPos> path, int mouth, int enclosed, Set<BlockPos> taken) {
		BlockPos hole = path.getLast();
		for (int back = SIGN_BACK_MIN; back <= SIGN_BACK_MAX; back++) {
			int i = path.size() - 1 - back;
			if (i <= mouth || i + 1 < enclosed) {
				break;
			}
			BlockPos at = path.get(i).above();
			if (taken.contains(at) || !open(level.getBlockState(at)) || columns(at, hole) < SIGN_CLEAR) {
				continue;
			}
			Direction going = heading(path.get(i), path.get(i + 1));
			for (Direction wall : List.of(going.getCounterClockWise(), going.getClockWise(), going)) {
				Direction facing = wall.getOpposite();
				BlockPos behind = at.relative(wall);
				BlockState state = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
				if (columns(at.relative(facing), hole) >= SIGN_CLEAR && level.isLoaded(behind)
						&& level.getBlockState(behind).isFaceSturdy(level, behind, facing) && state.canSurvive(level, at)) {
					return new Left(at.immutable(), state);
				}
			}
		}
		return null;
	}

	/** The torches, the sign and the floor torch for one end, with every safety rule; a plan or why not. */
	static Result build(ScEnv env, ServerLevel level, List<BlockPos> path, int mouth, int enclosed, Shaft shaft, Config config,
			List<BlockPos> bases, List<ServerPlayer> people) {
		BlockPos hole = path.getLast();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!level.isLoaded(hole.relative(side))) {
				return Result.none("not loaded");
			}
		}
		Direction in = heading(path.get(path.size() - 2), hole);
		Set<BlockPos> taken = new LinkedHashSet<>(shaft.cells());
		taken.add(hole);
		Left sign = sign(level, path, mouth, enclosed, taken);
		if (sign == null) {
			return Result.none("no wall " + SIGN_BACK_MIN + " to " + SIGN_BACK_MAX + " cells before the hole for the sign, with only the trail on from it");
		}
		taken.add(sign.pos());
		// The floor torch: beside the hole, a side first, else the cell one came from.
		Left floor = null;
		for (BlockPos at : List.of(hole.relative(in.getClockWise()), hole.relative(in.getCounterClockWise()), path.get(path.size() - 2))) {
			BlockState torch = Blocks.TORCH.defaultBlockState();
			if (!taken.contains(at) && open(level.getBlockState(at)) && torch.canSurvive(level, at)) {
				floor = new Left(at.immutable(), torch);
				break;
			}
		}
		if (floor == null) {
			return Result.none("no floor beside the hole for a torch");
		}
		taken.add(floor.pos());
		List<Left> torches = torches(level, path, mouth, config, taken);
		if (torches == null) {
			return Result.none("no right-hand wall for a torch every " + config.spacingMin + " to " + config.spacingMax + " cells");
		}
		Plan plan = new Plan(List.copyOf(path), mouth, List.copyOf(torches), List.copyOf(shaft.dug()), shaft.lava(), floor, sign);
		Set<BlockPos> cells = plan.cells();
		if (!ScEnv.loaded(level, cells)) {
			return Result.none("not loaded");
		}
		for (BlockPos cell : cells) {
			if (Bases.near(bases, cell, config.baseClearance)) {
				return Result.none("within " + (int) config.baseClearance + " of a base");
			}
		}
		if (env.anyProtected(level, cells)) {
			return Result.none("a protected area");
		}
		ServerPlayer near = ScGates.within(people, cells, config.nobodyWithin);
		if (near != null) {
			return Result.none(ScGates.name(near) + " is within " + (int) config.nobodyWithin);
		}
		if (!env.view().outOfView(level, cells)) {
			return Result.none("in view");
		}
		return new Result(plan, plan.summary());
	}

	/**
	 * Leaves the plan in one batch, left by others: the shaft dug down to its floor, the water at its foot, the torches and
	 * the sign; false (nothing left) if it was refused.
	 */
	static boolean leave(ScEnv env, ServerLevel level, Plan plan, List<String> lines) {
		TraceBatch batch = env.traces().batch(level, CAUSE);
		for (BlockPos cell : plan.shaft()) {
			batch.remove(cell);
		}
		batch.leave(plan.water(), Blocks.WATER.defaultBlockState());
		for (Left torch : plan.torches()) {
			batch.leave(torch.pos(), torch.state());
		}
		batch.leave(plan.floorTorch().pos(), plan.floorTorch().state());
		batch.leave(plan.sign().pos(), plan.sign().state(), signData(level, lines));
		return batch.commit();
	}

	/** Why no more trails may be left in this world, or null. */
	public static @Nullable String capRefusal(ScMarks marks, Config config) {
		int left = marks.trails().size();
		return left >= config.maxPerWorld ? left + " left in this world already (at most " + config.maxPerWorld + ")" : null;
	}

	/**
	 * The card's work: from up to {@value #SPOTS_PER_FIRE} remembered cave spots nearest the target (loaded, within
	 * {@value #SPOT_REACH}), or from {@code starts} when given (debug), the first plan found is left and recorded.
	 */
	public static Result leaveFor(ScEnv env, ServerPlayer target, @Nullable List<BlockPos> starts, NewScarPlacer.@Nullable VisitLookup visits,
			Config config, RandomSource random, boolean forced) {
		ServerLevel level = target.level();
		String refusal = ScGates.targetRefusal(env.state(), env.watch(), target, level, forced);
		if (refusal != null) {
			return Result.none(refusal);
		}
		String capped = capRefusal(env.marks(), config);
		if (capped != null) {
			return Result.none(capped);
		}
		List<BlockPos> from = starts != null ? starts : spots(env, level, target);
		if (from.isEmpty()) {
			return Result.none("no cave spot a participant stood in is loaded within " + SPOT_REACH);
		}
		String last = "nothing";
		for (BlockPos start : from) {
			Result found = search(env, level, start, config, visits, random);
			if (found.plan() == null) {
				last = found.reason();
				continue;
			}
			if (!leave(env, level, found.plan(), lines(env.server()))) {
				last = "the trail was refused (in view)";
				continue;
			}
			// In this tick: once per world, whoever's card asks next.
			env.marks().addTrail(new ScMarks.Trail(GlobalPos.of(level.dimension(), found.plan().hole()), GameClock.day(env.server())));
			A1016_02.LOGGER.info("[a1016] world: {} at {}", ID, found.plan().hole().toShortString());
			return found;
		}
		return Result.none(last);
	}

	/** The remembered cave spots in this level, loaded, within {@value #SPOT_REACH} of the target, nearest first. */
	static List<BlockPos> spots(ScEnv env, ServerLevel level, ServerPlayer target) {
		List<BlockPos> spots = new ArrayList<>();
		for (SignatureData.Spot spot : env.data().signatures().caveSpots()) {
			BlockPos pos = spot.pos().pos();
			if (spot.pos().dimension().equals(level.dimension()) && pos.closerThan(target.blockPosition(), SPOT_REACH) && level.isLoaded(pos)) {
				spots.add(pos);
			}
		}
		spots.sort(Comparator.comparingDouble(p -> p.distSqr(target.blockPosition())));
		return spots.size() > SPOTS_PER_FIRE ? List.copyOf(spots.subList(0, SPOTS_PER_FIRE)) : spots;
	}

	/** Debug: the remembered cave spot nearest {@code at} within 64 blocks, else {@code at} itself. */
	public static BlockPos nearestSpot(ScEnv env, ServerLevel level, BlockPos at) {
		return env.data().signatures().caveSpots().stream()
				.filter(s -> s.pos().dimension().equals(level.dimension()) && s.pos().pos().closerThan(at, 64))
				.map(s -> s.pos().pos())
				.min(Comparator.comparingDouble(p -> p.distSqr(at)))
				.orElse(at);
	}

	/** Debug lines: what was left, the cave spots, and the gate now. */
	public static List<String> describe(ScEnv env, ServerPlayer target) {
		Config config = config();
		List<String> lines = new ArrayList<>();
		String gate = ScGates.targetRefusal(env.state(), env.watch(), target, target.level(), false);
		String capped = capRefusal(env.marks(), config);
		lines.add("11: " + (gate != null ? gate : capped != null ? capped : "open") + "; left " + env.marks().trails().size() + " of " + config.maxPerWorld);
		for (ScMarks.Trail trail : env.marks().trails()) {
			lines.add("11: left at " + trail.pos().pos().toShortString() + " on day " + trail.day());
		}
		lines.add("11: cave spots remembered " + env.data().signatures().caveSpots().size() + ", " + spots(env, target.level(), target).size()
				+ " loaded near you");
		return lines;
	}

	/** The card ({@value #ID}): MINOR, Traces, LIGHT and SCAR, MOURNER and CARVER, no fake, once per world. */
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
			return Set.of(Habit.MOURNER, Habit.CARVER);
		}

		@Override
		public Set<CardTag> tags() {
			return Set.of(CardTag.LIGHT, CardTag.SCAR);
		}

		@Override
		public boolean hasFake() {
			return false;
		}

		/** Cheap: the gates, the world's cap and a remembered cave spot loaded near the target. */
		@Override
		public boolean contextFits(ServerPlayer player, ServerLevel world) {
			ScEnv env = ScEnv.live(world.getServer());
			return world.dimension() == Level.OVERWORLD && ScGates.targetRefusal(env.state(), env.watch(), player, world, false) == null
					&& capRefusal(env.marks(), config()) == null && !spots(env, world, player).isEmpty();
		}

		@Override
		public FireResult fire(FireContext ctx) {
			ScEnv env = ScEnv.live(ctx.level().getServer());
			Result result = leaveFor(env, ctx.player(), null, env.watch()::lastVisitDay, config(), ctx.random(), ctx.forced());
			if (result.plan() != null) {
				return FireResult.FIRED;
			}
			return ScGates.targetRefusal(env.state(), env.watch(), ctx.player(), ctx.level(), ctx.forced()) != null
					|| capRefusal(env.marks(), config()) != null ? FireResult.SKIPPED : FireResult.NO_SPOT;
		}
	}
}
