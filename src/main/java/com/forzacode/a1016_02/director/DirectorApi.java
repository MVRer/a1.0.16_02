package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.WorldProfile;

import net.minecraft.server.MinecraftServer;

import org.jspecify.annotations.Nullable;

/**
 * Read-only view of the director for debug tools (the dev overlay and the scripted playthrough). Nothing here
 * changes the director's memory, the shared state or what the director decides: the snapshot only reads, and a
 * dry run works on a fresh copy. Server thread only.
 */
public final class DirectorApi {
	private DirectorApi() {
	}

	/**
	 * A card the director drew and holds.
	 *
	 * @param heldTicks play ticks since it was drawn
	 * @param waiting   why it has not fired yet: a gate, or "its moment" when every gate is open and only the
	 *                  card's own context (or an out-of-view spot) is missing
	 */
	public record Held(Tier tier, String cardId, long heldTicks, String waiting) {
	}

	/**
	 * When a tier may fire next.
	 *
	 * @param binding    the gate that lifts last ("open" if none, "stage" if the stage is too early)
	 * @param allowedIn  play ticks until every timed gate is open, 0 if open now, -1 if only a stage change opens it
	 * @param dueInTicks majors only: play ticks until the scheduled major slot, -1 if none is scheduled
	 */
	public record Next(String binding, long allowedIn, long dueInTicks) {
	}

	/**
	 * The live director at one moment.
	 *
	 * @param hourTicks          play ticks in one real hour (smaller in devFastMode); use it to show durations
	 * @param quietUntilDayTicks in-game day ticks at which the running quiet ends, or -1
	 * @param blocked            why nothing at all may fire right now, or null
	 * @param lastFire           newest fire in the history (fake or real, forced included), or null
	 * @param lastFireAgo        play ticks since {@code lastFire}
	 * @param sessionTicks       play ticks since the subject joined, or -1 if no session is open
	 */
	public record Snapshot(Stage stage, double attention, double tension, double tensionThreshold, Tempo tempo, double paceFactor,
			long playTicks, long dayTicks, long hourTicks, boolean inQuiet, long quietUntilDayTicks, double pity, @Nullable String blocked,
			List<Held> held, DirectorMemory.@Nullable HistoryEntry lastFire, long lastFireAgo, Next minor, Next major, long sessionTicks,
			boolean sessionEmpty) {
	}

	/** Reads the live director state. Changes nothing. */
	public static Snapshot snapshot(MinecraftServer server) {
		HerobrineState state = HerobrineState.get(server);
		DirectorRules rules = DirectorRules.current(state.profile());
		DirectorBrain brain = new DirectorBrain(rules, DirectorImpl.cards(), DirectorConfig.get().historySize);
		DirectorMemory m = DirectorData.get(server).memory();
		DirectorBrain.Clock c = DirectorImpl.clock(server);
		Stage stage = state.stage();
		long now = c.playTicks();
		String blocked = brain.globalBlock(m, c);

		List<Held> held = new ArrayList<>();
		for (Tier tier : DirectorBrain.PRIORITY) {
			String id = m.held.get(tier);
			if (id == null) {
				continue;
			}
			CardInfo card = brain.card(id);
			String why = blocked;
			if (why == null) {
				why = card == null ? "no longer registered" : brain.fireBlock(card, m, c, stage);
			}
			held.add(new Held(tier, id, Math.max(0, now - m.heldSince.getOrDefault(tier, now)), why == null ? "its moment" : why));
		}

		DirectorMemory.HistoryEntry last = m.history.isEmpty() ? null : m.history.getLast();
		boolean inQuiet = brain.inQuiet(m, c);
		return new Snapshot(stage, state.attention(), state.tension(), rules.tensionThreshold, rules.profile.tempo(),
				rules.profile.tempo().paceFactor(), now, c.dayTicks(), rules.hourTicks, inQuiet, inQuiet ? m.quietUntilDayTicks : -1,
				brain.pity(m, c), blocked, List.copyOf(held), last, last == null ? 0 : Math.max(0, now - last.playTicks()),
				next(Tier.MINOR, brain, rules, m, c, stage), next(Tier.MAJOR, brain, rules, m, c, stage),
				m.sessionStart < 0 ? -1 : Math.max(0, now - m.sessionStart), m.sessionEmpty && now < m.sessionEmptyUntil);
	}

	/** Every registered card as the decks see it (malformed cards are left out, as the live director does). */
	public static List<CardInfo> registeredCards() {
		return DirectorImpl.cards();
	}

	/**
	 * A deterministic dry run from a fresh state: play tick 0, day 0, Alone, tension 0, a new memory, the live config
	 * and this profile. Contexts fit and sessions start and end by dice seeded with {@code seed} (see
	 * {@link DirectorSim}). Nothing outside the run changes.
	 *
	 * @param attention held constant for the whole run ({@link #neutralAttention()} keeps the tempo's own pace)
	 */
	public static DirectorSim.Result dryRun(WorldProfile profile, Collection<CardInfo> cards, double hours, long seed, double attention) {
		DirectorRules rules = DirectorRules.current(profile);
		DirectorConfig config = DirectorConfig.get();
		DirectorSim.Params params = DirectorSim.Params.from(config, rules);
		params.hours = hours;
		params.seed = seed;
		params.attention = attention;
		return DirectorSim.run(rules, cards, Math.max(config.historySize, 1), new DirectorMemory(), Stage.ALONE, 0,
				new DirectorBrain.Clock(0, 0), params);
	}

	/** Attention at which the stage clock runs at the tempo's own pace. */
	public static double neutralAttention() {
		return DirectorConfig.get().attentionNeutral;
	}

	private static Next next(Tier tier, DirectorBrain brain, DirectorRules rules, DirectorMemory m, DirectorBrain.Clock c, Stage stage) {
		long due = tier == Tier.MAJOR && m.nextMajorDue >= 0 ? Math.max(0, m.nextMajorDue - c.playTicks()) : -1;
		Stage min = brain.tierMinStage(tier);
		if (!stage.atLeast(min)) {
			return new Next("stage (from " + min + ")", -1, due);
		}
		long now = c.playTicks();
		Wait wait = new Wait();
		if (m.sessionStart >= 0) {
			wait.consider("join grace", m.sessionStart + rules.joinGrace - now);
		}
		if (brain.inQuiet(m, c)) {
			wait.consider("quiet", m.quietUntilDayTicks - c.dayTicks());
		}
		if (m.sessionEmpty) {
			wait.consider("empty session", m.sessionEmptyUntil - now);
		}
		wait.consider("just fired", m.lastFireAny + rules.minAnyGap - now);
		if (tier == Tier.MINOR) {
			wait.consider("minor gap", m.lastFireTier.getOrDefault(Tier.MINOR, DirectorMemory.NEVER) + rules.minorGap - now);
		} else {
			wait.consider("major gap", m.lastMajorOrSignature + rules.majorGap - now);
			wait.consider("first day", rules.noMajorBeforeDay * DirectorBrain.DAY_TICKS - c.dayTicks());
		}
		return new Next(wait.binding, wait.ticks, due);
	}

	/** The gate that lifts last. */
	private static final class Wait {
		String binding = "open";
		long ticks;

		void consider(String gate, long left) {
			if (left > ticks) {
				ticks = left;
				binding = gate;
			}
		}
	}
}
