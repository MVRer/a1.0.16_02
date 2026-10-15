package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.AccidentPlannerImpl;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.core.AccidentPlanner;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TrapType;
import com.forzacode.a1016_02.dig.DigData;
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
 * by default, never saved): once per second, every op player gets the director's state as they see it (their own
 * pacing and tension, the shared stage and attention, their own armed trap and how many others are armed, the world's
 * fog spell, each participant's tunnel ladder rung) as an {@link OverlayPayload}. Read-only: it never changes the
 * director or the world.
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
		sendToOps(server, enabled);
	}

	private static void tick(MinecraftServer server) {
		if (enabled && server.getTickCount() % PERIOD_TICKS == 0) {
			sendToOps(server, true);
		}
	}

	/** The overlay for one viewer: their own pacing, tension and trap, the shared stage and attention, the fog spell and the ladder. */
	static OverlayPayload build(MinecraftServer server, UUID viewer) {
		try {
			return new OverlayPayload(true, OverlayText.rows(DirectorApi.snapshot(server, viewer), trap(server, viewer), sighting(server), fog(server),
					ladder(server, viewer)));
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] debug overlay failed", e);
			return new OverlayPayload(true, List.of("error" + OverlayPayload.SEPARATOR + e));
		}
	}

	/** Every op player gets their own overlay, or the payload that hides it. */
	private static void sendToOps(MinecraftServer server, boolean on) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
				OverlayPayload.send(player, on ? build(server, player.getUUID()) : OverlayPayload.OFF);
			}
		}
	}

	/** The viewer's own armed trap and its phase (or "none"), and how many other players have one armed (one each). */
	static String trap(MinecraftServer server, UUID viewer) {
		AccidentPlanner planner = Services.accidents();
		if (planner instanceof AccidentPlannerImpl impl) {
			return trap(impl.armedTraps(server), viewer);
		}
		return planner.armed(viewer).map(TrapType::id).orElse("none");
	}

	/** {@link #trap(MinecraftServer, UUID)} from every armed trap by its player. */
	static String trap(Map<UUID, ArmedTrap> traps, UUID viewer) {
		ArmedTrap own = traps.get(viewer);
		int others = traps.size() - (own == null ? 0 : 1);
		String text = own == null ? "none" : describe(own);
		return others == 0 ? text : text + Fmt.f(" | %d other%s armed", others, others == 1 ? "" : "s");
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

	/** The world's fog spell (D-071) on the overworld clock, with the installed weather numbers. */
	static String fog(MinecraftServer server) {
		return fog(HerobrineState.get(server).effects().fogSpell(), server.overworld().getDefaultClockTime(), FogWeatherRules.settings());
	}

	/**
	 * A fog spell as one line: "none", or its level, from and until on the overworld clock ("day 3 11500", as
	 * {@code /a1016 fog} prints it), the fog it gives at {@code clock} and "quiet" for a spell without the director's
	 * boost.
	 */
	static String fog(Optional<FogSpell> spell, long clock, FogWeatherRules.Settings settings) {
		if (spell.isEmpty()) {
			return "none";
		}
		FogSpell s = spell.get();
		return Fmt.f("level %.2f, %s to %s | fog now %.2f%s", s.level(), when(s.fromClock()), when(s.untilClock()), s.amount(clock, settings),
				s.quiet() ? " | quiet" : "");
	}

	private static String when(long clock) {
		return Fmt.f("day %d %d", Math.floorDiv(clock, FogWeatherRules.DAY), Math.floorMod(clock, FogWeatherRules.DAY));
	}

	/** One participant's tunnel ladder rung (D-072): the highest rung they heard, 0 to 3. */
	record Rung(String name, boolean viewer, int rung) {
	}

	/** Every participant's tunnel ladder rung, in join order ({@link DigData#ladderRung}). */
	static String ladder(MinecraftServer server, UUID viewer) {
		DigData dig = DigData.get(server);
		List<Rung> rungs = new ArrayList<>();
		for (ServerPlayer player : Services.watch().participants(server)) {
			rungs.add(new Rung(player.getName().getString(), player.getUUID().equals(viewer), dig.ladderRung(player.getUUID())));
		}
		return ladder(rungs);
	}

	/** "Ana rung 2 (you), Ben rung 0", or "nobody taking part". */
	static String ladder(List<Rung> rungs) {
		if (rungs.isEmpty()) {
			return "nobody taking part";
		}
		StringJoiner joiner = new StringJoiner(", ");
		for (Rung r : rungs) {
			joiner.add(r.name() + " rung " + r.rung() + (r.viewer() ? " (you)" : ""));
		}
		return joiner.toString();
	}
}
