package com.forzacode.a1016_02.ending;

import static com.forzacode.a1016_02.ending.EndingTestSupport.facts;

import java.util.Optional;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The commit rules of Endings A, B and C as pure functions of the facts, with a forced clock: each condition on its
 * own, the order between paths, and the director flags the paths use.
 */
public class EndingRuleTests {
	static final long DAY = GameClock.TICKS_PER_DAY;
	static final long NEVER = EndingState.NEVER;
	static final int OBEY_DAYS = 3;

	static void expectWhy(GameTestHelper helper, Optional<String> why, String contains, String what) {
		helper.assertTrue(why.isPresent(), what + ": it committed");
		helper.assertTrue(why.get().contains(contains), what + ": expected '" + contains + "' in '" + why.get() + "'");
	}

	/** "Stop." at day 20 after naming him at day 19; the last read at day 18, the last trace visit on day 17. */
	static EndingFacts obeying(long now) {
		return facts(Stage.TELLING, true, now, 20 * DAY, 19 * DAY, 19 * DAY, 18 * DAY, 17, NEVER, 4, 0, 30, 0, 0, 0, false, 0, 0, 0);
	}

	@GameTest
	public void endingACommitsOnlyOnceTheyObey(GameTestHelper helper) {
		EndingConfig cfg = new EndingConfig();
		expectWhy(helper, EndingRules.aWhy(obeying(21 * DAY), cfg, OBEY_DAYS), "obeying", "one day after Stop.");
		expectWhy(helper, EndingRules.aWhy(obeying(22 * DAY + DAY / 2), cfg, OBEY_DAYS), "obeying", "two and a half days after Stop.");
		helper.assertTrue(EndingRules.aWhy(obeying(23 * DAY), cfg, OBEY_DAYS).isEmpty(), "three quiet days after Stop. did not commit A: "
				+ EndingRules.aWhy(obeying(23 * DAY), cfg, OBEY_DAYS));
		helper.assertTrue(EndingRules.decide(obeying(23 * DAY), cfg, OBEY_DAYS).map(d -> d.path() == EndingPath.A).orElse(false),
				"decide did not pick A");

		// No "Stop." yet, or too early a stage.
		EndingFacts noStop = facts(Stage.TELLING, false, 30 * DAY, NEVER, 10 * DAY, 10 * DAY, NEVER, -1, NEVER, 1, 0, 0, 0, 0, 0, false, 0, 0, 0);
		expectWhy(helper, EndingRules.aWhy(noStop, cfg, OBEY_DAYS), "Stop.", "without Stop.");
		EndingFacts early = facts(Stage.PROXIMITY, true, 30 * DAY, 20 * DAY, NEVER, NEVER, NEVER, -1, NEVER, 0, 0, 0, 0, 0, 0, false, 0, 0, 0);
		expectWhy(helper, EndingRules.aWhy(early, cfg, OBEY_DAYS), "stage", "in Proximity");

		// Naming him after "Stop." loses A for good.
		EndingFacts named = facts(Stage.TELLING, true, 40 * DAY, 20 * DAY, 21 * DAY, 21 * DAY, NEVER, -1, NEVER, 5, 1, 0, 0, 0, 0, false, 0, 0, 0);
		expectWhy(helper, EndingRules.aWhy(named, cfg, OBEY_DAYS), "named him", "named after Stop.");

		// A telling without his name after "Stop." starts the obeying clock again.
		EndingFacts told = facts(Stage.TELLING, true, 24 * DAY, 20 * DAY, 22 * DAY, 19 * DAY, NEVER, -1, NEVER, 5, 1, 0, 0, 0, 0, false, 0, 0, 0);
		expectWhy(helper, EndingRules.aWhy(told, cfg, OBEY_DAYS), "obeying", "told at day 22");
		helper.assertTrue(EndingRules.aWhy(told.at(25 * DAY), cfg, OBEY_DAYS).isEmpty(), "three days after the last telling did not commit A");

		// Reading a fragment or going near his traces in the last aQuietDays holds A back.
		EndingFacts read = facts(Stage.TELLING, true, 30 * DAY, 20 * DAY, 19 * DAY, 19 * DAY, 29 * DAY, -1, NEVER, 4, 0, 0, 0, 0, 0, false, 0, 0, 0);
		expectWhy(helper, EndingRules.aWhy(read, cfg, OBEY_DAYS), "no reading", "read a fragment yesterday");
		helper.assertTrue(EndingRules.aWhy(read.at(32 * DAY), cfg, OBEY_DAYS).isEmpty(), "three days after the read did not commit A");
		EndingFacts traces = facts(Stage.TELLING, true, 30 * DAY, 20 * DAY, 19 * DAY, 19 * DAY, NEVER, 29, NEVER, 4, 0, 0, 0, 0, 0, false, 0, 0, 0);
		expectWhy(helper, EndingRules.aWhy(traces, cfg, OBEY_DAYS), "traces", "went near a tunnel yesterday");
		helper.succeed();
	}

