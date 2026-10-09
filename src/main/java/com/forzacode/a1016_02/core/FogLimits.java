package com.forzacode.a1016_02.core;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Where the world fog ends for one player, computed the same way on both sides so the server (where he may stand)
 * and the client (what the fog hides) agree. Pure math plus one server-side convenience; no client classes.
 *
 * <p>The un-pulled render limit is vanilla's: the effective render distance (the smaller of the client's and the
 * server's view distance, at least 2 chunks) times 16; vanilla fog ends exactly there in the overworld. The dusk fog
 * ({@code HerobrineState.Effects.duskFogLevel}) pulls it in with atmosphere's client curve: weighted by the time of
 * day ({@link #duskWeight}: none by day, easing in slowly from the shape's start to full at its peak, easing to
 * {@code duskNightWeight} for the night, faded before sunrise), then geometric toward {@code duskMinFogBlocks}
 * ({@link #fogEnd}). Fog surges (short, per player) and the client's easing between dusk levels (at most
 * {@code duskLevelChangeSeconds} after a change; the client's own fog report covers it) are not included. Fixed-time
 * dimensions get no dusk fog. Atmosphere owns the shape: it installs its config with {@link #installShape}; until then
 * {@link Shape#DEFAULT} matches atmosphere's defaults.
 */
public final class FogLimits {
	/**
	 * The dusk fog shape (atmosphere's {@code duskMinFogBlocks}, {@code duskNightWeight} and the {@code duskFog...}
	 * timing). Times are times of day (0 is sunrise, 12000 sunset): the fog starts building at {@code duskStart}, is
	 * full from {@code duskPeak} to {@code duskHoldUntil}, eases to the night weight by {@code duskNightFrom}, starts
	 * fading at {@code duskFadeFrom} and is gone at {@code duskEnd}. {@code duskEaseExponent} shapes every ease
	 * ({@link #ease}). The constructor puts the times in that order inside one day and the exponent in 1 to 8.
	 */
	public record Shape(double duskMinFogBlocks, double duskNightWeight, long duskStart, long duskPeak, long duskHoldUntil, long duskNightFrom,
			long duskFadeFrom, long duskEnd, double duskEaseExponent) {
		public static final Shape DEFAULT = new Shape(24.0, 0.55);

		public Shape {
			duskStart = Math.clamp(duskStart, 0L, DAY);
			duskPeak = Math.clamp(duskPeak, duskStart, DAY);
			duskHoldUntil = Math.clamp(duskHoldUntil, duskPeak, DAY);
			duskNightFrom = Math.clamp(duskNightFrom, duskHoldUntil, DAY);
			duskFadeFrom = Math.clamp(duskFadeFrom, duskNightFrom, DAY);
			duskEnd = Math.clamp(duskEnd, duskFadeFrom, DAY);
			duskEaseExponent = Double.isNaN(duskEaseExponent) ? 3.0 : Math.clamp(duskEaseExponent, 1.0, 8.0);
		}

		/** This fog end and night weight with the default timing (from 9000, full 13000 to 13800, night by 16000, fading 19500 to 23500, exponent 3). */
		public Shape(double duskMinFogBlocks, double duskNightWeight) {
			this(duskMinFogBlocks, duskNightWeight, 9000L, 13000L, 13800L, 16000L, 19500L, 23500L, 3.0);
		}
	}

	/**
	 * @param chunks      effective render distance in chunks
	 * @param renderLimit where vanilla fog is complete without any dusk fog, in blocks
	 * @param fogEnd      where the fog is complete now, with the dusk fog for this time of day, in blocks
	 */
	public record Result(int chunks, double renderLimit, double fogEnd) {
	}

	public static final long DAY = 24000L;

	private static volatile Supplier<Shape> shape = () -> Shape.DEFAULT;

	private FogLimits() {
	}

	/** Atmosphere: where the current shape comes from (its config section). Any thread. */
	public static void installShape(Supplier<Shape> source) {
		shape = Objects.requireNonNull(source);
	}

	/** The installed shape, or {@link Shape#DEFAULT}. */
	public static Shape shape() {
		Shape current = shape.get();
		return current != null ? current : Shape.DEFAULT;
	}

	/** Vanilla's effective render distance: {@code min(client, server)} chunks, the server's if the client's is unknown (0 or less), at least 2. */
	public static int effectiveChunks(int clientChunks, int serverChunks) {
		int chunks = clientChunks > 0 ? Math.min(clientChunks, serverChunks) : serverChunks;
		return Math.max(chunks, 2);
	}

	/** The fog for these view distances, dusk fog level (0 to 1) and day time, with the installed shape. */
	public static Result of(int clientChunks, int serverChunks, float duskFogLevel, long dayTime) {
		return of(clientChunks, serverChunks, duskFogLevel, dayTime, shape());
	}

	/** The fog for these view distances, dusk fog level (0 to 1), day time (0 is sunrise) and shape. */
	public static Result of(int clientChunks, int serverChunks, float duskFogLevel, long dayTime, Shape shape) {
		int chunks = effectiveChunks(clientChunks, serverChunks);
		double renderLimit = chunks * 16.0;
		double amount = clamp01(duskFogLevel) * duskWeight(dayTime, shape);
		return new Result(chunks, renderLimit, fogEnd(renderLimit, shape.duskMinFogBlocks(), amount));
	}

	/**
	 * The fog one player sees now: their requested view distance, the server's, the world's dusk fog level and their
	 * level's time of day (no dusk fog where time is fixed). Server thread.
	 */
	public static Result of(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Level level = player.level();
		boolean fixed = level.dimensionType().hasFixedTime();
		float dusk = fixed ? 0.0F : HerobrineState.get(server).effects().duskFogLevel();
		return of(player.requestedViewDistance(), server.getPlayerList().getViewDistance(), dusk, level.getDefaultClockTime());
	}

	/** {@link #duskWeight(long, Shape)} with the default timing and this night weight. */
	public static double duskWeight(long timeOfDay, double nightWeight) {
		return duskWeight(timeOfDay, new Shape(Shape.DEFAULT.duskMinFogBlocks(), nightWeight));
	}

	/**
	 * How much of the dusk fog applies at a time of day (0 is sunrise, 12000 sunset), 0 to 1: none by day; from
	 * {@code duskStart} it eases in ({@link #ease}: barely anything for the first minutes) to 1 at {@code duskPeak};
	 * holds to {@code duskHoldUntil}; eases to the night weight by {@code duskNightFrom}; from {@code duskFadeFrom} it
	 * fades, the rise played backwards, to nothing at {@code duskEnd}. Continuous everywhere. The same curve as
	 * atmosphere's client fog ({@code Curves.duskWeight}).
	 */
	public static double duskWeight(long timeOfDay, Shape shape) {
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
	 * Fog end distance for a fog amount (0 to 1): geometric between {@code base} and {@code minEnd}, never further
	 * out than {@code base}. The same curve as atmosphere's client fog.
	 */
	public static double fogEnd(double base, double minEnd, double amount) {
		if (base <= minEnd || amount <= 0.0) {
			return base;
		}
		return base * Math.pow(minEnd / base, clamp01(amount));
	}

	private static double clamp01(double value) {
		return value < 0.0 ? 0.0 : Math.min(1.0, value);
	}

	private static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}
}
