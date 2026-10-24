package com.forzacode.a1016_02.world.sc2.kit;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * The noise's blocks over a whole box at once, as the generator's own {@code getBaseColumn} gives them column by column
 * (the final density and the aquifer: stone, water, lava, air; no surface, carvers or features), at a fraction of the
 * cost: one volume per chunk instead of one per column. For tests and the dev survey ({@link #base}: an
 * {@link Overlay.Base} over the real noise).
 */
public final class NoiseVolume {
	private NoiseVolume() {
	}

	/** The fluid the generator puts where the density is not solid: lava under -54 (or the sea, if lower), else its sea. */
	static Aquifer.FluidPicker fluids(NoiseGeneratorSettings settings) {
		Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
		int seaLevel = settings.seaLevel();
		Aquifer.FluidStatus sea = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
		return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : sea;
	}

	/**
	 * The blocks of the box from ({@code x0}, {@code y0}, {@code z0}), {@code sx} by {@code sy} by {@code sz}, indexed
	 * {@code y + (x + z * sx) * sy} from its corner.
	 */
	public static BlockState[] blocks(NoiseBasedChunkGenerator generator, RandomState random, int x0, int y0, int z0, int sx, int sy, int sz) {
		NoiseGeneratorSettings settings = generator.generatorSettings().value();
		DensityVolume volume = new DensityVolume(sx, sy, sz, x0, y0, z0);
		BlockState[] out = new BlockState[volume.size()];
		BlockState stone = settings.defaultBlock();
		try (NoiseChunk chunk = new NoiseChunk(random, null, settings, fluids(settings), Blender.empty(), volume)) {
			Aquifer aquifer = chunk.aquifer();
			DensitySampler.Bound density = chunk.cachingSamplers().get(settings.noiseRouter().finalDensity());
			try (ScopedDensityBuffer buffer = density.sampleVolume(volume)) {
				for (int z = 0; z < sz; z++) {
					for (int x = 0; x < sx; x++) {
						// From the top down, as the generator reads a column (the aquifer may keep state down a column).
						for (int y = sy - 1; y >= 0; y--) {
							int index = volume.indexUnchecked(x, y, z);
							BlockState state = aquifer.computeSubstance(x0 + x, y0 + y, z0 + z, buffer.get(index));
							out[index] = state == null ? stone : state;
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * The noise's blocks from {@code minY} to {@code maxY} as an overlay's base, one chunk's volume made the first time a
	 * cell of it is read (air above, the generator's default block below the range). One thread.
	 */
	public static Overlay.Base base(NoiseBasedChunkGenerator generator, RandomState random, int minY, int maxY) {
		int sy = maxY - minY + 1;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState stone = generator.generatorSettings().value().defaultBlock();
		Long2ObjectOpenHashMap<BlockState[]> chunks = new Long2ObjectOpenHashMap<>();
		return (x, y, z) -> {
			if (y > maxY) {
				return air;
			}
			if (y < minY) {
				return stone;
			}
			int cx = x >> 4;
			int cz = z >> 4;
			long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
			BlockState[] blocks = chunks.get(key);
			if (blocks == null) {
				blocks = blocks(generator, random, cx << 4, minY, cz << 4, 16, sy, 16);
				chunks.put(key, blocks);
			}
			return blocks[(y - minY) + ((x & 15) + (z & 15) * 16) * sy];
		};
	}
}
