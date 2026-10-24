package com.forzacode.a1016_02.world.sc2.s17;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.lore.LoreApi;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Where 17e last saw each player's own book about him (lore's own-writing marker, {@link LoreApi#bookAboutHimWriters}):
 * every {@value ArchiveRules#SCAN_EVERY} ticks, the containers in the 3 x 3 chunks round each participant (already loaded;
 * nothing is loaded), never one inside his places. Session only: it fills again as players walk round their bases.
 */
final class Sightings {
	/** A container that held a writer's book. */
	record Seen(ResourceKey<Level> dimension, BlockPos container) {
	}

	private final Map<UUID, Seen> byWriter = new HashMap<>();

	/**
	 * Looks round each player.
	 *
	 * @param skip containers not to count (inside one of his places)
	 */
	void scan(List<ServerPlayer> people, BiPredicate<ServerLevel, BlockPos> skip) {
		for (ServerPlayer player : people) {
			ServerLevel level = player.level();
			ChunkPos at = player.chunkPosition();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(at.x() + dx, at.z() + dz);
					if (chunk == null) {
						continue;
					}
					for (BlockEntity be : chunk.getBlockEntities().values()) {
						// R12-12: never a container whose loot is not rolled yet (reading it would roll it, with nobody there).
						if (be instanceof net.minecraft.world.RandomizableContainer loot && loot.getLootTable() != null) {
							continue;
						}
						if (be instanceof Container container && !skip.test(level, be.getBlockPos())) {
							look(level, be.getBlockPos(), container);
						}
					}
				}
			}
		}
	}

	private void look(ServerLevel level, BlockPos pos, Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			for (UUID writer : LoreApi.bookAboutHimWriters(stack)) {
				byWriter.put(writer, new Seen(level.dimension(), pos.immutable()));
			}
		}
	}

	Optional<Seen> of(UUID writer) {
		return Optional.ofNullable(byWriter.get(writer));
	}

	void forget(UUID writer) {
		byWriter.remove(writer);
	}

	/** Tests and the switch. */
	void clear() {
		byWriter.clear();
	}

	/** The slot of this container holding a book of this writer's about him, or -1. */
	static int slotOf(Container container, UUID writer) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && LoreApi.bookAboutHimWriters(stack).contains(writer)) {
				return i;
			}
		}
		return -1;
	}
}
