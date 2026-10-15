package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.util.RandomSource;

/**
 * The director's decisions, with no world in sight: clocks, the memories, an {@link Env} for the shared state and
 * the cards' context, and a random in; decisions out. The live director, timewarp and the dry-run simulator all
 * run this same class.
 *
 * <p>Multiplayer (D-058 to D-060): the {@link WorldMemory} is shared (the stage clock, once-per-world cards), and
 * each player has a {@link PacingMemory} of their own, decided on their own clock ({@link Clock#playTicks}). Stages
 * move only in {@link #advanceWorld}, on the world clock ({@link Clock#worldTicks}): there is one stage, and calling
 * it again in the same tick (for a second player) moves nothing. A card fires for the player whose pacing picked it.
 * Everyone who shares that moment ({@link Env#sharers()}, taken at fire time) counts it as felt: it goes into their
 * gaps, hard limits, major schedule, no-repeat cycle and tension, never as their own draw. So that nobody feels more
 * than their own rules allow, a card only fires when every sharer could have had it on their own right now (their
 * gates open, and they have not seen it this cycle); otherwise it waits, as for a moment that does not fit. And so
 * that a group is not busier than one player, the ambient and minor odds are split between everyone sharing: each
 * rolls its share of one solo rate.
 *
 * <p>Rules (DESIGN.md "The director", 4b; ARCHITECTURE "Director"):
 * <ul>
 * <li>Stages move by real play time: ALONE to TRACES at a point rolled inside {@code Pacing.tracesStart(tempo)},
 * TRACES to PROXIMITY inside {@code proximityStart(tempo)}. The stage clock runs faster or slower with attention,
 * within the configured band. TELLING comes only from a {@code HerobrineEvents.TELLING} that names him (D-041);
 * other telling (writing near his traces) never moves the stage. REMOVAL never from here.</li>
 * <li>One deck per tier and player, drawn without replacement and weighted by the profile habits and per-stage tag
 * weights (a weight below 1 also skips the card for that draw with chance 1 - weight). A deck runs out when every
 * card it has not fired this cycle is ruled out by the stage or the profile (beside friends, or was had by one of
 * them: never drawn while they are close); only then do its fired cards come back,
 * and the last one fired is never drawn next. Cards that only wait on a passing gate (no accident yet, a sighting
 * today, a skip roll) hold the reshuffle back, up to {@code deckStall} of in-game time. Signature cards never come
 * back, and fire at most once per world whoever gets them.</li>
 * <li>A drawn card is held until its context fits; {@code NO_SPOT} and {@code SKIPPED} keep it held. A card held
 * too long goes back into its deck.</li>
 * <li>Gates: the silence flag ({@link DirectorFlags}: nothing at all fires), join grace, forced quiet, empty
 * sessions, a small gap between any two fires, the minor and major gaps (never shortened by the pace multiplier flag),
 * no major (or signature) in the player's first {@code noMajorBeforeDay} in-game days, no ACCIDENT card before
 * {@code firstAccident} of their own play, at most {@code aloneMaxAmbient} ambients in Alone, sightings per day.
 * Every card waits for its own earliest stage and its tier's first stage ({@code minorMinStage},
 * {@code majorMinStage}, {@code signatureMinStage}).</li>
 * <li>Rates: ambient and minor tiers roll per director tick at their per-hour rate, raised by the pity bonus and
 * spread out by a refractory gap that keeps the mean;
 * majors are scheduled every {@code proximityMajorGap(tempo)}, each slot one gap after the previous slot's due time
 * (waiting for a slot never delays the next one), never sooner than the shortest gap after a fire (a slot whose
 * card a stage weight passes over is spent); signatures are due as soon as one is eligible.</li>
 * <li>Every fire adds tension by tier (fakes scaled). At the threshold a quiet of 1 to 4 in-game days starts and
 * tension drops; the threshold is checked after every fire and on every decision tick, so tension other systems
 * raise ({@code Attention.raiseTension}) starts a quiet too. Tension decays outside quiet.</li>
 * </ul>
 */
public final class DirectorBrain {
	public static final long DAY_TICKS = 24000L;
	/** Order in which tiers get the single fire a director tick allows. */
	static final Tier[] PRIORITY = {Tier.SIGNATURE, Tier.MAJOR, Tier.MINOR, Tier.AMBIENT};
	static final Set<String> PROFILE_SIGNATURES = Set.of(CardInfo.SIGNATURE_STILL_BURNING, CardInfo.SIGNATURE_HOUSE_ELSEWHERE,
			CardInfo.SIGNATURE_CROSS_ROW);

	/**
	 * Shared state and the world-facing half of the cards, for the one player being decided for (tension and the cards
	 * are theirs). The live director uses the world; dry runs use dice.
	 */
	public interface Env {
		Stage stage();

		void setStage(Stage stage);

		double attention();

		/** This player's tension (D-059). */
		double tension();

		void addTension(double delta, String reason);

		boolean contextFits(CardInfo card);

		FireResult fire(CardInfo card, boolean fake);

