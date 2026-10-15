package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.director.DirectorApi;
import com.forzacode.a1016_02.director.DirectorMemory;

/**
 * The overlay's rows from a director snapshot (the viewing player's own pacing and tension, plus the shared stage and
 * attention), the viewer's armed trap with the others' count, the sighting phase, the world's fog spell and each
 * participant's tunnel ladder rung. Pure text, {@code Locale.ROOT}.
 */
final class OverlayText {
	private static final long DAY_TICKS = 24000L;

	private OverlayText() {
	}

	/**
	 * @param trap     the viewer's own armed trap ("none" if they have none), then how many other players have one armed
	 * @param sighting the sighting phase ("none" if no figure is out)
	 * @param fog      the world's fog spell ("none" if there is none; {@code DebugOverlay.fog})
	 * @param ladder   each participant's tunnel ladder rung ({@code DebugOverlay.ladder})
	 */
	static List<String> rows(DirectorApi.Snapshot s, String trap, String sighting, String fog, String ladder) {
		long h = s.hourTicks();
		List<String> rows = new ArrayList<>();
		row(rows, "you", Fmt.f("%s%s | %d taking part", s.player(), s.participating() ? "" : " (not taking part)", s.participants()));
		row(rows, "stage", Fmt.f("%s | tempo %s x%.2f (shared)", s.stage(), s.tempo(), s.paceFactor()));
		row(rows, "attention", Fmt.f("%.1f (shared) | your tension %.1f/%.0f | pity +%.0f%%", s.attention(), s.tension(), s.tensionThreshold(),
				s.pity() * 100));
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
				: Fmt.f("%s %s%s%s%s, %s ago", Fmt.lower(last.tier()), last.cardId(), last.fake() ? " (fake)" : " (real)", last.forced() ? " forced" : "",
						last.felt() ? " felt, someone else's" : "", Fmt.dur(s.lastFireAgo(), h)));
		row(rows, "next minor", next(s.minor(), h));
		row(rows, "next major", next(s.major(), h) + (s.major().dueInTicks() < 0 ? "" : " | due in " + Fmt.dur(s.major().dueInTicks(), h)));
		row(rows, "trap", trap);
		row(rows, "sighting", sighting);
		row(rows, "fog spell", fog);
		row(rows, "ladder", ladder);
		row(rows, "clock", Fmt.f("play %s (world %s) | day %d | %s", Fmt.hm(s.playTicks(), h), Fmt.hm(s.worldPlayTicks(), h),
				Math.floorDiv(s.dayTicks(), DAY_TICKS), s.sessionTicks() < 0 ? "no session" : "session " + Fmt.dur(s.sessionTicks(), h)
						+ (s.sessionEmpty() ? " (empty)" : "")));
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
