package com.forzacode.a1016_02.entity;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.atmosphere.WeatherFog;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.ModConfig;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * FIX-PLAIN, from Mariano's playtest of v1.3: {@code /a1016 fire the_screenshot} answered FIRED and F2 saved a plain
 * picture, twice, on a clear Proximity night at 16 chunks. Two things kept him out. On the server, the arm was paused
 * all along: that in-game day (day 14) already had 12 sightings (the {@code /a1016 entity spawn} tries a few minutes
 * before), and nothing said so. On the client, a normal night's fog is too far out for the old band: the dusk fog at 16
 * chunks ends about 142 blocks out, 0.6 of it is 85, and in a 1080-pixel frame at 70 degrees he is 24 pixels tall only
 * up to about 80 blocks, so the band was empty and the arm waited forever. Now the band slides in to his reach, the
 * pause carries its reason, and an armed F2 that stays plain says why. The drawn fog here is atmosphere's own math on
 * vanilla's overworld fog, as {@code ClientAtmosphere.applyFog} draws it.
 */
public class ScreenshotPlainTests {
	private static final long DAY = 24000L;
	private static final Vec3 EYE = new Vec3(0.5, 65.62, 0.5);
	private static final ScreenshotSpot.Ground FLAT = (x, z) -> OptionalDouble.of(64.0);
	private static final ScreenshotSpot.Sight OPEN = (from, to) -> true;
	private static final ScreenshotSpot.Settings S = ScreenshotSpot.Settings.DEFAULT;
	/** Proximity's dusk fog level (atmosphere's {@code duskFogByStage[2]}) and a clear night's time of day. */
	private static final float PROXIMITY_DUSK = 0.45F;
	private static final long NIGHT = 18000L;

	/** Looking south and a little down, this field of view, a 16:10 frame this many pixels tall. */
	private static ScreenshotSpot.View view(double fov, int height) {
		return new ScreenshotSpot.View(EYE, 0.0F, 8.0F, fov, 1.6, height);
	}

