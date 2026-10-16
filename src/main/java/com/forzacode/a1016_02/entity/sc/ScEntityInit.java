package com.forzacode.a1016_02.entity.sc;

import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.SelfCreated;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Entity's part of the self-created layer (docs/SELF_CREATED.md: 02, 03 and the {@code FIGURE_LEFT} of 10), called from
 * {@code EntityInit}: the card, the payloads, the arms' ticks and sessions, the reverts the switch runs, and the debug
 * numbers.
 */
public final class ScEntityInit {
	private ScEntityInit() {
	}

	public static void init() {
		NamelessConfig.get();
		HisViewConfig.get();
		Director.register(new NamelessCard(Nameless.LIVE));
		NamelessPayload.register();
		NamelessSpentPayload.register();
		NamelessReachPayload.register();
		HisViewPayload.register();
		HisViewTakenPayload.register();
		HerobrineEvents.PARTICIPATION.register(Nameless.LIVE::onParticipation);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Nameless.LIVE.tick(server);
			HisView.LIVE.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Nameless.LIVE.clear();
			HisView.LIVE.clear();
		});
		// Switched off: every arm ends (the friends' names are drawn again at once) and the world's old picture goes back.
		SelfCreated.onSwitchedOff("sc02", Nameless.LIVE::disarmAll);
		SelfCreated.onSwitchedOff("sc03", HisView.LIVE::restore);
		ScEntityCommands.register();
	}
}
