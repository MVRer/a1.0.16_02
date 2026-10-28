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
	public static final int CURRENT = 4;

	public int version = 0;
	/** The lowest stage whose new land may hold one (0: from the world's creation, D-108). */
	@SerializedName("a")
	public int fromStage = 0;
	/** No start nearer than this to (0, 0) or to the world spawn (blocks). */
	@SerializedName("b")
	public int minOriginDistance = 352;
	/** On land, the ground under the footprint (9 samples) varies at most this much (blocks). */
	@SerializedName("c")
	public int maxGroundSpread = 16;
	/**
	 * How often each size is wanted (base 63, 95, 127, 191); a size that does not fit falls back to the next smaller.
	 * Version 2: mostly the two big ones (R11-9: the 63 stood lower than the hills; the first versions' was about 127 by
	 * 64). Version 4 (R3-2, D-115): the 191 a quarter of them, where the land allows it.
	 */
	@SerializedName("d")
	public double[] sizeWeights = {0.05, 0.35, 0.35, 0.25};
	/** On land, the share with no way in. */
	@SerializedName("e")
	public double sealedChance = 0.15;
	/** The share with a light on top (the sea form: 0.5 in version 1, this share from version 2). */
	@SerializedName("f")
	public double apexLightChance = 0.75;
	/** 16b: inside this long first (seconds). */
	@SerializedName("g")
	public double closeAfterSeconds = 40;
	/** 16b: the player at least this far from the inner mouth (blocks). */
	@SerializedName("h")
	public int closeMinDistance = 12;
	/** 16b: at most this many times a place, ever. */
	@SerializedName("i")
	public int closePerPlaceMax = 3;
	/** 16c: the player at least this many steps down. */
	@SerializedName("j")
	public int darkStepsMin = 6;
	/** 16c: one light every this many to {@link #darkEveryMax} seconds. */
	@SerializedName("k")
	public double darkEveryMin = 5;
	@SerializedName("l")
	public double darkEveryMax = 8;
	/** 16c: at most this many lights a visit. */
	@SerializedName("m")
	public int darkPerVisit = 6;
	/** 16c: after this many, the glimpse may come. */
	@SerializedName("n")
	public int glimpseAfter = 3;
	/** The sea form's share with a light on top (version 4: every one, D-110). */
	@SerializedName("o")
	public double seaLightChance = 1.0;
	/** How far the strip round the way in stands back from the face (blocks; version 4's sign, D-110). */
	@SerializedName("p")
	public int stripDepth = 1;
	/**
	 * On land, the ground under the 127's and the 191's footprint (9 samples; the 191's 7 x 7) varies at most this much
	 * (blocks; the 63 and the 95 keep {@code c}). The larger ones span more of the land's own roll (R3-2: T2).
	 */
	@SerializedName("q")
	public int maxGroundSpreadLarge = 24;
	/** 16e (the trail goes out): a participant inside this long, or a step down the pit, first (seconds). */
	@SerializedName("r")
	public double trailAfterSeconds = 6;
	/** 16e: one of the trail's lights every this many seconds at least (the kit tries an answer every 5). */
	@SerializedName("s")
	public double trailEverySeconds = 4;
	/** 16g (a sealed one opens): a participant this long within {@link PyramidOpening#NEAR} of where its way in would be (seconds). */
	@SerializedName("t")
	public double openAfterSeconds = 20;

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

	/**
	 * The most the ground under a size's footprint may vary on land and at sea: {@code c} for the 63 and the 95, {@code q}
	 * for the 127, two thirds more for the 191 (its 7 x 7 span twice the 95's land; 96 high, its lowest courses buried on
	 * one side, it is still a wall to the sky; R3-2 measured 1 in 40 made at a third more, 1 in 9 at this).
	 */
	public int spread(int size) {
		int large = Math.max(maxGroundSpread, maxGroundSpreadLarge);
		return size >= 3 ? large * 5 / 3 : size == 2 ? large : maxGroundSpread;
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
		if (version >= 1 && version < 3) {
			// 2 to 3 (round 3, D-108, D-109): the places from the world's creation, asleep until Traces, and
			// nearer the world spawn, where still version 2's.
			Sc16Config d = new Sc16Config();
			fromStage = ModConfig.Upgrades.keepOrNew(fromStage, 1, d.fromStage);
			minOriginDistance = ModConfig.Upgrades.keepOrNew(minOriginDistance, 560, d.minOriginDistance);
		}
		if (version >= 1 && version < 4) {
			// 3 to 4 (round 3, R3-2, D-115): the 191 among the sizes and a light on more of the tops, where still version
			// 3's; the new keys (o, p, q) take their defaults.
			Sc16Config d = new Sc16Config();
			if (java.util.Arrays.equals(sizeWeights, new double[] {0.1, 0.45, 0.45})) {
				sizeWeights = d.sizeWeights;
			}
			apexLightChance = ModConfig.Upgrades.keepOrNew(apexLightChance, 0.6, d.apexLightChance);
			// Mariano's playtest (a pyramid "seemed to have no purpose"): 16b and 16c come sooner, where still version 3's.
			closeAfterSeconds = ModConfig.Upgrades.keepOrNew(closeAfterSeconds, 90.0, d.closeAfterSeconds);
			closeMinDistance = ModConfig.Upgrades.keepOrNew(closeMinDistance, 20, d.closeMinDistance);
			darkStepsMin = ModConfig.Upgrades.keepOrNew(darkStepsMin, 12, d.darkStepsMin);
			darkEveryMin = ModConfig.Upgrades.keepOrNew(darkEveryMin, 8.0, d.darkEveryMin);
			darkEveryMax = ModConfig.Upgrades.keepOrNew(darkEveryMax, 12.0, d.darkEveryMax);
			glimpseAfter = ModConfig.Upgrades.keepOrNew(glimpseAfter, 4, d.glimpseAfter);
		}
		version = CURRENT;
		return true;
	}
}
