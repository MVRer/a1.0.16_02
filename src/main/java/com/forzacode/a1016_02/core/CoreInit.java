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
		CommandHooks.install();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> Services.sites().attach(server));
		ServerLifecycleEvents.SERVER_STARTED.register(HerobrineState::get);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Services.sites().detach();
			Services.watch().clear();
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Services.watch().onJoin(handler.player);
			ClientEffects.sync(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Services.watch().onLeave(handler.player));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			boolean subjectOnline = Services.watch().subject(server).isPresent();
			GameClock.tick(server, subjectOnline);
			Services.watch().tick(server);
			Services.director().tick(server);
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				Services.watch().onBroken(serverLevel, pos);
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
