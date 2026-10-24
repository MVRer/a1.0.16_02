package com.forzacode.a1016_02.entity.sc2.s31;

import com.forzacode.a1016_02.core.Director;

/**
 * Feature 31 of the self-created layer's second wave (docs/SELF_CREATED_2.md), entity's part: the cards {@code sc_31} (the
 * top at dusk, {@link Apex}) and {@code sc_31c} (among the trunks inside, {@link InsideTrunks}), the debug number and the dev
 * exhibit. 31b is the archive's own glimpse (world, through core's {@code Figure}). Nothing to give back when switched off:
 * a sighting leaves nothing, and the cards check the switch first ({@code ScGates}).
 */
public final class S31 {
	private S31() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc31Config.get();
		Director.register(Sc31Card.apex());
		Director.register(Sc31Card.trunks());
		Sc31Commands.register();
		Sc31Gallery.register(); // ignored outside a development environment
	}
}
