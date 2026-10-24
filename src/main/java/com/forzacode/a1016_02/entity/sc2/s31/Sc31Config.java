package com.forzacode.a1016_02.entity.sc2.s31;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc31} (keys {@code a} to {@code c}), entity's (docs/SELF_CREATED_2.md, 31 and 31c). Version 2 (review
 * R11-4, R10-15): the top {@code a} 56 to {@code b} 110 off (from 96 to 256: at 110 and over he was a nub on the point),
 * where still version 1's.
 */
public final class Sc31Config implements ModConfig.Versioned {
	public static final String SECTION = "sc31";
	public static final int CURRENT = 2;

	public int version = 0;
	/** 31: the top block he stands on is at least this far from the player (blocks, horizontal)... */
	@SerializedName("a")
	public double apexMin = 56.0;
	/** ...and at most this far. */
	@SerializedName("b")
	public double apexMax = 110.0;
	/** 31c: the player within this many blocks (horizontal) of the place's middle among the trunks. */
	@SerializedName("c")
	public double trunksMax = 40.0;

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (review R11-4, R10-15): near enough to read as someone standing on the top.
			Sc31Config d = new Sc31Config();
			apexMin = ModConfig.Upgrades.keepOrNew(apexMin, 96.0, d.apexMin);
			apexMax = ModConfig.Upgrades.keepOrNew(apexMax, 256.0, d.apexMax);
		}
		version = CURRENT;
		return true;
	}

	public static Sc31Config get() {
		return ModConfig.section(SECTION, Sc31Config.class, Sc31Config::new);
	}
}
