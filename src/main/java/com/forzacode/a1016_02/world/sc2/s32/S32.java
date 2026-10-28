package com.forzacode.a1016_02.world.sc2.s32;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Gallery;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import org.jspecify.annotations.Nullable;

/**
 * Feature 32 of the self-created layer's second wave, round 3 (docs/SELF_CREATED_2_R3.md, "32"), world's part: the
 * feature type and placed feature {@code a1016_02:world/s32} (every overworld biome, the last decoration step), its
 * config's copy for worldgen, the world's {@link CrumbData} (drained every tick and at stop), the runtime rules 32b and
 * 32c ({@link CrumbRules}), {@code /a1016 sc 32} and the dev-only exhibits. What generated stays (D-093): switched off,
 * no new crumb is made and the rules stop and give back what they took or moved.
 */
public final class S32 {
	/** Its id (opaque, D-103). */
	public static final String ID = "s32";
	/** Every cause of its rules starts with this. */
	public static final String CAUSE = "world:s32";
	public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, A1016_02.id("world/s32"));

	/** The config worldgen reads (a copy, published on the server thread). */
	private static volatile @Nullable Sc32Config config;

	private S32() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, A1016_02.id("world/s32"), CrumbFeature.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> publish());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> publish());
		ServerTickEvents.END_SERVER_TICK.register(S32::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(S32::drain);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CrumbIndex.clear();
			CrumbRules.LIVE.reset();
			config = null;
		});
		CrumbRules.init();
		CrumbCommands.register();
		if (Gallery.active()) {
			CrumbGallery.register();
		}
	}

	/** The config worldgen reads now (null before the server starts). Any thread. */
	public static @Nullable Sc32Config config() {
		return config;
	}

	/** Reads the config section again and publishes a copy (server thread). */
	public static void publish() {
		config = Sc32Config.get().copy();
	}

	/** Tests: publishes this config (null: none). */
	public static void publish(@Nullable Sc32Config c) {
		config = c == null ? null : c.copy();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 100 == 0) {
			publish();
		}
		drain(server);
		CrumbRules.LIVE.tick(server);
	}

	/** Keeps the crumbs worldgen built since the last drain. Server thread. */
	public static void drain(MinecraftServer server) {
		if (CrumbData.pending()) {
			try {
				CrumbData.get(server).drain();
			} catch (RuntimeException e) {
				A1016_02.LOGGER.debug("[a1016] s2: s32 nothing kept now", e);
			}
		}
	}
}
