package com.forzacode.a1016_02.atmosphere.sc;

import com.forzacode.a1016_02.core.ModConfig;

/** {@code sections.sc07} (version 1): card {@code sc_07}. See docs/SELF_CREATED.md, 07. */
public final class Sc07Config implements ModConfig.Versioned {
	public static final String SECTION = "sc07";
	public static final int VERSION = 1;

	public int version = 0;
	/** Mobs within this many blocks of a known base. */
	public int baseRadius = 64;
	/** Nobody (spectators included) within this many blocks of the mob. */
	public int nobodyWithin = 16;
	/** The target at least this far from the mob, or asleep. */
	public int awayDistance = 48;
	public int maxPerWorld = 3;
	/** In-game days between two, world-wide. */
	public int minDaysApart = 3;

	public static Sc07Config get() {
		return ModConfig.section(SECTION, Sc07Config.class, Sc07Config::new);
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
