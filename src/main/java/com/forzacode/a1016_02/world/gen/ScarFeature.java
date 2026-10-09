package com.forzacode.a1016_02.world.gen;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The old scars, placed once per chunk at the end of vegetal decoration (after trees, before snow). Everything is
 * decided by the {@link ScarPlanner} of the current {@link ScarContext} snapshot; this only hands it the chunk.
 */
public record ScarFeature() implements Feature {
	public static final MapCodec<ScarFeature> CODEC = MapCodec.unit(ScarFeature::new);

	@Override
	public MapCodec<ScarFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		ScarContext context = ScarContext.current();
		if (context == null) {
			return false;
		}
		ScarPlanner planner = context.planner(level.getLevel());
		if (planner == null) {
			return false;
		}
		try {
			return planner.decorate(level, ChunkPos.containing(origin));
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] world: scars failed in chunk {}", ChunkPos.containing(origin), e);
			return false;
		}
	}
}
