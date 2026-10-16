package com.forzacode.a1016_02.atmosphere.sc;

import com.forzacode.a1016_02.core.ModConfig;

/** {@code sections.sc05} (version 1): cards {@code sc_05} and {@code sc_05b}. See docs/SELF_CREATED.md, 05. */
public final class Sc05Config implements ModConfig.Versioned {
	public static final String SECTION = "sc05";
	public static final int VERSION = 1;

	public int version = 0;
	/** Away: at least this far from the base (or in another dimension)... */
	public int awayDistance = 48;
	/** ...for this many minutes of the player's own play. */
	public double awayMinutes = 10;
	/** An arm lasts this many minutes of the player's play. */
	public double armMinutes = 60;
	/** Covered floor cells within this many blocks of the base. */
	public int houseRadius = 6;
	public int minCoveredCells = 4;
	/** The steps play while the player is this far from the door, coming closer. */
	public double minDistance = 6;
	public double maxDistance = 16;
	public int stepsMin = 3;
	public int stepsMax = 5;
	public int stepTicks = 8;
	/** Heard to 16 times this (25.6 blocks). */
	public double stepVolume = 1.6;
	public int closeDelayMin = 15;
	public int closeDelayMax = 30;
	public double closeVolume = 1.6;
	/** {@code sc_05b} plays when the player comes this close to the base. */
	public double approachDistance = 32;
	public int mobRadius = 24;
	public int minMobs = 3;
	public double holdSecondsMax = 12;
	public double lookSeconds = 2;

	public static Sc05Config get() {
		return ModConfig.section(SECTION, Sc05Config.class, Sc05Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= VERSION) {
			return false;
		}
		version = VERSION;
		return true;
	}
}