		/**
		 * Everyone else who would feel a card fired for this player right now (D-060.3), in a fixed order. Called once
		 * per decision that gets past the global gates, before its draws; the same list gates and records the fire.
		 * None by default (one player).
		 */
		default List<Sharer> sharers() {
			return List.of();
		}
	}

	/**
	 * Another participant sharing the moment: their pacing, their clock, their env (for their tension) and where
	 * their decisions are recorded.
	 */
	public record Sharer(PacingMemory memory, Clock clock, Env env, Recorder recorder) {
	}

	/** Observer of decisions (logs, the simulator, tests). Every method is optional. */
	public interface Recorder {
		Recorder NONE = new Recorder() {
		};

		default void stageChanged(Clock clock, Stage from, Stage to) {
		}

		default void sessionStarted(Clock clock, Stage stage, boolean empty, long emptyUntil) {
		}

		default void sessionEnded(Clock clock) {
		}

		default void drew(Clock clock, CardInfo card) {
		}

		default void gaveUp(Clock clock, Tier tier, String cardId, String why) {
		}

		default void refilled(Clock clock, Tier tier) {
		}

		default void fired(Clock clock, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
		}

		/** Someone else's card was felt by this player (D-060.3). */
		default void felt(Clock clock, CardInfo card, boolean fake, boolean forced, Stage stage, double tensionAfter) {
		}

		default void quietStarted(Clock clock, int days, long untilDayTicks, double tensionBefore) {
		}
	}

	/**
	 * @param playTicks  the player's own play ticks ({@code GameClock.playTicks(server, uuid)}): their pacing runs on it
	 * @param dayTicks   in-game time: {@code GameClock.day * 24000} plus the time of day (the world's, shared)
	 * @param worldTicks the world clock ({@code GameClock.playTicks(server)}): the stage clock runs on it
	 */
	public record Clock(long playTicks, long dayTicks, long worldTicks) {
		/** One player who has been there all along: their clock is the world's. */
		public Clock(long playTicks, long dayTicks) {
			this(playTicks, dayTicks, playTicks);
		}

		public long day() {
			return Math.floorDiv(dayTicks, DAY_TICKS);
		}

		/** Every clock moved by {@code ticks} (the player played them). */
		public Clock plus(long ticks) {
			return new Clock(playTicks + ticks, dayTicks + ticks, worldTicks + ticks);
		}
	}

	private final DirectorRules rules;
	private final Map<String, CardInfo> byId = new LinkedHashMap<>();
	private final Map<Tier, List<CardInfo>> byTier = new EnumMap<>(Tier.class);
	private final int historySize;

	public DirectorBrain(DirectorRules rules, Collection<CardInfo> cards, int historySize) {
		this.rules = rules;
		this.historySize = historySize;
		for (Tier tier : Tier.values()) {
			byTier.put(tier, new ArrayList<>());
		}
		for (CardInfo card : cards) {
			if (byId.putIfAbsent(card.id(), card) == null) {
				byTier.get(card.tier()).add(card);
			}
		}
	}

	public DirectorRules rules() {
		return rules;
	}

	public List<CardInfo> cards(Tier tier) {
		return byTier.get(tier);
	}

	public CardInfo card(String id) {
		return id == null ? null : byId.get(id);
	}

	// --- sessions (per player, D-062.4) ---

	/**
	 * This player started taking part: a new session for them only. From Traces on, the dice decide whether their
	 * whole session stays empty, except that a player who starts next to someone already in a session takes that
	 * session's mood (empty until the same moment, or not empty): otherwise each friend's own empty sessions would
	 * add up and a group would be silent far more often than one player. Nobody else's session changes.
	 */
	public void onJoin(WorldMemory w, PacingMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		advanceTime(w, m, c, env, random, rec);
		Stage stage = env.stage();
		m.sessionStart = c.playTicks();
		m.sessionCount++;
		m.sessionMinorRate = rules.minorsPerHourMin + random.nextDouble() * (rules.minorsPerHourMax - rules.minorsPerHourMin);
		boolean rolled = stage.atLeast(Stage.TRACES) && random.nextDouble() < rules.emptySessionChance;
		Sharer company = null;
		for (Sharer sharer : env.sharers()) {
			if (sharer.memory().sessionStart >= 0 && sharer.memory().sessionStart <= sharer.clock().playTicks()) {
				company = sharer;
				break;
			}
		}
		if (company == null) {
			m.sessionEmpty = rolled;
			m.sessionEmptyUntil = rolled ? c.playTicks() + rules.emptySessionMax : -1;
		} else {
			PacingMemory theirs = company.memory();
			long left = theirs.sessionEmptyUntil - company.clock().playTicks();
			m.sessionEmpty = theirs.sessionEmpty && left > 0;
			m.sessionEmptyUntil = m.sessionEmpty ? c.playTicks() + left : -1;
		}
		if (m.sessionEmpty) {
			m.emptySessionCount++;
		}
		m.lowRenderAccum = 0;
		m.lowRenderCounted = false;
		rec.sessionStarted(c, stage, m.sessionEmpty, m.sessionEmptyUntil);
	}

