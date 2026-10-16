package com.forzacode.a1016_02.accident.sc;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * {@code sections.sc15} (docs/SELF_CREATED.md, 15): {@link StoodOn}'s numbers. Version 2 (review R9: never lethal on
 * its own; step 1 to 2: {@code airDropMax} 6 to 3, now counted from the feet, where still version 1's default; the
 * lava and water depth {@code cavityDepthMax} is gone, a file's old value is skipped; {@code minHealth} is new). A later
 * default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc15Config implements ModConfig.Versioned {
	public static final String SECTION = "sc15";
	public static final int CURRENT = 2;

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
	/** The fall from their feet onto the floor under it, at most this many blocks (never more than {@link StoodOn#SAFE_FALL}). */
	public double airDropMax = 3;
	/** Never while the player has less health than this. */
	public float minHealth = 10;
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
		if (version < 2) {
			// 1 to 2 (review R9): the drop, now from the feet and short enough to do no damage, where still version 1's.
			airDropMax = ModConfig.Upgrades.keepOrNew(airDropMax, 6.0, new Sc15Config().airDropMax);
		}
		version = CURRENT;
		return true;
	}
}
