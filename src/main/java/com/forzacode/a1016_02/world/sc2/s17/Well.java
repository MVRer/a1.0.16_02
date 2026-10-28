package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Fault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * The archive v3's well (docs/SELF_CREATED_2_R3.md, "The archive v3: the stacks", D-113), pure: a chunk-aligned square
 * shaft {@value #SIDE} x {@value #SIDE} beside the hall, from a pool of black water just over the world's bottom to a
 * flat cut on a section line at least {@value #OVER_LAND} over the highest land of its footprint, its face toward the
 * origin missing from the top down to the gallery nearest the land. Galleries every {@value #PITCH} round all four walls
 * ({@value #RING} wide, oak planks, a fence on the void side), the void 18 x 18 in the middle, bookshelf walls, one flight
 * of stairs a gallery turning round the void, the hall's floor one gallery's floor (a corridor joins them).
 *
 * <p>Its own coordinates: {@code i} 0..31 from the edge nearest the hall outward (negative in the corridor), {@code j}
 * 0..31 from the back (the hall's dais end) to the front (toward the origin), any {@code y}. A cell's block is a pure
 * function of those and of its column's ground ({@link #state}), so every chunk builds its part of the same thing.
 */
final class Well {
	static final int SIDE = 32;
	/** The interior's first and last {@code i} or {@code j} (galleries and void). */
	static final int IN0 = 4;
	static final int IN1 = 27;
	/** The void's first and last {@code i} or {@code j}. */
	static final int VOID0 = 7;
	static final int VOID1 = 24;
	/** Gallery pitch: a floor, then 5 clear. */
	static final int PITCH = 6;
	static final int RING = 3;
	/**
	 * The cut stands at least this much over the highest land, then up to the next section line: a piece of the land two
	 * chunks by two lifted where it should not be, its own soil on top, its own stone for sides (R3-1: the open face of the
	 * first cuts, 20 and 48 over the land, read as a building coming out of the ground on the real terrain, Mariano's
	 * complaint about 1.5.0; only the way in is cut into it now).
	 */
	static final int OVER_LAND = 24;
	/**
	 * The way in: a crack {@value #WAY_WIDE} wide through the front wall's middle (i 15, 16), from the ground gallery's floor
	 * up to the soil: the piece split, not a door (R3-1: a 4 x 4 cut at its foot read as a door into a building).
	 */
	static final int WAY_WIDE = 2;
	static final int WAY_I0 = 15;
	/** The piece's skin: its outer layer over the land is the world's (stone, dirt, its surface on top), this deep in dirt. */
	static final int SOIL = 4;
	/** The apron in front of the way in: {@value #APRON} deep, i {@value #APRON_I0} to {@value #APRON_I1} (the crack's approach). */
	static final int APRON = 4;
	static final int APRON_I0 = 12;
	static final int APRON_I1 = 19;
	/** The apron's cut reaches at most this far over its floor (and its fill this far under). */
	static final int APRON_CUT = 24;
	/**
	 * The pool: 3 deep, its floor 2 thick; the lowest gallery floor at least this over the world's lowest block (the
	 * jacket under the floor keeps the kit's 6 over the bottom).
	 */
	static final int POOL_DEPTH = 3;
	static final int OVER_LOWEST = 13;
	/** Lights every this many cells along the ring; the lure every {@value #LURE_EVERY}. */
	static final int LIGHT_EVERY = 6;
	static final int LURE_EVERY = 3;
	/** The galleries lit from the top down, by default (config {@code s}). */
	static final int LIT_DEFAULT = 6;
	/** The day books' shelves along each wall ({@code i} or {@code j}). */
	static final int[] DAY_AT = {10, 21};
	/** A flight's first stair along its wall; it runs {@value #STAIRS} stairs down from there. */
	static final int FLIGHT_AT = 12;
	static final int STAIRS = 5;
	/** The pool's ladder ({@code j} on the near side of the void). */
	static final int POOL_LADDER = 16;
	/** The corridor's doorway: hall {@code f} -4..-6, 4 high. */
	static final int DOOR_F = -5;
	static final int DOOR_HIGH = 4;

	/**
	 * Version 4 (R3A-1, FIX-R3-17): the piece stands this much over its own land, column by column (its top the land's own
	 * relief lifted, as a chunk shoved up a section), at least {@value #LIFT} and at most {@value #LIFT_MAX}: version 3's
	 * flat cut 24 to 40 over the highest land read as a tower on the real terrain.
	 */
	static final int LIFT = 16;
	static final int LIFT_MAX = 24;
	/** Version 4: over the piece's top, a tree that stood on the land is taken away up to this high over the crest. */
	static final int CLEAR = 12;
	/**
	 * Version 4: the talus's core before the crack (the crack and one a side), and its reach (out plus to a side: it slopes
	 * one a block every way, so it meets the land everywhere within its reach and never stands over it as a ridge).
	 */
	static final int CORE_I0 = WAY_I0 - 1;
	static final int CORE_I1 = WAY_I0 + WAY_WIDE;
	static final int TALUS = 24;
	/** Version 4: the faces' blobs (andesite, diorite, granite, gravel, dirt), at most one in a cell this wide, 5 to 8 across. */
	static final int BLOB = 9;

	/** Sides of the ring (a flight's wall): near (i low), back (j low), far (i high), front (j high). */
	static final int NEAR = 0;
	static final int BACK = 1;
	static final int FAR = 2;
	static final int FRONT = 3;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
	private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
	private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
	private static final BlockState SHELF = Blocks.BOOKSHELF.defaultBlockState();
	private static final BlockState GLOW = Blocks.GLOWSTONE.defaultBlockState();
	private static final BlockState FENCE = Blocks.OAK_FENCE.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
	private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
	private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
	private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
	private static final BlockState COAL = Blocks.COAL_ORE.defaultBlockState();
	private static final BlockState IRON = Blocks.IRON_ORE.defaultBlockState();
	/** The lighting fault in the galleries over the land: lit as at noon with nothing lighting them (D-110). */
	static final BlockState FAULT = Fault.lightOf(Fault.LEVEL, false);

	final ArchivePlan plan;
	/** The hall's side the well stands on ({@code r} sign). */
	final int side;
	/** The footprint's local {@code r} and {@code f} ranges. */
	final int r0;
	final int r1;
	final int f0;
	final int f1;
	/** The world's lowest worldgen block, the cut's Y (the shell's last block is one under), the front land's Y. */
	final int lowest;
	final int top;
	final int land;
	/** Galleries {@code k} (floor at {@code y0 + 6k}): the lowest, the one at the land, the highest, the lit one far down. */
	final int kMin;
	final int kGround;
	final int kTop;
	final int kLure;
	/** The highest gallery of day books (days 1 to 48): the first one wholly under the land (the ground gallery's face is open). */
	final int kDays;
	final int lit;
	/** The flights' phase: the flight from gallery {@code k} runs on side {@code (k + phase) mod 4}. */
	final int phase;
	/** Version 4: the lift over each column's land (0 in version 3, whose top is flat at {@link #top}). */
	final int lift;
	/** Version 4: the highest the piece's surface stands (version 3: {@code top - 1}). */
	final int crest;
	/** Version 4: the cave sections cut into the faces. */
	private final List<Cave> caves;
	private final int y0;
	private final long seed;

	/** A cave's section in a face: its side, its middle along the face and in height, its half width and half height. */
	record Cave(int side, double t, double y, double a, double b) {
	}

	private Well(ArchivePlan plan, int[] square, int lowest, int top, int land, int lit) {
		this(plan, square, lowest, top, land, lit, 0, top - 1);
	}

	private Well(ArchivePlan plan, int[] square, int lowest, int top, int land, int lit, int lift, int crest) {
		this.plan = plan;
		this.side = square[0];
		this.r0 = square[1];
		this.r1 = square[2];
		this.f0 = square[3];
		this.f1 = square[3] + SIDE - 1;
		this.lowest = lowest;
		this.top = top;
		this.land = land;
		this.lit = lit <= 0 ? LIT_DEFAULT : lit;
		this.y0 = plan.floorY();
		this.seed = plan.seed;
		this.kMin = Math.ceilDiv(lowest + OVER_LOWEST - y0, PITCH);
		this.kTop = Math.floorDiv(top - SOIL - PITCH - y0, PITCH);
		// The ground gallery's floor at or over the front land (and its water): the apron before it is filled, never cut into a
		// pond or a slope (SC2-H's survey: a cut beside a pond leaked).
		this.kGround = Math.max(1, Math.min(kTop - 1, Math.ceilDiv(land - y0, PITCH)));
		int below = kGround - kMin;
		int lure = Math.max(kMin + 1, kGround - Math.round(below * 2f / 3f));
		if (lure == 0 || lure == -1) {
			// Never the hall's gallery, nor the one under it (its ceiling lights are the hall gallery's floor).
			lure = -2 > kMin ? -2 : 1;
		}
		this.kLure = lure;
		this.kDays = kGround - 1;
		// Never a flight on the near wall from the hall's gallery or into it (the doorway), never on the front from the
		// ground gallery (the way in).
		int p = 1;
		if (Math.floorMod(kGround + p, 4) == FRONT) {
			p = 2;
		}
		this.phase = p;
		this.lift = lift;
		this.crest = lift > 0 ? Math.max(crest, top - 1) : top - 1;
		this.caves = lift > 0 ? caves() : List.of();
	}

	/** The well of a v3 plan from its kept numbers ({@code lowest}, {@code top}, {@code land}). */
	static Well of(ArchivePlan plan, int lowest, int top, int land, int lit) {
		return new Well(plan, square(plan), lowest, top, land, lit);
	}

	/** The well of a v4 plan from its kept numbers (version 3's, then the lift and the crest). */
	static Well of(ArchivePlan plan, int lowest, int top, int land, int lit, int lift, int crest) {
		return new Well(plan, square(plan), lowest, top, land, lit, Math.max(1, lift), crest);
	}

	/** True for version 4's piece of land (its top the land's relief lifted, its faces a section of the world). */
	boolean lifted() {
		return lift > 0;
	}

	/**
	 * The footprint: on the side away from the bay, its edge nearest the hall the first chunk border at least
	 * {@code 4 cols + 3} out (clear of the cells and their seal), its {@code j} span holding the doorway at hall
	 * {@code f} {@value #DOOR_F} at least 8 in from either end. Returns side, r0, r1, f0.
	 */
	static int[] square(ArchivePlan plan) {
		int s = -plan.baySide;
		int nearMin = 4 * plan.cols + 3;
		for (int near = nearMin; near < nearMin + 16; near++) {
			int ra = s > 0 ? near : -near - SIDE + 1;
			int rb = ra + SIDE - 1;
			for (int fLo = DOOR_F - 23; fLo <= DOOR_F - 8; fLo++) {
				BoundingBox box = plan.frame.box(ra, 0, fLo, rb, 0, fLo + SIDE - 1);
				if (Fault.chunkAligned(box)) {
					return new int[] {s, ra, rb, fLo};
				}
			}
		}
		throw new IllegalStateException("no chunk-aligned square");
	}

	/**
	 * Measures the land under a plan's well: the cut's Y (the first section line at least {@value #OVER_LAND} over the
	 * highest of 25 samples on a 5 x 5 grid over the footprint, plus 2 for what the grid misses) and the front land (the
	 * highest land or water of 8 samples over the apron, plus 1), and 1 if the apron's fill stays within its reach (the
	 * land before it falls at most 18 under it), else 0. Returns {top, land, fits}.
	 */
	static int[] measure(ArchivePlan plan, Terrain terrain) {
		if (plan.version >= 4) {
			return measure4(plan, terrain);
		}
		int[] sq = square(plan);
		int s = sq[0];
		int highest = Integer.MIN_VALUE;
		for (int a = 0; a <= 4; a++) {
			for (int b = 0; b <= 4; b++) {
				int i = Math.min(SIDE - 1, a * 8);
				int j = Math.min(SIDE - 1, b * 8);
				BlockPos p = plan.at(s > 0 ? sq[1] + i : sq[2] - i, 0, sq[3] + j);
				highest = Math.max(highest, terrain.ground(p.getX(), p.getZ()));
			}
		}
		int land = Integer.MIN_VALUE;
		int[] across = {APRON_I0, 15, 16, APRON_I1};
		for (int row : new int[] {SIDE, SIDE + APRON - 1}) {
			for (int i : across) {
				BlockPos p = plan.at(s > 0 ? sq[1] + i : sq[2] - i, 0, sq[3] + row);
				land = Math.max(land, Math.max(terrain.ground(p.getX(), p.getZ()), terrain.surface(p.getX(), p.getZ())));
			}
		}
		land += 1;
		highest = Math.max(highest, land);
		// The apron fills down to the land at most APRON_CUT: a front that falls further away (a cliff) is no place for it.
		int low = Integer.MAX_VALUE;
		for (int row : new int[] {SIDE, SIDE + APRON - 1}) {
			for (int i : across) {
				BlockPos p = plan.at(s > 0 ? sq[1] + i : sq[2] - i, 0, sq[3] + row);
				low = Math.min(low, terrain.ground(p.getX(), p.getZ()));
			}
		}
		return new int[] {Fault.sectionCeil(highest + 2 + OVER_LAND), land, land - low <= APRON_CUT - PITCH ? 1 : 0};
	}

	/**
	 * Version 4 (R3A-1): the same front land as version 3, and the piece lifted over its own land: the lowest and highest
	 * of 25 samples on a 5 x 5 grid over the footprint; the cut ({@link #top}, the shaft's) {@value #LIFT} over the lowest
	 * column, or higher if a gallery over the ground gallery needs it (the ground gallery always at or over the front land
	 * and its water); the lift (each column's surface over its land) that, at most {@value #LIFT_MAX}; the crest 2 over the
	 * highest's (what the grid misses is cut flat there). It fits where version 3's apron fits and the land on the talus's
	 * rim (10 samples {@value #TALUS} out) is no more than 3 under the talus's foot there. Returns {top, land, fits, lift,
	 * crest}.
	 */
	static int[] measure4(ArchivePlan plan, Terrain terrain) {
		int[] sq = square(plan);
		int s = sq[0];
		int highest = Integer.MIN_VALUE;
		int lowest = Integer.MAX_VALUE;
		for (int a = 0; a <= 4; a++) {
			for (int b = 0; b <= 4; b++) {
				int i = Math.min(SIDE - 1, a * 8);
				int j = Math.min(SIDE - 1, b * 8);
				BlockPos p = plan.at(s > 0 ? sq[1] + i : sq[2] - i, 0, sq[3] + j);
				int g = terrain.ground(p.getX(), p.getZ());
				highest = Math.max(highest, g);
				lowest = Math.min(lowest, g);
			}
		}
		int land = Integer.MIN_VALUE;
		int low = Integer.MAX_VALUE;
		int[] across = {APRON_I0, 15, 16, APRON_I1};
		for (int row : new int[] {SIDE, SIDE + APRON - 1}) {
			for (int i : across) {
				BlockPos p = plan.at(s > 0 ? sq[1] + i : sq[2] - i, 0, sq[3] + row);
				int g = terrain.ground(p.getX(), p.getZ());
				land = Math.max(land, Math.max(g, terrain.surface(p.getX(), p.getZ())));
				low = Math.min(low, g);
			}
		}
		land += 1;
		int y0 = plan.floorY();
		int sill = y0 + PITCH * Math.max(1, Math.ceilDiv(land - y0, PITCH));
		int top = Math.max(sill + 2 * PITCH + SOIL, lowest + LIFT + 1);
		int lift = Math.clamp(top - 1 - lowest, LIFT, LIFT_MAX);
		boolean fits = land - low <= APRON_CUT - PITCH;
		for (int a = 0; a <= TALUS && fits; a += 6) {
			for (int i : new int[] {CORE_I0 - a, CORE_I1 + a}) {
				BlockPos p = plan.at(s > 0 ? sq[1] + i : sq[2] - i, 0, sq[3] + SIDE + TALUS - a);
				fits &= terrain.ground(p.getX(), p.getZ()) >= sill - TALUS - 3;
			}
		}
		return new int[] {top, land, fits ? 1 : 0, lift, Math.max(top - 1, highest + lift + 2)};
	}

	// --- coordinates ---

	int localR(int i) {
		return side > 0 ? r0 + i : r1 - i;
	}

	int iOf(int r) {
		return side > 0 ? r - r0 : r1 - r;
	}

	BlockPos at(int i, int y, int j) {
		BlockPos p = plan.at(localR(i), 0, f0 + j);
		return new BlockPos(p.getX(), y, p.getZ());
	}

	/** The well's {@code i} and {@code j} of a world column, or null outside its footprint. */
	int @Nullable [] ij(int x, int z) {
		int i = iOf(plan.localR(x, z));
		int j = plan.localF(x, z) - f0;
		return i >= 0 && i < SIDE && j >= 0 && j < SIDE ? new int[] {i, j} : null;
	}

	/** The well's {@code i} and {@code j} of any world column (outside its footprint too: the apron, the talus). */
	int[] ijAnywhere(int x, int z) {
		return new int[] {iOf(plan.localR(x, z)), plan.localF(x, z) - f0};
	}

	int floorY(int k) {
		return y0 + PITCH * k;
	}

	/** The gallery a player's feet at {@code y} are on (standing on its floor or anywhere in its 5 clear). */
	int galleryAt(int y) {
		return Math.floorDiv(y - 1 - y0, PITCH);
	}

	/** The gallery whose floor or space a block at {@code y} is part of. */
	private int levelOf(int y) {
		return Math.floorDiv(y - y0, PITCH);
	}

	/** The bottom of everything (the pool's floor, 2 thick). */
	int bottom() {
		return floorY(kMin) - POOL_DEPTH - 2;
	}

	int waterTop() {
		return floorY(kMin) - 1;
	}

	static boolean inside(int i, int j) {
		return i >= IN0 && i <= IN1 && j >= IN0 && j <= IN1;
	}

	static boolean inVoid(int i, int j) {
		return i >= VOID0 && i <= VOID1 && j >= VOID0 && j <= VOID1;
	}

	static boolean ring(int i, int j) {
		return inside(i, j) && !inVoid(i, j);
	}

	/** The wall layer of a footprint cell outside the interior: 0 the outer face ... 3 the bookshelf face. */
	static int layer(int i, int j) {
		return Math.min(Math.min(i, j), Math.min(SIDE - 1 - i, SIDE - 1 - j));
	}

	/** The side a ring or face cell belongs to (the nearest wall). */
	static int sideOf(int i, int j) {
		int dn = i;
		int db = j;
		int df = SIDE - 1 - i;
		int dfr = SIDE - 1 - j;
		int min = Math.min(Math.min(dn, db), Math.min(df, dfr));
		return min == dn ? NEAR : min == db ? BACK : min == df ? FAR : FRONT;
	}

	/** The position along a side's wall ({@code j} on the near and far sides, {@code i} on the back and front). */
	static int along(int s, int i, int j) {
		return s == NEAR || s == FAR ? j : i;
	}

	/** The design direction a side's face looks (into the well). */
	Direction faceDir(int s) {
		return switch (s) {
			case NEAR -> side > 0 ? Direction.EAST : Direction.WEST;
			case FAR -> side > 0 ? Direction.WEST : Direction.EAST;
			case BACK -> Direction.NORTH;
			default -> Direction.SOUTH;
		};
	}

	/** The design direction of increasing {@link #along} on a side. */
	Direction alongDir(int s) {
		return s == NEAR || s == FAR ? Direction.NORTH : side > 0 ? Direction.EAST : Direction.WEST;
	}

	/** The lane (next to the face) of a side: its {@code i} (near, far) or {@code j} (back, front). */
	static int lane(int s) {
		return s == NEAR || s == BACK ? IN0 : IN1;
	}

	/** The ring's middle line of a side. */
	static int middle(int s) {
		return s == NEAR || s == BACK ? IN0 + 1 : IN1 - 1;
	}

	/** The side of the flight from gallery {@code k} down to {@code k - 1}, or -1 (none: above the ground, or the lowest). */
	int flightSide(int k) {
		if (k > kGround || k <= kMin) {
			return -1;
		}
		return Math.floorMod(k + phase, 4);
	}

	boolean lit(int k) {
		if (k == kLure) {
			return true;
		}
		return k >= kGround && k <= kTop && (k > kTop - lit || (kTop - k) % 2 == 0);
	}

	/** The doorway's {@code j} (its middle). */
	int doorJ() {
		return DOOR_F - f0;
	}

	boolean doorway(int i, int y, int j) {
		return Math.abs(j - doorJ()) <= 1 && y >= y0 + 1 && y <= y0 + DOOR_HIGH && i <= IN0 - 1;
	}

	// --- blocks ---

	/**
	 * The block at ({@code i}, {@code y}, {@code j}) of the footprint, or null to leave what is there; {@code ground} is
	 * the column's land (the outer layer stands from 3 under it).
	 */
	@Nullable BlockState state(int i, int y, int j, int ground) {
		return state(i, y, j, ground, GRASS);
	}

	/**
	 * {@link #state(int, int, int, int)}, {@code surface} the land's own top block (the piece's skin carries it on top).
	 */
	@Nullable BlockState state(int i, int y, int j, int ground, BlockState surface) {
		if (lift > 0) {
			return state4(i, y, j, ground, surface);
		}
		if (y < bottom() || y >= top) {
			return null;
		}
		if (y >= top - SOIL) {
			// The piece's top: its soil over the whole of it, the land's surface on it (it keeps the sky out of the shaft, so
			// the depths stay dark by day too).
			return y == top - 1 ? surface : soil(surface);
		}
		int ground0 = floorY(kGround);
		boolean front = j > IN1;
		if (front && way(i, y)) {
			return AIR; // the way in: one rectangular cut at the foot of the face toward the origin
		}
		if (!inside(i, j)) {
			int layer = layer(i, j);
			if (doorway(i, y, j)) {
				return AIR;
			}
			if (layer == 0) {
				// The outer layer is the world's: its own ground under the land (jacketed), its stone over it, cut flat on
				// the chunk border. No cobblestone of the shell is ever outside (R3-1, Mariano's report on 1.5.0).
				return y > ground ? skin(i, y, j) : null;
			}
			if (front && (y == ground0 || layer < 3)) {
				return STONE; // the way's sill and the front's inner layers: the world's rock round the cut, no wall in it
			}
			if (layer == 3 && y > waterTop()) {
				return face(i, y, j);
			}
			return shell(i, y, j);
		}
		return insideCell(i, y, j);
	}

	/** A cell of the shaft's inside (the pool, its floor, the void and the ring), as version 3 builds it. */
	private BlockState insideCell(int i, int y, int j) {
		int floor0 = floorY(kMin);
		if (y < floor0 - POOL_DEPTH) {
			return COBBLE;
		}
		boolean hole = inVoid(i, j);
		if (y < floor0) {
			if (!hole) {
				return COBBLE;
			}
			return poolLadder(i, j) ? ladder(true) : WATER;
		}
		if (hole) {
			return poolLadder(i, j) && y == floor0 ? ladder(false) : AIR;
		}
		return ringCell(i, y, j);
	}

	/**
	 * Version 4 (R3A-1): the piece of land lifted. Each column's surface stands {@link #lift} over its own land (never under
	 * the shaft's cut, never over the crest), the land's surface block on it and 3 or 4 of its soil under that right to the
	 * faces; under the soil and over the land the outer layers are a section of the world ({@link #section}: stone with
	 * blobs of andesite, diorite, granite, gravel and dirt, coal and iron, a cave or two cut 2 into the faces); no
	 * cobblestone over the land. The crack, the shell under the land, the faces' bookshelves and the inside as version 3.
	 */
	private @Nullable BlockState state4(int i, int y, int j, int ground, BlockState surface) {
		if (y < bottom()) {
			return null;
		}
		int s = surfaceY(i, j, ground);
		if (y > s) {
			return null;
		}
		int layer = inside(i, j) ? 4 : layer(i, j);
		if (y >= top - SOIL) {
			if (layer == 0 && y <= ground) {
				return null; // the world's own under its land
			}
			if (y == s) {
				return surface;
			}
			if (y >= s - soilDepth(i, j)) {
				return soil(surface);
			}
			return section(i, y, j, s);
		}
		boolean front = j > IN1;
		if (front && way(i, y)) {
			return AIR;
		}
		if (layer == 4) {
			return insideCell(i, y, j);
		}
		if (doorway(i, y, j)) {
			return AIR;
		}
		if (layer < 3 && y > ground) {
			return section(i, y, j, s); // over the land the outer layers are the world's section, never a wall
		}
		if (layer == 0) {
			return null;
		}
		if (front && (y == floorY(kGround) || layer < 3)) {
			return STONE;
		}
		if (layer == 3 && y > waterTop()) {
			return face(i, y, j);
		}
		return shell(i, y, j);
	}

	/**
	 * Version 4: a column's surface over its land {@code ground}, with the land's own small bumps (0 to 2, in patches),
	 * never under the shaft's cut nor over the crest.
	 */
	int surfaceY(int i, int j, int ground) {
		if (lift <= 0) {
			return top - 1;
		}
		return Math.clamp((long) Math.min(ground, crest) + lift + bump(i, j), top - 1, crest);
	}

	/** Version 4: the top's bumps, 0 to 2 in patches of about 4 (a field's own unevenness, on flat land too). */
	private int bump(int i, int j) {
		double a = Draw.unit(seed, Math.floorDiv(i, 4), 6800, Math.floorDiv(j, 4));
		double b = Draw.unit(seed, Math.floorDiv(i + 2, 4), 6801, Math.floorDiv(j + 2, 4));
		return (int) Math.floor(a + b);
	}

	/** Version 4: the soil under a column's surface block, 3 to 5 in patches (with it, 4 to 6 of the land's top). */
	private int soilDepth(int i, int j) {
		return 3 + (int) Math.floor(2 * Draw.unit(seed, Math.floorDiv(i, 3), 6900, Math.floorDiv(j, 3)) + 0.5 * Draw.unit(seed, i, 6901, j));
	}

	/**
	 * Version 4: a cell of the world's section in the piece (what a chunk shoved up shows in its faces): stone, a blob of
	 * andesite, diorite, granite, gravel or (near the top) dirt where one stands (one at most in a cell of {@value #BLOB},
	 * 4 to 7 across), coal and iron here and there; air in a face's cave. Nothing that falls stands over an opening.
	 */
	BlockState section(int i, int y, int j, int s) {
		if (cave(i, y, j)) {
			return AIR;
		}
		boolean overOpen = cave(i, y - 1, j) || j > IN1 && way(i, y - 1);
		int ci = Math.floorDiv(i, BLOB);
		int cy = Math.floorDiv(y, BLOB);
		int cj = Math.floorDiv(j, BLOB);
		double kind = Draw.unit(seed, ci, cy + 5000, cj);
		if (kind < 0.82) {
			double r = 2.5 + 1.5 * Draw.unit(seed, ci, cy + 5100, cj);
			double span = BLOB - 2 * r;
			double mi = ci * BLOB + r + span * Draw.unit(seed, ci, cy + 5200, cj);
			double my = cy * BLOB + r + span * Draw.unit(seed, ci, cy + 5300, cj);
			double mj = cj * BLOB + r + span * Draw.unit(seed, ci, cy + 5400, cj);
			double di = i + 0.5 - mi;
			double dy = (y + 0.5 - my) * 1.3;
			double dj = j + 0.5 - mj;
			if (di * di + dy * dy + dj * dj <= r * r) {
				if (kind < 0.22) {
					return ANDESITE;
				}
				if (kind < 0.40) {
					return DIORITE;
				}
				if (kind < 0.58) {
					return GRANITE;
				}
				if (kind < 0.68) {
					if (!overOpen) {
						return GRAVEL;
					}
				} else if (y > s - 16) {
					return DIRT;
				}
			}
		}
		double u = Draw.unit(seed, i, y + 3000, j);
		if (u < 0.012) {
			return COAL;
		}
		if (u < 0.018 && y < s - 8) {
			return IRON;
		}
		if (u < 0.026 && !overOpen) {
			return GRAVEL;
		}
		return STONE;
	}

	/** Version 4: true in a face's cave (2 deep at its middle, 1 at its rim; never in the crack's face near the crack). */
	boolean cave(int i, int y, int j) {
		if (caves.isEmpty() || i < 0 || j < 0 || i >= SIDE || j >= SIDE) {
			return false;
		}
		int layer = layer(i, j);
		if (layer > 1) {
			return false;
		}
		int s = sideOf(i, j);
		for (Cave c : caves) {
			if (c.side() != s) {
				continue;
			}
			double dt = (along(s, i, j) + 0.5 - c.t()) / c.a();
			double dy = (y + 0.5 - c.y()) / c.b();
			// A ragged rim: a cave's mouth, not a cut opening.
			double e = dt * dt + dy * dy + 0.45 * (Draw.unit(seed, along(s, i, j), y + 7400, s) - 0.5);
			if (e <= 1 && layer < (e < 0.45 ? 2 : 1)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Version 4: one cave in the back, near or far face, and most of the time a second in another of them: a ragged mouth
	 * 6 to 10 wide, 4 to 6 high (never in the face of the crack, where a hole that size read as a window).
	 */
	private List<Cave> caves() {
		double lo = land + 4;
		double hi = top - SOIL - 4;
		List<Cave> out = new ArrayList<>();
		if (hi < lo) {
			return out;
		}
		int[] sides = {BACK, NEAR, FAR};
		int first = Draw.between(seed, 7100, 0, 2);
		int count = Draw.chance(seed, 7200, 0, 0, 0.6) ? 2 : 1;
		for (int n = 0; n < count; n++) {
			double a = 3 + 2 * Draw.unit(seed, 7103, n, 0);
			double t = a + 3 + (SIDE - 2 * a - 6) * Draw.unit(seed, 7101, n, 0);
			out.add(new Cave(sides[(first + n) % 3], t, lo + (hi - lo) * Draw.unit(seed, 7102, n, 0), a, 2 + Draw.unit(seed, 7104, n, 0)));
		}
		return out;
	}

	/** Version 4: a plant on the piece's top (short grass, now and then a flower; a dead bush on sand), or null. */
	@Nullable BlockState plant(int i, int j, BlockState surface) {
		double u = Draw.unit(seed, i, 7000, j);
		if (surface.is(Blocks.GRASS_BLOCK)) {
			return u < 0.14 ? Blocks.SHORT_GRASS.defaultBlockState() : u < 0.15 ? Blocks.DANDELION.defaultBlockState() : u < 0.16 ? Blocks.POPPY.defaultBlockState() : null;
		}
		return surface.is(Blocks.SAND) || surface.is(Blocks.RED_SAND) ? u < 0.012 ? Blocks.DEAD_BUSH.defaultBlockState() : null : null;
	}

	// --- version 4: the talus before the crack ---

	/**
	 * Version 4: how far a column in front of the face is from the talus's core (out plus to the side), or -1 for one
	 * outside its reach. The core is the crack and one a side ({@value #CORE_I0} to {@value #CORE_I1}) at the face.
	 */
	static int talusReach(int i, int j) {
		if (j < SIDE || j > SIDE + TALUS || i < CORE_I0 - TALUS || i > CORE_I1 + TALUS) {
			return -1;
		}
		int out = j - SIDE;
		int aside = Math.max(0, Math.max(CORE_I0 - i, i - CORE_I1));
		int reach = out + aside;
		return reach <= TALUS ? reach : -1;
	}

	/**
	 * Version 4: a column's height in front of the face once the talus is built: the land as it is where it stands within
	 * one a block of the crack's sill (nothing raised on land at the sill's height), else filled up to (scree) or cut down
	 * to (a notch) that slope; outside the talus's reach, the land.
	 */
	int frontHeight(int i, int j, int ground) {
		int reach = talusReach(i, j);
		if (reach < 0) {
			return ground;
		}
		int sill = floorY(kGround);
		// Scree lies ragged: past the crack's own step, a column now and then one lower than the slope.
		int jag = reach > 1 && Draw.chance(seed, i, 7350, j, 0.45) ? 1 : 0;
		return Math.clamp(ground, sill - reach - jag, sill + reach);
	}

	/** Version 4: the talus's scree: gravel, stone, andesite, a little dirt and granite, the world's own, nothing that grows. */
	BlockState scree(int i, int y, int j) {
		double u = Draw.unit(seed, i, y + 7300, j);
		return u < 0.40 ? GRAVEL : u < 0.65 ? STONE : u < 0.80 ? ANDESITE : u < 0.92 ? DIRT : GRANITE;
	}

	private static boolean poolLadder(int i, int j) {
		return i == VOID0 && j == POOL_LADDER;
	}

	private BlockState ladder(boolean wet) {
		return plan.frame.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, faceDir(NEAR)))
				.setValue(BlockStateProperties.WATERLOGGED, wet);
	}

	private BlockState shell(int i, int y, int j) {
		return Draw.chance(seed, i, y + 2000, j, 0.08) ? MOSSY : COBBLE;
	}

	/**
	 * The piece's surface block from its land's biome (the same in worldgen and the gallery: the noise's raw blocks have no
	 * grass): sand in deserts and on beaches, red sand in badlands, else grass.
	 */
	static BlockState surfaceOf(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
		if (biome.is(net.minecraft.world.level.biome.Biomes.DESERT) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)) {
			return Blocks.SAND.defaultBlockState();
		}
		if (biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS)) {
			return Blocks.RED_SAND.defaultBlockState();
		}
		return GRASS;
	}

	/** The soil under a surface block: sandstone under sand (a desert's own section), dirt under the rest. */
	static BlockState soil(BlockState surface) {
		if (surface.is(Blocks.SAND)) {
			return Blocks.SANDSTONE.defaultBlockState();
		}
		if (surface.is(Blocks.RED_SAND)) {
			return Blocks.RED_SANDSTONE.defaultBlockState();
		}
		return DIRT;
	}

	/** The piece's outer skin over the land: stone, with the first versions' dirt and gravel pockets and ores in it. */
	private BlockState skin(int i, int y, int j) {
		double pocket = Draw.unit(seed, Math.floorDiv(i, 4), Math.floorDiv(y, 3) + 3500, Math.floorDiv(j, 4));
		if (pocket < 0.07) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (pocket < 0.13 && y > top - SOIL - 12) {
			return DIRT;
		}
		double u = Draw.unit(seed, i, y + 3000, j);
		if (u < 0.012) {
			return Blocks.COAL_ORE.defaultBlockState();
		}
		if (u < 0.018) {
			return Blocks.IRON_ORE.defaultBlockState();
		}
		if (u < 0.03) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		return STONE;
	}

	/** A cell of the ring over the lowest floor. */
	private BlockState ringCell(int i, int y, int j) {
		int k = levelOf(y);
		int m = y - floorY(k);
		if (k > kTop) {
			return AIR;
		}
		int s = sideOf(i, j);
		int t = along(s, i, j);
		if (m == 0) {
			// The floor: a stair hole over the flight from here, a light over the gallery below if it is lit.
			int fs = flightSide(k);
			if (fs >= 0 && s == fs && laneCell(fs, i, j) && t >= FLIGHT_AT && t < FLIGHT_AT + 3) {
				return AIR;
			}
			if (k - 1 >= kMin && lit(k - 1) && middleCell(s, i, j) && lightAt(k - 1, t)) {
				return GLOW;
			}
			return PLANKS;
		}
		// The flight from the gallery above lands here: stair n (0 the highest) at m = 5 - n.
		int fs = flightSide(k + 1);
		if (fs >= 0 && s == fs && laneCell(fs, i, j)) {
			int n = t - FLIGHT_AT;
			if (n >= 0 && n < STAIRS && m == STAIRS - n) {
				return plan.frame.turn(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, alongDir(fs).getOpposite()));
			}
		}
		if (m == 1 && rail(i, j) && !(k == kMin && i == VOID0 - 1 && j == POOL_LADDER)) {
			return FENCE;
		}
		if (m == 2 && faultAt(k, s, i, j, t)) {
			return FAULT;
		}
		return AIR;
	}

	/** The lighting fault's cells: over the land's galleries, on the ring's middle line every 3 at head height. */
	private boolean faultAt(int k, int s, int i, int j, int t) {
		return k > kGround && middleCell(s, i, j) && Math.floorMod(t - IN0 - 1, 3) == 0;
	}

	/** The way in's cut (through the front wall's layers), for a front cell. */
	boolean way(int i, int y) {
		int floor = floorY(kGround);
		return i >= WAY_I0 && i < WAY_I0 + WAY_WIDE && y > floor && y < top - SOIL;
	}

	/**
	 * The lighting fault on the piece's four faces (D-110: a face toward the viewer, lit as at noon with nothing lighting
	 * it): for a cell one out from a face (i or j -1 or 32), true where a light stands, every 3 along the face and up from
	 * the land, over the column's ground.
	 */
	boolean faceLight(int i, int y, int j, int ground) {
		return faceLight(i, y, j, ground, top);
	}

	/** {@link #faceLight}, under {@code below} (version 4: the surface of the piece's column beside it). */
	boolean faceLight(int i, int y, int j, int ground, int below) {
		boolean outI = (i == -1 || i == SIDE) && j >= 0 && j < SIDE;
		boolean outJ = (j == -1 || j == SIDE) && i >= 0 && i < SIDE;
		if (!outI && !outJ || y <= ground || y >= below) {
			return false;
		}
		int t = outI ? j : i;
		return Math.floorMod(t - 1, 3) == 0 && Math.floorMod(y - ground - 2, 3) == 0;
	}

	private static boolean laneCell(int s, int i, int j) {
		return s == NEAR || s == FAR ? i == lane(s) : j == lane(s);
	}

	private static boolean middleCell(int s, int i, int j) {
		return s == NEAR || s == FAR ? i == middle(s) : j == middle(s);
	}

	/** The ring cell next to the void (where the rail stands). */
	static boolean rail(int i, int j) {
		return ring(i, j) && i >= VOID0 - 1 && i <= VOID1 + 1 && j >= VOID0 - 1 && j <= VOID1 + 1;
	}

	private boolean lightAt(int k, int t) {
		int every = k == kLure ? LURE_EVERY : LIGHT_EVERY;
		return Math.floorMod(t - IN0 - 3, every) == 0;
	}

	/** The bookshelf face (layer 3) over the pool. */
	private BlockState face(int i, int y, int j) {
		int k = levelOf(y);
		int m = y - floorY(k);
		if (k > kTop) {
			k = kTop;
			m = Math.min(5, y - floorY(kTop));
		}
		if (m == 0 || y == top - 1) {
			return shell(i, y, j);
		}
		int s = sideOf(i, j);
		int t = along(s, i, j);
		if (m == 3 && lit(k) && lightAt(k, t)) {
			return GLOW;
		}
		if (m == 2 && k <= kDays && k >= kMin && isDayShelf(t)) {
			return plan.frame.turn(Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(ChiseledBookShelfBlock.FACING, faceDir(s)));
		}
		return SHELF;
	}

	private static boolean isDayShelf(int t) {
		return t == DAY_AT[0] || t == DAY_AT[1];
	}

	// --- the corridor ---

	/** The corridor's {@code i} nearest the hall (its first cell past the hall's shell). */
	int corridorI0() {
		return iOf(side * (ArchivePlan.SHELL + 1));
	}

	/** True for a corridor column (between the hall's shell and the well: the doorway's width and its walls). */
	boolean corridor(int i, int j) {
		return i >= corridorI0() && i < 0 && Math.abs(j - doorJ()) <= 2;
	}

	/** True for a column the well writes: its footprint or the corridor. */
	boolean owns(int i, int j) {
		return i >= 0 && i < SIDE && j >= 0 && j < SIDE || corridor(i, j);
	}

	/** The corridor's block: 3 wide, 4 high, cobblestone round it, planks underfoot, dark. */
	@Nullable BlockState corridorState(int i, int y, int j) {
		if (y < y0 - 1 || y > y0 + DOOR_HIGH + 1) {
			return null;
		}
		boolean wall = Math.abs(j - doorJ()) == 2;
		if (wall || y == y0 - 1 || y == y0 + DOOR_HIGH + 1) {
			return shell(i, y, j);
		}
		return y == y0 ? PLANKS : AIR;
	}

	// --- the apron ---

	/** The apron's cells: just outside the front, {@value #APRON} deep, before the crack. */
	static boolean apron(int i, int j) {
		return i >= APRON_I0 && i <= APRON_I1 && j >= SIDE && j < SIDE + APRON;
	}

	/** The crack's floor through the front wall (the sill). */
	static boolean wayFloor(int i, int j) {
		return i >= WAY_I0 && i < WAY_I0 + WAY_WIDE && j > IN1 && j < SIDE;
	}

	// --- for the shape and the rules ---

	/** Everything the well may write: its footprint from its bottom to the cut, the corridor, the apron and its edge. */
	BoundingBox box() {
		int jacket = ArchiveShape.JACKET;
		BoundingBox shaft = BoundingBox.fromCorners(at(0, floorY(kMin) - POOL_DEPTH - jacket, 0), at(SIDE - 1, lift > 0 ? crest + CLEAR : top - 1, SIDE - 1));
		// The faces' lights, one out all round, from the land up.
		shaft = shaft.encapsulate(BoundingBox.fromCorners(at(-1, floorY(kGround) - APRON_CUT, -1), at(SIDE, top - 1, SIDE)));
		shaft = shaft.encapsulate(BoundingBox.fromCorners(at(corridorI0(), y0 + 1 - jacket, doorJ() - 1 - jacket),
				at(0, y0 + DOOR_HIGH + jacket, doorJ() + 1 + jacket)));
		BoundingBox apron = lift > 0
				? BoundingBox.fromCorners(at(CORE_I0 - TALUS - 1, floorY(kGround) - TALUS, SIDE), at(CORE_I1 + TALUS + 1, floorY(kGround) + TALUS + CLEAR, SIDE + TALUS + 1))
				: BoundingBox.fromCorners(at(APRON_I0 - 1, floorY(kGround) - APRON_CUT, SIDE - 4), at(APRON_I1 + 1, floorY(kGround) + APRON_CUT, SIDE + APRON));
		// The faces' lights one out (j = SIDE in front, from the land up; the footprint box covers the rest within one).
		return shaft.encapsulate(apron);
	}

	/** The well's inside: the void and the galleries, from the water to the cut. */
	BoundingBox insideBox() {
		return BoundingBox.fromCorners(at(IN0, floorY(kMin) - POOL_DEPTH, IN0), at(IN1, top - SOIL - 1, IN1));
	}

	/** The inside heights of a column the well owns ({lo, hi}), or null. */
	int @Nullable [] insideRange(int i, int j) {
		if (inside(i, j)) {
			return new int[] {floorY(kMin) - POOL_DEPTH, top - SOIL - 1};
		}
		if (i <= IN0 - 1 && i >= corridorI0() && Math.abs(j - doorJ()) <= 1) {
			return new int[] {y0 + 1, y0 + DOOR_HIGH};
		}
		return null;
	}

	/** The gallery a player standing here is on ({@code k}), or {@link Integer#MIN_VALUE} if not in the well. */
	int galleryOf(BlockPos feet) {
		int[] c = ij(feet.getX(), feet.getZ());
		if (c == null || !inside(c[0], c[1]) || feet.getY() < floorY(kMin) - POOL_DEPTH || feet.getY() >= top - SOIL) {
			return Integer.MIN_VALUE;
		}
		return Math.max(kMin, galleryAt(feet.getY()));
	}

	/**
	 * Every light of gallery {@code k} as generated: the glowstone of its ceiling (in the floor above) and its wall, and
	 * over the land the lighting fault's cells (17f takes them all).
	 */
	List<BlockPos> lights(int k) {
		List<BlockPos> out = new ArrayList<>();
		if (k < kMin || k > kTop) {
			return out;
		}
		for (int i = 3; i <= SIDE - 4; i++) {
			for (int j = 3; j <= SIDE - 4; j++) {
				if (ring(i, j) && k + 1 <= kTop && state(i, floorY(k + 1), j, Integer.MAX_VALUE) == GLOW) {
					out.add(at(i, floorY(k + 1), j));
				}
				if (layer(i, j) == 3 && state(i, floorY(k) + 3, j, Integer.MAX_VALUE) == GLOW) {
					out.add(at(i, floorY(k) + 3, j));
				}
				if (ring(i, j) && state(i, floorY(k) + 2, j, Integer.MAX_VALUE) == FAULT) {
					out.add(at(i, floorY(k) + 2, j));
				}
			}
		}
		return out;
	}

	/** The day books' shelves of gallery {@code k} (8: two a wall), as generated; from {@link #kDays} down. */
	List<BlockPos> dayShelves(int k) {
		List<BlockPos> out = new ArrayList<>();
		if (k > kDays || k < kMin) {
			return out;
		}
		int y = floorY(k) + 2;
		for (int t : DAY_AT) {
			for (int[] c : new int[][] {{3, t}, {t, 3}, {28, t}, {t, 28}}) {
				BlockState b = state(c[0], y, c[1], Integer.MAX_VALUE);
				if (b != null && b.is(Blocks.CHISELED_BOOKSHELF)) {
					out.add(at(c[0], y, c[1]));
				}
			}
		}
		return out;
	}

	/** The sign point: the middle of the open face, halfway from the ground gallery's floor to the cut. */
	BlockPos sign() {
		return at(SIDE / 2, (floorY(kGround) + top) / 2, SIDE - 1);
	}

	/** Where one stands on the ground gallery's sill, looking in (the well's way in from the land). */
	BlockPos sill() {
		return at(SIDE / 2, floorY(kGround) + 1, SIDE - 2);
	}
}
