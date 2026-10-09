package com.forzacode.a1016_02.core;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.RandomSource;

/**
 * Every shared timing and distance rule (DESIGN.md "The director", 4b, Triggers, and the related rules), stored
 * under {@code pacing} in {@code config/a1016_02.json}. Fields are in natural units (seconds, minutes, hours,
 * in-game days, blocks). Read real-time values through the {@code ...Ticks()} accessors: they apply
 * {@code devFastMode}. In-game days are never divided; use {@code /a1016 timewarp} for those.
 */
public final class Pacing {
	/** A range of ticks. */
	public record TickRange(long min, long max) {
		public long pick(RandomSource random) {
			return max <= min ? min : Math.min(max, min + (long) (random.nextDouble() * (max - min + 1)));
		}
	}

	// --- director loop (D-011) ---
	public double directorTickSeconds = 30;
	public double joinGraceMinutes = 5;
	/** No major event while the in-game day is below this (day 0 is the first day). */
	public int noMajorBeforeDay = 1;
	public double minorGapMinutes = 15;
	public double majorGapMinutes = 60;
	public double fakeChance = 1.0 / 3.0;

	// --- tempo: paceFactor = 1 + tempoShift * direction (0.6 / 1.0 / 1.4) ---
	public double tempoShift = 0.4;

	// --- stages by real play time (hours, before paceFactor) ---
	public double tracesStartMinHours = 1;
	public double tracesStartMaxHours = 3;
	public double proximityStartMinHours = 5;
	public double proximityStartMaxHours = 6;
	public int aloneMaxAmbient = 1;
	public double tracesAmbientPerHour = 1;
	public double proximityMinorsPerHourMin = 1;
	public double proximityMinorsPerHourMax = 2;
	public double proximityMajorEveryHoursMin = 2;
	public double proximityMajorEveryHoursMax = 4;
	public double firstAccidentMinHours = 6;
	/** From Traces on, some whole sessions contain nothing. */
	public double emptySessionMinMinutes = 45;
	/**
	 * Chance per session, from Traces on, that the whole session stays empty: about 1 in 5. With the forced quiets
	 * this leaves about 40% of Proximity silent in the 20 h dry runs (it was about half at 0.25).
	 */
	public double emptySessionChance = 0.2;

	// --- tension and quiet ---
	public double tensionAmbient = 5;
	public double tensionMinor = 12;
	public double tensionMajor = 30;
	public double tensionSignature = 50;
	/**
	 * Tension that forces a quiet: about 2 h of an active Proximity (a major, three minors and a few ambients). Tuned with the
	 * multi-seed dry runs (P1-1c) so Proximity lands at the low end of 4b: about 0.8 minors per hour overall
	 * (1.3 while active) and a major every 2.3 h (early), 3.7 h (slow burn) and 5.3 h (very late).
	 */
	public double tensionThreshold = 70;
	public int quietMinDays = 1;
	public int quietMaxDays = 4;
	/** Tension lost per real hour of play outside quiet. */
	public double tensionDecayPerHour = 5;
	/**
	 * Pity timer: after this much real play time without any fire (quiets and empty sessions count), the ambient
	 * and minor odds rise by {@code pityBonusPerHour} per hour, up to {@code pityMaxBonus} (+100% = double), so
	 * something always comes back after a long silence.
	 */
	public double pityStartHours = 1;
	public double pityBonusPerHour = 0.25;
	public double pityMaxBonus = 1.0;

	// --- sightings ---
	// sightingMinDistance and stareSeconds are retired: the figure's distances and stare live in the entity section
	// (D-035). Old config files that still have them load fine; the keys are skipped and dropped on the next save.
	public int sightingMinSpacing = 300;
	public int sightingsPerDayMax = 1;

	// --- mining in the dark ---
	public double miningDarkStillSeconds = 20;
	public double miningDarkSoundGapMinutes = 10;
	public int miningDarkPerNight = 1;

	// --- scars, world and dig ---
	public int newScarAwayDays = 2;
	public int chestRadius = 16;
	public int torchesBehindDistance = 40;
	public int mountainLightMinDistance = 100;
	public double silenceSeconds = 60;
	public double fogDriftMinSeconds = 3;
	public double fogDriftMaxSeconds = 8;
	public int digBelow = 3;
	public int breachWithin = 2;
	public int tunnelGrowthPerVisit = 10;
	public int houseCopyMinDay = 20;
	public int oldScarMinFromSpawn = 300;

