package com.forzacode.a1016_02.world.sc2.kit;

import java.util.List;

import net.minecraft.world.level.ChunkPos;

/**
 * A placement of the kit's own ({@link RaysPlacement} for 16 and 16d, {@link AxesPlacement} for the line's marker), which
 * vanilla's {@code /locate} does not search (26.3's {@code ChunkGenerator.findNearestMapStructure} knows only concentric
 * rings and random spreads): {@code world.mixin.ChunkGeneratorLocateMixin} asks its chunks, nearest first, and
 * {@link Decided#around} its candidates.
 */
public interface KitPlacement {
	/** Every placement chunk it can pick in a world with this seed (before its own {@code isStructureChunk} check). Any thread. */
	List<ChunkPos> chunks(long seed);
}
