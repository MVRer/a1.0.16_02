package com.forzacode.a1016_02.world.sc2.s17;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.google.gson.annotations.SerializedName;

/**
 * {@code sections.sc17} (docs/SELF_CREATED_2.md, feature 17, "Config"). Version 1, opaque keys {@code a} to {@code r}
 * from the first version (D-080, D-103). Read on the server thread; worldgen reads it through the kit's snapshot.
 */
public final class Sc17Config implements ModConfig.Versioned, Sc2Kind.Gates {
	public static final String SECTION = "sc17";
	public static final int VERSION = 1;

	public int version = 0;
	/** The lowest stage whose newly generated land may hold one (1, Traces). */
	@SerializedName("a")
	public int fromStage = 1;
	/** No start nearer than this to the origin or the world spawn. */
	@SerializedName("b")
	public int minOriginDistance = 500;
	/** The chance of the long hall (95 instead of 63, the bigger grid of cells). */
	@SerializedName("c")
	public double longHallChance = 0.25;
	/** The share of the cells' wall blocks that are infested. */
	@SerializedName("d")
	public double infestedShare = 0.08;
	/** 17b: seconds inside (at least {@code f} in from the outer cell) before the lights go. */
	@SerializedName("e")
	public int darkAfterSeconds = 60;
	/** 17b: blocks in from the outer cell, as walked through the cells (cell pitch 8). */
	@SerializedName("f")
	public int darkDepth = 24;
	/** 17b: at least this many hall rows dark between the player and the way out. */
	@SerializedName("g")
	public int darkRowsMin = 8;
	/** 17b: ticks between rows. */
	@SerializedName("h")
	public int darkRowTicks = 160;
	/** 17b: in-game days between two at one place. */
	@SerializedName("i")
	public int darkDaysApart = 1;
	/** 17b: after this many rows, the glimpse at the far end of the dark. */
	@SerializedName("j")
	public int glimpseAfterRows = 4;
	/** 17c: every player who has been inside is at least this far away. */
	@SerializedName("k")
	public double doorFinderAway = 64.0;
	/** 17c: a newcomer within this of the open mouth. */
	@SerializedName("l")
	public double doorNewcomerNear = 24.0;
	/** 17c: at most this many moves per place. */
	@SerializedName("m")
	public int doorMovesMax = 2;
	/** 17d: at most this many moves per visit. */
	@SerializedName("n")
	public int markerMovesPerVisit = 2;
	/** 17e: the container is at least this far from its owner. */
	@SerializedName("o")
	public double bookAwayDistance = 64.0;
	/** 17c alone: the chance a player coming back after a day away finds it moved. */
	@SerializedName("p")
	public double doorMovedChance = 0.3;
	/** 11+: the chance the trail leads into the cells. */
	@SerializedName("q")
	public double t0rchChance = 0.5;
	/** 11+: a cell within this of the cave walk. */
	@SerializedName("r")
	public int t0rchRadius = 64;

	public static Sc17Config get() {
		return ModConfig.section(SECTION, Sc17Config.class, Sc17Config::new);
	}

	@Override
	public int fromStage() {
		return fromStage;
	}

	@Override
	public int minOriginDistance() {
		return minOriginDistance;
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
