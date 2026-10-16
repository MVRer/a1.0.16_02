package com.forzacode.a1016_02.director;

import static com.forzacode.a1016_02.director.DirectorTestSupport.card;
import static com.forzacode.a1016_02.director.DirectorTestSupport.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.sc.PlaceMemory;
import com.forzacode.a1016_02.director.sc.Places;
import com.forzacode.a1016_02.director.sc.PlacesCommands;
import com.forzacode.a1016_02.director.sc.Sc01Config;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * The self-created layer's director part (docs/SELF_CREATED.md, 01): places remember. The pure factor (radius,
 * dimension, decay, the cap, forgetting, the away factor), the trusted chunk (an hour of dwell, no place near its
 * centre, the debug trust), where a fire is remembered (the target's and a felt sharer's own spots, never a fake or a
 * forced fire), the brain's gate (Traces on, after the player's first hour, ambient and minor rates only), storage
 * (an old pacing loads with none), the section, the revert, the debug lines and the dry runs with the dread share
 * against the plain ones. Pure logic and its own memories; the live helpers are asked with a mock player only.
 */
public class SelfCreatedDirectorTests {
	private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
	private static final long DAY = DirectorBrain.DAY_TICKS;

	/** A hand-driven env with a position, a place factor and the live way of remembering places. */
	static final class PlaceEnv implements DirectorBrain.Env {
		Stage stage;
		double tension;
		double factor = 1;
		ResourceKey<Level> dimension = OVERWORLD;
		BlockPos pos;
		List<DirectorBrain.Sharer> sharers = List.of();
		final Sc01Config cfg = new Sc01Config();
		final List<String> fired = new ArrayList<>();
		int remembered;

		PlaceEnv(Stage stage, BlockPos pos) {
			this.stage = stage;
			this.pos = pos;
		}

		@Override
		public Stage stage() {
			return stage;
		}

		@Override
		public void setStage(Stage stage) {
			this.stage = stage;
		}

		@Override
		public double attention() {
			return 10;
		}

		@Override
		public double tension() {
			return tension;
		}

		@Override
		public void addTension(double delta, String reason) {
			tension = Math.max(0, Math.min(100, tension + delta));
		}

		@Override
		public boolean contextFits(CardInfo card) {
			return true;
		}

		@Override
		public FireResult fire(CardInfo card, boolean fake) {
			fired.add(card.id() + (fake ? " fake" : ""));
			return FireResult.FIRED;
		}

		@Override
		public List<DirectorBrain.Sharer> sharers() {
			return sharers;
		}

		@Override
		public Optional<FogSpell> fogSpell() {
			return Optional.empty();
		}

		@Override
		public double placeFactor() {
			return factor;
		}

		@Override
		public void remember(PacingMemory m, CardInfo card, DirectorBrain.Clock c, boolean felt) {
			remembered++;
			Places.remember(m.places(), dimension, pos, card, c.playTicks(), c.dayTicks(), cfg);
		}
	}

	private static CardInfo minor(String id) {
		return card(id, Tier.MINOR, Stage.TRACES, false);
	}

	// --- the factor ---

