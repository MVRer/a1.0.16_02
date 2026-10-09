package com.forzacode.a1016_02.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CardTag;
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

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

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
				if (seed == Playthrough.DEFAULT_SEED) {
					// Soft targets are rates: reported in the test log, never asserted.
					A1016_02.LOGGER.info("[a1016] playthrough 20h seed {}: {}", seed, report.summary());
					for (PlaythroughCheck.Check check : report.soft()) {
						A1016_02.LOGGER.info("[a1016]   soft [{}] {}: {}", check.status(), check.name(), check.measured());
					}
				}
				helper.assertTrue(report.result().stageAt(Stage.PROXIMITY) >= 0, at + "never reached Proximity in 20 h, so most limits went untested");
				helper.assertTrue(report.result().count(Kind.FIRE) > 0, at + "nothing fired in 20 h, so the limits went untested");
				helper.assertTrue(report.hard().size() == 7, at + "expected 7 hard checks, got " + report.hard().size());
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
		DirectorApi.Snapshot snapshot = new DirectorApi.Snapshot(Stage.PROXIMITY, 12.5, 34.5, 60, Tempo.EARLY, 0.6, 2 * 72_000 + 14 * 1_200,
				3 * 24_000 + 100, 72_000, true, 5 * 24_000, 0.15, "quiet until day 5",
				List.of(new DirectorApi.Held(Tier.MINOR, "chest_opens", 4 * 1_200, "minor gap")),
				new DirectorMemory.HistoryEntry(72_000, 3, "sighting_close", Tier.MAJOR, true, false, Stage.PROXIMITY), 23 * 1_200,
				new DirectorApi.Next("minor gap", 11 * 1_200, -1), new DirectorApi.Next("major gap", 47 * 1_200, 80 * 1_200), 45 * 1_200, false);
		Locale before = Locale.getDefault();
		List<String> rows;
		try {
			Locale.setDefault(Locale.forLanguageTag("es-ES"));
			rows = OverlayText.rows(snapshot, "dark_corner (set) at 1, 64, 2", "ridge rising (unseen)");
		} finally {
			Locale.setDefault(before);
		}
		String text = String.join("\n", rows);
		helper.assertTrue(rows.stream().allMatch(row -> row.indexOf(OverlayPayload.SEPARATOR) > 0), "a row without a label: " + rows);
		helper.assertFalse(LOCALIZED_DECIMAL.matcher(text).find(), "a localized decimal in the overlay: " + text);
		for (String expected : List.of("PROXIMITY", "x0.60", "12.5", "34.5/60", "+15%", "until day 5", "chest_opens", "sighting_close (fake)",
				"23m00s ago", "in 11m00s (minor gap)", "due in 1h20m", "dark_corner", "ridge rising", "play 2h14m", "day 3")) {
			helper.assertTrue(text.contains(expected), "the overlay is missing '" + expected + "':\n" + text);
		}
		helper.succeed();
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
		double tensionBefore = state.tension();
		DirectorApi.Snapshot snapshot = DirectorApi.snapshot(server);
		OverlayPayload payload = DebugOverlay.build(server);
		helper.assertTrue(snapshot.stage() == state.stage(), "snapshot stage " + snapshot.stage() + ", state " + state.stage());
		helper.assertTrue(payload.on() && payload.rows().size() >= 10, "overlay rows " + payload.rows());
		helper.assertTrue(payload.rows().stream().noneMatch(row -> row.startsWith("error")), "overlay error " + payload.rows());
		helper.assertTrue(DirectorData.get(server).memory().toTag().toString().equals(memoryBefore), "the snapshot changed the director's memory");
		helper.assertTrue(state.tension() == tensionBefore, "the snapshot changed tension");
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

	private static PlaythroughCheck.Limits limits(long hour) {
		return new PlaythroughCheck.Limits(hour, 600, 6_000, 18_000, 1, 1, 6 * hour, 6, 1.0, 1.0, 1, 2, 2 * hour, 4 * hour, 0.25);
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
