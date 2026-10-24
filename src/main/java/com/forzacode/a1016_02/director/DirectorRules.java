package com.forzacode.a1016_02.director;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.director.sc.ScPacing;

/**
 * Every number the director's logic uses, in ticks, resolved once from {@link Pacing}, {@link DirectorConfig}
 * and the world profile. Real-time values go through the {@code Pacing} accessors or
 * {@link ModConfig#realTicks(double)}, so {@code devFastMode} applies; in-game days are never divided.
 * Fields are public and mutable so tests can tighten a rule; the live director builds a fresh copy every tick.
 */
public final class DirectorRules {
	/**
	 * One beat of {@link #beats}: this card is reserved as the player's next draw of its tier for {@code window} play ticks
	 * (R10-2).
	 */
	public record Beat(String cardId, long window) {
	}

	public WorldProfile profile;

	// cadence and gates
	public long tickInterval;
	public long hourTicks;
	public long joinGrace;
	public long minorGap;
	public long majorGap;
	public long minAnyGap;
	/** Moments a player's own first {@link #hourTicks} of play may hold (D-070); negative for no cap. */
	public int firstHourMaxFires;
	public long holdGiveUp;
	/** A held ambient or minor card whose moment does not fit goes back after this many play ticks. */
	public long heldUnfitGiveBack;
	/** A held BLIND card whose moment does not fit (no menu open at a look, no fitting recheck) goes back after this many play ticks (D-077). */
	public long blindHold;
	/** A card given back because its moment did not come is parked (not drawn, out for the run-out rule) this many play ticks; 0: never. */
	public long unfitPark;
	public int noMajorBeforeDay;
	public long firstAccident;
	public int sightingsPerDayMax;
	public int houseCopyMinDay;
	public Stage minorMinStage;
	public Stage majorMinStage;
	public Stage signatureMinStage;

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
	/** First stage a session may be rolled empty in (D-070: Proximity). */
	public Stage emptySessionMinStage;

	// friends (D-074): the ambient and minor rate a group sharing a moment rolls between them, in solo rates
	public double groupRateFactor;

	// fog weather (D-071): the boost while a fog spell that is not quiet is on
	public double fogWeatherOddsFactor;
	public double fogWeatherSightingWeight;
	/** A major slot due within this many play ticks may come now. */
	public long fogWeatherMajorPull;

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
	/** In-game day ticks a deck may stay stuck on gated cards before it reshuffles anyway. */
	public long deckStall;

	// a group of cards paced as one (the self-created layer's second wave, docs/SELF_CREATED_2.md "Pacing"; FIX-SC2-PACE).
	// Empty (the default, and live while the layer is off): every card is drawn as ever.
	/** The group's cards. */
	public Set<String> groupCards = Set.of();
	/**
	 * Per stage and tier: the share of that tier's due draws that come from the group, which then has a no-repeat cycle of
	 * its own; a stage and tier without a share (or 0) draws the group's cards as any other card.
	 */
	public final Map<Stage, Map<Tier, Double>> groupShare = new EnumMap<>(Stage.class);
	/** A card's own weight in a draw, times its habit and stage weights (1 if missing). */
	public final Map<String, Double> cardWeights = new HashMap<>();
	/** A group card's habit weight is never below this (a card whose habits miss the world's still comes). */
	public double groupHabitFloor;
	/** Play ticks between two group moments one player goes through, their own or felt; 0: no gap. */
	public long groupGap;
	/** Play ticks before the same group card comes again for one player (their own or felt); 0: only its group's cycle. */
	public long groupRepeat;

	// the first beat (R10-2): from beatStage on, each card of the list in turn is reserved as the player's next draw of its tier
	/** The beats in order, each with its window of play ticks; empty: none. */
	public List<Beat> beats = List.of();
	public Stage beatStage = Stage.PROXIMITY;
	/** For this many play ticks of the beats' window a beat looks for its moment only at dusk or at night. */
	public long beatDarkFirst;

	// cards on a clock of their own (the layer's hunts, R10-4): never drawn from their deck; each comes at a passing look
	/** The cards; empty: none. */
	public Set<String> ownClockCards = Set.of();
	/** None before this much of the player's play (the tempo applied); used instead of {@link #firstAccident}. */
	public long ownClockFrom;
	/** At most one of them per this many play ticks for one player. */
	public long ownClockGap;
	/** After the player's first, one may take a major slot due within this many play ticks (the first, or a called one: any). */
	public long ownClockPull;