	/** A place counts within the radius in its own dimension, decays by its half life, caps at maxFactor and is forgotten. */
	@GameTest
	public void theFactorCountsNearPlacesDecaysCapsAndForgets(GameTestHelper helper) {
		Sc01Config cfg = new Sc01Config();
		PlaceMemory m = new PlaceMemory();
		BlockPos at = new BlockPos(100, 64, 100);
		Places.remember(m, OVERWORLD, at, minor("m"), 0, 0, cfg);
		helper.assertTrue(close(Places.factor(m, OVERWORLD, at, 0, 0, 72_000, cfg), 1.3), "a minor's place: " + Places.factor(m, OVERWORLD, at, 0, 0, 72_000, cfg));
		helper.assertTrue(close(Places.factor(m, OVERWORLD, at.offset(31, 0, 0), 0, 0, 72_000, cfg), 1.3), "31 blocks off is within the radius");
		helper.assertTrue(close(Places.factor(m, OVERWORLD, at.offset(0, 33, 0), 0, 0, 72_000, cfg), 1.0), "33 blocks up is outside it (3D)");
		helper.assertTrue(close(Places.factor(m, Level.NETHER, at, 0, 0, 72_000, cfg), 1.0), "another dimension counted");
		helper.assertTrue(close(Places.factor(m, OVERWORLD, at, 0, 4 * DAY, 72_000, cfg), 1.15), "not halved after 4 in-game days");
		helper.assertTrue(close(Places.factor(m, OVERWORLD, at, 0, 16 * DAY, 72_000, cfg), 1.0), "a minor 16 days old (0.019) is not forgotten");
		PlaceMemory sighting = new PlaceMemory();
		Places.remember(sighting, OVERWORLD, at, card("s", Tier.MAJOR, Stage.PROXIMITY, false, CardTag.SIGHTING), 0, 0, cfg);
		helper.assertTrue(close(Places.factor(sighting, OVERWORLD, at, 0, 0, 72_000, cfg), 1.7), "a major sighting weighs 0.5 + 0.2");
		PlaceMemory many = new PlaceMemory();
		for (int i = 0; i < 30; i++) {
			Places.remember(many, OVERWORLD, at.offset(i % 3, 0, 0), card("a" + i, Tier.SIGNATURE, Stage.TRACES, false), i, 0, cfg);
		}
		helper.assertTrue(many.places().size() == cfg.maxPlaces, "kept " + many.places().size() + " places, not " + cfg.maxPlaces);
		helper.assertTrue(many.places().getFirst().playTicks() == 30 - cfg.maxPlaces, "the oldest did not go first");
		helper.assertTrue(close(Places.factor(many, OVERWORLD, at, 0, 0, 72_000, cfg), cfg.maxFactor), "not capped at " + cfg.maxFactor);
		Sc01Config away = new Sc01Config();
		away.awayFactor = 0.9;
		helper.assertTrue(close(Places.factor(m, OVERWORLD, at.offset(100, 0, 0), 0, 0, 72_000, away), 0.9)
				&& close(Places.factor(m, OVERWORLD, at, 0, 0, 72_000, away), 1.3), "the away factor is only where the factor is exactly 1");
		helper.succeed();
	}

	/**
	 * The trusted chunk: the most dwelt chunk once it holds an hour of the player's play (halved every hour, so about an
	 * hour and a half of being there), never with a remembered place within 32 blocks of its centre; there the factor is
	 * 1.6. A debug trust names a chunk for an hour of play, the place rule still holding.
	 */
	@GameTest
	public void theTrustedChunkNeedsAnHourOfDwellAndNoPlaceNearIt(GameTestHelper helper) {
		Sc01Config cfg = new Sc01Config();
		long hour = 72_000;
		long interval = 600;
		PlaceMemory m = new PlaceMemory();
		BlockPos home = new BlockPos(40, 70, 40);
		PlaceMemory.Chunk chunk = PlaceMemory.Chunk.of(OVERWORLD, home);
		long play = 0;
		for (; play < 45 * 1200; play += interval) {
			m.sampleDwell(chunk, play, interval, hour);
		}
		helper.assertTrue(Places.trusted(m, play, 0, hour, cfg).isEmpty(), "trusted after 45 minutes: " + m.dwell());
		for (; play < 100 * 1200; play += interval) {
			m.sampleDwell(chunk, play, interval, hour);
		}
		helper.assertTrue(Places.trusted(m, play, 0, hour, cfg).equals(Optional.of(chunk)), "not trusted after 100 minutes there: " + m.dwell());
		helper.assertTrue(close(Places.factor(m, OVERWORLD, home, play, 0, hour, cfg), cfg.trustedFactor), "the trusted factor");
		helper.assertTrue(close(Places.factor(m, OVERWORLD, home.offset(32, 0, 0), play, 0, hour, cfg), 1.0), "trusted outside the chunk");
		// Many chunks: at most 64 kept, the least dwelt out, and the most dwelt one stays.
		PlaceMemory wander = new PlaceMemory();
		long t = 0;
		for (int i = 0; i < 20; i++) {
			wander.sampleDwell(chunk, t += interval, interval, hour);
		}
		for (int i = 0; i < 100; i++) {
			wander.sampleDwell(new PlaceMemory.Chunk(OVERWORLD, 100 + i, 100), t += interval, interval, hour);
		}
		helper.assertTrue(wander.dwell().size() == PlaceMemory.DWELL_CHUNKS && wander.dwell().containsKey(chunk)
				&& Places.mostDwelt(wander).getKey().equals(chunk), "dwell kept " + wander.dwell().size() + " chunks: " + Places.mostDwelt(wander));
		// A place 40 blocks from the centre leaves it trusted; one within 32 does not, until it is forgotten.
		BlockPos centre = new BlockPos((chunk.x() << 4) + 8, 70, (chunk.z() << 4) + 8);
		Places.remember(m, OVERWORLD, centre.offset(40, 0, 0), minor("far"), play, 0, cfg);
		helper.assertTrue(Places.trusted(m, play, 0, hour, cfg).isPresent(), "a place 40 blocks off took the trust");
		Places.remember(m, OVERWORLD, centre.offset(20, 0, 0), minor("near"), play, 0, cfg);
		helper.assertTrue(Places.trusted(m, play, 0, hour, cfg).isEmpty(), "a place 20 blocks from its centre kept it trusted");
		helper.assertTrue(Places.trusted(m, play, 20 * DAY, hour, cfg).isPresent(), "a forgotten place still keeps the trust away");
		// The debug trust.
		PlaceMemory fresh = new PlaceMemory();
		PlaceMemory.Chunk other = new PlaceMemory.Chunk(OVERWORLD, -5, 9);
		fresh.setTrust(new PlaceMemory.Trust(other, hour));
		helper.assertTrue(Places.trusted(fresh, hour - 1, 0, hour, cfg).equals(Optional.of(other)), "the debug trust did not name its chunk");
		helper.assertTrue(Places.trusted(fresh, hour, 0, hour, cfg).isEmpty(), "the debug trust outlived its hour");
		helper.succeed();
	}

