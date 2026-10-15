package com.forzacode.a1016_02.atmosphere;

import java.util.List;

import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;

import org.jspecify.annotations.Nullable;

/**
 * Atmosphere tunables, stored under {@code sections.atmosphere} in {@code config/a1016_02.json}. Shared pacing
 * (silence length, fog drift length, mining in the dark, chest radius) stays in {@code Pacing}. Fields named
 * {@code ...Seconds} are real time and go through {@link ModConfig#realTicks}; fields named {@code ...Ticks} shape a
 * single sound or animation, or run on the game clock (the fog spell's ease), and are never divided by
 * {@code devFastMode}.
 *
 * <p>Versioned (D-075): {@link #version} is the section's version, 0 in a file from v1.1 or older; {@link #upgrade}
 * brings it up one step at a time, replacing only values that still equal the old default.
 */
public final class AtmosphereConfig implements ModConfig.Versioned {
	/** The section's current version. 1: v1.2 (D-070's cave sound radius and fog drift fade). */
	public static final int VERSION = 1;

	/** The version this section was last written at (0: v1.1 or older; the field's default stays 0). */
	public int version = 0;

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
	/** v1.1: 100. A slower way back, so a drift reads as weather, not a glitch (D-070). */
	public int fogDriftFadeTicks = 160;
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
	/** On the surface the player must stand this still; underground no stillness is needed (D-070). */
	public double caveSoundStillSeconds = 8;
	/** v1.1: 48. Still past the sound's own 21-block hearing range, so a friend never hears the player's cave sound. */
	public int caveSoundAloneRadius = 24;

	// --- far in the rock ---
	/** No other participant within this many blocks of the player. */
	public int rockSoundAloneRadius = 24;
	/** The sound is this far from the player (3D), at least... */
	public int rockSoundMinDistance = 18;
	/** ...and at most this far. */
	public int rockSoundMaxDistance = 28;
	/** At most this many blocks above the player's feet (below or to the side, never overhead). */
	public int rockSoundMaxAbove = 1;
	/** At most this many blocks below the player's feet. */
	public int rockSoundMaxBelow = 14;
	/**
	 * Its volume. Over 1 widens the hearing range ({@code 16 * volume} blocks), which a sound 18 to 28 blocks off needs
	 * to be heard at all: at 2 the range is 32 blocks, so it is faint there (vanilla's linear fall-off: about a third
	 * of full at 18 blocks, an eighth at 28).
	 */
	public double rockSoundVolume = 2.0;
	public double rockSoundPitchMin = 0.7;
	public double rockSoundPitchMax = 0.95;
	/** Share of the stone and deepslate sounds that are a hit (the rest a break). Gravel always slides. */
	public double rockSoundHitShare = 0.65;

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

	// --- fog weather (D-071): fog nights, morning carry-overs, fog days ---
	/** Chance of a fog night per stage (0 Alone to 4 Removal), from the fourth night on. */
	public double[] fogNightChanceByStage = {0.25, 0.33, 0.4, 0.45, 0.5};
	/** Chance that the second night is a fog night (the first never is; the third is if neither before it was). */
	public double fogSecondNightChance = 0.5;
	/** After this many clear nights in a row the next one is a fog night. */
	public int fogClearNightsBeforeFog = 4;
	/** Share of fog nights that carry into a grey morning (to 5500). */
	public double fogMorningCarryChance = 0.35;
	/** Chance of a fog day per stage, per day not already covered (never in Alone, never two in a row). */
	public double[] fogDayChanceByStage = {0.0, 0.06, 0.1, 0.12, 0.12};
	/** A fog night's level per stage, on the dusk fog's scale (at a 12-chunk view 0.45 ends the fog at about 75 blocks, 0.8 at 37). */
	public double[] fogNightLevelByStage = {0.45, 0.55, 0.65, 0.72, 0.8};
	/** A fog day's level per stage. */
	public double[] fogDayLevelByStage = {0.4, 0.45, 0.55, 0.6, 0.65};
	/** Share of spells that are quiet (no director boost), so a fog night is never a sure sign. */
	public double fogSpellQuietShare = 0.25;
	/** A spell eases in over this many game ticks from its start, with the dusk fog's ease ({@link #duskFogEaseExponent}). */
	public long fogSpellRampTicks = 2500;
	/**
	 * And out over this many before its end. Tonight is rolled this long before a fog day ends (10000 by default), so a
	 * fog day never thins before it hands over to a fog night.
	 */
	public long fogSpellFadeTicks = 1500;
	/**
	 * The spell amount from which the close-in haze (vanilla's faint far haze, pulled in to the fog end) is whole; less
	 * gets that share of it, so the haze comes and goes over the spell's ease (lower brings it in and takes it away
	 * faster). Only noticeable by day, where the day levels (0.4 to 0.65) get two thirds to all of it: at night the
	 * stage's dusk fog already has the whole haze.
	 */
	public double fogSpellHazeFullAmount = 0.6;
	/** At night the fog color is lifted from vanilla's near black toward this color... */
	public String fogNightColor = "#3b4048";
	/** ...by this share times the spell's amount, so a fog night shows as a pale wall in the dark. */
	public double fogNightColorLift = 0.6;
	/** By day the fog color is desaturated toward this grey by the spell's amount. */
	public String fogDayColor = "#b9bec4";

