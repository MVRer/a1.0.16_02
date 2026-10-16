package com.forzacode.a1016_02.atmosphere.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;

/**
 * {@code sections.sc04} (version 1): the night roll of {@link EmptyNight}. Field names are plain rules; the doc
 * (docs/SELF_CREATED.md, 04) has the rest.
 */
public final class Sc04Config implements ModConfig.Versioned {
	public static final String SECTION = "sc04";
	public static final int VERSION = 1;

	public int version = 0;
	/** The chance per night by stage (0 Alone to 4 Removal). */
	public double[] chanceByStage = {0.0, 0.2, 0.25, 0.25, 0.15};
	/** At least this many nights after the last one. */
	public int minNightsApart = 6;
	/** At most this many in a world. */
	public int maxPerWorld = 5;
	/** Only on a night whose own phase is full or gibbous. */
	public boolean brightPhasesOnly = true;
	/** Blocks (horizontal) around each participant where nothing hostile spawns that night. */
	public int spawnRadius = 96;
	/** Blocks around each participant where animals are quiet that night. */
	public int quietRadius = 48;
	public boolean quietAnimals = true;
	public boolean noPhantoms = true;

	public static Sc04Config get() {
		return ModConfig.section(SECTION, Sc04Config.class, Sc04Config::new);
	}

	/** The chance for this stage (0 past the list or for a bad list). */
	public double chance(Stage stage) {
		double[] chances = chanceByStage;
		if (chances == null || chances.length == 0) {
			return 0.0;
		}
		double c = chances[Math.min(stage.level(), chances.length - 1)];
		return Double.isFinite(c) ? Math.clamp(c, 0.0, 1.0) : 0.0;
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
