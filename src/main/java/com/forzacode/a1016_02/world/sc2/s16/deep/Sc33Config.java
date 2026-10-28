package com.forzacode.a1016_02.world.sc2.s16.deep;

import com.forzacode.a1016_02.core.ModConfig;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc33} (docs/SELF_CREATED_2_R3.md, "33", "Config"): the numbers of the depths' answers, 33b to 33e.
 * Version 1, keys opaque from the start (D-103). The shape's numbers are constants of the pyramid's version, never here
 * (a start's shape never moves). A pair is {min, max}. Read on the server thread.
 */
public final class Sc33Config implements ModConfig.Versioned {
	public static final String SECTION = "sc33";
	public static final int CURRENT = 1;

	public int version = 0;
	/** 33b: a participant on the pit's floor or in the tunnel this long first (seconds). */
	@SerializedName("a")
	public double lureSeconds = 20;
	/** 33c: in the rooms, a door every this many seconds. */
	@SerializedName("b")
	public double[] roomsDoorEvery = {60, 120};
	/** 33c: in the warren, a door every this many seconds. */
	@SerializedName("c")
	public double[] warrenDoorEvery = {25, 50};
	/** 33c: in the warren, the share of the doors that really move. */
	@SerializedName("d")
	public double doorMovedShare = 0.5;
	/** 33c: at most this many doors a visit. */
	@SerializedName("e")
	public int doorsPerVisit = 12;
	/** 33c: at most this many of them moved. */
	@SerializedName("f")
	public int doorMovesPerVisit = 6;
	/** 33c: the steps beside you, this many a walk. */
	@SerializedName("g")
	public int[] stepsPerWalk = {6, 12};
	/** 33c: each step answered this late (ticks). */
	@SerializedName("h")
	public int stepLateTicks = 6;
	/** 33c: at most this many walks a visit. */
	@SerializedName("i")
	public int walksPerVisit = 2;
	/** The doors' sounds' volume (vanilla 1.0; 1.6 is clear at about 10 blocks). */
	@SerializedName("j")
	public double doorVolume = 1.6;
	/** The steps' volume. */
	@SerializedName("k")
	public double stepVolume = 1.6;
	/** The iron door's volume (heard to 32). */
	@SerializedName("l")
	public double ironVolume = 2.0;
	/** 33d: a participant in the warren this long (seconds), or anyone past the iron door. */
	@SerializedName("m")
	public double darkAfterSeconds = 45;
	/** 33d: a light every this many seconds. */
	@SerializedName("n")
	public double[] darkEverySeconds = {3, 6};
	/** 33e: a participant in the warren this long first (seconds). */
	@SerializedName("o")
	public double ironOpenAfterSeconds = 60;
	/** 33e: everyone at least this far from the iron door when it opens. */
	@SerializedName("p")
	public int ironOpenMinDistance = 16;
	/** 33e: a participant past it this long before it shuts (seconds). */
	@SerializedName("q")
	public double ironShutAfterSeconds = 20;
	/** 33e: everyone past it at least this far from it. */
	@SerializedName("r")
	public int ironShutMinDistance = 8;
	/** 33e: at most this many times a place, ever. */
	@SerializedName("s")
	public int ironShutPerPlaceMax = 3;
	/** 33e: his glimpse this far down his tunnel from the player. */
	@SerializedName("t")
	public int[] glimpseDistance = {14, 24};

	public static Sc33Config get() {
		return ModConfig.section(SECTION, Sc33Config.class, Sc33Config::new);
	}

	/** {@code pair[0]} to {@code pair[1]} (a broken pair: {@code fallback}). */
	static double[] range(double[] pair, double lo, double hi) {
		return pair != null && pair.length == 2 && pair[0] >= 0 && pair[1] >= pair[0] ? pair : new double[] {lo, hi};
	}

	static int[] range(int[] pair, int lo, int hi) {
		return pair != null && pair.length == 2 && pair[0] >= 0 && pair[1] >= pair[0] ? pair : new int[] {lo, hi};
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
