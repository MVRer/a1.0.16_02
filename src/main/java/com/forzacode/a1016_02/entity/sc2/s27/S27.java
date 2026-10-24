package com.forzacode.a1016_02.entity.sc2.s27;

import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.SelfCreated;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Feature 27 of the self-created layer's second wave (docs/SELF_CREATED_2.md), entity's part: the card, the payloads, the
 * showings' ticks and sessions, the switch's end, the debug number and the dev exhibit. The client's part is
 * {@code entity.client.sc2.s27.S27Client}.
 */
public final class S27 {
	private S27() {
	}

	/** Called once from its workstream's second-wave init. */
	public static void init() {
		Sc27Config.get();
		Director.register(new Sc27Card(Sc27.LIVE));
		Sc27Payload.register();
		Sc27EndPayload.register();
		Sc27SeenPayload.register();
		HerobrineEvents.PARTICIPATION.register(Sc27.LIVE::onParticipation);
		ServerTickEvents.END_SERVER_TICK.register(Sc27.LIVE::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Sc27.LIVE.clear());
		// Switched off: every copy ends on its viewer's client (walking off where it is seen) and no new one comes.
		SelfCreated.onSwitchedOff("sc27", Sc27.LIVE::endAll);
		Sc27Commands.register();
		Sc27Gallery.register(); // ignored outside a development environment
	}
}
