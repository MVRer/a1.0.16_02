package com.forzacode.a1016_02.core;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Fragment placement and read tracking. The lore workstream installs the real one. */
public interface FragmentService {
	/** True if the world profile rolled this fragment ("F01".."F30"). */
	boolean isEnabled(MinecraftServer server, String id);

	/** Places a fragment near {@code hint} (through {@link TraceService#leave}). False if it could not. */
	boolean place(String id, ServerLevel level, BlockPos hint);

	/** Records a read: updates {@link HerobrineState} and fires {@link HerobrineEvents#FRAGMENT_READ} once. */
	void markRead(ServerPlayer player, String id);

	/** Where the fragment was placed, if it was. */
	Optional<GlobalPos> placed(MinecraftServer server, String id);

	/** Default: reads the profile and state, places nothing. */
	final class Stub implements FragmentService {
		@Override
		public boolean isEnabled(MinecraftServer server, String id) {
			return HerobrineState.get(server).profile().fragments().contains(id);
		}

		@Override
		public boolean place(String id, ServerLevel level, BlockPos hint) {
			return false;
		}

		@Override
		public void markRead(ServerPlayer player, String id) {
		}

		@Override
		public Optional<GlobalPos> placed(MinecraftServer server, String id) {
			return Optional.ofNullable(HerobrineState.get(server).fragmentsPlaced().get(id));
		}
	}
}
