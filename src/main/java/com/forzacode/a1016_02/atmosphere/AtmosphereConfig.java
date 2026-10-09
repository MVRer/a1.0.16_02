package com.forzacode.a1016_02.atmosphere;

import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;

/**
 * Atmosphere tunables, stored under {@code sections.atmosphere} in {@code config/a1016_02.json}. Shared pacing
 * (silence length, fog drift length, mining in the dark, chest radius) stays in {@code Pacing}. Fields named
 * {@code ...Seconds} are real time and go through {@link ModConfig#realTicks}; fields named {@code ...Ticks} shape a
 * single sound or animation and are never divided by {@code devFastMode}.
 */
public final class AtmosphereConfig {
	// --- dusk fog and the client fog shape ---
	/** Dusk fog level per stage (0 Alone to 4 Removal), 0 = vanilla, 1 = heaviest. Stage 0 is "easy to blame on settings". */
	public float[] duskFogByStage = {0.15F, 0.3F, 0.45F, 0.6F, 0.7F};
	/** Fog end distance in blocks at dusk level 1. */
	public double duskMinFogBlocks = 24;
	/** Share of the dusk fog that stays through the night (it peaks at dusk). */
	public double duskNightWeight = 0.55;
	/**
	 * When the dusk fog starts building (time of day: 0 is sunrise, 12000 sunset, 24000 the next sunrise). It eases in
	 * so slowly ({@link #duskFogEaseExponent}) that the first minutes are hardly there.
	 */
	public long duskFogStart = 9000;
	/** When it is full (deep dusk). */
	public long duskFogPeak = 13000;
	/** It stays full until here... */
	public long duskFogHoldUntil = 13800;
	/** ...then eases to {@link #duskNightWeight}, reached here. */
	public long duskFogNightFrom = 16000;
	/** The night fog starts fading here... */
	public long duskFogFadeFrom = 19500;
	/** ...and is gone here, before sunrise. The fade is the rise played backwards. */
	public long duskFogEnd = 23500;
	/**
	 * How late the dusk fog's eases happen ({@code x^p * (p + 1 - p * x)}, 1 to 8): 2 is a smoothstep, 3 starts like a
	 * cubic (a fifth of the way in, 3% of the fog), higher waits even longer and then comes faster.
	 */
	public double duskFogEaseExponent = 3.0;
	/** Real seconds over which the client eases to a new dusk fog level. The first level after joining applies at once. */
	public double duskLevelChangeSeconds = 90;
	/**
	 * Dusk level from which the close-in haze is whole (every stage level is). A level below it gets that share of the
	 * haze, so fog arriving from level 0 brings its haze gradually.
	 */
	public double duskHazeFullLevel = 0.15;
	/** Fog end distance in blocks at fog surge strength 1. */
	public double surgeMinFogBlocks = 10;
	/** Fog start as a share of the fog end when the fog is at its heaviest (the old close-in fog). */
	public double heavyFogStartFraction = 0.15;

	// --- fog drift (D-022: 0.85 was too strong in playtests, 0.7 at most) ---
	public double fogDriftStrengthMin = 0.45;
	public double fogDriftStrengthMax = 0.7;
	public int fogDriftRampTicks = 16;
	public int fogDriftFadeTicks = 100;
	public double fogDriftNoCombatSeconds = 30;
	/** In daylight only this share of draws goes ahead ("rarer in daylight"). */
	public double fogDriftDaylightChance = 0.35;

	// --- silence ---
	public double silenceFadeSeconds = 20;

	// --- animals facing the fog ---
	public int animalsRadius = 32;
	public int animalsMin = 2;
	public int animalsPointDistance = 52;
	public double animalsMaxSeconds = 180;
	/** Walking this many blocks toward the point releases them. */
	public double animalsReleaseBlocks = 6;
	public int animalsStaggerTicks = 30;
	/** The false positive: one animal looks off into the fog for this long (a random length in between). */
	public double animalsFakeMinSeconds = 3;
	public double animalsFakeMaxSeconds = 6;

	// --- distant cave sound ---
	public double caveSoundStillSeconds = 8;
	public int caveSoundAloneRadius = 48;

	// --- mining in the dark (timing gates are in Pacing) ---
	public int miningMinDistance = 6;
	public int miningMaxDistance = 13;

	// --- chest opens ---
	public double chestStackChance = 0.35;
	public int chestCloseMinTicks = 18;
	public int chestCloseMaxTicks = 40;

