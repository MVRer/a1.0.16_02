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

	// --- echoes (D-076): your own sounds again, minutes later, where you can't see ---
	/** Never within this many real seconds of the player's own combat. */
	public double echoNoCombatSeconds = 30;
	/** echo_mining: one of the player's bursts at least this many minutes of their play ago (its last break)... */
	public double echoMiningMinAgeMinutes = 5;
	/** ...and at most this many. */
	public double echoMiningMaxAgeMinutes = 30;
	/** The rock it comes from is this far from the player's feet (3D), at least... */
	public int echoMiningMinDistance = 14;
	/** ...and at most this far. */
	public int echoMiningMaxDistance = 24;
	/** At most this many blocks above the player's feet (below or beside them, never overhead). */
	public int echoMiningMaxAbove = 1;
	/** At most this many blocks below the player's feet. */
	public int echoMiningMaxBelow = 18;
	/**
	 * A break's volume. Vanilla's (1 for stone) is heard to 16 blocks only, short of the 14 to 24 the echo comes from;
	 * at 2 it carries to 32 blocks, faint there (vanilla's linear fall-off: about half of full at 14 blocks, a quarter
	 * at 24).
	 */
	public double echoMiningBreakVolume = 2.0;
	/** A hit's volume: heard to 28 blocks, a little fainter than the breaks at every distance of the band (vanilla's hits are a quarter of a break). */
	public double echoMiningHitVolume = 1.75;
	/**
	 * The hits: vanilla's dig time with the block's right tool is {@code 30 * hardness / speed} ticks, a hit every 4.
	 * The player's own gaps give their tool's speed; a block never takes longer than with a tool this slow (2: wood), so
	 * a pause between two blocks is quiet, not more hits.
	 */
	public double echoMiningSlowToolSpeed = 2.0;
	/** The tool speed assumed when the player's gaps tell nothing (6: iron). */
	public double echoMiningToolSpeed = 6.0;
	/**
	 * The fake's steady rhythm: a gap between its breaks from this many ticks (at least 10, so each gap, give or take 2,
	 * is a dig of 2 ticks or more after vanilla's 6-tick lead, never an instant break)...
	 */
	public int echoMiningFakeGapMinTicks = 10;
	/** ...to this many (under 60, the 3 s that make a burst). */
	public int echoMiningFakeGapMaxTicks = 50;
	/** echo_door: a door, trapdoor or gate the player used at least this many minutes of their play ago... */
	public double echoDoorMinAgeMinutes = 5;
	/** ...and at most this many. */
	public double echoDoorMaxAgeMinutes = 60;
	/** It is now this far from the player (3D), at least... */
	public int echoDoorMinDistance = 6;
	/** ...and at most this far. */
	public int echoDoorMaxDistance = 16;
	/** The player's own gap between opening and closing it, if it was at most this many ticks; else a plausible one. */
	public int echoDoorMaxGapTicks = 100;
	/** A plausible gap, from this many ticks... */
	public int echoDoorGapMinTicks = 12;
	/** ...to this many. */
	public int echoDoorGapMaxTicks = 40;
	/**
	 * The door's own sound, louder than vanilla's door (1: heard to 16 blocks, falling off linearly to nothing there, so
	 * the band's far end was silent) so the whole 6 to 16 band carries: at 1.6 it is heard to 25.6 blocks (a volume v
	 * reaches 16v), about three quarters of full at 6 blocks and over a third at 16; anyone else within 25.6 hears it
	 * too (D-060), well inside the shared moment's 48.
	 */
	public double echoDoorVolume = 1.6;

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
	 * A spell counts as "fog now" for every gate on it (the director's boost, daytime sightings: core's
	 * {@code FogSpell.counts}) while its amount is at least this share of its level, so its first and last ticks and a
	 * carry-over's tail do not count.
	 */
	public double fogGateShare = FogWeatherRules.DEFAULT_GATE_SHARE;
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
	 * v1.2 (fog weather, far in the rock) is missing from a v1.1 file and gets its default, as v1.3's echo fields
	 * ({@code echo...}, D-076) do from any older file: new fields, no step.
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
	 * the director's sims and the live weather use this config, and every spell made carries its ramp, fade and exponent
	 * (a client draws a spell with the spell's own ease, never its own config). The ease exponent is the dusk fog's
	 * ({@link #duskFogEaseExponent}); {@link #fogGateShare} is core's gate on "fog now".
	 */
	public FogWeatherRules.Settings fogWeather() {
		return new FogWeatherRules.Settings(perStage(fogNightChanceByStage), fogSecondNightChance, fogClearNightsBeforeFog, fogMorningCarryChance,
				perStage(fogDayChanceByStage), perStage(fogNightLevelByStage), perStage(fogDayLevelByStage), fogSpellQuietShare, fogSpellRampTicks,
				fogSpellFadeTicks, duskFogEaseExponent, fogGateShare);
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
