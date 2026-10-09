package com.forzacode.a1016_02.lore;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ModConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * F28's abandoned camp: found once by a background structure search (like an explorer map's, skipping camps
 * already used), remembered in {@link LoreData} as {@code F28/camp}, searched again only after
 * {@link LoreConfig#campRetryMinutes} when there was none. The server thread never runs the search.
 */
final class CampSearch {
	static final TagKey<Structure> CAMPS = TagKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("abandoned_camp"));
	static final String ANCHOR = "F28/camp";

	private static final AtomicBoolean RUNNING = new AtomicBoolean();
	private static volatile long retryAt = Long.MIN_VALUE;

	private CampSearch() {
	}

	/** The camp to use, if one is known. Otherwise starts a search (when none is running or waiting) and returns empty. */
	static Optional<BlockPos> camp(ServerLevel level, BlockPos near) {
		MinecraftServer server = level.getServer();
		Optional<GlobalPos> known = LoreData.get(server).anchor(ANCHOR).filter(p -> p.dimension().equals(level.dimension()));
		if (known.isPresent()) {
			return known.map(GlobalPos::pos);
		}
		if (server.getTickCount() < retryAt || !RUNNING.compareAndSet(false, true)) {
			return Optional.empty();
		}
		int radius = Math.max(1, LoreConfig.get().campSearchRadiusChunks);
		CompletableFuture.supplyAsync(() -> level.findNearestMapStructure(CAMPS, near, radius, true), Util.backgroundExecutor())
				.whenComplete((found, error) -> server.execute(() -> {
					RUNNING.set(false);
					if (error != null) {
						A1016_02.LOGGER.error("[a1016] lore: the abandoned camp search failed", error);
					}
					if (found == null) {
						retryAt = server.getTickCount() + ModConfig.realTicks(LoreConfig.get().campRetryMinutes * 60);
						return;
					}
					LoreData.get(server).setAnchor(ANCHOR, GlobalPos.of(level.dimension(), found));
					A1016_02.LOGGER.info("[a1016] lore: F28 will use the abandoned camp at {}", found.toShortString());
				}));
		return Optional.empty();
	}

	/** This camp has no unopened secret chest: never use it again (like a claimed explorer-map target) and search anew. */
	static void reject(ServerLevel level, StructureStart start) {
		level.structureManager().addReference(start);
		LoreData.get(level.getServer()).removeAnchor(ANCHOR);
	}

	/** The camp anchor points at a chunk without a camp: forget it. */
	static void forget(ServerLevel level) {
		LoreData.get(level.getServer()).removeAnchor(ANCHOR);
	}

	static void reset() {
		retryAt = Long.MIN_VALUE;
		RUNNING.set(false);
	}
}
