package com.forzacode.a1016_02.atmosphere.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/** {@code sections.sc07} (version 2): card {@code sc_07}. See docs/SELF_CREATED.md, 07. */
public final class Sc07Config implements ModConfig.Versioned {
	public static final String SECTION = "sc07";
	public static final int VERSION = 2;

	public int version = 0;
	/** Mobs within this many blocks of a known base. */
	@SerializedName(value = "a", alternate = "baseRadius")
	public int baseRadius = 64;
	/** Nobody (spectators included) within this many blocks of the mob. */
	@SerializedName(value = "b", alternate = "nobodyWithin")
	public int nobodyWithin = 16;
	/** The target at least this far from the mob, or asleep. */
	@SerializedName(value = "c", alternate = "awayDistance")
	public int awayDistance = 48;
	@SerializedName(value = "d", alternate = "maxPerWorld")
	public int maxPerWorld = 3;
	/** In-game days between two, world-wide. */
	@SerializedName(value = "e", alternate = "minDaysApart")
	public int minDaysApart = 3;

	public static Sc07Config get() {
		return ModConfig.section(SECTION, Sc07Config.class, Sc07Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= VERSION) {
			return false;
		}
		// 1 to 2 (D-080): the keys are opaque letters; a value under an old key is read into its field (its
		// @SerializedName alternate) and only the new key is written back.
		version = VERSION;
		return true;
	}
}
