package com.forzacode.a1016_02.world.gen;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terrain questions the scar planners ask. Worldgen answers from the noise ({@link NoiseTerrain}), which is
 * deterministic and needs no generated chunks, so every chunk of a scar agrees on its shape; live code answers
 * from loaded blocks.
 */
public interface Terrain {
	int seaLevel();

	int minY();

	/** Y of the top solid ground block (under water, trees and plants). */
	int ground(int x, int z);

	/** Y of the top block counting water (not trees). Greater than {@link #ground} over water. */
	int surface(int x, int z);

	Holder<Biome> biome(int x, int y, int z);

	/** The block at a position as the terrain generator made it (caves and fluids included). */
	BlockState block(int x, int y, int z);

	default boolean wet(int x, int z) {
		return surface(x, z) > ground(x, z);
	}

	default Holder<Biome> surfaceBiome(int x, int z) {
		return biome(x, ground(x, z), z);
	}

	static boolean isOcean(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN);
	}

	static boolean isWetland(Holder<Biome> biome) {
		return isOcean(biome) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.SWAMP)
				|| biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.STONY_SHORE) || biome.is(Biomes.SNOWY_BEACH);
	}

	/** Woods where trees stand close: no houses here (half a tree through a roof looks like a bug, not a scar). */
	static boolean isDenseWoods(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA)
				|| biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA);
	}

	/** Trees worth stripping. */
	static boolean isWooded(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.WINDSWEPT_FOREST)
				|| biome.is(Biomes.WOODED_BADLANDS);
	}

	/** Grass that can die: no deserts, badlands, snow or bare peaks. */
	static boolean isGrassy(Holder<Biome> biome) {
		return !isWetland(biome) && !biome.is(BiomeTags.IS_BADLANDS) && !biome.is(Biomes.DESERT) && !biome.is(Biomes.SNOWY_PLAINS)
				&& !biome.is(Biomes.ICE_SPIKES) && !biome.is(Biomes.SNOWY_TAIGA) && !biome.is(Biomes.SNOWY_SLOPES) && !biome.is(Biomes.FROZEN_PEAKS)
				&& !biome.is(Biomes.JAGGED_PEAKS) && !biome.is(Biomes.STONY_PEAKS) && !biome.is(Biomes.GROVE)
				&& !biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS) && !biome.is(Biomes.MUSHROOM_FIELDS);
	}
}
