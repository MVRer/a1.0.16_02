package com.forzacode.a1016_02.atmosphere.sc2.s29;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc29} (docs/SELF_CREATED_2.md, 29): the card's numbers, opaque keys from version 1 (D-080, D-103). A
 * later default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises
 * {@link #CURRENT}.
 */
public final class Sc29Config implements ModConfig.Versioned {
	public static final String SECTION = "sc29";
	public static final int CURRENT = 1;

	public int version = 0;
	/** The other player's run of breaks is at most this many minutes old (real time). */
	@SerializedName("a")
	public double burstMinutes = 5.0;
	/** It plays from inside the rock at least this far from the target (blocks, 3D). */
	@SerializedName("b")
	public int distanceMin = 18;
	/** And at most this far. */
	@SerializedName("c")
	public int distanceMax = 28;
	/** At least this many degrees away from where the other player really is, seen from the target. */
	@SerializedName("d")
	public double bearingOff = 100.0;
	/** The other player is at least this far from the target (blocks, 3D, in the same level). */
	@SerializedName("e")
	public double friendMin = 40.0;
	/** And at most this far. */
	@SerializedName("f")
	public double friendMax = 200.0;

	public static Sc29Config get() {
		return ModConfig.section(SECTION, Sc29Config.class, Sc29Config::new);
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
