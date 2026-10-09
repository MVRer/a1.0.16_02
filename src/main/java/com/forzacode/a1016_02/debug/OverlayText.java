package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.director.DirectorApi;
import com.forzacode.a1016_02.director.DirectorMemory;

/** The overlay's rows from a director snapshot, the armed trap and the sighting phase. Pure text, {@code Locale.ROOT}. */
final class OverlayText {
	private static final long DAY_TICKS = 24000L;

	private OverlayText() {
	}

	/**
	 * @param trap     the armed trap ("none" if nothing is armed)
	 * @param sighting the sighting phase ("none" if no figure is out)
	 */
	static List<String> rows(DirectorApi.Snapshot s, String trap, String sighting) {
		long h = s.hourTicks();
		List<String> rows = new ArrayList<>();
		row(rows, "stage", Fmt.f("%s | tempo %s x%.2f", s.stage(), s.tempo(), s.paceFactor()));
		row(rows, "attention", Fmt.f("%.1f | tension %.1f/%.0f | pity +%.0f%%", s.attention(), s.tension(), s.tensionThreshold(), s.pity() * 100));
		row(rows, "quiet", !s.inQuiet() ? "no"
				: Fmt.f("until day %d (%.2f d left)", Math.floorDiv(s.quietUntilDayTicks(), DAY_TICKS),
						(s.quietUntilDayTicks() - s.dayTicks()) / (double) DAY_TICKS));
		row(rows, "now", s.blocked() == null ? "open" : s.blocked());
		if (s.held().isEmpty()) {
			row(rows, "held", "-");
		}
		for (DirectorApi.Held held : s.held()) {
			row(rows, "held", Fmt.f("%s %s (%s) waits: %s", Fmt.lower(held.tier()), held.cardId(), Fmt.dur(held.heldTicks(), h), held.waiting()));
		}
		DirectorMemory.HistoryEntry last = s.lastFire();
		row(rows, "last", last == null ? "-"
				: Fmt.f("%s %s%s%s, %s ago", Fmt.lower(last.tier()), last.cardId(), last.fake() ? " (fake)" : " (real)", last.forced() ? " forced" : "",
						Fmt.dur(s.lastFireAgo(), h)));
		row(rows, "next minor", next(s.minor(), h));
		row(rows, "next major", next(s.major(), h) + (s.major().dueInTicks() < 0 ? "" : " | due in " + Fmt.dur(s.major().dueInTicks(), h)));
		row(rows, "trap", trap);
		row(rows, "sighting", sighting);
		row(rows, "clock", Fmt.f("play %s | day %d | %s", Fmt.hm(s.playTicks(), h), Math.floorDiv(s.dayTicks(), DAY_TICKS),
				s.sessionTicks() < 0 ? "no session" : "session " + Fmt.dur(s.sessionTicks(), h) + (s.sessionEmpty() ? " (empty)" : "")));
		return rows;
	}

	private static String next(DirectorApi.Next next, long hourTicks) {
		if (next.allowedIn() < 0) {
			return next.binding();
		}
		return next.allowedIn() == 0 ? "allowed now" : "in " + Fmt.dur(next.allowedIn(), hourTicks) + " (" + next.binding() + ")";
	}

	private static void row(List<String> rows, String label, String value) {
		rows.add(label + OverlayPayload.SEPARATOR + value);
	}
}
