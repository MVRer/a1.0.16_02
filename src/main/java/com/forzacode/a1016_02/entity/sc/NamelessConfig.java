package com.forzacode.a1016_02.entity.sc;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * {@code sections.sc02} (version 1), entity's: the card's fit, the client's rule and the arm (docs/SELF_CREATED.md, 02). The
 * client reads its own copy (in singleplayer the same file), as the glimpse's hide latch does.
 */
public final class NamelessConfig implements ModConfig.Versioned {
	public static final String SECTION = "sc02";
	public static final int CURRENT = 1;

	public int version = 0;
	/** The card fits only with another participant at least this far from the target (blocks, outside the shared radius). */
	public double friendMinAtFire = 48.0;
	/** The client's band: no nearer than this... */
	public double minDistance = 24.0;
	/** ...and no farther than this (blocks; also the card's farthest friend). */
	public double maxDistance = 64.0;
	/** Past this share of the way from the visible fog start to its end. */
	public double fogShare = 0.5;
	/** Stood still (moved under 0.1 blocks) for this long, seconds. */
	public double stillSeconds = 1.5;
	/** An arm lasts this long of the target's play, minutes... */
	public double armMinutes = 20.0;
	/** ...or until the client has hidden names for this long in all, seconds. */
	public double hideSecondsTotal = 30.0;

	@Override
	public boolean upgrade() {
		if (version >= CURRENT) {
			return false;
		}
		version = CURRENT;
		return true;
	}

	public static NamelessConfig get() {
		return ModConfig.section(SECTION, NamelessConfig.class, NamelessConfig::new);
	}

	/** {@link #armMinutes} in play ticks. */
	public long armTicks() {
		return ModConfig.realTicks(Math.max(0.0, armMinutes) * 60.0);
	}

	/** {@link #hideSecondsTotal} in ticks. */
	public int hideTicks() {
		return (int) Math.min(Integer.MAX_VALUE, ModConfig.realTicks(Math.max(0.0, hideSecondsTotal)));
	}

	/** {@link #stillSeconds} in ticks. */
	public long stillTicks() {
		return ModConfig.realTicks(Math.max(0.0, stillSeconds));
	}
}
