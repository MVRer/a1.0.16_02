package com.forzacode.a1016_02.director;

import static com.forzacode.a1016_02.director.DirectorTestSupport.HOURS;
import static com.forzacode.a1016_02.director.DirectorTestSupport.SEEDS;
import static com.forzacode.a1016_02.director.DirectorTestSupport.card;
import static com.forzacode.a1016_02.director.DirectorTestSupport.fires;
import static com.forzacode.a1016_02.director.DirectorTestSupport.fresh;
import static com.forzacode.a1016_02.director.DirectorTestSupport.playthroughs;
import static com.forzacode.a1016_02.director.DirectorTestSupport.rules;
import static com.forzacode.a1016_02.director.DirectorTestSupport.where;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.director.DirectorSim.Event;
import com.forzacode.a1016_02.director.DirectorSim.Kind;
import com.forzacode.a1016_02.director.DirectorTestSupport.Playthrough;
import com.forzacode.a1016_02.director.DirectorTestSupport.ScriptEnv;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

/**
 * Director game tests. The pacing proofs replay fixed-seed 20 h playthroughs (every tempo, six seeds, the
 * synthetic deck) through the same {@link DirectorBrain} the live director runs, and check every limit
 * independently of {@link DirectorSim#check}. The soft-rate floors pool the scripted playthrough (registered deck)
 * over {@link DirectorApi#tuningSeeds()}. Only {@code liveRulesReadTheFlags} touches the shared world state, and
 * puts it back.
 */
public class DirectorGameTests {
	@GameTest
	public void directorIsInstalled(GameTestHelper helper) {
		helper.assertTrue(Services.director() instanceof DirectorImpl, "the real director is not installed: " + Services.director());
		helper.succeed();
	}

