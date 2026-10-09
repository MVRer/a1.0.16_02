package com.forzacode.a1016_02.lore;

import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
		TellingCards.register();

		HerobrineEvents.STAGE_CHANGED.register((server, oldStage, newStage) -> engine.onStageChanged());
		HerobrineEvents.MARKED_DEATH.register((player, cause, pos) -> LiveBooks.onMarkedDeath(player, cause,
				TellingData.get(player.level().getServer())));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ChunkGate.tick(server);
			engine.tick(server);
			reads.tick(server);
			triggers.tick(server);
			Telling.tick(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			engine.reset();
			triggers.reset();
			reads.clear();
			ChunkGate.clear();
			CampSearch.reset();
			Telling.reset();
			UnbreakableSigns.load(server);
			// Choose the untouched grove early, so it exists before any scar could be made in it.
			UntouchedGrove.ensure(server.overworld(), server.overworld().getRespawnData().pos(), server.overworld().getRandom());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ChunkGate.clear();
			CampSearch.reset();
			UnbreakableSigns.clear();
			Telling.reset();
			engine.reset();
			triggers.reset();
			reads.clear();
		});
		// F30's signs: no player may break them (the rest is in lore's mixins).
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !UnbreakableSigns.isProtected(level, pos));
		// Telling: breaking your own sign about him; naming him in chat (signs and books come through lore's mixins).
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
				Telling.onBlockBroken(serverPlayer, serverLevel, pos);
			}
		});
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> Telling.onChat(sender, message.signedContent()));
		ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) -> {
			ServerPlayer player = source.getPlayer();
			if (player != null) {
				Telling.onChat(player, message.signedContent());
			}
		});
		LoreCommands.register(engine);
	}
}
