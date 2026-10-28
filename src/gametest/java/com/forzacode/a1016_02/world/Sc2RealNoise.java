package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.RaysPlacement;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Piece;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.core.Holder.Reference;

import org.jspecify.annotations.Nullable;

/**
 * Real overworld noise without a level, for the second wave's height and terrain tests (SC2-H): a world preset's
 * generator and random state for any seed and any height (a data pack's {@code min_y} and {@code height} clamp the
 * noise exactly as a level of that height would), its terrain, its structure placements, and worldgen's own path for a
 * start ({@link Structure#generate}, the kit's gates and siting, vanilla's biome check) with that height. Nothing is
 * generated or loaded.
 */
final class Sc2RealNoise {
	private Sc2RealNoise() {
	}

	/** One world's noise. */
	record World(String name, long seed, NoiseBasedChunkGenerator generator, RandomState random, LevelHeightAccessor height, Terrain terrain,
			ChunkGeneratorStructureState structures) {
		int minY() {
			return height.getMinY();
		}

		int maxY() {
			return height.getMaxY();
		}

		/** The same noise with another height (a data pack's). */
		World withHeight(ServerLevel level, int minY, int height) {
			return of(level, name, generator.generatorSettings().unwrapKey().orElseThrow(), seed, minY, height);
		}
	}

	/** The world presets the tests read: the default overworld, amplified, large biomes. */
	static final Map<String, ResourceKey<NoiseGeneratorSettings>> PRESETS = presets();

	private static Map<String, ResourceKey<NoiseGeneratorSettings>> presets() {
		Map<String, ResourceKey<NoiseGeneratorSettings>> out = new LinkedHashMap<>();
		out.put("normal", NoiseGeneratorSettings.OVERWORLD);
		out.put("amplified", NoiseGeneratorSettings.AMPLIFIED);
		out.put("large", NoiseGeneratorSettings.LARGE_BIOMES);
		return out;
	}

	/** The noise of {@code preset} for {@code seed}, as a level from {@code minY} with {@code height} blocks would read it. */
	static World of(ServerLevel level, String name, ResourceKey<NoiseGeneratorSettings> preset, long seed, int minY, int height) {
		HolderGetter<NoiseGeneratorSettings> settingsLookup = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
		Holder<NoiseGeneratorSettings> settings = settingsLookup.getOrThrow(preset);
		HolderGetter<MultiNoiseBiomeSourceParameterList> lists = level.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
		MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(lists.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
		HolderGetter<NormalNoise> noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
		RandomState random = RandomState.create(noises, seed, settings.value());
		LevelHeightAccessor accessor = LevelHeightAccessor.create(minY, height);
		Terrain terrain = Terrains.noise(generator, random, accessor);
		ChunkGeneratorStructureState structures = ChunkGeneratorStructureState.createForNormal(random, seed, ChunkPos.ZERO, biomes,
				level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET));
		return new World(name, seed, generator, random, accessor, terrain, structures);
	}

	/** A real level's own noise (a world made with the default preset: the client run's). */
	static World of(ServerLevel level) {
		NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
		return new World("level", level.getSeed(), generator, level.getChunkSource().randomState(), LevelHeightAccessor.create(level.getMinY(), level.getHeight()),
				Terrains.noise(level), level.getChunkSource().getGeneratorState());
	}

	/** The default overworld's noise and height (-64 to 319). */
	static World normal(ServerLevel level, long seed) {
		return of(level, "normal", NoiseGeneratorSettings.OVERWORLD, seed, -64, 384);
	}

	/** Every kind a structure JSON names: the kind's id, its structure. */
	static Map<String, Holder<Structure>> structures(ServerLevel level) {
		Map<String, Holder<Structure>> out = new LinkedHashMap<>();
		for (Reference<Structure> holder : level.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().toList()) {
			if (holder.value() instanceof Sc2Structure sc2 && !sc2.marker() && Sc2Kind.get(sc2.kind()) != null) {
				out.put(sc2.kind(), holder);
			}
		}
		return out;
	}

