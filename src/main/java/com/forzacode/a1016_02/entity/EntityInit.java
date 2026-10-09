package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.core.Director;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Common entrypoint of the entity workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class EntityInit {
	private EntityInit() {
	}

	public static void init() {
		ModEntities.register();
		EntityConfig.get(); // writes the defaults into the config file
		for (Variant variant : Variant.values()) {
			Director.register(new SightingCard(variant));
		}
		EntityCommands.register();
		ServerTickEvents.END_SERVER_TICK.register(FigureApi::sweep);
	}
}
