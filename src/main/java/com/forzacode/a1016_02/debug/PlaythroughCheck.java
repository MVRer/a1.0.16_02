package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.CardInfo;
import com.forzacode.a1016_02.director.DirectorRules;
import com.forzacode.a1016_02.director.DirectorSim.Event;
import com.forzacode.a1016_02.director.DirectorSim.Kind;

/**
 * DESIGN.md 4b, checked against a recorded dry run without asking the director's own rules. Hard limits are the
 * "never" rules; soft targets are rates, reported but never failed. Numbers come from {@code Pacing} through
 * {@link DirectorRules}, so they follow the config (and devFastMode) like the director does.
 */
final class PlaythroughCheck {
	enum Status { PASS, FAIL, OFF, NA }

	/** One checked rule. {@code breaches} lists every place a hard limit broke (empty when it held). */
	record Check(String name, boolean hard, Status status, String measured, List<String> breaches) {
		boolean failed() {
			return hard && status == Status.FAIL;
		}
	}

	/**
	 * The numbers the checks use, in play ticks.
	 *
	 * @param majorEveryMin the Proximity major gap, already scaled by the tempo (2 to 4 h times its pace factor)
	 */
	record Limits(long hourTicks, long tickInterval, long joinGrace, long minorGap, int noMajorBeforeDay, int aloneMaxAmbient,
			long firstAccident, double firstAccidentHours, double paceFactor, double tracesAmbientPerHour, double minorsPerHourMin,
			double minorsPerHourMax, long majorEveryMin, long majorEveryMax, double emptyChance) {
		static Limits of(DirectorRules rules, double firstAccidentHours, double paceFactor) {
			return new Limits(rules.hourTicks, rules.tickInterval, rules.joinGrace, rules.minorGap, rules.noMajorBeforeDay, rules.aloneMaxAmbient,
					rules.firstAccident, firstAccidentHours, paceFactor, rules.tracesAmbientPerHour, rules.minorsPerHourMin, rules.minorsPerHourMax,
					rules.majorEvery.min(), rules.majorEvery.max(), rules.emptySessionChance);
		}

		double hours(long ticks) {
			return ticks / (double) hourTicks;
		}
	}

	/** Play ticks spent in a stage, and the part of them outside quiets and empty sessions. */
	record StageTime(long total, long active) {
	}

	/** Where the time went, from the event record. Intervals are [start, end) play ticks. */
	record Timeline(Map<Stage, StageTime> time, List<long[]> quiets, List<long[]> empties, List<long[]> sessions) {
		StageTime in(Stage stage) {
			return time.getOrDefault(stage, new StageTime(0, 0));
		}
	}

	private PlaythroughCheck() {
	}

	static Timeline timeline(List<Event> events, long startPlay, long endPlay, Stage startStage, Limits limits) {
		List<long[]> quiets = new ArrayList<>();
		List<long[]> empties = new ArrayList<>();
		List<long[]> sessions = new ArrayList<>();
		long sessionStart = -1;
		long emptyStart = -1;
		long emptyUntil = -1;
		for (Event e : events) {
			if (e.kind() == Kind.QUIET) {
				// Dry runs move the in-game clock with play time, so a quiet's day ticks are its play ticks.
				quiets.add(new long[] {e.play(), e.play() + Math.max(0, e.until() - e.dayTicks())});
			}
			if (e.kind() == Kind.SESSION_END || e.kind() == Kind.SESSION_START) {
				if (sessionStart >= 0) {
					sessions.add(new long[] {sessionStart, e.play()});
					sessionStart = -1;
				}
				if (emptyStart >= 0) {
					empties.add(new long[] {emptyStart, Math.min(e.play(), emptyUntil)});
					emptyStart = -1;
				}
			}
			if (e.kind() == Kind.SESSION_START) {
				sessionStart = e.play();
				if (e.flag()) {
					emptyStart = e.play();
					emptyUntil = e.until();
				}
			}
		}
		if (sessionStart >= 0) {
			sessions.add(new long[] {sessionStart, endPlay});
		}
		if (emptyStart >= 0) {
			empties.add(new long[] {emptyStart, Math.min(endPlay, emptyUntil)});
		}

		Map<Stage, long[]> sums = new EnumMap<>(Stage.class);
		List<Event> stages = events.stream().filter(e -> e.kind() == Kind.STAGE).toList();
		long step = Math.max(1, limits.tickInterval());
		int next = 0;
		Stage stage = startStage;
		for (long t = startPlay + step; t <= endPlay; t += step) {
			while (next < stages.size() && stages.get(next).play() <= t) {
				stage = stages.get(next++).stage();
			}
			long[] sum = sums.computeIfAbsent(stage, s -> new long[2]);
			sum[0] += step;
			if (!inside(quiets, t) && !inside(empties, t)) {
				sum[1] += step;
			}
		}
		Map<Stage, StageTime> time = new EnumMap<>(Stage.class);
		sums.forEach((s, sum) -> time.put(s, new StageTime(sum[0], sum[1])));
		return new Timeline(time, quiets, empties, sessions);
	}