	/** This player stopped taking part: their session ends, nobody else's. */
	public void onLeave(PacingMemory m, Clock c, Recorder rec) {
		m.sessionStart = -1;
		m.sessionEmpty = false;
		m.sessionEmptyUntil = -1;
		rec.sessionEnded(c);
	}

	// --- time ---

	/** {@link #advanceWorld} then {@link #advancePlayer}: time passes for the world and for this player. Never fires. */
	public void advanceTime(WorldMemory w, PacingMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		advanceWorld(w, c, env, random, rec);
		advancePlayer(m, c, env, random);
	}

	/**
	 * The world's time passes: the stage clock (on the world clock, weighted by attention) and stage changes. Called
	 * again in the same tick (another player's decision), it moves nothing: the time step is zero and the stage it
	 * reads has already moved. Never fires a card.
	 */
	public void advanceWorld(WorldMemory w, Clock c, Env env, RandomSource random, Recorder rec) {
		String tempo = rules.profile.tempo().name();
		if (w.tracesAt < 0 || w.proximityAt < 0 || !tempo.equals(w.rolledTempo)) {
			w.tracesAt = rules.tracesStart.pick(random);
			w.proximityAt = Math.max(w.tracesAt + 1, rules.proximityStart.pick(random));
			w.rolledTempo = tempo;
		}
		if (w.stageClock < 0 || w.lastStepPlay < 0) {
			w.stageClock = c.worldTicks();
			w.lastStepPlay = c.worldTicks();
		}
		long dt = Math.max(0, c.worldTicks() - w.lastStepPlay);
		w.lastStepPlay = Math.max(w.lastStepPlay, c.worldTicks());
		if (dt > 0) {
			w.stageClock += dt / rules.attentionFactor(env.attention());
		}
		Stage stage = env.stage();
		if (stage == Stage.ALONE && w.stageClock >= w.tracesAt) {
			stage = changeStage(c, env, stage, Stage.TRACES, rec);
		}
		if (stage == Stage.TRACES && w.stageClock >= w.proximityAt) {
			changeStage(c, env, stage, Stage.PROXIMITY, rec);
		}
	}

	/** This player's time passes (their own clock): tension decay outside quiet, and their first major slot. */
	public void advancePlayer(PacingMemory m, Clock c, Env env, RandomSource random) {
		if (m.lastStepPlay < 0) {
			m.lastStepPlay = c.playTicks();
		}
		long dt = Math.max(0, c.playTicks() - m.lastStepPlay);
		m.lastStepPlay = Math.max(m.lastStepPlay, c.playTicks());
		if (dt > 0) {
			double tension = env.tension();
			if (!inQuiet(m, c) && tension > 0) {
				env.addTension(-Math.min(tension, dt * rules.tensionDecayPerTick), "decay");
			}
		}
		Stage stage = env.stage();
		if (stage.atLeast(rules.majorMinStage) && stage != Stage.REMOVAL && m.nextMajorDue < 0) {
			m.nextMajorDue = c.playTicks() + rules.majorEvery.pick(random);
		}
	}

	/**
	 * Lore fired {@code HerobrineEvents.TELLING}, from any player. Every telling counts as telling (the group's
	 * "obeyed after Stop." wait starts over, D-064), but only one that names him starts Telling (D-041): writing near
	 * his traces never moves the stage.
	 *
	 * @return true if the stage moved to Telling
	 */
	public boolean onTelling(WorldMemory w, Clock c, Env env, boolean namesHim, Recorder rec) {
		w.lastTellingDay = c.day();
		w.obeyCounted = false;
		return namesHim && enterTelling(c, env, rec);
	}

	/** Telling begins (his name was written). Never moves out of Telling or Removal. */
	public boolean enterTelling(Clock c, Env env, Recorder rec) {
		Stage stage = env.stage();
		if (stage.atLeast(Stage.TELLING)) {
			return false;
		}
		changeStage(c, env, stage, Stage.TELLING, rec);
		return true;
	}

	private static Stage changeStage(Clock c, Env env, Stage from, Stage to, Recorder rec) {
		env.setStage(to);
		rec.stageChanged(c, from, to);
		return to;
	}

	// --- the decision ---

