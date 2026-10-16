package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.Signature;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.util.RandomSource;

import org.jspecify.annotations.Nullable;

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
 * gates open, and they have not seen it this cycle); otherwise it waits, as for a moment that does not fit. A group
 * sharing a moment rolls {@code groupRateFactor} (1.5) solo ambient and minor rates between them, each its share
 * (D-074): together is shared atmosphere, never a multiple of it. A {@code PERSONAL} card (one player alone) is never
 * drawn beside a sharer, and one held from before goes back the moment a sharer is close. An {@code UNSHARED} card (a
 * moment only its target has) is felt by nobody else: it fires beside sharers, waits for none of their gates and touches
 * none of their pacing ({@link #feeling}).
 *
 * <p>Fog weather (D-071): while a fog spell that is not quiet is on for the player ({@link Env#fogSpell}), the
 * ambient and minor odds are {@code fogWeatherOddsFactor} times higher, SIGHTING cards weigh
 * {@code fogWeatherSightingWeight} times more, and a major slot due within {@code fogWeatherMajorPull} may come now.
 * Every gate holds as ever.
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
 * too long ({@code holdGiveUp}) goes back into its deck, and an ambient or minor card whose context has not fit for
 * {@code heldUnfitGiveBack} (5 minutes) of looks (one each director tick; looks a gate held back do not count) goes
 * back sooner, so a card for a moment far off never freezes its tier; the next draw passes a given-back card over if it
 * can. A card that goes back either way having been looked at all along without fitting is parked for
 * {@code unfitPark} (30 minutes) of the player's play (R1, v1.2): never drawn, and ruled out for the run-out rule, so a
 * deck whose only undrawn cards wait on a moment that never comes (the tunnel ladder for someone who never walks a long
 * dug tunnel) refills with the cards that can fire instead of drawing the same one again and again. After its park it
 * is drawn as any other card and fires once its moment comes; until it fires, a hold of it goes back (parked again) at
 * its first look that does not fit. Every director tick each parked card's moment is looked at in passing: one that
 * fits ends its park and that strike there, so a card whose moment is a short stretch of every day (the ridge's dusk)
 * keeps its whole holds. An ambient or minor card parked as it goes back hands the roll that drew it to the next card
 * in the same tick, however many such cards the draw meets in a row, so a card whose moment never comes costs its tier
 * one five-minute hold, never a fire.</li>
 * <li>Gates: the silence flag ({@link DirectorFlags}: nothing at all fires), join grace, forced quiet, empty
 * sessions (rolled from {@code emptySessionMinStage} on, Proximity), a small gap between any two fires, at most
 * {@code firstHourMaxFires} (5) moments in the player's own first hour (D-070), the minor and major gaps (never
 * shortened by the pace multiplier flag), no major (or signature) in the player's first {@code noMajorBeforeDay}
 * in-game days, no ACCIDENT card before {@code firstAccident} of their own play, at most {@code aloneMaxAmbient}
 * ambients in Alone, sightings per day.
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
 * <li>Blind moments (D-077): a {@code BLIND} card's moment is a menu open on the player's screen, which comes often
 * but never lasts, so a held one waits {@code blindHold} (15 minutes) of looks instead of the five-minute unfit
 * give-back before it goes back, parked like any card whose moment did not come (a player who never opens a menu, or
 * a client without the blind signal, never freezes a tier). While a menu is open, {@link #recheck} looks again at
 * what the player's pacing already holds or has parked (BLIND cards first) and fires one whose moment fits now,
 * through every gate of {@link #step}, a parked one released first; it never rolls a tier or draws.</li>
 * <li>Places remember (docs/SELF_CREATED.md, 01; D-084): from Traces on, after the player's own first hour, the
 * ambient and minor rates are multiplied by {@link Env#placeFactor} (up to 1.8 near where things happened to them, 1.6 in
 * the one chunk they trust), beside the fog factor and before the refractory wait; never a gap, a cap, a major slot or
 * a hard limit. Every fire that is neither fake nor forced is handed to {@link Env#remember} for the target and for
 * every sharer who felt it, each at their own spot.</li>
 * </ul>
 */
public final class DirectorBrain {
	public static final long DAY_TICKS = 24000L;
	/** Order in which tiers get the single fire a director tick allows. */
	static final Tier[] PRIORITY = {Tier.SIGNATURE, Tier.MAJOR, Tier.MINOR, Tier.AMBIENT};
	static final Set<String> PROFILE_SIGNATURES = Set.of(CardInfo.SIGNATURE_STILL_BURNING, CardInfo.SIGNATURE_HOUSE_ELSEWHERE,
			CardInfo.SIGNATURE_CROSS_ROW);
	/** Why a card held for {@code holdGiveUp} goes back. */
	static final String HELD_TOO_LONG = "held too long";
	/** Why an ambient or minor card whose moment does not fit goes back after {@code heldUnfitGiveBack}. */
	static final String UNFIT = "its moment did not come";
	/** Added to why a card went back when it is parked too ({@link #park}). */
	static final String PARKED = " (parked)";

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
		 * per decision, before its gates (sessions that meet share one mood first); the same list gates and records
		 * the fire. None by default (one player).
		 */
		default List<Sharer> sharers() {
			return List.of();
		}

		/**
		 * The fog spell on for this player right now (D-071), or empty: the world's spell while it counts as fog at the
		 * clock ({@code FogSpell.counts}: its amount at least half its level, so not while it eases in or out below
		 * that) and the player's dimension shows weather (none in fixed-time dimensions, as {@code FogLimits.of} reads
		 * it). The live director reads the stored spell on the level's clock; dry runs roll one per in-game day with
		 * {@code FogWeatherRules}. A quiet spell gives no boost. None by default (no weather).
		 */
		default Optional<FogSpell> fogSpell() {
			return Optional.empty();
		}

		/**
		 * Places remember (docs/SELF_CREATED.md, 01; D-084): the rate factor at this player's position now, from the
		 * places where things happened to them and the one chunk they trust ({@code director.sc.Places}). The brain
		 * multiplies the ambient and minor rates by it, beside the fog factor, only from Traces on and after the player's
		 * own first hour ({@link #placeFactor(Env, Stage, Clock)}); never a gap, cap, major slot or hard limit. Live, 1
		 * while the layer is off; dry runs roll it from a dread share. 1 by default (nothing changes).
		 */
		default double placeFactor() {
			return 1.0;
		}

		/**
		 * A fire that is neither fake nor forced reached this player: theirs ({@code felt} false) or one they felt beside
		 * someone (true), with their own pacing and clock. Live, it remembers where the moment is
		 * ({@code director.sc.Places.placeOf}: where they stand, or the home for a card at home), or, for a card that
		 * arms now and plays later at a spot of its own ({@code Places.AT_TRACE}), where its trace is ledgered later.
		 * Nothing by default.
		 */
		default void remember(PacingMemory m, CardInfo card, Clock c, boolean felt) {
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

		/** Meeting someone whose session started earlier, this player's session took that session's mood (D-060.3). */
		default void sessionMood(Clock clock, boolean empty, long emptyUntil) {
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

		/** A world saved under older rules rolled its stage points again (D-075), at these world play ticks. */
		default void rulesUpgraded(Clock clock, int fromVersion, long tracesAt, long proximityAt) {
		}

		/**
		 * A blind moment's recheck for this player ({@link #recheck}, D-077) ended with this outcome, having fired
		 * {@code fired} (its fire was recorded as any other, {@link #fired} first), or nothing (null).
		 */
		default void rechecked(Clock clock, @Nullable CardInfo fired, String outcome) {
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
	 * This player started taking part: a new session for them only. From {@code emptySessionMinStage} on (Proximity,
	 * D-070), the dice decide whether their whole session stays empty, except that a player who starts next to someone
	 * already in a session takes that session's mood (empty until the same moment, or not empty): otherwise each
	 * friend's own empty sessions would add up and a group would be silent far more often than one player. Nobody
	 * else's session changes. A pacing saved under older rules (D-075) rolls its major slot again here, never later than
	 * it was; the session minor rate is rolled at every session.
	 */
	public void onJoin(WorldMemory w, PacingMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		upgradePacing(m, c, random);
		advanceTime(w, m, c, env, random, rec);
		Stage stage = env.stage();
		m.sessionStart = c.playTicks();
		m.sessionCount++;
		m.sessionMinorRate = rules.minorsPerHourMin + random.nextDouble() * (rules.minorsPerHourMax - rules.minorsPerHourMin);
		boolean rolled = stage.atLeast(rules.emptySessionMinStage) && random.nextDouble() < rules.emptySessionChance;
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

	/**
	 * D-075: a pacing rolled under older rules (v1.1's major every 2 to 4 hours) takes the current ones at its next
	 * session: a major slot still ahead is rolled again from now, and kept if the old one comes sooner. Once.
	 */
	private void upgradePacing(PacingMemory m, Clock c, RandomSource random) {
		if (m.rulesVersion >= WorldMemory.RULES_VERSION) {
			return;
		}
		if (m.nextMajorDue > c.playTicks()) {
			m.nextMajorDue = Math.min(m.nextMajorDue, c.playTicks() + rules.majorEvery.pick(random));
		}
		m.rulesVersion = WorldMemory.RULES_VERSION;
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
		upgradeWorld(w, c, env, random, rec);
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

	/**
	 * D-075: a world whose stage points were rolled under older rules (v1.1: Traces at 1 to 3 hours, Proximity at 5 to
	 * 6) rolls them again in the current windows, once, before its time moves. A stage already reached stays reached
	 * (the stage never moves back), and none is skipped: a world still in Alone whose new Traces point has already gone
	 * by starts Traces now, with Proximity its rolled stretch later. A world that never rolled them rolls them as any
	 * new world does.
	 */
	private void upgradeWorld(WorldMemory w, Clock c, Env env, RandomSource random, Recorder rec) {
		if (w.rulesVersion >= WorldMemory.RULES_VERSION) {
			return;
		}
		int from = w.rulesVersion;
		w.rulesVersion = WorldMemory.RULES_VERSION;
		if (w.tracesAt < 0 && w.proximityAt < 0) {
			return;
		}
		long traces = rules.tracesStart.pick(random);
		long proximity = Math.max(traces + 1, rules.proximityStart.pick(random));
		if (env.stage() == Stage.ALONE && w.stageClock >= traces) {
			long now = (long) Math.floor(w.stageClock);
			proximity = now + (proximity - traces);
			traces = now;
		}
		w.tracesAt = traces;
		w.proximityAt = proximity;
		w.rolledTempo = rules.profile.tempo().name();
		rec.rulesUpgraded(c, from, traces, proximity);
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
		endParks(m);
		releaseFitting(m, env);
		releaseStaleHolds(w, m, c, stage, rec);
		List<Sharer> sharers = env.sharers();
		shareMood(m, c, sharers, random, rec);
		releaseForSharers(m, c, sharers, rec);
		String blocked = globalBlock(m, c);
		if (blocked != null) {
			return "waiting: " + blocked;
		}
		boolean fog = boosts(env.fogSpell());
		double place = placeFactor(env, stage, c);
		List<String> notes = new ArrayList<>();
		for (Tier tier : PRIORITY) {
			// A card parked as it goes back (its moment did not come at any look) hands the roll that drew it to the next
			// card, so a card whose moment never comes costs its tier no fire (R1, v1.2). Each hand-on parks a card, which
			// is then out of the draw, so the chain ends within the tier's cards. No smaller cap (INT-2, v1.3): with two a
			// tick, a deck holding three or more such cards (the tunnel ladder's and far in the rock for a surface player,
			// a BLIND card struck between menus) lost the roll at the end of nearly every cycle, about a tenth of its fires.
			int carried = 0;
			while (true) {
				CardInfo card = card(m.held.get(tier));
				if (card == null) {
					if (tierBlock(tier, m, c, stage) != null || carried == 0 && !due(tier, m, c, stage, random, sharers.size(), fog, place)) {
						break;
					}
					Set<String> passedOver = new HashSet<>();
					card = draw(tier, w, m, c, stage, sharers, random, rec, passedOver, fog);
					if (card == null) {
						if (tier == Tier.MAJOR && !passedOver.isEmpty()) {
							// A stage weight passed the slot's card over: the slot is spent, as a probabilistic tier's
							// tick is. Retrying every tick would re-roll an "almost never" card until it fires.
							scheduleNextMajor(m, c.playTicks(), random);
						}
						break;
					}
					m.held.put(tier, card.id());
					m.heldSince.put(tier, c.playTicks());
					m.unfitLooks.remove(tier);
					rec.drew(c, card);
				}
				String why = fireBlock(card, w, m, c, stage);
				if (why != null) {
					notes.add(card.id() + " waits (" + why + ")");
					break;
				}
				String shared = sharedBlock(card, w, sharers, stage);
				if (shared != null) {
					notes.add(card.id() + " waits (" + shared + ")");
					break;
				}
				if (!env.contextFits(card)) {
					m.unfitLooks.merge(tier, 1L, Long::sum);
					if (unfitHold(card) > 0 && unfitGoesBack(m, c, tier, card)) {
						// Its moment may be far off (a card only for a tunnel, drawn on the surface; a BLIND card for someone
						// who opens no menu): it must not freeze the tier for the whole hold. Majors and signatures keep
						// theirs. Parked, it is out of its deck for a while, and the roll that drew it goes to the next card.
						boolean parks = parksNow(m, tier, card);
						String back = parks ? UNFIT + PARKED : UNFIT;
						giveUp(m, c, tier, card.id(), back, rec);
						if (parks) {
							park(m, c, card.id());
						}
						notes.add(card.id() + " goes back: " + back);
						if (parks && carried < byTier.get(tier).size()) {
							carried++;
							continue;
						}
					} else {
						notes.add(card.id() + " held: the moment does not fit");
					}
					break;
				}
				boolean fake = card.hasFake() && random.nextDouble() < rules.fakeChance;
				FireResult result = env.fire(card, fake);
				if (result == FireResult.FIRED) {
					recordFire(w, m, c, card, fake, false, stage, env, sharers, random, rec);
					return "fired " + card.id() + (fake ? " (fake)" : "") + feltBy(card, sharers);
				}
				notes.add(card.id() + " " + result + ", kept for later");
				break;
			}
		}
		return notes.isEmpty() ? "nothing due" : String.join("; ", notes);
	}

	/**
	 * Play ticks of looks at which its moment did not fit after which a held card goes back into its deck, parked
	 * ({@link #unfitGoesBack}), or 0 for a card that only has the long {@code holdGiveUp}: {@code blindHold} (15 minutes)
	 * for a {@code BLIND} card of any tier (D-077: a menu comes often, and the blind moments' rechecks look for it; a
	 * player who never opens one, or a client without the blind signal, must not freeze a tier), {@code heldUnfitGiveBack}
	 * (5 minutes) for another ambient or minor, 0 for a major or signature.
	 */
	long unfitHold(CardInfo card) {
		if (card.has(CardTag.BLIND)) {
			return rules.blindHold;
		}
		return card.tier() == Tier.AMBIENT || card.tier() == Tier.MINOR ? rules.heldUnfitGiveBack : 0;
	}

	/**
	 * The places' rate factor for this decision (docs/SELF_CREATED.md, 01; D-084): {@link Env#placeFactor} from Traces on
	 * once the player's own first hour is over, else exactly 1 (Alone, the first hour: no places yet).
	 */
	public double placeFactor(Env env, Stage stage, Clock c) {
		if (!stage.atLeast(Stage.TRACES) || c.playTicks() < rules.hourTicks) {
			return 1.0;
		}
		double factor = env.placeFactor();
		return Double.isFinite(factor) ? Math.max(0, factor) : 1.0;
	}

	/** True for a fog spell that is on and not quiet (D-071): the director's odds go up. */
	public static boolean boosts(Optional<FogSpell> spellOn) {
		return spellOn.isPresent() && !spellOn.get().quiet();
	}

	/**
	 * With someone close by, the cards held for this player that can never reach that moment go back into their decks
	 * at once, not after {@code holdGiveUp}, so they never block a tier while friends stand together and a card that
	 * suits everyone here may be drawn instead: a card one of them already had (D-069), and a {@code PERSONAL} card, for
	 * one player alone (D-074). Each is passed over by the next draw when it can be ({@code lastGivenUp}).
	 */
	private void releaseForSharers(PacingMemory m, Clock c, List<Sharer> sharers, Recorder rec) {
		if (sharers.isEmpty()) {
			return;
		}
		for (Tier tier : PRIORITY) {
			CardInfo card = card(m.held.get(tier));
			if (card == null) {
				continue;
			}
			if (card.has(CardTag.PERSONAL)) {
				giveUp(m, c, tier, card.id(), "someone came close (a card for one player alone)", rec);
			} else if (anyHad(card, sharers)) {
				giveUp(m, c, tier, card.id(), "someone close by already had it", rec);
			}
		}
	}

	// --- blind moments (D-077) ---

	/**
	 * A blind moment's recheck for this player alone (D-077): their client has a menu open, and core asks again between
	 * director ticks ({@code Director.recheck}). It looks only at what their pacing already has waiting: the cards they
	 * hold, then the cards they have parked or struck ({@link #park}), {@code BLIND} cards first, each tier in
	 * {@link #PRIORITY} order. The first whose gates are open and whose moment fits now ({@code contextFits}) fires,
	 * recorded as any fire (felt by whoever shares the moment); a parked one is released first, as a passing look
	 * releases it ({@link #releaseFitting}). At most one fire. Every gate of {@link #step} holds: an open session, a
	 * quiet the tension calls for (checked first, as a director tick does), the silence, the join grace, the quiet, an
	 * empty session, the gap after any fire, the first hour's cap, the tier's gaps and caps, the card's own gates, the
	 * sharers' gates (D-060.3), a {@code PERSONAL} card never beside a sharer (D-074), and a parked major only while a
	 * major slot is due. It never rolls a tier's odds and never draws; time does not move (no stage change, no decay)
	 * and nothing goes back into a deck here: a look that does not fit is not counted ({@code unfitLooks}), and holds
	 * that a sharer close by rules out wait for the next director tick to go back. A held card whose moment fits here
	 * but finds no spot stays held, and its unfit looks start again: its moment came, so its unfit hold
	 * ({@link #unfitHold}) counts from there.
	 *
	 * @return a one-line description of what happened, as {@link #step} gives
	 */
	public String recheck(WorldMemory w, PacingMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		m.rechecks++;
		CardInfo fired = null;
		String outcome;
		if (m.sessionStart < 0 || m.sessionStart > c.playTicks()) {
			outcome = "waiting: no session";
		} else {
			// Other systems may have raised tension since the last tick: a quiet it calls for starts before anything fires.
			checkQuiet(m, c, env, random, rec);
			String blocked = globalBlock(m, c);
			List<CardInfo> waiting = blocked == null ? waiting(m) : List.of();
			if (blocked != null) {
				outcome = "waiting: " + blocked;
			} else if (waiting.isEmpty()) {
				outcome = "nothing held or parked";
			} else {
				Stage stage = env.stage();
				boolean fog = boosts(env.fogSpell());
				List<Sharer> sharers = env.sharers();
				List<String> notes = new ArrayList<>();
				outcome = null;
				for (CardInfo card : waiting) {
					boolean held = card.id().equals(m.held.get(card.tier()));
					String why = recheckBlock(card, held, w, m, c, stage, sharers, fog);
					if (why != null) {
						notes.add(card.id() + " waits (" + why + ")");
						continue;
					}
					if (!env.contextFits(card)) {
						notes.add(card.id() + (held ? " held" : " parked") + ": the moment does not fit");
						continue;
					}
					if (!held) {
						m.parkedUntil.remove(card.id());
					}
					boolean fake = card.hasFake() && random.nextDouble() < rules.fakeChance;
					FireResult result = env.fire(card, fake);
					if (result == FireResult.FIRED) {
						recordFire(w, m, c, card, fake, false, stage, env, sharers, random, rec);
						m.recheckFires++;
						fired = card;
						outcome = "fired " + card.id() + (fake ? " (fake)" : "") + (held ? "" : ", released from its park")
								+ feltBy(card, sharers);
						break;
					}
					if (held) {
						m.unfitLooks.remove(card.tier());
					}
					notes.add(card.id() + " " + result + ", kept for later");
				}
				if (outcome == null) {
					outcome = String.join("; ", notes);
				}
			}
		}
		rec.rechecked(c, fired, outcome);
		return outcome;
	}

	/**
	 * What a recheck looks at, in its order: the held cards, then the parked or struck ones not held, each tier in
	 * {@link #PRIORITY} order (parks in the order they were made), every {@code BLIND} card before the rest. Cards no
	 * longer registered are left out.
	 */
	private List<CardInfo> waiting(PacingMemory m) {
		List<CardInfo> blind = new ArrayList<>();
		List<CardInfo> other = new ArrayList<>();
		for (Tier tier : PRIORITY) {
			CardInfo card = card(m.held.get(tier));
			if (card != null) {
				(card.has(CardTag.BLIND) ? blind : other).add(card);
			}
		}
		for (Tier tier : PRIORITY) {
			for (String id : m.parkedUntil.keySet()) {
				CardInfo card = card(id);
				if (card != null && card.tier() == tier && !id.equals(m.held.get(tier))) {
					(card.has(CardTag.BLIND) ? blind : other).add(card);
				}
			}
		}
		blind.addAll(other);
		return blind;
	}

	/**
	 * Why this held or parked card may not fire at a recheck now, or null: a {@code PERSONAL} card beside a sharer, its
	 * tier's and its own gates ({@link #fireBlock}), for a parked card one already had and a major with no slot due, and
	 * the sharers' gates ({@link #sharedBlock(CardInfo, WorldMemory, List, Stage)}).
	 */
	private @Nullable String recheckBlock(CardInfo card, boolean held, WorldMemory w, PacingMemory m, Clock c, Stage stage, List<Sharer> sharers,
			boolean fog) {
		if (!sharers.isEmpty() && card.has(CardTag.PERSONAL)) {
			return "someone close by: a card for one player alone";
		}
		String why = fireBlock(card, w, m, c, stage);
		if (why == null && !held) {
			if (had(card, m)) {
				why = "already had it";
			} else if (card.tier() == Tier.MAJOR && !majorDue(m, c, fog)) {
				why = "no major slot due";
			}
		}
		return why != null ? why : sharedBlock(card, w, sharers, stage);
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
		if (firstHourFull(m, c)) {
			return "first hour full";
		}
		return null;
	}

	/** True while the player's own first hour of play already holds {@code firstHourMaxFires} moments (D-070). */
	public boolean firstHourFull(PacingMemory m, Clock c) {
		return rules.firstHourMaxFires >= 0 && c.playTicks() < rules.hourTicks && m.firstHourFires >= rules.firstHourMaxFires;
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
		for (Sharer sharer : feeling(card, sharers)) {
			if (had(card, sharer.memory())) {
				return true;
			}
		}
		return false;
	}

	/** Who feels this card's fire beside its target (D-060.3): every sharer, nobody for an {@code UNSHARED} card. */
	static List<Sharer> feeling(CardInfo card, List<Sharer> sharers) {
		return card.has(CardTag.UNSHARED) ? List.of() : sharers;
	}

	/** The end of a fire's line: how many more felt it, if any. */
	private static String feltBy(CardInfo card, List<Sharer> sharers) {
		List<Sharer> felt = feeling(card, sharers);
		return felt.isEmpty() ? "" : ", felt by " + felt.size() + " more";
	}

	/**
	 * The first sharer's reason ({@link #sharedBlock(CardInfo, WorldMemory, PacingMemory, Clock, Stage)}), or null. Only
	 * those who would feel it ({@link #feeling}): an {@code UNSHARED} card waits for nobody else's gates.
	 */
	public String sharedBlock(CardInfo card, WorldMemory w, List<Sharer> sharers, Stage stage) {
		for (Sharer sharer : feeling(card, sharers)) {
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
	 * @param sharing how many others share this player's moments right now: together, the group rolls
	 *                {@code groupRateFactor} solo rates between them (D-074), each of the {@code sharing + 1} a share of
	 *                it. The factor sets the group's rate, so the refractory wait after a fire is the group rate's (a
	 *                fire anyone felt starts it for everyone); then the odds are split. Being together never multiplies
	 *                the ambient and minor odds by the number of friends (D-060.3); alone the solo rate is rolled
	 *                exactly as ever
	 * @param fog     a fog spell that is not quiet is on (D-071): the ambient and minor rate times
	 *                {@code fogWeatherOddsFactor}, the refractory wait included, so the mean rises by the whole factor;
	 *                and a major slot due within {@code fogWeatherMajorPull} is due now
	 * @param place   the places' factor ({@link #placeFactor(Env, Stage, Clock)}, D-084): the ambient and minor rate times it,
	 *                beside the fog's, before the refractory wait; 1 changes nothing
	 */
	private boolean due(Tier tier, PacingMemory m, Clock c, Stage stage, RandomSource random, int sharing, boolean fog, double place) {
		return switch (tier) {
			case AMBIENT, MINOR -> {
				double perHour = rate(tier, m, stage);
				if (fog) {
					perHour *= rules.fogWeatherOddsFactor;
				}
				if (place != 1.0) {
					perHour *= place;
				}
				if (sharing > 0) {
					perHour *= rules.groupRateFactor;
				}
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
				if (sharing > 0) {
					perHour /= 1 + sharing;
				}
				double perTick = perHour * (1 + pity(m, c)) * rules.tickInterval / rules.hourTicks;
				yield random.nextDouble() < 1 - Math.exp(-perTick);
			}
			case MAJOR -> majorDue(m, c, fog);
			case SIGNATURE -> true;
		};
	}

	/** This player's major slot is due now, or within {@code fogWeatherMajorPull} under a fog spell that boosts. */
	private boolean majorDue(PacingMemory m, Clock c, boolean fog) {
		return m.nextMajorDue >= 0 && (c.playTicks() >= m.nextMajorDue || fog && m.nextMajorDue - c.playTicks() <= rules.fogWeatherMajorPull);
	}

	// --- decks ---

	/**
	 * Cards of this tier this player could draw now: undrawn this cycle, not the last one fired, not parked, every gate
	 * open.
	 */
	public List<CardInfo> candidates(Tier tier, WorldMemory w, PacingMemory m, Clock c, Stage stage) {
		Set<String> cycle = m.cycleFired.get(tier);
		String last = m.lastFired.get(tier);
		List<CardInfo> out = new ArrayList<>();
		for (CardInfo card : byTier.get(tier)) {
			if (!cycle.contains(card.id()) && !card.id().equals(last) && !parked(m, c, card.id()) && cardBlock(card, w, m, c, stage) == null) {
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
	 * over together. A {@code PERSONAL} card is out of the moment while anyone is close (D-074), as a stage rule would
	 * rule it out: never drawn beside a friend, and never holding the deck back (a deck whose only undrawn cards are
	 * PERSONAL has run out for the moment and refills by the solo rule). Alone, the draw is exactly the solo one.
	 *
	 * <p>A parked card (given back because its moment did not come, {@link #park}) is out of the deck for its park the
	 * same way, alone or not: never drawn, and ruled out for the run-out rule, so a deck whose only undrawn cards are
	 * parked refills now with the cards that can fire.
	 *
	 * @param skipped filled with the cards a stage weight passed over in this draw
	 * @param fog     a fog spell that is not quiet is on: SIGHTING cards weigh more ({@link #stageWeight})
	 */
	private CardInfo draw(Tier tier, WorldMemory w, PacingMemory m, Clock c, Stage stage, List<Sharer> sharers, RandomSource random, Recorder rec,
			Set<String> skipped, boolean fog) {
		boolean refilled = false;
		String givenUp = m.lastGivenUp.get(tier);
		while (true) {
			List<CardInfo> eligible = candidates(tier, w, m, c, stage);
			eligible.removeIf(card -> skipped.contains(card.id()));
			if (!sharers.isEmpty()) {
				// A card for one player alone is out of a shared moment, as a stage rule would rule it out (D-074): a deck
				// whose only undrawn cards are such turns over by the solo rule, so a pair never deadlocks on them.
				eligible.removeIf(card -> card.has(CardTag.PERSONAL));
			}
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
				if (!runOut(tier, w, m, c, stage, sharers)) {
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
			CardInfo picked = weightedPick(eligible, stage, random, fog);
			if (picked == null) {
				return null;
			}
			double tagWeight = stageWeight(stage, picked, fog);
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
	 * already fired once per world), or is parked (its moment did not come, {@link #park}). A card that only waits on a
	 * passing gate (a sighting today, no accident yet, a skip roll) keeps it going.
	 */
	public boolean runOut(Tier tier, WorldMemory w, PacingMemory m, Clock c, Stage stage) {
		return runOut(tier, w, m, c, stage, List.of());
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
			if (card != null && !id.equals(last) && !parked(m, c, id) && cardBlock(card, w, m, c, stage) == null && !spentForTheMoment(card, sharers)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * {@link #runOut(Tier, WorldMemory, PacingMemory, Clock, Stage)} beside friends: a card one of them had, and a
	 * {@code PERSONAL} card, are spent for the moment too.
	 */
	private boolean runOut(Tier tier, WorldMemory w, PacingMemory m, Clock c, Stage stage, List<Sharer> sharers) {
		Set<String> cycle = m.cycleFired.get(tier);
		for (CardInfo card : byTier.get(tier)) {
			if (!cycle.contains(card.id()) && staticBlock(card, w, stage) == null && !parked(m, c, card.id()) && !spentForTheMoment(card, sharers)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * With friends close by, a card that cannot be this moment's: one of them already had it (D-060.3), or it is a
	 * {@code PERSONAL} card, for one player alone (D-074). Never true alone.
	 */
	private static boolean spentForTheMoment(CardInfo card, List<Sharer> sharers) {
		return !sharers.isEmpty() && (card.has(CardTag.PERSONAL) || anyHad(card, sharers));
	}

	/**
	 * A card's stage weight right now: the product of its tags' weights for the stage, and for a SIGHTING card times
	 * {@code fogWeatherSightingWeight} while a fog spell that is not quiet is on (D-071). It weighs the pick and, below
	 * 1, is the chance the drawn card is kept. A weight of 0 stays 0: the fog never brings a card into a stage.
	 */
	public double stageWeight(Stage stage, CardInfo card, boolean fog) {
		double weight = rules.tagWeight(stage, card);
		return fog && card.has(CardTag.SIGHTING) ? weight * rules.fogWeatherSightingWeight : weight;
	}

	private CardInfo weightedPick(List<CardInfo> eligible, Stage stage, RandomSource random, boolean fog) {
		double total = 0;
		double[] weights = new double[eligible.size()];
		for (int i = 0; i < weights.length; i++) {
			CardInfo card = eligible.get(i);
			weights[i] = Math.max(0, rules.habitWeight(card) * stageWeight(stage, card, fog));
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

	/**
	 * Held cards that go back on their own: one no longer registered, one ruled out by the stage or the profile, and one
	 * held for {@code holdGiveUp} without firing, which is also parked ({@link #park}) if it keeps not fitting
	 * ({@link #parksNow}).
	 */
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
				why = HELD_TOO_LONG;
			} else {
				why = staticBlock(card, w, stage);
			}
			if (why != null) {
				if (card != null) {
					// Parked only if its moment was what it waited for: a hold that sat out a quiet or an empty session
					// goes back as it always did.
					boolean parks = why.equals(HELD_TOO_LONG) && parksNow(m, tier, card);
					giveUp(m, c, tier, id, parks ? why + PARKED : why, rec);
					if (parks) {
						park(m, c, id);
					}
				} else {
					m.held.remove(tier);
					m.heldSince.remove(tier);
					m.unfitLooks.remove(tier);
					rec.gaveUp(c, tier, id, why);
				}
			}
		}
	}

	/** A held card goes back into its deck ({@code lastGivenUp}: the next draw passes it over if it can). */
	private static void giveUp(PacingMemory m, Clock c, Tier tier, String id, String why, Recorder rec) {
		m.held.remove(tier);
		m.heldSince.remove(tier);
		m.unfitLooks.remove(tier);
		m.lastGivenUp.put(tier, id);
		rec.gaveUp(c, tier, id, why);
	}

	/**
	 * A card that went back because its moment did not come is parked for {@code unfitPark} of this player's play
	 * (R1, v1.2): never drawn, and ruled out for the run-out rule, so its deck refills with the cards that can fire
	 * instead of drawing it again and again while its moment never comes (a tunnel card for a surface player). It comes
	 * back after the park, and fires once its moment does; until it fires, a hold of it goes back at its first look that
	 * does not fit ({@link #unfitGoesBack}), and hands its roll on, so it costs its tier no fire. A passing look at
	 * which its moment fits ends the park and the strike ({@link #releaseFitting}).
	 */
	private void park(PacingMemory m, Clock c, String id) {
		if (rules.unfitPark > 0) {
			m.parkedUntil.put(id, c.playTicks() + rules.unfitPark);
		}
	}

	/** True while this card is parked for this player ({@link #park}). */
	public boolean parked(PacingMemory m, Clock c, String id) {
		Long until = m.parkedUntil.get(id);
		return rules.unfitPark > 0 && until != null && c.playTicks() < until && until - c.playTicks() <= rules.unfitPark;
	}

	/**
	 * True if the current hold of this card's tier has been looked at for a whole {@link #unfitHold} of its own (a major's
	 * or signature's, at its long hold's end: {@code heldUnfitGiveBack}) and its moment never fit: as many looks as
	 * director ticks in it. Looks a gate held back (the gap after any fire, a quiet, an empty session, a friend's gates) do
	 * not count, so a hold only goes back for its moment, never for a gate; a recheck at which a held card's moment fit
	 * without firing starts the count again ({@link #recheck}).
	 */
	private boolean unfitAllAlong(PacingMemory m, Tier tier, CardInfo card) {
		long hold = unfitHold(card) > 0 ? unfitHold(card) : rules.heldUnfitGiveBack;
		return m.unfitLooks.getOrDefault(tier, 0L) >= Math.max(1, hold / rules.tickInterval);
	}

	/**
	 * True when a held card with an unfit hold ({@link #unfitHold}) whose moment does not fit at this look goes back:
	 * after that hold of play looked at all along ({@link #unfitAllAlong}), or at once for a struck one ({@link #struck}):
	 * one look, then back.
	 */
	private boolean unfitGoesBack(PacingMemory m, Clock c, Tier tier, CardInfo card) {
		if (struck(m, card)) {
			return true;
		}
		return c.playTicks() - m.heldSince.getOrDefault(tier, c.playTicks()) >= unfitHold(card) && unfitAllAlong(m, tier, card);
	}

	/**
	 * Whether the held card of this tier, going back now because its moment did not come, is parked: always while
	 * parking is on ({@code unfitPark} above 0), for a hold looked at all along or a card parked before.
	 */
	private boolean parksNow(PacingMemory m, Tier tier, CardInfo card) {
		return rules.unfitPark > 0 && (struck(m, card) || unfitAllAlong(m, tier, card));
	}

	/**
	 * True if this card already went back because its moment did not come ({@link #park}) and since then has neither
	 * fired, nor been felt, nor fitted at a passing look ({@link #releaseFitting}).
	 */
	private boolean struck(PacingMemory m, CardInfo card) {
		return rules.unfitPark > 0 && m.parkedUntil.containsKey(card.id());
	}

	/**
	 * Park entries stay until their card fires or is felt, or its moment fits at a passing look ({@link #releaseFitting});
	 * an ended park still means one look, then back. Those of cards no longer registered are dropped, and every one
	 * when parking is off.
	 */
	private void endParks(PacingMemory m) {
		if (!m.parkedUntil.isEmpty()) {
			if (rules.unfitPark <= 0) {
				m.parkedUntil.clear();
			} else {
				m.parkedUntil.keySet().removeIf(id -> !byId.containsKey(id));
			}
		}
	}

	/**
	 * Every director tick, each parked or struck card's moment is looked at in passing, whatever the gates (FIX-FINAL,
	 * v1.2): a card whose moment fits at such a look is not one whose moment never comes, so its park and its strike end
	 * there, and it is drawn again as any other card, with whole holds. A card whose moment is a short stretch of every
	 * day (the ridge's dusk sky, about 15% of it) is drawn mostly outside it and parked; without these looks it stayed
	 * struck until it fired, went back at its first look on nearly every later draw, and fired about a third less often
	 * than with no park at all. A card whose moment never comes is never released and costs its tier no more than
	 * before. The looks are only {@code contextFits} (cheap by contract), at most one per registered card.
	 */
	private void releaseFitting(PacingMemory m, Env env) {
		if (!m.parkedUntil.isEmpty()) {
			m.parkedUntil.keySet().removeIf(id -> {
				CardInfo card = card(id);
				return card != null && env.contextFits(card);
			});
		}
	}

	/**
	 * Sessions that meet share one mood (D-060.3), as a session started next to someone takes theirs ({@link #onJoin}):
	 * among this player and everyone close by, the session that started earliest is the moment's, and every later one
	 * takes its mood (empty until the same moment, or not empty). Otherwise one friend's empty session, rolled far
	 * away, would hold every card of the other's back for its whole length, and a pair would be silent far more often
	 * than one player. Two that started together keep one of theirs, picked by the dice, only when they differ. Alone
	 * nothing changes.
	 */
	private static void shareMood(PacingMemory m, Clock c, List<Sharer> sharers, RandomSource random, Recorder rec) {
		if (sharers.isEmpty()) {
			return;
		}
		List<PacingMemory> memories = new ArrayList<>();
		List<Clock> clocks = new ArrayList<>();
		List<Recorder> recorders = new ArrayList<>();
		memories.add(m);
		clocks.add(c);
		recorders.add(rec);
		for (Sharer sharer : sharers) {
			memories.add(sharer.memory());
			clocks.add(sharer.clock());
			recorders.add(sharer.recorder());
		}
		long earliest = -1;
		List<Integer> hosts = new ArrayList<>();
		for (int i = 0; i < memories.size(); i++) {
			PacingMemory p = memories.get(i);
			long now = clocks.get(i).playTicks();
			if (p.sessionStart < 0 || p.sessionStart > now) {
				continue;
			}
			long since = now - p.sessionStart;
			if (since > earliest) {
				earliest = since;
				hosts.clear();
			}
			if (since == earliest) {
				hosts.add(i);
			}
		}
		if (hosts.isEmpty()) {
			return;
		}
		int host = hosts.getFirst();
		for (int i : hosts) {
			if (moodLeft(memories.get(i), clocks.get(i)) != moodLeft(memories.get(host), clocks.get(host))) {
				host = hosts.get(random.nextInt(hosts.size()));
				break;
			}
		}
		long left = moodLeft(memories.get(host), clocks.get(host));
		for (int i = 0; i < memories.size(); i++) {
			PacingMemory p = memories.get(i);
			long now = clocks.get(i).playTicks();
			if (i == host || p.sessionStart < 0 || p.sessionStart > now || moodLeft(p, clocks.get(i)) == left) {
				continue;
			}
			p.sessionEmpty = left > 0;
			p.sessionEmptyUntil = left > 0 ? now + left : -1;
			recorders.get(i).sessionMood(clocks.get(i), p.sessionEmpty, p.sessionEmptyUntil);
		}
	}

	/** Play ticks left of this player's empty session, or 0 when it is not empty (any more). */
	private static long moodLeft(PacingMemory m, Clock c) {
		return m.sessionEmpty && c.playTicks() < m.sessionEmptyUntil ? m.sessionEmptyUntil - c.playTicks() : 0;
	}

	// --- fires, felt moments, tension, quiet ---

	private void recordFire(WorldMemory w, PacingMemory m, Clock c, CardInfo card, boolean fake, boolean forced, Stage stage, Env env,
			List<Sharer> sharers, RandomSource random, Recorder rec) {
		Tier tier = card.tier();
		long now = c.playTicks();
		if (card.id().equals(m.held.get(tier))) {
			m.held.remove(tier);
			m.heldSince.remove(tier);
			m.unfitLooks.remove(tier);
		}
		m.cycleFired.get(tier).add(card.id());
		m.lastFired.put(tier, card.id());
		m.lastGivenUp.remove(tier);
		m.deckStuckSince.remove(tier);
		m.parkedUntil.remove(card.id());
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
			if (now < rules.hourTicks) {
				m.firstHourFires++;
			}
			m.totalFires++;
			if (card.hasFake()) {
				m.fakeableFires++;
			}
			if (fake) {
				m.fakeFires++;
			}
			env.addTension(rules.tensionFor(tier) * (fake ? rules.fakeTensionFactor : 1), "fired " + card.id());
			if (!fake) {
				env.remember(m, card, c, false);
			}
			rec.fired(c, card, fake, false, stage, env.tension());
			checkQuiet(m, c, env, random, rec);
		}
		for (Sharer sharer : feeling(card, sharers)) {
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
			m.unfitLooks.remove(tier);
		}
		m.cycleFired.get(tier).add(card.id());
		m.lastFired.put(tier, card.id());
		m.parkedUntil.remove(card.id());
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
		if (now < rules.hourTicks) {
			m.firstHourFires++;
		}
		env.addTension(rules.tensionFor(tier) * (fake ? rules.fakeTensionFactor : 1), "felt " + card.id());
		if (!fake) {
			env.remember(m, card, c, true);
		}
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
