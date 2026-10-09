package com.forzacode.a1016_02.atmosphere;

import com.forzacode.a1016_02.core.FogLimits;

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

	/** {@link #duskWeight(long, FogLimits.Shape)} with the default timing and this night weight. */
	public static double duskWeight(long timeOfDay, double nightWeight) {
		return duskWeight(timeOfDay, new FogLimits.Shape(FogLimits.Shape.DEFAULT.duskMinFogBlocks(), nightWeight));
	}

	/**
	 * How much of the dusk fog applies at a time of day (0 is sunrise, 12000 sunset), 0 to 1. None by day. From
	 * {@code duskStart} (9000) it eases in so slowly that the first minutes are hardly there ({@link #ease}), reaching
	 * 1 at {@code duskPeak} (13000, deep dusk); it holds to {@code duskHoldUntil}, eases to the night weight by
	 * {@code duskNightFrom}, and from {@code duskFadeFrom} fades out as the rise played backwards, gone at
	 * {@code duskEnd} (before sunrise). Continuous everywhere. Core's {@code FogLimits.duskWeight} is the same curve.
	 */
	public static double duskWeight(long timeOfDay, FogLimits.Shape shape) {
		long t = Math.floorMod(timeOfDay, DAY);
		double night = clamp01(shape.duskNightWeight());
		double p = shape.duskEaseExponent();
		if (t < shape.duskStart() || t >= shape.duskEnd()) {
			return 0.0;
		}
		if (t < shape.duskPeak()) {
			return ease(span(t, shape.duskStart(), shape.duskPeak()), p);
		}
		if (t < shape.duskHoldUntil()) {
			return 1.0;
		}
		if (t < shape.duskNightFrom()) {
			return lerp(ease(1.0 - span(t, shape.duskHoldUntil(), shape.duskNightFrom()), p), night, 1.0);
		}
		if (t < shape.duskFadeFrom()) {
			return night;
		}
		return night * ease(1.0 - span(t, shape.duskFadeFrom(), shape.duskEnd()), p);
	}

	/**
	 * How much of the dusk fog's close-in haze applies at a time of day, 0 to 1 (see {@link #applyDusk}). It builds
	 * with the fog ({@link #duskWeight}) up to the peak, stays whole while the fog eases to the night weight and through
	 * the night (so dusk and night keep their tuned look), and fades with the night fog before sunrise.
	 */
	public static double duskHaze(long timeOfDay, FogLimits.Shape shape) {
		double w = duskWeight(timeOfDay, shape);
		long t = Math.floorMod(timeOfDay, DAY);
		if (t >= shape.duskStart() && t < shape.duskHoldUntil()) {
			return w;
		}
		return clamp01(w / Math.max(0.05, clamp01(shape.duskNightWeight())));
	}

	/**
	 * The dusk fog's ease, 0 to 1 over {@code x} 0 to 1: {@code x^p * (p + 1 - p * x)}. It starts like {@code x^p}
	 * (with {@code p} 3, a fifth of the way in it is at 3%) and lands flat on 1. {@code p} 2 is a smoothstep.
	 */
	public static double ease(double x, double p) {
		double v = clamp01(x);
		return clamp01(Math.pow(v, p) * (p + 1.0 - p * v));
	}

	private static double span(long t, long from, long to) {
		return to > from ? (double) (t - from) / (to - from) : 1.0;
	}

	/**
	 * How much dusk fog applies now (0 to 1): the dusk level (clamped to 0 to 1) times {@link #duskWeight}. The client
	 * fog uses exactly this, and core's {@code FogLimits} does the same math with the installed shape.
	 */
	public static double duskAmount(double duskLevel, long timeOfDay, FogLimits.Shape shape) {
		return clamp01(duskLevel) * duskWeight(timeOfDay, shape);
	}

	/**
	 * The world fog's distances in blocks, as vanilla's {@code FogData} holds them, without client classes. The
	 * environmental fog is spherical and starts at the camera in vanilla's overworld (0 to 1024: a faint haze); the
	 * render-distance fog is the short wall at the render limit; sky and clouds have their own ends. The shader draws
	 * the larger of the environmental and the render-distance fog.
	 */
	public static final class Fog {
		public double environmentalStart;
		public double environmentalEnd;
		public double renderDistanceStart;
		public double renderDistanceEnd;
		public double skyEnd;
		public double cloudEnd;

		public Fog set(double environmentalStart, double environmentalEnd, double renderDistanceStart, double renderDistanceEnd, double skyEnd,
				double cloudEnd) {
			this.environmentalStart = environmentalStart;
			this.environmentalEnd = environmentalEnd;
			this.renderDistanceStart = renderDistanceStart;
			this.renderDistanceEnd = renderDistanceEnd;
			this.skyEnd = skyEnd;
			this.cloudEnd = cloudEnd;
			return this;
		}

		/** Where the fog is complete: the nearer of the two world fog ends. */
		public double end() {
			return Math.min(environmentalEnd, renderDistanceEnd);
		}
	}

	/**
	 * The dusk fog on top of vanilla's world fog. Continuous in every input: with {@code amount} and {@code haze} at 0
	 * it is vanilla's fog exactly, and no distance jumps as they grow.
	 *
	 * <ul>
	 * <li>The nearer of the two world fog ends ({@code base}, vanilla's render limit in the overworld) is pulled in
	 * geometrically ({@link #fogEnd}) to {@code end}; the fog is complete there, exactly where core's {@code FogLimits}
	 * puts it.</li>
	 * <li>Every end beyond {@code base} (vanilla's faint haze out to 1024 blocks, the clouds) closes in with
	 * {@code haze}: its fog per block blends from vanilla's toward the dusk fog's ({@link #pullEnd}). At haze 1 it ends
	 * at {@code end} too, the close-in haze from the camera that dusk and night had before (v0.4); before, it switched
	 * on at full strength the instant any dusk fog applied, which was the pop at sunset.</li>
	 * <li>Starts move from their vanilla share of their end toward {@code heavyStartFraction} as the amount grows,
	 * never further out than vanilla's.</li>
	 * </ul>
	 */
	public static void applyDusk(Fog fog, double amount, double haze, double minEnd, double heavyStartFraction) {
		double a = clamp01(amount);
		double h = clamp01(haze);
		double base = fog.end();
		if (a <= 0.0 && h <= 0.0 || !(base > 1.0)) {
			return;
		}
		double end = fogEnd(base, minEnd, a);
		double heavy = clamp01(heavyStartFraction);
		double environmentalEnd = pullEnd(fog.environmentalEnd, base, end, h);
		double renderDistanceEnd = pullEnd(fog.renderDistanceEnd, base, end, h);
		fog.environmentalStart = pullStart(fog.environmentalStart, fog.environmentalEnd, environmentalEnd, a, heavy);
		fog.renderDistanceStart = pullStart(fog.renderDistanceStart, fog.renderDistanceEnd, renderDistanceEnd, a, heavy);
		fog.environmentalEnd = environmentalEnd;
		fog.renderDistanceEnd = renderDistanceEnd;
		fog.skyEnd = pullEnd(fog.skyEnd, base, end, h);
		fog.cloudEnd = pullEnd(fog.cloudEnd, base, end, h);
	}

	/**
	 * One fog end under the dusk fog. An end at or inside {@code base} goes to at most {@code end}. One beyond it keeps
	 * its own fog per block ({@code 1 / vanilla}), gains what the dusk fog adds at the base ({@code 1/end - 1/base})
	 * and, with {@code haze}, the rest of the way to {@code 1 / end}. The added part fades in only over the first
	 * {@code base} blocks beyond the base, so the result is continuous across the base too. Never past vanilla's end,
	 * never inside {@code end}.
	 */
	static double pullEnd(double vanilla, double base, double end, double haze) {
		if (!(vanilla > base)) {
			return Math.min(vanilla, end);
		}
		double far = clamp01((vanilla - base) / base);
		double added = (1.0 - (1.0 - haze) * far) * (1.0 / end - 1.0 / base) + haze * (1.0 / base - 1.0 / vanilla);
		// Never inside end, not even by rounding: the end that was nearer stays the nearer one.
		return Math.max(end, Math.min(vanilla, 1.0 / (1.0 / vanilla + added)));
	}

	/** One fog start under the dusk fog: its share of the (new) end moves from vanilla's toward {@code heavy} with the amount. */
	private static double pullStart(double vanilla, double vanillaEnd, double end, double amount, double heavy) {
		double share = vanillaEnd > 0.0 ? clamp01(vanilla / vanillaEnd) : 0.0;
		return Math.min(vanilla, end * lerp(amount, share, heavy));
	}

	/**
	 * A fog surge on top of the (dusk) fog, as tuned in v0.4 (D-022): from the first noticeable strength the fog closes
	 * in from the camera to {@link #fogEnd} toward {@code minEnd}, its start moving toward {@code heavyStartFraction}.
	 * With the dusk fog's haze full this is exactly the v0.4 dusk-and-surge fog.
	 */
	public static void applySurge(Fog fog, double amount, double minEnd, double heavyStartFraction) {
		if (amount < 1.0E-3) {
			return;
		}
		boolean renderLimited = fog.renderDistanceEnd <= fog.environmentalEnd;
		double base = renderLimited ? fog.renderDistanceEnd : fog.environmentalEnd;
		double baseStart = renderLimited ? fog.renderDistanceStart : fog.environmentalStart;
		if (base <= 1.0) {
			return;
		}
		double end = fogEnd(base, minEnd, amount);
		double start = end * lerp(clamp01(amount), clamp01(baseStart / base), clamp01(heavyStartFraction));
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
		fog.environmentalStart = Math.min(fog.environmentalStart, start);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, end);
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, start);
		fog.skyEnd = Math.min(fog.skyEnd, end);
		fog.cloudEnd = Math.min(fog.cloudEnd, end);
	}

	/**
	 * Dead mountain quiet: one tick of the 0 (normal) to 1 (silent) state, rising over {@code fadeOutTicks} while
	 * inside and falling over {@code restoreTicks} after leaving.
	 */
	public static double quietStep(double quiet, boolean inside, int fadeOutTicks, int restoreTicks) {
		double step = inside ? 1.0 / Math.max(1, fadeOutTicks) : -1.0 / Math.max(1, restoreTicks);
		return clamp01(quiet + step);
	}

	/** Volume multiplier for a quiet state: eased, so the sound slips away and comes back without a step. */
	public static double quietVolume(double quiet) {
		return 1.0 - smooth(quiet);
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
