package com.forzacode.a1016_02.director;

import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

/** Common entrypoint of the director workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class DirectorInit {
	private static final DirectorImpl DIRECTOR = new DirectorImpl();

	private DirectorInit() {
	}

	public static void init() {
		Services.installDirector(DIRECTOR);
		DirectorCommands.register(DIRECTOR);

		// Core's JOIN handler runs first (CoreInit registers before us), so the subject is already known.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (Services.watch().isSubject(handler.player)) {
				DIRECTOR.onJoin(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (Services.watch().isSubject(handler.player)) {
				DIRECTOR.onLeave(server);
			}
		});
		HerobrineEvents.TELLING.register((player, text, pos, namesHim) -> DIRECTOR.onTelling(player.level().getServer()));
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				DirectorTriggers.onStartSleeping(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DirectorTriggers.clear());
	}

	/** The installed director (tests and commands). */
	public static DirectorImpl director() {
		return DIRECTOR;
	}
}
