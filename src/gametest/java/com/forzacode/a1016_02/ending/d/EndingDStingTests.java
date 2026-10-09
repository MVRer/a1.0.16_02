package com.forzacode.a1016_02.ending.d;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The sting: once D is complete, new chunks are not carved and their noise caves are filled (the carver gate on the
 * generator, off by default). Existing chunks are never touched.
 */
public class EndingDStingTests {
	private static ProtoChunk freshChunk(ServerLevel level, ChunkPos pos) {
		return new ProtoChunk(pos, UpgradeData.EMPTY, level, PalettedContainerFactory.create(level.registryAccess()), null);
	}

	/** Open cells (air or fluid) under each column's top solid block. */
	private static int caveCells(ChunkAccess chunk, int minY) {
		Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		int open = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int top = floor.getFirstAvailable(x, z) - 1;
				for (int y = top - 1; y > minY + 8; y--) {
					BlockState state = chunk.getBlockState(pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z));
					if (state.isAir() || !state.getFluidState().isEmpty() && state.canBeReplaced()) {
						open++;
					}
				}
			}
		}
		return open;
	}

	@GameTest
	public void stingFillsOnlyUnderTheGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ProtoChunk chunk = freshChunk(level, new ChunkPos(10000, 10000));
		int minY = level.getMinY();
		BlockState stone = Blocks.STONE.defaultBlockState();
		int base = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = minY; y <= minY + 40; y++) {
					chunk.setBlockState(new BlockPos(base + x, y, baseZ + z), stone);
				}
			}
		}
		// A cave pocket and a buried water pocket under the ground; the sea above it at one column.
		chunk.setBlockState(new BlockPos(base + 5, minY + 10, baseZ + 5), Blocks.AIR.defaultBlockState());
		chunk.setBlockState(new BlockPos(base + 5, minY + 11, baseZ + 5), Blocks.AIR.defaultBlockState());
		chunk.setBlockState(new BlockPos(base + 6, minY + 20, baseZ + 6), Blocks.WATER.defaultBlockState());
		for (int y = minY + 41; y <= minY + 45; y++) {
			chunk.setBlockState(new BlockPos(base + 8, y, baseZ + 8), Blocks.WATER.defaultBlockState());
		}
		Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
		int filled = Sting.fill(chunk, stone, minY);
		helper.assertTrue(filled == 3, "expected the two cave cells and the buried water filled, got " + filled);
		helper.assertTrue(chunk.getBlockState(new BlockPos(base + 5, minY + 10, baseZ + 5)).is(Blocks.STONE), "the cave is still open");
		helper.assertTrue(chunk.getBlockState(new BlockPos(base + 6, minY + 20, baseZ + 6)).is(Blocks.STONE), "the buried water is still there");
		helper.assertTrue(chunk.getBlockState(new BlockPos(base + 8, minY + 43, baseZ + 8)).is(Blocks.WATER), "the sea above the ground was filled");
		helper.assertTrue(chunk.getBlockState(new BlockPos(base + 2, minY + 41, baseZ + 2)).isAir(), "the sky above the ground was filled");
		helper.succeed();
	}

	/**
	 * Chunks {@link #stingChunksHaveNoCaves} is generating right now. The gate is one global flag read by the worldgen
	 * threads, so {@link #stingGateFollowsTheCompleteFlag} waits until none is (game test batches overlap).
	 */
	private static final java.util.concurrent.atomic.AtomicInteger GENERATING = new java.util.concurrent.atomic.AtomicInteger();

	@GameTest(maxTicks = 12000)
	public void stingGateFollowsTheCompleteFlag(GameTestHelper helper) {
		helper.succeedWhen(() -> {
			if (GENERATING.get() > 0) {
				throw helper.assertionException("waiting: the cave test has a chunk generating");
			}
			try {
				Sting.set(false, false);
				helper.assertFalse(Sting.skipCarvers() || Sting.fillNoiseCaves(), "the gate is on by default");
				Sting.refresh(helper.getLevel().getServer());
				helper.assertFalse(Sting.skipCarvers(), "the gate opened without ending:d_complete");
				Sting.set(true, true);
				helper.assertTrue(Sting.skipCarvers() && Sting.fillNoiseCaves(), "the gate did not close");
			} finally {
				Sting.set(false, false);
			}
		});
	}

	/**
	 * The real overworld generator, on fresh chunks off the server thread: with the gate open the chunk has caves;
	 * with it closed (D complete) nothing under the ground is open. Same chunks, same seed.
	 */
	@GameTest(maxTicks = 6000)
	public void stingChunksHaveNoCaves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HolderGetter<NoiseGeneratorSettings> settingsLookup = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
		Holder<NoiseGeneratorSettings> settings = settingsLookup.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
		HolderGetter<MultiNoiseBiomeSourceParameterList> presets = level.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
		MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
		HolderGetter<NormalNoise> noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
		RandomState random = RandomState.create(noises, -4180345853331630785L, settings.value());
		ChunkPos pos = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
		// No structures here: the generator's structure lookup would otherwise wait on the server thread.
		StructureManager structures = new StructureManager(level, level.getServer().getWorldGenSettings().options(), null) {
			@Override
			public List<StructureStart> startsForStructure(int x, int z, Predicate<Structure> matcher) {
				return List.of();
			}
		};
		Set<Holder<Biome>> possible = Set.copyOf(biomes.possibleBiomes());

		ProtoChunk open = freshChunk(level, pos);
		ProtoChunk closed = freshChunk(level, pos);
		// Worldgen threads do the work; the test polls each tick (slowed a little, since the test server does not wait).
		GENERATING.incrementAndGet();
		Sting.set(false, false);
		CompletableFuture<ChunkAccess> before = generate(generator, random, structures, level, possible, open);
		CompletableFuture<?>[] after = new CompletableFuture<?>[1];
		helper.succeedWhen(() -> {
			if (after[0] == null) {
				if (!before.isDone()) {
					pause();
					throw helper.assertionException("still generating the open chunk");
				}
				Sting.set(true, true);
				after[0] = generate(generator, random, structures, level, possible, closed).whenComplete((c, e) -> {
					Sting.set(false, false);
					GENERATING.decrementAndGet();
				});
			}
			if (!after[0].isDone()) {
				pause();
				throw helper.assertionException("still generating the closed chunk");
			}
			for (CompletableFuture<?> future : new CompletableFuture<?>[] {before, after[0]}) {
				if (future.isCompletedExceptionally()) {
					Throwable error = future.exceptionNow();
					while (error.getCause() != null) {
						error = error.getCause();
					}
					error.printStackTrace();
					throw helper.assertionException("chunk generation failed: " + error);
				}
			}
			int minY = level.getMinY();
			int caves = caveCells(open, minY);
			int sealed = caveCells(closed, minY);
			helper.assertTrue(caves > 0, "the open chunk had no caves to compare with");
			helper.assertTrue(sealed == 0, "a chunk generated after D still has " + sealed + " open cells underground (" + caves + " before)");
		});
	}

	private static void pause() {
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static CompletableFuture<ChunkAccess> generate(NoiseBasedChunkGenerator generator, RandomState random, StructureManager structures,
			ServerLevel level, Set<Holder<Biome>> possible, ProtoChunk chunk) {
		return generator.createBiomes(random, Blender.empty(), structures, chunk)
				// Biomes from the chunk itself (the level's biome manager would read the level's chunks from this thread).
				.thenCompose(c -> {
					chunk.setPersistedStatus(ChunkStatus.BIOMES);
					return generator.buildTerrain(c, Blender.empty(), random, structures, new BiomeManager(c, 0L), null, possible);
				});
	}
}
