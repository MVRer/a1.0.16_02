package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

/**
 * VERIFY (v1.2): the unfit park (FIX-DIR, R1#1) against cards whose moment comes and goes with the time of day. The
 * dry runs (DESIGN 4b) let every card fit by an independent 35% die at each look, so a hold almost always fits within
 * a few looks and no card that fits half the time is ever parked there. Live, the sighting cards' gate
 * ({@code SightingGates.check}: never in clear daylight, the ridge only at dusk, the light only at night) is shut for
 * whole stretches of the in-game day: a sighting drawn by day is looked at all along for five minutes, parked, and
 * from then on goes back (parked again) at the first look that does not fit, until it fires (since FIX-FINAL, or until
 * its moment fits at one of the director's passing looks, which ends the park and the strike).
 */
public class VerifyV12DirectorTests {
	private static final int SIGHTINGS = 5;
	private static final int OTHERS = 3;

	/** Traces, a deck of five night-only sighting minors and three other minors that fit by the dice (35%). */
	private static double[] run(DirectorRules rules, boolean nightOnly, long from, long until, double share, long seed, double hours) {
		List<CardInfo> deck = new ArrayList<>();
		for (int i = 0; i < SIGHTINGS; i++) {
			deck.add(DirectorTestSupport.card("sighting_" + i, Tier.MINOR, Stage.TRACES, false, CardTag.SIGHTING));
		}
		for (int i = 0; i < OTHERS; i++) {
			deck.add(DirectorTestSupport.card("other_" + i, Tier.MINOR, Stage.TRACES, false));
		}
		DirectorBrain brain = new DirectorBrain(rules, deck, 50);
		DirectorTestSupport.Solo solo = DirectorTestSupport.pinned(rules);
		DirectorTestSupport.ScriptEnv env = new DirectorTestSupport.ScriptEnv(Stage.TRACES, 0, rules.attentionNeutral);
		RandomSource dice = RandomSource.create(seed * 31 + 7);
		DirectorBrain.Clock[] now = {new DirectorBrain.Clock(rules.hourTicks, 2 * DirectorBrain.DAY_TICKS)};
		env.fits = card -> {
			if (card.id().startsWith("sighting_")) {
				// Night (12000 to 24000 of the in-game day): half of every day, in one stretch (or by the dice, half the looks).
				long t = Math.floorMod(now[0].dayTicks(), DirectorBrain.DAY_TICKS);
				return nightOnly ? t >= from && t < until : dice.nextDouble() < share;
			}
			return dice.nextDouble() < 0.35;
		};
		RandomSource random = RandomSource.create(seed);
		solo.startAt(now[0]);
		long end = now[0].playTicks() + (long) (hours * rules.hourTicks);
		while (now[0].playTicks() < end) {
			now[0] = now[0].plus(rules.tickInterval);
			env.tension = 0;
			solo.step(brain, now[0], env, random, DirectorBrain.Recorder.NONE);
		}
		double sightings = env.fired.stream().filter(id -> id.startsWith("sighting_")).count();
		double others = env.fired.size() - sightings;
		return new double[] {sightings / hours, others / hours};
	}

	private static double[] runs(DirectorRules rules, boolean nightOnly, long from, long until, double share) {
		double[] sum = new double[2];
		int seeds = 24;
		for (int seed = 1; seed <= seeds; seed++) {
			double[] r = run(rules, nightOnly, from, until, share, seed, 20);
			sum[0] += r[0] / seeds;
			sum[1] += r[1] / seeds;
		}
		return sum;
	}

	@GameTest(maxTicks = 2000)
	public void verifyTheParkDoesNotStarveCardsWhoseMomentComesEveryNight(GameTestHelper helper) {
		DirectorRules parked = DirectorTestSupport.rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorRules unparked = DirectorTestSupport.rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		unparked.unfitPark = 0;
		double[] dice = runs(parked, false, 12_000, 24_000, 0.5);
		double[] night = runs(parked, true, 12_000, 24_000, 0.5);
		double[] nightNoPark = runs(unparked, true, 12_000, 24_000, 0.5);
		String summary = String.format(Locale.ROOT,
				"sighting minors per hour (other minors): fit by the dice (as the dry runs) %.3f (%.3f); night only, parked (main) %.3f (%.3f); night only, park off %.3f (%.3f)",
				dice[0], dice[1], night[0], night[1], nightNoPark[0], nightNoPark[1]);
		A1016_02.LOGGER.info("[a1016] VERIFY unfit park: {}", summary);
		// A card that fits every night must not lose a large share of its fires to the park meant for cards that never fit.
		helper.assertTrue(night[0] >= 0.75 * nightNoPark[0], summary);
		helper.succeed();
	}

	/**
	 * FINDING (low, fixed in FIX-FINAL: every director tick a parked or struck card's moment is looked at in passing, and
	 * one that fits is released, park and strike; the dusk-only minors here went from about 0.26 to 0.45 an hour, 0.39
	 * with the park off): a card whose moment is a short stretch of every in-game day, as the ridge's dusk sky (about 15% of
	 * the day), is drawn mostly outside it, looked at all along for five minutes, parked, and from then on is struck: each
	 * later draw outside its stretch parks it again at once. On main it fires about a third less often than with the
	 * park off, and about half as often as the dry runs (every look an independent die) show, so DESIGN 4b's sighting
	 * share overstates the ridge. The tier keeps its rate (the roll goes to another card); the ridge's share is what is lost.
	 */
	@GameTest(maxTicks = 2000)
	public void verifyFindingADuskOnlyCardLosesAThirdOfItsFiresToThePark(GameTestHelper helper) {
		DirectorRules parked = DirectorTestSupport.rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorRules unparked = DirectorTestSupport.rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		unparked.unfitPark = 0;
		double[] dice = runs(parked, false, 12_000, 15_600, 0.15);
		double[] dusk = runs(parked, true, 12_000, 15_600, 0.15);
		double[] duskNoPark = runs(unparked, true, 12_000, 15_600, 0.15);
		String summary = String.format(Locale.ROOT,
				"dusk-only minors per hour (other minors): by the dice (as the dry runs) %.3f (%.3f); parked (main) %.3f (%.3f); park off %.3f (%.3f)",
				dice[0], dice[1], dusk[0], dusk[1], duskNoPark[0], duskNoPark[1]);
		A1016_02.LOGGER.info("[a1016] VERIFY unfit park, dusk only: {}", summary);
		helper.assertTrue(dusk[0] >= 0.75 * duskNoPark[0], summary);
		helper.succeed();
	}
}
