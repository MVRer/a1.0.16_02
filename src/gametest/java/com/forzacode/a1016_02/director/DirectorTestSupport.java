package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Density;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.debug.DebugPingCard;

import net.minecraft.util.RandomSource;

/**
 * Fixtures for {@link DirectorGameTests}: fixed-seed 20 h playthroughs over the synthetic deck, group runs over the
 * registered deck, the 4b hard limits replayed per player, and scripted envs for one player or several.
 */
class DirectorTestSupport {
	static final long[] SEEDS = {1L, 7L, 42L, 1016L, 0xA1016L, 99_991L};
	static final double HOURS = 20;
	private static final Signature[] SIGNATURES = Signature.values();

	/** One fixed-seed playthrough. */
	record Playthrough(Tempo tempo, long seed, DirectorRules rules, DirectorSim.Result result) {
	}

	private static List<Playthrough> cached;

	static WorldProfile profile(Tempo tempo, Signature signature, Set<String> fragments) {
		return new WorldProfile(EnumSet.of(Habit.CARVER, Habit.MOURNER), Density.NORMAL, tempo, fragments, signature);
	}

	static DirectorRules rules(Tempo tempo, Signature signature) {
		return DirectorRules.from(new Pacing(), new DirectorConfig(), profile(tempo, signature, WorldProfile.FIXED_FRAGMENTS));
	}

	/**
	 * v1.1's pacing, before D-070 (core's C-1 changed the defaults). Only for the tuning tests that still assert v1.1's
	 * 4b; W-DIR retargets them to v1.2's numbers and drops this.
	 */
	static Pacing v11Pacing() {
		Pacing p = new Pacing();
		p.tracesStartMinHours = 1;
		p.tracesStartMaxHours = 3;
		p.proximityStartMinHours = 5;
		p.proximityStartMaxHours = 6;
		p.tempoShift = 0.4;
		p.aloneMaxAmbient = 1;
		p.tracesAmbientPerHour = 1;
		p.proximityMinorsPerHourMin = 1;
		p.proximityMinorsPerHourMax = 2;
		p.proximityMajorEveryHoursMin = 2;
		p.proximityMajorEveryHoursMax = 4;
		p.firstAccidentMinHours = 6;
		p.emptySessionChance = 0.2;
		p.pityStartHours = 1;
		p.fogDriftMinSeconds = 3;
		p.fogDriftMaxSeconds = 8;
		return p;
	}

	static DirectorSim.Params params(DirectorRules rules, long seed, double attention, double hours) {
		DirectorSim.Params params = DirectorSim.Params.from(new DirectorConfig(), rules);
		params.hours = hours;
		params.seed = seed;
		params.attention = attention;
		return params;
	}

	/** A fresh world played for {@code hours} from tick 0, day 0, in Alone. */
	static DirectorSim.Result fresh(DirectorRules rules, long seed, double attention, double hours) {
		return DirectorSim.run(rules, SyntheticDeck.cards(), 1000, new DirectorMemory(), Stage.ALONE, 0,
				new DirectorBrain.Clock(0, 0), params(rules, seed, attention, hours));
	}

	/**
	 * The scripted playthrough's dry runs on the code defaults: the registered deck (without the debug ping card,
	 * whose moment never fits), the seed's rolled profile with this tempo, {@link DirectorApi#tuningSeeds()}, 20 h
	 * each from a fresh state at neutral attention. Unlike {@code DirectorApi.dryRuns}, a stale config file cannot
	 * change the numbers.
	 */
	static List<DirectorSim.Result> registryRuns(Tempo tempo) {
		return registryRuns(tempo, new Pacing());
	}

