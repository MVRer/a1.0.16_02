package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.LongFunction;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.WorldProfile;

import net.minecraft.server.MinecraftServer;

import org.jspecify.annotations.Nullable;

/**
 * Read-only view of the director for debug tools (the dev overlay and the scripted playthrough). Nothing here
 * changes the director's memory, the shared state or what the director decides: the snapshot only reads (it never
 * even creates a pacing for a player who has none), and a dry run works on a fresh copy. Server thread only.
 */
public final class DirectorApi {
	private DirectorApi() {
	}

	/**
	 * A card the director drew and holds for the player.
	 *
	 * @param heldTicks play ticks since it was drawn
	 * @param waiting   why it has not fired yet: a gate, or "its moment" when every gate is open and only the
	 *                  card's own context (or an out-of-view spot) is missing
	 */
	public record Held(Tier tier, String cardId, long heldTicks, String waiting) {
	}

	/**
	 * When a tier may fire next for the player.
	 *
	 * @param binding    the gate that lifts last ("open" if none, "stage" if the stage is too early)
	 * @param allowedIn  play ticks until every timed gate is open, 0 if open now, -1 if only a stage change opens it
	 * @param dueInTicks majors only: play ticks until the scheduled major slot, -1 if none is scheduled
	 */
	public record Next(String binding, long allowedIn, long dueInTicks) {
	}

	/**
	 * The live director at one moment, seen by one player: the shared world (stage, attention, tempo, day, the world
	 * clock) and that player's own pacing (tension, quiet, pity, held cards, last fire, next minor and major, session,
	 * their own clock).
	 *
	 * @param player             the player's name as last seen
	 * @param participating      they take part right now (online, not a spectator)
	 * @param participants       how many take part right now
	 * @param tension            the player's own tension
	 * @param playTicks          the player's own play ticks
	 * @param worldPlayTicks     the world clock (stages move on it)
	 * @param hourTicks          play ticks in one real hour (smaller in devFastMode); use it to show durations
	 * @param quietUntilDayTicks in-game day ticks at which the player's running quiet ends, or -1
	 * @param blocked            why nothing at all may fire for them right now, or null
	 * @param lastFire           newest fire in their history (theirs or felt, fake or real, forced included), or null
	 * @param lastFireAgo        play ticks since {@code lastFire}
	 * @param sessionTicks       play ticks since their session started, or -1 if no session is open
	 */
	public record Snapshot(String player, boolean participating, int participants, Stage stage, double attention, double tension,
			double tensionThreshold, Tempo tempo, double paceFactor, long playTicks, long worldPlayTicks, long dayTicks, long hourTicks,
			boolean inQuiet, long quietUntilDayTicks, double pity, @Nullable String blocked, List<Held> held,
			DirectorMemory.@Nullable HistoryEntry lastFire, long lastFireAgo, Next minor, Next major, long sessionTicks, boolean sessionEmpty) {
	}