	/**
	 * The fog the client draws for a dusk fog level at this time of day and render distance, no spell, no surge: vanilla's
	 * overworld fog (environmental 0 to 1024, the render-distance fog over the last tenth of the render distance) shaped
	 * by atmosphere's {@code WeatherFog.shape} with the dusk amount and its haze ({@code DuskLevel}: the level over
	 * {@code duskHazeFullLevel}, at most 1), as {@code ClientAtmosphere.applyFog} does once the level has eased in.
	 */
	static ScreenshotSpot.Fog drawnDuskFog(float level, long timeOfDay, int chunks) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		FogLimits.Shape shape = cfg.fogShape();
		double blocks = chunks * 16.0;
		double span = Math.clamp(blocks / 10.0, 4.0, 64.0);
		Curves.Fog f = new Curves.Fog().set(0.0, 1024.0, blocks - span, blocks, blocks, 2048.0);
		double presence = cfg.duskHazeFullLevel > 0.0 ? Math.min(1.0, level / cfg.duskHazeFullLevel) : 1.0;
		WeatherFog.shape(f, Curves.duskAmount(level, timeOfDay, shape), Curves.duskHaze(timeOfDay, shape) * presence, 0.0, 0.0, cfg);
		// The client hands the shader floats.
		return new ScreenshotSpot.Fog((float) f.environmentalStart, (float) f.environmentalEnd, (float) f.renderDistanceStart, (float) f.renderDistanceEnd);
	}

	/** The band before FIX-PLAIN: 0.6 to 0.75 of the fog end, refused when his reach or the cap is nearer than 0.6 of it. */
	private static ScreenshotSpot.Band oldBand(ScreenshotSpot.View view, ScreenshotSpot.Fog fog) {
		double end = fog.visibleEnd();
		double start = fog.visibleStart();
		double fogged = start < end ? start + S.minFog() * (end - start) : end;
		double near = Math.max(S.nearShare() * end, Math.max(S.minDistance(), fogged));
		double far = Math.min(S.farShare() * end, Math.min(S.maxDistance(), view.reach(S.minPixels(), S.edgeMin())));
		return new ScreenshotSpot.Band(near, far);
	}

	/**
	 * The playtest's scene on the client: Proximity's clear night at 16 chunks ends the fog where the server says (about
	 * 142 blocks), so nothing reads as a surge; the old band was empty in a 1080-pixel frame at 70 degrees (0.6 of the
	 * fog end, 85, past his reach of about 80); now the band ends at his reach and starts at 0.8 of it, and every spot on
	 * flat ground is 24 pixels tall or more, 12 blocks or more away, never past the reach, and at least a tenth fogged
	 * (here about half: the environmental fog runs from the camera to the dusk fog's end).
	 */
	@GameTest
	public void aClearProximityNightAtSixteenChunksHasASpot(GameTestHelper helper) {
		ScreenshotSpot.Fog fog = drawnDuskFog(PROXIMITY_DUSK, NIGHT, 16);
		double weather = FogLimits.of(16, 16, PROXIMITY_DUSK, NIGHT, AtmosphereConfig.get().fogShape()).fogEnd();
		helper.assertTrue(Math.abs(fog.visibleEnd() - weather) < 1.0E-3 && weather > 135.0 && weather < 150.0,
				"the scenario: the drawn fog end " + fog.visibleEnd() + ", the weather's " + weather);
		helper.assertFalse(ScreenshotRules.pulledIn(fog.visibleEnd(), weather), "a normal night reads as a surge");
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, true, 15, 10, false, false, true, fog.visibleEnd(), weather).isEmpty(),
				"a normal night fails the conditions");

		ScreenshotSpot.View hd = view(70.0, 1080);
		double reach = hd.reach(S.minPixels(), S.edgeMin());
		helper.assertTrue(oldBand(hd, fog).isEmpty() && reach < S.nearShare() * weather, String.format(Locale.ROOT,
				"the old band was not empty: %s, reach %.1f, 0.6 of the fog end %.1f", oldBand(hd, fog), reach, S.nearShare() * weather));
		ScreenshotSpot.Band band = ScreenshotSpot.band(hd, fog, S);
		helper.assertTrue(!band.isEmpty() && Math.abs(band.far() - reach) < 1.0E-9 && Math.abs(band.near() - 0.8 * reach) < 1.0E-6,
				"the band does not slide in to his reach " + reach + ": " + band);
		for (int seed = 0; seed < 30; seed++) {
			ScreenshotSpot.Search search = ScreenshotSpot.search(hd, fog, S, FLAT, OPEN, RandomSource.create(seed));
			helper.assertTrue(search.spot().isPresent(), "no spot on a clear Proximity night, seed " + seed + ": " + search.why());
			ScreenshotSpot.Spot s = search.spot().get();
			helper.assertTrue(s.pixels() >= S.minPixels() && s.distance() >= S.minDistance() && s.distance() <= reach + 1.0 && s.fog() >= S.minFog()
					&& s.fog() > 0.3 && search.why().isEmpty(), "spot " + s);
		}
		helper.succeed();
	}

	/**
	 * His distance follows the frame: at Mariano's Retina picture (3024x1898) he may stand in the plain band (0.6 to
	 * 0.75 of the fog end); a wider field of view or a smaller picture brings him nearer, never under 12 blocks and
	 * never under 24 pixels; a picture so small that 24 pixels means nearer than 12 has no spot, and says so.
	 */
	@GameTest
	public void hisDistanceFollowsTheFieldOfViewAndThePicture(GameTestHelper helper) {
		ScreenshotSpot.Fog fog = drawnDuskFog(PROXIMITY_DUSK, NIGHT, 16);
		double end = fog.visibleEnd();
		ScreenshotSpot.View retina = new ScreenshotSpot.View(EYE, 0.0F, 8.0F, 70.0, 3024.0 / 1898.0, 1898);
		ScreenshotSpot.Band plain = ScreenshotSpot.band(retina, fog, S);
		helper.assertTrue(Math.abs(plain.near() - 0.6 * end) < 1.0E-6 && Math.abs(plain.far() - 0.75 * end) < 1.0E-6, "the Retina band: " + plain);
		double lastFar = Double.POSITIVE_INFINITY;
		for (double fov : new double[] {70.0, 90.0, 110.0}) {
			for (int height : new int[] {1080, 720, 480}) {
				ScreenshotSpot.View v = view(fov, height);
				ScreenshotSpot.Band band = ScreenshotSpot.band(v, fog, S);
				helper.assertTrue(!band.isEmpty() && band.near() >= S.minDistance() && band.far() <= v.reach(S.minPixels(), S.edgeMin()) + 1.0E-9,
						"fov " + fov + ", " + height + " px: " + band);
				boolean found = false;
				for (int seed = 0; seed < 10; seed++) {
					Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(v, fog, S, FLAT, OPEN, RandomSource.create(seed));
					helper.assertTrue(spot.map(s -> v.pixelsTall(s.feet()) >= S.minPixels() && s.distance() >= S.minDistance()).orElse(true),
							"fov " + fov + ", " + height + " px: " + spot);
					found |= spot.isPresent();
				}
				helper.assertTrue(found, "no spot at fov " + fov + ", " + height + " px in " + band);
				if (height == 1080) {
					helper.assertTrue(band.far() < lastFar, "a wider field of view does not bring him nearer: " + band);
					lastFar = band.far();
				}
			}
		}
		ScreenshotSpot.View tiny = view(110.0, 200);
		ScreenshotSpot.Search none = ScreenshotSpot.search(tiny, fog, S, FLAT, OPEN, RandomSource.create(1));
		helper.assertTrue(none.spot().isEmpty() && none.band().isEmpty() && none.why().startsWith("no distance fits") && none.why().contains("reach"),
				"a 200-pixel picture: " + none);
		helper.succeed();
	}

	/**
	 * Looking down from a height he is foreshortened: a level view's reach is too far. From 36 blocks above flat ground
	 * (looking 25 degrees down) he is under 24 pixels everywhere in the band, so a try walks nearer along its column and
	 * finds him nearer than the band, still 24 pixels or more and never under the floor; from 46 blocks up at the creative flying field of view (77 degrees, the
	 * dev client's) he is under 24 pixels everywhere, so there is no spot and the reason says so.
	 */
	@GameTest
	public void fromAHillHeStandsNearerAndNeverSmall(GameTestHelper helper) {
		ScreenshotSpot.Fog fog = drawnDuskFog(PROXIMITY_DUSK, NIGHT, 16);
		ScreenshotSpot.View hill = new ScreenshotSpot.View(new Vec3(0.5, 100.62, 0.5), 0.0F, 25.0F, 70.0, 1.6, 1080);
		ScreenshotSpot.Band band = ScreenshotSpot.band(hill, fog, S);
		double floor = Math.max(S.minDistance(), fog.coversFrom(S.minFog()));
		int found = 0;
		for (int seed = 0; seed < 20; seed++) {
			ScreenshotSpot.Search search = ScreenshotSpot.search(hill, fog, S, FLAT, OPEN, RandomSource.create(seed));
			if (search.spot().isPresent()) {
				ScreenshotSpot.Spot s = search.spot().get();
				helper.assertTrue(hill.pixelsTall(s.feet()) >= S.minPixels() && s.distance() < band.near() && s.distance() >= floor - 1.0E-9
						&& s.fog() >= S.minFog(), "from the hill: " + s + " in " + band);
				found++;
			}
		}
		helper.assertTrue(found >= 10, "from the hill only " + found + " of 20 found a spot");
		ScreenshotSpot.View high = new ScreenshotSpot.View(new Vec3(0.5, 111.62, 0.5), 0.0F, 20.0F, 77.0, 16.0 / 9.0, 1080);
		ScreenshotSpot.Search none = ScreenshotSpot.search(high, fog, S, FLAT, OPEN, RandomSource.create(3));
		helper.assertTrue(none.spot().isEmpty() && none.why().contains("too small in the picture"), "from 46 blocks up: " + none);
		helper.succeed();
	}

	/**
	 * Fogged by each fog on its own: the drawn fog covers a point with the larger of the environmental and the
	 * render-distance fog, so where their ends differ by a hair (floats) the band never waits for the later start of the
	 * one with the nearer end. The old band took that start (about 106 blocks here) and left one block of band.
	 */
	@GameTest
	public void theFogCoversHimFromTheNearerOfTheTwoFogs(GameTestHelper helper) {
		ScreenshotSpot.Fog fog = drawnDuskFog(PROXIMITY_DUSK, NIGHT, 16);
		double end = fog.visibleEnd();
		ScreenshotSpot.Fog hair = new ScreenshotSpot.Fog(0.0, end + 1.0E-3, fog.renderDistanceStart(), end);
		helper.assertTrue(hair.visibleStart() > 0.6 * end && Math.abs(hair.coversFrom(0.1) - 0.1 * (end + 1.0E-3)) < 1.0E-9,
				"the scenario: visible start " + hair.visibleStart() + ", covered from " + hair.coversFrom(0.1));
		ScreenshotSpot.View retina = new ScreenshotSpot.View(EYE, 0.0F, 8.0F, 70.0, 3024.0 / 1898.0, 1898);
		helper.assertTrue(oldBand(retina, hair).far() - oldBand(retina, hair).near() < 2.0, "the old band was wide: " + oldBand(retina, hair));
		ScreenshotSpot.Band band = ScreenshotSpot.band(retina, hair, S);
		helper.assertTrue(Math.abs(band.near() - 0.6 * end) < 1.0E-3 && Math.abs(band.far() - 0.75 * end) < 1.0E-3, "the band: " + band);
		helper.assertTrue(new ScreenshotSpot.Fog(0.0, 1024.0, 230.4, 256.0).coversFrom(0.1) == 102.4
				&& new ScreenshotSpot.Fog(1000.0, 2000.0, 10.0, 20.0).coversFrom(0.5) == 15.0
				&& Double.isNaN(new ScreenshotSpot.Fog(0.0, Double.NaN, 0.0, -1.0).coversFrom(0.1)), "coversFrom");
		helper.succeed();
	}

	/**
	 * The playtest's server side: 12 sightings on day 14 (the debug spawns), then {@code /a1016 fire the_screenshot}
	 * arms the player FIRED but paused, and the state carries why ("today (day 14) already had 12 sightings ... it waits
	 * for day 15"); a report that day is not taken; on day 15 the state says ready, with no reason. He being out says so
	 * too, and comes first.
	 */
	@GameTest
	public void aPausedArmSaysWhy(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ScreenshotGameTests.Hooks hooks = new ScreenshotGameTests.Hooks().with(a);
		hooks.fog = true;
		hooks.dayTicks = 14 * DAY + 12000;
		for (int i = 0; i < 12; i++) {
			hooks.data.recordSighting(i % 2 == 0 ? "between_trunks" : "cow", GlobalPos.of(Level.OVERWORLD, new BlockPos(i * 40, 64, 0)), 14);
		}
		TheScreenshot shot = new TheScreenshot(hooks);
		helper.assertTrue(shot.arm(a) == FireResult.FIRED, "the arm");
		ScreenshotStatePayload told = shot.told(a.getUUID()).orElseThrow();
		int max = ModConfig.pacing().sightingsPerDayMax;
		String expected = ScreenshotRules.paused(false, 14, 14, 12, max).orElseThrow();
		helper.assertTrue(!told.armed() && told.paused().equals(expected) && expected.contains("day 14") && expected.contains("12 sightings")
				&& expected.contains("day 15"), "told " + told);
		helper.assertTrue(shot.paused(server).equals(Optional.of(expected)), "the command's reason: " + shot.paused(server));
		helper.assertTrue(TheScreenshot.describe(told).startsWith("paused: today (day 14)"), "described: " + TheScreenshot.describe(told));
		helper.assertTrue(ScreenshotRules.plainLine("paused by the server: " + told.paused()).startsWith("[a1016] screenshot armed but plain: paused"),
				"the client's line");
		hooks.out = true;
		shot.check(server);
		helper.assertTrue(shot.told(a.getUUID()).map(s -> s.paused().startsWith("he is out")).orElse(false), "he is out first: " + shot.told(a.getUUID()));
		hooks.out = false;
		helper.assertTrue(shot.report(a) == TheScreenshot.Report.IGNORED && !hooks.data.screenshotTaken(), "a report on day 14 was taken");

		TheScreenshot next = new TheScreenshot(hooks);
		next.arm(a);
		hooks.dayTicks = 15 * DAY + 100;
		next.check(server);
		ScreenshotStatePayload ready = next.told(a.getUUID()).orElseThrow();
		helper.assertTrue(ready.armed() && ready.paused().isEmpty() && TheScreenshot.describe(ready).startsWith("ready"), "day 15: " + ready);
		helper.assertTrue(ScreenshotRules.paused(false, 15, 14, 12, max).isEmpty() && ScreenshotRules.paused(false, 14, 14, 0, max).isEmpty(),
				"a day with room is paused");
		helper.assertTrue(ScreenshotRules.plainLine("").equals(ScreenshotRules.PLAIN + "no reason given"), "an empty reason");
		helper.succeed();
	}

	/**
	 * A search that finds nothing says why: no ground (64 tries, each "no ground to stand on"), every head hidden, or a
	 * fog that only starts past his reach; and a found spot says nothing. The reason the payload carries round-trips and
	 * a state without one has "".
	 */
	@GameTest
	public void aSearchThatFindsNothingSaysWhy(GameTestHelper helper) {
		ScreenshotSpot.Fog fog = drawnDuskFog(PROXIMITY_DUSK, NIGHT, 16);
		ScreenshotSpot.View hd = view(70.0, 1080);
		ScreenshotSpot.Search noGround = ScreenshotSpot.search(hd, fog, S, (x, z) -> OptionalDouble.empty(), OPEN, RandomSource.create(2));
		helper.assertTrue(noGround.spot().isEmpty() && noGround.why().startsWith("no spot in 64 tries") && noGround.why().contains("no ground to stand on"),
				"no ground: " + noGround.why());
		ScreenshotSpot.Search hidden = ScreenshotSpot.search(hd, fog, S, FLAT, (from, to) -> false, RandomSource.create(2));
		helper.assertTrue(hidden.spot().isEmpty() && hidden.why().contains("his head or chest hidden"), "hidden: " + hidden.why());
		ScreenshotSpot.Search late = ScreenshotSpot.search(hd, new ScreenshotSpot.Fog(0.0, 2000.0, 180.0, 200.0), S, FLAT, OPEN, RandomSource.create(2));
		helper.assertTrue(late.spot().isEmpty() && late.why().startsWith("no distance fits"), "a late fog: " + late.why());
		ScreenshotSpot.Search none = ScreenshotSpot.search(hd, new ScreenshotSpot.Fog(Double.NaN, Double.NaN, Double.NaN, Double.NaN), S, FLAT, OPEN,
				RandomSource.create(2));
		helper.assertTrue(none.why().equals("no visible fog end in the frame"), "no fog end: " + none.why());
		ScreenshotSpot.Search found = ScreenshotSpot.search(hd, fog, S, FLAT, OPEN, RandomSource.create(2));
		helper.assertTrue(found.spot().isPresent() && found.why().isEmpty() && ScreenshotSpot.emptyBand(hd, fog, S).isEmpty(), "found: " + found);

		ScreenshotStatePayload paused = new ScreenshotStatePayload(false, true, 142.5F, 3, "he is out somewhere");
		ByteBuf buf = Unpooled.buffer();
		try {
			ScreenshotStatePayload.CODEC.encode(buf, paused);
			ScreenshotStatePayload back = ScreenshotStatePayload.CODEC.decode(buf);
			helper.assertTrue(back.equals(paused) && buf.readableBytes() == 0, "round trip: " + back);
		} finally {
			buf.release();
		}
		helper.assertTrue(new ScreenshotStatePayload(true, false, 1.0F, 1).paused().isEmpty() && ScreenshotStatePayload.OFF.paused().isEmpty()
				&& new ScreenshotStatePayload(false, false, 0.0F, 1, "x".repeat(400)).paused().length() == ScreenshotStatePayload.MAX_PAUSED, "the reason");
		helper.succeed();
	}
}
