package com.forzacode.a1016_02.atmosphere;

import com.forzacode.a1016_02.atmosphere.card.FootstepLateCard;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;

/**
 * Server side of the atmosphere layer: ticks MobTamper, the timeline and the footstep watcher; turns music off the
 * first night any participant is online (once per world); sets the dusk fog level for the stage (the world's); rolls
 * and stores the fog weather ({@link FogWeather}, D-071); keeps the dead mountains' areas and sends each client the
 * ones near it.
 *
 * <p>Sessions (D-062.4): {@code HerobrineEvents.PARTICIPATION} drives per-player session work. Starting to take part
 * re-sends that player's running effects (once per connection, {@link ActiveEffects}); stopping (leaving, or turning
 * spectator) ends that player's footstep session and releases the mobs tampered for them and nobody still here, out of
 * view, and nobody else's. Fabric's
 * JOIN and DISCONNECT are only used for what belongs to the client connection itself, spectators included: whether a
 * fresh client still needs its re-sync, and which dead mountains a client was told about.
 */
public final class AtmosphereServer {
	private static final int CHECK_TICKS = 20;

	private AtmosphereServer() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(AtmosphereServer::tick);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> MobTamperImpl.INSTANCE.onUnload(entity));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ActiveEffects.onConnect(handler.player.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ActiveEffects.onDisconnect(handler.player.getUUID());
			DeadMountains.onLeave(handler.player);
		});
		HerobrineEvents.PARTICIPATION.register((server, player, participating) -> {
			if (participating) {
				ActiveEffects.onParticipating(server, player);
			} else {
				FootstepLateCard.onLeave(player);
				MobTamperImpl.INSTANCE.releaseFor(server, player, Services.watch());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Tasks.clear(server);
			MobTamperImpl.INSTANCE.clear();
			ActiveEffects.clear();
			FootstepLateCard.clear();
			DeadMountains.clear();
		});
		HerobrineEvents.STAGE_CHANGED.register((server, oldStage, newStage) -> applyDuskFog(server, newStage));
	}

	private static void tick(MinecraftServer server) {
		MobTamperImpl.INSTANCE.tick(server);
		Tasks.tick(server);
		FootstepLateCard.tick(server);
		DeadMountains.tick(server);
		if (server.getTickCount() % CHECK_TICKS == 0) {
			checkFirstNight(server);
			checkDuskFog(server);
			FogWeather.check(server);
		}
	}

	/**
	 * Music goes off the first time it is night (by the overworld clock) while any participant is online, wherever
	 * they are, and is never turned off again by us. Once per world: the flag is saved in the same tick.
	 */
	private static void checkFirstNight(MinecraftServer server) {
		AtmosphereData data = AtmosphereData.get(server);
		if (Gates.musicOffNow(data.musicOffDone(), !Services.watch().participants(server).isEmpty(), Gates.isNight(server.overworld()))) {
			data.setMusicOffDone();
			ClientEffects.setMusicOff(server, true);
		}
	}

	/** Sets the dusk fog for the current stage once per stage, so a later override (an ending) stays. */
	private static void checkDuskFog(MinecraftServer server) {
		Stage stage = HerobrineState.get(server).stage();
		if (AtmosphereData.get(server).duskStage() != stage.level()) {
			applyDuskFog(server, stage);
		}
	}

	private static void applyDuskFog(MinecraftServer server, Stage stage) {
		AtmosphereData.get(server).setDuskStage(stage.level());
		ClientEffects.setDuskFog(server, AtmosphereConfig.get().duskFogFor(stage));
	}
}
