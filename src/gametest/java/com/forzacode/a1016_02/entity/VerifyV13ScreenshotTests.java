package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.core.Pacing;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * VERIFY-3 (v1.3): the screenshot against the one-sighting-per-day rule (DESIGN, Sightings: "Never two in one in-game
 * day"; D-078: the screenshot "counts as that player's sighting for the day"). The arm lasts 2 in-game days and only
 * pauses while he is out; the director counted its SIGHTING on the day it armed, and entity's own day record
 * ({@code EntityData.dayAllows}, world-wide, the one {@code SightingGates} checks) only when the report comes.
 */
public class VerifyV13ScreenshotTests {
	private static final long DAY = 24000L;

	/**
	 * A figure seen today (any player's sighting, recorded in entity's day record), gone again; then the armed player's
	 * F2 in fog the same in-game day saves him a second time that day: the report is recorded and the day holds two.
	 * Expected: the arm waits (paused, or the report not taken) while today already had a sighting, as every sighting
	 * card's gate does ("already one today").
	 */
	@GameTest
	public void verifyTheScreenshotIsNeverTheDaysSecondSighting(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		R5ScreenshotReviewTests.Hooks hooks = new R5ScreenshotReviewTests.Hooks().with(a);
		TheScreenshot shot = new TheScreenshot(hooks);
		shot.arm(a); // day 3: the director counted A's sighting for day 3
		hooks.dayTicks += DAY; // day 4, the arm's second day
		long today = Math.floorDiv(hooks.dayTicks, DAY);
		hooks.data.recordSighting("COW", GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), today); // he was seen today, and left
		int maxPerDay = new Pacing().sightingsPerDayMax;
		helper.assertTrue(!hooks.data.dayAllows(today, maxPerDay), "the scenario: today already had its sighting");
		shot.check(server);
		boolean readyAfterASighting = shot.told(a.getUUID()).map(ScreenshotStatePayload::armed).orElse(false);
		TheScreenshot.Report report = shot.report(a);
		helper.assertTrue(!readyAfterASighting || report != TheScreenshot.Report.RECORDED,
				"the screenshot shows him on a day that already had a sighting: arm ready " + readyAfterASighting + ", report " + report
						+ ", sightings on day " + today + ": " + hooks.data.countOnLastDay() + " (max " + maxPerDay + ")");
		helper.succeed();
	}
}
