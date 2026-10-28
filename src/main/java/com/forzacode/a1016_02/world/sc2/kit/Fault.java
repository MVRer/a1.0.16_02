package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The error sign's grammar (docs/SELF_CREATED_2_R3.md, D-110, "The error signs"): what every place uses to show itself
 * from far as a fault in the world, never as a welcome. Worldgen-safe (through a {@link Writer}, within its clip, any
 * chunk order: everything here is decided per column from the square's own numbers), and the same through {@code Live}.
 * <ul>
 * <li><b>The lighting fault</b> ({@link #light}): a chunk-aligned square of the world lit as at noon with nothing
 * lighting it: {@code minecraft:light} on a {@value #GRID}-block grid one over the surface, inset {@value #INSET} from
 * the square's edge, waterlogged in water, each at {@value #LEVEL} (the square's top cells keep light {@value #MIN_LIT}
 * or more however the land steps). By day it does not show; by night it is the sign. Its light stops monsters spawning
 * on it and in a ring round it (never inside a place: the surface holds it), melts snow and ice on it and keeps new snow
 * off. Light falls by one a block, so the edge is soft: on open level land 11 three blocks out, 6 eight out, 0 fifteen
 * out (the plan's "at most 6 three blocks outside" cannot hold with 10 at the edge: R3-0's note). Its cells
 * are set through the writer (worldgen guards them, SC2-H); {@link #cells} finds them again for an answer that takes
 * them (through the {@code TraceService}, ledgered and given back as any answer's).</li>
 * <li><b>The cut</b> ({@link #cut}, {@link #lip}): chunk-aligned vertical faces, flat tops on a section line
 * ({@link #sectionFloor}), a piece of the world missing or standing where it should not; a face over 3 that a player
 * can walk off gets a 1-high lip ({@code world.Edges}' rule, D-097.7).</li>
 * <li><b>Alignment</b>: {@link #square}, {@link #sectionFloor}, {@link #sectionCeil}.</li>
 * </ul>
 * Glowstone only where a block is meant (each place's own). Nothing here logs.
 */
public final class Fault {
	/**
	 * The lights' level: full, as noon's sky (the gallery's 00f at 12 read as a dim orange patch at 64 and nothing at 128 by
	 * night; at 15 the square's top cells keep 13 or more on level land, which also melts snow and ice on it and keeps
	 * new snow off, as the plan's cold-biome square without snow wants).
	 */
	public static final int LEVEL = 15;
	/** The most a light is raised to where the land steps under it (the level already is the most). */
	public static final int MAX_LEVEL = 15;
	/** The light every top cell of the square should keep (95 % of them at least, measured: {@code SelfCreated3KitTests}). */
	public static final int MIN_LIT = 10;
	/** The lights' grid (blocks). */
	public static final int GRID = 3;
	/** The first light's inset from the square's edge (blocks). */
	public static final int INSET = 1;
	/** The highest face (blocks) one may walk off without a lip (D-097.7). */
	public static final int MAX_FACE = 3;

	private Fault() {
	}

	// --- alignment ---

	/** The section line at or under {@code y} (a multiple of 16). */
	public static int sectionFloor(int y) {
		return Math.floorDiv(y, 16) * 16;
	}

	/** The section line at or over {@code y} (a multiple of 16). */
	public static int sectionCeil(int y) {
		return -Math.floorDiv(-y, 16) * 16;
	}

	/**
	 * The chunk-aligned square of {@code side} blocks (rounded up to a multiple of 16, at least 16) whose chunks are
	 * centred on the chunk holding ({@code x}, {@code z}) (an even count of chunks leans east and south), from
	 * {@code minY} to {@code maxY}.
	 */
	public static BoundingBox square(int x, int z, int side, int minY, int maxY) {
		int chunks = Math.max(1, (side + 15) / 16);
		int cx = (x >> 4) - (chunks - 1) / 2;
		int cz = (z >> 4) - (chunks - 1) / 2;
		return new BoundingBox(cx * 16, minY, cz * 16, cx * 16 + chunks * 16 - 1, maxY, cz * 16 + chunks * 16 - 1);
	}

	/** True if the box's x and z run on chunk borders. */
	public static boolean chunkAligned(BoundingBox box) {
		return (box.minX() & 15) == 0 && (box.minZ() & 15) == 0 && (box.maxX() & 15) == 15 && (box.maxZ() & 15) == 15;
	}

	// --- the lighting fault ---

	/**
	 * The column's top cell for the fault: the cell over its highest block that is not air, a plant, snow on the ground or
	 * leaves (looking down from {@code fromY}), or its top water cell; {@code Integer.MIN_VALUE} if the writer reads
	 * nothing there. {@code water[0]} says which.
	 */
	static int topCell(Writer w, int x, int z, int fromY, boolean[] water) {
		int bottom = Math.max(w.minY(), w.clip().minY());
		for (int y = Math.min(fromY, w.clip().maxY()); y >= bottom; y--) {
			BlockState state = w.get(x, y, z);
			if (state.isAir() || state.is(BlockTags.LEAVES)) {
				continue;
			}
			// A light of a fault already in the water's top cell is that top (FIX-R3-C: a fill over its own lit water put a
			// second light in the air over each wet one, what the real run read as 16d's wet lights gone).
			if (state.is(Blocks.WATER) || isLight(state) && state.getValue(BlockStateProperties.WATERLOGGED)) {
				water[0] = true;
				return y;
			}
			if (state.getFluidState().isEmpty() && state.canBeReplaced()) {
				continue;
			}
			water[0] = false;
			return y + 1;
		}
		return Integer.MIN_VALUE;
	}

	/**
	 * Lights {@code square}'s part inside the writer's clip (the lighting fault, above), looking for each column's top
	 * from {@code fromY} down (the place's own top over the square: what it built, or the land's). Plants and snow in a
	 * light's cell and over it go; leaves stay. Returns the cells set. The same cells whatever the chunk order: each
	 * light's level reads only its own 3 x 3 columns, and a column outside the clip counts as level.
	 */
	public static List<BlockPos> light(Writer w, BoundingBox square, int fromY) {
		BoundingBox clip = w.clip();
		int x0 = Math.max(square.minX(), clip.minX());
		int x1 = Math.min(square.maxX(), clip.maxX());
		int z0 = Math.max(square.minZ(), clip.minZ());
		int z1 = Math.min(square.maxZ(), clip.maxZ());
		List<BlockPos> out = new ArrayList<>();
		if (x0 > x1 || z0 > z1) {
			return out;
		}
		// Each column's top once (the lights' 3 x 3 read their neighbours').
		int sx = x1 - x0 + 3;
		int sz = z1 - z0 + 3;
		int[] tops = new int[sx * sz];
		boolean[] wet = new boolean[sx * sz];
		boolean[] water = new boolean[1];
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				int i = (x - x0 + 1) * sz + (z - z0 + 1);
				boolean in = x >= square.minX() && x <= square.maxX() && z >= square.minZ() && z <= square.maxZ() && w.inside(x, clip.minY(), z);
				tops[i] = in ? topCell(w, x, z, fromY, water) : Integer.MIN_VALUE;
				wet[i] = in && water[0];
			}
		}
		for (int x = firstOnGrid(square.minX(), x0); x <= Math.min(x1, square.maxX() - INSET); x += GRID) {
			for (int z = firstOnGrid(square.minZ(), z0); z <= Math.min(z1, square.maxZ() - INSET); z += GRID) {
				int i = (x - x0 + 1) * sz + (z - z0 + 1);
				int y = tops[i];
				if (y == Integer.MIN_VALUE || !w.inside(x, y, z)) {
					continue;
				}
				int level = LEVEL;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int other = tops[(x + dx - x0 + 1) * sz + (z + dz - z0 + 1)];
						if (other != Integer.MIN_VALUE) {
							level = Math.max(level, MIN_LIT + Math.abs(dx) + Math.abs(dz) + Math.abs(other - y));
						}
					}
				}
				BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Math.min(MAX_LEVEL, level))
						.setValue(BlockStateProperties.WATERLOGGED, wet[i]);
				if (w.set(x, y, z, light)) {
					out.add(new BlockPos(x, y, z));
				}
				if (!wet[i]) {
					// A tall plant's top or snow over the light's cell would hang in the air.
					for (int up = y + 1; up <= Math.min(y + 2, clip.maxY()); up++) {
						BlockState over = w.get(x, up, z);
						if (!over.isAir() && over.getFluidState().isEmpty() && over.canBeReplaced() && !over.is(BlockTags.LEAVES)) {
							w.set(x, up, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		return out;
	}

	/** The first grid coordinate (from {@code edge + INSET}, every {@value #GRID}) at or after {@code from}. */
	private static int firstOnGrid(int edge, int from) {
		int first = edge + INSET;
		if (from <= first) {
			return first;
		}
		return first + (from - first + GRID - 1) / GRID * GRID;
	}

	/**
	 * The fault's light blocks in this square of a live level (an answer's: 20b v2 and 18c take them; columns read from
	 * their top down to their highest motion-blocking block, leaves not counted). Server thread; the square's chunks
	 * loaded.
	 */
	public static List<BlockPos> cells(ServerLevel level, BoundingBox square) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = square.minX(); x <= square.maxX(); x++) {
			for (int z = square.minZ(); z <= square.maxZ(); z++) {
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				int bottom = Math.max(square.minY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 2);
				for (int y = Math.min(top, square.maxY()); y >= bottom; y--) {
					if (level.getBlockState(at.set(x, y, z)).is(Blocks.LIGHT)) {
						out.add(at.immutable());
					}
				}
			}
		}
		return out;
	}

	// --- the cut ---

	/**
	 * A piece of the world missing: every block of the square's columns (inside the clip) over {@code topY} goes, water
	 * included (the top is flat on {@code topY}). Returns the cells cleared. Its faces are the place's to make fair
	 * ({@link #lip}, or stopping the cut where the land round it rises more than {@value #MAX_FACE}).
	 */
	public static int cut(Writer w, BoundingBox square, int topY) {
		BoundingBox clip = w.clip();
		int cleared = 0;
		for (int x = Math.max(square.minX(), clip.minX()); x <= Math.min(square.maxX(), clip.maxX()); x++) {
			for (int z = Math.max(square.minZ(), clip.minZ()); z <= Math.min(square.maxZ(), clip.maxZ()); z++) {
				for (int y = Math.max(topY + 1, clip.minY()); y <= Math.min(square.maxY(), clip.maxY()); y++) {
					if (!w.get(x, y, z).isAir() && w.set(x, y, z, Blocks.AIR.defaultBlockState())) {
						cleared++;
					}
				}
			}
		}
		return cleared;
	}

	/**
	 * The fair edge of a square standing out of the land: on each of its border columns (inside the clip) whose top stands
	 * more than {@value #MAX_FACE} over the land just outside it, one block of {@code material} on its top, a 1-high lip
	 * that stops a walk ({@code world.Edges}' rule). {@code land} is the land's top cell outside (the cell over its ground: the noise's, or the
	 * place's own reading); the square's own top is read through the writer from {@code fromY} down. Returns the lips set.
	 */
	public static int lip(Writer w, BoundingBox square, int fromY, java.util.function.IntBinaryOperator land, BlockState material) {
		BoundingBox clip = w.clip();
		int set = 0;
		boolean[] water = new boolean[1];
		for (int x = Math.max(square.minX(), clip.minX()); x <= Math.min(square.maxX(), clip.maxX()); x++) {
			for (int z = Math.max(square.minZ(), clip.minZ()); z <= Math.min(square.maxZ(), clip.maxZ()); z++) {
				int ox = x == square.minX() ? -1 : x == square.maxX() ? 1 : 0;
				int oz = z == square.minZ() ? -1 : z == square.maxZ() ? 1 : 0;
				if (ox == 0 && oz == 0) {
					continue;
				}
				int top = topCell(w, x, z, fromY, water);
				if (top == Integer.MIN_VALUE || water[0]) {
					continue;
				}
				int outside = Math.min(ox != 0 ? land.applyAsInt(x + ox, z) : Integer.MAX_VALUE, oz != 0 ? land.applyAsInt(x, z + oz) : Integer.MAX_VALUE);
				if (top - outside > MAX_FACE && w.get(x, top, z).isAir() && w.set(x, top, z, material)) {
					set++;
				}
			}
		}
		return set;
	}

	/** True if this state is one of the fault's lights. */
	public static boolean isLight(BlockState state) {
		return state.is(Blocks.LIGHT);
	}

	/** A fault light of this level (waterlogged or not), for tests and answers that put one back. */
	public static BlockState lightOf(int level, boolean waterlogged) {
		return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Math.clamp(level, 0, 15)).setValue(BlockStateProperties.WATERLOGGED, waterlogged);
	}
}
