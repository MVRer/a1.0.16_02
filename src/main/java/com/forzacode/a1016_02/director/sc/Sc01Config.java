package com.forzacode.a1016_02.director.sc;

import java.util.LinkedHashMap;
import java.util.Map;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc01} (docs/SELF_CREATED.md, 01; docs/SELF_CREATED_2.md, 01+): the numbers of {@link Places}. Version
 * 4. The step from 1 to 2 lowers {@link #awayFactor} from 1.0 to 0.9 where it is still 1.0 (SC-5: the dry runs with every
 * card of the layer and the dread share put a very late world's Traces minors 17% over DESIGN 4b at 1.0, 8% at 0.9); 2 to
 * 3 makes the keys opaque (D-080); 3 to 4 only adds {@link #remainsFactor} and {@link #simRemainsShare} (SC2-9, 01+: keys
 * {@code o} and {@code p}, opaque from the start, with no key from before); 4 to 5 adds the second wave's pacing
 * ({@link ScPacing}; FIX-SC2-PACE: keys {@code q} to {@code z}, opaque from the start) and raises {@link #simRemainsShare}
 * from 0.03 to 0.06 where still 0.03 (the dry runs only: FIX-SC2-WORLD's denser places, R10-3, about twice the play at
 * them). A later default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises
 * {@link #CURRENT}.
 */
public final class Sc01Config implements ModConfig.Versioned {
	public static final String SECTION = "sc01";
	public static final int CURRENT = 5;

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
	/**
	 * 01+ (docs/SELF_CREATED_2.md): inside one of his places (its interior) or within {@link #radius} of its way in, the
	 * factor is at least this, never past {@link #maxFactor}.
	 */
	@SerializedName("o")
	public double remainsFactor = 1.5;
	/**
	 * Dry runs: the share of director ticks spent at one of his places, at {@link #remainsFactor} (and a visit inside one
	 * per 8 minutes of it, {@code SimFits.visitsPerHour}). 0.06 since version 5: the denser places (R10-3).
	 */
	@SerializedName("p")
	public double simRemainsShare = 0.06;
	/**
	 * The second wave's pacing ({@link ScPacing}, docs/SELF_CREATED_2.md "Pacing"): each of its cards' own weight in a
	 * draw (1 if missing).
	 */
	@SerializedName("q")
	public Map<String, Double> weights = defaultWeights();
	/** In Traces: the share of the ambient and the minor tier's due draws that come from the second wave, in that order. */
	@SerializedName("r")
	public double[] tracesShares = {0, 0};
	/** From Proximity on: the same shares. */
	@SerializedName("s")
	public double[] laterShares = {0.3, 0.75};
	/** Minutes of a player's play between two of its moments, their own or felt. */
	@SerializedName("t")
	public double gapMinutes = 30;
	/** Hours of a player's play before the same card of the second wave comes again for them. */
	@SerializedName("u")
	public double repeatHours = 2;
	/** From Proximity on, the first beat's windows (minutes of play), one per beat in turn. */
	@SerializedName("v")
	public double[] beatMinutes = {90, 60};
	/** For this many minutes of those windows, a beat looks for its moment only at dusk or at night. */
	@SerializedName("w")
	public double beatDarkMinutes = 45;
	/** Hours of play (times the tempo, never before Proximity) before the hunts' own clock lets the first one come. */
	@SerializedName("x")
	public double huntFromHours = 2.5;
	/** Hours of play between two hunts for one player. */
	@SerializedName("y")
	public double huntGapHours = 2;
	/** After a player's first hunt, one may take a major slot due within this many minutes (one called by a trespass: any). */
	@SerializedName("z")
	public double huntPullMinutes = 60;

	/** The second wave's cards' own weights by default. */
	public static Map<String, Double> defaultWeights() {
		Map<String, Double> weights = new LinkedHashMap<>();
		weights.put("sc_21", 2.0);
		weights.put("sc_22", 3.0);
		weights.put("sc_23", 2.0);
		weights.put("sc_31", 3.0);
		weights.put("sc_31c", 3.0);
		return weights;
	}

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
		// 3 to 4 (01+): only the two new fields, read at their defaults from a file without them.
		if (version < 5) {
			// 4 to 5 (FIX-SC2-PACE): the second wave's pacing, q to z, read at their defaults from a file without them; the dry
			// runs' share of play at his places for the denser places, only where still version 4's.
			simRemainsShare = ModConfig.Upgrades.keepOrNew(simRemainsShare, 0.03, 0.06);
		}
		version = CURRENT;
		return true;
	}
}