	/** {@link #registryRuns(Tempo)} on this pacing. */
	static List<DirectorSim.Result> registryRuns(Tempo tempo, Pacing pacing) {
		List<CardInfo> deck = DirectorApi.registeredCards().stream().filter(card -> !DebugPingCard.ID.equals(card.id())).toList();
		List<DirectorSim.Result> out = new ArrayList<>();
		for (long seed : DirectorApi.tuningSeeds()) {
			DirectorConfig config = new DirectorConfig();
			DirectorRules rules = DirectorRules.from(pacing, config, com.forzacode.a1016_02.debug.Playthrough.profile(seed, tempo));
			DirectorSim.Params params = DirectorSim.Params.from(config, rules);
			params.hours = HOURS;
			params.seed = seed;
			params.attention = config.attentionNeutral;
			out.add(DirectorSim.run(rules, deck, Math.max(config.historySize, 1), new DirectorMemory(), Stage.ALONE, 0,
					new DirectorBrain.Clock(0, 0), params));
		}
		return out;
	}

	/**
	 * The scripted playthrough's dry runs for several players on the code defaults ({@link #registryRuns}'s deck,
	 * profiles and attention), one group run per seed, 20 h of world play each.
	 */
	static List<DirectorSim.Group> groupRuns(Tempo tempo, List<DirectorSim.Seat> seats, DirectorSim.Sharing sharing, List<Long> seeds) {
		List<CardInfo> deck = DirectorApi.registeredCards().stream().filter(card -> !DebugPingCard.ID.equals(card.id())).toList();
		List<DirectorSim.Group> out = new ArrayList<>();
		for (long seed : seeds) {
			DirectorConfig config = new DirectorConfig();
			DirectorRules rules = DirectorRules.from(new Pacing(), config, com.forzacode.a1016_02.debug.Playthrough.profile(seed, tempo));
			DirectorSim.Params params = DirectorSim.Params.from(config, rules);
			params.hours = HOURS;
			params.seed = seed;
			params.attention = config.attentionNeutral;
			out.add(DirectorSim.runGroup(rules, deck, Math.max(config.historySize, 1), seats, sharing, params));
		}
		return out;
	}

	/** Every player's result of these group runs, in run order. */
	static List<DirectorSim.Result> seats(List<DirectorSim.Group> groups) {
		List<DirectorSim.Result> out = new ArrayList<>();
		groups.forEach(group -> out.addAll(group.players));
		return out;
	}

	/**
	 * DESIGN.md 4b's "never" rules replayed on what one player went through (their own fires and what they felt from
	 * others, on their own clock), independently of {@link DirectorSim#check}: nothing in the first minutes after
	 * joining, at least the minor gap between minors, no two majors (or signatures) within a real hour, no major in
	 * their own first in-game day, at most the Alone ambient cap and nothing else in Alone, no accident card before
	 * their own first-accident time. One line per breach.
	 */
	static List<String> feltHardLimits(DirectorSim.Result r, Collection<CardInfo> cards) {
		DirectorRules rules = r.rules;
		Map<String, CardInfo> byId = new HashMap<>();
		cards.forEach(card -> byId.put(card.id(), card));
		long firstDayEnd = r.startPacing.firstDayTicks() + rules.noMajorBeforeDay * DirectorBrain.DAY_TICKS;
		List<String> out = new ArrayList<>();
		long session = Long.MIN_VALUE;
		DirectorSim.Event lastMinor = null;
		DirectorSim.Event lastMajor = null;
		int alone = 0;
		for (DirectorSim.Event e : r.events) {
			if (e.kind() == DirectorSim.Kind.SESSION_START) {
				session = e.play();
				continue;
			}
			if (!e.felt()) {
				continue;
			}
			String at = e.kind() + " " + e.cardId() + " at " + e.play() + ": ";
			if (session != Long.MIN_VALUE && e.play() - session < rules.joinGrace) {
				out.add(at + "in the join grace");
			}
			if (e.tier() == Tier.MINOR) {
				if (lastMinor != null && e.play() - lastMinor.play() < rules.minorGap) {
					out.add(at + "minors " + (e.play() - lastMinor.play()) + " ticks apart");
				}
				lastMinor = e;
			}
			if (e.tier() == Tier.MAJOR || e.tier() == Tier.SIGNATURE) {
				if (lastMajor != null && e.play() - lastMajor.play() < rules.hourTicks) {
					out.add(at + "two majors " + (e.play() - lastMajor.play()) + " ticks apart");
				}
				if (e.dayTicks() < firstDayEnd) {
					out.add(at + "a major in their first in-game day");
				}
				lastMajor = e;
			}
			if (e.stage() == Stage.ALONE && (e.tier() != Tier.AMBIENT || ++alone > rules.aloneMaxAmbient)) {
				out.add(at + "too much in Alone");
			}
			CardInfo card = byId.get(e.cardId());
			if (card != null && card.has(CardTag.ACCIDENT) && e.play() < rules.firstAccident) {
				out.add(at + "an accident before " + rules.firstAccident);
			}
		}
		return out;
	}

