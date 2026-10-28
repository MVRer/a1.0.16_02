package com.forzacode.a1016_02.world.sc2.s32;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc32} (docs/SELF_CREATED_2_R3.md, 32): how the lines of feature 32 are laid and what its two runtime
 * rules do. Version 1, keys opaque from the start (D-103). Read on the server thread; worldgen reads the copy
 * {@link S32} publishes. A line keeps the numbers it was fixed with ({@link CrumbLines}), whatever this says later. A
 * later default change adds a step to {@link #upgrade} with {@code ModConfig.Upgrades.keepOrNew} and raises
 * {@link #CURRENT}.
 */
public final class Sc32Config implements ModConfig.Versioned {
	public static final String SECTION = "sc32";
	public static final int CURRENT = 1;

	public int version = 0;
	/** Lines from each place (1 to 3: toward the world spawn first, then the two others). */
	@SerializedName("a")
	public int linesPerPlace = 3;
	/** No crumb within this of the world spawn (blocks). */
	@SerializedName("b")
	public int spawnClear = 256;
	/** A line is at most this long (blocks). */
	@SerializedName("c")
	public int lineMax = 640;
	/** The near tier's spacing (blocks). */
	@SerializedName("d")
	public int nearSpacing = 16;
	/** The mid tier's spacing (blocks). */
	@SerializedName("e")
	public int midSpacing = 24;
	/** The far tier's spacing (blocks, give or take 8). */
	@SerializedName("f")
	public int farSpacing = 32;
	/** The near tier runs from 64 to this along the line (blocks). */
	@SerializedName("g")
	public int nearTo = 192;
	/** The mid tier runs from the near tier's end to this (blocks); the far one from here to the line's end. */
	@SerializedName("h")
	public int midTo = 384;
	/** 32b: everyone who has seen the crumb is at least this far from it (blocks). */
	@SerializedName("i")
	public int goneSeerAway = 96;
	/** 32b: a participant who has not seen it comes within this of it (blocks). */
	@SerializedName("j")
	public int goneNewcomerNear = 64;
	/** 32c: a far crumb is moved to at least this from the player's base (blocks) ... */
	@SerializedName("k")
	public int pullMin = 96;
	/** ... and at most this (blocks). */
	@SerializedName("l")
	public int pullMax = 160;
	/** 32c: at most this many crumbs moved from one line. */
	@SerializedName("m")
	public int pullPerLine = 4;
	/** 32c: never within this of any base (blocks). */
	@SerializedName("n")
	public int pullBaseClear = 64;

	public static Sc32Config get() {
		return ModConfig.section(SECTION, Sc32Config.class, Sc32Config::new);
	}

	/** A copy (worldgen keeps the one published, never the live section). */
	public Sc32Config copy() {
		Sc32Config c = new Sc32Config();
		c.version = version;
		c.linesPerPlace = linesPerPlace;
		c.spawnClear = spawnClear;
		c.lineMax = lineMax;
		c.nearSpacing = nearSpacing;
		c.midSpacing = midSpacing;
		c.farSpacing = farSpacing;
		c.nearTo = nearTo;
		c.midTo = midTo;
		c.goneSeerAway = goneSeerAway;
		c.goneNewcomerNear = goneNewcomerNear;
		c.pullMin = pullMin;
		c.pullMax = pullMax;
		c.pullPerLine = pullPerLine;
		c.pullBaseClear = pullBaseClear;
		return c;
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
