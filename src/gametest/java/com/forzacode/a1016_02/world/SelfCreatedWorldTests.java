package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogPlace;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc.Prints;
import com.forzacode.a1016_02.world.sc.ScEnv;
import com.forzacode.a1016_02.world.sc.ScGates;
import com.forzacode.a1016_02.world.sc.Tracks;
import com.forzacode.a1016_02.world.sc.Turned;
import com.forzacode.a1016_02.world.sc.WhereHeStood;
import com.forzacode.a1016_02.world.sc.WorldSc;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * SC-3, the self-created layer's world part ({@code docs/SELF_CREATED.md}): 08 (things turned to face the bed), 09
 * (tracks to the door), 10 (where he stood) here, 11 (the torches into the cave), the sections and the saved marks in
 * {@link SelfCreatedWorldTrailTests}. Every test has its own players, story state and world data ({@link Friends}),
 * its own view ({@link ScEnv}) and, for 10, its own queue and fog; nobody joins the server.
 */
public class SelfCreatedWorldTests extends SelfCreatedWorldTrailTests {
	private static final String BIG = WorldSignatureTests.BIG;

	// --- helpers ---

	/** A player who places things and is online nowhere (only the footprint records them). */
	private static ServerPlayer builder(GameTestHelper helper) {
		return new Friends.Friend(helper.getLevel(), UUID.randomUUID(), "sc3_builder");
	}

	/** Sets a block and records it as placed by {@code builder}. */
	private static BlockPos put(GameTestHelper helper, ServerPlayer builder, BlockPos rel, BlockState state) {
		helper.setBlock(rel, state);
		BlockPos abs = helper.absolutePos(rel);
		Services.watch().onPlaced(builder, helper.getLevel(), abs, state);
		return abs;
	}

