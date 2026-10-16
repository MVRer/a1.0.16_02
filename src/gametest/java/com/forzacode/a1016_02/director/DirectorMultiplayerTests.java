package com.forzacode.a1016_02.director;

import static com.forzacode.a1016_02.director.DirectorTestSupport.card;
import static com.forzacode.a1016_02.director.DirectorTestSupport.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.DirectorSim.Event;
import com.forzacode.a1016_02.director.DirectorSim.Kind;
import com.forzacode.a1016_02.director.DirectorTestSupport.PlayerEnv;
import com.forzacode.a1016_02.director.DirectorTestSupport.SharedStage;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Two-player director tests (MP-1, D-058 to D-062). Nobody takes part on the game test server and other tests run
 * beside these, so every test uses pure logic with its own players' UUIDs, memories and envs ({@link PlayerEnv},
 * {@link DirectorSim#runGroup}), or players of its own that never join the server. Nothing here touches the live
 * director's memory or the world's state. {@link DirectorGameTests} extends this.
 */
public class DirectorMultiplayerTests extends DirectorV13Tests {
	private static final UUID ANA = new UUID(0xA1L, 1L);
	private static final UUID BEN = new UUID(0xBEL, 2L);
	private static final List<Long> SEEDS = DirectorApi.tuningSeeds();

	/** A player that never joins the server: only a position, a level and a game mode for the checks that read them. */
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

	/** A pacing in a session that started long ago (past its join grace), first day long over. */
	private static PacingMemory playing() {
		PacingMemory m = new PacingMemory();
		m.sessionStart = 0;
		return m;
	}

	/** Ana's and Ben's clocks this tick: their own play ticks differ (Ben joined later), the world's are shared. */
	private static DirectorBrain.Clock clock(long own, long world) {
		return new DirectorBrain.Clock(own, 2 * DirectorBrain.DAY_TICKS + world, world);
	}

	// --- one world, two pacings ---

	/**
	 * Both players decide in the same tick, the tick the stage clock passes its point: the stage moves once. Both
	 * hold the world's one signature card when its moment comes for both at once: it fires once, for whoever goes
	 * first, and the other's hold goes back without firing (D-062.3).
	 */
	@GameTest
	public void oneStageChangeAndOneSignatureWhenTwoDecideInOneTick(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		CardInfo signature = card("sig_once", Tier.SIGNATURE, Stage.TRACES, false);
		DirectorBrain brain = new DirectorBrain(rules, List.of(signature), 10);
		long step = rules.tickInterval;
		long world0 = rules.hourTicks;
		WorldMemory world = new WorldMemory();
		world.rolledTempo = rules.profile.tempo().name();
		world.stageClock = world0;
		world.lastStepPlay = world0;
		world.tracesAt = world0 + 3 * step;
		world.proximityAt = Long.MAX_VALUE / 4;
		SharedStage stage = new SharedStage(Stage.ALONE);
		PacingMemory ana = playing();
		PacingMemory ben = playing();
		PlayerEnv anaEnv = new PlayerEnv(stage);
		PlayerEnv benEnv = new PlayerEnv(stage);
		anaEnv.fits = card -> false;
		benEnv.fits = card -> false;
		RandomSource random = RandomSource.create(4);
		long anaOwn = 5 * rules.hourTicks;
		long benOwn = rules.hourTicks / 2;
		for (int i = 1; i <= 6; i++) {
			long w = world0 + i * step;
			brain.step(world, ana, clock(anaOwn + i * step, w), anaEnv, random, DirectorBrain.Recorder.NONE);
			brain.step(world, ben, clock(benOwn + i * step, w), benEnv, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(stage.changes.equals(List.of(Stage.TRACES)), "the stage moved " + stage.changes + " for two players in one tick");
		helper.assertTrue("sig_once".equals(ana.held(Tier.SIGNATURE)) && "sig_once".equals(ben.held(Tier.SIGNATURE)),
				"both should hold the signature while its moment does not fit: " + ana.held(Tier.SIGNATURE) + ", " + ben.held(Tier.SIGNATURE));

		anaEnv.fits = card -> true;
		benEnv.fits = card -> true;
		long w = world0 + 7 * step;
		brain.step(world, ana, clock(anaOwn + 7 * step, w), anaEnv, random, DirectorBrain.Recorder.NONE);
		brain.step(world, ben, clock(benOwn + 7 * step, w), benEnv, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(anaEnv.fired.equals(List.of("sig_once")) && benEnv.fired.isEmpty(), "the signature fired " + anaEnv.fired + " and " + benEnv.fired);
		helper.assertTrue(world.signaturesFired().equals(Set.of("sig_once")) && ben.held(Tier.SIGNATURE) == null,
				"once per world: " + world.signaturesFired() + ", Ben still holds " + ben.held(Tier.SIGNATURE));
		helper.succeed();
	}

	/**
	 * Sessions are per player (D-062.4): Ben starting changes nothing of Ana's, his join grace and his first in-game day
	 * hold back only his own cards while they are apart (Ana's major still comes), and his leaving does not end her
	 * session.
	 */
	@GameTest
	public void eachPlayersSessionIsTheirOwn(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1000;
		rules.minAnyGap = 0;
		List<CardInfo> deck = List.of(card("a", Tier.AMBIENT, Stage.ALONE, false), card("b", Tier.AMBIENT, Stage.ALONE, false),
				card("c", Tier.AMBIENT, Stage.ALONE, false), card("maj", Tier.MAJOR, Stage.PROXIMITY, false));
		DirectorBrain brain = new DirectorBrain(rules, deck, 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		PacingMemory ana = playing();
		PlayerEnv anaEnv = new PlayerEnv(stage);
		PlayerEnv benEnv = new PlayerEnv(stage);
		RandomSource random = RandomSource.create(9);
		long step = rules.tickInterval;
		long w = 3 * rules.hourTicks;
		DirectorBrain.Clock anaClock = clock(w, w);
		brain.step(world, ana, anaClock, anaEnv, random, DirectorBrain.Recorder.NONE);
		String anaBefore = ana.toTag().toString();

		// Ben takes part for the first time: his own first day starts now.
		DirectorBrain.Clock benClock = clock(0, w);
		PacingMemory ben = new PacingMemory(benClock.dayTicks());
		brain.onJoin(world, ben, benClock, benEnv, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(ana.toTag().toString().equals(anaBefore), "Ben's join changed Ana's pacing");
		helper.assertTrue(ben.sessionStart == 0 && brain.firstDay(ben, benClock) && !brain.firstDay(ana, anaClock),
				"Ben's session and first day should start on his own join, and only his");

		ana.nextMajorDue = w;
		int graceTicks = (int) (rules.joinGrace / step) - 1;
		for (int i = 1; i <= graceTicks; i++) {
			brain.step(world, ana, clock(w + i * step, w + i * step), anaEnv, random, DirectorBrain.Recorder.NONE);
			brain.step(world, ben, clock(i * step, w + i * step), benEnv, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(!anaEnv.fired.isEmpty() && benEnv.fired.isEmpty(), "in Ben's join grace Ana fired " + anaEnv.fired + ", Ben " + benEnv.fired);
		helper.assertTrue(anaEnv.fired.contains("maj") && "first day".equals(brain.tierBlock(Tier.MAJOR, ben, clock(graceTicks * step, w), Stage.PROXIMITY)),
				"Ana's major should come while Ben is in his first day: " + anaEnv.fired);

		long anaSession = ana.sessionStart;
		brain.onLeave(ben, clock(graceTicks * step, w + graceTicks * step), DirectorBrain.Recorder.NONE);
		helper.assertTrue(ana.sessionStart == anaSession && ana.sessionStart >= 0 && ben.sessionStart < 0, "Ben's leave ended Ana's session");
		helper.succeed();
	}

	/**
	 * A card fired for Ana with Ben close by (D-060.3) counts in Ben's pacing (his minor gap, the "just fired" gap, his
	 * no-repeat cycle, his tension and the quiet it starts), but not as his own draw: his fire count stays 0. His
	 * hold on the same card goes; he cannot fire a minor himself in the same tick; his quiet starts once, not twice.
	 */
	@GameTest
	public void aFeltMomentCountsInTheirPacingNotTheirDraws(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minorsPerHourMin = rules.minorsPerHourMax = 1000;
		rules.proximityAmbientPerHour = 0;
		CardInfo minor = card("m1", Tier.MINOR, Stage.TRACES, false);
		DirectorBrain brain = new DirectorBrain(rules, List.of(minor), 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		PacingMemory ana = playing();
		PacingMemory ben = playing();
		ben.held.put(Tier.MINOR, "m1");
		ben.heldSince.put(Tier.MINOR, rules.hourTicks);
		PlayerEnv anaEnv = new PlayerEnv(stage);
		PlayerEnv benEnv = new PlayerEnv(stage);
		anaEnv.fits = card -> card.id().equals("m1");
		benEnv.tension = rules.tensionThreshold - rules.tensionFor(Tier.MINOR) + 0.5;
		long w = 4 * rules.hourTicks;
		DirectorBrain.Clock anaClock = clock(w, w);
		DirectorBrain.Clock benClock = clock(2 * rules.hourTicks, w);
		ana.lastStepPlay = anaClock.playTicks();
		ben.lastStepPlay = benClock.playTicks();
		anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, benClock, benEnv, DirectorBrain.Recorder.NONE));
		String outcome = brain.step(world, ana, anaClock, anaEnv, RandomSource.create(1), DirectorBrain.Recorder.NONE);
		helper.assertTrue(anaEnv.fired.equals(List.of("m1")) && outcome.contains("felt by 1"), "Ana's tick: " + outcome);

		long now = benClock.playTicks();
		helper.assertTrue(ben.lastFireTier.get(Tier.MINOR) == now && ben.lastFireAny == now, "Ben's gaps did not count it");
		helper.assertTrue(ben.cycleFired(Tier.MINOR).contains("m1") && "m1".equals(ben.lastFired.get(Tier.MINOR)), "Ben's deck did not see it");
		helper.assertTrue(ben.held(Tier.MINOR) == null, "Ben still holds the card he just felt");
		helper.assertTrue(ben.totalFires == 0 && ben.feltFires == 1 && ana.totalFires == 1 && ana.feltFires == 0, "felt counted as a draw");
		DirectorMemory.HistoryEntry felt = ben.history().getLast();
		helper.assertTrue(felt.felt() && felt.cardId().equals("m1") && felt.playTicks() == now, "Ben's history: " + ben.history());
		helper.assertTrue(brain.inQuiet(ben, benClock) && ben.quietCount == 1 && benEnv.tension <= rules.tensionAfterQuiet + 1e-9,
				"the tension it added did not start Ben's quiet: quiet " + ben.quietCount + ", tension " + benEnv.tension);

		// Ben's own decision in the same tick: nothing (his quiet, and the minor gap behind it), and the quiet starts once.
		benEnv.tension = 100;
		brain.step(world, ben, benClock, benEnv, RandomSource.create(2), DirectorBrain.Recorder.NONE);
		helper.assertTrue(benEnv.fired.isEmpty() && ben.quietCount == 1, "Ben fired " + benEnv.fired + ", quiets " + ben.quietCount);
		helper.assertTrue(brain.tierBlock(Tier.MINOR, ben, benClock, Stage.PROXIMITY) != null, "Ben's minor gap is open right after a felt minor");
		helper.succeed();
	}

	/**
	 * A shared moment must be one each of them could have had on their own: Ana's card waits while Ben, close by, is in
	 * his join grace, in a quiet or his own first day; it fires once nothing of his holds it back. (A card he already
	 * had is never drawn beside him, and one held from before he came goes back at once: see the next tests.)
	 */
	@GameTest
	public void aSharedMomentWaitsForEveryonesOwnGates(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minAnyGap = 0;
		CardInfo major = card("major_x", Tier.MAJOR, Stage.PROXIMITY, false);
		DirectorBrain brain = new DirectorBrain(rules, List.of(major), 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		long w = 6 * rules.hourTicks;
		List<String> reasons = new ArrayList<>();

		for (String case_ : List.of("join grace", "quiet", "first day", "none")) {
			PacingMemory ana = playing();
			ana.nextMajorDue = 0;
			PacingMemory ben = playing();
			DirectorBrain.Clock benClock = clock(rules.hourTicks, w);
			switch (case_) {
				case "join grace" -> ben.sessionStart = benClock.playTicks() - 1;
				case "quiet" -> ben.quietUntilDayTicks = benClock.dayTicks() + DirectorBrain.DAY_TICKS;
				case "first day" -> ben.firstDayTicks = benClock.dayTicks() - 10;
				default -> {
				}
			}
			PlayerEnv anaEnv = new PlayerEnv(stage);
			anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, benClock, new PlayerEnv(stage), DirectorBrain.Recorder.NONE));
			String outcome = brain.step(world, ana, clock(w, w), anaEnv, RandomSource.create(3), DirectorBrain.Recorder.NONE);
			reasons.add(case_ + ": " + outcome);
			if (case_.equals("none")) {
				helper.assertTrue(anaEnv.fired.equals(List.of("major_x")) && ben.feltFires == 1, "with nothing holding Ben back: " + outcome);
			} else {
				helper.assertTrue(anaEnv.fired.isEmpty() && outcome.contains("someone close by: " + case_) && ben.feltFires == 0,
						"Ben's " + case_ + " did not hold Ana's card back: " + outcome);
				helper.assertTrue("major_x".equals(ana.held(Tier.MAJOR)), "the card should wait, held, for a better moment: " + outcome);
			}
		}
		A1016_02.LOGGER.info("[a1016] director shared gates: {}", reasons);
		helper.succeed();
	}

	/**
	 * Ben, who joined late, never draws a card Ana beside him already had (in her cycle, or her last): it could not reach
	 * her, so it would only sit held and block his tier while they stay together. He draws the one she has not had;
	 * with nothing left that is new to both, he draws nothing, holds nothing and his own deck is not refilled for it.
	 * Once she walks off he draws from his own deck as before.
	 */
	@GameTest
	public void aLateJoinerNeverDrawsWhatTheFriendBesideThemHad(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minorsPerHourMin = rules.minorsPerHourMax = 1_000_000;
		rules.proximityAmbientPerHour = 0;
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("m_a", Tier.MINOR, Stage.TRACES, false), card("m_b", Tier.MINOR, Stage.TRACES, false),
				card("m_c", Tier.MINOR, Stage.TRACES, false)), 50);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		long w = 6 * rules.hourTicks;
		DirectorBrain.Clock anaClock = clock(w, w);
		DirectorBrain.Clock benClock = clock(rules.hourTicks, w);
		for (int seed = 0; seed < 20; seed++) {
			WorldMemory world = new WorldMemory();
			DirectorTestSupport.pin(world, rules);
			PacingMemory ana = playing();
			ana.cycleFired.get(Tier.MINOR).add("m_a");
			ana.lastFired.put(Tier.MINOR, "m_b");
			PacingMemory ben = playing();
			ben.lastStepPlay = benClock.playTicks();
			PlayerEnv benEnv = new PlayerEnv(stage);
			benEnv.fits = card -> false; // the moment never fits, so whatever he draws stays held where the test sees it
			benEnv.sharers = List.of(new DirectorBrain.Sharer(ana, anaClock, new PlayerEnv(stage), DirectorBrain.Recorder.NONE));
			String outcome = brain.step(world, ben, benClock, benEnv, RandomSource.create(seed), DirectorBrain.Recorder.NONE);
			helper.assertTrue("m_c".equals(ben.held(Tier.MINOR)), "seed " + seed + ": beside Ana, Ben drew " + ben.held(Tier.MINOR) + " (" + outcome + ")");
		}

		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		PacingMemory ana = playing();
		ana.cycleFired.get(Tier.MINOR).addAll(List.of("m_a", "m_c"));
		ana.lastFired.put(Tier.MINOR, "m_b");
		PacingMemory ben = playing();
		ben.cycleFired.get(Tier.MINOR).add("m_a");
		ben.lastStepPlay = benClock.playTicks();
		PlayerEnv benEnv = new PlayerEnv(stage);
		benEnv.fits = card -> false;
		benEnv.sharers = List.of(new DirectorBrain.Sharer(ana, anaClock, new PlayerEnv(stage), DirectorBrain.Recorder.NONE));
		brain.step(world, ben, benClock, benEnv, RandomSource.create(7), DirectorBrain.Recorder.NONE);
		helper.assertTrue(ben.held(Tier.MINOR) == null && ben.cycleFired(Tier.MINOR).equals(Set.of("m_a")),
				"with nothing new to both, Ben held " + ben.held(Tier.MINOR) + ", his cycle " + ben.cycleFired(Tier.MINOR));
		benEnv.sharers = List.of();
		brain.step(world, ben, benClock.plus(rules.tickInterval), benEnv, RandomSource.create(7), DirectorBrain.Recorder.NONE);
		helper.assertTrue(Set.of("m_b", "m_c").contains(ben.held(Tier.MINOR)), "alone again, Ben drew " + ben.held(Tier.MINOR));
		helper.succeed();
	}

	/**
	 * MP-7 #2: Ana was holding major_x from before Ben came close, and Ben already had it: it can never reach him while
	 * they stay together, so it goes back into her deck at once (passed over next time) and major_y, new to both, is
	 * drawn and fires the same tick, felt by Ben. It never waits out {@code holdGiveUp} blocking her majors.
	 */
	@GameTest
	public void aHoldAFriendAlreadyHadGoesBackAtOnce(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minAnyGap = 0;
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("major_x", Tier.MAJOR, Stage.PROXIMITY, false),
				card("major_y", Tier.MAJOR, Stage.PROXIMITY, false)), 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		long w = 6 * rules.hourTicks;
		PacingMemory ana = playing();
		ana.nextMajorDue = 0;
		ana.held.put(Tier.MAJOR, "major_x");
		ana.heldSince.put(Tier.MAJOR, w);
		PacingMemory ben = playing();
		ben.cycleFired.get(Tier.MAJOR).add("major_x");
		PlayerEnv anaEnv = new PlayerEnv(stage);
		anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, clock(rules.hourTicks, w), new PlayerEnv(stage), DirectorBrain.Recorder.NONE));
		List<String> gaveUp = new ArrayList<>();
		DirectorBrain.Recorder rec = new DirectorBrain.Recorder() {
			@Override
			public void gaveUp(DirectorBrain.Clock clock, Tier tier, String cardId, String why) {
				gaveUp.add(cardId + ": " + why);
			}
		};
		String outcome = brain.step(world, ana, clock(w, w), anaEnv, RandomSource.create(3), rec);
		helper.assertTrue(gaveUp.equals(List.of("major_x: someone close by already had it")) && !ana.cycleFired(Tier.MAJOR).contains("major_x"),
				"the hold Ben already had did not go back into the deck at once: " + gaveUp + " (" + outcome + ")");
		helper.assertTrue(anaEnv.fired.equals(List.of("major_y")) && ben.feltFires == 1, "major_y did not fire for both: " + outcome);
		helper.succeed();
	}

	/**
	 * MP-7 #3: sessions that started apart share one mood when they meet, the earlier session's. Ben rolled an empty
	 * session far away and walks to Ana, whose session started earlier and is not empty: his takes hers, so Ana's card
	 * fires and he feels it (it used to wait out his empty session). The other way round, Ana's earlier session is the
	 * empty one: Ben's turns empty too, so the pair is never busier than one player.
	 */
	@GameTest
	public void sessionsThatMeetShareTheEarlierOnesMood(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minAnyGap = 0;
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("major_x", Tier.MAJOR, Stage.PROXIMITY, false)), 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		long w = 6 * rules.hourTicks;
		DirectorBrain.Clock anaClock = clock(w, w);
		DirectorBrain.Clock benClock = clock(rules.hourTicks, w);

		PacingMemory ana = playing();
		ana.nextMajorDue = 0;
		PacingMemory ben = playing();
		ben.sessionStart = benClock.playTicks() - rules.hourTicks / 2; // his session began half an hour ago, hers long before
		ben.sessionEmpty = true;
		ben.sessionEmptyUntil = benClock.playTicks() + rules.emptySessionMax;
		PlayerEnv anaEnv = new PlayerEnv(stage);
		anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, benClock, new PlayerEnv(stage), DirectorBrain.Recorder.NONE));
		String outcome = brain.step(world, ana, anaClock, anaEnv, RandomSource.create(3), DirectorBrain.Recorder.NONE);
		helper.assertTrue(!ben.sessionEmpty && anaEnv.fired.equals(List.of("major_x")) && ben.feltFires == 1,
				"Ben's empty session rolled apart held Ana's card back: " + outcome);

		PacingMemory ana2 = playing();
		ana2.sessionEmpty = true;
		ana2.sessionEmptyUntil = anaClock.playTicks() + rules.hourTicks;
		PacingMemory ben2 = playing();
		ben2.sessionStart = benClock.playTicks() - rules.hourTicks / 2;
		ben2.nextMajorDue = 0;
		PlayerEnv benEnv = new PlayerEnv(stage);
		benEnv.sharers = List.of(new DirectorBrain.Sharer(ana2, anaClock, new PlayerEnv(stage), DirectorBrain.Recorder.NONE));
		String benOutcome = brain.step(world, ben2, benClock, benEnv, RandomSource.create(3), DirectorBrain.Recorder.NONE);
		helper.assertTrue(ben2.sessionEmpty && ben2.sessionEmptyUntil == benClock.playTicks() + rules.hourTicks && benEnv.fired.isEmpty()
				&& benOutcome.contains("empty session"), "Ben did not take Ana's earlier empty session: " + benOutcome);
		helper.succeed();
	}

	/**
	 * Ana and Ben each had a different card while apart (m_a hers, m_b his), then every card new to both while together
	 * (m_c): each one's only undrawn card is the one the other had. Those cards are spent for the moment, so the deck
	 * has run out for the pair and refills at once: something fires while they stay together, and never a card the
	 * other already had.
	 */
	@GameTest
	public void cardsHadApartNeverDeadlockTheDeckTogether(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minorsPerHourMin = rules.minorsPerHourMax = 1_000_000;
		rules.proximityAmbientPerHour = 0;
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("m_a", Tier.MINOR, Stage.TRACES, false), card("m_b", Tier.MINOR, Stage.TRACES, false),
				card("m_c", Tier.MINOR, Stage.TRACES, false)), 50);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		PacingMemory ana = playing();
		ana.cycleFired.get(Tier.MINOR).addAll(List.of("m_a", "m_c"));
		ana.lastFired.put(Tier.MINOR, "m_c");
		PacingMemory ben = playing();
		ben.cycleFired.get(Tier.MINOR).addAll(List.of("m_b", "m_c"));
		ben.lastFired.put(Tier.MINOR, "m_c");
		PlayerEnv anaEnv = new PlayerEnv(stage);
		PlayerEnv benEnv = new PlayerEnv(stage);
		RandomSource random = RandomSource.create(5);
		long w = 6 * rules.hourTicks + rules.minorGap;
		List<String> outcomes = new ArrayList<>();
		for (int i = 0; i < 20 && anaEnv.fired.isEmpty() && benEnv.fired.isEmpty(); i++) {
			long t = w + i * rules.tickInterval;
			DirectorBrain.Clock anaClock = clock(t, t);
			DirectorBrain.Clock benClock = clock(t - 2 * rules.hourTicks, t);
			anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, benClock, benEnv, DirectorBrain.Recorder.NONE));
			outcomes.add("Ana: " + brain.step(world, ana, anaClock, anaEnv, random, DirectorBrain.Recorder.NONE));
			benEnv.sharers = List.of(new DirectorBrain.Sharer(ana, anaClock, anaEnv, DirectorBrain.Recorder.NONE));
			outcomes.add("Ben: " + brain.step(world, ben, benClock, benEnv, random, DirectorBrain.Recorder.NONE));
		}
		List<String> fired = new ArrayList<>(anaEnv.fired);
		fired.addAll(benEnv.fired);
		helper.assertTrue(fired.size() == 1, "nothing fired for the pair together: " + outcomes);
		helper.assertTrue(anaEnv.fired.isEmpty() || anaEnv.fired.equals(List.of("m_a")), "Ana fired " + anaEnv.fired + ", which Ben had");
		helper.assertTrue(benEnv.fired.isEmpty() || benEnv.fired.equals(List.of("m_b")), "Ben fired " + benEnv.fired + ", which Ana had");
		helper.succeed();
	}

	/**
	 * Together the whole cycle: Ana's and Ben's decks are both used up on the same cards. Each one's deck refills by
	 * the solo rule even though the other still had every card, so the pair's decks turn over and something fires
	 * again (a refill is only kept back when the player still had undrawn cards of their own).
	 */
	@GameTest
	public void usedUpDecksTurnOverTogether(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minorsPerHourMin = rules.minorsPerHourMax = 1_000_000;
		rules.proximityAmbientPerHour = 0;
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("m_a", Tier.MINOR, Stage.TRACES, false), card("m_b", Tier.MINOR, Stage.TRACES, false),
				card("m_c", Tier.MINOR, Stage.TRACES, false)), 50);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		PacingMemory ana = playing();
		ana.cycleFired.get(Tier.MINOR).addAll(List.of("m_a", "m_b", "m_c"));
		ana.lastFired.put(Tier.MINOR, "m_c");
		PacingMemory ben = playing();
		ben.cycleFired.get(Tier.MINOR).addAll(List.of("m_a", "m_b", "m_c"));
		ben.lastFired.put(Tier.MINOR, "m_c");
		PlayerEnv anaEnv = new PlayerEnv(stage);
		PlayerEnv benEnv = new PlayerEnv(stage);
		RandomSource random = RandomSource.create(9);
		long w = 6 * rules.hourTicks + rules.minorGap;
		List<String> outcomes = new ArrayList<>();
		for (int i = 0; i < 20 && anaEnv.fired.isEmpty() && benEnv.fired.isEmpty(); i++) {
			long t = w + i * rules.tickInterval;
			DirectorBrain.Clock anaClock = clock(t, t);
			DirectorBrain.Clock benClock = clock(t - 2 * rules.hourTicks, t);
			anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, benClock, benEnv, DirectorBrain.Recorder.NONE));
			outcomes.add("Ana: " + brain.step(world, ana, anaClock, anaEnv, random, DirectorBrain.Recorder.NONE));
			benEnv.sharers = List.of(new DirectorBrain.Sharer(ana, anaClock, anaEnv, DirectorBrain.Recorder.NONE));
			outcomes.add("Ben: " + brain.step(world, ben, benClock, benEnv, random, DirectorBrain.Recorder.NONE));
		}
		List<String> fired = new ArrayList<>(anaEnv.fired);
		fired.addAll(benEnv.fired);
		helper.assertTrue(fired.size() == 1 && !fired.getFirst().equals("m_c"), "the used-up decks never turned over: " + fired + " " + outcomes);
		helper.succeed();
	}

	/**
	 * Two players apart for the first 3 hours (their decks drift apart), then together for the rest: together they
	 * still feel minors at least 0.7 times the solo rate (the deck never deadlocks on what each had apart), and every
	 * hard limit holds. The registered deck, and the synthetic one, whose small tiers run through their cards fast; and
	 * a pair who meet and part every 40 minutes, whose sessions (rolled apart) share the earlier one's mood each time
	 * they meet (MP-7 #3).
	 */
	@GameTest(maxTicks = 2000)
	public void twoPlayersApartThenTogetherKeepTheirMinors(GameTestHelper helper) {
		List<DirectorSim.Seat> seats = List.of(new DirectorSim.Seat(ANA, 0), new DirectorSim.Seat(BEN, 0));
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> soloRuns = DirectorTestSupport.registryRuns(tempo);
			long hour = soloRuns.getFirst().rules.hourTicks;
			long meet = 3 * hour;
			DirectorSim.Sharing laterTogether = (worldTicks, a, b) -> worldTicks >= meet;
			List<DirectorSim.Group> groups = DirectorTestSupport.groupRuns(tempo, seats, laterTogether, SEEDS);
			apartThenTogether(helper, tempo + " registered deck", DirectorTestSupport.registryDeck(), DirectorSim.Rates.of(soloRuns), groups, meet);

			long stretch = 40 * hour / 60;
			DirectorSim.Sharing meetAndPart = (worldTicks, a, b) -> (worldTicks / stretch) % 2 == 1;
			List<DirectorSim.Result> soloSynthetic = new ArrayList<>();
			List<DirectorSim.Group> synthetic = new ArrayList<>();
			List<DirectorSim.Group> meeting = new ArrayList<>();
			// The small deck's runs vary a lot from seed to seed (a streak of empty sessions silences hours): pool 30.
			for (int i = 0; i < 30; i++) {
				long seed = 1000 + i;
				DirectorRules rules = rules(tempo, Signature.values()[i % Signature.values().length]);
				DirectorSim.Params params = DirectorTestSupport.params(rules, seed, rules.attentionNeutral, DirectorTestSupport.HOURS);
				soloSynthetic.add(DirectorTestSupport.fresh(rules, seed, rules.attentionNeutral, DirectorTestSupport.HOURS));
				synthetic.add(DirectorSim.runGroup(rules, SyntheticDeck.cards(), 1000, seats, laterTogether, params));
				meeting.add(DirectorSim.runGroup(rules, SyntheticDeck.cards(), 1000, seats, meetAndPart, params));
			}
			DirectorSim.Rates solo = DirectorSim.Rates.of(soloSynthetic);
			apartThenTogether(helper, tempo + " synthetic deck", SyntheticDeck.cards(), solo, synthetic, meet);
			apartThenTogether(helper, tempo + " synthetic deck, meeting every 40 min", SyntheticDeck.cards(), solo, meeting, 0);
		}
		helper.succeed();
	}

	/**
	 * Every limit held, and the felt Proximity minors per hour from {@code meet} on are at least 0.7 times solo (the deck
	 * never deadlocks), never past the group factor's 1.7.
	 */
	private static void apartThenTogether(GameTestHelper helper, String what, List<CardInfo> deck, DirectorSim.Rates solo, List<DirectorSim.Group> groups,
			long meet) {
		String at = what + ", apart then together: ";
		long minors = 0;
		long ticks = 0;
		long hour = 1;
		for (DirectorSim.Group group : groups) {
			helper.assertTrue(group.allViolations().isEmpty(), at + group.allViolations());
			for (DirectorSim.Result player : group.players) {
				hour = player.rules.hourTicks;
				List<String> hard = DirectorTestSupport.feltHardLimits(player, deck);
				helper.assertTrue(hard.isEmpty(), at + "seed " + player.params.seed + ": " + hard);
				long proximity = player.stageAt(Stage.PROXIMITY);
				if (proximity < 0) {
					continue;
				}
				long from = Math.max(proximity, meet);
				ticks += Math.max(0, player.endPlay - from);
				minors += player.felt().stream().filter(e -> e.tier() == Tier.MINOR && e.stage() == Stage.PROXIMITY && e.play() >= from).count();
			}
		}
		double together = ticks <= 0 ? 0 : minors / (ticks / (double) hour);
		A1016_02.LOGGER.info("[a1016] director {}, apart then together: {} minors/h together (solo {})", what, String.format(Locale.ROOT, "%.3f", together),
				String.format(Locale.ROOT, "%.3f", solo.minorsPerHour()));
		assertNear(helper, at + "felt minors/h together", together, solo.minorsPerHour(), 0.7, 1.7);
	}

	/**
	 * A player who took part once and never came back blocks nobody and their memory does not change while away:
	 * Ana fires the signature Ben was holding, and when Ben returns his hold goes back without firing.
	 */
	@GameTest
	public void aPlayerWhoStaysAwayBlocksNobody(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		CardInfo signature = card("sig_away", Tier.SIGNATURE, Stage.TRACES, false);
		DirectorBrain brain = new DirectorBrain(rules, List.of(signature, card("amb", Tier.AMBIENT, Stage.ALONE, false)), 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		SharedStage stage = new SharedStage(Stage.PROXIMITY);
		PacingMemory ben = new PacingMemory();
		ben.held.put(Tier.SIGNATURE, "sig_away");
		ben.heldSince.put(Tier.SIGNATURE, 100L);
		ben.quietUntilDayTicks = 40 * DirectorBrain.DAY_TICKS;
		String benAway = ben.toTag().toString();

		PacingMemory ana = playing();
		PlayerEnv anaEnv = new PlayerEnv(stage);
		RandomSource random = RandomSource.create(6);
		long w = 3 * rules.hourTicks;
		for (int i = 0; i < 20 && anaEnv.fired.isEmpty(); i++) {
			w += rules.tickInterval;
			brain.step(world, ana, clock(w, w), anaEnv, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(anaEnv.fired.contains("sig_away"), "Ana never got the signature Ben was holding: " + anaEnv.fired);
		helper.assertTrue(ben.toTag().toString().equals(benAway), "Ben's memory changed while he was away");

		PlayerEnv benEnv = new PlayerEnv(stage);
		DirectorBrain.Clock back = clock(rules.hourTicks, w + rules.tickInterval);
		brain.onJoin(world, ben, back, benEnv, random, DirectorBrain.Recorder.NONE);
		brain.step(world, ben, back.plus(rules.tickInterval), benEnv, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(ben.held(Tier.SIGNATURE) == null && !benEnv.fired.contains("sig_away"), "Ben's stale hold fired or stayed");
		helper.succeed();
	}

	// --- two-player dry runs (the registered deck, the playthrough's profiles, D-044 and D-054's code defaults) ---

	/** Two players online the whole time and never close: each one gets the solo pace and every hard limit holds. */
	@GameTest(maxTicks = 2000)
	public void twoPlayersApartEachFeelTheSoloPace(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		for (Tempo tempo : Tempo.values()) {
			DirectorSim.Rates solo = DirectorSim.Rates.of(DirectorTestSupport.registryRuns(tempo));
			List<DirectorSim.Group> groups = DirectorTestSupport.groupRuns(tempo, List.of(new DirectorSim.Seat(ANA, 0), new DirectorSim.Seat(BEN, 0)),
					DirectorSim.Sharing.APART, SEEDS);
			List<DirectorSim.Result> players = DirectorTestSupport.seats(groups);
			DirectorSim.Rates felt = DirectorSim.Rates.felt(players);
			String at = tempo + " apart: ";
			A1016_02.LOGGER.info("[a1016] director two players apart {}: {} (solo: {})", tempo, felt.summary(), solo.summary());
			for (DirectorSim.Group group : groups) {
				helper.assertTrue(group.allViolations().isEmpty(), at + group.allViolations());
				for (DirectorSim.Result player : group.players) {
					helper.assertTrue(player.count(Kind.FELT) == 0, at + "felt someone else's card although nobody was close");
					List<String> hard = DirectorTestSupport.feltHardLimits(player, deck);
					helper.assertTrue(hard.isEmpty(), at + "seed " + player.params.seed + ": " + hard);
				}
			}
			assertNear(helper, at + "minors/h", felt.minorsPerHour(), solo.minorsPerHour(), 0.8, 1.2);
			assertNear(helper, at + "majors/h", felt.majorsPerHour(), solo.majorsPerHour(), 0.8, 1.2);
			assertNear(helper, at + "Traces ambients/h", felt.tracesAmbientsPerHour(), solo.tracesAmbientsPerHour(), 0.8, 1.2);
		}
		helper.succeed();
	}

	/**
	 * Two players always together: both feel exactly the same moments, each one's felt timeline keeps every solo
	 * hard limit, and the pair rolls {@code groupRateFactor} (1.5) solo rates between them (D-074): Traces' ambients,
	 * which no hard gap holds back, come 1.2 to 1.7 times as often as alone. The minors stay near one player's (at least
	 * it, never past the factor): each one's 15-minute minor gap and tension quiets hold for everything they feel
	 * (D-060.3), and those bind before the factor does. Majors are scheduled, not rolled, so they stay about one player's.
	 */
	@GameTest(maxTicks = 2000)
	public void twoPlayersTogetherShareOneAndAHalfRates(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		long shared = 0;
		for (Tempo tempo : Tempo.values()) {
			DirectorSim.Rates solo = DirectorSim.Rates.of(DirectorTestSupport.registryRuns(tempo));
			List<DirectorSim.Group> groups = DirectorTestSupport.groupRuns(tempo, List.of(new DirectorSim.Seat(ANA, 0), new DirectorSim.Seat(BEN, 0)),
					DirectorSim.Sharing.TOGETHER, SEEDS);
			DirectorSim.Rates felt = DirectorSim.Rates.felt(DirectorTestSupport.seats(groups));
			String at = tempo + " together: ";
			A1016_02.LOGGER.info("[a1016] director two players together {}: {} (solo: {})", tempo, felt.summary(), solo.summary());
			for (DirectorSim.Group group : groups) {
				helper.assertTrue(group.allViolations().isEmpty(), at + group.allViolations());
				DirectorSim.Result ana = group.player(ANA);
				DirectorSim.Result ben = group.player(BEN);
				helper.assertTrue(feltSequence(ana, 0).equals(feltSequence(ben, 0)), at + "seed " + ana.params.seed + ": they did not feel the same moments");
				shared += ana.count(Kind.FELT) + ben.count(Kind.FELT);
				for (DirectorSim.Result player : group.players) {
					List<String> hard = DirectorTestSupport.feltHardLimits(player, deck);
					helper.assertTrue(hard.isEmpty(), at + "seed " + player.params.seed + ": " + hard);
				}
			}
			assertNear(helper, at + "felt minors/h", felt.minorsPerHour(), solo.minorsPerHour(), 0.95, 1.7);
			assertNear(helper, at + "felt majors/h", felt.majorsPerHour(), solo.majorsPerHour(), 0.7, 1.25);
			assertNear(helper, at + "felt Traces ambients/h", felt.tracesAmbientsPerHour(), solo.tracesAmbientsPerHour(), 1.2, 1.7);
		}
		helper.assertTrue(shared > 1000, "only " + shared + " shared moments: the test proved nothing");
		helper.succeed();
	}

	/**
	 * Ben first takes part 3 hours after Ana: his join grace and his own first in-game day hold for everything he feels,
	 * close to her or not, while apart Ana's pacing goes on through them (her session never restarts at his join).
	 */
	@GameTest(maxTicks = 2000)
	public void aPlayerJoiningThreeHoursLateHasTheirOwnGraceAndFirstDay(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		List<Long> seeds = SEEDS.subList(0, 96);
		int anaInBenGrace = 0;
		for (DirectorSim.Sharing sharing : List.of(DirectorSim.Sharing.APART, DirectorSim.Sharing.TOGETHER)) {
			boolean together = sharing == DirectorSim.Sharing.TOGETHER;
			for (Tempo tempo : Tempo.values()) {
				String at = tempo + (together ? " together" : " apart") + ", Ben 3 h late: ";
				for (DirectorSim.Group group : DirectorTestSupport.groupRuns(tempo, List.of(new DirectorSim.Seat(ANA, 0), new DirectorSim.Seat(BEN, 3)),
						sharing, seeds)) {
					helper.assertTrue(group.allViolations().isEmpty(), at + group.allViolations());
					DirectorSim.Result ana = group.player(ANA);
					DirectorSim.Result ben = group.player(BEN);
					DirectorRules rules = ben.rules;
					long joinedAt = 3 * rules.hourTicks;
					long benFirstDayEnd = ben.startDayTicks + rules.noMajorBeforeDay * DirectorBrain.DAY_TICKS;
					helper.assertTrue(ben.startPlay == 0 && ben.startDayTicks == joinedAt && ben.startPacing.firstDayTicks() == joinedAt,
							at + "Ben's clock and first day should start at his join");
					helper.assertTrue(DirectorTestSupport.feltHardLimits(ben, deck).isEmpty() && DirectorTestSupport.feltHardLimits(ana, deck).isEmpty(),
							at + DirectorTestSupport.feltHardLimits(ben, deck) + " " + DirectorTestSupport.feltHardLimits(ana, deck));
					for (Event e : ben.felt()) {
						helper.assertTrue(e.play() >= rules.joinGrace, at + "Ben felt " + e.cardId() + " in his first minutes");
						helper.assertTrue(!(e.tier() == Tier.MAJOR || e.tier() == Tier.SIGNATURE) || e.dayTicks() >= benFirstDayEnd,
								at + "Ben felt a major on his first day");
					}
					// Ana's session goes on: a session of hers starts at Ben's join only where the world's sessions turn over.
					boolean restarted = ana.events.stream().anyMatch(e -> e.kind() == Kind.SESSION_START && e.play() == joinedAt);
					boolean turnover = ana.events.stream().anyMatch(e -> e.kind() == Kind.SESSION_END && e.play() == joinedAt);
					helper.assertFalse(restarted && !turnover, at + "Ana's session restarted when Ben joined");
					if (together) {
						helper.assertTrue(feltSequence(ana, joinedAt).equals(feltSequence(ben, 0)), at + "they did not feel the same after he joined");
					} else {
						anaInBenGrace += (int) ana.felt().stream().filter(e -> e.play() >= joinedAt && e.play() < joinedAt + rules.joinGrace).count();
					}
				}
			}
		}
		A1016_02.LOGGER.info("[a1016] director late joiner: apart, Ana felt {} cards inside Ben's join grace", anaInBenGrace);
		helper.assertTrue(anaInBenGrace > 0, "apart, Ben's join grace held Ana back");
		helper.succeed();
	}

	/** The cards a player went through, in order, from their own play tick {@code from} on. */
	private static List<String> feltSequence(DirectorSim.Result r, long from) {
		return r.felt().stream().filter(e -> e.play() >= from).map(e -> e.cardId() + (e.fake() ? "*" : "")).toList();
	}

	private static void assertNear(GameTestHelper helper, String what, double value, double solo, double low, double high) {
		helper.assertTrue(solo > 0 && value >= low * solo && value <= high * solo,
				String.format(Locale.ROOT, "%s %.3f against %.3f solo (%.2f x, allowed %.2f to %.2f)", what, value, solo, solo > 0 ? value / solo : 0, low,
						high));
	}

	// --- old saves (D-062.8) ---

	/**
	 * A v1.0 world: its {@code state.dat} names one subject and its {@code director.dat} is one flat memory. The world
	 * fields become the world part, the rest becomes that subject's pacing (the first known player), and the new
	 * format round-trips.
	 */
	@GameTest
	public void anOldDirectorSaveBecomesTheFirstKnownPlayersPacing(GameTestHelper helper) {
		UUID alex = UUID.fromString("00000000-0000-0000-0000-0000000a1016");
		CompoundTag oldState = new CompoundTag();
		CompoundTag subject = new CompoundTag();
		subject.putIntArray("uuid", UUIDUtil.uuidToIntArray(alex));
		subject.putString("name", "Alex");
		oldState.put("subject", subject);
		CompoundTag clock = new CompoundTag();
		clock.putLong("playTicks", 500_000L);
		oldState.put("clock", clock);
		HerobrineState state = HerobrineState.CODEC.parse(NbtOps.INSTANCE, oldState).getOrThrow();
		helper.assertTrue(state.players().equals(List.of(new HerobrineState.Subject(alex, "Alex"))), "the old state's subject: " + state.players());

		// director.dat as v1.0 wrote it: every field at the top level.
		CompoundTag v1 = new CompoundTag();
		v1.putLong("lastStepPlay", 499_400L);
		v1.putDouble("stageClock", 480_000.0);
		v1.putLong("tracesAt", 150_000L);
		v1.putLong("proximityAt", 400_000L);
		v1.putString("rolledTempo", "SLOW_BURN");
		v1.putLong("nextMajorDue", 520_000L);
		CompoundTag cycles = new CompoundTag();
		cycles.putString("MINOR", "chest_opens,torch_gone");
		v1.put("cycleFired", cycles);
		CompoundTag lastFired = new CompoundTag();
		lastFired.putString("MINOR", "torch_gone");
		v1.put("lastFired", lastFired);
		CompoundTag held = new CompoundTag();
		held.putString("MAJOR", "sighting_close");
		v1.put("held", held);
		v1.putString("signaturesFired", "signature_cross_row");
		v1.putLong("lastFireAny", 498_000L);
		CompoundTag lastTag = new CompoundTag();
		lastTag.putLong("SOUND", 497_000L);
		v1.put("lastTag", lastTag);
		v1.putLong("quietUntilDayTicks", 300_000L);
		v1.putLong("sessionStart", 450_000L);
		v1.putInt("sessionCount", 7);
		v1.putInt("totalFires", 31);
		ListTag history = new ListTag();
		CompoundTag entry = new CompoundTag();
		entry.putLong("play", 498_000L);
		entry.putLong("day", 12L);
		entry.putString("card", "torch_gone");
		entry.putString("tier", "MINOR");
		entry.putBoolean("fake", false);
		entry.putBoolean("forced", false);
		entry.putString("stage", "PROXIMITY");
		history.add(entry);
		v1.put("history", history);
		v1.putLong("daylightAccum", 6000L);
		v1.putLong("lastSleptDay", 11L);
		v1.putLong("lastTellingDay", 9L);
		v1.putLong("lastDisc13Play", 300_000L);
		v1.putString("lastWarpSummary", "+3d old");

		DirectorData data = DirectorData.CODEC.parse(NbtOps.INSTANCE, v1).getOrThrow();
		DirectorMemory memory = data.memory();
		helper.assertTrue(memory.players().isEmpty() && memory.unowned() != null, "the old pacing should wait for its owner");
		data.adoptUnowned(state);
		PacingMemory pacing = memory.find(alex).orElse(null);
		helper.assertTrue(pacing != null && memory.unowned() == null && memory.players().size() == 1, "Alex did not get the old pacing");
		WorldMemory world = memory.world();
		helper.assertTrue(world.stageClock == 480_000.0 && world.tracesAt == 150_000L && world.proximityAt == 400_000L && world.lastStepPlay == 499_400L
				&& world.signaturesFired().contains("signature_cross_row") && world.lastTagFire(CardTag.SOUND) == 497_000L
				&& world.daylightAccum == 6000L && world.lastSleptDay == 11L && world.lastTellingDay == 9L && world.lastDisc13Play == 300_000L
				&& world.lastWarpSummary.equals("+3d old"), "the world part lost a field");
		helper.assertTrue(pacing.nextMajorDue == 520_000L && pacing.cycleFired(Tier.MINOR).equals(Set.of("chest_opens", "torch_gone"))
				&& "torch_gone".equals(pacing.lastFired.get(Tier.MINOR)) && "sighting_close".equals(pacing.held(Tier.MAJOR))
				&& pacing.lastFireAny == 498_000L && pacing.lastTagFire(CardTag.SOUND) == 497_000L && pacing.quietUntilDayTicks() == 300_000L && pacing.sessionStart == 450_000L
				&& pacing.sessionCount == 7 && pacing.totalFires == 31 && pacing.lastStepPlay == 499_400L && pacing.firstDayTicks() == 0
				&& pacing.history().size() == 1 && !pacing.history().getFirst().felt(), "Alex's pacing lost a field");
		// His old timers keep running on his own clock: core gives him the old play ticks as his own (core's v1 test).

		Tag saved = DirectorData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		helper.assertTrue(saved instanceof CompoundTag tag && tag.getIntOr("format", 0) == DirectorMemory.FORMAT && !tag.contains("stageClock"),
				"not saved in the new format: " + saved);
		DirectorData again = DirectorData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(again.memory().toTag().equals(memory.toTag()) && again.memory().find(alex).isPresent(), "the new format did not round-trip");

		// An old memory from a world nobody ever joined stays unowned (and saved) until someone is known.
		DirectorData nobody = DirectorData.CODEC.parse(NbtOps.INSTANCE, v1).getOrThrow();
		nobody.adoptUnowned(new HerobrineState());
		Tag kept = DirectorData.CODEC.encodeStart(NbtOps.INSTANCE, nobody).getOrThrow();
		helper.assertTrue(nobody.memory().unowned() != null && DirectorData.CODEC.parse(NbtOps.INSTANCE, kept).getOrThrow().memory().unowned() != null,
				"the unowned old memory was lost");
		helper.succeed();
	}

	// --- triggers: once per occasion, or per player ---

	/**
	 * Attention triggers count once per occasion, not once per witness: two players sleeping through one night are one
	 * SLEPT; one disc 13 jukebox is one DISC_13_UNDERGROUND however many hear it; daylight is one world tally.
	 * LOW_RENDER_DISTANCE is per player and once per session.
	 */
	@GameTest
	public void triggersCountOncePerOccasion(GameTestHelper helper) {
		WorldMemory world = new WorldMemory();
		// Ana lies down, then Ben the same night: one SLEPT, with or without the once-per-day rule.
		helper.assertTrue(DirectorTriggers.sleptRule(world, 5, false, true), "the first sleeper did not count");
		helper.assertFalse(DirectorTriggers.sleptRule(world, 5, true, true), "the second sleeper of the night counted");
		helper.assertFalse(DirectorTriggers.sleptRule(world, 5, false, true), "the same night counted twice");
		WorldMemory loose = new WorldMemory();
		helper.assertTrue(DirectorTriggers.sleptRule(loose, 5, false, false) && !DirectorTriggers.sleptRule(loose, 5, true, false),
				"without once-per-day, joining someone already asleep is still the same sleep");
		helper.assertTrue(DirectorTriggers.sleptRule(world, 6, false, true), "the next night did not count");

		// One jukebox start: one count, then the world cooldown, whoever hears it.
		long cooldown = 12_000;
		helper.assertTrue(DirectorTriggers.disc13Rule(world, 100_000, true, cooldown), "disc 13 heard did not count");
		helper.assertFalse(DirectorTriggers.disc13Rule(world, 100_000 + cooldown - 1, true, cooldown), "a second jukebox inside the cooldown counted");
		helper.assertFalse(DirectorTriggers.disc13Rule(world, 200_000, false, cooldown), "disc 13 nobody heard counted");
		helper.assertTrue(DirectorTriggers.disc13Rule(world, 200_000, true, cooldown), "disc 13 after the cooldown did not count");

		// Earshot: anyone alive, in that level, close enough; never per listener.
		ServerLevel level = helper.getLevel();
		BlockPos jukebox = helper.absolutePos(new BlockPos(1, 1, 1));
		Stranger near = new Stranger(level, "near", Vec3.atCenterOf(jukebox).add(3, 0, 0));
		Stranger alsoNear = new Stranger(level, "alsoNear", Vec3.atCenterOf(jukebox).add(0, 0, 5));
		Stranger far = new Stranger(level, "far", Vec3.atCenterOf(jukebox).add(200, 0, 0));
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		helper.assertTrue(DirectorTriggers.anyoneInEarshot(List.of(near, alsoNear), level, jukebox, 64), "two listeners: nobody heard it");
		helper.assertFalse(DirectorTriggers.anyoneInEarshot(List.of(far), level, jukebox, 64), "heard from 200 blocks");
		if (nether != null) {
			Stranger elsewhere = new Stranger(nether, "elsewhere", Vec3.atCenterOf(jukebox));
			helper.assertFalse(DirectorTriggers.anyoneInEarshot(List.of(elsewhere), level, jukebox, 64), "heard from another dimension");
		}
		alsoNear.setHealth(0.0F);
		helper.assertFalse(DirectorTriggers.anyoneInEarshot(List.of(alsoNear, far), level, jukebox, 64), "a dead player heard it");

		// Daylight: the world tally moves once per world step, however many are in the open.
		WorldMemory sunny = new WorldMemory();
		int counts = 0;
		for (int i = 0; i < 40; i++) {
			counts += DirectorTriggers.daylightRule(sunny, true, 600, 12_000) ? 1 : 0;
		}
		helper.assertTrue(counts == 2, "daylight counted " + counts + " times in 40 world steps of 600 ticks");

		// Low render distance: Ana's own, once per session; Ben's normal view never counts; a new session counts again.
		PacingMemory ana = new PacingMemory();
		PacingMemory ben = new PacingMemory();
		int anaCounts = 0;
		int benCounts = 0;
		for (int i = 0; i < 100; i++) {
			anaCounts += DirectorTriggers.lowRenderRule(ana, true, 600, 12_000) ? 1 : 0;
			benCounts += DirectorTriggers.lowRenderRule(ben, false, 600, 12_000) ? 1 : 0;
		}
		helper.assertTrue(anaCounts == 1 && benCounts == 0, "low render counted " + anaCounts + " for Ana, " + benCounts + " for Ben");
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		new DirectorBrain(rules, List.of(), 10).onJoin(new WorldMemory(), ana, new DirectorBrain.Clock(0, 0), new PlayerEnv(new SharedStage(Stage.ALONE)),
				RandomSource.create(1), DirectorBrain.Recorder.NONE);
		for (int i = 0; i < 20; i++) {
			anaCounts += DirectorTriggers.lowRenderRule(ana, true, 600, 12_000) ? 1 : 0;
		}
		helper.assertTrue(anaCounts == 2, "a new session did not count again: " + anaCounts);

		// OBEYED_AFTER_STOP is the group's: anyone's telling starts the wait over for everyone.
		HerobrineState state = new HerobrineState();
		state.setStopFired(true);
		WorldMemory obey = new WorldMemory();
		helper.assertFalse(DirectorTriggers.obeyRule(state, obey, 10, 3), "fired the day Stop. was seen");
		new DirectorBrain(rules, List.of(), 10).onTelling(obey, new DirectorBrain.Clock(0, 12 * DirectorBrain.DAY_TICKS), new PlayerEnv(
				new SharedStage(Stage.TELLING)), false, DirectorBrain.Recorder.NONE);
		helper.assertFalse(DirectorTriggers.obeyRule(state, obey, 14, 3), "someone told on day 12, yet day 14 counted as obeying");
		helper.assertTrue(DirectorTriggers.obeyRule(state, obey, 15, 3), "three quiet days after the telling did not count");
		helper.succeed();
	}

	// --- the live loop's pieces ---

	/** Each participant decides once per interval on their own tick: two who start together never share a tick. */
	@GameTest
	public void decisionsAreStaggeredPerPlayer(GameTestHelper helper) {
		DecisionSchedule schedule = new DecisionSchedule();
		long interval = 600;
		long anaFirst = schedule.start(ANA, 1000, interval);
		long benFirst = schedule.start(BEN, 1000, interval);
		helper.assertTrue(anaFirst == 1600 && benFirst != anaFirst && benFirst - anaFirst < interval, "first ticks " + anaFirst + ", " + benFirst);
		helper.assertTrue(schedule.start(ANA, 1300, interval) == anaFirst, "starting twice moved Ana's tick");
		int anaDecisions = 0;
		int benDecisions = 0;
		for (long now = 1000; now < 1000 + 10 * interval; now++) {
			List<UUID> due = schedule.due(now, interval);
			helper.assertTrue(due.size() <= 1, "two players decided in tick " + now + ": " + due);
			anaDecisions += due.contains(ANA) ? 1 : 0;
			benDecisions += due.contains(BEN) ? 1 : 0;
		}
		helper.assertTrue(anaDecisions >= 9 && anaDecisions <= 10 && benDecisions >= 9 && benDecisions <= 10, "decisions " + anaDecisions + ", " + benDecisions);
		schedule.stop(BEN);
		helper.assertTrue(schedule.contains(ANA) && !schedule.contains(BEN), "Ben's stop touched Ana");
		long benAgain = schedule.start(BEN, 9000, interval);
		helper.assertTrue(Math.floorMod(benAgain, interval) != Math.floorMod(schedule.nextTick(ANA), interval), "Ben came back on Ana's tick");
		schedule.clear();
		helper.assertFalse(schedule.anyDue(Long.MAX_VALUE), "a cleared schedule still has players");
		helper.succeed();
	}

	/**
	 * The server's tick count went back (a new server without a stop): restarting the schedule from the new count
	 * gives everyone a decision within one interval again, staggered and in the same order, where their old ticks
	 * would have held every decision back for hours.
	 */
	@GameTest
	public void aTickCountThatWentBackDoesNotStarveDecisions(GameTestHelper helper) {
		DecisionSchedule schedule = new DecisionSchedule();
		long interval = 600;
		schedule.start(ANA, 2_000_000, interval);
		schedule.start(BEN, 2_000_000, interval);
		long now = 50;
		helper.assertFalse(schedule.anyDue(now + 2 * interval), "the old ticks are due on the new count");
		schedule.restart(now, interval);
		helper.assertTrue(schedule.players().equals(List.of(ANA, BEN)), "the restart changed who is scheduled: " + schedule.players());
		long ana = schedule.nextTick(ANA);
		long ben = schedule.nextTick(BEN);
		helper.assertTrue(ana == now + interval && ben > now && ben - now <= interval + 1 && Math.floorMod(ana, interval) != Math.floorMod(ben, interval),
				"next ticks after the restart " + ana + ", " + ben);
		int decisions = 0;
		for (long tick = now; tick <= now + 2 * interval + 1; tick++) {
			decisions += schedule.due(tick, interval).size();
		}
		helper.assertTrue(decisions == 4, decisions + " decisions in two intervals after the restart, not 4");
		helper.succeed();
	}

	/** {@code /a1016 state} and the director lines: one line per player, each with their own numbers, Locale.ROOT. */
	@GameTest
	public void debugLinesShowEachPlayersOwnPacing(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("held_card", Tier.MINOR, Stage.TRACES, false)), 10);
		PacingMemory ana = playing();
		ana.quietUntilDayTicks = 30 * DirectorBrain.DAY_TICKS;
		ana.totalFires = 4;
		PacingMemory ben = new PacingMemory();
		ben.held.put(Tier.MINOR, "held_card");
		ben.feltFires = 3;
		Locale before = Locale.getDefault();
		String anaLine;
		String benLine;
		try {
			Locale.setDefault(Locale.forLanguageTag("es-ES"));
			anaLine = DirectorDebug.pacingLine("Ana", ana, clock(5 * rules.hourTicks, 6 * rules.hourTicks), Stage.PROXIMITY, 12.5, rules, brain);
			benLine = DirectorDebug.pacingLine("Ben", ben, clock(rules.hourTicks, 6 * rules.hourTicks), Stage.PROXIMITY, 0, rules, brain);
		} finally {
			Locale.setDefault(before);
		}
		helper.assertTrue(anaLine.contains("Ana:") && anaLine.contains("tension 12.5/") && anaLine.contains("quiet until day 30") && anaLine.contains("fires 4"),
				"Ana's line: " + anaLine);
		helper.assertTrue(benLine.contains("Ben:") && benLine.contains("tension 0.0/") && benLine.contains("session none")
				&& benLine.contains("minor held_card") && benLine.contains("felt 3"), "Ben's line: " + benLine);
		helper.assertFalse((anaLine + benLine).matches(".*\\d,\\d.*"), "a localized decimal: " + anaLine + " / " + benLine);
		helper.succeed();
	}

	/**
	 * D-064: Ending A's obeying is the group's. After "Stop.", {@code director:obeyed_after_stop} comes only once nobody
	 * has told for {@code obeyDays} in-game days. Ana keeping quiet while Ben tells is no obeying: his telling takes the
	 * flag away at once and the wait starts over from his day; Ana's later telling starts it over again from hers.
	 */
	@GameTest
	public void obeyedAfterStopMeansNobodyInTheGroupTold(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorBrain brain = new DirectorBrain(rules, List.of(), 10);
		SharedStage stage = new SharedStage(Stage.TELLING);
		HerobrineState state = new HerobrineState();
		state.setStopFired(true);
		WorldMemory world = new WorldMemory();
		int days = 3;
		long day = DirectorBrain.DAY_TICKS;
		helper.assertFalse(DirectorTriggers.obeyRule(state, world, 10, days), "fired the day Stop. was seen");
		helper.assertTrue(DirectorTriggers.obeyRule(state, world, 13, days) && state.hasFlag(DirectorFlags.OBEYED_AFTER_STOP),
				"nobody told for three days, yet the group did not obey");

		// Ben tells on day 14 (on his own clock; Ana says nothing): the flag goes at once, before any world step.
		brain.onTelling(world, new DirectorBrain.Clock(rules.hourTicks, 14 * day + 100, 30 * rules.hourTicks), new PlayerEnv(stage), false,
				DirectorBrain.Recorder.NONE);
		DirectorTriggers.obeyBroken(state);
		helper.assertFalse(state.hasFlag(DirectorFlags.OBEYED_AFTER_STOP), "Ben told, yet the group still counts as obeying");
		helper.assertFalse(DirectorTriggers.obeyRule(state, world, 16, days) || state.hasFlag(DirectorFlags.OBEYED_AFTER_STOP),
				"two days after Ben's telling counted as obeying");

		// Ana tells on day 16: the wait starts over from her day, not Ben's.
		brain.onTelling(world, new DirectorBrain.Clock(25 * rules.hourTicks, 16 * day + 500, 30 * rules.hourTicks), new PlayerEnv(stage), false,
				DirectorBrain.Recorder.NONE);
		DirectorTriggers.obeyBroken(state);
		helper.assertFalse(DirectorTriggers.obeyRule(state, world, 17, days) || DirectorTriggers.obeyRule(state, world, 18, days),
				"three days after Ben's telling counted, though Ana told since");
		helper.assertTrue(DirectorTriggers.obeyRule(state, world, 19, days) && state.hasFlag(DirectorFlags.OBEYED_AFTER_STOP),
				"three quiet days after the last telling of anyone did not count");
		DirectorTriggers.obeyBroken(new HerobrineState());
		helper.succeed();
	}

	/**
	 * Nobody takes part for the run's first two hours, then Ana, then Ben an hour later. The world clock, the day and
	 * the stage wait for the first of them (the live world clock stops while nobody takes part), so every stage change
	 * is the world's once and on Ana's record; each seat decides from one tick after its join, so the time it books is
	 * exactly its play time; and every hard limit holds.
	 */
	@GameTest(maxTicks = 2000)
	public void theWorldWaitsWhileNobodyIsSeated(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		int stages = 0;
		for (Tempo tempo : Tempo.values()) {
			for (DirectorSim.Group group : DirectorTestSupport.groupRuns(tempo, List.of(new DirectorSim.Seat(ANA, 2), new DirectorSim.Seat(BEN, 3)),
					DirectorSim.Sharing.TOGETHER, SEEDS.subList(0, 32))) {
				DirectorSim.Result ana = group.player(ANA);
				DirectorSim.Result ben = group.player(BEN);
				String at = tempo + " seed " + ana.params.seed + ": ";
				helper.assertTrue(group.allViolations().isEmpty(), at + group.allViolations());
				long hour = ana.rules.hourTicks;
				helper.assertTrue(ana.startDayTicks == 0 && ben.startDayTicks == hour && ana.startStage == Stage.ALONE,
						at + "the world ran while nobody was seated: Ana joined at day tick " + ana.startDayTicks + " in " + ana.startStage + ", Ben at "
								+ ben.startDayTicks);
				long worldStages = group.worldEvents.stream().filter(e -> e.kind() == Kind.STAGE).count();
				helper.assertTrue(worldStages == ana.count(Kind.STAGE), at + worldStages + " world stage changes, " + ana.count(Kind.STAGE) + " on Ana's record");
				stages += (int) worldStages;
				for (DirectorSim.Result player : group.players) {
					long booked = 0;
					for (Stage s : Stage.values()) {
						booked += player.stageTicks(s);
					}
					helper.assertTrue(booked == player.endPlay - player.startPlay,
							at + "booked " + booked + " of " + (player.endPlay - player.startPlay) + " play ticks");
					List<String> hard = DirectorTestSupport.feltHardLimits(player, deck);
					helper.assertTrue(hard.isEmpty(), at + hard);
				}
			}
		}
		helper.assertTrue(stages > 0, "no stage ever moved: the test proved nothing");
		helper.succeed();
	}
}
