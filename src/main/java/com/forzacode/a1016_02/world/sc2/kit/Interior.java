package com.forzacode.a1016_02.world.sc2.kit;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The inside of a place, where a player counts as in it ({@code Remains.containing}): the pyramid's core and pit, the
 * archive's hall and cells, the hollow's cavity; never its walls or grounds. Asked once a second for every participant
 * standing in the place's box, so it must be cheap (a few comparisons, no block reads, no allocation). Immutable.
 */
@FunctionalInterface
public interface Interior {
	/** Nothing is inside. */
	Interior NONE = (x, y, z) -> false;

	boolean contains(int x, int y, int z);

	default boolean contains(BlockPos pos) {
		return contains(pos.getX(), pos.getY(), pos.getZ());
	}

	/** Every cell of this box (a copy is kept). */
	static Interior box(BoundingBox box) {
		int x0 = box.minX();
		int y0 = box.minY();
		int z0 = box.minZ();
		int x1 = box.maxX();
		int y1 = box.maxY();
		int z1 = box.maxZ();
		return (x, y, z) -> x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
	}

	/** Inside any of these. */
	static Interior union(List<Interior> parts) {
		Interior[] all = parts.toArray(Interior[]::new);
		return (x, y, z) -> {
			for (Interior part : all) {
				if (part.contains(x, y, z)) {
					return true;
				}
			}
			return false;
		};
	}

	/** Inside both. */
	default Interior and(Interior other) {
		return (x, y, z) -> contains(x, y, z) && other.contains(x, y, z);
	}
}
