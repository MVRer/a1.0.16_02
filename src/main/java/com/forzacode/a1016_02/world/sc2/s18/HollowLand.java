package com.forzacode.a1016_02.world.sc2.s18;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * The land's top on a grid, read from the noise a tile of points at a time (R3-3: the hollow's siting read about 2 500
 * points at 150 us each, one noise column and its aquifer each). The generator reads a column's top as the first block
 * from the top whose final density is over 0 (the aquifer only picks what fills the rest); here one strided volume of the
 * final density over a tile of grid points gives the same answer for all of them at once, from {@code yLow} up (a top
 * under it reads {@code yLow - 1}). Over any other terrain (the gallery's, tests', the live level's) each point is read
 * as {@link Terrain#ground} and clamped the same way, so a shape is the same whichever reads it. Any thread.
 */
public final class HollowLand {
	/** Grid points a tile side (a volume each). */
	static final int TILE = 16;
	/** Tests only (the cost test's A/B): false reads every point as a column. */
	public static volatile boolean volumes = true;

	private HollowLand() {
	}

	/**
	 * The land's top at the {@code nx} x {@code nz} points {@code step} apart from ({@code x0}, {@code z0}) for which
	 * {@code want} is true (index {@code i * nz + j}, {@code i} along x), each at least {@code yLow - 1}; the others
	 * {@code yLow - 1}. {@code yLow} is raised to a block over the sea at least: under the sea the aquifer's barriers stand
	 * solid where the density is not (the volume would not see them).
	 */
	public static int[] tops(Terrain terrain, int x0, int z0, int step, int nx, int nz, int yLow, boolean[] want) {
		return read(terrain, x0, z0, step, nx, nz, Math.max(yLow, terrain.seaLevel() + 1), want);
	}

	/** {@link #tops} once {@code yLow} is clamped over the sea. */
	private static int[] read(Terrain terrain, int x0, int z0, int step, int nx, int nz, int yLow, boolean[] want) {
		int[] out = new int[nx * nz];
		java.util.Arrays.fill(out, yLow - 1);
		Terrains.NoiseLand land = volumes ? Terrains.noiseLand(terrain) : null;
		if (land == null) {
			for (int i = 0; i < nx; i++) {
				for (int j = 0; j < nz; j++) {
					if (want[i * nz + j]) {
						out[i * nz + j] = Math.max(yLow - 1, terrain.ground(x0 + i * step, z0 + j * step));
					}
				}
			}
			return out;
		}
		NoiseGeneratorSettings settings = land.generator().generatorSettings().value();
		int cell = 8;
		int top = land.minY() + land.height() - 1;
		// A cell under the lowest height asked: the volume's own lowest layer may read otherwise than the column's.
		int y0 = Math.max(land.minY(), land.minY() + Math.floorDiv(Math.max(yLow, land.minY()) - land.minY(), cell) * cell - cell);
		if (y0 > top) {
			return out;
		}
		int sy = top - y0 + 1;
		Aquifer.FluidPicker fluids = fluids(settings);
		for (int ti = 0; ti < nx; ti += TILE) {
			for (int tj = 0; tj < nz; tj += TILE) {
				int sx = Math.min(TILE, nx - ti);
				int sz = Math.min(TILE, nz - tj);
				boolean any = false;
				for (int i = ti; i < ti + sx && !any; i++) {
					for (int j = tj; j < tj + sz && !any; j++) {
						any = want[i * nz + j];
					}
				}
				if (!any) {
					continue;
				}
				DensityVolume volume = new DensityVolume(sx, sy, sz, x0 + ti * step, y0, z0 + tj * step, step, 1, step);
				try (NoiseChunk chunk = new NoiseChunk(land.random(), null, settings, fluids, Blender.empty(), volume)) {
					DensitySampler.Bound density = chunk.cachingSamplers().get(settings.noiseRouter().finalDensity());
					try (ScopedDensityBuffer buffer = density.sampleVolume(volume)) {
						for (int i = 0; i < sx; i++) {
							for (int j = 0; j < sz; j++) {
								if (!want[(ti + i) * nz + tj + j]) {
									continue;
								}
								for (int y = sy - 1; y >= 0; y--) {
									if (buffer.get(volume.indexUnchecked(i, y, j)) > 0.0F) {
										out[(ti + i) * nz + tj + j] = Math.max(yLow - 1, y0 + y);
										break;
									}
								}
							}
						}
					}
				}
			}
		}
		return out;
	}

	/** The fluid the generator puts where the density is not solid (never read here: the aquifer is not asked). */
	private static Aquifer.FluidPicker fluids(NoiseGeneratorSettings settings) {
		Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
		int seaLevel = settings.seaLevel();
		Aquifer.FluidStatus sea = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
		return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : sea;
	}
}
