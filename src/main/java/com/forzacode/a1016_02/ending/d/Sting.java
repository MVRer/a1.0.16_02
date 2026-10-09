package com.forzacode.a1016_02.ending.d;

import com.forzacode.a1016_02.core.HerobrineState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sting: when the pass ends, the half that takes things out ends with it. Once {@code ending:d_complete} is set,
 * chunks generated from then on have no caves, no ravines, nothing carved: the carvers are skipped, and in the
 * overworld the noise caves are filled before the surface is built (every open cell under the column's top solid
 * block becomes the stone the world is made of, aquifers too). Chunks that already exist are never touched. The gate
 * is one volatile flag read by the worldgen threads ({@code ending.mixin.d.NoiseBasedChunkGeneratorMixin}).
 */
public final class Sting {
	private static volatile boolean carversOff;
	private static volatile boolean noiseCavesOff;

	private Sting() {
	}

	/** Reads the flag and the config (server start, and when D completes). Server thread. */
	public static void refresh(MinecraftServer server) {
		boolean complete = HerobrineState.get(server).hasFlag(EndingDInit.COMPLETE_FLAG);
		EndingDConfig cfg = EndingDConfig.get();
		set(complete && cfg.carversOff, complete && cfg.noiseCavesOff);
	}

	/** Tests and server stop. */
	public static void set(boolean carvers, boolean noiseCaves) {
		carversOff = carvers;
		noiseCavesOff = noiseCaves;
	}

	/** Worldgen threads: skip the carvers for this chunk. */
	public static boolean skipCarvers() {
		return carversOff;
	}

	/** Worldgen threads: fill this overworld chunk's noise caves. */
	public static boolean fillNoiseCaves() {
		return noiseCavesOff;
	}

	/**
	 * Fills the noise caves of a freshly filled chunk: in each column, every cell that is air or fluid below the top
	 * solid block (the {@code OCEAN_FLOOR_WG} height) becomes {@code stone}. The sea, lakes and the sky above the ground
	 * stay as they are. Returns how many cells were filled. Worldgen threads; touches only this chunk.
	 */
	public static int fill(ChunkAccess chunk, BlockState stone, int minY) {
		Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int filled = 0;
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int top = floor.getFirstAvailable(x, z) - 1;
				for (int y = top - 1; y >= minY; y--) {
					pos.set(baseX + x, y, baseZ + z);
					BlockState state = chunk.getBlockState(pos);
					if (state.isAir() || !state.getFluidState().isEmpty() && state.canBeReplaced()) {
						chunk.setBlockState(pos, stone);
						filled++;
					}
				}
			}
		}
		return filled;
	}
}