	private static boolean inside(List<long[]> intervals, long t) {
		for (long[] interval : intervals) {
			if (t >= interval[0] && t < interval[1]) {
				return true;
			}
		}
		return false;
	}

	private static boolean isMajor(Event e) {
		return e.tier() == Tier.MAJOR || e.tier() == Tier.SIGNATURE;
	}

	/** The "never" rules of 4b. */
	static List<Check> hard(List<Event> events, Limits limits, Map<String, CardInfo> cards, List<String> directorViolations) {
		long h = limits.hourTicks();
		List<Event> fires = events.stream().filter(e -> e.kind() == Kind.FIRE).toList();
		List<Check> checks = new ArrayList<>();

		// Nothing in the first minutes after joining.
		List<String> breaches = new ArrayList<>();
		long session = Long.MIN_VALUE;
		for (Event e : events) {
			if (e.kind() == Kind.SESSION_START) {
				session = e.play();
			} else if (e.kind() == Kind.FIRE && session != Long.MIN_VALUE && e.play() - session < limits.joinGrace()) {
				breaches.add(at(e, h) + e.cardId() + " fired " + Fmt.dur(e.play() - session, h) + " after joining");
			}
		}
		checks.add(hard(Fmt.f("nothing in the first %d min after joining", Fmt.minutes(limits.joinGrace(), h)), breaches,
				fires.size() + " fires checked"));

		// No major (or signature) in the first in-game day(s).
		breaches = new ArrayList<>();
		for (Event e : fires) {
			if (isMajor(e) && e.day() < limits.noMajorBeforeDay()) {
				breaches.add(at(e, h) + Fmt.lower(e.tier()) + " " + e.cardId() + " on day " + e.day());
			}
		}
		long majors = fires.stream().filter(PlaythroughCheck::isMajor).count();
		checks.add(hard(limits.noMajorBeforeDay() == 1 ? "no major on day 0" : "no major before day " + limits.noMajorBeforeDay(), breaches,
				majors + " majors and signatures checked"));

		// Minors apart.
		breaches = new ArrayList<>();
		Event lastMinor = null;
		long minorCount = 0;
		long closest = Long.MAX_VALUE;
		for (Event e : fires) {
			if (e.tier() != Tier.MINOR) {
				continue;
			}
			minorCount++;
			if (lastMinor != null) {
				long gap = e.play() - lastMinor.play();
				closest = Math.min(closest, gap);
				if (gap < limits.minorGap()) {
					breaches.add(at(e, h) + lastMinor.cardId() + " then " + e.cardId() + " only " + Fmt.dur(gap, h) + " apart");
				}
			}
			lastMinor = e;
		}
		checks.add(hard(Fmt.f("at least %d min between minors", Fmt.minutes(limits.minorGap(), h)), breaches,
				minorCount + " minors" + (closest == Long.MAX_VALUE ? "" : ", closest " + Fmt.dur(closest, h) + " apart")));

		// At most one major per real hour: no two majors (or signatures) inside any one-hour window.
		breaches = new ArrayList<>();
		Event lastMajor = null;
		closest = Long.MAX_VALUE;
		for (Event e : fires) {
			if (!isMajor(e)) {
				continue;
			}
			if (lastMajor != null) {
				long gap = e.play() - lastMajor.play();
				closest = Math.min(closest, gap);
				if (gap < h) {
					breaches.add(at(e, h) + lastMajor.cardId() + " then " + e.cardId() + " only " + Fmt.dur(gap, h) + " apart");
				}
			}
			lastMajor = e;
		}
		checks.add(hard("at most 1 major per real hour", breaches,
				majors + " majors and signatures" + (closest == Long.MAX_VALUE ? "" : ", closest " + Fmt.dur(closest, h) + " apart")));

		// Alone: at most one ambient oddity, nothing else.
		breaches = new ArrayList<>();
		int aloneAmbients = 0;
		for (Event e : fires) {
			if (e.stage() != Stage.ALONE) {
				continue;
			}
			if (e.tier() != Tier.AMBIENT) {
				breaches.add(at(e, h) + Fmt.lower(e.tier()) + " " + e.cardId() + " in Alone");
			} else if (++aloneAmbients > limits.aloneMaxAmbient()) {
				breaches.add(at(e, h) + "ambient number " + aloneAmbients + " in Alone (" + e.cardId() + ")");
			}
		}
		checks.add(hard(Fmt.f("at most %d ambient in Alone, nothing else", limits.aloneMaxAmbient()), breaches,
				aloneAmbients + " ambient in Alone"));

		// No accident before hour 6 x the tempo factor.
		breaches = new ArrayList<>();
		Event firstAccident = null;
		int accidents = 0;
		for (Event e : fires) {
			CardInfo card = cards.get(e.cardId());
			if (card == null || !card.has(CardTag.ACCIDENT)) {
				continue;
			}
			accidents++;
			if (firstAccident == null) {
				firstAccident = e;
			}
			if (e.play() < limits.firstAccident()) {
				breaches.add(at(e, h) + "accident card " + e.cardId());
			}
		}
		checks.add(hard(Fmt.f("no accident before %s (%.0f h x %.2f)", Fmt.hm(limits.firstAccident(), h), limits.firstAccidentHours(),
				limits.paceFactor()), breaches,
				accidents + " accident fires" + (firstAccident == null ? "" : ", first at " + Fmt.hm(firstAccident.play(), h))));

		// The director's own replay of its rules (decks, quiets, signatures, fakes, empty sessions only from Traces).
		checks.add(hard("director's own rules (deck, quiet, signature, fake)", directorViolations, directorViolations.isEmpty() ? "all held"
				: directorViolations.size() + " broken"));
		return checks;
	}