	/** The registered deck the scripted playthrough plays (without the debug ping card). */
	static List<CardInfo> registryDeck() {
		return DirectorApi.registeredCards().stream().filter(card -> !DebugPingCard.ID.equals(card.id())).toList();
	}

	/** One shared stage for several {@link PlayerEnv}s, counting its changes. */
	static final class SharedStage {
		Stage stage;
		final List<Stage> changes = new ArrayList<>();

		SharedStage(Stage stage) {
			this.stage = stage;
		}
	}

	/** One player's hand-driven env over a {@link SharedStage}: their own tension, fits, fires and sharers. */
	static final class PlayerEnv implements DirectorBrain.Env {
		final SharedStage world;
		double tension;
		Predicate<CardInfo> fits = card -> true;
		List<DirectorBrain.Sharer> sharers = List.of();
		final List<String> fired = new ArrayList<>();

		PlayerEnv(SharedStage world) {
			this.world = world;
		}

		@Override
		public Stage stage() {
			return world.stage;
		}

		@Override
		public void setStage(Stage stage) {
			world.stage = stage;
			world.changes.add(stage);
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
			return fits.test(card);
		}

		@Override
		public FireResult fire(CardInfo card, boolean fake) {
			fired.add(card.id());
			return FireResult.FIRED;
		}

		@Override
		public List<DirectorBrain.Sharer> sharers() {
			return sharers;
		}
	}

	/** Every tempo times every seed, 20 h each, attention at neutral. Computed once. */
	static synchronized List<Playthrough> playthroughs() {
		if (cached == null) {
			List<Playthrough> out = new ArrayList<>();
			for (Tempo tempo : Tempo.values()) {
				for (int i = 0; i < SEEDS.length; i++) {
					DirectorRules rules = rules(tempo, SIGNATURES[i % SIGNATURES.length]);
					out.add(new Playthrough(tempo, SEEDS[i], rules, fresh(rules, SEEDS[i], rules.attentionNeutral, HOURS)));
				}
			}
			cached = List.copyOf(out);
		}
		return cached;
	}

	static List<DirectorSim.Event> fires(DirectorSim.Result result, Predicate<DirectorSim.Event> filter) {
		return result.events.stream().filter(e -> e.kind() == DirectorSim.Kind.FIRE && filter.test(e)).toList();
	}

	static String where(Playthrough p) {
		return p.tempo() + "/seed " + p.seed() + ": ";
	}

	static CardInfo card(String id, Tier tier, Stage stage, boolean fake, CardTag... tags) {
		return new CardInfo(id, tier, stage, Set.of(), tags.length == 0 ? Set.of() : EnumSet.copyOf(List.of(tags)), fake);
	}

	/** One player's memories for scripted runs: the shared world part and their own pacing. */
	static final class Solo {
		final WorldMemory world = new WorldMemory();
		final PacingMemory pacing;

		Solo() {
			this(new PacingMemory());
		}

		Solo(PacingMemory pacing) {
			this.pacing = pacing;
		}

		/** Both time steps start at this clock (no decay or stage clock from tick 0). */
		void startAt(DirectorBrain.Clock clock) {
			world.lastStepPlay = clock.worldTicks();
			pacing.lastStepPlay = clock.playTicks();
		}

