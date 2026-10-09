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
 * The director's decisions, with no world in sight: a clock, the memory, an {@link Env} for the shared state and
 * the cards' context, and a random in; decisions out. The live director, timewarp and the dry-run simulator all
 * run this same class.
 *
 * <p>Rules (DESIGN.md "The director", 4b; ARCHITECTURE "Director"):
 * <ul>
 * <li>Stages move by real play time: ALONE to TRACES at a point rolled inside {@code Pacing.tracesStart(tempo)},
 * TRACES to PROXIMITY inside {@code proximityStart(tempo)}. The stage clock runs faster or slower with attention,
 * within the configured band. TELLING comes only from a {@code HerobrineEvents.TELLING} that names him (D-041);
 * other telling (writing near his traces) never moves the stage. REMOVAL never from here.</li>
 * <li>One deck per tier, drawn without replacement and weighted by the profile habits and per-stage tag weights
 * (a weight below 1 also skips the card for that draw with chance 1 - weight). A deck runs out when every card it
 * has not fired this cycle is ruled out by the stage or the profile; only then do its fired cards come back, and
 * the last one fired is never drawn next. Cards that only wait on a passing gate (no accident yet, a sighting
 * today, a skip roll) hold the reshuffle back, up to {@code deckStall} of in-game time. Signature cards never come
 * back.</li>
 * <li>A drawn card is held until its context fits; {@code NO_SPOT} and {@code SKIPPED} keep it held. A card held
 * too long goes back into its deck.</li>
 * <li>Gates: join grace, forced quiet, empty sessions, a small gap between any two fires, the minor and major gaps,
 * no major (or signature) before {@code noMajorBeforeDay}, no ACCIDENT card before {@code firstAccident}, at most
 * {@code aloneMaxAmbient} ambients in Alone, sightings per day. Every card waits for its own earliest stage and its
 * tier's first stage ({@code minorMinStage}, {@code majorMinStage}, {@code signatureMinStage}).</li>
 * <li>Rates: ambient and minor tiers roll per director tick at their per-hour rate, raised by the pity bonus and
 * spread out by a refractory gap that keeps the mean;
 * majors are scheduled every {@code proximityMajorGap(tempo)} (a slot whose card a stage weight passes over is
 * spent); signatures are due as soon as one is eligible.</li>
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

	/** Shared state and the world-facing half of the cards. The live director uses the world; dry runs use dice. */
	public interface Env {
		Stage stage();

		void setStage(Stage stage);

		double attention();

		double tension();

		void addTension(double delta, String reason);

		boolean contextFits(CardInfo card);

		FireResult fire(CardInfo card, boolean fake);
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

		default void quietStarted(Clock clock, int days, long untilDayTicks, double tensionBefore) {
		}
	}

	/**
	 * @param playTicks real play ticks ({@code GameClock.playTicks})
	 * @param dayTicks  in-game time: {@code GameClock.day * 24000} plus the time of day
	 */
	public record Clock(long playTicks, long dayTicks) {
		public long day() {
			return Math.floorDiv(dayTicks, DAY_TICKS);
		}

		public Clock plus(long ticks) {
			return new Clock(playTicks + ticks, dayTicks + ticks);
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

	// --- sessions ---

	/** The subject joined: a new session. From Traces on, the dice decide whether the whole session stays empty. */
	public void onJoin(DirectorMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		advanceTime(m, c, env, random, rec);
		Stage stage = env.stage();
		m.sessionStart = c.playTicks();
		m.sessionCount++;
		m.sessionMinorRate = rules.minorsPerHourMin + random.nextDouble() * (rules.minorsPerHourMax - rules.minorsPerHourMin);
		m.sessionEmpty = stage.atLeast(Stage.TRACES) && random.nextDouble() < rules.emptySessionChance;
		m.sessionEmptyUntil = m.sessionEmpty ? c.playTicks() + rules.emptySessionMax : -1;
		if (m.sessionEmpty) {
			m.emptySessionCount++;
		}
		rec.sessionStarted(c, stage, m.sessionEmpty, m.sessionEmptyUntil);
	}

	public void onLeave(DirectorMemory m, Clock c, Recorder rec) {
		m.sessionStart = -1;
		m.sessionEmpty = false;
		m.sessionEmptyUntil = -1;
		rec.sessionEnded(c);
	}

	// --- time ---

	/** Time passes: the stage clock (weighted by attention), stage changes and tension decay. Never fires a card. */
	public void advanceTime(DirectorMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		String tempo = rules.profile.tempo().name();
		if (m.tracesAt < 0 || m.proximityAt < 0 || !tempo.equals(m.rolledTempo)) {
			m.tracesAt = rules.tracesStart.pick(random);
			m.proximityAt = Math.max(m.tracesAt + 1, rules.proximityStart.pick(random));
			m.rolledTempo = tempo;
		}
		if (m.stageClock < 0 || m.lastStepPlay < 0) {
			m.stageClock = c.playTicks();
			m.lastStepPlay = c.playTicks();
		}
		long dt = Math.max(0, c.playTicks() - m.lastStepPlay);
		m.lastStepPlay = Math.max(m.lastStepPlay, c.playTicks());
		if (dt > 0) {
			m.stageClock += dt / rules.attentionFactor(env.attention());
			double tension = env.tension();
			if (!inQuiet(m, c) && tension > 0) {
				env.addTension(-Math.min(tension, dt * rules.tensionDecayPerTick), "decay");
			}
		}
		Stage stage = env.stage();
		if (stage == Stage.ALONE && m.stageClock >= m.tracesAt) {
			stage = changeStage(c, env, stage, Stage.TRACES, rec);
		}
		if (stage == Stage.TRACES && m.stageClock >= m.proximityAt) {
			stage = changeStage(c, env, stage, Stage.PROXIMITY, rec);
		}
		if (stage.atLeast(rules.majorMinStage) && stage != Stage.REMOVAL && m.nextMajorDue < 0) {
			m.nextMajorDue = c.playTicks() + rules.majorEvery.pick(random);
		}
	}

	/**
	 * Lore fired {@code HerobrineEvents.TELLING}. Every telling counts as telling (the "obeyed after Stop." wait
	 * starts over), but only one that names him starts Telling (D-041): writing near his traces never moves the stage.
	 *
	 * @return true if the stage moved to Telling
	 */
	public boolean onTelling(DirectorMemory m, Clock c, Env env, boolean namesHim, Recorder rec) {
		m.lastTellingDay = c.day();
		m.obeyCounted = false;
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
	 * One director tick: let time pass, then draw, hold or fire at most one card, obeying every gate.
	 *
	 * @return a one-line description of what happened, for {@code /a1016 director tick} and the log
	 */
	public String step(DirectorMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		advanceTime(m, c, env, random, rec);
		if (m.sessionStart < 0 || m.sessionStart > c.playTicks()) {
			onJoin(m, c, env, random, rec);
		}
		// Other systems may have raised tension since the last tick, not only our own fires.
		checkQuiet(m, c, env, random, rec);
		Stage stage = env.stage();
		releaseStaleHolds(m, c, stage, rec);
		String blocked = globalBlock(m, c);
		if (blocked != null) {
			return "waiting: " + blocked;
		}
		List<String> notes = new ArrayList<>();
		for (Tier tier : PRIORITY) {
			CardInfo card = card(m.held.get(tier));
			if (card == null) {
				if (tierBlock(tier, m, c, stage) != null || !due(tier, m, c, stage, random)) {
					continue;
				}
				Set<String> passedOver = new HashSet<>();
				card = draw(tier, m, c, stage, random, rec, passedOver);
				if (card == null) {
					if (tier == Tier.MAJOR && !passedOver.isEmpty()) {
						// A stage weight passed the slot's card over: the slot is spent, as a probabilistic tier's
						// tick is. Retrying every tick would re-roll an "almost never" card until it fires.
						m.nextMajorDue = c.playTicks() + rules.majorEvery.pick(random);
					}
					continue;
				}
				m.held.put(tier, card.id());
				m.heldSince.put(tier, c.playTicks());
				rec.drew(c, card);
			}
			String why = fireBlock(card, m, c, stage);
			if (why != null) {
				notes.add(card.id() + " waits (" + why + ")");
				continue;
			}
			if (!env.contextFits(card)) {
				notes.add(card.id() + " held: the moment does not fit");
				continue;
			}
			boolean fake = card.hasFake() && random.nextDouble() < rules.fakeChance;
			FireResult result = env.fire(card, fake);
			if (result == FireResult.FIRED) {
				recordFire(m, c, card, fake, false, stage, env, random, rec);
				return "fired " + card.id() + (fake ? " (fake)" : "");
			}
			notes.add(card.id() + " " + result + ", kept for later");
		}
		return notes.isEmpty() ? "nothing due" : String.join("; ", notes);
	}

	/** A forced debug fire happened: history, deck and tag times, never pacing, tension or quiet. */
	public void recordForced(DirectorMemory m, Clock c, CardInfo card, boolean fake, Stage stage, Recorder rec) {
		recordFire(m, c, card, fake, true, stage, null, null, rec);
	}

	// --- gates ---

	public boolean inQuiet(DirectorMemory m, Clock c) {
		return c.dayTicks() < m.quietUntilDayTicks;
	}

	/** Reason nothing at all may fire now, or null. */
	public String globalBlock(DirectorMemory m, Clock c) {
		long now = c.playTicks();
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

	/** Reason this tier may not fire now, or null. */
	public String tierBlock(Tier tier, DirectorMemory m, Clock c, Stage stage) {
		long now = c.playTicks();
		return switch (tier) {
			case AMBIENT -> stage == Stage.ALONE && m.aloneAmbientCount >= rules.aloneMaxAmbient ? "Alone ambient cap" : null;
			case MINOR -> !stage.atLeast(rules.minorMinStage) ? "stage"
					: now - m.lastFireTier.getOrDefault(Tier.MINOR, DirectorMemory.NEVER) < rules.minorGap ? "minor gap" : null;
			case MAJOR -> !stage.atLeast(rules.majorMinStage) ? "stage"
					: c.day() < rules.noMajorBeforeDay ? "first day"
					: now - m.lastMajorOrSignature < rules.majorGap ? "major gap" : null;
			case SIGNATURE -> !stage.atLeast(rules.signatureMinStage) ? "stage"
					: c.day() < rules.noMajorBeforeDay ? "first day"
					: now - m.lastMajorOrSignature < rules.majorGap ? "major gap" : null;
		};
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

	/** Reason this card may not fire now (tier and card gates), or null. */
	public String fireBlock(CardInfo card, DirectorMemory m, Clock c, Stage stage) {
		String tier = tierBlock(card.tier(), m, c, stage);
		return tier != null ? tier : cardBlock(card, m, c, stage);
	}

	private String cardBlock(CardInfo card, DirectorMemory m, Clock c, Stage stage) {
		String fixed = staticBlock(card, m, stage);
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

	/** Blocks that only change with the stage, the profile or a once-per-world fire (never with the moment). */
	private String staticBlock(CardInfo card, DirectorMemory m, Stage stage) {
		if (!stage.atLeast(minStage(card))) {
			return "stage";
		}
		if (rules.tagWeight(stage, card) <= 0) {
			return "not in " + stage;
		}
		if (rules.habitWeight(card) <= 0) {
			return "not this world's habits";
		}
		if (oncePerWorld(card) && m.signaturesFired.contains(card.id())) {
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
	public double pity(DirectorMemory m, Clock c) {
		long since = c.playTicks() - Math.max(0, m.lastFireAny);
		double hoursPast = (since - rules.pityStart) / (double) rules.hourTicks;
		return hoursPast <= 0 ? 0 : Math.min(rules.pityMaxBonus, hoursPast * rules.pityBonusPerHour);
	}

	/** Per-hour rate of a probabilistic tier in this stage (before pity), or -1 for scheduled tiers. */
	public double rate(Tier tier, DirectorMemory m, Stage stage) {
		return switch (tier) {
			case AMBIENT -> switch (stage) {
				case ALONE -> rules.aloneAmbientPerHour;
				case TRACES -> rules.tracesAmbientPerHour;
				case PROXIMITY -> rules.proximityAmbientPerHour;
				default -> rules.tellingAmbientPerHour;
			};
			case MINOR -> stage.atLeast(Stage.PROXIMITY)
					? (m.sessionMinorRate >= 0 ? m.sessionMinorRate : (rules.minorsPerHourMin + rules.minorsPerHourMax) / 2)
					: rules.tracesMinorsPerHour;
			default -> -1;
		};
	}

	private boolean due(Tier tier, DirectorMemory m, Clock c, Stage stage, RandomSource random) {
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
				double perTick = perHour * (1 + pity(m, c)) * rules.tickInterval / rules.hourTicks;
				yield random.nextDouble() < 1 - Math.exp(-perTick);
			}
			case MAJOR -> m.nextMajorDue >= 0 && c.playTicks() >= m.nextMajorDue;
			case SIGNATURE -> true;
		};
	}

	// --- decks ---

	/** Cards of this tier that could be drawn now: undrawn this cycle, not the last one fired, every gate open. */
	public List<CardInfo> candidates(Tier tier, DirectorMemory m, Clock c, Stage stage) {
		Set<String> cycle = m.cycleFired.get(tier);
		String last = m.lastFired.get(tier);
		List<CardInfo> out = new ArrayList<>();
		for (CardInfo card : byTier.get(tier)) {
			if (!cycle.contains(card.id()) && !card.id().equals(last) && cardBlock(card, m, c, stage) == null) {
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
	 * @param skipped filled with the cards a stage weight passed over in this draw
	 */
	private CardInfo draw(Tier tier, DirectorMemory m, Clock c, Stage stage, RandomSource random, Recorder rec, Set<String> skipped) {
		boolean refilled = false;
		String givenUp = m.lastGivenUp.get(tier);
		while (true) {
			List<CardInfo> eligible = candidates(tier, m, c, stage);
			eligible.removeIf(card -> skipped.contains(card.id()));
			if (eligible.isEmpty()) {
				if (refilled || tier == Tier.SIGNATURE || m.cycleFired.get(tier).isEmpty()) {
					return null;
				}
				if (!runOut(tier, m, stage)) {
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
	public boolean runOut(Tier tier, DirectorMemory m, Stage stage) {
		Set<String> cycle = m.cycleFired.get(tier);
		for (CardInfo card : byTier.get(tier)) {
			if (!cycle.contains(card.id()) && staticBlock(card, m, stage) == null) {
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

	private void releaseStaleHolds(DirectorMemory m, Clock c, Stage stage, Recorder rec) {
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
				why = staticBlock(card, m, stage);
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

	// --- fires, tension, quiet ---

	private void recordFire(DirectorMemory m, Clock c, CardInfo card, boolean fake, boolean forced, Stage stage, Env env,
			RandomSource random, Recorder rec) {
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
			m.signaturesFired.add(card.id());
		}
		for (CardTag tag : card.tags()) {
			m.lastTag.put(tag, now);
		}
		if (card.has(CardTag.SIGHTING)) {
			if (m.sightingDay != c.day()) {
				m.sightingDay = c.day();
				m.sightingsThatDay = 0;
			}
			m.sightingsThatDay++;
		}
		m.addHistory(new DirectorMemory.HistoryEntry(now, c.day(), card.id(), tier, fake, forced, stage), historySize);
		if (forced) {
			rec.fired(c, card, fake, true, stage, Double.NaN);
			return;
		}
		m.lastFireAny = now;
		m.lastFireTier.put(tier, now);
		if (tier == Tier.MAJOR || tier == Tier.SIGNATURE) {
			m.lastMajorOrSignature = now;
		}
		if (tier == Tier.MAJOR) {
			m.nextMajorDue = now + rules.majorEvery.pick(random);
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

	/** Tension at the threshold starts a quiet, whatever raised it. A running quiet is never restarted. */
	private void checkQuiet(DirectorMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
		if (!inQuiet(m, c) && env.tension() >= rules.tensionThreshold) {
			startQuiet(m, c, env, random, rec);
		}
	}

	private void startQuiet(DirectorMemory m, Clock c, Env env, RandomSource random, Recorder rec) {
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
