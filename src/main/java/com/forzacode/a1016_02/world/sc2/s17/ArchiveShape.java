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
import com.forzacode.a1016_02.world.sc2.s17.books.Books;

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
	/** Version 3: the body's jacket of natural ground round every inside (the face and shell 3, then 1 of the world's). */
	static final int JACKET = 4;

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
	 * front of it (R11-12, R10-6). 3 (round 3, D-113, docs/SELF_CREATED_2_R3.md): the stacks: the hall 41 high, three tiers
	 * of stacks with decks at 9, 18 and 27, the light a grid of single glowstone floating at 17 where the old ceiling was;
	 * the well ({@link Well}) beside it from the world's bottom to a cut over the land, its face toward the origin missing;
	 * the six's records written.
	 */
	public static final int VERSION = 4;
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
	public BlockPos sign() {
		return plan.well != null ? plan.well.sign() : plan.entrance();
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
		if (version >= 3) {
			// Version 3 (Mariano's report on 1.5.0: never a wall of it in sight): the ways sealed as before; the body (hall,
			// cells, corridor, the well under its cut) jacketed {@value #JACKET} round every inside, air filled even over the
			// land (a crevasse, a cliff face, a cave beside it), so no shell block ever meets air outside the plan's openings.
			Seal.Material cover = (x, y, z) -> natural(w, x, Math.min(y, w.ground(x, z)), z);
			Seal.around(w, clip, memo(clip, Seal.REACH, this::waysColumn), Seal.REACH, (x, y, z) -> natural(w, x, y, z));
			Seal.around(w, clip, memo(clip, JACKET, this::bodyColumn), JACKET, cover);
		} else {
			Seal.around(w, clip, this::insideColumn, Seal.REACH, (x, y, z) -> natural(w, x, y, z));
		}
		// 2. Every block, column by column, only where the shape has something to say.
		BoundingBox box = plan.box();
		List<BlockPos> shelves = new ArrayList<>();
		// The well's surface block, one for the whole of it (its middle column's biome), read once a fill.
		BlockState[] wellSurface = new BlockState[1];
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
			Well well = plan.well;
			if (well != null && well.owns(well.iOf(r), f - well.f0)) {
				wellColumn(w, well, x, z, well.iOf(r), f - well.f0, lo, hi, wellSurface);
				return;
			}
			boolean way = version >= 3 && plan.tunnelAt(r, f) >= 0;
			for (int y = lo; y <= hi; y++) {
				BlockState state = state(r, y - y0, f, ground);
				if (way && state == COBBLE && facesTheOpen(w, x, y, z)) {
					// Version 3: a lining block facing natural air (a crevasse beside the way) is the world's stone, never a wall.
					state = STONE;
				}
				if (state != null) {
					w.set(x, y, z, state);
					if (state.is(Blocks.CHISELED_BOOKSHELF)) {
						shelves.add(new BlockPos(x, y, z));
					}
				}
			}
		});
		// 2c. Version 3: the apron before the well's way in, its kerb and lip; the lighting fault on the piece's faces.
		if (plan.well != null) {
			if (plan.well.lifted()) {
				talus(w, plan.well);
			} else {
				apron(w, plan.well);
			}
			faceLights(w, plan.well);
		}
		// 2b. Version 2: the mouths' notches open to the sky, the light in the open way's lintel, dead ground before it.
		if (version >= 2) {
			mouths(w);
		}
		// 3. The containers: the shelves' books, the records, the chests stay empty.
		for (BlockPos pos : shelves) {
			List<ItemStack> books = books(pos, w.server());
			if (!books.isEmpty()) {
				w.contents(pos, books);
			}
		}
	}

	/** The sides {@link #facesTheOpen} looks at: four round and over. */
	private static final int[][] OPEN_SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};

	/**
	 * Version 3: true if a way's lining cell has natural open air or water beside it or over it (a crevasse, a ravine, the
	 * land's open air): read from the chunk's own blocks after the seal (which filled the caves under the land), and for a
	 * neighbour in another chunk from the land's height there. The way's own cells (carved, lining) never count.
	 */
	private boolean facesTheOpen(Writer w, int x, int y, int z) {
		for (int[] d : OPEN_SIDES) {
			int nx = x + d[0];
			int ny = y + d[1];
			int nz = z + d[2];
			int r = plan.localR(nx, nz);
			int f = plan.localF(nx, nz);
			int u = ny - plan.floorY();
			int t = plan.tunnelAt(r, f);
			if (t >= 0) {
				ArchivePlan.Tunnel tunnel = plan.tunnels[t];
				int i = f - plan.tunnelF0;
				boolean core = r >= tunnel.left() && r <= tunnel.left() + 1;
				if (core && ArchivePlan.carved(tunnel, i) && u >= ArchivePlan.foot(i) && u <= ArchivePlan.top(i)) {
					continue; // the way itself
				}
			}
			if (w.inside(nx, ny, nz)) {
				BlockState there = w.get(nx, ny, nz);
				if (there.isAir() || !there.getFluidState().isEmpty()) {
					return true;
				}
			} else if (ny > w.ground(nx, nz)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A column function asked once per column of the clip and its rim (the seal asks each column's neighbours again: 81
	 * times a column at the jacket's reach).
	 */
	private static Seal.Columns memo(BoundingBox clip, int reach, Seal.Columns columns) {
		int x0 = clip.minX() - reach;
		int z0 = clip.minZ() - reach;
		int nx = clip.getXSpan() + 2 * reach;
		int nz = clip.getZSpan() + 2 * reach;
		long[] ranges = new long[nx * nz];
		boolean[] known = new boolean[nx * nz];
		return (x, z) -> {
			int ix = x - x0;
			int iz = z - z0;
			if (ix < 0 || iz < 0 || ix >= nx || iz >= nz) {
				return columns.range(x, z);
			}
			int k = ix * nz + iz;
			if (!known[k]) {
				ranges[k] = columns.range(x, z);
				known[k] = true;
			}
			return ranges[k];
		};
	}

	/** Version 3: the body's inside of a column (hall, well, corridor, cells), for the jacket. */
	private long bodyColumn(int x, int z) {
		int r = plan.localR(x, z);
		int f = plan.localF(x, z);
		return plan.tunnelAt(r, f) >= 0 || !plan.inHall(r, f) && plan.bayCell(r, f) >= 0 ? Seal.NONE : insideColumn(x, z);
	}

	/**
	 * Version 3: the ways' inside of a column (the three tunnels, his tunnel from the bay), sealed as version 2's; a lining
	 * block facing the open is stone ({@link #facesTheOpen}).
	 */
	private long waysColumn(int x, int z) {
		int r = plan.localR(x, z);
		int f = plan.localF(x, z);
		return plan.tunnelAt(r, f) >= 0 || !plan.inHall(r, f) && plan.bayCell(r, f) >= 0 ? insideColumn(x, z) : Seal.NONE;
	}

	/**
	 * What the fill means at a world cell (for tests): its block, air where it opens the world (its insides, its ways, the
	 * well's open face), or null where it leaves the world as it is. The seal's and the apron's changes are not in it.
	 */
	@Nullable BlockState intended(int x, int y, int z, com.forzacode.a1016_02.world.gen.Terrain terrain) {
		int ground = terrain.ground(x, z);
		int r = plan.localR(x, z);
		int f = plan.localF(x, z);
		Well well = plan.well;
		if (well != null) {
			int i = well.iOf(r);
			int j = f - well.f0;
			if (i >= 0 && i < Well.SIDE && j >= 0 && j < Well.SIDE) {
				return well.state(i, y, j, ground);
			}
			if (well.corridor(i, j)) {
				return well.corridorState(i, y, j);
			}
		}
		long range = columnRange(r, f);
		int u = y - plan.floorY();
		if (range == Seal.NONE || u < Seal.lo(range) || u > Seal.hi(range)) {
			return null;
		}
		return state(r, u, f, ground - plan.floorY());
	}

	/** The inside of a column, for the seal: the hall, the cells, the carved tunnels and his tunnel; v3 the well. */
	private long insideColumn(int x, int z) {
		int r = plan.localR(x, z);
		int f = plan.localF(x, z);
		int y0 = plan.floorY();
		if (plan.inHall(r, f)) {
			return Seal.range(y0 + 1, y0 + plan.height());
		}
		Well well = plan.well;
		if (well != null) {
			int i = well.iOf(r);
			int j = f - well.f0;
			if (well.owns(i, j)) {
				int[] in = well.insideRange(i, j);
				return in == null ? Seal.NONE : Seal.range(in[0], in[1]);
			}
			if (plan.wellDoor(r, 1, f)) {
				return Seal.range(y0 + 1, y0 + Well.DOOR_HIGH);
			}
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
			return sandy(w, x, g, z) ? SANDSTONE : DIRT;
		}
		return y < 0 ? DEEPSLATE : STONE;
	}

	/**
	 * True if the land's top block here is sand or sandstone. Over the noise (worldgen) that block is the generator's own
	 * default block (the noise has no surface yet; a vein's block is never sand), so it is read once from the settings,
	 * not from a column of the noise (FIX-R3-17: that read was most of a sealed chunk's cost); the gallery's terrain reads it.
	 */
	private static boolean sandy(Writer w, int x, int g, int z) {
		com.forzacode.a1016_02.world.sc2.kit.Terrains.NoiseLand land = com.forzacode.a1016_02.world.sc2.kit.Terrains.noiseLand(w.terrain());
		BlockState top = land != null ? land.generator().generatorSettings().value().defaultBlock() : w.terrain().block(x, g, z);
		return top.is(Blocks.SAND) || top.is(Blocks.RED_SAND) || top.is(Blocks.SANDSTONE);
	}

	/** The heights ({@code u}) the shape may write in this column, or {@link Seal#NONE}. */
	long columnRange(int r, int f) {
		long range = Seal.NONE;
		Well well = plan.well;
		if (well != null) {
			int i = well.iOf(r);
			int j = f - well.f0;
			if (well.owns(i, j)) {
				int y0 = plan.floorY();
				return i < 0 ? Seal.range(-1, Well.DOOR_HIGH + 1) : Seal.range(well.bottom() - y0, (well.lifted() ? well.crest + Well.CLEAR : well.top - 1) - y0);
			}
		}
		if (Math.abs(r) <= ArchivePlan.SHELL && f <= ArchivePlan.CELLS_BACK - 1 && f >= -plan.length - 3) {
			range = merge(range, -2, plan.ceiling() + 1);
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
		if (Math.abs(r) <= ArchivePlan.SHELL && f <= ArchivePlan.CELLS_BACK - 1 && f >= -plan.length - 3 && u >= -2 && u <= plan.ceiling() + 1) {
			return version >= 3 ? hall3(r, u, f) : hall(r, u, f);
		}
		if (Math.abs(r) <= 4 * plan.cols + 1 && f >= 2 && f <= plan.tunnelF0 + 1 && u >= -1 && u <= ArchivePlan.CELL_HEIGHT + 2) {
			return cells(r, u, f);
		}
		return null;
	}

	// --- version 3: the stacks ---

	private BlockState hall3(int r, int u, int f) {
		int ar = Math.abs(r);
		boolean inside = plan.inHall(r, f);
		if (r == 0 && f >= 0 && (u == 1 || u == 2)) {
			return AIR; // the door
		}
		if (plan.wellDoor(r, u, f)) {
			return AIR;
		}
		if (inside && u >= 1 && u <= ArchivePlan.HEIGHT3) {
			return interior3(r, u, f);
		}
		if (inside && u == 0) {
			if (plan.inBay(r, f)) {
				return AIR;
			}
			return Math.abs(r) <= 1 && !plan.inDais(r, f) ? RUNNER : PLANKS;
		}
		if (ar == ArchivePlan.FACE && u >= 0 && u <= ArchivePlan.HEIGHT3 && plan.inBay(r - Integer.signum(r), f)) {
			return AIR; // the bay: the shelves taken down to the shell
		}
		if (ar <= ArchivePlan.FACE && f <= 0 && f >= -plan.length - 1 && u >= 1 && u <= ArchivePlan.HEIGHT3) {
			return face3(r, u, f);
		}
		return shell(r, u, f);
	}

	private BlockState face3(int r, int u, int f) {
		int ar = Math.abs(r);
		if (plan.isHallLight(r, u, f)) {
			return GLOW;
		}
		if (f == -plan.length - 1) {
			if (ar <= ArchivePlan.DAIS_HALF && u >= ArchivePlan.DAIS_HEIGHT + 1 && u <= ArchivePlan.DAIS_HEIGHT + 4) {
				return recordShelf(r, u);
			}
			if (ar <= ArchivePlan.HALF && u <= ArchivePlan.REACH3) {
				return plan.frame.turn(EMPTIED.setValue(ChiseledBookShelfBlock.FACING, Direction.NORTH));
			}
			return SHELF;
		}
		if (f == 0) {
			return ar == ArchivePlan.STACK_IN ? LOG : taken3(Direction.SOUTH, r, u, f);
		}
		if (ar == ArchivePlan.FACE) {
			if (plan.isPilaster(f) || u > ArchivePlan.TOP_DECK3 && frameLine(f)) {
				return LOG;
			}
			return taken3(r < 0 ? Direction.EAST : Direction.WEST, r, u, f);
		}
		return SHELF;
	}

	private BlockState interior3(int r, int u, int f) {
		if (u == ArchivePlan.FLOAT3 && plan.isFloat3(r, f)) {
			return GLOW;
		}
		if (plan.inDais(r, f)) {
			return dais(r, u, f);
		}
		if (plan.ladder3(r, u, f)) {
			return plan.frame.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, r < 0 ? Direction.EAST : Direction.WEST));
		}
		if (u == ArchivePlan.HEIGHT3 && frameLine(f)) {
			return beam;
		}
		if (plan.isPost(r, f) && u >= 1 && u <= ArchivePlan.POST) {
			return u == ArchivePlan.POST ? GLOW : LOG;
		}
		if (plan.isPier(r, f) && u >= 1 && u <= ArchivePlan.STACK3) {
			return plan.isHallLight(r, u, f) ? GLOW : LOG;
		}
		if (u == ArchivePlan.FLOAT3 && plan.isFloat3(r, f)) {
			return GLOW;
		}
		if (plan.isDeck3(r, u, f)) {
			return PLANKS;
		}
		if (plan.isStack(r, f) && u >= 1 && u <= ArchivePlan.STACK3) {
			int k = plan.rowOf(f);
			boolean front = f == plan.rowFront(k);
			boolean bayFace = (front && plan.aisleOf(f + 1) == plan.bayAisle || !front && plan.aisleOf(f - 1) == plan.bayAisle)
					&& r * plan.baySide > ArchivePlan.STACK_IN;
			if (bayFace && u <= Draw.between(seed, (long) r * 977 + f, 5, ArchivePlan.REACH3)) {
				return AIR;
			}
			if (plan.isEditionShelf3(r, u, f)) {
				return plan.frame.turn(EMPTIED.setValue(ChiseledBookShelfBlock.FACING, front ? Direction.NORTH : Direction.SOUTH));
			}
			return taken3(front ? Direction.NORTH : Direction.SOUTH, r, u, f);
		}
		if (u == 1 && plan.rowLectern(r, f)) {
			Direction side = r < 0 ? Direction.EAST : Direction.WEST;
			return plan.frame.turn(Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, side));
		}
		if (plan.isRail3(r, u, f)) {
			return FENCE;
		}
		return AIR;
	}

	/**
	 * Version 3's theft: as version 2's from the floor up to a hand over the first deck, full above. Version 4 (R3A-11): every
	 * shelf a hand could reach is emptied, as the dais wall is (a board here and there low down), the colour only above the
	 * first deck: full where nobody could reach, empty where a hand could.
	 */
	private BlockState taken3(Direction facing, int r, int u, int f) {
		if (u > ArchivePlan.REACH3) {
			return SHELF;
		}
		double far = Math.clamp((double) -f / Math.max(1, plan.length), 0.0, 1.0);
		double share = version >= 4 ? 1.0 : u <= 4 ? 0.70 + 0.25 * far : 0.40 + 0.40 * far;
		if (version >= 4 && u <= 4 && Draw.chance(seed, r, u + 9000, f, 0.1)) {
			return PLANKS;
		}
		if (Draw.chance(seed, r, u + 8000, f, share)) {
			return plan.frame.turn(EMPTIED.setValue(ChiseledBookShelfBlock.FACING, facing));
		}
		if (u <= 4 && Draw.chance(seed, r, u + 9000, f, 0.1)) {
			return PLANKS;
		}
		return SHELF;
	}

	/** A column of the well (its footprint or the corridor to it): every cell from {@code lo} to {@code hi} it says. */
	private void wellColumn(Writer w, Well well, int x, int z, int i, int j, int lo, int hi, BlockState[] surfaceOnce) {
		if (i < 0) {
			if (!well.corridor(i, j)) {
				return;
			}
			for (int y = lo; y <= hi; y++) {
				BlockState state = well.corridorState(i, y, j);
				if (state != null) {
					w.set(x, y, z, state);
				}
			}
			return;
		}
		// The land only where it matters (the outer layer stands on it); the piece's surface is its middle column's, one block
		// for the whole of it (a column's own read costs a noise sample).
		int ground = well.lifted() || Well.layer(i, j) == 0 ? w.ground(x, z) : Integer.MAX_VALUE;
		BlockState surface = surfaceOnce[0];
		if (surface == null) {
			BlockPos middle = well.at(Well.SIDE / 2, 0, Well.SIDE / 2);
			surface = Well.surfaceOf(w.terrain().surfaceBiome(middle.getX(), middle.getZ()));
			surfaceOnce[0] = surface;
		}
		for (int y = Math.max(lo, well.bottom()); y <= Math.min(hi, well.lifted() ? well.crest : well.top - 1); y++) {
			BlockState state = well.state(i, y, j, ground, surface);
			if (state != null) {
				w.set(x, y, z, state);
			}
		}
		if (well.lifted()) {
			// Version 4: a tree that stood on the land is gone from over the piece's top; the top's own grass and flowers.
			int s = well.surfaceY(i, j, ground);
			for (int y = s + 1; y <= Math.min(hi, well.crest + Well.CLEAR); y++) {
				BlockState here = w.get(x, y, z);
				if (here.is(net.minecraft.tags.BlockTags.LOGS) || here.is(net.minecraft.tags.BlockTags.LEAVES) || here.is(Blocks.VINE)
						|| !here.isAir() && here.canBeReplaced()) {
					w.set(x, y, z, AIR);
				}
			}
			BlockState plant = well.plant(i, j, surface);
			if (plant != null && s + 1 <= hi && w.get(x, s + 1, z).isAir()) {
				w.set(x, s + 1, z, plant);
			}
		}
	}

	/**
	 * The apron (D-110, 17): the land before the ground gallery's open face cut or filled flat at its floor for
	 * {@value Well#APRON}, dead ground on it (coarse dirt, nothing growing); a kerb on its edge where the land falls away more
	 * than 3, a lip on the land's edge where it rises more than 3 (D-097.7), along the open face's sides too.
	 */
	private void apron(Writer w, Well well) {
		int floor = well.floorY(well.kGround);
		for (int i = Well.APRON_I0 - 1; i <= Well.APRON_I1 + 1; i++) {
			for (int j = Well.SIDE - 4; j <= Well.SIDE + Well.APRON; j++) {
				BlockPos column = well.at(i, floor, j);
				int x = column.getX();
				int z = column.getZ();
				if (x < w.minX() || x > w.maxX() || z < w.minZ() || z > w.maxZ()) {
					continue;
				}
				boolean onApron = Well.apron(i, j);
				boolean sill = Well.wayFloor(i, j);
				boolean edge = !onApron && !sill && !(i >= 0 && i < Well.SIDE && j < Well.SIDE);
				int ground = w.ground(x, z);
				if (onApron) {
					for (int y = floor + 1; y <= floor + Well.APRON_CUT; y++) {
						if (!w.get(x, y, z).isAir()) {
							w.set(x, y, z, AIR);
						}
					}
					for (int y = Math.max(ground + 1, floor - Well.APRON_CUT); y < floor; y++) {
						w.set(x, y, z, DIRT);
					}
					w.set(x, floor, z, COARSE);
				}
				if ((onApron || sill) && fallsAway(well, w, i, j, floor)) {
					// The kerb: an apron or sill edge cell over land that falls away.
					w.set(x, floor + 1, z, STONE);
				} else if (edge && ground > floor + 3 && ground <= floor + Well.APRON_CUT) {
					// The lip: the land's edge over the cut.
					if (nearestOpen(well, i, j, floor) != null && w.get(x, ground + 1, z).canBeReplaced()) {
						w.set(x, ground + 1, z, STONE);
					}
				}
			}
		}
	}

	/** The lighting fault on the well's piece of land: lights one out from its four faces, from the land up ({@link Well#faceLight}). */
	private static void faceLights(Writer w, Well well) {
		for (int i = -1; i <= Well.SIDE; i++) {
			for (int j = -1; j <= Well.SIDE; j++) {
				if (i >= 0 && i < Well.SIDE && j >= 0 && j < Well.SIDE) {
					j = Well.SIDE - 1;
					continue;
				}
				BlockPos column = well.at(i, 0, j);
				int x = column.getX();
				int z = column.getZ();
				if (x < w.minX() || x > w.maxX() || z < w.minZ() || z > w.maxZ()) {
					continue;
				}
				int ground;
				int below = well.top;
				if (well.lifted()) {
					// Version 4: from the land (the talus before the crack) up to the surface of the piece's column beside it.
					ground = well.frontHeight(i, j, w.ground(x, z));
					int bi = Math.clamp(i, 0, Well.SIDE - 1);
					int bj = Math.clamp(j, 0, Well.SIDE - 1);
					BlockPos beside = well.at(bi, 0, bj);
					below = well.surfaceY(bi, bj, w.ground(beside.getX(), beside.getZ())) + 1;
				} else {
					ground = Math.max(w.ground(x, z), j == Well.SIDE ? well.floorY(well.kGround) : Integer.MIN_VALUE);
				}
				for (int y = ground + 1; y < below; y++) {
					if (!well.faceLight(i, y, j, ground, below) || !w.inside(x, y, z)) {
						continue;
					}
					BlockState here = w.get(x, y, z);
					if (here.isAir()) {
						w.set(x, y, z, Well.FAULT);
					} else if (here.is(Blocks.WATER) && here.getFluidState().isSource()) {
						w.set(x, y, z, com.forzacode.a1016_02.world.sc2.kit.Fault.lightOf(com.forzacode.a1016_02.world.sc2.kit.Fault.LEVEL, true));
					}
				}
			}
		}
	}

	/**
	 * Version 4 (R3A-1): the talus before the crack instead of the apron. In front of the crack and one a side the land is
	 * brought to the sill's height, and from there it slopes one a block out and to the sides ({@link Well#frontHeight}):
	 * scree (gravel, stone, andesite) where the land is lower, a notch cut where it is higher, the land as it is where it
	 * already lies on that slope; nothing raised on land at the sill's height, no coarse dirt, nothing framing the crack. A
	 * notch never opens water: its cut stops at a fluid or a cell beside one. The fair edge (D-097.7): wherever a column of
	 * the talus and one beside it (the talus's or the land's) stand more than 3 apart, a stone kerb on the higher one.
	 */
	private void talus(Writer w, Well well) {
		int top = well.floorY(well.kGround) + Well.TALUS + Well.CLEAR;
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int i = Well.CORE_I0 - Well.TALUS - 1; i <= Well.CORE_I1 + Well.TALUS + 1; i++) {
			for (int j = Well.SIDE; j <= Well.SIDE + Well.TALUS + 1; j++) {
				BlockPos column = well.at(i, 0, j);
				int x = column.getX();
				int z = column.getZ();
				if (x < w.minX() || x > w.maxX() || z < w.minZ() || z > w.maxZ()) {
					continue;
				}
				int reach = Well.talusReach(i, j);
				int ground = w.ground(x, z);
				int h = well.frontHeight(i, j, ground);
				if (h > ground) {
					for (int y = ground + 1; y <= h; y++) {
						w.set(x, y, z, well.scree(i, y, j));
					}
				} else if (h < ground) {
					for (int y = h + 1; y <= top; y++) {
						BlockState here = w.get(x, y, z);
						if (here.isAir()) {
							continue;
						}
						if (!here.getFluidState().isEmpty() || wetBeside(w, x, y, z)) {
							break;
						}
						w.set(x, y, z, AIR);
					}
				}
				boolean kerb = false;
				for (int[] d : steps) {
					int ni = i + d[0];
					int nj = j + d[1];
					if (ni >= 0 && ni < Well.SIDE && nj < Well.SIDE || reach < 0 && Well.talusReach(ni, nj) < 0) {
						continue; // the piece's own face, or the land beside the land
					}
					BlockPos n = well.at(ni, 0, nj);
					kerb |= h - well.frontHeight(ni, nj, w.ground(n.getX(), n.getZ())) > 3;
				}
				if (kerb && w.get(x, h + 1, z).canBeReplaced()) {
					w.set(x, h + 1, z, STONE);
				}
			}
		}
	}

	/** Version 4: a fluid beside or over this cell (the chunk's own blocks; the noise's beyond it). */
	private static boolean wetBeside(Writer w, int x, int y, int z) {
		for (int[] d : OPEN_SIDES) {
			int nx = x + d[0];
			int ny = y + d[1];
			int nz = z + d[2];
			BlockState there = w.inside(nx, ny, nz) ? w.get(nx, ny, nz) : w.terrain().block(nx, ny, nz);
			if (!there.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** True if an apron (or sill) border cell has land more than 3 under it just outside. */
	private static boolean fallsAway(Well well, Writer w, int i, int j, int floor) {
		int[][] out = {{i - 1, j}, {i + 1, j}, {i, j + 1}};
		for (int[] o : out) {
			boolean open = Well.apron(o[0], o[1]) || o[0] >= 0 && o[0] < Well.SIDE && o[1] < Well.SIDE;
			if (open) {
				continue; // the apron itself, or the piece's own wall
			}
			BlockPos c = well.at(o[0], floor, o[1]);
			if (w.ground(c.getX(), c.getZ()) < floor - 3) {
				return true;
			}
		}
		return false;
	}

	/** The apron or sill cell next to an outside edge column, or null if none touches it. */
	private static @Nullable BlockPos nearestOpen(Well well, int i, int j, int floor) {
		int[][] in = {{i + 1, j}, {i - 1, j}, {i, j - 1}};
		for (int[] o : in) {
			boolean open = Well.apron(o[0], o[1]) || Well.wayFloor(o[0], o[1]);
			if (open) {
				return well.at(o[0], floor, o[1]);
			}
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
	private List<ItemStack> books(BlockPos pos, net.minecraft.server.@Nullable MinecraftServer server) {
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
				String name = ArchivePlan.LIST_NAMES.get(index);
				out.set(1, server != null && version >= 3 ? Books.six(server, name, server.overworld().getSeed()) : record(name));
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