	/**
	 * One director tick for one player: let time pass, then draw, hold or fire at most one card for them, obeying every
	 * gate (theirs, and those of everyone who would share the moment).
	 *
	 * @return a one-line description of what happened, for {@code /a1016 director tick} and the log
	 */
	public String step(WorldMemory w, PacingMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		advanceTime(w, m, c, env, random, rec);
		if (m.sessionStart < 0 || m.sessionStart > c.playTicks()) {
			onJoin(w, m, c, env, random, rec);
		}
		// Other systems may have raised tension since the last tick, not only our own fires.
		checkQuiet(m, c, env, random, rec);
		Stage stage = env.stage();
		releaseStaleHolds(w, m, c, stage, rec);
		String blocked = globalBlock(m, c);
		if (blocked != null) {
			return "waiting: " + blocked;
		}
		List<String> notes = new ArrayList<>();
		List<Sharer> sharers = env.sharers();
		for (Tier tier : PRIORITY) {
			CardInfo card = card(m.held.get(tier));
			if (card == null) {
				if (tierBlock(tier, m, c, stage) != null || !due(tier, m, c, stage, random, sharers.size())) {
					continue;
				}
				Set<String> passedOver = new HashSet<>();
				card = draw(tier, w, m, c, stage, sharers, random, rec, passedOver);
				if (card == null) {
					if (tier == Tier.MAJOR && !passedOver.isEmpty()) {
						// A stage weight passed the slot's card over: the slot is spent, as a probabilistic tier's
						// tick is. Retrying every tick would re-roll an "almost never" card until it fires.
						scheduleNextMajor(m, c.playTicks(), random);
					}
					continue;
				}
				m.held.put(tier, card.id());
				m.heldSince.put(tier, c.playTicks());
				rec.drew(c, card);
			}
			String why = fireBlock(card, w, m, c, stage);
			if (why != null) {
				notes.add(card.id() + " waits (" + why + ")");
				continue;
			}
			String shared = sharedBlock(card, w, sharers, stage);
			if (shared != null) {
				notes.add(card.id() + " waits (" + shared + ")");
				continue;
			}
			if (!env.contextFits(card)) {
				notes.add(card.id() + " held: the moment does not fit");
				continue;
			}
			boolean fake = card.hasFake() && random.nextDouble() < rules.fakeChance;
			FireResult result = env.fire(card, fake);
			if (result == FireResult.FIRED) {
				recordFire(w, m, c, card, fake, false, stage, env, sharers, random, rec);
				return "fired " + card.id() + (fake ? " (fake)" : "") + (sharers.isEmpty() ? "" : ", felt by " + sharers.size() + " more");
			}
			notes.add(card.id() + " " + result + ", kept for later");
		}
		return notes.isEmpty() ? "nothing due" : String.join("; ", notes);
	}

	/**
	 * A forced debug fire happened for this player: their history, deck and the world's tag times, never their pacing,
	 * tension or quiet. Everyone who shared the moment counts it as felt like any fire (D-060.3).
	 */
	public void recordForced(WorldMemory w, PacingMemory m, Clock c, CardInfo card, boolean fake, Stage stage, List<Sharer> sharers,
			RandomSource random, Recorder rec) {
		recordFire(w, m, c, card, fake, true, stage, null, sharers, random, rec);
	}

	// --- gates ---

	public boolean inQuiet(PacingMemory m, Clock c) {
		return c.dayTicks() < m.quietUntilDayTicks;
	}

	/** Reason nothing at all may fire for this player now, or null. */
	public String globalBlock(PacingMemory m, Clock c) {
		long now = c.playTicks();
		if (c.day() < rules.silenceUntilDay) {
			return rules.silenceUntilDay == DirectorFlags.FOREVER ? "silenced for good" : "silenced until day " + rules.silenceUntilDay;
		}
		if (m.sessionStart >= 0 && now - m.sessionStart < rules.joinGrace) {
			return "join grace";
		}
		if (inQuiet(m, c)) {
			return "quiet until day " + Math.floorDiv(m.quietUntilDayTicks, DAY_TICKS);
		}
		if (m.sessionEmpty && now < m.sessionEmptyUntil) {
			return "empty session";
		}
		if (now - m.lastFireAny < rules.minAnyGap) {
			return "just fired";
		}
		return null;
	}

	/** Reason this tier may not fire for this player now, or null. */
	public String tierBlock(Tier tier, PacingMemory m, Clock c, Stage stage) {
		long now = c.playTicks();
		return switch (tier) {
			case AMBIENT -> stage == Stage.ALONE && m.aloneAmbientCount >= rules.aloneMaxAmbient ? "Alone ambient cap" : null;
			case MINOR -> !stage.atLeast(rules.minorMinStage) ? "stage"
					: now - m.lastFireTier.getOrDefault(Tier.MINOR, DirectorMemory.NEVER) < rules.minorGap ? "minor gap" : null;
			case MAJOR -> !stage.atLeast(rules.majorMinStage) ? "stage"
					: firstDay(m, c) ? "first day"
					: now - m.lastMajorOrSignature < rules.majorGap ? "major gap" : null;
			case SIGNATURE -> !stage.atLeast(rules.signatureMinStage) ? "stage"
					: firstDay(m, c) ? "first day"
					: now - m.lastMajorOrSignature < rules.majorGap ? "major gap" : null;
		};
	}

	/**
	 * True inside the player's own first {@code noMajorBeforeDay} in-game days, counted from when they first took part
	 * (D-011; for the world's first player that is day 0 on).
	 */
	public boolean firstDay(PacingMemory m, Clock c) {
		return c.dayTicks() < firstDayEnd(m);
	}

	/** Day ticks at which the player's first-day rule ends. */
	public long firstDayEnd(PacingMemory m) {
		return m.firstDayTicks + (long) rules.noMajorBeforeDay * DAY_TICKS;
	}