	// --- triggers and telling ---
	public int disc13BelowY = 50;
	public int tellingRadius = 32;
	public int lowRenderDistanceChunks = 8;
	public int obeyDays = 3;

	// --- endings ---
	public int endingBMarkedDeaths = 3;
	public int endingAQuietMinDays = 5;
	public int endingAQuietMaxDays = 7;

	// --- core rules: out-of-view check (D-012) and player watch ---
	public double viewNearBlocks = 3;
	public double viewConeDegrees = 160;
	public double watchSampleSeconds = 5;
	public int visitRadiusChunks = 1;
	public int footprintMaxPerDimension = 200_000;

	/** Weight per {@link AttentionTrigger}, by name. Missing entries are filled with the defaults. */
	public Map<String, Double> attentionWeights = new LinkedHashMap<>();

	public Pacing() {
		fillMissingWeights();
	}

	void fillMissingWeights() {
		if (attentionWeights == null) {
			attentionWeights = new LinkedHashMap<>();
		}
		for (AttentionTrigger trigger : AttentionTrigger.values()) {
			attentionWeights.putIfAbsent(trigger.name(), trigger.defaultWeight());
		}
	}

	public double attentionWeight(AttentionTrigger trigger) {
		return attentionWeights.getOrDefault(trigger.name(), trigger.defaultWeight());
	}

	public double paceFactor(Tempo tempo) {
		return 1.0 + tempoShift * tempo.direction();
	}

	// --- real-time accessors (ticks; divided in devFastMode) ---

	public long directorTickTicks() {
		return ModConfig.realTicks(directorTickSeconds);
	}

	public long joinGraceTicks() {
		return ModConfig.realTicks(joinGraceMinutes * 60);
	}

	public long minorGapTicks() {
		return ModConfig.realTicks(minorGapMinutes * 60);
	}

	public long majorGapTicks() {
		return ModConfig.realTicks(majorGapMinutes * 60);
	}

	/** When Traces starts, in play ticks, scaled by the tempo. */
	public TickRange tracesStart(Tempo tempo) {
		return hoursRange(tracesStartMinHours, tracesStartMaxHours, paceFactor(tempo));
	}

	/** When Proximity starts, in play ticks, scaled by the tempo. */
	public TickRange proximityStart(Tempo tempo) {
		return hoursRange(proximityStartMinHours, proximityStartMaxHours, paceFactor(tempo));
	}

	/** Gap between majors in Proximity, scaled by the tempo. */
	public TickRange proximityMajorGap(Tempo tempo) {
		return hoursRange(proximityMajorEveryHoursMin, proximityMajorEveryHoursMax, paceFactor(tempo));
	}

	public long firstAccidentTicks(Tempo tempo) {
		return ModConfig.realTicks(firstAccidentMinHours * 3600 * paceFactor(tempo));
	}

	public long emptySessionTicks() {
		return ModConfig.realTicks(emptySessionMinMinutes * 60);
	}

	public long tensionDecayPeriodTicks() {
		return ModConfig.realTicks(3600);
	}

	public long pityStartTicks() {
		return ModConfig.realTicks(pityStartHours * 3600);
	}

	public long miningDarkStillTicks() {
		return ModConfig.realTicks(miningDarkStillSeconds);
	}

	public long miningDarkSoundGapTicks() {
		return ModConfig.realTicks(miningDarkSoundGapMinutes * 60);
	}

	public long silenceTicks() {
		return ModConfig.realTicks(silenceSeconds);
	}

	public TickRange fogDrift() {
		return new TickRange(ModConfig.realTicks(fogDriftMinSeconds), ModConfig.realTicks(fogDriftMaxSeconds));
	}

	/** Sampling cadence of {@link PlayerWatch}; a cost setting, so it is not divided by devFastMode. */
	public long watchSampleTicks() {
		return Math.max(1, Math.round(watchSampleSeconds * 20));
	}

	private static TickRange hoursRange(double minHours, double maxHours, double factor) {
		return new TickRange(ModConfig.realTicks(minHours * 3600 * factor), ModConfig.realTicks(maxHours * 3600 * factor));
	}
}