	/** Reads the live director state as {@code viewer} sees it. Changes nothing. */
	public static Snapshot snapshot(MinecraftServer server, UUID viewer) {
		HerobrineState state = HerobrineState.get(server);
		DirectorRules rules = DirectorRules.live(state);
		DirectorBrain brain = new DirectorBrain(rules, DirectorImpl.cards(), DirectorConfig.get().historySize);
		DirectorMemory memory = DirectorData.get(server).memory();
		WorldMemory w = memory.world();
		PacingMemory m = memory.find(viewer).orElseGet(PacingMemory::new);
		DirectorBrain.Clock c = DirectorImpl.clock(server, viewer);
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
				why = card == null ? "no longer registered" : brain.fireBlock(card, w, m, c, stage);
			}
			held.add(new Held(tier, id, Math.max(0, now - m.heldSince.getOrDefault(tier, now)), why == null ? "its moment" : why));
		}

		DirectorMemory.HistoryEntry last = m.history.isEmpty() ? null : m.history.getLast();
		boolean inQuiet = brain.inQuiet(m, c);
		return new Snapshot(DirectorImpl.name(server, viewer), Services.watch().participant(server, viewer).isPresent(),
				Services.watch().participants(server).size(), stage, state.attention(), state.tension(viewer), rules.tensionThreshold,
				rules.profile.tempo(), rules.profile.tempo().paceFactor(), now, c.worldTicks(), c.dayTicks(), rules.hourTicks, inQuiet,
				inQuiet ? m.quietUntilDayTicks : -1, brain.pity(m, c), blocked, List.copyOf(held), last,
				last == null ? 0 : Math.max(0, now - last.playTicks()), next(Tier.MINOR, brain, rules, m, c, stage),
				next(Tier.MAJOR, brain, rules, m, c, stage), m.sessionStart < 0 ? -1 : Math.max(0, now - m.sessionStart),
				m.sessionEmpty && now < m.sessionEmptyUntil);
	}

	/** Every registered card as the decks see it (malformed cards are left out, as the live director does). */
	public static List<CardInfo> registeredCards() {
		return DirectorImpl.cards();
	}

	/**
	 * A deterministic dry run for one player from a fresh state: play tick 0, day 0, Alone, tension 0, a new memory, the
	 * live config and this profile. Contexts fit and sessions start and end by dice seeded with {@code seed} (see
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
		return DirectorSim.run(rules, cards, Math.max(config.historySize, 1), new WorldMemory(), new PacingMemory(), Stage.ALONE, 0,
				new DirectorBrain.Clock(0, 0), params);
	}

	/**
	 * As {@link #dryRun}, for several players from a fresh world (see {@link DirectorSim#runGroup}): each seat joins at
	 * its own hour, and {@code sharing} says who feels whose cards.
	 */
	public static DirectorSim.Group dryRunGroup(WorldProfile profile, Collection<CardInfo> cards, double hours, long seed, double attention,
			List<DirectorSim.Seat> seats, DirectorSim.Sharing sharing) {
		DirectorRules rules = DirectorRules.current(profile);
		DirectorConfig config = DirectorConfig.get();
		DirectorSim.Params params = DirectorSim.Params.from(config, rules);
		params.hours = hours;
		params.seed = seed;
		params.attention = attention;
		return DirectorSim.runGroup(rules, cards, Math.max(config.historySize, 1), seats, sharing, params);
	}

	/**
	 * Seeds for multi-seed dry runs (tuning and the soft-rate floors test): the playthrough's default seed first, then
	 * 1 to 191. One seed is one world's dice: pooled over 32 seeds a rate still swings about 5% from one set to the
	 * next, over 192 about 1 to 2%. A 20 h dry run takes a few milliseconds, so the wide set is cheap.
	 */
	public static List<Long> tuningSeeds() {
		List<Long> seeds = new ArrayList<>();
		seeds.add(1016L);
		for (long seed = 1; seed < 192; seed++) {
			seeds.add(seed);
		}
		return List.copyOf(seeds);
	}

	/**
	 * {@link #dryRun} once per seed, each from a fresh state with the profile {@code profiles} gives for that seed
	 * (for example the seed's rolled profile with a fixed tempo). Pool the rates with {@link DirectorSim.Rates#of}.
	 * Same arguments and config, same runs.
	 */
	public static List<DirectorSim.Result> dryRuns(LongFunction<WorldProfile> profiles, Collection<CardInfo> cards, double hours,
			Collection<Long> seeds, double attention) {
		List<DirectorSim.Result> out = new ArrayList<>();
		for (long seed : seeds) {
			out.add(dryRun(profiles.apply(seed), cards, hours, seed, attention));
		}
		return out;
	}

	/** Attention at which the stage clock runs at the tempo's own pace. */
	public static double neutralAttention() {
		return DirectorConfig.get().attentionNeutral;
	}

	private static Next next(Tier tier, DirectorBrain brain, DirectorRules rules, PacingMemory m, DirectorBrain.Clock c, Stage stage) {
		long due = tier == Tier.MAJOR && m.nextMajorDue >= 0 ? Math.max(0, m.nextMajorDue - c.playTicks()) : -1;
		Stage min = brain.tierMinStage(tier);
		if (!stage.atLeast(min)) {
			return new Next("stage (from " + min + ")", -1, due);
		}
		long now = c.playTicks();
		if (rules.silenceUntilDay == DirectorFlags.FOREVER) {
			return new Next("silence (for good)", -1, due);
		}
		Wait wait = new Wait();
		if (rules.silenceUntilDay != DirectorFlags.NO_SILENCE) {
			wait.consider("silence", rules.silenceUntilDay * DirectorBrain.DAY_TICKS - c.dayTicks());
		}
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
			wait.consider("first day", brain.firstDayEnd(m) - c.dayTicks());
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
