package com.forzacode.a1016_02.core;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Marked deaths: the cross, the cause on the list, and {@link HerobrineEvents#MARKED_DEATH}. The accident
 * workstream installs the real one.
 */
public interface DeathMarker {
	/** Marks a death: records a {@link MarkedDeath} in {@link HerobrineState} and fires the event. */
	void mark(ServerPlayer player, String cause, BlockPos pos);

	/** How many deaths are marked in this world. */
	int count(MinecraftServer server);

	/**
	 * The bottom of the post of the newest cross that stands for a marked death (the cross is built once the spot is
	 * out of view, so it can come a while after the death), or empty if none stands yet. Default: none.
	 */
	default Optional<GlobalPos> lastCrossPos(MinecraftServer server) {
		return Optional.empty();
	}

	/** Default: marks nothing, counts what the state holds. */
	final class Stub implements DeathMarker {
		@Override
		public void mark(ServerPlayer player, String cause, BlockPos pos) {
		}

		@Override
		public int count(MinecraftServer server) {
			return HerobrineState.get(server).markedDeaths().size();
		}
	}
}