	@GameTest
	public void endingBCommitsWhenTheyKeepTelling(GameTestHelper helper) {
		EndingConfig cfg = new EndingConfig();
		EndingFacts calm = facts(Stage.TELLING, true, 30 * DAY, 20 * DAY, 25 * DAY, 25 * DAY, NEVER, -1, NEVER, 5, 2, 40, 0, 0, 0, false, 0, 0, 0);
		helper.assertTrue(EndingRules.bReason(calm, cfg).isEmpty(), "B committed on two tellings after Stop.");
		EndingFacts after = facts(Stage.TELLING, true, 30 * DAY, 20 * DAY, 25 * DAY, 25 * DAY, NEVER, -1, NEVER, 6, cfg.bTellingsAfterStop, 40, 0, 0, 0,
				false, 0, 0, 0);
		helper.assertTrue(EndingRules.bReason(after, cfg).map(r -> r.contains("after")).orElse(false), "kept telling after Stop. did not commit B");
		EndingFacts many = facts(Stage.TELLING, false, 30 * DAY, NEVER, 29 * DAY, 29 * DAY, NEVER, -1, NEVER, cfg.bTellingCount, 0, 40, 0, 0, 0, false, 0,
				0, 0);
		helper.assertTrue(EndingRules.bReason(many, cfg).map(r -> r.contains("told")).orElse(false), "the telling count did not commit B");
		EndingFacts loud = facts(Stage.TELLING, false, 30 * DAY, NEVER, 29 * DAY, 29 * DAY, NEVER, -1, NEVER, 1, 0, cfg.bAttention, 0, 0, 0, false, 0, 0,
				0);
		helper.assertTrue(EndingRules.bReason(loud, cfg).map(r -> r.contains("attention")).orElse(false), "top attention did not commit B");
		// Proximity and never told: top attention alone is not telling.
		EndingFacts untold = new EndingFacts(Stage.PROXIMITY, false, false, 30 * DAY, NEVER, NEVER, NEVER, NEVER, -1, NEVER, 0, 0, 100, 0, 0, 0, false,
				0, 0, 0);
		helper.assertTrue(EndingRules.bReason(untold, cfg).isEmpty(), "B committed before any telling, in Proximity");
		helper.assertTrue(EndingRules.decide(after, cfg, OBEY_DAYS).map(d -> d.path() == EndingPath.B).orElse(false), "decide did not pick B");
		helper.succeed();
	}

	/** Everything Ending C needs, on day 14: two fragments burned, none held, 32 of 40 own blocks broken, 8 left. */
	static EndingFacts recordKept(long now) {
		return facts(Stage.TELLING, true, now, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 10 * DAY, 6, 0, 20, 0, 2, 0, false, 40, 8, 32);
	}

