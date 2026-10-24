package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.sc2.s25.Sc25Config;
import com.forzacode.a1016_02.accident.sc2.s26.CallTrap;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.sc.PlaceMemory;
import com.forzacode.a1016_02.director.sc.Places;
import com.forzacode.a1016_02.director.sc.Sc01Config;
import com.forzacode.a1016_02.director.sc.ScPacing;
import com.forzacode.a1016_02.director.sc.SimFits;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * SC2-9 and FIX-SC2-PACE, the second wave's pacing (docs/SELF_CREATED_2.md, "Pacing and director integration") and 01+.
 * <ul>
 *   <li>Every card of the second wave is registered with its row of the design's table (the hunts are the accident
 *   planner's own cards: MAJOR, Proximity, ACCIDENT, never fake), the director paces it as the layer's pacing says
 *   ({@link ScPacing}: the group, the first beat, the hunts' own clock), and each has its own dry-run moment
 *   ({@link SimFits}).</li>
 *   <li>The guardrails (192 seeds, 20 h, every tempo, the first wave's four cases): D-070's first hour, every hard limit
 *   (the group's gap and the hunts' own clock included), DESIGN 4b's rates within 15% of the deck without the layer, the
 *   traps on the accident clock never earlier and not much later, the second wave never before its stage nor in Alone,
 *   never more than two of its moments in an hour, never a fifth of everything that fires.</li>
 *   <li>The experience (R10-1, R10-2, R10-4: the wave must be met, rarity is no goal): from Proximity about one of its
 *   moments every 1.5 to 2.5 hours of play; the first beat by an hour and a half into Proximity in four runs of five; a
 *   player of five sessions has met the held breath, at least two of its features and, if they go out at night, a hunt
 *   about one time in two; two players together share the first beat and never a hunt.</li>
 *   <li>The brain's rules on cards of the tests' own: the beat at its first fitting look (dusk first, never a fake, never
 *   held), the group's share only for moments that are there (its gap, its repeat), the own clock (never drawn, from its
 *   time, its gap, the slot, a call); the trespass's call in 26's gate.</li>
 *   <li>01+: his places raise the place factor to at least {@code remainsFactor}, never past {@code maxFactor}; an
 *   answer of his places is remembered as a place of the player's; the dry runs model the share of play there; the
 *   section's steps from 3 on only add keys.</li>
 * </ul>
 */
public class SelfCreated2PacingTests {
	/** One row of the design's table: tier, earliest stage, tags, habits, fake. */
	private record Row(Tier tier, Stage stage, Set<CardTag> tags, Set<Habit> habits, boolean fake) {
	}

	/** docs/SELF_CREATED_2.md, "Pacing and director integration", and the hunts' planner cards (SC2-4). */
	static final Map<String, Row> DESIGN = Map.ofEntries(
			Map.entry("sc_21", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.MOB), EnumSet.of(Habit.WATCHER, Habit.VISITOR), true)),
			Map.entry("sc_22", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.MOB), EnumSet.of(Habit.WATCHER, Habit.MOURNER), false)),
			Map.entry("sc_23", new Row(Tier.MINOR, Stage.PROXIMITY, EnumSet.of(CardTag.MOB), EnumSet.of(Habit.WATCHER), true)),
			Map.entry("sc_24", new Row(Tier.AMBIENT, Stage.TRACES, EnumSet.of(CardTag.MOB, CardTag.UNSHARED), EnumSet.of(Habit.WATCHER, Habit.VISITOR), false)),
			Map.entry("sc_27", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.FOG, CardTag.UNSHARED), EnumSet.of(Habit.WATCHER), false)),
			Map.entry("sc_28", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.ITEM), EnumSet.of(Habit.COLLECTOR, Habit.VISITOR), true)),
			Map.entry("sc_29", new Row(Tier.AMBIENT, Stage.TRACES, EnumSet.of(CardTag.SOUND, CardTag.PERSONAL), EnumSet.of(Habit.WATCHER), false)),
			Map.entry("sc_30", new Row(Tier.MINOR, Stage.PROXIMITY, EnumSet.of(CardTag.ITEM, CardTag.UNSHARED), EnumSet.of(Habit.STRIPPER, Habit.COLLECTOR), false)),
			Map.entry("sc_31", new Row(Tier.MINOR, Stage.TRACES, EnumSet.of(CardTag.SIGHTING), EnumSet.of(Habit.WATCHER), true)),
			Map.entry("sc_31c", new Row(Tier.MINOR, Stage.PROXIMITY, EnumSet.of(CardTag.SIGHTING), EnumSet.of(Habit.STRIPPER), false)),
			Map.entry("accident_sc25", new Row(Tier.MAJOR, Stage.PROXIMITY, EnumSet.of(CardTag.ACCIDENT), Set.of(), false)),
			Map.entry("accident_sc26", new Row(Tier.MAJOR, Stage.PROXIMITY, EnumSet.of(CardTag.ACCIDENT), Set.of(), false)));

	/** The second wave's cards. */
	static final Predicate<CardInfo> WAVE2 = card -> DESIGN.containsKey(card.id());
	/** Every card of the layer, both waves. */
	static final Predicate<CardInfo> LAYER = card -> card.id().startsWith("sc_") || WAVE2.test(card);
	/** A second-wave card id by its shape (sc_16 to sc_39, a letter or none after it), or one of the hunts' planner cards. */
	private static final String WAVE2_ID = "sc_(1[6-9]|[23]\\d)\\D*|accident_sc2\\d";
	/** The features the second wave's cards are (31 and 31c are one), for the per-feature shares. */
	static final List<String> FEATURES = List.of("21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31");

	/** The feature a second-wave card is, or null. */
	static String feature(String cardId) {
		if (cardId.startsWith("accident_sc")) {
			return cardId.substring("accident_sc".length());
		}
		return DESIGN.containsKey(cardId) ? cardId.substring(3, 5) : null;
	}

	/**
	 * Every second-wave card is in the registered deck with its row of the design's table, and no other card has a
	 * second-wave id; the layer's pacing names exactly them (the group: every one; the beats: 23 then 21; the own clock:
	 * the hunts); each has a dry-run moment of its own: a chance a look below the usual dice, or a night's for a dark one.
	 */
	@GameTest
	public void everySecondWaveCardIsInTheDeckWithTheDesignsRow(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		List<String> wave2 = deck.stream().map(CardInfo::id).filter(id -> id.matches(WAVE2_ID)).sorted().toList();
		helper.assertTrue(wave2.size() == DESIGN.size() && DESIGN.keySet().containsAll(wave2), "the second wave's cards: " + wave2 + ", the design: "
				+ DESIGN.keySet());
		double usual = new DirectorConfig().simFitChance;
		for (CardInfo card : deck) {
			Row row = DESIGN.get(card.id());
			if (row == null) {
				helper.assertFalse(ScPacing.WAVE.contains(card.id()), card.id() + " is paced as the second wave");
				continue;
			}
			Row now = new Row(card.tier(), card.earliestStage(), card.tags(), card.habits(), card.hasFake());
			helper.assertTrue(row.equals(now), card.id() + ": " + now + ", the design says " + row);
			helper.assertTrue(!card.earliestStage().atLeast(Stage.TELLING) && card.earliestStage().atLeast(Stage.TRACES), card.id() + " is not from Traces");
			Double chance = SimFits.CHANCES.get(card.id());
			boolean dark = SimFits.DARK.contains(card.id());
			helper.assertTrue(chance != null && chance > 0 && (dark ? chance <= 1 : chance < usual), card.id() + "'s dry-run chance " + chance);
		}
		helper.assertTrue(ScPacing.WAVE.equals(DESIGN.keySet()), "the group: " + ScPacing.WAVE);
		helper.assertTrue(ScPacing.BEATS.equals(List.of("sc_23", "sc_21")) && ScPacing.HUNTS.equals(Set.of("accident_sc25", "accident_sc26")),
				"the beats " + ScPacing.BEATS + ", the hunts " + ScPacing.HUNTS);
		for (Set<String> named : List.of(SimFits.ALONE, SimFits.COMPANY, SimFits.DARK, SimFits.FOG, SimFits.CAPS.keySet(), SimFits.CALLED.keySet())) {
			helper.assertTrue(DESIGN.keySet().containsAll(named), "a dry-run rule names a card outside the wave: " + named);
		}
		helper.assertTrue(SimFits.DARK.containsAll(ScPacing.HUNTS) && SimFits.ALONE.containsAll(ScPacing.HUNTS), "the hunts are dark and alone");
		helper.succeed();
	}

	/** One run per tuning seed of this tempo over this deck, the places' dread share or none, the moments as given (null: the section's own). */
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
			if (chance != null) {
				params.fitChanceOf = chance;
			}
			out.add(DirectorSim.run(rules, deck, Math.max(config.historySize, 1), new DirectorMemory(), Stage.ALONE, 0, new DirectorBrain.Clock(0, 0), params));
		}
		return out;
	}

	/**
	 * What the second wave was for the players of a set of runs (their own fires and what they felt): its moments per
	 * played hour in Traces and from Proximity, every moment and the real ones (no fake), its share of every fire, the
	 * most in an hour of a run, how many came before their stage or in Alone; how many runs had their first real moment
	 * of the wave by 1.5 h into Proximity (of those that reached it); the first hunt's median hour; and per number of
	 * sessions (3 and 5): the runs that lasted that many, each feature's share of them that met it for real, a hunt's
	 * share, the features met on average.
	 */
	record Experience(double tracesPerHour, double proximityPerHour, double proximityRealPerHour, double share, int mostInAnHour, int beforeStage,
			int beatRuns, int beatBy, double firstHuntMedian, Map<Integer, Integer> sessionRuns, Map<Integer, Map<String, Integer>> met,
			Map<Integer, Integer> hunt, Map<Integer, Double> distinct) {
		static Experience of(List<DirectorSim.Result> runs, Map<String, CardInfo> byId) {
			long hour = runs.isEmpty() ? 1 : runs.getFirst().rules.hourTicks;
			double tracesH = 0;
			double proximityH = 0;
			int traces = 0;
			int proximity = 0;
			int proximityReal = 0;
			int all = 0;
			int wave = 0;
			int most = 0;
			int early = 0;
			int beatRuns = 0;
			int beatBy = 0;
			List<Double> hunts = new ArrayList<>();
			Map<Integer, Integer> sessionRuns = new HashMap<>();
			Map<Integer, Map<String, Integer>> met = new HashMap<>();
			Map<Integer, Integer> hunt = new HashMap<>();
			Map<Integer, Double> distinct = new HashMap<>();
			for (DirectorSim.Result r : runs) {
				tracesH += r.stageTicks(Stage.TRACES) / (double) hour;
				proximityH += (r.stageTicks(Stage.PROXIMITY) + r.stageTicks(Stage.TELLING)) / (double) hour;
				long proximityAt = r.stageAt(Stage.PROXIMITY);
				beatRuns += proximityAt >= 0 ? 1 : 0;
				boolean beat = false;
				boolean hunted = false;
				List<Long> times = new ArrayList<>();
				for (DirectorSim.Event e : r.felt()) {
					all += e.kind() == DirectorSim.Kind.FIRE ? 1 : 0;
					CardInfo card = byId.get(e.cardId());
					String f = feature(e.cardId());
					if (card == null || f == null) {
						continue;
					}
					wave += e.kind() == DirectorSim.Kind.FIRE ? 1 : 0;
					times.add(e.play());
					if (!e.stage().atLeast(card.earliestStage()) || e.stage() == Stage.ALONE) {
						early++;
					}
					if (e.stage() == Stage.TRACES) {
						traces++;
					} else if (e.stage().atLeast(Stage.PROXIMITY)) {
						proximity++;
						proximityReal += e.fake() ? 0 : 1;
					}
					if (!beat && !e.fake() && proximityAt >= 0 && e.play() >= proximityAt) {
						beat = true;
						beatBy += e.play() - proximityAt <= hour * 3 / 2 ? 1 : 0;
					}
					if (!hunted && ScPacing.HUNTS.contains(e.cardId())) {
						hunted = true;
						hunts.add((e.play() - r.startPlay) / (double) hour);
					}
				}
				for (int i = 0; i < times.size(); i++) {
					int n = 0;
					for (int j = i; j < times.size() && times.get(j) - times.get(i) < hour; j++) {
						n++;
					}
					most = Math.max(most, n);
				}
				List<Long> ends = r.events.stream().filter(e -> e.kind() == DirectorSim.Kind.SESSION_END).map(DirectorSim.Event::play).toList();
				for (int n : List.of(3, 5)) {
					if (ends.size() < n) {
						continue;
					}
					long end = ends.get(n - 1);
					sessionRuns.merge(n, 1, Integer::sum);
					Set<String> features = new HashSet<>();
					for (DirectorSim.Event e : r.felt()) {
						String f = feature(e.cardId());
						if (f != null && !e.fake() && e.play() <= end) {
							features.add(f);
						}
					}
					for (String f : features) {
						met.computeIfAbsent(n, k -> new HashMap<>()).merge(f, 1, Integer::sum);
					}
					hunt.merge(n, features.contains("25") || features.contains("26") ? 1 : 0, Integer::sum);
					distinct.merge(n, (double) features.size(), Double::sum);
				}
			}
			hunts.sort(null);
			return new Experience(tracesH <= 0 ? 0 : traces / tracesH, proximityH <= 0 ? 0 : proximity / proximityH,
					proximityH <= 0 ? 0 : proximityReal / proximityH, all == 0 ? 0 : wave / (double) all, most, early, beatRuns, beatBy,
					hunts.size() * 2 <= runs.size() ? Double.POSITIVE_INFINITY : hunts.get(runs.size() / 2), sessionRuns, met, hunt, distinct);
		}

		/** The share of the runs of {@code n} sessions that met this feature for real. */
		double met(int n, String feature) {
			int runs = sessionRuns.getOrDefault(n, 0);
			return runs == 0 ? 0 : met.getOrDefault(n, Map.of()).getOrDefault(feature, 0) / (double) runs;
		}

		double hunt(int n) {
			int runs = sessionRuns.getOrDefault(n, 0);
			return runs == 0 ? 0 : hunt.getOrDefault(n, 0) / (double) runs;
		}

		double distinct(int n) {
			int runs = sessionRuns.getOrDefault(n, 0);
			return runs == 0 ? 0 : distinct.getOrDefault(n, 0.0) / runs;
		}

		double beatShare() {
			return beatRuns == 0 ? 0 : beatBy / (double) beatRuns;
		}

		String summary() {
			StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
					"the second wave: Traces %.2f/h, from Proximity %.2f/h (%.2f real), %.1f%% of every fire, at most %d in an hour; the first real one by"
							+ " Proximity + 1.5 h in %.0f%%; the first hunt's median %.1f h",
					tracesPerHour, proximityPerHour, proximityRealPerHour, 100 * share, mostInAnHour, 100 * beatShare(), firstHuntMedian));
			for (int n : List.of(3, 5)) {
				out.append(String.format(Locale.ROOT, "; %d sessions (%d runs): %.2f features, a hunt %.0f%% |", n, sessionRuns.getOrDefault(n, 0), distinct(n),
						100 * hunt(n)));
				for (String f : FEATURES) {
					out.append(String.format(Locale.ROOT, " %s %.0f%%", f, 100 * met(n, f)));
				}
			}
			return out.toString();
		}
	}

	/** The first accident of any kind, the hunts' included: its median hour over these runs (infinity if half had none). */
	static double firstAccidentOfAnyKind(List<DirectorSim.Result> runs, Map<String, CardInfo> byId) {
		List<Double> firsts = new ArrayList<>();
		for (DirectorSim.Result r : runs) {
			for (DirectorSim.Event e : r.fires()) {
				CardInfo card = byId.get(e.cardId());
				if (card != null && card.has(CardTag.ACCIDENT)) {
					firsts.add((e.play() - r.startPlay) / (double) r.rules.hourTicks);
					break;
				}
			}
		}
		firsts.sort(null);
		return firsts.size() * 2 <= runs.size() ? Double.POSITIVE_INFINITY : firsts.get(runs.size() / 2);
	}

	/**
	 * The guardrails with both waves (see the class), in the first wave's four cases (every card at its own dice, with
	 * {@code sc01}'s dread share and 01+'s share at his places, with every card of the layer never fitting, with every one
	 * fitting 2% a look) and every tempo: D-070's first hour, every hard limit, DESIGN 4b's rates within 15% of the deck
	 * without the layer; the traps on the accident clock: never earlier, and their first one's median within a quarter (the
	 * hunts' own quiets and major gap hold them back a little; the first accident of any kind comes earlier); the second
	 * wave never before its stage nor in Alone, never more than two of its moments in an hour, never a fifth of all fires.
	 */
	@GameTest(maxTicks = 6000)
	public void theDryRunsWithBothWavesKeepTheFirstHourTheLimitsAndTheRates(GameTestHelper helper) {
		List<CardInfo> all = DirectorTestSupport.registryDeck();
		List<CardInfo> before = all.stream().filter(LAYER.negate()).toList();
		Map<String, CardInfo> byId = new LinkedHashMap<>();
		all.forEach(card -> byId.put(card.id(), card));
		Sc01Config places = new Sc01Config();
		double usual = new DirectorConfig().simFitChance;
		ToDoubleFunction<CardInfo> rare = card -> LAYER.test(card) ? SelfCreatedPacingTests.RARE_FIT : usual;
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> plain = runs(tempo, before, null, null, null);
			DirectorTestSupport.FourB was = DirectorTestSupport.FourB.of(plain, before);
			A1016_02.LOGGER.info("[a1016] sc2 pacing {} without the layer: {}; {}", tempo, was.summary(), DirectorTestSupport.FirstHour.of(plain).summary());
			Map<String, List<DirectorSim.Result>> cases = new LinkedHashMap<>();
			cases.put("every card", runs(tempo, all, null, null, null));
			cases.put("every card, dread share", runs(tempo, all, places, null, null));
			cases.put("the layer never fits, dread share", runs(tempo, all, places, LAYER, null));
			cases.put("the layer fits rarely, dread share", runs(tempo, all, places, null, rare));
			for (Map.Entry<String, List<DirectorSim.Result>> c : cases.entrySet()) {
				List<DirectorSim.Result> runs = c.getValue();
				DirectorTestSupport.FirstHour first = DirectorTestSupport.FirstHour.of(runs);
				DirectorTestSupport.FourB now = DirectorTestSupport.FourB.of(runs, all);
				Experience wave = Experience.of(runs, byId);
				double anyKind = firstAccidentOfAnyKind(runs, byId);
				A1016_02.LOGGER.info("[a1016] sc2 pacing {} {}: {}; {}; the first accident of any kind {} h; {}", tempo, c.getKey(), now.summary(), first.summary(),
						String.format(Locale.ROOT, "%.2f", anyKind), wave.summary());
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
				helper.assertTrue(now.firstAccidentMinHours() >= 0.95 * was.firstAccidentMinHours() && now.firstAccidentMedianHours() <= 1.25 * was.firstAccidentMedianHours()
						&& now.firstAccidentMedianHours() >= 0.85 * was.firstAccidentMedianHours(), at + "the traps on the accident clock moved");
				helper.assertTrue(anyKind <= 1.15 * was.firstAccidentMedianHours(), at + "the first accident of any kind came later: " + anyKind);
				helper.assertTrue(wave.beforeStage() == 0, at + wave.beforeStage() + " second-wave fires before their stage or in Alone");
				helper.assertTrue(wave.mostInAnHour() <= 2 && wave.share() <= 0.2, at + "the second wave clusters or crowds: " + wave.summary());
			}
		}
		helper.succeed();
	}

	/**
	 * The experience (R10-1, R10-2, R10-4; FIX-SC2-PACE), every card at its own dice with the dread share, a player out
	 * far from home on three nights of ten ({@link SimFits#NIGHT_OUT}), every tempo: from Proximity one of the wave's
	 * moments every 1.5 to 2.5 h of play (0.38 to 0.67 an hour), Traces at least one in seven hours; the first real one by
	 * 1.5 h into Proximity in four runs of five (the first beat); a player of three sessions has met the held breath four
	 * times in five, of five sessions nine in ten, at least 2.3 features of the wave on average and a hunt in at least 45%;
	 * the first hunt's median (over every player, a run with none counting as never) by 10 h of play. The per-feature shares are logged for the docs.
	 */
	@GameTest(maxTicks = 3000)
	public void theSecondWaveIsMetInAPlayersFirstSessions(GameTestHelper helper) {
		List<CardInfo> all = DirectorTestSupport.registryDeck();
		Map<String, CardInfo> byId = new LinkedHashMap<>();
		all.forEach(card -> byId.put(card.id(), card));
		for (Tempo tempo : Tempo.values()) {
			Experience wave = Experience.of(runs(tempo, all, new Sc01Config(), null, null), byId);
			A1016_02.LOGGER.info("[a1016] sc2 experience {}: {}", tempo, wave.summary());
			String at = tempo + ": " + wave.summary() + ": ";
			helper.assertTrue(wave.proximityPerHour() >= 0.38 && wave.proximityPerHour() <= 0.67, at + "not one every 1.5 to 2.5 h from Proximity");
			helper.assertTrue(wave.tracesPerHour() >= 0.15, at + "Traces holds almost none");
			helper.assertTrue(wave.beatShare() >= 0.8, at + "the first beat comes late");
			helper.assertTrue(wave.met(3, "23") >= 0.8 && wave.met(5, "23") >= 0.9, at + "the held breath is not met");
			helper.assertTrue(wave.distinct(5) >= 2.3, at + "too few of the wave's features in five sessions");
			helper.assertTrue(wave.hunt(5) >= 0.45 && wave.firstHuntMedian() <= 10, at + "the danger comes late or never");
		}
		helper.succeed();
	}

	/**
	 * Two players (FIX-SC2-PACE, D-098): together they share the first beat (both feel the same one, never a fake) and
	 * never have a hunt (its player alone); apart each has a beat of their own; every limit holds for both.
	 */
	@GameTest(maxTicks = 3000)
	public void twoPlayersShareTheFirstBeatAndNeverAHuntTogether(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		UUID ana = new UUID(0x5CA1L, 1L);
		UUID ben = new UUID(0x5CA1L, 2L);
		List<DirectorSim.Seat> seats = List.of(new DirectorSim.Seat(ana, 0), new DirectorSim.Seat(ben, 0));
		List<Long> seeds = DirectorApi.tuningSeeds().subList(0, 96);
		int together = 0;
		int apart = 0;
		for (DirectorSim.Sharing sharing : List.of(DirectorSim.Sharing.TOGETHER, DirectorSim.Sharing.APART)) {
			for (DirectorSim.Group group : DirectorTestSupport.groupRuns(Tempo.SLOW_BURN, seats, sharing, seeds)) {
				String at = (sharing == DirectorSim.Sharing.TOGETHER ? "together" : "apart") + ", seed " + group.players.getFirst().params.seed + ": ";
				helper.assertTrue(group.allViolations().isEmpty(), at + group.allViolations());
				List<DirectorSim.Event> beats = new ArrayList<>();
				for (DirectorSim.Result player : group.players) {
					helper.assertTrue(DirectorTestSupport.feltHardLimits(player, deck).isEmpty(), at + DirectorTestSupport.feltHardLimits(player, deck));
					long proximity = player.stageAt(Stage.PROXIMITY);
					DirectorSim.Event beat = player.felt().stream().filter(e -> proximity >= 0 && e.play() >= proximity && ScPacing.BEATS.contains(e.cardId())
							&& !e.fake()).findFirst().orElse(null);
					beats.add(beat);
					if (sharing == DirectorSim.Sharing.TOGETHER) {
						helper.assertTrue(player.felt().stream().noneMatch(e -> ScPacing.HUNTS.contains(e.cardId())), at + "a hunt beside a friend");
					}
				}
				if (beats.get(0) != null && beats.get(1) != null) {
					boolean shared = beats.get(0).cardId().equals(beats.get(1).cardId()) && beats.get(0).dayTicks() == beats.get(1).dayTicks();
					if (sharing == DirectorSim.Sharing.TOGETHER) {
						helper.assertTrue(shared && (beats.get(0).kind() == DirectorSim.Kind.FELT) != (beats.get(1).kind() == DirectorSim.Kind.FELT),
								at + "they did not share their first beat: " + beats);
						together++;
					} else {
						helper.assertTrue(beats.stream().allMatch(e -> e.kind() == DirectorSim.Kind.FIRE), at + "apart, a beat was felt: " + beats);
						apart++;
					}
				}
			}
		}
		A1016_02.LOGGER.info("[a1016] sc2 beats: {} pairs together shared one, {} pairs apart had one each", together, apart);
		helper.assertTrue(together >= 70 && apart >= 70, "too few beats to tell: " + together + " together, " + apart + " apart");
		helper.succeed();
	}

	// --- the brain's rules, on cards of the test's own ---

	private static final CardInfo BEAT_A = DirectorTestSupport.card("t_beat_a", Tier.MINOR, Stage.PROXIMITY, true, CardTag.MOB);
	private static final CardInfo BEAT_B = DirectorTestSupport.card("t_beat_b", Tier.MINOR, Stage.TRACES, true, CardTag.MOB);
	private static final CardInfo GROUP_A = DirectorTestSupport.card("t_group_a", Tier.MINOR, Stage.TRACES, true, CardTag.MOB);
	private static final CardInfo GROUP_B = DirectorTestSupport.card("t_group_b", Tier.MINOR, Stage.TRACES, false, CardTag.ITEM);
	private static final CardInfo MAIN_A = DirectorTestSupport.card("t_main_a", Tier.MINOR, Stage.TRACES, false, CardTag.SOUND);
	private static final CardInfo MAIN_B = DirectorTestSupport.card("t_main_b", Tier.MINOR, Stage.TRACES, false, CardTag.SCAR);
	private static final CardInfo HUNT = DirectorTestSupport.card("t_hunt", Tier.MAJOR, Stage.PROXIMITY, false, CardTag.ACCIDENT);
	private static final CardInfo TRAP = DirectorTestSupport.card("t_trap", Tier.MAJOR, Stage.PROXIMITY, false, CardTag.ACCIDENT);
	private static final CardInfo MAJOR = DirectorTestSupport.card("t_major", Tier.MAJOR, Stage.PROXIMITY, false, CardTag.SCAR);
	private static final CardInfo AMBIENT = DirectorTestSupport.card("t_ambient", Tier.AMBIENT, Stage.ALONE, false, CardTag.SOUND);

	/** A hand-driven env: what fits, what is called, what fired and whether as a fake. */
	private static final class PaceEnv implements DirectorBrain.Env {
		Stage stage = Stage.PROXIMITY;
		double tension;
		Predicate<CardInfo> fits = card -> true;
		Predicate<CardInfo> called = card -> false;
		List<DirectorBrain.Sharer> sharers = List.of();
		final List<String> fired = new ArrayList<>();
		final List<Boolean> fakes = new ArrayList<>();
		final List<Long> at = new ArrayList<>();
		long now;

		@Override
		public Stage stage() {
			return stage;
		}

		@Override
		public void setStage(Stage stage) {
			this.stage = stage;
		}

		@Override
		public double attention() {
			return 10;
		}

		@Override
		public double tension() {
			return tension;
		}

		@Override
		public void addTension(double delta, String reason) {
			tension = Math.max(0, Math.min(100, tension + delta));
		}

		@Override
		public boolean contextFits(CardInfo card) {
			return fits.test(card);
		}

		@Override
		public FireResult fire(CardInfo card, boolean fake) {
			fired.add(card.id());
			fakes.add(fake);
			at.add(now);
			return FireResult.FIRED;
		}

		@Override
		public List<DirectorBrain.Sharer> sharers() {
			return sharers;
		}

		@Override
		public Optional<FogSpell> fogSpell() {
			return Optional.empty();
		}

		@Override
		public boolean called(CardInfo card) {
			return called.test(card);
		}
	}

	/** Rules for the brain tests: the layer's pacing cleared, a minor due every look (the gaps still hold), no quiets. */
	private static DirectorRules testRules() {
		DirectorRules rules = DirectorTestSupport.rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		ScPacing.clear(rules);
		rules.minorsPerHourMin = 500;
		rules.minorsPerHourMax = 500;
		rules.tracesMinorsPerHour = 500;
		rules.rateRefractory = 0;
		rules.tensionThreshold = 1e9;
		rules.emptySessionChance = 0;
		rules.fakeChance = 1.0;
		rules.firstHourMaxFires = -1;
		return rules;
	}

	/** Steps one player for {@code ticks} director ticks from {@code clock}, the env's {@code now} following. */
	private static DirectorBrain.Clock run(DirectorBrain brain, DirectorTestSupport.Solo solo, PaceEnv env, DirectorBrain.Clock clock, int ticks, RandomSource random) {
		for (int i = 0; i < ticks; i++) {
			clock = clock.plus(brain.rules().tickInterval);
			env.now = clock.playTicks();
			env.tension = 0;
			solo.step(brain, clock, env, random, DirectorBrain.Recorder.NONE);
		}
		return clock;
	}

	/**
	 * The first beat (R10-2): from Proximity, the first beat's card is the minor tier's next draw at the first look its
	 * moment fits, at dusk or at night at first, never a fake (every fakeable card is a fake in these rules); a moment that
	 * does not come is never held (the tier's other cards fire meanwhile); the windows run on active play, and the next
	 * beat takes over when one is over; the first that comes ends them.
	 */
	@GameTest
	public void theFirstBeatComesAtItsFirstFittingLookNeverFakeNeverHeld(GameTestHelper helper) {
		DirectorRules rules = testRules();
		long minute = rules.hourTicks / 60;
		rules.beats = List.of(new DirectorRules.Beat(BEAT_A.id(), 60 * minute), new DirectorRules.Beat(BEAT_B.id(), 30 * minute));
		rules.beatDarkFirst = 20 * minute;
		DirectorBrain brain = new DirectorBrain(rules, List.of(BEAT_A, BEAT_B, MAIN_A, MAIN_B, AMBIENT), 200);
		RandomSource random = RandomSource.create(5);

		// Morning (time of day 1000): the beat looks only at dusk or night for its first 20 minutes; its moment is there.
		DirectorTestSupport.Solo solo = DirectorTestSupport.pinned(rules);
		PaceEnv env = new PaceEnv();
		DirectorBrain.Clock clock = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS + 1000);
		solo.startAt(clock);
		clock = run(brain, solo, env, clock, 16, random);
		helper.assertFalse(env.fired.contains(BEAT_A.id()), "the beat came in the morning: " + env.fired);
		helper.assertTrue(solo.pacing.beatFrom() >= 0 && !solo.pacing.beatDone(), "the beats' window did not open");
		// On to dusk (12000): the next minor is the beat, real.
		while (Math.floorMod(clock.dayTicks(), DirectorBrain.DAY_TICKS) < 12_000 || Math.floorMod(clock.dayTicks(), DirectorBrain.DAY_TICKS) >= 23_000) {
			clock = run(brain, solo, env, clock, 1, random);
		}
		clock = run(brain, solo, env, clock, 60, random);
		int index = env.fired.indexOf(BEAT_A.id());
		helper.assertTrue(index >= 0 && !env.fakes.get(index), "the beat at dusk: " + env.fired + " " + env.fakes);
		helper.assertTrue(solo.pacing.beatDone(), "the beat came and the beats go on");
		helper.assertTrue(env.fakes.stream().filter(fake -> !fake).count() >= 1 && env.fired.stream().filter(id -> id.equals(BEAT_A.id())).count() == 1,
				"one beat only: " + env.fired);

		// Its moment never comes: never held, the tier fires its other cards; after 60 + 30 minutes of active play it is over.
		solo = DirectorTestSupport.pinned(rules);
		env = new PaceEnv();
		env.fits = card -> !card.id().equals(BEAT_A.id()) && !card.id().equals(BEAT_B.id());
		clock = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS + 13_000);
		solo.startAt(clock);
		boolean everHeld = false;
		for (int i = 0; i < 200; i++) {
			clock = run(brain, solo, env, clock, 1, random);
			everHeld |= solo.pacing.beatHeld != null;
			if (i == 130) {
				DirectorRules.Beat beat = brain.currentBeat(solo.pacing, clock);
				helper.assertTrue(beat != null && beat.cardId().equals(BEAT_B.id()), "the second beat after the first's window: " + beat);
			}
		}
		helper.assertFalse(everHeld, "a beat whose moment did not come was held as a beat");
		helper.assertTrue(env.fired.stream().filter(id -> id.startsWith("t_main")).count() >= 4, "the tier's other cards did not fire: " + env.fired);
		helper.assertTrue(solo.pacing.beatDone() && brain.currentBeat(solo.pacing, clock) == null, "the beats' windows are not over");

		// A friend beside them who already had the beat: never drawn while they stand together; a felt beat ends it.
		solo = DirectorTestSupport.pinned(rules);
		env = new PaceEnv();
		DirectorTestSupport.Solo friend = DirectorTestSupport.pinned(rules);
		friend.pacing.cycleFired.get(Tier.MINOR).add(BEAT_A.id());
		clock = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS + 13_000);
		DirectorBrain.Clock friendClock = clock;
		env.sharers = List.of(new DirectorBrain.Sharer(friend.pacing, friendClock, new PaceEnv(), DirectorBrain.Recorder.NONE));
		solo.startAt(clock);
		clock = run(brain, solo, env, clock, 40, random);
		helper.assertFalse(env.fired.contains(BEAT_A.id()), "drawn beside a friend who had it: " + env.fired);
		DirectorTestSupport.Solo felt = DirectorTestSupport.pinned(rules);
		PaceEnv feltEnv = new PaceEnv();
		DirectorTestSupport.Solo target = DirectorTestSupport.pinned(rules);
		PaceEnv targetEnv = new PaceEnv();
		DirectorBrain.Clock both = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS + 13_000);
		felt.startAt(both);
		target.startAt(both);
		targetEnv.sharers = List.of(new DirectorBrain.Sharer(felt.pacing, both, feltEnv, DirectorBrain.Recorder.NONE));
		for (int i = 0; i < 240 && !targetEnv.fired.contains(BEAT_A.id()); i++) {
			both = both.plus(rules.tickInterval);
			targetEnv.now = both.playTicks();
			targetEnv.sharers = List.of(new DirectorBrain.Sharer(felt.pacing, both, feltEnv, DirectorBrain.Recorder.NONE));
			target.step(brain, both, targetEnv, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(targetEnv.fired.contains(BEAT_A.id()) && felt.pacing.beatDone() && target.pacing.beatDone(), "a shared beat ends both: "
				+ targetEnv.fired + ", the friend's done " + felt.pacing.beatDone());
		helper.succeed();
	}

	/**
	 * The group (FIX-SC2-PACE): its share of the minor draws takes a group card whose moment fits at that look (one that
	 * does not is never held: the rest of the deck draws); two group moments are at least the gap apart (a held group card
	 * goes back when the gap starts); the same group card not again within its repeat; and with no group card fitting, the
	 * tier fires the rest as ever.
	 */
	@GameTest
	public void theGroupTakesItsShareOnlyForMomentsThatAreThere(GameTestHelper helper) {
		DirectorRules rules = testRules();
		long minute = rules.hourTicks / 60;
		rules.groupCards = Set.of(GROUP_A.id(), GROUP_B.id());
		rules.groupShare.put(Stage.PROXIMITY, new java.util.EnumMap<>(Map.of(Tier.MINOR, 1.0)));
		rules.groupGap = 30 * minute;
		rules.groupRepeat = 3 * rules.hourTicks;
		DirectorBrain brain = new DirectorBrain(rules, List.of(GROUP_A, GROUP_B, MAIN_A, MAIN_B, AMBIENT), 200);
		RandomSource random = RandomSource.create(9);
		DirectorTestSupport.Solo solo = DirectorTestSupport.pinned(rules);
		PaceEnv env = new PaceEnv();
		env.fits = card -> !card.id().equals(GROUP_A.id());
		DirectorBrain.Clock clock = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		solo.startAt(clock);
		boolean heldA = false;
		for (int i = 0; i < 12 * 120; i++) {
			clock = run(brain, solo, env, clock, 1, random);
			heldA |= GROUP_A.id().equals(solo.pacing.held(Tier.MINOR));
		}
		helper.assertFalse(heldA || env.fired.contains(GROUP_A.id()), "a group card whose moment never came was held or fired");
		List<Long> groupAt = new ArrayList<>();
		for (int i = 0; i < env.fired.size(); i++) {
			if (env.fired.get(i).equals(GROUP_B.id())) {
				groupAt.add(env.at.get(i));
			}
		}
		helper.assertTrue(groupAt.size() >= 3, "the group's share did not come: " + env.fired);
		for (int i = 1; i < groupAt.size(); i++) {
			helper.assertTrue(groupAt.get(i) - groupAt.get(i - 1) >= rules.groupRepeat, "the same group card within its repeat: " + groupAt);
		}
		helper.assertTrue(env.fired.stream().filter(id -> id.startsWith("t_main")).count() >= 20, "the rest of the deck did not fire: " + env.fired);

		// Every group card fits: the share takes them, never two within the gap, and the minors' gap holds as ever.
		solo = DirectorTestSupport.pinned(rules);
		env = new PaceEnv();
		rules.groupRepeat = 0;
		clock = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		solo.startAt(clock);
		run(brain, solo, env, clock, 6 * 120, random);
		long last = Long.MIN_VALUE / 4;
		long lastMinor = Long.MIN_VALUE / 4;
		int group = 0;
		for (int i = 0; i < env.fired.size(); i++) {
			long t = env.at.get(i);
			if (rules.groupCards.contains(env.fired.get(i))) {
				helper.assertTrue(t - last >= rules.groupGap, "two group moments within the gap");
				last = t;
				group++;
			}
			if (env.fired.get(i).startsWith("t_")) {
				helper.assertTrue(t - lastMinor >= rules.minorGap || env.fired.get(i).equals(AMBIENT.id()), "minors within the minor gap");
				lastMinor = env.fired.get(i).equals(AMBIENT.id()) ? lastMinor : t;
			}
		}
		helper.assertTrue(group >= 6, "the group fired " + group + " times in 6 h with a share of 1");
		helper.succeed();
	}

	/**
	 * The hunts' own clock (R10-4): never drawn from the major deck; none before its time (though before the first
	 * accident's), the player's first at its first fitting look whatever the slot; a later one only while the major slot
	 * is due within the pull, or when the world calls it; its own gap; the major gap after any major; never in the
	 * major deck's cycle.
	 */
	@GameTest
	public void theHuntsComeOnTheirOwnClock(GameTestHelper helper) {
		DirectorRules rules = testRules();
		long hour = rules.hourTicks;
		rules.ownClockCards = Set.of(HUNT.id());
		rules.ownClockFrom = 3 * hour;
		rules.ownClockGap = 2 * hour;
		rules.ownClockPull = hour;
		rules.firstAccident = 4 * hour;
		rules.majorEvery = new Pacing.TickRange(3 * hour, 3 * hour);
		DirectorBrain brain = new DirectorBrain(rules, List.of(HUNT, TRAP, MAJOR, MAIN_A, AMBIENT), 200);
		RandomSource random = RandomSource.create(13);
		DirectorTestSupport.Solo solo = DirectorTestSupport.pinned(rules);
		solo.pacing.firstDayTicks = -10 * DirectorBrain.DAY_TICKS;
		PaceEnv env = new PaceEnv();
		DirectorBrain.Clock clock = new DirectorBrain.Clock(hour, 2 * DirectorBrain.DAY_TICKS);
		solo.startAt(clock);
		clock = run(brain, solo, env, clock, 4 * 120 - 1, random);
		helper.assertFalse(env.fired.contains(HUNT.id()) && env.at.get(env.fired.indexOf(HUNT.id())) < rules.ownClockFrom, "a hunt before its clock");
		int first = env.fired.indexOf(HUNT.id());
		helper.assertTrue(first >= 0 && env.at.get(first) < rules.firstAccident && !env.fakes.get(first), "the first hunt at its clock: " + env.fired + " " + env.at);
		helper.assertFalse(solo.pacing.cycleFired(Tier.MAJOR).contains(HUNT.id()) || HUNT.id().equals(solo.pacing.held(Tier.MAJOR)), "the hunt in the major deck");
		clock = run(brain, solo, env, clock, 16 * 120, random);
		List<Long> hunts = new ArrayList<>();
		List<Long> majors = new ArrayList<>();
		for (int i = 0; i < env.fired.size(); i++) {
			String id = env.fired.get(i);
			if (id.equals(HUNT.id())) {
				hunts.add(env.at.get(i));
			}
			if (id.equals(HUNT.id()) || id.equals(TRAP.id()) || id.equals(MAJOR.id())) {
				majors.add(env.at.get(i));
			}
		}
		for (int i = 1; i < hunts.size(); i++) {
			helper.assertTrue(hunts.get(i) - hunts.get(i - 1) >= rules.ownClockGap, "two hunts within their own gap: " + hunts);
		}
		for (int i = 1; i < majors.size(); i++) {
			helper.assertTrue(majors.get(i) - majors.get(i - 1) >= rules.majorGap, "two majors within the major gap: " + majors);
		}
		helper.assertTrue(hunts.size() >= 2 && hunts.size() <= 8, "hunts in 20 h: " + hunts);

		// No slot near and not called: no second hunt; called: it comes at once (its gaps open).
		solo = DirectorTestSupport.pinned(rules);
		solo.pacing.firstDayTicks = -10 * DirectorBrain.DAY_TICKS;
		env = new PaceEnv();
		clock = new DirectorBrain.Clock(5 * hour, 2 * DirectorBrain.DAY_TICKS);
		solo.startAt(clock);
		clock = run(brain, solo, env, clock, 4, random);
		helper.assertTrue(env.fired.contains(HUNT.id()), "the first hunt any time after its clock: " + env.fired);
		solo.pacing.nextMajorDue = clock.playTicks() + 10 * hour;
		PaceEnv quiet = new PaceEnv();
		quiet.fits = card -> card.id().equals(HUNT.id());
		clock = run(brain, solo, quiet, clock.plus(3 * hour), 40, random);
		helper.assertFalse(quiet.fired.contains(HUNT.id()), "a later hunt with no slot near and no call: " + quiet.fired);
		quiet.called = card -> card.id().equals(HUNT.id());
		run(brain, solo, quiet, clock, 4, random);
		helper.assertTrue(quiet.fired.contains(HUNT.id()), "a called hunt did not come: " + quiet.fired);
		helper.succeed();
	}

	/**
	 * The trespass's call (R10-4): a trespass in one of his places within one in-game day calls a hunt ({@link ScPacing#calls})
	 * and makes 26's night one with no fog spell ({@code CallTrap.dark}); a day and a bit later, nothing; never without a
	 * trespass.
	 */
	@GameTest
	public void aTrespassCallsAHuntThatNightAndItsCallNeedsNoFog(GameTestHelper helper) {
		helper.assertTrue(ScPacing.calls(1000, 1000 + 23_999) && ScPacing.calls(1000, 1000 + ScPacing.CALL_TICKS), "within the day");
		helper.assertFalse(ScPacing.calls(1000, 1000 + ScPacing.CALL_TICKS + 1) || ScPacing.calls(-1, 5000) || ScPacing.calls(5000, 4000), "outside it");
		helper.assertTrue(CallTrap.CALL_TICKS == ScPacing.CALL_TICKS && CallTrap.calls(1000, 1000 + CallTrap.CALL_TICKS)
				&& !CallTrap.calls(1000, 1001 + CallTrap.CALL_TICKS) && !CallTrap.calls(-1, 10) && !CallTrap.calls(5000, 4000), "26's own window");
		helper.assertTrue(ScPacing.called("accident_sc26", 1000, 2000) && !ScPacing.called("sc_23", 1000, 2000), "only the hunts are called");
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerLevel level = helper.getLevel();
		long now = level.getServer().overworld().getGameTime();
		boolean[] night = {true};
		boolean[] fog = {false};
		CallTrap.Env env = new CallTrap.Env(new com.forzacode.a1016_02.accident.sc2.s26.Sc26Config(), new com.forzacode.a1016_02.accident.sc2.HuntData(),
				l -> List.of(), () -> night[0], () -> fog[0], () -> 7, () -> true, () -> null, false);
		helper.assertFalse(CallTrap.dark(player, env), "a clear night with no trespass");
		Sc2Data data = WorldData.get(level.getServer()).sc2();
		data.trespass(player.getUUID(), now);
		helper.assertTrue(CallTrap.dark(player, env), "a clear night after a trespass");
		night[0] = false;
		helper.assertFalse(CallTrap.dark(player, env), "a day after a trespass");
		night[0] = true;
		data.trespass(player.getUUID(), now + 1000);
		helper.assertFalse(CallTrap.dark(player, env), "a trespass in the future (a clock that went back) calls nothing");
		fog[0] = true;
		helper.assertTrue(CallTrap.dark(player, env), "a fog night");
		helper.succeed();
	}

	// --- 01+ ---

	/**
	 * 01+: at one of his places the factor is at least {@code remainsFactor} (1.5, not the away factor), a stronger
	 * place's dread stands, and it never passes {@code maxFactor}; away from them nothing changes.
	 */
	@GameTest
	public void hisPlacesRaiseTheFactorToTheRemainsFactorNeverPastTheMax(GameTestHelper helper) {
		Sc01Config cfg = new Sc01Config();
		PlaceMemory memory = new PlaceMemory();
		BlockPos at = new BlockPos(100, 64, 100);
		helper.assertTrue(close(Places.factor(memory, Level.OVERWORLD, at, 0, 0, 72_000, true, cfg), 1.5), "at a place of his");
		helper.assertTrue(close(Places.factor(memory, Level.OVERWORLD, at, 0, 0, 72_000, false, cfg), cfg.awayFactor), "away");
		helper.assertTrue(close(Places.factor(memory, Level.OVERWORLD, at, 0, 0, 72_000, cfg), cfg.awayFactor), "the old form is away");
		Places.rememberAnswer(memory, Level.OVERWORLD, at, 0, 0, cfg);
		Places.rememberAnswer(memory, Level.OVERWORLD, at, 0, 0, cfg);
		Places.rememberAnswer(memory, Level.OVERWORLD, at, 0, 0, cfg);
		helper.assertTrue(memory.places().size() == 3 && memory.places().getFirst().tier() == Tier.MINOR && !memory.places().getFirst().sighting(),
				"an answer is remembered as a minor's place: " + memory.places());
		helper.assertTrue(close(Places.factor(memory, Level.OVERWORLD, at, 0, 0, 72_000, true, cfg), 1.8), "three answers (x1.9) capped at 1.8, not lowered to 1.5");
		helper.assertTrue(close(Places.factor(memory, Level.OVERWORLD, at, 0, 0, 72_000, false, cfg), 1.8), "the answers' places count by themselves");
		Sc01Config low = new Sc01Config();
		low.maxFactor = 1.3;
		helper.assertTrue(close(Places.factor(new PlaceMemory(), Level.OVERWORLD, at, 0, 0, 72_000, true, low), 1.3), "past maxFactor");
		helper.succeed();
	}

	/**
	 * 01+: inside a place's interior, or within the places' radius (32) of its way in, is "at his places"; 33 off, another
	 * level or no place known is not. Over a place of the test's own (a line's plane, whose inside is its box).
	 */
	@GameTest
	public void atHisPlacesIsInsideOrWithinThirtyTwoOfTheWayIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc2Data data = new Sc2Data();
		BlockPos mouth = new BlockPos(-6_100_000, 70, 6_100_200);
		Remains.Place place = new Remains.Place(Remains.Kind.LINE, level.dimension(), new BoundingBox(-6_100_000, 64, 6_100_000, -6_100_000, 200, 6_100_400),
				new BlockPos(-6_100_000, 65, 6_100_100), mouth, Direction.SOUTH, Optional.empty(), 1L, 0);
		data.add(Sc2Data.Known.of(place, "s19", new Start(place.anchor(), Direction.SOUTH, 0, 1L), new CompoundTag()));
		RemainsImpl remains = RemainsImpl.over(data);
		int reach = new Sc01Config().radius;
		helper.assertTrue(DirectorImpl.atRemains(remains, level, new BlockPos(-6_100_000, 120, 6_100_300), reach), "inside the plane");
		helper.assertTrue(DirectorImpl.atRemains(remains, level, mouth.offset(31, 0, 0), reach), "31 from the way in");
		helper.assertTrue(DirectorImpl.atRemains(remains, level, mouth.offset(20, 20, 10), reach), "within 32 (3D) of the way in");
		helper.assertFalse(DirectorImpl.atRemains(remains, level, mouth.offset(33, 0, 0), reach), "33 from the way in");
		helper.assertFalse(DirectorImpl.atRemains(remains, level, mouth.offset(20, 30, 0), reach), "36 (3D) from the way in");
		helper.assertFalse(DirectorImpl.atRemains(new Remains.Stub(), level, mouth, reach), "no place known");
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		helper.assertFalse(nether != null && DirectorImpl.atRemains(remains, nether, mouth, reach), "another level");
		helper.succeed();
	}

	/**
	 * 01+: an answer of his places ({@code REMAINS_ANSWERED}) is remembered for that player, into their pacing, at the way
	 * in when they are not here; nothing for a player the director keeps no pacing for.
	 */
	@GameTest
	public void anAnswerOfHisPlacesIsRememberedAsAPlaceOfThePlayers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DirectorMemory memory = new DirectorMemory();
		UUID ana = new UUID(0x5C29L, 1L);
		UUID nobody = new UUID(0x5C29L, 2L);
		PacingMemory pacing = memory.pacing(ana, 0);
		BlockPos mouth = new BlockPos(-6_200_000, 70, 6_200_000);
		Remains.Place place = new Remains.Place(Remains.Kind.PYRAMID, level.dimension(), new BoundingBox(-6_200_100, 64, 6_200_000, -6_200_000, 160, 6_200_100),
				new BlockPos(-6_200_050, 60, 6_200_050), mouth, Direction.EAST, Optional.of(new BlockPos(-6_200_050, 160, 6_200_050)), 2L, 0);
		boolean on = com.forzacode.a1016_02.core.SelfCreated.enabled();
		boolean remembered = DirectorImpl.rememberAnswer(level, memory, place, ana);
		helper.assertTrue(remembered == on, "remembered " + remembered + " with the layer " + (on ? "on" : "off"));
		if (on) {
			List<PlaceMemory.Place> places = pacing.places().places();
			helper.assertTrue(places.size() == 1 && places.getFirst().pos().equals(mouth) && places.getFirst().tier() == Places.ANSWER_TIER
					&& places.getFirst().dimension().equals(level.dimension()), "the place: " + places);
		}
		helper.assertFalse(DirectorImpl.rememberAnswer(level, memory, place, nobody) || memory.find(nobody).isPresent(), "a player without a pacing got one");
		helper.succeed();
	}

	/**
	 * 01+: the dry runs model {@code simRemainsShare} of play at his places at {@code remainsFactor} (never past
	 * {@code maxFactor}); without a share every run is as before, and with play there all the time Traces' minors come
	 * oftener.
	 */
	@GameTest(maxTicks = 2000)
	public void theDryRunsModelTheShareOfPlayAtHisPlaces(GameTestHelper helper) {
		Sc01Config cfg = new Sc01Config();
		DirectorSim.Params params = new DirectorSim.Params().places(cfg);
		helper.assertTrue(params.remainsShare == 0.06 && params.remainsFactor == 1.5, "the share " + params.remainsShare + " at " + params.remainsFactor);
		Sc01Config high = new Sc01Config();
		high.remainsFactor = 2.5;
		helper.assertTrue(new DirectorSim.Params().places(high).remainsFactor == high.maxFactor, "past maxFactor");
		helper.assertTrue(new DirectorSim.Params().remainsShare == 0, "a share by default");
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		double[] minors = new double[2];
		for (int k = 0; k < 2; k++) {
			Sc01Config there = new Sc01Config();
			there.simDreadShare = 0;
			there.simTrustedShare = 0;
			there.awayFactor = 1.0;
			there.simRemainsShare = k == 0 ? 0 : 1;
			List<DirectorSim.Result> runs = new ArrayList<>();
			for (long seed : DirectorApi.tuningSeeds().subList(0, 48)) {
				DirectorConfig config = new DirectorConfig();
				DirectorRules rules = DirectorRules.from(new Pacing(), config, com.forzacode.a1016_02.debug.Playthrough.profile(seed, Tempo.SLOW_BURN));
				DirectorSim.Params p = DirectorSim.Params.from(config, rules).places(there);
				p.hours = 12;
				p.seed = seed;
				p.attention = config.attentionNeutral;
				runs.add(DirectorSim.run(rules, deck, Math.max(config.historySize, 1), new DirectorMemory(), Stage.ALONE, 0, new DirectorBrain.Clock(0, 0), p));
			}
			minors[k] = DirectorTestSupport.FourB.of(runs, deck).tracesMinorsPerHour();
		}
		A1016_02.LOGGER.info(String.format(Locale.ROOT, "[a1016] sc2 01+: Traces minors %.3f/h away, %.3f/h always at his places", minors[0], minors[1]));
		helper.assertTrue(minors[1] > minors[0] * 1.05, String.format(Locale.ROOT, "Traces minors %.3f there, %.3f away", minors[1], minors[0]));
		helper.succeed();
	}

	/**
	 * 01+ and FIX-SC2-PACE: {@code sc01} version 3 (opaque keys, hand edits) steps to 5, and version 4 to 5: every value
	 * kept, o and p and the second wave's pacing (q to z) at their defaults and written under their letters.
	 */
	@GameTest
	public void theSectionStepsFromThreeOnAddingOnlyItsKeys(GameTestHelper helper) {
		Sc01Config fresh = new Sc01Config();
		for (int from : List.of(3, 4)) {
			JsonObject old = new JsonObject();
			old.addProperty("version", from);
			old.addProperty("a", 30);
			old.addProperty("k", 0.8);
			old.addProperty("l", 0.25);
			if (from == 4) {
				old.addProperty("o", 1.4);
				old.addProperty("p", 0.03);
			}
			JsonObject sections = new JsonObject();
			sections.add(Sc01Config.SECTION, old);
			Sc01Config cfg = ModConfig.readSection(sections, Sc01Config.SECTION, Sc01Config.class, Sc01Config::new);
			String at = "from version " + from + ": ";
			helper.assertTrue(cfg.version == 5 && Sc01Config.CURRENT == 5, at + "version " + cfg.version);
			helper.assertTrue(cfg.maxPlaces == 30 && cfg.awayFactor == 0.8 && cfg.simDreadShare == 0.25, at + "a hand edit was lost");
			helper.assertTrue(cfg.remainsFactor == (from == 4 ? 1.4 : 1.5) && cfg.simRemainsShare == 0.06, at + "o and p: " + cfg.remainsFactor + ", "
					+ cfg.simRemainsShare);
			helper.assertTrue(cfg.weights.equals(fresh.weights) && java.util.Arrays.equals(cfg.tracesShares, fresh.tracesShares)
					&& java.util.Arrays.equals(cfg.laterShares, fresh.laterShares) && cfg.gapMinutes == fresh.gapMinutes && cfg.repeatHours == fresh.repeatHours
					&& java.util.Arrays.equals(cfg.beatMinutes, fresh.beatMinutes) && cfg.beatDarkMinutes == fresh.beatDarkMinutes
					&& cfg.huntFromHours == fresh.huntFromHours && cfg.huntGapHours == fresh.huntGapHours && cfg.huntPullMinutes == fresh.huntPullMinutes,
					at + "q to z not at their defaults");
			JsonObject written = new Gson().toJsonTree(cfg).getAsJsonObject();
			for (String key : List.of("o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z")) {
				helper.assertTrue(written.has(key), at + "not written: " + key + " in " + written.keySet());
			}
			helper.assertFalse(written.has("remainsFactor") || written.has("weights"), at + "a key in words: " + written.keySet());
		}
		helper.succeed();
	}

	/**
	 * FIX-SC2-PACE: {@code sc25} version 1 steps to 2: the day's roll ({@code m}) 0.5 to 1.0 where still 0.5, a hand edit
	 * kept; a file already at 2 keeps its value whatever it is.
	 */
	@GameTest
	public void thePacksDayRollGivesWayToTheOwnClock(GameTestHelper helper) {
		double[][] cases = {{1, 0.5, 1.0}, {1, 0.7, 0.7}, {2, 0.5, 0.5}};
		for (double[] c : cases) {
			JsonObject old = new JsonObject();
			old.addProperty("version", (int) c[0]);
			old.addProperty("m", c[1]);
			old.addProperty("k", 4);
			JsonObject sections = new JsonObject();
			sections.add(Sc25Config.SECTION, old);
			Sc25Config cfg = ModConfig.readSection(sections, Sc25Config.SECTION, Sc25Config.class, Sc25Config::new);
			helper.assertTrue(cfg.version == 2 && Sc25Config.CURRENT == 2 && cfg.nightChance == c[2] && cfg.maxPerWorld == 4,
					"version " + (int) c[0] + " with m " + c[1] + ": version " + cfg.version + ", m " + cfg.nightChance + ", k " + cfg.maxPerWorld);
		}
		helper.assertTrue(new Sc25Config().nightChance == 1.0, "the default");
		helper.succeed();
	}

	private static boolean within(double now, double was) {
		return was <= 0 ? now <= 0 : Math.abs(now / was - 1) <= 0.15;
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1e-9;
	}
}
