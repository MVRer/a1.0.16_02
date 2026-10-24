package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Seal;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Writer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * The archive's {@link Shape} (docs/SELF_CREATED_2.md, feature 17): its {@link ArchivePlan} built block by block. Every
 * cell's block is a pure function of its local coordinates ({@link #state}) and, for the tunnels' lining, of its column's
 * ground, so any chunk in any order builds its part of the same thing. The seal first (fluid, cave air and falling blocks
 * within 2 of every inside become natural stone, dirt near the surface), then the blocks, then the containers.
 */
final class ArchiveShape implements Shape {
	static final String EXTRA_TUNNELS = "t";

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
	private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
	private static final BlockState INFESTED = Blocks.INFESTED_COBBLESTONE.defaultBlockState();
	private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
	private static final BlockState SHELF = Blocks.BOOKSHELF.defaultBlockState();
	private static final BlockState GLOW = Blocks.GLOWSTONE.defaultBlockState();
	private static final BlockState FENCE = Blocks.OAK_FENCE.defaultBlockState();
	private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();

	private static final BlockState RUNNER = Blocks.SPRUCE_PLANKS.defaultBlockState();
	/** A chiseled bookshelf with every slot empty. */
	private static final BlockState EMPTIED = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
	private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();

	/**
	 * The shape's current version (R12-2). 1: as first built. 2 (the review): the theft legible as a rule (R11-6: on every
	 * stack, long wall and the entry wall, the shelves within reach mostly taken, chiseled and empty, 70 % at the door to
	 * 95 % at the dais up to {@code u} 4, 40 % to 80 % to the deck, full above it, and one in ten of the plain ones within
	 * reach gone to planks); the cells' ceilings oak planks (R11-19); the ways' mouths a notch open to the sky 3 deep with the
	 * land's own sides, no lining within {@value #BARE} of a mouth, a glowstone in the open way's lintel and dead ground in
	 * front of it (R11-12, R10-6).
	 */
	public static final int VERSION = 2;
	/** Version 2: no lining in the last cells this many inside a mouth (none in view from outside). */
	static final int BARE = 6;
	/** Version 2: the notch outside a mouth is this many cells deep. */
	static final int NOTCH = 3;

	private final ArchivePlan plan;
	private final long seed;
	/** The version it is built as. */
	private final int version;
	/** A beam across the hall (an oak log along {@code r}). */
	private final BlockState beam;

