package com.forzacode.a1016_02.ending.d;

import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.ending.EndingApi;
import com.forzacode.a1016_02.ending.EndingPath;

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
 * {@code ending.EndingInit}. State lives in {@link EndingDState}; shared facts in {@code HerobrineState} flags. One
 * chain for the world (D-064): any participant can do a step, and nothing waits for one player in particular.
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
			LastMinute.syncFlag(server); // never left over from a sequence that is not running
		});
		// ending:last_minute never outlives a run: cleared before the save (a resume sets it again), then the rest.
		ServerLifecycleEvents.SERVER_STOPPING.register(LastMinute::clearFlag);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		// Anyone who takes part can do a step (D-064): whoever breaks the block, throws the item, cuts the log.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
				EndingDState data = EndingDState.get(serverLevel.getServer());
				Marks.onBreak(serverLevel, serverPlayer, pos, state);
				Grove.onBreak(serverLevel, serverPlayer, pos, state, data, EndingApi::takesPart);
				Cairn.onBreak(serverLevel, serverPlayer, pos, state, data, EndingApi::takesPart);
			}
			return true;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				Marks.onItemAdded(level, item);
				Cairn.onItemAdded(level, item, EndingDConfig.get(), EndingApi::takesPart);
			}
		});
		// Someone joins in the middle of the last minute: it plays for them too.
		HerobrineEvents.PARTICIPATION.register((server, player, participating) -> {
			if (participating) {
				Services.watch().participant(server, player).ifPresent(p -> LastMinute.onParticipation(p, EndingDConfig.get()));
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
		} else if (server.getTickCount() % Math.max(1, cfg.checkTicks) == 0 && !EndingApi.ended(server)) {
			// The chain only runs while the story goes on (an ending that already finished leaves nothing to bury).
			Step before = data.step();
			Chain.check(server, data, cfg);
			if (data.step() != before && data.step().atLeast(Step.GROVE)) {
				EndingApi.setProgress(server, EndingPath.D, data.step().number());
			}
		}
	}

	static void clear() {
		Marks.clear();
		Grove.clear();
		Cairn.clear();
		Camp.clear();
		Chain.clear();
		Stair.clear();
		LastMinute.reset();
		Afterward.clear();
		Sting.set(false, false);
	}
}