	@GameTest
	public void jukeboxMixinApplies(GameTestHelper helper) {
		// No subject in the test world, so nothing counts; this proves the hooks are woven in and harmless.
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, Blocks.JUKEBOX);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(rel);
		helper.assertTrue(pos.getY() < ModConfig.pacing().disc13BelowY, "the test structure is not underground");
		JukeboxBlockEntity jukebox = (JukeboxBlockEntity) level.getBlockEntity(pos);
		Holder<JukeboxSong> thirteen = level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getOrThrow(JukeboxSongs.THIRTEEN);
		jukebox.getSongPlayer().play(level, thirteen);
		helper.assertTrue(jukebox.getSongPlayer().isPlaying(), "disc 13 did not start");
		jukebox.getSongPlayer().stop(level, level.getBlockState(pos));
		helper.assertFalse(jukebox.getSongPlayer().isPlaying(), "disc 13 did not stop");
		boolean woven = Arrays.stream(JukeboxSongPlayer.class.getDeclaredMethods()).filter(m -> m.getName().contains("a1016$")).count() == 2;
		helper.assertTrue(woven, "the jukebox mixin is not applied");
		helper.succeed();
	}

	@GameTest
	public void stagesAdvanceInsideTempoWindows(GameTestHelper helper) {
		for (Tempo tempo : Tempo.values()) {
			for (long seed : SEEDS) {
				DirectorRules rules = rules(tempo, Signature.CROSS_ROW);
				long tick = rules.tickInterval;
				double band = rules.attentionBand;
				DirectorSim.Result neutral = fresh(rules, seed, rules.attentionNeutral, HOURS);
				long traces = neutral.stageAt(Stage.TRACES);
				long proximity = neutral.stageAt(Stage.PROXIMITY);
				String at = tempo + "/seed " + seed + ": ";
				helper.assertTrue(traces >= rules.tracesStart.min() && traces <= rules.tracesStart.max() + tick,
						at + "Traces at " + traces + " outside " + rules.tracesStart);
				helper.assertTrue(proximity >= rules.proximityStart.min() && proximity <= rules.proximityStart.max() + tick,
						at + "Proximity at " + proximity + " outside " + rules.proximityStart);
				helper.assertTrue(neutral.events.stream().noneMatch(e -> e.kind() == Kind.STAGE && e.stage().atLeast(Stage.TELLING)),
						at + "the director moved into Telling or Removal on its own");

				// Attention tilts the same rolled points, never past the band.
				DirectorSim.Result high = fresh(rules, seed, 100, HOURS);
				DirectorSim.Result low = fresh(rules, seed, 0, HOURS);
				helper.assertTrue(high.stageAt(Stage.TRACES) <= traces && high.stageAt(Stage.TRACES) >= (long) (rules.tracesStart.min() * (1 - band)) - tick,
						at + "high attention Traces at " + high.stageAt(Stage.TRACES));
				helper.assertTrue(high.stageAt(Stage.PROXIMITY) <= proximity && high.stageAt(Stage.PROXIMITY) >= (long) (rules.proximityStart.min() * (1 - band)) - tick,
						at + "high attention Proximity at " + high.stageAt(Stage.PROXIMITY));
				helper.assertTrue(low.stageAt(Stage.TRACES) >= traces && low.stageAt(Stage.TRACES) <= (long) (rules.tracesStart.max() * (1 + band)) + tick,
						at + "low attention Traces at " + low.stageAt(Stage.TRACES));
				helper.assertTrue(low.stageAt(Stage.PROXIMITY) >= proximity && low.stageAt(Stage.PROXIMITY) <= (long) (rules.proximityStart.max() * (1 + band)) + tick,
						at + "low attention Proximity at " + low.stageAt(Stage.PROXIMITY));
			}
		}
		helper.succeed();
	}

	@GameTest
	public void tellingOnlyFromTheEvent(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorBrain brain = new DirectorBrain(rules, SyntheticDeck.cards(), 100);
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		DirectorMemory memory = new DirectorMemory();
		RandomSource random = RandomSource.create(5);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(0, 0);
		for (int i = 0; i < 2000; i++) {
			clock = clock.plus(rules.tickInterval);
			brain.step(memory, clock, env, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(env.stage == Stage.PROXIMITY, "no timed fallback into Telling (D-006), got " + env.stage);

		// D-041: writing near his traces counts as telling but never moves the stage, in any stage before Telling.
		DirectorBrain.Clock day5 = new DirectorBrain.Clock(clock.playTicks(), 5 * DirectorBrain.DAY_TICKS + 100);
		for (Stage before : List.of(Stage.ALONE, Stage.TRACES, Stage.PROXIMITY)) {
			env.stage = before;
			memory.lastTellingDay = -1;
			memory.obeyCounted = true;
			helper.assertFalse(brain.onTelling(memory, day5, env, false, DirectorBrain.Recorder.NONE), "writing near his traces started Telling in " + before);
			helper.assertTrue(env.stage == before, "writing near his traces moved " + before + " to " + env.stage);
			helper.assertTrue(memory.lastTellingDay == 5 && !memory.obeyCounted, "writing near his traces did not count as telling");
		}
		// Only naming him starts Stage 3.
		memory.lastTellingDay = -1;
		helper.assertTrue(brain.onTelling(memory, day5, env, true, DirectorBrain.Recorder.NONE) && env.stage == Stage.TELLING,
				"naming him did not start Telling, got " + env.stage);
		helper.assertTrue(memory.lastTellingDay == 5, "naming him did not count as telling");
		helper.assertFalse(brain.onTelling(memory, day5, env, true, DirectorBrain.Recorder.NONE), "Telling started twice");
		env.stage = Stage.REMOVAL;
		helper.assertFalse(brain.onTelling(memory, day5, env, true, DirectorBrain.Recorder.NONE) || env.stage != Stage.REMOVAL, "Telling replaced Removal");
		helper.succeed();
	}

	@GameTest
	public void signaturesWaitForTheirStage(GameTestHelper helper) {
		CardInfo early = card("sig_from_alone", Tier.SIGNATURE, Stage.ALONE, false);
		CardInfo traces = card("sig_from_traces", Tier.SIGNATURE, Stage.TRACES, false);
		CardInfo late = card("sig_from_proximity", Tier.SIGNATURE, Stage.PROXIMITY, false);
		List<CardInfo> deck = List.of(early, traces, late);
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.minAnyGap = 0;
		helper.assertTrue(rules.signatureMinStage == Stage.TRACES, "signatures start in " + rules.signatureMinStage + ", expected Traces by default");
		DirectorBrain brain = new DirectorBrain(rules, deck, 10);
		helper.assertTrue(brain.minStage(early) == Stage.TRACES && brain.minStage(late) == Stage.PROXIMITY, "minimum stages");
		int ticks = (int) (4 * rules.hourTicks / rules.tickInterval);

		ScriptEnv alone = DirectorTestSupport.drive(brain, DirectorTestSupport.pinned(rules), Stage.ALONE, ticks, 3, DirectorBrain.Recorder.NONE);
		helper.assertTrue(alone.fired.isEmpty(), "a signature fired in Alone: " + alone.fired);
		ScriptEnv inTraces = DirectorTestSupport.drive(brain, DirectorTestSupport.pinned(rules), Stage.TRACES, ticks, 3, DirectorBrain.Recorder.NONE);
		helper.assertTrue(new HashSet<>(inTraces.fired).equals(Set.of(early.id(), traces.id())) && inTraces.fired.size() == 2,
				"Traces should fire the two Traces-ready signatures once each: " + inTraces.fired);
		ScriptEnv inProximity = DirectorTestSupport.drive(brain, DirectorTestSupport.pinned(rules), Stage.PROXIMITY, ticks, 3, DirectorBrain.Recorder.NONE);
		helper.assertTrue(inProximity.fired.size() == 3 && new HashSet<>(inProximity.fired).size() == 3, "Proximity should fire all three once: " + inProximity.fired);

		// The config minimum applies on top of each card's own stage.
		rules.signatureMinStage = Stage.PROXIMITY;
		DirectorBrain strict = new DirectorBrain(rules, deck, 10);
		ScriptEnv strictTraces = DirectorTestSupport.drive(strict, DirectorTestSupport.pinned(rules), Stage.TRACES, ticks, 3, DirectorBrain.Recorder.NONE);
		helper.assertTrue(strictTraces.fired.isEmpty(), "signatures fired in Traces with a Proximity minimum: " + strictTraces.fired);
		helper.succeed();
	}

	@GameTest
	public void decksNeverRepeatUntilExhausted(GameTestHelper helper) {
		int repeats = 0;
		int refills = 0;
		List<CardInfo> synthetic = SyntheticDeck.cards();
		for (Playthrough p : playthroughs()) {
			Map<Tier, Set<String>> cycle = new EnumMap<>(Tier.class);
			Map<Tier, String> last = new EnumMap<>(Tier.class);
			Map<Tier, Long> lastProgress = new EnumMap<>(Tier.class);
			Set<String> everFired = new HashSet<>();
			String previous = null;
			for (Tier tier : Tier.values()) {
				cycle.put(tier, new HashSet<>());
			}
			for (Event e : p.result().events) {
				if (e.kind() == Kind.DRAW) {
					lastProgress.put(e.tier(), e.dayTicks());
				} else if (e.kind() == Kind.REFILL) {
					helper.assertFalse(cycle.get(e.tier()).isEmpty(), where(p) + "refilled a deck with nothing fired");
					// No card comes back while one it has not fired could still be drawn in this stage and profile,
					// unless the deck drew nothing for the whole stall.
					List<String> waiting = synthetic.stream().filter(card -> card.tier() == e.tier() && !cycle.get(e.tier()).contains(card.id())
							&& !DirectorTestSupport.outOfStageOrProfile(p.rules(), card, e.stage(), everFired)).map(CardInfo::id).toList();
					long stuck = e.dayTicks() - lastProgress.getOrDefault(e.tier(), p.result().startDayTicks);
					helper.assertTrue(waiting.isEmpty() || stuck >= p.rules().deckStall,
							where(p) + e.tier() + " deck reshuffled after " + stuck + " day ticks while " + waiting + " could still be drawn");
					lastProgress.put(e.tier(), e.dayTicks());
					cycle.get(e.tier()).clear();
					refills++;
				} else if (e.kind() == Kind.FIRE) {
					helper.assertFalse(e.cardId().equals(previous), where(p) + e.cardId() + " fired twice in a row");
					helper.assertFalse(e.cardId().equals(last.get(e.tier())), where(p) + e.cardId() + " twice in a row in its deck");
					helper.assertTrue(cycle.get(e.tier()).add(e.cardId()), where(p) + e.cardId() + " repeated before its deck ran out");
					if (!everFired.add(e.cardId())) {
						repeats++;
					}
					last.put(e.tier(), e.cardId());
					previous = e.cardId();
				}
			}
		}
		helper.assertTrue(refills > 0 && repeats > 0, "decks never ran out (refills " + refills + ", repeats " + repeats + "): the test proved nothing");

		// A deck where every card always fits: each cycle is a full permutation before anything comes back.
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1000;
		rules.minAnyGap = 0;
		List<CardInfo> deck = List.of(card("a", Tier.AMBIENT, Stage.ALONE, false), card("b", Tier.AMBIENT, Stage.ALONE, false),
				card("c", Tier.AMBIENT, Stage.ALONE, false), card("d", Tier.AMBIENT, Stage.ALONE, false));
		DirectorBrain brain = new DirectorBrain(rules, deck, 100);
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		DirectorMemory memory = new DirectorMemory();
		RandomSource random = RandomSource.create(77);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(0, 0);
		while (env.fired.size() < 40 && clock.playTicks() < 100 * rules.hourTicks) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0; // no quiet in this check
			brain.step(memory, clock, env, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(env.fired.size() == 40, "only " + env.fired.size() + " fires");
		for (int i = 0; i + 4 <= env.fired.size(); i += 4) {
			helper.assertTrue(new HashSet<>(env.fired.subList(i, i + 4)).size() == 4, "cycle " + env.fired.subList(i, i + 4) + " is not a full deck");
		}
		helper.succeed();
	}

	/** Watches one deck: a repeat inside a cycle, or a reshuffle before every drawable card fired, is a violation. */
	private static final class DeckWatch implements DirectorBrain.Recorder {
		final Set<String> drawable;
		final Set<String> cycle = new HashSet<>();
		final List<String> violations = new ArrayList<>();
		final List<Long> refills = new ArrayList<>();
		final List<Long> fireDays = new ArrayList<>();

		DeckWatch(String... drawable) {
			this.drawable = Set.of(drawable);
		}

		@Override
		public void fired(DirectorBrain.Clock clock, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
			if (!cycle.add(card.id())) {
				violations.add(card.id() + " repeated while " + missing() + " had not fired");
			}
			fireDays.add(clock.dayTicks());
		}

		@Override
		public void refilled(DirectorBrain.Clock clock, Tier tier) {
			if (!cycle.containsAll(drawable)) {
				violations.add("reshuffled at " + clock.dayTicks() + " while " + missing() + " had not fired");
			}
			refills.add(clock.dayTicks());
			cycle.clear();
		}

		Set<String> missing() {
			Set<String> out = new HashSet<>(drawable);
			out.removeAll(cycle);
			return out;
		}
	}

	@GameTest
	public void decksWaitForGatedCards(GameTestHelper helper) {
		CardInfo sighting = card("s", Tier.AMBIENT, Stage.ALONE, false, CardTag.SIGHTING);
		CardInfo accident = card("x", Tier.AMBIENT, Stage.ALONE, false, CardTag.ACCIDENT);
		CardInfo telling = card("t", Tier.AMBIENT, Stage.TELLING, false);
		CardInfo text = card("w", Tier.AMBIENT, Stage.ALONE, false, CardTag.TEXT);
		CardInfo a = card("a", Tier.AMBIENT, Stage.ALONE, false);
		CardInfo b = card("b", Tier.AMBIENT, Stage.ALONE, false);
		CardInfo c = card("c", Tier.AMBIENT, Stage.ALONE, false);
		List<CardInfo> deck = List.of(a, b, c, sighting, accident, telling, text);
		DirectorRules rules = deckRules();
		int ticksPerHour = (int) (rules.hourTicks / rules.tickInterval);

		// 1. The accident card waits for its gate and the sighting is mostly skipped by its weight: nothing comes back
		// until both have fired. Telling-only and zero-weight cards never hold the deck.
		rules.firstAccident = 3 * rules.hourTicks; // two hours into the run
		rules.deckStall = Long.MAX_VALUE / 4;
		DeckWatch watch = new DeckWatch("a", "b", "c", "s", "x");
		DirectorMemory memory = DirectorTestSupport.pinned(rules);
		ScriptEnv env = DirectorTestSupport.drive(new DirectorBrain(rules, deck, 100), memory, Stage.PROXIMITY, 6 * ticksPerHour, 21, watch);
		helper.assertTrue(watch.violations.isEmpty(), "gated: " + watch.violations);
		int xAt = env.fired.indexOf("x");
		helper.assertTrue(xAt == 4 && new HashSet<>(env.fired.subList(0, 4)).equals(Set.of("a", "b", "c", "s")),
				"before the accident gate opened: " + env.fired);
		helper.assertTrue(watch.refills.size() >= 1 && env.fired.size() > 6, "the deck never came back after the gated card fired: " + env.fired);
		helper.assertFalse(env.fired.contains("t") || env.fired.contains("w"), "a card outside its stage fired: " + env.fired);

		// 2. A card that never gets past its gate holds the deck back only for the stall (default 3 in-game days).
		DirectorRules stall = deckRules();
		stall.firstAccident = Long.MAX_VALUE / 4;
		helper.assertTrue(stall.deckStall == 3 * DirectorBrain.DAY_TICKS, "default stall " + stall.deckStall + " day ticks");
		DeckWatch stallWatch = new DeckWatch("a", "b", "c");
		ScriptEnv stallEnv = DirectorTestSupport.drive(new DirectorBrain(stall, List.of(a, b, c, accident, telling, text), 100),
				DirectorTestSupport.pinned(stall), Stage.PROXIMITY, 4 * ticksPerHour, 21, stallWatch);
		helper.assertTrue(stallWatch.violations.isEmpty(), "stall: " + stallWatch.violations);
		helper.assertFalse(stallEnv.fired.contains("x"), "the gated card fired");
		helper.assertTrue(!stallWatch.refills.isEmpty() && stallEnv.fired.size() > 3, "the stalled deck never reshuffled: " + stallEnv.fired);
		long lastNew = stallWatch.fireDays.get(2);
		helper.assertTrue(stallWatch.refills.getFirst() - lastNew >= stall.deckStall,
				"reshuffled " + (stallWatch.refills.getFirst() - lastNew) + " day ticks after the last new card, before the stall");

		// 3. When only the stage rules the rest out, the deck comes back at once (never the last card first).
		DirectorRules open = deckRules();
		open.deckStall = Long.MAX_VALUE / 4;
		DeckWatch openWatch = new DeckWatch("a", "b");
		ScriptEnv openEnv = DirectorTestSupport.drive(new DirectorBrain(open, List.of(a, b, telling, text), 100),
				DirectorTestSupport.pinned(open), Stage.PROXIMITY, 20, 21, openWatch);
		helper.assertTrue(openWatch.violations.isEmpty() && openEnv.fired.size() >= 15 && openWatch.refills.size() >= 6,
				"stage-only run out: fired " + openEnv.fired + ", refills " + openWatch.refills.size() + ", " + openWatch.violations);
		for (int i = 1; i < openEnv.fired.size(); i++) {
			helper.assertFalse(openEnv.fired.get(i).equals(openEnv.fired.get(i - 1)), "the same card twice in a row: " + openEnv.fired);
		}
		helper.succeed();
	}

	/** Ambient fires as often as the gates allow; sightings are rarely kept and TEXT is out of Proximity. */
	private static DirectorRules deckRules() {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1000;
		rules.minAnyGap = 0;
		rules.sightingsPerDayMax = 100;
		Map<CardTag, Double> weights = new EnumMap<>(CardTag.class);
		weights.put(CardTag.SIGHTING, 0.05);
		weights.put(CardTag.TEXT, 0.0);
		rules.stageTagWeights.put(Stage.PROXIMITY, weights);
		return rules;
	}

	@GameTest
	public void quietFromTensionRaisedElsewhere(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorBrain brain = new DirectorBrain(rules, List.of(), 10); // nothing can fire: only outside tension counts
		DirectorMemory memory = DirectorTestSupport.pinned(rules);
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, rules.tensionThreshold - 10, rules.attentionNeutral);
		RandomSource random = RandomSource.create(8);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		memory.lastStepPlay = clock.playTicks();
		clock = clock.plus(rules.tickInterval);
		brain.step(memory, clock, env, random, DirectorBrain.Recorder.NONE);
		helper.assertFalse(brain.inQuiet(memory, clock), "a quiet started below the threshold");

		// Another system raises tension between two director ticks (Attention.raiseTension).
		env.addTension(20, "elsewhere");
		clock = clock.plus(rules.tickInterval);
		brain.step(memory, clock, env, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(brain.inQuiet(memory, clock) && memory.quietCount == 1, "tension raised elsewhere did not start a quiet");
		helper.assertTrue(env.fireCalls == 0 && env.tension <= rules.tensionAfterQuiet + 1e-9, "fires " + env.fireCalls + ", tension " + env.tension);
		long days = (memory.quietUntilDayTicks - clock.dayTicks()) / DirectorBrain.DAY_TICKS;
		helper.assertTrue(days >= rules.quietMinDays && days <= rules.quietMaxDays, "quiet of " + days + " days");

		// A running quiet is never restarted or stretched.
		long until = memory.quietUntilDayTicks;
		env.addTension(100, "elsewhere");
		clock = clock.plus(rules.tickInterval);
		brain.step(memory, clock, env, random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(memory.quietUntilDayTicks == until && memory.quietCount == 1, "the quiet restarted while running");
		helper.succeed();
	}

	@GameTest
	public void minorAndMajorSpacing(GameTestHelper helper) {
		int minors = 0;
		int majors = 0;
		for (Playthrough p : playthroughs()) {
			DirectorRules rules = p.rules();
			long lastMinor = Long.MIN_VALUE / 4;
			List<Long> majorTimes = new ArrayList<>();
			for (Event e : fires(p.result(), e -> true)) {
				if (e.tier() == Tier.MINOR) {
					helper.assertTrue(e.play() - lastMinor >= rules.minorGap, where(p) + "minors " + (e.play() - lastMinor) + " ticks apart");
					lastMinor = e.play();
					minors++;
				} else if (e.tier() == Tier.MAJOR || e.tier() == Tier.SIGNATURE) {
					majorTimes.add(e.play());
					majors++;
				}
			}
			for (long t : majorTimes) {
				long inHour = majorTimes.stream().filter(o -> o >= t && o < t + rules.hourTicks).count();
				helper.assertTrue(inHour <= 1, where(p) + inHour + " majors in the real hour from " + t);
			}
		}
		helper.assertTrue(minors >= 100 && majors >= 18, "too few fires to prove spacing: minors " + minors + ", majors " + majors);
		helper.succeed();
	}

	@GameTest
	public void joinGraceAndNoMajorOnDayZero(GameTestHelper helper) {
		for (Playthrough p : playthroughs()) {
			long session = -1;
			for (Event e : p.result().events) {
				if (e.kind() == Kind.SESSION_START) {
					session = e.play();
				} else if (e.kind() == Kind.FIRE) {
					helper.assertTrue(e.play() - session >= p.rules().joinGrace, where(p) + e.cardId() + " fired " + (e.play() - session) + " ticks after joining");
					if (e.tier() == Tier.MAJOR || e.tier() == Tier.SIGNATURE) {
						helper.assertTrue(e.day() >= p.rules().noMajorBeforeDay, where(p) + e.cardId() + " on day " + e.day());
					}
				}
			}
		}

		// Pressure: Proximity from the first tick, a major already due, short sessions, none of them empty,
		// everything always fits.
		DirectorRules rules = rules(Tempo.EARLY, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 60;
		rules.emptySessionChance = 0;
		DirectorMemory memory = new DirectorMemory();
		memory.nextMajorDue = 0;
		DirectorSim.Params params = DirectorTestSupport.params(rules, 3, rules.attentionNeutral, 3);
		params.fitChance = 1;
		params.noSpotChance = 0;
		params.sessionMin = params.sessionMax = 12 * 60 * 20 * rules.hourTicks / 72000;
		DirectorSim.Result result = DirectorSim.run(rules, SyntheticDeck.cards(), 100, memory, Stage.PROXIMITY, 0, new DirectorBrain.Clock(0, 0), params);
		long session = -1;
		boolean firedOnDayZero = false;
		long firstMajor = -1;
		for (Event e : result.events) {
			if (e.kind() == Kind.SESSION_START) {
				session = e.play();
			} else if (e.kind() == Kind.FIRE) {
				helper.assertTrue(e.play() - session >= rules.joinGrace, "pressure: " + e.cardId() + " in the first minutes after joining");
				firedOnDayZero |= e.day() == 0;
				if (e.tier() == Tier.MAJOR && firstMajor < 0) {
					firstMajor = e.play();
					helper.assertTrue(e.day() >= rules.noMajorBeforeDay, "pressure: a major on day " + e.day());
				}
			}
		}
		helper.assertTrue(firedOnDayZero, "pressure: nothing fired on day 0, the gate was not tested");
		helper.assertTrue(firstMajor >= 0, "pressure: no major after day 0, the gate was not tested");
		helper.succeed();
	}

	@GameTest
	public void forcedQuietFollowsThreshold(GameTestHelper helper) {
		Set<Long> lengths = new HashSet<>();
		int quiets = 0;
		for (Playthrough p : playthroughs()) {
			DirectorRules rules = p.rules();
			List<Event> events = p.result().events;
			long quietUntil = -1;
			for (int i = 0; i < events.size(); i++) {
				Event e = events.get(i);
				if (e.kind() == Kind.FIRE) {
					helper.assertTrue(e.dayTicks() >= quietUntil, where(p) + e.cardId() + " fired during a quiet");
					if (e.value() >= rules.tensionThreshold) {
						Event next = i + 1 < events.size() ? events.get(i + 1) : null;
						helper.assertTrue(next != null && next.kind() == Kind.QUIET && next.play() == e.play(),
								where(p) + "tension " + e.value() + " after " + e.cardId() + " without a quiet");
					}
				} else if (e.kind() == Kind.QUIET) {
					long days = (e.until() - e.dayTicks()) / DirectorBrain.DAY_TICKS;
					helper.assertTrue(e.value() >= rules.tensionThreshold, where(p) + "quiet at tension " + e.value());
					helper.assertTrue(days >= rules.quietMinDays && days <= rules.quietMaxDays, where(p) + "quiet of " + days + " days");
					quietUntil = e.until();
					lengths.add(days);
					quiets++;
				}
			}
		}
		helper.assertTrue(quiets >= 10 && lengths.size() >= 3, "quiets " + quiets + ", lengths " + lengths + ": not random or never reached");

		// Direct: below the threshold nothing happens; crossing it starts a quiet and spends the tension.
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorBrain brain = new DirectorBrain(rules, List.of(card("m", Tier.MAJOR, Stage.ALONE, false)), 10);
		DirectorMemory memory = new DirectorMemory();
		memory.nextMajorDue = 0;
		memory.sessionStart = 0;
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, rules.tensionThreshold - rules.tensionFor(Tier.MAJOR) - 1, rules.attentionNeutral);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		memory.lastStepPlay = clock.playTicks();
		brain.step(memory, clock, env, RandomSource.create(1), DirectorBrain.Recorder.NONE);
		helper.assertTrue(env.fired.size() == 1 && !brain.inQuiet(memory, clock), "a fire below the threshold started a quiet");
		env.tension = rules.tensionThreshold - rules.tensionFor(Tier.MAJOR) + 0.5; // a little decays before the fire
		memory.cycleFired.get(Tier.MAJOR).clear();
		memory.lastFired.clear();
		memory.nextMajorDue = 0;
		memory.lastMajorOrSignature = DirectorMemory.NEVER;
		memory.lastFireAny = DirectorMemory.NEVER;
		clock = clock.plus(rules.tickInterval);
		brain.step(memory, clock, env, RandomSource.create(2), DirectorBrain.Recorder.NONE);
		helper.assertTrue(env.fired.size() == 2 && brain.inQuiet(memory, clock), "reaching the threshold did not start a quiet");
		helper.assertTrue(env.tension <= rules.tensionAfterQuiet + 1e-9, "tension stayed at " + env.tension + " after the quiet started");
		helper.succeed();
	}

	@GameTest
	public void emptySessionsFromTracesOn(GameTestHelper helper) {
		int empty = 0;
		int long45 = 0;
		int aloneSessions = 0;
		for (Playthrough p : playthroughs()) {
			for (Event e : p.result().events) {
				if (e.kind() == Kind.SESSION_START) {
					if (e.flag()) {
						helper.assertTrue(e.stage().atLeast(Stage.TRACES), where(p) + "empty session in " + e.stage());
					} else if (e.stage() == Stage.ALONE) {
						aloneSessions++;
					}
				}
			}
			for (long[] span : p.result().emptySessions()) {
				empty++;
				if (span[1] - span[0] >= p.rules().emptySessionMin) {
					long45++;
				}
				helper.assertTrue(fires(p.result(), e -> e.play() >= span[0] && e.play() < span[1]).isEmpty(),
						where(p) + "a fire inside the empty session from " + span[0]);
			}
		}
		helper.assertTrue(aloneSessions > 0, "no session started in Alone: the 'from Traces on' rule was not tested");
		helper.assertTrue(empty >= 6 && long45 >= 4, "empty sessions " + empty + " (45+ min: " + long45 + ")");
		helper.succeed();
	}

	@GameTest
	public void fakeRatioAboutOneThird(GameTestHelper helper) {
		int fakeable = 0;
		int fakes = 0;
		for (Playthrough p : playthroughs()) {
			for (Event e : fires(p.result(), e -> true)) {
				CardInfo card = SyntheticDeck.cards().stream().filter(c -> c.id().equals(e.cardId())).findFirst().orElseThrow();
				helper.assertTrue(card.hasFake() || !e.fake(), where(p) + e.cardId() + " fired as a fake but has none");
				if (card.hasFake()) {
					fakeable++;
					fakes += e.fake() ? 1 : 0;
				}
			}
		}
		double ratio = fakes / (double) fakeable;
		helper.assertTrue(fakeable >= 200, "only " + fakeable + " fakeable fires");
		helper.assertTrue(ratio > 0.26 && ratio < 0.41, String.format("fake ratio %.3f (%d of %d), expected about 1/3", ratio, fakes, fakeable));
		helper.succeed();
	}

	@GameTest
	public void everyLimitHoldsOverTwentyHours(GameTestHelper helper) {
		for (Playthrough p : playthroughs()) {
			DirectorRules rules = p.rules();
			helper.assertTrue(p.result().violations.isEmpty(), where(p) + p.result().violations);
			int aloneAmbients = 0;
			Set<String> once = new HashSet<>();
			for (Event e : fires(p.result(), e -> true)) {
				CardInfo card = SyntheticDeck.cards().stream().filter(c -> c.id().equals(e.cardId())).findFirst().orElseThrow();
				if (card.has(CardTag.ACCIDENT)) {
					helper.assertTrue(e.play() >= rules.firstAccident, where(p) + "accident " + e.cardId() + " at " + e.play());
				}
				if (e.stage() == Stage.ALONE) {
					helper.assertTrue(e.tier() == Tier.AMBIENT && ++aloneAmbients <= rules.aloneMaxAmbient, where(p) + "too much in Alone: " + e.cardId());
				}
				if (e.tier() == Tier.SIGNATURE) {
					helper.assertTrue(once.add(e.cardId()), where(p) + "signature " + e.cardId() + " twice");
					WorldProfile profile = rules.profile;
					switch (e.cardId()) {
						case CardInfo.SIGNATURE_STILL_BURNING -> helper.assertTrue(profile.hasStillBurning(), where(p) + "still burning in a " + profile.signature() + " world");
						case CardInfo.SIGNATURE_HOUSE_ELSEWHERE -> helper.assertTrue(profile.hasHouseCopy() && e.day() >= rules.houseCopyMinDay,
								where(p) + "house elsewhere on day " + e.day() + " in a " + profile.signature() + " world");
						case CardInfo.SIGNATURE_CROSS_ROW -> helper.assertTrue(profile.signature() == Signature.CROSS_ROW, where(p) + "cross row in a " + profile.signature() + " world");
						default -> {
						}
					}
				}
			}
			long signatures = fires(p.result(), e -> e.tier() == Tier.SIGNATURE).size();
			helper.assertTrue(signatures == 2, where(p) + signatures + " signature fires, expected the world's own plus the other one");
		}
		helper.succeed();
	}

	@GameTest
	public void heldCardsWaitAndGoBack(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 1000;
		CardInfo never = card("never_fits", Tier.AMBIENT, Stage.ALONE, false);
		CardInfo spot = card("needs_spot", Tier.AMBIENT, Stage.ALONE, false);
		CardInfo easy = card("easy", Tier.AMBIENT, Stage.ALONE, false);
		DirectorBrain brain = new DirectorBrain(rules, List.of(never, spot, easy), 100);
		ScriptEnv env = new ScriptEnv(Stage.PROXIMITY, 0, rules.attentionNeutral);
		env.fits = card -> card != never;
		Map<String, Integer> noSpots = new HashMap<>();
		env.result = card -> card == spot && noSpots.merge(card.id(), 1, Integer::sum) <= 3 ? FireResult.NO_SPOT : FireResult.FIRED;
		DirectorMemory memory = new DirectorMemory();
		RandomSource random = RandomSource.create(11);
		List<String> drawn = new ArrayList<>();
		List<long[]> gaveUp = new ArrayList<>();
		DirectorBrain.Recorder rec = new DirectorBrain.Recorder() {
			@Override
			public void drew(DirectorBrain.Clock clock, CardInfo card) {
				drawn.add(card.id());
			}

			@Override
			public void gaveUp(DirectorBrain.Clock clock, Tier tier, String cardId, String why) {
				gaveUp.add(new long[] {clock.playTicks(), memory.heldSince.getOrDefault(tier, -1L)});
			}
		};
		DirectorBrain.Clock clock = new DirectorBrain.Clock(0, 0);
		long heldNeverAt = -1;
		for (int i = 0; i < 2000 && (gaveUp.isEmpty() || !env.fired.contains("needs_spot")); i++) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			brain.step(memory, clock, env, random, rec);
			if ("never_fits".equals(memory.held(Tier.AMBIENT)) && heldNeverAt < 0) {
				heldNeverAt = memory.heldSince.get(Tier.AMBIENT);
			}
		}
		helper.assertTrue(env.fired.contains("needs_spot"), "the NO_SPOT card never fired");
		helper.assertTrue(drawn.stream().filter("needs_spot"::equals).count() == 1, "NO_SPOT put the card back instead of holding it: " + drawn);
		helper.assertTrue(heldNeverAt >= 0 && !gaveUp.isEmpty(), "the card that never fits was never held and given up");
		helper.assertTrue(gaveUp.getFirst()[0] - heldNeverAt >= rules.holdGiveUp, "gave up after " + (gaveUp.getFirst()[0] - heldNeverAt) + " ticks");
		helper.assertFalse(memory.cycleFired(Tier.AMBIENT).contains("never_fits"), "the given-up card did not go back into its deck");
		helper.assertFalse(env.fired.contains("never_fits"), "a card fired although its moment never fit");
		helper.succeed();
	}

	@GameTest
	public void sightingsDropOffInTelling(GameTestHelper helper) {
		int telling = 0;
		int proximity = 0;
		Map<Tier, Integer> tellingByTier = new EnumMap<>(Tier.class);
		for (long seed : SEEDS) {
			DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
			for (Stage stage : List.of(Stage.TELLING, Stage.PROXIMITY)) {
				DirectorSim.Result result = DirectorSim.run(rules, SyntheticDeck.cards(), 100, new DirectorMemory(), stage, 0,
						new DirectorBrain.Clock(0, 0), DirectorTestSupport.params(rules, seed, rules.attentionNeutral, HOURS));
				List<Event> sightings = fires(result, e -> SyntheticDeck.cards().stream().anyMatch(c -> c.id().equals(e.cardId()) && c.has(CardTag.SIGHTING)));
				if (stage == Stage.TELLING) {
					telling += sightings.size();
					sightings.forEach(e -> tellingByTier.merge(e.tier(), 1, Integer::sum));
				} else {
					proximity += sightings.size();
				}
			}
		}
		helper.assertTrue(proximity >= 8 && telling * 5 <= proximity, "sightings: Telling " + telling + " " + tellingByTier + ", Proximity " + proximity);
		helper.succeed();
	}

	@GameTest
	public void forcedFireIsRecorded(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		CardInfo sighting = card("forced_card", Tier.MAJOR, Stage.PROXIMITY, true, CardTag.SIGHTING);
		DirectorBrain brain = new DirectorBrain(rules, List.of(sighting), 10);
		DirectorMemory memory = new DirectorMemory();
		DirectorBrain.Clock clock = new DirectorBrain.Clock(1234, 100);
		brain.recordForced(memory, clock, sighting, true, Stage.ALONE, DirectorBrain.Recorder.NONE);
		DirectorMemory.HistoryEntry last = memory.history().getLast();
		helper.assertTrue(last.cardId().equals("forced_card") && last.forced() && last.fake() && last.playTicks() == 1234, "history: " + memory.history());
		helper.assertTrue(memory.lastTagFire(CardTag.SIGHTING) == 1234, "tag time not recorded");
		helper.assertTrue(memory.lastFireAny == DirectorMemory.NEVER && memory.lastMajorOrSignature == DirectorMemory.NEVER, "a forced fire moved the pacing timers");
		helper.assertTrue(memory.cycleFired(Tier.MAJOR).contains("forced_card"), "a forced fire did not count in its deck");
		helper.succeed();
	}

	@GameTest
	public void memoryRoundTripsAndDryRunsChangeNothing(GameTestHelper helper) {
		Playthrough p = playthroughs().getFirst();
		DirectorMemory played = p.result().memory;
		DirectorMemory copy = DirectorMemory.fromTag(played.toTag());
		helper.assertTrue(copy.toTag().equals(played.toTag()), "memory changed through a save and load");
		helper.assertTrue(copy.history().equals(played.history()) && copy.quietUntilDayTicks() == played.quietUntilDayTicks(), "history or quiet lost");

		// A dry run never touches the memory it starts from; advancing time alone never fires anything.
		DirectorMemory before = played.copy();
		DirectorSim.run(p.rules(), SyntheticDeck.cards(), 100, played, p.result().stage, p.result().tension,
				new DirectorBrain.Clock(p.result().endPlay, p.result().endPlay), DirectorTestSupport.params(p.rules(), 9, 10, 5));
		helper.assertTrue(before.toTag().equals(played.toTag()), "a dry run changed the live memory");
		DirectorBrain brain = new DirectorBrain(p.rules(), SyntheticDeck.cards(), 1000);
		ScriptEnv env = new ScriptEnv(Stage.ALONE, 50, p.rules().attentionNeutral);
		DirectorMemory fresh = new DirectorMemory();
		RandomSource random = RandomSource.create(4);
		for (long t = 0; t <= 30 * DirectorBrain.DAY_TICKS; t += p.rules().tickInterval) {
			brain.advanceTime(fresh, new DirectorBrain.Clock(t, t), env, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(env.fireCalls == 0 && fresh.history().isEmpty(), "advancing time fired a card");
		helper.assertTrue(env.stage == Stage.PROXIMITY && env.tension < 50, "time alone did not move the stage or decay tension: " + env.stage + " " + env.tension);
		helper.succeed();
	}

	/**
	 * 4b's rates on the multi-seed average of the scripted playthrough (registered deck, 20 h per seed, code
	 * defaults): at the low end of every range, never below its floor, and the silence still there. One seed is
	 * one world's dice, so only the pooled rates are asserted; every run's hard limits hold too.
	 */
	@GameTest(maxTicks = 2000)
	public void softRatesLandAtTheLowEndOfFourB(GameTestHelper helper) {
		Map<Tier, Long> deck = new EnumMap<>(Tier.class);
		DirectorApi.registeredCards().forEach(card -> deck.merge(card.tier(), 1L, Long::sum));
		helper.assertTrue(deck.getOrDefault(Tier.MINOR, 0L) >= 8 && deck.getOrDefault(Tier.MAJOR, 0L) >= 8,
				"the registered deck is too small for the rates to mean anything: " + deck);
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> runs = DirectorTestSupport.registryRuns(tempo);
			DirectorSim.Rates rates = DirectorSim.Rates.of(runs);
			DirectorRules rules = runs.getFirst().rules;
			double hour = rules.hourTicks;
			String at = tempo + " over " + rates.runs() + " seeds: ";
			A1016_02.LOGGER.info("[a1016] director soft rates {}: {}", tempo, rates.summary());
			for (DirectorSim.Result run : runs) {
				helper.assertTrue(run.violations.isEmpty(), at + "seed " + run.params.seed + " broke a limit: " + run.violations);
			}
			helper.assertTrue(rates.runs() >= 5, at + "fewer than 5 seeds");

			// Traces: about one ambient per hour; the tempo may shift a rate by 40%, and never above the +50% band.
			helper.assertTrue(rates.tracesAmbientsPerHour() >= 0.6 * rules.tracesAmbientPerHour
					&& rates.tracesAmbientsPerHour() <= 1.5 * rules.tracesAmbientPerHour, at + "Traces ambients " + rates.summary());

			// Proximity: one or two minors per active hour; overall at least 0.7 (slow burn) or 0.6 (40% slower).
			double minorFloor = tempo == Tempo.SLOW_BURN ? 0.7 : 0.6;
			helper.assertTrue(rates.minorsPerActiveHour() >= rules.minorsPerHourMin && rates.minorsPerActiveHour() <= rules.minorsPerHourMax,
					at + "minors while active " + rates.summary());
			helper.assertTrue(rates.minorsPerHour() >= minorFloor && rates.minorsPerHour() <= rules.minorsPerHourMax,
					at + "minors overall below " + minorFloor + "/h: " + rates.summary());

			// A major every 2 to 4 hours times the tempo, over all the time in Proximity.
			helper.assertTrue(rates.hoursPerMajor() <= rules.majorEvery.max() / hour && rates.hoursPerMajor() >= rules.majorEvery.min() / hour,
					at + String.format(Locale.ROOT, "a major every %.2f h, outside %.1f to %.1f h", rates.hoursPerMajor(),
							rules.majorEvery.min() / hour, rules.majorEvery.max() / hour));

			// Rarity: forced quiets and empty sessions still take a clear share, and from Traces on some whole
			// 45+ minute sessions have nothing at all.
			helper.assertTrue(rates.proximityQuietShare() >= 0.3, at + "Proximity is quiet or empty only " + rates.summary());
			helper.assertTrue(rates.quiets() >= 2 * rates.runs() && rates.emptySessions() >= rates.runs() / 2
					&& rates.silentSessions() >= rates.runs(), at + "too few silences: " + rates.summary());
		}
		helper.succeed();
	}

	@GameTest
	public void flagsParseDefensively(GameTestHelper helper) {
		helper.assertTrue(DirectorFlags.parse(Set.of()).equals(DirectorFlags.Values.NONE), "no flags should mean no silence and pace x1");
		helper.assertTrue(DirectorFlags.parse(Set.of("lore:f04_done", "ending:last_sighting")).equals(DirectorFlags.Values.NONE),
				"other workstreams' flags changed the director");

		// Silence: a day, -1 or the literal mean what they say; the latest day wins; bad values are ignored.
		helper.assertTrue(DirectorFlags.parse(Set.of("director:silence_until_day=12")).silenceUntilDay() == 12, "silence until day 12");
		helper.assertTrue(DirectorFlags.parse(Set.of("director:silence_until_day=-1")).silenceUntilDay() == DirectorFlags.FOREVER, "-1 is forever");
		helper.assertTrue(DirectorFlags.parse(Set.of("director:silence_forever")).silenceUntilDay() == DirectorFlags.FOREVER, "the literal is forever");
		helper.assertTrue(DirectorFlags.parse(Set.of("director:silence_until_day=5", "director:silence_until_day=9")).silenceUntilDay() == 9,
				"two silences: the later day should win");
		for (String bad : List.of("director:silence_until_day=", "director:silence_until_day=abc", "director:silence_until_day=-5",
				"director:silence_until_day=2.5")) {
			helper.assertTrue(DirectorFlags.parse(Set.of(bad)).equals(DirectorFlags.Values.NONE), "bad flag '" + bad + "' was not ignored");
		}
		helper.assertTrue(DirectorFlags.parse(Set.of("director:silence_until_day=abc", "director:silence_until_day=7")).silenceUntilDay() == 7,
				"a bad silence flag hid a good one");
		DirectorFlags.Values day3 = DirectorFlags.parse(Set.of("director:silence_until_day=3"));
		helper.assertTrue(day3.silenced(2) && !day3.silenced(3) && !DirectorFlags.Values.NONE.silenced(0), "silenced(day)");

		// Pace multiplier: 0.25 to 4, the largest wins; out of range is clamped, not a number is ignored.
		helper.assertTrue(DirectorFlags.parse(Set.of("director:pace_multiplier=2")).paceMultiplier() == 2, "pace x2");
		helper.assertTrue(DirectorFlags.parse(Set.of("director:pace_multiplier=0.25")).paceMultiplier() == 0.25, "pace x0.25 (the floor)");
		helper.assertTrue(DirectorFlags.parse(Set.of("director:pace_multiplier=4")).paceMultiplier() == 4, "pace x4 (the cap)");
		helper.assertTrue(DirectorFlags.parse(Set.of("director:pace_multiplier=0.5", "director:pace_multiplier=1.5")).paceMultiplier() == 1.5,
				"two multipliers: the larger should win");
		Map<String, Double> clamped = Map.of("director:pace_multiplier=5", 4.0, "director:pace_multiplier=Infinity", 4.0,
				"director:pace_multiplier=0.1", 0.25, "director:pace_multiplier=-2", 0.25, "director:pace_multiplier=0", 0.25);
		clamped.forEach((flag, expected) -> helper.assertTrue(DirectorFlags.parse(Set.of(flag)).paceMultiplier() == expected,
				"'" + flag + "' should clamp to " + expected + ", got " + DirectorFlags.parse(Set.of(flag)).paceMultiplier()));
		for (String bad : List.of("director:pace_multiplier=NaN", "director:pace_multiplier=fast", "director:pace_multiplier=")) {
			helper.assertTrue(DirectorFlags.parse(Set.of(bad)).paceMultiplier() == 1, "bad flag '" + bad + "' was not ignored");
		}

		// Applied (D-045): the typical major schedule divided down to the 1 h floor, decay multiplied; the minor and
		// major gaps never shorter (a slower pace lengthens them); join grace, day 0 and the first accident untouched.
		DirectorRules plain = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		long hour = plain.hourTicks;
		helper.assertTrue(plain.majorGap == hour && plain.minorGap == hour / 4, "default floors: major gap " + plain.majorGap + ", minor gap "
				+ plain.minorGap);
		for (double x : new double[] {1.6, 4, 0.5}) {
			DirectorRules paced = DirectorFlags.apply(rules(Tempo.SLOW_BURN, Signature.CROSS_ROW),
					DirectorFlags.parse(Set.of("director:pace_multiplier=" + x)));
			String at = "pace x" + x + ": ";
			long minorGap = Math.max(plain.minorGap, Math.round(plain.minorGap / x));
			long majorGap = Math.max(plain.majorGap, Math.round(plain.majorGap / x));
			helper.assertTrue(paced.minorGap == minorGap && paced.minorGap >= hour / 4, at + "minor gap " + paced.minorGap);
			helper.assertTrue(paced.majorGap == majorGap && paced.majorGap >= hour, at + "major gap " + paced.majorGap);
			long everyMin = Math.max(majorGap, Math.round(plain.majorEvery.min() / x));
			long everyMax = Math.max(everyMin, Math.round(plain.majorEvery.max() / x));
			helper.assertTrue(paced.majorEvery.min() == everyMin && paced.majorEvery.max() == everyMax && paced.majorEvery.min() >= hour,
					at + "majors every " + paced.majorEvery);
			helper.assertTrue(Math.abs(paced.tensionDecayPerTick - plain.tensionDecayPerTick * x) < 1e-12, at + "decay " + paced.tensionDecayPerTick);
			helper.assertTrue(paced.joinGrace == plain.joinGrace && paced.noMajorBeforeDay == plain.noMajorBeforeDay
					&& paced.firstAccident == plain.firstAccident && paced.minAnyGap == plain.minAnyGap, at + "a protecting limit was scaled");
		}
		DirectorRules unflagged = DirectorFlags.apply(rules(Tempo.SLOW_BURN, Signature.CROSS_ROW), DirectorFlags.Values.NONE);
		helper.assertTrue(unflagged.minorGap == plain.minorGap && unflagged.majorEvery.equals(plain.majorEvery)
				&& unflagged.silenceUntilDay == DirectorFlags.NO_SILENCE, "no flags changed the rules");
		helper.succeed();
	}

	@GameTest
	public void silenceFlagStopsEveryFire(GameTestHelper helper) {
		DirectorSim.Result control = flagged(Set.of(), 8);
		helper.assertTrue(fires(control, e -> e.day() < 5).size() >= 3, "control: too few fires before day 5 to prove the silence");

		DirectorSim.Result untilDay5 = flagged(Set.of("director:silence_until_day=5"), 8);
		helper.assertTrue(fires(untilDay5, e -> e.day() < 5).isEmpty(), "fired while silenced: " + fires(untilDay5, e -> e.day() < 5));
		helper.assertTrue(fires(untilDay5, e -> e.day() >= 5).size() >= 5, "pacing did not come back after the silence");
		helper.assertTrue(untilDay5.violations.isEmpty(), "limits after the silence: " + untilDay5.violations);

		for (String forever : List.of("director:silence_forever", "director:silence_until_day=-1")) {
			DirectorSim.Result silent = flagged(Set.of(forever), 8);
			helper.assertTrue(silent.count(Kind.FIRE) == 0, forever + ": " + silent.count(Kind.FIRE) + " fires, fakes included");
		}

		// The silence is one more gate: the brain says so, and clearing the flag opens it again.
		DirectorRules rules = DirectorFlags.apply(rules(Tempo.SLOW_BURN, Signature.CROSS_ROW), DirectorFlags.parse(Set.of("director:silence_forever")));
		DirectorBrain brain = new DirectorBrain(rules, SyntheticDeck.cards(), 10);
		DirectorMemory memory = DirectorTestSupport.pinned(rules);
		DirectorBrain.Clock day9 = new DirectorBrain.Clock(5 * rules.hourTicks, 9 * DirectorBrain.DAY_TICKS);
		helper.assertTrue("silenced for good".equals(brain.globalBlock(memory, day9)), "gate: " + brain.globalBlock(memory, day9));
		DirectorBrain cleared = new DirectorBrain(rules(Tempo.SLOW_BURN, Signature.CROSS_ROW), SyntheticDeck.cards(), 10);
		helper.assertTrue(cleared.globalBlock(memory, day9) == null, "cleared flag still blocks: " + cleared.globalBlock(memory, day9));
		helper.succeed();
	}

	@GameTest
	public void paceMultiplierKeepsTheHardFloors(GameTestHelper helper) {
		DirectorSim.Result normal = flagged(Set.of(), 30);
		long normalMajors = fires(normal, e -> e.tier() == Tier.MAJOR).size();
		long normalMinors = fires(normal, e -> e.tier() == Tier.MINOR).size();
		helper.assertTrue(normalMajors > 0 && normalMinors > 0, "x1: majors " + normalMajors + ", minors " + normalMinors);
		long hour = normal.rules.hourTicks;
		for (String x : List.of("1.6", "4")) {
			DirectorSim.Result fast = flagged(Set.of("director:pace_multiplier=" + x), 30);
			String at = "pace x" + x + ": ";
			long majors = fires(fast, e -> e.tier() == Tier.MAJOR).size();
			long minors = fires(fast, e -> e.tier() == Tier.MINOR).size();
			// Accidents come closer together: more majors and minors than at x1 ...
			helper.assertTrue(majors > normalMajors && minors > normalMinors, at + "majors " + majors + " (x1 " + normalMajors + "), minors " + minors
					+ " (x1 " + normalMinors + ")");
			// ... but never closer than 4b's floors: 1 real hour between majors, 15 real minutes between minors.
			long closestMajor = closest(fires(fast, e -> e.tier() == Tier.MAJOR || e.tier() == Tier.SIGNATURE));
			long closestMinor = closest(fires(fast, e -> e.tier() == Tier.MINOR));
			helper.assertTrue(closestMajor >= hour, at + "majors only " + closestMajor + " ticks apart");
			helper.assertTrue(closestMinor >= hour / 4, at + "minors only " + closestMinor + " ticks apart");
			// Join grace and no major on day 0 are replayed by the sim's own check with the unscaled numbers.
			helper.assertTrue(fast.violations.isEmpty() && fast.rules.joinGrace == normal.rules.joinGrace, at + "limits " + fast.violations);
		}
		helper.succeed();
	}

	private static long closest(List<Event> fires) {
		long closest = Long.MAX_VALUE;
		for (int i = 1; i < fires.size(); i++) {
			closest = Math.min(closest, fires.get(i).play() - fires.get(i - 1).play());
		}
		return closest;
	}

	@GameTest
	public void liveRulesReadTheFlags(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		DirectorImpl director = DirectorInit.director();
		String silence = "director:silence_until_day=999999";
		String pace = "director:pace_multiplier=2";
		boolean hadSilence = state.hasFlag(silence);
		boolean hadPace = state.hasFlag(pace);
		try {
			state.setFlag(silence, true);
			state.setFlag(pace, true);
			DirectorRules live = director.rules(server);
			helper.assertTrue(live.silenceUntilDay == 999_999 && live.paceMultiplier == 2, "live rules ignored the flags");
			String blocked = DirectorApi.snapshot(server).blocked();
			helper.assertTrue(blocked != null && blocked.startsWith("silenced"), "snapshot: " + blocked);
			String lines = String.join("\n", director.debugLines(server));
			helper.assertTrue(lines.contains("silence until day 999999, pace x2.00"), "debug lines:\n" + lines);
		} finally {
			state.setFlag(silence, hadSilence);
			state.setFlag(pace, hadPace);
		}
		DirectorRules cleared = director.rules(server);
		helper.assertTrue(cleared.silenceUntilDay == DirectorFlags.NO_SILENCE && cleared.paceMultiplier == 1, "the flags outlived their removal");
		helper.succeed();
	}

	/** A Proximity run from tick 0, day 0 (every fit lands), with these flags on the rules. */
	private static DirectorSim.Result flagged(Set<String> flags, double hours) {
		DirectorRules rules = DirectorFlags.apply(rules(Tempo.SLOW_BURN, Signature.CROSS_ROW), DirectorFlags.parse(flags));
		DirectorSim.Params params = DirectorTestSupport.params(rules, 5, rules.attentionNeutral, hours);
		params.fitChance = 1;
		params.noSpotChance = 0;
		return DirectorSim.run(rules, SyntheticDeck.cards(), 1000, new DirectorMemory(), Stage.PROXIMITY, 0, new DirectorBrain.Clock(0, 0), params);
	}
}
