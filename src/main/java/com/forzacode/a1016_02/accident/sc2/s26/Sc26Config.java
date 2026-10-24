package com.forzacode.a1016_02.accident.sc2.s26;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc26} (docs/SELF_CREATED_2.md, 26): {@link CallTrap}'s and {@link Call}'s numbers, opaque keys from
 * version 1 (D-080, D-103). The fair-danger floors ({@code accident.sc2.Hunts}: 60 s of tells before the first is let
 * go, health 14, 64 from the base) hold whatever these say. A later default change adds a step to {@link #upgrade}
 * with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc26Config implements ModConfig.Versioned {
	public static final String SECTION = "sc26";
	public static final int CURRENT = 2;

	public int version = 0;
	/** Instruments are looked for this far from the target (blocks). */
	@SerializedName("a")
	public int radius = 80;
	/** At least this many. */
	@SerializedName("b")
	public int minMobs = 4;
	/** At most this many (never over {@code Call.HARD_MAX}, 6). */
	@SerializedName("c")
	public int maxMobs = 6;
	/** The quiet first part lasts this long (seconds). */
	@SerializedName("d")
	public double silenceSeconds = 20;
	/** Each is let go to its own targeting this near the target (blocks). */
	@SerializedName("e")
	public double releaseAt = 16;
	/** It ends this long after it began (seconds). */
	@SerializedName("f")
	public double secondsMax = 180;
	/** At most once in this many in-game days per player. */
	@SerializedName("g")
	public int daysApart = 7;
	/** At most this many in a world. */
	@SerializedName("h")
	public int maxPerWorld = 2;
	/** At most this many of the walking dead toward one door. */
	@SerializedName("i")
	public int perDoorMax = 4;

	public static Sc26Config get() {
		return ModConfig.section(SECTION, Sc26Config.class, Sc26Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (review R10-19): a few, never a horde, where still version 1's.
			maxMobs = ModConfig.Upgrades.keepOrNew(maxMobs, 8, new Sc26Config().maxMobs);
		}
		version = CURRENT;
		return true;
	}
}
