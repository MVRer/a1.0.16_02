package com.forzacode.a1016_02.atmosphere;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.card.AtmosphereCards;
import com.forzacode.a1016_02.atmosphere.card.CowWhereNothingSpawnsCard;
import com.forzacode.a1016_02.atmosphere.card.DoorLeftOpenCard;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the atmosphere workstream: MobTamper (in {@link TamperGameTests}), the deterministic card gates, the
 * client effect curves, and the spot finders (house wall, doors, sealed rooms).
 */
public class AtmosphereGameTests extends TamperGameTests {
	private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	@GameTest
	public void cardsAreRegistered(GameTestHelper helper) {
		List<EventCard> cards = AtmosphereCards.all();
		for (EventCard card : cards) {
			helper.assertTrue(CardRegistry.get(card.id()).isPresent(), "card not registered: " + card.id());
			helper.assertFalse(card.tags().isEmpty(), "card without tags: " + card.id());
		}
		for (String id : List.of("fog_drift", "animals_face_fog", "distant_cave_sound", "silence", "mining_in_the_dark", "chest_opens",
				"door_left_open", "one_block_missing", "footstep_late", "compass_drift", "cow_where_nothing_spawns", "villagers_inside_at_noon",
				"dog_wont_go", "cat_hisses_corner", "patient_skeleton", "empty_water", "bat_in_sealed_room", "zombie_at_dusk")) {
			helper.assertTrue(CardRegistry.get(id).isPresent(), "missing card " + id);
		}
		helper.assertTrue(CardRegistry.get("distant_cave_sound").orElseThrow().hasFake(), "the cave sound is a false positive");
		helper.assertTrue(CardRegistry.get("silence").orElseThrow().earliestStage() == Stage.TRACES, "silence stage");
		helper.assertTrue(CardRegistry.get("mining_in_the_dark").orElseThrow().earliestStage() == Stage.PROXIMITY, "mining stage");
		helper.succeed();
	}

