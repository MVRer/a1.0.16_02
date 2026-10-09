package com.forzacode.a1016_02.world.gen;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Terrain straight from the noise: the same answer on every thread and for every chunk, generated or not.
 * Trees, carvers and features are not in it; caves and aquifers are. Thread-safe.
 */
public final class NoiseTerrain implements Terrain {
	private record CachedColumn(int x, int z, NoiseColumn column) {
	}

	private final ServerLevel level;
	private final ChunkGenerator generator;
	private final RandomState randomState;
	private final ThreadLocal<BiomeResolver> resolver;
	private final ThreadLocal<CachedColumn> lastColumn = new ThreadLocal<>();

	public NoiseTerrain(ServerLevel level) {
		this.level = level;
		this.generator = level.getChunkSource().getGenerator();
		this.randomState = level.getChunkSource().randomState();
		this.resolver = ThreadLocal.withInitial(() -> generator.getBiomeSource().createUncachedResolver(randomState));
	}

	@Override
	public int seaLevel() {
		return generator.getSeaLevel();
	}

	@Override
	public int minY() {
		return level.getMinY();
	}

	@Override
	public int ground(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState) - 1;
	}

	@Override
	public int surface(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) - 1;
	}

	@Override
	public Holder<Biome> biome(int x, int y, int z) {
		return resolver.get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
	}

	@Override
	public BlockState block(int x, int y, int z) {
		CachedColumn cached = lastColumn.get();
		if (cached == null || cached.x() != x || cached.z() != z) {
			cached = new CachedColumn(x, z, generator.getBaseColumn(x, z, level, randomState));
			lastColumn.set(cached);
		}
		if (y < level.getMinY() || y > level.getMaxY()) {
			return Blocks.AIR.defaultBlockState();
		}
		return cached.column().getBlock(y);
	}

	/** The spawn chunk the server starts its spawn search from (D-009 measures from the real spawn too). */
	public net.minecraft.world.level.ChunkPos spawnOrigin() {
		return generator.getOrigin(randomState);
	}
}
