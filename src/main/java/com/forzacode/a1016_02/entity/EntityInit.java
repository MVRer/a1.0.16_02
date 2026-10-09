package com.forzacode.a1016_02.entity;

/** Common entrypoint of the entity workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class EntityInit {
	private EntityInit() {
	}

	public static void init() {
		// Registers the figure (/summon a1016_02:him for dev). It never spawns on its own here.
		ModEntities.register();
	}
}
