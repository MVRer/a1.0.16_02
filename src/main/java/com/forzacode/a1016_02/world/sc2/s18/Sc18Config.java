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
	public static final int CURRENT = 2;

	public int version = 0;
	/** The lowest stage whose new land may hold one (1, Traces). */
	@SerializedName("a")
	public int fromStage = 1;
	/** No start nearer than this to (0, 0) or to the world spawn (blocks). */
	@SerializedName("b")
	public int minOriginDistance = 700;
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
		version = CURRENT;
		return true;
	}
}
