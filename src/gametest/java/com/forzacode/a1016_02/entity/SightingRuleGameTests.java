package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Sighting gates and spawn spots. {@link EntityGameTests} extends this so they run under its registered entrypoint. */
public class SightingRuleGameTests extends FogAndRunGameTests {
	private static final double NEAR = 3;
	private static final double CONE = 160;

	/** Atmosphere's default: fog end at full dusk fog. */
	private static final double DUSK_MIN = 24;

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-6;
	}

	@GameTest
	public void fogBandNeverCloserThanTheMinimum(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		double min = config.minDistance();
		helper.assertTrue(min == 12, "the minimum distance defaults to 12 (D-035): " + min);
		for (int chunks = 2; chunks <= 32; chunks++) {
			for (double dusk : new double[] {0.0, 0.5, 1.0}) {
				for (int sim : new int[] {0, 2, 6, 12}) {
					for (boolean close : new boolean[] {false, true}) {
						for (double reported : new double[] {Double.NaN, 6, 14, 20, 30, 55, 120, 400}) {
							FogEdge edge = FogEdge.compute(chunks, 32, sim, dusk, DUSK_MIN, reported, close, config);
							String what = "chunks=" + chunks + " dusk=" + dusk + " sim=" + sim + " close=" + close + " reported=" + reported + " -> " + edge;
							helper.assertTrue(edge.inner() >= min, "band starts closer than " + min + ": " + what);
							helper.assertTrue(edge.outer() >= edge.inner() + 2.0 - 1.0E-6, "band thinner than 2 blocks: " + what);
							helper.assertTrue(edge.limit() == (Double.isNaN(reported) ? edge.estimate() : reported) && edge.fromClient() == !Double.isNaN(reported),
									"the report does not decide the fog end: " + what);
							helper.assertTrue(!edge.seeable() || edge.outer() <= edge.limit() - config.edgeMarginBlocks + 1.0E-6,
									"a seeable band reaches into the fog: " + what);
							helper.assertTrue(edge.seeable() || edge.limit() - config.edgeMarginBlocks < min + 2.0 + 1.0E-6,
									"fog that leaves room past the minimum counted as too thick: " + what);
							helper.assertTrue(edge.estimate() <= edge.renderLimit() + 1.0E-6, "dusk fog pushed the fog out: " + what);
							helper.assertTrue(sim <= 0 || edge.outer() <= Math.max(FogEdge.tickingReach(sim), min + 2.0) + 1.0E-6,
									"band reaches the edge of the ticking range: " + what);
							helper.assertTrue(!close || edge.outer() <= Math.max(config.closeMaxDistance, min + 2.0) + 1.0E-6, "close band too far: " + what);
						}
					}
				}
			}
		}
		// 12 chunks, no dusk fog, no report yet: 0.55 to 0.75 of the 192-block fog end.
		FogEdge vanilla = FogEdge.compute(12, 12, 0, 0.0, DUSK_MIN, Double.NaN, false, config);
		helper.assertTrue(near(vanilla.inner(), 0.55 * 192) && near(vanilla.outer(), 0.75 * 192) && !vanilla.fromClient(),
				"12 chunks should put him 106 to 144 blocks out: " + vanilla);
		FogEdge client = FogEdge.compute(6, 12, 0, 0.0, DUSK_MIN, Double.NaN, false, config);
		helper.assertTrue(client.chunks() == 6 && client.outer() <= 0.75 * 96 + 1.0E-6, "the client's smaller view distance wins: " + client);
		// The estimate follows atmosphere's dusk fog: half of it closes 192 in to sqrt(192 * 24), all of it to 24.
		FogEdge half = FogEdge.compute(12, 12, 0, 0.5, DUSK_MIN, Double.NaN, false, config);
		helper.assertTrue(near(half.limit(), Math.sqrt(192 * DUSK_MIN)) && half.outer() < half.limit(), "half dusk fog: " + half);
		FogEdge dusk = FogEdge.compute(12, 12, 0, 1.0, DUSK_MIN, Double.NaN, false, config);
		helper.assertTrue(near(dusk.limit(), DUSK_MIN) && near(dusk.inner(), 0.55 * DUSK_MIN) && dusk.seeable(), "full dusk fog: " + dusk);
		// The client's report wins over the estimate. Mariano's playtest: dusk fog 0.6 at dusk draws the fog end at
		// 192 * (24 / 192)^0.6, about 55 blocks; he stands 30 to 41 blocks out, the close one 19 to 28.
		double fogEnd = 192 * Math.pow(DUSK_MIN / 192, 0.6);
		FogEdge reported = FogEdge.compute(12, 12, 0, 0.0, DUSK_MIN, fogEnd, false, config);
		helper.assertTrue(reported.fromClient() && near(reported.limit(), fogEnd) && near(reported.inner(), 0.55 * fogEnd)
				&& near(reported.outer(), 0.75 * fogEnd), "the reported fog end is not used: " + reported);
		FogEdge reportedClose = FogEdge.compute(12, 12, 0, 0.0, DUSK_MIN, fogEnd, true, config);
		helper.assertTrue(near(reportedClose.inner(), 0.35 * fogEnd) && near(reportedClose.outer(), 0.50 * fogEnd), "close in dusk fog: " + reportedClose);
		// Fog so thick (a surge, blindness, under water) that 12 blocks out is past it: no sighting can be seen.
		helper.assertFalse(FogEdge.compute(12, 12, 0, 0.0, DUSK_MIN, 14.0, false, config).seeable(), "a sighting in 14-block fog");
		helper.succeed();
	}

	@GameTest
	public void closeBandIsAShareOfTheFogClampedToItsBlocks(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		helper.assertTrue(config.closeFractionMin == 0.35 && config.closeFractionMax == 0.50 && config.closeMinDistance == 16
				&& config.closeMaxDistance == 28 && config.normalFractionMin == 0.55 && config.normalFractionMax == 0.75, "D-035 defaults");
		// Inside the clamp: plain shares of the fog end.
		double[] mid = FogEdge.band(55.0, true, 0, config);
		helper.assertTrue(near(mid[0], 0.35 * 55) && near(mid[1], 0.50 * 55), "55-block fog: " + mid[0] + ".." + mid[1]);
		// Clear weather: both shares past 28, so the band is the clamp's last 2 blocks.
		double[] clear = FogEdge.band(192.0, true, 0, config);
		helper.assertTrue(near(clear[0], 26) && near(clear[1], 28), "clear: " + clear[0] + ".." + clear[1]);
		// Thick fog: both shares under 16, so the band is the clamp's first 2 blocks.
		double[] thick = FogEdge.band(30.0, true, 0, config);
		helper.assertTrue(near(thick[0], 16) && near(thick[1], 18), "30-block fog: " + thick[0] + ".." + thick[1]);
		// Thicker still: the margin inside the fog end pulls it in, the minimum distance holds.
		double[] thicker = FogEdge.band(18.0, true, 0, config);
		helper.assertTrue(near(thicker[0], 14) && near(thicker[1], 16), "18-block fog: " + thicker[0] + ".." + thicker[1]);
		// The normal band never takes the clamp.
		double[] normal = FogEdge.band(55.0, false, 0, config);
		helper.assertTrue(near(normal[0], 0.55 * 55) && near(normal[1], 0.75 * 55), "normal: " + normal[0] + ".." + normal[1]);
		// Everything follows the tunables, swapped ends included.
		config.closeFractionMin = 0.4;
		config.closeFractionMax = 0.25;
		config.closeMinDistance = 22;
		config.closeMaxDistance = 12;
		double[] tuned = FogEdge.band(55.0, true, 0, config);
		helper.assertTrue(near(tuned[0], 0.25 * 55) && near(tuned[1], 22), "tuned: " + tuned[0] + ".." + tuned[1]);
		// minDistance never goes under 8, whatever the file says.
		config.minDistance = 4;
		helper.assertTrue(config.minDistance() == EntityConfig.MIN_DISTANCE_FLOOR, "minDistance under 8: " + config.minDistance());
		config.minDistance = Double.NaN;
		helper.assertTrue(config.minDistance() == EntityConfig.MIN_DISTANCE_FLOOR, "a NaN minDistance");
		config.minDistance = 20;
		double[] far = FogEdge.band(55.0, true, 0, config);
		helper.assertTrue(far[0] >= 20, "a tuned minDistance is not held: " + far[0]);
		helper.succeed();
	}

	@GameTest
	public void approachCountsOnlyRealApproaches(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		double step = config.approachStepBlocks;
		// One block forward, the bug that sent him running: nothing.
		Approach one = new Approach();
		one.update(30.0, step);
		helper.assertTrue(one.update(29.0, step) == 0.0, "a single step counted");
		// Shuffling forward and back for a long time: nothing.
		Approach shuffle = new Approach();
		for (int i = 0; i < 200; i++) {
			shuffle.update(i % 2 == 0 ? 30.0 : 29.0, step);
		}
		helper.assertTrue(shuffle.closed() == 0.0, "shuffling counted " + shuffle.closed());
		// Strafing in a straight line past him at 30 blocks: the distance only grows.
		Approach strafe = new Approach();
		for (int i = 0; i <= 100; i++) {
			strafe.update(Math.hypot(30.0, i * 0.2), step);
		}
		helper.assertTrue(strafe.closed() == 0.0, "strafing counted " + strafe.closed());
		// Walking straight at him, 0.2 blocks a tick: counts, and reaches 10 blocks after about 10 blocks.
		Approach walk = new Approach();
		double d = 40.0;
		walk.update(d, step);
		int ticks = 0;
		while (walk.update(d -= 0.2, step) < config.approachBlocks && ticks < 1000) {
			ticks++;
		}
		helper.assertTrue(walk.closed() >= config.approachBlocks && 40.0 - d <= config.approachBlocks + step + 1.0E-6, "walking in: " + (40.0 - d));
		// Walk in 6, back off 6, walk in 6 again: 12 closed, both walks count.
		Approach twice = new Approach();
		for (double x : new double[] {40, 37, 34, 37, 40, 37, 34}) {
			twice.update(x, step);
		}
		helper.assertTrue(Math.abs(twice.closed() - 12.0) < 1.0E-6, "in, back, in again: " + twice.closed());
		helper.succeed();
	}

	@GameTest
	public void nothingButFleeEndsASightingBeforeMinSeen(GameTestHelper helper) {
		long minSeen = 60;
		// Never seen: only the flee distance ends it.
		helper.assertTrue(SightingRules.endCause(false, true, true, -1, minSeen) == SightingRules.EndCause.NONE, "ended unseen");
		helper.assertTrue(SightingRules.endCause(true, false, false, -1, minSeen) == SightingRules.EndCause.FLEE, "no flee unseen");
		// Seen for less than minSeen: a finished stare or approach waits, the flee distance does not.
		helper.assertTrue(SightingRules.endCause(false, true, true, minSeen - 1, minSeen) == SightingRules.EndCause.NONE, "ended before minSeen");
		helper.assertTrue(SightingRules.endCause(true, true, true, 0, minSeen) == SightingRules.EndCause.FLEE, "flee waited for minSeen");
		// From minSeen on: the stare first, then the approach.
		helper.assertTrue(SightingRules.endCause(false, true, true, minSeen, minSeen) == SightingRules.EndCause.STARE, "stare at minSeen");
		helper.assertTrue(SightingRules.endCause(false, false, true, minSeen + 5, minSeen) == SightingRules.EndCause.APPROACH, "approach after minSeen");
		helper.assertTrue(SightingRules.endCause(false, false, false, minSeen + 5, minSeen) == SightingRules.EndCause.NONE, "ended for nothing");
		// The out-of-view removals wait for minSeen too, unless he fled.
		helper.assertFalse(SightingRules.mayEndOutOfView(true, minSeen - 1, minSeen, false), "removed out of view before minSeen");
		helper.assertTrue(SightingRules.mayEndOutOfView(true, minSeen - 1, minSeen, true), "kept after fleeing");
		helper.assertTrue(SightingRules.mayEndOutOfView(true, minSeen, minSeen, false) && SightingRules.mayEndOutOfView(false, -1, minSeen, false),
				"kept after minSeen, or never seen");
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void spawnSpotIsOutOfViewAndNeverTooClose(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player viewer = helper.makeMockServerPlayer(GameType.SURVIVAL);
		viewer.snapTo(helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)), 0.0F, 0.0F); // looking south (+z)
		loadAround(level, viewer.blockPosition(), 3);
		List<TraceService.Viewer> viewers = List.of(TraceService.Viewer.of(viewer, 8));
		Predicate<AABB> hidden = box -> TraceService.isOutOfView(level, box, viewers, NEAR, CONE);
		double min = new EntityConfig().minDistance();

		// Sanity: a figure ahead at 30 blocks (raised above any test structure) is in view, so the check is not vacuous.
		Vec3 ahead = viewer.position().add(0.0, 12.0, 30.0);
		helper.assertFalse(hidden.test(HimEntity.viewBox(ahead)), "a figure straight ahead counts as hidden");

		// A tiny render distance and full dusk fog, or a client reporting thick fog: the band collapses to the minimum
		// distance, never closer.
		FogEdge tight = FogEdge.compute(2, 2, 0, 1.0, DUSK_MIN, Double.NaN, false, new EntityConfig());
		for (FogEdge edge : List.of(tight, FogEdge.compute(3, 3, 0, 0.0, DUSK_MIN, Double.NaN, false, new EntityConfig()),
				FogEdge.compute(12, 12, 0, 0.0, DUSK_MIN, 20.0, true, new EntityConfig()),
				FogEdge.compute(12, 12, 0, 0.0, DUSK_MIN, Double.NaN, true, new EntityConfig()))) {
			for (long seed = 1; seed <= 4; seed++) {
				SpotFinder.Query q = new SpotFinder.Query(level, viewer.position(), viewer.getEyePosition(), edge.inner(), edge.outer(), min,
						ModEntities.HIM.getDimensions(), hidden, level::isLoaded, RandomSource.create(seed), 48);
				Optional<SpotFinder.Spot> spot = SpotFinder.open(q);
				helper.assertTrue(spot.isPresent(), "no spot on open flat ground in band " + edge);
				Vec3 feet = spot.get().pos();
				AABB model = HimEntity.viewBox(feet);
				helper.assertTrue(model.contains(feet.add(0.49, 1.4, 0.0)) && model.contains(feet.add(-0.55, 0.3, -0.55)), "view box misses the model");
				helper.assertTrue(TraceService.isOutOfView(level, model, viewers, NEAR, CONE), "spawned in view at " + feet);
				helper.assertTrue(Math.sqrt(model.distanceToSqr(viewer.getEyePosition())) >= min, "spawned closer than " + min + ": " + feet);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void threeHundredBlocksFromTheLastSighting(GameTestHelper helper) {
		int spacing = ModConfig.pacing().sightingMinSpacing;
		EntityData data = new EntityData();
		helper.assertTrue(data.farEnough(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), spacing), "no sighting yet, yet too close");
		data.recordSighting("ridge", GlobalPos.of(Level.OVERWORLD, new BlockPos(100, 70, 100)), 3);
		helper.assertFalse(data.farEnough(GlobalPos.of(Level.OVERWORLD, new BlockPos(100 + spacing - 1, 64, 100)), spacing), "299 blocks away allowed");
		helper.assertFalse(data.farEnough(GlobalPos.of(Level.OVERWORLD, new BlockPos(300, 64, 300)), spacing), "283 blocks (diagonal) allowed");
		helper.assertTrue(data.farEnough(GlobalPos.of(Level.OVERWORLD, new BlockPos(100 + spacing, 64, 100)), spacing), "300 blocks away refused");
		helper.assertTrue(data.farEnough(GlobalPos.of(Level.NETHER, new BlockPos(100, 64, 100)), spacing), "another dimension refused");
		data.recordFake(GlobalPos.of(Level.OVERWORLD, new BlockPos(1000, 64, 1000)), 9);
		helper.assertFalse(data.farEnough(GlobalPos.of(Level.OVERWORLD, new BlockPos(1100, 64, 1000)), spacing), "a fake does not count for the place");
		helper.succeed();
	}

	@GameTest
	public void neverTheSameVariantTwiceInARow(GameTestHelper helper) {
		EntityData data = new EntityData();
		helper.assertTrue(data.variantAllows(Variant.COW), "first sighting refused");
		data.recordSighting(Variant.COW.shortName(), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 1);
		helper.assertFalse(data.variantAllows(Variant.COW), "the cow twice in a row");
		helper.assertTrue(data.variantAllows(Variant.RIDGE), "another variant refused");
		data.recordFake(GlobalPos.of(Level.OVERWORLD, new BlockPos(900, 64, 0)), 2);
		helper.assertFalse(data.variantAllows(Variant.COW), "a fake reset the variant rule");
		data.recordSighting(Variant.RIDGE.shortName(), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 3);
		helper.assertTrue(data.variantAllows(Variant.COW) && !data.variantAllows(Variant.RIDGE), "the rule follows the last variant");
		helper.succeed();
	}

	@GameTest
	public void neverTwoInOneInGameDay(GameTestHelper helper) {
		int max = ModConfig.pacing().sightingsPerDayMax;
		EntityData data = new EntityData();
		helper.assertTrue(data.dayAllows(5, max), "nothing yet, refused");
		data.recordSighting("cow", GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 5);
		helper.assertFalse(data.dayAllows(5, max), "two on day 5");
		helper.assertTrue(data.dayAllows(6, max), "day 6 refused");
		data.recordFake(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 6);
		helper.assertFalse(data.dayAllows(6, max), "a fake then a sighting on the same day");
		helper.assertTrue(SightingRules.dayAllows(7, 1, 7, 2) && !SightingRules.dayAllows(7, 2, 7, 2), "the per-day maximum");
		helper.succeed();
	}

	@GameTest
	public void timeOfDayGates(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		helper.assertTrue(SightingRules.clearDaylight(6000, false, config), "noon counts as dusk");
		helper.assertFalse(SightingRules.clearDaylight(6000, true, config), "rain at noon is clear daylight");
		helper.assertFalse(SightingRules.clearDaylight(12500, false, config), "dusk is clear daylight");
		helper.assertFalse(SightingRules.clearDaylight(18000, false, config), "midnight is clear daylight");
		helper.assertTrue(SightingRules.duskSky(12500, false, config) && SightingRules.duskSky(23000, false, config), "dusk and dawn skies");
		helper.assertFalse(SightingRules.duskSky(18000, false, config), "midnight is a dusk sky");
		helper.assertTrue(SightingRules.night(18000, config) && !SightingRules.night(12000, config), "night window");
		helper.assertTrue(SightingRules.inWindow(500, 23000, 1000) && !SightingRules.inWindow(5000, 23000, 1000), "windows past midnight");
		helper.succeed();
	}

	@GameTest
	public void everyVariantIsACard(GameTestHelper helper) {
		for (Variant variant : Variant.values()) {
			Optional<EventCard> card = CardRegistry.get(variant.cardId());
			helper.assertTrue(card.isPresent(), "missing card " + variant.cardId());
			helper.assertTrue(card.get().tags().contains(CardTag.SIGHTING) && card.get().habits().equals(java.util.Set.of(Habit.WATCHER)),
					variant.cardId() + " tags/habits");
			helper.assertTrue(variant == Variant.COW || card.get().earliestStage() != Stage.ALONE, "only the cow may happen in Alone: " + variant);
		}
		helper.assertTrue(Variant.CLOSE.tier() == Tier.MAJOR && Variant.CLOSE.earliestStage() == Stage.PROXIMITY, "close is a Proximity major");
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		helper.assertFalse(CardRegistry.get(Variant.LAST_ONE.cardId()).orElseThrow().contextFits(player, helper.getLevel()),
				"the last one fits without the Ending A flag");
		helper.succeed();
	}

	@GameTest
	public void recordSurvivesSaving(GameTestHelper helper) {
		EntityData data = new EntityData();
		data.recordSighting("ridge", GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 70, -9)), 4);
		data.recordFake(GlobalPos.of(Level.OVERWORLD, new BlockPos(500, 70, -9)), 4);
		data.recordStared();
		Tag encoded = EntityData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		EntityData decoded = EntityData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
		helper.assertTrue(decoded.lastVariant().equals("ridge") && decoded.lastDay() == 4 && decoded.countOnLastDay() == 2
				&& decoded.sightings() == 1 && decoded.fakes() == 1 && decoded.stared() == 1
				&& decoded.lastPos() != null && decoded.lastPos().pos().equals(new BlockPos(500, 70, -9)), "record lost on save: " + encoded);
		helper.succeed();
	}

	@GameTest
	public void viewBoxCoversTheModelInEveryPose(GameTestHelper helper) {
		Vec3 feet = new Vec3(10.5, 64.0, -3.5);
		AABB box = HimEntity.viewBox(feet);
		// Model extremes in blocks (player scale 0.9375, outer layers included), facing south: standing shoulders and
		// sleeves, swinging hands, the hat; low on all fours the legs 0.74 behind and the head 0.38 in front.
		double[][] extremes = {
				{0.49, 1.40, 0.0}, {-0.49, 1.40, 0.0}, {0.40, 0.90, 0.50}, {0.0, 1.91, 0.0}, {0.27, 1.91, 0.27},
				{0.25, 0.0, 0.74}, {-0.25, 0.72, 0.74}, {0.50, 0.0, 0.10}, {0.28, 1.13, -0.38}, {-0.28, 0.65, -0.38}};
		for (int yaw = 0; yaw < 360; yaw += 5) {
			double r = Math.toRadians(yaw);
			for (double[] p : extremes) {
				Vec3 rotated = new Vec3(p[0] * Math.cos(r) - p[2] * Math.sin(r), p[1], p[0] * Math.sin(r) + p[2] * Math.cos(r));
				helper.assertTrue(box.deflate(0.05).contains(feet.add(rotated)), "the view box misses " + rotated + " at yaw " + yaw);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void spawnAtKeepsTheMinimumDistance(GameTestHelper helper) {
		double min = EntityConfig.get().minDistance();
		Vec3 feet = new Vec3(0.5, 64.0, 0.5);
		AABB box = HimEntity.viewBox(feet);
		helper.assertTrue(FigureApi.tooClose(List.of(new Vec3(0.5, 65.6, min - 0.5)), box, min), "a player just inside the minimum distance is not too close");
		helper.assertTrue(FigureApi.tooClose(List.of(new Vec3(100, 65, 100), new Vec3(min, 65.6, 0.5)), box, min),
				"one far player hides a near one");
		helper.assertFalse(FigureApi.tooClose(List.of(new Vec3(0.5, 65.6, min + 1.5)), box, min), "a player just past the minimum distance is too close");
		helper.assertFalse(FigureApi.tooClose(List.of(), box, min), "nobody is too close");
		helper.succeed();
	}

	@GameTest(skyAccess = true, maxTicks = 100)
	public void lightSpotsStayInTheFogBand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		helper.setBlock(4, 1, 4, net.minecraft.world.level.block.Blocks.TORCH);
		BlockPos light = helper.absolutePos(new BlockPos(4, 1, 4));
		loadAround(level, light, 2);
		Vec3 player = Vec3.atBottomCenterOf(light).add(-30.0, 0.0, 0.0);
		Vec3 eye = player.add(0.0, 1.62, 0.0);
		double min = EntityConfig.get().minDistance();
		helper.succeedWhen(() -> {
			SpotFinder.Query band = new SpotFinder.Query(level, player, eye, 26.0, 34.0, min, ModEntities.HIM.getDimensions(),
					box -> true, level::isLoaded, RandomSource.create(7), 48);
			Optional<SpotFinder.Spot> spot = SpotFinder.light(band, List.of(light), 9, 3, 7);
			helper.assertTrue(spot.isPresent(), "no spot at the edge of the glow yet");
			double d = SpotFinder.horizontal(player, spot.get().pos());
			helper.assertTrue(d >= 25.5 && d <= 34.5, "light spot outside the band: " + d);
			SpotFinder.Query beyond = new SpotFinder.Query(level, player, eye, 60.0, 70.0, min, ModEntities.HIM.getDimensions(),
					box -> true, level::isLoaded, RandomSource.create(7), 48);
			helper.assertTrue(SpotFinder.light(beyond, List.of(light), 9, 3, 7).isEmpty(), "a light nearer than the fog band gave a spot");
		});
	}

	/** Loads (generates if needed) the chunks around a position so terrain checks see real ground. */
	static void loadAround(ServerLevel level, BlockPos center, int radiusChunks) {
		ChunkPos c = ChunkPos.containing(center);
		for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
			for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
				level.getChunk(c.x() + dx, c.z() + dz);
			}
		}
	}
}
