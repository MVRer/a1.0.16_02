package com.forzacode.a1016_02.world.sc2.s20;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.gen.Vegetation;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Frame;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Seal;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Writer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Feature 20's shape (docs/SELF_CREATED_2.md, 20): a square of whole chunks cut flat into the land at one height. Inside
 * it everything over the floor is taken away (the hill it cuts into stands round it as straight cliffs, sealed two deep
 * so no cave, water or lava opens into it), the floor is grass on two dirt on one course of bedrock, and where the land
 * falls away the floor is built up under that with earth to the ground, so its sides stand straight. In its middle a
 * little building facing the origin: a cobblestone course, oak plank walls three high, one pane a side, a flat plank
 * roof, a mossy cobblestone floor, a doorway with no door and two torches burning on the back wall.
 *
 * <p>Versions (R12-2, {@link Start#versionOr}): 1, as first built; 2 (the review): its edges fair where the design keeps
 * them (R12-3, R13-3): where the land falls away from a side by 2 or more, that side's edge raised a block, a kerb of turf
 * (the plan's "raised earth edge"), and where a cut side stands more than 3 over the floor, a lip on the cliff's top a block
 * over the land coming down to it, so nobody walks over either by accident; the building of the first house's
 * proportions (two plank courses over the cobblestone, the roof on them, so the doorway reaches near the eaves), a window
 * of two glass blocks at eye height in each wall, and its two torches on the side walls a block in from the doorway, so
 * the light spills out of it onto the grass at night (R11-5).
 *
 * <p>The start's origin is the square's middle (a chunk corner) at the floor's height; its variant the side in chunks.
 * The place's anchor is the middle of the building's inside at its floor, its mouth the cell outside the doorway, its
 * inside the air over the square up to {@value #INSIDE_UP} high.
 */
public final class FloorShape implements Shape {
	/** The cliffs' seal reaches this far outside the square. */
	public static final int REACH = Seal.REACH;
	/** The box reaches this far under the lowest ground found (the built-up sides' footing). */
	public static final int BELOW = 16;
	/** The box reaches this far over the highest ground found (trees standing on it are taken too). */
	public static final int ABOVE = 40;
	/** A player is inside up to this high over the floor. */
	public static final int INSIDE_UP = 16;
	/** The building's side. */
	public static final int HOUSE = 7;
	/** Ground samples a side when the shape is made (25 in all). */
	static final int GRID = 5;
	/** The shape's current version (R12-2). */
	public static final int VERSION = 2;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
	private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
	private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
	private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
	private static final BlockState PANE = Blocks.GLASS_PANE.defaultBlockState();
	private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
	/** The built-up sides' face: earth, a little coarse and rooted. */
	private static final Draw.Mix FACE = Draw.Mix.of(DIRT, 14).and(Blocks.COARSE_DIRT.defaultBlockState(), 4).and(Blocks.ROOTED_DIRT.defaultBlockState(), 2);

	private final Start start;
	/** The version it is built as (R12-2). */
	private final int version;
	private final int side;
	private final int minX;
	private final int minZ;
	private final int maxX;
	private final int maxZ;
	private final int floor;
	private final BoundingBox box;
	private final Frame house;

	public FloorShape(Start start, Terrain terrain) {
		this.start = start;
		this.version = start.versionOr(VERSION);
		this.side = sideOf(start.variant());
		BlockPos o = start.origin();
		this.minX = o.getX() - side / 2;
		this.minZ = o.getZ() - side / 2;
		this.maxX = minX + side - 1;
		this.maxZ = minZ + side - 1;
		this.floor = o.getY();
		int lo = floor;
		int hi = floor;
		for (int i = 0; i < GRID; i++) {
			for (int j = 0; j < GRID; j++) {
				int g = terrain.ground(minX + Math.round((side - 1) * i / (GRID - 1.0F)), minZ + Math.round((side - 1) * j / (GRID - 1.0F)));
				lo = Math.min(lo, g);
				hi = Math.max(hi, g);
			}
		}
		int bottom = Math.min(lo, floor - 4) - BELOW;
		this.box = new BoundingBox(minX - REACH, bottom, minZ - REACH, maxX + REACH, hi + ABOVE, maxZ + REACH);
		this.house = Frame.square(new BlockPos(o.getX() - HOUSE / 2, floor, o.getZ() - HOUSE / 2), HOUSE, start.facing());
	}

	/** The side in blocks of a variant (whole chunks, 2 to 8). */
	public static int sideOf(int variant) {
		return Math.clamp(variant & 0xF, 2, 8) * 16;
	}

	/** The variant of a side in blocks. */
	public static int variant(int sideBlocks) {
		return Math.clamp(sideBlocks / 16, 2, 8);
	}

	/** The floor's own cells of a recorded place: the square, at the floor's height. */
	public static BoundingBox floorOf(Remains.Place place) {
		BoundingBox b = place.box();
		int y = place.anchor().getY() - 1;
		return new BoundingBox(b.minX() + REACH, y, b.minZ() + REACH, b.maxX() - REACH, y, b.maxZ() - REACH);
	}

	public int side() {
		return side;
	}

	/** The version it is built as (R12-2). */
	public int version() {
		return version;
	}

	/** The building's roof course over the floor (5; version 2: 4). */
	public int roof() {
		return version >= 2 ? 4 : 5;
	}

	public int floorY() {
		return floor;
	}

	/** The square at the floor's height. */
	public BoundingBox square() {
		return new BoundingBox(minX, floor, minZ, maxX, floor, maxZ);
	}

	public Frame house() {
		return house;
	}

	@Override
	public BoundingBox box() {
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	@Override
	public BlockPos anchor() {
		return house.pos(HOUSE / 2, 1, -(HOUSE / 2));
	}

	@Override
	public BlockPos entrance() {
		return house.pos(HOUSE / 2, 1, 1);
	}

	@Override
	public Direction entranceFacing() {
		return start.facing();
	}

	@Override
	public Interior interior() {
		return Interior.box(new BoundingBox(minX, floor + 1, minZ, maxX, floor + INSIDE_UP, maxZ));
	}

	boolean inSquare(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	private boolean onEdge(int x, int z) {
		return x == minX || x == maxX || z == minZ || z == maxZ;
	}

	@Override
	public void fill(Writer w, BoundingBox clip) {
		Draw.columns(w, box, (x, z) -> {
			if (inSquare(x, z)) {
				square(w, x, z);
			} else {
				seal(w, x, z);
			}
		});
		if (version >= 2) {
			Draw.columns(w, box, (x, z) -> {
				if (inSquare(x, z)) {
					kerb(w, x, z);
				} else if (ringOf(x, z) == 1) {
					lip(w, x, z);
				}
			});
		}
		building(w);
	}

	/** How far a column outside the square is from it (Chebyshev; 0 inside). */
	public int ringOf(int x, int z) {
		int dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
		int dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0;
		return Math.max(dx, dz);
	}

	/**
	 * Version 2: an edge column of the square whose land outside lies 2 or more under the floor (a built-up side) is
	 * raised a block, a kerb of turf, so walking off the floor there takes a jump (R12-3, R13-3).
	 */
	private void kerb(Writer w, int x, int z) {
		if (!onEdge(x, z)) {
			return;
		}
		int low = Integer.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!inSquare(x + dx, z + dz)) {
					low = Math.min(low, w.ground(x + dx, z + dz));
				}
			}
		}
		if (low <= floor - 2) {
			w.set(x, floor, z, DIRT);
			w.set(x, floor + 1, z, GRASS);
		}
	}

	/**
	 * Version 2: a column just outside a cut side standing more than 3 over the floor gets a lip on the cliff's top, a block
	 * over the highest land coming down to it (the land's own: turf over earth, stone over stone), so whoever walks down the
	 * hill toward the cliff meets it and crossing it is a deliberate jump (R12-3, R13-3).
	 */
	private void lip(Writer w, int x, int z) {
		int top = Draw.standingTop(w, x, z, box.maxY(), floor);
		if (top - floor <= 3) {
			return;
		}
		int out = top;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (ringOf(x + dx, z + dz) == 2) {
					out = Math.max(out, Math.max(w.ground(x + dx, z + dz), w.surface(x + dx, z + dz)));
				}
			}
		}
		BlockState topState = w.get(x, top, z);
		boolean turf = topState.is(Blocks.GRASS_BLOCK) || topState.is(Blocks.PODZOL) || topState.is(Blocks.MYCELIUM);
		BlockState fill = turf ? DIRT : Draw.solidGround(topState) ? topState : STONE;
		BlockState cap = turf ? topState.getBlock().defaultBlockState() : fill;
		int lipTop = Math.max(top, out) + 1;
		if (turf) {
			w.set(x, top, z, DIRT);
		}
		for (int y = top + 1; y <= lipTop; y++) {
			BlockState here = w.get(x, y, z);
			if (here.isAir() || Vegetation.isPlant(here) || Vegetation.isSnowLayer(here) || here.is(BlockTags.LEAVES)) {
				w.set(x, y, z, y == lipTop ? cap : fill);
			}
		}
	}

	/**
	 * Outside the square, within {@link #REACH} of it, where the land stands over the floor: the cliff's air (caves) and
	 * fluid under the ground, and water over it, become earth near the top and stone deeper, so nothing opens or runs in.
	 */
	private void seal(Writer w, int x, int z) {
		int ground = w.ground(x, z);
		int surface = Math.max(ground, w.surface(x, z));
		for (int y = floor + 1; y <= surface; y++) {
			BlockState state = w.get(x, y, z);
			boolean leaks = y <= ground ? state.isAir() || !state.getFluidState().isEmpty() : !state.getFluidState().isEmpty();
			if (leaks) {
				w.set(x, y, z, y >= ground - 2 ? DIRT : STONE);
			}
		}
		// The crowns of trees that stood on the square would float here: the cut takes them too.
		for (int y = Math.max(floor, surface) + 1; y <= box.maxY(); y++) {
			if (w.get(x, y, z).is(BlockTags.LEAVES)) {
				w.set(x, y, z, AIR);
			}
		}
	}

	/** One column of the square: cleared over the floor, the floor's four courses, built up under them to the ground. */
	private void square(Writer w, int x, int z) {
		Draw.clear(w, x, z, floor + 1, box.maxY());
		w.set(x, floor, z, GRASS);
		w.set(x, floor - 1, z, DIRT);
		w.set(x, floor - 2, z, DIRT);
		w.set(x, floor - 3, z, BEDROCK);
		int ground = w.ground(x, z);
		if (ground < floor - 4) {
			boolean edge = onEdge(x, z);
			for (int y = ground + 1; y <= floor - 4; y++) {
				w.set(x, y, z, edge ? FACE.at(start.seed(), x - minX, y - floor, z - minZ) : DIRT);
			}
			Draw.foundation(w, x, z, ground, DIRT, 8);
		}
	}

	/** The little building in the middle. */
	private void building(Writer w) {
		if (version >= 2) {
			firstHouse(w);
			return;
		}
		Frame h = house;
		for (int r = 0; r < HOUSE; r++) {
			for (int f = 0; f > -HOUSE; f--) {
				boolean wall = r == 0 || r == HOUSE - 1 || f == 0 || f == -(HOUSE - 1);
				w.set(h.pos(r, 0, f), wall ? COBBLE : MOSSY);
				for (int u = 1; u <= 4; u++) {
					w.set(h.pos(r, u, f), wall ? (u == 1 ? COBBLE : PLANKS) : AIR);
				}
				w.set(h.pos(r, 5, f), PLANKS);
			}
		}
		int mid = HOUSE / 2;
		w.set(h.pos(mid, 1, 0), AIR);
		w.set(h.pos(mid, 2, 0), AIR);
		w.set(h.pos(HOUSE - 2, 2, 0), PANE);
		w.set(h.pos(mid, 2, -(HOUSE - 1)), PANE);
		w.set(h.pos(0, 2, -mid), PANE);
		w.set(h.pos(HOUSE - 1, 2, -mid), PANE);
		BlockState torch = h.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
		w.set(h.pos(1, 3, -(HOUSE - 2)), torch);
		w.set(h.pos(HOUSE - 2, 3, -(HOUSE - 2)), torch);
	}

	/** The cells of the building's two torches (version 1: on the back wall; 2: on the side walls by the doorway). */
	public java.util.List<BlockPos> torches() {
		Frame h = house;
		if (version >= 2) {
			return java.util.List.of(h.pos(1, 2, -1), h.pos(HOUSE - 2, 2, -1));
		}
		return java.util.List.of(h.pos(1, 3, -(HOUSE - 2)), h.pos(HOUSE - 2, 3, -(HOUSE - 2)));
	}

	/**
	 * Version 2's building, the first house (R11-5): a cobblestone ring and course, two oak plank courses over it and the
	 * flat roof on them (inside 3 high: the 1 x 2 doorway reaches near the eaves), a mossy floor, a window of two glass
	 * blocks at eye height in each wall, and the two torches on the side walls a block in from the doorway, so the light
	 * spills out of it.
	 */
	private void firstHouse(Writer w) {
		Frame h = house;
		int roof = roof();
		for (int r = 0; r < HOUSE; r++) {
			for (int f = 0; f > -HOUSE; f--) {
				boolean wall = r == 0 || r == HOUSE - 1 || f == 0 || f == -(HOUSE - 1);
				w.set(h.pos(r, 0, f), wall ? COBBLE : MOSSY);
				for (int u = 1; u < roof; u++) {
					w.set(h.pos(r, u, f), wall ? (u == 1 ? COBBLE : PLANKS) : AIR);
				}
				w.set(h.pos(r, roof, f), PLANKS);
			}
		}
		int mid = HOUSE / 2;
		w.set(h.pos(mid, 1, 0), AIR);
		w.set(h.pos(mid, 2, 0), AIR);
		for (BlockPos glass : java.util.List.of(h.pos(HOUSE - 3, 2, 0), h.pos(HOUSE - 2, 2, 0), h.pos(mid, 2, -(HOUSE - 1)), h.pos(mid - 1, 2, -(HOUSE - 1)),
				h.pos(0, 2, -3), h.pos(0, 2, -4), h.pos(HOUSE - 1, 2, -3), h.pos(HOUSE - 1, 2, -4))) {
			w.set(glass, GLASS);
		}
		w.set(h.pos(1, 2, -1), h.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST)));
		w.set(h.pos(HOUSE - 2, 2, -1), h.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST)));
	}
}
