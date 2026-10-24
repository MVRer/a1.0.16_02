package com.forzacode.a1016_02.world.sc2.kit;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.Vegetation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * Building helpers for {@link Shape#fill}: boxes, shells, columns, foundations, clearing, dead ground, containers, and
 * per-cell randomness. Every helper writes through the {@link Writer}, so everything is clipped to the call's chunk; pass
 * whole-structure boxes freely, only their part inside the clip is visited.
 */
public final class Draw {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

	private Draw() {
	}

	/** What a cell becomes, or null to leave it. */
	@FunctionalInterface
	public interface Paint {
		@Nullable BlockState at(int x, int y, int z);
	}

	/** Work on one column. */
	@FunctionalInterface
	public interface ColumnAction {
		void at(int x, int z);
	}

	/**
	 * A few materials mixed by weight, picked per cell from the seed ({@link #unit}): a face of bricks with a little cracked
	 * and mossy brick, the same in every chunk. Immutable.
	 *
	 * <pre>{@code
	 * Draw.Mix face = Draw.Mix.of(Blocks.BRICKS.defaultBlockState(), 20).and(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 1);
	 * w.set(x, y, z, face.at(seed, x - x0, y - y0, z - z0));
	 * }</pre>
	 */
	public static final class Mix {
		private final List<BlockState> states;
		private final double[] upTo;

		private Mix(List<BlockState> states, double[] upTo) {
			this.states = states;
			this.upTo = upTo;
		}

		public static Mix of(BlockState state, double weight) {
			return new Mix(List.of(state), new double[] {Math.max(0, weight)});
		}

		/** This mix with one more material. */
		public Mix and(BlockState state, double weight) {
			List<BlockState> more = new java.util.ArrayList<>(states);
			more.add(state);
			double[] next = java.util.Arrays.copyOf(upTo, upTo.length + 1);
			next[upTo.length] = upTo[upTo.length - 1] + Math.max(0, weight);
			return new Mix(List.copyOf(more), next);
		}

		/** The material of this cell. */
		public BlockState at(long seed, int x, int y, int z) {
			double total = upTo[upTo.length - 1];
			double roll = unit(seed, x, y, z) * total;
			for (int i = 0; i < upTo.length; i++) {
				if (roll < upTo[i]) {
					return states.get(i);
				}
			}
			return states.getLast();
		}
	}

	// --- per-cell randomness (never a shared RandomSource: chunks come in any order) ---

	/**
	 * A number in [0, 1) for this seed and cell, the same in every chunk and on every run. Pass the cell relative to the
	 * start's origin, so the same start built elsewhere (the gallery builds worldgen's starts at its own origins) looks the
	 * same.
	 */
	public static double unit(long seed, int x, int y, int z) {
		return Hash.unit(Hash.of(seed, x, y, z));
	}

	/** True with probability {@code p} for this seed and cell. */
	public static boolean chance(long seed, int x, int y, int z, double p) {
		return unit(seed, x, y, z) < p;
	}

	/** An int in [min, max] for this seed and key. */
	public static int between(long seed, long key, int min, int max) {
		return Hash.between(Hash.of(seed, key), min, max);
	}

	// --- boxes ---

	/** The overlap of two boxes, if any. */
	public static Optional<BoundingBox> intersection(BoundingBox a, BoundingBox b) {
		int x0 = Math.max(a.minX(), b.minX());
		int y0 = Math.max(a.minY(), b.minY());
		int z0 = Math.max(a.minZ(), b.minZ());
		int x1 = Math.min(a.maxX(), b.maxX());
		int y1 = Math.min(a.maxY(), b.maxY());
		int z1 = Math.min(a.maxZ(), b.maxZ());
		return x0 > x1 || y0 > y1 || z0 > z1 ? Optional.empty() : Optional.of(new BoundingBox(x0, y0, z0, x1, y1, z1));
	}

	/** Every cell of {@code box} inside the writer's clip becomes {@code state}. */
	public static void fill(Writer w, BoundingBox box, BlockState state) {
		paint(w, box, (x, y, z) -> state);
	}

	/** Every cell of {@code box} inside the writer's clip becomes what {@code paint} says (null leaves it). */
	public static void paint(Writer w, BoundingBox box, Paint paint) {
		int x0 = Math.max(box.minX(), w.minX());
		int y0 = Math.max(box.minY(), w.minY());
		int z0 = Math.max(box.minZ(), w.minZ());
		int x1 = Math.min(box.maxX(), w.maxX());
		int y1 = Math.min(box.maxY(), w.maxY());
		int z1 = Math.min(box.maxZ(), w.maxZ());
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					BlockState state = paint.at(x, y, z);
					if (state != null) {
						w.set(x, y, z, state);
					}
				}
			}
		}
	}

	/** The outer {@code thickness} cells of {@code outer} (walls, floor and roof) become {@code state}. */
	public static void shell(Writer w, BoundingBox outer, int thickness, BlockState state) {
		paint(w, outer, (x, y, z) -> depth(outer, x, y, z) < thickness ? state : null);
	}

	/** {@link #shell} of {@code shell}, and everything inside it {@code inside}. */
	public static void hollow(Writer w, BoundingBox outer, int thickness, BlockState shell, BlockState inside) {
		paint(w, outer, (x, y, z) -> depth(outer, x, y, z) < thickness ? shell : inside);
	}

	/** How many cells this one is in from the nearest face of {@code box} (0 on a face). */
	public static int depth(BoundingBox box, int x, int y, int z) {
		int dx = Math.min(x - box.minX(), box.maxX() - x);
		int dy = Math.min(y - box.minY(), box.maxY() - y);
		int dz = Math.min(z - box.minZ(), box.maxZ() - z);
		return Math.min(dx, Math.min(dy, dz));
	}

	/** Every column of {@code box} inside the writer's clip. */
	public static void columns(Writer w, BoundingBox box, ColumnAction action) {
		for (int x = Math.max(box.minX(), w.minX()); x <= Math.min(box.maxX(), w.maxX()); x++) {
			for (int z = Math.max(box.minZ(), w.minZ()); z <= Math.min(box.maxZ(), w.maxZ()); z++) {
				action.at(x, z);
			}
		}
	}

	/**
	 * A tunnel {@code width} wide and {@code height} high, from {@code start} (its lower left cell, looking along
	 * {@code dir}; the width runs to the right) for {@code length} cells, its floor rising {@code rise} (1 up, -1 down, 0
	 * flat) once every {@code every} cells (the archive's way in: 1 every 2). Where the floor steps, the higher of the two
	 * cells' ceilings is carved over both, so a player walks up or down it without a jump into the ceiling. Every cell
	 * becomes {@code fill} (air). Returns the floor cell after the last one.
	 */
	public static BlockPos tunnel(Writer w, BlockPos start, Direction dir, int length, int width, int height, int rise, int every, BlockState fill) {
		Direction right = dir.getClockWise();
		int step = Math.max(1, every);
		for (int i = 0; i < length; i++) {
			int floor = rise * (i / step);
			int before = i == 0 ? floor : rise * ((i - 1) / step);
			int after = i + 1 >= length ? floor : rise * ((i + 1) / step);
			int top = Math.max(floor, Math.max(before, after)) + height - 1;
			for (int a = 0; a < width; a++) {
				for (int y = floor; y <= top; y++) {
					w.set(start.getX() + dir.getStepX() * i + right.getStepX() * a, start.getY() + y, start.getZ() + dir.getStepZ() * i + right.getStepZ() * a, fill);
				}
			}
		}
		return start.relative(dir, length).above(rise * (length / step));
	}

	/** Cells {@code y0} to {@code y1} (either order) of one column. */
	public static void column(Writer w, int x, int z, int y0, int y1, BlockState state) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			w.set(x, y, z, state);
		}
	}

	// --- ground ---

	/**
	 * True for what a foundation stands on: a full solid block that is not a plant, a tree part, a fluid or a falling
	 * block (falling blocks are filled under, so nothing slides out from under a wall).
	 */
	public static boolean solidGround(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !Vegetation.dies(state) && !Vegetation.isSnowLayer(state)
				&& !(state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) && state.canOcclude();
	}

	/**
	 * The highest cell of a column from {@code fromY} down to {@code toY} one could stand on (anything that stops a walk:
	 * the land with its grass, a stair, a slab, a log; or a fluid; never leaves), or {@code toY - 1} if none. Reads only what
	 * the writer sees.
	 */
	public static int standingTop(Writer w, int x, int z, int fromY, int toY) {
		for (int y = fromY; y >= toY; y--) {
			BlockState state = w.get(x, y, z);
			if (!state.getFluidState().isEmpty() || state.isSolid() && !state.is(BlockTags.LEAVES)) {
				return y;
			}
		}
		return toY - 1;
	}

	/**
	 * A lip on a column's top at {@code top} (the second wave's fair edges, R12-3 and R13-3): one block over it of its own
	 * top's material (grass over dirt then, sandstone for sand, stone for gravel or anything that would fall or is not a
	 * full block), so whoever walks toward the edge meets it and crossing it is a deliberate jump. Nothing if a solid block
	 * stands there already.
	 */
	public static void lip(Writer w, int x, int z, int top) {
		BlockState over = w.get(x, top + 1, z);
		if (over.isSolid() && !over.is(BlockTags.LEAVES)) {
			return;
		}
		BlockState under = w.get(x, top, z);
		BlockState lip;
		if (under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.PODZOL) || under.is(Blocks.MYCELIUM)) {
			lip = under.getBlock().defaultBlockState();
			w.set(x, top, z, DIRT);
		} else if (under.is(Blocks.SAND) || under.is(Blocks.SANDSTONE)) {
			lip = Blocks.SANDSTONE.defaultBlockState();
		} else if (under.is(Blocks.RED_SAND) || under.is(Blocks.RED_SANDSTONE)) {
			lip = Blocks.RED_SANDSTONE.defaultBlockState();
		} else if (solidGround(under)) {
			lip = under;
		} else {
			lip = Blocks.STONE.defaultBlockState();
		}
		w.set(x, top + 1, z, lip);
	}

	/**
	 * A foundation under ({@code x}, {@code z}): from {@code topY} down, every cell that is not {@link #solidGround}
	 * becomes {@code state}, until solid ground or {@code maxDepth} cells. Returns the cells filled.
	 */
	public static int foundation(Writer w, int x, int z, int topY, BlockState state, int maxDepth) {
		int filled = 0;
		for (int y = topY; y > topY - maxDepth && y >= w.minY(); y--) {
			if (!w.inside(x, y, z)) {
				return filled;
			}
			if (solidGround(w.get(x, y, z))) {
				return filled;
			}
			w.set(x, y, z, state);
			filled++;
		}
		return filled;
	}

	/** Cells {@code fromY} to {@code toY} of the column become air (fluids too). */
	public static void clear(Writer w, int x, int z, int fromY, int toY) {
		for (int y = fromY; y <= toY; y++) {
			w.set(x, y, z, AIR);
		}
	}

	/**
	 * Dead ground in one column: grass, moss and podzol to dirt, mycelium to dirt; plants, snow layers, leaves, vines and
	 * what hangs from them gone, from the ground up {@code up} cells (trunks stay: bare trees).
	 */
	public static void deadGround(Writer w, int x, int z, int up) {
		int ground = w.ground(x, z);
		BlockState top = w.get(x, ground, z);
		if (Vegetation.isGrassGround(top) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.DIRT_PATH)) {
			w.set(x, ground, z, DIRT);
		}
		for (int y = ground + 1; y <= ground + up; y++) {
			BlockState state = w.get(x, y, z);
			if (state.isAir()) {
				continue;
			}
			if (Vegetation.isPlant(state) || Vegetation.isLeafy(state) || Vegetation.isSnowLayer(state) || state.is(BlockTags.LEAVES)) {
				w.set(x, y, z, AIR);
			}
		}
	}

	// --- containers ---

	/** A chest facing {@code facing} holding these stacks (worldgen-safe). */
	public static void chest(Writer w, BlockPos pos, Direction facing, List<ItemStack> items) {
		if (w.set(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing)) || w.inside(pos)) {
			w.contents(pos, items);
		}
	}

	/**
	 * A chiseled bookshelf facing {@code facing} with these stacks in its six slots (empty stacks leave a slot empty),
	 * each filled slot shown in its state (worldgen-safe).
	 */
	public static void shelf(Writer w, BlockPos pos, Direction facing, List<ItemStack> books) {
		BlockState state = Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(ChiseledBookShelfBlock.FACING, facing);
		for (int slot = 0; slot < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size(); slot++) {
			boolean full = slot < books.size() && !books.get(slot).isEmpty();
			state = state.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(slot), full);
		}
		w.set(pos, state);
		w.contents(pos, books);
	}

	/** A lectern facing {@code facing}, with this book on it if it is not empty (worldgen-safe). */
	public static void lectern(Writer w, BlockPos pos, Direction facing, ItemStack book) {
		w.set(pos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, !book.isEmpty()));
		if (!book.isEmpty()) {
			w.blockEntity(pos, LecternBlockEntity.class, lectern -> lectern.setBook(book.copy()));
		}
	}
}
