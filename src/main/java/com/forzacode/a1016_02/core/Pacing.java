package com.forzacode.a1016_02.core;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.RandomSource;

/**
 * Every shared timing and distance rule (DESIGN.md "The director", 4b, Triggers, and the related rules), stored
 * under {@code pacing} in {@code config/a1016_02.json}. Fields are in natural units (seconds, minutes, hours,
 * in-game days, blocks). Read real-time values through the {@code ...Ticks()} accessors: they apply
 * {@code devFastMode}. In-game days are never divided; use {@code /a1016 timewarp} for those.
 *
 * <p>Versioned with the file's root ({@link ModConfig#configVersion}, D-075): a changed default gets a step in
 * {@link #upgrade} and a higher {@link ModConfig#CONFIG_VERSION}, so files written before it take the new number unless
 * the player changed it.
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

	// --- tempo: paceFactor = 1 + tempoShift * direction (0.75 / 1.0 / 1.25) ---
	public double tempoShift = 0.25;

	// --- stages by real play time (hours, before paceFactor; D-070: something in the first hour) ---
	public double tracesStartMinHours = 0.4;
	public double tracesStartMaxHours = 0.9;
	public double proximityStartMinHours = 1.75;
	public double proximityStartMaxHours = 2.75;
	public int aloneMaxAmbient = 3;
	public double tracesAmbientPerHour = 2;
	public double proximityMinorsPerHourMin = 1.5;
	public double proximityMinorsPerHourMax = 2.5;
	public double proximityMajorEveryHoursMin = 1.5;
	public double proximityMajorEveryHoursMax = 3;
	public double firstAccidentMinHours = 3.5;
	/** Some whole sessions contain nothing, at least this long (from Proximity on since v1.2, D-070; the director decides). */
	public double emptySessionMinMinutes = 45;
	/**
	 * Chance per session that the whole session stays empty: about 1 in 10 (D-070; v1.1 had 1 in 5). Silence is still
	 * designed, just shorter. Which stages roll it is the director's rule.
	 */
	public double emptySessionChance = 0.1;

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
	public double pityStartHours = 0.5;
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
	public double fogDriftMinSeconds = 4;
	public double fogDriftMaxSeconds = 10;
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

	// --- multiplayer (D-060) ---
	/**
	 * Shared moments: participants within this many blocks of the target (same dimension) feel the same fog surge,
	 * silence and fired card, and it counts for each of them.
	 */
	public int sharedMomentRadius = 48;

	/** Weight per {@link AttentionTrigger}, by name. Missing entries are filled with the defaults. */
	public Map<String, Double> attentionWeights = new LinkedHashMap<>();

	public Pacing() {
		fillMissingWeights();
	}

	/**
	 * Brings a pacing read at root version {@code from} up to {@link ModConfig#CONFIG_VERSION}, one step at a time;
	 * each step replaces only the values that still equal that version's defaults ({@link ModConfig.Upgrades}).
	 * Step 0 to 1 (v1.2, D-070): Traces at 0.4 to 0.9 h (was 1 to 3), Proximity at 1.75 to 2.75 h (5 to 6), tempo shift
	 * 0.25 (0.4), up to 3 ambients in Alone (1), 2 ambients an hour in Traces (1), 1.5 to 2.5 minors an hour in
	 * Proximity (1 to 2), a major every 1.5 to 3 h (2 to 4), the first accident from 3.5 h (6), empty sessions 0.1
	 * (0.2), pity from 0.5 h (1), fog drifts of 4 to 10 s (3 to 8). The stage windows stay in order: a window whose new
	 * minimum would pass its kept maximum (or the other way round) keeps both its old values, and a Traces window the
	 * player slowed by hand past the new Proximity start keeps the player's old Proximity window, so Proximity never
	 * starts inside Traces (a v1.1 file whose own values overlapped is left as the player wrote it).
	 */
	void upgrade(int from) {
		Pacing d = new Pacing();
		if (from < 1) {
			double oldTracesMin = tracesStartMinHours;
			double oldTracesMax = tracesStartMaxHours;
			double oldProximityMin = proximityStartMinHours;
			double oldProximityMax = proximityStartMaxHours;
			tracesStartMinHours = ModConfig.Upgrades.keepOrNew(tracesStartMinHours, 1, d.tracesStartMinHours);
			tracesStartMaxHours = ModConfig.Upgrades.keepOrNew(tracesStartMaxHours, 3, d.tracesStartMaxHours);
			proximityStartMinHours = ModConfig.Upgrades.keepOrNew(proximityStartMinHours, 5, d.proximityStartMinHours);
			proximityStartMaxHours = ModConfig.Upgrades.keepOrNew(proximityStartMaxHours, 6, d.proximityStartMaxHours);
			// Half a window moved (the other half hand-set): keep the window the player had.
			if (tracesStartMinHours > tracesStartMaxHours && oldTracesMin <= oldTracesMax) {
				tracesStartMinHours = oldTracesMin;
				tracesStartMaxHours = oldTracesMax;
			}
			if (proximityStartMinHours > proximityStartMaxHours && oldProximityMin <= oldProximityMax) {
				proximityStartMinHours = oldProximityMin;
				proximityStartMaxHours = oldProximityMax;
			}
			// A Traces window slowed by hand past the new Proximity start: Proximity keeps the player's old window.
			if (tracesStartMaxHours > proximityStartMinHours && oldTracesMax <= oldProximityMin) {
				proximityStartMinHours = oldProximityMin;
				proximityStartMaxHours = oldProximityMax;
			}
			tempoShift = ModConfig.Upgrades.keepOrNew(tempoShift, 0.4, d.tempoShift);
			aloneMaxAmbient = ModConfig.Upgrades.keepOrNew(aloneMaxAmbient, 1, d.aloneMaxAmbient);
			tracesAmbientPerHour = ModConfig.Upgrades.keepOrNew(tracesAmbientPerHour, 1, d.tracesAmbientPerHour);
			proximityMinorsPerHourMin = ModConfig.Upgrades.keepOrNew(proximityMinorsPerHourMin, 1, d.proximityMinorsPerHourMin);
			proximityMinorsPerHourMax = ModConfig.Upgrades.keepOrNew(proximityMinorsPerHourMax, 2, d.proximityMinorsPerHourMax);
			proximityMajorEveryHoursMin = ModConfig.Upgrades.keepOrNew(proximityMajorEveryHoursMin, 2, d.proximityMajorEveryHoursMin);
			proximityMajorEveryHoursMax = ModConfig.Upgrades.keepOrNew(proximityMajorEveryHoursMax, 4, d.proximityMajorEveryHoursMax);
			firstAccidentMinHours = ModConfig.Upgrades.keepOrNew(firstAccidentMinHours, 6, d.firstAccidentMinHours);
			emptySessionChance = ModConfig.Upgrades.keepOrNew(emptySessionChance, 0.2, d.emptySessionChance);
			pityStartHours = ModConfig.Upgrades.keepOrNew(pityStartHours, 1, d.pityStartHours);
			fogDriftMinSeconds = ModConfig.Upgrades.keepOrNew(fogDriftMinSeconds, 3, d.fogDriftMinSeconds);
			fogDriftMaxSeconds = ModConfig.Upgrades.keepOrNew(fogDriftMaxSeconds, 8, d.fogDriftMaxSeconds);
		}
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
