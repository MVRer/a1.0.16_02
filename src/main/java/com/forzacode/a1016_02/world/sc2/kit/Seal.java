package com.forzacode.a1016_02.world.sc2.kit;

import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The common seal (docs/SELF_CREATED_2.md, "Remains: common rules"): within {@code reach} (2) blocks of an interior,
 * fluid, air and falling blocks become the structure's own material, so no cave opens into it unplanned, no sand falls
 * and no water or lava runs in. Ores and stone stay. The interior is given per column ({@link Columns}: one range of
 * heights; a pure function, the same for every chunk), so a chunk seals exactly what its neighbours expect. Seal before
 * cutting the openings: air within reach is filled, outdoors too.
 */
public final class Seal {
	/** No interior in a column. */
	public static final long NONE = Long.MIN_VALUE;
	/** The common reach. */
	public static final int REACH = 2;

	private Seal() {
	}

	/** The interior's heights in each column: {@link #range} or {@link #NONE}. Pure and cheap (asked 25 times a column). */
	@FunctionalInterface
	public interface Columns {
		long range(int x, int z);
	}

	/** The material a sealed cell becomes. */
	@FunctionalInterface
	public interface Material {
		BlockState at(int x, int y, int z);
	}

	/** Heights {@code lo} to {@code hi} (inclusive) packed for {@link Columns}; {@link #NONE} if {@code hi < lo}. */
	public static long range(int lo, int hi) {
		return hi < lo ? NONE : ((long) lo << 32) | (hi & 0xFFFFFFFFL);
	}

	public static int lo(long range) {
		return (int) (range >> 32);
	}

	public static int hi(long range) {
		return (int) range;
	}

	/** True for what the seal fills: a fluid, air, or a falling block. */
	public static boolean leaks(BlockState state) {
		return state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof FallingBlock;
	}

	/**
	 * Seals every cell of {@code clip} within {@code reach} (Chebyshev) of the interior that is not itself inside it.
	 * Returns how many cells became material.
	 */
	public static int around(Writer w, BoundingBox clip, Columns interior, int reach, Material material) {
		int sealed = 0;
		for (int x = clip.minX(); x <= clip.maxX(); x++) {
			for (int z = clip.minZ(); z <= clip.maxZ(); z++) {
				long own = interior.range(x, z);
				int zoneLo = Integer.MAX_VALUE;
				int zoneHi = Integer.MIN_VALUE;
				for (int dx = -reach; dx <= reach; dx++) {
					for (int dz = -reach; dz <= reach; dz++) {
						long near = dx == 0 && dz == 0 ? own : interior.range(x + dx, z + dz);
						if (near != NONE) {
							zoneLo = Math.min(zoneLo, lo(near) - reach);
							zoneHi = Math.max(zoneHi, hi(near) + reach);
						}
					}
				}
				if (zoneLo > zoneHi) {
					continue;
				}
				int from = Math.max(zoneLo, clip.minY());
				int to = Math.min(zoneHi, clip.maxY());
				for (int y = from; y <= to; y++) {
					if (own != NONE && y >= lo(own) && y <= hi(own)) {
						y = hi(own);
						continue;
					}
					if (leaks(w.get(x, y, z)) && w.set(x, y, z, material.at(x, y, z))) {
						sealed++;
					}
				}
			}
		}
		return sealed;
	}
}
