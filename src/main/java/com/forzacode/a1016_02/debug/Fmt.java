package com.forzacode.a1016_02.debug;

import java.util.Locale;

/** Number and duration text for debug output. Always {@link Locale#ROOT} (D-031): "0.5", never "0,5". */
final class Fmt {
	private Fmt() {
	}

	static String f(String format, Object... args) {
		return String.format(Locale.ROOT, format, args);
	}

	/** Play ticks as real time, "2h05m". {@code hourTicks} is one real hour in ticks (smaller in devFastMode). */
	static String hm(long ticks, long hourTicks) {
		long minutes = Math.round(ticks * 60.0 / Math.max(1, hourTicks));
		return f("%dh%02dm", minutes / 60, minutes % 60);
	}

	/** Play ticks as a short real-time countdown: "40s", "12m05s", "1h20m". */
	static String dur(long ticks, long hourTicks) {
		long seconds = Math.round(Math.max(0, ticks) * 3600.0 / Math.max(1, hourTicks));
		if (seconds < 60) {
			return seconds + "s";
		}
		if (seconds < 3600) {
			return f("%dm%02ds", seconds / 60, seconds % 60);
		}
		return f("%dh%02dm", seconds / 3600, seconds / 60 % 60);
	}

	/** Play ticks as real minutes. */
	static long minutes(long ticks, long hourTicks) {
		return Math.round(ticks * 60.0 / Math.max(1, hourTicks));
	}

	static String lower(Enum<?> e) {
		return e.name().toLowerCase(Locale.ROOT);
	}
}
