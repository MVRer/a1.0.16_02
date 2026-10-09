package com.forzacode.a1016_02.dig;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * The dig workstream's tunables, stored under {@code sections.dig} in {@code config/a1016_02.json}. Shared rules
 * (dig clearance, breach distance, tunnel growth per visit, torches-behind distance) come from {@code Pacing}.
 * Real-time values are in seconds or minutes and go through {@link ModConfig#realTicks(double)}.
 */
public final class DigConfig {
	// --- "Under you": the network under the base ---
	/** Tunnel length (2x2 steps) the network grows per in-game night. */
	public int networkBlocksPerNight = 6;
	/** Nights a base must have existed before the network starts under it. */
	public int networkStartAfterNights = 1;
	/** Corridor depth below the base (blocks), unless the player's digs force it deeper. */
	public int networkDepthBelowBase = 12;
	/** How far the network spreads from the base (horizontal, blocks). */
	public int networkRadius = 32;
	/** Missed nights (sleeping, timewarp, time away) that are grown at once, at most. */
	public int networkMaxCatchUpNights = 7;
	/** Growth that can wait for the base to be loaded and out of view, in steps. */
	public int networkMaxBudget = 60;
	/** Blocks the network keeps from caves the player explored. */
	public int networkExploredClearance = 8;
	/** Nights of growth before the shaft under the bed starts. */
	public int networkShaftAfterNights = 4;
	/** Nights of growth before the dead end for the chest is dug. */
	public int networkChestAfterNights = 3;
	/** A base that moves farther than this starts a new network. */
	public int networkBaseMoveDistance = 64;
	/** Run length of a corridor before it turns (straight runs, rare turns). */
	public int networkRunMin = 6;
	public int networkRunMax = 14;
	/** Chance that a corridor branches when it turns. */
	public double networkBranchChance = 0.35;
	public int networkMaxHeads = 4;
	/** Chests he can take for the network: at least this far from the player and the base. */
	public int chestSourceMinDistance = 96;
	/** How far from the base he looks for a chest at a recorded site. */
	public int chestSourceSiteRadius = 1000;
	/**
	 * Stacks taken from the base's chests before the network had its chest (still in the ledger) that are moved into
	 * it per in-game night, oldest first, out of view.
	 */
	public int networkStacksRestoredPerNight = 2;

	// --- card tunnels ---
	/** Solid blocks a card tunnel keeps from anything the player dug or placed (unless the card breaks in). */
	public int tunnelPlayerClearance = 2;
	public int tunnelRunMin = 8;
	public int tunnelRunMax = 20;
	/** The tunnel that grows starts this far from the base (horizontal). */
	public int growingStartMin = 40;
	public int growingStartMax = 72;
	/** It stops when its end is this close to the base (horizontal), or when the next step would reach the player's spaces. */
	public int growingStopShortOfBase = 8;
	/** Being this close to the tunnel counts as a visit; this far away counts as gone. */
	public int growingVisitRadius = 12;
	public int growingLeaveDistance = 48;
	/** In-game days between two growths. */
	public int growingMinDaysBetween = 1;
	public int intoMineLengthMin = 12;
	public int intoMineLengthMax = 24;
	/** The player has left a mine this far away. */
	public int intoMineMinDistance = 32;
	public int demoTunnelLength = 16;

	// --- torches ---
	public int torchesGoneMinDistance = 48;
	public int torchesGoneMax = 8;
	public int torchesGoneSearchRadius = 12;
	public double torchesBehindSessionMinutes = 10;
	public double torchesBehindGapSeconds = 6;
	public int torchesBehindMinTorches = 3;

	// --- mining that moves ---
	public int miningStartDistance = 24;
	public int miningSteps = 10;
	public double miningStepSeconds = 1.6;

	// --- trees ---
	public int treesAwayDistance = 64;
	public int treesPerFire = 3;
	public int saplingScanRadius = 48;
	public double saplingScanSeconds = 30;

	// --- home cues ---
	/** The player counts as at home within this many blocks of the base. */
	public int homeRadius = 24;
	/** A network cell must be within this many blocks of the player for a home cue. */
	public int homeCueReach = 20;
	public float underYouSoundVolume = 0.5F;
	public float underYouStepVolume = 0.35F;

	// --- trigger and sampling ---
	/** ENTERED_TUNNEL fires at most once per this many real minutes. */
	public double enteredTunnelCooldownMinutes = 10;
	/** Explored-cave sampling: a new point when the player is this far from the last one. */
	public int exploredSpacing = 3;
	public int exploredMaxPerDimension = 60_000;
	public int plantedMaxPerDimension = 4_000;

	public static DigConfig get() {
		return ModConfig.section("dig", DigConfig.class, DigConfig::new);
	}
}
