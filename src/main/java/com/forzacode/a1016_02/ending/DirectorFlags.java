package com.forzacode.a1016_02.ending;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.HerobrineState;

/**
 * The director's hooks, as {@link HerobrineState} flags: {@code director:silence_until_day=<n>} (no events until
 * in-game day {@code n}; {@code -1} means forever) and {@code director:pace_multiplier=<x>} (above 1: events and
 * accidents closer together). The ending sets them; the director reads them. At most one of each is ever set.
 */
public final class DirectorFlags {
	public static final String SILENCE_PREFIX = "director:silence_until_day=";
	public static final String PACE_PREFIX = "director:pace_multiplier=";
	/** {@code silence_until_day} value meaning "forever". */
	public static final long FOREVER = -1L;

	private DirectorFlags() {
	}

	/** No director events until this in-game day ({@link #FOREVER} for good). */
	public static void silenceUntil(HerobrineState state, long day) {
		set(state, SILENCE_PREFIX, Long.toString(day));
	}

	public static void silenceForever(HerobrineState state) {
		silenceUntil(state, FOREVER);
	}

	public static void clearSilence(HerobrineState state) {
		clear(state, SILENCE_PREFIX);
	}

	/** The silence day set now, if any ({@link #FOREVER} for good). */
	public static Optional<Long> silence(HerobrineState state) {
		return value(state, SILENCE_PREFIX).flatMap(v -> {
			try {
				return Optional.of(Long.parseLong(v));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		});
	}

	public static void pace(HerobrineState state, double multiplier) {
		set(state, PACE_PREFIX, String.format(Locale.ROOT, "%.2f", multiplier));
	}

	public static void clearPace(HerobrineState state) {
		clear(state, PACE_PREFIX);
	}

	/** The pace multiplier set now, if any. */
	public static Optional<Double> pace(HerobrineState state) {
		return value(state, PACE_PREFIX).flatMap(v -> {
			try {
				return Optional.of(Double.parseDouble(v));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		});
	}

	private static void set(HerobrineState state, String prefix, String value) {
		String flag = prefix + value;
		if (state.hasFlag(flag)) {
			return;
		}
		clear(state, prefix);
		state.setFlag(flag, true);
	}

	private static void clear(HerobrineState state, String prefix) {
		for (String flag : List.copyOf(state.flags())) {
			if (flag.startsWith(prefix)) {
				state.setFlag(flag, false);
			}
		}
	}

	private static Optional<String> value(HerobrineState state, String prefix) {
		return state.flags().stream().filter(f -> f.startsWith(prefix)).findFirst().map(f -> f.substring(prefix.length()));
	}
}
