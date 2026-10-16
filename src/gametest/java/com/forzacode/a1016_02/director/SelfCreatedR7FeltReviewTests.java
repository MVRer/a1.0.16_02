package com.forzacode.a1016_02.director;

import static com.forzacode.a1016_02.director.DirectorTestSupport.rules;

import java.util.List;

import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

/**
 * Bug hunt R7 on the self-created layer (docs/SELF_CREATED.md), L4: a card whose moment only its target's own client has
 * is never felt by a friend beside them. Taken from {@code review/sc-r7}'s {@code SelfCreatedR7DirectorReviewTests} (its
 * other test, 01's home places, is another fix's); it failed on {@code self-created} at 5348ede.
 */
public class SelfCreatedR7FeltReviewTests {
	private static CardInfo registered(String id) {
		return DirectorTestSupport.registryDeck().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
	}

	/**
	 * 14 (a) "is the host's" (only their world list changes; a guest has none for this world), and a moment counts for
	 * the players who share it (D-060.3): a fire reaches everyone close by as felt. sc_14 is not PERSONAL, so a LAN guest
	 * beside the host feels it: their minor gap, tension, no-repeat cycle and 01's place are spent on a moment they never
	 * get. (02's arm is the same for a third player beside the target: only the target's client hides the name.)
	 */
	@GameTest
	public void aGuestBesideTheHostNeverFeelsTheHostsWorldList(GameTestHelper helper) {
		DirectorRules r = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		r.tracesMinorsPerHour = 1000;
		r.minorsPerHourMin = 1000;
		r.minorsPerHourMax = 1000;
		r.minorGap = 0;
		r.minAnyGap = 0;
		r.rateRefractory = 0;
		r.fakeChance = 0;
		List<CardInfo> deck = List.of(registered("sc_14"));
		DirectorBrain brain = new DirectorBrain(r, deck, 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, r);
		PacingMemory host = new PacingMemory();
		PacingMemory guest = new PacingMemory();
		host.sessionStart = 0;
		guest.sessionStart = 0;
		SelfCreatedDirectorTests.PlaceEnv hostEnv = new SelfCreatedDirectorTests.PlaceEnv(Stage.TRACES, new BlockPos(10, 64, 10));
		SelfCreatedDirectorTests.PlaceEnv guestEnv = new SelfCreatedDirectorTests.PlaceEnv(Stage.TRACES, new BlockPos(14, 64, 10));
		DirectorBrain.Clock clock = new DirectorBrain.Clock(2 * r.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		world.lastStepPlay = clock.worldTicks();
		host.lastStepPlay = clock.playTicks();
		guest.lastStepPlay = clock.playTicks();
		RandomSource random = RandomSource.create(7);
		for (int i = 0; i < 80 && hostEnv.fired.isEmpty(); i++) {
			clock = clock.plus(r.tickInterval);
			hostEnv.tension = 0;
			guestEnv.tension = 0;
			hostEnv.sharers = List.of(new DirectorBrain.Sharer(guest, clock, guestEnv, DirectorBrain.Recorder.NONE));
			brain.step(world, host, clock, hostEnv, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(hostEnv.fired.contains("sc_14"), "setup: sc_14 never fired for the host: " + hostEnv.fired);
		List<DirectorMemory.HistoryEntry> felt = guest.history().stream().filter(e -> e.cardId().equals("sc_14")).toList();
		helper.assertTrue(felt.isEmpty(), "the guest felt the host's world list: " + felt + ", their places " + guest.places().places());
		helper.succeed();
	}
}
