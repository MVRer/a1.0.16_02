package com.forzacode.a1016_02.atmosphere.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/** {@code sections.sc05} (version 2): cards {@code sc_05} and {@code sc_05b}. See docs/SELF_CREATED.md, 05. */
public final class Sc05Config implements ModConfig.Versioned {
	public static final String SECTION = "sc05";
	public static final int VERSION = 2;

	public int version = 0;
	/** Away: at least this far from the base (or in another dimension)... */
	@SerializedName(value = "a", alternate = "awayDistance")
	public int awayDistance = 48;
	/** ...for this many minutes of the player's own play. */
	@SerializedName(value = "b", alternate = "awayMinutes")
	public double awayMinutes = 10;
	/** An arm lasts this many minutes of the player's play. */
	@SerializedName(value = "c", alternate = "armMinutes")
	public double armMinutes = 60;
	/** Covered floor cells within this many blocks of the base. */
	@SerializedName(value = "d", alternate = "houseRadius")
	public int houseRadius = 6;
	@SerializedName(value = "e", alternate = "minCoveredCells")
	public int minCoveredCells = 4;
	/** The steps play while the player is this far from the door, coming closer. */
	@SerializedName(value = "f", alternate = "minDistance")
	public double minDistance = 6;
	@SerializedName(value = "g", alternate = "maxDistance")
	public double maxDistance = 16;
	@SerializedName(value = "h", alternate = "stepsMin")
	public int stepsMin = 3;
	@SerializedName(value = "i", alternate = "stepsMax")
	public int stepsMax = 5;
	@SerializedName(value = "j", alternate = "stepTicks")
	public int stepTicks = 8;
	/** Heard to 16 times this (25.6 blocks). */
	@SerializedName(value = "k", alternate = "stepVolume")
	public double stepVolume = 1.6;
	@SerializedName(value = "l", alternate = "closeDelayMin")
	public int closeDelayMin = 15;
	@SerializedName(value = "m", alternate = "closeDelayMax")
	public int closeDelayMax = 30;
	@SerializedName(value = "n", alternate = "closeVolume")
	public double closeVolume = 1.6;
	/** {@code sc_05b} plays when the player comes this close to the base. */
	@SerializedName(value = "o", alternate = "approachDistance")
	public double approachDistance = 32;
	@SerializedName(value = "p", alternate = "mobRadius")
	public int mobRadius = 24;
	@SerializedName(value = "q", alternate = "minMobs")
	public int minMobs = 3;
	@SerializedName(value = "r", alternate = "holdSecondsMax")
	public double holdSecondsMax = 12;
	@SerializedName(value = "s", alternate = "lookSeconds")
	public double lookSeconds = 2;

	public static Sc05Config get() {
		return ModConfig.section(SECTION, Sc05Config.class, Sc05Config::new);
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
