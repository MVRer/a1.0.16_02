package com.forzacode.a1016_02.entity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Common entrypoint of the entity workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class EntityInit {
	private EntityInit() {
	}

	public static void init() {
		ModEntities.register();

		// Prototype behavior, kept unchanged from the template: the figure appears in front of the player on join.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> HimEntity.spawnInFrontOf(handler.player));
	}
}