	// --- door left open ---
	public int doorSearchRadius = 48;
	public int doorAwayBlocks = 24;

	// --- one block missing ---
	public int houseRadius = 14;
	public int houseAwayBlocks = 12;

	// --- footstep that stops late ---
	public double footstepArmSeconds = 120;
	public int footstepMinWalkTicks = 20;
	public int footstepDelayMinTicks = 6;
	public int footstepDelayMaxTicks = 12;

	// --- compass drift ---
	public double compassDriftSeconds = 60;
	public int compassDriftMinBlocks = 90;
	public int compassDriftMaxBlocks = 170;
	/** The needle settles back once the player is this close to where it pointed. */
	public int compassSettleBlocks = 48;

	// --- night (by the clock, not by sky darkness: a daytime thunderstorm is not night) ---
	public long nightFrom = 13000;
	public long nightTo = 23000;

	// --- mobs acting wrong ---
	public int deadMountainSearchRadius = 192;
	public int cowSearchRadius = 96;
	public double cowStandSeconds = 120;
	/** The cow on a dead mountain is moved at least this far from the player (blocks). */
	public int cowMinDistance = 24;
	public int villagerRadius = 48;
	public long noonFrom = 4500;
	public long noonTo = 7500;
	public double villagerWindowSeconds = 90;
	public int dogRadius = 24;
	public double dogStaySeconds = 30;
	public int catRadius = 16;
	public double catHissSeconds = 8;
	public long duskFrom = 11800;
	public long duskTo = 13600;
	public int skeletonMinDistance = 20;
	public int skeletonRadius = 48;
	public double skeletonFollowSeconds = 75;
	/** After following, it keeps trying this long to be gone (moved far off, out of view) before it is simply released. */
	public double skeletonGoneTriesSeconds = 30;
	/**
	 * The skeleton's freeze, face and silence lease, renewed every 10 ticks while it follows. A tamper lease, not
	 * pacing: never divided by devFastMode, and at least 20 so it outlasts the renewal.
	 */
	public int skeletonHoldTicks = 40;
	public int waterRadius = 32;
	public int waterMoveMinBlocks = 64;
	public int waterMoveMaxBlocks = 112;
	public int batRadius = 64;
	public int roomSearchRadius = 14;
	public int roomMaxCells = 400;
	public int zombieMinDistance = 16;
	public int zombieRadius = 48;
	public double zombieStillSeconds = 20;
	/** The false positive (a zombie only looking at you) lasts a quarter of {@code zombieStillSeconds}, at least this long. */
	public double zombieFakeMinSeconds = 2;

	// --- dead mountains: quieter than anywhere else, and no animals ---
	/** Dead mountains whose edge is within this many blocks of a player are sent to their client (on every chunk change). */
	public int deadMountainSendBlocks = 160;
	/** Ticks for ambience and music to fade out once the player stands on a dead mountain. */
	public int deadMountainFadeOutTicks = 80;
	/** Ticks for them to come back after leaving it. */
	public int deadMountainRestoreTicks = 200;
	/** Natural passive spawns (animals, bats, fish) are refused inside dead mountains. Never removes a mob. */
	public boolean deadMountainNoAnimals = true;

	// --- MobTamper ---
	/** How fast a frozen mob turns to face its point, in degrees per tick. */
	public float tamperTurnDegreesPerTick = 7.0F;

	public static AtmosphereConfig get() {
		return ModConfig.section("atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
	}

	/** The dusk fog shape for core's {@link FogLimits}, installed live by {@code AtmosphereInit} ({@code FogLimits.installShape(() -> get().fogShape())}). */
	public FogLimits.Shape fogShape() {
		return new FogLimits.Shape(duskMinFogBlocks, duskNightWeight, duskFogStart, duskFogPeak, duskFogHoldUntil, duskFogNightFrom, duskFogFadeFrom,
				duskFogEnd, duskFogEaseExponent);
	}

	public float duskFogFor(Stage stage) {
		if (duskFogByStage == null || duskFogByStage.length == 0) {
			return 0.0F;
		}
		return duskFogByStage[Math.min(stage.level(), duskFogByStage.length - 1)];
	}

	public static int ticks(double seconds) {
		return (int) Math.min(Integer.MAX_VALUE, ModConfig.realTicks(seconds));
	}
}
