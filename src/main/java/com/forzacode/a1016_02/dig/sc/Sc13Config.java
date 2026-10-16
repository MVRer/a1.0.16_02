package com.forzacode.a1016_02.dig.sc;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc13} (docs/SELF_CREATED.md, 13): {@link WayBack}'s numbers. Version 2; a later default change adds
 * a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises {@link #CURRENT}.
 */
public final class Sc13Config implements ModConfig.Versioned {
	public static final String SECTION = "sc13";
	public static final int CURRENT = 2;

	public int version = 0;
	/** The player has walked at least this many path blocks past the point. */
	@SerializedName(value = "a", alternate = "pastBlocks")
	public int pastBlocks = 40;
	/** Their trail is sampled this often while underground (ticks). */
	@SerializedName(value = "b", alternate = "trailSampleTicks")
	public int trailSampleTicks = 20;
	/** At most this many samples are kept. */
	@SerializedName(value = "c", alternate = "trailLength")
	public int trailLength = 96;
	/** A player's torch within this many blocks of the point on the way out. */
	@SerializedName(value = "d", alternate = "nextTorchWithin")
	public int nextTorchWithin = 6;
	/** Nobody (the player included) within this many blocks of the point. */
	@SerializedName(value = "e", alternate = "nobodyWithin")
	public int nobodyWithin = 16;
	/** At most this many in a world. */
	@SerializedName(value = "f", alternate = "maxPerWorld")
	public int maxPerWorld = 1;

	public static Sc13Config get() {
		return ModConfig.section(SECTION, Sc13Config.class, Sc13Config::new);
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
