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
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
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
 * independently of {@link DirectorSim#check}. None of them touch the shared world state.
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
		helper.assertTrue(brain.enterTelling(clock, env, DirectorBrain.Recorder.NONE) && env.stage == Stage.TELLING, "TELLING did not start Telling");
		env.stage = Stage.REMOVAL;
		helper.assertFalse(brain.enterTelling(clock, env, DirectorBrain.Recorder.NONE), "Telling replaced Removal");
		helper.succeed();
	}

	@GameTest
	public void decksNeverRepeatUntilExhausted(GameTestHelper helper) {
		int repeats = 0;
		int refills = 0;
		for (Playthrough p : playthroughs()) {
			Map<Tier, Set<String>> cycle = new EnumMap<>(Tier.class);
			Map<Tier, String> last = new EnumMap<>(Tier.class);
			Set<String> everFired = new HashSet<>();
			String previous = null;
			for (Tier tier : Tier.values()) {
				cycle.put(tier, new HashSet<>());
			}
			for (Event e : p.result().events) {
				if (e.kind() == Kind.REFILL) {
					helper.assertFalse(cycle.get(e.tier()).isEmpty(), where(p) + "refilled a deck with nothing fired");
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

		// Pressure: Proximity from the first tick, a major already due, short sessions, everything always fits.
		DirectorRules rules = rules(Tempo.EARLY, Signature.CROSS_ROW);
		rules.proximityAmbientPerHour = 60;
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
		for (long seed : SEEDS) {
			DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
			for (Stage stage : List.of(Stage.TELLING, Stage.PROXIMITY)) {
				DirectorSim.Result result = DirectorSim.run(rules, SyntheticDeck.cards(), 100, new DirectorMemory(), stage, 0,
						new DirectorBrain.Clock(0, 0), DirectorTestSupport.params(rules, seed, rules.attentionNeutral, HOURS));
				int sightings = fires(result, e -> SyntheticDeck.cards().stream().anyMatch(c -> c.id().equals(e.cardId()) && c.has(CardTag.SIGHTING))).size();
				if (stage == Stage.TELLING) {
					telling += sightings;
				} else {
					proximity += sightings;
				}
			}
		}
		helper.assertTrue(proximity >= 8 && telling * 5 <= proximity, "sightings: Telling " + telling + ", Proximity " + proximity);
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
}
