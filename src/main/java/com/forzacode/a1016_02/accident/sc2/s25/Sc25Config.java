package com.forzacode.a1016_02.accident.sc2.s25;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc25} (docs/SELF_CREATED_2.md, 25): {@link PackTrap}'s and {@link Pack}'s numbers, opaque keys from
 * version 1 (D-080, D-103). The fair-danger floors ({@code accident.sc2.Hunts}: 60 s of tells, health 14, 64 from
 * the base) are never loosened by these: a smaller value here is read as the floor. Version 2 (FIX-SC2-PACE, R10-4):
 * {@link #nightChance} 0.5 to 1.0 where still 0.5, since the director's own clock for the hunts paces them now (from
 * {@code sc01 x}, one per {@code sc01 y}); the day's roll only halved the nights a pack could come on top of it. A later
 * default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc25Config implements ModConfig.Versioned {
	public static final String SECTION = "sc25";
	public static final int CURRENT = 2;

	public int version = 0;
	/** At least this far from the target's base (blocks, horizontal; never under 64 unless forced). */
	@SerializedName("a")
	public int fromBase = 64;
	/** Never under this health (never under 14). */
	@SerializedName("b")
	public float minHealth = 14;
	/** At least this many wild adult instruments loaded near, out of everyone's view. */
	@SerializedName("c")
	public int minWolves = 3;
	/** Looked for this far from the target (blocks). */
	@SerializedName("d")
	public int gatherRadius = 128;
	/** At most this many (3, one more at armor 10, one more at armor 15). */
	@SerializedName("e")
	public int packMax = 5;
	/** The first part lasts at least this long (seconds, never under 60). */
	@SerializedName("f")
	public double stalkSecondsMin = 60;
	/** And at most this long. */
	@SerializedName("g")
	public double stalkSecondsMax = 120;
	/** The two long tells' volume (3.0: heard to 48). */
	@SerializedName("h")
	public float howlVolume = 3.0F;
	/** The last part lasts at most this long (seconds). */
	@SerializedName("i")
	public double attackSecondsMax = 40;
	/** At most once in this many in-game days per player. */
	@SerializedName("j")
	public int daysApart = 5;
	/** At most this many in a world. */
	@SerializedName("k")
	public int maxPerWorld = 3;
	/** This many times as likely tonight for a player inside one of his places within the last 2 in-game days. */
	@SerializedName("l")
	public double trespassFactor = 2.0;
	/**
	 * The chance a night (or a fog) fits for a player at all: rolled once per player per in-game day (1.0 since version 2:
	 * every dark night may, the director's own clock paces the hunts).
	 */
	@SerializedName("m")
	public double nightChance = 1.0;
	/** Waiting below a player out of reach lasts at most this long (seconds) before they go. */
	@SerializedName("n")
	public double siegeSeconds = 30;

	public static Sc25Config get() {
		return ModConfig.section(SECTION, Sc25Config.class, Sc25Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		if (version < 2) {
			// 1 to 2 (FIX-SC2-PACE): the day's roll gives way to the director's own clock, only where still version 1's.
			nightChance = ModConfig.Upgrades.keepOrNew(nightChance, 0.5, 1.0);
		}
		version = CURRENT;
		return true;
	}
}
