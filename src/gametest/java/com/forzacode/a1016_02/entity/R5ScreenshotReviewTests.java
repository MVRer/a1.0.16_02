package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Review R5 (v1.3 bug hunt): the screenshot (D-078), each test failing on main at 4637570. The spot tests call the
 * client's own path: {@code ScreenshotClient.spot} is {@link ScreenshotRules#clientConditions} with the frame's visible
 * fog end ({@code FogReport.visibleEnd} of the last frame's {@code FogData}) and the server's fog flag, then
 * {@link ScreenshotSpot#find} with that fog end and {@link EntityConfig#screenshotSpot()}. The arm tests drive
 * {@link TheScreenshot} on hooks of their own, as {@code ScreenshotGameTests} does.
 */
public class R5ScreenshotReviewTests {
	private static final long DAY = 24000L;
	private static final Vec3 EYE = new Vec3(0.5, 65.62, 0.5);
	private static final ScreenshotSpot.Ground FLAT = (x, z) -> OptionalDouble.of(64.0);
	private static final ScreenshotSpot.Sight OPEN = (from, to) -> true;

	/** Looking south and level, the default 70-degree field of view, a 1920x1080 frame. */
	private static ScreenshotSpot.View view() {
		return new ScreenshotSpot.View(EYE, 0.0F, 0.0F, 70.0, 1920.0 / 1080.0, 1080);
	}

	/**
	 * The client's two steps for this drawn fog end and fog flag: the conditions, then the spot. (FIX-SHOT: the
	 * conditions take the server's weather fog end too; here it is the drawn one, so nothing reads as pulled in.)
	 */
	private static Optional<ScreenshotSpot.Spot> clientSpot(double fogEnd, boolean fogOn, long seed) {
		EntityConfig config = EntityConfig.get();
		if (ScreenshotRules.clientConditions(true, false, false, true, 15, config.screenshotMinSkyLight, false, false, fogOn, fogEnd, fogEnd)
				.isPresent()) {
			return Optional.empty();
		}
		return ScreenshotSpot.find(view(), fogEnd, config.screenshotSpot(), FLAT, OPEN, RandomSource.create(seed));
	}

	/**
	 * Every sighting stands at least {@code minDistance} (12, D-035) from the player; the screenshot has no floor. With
	 * Blindness outdoors (a suspicious stew of azure bluet) vanilla's fog end is 5 blocks, a fog surge pulls it to
	 * {@code surgeMinFogBlocks} 10, Darkness to 15: each is "fog inside 160", so the conditions pass and he is put
	 * 0.6 to 0.75 of it away, at arm's length (3 to 4 blocks with Blindness), into the one picture of the world.
	 * (FIX-SHOT: render distance alone no longer counts as fog, and Blindness, Darkness or a surge make the arm wait;
	 * the scenario is now one where they went unnoticed, weather fog on and the drawn fog that near, and the floor holds.)
	 */
	@GameTest
	public void theScreenshotNeverPutsHimNearerThanAnySighting(GameTestHelper helper) {
		double floor = EntityConfig.get().minDistance();
		List<String> near = new ArrayList<>();
		for (double fogEnd : new double[] {5.0, 10.0, 15.0}) {
			helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, true, 15, 10, false, false, true, fogEnd, fogEnd).isEmpty(),
					"the scenario: a fog end of " + fogEnd + " passes the conditions");
			for (int seed = 0; seed < 20; seed++) {
				Optional<ScreenshotSpot.Spot> spot = clientSpot(fogEnd, true, seed);
				if (spot.isPresent() && spot.get().distance() < floor) {
					near.add(String.format(Locale.ROOT, "fog end %.0f -> %.2f blocks", fogEnd, spot.get().distance()));
					break;
				}
			}
		}
		helper.assertTrue(near.isEmpty(), "he is put nearer than minDistance " + floor + ": " + near);
		helper.succeed();
	}

	/**
	 * Every sighting stands at most {@code maxSpawnDistance} (192) away, whatever the render distance; the screenshot
	 * has no cap. At render distance 32, a Traces night's dusk fog (0.3 at the night weight 0.55: 0.165, over the flag's
	 * 0.1) turns the server's fog flag on while the fog the client draws ends about 310 blocks out, so the conditions
	 * pass and he stands 186 to 232 blocks away: about 7 pixels tall in a 1080p frame, the once-per-world screenshot
	 * spent on a speck.
	 */
	@GameTest
	public void theScreenshotNeverPutsHimFartherThanAnySighting(GameTestHelper helper) {
		FogLimits.Shape shape = FogLimits.Shape.DEFAULT;
		long night = 18000L;
		double amount = ScreenshotRules.fogAmount(0.3F, night, shape, 0.0, 15);
		boolean fogOn = ScreenshotRules.fogOn(amount, EntityConfig.get().screenshotFogOnAmount);
		double fogEnd = FogLimits.of(32, 32, 0.3F, night, shape).fogEnd();
		helper.assertTrue(fogOn && fogEnd > 160.0, String.format(Locale.ROOT, "the scenario: fog on %s (amount %.3f), fog end %.0f", fogOn, amount, fogEnd));
		double cap = EntityConfig.get().maxSpawnDistance;
		List<String> far = new ArrayList<>();
		for (int seed = 0; seed < 20 && far.isEmpty(); seed++) {
			Optional<ScreenshotSpot.Spot> spot = clientSpot(fogEnd, fogOn, seed);
			if (spot.isPresent() && spot.get().distance() > cap) {
				ScreenshotSpot.Spot s = spot.get();
				ScreenshotSpot.Screen feet = view().project(s.feet());
				ScreenshotSpot.Screen head = view().project(s.feet().add(0.0, ScreenshotSpot.BODY_HEIGHT, 0.0));
				far.add(String.format(Locale.ROOT, "%.0f blocks, %.1f px tall at 1080p", s.distance(), (feet.y() - head.y()) * 1080.0));
			}
		}
		helper.assertTrue(far.isEmpty(), "he is put farther than maxSpawnDistance " + cap + ": " + far);
		helper.succeed();
	}

	/**
	 * Once per world (D-078): "only one screenshot came out". The gate only refuses a player who is armed himself, so
	 * the director arms a second player while the first is armed; both clients then render him into their own PNG, and
	 * the server only learns of the second after the first report (one round trip), when the second image is already on
	 * that player's disk.
	 */
	@GameTest
	public void onlyOnePlayerIsArmedAtATime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		Hooks hooks = new Hooks().with(a, b);
		TheScreenshot shot = new TheScreenshot(hooks);
		ScreenshotCard card = new ScreenshotCard(shot);
		helper.assertTrue(fire(card, a, level) == FireResult.FIRED, "A was not armed");
		Optional<String> whyNotB = shot.gate(b, level);
		FireResult second = fire(card, b, level);
		helper.assertTrue(whyNotB.isPresent() && second != FireResult.FIRED,
				"B can be armed while A is (B's fire: " + second + ", armed now: " + shot.armed().size() + "): two clients may each save him");
		helper.succeed();
	}

	/**
	 * A report is taken from any player in the arms map: also while the arm is paused (he is out, or Ending D's last
	 * minute; the client may still hold "ready" for up to {@link TheScreenshot#TICK_EVERY} ticks plus the trip, so his
	 * copy is drawn while a real figure stands somewhere) and after the arm ran out but before the next look at the
	 * arms. Both record the world's one screenshot.
	 */
	@GameTest
	public void aReportIsTakenOnlyFromAReadyArm(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = mock(helper);
		Hooks paused = new Hooks().with(a);
		TheScreenshot shot = new TheScreenshot(paused);
		shot.arm(a);
		paused.out = true;
		shot.check(server);
		helper.assertTrue(shot.told(a.getUUID()).map(s -> !s.armed()).orElse(false), "the scenario: the arm is paused");
		TheScreenshot.Report whilePaused = shot.report(a);

		Hooks late = new Hooks().with(a);
		TheScreenshot expired = new TheScreenshot(late);
		expired.arm(a);
		late.dayTicks += 2 * DAY + 5; // ran out 5 ticks ago; the next look at the arms is up to 10 ticks away
		TheScreenshot.Report afterRunOut = expired.report(a);
		helper.assertTrue(whilePaused == TheScreenshot.Report.IGNORED && afterRunOut == TheScreenshot.Report.IGNORED,
				"a paused arm's report: " + whilePaused + " (recorded " + paused.data.screenshotTaken() + "); a run-out arm's report: " + afterRunOut
						+ " (recorded " + late.data.screenshotTaken() + ")");
		helper.succeed();
	}

	/**
	 * An arm outlives the story. Endings A, C and D make the world quiet for good ({@code director:silence_forever};
	 * D: "the story ends: nothing more is armed"; A: "the world goes quiet and vanilla again"), and only Ending D's last
	 * minute pauses an arm, so an arm set in the 2 in-game days before an ending is ready again after it: the next F2
	 * in fog (or at a render distance of 9 or less, or in rain) shows him after he is gone for good. The card's gate
	 * fits then too. The live flag is set and put back inside this one call.
	 */
	@GameTest
	public void anArmEndsWithTheStory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		Hooks hooks = new Hooks().with(a, b);
		hooks.liveLastMinute = server;
		TheScreenshot shot = new TheScreenshot(hooks);
		shot.arm(a);
		HerobrineState state = HerobrineState.get(server);
		String silence = "director:silence_forever";
		boolean had = state.hasFlag(silence);
		boolean readyAfter;
		Optional<String> gateAfter;
		try {
			state.setFlag(silence, true);
			shot.check(server);
			readyAfter = shot.told(a.getUUID()).map(ScreenshotStatePayload::armed).orElse(false);
			gateAfter = shot.gate(b, level);
		} finally {
			state.setFlag(silence, had);
		}
		helper.assertFalse(readyAfter, "A's arm is still ready once the world is quiet for good");
		helper.assertTrue(gateAfter.isPresent(), "the card can arm B once the world is quiet for good");
		helper.succeed();
	}

	// --- helpers, as ScreenshotGameTests has them ---

	private static ServerPlayer mock(GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	private static FireResult fire(EventCard card, ServerPlayer player, ServerLevel level) {
		return card.fire(new FireContext(player, level, false, false, RandomSource.create(5)));
	}

	/** A test's hooks; Ending D's last minute is read from the live state when {@link #liveLastMinute} is set. */
	static final class Hooks implements TheScreenshot.Hooks {
		final EntityData data = new EntityData();
		final Map<UUID, ServerPlayer> online = new HashMap<>();
		final List<ScreenshotStatePayload> sent = new ArrayList<>();
		boolean out;
		long dayTicks = 3 * DAY + 1000;
		MinecraftServer liveLastMinute;

		Hooks with(ServerPlayer... players) {
			for (ServerPlayer player : players) {
				online.put(player.getUUID(), player);
			}
			return this;
		}

		@Override
		public EntityData data(MinecraftServer server) {
			return data;
		}

		@Override
		public Stage stage(MinecraftServer server) {
			return Stage.TRACES;
		}

		@Override
		public boolean lastMinute(MinecraftServer server) {
			return liveLastMinute != null && TheScreenshot.LIVE_HOOKS.lastMinute(liveLastMinute);
		}

		@Override
		public boolean heIsOut(MinecraftServer server) {
			return out;
		}

		@Override
		public long dayTicks(MinecraftServer server) {
			return dayTicks;
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(online.get(player));
		}

		@Override
		public boolean fogOn(ServerPlayer player) {
			return true;
		}

		@Override
		public boolean send(ServerPlayer player, ScreenshotStatePayload payload) {
			sent.add(payload);
			return true;
		}
	}
}
