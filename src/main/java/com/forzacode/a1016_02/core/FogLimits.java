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
 * day ({@link #duskWeight}, none by day, full at dusk, {@code duskNightWeight} at night), then geometric toward
 * {@code duskMinFogBlocks} ({@link #fogEnd}). Fog surges (short, per player) are not included. Fixed-time dimensions
 * get no dusk fog. Atmosphere owns the shape: it installs its config with {@link #installShape}; until then
 * {@link Shape#DEFAULT} matches atmosphere's defaults.
 */
public final class FogLimits {
	/** The dusk fog shape (atmosphere's {@code duskMinFogBlocks} and {@code duskNightWeight}). */
	public record Shape(double duskMinFogBlocks, double duskNightWeight) {
		public static final Shape DEFAULT = new Shape(24.0, 0.55);
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
		double amount = clamp01(duskFogLevel) * duskWeight(dayTime, shape.duskNightWeight());
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

	/**
	 * How much of the dusk fog applies at a time of day (0 is sunrise, 12000 sunset): none by day, rising through
	 * sunset to 1 at dusk (12500 to 13800), easing to {@code nightWeight} for the night, gone by sunrise. The same
	 * curve as atmosphere's client fog.
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

	private static double smooth(double t) {
		double x = clamp01(t);
		return x * x * (3.0 - 2.0 * x);
	}

	private static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}
}
