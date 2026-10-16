package com.forzacode.a1016_02.atmosphere.sc;

import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.card.ComingHome;
import com.forzacode.a1016_02.atmosphere.card.NameGoneCard;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Atmosphere's part of the layer (docs/SELF_CREATED.md: 04, 05, 06, 07 and the fog places' client side): its config
 * sections, the sky payload, the debug commands, the switch's reverts ({@link SelfCreated#onSwitchedOff}) and the
 * server-side ticks. Called from {@code AtmosphereInit} and {@code AtmosphereServer}. Server thread.
 */
public final class AtmosphereSc {
	/** While the layer is off, names it took and could not give back yet are tried again this often (ticks). */
	public static final int NAMES_RETRY_TICKS = 600;

	private static boolean namesPending;

	private AtmosphereSc() {
	}

	/** Common init: sections, the payload, the commands, the reverts and a joining client's sky. */
	public static void init() {
		Sc04Config.get();
		Sc05Config.get();
		Sc06Config.get();
		Sc07Config.get();
		EmptyNightPayload.register();
		ScCommands.register();
		SelfCreated.onSwitchedOff("sc04", EmptyNight::revert);
		SelfCreated.onSwitchedOff("sc05", server -> ComingHome.LIVE.clear(server));
		SelfCreated.onSwitchedOff("sc06", server -> InStep.LIVE.clear());
		SelfCreated.onSwitchedOff("sc07", server -> namesPending = giveNamesBack(server) > 0);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> EmptyNightPayload.send(handler.player,
				new EmptyNightPayload(EmptyNight.windowNow(server))));
	}

	/** Every server tick (atmosphere's): 05's watcher, 06's episodes, and while the layer is off, names still to give back. */
	public static void tick(MinecraftServer server) {
		if (SelfCreated.enabled()) {
			ComingHome.LIVE.tick(server);
			InStep.LIVE.tick(server);
		} else if (namesPending && server.getTickCount() % NAMES_RETRY_TICKS == 0) {
			namesPending = giveNamesBack(server) > 0;
		}
	}

	/** That player stopped taking part. */
	public static void onLeave(UUID player) {
		ComingHome.LIVE.onLeave(player);
		InStep.LIVE.onLeave(player);
	}

	/** The server stopped: everything in memory goes. */
	public static void clear(MinecraftServer server) {
		ComingHome.LIVE.clear(server);
		InStep.LIVE.reset();
		EmptyNight.clear();
		namesPending = false;
	}

	/**
	 * 07's revert: every name it took ({@link NameGoneCard#CAUSE}) goes back through the ledger's own undo (out of view,
	 * never loading a chunk); returns how many are still to give back (not loaded, in view, or not found now).
	 */
	public static int giveNamesBack(MinecraftServer server) {
		int left = 0;
		for (TraceLedger.Entry entry : List.copyOf(TraceLedger.get(server).entries())) {
			if (entry.kind() != TraceLedger.Kind.NAME || !NameGoneCard.CAUSE.equals(entry.cause())) {
				continue;
			}
			ServerLevel level = server.getLevel(entry.pos().dimension());
			if (level == null) {
				continue;
			}
			TraceService.UndoResult result = Services.traces().undo(level, entry);
			if (result != TraceService.UndoResult.DONE) {
				left++;
			}
		}
		if (left > 0) {
			A1016_02.LOGGER.debug("[a1016] sc07 off: {} to give back later", left);
		}
		return left;
	}
}
