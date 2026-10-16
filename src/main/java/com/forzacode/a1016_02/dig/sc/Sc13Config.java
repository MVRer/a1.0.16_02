package com.forzacode.a1016_02.dig.sc;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * {@code sections.sc13} (docs/SELF_CREATED.md, 13): {@link WayBack}'s numbers. Version 1; a later default change adds
 * a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc13Config implements ModConfig.Versioned {
	public static final String SECTION = "sc13";
	public static final int CURRENT = 1;

	public int version = 0;
	/** The player has walked at least this many path blocks past the point. */
	public int pastBlocks = 40;
	/** Their trail is sampled this often while underground (ticks). */
	public int trailSampleTicks = 20;
	/** At most this many samples are kept. */
	public int trailLength = 96;
	/** A player's torch within this many blocks of the point on the way out. */
	public int nextTorchWithin = 6;
	/** Nobody (the player included) within this many blocks of the point. */
	public int nobodyWithin = 16;
	/** At most this many in a world. */
	public int maxPerWorld = 1;

	public static Sc13Config get() {
		return ModConfig.section(SECTION, Sc13Config.class, Sc13Config::new);
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