		String step(DirectorBrain brain, DirectorBrain.Clock clock, DirectorBrain.Env env, RandomSource random, DirectorBrain.Recorder rec) {
			return brain.step(world, pacing, clock, env, random, rec);
		}
	}

	/** Memory for scripted runs: a session already past its join grace and stage points that never come. */
	static Solo pinned(DirectorRules rules) {
		Solo memory = new Solo();
		pin(memory.world, rules);
		memory.pacing.sessionStart = 0;
		return memory;
	}

	/** Stage points that never come, for scripted runs in a fixed stage. */
	static void pin(WorldMemory world, DirectorRules rules) {
		world.tracesAt = Long.MAX_VALUE / 4;
		world.proximityAt = Long.MAX_VALUE / 4;
		world.rolledTempo = rules.profile.tempo().name();
	}

	/** Steps the brain {@code ticks} times from hour 1, day 2, in a fixed stage, with tension held at 0 (no quiet). */
	static ScriptEnv drive(DirectorBrain brain, Solo memory, Stage stage, int ticks, long seed, DirectorBrain.Recorder rec) {
		DirectorRules rules = brain.rules();
		ScriptEnv env = new ScriptEnv(stage, 0, rules.attentionNeutral);
		RandomSource random = RandomSource.create(seed);
		DirectorBrain.Clock clock = new DirectorBrain.Clock(rules.hourTicks, 2 * DirectorBrain.DAY_TICKS);
		memory.startAt(clock);
		for (int i = 0; i < ticks; i++) {
			clock = clock.plus(rules.tickInterval);
			env.tension = 0;
			memory.step(brain, clock, env, random, rec);
		}
		return env;
	}

	/**
	 * Independent of the brain: true if this card cannot be drawn in this stage whatever the moment, because of the
	 * stage (its own, its tier's, a zero tag weight) or the profile (habits, the world's signature, once per world).
	 */
	static boolean outOfStageOrProfile(DirectorRules rules, CardInfo card, Stage stage, Set<String> oncePerWorldFired) {
		Stage tierMin = switch (card.tier()) {
			case AMBIENT -> Stage.ALONE;
			case MINOR -> rules.minorMinStage;
			case MAJOR -> rules.majorMinStage;
			case SIGNATURE -> rules.signatureMinStage;
		};
		if (!stage.atLeast(card.earliestStage()) || !stage.atLeast(tierMin)) {
			return true;
		}
		if (rules.tagWeight(stage, card) <= 0 || rules.habitWeight(card) <= 0) {
			return true;
		}
		if (DirectorBrain.oncePerWorld(card) && oncePerWorldFired.contains(card.id())) {
			return true;
		}
		return switch (card.id()) {
			case CardInfo.SIGNATURE_STILL_BURNING -> !rules.profile.hasStillBurning();
			case CardInfo.SIGNATURE_HOUSE_ELSEWHERE -> !rules.profile.hasHouseCopy();
			case CardInfo.SIGNATURE_CROSS_ROW -> rules.profile.signature() != Signature.CROSS_ROW;
			default -> false;
		};
	}

	/** A hand-driven env: stage, tension and attention fields, scripted context and fire results. */
	static final class ScriptEnv implements DirectorBrain.Env {
		Stage stage;
		double tension;
		double attention;
		Predicate<CardInfo> fits = card -> true;
		Function<CardInfo, FireResult> result = card -> FireResult.FIRED;
		final List<String> fired = new ArrayList<>();
		int fireCalls;

		ScriptEnv(Stage stage, double tension, double attention) {
			this.stage = stage;
			this.tension = tension;
			this.attention = attention;
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
			return attention;
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
			return fits.test(card);
		}

		@Override
		public FireResult fire(CardInfo card, boolean fake) {
			fireCalls++;
			FireResult r = result.apply(card);
			if (r == FireResult.FIRED) {
				fired.add(card.id());
			}
			return r;
		}
	}
}
