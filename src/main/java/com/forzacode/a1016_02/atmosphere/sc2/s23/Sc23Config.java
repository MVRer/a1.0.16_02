package com.forzacode.a1016_02.atmosphere.sc2.s23;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/** {@code sections.sc23} (version 1, opaque keys from the start, D-103): card {@code sc_23}. See docs/SELF_CREATED_2.md, 23. */
public final class Sc23Config implements ModConfig.Versioned {
	public static final String SECTION = "sc23";
	public static final int VERSION = 1;

	public int version = 0;
	/** At least this many mobs within {@link #radius} of the target. */
	@SerializedName("a")
	public int minMobs = 8;
	@SerializedName("b")
	public double radius = 48.0;
	/** At most this many are held, nearest first. */
	@SerializedName("c")
	public int maxMobs = 40;
	@SerializedName("d")
	public double seconds = 8.0;
	/** The bearing is the nearest of his places within this. */
	@SerializedName("e")
	public int remainsRadius = 2048;
	/** It may come within 30 s of the target's combat (D-101). */
	@SerializedName("f")
	public boolean duringCombat = true;

	/** At least this many of the mobs in the target's view. */
	public static final int MIN_SEEN = 3;
	/** The point they all face is this far along the bearing. */
	public static final double FACE_DISTANCE = 200.0;
	/** The shared silence fades in and out over this (ticks). */
	public static final int SILENCE_FADE = 20;

	public static Sc23Config get() {
		return ModConfig.section(SECTION, Sc23Config.class, Sc23Config::new);
	}

	@Override
	public boolean upgrade() {
		if (version >= VERSION) {
			return false;
		}
		version = VERSION;
		return true;
	}
}
