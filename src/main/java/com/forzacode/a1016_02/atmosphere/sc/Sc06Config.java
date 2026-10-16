package com.forzacode.a1016_02.atmosphere.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/** {@code sections.sc06} (version 2): card {@code sc_06}. See docs/SELF_CREATED.md, 06. */
public final class Sc06Config implements ModConfig.Versioned {
	public static final String SECTION = "sc06";
	public static final int VERSION = 2;

	public int version = 0;
	/** Seconds more of walking after the fire before anything answers. */
	@SerializedName(value = "a", alternate = "walkSecondsBeforeStart")
	public double walkSecondsBeforeStart = 3;
	/** Blocks behind the player, along their way. */
	@SerializedName(value = "b", alternate = "behindMin")
	public double behindMin = 6;
	@SerializedName(value = "c", alternate = "behindMax")
	public double behindMax = 9;
	/** Ticks after each of the player's own steps. */
	@SerializedName(value = "d", alternate = "lagTicksMin")
	public int lagTicksMin = 4;
	@SerializedName(value = "e", alternate = "lagTicksMax")
	public int lagTicksMax = 6;
	@SerializedName(value = "f", alternate = "stepsMin")
	public int stepsMin = 6;
	@SerializedName(value = "g", alternate = "stepsMax")
	public int stepsMax = 14;
	/** Heard to 16 times this (19.2 blocks). */
	@SerializedName(value = "h", alternate = "stepVolume")
	public double stepVolume = 1.2;
	/** Looking within this many degrees of behind ends it. */
	@SerializedName(value = "i", alternate = "lookBackDegrees")
	public double lookBackDegrees = 70;

	public static Sc06Config get() {
		return ModConfig.section(SECTION, Sc06Config.class, Sc06Config::new);
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