	/** Dirt under grass over the whole 24 x 24 area (y 0 and 1). */
	private static void grass(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(x, 0, z, Blocks.DIRT);
				helper.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
			}
		}
	}

	/** A test's env whose view is these watchers' (each with an 8-chunk view). */
	private static ScEnv watched(Friends friends, List<ServerPlayer> watchers) {
		List<TraceService.Viewer> viewers = watchers.stream().map(p -> TraceService.Viewer.of(p, 8)).toList();
		return new ScEnv(friends.bases(), Services.traces(), (level, cells) -> TraceService.positionsOutOfView(level, cells, viewers, 3, 160),
				level -> List.copyOf(friends.online), (s, p, m) -> false, Services.protectedAreas());
	}

	private static ScEnv unseen(Friends friends) {
		return watched(friends, List.of());
	}

	/** Puts {@code player} at {@code rel} looking at {@code atRel}. */
	private static void look(GameTestHelper helper, ServerPlayer player, Vec3 rel, Vec3 atRel) {
		Vec3 from = helper.absoluteVec(rel);
		Vec3 to = helper.absoluteVec(atRel);
		double dx = to.x - from.x;
		double dy = to.y - (from.y + player.getEyeHeight());
		double dz = to.z - from.z;
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		player.snapTo(from.x, from.y, from.z, yaw, pitch);
		// What it sees is its head's yaw (a living entity's view), not its body's.
		player.setYHeadRot(yaw);
	}

	/** Moves a test player without turning it (a server player's own move would reset a connection it has not). */
	private static void moveTo(GameTestHelper helper, ServerPlayer player, Vec3 rel) {
		Vec3 at = helper.absoluteVec(rel);
		player.snapTo(at.x, at.y, at.z, player.getYRot(), player.getXRot());
	}

	/** The test-area position of an absolute one (what {@code absolutePos} was given). */
	private static BlockPos rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	private static List<TraceLedger.Entry> ledger(GameTestHelper helper, String cause) {
		return TraceLedger.get(helper.getLevel().getServer()).entries().stream()
				.filter(e -> e.cause().startsWith(cause) && helper.getBounds().contains(Vec3.atCenterOf(e.pos().pos()))).toList();
	}

	private static BlockState facing(Block block, Direction facing) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	// --- 08 ---

	/**
	 * Placed things near the base turn to face the bed (ledgered CONVERT with the old facing); never a double chest or
	 * a redstone part; a sign by its rotation; one in Traces, two from Proximity; the switch's give-back turns them back
	 * once, and leaves one the player turned again since.
	 */
	@GameTest(structure = BIG, maxTicks = 40)
	public void turnedFacesPlacedThingsTowardTheBedAndTurnsBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 23, 1, 23)) {
			helper.setBlock(rel, Blocks.STONE);
		}
		ServerPlayer builder = builder(helper);
		BlockPos base = helper.absolutePos(new BlockPos(4, 2, 10));
		BlockPos furnace = put(helper, builder, new BlockPos(10, 2, 10), facing(Blocks.FURNACE, Direction.NORTH));
		BlockPos pumpkin = put(helper, builder, new BlockPos(10, 2, 12), facing(Blocks.CARVED_PUMPKIN, Direction.NORTH));
		BlockPos sign = put(helper, builder, new BlockPos(4, 2, 4), Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8));
		BlockPos already = put(helper, builder, new BlockPos(14, 2, 10), facing(Blocks.FURNACE, Direction.WEST));
		BlockPos left = put(helper, builder, new BlockPos(10, 2, 16), facing(Blocks.CHEST, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		BlockPos right = put(helper, builder, new BlockPos(11, 2, 16), facing(Blocks.CHEST, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		BlockPos observer = put(helper, builder, new BlockPos(12, 2, 6), Blocks.OBSERVER.defaultBlockState());
		BlockPos dispenser = put(helper, builder, new BlockPos(13, 2, 6), Blocks.DISPENSER.defaultBlockState());
		Friends.Known owner = new Friends.Known("sc3_owner", 0, Friends.at(helper, new BlockPos(4, 2, 10)));
		Friends friends = new Friends(helper, Friends.state(helper, owner));
		ServerPlayer target = friends.join(owner, new Vec3(4.5, 2.0, 70.5));
		ScEnv env = watched(friends, List.of(target));
		Turned.Config config = new Turned.Config();

		List<Turned.Candidate> candidates = Turned.candidates(env, level, target, base, config, false);
		List<BlockPos> listed = candidates.stream().map(Turned.Candidate::pos).toList();
		helper.assertTrue(listed.containsAll(List.of(furnace, pumpkin, sign, already)) && listed.size() == 4, "the candidates: " + listed);
		helper.assertFalse(listed.contains(left) || listed.contains(right) || listed.contains(observer) || listed.contains(dispenser),
				"a double chest or a redstone part is a candidate");
		helper.assertTrue(candidates.stream().filter(c -> c.pos().equals(already)).allMatch(c -> !c.ok() && c.refusal().contains("already")),
				"a furnace already facing the bed may turn");

		Turned.Outcome traces = Turned.turn(env, target, Stage.TRACES, RandomSource.create(1), false, config);
		helper.assertTrue(traces.result() == FireResult.FIRED && traces.turned().size() == 1, "Traces: " + traces);
		Turned.Outcome proximity = Turned.turn(env, target, Stage.PROXIMITY, RandomSource.create(2), true, config);
		helper.assertTrue(proximity.result() == FireResult.FIRED && proximity.turned().size() == 2, "Proximity: " + proximity);
		helper.assertTrue(level.getBlockState(furnace).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST, "the furnace does not face the bed");
		helper.assertTrue(level.getBlockState(pumpkin).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST, "the pumpkin does not face the bed");
		helper.assertTrue(level.getBlockState(sign).getValue(BlockStateProperties.ROTATION_16) == 0, "the sign does not face the bed");
		helper.assertTrue(level.getBlockState(left).getValue(ChestBlock.TYPE) == ChestType.LEFT
				&& level.getBlockState(left).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH, "the double chest turned");
		helper.assertTrue(friends.data.sc().base(Friends.at(helper, new BlockPos(4, 2, 10))).turned() == 3, "the base's count");
		Turned.Outcome nothing = Turned.turn(env, target, Stage.PROXIMITY, RandomSource.create(3), true, config);
		helper.assertTrue(nothing.result() == FireResult.NO_SPOT, "a fourth turn: " + nothing);
		Turned.Outcome spaced = Turned.turn(env, target, Stage.PROXIMITY, RandomSource.create(4), false, config);
		helper.assertTrue(spaced.result() == FireResult.SKIPPED && spaced.reason().contains("days apart"), "the days apart: " + spaced);

		List<TraceLedger.Entry> entries = ledger(helper, Turned.CAUSE);
		helper.assertTrue(entries.size() == 3 && entries.stream().allMatch(e -> e.kind() == TraceLedger.Kind.CONVERT), "ledgered: " + entries);
		helper.assertTrue(entries.stream().allMatch(e -> e.state().isPresent() && Turned.onlyFacingDiffers(level.getBlockState(e.pos().pos()), e.state().get())
				&& level.getBlockState(e.pos().pos()) != e.state().get()), "an entry does not hold the old facing");
		// The player turns the pumpkin to the south since: it is theirs now and stays.
		level.setBlock(pumpkin, Blocks.JACK_O_LANTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
		java.util.function.Predicate<TraceLedger.Entry> here = e -> e.cause().equals(Turned.CAUSE) && helper.getBounds().contains(Vec3.atCenterOf(e.pos().pos()));
		helper.assertTrue(WorldSc.turnBack(level.getServer(), here) == 2, "not two turned back");
		helper.assertTrue(level.getBlockState(furnace).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH
				&& level.getBlockState(sign).getValue(BlockStateProperties.ROTATION_16) == 8, "the give-back did not turn them back");
		helper.assertTrue(level.getBlockState(pumpkin).is(Blocks.JACK_O_LANTERN)
				&& level.getBlockState(pumpkin).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.SOUTH, "the player's own change was undone");
		helper.assertTrue(ledger(helper, Turned.CAUSE).size() == 1, "the turned-back entries are still ledgered: " + ledger(helper, Turned.CAUSE));
		helper.assertTrue(WorldSc.turnBack(level.getServer(), here) == 0, "turned back twice");
		helper.succeed();
	}

	/** In view, someone within 16, or the target within 32 and awake: nothing turns. */
	@GameTest(structure = BIG, maxTicks = 40)
	public void turnedRefusesInViewNearSomeoneOrTheTargetClose(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 23, 1, 23)) {
			helper.setBlock(rel, Blocks.STONE);
		}
		BlockPos furnace = put(helper, builder(helper), new BlockPos(10, 2, 10), facing(Blocks.FURNACE, Direction.NORTH));
		BlockPos base = helper.absolutePos(new BlockPos(4, 2, 10));
		Friends.Known owner = new Friends.Known("sc3_owner", 0, Friends.at(helper, new BlockPos(4, 2, 10)));
		Friends friends = new Friends(helper, Friends.state(helper, owner));
		ServerPlayer target = friends.join(owner, new Vec3(10.5, 2.0, 30.5));
		Turned.Config config = new Turned.Config();

		String close = Turned.candidates(unseen(friends), level, target, base, config, false).getFirst().refusal();
		helper.assertTrue(close != null && close.contains("within 32"), "the target 20 away: " + close);
		moveTo(helper, target, new Vec3(10.5, 2.0, 70.5));
		Friends.Known passer = new Friends.Known("sc3_passer", 0, null);
		ServerPlayer near = friends.join(passer, new Vec3(13.5, 2.0, 18.5));
		String someone = Turned.candidates(unseen(friends), level, target, base, config, false).getFirst().refusal();
		helper.assertTrue(someone != null && someone.contains("within 16"), "someone 9 away: " + someone);
		look(helper, near, new Vec3(10.5, 8.0, 30.5), new Vec3(10.5, 2.5, 10.5));
		String seen = Turned.candidates(watched(friends, List.of(near)), level, target, base, config, false).getFirst().refusal();
		helper.assertTrue(seen != null && seen.contains("in view"), "watched from 20 away: " + seen);
		Turned.Outcome outcome = Turned.turn(watched(friends, List.of(near)), target, Stage.PROXIMITY, RandomSource.create(1), false, config);
		helper.assertTrue(outcome.result() == FireResult.NO_SPOT, "turned in view: " + outcome);
		helper.assertTrue(level.getBlockState(furnace).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH, "the furnace turned");
		helper.succeed();
	}

	// --- 09 ---

	/** The small house: a 3 x 3 floor (x 3-5, z 9-11), walls, a roof at y 4, an oak door in the east wall (6, 2, 10). */
	private static BlockPos house(GameTestHelper helper, ServerPlayer builder) {
		for (int x = 2; x <= 6; x++) {
			for (int z = 8; z <= 12; z++) {
				boolean wall = x == 2 || x == 6 || z == 8 || z == 12;
				helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
				helper.setBlock(x, 4, z, Blocks.OAK_PLANKS);
				for (int y = 2; y <= 3; y++) {
					helper.setBlock(x, y, z, wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		ServerLevel level = helper.getLevel();
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
		BlockPos lower = helper.absolutePos(new BlockPos(6, 2, 10));
		level.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
		level.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
		Services.watch().onPlaced(builder, level, lower, level.getBlockState(lower));
		return lower;
	}

	private static Friends homeOwner(GameTestHelper helper, Vec3 at) {
		Friends.Known owner = new Friends.Known("sc3_home", 0, Friends.at(helper, new BlockPos(4, 2, 10)));
		Friends friends = new Friends(helper, Friends.state(helper, owner));
		ServerPlayer target = friends.join(owner, at);
		target.snapTo(target.getX(), target.getY(), target.getZ(), 270.0F, 0.0F);
		target.setYHeadRot(270.0F);
		return friends;
	}

	private static Tracks.Config shortTracks() {
		Tracks.Config config = new Tracks.Config();
		config.lengthMin = 10;
		config.lengthMax = 10;
		return config;
	}

	/**
	 * The line comes straight in from the open to the ground outside the door, a print every 2 cells alternating
	 * beside the line, stone taking none; ledgered, counted for the base.
	 */
	@GameTest(structure = BIG, skyAccess = true, maxTicks = 40)
	public void tracksComeStraightInToTheGroundOutsideTheDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		grass(helper);
		BlockPos lower = house(helper, builder(helper));
		for (int x = 10; x <= 12; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(x, 1, z, Blocks.STONE);
			}
		}
		Friends friends = homeOwner(helper, new Vec3(23.5, 2.0, 60.5));
		ServerPlayer target = friends.online.getFirst();
		Optional<Tracks.Door> door = Tracks.door(unseen(friends), level, helper.absolutePos(new BlockPos(4, 2, 10)));
		helper.assertTrue(door.isPresent() && door.get().lower().equals(lower) && door.get().outside().equals(lower.east()), "the door: " + door);

		Tracks.Outcome outcome = Tracks.walk(watched(friends, List.of(target)), target, Stage.TRACES, RandomSource.create(7), false, false, false, shortTracks());
		helper.assertTrue(outcome.result() == FireResult.FIRED, "no tracks: " + outcome.reason());
		List<BlockPos> prints = outcome.plan().prints();
		helper.assertTrue(prints.getLast().equals(helper.absolutePos(new BlockPos(7, 1, 10))), "the path does not end outside the door: " + prints);
		helper.assertTrue(prints.size() == 5, "5 prints (one column on the stone): " + prints);
		helper.assertTrue(prints.get(prints.size() - 2).equals(helper.absolutePos(new BlockPos(9, 1, 11))), "the second print is not beside the line");
		for (BlockPos print : prints) {
			helper.assertTrue(level.getBlockState(print).is(Blocks.DIRT), "a print is not bare dirt: " + print);
			BlockPos rel = rel(helper, print);
			helper.assertTrue(rel.getX() < 10 || rel.getX() > 12, "a print on the stone");
			helper.assertTrue(Math.abs(rel.getZ() - 10) <= 3, "the line wandered off: " + rel);
		}
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(11, 1, 10));
		helper.assertTrue(ledger(helper, Tracks.CAUSE).size() == outcome.plan().edits().size(), "not every edit is ledgered");
		helper.assertTrue(friends.data.sc().base(Friends.at(helper, new BlockPos(4, 2, 10))).tracks() == 1, "not counted for the base");
		helper.succeed();
	}

	/** The line itself: straight for the last cells before the door, then a wander of one block at most every 4. */
	@GameTest
	public void tracksLineWandersOneBlockEveryFourAndPrintsAlternate(GameTestHelper helper) {
		BlockPos outside = new BlockPos(100, 64, 100);
		for (int seed = 0; seed < 20; seed++) {
			List<BlockPos> line = Tracks.line(outside, 270.0F, 24, RandomSource.create(seed));
			helper.assertTrue(line.getFirst().equals(outside), "the line does not start at the door");
			for (int t = 1; t < line.size(); t++) {
				int dz = line.get(t).getZ() - line.get(t - 1).getZ();
				helper.assertTrue(line.get(t).getX() == outside.getX() + t, "not straight out east");
				helper.assertTrue(dz == 0 || t % Tracks.WANDER_EVERY == 0 && Math.abs(dz) == 1, "a wander off the beat at " + t + ": " + dz);
				helper.assertTrue(t >= Tracks.WANDER_EVERY || dz == 0, "a wander near the door");
			}
			List<BlockPos> steps = Tracks.steps(line, 270.0F, 2);
			for (int k = 0; k < steps.size(); k++) {
				BlockPos on = line.get(k * 2);
				helper.assertTrue(steps.get(k).equals(k % 2 == 0 ? on : on.south()), "print " + k + " does not alternate");
			}
		}
		List<BlockPos> curve = Tracks.curve(outside, 3.0, 0.0, 5);
		BlockPos a = curve.getFirst();
		BlockPos b = curve.get(curve.size() / 2);
		BlockPos c = curve.getLast();
		long cross = (long) (b.getX() - a.getX()) * (c.getZ() - a.getZ()) - (long) (b.getZ() - a.getZ()) * (c.getX() - a.getX());
		helper.assertTrue(curve.size() >= 3 && cross != 0, "the fake's prints are not a curve: " + curve);
		helper.succeed();
	}

	/** Each surface's print: grass, podzol and mycelium bare, snow one layer less, farmland and its crop, a plant; stone, sand and gravel none. */
	@GameTest(structure = BIG, skyAccess = true, maxTicks = 40)
	public void printsBareEachSurfaceAndSkipStoneSandAndGravel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		grass(helper);
		helper.setBlock(2, 2, 2, Blocks.SHORT_GRASS);
		helper.setBlock(4, 2, 2, Blocks.SNOW);
		helper.setBlock(6, 2, 2, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
		helper.setBlock(8, 1, 2, Blocks.FARMLAND);
		helper.setBlock(8, 2, 2, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 5));
		helper.setBlock(10, 1, 2, Blocks.PODZOL);
		helper.setBlock(12, 1, 2, Blocks.MYCELIUM);
		helper.setBlock(14, 1, 2, Blocks.STONE);
		helper.setBlock(16, 1, 2, Blocks.SAND);
		helper.setBlock(18, 1, 2, Blocks.GRAVEL);
		helper.setBlock(20, 1, 2, Blocks.COARSE_DIRT);
		helper.setBlock(20, 2, 2, Blocks.POPPY);
		helper.setBlock(22, 2, 2, Blocks.LEAF_LITTER);

		List<Prints.Edit> all = new ArrayList<>();
		for (int x = 2; x <= 22; x += 2) {
			BlockPos ground = Prints.ground(level, helper.absolutePos(new BlockPos(x, 1, 2)).getX(), helper.absolutePos(new BlockPos(x, 1, 2)).getZ(),
					helper.absolutePos(new BlockPos(x, 1, 2)).getY());
			helper.assertTrue(helper.absolutePos(new BlockPos(x, 1, 2)).equals(ground), "the ground under column " + x + ": " + ground);
			List<Prints.Edit> edits = Prints.at(level, ground);
			helper.assertTrue(edits.isEmpty() == (x == 14 || x == 16 || x == 18), "column " + x + " print: " + edits);
			all.addAll(edits);
		}
		TraceBatch batch = Services.traces().batch(level, "test:sc3/prints");
		Prints.queue(batch, all);
		helper.assertTrue(batch.commit(), "the prints were refused");
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(2, 1, 2));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 2, 2));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 2, 2));
		helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(4, 1, 2));
		helper.assertTrue(helper.getBlockState(new BlockPos(6, 2, 2)).getValue(SnowLayerBlock.LAYERS) == 2, "the snow is not one layer less");
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(8, 1, 2));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, 2, 2));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(10, 1, 2));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(12, 1, 2));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(14, 1, 2));
		helper.assertBlockPresent(Blocks.SAND, new BlockPos(16, 1, 2));
		helper.assertBlockPresent(Blocks.GRAVEL, new BlockPos(18, 1, 2));
		helper.assertBlockPresent(Blocks.COARSE_DIRT, new BlockPos(20, 1, 2));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(20, 2, 2));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(22, 1, 2));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(22, 2, 2));
		helper.succeed();
	}

	/** Watched, someone near a print, or the target close outside: nothing changes; the target inside the house is no bar. */
	@GameTest(structure = BIG, skyAccess = true, maxTicks = 40)
	public void tracksRefusedInViewOrNearChangeNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		grass(helper);
		house(helper, builder(helper));
		Friends friends = homeOwner(helper, new Vec3(23.5, 2.0, 60.5));
		ServerPlayer target = friends.online.getFirst();
		Tracks.Config config = shortTracks();

		Friends.Known watcherKnown = new Friends.Known("sc3_watcher", 0, null);
		ServerPlayer watcher = friends.join(watcherKnown, new Vec3(12.5, 14.0, 23.5));
		look(helper, watcher, new Vec3(12.5, 14.0, 23.5), new Vec3(12.5, 1.5, 10.5));
		Tracks.Outcome seen = Tracks.walk(watched(friends, List.of(target, watcher)), target, Stage.TRACES, RandomSource.create(7), false, false, false, config);
		helper.assertTrue(seen.result() == FireResult.NO_SPOT && seen.reason().contains("in view"), "watched: " + seen.reason());

		moveTo(helper, watcher, new Vec3(14.5, 2.0, 14.5));
		Tracks.Outcome near = Tracks.walk(unseen(friends), target, Stage.TRACES, RandomSource.create(7), false, false, false, config);
		helper.assertTrue(near.result() == FireResult.NO_SPOT && near.reason().contains("within 16"), "someone near: " + near.reason());
		friends.leave(watcher);

		moveTo(helper, target, new Vec3(20.5, 2.0, 20.5));
		Tracks.Outcome close = Tracks.walk(unseen(friends), target, Stage.TRACES, RandomSource.create(7), false, false, false, config);
		helper.assertTrue(close.result() == FireResult.NO_SPOT && close.reason().contains("of the door"), "the target close outside: " + close.reason());
		for (int x = 7; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(x, 1, z));
			}
		}

		moveTo(helper, target, new Vec3(4.5, 2.0, 10.5));
		Tracks.Outcome inside = Tracks.walk(unseen(friends), target, Stage.TRACES, RandomSource.create(7), false, false, false, config);
		helper.assertTrue(inside.result() == FireResult.FIRED, "the target inside the house held them back: " + inside.reason());
		helper.succeed();
	}

	/** From Proximity (forced here) the prints go round the house 1 to 2 blocks from its walls and back out beside the way in. */
	@GameTest(structure = BIG, skyAccess = true, maxTicks = 40)
	public void tracksGoRoundTheHouseAndBackOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		grass(helper);
		house(helper, builder(helper));
		Friends friends = homeOwner(helper, new Vec3(23.5, 2.0, 60.5));
		ServerPlayer target = friends.online.getFirst();
		Tracks.Outcome outcome = Tracks.walk(unseen(friends), target, Stage.PROXIMITY, RandomSource.create(9), false, false, true, shortTracks());
		helper.assertTrue(outcome.result() == FireResult.FIRED && outcome.plan().circled(), "no loop: " + outcome.reason());
		boolean west = false;
		boolean north = false;
		boolean south = false;
		int outBeside = 0;
		for (BlockPos print : outcome.plan().prints()) {
			BlockPos rel = rel(helper, print);
			helper.assertTrue(level.getBlockState(print).is(Blocks.DIRT), "a print is not bare: " + rel);
			helper.assertFalse(rel.getX() >= 2 && rel.getX() <= 6 && rel.getZ() >= 8 && rel.getZ() <= 12, "a print in the house: " + rel);
			west |= rel.getX() <= 1;
			north |= rel.getZ() <= 7 && rel.getX() <= 8;
			south |= rel.getZ() >= 13 && rel.getX() <= 8;
			if (rel.getX() >= 11) {
				outBeside++;
			}
		}
		helper.assertTrue(west && north && south, "the loop does not go round: west " + west + ", north " + north + ", south " + south + ": "
				+ outcome.plan().prints().stream().map(p -> rel(helper, p).toShortString()).toList());
		// Past the house's ring only the two lines print: the way in, and the way back out 2 blocks beside it.
		helper.assertTrue(outBeside >= 6, "the way back out is not beside the way in: " + outBeside + " prints past x 11");
		helper.succeed();
	}

	// --- 10 ---

	/** A test's 10: its own queue, a fog sink that records, and whether anyone sees. */
	private static final class Stood {
		final List<FogPlace> fog = new ArrayList<>();
		final WhereHeStood.Queue queue = new WhereHeStood.Queue();
		final boolean[] seen = {false};
		final Friends friends;
		final ScEnv env;

		Stood(GameTestHelper helper) {
			Friends.Known owner = new Friends.Known("sc3_seer", 0, null);
			friends = new Friends(helper, Friends.state(helper, owner));
			friends.join(owner, new Vec3(12.5, 2.0, 80.5));
			env = new ScEnv(friends.bases(), Services.traces(), (level, cells) -> !seen[0], level -> List.copyOf(friends.online),
					(server, place, max) -> fog.add(place), Services.protectedAreas());
		}

		UUID who() {
			return friends.online.getFirst().getUUID();
		}
	}

	/** Prints where he stood and the plants around it, only once out of view; the fog place with the section's numbers. */
	@GameTest(structure = BIG, skyAccess = true, maxTicks = 100)
	public void whereHeStoodPrintsOnceOutOfViewAndSetsTheFogPlace(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		grass(helper);
		helper.setBlock(11, 2, 10, Blocks.SHORT_GRASS);
		helper.setBlock(10, 2, 12, Blocks.POPPY);
		helper.setBlock(12, 2, 10, Blocks.FERN);
		helper.setBlock(13, 2, 10, Blocks.POPPY);
		Vec3 stoodAt = helper.absoluteVec(new Vec3(10.5, 2.0, 10.5));
		CompletableFuture<?> lit = WorldSignatureTests.lightSettled(level, helper.absolutePos(new BlockPos(10, 2, 10)));
		Stood stood = new Stood(helper);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(lit.isDone(), "the light is not settled yet");
			if (!done[0]) {
				done[0] = true;
				WhereHeStood.Config config = new WhereHeStood.Config();
				String did = WhereHeStood.figureLeft(stood.env, stood.queue, level, stoodAt, 0.0F, List.of(), "ridge", stood.who(), true,
						new WhereHeStood.Rolls(true, false, true), RandomSource.create(5), config);
				helper.assertTrue(stood.queue.pending().size() == 1, "nothing queued: " + did);
				stood.seen[0] = true;
				helper.assertTrue(stood.queue.tick(stood.env, level.getServer().getTickCount()) == 0, "printed in view");
				helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(10, 1, 10));
				helper.assertBlockPresent(Blocks.SHORT_GRASS, new BlockPos(11, 2, 10));
				stood.seen[0] = false;
				helper.assertTrue(stood.queue.tick(stood.env, level.getServer().getTickCount()) == 1 && stood.queue.pending().isEmpty(), "not printed out of view");

				helper.assertTrue(stood.fog.size() == 1, "no fog place");
				FogPlace place = stood.fog.getFirst();
				long length = place.untilClock() - place.fromClock();
				helper.assertTrue(place.center().equals(stoodAt) && place.radius() == 40.0F && place.level() == 0.45F && place.rampTicks() == 1200
						&& place.fadeTicks() == 1200 && place.dimension() == Level.OVERWORLD && length >= 24000 && length <= 72000, "the fog place: " + place);
			}
			helper.assertBlockPresent(Blocks.DIRT, new BlockPos(10, 1, 10));
			BlockPos beside = rel(helper, WhereHeStood.beside(helper.absolutePos(new BlockPos(10, 1, 10)), 0.0F, 1));
			BlockPos other = rel(helper, WhereHeStood.beside(helper.absolutePos(new BlockPos(10, 1, 10)), 0.0F, -1));
			helper.assertTrue(helper.getBlockState(beside).is(Blocks.DIRT) || helper.getBlockState(other).is(Blocks.DIRT), "no second print across his facing");
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(11, 2, 10));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(10, 2, 12));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(12, 2, 10));
			helper.assertBlockPresent(Blocks.POPPY, new BlockPos(13, 2, 10));
		});
	}

	/** Dropped after the retry minutes; with the run roll, a print at each sample of his way out. */
	@GameTest(structure = BIG, skyAccess = true, maxTicks = 100)
	public void whereHeStoodDropsAfterTenMinutesAndPrintsHisWayOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		grass(helper);
		Vec3 stoodAt = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		CompletableFuture<?> lit = WorldSignatureTests.lightSettled(level, helper.absolutePos(new BlockPos(4, 2, 4)));
		Stood stood = new Stood(helper);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(lit.isDone(), "the light is not settled yet");
			if (!done[0]) {
				done[0] = true;
				WhereHeStood.Config config = new WhereHeStood.Config();
				long now = level.getServer().getTickCount();
				List<Vec3> path = WhereHeStood.pathAway(stoodAt, 270.0F, 6);
				WhereHeStood.figureLeft(stood.env, stood.queue, level, stoodAt, 270.0F, path, "close", stood.who(), true, new WhereHeStood.Rolls(true, true, false),
						RandomSource.create(1), config);
				helper.assertTrue(stood.queue.pending().getFirst().run().size() == 6, "the way out's columns: " + stood.queue.pending());
				stood.queue.tick(stood.env, now + Math.round(config.retryMinutes * 60 * 20) + 2);
				helper.assertTrue(stood.queue.pending().isEmpty() && stood.fog.isEmpty(), "not dropped after the retry minutes");
				helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(4, 1, 4));

				WhereHeStood.figureLeft(stood.env, stood.queue, level, stoodAt, 270.0F, path, "close", stood.who(), true, new WhereHeStood.Rolls(true, true, false),
						RandomSource.create(1), config);
				helper.assertTrue(stood.queue.tick(stood.env, now) == 1, "not printed");
			}
			helper.assertBlockPresent(Blocks.DIRT, new BlockPos(4, 1, 4));
			for (int i = 1; i <= 6; i++) {
				helper.assertBlockPresent(Blocks.DIRT, new BlockPos(4 + 2 * i, 1, 4));
			}
		});
	}

	/** Nothing underground, in the Nether, unseen, for nobody in the story, or for the glimpse, the last one and the End's and Nether's. */
	@GameTest(structure = BIG, skyAccess = true)
	public void whereHeStoodLeavesNothingUndergroundUnseenOrForTheGlimpse(GameTestHelper helper) {
		helper.assertTrue(WhereHeStood.refusal(Level.OVERWORLD, 15, true, true, "ridge") == null, "a seen ridge figure on the surface marks nothing");
		helper.assertTrue(WhereHeStood.refusal(Level.NETHER, 15, true, true, "ridge") != null, "the Nether");
		helper.assertTrue(WhereHeStood.refusal(Level.OVERWORLD, 9, true, true, "ridge") != null, "underground (sky light 9)");
		helper.assertTrue(WhereHeStood.refusal(Level.OVERWORLD, 15, false, true, "ridge") != null, "unseen");
		helper.assertTrue(WhereHeStood.refusal(Level.OVERWORLD, 15, true, false, "ridge") != null, "for nobody in the story");
		for (String variant : List.of("glimpse", "last_one", "among_endermen", "among_piglins")) {
			helper.assertTrue(WhereHeStood.refusal(Level.OVERWORLD, 15, true, true, variant) != null, "the " + variant + " marks");
		}
		Stood stood = new Stood(helper);
		Vec3 stoodAt = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		WhereHeStood.figureLeft(stood.env, stood.queue, helper.getLevel(), stoodAt, 0.0F, List.of(), "ridge", stood.who(), false, WhereHeStood.Rolls.ALL,
				RandomSource.create(1), new WhereHeStood.Config());
		WhereHeStood.figureLeft(stood.env, stood.queue, helper.getLevel(), stoodAt, 0.0F, List.of(), "glimpse", stood.who(), true, WhereHeStood.Rolls.ALL,
				RandomSource.create(1), new WhereHeStood.Config());
		WhereHeStood.figureLeft(stood.env, stood.queue, helper.getLevel(), stoodAt, 0.0F, List.of(), "ridge", UUID.randomUUID(), true, WhereHeStood.Rolls.ALL,
				RandomSource.create(1), new WhereHeStood.Config());
		helper.assertTrue(stood.queue.pending().isEmpty() && stood.fog.isEmpty(), "something queued: " + stood.queue.pending() + " " + stood.fog);
		helper.succeed();
	}

	/** The world's fog places: at most 4 at full at once (the oldest ends early with its fade); put back as it was. */
	@GameTest
	public void whereHeStoodFogPlacesAreCappedAtFour(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<FogPlace> before = HerobrineState.get(server).effects().fogPlaces();
		long now = server.overworld().getDefaultClockTime();
		WhereHeStood.Config config = new WhereHeStood.Config();
		try {
			ClientEffects.clearFogPlaces(server);
			for (int i = 0; i < 5; i++) {
				FogPlace place = WhereHeStood.fogPlace(new Vec3(1.0e6 + i * 1000.0, 64.0, 1.0e6), now, 2.0, config);
				helper.assertTrue(ClientEffects.addFogPlace(server, place, config.maxFogPlaces), "place " + i + " refused");
			}
			List<FogPlace> places = HerobrineState.get(server).effects().fogPlaces();
			long full = places.stream().filter(p -> !p.fadingBy(now)).count();
			helper.assertTrue(places.size() == 5 && full == 4, "places " + places.size() + ", at full " + full);
		} finally {
			ClientEffects.clearFogPlaces(server);
			for (FogPlace place : before) {
				ClientEffects.addFogPlace(server, place, ClientEffects.FOG_PLACES_MAX);
			}
		}
		helper.succeed();
	}

	// --- the cards, the gates and the switch ---

	/** The three cards are registered as the design's table says. */
	@GameTest
	public void theCardsAreRegisteredAsTheTableSays(GameTestHelper helper) {
		check(helper, "sc_08", Tier.MINOR, Stage.TRACES, Set.of(CardTag.SCAR), Set.of(Habit.VISITOR, Habit.COLLECTOR), false);
		check(helper, "sc_09", Tier.MINOR, Stage.TRACES, Set.of(CardTag.SCAR), Set.of(Habit.VISITOR, Habit.WATCHER), true);
		check(helper, "sc_11", Tier.MINOR, Stage.TRACES, Set.of(CardTag.LIGHT, CardTag.SCAR), Set.of(Habit.MOURNER, Habit.CARVER), false);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, String id, Tier tier, Stage stage, Set<CardTag> tags, Set<Habit> habits, boolean fake) {
		Optional<EventCard> card = CardRegistry.get(id);
		helper.assertTrue(card.isPresent(), id + " is not registered");
		EventCard c = card.get();
		helper.assertTrue(c.tier() == tier && c.earliestStage() == stage && c.tags().equals(tags) && c.habits().equals(habits) && c.hasFake() == fake, id + " is not as the table says");
	}

	/** The common gates: the story's quiet flags, the overworld, the target's combat. */
	@GameTest
	public void theCommonGatesHoldTheLayerBack(GameTestHelper helper) {
		Friends.Known owner = new Friends.Known("sc3_gates", 0, null);
		Friends friends = new Friends(helper, Friends.state(helper, owner));
		ServerPlayer player = friends.join(owner, new Vec3(1.5, 2.0, 1.5));
		helper.assertTrue(ScGates.worldRefusal(friends.state) == null, "a fresh story refuses");
		helper.assertTrue(ScGates.targetRefusal(friends.state, friends.watch, player, helper.getLevel(), false) == null, "a fresh player refuses");
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		if (nether != null) {
			helper.assertTrue(ScGates.targetRefusal(friends.state, friends.watch, player, nether, false) != null, "the Nether is open");
		}
		helper.assertTrue(ScGates.combatRefusal(ScGates.NO_COMBAT_SECONDS * 20L - 1) != null && ScGates.combatRefusal(ScGates.NO_COMBAT_SECONDS * 20L) == null,
				"the combat gate");
		for (String flag : ScGates.QUIET_FLAGS) {
			friends.state.setFlag(flag, true);
			helper.assertTrue(ScGates.worldRefusal(friends.state) != null, flag + " does not hold the layer back");
			friends.state.setFlag(flag, false);
		}
		friends.state.setFlag("ending:ended", true);
		Turned.Outcome turned = Turned.turn(unseen(friends), player, Stage.PROXIMITY, RandomSource.create(1), true, new Turned.Config());
		Tracks.Outcome tracks = Tracks.walk(unseen(friends), player, Stage.PROXIMITY, RandomSource.create(1), false, true, true, new Tracks.Config());
		helper.assertTrue(turned.result() == FireResult.SKIPPED && tracks.result() == FireResult.SKIPPED, "a forced fire after the end: " + turned + " " + tracks);
		helper.succeed();
	}

	/** The switch's give-back: every entry of a cause loaded now goes back once, newest first; again does nothing. */
	@GameTest(structure = BIG, maxTicks = 40)
	public void switchedOffGivesBackWhatIsLoadedOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 23, 1, 23)) {
			helper.setBlock(rel, Blocks.GRASS_BLOCK);
		}
		helper.setBlock(5, 2, 5, Blocks.POPPY);
		String cause = Tracks.CAUSE + "/test-" + helper.absolutePos(BlockPos.ZERO).toShortString();
		TraceBatch batch = Services.traces().batch(level, cause);
		Prints.queue(batch, Prints.at(level, helper.absolutePos(new BlockPos(5, 1, 5))));
		helper.assertTrue(batch.commit(), "the print was refused");
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(5, 1, 5));
		helper.assertTrue(WorldSc.giveBack(level.getServer(), cause) == 2, "not both edits given back");
		helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(5, 1, 5));
		helper.assertBlockPresent(Blocks.POPPY, new BlockPos(5, 2, 5));
		helper.assertTrue(WorldSc.giveBack(level.getServer(), cause) == 0, "given back twice");
		helper.succeed();
	}
}