	// flags set by other workstreams ({@link DirectorFlags}); defaults when no flag is set
	/** No fire at all while the in-game day is below this ({@link DirectorFlags#FOREVER} for good). */
	public long silenceUntilDay = DirectorFlags.NO_SILENCE;
	/**
	 * Already applied to the major schedule, the gaps (never below their floors) and decay above; the brain also
	 * multiplies the minor rate by it.
	 */
	public double paceMultiplier = 1;

	private DirectorRules() {
	}

	/**
	 * The rules from the loaded config and this profile, without any {@link DirectorFlags} (dry runs of a profile): the
	 * self-created layer's pacing from its loaded section while the layer is on, none while it is off ({@link ScPacing}).
	 */
	public static DirectorRules current(WorldProfile profile) {
		return ScPacing.live(from(ModConfig.pacing(), DirectorConfig.get(), profile), null);
	}

	/**
	 * The live rules: the loaded config, this world's profile and the director's flags in the shared state; the layer's
	 * pacing only while the layer is on and its story goes on ({@link ScPacing#live}).
	 */
	public static DirectorRules live(HerobrineState state) {
		return DirectorFlags.apply(ScPacing.live(from(ModConfig.pacing(), DirectorConfig.get(), state.profile()), state), DirectorFlags.parse(state.flags()));
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
		rules.firstHourMaxFires = config.firstHourMaxFires;
		rules.holdGiveUp = Math.max(1, ModConfig.realTicks(config.holdGiveUpMinutes * 60));
		rules.heldUnfitGiveBack = Math.max(1, Math.min(rules.holdGiveUp, ModConfig.realTicks(config.heldUnfitGiveBackMinutes * 60)));
		rules.blindHold = Math.max(1, Math.min(rules.holdGiveUp, ModConfig.realTicks(config.blindHoldMinutes * 60)));
		rules.unfitPark = Math.max(0, ModConfig.realTicks(Math.max(0, config.unfitParkMinutes) * 60));
		rules.noMajorBeforeDay = pacing.noMajorBeforeDay;
		rules.firstAccident = pacing.firstAccidentTicks(profile.tempo());
		rules.sightingsPerDayMax = pacing.sightingsPerDayMax;
		rules.houseCopyMinDay = pacing.houseCopyMinDay;
		rules.minorMinStage = stage(config.minorMinStage, Stage.TRACES);
		rules.majorMinStage = stage(config.majorMinStage, Stage.PROXIMITY);
		rules.signatureMinStage = stage(config.signatureMinStage, Stage.TRACES);

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
		rules.emptySessionMinStage = stage(config.emptySessionMinStage, Stage.PROXIMITY);

		rules.groupRateFactor = Math.max(0, config.groupRateFactor);
		rules.fogWeatherOddsFactor = Math.max(0, config.fogWeatherOddsFactor);
		rules.fogWeatherSightingWeight = Math.max(0, config.fogWeatherSightingWeight);
		rules.fogWeatherMajorPull = Math.max(0, ModConfig.realTicks(Math.max(0, config.fogWeatherMajorPullMinutes) * 60));

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
		rules.deckStall = Math.max(1, Math.round(Math.max(0, config.deckStallDays) * DirectorBrain.DAY_TICKS));
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
		// The self-created layer's pacing at its code defaults (dry runs and tests); live rules take the loaded section.
		ScPacing.apply(rules, ScPacing.defaults(), pacing.paceFactor(profile.tempo()));
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

	/**
	 * A card's weight in a draw before its stage weight: its habit weight (never below {@link #groupHabitFloor} for a
	 * group card) times its own weight ({@link #cardWeights}).
	 */
	public double drawWeight(CardInfo card) {
		double habit = habitWeight(card);
		if (groupCards.contains(card.id())) {
			habit = Math.max(habit, groupHabitFloor);
		}
		return habit * cardWeights.getOrDefault(card.id(), 1.0);
	}

	/** The group's share of this tier's due draws in this stage (0: the group's cards are drawn as any). */
	public double groupShare(Stage stage, Tier tier) {
		Map<Tier, Double> byTier = groupShare.get(stage);
		Double share = byTier == null ? null : byTier.get(tier);
		return share == null ? 0 : Math.max(0, Math.min(1, share));
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
