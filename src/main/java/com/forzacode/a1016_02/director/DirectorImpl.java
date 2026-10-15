package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

/**
 * The real {@link Director}: runs {@link DirectorBrain} against the live world. Every targetable participant
 * ({@code PlayerWatch.isTargetable}) gets their own decisions every {@code Pacing.directorTickTicks()} on their own
 * staggered cadence ({@link DecisionSchedule}), from their own {@link PacingMemory} and clock (D-059); a card fires
 * for the player whose pacing picked it, and counts as felt for everyone sharing the moment (D-060.3). The world's
 * time (the stage clock) and the triggers that count once per occasion run on a world cadence of the same length
 * while anyone takes part. Sessions follow {@code HerobrineEvents.PARTICIPATION}, per player. Only UUIDs are kept
 * across ticks; each decision looks its player up again and skips quietly if they are gone or dead (D-062.1). Every
 * world change is the card's, through {@code TraceService} and {@code MobTamper}. Persistent state is
 * {@link DirectorData}.
 */
public final class DirectorImpl implements Director {
	private static final Set<String> BROKEN_CARDS = new HashSet<>();
	private final RandomSource random = RandomSource.create();
	private final DecisionSchedule schedule = new DecisionSchedule();
	/** Server tick of the next world step, and the interval it was set with; 0 after a server stop. */
	private long nextWorldTick;
	private long worldInterval;

	// --- Director ---

	@Override
	public void tick(MinecraftServer server) {
		long now = server.getTickCount();
		// A tick count that went back (a new server without a stop) restarts the world cadence, and every player's:
		// their next decisions would otherwise wait for ticks far ahead of the new count, and the world step would
		// not start them again since they are still scheduled. Their sessions go on as they were.
		boolean wentBack = nextWorldTick - now > worldInterval;
		if (now >= nextWorldTick || wentBack) {
			DirectorRules rules = rules(server);
			if (wentBack) {
				schedule.restart(now, rules.tickInterval);
			}
			worldInterval = rules.tickInterval;
			nextWorldTick = now + rules.tickInterval;
			worldStep(server, rules);
		}
		if (!schedule.anyDue(now)) {
			return;
		}
		DirectorRules rules = rules(server);
		PlayerWatch watch = Services.watch();
		for (UUID uuid : schedule.due(now, rules.tickInterval)) {
			Optional<ServerPlayer> player = watch.participant(server, uuid).filter(watch::isTargetable);
			if (player.isEmpty()) {
				continue; // dead, or gone and not told yet: skip quietly (D-062.1)
			}
			decide(server, player.get(), rules);
			DirectorData data = DirectorData.get(server);
			data.memory().find(uuid).ifPresent(pacing -> DirectorTriggers.periodicFor(server, player.get(), pacing, rules));
			data.setDirty();
		}
	}

