package com.forzacode.a1016_02.director;

import static com.forzacode.a1016_02.director.DirectorTestSupport.card;
import static com.forzacode.a1016_02.director.DirectorTestSupport.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
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
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * v1.3's director tests (W-DIR2, D-077): a blind moment's recheck ({@link DirectorBrain#recheck}) fires a held or parked
 * card whose moment fits now, BLIND cards first, through every gate of a director tick and never drawing; a held BLIND
 * card goes back after {@code blindHold} (15 minutes) of unfit looks, parked; the dry runs' blind moments
 * ({@code simBlindPerHour}), and no tier frozen without them; the director
 * section's step to version 3; {@code /a1016 director}'s recheck line. Pure logic and dry runs with their own memories,
 * and one live call for a player who never joins. {@link DirectorMultiplayerTests} extends this.
 */
public class DirectorV13Tests extends DirectorV12Tests {
	private static final UUID BEN = new UUID(0xBEL, 13L);

	/** Stand-ins for dig's blind cards (D-077), as the decks see them. */
	private static final CardInfo BLIND_STEP = card("sim_blind_step", Tier.AMBIENT, Stage.ALONE, true, CardTag.SOUND, CardTag.PERSONAL, CardTag.BLIND);
	private static final CardInfo BLIND_TORCH = card("sim_blind_torch", Tier.MINOR, Stage.TRACES, false, CardTag.LIGHT, CardTag.PERSONAL, CardTag.BLIND);
	private static final CardInfo BLIND_DOOR = card("sim_blind_door", Tier.MINOR, Stage.TRACES, false, CardTag.SOUND, CardTag.PERSONAL, CardTag.BLIND);
	private static final List<CardInfo> BLIND_STAND_INS = List.of(BLIND_STEP, BLIND_TORCH, BLIND_DOOR);

	/** Hour 2 of the player's play, day 2: past the join grace, the first hour and the first in-game day. */
	private static DirectorBrain.Clock playing(DirectorRules rules) {
		return new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
	}

	/** A scripted player past their grace and first day, stage points pinned, holding {@code card} since {@code at}. */
	private static Solo holding(DirectorRules rules, CardInfo card, DirectorBrain.Clock at) {
		Solo solo = DirectorTestSupport.pinned(rules);
		solo.startAt(at);
		solo.pacing.held.put(card.tier(), card.id());
		solo.pacing.heldSince.put(card.tier(), at.playTicks());
		return solo;
	}

	/** Records the recheck outcomes, the draws and the give-backs. */
	private static final class Watch implements DirectorBrain.Recorder {
		final List<String> rechecked = new ArrayList<>();
		final List<String> drew = new ArrayList<>();
		final List<String> gaveUp = new ArrayList<>();

		@Override
		public void rechecked(DirectorBrain.Clock clock, @Nullable CardInfo fired, String outcome) {
			rechecked.add((fired == null ? "-" : fired.id()) + ": " + outcome);
		}

		@Override
		public void drew(DirectorBrain.Clock clock, CardInfo card) {
			drew.add(card.id());
		}

		@Override
		public void refilled(DirectorBrain.Clock clock, Tier tier) {
			drew.add("refill " + tier);
		}

		@Override
		public void gaveUp(DirectorBrain.Clock clock, Tier tier, String cardId, String why) {
			gaveUp.add(cardId + ": " + why);
		}
	}

	// --- the recheck ---

	/**
	 * D-077: a held BLIND card whose moment is not there (no menu open) stays held at a recheck, which counts no unfit
	 * look and fires nothing; while the player is blind the recheck fires it, recorded as any fire (history, deck, gaps,
	 * counts) and as a recheck that fired. The next recheck is held back by the gap after a fire; with nothing held or
	 * parked a recheck fires nothing.
	 */
	@GameTest
	public void aRecheckFiresAHeldBlindCardOnlyWhileBlind(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorBrain brain = new DirectorBrain(rules, List.of(BLIND_STEP, card("plain_a", Tier.AMBIENT, Stage.ALONE, false)), 50);
		DirectorBrain.Clock clock = playing(rules);
		Solo solo = holding(rules, BLIND_STEP, clock);
		solo.pacing.unfitLooks.put(Tier.AMBIENT, 7L);
		boolean[] blind = {false};
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		env.fits = card -> card.has(CardTag.BLIND) && blind[0];
		Watch watch = new Watch();
		RandomSource random = RandomSource.create(5);

		String outcome = brain.recheck(solo.world, solo.pacing, clock.plus(30), env, random, watch);
		helper.assertTrue(env.fired.isEmpty() && BLIND_STEP.id().equals(solo.pacing.held(Tier.AMBIENT)) && solo.pacing.unfitLooks.get(Tier.AMBIENT) == 7L,
				"not blind, the recheck changed the hold or fired: " + outcome + ", looks " + solo.pacing.unfitLooks);
		helper.assertTrue(outcome.contains("does not fit") && solo.pacing.rechecks() == 1 && solo.pacing.recheckFires() == 0, "outcome " + outcome);

		blind[0] = true;
		DirectorBrain.Clock now = clock.plus(50);
		outcome = brain.recheck(solo.world, solo.pacing, now, env, random, watch);
		helper.assertTrue(env.fired.equals(List.of(BLIND_STEP.id())) && solo.pacing.held(Tier.AMBIENT) == null, "blind, it did not fire: " + outcome);
		helper.assertTrue(solo.pacing.cycleFired(Tier.AMBIENT).contains(BLIND_STEP.id()) && solo.pacing.lastFireAny == now.playTicks()
				&& solo.pacing.totalFires == 1 && solo.pacing.rechecks() == 2 && solo.pacing.recheckFires() == 1
				&& solo.pacing.history().getLast().cardId().equals(BLIND_STEP.id()), "the fire was not recorded as one: " + solo.pacing.history());
		helper.assertTrue(watch.rechecked.getLast().startsWith(BLIND_STEP.id() + ": fired"), "the recorder: " + watch.rechecked);

		outcome = brain.recheck(solo.world, solo.pacing, now.plus(20), env, random, watch);
		helper.assertTrue(env.fired.size() == 1 && outcome.equals("waiting: just fired"), "right after a fire: " + outcome);
		Solo empty = DirectorTestSupport.pinned(rules);
		empty.startAt(clock);
		env.fits = card -> true;
		outcome = brain.recheck(empty.world, empty.pacing, clock, env, random, watch);
		helper.assertTrue(env.fired.size() == 1 && outcome.equals("nothing held or parked"), "nothing held: " + outcome);
		helper.assertTrue(watch.drew.isEmpty(), "a recheck drew: " + watch.drew);
		helper.succeed();
	}

	/**
	 * D-077: BLIND cards first. A held minor that fits waits while a held BLIND ambient fires (whatever the tiers' order),
	 * and a parked BLIND card that fits goes before a held card that is not BLIND; each parked card fired is released.
	 */
	@GameTest
	public void aRecheckLooksAtBlindCardsFirst(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minAnyGap = 0;
		CardInfo plainMinor = card("plain_minor", Tier.MINOR, Stage.TRACES, false, CardTag.SCAR);
		DirectorBrain brain = new DirectorBrain(rules, List.of(BLIND_STEP, BLIND_DOOR, plainMinor), 50);
		DirectorBrain.Clock clock = playing(rules);
		Solo solo = holding(rules, plainMinor, clock);
		solo.pacing.held.put(Tier.AMBIENT, BLIND_STEP.id());
		solo.pacing.heldSince.put(Tier.AMBIENT, clock.playTicks());
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		RandomSource random = RandomSource.create(6);
		brain.recheck(solo.world, solo.pacing, clock, env, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(env.fired.equals(List.of(BLIND_STEP.id())) && plainMinor.id().equals(solo.pacing.held(Tier.MINOR)),
				"the held BLIND ambient did not go first: " + env.fired);

		Solo parked = holding(rules, plainMinor, clock);
		parked.pacing.parkedUntil.put(BLIND_DOOR.id(), clock.playTicks() + rules.unfitPark);
		ScriptEnv second = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		String outcome = brain.recheck(parked.world, parked.pacing, clock, second, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(second.fired.equals(List.of(BLIND_DOOR.id())) && parked.pacing.parkedUntil(BLIND_DOOR.id()) == -1
				&& plainMinor.id().equals(parked.pacing.held(Tier.MINOR)) && outcome.contains("released"), "the parked BLIND card: " + outcome);
		helper.succeed();
	}

	/**
	 * D-077: a recheck passes every gate a director tick does. A held BLIND card whose moment fits fires at a recheck with
	 * every gate open (each case's control), and fires nothing under each gate: no session, the silence, the join grace,
	 * the quiet, tension at the threshold (the quiet it calls for starts first), an empty session, the gap after any fire,
	 * the first hour's cap, the minor gap, the Alone cap, the stage, the major gap, the first in-game day, a sighting
	 * already today, a friend's own gates (their quiet), a card a friend already had, and a PERSONAL card beside a
	 * friend. Beside a friend whose gates are open, a card that is not PERSONAL fires and is felt by them.
	 */
	@GameTest
	public void aRecheckObeysEveryGate(GameTestHelper helper) {
		CardInfo shared = card("blind_shared", Tier.AMBIENT, Stage.ALONE, false, CardTag.SOUND, CardTag.BLIND);
		CardInfo major = card("blind_major", Tier.MAJOR, Stage.PROXIMITY, false, CardTag.SCAR, CardTag.BLIND);
		CardInfo sighting = card("blind_sighting", Tier.MINOR, Stage.TRACES, false, CardTag.SIGHTING, CardTag.BLIND);
		record Gate(String name, CardInfo card, Stage stage, String expect, Consumer<Case> on) {
		}
		List<Gate> gates = List.of(
				new Gate("no session", BLIND_STEP, Stage.PROXIMITY, "no session", g -> g.solo.pacing.sessionStart = -1),
				new Gate("silence", BLIND_STEP, Stage.PROXIMITY, "silenced", g -> g.rules.silenceUntilDay = 99),
				new Gate("join grace", BLIND_STEP, Stage.PROXIMITY, "join grace", g -> g.solo.pacing.sessionStart = g.clock.playTicks() - 20),
				new Gate("quiet", BLIND_STEP, Stage.PROXIMITY, "quiet until", g -> g.solo.pacing.quietUntilDayTicks = g.clock.dayTicks() + DirectorBrain.DAY_TICKS),
				new Gate("tension", BLIND_STEP, Stage.PROXIMITY, "quiet until", g -> g.env.tension = g.rules.tensionThreshold),
				new Gate("empty session", BLIND_STEP, Stage.PROXIMITY, "empty session", g -> {
					g.solo.pacing.sessionEmpty = true;
					g.solo.pacing.sessionEmptyUntil = g.clock.playTicks() + g.rules.hourTicks;
				}),
				new Gate("gap after any fire", BLIND_STEP, Stage.PROXIMITY, "just fired", g -> g.solo.pacing.lastFireAny = g.clock.playTicks() - 20),
				new Gate("first hour", BLIND_STEP, Stage.PROXIMITY, "first hour full", g -> {
					g.clock = new DirectorBrain.Clock(g.rules.hourTicks / 2, 2 * DirectorBrain.DAY_TICKS);
					g.solo.pacing.firstHourFires = g.rules.firstHourMaxFires;
				}),
				new Gate("minor gap", BLIND_DOOR, Stage.PROXIMITY, "minor gap",
						g -> g.solo.pacing.lastFireTier.put(Tier.MINOR, g.clock.playTicks() - g.rules.minorGap / 2)),
				new Gate("Alone cap", BLIND_STEP, Stage.ALONE, "Alone ambient cap", g -> g.solo.pacing.aloneAmbientCount = g.rules.aloneMaxAmbient),
				new Gate("stage", BLIND_DOOR, Stage.PROXIMITY, "stage", g -> g.env.world.stage = Stage.ALONE),
				new Gate("major gap", major, Stage.PROXIMITY, "major gap", g -> g.solo.pacing.lastMajorOrSignature = g.clock.playTicks() - g.rules.majorGap / 2),
				new Gate("first day", major, Stage.PROXIMITY, "first day", g -> g.solo.pacing.firstDayTicks = g.clock.dayTicks()),
				new Gate("sighting today", sighting, Stage.PROXIMITY, "sighting already today", g -> {
					g.solo.pacing.sightingDay = g.clock.day();
					g.solo.pacing.sightingsThatDay = g.rules.sightingsPerDayMax;
				}),
				new Gate("a friend's quiet", shared, Stage.PROXIMITY, "someone close by: quiet", g -> {
					g.friend(true);
					g.ben.quietUntilDayTicks = g.clock.dayTicks() + DirectorBrain.DAY_TICKS;
				}),
				new Gate("a friend had it", shared, Stage.PROXIMITY, "someone close by: already had it", g -> {
					g.friend(true);
					g.ben.cycleFired.get(Tier.AMBIENT).add(shared.id());
				}),
				new Gate("PERSONAL beside a friend", BLIND_STEP, Stage.PROXIMITY, "a card for one player alone", g -> g.friend(true)));
		for (Gate gate : gates) {
			Case open = new Case(gate.card(), gate.stage());
			if (gate.name().startsWith("a friend")) {
				open.friend(true);
			}
			String fired = open.recheck();
			helper.assertTrue(open.env.fired.equals(List.of(gate.card().id())), gate.name() + ", every gate open: " + fired);
			if (open.ben != null) {
				helper.assertTrue(open.ben.feltFires == 1 && open.ben.cycleFired(Tier.AMBIENT).contains(shared.id()), gate.name() + ": the friend did not feel it");
			}

			Case shut = new Case(gate.card(), gate.stage());
			gate.on().accept(shut);
			String outcome = shut.recheck();
			helper.assertTrue(shut.env.fired.isEmpty() && gate.card().id().equals(shut.solo.pacing.held(gate.card().tier())),
					gate.name() + ": fired or let go of the hold: " + outcome);
			helper.assertTrue(outcome.contains(gate.expect()), gate.name() + ": " + outcome);
			if (gate.name().equals("tension")) {
				helper.assertTrue(shut.solo.pacing.quietCount == 1, "tension at the threshold started no quiet: " + outcome);
			}
		}
		helper.succeed();
	}

	/** One scripted recheck: a player holding one card that fits, every gate open until a case shuts one. */
	private static final class Case {
		final DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		final CardInfo card;
		final DirectorBrain brain;
		final Solo solo;
		final PlayerEnv env;
		DirectorBrain.Clock clock;
		@Nullable PacingMemory ben;

		Case(CardInfo card, Stage stage) {
			this.card = card;
			this.brain = new DirectorBrain(rules, List.of(card), 50);
			this.clock = playing(rules);
			this.solo = holding(rules, card, clock);
			this.env = new PlayerEnv(new SharedStage(stage));
		}

		/** A friend close by (Ben, in a session long past its grace). */
		void friend(boolean close) {
			ben = new PacingMemory();
			ben.sessionStart = 0;
			env.sharers = close ? List.of(new DirectorBrain.Sharer(ben, clock, new PlayerEnv(env.world), DirectorBrain.Recorder.NONE)) : List.of();
		}

		String recheck() {
			return brain.recheck(solo.world, solo.pacing, clock, env, RandomSource.create(9), DirectorBrain.Recorder.NONE);
		}
	}

	/**
	 * D-077: a recheck never rolls a tier or draws. With nothing held or parked, every card fitting, every gate open,
	 * a major slot due and rates that would draw at once, fifty rechecks fire nothing, hold nothing, draw nothing and
	 * leave the decks and the major slot as they were; the next director tick draws and fires.
	 */
	@GameTest
	public void aRecheckNeverDraws(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1_000_000;
		rules.minorsPerHourMin = 1_000_000;
		rules.minorsPerHourMax = 1_000_000;
		DirectorBrain brain = new DirectorBrain(rules, List.of(BLIND_STEP, BLIND_DOOR, card("plain_a", Tier.AMBIENT, Stage.ALONE, false),
				card("plain_minor", Tier.MINOR, Stage.TRACES, false), card("plain_major", Tier.MAJOR, Stage.PROXIMITY, false)), 50);
		DirectorBrain.Clock clock = playing(rules);
		Solo solo = DirectorTestSupport.pinned(rules);
		solo.startAt(clock);
		solo.pacing.nextMajorDue = clock.playTicks();
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		Watch watch = new Watch();
		RandomSource random = RandomSource.create(11);
		for (int i = 0; i < 50; i++) {
			brain.recheck(solo.world, solo.pacing, clock.plus(30L + 20L * i), env, random, watch);
		}
		helper.assertTrue(env.fired.isEmpty() && env.fireCalls == 0 && watch.drew.isEmpty(), "a recheck fired or drew: " + env.fired + ", " + watch.drew);
		for (Tier tier : Tier.values()) {
			helper.assertTrue(solo.pacing.held(tier) == null && solo.pacing.cycleFired(tier).isEmpty(), tier + " changed: held " + solo.pacing.held(tier));
		}
		helper.assertTrue(solo.pacing.nextMajorDue() == clock.playTicks() && solo.pacing.rechecks() == 50
				&& watch.rechecked.stream().allMatch(line -> line.equals("-: nothing held or parked")), "rechecks: " + watch.rechecked.getFirst());
		solo.step(brain, clock.plus(rules.tickInterval), env, random, watch);
		helper.assertTrue(env.fired.size() == 1 && !watch.drew.isEmpty(), "the director tick did not draw: " + watch.drew);
		helper.succeed();
	}

	/**
	 * D-077: a parked card is looked at too. One that is not BLIND and fits is released and fires (its park, or a strike
	 * whose park is over, goes); one that does not fit stays parked and its look is not counted; a parked major waits for
	 * a due slot (the major schedule), and fires once one is due.
	 */
	@GameTest
	public void aParkedCardThatFitsIsReleasedAndFired(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		CardInfo echo = card("echo_like", Tier.AMBIENT, Stage.ALONE, true, CardTag.SOUND);
		CardInfo major = card("major_parked", Tier.MAJOR, Stage.PROXIMITY, false, CardTag.SCAR);
		DirectorBrain brain = new DirectorBrain(rules, List.of(echo, major), 50);
		DirectorBrain.Clock clock = playing(rules);
		RandomSource random = RandomSource.create(12);

		for (long until : new long[] {clock.playTicks() + rules.unfitPark, clock.playTicks() - 1}) {
			Solo solo = DirectorTestSupport.pinned(rules);
			solo.startAt(clock);
			solo.pacing.parkedUntil.put(echo.id(), until);
			ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
			env.fits = card -> false;
			String outcome = brain.recheck(solo.world, solo.pacing, clock, env, random, DirectorBrain.Recorder.NONE);
			helper.assertTrue(env.fired.isEmpty() && solo.pacing.parkedUntil(echo.id()) == until && solo.pacing.unfitLooks.isEmpty()
					&& outcome.contains("parked: the moment does not fit"), "unfit, parked until " + until + ": " + outcome);
			env.fits = card -> true;
			outcome = brain.recheck(solo.world, solo.pacing, clock, env, random, DirectorBrain.Recorder.NONE);
			helper.assertTrue(env.fired.equals(List.of(echo.id())) && solo.pacing.parkedUntil(echo.id()) == -1 && solo.pacing.held(Tier.AMBIENT) == null,
					"fitting, parked until " + until + ": " + outcome);
		}

		Solo solo = DirectorTestSupport.pinned(rules);
		solo.startAt(clock);
		solo.pacing.parkedUntil.put(major.id(), clock.playTicks() + rules.unfitPark);
		solo.pacing.nextMajorDue = clock.playTicks() + rules.hourTicks;
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		String outcome = brain.recheck(solo.world, solo.pacing, clock, env, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(env.fired.isEmpty() && outcome.contains("no major slot due") && solo.pacing.parkedUntil(major.id()) > 0, "a parked major: " + outcome);
		solo.pacing.nextMajorDue = clock.playTicks();
		outcome = brain.recheck(solo.world, solo.pacing, clock, env, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(env.fired.equals(List.of(major.id())) && solo.pacing.nextMajorDue() > clock.playTicks(), "a parked major, slot due: " + outcome);
		helper.succeed();
	}

	// --- the BLIND hold ---

	/**
	 * D-077 (the orchestrator's decision after W-DIR2's first report): a held BLIND card whose moment does not come keeps
	 * its hold past the five-minute unfit give-back and goes back after {@code blindHold} (15 minutes) of looks, parked
	 * like any unfit card, with the roll that drew it handed on (another ambient fires in that tick): a player who opens
	 * no menu never freezes a tier. A recheck at which its moment fit but found no spot starts the count again. A recheck
	 * while blind ten minutes into a hold fires it.
	 */
	@GameTest
	public void aBlindCardHoldsFifteenMinutesThenIsParked(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1_000_000;
		rules.minAnyGap = 0;
		helper.assertTrue(rules.blindHold == rules.hourTicks / 4 && rules.heldUnfitGiveBack < rules.blindHold && rules.blindHold < rules.holdGiveUp
				&& rules.unfitPark > 0, "the holds " + rules.heldUnfitGiveBack + ", " + rules.blindHold + ", " + rules.holdGiveUp);
		DirectorBrain brain = new DirectorBrain(rules, List.of(BLIND_STEP, card("plain_a", Tier.AMBIENT, Stage.ALONE, false),
				card("plain_b", Tier.AMBIENT, Stage.ALONE, false)), 50);
		helper.assertTrue(brain.unfitHold(BLIND_STEP) == rules.blindHold && brain.unfitHold(BLIND_DOOR) == rules.blindHold
				&& brain.unfitHold(card("blind_major", Tier.MAJOR, Stage.PROXIMITY, false, CardTag.BLIND)) == rules.blindHold
				&& brain.unfitHold(card("a", Tier.AMBIENT, Stage.ALONE, false)) == rules.heldUnfitGiveBack
				&& brain.unfitHold(card("m", Tier.MINOR, Stage.TRACES, false)) == rules.heldUnfitGiveBack
				&& brain.unfitHold(card("j", Tier.MAJOR, Stage.PROXIMITY, false)) == 0, "unfitHold");
		DirectorBrain.Clock clock = playing(rules);
		long heldAt = clock.playTicks();
		Solo solo = holding(rules, BLIND_STEP, clock);
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		env.fits = card -> !card.has(CardTag.BLIND);
		Watch watch = new Watch();
		RandomSource random = RandomSource.create(13);
		while (watch.gaveUp.isEmpty() && clock.playTicks() < heldAt + rules.holdGiveUp) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			solo.step(brain, clock, env, random, watch);
			if (clock.playTicks() - heldAt < rules.blindHold) {
				helper.assertTrue(BLIND_STEP.id().equals(solo.pacing.held(Tier.AMBIENT)) && env.fired.isEmpty(),
						"the BLIND hold went back after " + (clock.playTicks() - heldAt) + " ticks: " + watch.gaveUp);
			}
		}
		long back = clock.playTicks();
		helper.assertTrue(watch.gaveUp.equals(List.of(BLIND_STEP.id() + ": " + DirectorBrain.UNFIT + DirectorBrain.PARKED)) && back - heldAt >= rules.blindHold
				&& back - heldAt < rules.blindHold + rules.tickInterval, "the BLIND hold: " + watch.gaveUp + " after " + (back - heldAt));
		helper.assertTrue(solo.pacing.parkedUntil(BLIND_STEP.id()) == back + rules.unfitPark && env.fired.size() == 1 && env.fired.getFirst().startsWith("plain_"),
				"not parked, or its roll was not handed on: parked until " + solo.pacing.parkedUntil(BLIND_STEP.id()) + ", fired " + env.fired);

		// Its moment comes at a recheck ten minutes in, but no spot is found: the fifteen minutes count again from there.
		DirectorBrain.Clock start = playing(rules);
		Solo spotless = holding(rules, BLIND_STEP, start);
		boolean[] blind = {false};
		ScriptEnv noSpot = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		noSpot.fits = card -> !card.has(CardTag.BLIND) || blind[0];
		noSpot.result = card -> card.has(CardTag.BLIND) ? FireResult.NO_SPOT : FireResult.FIRED;
		Watch spotWatch = new Watch();
		DirectorBrain.Clock at = start;
		while (at.playTicks() - start.playTicks() < 10 * rules.hourTicks / 60) {
			at = at.plus(rules.tickInterval);
			noSpot.tension = 0;
			spotless.step(brain, at, noSpot, random, spotWatch);
		}
		long lastTick = at.playTicks();
		blind[0] = true;
		String outcome = brain.recheck(spotless.world, spotless.pacing, at.plus(30), noSpot, random, spotWatch);
		blind[0] = false;
		helper.assertTrue(outcome.contains("NO_SPOT") && BLIND_STEP.id().equals(spotless.pacing.held(Tier.AMBIENT)) && !spotless.pacing.unfitLooks.containsKey(Tier.AMBIENT),
				"a fitting recheck without a spot: " + outcome + ", looks " + spotless.pacing.unfitLooks);
		while (spotWatch.gaveUp.isEmpty() && at.playTicks() < start.playTicks() + rules.holdGiveUp) {
			at = at.plus(rules.tickInterval);
			noSpot.tension = 0;
			spotless.step(brain, at, noSpot, random, spotWatch);
		}
		helper.assertTrue(spotWatch.gaveUp.equals(List.of(BLIND_STEP.id() + ": " + DirectorBrain.UNFIT + DirectorBrain.PARKED))
				&& at.playTicks() - lastTick >= rules.blindHold && at.playTicks() - lastTick < rules.blindHold + rules.tickInterval,
				"after the fitting recheck the hold went back " + (at.playTicks() - lastTick) + " ticks later: " + spotWatch.gaveUp);

		// Ten minutes into a hold, a menu opens: the recheck fires it.
		DirectorBrain.Clock from = playing(rules);
		Solo caught = holding(rules, BLIND_STEP, from);
		ScriptEnv later = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		later.fits = card -> false;
		DirectorBrain.Clock now = from;
		while (now.playTicks() - from.playTicks() < 10 * rules.hourTicks / 60) {
			now = now.plus(rules.tickInterval);
			later.tension = 0;
			caught.step(brain, now, later, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(BLIND_STEP.id().equals(caught.pacing.held(Tier.AMBIENT)), "the hold went back before 10 minutes");
		later.fits = card -> true;
		brain.recheck(caught.world, caught.pacing, now.plus(30), later, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(later.fired.equals(List.of(BLIND_STEP.id())), "10 minutes in, the recheck did not fire it: " + later.fired);
		helper.succeed();
	}

	/**
	 * INT-2: a roll goes through every struck card it meets. Three ambients whose moment never comes and a BLIND card
	 * struck between menus (each went back before, their parks over) are the only undrawn cards of the deck: one due
	 * roll draws each, sends each back parked at its one look, and goes on to the deck's refill, so the ambient fires in
	 * that same tick. With the hand-on capped at two a tick (v1.2) the third struck card ended the roll, at the end of
	 * nearly every cycle: about a tenth of Proximity's ambients for a deck with that many cards that never fit.
	 */
	@GameTest
	public void aRollGoesThroughEveryStruckCard(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1_000_000;
		rules.minAnyGap = 0;
		List<String> struck = List.of("never_a", "never_b", "never_c", BLIND_STEP.id());
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("never_a", Tier.AMBIENT, Stage.ALONE, false), card("never_b", Tier.AMBIENT, Stage.ALONE, false),
				card("never_c", Tier.AMBIENT, Stage.ALONE, false), BLIND_STEP, card("plain_a", Tier.AMBIENT, Stage.ALONE, false),
				card("plain_b", Tier.AMBIENT, Stage.ALONE, false)), 50);
		DirectorBrain.Clock clock = playing(rules);
		Solo solo = DirectorTestSupport.pinned(rules);
		solo.startAt(clock);
		// This cycle fired both plain cards, plain_a last; the struck cards' parks ended just now.
		solo.pacing.cycleFired.get(Tier.AMBIENT).addAll(List.of("plain_b", "plain_a"));
		solo.pacing.lastFired.put(Tier.AMBIENT, "plain_a");
		struck.forEach(id -> solo.pacing.parkedUntil.put(id, clock.playTicks()));
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		env.fits = card -> card.id().startsWith("plain_");
		Watch watch = new Watch();
		DirectorBrain.Clock now = clock.plus(rules.tickInterval);
		String outcome = solo.step(brain, now, env, RandomSource.create(17), watch);
		helper.assertTrue(env.fired.equals(List.of("plain_b")), "the roll did not reach the refill: " + outcome);
		for (String id : struck) {
			helper.assertTrue(solo.pacing.parkedUntil(id) == now.playTicks() + rules.unfitPark && watch.gaveUp.contains(id + ": " + DirectorBrain.UNFIT + DirectorBrain.PARKED),
					id + " was not looked at and parked again: until " + solo.pacing.parkedUntil(id) + ", " + watch.gaveUp);
		}
		helper.succeed();
	}

	// --- dry runs ---

	/**
	 * D-077 in the dry runs: without BLIND cards the blind moments change nothing. The registered deck (BLIND cards left
	 * out) and the synthetic deck play event for event the same with {@code simBlindPerHour} 20 and 0, every tempo, while
	 * the runs do have their blind moments.
	 */
	@GameTest(maxTicks = 2000)
	public void soloDryRunsWithoutBlindCardsAreUnchanged(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck().stream().filter(card -> !card.has(CardTag.BLIND)).toList();
		DirectorConfig none = new DirectorConfig();
		none.simBlindPerHour = 0;
		helper.assertTrue(new DirectorConfig().simBlindPerHour == 20, "simBlindPerHour " + new DirectorConfig().simBlindPerHour);
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> with = DirectorTestSupport.registryRuns(tempo, new DirectorConfig(), deck, null);
			List<DirectorSim.Result> without = DirectorTestSupport.registryRuns(tempo, none, deck, null);
			long moments = 0;
			for (int i = 0; i < with.size(); i++) {
				helper.assertTrue(with.get(i).events.equals(without.get(i).events), tempo + " seed " + with.get(i).params.seed + ": blind moments changed a run");
				helper.assertTrue(without.get(i).rechecks == 0 && with.get(i).recheckFires().isEmpty(), tempo + ": rechecks without blind moments");
				moments += with.get(i).rechecks;
			}
			double perHour = moments / (with.size() * DirectorTestSupport.HOURS);
			helper.assertTrue(perHour > 15 && perHour < 22, tempo + ": " + perHour + " blind moments an hour");
		}
		for (DirectorTestSupport.Playthrough p : DirectorTestSupport.playthroughs()) {
			DirectorSim.Params params = DirectorTestSupport.params(p.rules(), p.seed(), p.rules().attentionNeutral, DirectorTestSupport.HOURS);
			params.blindPerHour = 0;
			DirectorSim.Result without = DirectorSim.run(p.rules(), SyntheticDeck.cards(), 1000, new DirectorMemory(), Stage.ALONE, 0,
					new DirectorBrain.Clock(0, 0), params);
			helper.assertTrue(p.result().events.equals(without.events) && p.result().rechecks > 0, DirectorTestSupport.where(p) + "the synthetic deck changed");
		}
		helper.succeed();
	}

	/**
	 * D-077 in the dry runs, measured: the registered deck plus stand-ins for dig's three blind cards (an Alone ambient and
	 * two Traces minors, PERSONAL and BLIND). Their moments fit only at a blind moment's recheck, so every fire of theirs
	 * is a recheck's; every limit holds over the 20 h, D-070's first hour holds, and no 4b rate falls more than 15% below
	 * the plain deck's (the rates are logged). Together, a PERSONAL BLIND card never comes.
	 */
	@GameTest(maxTicks = 3000)
	public void blindCardsFireOnlyAtRechecksInTheDryRuns(GameTestHelper helper) {
		List<CardInfo> plainDeck = plainDeck();
		List<CardInfo> deck = withStandIns(plainDeck);
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> plainRuns = DirectorTestSupport.registryRuns(tempo, new DirectorConfig(), plainDeck, null);
			List<DirectorSim.Result> runs = DirectorTestSupport.registryRuns(tempo, new DirectorConfig(), deck, null);
			DirectorTestSupport.FourB plain = DirectorTestSupport.FourB.of(plainRuns, plainDeck);
			DirectorTestSupport.FourB with = DirectorTestSupport.FourB.of(runs, deck);
			DirectorTestSupport.FirstHour first = DirectorTestSupport.FirstHour.of(runs);
			long blindFires = 0;
			long recheckFires = 0;
			for (DirectorSim.Result run : runs) {
				String seed = tempo + " seed " + run.params.seed + ": ";
				for (DirectorSim.Event e : run.fires()) {
					boolean standIn = BLIND_STAND_INS.stream().anyMatch(card -> card.id().equals(e.cardId()));
					helper.assertTrue(!standIn || DirectorSim.RECHECK.equals(e.note()), seed + e.cardId() + " fired at a director tick");
					blindFires += standIn ? 1 : 0;
				}
				recheckFires += run.recheckFires().size();
				helper.assertTrue(run.violations.isEmpty(), seed + run.violations);
				List<String> hard = DirectorTestSupport.feltHardLimits(run, deck);
				helper.assertTrue(hard.isEmpty(), seed + hard);
			}
			double hours = runs.size() * DirectorTestSupport.HOURS;
			A1016_02.LOGGER.info("[a1016] director blind cards {}: plain {}", tempo, plain.summary());
			A1016_02.LOGGER.info("[a1016] director blind cards {}: with BLIND stand-ins {}; {}; {} BLIND fires ({} /h), {} from rechecks", tempo, with.summary(),
					first.summary(), blindFires, String.format(Locale.ROOT, "%.2f", blindFires / hours), recheckFires);
			helper.assertTrue(blindFires > runs.size() && blindFires == recheckFires, tempo + ": " + blindFires + " BLIND fires, " + recheckFires + " from rechecks");
			// W-DIR2 measured 9% fewer Alone ambients and 7% or less elsewhere (a BLIND card waits for a menu); a guard, not a target.
			helper.assertTrue(with.aloneAmbientsPerHour() >= 0.85 * plain.aloneAmbientsPerHour() && with.tracesAmbientsPerHour() >= 0.85 * plain.tracesAmbientsPerHour()
					&& with.tracesMinorsPerHour() >= 0.85 * plain.tracesMinorsPerHour() && with.proximityAmbientsPerHour() >= 0.85 * plain.proximityAmbientsPerHour()
					&& with.proximityMinorsPerHour() >= 0.85 * plain.proximityMinorsPerHour(), tempo + ": a rate fell more than 15%: " + with.summary() + " against "
							+ plain.summary());
			helper.assertTrue(first.by30() * 4 >= first.runs() * 3 && first.by45() * 10 >= first.runs() * 9 && first.twoOrMore() * 10 >= first.runs() * 7
					&& first.most() <= 5, tempo + ", the first hour: " + first.summary());
		}

		List<DirectorSim.Seat> seats = List.of(new DirectorSim.Seat(new UUID(0xA1L, 13L), 0), new DirectorSim.Seat(BEN, 0));
		for (long seed : DirectorApi.tuningSeeds().subList(0, 6)) {
			DirectorConfig config = new DirectorConfig();
			DirectorRules rules = DirectorRules.from(new com.forzacode.a1016_02.core.Pacing(), config,
					com.forzacode.a1016_02.debug.Playthrough.profile(seed, Tempo.SLOW_BURN));
			DirectorSim.Params params = DirectorSim.Params.from(config, rules);
			params.hours = DirectorTestSupport.HOURS;
			params.seed = seed;
			params.attention = config.attentionNeutral;
			DirectorSim.Group together = DirectorSim.runGroup(rules, deck, 1000, seats, DirectorSim.Sharing.TOGETHER, params);
			helper.assertTrue(together.allViolations().isEmpty(), "together: " + together.allViolations());
			for (DirectorSim.Result player : together.players) {
				helper.assertTrue(player.felt().stream().noneMatch(e -> BLIND_STAND_INS.stream().anyMatch(c -> c.id().equals(e.cardId()))),
						"seed " + seed + ", together, a PERSONAL BLIND card came");
				helper.assertTrue(player.rechecks > 0, "seed " + seed + ": no blind moments in a group run");
			}
		}
		helper.succeed();
	}

	/**
	 * D-077 (the orchestrator's decision): with no blind moments at all, as for a player who never opens a menu or a
	 * client without the blind signal, the stand-ins registered never freeze a tier: each held one goes back after its 15
	 * minutes, parked, with its roll handed on, so Proximity's ambients stay within 10% of the plain deck's, every tempo.
	 * No BLIND card fires and every limit holds; the other 4b rates are logged.
	 */
	@GameTest(maxTicks = 3000)
	public void noBlindMomentsNeverFreezeATier(GameTestHelper helper) {
		List<CardInfo> plainDeck = plainDeck();
		List<CardInfo> deck = withStandIns(plainDeck);
		DirectorConfig none = new DirectorConfig();
		none.simBlindPerHour = 0;
		for (Tempo tempo : Tempo.values()) {
			DirectorTestSupport.FourB plain = DirectorTestSupport.FourB.of(DirectorTestSupport.registryRuns(tempo, new DirectorConfig(), plainDeck, null), plainDeck);
			List<DirectorSim.Result> runs = DirectorTestSupport.registryRuns(tempo, none, deck, null);
			DirectorTestSupport.FourB menuless = DirectorTestSupport.FourB.of(runs, deck);
			long parks = 0;
			for (DirectorSim.Result run : runs) {
				String seed = tempo + " seed " + run.params.seed + ", no blind moments: ";
				helper.assertTrue(run.fires().stream().noneMatch(e -> BLIND_STAND_INS.stream().anyMatch(c -> c.id().equals(e.cardId()))), seed + "a BLIND card fired");
				helper.assertTrue(run.violations.isEmpty(), seed + run.violations);
				List<String> hard = DirectorTestSupport.feltHardLimits(run, deck);
				helper.assertTrue(hard.isEmpty(), seed + hard);
				parks += run.events.stream().filter(e -> e.kind() == DirectorSim.Kind.GIVE_UP && e.note().endsWith(DirectorBrain.PARKED)
						&& BLIND_STAND_INS.stream().anyMatch(c -> c.id().equals(e.cardId()))).count();
			}
			A1016_02.LOGGER.info("[a1016] director blind cards {}: no blind moments {} ({} BLIND parks) against plain {}", tempo, menuless.summary(), parks,
					plain.summary());
			helper.assertTrue(parks >= runs.size(), tempo + ": only " + parks + " BLIND parks in " + runs.size() + " runs");
			helper.assertTrue(menuless.proximityAmbientsPerHour() >= 0.9 * plain.proximityAmbientsPerHour(), tempo + ": Proximity ambients "
					+ menuless.proximityAmbientsPerHour() + " against plain " + plain.proximityAmbientsPerHour());
		}
		helper.succeed();
	}

	/** The registered deck the playthrough plays, without any BLIND card (dig's, once registered). */
	private static List<CardInfo> plainDeck() {
		return DirectorTestSupport.registryDeck().stream().filter(card -> !card.has(CardTag.BLIND)).toList();
	}

	/** This deck with the stand-ins for dig's three blind cards. */
	private static List<CardInfo> withStandIns(List<CardInfo> plainDeck) {
		List<CardInfo> deck = new ArrayList<>(plainDeck);
		deck.addAll(BLIND_STAND_INS);
		return deck;
	}

	// --- config, commands, the live director ---

	/**
	 * D-075 for v1.3: the director section's step 2 to 3 only adds {@code blindHoldMinutes} (15) and
	 * {@code simBlindPerHour} (20); a version 2 section keeps every value it has, hand-changed or not, and a missing section
	 * is v1.3's defaults.
	 */
	@GameTest
	public void theDirectorSectionStepToVersion3OnlyAddsTheBlindMoments(GameTestHelper helper) {
		Gson gson = new Gson();
		DirectorConfig v2 = new DirectorConfig();
		v2.version = 2;
		v2.unfitParkMinutes = 12; // the player's
		v2.aloneAmbientPerHour = 0.3; // the player's, though v1.1's default
		v2.simFitChance = 0.5; // the player's
		JsonObject section = gson.toJsonTree(v2).getAsJsonObject();
		section.remove("blindHoldMinutes");
		section.remove("simBlindPerHour");
		JsonObject sections = new JsonObject();
		sections.add("director", section);
		DirectorConfig read = ModConfig.readSection(sections, "director", DirectorConfig.class, DirectorConfig::new);
		v2.version = DirectorConfig.VERSION;
		helper.assertTrue(DirectorConfig.VERSION == 3 && read.version == 3 && read.blindHoldMinutes == 15 && read.simBlindPerHour == 20
				&& gson.toJson(read).equals(gson.toJson(v2)), "a version 2 section upgraded to " + gson.toJson(read));
		DirectorConfig mine = new DirectorConfig();
		mine.version = DirectorConfig.VERSION;
		mine.simBlindPerHour = 4;
		JsonObject again = new JsonObject();
		again.add("director", gson.toJsonTree(mine));
		helper.assertTrue(ModConfig.readSection(again, "director", DirectorConfig.class, DirectorConfig::new).simBlindPerHour == 4, "the player's 4 was lost");
		DirectorConfig fresh = ModConfig.readSection(new JsonObject(), "director", DirectorConfig.class, DirectorConfig::new);
		helper.assertTrue(fresh.version == 3 && fresh.blindHoldMinutes == 15 && fresh.simBlindPerHour == 20, "a missing section: " + gson.toJson(fresh));
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		helper.assertTrue(DirectorSim.Params.from(mine, rules).blindPerHour == 4 && DirectorSim.Params.from(fresh, rules).blindPerHour == 20, "Params.from");
		helper.succeed();
	}

	/**
	 * {@code /a1016 director}'s recheck line (D-077): blind now or not, the counts and the last recheck, in
	 * {@code Locale.ROOT}; the counts are saved with the pacing. The live director's recheck for someone who does not take
	 * part does nothing: no pacing, no last recheck.
	 */
	@GameTest
	public void theDirectorCommandShowsTheRechecks(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		PacingMemory m = new PacingMemory();
		m.rechecks = 14;
		m.recheckFires = 2;
		PacingMemory loaded = PacingMemory.fromTag(m.toTag());
		helper.assertTrue(loaded.rechecks() == 14 && loaded.recheckFires() == 2 && PacingMemory.fromTag(new PacingMemory().toTag()).rechecks() == 0,
				"the counts were not saved: " + loaded.rechecks() + ", " + loaded.recheckFires());
		Locale before = Locale.getDefault();
		String blind;
		String calm;
		try {
			Locale.setDefault(Locale.forLanguageTag("es-ES"));
			long now = 3 * rules.hourTicks;
			blind = DirectorDebug.recheckLine("Ana", loaded, 64,new DirectorImpl.LastRecheck(now - rules.hourTicks / 60, "fired sim_blind_step"), now, rules);
			calm = DirectorDebug.recheckLine("Ana", new PacingMemory(), 0, null, now, rules);
		} finally {
			Locale.setDefault(before);
		}
		helper.assertTrue(blind.equals("director: Ana: blind for 3.2s | rechecks 14, 2 fired | last 0h01m ago: fired sim_blind_step"), "the line: " + blind);
		helper.assertTrue(calm.equals("director: Ana: not blind | rechecks 0, 0 fired"), "the calm line: " + calm);

		MinecraftServer server = helper.getLevel().getServer();
		Stranger stranger = new Stranger(helper.getLevel(), "a1016_w13", Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)));
		DirectorImpl director = DirectorInit.director();
		director.recheck(server, stranger);
		helper.assertTrue(DirectorData.get(server).memory().find(stranger.getUUID()).isEmpty() && director.lastRecheck(stranger.getUUID()) == null,
				"a recheck for someone not taking part touched the director");
		helper.succeed();
	}

	/** A player that never joins the server. */
	private static final class Stranger extends ServerPlayer {
		Stranger(ServerLevel level, String name, Vec3 pos) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
			snapTo(pos, 0.0F, 0.0F);
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
