package com.forzacode.a1016_02.accident.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc15} (docs/SELF_CREATED.md, 15): {@link StoodOn}'s numbers. Version 3 (review R9: never lethal on
 * its own; step 1 to 2: {@code airDropMax} 6 to 3, now counted from the feet, where still version 1's default; the
 * lava and water depth {@code cavityDepthMax} is gone, a file's old value is skipped; {@code minHealth} is new; step 2
 * to 3: the keys are opaque, D-080). A later default change adds a step to {@link #upgrade} with
 * {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc15Config implements ModConfig.Versioned {
	public static final String SECTION = "sc15";
	public static final int CURRENT = 3;

	public int version = 0;
	/** An arm lasts this many minutes of the player's play. */
	@SerializedName(value = "a", alternate = "armMinutes")
	public double armMinutes = 20;
	/** The cell is on one of their routes: walked at least this many separate times. */
	@SerializedName(value = "b", alternate = "minPasses")
	public int minPasses = 3;
	/** The cell their feet stood on this long ago. */
	@SerializedName(value = "c", alternate = "delaySecondsMin")
	public double delaySecondsMin = 3;
	@SerializedName(value = "d", alternate = "delaySecondsMax")
	public double delaySecondsMax = 8;
	/** The player at least this far from it by then (blocks). */
	@SerializedName(value = "e", alternate = "behindMin")
	public int behindMin = 5;
	/** The fall from their feet onto the floor under it, at most this many blocks (never more than {@link StoodOn#SAFE_FALL}). */
	@SerializedName(value = "f", alternate = "airDropMax")
	public double airDropMax = 3;
	/** Never while the player has less health than this. */
	@SerializedName(value = "g", alternate = "minHealth")
	public float minHealth = 10;
	/** Nobody else within this many blocks of it. */
	@SerializedName(value = "h", alternate = "nobodyWithin")
	public int nobodyWithin = 16;
	/** At most this many in a world (and once per in-game day per player). */
	@SerializedName(value = "i", alternate = "maxPerWorld")
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
		// 2 to 3 (D-080): the keys are opaque letters; a value under an old key is read into its field (its
		// @SerializedName alternate) and only the new key is written back.
		version = CURRENT;
		return true;
	}
}