	/** First stage a tier draws in. */
	public Stage tierMinStage(Tier tier) {
		return switch (tier) {
			case AMBIENT -> Stage.ALONE;
			case MINOR -> rules.minorMinStage;
			case MAJOR -> rules.majorMinStage;
			case SIGNATURE -> rules.signatureMinStage;
		};
	}

	/** First stage this card may fire in: its own earliest stage, never before its tier's. */
	public Stage minStage(CardInfo card) {
		Stage tier = tierMinStage(card.tier());
		return tier.atLeast(card.earliestStage()) ? tier : card.earliestStage();
	}

	/** Reason this card may not fire for this player now (tier and card gates), or null. */
	public String fireBlock(CardInfo card, WorldMemory w, PacingMemory m, Clock c, Stage stage) {
		String tier = tierBlock(card.tier(), m, c, stage);
		return tier != null ? tier : cardBlock(card, w, m, c, stage);
	}

	/**
	 * Why a card picked for someone else may not reach this player now (D-060.3), or null: the moment must be one they
	 * could have had on their own (every gate of theirs open), and new to them (not in their cycle, not their last).
	 */
	public String sharedBlock(CardInfo card, WorldMemory w, PacingMemory m, Clock c, Stage stage) {
		String why = globalBlock(m, c);
		if (why == null) {
			why = fireBlock(card, w, m, c, stage);
		}
		if (why == null && had(card, m)) {
			why = "already had it";
		}
		return why;
	}

	/** True if this player already had the card: it is in their no-repeat cycle, or was their last of its tier. */
	private static boolean had(CardInfo card, PacingMemory m) {
		return m.cycleFired.get(card.tier()).contains(card.id()) || card.id().equals(m.lastFired.get(card.tier()));
	}

	private static boolean anyHad(CardInfo card, List<Sharer> sharers) {
		for (Sharer sharer : sharers) {
			if (had(card, sharer.memory())) {
				return true;
			}
		}
		return false;
	}

	/** The first sharer's reason ({@link #sharedBlock(CardInfo, WorldMemory, PacingMemory, Clock, Stage)}), or null. */
	public String sharedBlock(CardInfo card, WorldMemory w, List<Sharer> sharers, Stage stage) {
		for (Sharer sharer : sharers) {
			String why = sharedBlock(card, w, sharer.memory(), sharer.clock(), stage);
			if (why != null) {
				return "someone close by: " + why;
			}
		}
		return null;
	}

	private String cardBlock(CardInfo card, WorldMemory w, PacingMemory m, Clock c, Stage stage) {
		String fixed = staticBlock(card, w, stage);
		if (fixed != null) {
			return fixed;
		}
		if (card.has(CardTag.ACCIDENT) && c.playTicks() < rules.firstAccident) {
			return "no accident yet";
		}
		if (card.has(CardTag.SIGHTING) && m.sightingDay == c.day() && m.sightingsThatDay >= rules.sightingsPerDayMax) {
			return "sighting already today";
		}
		if (CardInfo.SIGNATURE_HOUSE_ELSEWHERE.equals(card.id()) && c.day() < rules.houseCopyMinDay) {
			return "house copy waits for day " + rules.houseCopyMinDay;
		}
		return null;
	}

	/** Blocks that only change with the stage, the profile or a once-per-world fire (never with the moment or the player). */
	private String staticBlock(CardInfo card, WorldMemory w, Stage stage) {
		if (!stage.atLeast(minStage(card))) {
			return "stage";
		}
		if (rules.tagWeight(stage, card) <= 0) {
			return "not in " + stage;
		}
		if (rules.habitWeight(card) <= 0) {
			return "not this world's habits";
		}
		if (oncePerWorld(card) && w.signaturesFired.contains(card.id())) {
			return "once per world";
		}
		return switch (card.id()) {
			case CardInfo.SIGNATURE_STILL_BURNING -> rules.profile.hasStillBurning() ? null : "not this world's signature";
			case CardInfo.SIGNATURE_HOUSE_ELSEWHERE -> rules.profile.hasHouseCopy() ? null : "not this world's signature";
			case CardInfo.SIGNATURE_CROSS_ROW -> rules.profile.signature() == Signature.CROSS_ROW ? null : "not this world's signature";
			default -> null;
		};
	}

	static boolean oncePerWorld(CardInfo card) {
		return card.tier() == Tier.SIGNATURE || PROFILE_SIGNATURES.contains(card.id());
	}

	/** The pity bonus: after a long stretch without any fire, the odds rise slowly up to the cap. */
	public double pity(PacingMemory m, Clock c) {
		long since = c.playTicks() - Math.max(0, m.lastFireAny);
		double hoursPast = (since - rules.pityStart) / (double) rules.hourTicks;
		return hoursPast <= 0 ? 0 : Math.min(rules.pityMaxBonus, hoursPast * rules.pityBonusPerHour);
	}

