package com.forzacode.a1016_02.world;

import java.util.List;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc.ScEnv;
import com.forzacode.a1016_02.world.sc.ScMarks;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc.Tracks;
import com.forzacode.a1016_02.world.sc.Turned;
import com.forzacode.a1016_02.world.sc.WhereHeStood;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SC-3, 11 ({@code docs/SELF_CREATED.md}): someone's torches into a cave branch nobody visited, a shaft to lava and a
 * sign. Each test builds the same cave in its own {@value #CAVE} area: solid stone, a natural corridor 1 wide and 2
 * high along x at feet height 14 (z 4) from the known cave spot (x 1) to a dead end (x 40), and under its end natural
 * stone straight down to a lava source. Only the spot's chunk counts as visited. Its players, story state and world
 * data are its own ({@link Friends}); nobody joins the server.
 */
public class SelfCreatedWorldTrailTests {
	static final String CAVE = "a1016_02:world/sc/cave";
	static final int FEET_Y = 14;
	static final int CORRIDOR_Z = 4;
	static final int END_X = 40;
	static final int LAVA_Y = 5;

	/** Small numbers for the small cave (the shaft from y 13 to 6 is 8 deep). */
	static TorchTrail.Config caveConfig() {
		TorchTrail.Config config = new TorchTrail.Config();
		config.pathMin = 30;
		config.pathMax = 45;
		config.shaftMin = 6;
		config.shaftMax = 10;
		config.minDepthBelowSurface = 4;
		return config;
	}

	/** The cave: stone everywhere, the corridor, lava under its end. Dug by {@code digger} (a player-dug tunnel) if given. */
	static BlockPos cave(GameTestHelper helper, ServerPlayer digger, boolean lava) {
		for (BlockPos rel : BlockPos.betweenClosed(0, 0, 0, 43, 23, 8)) {
			helper.setBlock(rel, Blocks.STONE);
		}
		for (int x = 1; x <= END_X; x++) {
			for (int y = FEET_Y; y <= FEET_Y + 1; y++) {
				BlockPos rel = new BlockPos(x, y, CORRIDOR_Z);
				if (digger != null) {
					helper.assertTrue(digger.gameMode.destroyBlock(helper.absolutePos(rel)), "could not dig " + rel);
				} else {
					helper.setBlock(rel, Blocks.AIR);
				}
			}
		}
		if (lava) {
			helper.setBlock(new BlockPos(END_X, LAVA_Y, CORRIDOR_Z), Blocks.LAVA);
		}
		return helper.absolutePos(new BlockPos(1, FEET_Y, CORRIDOR_Z));
	}

	/** Only the spot's chunk was ever visited. */
	static NewScarPlacer.VisitLookup onlySpot(BlockPos spot) {
		ChunkPos visited = ChunkPos.containing(spot);
		return (level, chunk) -> chunk.equals(visited) ? 5 : -1;
	}

	/** A test's own env: its players, state and data, nobody seeing anything unless {@code seen}. */
	static ScEnv env(Friends friends, boolean seen) {
		return new ScEnv(friends.bases(), Services.traces(), (level, cells) -> !seen, level -> List.copyOf(friends.online), (s, p, m) -> false,
				Services.protectedAreas());
	}

	/** Friends with one known player whose first block (their base) is {@code baseRel} in the test's area. */
	static Friends friends(GameTestHelper helper, BlockPos baseRel) {
		Friends.Known owner = new Friends.Known("sc3_owner", 0, Friends.at(helper, baseRel));
		Friends friends = new Friends(helper, Friends.state(helper, owner));
		friends.join(owner, new Vec3(20.5, FEET_Y + 60.0, 200.5));
		return friends;
	}

	static ServerPlayer target(Friends friends) {
		return friends.online.getFirst();
	}

	/** The torches on the right-hand wall at the spacing, the shaft to lava, "brb", every cause left by others; once per world. */
	@GameTest(structure = CAVE, maxTicks = 40)
	public void trailLeavesTorchesOnTheRightWallAShaftToLavaAndBrb(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos spot = cave(helper, null, true);
		Friends friends = friends(helper, new BlockPos(20, FEET_Y, 400));
		ScEnv env = env(friends, false);
		TorchTrail.Config config = caveConfig();
		TorchTrail.Result result = TorchTrail.leaveFor(env, target(friends), List.of(spot), onlySpot(spot), config, RandomSource.create(3), false);
		helper.assertTrue(result.plan() != null, "nothing left: " + result.reason());
		TorchTrail.Plan plan = result.plan();

		BlockPos hole = helper.absolutePos(new BlockPos(END_X, FEET_Y, CORRIDOR_Z));
		helper.assertTrue(plan.hole().equals(hole), "the hole is not at the dead end: " + plan.hole());
		ChunkPos visited = ChunkPos.containing(spot);
		helper.assertTrue(plan.mouth() > 0 && !ChunkPos.containing(plan.path().get(plan.mouth())).equals(visited)
				&& ChunkPos.containing(plan.path().get(plan.mouth() - 1)).equals(visited), "the mouth is not the first unvisited cell");
		// Torches: on the right-hand wall going in (+x, so the wall at z + 1), facing away from it, at the spacing.
		helper.assertTrue(plan.torches().size() >= 2, "too few torches: " + plan.torches().size());
		int previous = Integer.MIN_VALUE;
		for (TorchTrail.Left torch : plan.torches()) {
			BlockState state = level.getBlockState(torch.pos());
			helper.assertTrue(state.is(Blocks.WALL_TORCH) && state.getValue(WallTorchBlock.FACING) == Direction.NORTH, "not a wall torch facing away from the right wall at "
					+ torch.pos() + ": " + state);
			helper.assertTrue(level.getBlockState(torch.pos().south()).is(Blocks.STONE), "not on the right-hand wall");
			int x = torch.pos().getX();
			if (previous == Integer.MIN_VALUE) {
				int mouthX = plan.path().get(plan.mouth()).getX();
				helper.assertTrue(x - mouthX >= 0 && x - mouthX <= TorchTrail.MOUTH_TORCH, "the first torch is not within 6 of the mouth");
			} else {
				helper.assertTrue(x - previous >= config.spacingMin && x - previous <= config.spacingMax, "torches " + (x - previous) + " apart");
			}
			helper.assertTrue(x < hole.getX(), "a torch past the hole");
			previous = x;
		}
		// The shaft: straight down through the stone, opening on the lava.
		for (int y = FEET_Y - 1; y > LAVA_Y; y--) {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(END_X, y, CORRIDOR_Z));
		}
		helper.assertBlockPresent(Blocks.LAVA, new BlockPos(END_X, LAVA_Y, CORRIDOR_Z));
		helper.assertTrue(plan.shaft().size() == FEET_Y - 1 - LAVA_Y, "the shaft is " + plan.shaft().size() + " deep");
		// The floor torch beside the hole and the sign over it: "brb", not waxed.
		helper.assertBlockPresent(Blocks.TORCH, new BlockPos(END_X - 1, FEET_Y, CORRIDOR_Z));
		BlockPos signAt = hole.above();
		BlockState sign = level.getBlockState(signAt);
		helper.assertTrue(sign.is(Blocks.OAK_WALL_SIGN) && sign.getValue(WallSignBlock.FACING) == Direction.WEST, "no oak wall sign over the hole: " + sign);
		helper.assertTrue(level.getBlockEntity(signAt) instanceof SignBlockEntity entity
				&& entity.getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString().equals("brb") && !entity.isWaxed(), "the sign does not read brb");
		helper.assertTrue(TorchTrail.lines(level.getServer()).equals(List.of("brb", "", "", "")), "t0rch.json's lines");
		// Left by others: only the shaft is ledgered, never undone.
		List<TraceLedger.Entry> ledgered = TraceLedger.get(level.getServer()).entries().stream()
				.filter(e -> helper.getBounds().contains(Vec3.atCenterOf(e.pos().pos()))).toList();
		helper.assertTrue(ledgered.size() == plan.shaft().size(), "ledgered " + ledgered.size() + " entries");
		helper.assertTrue(ledgered.stream().allMatch(e -> e.cause().equals(TorchTrail.CAUSE) && TraceLedger.isLeftByOthers(e.cause())), "a cause not left by others");
		// Once per world, recorded in the tick.
		helper.assertTrue(friends.data.sc().trails().size() == 1 && friends.data.sc().trails().getFirst().pos().pos().equals(hole), "not recorded");
		TorchTrail.Result again = TorchTrail.leaveFor(env, target(friends), List.of(spot), onlySpot(spot), config, RandomSource.create(4), true);
		helper.assertTrue(again.plan() == null && again.reason().contains("already"), "a second trail: " + again.reason());
		helper.succeed();
	}

	/** Near a base, in a player-dug tunnel, without lava under it, someone near, or in view: nothing is left. */
	@GameTest(structure = CAVE, maxTicks = 40)
	public void trailRefusesNearABaseInADugTunnelWithoutLavaOrInView(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TorchTrail.Config config = caveConfig();
		BlockPos spot = cave(helper, null, true);
		BlockPos holeRel = new BlockPos(END_X, FEET_Y - 1, CORRIDOR_Z);

		Friends nearBase = friends(helper, new BlockPos(20, FEET_Y, 30));
		TorchTrail.Result result = TorchTrail.search(env(nearBase, false), level, spot, config, onlySpot(spot), RandomSource.create(1));
		helper.assertTrue(result.plan() == null && result.reason().contains("of a base"), "near a base: " + result.reason());

		Friends far = friends(helper, new BlockPos(20, FEET_Y, 400));
		result = TorchTrail.search(env(far, true), level, spot, config, onlySpot(spot), RandomSource.create(1));
		helper.assertTrue(result.plan() == null && result.reason().contains("in view"), "in view: " + result.reason());

		Friends.Known passer = new Friends.Known("sc3_passer", 0, null);
		far.join(passer, new Vec3(30.5, FEET_Y, CORRIDOR_Z + 0.5));
		result = TorchTrail.search(env(far, false), level, spot, config, onlySpot(spot), RandomSource.create(1));
		helper.assertTrue(result.plan() == null && result.reason().contains("within 32"), "someone near: " + result.reason());
		far.leave(far.online.getLast());

		result = TorchTrail.search(env(far, false), level, spot, config, (l, chunk) -> 5, RandomSource.create(1));
		helper.assertTrue(result.plan() == null && result.reason().contains("nobody visited"), "a branch everyone visited: " + result.reason());

		helper.setBlock(new BlockPos(END_X, LAVA_Y, CORRIDOR_Z), Blocks.STONE);
		result = TorchTrail.search(env(far, false), level, spot, config, onlySpot(spot), RandomSource.create(1));
		helper.assertTrue(result.plan() == null && result.reason().contains("no shaft"), "no lava: " + result.reason());

		ServerPlayer digger = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		spot = cave(helper, digger, true);
		result = TorchTrail.search(env(far, false), level, spot, config, onlySpot(spot), RandomSource.create(1));
		helper.assertTrue(result.plan() == null && result.reason().contains("not natural cave"), "a dug tunnel: " + result.reason());

		helper.assertBlockPresent(Blocks.STONE, holeRel);
		helper.assertTrue(far.data.sc().trails().isEmpty() && nearBase.data.sc().trails().isEmpty(), "a refused trail was recorded");
		helper.succeed();
	}

	/** A save from before the layer loads with no marks; marks round-trip; a part that cannot be read loads as none. */
	@GameTest
	public void worldDataKeepsTheLayersMarksAndOldSavesLoad(GameTestHelper helper) {
		var ops = Friends.ops(helper);
		WorldData fresh = new WorldData();
		CompoundTag tag = (CompoundTag) WorldData.CODEC.encodeStart(ops, fresh).getOrThrow();
		helper.assertFalse(tag.contains("sc"), "empty marks were written");
		WorldData old = WorldData.CODEC.parse(ops, tag).getOrThrow();
		helper.assertTrue(old.sc().isEmpty(), "an old save has marks");

		GlobalPos base = Friends.at(helper, new BlockPos(1, 1, 1));
		fresh.sc().turned(base, 7, 2);
		fresh.sc().tracked(base, 8);
		fresh.sc().addTrail(new ScMarks.Trail(Friends.at(helper, new BlockPos(2, 2, 2)), 9));
		CompoundTag written = (CompoundTag) WorldData.CODEC.encodeStart(ops, fresh).getOrThrow();
		WorldData read = WorldData.CODEC.parse(ops, written).getOrThrow();
		ScMarks.Base marks = read.sc().base(base);
		helper.assertTrue(marks.turned() == 2 && marks.turnedDay() == 7 && marks.tracks() == 1 && marks.tracksDay() == 8, "marks: " + marks);
		helper.assertTrue(read.sc().trails().size() == 1 && read.sc().trails().getFirst().day() == 9, "trails: " + read.sc().trails());

		written.putString("sc", "not marks");
		WorldData lenient = WorldData.CODEC.parse(ops, written).getOrThrow();
		helper.assertTrue(lenient.sc().isEmpty() && lenient.salt() == fresh.salt(), "unreadable marks failed the file");
		helper.succeed();
	}

	/** Each section is version 1 with the design's numbers. */
	@GameTest
	public void theSectionsAreVersionOneWithTheDesignsNumbers(GameTestHelper helper) {
		JsonObject none = new JsonObject();
		Turned.Config turned = ModConfig.readSection(none, Turned.SECTION, Turned.Config.class, Turned.Config::new);
		helper.assertTrue(turned.version == 1 && turned.baseRadius == 12 && turned.nobodyWithin == 16 && turned.awayDistance == 32 && turned.perFireTraces == 1
				&& turned.perFireLater == 2 && turned.minDaysApart == 2 && turned.maxPerBase == 8 && turned.signs, "sc08");
		Tracks.Config tracks = ModConfig.readSection(none, Tracks.SECTION, Tracks.Config.class, Tracks.Config::new);
		helper.assertTrue(tracks.version == 1 && tracks.lengthMin == 14 && tracks.lengthMax == 28 && tracks.stride == 2 && tracks.minPrints == 5
				&& tracks.circleChance == 0.4 && tracks.nobodyWithin == 16 && tracks.awayDistance == 32 && tracks.minDaysApart == 2 && tracks.maxPerBase == 4, "sc09");
		WhereHeStood.Config stood = ModConfig.readSection(none, WhereHeStood.SECTION, WhereHeStood.Config.class, WhereHeStood.Config::new);
		helper.assertTrue(stood.version == 1 && stood.standChance == 0.7 && stood.runChance == 0.35 && stood.clearRadius == 2 && stood.maxRunPrints == 12
				&& stood.retryMinutes == 10 && stood.fogChance == 0.6 && stood.fogRadius == 40 && stood.fogLevel == 0.45F && stood.fogRampTicks == 1200
				&& stood.fogDaysMin == 1 && stood.fogDaysMax == 3 && stood.maxFogPlaces == 4, "sc10");
		TorchTrail.Config trail = ModConfig.readSection(none, TorchTrail.SECTION, TorchTrail.Config.class, TorchTrail.Config::new);
		helper.assertTrue(trail.version == 1 && trail.pathMin == 30 && trail.pathMax == 90 && trail.spacingMin == 8 && trail.spacingMax == 12
				&& trail.shaftMin == 8 && trail.shaftMax == 30 && trail.minDepthBelowSurface == 12 && trail.nobodyWithin == 32 && trail.baseClearance == 64
				&& trail.maxPerWorld == 1, "sc11");
		JsonObject old = JsonParser.parseString("{\"sc11\": {\"pathMin\": 40}}").getAsJsonObject();
		TorchTrail.Config kept = ModConfig.readSection(old, TorchTrail.SECTION, TorchTrail.Config.class, TorchTrail.Config::new);
		helper.assertTrue(kept.version == 1 && kept.pathMin == 40, "a hand edit was not kept");
		helper.succeed();
	}
}
