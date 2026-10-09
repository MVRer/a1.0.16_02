package com.forzacode.a1016_02.ending;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.ending.EndingBeats.A;
import com.forzacode.a1016_02.ending.EndingBeats.B;
import com.forzacode.a1016_02.ending.EndingTestSupport.Run;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Each path's beats with a forced clock and private state: A's sighting, quiet, accident and sign; B's escalation,
 * copy, doorway, final death and record; C's silence and its reversal on naming; the third-death rule; hardcore's one
 * death; the debug step. The recording ports stand in for the other workstreams.
 */
public class EndingBeatTests extends EndingRuleTests {
	/** What the player looked like before: the ending never damages, moves or touches them. */
	record Untouched(float health, Vec3 pos, Vec3 motion, int fire) {
		static Untouched of(Run r) {
			return new Untouched(r.player.getHealth(), r.player.position(), r.player.getDeltaMovement(), r.player.getRemainingFireTicks());
		}

		void check(GameTestHelper helper, Run r) {
			helper.assertTrue(r.player.getHealth() == health && r.player.position().equals(pos) && r.player.getDeltaMovement().equals(motion)
					&& r.player.getRemainingFireTicks() == fire, "the ending touched the player");
		}
	}

	@GameTest
	public void endingAGoesQuietThenOneAccidentThenTheSign(GameTestHelper helper) {
		Run r = new Run(helper);
		Untouched before = Untouched.of(r);
		r.state.setStopFired(true);
		r.commit(EndingPath.A);
		helper.assertTrue(r.state.stage() == Stage.REMOVAL, "A did not enter Stage 4");
		helper.assertTrue(r.state.hasFlag(EndingEngine.LAST_SIGHTING_FLAG), "ending:last_sighting not set");
		helper.assertTrue(r.state.hasFlag("ending:path=A"), "the path is not mirrored");
		helper.assertTrue(DirectorHooks.silence(r.state).orElse(0L) == DirectorHooks.FOREVER, "the world did not go quiet");
		helper.assertTrue(r.ports.disarms == 1, "an armed trap was left for the false peace");

		r.tick();
		helper.assertTrue(r.ports.sightings == 1 && r.data.sightingTries() == 1, "sighting_last_one was not fired");
		helper.assertTrue(r.beat(A.class, EndingPath.A) == A.SIGHTING, "moved on before he was seen");
		r.tick();
		helper.assertTrue(r.ports.sightings == 1, "fired twice inside the retry gap");

		r.state.setFlag(EndingEngine.LAST_SIGHTING_SEEN_FLAG, true);
		r.tick();
		helper.assertTrue(r.beat(A.class, EndingPath.A) == A.QUIET, "no quiet after he was seen");
		long until = r.data.silenceUntilDay();
		helper.assertTrue(until >= r.today() + 5 && until <= r.today() + 7, "the quiet is not 5 to 7 days: until " + until + " from " + r.today());
		helper.assertTrue(DirectorHooks.silence(r.state).orElse(0L) == until, "director:silence_until_day is not the quiet's end");

		r.ports.armable.add("lava_floor");
		r.days(4);
		r.tick();
		helper.assertTrue(r.beat(A.class, EndingPath.A) == A.QUIET && r.ports.armed.isEmpty(), "the accident came during the quiet");
		r.now = until * GameClock.TICKS_PER_DAY + 100;
		r.tick();
		helper.assertTrue(r.beat(A.class, EndingPath.A) == A.ACCIDENT, "the quiet did not end");
		helper.assertTrue(DirectorHooks.silence(r.state).orElse(0L) == DirectorHooks.FOREVER, "other events came back for the accident");
		r.tick();
		helper.assertTrue(r.ports.armed.equals(List.of("lava_floor")), "the lava floor was not armed: " + r.ports.armed);

		GlobalPos death = r.at(3, 1, 3);
		r.state.addMarkedDeath(new com.forzacode.a1016_02.core.MarkedDeath("lava", death, r.today()));
		r.engine.onMarkedDeath(r.ctx(), death, 1, false);
		helper.assertTrue(r.beat(A.class, EndingPath.A) == A.SIGN && r.data.deathPos().equals(Optional.of(death)), "the death did not lead to the sign");
		r.tick();
		helper.assertTrue(r.ports.signMovedTo.isEmpty() && r.beat(A.class, EndingPath.A) == A.SIGN, "the sign moved with no cross standing");
		r.ports.cross = r.at(4, 1, 3);
		r.tick();
		helper.assertTrue(r.ports.signMovedTo.equals(List.of(r.ports.cross)), "the Stop. sign was not moved to the cross");
		helper.assertTrue(r.beat(A.class, EndingPath.A) == A.DONE && r.data.ended() && r.state.hasFlag(EndingEngine.ENDED_FLAG), "A did not end");
		helper.assertTrue(r.data.path() == EndingPath.A && r.state.stage() == Stage.REMOVAL, "the final state is not A in Stage 4");
		before.check(helper, r);
		helper.succeed();
	}

