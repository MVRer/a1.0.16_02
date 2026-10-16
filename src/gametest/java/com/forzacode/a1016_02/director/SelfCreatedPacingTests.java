package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.sc.Sc01Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * SC-5, the self-created layer's pacing (docs/SELF_CREATED.md, "Pacing"): every card of the layer is in the registered
 * deck with the design's row, and the dry runs with all of them (192 seeds, 20 h, every tempo) keep D-070's first hour
 * and every hard limit and stay within 15% of the deck without them (DESIGN 4b's v1.3 runs) in Traces' and Proximity's
 * ambient and minor rates and Proximity's majors: with every card's moment at the usual dice, with {@code sc01}'s dread
 * share, with every card of the layer never fitting (a player with no friend, no house, no pets, no caves of the right
 * shape: they park and hand their rolls on), and with every one fitting rarely (2% a look: the way back's cave passage
 * holding the major tier for its long hold, then parking). The numbers are logged.
 */
public class SelfCreatedPacingTests {
	/** One row of the design's table: tier, earliest stage, tags, habits, fake. */
	private record Row(Tier tier, Stage stage, Set<CardTag> tags, Set<Habit> habits, boolean fake) {
	}

	private static final Map<String, Row> DESIGN = Map.ofEntries(
			Map.entry("sc_02", new Row(Tier.AMBIENT, Stage.TRACES, EnumSet.of(CardTag.FOG), EnumSet.of(Habit.WATCHER), false)),
			Map.entry("sc_05", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.SOUND, CardTag.PERSONAL), EnumSet.of(Habit.VISITOR, Habit.WATCHER), true)),
			Map.entry("sc_05b", new Row(Tier.AMBIENT, Stage.TRACES, EnumSet.of(CardTag.MOB), EnumSet.of(Habit.WATCHER, Habit.VISITOR), true)),
			Map.entry("sc_06", new Row(Tier.AMBIENT, Stage.ALONE, EnumSet.of(CardTag.SOUND, CardTag.PERSONAL), EnumSet.of(Habit.WATCHER), true)),
			Map.entry("sc_07", new Row(Tier.MINOR, Stage.PROXIMITY, EnumSet.of(CardTag.MOB), EnumSet.of(Habit.COLLECTOR, Habit.STRIPPER), false)),
			Map.entry("sc_08", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.SCAR), EnumSet.of(Habit.VISITOR, Habit.COLLECTOR), false)),
			Map.entry("sc_09", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.SCAR), EnumSet.of(Habit.VISITOR, Habit.WATCHER), true)),
			Map.entry("sc_11", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.LIGHT, CardTag.SCAR), EnumSet.of(Habit.MOURNER, Habit.CARVER), false)),
			Map.entry("sc_12", new Row(Tier.MINOR, Stage.PROXIMITY, EnumSet.of(CardTag.DIG), EnumSet.of(Habit.CARVER, Habit.WATCHER), false)),
			Map.entry("sc_13", new Row(Tier.MAJOR, Stage.PROXIMITY, EnumSet.of(CardTag.DIG, CardTag.PERSONAL), EnumSet.of(Habit.CARVER, Habit.STRIPPER), false)),
			Map.entry("sc_14", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.TEXT), EnumSet.of(Habit.WATCHER, Habit.VISITOR), false)),
			Map.entry("sc_15", new Row(Tier.MINOR, Stage.PROXIMITY, EnumSet.of(CardTag.DIG, CardTag.PERSONAL), EnumSet.of(Habit.CARVER, Habit.STRIPPER), false)));

	private static final Predicate<CardInfo> LAYER = card -> card.id().startsWith("sc_");
	/** A look at a card of the layer fits this often in the rare runs. */
	static final double RARE_FIT = 0.02;

	/** Every card of the layer is registered with its row of the design's table, and no other card is named sc_. */
	@GameTest
	public void everyCardOfTheLayerIsInTheDeckWithTheDesignsRow(GameTestHelper helper) {
		List<CardInfo> layer = DirectorTestSupport.registryDeck().stream().filter(LAYER).toList();
		helper.assertTrue(layer.size() == DESIGN.size(), "the layer's cards: " + layer.stream().map(CardInfo::id).toList());
		for (CardInfo card : layer) {
			Row row = DESIGN.get(card.id());
			helper.assertTrue(row != null, card.id() + " is not in the design's table");
			Row now = new Row(card.tier(), card.earliestStage(), card.tags(), card.habits(), card.hasFake());
			helper.assertTrue(row.equals(now), card.id() + ": " + now + ", the design says " + row);
		}
		helper.succeed();
	}

	/** One run per tuning seed of this tempo over this deck, the places' dread share or none, the moments as given. */
	static List<DirectorSim.Result> runs(Tempo tempo, List<CardInfo> deck, Sc01Config places, Predicate<CardInfo> never, ToDoubleFunction<CardInfo> chance) {
		List<DirectorSim.Result> out = new ArrayList<>();
		for (long seed : DirectorApi.tuningSeeds()) {
			DirectorConfig config = new DirectorConfig();
			DirectorRules rules = DirectorRules.from(new Pacing(), config, com.forzacode.a1016_02.debug.Playthrough.profile(seed, tempo));
			DirectorSim.Params params = DirectorSim.Params.from(config, rules);
			if (places != null) {
				params.places(places);
			}
			params.hours = DirectorTestSupport.HOURS;
			params.seed = seed;
			params.attention = config.attentionNeutral;
			params.neverFits = never;
			params.fitChanceOf = chance;
			out.add(DirectorSim.run(rules, deck, Math.max(config.historySize, 1), new DirectorMemory(), Stage.ALONE, 0, new DirectorBrain.Clock(0, 0), params));
		}
		return out;
	}

	/**
	 * The dry runs with every card of the layer (see the class): D-070's first hour, every hard limit, and the 4b rates
	 * within 15% of the deck without the layer's cards, in each case and tempo.
	 */
	@GameTest(maxTicks = 6000)
	public void theDryRunsWithEveryCardKeepTheFirstHourTheLimitsAndTheRates(GameTestHelper helper) {
		List<CardInfo> all = DirectorTestSupport.registryDeck();
		List<CardInfo> before = all.stream().filter(LAYER.negate()).toList();
		Sc01Config places = new Sc01Config();
		ToDoubleFunction<CardInfo> rare = card -> LAYER.test(card) ? RARE_FIT : new DirectorConfig().simFitChance;
		for (Tempo tempo : Tempo.values()) {
			DirectorTestSupport.FourB was = DirectorTestSupport.FourB.of(DirectorTestSupport.registryRuns(tempo, new DirectorConfig(), before, null), before);
			A1016_02.LOGGER.info("[a1016] sc pacing {} without the layer: {}", tempo, was.summary());
			Map<String, List<DirectorSim.Result>> cases = new java.util.LinkedHashMap<>();
			cases.put("every card", runs(tempo, all, null, null, null));
			cases.put("every card, dread share", runs(tempo, all, places, null, null));
			cases.put("the layer never fits, dread share", runs(tempo, all, places, LAYER, null));
			cases.put("the layer fits rarely, dread share", runs(tempo, all, places, null, rare));
			for (Map.Entry<String, List<DirectorSim.Result>> c : cases.entrySet()) {
				List<DirectorSim.Result> runs = c.getValue();
				DirectorTestSupport.FirstHour first = DirectorTestSupport.FirstHour.of(runs);
				DirectorTestSupport.FourB now = DirectorTestSupport.FourB.of(runs, all);
				A1016_02.LOGGER.info("[a1016] sc pacing {} {}: {}; {}", tempo, c.getKey(), now.summary(), first.summary());
				String at = tempo + ", " + c.getKey() + ", " + now.summary() + " against " + was.summary() + ": ";
				helper.assertTrue(first.by30() * 4 >= first.runs() * 3 && first.by45() * 10 >= first.runs() * 9 && first.twoOrMore() * 10 >= first.runs() * 7
						&& first.most() <= 5, at + "the first hour: " + first.summary());
				for (DirectorSim.Result run : runs) {
					helper.assertTrue(run.violations.isEmpty(), at + "seed " + run.params.seed + ": " + run.violations);
					List<String> hard = DirectorTestSupport.feltHardLimits(run, all);
					helper.assertTrue(hard.isEmpty(), at + "seed " + run.params.seed + ": " + hard);
				}
				helper.assertTrue(within(now.tracesAmbientsPerHour(), was.tracesAmbientsPerHour()), at + "Traces' ambients moved more than 15%");
				helper.assertTrue(within(now.tracesMinorsPerHour(), was.tracesMinorsPerHour()), at + "Traces' minors moved more than 15%");
				helper.assertTrue(within(now.proximityAmbientsPerHour(), was.proximityAmbientsPerHour()), at + "Proximity's ambients moved more than 15%");
				helper.assertTrue(within(now.proximityMinorsPerHour(), was.proximityMinorsPerHour()), at + "Proximity's minors moved more than 15%");
				helper.assertTrue(within(now.hoursPerMajor(), was.hoursPerMajor()), at + "Proximity's majors moved more than 15%");
			}
		}
		helper.succeed();
	}

	private static boolean within(double now, double was) {
		return was <= 0 ? now <= 0 : Math.abs(now / was - 1) <= 0.15;
	}
}