	/**
	 * The rates of 4b, "allowing quiet": quiets and empty sessions may make a stage sparser than the target, never
	 * busier. So a rate is on target when it is not above the band over all the time in the stage, and not below it
	 * over the active time (neither quiet nor an empty session). A run that reached Telling (the director config's
	 * {@code simTellingAtHour}) also gets "Telling: sightings almost none".
	 */
	static List<Check> soft(List<Event> events, Timeline timeline, Limits limits, Map<String, CardInfo> cards) {
		long h = limits.hourTicks();
		List<Event> fires = events.stream().filter(e -> e.kind() == Kind.FIRE).toList();
		List<Check> checks = new ArrayList<>();

		// Traces: about one ambient per hour.
		StageTime traces = timeline.in(Stage.TRACES);
		long tracesAmbients = fires.stream().filter(e -> e.stage() == Stage.TRACES && e.tier() == Tier.AMBIENT).count();
		double target = limits.tracesAmbientPerHour();
		checks.add(rate(Fmt.f("Traces: about %.1f ambient per hour (+-50%%)", target), tracesAmbients, traces, h / 2, target * 0.5, target * 1.5,
				"ambients", limits));

		// Proximity: one or two minors per hour.
		StageTime proximity = timeline.in(Stage.PROXIMITY);
		long minors = fires.stream().filter(e -> e.stage() == Stage.PROXIMITY && e.tier() == Tier.MINOR).count();
		checks.add(rate(Fmt.f("Proximity: %.0f to %.0f minors per hour", limits.minorsPerHourMin(), limits.minorsPerHourMax()), minors, proximity, h,
				limits.minorsPerHourMin(), limits.minorsPerHourMax(), "minors", limits));

		// Proximity: a major every 2 to 4 hours (times the tempo factor). As gaps: closer than the shortest gap over
		// all the time, or farther apart than the longest gap over the active time, is off.
		long majors = fires.stream().filter(e -> e.stage() == Stage.PROXIMITY && e.tier() == Tier.MAJOR).count();
		String majorName = Fmt.f("Proximity: a major every %s to %s", Fmt.hm(limits.majorEveryMin(), h), Fmt.hm(limits.majorEveryMax(), h));
		if (majors == 0) {
			boolean longEnough = proximity.active() > limits.majorEveryMax();
			checks.add(soft(majorName, longEnough ? Status.OFF : Status.NA, Fmt.f("none in %s active (%s in Proximity)", Fmt.hm(proximity.active(), h),
					Fmt.hm(proximity.total(), h))));
		} else {
			long active = proximity.active() / majors;
			long overall = proximity.total() / majors;
			boolean ok = overall >= limits.majorEveryMin() && active <= limits.majorEveryMax();
			checks.add(soft(majorName, ok ? Status.PASS : Status.OFF, Fmt.f("every %s active, %s overall (%d major%s, %s active of %s)",
					Fmt.hm(active, h), Fmt.hm(overall, h), majors, majors == 1 ? "" : "s", Fmt.hm(proximity.active(), h), Fmt.hm(proximity.total(), h))));
		}

		// From Traces on, some whole sessions are empty.
		long sessionsLater = events.stream().filter(e -> e.kind() == Kind.SESSION_START && e.stage().atLeast(Stage.TRACES)).count();
		long emptyLater = events.stream().filter(e -> e.kind() == Kind.SESSION_START && e.flag() && e.stage().atLeast(Stage.TRACES)).count();
		String emptyName = "empty sessions from Traces on";
		if (sessionsLater == 0) {
			checks.add(soft(emptyName, Status.NA, "no session started in Traces or later"));
		} else {
			checks.add(soft(emptyName, emptyLater > 0 ? Status.PASS : Status.OFF, Fmt.f("%d of %d sessions (chance %.2f each)", emptyLater,
					sessionsLater, limits.emptyChance())));
		}

		// Telling: sightings almost none (DESIGN.md "Sightings"): at most one, or a fifth of Proximity's rate.
		StageTime telling = timeline.in(Stage.TELLING);
		if (telling.total() > 0) {
			String tellingName = "Telling: sightings almost none (at most a fifth of Proximity's rate)";
			if (telling.total() < h) {
				checks.add(soft(tellingName, Status.NA, Fmt.f("only %s in Telling", Fmt.hm(telling.total(), h))));
			} else {
				long inTelling = sightings(fires, cards, Stage.TELLING);
				long inProximity = sightings(fires, cards, Stage.PROXIMITY);
				double tellingRate = inTelling / limits.hours(telling.total());
				double proximityRate = proximity.total() <= 0 ? 0 : inProximity / limits.hours(proximity.total());
				boolean ok = inTelling <= 1 || tellingRate * 5 <= proximityRate;
				checks.add(soft(tellingName, ok ? Status.PASS : Status.OFF, Fmt.f("%.2f/h in Telling (%d in %s), %.2f/h in Proximity (%d in %s)",
						tellingRate, inTelling, Fmt.hm(telling.total(), h), proximityRate, inProximity, Fmt.hm(proximity.total(), h))));
			}
		}
		return checks;
	}

