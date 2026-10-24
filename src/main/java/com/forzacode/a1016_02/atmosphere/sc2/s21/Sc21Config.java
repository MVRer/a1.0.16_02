package com.forzacode.a1016_02.atmosphere.sc2.s21;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc21} (opaque keys from the start, D-103): card {@code sc_21}. See docs/SELF_CREATED_2.md, 21. Version 2
 * (review R11-10): {@code d} 2.2 to 3.2 and {@code k} 48 to 72, where still version 1's.
 */
public final class Sc21Config implements ModConfig.Versioned {
	public static final String SECTION = "sc21";
	public static final int VERSION = 2;

	public int version = 0;
	/** At least this many animals of one kind in the cluster. */
	@SerializedName("a")
	public int minMobs = 7;
	/** The cluster's middle at most this far from the target (and at least {@link #MIN_DISTANCE}). */
	@SerializedName("b")
	public int maxDistance = 64;
	/** At most this many animals take part. */
	@SerializedName("c")
	public int maxMobs = 14;
	/** Blocks of the way round per animal: the radius is the count times this over 2 pi, clamped 4 to 9 (a gap of about 2 each). */
	@SerializedName("d")
	public double spacing = 3.2;
	@SerializedName("e")
	public int circleSecondsMin = 90;
	@SerializedName("f")
	public int circleSecondsMax = 180;
	/** Anyone this near the middle ends it (the stare, then the animals go). */
	@SerializedName("g")
	public double breakDistance = 12.0;
	@SerializedName("h")
	public double stareSeconds = 3.0;
	@SerializedName("i")
	public int maxPerWorld = 6;
	/** In-game days between two, world-wide. */
	@SerializedName("j")
	public int minDaysApart = 2;
	/** At most this many cells of the way round are bared (and the middle one): every cell of a ring of 9 (one wide). */
	@SerializedName("k")
	public int printCells = 72;
	/** On an empty night (04), the chance that one forms without a card (04+). */
	@SerializedName("l")
	public double emptyNightChance = 0.5;

	/** The cluster's middle at least this far from the target. */
	public static final int MIN_DISTANCE = 24;
	/** Animals of one kind within this of the cluster's middle make the cluster. */
	public static final int CLUSTER_RADIUS = 24;

	public static Sc21Config get() {
		return ModConfig.section(SECTION, Sc21Config.class, Sc21Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= VERSION) {
			return false;
		}
		if (version >= 1 && version < 2) {
			// 1 to 2 (review R11-10): a wider ring with gaps between the animals, every cell of it bared.
			Sc21Config d = new Sc21Config();
			spacing = ModConfig.Upgrades.keepOrNew(spacing, 2.2, d.spacing);
			printCells = ModConfig.Upgrades.keepOrNew(printCells, 48, d.printCells);
		}
		version = VERSION;
		return true;
	}
}