	/** The placement of the structure set that holds this structure, or null. */
	static @Nullable StructurePlacement placement(ServerLevel level, Holder<Structure> structure) {
		for (Holder<StructureSet> set : level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElements().toList()) {
			for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
				if (entry.structure().equals(structure)) {
					return set.value().placement();
				}
			}
		}
		return null;
	}

	/**
	 * The chunks the placement picks for this structure within {@code radius} blocks of ({@code x}, {@code z}), nearest
	 * first, at most {@code max} (a random spread's one chunk a region; the rays' slots).
	 */
	static List<ChunkPos> candidates(World world, StructurePlacement placement, int x, int z, int radius, int max) {
		List<ChunkPos> out = new ArrayList<>();
		if (placement instanceof RandomSpreadStructurePlacement spread) {
			int spacing = spread.spacing();
			int cx0 = (x - radius) >> 4;
			int cz0 = (z - radius) >> 4;
			int cx1 = (x + radius) >> 4;
			int cz1 = (z + radius) >> 4;
			for (int rx = Math.floorDiv(cx0, spacing); rx <= Math.floorDiv(cx1, spacing); rx++) {
				for (int rz = Math.floorDiv(cz0, spacing); rz <= Math.floorDiv(cz1, spacing); rz++) {
					ChunkPos chunk = spread.getPotentialStructureChunk(world.seed(), rx * spacing, rz * spacing);
					if (chunk.x() >= cx0 && chunk.x() <= cx1 && chunk.z() >= cz0 && chunk.z() <= cz1
							&& placement.isStructureChunk(world.structures(), chunk.x(), chunk.z())) {
						out.add(chunk);
					}
				}
			}
		} else if (placement instanceof RaysPlacement rays) {
			for (RaysPlacement.Slot slot : rays.slots(world.seed(), rays.slots())) {
				ChunkPos chunk = slot.chunk();
				if (Math.abs(chunk.getMiddleBlockX() - x) <= radius && Math.abs(chunk.getMiddleBlockZ() - z) <= radius
						&& placement.isStructureChunk(world.structures(), chunk.x(), chunk.z())) {
					out.add(chunk);
				}
			}
		}
		out.sort((a, b) -> Long.compare(dist2(a, x, z), dist2(b, x, z)));
		return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
	}

	private static long dist2(ChunkPos chunk, int x, int z) {
		long dx = chunk.getMiddleBlockX() - x;
		long dz = chunk.getMiddleBlockZ() - z;
		return dx * dx + dz * dz;
	}

	/** A snapshot that lets every kind generate (the switch on, Traces, the spawn at the origin, each kind's config). */
	static Snapshot snapshot(long seed) {
		Map<String, Object> configs = new HashMap<>();
		for (Sc2Kind kind : Sc2Kind.all()) {
			configs.put(kind.id(), kind.gates());
		}
		return new Snapshot(true, Stage.TRACES, BlockPos.ZERO, seed, 7L, configs);
	}

	/**
	 * Worldgen's own path for this structure in this chunk of this world (the kit's gates with {@code snapshot}, the kind's
	 * siting, the kit's height and reach, vanilla's biome check), as {@code ChunkGenerator.createStructures} asks it; null if
	 * no start. Installs {@code snapshot} for the call (server thread, within one tick).
	 */
	static @Nullable StructureStart generate(ServerLevel level, World world, Holder<Structure> structure, ChunkPos chunk, Snapshot snapshot) {
		Snapshot.install(snapshot);
		StructureStart start = structure.value().generate(structure, Level.OVERWORLD, level.registryAccess(), world.generator(),
				world.generator().getBiomeSource(), world.random().createClimateSampler(SamplerContext.builder().enableCaches().build()), world.random(), level.getServer().getStructureTemplateManager(), world.seed(), chunk,
				0, world.height(), structure.value().biomes()::contains);
		return start.isValid() ? start : null;
	}

	/** The shape of a start the kit made. */
	static Shape shape(StructureStart start, Terrain terrain) {
		Sc2Piece piece = (Sc2Piece) start.getPieces().getFirst();
		return piece.shape(terrain);
	}

	/** A terrain that counts what it is asked (the cost in samples). */
	static final class Counted implements Terrain {
		private final Terrain inner;
		int ground;
		int surface;
		int blocks;
		int biomes;

		Counted(Terrain inner) {
			this.inner = inner;
		}

		int samples() {
			return ground + surface + blocks + biomes;
		}

		@Override
		public int seaLevel() {
			return inner.seaLevel();
		}

		@Override
		public int minY() {
			return inner.minY();
		}

		@Override
		public int ground(int x, int z) {
			ground++;
			return inner.ground(x, z);
		}

		@Override
		public int surface(int x, int z) {
			surface++;
			return inner.surface(x, z);
		}

		@Override
		public Holder<net.minecraft.world.level.biome.Biome> biome(int x, int y, int z) {
			biomes++;
			return inner.biome(x, y, z);
		}

		@Override
		public BlockState block(int x, int y, int z) {
			blocks++;
			return inner.block(x, y, z);
		}
	}

	/** Prints a line of a survey (the test's own output, as the other cost tests print theirs). */
	static void log(String line) {
		System.out.println("[a1016] sc2h " + line);
	}

	/** True if this structure would take this start's biome as vanilla checks it (at the ground of the origin's column). */
	static boolean biomeFits(World world, Holder<Structure> structure, BlockPos origin) {
		int ground = world.terrain().ground(origin.getX(), origin.getZ());
		return structure.value().biomes().contains(world.terrain().biome(origin.getX(), ground, origin.getZ()));
	}

	static Optional<ChunkPos> nearest(List<ChunkPos> chunks) {
		return chunks.isEmpty() ? Optional.empty() : Optional.of(chunks.getFirst());
	}
}
