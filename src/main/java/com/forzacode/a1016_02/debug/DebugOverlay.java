package com.forzacode.a1016_02.debug;

import java.util.List;
import java.util.StringJoiner;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.AccidentPlannerImpl;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.core.AccidentPlanner;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TrapType;
import com.forzacode.a1016_02.director.DirectorApi;
import com.forzacode.a1016_02.entity.FigureApi;
import com.forzacode.a1016_02.entity.HimEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Dev overlay, server side. Only in a development environment and only after {@code /a1016 debug overlay on} (off
 * by default, never saved): once per second, every op player gets the director's state as an {@link OverlayPayload}.
 * Read-only: it never changes the director or the world.
 */
final class DebugOverlay {
	private static final int PERIOD_TICKS = 20;
	private static boolean enabled;

	private DebugOverlay() {
	}

	static void init() {
		OverlayPayload.register();
		if (!available()) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(DebugOverlay::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> enabled = false);
	}

	/** Dev only: a release jar never shows the overlay. */
	static boolean available() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	static boolean enabled() {
		return enabled;
	}

	static void setEnabled(MinecraftServer server, boolean on) {
		enabled = on && available();
		if (enabled) {
			sendToOps(server, build(server));
		} else {
			sendToOps(server, OverlayPayload.OFF);
		}
	}

	private static void tick(MinecraftServer server) {
		if (enabled && server.getTickCount() % PERIOD_TICKS == 0) {
			sendToOps(server, build(server));
		}
	}

	static OverlayPayload build(MinecraftServer server) {
		try {
			return new OverlayPayload(true, OverlayText.rows(DirectorApi.snapshot(server), trap(server), sighting(server)));
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] debug overlay failed", e);
			return new OverlayPayload(true, List.of("error" + OverlayPayload.SEPARATOR + e));
		}
	}

	private static void sendToOps(MinecraftServer server, OverlayPayload payload) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
				OverlayPayload.send(player, payload);
			}
		}
	}

	/** The armed trap and its phase, or "none". */
	static String trap(MinecraftServer server) {
		AccidentPlanner planner = Services.accidents();
		if (planner instanceof AccidentPlannerImpl impl) {
			return impl.armedTrap(server).map(DebugOverlay::describe).orElse("none");
		}
		return planner.armed().map(TrapType::id).orElse("none");
	}

	private static String describe(ArmedTrap trap) {
		return trap.type() + " (" + Fmt.lower(trap.phase()) + ") at " + trap.pos().toShortString();
	}

	/** Every figure that is out, with its variant and phase, or "none". */
	static String sighting(MinecraftServer server) {
		List<HimEntity> out = FigureApi.active(server);
		if (out.isEmpty()) {
			return "none";
		}
		StringJoiner joiner = new StringJoiner(", ");
		for (HimEntity him : out) {
			joiner.add(him.variant().shortName() + " " + Fmt.lower(him.phase()) + (him.everSeen() ? " (seen)" : " (unseen)"));
		}
		return joiner.toString();
	}
}
