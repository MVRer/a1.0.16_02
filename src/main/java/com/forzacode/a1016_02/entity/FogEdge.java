package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.core.HerobrineState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * How far one player can see, and the band where he may stand (D-035): a share of the fog end the client actually
 * draws, so he reads as a hazy but clear shape, never a speck lost in the fog.
 *
 * <p>The fog end ({@link #limit}) is the one the player's client reports ({@link ReportedFog}: vanilla fog, the dusk
 * fog and fog surges included) while that report is fresh. Until one arrives it is the server's estimate: vanilla's
 * render-distance fog end (the smaller of the client's requested view distance and the server's, in blocks) pulled in
 * by the dusk fog the way atmosphere's client pulls it in, {@code Curves.fogEnd(renderEnd, duskMinFogBlocks,
 * duskFogLevel * duskWeight(time of day))}, surges left out.
 *
 * <p>Every variant but the close one stands {@code normalFractionMin..Max} of the fog end away. The close one stands
 * {@code closeFractionMin..Max} of it, clamped to {@code closeMinDistance..closeMaxDistance} blocks. Either band stays
 * {@code edgeMarginBlocks} inside the fog end and inside the simulation (entity-ticking) distance, so he can always
 * walk off without freezing, and never starts closer than {@code minDistance}. When the fog is so thick that the
 * band has to reach past the margin, he cannot be seen there: {@link #seeable} is false and no sighting starts.
 *
 * @param chunks      effective render distance in chunks
 * @param renderLimit vanilla's render-distance fog end in blocks, where fog is complete without any other fog
 * @param estimate    the server's estimate of the visible fog end
 * @param reported    the client's fresh report, NaN if there is none
 * @param limit       the fog end the band uses: {@code reported} if known, else {@code estimate}
 * @param inner       nearest spawn distance (horizontal)
 * @param outer       farthest spawn distance (horizontal)
 * @param seeable     false if the band reaches past the fog end's margin (fog thicker than the minimum distance)
 */
public record FogEdge(int chunks, double renderLimit, double estimate, double reported, double limit, double inner, double outer, boolean seeable) {
	/** The band for every variant but the close one. */
	public static FogEdge of(ServerPlayer player) {
		return of(player, false);
	}

	/** @param close the close variant's band */
	public static FogEdge of(ServerPlayer player, boolean close) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		AtmosphereConfig atmosphere = AtmosphereConfig.get();
		int requested = player.requestedViewDistance();
		int serverChunks = server.getPlayerList().getViewDistance();
		double reported = ReportedFog.fresh(player, renderLimit(requested, serverChunks));
		return compute(requested, serverChunks, server.getPlayerList().getSimulationDistance(),
				duskAmount(level, HerobrineState.get(server).effects().duskFogLevel(), atmosphere.duskNightWeight), atmosphere.duskMinFogBlocks, reported,
				close, EntityConfig.get());
	}

	/** How much of the dusk fog the client applies right now, 0 to 1 (none where time is fixed). */
	static double duskAmount(ServerLevel level, float duskFogLevel, double nightWeight) {
		if (duskFogLevel <= 0.0F || level.dimensionType().hasFixedTime()) {
			return 0.0;
		}
		return duskFogLevel * Curves.duskWeight(level.getDefaultClockTime(), nightWeight);
	}

	/** The fog end the client sees by the server's estimate: the render-distance fog end, pulled in by the dusk fog. */
	public static double visibleFogEnd(double renderLimit, double duskAmount, double duskMinFogBlocks) {
		return Curves.fogEnd(renderLimit, duskMinFogBlocks, duskAmount);
	}

	/** Effective render distance in chunks: the client's (if known) capped by the server's, at least 2. */
	static int chunks(int requestedChunks, int serverChunks) {
		int chunks = requestedChunks > 0 ? Math.min(requestedChunks, serverChunks) : serverChunks;
		return Math.max(chunks, 2);
	}

	static double renderLimit(int requestedChunks, int serverChunks) {
		return chunks(requestedChunks, serverChunks) * 16.0;
	}

	/**
	 * @param requestedChunks  the client's view distance (0 or less if unknown)
	 * @param serverChunks     the server's view distance
	 * @param simulationChunks the server's simulation distance (0 or less to ignore)
	 * @param duskAmount       dusk fog applied now, 0 to 1 ({@link #duskAmount})
	 * @param duskMinFogBlocks fog end at full dusk fog (atmosphere's config)
	 * @param reported         the client's fresh fog end report ({@link ReportedFog#fresh}), NaN to use the estimate
	 * @param close            the close variant's band
	 */
	public static FogEdge compute(int requestedChunks, int serverChunks, int simulationChunks, double duskAmount, double duskMinFogBlocks,
			double reported, boolean close, EntityConfig config) {
		int chunks = chunks(requestedChunks, serverChunks);
		double renderLimit = chunks * 16.0;
		double estimate = visibleFogEnd(renderLimit, duskAmount, duskMinFogBlocks);
		double limit = Double.isNaN(reported) ? estimate : reported;
		double[] band = band(limit, close, simulationChunks, config);
		double margin = Math.max(0.0, config.edgeMarginBlocks);
		boolean seeable = band[1] <= limit - margin + 1.0E-6;
		return new FogEdge(chunks, renderLimit, estimate, reported, limit, band[0], band[1], seeable);
	}

	/**
	 * The band for a fog end, as {inner, outer}: the variant's share of {@code limit} (the close one clamped to its
	 * blocks), kept inside the fog end's margin, the spawn cap and the ticking range, never closer than
	 * {@code minDistance}, and at least 2 blocks deep.
	 */
	public static double[] band(double limit, boolean close, int simulationChunks, EntityConfig config) {
		double a = fraction(close ? config.closeFractionMin : config.normalFractionMin);
		double b = fraction(close ? config.closeFractionMax : config.normalFractionMax);
		double inner = limit * Math.min(a, b);
		double outer = limit * Math.max(a, b);
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
		outer = Math.min(Math.min(outer, limit - config.edgeMarginBlocks), config.maxSpawnDistance);
		if (simulationChunks > 0) {
			outer = Math.min(outer, tickingReach(simulationChunks));
		}
		inner = Math.max(Math.min(inner, outer - 2.0), config.minDistance());
		outer = Math.max(outer, inner + 2.0);
		return new double[] {inner, outer};
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
