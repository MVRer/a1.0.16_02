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
	public int waterRadius = 32;
	public int waterMoveMinBlocks = 64;
	public int waterMoveMaxBlocks = 112;
	public int batRadius = 64;
	public int roomSearchRadius = 14;
	public int roomMaxCells = 400;
	public int zombieMinDistance = 16;
	public int zombieRadius = 48;
	public double zombieStillSeconds = 20;

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
		return new FogLimits.Shape(duskMinFogBlocks, duskNightWeight);
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
