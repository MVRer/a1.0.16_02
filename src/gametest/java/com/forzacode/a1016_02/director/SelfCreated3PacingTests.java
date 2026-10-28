package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.director.sc.Sc01Config;
import com.forzacode.a1016_02.director.sc.ScPacing;
import com.forzacode.a1016_02.director.sc.SimFits;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsAnswers;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * R3-P's game tests (docs/SELF_CREATED_2_R3.md, "Pacing (D-119, R3-P)"): the director's pacing with round 3's places (from
 * the world's creation, asleep until Traces, denser and found from far), their answers, the crumbs' runtime moments and
 * the depths. The dry runs are {@link SelfCreated2PacingTests}' (192 seeds of 20 h per tempo, the registered deck, the
 * second wave's moments as {@code SimFits} has them); this class adds round 3's bounds, its rows of "what a player meets"
 * and a dense world, and the live hooks (01+ asleep, the wave's moments that are no card).
 */
public class SelfCreated3PacingTests {
	/** A world much denser than the dry runs' (every hour of play there a third: visits three times as often). */
	static final double DENSE_SHARE = 0.3;
	/** The sessions the rows are read at. */
	static final List<Integer> SESSIONS = List.of(1, 3, 5);

	/**
	 * One run per tuning seed of this tempo over this deck: the rules with {@code pacing}'s wave (its {@code s}, {@code t}
	 * and the rest), {@code places}' shares of play (null: none, the visits at the section's default), the moments as
	 * given (null: the dry runs' own), then {@code tweak}.
	 */
	static List<DirectorSim.Result> runs(Tempo tempo, List<CardInfo> deck, Sc01Config pacing, @Nullable Sc01Config places,
			@Nullable Predicate<CardInfo> never, @Nullable ToDoubleFunction<CardInfo> chance, @Nullable Consumer<DirectorSim.Params> tweak) {
		List<DirectorSim.Result> out = new ArrayList<>();
		Pacing timing = new Pacing();
		for (long seed : DirectorApi.tuningSeeds()) {
			DirectorConfig config = new DirectorConfig();
			DirectorRules rules = DirectorRules.from(timing, config, com.forzacode.a1016_02.debug.Playthrough.profile(seed, tempo));
			ScPacing.apply(rules, pacing, timing.paceFactor(tempo));
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
			if (tweak != null) {
				tweak.accept(params);
			}
			out.add(DirectorSim.run(rules, deck, Math.max(config.historySize, 1), new DirectorMemory(), Stage.ALONE, 0, new DirectorBrain.Clock(0, 0), params));
		}
		return out;
	}

	/**
	 * His places in a set of runs (R3-P): visits a played hour from Traces; answers a visit, a played hour from Traces, the
	 * most in one in-game day and in one visit; answers that came in a quiet but a player's first (the kit refuses them);
	 * the most of the wave's moments in an hour the director paces (its cards and the crumbs, 33 left out) and with the
	 * depths; and per session (1, 3, 5): the runs that lasted that many, a place's sign seen by then (T4 folded in:
	 * {@link SimFits#SIGN_PER_SESSION} a session, on dice of the run's own), a place entered awake, an answer felt.
	 */
	record PlaceRows(double visitsPerHour, double answersPerVisit, double answersPerHour, int mostADay, int mostAVisit, int inQuiet,
			int mostDirectorAnHour, int mostAnHour, int clustered, Map<Integer, Integer> runs, Map<Integer, Integer> sign, Map<Integer, Integer> entered,
			Map<Integer, Integer> answered) {
		static PlaceRows of(List<DirectorSim.Result> results) {
			double awakeHours = 0;
			int visits = 0;
			int answers = 0;
			int mostADay = 0;
			int mostAVisit = 0;
			int inQuiet = 0;
			int mostDirector = 0;
			int mostAll = 0;
			int clustered = 0;
			Map<Integer, Integer> runs = new HashMap<>();
			Map<Integer, Integer> sign = new HashMap<>();
			Map<Integer, Integer> entered = new HashMap<>();
			Map<Integer, Integer> answered = new HashMap<>();
			for (DirectorSim.Result r : results) {
				long hour = r.rules.hourTicks;
				awakeHours += (r.stageTicks(Stage.TRACES) + r.stageTicks(Stage.PROXIMITY) + r.stageTicks(Stage.TELLING)) / (double) hour;
				Map<Long, Integer> byDay = new HashMap<>();
				boolean answeredBefore = false;
				int thisVisit = 0;
				List<Long> director = new ArrayList<>();
				List<Long> all = new ArrayList<>();
				for (DirectorSim.Event e : r.events) {
					switch (e.kind()) {
						case VISIT -> {
							visits++;
							answeredBefore |= thisVisit > 0;
							thisVisit = 0;
						}
						case ANSWER -> {
							// A player's first answers (their first visit's) come even in a quiet, as each one's first time does.
							answers++;
							mostAVisit = Math.max(mostAVisit, ++thisVisit);
							mostADay = Math.max(mostADay, byDay.merge(e.day(), 1, Integer::sum));
							inQuiet += e.flag() && answeredBefore ? 1 : 0;
						}
						case MOMENT -> {
							all.add(e.play());
							if (!e.cardId().equals("33")) {
								director.add(e.play());
							}
						}
						case FIRE, FELT -> {
							if (SelfCreated2PacingTests.feature(e.cardId()) != null) {
								all.add(e.play());
								director.add(e.play());
							}
						}
						default -> {
						}
					}
				}
				mostDirector = Math.max(mostDirector, mostInAnHour(director, hour));
				int most = mostInAnHour(all, hour);
				mostAll = Math.max(mostAll, most);
				clustered += most > 2 ? 1 : 0;
				List<Long> ends = r.events.stream().filter(e -> e.kind() == DirectorSim.Kind.SESSION_END).map(DirectorSim.Event::play).toList();
				// SplittableRandom: a fresh java.util.Random's first doubles for neighbouring seeds lie together.
				java.util.SplittableRandom signs = new java.util.SplittableRandom(r.params.seed ^ 0x5C3_7404L);
				int seenAt = Integer.MAX_VALUE;
				for (int s = 1; s <= ends.size() && seenAt == Integer.MAX_VALUE; s++) {
					seenAt = signs.nextDouble() < SimFits.SIGN_PER_SESSION ? s : Integer.MAX_VALUE;
				}
				for (int n : SESSIONS) {
					if (ends.size() < n) {
						continue;
					}
					long end = ends.get(n - 1);
					runs.merge(n, 1, Integer::sum);
					sign.merge(n, seenAt <= n ? 1 : 0, Integer::sum);
					entered.merge(n, r.events.stream().anyMatch(e -> e.kind() == DirectorSim.Kind.VISIT && e.play() <= end) ? 1 : 0, Integer::sum);
					answered.merge(n, r.events.stream().anyMatch(e -> e.kind() == DirectorSim.Kind.ANSWER && e.play() <= end) ? 1 : 0, Integer::sum);
				}
			}
			return new PlaceRows(awakeHours <= 0 ? 0 : visits / awakeHours, visits == 0 ? 0 : answers / (double) visits,
					awakeHours <= 0 ? 0 : answers / awakeHours, mostADay, mostAVisit, inQuiet, mostDirector, mostAll, clustered, runs, sign, entered, answered);
		}

		static int mostInAnHour(List<Long> times, long hour) {
			List<Long> sorted = new ArrayList<>(times);
			sorted.sort(null);
			int most = 0;
			for (int i = 0; i < sorted.size(); i++) {
				int n = 0;
				for (int j = i; j < sorted.size() && sorted.get(j) - sorted.get(i) < hour; j++) {
					n++;
				}
				most = Math.max(most, n);
			}
			return most;
		}

		double share(Map<Integer, Integer> counts, int n) {
			int all = runs.getOrDefault(n, 0);
			return all == 0 ? 0 : counts.getOrDefault(n, 0) / (double) all;
		}

		String summary() {
			StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
					"his places: %.2f visits/h awake, %.2f answers a visit, %.2f answers/h awake, at most %d an in-game day and %d a visit, %d in a"
							+ " quiet; the wave's moments at most %d in an hour from the director, %d with the depths (%d of %d runs had an hour of 3+) |",
					visitsPerHour, answersPerVisit, answersPerHour, mostADay, mostAVisit, inQuiet, mostDirectorAnHour, mostAnHour, clustered,
					runs.getOrDefault(1, 0)));
			for (int n : SESSIONS) {
				out.append(String.format(Locale.ROOT, " by session %d (%d runs): a sign %.0f%%, entered awake %.0f%%, an answer %.0f%%;", n,
						runs.getOrDefault(n, 0), 100 * share(sign, n), 100 * share(entered, n), 100 * share(answered, n)));
			}
			return out.toString();
		}
	}

	/**
	 * Round 3's bounds (D-119), every tempo, every card at its own dice with {@code sc01}'s shares (01+ at round 3's
	 * share, only while awake): from Proximity 0.40 to 0.55 of the second wave's real moments a played hour (its cards
	 * and the moments that are no card); never more than two of them in an hour from the director (its cards and the
	 * crumbs; the depths come when the player goes down and are logged); the first real one by Proximity + 1.5 h in four
	 * runs of five; the hunts on their own clock (the hard limits); every answer within its caps (a visit, an in-game day,
	 * none in a quiet but a player's first). The rows: a sign by session 3 for at least 80 % of players, an answer felt by
	 * session 5 for at least half. {@link SelfCreated2PacingTests} holds D-070, DESIGN 4b and the traps in the four cases
	 * over the same dry runs.
	 */
	@GameTest(maxTicks = 6000)
	public void theDryRunsKeepRoundThreesBoundsAndMeetItsPlaces(GameTestHelper helper) {
		List<CardInfo> all = DirectorTestSupport.registryDeck();
		Map<String, CardInfo> byId = new LinkedHashMap<>();
		all.forEach(card -> byId.put(card.id(), card));
		Sc01Config section = new Sc01Config();
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> runs = runs(tempo, all, section, section, null, null, null);
			SelfCreated2PacingTests.Experience wave = SelfCreated2PacingTests.Experience.of(runs, byId);
			PlaceRows places = PlaceRows.of(runs);
			DirectorTestSupport.FirstHour first = DirectorTestSupport.FirstHour.of(runs);
			A1016_02.LOGGER.info("[a1016] sc3 pacing {}: {}; {}; {}", tempo, first.summary(), wave.summary(), places.summary());
			String at = tempo + ": " + wave.summary() + "; " + places.summary() + ": ";
			for (DirectorSim.Result run : runs) {
				helper.assertTrue(run.violations.isEmpty(), at + "seed " + run.params.seed + ": " + run.violations);
				List<String> hard = DirectorTestSupport.feltHardLimits(run, all);
				helper.assertTrue(hard.isEmpty(), at + "seed " + run.params.seed + ": " + hard);
			}
			helper.assertTrue(first.by30() * 4 >= first.runs() * 3 && first.by45() * 10 >= first.runs() * 9, at + "the first hour: " + first.summary());
			helper.assertTrue(wave.proximityRealPerHour() >= 0.40 && wave.proximityRealPerHour() <= 0.55, at + "not 0.40 to 0.55 real an hour from Proximity");
			helper.assertTrue(places.mostDirectorAnHour() <= 2, at + "more than two of the wave's moments in an hour from the director");
			helper.assertTrue(wave.beatShare() >= 0.8, at + "the first real moment comes late");
			helper.assertTrue(wave.beforeStage() == 0, at + "a moment before its stage");
			helper.assertTrue(places.mostAVisit() <= SimFits.ANSWERS_PER_VISIT_MAX + 2 && places.mostADay() <= SimFits.ANSWERS_PER_DAY
					&& places.inQuiet() == 0, at + "an answer past its caps");
			helper.assertTrue(places.share(places.sign(), 3) >= 0.8, at + "a sign by session 3");
			helper.assertTrue(places.share(places.answered(), 5) >= 0.5, at + "an answer by session 5");
		}
		helper.succeed();
	}

	/**
	 * A dense world (a third of every hour of play at his places, visits three times the dry runs'), every tempo: the
	 * answers keep their caps (a visit, an in-game day, the quiet) and their tension buys quiets rather than a stream:
	 * at most 1.5 answers an hour of awake play on average; the director still paces the wave (never more than two of
	 * its moments in an hour, every hard limit).
	 */
	@GameTest(maxTicks = 4000)
	public void aDenseWorldKeepsItsAnswersToTheirCaps(GameTestHelper helper) {
		List<CardInfo> all = DirectorTestSupport.registryDeck();
		Map<String, CardInfo> byId = new LinkedHashMap<>();
		all.forEach(card -> byId.put(card.id(), card));
		Sc01Config section = new Sc01Config();
		Sc01Config dense = new Sc01Config();
		dense.simRemainsShare = DENSE_SHARE;
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> runs = runs(tempo, all, section, dense, null, null, null);
			SelfCreated2PacingTests.Experience wave = SelfCreated2PacingTests.Experience.of(runs, byId);
			PlaceRows places = PlaceRows.of(runs);
			DirectorTestSupport.FourB rates = DirectorTestSupport.FourB.of(runs, all);
			A1016_02.LOGGER.info("[a1016] sc3 dense {}: {}; {}; {}", tempo, rates.summary(), wave.summary(), places.summary());
			String at = tempo + " (dense): " + places.summary() + ": ";
			for (DirectorSim.Result run : runs) {
				helper.assertTrue(run.violations.isEmpty(), at + "seed " + run.params.seed + ": " + run.violations);
				List<String> hard = DirectorTestSupport.feltHardLimits(run, all);
				helper.assertTrue(hard.isEmpty(), at + "seed " + run.params.seed + ": " + hard);
			}
			helper.assertTrue(places.mostAVisit() <= SimFits.ANSWERS_PER_VISIT_MAX + 2 && places.mostADay() <= SimFits.ANSWERS_PER_DAY
					&& places.inQuiet() == 0, at + "an answer past its caps");
			helper.assertTrue(places.answersPerHour() <= 1.5, at + "the answers come as a stream");
			helper.assertTrue(places.mostDirectorAnHour() <= 2, at + "more than two of the wave's moments in an hour from the director");
		}
		helper.succeed();
	}

	/**
	 * The live kit in a dense world (R3-P): six places a few blocks apart, a player going from one to the next on one
	 * in-game day: an answer comes once a player a day whichever place they are in (33b and 33c's own rules, and the
	 * common rule), a place's day cap holds for a second player, the next day it may come again; a player in their quiet
	 * has only their first.
	 */
	@GameTest
	public void aDenseWorldsAnswersKeepThePerPlayerAndPerPlaceDayCaps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc2Data data = new Sc2Data();
		List<Remains.Place> places = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			BlockPos anchor = new BlockPos(-6_200_000 + i * 40, 70, 6_200_000);
			Remains.Place place = new Remains.Place(Remains.Kind.FLOOR, level.dimension(), new BoundingBox(anchor.getX(), 64, anchor.getZ(),
					anchor.getX() + 20, 80, anchor.getZ() + 20), anchor, anchor.south(), Direction.SOUTH, Optional.empty(), 7L + i, 0);
			data.add(Sc2Data.Known.of(place, "s20", new Start(anchor, Direction.SOUTH, 0, 7L + i), new CompoundTag()));
			places.add(place);
		}
		TestPlayer ana = new TestPlayer(level, "ana");
		TestPlayer ben = new TestPlayer(level, "ben");
		Map<Remains.Place, List<ServerPlayer>> inside = new HashMap<>();
		java.util.Set<UUID> quiet = new java.util.HashSet<>();
		long[] day = {50};
		List<String> answered = new ArrayList<>();
		Answers answers = Answers.over(new Answers.Env(s -> data, Services.traces().forced(), (l, cells) -> true,
				(s, place) -> List.copyOf(inside.getOrDefault(place, List.of())), quiet::contains, uuid -> 400_000L, s -> null, () -> day[0],
				() -> 10_000L, (l, place, answer, player) -> answered.add(answer + ":" + player.getName().getString() + "@" + places.indexOf(place))));
		Answers.Planner nothing = (lvl, place, in) -> Answers.Edit.of(List.of(), traces -> true);
		List<Answers.Rules> rules = List.of(DepthsAnswers.lureRules(), DepthsAnswers.roomsRules(true), Answers.Rules.of("t3p", "world:t3p"));
		for (Answers.Rules rule : rules) {
			for (Remains.Place place : places) {
				inside.clear();
				inside.put(place, List.of(ana));
				answers.submit(level, place, rule, nothing);
			}
			long got = answered.stream().filter(a -> a.startsWith(rule.answer() + ":ana")).count();
			helper.assertTrue(got == 1, rule.answer() + ": ana had it " + got + " times on one day in six places: " + answered);
		}
		// A place's day cap (the common rule: once a place a day): ben in ana's place has none; in the next one he has his.
		inside.clear();
		inside.put(places.getFirst(), List.of(ben));
		answers.submit(level, places.getFirst(), rules.get(2), nothing);
		inside.clear();
		inside.put(places.get(1), List.of(ben));
		answers.submit(level, places.get(1), rules.get(2), nothing);
		helper.assertTrue(answered.contains("t3p:ben@1") && !answered.contains("t3p:ben@0"), "the place's day cap: " + answered);
		// The next day: once more, in any of them.
		day[0]++;
		inside.clear();
		inside.put(places.get(3), List.of(ana));
		answers.submit(level, places.get(3), rules.getFirst(), nothing);
		helper.assertTrue(answered.contains(rules.getFirst().answer() + ":ana@3"), "the next day: " + answered);
		// In their quiet a player has their first of an answer only.
		quiet.add(ana.getUUID());
		Answers.Rules fresh = Answers.Rules.of("t3q", "world:t3q").perPlaceDay(Integer.MAX_VALUE);
		inside.clear();
		inside.put(places.get(4), List.of(ana));
		helper.assertTrue(answers.submit(level, places.get(4), fresh, nothing).status() == Answers.Status.DONE, "the first in a quiet");
		day[0]++;
		helper.assertTrue(answers.submit(level, places.get(4), fresh, nothing).status() == Answers.Status.REFUSED, "a second in a quiet");
		helper.succeed();
	}

	/** 01+ while his places sleep (D-108, R3-P): no factor at a place while they are asleep, the factor while awake. */
	@GameTest
	public void hisPlacesGiveNoFactorWhileAsleep(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc2Data data = new Sc2Data();
		BlockPos mouth = new BlockPos(-6_300_000, 70, 6_300_200);
		Remains.Place place = new Remains.Place(Remains.Kind.LINE, level.dimension(), new BoundingBox(-6_300_000, 64, 6_300_000, -6_300_000, 200, 6_300_400),
				new BlockPos(-6_300_000, 65, 6_300_100), mouth, Direction.SOUTH, Optional.empty(), 1L, 0);
		data.add(Sc2Data.Known.of(place, "s19", new Start(place.anchor(), Direction.SOUTH, 0, 1L), new CompoundTag()));
		RemainsImpl remains = RemainsImpl.over(data);
		int reach = new Sc01Config().radius;
		helper.assertTrue(DirectorImpl.remainsFactorHere(null, remains, level, mouth, reach), "awake, at the way in");
		helper.assertFalse(DirectorImpl.remainsFactorHere("asleep", remains, level, mouth, reach), "asleep, at the way in");
		helper.assertFalse(DirectorImpl.remainsFactorHere(null, remains, level, mouth.offset(40, 0, 0), reach), "awake, 40 off");
		MinecraftServer server = level.getServer();
		if (!com.forzacode.a1016_02.core.HerobrineState.get(server).stage().atLeast(Stage.TRACES)) {
			helper.assertTrue(remains.asleep(server) != null && !Remains.awake(server), "the test world is under Traces: asleep");
		}
		helper.succeed();
	}

	/**
	 * The wave's moments that are no card (R3-P): 32b and 32c through the director's hook and an answer below the pit
	 * start the player's wave gap; another answer does not; a player the director keeps no pacing for, or rules without
	 * a gap, change nothing; and in the dry runs the crumbs' moments never come inside a gap while 33's start one.
	 */
	@GameTest(maxTicks = 2000)
	public void theCrumbsAndTheDepthsStartTheWaveGap(GameTestHelper helper) {
		DirectorMemory memory = new DirectorMemory();
		UUID ana = UUID.randomUUID();
		PacingMemory pacing = memory.pacing(ana, 0);
		long gap = 30 * 60 * 20;
		helper.assertFalse(DirectorImpl.inWaveGap(pacing, 100_000, gap), "no gap before any moment");
		helper.assertTrue(DirectorImpl.waveMoment(memory, ana, 100_000, gap) && pacing.lastGroup() == 100_000, "the moment starts the gap");
		helper.assertTrue(DirectorImpl.inWaveGap(pacing, 100_000 + gap - 1, gap) && !DirectorImpl.inWaveGap(pacing, 100_000 + gap, gap), "30 min");
		helper.assertFalse(DirectorImpl.waveMoment(memory, UUID.randomUUID(), 100_000, gap), "no pacing, no gap");
		helper.assertFalse(DirectorImpl.waveMoment(memory, ana, 200_000, 0) || pacing.lastGroup() != 100_000, "rules without a gap");
		helper.assertTrue(DirectorImpl.belowThePit("33b") && DirectorImpl.belowThePit("33f") && !DirectorImpl.belowThePit("16b")
				&& !DirectorImpl.belowThePit(null), "below the pit");
		helper.assertTrue(Services.director().waveMomentMay(helper.getLevel().getServer(), UUID.randomUUID()), "a stranger may");
		long hour = 72_000;
		helper.assertTrue(DirectorImpl.waveMomentMay(null, 0, hour, gap), "no pacing kept: nothing held");
		helper.assertFalse(DirectorImpl.waveMomentMay(pacing, hour - 1, hour, gap), "in the first hour");
		helper.assertFalse(DirectorImpl.waveMomentMay(pacing, 100_000 + gap - 1, hour, gap), "in the gap");
		helper.assertTrue(DirectorImpl.waveMomentMay(pacing, 100_000 + gap, hour, gap), "after the gap");
		List<CardInfo> all = DirectorTestSupport.registryDeck();
		List<DirectorSim.Result> runs = runs(Tempo.SLOW_BURN, all, new Sc01Config(), new Sc01Config(), null, null, p -> {
			p.crumbsPerHour = 2;
			p.visitsPerHour = 2;
			p.depthsShare = 1;
		});
		int crumbs = 0;
		int depths = 0;
		for (DirectorSim.Result r : runs.subList(0, 48)) {
			helper.assertTrue(r.violations.isEmpty(), "seed " + r.params.seed + ": " + r.violations);
			crumbs += (int) r.events.stream().filter(e -> e.kind() == DirectorSim.Kind.MOMENT && e.cardId().startsWith("32")).count();
			depths += (int) r.events.stream().filter(e -> e.kind() == DirectorSim.Kind.MOMENT && e.cardId().equals("33")).count();
		}
		helper.assertTrue(crumbs > 0 && depths > 0, "no moments: " + crumbs + ", " + depths);
		helper.succeed();
	}

	/**
	 * {@code sc01} version 5 steps to 6 (R3-P): {@code p} 0.06 to 0.10 and {@code s}'s minor share 0.75 to 0.4 where still
	 * version 5's, a hand edit kept (the ambient share and every other key as they were).
	 */
	@GameTest
	public void theSectionStepsFromFiveToSixKeepingHandEdits(GameTestHelper helper) {
		for (boolean edited : List.of(false, true)) {
			com.google.gson.JsonObject old = new com.google.gson.JsonObject();
			old.addProperty("version", 5);
			old.addProperty("p", edited ? 0.08 : 0.06);
			com.google.gson.JsonArray shares = new com.google.gson.JsonArray();
			shares.add(edited ? 0.2 : 0.3);
			shares.add(edited ? 0.6 : 0.75);
			old.add("s", shares);
			old.addProperty("t", 40);
			com.google.gson.JsonObject sections = new com.google.gson.JsonObject();
			sections.add(Sc01Config.SECTION, old);
			Sc01Config cfg = com.forzacode.a1016_02.core.ModConfig.readSection(sections, Sc01Config.SECTION, Sc01Config.class, Sc01Config::new);
			String at = (edited ? "edited" : "defaults") + ": ";
			helper.assertTrue(cfg.version == Sc01Config.CURRENT && Sc01Config.CURRENT == 6, at + "version " + cfg.version);
			helper.assertTrue(cfg.simRemainsShare == (edited ? 0.08 : 0.10), at + "p " + cfg.simRemainsShare);
			helper.assertTrue(java.util.Arrays.equals(cfg.laterShares, edited ? new double[] {0.2, 0.6} : new double[] {0.3, 0.4}),
					at + "s " + java.util.Arrays.toString(cfg.laterShares));
			helper.assertTrue(cfg.gapMinutes == 40, at + "t " + cfg.gapMinutes);
		}
		helper.succeed();
	}

	/**
	 * The tuning's sweep (when asked: {@code -Da1016_02.sc3pace=true}): the wave's share {@code s} and the share of play
	 * at his places {@code p} around the defaults, every tempo, logged for the docs.
	 */
	@GameTest(maxTicks = 20000)
	public void theSweepOfTheWavesShareAndThePlacesShareWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc3pace")) {
			helper.succeed();
			return;
		}
		List<CardInfo> all = DirectorTestSupport.registryDeck();
		Map<String, CardInfo> byId = new LinkedHashMap<>();
		all.forEach(card -> byId.put(card.id(), card));
		Map<String, Consumer<DirectorSim.Params>> tweaks = new LinkedHashMap<>();
		tweaks.put("as is", p -> {
		});
		tweaks.put("no depths moment", p -> p.depthsShare = 0);
		tweaks.put("no crumbs", p -> {
			p.crumbsPerHour = 0;
			p.crumbsSharedPerHour = 0;
		});
		tweaks.put("no answer tension", p -> p.answerTension = 0);
		String only = System.getProperty("a1016_02.sc3pace.only", "");
		for (double[] variant : new double[][] {{0.3, 0.4, 0.10}, {0.3, 0.5, 0.10}, {0.2, 0.4, 0.10}, {0.25, 0.45, 0.10}}) {
			Sc01Config cfg = new Sc01Config();
			cfg.laterShares = new double[] {variant[0], variant[1]};
			cfg.simRemainsShare = variant[2];
			for (Map.Entry<String, Consumer<DirectorSim.Params>> tweak : tweaks.entrySet()) {
				if (!only.isEmpty() && !only.contains(tweak.getKey())) {
					continue;
				}
				for (Tempo tempo : Tempo.values()) {
					List<DirectorSim.Result> runs = runs(tempo, all, cfg, cfg, null, null, tweak.getValue());
					A1016_02.LOGGER.info("[a1016] sc3 sweep s {} {} p {} {} {}: {}; {}; {}", variant[0], variant[1], variant[2], tweak.getKey(), tempo,
							DirectorTestSupport.FourB.of(runs, all).summary(), SelfCreated2PacingTests.Experience.of(runs, byId).summary(),
							PlaceRows.of(runs).summary());
				}
			}
		}
		helper.succeed();
	}

	static final class TestPlayer extends ServerPlayer {
		TestPlayer(ServerLevel level, String name) {
			super(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes(("sc3p " + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name),
					ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
			setHealth(20.0F);
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}
}
