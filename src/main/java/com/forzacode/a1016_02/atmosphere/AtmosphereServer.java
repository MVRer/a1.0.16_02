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
 * Server side of the atmosphere layer: ticks MobTamper, the timeline and the footstep watcher; turns music off from
 * the subject's first night; sets the dusk fog level for the stage; re-sends running effects on join; keeps the dead
 * mountains' areas and sends each client the ones near it.
 */
public final class AtmosphereServer {
	private static final int CHECK_TICKS = 20;

	private AtmosphereServer() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(AtmosphereServer::tick);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> MobTamperImpl.INSTANCE.onUnload(entity));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ActiveEffects.onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			FootstepLateCard.onLeave(handler.player);
			DeadMountains.onLeave(handler.player);
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
		MobTamperImpl.INSTANCE.tick();
		Tasks.tick(server);
		FootstepLateCard.tick(server);
		DeadMountains.tick(server);
		if (server.getTickCount() % CHECK_TICKS == 0) {
			checkFirstNight(server);
			checkDuskFog(server);
		}
	}

	/** Music goes off the first time it is night (by the clock) while the subject is online, and is never turned off again by us. */
	private static void checkFirstNight(MinecraftServer server) {
		AtmosphereData data = AtmosphereData.get(server);
		if (data.musicOffDone() || Services.watch().subject(server).isEmpty() || !Gates.isNight(server.overworld())) {
			return;
		}
		data.setMusicOffDone();
		ClientEffects.setMusicOff(server, true);
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
