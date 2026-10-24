package com.forzacode.a1016_02.lore.sc2.s30;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc30} (docs/SELF_CREATED_2.md, 30): the card's numbers, opaque keys from version 1 (D-080, D-103). A
 * later default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises
 * {@link #CURRENT}.
 */
public final class Sc30Config implements ModConfig.Versioned {
	public static final String SECTION = "sc30";
	public static final int CURRENT = 1;

	public int version = 0;
	/** The square is at least this many pixels a side. */
	@SerializedName("a")
	public int sizeMin = 9;
	/** And at most this many. */
	@SerializedName("b")
	public int sizeMax = 15;
	/** The target at least this far from their base (blocks, horizontal). */
	@SerializedName("c")
	public double awayDistance = 64.0;
	/** The second form from this stage on (3: Telling). */
	@SerializedName("d")
	public int showFromStage = 3;
	/** At most this many in a world. */
	@SerializedName("e")
	public int maxPerWorld = 3;

	public static Sc30Config get() {
		return ModConfig.section(SECTION, Sc30Config.class, Sc30Config::new);
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