	@Override
	public FireResult fire(MinecraftServer server, ServerPlayer player, String cardId, boolean fake) {
		Optional<EventCard> card = CardRegistry.get(cardId);
		PlayerWatch watch = Services.watch();
		if (card.isEmpty() || !watch.isTargetable(player)) {
			return FireResult.SKIPPED;
		}
		boolean asFake = fake && card.get().hasFake();
		// Who shares the moment is taken as it happens (D-060.3), before the card can change anything.
		List<ServerPlayer> sharing = watch.sharingWith(player);
		FireResult result;
		try {
			result = card.get().fire(new FireContext(player, player.level(), asFake, true, random));
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] director: card {} threw on a forced fire", cardId, e);
			return FireResult.SKIPPED;
		}
		if (result == FireResult.FIRED) {
			DirectorData data = DirectorData.get(server);
			DirectorMemory memory = data.memory();
			UUID uuid = player.getUUID();
			DirectorBrain.Clock c = clock(server, uuid);
			brain(rules(server)).recordForced(memory.world(), memory.pacing(uuid, c.dayTicks()), c, CardInfo.of(card.get()), asFake,
					HerobrineState.get(server).stage(), sharers(server, memory, uuid, sharing), random, log(server, uuid));
			data.setDirty();
			HerobrineEvents.CARD_FIRED.invoker().onCardFired(player, cardId, asFake);
		}
		return result == null ? FireResult.SKIPPED : result;
	}

	/** Timewarp with the summary from the first participant's pacing (else the first known player's, D-062.9). */
	@Override
	public List<String> timewarp(MinecraftServer server, int days) {
		return timewarp(server, days, focus(server));
	}

	/** Timewarp (every clock moves) with the summary from this player's pacing; null for a fresh player's. */
	public List<String> timewarp(MinecraftServer server, int days, @Nullable UUID focus) {
		DirectorSim.Result result = timewarpReport(server, days, focus);
		List<String> lines = new ArrayList<>(result.summary());
		lines.add(focus == null ? "(dry run of a fresh player's pacing: nobody is known yet)" : "(dry run of " + name(server, focus) + "'s pacing; "
				+ "every known player's clock moved)");
		return lines;
	}

	/** Play ticks of the world clock since a card with this tag fired for anyone, or {@link Long#MAX_VALUE} if none ever did. */
	@Override
	public long ticksSinceTag(MinecraftServer server, CardTag tag) {
		long at = DirectorData.get(server).memory().world().lastTagFire(tag);
		return at == DirectorMemory.NEVER ? Long.MAX_VALUE : GameClock.playTicks(server) - at;
	}

	/**
	 * That player's own play ticks since a card with this tag fired for them or was felt by them (D-060.3), or
	 * {@link Long#MAX_VALUE} if none ever did. The per-player form of {@link #ticksSinceTag(MinecraftServer, CardTag)}.
	 */
	@Override
	public long ticksSinceTag(MinecraftServer server, UUID player, CardTag tag) {
		long at = DirectorData.get(server).memory().find(player).map(m -> m.lastTagFire(tag)).orElse(DirectorMemory.NEVER);
		return at == DirectorMemory.NEVER ? Long.MAX_VALUE : GameClock.playTicks(server, player) - at;
	}

	/** True while any participant is in a forced quiet of their own (D-059; the conservative answer for one flag). */
	@Override
	public boolean inQuiet(MinecraftServer server) {
		DirectorMemory memory = DirectorData.get(server).memory();
		long dayTicks = GameClock.dayTicks(server);
		for (ServerPlayer player : Services.watch().participants(server)) {
			Optional<PacingMemory> pacing = memory.find(player.getUUID());
			if (pacing.isPresent() && dayTicks < pacing.get().quietUntilDayTicks()) {
				return true;
			}
		}
		return false;
	}

	/** True while this player is in a forced quiet of their own. */
	@Override
	public boolean inQuiet(MinecraftServer server, UUID player) {
		return DirectorData.get(server).memory().find(player).map(m -> GameClock.dayTicks(server) < m.quietUntilDayTicks()).orElse(false);
	}

	@Override
	public List<String> debugLines(MinecraftServer server) {
		return DirectorDebug.stateLines(this, server);
	}

	// --- the live loop ---

	/**
	 * The world's step: the stage clock on the world clock, and the triggers that count once per occasion. Also
	 * starts or stops a session the participation events somehow missed, so nobody is left without decisions.
	 */
	private void worldStep(MinecraftServer server, DirectorRules rules) {
		List<ServerPlayer> participants = Services.watch().participants(server);
		Set<UUID> taking = new HashSet<>();
		for (ServerPlayer player : participants) {
			taking.add(player.getUUID());
			if (!schedule.contains(player.getUUID())) {
				onJoin(server, player.getUUID());
			}
		}
		for (UUID uuid : schedule.players()) {
			if (!taking.contains(uuid)) {
				onLeave(server, uuid);
			}
		}
		if (participants.isEmpty()) {
			return;
		}
		DirectorData data = DirectorData.get(server);
		DirectorBrain.Clock world = worldClock(server);
		brain(rules).advanceWorld(data.memory().world(), world, new WorldEnv(server, null, null), random, DirectorLog.WORLD);
		DirectorTriggers.periodicWorld(server, participants, rules, world);
		data.setDirty();
	}

	/** One decision now for this player, obeying every gate ({@code /a1016 director tick}). */
	public String decideNow(MinecraftServer server, ServerPlayer player) {
		if (!Services.watch().isTargetable(player)) {
			return player.getName().getString() + " is not taking part (a spectator, dead, or offline)";
		}
		return decide(server, player, rules(server));
	}

	private String decide(MinecraftServer server, ServerPlayer player, DirectorRules rules) {
		DirectorData data = DirectorData.get(server);
		DirectorMemory memory = data.memory();
		UUID uuid = player.getUUID();
		DirectorBrain.Clock c = clock(server, uuid);
		String outcome = brain(rules).step(memory.world(), memory.pacing(uuid, c.dayTicks()), c, new WorldEnv(server, uuid, player), random,
				log(server, uuid));
		data.setDirty();
		DirectorLog.decision(player.getName().getString(), outcome);
		return outcome;
	}

	/** {@code HerobrineEvents.PARTICIPATION} true: this player's own session and join grace start (D-062.4). */
	void onJoin(MinecraftServer server, UUID player) {
		DirectorRules rules = rules(server);
		DirectorData data = DirectorData.get(server);
		DirectorMemory memory = data.memory();
		DirectorBrain.Clock c = clock(server, player);
		// Looked up for this call only: whoever they start next to (their session takes that session's mood).
		ServerPlayer online = Services.watch().participant(server, player).orElse(null);
		brain(rules).onJoin(memory.world(), memory.pacing(player, c.dayTicks()), c, new WorldEnv(server, player, online), random,
				log(server, player));
		data.setDirty();
		schedule.start(player, server.getTickCount(), rules.tickInterval);
	}

	/** {@code HerobrineEvents.PARTICIPATION} false (left, or a spectator now): only this player's session ends. */
	void onLeave(MinecraftServer server, UUID player) {
		schedule.stop(player);
		DirectorData data = DirectorData.get(server);
		data.memory().find(player).ifPresent(pacing -> {
			brain(rules(server)).onLeave(pacing, clock(server, player), log(server, player));
			data.setDirty();
		});
	}

	/** Server stop: forgets every schedule (nothing per session survives). */
	void clear() {
		schedule.clear();
		nextWorldTick = 0;
		worldInterval = 0;
	}

	/**
	 * Lore fired {@code TELLING}, for any player: every telling notes the day for the group's "obeyed", but only one
	 * that names him moves to Telling (D-041; never back, never into Removal). Writing near his traces leaves the
	 * stage alone.
	 */
	void onTelling(MinecraftServer server, boolean namesHim) {
		DirectorData data = DirectorData.get(server);
		brain(rules(server)).onTelling(data.memory().world(), worldClock(server), new WorldEnv(server, null, null), namesHim, DirectorLog.WORLD);
		HerobrineState state = HerobrineState.get(server);
		DirectorTriggers.obeyBroken(state); // the group's obeying starts over, whoever told
		if (namesHim && !state.tellingStarted()) {
			state.setTellingStarted(true);
		}
		data.setDirty();
	}

	/** Clears this player's forced quiet ({@code /a1016 director quiet clear}). */
	void clearQuiet(MinecraftServer server, UUID player) {
		DirectorData data = DirectorData.get(server);
		data.memory().find(player).ifPresent(pacing -> {
			pacing.quietUntilDayTicks = -1;
			data.setDirty();
		});
	}

	/**
	 * Timewarp: a dry run of the skipped time for one player's pacing (nothing fires) for the summary, then every
	 * clock moves ({@code GameClock.warp}: the world's and every known player's) and the real memory gets what time
	 * alone does: the stage clock and stages, and for each player tension decay and quiet running out.
	 *
	 * @param focus whose pacing the summary plays; null for a fresh player's (nobody known)
	 */
	public DirectorSim.Result timewarpReport(MinecraftServer server, int days, @Nullable UUID focus) {
		HerobrineState state = HerobrineState.get(server);
		DirectorData data = DirectorData.get(server);
		DirectorMemory memory = data.memory();
		DirectorRules rules = rules(server);
		DirectorConfig config = DirectorConfig.get();
		DirectorBrain.Clock worldBefore = worldClock(server);
		DirectorBrain.Clock before = focus == null ? worldBefore : clock(server, focus);
		long span = Math.max(0, days) * DirectorBrain.DAY_TICKS;

		DirectorSim.Params params = DirectorSim.Params.from(config, rules);
		params.hours = span / (double) rules.hourTicks;
		params.seed = random.nextLong();
		params.continueSession = true;
		params.singleSession = true;
		params.attention = state.attention();
		// The summary shows what time alone does: nobody writes his name during a timewarp.
		params.tellingAtHour = -1;
		PacingMemory pacing = focus == null ? new PacingMemory(before.dayTicks())
				: memory.find(focus).orElseGet(() -> new PacingMemory(before.dayTicks()));
		if (focus != null) {
			params.player = focus;
		}
		DirectorSim.Result result = DirectorSim.run(rules, cards(), config.historySize, memory.world(), pacing, state.stage(),
				focus == null ? 0 : state.tension(focus), before, params);

		// Every known player's own clock before the warp moves them all.
		Map<UUID, Long> playerClocks = new LinkedHashMap<>();
		memory.players().keySet().forEach(uuid -> playerClocks.put(uuid, GameClock.playTicks(server, uuid)));
		GameClock.warp(server, days);
		DirectorBrain brain = brain(rules);
		WorldEnv worldEnv = new WorldEnv(server, null, null);
		Map<UUID, WorldEnv> envs = new LinkedHashMap<>();
		playerClocks.keySet().forEach(uuid -> envs.put(uuid, new WorldEnv(server, uuid, null)));
		for (long t = rules.tickInterval; t < span; t += rules.tickInterval) {
			advanceAll(brain, memory, worldBefore, t, playerClocks, worldEnv, envs);
		}
		advanceAll(brain, memory, worldBefore, span, playerClocks, worldEnv, envs);
		memory.world().lastWarpSummary = "+" + days + "d " + result.summary().getFirst();
		data.setDirty();
		for (String line : result.summary()) {
			A1016_02.LOGGER.info("[a1016] timewarp +{}d: {}", days, line);
		}
		return result;
	}

	/** Time alone, {@code step} ticks after the warp began: the world's stage clock, then each known player's own time. */
	private void advanceAll(DirectorBrain brain, DirectorMemory memory, DirectorBrain.Clock worldBefore, long step, Map<UUID, Long> playerClocks,
			WorldEnv worldEnv, Map<UUID, WorldEnv> envs) {
		brain.advanceWorld(memory.world(), worldBefore.plus(step), worldEnv, random, DirectorLog.WORLD);
		for (Map.Entry<UUID, Long> entry : playerClocks.entrySet()) {
			DirectorBrain.Clock own = new DirectorBrain.Clock(entry.getValue() + step, worldBefore.dayTicks() + step, worldBefore.worldTicks() + step);
			memory.find(entry.getKey()).ifPresent(m -> brain.advancePlayer(m, own, envs.get(entry.getKey()), random));
		}
	}

	/**
	 * A deterministic dry run from this player's current pacing ({@code /a1016 director sim}), with them naming him
	 * {@code tellingAtHour} hours into the run (null: {@code simTellingAtHour} from the config, negative: never).
	 * Changes nothing.
	 */
	public DirectorSim.Result simulate(MinecraftServer server, UUID player, int hours, boolean synthetic, @Nullable Double tellingAtHour) {
		HerobrineState state = HerobrineState.get(server);
		DirectorRules rules = rules(server);
		DirectorConfig config = DirectorConfig.get();
		DirectorSim.Params params = DirectorSim.Params.from(config, rules);
		params.hours = hours;
		params.player = player;
		if (tellingAtHour != null) {
			params.tellingAtHour = tellingAtHour;
		}
		DirectorBrain.Clock c = clock(server, player);
		params.seed = server.getWorldGenSettings().options().seed() ^ (c.playTicks() * 0x9E3779B97F4A7C15L) ^ (synthetic ? 0x5EED : 0);
		params.continueSession = Services.watch().participant(server, player).isPresent();
		params.attention = state.attention();
		List<CardInfo> cards = synthetic ? SyntheticDeck.cards() : cards();
		return DirectorSim.run(rules, cards, config.historySize, DirectorData.get(server).memory(), state.stage(), state.tension(player), c, params);
	}

	// --- helpers ---

	/** The live rules, rebuilt on every decision tick: config, profile and the {@link DirectorFlags}. */
	DirectorRules rules(MinecraftServer server) {
		return DirectorRules.live(HerobrineState.get(server));
	}

	DirectorBrain brain(DirectorRules rules) {
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

	/** This player's clock: their own play ticks, the in-game day ticks (timewarp included) and the world clock. */
	static DirectorBrain.Clock clock(MinecraftServer server, UUID player) {
		return new DirectorBrain.Clock(GameClock.playTicks(server, player), GameClock.dayTicks(server), GameClock.playTicks(server));
	}

	/** The world clock (the stage clock runs on it), as a clock of its own. */
	static DirectorBrain.Clock worldClock(MinecraftServer server) {
		long world = GameClock.playTicks(server);
		return new DirectorBrain.Clock(world, GameClock.dayTicks(server), world);
	}

	/** Whose pacing the console or a timewarp means (D-062.9): the first participant, else the first known player. */
	static @Nullable UUID focus(MinecraftServer server) {
		List<ServerPlayer> participants = Services.watch().participants(server);
		if (!participants.isEmpty()) {
			return participants.getFirst().getUUID();
		}
		return HerobrineState.get(server).players().stream().findFirst().map(HerobrineState.Subject::uuid).orElse(null);
	}

	/** A player's name as last seen, or the start of their UUID. */
	static String name(MinecraftServer server, UUID player) {
		return HerobrineState.get(server).player(player).map(HerobrineState.Subject::name).orElse(player.toString().substring(0, 8));
	}

	private static DirectorLog log(MinecraftServer server, UUID player) {
		return new DirectorLog(name(server, player));
	}

	/** Everyone in {@code sharing} but the target, as the brain's sharers (their memory, clock, env and log). */
	private List<DirectorBrain.Sharer> sharers(MinecraftServer server, DirectorMemory memory, UUID target, List<ServerPlayer> sharing) {
		List<DirectorBrain.Sharer> out = new ArrayList<>();
		long dayTicks = GameClock.dayTicks(server);
		for (ServerPlayer other : sharing) {
			UUID uuid = other.getUUID();
			if (!uuid.equals(target)) {
				out.add(new DirectorBrain.Sharer(memory.pacing(uuid, dayTicks), clock(server, uuid), new WorldEnv(server, uuid, other), log(server, uuid)));
			}
		}
		return out;
	}

	/**
	 * The live world behind the brain, for one player: their tension, their cards (in their own level), and who
	 * shares their moments. With no player (the world step, timewarp, a player not looked up) nothing can fire.
	 * Lives for one decision only.
	 */
	private final class WorldEnv implements DirectorBrain.Env {
		private final MinecraftServer server;
		private final @Nullable UUID uuid;
		private final @Nullable ServerPlayer player;

		WorldEnv(MinecraftServer server, @Nullable UUID uuid, @Nullable ServerPlayer player) {
			this.server = server;
			this.uuid = uuid;
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
			return uuid == null ? 0 : HerobrineState.get(server).tension(uuid);
		}

		@Override
		public void addTension(double delta, String reason) {
			if (uuid == null) {
				return;
			}
			if (delta > 1e-9) {
				Attention.raiseTension(server, uuid, delta, reason);
			} else if (delta < -1e-9) {
				Attention.lowerTension(server, uuid, -delta, reason);
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

		@Override
		public List<DirectorBrain.Sharer> sharers() {
			if (player == null || uuid == null) {
				return List.of();
			}
			return DirectorImpl.this.sharers(server, DirectorData.get(server).memory(), uuid, Services.watch().sharingWith(player));
		}
	}

	/** Logs what the live director decides, for one player (or the world). */
	static final class DirectorLog implements DirectorBrain.Recorder {
		static final DirectorLog WORLD = new DirectorLog("world");
		private static final Set<String> QUIET_OUTCOMES = Set.of("nothing due");
		private final String who;

		DirectorLog(String who) {
			this.who = who;
		}

		static void decision(String who, String outcome) {
			if (DirectorConfig.get().verboseLog && !QUIET_OUTCOMES.contains(outcome)) {
				A1016_02.LOGGER.info("[a1016] director ({}): {}", who, outcome);
			} else {
				A1016_02.LOGGER.debug("[a1016] director ({}): {}", who, outcome);
			}
		}

		@Override
		public void stageChanged(DirectorBrain.Clock clock, Stage from, Stage to) {
			A1016_02.LOGGER.info("[a1016] director: stage {} -> {} at world play {}", from, to, playTime(clock.worldTicks()));
		}

		@Override
		public void sessionStarted(DirectorBrain.Clock clock, Stage stage, boolean empty, long emptyUntil) {
			A1016_02.LOGGER.info("[a1016] director ({}): session starts in {}{}", who, stage, empty ? " (empty on purpose)" : "");
		}

		@Override
		public void sessionMood(DirectorBrain.Clock clock, boolean empty, long emptyUntil) {
			A1016_02.LOGGER.info("[a1016] director ({}): the session met an earlier one and takes its mood ({})", who, empty ? "empty" : "not empty");
		}

		@Override
		public void gaveUp(DirectorBrain.Clock clock, Tier tier, String cardId, String why) {
			A1016_02.LOGGER.info("[a1016] director ({}): {} card {} goes back into its deck ({})", who, tier, cardId, why);
		}

		@Override
		public void refilled(DirectorBrain.Clock clock, Tier tier) {
			A1016_02.LOGGER.debug("[a1016] director ({}): {} deck ran out and was reshuffled", who, tier);
		}

		@Override
		public void fired(DirectorBrain.Clock clock, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
			A1016_02.LOGGER.info("[a1016] director ({}): {} {}{}{} in {}", who, card.tier(), card.id(), fake ? " (fake)" : "", forced ? " (forced)" : "",
					stage);
		}

		@Override
		public void felt(DirectorBrain.Clock clock, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
			A1016_02.LOGGER.info("[a1016] director ({}): felt {} {} too", who, card.tier(), card.id());
		}

		@Override
		public void quietStarted(DirectorBrain.Clock clock, int days, long untilDayTicks, double tensionBefore) {
			A1016_02.LOGGER.info(String.format(Locale.ROOT, "[a1016] director (%s): tension %.1f, quiet for %d in-game days", who, tensionBefore, days));
		}

		static String playTime(long ticks) {
			return String.format(Locale.ROOT, "%dh%02dm", ticks / 72000, ticks / 1200 % 60);
		}
	}
}
