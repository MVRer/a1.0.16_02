package com.forzacode.a1016_02.lore;

import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Common entrypoint of the lore workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class LoreInit {
	private LoreInit() {
	}

	public static void init() {
		FragmentData.register();
		FragmentEngine engine = new FragmentEngine();
		Services.installFragments(new FragmentServiceImpl(engine));
		ReadWatcher reads = new ReadWatcher();
		reads.register();
		LoreTriggers triggers = new LoreTriggers();

		HerobrineEvents.STAGE_CHANGED.register((server, oldStage, newStage) -> engine.onStageChanged());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			engine.tick(server);
			reads.tick(server);
			triggers.tick(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			engine.reset();
			triggers.reset();
			reads.clear();
			// Choose the untouched grove early, so it exists before any scar could be made in it.
			UntouchedGrove.ensure(server.overworld(), server.overworld().getRespawnData().pos(), server.overworld().getRandom());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			engine.reset();
			triggers.reset();
			reads.clear();
		});
		LoreCommands.register(engine);
	}
}
