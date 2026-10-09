package com.forzacode.a1016_02.director;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.WorldProfile;

/**
 * Every number the director's logic uses, in ticks, resolved once from {@link Pacing}, {@link DirectorConfig}
 * and the world profile. Real-time values go through the {@code Pacing} accessors or
 * {@link ModConfig#realTicks(double)}, so {@code devFastMode} applies; in-game days are never divided.
 * Fields are public and mutable so tests can tighten a rule; the live director builds a fresh copy every tick.
 */
public final class DirectorRules {
	public WorldProfile profile;

	// cadence and gates
	public long tickInterval;
	public long hourTicks;
	public long joinGrace;
	public long minorGap;
	public long majorGap;
	public long minAnyGap;
	public long holdGiveUp;
	public int noMajorBeforeDay;
	public long firstAccident;
	public int sightingsPerDayMax;
	public int houseCopyMinDay;
	public Stage minorMinStage;
	public Stage majorMinStage;

	// stages
	public Pacing.TickRange tracesStart;
	public Pacing.TickRange proximityStart;
	public double attentionNeutral;
	public double attentionFullEffect;
	public double attentionBand;

	// rates
	public int aloneMaxAmbient;
	public double aloneAmbientPerHour;
	public double tracesAmbientPerHour;
	public double proximityAmbientPerHour;
	public double tellingAmbientPerHour;
	public double tracesMinorsPerHour;
	public double minorsPerHourMin;
	public double minorsPerHourMax;
	public double rateRefractory;
	public Pacing.TickRange majorEvery;

	// empty sessions
	public double emptySessionChance;
	public long emptySessionMin;
	public long emptySessionMax;

	// fakes, tension, quiet, pity
	public double fakeChance;
	public double fakeTensionFactor;
	public final Map<Tier, Double> tension = new EnumMap<>(Tier.class);
	public double tensionThreshold;
	public double tensionAfterQuiet;
	public int quietMinDays;
	public int quietMaxDays;
	public double tensionDecayPerTick;
	public long pityStart;
	public double pityBonusPerHour;
	public double pityMaxBonus;

	// weights
	public double habitMatchWeight;
	public double habitMissWeight;
	public final Map<Stage, Map<CardTag, Double>> stageTagWeights = new EnumMap<>(Stage.class);

	private DirectorRules() {
	}

	/** The live rules: the loaded config and this world's profile. */
	public static DirectorRules current(WorldProfile profile) {
		return from(ModConfig.pacing(), DirectorConfig.get(), profile);
	}

