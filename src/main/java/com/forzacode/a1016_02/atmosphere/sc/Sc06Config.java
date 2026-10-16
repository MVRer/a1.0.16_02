package com.forzacode.a1016_02.atmosphere.sc;

import com.forzacode.a1016_02.core.ModConfig;

/** {@code sections.sc06} (version 1): card {@code sc_06}. See docs/SELF_CREATED.md, 06. */
public final class Sc06Config implements ModConfig.Versioned {
	public static final String SECTION = "sc06";
	public static final int VERSION = 1;

	public int version = 0;
	/** Seconds more of walking after the fire before anything answers. */
	public double walkSecondsBeforeStart = 3;
	/** Blocks behind the player, along their way. */
	public double behindMin = 6;
	public double behindMax = 9;
	/** Ticks after each of the player's own steps. */
	public int lagTicksMin = 4;
	public int lagTicksMax = 6;
	public int stepsMin = 6;
	public int stepsMax = 14;
	/** Heard to 16 times this (19.2 blocks). */
	public double stepVolume = 1.2;
	/** Looking within this many degrees of behind ends it. */
	public double lookBackDegrees = 70;

	public static Sc06Config get() {
		return ModConfig.section(SECTION, Sc06Config.class, Sc06Config::new);
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
