package com.forzacode.a1016_02.director;

import static com.forzacode.a1016_02.director.DirectorTestSupport.card;
import static com.forzacode.a1016_02.director.DirectorTestSupport.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.DirectorTestSupport.ScriptEnv;
import com.forzacode.a1016_02.director.DirectorTestSupport.Solo;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

/**
 * Bug hunt R1 (v1.2): the director's held cards and decks, and the dry runs' fog weather against the live weather.
 * Pure logic and dry runs with their own memories. {@link DirectorV12Tests} extends this.
 */
public class R1ReviewDirectorTests {
	/** Play ticks of every minor fired in {@code hours} of a scripted solo run in this stage (tension held at 0, never quiet). */
	private static List<Long> minorFires(DirectorBrain brain, Stage stage, Predicate<CardInfo> fits, double hours, long seed) {
		DirectorRules rules = brain.rules();
		Solo solo = DirectorTestSupport.pinned(rules);
		ScriptEnv env = new ScriptEnv(stage, 0, rules.attentionNeutral);
		env.fits = fits;
		List<Long> minors = new ArrayList<>();
		RandomSource random = RandomSource.create(seed);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		solo.startAt(clock);
		long end = clock.playTicks() + Math.round(hours * rules.hourTicks);
		while (clock.playTicks() < end) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			solo.step(brain, clock, env, random, DirectorBrain.Recorder.NONE);
			if (solo.pacing.lastFireTier.getOrDefault(Tier.MINOR, DirectorMemory.NEVER) == clock.playTicks()) {
				minors.add(clock.playTicks() - rules.hourTicks);
			}
		}
		return minors;
	}

	private static long count(List<Long> fires, long from, long to) {
		return fires.stream().filter(t -> t >= from && t < to).count();
	}

	/**
	 * FINDING (HIGH): a card whose moment never comes freezes its tier for good once every other card of the cycle has
	 * fired. The deck never runs out (the card only waits on its moment, so {@code runOut} is false), the deck-stall
	 * refill never starts ({@code deckStuckSince} is only set when nothing can be drawn), and the 5-minute unfit
	 * give-back puts it straight back for the next draw, which takes it again (it is the only card left): draw, hold 5
	 * min, give back, draw. Here three plain Proximity minors and the tunnel ladder's second rung, which never fits for
	 * someone who never walks a long straight dug tunnel (its rung order and tunnel are its context check): after the
	 * three plain ones the minor tier is silent for the rest of the 20 h. Without the ladder card the same deck keeps
	 * firing about two minors an hour.
	 */
	@GameTest(maxTicks = 400)
	public void r1ACardWhoseMomentNeverComesNeverFreezesItsTierForGood(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		List<CardInfo> plain = List.of(card("m1", Tier.MINOR, Stage.TRACES, false), card("m2", Tier.MINOR, Stage.TRACES, false),
				card("m3", Tier.MINOR, Stage.TRACES, false));
		CardInfo ladder = card("steps_behind_coming", Tier.MINOR, Stage.TRACES, true, CardTag.SOUND, CardTag.PERSONAL);
		Predicate<CardInfo> notTheLadder = card -> !card.id().equals(ladder.id());
		long tenHours = 10 * rules.hourTicks;

		List<Long> control = minorFires(new DirectorBrain(rules, plain, 50), Stage.PROXIMITY, notTheLadder, 20, 3);
		helper.assertTrue(count(control, tenHours, 2 * tenHours) >= 10, "control: without the ladder card " + count(control, tenHours, 2 * tenHours)
				+ " minors in the last 10 h");

		List<CardInfo> deck = new ArrayList<>(plain);
		deck.add(ladder);
		List<Long> fires = minorFires(new DirectorBrain(rules, deck, 50), Stage.PROXIMITY, notTheLadder, 20, 3);
		long lastHalf = count(fires, tenHours, 2 * tenHours);
		helper.assertTrue(lastHalf >= 10, "with one card whose moment never comes: " + fires.size() + " minors in 20 h, the last at "
				+ (fires.isEmpty() ? "-" : String.format("%.2f h", fires.getLast() / (double) rules.hourTicks)) + ", " + lastHalf
				+ " in the last 10 h (the tier froze on " + ladder.id() + ")");
		helper.succeed();
	}

	/**
	 * The same on the registered deck: a player for whom every card fits except the tunnel ladder's three (they never
	 * walk a straight dug tunnel 16 long). Once the cycle's other minors have fired, the minor tier stops for good.
	 */
	@GameTest(maxTicks = 1200)
	public void r1TheRegisteredMinorDeckKeepsGoingForSomeoneWhoNeverWalksALongTunnel(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		long minorsInDeck = deck.stream().filter(c -> c.tier() == Tier.MINOR && c.earliestStage().level() <= Stage.PROXIMITY.level()).count();
		Predicate<CardInfo> notTheLadder = card -> !card.id().startsWith("steps_behind_");
		List<Long> fires = minorFires(new DirectorBrain(rules, deck, 200), Stage.PROXIMITY, notTheLadder, 30, 11);
		long hour = rules.hourTicks;
		StringBuilder perFive = new StringBuilder();
		for (int h = 0; h < 30; h += 5) {
			perFive.append(count(fires, h * hour, (h + 5) * hour)).append(h + 5 < 30 ? ", " : "");
		}
		A1016_02.LOGGER.info("[a1016] R1 registered deck, {} minors up to Proximity: minors per 5 h of Proximity {}", minorsInDeck, perFive);
		long last = count(fires, 20 * hour, 30 * hour);
		helper.assertTrue(last >= 10, minorsInDeck + " minors in the deck; minors per 5 h: " + perFive + "; " + last + " in hours 20 to 30, the last at "
				+ (fires.isEmpty() ? "-" : String.format("%.1f h", fires.getLast() / (double) hour)));
		helper.succeed();
	}

	/**
	 * D-074 with three players (checked): all three always together roll about the pair's 1.5 solo rates between them
	 * (Traces' ambients, which no hard gap holds back, at 1.2 to 1.7 times one player's), with every limit held. In a
	 * chain (Ana and Ben close, Ben and Cal close, Ana and Cal never) the one in the middle shares both ends' moments:
	 * logged.
	 */
	@GameTest(maxTicks = 2000)
	public void r1ThreePlayersTogetherRollThePairsRate(GameTestHelper helper) {
		Tempo tempo = Tempo.SLOW_BURN;
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		List<Long> seeds = DirectorApi.tuningSeeds().subList(0, 64);
		DirectorSim.Rates solo = DirectorSim.Rates.of(DirectorTestSupport.registryRuns(tempo));
		UUID ana = new UUID(0xA1L, 1L);
		UUID ben = new UUID(0xBEL, 2L);
		UUID cal = new UUID(0xCAL, 3L);
		List<DirectorSim.Seat> seats = List.of(new DirectorSim.Seat(ana, 0), new DirectorSim.Seat(ben, 0), new DirectorSim.Seat(cal, 0));
		List<DirectorSim.Group> together = DirectorTestSupport.groupRuns(tempo, seats, DirectorSim.Sharing.TOGETHER, seeds);
		for (DirectorSim.Group group : together) {
			helper.assertTrue(group.allViolations().isEmpty(), "three together: " + group.allViolations());
			for (DirectorSim.Result player : group.players) {
				List<String> hard = DirectorTestSupport.feltHardLimits(player, deck);
				helper.assertTrue(hard.isEmpty(), "three together, seed " + player.params.seed + ": " + hard);
			}
		}
		double ratio = DirectorSim.Rates.felt(DirectorTestSupport.seats(together)).tracesAmbientsPerHour() / solo.tracesAmbientsPerHour();
		DirectorSim.Sharing chain = (t, a, b) -> !((a.equals(ana) && b.equals(cal)) || (a.equals(cal) && b.equals(ana)));
		List<DirectorSim.Group> chained = DirectorTestSupport.groupRuns(tempo, seats, chain, seeds);
		List<DirectorSim.Result> middle = new ArrayList<>();
		List<DirectorSim.Result> ends = new ArrayList<>();
		for (DirectorSim.Group group : chained) {
			helper.assertTrue(group.allViolations().isEmpty(), "a chain: " + group.allViolations());
			middle.add(group.player(ben));
			ends.add(group.player(ana));
			ends.add(group.player(cal));
		}
		double middleRatio = DirectorSim.Rates.felt(middle).tracesAmbientsPerHour() / solo.tracesAmbientsPerHour();
		double endRatio = DirectorSim.Rates.felt(ends).tracesAmbientsPerHour() / solo.tracesAmbientsPerHour();
		A1016_02.LOGGER.info("[a1016] R1 three players: together {} x solo Traces ambients; a chain: middle {} x, ends {} x", ratio, middleRatio, endRatio);
		helper.assertTrue(ratio >= 1.2 && ratio <= 1.7, "three together felt " + ratio + " x one player's Traces ambients");
		helper.succeed();
	}

	/**
	 * FINDING (low, fixed in INT-1: both now roll at core's {@code FogWeatherRules.NIGHT_ROLL}, 10000): the dry runs roll
	 * tonight at {@code DirectorSim.NIGHT_ROLL} 11000 ("atmosphere's live weather rolls
	 * it then too"), but the live weather rolls it at {@code FogWeather.nightRollTime} 10000 (DAY_UNTIL minus the
	 * fade). {@code DirectorImpl.liveWeather} counts tonight as not rolled before 11000 too, so a dry run, timewarp or
	 * {@code /a1016 director sim} started between 10000 and 11000 rolls tonight a second time and replaces the spell the
	 * live weather already stored (here a quiet fog night): the forecast boosts a night that will not boost, or brings
	 * fog to a night the live weather rolled clear. (The sim's sky also lets a fog day thin to 11% before handing over,
	 * where the live weather never thins.)
	 */
	@GameTest
	public void r1ADryRunKeepsTheFogNightTheLiveWeatherAlreadyRolled(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		long day = 3;
		long start = day * DirectorBrain.DAY_TICKS + 10_500; // after the live roll at 10000, before the sim's at 11000
		FogSpell live = new FogSpell(FogWeatherRules.nightFrom(day), FogWeatherRules.nightUntil(day, false), 0.55F, true);
		int replaced = 0;
		int boosted = 0;
		int seeds = 24;
		for (long seed = 1; seed <= seeds; seed++) {
			DirectorSim.Params params = DirectorTestSupport.params(rules, seed, rules.attentionNeutral, 0.12); // ends before 23000
			params.fogSpell = Optional.of(live);
			// What DirectorImpl.liveWeather passes at 10500: c.day() nights, tonight not counted yet.
			params.fogHistory = new FogWeatherRules.History((int) day, 0, false, false);
			DirectorSim.Result r = DirectorSim.run(rules, SyntheticDeck.cards(), 100, new DirectorMemory(), Stage.TRACES, 0,
					new DirectorBrain.Clock(5 * rules.hourTicks, start), params);
			if (!r.fogSpells.equals(List.of(live))) {
				replaced++;
			}
			if (r.skyTicks(DirectorSim.Sky.FOG) > 0) {
				boosted++;
			}
		}
		helper.assertTrue(replaced == 0 && boosted == 0, "a dry run from 10500 rolled tonight again in " + replaced + " of " + seeds + " seeds (" + boosted
				+ " boosting a night the live weather made quiet); the sims roll tonight at " + FogWeatherRules.nightRollTime(FogWeatherRules.Settings.DEFAULT)
				+ ", the live weather at " + FogWeatherRules.NIGHT_ROLL);
		helper.succeed();
	}
}
