package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tempo;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.director.CardInfo;
import com.forzacode.a1016_02.director.DirectorApi;
import com.forzacode.a1016_02.director.DirectorData;
import com.forzacode.a1016_02.director.DirectorMemory;
import com.forzacode.a1016_02.director.DirectorSim.Event;
import com.forzacode.a1016_02.director.DirectorSim.Kind;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the debug workstream: the scripted playthrough (20 h, every tempo, every hard 4b limit asserted,
 * the rates only reported), the limit checks themselves, and the dev overlay's text, payload and read-only snapshot.
 */
public class DebugGameTests {
	private static final long[] SEEDS = {Playthrough.DEFAULT_SEED, 1L, 42L};
	private static final Pattern LOCALIZED_DECIMAL = Pattern.compile("\\d,\\d");

	@GameTest
	public void playthroughTwentyHoursHoldsEveryHardLimit(GameTestHelper helper) {
		for (long seed : SEEDS) {
			List<Playthrough.Report> reports = Playthrough.runAll(20, seed);
			helper.assertTrue(reports.size() == Tempo.values().length, "one report per tempo, got " + reports.size());
			for (Playthrough.Report report : reports) {
				String at = report.tempo() + "/seed " + seed + ": ";
				// The real deck: every workstream's cards are registered (only debug_ping is left out).
				for (String id : List.of("fog_drift", "sighting_across_water", "tunnel_that_grows", "signature_house_elsewhere", "stop_sign",
						"accident_lava_floor", "missing_first_block")) {
					helper.assertTrue(report.cards().containsKey(id), at + "the playthrough's deck lacks the real card " + id);
				}
				helper.assertTrue(report.leftOut() == 1, at + report.leftOut() + " registered cards left out of the deck (only debug_ping should be)");
				if (seed == Playthrough.DEFAULT_SEED) {
					A1016_02.LOGGER.info("[a1016] playthrough 20h seed {}: {} real cards {}", seed, report.cards().size(), Playthrough.deckByTier(report.cards()));
					// Soft targets are rates: reported in the test log, never asserted.
					A1016_02.LOGGER.info("[a1016] playthrough 20h seed {}: {}", seed, report.summary());
					for (PlaythroughCheck.Check check : report.soft()) {
						A1016_02.LOGGER.info("[a1016]   soft [{}] {}: {}", check.status(), check.name(), check.measured());
					}
				}
				helper.assertTrue(report.result().stageAt(Stage.PROXIMITY) >= 0, at + "never reached Proximity in 20 h, so most limits went untested");
				helper.assertTrue(report.result().count(Kind.FIRE) > 0, at + "nothing fired in 20 h, so the limits went untested");
				int expected = report.limits().groupGap() > 0 && !report.limits().groupCards().isEmpty() ? 8 : 7; // R3-P: the wave's gap when the layer paces
				helper.assertTrue(report.hard().size() == expected, at + "expected " + expected + " hard checks, got " + report.hard().size());
				for (PlaythroughCheck.Check check : report.hard()) {
					helper.assertTrue(check.status() == PlaythroughCheck.Status.PASS, at + check.name() + " broken: " + check.breaches());
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void playthroughIsDeterministic(GameTestHelper helper) {
		List<String> first = Playthrough.run(Tempo.EARLY, 8, 99L).log();
		List<String> second = Playthrough.run(Tempo.EARLY, 8, 99L).log();
		helper.assertTrue(first.equals(second), "the same seed gave two different playthroughs");
		List<String> other = Playthrough.run(Tempo.EARLY, 8, 100L).log();
		helper.assertFalse(first.equals(other), "a different seed gave the same playthrough");
		helper.assertTrue(first.stream().noneMatch(line -> LOCALIZED_DECIMAL.matcher(line).find()), "the log has a localized decimal");
		helper.succeed();
	}

	@GameTest
	public void hardChecksCatchEveryBrokenLimit(GameTestHelper helper) {
		long hour = 72_000;
		PlaythroughCheck.Limits limits = limits(hour);
		Map<String, CardInfo> cards = Map.of("accident_x", new CardInfo("accident_x", Tier.MAJOR, Stage.PROXIMITY, Set.of(), Set.of(CardTag.ACCIDENT), false));

		List<Event> broken = new ArrayList<>();
		broken.add(session(0, Stage.ALONE));
		broken.add(fire(1_000, 1_000, Stage.ALONE, Tier.AMBIENT, "amb_a"));       // 50 s after joining
		broken.add(fire(10_000, 10_000, Stage.ALONE, Tier.AMBIENT, "amb_b"));     // second ambient in Alone
		broken.add(fire(12_000, 12_000, Stage.ALONE, Tier.MINOR, "minor_a"));     // a minor in Alone
		broken.add(stage(15_000, Stage.PROXIMITY));
		broken.add(fire(20_000, 20_000, Stage.PROXIMITY, Tier.MAJOR, "major_a")); // a major on day 0
		broken.add(fire(25_000, 25_000, Stage.PROXIMITY, Tier.MINOR, "minor_b")); // 11 min after minor_a
		broken.add(fire(40_000, 40_000, Stage.PROXIMITY, Tier.MAJOR, "major_b")); // 17 min after major_a
		broken.add(fire(100_000, 100_000, Stage.PROXIMITY, Tier.MAJOR, "accident_x")); // before hour 6
		List<PlaythroughCheck.Check> checks = PlaythroughCheck.hard(broken, limits, cards, List.of("a broken director rule"));
		helper.assertTrue(checks.size() == 7, "expected 7 hard checks, got " + checks.size());
		for (PlaythroughCheck.Check check : checks) {
			helper.assertTrue(check.status() == PlaythroughCheck.Status.FAIL && !check.breaches().isEmpty(), "missed a broken limit: " + check.name());
		}

		List<Event> clean = new ArrayList<>();
		clean.add(session(0, Stage.ALONE));
		clean.add(fire(7_000, 7_000, Stage.ALONE, Tier.AMBIENT, "amb_a"));
		clean.add(stage(20_000, Stage.TRACES));
		clean.add(fire(30_000, 30_000, Stage.TRACES, Tier.MINOR, "minor_a"));
		clean.add(fire(48_000, 48_000, Stage.TRACES, Tier.MINOR, "minor_b"));    // exactly 15 min later
		clean.add(stage(50_000, Stage.PROXIMITY));
		clean.add(fire(60_000, 60_000, Stage.PROXIMITY, Tier.MAJOR, "major_a")); // day 2
		clean.add(fire(132_000, 132_000, Stage.PROXIMITY, Tier.MAJOR, "major_b")); // exactly an hour later
		clean.add(session(200_000, Stage.PROXIMITY));
		clean.add(fire(206_000, 206_000, Stage.PROXIMITY, Tier.AMBIENT, "amb_b")); // 5 min after joining
		clean.add(fire(6 * hour + 72_000, 6 * hour + 72_000, Stage.PROXIMITY, Tier.MAJOR, "accident_x"));
		for (PlaythroughCheck.Check check : PlaythroughCheck.hard(clean, limits, cards, List.of())) {
			helper.assertTrue(check.status() == PlaythroughCheck.Status.PASS, "false alarm on " + check.name() + ": " + check.breaches());
		}

		// Allowing quiet: a quiet and an empty session are left out of the active time the rates use.
		List<Event> timed = new ArrayList<>(clean);
		timed.add(new Event(Kind.QUIET, 140_000, 140_000, Stage.PROXIMITY, null, null, false, 61, 140_000 + 24_000, false, "1d"));
		timed.sort((a, b) -> Long.compare(a.play(), b.play()));
		PlaythroughCheck.Timeline timeline = PlaythroughCheck.timeline(timed, 0, 3 * hour, Stage.ALONE, limits);
		PlaythroughCheck.StageTime proximity = timeline.in(Stage.PROXIMITY);
		// The timeline samples at the director tick (600 ticks here), so stage edges land on the next sample.
		helper.assertTrue(Math.abs(proximity.total() - (3 * hour - 50_000)) <= 600, "Proximity lasted " + proximity.total());
		helper.assertTrue(proximity.active() == proximity.total() - 24_000, "Proximity active " + proximity.active() + " of " + proximity.total());
		helper.succeed();
	}

	@GameTest
	public void overlayTextIgnoresTheSystemLocale(GameTestHelper helper) {
		DirectorApi.Snapshot snapshot = new DirectorApi.Snapshot("Ana", true, 2, Stage.PROXIMITY, 12.5, 34.5, 60, Tempo.EARLY, 0.6,
				2 * 72_000 + 14 * 1_200, 5 * 72_000 + 30 * 1_200, 3 * 24_000 + 100, 72_000, true, 5 * 24_000, 0.15, "quiet until day 5",
				List.of(new DirectorApi.Held(Tier.MINOR, "chest_opens", 4 * 1_200, "minor gap")),
				new DirectorMemory.HistoryEntry(72_000, 3, "sighting_close", Tier.MAJOR, true, false, Stage.PROXIMITY, true), 23 * 1_200,
				new DirectorApi.Next("minor gap", 11 * 1_200, -1), new DirectorApi.Next("major gap", 47 * 1_200, 80 * 1_200), 45 * 1_200, false);
		// A fog night on day 3, quiet, a third of the way into its ramp; two players on the ladder.
		Optional<FogSpell> spell = Optional.of(new FogSpell(3 * 24_000L + 11_500, 3 * 24_000L + 23_500, 0.55F, true));
		Locale before = Locale.getDefault();
		List<String> rows;
		try {
			Locale.setDefault(Locale.forLanguageTag("es-ES"));
			String fog = DebugOverlay.fog(spell, 3 * 24_000L + 12_500, FogWeatherRules.Settings.DEFAULT);
			String ladder = DebugOverlay.ladder(List.of(new DebugOverlay.Rung("Ana", true, 2), new DebugOverlay.Rung("Ben", false, 0)));
			String blind = DebugOverlay.blind(List.of(new DebugOverlay.Blind("Ana", true, 64, "blind_step", 5 * 1_200)), 72_000);
			String shot = DebugOverlay.screenshot(List.of(new DebugOverlay.ShotArm("Ana", 36_000, false, true)), -1, null);
			rows = OverlayText.rows(snapshot, "dark_corner (set) at 1, 64, 2", "ridge rising (unseen)", fog, ladder, blind, shot);
		} finally {
			Locale.setDefault(before);
		}
		String text = String.join("\n", rows);
		helper.assertTrue(rows.stream().allMatch(row -> row.indexOf(OverlayPayload.SEPARATOR) > 0), "a row without a label: " + rows);
		helper.assertFalse(LOCALIZED_DECIMAL.matcher(text).find(), "a localized decimal in the overlay: " + text);
		for (String expected : List.of("Ana", "2 taking part", "PROXIMITY", "x0.60", "12.5 (shared)", "your tension 34.5/60", "+15%", "until day 5",
				"chest_opens", "sighting_close (fake)", "felt, someone else's", "23m00s ago", "in 11m00s (minor gap)", "due in 1h20m", "dark_corner",
				"ridge rising", "play 2h14m (world 5h30m)", "day 3", "fog spell\tlevel 0.55, day 3 11500 to day 3 23500 | fog now 0.", "| quiet",
				"ladder\tAna rung 2 (you), Ben rung 0", "blind\tAna (you) blind 64t (3.2 s), last blind_step 5m00s ago",
				"screenshot\tarmed: Ana (1.50 d left, fog on)")) {
			helper.assertTrue(text.contains(expected), "the overlay is missing '" + expected + "':\n" + text);
		}
		helper.succeed();
	}

	/**
	 * The overlay's trap row is the viewer's own (one trap per player): Ana sees hers and how many others are armed,
	 * Ben (no trap of his) sees none and the count, someone alone with their trap sees no count, nobody's sees "none".
	 */
	@GameTest
	public void overlayTrapRowIsTheViewersOwnPlusTheOthersCount(GameTestHelper helper) {
		UUID ana = UUID.randomUUID();
		UUID ben = UUID.randomUUID();
		UUID cal = UUID.randomUUID();
		Map<UUID, ArmedTrap> traps = new LinkedHashMap<>();
		traps.put(cal, trap("lava_floor", new BlockPos(100, 12, -40)));
		traps.put(ana, trap("dark_corner", new BlockPos(1, 64, 2)));
		String anas = DebugOverlay.trap(traps, ana);
		String bens = DebugOverlay.trap(traps, ben);
		helper.assertTrue(anas.startsWith("dark_corner (set) at 1, 64, 2") && anas.endsWith("| 1 other armed") && !anas.contains("lava_floor"),
				"Ana's row: " + anas);
		helper.assertTrue(bens.equals("none | 2 others armed"), "Ben's row: " + bens);
		helper.assertTrue(DebugOverlay.trap(Map.of(ana, traps.get(ana)), ana).equals("dark_corner (set) at 1, 64, 2"), "a lone trap got a count");
		helper.assertTrue(DebugOverlay.trap(Map.of(), ana).equals("none"), "nobody's trap: " + DebugOverlay.trap(Map.of(), ana));
		helper.succeed();
	}

	/**
	 * The overlay's fog row (D-071) is the world's spell as /a1016 fog names it: none, or its level, from and until on
	 * the clock, the fog it gives now and "quiet"; the ladder row (D-072) is every participant's rung, the viewer's
	 * marked.
	 */
	@GameTest
	public void overlayFogAndLadderRows(GameTestHelper helper) {
		FogWeatherRules.Settings d = FogWeatherRules.Settings.DEFAULT;
		long day = 24_000L;
		helper.assertTrue(DebugOverlay.fog(Optional.empty(), 5 * day, d).equals("none"), "no spell: " + DebugOverlay.fog(Optional.empty(), 5 * day, d));
		FogSpell night = new FogSpell(4 * day + 11_500, 5 * day + 5_500, 0.65F, false);
		String full = DebugOverlay.fog(Optional.of(night), 4 * day + 18_000, d);
		helper.assertTrue(full.equals("level 0.65, day 4 11500 to day 5 5500 | fog now 0.65"), "a full fog night with its morning: " + full);
		String before = DebugOverlay.fog(Optional.of(night), 4 * day + 10_000, d);
		helper.assertTrue(before.endsWith("fog now 0.00"), "a spell not started yet gives no fog: " + before);
		String quiet = DebugOverlay.fog(Optional.of(new FogSpell(night.fromClock(), night.untilClock(), 0.65F, true)), 4 * day + 18_000, d);
		helper.assertTrue(quiet.endsWith(" | quiet"), "a quiet spell: " + quiet);
		helper.assertTrue(DebugOverlay.ladder(List.of()).equals("nobody taking part"), "nobody: " + DebugOverlay.ladder(List.of()));
		String ladder = DebugOverlay.ladder(List.of(new DebugOverlay.Rung("Ben", false, 3), new DebugOverlay.Rung("Ana", true, 0)));
		helper.assertTrue(ladder.equals("Ben rung 3, Ana rung 0 (you)"), "the ladder row: " + ladder);
		// Live: nobody takes part on the test server.
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(DebugOverlay.ladder(server, UUID.randomUUID()).equals("nobody taking part"), "live ladder " + DebugOverlay.ladder(server, UUID.randomUUID()));
		helper.succeed();
	}

	/**
	 * v1.3's overlay rows (INT-2): the blind row is every participant's blind moment (D-077, server ticks and seconds)
	 * and the newest echo or blind card that fired for them (D-076), the viewer's marked; the screenshot row (D-078) is
	 * who is armed, with the in-game days left and whether their arm is paused or has fog, and the day it happened.
	 */
	@GameTest
	public void overlayBlindAndScreenshotRows(GameTestHelper helper) {
		long hour = 72_000;
		helper.assertTrue(DebugOverlay.blind(List.of(), hour).equals("nobody taking part"), "nobody: " + DebugOverlay.blind(List.of(), hour));
		String blind = DebugOverlay.blind(List.of(new DebugOverlay.Blind("Ana", false, 0, "echo_mining", 12 * 1_200),
				new DebugOverlay.Blind("Ben", true, 31, null, 0)), hour);
		helper.assertTrue(blind.equals("Ana not blind, last echo_mining 12m00s ago | Ben (you) blind 31t (1.6 s)"), "the blind row: " + blind);
		helper.assertTrue(DebugOverlay.echoOrBlind("echo_mining") && DebugOverlay.echoOrBlind("echo_door") && DebugOverlay.echoOrBlind("blind_step")
				&& DebugOverlay.echoOrBlind("blind_torch") && DebugOverlay.echoOrBlind("blind_door") && !DebugOverlay.echoOrBlind("chest_opens")
				&& !DebugOverlay.echoOrBlind("steps_behind_once"), "the echo and blind cards");
		helper.assertTrue(DebugOverlay.screenshot(List.of(), -1, null).equals("not armed"), "nobody armed: " + DebugOverlay.screenshot(List.of(), -1, null));
		String shot = DebugOverlay.screenshot(List.of(new DebugOverlay.ShotArm("Ana", 12_000, true, true), new DebugOverlay.ShotArm("Ben", 48_000, false, false)),
				-1, null);
		helper.assertTrue(shot.equals("armed: Ana (0.50 d left, paused), Ben (2.00 d left)"), "the screenshot row: " + shot);
		String taken = DebugOverlay.screenshot(List.of(), 12, "Ben");
		helper.assertTrue(taken.equals("not armed | taken day 12 by Ben"), "after the screenshot: " + taken);
		// Live: nobody takes part on the test server; the row reads the screenshot's state and changes nothing.
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(DebugOverlay.blind(server, UUID.randomUUID(), hour).equals("nobody taking part"), "live blind " + DebugOverlay.blind(server, UUID.randomUUID(), hour));
		String live = DebugOverlay.screenshot(server);
		helper.assertTrue(live.startsWith("not armed") || live.startsWith("armed: "), "live screenshot " + live);
		helper.succeed();
	}

	private static ArmedTrap trap(String type, BlockPos pos) {
		return new ArmedTrap(type, Level.OVERWORLD, pos, List.of(), List.of(), Optional.empty(), List.of(), ArmedTrap.Phase.SET,
				new ArmedTrap.Window(0, 0, 0, ArmedTrap.NO_CLOCK), pos, pos, "test", 0, Set.of(), Optional.empty());
	}

	@GameTest
	public void overlayPayloadRoundTrips(GameTestHelper helper) {
		OverlayPayload payload = new OverlayPayload(true, List.of("stage\tPROXIMITY", "tension\t12.5/60"));
		ByteBuf buf = Unpooled.buffer();
		try {
			OverlayPayload.CODEC.encode(buf, payload);
			OverlayPayload decoded = OverlayPayload.CODEC.decode(buf);
			helper.assertTrue(decoded.equals(payload), "decoded " + decoded);
		} finally {
			buf.release();
		}
		helper.assertTrue(OverlayPayload.TYPE.id().toString().equals("a1016_02:debug/overlay"), "payload id " + OverlayPayload.TYPE.id());
		helper.succeed();
	}

	@GameTest
	public void liveSnapshotOnlyReads(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String memoryBefore = DirectorData.get(server).memory().toTag().toString();
		HerobrineState state = HerobrineState.get(server);
		// A viewer the director has never paced (nobody takes part on the test server): nothing is created for them.
		UUID viewer = UUID.randomUUID();
		DirectorApi.Snapshot snapshot = DirectorApi.snapshot(server, viewer);
		OverlayPayload payload = DebugOverlay.build(server, viewer);
		helper.assertTrue(snapshot.stage() == state.stage(), "snapshot stage " + snapshot.stage() + ", state " + state.stage());
		helper.assertTrue(!snapshot.participating() && snapshot.tension() == 0 && snapshot.sessionTicks() == -1, "a stranger's pacing: " + snapshot);
		helper.assertTrue(payload.on() && payload.rows().size() >= 10, "overlay rows " + payload.rows());
		helper.assertTrue(payload.rows().stream().noneMatch(row -> row.startsWith("error")), "overlay error " + payload.rows());
		helper.assertTrue(DirectorData.get(server).memory().find(viewer).isEmpty(), "the snapshot made a pacing for its viewer");
		helper.assertTrue(DirectorData.get(server).memory().toTag().toString().equals(memoryBefore), "the snapshot changed the director's memory");
		helper.assertTrue(state.tension(viewer) == 0, "the snapshot changed tension");
		helper.succeed();
	}

	/** A console that writes down what it is told (successes and failures alike). */
	private static CommandSource capture(List<String> said) {
		return new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
	}

	/**
	 * Why {@code ./gradlew runServer} answered "unexpected error" to every console line piped in through stdin, vanilla
	 * {@code stop} included (WIRE-1). A dedicated server makes each console line's source on its console thread as it
	 * reads the line ({@code createCommandSourceStack()}). Lines piped in at once are read while the server is still
	 * starting, before any level exists, so their source has no level, and vanilla's
	 * {@code Commands.executeCommandInContext} reads that level's game rules before running anything: every such line
	 * fails, whatever the command, before any mod code runs. The same lines read once the world is up run normally.
	 * Shown with a vanilla command beside ours, on a source made exactly as the console thread makes it then.
	 */
	@GameTest
	public void consoleLinesReadBeforeTheWorldLoadsFailInVanillaToo(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> said = new ArrayList<>();
		CommandSourceStack early = new CommandSourceStack(capture(said), Vec3.ZERO, Vec2.ZERO, null, LevelBasedPermissionSet.OWNER,
				Component.literal("Server"), server);
		CommandSourceStack late = server.createCommandSourceStack().withSource(capture(said));
		for (String command : List.of("list", "a1016 state")) {
			said.clear();
			server.getCommands().performPrefixedCommand(early, command);
			String before = String.join("\n", said);
			helper.assertTrue(before.contains("unexpected error"), "'" + command + "' read before the world loaded said:\n" + before);
			said.clear();
			server.getCommands().performPrefixedCommand(late, command);
			String after = String.join("\n", said);
			helper.assertTrue(!said.isEmpty() && !after.contains("unexpected error"), "'" + command + "' once the world is up said:\n" + after);
		}
		// What throws (vanilla logs it only at debug level): the missing level, read by Commands itself before any node runs.
		NullPointerException thrown = null;
		try {
			Commands.executeCommandInContext(early, context -> {
			});
		} catch (NullPointerException e) {
			thrown = e;
		}
		helper.assertTrue(thrown != null && thrown.getStackTrace()[0].getClassName().equals(Commands.class.getName())
				&& thrown.getStackTrace()[0].getMethodName().equals("executeCommandInContext"), "not vanilla's missing level: " + thrown);
		StackTraceElement[] trace = thrown.getStackTrace();
		A1016_02.LOGGER.info("[a1016] a console line read before the world loads: {} at {}, {}", thrown.getMessage(), trace[0], trace[1]);
		helper.succeed();
	}

	/**
	 * D-062.9 from the console with nobody taking part (the game test server): the read-only commands show the world
	 * part, the ones that act on a player say nobody takes part instead of failing, and every form with a player
	 * argument parses. Nothing here changes the world or the director (no timewarp, no fire lands).
	 */
	@GameTest
	public void consoleCommandsActOnTheFirstParticipantOrSaySoWhenNobodyTakesPart(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> said = new ArrayList<>();
		CommandSourceStack console = server.createCommandSourceStack().withSource(capture(said));
		String memoryBefore = DirectorData.get(server).memory().toTag().toString();
		for (String command : List.of("a1016 state", "a1016 director", "a1016 fire debug_ping", "a1016 fire debug_ping fake", "a1016 director tick",
				"a1016 director quiet clear")) {
			said.clear();
			server.getCommands().performPrefixedCommand(console, command);
			String text = String.join("\n", said);
			helper.assertTrue(!said.isEmpty() && !text.contains("unexpected error"), "'" + command + "' said:\n" + text);
			if (command.contains("fire") || command.contains("tick")) {
				helper.assertTrue(text.contains("nobody takes part right now"), "'" + command + "' did not say nobody takes part:\n" + text);
			}
			if (command.equals("a1016 state") || command.equals("a1016 director")) {
				helper.assertTrue(text.contains("director: world stage clock"), "'" + command + "' lacks the world part:\n" + text);
			}
		}
		helper.assertTrue(DirectorData.get(server).memory().toTag().toString().equals(memoryBefore), "a console command changed the director's memory");
		for (String command : List.of("a1016 fire debug_ping Ana", "a1016 fire debug_ping fake Ana", "a1016 director Ana", "a1016 director tick Ana",
				"a1016 director quiet clear Ana", "a1016 director sim 3 Ana", "a1016 director sim 3 synthetic telling 1.5 Ana", "a1016 director timewarp 2 Ana")) {
			var parse = server.getCommands().getDispatcher().parse(command, console);
			helper.assertTrue(parse.getExceptions().isEmpty() && !parse.getReader().canRead() && parse.getContext().getCommand() != null,
					"'" + command + "' does not parse: " + parse.getExceptions());
		}
		helper.succeed();
	}

	/** A run that reached Telling (simTellingAtHour) is checked for "sightings almost none"; one that did not is not. */
	@GameTest
	public void softCheckSightingsInTelling(GameTestHelper helper) {
		long hour = 72_000;
		PlaythroughCheck.Limits limits = limits(hour);
		Map<String, CardInfo> cards = Map.of("sight", new CardInfo("sight", Tier.MINOR, Stage.TRACES, Set.of(), Set.of(CardTag.SIGHTING), true),
				"scar", new CardInfo("scar", Tier.MINOR, Stage.TRACES, Set.of(), Set.of(CardTag.SCAR), false));
		List<Event> base = new ArrayList<>();
		base.add(session(0, Stage.ALONE));
		base.add(stage(600, Stage.PROXIMITY));
		for (int i = 0; i < 5; i++) {
			base.add(fire(i * hour + 30_000, i * hour + 30_000, Stage.PROXIMITY, Tier.MINOR, "sight"));
		}
		String name = "Telling: sightings";
		PlaythroughCheck.Timeline noTelling = PlaythroughCheck.timeline(base, 0, 10 * hour, Stage.ALONE, limits);
		helper.assertTrue(PlaythroughCheck.soft(base, noTelling, limits, cards).stream().noneMatch(c -> c.name().startsWith(name)),
				"a run that never reached Telling got the Telling check");

		List<Event> quiet = new ArrayList<>(base);
		quiet.add(new Event(Kind.TELLING, 5 * hour, 5 * hour, Stage.PROXIMITY, null, null, false, 0, -1, true, "named him"));
		quiet.add(stage(5 * hour, Stage.TELLING));
		quiet.add(fire(6 * hour, 6 * hour, Stage.TELLING, Tier.MINOR, "sight"));
		quiet.add(fire(7 * hour, 7 * hour, Stage.TELLING, Tier.MINOR, "scar"));
		PlaythroughCheck.Check ok = PlaythroughCheck.soft(quiet, PlaythroughCheck.timeline(quiet, 0, 10 * hour, Stage.ALONE, limits), limits, cards)
				.stream().filter(c -> c.name().startsWith(name)).findFirst().orElse(null);
		helper.assertTrue(ok != null && ok.status() == PlaythroughCheck.Status.PASS, "one sighting in 5 h of Telling is almost none: " + ok);

		List<Event> busy = new ArrayList<>(quiet);
		busy.add(fire(8 * hour, 8 * hour, Stage.TELLING, Tier.MINOR, "sight"));
		busy.add(fire(9 * hour, 9 * hour, Stage.TELLING, Tier.MINOR, "sight"));
		PlaythroughCheck.Check off = PlaythroughCheck.soft(busy, PlaythroughCheck.timeline(busy, 0, 10 * hour, Stage.ALONE, limits), limits, cards)
				.stream().filter(c -> c.name().startsWith(name)).findFirst().orElse(null);
		helper.assertTrue(off != null && off.status() == PlaythroughCheck.Status.OFF && !off.failed(), "3 sightings in 5 h of Telling passed: " + off);
		helper.succeed();
	}

	/**
	 * R3-P: the second wave's moments apart is a hard check: its cards and its moments that are no card (the crumbs')
	 * never closer than the gap; the depths' (33) only start it.
	 */
	@GameTest
	public void theSecondWavesMomentsApartIsAHardCheck(GameTestHelper helper) {
		long hour = 72_000;
		long gap = hour / 2;
		PlaythroughCheck.Limits plain = limits(hour);
		PlaythroughCheck.Limits limits = new PlaythroughCheck.Limits(hour, 600, 6_000, 18_000, 1, 1, 6 * hour, 6, 1.0, 1.0, 1, 2, 2 * hour, 4 * hour, 0.25,
				java.util.Set.of(), 0, java.util.Set.of("w_a", "w_b"), gap);
		List<Event> events = new ArrayList<>();
		events.add(session(0, Stage.PROXIMITY));
		events.add(fire(hour, hour, Stage.PROXIMITY, Tier.MINOR, "w_a"));
		events.add(new Event(Kind.MOMENT, hour + gap / 3, hour + gap / 3, Stage.PROXIMITY, null, "33", false, 0, -1, false, "")); // starts it
		events.add(fire(hour + gap, hour + gap, Stage.PROXIMITY, Tier.MINOR, "w_b"));                                      // 20 min after 33
		events.add(new Event(Kind.MOMENT, 3 * hour, 3 * hour, Stage.PROXIMITY, null, "32c", false, 0, -1, false, ""));
		events.add(new Event(Kind.MOMENT, 3 * hour + gap / 6, 3 * hour + gap / 6, Stage.PROXIMITY, null, "32b", false, 0, -1, false, ""));
		events.add(fire(5 * hour, 5 * hour, Stage.PROXIMITY, Tier.MINOR, "w_a"));
		String name = "at least 30 min between the second wave's moments";
		PlaythroughCheck.Check check = PlaythroughCheck.hard(events, limits, Map.of(), List.of()).stream().filter(c -> c.name().equals(name)).findFirst()
				.orElse(null);
		helper.assertTrue(check != null && check.failed() && check.breaches().size() == 2, "two breaches: " + check);
		helper.assertTrue(PlaythroughCheck.hard(events, plain, Map.of(), List.of()).stream().noneMatch(c -> c.name().equals(name)), "no gap, no check");
		helper.succeed();
	}

	private static PlaythroughCheck.Limits limits(long hour) {
		return new PlaythroughCheck.Limits(hour, 600, 6_000, 18_000, 1, 1, 6 * hour, 6, 1.0, 1.0, 1, 2, 2 * hour, 4 * hour, 0.25, java.util.Set.of(), 0, java.util.Set.of(), 0);
	}

	private static Event session(long play, Stage stage) {
		return new Event(Kind.SESSION_START, play, play, stage, null, null, false, 0, -1, false, "");
	}

	private static Event stage(long play, Stage stage) {
		return new Event(Kind.STAGE, play, play, stage, null, null, false, 0, -1, false, "");
	}

	private static Event fire(long play, long dayTicks, Stage stage, Tier tier, String id) {
		return new Event(Kind.FIRE, play, dayTicks, stage, tier, id, false, 0, -1, false, "");
	}
}
