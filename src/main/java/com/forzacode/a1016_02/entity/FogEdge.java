package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.core.FogLimits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * How far one player can see, and the band where he may stand (D-035): a share of the fog end the client actually
 * draws, so he reads as a hazy but clear shape, never a speck lost in the fog.
 *
 * <p>The fog end ({@link #limit}) is the one the player's client reports ({@link ReportedFog}: vanilla fog, the dusk
 * fog and fog surges included) while that report is fresh. Until one arrives it is core's estimate,
 * {@link FogLimits#of(ServerPlayer)}: the render-distance fog end pulled in by the dusk fog and the world's fog spell
 * (D-071: a fog night, its morning, a fog day, on the overworld clock), surges left out. So the band follows a fog
 * night or a fog day even without a fresh client report.
 *
 * <p>Each variant takes its {@link Kind}'s band. Most stand {@code normalFractionMin..Max} of the fog end away. The close
 * one stands {@code closeFractionMin..Max} of it, clamped to {@code closeMinDistance..closeMaxDistance} blocks. Between
 * trunks stands {@code trunkMinDistance..trunkMaxDistance} blocks away (18 to 40, as of the trunk fix), whatever the fog
 * end: a share of a clear day's fog end put him 140 to 190 blocks out, where half a figure behind a tree is a few
 * pixels and other trees hide him. Every band stays {@code edgeMarginBlocks} inside the fog end and inside the
 * simulation (entity-ticking) distance, so he can always walk off without freezing, and never starts closer than
 * {@code minDistance}. When the fog is so thick that the band has to reach past the margin, he cannot be seen there:
 * {@link #seeable} is false and no sighting starts.
 *
 * @param chunks      effective render distance in chunks
 * @param renderLimit vanilla's render-distance fog end in blocks, where fog is complete without any other fog
 * @param estimate    core's estimate of the visible fog end ({@link FogLimits.Result#fogEnd})
 * @param reported    the client's fresh report, NaN if there is none
 * @param limit       the fog end the band uses: {@code reported} if known, else {@code estimate}
 * @param inner       nearest spawn distance (horizontal)
 * @param outer       farthest spawn distance (horizontal)
 * @param seeable     false if the band reaches past the fog end's margin (fog thicker than the minimum distance)
 */
public record FogEdge(int chunks, double renderLimit, double estimate, double reported, double limit, double inner, double outer, boolean seeable) {
	/** Which band a variant stands in. */
	public enum Kind {
		/** {@code normalFractionMin..Max} of the fog end: every variant but the two below. */
		NORMAL,
		/** The close one: {@code closeFractionMin..Max} of the fog end, clamped to {@code closeMinDistance..closeMaxDistance} blocks. */
		CLOSE,
		/** Between trunks: {@code trunkMinDistance..trunkMaxDistance} blocks, whatever the fog end (inside its margin). */
		TRUNK;

		public static Kind of(Variant variant) {
			return switch (variant) {
				case CLOSE -> CLOSE;
				case BETWEEN_TRUNKS -> TRUNK;
				default -> NORMAL;
			};
		}
	}

	/** Default trunk band in blocks, for a file that holds NaN there. */
	private static final double TRUNK_MIN_DEFAULT = 18.0;
	private static final double TRUNK_MAX_DEFAULT = 40.0;

	/** The band for every variant but the close one and between trunks. */
	public static FogEdge of(ServerPlayer player) {
		return of(player, Kind.NORMAL);
	}

	/** @param close the close variant's band, else the normal one */
	public static FogEdge of(ServerPlayer player, boolean close) {
		return of(player, close ? Kind.CLOSE : Kind.NORMAL);
	}

	/** The variant's own band ({@link Kind#of}). */
	public static FogEdge of(ServerPlayer player, Variant variant) {
		return of(player, Kind.of(variant));
	}

	public static FogEdge of(ServerPlayer player, Kind kind) {
		FogLimits.Result fog = FogLimits.of(player);
		int simulation = player.level().getServer().getPlayerList().getSimulationDistance();
		return compute(fog, simulation, ReportedFog.fresh(player, fog.renderLimit()), kind, EntityConfig.get());
	}

	/** {@link #compute(FogLimits.Result, int, double, Kind, EntityConfig)} for the close band or the normal one. */
	public static FogEdge compute(FogLimits.Result fog, int simulationChunks, double reported, boolean close, EntityConfig config) {
		return compute(fog, simulationChunks, reported, close ? Kind.CLOSE : Kind.NORMAL, config);
	}

	/**
	 * @param fog              core's fog for the player (render limit and estimated fog end)
	 * @param simulationChunks the server's simulation distance (0 or less to ignore)
	 * @param reported         the client's fresh fog end report ({@link ReportedFog#fresh}), NaN to use the estimate
	 * @param kind             which band
	 */
	public static FogEdge compute(FogLimits.Result fog, int simulationChunks, double reported, Kind kind, EntityConfig config) {
		double limit = Double.isNaN(reported) ? fog.fogEnd() : reported;
		double[] band = band(limit, kind, simulationChunks, config);
		double margin = Math.max(0.0, config.edgeMarginBlocks);
		boolean seeable = band[1] <= limit - margin + 1.0E-6;
		return new FogEdge(fog.chunks(), fog.renderLimit(), fog.fogEnd(), reported, limit, band[0], band[1], seeable);
	}

	/** {@link #band(double, Kind, int, EntityConfig)} for the close band or the normal one. */
	public static double[] band(double limit, boolean close, int simulationChunks, EntityConfig config) {
		return band(limit, close ? Kind.CLOSE : Kind.NORMAL, simulationChunks, config);
	}

	/**
	 * The band for a fog end, as {inner, outer}: the kind's share of {@code limit} (the close one clamped to its
	 * blocks) or, between trunks, its blocks; kept inside the fog end's margin, the spawn cap and the ticking range,
	 * never closer than {@code minDistance}, and at least 2 blocks deep.
	 */
	public static double[] band(double limit, Kind kind, int simulationChunks, EntityConfig config) {
		double inner;
		double outer;
		if (kind == Kind.TRUNK) {
			double a = blocks(config.trunkMinDistance, TRUNK_MIN_DEFAULT);
			double b = blocks(config.trunkMaxDistance, TRUNK_MAX_DEFAULT);
			inner = Math.min(a, b);
			outer = Math.max(a, b);
		} else {
			boolean close = kind == Kind.CLOSE;
			double a = fraction(close ? config.closeFractionMin : config.normalFractionMin);
			double b = fraction(close ? config.closeFractionMax : config.normalFractionMax);
			inner = limit * Math.min(a, b);
			outer = limit * Math.max(a, b);
			if (close) {
				double lo = Math.min(config.closeMinDistance, config.closeMaxDistance);
				double hi = Math.max(config.closeMinDistance, config.closeMaxDistance);
				inner = Mth.clamp(inner, lo, hi);
				outer = Mth.clamp(outer, lo, hi);
				if (outer - inner < 2.0) {
					// Both ends hit the same side of the clamp: keep 2 blocks of depth inside it if it has room.
					outer = Math.min(hi, inner + 2.0);
					inner = Math.max(lo, Math.min(inner, outer - 2.0));
				}
			}
		}
		outer = Math.min(Math.min(outer, limit - config.edgeMarginBlocks), config.maxSpawnDistance);
		if (simulationChunks > 0) {
			outer = Math.min(outer, tickingReach(simulationChunks));
		}
		inner = Math.max(Math.min(inner, outer - 2.0), config.minDistance());
		outer = Math.max(outer, inner + 2.0);
		return new double[] {inner, outer};
	}

	/** A distance in blocks from the file: {@code fallback} for NaN, never negative. */
	private static double blocks(double value, double fallback) {
		return Double.isNaN(value) ? fallback : Math.max(0.0, value);
	}

	private static double fraction(double value) {
		return Double.isNaN(value) ? 0.5 : Mth.clamp(value, 0.05, 1.0);
	}

	/** True if the band uses the client's report rather than the server's estimate. */
	public boolean fromClient() {
		return !Double.isNaN(reported);
	}

	/**
	 * Farthest spawn distance that stays inside the entity-ticking range with room to spare: the range is at least
	 * {@code (simulation - 1)} chunks around the player's chunk, and the spot needs {@link HimEntity#SPAWN_TICK_MARGIN}
	 * more blocks of it plus half a chunk of slack.
	 */
	public static double tickingReach(int simulationChunks) {
		return (simulationChunks - 1) * 16.0 - HimEntity.SPAWN_TICK_MARGIN - 8.0;
	}
}