	@GameTest
	public void endingASightingIsTriedThenGivenUp(GameTestHelper helper) {
		Run r = new Run(helper);
		r.state.setStopFired(true);
		r.commit(EndingPath.A);
		r.ports.sightingResult = FireResult.FIRED;
		for (int i = 0; i < 3; i++) {
			r.tick();
			r.now += 2400;
		}
		helper.assertTrue(r.data.sightingTries() == 3, "not tried three times: " + r.data.sightingTries());
		r.tick();
		helper.assertTrue(r.beat(A.class, EndingPath.A) == A.QUIET, "never seen after three tries, still waiting");

		Run late = new Run(helper);
		late.state.setStopFired(true);
		late.commit(EndingPath.A);
		late.ports.sightingResult = FireResult.NO_SPOT;
		late.tick();
		late.days(late.cfg.aSightingMaxDays + 0.1);
		late.tick();
		helper.assertTrue(late.beat(A.class, EndingPath.A) == A.QUIET, "no spot for days, still waiting for the sighting");
		helper.succeed();
	}

	@GameTest
	public void tellingDuringTheFalsePeaceTurnsItIntoB(GameTestHelper helper) {
		Run r = new Run(helper);
		r.state.setStopFired(true);
		r.data.setStopSeenAt(r.now - 5 * GameClock.TICKS_PER_DAY);
		r.commit(EndingPath.A);
		r.data.recordTelling(r.now, false);
		r.data.recordTelling(r.now, false);
		r.data.recordTelling(r.now, false);
		r.tick();
		helper.assertTrue(r.data.path() == EndingPath.B, "kept telling during A, still A");
		helper.assertFalse(r.state.hasFlag(EndingEngine.LAST_SIGHTING_FLAG), "A's last sighting is still allowed on B");
		helper.assertTrue(DirectorHooks.pace(r.state).isPresent() && DirectorHooks.silence(r.state).isEmpty(), "B's flags are wrong: " + r.state.flags());
		helper.succeed();
	}

