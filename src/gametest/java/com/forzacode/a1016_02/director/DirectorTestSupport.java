package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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

/** Fixtures for {@link DirectorGameTests}: fixed-seed 20 h playthroughs over the synthetic deck, and a scripted env. */
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
