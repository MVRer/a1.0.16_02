package com.forzacode.a1016_02.dig.sc;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * {@code sections.sc12} (docs/SELF_CREATED.md, 12): {@link RestRefusal}'s numbers. Version 1; a later default change
 * adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc12Config implements ModConfig.Versioned {
	public static final String SECTION = "sc12";
	public static final int CURRENT = 1;

	public int version = 0;
	/** The shaft's top cell at most this many blocks under the bed. */
	public int shaftBelowMax = 2;
	/** An arm lasts this many in-game nights (on {@code GameClock.dayTicks}). */
	public int armNights = 3;
	/** Beds within this many blocks of a refused bed are refused too, for {@link #sharedSeconds}. */
	public int sharedBedRadius = 8;
	public double sharedSeconds = 20;
	/** At most this many refusals in a world. */
	public int maxPerWorld = 3;

	public static Sc12Config get() {
		return ModConfig.section(SECTION, Sc12Config.class, Sc12Config::new);
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
