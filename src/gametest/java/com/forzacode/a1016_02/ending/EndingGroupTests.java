package com.forzacode.a1016_02.ending;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.ending.EndingBeats.B;
import com.forzacode.a1016_02.ending.EndingTestSupport.Run;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * Endings A, B and C with friends (D-058, D-063, D-064), two mock players with the run's own online list and
 * {@code PlayerWatch} (mock players are never in the server's list): each player's own third marked death, hardcore
 * lines finished one at a time, two deaths in one tick, C with a friend away, the house an owner's, and nobody kept
 * across ticks.
 */
public class EndingGroupTests extends EndingWorldTests {
	/** D-063: the third-death rule counts each player's own marked deaths, never the world's together. */
	@GameTest
	public void aPlayersOwnThirdMarkedDeathCommitsB(GameTestHelper helper) {
		Run r = new Run(helper);
		ServerPlayer a = r.player;
		ServerPlayer b = r.friend();
		r.die(a, r.at(1, 1, 1));
		r.die(b, r.at(2, 1, 2));
		r.die(a, r.at(1, 1, 1));
		r.die(b, r.at(2, 1, 2));
		helper.assertTrue(r.state.markedDeaths().size() == 4 && r.data.path() == EndingPath.NONE, "four deaths between two players committed B: "
				+ r.data.path());
		r.die(a, r.at(1, 1, 1));
		helper.assertTrue(r.data.path() == EndingPath.B && r.data.reason().contains("third"), "A's own third marked death did not commit B");
		helper.assertFalse(r.data.ended(), "the third-death rule ended the story instead of committing B");
		helper.assertTrue(r.state.stage() == Stage.REMOVAL, "B did not begin Stage 4");

		// The tick's catch-up counts the same way: two of each, nothing; the third of one player's own, B.
		Run t = new Run(helper);
		ServerPlayer c = t.friend();
		for (ServerPlayer who : List.of(t.player, c, t.player, c)) {
			t.state.addMarkedDeath(who.getUUID(), new MarkedDeath("fell", t.at(0, 1, 0), t.today()));
		}
		t.tick();
		helper.assertTrue(t.data.path() == EndingPath.NONE && t.data.deathsSeen() == 4, "the tick counted the world's deaths together");
		t.state.addMarkedDeath(c.getUUID(), new MarkedDeath("fell", t.at(0, 1, 0), t.today()));
		t.tick();
		helper.assertTrue(t.data.path() == EndingPath.B, "the tick missed a player's own third marked death");
		helper.succeed();
	}

	/**
	 * D-063 in hardcore: A's marked death finishes A's line only, and B plays on with the story going; B's marked death
	 * leaves nobody in it, and only then does the story end, with Ending B's record.
	 */
	@GameTest
	public void hardcoreAFriendsDeathFinishesOnlyTheirLine(GameTestHelper helper) {
		Run r = new Run(helper);
		r.hardcore = true;
		ServerPlayer a = r.player;
		ServerPlayer b = r.friend();
		r.state.setStopFired(true);
		r.commit(EndingPath.A);
		r.die(a, r.at(1, 1, 1));
		helper.assertFalse(r.data.ended() || r.state.hasFlag(EndingEngine.ENDED_FLAG), "one friend's hardcore death ended the story while another plays");
		helper.assertTrue(r.data.lineFinished(a.getUUID()) && r.state.hasFlag(EndingApi.LINE_FINISHED_FLAG + a.getUUID()), "A's line is not finished");
		helper.assertFalse(r.data.lineFinished(b.getUUID()), "B's line was finished by A's death");
		helper.assertTrue(r.inIt().equals(List.of(b)), "A is still in it after their line was finished: " + r.inIt().size());
		helper.assertTrue(r.data.path() == EndingPath.A && r.beat(EndingBeats.A.class, EndingPath.A) == EndingBeats.A.SIGHTING,
				"A's false peace did not go on for B");
		helper.assertTrue(r.ports.f10Finished == 0, "F10 was finished for the world by one friend's death");
		// The beats go on for B only: the last sighting fires for B, never for A.
		r.tick();
		helper.assertTrue(r.ports.sightings == 1, "the sighting did not go on for the one still in it");

		r.die(b, r.at(2, 1, 2));
		helper.assertTrue(r.data.ended() && r.state.hasFlag(EndingEngine.ENDED_FLAG), "the last one's hardcore death did not end the story");
		helper.assertTrue(r.data.lineFinished(b.getUUID()) && r.data.finishedLines().size() == 2, "B's line is not finished");
		helper.assertTrue(r.beat(EndingBeats.A.class, EndingPath.A) == EndingBeats.A.SIGN, "A's sign does not follow the last death");

		// Nobody left on B's path: the story ends with B's record.
		Run onB = new Run(helper);
		onB.hardcore = true;
		ServerPlayer friend = onB.friend();
		onB.commit(EndingPath.B);
		onB.data.setHouse(onB.at(0, 150, 0));
		onB.die(onB.player, onB.at(1, 1, 1));
		helper.assertTrue(!onB.data.ended() && onB.beat(B.class, EndingPath.B) == B.ESCALATE && onB.ports.f10Finished == 0,
				"one friend's death on B's path ended B");
		onB.die(friend, onB.at(1, 1, 1));
		// B's record names every finished line, once each, in the order they were finished.
		helper.assertTrue(onB.data.ended() && onB.ports.f10Named.equals(List.of(onB.player.getUUID(), friend.getUUID()))
				&& onB.beat(B.class, EndingPath.B) == B.RECORD, "the last one's death did not end B with its record: " + onB.ports.f10Named);
		helper.succeed();
	}

	/**
	 * Who is "left in it" (D-063): the online participants, alive, whose line is open. A friend who is offline does not
	 * keep the story alive, and is not killed off by it: their line stays open. An ordinary death in hardcore (a
	 * spectator now) is simply out.
	 */
	@GameTest
	public void anOfflineFriendNeitherKeepsTheStoryNorIsFinished(GameTestHelper helper) {
		Run r = new Run(helper);
		r.hardcore = true;
		ServerPlayer away = r.friend();
		r.online.remove(away);
		r.die(r.player, r.at(1, 1, 1));
		helper.assertTrue(r.data.ended(), "an offline friend kept the story alive");
		helper.assertFalse(r.data.lineFinished(away.getUUID()), "the offline friend's line was finished by someone else's death");
		helper.assertTrue(r.data.path() == EndingPath.B, "the story did not end with Ending B: " + r.data.path());

		// Normal mode: a friend's death never finishes anyone's line.
		Run normal = new Run(helper);
		ServerPlayer b = normal.friend();
		normal.die(b, normal.at(1, 1, 1));
		helper.assertTrue(normal.data.finishedLines().isEmpty() && !normal.data.ended(), "a normal-mode death finished a line");
		helper.succeed();
	}

	/** D-062.3: two marked deaths in one tick (both caught by one call, then the second event): the story ends once. */
	@GameTest
	public void twoDeathsInOneTickEndTheStoryOnce(GameTestHelper helper) {
		Run r = new Run(helper);
		r.hardcore = true;
		ServerPlayer a = r.player;
		ServerPlayer b = r.friend();
		r.state.addMarkedDeath(a.getUUID(), new MarkedDeath("lava", r.at(1, 1, 1), r.today()));
		r.state.addMarkedDeath(b.getUUID(), new MarkedDeath("lava", r.at(1, 1, 2), r.today()));
		r.engine.onMarkedDeath(r.ctx());
		r.engine.onMarkedDeath(r.ctx());
		r.tick();
		long ends = r.data.log().stream().filter(line -> line.startsWith("the story ends")).count();
		helper.assertTrue(r.data.ended() && ends == 1, "the story did not end exactly once: " + ends);
		helper.assertTrue(r.ports.f10Named.equals(List.of(a.getUUID(), b.getUUID())), "F10 did not name each finished line once, in order: "
				+ r.ports.f10Named);
		helper.assertTrue(r.data.finishedLines().size() == 2 && r.data.deathsSeen() == 2, "the two deaths were not each handled once");
		helper.succeed();
	}

	/**
	 * D-064 and Ending C: one rule set for the group. Fragments the group holds must burn, but one a friend took along
	 * while they are away never blocks C: it counts again once they play and carry it. The house is the group's, taken
	 * apart by anyone's hand.
	 */
	@GameTest
	public void endingCWithAFriendOffline(GameTestHelper helper) {
		Run r = new Run(helper);
		ServerPlayer a = r.player;
		ServerPlayer b = r.friend();
		b.getInventory().setItem(3, EndingWorldTests.fragment("F09"));
		EndingWatch.sampleHeld(a, r.data);
		EndingWatch.sampleHeld(b, r.data);
		r.data.addHeld("F02", a.getUUID());
		r.data.addBurned("F02");
		helper.assertTrue(r.data.keeper("F09").equals(Optional.of(b.getUUID())) && r.data.keeper("F02").isEmpty(), "the keepers are wrong");

		EndingFacts both = EndingWatch.facts(r.server, r.inIt(), r.state, r.data, r.cfg, r.ports, r.now);
		helper.assertTrue(both.fragmentsUnburned() == 1 && both.holdsFragment(), "the fragment B carries does not count while B plays");

		// B leaves, taking F09 along: it is no longer the playing group's to burn.
		r.online.remove(b);
		EndingFacts away = EndingWatch.facts(r.server, r.inIt(), r.state, r.data, r.cfg, r.ports, r.now);
		helper.assertTrue(away.fragmentsUnburned() == 0 && !away.holdsFragment(), "an offline friend's fragment still blocks C: " + away.fragmentsUnburned());

		// The rest of C, done by A for the group: the house taken apart, three quiet days.
		long quietSince = r.now - 4 * GameClock.TICKS_PER_DAY;
		EndingFacts done = EndingTestSupport.facts(Stage.TELLING, false, r.now, EndingState.NEVER, quietSince, quietSince, EndingState.NEVER, -1,
				EndingState.NEVER, 6, 0, 20, 0, r.data.fragmentsBurned(), away.fragmentsUnburned(), away.holdsFragment(), 40, 4, 34);
		helper.assertTrue(EndingRules.cWhy(done, r.cfg).isEmpty(), "C is blocked with a friend away: " + EndingRules.cWhy(done, r.cfg));
		r.engine.tick(r.ctx(), done);
		helper.assertTrue(r.data.path() == EndingPath.C, "C did not commit for the group: " + r.data.path());

		// Had B come back first carrying it, C would have waited for F09 to burn.
		r.online.add(b);
		EndingFacts back = EndingWatch.facts(r.server, r.inIt(), r.state, r.data, r.cfg, r.ports, r.now);
		helper.assertTrue(back.fragmentsUnburned() == 1, "the friend's fragment did not count again once they played");

		// An old save's fragment (no keeper) is the old subject's: it counts while nobody knows better.
		EndingState old = new EndingState();
		old.addHeld("F05");
		helper.assertTrue(old.unburned(uuid -> false, null).equals(Set.of("F05")) && old.unburned(uuid -> false, a.getUUID()).isEmpty()
				&& old.unburned(uuid -> true, a.getUUID()).equals(Set.of("F05")), "an old save's held fragment is not the old subject's");
		helper.succeed();
	}

	/**
	 * D-061: B's "your house" is the house copy owner's (world's choice), never whoever happens to be first online, and
	 * the group's home is the stored home owner's.
	 */
	@GameTest
	public void theHouseFollowsTheStoredOwners(GameTestHelper helper) {
		Run r = new Run(helper);
		ServerPlayer a = r.player;
		ServerPlayer b = r.friend();
		BlockPos bBed = r.at(5, 1, 40).pos();
		b.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(helper.getLevel().dimension(), bBed, 0.0F, 0.0F), true), false);
		// Only B online when world chose: B's house is copied.
		r.online.remove(a);
		helper.assertTrue(r.watch.ownerFor(r.server, EndingApi.HOUSE_COPY_OWNER).equals(Optional.of(b.getUUID())), "the owner was not stored");
		r.online.addFirst(a);
		r.data.setHome(r.at(0, 1, 0));
		r.commit(EndingPath.B);
		helper.assertTrue(r.data.house().equals(Optional.of(GlobalPos.of(helper.getLevel().dimension(), bBed))),
				"B empties " + r.data.house() + ", not the house copy owner's");

		// No copy owner: the group's home.
		Run home = new Run(helper);
		home.friend();
		home.data.setHome(home.at(3, 1, 3));
		home.commit(EndingPath.B);
		helper.assertTrue(home.data.house().equals(Optional.of(home.at(3, 1, 3))), "without a copy owner B did not take the group's home");
		helper.succeed();
	}

	/** The beats act on whoever is in it at that moment, and on nobody who left (D-062.1, D-062.5). */
	@GameTest
	public void theBeatsActOnWhoeverIsThereNow(GameTestHelper helper) {
		Run r = new Run(helper);
		ServerPlayer a = r.player;
		ServerPlayer b = r.friend();
		a.snapTo(Vec3.atBottomCenterOf(r.at(1, 1, 1).pos()), 0, 0);
		b.snapTo(Vec3.atBottomCenterOf(r.at(7, 1, 7).pos()), 0, 0);
		r.state.setStopFired(true);
		r.commit(EndingPath.A);
		r.data.setProgress(EndingPath.A, EndingBeats.A.ACCIDENT.ordinal(), r.now);
		r.ports.armable.add("lava_floor");
		// A leaves: the one accident is armed around B, the one still there.
		r.online.remove(a);
		r.tick();
		helper.assertTrue(r.ports.armed.equals(List.of("lava_floor")) && r.ports.traps.get(b.getUUID()) != null
				&& r.ports.traps.get(b.getUUID()).pos().pos().equals(b.blockPosition()), "the accident was not armed around the one still in it");
		// Nobody in it: nothing is armed, nothing breaks.
		r.ports.disarmTraps();
		r.online.clear();
		r.data.setProgress(EndingPath.A, EndingBeats.A.ACCIDENT.ordinal(), r.now);
		r.minutes(120);
		r.tick();
		helper.assertTrue(r.ports.armed.size() == 1, "something was armed with nobody in it");
		helper.succeed();
	}
}
