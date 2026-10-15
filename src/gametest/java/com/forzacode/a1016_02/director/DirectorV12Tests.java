package com.forzacode.a1016_02.director;

import static com.forzacode.a1016_02.director.DirectorTestSupport.card;
import static com.forzacode.a1016_02.director.DirectorTestSupport.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.DirectorTestSupport.PlayerEnv;
import com.forzacode.a1016_02.director.DirectorTestSupport.ScriptEnv;
import com.forzacode.a1016_02.director.DirectorTestSupport.SharedStage;
import com.forzacode.a1016_02.director.DirectorTestSupport.Solo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * v1.2's director tests (D-070 to D-075): the first hour on the registered deck, fog weather and its boost, PERSONAL
 * cards beside friends, the group factor's solo path, a v1.1 world's re-roll and the director section's upgrade. Pure
 * logic and dry runs with their own memories; nothing here touches the live director or the world's state.
 * {@link DirectorMultiplayerTests} extends this.
 */
public class DirectorV12Tests {
	private static final UUID ANA = new UUID(0xA1L, 1L);
	private static final UUID BEN = new UUID(0xBEL, 2L);

	// --- D-070: the first hour ---

	/**
	 * D-070's first hour on the registered deck, every tempo, over {@link DirectorApi#tuningSeeds()} (fresh 20 h runs):
	 * the first fire, fakes included, by 30 minutes of play in at least 3 runs of 4 and by 45 in 9 of 10, two or more in
	 * the first hour in 7 of 10, never more than five in it; Traces inside 0.4 to 0.9 h times the tempo; every hard limit
	 * held over the 20 h. The 4b numbers the runs show are logged for DESIGN.
	 */
	@GameTest(maxTicks = 2000)
	public void theFirstHourHasSomethingInIt(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> runs = DirectorTestSupport.registryRuns(tempo);
			DirectorTestSupport.FirstHour first = DirectorTestSupport.FirstHour.of(runs);
			String at = tempo + ", " + first.summary() + ": ";
			A1016_02.LOGGER.info("[a1016] director first hour {}: {}", tempo, first.summary());
			A1016_02.LOGGER.info("[a1016] director 4b {}: {}", tempo, DirectorTestSupport.FourB.of(runs, deck).summary());
			helper.assertTrue(first.by30() * 4 >= first.runs() * 3, at + "the first fire by 30 minutes in fewer than 3 runs of 4");
			helper.assertTrue(first.by45() * 10 >= first.runs() * 9, at + "the first fire by 45 minutes in fewer than 9 runs of 10");
			helper.assertTrue(first.twoOrMore() * 10 >= first.runs() * 7, at + "two or more in the first hour in fewer than 7 runs of 10");
			helper.assertTrue(first.most() <= 5, at + "more than five in a first hour");
			DirectorRules rules = runs.getFirst().rules;
			double factor = new Pacing().paceFactor(tempo);
			helper.assertTrue(Math.abs(rules.tracesStart.min() / (double) rules.hourTicks - 0.4 * factor) < 0.01
					&& Math.abs(rules.tracesStart.max() / (double) rules.hourTicks - 0.9 * factor) < 0.01, at + "Traces' window " + rules.tracesStart);
			for (DirectorSim.Result run : runs) {
				String seed = at + "seed " + run.params.seed + ": ";
				long traces = run.stageAt(Stage.TRACES);
				helper.assertTrue(traces >= rules.tracesStart.min() && traces <= rules.tracesStart.max() + rules.tickInterval,
						seed + "Traces at " + traces + " outside " + rules.tracesStart);
				helper.assertTrue(run.violations.isEmpty(), seed + run.violations);
				List<String> hard = DirectorTestSupport.feltHardLimits(run, deck);
				helper.assertTrue(hard.isEmpty(), seed + hard);
			}
		}
		helper.succeed();
	}

	/**
	 * The first hour's cap (D-070, {@code firstHourMaxFires}): with every rate turned up and every moment fitting, a
	 * player's own first hour holds five moments, theirs and felt alike, and the sixth waits for the hour to end.
	 */
	@GameTest
	public void theFirstHourNeverHoldsMoreThanFive(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.aloneAmbientPerHour = 1000;
		rules.minAnyGap = 0;
		rules.aloneMaxAmbient = 100;
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("a", Tier.AMBIENT, Stage.ALONE, false), card("b", Tier.AMBIENT, Stage.ALONE, false),
				card("c", Tier.AMBIENT, Stage.ALONE, false)), 50);
		Solo solo = new Solo();
		DirectorTestSupport.pin(solo.world, rules);
		solo.pacing.sessionStart = 0;
		ScriptEnv env = new ScriptEnv(Stage.ALONE, 0, rules.attentionNeutral);
		RandomSource random = RandomSource.create(1);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(rules.joinGrace, rules.joinGrace);
		solo.startAt(clock);
		String blocked = null;
		while (clock.playTicks() < rules.hourTicks - rules.tickInterval) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			solo.step(brain, clock, env, random, DirectorBrain.Recorder.NONE);
			blocked = brain.globalBlock(solo.pacing, clock);
		}
		helper.assertTrue(env.fired.size() == rules.firstHourMaxFires && rules.firstHourMaxFires == 5, "first hour fires: " + env.fired);
		helper.assertTrue("first hour full".equals(blocked), "the gate at the end of the first hour: " + blocked);
		for (int i = 0; i < 4; i++) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			solo.step(brain, clock, env, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(env.fired.size() > 5, "nothing came once the first hour was over: " + env.fired);

		// A friend's full first hour holds back a moment that would reach them.
		PacingMemory ben = new PacingMemory();
		ben.sessionStart = 0;
		ben.firstHourFires = 5;
		helper.assertTrue("first hour full".equals(brain.sharedBlock(card("a", Tier.AMBIENT, Stage.ALONE, false), solo.world, ben,
				new DirectorBrain.Clock(rules.hourTicks / 2, rules.hourTicks / 2), Stage.ALONE)), "a friend's full first hour did not hold the moment back");
		helper.succeed();
	}

	/**
	 * A held ambient (or minor) card whose moment does not fit, as the tunnel ladder's first step drawn on the surface,
	 * goes back into its deck after {@code heldUnfitGiveBack} (5 minutes) instead of freezing its tier for the whole
	 * hold: the next draw passes it over and another ambient fires; once its moment comes (underground) it is drawn again
	 * and fires. A major whose moment never fits keeps the long hold.
	 */
	@GameTest
	public void aCardWhoseMomentNeverFitsNeverFreezesItsTier(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1_000_000;
		rules.minAnyGap = 0;
		helper.assertTrue(rules.heldUnfitGiveBack == rules.hourTicks / 12 && rules.holdGiveUp == rules.hourTicks, "the holds: " + rules.heldUnfitGiveBack
				+ " and " + rules.holdGiveUp + " ticks");
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("steps_behind", Tier.AMBIENT, Stage.ALONE, true, CardTag.SOUND, CardTag.PERSONAL),
				card("plain_a", Tier.AMBIENT, Stage.ALONE, false), card("plain_b", Tier.AMBIENT, Stage.ALONE, false)), 50);
		Solo solo = DirectorTestSupport.pinned(rules);
		boolean[] underground = {false};
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		env.fits = card -> !card.id().equals("steps_behind") || underground[0];
		List<long[]> gaveUp = new ArrayList<>();
		DirectorBrain.Recorder rec = new DirectorBrain.Recorder() {
			@Override
			public void gaveUp(DirectorBrain.Clock clock, Tier tier, String cardId, String why) {
				if (cardId.equals("steps_behind") && why.equals("its moment did not come")) {
					gaveUp.add(new long[] {clock.playTicks()});
				}
			}
		};
		RandomSource random = RandomSource.create(4);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		solo.startAt(clock);
		long heldAt = clock.playTicks();
		solo.pacing.held.put(Tier.AMBIENT, "steps_behind");
		solo.pacing.heldSince.put(Tier.AMBIENT, heldAt);
		while (gaveUp.isEmpty() && clock.playTicks() < heldAt + rules.holdGiveUp) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			solo.step(brain, clock, env, random, rec);
		}
		helper.assertTrue(env.fired.isEmpty() && !gaveUp.isEmpty() && gaveUp.getFirst()[0] - heldAt >= rules.heldUnfitGiveBack
				&& gaveUp.getFirst()[0] - heldAt < rules.heldUnfitGiveBack + rules.tickInterval, "the unfit hold went back after "
				+ (gaveUp.isEmpty() ? "never" : gaveUp.getFirst()[0] - heldAt) + " ticks, fired " + env.fired);
		helper.assertTrue(!"steps_behind".equals(solo.pacing.held(Tier.AMBIENT)) && !solo.pacing.cycleFired(Tier.AMBIENT).contains("steps_behind"),
				"the card did not go back into its deck");
		clock = clock.plus(rules.tickInterval);
		env.tension = 0;
		solo.step(brain, clock, env, random, rec);
		helper.assertTrue(env.fired.size() == 1 && env.fired.getFirst().startsWith("plain_"), "the next draw did not pass it over: " + env.fired);

		// Underground now: it is drawn again (once the others had their turn) and fires.
		underground[0] = true;
		for (int i = 0; i < 20 && !env.fired.contains("steps_behind"); i++) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			solo.step(brain, clock, env, random, rec);
		}
		helper.assertTrue(env.fired.contains("steps_behind"), "its moment came and it never fired: " + env.fired);

		// A major whose moment never fits keeps its long hold.
		DirectorBrain majors = new DirectorBrain(rules, List.of(card("major_x", Tier.MAJOR, Stage.PROXIMITY, false)), 10);
		Solo major = DirectorTestSupport.pinned(rules);
		ScriptEnv never = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		never.fits = card -> false;
		DirectorBrain.Clock at = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		major.startAt(at);
		major.pacing.nextMajorDue = at.playTicks();
		for (int i = 0; i < 40; i++) {
			at = at.plus(rules.tickInterval);
			never.tension = 0;
			major.step(majors, at, never, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue("major_x".equals(major.pacing.held(Tier.MAJOR)), "a major went back before its long hold: " + major.pacing.held(Tier.MAJOR));
		helper.succeed();
	}

	// --- D-071: fog weather ---

	/**
	 * Fog weather on the registered deck, every tempo: an hour under a fog spell that is not quiet has at least twice the
	 * fires of a clear hour (while the director may act: quiets and empty sessions are silences no weather opens), and an
	 * hour under a quiet spell about what a clear one has.
	 */
	@GameTest(maxTicks = 2000)
	public void fogSpellHoursHaveTwiceTheFires(GameTestHelper helper) {
		for (Tempo tempo : Tempo.values()) {
			DirectorTestSupport.SkyRates sky = DirectorTestSupport.SkyRates.of(DirectorTestSupport.registryRuns(tempo));
			String at = tempo + ", " + sky.summary() + ": ";
			A1016_02.LOGGER.info("[a1016] director fog weather {}: {} (x{} active, x{} overall)", tempo, sky.summary(),
					String.format(java.util.Locale.ROOT, "%.2f", sky.fogActive() / sky.clearActive()), String.format(java.util.Locale.ROOT, "%.2f", sky.fog() / sky.clear()));
			helper.assertTrue(sky.fogHours() >= 100 && sky.quietHours() >= 30, at + "too little fog to tell");
			helper.assertTrue(sky.fogActive() >= 2 * sky.clearActive(), at + "fog spell hours have less than twice a clear hour's fires");
			helper.assertTrue(sky.quietActive() <= 1.25 * sky.clearActive() && 1.5 * sky.quietActive() <= sky.fogActive(), at + "a quiet spell boosted the director");
		}
		helper.succeed();
	}

	/**
	 * The boost itself (D-071), scripted: a major slot due within {@code fogWeatherMajorPull} comes now under a fog spell
	 * that is not quiet, never under a quiet one or a clear sky, and never one due later; ambient odds rise by the factor
	 * and not at all for a quiet spell; a SIGHTING card's stage weight is times {@code fogWeatherSightingWeight}, a zero
	 * stays zero, other cards keep theirs.
	 */
	@GameTest
	public void theFogBoostComesOnlyFromASpellThatIsNotQuiet(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		long now = 5 * rules.hourTicks;
		Optional<FogSpell> fog = Optional.of(new FogSpell(0, Long.MAX_VALUE / 4, 0.6F, false));
		Optional<FogSpell> quiet = Optional.of(new FogSpell(0, Long.MAX_VALUE / 4, 0.6F, true));
		helper.assertTrue(rules.fogWeatherMajorPull == rules.hourTicks && rules.fogWeatherOddsFactor > 1 && rules.fogWeatherSightingWeight == 3,
				"the boost's defaults: pull " + rules.fogWeatherMajorPull + ", odds x" + rules.fogWeatherOddsFactor + ", sightings x" + rules.fogWeatherSightingWeight);
		Map<String, Long> majorDueIn = Map.of("clear, due in 50 min", 50L, "quiet, due in 50 min", 50L, "fog, due in 50 min", 50L, "fog, due in 70 min", 70L);
		for (Map.Entry<String, Long> c : majorDueIn.entrySet()) {
			DirectorBrain brain = new DirectorBrain(rules, List.of(card("major_m", Tier.MAJOR, Stage.PROXIMITY, false)), 10);
			Solo solo = DirectorTestSupport.pinned(rules);
			DirectorBrain.Clock clock = new DirectorBrain.Clock(now, 3 * DirectorBrain.DAY_TICKS);
			solo.startAt(clock);
			solo.pacing.nextMajorDue = now + c.getValue() * rules.hourTicks / 60;
			ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
			env.fog = c.getKey().startsWith("fog") ? fog : c.getKey().startsWith("quiet") ? quiet : Optional.empty();
			String outcome = solo.step(brain, clock, env, RandomSource.create(2), DirectorBrain.Recorder.NONE);
			boolean pulled = c.getKey().equals("fog, due in 50 min");
			helper.assertTrue(env.fired.equals(pulled ? List.of("major_m") : List.of()), c.getKey() + ": " + outcome);
		}

		// Ambient odds: an hour's worth of ticks, many times over, at 1 an hour.
		Map<String, Integer> fires = new java.util.LinkedHashMap<>();
		for (String sky : List.of("clear", "quiet", "fog")) {
			DirectorRules odds = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
			odds.proximityAmbientPerHour = 1;
			odds.minAnyGap = 0;
			DirectorBrain brain = new DirectorBrain(odds, List.of(card("a", Tier.AMBIENT, Stage.ALONE, false), card("b", Tier.AMBIENT, Stage.ALONE, false),
					card("c", Tier.AMBIENT, Stage.ALONE, false)), 10);
			Solo solo = DirectorTestSupport.pinned(odds);
			ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, odds.attentionNeutral);
			env.fog = sky.equals("fog") ? fog : sky.equals("quiet") ? quiet : Optional.empty();
			RandomSource random = RandomSource.create(7);
			DirectorBrain.Clock clock = new DirectorBrain.Clock(odds.hourTicks, 2 * DirectorBrain.DAY_TICKS);
			solo.startAt(clock);
			for (int i = 0; i < 300 * odds.hourTicks / odds.tickInterval; i++) {
				clock = clock.plus(odds.tickInterval);
				env.tension = 0;
				solo.pacing.lastFireAny = clock.playTicks(); // no pity (no gap either: minAnyGap is 0): the rate alone
				solo.step(brain, clock, env, random, DirectorBrain.Recorder.NONE);
			}
			fires.put(sky, env.fired.size());
		}
		double boost = fires.get("fog") / (double) fires.get("clear");
		double quietBoost = fires.get("quiet") / (double) fires.get("clear");
		helper.assertTrue(Math.abs(boost - rules.fogWeatherOddsFactor) < 0.15 * rules.fogWeatherOddsFactor && Math.abs(quietBoost - 1) < 0.15,
				"ambient fires in 300 h: " + fires + " (fog x" + boost + ", quiet x" + quietBoost + ")");

		// The SIGHTING weight.
		DirectorBrain brain = new DirectorBrain(rules, List.of(), 10);
		CardInfo sighting = card("s", Tier.MINOR, Stage.TRACES, true, CardTag.SIGHTING);
		CardInfo scar = card("x", Tier.MINOR, Stage.TRACES, false, CardTag.SCAR);
		CardInfo text = card("t", Tier.MINOR, Stage.TRACES, false, CardTag.TEXT, CardTag.SIGHTING);
		helper.assertTrue(Math.abs(brain.stageWeight(Stage.TRACES, sighting, true) - 3 * rules.tagWeight(Stage.TRACES, sighting)) < 1e-9
				&& brain.stageWeight(Stage.TRACES, sighting, false) == rules.tagWeight(Stage.TRACES, sighting), "a sighting's weight in fog");
		helper.assertTrue(brain.stageWeight(Stage.ALONE, scar, true) == rules.tagWeight(Stage.ALONE, scar), "fog changed a scar's weight");
		helper.assertTrue(brain.stageWeight(Stage.ALONE, text, true) == 0, "fog brought a zero-weight card into its stage");
		helper.assertTrue(DirectorBrain.boosts(fog) && !DirectorBrain.boosts(quiet) && !DirectorBrain.boosts(Optional.empty()), "boosts()");
		helper.succeed();
	}

	/**
	 * The live director's sky (D-071): the stored spell counts while the level's clock is inside it, never in a
	 * fixed-time dimension, and {@code /a1016 director} says what it does.
	 */
	@GameTest
	public void theLiveDirectorReadsTheStoredSpellOnTheLevelClock(GameTestHelper helper) {
		FogSpell night = new FogSpell(FogWeatherRules.nightFrom(4), FogWeatherRules.nightUntil(4, false), 0.65F, false);
		Optional<FogSpell> stored = Optional.of(night);
		helper.assertTrue(DirectorImpl.fogSpellOn(stored, night.fromClock(), false).equals(stored)
				&& DirectorImpl.fogSpellOn(stored, night.untilClock() - 1, false).equals(stored), "inside the spell");
		helper.assertTrue(DirectorImpl.fogSpellOn(stored, night.fromClock() - 1, false).isEmpty()
				&& DirectorImpl.fogSpellOn(stored, night.untilClock(), false).isEmpty(), "outside the spell");
		helper.assertTrue(DirectorImpl.fogSpellOn(stored, night.fromClock() + 100, true).isEmpty(), "a fixed-time dimension has no fog weather");
		helper.assertTrue(DirectorImpl.fogSpellOn(Optional.empty(), night.fromClock(), false).isEmpty(), "no spell stored");
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		String clear = DirectorDebug.fogLine(stored, night.fromClock() - 1, rules);
		String on = DirectorDebug.fogLine(stored, night.fromClock() + 100, rules);
		String quiet = DirectorDebug.fogLine(Optional.of(new FogSpell(night.fromClock(), night.untilClock(), 0.65F, true)), night.fromClock() + 100, rules);
		helper.assertTrue(clear.startsWith("clear") && on.startsWith("a fog spell until day 4 23500") && on.contains("x3.5") && quiet.startsWith("a quiet spell")
				&& quiet.endsWith("no boost"), "fog lines: " + clear + " / " + on + " / " + quiet);
		helper.succeed();
	}

	/**
	 * The dry runs' sky follows core's rules (D-071): the first night is always clear, the second or third is a fog
	 * night, every spell ends where a night, its morning or a day ends, about a quarter of them are quiet, and there are
	 * morning carry-overs and fog days. One sky for a group: both seats see the same spells.
	 */
	@GameTest(maxTicks = 2000)
	public void theDryRunsSkyFollowsCoresRules(GameTestHelper helper) {
		int spells = 0;
		int quiet = 0;
		int mornings = 0;
		int days = 0;
		for (Tempo tempo : Tempo.values()) {
			for (DirectorSim.Result run : DirectorTestSupport.registryRuns(tempo)) {
				String at = tempo + " seed " + run.params.seed + ": ";
				long night1 = FogWeatherRules.nightFrom(1) + 3000;
				long night2 = FogWeatherRules.nightFrom(2) + 3000;
				helper.assertTrue(run.fogSpells.stream().noneMatch(s -> s.activeAt(FogWeatherRules.nightFrom(0) + 3000)), at + "the first night was foggy");
				helper.assertTrue(run.fogSpells.stream().anyMatch(s -> s.activeAt(night1) || s.activeAt(night2)), at + "neither the second nor the third night was foggy");
				for (FogSpell spell : run.fogSpells) {
					long end = Math.floorMod(spell.untilClock(), FogWeatherRules.DAY);
					helper.assertTrue(end == FogWeatherRules.NIGHT_UNTIL || end == FogWeatherRules.MORNING_UNTIL || end == FogWeatherRules.DAY_UNTIL,
							at + "a spell ends at " + end + ": " + spell);
					spells++;
					quiet += spell.quiet() ? 1 : 0;
					mornings += end == FogWeatherRules.MORNING_UNTIL ? 1 : 0;
					days += Math.floorMod(spell.fromClock(), FogWeatherRules.DAY) == FogWeatherRules.DAY_FROM ? 1 : 0;
				}
			}
		}
		double share = quiet / (double) spells;
		A1016_02.LOGGER.info("[a1016] director dry-run sky: {} spells, {} quiet, {} morning carry-overs, {} fog days", spells, quiet, mornings, days);
		helper.assertTrue(spells > 5000 && share > 0.2 && share < 0.3 && mornings > 500 && days > 100,
				"spells " + spells + ", quiet " + quiet + ", mornings " + mornings + ", days " + days);

		DirectorSim.Group group = DirectorTestSupport.groupRuns(Tempo.SLOW_BURN, List.of(new DirectorSim.Seat(ANA, 0), new DirectorSim.Seat(BEN, 0)),
				DirectorSim.Sharing.APART, List.of(1016L)).getFirst();
		helper.assertTrue(!group.players.get(0).fogSpells.isEmpty() && group.players.get(0).fogSpells.equals(group.players.get(1).fogSpells),
				"two seats saw different skies");
		helper.succeed();
	}

	// --- D-074: friends ---

	/**
	 * D-074, scripted: Ana alone holds a PERSONAL card; the moment Ben is close it goes back into her deck (passed over
	 * next time) and a card for both fires instead, felt by him; while he stays the PERSONAL card is never drawn, and the
	 * deck turns over without it; Ben's own PERSONAL hold goes back beside her too; once he walks off it comes again.
	 */
	@GameTest
	public void aPersonalCardIsNeverDrawnBesideAFriendAndGoesBackWhenOneComes(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1_000_000;
		rules.minAnyGap = 0;
		CardInfo steps = card("steps_behind", Tier.AMBIENT, Stage.ALONE, true, CardTag.SOUND, CardTag.PERSONAL);
		DirectorBrain brain = new DirectorBrain(rules, List.of(steps, card("plain_a", Tier.AMBIENT, Stage.ALONE, false),
				card("plain_b", Tier.AMBIENT, Stage.ALONE, false)), 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		long w = 6 * rules.hourTicks;
		PacingMemory ana = new PacingMemory();
		ana.sessionStart = 0;
		ana.cycleFired.get(Tier.AMBIENT).addAll(List.of("plain_a", "plain_b"));
		ana.lastFired.put(Tier.AMBIENT, "plain_b");
		PacingMemory ben = new PacingMemory();
		ben.sessionStart = 0;
		PlayerEnv anaEnv = new PlayerEnv(stage);
		PlayerEnv benEnv = new PlayerEnv(stage);
		anaEnv.fits = card -> false;
		RandomSource random = RandomSource.create(3);
		brain.step(world, ana, clock(w, w), anaEnv, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue("steps_behind".equals(ana.held(Tier.AMBIENT)), "alone, Ana should hold the PERSONAL card: " + ana.held(Tier.AMBIENT));

		List<String> gaveUp = new ArrayList<>();
		DirectorBrain.Recorder rec = new DirectorBrain.Recorder() {
			@Override
			public void gaveUp(DirectorBrain.Clock clock, Tier tier, String cardId, String why) {
				gaveUp.add(cardId + ": " + why);
			}
		};
		anaEnv.fits = card -> true;
		anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, clock(2 * rules.hourTicks, w + rules.tickInterval), benEnv, DirectorBrain.Recorder.NONE));
		String outcome = brain.step(world, ana, clock(w + rules.tickInterval, w + rules.tickInterval), anaEnv, random, rec);
		helper.assertTrue(gaveUp.size() == 1 && gaveUp.getFirst().startsWith("steps_behind: someone came close"), "the PERSONAL hold did not go back: " + gaveUp);
		helper.assertTrue(ana.held(Tier.AMBIENT) == null && !ana.cycleFired(Tier.AMBIENT).contains("steps_behind"),
				"the PERSONAL card should be back in the deck: held " + ana.held(Tier.AMBIENT) + ", cycle " + ana.cycleFired(Tier.AMBIENT));
		helper.assertTrue(anaEnv.fired.equals(List.of("plain_a")) && ben.feltFires == 1, "a card for both did not fire at once: " + outcome);

		// Together for a while, both deciding: the decks turn over without the PERSONAL card, which stays undrawn in both.
		for (int i = 2; i < 80; i++) {
			long t = w + i * rules.tickInterval;
			DirectorBrain.Clock anaClock = clock(t, t);
			DirectorBrain.Clock benClock = clock(2 * rules.hourTicks + i * rules.tickInterval, t);
			anaEnv.tension = 0;
			benEnv.tension = 0;
			anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, benClock, benEnv, DirectorBrain.Recorder.NONE));
			brain.step(world, ana, anaClock, anaEnv, random, rec);
			benEnv.sharers = List.of(new DirectorBrain.Sharer(ana, anaClock, anaEnv, DirectorBrain.Recorder.NONE));
			brain.step(world, ben, benClock, benEnv, random, rec);
		}
		helper.assertTrue(anaEnv.fired.size() + benEnv.fired.size() > 40 && !anaEnv.fired.contains("steps_behind") && !benEnv.fired.contains("steps_behind"),
				"beside each other: Ana " + anaEnv.fired + ", Ben " + benEnv.fired + ", gave up " + gaveUp);

		// Ben's own PERSONAL hold goes back beside Ana too.
		long t80 = w + 80 * rules.tickInterval;
		ben.held.put(Tier.AMBIENT, "steps_behind");
		ben.heldSince.put(Tier.AMBIENT, 2 * rules.hourTicks + 79 * rules.tickInterval);
		benEnv.sharers = List.of(new DirectorBrain.Sharer(ana, clock(t80, t80), anaEnv, DirectorBrain.Recorder.NONE));
		benEnv.fits = card -> false;
		brain.step(world, ben, clock(2 * rules.hourTicks + 80 * rules.tickInterval, t80), benEnv, random, rec);
		helper.assertTrue(!"steps_behind".equals(ben.held(Tier.AMBIENT)) && gaveUp.getLast().startsWith("steps_behind: someone came close"),
				"Ben's own PERSONAL hold stayed beside Ana: " + gaveUp);

		anaEnv.sharers = List.of();
		int before = anaEnv.fired.size();
		for (int i = 81; i < 100 && !anaEnv.fired.subList(before, anaEnv.fired.size()).contains("steps_behind"); i++) {
			long t = w + i * rules.tickInterval;
			anaEnv.tension = 0;
			brain.step(world, ana, clock(t, t), anaEnv, random, rec);
		}
		helper.assertTrue(anaEnv.fired.subList(before, anaEnv.fired.size()).contains("steps_behind"), "alone again, the PERSONAL card never came: "
				+ anaEnv.fired.subList(before, anaEnv.fired.size()));
		helper.succeed();
	}

	/**
	 * D-074 in dry runs: a deck with PERSONAL cards (an ambient in Alone, a minor in Traces), two players. Always
	 * together, no PERSONAL card ever fires or is felt, and every limit holds; always apart, they come for each.
	 */
	@GameTest(maxTicks = 2000)
	public void personalCardsComeOnlyToSomeoneAlone(GameTestHelper helper) {
		List<CardInfo> deck = new ArrayList<>(SyntheticDeck.cards());
		deck.add(card("sim_steps_once", Tier.AMBIENT, Stage.ALONE, true, CardTag.SOUND, CardTag.PERSONAL));
		deck.add(card("sim_steps_coming", Tier.MINOR, Stage.TRACES, true, CardTag.SOUND, CardTag.PERSONAL));
		Set<String> personal = Set.of("sim_steps_once", "sim_steps_coming");
		List<DirectorSim.Seat> seats = List.of(new DirectorSim.Seat(ANA, 0), new DirectorSim.Seat(BEN, 0));
		int apart = 0;
		for (int i = 0; i < 12; i++) {
			DirectorRules rules = rules(Tempo.values()[i % 3], Signature.values()[i % Signature.values().length]);
			DirectorSim.Params params = DirectorTestSupport.params(rules, 500 + i, rules.attentionNeutral, DirectorTestSupport.HOURS);
			DirectorSim.Group together = DirectorSim.runGroup(rules, deck, 1000, seats, DirectorSim.Sharing.TOGETHER, params);
			helper.assertTrue(together.allViolations().isEmpty(), "together: " + together.allViolations());
			for (DirectorSim.Result player : together.players) {
				helper.assertTrue(player.felt().stream().noneMatch(e -> personal.contains(e.cardId())), "together, a PERSONAL card came: "
						+ player.felt().stream().filter(e -> personal.contains(e.cardId())).toList());
				helper.assertTrue(DirectorTestSupport.feltHardLimits(player, deck).isEmpty(), "together: " + DirectorTestSupport.feltHardLimits(player, deck));
				helper.assertTrue(player.felt().size() > 20, "together, the pair's decks stalled: " + player.felt().size() + " moments");
			}
			DirectorSim.Group alone = DirectorSim.runGroup(rules, deck, 1000, seats, DirectorSim.Sharing.APART, params);
			helper.assertTrue(alone.allViolations().isEmpty(), "apart: " + alone.allViolations());
			for (DirectorSim.Result player : alone.players) {
				apart += (int) player.fires().stream().filter(e -> personal.contains(e.cardId())).count();
			}
		}
		helper.assertTrue(apart > 24, "apart, PERSONAL cards fired only " + apart + " times");
		helper.succeed();
	}

	/**
	 * D-074: the group factor lives only where a moment is shared. Alone, the dry runs are the same with any group
	 * factor (the solo path is untouched), event for event.
	 */
	@GameTest(maxTicks = 2000)
	public void theGroupFactorNeverTouchesTheSoloPath(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		for (Tempo tempo : Tempo.values()) {
			for (long seed : DirectorApi.tuningSeeds().subList(0, 12)) {
				List<List<DirectorSim.Event>> runs = new ArrayList<>();
				for (double factor : new double[] {1, 1.5, 3}) {
					DirectorConfig config = new DirectorConfig();
					config.groupRateFactor = factor;
					DirectorRules rules = DirectorRules.from(new Pacing(), config, com.forzacode.a1016_02.debug.Playthrough.profile(seed, tempo));
					DirectorSim.Params params = DirectorSim.Params.from(config, rules);
					params.hours = DirectorTestSupport.HOURS;
					params.seed = seed;
					params.attention = config.attentionNeutral;
					runs.add(DirectorSim.run(rules, deck, 1000, new DirectorMemory(), Stage.ALONE, 0, new DirectorBrain.Clock(0, 0), params).events);
				}
				helper.assertTrue(runs.get(0).equals(runs.get(1)) && runs.get(1).equals(runs.get(2)), tempo + " seed " + seed + ": the group factor changed a solo run");
			}
		}
		helper.succeed();
	}

	// --- D-075: world rules and the config section ---

	/**
	 * D-075, the world part: a world saved before v1.2 ({@code rulesVersion} missing, so 0) rolls its stage points again
	 * in v1.2's windows on its first time step, once. One still in Alone before them stays in Alone; one already in
	 * Traces stays there (never back); one in Alone past every new Traces point starts Traces now and keeps Proximity a
	 * rolled Traces stretch away (no stage skipped); one in Proximity stays. A v1.2 world keeps its points. A pacing saved
	 * before v1.2 rolls its major slot again at its next session (never later than it was) and its session minor rate in
	 * v1.2's range; a v1.2 pacing keeps its slot.
	 */
	@GameTest
	public void aWorldSavedBeforeV12RollsItsStagePointsAgainInTheNewWindows(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		long hour = rules.hourTicks;
		DirectorBrain brain = new DirectorBrain(rules, List.of(), 10);
		Pacing.TickRange traces = rules.tracesStart;
		Pacing.TickRange proximity = rules.proximityStart;
		helper.assertTrue(new WorldMemory().rulesVersion() == WorldMemory.RULES_VERSION && WorldMemory.RULES_VERSION == 1, "a new world part's rules");
		for (long seed = 0; seed < 40; seed++) {
			// In Alone, before the new windows (v1.1 had Traces at 2.5 h, Proximity at 5.5 h).
			WorldMemory alone = v11World(0.2 * hour, (long) (2.5 * hour), (long) (5.5 * hour));
			ScriptEnv env = new ScriptEnv(Stage.ALONE, 0, rules.attentionNeutral);
			brain.advanceWorld(alone, worldClock(0.2 * hour), env, RandomSource.create(seed), DirectorBrain.Recorder.NONE);
			helper.assertTrue(alone.rulesVersion() == 1 && inside(alone.tracesAt(), traces) && inside(alone.proximityAt(), proximity) && env.stage == Stage.ALONE,
					"seed " + seed + ", Alone at 0.2 h: traces " + alone.tracesAt() + " in " + traces + ", proximity " + alone.proximityAt() + " in " + proximity
							+ ", stage " + env.stage);

			// Traces reached at 1.2 h under v1.1: stays in Traces whatever the new Traces point.
			WorldMemory inTraces = v11World(1.5 * hour, (long) (1.2 * hour), (long) (5.5 * hour));
			ScriptEnv tracesEnv = new ScriptEnv(Stage.TRACES, 0, rules.attentionNeutral);
			brain.advanceWorld(inTraces, worldClock(1.5 * hour), tracesEnv, RandomSource.create(seed), DirectorBrain.Recorder.NONE);
			helper.assertTrue(tracesEnv.stage == Stage.TRACES && inside(inTraces.tracesAt(), traces) && inside(inTraces.proximityAt(), proximity),
					"seed " + seed + ", in Traces at 1.5 h: " + tracesEnv.stage + ", traces " + inTraces.tracesAt() + ", proximity " + inTraces.proximityAt());

			// Still in Alone at 1.2 h (v1.1's slow window), past every new Traces point: Traces now, Proximity later.
			WorldMemory late = v11World(1.2 * hour, (long) (2.8 * hour), (long) (5.5 * hour));
			ScriptEnv lateEnv = new ScriptEnv(Stage.ALONE, 0, rules.attentionNeutral);
			brain.advanceWorld(late, worldClock(1.2 * hour), lateEnv, RandomSource.create(seed), DirectorBrain.Recorder.NONE);
			long stretch = late.proximityAt() - late.tracesAt();
			helper.assertTrue(lateEnv.stage == Stage.TRACES && late.tracesAt() == (long) (1.2 * hour)
					&& stretch >= proximity.min() - traces.max() && stretch <= proximity.max() - traces.min() && lateEnv.stage != Stage.PROXIMITY,
					"seed " + seed + ", Alone at 1.2 h: " + lateEnv.stage + ", traces " + late.tracesAt() + ", proximity " + late.proximityAt());

			// Proximity reached: stays.
			WorldMemory near = v11World(6 * hour, (long) (2 * hour), (long) (5.2 * hour));
			ScriptEnv nearEnv = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
			brain.advanceWorld(near, worldClock(6 * hour), nearEnv, RandomSource.create(seed), DirectorBrain.Recorder.NONE);
			helper.assertTrue(nearEnv.stage == Stage.PROXIMITY && near.rulesVersion() == 1, "seed " + seed + ": Proximity moved to " + nearEnv.stage);
		}

		// A v1.2 world keeps its points, and the re-roll happens once.
		WorldMemory current = v11World(0.2 * hour, (long) (2.5 * hour), (long) (5.5 * hour));
		current.rulesVersion = WorldMemory.RULES_VERSION;
		WorldMemory loaded = WorldMemory.fromTag(current.toTag());
		ScriptEnv env = new ScriptEnv(Stage.ALONE, 0, rules.attentionNeutral);
		brain.advanceWorld(loaded, worldClock(0.2 * hour), env, RandomSource.create(1), DirectorBrain.Recorder.NONE);
		helper.assertTrue(loaded.tracesAt() == (long) (2.5 * hour) && loaded.proximityAt() == (long) (5.5 * hour), "a v1.2 world's points were rolled again");
		WorldMemory once = v11World(0.2 * hour, (long) (2.5 * hour), (long) (5.5 * hour));
		brain.advanceWorld(once, worldClock(0.2 * hour), env, RandomSource.create(1), DirectorBrain.Recorder.NONE);
		long rolled = once.tracesAt();
		brain.advanceWorld(once, worldClock(0.2 * hour), env, RandomSource.create(99), DirectorBrain.Recorder.NONE);
		helper.assertTrue(once.tracesAt() == rolled && WorldMemory.fromTag(once.toTag()).rulesVersion() == 1, "the re-roll happened twice or was not saved");

		// Pacings: a v1.1 slot 5 h ahead is rolled again at the next session; one 10 minutes ahead is kept; a v1.2 one keeps its own.
		long now = 8 * hour;
		DirectorBrain.Clock clock = new DirectorBrain.Clock(now, 6 * DirectorBrain.DAY_TICKS, now);
		for (long seed = 0; seed < 20; seed++) {
			PacingMemory far = v11Pacing(now + 5 * hour);
			brain.onJoin(pinnedWorld(rules), far, clock, new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral), RandomSource.create(seed),
					DirectorBrain.Recorder.NONE);
			helper.assertTrue(far.rulesVersion() == 1 && far.nextMajorDue() >= now + rules.majorEvery.min() && far.nextMajorDue() <= now + rules.majorEvery.max()
					&& far.sessionMinorRate() >= rules.minorsPerHourMin && far.sessionMinorRate() <= rules.minorsPerHourMax,
					"seed " + seed + ": the v1.1 pacing's slot " + far.nextMajorDue() + ", minor rate " + far.sessionMinorRate());
		}
		PacingMemory soon = v11Pacing(now + hour / 6);
		brain.onJoin(pinnedWorld(rules), soon, clock, new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral), RandomSource.create(5), DirectorBrain.Recorder.NONE);
		helper.assertTrue(soon.nextMajorDue() == now + hour / 6, "a v1.1 slot sooner than the new rules' was moved later: " + soon.nextMajorDue());
		PacingMemory v12 = v11Pacing(now + 5 * hour);
		v12.rulesVersion = WorldMemory.RULES_VERSION;
		PacingMemory v12Loaded = PacingMemory.fromTag(v12.toTag());
		brain.onJoin(pinnedWorld(rules), v12Loaded, clock, new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral), RandomSource.create(5),
				DirectorBrain.Recorder.NONE);
		helper.assertTrue(v12Loaded.nextMajorDue() == now + 5 * hour, "a v1.2 pacing's slot was rolled again: " + v12Loaded.nextMajorDue());
		helper.succeed();
	}

	/**
	 * D-075, the director's section: a v1.1 file (no version, v1.1's defaults, a few values the player changed) upgrades
	 * to v1.2's defaults where nothing was changed and keeps every hand-changed value (a rate, a tag weight, an added tag);
	 * the new fields come with their defaults; the result is written at version 1 and never upgraded again; a missing
	 * section is v1.2's defaults.
	 */
	@GameTest
	public void theDirectorSectionUpgradeKeepsWhatThePlayerChanged(GameTestHelper helper) {
		Gson gson = new Gson();
		DirectorConfig old = new DirectorConfig();
		old.aloneAmbientPerHour = 0.3;
		old.proximityAmbientPerHour = 2.0;
		old.tracesMinorsPerHour = 0.5; // the player's (v1.1's default was 0.25)
		old.stageTagWeights = DirectorConfig.v11StageTagWeights();
		old.stageTagWeights.get("ALONE").put("SCAR", 0.45); // the player's (0.3)
		old.stageTagWeights.get("TRACES").put("MOB", 0.8); // the player's addition
		JsonObject section = gson.toJsonTree(old).getAsJsonObject();
		for (String added : List.of("version", "firstHourMaxFires", "emptySessionMinStage", "groupRateFactor", "fogWeatherOddsFactor",
				"fogWeatherSightingWeight", "fogWeatherMajorPullMinutes")) {
			section.remove(added);
		}
		JsonObject sections = new JsonObject();
		sections.add("director", section);
		DirectorConfig read = ModConfig.readSection(sections, "director", DirectorConfig.class, DirectorConfig::new);
		DirectorConfig d = new DirectorConfig();
		helper.assertTrue(read.version == DirectorConfig.VERSION && DirectorConfig.VERSION == 1, "version " + read.version);
		helper.assertTrue(read.aloneAmbientPerHour == d.aloneAmbientPerHour && read.proximityAmbientPerHour == 2.5 && read.tracesMinorsPerHour == 0.5,
				"rates: " + read.aloneAmbientPerHour + ", " + read.proximityAmbientPerHour + ", " + read.tracesMinorsPerHour);
		Map<String, Double> alone = read.stageTagWeights.get("ALONE");
		Map<String, Double> traces = read.stageTagWeights.get("TRACES");
		helper.assertTrue(alone.get("SIGHTING") == 0.3 && alone.get("SCAR") == 0.45 && alone.get("DIG") == 0.6 && alone.get("ACCIDENT") == 0 && alone.get("TEXT") == 0,
				"Alone's weights: " + alone);
		helper.assertTrue(traces.get("SIGHTING") == 0.5 && traces.get("TEXT") == 0.3 && traces.get("MOB") == 0.8, "Traces' weights: " + traces);
		helper.assertTrue(read.stageTagWeights.get("TELLING").equals(d.stageTagWeights.get("TELLING")), "Telling's weights: " + read.stageTagWeights.get("TELLING"));
		helper.assertTrue(read.firstHourMaxFires == 5 && read.emptySessionMinStage.equals("PROXIMITY") && read.groupRateFactor == 1.5
				&& read.fogWeatherOddsFactor == d.fogWeatherOddsFactor && read.fogWeatherSightingWeight == 3 && read.fogWeatherMajorPullMinutes == 60,
				"the v1.2 fields: " + gson.toJson(read));

		// Written at version 1, it reads back unchanged; a v1.2 value equal to an old default is the player's.
		JsonObject again = new JsonObject();
		again.add("director", gson.toJsonTree(read));
		DirectorConfig twice = ModConfig.readSection(again, "director", DirectorConfig.class, DirectorConfig::new);
		helper.assertTrue(gson.toJson(twice).equals(gson.toJson(read)), "a second read changed the section");
		DirectorConfig chosen = new DirectorConfig();
		chosen.version = DirectorConfig.VERSION;
		chosen.aloneAmbientPerHour = 0.3;
		JsonObject mine = new JsonObject();
		mine.add("director", gson.toJsonTree(chosen));
		helper.assertTrue(ModConfig.readSection(mine, "director", DirectorConfig.class, DirectorConfig::new).aloneAmbientPerHour == 0.3,
				"a v1.2 file's 0.3 was taken for v1.1's default");

		DirectorConfig fresh = ModConfig.readSection(new JsonObject(), "director", DirectorConfig.class, DirectorConfig::new);
		helper.assertTrue(fresh.version == DirectorConfig.VERSION && gson.toJson(fresh).equals(gson.toJson(withVersion(new DirectorConfig()))),
				"a missing section is not v1.2's defaults: " + gson.toJson(fresh));
		helper.succeed();
	}

	// --- helpers ---

	private static DirectorBrain.Clock clock(long own, long world) {
		return new DirectorBrain.Clock(own, 2 * DirectorBrain.DAY_TICKS + world, world);
	}

	private static DirectorBrain.Clock worldClock(double worldTicks) {
		long w = (long) worldTicks;
		return new DirectorBrain.Clock(w, w, w);
	}

	private static boolean inside(long ticks, Pacing.TickRange range) {
		return ticks >= range.min() && ticks <= range.max();
	}

	/** A world part as v1.1 saved it: no rulesVersion, its stage clock and points, stepped up to now. */
	private static WorldMemory v11World(double stageClock, long tracesAt, long proximityAt) {
		WorldMemory w = new WorldMemory();
		w.stageClock = stageClock;
		w.lastStepPlay = (long) stageClock;
		w.tracesAt = tracesAt;
		w.proximityAt = proximityAt;
		w.rolledTempo = Tempo.SLOW_BURN.name();
		CompoundTag tag = w.toTag();
		tag.remove("rulesVersion");
		return WorldMemory.fromTag(tag);
	}

	/** A pacing as v1.1 saved it: no rulesVersion, a session long over, its next major slot here. */
	private static PacingMemory v11Pacing(long nextMajorDue) {
		PacingMemory m = new PacingMemory();
		m.nextMajorDue = nextMajorDue;
		m.sessionMinorRate = 1.2;
		CompoundTag tag = m.toTag();
		tag.remove("rulesVersion");
		return PacingMemory.fromTag(tag);
	}

	private static WorldMemory pinnedWorld(DirectorRules rules) {
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		return world;
	}

	private static DirectorConfig withVersion(DirectorConfig config) {
		config.version = DirectorConfig.VERSION;
		return config;
	}
}
