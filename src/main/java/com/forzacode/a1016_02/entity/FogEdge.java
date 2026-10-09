package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * How far one player can see, and the band where he may stand: {@code spawnDistanceFractionMin..Max} (0.45 to 0.70)
 * of the fog end the client actually sees, so he reads as a hazy but clear shape, never a speck lost in the fog.
 *
 * <p>The visible fog end ({@link #limit}) is vanilla's render-distance fog end (the smaller of the client's requested
 * view distance and the server's, in blocks) pulled in by the dusk fog the way atmosphere's client pulls it in:
 * {@code Curves.fogEnd(renderEnd, duskMinFogBlocks, duskFogLevel * duskWeight(time of day))}. Fog surges are short and
 * left out. Until core shares a fog-end helper, this mirrors the client here. The band also stays inside the
 * simulation (entity-ticking) distance, so he can always walk off without freezing, and never starts closer than
 * {@code Pacing.sightingMinDistance}. The close variant has its own band ({@code closeMinDistance..closeMaxDistance}).
 *
 * @param chunks      effective render distance in chunks
 * @param renderLimit vanilla's render-distance fog end in blocks, where fog is complete without dusk fog
 * @param limit       the visible fog end: {@code renderLimit} pulled in by the dusk fog
 * @param inner       nearest spawn distance (horizontal)
 * @param outer       farthest spawn distance (horizontal)
 */
public record FogEdge(int chunks, double renderLimit, double limit, double inner, double outer) {
	/** The band for every variant but the close one. */
	public static FogEdge of(ServerPlayer player) {
		return of(player, false);
	}

	/** @param close the close variant's band (24 to 36 blocks by default, never past the general band's far side) */
	public static FogEdge of(ServerPlayer player, boolean close) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		AtmosphereConfig atmosphere = AtmosphereConfig.get();
		return compute(player.requestedViewDistance(), server.getPlayerList().getViewDistance(), server.getPlayerList().getSimulationDistance(),
				duskAmount(level, HerobrineState.get(server).effects().duskFogLevel(), atmosphere.duskNightWeight), atmosphere.duskMinFogBlocks, close,
				ModConfig.pacing().sightingMinDistance, EntityConfig.get());
	}

	/** How much of the dusk fog the client applies right now, 0 to 1 (none where time is fixed). */
	static double duskAmount(ServerLevel level, float duskFogLevel, double nightWeight) {
		if (duskFogLevel <= 0.0F || level.dimensionType().hasFixedTime()) {
			return 0.0;
		}
		return duskFogLevel * Curves.duskWeight(level.getDefaultClockTime(), nightWeight);
	}

	/** The fog end the client sees: the render-distance fog end, pulled in by the dusk fog. */
	public static double visibleFogEnd(double renderLimit, double duskAmount, double duskMinFogBlocks) {
		return Curves.fogEnd(renderLimit, duskMinFogBlocks, duskAmount);
	}

	/**
	 * @param requestedChunks  the client's view distance (0 or less if unknown)
	 * @param serverChunks     the server's view distance
	 * @param simulationChunks the server's simulation distance (0 or less to ignore)
	 * @param duskAmount       dusk fog applied now, 0 to 1 ({@link #duskAmount})
	 * @param duskMinFogBlocks fog end at full dusk fog (atmosphere's config)
	 * @param close            the close variant's band
	 */
	public static FogEdge compute(int requestedChunks, int serverChunks, int simulationChunks, double duskAmount, double duskMinFogBlocks, boolean close,
			int minDistance, EntityConfig config) {
		int chunks = requestedChunks > 0 ? Math.min(requestedChunks, serverChunks) : serverChunks;
		chunks = Math.max(chunks, 2);
		double renderLimit = chunks * 16.0;
		double limit = visibleFogEnd(renderLimit, duskAmount, duskMinFogBlocks);
		double a = Mth.clamp(config.spawnDistanceFractionMin, 0.05, 1.0);
		double b = Mth.clamp(config.spawnDistanceFractionMax, 0.05, 1.0);
		double inner = limit * Math.min(a, b);
		double outer = limit * Math.max(a, b);
		if (close) {
			inner = config.closeMinDistance;
			outer = Math.min(outer, config.closeMaxDistance);
		}
		outer = Math.min(Math.min(outer, limit - config.edgeMarginBlocks), config.maxSpawnDistance);
		if (simulationChunks > 0) {
			outer = Math.min(outer, tickingReach(simulationChunks));
		}
		inner = Math.max(Math.min(inner, outer - 2.0), minDistance);
		outer = Math.max(outer, inner + 2.0);
		return new FogEdge(chunks, renderLimit, limit, inner, outer);
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
