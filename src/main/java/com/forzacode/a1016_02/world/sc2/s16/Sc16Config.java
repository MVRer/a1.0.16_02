package com.forzacode.a1016_02.world.sc2.s16;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc16} (docs/SELF_CREATED_2.md, 16): the numbers of the remains kind {@code s16} and its sea form
 * {@code s16d}, of 16b and of 16c. Version 1, keys opaque from the start (D-103: no old keys). The rays themselves (how
 * far apart, how many) live in the structure set's JSON. Read on the server thread; worldgen reads it through the kit's
 * snapshot. A later default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises
 * {@link #CURRENT}.
 */
public final class Sc16Config implements ModConfig.Versioned, Sc2Kind.Gates {
	public static final String SECTION = "sc16";
	public static final int CURRENT = 2;

	public int version = 0;
	/** The lowest stage whose new land may hold one (1, Traces). */
	@SerializedName("a")
	public int fromStage = 1;
	/** No start nearer than this to (0, 0) or to the world spawn (blocks). */
	@SerializedName("b")
	public int minOriginDistance = 560;
	/** On land, the ground under the footprint (9 samples) varies at most this much (blocks). */
	@SerializedName("c")
	public int maxGroundSpread = 16;
	/**
	 * How often each size is wanted (base 63, 95, 127); a size that does not fit falls back to the next smaller. Version 2:
	 * mostly the two big ones (R11-9: the 63 stood lower than the hills; the first versions' was about 127 by 64).
	 */
	@SerializedName("d")
	public double[] sizeWeights = {0.1, 0.45, 0.45};
	/** On land, the share with no way in. */
	@SerializedName("e")
	public double sealedChance = 0.15;
	/** The share with a light on top (the sea form: 0.5 in version 1, this share from version 2). */
	@SerializedName("f")
	public double apexLightChance = 0.6;
	/** 16b: inside this long first (seconds). */
	@SerializedName("g")
	public double closeAfterSeconds = 90;
	/** 16b: the player at least this far from the inner mouth (blocks). */
	@SerializedName("h")
	public int closeMinDistance = 20;
	/** 16b: at most this many times a place, ever. */
	@SerializedName("i")
	public int closePerPlaceMax = 3;
	/** 16c: the player at least this many steps down. */
	@SerializedName("j")
	public int darkStepsMin = 12;
	/** 16c: one light every this many to {@link #darkEveryMax} seconds. */
	@SerializedName("k")
	public double darkEveryMin = 8;
	@SerializedName("l")
	public double darkEveryMax = 12;
	/** 16c: at most this many lights a visit. */
	@SerializedName("m")
	public int darkPerVisit = 6;
	/** 16c: after this many, the glimpse may come. */
	@SerializedName("n")
	public int glimpseAfter = 4;

	public static Sc16Config get() {
		return ModConfig.section(SECTION, Sc16Config.class, Sc16Config::new);
	}

	@Override
	public int fromStage() {
		return fromStage;
	}

	@Override
	public int minOriginDistance() {
		return minOriginDistance;
	}

	/** The weight of size {@code index} (0 for a missing or negative one). */
	public double weight(int index) {
		return sizeWeights != null && index >= 0 && index < sizeWeights.length ? Math.max(0, sizeWeights[index]) : 0;
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (review R10-3, R10-6, R11-9): nearer, on rougher land, mostly big, rarely closed, more often lit, where
			// still version 1's.
			Sc16Config d = new Sc16Config();
			minOriginDistance = ModConfig.Upgrades.keepOrNew(minOriginDistance, 640, d.minOriginDistance);
			maxGroundSpread = ModConfig.Upgrades.keepOrNew(maxGroundSpread, 12, d.maxGroundSpread);
			if (java.util.Arrays.equals(sizeWeights, new double[] {0.5, 0.35, 0.15})) {
				sizeWeights = d.sizeWeights;
			}
			sealedChance = ModConfig.Upgrades.keepOrNew(sealedChance, 0.25, d.sealedChance);
			apexLightChance = ModConfig.Upgrades.keepOrNew(apexLightChance, 0.35, d.apexLightChance);
		}
		version = CURRENT;
		return true;
	}
}
