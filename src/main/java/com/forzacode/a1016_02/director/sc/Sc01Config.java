package com.forzacode.a1016_02.director.sc;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * {@code sections.sc01} (docs/SELF_CREATED.md, 01): the numbers of {@link Places}. Version 2: the step from 1 lowers
 * {@link #awayFactor} from 1.0 to 0.9 where it is still 1.0 (SC-5: the dry runs with every card of the layer and the
 * dread share put a very late world's Traces minors 17% over DESIGN 4b at 1.0, 8% at 0.9). A later default change adds
 * a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc01Config implements ModConfig.Versioned {
	public static final String SECTION = "sc01";
	public static final int CURRENT = 2;

	public int version = 0;
	/** Places kept per player, the oldest out. */
	public int maxPlaces = 24;
	/** Places within this many blocks (3D, same dimension) count; also how far from a chunk's centre a place keeps it from being trusted. */
	public int radius = 32;
	/** The factor never goes above this. */
	public double maxFactor = 1.8;
	/** A place's weight halves every this many in-game days. */
	public double halfLifeDays = 4;
	public double weightAmbient = 0.15;
	public double weightMinor = 0.3;
	/** Majors and signatures. */
	public double weightMajor = 0.5;
	/** Added for a card with the SIGHTING tag. */
	public double weightSighting = 0.2;
	/** The factor while the player stands in the trusted chunk. */
	public double trustedFactor = 1.6;
	/** Dwell (hours of the player's play, halved every hour of it) a chunk needs to be trusted. */
	public double trustedMinHours = 1;
	/** Multiplies the rate where the factor is exactly 1 (tuning; 1.0 in version 1). */
	public double awayFactor = 0.9;
	/** Dry runs: the share of director ticks at {@link #simDreadFactor}. */
	public double simDreadShare = 0.3;
	public double simDreadFactor = 1.4;
	/** Dry runs: the share of director ticks at {@link #trustedFactor}. */
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
		version = CURRENT;
		return true;
	}
}
