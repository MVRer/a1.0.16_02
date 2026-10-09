package com.forzacode.a1016_02.ending.d;

import com.forzacode.a1016_02.core.CommandHooks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

/**
 * Ending D, "No longer with us": the chain, the last minute, the afterward and the sting. Called by
 * {@code ending.EndingInit}. State lives in {@link EndingDState}; shared facts in {@code HerobrineState} flags.
 */
public final class EndingDInit {
	/** Set when D is complete (the last plank on his cross). Gates the sting. */
	public static final String COMPLETE_FLAG = "ending:d_complete";
	/** Set when D is complete: the director stays silent for good. */
	public static final String SILENCE_FOREVER_FLAG = "director:silence_forever";

	private EndingDInit() {
	}

	public static void init() {
		EndingDConfig.get();
		CalmMusicPayload.register();
		ServerTickEvents.END_SERVER_TICK.register(EndingDInit::tick);
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) {
				Sting.refresh(server);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Sting.refresh(server);
			LastMinute.resume(server, EndingDState.get(server));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
				Marks.onBreak(serverLevel, serverPlayer, pos, state);
				Grove.onBreak(serverLevel, serverPlayer, pos, state);
				Cairn.onBreak(serverLevel, serverPlayer, pos, state);
			}
			return true;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				Marks.onItemAdded(level, item);
				Cairn.onItemAdded(level, item, EndingDConfig.get());
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer serverPlayer && !level.isClientSide()) {
				Marks.onUseBlock(serverPlayer, player.getItemInHand(hand), hit);
			}
			return InteractionResult.PASS;
		});
		CommandHooks.register(EndingDCommands::register);
	}

	private static void tick(MinecraftServer server) {
		Marks.tick(server);
		EndingDState data = EndingDState.get(server);
		EndingDConfig cfg = EndingDConfig.get();
		if (LastMinute.running()) {
			LastMinute.tick(server, data, cfg);
		} else if (data.step() == Step.LAST_MINUTE) {
			LastMinute.resume(server, data);
		}
		if (data.step() == Step.AFTERWARD) {
			Afterward.tick(server, data, cfg);
		} else if (server.getTickCount() % Math.max(1, cfg.checkTicks) == 0) {
			Chain.check(server, data, cfg);
		}
	}

	static void clear() {
		Marks.clear();
		Grove.clear();
		Cairn.clear();
		Chain.clear();
		Stair.clear();
		LastMinute.reset();
		Afterward.clear();
		Sting.set(false, false);
	}
}