	private static long sightings(List<Event> fires, Map<String, CardInfo> cards, Stage stage) {
		return fires.stream().filter(e -> e.stage() == stage && cards.containsKey(e.cardId()) && cards.get(e.cardId()).has(CardTag.SIGHTING)).count();
	}

	/** A per-hour rate: not above {@code hi} over all the stage's time, not below {@code lo} over its active time. */
	private static Check rate(String name, long count, StageTime time, long minActive, double lo, double hi, String what, Limits limits) {
		long h = limits.hourTicks();
		if (time.active() < minActive) {
			return soft(name, Status.NA, Fmt.f("only %s active (%s in the stage)", Fmt.hm(time.active(), h), Fmt.hm(time.total(), h)));
		}
		double active = count / limits.hours(time.active());
		double overall = count / limits.hours(time.total());
		boolean ok = overall <= hi && active >= lo;
		return soft(name, ok ? Status.PASS : Status.OFF, Fmt.f("%.2f/h active, %.2f/h overall (%d %s, %s active of %s)", active, overall, count, what,
				Fmt.hm(time.active(), h), Fmt.hm(time.total(), h)));
	}

	private static Check hard(String name, List<String> breaches, String measured) {
		return new Check(name, true, breaches.isEmpty() ? Status.PASS : Status.FAIL, measured, List.copyOf(breaches));
	}

	private static Check soft(String name, Status status, String measured) {
		return new Check(name, false, status, measured, List.of());
	}

	private static String at(Event e, long hourTicks) {
		return Fmt.hm(e.play(), hourTicks) + " day " + e.day() + ": ";
	}
}
