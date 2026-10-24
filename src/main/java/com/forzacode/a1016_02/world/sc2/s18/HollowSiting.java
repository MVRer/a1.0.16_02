package com.forzacode.a1016_02.world.sc2.s18;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import org.jspecify.annotations.Nullable;

/**
 * Where one stands in a candidate chunk (docs/SELF_CREATED_2.md, 18, "Rules (structure)"), after the kit's gates (the
 * switch, the stage, far enough from the origin and the spawn): the land at the chunk's middle in the kind's biomes
 * ({@code #a1016_02:world/s18}, checked here first so nothing else is sampled elsewhere, as vanilla checks it after);
 * the ground on a 9 x 9 grid 16 apart round the middle (81 samples, the centre's shared with the biome's); the floor
 * {@code Yf} two over the lowest of the nine samples on the side facing the origin (where the way in comes out), at
 * least a block over the sea; at least 40 % of the samples {@code c} or more over the floor. Then its shape is made once
 * (it needs the terrain all round) and must hold a square and a way in, and its box must keep clear of the world's
 * bottom and top ({@link #withinHeight}: refused, never clipped); the factory takes that same shape ({@link #create})
 * instead of making it again.
 */
public final class HollowSiting implements Siting {
	public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, A1016_02.id("world/s18"));
	/** The samples' half extent and count per side. */
	public static final int HALF = 64;
	public static final int PER_SIDE = 9;
	/** The share of samples that must rise {@code c} over the floor. */
	public static final double RISE_SHARE = 0.4;

	public static final HollowSiting INSTANCE = new HollowSiting();

	/** The shape siting made last on this thread, for the factory that asks for it next ({@link #create}). */
	private static final ThreadLocal<@Nullable HollowShape> MADE = new ThreadLocal<>();

	/** A start's shape over one land (R12-5): the same start over the same noise. */
	private record Key(Start start, Object land) {
	}

	/** How many recent shapes are kept. */
	static final int KEPT = 8;

	/**
	 * The shapes made last over the noise (R12-5: a start's shape costs 0.3 s or more on worldgen's one queue), so a start
	 * whose chunk is saved and loaded again in the same run (its piece made anew from its NBT) is not built again; bounded,
	 * any thread, cleared at stop. Never for the gallery's or tests' own terrains.
	 */
	private static final Map<Key, HollowShape> RECENT = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, HollowShape> eldest) {
			return size() > KEPT;
		}
	};

	private HollowSiting() {
	}

	@Override
	public Optional<Start> find(Query query) {
		Sc18Config config = query.snapshot().config(S18.ID, Sc18Config.class);
		if (config == null) {
			return Optional.empty();
		}
		Terrain terrain = query.terrain();
		int cx = query.chunk().getMiddleBlockX();
		int cz = query.chunk().getMiddleBlockZ();
		int middle = terrain.ground(cx, cz);
		Holder<Biome> biome = terrain.biome(cx, middle, cz);
		if (!biome.is(BIOMES)) {
			return Optional.empty();
		}
		Direction facing = Siting.facingToOrigin(cx, cz);
		int[] grid = grid(terrain, cx, cz, middle);
		int floor = floor(grid, facing, terrain.seaLevel());
		if (rise(grid, floor, config.minRise) < RISE_SHARE) {
			return Optional.empty();
		}
		long seed = query.seed(S18.ID);
		Start start = new Start(new BlockPos(cx, floor, cz), facing, variant(config, seed, spruce(biome)), seed);
		HollowShape shape = new HollowShape(start, terrain);
		if (!shape.valid() || !withinHeight(shape.box(), query.minY(), query.maxY())) {
			return Optional.empty();
		}
		MADE.set(shape);
		keep(start, terrain, shape);
		return Optional.of(start);
	}

	/**
	 * Kept clear of the world's bottom (its bedrock) and its top: a start whose box comes nearer is not made (never
	 * clipped). The kit refuses every kind's box within its own margins too ({@code Heights}, 6 and 6); this one keeps 8
	 * under the top.
	 */
	public static final int BOTTOM_MARGIN = 6;
	public static final int TOP_MARGIN = 8;

	/**
	 * True if the box stays at least {@value #BOTTOM_MARGIN} over the world's lowest block and {@value #TOP_MARGIN} under its
	 * highest (the level's own numbers: a data pack may change the overworld's height).
	 */
	public static boolean withinHeight(net.minecraft.world.level.levelgen.structure.BoundingBox box, int minY, int maxY) {
		return box.minY() >= minY + BOTTOM_MARGIN && box.maxY() <= maxY - TOP_MARGIN;
	}

	/**
	 * The kind's factory: the shape siting just made for this start on this thread, else one made over the same noise
	 * lately (a start loaded again), else a new one.
	 */
	public static Shape create(Start start, Terrain terrain) {
		HollowShape made = MADE.get();
		MADE.remove();
		if (made != null && sameStart(made.start(), start)) {
			return made;
		}
		Object land = Terrains.identity(terrain);
		if (land != null) {
			synchronized (RECENT) {
				HollowShape kept = RECENT.get(new Key(tied(start), land));
				if (kept != null) {
					return kept;
				}
			}
		}
		HollowShape shape = new HollowShape(start, terrain);
		keep(start, terrain, shape);
		return shape;
	}

	/** The start as the kind ties it (its version), so siting's and a saved piece's are one key. */
	private static Start tied(Start start) {
		return start.withVersion(start.versionOr(HollowShape.VERSION));
	}

	private static boolean sameStart(Start made, Start asked) {
		return tied(made).equals(tied(asked));
	}

	private static void keep(Start start, Terrain terrain, HollowShape shape) {
		Object land = Terrains.identity(terrain);
		if (land != null) {
			synchronized (RECENT) {
				RECENT.put(new Key(tied(start), land), shape);
			}
		}
	}

	/** Server stop: nothing is kept. */
	public static void clear() {
		synchronized (RECENT) {
			RECENT.clear();
		}
	}

	/** The variant from the config's numbers and the seed: the high opening by its chance, the stems' count, the wood. */
	public static int variant(Sc18Config config, long seed, boolean spruce) {
		boolean window = Draw.unit(seed, 18, 1, 9) < config.highWindowChance;
		int lo = Math.max(0, Math.min(config.trunksMin, config.trunksMax));
		int hi = Math.max(lo, config.trunksMax);
		int stems = Draw.between(seed, 0x18A7L, lo, hi);
		return HollowShape.variant(window, spruce, config.shellTop, config.shellSide, config.pillarEvery, stems);
	}

	/** Spruce stems where the land is cold or high (peaks, slopes, groves, the windswept hills), oak elsewhere. */
	public static boolean spruce(Holder<Biome> biome) {
		return biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.GROVE)
				|| biome.is(Biomes.WINDSWEPT_HILLS) || biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS) || biome.is(Biomes.WINDSWEPT_FOREST);
	}

	/** The ground on the 9 x 9 grid, row by row ({@code i} along x), the middle given (80 more samples). */
	public static int[] grid(Terrain terrain, int cx, int cz, int middle) {
		int[] out = new int[PER_SIDE * PER_SIDE];
		int step = 2 * HALF / (PER_SIDE - 1);
		int mid = PER_SIDE / 2;
		for (int i = 0; i < PER_SIDE; i++) {
			for (int j = 0; j < PER_SIDE; j++) {
				out[i * PER_SIDE + j] = i == mid && j == mid ? middle : terrain.ground(cx - HALF + i * step, cz - HALF + j * step);
			}
		}
		return out;
	}

	/** Two over the lowest of the nine samples on the side the way in faces, and at least a block over the sea. */
	public static int floor(int[] grid, Direction facing, int seaLevel) {
		int last = PER_SIDE - 1;
		int lowest = Integer.MAX_VALUE;
		for (int k = 0; k < PER_SIDE; k++) {
			int i;
			int j;
			switch (facing) {
				case EAST -> {
					i = last;
					j = k;
				}
				case WEST -> {
					i = 0;
					j = k;
				}
				case SOUTH -> {
					i = k;
					j = last;
				}
				default -> {
					i = k;
					j = 0;
				}
			}
			lowest = Math.min(lowest, grid[i * PER_SIDE + j]);
		}
		return Math.max(lowest + 2, seaLevel + 1);
	}

	/** The share of the samples at least {@code rise} over the floor. */
	public static double rise(int[] grid, int floor, int rise) {
		int high = 0;
		for (int g : grid) {
			if (g >= floor + rise) {
				high++;
			}
		}
		return grid.length == 0 ? 0 : (double) high / grid.length;
	}
}