	// --- remembering ---

	/**
	 * A real fire is remembered where its target stands, and a sharer who felt it where they stand; a fake or a forced
	 * fire is remembered by nobody.
	 */
	@GameTest
	public void aFireIsRememberedAtTheTargetsAndTheSharersOwnSpots(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		rules.tracesAmbientPerHour = 1000;
		rules.minAnyGap = 0;
		rules.rateRefractory = 0;
		rules.fakeChance = 0;
		List<CardInfo> cards = List.of(card("p_a", Tier.AMBIENT, Stage.TRACES, true), card("p_b", Tier.AMBIENT, Stage.TRACES, true),
				card("p_c", Tier.AMBIENT, Stage.TRACES, true), card("p_d", Tier.AMBIENT, Stage.TRACES, true));
		DirectorBrain brain = new DirectorBrain(rules, cards, 50);
		WorldMemory world = new WorldMemory();
		DirectorTestSupport.pin(world, rules);
		PacingMemory ana = new PacingMemory();
		PacingMemory ben = new PacingMemory();
		ana.sessionStart = 0;
		ben.sessionStart = 0;
		BlockPos anaAt = new BlockPos(10, 64, 10);
		BlockPos benAt = new BlockPos(30, 64, 10);
		PlaceEnv anaEnv = new PlaceEnv(Stage.TRACES, anaAt);
		PlaceEnv benEnv = new PlaceEnv(Stage.TRACES, benAt);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DAY);
		world.lastStepPlay = clock.worldTicks();
		ana.lastStepPlay = clock.playTicks();
		ben.lastStepPlay = clock.playTicks();
		RandomSource random = RandomSource.create(5);
		for (int i = 0; i < 40 && anaEnv.fired.isEmpty(); i++) {
			clock = clock.plus(rules.tickInterval);
			anaEnv.tension = 0;
			benEnv.tension = 0;
			anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, clock, benEnv, DirectorBrain.Recorder.NONE));
			brain.step(world, ana, clock, anaEnv, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(anaEnv.fired.size() == 1, "nothing fired: " + anaEnv.fired);
		helper.assertTrue(ana.places().places().size() == 1 && ana.places().places().getFirst().pos().equals(anaAt)
				&& ana.places().places().getFirst().tier() == Tier.AMBIENT, "the target's place: " + ana.places().places());
		helper.assertTrue(ben.places().places().size() == 1 && ben.places().places().getFirst().pos().equals(benAt), "the sharer's place: " + ben.places().places());
		rules.fakeChance = 1;
		for (int i = 0; i < 40 && anaEnv.fired.size() < 2; i++) {
			clock = clock.plus(rules.tickInterval);
			anaEnv.tension = 0;
			benEnv.tension = 0;
			anaEnv.sharers = List.of(new DirectorBrain.Sharer(ben, clock, benEnv, DirectorBrain.Recorder.NONE));
			brain.step(world, ana, clock, anaEnv, random, DirectorBrain.Recorder.NONE);
		}
		helper.assertTrue(anaEnv.fired.size() == 2 && anaEnv.fired.get(1).endsWith(" fake"), "no fake fired: " + anaEnv.fired);
		helper.assertTrue(ana.places().places().size() == 1 && ben.places().places().size() == 1, "a fake was remembered");
		brain.recordForced(world, ana, clock, cards.get(3), false, Stage.TRACES, List.of(new DirectorBrain.Sharer(ben, clock, benEnv,
				DirectorBrain.Recorder.NONE)), random, DirectorBrain.Recorder.NONE);
		helper.assertTrue(ana.places().places().size() == 1 && ben.places().places().size() == 1 && anaEnv.remembered == 1 && benEnv.remembered == 1,
				"a forced fire was remembered");
		helper.succeed();
	}

	/** The live helpers: a fire is remembered where the player stands, the factor read there, the dwell sampled (layer on). */
	@GameTest
	public void theLiveHelpersRememberAndReadWhereThePlayerStands(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))), 0, 0);
		PacingMemory pacing = new PacingMemory();
		DirectorBrain.Clock clock = new DirectorBrain.Clock(5 * 72_000, com.forzacode.a1016_02.core.GameClock.dayTicks(helper.getLevel().getServer()));
		DirectorImpl.remember(player, pacing, minor("live"), clock);
		helper.assertTrue(pacing.places().places().size() == 1 && pacing.places().places().getFirst().pos().equals(player.blockPosition()),
				"not remembered where the player stands: " + pacing.places().places());
		double factor = DirectorImpl.placeFactor(helper.getLevel().getServer(), player, pacing);
		helper.assertTrue(factor > 1.0, "no factor where it happened: " + factor);
		DirectorImpl.sampleDwell(player, pacing, clock.playTicks(), rules(Tempo.SLOW_BURN, Signature.CROSS_ROW));
		helper.assertTrue(pacing.places().dwell().containsKey(PlaceMemory.Chunk.of(player.level().dimension(), player.blockPosition())), "no dwell sampled");
		helper.succeed();
	}

	// --- the brain ---

	/**
	 * The brain asks the factor only from Traces on and after the player's own first hour; in Traces a factor of 1.8
	 * raises the ambient and the minor rate (about 1.6 to 1.8 times, the gaps holding), and the major schedule is the
	 * same.
	 */
	@GameTest(maxTicks = 600)
	public void theFactorRaisesOnlyAmbientAndMinorRatesFromTracesAfterTheFirstHour(GameTestHelper helper) {
		DirectorRules rules = rules(Tempo.SLOW_BURN, Signature.CROSS_ROW);
		DirectorBrain gate = new DirectorBrain(rules, List.of(), 10);
		PlaceEnv env = new PlaceEnv(Stage.TRACES, BlockPos.ZERO);
		env.factor = 1.8;
		DirectorBrain.Clock late = new DirectorBrain.Clock(2 * rules.hourTicks, 2 * DAY);
		DirectorBrain.Clock early = new DirectorBrain.Clock(rules.hourTicks - 1, 2 * DAY);
		helper.assertTrue(gate.placeFactor(env, Stage.ALONE, late) == 1.0, "applied in Alone");
		helper.assertTrue(gate.placeFactor(env, Stage.TRACES, early) == 1.0, "applied in the first hour");
		helper.assertTrue(gate.placeFactor(env, Stage.TRACES, late) == 1.8 && gate.placeFactor(env, Stage.PROXIMITY, late) == 1.8, "not applied");
		env.factor = Double.NaN;
		helper.assertTrue(gate.placeFactor(env, Stage.TRACES, late) == 1.0, "a broken factor was used");

		int[] plain = counts(rules, Stage.TRACES, 1.0, 12_000);
		int[] dread = counts(rules, Stage.TRACES, 1.8, 12_000);
		double ambient = dread[0] / (double) plain[0];
		double minors = dread[1] / (double) plain[1];
		A1016_02.LOGGER.info("[a1016] sc 01 brain: Traces x1.8: ambients {} -> {} (x{}), minors {} -> {} (x{})", plain[0], dread[0], ambient, plain[1], dread[1],
				minors);
		helper.assertTrue(ambient > 1.45 && ambient < 2.0, "ambients x" + ambient + " (" + plain[0] + " -> " + dread[0] + ")");
		helper.assertTrue(minors > 1.3 && minors < 2.0, "minors x" + minors + " (" + plain[1] + " -> " + dread[1] + ")");
		int[] proximity = counts(rules, Stage.PROXIMITY, 1.0, 12_000);
		int[] proximityDread = counts(rules, Stage.PROXIMITY, 1.8, 12_000);
		double majors = proximityDread[2] / (double) proximity[2];
		helper.assertTrue(majors > 0.9 && majors < 1.1, "majors x" + majors + " (" + proximity[2] + " -> " + proximityDread[2] + ")");
		helper.succeed();
	}

	/** Ambient, minor and major fires over {@code ticks} director ticks in one stage at this factor (tension held at 0). */
	private static int[] counts(DirectorRules rules, Stage stage, double factor, int ticks) {
		List<CardInfo> cards = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			cards.add(card("amb" + i, Tier.AMBIENT, Stage.ALONE, false));
			cards.add(card("min" + i, Tier.MINOR, Stage.TRACES, false));
			cards.add(card("maj" + i, Tier.MAJOR, Stage.PROXIMITY, false));
		}
		DirectorBrain brain = new DirectorBrain(rules, cards, 10);
		DirectorTestSupport.Solo solo = DirectorTestSupport.pinned(rules);
		PlaceEnv env = new PlaceEnv(stage, BlockPos.ZERO);
		env.factor = factor;
		RandomSource random = RandomSource.create(1016);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(rules.hourTicks, 2 * DAY);
		solo.startAt(clock);
		int[] out = new int[3];
		for (int i = 0; i < ticks; i++) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			int before = env.fired.size();
			solo.step(brain, clock, env, random, DirectorBrain.Recorder.NONE);
			for (String id : env.fired.subList(before, env.fired.size())) {
				out[id.startsWith("amb") ? 0 : id.startsWith("min") ? 1 : 2]++;
			}
		}
		return out;
	}

	// --- the dry runs ---

	/**
	 * The registered deck's dry runs (every tempo, {@link DirectorApi#tuningSeeds()}, 20 h each) with the section's dread
	 * share ({@code simDreadShare} 0.3 of the director ticks at 1.4, {@code simTrustedShare} 0.05 at 1.6): the first hour is
	 * the plain runs' fire for fire (the factor never applies there), so D-070's targets hold; Traces' and Proximity's
	 * ambient and minor rates stay within 15% of the plain runs (DESIGN 4b), majors within 15%; every hard limit holds.
	 * The numbers are logged.
	 */
	@GameTest(maxTicks = 3000)
	public void theDryRunsWithTheDreadShareKeepTheFirstHourAndTheRates(GameTestHelper helper) {
		List<CardInfo> deck = DirectorTestSupport.registryDeck();
		Sc01Config cfg = new Sc01Config();
		for (Tempo tempo : Tempo.values()) {
			List<DirectorSim.Result> plain = DirectorTestSupport.registryRuns(tempo);
			List<DirectorSim.Result> dread = new ArrayList<>();
			for (long seed : DirectorApi.tuningSeeds()) {
				DirectorConfig config = new DirectorConfig();
				DirectorRules rules = DirectorRules.from(new Pacing(), config, com.forzacode.a1016_02.debug.Playthrough.profile(seed, tempo));
				DirectorSim.Params params = DirectorSim.Params.from(config, rules).places(cfg);
				params.hours = DirectorTestSupport.HOURS;
				params.seed = seed;
				params.attention = config.attentionNeutral;
				dread.add(DirectorSim.run(rules, deck, Math.max(config.historySize, 1), new DirectorMemory(), Stage.ALONE, 0, new DirectorBrain.Clock(0, 0), params));
			}
			DirectorTestSupport.FirstHour first = DirectorTestSupport.FirstHour.of(dread);
			DirectorTestSupport.FourB was = DirectorTestSupport.FourB.of(plain, deck);
			DirectorTestSupport.FourB now = DirectorTestSupport.FourB.of(dread, deck);
			A1016_02.LOGGER.info("[a1016] sc 01 dread share {}: plain {}", tempo, was.summary());
			A1016_02.LOGGER.info("[a1016] sc 01 dread share {}: dread {}; {}", tempo, now.summary(), first.summary());
			String at = tempo + ", dread " + now.summary() + " against plain " + was.summary() + ": ";
			helper.assertTrue(first.by30() * 4 >= first.runs() * 3 && first.by45() * 10 >= first.runs() * 9 && first.twoOrMore() * 10 >= first.runs() * 7
					&& first.most() <= 5, at + "the first hour: " + first.summary());
			for (int i = 0; i < dread.size(); i++) {
				DirectorSim.Result d = dread.get(i);
				DirectorSim.Result p = plain.get(i);
				long hour = d.rules.hourTicks;
				helper.assertTrue(firstHour(d, hour).equals(firstHour(p, hour)), at + "seed " + d.params.seed + ": the first hour changed: " + firstHour(d, hour)
						+ " against " + firstHour(p, hour));
				helper.assertTrue(d.violations.isEmpty(), at + "seed " + d.params.seed + ": " + d.violations);
				List<String> hard = DirectorTestSupport.feltHardLimits(d, deck);
				helper.assertTrue(hard.isEmpty(), at + "seed " + d.params.seed + ": " + hard);
			}
			helper.assertTrue(within(now.tracesAmbientsPerHour(), was.tracesAmbientsPerHour()) && within(now.tracesMinorsPerHour(), was.tracesMinorsPerHour())
					&& within(now.proximityAmbientsPerHour(), was.proximityAmbientsPerHour())
					&& within(now.proximityMinorsPerHour(), was.proximityMinorsPerHour()), at + "a rate moved more than 15%");
			helper.assertTrue(within(now.hoursPerMajor(), was.hoursPerMajor()), at + "majors moved more than 15%");
		}
		helper.succeed();
	}

	/** The first hour's fires of a run: card, tier and play tick. */
	private static List<String> firstHour(DirectorSim.Result result, long hour) {
		return result.fires().stream().filter(e -> e.play() - result.startPlay < hour).map(e -> e.cardId() + "@" + e.play()).toList();
	}

	private static boolean within(double now, double was) {
		return was <= 0 ? now <= 0 : Math.abs(now / was - 1) <= 0.15;
	}

	// --- storage, the section, the revert, the debug lines ---

	/** A pacing from before the places (v1.3) loads with none and writes no key; places, dwell and a trust round-trip. */
	@GameTest
	public void anOldPacingLoadsWithNoPlacesAndTheyRoundTrip(GameTestHelper helper) {
		PacingMemory fresh = new PacingMemory();
		CompoundTag tag = fresh.toTag();
		helper.assertFalse(tag.contains("sc01"), "an empty memory wrote the places key");
		PacingMemory old = PacingMemory.fromTag(tag);
		helper.assertTrue(old.places().isEmpty(), "an old pacing loaded places");
		PacingMemory m = new PacingMemory();
		Sc01Config cfg = new Sc01Config();
		Places.remember(m.places(), OVERWORLD, new BlockPos(1, 2, 3), card("rt", Tier.MAJOR, Stage.PROXIMITY, false, CardTag.SIGHTING), 77, 5 * DAY, cfg);
		Places.remember(m.places(), Level.NETHER, new BlockPos(-4, 50, 9), minor("rt2"), 99, 6 * DAY, cfg);
		m.places().sampleDwell(PlaceMemory.Chunk.of(OVERWORLD, new BlockPos(1, 2, 3)), 600, 600, 72_000);
		m.places().setTrust(new PlaceMemory.Trust(new PlaceMemory.Chunk(OVERWORLD, 3, -2), 12_345));
		PacingMemory back = PacingMemory.fromTag(m.toTag());
		helper.assertTrue(back.places().places().equals(m.places().places()), "places: " + back.places().places());
		helper.assertTrue(back.places().dwell().equals(m.places().dwell()), "dwell: " + back.places().dwell());
		helper.assertTrue(back.places().trust().equals(m.places().trust()), "trust: " + back.places().trust());
		CompoundTag broken = m.toTag();
		broken.getCompoundOrEmpty("sc01").getListOrEmpty("places").getCompoundOrEmpty(0).putString("tier", "NOT_A_TIER");
		helper.assertTrue(PacingMemory.fromTag(broken).places().places().size() == 1, "a broken place was kept or broke the rest");
		helper.succeed();
	}

	/** {@code sections.sc01} is version 1 with the design's numbers. */
	@GameTest
	public void theSectionIsVersionOneWithTheDesignsNumbers(GameTestHelper helper) {
		Sc01Config cfg = ModConfig.readSection(new JsonObject(), Sc01Config.SECTION, Sc01Config.class, Sc01Config::new);
		helper.assertTrue(cfg.version == 1 && cfg.maxPlaces == 24 && cfg.radius == 32 && cfg.maxFactor == 1.8 && cfg.halfLifeDays == 4
				&& cfg.weightAmbient == 0.15 && cfg.weightMinor == 0.3 && cfg.weightMajor == 0.5 && cfg.weightSighting == 0.2 && cfg.trustedFactor == 1.6
				&& cfg.trustedMinHours == 1 && cfg.awayFactor == 1.0 && cfg.simDreadShare == 0.3 && cfg.simDreadFactor == 1.4 && cfg.simTrustedShare == 0.05,
				"the section's numbers");
		helper.succeed();
	}

	/** The switch's revert takes every debug trust away; the debug lines say the factor, the places and the trusted chunk. */
	@GameTest
	public void theRevertTakesTheTrustsAndTheDebugLinesSayTheFactor(GameTestHelper helper) {
		DirectorMemory memory = new DirectorMemory();
		PacingMemory pacing = memory.pacing(new java.util.UUID(1, 2), 0);
		pacing.places().setTrust(new PlaceMemory.Trust(new PlaceMemory.Chunk(OVERWORLD, 0, 0), Long.MAX_VALUE));
		helper.assertTrue(PlacesCommands.revert(memory) && pacing.places().trust().isEmpty(), "the trust stayed");
		helper.assertFalse(PlacesCommands.revert(memory), "a second revert changed something");
		Sc01Config cfg = new Sc01Config();
		Places.remember(pacing.places(), OVERWORLD, new BlockPos(5, 64, 5), minor("dbg"), 0, 0, cfg);
		List<String> lines = Places.describe("Ana", pacing.places(), OVERWORLD, new BlockPos(5, 64, 5), 0, 0, 72_000, true, cfg);
		helper.assertTrue(lines.size() == 3 && lines.get(0).contains("x1.30") && lines.get(1).contains("1 of 24") && lines.get(2).contains("no trusted chunk"),
				"the lines: " + lines);
		helper.assertTrue(String.join("\n", lines).chars().noneMatch(ch -> ch > 127), "a line is not plain");
		com.forzacode.a1016_02.core.HerobrineState state = com.forzacode.a1016_02.core.HerobrineState.CODEC
				.parse(com.mojang.serialization.JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		helper.assertFalse(Places.storyQuiet(state), "a fresh story is quiet");
		for (String flag : List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete", "ending:last_minute",
				"director:obeyed_after_stop")) {
			state.setFlag(flag, true);
			helper.assertTrue(Places.storyQuiet(state), flag + " left the factor on");
			state.setFlag(flag, false);
		}
		helper.succeed();
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1e-6;
	}

}
