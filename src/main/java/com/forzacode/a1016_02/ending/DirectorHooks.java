package com.forzacode.a1016_02.ending;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.director.DirectorFlags;

/**
 * Writes the director's hooks as {@link HerobrineState} flags (the director reads them every decision tick, see
 * {@link DirectorFlags}): {@code director:silence_until_day=<n>} (no events while the in-game day is below
 * {@code n}), {@code director:silence_forever} (for good) and {@code director:pace_multiplier=<x>} (above 1: events
 * and accidents closer together, never past 4b's hard floors, D-045). The ending keeps at most one silence flag and
 * one pace flag set.
 */
public final class DirectorHooks {
	/** {@link #silence} for a silence for good. */
	public static final long FOREVER = -1L;
	/** Read only: the director sets it while OBEYED_AFTER_STOP has fired and no telling came since (Ending A's rule). */
	public static final String OBEYED_AFTER_STOP = DirectorFlags.OBEYED_AFTER_STOP;

	private DirectorHooks() {
	}

	/** No director events until this in-game day ({@link #FOREVER}: for good). */
	public static void silenceUntil(HerobrineState state, long day) {
		setSilence(state, day == FOREVER ? DirectorFlags.SILENCE_FOREVER : DirectorFlags.SILENCE_UNTIL_DAY + day);
	}

	/** {@code director:silence_forever}. */
	public static void silenceForever(HerobrineState state) {
		setSilence(state, DirectorFlags.SILENCE_FOREVER);
	}

	public static void clearSilence(HerobrineState state) {
		clear(state, DirectorFlags.SILENCE_UNTIL_DAY);
		state.setFlag(DirectorFlags.SILENCE_FOREVER, false);
	}

	/** The silence as the director reads it: the day it ends, {@link #FOREVER}, or empty if none. */
	public static Optional<Long> silence(HerobrineState state) {
		long day = DirectorFlags.parse(state.flags()).silenceUntilDay();
		if (day == DirectorFlags.NO_SILENCE) {
			return Optional.empty();
		}
		return Optional.of(day == DirectorFlags.FOREVER ? FOREVER : day);
	}

	public static void pace(HerobrineState state, double multiplier) {
		String flag = DirectorFlags.PACE_MULTIPLIER + String.format(Locale.ROOT, "%.2f", multiplier);
		if (state.hasFlag(flag) && count(state, DirectorFlags.PACE_MULTIPLIER) == 1) {
			return;
		}
		clearPace(state);
		state.setFlag(flag, true);
	}

	public static void clearPace(HerobrineState state) {
		clear(state, DirectorFlags.PACE_MULTIPLIER);
	}

	/** The pace multiplier set now (as the director reads it), or empty if none is set. */
	public static Optional<Double> pace(HerobrineState state) {
		if (count(state, DirectorFlags.PACE_MULTIPLIER) == 0) {
			return Optional.empty();
		}
		return Optional.of(DirectorFlags.parse(state.flags()).paceMultiplier());
	}

	private static void setSilence(HerobrineState state, String flag) {
		long silences = count(state, DirectorFlags.SILENCE_UNTIL_DAY) + (state.hasFlag(DirectorFlags.SILENCE_FOREVER) ? 1 : 0);
		if (state.hasFlag(flag) && silences == 1) {
			return;
		}
		clearSilence(state);
		state.setFlag(flag, true);
	}

	private static long count(HerobrineState state, String prefix) {
		return state.flags().stream().filter(f -> f.startsWith(prefix)).count();
	}

	private static void clear(HerobrineState state, String prefix) {
		for (String flag : List.copyOf(state.flags())) {
			if (flag.startsWith(prefix)) {
				state.setFlag(flag, false);
			}
		}
	}
}
