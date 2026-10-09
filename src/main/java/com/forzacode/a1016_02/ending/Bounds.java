package com.forzacode.a1016_02.ending;

import java.util.Collection;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** A box of blocks in one dimension (the house copy, or the real house): inside means inside the box, edges included. */
public record Bounds(ResourceKey<Level> dimension, BoundingBox box) {
	public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
		return dimension.equals(dim) && box.isInside(pos);
	}

	public boolean contains(GlobalPos pos) {
		return contains(pos.dimension(), pos.pos());
	}

	/** The box around these blocks, or empty if there are none. */
	public static Optional<Bounds> around(ResourceKey<Level> dimension, Collection<BlockPos> blocks) {
		return BoundingBox.encapsulatingPositions(blocks).map(box -> new Bounds(dimension, box));
	}
}
