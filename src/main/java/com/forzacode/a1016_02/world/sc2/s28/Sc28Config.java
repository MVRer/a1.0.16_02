package com.forzacode.a1016_02.world.sc2.s28;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc28} (docs/SELF_CREATED_2.md, 28): the card's numbers, opaque keys from version 1 (D-080, D-103). A
 * later default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises
 * {@link #CURRENT}.
 */
public final class Sc28Config implements ModConfig.Versioned {
	public static final String SECTION = "sc28";
	public static final int CURRENT = 1;

	public int version = 0;
	/** A visit counts for this many in-game days. */
	@SerializedName("a")
	public double visitDays = 2.0;
	/** A visit is coming within this many blocks of another player's base (horizontal); the containers of a base are this close to it too. */
	@SerializedName("b")
	public int visitRadius = 16;
	/** Nobody within this many blocks of either container (a sleeping owner aside). */
	@SerializedName("c")
	public double nobodyWithin = 16.0;
	/** Each of the two players at least this far from their own base (horizontal), or asleep, or in another dimension. */
	@SerializedName("d")
	public double awayDistance = 32.0;
	/** At most this many in a world. */
	@SerializedName("e")
	public int maxPerWorld = 3;
	/** At least this many in-game days apart. */
	@SerializedName("f")
	public int minDaysApart = 3;
	/** A stack of at most this many items. */
	@SerializedName("g")
	public int maxCount = 64;

	public static Sc28Config get() {
		return ModConfig.section(SECTION, Sc28Config.class, Sc28Config::new);
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
