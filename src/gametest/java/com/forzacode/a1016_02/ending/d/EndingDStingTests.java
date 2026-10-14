package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
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

	/**
	 * Each column's ground: the noise terrain's {@code OCEAN_FLOOR_WG} height as the generator itself reports it, the
	 * top {@link Sting#fill} sees (the same in every column of {@link #STING_CHUNKS}). Not the chunk's heightmap after
	 * generation: the surface step then builds a frozen ocean's icebergs on the sea (packed ice and snow, with holes on
	 * purpose), that heightmap moves up onto the ice, and the sea under the iceberg and in its holes would count as
	 * underground.
	 */
	private static int[] ground(NoiseBasedChunkGenerator generator, RandomState random, ChunkAccess chunk) {
		int[] ground = new int[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				ground[x + z * 16] = generator.getBaseHeight(chunk.getPos().getMinBlockX() + x, chunk.getPos().getMinBlockZ() + z, Heightmap.Types.OCEAN_FLOOR_WG,
						chunk, random);
			}
		}
		return ground;
	}

	/** Open cells (air or fluid) under each column's top solid block of the {@link #ground}. */
	private static int caveCells(ChunkAccess chunk, int[] ground, int minY) {
		int open = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int top = ground[x + z * 16] - 1;
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
	 * Set while {@link #stingChunksHaveNoCaves} has chunks generating. The gate is one global flag read by the worldgen
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

	/** The dev world's seed. */
	private static final long STING_SEED = -4180345853331630785L;

	/**
	 * The chunks {@link #stingChunksHaveNoCaves} generates: fixed, so every run sees the same terrain (it used to take
	 * the chunk at its own test position, random per run). Picked from a scan of {@link #STING_SEED}.
	 */
	private static final List<ChunkPos> STING_CHUNKS = List.of(
			new ChunkPos(-122913, 901261), // frozen ocean, an iceberg on the sea (failed on CI when the count used the heightmap)
			new ChunkPos(-35, 24), // frozen ocean, a big iceberg, and a canyon starts here
			new ChunkPos(-39, 0), // snowy plains, a canyon full of aquifer water
			new ChunkPos(42, 6), // deep ocean, water and lava aquifers under the floor
			new ChunkPos(72, -60), // stony peaks, big noise caves
			new ChunkPos(66, -54), // jagged peaks over the deep dark
			new ChunkPos(24, -24), // plains over the deep dark, ancient city depth
			new ChunkPos(54, -186), // eroded badlands, with the pillars the surface step builds
			new ChunkPos(-108, 120), // swamp, water in its caves
			new ChunkPos(0, 0)); // the dev world's spawn, a dappled forest

	/**
	 * The real overworld generator, on fresh chunks off the server thread: with the gate open each chunk has caves;
	 * with it closed (D complete) nothing under the ground is open. Same chunks ({@link #STING_CHUNKS}), same seed.
	 */
	@GameTest(maxTicks = 12000)
	public void stingChunksHaveNoCaves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HolderGetter<NoiseGeneratorSettings> settingsLookup = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
		Holder<NoiseGeneratorSettings> settings = settingsLookup.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
		HolderGetter<MultiNoiseBiomeSourceParameterList> presets = level.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
		MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
		HolderGetter<NormalNoise> noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
		RandomState random = RandomState.create(noises, STING_SEED, settings.value());
		// No structures here: the generator's structure lookup would otherwise wait on the server thread.
		StructureManager structures = new StructureManager(level, level.getServer().getWorldGenSettings().options(), null) {
			@Override
			public List<StructureStart> startsForStructure(int x, int z, Predicate<Structure> matcher) {
				return List.of();
			}
		};
		Set<Holder<Biome>> possible = Set.copyOf(biomes.possibleBiomes());
		int minY = level.getMinY();

		List<ProtoChunk> open = STING_CHUNKS.stream().map(pos -> freshChunk(level, pos)).toList();
		List<ProtoChunk> closed = STING_CHUNKS.stream().map(pos -> freshChunk(level, pos)).toList();
		// Worldgen threads do the work: every chunk with the gate open, then every chunk with it closed (the gate is
		// global). The test polls each tick (slowed a little, since the test server does not wait).
		GENERATING.incrementAndGet();
		Sting.set(false, false);
		CompletableFuture<List<String>> done = CompletableFuture.allOf(open.stream().map(c -> generate(generator, random, structures, level, possible, c))
				.toArray(CompletableFuture[]::new)).thenCompose(v -> {
					Sting.set(true, true);
					List<CompletableFuture<String>> checks = new ArrayList<>();
					for (int i = 0; i < STING_CHUNKS.size(); i++) {
						ProtoChunk before = open.get(i);
						checks.add(generate(generator, random, structures, level, possible, closed.get(i)).thenApply(after -> {
							int[] ground = ground(generator, random, after);
							int caves = caveCells(before, ground, minY);
							int sealed = caveCells(after, ground, minY);
							return caves == 0 ? after.getPos() + ": the open chunk had no caves to compare with"
									: sealed > 0 ? after.getPos() + ": " + sealed + " open cells underground (" + caves + " before)" : null;
						}));
					}
					return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
							.thenApply(w -> checks.stream().map(CompletableFuture::join).filter(java.util.Objects::nonNull).toList());
				}).whenComplete((failures, error) -> {
					Sting.set(false, false);
					GENERATING.decrementAndGet();
				});
		helper.succeedWhen(() -> {
			if (!done.isDone()) {
				pause();
				throw helper.assertionException("still generating the chunks");
			}
			if (done.isCompletedExceptionally()) {
				Throwable error = done.exceptionNow();
				while (error.getCause() != null) {
					error = error.getCause();
				}
				error.printStackTrace();
				throw helper.assertionException("chunk generation failed: " + error);
			}
			List<String> failures = done.join();
			helper.assertTrue(failures.isEmpty(), "chunks generated after D are not closed: " + String.join("; ", failures));
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
