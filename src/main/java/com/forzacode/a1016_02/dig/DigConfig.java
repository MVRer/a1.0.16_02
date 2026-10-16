package com.forzacode.a1016_02.dig;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * The dig workstream's tunables, stored under {@code sections.dig} in {@code config/a1016_02.json}. Shared rules
 * (dig clearance, breach distance, tunnel growth per visit, torches-behind distance) come from {@code Pacing}.
 * Real-time values are in seconds or minutes and go through {@link ModConfig#realTicks(double)}.
 * <p>
 * Versions (D-075, {@link ModConfig.Versioned}): 1 is v1.2, which only added the tunnel ladder's fields (D-072); a
 * file without them reads their defaults, so step 0 to 1 changes no value. v1.3's blind moments (D-077) are new fields
 * only, without a step. 2 is v1.3's playtest fixes (FIX-GLIMPSE): louder steps ({@link #ladderVolumeFar} 0.7 to 1.5,
 * {@link #ladderVolumeNear} 0.9 to 1.0, {@link #ladderGlimpseVolumeMax} 1.3 to 1.8, {@link #blindStepVolume} 0.5 to
 * 1.0) and no other-light rule for {@code blind_torch} ({@link #blindTorchOtherLightRadius} 8 to 0), each only where it
 * still equals v1.3's default; rung 3's darkening fields are new.
 */
public final class DigConfig implements ModConfig.Versioned {
	/** This section's current version. Every later default change adds a step to {@link #upgrade()} and raises it. */
	public static final int CURRENT_VERSION = 2;

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
	/**
	 * Share of those cells whose walls (both sides, at the feet and the head) are sealed. 0.45 lets a strip mine with a
	 * branch every 2 cells count (half its cells open); a tunnel open to a natural cave along it does not.
	 */
	public double ladderMinSealedShare = 0.45;
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
	 * A step's volume when it is {@link #ladderComingFrom} blocks from the player, going to {@link #ladderVolumeNear}
	 * at {@link #ladderComingStopMin}. Vanilla plays a sound at most at full loudness (a volume over 1 only reaches
	 * farther: its range is {@code 16 * max(volume, 1)} blocks) and fades it linearly to nothing at that range, so 1.5
	 * far off (a 24-block range) makes a step 15 back about a third as loud as one at the ear, and 1.0 near makes one 4
	 * back three quarters (at v1.3's 0.7 and 0.9 they were a twentieth and a half: too quiet, FIX-GLIMPSE). Each step
	 * is also held under what a friend outside the shared moment radius could hear ({@code Ladder.stepVolume}), so a
	 * friend 48 away never hears one.
	 */
	public float ladderVolumeFar = 1.5F;
	public float ladderVolumeNear = 1.0F;
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
	/**
	 * Rung 2's fake: a few steps going away, fading out: the first this far back (10 to 13 is well heard), the last
	 * {@link #ladderComingFakeTo} back (at most 14.5, so it is still inside vanilla's 16-block range, and never past
	 * the run's end), evenly between, each quieter down to {@link #ladderComingFakeLastFactor} of a step's volume there.
	 */
	public int ladderComingFakeFrom = 12;
	public int ladderComingFakeTo = 14;
	public int ladderComingFakeSteps = 4;
	public float ladderComingFakeLastFactor = 0.5F;
	/** Rung 3 ({@code steps_behind_glimpse}): only once the last rung 2 is at least this many minutes of play old. */
	public double ladderGlimpseAfterComingMinutes = 20;
	/** Rung 3 at most once per in-game day, and this many hours of the player's own play apart. */
	public double ladderGlimpseGapHours = 2;
	/**
	 * Rung 3's spot: this many blocks back, in the dark: the light a player would see at his feet and at his eyes at
	 * most this (vanilla's raw brightness: the block light, or the sky light less the sky's darkening, so neither the
	 * torchlight nor the daylight down a shaft or in from a tunnel mouth reaches him).
	 */
	public int ladderGlimpseSpotMin = 14;
	public int ladderGlimpseSpotMax = 20;
	public int ladderGlimpseMaxBlockLight = 3;
	/**
	 * Rung 3 makes the dark he appears in (FIX-GLIMPSE, Mariano): before the steps start it takes every light a player
	 * placed (a torch or lantern, soul and copper ones too, never a redstone torch) that lights the tunnel from this many
	 * blocks back to {@link #ladderDarkTo} over {@link #ladderGlimpseMaxBlockLight}, out of every player's view, at
	 * most {@link #ladderGlimpseMaxTorches} of them (0: none), in one silent edit ({@code dig:glimpse_torches}).
	 */
	public int ladderDarkFrom = 6;
	public int ladderDarkTo = 24;
	public int ladderGlimpseMaxTorches = 8;
	/**
	 * Rung 3's steps, coming up the tunnel to the spot (at most a step and a half apart, closer when the spot is far, so
	 * at least three are in hearing range for a spot up to 19 back), all within the range below where they can be.
	 */
	public int ladderGlimpseStepsMin = 4;
	public int ladderGlimpseStepsMax = 7;
	/**
	 * Rung 3's steps play at this volume: vanilla's hearing range is {@code max(volume, 1) * 16}, so 1.8 reaches about
	 * 29 blocks and a step at the spot 14 to 20 back is heard clearly (a third to a half as loud as one at the ear,
	 * louder as they come; v1.3's 1.3 left them a twentieth to a third). Each step is also held under the shared moment
	 * radius, so a friend outside it never hears one.
	 */
	public float ladderGlimpseVolumeMax = 1.8F;

	// --- blind moments: while a menu is open (D-077, v1.3) ---
	/**
	 * Every blind card waits until the player has been blind (a counting screen open, {@code PlayerWatch.blindTicks})
	 * at least this long (seconds): the director's first recheck of a blind moment comes at 1.5 s.
	 */
	public double blindMinSeconds = 1.5;
	/** Never within this long of combat (seconds). */
	public double blindNoCombatSeconds = 30;
	/** {@code blind_step}: one or two steps this many blocks behind the player, against their facing. */
	public int blindStepBackMin = 3;
	public int blindStepBackMax = 5;
	public double blindStepTwoStepsChance = 0.5;
	/** Ticks between the two steps (a walking step), and how late the fake's own step comes. Plain ticks. */
	public int blindStepTicks = 8;
	/**
	 * A step's volume (at most 1, so its range stays vanilla's 16 blocks and nobody outside the alone radius hears it).
	 * 1.0 at 4 blocks is, at the ear, three quarters of full loudness, five times the player's own step (vanilla's 0.15
	 * at their feet); v1.3's 0.5 was too quiet in play (FIX-GLIMPSE).
	 */
	public float blindStepVolume = 1.0F;
	/** {@code blind_torch}: a torch or lantern a player placed, this many blocks from the player. */
	public int blindTorchMinDistance = 6;
	public int blindTorchMaxDistance = 20;
	/**
	 * Only while another light source is within this many blocks of it (the cheap rule for "never the only light of a
	 * spawn-proof room"): a block giving at least {@link #blindTorchOtherLightMin} light. 0 (the default since
	 * FIX-GLIMPSE): no such rule, one torch is enough; v1.3's 8 meant no cave torch ever went (they are spaced farther).
	 */
	public int blindTorchOtherLightRadius = 0;
	/** What counts as a light source for those rules: a block whose light is at least this (a redstone torch is 7). */
	public int blindTorchOtherLightMin = 7;
	/**
	 * Never the last light within this many blocks of a bed: a known player's respawn point (a bed or an anchor), never
	 * their first placed block (in a new world often a cave torch).
	 */
	public int blindTorchBedRadius = 6;
	/** {@code blind_door}: a closed door, trapdoor or fence gate this many blocks from the player, out of view. */
	public int blindDoorMinDistance = 4;
	public int blindDoorMaxDistance = 12;
	/** Ticks between its open and its close sound (someone coming through). Plain ticks. */
	public int blindDoorGapTicksMin = 20;
	public int blindDoorGapTicksMax = 40;
	/** Its sounds' volume (vanilla's door is 1: heard within 16 blocks). */
	public float blindDoorVolume = 1.0F;

	/** D-075: brings an older section up to {@link #CURRENT_VERSION}; values the player changed are kept. */
	@Override
	public boolean upgrade() {
		if (version >= CURRENT_VERSION) {
			return false;
		}
		// 0 to 1 (v1.2): new fields only (the tunnel ladder, D-072), read as their defaults; no old default changed.
		if (version < 2) {
			// 1 to 2 (FIX-GLIMPSE, v1.3's playtest): louder steps and no other-light rule, only where still v1.3's default.
			DigConfig d = new DigConfig();
			ladderVolumeFar = ModConfig.Upgrades.keepOrNew(ladderVolumeFar, 0.7F, d.ladderVolumeFar);
			ladderVolumeNear = ModConfig.Upgrades.keepOrNew(ladderVolumeNear, 0.9F, d.ladderVolumeNear);
			ladderGlimpseVolumeMax = ModConfig.Upgrades.keepOrNew(ladderGlimpseVolumeMax, 1.3F, d.ladderGlimpseVolumeMax);
			blindStepVolume = ModConfig.Upgrades.keepOrNew(blindStepVolume, 0.5F, d.blindStepVolume);
			blindTorchOtherLightRadius = ModConfig.Upgrades.keepOrNew(blindTorchOtherLightRadius, 8, d.blindTorchOtherLightRadius);
		}
		version = CURRENT_VERSION;
		return true;
	}

	public static DigConfig get() {
		return ModConfig.section("dig", DigConfig.class, DigConfig::new);
	}
}