	/** Per-hour rate of a probabilistic tier in this stage (before pity), or -1 for scheduled tiers. */
	public double rate(Tier tier, PacingMemory m, Stage stage) {
		return switch (tier) {
			case AMBIENT -> switch (stage) {
				case ALONE -> rules.aloneAmbientPerHour;
				case TRACES -> rules.tracesAmbientPerHour;
				case PROXIMITY -> rules.proximityAmbientPerHour;
				default -> rules.tellingAmbientPerHour;
			};
			// The pace multiplier flag runs the typical minor schedule faster or slower; the minor gap still holds.
			case MINOR -> rules.paceMultiplier * (stage.atLeast(Stage.PROXIMITY)
					? (m.sessionMinorRate >= 0 ? m.sessionMinorRate : (rules.minorsPerHourMin + rules.minorsPerHourMax) / 2)
					: rules.tracesMinorsPerHour);
			default -> -1;
		};
	}

	/**
	 * @param sharing how many others share this player's moments right now: together, the group rolls one solo rate
	 *                between them (each of the {@code sharing + 1} a share of it, after the same refractory wait), so being
	 *                together never multiplies the ambient and minor odds (D-060.3)
	 */
	private boolean due(Tier tier, PacingMemory m, Clock c, Stage stage, RandomSource random, int sharing) {
		return switch (tier) {
			case AMBIENT, MINOR -> {
				double perHour = rate(tier, m, stage);
				if (perHour <= 0) {
					yield false;
				}
				long last = m.lastFireTier.getOrDefault(tier, DirectorMemory.NEVER);
				if (last != DirectorMemory.NEVER && rules.rateRefractory > 0) {
					// Spread fires out: wait part of the mean interval, then raise the odds so the mean stays 1 / rate.
					if (c.playTicks() - last < rules.rateRefractory * rules.hourTicks / perHour) {
						yield false;
					}
					perHour /= 1 - rules.rateRefractory;
				}
				perHour /= 1 + Math.max(0, sharing);
				double perTick = perHour * (1 + pity(m, c)) * rules.tickInterval / rules.hourTicks;
				yield random.nextDouble() < 1 - Math.exp(-perTick);
			}
			case MAJOR -> m.nextMajorDue >= 0 && c.playTicks() >= m.nextMajorDue;
			case SIGNATURE -> true;
		};
	}

	// --- decks ---

	/** Cards of this tier this player could draw now: undrawn this cycle, not the last one fired, every gate open. */
	public List<CardInfo> candidates(Tier tier, WorldMemory w, PacingMemory m, Clock c, Stage stage) {
		Set<String> cycle = m.cycleFired.get(tier);
		String last = m.lastFired.get(tier);
		List<CardInfo> out = new ArrayList<>();
		for (CardInfo card : byTier.get(tier)) {
			if (!cycle.contains(card.id()) && !card.id().equals(last) && cardBlock(card, w, m, c, stage) == null) {
				out.add(card);
			}
		}
		return out;
	}

	/**
	 * Draws one card. A card whose stage weight is below 1 is skipped for this draw with chance 1 - weight
	 * ("almost zero" means almost never). When nothing undrawn can be drawn now, the fired cards come back (except
	 * the last one fired) only if the deck has {@link #runOut run out}, or if it has drawn nothing for
	 * {@code deckStall} of in-game time since it got stuck. Otherwise it waits for its gated cards.
	 *
	 * <p>With friends close by ({@code sharers}, D-060.3) it never draws a card one of them already had (in their
	 * cycle, or their last of the tier): the moment could not reach them, so it would only sit held, blocking the tier
	 * while they stay together. Those cards are spent for this moment, so they count as fired for the run-out rule:
	 * when every card left undrawn is ruled out or one a friend had (each had different cards apart, then every card
	 * new to both while together), the deck refills now instead of deadlocking while they stay together, as long as
	 * the refill gives back a card none of them had (otherwise the cycle is kept); when some are only gated, it waits
	 * for them as alone. A deck whose own undrawn cards are all gone refills by the solo rule, so a pair's decks turn
	 * over together. Alone, the draw is exactly the solo one.
	 *
	 * @param skipped filled with the cards a stage weight passed over in this draw
	 */
	private CardInfo draw(Tier tier, WorldMemory w, PacingMemory m, Clock c, Stage stage, List<Sharer> sharers, RandomSource random, Recorder rec,
			Set<String> skipped) {
		boolean refilled = false;
		String givenUp = m.lastGivenUp.get(tier);
		while (true) {
			List<CardInfo> eligible = candidates(tier, w, m, c, stage);
			eligible.removeIf(card -> skipped.contains(card.id()));
			boolean onlyTheirs = false;
			if (!eligible.isEmpty() && !sharers.isEmpty()) {
				eligible.removeIf(card -> anyHad(card, sharers));
				onlyTheirs = eligible.isEmpty();
			}
			if (eligible.isEmpty()) {
				if (refilled || tier == Tier.SIGNATURE || m.cycleFired.get(tier).isEmpty()) {
					return null;
				}
				if (onlyTheirs && !refillHelps(tier, w, m, c, stage, sharers)) {
					return null; // only cards a friend had are left, and nothing that would come back is new to everyone here
				}
				if (!runOut(tier, w, m, stage, sharers)) {
					Long stuckSince = m.deckStuckSince.get(tier);
					if (stuckSince == null || stuckSince > c.dayTicks()) {
						stuckSince = c.dayTicks();
						m.deckStuckSince.put(tier, stuckSince);
					}
					if (c.dayTicks() - stuckSince < rules.deckStall) {
						return null;
					}
				}
				m.cycleFired.get(tier).clear();
				m.deckStuckSince.remove(tier);
				rec.refilled(c, tier);
				refilled = true;
				continue;
			}
			if (givenUp != null && eligible.size() > 1) {
				eligible.removeIf(card -> card.id().equals(givenUp));
			}
			CardInfo picked = weightedPick(eligible, stage, random);
			if (picked == null) {
				return null;
			}
			double tagWeight = rules.tagWeight(stage, picked);
			if (tagWeight < 1 && random.nextDouble() >= tagWeight) {
				skipped.add(picked.id());
				continue;
			}
			m.deckStuckSince.remove(tier);
			return picked;
		}
	}

