package com.forzacode.a1016_02.world;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.world.gen.ScarContext;
import com.forzacode.a1016_02.world.gen.ScarFeature;
import com.forzacode.a1016_02.world.live.EmptiedHouseCard;
import com.forzacode.a1016_02.world.live.LightOnMountainCard;
import com.forzacode.a1016_02.world.live.LoneLightNearBaseCard;
import com.forzacode.a1016_02.world.live.NewScarCard;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.live.WorldWatch;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Common entrypoint of the world workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class WorldInit {
	/** The placed feature that grows every old scar ({@code data/a1016_02/worldgen/placed_feature/world/scars.json}). */
	public static final ResourceKey<PlacedFeature> SCARS = ResourceKey.create(Registries.PLACED_FEATURE, A1016_02.id("world/scars"));

	private WorldInit() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, A1016_02.id("world/scars"), ScarFeature.CODEC);
		Registry.register(BuiltInRegistries.TICKET_TYPE, A1016_02.id("world/new_scar"), NewScarPlacer.TICKET);
		// Last in vegetal decoration: after the trees (so they can be stripped), before snow and ice.
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.VEGETAL_DECORATION, SCARS);

		WorldCommands.register();
		Director.register(new NewScarCard());
		Director.register(new LightOnMountainCard());
		Director.register(new LoneLightNearBaseCard());
		Director.register(new EmptiedHouseCard());
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
				WorldWatch.onBroken(serverLevel, serverPlayer, pos, state);
			}
		});

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			try {
				ScarContext.refresh(server);
			} catch (RuntimeException e) {
				A1016_02.LOGGER.warn("[a1016] world: scar snapshot not ready at start, retrying when the server runs", e);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ScarContext.refresh(server);
			ScarContext.warmUp(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(WorldSites::drain);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ScarContext.clear();
			WorldSites.clear();
			NewScarPlacer.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 == 0) {
				ScarContext.refresh(server);
			}
			WorldSites.drain(server);
			WorldWatch.tick(server);
			NewScarPlacer.tick(server);
		});
	}
}
