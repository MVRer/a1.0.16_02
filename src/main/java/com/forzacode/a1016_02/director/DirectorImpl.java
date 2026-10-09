package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

/**
 * The real {@link Director}: runs {@link DirectorBrain} every {@code Pacing.directorTickTicks()} while the subject
 * is online, against the live world. Cards fire for the subject only; every world change is the card's, through
 * {@code TraceService} and {@code MobTamper}. Persistent state is {@link DirectorData}.
 */
public final class DirectorImpl implements Director {
	private static final Set<String> BROKEN_CARDS = new HashSet<>();
	private final RandomSource random = RandomSource.create();
	private long countdown;

	// --- Director ---

	@Override
	public void tick(MinecraftServer server) {
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty() || --countdown > 0) {
			return;
		}
		DirectorRules rules = rules(server);
		countdown = rules.tickInterval;
		decide(server, subject.get(), rules);
		DirectorTriggers.periodic(server, subject.get(), rules, clock(server));
	}

	@Override
	public FireResult fire(MinecraftServer server, String cardId, boolean fake) {
		Optional<EventCard> card = CardRegistry.get(cardId);
		Optional<ServerPlayer> player = Services.watch().subject(server);
		if (card.isEmpty() || player.isEmpty()) {
			return FireResult.SKIPPED;
		}
		ServerPlayer subject = player.get();
		boolean asFake = fake && card.get().hasFake();
		FireResult result;
		try {
			result = card.get().fire(new FireContext(subject, subject.level(), asFake, true, random));
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] director: card {} threw on a forced fire", cardId, e);
			return FireResult.SKIPPED;
		}
		if (result == FireResult.FIRED) {
			DirectorData data = DirectorData.get(server);
			brain(server, rules(server)).recordForced(data.memory(), clock(server), CardInfo.of(card.get()), asFake,
					HerobrineState.get(server).stage(), DirectorLog.INSTANCE);
			data.setDirty();
			HerobrineEvents.CARD_FIRED.invoker().onCardFired(subject, cardId, asFake);
		}
		return result;
	}

	@Override
	public List<String> timewarp(MinecraftServer server, int days) {
		return timewarpReport(server, days).summary();
	}

	@Override
	public long ticksSinceTag(MinecraftServer server, CardTag tag) {
		long at = DirectorData.get(server).memory().lastTagFire(tag);
		return at == DirectorMemory.NEVER ? Long.MAX_VALUE : GameClock.playTicks(server) - at;
	}

	@Override
	public boolean inQuiet(MinecraftServer server) {
		return clock(server).dayTicks() < DirectorData.get(server).memory().quietUntilDayTicks();
	}

	@Override
	public List<String> debugLines(MinecraftServer server) {
		return DirectorDebug.stateLines(this, server);
	}

	// --- the live loop ---

	/** One decision now, obeying every gate ({@code /a1016 director tick}). */
	public String decideNow(MinecraftServer server) {
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			return "the subject is not online";
		}
		return decide(server, subject.get(), rules(server));
	}

	private String decide(MinecraftServer server, ServerPlayer player, DirectorRules rules) {
		DirectorData data = DirectorData.get(server);
		String outcome = brain(server, rules).step(data.memory(), clock(server), new WorldEnv(server, player), random, DirectorLog.INSTANCE);
		data.setDirty();
		DirectorLog.decision(outcome);
		return outcome;
	}

	/** The subject joined: a new session (join grace, empty-session roll). */
	void onJoin(MinecraftServer server) {
		DirectorRules rules = rules(server);
		DirectorData data = DirectorData.get(server);
		ServerPlayer player = Services.watch().subject(server).orElse(null);
		brain(server, rules).onJoin(data.memory(), clock(server), new WorldEnv(server, player), random, DirectorLog.INSTANCE);
		data.setDirty();
		countdown = rules.tickInterval;
	}

	void onLeave(MinecraftServer server) {
		DirectorData data = DirectorData.get(server);
		brain(server, rules(server)).onLeave(data.memory(), clock(server), DirectorLog.INSTANCE);
		data.setDirty();
	}

	/**
	 * Lore fired {@code TELLING}: every telling notes the day for "obeyed", but only one that names him moves to
	 * Telling (D-041; never back, never into Removal). Writing near his traces leaves the stage alone.
	 */
	void onTelling(MinecraftServer server, boolean namesHim) {
		DirectorData data = DirectorData.get(server);
		brain(server, rules(server)).onTelling(data.memory(), clock(server), new WorldEnv(server, null), namesHim, DirectorLog.INSTANCE);
		HerobrineState state = HerobrineState.get(server);
		if (namesHim && !state.tellingStarted()) {
			state.setTellingStarted(true);
		}
		data.setDirty();
	}

	/** Clears a forced quiet ({@code /a1016 director quiet clear}). */
	void clearQuiet(MinecraftServer server) {
		DirectorData data = DirectorData.get(server);
		data.memory().quietUntilDayTicks = -1;
		data.setDirty();
	}

	/**
	 * Timewarp: a dry run of the skipped time (nothing fires) for the summary, then the clock moves and the real
	 * state gets what time alone does: the stage clock and stages, tension decay, quiet running out.
	 */
	public DirectorSim.Result timewarpReport(MinecraftServer server, int days) {
		HerobrineState state = HerobrineState.get(server);
		DirectorData data = DirectorData.get(server);
		DirectorMemory memory = data.memory();
		DirectorRules rules = rules(server);
		DirectorConfig config = DirectorConfig.get();
		DirectorBrain.Clock before = clock(server);
		long span = Math.max(0, days) * DirectorBrain.DAY_TICKS;

		DirectorSim.Params params = DirectorSim.Params.from(config, rules);
		params.hours = span / (double) rules.hourTicks;
		params.seed = random.nextLong();
		params.continueSession = true;
		params.singleSession = true;
		params.attention = state.attention();
		// The summary shows what time alone does: nobody writes his name during a timewarp.
		params.tellingAtHour = -1;
		DirectorSim.Result result = DirectorSim.run(rules, cards(), config.historySize, memory, state.stage(), state.tension(), before, params);

		GameClock.warp(server, days);
		DirectorBrain brain = brain(server, rules);
		WorldEnv env = new WorldEnv(server, Services.watch().subject(server).orElse(null));
		for (long t = rules.tickInterval; t < span; t += rules.tickInterval) {
			brain.advanceTime(memory, before.plus(t), env, random, DirectorLog.INSTANCE);
		}
		brain.advanceTime(memory, before.plus(span), env, random, DirectorLog.INSTANCE);
		memory.lastWarpSummary = "+" + days + "d " + result.summary().getFirst();
		data.setDirty();
		for (String line : result.summary()) {
			A1016_02.LOGGER.info("[a1016] timewarp +{}d: {}", days, line);
		}
		return result;
	}

	/** A deterministic dry run from the current state ({@code /a1016 director sim}). Changes nothing. */
	public DirectorSim.Result simulate(MinecraftServer server, int hours, boolean synthetic) {
		return simulate(server, hours, synthetic, null);
	}

	/**
	 * As {@link #simulate(MinecraftServer, int, boolean)}, with the subject naming him {@code tellingAtHour} hours
	 * into the run (null: {@code simTellingAtHour} from the config, negative: never).
	 */
	public DirectorSim.Result simulate(MinecraftServer server, int hours, boolean synthetic, @Nullable Double tellingAtHour) {
		HerobrineState state = HerobrineState.get(server);
		DirectorRules rules = rules(server);
		DirectorConfig config = DirectorConfig.get();
		DirectorSim.Params params = DirectorSim.Params.from(config, rules);
		params.hours = hours;
		if (tellingAtHour != null) {
			params.tellingAtHour = tellingAtHour;
		}
		long play = GameClock.playTicks(server);
		params.seed = server.getWorldGenSettings().options().seed() ^ (play * 0x9E3779B97F4A7C15L) ^ (synthetic ? 0x5EED : 0);
		params.continueSession = Services.watch().subject(server).isPresent();
		params.attention = state.attention();
		List<CardInfo> cards = synthetic ? SyntheticDeck.cards() : cards();
		return DirectorSim.run(rules, cards, config.historySize, DirectorData.get(server).memory(), state.stage(), state.tension(),
				clock(server), params);
	}

	// --- helpers ---

	/** The live rules, rebuilt on every decision tick: config, profile and the {@link DirectorFlags}. */
	DirectorRules rules(MinecraftServer server) {
		return DirectorRules.live(HerobrineState.get(server));
	}

	DirectorBrain brain(MinecraftServer server, DirectorRules rules) {
		return new DirectorBrain(rules, cards(), DirectorConfig.get().historySize);
	}

	static List<CardInfo> cards() {
		List<CardInfo> out = new ArrayList<>();
		for (EventCard card : CardRegistry.all()) {
			try {
				out.add(CardInfo.of(card));
			} catch (RuntimeException e) {
				if (BROKEN_CARDS.add(card.id())) {
					A1016_02.LOGGER.error("[a1016] director: card {} is malformed and is left out of the decks", card.id(), e);
				}
			}
		}
		return out;
	}

	/** Play ticks and in-game day ticks (day including timewarp, plus the overworld time of day). */
	static DirectorBrain.Clock clock(MinecraftServer server) {
		return new DirectorBrain.Clock(GameClock.playTicks(server), GameClock.dayTicks(server));
	}

	/** The live world behind the brain. Cards fire for the subject only. */
	private final class WorldEnv implements DirectorBrain.Env {
		private final MinecraftServer server;
		private final @Nullable ServerPlayer player;

		WorldEnv(MinecraftServer server, @Nullable ServerPlayer player) {
			this.server = server;
			this.player = player;
		}

		@Override
		public Stage stage() {
			return HerobrineState.get(server).stage();
		}

		@Override
		public void setStage(Stage stage) {
			HerobrineState.get(server).setStage(server, stage);
		}

		@Override
		public double attention() {
			return HerobrineState.get(server).attention();
		}

		@Override
		public double tension() {
			return HerobrineState.get(server).tension();
		}

		@Override
		public void addTension(double delta, String reason) {
			if (delta > 1e-9) {
				Attention.raiseTension(server, delta, reason);
			} else if (delta < -1e-9) {
				Attention.lowerTension(server, -delta, reason);
			}
		}

		@Override
		public boolean contextFits(CardInfo card) {
			EventCard real = CardRegistry.get(card.id()).orElse(null);
			if (real == null || player == null) {
				return false;
			}
			try {
				return real.contextFits(player, player.level());
			} catch (RuntimeException e) {
				A1016_02.LOGGER.error("[a1016] director: card {} threw in contextFits", card.id(), e);
				return false;
			}
		}

		@Override
		public FireResult fire(CardInfo card, boolean fake) {
			EventCard real = CardRegistry.get(card.id()).orElse(null);
			if (real == null || player == null) {
				return FireResult.SKIPPED;
			}
			FireResult result;
			try {
				result = real.fire(new FireContext(player, player.level(), fake, false, random));
			} catch (RuntimeException e) {
				A1016_02.LOGGER.error("[a1016] director: card {} threw in fire", card.id(), e);
				return FireResult.SKIPPED;
			}
			if (result == FireResult.FIRED) {
				HerobrineEvents.CARD_FIRED.invoker().onCardFired(player, card.id(), fake);
			}
			return result == null ? FireResult.SKIPPED : result;
		}
	}

	/** Logs what the live director decides. */
	static final class DirectorLog implements DirectorBrain.Recorder {
		static final DirectorLog INSTANCE = new DirectorLog();
		private static final Set<String> QUIET_OUTCOMES = Set.of("nothing due");

		static void decision(String outcome) {
			if (DirectorConfig.get().verboseLog && !QUIET_OUTCOMES.contains(outcome)) {
				A1016_02.LOGGER.info("[a1016] director: {}", outcome);
			} else {
				A1016_02.LOGGER.debug("[a1016] director: {}", outcome);
			}
		}

		@Override
		public void stageChanged(DirectorBrain.Clock clock, Stage from, Stage to) {
			A1016_02.LOGGER.info("[a1016] director: stage {} -> {} at play {}", from, to, playTime(clock.playTicks()));
		}

		@Override
		public void sessionStarted(DirectorBrain.Clock clock, Stage stage, boolean empty, long emptyUntil) {
			A1016_02.LOGGER.info("[a1016] director: session starts in {}{}", stage, empty ? " (empty on purpose)" : "");
		}

		@Override
		public void gaveUp(DirectorBrain.Clock clock, Tier tier, String cardId, String why) {
			A1016_02.LOGGER.info("[a1016] director: {} card {} goes back into its deck ({})", tier, cardId, why);
		}

		@Override
		public void refilled(DirectorBrain.Clock clock, Tier tier) {
			A1016_02.LOGGER.debug("[a1016] director: {} deck ran out and was reshuffled", tier);
		}

		@Override
		public void fired(DirectorBrain.Clock clock, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
			A1016_02.LOGGER.info("[a1016] director: {} {}{}{} in {}", card.tier(), card.id(), fake ? " (fake)" : "", forced ? " (forced)" : "", stage);
		}

		@Override
		public void quietStarted(DirectorBrain.Clock clock, int days, long untilDayTicks, double tensionBefore) {
			A1016_02.LOGGER.info(String.format(Locale.ROOT, "[a1016] director: tension %.1f, quiet for %d in-game days", tensionBefore, days));
		}

		static String playTime(long ticks) {
			return String.format(Locale.ROOT, "%dh%02dm", ticks / 72000, ticks / 1200 % 60);
		}
	}
}