	/**
	 * A deck has run out when every card it has not fired this cycle is ruled out by the stage or the profile (or
	 * already fired once per world). A card that only waits on the moment (a gate, a skip roll) keeps it going.
	 */
	public boolean runOut(Tier tier, WorldMemory w, PacingMemory m, Stage stage) {
		return runOut(tier, w, m, stage, List.of());
	}

	/**
	 * Beside friends: true if refilling this player's deck now would give them a card to draw, one of this cycle's
	 * (not their last) whose gates are open and that none of the friends had. Otherwise the refill would only throw
	 * their cycle away.
	 */
	private boolean refillHelps(Tier tier, WorldMemory w, PacingMemory m, Clock c, Stage stage, List<Sharer> sharers) {
		String last = m.lastFired.get(tier);
		for (String id : m.cycleFired.get(tier)) {
			CardInfo card = card(id);
			if (card != null && !id.equals(last) && cardBlock(card, w, m, c, stage) == null && !anyHad(card, sharers)) {
				return true;
			}
		}
		return false;
	}

	/** {@link #runOut(Tier, WorldMemory, PacingMemory, Stage)} beside friends: a card one of them had is spent for the moment too. */
	private boolean runOut(Tier tier, WorldMemory w, PacingMemory m, Stage stage, List<Sharer> sharers) {
		Set<String> cycle = m.cycleFired.get(tier);
		for (CardInfo card : byTier.get(tier)) {
			if (!cycle.contains(card.id()) && staticBlock(card, w, stage) == null && !anyHad(card, sharers)) {
				return false;
			}
		}
		return true;
	}

