package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.core.Director;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Common entrypoint of the entity workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class EntityInit {
	private EntityInit() {
	}

	public static void init() {
		ModEntities.register();
		EntityConfig.get().migrate(); // writes the defaults into the config file, and new defaults over old ones
		for (Variant variant : Variant.values()) {
			Director.register(new SightingCard(variant));
		}
		EntityCommands.register();
		FogEndPayload.register(); // the client's real fog end (D-035)
		ReportedFog.register();
		ServerTickEvents.END_SERVER_TICK.register(FigureApi::sweep);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GoUnder.clearLive());
	}
}
