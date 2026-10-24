package com.forzacode.a1016_02.world.sc2.s16;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Feature 16's shape (docs/SELF_CREATED_2.md, 16): the stepped pyramid of the first versions' bricks, its hollow core,
 * its negative dug out under it, its way in toward the origin, and his pass over it.
 *
 * <p>The start's origin is the base's centre at the height of its lowest course (the first cell over the ground); its
 * facing is the side the way in looks out of. With {@code R} the base's half width (31, 47, 63 for a base of 63, 95,
 * 127), the column whose Chebyshev distance from the centre is {@code d} holds courses 0 to {@code R - d}: every course
 * one block in on every side, the apex course {@code R} a single block. Measured in the column's own course, the core
 * is the same shape inset by {@value #SHELL}: air from the floor to {@value #SHELL} under the apex, three blocks of
 * shell over it everywhere and a stepped vault you only see from below. Under the core (land only) the pit: its
 * opening is the core's floor inset by {@value #RIM} more ({@code R - 6} half wide, the rim walkway between it and the
 * walls), every level one down and one in, a lining of brick on every tread and riser, down to a 3 x 3 floor
 * {@code R - 7} steps down with a cobblestone and a torch in its middle. The floor of the core is the ground,
 * dead.
 *
 * <p>His pass, all removals and all decided here from the start's seed (never per chunk): the dead-end cuts into the
 * faces (each with the core kept three away from it), wear running down the faces in streaks (a step worn to a stair,
 * to a slab, or gone), the earth's stain on the lowest courses and under each cut, one face brick in 300 missing, the
 * grounds (dirt, bare trunks, a sharp edge). The sea form ({@link #sea}) stands on the ocean floor: no way in, no pit,
 * the core dry; what is taken there fills with water.
 *
 * <p>Versions (R12-2, {@link Start#versionOr}): 1, as first built; 2 (the review): on land a plinth where the ground falls
 * away, the pyramid's own steps going on down a block out each course to the land (no unguarded drop over 3 at its
 * foot, R12-3 and R13-3); the point taken off, the top a 3 x 3 level, glowstone there on a lit one (R11-1, R11-4); the
 * damp in dark drips only, brown terracotta and mud over brick (R11-16); the way in reached by a cut exactly 2 wide
 * through higher land, its sides the land itself (R11-16); the grounds per block (dirt and coarse dirt, a dead bush in
 * 40, a fringe thinning to nothing) and the trees judged by their trunks, a tree standing in the ring losing every leaf
 * and one outside it none, no leaf left with no log near (R11-7). 3 (the review, R11-17): from the pit's 3 x 3 floor a
 * dead-end tunnel 2 wide and 2 high runs {@value #TUNNEL_LENGTH} under the steps toward the way in's side, unlit (someone
 * went further); its mouth takes the two lowest treads of its strip (a drop of 3 at most), the seal kept round it.
 */
public final class PyramidShape implements Shape {
	/** The base widths of the three sizes. */
	public static final int[] BASES = {63, 95, 127};
	/** The shell's thickness (each course of the core is this far in from the course's face). */
	public static final int SHELL = 3;
	/** The rim walkway between the core's walls and the pit's opening. */
	public static final int RIM = 3;
	/** The grounds die out to this far past the base's half width, and at half density to {@link #GROUNDS_HALF}. */
	public static final int GROUNDS_FULL = 24;
	public static final int GROUNDS_HALF = 40;
	/** How deep a foundation fills at most. */
	public static final int FOUNDATION_MAX = 24;
	/** How far above the ground leaves and plants are looked for. */
	public static final int LEAVES_UP = 32;
	/** One face brick in this many is missing. */
	public static final int MISSING_ONE_IN = 300;
	/** How far in front of the way in the approach is cleared. */
	public static final int APPROACH = 5;
	/** How deep the dead-end cuts go into a face. */
	public static final int CUT_DEPTH_MIN = 6;
	public static final int CUT_DEPTH_MAX = 10;
	/** The shape's current version (R12-2). */
	public static final int VERSION = 3;
	/** Version 3: the dead-end tunnel's length from the pit's floor (R11-17). */
	public static final int TUNNEL_LENGTH = 8;
	/** Version 2: the plinth steps out at most this many courses under the base (where the land is that much lower). */
	public static final int PLINTH_MAX = FOUNDATION_MAX;
	/** Version 2: the cut to the way in reaches at most this far out through higher land. */
	public static final int SLOT_MAX = 24;
	/** Version 2: trees are judged within this many blocks past the ring, and a leaf by a log at most this far from it. */
	public static final int CANOPY_PAST = 7;
	public static final int LEAF_REACH = 4;

	private static final int BIT_SEALED = 4;
	private static final int BIT_LIGHT = 8;
	private static final int BIT_SEA = 16;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState BRICKS = Blocks.BRICKS.defaultBlockState();
	private static final BlockState MUD_BRICKS = Blocks.MUD_BRICKS.defaultBlockState();
	private static final BlockState MUD = Blocks.MUD.defaultBlockState();
	private static final BlockState DEAD_BUSH = Blocks.DEAD_BUSH.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
	private static final BlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.defaultBlockState();
	private static final BlockState PACKED_MUD = Blocks.PACKED_MUD.defaultBlockState();
	private static final BlockState BROWN_TERRACOTTA = Blocks.DYED_TERRACOTTA.brown().defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
	private static final BlockState SAND = Blocks.SAND.defaultBlockState();
	private static final BlockState COBBLESTONE = Blocks.COBBLESTONE.defaultBlockState();
	private static final BlockState TORCH = Blocks.TORCH.defaultBlockState();
	private static final BlockState GLOWSTONE = Blocks.GLOWSTONE.defaultBlockState();
	private static final BlockState SLAB = Blocks.BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

	/** A dead-end cut into a face: its cells, and the box the core keeps out of. */
	record Cut(Direction out, int layer, int lateral, int depth, BoundingBox cells, BoundingBox keepOut) {
	}

	private final Start start;
	/** The version it is built as (R12-2). */
	private final int version;
	/** Its highest course: {@code r} (version 1, a single block) or {@code r - 1} (version 2, a 3 x 3 level). */
	private final int top;
	private final int cx;
	private final int y0;
	private final int cz;
	private final Direction facing;
	private final Direction right;
	private final int size;
	private final int r;
	private final int p0;
	private final boolean sealed;
	private final boolean light;
	private final boolean sea;
	/** The sea form: the top water block over the centre when it was made (the water's surface). */
	private final int waterTop;
	private final long seed;
	private final BoundingBox box;
	private final BoundingBox footprint;
	private final List<Cut> cuts;

	public PyramidShape(Start start, Terrain terrain) {
		this.start = start;
		this.version = start.versionOr(VERSION);
		this.cx = start.origin().getX();
		this.y0 = start.origin().getY();
		this.cz = start.origin().getZ();
		this.facing = start.facing();
		this.right = facing.getClockWise();
		this.size = size(start.variant());
		this.r = (BASES[size] - 1) / 2;
		this.p0 = r - SHELL - RIM;
		this.top = version >= 2 ? r - 1 : r;
		this.sea = sea(start.variant());
		this.sealed = sea || sealed(start.variant());
		this.light = light(start.variant());
		this.seed = start.seed();
		this.waterTop = sea ? Math.max(terrain.surface(cx, cz), y0 - 1) : Integer.MIN_VALUE;
		int reach = r + GROUNDS_HALF + 1;
		int bottom = bottomY(y0, size, sea);
		this.box = new BoundingBox(cx - reach, bottom, cz - reach, cx + reach, topY(y0, size), cz + reach);
		this.footprint = new BoundingBox(cx - r, bottom, cz - r, cx + r, y0 + r, cz + r);
		this.cuts = cuts();
	}

	// --- the variant ---

	/** The variant for a size index (0 to 2) and the three flags. */
	public static int variant(int size, boolean sealed, boolean light, boolean sea) {
		return Math.clamp(size, 0, BASES.length - 1) | (sealed ? BIT_SEALED : 0) | (light ? BIT_LIGHT : 0) | (sea ? BIT_SEA : 0);
	}

	public static int size(int variant) {
		return Math.min(variant & 3, BASES.length - 1);
	}

	public static boolean sealed(int variant) {
		return (variant & BIT_SEALED) != 0;
	}

	public static boolean light(int variant) {
		return (variant & BIT_LIGHT) != 0;
	}

	public static boolean sea(int variant) {
		return (variant & BIT_SEA) != 0;
	}

	/** The base's half width for a size index. */
	public static int halfWidth(int size) {
		return (BASES[Math.clamp(size, 0, BASES.length - 1)] - 1) / 2;
	}

	/** The height (courses) for a size index: 32, 48, 64. */
	public static int height(int size) {
		return halfWidth(size) + 1;
	}

	/** The lowest cell a pyramid of this size with its lowest course at {@code y0} may write (its box's bottom). */
	public static int bottomY(int y0, int size, boolean sea) {
		int p0 = halfWidth(size) - SHELL - RIM;
		return Math.min(y0 - 2 - (sea ? 0 : p0) - Seal.REACH - 1, y0 - 1 - FOUNDATION_MAX);
	}

	/** The highest cell it may write (its box's top: the leaves cleared over the apex). */
	public static int topY(int y0, int size) {
		return y0 + Math.max(halfWidth(size), 16) + LEAVES_UP;
	}

	/** The steps from the rim down to the pit's 3 x 3 floor for a size index: 24, 40, 56. */
	public static int pitDepth(int size) {
		return halfWidth(size) - SHELL - RIM - 1;
	}

	// --- what it is, by position (pure: tests and the answers ask these) ---

	public int halfWidth() {
		return r;
	}

	/** The version it is built as (R12-2). */
	public int version() {
		return version;
	}

	/** Its highest course over the base ({@code r}; version 2: {@code r - 1}, a 3 x 3 level). */
	public int topCourse() {
		return top;
	}

	/** True if it stands on a plinth where the land falls away (version 2 on land). */
	public boolean plinth() {
		return version >= 2 && !sea;
	}

	public int height() {
		return r + 1;
	}

	/** The lowest course's height (the origin's y): the core's floor is the block under it. */
	public int baseY() {
		return y0;
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

	public boolean hasPit() {
		return !sea;
	}

	public boolean hasWayIn() {
		return !sealed;
	}

	public boolean sea() {
		return sea;
	}

	public boolean lit() {
		return light;
	}

	/** The pit opening's half width (the rim's inner edge is one further). */
	public int pitOpening() {
		return p0;
	}

	/** The steps from the rim down to the pit's floor. */
	public int pitDepth() {
		return p0 - 1;
	}

	/** The feet's height on the pit's 3 x 3 floor. */
	public int pitBottomY() {
		return y0 - p0;
	}

	/** The Chebyshev ring of a column. */
	public int ring(int x, int z) {
		return Math.max(Math.abs(x - cx), Math.abs(z - cz));
	}

	/** True for a cell of the solid (the shell, the core and the faces, before any cut or wear). */
	public boolean inMass(int x, int y, int z) {
		int layer = y - y0;
		return layer >= 0 && layer <= Math.min(r - ring(x, z), top);
	}

	/** True for a cell of the core (air inside the shell), cuts' keep-outs excluded. */
	public boolean core(int x, int y, int z) {
		int layer = y - y0;
		if (layer < 0 || ring(x, z) > r - SHELL - layer) {
			return false;
		}
		for (Cut cut : cuts) {
			if (cut.keepOut().isInside(x, y, z)) {
				return false;
			}
		}
		return true;
	}

	/** The pit's air half width at height {@code y}, or -1 where there is no pit air. */
	public int pitAirAt(int y) {
		if (!hasPit()) {
			return -1;
		}
		int j = y0 - 1 - y;
		return j < 0 || j >= p0 ? -1 : p0 - j;
	}

	/** True for a cell of the pit's air. */
	public boolean pit(int x, int y, int z) {
		int half = pitAirAt(y);
		return half >= 0 && ring(x, z) <= half;
	}

	/**
	 * True for a cell of the pit's brick lining (every tread and riser, the kerb on the rim's edge, the 3 x 3 floor; version
	 * 3: but the two treads its tunnel's mouth takes).
	 */
	public boolean lining(int x, int y, int z) {
		if (!hasPit() || y < y0 - 1 - p0 || y > y0 - 1 || pit(x, y, z) || tunnel(x, y, z)) {
			return false;
		}
		int d = ring(x, z);
		int above = pitAirAt(y + 1);
		int here = pitAirAt(y);
		return above >= 0 && d <= above || here >= 0 && d - 1 <= here || y == y0 - 1 && d == p0 + 1;
	}

	/** Inside: the core, the pit or its tunnel (never the shell, the way in, the cuts or the grounds). */
	@Override
	public Interior interior() {
		return (x, y, z) -> core(x, y, z) || pit(x, y, z) || tunnel(x, y, z);
	}

	/**
	 * True for a cell of the tunnel from the pit's floor (version 3, land): 2 wide (the way in's two sides), 2 high from
	 * the floor's feet height, from 2 out (the first tread) to {@value #TUNNEL_LENGTH} + 1 out toward the way in's side.
	 */
	public boolean tunnel(int x, int y, int z) {
		if (version < 3 || !hasPit() || y < pitBottomY() || y > pitBottomY() + 1) {
			return false;
		}
		int f = (x - cx) * facing.getStepX() + (z - cz) * facing.getStepZ();
		int side = (x - cx) * right.getStepX() + (z - cz) * right.getStepZ();
		return side >= 0 && side <= 1 && f >= 2 && f <= TUNNEL_LENGTH + 1;
	}

	/** The tunnel's cells (version 3, land), from its mouth to its end; empty for none. */
	public List<BlockPos> tunnelCells() {
		List<BlockPos> out = new ArrayList<>();
		if (version < 3 || !hasPit()) {
			return out;
		}
		int down = pitBottomY() - y0;
		for (int f = 2; f <= TUNNEL_LENGTH + 1; f++) {
			for (int side = 0; side <= 1; side++) {
				out.add(local(side, down, f));
				out.add(local(side, down + 1, f));
			}
		}
		return out;
	}

	/** The way in's cells (empty when sealed): two rows of three, the lower one starting at the outer mouth. */
	public List<BlockPos> wayIn() {
		List<BlockPos> out = new ArrayList<>();
		if (sealed) {
			return out;
		}
		for (int side = 0; side <= 1; side++) {
			for (int f = r; f >= r - 2; f--) {
				out.add(local(side, 0, f));
			}
			for (int f = r - 1; f >= r - 3; f--) {
				out.add(local(side, 1, f));
			}
		}
		return out;
	}

	/** The inner mouth's 2 x 2: the last cell of each of the way in's four rows, next to the core. */
	public List<BlockPos> innerMouth() {
		if (sealed) {
			return List.of();
		}
		return List.of(local(0, 0, r - 2), local(1, 0, r - 2), local(0, 1, r - 3), local(1, 1, r - 3));
	}

	/** The cell just inside the way in (the core's first floor cell past it, feet height), between the two rows. */
	public BlockPos insideMouth() {
		return local(0, 0, r - 3);
	}

	/**
	 * The bricks of the core's inner face beside the mouth, {@code 2} to {@code 6} to either side of it in the lowest
	 * three courses (what 16b moves into the mouth), nearest first.
	 */
	public List<BlockPos> besideMouth() {
		List<BlockPos> out = new ArrayList<>();
		if (sealed) {
			return out;
		}
		for (int off = 2; off <= 6; off++) {
			for (int layer = 0; layer <= 2; layer++) {
				int f = r - SHELL - layer + 1;
				out.add(local(-off, layer, f));
				out.add(local(1 + off, layer, f));
			}
		}
		return out;
	}

	/** A position in the frame of the way in: {@code side} to the right, {@code layer} up, {@code f} out toward the front. */
	public BlockPos local(int side, int layer, int f) {
		return new BlockPos(cx + right.getStepX() * side + facing.getStepX() * f, y0 + layer, cz + right.getStepZ() * side + facing.getStepZ() * f);
	}

	/** The pit's ring of feet cells {@code steps} down from the rim (the tread they stand on is under them), or empty. */
	public int ringAtSteps(int steps) {
		if (!hasPit() || steps < 1 || steps > pitDepth() + 1) {
			return -1;
		}
		return Math.max(1, p0 - steps + 1);
	}

	/** The cells of each dead-end cut. */
	public List<BoundingBox> cutCells() {
		return cuts.stream().map(Cut::cells).toList();
	}

	/** The box round each cut that the core keeps out of. */
	public List<BoundingBox> cutKeepOuts() {
		return cuts.stream().map(Cut::keepOut).toList();
	}

	// --- Shape ---

	@Override
	public BoundingBox box() {
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	/** The pit's floor (where the torch stands) on land, the core's floor centre at sea. */
	@Override
	public BlockPos anchor() {
		return hasPit() ? new BlockPos(cx, pitBottomY(), cz) : new BlockPos(cx, y0, cz);
	}

	@Override
	public BlockPos entrance() {
		return local(0, 0, r);
	}

	@Override
	public Direction entranceFacing() {
		return facing;
	}

	/** The top block's middle (version 2: the middle of its 3 x 3 top level; one stands on it a block over). */
	@Override
	public Optional<BlockPos> apex() {
		return Optional.of(new BlockPos(cx, y0 + top, cz));
	}

	@Override
	public void fill(Writer w, BoundingBox clip) {
		// 1. The grounds, the clearing over the footprint, the foundation under it (version 2: the plinth, the trees).
		Draw.columns(w, box, (x, z) -> prepare(w, x, z));
		if (version >= 2 && !sea) {
			canopies(w, clip);
		}
		// 2. The seal round what is underground of the inside (the pit and the floor), before anything is opened.
		Seal.around(w, clip, this::sealRange, Seal.REACH, (x, y, z) -> BRICKS);
		if (version >= 3 && hasPit()) {
			// The tunnel's own (a column holds one range: the pit's air over it is sealed by the first pass, and what this
			// pass bricks in the pit's air the mass opens again).
			Seal.around(w, clip, this::tunnelRange, Seal.REACH, (x, y, z) -> BRICKS);
		}
		// 3. The mass: shell, core, floor, the faces with his pass; then the pit and its lining.
		Draw.columns(w, footprint, (x, z) -> mass(w, x, z));
		for (BlockPos cell : tunnelCells()) {
			w.set(cell, AIR);
		}
		// 4. The openings: the way in and its approach, the cuts.
		if (!sealed) {
			for (BlockPos cell : wayIn()) {
				w.set(cell, AIR);
			}
			for (int side = 0; side <= 1; side++) {
				for (int f = r; f >= r - 2; f--) {
					w.set(local(side, -1, f), DIRT);
				}
			}
			if (version >= 2) {
				slot(w);
			} else {
				approach(w);
			}
		}
		for (Cut cut : cuts) {
			Draw.fill(w, cut.cells(), sea && cut.cells().minY() <= waterTop ? WATER : AIR);
		}
		// 5. The pit's light, and the apex.
		if (hasPit()) {
			w.set(cx, pitBottomY(), cz, COBBLESTONE);
			w.set(cx, pitBottomY() + 1, cz, TORCH);
		}
		if (version >= 2) {
			// The point taken off: its top a 3 x 3 level, lit on a lit one (R11-1, R11-4).
			if (light) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						w.set(cx + dx, y0 + top, cz + dz, GLOWSTONE);
					}
				}
			}
		} else {
			w.set(cx, y0 + r, cz, light ? GLOWSTONE : BRICKS);
		}
	}

	// --- 1. grounds and footprint ---

	private void prepare(Writer w, int x, int z) {
		int dx = x - cx;
		int dz = z - cz;
		int d = Math.max(Math.abs(dx), Math.abs(dz));
		if (d <= r) {
			// Over the footprint: whatever grew there goes (the terrain stays and buries the lowest courses where higher).
			int top = y0 + Math.min(r - d, this.top);
			int ground = w.ground(x, z);
			for (int y = top + 1; y <= Math.max(top, ground) + LEAVES_UP && y <= box.maxY(); y++) {
				BlockState state = w.get(x, y, z);
				if (!state.isAir() && (Vegetation.dies(state) || Vegetation.isSnowLayer(state) || sea && isSeaPlant(state))) {
					w.set(x, y, z, sea && y <= waterTop ? WATER : AIR);
				}
			}
			Draw.foundation(w, x, z, y0 - 1, BRICKS, FOUNDATION_MAX);
			return;
		}
		double dist = Math.sqrt((double) dx * dx + (double) dz * dz) - (r + 0.5);
		if (version >= 2 && !sea) {
			if (dist <= GROUNDS_HALF) {
				grounds(w, x, z, dx, dz, dist);
			}
			if (d - r <= PLINTH_MAX) {
				plinth(w, x, z, dx, dz, d);
			}
			return;
		}
		if (dist > GROUNDS_HALF) {
			return;
		}
		if (sea) {
			if (dist <= GROUNDS_FULL) {
				int ground = w.ground(x, z);
				for (int y = ground + 1; y <= waterTop; y++) {
					if (isSeaPlant(w.get(x, y, z))) {
						w.set(x, y, z, WATER);
					}
				}
			}
			return;
		}
		boolean full = dist <= GROUNDS_FULL;
		// Past the ring's sharp edge, a fringe of small dead patches thinning out to nothing (half of it on average);
		// the trees there keep their leaves, so the ring's edge cuts the trees that stand on it.
		if (!full) {
			double t = (dist - GROUNDS_FULL) / (GROUNDS_HALF - GROUNDS_FULL);
			if (Draw.unit(seed ^ 0x6D2B79F5L, dx, 0, dz) > 0.85 - 0.7 * t) {
				return;
			}
		}
		int ground = w.ground(x, z);
		if (full) {
			Draw.deadGround(w, x, z, LEAVES_UP);
		} else {
			BlockState top = w.get(x, ground, z);
			if (Vegetation.isGrassGround(top) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM)) {
				w.set(x, ground, z, DIRT);
			}
			BlockState over = w.get(x, ground + 1, z);
			if (Vegetation.isPlant(over) || Vegetation.isSnowLayer(over)) {
				w.set(x, ground + 1, z, AIR);
				if (w.get(x, ground + 2, z).is(Blocks.TALL_GRASS) || w.get(x, ground + 2, z).is(Blocks.LARGE_FERN)) {
					w.set(x, ground + 2, z, AIR);
				}
			}
		}
		// Nearer the base the dirt is coarser (inside the ring only).
		BlockState top = w.get(x, ground, z);
		if (full && top.is(Blocks.DIRT)) {
			double near = 1.0 - Math.clamp(dist / GROUNDS_FULL, 0.0, 1.0);
			if (noise(seed ^ 0x2545F491L, dx / 5.0, dz / 5.0) + 0.45 * near > 0.78) {
				w.set(x, ground, z, COARSE_DIRT);
			}
		}
	}

	/**
	 * Version 2's grounds in one column outside the footprint (R11-7): inside the ring dirt and coarse dirt, block by block
	 * (seven in ten dirt), a dead bush in 40 and nothing else growing; past its sharp edge the same by a chance falling from
	 * 85 % to nothing at {@link #GROUNDS_HALF}. Leaves are left to {@link #canopies} (trees go by their trunks).
	 */
	private void grounds(Writer w, int x, int z, int dx, int dz, double dist) {
		boolean full = dist <= GROUNDS_FULL;
		if (!full) {
			double t = (dist - GROUNDS_FULL) / (GROUNDS_HALF - GROUNDS_FULL);
			if (Draw.unit(seed ^ 0x6D2B79F5L, dx, 0, dz) >= 0.85 * (1.0 - t)) {
				return;
			}
		}
		int ground = w.ground(x, z);
		BlockState top = w.get(x, ground, z);
		boolean earth = Vegetation.isGrassGround(top) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.DIRT_PATH) || top.is(Blocks.DIRT)
				|| top.is(Blocks.COARSE_DIRT) || top.is(Blocks.ROOTED_DIRT);
		if (earth) {
			w.set(x, ground, z, Draw.unit(seed ^ 0x2545F491L, dx, 1, dz) < 0.3 ? COARSE_DIRT : DIRT);
		}
		for (int y = ground + 1; y <= ground + 3; y++) {
			BlockState over = w.get(x, y, z);
			if (Vegetation.isPlant(over) || Vegetation.isSnowLayer(over) || over.is(Blocks.VINE)) {
				w.set(x, y, z, AIR);
			}
		}
		if (full && earth && w.get(x, ground + 1, z).isAir() && Draw.unit(seed ^ 0xB054DL, dx, 2, dz) < 1.0 / 40) {
			w.set(x, ground + 1, z, DEAD_BUSH);
		}
	}

	/**
	 * Version 2's plinth in one column {@code d - r} out from the base (R12-3, R13-3): where the land lies lower than the
	 * course the pyramid would have there ({@code y0 - k}), brick from that course down to the land (at most as deep as the
	 * foundation), its top a step of the faces' own (worn, damp), so the steps go on down a block out each course and
	 * nothing at its foot drops more than a block.
	 */
	private void plinth(Writer w, int x, int z, int dx, int dz, int d) {
		int k = d - r;
		int course = y0 - k;
		int ground = w.ground(x, z);
		if (ground >= course) {
			return;
		}
		int bottom = Math.max(ground + 1, y0 - 1 - FOUNDATION_MAX);
		for (int y = course - 1; y >= bottom; y--) {
			w.set(x, y, z, BRICKS);
		}
		BlockState step = face(x, z, dx, dz, d, -k);
		w.set(x, course, z, step.isAir() ? BRICKS : step);
	}

	/**
	 * Version 2's trees by their trunks (R11-7): in the ring and {@value #CANOPY_PAST} past it, each leaf goes with the
	 * nearest log within {@value #LEAF_REACH} (a tree standing in the ring loses every leaf, one standing outside it keeps
	 * them all), and a leaf with no log that near goes too where it hangs over the ring or near it (nothing floats). Only
	 * this chunk's own logs are seen (a tree across a chunk's edge is judged in each by what stands there).
	 */
	private void canopies(Writer w, BoundingBox clip) {
		List<int[]> logs = new ArrayList<>();
		int reach = r + GROUNDS_HALF;
		for (int x = Math.max(clip.minX(), cx - reach); x <= Math.min(clip.maxX(), cx + reach); x++) {
			for (int z = Math.max(clip.minZ(), cz - reach); z <= Math.min(clip.maxZ(), cz + reach); z++) {
				double dist = ringDistance(x, z);
				if (dist > GROUNDS_FULL + CANOPY_PAST + LEAF_REACH || ring(x, z) <= r) {
					continue;
				}
				int ground = w.ground(x, z);
				for (int y = ground + 1; y <= ground + LEAVES_UP; y++) {
					if (w.get(x, y, z).is(net.minecraft.tags.BlockTags.LOGS)) {
						logs.add(new int[] {x, y, z, dist <= GROUNDS_FULL ? 1 : 0});
					}
				}
			}
		}
		for (int x = Math.max(clip.minX(), cx - reach); x <= Math.min(clip.maxX(), cx + reach); x++) {
			for (int z = Math.max(clip.minZ(), cz - reach); z <= Math.min(clip.maxZ(), cz + reach); z++) {
				double dist = ringDistance(x, z);
				if (dist > GROUNDS_FULL + CANOPY_PAST || ring(x, z) <= r) {
					continue;
				}
				int ground = w.ground(x, z);
				for (int y = ground + 1; y <= ground + LEAVES_UP; y++) {
					BlockState state = w.get(x, y, z);
					if (!state.is(net.minecraft.tags.BlockTags.LEAVES) && !Vegetation.isLeafy(state)) {
						continue;
					}
					int nearest = Integer.MAX_VALUE;
					boolean inside = false;
					for (int[] log : logs) {
						int dd = Math.max(Math.abs(log[0] - x), Math.max(Math.abs(log[1] - y), Math.abs(log[2] - z)));
						if (dd <= LEAF_REACH && dd < nearest) {
							nearest = dd;
							inside = log[3] == 1;
						}
					}
					if (nearest == Integer.MAX_VALUE ? dist <= GROUNDS_FULL + 3 : inside) {
						w.set(x, y, z, AIR);
					}
				}
			}
		}
	}

	/** How far a column stands past the base's circle ({@code r + 0.5} from the middle; negative inside it). */
	private double ringDistance(int x, int z) {
		double dx = x - cx;
		double dz = z - cz;
		return Math.sqrt(dx * dx + dz * dz) - (r + 0.5);
	}

	private static boolean isSeaPlant(BlockState state) {
		return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
				|| state.is(Blocks.SEA_PICKLE);
	}

	// --- 2. the seal ---

	/** The tunnel's heights in each of its columns (version 3), else none. */
	private long tunnelRange(int x, int z) {
		return tunnel(x, pitBottomY(), z) ? Seal.range(pitBottomY(), pitBottomY() + 1) : Seal.NONE;
	}

	/** What the seal keeps in each column: the pit's air and the floor (above the ground, the shell itself is the seal). */
	private long sealRange(int x, int z) {
		int d = ring(x, z);
		if (d > r - SHELL) {
			return Seal.NONE;
		}
		if (hasPit() && d <= p0) {
			return Seal.range(y0 - 1 - (p0 - Math.max(d, 1)), y0 - 1);
		}
		return Seal.range(y0 - 1, y0 - 1);
	}

	// --- 3. the mass ---

	private void mass(Writer w, int x, int z) {
		int dx = x - cx;
		int dz = z - cz;
		int d = Math.max(Math.abs(dx), Math.abs(dz));
		if (d > r) {
			return;
		}
		int surface = Math.min(r - d, top);
		for (int layer = 0; layer <= surface; layer++) {
			int y = y0 + layer;
			if (!w.inside(x, y, z)) {
				continue;
			}
			if (core(x, y, z)) {
				w.set(x, y, z, AIR);
			} else if (layer == surface) {
				w.set(x, y, z, face(x, z, dx, dz, d, layer));
			} else {
				w.set(x, y, z, BRICKS);
			}
		}
		// The floor of the core: the ground, dead (sand on the sea floor); the pit under it.
		if (d <= r - SHELL) {
			if (hasPit() && d <= p0 + 1) {
				pitColumn(w, x, z, d);
			} else {
				w.set(x, y0 - 1, z, sea ? SAND : floor(dx, dz));
			}
		}
	}

	private BlockState floor(int dx, int dz) {
		return noise(seed ^ 0x51ED270BL, dx / 4.0, dz / 4.0) + 0.2 * Draw.unit(seed, dx, -1, dz) > 0.62 ? COARSE_DIRT : DIRT;
	}

	private void pitColumn(Writer w, int x, int z, int d) {
		for (int y = y0 - 1; y >= y0 - 1 - p0; y--) {
			if (pit(x, y, z)) {
				w.set(x, y, z, AIR);
			} else if (lining(x, y, z)) {
				w.set(x, y, z, BRICKS);
			}
		}
	}

	/**
	 * The outer cell of a column (a step's tread and riser): bricks, worn where the face's streaks run (a stair, a slab,
	 * or gone), stained with earth on the lowest courses and under each cut, one in {@value #MISSING_ONE_IN} missing.
	 * The top courses stay sharp.
	 */
	private BlockState face(int x, int z, int dx, int dz, int d, int layer) {
		int adx = Math.abs(dx);
		int adz = Math.abs(dz);
		Direction out;
		int lateral;
		int gap;
		if (adx >= adz) {
			out = dx >= 0 ? Direction.EAST : Direction.WEST;
			gap = adx - adz;
		} else {
			out = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
			gap = adz - adx;
		}
		// Across the face, as the cuts count it (along the face's clockwise turn).
		Direction along = out.getClockWise();
		lateral = dx * along.getStepX() + dz * along.getStepZ();
		int y = y0 + layer;
		boolean wet = sea && y <= waterTop;
		BlockState empty = wet ? WATER : AIR;
		if (layer >= r - 3) {
			return BRICKS;
		}
		if (Draw.chance(seed ^ 0x3C6EF372L, dx, layer, dz, 1.0 / MISSING_ONE_IN)) {
			return empty;
		}
		// The wear: streaks running down each face (slow across, slower down), more of it along the arrises and higher
		// up; a worn step is a stair, more worn a slab, most worn gone.
		long faceSeed = seed ^ (0x9E3779B97F4A7C15L * (out.get2DDataValue() + 1));
		double streak = noise(faceSeed, lateral / 2.5, layer / 16.0);
		double fine = noise(faceSeed ^ 0x77L, lateral / 1.3, layer / 2.2);
		double arris = Math.max(0.0, 1.0 - gap / 3.0);
		double wear = streak * 0.8 + fine * 0.2 + arris * 0.3 + 0.1 * layer / r - 0.02;
		if (wear > 0.93) {
			return empty;
		}
		if (wear > 0.81) {
			return wet ? SLAB.setValue(BlockStateProperties.WATERLOGGED, true) : SLAB;
		}
		if (wear > 0.71 && gap > 0) {
			BlockState stair = Blocks.BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, out.getOpposite()).setValue(StairBlock.HALF, Half.BOTTOM);
			return wet ? stair.setValue(BlockStateProperties.WATERLOGGED, true) : stair;
		}
		// The earth's stain: damp wicked up the lowest courses, its top ragged in drips; and a run under each cut.
		boolean stained = false;
		double drip = noise(faceSeed ^ 0x1234L, lateral / 2.2, 0.5);
		double damp = 1.0 + 4.0 * drip * drip;
		if (layer < damp) {
			// Version 2: drips over brick, not a band (R11-16).
			stained = Draw.unit(seed ^ 0x5851F42DL, dx, layer, dz) < (version >= 2 ? layer < damp - 1.0 ? 0.62 : 0.28 : layer < damp - 1.0 ? 0.96 : 0.35);
		}
		if (stained) {
			if (version >= 2) {
				return Draw.unit(seed ^ 0x14057B7EL, dx, layer, dz) < 0.65 ? BROWN_TERRACOTTA : MUD;
			}
			return Draw.unit(seed ^ 0x14057B7EL, dx, layer, dz) < 0.65 ? MUD_BRICKS : PACKED_MUD;
		}
		// Under each cut, a dark run down the steps, as if the hole had wept.
		for (Cut cut : cuts) {
			int run = 12 + cut.depth();
			if (cut.out() == out && layer < cut.layer() && layer >= cut.layer() - run) {
				int across = lateral - cut.lateral();
				double fade = 1.0 - (double) (cut.layer() - layer) / run;
				double p = across >= 0 && across <= 1 ? 0.55 + 0.45 * fade : (across == -1 || across == 2) && fade > 0.55 ? 0.6 * fade : 0;
				if (p > 0 && Draw.unit(seed ^ 0x2F6B3A1DL, dx, layer, dz) < p) {
					return Draw.unit(seed ^ 0x3A9F1E27L, dx, layer, dz) < 0.6 ? BROWN_TERRACOTTA : version >= 2 ? MUD : MUD_BRICKS;
				}
			}
		}
		return BRICKS;
	}

	// --- 4. the openings ---

	/** In front of the way in: anything standing in a 4 x 3 x {@value #APPROACH} space goes, and the floor holds a foot. */
	private void approach(Writer w) {
		for (int side = -1; side <= 2; side++) {
			for (int f = r + 1; f <= r + APPROACH; f++) {
				BlockPos floor = local(side, -1, f);
				if (w.inside(floor) && !Draw.solidGround(w.get(floor))) {
					w.set(floor, DIRT);
					// Earth under it down to the land where the land in front is lower (SC2-H: no slab in the air).
					Draw.foundation(w, floor.getX(), floor.getZ(), floor.getY() - 1, DIRT, FOUNDATION_MAX);
				}
				for (int layer = 0; layer <= 2; layer++) {
					BlockPos cell = local(side, layer, f);
					if (w.inside(cell) && !w.get(cell).isAir()) {
						w.set(cell, AIR);
					}
				}
			}
		}
	}

	/**
	 * Version 2's way to the way in (R11-16): where the land in front stands higher than the base, a cut exactly 2 wide on
	 * the way in's own line, everything over its floor taken, its sides the land itself (what would fall there made rock:
	 * sandstone for sand), out to where the land is no higher than the floor, at most {@value #SLOT_MAX}; where the land is
	 * lower the plinth's steps lead up to the mouth.
	 */
	private void slot(Writer w) {
		// Nothing standing in front of the mouth (a bare trunk, a bush), as the first version's approach.
		for (int side = 0; side <= 1; side++) {
			for (int f = r + 1; f <= r + APPROACH; f++) {
				for (int layer = 0; layer <= 2; layer++) {
					BlockPos cell = local(side, layer, f);
					if (w.inside(cell) && !w.get(cell).isAir() && !Draw.solidGround(w.get(cell))) {
						w.set(cell, AIR);
					}
				}
			}
		}
		int floorY = y0 - 1;
		int end = r;
		for (int f = r + 1; f <= r + SLOT_MAX; f++) {
			boolean higher = false;
			for (int side = 0; side <= 1; side++) {
				BlockPos floor = local(side, -1, f);
				higher |= w.ground(floor.getX(), floor.getZ()) > floorY;
			}
			if (!higher) {
				break;
			}
			end = f;
		}
		if (end == r) {
			return;
		}
		for (int f = r; f <= end; f++) {
			for (int side = 0; side <= 1; side++) {
				BlockPos floor = local(side, -1, f);
				if (f > r && w.inside(floor) && !Draw.solidGround(w.get(floor))) {
					w.set(floor, DIRT);
					Draw.foundation(w, floor.getX(), floor.getZ(), floor.getY() - 1, DIRT, FOUNDATION_MAX);
				}
				int land = w.ground(floor.getX(), floor.getZ());
				// At the face only over its base course (the mouth's upper half), out in front everything over the floor.
				for (int y = f == r ? y0 + 1 : floorY + 1; y <= Math.max(land, floorY + 3) + LEAVES_UP; y++) {
					if (w.inside(floor.getX(), y, floor.getZ()) && !w.get(floor.getX(), y, floor.getZ()).isAir()) {
						w.set(floor.getX(), y, floor.getZ(), AIR);
					}
				}
			}
			for (int side : new int[] {-1, 2}) {
				BlockPos wall = local(side, -1, f);
				int land = w.ground(wall.getX(), wall.getZ());
				for (int y = wall.getY() + 1; y <= land; y++) {
					BlockState state = w.get(wall.getX(), y, wall.getZ());
					if (w.inside(wall.getX(), y, wall.getZ()) && (state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock || Seal.leaks(state))) {
						w.set(wall.getX(), y, wall.getZ(), state.is(Blocks.SAND) ? SANDSTONE : state.is(Blocks.RED_SAND) ? RED_SANDSTONE : STONE);
					}
				}
			}
		}
		// Round the cut, where its side stands more than 3 over its floor, a lip (R12-3: nobody walks into it by accident).
		for (int f = r - 1; f <= end + 1; f++) {
			for (int side = -1; side <= 2; side++) {
				if (side >= 0 && side <= 1 && f >= r && f <= end) {
					continue;
				}
				BlockPos column = local(side, -1, f);
				if (!w.inside(column)) {
					continue;
				}
				int top = Draw.standingTop(w, column.getX(), column.getZ(), floorY + 64, floorY - 8);
				if (top - floorY > 3) {
					Draw.lip(w, column.getX(), column.getZ(), top);
				}
			}
		}
	}

	/**
	 * One to three dead-end 2 x 2 cuts into the faces, 4 courses up to half the height, {@value #CUT_DEPTH_MIN} to
	 * {@value #CUT_DEPTH_MAX} deep, the core kept {@code Seal.REACH} away from each (the masonry round a deeper end hangs
	 * into the vault like a beam). The first is in the
	 * face of the way in (the one you walk up to), a third to a half of the way up and off to one side of the mouth.
	 */
	private List<Cut> cuts() {
		List<Cut> out = new ArrayList<>();
		int count = Draw.between(seed, 0x43555453L, 1, 3);
		int top = Math.max(5, (r + 1) / 2);
		for (int i = 0; i < count; i++) {
			boolean first = i == 0;
			Direction face = first ? facing : Direction.from2DDataValue(Draw.between(seed, 0x100L + i, 0, 3));
			int layer = Draw.between(seed, 0x200L + i, first ? Math.max(4, (r + 1) / 3) : 4, top);
			int halfFace = r - layer;
			int span = halfFace - 6;
			if (span < 1) {
				continue;
			}
			int lateral = Draw.between(seed, 0x300L + i, -span, span - 1);
			if (first) {
				int side = Draw.between(seed, 0x500L, 0, 1) == 0 ? -1 : 1;
				lateral = Math.clamp(side * Draw.between(seed, 0x600L, 4, 12), -span, span - 1);
			}
			int depth = Draw.between(seed, 0x400L + i, CUT_DEPTH_MIN, CUT_DEPTH_MAX);
			if (face == facing && Math.abs(lateral) <= 3 && layer <= 5) {
				continue;
			}
			Direction along = face.getClockWise();
			int outer = r - layer;
			int inner = outer - depth + 1;
			BlockPos a = new BlockPos(cx + face.getStepX() * inner + along.getStepX() * lateral, y0 + layer, cz + face.getStepZ() * inner + along.getStepZ() * lateral);
			BlockPos b = new BlockPos(cx + face.getStepX() * outer + along.getStepX() * (lateral + 1), y0 + layer + 1,
					cz + face.getStepZ() * outer + along.getStepZ() * (lateral + 1));
			BoundingBox cells = BoundingBox.fromCorners(a, b);
			boolean clash = false;
			for (Cut other : out) {
				if (other.keepOut().intersects(cells)) {
					clash = true;
				}
			}
			if (!clash) {
				out.add(new Cut(face, layer, lateral, depth, cells, cells.inflatedBy(Seal.REACH)));
			}
		}
		return List.copyOf(out);
	}

	// --- noise ---

	/** A smooth value noise in [0, 1] over the plane (a hash at each lattice point, smoothstep between). */
	static double noise(long seed, double x, double y) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		double fx = x - x0;
		double fy = y - y0;
		double sx = fx * fx * (3 - 2 * fx);
		double sy = fy * fy * (3 - 2 * fy);
		double a = Hash.unit(Hash.of(seed, x0, y0));
		double b = Hash.unit(Hash.of(seed, x0 + 1, y0));
		double c = Hash.unit(Hash.of(seed, x0, y0 + 1));
		double e = Hash.unit(Hash.of(seed, x0 + 1, y0 + 1));
		double top = a + (b - a) * sx;
		double bottom = c + (e - c) * sx;
		return top + (bottom - top) * sy;
	}
}