	@GameTest
	public void endingCCommitsOnlyWhenEverythingIsDone(GameTestHelper helper) {
		EndingConfig cfg = new EndingConfig();
		helper.assertTrue(EndingRules.cWhy(recordKept(14 * DAY), cfg).isEmpty(), "C did not commit: " + EndingRules.cWhy(recordKept(14 * DAY), cfg));
		expectWhy(helper, EndingRules.cWhy(recordKept(12 * DAY), cfg), "not naming him", "two days after naming him");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.TELLING, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 10 * DAY, 6, 0, 20, 0, 2, 1,
				false, 40, 8, 32), cfg), "never burned", "one fragment they held never went into the fire");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.TELLING, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 10 * DAY, 6, 0, 20, 0, 0, 0,
				false, 40, 8, 32), cfg), "burned", "no fragment burned");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.TELLING, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 10 * DAY, 6, 0, 20, 0, 2, 0,
				true, 40, 8, 32), cfg), "holds", "still holding one");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.TELLING, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 10 * DAY, 6, 0, 20, 0, 2, 0,
				false, 40, 20, 20), cfg), "house", "half the house still standing");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.TELLING, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 10 * DAY, 6, 0, 20, 0, 2, 0,
				false, 40, 8, 10), cfg), "house", "most of it gone, but not by their hand");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.TELLING, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 10 * DAY, 6, 0, 20, 0, 2, 0,
				false, 8, 0, 8), cfg), "house", "a house too small to count");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.TELLING, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 13, 10 * DAY, 6, 0, 20, 0, 2, 0,
				false, 40, 8, 32), cfg), "scars", "went back to a scar yesterday");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.TELLING, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 13 * DAY, 6, 0, 20, 0, 2, 0,
				false, 40, 8, 32), cfg), "fog", "stared into the fog yesterday");
		expectWhy(helper, EndingRules.cWhy(facts(Stage.PROXIMITY, true, 14 * DAY, 5 * DAY, 10 * DAY, 10 * DAY, NEVER, 10, 10 * DAY, 6, 0, 20, 0, 2, 0,
				false, 40, 8, 32), cfg), "stage", "in Proximity");
		helper.succeed();
	}

	@GameTest
	public void pathsAreDecidedLoudestFirst(GameTestHelper helper) {
		EndingConfig cfg = new EndingConfig();
		// C and A both hold (they obeyed, and did what the team did): C, the deliberate one.
		EndingFacts both = facts(Stage.TELLING, true, 30 * DAY, 20 * DAY, 19 * DAY, 19 * DAY, NEVER, 10, NEVER, 4, 0, 10, 0, 1, 0, false, 40, 0, 40);
		helper.assertTrue(EndingRules.aWhy(both, cfg, OBEY_DAYS).isEmpty() && EndingRules.cWhy(both, cfg).isEmpty(), "fixture: A and C should both hold");
		helper.assertTrue(EndingRules.decide(both, cfg, OBEY_DAYS).map(d -> d.path() == EndingPath.C).orElse(false), "C did not win over A");
		// Telling holds too: B.
		EndingFacts loud = facts(Stage.TELLING, true, 30 * DAY, 20 * DAY, 19 * DAY, 19 * DAY, NEVER, 10, NEVER, cfg.bTellingCount, 0, 10, 0, 1, 0, false,
				40, 0, 40);
		helper.assertTrue(EndingRules.decide(loud, cfg, OBEY_DAYS).map(d -> d.path() == EndingPath.B).orElse(false), "B did not win");
		// Nothing holds: no path.
		helper.assertTrue(EndingRules.decide(obeying(21 * DAY), cfg, OBEY_DAYS).isEmpty(), "a path committed too early");
		helper.succeed();
	}

	@GameTest
	public void directorFlagsAreSingleAndParseable(GameTestHelper helper) {
		HerobrineState state = new HerobrineState();
		DirectorHooks.silenceUntil(state, 12);
		DirectorHooks.silenceUntil(state, 15);
		helper.assertTrue(state.hasFlag("director:silence_until_day=15") && !state.hasFlag("director:silence_until_day=12"),
				"silence flags: " + state.flags());
		helper.assertTrue(DirectorHooks.silence(state).orElse(0L) == 15, "silence did not read back");
		DirectorHooks.silenceForever(state);
		helper.assertTrue(state.hasFlag("director:silence_forever") && state.flags().stream().noneMatch(f -> f.startsWith("director:silence_until")),
				"forever is not director:silence_forever alone: " + state.flags());
		helper.assertTrue(com.forzacode.a1016_02.director.DirectorFlags.parse(state.flags()).silenced(Long.MAX_VALUE - 1),
				"the director does not read it as for good");
		DirectorHooks.silenceUntil(state, 20);
		helper.assertFalse(state.hasFlag("director:silence_forever"), "a silence until a day left forever behind");
		helper.assertTrue(com.forzacode.a1016_02.director.DirectorFlags.parse(state.flags()).silenced(19)
				&& !com.forzacode.a1016_02.director.DirectorFlags.parse(state.flags()).silenced(20), "the director reads the day wrong");
		DirectorHooks.pace(state, 1.6);
		helper.assertTrue(state.hasFlag("director:pace_multiplier=1.60"), "pace flag not in Locale.ROOT form: " + state.flags());
		helper.assertTrue(Math.abs(DirectorHooks.pace(state).orElse(0.0) - 1.6) < 1.0E-9, "pace did not read back");
		DirectorHooks.clearSilence(state);
		DirectorHooks.clearPace(state);
		helper.assertTrue(state.flags().stream().noneMatch(f -> f.startsWith("director:")), "flags left behind: " + state.flags());
		helper.succeed();
	}
}