	@GameTest
	public void gatesHold(GameTestHelper helper) {
		// Fog drift: never during combat.
		helper.assertFalse(Gates.fogDrift(100, 600), "fog drift in combat");
		helper.assertTrue(Gates.fogDrift(600, 600) && Gates.fogDrift(Long.MAX_VALUE, 600), "fog drift out of combat");

		// Mining in the dark: night, bed or 20 s still, no sound card in the gap, once per night.
		Pacing pacing = ModConfig.pacing();
		long still = pacing.miningDarkStillTicks();
		long gap = pacing.miningDarkSoundGapTicks();
		int perNight = pacing.miningDarkPerNight;
		helper.assertTrue(still >= 20 || ModConfig.get().devFastMode, "still threshold is not from Pacing");
		helper.assertFalse(Gates.miningInTheDark(false, true, still, still, gap, gap, 0, perNight), "mining by day");
		helper.assertFalse(Gates.miningInTheDark(true, false, still - 1, still, gap, gap, 0, perNight), "mining while moving");
		helper.assertFalse(Gates.miningInTheDark(true, true, 0, still, gap - 1, gap, 0, perNight), "mining right after a sound card");
		helper.assertFalse(Gates.miningInTheDark(true, true, 0, still, Long.MAX_VALUE, gap, perNight, perNight), "mining twice in a night");
		helper.assertTrue(Gates.miningInTheDark(true, true, 0, still, Long.MAX_VALUE, gap, 0, perNight), "mining in bed");
		helper.assertTrue(Gates.miningInTheDark(true, false, still, still, gap, gap, 0, perNight), "mining while still");

		// Footstep: once per session, on foot.
		helper.assertTrue(Gates.footstep(false, false, true, false), "footstep");
		helper.assertFalse(Gates.footstep(true, false, true, false), "footstep twice in a session");
		helper.assertFalse(Gates.footstep(false, true, true, false), "footstep armed twice");
		helper.assertFalse(Gates.footstep(false, false, false, false) || Gates.footstep(false, false, true, true), "footstep off foot");

		// Cave sound: alone and still.
		helper.assertTrue(Gates.caveSound(true, 200, 160) && !Gates.caveSound(false, 200, 160) && !Gates.caveSound(true, 10, 160), "cave sound");

		// Time windows, including one that wraps past midnight.
		helper.assertTrue(Gates.inWindow(6000, 4500, 7500) && !Gates.inWindow(7500, 4500, 7500), "noon window");
		helper.assertTrue(Gates.inWindow(23000, 22000, 1000) && Gates.inWindow(24500, 22000, 1000) && !Gates.inWindow(5000, 22000, 1000), "wrapping window");

		// Away from home.
		helper.assertTrue(Gates.away(false, 0, 24) && Gates.away(true, 24 * 24, 24) && !Gates.away(true, 23 * 23, 24), "away");

		// Night is by the clock (a daytime thunderstorm is not night).
		AtmosphereConfig cfg = AtmosphereConfig.get();
		helper.assertTrue(Gates.inWindow(18000, cfg.nightFrom, cfg.nightTo) && !Gates.inWindow(6000, cfg.nightFrom, cfg.nightTo)
				&& !Gates.inWindow(12000, cfg.nightFrom, cfg.nightTo), "night window");

		// Scans of an area that is not loaded find nothing (and never load it).
		BlockPos far = new BlockPos(29_000_000 - 64, 64, 29_000_000 - 64);
		helper.assertFalse(WorldScan.areaLoaded(helper.getLevel(), far, 8), "a far-away area counts as loaded");
		helper.assertTrue(WorldScan.areaLoaded(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)), 4), "the test area counts as unloaded");
		helper.assertTrue(WorldScan.houseWalls(helper.getLevel(), far, 8).isEmpty() && DoorLeftOpenCard.closedPlacedDoors(helper.getLevel(), far, 8).isEmpty(),
				"scanned an unloaded area");
		helper.assertFalse(helper.getLevel().hasChunk(far.getX() >> 4, far.getZ() >> 4), "a scan loaded the chunk");
		helper.succeed();
	}

	@GameTest
	public void miningCountsOncePerNight(GameTestHelper helper) {
		AtmosphereData data = AtmosphereData.get(helper.getLevel().getServer());
		long night = 7_000_000L + RandomSource.create().nextInt(1_000_000);
		int perNight = ModConfig.pacing().miningDarkPerNight;
		helper.assertTrue(data.miningOn(night) == 0, "a fresh night has plays");
		data.recordMining(night);
		helper.assertTrue(data.miningOn(night) == 1 && data.miningOn(night + 1) == 0, "per-night count");
		helper.assertFalse(Gates.miningInTheDark(true, true, 0, 0, Long.MAX_VALUE, 0, data.miningOn(night), Math.min(perNight, 1)),
				"mining allowed again the same night");
		helper.succeed();
	}

	@GameTest
	public void transientEffectsSurviveASave(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		AtmosphereData data = new AtmosphereData();
		UUID player = UUID.randomUUID();
		data.setSilence(player, 1000, 1200, 400);
		data.setCompass(player, 1100, 1200, -300, 450, 48);
		data.setDuskStage(2);
		data.recordMining(5);
		Tag encoded = AtmosphereData.CODEC.encodeStart(ops, data).getOrThrow();
		AtmosphereData decoded = AtmosphereData.CODEC.parse(ops, encoded).getOrThrow();
		AtmosphereData.Transient t = decoded.transientOf(player).orElseThrow();
		helper.assertTrue(t.silenceLeft(1600) == 600 && t.silenceLeft(2200) == 0, "silence left after reload");
		helper.assertTrue(t.compassLeft(1600) == 700 && t.compassX() == -300 && t.compassZ() == 450 && t.compassSettle() == 48, "compass after reload");
		helper.assertTrue(decoded.duskStage() == 2 && decoded.miningOn(5) == 1, "dusk stage / mining count after reload");
		data.setSilence(player, 5000, 0, 0);
		data.setCompass(player, 5000, 0, 0, 0, 0);
		helper.assertTrue(data.transientOf(player).isEmpty(), "ended effects are not dropped");
		helper.succeed();
	}

	@GameTest
	public void curvesShapeTheEffects(GameTestHelper helper) {
		// Dusk fog: none at noon or dawn, full at dusk, partial at night, rising smoothly.
		helper.assertTrue(Curves.duskWeight(6000, 0.55) == 0.0 && Curves.duskWeight(0, 0.55) == 0.0, "dusk fog by day");
		helper.assertTrue(Curves.duskWeight(13000, 0.55) == 1.0, "dusk fog peaks at dusk");
		helper.assertTrue(Math.abs(Curves.duskWeight(18000, 0.55) - 0.55) < 1.0E-9, "dusk fog at night");
		// It eases in from 9000 so slowly that the first real minute is hardly there, and gets going toward deep dusk.
		helper.assertTrue(Curves.duskWeight(9000, 0.55) == 0.0 && Curves.duskWeight(9600, 0.55) < 0.02 && Curves.duskWeight(10200, 0.55) < 0.1,
				"dusk fog is not slow to start");
		helper.assertTrue(Curves.duskWeight(12000, 0.55) > 0.6, "dusk fog is not there by sunset");
		double previous = 0.0;
		for (long t = 8800; t <= 13000; t += 100) {
			double w = Curves.duskWeight(t, 0.55);
			helper.assertTrue(w >= previous && w - previous < 0.05, "dusk fog jumps at " + t);
			previous = w;
		}
		for (long t = 13000; t <= 24000; t += 100) {
			double w = Curves.duskWeight(t, 0.55);
			helper.assertTrue(w <= previous && previous - w < 0.05, "dusk fog drops at " + t);
			previous = w;
		}
		helper.assertTrue(Curves.duskWeight(23500, 0.55) == 0.0, "dusk fog not gone before sunrise");
		for (long t = 0; t < 24000; t += 250) {
			helper.assertTrue(Curves.duskWeight(t, 0.55) <= Curves.duskWeight(13000, 0.55), "dusk fog stronger than at dusk at " + t);
		}
		// The timing is the config's: start, peak and ease exponent move the curve; nonsense is put in order.
		FogLimits.Shape early = new FogLimits.Shape(24, 0.55, 6000, 9000, 9500, 12000, 18000, 20000, 2.0);
		helper.assertTrue(Curves.duskWeight(5999, early) == 0.0 && Curves.duskWeight(9000, early) == 1.0
				&& Math.abs(Curves.duskWeight(7500, early) - 0.5) < 1.0E-9, "dusk fog timing not configurable");
		FogLimits.Shape messy = new FogLimits.Shape(24, 0.55, 13000, 9000, -5, 30000, 20000, 19000, Double.NaN);
		helper.assertTrue(messy.duskPeak() == 13000 && messy.duskHoldUntil() == 13000 && messy.duskNightFrom() == 24000
				&& messy.duskEnd() == 24000 && messy.duskEaseExponent() == 3.0, "dusk fog timing not put in order: " + messy);

		// Surge: sharp rise, hold, eased fade, then nothing.
		helper.assertTrue(Curves.surgeEnvelope(0, 16, 100, 100) == 0.0 && Curves.surgeEnvelope(16, 16, 100, 100) == 1.0
				&& Curves.surgeEnvelope(80, 16, 100, 100) == 1.0 && Curves.surgeEnvelope(216, 16, 100, 100) == 0.0, "surge envelope");
		helper.assertTrue(Curves.surgeEnvelope(4, 16, 100, 100) > 0.4, "surge is not sharp");
		helper.assertTrue(Curves.surgeEnvelope(140, 16, 100, 100) > Curves.surgeEnvelope(180, 16, 100, 100), "surge fade is not easing back");

		// Fog distance: amount 0 keeps the distance, 1 reaches the minimum, never pushes fog out.
		helper.assertTrue(Curves.fogEnd(192, 24, 0) == 192 && Math.abs(Curves.fogEnd(192, 24, 1) - 24) < 1.0E-9 && Curves.fogEnd(16, 24, 1) == 16, "fog end");
		double stage0 = Curves.fogEnd(192, 24, 0.15);
		helper.assertTrue(stage0 > 120 && stage0 < 180, "stage 0 dusk fog is not 'slightly heavy': " + stage0);

		// Silence: quick cut, silent, then back gradually (no big steps), ambience before music.
		int fade = 400;
		helper.assertTrue(Curves.silenceVolume(-1, 12, 1200, fade, 0) == 1.0 && Curves.silenceVolume(600, 12, 1200, fade, 0) == 0.0, "silence");
		double last = 0.0;
		for (int t = 1200; t <= 1200 + fade; t++) {
			double v = Curves.silenceVolume(t, 12, 1200, fade, 0);
			helper.assertTrue(v >= last && v - last < 0.02, "silence comes back all at once at " + t);
			last = v;
		}
		helper.assertTrue(last == 1.0, "silence never fully returns");
		helper.assertTrue(Curves.silenceVolume(1400, 12, 1200, fade, 0) > Curves.silenceVolume(1400, 12, 1200, fade, 0.45), "ambience does not come back before music");
		helper.succeed();
	}

	/**
	 * Core's server-side fog ({@code FogLimits}, where he may stand) uses atmosphere's live config and lands exactly
	 * where the client fog ends, for the stage dusk levels at several times of day and view distances, also after the
	 * config changes.
	 */
	@GameTest
	public void serverFogMatchesClientFog(GameTestHelper helper) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		helper.assertTrue(FogLimits.shape().equals(cfg.fogShape()), "FogLimits does not use atmosphere's shape: " + FogLimits.shape() + " vs " + cfg.fogShape());
		double minFog = cfg.duskMinFogBlocks;
		double nightWeight = cfg.duskNightWeight;
		long[] timing = {cfg.duskFogStart, cfg.duskFogPeak, cfg.duskFogHoldUntil, cfg.duskFogNightFrom, cfg.duskFogFadeFrom, cfg.duskFogEnd};
		double exponent = cfg.duskFogEaseExponent;
		try {
			for (double[] shape : new double[][] {{minFog, nightWeight, exponent, 0}, {40.0, 0.3, 2.0, 1}, {12.0, 0.9, 4.5, 2}, {24.0, 0.0, 3.0, 3}}) {
				cfg.duskMinFogBlocks = shape[0];
				cfg.duskNightWeight = shape[1];
				cfg.duskFogEaseExponent = shape[2];
				// The default timing, an earlier and shorter one, a later one, and one put in order by the shape.
				long[] times = switch ((int) shape[3]) {
					case 1 -> new long[] {7000, 11000, 11500, 14000, 21000, 22000};
					case 2 -> new long[] {11000, 13500, 14000, 18000, 20000, 23999};
					case 3 -> new long[] {12000, 10000, 15000, 14000, 22000, 21000};
					default -> timing;
				};
				cfg.duskFogStart = times[0];
				cfg.duskFogPeak = times[1];
				cfg.duskFogHoldUntil = times[2];
				cfg.duskFogNightFrom = times[3];
				cfg.duskFogFadeFrom = times[4];
				cfg.duskFogEnd = times[5];
				helper.assertTrue(FogLimits.shape().equals(cfg.fogShape()), "FogLimits did not follow a config change: " + FogLimits.shape());
				for (Stage stage : Stage.values()) {
					float dusk = cfg.duskFogFor(stage);
					for (long time = 0; time <= 24000 * 2; time += 100) {
						for (int chunks : new int[] {2, 8, 12, 32}) {
							FogLimits.Result server = FogLimits.of(chunks, 32, dusk, time);
							// The client's own math in ClientAtmosphere.applyFog, from vanilla's overworld fog.
							Curves.Fog fog = DuskFogGameTests.vanilla(server.renderLimit(), false);
							Curves.applyDusk(fog, Curves.duskAmount(dusk, time, cfg.fogShape()), Curves.duskHaze(time, cfg.fogShape()), cfg.duskMinFogBlocks,
									cfg.heavyFogStartFraction);
							helper.assertTrue(Math.abs(server.fogEnd() - fog.end()) < 1.0E-9, String.format(Locale.ROOT,
									"server fog %.4f != client %.4f (stage %s, t %d, %d chunks, min %.1f, night %.2f, shape %s)", server.fogEnd(), fog.end(),
									stage, time, chunks, cfg.duskMinFogBlocks, cfg.duskNightWeight, cfg.fogShape()));
							helper.assertTrue(FogLimits.duskWeight(time, cfg.fogShape()) == Curves.duskWeight(time, cfg.fogShape()), "dusk weight differs at " + time);
						}
					}
				}
			}
		} finally {
			cfg.duskMinFogBlocks = minFog;
			cfg.duskNightWeight = nightWeight;
			cfg.duskFogEaseExponent = exponent;
			cfg.duskFogStart = timing[0];
			cfg.duskFogPeak = timing[1];
			cfg.duskFogHoldUntil = timing[2];
			cfg.duskFogNightFrom = timing[3];
			cfg.duskFogFadeFrom = timing[4];
			cfg.duskFogEnd = timing[5];
		}
		// Sanity: by day there is no dusk fog, at dusk the heaviest stage pulls it well in.
		float heaviest = cfg.duskFogFor(Stage.REMOVAL);
		helper.assertTrue(FogLimits.of(12, 12, heaviest, 6000).fogEnd() == 192.0 && FogLimits.of(12, 12, heaviest, 13000).fogEnd() < 100.0,
				"dusk fog by day or not at dusk");
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void wallFinderNeverPicksTheRoof(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer builder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		// A 6x6 house: stone floor, plank walls three high, plank roof at y=4.
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				place(helper, builder, new BlockPos(x, 4, z), Blocks.OAK_PLANKS.defaultBlockState());
				boolean edge = x == 1 || x == 6 || z == 1 || z == 6;
				for (int y = 1; y <= 3; y++) {
					if (edge) {
						place(helper, builder, new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState());
					}
				}
			}
		}
		// Other tests run next to this one and may leave player-placed blocks of their own: keep only this house.
		List<WorldScan.WallSpot> spots = WorldScan.houseWalls(level, helper.absolutePos(new BlockPos(3, 2, 3)), 8).stream()
				.filter(s -> inside(rel(helper, s.pos())))
				.toList();
		helper.assertFalse(spots.isEmpty(), "no wall block found in a plain house");
		boolean eyeLevel = false;
		for (WorldScan.WallSpot spot : spots) {
			BlockPos rel = rel(helper, spot.pos());
			helper.assertTrue(rel.getY() >= 1 && rel.getY() <= 3, "picked a block outside the wall: " + rel);
			boolean edge = rel.getX() == 1 || rel.getX() == 6 || rel.getZ() == 1 || rel.getZ() == 6;
			boolean corner = (rel.getX() == 1 || rel.getX() == 6) && (rel.getZ() == 1 || rel.getZ() == 6);
			helper.assertTrue(edge && !corner, "picked a corner or a non-wall block: " + rel);
			helper.assertTrue(level.getBlockState(spot.pos().relative(spot.outward())).isAir()
					&& level.getBlockState(spot.pos().relative(spot.outward().getOpposite())).isAir(), "not a window between inside and outside: " + rel);
			eyeLevel |= spot.eyeLevel() && rel.getY() == 2;
		}
		helper.assertTrue(eyeLevel, "no eye-level window found");

		// Removing one leaves a clean 1x1 hole: the wall above and below stays.
		WorldScan.WallSpot spot = spots.getFirst();
		helper.assertTrue(Services.traces().remove(level, spot.pos(), "test:one_block_missing"), "remove refused with nobody looking");
		helper.assertTrue(level.getBlockState(spot.pos()).isAir() && level.getBlockState(spot.pos().above()).is(Blocks.OAK_PLANKS)
				&& !level.getBlockState(spot.pos().below()).isAir(), "not a 1x1 hole");
		helper.succeed();
	}

	@GameTest
	public void doorFinderOnlyOpensPlacedClosedDoors(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer builder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		floor(helper);
		BlockPos placedClosed = new BlockPos(1, 1, 1);
		BlockPos placedOpen = new BlockPos(3, 1, 1);
		BlockPos natural = new BlockPos(5, 1, 1);
		BlockPos iron = new BlockPos(1, 1, 4);
		door(helper, placedClosed, Blocks.OAK_DOOR, false);
		door(helper, placedOpen, Blocks.OAK_DOOR, true);
		door(helper, natural, Blocks.OAK_DOOR, false);
		door(helper, iron, Blocks.IRON_DOOR, false);
		for (BlockPos pos : List.of(placedClosed, placedOpen, iron)) {
			Services.watch().onPlaced(builder, level, helper.absolutePos(pos), helper.getBlockState(pos));
		}
		List<BlockPos> doors = DoorLeftOpenCard.closedPlacedDoors(level, helper.absolutePos(new BlockPos(3, 1, 3)), 6);
		String found = "door candidates: " + doors.stream().map(p -> rel(helper, p)).toList();
		helper.assertTrue(doors.contains(helper.absolutePos(placedClosed)), found);
		helper.assertFalse(doors.contains(helper.absolutePos(placedOpen)) || doors.contains(helper.absolutePos(iron)), found);
		// A door nobody placed is never a candidate (unless a stale footprint from an earlier run says otherwise).
		helper.assertTrue(doors.contains(helper.absolutePos(natural)) == Services.watch().wasPlacedByPlayer(level, helper.absolutePos(natural)), found);

		BlockPos lower = helper.absolutePos(placedClosed);
		boolean opened = Services.traces().batch(level, "test:door_left_open")
				.convert(lower, level.getBlockState(lower).setValue(DoorBlock.OPEN, true))
				.convert(lower.above(), level.getBlockState(lower.above()).setValue(DoorBlock.OPEN, true))
				.commit();
		helper.assertTrue(opened && level.getBlockState(lower).getValue(DoorBlock.OPEN) && level.getBlockState(lower.above()).getValue(DoorBlock.OPEN)
				&& level.getBlockState(lower.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "door did not open whole");
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void sealedRoomFinderNeedsNoOpenings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A stone shell 5x5x5 with a 3x3x3 room inside.
		for (int x = 1; x <= 5; x++) {
			for (int y = 0; y <= 4; y++) {
				for (int z = 1; z <= 5; z++) {
					boolean shell = x == 1 || x == 5 || y == 0 || y == 4 || z == 1 || z == 5;
					helper.setBlock(x, y, z, shell ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		BlockPos inside = helper.absolutePos(new BlockPos(3, 2, 3));
		List<BlockPos> room = WorldScan.sealedRoom(level, inside, 400);
		helper.assertTrue(room != null && room.size() == 27, "sealed room: " + (room == null ? "none" : room.size() + " cells"));

		helper.setBlock(1, 2, 3, Blocks.GLASS);
		helper.assertTrue(WorldScan.sealedRoom(level, inside, 400) != null, "a glass window counts as an opening");

		helper.setBlock(1, 2, 3, Blocks.AIR);
		helper.assertTrue(WorldScan.sealedRoom(level, inside, 400) == null, "a hole in the wall still counts as sealed");
		helper.succeed();
	}

	@GameTest
	public void cowCardNeedsADeadMountain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 1.0, 3.5)), 0.0F, 0.0F);
		int radius = AtmosphereConfig.get().deadMountainSearchRadius;
		boolean siteNearby = !Services.sites().find(SiteType.DEAD_MOUNTAIN, GlobalPos.of(level.dimension(), player.blockPosition()), radius).isEmpty();
		if (!siteNearby) {
			FireResult result = new CowWhereNothingSpawnsCard().fire(new FireContext(player, level, false, true, RandomSource.create(7)));
			helper.assertTrue(result == FireResult.NO_SPOT, "no dead mountain, yet " + result);
		}
		helper.succeed();
	}

	@GameTest
	public void duskFogLevelsRiseWithTheStage(GameTestHelper helper) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		float previous = -1.0F;
		for (Stage stage : Stage.values()) {
			float level = cfg.duskFogFor(stage);
			helper.assertTrue(level >= previous && level >= 0.0F && level <= 1.0F, "dusk fog for " + stage + " is " + level);
			previous = level;
		}
		helper.assertTrue(cfg.duskFogFor(Stage.ALONE) > 0.0F && cfg.duskFogFor(Stage.ALONE) <= 0.25F, "stage 0 dusk fog is not slight");
		helper.assertTrue(Services.mobs() instanceof com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl, "MobTamper not installed");
		helper.succeed();
	}

	/** Relative position (rotation NONE). {@code GameTestHelper.relativePos} does not invert {@code absolutePos} in 26.3. */
	private static BlockPos rel(GameTestHelper helper, BlockPos absolute) {
		return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	private static boolean inside(BlockPos rel) {
		return rel.getX() >= 0 && rel.getX() < 8 && rel.getY() >= 0 && rel.getY() < 8 && rel.getZ() >= 0 && rel.getZ() < 8;
	}

	private static void place(GameTestHelper helper, ServerPlayer builder, BlockPos rel, BlockState state) {
		helper.setBlock(rel, state);
		Services.watch().onPlaced(builder, helper.getLevel(), helper.absolutePos(rel), state);
	}

	private static void door(GameTestHelper helper, BlockPos rel, Block block, boolean open) {
		ServerLevel level = helper.getLevel();
		BlockState lower = block.defaultBlockState().setValue(DoorBlock.OPEN, open).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		level.setBlock(helper.absolutePos(rel), lower, SET_FLAGS);
		level.setBlock(helper.absolutePos(rel.above()), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), SET_FLAGS);
	}
}
