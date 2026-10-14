package com.forzacode.a1016_02.lore;

import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Each player's oldest placed blocks, by UUID ({@link LoreData#placements}, at most {@link LoreConfig#placementLogMax}
 * each): F13's cairn falls back on the story owner's oldest one still standing at their base when their first
 * crafting table, chest and block are all gone (D-066, M11). Fed by lore's block placement mixin; a block a player
 * breaks leaves the log, which makes room for a newer one. Bookkeeping only: nothing in the world changes.
 */
public final class PlacementLog {
	private PlacementLog() {
	}

	/** A player placed a block (lore's mixin, server side). Only a participant's placements are theirs (D-058). */
	public static void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		if (!Services.watch().isParticipant(player)) {
			return;
		}
		record(LoreData.get(level.getServer()), player, level, pos, state);
	}

	static boolean record(LoreData data, ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		return data.logPlacement(player.getUUID(), new PlacedBlock(GlobalPos.of(level.dimension(), pos.immutable()), state),
				Math.max(1, LoreConfig.get().placementLogMax));
	}

	/** A block was broken by a player: it leaves every log it is in. */
	static void onBroken(ServerLevel level, BlockPos pos) {
		LoreData.get(level.getServer()).forgetPlacement(GlobalPos.of(level.dimension(), pos.immutable()));
	}
}
