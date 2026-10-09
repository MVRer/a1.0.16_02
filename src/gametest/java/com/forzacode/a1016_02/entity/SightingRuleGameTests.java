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
public class SightingRuleGameTests {
	private static final double NEAR = 3;
	private static final double CONE = 160;

	@GameTest
	public void fogBandNeverCloserThanTheMinimum(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		int min = ModConfig.pacing().sightingMinDistance;
		for (int chunks = 2; chunks <= 32; chunks++) {
			for (float fog : new float[] {0.0F, 0.5F, 1.0F}) {
				for (int sim : new int[] {0, 2, 6, 12}) {
					for (boolean atMax : new boolean[] {false, true}) {
						FogEdge edge = FogEdge.compute(chunks, 32, sim, fog, atMax, min, config);
						String what = "chunks=" + chunks + " fog=" + fog + " sim=" + sim + " max=" + atMax + " -> " + edge;
						helper.assertTrue(edge.inner() >= min, "band starts closer than " + min + ": " + what);
						helper.assertTrue(edge.outer() >= edge.inner(), "empty band: " + what);
						helper.assertTrue(edge.outer() <= Math.max(edge.limit(), min + 2.0) + 1.0E-6, "band past the pulled-in limit: " + what);
						helper.assertTrue(sim <= 0 || edge.outer() <= Math.max(FogEdge.tickingReach(sim), min + 2.0) + 1.0E-6,
								"band reaches the edge of the ticking range: " + what);
					}
				}
			}
		}
		FogEdge vanilla = FogEdge.compute(12, 12, 0, 0.0F, false, min, config);
		helper.assertTrue(vanilla.outer() <= 192 && vanilla.outer() > 170 && vanilla.inner() > 150, "12 chunks should put him at the fog edge: " + vanilla);
		FogEdge client = FogEdge.compute(6, 12, 0, 0.0F, false, min, config);
		helper.assertTrue(client.chunks() == 6 && client.outer() <= 96, "the client's smaller view distance wins: " + client);
		FogEdge dusk = FogEdge.compute(12, 12, 0, 1.0F, false, min, config);
		helper.assertTrue(dusk.outer() < vanilla.outer() * 0.5, "dusk fog does not pull him in: " + dusk);
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
		int min = ModConfig.pacing().sightingMinDistance;

		// Sanity: a figure ahead at 30 blocks (raised above any test structure) is in view, so the check is not vacuous.
		Vec3 ahead = viewer.position().add(0.0, 12.0, 30.0);
		helper.assertFalse(hidden.test(HimEntity.viewBox(ahead)), "a figure straight ahead counts as hidden");

		// A tiny render distance and full dusk fog: the band collapses to the minimum distance, never closer.
		FogEdge tight = FogEdge.compute(2, 2, 0, 1.0F, false, min, new EntityConfig());
		for (FogEdge edge : List.of(tight, FogEdge.compute(3, 3, 0, 0.0F, false, min, new EntityConfig()))) {
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
		int min = ModConfig.pacing().sightingMinDistance;
		Vec3 feet = new Vec3(0.5, 64.0, 0.5);
		AABB box = HimEntity.viewBox(feet);
		helper.assertTrue(FigureApi.tooClose(List.of(new Vec3(0.5, 65.6, min - 0.5)), box, min), "a player 23 blocks off is not too close");
		helper.assertTrue(FigureApi.tooClose(List.of(new Vec3(100, 65, 100), new Vec3(min, 65.6, 0.5)), box, min),
				"one far player hides a near one");
		helper.assertFalse(FigureApi.tooClose(List.of(new Vec3(0.5, 65.6, min + 1.5)), box, min), "a player 25 blocks off is too close");
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
		int min = ModConfig.pacing().sightingMinDistance;
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
