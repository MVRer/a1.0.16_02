package com.forzacode.a1016_02.world.sc2.s16;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Heights;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where a pyramid stands in a slot (docs/SELF_CREATED_2.md, 16, "Rules (structure)"; R3-2), after the kit's gates (the
 * switch, far enough from the origin and the spawn): the size by the weights, falling back to the next smaller one that
 * fits (then the larger ones); for each size the centre at the slot chunk's middle column, else 16 or 32 off it
 * ({@link #OFFSETS}, nearest first, as far as the size's box stays within the kit's reach); on land the ground under the
 * footprint (9 samples; the 191: 7 x 7) varies at most {@code c} (the 63 and the 95), {@code q} (the 127) or two thirds
 * more (the 191) and is mostly dry; at sea ({@link #SEA}) the floor varies at most {@code c} and the apex stands at least
 * 12 over the water; the centre's biome the kind's own (vanilla's check at the stub, asked here as well so another spot
 * can be tried: a river or a dark wood under the middle no longer loses the slot). The way in faces the origin (D-095).
 * A size whose pit would come within the kit's margin of the level's bottom, or whose top within its margin of the
 * level's top ({@link Heights}), is passed over (SC2-H: a short world gets a smaller one, never a clipped one); so is a
 * spot whose ground on a 7 x 7 under it falls under where the foundation ends, or (on land) whose land round its foot,
 * 10 and 24 out, falls under where the plinth ends (a valley or a trench would leave either hanging: SC2-H's audit).
 * Every column is read once a siting ({@link Samples}); a slot stops after {@value #READS_MAX} reads, once a world (the
 * kit's {@code Decided}).
 *
 * <p>Round 3 (R3-2): the 191, the offsets, the larger sizes' spread and the biome asked here raised the land 16 within 800
 * of the spawn from 65 % of the worlds (R3-0's find test) to 90 %.
 */
public final class PyramidSiting implements Siting {
	/** The apex stands at least this far over the water at sea. */
	public static final int OVER_THE_WATER = 12;
	/** On land, at most this many of the 9 samples may be under water (1 before the review, R10-3: a shore or a pond takes one; the seal and the foundation keep it dry). */
	public static final int WET_MAX = 3;
	/** The 191's ground samples a side (7 x 7, about 32 apart: SC2-H's foundation rule over a base that wide). */
	public static final int BIG_SAMPLES = 7;
	/** Where the centre may stand, from the slot chunk's middle (blocks), nearest first (R3-2). */
	static final int[][] OFFSETS = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {16, 16}, {16, -16}, {-16, 16}, {-16, -16}, {32, 0}, {-32, 0}, {0, 32},
			{0, -32}};
	/** A slot's siting stops trying spots once it has read the land this many times (about 0.25 ms a read; R3-2's measure). */
	public static final int READS_MAX = 128;
	/** One size stops trying spots once it has read this many more (the next size gets its turn). */
	public static final int SIZE_READS = 40;
	/** The nearer ring round the foot the land is read on (blocks out of the base; the other is the plinth's reach). */
	public static final int FOOT_NEAR = 10;
	/** The sea form tries the middle and the four 16 off it (an ocean's floor is smooth: the land's other spots rarely help). */
	static final int SEA_OFFSETS = 5;
	/** The nine samples' order: the centre, the corners, the edges' middles (the likeliest to break the spread first). */
	private static final int[][] SAMPLE_ORDER = {{0, 0}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
	/** The land kind's biomes (its structure JSON's), asked at each spot tried. */
	public static final TagKey<Biome> LAND_BIOMES = TagKey.create(Registries.BIOME, A1016_02.id("world/s16"));
	/** The sea form's. */
	public static final TagKey<Biome> SEA_BIOMES = TagKey.create(Registries.BIOME, A1016_02.id("world/s16d"));

	public static final PyramidSiting LAND = new PyramidSiting(false);
	public static final PyramidSiting SEA = new PyramidSiting(true);

	private final boolean sea;

	private PyramidSiting(boolean sea) {
		this.sea = sea;
	}

	@Override
	public Optional<Start> find(Query query) {
		Sc16Config config = query.snapshot().config(sea ? S16.SEA_ID : S16.ID, Sc16Config.class);
		if (config == null) {
			return Optional.empty();
		}
		return find(query, config, new Samples(query.terrain()));
	}

	/** {@link #find} with this config, reading the land through {@code samples} (tests count the reads). */
	public Optional<Start> find(Query query, Sc16Config config, Samples samples) {
		int mx = query.chunk().getMiddleBlockX();
		int mz = query.chunk().getMiddleBlockZ();
		long seed = query.seed(sea ? S16.SEA_ID : S16.ID);
		if (samples.biome(mx, mz).is(SEA_BIOMES) != sea) {
			// The slot's middle is the other form's (the sea's or the land's): it takes the slot, this one never would.
			return Optional.empty();
		}
		for (int size : order(config, seed)) {
			int spread = sea ? config.maxGroundSpread : config.spread(size);
			int reach = PyramidShape.reach(size);
			int first = samples.reads();
			for (int o = 0; o < (sea ? SEA_OFFSETS : OFFSETS.length); o++) {
				if (samples.reads() > READS_MAX) {
					// Land too rough for any of them: what it has read says so (a bound on the slot's one siting).
					return Optional.empty();
				}
				if (samples.reads() - first > SIZE_READS) {
					// This size fits nowhere near: the next one.
					break;
				}
				int[] off = OFFSETS[o];
				int cx = mx + off[0];
				int cz = mz + off[1];
				if (!Sc2Structure.withinReach(new BoundingBox(cx - reach, 0, cz - reach, cx + reach, 0, cz + reach), query.chunk())) {
					continue;
				}
				int y0 = fits(samples, cx, cz, size, spread, sea);
				if (y0 == Integer.MIN_VALUE || !Heights.within(new BoundingBox(cx, PyramidShape.bottomY(y0, size, sea), cz, cx, PyramidShape.topY(y0, size), cz),
						query.minY(), query.maxY())) {
					// Too deep a pit or too high an apex for this level's height: the next, never a clipped one (SC2-H).
					continue;
				}
				int lowest = y0 - 1 - PyramidShape.FOUNDATION_MAX;
				int half = PyramidShape.halfWidth(size);
				// The cheapest first: the biome (one read), the land round the foot (16), then under it (the 7 x 7).
				if (!samples.biome(cx, cz).is(sea ? SEA_BIOMES : LAND_BIOMES)) {
					continue;
				}
				if (!sea && !aroundTheFoot(samples, cx, cz, half, lowest)) {
					// The land round its foot falls away deeper than its plinth reaches (R3-2's survey: a plinth left in the air).
					continue;
				}
				if (size < 3 && !onGrid(samples, cx, cz, half, lowest)) {
					// A valley or a trench under it, deeper than the foundation reaches (SC2-H; R3-2: a 7 x 7 for every size, as
					// the 191's in fits).
					continue;
				}
				boolean sealed = !sea && Draw.unit(seed, 16, 1, 5) < config.sealedChance;
				boolean light = Draw.unit(seed, 16, 2, 6) < (sea ? config.seaLightChance : config.apexLightChance);
				int variant = PyramidShape.variant(size, sealed, light, sea, config.stripDepth);
				return Optional.of(new Start(new BlockPos(cx, y0, cz), Siting.facingToOrigin(cx, cz), variant, seed));
			}
		}
		return Optional.empty();
	}

	/** Each column's ground and surface read once a siting (spots and sizes share their samples). */
	public static final class Samples {
		private final Terrain terrain;
		private final Map<Long, Integer> ground = new HashMap<>();
		private final Map<Long, Integer> surface = new HashMap<>();
		private int reads;

		public Samples(Terrain terrain) {
			this.terrain = terrain;
		}

		public int ground(int x, int z) {
			return ground.computeIfAbsent(ChunkPos.pack(x, z), k -> {
				reads++;
				return terrain.ground(x, z);
			});
		}

		public int surface(int x, int z) {
			return surface.computeIfAbsent(ChunkPos.pack(x, z), k -> {
				reads++;
				return terrain.surface(x, z);
			});
		}

		/** The biome at the column's ground (where vanilla's check at the stub looks). */
		public Holder<Biome> biome(int x, int z) {
			reads++;
			return terrain.biome(x, ground(x, z), z);
		}

		/** How many times the terrain was read. */
		public int reads() {
			return reads;
		}
	}

	/** The sizes to try: the one the weights pick, then each smaller, then each larger. */
	public static List<Integer> order(Sc16Config config, long seed) {
		double total = 0;
		for (int i = 0; i < PyramidShape.BASES.length; i++) {
			total += config.weight(i);
		}
		int pick = 0;
		if (total > 0) {
			double roll = Draw.unit(seed, 16, 0, 4) * total;
			for (int i = 0; i < PyramidShape.BASES.length; i++) {
				roll -= config.weight(i);
				if (roll < 0) {
					pick = i;
					break;
				}
				pick = i;
			}
		}
		List<Integer> out = new ArrayList<>();
		for (int i = pick; i >= 0; i--) {
			out.add(i);
		}
		for (int i = pick + 1; i < PyramidShape.BASES.length; i++) {
			out.add(i);
		}
		return out;
	}

	/**
	 * True if the ground under the footprint never falls under {@code lowest} (where its foundation ends) at the 16 points
	 * between the nine (a 5 x 5 grid; asked only once the nine fit): SC2-H, a valley or a sea trench between the nine left
	 * the foundation hanging over open air.
	 */
	static boolean underFootprint(Terrain terrain, int cx, int cz, int half, int lowest) {
		return underFootprint(new Samples(terrain), cx, cz, half, lowest);
	}

	/** True if the ground never falls under {@code lowest} on the 7 x 7 grid over the footprint (the 191's own check, R3-2). */
	static boolean onGrid(Samples samples, int cx, int cz, int half, int lowest) {
		int n = BIG_SAMPLES / 2;
		for (int a = -n; a <= n; a++) {
			for (int b = -n; b <= n; b++) {
				if (samples.ground(cx + a * half / n, cz + b * half / n) < lowest) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * True if the land round the foot never falls under {@code lowest} (where the plinth ends) at the eight compass points
	 * {@value #FOOT_NEAR} and {@link PyramidShape#PLINTH_MAX} out of the base (R3-2: with the larger spreads a plinth was
	 * left hanging over a valley beside the base; SC2-H's audit counts it).
	 */
	static boolean aroundTheFoot(Samples samples, int cx, int cz, int half, int lowest) {
		for (int out : new int[] {FOOT_NEAR, PyramidShape.PLINTH_MAX}) {
			int d = half + out;
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					if ((a != 0 || b != 0) && samples.ground(cx + a * d, cz + b * d) < lowest) {
						return false;
					}
				}
			}
		}
		return true;
	}

	static boolean underFootprint(Samples samples, int cx, int cz, int half, int lowest) {
		for (int a = -2; a <= 2; a++) {
			for (int b = -2; b <= 2; b++) {
				if ((a & 1) == 0 && (b & 1) == 0) {
					continue;
				}
				if (samples.ground(cx + a * half / 2, cz + b * half / 2) < lowest) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The height of the lowest course if a pyramid of this size fits here (the median of the ground samples plus one),
	 * else {@link Integer#MIN_VALUE}: the samples' spread at most {@code maxSpread}; on land at most {@link #WET_MAX}
	 * samples under water; at sea the centre under water and the apex {@link #OVER_THE_WATER} over it. The 191 (size 3):
	 * the nine first, then its 7 x 7, all within the spread and none under where the foundation ends.
	 */
	public static int fits(Terrain terrain, int cx, int cz, int size, int maxSpread, boolean sea) {
		return fits(new Samples(terrain), cx, cz, size, maxSpread, sea);
	}

	public static int fits(Samples samples, int cx, int cz, int size, int maxSpread, boolean sea) {
		int half = PyramidShape.halfWidth(size);
		int[] ground = new int[9];
		// The cheapest refusals first (SC2-H: most candidates fail the spread, so they need no surface samples): the
		// corners first, and out at the first sample that breaks the spread (R3-2: a slot tries many spots).
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int k = 0; k < 9; k++) {
			int a = SAMPLE_ORDER[k][0];
			int b = SAMPLE_ORDER[k][1];
			int g = samples.ground(cx + a * half, cz + b * half);
			ground[(a + 1) * 3 + b + 1] = g;
			lo = Math.min(lo, g);
			hi = Math.max(hi, g);
			if (hi - lo > maxSpread) {
				return Integer.MIN_VALUE;
			}
		}
		int i;
		int median = Siting.median(ground);
		if (size >= 3) {
			int n = BIG_SAMPLES / 2;
			for (int a = -n; a <= n; a++) {
				for (int b = -n; b <= n; b++) {
					int g = samples.ground(cx + a * half / n, cz + b * half / n);
					lo = Math.min(lo, g);
					hi = Math.max(hi, g);
					if (hi - lo > maxSpread || g < median - PyramidShape.FOUNDATION_MAX) {
						return Integer.MIN_VALUE;
					}
				}
			}
		}
		if (sea) {
			int water = samples.surface(cx, cz);
			if (water <= ground[4] || median + half < water + OVER_THE_WATER) {
				return Integer.MIN_VALUE;
			}
		} else {
			int wet = 0;
			i = 0;
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					if (samples.surface(cx + a * half, cz + b * half) > ground[i++] && ++wet > WET_MAX) {
						return Integer.MIN_VALUE;
					}
				}
			}
		}
		return median + 1;
	}
}
