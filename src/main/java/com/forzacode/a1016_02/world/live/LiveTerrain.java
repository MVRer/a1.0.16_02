package com.forzacode.a1016_02.world.live;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.gen.Vegetation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@link Terrain} over the blocks of a running level, for live scars. Columns in chunks that are not loaded read
 * as the bottom of the world (never "ground"), so plans stay inside loaded land. Server thread only.
 */
public final class LiveTerrain implements Terrain {
	private final ServerLevel level;

	public LiveTerrain(ServerLevel level) {
		this.level = level;
	}

	public boolean loaded(int x, int z) {
		return level.hasChunk(x >> 4, z >> 4);
	}

	@Override
	public int seaLevel() {
		return level.getSeaLevel();
	}

	@Override
	public int minY() {
		return level.getMinY();
	}

	@Override
	public int ground(int x, int z) {
		if (!loaded(x, z)) {
			return level.getMinY() - 1;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
		for (int y = top; y >= level.getMinY(); y--) {
			pos.setY(y);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || Vegetation.isSnowLayer(state) || Vegetation.dies(state) || !state.getFluidState().isEmpty() && !state.isSolid()) {
				continue;
			}
			return y;
		}
		return level.getMinY() - 1;
	}

	@Override
	public int surface(int x, int z) {
		if (!loaded(x, z)) {
			return level.getMinY() - 1;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
		for (int y = top; y >= level.getMinY(); y--) {
			pos.setY(y);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || Vegetation.isSnowLayer(state) || Vegetation.dies(state) && state.getFluidState().isEmpty()) {
				continue;
			}
			return y;
		}
		return level.getMinY() - 1;
	}

	@Override
	public Holder<Biome> biome(int x, int y, int z) {
		return level.getBiome(new BlockPos(x, y, z));
	}

	@Override
	public BlockState block(int x, int y, int z) {
		return level.getBlockState(new BlockPos(x, y, z));
	}
}