	ArchiveShape(ArchivePlan plan) {
		this.plan = plan;
		this.seed = plan.seed;
		this.version = plan.start.versionOr(VERSION);
		this.beam = plan.frame.turn(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
	}

	/** The kind's factory: the tunnels measured over the terrain. */
	static ArchiveShape over(Start start, Terrain terrain) {
		return new ArchiveShape(ArchivePlan.of(start, terrain));
	}

	ArchivePlan plan() {
		return plan;
	}

	/** The version it is built as. */
	int version() {
		return version;
	}

	/** The tunnels' lengths kept with a recorded place. */
	static int[] lengths(CompoundTag extra) {
		return extra.getIntArray(EXTRA_TUNNELS).orElse(new int[0]);
	}

	@Override
	public BoundingBox box() {
		return plan.box();
	}

	@Override
	public BlockPos anchor() {
		return plan.lectern();
	}

	@Override
	public BlockPos entrance() {
		return plan.entrance();
	}

	@Override
	public Direction entranceFacing() {
		return plan.frame.facing();
	}

	@Override
	public Interior interior() {
		return plan.interior();
	}

	@Override
	public CompoundTag extra() {
		CompoundTag tag = new CompoundTag();
		tag.put(EXTRA_TUNNELS, new IntArrayTag(plan.lengths()));
		return tag;
	}

	// --- fill ---

	@Override
	public void fill(Writer w, BoundingBox clip) {
		int y0 = plan.floorY();
		// 1. The seal, before anything is opened: within 2 of every inside, leaks become natural ground (never above it).
		Seal.around(w, clip, this::insideColumn, Seal.REACH, (x, y, z) -> natural(w, x, y, z));
		// 2. Every block, column by column, only where the shape has something to say.
		BoundingBox box = plan.box();
		List<BlockPos> shelves = new ArrayList<>();
		Draw.columns(w, box, (x, z) -> {
			int r = plan.localR(x, z);
			int f = plan.localF(x, z);
			long range = columnRange(r, f);
			if (range == Seal.NONE) {
				return;
			}
			int ground = Integer.MIN_VALUE;
			if (plan.tunnelAt(r, f) >= 0) {
				ground = w.ground(x, z) - y0;
			}
			int lo = Math.max(Seal.lo(range) + y0, w.minY());
			int hi = Math.min(Seal.hi(range) + y0, w.maxY());
			for (int y = lo; y <= hi; y++) {
				BlockState state = state(r, y - y0, f, ground);
				if (state != null) {
					w.set(x, y, z, state);
					if (state.is(Blocks.CHISELED_BOOKSHELF)) {
						shelves.add(new BlockPos(x, y, z));
					}
				}
			}
		});
		// 2b. Version 2: the mouths' notches open to the sky, the light in the open way's lintel, dead ground before it.
		if (version >= 2) {
			mouths(w);
		}
		// 3. The containers: the shelves' books, the records, the chests stay empty.
		for (BlockPos pos : shelves) {
			List<ItemStack> books = books(pos);
			if (!books.isEmpty()) {
				w.contents(pos, books);
			}
		}
	}

	/** The inside of a column, for the seal: the hall, the cells, the carved tunnels and his tunnel. */
	private long insideColumn(int x, int z) {
		int r = plan.localR(x, z);
		int f = plan.localF(x, z);
		int y0 = plan.floorY();
		if (plan.inHall(r, f)) {
			return Seal.range(y0 + 1, y0 + ArchivePlan.HEIGHT);
		}
		if (Math.abs(r) <= 4 * plan.cols - 1 && f > ArchivePlan.CELLS_BACK && f < plan.tunnelF0) {
			return Seal.range(y0 + 1, y0 + ArchivePlan.CELL_HEIGHT);
		}
		int t = plan.tunnelAt(r, f);
		if (t >= 0) {
			ArchivePlan.Tunnel tunnel = plan.tunnels[t];
			int i = f - plan.tunnelF0;
			if (r >= tunnel.left() && r <= tunnel.left() + 1 && ArchivePlan.carved(tunnel, i)) {
				return Seal.range(y0 + ArchivePlan.foot(i), y0 + ArchivePlan.top(i));
			}
			return Seal.NONE;
		}
		int j = plan.bayCell(r, f);
		if (j >= 0) {
			return Seal.range(y0 + ArchivePlan.bayFoot(j), y0 + ArchivePlan.bayTop(j));
		}
		return Seal.NONE;
	}

	/** What a sealed cell becomes: nothing above the ground; dirt (sandstone under sand) near it; stone, deepslate deep. */
	private static BlockState natural(Writer w, int x, int y, int z) {
		int g = w.ground(x, z);
		if (y > g) {
			return w.get(x, y, z);
		}
		if (y > g - 4) {
			BlockState top = w.terrain().block(x, g, z);
			return top.is(Blocks.SAND) || top.is(Blocks.RED_SAND) || top.is(Blocks.SANDSTONE) ? SANDSTONE : DIRT;
		}
		return y < 0 ? DEEPSLATE : STONE;
	}

	/** The heights ({@code u}) the shape may write in this column, or {@link Seal#NONE}. */
	long columnRange(int r, int f) {
		long range = Seal.NONE;
		if (Math.abs(r) <= ArchivePlan.SHELL && f <= ArchivePlan.CELLS_BACK - 1 && f >= -plan.length - 3) {
			range = merge(range, -2, ArchivePlan.CEILING + 1);
		}
		if (Math.abs(r) <= 4 * plan.cols + 1 && f >= 2 && f <= plan.tunnelF0 + 1) {
			range = merge(range, -1, ArchivePlan.CELL_HEIGHT + 2);
		}
		int t = plan.tunnelAt(r, f);
		if (t >= 0) {
			int i = f - plan.tunnelF0;
			range = merge(range, ArchivePlan.foot(i) - 1, ArchivePlan.top(i) + 1);
		}
		int j = plan.bayCell(r, f);
		if (j >= 0) {
			range = merge(range, ArchivePlan.bayFoot(j), ArchivePlan.bayTop(j));
		}
		return range;
	}

	private static long merge(long range, int lo, int hi) {
		if (range == Seal.NONE) {
			return Seal.range(lo, hi);
		}
		return Seal.range(Math.min(Seal.lo(range), lo), Math.max(Seal.hi(range), hi));
	}

	// --- every block ---

	/**
	 * The block at local ({@code r}, {@code u}, {@code f}), or null to leave what is there. {@code ground} is the column's
	 * ground in {@code u} where a tunnel runs (its lining stops there), else unused.
	 */
	@Nullable BlockState state(int r, int u, int f, int ground) {
		int t = plan.tunnelAt(r, f);
		if (t >= 0) {
			BlockState s = tunnel(plan.tunnels[t], r, u, f, ground);
			if (s != null) {
				return s;
			}
		}
		int j = plan.bayCell(r, f);
		if (j >= 0 && u >= ArchivePlan.bayFoot(j) && u <= ArchivePlan.bayTop(j)) {
			return AIR;
		}
		if (Math.abs(r) <= ArchivePlan.SHELL && f <= ArchivePlan.CELLS_BACK - 1 && f >= -plan.length - 3 && u >= -2 && u <= ArchivePlan.CEILING + 1) {
			return hall(r, u, f);
		}
		if (Math.abs(r) <= 4 * plan.cols + 1 && f >= 2 && f <= plan.tunnelF0 + 1 && u >= -1 && u <= ArchivePlan.CELL_HEIGHT + 2) {
			return cells(r, u, f);
		}
		return null;
	}

	private BlockState hall(int r, int u, int f) {
		int ar = Math.abs(r);
		boolean inside = plan.inHall(r, f);
		if (r == 0 && f >= 0 && (u == 1 || u == 2)) {
			return AIR; // the door
		}
		if (inside && u >= 1 && u <= ArchivePlan.HEIGHT) {
			return interior(r, u, f);
		}
		if (inside && u == 0) {
			if (plan.inBay(r, f)) {
				return AIR;
			}
			return Math.abs(r) <= 1 && !plan.inDais(r, f) ? RUNNER : PLANKS;
		}
		if (ar == ArchivePlan.FACE && u >= 0 && u <= ArchivePlan.HEIGHT && plan.inBay(r - Integer.signum(r), f)) {
			return AIR; // the bay: the shelves taken down to the shell
		}
		boolean face = ar <= ArchivePlan.FACE && f <= 0 && f >= -plan.length - 1 && u >= 1 && u <= ArchivePlan.HEIGHT;
		if (face) {
			return face(r, u, f);
		}
		if (inside && u == ArchivePlan.CEILING) {
			return plan.isGrid(r, f) ? GLOW : COBBLE;
		}
		return shell(r, u, f);
	}

	private BlockState shell(int r, int u, int f) {
		return Draw.chance(seed, r, u + 1000, f, 0.08) ? MOSSY : COBBLE;
	}

	/** True on a row's line or a pilaster's: where the frame (ribs, beams, pilasters) stands. */
	private boolean frameLine(int f) {
		int k = plan.rowOf(f);
		return k >= 0 && f == plan.rowFront(k) || plan.isPilaster(f);
	}

	private BlockState face(int r, int u, int f) {
		int ar = Math.abs(r);
		if (plan.isHallLight(r, u, f)) {
			return GLOW;
		}
		int end = -plan.length - 1;
		if (f == end) {
			if (ar <= ArchivePlan.DAIS_HALF && u >= ArchivePlan.DAIS_HEIGHT + 1 && u <= ArchivePlan.DAIS_HEIGHT + 4) {
				return recordShelf(r, u);
			}
			// The end wall: every shelf emptied.
			return ar <= ArchivePlan.HALF ? plan.frame.turn(EMPTIED.setValue(ChiseledBookShelfBlock.FACING, Direction.NORTH)) : SHELF;
		}
		if (f == 0) {
			// The entry wall: two pilasters in line with the piers, a light in each.
			return ar == ArchivePlan.STACK_IN ? LOG : version >= 2 ? taken(Direction.NORTH, r, u, f) : SHELF;
		}
		if (ar == ArchivePlan.FACE) {
			if (plan.isPilaster(f) || u > ArchivePlan.DECK && frameLine(f)) {
				return LOG;
			}
			if (version >= 2) {
				return taken(r < 0 ? Direction.EAST : Direction.WEST, r, u, f);
			}
			// More of the long walls' shelves emptied the nearer the end.
			double far = (double) -f / plan.length;
			if (Draw.chance(seed, r, u + 6000, f, 0.55 * far * far)) {
				return plan.frame.turn(EMPTIED.setValue(ChiseledBookShelfBlock.FACING, r < 0 ? Direction.EAST : Direction.WEST));
			}
		}
		return SHELF;
	}

	private BlockState interior(int r, int u, int f) {
		int ar = Math.abs(r);
		Direction side = r < 0 ? Direction.EAST : Direction.WEST; // from a stack toward the nave (design)
		if (plan.inDais(r, f)) {
			return dais(r, u, f);
		}
		if (plan.ladderColumn(r, f) && u >= 1 && u <= ArchivePlan.DECK) {
			return ladder(r, f);
		}
		if (u == ArchivePlan.HEIGHT && frameLine(f)) {
			return beam;
		}
		if (plan.isPost(r, f) && u >= 1 && u <= ArchivePlan.POST) {
			return u == ArchivePlan.POST ? GLOW : LOG;
		}
		if (plan.isPier(r, f) && u >= 1 && u <= ArchivePlan.STACK_TOP) {
			return plan.isHallLight(r, u, f) ? GLOW : LOG;
		}
		if (plan.isStack(r, f) && u >= 1 && u <= ArchivePlan.STACK_TOP) {
			int k = plan.rowOf(f);
			boolean front = f == plan.rowFront(k);
			boolean bayFace = (front && plan.aisleOf(f + 1) == plan.bayAisle || !front && plan.aisleOf(f - 1) == plan.bayAisle)
					&& r * plan.baySide > ArchivePlan.STACK_IN;
			// Taken down from the floor up, each column to its own height (the top ones left hanging under the gallery).
			if (bayFace && u <= Draw.between(seed, (long) r * 977 + f, 5, ArchivePlan.STACK_TOP)) {
				return AIR;
			}
			if (version >= 2) {
				return taken(front ? Direction.NORTH : Direction.SOUTH, r, u, f);
			}
			if (ar < ArchivePlan.HALF + 1 && Draw.chance(seed, r, u + 3000, f, 1.0 / 6.0)) {
				return chiseled(front ? Direction.NORTH : Direction.SOUTH, r, u, f);
			}
			return SHELF;
		}
		if (u == 1 && plan.rowLectern(r, f)) {
			return plan.frame.turn(Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, side));
		}
		if (u == ArchivePlan.DECK && plan.isDeck(r, f)) {
			return PLANKS;
		}
		if (u == ArchivePlan.DECK + 1 && plan.isRail(r, f)) {
			return FENCE;
		}
		return AIR;
	}

