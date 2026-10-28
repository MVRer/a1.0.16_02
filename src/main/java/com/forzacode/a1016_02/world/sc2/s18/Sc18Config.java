package com.forzacode.a1016_02.world.sc2.s18;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc18} (docs/SELF_CREATED_2.md, 18): the numbers of the remains kind {@code s18} and of 18b. Version 2,
 * keys opaque from the start (D-103: no old keys). How far apart they are lives in the structure set's JSON (random
 * spread). Read on the server thread; worldgen reads it through the kit's snapshot. A later default change adds a step to
 * {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc18Config implements ModConfig.Versioned, Sc2Kind.Gates {
	public static final String SECTION = "sc18";
	public static final int CURRENT = 3;

	public int version = 0;
	/** The lowest stage whose new land may hold one (0: from the world's creation, D-108). */
	@SerializedName("a")
	public int fromStage = 0;
	/** No start nearer than this to (0, 0) or to the world spawn (blocks). */
	@SerializedName("b")
	public int minOriginDistance = 416;
	/** At least 40 % of the 81 ground samples rise this far over the floor (blocks; 36 in version 1, R10-3). */
	@SerializedName("c")
	public int minRise = 28;
	/** The rock kept over the inside under the land's top (blocks). */
	@SerializedName("d")
	public int shellTop = 7;
	/** The rock kept between the inside and a steep side (blocks). */
	@SerializedName("e")
	public int shellSide = 5;
	/** The columns left standing, one every this many blocks on a grid (the plan's 14 crowded the view: gallery round 1). */
	@SerializedName("f")
	public int pillarEvery = 21;
	/** How many bare stems stand on the dirt in the middle, at least and at most. */
	@SerializedName("g")
	public int trunksMin = 9;
	@SerializedName("h")
	public int trunksMax = 13;
	/** The share with a high opening where the mountain allows one (0.5 in version 1, R11-8). */
	@SerializedName("i")
	public double highWindowChance = 0.9;
	/** 18b: inside this long first (seconds). */
	@SerializedName("j")
	public double closeAfterSeconds = 120;
	/** 18b: everyone inside at least this far from the inner mouth (blocks). */
	@SerializedName("k")
	public int closeMinDistance = 24;
	/** 18b: at most this many times a place, ever. */
	@SerializedName("l")
	public int closePerPlaceMax = 2;
	/** Version 3's summit cut: the peak's top taken this deep (blocks, at least and at most; R3-3). */
	@SerializedName("m")
	public int[] summitCut = {8, 14};
	/** 18c: a participant this long inside near the grove first (seconds). */
	@SerializedName("n")
	public double pieceDarkAfterSeconds = 60;

	/** The cut's depth for this seed, within {@code m} (8 to 14 as built). */
	public int summitCutFor(long seed) {
		int lo = summitCut != null && summitCut.length > 0 ? summitCut[0] : 8;
		int hi = summitCut != null && summitCut.length > 1 ? summitCut[1] : 14;
		lo = Math.clamp(lo, 4, 15);
		hi = Math.clamp(Math.max(lo, hi), lo, 15);
		return com.forzacode.a1016_02.world.sc2.kit.Draw.between(seed, 0x18C7L, lo, hi);
	}

	public static Sc18Config get() {
		return ModConfig.section(SECTION, Sc18Config.class, Sc18Config::new);
	}

	@Override
	public int fromStage() {
		return fromStage;
	}

	@Override
	public int minOriginDistance() {
		return minOriginDistance;
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (review R10-3, R11-8): lower mountains hold one, and nearly every one has its opening, where still version 1's.
			Sc18Config d = new Sc18Config();
			minRise = ModConfig.Upgrades.keepOrNew(minRise, 36, d.minRise);
			highWindowChance = ModConfig.Upgrades.keepOrNew(highWindowChance, 0.5, d.highWindowChance);
		}
		if (version >= 1 && version < 3) {
			// 2 to 3 (round 3, D-108, D-109): the places from the world's creation, asleep until Traces, and
			// nearer the world spawn, where still version 2's.
			Sc18Config d = new Sc18Config();
			fromStage = ModConfig.Upgrades.keepOrNew(fromStage, 1, d.fromStage);
			minOriginDistance = ModConfig.Upgrades.keepOrNew(minOriginDistance, 700, d.minOriginDistance);
		}
		version = CURRENT;
		return true;
	}
}