	public static DirectorRules from(Pacing pacing, DirectorConfig config, WorldProfile profile) {
		DirectorRules rules = new DirectorRules();
		rules.profile = profile;

		rules.tickInterval = Math.max(1, pacing.directorTickTicks());
		rules.hourTicks = Math.max(1, ModConfig.realTicks(3600));
		rules.joinGrace = pacing.joinGraceTicks();
		rules.minorGap = pacing.minorGapTicks();
		rules.majorGap = pacing.majorGapTicks();
		rules.minAnyGap = ModConfig.realTicks(config.minAnyGapMinutes * 60);
		rules.holdGiveUp = Math.max(1, ModConfig.realTicks(config.holdGiveUpMinutes * 60));
		rules.noMajorBeforeDay = pacing.noMajorBeforeDay;
		rules.firstAccident = pacing.firstAccidentTicks(profile.tempo());
		rules.sightingsPerDayMax = pacing.sightingsPerDayMax;
		rules.houseCopyMinDay = pacing.houseCopyMinDay;
		rules.minorMinStage = stage(config.minorMinStage, Stage.TRACES);
		rules.majorMinStage = stage(config.majorMinStage, Stage.PROXIMITY);

		rules.tracesStart = pacing.tracesStart(profile.tempo());
		rules.proximityStart = pacing.proximityStart(profile.tempo());
		rules.attentionNeutral = config.attentionNeutral;
		rules.attentionFullEffect = Math.max(1e-6, config.attentionFullEffect);
		rules.attentionBand = Math.max(0, Math.min(0.95, config.attentionBand));

		rules.aloneMaxAmbient = pacing.aloneMaxAmbient;
		rules.aloneAmbientPerHour = config.aloneAmbientPerHour;
		rules.tracesAmbientPerHour = pacing.tracesAmbientPerHour;
		rules.proximityAmbientPerHour = config.proximityAmbientPerHour;
		rules.tellingAmbientPerHour = config.tellingAmbientPerHour;
		rules.tracesMinorsPerHour = config.tracesMinorsPerHour;
		rules.minorsPerHourMin = pacing.proximityMinorsPerHourMin;
		rules.minorsPerHourMax = Math.max(pacing.proximityMinorsPerHourMin, pacing.proximityMinorsPerHourMax);
		rules.majorEvery = pacing.proximityMajorGap(profile.tempo());
		rules.rateRefractory = Math.max(0, Math.min(0.9, config.rateRefractory));

		rules.emptySessionChance = pacing.emptySessionChance;
		rules.emptySessionMin = pacing.emptySessionTicks();
		rules.emptySessionMax = Math.max(rules.emptySessionMin, ModConfig.realTicks(config.emptySessionMaxMinutes * 60));

		rules.fakeChance = pacing.fakeChance;
		rules.fakeTensionFactor = config.fakeTensionFactor;
		rules.tension.put(Tier.AMBIENT, pacing.tensionAmbient);
		rules.tension.put(Tier.MINOR, pacing.tensionMinor);
		rules.tension.put(Tier.MAJOR, pacing.tensionMajor);
		rules.tension.put(Tier.SIGNATURE, pacing.tensionSignature);
		rules.tensionThreshold = pacing.tensionThreshold;
		rules.tensionAfterQuiet = Math.min(config.tensionAfterQuiet, pacing.tensionThreshold);
		rules.quietMinDays = Math.max(0, pacing.quietMinDays);
		rules.quietMaxDays = Math.max(rules.quietMinDays, pacing.quietMaxDays);
		rules.tensionDecayPerTick = pacing.tensionDecayPerHour / Math.max(1, pacing.tensionDecayPeriodTicks());
		rules.pityStart = pacing.pityStartTicks();
		rules.pityBonusPerHour = pacing.pityBonusPerHour;
		rules.pityMaxBonus = pacing.pityMaxBonus;

		rules.habitMatchWeight = config.habitMatchWeight;
		rules.habitMissWeight = config.habitMissWeight;
		if (config.stageTagWeights != null) {
			config.stageTagWeights.forEach((stageName, tags) -> {
				Stage stage = stage(stageName, null);
				if (stage == null || tags == null) {
					return;
				}
				Map<CardTag, Double> byTag = new EnumMap<>(CardTag.class);
				tags.forEach((tagName, weight) -> {
					CardTag tag = tag(tagName);
					if (tag != null && weight != null) {
						byTag.put(tag, Math.max(0, weight));
					}
				});
				rules.stageTagWeights.put(stage, byTag);
			});
		}
		return rules;
	}

	/** Stage times multiplier for this attention: 1 at neutral, within 1 +- band. */
	public double attentionFactor(double attention) {
		double shift = attentionBand * (attention - attentionNeutral) / attentionFullEffect;
		return Math.max(1 - attentionBand, Math.min(1 + attentionBand, 1 - shift));
	}

	/** Product of the stage weights of every tag on the card (1 if none are set). */
	public double tagWeight(Stage stage, CardInfo card) {
		Map<CardTag, Double> byTag = stageTagWeights.get(stage);
		double weight = 1;
		if (byTag != null) {
			for (CardTag tag : card.tags()) {
				weight *= byTag.getOrDefault(tag, 1.0);
			}
		}
		return weight;
	}

	public double habitWeight(CardInfo card) {
		if (card.habits().isEmpty()) {
			return 1;
		}
		long matches = card.habits().stream().filter(profile.habits()::contains).count();
		return matches == 0 ? habitMissWeight : 1 + (habitMatchWeight - 1) * matches;
	}

	public double tensionFor(Tier tier) {
		return tension.getOrDefault(tier, 0.0);
	}

	private static Stage stage(String name, Stage fallback) {
		if (name == null) {
			return fallback;
		}
		try {
			return Stage.valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	private static CardTag tag(String name) {
		try {
			return CardTag.valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}
}
