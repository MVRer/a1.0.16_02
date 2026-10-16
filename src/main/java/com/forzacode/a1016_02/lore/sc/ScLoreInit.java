package com.forzacode.a1016_02.lore.sc;

import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.SelfCreated;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Lore's part of the self-created layer (docs/SELF_CREATED.md: 14), called from {@code LoreInit}: the card, the files at the
 * start and the stop, the statistics on {@code TRACED}, the switch's revert and the debug number. The world list's side is
 * {@code lore.client.WorldListMarks}; {@code /seed}'s is {@code lore.mixin.SeedCommandMixin}.
 */
public final class ScLoreInit {
	private ScLoreInit() {
	}

	public static void init() {
		RecordConfig.get();
		Director.register(new RecordCard(TheRecord.LIVE));
		HerobrineEvents.TRACED.register(TheRecord.LIVE::onTraced);
		ServerLifecycleEvents.SERVER_STARTED.register(TheRecord.LIVE::onStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(TheRecord.LIVE::onStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TheRecord.LIVE.clear());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TheRecord.LIVE.onJoin(server, handler.player));
		// Switched off: the world list shows the truth again, and the statistics he added are taken back.
		SelfCreated.onSwitchedOff("sc14", TheRecord.LIVE::revert);
		ScLoreCommands.register();
	}
}
