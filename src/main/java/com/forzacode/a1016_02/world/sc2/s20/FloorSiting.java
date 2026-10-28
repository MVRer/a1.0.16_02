package com.forzacode.a1016_02.world.sc2.s20;

import java.util.Optional;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

/**
 * Where a square of feature 20 goes in a candidate chunk (docs/SELF_CREATED_2.md, 20), after the kit's gates (the
 * switch, the stage at least {@code a}, {@code b} from the origin and the spawn) and before vanilla's biome check at its
 * middle: whole chunks round the candidate's corner, in hilly country (the ground under it, 25 samples, varying from
 * lowest to highest by an eighth to a half of its side: 12 to 48 for the default 96), dry (at most {@value #WET_MAX} wet samples, never
 * its middle); the floor at the median of the samples, so it is cut into the land as much as it is built up; the
 * building's doorway toward the origin (D-095). Refused, never clipped, if its box (the shape's own rule over the same
 * samples) would come within {@value #MARGIN} of the level's lowest or highest block (the bedrock layers, the build
 * limit; read from the level). 25 ground samples, and 25 surface samples only where the relief fits (D-104: at most 81).
 *
 * <p>Round 3 (R3-3): the sizes 192 (49 samples, relief 24 to 64), 128 (the 192's own samples), the config's side and 64 tried
 * in turn, the dry middle first, each column read once a siting (at most about 95 ground samples where every size is
 * tried); the biome tag widened to the grassy hills.
 */
public final class FloorSiting implements Siting {
	/** The ground under the square varies at least its side over this (it is cut into hills, not laid on a plain) ... */
	public static final int RELIEF_MIN_PER = 8;
	/** ... and at most its side over this (the cliffs stay a few storeys, not a canyon). */
	public static final int RELIEF_MAX_PER = 2;
	/** At most this many of the 25 samples may be under water. */
	public static final int WET_MAX = 1;
	/** The box keeps this far inside the level's height at both ends. */
	public static final int MARGIN = 6;
	/** A square this much wider than the config's side is tried first (two chunks: 128 for the default 96). */
	public static final int WIDER = 32;

	public static final FloorSiting INSTANCE = new FloorSiting();

	/** What the samples say: the floor's height and the lowest and highest ground. */
	public record Fit(int floor, int lowest, int highest) {
		/** The lowest cell the shape writes (its footing under the bedrock course and the lowest ground). */
		public int bottom() {
			return Math.min(lowest, floor - 4) - FloorShape.BELOW;
		}

		/** The highest cell the shape writes (over the highest ground: the trees it takes). */
		public int top() {
			return Math.max(highest, floor) + FloorShape.ABOVE;
		}

		/** True if the box stays {@value #MARGIN} inside a level from {@code minY} to {@code maxY}. */
		public boolean fits(int minY, int maxY) {
			return bottom() >= minY + MARGIN && top() <= maxY - MARGIN;
		}
	}

	private FloorSiting() {
	}

	@Override
	public Optional<Start> find(Query query) {
		Sc20Config config = query.snapshot().config(S20.ID, Sc20Config.class);
		if (config == null) {
			return Optional.empty();
		}
		// The square's middle is the candidate chunk's lowest corner, so the square is whole chunks round it. Two chunks
		// wider first where the land allows (R11-5: 96 read small from 200 in hilly country), else the config's side.
		int cx = query.chunk().getMinBlockX();
		int cz = query.chunk().getMinBlockZ();
		// Every size shares the middle, which must be dry: at sea nothing more is read.
		Terrain terrain = new Memo(query.terrain());
		if (terrain.surface(cx, cz) > terrain.ground(cx, cz)) {
			return Optional.empty();
		}
		int side = 0;
		Fit fit = null;
		for (int tried : sides(config)) {
			Fit f = fit(terrain, cx, cz, tried, FloorShape.gridOf(FloorShape.VERSION, tried));
			if (f != null && f.fits(query.minY(), query.maxY())) {
				side = tried;
				fit = f;
				break;
			}
		}
		if (fit == null) {
			return Optional.empty();
		}
		Direction facing = Siting.facingToward(cx, cz, 0, 0);
		return Optional.of(new Start(new BlockPos(cx, fit.floor(), cz), facing, FloorShape.variant(side), query.seed(S20.ID)));
	}

