package com.forzacode.a1016_02.atmosphere;

/**
 * Pure math behind the client effects (fog shape, dusk weight, silence fade), kept free of client classes so the
 * game tests can check it.
 */
public final class Curves {
	public static final long DAY = 24000L;

	private Curves() {
	}

	public static double clamp01(double value) {
		return value < 0.0 ? 0.0 : Math.min(1.0, value);
	}

	public static double smooth(double t) {
		double x = clamp01(t);
		return x * x * (3.0 - 2.0 * x);
	}

	public static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}

	/**
	 * How much of the dusk fog applies at a time of day (0 is sunrise, 12000 sunset): none by day, rising through
	 * sunset to 1 at dusk (12500 to 13800), easing to {@code nightWeight} for the night, gone by sunrise.
	 */
	public static double duskWeight(long timeOfDay, double nightWeight) {
		double t = Math.floorMod(timeOfDay, DAY);
		double night = clamp01(nightWeight);
		if (t < 10500) {
			return 0.0;
		}
		if (t < 12500) {
			return smooth((t - 10500) / 2000.0);
		}
		if (t < 13800) {
			return 1.0;
		}
		if (t < 15500) {
			return lerp(smooth((t - 13800) / 1700.0), 1.0, night);
		}
		if (t < 22000) {
			return night;
		}
		if (t < 23500) {
			return lerp(smooth((t - 22000) / 1500.0), night, 0.0);
		}
		return 0.0;
	}

	/** A fog surge's shape over time: a sharp rise over {@code ramp}, a hold, then an eased fade. 0 to 1. */
	public static double surgeEnvelope(double t, int ramp, int hold, int fade) {
		if (t < 0.0) {
			return 0.0;
		}
		if (t < ramp) {
			double x = t / Math.max(1, ramp);
			return 1.0 - (1.0 - x) * (1.0 - x);
		}
		t -= ramp;
		if (t < hold) {
			return 1.0;
		}
		t -= hold;
		if (t < fade) {
			return 1.0 - smooth(t / Math.max(1, fade));
		}
		return 0.0;
	}

	/** Total length of a surge in ticks. */
	public static long surgeLength(int ramp, int hold, int fade) {
		return (long) Math.max(0, ramp) + Math.max(0, hold) + Math.max(0, fade);
	}

	/**
	 * Fog end distance for a fog amount (0 to 1): geometric between the current distance and {@code minEnd}, so equal
	 * steps in amount feel like equal steps in fog. Never pushes the fog further out than {@code base}.
	 */
	public static double fogEnd(double base, double minEnd, double amount) {
		if (base <= minEnd || amount <= 0.0) {
			return base;
		}
		return base * Math.pow(minEnd / base, clamp01(amount));
	}

	/** Two fog amounts together: each closes in on what the other left. */
	public static double combine(double a, double b) {
		return 1.0 - (1.0 - clamp01(a)) * (1.0 - clamp01(b));
	}

	/**
	 * Volume multiplier during a silence: a quick cut over {@code cut} ticks, silent until {@code silent} ticks,
	 * then back over {@code fade} ticks, slowly at first. {@code lag} (0 to 0.9) delays this category's return inside
	 * the fade so not everything comes back at once.
	 */
	public static double silenceVolume(double t, int cut, int silent, int fade, double lag) {
		if (t < 0.0) {
			return 1.0;
		}
		if (t < cut) {
			return 1.0 - smooth(t / Math.max(1, cut));
		}
		if (t < silent) {
			return 0.0;
		}
		double f = (t - silent) / Math.max(1, fade);
		double l = Math.min(0.9, Math.max(0.0, lag));
		double x = clamp01((f - l) / (1.0 - l));
		return x * x;
	}

	public static long silenceLength(int silent, int fade) {
		return (long) Math.max(0, silent) + Math.max(0, fade);
	}
}