	private CardInfo weightedPick(List<CardInfo> eligible, Stage stage, RandomSource random) {
		double total = 0;
		double[] weights = new double[eligible.size()];
		for (int i = 0; i < weights.length; i++) {
			CardInfo card = eligible.get(i);
			weights[i] = Math.max(0, rules.habitWeight(card) * rules.tagWeight(stage, card));
			total += weights[i];
		}
		if (total <= 0) {
			return null;
		}
		double roll = random.nextDouble() * total;
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				return eligible.get(i);
			}
		}
		return eligible.getLast();
	}

	private void releaseStaleHolds(WorldMemory w, PacingMemory m, Clock c, Stage stage, Recorder rec) {
		for (Tier tier : Tier.values()) {
			String id = m.held.get(tier);
			if (id == null) {
				continue;
			}
			CardInfo card = card(id);
			String why;
			if (card == null) {
				why = "no longer registered";
			} else if (c.playTicks() - m.heldSince.getOrDefault(tier, c.playTicks()) >= rules.holdGiveUp) {
				why = "held too long";
			} else {
				why = staticBlock(card, w, stage);
			}
			if (why != null) {
				m.held.remove(tier);
				m.heldSince.remove(tier);
				if (card != null) {
					m.lastGivenUp.put(tier, id);
				}
				rec.gaveUp(c, tier, id, why);
			}
		}
	}

	// --- fires, felt moments, tension, quiet ---

	private void recordFire(WorldMemory w, PacingMemory m, Clock c, CardInfo card, boolean fake, boolean forced, Stage stage, Env env,
			List<Sharer> sharers, RandomSource random, Recorder rec) {
		Tier tier = card.tier();
		long now = c.playTicks();
		if (card.id().equals(m.held.get(tier))) {
			m.held.remove(tier);
			m.heldSince.remove(tier);
		}
		m.cycleFired.get(tier).add(card.id());
		m.lastFired.put(tier, card.id());
		m.lastGivenUp.remove(tier);
		m.deckStuckSince.remove(tier);
		if (oncePerWorld(card)) {
			// D-062.3: set in the same call that fired it, so nobody else's decision can fire it again this tick.
			w.signaturesFired.add(card.id());
		}
		for (CardTag tag : card.tags()) {
			w.lastTag.put(tag, c.worldTicks());
			m.lastTag.put(tag, now);
		}
		countSighting(m, c, card);
		m.addHistory(new DirectorMemory.HistoryEntry(now, c.day(), card.id(), tier, fake, forced, stage, false), historySize);
		if (forced) {
			rec.fired(c, card, fake, true, stage, Double.NaN);
		} else {
			m.lastFireAny = now;
			m.lastFireTier.put(tier, now);
			if (tier == Tier.MAJOR || tier == Tier.SIGNATURE) {
				m.lastMajorOrSignature = now;
			}
			if (tier == Tier.MAJOR) {
				scheduleNextMajor(m, now, random);
			}
			if (tier == Tier.AMBIENT && stage == Stage.ALONE) {
				m.aloneAmbientCount++;
			}
			m.totalFires++;
			if (card.hasFake()) {
				m.fakeableFires++;
			}
			if (fake) {
				m.fakeFires++;
			}
			env.addTension(rules.tensionFor(tier) * (fake ? rules.fakeTensionFactor : 1), "fired " + card.id());
			rec.fired(c, card, fake, false, stage, env.tension());
			checkQuiet(m, c, env, random, rec);
		}
		for (Sharer sharer : sharers) {
			recordFelt(sharer, card, fake, forced, stage, random);
		}
	}

	/**
	 * Someone else's card reached this player (D-060.3): it counts in their pacing as if it had been theirs (the gaps
	 * and hard limits of its tier, the major schedule, the Alone cap, sightings per day, the no-repeat cycle, tension
	 * and the quiet it may start), but not as their own draw: their held cards (other than this one) and their fire
	 * counts stay as they were. A forced (debug) fire skipped every gate, so it counts for them as it does for its
	 * target: the history, the no-repeat cycle and the tag times only, never their gaps, major slot, Alone cap,
	 * tension or quiet.
	 */
	private void recordFelt(Sharer sharer, CardInfo card, boolean fake, boolean forced, Stage stage, RandomSource random) {
		PacingMemory m = sharer.memory();
		Clock c = sharer.clock();
		Env env = sharer.env();
		Recorder rec = sharer.recorder();
		Tier tier = card.tier();
		long now = c.playTicks();
		if (card.id().equals(m.held.get(tier))) {
			m.held.remove(tier);
			m.heldSince.remove(tier);
		}
		m.cycleFired.get(tier).add(card.id());
		m.lastFired.put(tier, card.id());
		for (CardTag tag : card.tags()) {
			m.lastTag.put(tag, now);
		}
		countSighting(m, c, card);
		m.addHistory(new DirectorMemory.HistoryEntry(now, c.day(), card.id(), tier, fake, forced, stage, true), historySize);
		if (forced) {
			rec.felt(c, card, fake, true, stage, Double.NaN);
			return;
		}
		m.feltFires++;
		m.lastFireAny = now;
		m.lastFireTier.put(tier, now);
		if (tier == Tier.MAJOR || tier == Tier.SIGNATURE) {
			m.lastMajorOrSignature = now;
		}
		if (tier == Tier.MAJOR) {
			scheduleNextMajor(m, now, random);
		}
		if (tier == Tier.AMBIENT && stage == Stage.ALONE) {
			m.aloneAmbientCount++;
		}
		env.addTension(rules.tensionFor(tier) * (fake ? rules.fakeTensionFactor : 1), "felt " + card.id());
		rec.felt(c, card, fake, forced, stage, env.tension());
		checkQuiet(m, c, env, random, rec);
	}

	private static void countSighting(PacingMemory m, Clock c, CardInfo card) {
		if (card.has(CardTag.SIGHTING)) {
			if (m.sightingDay != c.day()) {
				m.sightingDay = c.day();
				m.sightingsThatDay = 0;
			}
			m.sightingsThatDay++;
		}
	}

	/**
	 * The slot due now was used (fired or spent): the next one is a {@code majorEvery} gap after this slot's due time,
	 * not after the moment it was used. A slot that waited out a quiet, an empty session or its card's moment
	 * would otherwise push every later slot back as well, and that silence would count twice (once as silence,
	 * once as a longer gap): majors drifted about half an hour per slot behind the 4b schedule. The next slot is
	 * never closer than the shortest gap after now, so a late major is never followed by a hurried one.
	 */
	private void scheduleNextMajor(PacingMemory m, long now, RandomSource random) {
		long slot = m.nextMajorDue >= 0 && m.nextMajorDue <= now ? m.nextMajorDue : now;
		m.nextMajorDue = Math.max(slot + rules.majorEvery.pick(random), now + rules.majorEvery.min());
	}

	/** Tension at the threshold starts a quiet, whatever raised it. A running quiet is never restarted. */
	private void checkQuiet(PacingMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		if (!inQuiet(m, c) && env.tension() >= rules.tensionThreshold) {
			startQuiet(m, c, env, random, rec);
		}
	}

	private void startQuiet(PacingMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		int days = rules.quietMinDays + random.nextInt(rules.quietMaxDays - rules.quietMinDays + 1);
		double before = env.tension();
		m.quietStartDayTicks = c.dayTicks();
		m.quietUntilDayTicks = c.dayTicks() + days * DAY_TICKS;
		m.quietCount++;
		if (before > rules.tensionAfterQuiet) {
			env.addTension(rules.tensionAfterQuiet - before, "quiet");
		}
		rec.quietStarted(c, days, m.quietUntilDayTicks, before);
	}
}
