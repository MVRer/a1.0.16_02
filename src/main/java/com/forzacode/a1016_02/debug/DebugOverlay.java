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
import com.forzacode.a1016_02.atmosphere.card.EchoDoorCard;
import com.forzacode.a1016_02.atmosphere.card.EchoMiningCard;
import com.forzacode.a1016_02.core.AccidentPlanner;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TrapType;
import com.forzacode.a1016_02.dig.DigData;
import com.forzacode.a1016_02.director.DirectorApi;
import com.forzacode.a1016_02.director.DirectorData;
import com.forzacode.a1016_02.director.DirectorMemory;
import com.forzacode.a1016_02.entity.EntityData;
import com.forzacode.a1016_02.entity.FigureApi;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.ScreenshotStatePayload;
import com.forzacode.a1016_02.entity.TheScreenshot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Dev overlay, server side. Only in a development environment and only after {@code /a1016 debug overlay on} (off
 * by default, never saved): once per second, every op player gets the director's state as they see it (their own
 * pacing and tension, the shared stage and attention, their own armed trap and how many others are armed, the world's
 * fog spell, each participant's tunnel ladder rung, blind moment and last echo or blind card, and who is armed for the
 * screenshot) as an {@link OverlayPayload}. With {@code /a1016 debug overlay sc} (the self-created layer, D-080: only
 * then) it also shows the layer's rows ({@link ScIndex#overlay}). Read-only: it never changes the director or the world.
 */
final class DebugOverlay {
	private static final int PERIOD_TICKS = 20;
	private static final long DAY_TICKS = 24000L;
	private static boolean enabled;
	/** The layer's rows too ({@code /a1016 debug overlay sc}); off with the overlay. */
	private static boolean scRows;

	private DebugOverlay() {
	}

