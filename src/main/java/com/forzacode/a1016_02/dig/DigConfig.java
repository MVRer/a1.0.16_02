package com.forzacode.a1016_02.dig;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * The dig workstream's tunables, stored under {@code sections.dig} in {@code config/a1016_02.json}. Shared rules
 * (dig clearance, breach distance, tunnel growth per visit, torches-behind distance) come from {@code Pacing}.
 * Real-time values are in seconds or minutes and go through {@link ModConfig#realTicks(double)}.
 * <p>
 * Versions (D-075, {@link ModConfig.Versioned}): 1 is v1.2, which only added the tunnel ladder's fields (D-072); a
 * file without them reads their defaults, so step 0 to 1 changes no value.
 */
public final class DigConfig implements ModConfig.Versioned {
	/** This section's current version. Every later default change adds a step to {@link #upgrade()} and raises it. */
	public static final int CURRENT_VERSION = 1;

	/** The version this section was last written at; missing (0) is v1.1 or older. */
	public int version = 0;

	// --- "Under you": the network under the base ---
	/** Tunnel length (2x2 steps) the network grows per in-game night. */
	public int networkBlocksPerNight = 6;
	/** Nights a base must have existed (and its player been at home there) before a network starts under it. */
	public int networkStartAfterNights = 1;
	/**
	 * Bases within this distance of a network's base share that network (friends in one house get one network); a
	 * base farther from every network gets its own (blocks).
	 */
	public int networkShareDistance = 64;
	/**
	 * At most this many networks in use per world: one counts while it is under some owner's base. A network all its
	 * players moved away from (a camp bed slept in for a night) no longer counts, so a new base always gets one.
	 */
	public int networkMaxPerWorld = 6;
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
	/**
	 * A player's base that moves farther than this is a new base (its nights count again before a network starts under
	 * it); stacks taken from chests this close to a network's base go into that network's chest.
	 */
	public int networkBaseMoveDistance = 64;
	/** Run length of a corridor before it turns (straight runs, rare turns). */
	public int networkRunMin = 6;
	public int networkRunMax = 14;
	/** Chance that a corridor branches when it turns. */
	public double networkBranchChance = 0.35;
	public int networkMaxHeads = 4;
	/** Chests he can take for the network: at least this far from every player and from the base. */
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
	/** Every participant has left a mine this far away. */
	public int intoMineMinDistance = 32;
	public int demoTunnelLength = 16;

	// --- torches ---
	public int torchesGoneMinDistance = 48;
	public int torchesGoneMax = 8;
	public int torchesGoneSearchRadius = 12;
	public double torchesBehindSessionMinutes = 10;
	public double torchesBehindGapSeconds = 6;
	public int torchesBehindMinTorches = 3;
	/** Torches gone: only after this long without combat (seconds). */
	public double torchesGoneNoCombatSeconds = 10;

	// --- mining that moves ---
	public int miningStartDistance = 24;
	public int miningSteps = 10;
	public double miningStepSeconds = 1.6;
	/** Mining that moves: only after this long without combat (seconds). */
	public double miningNoCombatSeconds = 30;

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
	/** The footstep under you: only once the player has stood still this long (seconds). */
	public double underYouStepStillSeconds = 3;

	// --- trigger and sampling ---
	/**
	 * ENTERED_TUNNEL fires at most once per this many real minutes of a player's own play, and once per occasion:
	 * everyone sharing the moment (D-060) shares the cooldown.
	 */
	public double enteredTunnelCooldownMinutes = 10;
	/** Explored-cave sampling: a new point when the player is this far from the last one. */
	public int exploredSpacing = 3;
	public int exploredMaxPerDimension = 60_000;
	public int plantedMaxPerDimension = 4_000;

	// --- someone behind you: the tunnel ladder (D-072) ---
	/** Cells of straight player-dug 2-high tunnel the player must have behind them, along their heading. */
	public int ladderMinRun = 16;
	/** Share of those cells whose walls (both sides, at the feet and the head) are sealed: "most cells". */
	public double ladderMinSealedShare = 0.6;
	/** At most one ladder event per this many minutes of the player's own play. */
	public double ladderGapMinutes = 25;
	/** Never within this long of combat (seconds). */
	public double ladderNoCombatSeconds = 30;
	/** A tunnel run whose start is within this many blocks of one already used never fires again for that player. */
	public int ladderUsedRunRadius = 24;
	/** Walking cadence: ticks between two steps (vanilla walking makes a step every 7 to 8 ticks). Plain ticks. */
	public int ladderStepTicks = 8;
	/** The sneaking variant of the coming steps: ticks between two steps, its share of the fires, its volume factor. */
	public int ladderSneakStepTicks = 13;
	public double ladderSneakChance = 0.25;
	public float ladderSneakVolumeFactor = 0.6F;
	/**
	 * A step's volume when it is {@link #ladderComingFrom} blocks from the player, rising to {@link #ladderVolumeNear}
	 * at {@link #ladderComingStopMin}. Never above 1 (it is clamped), so a step's hearing range stays vanilla's 16
	 * blocks and a friend 48 away never hears it. The client fades a sound linearly to nothing at that range, so a step
	 * 12 back at 0.73 is about as loud as the player's own step (vanilla's 0.15 at their feet), one 4 back at 0.9 about
	 * five times as loud.
	 */
	public float ladderVolumeFar = 0.7F;
	public float ladderVolumeNear = 0.9F;
	/** The rest of the steps stop the moment the player looks within this many degrees of the tunnel behind. */
	public double ladderLookBackDegrees = 70;
	/** Rung 1 ({@code steps_behind_once}): one or two steps this many blocks back, then nothing. */
	public int ladderOnceBackMin = 10;
	public int ladderOnceBackMax = 12;
	public double ladderOnceTwoStepsChance = 0.5;
	/**
	 * Rung 2 ({@code steps_behind_coming}): steps from this far back ("about 16": a step 16 away is at the very edge of
	 * vanilla's range and is never heard) closing to 4 to 6 blocks, then silence.
	 */
	public int ladderComingFrom = 15;
	public int ladderComingStopMin = 4;
	public int ladderComingStopMax = 6;
	public int ladderComingStepsMin = 6;
	public int ladderComingStepsMax = 10;
	/** Rung 2's fake: a few steps from this far back going away, fading out. */
	public int ladderComingFakeFrom = 12;
	public int ladderComingFakeSteps = 4;
	/** Rung 3 ({@code steps_behind_glimpse}): only once the last rung 2 is at least this many minutes of play old. */
	public double ladderGlimpseAfterComingMinutes = 20;
	/** Rung 3 at most once per in-game day, and this many hours of the player's own play apart. */
	public double ladderGlimpseGapHours = 2;
	/** Rung 3's spot: this many blocks back, past the torchlight (block light at his feet and eyes at most this). */
	public int ladderGlimpseSpotMin = 14;
	public int ladderGlimpseSpotMax = 20;
	public int ladderGlimpseMaxBlockLight = 3;
	/**
	 * Rung 3's steps, coming up to the spot a step and a half apart (a slow walk). Only those within vanilla's 16-block
	 * range are heard, so the nearest dark spot is taken first.
	 */
	public int ladderGlimpseStepsMin = 4;
	public int ladderGlimpseStepsMax = 7;

	/** D-075: brings an older section up to {@link #CURRENT_VERSION}; values the player changed are kept. */
	@Override
	public boolean upgrade() {
		if (version >= CURRENT_VERSION) {
			return false;
		}
		// 0 to 1 (v1.2): new fields only (the tunnel ladder, D-072), read as their defaults; no old default changed.
		version = CURRENT_VERSION;
		return true;
	}

	public static DigConfig get() {
		return ModConfig.section("dig", DigConfig.class, DigConfig::new);
	}
}
