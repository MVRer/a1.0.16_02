package com.forzacode.a1016_02.director;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Pacing;

/**
 * The two {@code HerobrineState} flags other workstreams (the endings) use to steer the director. The director reads
 * them on every decision tick, through {@link DirectorImpl#rules}.
 * <ul>
 * <li>{@code director:silence_until_day=<n>}: while the in-game day ({@code GameClock.day}) is below {@code n}, no
 * card fires at all, real or fake, any tier. {@code n = -1}, or the flag {@code director:silence_forever}, silences
 * the director for good (Ending C, and after Ending D). Removing the flag restores normal pacing. Debug forced fires
 * ({@code /a1016 fire}) skip this like every other gate.</li>
 * <li>{@code director:pace_multiplier=<x>}, 0.25 to 4 (1 when absent): the minor gap, the major gap and the
 * scheduled gap between majors are divided by {@code x}, and tension decays {@code x} times as fast (Ending B:
 * "accidents come closer together"). The join grace and "no major on day 0" are never scaled.</li>
 * </ul>
 * Bad values (not a number, out of range) are ignored and logged once per flag. If a flag is set more than once,
 * the strongest wins: the latest silence, the largest multiplier.
 */
public final class DirectorFlags {
	public static final String SILENCE_UNTIL_DAY = "director:silence_until_day=";
	public static final String SILENCE_FOREVER = "director:silence_forever";
	public static final String PACE_MULTIPLIER = "director:pace_multiplier=";
	public static final double PACE_MIN = 0.25;
	public static final double PACE_MAX = 4;
	/** {@link #silenceUntilDay} when no silence flag is set. */
	public static final long NO_SILENCE = Long.MIN_VALUE;
	/** {@link #silenceUntilDay} for a permanent silence. */
	public static final long FOREVER = Long.MAX_VALUE;

	private static final Set<String> REPORTED = new HashSet<>();

	/**
	 * @param silenceUntilDay no fire while the in-game day is below this; {@link #NO_SILENCE} or {@link #FOREVER}
	 * @param paceMultiplier  1 when absent
	 */
	public record Values(long silenceUntilDay, double paceMultiplier) {
		public static final Values NONE = new Values(NO_SILENCE, 1);

		public boolean silenced(long day) {
			return day < silenceUntilDay;
		}

		/** One line for the debug blocks. */
		public String describe() {
			String silence = silenceUntilDay == NO_SILENCE ? "off" : silenceUntilDay == FOREVER ? "forever" : "until day " + silenceUntilDay;
			return String.format(Locale.ROOT, "silence %s, pace x%.2f", silence, paceMultiplier);
		}
	}

	private DirectorFlags() {
	}

	/** Reads the director's flags out of the shared flag set. Never throws. */
	public static Values parse(Collection<String> flags) {
		long silence = NO_SILENCE;
		double pace = Double.NaN;
		for (String flag : flags) {
			if (flag == null) {
				continue;
			}
			if (flag.equals(SILENCE_FOREVER)) {
				silence = FOREVER;
			} else if (flag.startsWith(SILENCE_UNTIL_DAY)) {
				long day;
				try {
					day = Long.parseLong(flag.substring(SILENCE_UNTIL_DAY.length()).trim());
				} catch (NumberFormatException e) {
					bad(flag, "not a whole day number");
					continue;
				}
				if (day == -1 || day >= Long.MAX_VALUE / DirectorBrain.DAY_TICKS) {
					silence = FOREVER;
				} else if (day < 0) {
					bad(flag, "a day below -1");
				} else {
					silence = Math.max(silence, day);
				}
			} else if (flag.startsWith(PACE_MULTIPLIER)) {
				double x;
				try {
					x = Double.parseDouble(flag.substring(PACE_MULTIPLIER.length()).trim());
				} catch (NumberFormatException e) {
					bad(flag, "not a number");
					continue;
				}
				if (!(x >= PACE_MIN && x <= PACE_MAX)) {
					bad(flag, String.format(Locale.ROOT, "outside %.2f to %.0f", PACE_MIN, PACE_MAX));
				} else {
					pace = Double.isNaN(pace) ? x : Math.max(pace, x);
				}
			}
		}
		return new Values(silence, Double.isNaN(pace) ? 1 : pace);
	}

	/**
	 * Applies the flags to freshly built rules: the silence, and the pace multiplier on the minor gap, the major gap,
	 * the scheduled major gap and tension decay. Join grace and the first-day rule stay as they are.
	 */
	public static DirectorRules apply(DirectorRules rules, Values values) {
		rules.silenceUntilDay = values.silenceUntilDay();
		double x = values.paceMultiplier();
		rules.paceMultiplier = x;
		if (x != 1) {
			rules.minorGap = Math.round(rules.minorGap / x);
			rules.majorGap = Math.round(rules.majorGap / x);
			rules.majorEvery = new Pacing.TickRange(Math.max(1, Math.round(rules.majorEvery.min() / x)),
					Math.max(1, Math.round(rules.majorEvery.max() / x)));
			rules.tensionDecayPerTick *= x;
		}
		return rules;
	}

	private static void bad(String flag, String why) {
		synchronized (REPORTED) {
			if (REPORTED.add(flag)) {
				A1016_02.LOGGER.warn("[a1016] director: ignoring flag '{}' ({})", flag, why);
			}
		}
	}
}
