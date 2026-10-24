package com.forzacode.a1016_02.atmosphere.sc2.s24;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc24} (opaque keys from the start, D-103): card {@code sc_24}. See docs/SELF_CREATED_2.md, 24. Version 2
 * (review R10-10): the follow at the edge of view ({@code a} 16 to 28, {@code b} 28 to 40, {@code c} 12 to 20, {@code d} 40
 * to 56) and half a day to dawn ({@code e} 1 to 0.5), where still version 1's.
 */
public final class Sc24Config implements ModConfig.Versioned {
	public static final String SECTION = "sc24";
	public static final int VERSION = 2;

	public int version = 0;
	/** It keeps at least this far back along the target's way (blocks)... */
	@SerializedName("a")
	public double followMin = 28.0;
	/** ... and at most this far. */
	@SerializedName("b")
	public double followMax = 40.0;
	/** Never nearer than this to the target. */
	@SerializedName("c")
	public double nearest = 20.0;
	/** Further back than this and out of everyone's view, it is moved up behind them. */
	@SerializedName("d")
	public double catchUpAt = 56.0;
	/** In-game days it lasts (carried on to dawn when that falls at night). */
	@SerializedName("e")
	public double days = 0.5;
	@SerializedName("f")
	public int maxPerWorld = 4;
	/** In-game days between two, world-wide. */
	@SerializedName("g")
	public int minDaysApart = 4;

	/** The animal is found within this of the target. */
	public static final double FIND_RADIUS = 48.0;

	public static Sc24Config get() {
		return ModConfig.section(SECTION, Sc24Config.class, Sc24Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= VERSION) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (review R10-10): a glimpse at the edge of view, never an escort; gone at dawn.
			Sc24Config d = new Sc24Config();
			followMin = ModConfig.Upgrades.keepOrNew(followMin, 16.0, d.followMin);
			followMax = ModConfig.Upgrades.keepOrNew(followMax, 28.0, d.followMax);
			nearest = ModConfig.Upgrades.keepOrNew(nearest, 12.0, d.nearest);
			catchUpAt = ModConfig.Upgrades.keepOrNew(catchUpAt, 40.0, d.catchUpAt);
			days = ModConfig.Upgrades.keepOrNew(days, 1.0, d.days);
		}
		version = VERSION;
		return true;
	}
}
