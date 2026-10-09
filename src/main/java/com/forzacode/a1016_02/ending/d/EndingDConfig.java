package com.forzacode.a1016_02.ending.d;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * Ending D's tunables, {@code sections.ending_d} in {@code config/a1016_02.json}. Real-time values are in seconds and
 * go through {@link ModConfig#realTicks} (so {@code devFastMode} divides them); in-game distances are in blocks.
 */
public final class EndingDConfig {
	// --- the chain ---
	/** How often the chain looks at the subject, in ticks (not real time: detection, not pacing). */
	public int checkTicks = 10;
	/** Poplar logs the player must cut in the untouched grove (step 2). */
	public int groveLogsMin = 1;
	/** Within this many blocks of the cairn's center counts as being at the cairn (step 3). */
	public int cairnRadius = 16;
	/** How far from the cairn the first block must be carried (step 3). */
	public int carryAwayBlocks = 32;
	/** Torches in the bedrock chamber (step 5): one per dead list member. */
	public int torchesRequired = 6;
	/** How far (horizontally) from F30's twin the sentence may stand (step 6). */
	public int sentenceRadius = 2;

	// --- dangers ---
	/**
	 * Step 1: at night, while the player is this close to the map's camp, existing creepers this close to it are moved
	 * into its tents (searched this far around it), at most this many per night, this often, silent this long.
	 */
	public int campWatchRadius = 96;
	public int campRadius = 16;
	public int creeperSearchRadius = 48;
	public int creepersPerNight = 2;
	public double creeperCooldownSeconds = 30;
	public double creeperSilenceSeconds = 300;
	/** The leaf under a climber in the grove: only this high above the ground, at most this often. */
	public int leafDropMinHeight = 3;
	public double leafDropCooldownSeconds = 45;
	/** Spiders moved into the grove's canopy: from this far, at most this many per visit, frozen this long. */
	public int spiderSearchRadius = 48;
	public int spidersPerVisit = 2;
	public double spiderFreezeSeconds = 120;
	public double spiderCooldownSeconds = 40;
	/** How often step 3 tries to arm the cairn lure while the player is away from the cairn. */
	public double cairnArmRetrySeconds = 60;
	/** The stairwell floods once the player is this many blocks down the stair. */
	public int floodDepthBlocks = 8;
	/** The stair loses one block this often while the player is in the chamber, and faster once the sentence is written. */
	public double stairLossSeconds = 25;
	public double stairLossFastSeconds = 6;

	// --- the team's stair ---
	public double stairRetrySeconds = 20;
	/** Once a segment of the stair is built, the next one is tried this soon (each is its own out-of-view batch). */
	public double stairSegmentSeconds = 1;
	/** Shaft levels built per batch (each batch is one view check, all or nothing). */
	public int stairSegmentLevels = 16;
	/** Half width of the bedrock chamber around the stair's axis (3: a 7x7 room). */
	public int chamberHalfWidth = 3;
	/** Air above F30's twin in the chamber. */
	public int chamberHeadroom = 3;

	// --- the last minute ---
	/** The silence before the first stair block comes back. */
	public double footstepLeadSeconds = 1.5;
	public int footstepTicks = 8;
	/** A stair block that stays in view this long is left for the afterward. */
	public double footstepGiveUpSeconds = 10;
	public double torchDelaySeconds = 2;
	/** Move the clock forward so dawn breaks about this long after the last plank (the climb), never backward. */
	public boolean forceDawn = true;
	public double dawnLeadSeconds = 50;
	/** The silence lasts until the music; this is only its upper bound. */
	public double silenceSeconds = 900;
	public int silenceFadeTicks = 80;
	/** Give up waiting for the player to come out after this long (the rest of the minute still plays). */
	public double climbTimeoutSeconds = 900;
	/** Where he stands: this fraction of the player's full render distance (inside the ticking range). */
	public double figureDistanceFraction = 0.8;
	public double figureStareSeconds = 6;
	public double figureTimeoutSeconds = 120;
	/** The leaf wave: blocks placed per tick, and how long it may wait for each block to be out of view. */
	public int leafWavePerTick = 16;
	public double leafWaveTimeoutSeconds = 180;
	public double musicDelaySeconds = 4;

	// --- afterward ---
	public int undoPerTick = 4;
	public int undoIntervalTicks = 2;
	/** Far chunks the undo starts loading per tick, how many it holds at once, and how long it holds one at most. */
	public int chunkLoadsPerTick = 2;
	public int undoMaxClusters = 64;
	public double undoClusterTimeoutSeconds = 120;
	/** A full pass over the ledger that changed nothing waits this long before the next one. */
	public double undoRestSeconds = 30;
	public double regrowIntervalSeconds = 2;

	// --- the sting ---
	public boolean carversOff = true;
	public boolean noiseCavesOff = true;

	public static EndingDConfig get() {
		return ModConfig.section("ending_d", EndingDConfig.class, EndingDConfig::new);
	}

	/** Seconds to ticks, through {@link ModConfig#realTicks} (at least 1). */
	public static long ticks(double seconds) {
		return Math.max(1, ModConfig.realTicks(seconds));
	}
}
