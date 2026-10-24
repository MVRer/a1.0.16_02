package com.forzacode.a1016_02.world.sc2.s16;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Heights;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where a pyramid stands in a slot's chunk (docs/SELF_CREATED_2.md, 16, "Rules (structure)"), after the kit's gates
 * (the switch, the stage, far enough from the origin and the spawn) and before vanilla's biome check at the centre's
 * ground: the size by the weights, falling back to the next smaller one that fits (then the larger ones); on land the
 * ground under the footprint (9 samples) varies at most {@code c} and is mostly dry; at sea ({@link #SEA}) the floor
 * varies at most {@code c} too and the apex stands at least 12 over the water. The base's centre is the chunk's middle
 * column at the median sample, the way in faces the origin (D-095). A size whose pit would come within the kit's margin
 * of the level's bottom, or whose top within its margin of the level's top ({@link Heights}), is passed over for the next
 * (SC2-H: a short world gets a smaller one, never a clipped one). Nine ground samples a size, then (only if they
 * fit) nine surface samples on land or one at sea; for the size taken 16 more ground samples (the ground between the
 * nine never under where the foundation ends, or no start: a valley or a trench under it would leave the foundation
 * hanging). Land that fits no size costs 27 samples; at most 70 in all (D-104: at most 81).
 */
public final class PyramidSiting implements Siting {
	/** The apex stands at least this far over the water at sea. */
	public static final int OVER_THE_WATER = 12;
	/** On land, at most this many of the 9 samples may be under water (1 before the review, R10-3: a shore or a pond takes one; the seal and the foundation keep it dry). */
	public static final int WET_MAX = 3;

	public static final PyramidSiting LAND = new PyramidSiting(false);
	public static final PyramidSiting SEA = new PyramidSiting(true);

	private final boolean sea;

	private PyramidSiting(boolean sea) {
		this.sea = sea;
	}

	@Override
	public Optional<Start> find(Query query) {
		String id = sea ? S16.SEA_ID : S16.ID;
		Sc16Config config = query.snapshot().config(id, Sc16Config.class);
		if (config == null) {
			return Optional.empty();
		}
		int cx = query.chunk().getMiddleBlockX();
		int cz = query.chunk().getMiddleBlockZ();
		long seed = query.seed(id);
		for (int size : order(config, seed)) {
			int y0 = fits(query.terrain(), cx, cz, size, config.maxGroundSpread, sea);
			if (y0 == Integer.MIN_VALUE || !Heights.within(new BoundingBox(cx, PyramidShape.bottomY(y0, size, sea), cz, cx, PyramidShape.topY(y0, size), cz),
					query.minY(), query.maxY())) {
				// Too deep a pit or too high an apex for this level's height: the next size, never a clipped one (SC2-H).
				continue;
			}
			if (!underFootprint(query.terrain(), cx, cz, PyramidShape.halfWidth(size), y0 - 1 - PyramidShape.FOUNDATION_MAX)) {
				// A valley or a trench between the nine, deeper than the foundation reaches: none here (SC2-H).
				return Optional.empty();
			}
			boolean sealed = sea || Draw.unit(seed, 16, 1, 5) < config.sealedChance;
			boolean light = Draw.unit(seed, 16, 2, 6) < (sea ? 0.5 : config.apexLightChance);
			int variant = PyramidShape.variant(size, sealed, light, sea);
			return Optional.of(new Start(new BlockPos(cx, y0, cz), Siting.facingToOrigin(cx, cz), variant, seed));
		}
		return Optional.empty();
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
		for (int a = -2; a <= 2; a++) {
			for (int b = -2; b <= 2; b++) {
				if ((a & 1) == 0 && (b & 1) == 0) {
					continue;
				}
				if (terrain.ground(cx + a * half / 2, cz + b * half / 2) < lowest) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The height of the lowest course if a pyramid of this size fits here (the median of the ground samples plus one),
	 * else {@link Integer#MIN_VALUE}: the samples' spread at most {@code maxSpread}; on land at most {@link #WET_MAX}
	 * samples under water; at sea the centre under water and the apex {@link #OVER_THE_WATER} over it.
	 */
	public static int fits(Terrain terrain, int cx, int cz, int size, int maxSpread, boolean sea) {
		int half = PyramidShape.halfWidth(size);
		int[] ground = new int[9];
		int i = 0;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				ground[i++] = terrain.ground(cx + a * half, cz + b * half);
			}
		}
		// The cheapest refusals first (SC2-H: most candidates fail the spread, so they need no surface samples).
		if (Siting.spread(ground) > maxSpread) {
			return Integer.MIN_VALUE;
		}
		int median = Siting.median(ground);
		if (sea) {
			int water = terrain.surface(cx, cz);
			if (water <= ground[4] || median + half < water + OVER_THE_WATER) {
				return Integer.MIN_VALUE;
			}
		} else {
			int wet = 0;
			i = 0;
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					if (terrain.surface(cx + a * half, cz + b * half) > ground[i++] && ++wet > WET_MAX) {
						return Integer.MIN_VALUE;
					}
				}
			}
		}
		return median + 1;
	}

}
