package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Density;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogSpell;
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
	 * change the numbers. Computed once per tempo (the runs are deterministic; callers only read them).
	 */
	static synchronized List<DirectorSim.Result> registryRuns(Tempo tempo) {
		return REGISTRY_RUNS.computeIfAbsent(tempo, t -> registryRuns(t, new DirectorConfig()));
	}

	private static final Map<Tempo, List<DirectorSim.Result>> REGISTRY_RUNS = new EnumMap<>(Tempo.class);

	/** {@link #registryRuns(Tempo)} with this director section (a tuning variant), never cached. */
	static List<DirectorSim.Result> registryRuns(Tempo tempo, DirectorConfig config) {
		List<CardInfo> deck = registryDeck();
		List<DirectorSim.Result> out = new ArrayList<>();
		for (long seed : DirectorApi.tuningSeeds()) {
			DirectorRules rules = DirectorRules.from(new Pacing(), config, com.forzacode.a1016_02.debug.Playthrough.profile(seed, tempo));
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
	 * D-070's first hour over these runs (fakes count, each run's own fires): how many runs fired first within 30 and
	 * 45 minutes, how many had 2 or more fires in the first 60, and the most any run had in it.
	 */
	record FirstHour(int runs, int by30, int by45, int twoOrMore, int most, double medianFirstMinutes) {
		static FirstHour of(List<DirectorSim.Result> results) {
			int by30 = 0;
			int by45 = 0;
			int two = 0;
			int most = 0;
			List<Double> firsts = new ArrayList<>();
			for (DirectorSim.Result r : results) {
				long hour = r.rules.hourTicks;
				List<DirectorSim.Event> fires = r.fires();
				long first = fires.isEmpty() ? Long.MAX_VALUE : fires.getFirst().play() - r.startPlay;
				by30 += first <= hour / 2 ? 1 : 0;
				by45 += first <= hour * 3 / 4 ? 1 : 0;
				int inHour = (int) fires.stream().filter(e -> e.play() - r.startPlay < hour).count();
				two += inHour >= 2 ? 1 : 0;
				most = Math.max(most, inHour);
				firsts.add(first == Long.MAX_VALUE ? Double.POSITIVE_INFINITY : first * 60.0 / hour);
			}
			firsts.sort(null);
			return new FirstHour(results.size(), by30, by45, two, most, firsts.isEmpty() ? 0 : firsts.get(firsts.size() / 2));
		}

		String summary() {
			return String.format(java.util.Locale.ROOT, "%d runs: first fire by 30 min %.1f%%, by 45 min %.1f%% (median %.1f min), 2+ in the first hour %.1f%%, "
					+ "at most %d in it", runs, 100.0 * by30 / runs, 100.0 * by45 / runs, medianFirstMinutes, 100.0 * twoOrMore / runs, most);
		}
	}

	/**
	 * DESIGN.md 4b as the dry runs show it, pooled over runs (each run's own fires, time summed): Alone's length, the
	 * ambients per hour in each stage, Traces' and Proximity's minors per hour, Proximity's hours per major, and the
	 * first accident card's hour.
	 */
	record FourB(int runs, double aloneHours, double aloneAmbientsPerHour, double tracesAmbientsPerHour, double tracesMinorsPerHour,
			double proximityAmbientsPerHour, double proximityMinorsPerHour, double proximityMinorsPerActiveHour, double hoursPerMajor,
			double activeHoursPerMajor, double firstAccidentMedianHours, double firstAccidentMinHours, int runsWithAnAccident) {
		static FourB of(List<DirectorSim.Result> results, Collection<CardInfo> deck) {
			Map<String, CardInfo> byId = new HashMap<>();
			deck.forEach(card -> byId.put(card.id(), card));
			long hour = results.isEmpty() ? 1 : results.getFirst().rules.hourTicks;
			Map<Stage, Long> ambients = new EnumMap<>(Stage.class);
			Map<Stage, Long> minors = new EnumMap<>(Stage.class);
			long majors = 0;
			List<Double> accidents = new ArrayList<>();
			for (DirectorSim.Result r : results) {
				boolean accident = false;
				for (DirectorSim.Event e : r.fires()) {
					if (e.tier() == Tier.AMBIENT) {
						ambients.merge(e.stage(), 1L, Long::sum);
					} else if (e.tier() == Tier.MINOR) {
						minors.merge(e.stage(), 1L, Long::sum);
					} else if (e.tier() == Tier.MAJOR && e.stage() == Stage.PROXIMITY) {
						majors++;
					}
					CardInfo card = byId.get(e.cardId());
					if (!accident && card != null && card.has(CardTag.ACCIDENT)) {
						accident = true;
						accidents.add((e.play() - r.startPlay) / (double) hour);
					}
				}
			}
			long aloneTicks = sum(results, Stage.ALONE, false);
			long traces = sum(results, Stage.TRACES, false);
			long proximity = sum(results, Stage.PROXIMITY, false);
			long proximityActive = sum(results, Stage.PROXIMITY, true);
			accidents.sort(null);
			return new FourB(results.size(), aloneTicks / (double) hour / Math.max(1, results.size()), per(ambients.get(Stage.ALONE), aloneTicks, hour),
					per(ambients.get(Stage.TRACES), traces, hour), per(minors.get(Stage.TRACES), traces, hour), per(ambients.get(Stage.PROXIMITY), proximity, hour),
					per(minors.get(Stage.PROXIMITY), proximity, hour), per(minors.get(Stage.PROXIMITY), proximityActive, hour),
					majors == 0 ? Double.POSITIVE_INFINITY : proximity / (double) hour / majors,
					majors == 0 ? Double.POSITIVE_INFINITY : proximityActive / (double) hour / majors,
					accidents.isEmpty() ? Double.NaN : accidents.get(accidents.size() / 2), accidents.isEmpty() ? Double.NaN : accidents.getFirst(),
					accidents.size());
		}

		private static long sum(List<DirectorSim.Result> results, Stage stage, boolean active) {
			long total = 0;
			for (DirectorSim.Result r : results) {
				total += active ? r.activeTicks(stage) : r.stageTicks(stage);
			}
			return total;
		}

		private static double per(Long count, long ticks, long hour) {
			return ticks <= 0 ? 0 : (count == null ? 0 : count) / (ticks / (double) hour);
		}

		String summary() {
			return String.format(java.util.Locale.ROOT, "%d runs | Alone %.2fh, %.2f amb/h | Traces %.2f amb/h, %.2f minors/h | Proximity %.2f amb/h, "
					+ "%.2f minors/h (%.2f active), a major every %.2fh (%.2fh active) | first accident median %.2fh, earliest %.2fh (%d runs)", runs,
					aloneHours, aloneAmbientsPerHour, tracesAmbientsPerHour, tracesMinorsPerHour, proximityAmbientsPerHour, proximityMinorsPerHour,
					proximityMinorsPerActiveHour, hoursPerMajor, activeHoursPerMajor, firstAccidentMedianHours, firstAccidentMinHours, runsWithAnAccident);
		}
	}

	/**
	 * Fires per hour under each sky (D-071), pooled over runs: [all time, active time] for a fog spell that boosts, a
	 * quiet spell and no spell.
	 */
	record SkyRates(double fog, double fogActive, double quiet, double quietActive, double clear, double clearActive, double fogHours,
			double quietHours) {
		static SkyRates of(List<DirectorSim.Result> results) {
			long hour = results.isEmpty() ? 1 : results.getFirst().rules.hourTicks;
			double[] rate = new double[6];
			double[] hours = new double[2];
			DirectorSim.Sky[] skies = {DirectorSim.Sky.FOG, DirectorSim.Sky.QUIET, DirectorSim.Sky.CLEAR};
			for (int i = 0; i < skies.length; i++) {
				long fires = 0;
				long ticks = 0;
				long active = 0;
				for (DirectorSim.Result r : results) {
					fires += r.skyFires(skies[i]);
					ticks += r.skyTicks(skies[i]);
					active += r.skyActiveTicks(skies[i]);
				}
				rate[2 * i] = ticks <= 0 ? 0 : fires / (ticks / (double) hour);
				rate[2 * i + 1] = active <= 0 ? 0 : fires / (active / (double) hour);
				if (i < 2) {
					hours[i] = ticks / (double) hour;
				}
			}
			return new SkyRates(rate[0], rate[1], rate[2], rate[3], rate[4], rate[5], hours[0], hours[1]);
		}

		String summary() {
			return String.format(java.util.Locale.ROOT, "fires/h: fog %.2f (%.2f active, %.0fh), quiet fog %.2f (%.2f active, %.0fh), clear %.2f (%.2f active)", fog,
					fogActive, fogHours, quiet, quietActive, quietHours, clear, clearActive);
		}
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
	 * their own first in-game day, at most the Alone ambient cap and nothing else in Alone, at most five moments in
	 * their own first hour (D-070), no accident card before their own first-accident time. One line per breach.
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
		int firstHour = r.startPacing.firstHourFires;
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
			if (e.play() < rules.hourTicks && ++firstHour > rules.firstHourMaxFires) {
				out.add(at + "moment number " + firstHour + " in their first hour");
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
		Optional<FogSpell> fog = Optional.empty();
		final List<String> fired = new ArrayList<>();

		PlayerEnv(SharedStage world) {
			this.world = world;
		}

		@Override
		public Optional<FogSpell> fogSpell() {
			return fog;
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
		Optional<FogSpell> fog = Optional.empty();
		final List<String> fired = new ArrayList<>();
		int fireCalls;

		ScriptEnv(Stage stage, double tension, double attention) {
			this.stage = stage;
			this.tension = tension;
			this.attention = attention;
		}

		@Override
		public Optional<FogSpell> fogSpell() {
			return fog;
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
