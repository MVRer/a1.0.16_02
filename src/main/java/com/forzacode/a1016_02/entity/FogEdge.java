package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Where the fog edge is for one player, and the band just inside it where he may stand.
 *
 * <p>The render limit is the smaller of the client's requested view distance and the server's, in blocks; vanilla
 * fogs the last tenth of it. The dusk fog ({@link HerobrineState.Effects#duskFogLevel()}) pulls the limit in. The
 * band also stays well inside the simulation (entity-ticking) distance, so he can always walk off without
 * freezing, and never starts closer than {@code Pacing.sightingMinDistance}. The pulled-in {@link #limit} is an
 * estimate of the dusk fog, used only to place him, never to remove him.
 *
 * @param chunks      effective render distance in chunks
 * @param renderLimit the render limit in blocks, where vanilla fog is complete
 * @param limit       the limit pulled in by the dusk fog (an estimate until core shares the fog curve)
 * @param inner       nearest spawn distance (horizontal)
 * @param outer       farthest spawn distance (horizontal)
 */
public record FogEdge(int chunks, double renderLimit, double limit, double inner, double outer) {
	public static FogEdge of(ServerPlayer player, boolean atMaxDistance) {
		MinecraftServer server = player.level().getServer();
		float duskFog = HerobrineState.get(server).effects().duskFogLevel();
		return compute(player.requestedViewDistance(), server.getPlayerList().getViewDistance(), server.getPlayerList().getSimulationDistance(),
				duskFog, atMaxDistance, ModConfig.pacing().sightingMinDistance, EntityConfig.get());
	}

	/**
	 * @param requestedChunks  the client's view distance (0 or less if unknown)
	 * @param serverChunks     the server's view distance
	 * @param simulationChunks the server's simulation distance (0 or less to ignore)
	 * @param atMaxDistance    a narrow band right at the limit (the Alone stage's "max fog distance")
	 */
	public static FogEdge compute(int requestedChunks, int serverChunks, int simulationChunks, float duskFog, boolean atMaxDistance,
			int minDistance, EntityConfig config) {
		int chunks = requestedChunks > 0 ? Math.min(requestedChunks, serverChunks) : serverChunks;
		chunks = Math.max(chunks, 2);
		double renderLimit = chunks * 16.0;
		double pull = Mth.clamp(config.duskFogPull, 0.0, 0.95) * Mth.clamp(duskFog, 0.0F, 1.0F);
		double limit = renderLimit * (1.0 - pull);
		double outer = Math.min(limit - config.edgeMarginBlocks, config.maxSpawnDistance);
		if (simulationChunks > 0) {
			outer = Math.min(outer, tickingReach(simulationChunks));
		}
		double depth = Math.max(config.fogBandMinBlocks, outer * config.fogBandFraction);
		if (atMaxDistance) {
			depth = Math.max(2.0, depth / 3.0);
		}
		double inner = Math.max(outer - depth, minDistance);
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