	// --- MobTamper ---
	/** How fast a frozen mob turns to face its point, in degrees per tick. */
	public float tamperTurnDegreesPerTick = 7.0F;

	private transient @Nullable String parsedNightColor;
	private transient int nightRgb;
	private transient @Nullable String parsedDayColor;
	private transient int dayRgb;

	public static AtmosphereConfig get() {
		return ModConfig.section("atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
	}

	/**
	 * D-075: one step at a time from {@link #version}. 0 to 1 (v1.2, D-070): {@code caveSoundAloneRadius} 48 to 24 and
	 * {@code fogDriftFadeTicks} 100 to 160, each only where it still equals the old default. Everything else new in
	 * v1.2 (fog weather, far in the rock) is missing from a v1.1 file and gets its default.
	 */
	@Override
	public boolean upgrade() {
		if (version >= VERSION) {
			return false;
		}
		AtmosphereConfig d = new AtmosphereConfig();
		if (version < 1) {
			caveSoundAloneRadius = ModConfig.Upgrades.keepOrNew(caveSoundAloneRadius, 48, d.caveSoundAloneRadius);
			fogDriftFadeTicks = ModConfig.Upgrades.keepOrNew(fogDriftFadeTicks, 100, d.fogDriftFadeTicks);
		}
		version = VERSION;
		return true;
	}

	/**
	 * The fog weather numbers for core's {@link FogWeatherRules} (rolls, levels, the spell's ease), installed live by
	 * {@code AtmosphereInit} ({@code FogWeatherRules.install(() -> get().fogWeather())}): the server's {@code FogLimits},
	 * the director's sims, the live weather and the client fog all use this config. The ease exponent is the dusk
	 * fog's ({@link #duskFogEaseExponent}).
	 */
	public FogWeatherRules.Settings fogWeather() {
		return new FogWeatherRules.Settings(perStage(fogNightChanceByStage), fogSecondNightChance, fogClearNightsBeforeFog, fogMorningCarryChance,
				perStage(fogDayChanceByStage), perStage(fogNightLevelByStage), perStage(fogDayLevelByStage), fogSpellQuietShare, fogSpellRampTicks,
				fogSpellFadeTicks, duskFogEaseExponent);
	}

	private static List<Double> perStage(double @Nullable [] values) {
		return values == null ? List.of() : FogWeatherRules.perStage(values);
	}

	/** {@link #fogNightColor} as {@code 0xRRGGBB} ({@code #3b4048} if it does not parse). */
	public int fogNightRgb() {
		String color = fogNightColor;
		if (color == null || !color.equals(parsedNightColor)) {
			nightRgb = rgb(color, 0x3b4048);
			parsedNightColor = color;
		}
		return nightRgb;
	}

	/** {@link #fogDayColor} as {@code 0xRRGGBB} ({@code #b9bec4} if it does not parse). */
	public int fogDayRgb() {
		String color = fogDayColor;
		if (color == null || !color.equals(parsedDayColor)) {
			dayRgb = rgb(color, 0xb9bec4);
			parsedDayColor = color;
		}
		return dayRgb;
	}

	/** {@code #rrggbb} (or {@code rrggbb}) as {@code 0xRRGGBB}; {@code fallback} for anything else. */
	public static int rgb(@Nullable String hex, int fallback) {
		if (hex == null) {
			return fallback;
		}
		String digits = hex.strip();
		if (digits.startsWith("#")) {
			digits = digits.substring(1);
		}
		if (digits.length() != 6) {
			return fallback;
		}
		try {
			return Integer.parseInt(digits, 16);
		} catch (NumberFormatException e) {
			return fallback;
		}
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