	static void init() {
		OverlayPayload.register();
		if (!available()) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(DebugOverlay::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			enabled = false;
			scRows = false;
		});
	}

	/** Dev only: a release jar never shows the overlay. */
	static boolean available() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	static boolean enabled() {
		return enabled;
	}

	static boolean scRows() {
		return scRows;
	}

	static void setEnabled(MinecraftServer server, boolean on) {
		setEnabled(server, on, false);
	}

	/** On or off; {@code sc} adds the layer's rows (only with the overlay on). */
	static void setEnabled(MinecraftServer server, boolean on, boolean sc) {
		enabled = on && available();
		scRows = enabled && sc;
		sendToOps(server, enabled);
	}

	private static void tick(MinecraftServer server) {
		if (enabled && server.getTickCount() % PERIOD_TICKS == 0) {
			sendToOps(server, true);
		}
	}

	/**
	 * The overlay for one viewer: their own pacing, tension and trap, the shared stage and attention, the fog spell, the
	 * ladder, everyone's blind moments and the screenshot's arms.
	 */
	static OverlayPayload build(MinecraftServer server, UUID viewer) {
		try {
			DirectorApi.Snapshot snapshot = DirectorApi.snapshot(server, viewer);
			List<String> rows = new ArrayList<>(OverlayText.rows(snapshot, trap(server, viewer), sighting(server), fog(server), ladder(server, viewer),
					blind(server, viewer, snapshot.hourTicks()), screenshot(server)));
			if (scRows) {
				for (String[] row : ScIndex.overlay(server, viewer)) {
					rows.add(row[0] + OverlayPayload.SEPARATOR + row[1]);
				}
			}
			return new OverlayPayload(true, rows);
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

	/**
	 * One participant's blind moment (D-077: server ticks since their menu opened, 0 when none is open) and the newest
	 * echo or blind card (D-076, D-077) that fired for them, their own, with the play ticks since (null and 0 if none).
	 */
	record Blind(String name, boolean viewer, long blindTicks, @Nullable String lastCard, long lastAgo) {
	}

	/** Every participant's blind moment and last echo or blind card, in join order ({@link #blind(List, long)}). */
	static String blind(MinecraftServer server, UUID viewer, long hourTicks) {
		DirectorMemory memory = DirectorData.get(server).memory();
		List<Blind> rows = new ArrayList<>();
		for (ServerPlayer player : Services.watch().participants(server)) {
			UUID id = player.getUUID();
			DirectorMemory.HistoryEntry last = null;
			for (DirectorMemory.HistoryEntry entry : memory.find(id).map(m -> m.history()).orElse(List.of())) {
				if (!entry.felt() && echoOrBlind(entry.cardId())) {
					last = entry;
				}
			}
			rows.add(new Blind(player.getName().getString(), id.equals(viewer), Services.watch().blindTicks(player), last == null ? null : last.cardId(),
					last == null ? 0 : Math.max(0, GameClock.playTicks(server, id) - last.playTicks())));
		}
		return blind(rows, hourTicks);
	}

	/** An echo card (D-076) or a {@code BLIND} card (D-077). */
	static boolean echoOrBlind(String cardId) {
		return cardId.equals(EchoMiningCard.ID) || cardId.equals(EchoDoorCard.ID)
				|| CardRegistry.get(cardId).map(card -> card.tags().contains(CardTag.BLIND)).orElse(false);
	}

	/** "Ana (you) blind 64t (3.2 s), last blind_step 4m10s ago | Ben not blind", or "nobody taking part". */
	static String blind(List<Blind> rows, long hourTicks) {
		if (rows.isEmpty()) {
			return "nobody taking part";
		}
		StringJoiner joiner = new StringJoiner(" | ");
		for (Blind b : rows) {
			String moment = b.blindTicks() > 0 ? Fmt.f("blind %dt (%.1f s)", b.blindTicks(), b.blindTicks() / 20.0) : "not blind";
			joiner.add(b.name() + (b.viewer() ? " (you) " : " ") + moment
					+ (b.lastCard() == null ? "" : ", last " + b.lastCard() + " " + Fmt.dur(b.lastAgo(), hourTicks) + " ago"));
		}
		return joiner.toString();
	}

	/** One player armed for the screenshot (D-078): in-game ticks left, and what their client was told last. */
	record ShotArm(String name, long ticksLeft, boolean paused, boolean fogOn) {
	}

	/** Who is armed for the world's one screenshot, and whether it happened ({@link #screenshot(List, long, String)}). */
	static String screenshot(MinecraftServer server) {
		TheScreenshot shot = TheScreenshot.LIVE;
		HerobrineState state = HerobrineState.get(server);
		List<ShotArm> arms = new ArrayList<>();
		for (UUID id : shot.armed()) {
			ScreenshotStatePayload told = shot.told(id).orElse(ScreenshotStatePayload.OFF);
			arms.add(new ShotArm(name(state, id), shot.ticksLeft(server, id), !told.armed(), told.fogOn()));
		}
		EntityData data = EntityData.get(server);
		return screenshot(arms, data.screenshotTaken() ? data.screenshotDay() : -1, data.screenshotBy().map(id -> name(state, id)).orElse(null));
	}

	/** "armed: Ana (1.50 d left, fog on) | taken day 12 by Ben"; "not armed"; "paused" for an arm on hold. */
	static String screenshot(List<ShotArm> arms, long takenDay, @Nullable String takenBy) {
		StringJoiner joiner = new StringJoiner(", ");
		for (ShotArm arm : arms) {
			joiner.add(arm.name() + Fmt.f(" (%.2f d left", arm.ticksLeft() / (double) DAY_TICKS) + (arm.paused() ? ", paused" : arm.fogOn() ? ", fog on" : "")
					+ ")");
		}
		String armed = arms.isEmpty() ? "not armed" : "armed: " + joiner;
		return takenDay < 0 ? armed : armed + " | taken day " + takenDay + (takenBy == null ? "" : " by " + takenBy);
	}

	private static String name(HerobrineState state, UUID player) {
		return state.player(player).map(HerobrineState.Subject::name).orElse(player.toString().substring(0, 8));
	}
}