	@GameTest
	public void endingBEscalatesEmptiesFinishesAndEndsInTheCopy(GameTestHelper helper) {
		Run r = new Run(helper);
		Untouched before = Untouched.of(r);
		r.ports.copyExists = true;
		r.ports.copySite = r.at(0, 1, 40);
		r.commit(EndingPath.B);
		// High in the air: no furnishings, no doorway and no other test's blocks in reach.
		r.data.setHouse(r.at(0, 150, 0));
		helper.assertTrue(r.state.stage() == Stage.REMOVAL, "B did not enter Stage 4");
		helper.assertTrue(r.state.hasFlag("director:pace_multiplier=1.60"), "no pace multiplier above 1: " + r.state.flags());
		r.tick();
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.ESCALATE && r.ports.f20Calls == 1, "F20 is not tried during B");
		r.days(r.cfg.bEscalateDays);
		r.tick();
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.EMPTY_HOUSE, "the escalation did not end");
		r.tick();
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.FINISH_COPY, "an empty house was not passed over");
		r.tick();
		helper.assertTrue(r.ports.copyFinishes == 1 && r.beat(B.class, EndingPath.B) == B.FINISH_COPY, "the copy was not asked to finish");
		r.tick();
		helper.assertTrue(r.ports.copyFinishes == 1, "the copy was asked twice");
		r.ports.copyFinished = true;
		r.tick();
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.DOORWAY, "the finished copy did not lead on");
		r.tick();
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.DOORWAY, "no mob waited and it moved on at once");
		r.days(r.cfg.bDoorwayMaxDays);
		r.tick();
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.FINAL, "the doorway never gave up");

		r.ports.armable.add("dark_corner");
		r.player.snapTo(Vec3.atBottomCenterOf(r.ports.copySite.pos().offset(0, 0, 60)), 0, 0);
		r.tick();
		helper.assertTrue(r.ports.armed.isEmpty(), "the final trap was armed away from the copy");
		r.player.snapTo(Vec3.atBottomCenterOf(r.ports.copySite.pos().offset(2, 0, 1)), 0, 0);
		before = Untouched.of(r);
		r.now += 2400;
		r.tick();
		helper.assertTrue(r.ports.armed.equals(List.of("dark_corner")) && r.data.finalArmed(), "the final trap was not armed in the copy");

		r.engine.onMarkedDeath(r.ctx(), r.ports.copySite, 1, false);
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.RECORD && r.ports.f10Finished == 1, "the final death did not finish F10");
		r.tick();
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.RECORD, "B ended before F20 was placed");
		r.ports.f20Ready = true;
		r.tick();
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.DONE && r.data.ended() && r.data.f20Placed(), "B did not end with F20 placed");
		helper.assertTrue(DirectorHooks.pace(r.state).isEmpty() && DirectorHooks.silence(r.state).orElse(0L) == DirectorHooks.FOREVER,
				"the end left the pace on or the director awake: " + r.state.flags());
		before.check(helper, r);
		helper.succeed();
	}

	@GameTest
	public void onlyADeathInsideTheCopyIsBsFinalOne(GameTestHelper helper) {
		Run r = new Run(helper);
		r.ports.copyExists = true;
		r.ports.copySite = r.at(0, 150, 40);
		r.commit(EndingPath.B);
		r.data.setHouse(r.at(0, 150, 0));
		r.data.setProgress(EndingPath.B, B.FINAL.ordinal(), r.now);
		r.engine.onMarkedDeath(r.ctx(), r.at(0, 150, 100), 1, false);
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.FINAL && r.ports.f10Finished == 0, "a death far from the copy was the final one");
		r.engine.onMarkedDeath(r.ctx(), r.at(3, 151, 42), 2, false);
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.RECORD && r.ports.f10Finished == 1, "a death inside the copy was not the final one");
		helper.succeed();
	}

	@GameTest
	public void endingBArmsAccidentsCloseToHome(GameTestHelper helper) {
		Run r = new Run(helper);
		r.commit(EndingPath.B);
		r.data.setHouse(r.at(0, 1, 0));
		r.ports.armable.add("house_fire");
		r.player.snapTo(Vec3.atBottomCenterOf(r.at(0, 1, 200).pos()), 0, 0);
		r.tick();
		helper.assertTrue(r.ports.armed.isEmpty(), "armed far from home");
		r.player.snapTo(Vec3.atBottomCenterOf(r.at(10, 1, 10).pos()), 0, 0);
		r.now += 2400;
		r.tick();
		helper.assertTrue(r.ports.armed.equals(List.of("house_fire")), "nothing armed at home: " + r.ports.armed);
		r.ports.trapArmed = false;
		r.now += 2400;
		r.tick();
		helper.assertTrue(r.ports.armed.size() == 1, "a second home accident inside a day");
		r.days(r.cfg.bHomeArmDays);
		r.tick();
		helper.assertTrue(r.ports.armed.size() == 2, "no home accident the next day");
		helper.succeed();
	}

	@GameTest
	public void endingCGoesSilentAndHisNameUndoesIt(GameTestHelper helper) {
		Run r = new Run(helper);
		r.ports.trapArmed = true;
		r.data.addFragmentBurned();
		r.commit(EndingPath.C);
		helper.assertTrue(r.state.stage() == Stage.REMOVAL && r.state.hasFlag("ending:path=C"), "C did not enter Stage 4");
		helper.assertTrue(DirectorHooks.silence(r.state).orElse(0L) == DirectorHooks.FOREVER, "director:silence_until_day=-1 not set");
		helper.assertTrue(r.ports.duskFog.equals(List.of(0.0F)), "the dusk fog did not go to 0: " + r.ports.duskFog);
		helper.assertFalse(r.ports.trapArmed, "a trap stayed armed in the silence");
		r.days(30);
		r.tick();
		helper.assertTrue(r.data.path() == EndingPath.C && !r.data.ended(), "C did not hold");

		r.data.recordTelling(r.now, false);
		r.engine.onTelling(r.ctx(), false);
		helper.assertTrue(r.data.path() == EndingPath.C, "a telling without his name undid C");
		r.data.recordTelling(r.now, true);
		r.engine.onTelling(r.ctx(), true);
		helper.assertTrue(r.data.path() == EndingPath.NONE, "naming him did not undo C");
		helper.assertTrue(DirectorHooks.silence(r.state).isEmpty(), "the silence stayed after naming him");
		helper.assertTrue(r.state.stage() == Stage.TELLING, "the stage did not go back to Telling");
		helper.assertTrue(r.data.fragmentsBurned() == 0 && !r.state.hasFlag("ending:path=C"), "C's work was kept");
		helper.succeed();
	}

	@GameTest
	public void theThirdMarkedDeathIsEndingBWhateverThePath(GameTestHelper helper) {
		for (EndingPath path : new EndingPath[] {EndingPath.NONE, EndingPath.A, EndingPath.C, EndingPath.D}) {
			Run r = new Run(helper);
			if (path != EndingPath.NONE) {
				r.commit(path);
			}
			r.engine.onMarkedDeath(r.ctx(), r.at(1, 1, 1), 1, false);
			r.engine.onMarkedDeath(r.ctx(), r.at(1, 1, 1), 2, false);
			helper.assertTrue(r.data.path() == path, path + ": changed before the third death");
			r.engine.onMarkedDeath(r.ctx(), r.at(1, 1, 1), 3, false);
			helper.assertTrue(r.data.path() == EndingPath.B && r.data.reason().contains("third"), path + ": the third death did not commit B");
			helper.assertTrue(r.state.stage() == Stage.REMOVAL && DirectorHooks.pace(r.state).isPresent(), path + ": B did not start");
			if (path == EndingPath.C) {
				helper.assertTrue(r.ports.duskFog.getLast() == 0.7F, "C's clear dusk stayed on B");
				helper.assertTrue(DirectorHooks.silence(r.state).isEmpty(), "C's silence stayed on B");
			}
		}

		// A's ordinary accident is the third death: A's sign still goes to the cross while B runs.
		Run r = new Run(helper);
		r.commit(EndingPath.A);
		r.data.setProgress(EndingPath.A, A.ACCIDENT.ordinal(), r.now);
		r.data.setDeathsSeen(2);
		r.engine.onMarkedDeath(r.ctx(), r.at(2, 1, 2), 3, false);
		helper.assertTrue(r.data.path() == EndingPath.B && r.beat(A.class, EndingPath.A) == A.SIGN, "A's last beat was lost to B");
		r.ports.cross = r.at(2, 1, 3);
		r.tick();
		helper.assertTrue(r.ports.signMovedTo.size() == 1 && r.beat(A.class, EndingPath.A) == A.DONE, "the sign did not move while B ran");
		helper.assertTrue(r.data.path() == EndingPath.B && !r.data.ended(), "A's sign ended B's story");

		// The tick catches deaths the event missed.
		Run missed = new Run(helper);
		for (int i = 0; i < 3; i++) {
			missed.state.addMarkedDeath(new com.forzacode.a1016_02.core.MarkedDeath("fell", missed.at(0, 1, 0), missed.today()));
		}
		missed.tick();
		helper.assertTrue(missed.data.path() == EndingPath.B, "three recorded deaths did not commit B on the tick");
		helper.succeed();
	}

	@GameTest
	public void hardcoreOneMarkedDeathEndsTheStory(GameTestHelper helper) {
		Run none = new Run(helper);
		none.engine.onMarkedDeath(none.ctx(), none.at(1, 1, 1), 1, true);
		helper.assertTrue(none.data.ended() && none.state.hasFlag(EndingEngine.ENDED_FLAG), "a hardcore death did not end the story");
		helper.assertTrue(DirectorHooks.silence(none.state).orElse(0L) == DirectorHooks.FOREVER, "the director kept going after the end");
		none.ports.armable.add("lava_floor");
		none.state.setStopFired(true);
		none.data.setStopSeenAt(none.now - 10 * GameClock.TICKS_PER_DAY);
		none.engine.tick(none.ctx(), obeying(none.now).at(none.now));
		helper.assertTrue(none.data.path() == EndingPath.NONE && none.ports.armed.isEmpty(), "something committed or armed after the end");

		Run a = new Run(helper);
		a.commit(EndingPath.A);
		a.engine.onMarkedDeath(a.ctx(), a.at(2, 1, 2), 1, true);
		helper.assertTrue(a.data.ended() && a.beat(A.class, EndingPath.A) == A.SIGN, "hardcore A did not go to the sign");
		a.ports.cross = a.at(2, 1, 3);
		a.tick();
		helper.assertTrue(a.beat(A.class, EndingPath.A) == A.DONE && a.ports.signMovedTo.size() == 1, "hardcore A's sign did not move");

		Run b = new Run(helper);
		b.commit(EndingPath.B);
		b.engine.onMarkedDeath(b.ctx(), b.at(2, 1, 2), 1, true);
		helper.assertTrue(b.data.ended() && b.ports.f10Finished == 1 && b.beat(B.class, EndingPath.B) == B.RECORD, "hardcore B did not finish F10");
		b.ports.f20Ready = true;
		b.tick();
		helper.assertTrue(b.beat(B.class, EndingPath.B) == B.DONE && b.data.f20Placed(), "hardcore B did not place F20");
		b.engine.onMarkedDeath(b.ctx(), b.at(2, 1, 2), 2, true);
		helper.assertTrue(b.data.path() == EndingPath.B && b.ports.f10Finished == 1, "a death after the end changed something");
		helper.assertTrue(DirectorHooks.pace(b.state).isEmpty() && b.state.stage() == Stage.REMOVAL, "the final state is not consistent");
		helper.succeed();
	}

	@GameTest
	public void debugStepWalksEachPathToItsEnd(GameTestHelper helper) {
		Run a = new Run(helper);
		a.commit(EndingPath.A);
		for (A expected : new A[] {A.QUIET, A.ACCIDENT, A.SIGN, A.DONE}) {
			a.engine.step(a.ctx());
			helper.assertTrue(a.beat(A.class, EndingPath.A) == expected, "A's step did not reach " + expected + ": " + a.beat(A.class, EndingPath.A));
		}
		helper.assertTrue(a.data.ended(), "A's steps did not end the story");

		Run b = new Run(helper);
		b.commit(EndingPath.B);
		b.data.setHouse(b.at(0, 150, 0));
		for (B expected : new B[] {B.EMPTY_HOUSE, B.FINISH_COPY, B.DOORWAY, B.FINAL, B.RECORD, B.DONE}) {
			b.engine.step(b.ctx());
			helper.assertTrue(b.beat(B.class, EndingPath.B) == expected, "B's step did not reach " + expected + ": " + b.beat(B.class, EndingPath.B));
		}
		helper.assertTrue(b.data.ended() && b.ports.f10Finished == 1, "B's steps did not end with F10");

		Run c = new Run(helper);
		c.commit(EndingPath.C);
		List<String> lines = c.engine.step(c.ctx());
		helper.assertTrue(c.data.path() == EndingPath.C && lines.getFirst().contains("silent"), "C's step: " + lines);
		helper.succeed();
	}

	@GameTest
	public void theTickCommitsFromNoneAndStopIsNoticed(GameTestHelper helper) {
		Run r = new Run(helper);
		r.state.setStopFired(true);
		EndingWatch.sample(null, r.state, r.data, r.cfg, r.ports.watch(), r.now);
		helper.assertTrue(r.data.stopSeenAt() == r.now, "Stop. was not noticed");
		long stop = r.now;
		r.days(1);
		r.engine.tick(r.ctx(), new EndingFacts(Stage.TELLING, true, true, r.now, stop, stop - 1000, stop - 1000, EndingState.NEVER, -1,
				EndingState.NEVER, 4, 0, 10, 0, 0, false, 0, 0, 0));
		helper.assertTrue(r.data.path() == EndingPath.NONE, "A committed one day after Stop.");
		r.days(2);
		r.engine.tick(r.ctx(), new EndingFacts(Stage.TELLING, true, true, r.now, stop, stop - 1000, stop - 1000, EndingState.NEVER, -1,
				EndingState.NEVER, 4, 0, 10, 0, 0, false, 0, 0, 0));
		helper.assertTrue(r.data.path() == EndingPath.A && r.state.stage() == Stage.REMOVAL, "the tick did not commit A: " + r.data.path());
		helper.succeed();
	}
}
