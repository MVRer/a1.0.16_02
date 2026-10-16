package com.forzacode.a1016_02.dig.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc12} (docs/SELF_CREATED.md, 12): {@link RestRefusal}'s numbers. Version 2; a later default change
 * adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc12Config implements ModConfig.Versioned {
	public static final String SECTION = "sc12";
	public static final int CURRENT = 2;

	public int version = 0;
	/** The shaft's top cell at most this many blocks under the bed. */
	@SerializedName(value = "a", alternate = "shaftBelowMax")
	public int shaftBelowMax = 2;
	/** An arm lasts this many in-game nights (on {@code GameClock.dayTicks}). */
	@SerializedName(value = "b", alternate = "armNights")
	public int armNights = 3;
	/** Beds within this many blocks of a refused bed are refused too, for {@link #sharedSeconds}. */
	@SerializedName(value = "c", alternate = "sharedBedRadius")
	public int sharedBedRadius = 8;
	@SerializedName(value = "d", alternate = "sharedSeconds")
	public double sharedSeconds = 20;
	/** At most this many refusals in a world. */
	@SerializedName(value = "e", alternate = "maxPerWorld")
	public int maxPerWorld = 3;

	public static Sc12Config get() {
		return ModConfig.section(SECTION, Sc12Config.class, Sc12Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		// 1 to 2 (D-080): the keys are opaque letters; a value under an old key is read into its field (its
		// @SerializedName alternate) and only the new key is written back.
		version = CURRENT;
		return true;
	}
}
