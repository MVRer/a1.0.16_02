package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

import com.forzacode.a1016_02.accident.sc.Sc15Config;
import com.forzacode.a1016_02.accident.sc.StoodOn;
import com.forzacode.a1016_02.accident.sc.StoodOnData;
import com.forzacode.a1016_02.atmosphere.AtmosphereData;
import com.forzacode.a1016_02.atmosphere.card.ComingHome;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNight;
import com.forzacode.a1016_02.atmosphere.sc.InStep;
import com.forzacode.a1016_02.atmosphere.sc.Sc04Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc07Config;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.FogPlace;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.dig.DigData;
import com.forzacode.a1016_02.dig.sc.DigScMemory;
import com.forzacode.a1016_02.dig.sc.Sc12Config;
import com.forzacode.a1016_02.dig.sc.Sc13Config;
import com.forzacode.a1016_02.dig.sc.WayBack;
import com.forzacode.a1016_02.director.DirectorData;
import com.forzacode.a1016_02.director.DirectorImpl;
import com.forzacode.a1016_02.director.PacingMemory;
import com.forzacode.a1016_02.director.sc.PlaceMemory;
import com.forzacode.a1016_02.director.sc.Places;
import com.forzacode.a1016_02.director.sc.Sc01Config;
import com.forzacode.a1016_02.entity.sc.HisView;
import com.forzacode.a1016_02.entity.sc.Nameless;
import com.forzacode.a1016_02.lore.sc.RecordData;
import com.forzacode.a1016_02.lore.sc.TheRecord;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.sc.ScMarks;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc.Tracks;
import com.forzacode.a1016_02.world.sc.Turned;
import com.forzacode.a1016_02.world.sc.WhereHeStood;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The self-created layer's index (docs/SELF_CREATED.md, SC-5): {@code /a1016 sc} with no number prints the switch and
 * then one line per number, 01 to 15 and the second wave's 16 to 31 ({@link Sc2Index}, docs/SELF_CREATED_2.md SC2-9),
 * with its state in opaque words only (armed, done, waiting, counts, days, time left), never what a number is; each number's own command ({@code /a1016 sc <NN>}) says more. The dev overlay's
 * layer rows ({@code /a1016 debug overlay sc}) are {@link #overlay}. The index and the overlay are read-only: nothing
 * there changes the world, a memory or an arm. {@code /a1016 sc on|off} is the layer's switch. Lines go only to
 * whoever ran the command.
 */
final class ScIndex {
	/** At most this many participants get a row of their own in the overlay. */
	static final int MAX_OVERLAY_PLAYERS = 6;
	private static final long DAY_TICKS = 24_000L;

	private ScIndex() {
	}

	static void register() {
		CommandHooks.register((root, context) -> root.then(Commands.literal("sc").executes(ScIndex::index)
				.then(Commands.literal("on").executes(ctx -> switchTo(ctx, true)))
				.then(Commands.literal("off").executes(ctx -> switchTo(ctx, false)))));
	}

	private static int index(CommandContext<CommandSourceStack> ctx) {
		for (String line : lines(ctx.getSource().getServer())) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	/**
	 * {@code /a1016 sc on|off}: the layer's switch, live ({@code SelfCreated.setEnabled}: written to the config; off runs
	 * every part's revert now, and again at every start while it stays off).
	 */
	private static int switchTo(CommandContext<CommandSourceStack> ctx, boolean on) {
		boolean was = SelfCreated.enabled();
		SelfCreated.setEnabled(ctx.getSource().getServer(), on);
		String line = was == on ? "[a1016] sc: already " + (on ? "on" : "off")
				: on ? "[a1016] sc: on" : "[a1016] sc: off; every part put back what it could (the log names each one)";
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/** The switch, then 01 to 31, one line each. */
	static List<String> lines(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		lines.add("[a1016] sc: " + (SelfCreated.enabled() ? "on" : "off (sections.sc.enabled false: nothing below acts)")
				+ "; /a1016 sc <NN> for one number");
		List<ServerPlayer> players = Services.watch().participants(server);
		lines.add(line01(server, players));
		lines.add(safe(() -> Nameless.LIVE.line(server), "02"));
		lines.add(safe(() -> HisView.LIVE.line(server), "03"));
		lines.add(safe(() -> line04(server), "04"));
		lines.add(safe(() -> perPlayer("05", players, p -> armMinutes(ComingHome.LIVE.ticksLeft(server, p.getUUID()), ComingHome.LIVE.armedCard(p.getUUID()))),
				"05"));
		lines.add(safe(() -> line06(server, players), "06"));
		lines.add(safe(() -> line07(server), "07"));
		lines.add(safe(() -> line08(server), "08"));
		lines.add(safe(() -> line09(server), "09"));
		lines.add(safe(() -> line10(server), "10"));
		lines.add(safe(() -> line11(server), "11"));
		lines.add(safe(() -> line12(server), "12"));
		lines.add(safe(() -> line13(server, players), "13"));
		lines.add(safe(() -> TheRecord.LIVE.line(server), "14"));
		lines.add(safe(() -> line15(server, players), "15"));
		lines.addAll(Sc2Index.lines(server, players));
		return lines;
	}

	/** One number's line; a reader that fails says so on its own line and the rest still print. */
	private static String safe(java.util.function.Supplier<String> line, String number) {
		try {
			return line.get();
		} catch (RuntimeException e) {
			return number + ": could not be read (" + e.getClass().getSimpleName() + ")";
		}
	}

	// --- one line per number ---

	/** Each participant's factor where they stand now, the places they remember and whether a chunk is trusted. */
	private static String line01(MinecraftServer server, List<ServerPlayer> players) {
		return safe(() -> perPlayer("01", players, p -> factor(server, p)), "01");
	}

	/** "x1.30 (3 places, a trusted chunk)", "x1.00 (not yet: its first hour)", or "no pacing yet". */
	static String factor(MinecraftServer server, ServerPlayer player) {
		Optional<PacingMemory> pacing = DirectorData.get(server).memory().find(player.getUUID());
		if (pacing.isEmpty()) {
			return "no pacing yet";
		}
		Sc01Config cfg = Sc01Config.get();
		PlaceMemory places = pacing.get().places();
		long play = GameClock.playTicks(server, player.getUUID());
		long day = GameClock.dayTicks(server);
		long hour = Math.max(1, ModConfig.realTicks(3600));
		long remembered = places.places().stream().filter(place -> Places.remembered(place, day, cfg)).count();
		boolean trusted = Places.trusted(places, play, day, hour, cfg).isPresent();
		String what = remembered + (remembered == 1 ? " place" : " places") + (trusted ? ", a trusted chunk" : "");
		HerobrineState state = HerobrineState.get(server);
		String notYet = !SelfCreated.enabled() ? "off" : Places.storyQuiet(state) ? "the story is quiet" : !state.stage().atLeast(Stage.TRACES) ? "stage "
				+ Fmt.lower(state.stage()) : play < hour ? "their first hour" : null;
		if (notYet != null) {
			return "x1.00 (not now: " + notYet + "; " + what + ")";
		}
		double factor = Places.factor(places, player.level().dimension(), player.blockPosition(), play, day, hour,
				DirectorImpl.atRemains(player.level(), player.blockPosition(), cfg.radius), cfg);
		return Fmt.f("x%.2f (%s)", factor, what);
	}

	/** The world's count and cap, the last one, and the one set now (and whether its window holds the clock now). */
	private static String line04(MinecraftServer server) {
		EmptyNight.Memory m = AtmosphereData.get(server).emptyNight();
		long now = server.overworld().getDefaultClockTime();
		String set = m.night() == EmptyNight.NEVER ? "none set"
				: "set for day " + m.night() + (EmptyNight.sky(m.night()).contains(now) ? " (its window now)" : "");
		return Fmt.f("04: %d of %d, last %s, %s", m.count(), Sc04Config.get().maxPerWorld, m.last() == EmptyNight.NEVER ? "never" : "day " + m.last(), set);
	}

	private static String line06(MinecraftServer server, List<ServerPlayer> players) {
		long clock = server.overworld().getDefaultClockTime();
		return perPlayer("06", players, p -> {
			UUID id = p.getUUID();
			List<String> parts = new ArrayList<>();
			if (InStep.LIVE.running(id)) {
				parts.add("running");
			}
			long arm = InStep.LIVE.armTicksLeft(server, id);
			if (arm >= 0) {
				parts.add(Fmt.f("armed %.1f min", arm / 1200.0));
			}
			if (InStep.LIVE.spent(id, clock)) {
				parts.add("spent until the next day");
			}
			return parts.isEmpty() ? "waiting" : String.join(", ", parts);
		});
	}

	private static String line07(MinecraftServer server) {
		AtmosphereData.Sc07 sc07 = AtmosphereData.get(server).sc07();
		return Fmt.f("07: %d of %d, last %s", sc07.count(), Sc07Config.get().maxPerWorld, sc07.lastDay() < 0 ? "never" : "day " + sc07.lastDay());
	}

	private static String line08(MinecraftServer server) {
		return bases("08", WorldData.get(server).sc().bases(), true, Turned.config().maxPerBase);
	}

	private static String line09(MinecraftServer server) {
		return bases("09", WorldData.get(server).sc().bases(), false, Tracks.config().maxPerBase);
	}

	/** "08: 5 at 2 bases (most 4 of 8 at one), last day 9", or "08: none yet". */
	static String bases(String number, Map<GlobalPos, ScMarks.Base> bases, boolean turned, int maxPerBase) {
		int total = 0;
		int most = 0;
		int at = 0;
		long last = ScMarks.NEVER;
		for (ScMarks.Base base : bases.values()) {
			int n = turned ? base.turned() : base.tracks();
			if (n <= 0) {
				continue;
			}
			total += n;
			at++;
			most = Math.max(most, n);
			last = Math.max(last, turned ? base.turnedDay() : base.tracksDay());
		}
		if (total == 0) {
			return number + ": none yet";
		}
		return Fmt.f("%s: %d at %d base%s (most %d of %d at one), last day %d", number, total, at, at == 1 ? "" : "s", most, maxPerBase, last);
	}

	/** What waits in memory, and the world's places now (how many are easing out). */
	private static String line10(MinecraftServer server) {
		int waiting = WhereHeStood.LIVE.pending().size();
		List<FogPlace> places = HerobrineState.get(server).effects().fogPlaces();
		long clock = server.overworld().getDefaultClockTime();
		long fading = places.stream().filter(p -> p.fadingBy(clock) && !p.endedBy(clock)).count();
		long live = places.stream().filter(p -> !p.endedBy(clock)).count();
		return Fmt.f("10: %d waiting, %d place%s now%s (at most %d at once)", waiting, live, live == 1 ? "" : "s", fading > 0 ? " (" + fading + " easing out)" : "",
				WhereHeStood.config().maxFogPlaces);
	}

	private static String line11(MinecraftServer server) {
		List<ScMarks.Trail> trails = WorldData.get(server).sc().trails();
		StringJoiner days = new StringJoiner(", ");
		trails.forEach(t -> days.add("day " + t.day()));
		return Fmt.f("11: %d of %d%s", trails.size(), TorchTrail.config().maxPerWorld, trails.isEmpty() ? "" : " (" + days + ")");
	}

	private static String line12(MinecraftServer server) {
		DigScMemory sc = DigData.get(server).sc();
		long day = GameClock.dayTicks(server);
		StringJoiner arms = new StringJoiner(", ");
		sc.restArms().forEach((id, arm) -> arms.add(name(server, id) + Fmt.f(" %.2f d", Math.max(0, arm.untilDayTicks() - day) / (double) DAY_TICKS)));
		return Fmt.f("12: %d of %d, %s", sc.restRefusals(), Sc12Config.get().maxPerWorld, sc.restArms().isEmpty() ? "not armed" : "armed " + arms);
	}

	private static String line13(MinecraftServer server, List<ServerPlayer> players) {
		int done = DigData.get(server).sc().wayBacks();
		StringJoiner arms = new StringJoiner(", ");
		long now = server.getTickCount();
		for (ServerPlayer p : players) {
			long left = WayBack.armTicksLeft(p.getUUID(), now);
			if (left >= 0) {
				arms.add(p.getName().getString() + Fmt.f(" %.1f min", left / 1200.0));
			}
		}
		return Fmt.f("13: %d of %d, %s", done, Sc13Config.get().maxPerWorld, arms.length() == 0 ? "no debug arm" : "debug arm " + arms);
	}

	private static String line15(MinecraftServer server, List<ServerPlayer> players) {
		int count = StoodOnData.get(server).count();
		StringJoiner arms = new StringJoiner(", ");
		for (ServerPlayer p : players) {
			long left = StoodOn.ticksLeft(p.getUUID(), GameClock.playTicks(server, p.getUUID()));
			if (left >= 0) {
				arms.add(p.getName().getString() + Fmt.f(" %.1f min", left / 1200.0));
			}
		}
		return Fmt.f("15: %d of %d, %s", count, Sc15Config.get().maxPerWorld, arms.length() == 0 ? "not armed" : "armed " + arms);
	}

	// --- the overlay ---

	/**
	 * The overlay's layer rows (label, value): the switch with the world's numbers (04's window, 10's places, the
	 * counts), then one row per participant (the viewer first, at most {@link #MAX_OVERLAY_PLAYERS}) with their factor
	 * (01), every arm they hold with its time left (02, 03, 05, 06, 12, 13, 14, 15), and the second wave's: the place they
	 * are inside as its number and their scenes and arms of 21 to 27 ({@link Sc2Index#arms}).
	 */
	static List<String[]> overlay(MinecraftServer server, UUID viewer) {
		List<String[]> rows = new ArrayList<>();
		if (!SelfCreated.enabled()) {
			rows.add(new String[] {"sc", "off"});
			return rows;
		}
		rows.add(new String[] {"sc 04", safe(() -> line04(server).substring(4), "04")});
		rows.add(new String[] {"sc 10", safe(() -> line10(server).substring(4), "10")});
		List<ServerPlayer> players = new ArrayList<>(Services.watch().participants(server));
		players.sort((a, b) -> Boolean.compare(!a.getUUID().equals(viewer), !b.getUUID().equals(viewer)));
		for (ServerPlayer p : players.subList(0, Math.min(players.size(), MAX_OVERLAY_PLAYERS))) {
			String label = "sc " + p.getName().getString() + (p.getUUID().equals(viewer) ? " (you)" : "");
			rows.add(new String[] {label, safe(() -> arms(server, p), "arms")});
		}
		if (players.size() > MAX_OVERLAY_PLAYERS) {
			rows.add(new String[] {"sc", (players.size() - MAX_OVERLAY_PLAYERS) + " more taking part: /a1016 sc"});
		}
		return rows;
	}

	/** "01 x1.30 | 02 12.0m | 05 sc_05 40.0m | 06 running | 12 1.50d | 13 8.0m | 15 12.0m", or the factor alone. */
	static String arms(MinecraftServer server, ServerPlayer player) {
		UUID id = player.getUUID();
		StringJoiner out = new StringJoiner(" | ");
		out.add("01 " + factor(server, player));
		long nameless = Nameless.LIVE.ticksLeft(server, id);
		if (nameless >= 0) {
			out.add(Fmt.f("02 %.1fm", nameless / 1200.0));
		}
		if (HisView.LIVE.armedFigure() != 0 || HisView.LIVE.forcing()) {
			if (server.isSingleplayerOwner(player.nameAndId())) {
				out.add("03 " + (HisView.LIVE.armedFigure() != 0 ? "armed" : "next forced"));
			}
		}
		long home = ComingHome.LIVE.ticksLeft(server, id);
		if (home >= 0) {
			out.add("05 " + ComingHome.LIVE.armedCard(id).orElse("?") + Fmt.f(" %.1fm", home / 1200.0));
		}
		if (InStep.LIVE.running(id)) {
			out.add("06 running");
		} else if (InStep.LIVE.armTicksLeft(server, id) >= 0) {
			out.add(Fmt.f("06 armed %.1fm", InStep.LIVE.armTicksLeft(server, id) / 1200.0));
		}
		DigScMemory.RestArm rest = DigData.get(server).sc().restArm(id).orElse(null);
		if (rest != null) {
			out.add(Fmt.f("12 %.2fd", Math.max(0, rest.untilDayTicks() - GameClock.dayTicks(server)) / (double) DAY_TICKS));
		}
		long way = WayBack.armTicksLeft(id, server.getTickCount());
		if (way >= 0) {
			out.add(Fmt.f("13 %.1fm", way / 1200.0));
		}
		RecordData record = RecordData.get(server);
		if ((record.lastArmed() || record.versionArmed()) && server.isSingleplayerOwner(player.nameAndId())) {
			out.add("14 " + (record.lastArmed() ? "a" : "") + (record.versionArmed() ? "b" : ""));
		}
		long stood = StoodOn.ticksLeft(id, GameClock.playTicks(server, id));
		if (stood >= 0) {
			out.add(Fmt.f("15 %.1fm", stood / 1200.0));
		}
		try {
			Sc2Index.arms(server, player, out);
		} catch (RuntimeException e) {
			out.add("16-31 could not be read (" + e.getClass().getSimpleName() + ")");
		}
		return out.toString();
	}

	// --- helpers ---

	@FunctionalInterface
	private interface PerPlayer {
		String line(ServerPlayer player);
	}

	/** "NN: Ana ..., Ben ..." for every participant, or "NN: nobody taking part". */
	private static String perPlayer(String number, List<ServerPlayer> players, PerPlayer each) {
		if (players.isEmpty()) {
			return number + ": nobody taking part";
		}
		StringJoiner joiner = new StringJoiner(" | ");
		for (ServerPlayer p : players.subList(0, Math.min(players.size(), DebugCommands.MAX_PLAYER_LINES))) {
			joiner.add(p.getName().getString() + " " + each.line(p));
		}
		return number + ": " + joiner;
	}

	/** "armed 41.0 min (sc_05b)" or "not armed". */
	private static String armMinutes(long ticksLeft, Optional<String> card) {
		return ticksLeft < 0 ? "not armed" : Fmt.f("armed %.1f min", ticksLeft / 1200.0) + card.map(c -> " (" + c + ")").orElse("");
	}

	private static String name(MinecraftServer server, UUID player) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			return online.getName().getString();
		}
		return HerobrineState.get(server).player(player).map(HerobrineState.Subject::name).orElse(player.toString().substring(0, 8));
	}
}
