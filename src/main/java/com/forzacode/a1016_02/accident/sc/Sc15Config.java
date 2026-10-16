package com.forzacode.a1016_02.accident.sc;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * {@code sections.sc15} (docs/SELF_CREATED.md, 15): {@link StoodOn}'s numbers. Version 1; a later default change adds
 * a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc15Config implements ModConfig.Versioned {
	public static final String SECTION = "sc15";
	public static final int CURRENT = 1;

	public int version = 0;
	/** An arm lasts this many minutes of the player's play. */
	public double armMinutes = 20;
	/** The cell is on one of their routes: walked at least this many separate times. */
	public int minPasses = 3;
	/** The cell their feet stood on this long ago. */
	public double delaySecondsMin = 3;
	public double delaySecondsMax = 8;
	/** The player at least this far from it by then (blocks). */
	public int behindMin = 5;
	/** Lava or water this many blocks under it at most. */
	public int cavityDepthMax = 6;
	/** An air drop onto a floor at most this deep. */
	public int airDropMax = 6;
	/** Nobody else within this many blocks of it. */
	public int nobodyWithin = 16;
	/** At most this many in a world (and once per in-game day per player). */
	public int maxPerWorld = 6;

	public static Sc15Config get() {
		return ModConfig.section(SECTION, Sc15Config.class, Sc15Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		version = CURRENT;
		return true;
	}
}