	/**
	 * What a square of {@code side} whose middle is ({@code cx}, {@code cz}) would be here, or null if the land does not
	 * fit: the relief out of range, too wet, the middle wet. The samples are the shape's own grid.
	 */
	public static @Nullable Fit fit(Terrain terrain, int cx, int cz, int side) {
		return fit(terrain, cx, cz, side, FloorShape.GRID);
	}

	/** One siting's reads of the land, each column once (the sizes share some of their samples: the 128's are the 192's). */
	private static final class Memo implements Terrain {
		private final Terrain terrain;
		private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap grounds = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
		private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap surfaces = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

		Memo(Terrain terrain) {
			this.terrain = terrain;
		}

		@Override
		public int seaLevel() {
			return terrain.seaLevel();
		}

		@Override
		public int minY() {
			return terrain.minY();
		}

		@Override
		public int ground(int x, int z) {
			return grounds.computeIfAbsent(net.minecraft.world.level.ChunkPos.pack(x, z), k -> terrain.ground(x, z));
		}

		@Override
		public int surface(int x, int z) {
			return surfaces.computeIfAbsent(net.minecraft.world.level.ChunkPos.pack(x, z), k -> terrain.surface(x, z));
		}

		@Override
		public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome(int x, int y, int z) {
			return terrain.biome(x, y, z);
		}

		@Override
		public net.minecraft.world.level.block.state.BlockState block(int x, int y, int z) {
			return terrain.block(x, y, z);
		}
	}

	/**
	 * Version 3's sizes in the order tried (R3-3, D-115): the widest the config allows ({@code f}, 192), then 128, the
	 * config's side ({@code c}, 96), then 64; each once.
	 */
	public static int[] sides(Sc20Config config) {
		return java.util.stream.IntStream.of(config.sideMaxBlocks(), 128, config.sideBlocks(), 64).filter(s -> s <= config.sideMaxBlocks()).distinct().toArray();
	}

	/** The most the ground under a square of this side may vary: half its side, and 64 at most (a 64-high cut, R3-3). */
	public static int reliefMax(int side) {
		return Math.min(side / RELIEF_MAX_PER, RELIEF_CAP);
	}

	/** The ground under the widest squares varies at most this much (the cliffs stay a few storeys). */
	public static final int RELIEF_CAP = 64;

	/** {@link #fit(Terrain, int, int, int)} over {@code grid} samples a side (the shape's own, {@link FloorShape#gridOf}). */
	public static @Nullable Fit fit(Terrain terrain, int cx, int cz, int side, int grid) {
		int[] ground = new int[grid * grid];
		int i = 0;
		int minX = cx - side / 2;
		int minZ = cz - side / 2;
		for (int a = 0; a < grid; a++) {
			for (int b = 0; b < grid; b++) {
				ground[i++] = terrain.ground(minX + Math.round((side - 1) * a / (grid - 1.0F)),
						minZ + Math.round((side - 1) * b / (grid - 1.0F)));
			}
		}
		// The relief first (SC2-H: most candidates fail it, so they need no surface samples).
		int relief = Siting.spread(ground);
		if (relief < side / RELIEF_MIN_PER || relief > reliefMax(side)) {
			return null;
		}
		int wet = 0;
		i = 0;
		for (int a = 0; a < grid; a++) {
			for (int b = 0; b < grid; b++) {
				int x = minX + Math.round((side - 1) * a / (grid - 1.0F));
				int z = minZ + Math.round((side - 1) * b / (grid - 1.0F));
				if (terrain.surface(x, z) > ground[i]) {
					if (a == grid / 2 && b == grid / 2 || ++wet > WET_MAX * (grid * grid) / 25) {
						return null;
					}
				}
				i++;
			}
		}
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int g : ground) {
			lowest = Math.min(lowest, g);
			highest = Math.max(highest, g);
		}
		return new Fit(Siting.median(ground), lowest, highest);
	}
}
