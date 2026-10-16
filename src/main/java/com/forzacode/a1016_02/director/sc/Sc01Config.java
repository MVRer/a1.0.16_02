package com.forzacode.a1016_02.director.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc01} (docs/SELF_CREATED.md, 01): the numbers of {@link Places}. Version 3. The step from 1 to 2
 * lowers {@link #awayFactor} from 1.0 to 0.9 where it is still 1.0 (SC-5: the dry runs with every card of the layer and
 * the dread share put a very late world's Traces minors 17% over DESIGN 4b at 1.0, 8% at 0.9); 2 to 3 makes the keys
 * opaque (D-080). A later default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and
 * raises {@link #CURRENT}.
 */
public final class Sc01Config implements ModConfig.Versioned {
	public static final String SECTION = "sc01";
	public static final int CURRENT = 3;

	public int version = 0;
	/** Places kept per player, the oldest out. */
	@SerializedName(value = "a", alternate = "maxPlaces")
	public int maxPlaces = 24;
	/** Places within this many blocks (3D, same dimension) count; also how far from a chunk's centre a place keeps it from being trusted. */
	@SerializedName(value = "b", alternate = "radius")
	public int radius = 32;
	/** The factor never goes above this. */
	@SerializedName(value = "c", alternate = "maxFactor")
	public double maxFactor = 1.8;
	/** A place's weight halves every this many in-game days. */
	@SerializedName(value = "d", alternate = "halfLifeDays")
	public double halfLifeDays = 4;
	@SerializedName(value = "e", alternate = "weightAmbient")
	public double weightAmbient = 0.15;
	@SerializedName(value = "f", alternate = "weightMinor")
	public double weightMinor = 0.3;
	/** Majors and signatures. */
	@SerializedName(value = "g", alternate = "weightMajor")
	public double weightMajor = 0.5;
	/** Added for a card with the SIGHTING tag. */
	@SerializedName(value = "h", alternate = "weightSighting")
	public double weightSighting = 0.2;
	/** The factor while the player stands in the trusted chunk. */
	@SerializedName(value = "i", alternate = "trustedFactor")
	public double trustedFactor = 1.6;
	/** Dwell (hours of the player's play, halved every hour of it) a chunk needs to be trusted. */
	@SerializedName(value = "j", alternate = "trustedMinHours")
	public double trustedMinHours = 1;
	/** Multiplies the rate where the factor is exactly 1 (tuning; 1.0 in version 1). */
	@SerializedName(value = "k", alternate = "awayFactor")
	public double awayFactor = 0.9;
	/** Dry runs: the share of director ticks at {@link #simDreadFactor}. */
	@SerializedName(value = "l", alternate = "simDreadShare")
	public double simDreadShare = 0.3;
	@SerializedName(value = "m", alternate = "simDreadFactor")
	public double simDreadFactor = 1.4;
	/** Dry runs: the share of director ticks at {@link #trustedFactor}. */
	@SerializedName(value = "n", alternate = "simTrustedShare")
	public double simTrustedShare = 0.05;

	/** The section as it is now. */
	public static Sc01Config get() {
		return ModConfig.section(SECTION, Sc01Config.class, Sc01Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		if (version < 2) {
			// 1 to 2 (SC-5): the rate away from every place, only where it is still version 1's default.
			awayFactor = ModConfig.Upgrades.keepOrNew(awayFactor, 1.0, new Sc01Config().awayFactor);
		}
		// 2 to 3 (D-080): the keys are opaque letters; a value under an old key is read into its field (its
		// @SerializedName alternate) and only the new key is written back.
		version = CURRENT;
		return true;
	}
}
