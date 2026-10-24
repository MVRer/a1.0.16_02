package com.forzacode.a1016_02.world.sc2.s18;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.gen.Vegetation;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Seal;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Writer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * Feature 18's shape (docs/SELF_CREATED_2.md, 18): the inside of a mountain taken out, all of it his removal.
 *
 * <p>The start's origin is the middle of the place at the floor's height ({@code Yf}: the stone the floor is); its facing
 * is the side the way in looks out of (toward the world's origin, D-095). Everything is decided here from the start and
 * the terrain, never from blocks: the land's top on a grid every {@value #GRID} blocks out to {@value #EXTENT} each way
 * (the noise's own cell width, so the grid holds the noise's shape), and from it, for every column, how high the inside
 * reaches: under the land's top by the shell ({@code d} over the inside, {@code e} to a steep side, measured round the
 * column), never past a dome that closes the inside {@value #DOME} from the middle, and cut in terraces (the lowest
 * {@value #MIN_HEIGHT} high, each next {@value #TERRACE} higher), so its ceiling is the mountain's own shape seen from
 * under it, stepped. Only the part joined to the middle is taken. Columns of the stone are left standing on a square
 * grid ({@code f} apart, its rows along the way in, wider where the inside is taller, each widening into the ceiling in
 * three steps and with a step at its foot; none round the square, none in the nave from the square to the way in), a
 * few short stubs hang from the ceiling, and under the tallest part a perfect square of dirt holds bare stems that grew
 * without light. The way in is a 2 x 2 tunnel at the floor's height on the
 * line from that square toward the facing, through the rock to where the land falls to the floor, with a cut in front
 * where the slope needs one (and steps down where the land falls further). A start may also have a high opening: a 3 x
 * 3 shaft straight up through the shell from the top of the inside where the mountain's side is steep, so daylight
 * falls through it onto one square of the floor far below and the sky shows overhead, with a landing under it that
 * takes any fall (fair danger 7): a pool in the floor, or, where the land there is cold enough to freeze water (vanilla
 * freezes an open pool in such a biome, at worldgen's own freeze step and later by the weather, and ice takes no fall),
 * a drift of powder snow on the floor, which vanilla never freezes, melts or snows over, and which stops a fall of any
 * height as it does in the mountains (R12-1, shape version 2).
 *
 * <p>Seal first (the kit's common rule round the inside and the inner part of the way in), then take, then open.
 *
 * <p>Versions (R12-2, {@link Start#versionOr}): 1, the opening 3 x 3 with a pool up to 5 x 5, always water, the
 * steepest side preferred; 2 (R12-1, R11-8), the cold landing above, the opening 5 x 5 over a landing 7 x 7 (the floor
 * whole there), the one nearest the nave's line from the square to the way in preferred (from the inner mouth the first
 * thing seen is one column of daylight in front of the grove), and the walking floor's top two layers plain stone
 * (deepslate under 0: one worked material, no blots of the land's strata).
 */
public final class HollowShape implements Shape {
	/**
	 * The land's top is read every this many blocks (the noise's own cell width; every 8 left real cliffs under 3 blocks of
	 * rock), within {@value #SAMPLED} of the middle only (nothing is taken past the dome): about 2 800 samples, 0.3 to 0.6
	 * s once per start on a worldgen thread on the dev Mac.
	 */
	public static final int GRID = 4;
	/** The grid's half extent from the origin (blocks): the box is {@value #EXTENT} + 8 each way. */
	public static final int EXTENT = 120;
	/** The grid is read this far from the middle (the dome's reach and the side shell's). */
	public static final int SAMPLED = 120;
	/** The inside closes this far from the middle, rising at most {@value #DOME_SLOPE} a block toward the middle. */
	public static final int DOME = 108;
	public static final double DOME_SLOPE = 1.6;
	/** The lowest terrace's height; each next one is {@value #TERRACE} higher. */
	public static final int MIN_HEIGHT = 6;
	public static final int TERRACE = 5;
	/** Half the dirt square's side (15 x 15). */
	public static final int GROVE = 7;
	/** The square stands where the inside, a grid step round it, is this tall at least; else there is no start. */
	public static final int GROVE_MIN_HEIGHT = 21;
	/** And at most this far from the middle. */
	public static final int GROVE_REACH = 72;
	/** The way in at most this long through rock, its cut in front at most {@value #APPROACH_MAX}, its steps down {@value #STEPS_MAX}. */
	public static final int TUNNEL_MAX = 48;
	public static final int APPROACH_MAX = 24;
	public static final int STEPS_MAX = 8;
	/** Rock over the way in's opening, at its mouth (blocks). */
	public static final int LINTEL = 3;
	/** A column's widening into the ceiling: this many steps, on columns this tall and more. */
	public static final int CAPITAL = 3;
	public static final int CAPITAL_FROM = 16;
	/** One ceiling cell in this many has a stub hanging from it. */
	public static final int STUB_ONE_IN = 70;
	/** The high opening: a shaft straight up through at most {@value #WINDOW_SHELL_MAX} of rock, at least {@value #WINDOW_MIN_UP} over the floor. */
	public static final int WINDOW_MIN_UP = 40;
	public static final int WINDOW_SHELL_MAX = 16;
	/** The land must fall at least this much over 4 blocks beside it (a side, not a flat top; steeper is preferred, so it shows from below). */
	public static final int WINDOW_STEEP = 2;
	/** The pool under the high opening: this wide each way from its middle (5 x 5; version 2: 7 x 7), two deep. */
	public static final int POOL_HALF = 2;
	/** The shape's current version (R12-2): 2, a cold landing is powder snow (R12-1), the opening 5 x 5 by the nave, the floor plain (R11-8). */
	public static final int VERSION = 2;

	/** A grid point not read. */
	private static final int UNKNOWN = Integer.MIN_VALUE / 4;

	private static final int BIT_WINDOW = 1;
	private static final int BIT_SPRUCE = 2;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
	private static final BlockState PODZOL = Blocks.PODZOL.defaultBlockState();
	private static final BlockState ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState POWDER_SNOW = Blocks.POWDER_SNOW.defaultBlockState();
	private static final BlockState DEAD_BUSH = Blocks.DEAD_BUSH.defaultBlockState();
	private static final Draw.Mix PATCH = Draw.Mix.of(COARSE_DIRT, 5).and(PODZOL, 4).and(ROOTED_DIRT, 1);

	/** One bare stem: where it stands in the square ({@code a} across, {@code b} along the way in), how tall, its seed. */
	public record Stem(int a, int b, int height, long key) {
	}

	/** The way in: its lane (the left column's offset across), where the tunnel starts and ends, the cut and the steps. */
	record WayIn(int lane, int inner, int mouth, int approach, int steps) {
	}

	/**
	 * The high opening: its middle column, and for each of its 3 x 3 columns (row by row) the first cell over the inside
	 * and the last cell it takes (two over the land's top there); the way the land falls from it.
	 */
	record Window(int x, int z, int[] bottoms, int[] tops, Direction downhill) {
	}

	private final Start start;
	/** The version it is built as. */
	private final int version;
	private final int cx;
	private final int yf;
	private final int cz;
	private final Direction facing;
	private final Direction right;
	private final long seed;
	private final int shellTop;
	private final int shellSide;
	private final int pillarEvery;
	private final int stems;
	private final boolean spruce;
	private final boolean windowWanted;

	private final int n;
	private final int gx0;
	private final int gz0;
	/** The land's top at each grid point. */
	private final int[] surface;
	/** The highest cell the inside may take at each grid point. */
	private final int[] ceiling;
	/** The inside's terraced height at each grid point (0: none). */
	private final int[] height;
	/** The grid points of the part that is taken. */
	private final boolean[] taken;
	private final int gx;
	private final int gz;
	private final boolean valid;
	private final int pillarHalf;
	private final int pillarK;
	/** Each column's width by its grid index (0: none there). */
	private final byte[] pillars;
	private final @Nullable WayIn way;
	private final @Nullable Window window;
	private final List<BlockPos> pool;
	/** The landing under the high opening is a drift of powder snow (version 2, where the land there is cold), not water. */
	private final boolean snowLanding;
	/** The high opening's half width (1: 3 x 3; version 2: 2, 5 x 5) and the landing's (2; version 2: 3). */
	private final int shaftHalf;
	private final int poolHalf;
	/** How many points of the land's top were read (R12-5). */
	private int samples;
	private final List<Stem> stemList;
	private final List<BlockPos> bushes;
	private final int maxTop;
	private final BoundingBox box;
	private final byte[] coarse;

	public HollowShape(Start start, Terrain terrain) {
		this(start, terrain, false);
	}

	/** Tests (R12-5): the same shape read from every point of the grid, the way it was before. */
	public static HollowShape readingEveryPoint(Start start, Terrain terrain) {
		return new HollowShape(start, terrain, true);
	}

	private HollowShape(Start start, Terrain terrain, boolean everyPoint) {
		this.start = start;
		this.version = start.versionOr(VERSION);
		this.shaftHalf = version >= 2 ? 2 : 1;
		this.poolHalf = version >= 2 ? 3 : POOL_HALF;
		this.cx = start.origin().getX();
		this.yf = start.origin().getY();
		this.cz = start.origin().getZ();
		this.facing = start.facing();
		this.right = facing.getClockWise();
		this.seed = start.seed();
		int variant = start.variant();
		this.windowWanted = window(variant);
		this.spruce = spruce(variant);
		this.shellTop = shellTop(variant);
		this.shellSide = shellSide(variant);
		this.pillarEvery = pillarEvery(variant);
		this.stems = stems(variant);
		this.n = 2 * EXTENT / GRID + 1;
		this.gx0 = cx - EXTENT;
		this.gz0 = cz - EXTENT;
		this.surface = surface(terrain, everyPoint);
		this.ceiling = ceilings();
		this.height = new int[n * n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int x = gx0 + i * GRID;
				int z = gz0 + j * GRID;
				height[i * n + j] = terrace(Math.min(ceiling[i * n + j], domeTop(x, z)) - yf);
			}
		}
		int grovePoint = grovePoint();
		this.gx = grovePoint < 0 ? cx : gx0 + (grovePoint / n) * GRID;
		this.gz = grovePoint < 0 ? cz : gz0 + (grovePoint % n) * GRID;
		this.taken = grovePoint < 0 ? new boolean[n * n] : flood(grovePoint);
		this.pillarHalf = pillarEvery / 2;
		this.pillarK = pillarEvery <= 0 ? 0 : 2 * (EXTENT + 8) / pillarEvery + 2;
		this.pillars = new byte[(2 * pillarK + 1) * (2 * pillarK + 1)];
		if (pillarEvery > 0) {
			placePillars();
		}
		this.way = grovePoint < 0 ? null : findWayIn(terrain);
		this.valid = grovePoint >= 0 && way != null;
		this.window = valid && windowWanted ? findWindow(terrain) : null;
		this.pool = window == null ? List.of() : pool(window);
		this.snowLanding = version >= 2 && !pool.isEmpty() && coldLanding(terrain, pool, yf);
		this.stemList = valid ? placeStems() : List.of();
		this.bushes = valid ? placeBushes() : List.of();
		int top = yf;
		for (int k = 0; k < n * n; k++) {
			if (taken[k]) {
				top = Math.max(top, yf + height[k]);
			}
		}
		this.maxTop = top;
		int high = top;
		for (BlockPos cell : windowCells()) {
			high = Math.max(high, cell.getY());
		}
		this.box = new BoundingBox(cx - EXTENT - 8, yf - 20, cz - EXTENT - 8, cx + EXTENT + 7, Math.max(high, yf + 24) + Seal.REACH + 2, cz + EXTENT + 7);
		this.coarse = coarse();
	}

	// --- the land's top (R12-5) ---

	/** True for a grid point within the dome's reach (past it nothing is taken: those points are never read). */
	private boolean sampled(int i, int j) {
		int dx = i * GRID - EXTENT;
		int dz = j * GRID - EXTENT;
		return dx * dx + dz * dz <= SAMPLED * SAMPLED;
	}

	/**
	 * The land's top at the grid points that can matter, the same shape from fewer reads (R12-5: about 2 800 before): every
	 * other point first; then round every point read whose top is high enough for the inside to stand under it ({@code yf}
	 * + {@value #MIN_HEIGHT} + the top shell), every point within the reach of a ceiling and its neighbours (two grid steps
	 * and the side shell's), and again round each of those that is high enough, until none is left. A point never read is
	 * "unknown" (deep): no point the inside, its ceilings, its columns, the square, the way in or the opening read lies that
	 * near any, so nothing built depends on it (tested against reading every point). If no point read first within the
	 * square's reach is high enough for the square, nothing more is read: no square fits, so no start.
	 */
	private int[] surface(Terrain terrain, boolean everyPoint) {
		int[] out = new int[n * n];
		java.util.Arrays.fill(out, UNKNOWN);
		boolean[] read = new boolean[n * n];
		boolean[] queued = new boolean[n * n];
		int[] queue = new int[n * n];
		int tail = 0;
		int high = yf + MIN_HEIGHT + shellTop;
		int squareHigh = yf + GROVE_MIN_HEIGHT + shellTop;
		int squareReach = GROVE_REACH + 6 * GRID;
		boolean square = false;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (!sampled(i, j) || !everyPoint && ((i | j) & 1) != 0) {
					continue;
				}
				int k = i * n + j;
				out[k] = terrain.ground(gx0 + i * GRID, gz0 + j * GRID);
				read[k] = true;
				samples++;
				if (out[k] >= high) {
					queued[k] = true;
					queue[tail++] = k;
				}
				int dx = i * GRID - EXTENT;
				int dz = j * GRID - EXTENT;
				square |= out[k] >= squareHigh && dx * dx + dz * dz <= squareReach * squareReach;
			}
		}
		if (everyPoint || !square) {
			return out;
		}
		int d = 2 + Math.ceilDiv(shellSide + 2, GRID);
		for (int head = 0; head < tail; head++) {
			int k = queue[head];
			int i = k / n;
			int j = k % n;
			for (int ii = Math.max(0, i - d); ii <= Math.min(n - 1, i + d); ii++) {
				for (int jj = Math.max(0, j - d); jj <= Math.min(n - 1, j + d); jj++) {
					int kk = ii * n + jj;
					if (read[kk] || !sampled(ii, jj)) {
						continue;
					}
					out[kk] = terrain.ground(gx0 + ii * GRID, gz0 + jj * GRID);
					read[kk] = true;
					samples++;
					if (out[kk] >= high && !queued[kk]) {
						queued[kk] = true;
						queue[tail++] = kk;
					}
				}
			}
		}
		return out;
	}

	/** How many points of the land's top it read (R12-5). */
	public int samples() {
		return samples;
	}

	// --- the variant ---

	/** The variant for these choices (siting reads the config; a start keeps them, so a later config change never moves a cell). */
	public static int variant(boolean window, boolean spruce, int shellTop, int shellSide, int pillarEvery, int stems) {
		int top = Math.clamp(shellTop, 3, 15);
		int side = Math.clamp(shellSide, 3, 15);
		int every = pillarEvery <= 0 ? 0 : Math.clamp(pillarEvery, 8, 31);
		int count = Math.clamp(stems, 0, 31);
		return (window ? BIT_WINDOW : 0) | (spruce ? BIT_SPRUCE : 0) | top << 2 | side << 6 | every << 10 | count << 15;
	}

	public static boolean window(int variant) {
		return (variant & BIT_WINDOW) != 0;
	}

	public static boolean spruce(int variant) {
		return (variant & BIT_SPRUCE) != 0;
	}

	public static int shellTop(int variant) {
		return Math.max(3, variant >> 2 & 15);
	}

	public static int shellSide(int variant) {
		return Math.max(3, variant >> 6 & 15);
	}

	public static int pillarEvery(int variant) {
		int every = variant >> 10 & 31;
		return every < 8 ? 0 : every;
	}

	public static int stems(int variant) {
		return variant >> 15 & 31;
	}

	// --- the inside's height ---

	/** Terraced: under {@value #MIN_HEIGHT} nothing, else {@value #MIN_HEIGHT} and whole {@value #TERRACE}s more. */
	public static int terrace(int cells) {
		return cells < MIN_HEIGHT ? 0 : MIN_HEIGHT + (cells - MIN_HEIGHT) / TERRACE * TERRACE;
	}

	/** The highest cell the dome lets the inside take in this column. */
	private int domeTop(int x, int z) {
		double r = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
		return yf + (int) Math.floor(DOME_SLOPE * (DOME - r));
	}

	/**
	 * The highest cell the inside may take at each grid point: {@code d} under its own top (and up to 3 more where the land
	 * is steep), and under every top round it by what the side shell asks at that distance ({@code e} + 2, for what the
	 * grid cannot see between its points).
	 */
	private int[] ceilings() {
		int[] out = new int[n * n];
		int reach = shellSide + 2;
		int cells = (reach + GRID - 1) / GRID;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				// Where the land is steep the noise can dip between grid points (the gallery's real-noise test found 4):
				// keep up to 3 more under a steep point, a block for every 5 the land changes to its neighbours.
				int steep = 0;
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int ii = Math.clamp(i + d[0], 0, n - 1);
					int jj = Math.clamp(j + d[1], 0, n - 1);
					steep = Math.max(steep, Math.abs(surface[i * n + j] - surface[ii * n + jj]));
				}
				int c = surface[i * n + j] - shellTop - Math.min(3, steep / 5);
				for (int di = -cells; di <= cells; di++) {
					for (int dj = -cells; dj <= cells; dj++) {
						int ii = i + di;
						int jj = j + dj;
						if ((di == 0 && dj == 0) || ii < 0 || jj < 0 || ii >= n || jj >= n) {
							continue;
						}
						double h = GRID * Math.sqrt(di * di + dj * dj);
						if (h >= reach) {
							continue;
						}
						c = Math.min(c, surface[ii * n + jj] - (int) Math.ceil(Math.sqrt(reach * reach - h * h)));
					}
				}
				// The grid's edge: nothing is known past it, so nothing is taken there.
				if (i == 0 || j == 0 || i == n - 1 || j == n - 1) {
					c = Integer.MIN_VALUE / 2;
				}
				out[i * n + j] = c;
			}
		}
		return out;
	}

	/** The grid point under the tallest part: the most height kept a grid step round it, near enough the middle; -1 if none. */
	private int grovePoint() {
		int best = -1;
		int bestScore = GROVE_MIN_HEIGHT - 1;
		double bestR = Double.MAX_VALUE;
		int round = Math.max(1, 12 / GRID);
		for (int i = round; i < n - round; i++) {
			for (int j = round; j < n - round; j++) {
				int x = gx0 + i * GRID;
				int z = gz0 + j * GRID;
				double r = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
				if (r > GROVE_REACH) {
					continue;
				}
				int score = Integer.MAX_VALUE;
				for (int di = -round; di <= round && score > bestScore - 1; di++) {
					for (int dj = -round; dj <= round; dj++) {
						score = Math.min(score, height[(i + di) * n + j + dj]);
					}
				}
				if (score > bestScore || score == bestScore && best >= 0 && r < bestR) {
					best = i * n + j;
					bestScore = score;
					bestR = r;
				}
			}
		}
		return best;
	}

	/** The grid points joined to the square's (eight ways), where the inside has any height. */
	private boolean[] flood(int from) {
		boolean[] out = new boolean[n * n];
		int[] queue = new int[n * n];
		int head = 0;
		int tail = 0;
		out[from] = true;
		queue[tail++] = from;
		while (head < tail) {
			int k = queue[head++];
			int i = k / n;
			int j = k % n;
			for (int di = -1; di <= 1; di++) {
				for (int dj = -1; dj <= 1; dj++) {
					int ii = i + di;
					int jj = j + dj;
					if (ii < 0 || jj < 0 || ii >= n || jj >= n) {
						continue;
					}
					int kk = ii * n + jj;
					if (!out[kk] && height[kk] > 0) {
						out[kk] = true;
						queue[tail++] = kk;
					}
				}
			}
		}
		return out;
	}

	/**
	 * The columns: one on every crossing of the grid round the square (its rows along the way in), wide by how tall the
	 * inside is there (3 under 32, 5 under 64, else 7); none near the square (a clearing round it), none in the nave (the
	 * rows either side of the square's line, from the square to the way in), none where nothing is taken.
	 */
	private void placePillars() {
		for (int ka = -pillarK; ka <= pillarK; ka++) {
			for (int kb = -pillarK; kb <= pillarK; kb++) {
				int a = pillarHalf + ka * pillarEvery;
				int b = pillarHalf + kb * pillarEvery;
				if (Math.max(Math.abs(a), Math.abs(b)) < GROVE + 8) {
					continue;
				}
				// The nave: the two rows either side of the square's line are left out from the square to the way in, so
				// the way in opens on one long empty floor with the square at its end.
				if (Math.abs(a) <= pillarEvery / 2 + 1 && b > 0) {
					continue;
				}
				BlockPos c = at(a, 0, b);
				int h = rawHeight(c.getX(), c.getZ());
				if (h <= 0) {
					continue;
				}
				int width = h < 32 ? 3 : h < 64 ? 5 : 7;
				pillars[(ka + pillarK) * (2 * pillarK + 1) + kb + pillarK] = (byte) width;
			}
		}
	}

	/** The width of the column with these indices (0: none). */
	private int pillarWidth(int ka, int kb) {
		if (ka < -pillarK || kb < -pillarK || ka > pillarK || kb > pillarK) {
			return 0;
		}
		return pillars[(ka + pillarK) * (2 * pillarK + 1) + kb + pillarK];
	}

	/** The terraced height of the inside in this column, before columns and stubs (0: not taken). */
	private int rawHeight(int x, int z) {
		int fx = x - gx0;
		int fz = z - gz0;
		if (fx < 0 || fz < 0 || fx >= (n - 1) * GRID || fz >= (n - 1) * GRID) {
			return 0;
		}
		int i = fx / GRID;
		int j = fz / GRID;
		int k = i * n + j;
		if (!(taken[k] || taken[k + n] || taken[k + 1] || taken[k + n + 1])) {
			return 0;
		}
		double tx = (fx - i * GRID) / (double) GRID;
		double tz = (fz - j * GRID) / (double) GRID;
		double top = (ceiling[k] * (1 - tx) + ceiling[k + n] * tx) * (1 - tz) + (ceiling[k + 1] * (1 - tx) + ceiling[k + n + 1] * tx) * tz;
		return terrace((int) Math.floor(Math.min(top, domeTop(x, z))) - yf);
	}

	/**
	 * The cells the inside takes in this column, as {@link Seal#range}, or {@link Seal#NONE}: from over the floor up to
	 * its terraced height, less a column standing here, a column's widening into the ceiling, its foot, or a stub.
	 */
	public long column(int x, int z) {
		int h = rawHeight(x, z);
		if (h <= 0) {
			return Seal.NONE;
		}
		int lo = yf + 1;
		int hi = yf + h;
		boolean capital = false;
		if (pillarEvery > 0) {
			int a = lateral(x, z);
			int b = along(x, z);
			int ka = Math.floorDiv(a - pillarHalf + pillarEvery / 2, pillarEvery);
			int kb = Math.floorDiv(b - pillarHalf + pillarEvery / 2, pillarEvery);
			int width = pillarWidth(ka, kb);
			if (width > 0) {
				int ring = Math.max(Math.abs(a - pillarHalf - ka * pillarEvery), Math.abs(b - pillarHalf - kb * pillarEvery)) - width / 2;
				if (ring <= 0) {
					return Seal.NONE;
				}
				if (ring <= CAPITAL && h >= CAPITAL_FROM) {
					hi -= CAPITAL + 1 - ring;
					capital = true;
				}
				if (ring == 1) {
					lo = yf + 2;
				}
			}
		}
		if (!capital && h >= CAPITAL_FROM && Draw.unit(seed ^ 0x57AB5L, x - cx, 0, z - cz) * STUB_ONE_IN < 1.0) {
			hi -= Draw.between(seed ^ 0x57AB5L, Hash.of(x - cx, z - cz), 1, 4);
		}
		return hi < lo ? Seal.NONE : Seal.range(lo, hi);
	}

	/** True for a cell the inside takes. */
	public boolean taken(int x, int y, int z) {
		long range = column(x, z);
		return range != Seal.NONE && y >= Seal.lo(range) && y <= Seal.hi(range);
	}

	// --- the frame (across and along the way in, from the square's middle) ---

	/** Across the way in from the square's middle (to the right looking out). */
	public int lateral(int x, int z) {
		return (x - gx) * right.getStepX() + (z - gz) * right.getStepZ();
	}

	/** Along the way in from the square's middle (out toward the facing). */
	public int along(int x, int z) {
		return (x - gx) * facing.getStepX() + (z - gz) * facing.getStepZ();
	}

	/** The cell {@code a} across, {@code b} along from the square's middle, at height {@code y}. */
	public BlockPos at(int a, int y, int b) {
		return new BlockPos(gx + right.getStepX() * a + facing.getStepX() * b, y, gz + right.getStepZ() * a + facing.getStepZ() * b);
	}

	// --- the way in ---

	/** True if both columns of a lane are taken with room to walk at the floor. */
	private boolean open(int a, int b) {
		for (int side = 0; side <= 1; side++) {
			BlockPos c = at(a + side, 0, b);
			long range = column(c.getX(), c.getZ());
			if (range == Seal.NONE || Seal.lo(range) > yf + 1 || Seal.hi(range) < yf + 2) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The way in: on the square's own line first (straight down the middle aisle to it), else the shortest of the lanes
	 * between the rows of columns either side; null if none reaches the open land within the limits, dry.
	 */
	private @Nullable WayIn findWayIn(Terrain terrain) {
		int step = pillarEvery > 0 ? pillarEvery : 7;
		WayIn best = null;
		int bestCost = Integer.MAX_VALUE;
		for (int k = 0; k * step <= 42; k++) {
			for (int sign : k == 0 ? new int[] {1} : new int[] {-1, 1}) {
				WayIn lane = lane(terrain, sign * k * step);
				if (lane == null) {
					continue;
				}
				if (k == 0) {
					return lane;
				}
				int cost = (lane.mouth() - lane.inner()) + (lane.approach() - lane.mouth()) + 2 * (lane.steps() - lane.approach());
				if (cost < bestCost) {
					best = lane;
					bestCost = cost;
				}
			}
		}
		return best;
	}

	private @Nullable WayIn lane(Terrain terrain, int lane) {
		int exit = -1;
		for (int b = 0; b <= EXTENT + 8; b++) {
			if (open(lane, b)) {
				exit = b;
			}
		}
		if (exit < 0) {
			return null;
		}
		int inner = exit + 1;
		int b = inner;
		while (lowest(terrain, lane, lane + 1, b) >= yf + 2 + LINTEL) {
			b++;
			if (b - inner > TUNNEL_MAX) {
				return null;
			}
		}
		int mouth = b - 1;
		if (mouth - inner < 1) {
			return null;
		}
		while (highest(terrain, lane, lane + 1, b) > yf) {
			b++;
			if (b - mouth > APPROACH_MAX) {
				return null;
			}
		}
		int approach = b - 1;
		int floor = yf;
		while (lowest(terrain, lane, lane + 1, b) < floor - 1) {
			floor--;
			b++;
			if (b - approach > STEPS_MAX) {
				return null;
			}
		}
		int steps = b - 1;
		// Dry: no water over the land from the mouth to a block past the last step, nor beside it.
		for (int d = mouth; d <= steps + 1; d++) {
			for (int a = lane - 1; a <= lane + 2; a++) {
				BlockPos c = at(a, 0, d);
				if (terrain.surface(c.getX(), c.getZ()) > terrain.ground(c.getX(), c.getZ())) {
					return null;
				}
			}
		}
		// Inside the box, with room for its foundations.
		BlockPos far = at(lane - 2, 0, steps + 2);
		BlockPos far2 = at(lane + 3, 0, steps + 2);
		if (Math.max(Math.abs(far.getX() - cx), Math.abs(far.getZ() - cz)) > EXTENT || Math.max(Math.abs(far2.getX() - cx), Math.abs(far2.getZ() - cz)) > EXTENT) {
			return null;
		}
		return new WayIn(lane, inner, mouth, approach, steps);
	}

	private int lowest(Terrain terrain, int a0, int a1, int b) {
		int out = Integer.MAX_VALUE;
		for (int a = a0; a <= a1; a++) {
			BlockPos c = at(a, 0, b);
			out = Math.min(out, terrain.ground(c.getX(), c.getZ()));
		}
		return out;
	}

	private int highest(Terrain terrain, int a0, int a1, int b) {
		int out = Integer.MIN_VALUE;
		for (int a = a0; a <= a1; a++) {
			BlockPos c = at(a, 0, b);
			out = Math.max(out, terrain.ground(c.getX(), c.getZ()));
		}
		return out;
	}

	/** The floor of the way out at {@code b} along: the floor's height to the cut's end, a block lower each step after. */
	private int laneFloor(int b) {
		WayIn w = way;
		if (w == null || b <= w.approach()) {
			return yf;
		}
		return yf - (b - w.approach());
	}

	// --- the high opening ---

	/** The grid's land top between its points (for the search only: the chosen line is checked against the terrain). */
	private double top(int x, int z) {
		int fx = Math.clamp(x - gx0, 0, (n - 1) * GRID - 1);
		int fz = Math.clamp(z - gz0, 0, (n - 1) * GRID - 1);
		int i = fx / GRID;
		int j = fz / GRID;
		int k = i * n + j;
		double tx = (fx - i * GRID) / (double) GRID;
		double tz = (fz - j * GRID) / (double) GRID;
		return (surface[k] * (1 - tx) + surface[k + n] * tx) * (1 - tz) + (surface[k + 1] * (1 - tx) + surface[k + n + 1] * tx) * tz;
	}

	/**
	 * The high opening: a 3 x 3 shaft straight up from the top of a tall part of the inside through the shell, on the
	 * mountain's side (it falls {@value #WINDOW_STEEP} or more over 4 blocks beside it; the steeper the better, so the hole
	 * shows from below as a dark notch), at least {@value #WINDOW_MIN_UP} over the floor, through at most {@value #WINDOW_SHELL_MAX}
	 * of rock, every column of it open down to the floor (the daylight falls on it) and the floor under it whole for the
	 * pool; not near the square. The steepest (up to 12 over 4: a cliff's very edge is no better) and highest 40 are checked
	 * against the terrain itself. Null if none.
	 */
	private @Nullable Window findWindow(Terrain terrain) {
		List<int[]> found = new ArrayList<>();
		for (int i = 1; i < n - 1; i++) {
			for (int j = 1; j < n - 1; j++) {
				int k = i * n + j;
				if (!taken[k] || height[k] < WINDOW_MIN_UP) {
					continue;
				}
				int x = gx0 + i * GRID;
				int z = gz0 + j * GRID;
				if (Math.max(Math.abs(lateral(x, z)), Math.abs(along(x, z))) <= GROVE + 8) {
					continue;
				}
				int steep = 0;
				int down = 0;
				int[][] around = {{n, 0}, {-n, 1}, {1, 2}, {-1, 3}};
				for (int[] d : around) {
					int fall = surface[k] - surface[k + d[0]];
					if (fall > steep) {
						steep = fall;
						down = d[1];
					}
				}
				if (steep < WINDOW_STEEP || !floorWhole(x, z, version >= 2 ? poolHalf : 1) || !openDown(x, z)) {
					continue;
				}
				int score = Math.min(steep, 12) * 3 + (height[k] - WINDOW_MIN_UP) / 2;
				// Version 2: the nearest the nave's line first (R11-8), so the daylight falls in front of the square.
				found.add(new int[] {version >= 2 ? -naveDistance(x, z) : score, x, z, down, score});
			}
		}
		found.sort((a, b) -> b[0] != a[0] ? Integer.compare(b[0], a[0]) : b[4] != a[4] ? Integer.compare(b[4], a[4])
				: a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[2], b[2]));
		Direction[] downs = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
		for (int c = 0; c < Math.min(40, found.size()); c++) {
			int[] f = found.get(c);
			Window w = checked(terrain, f[1], f[2], downs[f[3]]);
			if (w != null) {
				return w;
			}
		}
		return null;
	}

	/** How far this column is from the nave's line (from the square's middle to the way in's inner mouth), in blocks. */
	private int naveDistance(int x, int z) {
		WayIn w = way;
		double a = lateral(x, z);
		double b = along(x, z);
		double ea = w == null ? 0 : w.lane() + 0.5;
		double eb = w == null ? 0 : w.inner();
		double length2 = ea * ea + eb * eb;
		double t = length2 <= 0 ? 0 : Math.clamp((a * ea + b * eb) / length2, 0.0, 1.0);
		return (int) Math.round(Math.hypot(a - t * ea, b - t * eb));
	}

	/** True if every column within {@code half} of ({@code x}, {@code z}) is taken from the floor up. */
	private boolean floorWhole(int x, int z, int half) {
		for (int a = -half; a <= half; a++) {
			for (int b = -half; b <= half; b++) {
				long range = column(x + a, z + b);
				if (range == Seal.NONE || Seal.lo(range) != yf + 1) {
					return false;
				}
			}
		}
		return true;
	}

	/** True if the opening's columns round ({@code x}, {@code z}) are each taken from the floor to their top in one run (no stub, no column's widening). */
	private boolean openDown(int x, int z) {
		for (int a = -shaftHalf; a <= shaftHalf; a++) {
			for (int b = -shaftHalf; b <= shaftHalf; b++) {
				long range = column(x + a, z + b);
				if (range == Seal.NONE || Seal.lo(range) != yf + 1 || Seal.hi(range) - yf < WINDOW_MIN_UP) {
					return false;
				}
			}
		}
		return true;
	}

	/** The shaft against the terrain itself: rock over every column of it, no more than {@value #WINDOW_SHELL_MAX} of it. */
	private @Nullable Window checked(Terrain terrain, int x, int z, Direction downhill) {
		int side = 2 * shaftHalf + 1;
		int[] bottoms = new int[side * side];
		int[] tops = new int[side * side];
		int c = 0;
		for (int a = -shaftHalf; a <= shaftHalf; a++) {
			for (int b = -shaftHalf; b <= shaftHalf; b++) {
				long range = column(x + a, z + b);
				int ground = terrain.ground(x + a, z + b);
				int hi = Seal.hi(range);
				if (ground - hi < 3 || ground - hi > WINDOW_SHELL_MAX) {
					return null;
				}
				bottoms[c] = hi + 1;
				tops[c] = ground + 2;
				c++;
			}
		}
		return new Window(x, z, bottoms, tops, downhill);
	}

	/**
	 * The landing's cells under the opening: up to 5 x 5 (version 2: 7 x 7) at the floor's height, wherever the floor is
	 * whole (at least the opening's own columns; version 2 asks the whole 7 x 7).
	 */
	private List<BlockPos> pool(Window w) {
		List<BlockPos> out = new ArrayList<>();
		for (int a = -poolHalf; a <= poolHalf; a++) {
			for (int b = -poolHalf; b <= poolHalf; b++) {
				long range = column(w.x() + a, w.z() + b);
				if (range != Seal.NONE && Seal.lo(range) == yf + 1) {
					out.add(new BlockPos(w.x() + a, yf, w.z() + b));
				}
			}
		}
		return List.copyOf(out);
	}

	/**
	 * True if water on any of these landing cells could freeze: a biome that may stand at the cell (any of the eight noise
	 * cells vanilla's biome lookup chooses between for it, at the water's height and the cell over it, where the freeze
	 * step and the weather read it) is cold enough to snow there ({@code Biome.coldEnoughToSnow}, height and all). The
	 * noise's own numbers, so worldgen, the gallery and a rebuilt place agree.
	 */
	static boolean coldLanding(Terrain terrain, List<BlockPos> cells, int y) {
		java.util.Set<Long> asked = new java.util.HashSet<>();
		for (BlockPos cell : cells) {
			BlockPos at = new BlockPos(cell.getX(), y, cell.getZ());
			for (int dy = 0; dy <= 1; dy++) {
				int qx0 = (cell.getX() - 2) >> 2;
				int qy0 = (y + dy - 2) >> 2;
				int qz0 = (cell.getZ() - 2) >> 2;
				for (int qx = qx0; qx <= qx0 + 1; qx++) {
					for (int qy = qy0; qy <= qy0 + 1; qy++) {
						for (int qz = qz0; qz <= qz0 + 1; qz++) {
							if (!asked.add(BlockPos.asLong(qx, qy, qz) ^ at.asLong() * 31L)) {
								continue;
							}
							if (terrain.biome(qx << 2, qy << 2, qz << 2).value().coldEnoughToSnow(at, terrain.seaLevel())) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	// --- the square and its stems ---

	private List<Stem> placeStems() {
		List<Stem> out = new ArrayList<>();
		for (int tryNo = 0; tryNo < 400 && out.size() < stems; tryNo++) {
			int a = Draw.between(seed, 0x5713L + tryNo * 3L, -GROVE + 1, GROVE - 1);
			int b = Draw.between(seed, 0x5714L + tryNo * 3L, -GROVE + 1, GROVE - 1);
			boolean near = false;
			for (Stem s : out) {
				near |= (s.a() - a) * (s.a() - a) + (s.b() - b) * (s.b() - b) < 9;
			}
			if (near || way != null && way.lane() == 0 && a >= -1 && a <= 2 && b > 0) {
				continue;
			}
			out.add(new Stem(a, b, Draw.between(seed, 0x5715L + tryNo * 3L, 6, 13), Hash.of(seed, 0x57E3L, tryNo)));
		}
		return List.copyOf(out);
	}

	private List<BlockPos> placeBushes() {
		List<BlockPos> out = new ArrayList<>();
		for (int tryNo = 0; tryNo < 40 && out.size() < 5; tryNo++) {
			int a = Draw.between(seed, 0xB054L + tryNo * 2L, -GROVE, GROVE);
			int b = Draw.between(seed, 0xB055L + tryNo * 2L, -GROVE, GROVE);
			boolean clash = false;
			for (Stem s : stemList) {
				clash |= Math.abs(s.a() - a) <= 1 && Math.abs(s.b() - b) <= 1;
			}
			if (!clash) {
				out.add(at(a, yf + 1, b));
			}
		}
		return List.copyOf(out);
	}

	// --- what it is (pure: tests and the answers ask these) ---

	/** False if no square or no way in fits: siting makes no start then. */
	public boolean valid() {
		return valid;
	}

	/** What it was made from. */
	public Start start() {
		return start;
	}

	/** The floor's height (the stone of the floor; the inside starts a block over it). */
	public int floorY() {
		return yf;
	}

	public int centerX() {
		return cx;
	}

	public int centerZ() {
		return cz;
	}

	public Direction facing() {
		return facing;
	}

	/** The middle of the dirt square (the floor's cell). */
	public BlockPos groveCenter() {
		return new BlockPos(gx, yf, gz);
	}

	public List<Stem> stems() {
		return stemList;
	}

	/** A stem's foot (the first cell over the floor). */
	public BlockPos stemFoot(Stem stem) {
		return at(stem.a(), yf + 1, stem.b());
	}

	public boolean spruce() {
		return spruce;
	}

	/** The tallest cell the inside takes. */
	public int maxTop() {
		return maxTop;
	}

	/** The inside's terraced height at the square's middle. */
	public int groveHeight() {
		return rawHeight(gx, gz);
	}

	/** The grid point heights (for tests and the debug line). */
	public int heightAtGrid(int i, int j) {
		return taken[i * n + j] ? height[i * n + j] : 0;
	}

	/** The way in's cells, the tunnel only (2 wide, 2 high), from the inner end out to the mouth. Empty if none. */
	public List<BlockPos> wayIn() {
		List<BlockPos> out = new ArrayList<>();
		WayIn w = way;
		if (w == null) {
			return out;
		}
		for (int b = w.inner(); b <= w.mouth(); b++) {
			for (int side = 0; side <= 1; side++) {
				for (int up = 1; up <= 2; up++) {
					out.add(at(w.lane() + side, yf + up, b));
				}
			}
		}
		return out;
	}

	/** The inner mouth's 2 x 2: the tunnel's innermost cells, next to the inside. Empty if none. */
	public List<BlockPos> innerMouth() {
		WayIn w = way;
		if (w == null) {
			return List.of();
		}
		return List.of(at(w.lane(), yf + 1, w.inner()), at(w.lane() + 1, yf + 1, w.inner()), at(w.lane(), yf + 2, w.inner()), at(w.lane() + 1, yf + 2, w.inner()));
	}

	/** How long the tunnel is through the rock (cells). */
	public int tunnelLength() {
		WayIn w = way;
		return w == null ? 0 : w.mouth() - w.inner() + 1;
	}

	/** The lane's offset across (0: on the square's own line). */
	public int lane() {
		WayIn w = way;
		return w == null ? 0 : w.lane();
	}

	/** The cut in front of the mouth and the steps after it: their columns' floor cells. */
	public List<BlockPos> approach() {
		List<BlockPos> out = new ArrayList<>();
		WayIn w = way;
		if (w == null) {
			return out;
		}
		for (int b = w.mouth() + 1; b <= w.steps(); b++) {
			for (int a = w.lane(); a <= w.lane() + 1; a++) {
				out.add(at(a, laneFloor(b), b));
			}
		}
		return out;
	}

	public boolean hasWindow() {
		return window != null;
	}

	/** The high opening's cells (3 x 3 columns, version 2 5 x 5, from just over the inside up through the land's top), empty if none. */
	public List<BlockPos> windowCells() {
		List<BlockPos> out = new ArrayList<>();
		Window w = window;
		if (w == null) {
			return out;
		}
		int c = 0;
		for (int a = -shaftHalf; a <= shaftHalf; a++) {
			for (int b = -shaftHalf; b <= shaftHalf; b++) {
				for (int y = w.bottoms()[c]; y <= w.tops()[c]; y++) {
					out.add(new BlockPos(w.x() + a, y, w.z() + b));
				}
				c++;
			}
		}
		return out;
	}

	/** The middle of the high opening where it comes out on the land (its middle column's top), or null. */
	public @Nullable BlockPos windowOuter() {
		Window w = window;
		return w == null ? null : new BlockPos(w.x(), w.tops()[shaftHalf * (2 * shaftHalf + 1) + shaftHalf] - 2, w.z());
	}

	/** The high opening's half width (1: 3 x 3; version 2: 2, 5 x 5). */
	public int windowHalf() {
		return shaftHalf;
	}

	/** The middle of the high opening's foot, over the pool (the first cell over the floor), or null. */
	public @Nullable BlockPos windowFoot() {
		Window w = window;
		return w == null ? null : new BlockPos(w.x(), yf + 1, w.z());
	}

	/** The way the land falls from the high opening (where it shows from below), or null. */
	public @Nullable Direction windowFacing() {
		Window w = window;
		return w == null ? null : w.downhill();
	}

	/** The pool's surface cells (the floor's height), empty without an opening. */
	public List<BlockPos> pool() {
		return pool;
	}

	/** True if the landing under the high opening is a drift of powder snow on the floor over {@link #pool}'s cells, not water in them. */
	public boolean snowLanding() {
		return snowLanding;
	}

	/** The version it is built as (R12-2). */
	public int version() {
		return version;
	}

	// --- Shape ---

	@Override
	public BoundingBox box() {
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	/** The middle of the dirt square, a cell over the floor. */
	@Override
	public BlockPos anchor() {
		return new BlockPos(gx, yf + 1, gz);
	}

	/** The mouth: the tunnel's outermost cell, lower left. */
	@Override
	public BlockPos entrance() {
		WayIn w = way;
		return w == null ? new BlockPos(cx, yf + 1, cz) : at(w.lane(), yf + 1, w.mouth());
	}

	@Override
	public Direction entranceFacing() {
		return facing;
	}

	/** Inside: the cells taken (never the rock, the columns, the way in or the opening). */
	@Override
	public Interior interior() {
		return this::taken;
	}

	/**
	 * The floor's height ({@code y}), the inside's height on a grid of 8 x 8 columns over the box ({@code c}, unsigned
	 * bytes, the most of any point round each cell: a little generous at the edges) and the inner mouth ({@code m}): the
	 * place's inside after a restart, and 18b's mouth, without the terrain.
	 */
	@Override
	public CompoundTag extra() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("y", yf);
		tag.putByteArray("c", coarse.clone());
		List<BlockPos> mouth = innerMouth();
		if (!mouth.isEmpty()) {
			tag.putLong("m", mouth.getFirst().asLong());
		}
		return tag;
	}

	/** The cells of the coarse grid per side ({@link #extra}). */
	public static final int COARSE = 8;
	public static final int COARSE_SIDE = 2 * (EXTENT + 8) / COARSE;

	private byte[] coarse() {
		byte[] out = new byte[COARSE_SIDE * COARSE_SIDE];
		int x0 = cx - EXTENT - 8;
		int z0 = cz - EXTENT - 8;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int k = i * n + j;
				if (!taken[k] || height[k] <= 0) {
					continue;
				}
				int px = gx0 + i * GRID;
				int pz = gz0 + j * GRID;
				for (int x = px - GRID; x <= px + GRID; x += GRID) {
					for (int z = pz - GRID; z <= pz + GRID; z += GRID) {
						int ci = Math.floorDiv(x - x0, COARSE);
						int cj = Math.floorDiv(z - z0, COARSE);
						if (ci < 0 || cj < 0 || ci >= COARSE_SIDE || cj >= COARSE_SIDE) {
							continue;
						}
						int c = ci * COARSE_SIDE + cj;
						out[c] = (byte) Math.max(out[c] & 0xFF, Math.min(255, height[k]));
					}
				}
			}
		}
		return out;
	}

	/**
	 * The inside of a recorded one from its {@link #extra} (no terrain, no shape): its box, the coarse grid's cell over the
	 * floor and up to its height. Null if the tag does not hold one.
	 */
	public static @Nullable Interior interiorOf(Start start, BoundingBox box, CompoundTag extra) {
		byte[] grid = extra.getByteArray("c").orElse(null);
		if (grid == null || grid.length != COARSE_SIDE * COARSE_SIDE) {
			return null;
		}
		int floor = extra.getIntOr("y", start.origin().getY());
		int x0 = start.origin().getX() - EXTENT - 8;
		int z0 = start.origin().getZ() - EXTENT - 8;
		byte[] cells = grid.clone();
		int bx0 = box.minX();
		int bx1 = box.maxX();
		int bz0 = box.minZ();
		int bz1 = box.maxZ();
		return (x, y, z) -> {
			if (x < bx0 || x > bx1 || z < bz0 || z > bz1 || y <= floor) {
				return false;
			}
			int ci = (x - x0) >> 3;
			int cj = (z - z0) >> 3;
			if (ci < 0 || cj < 0 || ci >= COARSE_SIDE || cj >= COARSE_SIDE) {
				return false;
			}
			return y <= floor + (cells[ci * COARSE_SIDE + cj] & 0xFF);
		};
	}

	/** The inner mouth's lower left cell kept in a recorded one's {@link #extra}, if any. */
	public static Optional<BlockPos> innerMouthOf(CompoundTag extra) {
		return extra.getLong("m").map(BlockPos::of);
	}

	// --- fill ---

	@Override
	public void fill(Writer w, BoundingBox clip) {
		if (!valid) {
			return;
		}
		// The columns round the clip, once (the seal asks each 25 times).
		int x0 = clip.minX() - Seal.REACH;
		int z0 = clip.minZ() - Seal.REACH;
		int sx = clip.getXSpan() + 2 * Seal.REACH;
		int sz = clip.getZSpan() + 2 * Seal.REACH;
		long[] cols = new long[sx * sz];
		long[] seal = new long[sx * sz];
		for (int i = 0; i < sx; i++) {
			for (int j = 0; j < sz; j++) {
				int x = x0 + i;
				int z = z0 + j;
				long c = column(x, z);
				cols[i * sz + j] = c;
				seal[i * sz + j] = sealRange(x, z, c);
			}
		}
		Seal.Columns sealed = (x, z) -> {
			int i = x - x0;
			int j = z - z0;
			return i >= 0 && j >= 0 && i < sx && j < sz ? seal[i * sz + j] : sealRange(x, z, column(x, z));
		};
		// 1. The seal, round the inside and the way in's inner part.
		Seal.around(w, clip, sealed, Seal.REACH, (x, y, z) -> y < 0 ? DEEPSLATE : STONE);
		// 2. The inside taken.
		for (int x = clip.minX(); x <= clip.maxX(); x++) {
			for (int z = clip.minZ(); z <= clip.maxZ(); z++) {
				long c = cols[(x - x0) * sz + z - z0];
				if (c == Seal.NONE) {
					continue;
				}
				for (int y = Math.max(Seal.lo(c), clip.minY()); y <= Math.min(Seal.hi(c), clip.maxY()); y++) {
					w.set(x, y, z, AIR);
				}
			}
		}
		// 2b. Version 2: the walking floor's top two layers one worked material (R11-8).
		if (version >= 2) {
			for (int x = clip.minX(); x <= clip.maxX(); x++) {
				for (int z = clip.minZ(); z <= clip.maxZ(); z++) {
					long c = cols[(x - x0) * sz + z - z0];
					if (c == Seal.NONE || Seal.lo(c) != yf + 1) {
						continue;
					}
					for (int y = yf - 1; y <= yf; y++) {
						if (y >= clip.minY() && y <= clip.maxY()) {
							w.set(x, y, z, y < 0 ? DEEPSLATE : STONE);
						}
					}
				}
			}
		}
		// 3. The square, its stems and a few dead bushes.
		grove(w);
		// 4. The way in, its cut and its steps.
		openWay(w);
		// 5. The high opening and the pool under it.
		openWindow(w);
	}

	/** What the seal keeps out of in a column: the inside, and the tunnel but its last three cells (the seal would fill the air in front). */
	private long sealRange(int x, int z, long column) {
		WayIn w = way;
		if (w == null) {
			return column;
		}
		int a = lateral(x, z) - w.lane();
		int b = along(x, z);
		if (a < 0 || a > 1 || b < w.inner() || b > w.mouth() - 3) {
			return column;
		}
		if (column == Seal.NONE) {
			return Seal.range(yf + 1, yf + 2);
		}
		return Seal.range(Math.min(Seal.lo(column), yf + 1), Math.max(Seal.hi(column), yf + 2));
	}

	private void grove(Writer w) {
		for (int a = -GROVE; a <= GROVE; a++) {
			for (int b = -GROVE; b <= GROVE; b++) {
				BlockPos floor = at(a, yf, b);
				if (w.inside(floor)) {
					w.set(floor, PATCH.at(seed ^ 0x6A0BEL, a, 0, b));
					w.set(floor.below(), DIRT);
				}
			}
		}
		BlockState log = spruce ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
		BlockState pale = spruce ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : Blocks.STRIPPED_OAK_LOG.defaultBlockState();
		for (Stem stem : stemList) {
			BlockPos foot = stemFoot(stem);
			// Grown without light: a few of them pale (their bark never came).
			BlockState wood = Hash.unit(Hash.of(stem.key(), 1)) < 0.3 ? pale : log;
			for (int up = 0; up < stem.height(); up++) {
				w.set(foot.above(up), wood);
			}
			// Bare limbs: none on the short ones, one to three on the others at different heights, one to three long,
			// sometimes turned up at the end; never two the same way.
			if (stem.height() >= 6) {
				int limbs = Hash.below(Hash.of(stem.key(), 2), stem.height() >= 9 ? 4 : 2);
				int first = Hash.below(Hash.of(stem.key(), 3), 4);
				for (int l = 0; l < limbs; l++) {
					Direction dir = Direction.from2DDataValue((first + l) % 4);
					int at = stem.height() - 2 - Hash.below(Hash.of(stem.key(), 4, l), Math.max(1, stem.height() / 2));
					int length = 1 + Hash.below(Hash.of(stem.key(), 5, l), 3);
					BlockState limb = wood.setValue(RotatedPillarBlock.AXIS, dir.getAxis());
					BlockPos cell = foot.above(at);
					for (int k = 1; k <= length; k++) {
						cell = cell.relative(dir);
						w.set(cell, limb);
					}
					if (Hash.unit(Hash.of(stem.key(), 6, l)) < 0.6) {
						for (int up = 1; up <= 1 + Hash.below(Hash.of(stem.key(), 7, l), 2); up++) {
							w.set(cell.above(up), wood);
						}
					}
				}
			}
		}
		for (BlockPos bush : bushes) {
			if (w.get(bush).isAir()) {
				w.set(bush, DEAD_BUSH);
			}
		}
	}

	private void openWay(Writer w) {
		WayIn way = this.way;
		if (way == null) {
			return;
		}
		for (BlockPos cell : wayIn()) {
			w.set(cell, AIR);
			BlockPos under = cell.getY() == yf + 1 ? cell.below() : null;
			if (under != null && w.inside(under) && !Draw.solidGround(w.get(under))) {
				w.set(under, STONE);
			}
		}
		// Over the mouth and beside it: no sand or gravel.
		for (int side = -1; side <= 2; side++) {
			for (int up = 3; up <= 2 + LINTEL + 1; up++) {
				for (int b = way.mouth() - 2; b <= way.mouth(); b++) {
					BlockPos cell = at(way.lane() + side, yf + up, b);
					if (w.inside(cell) && w.get(cell).getBlock() instanceof FallingBlock) {
						w.set(cell, STONE);
					}
				}
			}
		}
		// The cut in front (the tunnel's own 2 wide, everything over the floor), then the steps down to the land.
		for (int b = way.mouth() + 1; b <= way.steps(); b++) {
			int floor = laneFloor(b);
			for (int a = way.lane(); a <= way.lane() + 1; a++) {
				BlockPos base = at(a, floor, b);
				if (!w.inside(base.getX(), floor, base.getZ())) {
					continue;
				}
				for (int y = floor + 1; y <= Math.min(box.maxY(), floor + 48); y++) {
					BlockState state = w.get(base.getX(), y, base.getZ());
					if (!state.isAir()) {
						w.set(base.getX(), y, base.getZ(), AIR);
					}
				}
				BlockState top = w.get(base);
				if (!Draw.solidGround(top)) {
					w.set(base, b > way.approach() ? COARSE_DIRT : DIRT);
					Draw.foundation(w, base.getX(), base.getZ(), floor - 1, DIRT, 10);
				} else if (Vegetation.isGrassGround(top) || top.is(Blocks.PODZOL) || top.is(Blocks.SNOW_BLOCK)) {
					w.set(base, Draw.chance(seed, a, floor, b, 0.35) ? COARSE_DIRT : DIRT);
				}
			}
			// The cut's sides: nothing that falls.
			for (int a : new int[] {way.lane() - 1, way.lane() + 2}) {
				BlockPos base = at(a, floor, b);
				for (int y = floor + 1; y <= floor + 24; y++) {
					if (w.inside(base.getX(), y, base.getZ()) && w.get(base.getX(), y, base.getZ()).getBlock() instanceof FallingBlock) {
						w.set(base.getX(), y, base.getZ(), STONE);
					}
				}
			}
		}
	}

	private void openWindow(Writer w) {
		if (window == null) {
			return;
		}
		for (BlockPos cell : windowCells()) {
			w.set(cell, AIR);
		}
		if (snowLanding) {
			// A drift on the floor: nothing to freeze, and a fall of any height stops in it (R12-1).
			for (BlockPos cell : pool) {
				if (w.inside(cell) && Seal.leaks(w.get(cell))) {
					w.set(cell, STONE);
				}
				w.set(cell.above(), POWDER_SNOW);
			}
			return;
		}
		for (BlockPos cell : pool) {
			w.set(cell, WATER);
			w.set(cell.below(), WATER);
			BlockPos basin = cell.below(2);
			if (w.inside(basin) && Seal.leaks(w.get(basin))) {
				w.set(basin, STONE);
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				for (int down = 0; down <= 1; down++) {
					BlockPos wall = cell.relative(side).below(down);
					if (!pool.contains(wall.above(down)) && w.inside(wall) && Seal.leaks(w.get(wall))) {
						w.set(wall, STONE);
					}
				}
			}
		}
	}
}
