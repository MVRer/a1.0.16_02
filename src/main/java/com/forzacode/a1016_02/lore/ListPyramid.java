package com.forzacode.a1016_02.lore;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceService;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The list in lava: "you erased something, and he keeps count". For each burnt list (up to
 * {@link LoreConfig#listPyramidMax}) he raises one more small sand pyramid in the ocean nearest to where it burnt,
 * out of view, from sea-floor sand moved there (nothing created), and records it as an {@code OCEAN_PYRAMID}
 * site. A burn outside the overworld counts from the player's base. The ocean is found once by a biome search on
 * a background thread; the pyramid waits until its chunks are loaded and nobody sees it.
 */
final class ListPyramid {
	static final String CAUSE = "list_in_lava";
	private static final AtomicBoolean SEARCHING = new AtomicBoolean();
	/** After a search found no ocean, the next one waits this long. */
	private static final double NO_OCEAN_RETRY_SECONDS = 20 * 60;
	private static long nextTry;

	private ListPyramid() {
	}

	static void reset() {
		nextTry = 0;
	}

	static void tick(MinecraftServer server, TellingData data) {
		long now = server.getTickCount();
		if (now < nextTry || data.burned().pending() <= 0) {
			return;
		}
		nextTry = now + ModConfig.realTicks(LoreConfig.get().listPyramidRetrySeconds);
		ServerLevel overworld = server.overworld();
		TellingData.Burned burned = data.burned();
		if (burned.ocean().isEmpty()) {
			BlockPos from = burned.near().filter(p -> p.dimension().equals(Level.OVERWORLD)).map(GlobalPos::pos)
					.orElseGet(() -> Services.watch().subject(server).flatMap(Services.watch()::base)
							.filter(p -> p.dimension().equals(Level.OVERWORLD)).map(GlobalPos::pos)
							.orElse(overworld.getRespawnData().pos()));
			searchOcean(overworld, from);
			return;
		}
		Optional<BlockPos> core = raise(overworld, burned.ocean().get().pos(), overworld.getRandom(), Services.traces(),
				LoreConfig.get().candidatesPerAttempt);
		core.ifPresent(pos -> recordRaised(overworld, pos, data));
	}

	/** Records a raised pyramid: one fewer owed, a new {@code OCEAN_PYRAMID} site. */
	static void recordRaised(ServerLevel level, BlockPos core, TellingData data) {
		data.setBurned(data.burned().raisedOne());
		Services.sites().record(SiteType.OCEAN_PYRAMID, level.dimension(), core, 2);
		A1016_02.LOGGER.info("[a1016] lore: a new pyramid stands at {} (the list burnt)", core.toShortString());
	}

	/**
	 * Raises one pyramid near {@code ocean}: tries a few columns within 48 blocks (unvisited chunks first). Empty if
	 * none fits right now, or while its chunks load.
	 */
	static Optional<BlockPos> raise(ServerLevel level, BlockPos ocean, RandomSource random, TraceService traces, int tries) {
		for (BlockPos column : Terrain.candidates(level, ocean, 0, 48, Math.max(1, tries), random)) {
			if (!ChunkGate.request(level, column, 14)) {
				return Optional.empty();
			}
			Optional<Builders.Pyramid> pyramid = Builders.planPyramid(level, column, true);
			if (pyramid.isEmpty()) {
				continue;
			}
			Build build = Build.his(traces, level, CAUSE);
			Builders.raise(build, pyramid.get());
			if (build.commit()) {
				return Optional.of(pyramid.get().core());
			}
		}
		return Optional.empty();
	}

	/** The nearest ocean to {@code from}, searched by biome off the server thread (no chunk loads), then remembered. */
	private static void searchOcean(ServerLevel level, BlockPos from) {
		if (!SEARCHING.compareAndSet(false, true)) {
			return;
		}
		int radius = Math.max(64, LoreConfig.get().listPyramidOceanRadius);
		MinecraftServer server = level.getServer();
		CompletableFuture.supplyAsync(() -> {
			Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome -> biome.is(BiomeTags.IS_OCEAN), from, radius, 32, 64);
			return found == null ? null : found.getFirst();
		}, Util.backgroundExecutor()).whenComplete((found, error) -> {
			SEARCHING.set(false);
			if (error != null) {
				A1016_02.LOGGER.error("[a1016] lore: the ocean search for the burnt list failed", error);
				return;
			}
			if (found == null) {
				A1016_02.LOGGER.warn("[a1016] lore: no ocean within {} blocks of {} for the burnt list's pyramid", radius, from.toShortString());
				server.execute(() -> nextTry = server.getTickCount() + ModConfig.realTicks(NO_OCEAN_RETRY_SECONDS));
				return;
			}
			server.execute(() -> {
				TellingData live = TellingData.get(server);
				live.setBurned(live.burned().withOcean(GlobalPos.of(level.dimension(), found)));
				nextTry = 0;
			});
		});
	}
}