	private BlockState ladder(int r, int f) {
		Direction facing;
		if (Math.abs(r) == ArchivePlan.STACK_IN - 1) {
			facing = r < 0 ? Direction.EAST : Direction.WEST;
		} else {
			facing = f == plan.deckFront() ? Direction.NORTH : Direction.SOUTH;
		}
		return plan.frame.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
	}

	private BlockState dais(int r, int u, int f) {
		int back = -plan.length;
		int depth = f - back; // 0 at the back wall, 7 at the front step
		if (depth < ArchivePlan.PLATFORM) {
			if (u <= ArchivePlan.DAIS_HEIGHT) {
				return MOSSY;
			}
			if (u == ArchivePlan.DAIS_HEIGHT + 1 && r == 0 && depth == 2) {
				return plan.frame.turn(Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
			}
			return AIR;
		}
		int step = depth - ArchivePlan.PLATFORM; // 0, 1, 2
		int height = ArchivePlan.DAIS_HEIGHT - step; // its stair's u
		if (u < height) {
			return MOSSY;
		}
		if (u == height) {
			return plan.frame.turn(Blocks.MOSSY_COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		}
		return AIR;
	}

	private BlockState cells(int r, int u, int f) {
		int outer = 4 * plan.cols + 1;
		if (r == 0 && f <= ArchivePlan.CELLS_BACK && (u == 1 || u == 2)) {
			return AIR; // the hall's door through the back wall
		}
		if (Math.abs(r) == outer || f == 2 || f == plan.tunnelF0 + 1 || u == -1 || u == ArchivePlan.CELL_HEIGHT + 2) {
			return wall(r, u, f, false);
		}
		int rr = r + 4 * plan.cols;
		int ff = f - ArchivePlan.CELLS_BACK;
		boolean wallR = rr % ArchivePlan.CELL == 0;
		boolean wallF = ff % ArchivePlan.CELL == 0;
		int c = rr / ArchivePlan.CELL;
		int j = ff / ArchivePlan.CELL;
		int a = rr % ArchivePlan.CELL - 1;
		int b = ff % ArchivePlan.CELL - 1;
		if (u == ArchivePlan.CELL_HEIGHT + 1) {
			return !wallR && !wallF && a == 3 && b == 3 ? GLOW : version >= 2 && !wallR && !wallF ? PLANKS : COBBLE;
		}
		if (wallR || wallF) {
			if (u >= 1 && u <= 2 && door(wallR, wallF, c, j, a, b)) {
				return AIR;
			}
			return u == 0 ? COBBLE : wall(r, u, f, true);
		}
		if (u == 0) {
			return PLANKS;
		}
		if (u == 1) {
			BlockState d = detail(plan.detail(c, j), a, b);
			if (d != null) {
				return d;
			}
		}
		return AIR;
	}

	/** A doorway in a wall line between two cells (the middle of the wall, 1 x 2), if the maze opened it. */
	private boolean door(boolean wallR, boolean wallF, int c, int j, int a, int b) {
		if (wallR && !wallF) {
			// Between cells c-1 and c, row j; the door at the middle f (b == 3).
			return b == 3 && c >= 1 && c <= plan.cols - 1 && plan.edgeOpen(c - 1, j, true);
		}
		if (wallF && !wallR) {
			// Between rows j-1 and j, column c; the door at the middle r (a == 3).
			return a == 3 && j >= 1 && j <= plan.rows - 1 && plan.edgeOpen(c, j - 1, false);
		}
		return false;
	}

	private BlockState wall(int r, int u, int f, boolean inner) {
		if (u >= 1 && u <= ArchivePlan.CELL_HEIGHT && Draw.chance(seed, r, u + 4000, f, plan.infested)) {
			return INFESTED;
		}
		// Damp at the foot of the cells' walls.
		if (inner && u == 1 && Draw.chance(seed, r, u + 7000, f, 0.14)) {
			return MOSSY;
		}
		return COBBLE;
	}

	private @Nullable BlockState detail(ArchivePlan.Detail d, int a, int b) {
		return switch (d) {
			case LECTERN -> a == 5 && b == 1 ? plan.frame.turn(Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH)) : null;
			case CHAIR -> a == 1 && b == 1 ? plan.frame.turn(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)) : null;
			case SHELF -> a == 0 && b == 6 ? SHELF : null;
			case TABLE -> a == 6 && b == 6 ? Blocks.CRAFTING_TABLE.defaultBlockState() : null;
			case CHEST -> a == 0 && b == 0 ? plan.frame.turn(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST)) : null;
			case NONE -> null;
		};
	}

	private @Nullable BlockState tunnel(ArchivePlan.Tunnel t, int r, int u, int f, int ground) {
		int i = f - plan.tunnelF0;
		boolean carved = ArchivePlan.carved(t, i);
		boolean plugged = ArchivePlan.plugged(t, i);
		if (!carved && !plugged) {
			return null;
		}
		int foot = ArchivePlan.foot(i);
		int top = ArchivePlan.top(i);
		boolean core = r >= t.left() && r <= t.left() + 1;
		if (core && u >= foot && u <= top) {
			return carved ? AIR : null;
		}
		boolean lining = (core && (u == foot - 1 || u == top + 1)) || ((r == t.left() - 1 || r == t.left() + 2) && u >= foot - 1 && u <= top + 1);
		if (lining && u <= ground - 2 && (version < 2 || i < t.mouth() - BARE)) {
			return COBBLE;
		}
		return null;
	}

	/**
	 * Version 2's shelf within the hall (R11-6): taken from the floor up as high as a person could reach, more the deeper
	 * in; chiseled and empty where taken, a plain shelf where not, one in ten of those within reach gone (planks); above the
	 * deck full.
	 */
	private BlockState taken(Direction facing, int r, int u, int f) {
		if (u > ArchivePlan.STACK_TOP) {
			return SHELF;
		}
		double far = Math.clamp((double) -f / Math.max(1, plan.length), 0.0, 1.0);
		double share = u <= 4 ? 0.70 + 0.25 * far : 0.40 + 0.40 * far;
		if (Draw.chance(seed, r, u + 8000, f, share)) {
			return plan.frame.turn(EMPTIED.setValue(ChiseledBookShelfBlock.FACING, facing));
		}
		if (u <= 4 && Draw.chance(seed, r, u + 9000, f, 0.1)) {
			return PLANKS;
		}
		return SHELF;
	}

	/**
	 * Version 2's mouths (R11-12, R10-6): outside every way's mouth its first {@value #NOTCH} carved cells are opened to the
	 * sky, a notch whose sides are the land itself, so the hole is a black square at the foot of a vertical face; the open
	 * way's lintel (the block over its first covered cell) is glowstone, seen in the hillside at night; dead ground (coarse
	 * dirt, nothing growing) round its notch and in front of it.
	 */
	private void mouths(Writer w) {
		int y0 = plan.floorY();
		for (ArchivePlan.Tunnel t : plan.tunnels) {
			for (int i = t.mouth(); i < Math.min(t.mouth() + NOTCH, t.exit()); i++) {
				if (!ArchivePlan.carved(t, i)) {
					continue;
				}
				for (int side = 0; side <= 1; side++) {
					BlockPos floor = plan.at(t.left() + side, ArchivePlan.foot(i), plan.tunnelF0 + i);
					int ground = w.ground(floor.getX(), floor.getZ());
					for (int y = floor.getY(); y <= ground + 2; y++) {
						if (w.inside(floor.getX(), y, floor.getZ()) && !w.get(floor.getX(), y, floor.getZ()).isAir()) {
							w.set(floor.getX(), y, floor.getZ(), AIR);
						}
					}
				}
			}
			if (!t.open() || t.mouth() < 1) {
				continue;
			}
			int inner = t.mouth() - 1;
			BlockPos lintel = plan.at(t.left(), ArchivePlan.top(inner) + 1, plan.tunnelF0 + inner);
			if (w.inside(lintel) && !w.get(lintel).isAir() && w.get(lintel).getFluidState().isEmpty()) {
				w.set(lintel, GLOW);
			}
			for (int i = t.mouth() - 1; i <= t.exit() + 2; i++) {
				for (int a = t.left() - 3; a <= t.left() + 4; a++) {
					if (a >= t.left() && a <= t.left() + 1 && i < t.exit()) {
						continue;
					}
					BlockPos column = plan.at(a, 0, plan.tunnelF0 + i);
					int ground = w.ground(column.getX(), column.getZ());
					if (!w.inside(column.getX(), ground, column.getZ())) {
						continue;
					}
					BlockState top = w.get(column.getX(), ground, column.getZ());
					if (top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.DIRT)) {
						w.set(column.getX(), ground, column.getZ(), COARSE);
					}
					for (int y = ground + 1; y <= ground + 2; y++) {
						BlockState over = w.get(column.getX(), y, column.getZ());
						if (com.forzacode.a1016_02.world.gen.Vegetation.isPlant(over) || com.forzacode.a1016_02.world.gen.Vegetation.isSnowLayer(over)) {
							w.set(column.getX(), y, column.getZ(), AIR);
						}
					}
				}
			}
		}
	}

	// --- shelves and books ---

	private BlockState chiseled(Direction facing, int r, int u, int f) {
		BlockState state = plan.frame.turn(Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(ChiseledBookShelfBlock.FACING, facing));
		boolean[] full = slots(r, u, f);
		for (int s = 0; s < full.length; s++) {
			state = state.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(s), full[s]);
		}
		return state;
	}

	/** A stack's chiseled shelf: 0 to 2 books, most slots empty (the books were taken). */
	private boolean[] slots(int r, int u, int f) {
		boolean[] full = new boolean[6];
		double roll = Draw.unit(seed, r, u + 5000, f);
		int books = roll < 0.5 ? 0 : roll < 0.85 ? 1 : 2;
		for (int k = 0; k < books; k++) {
			full[Draw.between(seed, ((long) r * 31 + u) * 131 + f * 7L + k, 0, 5)] = true;
		}
		return full;
	}

	private BlockState recordShelf(int r, int u) {
		BlockState state = plan.frame.turn(Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(ChiseledBookShelfBlock.FACING, Direction.NORTH));
		boolean list = u == ArchivePlan.DAIS_HEIGHT + 3 && r != 0;
		return state.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(1), list);
	}

	/** What a chiseled shelf written here holds (matching the slot states its block was given). */
	private List<ItemStack> books(BlockPos pos) {
		int r = plan.localR(pos.getX(), pos.getZ());
		int f = plan.localF(pos.getX(), pos.getZ());
		int u = pos.getY() - plan.floorY();
		List<ItemStack> out = new ArrayList<>(6);
		for (int s = 0; s < 6; s++) {
			out.add(ItemStack.EMPTY);
		}
		if (f == -plan.length - 1) {
			if (u == ArchivePlan.DAIS_HEIGHT + 3 && r != 0 && Math.abs(r) <= ArchivePlan.DAIS_HALF) {
				int index = r < 0 ? r + 3 : r + 2; // -3..-1 -> 0..2, 1..3 -> 3..5
				out.set(1, record(ArchivePlan.LIST_NAMES.get(index)));
				return out;
			}
			return List.of();
		}
		if (version >= 2) {
			return List.of();
		}
		boolean[] full = slots(r, u, f);
		boolean any = false;
		for (int s = 0; s < 6; s++) {
			if (full[s]) {
				out.set(s, new ItemStack(Items.BOOK));
				any = true;
			}
		}
		return any ? out : List.of();
	}

	/** A record: a written book titled with a name, no author, one blank page, an original (D-100). */
	static ItemStack record(String name) {
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(Filterable.passThrough(name), "", 0, List.of(Filterable.passThrough(Component.empty())), true));
		return stack;
	}

	/** The title of a record (or any written book), or null. */
	static @Nullable String titleOf(ItemStack stack) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content == null ? null : content.title().raw();
	}
}
