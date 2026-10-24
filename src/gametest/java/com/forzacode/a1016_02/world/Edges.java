package com.forzacode.a1016_02.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The fair edge rule of the second wave's places (D-097.7, R12-3, R13-3), read off the built blocks: nobody walking on a
 * place's outer surface goes over an edge more than 3 high without water at its foot. Walking is the player's own
 * movement without a jump: onto a block one higher only by a stair or a slab, never onto a full block (a 1-high lip or
 * kerb stops a walk, and getting over it is a deliberate jump); down any height. The walk spreads from the seeds over the
 * columns' tops inside the area; a step down of more than 3 onto anything but water where either column is the place's own
 * is a fault.
 */
final class Edges {
	private Edges() {
	}

	/** The column's top block (solid or fluid; leaves not counted). */
	static int top(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	/**
	 * Every unguarded drop (at most 12, as text), walking from {@code seeds} (columns) inside {@code area}.
	 *
	 * @param own the place's own columns (a drop between two of the land's own is the land's)
	 */
	static List<String> unguardedDrops(ServerLevel level, BoundingBox area, Collection<BlockPos> seeds, BiPredicate<Integer, Integer> own) {
		List<String> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<long[]> queue = new ArrayDeque<>();
		for (BlockPos seed : seeds) {
			if (area.isInside(seed.getX(), area.minY(), seed.getZ()) && seen.add(ChunkPos.pack(seed.getX(), seed.getZ()))) {
				queue.add(new long[] {seed.getX(), seed.getZ()});
			}
		}
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty() && out.size() < 12) {
			long[] at = queue.poll();
			int x = (int) at[0];
			int z = (int) at[1];
			int y = top(level, x, z);
			for (int[] step : steps) {
				int nx = x + step[0];
				int nz = z + step[1];
				if (!area.isInside(nx, area.minY(), nz)) {
					continue;
				}
				int ny = top(level, nx, nz);
				BlockState there = level.getBlockState(new BlockPos(nx, ny, nz));
				if (ny > y) {
					boolean walkable = ny == y + 1 && (there.getBlock() instanceof StairBlock || there.getBlock() instanceof SlabBlock);
					if (!walkable) {
						continue;
					}
				}
				if (y - ny > 3 && there.getFluidState().isEmpty()) {
					if (own.test(x, z) || own.test(nx, nz)) {
						out.add(x + " " + y + " " + z + " to " + nx + " " + ny + " " + nz + " (" + (y - ny) + ")");
					}
					continue;
				}
				if (seen.add(ChunkPos.pack(nx, nz))) {
					queue.add(new long[] {nx, nz});
				}
			}
		}
		return out;
	}
}
