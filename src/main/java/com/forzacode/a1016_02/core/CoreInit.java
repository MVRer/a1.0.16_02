package com.forzacode.a1016_02.core;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Core's common init. Runs before every workstream's {@code init()}. */
public final class CoreInit {
	private CoreInit() {
	}

	public static void init() {
		ModConfig.load();
		ClientEffects.registerPayloads();
		BlindState.register();
		CommandHooks.install();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Services.sites().attach(server);
			Services.protectedAreas().attach(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			HerobrineState.get(server);
			// Read now, so its conversions are watched from the first block a player changes (TraceLedger.changedSince).
			TraceLedger.get(server);
			SelfCreated.serverStarted(server);
		});
		// The layer switched off: core's fog places go (clients are told at once, and get none on join).
		SelfCreated.onSwitchedOff("core", server -> {
			if (!HerobrineState.get(server).effects().fogPlaces().isEmpty()) {
				ClientEffects.clearFogPlaces(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Services.sites().detach();
			Services.protectedAreas().detach();
			Services.watch().clear();
			TraceLedger.stopWatching();
		});

		// Per player (D-062.4): a join or a leave only ever touches that player.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Services.watch().onJoin(handler.player);
			ClientEffects.sync(handler.player);
		});
		// Fabric may run DISCONNECT on the network thread (a client that quits or crashes): the watch hands the leave to
		// the server thread, so PARTICIPATION false always reaches its listeners there (R12-4).
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Services.watch().onDisconnect(server, handler.player));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Participation first (known players, the clocks, PARTICIPATION events), so everything after sees this tick's.
			Services.watch().tickParticipation(server);
			Services.watch().tick(server);
			Services.director().tick(server);
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				Services.watch().onBroken(serverLevel, pos);
				if (player instanceof ServerPlayer serverPlayer) {
					Services.watch().onMined(serverPlayer, serverLevel, pos, state);
				}
			}
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				Services.watch().onCombat(player);
			}
			if (source.getEntity() instanceof ServerPlayer attacker) {
				Services.watch().onCombat(attacker);
			}
		});
	}
}
