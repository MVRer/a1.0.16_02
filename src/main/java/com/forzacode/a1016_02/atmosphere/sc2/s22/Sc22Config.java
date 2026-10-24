package com.forzacode.a1016_02.atmosphere.sc2.s22;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc22} (opaque keys from the start, D-103): card {@code sc_22}. See docs/SELF_CREATED_2.md, 22. Version 2
 * (review R10-6): {@code a} 600 to 1500, where still version 1's.
 */
public final class Sc22Config implements ModConfig.Versioned {
	public static final String SECTION = "sc22";
	public static final int VERSION = 2;

	public int version = 0;
	/** One of his places within this of the target. */
	@SerializedName("a")
	public int destinationRadius = 1500;
	/** At least this many animals within {@link #ANIMAL_RADIUS}. */
	@SerializedName("b")
	public int minMobs = 5;
	@SerializedName("c")
	public int maxMobs = 9;
	/** They walk at most this long before they may go (longer while someone follows them, up to 400 s in all). */
	@SerializedName("d")
	public int walkSecondsMax = 120;
	/** They pass the target at least this far off ... */
	@SerializedName("e")
	public double passMin = 24.0;
	/** ... and at most this far. */
	@SerializedName("f")
	public double passMax = 40.0;
	@SerializedName("g")
	public int maxPerWorld = 3;
	/** In-game days between two, world-wide. */
	@SerializedName("h")
	public int minDaysApart = 3;

	/** The animals are found within this of the target. */
	public static final double ANIMAL_RADIUS = 96.0;
	/** The file starts this far from the target (at least, at most). */
	public static final double START_MIN = 48.0;
	public static final double START_MAX = 64.0;
	/** Blocks between one animal and the next (R11-13: 3.5, so from the side it reads as a file, not a herd). */
	public static final double SPACING = 3.5;
	/** The place is at least this far off (else there is no file to walk). */
	public static final double DESTINATION_MIN = 80.0;

	public static Sc22Config get() {
		return ModConfig.section(SECTION, Sc22Config.class, Sc22Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= VERSION) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (review R10-6): the file leads to a place farther off.
			destinationRadius = ModConfig.Upgrades.keepOrNew(destinationRadius, 600, new Sc22Config().destinationRadius);
		}
		version = VERSION;
		return true;
	}
}
