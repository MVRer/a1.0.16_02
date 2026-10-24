package com.forzacode.a1016_02.debug.client.gallery;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The gallery's world (docs/SELF_CREATED_2.md, "The client run"): a deep superflat, bedrock, 100 stone, 3 dirt and grass,
 * so the ground's top block is at Y 40 and there is rock to dig underground parts into; plains, no structures, no
 * decoration. Dev only.
 */
public final class GalleryWorlds {
	/** The ground's top block: the first air cell is one above. */
	public static final int GROUND = 40;

	private GalleryWorlds() {
	}

	/** The deep flat generator settings. */
	public static FlatLevelGeneratorSettings deep(HolderLookup.Provider registries) {
		HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
		return FlatLevelGeneratorSettings.getDefault(biomes, registries.lookupOrThrow(Registries.STRUCTURE_SET), registries.lookupOrThrow(Registries.PLACED_FEATURE))
				.withBiomeAndLayers(List.of(new FlatLayerInfo(1, Blocks.BEDROCK), new FlatLayerInfo(100, Blocks.STONE), new FlatLayerInfo(3, Blocks.DIRT),
						new FlatLayerInfo(1, Blocks.GRASS_BLOCK)), Optional.empty(), biomes.getOrThrow(Biomes.PLAINS));
	}

	/** A normal world's dimensions with the deep flat overworld (Fallback A's {@code DevWorld}). */
	public static WorldDimensions dimensions(HolderLookup.Provider registries) {
		return WorldPresets.createNormalWorldDimensions(registries).replaceOverworldGenerator(registries, new FlatLevelSource(deep(registries)));
	}

	/** The client game test's world settings: the deep flat, cheats on, normal difficulty, no structures. */
	public static void adjust(WorldCreationUiState state) {
		state.setAllowCommands(true);
		state.setDifficulty(Difficulty.NORMAL);
		state.setGenerateStructures(false);
		state.updateDimensions(PresetEditor.flatWorldConfigurator(deep(state.getSettings().worldgenLoadContext())));
	}
}
