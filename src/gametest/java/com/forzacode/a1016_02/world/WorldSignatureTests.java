package com.forzacode.a1016_02.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.world.gen.Builds;
import com.forzacode.a1016_02.world.sig.CrossRow;
import com.forzacode.a1016_02.world.sig.HouseCopier;
import com.forzacode.a1016_02.world.sig.HouseCopyState;
import com.forzacode.a1016_02.world.sig.HouseShell;
import com.forzacode.a1016_02.world.sig.LoneTorch;
import com.forzacode.a1016_02.world.sig.SiteSink;
import com.forzacode.a1016_02.world.sig.StillBurning;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the signatures (still burning, the house copy, the row of crosses) and the lone redstone torch.
 * They keep the shared world untouched: their own {@link SignatureData}, sites collected in a list, no player. The
 * big builds use the 24x16x24 {@value #BIG} structure.
 */
public class WorldSignatureTests extends WorldCrossTests {
	static final String BIG = "a1016_02:world/signature";

	private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			helper.setBlock(pos, block);
		}
	}

	private static List<TraceLedger.Entry> ledger(GameTestHelper helper, String cause) {
		return TraceLedger.get(helper.getLevel().getServer()).entries().stream()
				.filter(e -> e.cause().equals(cause) && helper.getBounds().contains(Vec3.atCenterOf(e.pos().pos()))).toList();
	}

	// --- once per world ---

	@GameTest
	public void eachSignatureHappensAtMostOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SignatureData data = new SignatureData();
		GlobalPos here = GlobalPos.of(level.dimension(), helper.absolutePos(BlockPos.ZERO));
		helper.assertTrue(StillBurning.refusal(data, false) == null, "a fresh world refuses still burning");
		helper.assertTrue(CrossRow.refusal(data) == null, "a fresh world refuses the row of crosses");
		helper.assertTrue(HouseCopier.refusal(data) == null, "a fresh world refuses the house copy");

		data.setStillBurning(new SignatureData.Burning(here, here, Optional.empty(), 3));
		data.setCrossRow(new SignatureData.CrossRow(List.of(here), 4));
		data.setHouseCopy(new HouseCopyState(level.dimension(), here.pos(), List.of(), List.of(), Optional.empty(), -1, List.of(), 0,
				HouseCopyState.Phase.SITE, 21));
		helper.assertTrue(StillBurning.refusal(data, false) != null, "still burning can happen twice");
		helper.assertTrue(CrossRow.refusal(data) != null, "the row of crosses can happen twice");
		helper.assertTrue(HouseCopier.refusal(data) != null, "the house copy can be started twice");

		// D-004: once lore left F21's still-burning furnace itself, the camp never comes.
		helper.assertTrue(StillBurning.refusal(new SignatureData(), true) != null, "still burning after lore's F21 furnace");
		helper.succeed();
	}

	// --- still burning (D-004) ---

	@GameTest(structure = BIG, maxTicks = 60)
	public void stillBurningCampWithF21(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		fill(helper, 0, 0, 0, 23, 1, 23, Blocks.STONE);
		WorldConfig config = new WorldConfig();
		BlockPos anchor = helper.absolutePos(new BlockPos(5, 1, 12));
		StillBurning.Plan alone = StillBurning.plan(level, anchor.getX(), anchor.getY(), anchor.getZ(), Direction.NORTH, Builds.Wood.OAK, 7L, false, config);
		helper.assertTrue(alone.house() == null, "an emptied house planned without F21");

		StillBurning.Plan plan = StillBurning.plan(level, anchor.getX(), anchor.getY(), anchor.getZ(), Direction.NORTH, Builds.Wood.OAK, 7L, true, config);
		helper.assertTrue(plan.house() != null, "no emptied house with F21");
		List<SiteRegistry.Site> sites = new ArrayList<>();
		StillBurning.Camp camp = StillBurning.build(level, plan, Services.traces(), SiteSink.collecting(sites));
		helper.assertTrue(camp != null, "the out-of-view camp was refused");

		BlockState furnace = level.getBlockState(camp.furnace());
		helper.assertTrue(furnace.is(Blocks.FURNACE) && furnace.getValue(AbstractFurnaceBlock.LIT), "the furnace is not lit: " + furnace);
		FurnaceBlockEntity entity = (FurnaceBlockEntity) level.getBlockEntity(camp.furnace());
		CompoundTag data = entity.saveCustomOnly(level.registryAccess());
		int lit = data.getIntOr("lit_time_remaining", 0);
		int input = entity.getItem(0).getCount();
		helper.assertTrue(input >= config.stillBurningInputCount - 1 && !entity.getItem(0).isEmpty(), "nothing waits to smelt: " + entity.getItem(0));
		helper.assertTrue(entity.getItem(1).is(Items.COAL), "no fuel left beside it");
		// Furnaces only tick while loaded: it must still burn for the whole input, about 13 minutes of ticking.
		helper.assertTrue(lit >= Math.max(config.stillBurningLitTicks, input * 200) - 40, "it burns only " + lit + " ticks");

		SiteRegistry.Site build = sites.stream().filter(s -> s.type() == SiteType.ABANDONED_BUILD).findFirst().orElseThrow();
		helper.assertTrue(build.claimedBy().equals(Optional.of(StillBurning.CLAIM)), "the camp's build can be emptied by the emptied-house card");
		SiteRegistry.Site house = sites.stream().filter(s -> s.type() == SiteType.EMPTIED_HOUSE).findFirst().orElseThrow();
		helper.assertTrue(house.claimedBy().isEmpty(), "F21's emptied house is claimed");
		int r = Math.max(3, house.size());
		BlockPos d = camp.furnace().subtract(house.pos());
		helper.assertTrue(Math.abs(d.getX()) <= r && Math.abs(d.getZ()) <= r && d.getY() >= -StillBurning.LORE_SCAN_BELOW && d.getY() <= StillBurning.LORE_SCAN_ABOVE,
				"lore's F21 scan of the emptied house misses the furnace: " + d.toShortString() + " size " + house.size());
		BoundingBox inside = plan.house().interior();
		for (BlockPos pos : BlockPos.betweenClosed(inside.minX(), inside.minY(), inside.minZ(), inside.maxX(), inside.maxY(), inside.maxZ())) {
			helper.assertTrue(level.getBlockState(pos).isAir(), "the emptied house is not empty at " + pos.toShortString());
		}
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	// --- your house, elsewhere (D-005) ---

	/** A 3x4 house: cobblestone floor (y 2), oak plank walls (y 3-4), spruce roof (y 5), a door and a crafting table. */
	private static List<BlockPos> buildHouse(GameTestHelper helper, ServerPlayer builder) {
		List<BlockPos> placed = new ArrayList<>();
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 5; z++) {
				placed.add(put(helper, builder, new BlockPos(x, 2, z), Blocks.COBBLESTONE.defaultBlockState()));
				placed.add(put(helper, builder, new BlockPos(x, 5, z), Blocks.SPRUCE_PLANKS.defaultBlockState()));
				boolean edge = x == 2 || x == 4 || z == 2 || z == 5;
				boolean door = x == 3 && z == 5;
				for (int y = 3; y <= 4 && edge && !door; y++) {
					placed.add(put(helper, builder, new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState()));
				}
			}
		}
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		placed.add(put(helper, builder, new BlockPos(3, 3, 5), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
		placed.add(put(helper, builder, new BlockPos(3, 4, 5), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
		placed.add(put(helper, builder, new BlockPos(3, 3, 3), Blocks.CRAFTING_TABLE.defaultBlockState()));
		return placed;
	}

	private static BlockPos put(GameTestHelper helper, ServerPlayer builder, BlockPos rel, BlockState state) {
		helper.setBlock(rel, state);
		BlockPos abs = helper.absolutePos(rel);
		Services.watch().onPlaced(builder, helper.getLevel(), abs, state);
		return abs;
	}

	/** Independent of HouseShell: can anything walk (empty collision) from the room to outside the house's box? */
	private static boolean openToOutside(ServerLevel level, BlockPos room, BoundingBox house) {
		BoundingBox reach = house.inflatedBy(1);
		Set<BlockPos> seen = new HashSet<>(List.of(room));
		Deque<BlockPos> queue = new ArrayDeque<>(List.of(room));
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			if (!house.isInside(pos)) {
				return true;
			}
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (reach.isInside(n) && seen.add(n) && level.getBlockState(n).getCollisionShape(level, n).isEmpty()) {
					queue.add(n);
				}
			}
		}
		return false;
	}

	/**
	 * Once Ending D is complete (or the story is over), nothing more leaves the house: the live tick cancels a pending
	 * finish and moves nothing, even with blocks due. The world's own copy state and flags are put back afterwards.
	 */
	@GameTest(structure = BIG, maxTicks = 40)
	public void houseCopyStopsOnceTheStoryIsOver(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer builder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		fill(helper, 0, 0, 0, 7, 1, 8, Blocks.STONE);
		fill(helper, 10, 0, 10, 20, 4, 21, Blocks.STONE);
		List<BlockPos> house = buildHouse(helper, builder);
		List<BlockState> before = house.stream().map(level::getBlockState).toList();
		WorldConfig config = new WorldConfig();
		HouseShell.Capture capture = HouseShell.capture(level, List.of(helper.absolutePos(new BlockPos(3, 3, 3))), config);
		helper.assertTrue(capture != null && capture.closed(), "the closed house was not captured");
		HouseCopyState state = HouseCopyState.captured(level.dimension(), capture, 21, 0);
		state = HouseCopier.placeSite(level, state, helper.absolutePos(new BlockPos(14, 5, 14)), SiteSink.collecting(new ArrayList<>()), 0);

		net.minecraft.server.MinecraftServer server = level.getServer();
		SignatureData data = WorldData.get(server).signatures();
		Optional<HouseCopyState> worldCopy = data.houseCopy();
		com.forzacode.a1016_02.core.HerobrineState flags = com.forzacode.a1016_02.core.HerobrineState.get(server);
		Set<String> hadFlags = new HashSet<>(flags.flags());
		try {
			for (String flag : HouseCopier.STOP_FLAGS) {
				HouseCopier.STOP_FLAGS.forEach(f -> flags.setFlag(f, false));
				// Ending B asked for the finish, and a building step is due (nextStep 0): both would move blocks now.
				data.setHouseCopy(state.withPhase(HouseCopyState.Phase.FINISHING));
				flags.setFlag(flag, true);
				helper.assertTrue(HouseCopier.stopped(server) && !HouseCopier.requestFinish(server), "a finish was taken after " + flag);
				for (int i = 0; i < 3; i++) {
					HouseCopier.tick(server);
				}
				HouseCopyState after = data.houseCopy().orElseThrow();
				helper.assertTrue(after.phase() == HouseCopyState.Phase.BUILDING && after.moved().isEmpty(),
						"after " + flag + " the copy is " + after.phase() + " with " + after.moved().size() + " moved");
				for (int i = 0; i < house.size(); i++) {
					helper.assertTrue(level.getBlockState(house.get(i)) == before.get(i), "a block left the house after " + flag + ": "
							+ house.get(i).toShortString());
				}
				helper.assertTrue(ledger(helper, HouseCopier.CAUSE).isEmpty() && ledger(helper, HouseCopier.CAUSE_LOCAL).isEmpty(),
						"the copy ledgered moves after " + flag);
			}
			// Without the flags, B's leaving cancels a pending finish too (the copy goes back to growing).
			HouseCopier.STOP_FLAGS.forEach(f -> flags.setFlag(f, false));
			data.setHouseCopy(state.withPhase(HouseCopyState.Phase.FINISHING));
			helper.assertTrue(HouseCopyApi.cancelFinish(server) && data.houseCopy().orElseThrow().phase() == HouseCopyState.Phase.BUILDING,
					"cancelFinish did not go back to building");
			helper.assertFalse(HouseCopyApi.cancelFinish(server), "cancelled a finish that was not pending");
		} finally {
			data.setHouseCopy(worldCopy.orElse(null));
			HouseCopier.STOP_FLAGS.forEach(f -> flags.setFlag(f, hadFlags.contains(f)));
		}
		helper.succeed();
	}

	/**
	 * Ending C, or the director silent for good: the copy waits and nothing leaves the house, even with a step due.
	 * Once C is undone (he is named again) and the silence lifts, it carries on.
	 */
	@GameTest(structure = BIG, maxTicks = 40)
	public void houseCopyWaitsWhileTheWorldIsQuiet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer builder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		fill(helper, 0, 0, 0, 7, 1, 8, Blocks.STONE);
		fill(helper, 10, 0, 10, 20, 4, 21, Blocks.STONE);
		List<BlockPos> house = buildHouse(helper, builder);
		List<BlockState> before = house.stream().map(level::getBlockState).toList();
		HouseShell.Capture capture = HouseShell.capture(level, List.of(helper.absolutePos(new BlockPos(3, 3, 3))), new WorldConfig());
		helper.assertTrue(capture != null && capture.closed(), "the closed house was not captured");
		HouseCopyState state = HouseCopier.placeSite(level, HouseCopyState.captured(level.dimension(), capture, 21, 0),
				helper.absolutePos(new BlockPos(14, 5, 14)), SiteSink.collecting(new ArrayList<>()), 0);
		helper.assertTrue(state.phase() == HouseCopyState.Phase.BUILDING && state.nextStep() <= 0, "the copy is not building with a step due");

		net.minecraft.server.MinecraftServer server = level.getServer();
		SignatureData data = WorldData.get(server).signatures();
		Optional<HouseCopyState> worldCopy = data.houseCopy();
		com.forzacode.a1016_02.core.HerobrineState flags = com.forzacode.a1016_02.core.HerobrineState.get(server);
		Set<String> hadFlags = new HashSet<>(flags.flags());
		List<String> touched = new ArrayList<>(HouseCopier.QUIET_FLAGS);
		touched.addAll(HouseCopier.STOP_FLAGS);
		try {
			touched.forEach(f -> flags.setFlag(f, false));
			for (String flag : HouseCopier.QUIET_FLAGS) {
				data.setHouseCopy(state);
				flags.setFlag(flag, true);
				for (int i = 0; i < 3; i++) {
					HouseCopier.tick(server);
				}
				HouseCopyState after = data.houseCopy().orElseThrow();
				helper.assertTrue(HouseCopier.paused(server) && after.phase() == HouseCopyState.Phase.BUILDING && after.moved().isEmpty(),
						"with " + flag + " the copy is " + after.phase() + " with " + after.moved().size() + " moved");
				for (int i = 0; i < house.size(); i++) {
					helper.assertTrue(level.getBlockState(house.get(i)) == before.get(i), "a block left the house with " + flag + ": "
							+ house.get(i).toShortString());
				}
				helper.assertTrue(ledger(helper, HouseCopier.CAUSE).isEmpty(), "the copy ledgered moves with " + flag);
				flags.setFlag(flag, false);
			}
			// C undone, the silence lifted: the copy carries on.
			data.setHouseCopy(state);
			helper.assertFalse(HouseCopier.paused(server) || HouseCopier.stopped(server), "still paused with no quiet flag");
			HouseCopier.tick(server);
			helper.assertTrue(!data.houseCopy().orElseThrow().moved().isEmpty() && !ledger(helper, HouseCopier.CAUSE).isEmpty(),
					"the copy did not carry on once the world was no longer quiet");
		} finally {
			data.setHouseCopy(worldCopy.orElse(null));
			touched.forEach(f -> flags.setFlag(f, hadFlags.contains(f)));
		}
		helper.succeed();
	}

	@GameTest(structure = BIG, maxTicks = 80)
	public void houseCopyOnlyMovesNeverTheRoofAndKeepsTheHouseClosed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer builder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		fill(helper, 0, 0, 0, 7, 1, 8, Blocks.STONE);
		fill(helper, 10, 0, 10, 20, 4, 21, Blocks.STONE); // the ground under the copy
		buildHouse(helper, builder);
		BlockPos room = helper.absolutePos(new BlockPos(3, 3, 4));
		BoundingBox house = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(4, 5, 5)));
		List<BlockPos> roof = new ArrayList<>();
		BlockPos.betweenClosed(helper.absolutePos(new BlockPos(2, 5, 2)), helper.absolutePos(new BlockPos(4, 5, 5))).forEach(p -> roof.add(p.immutable()));

		WorldConfig config = new WorldConfig();
		config.houseCopyFinishSearch = 4;
		config.houseCopyFinishDepth = 3;
		HouseShell.Capture capture = HouseShell.capture(level, List.of(helper.absolutePos(new BlockPos(3, 3, 3))), config);
		helper.assertTrue(capture != null && capture.closed(), "the closed house was not captured");
		long roofTargets = capture.targets().stream().filter(HouseCopyState.Target::roof).count();
		helper.assertTrue(roofTargets == roof.size(), "roof targets " + roofTargets);
		helper.assertTrue(capture.targets().stream().noneMatch(t -> t.state().is(Blocks.CRAFTING_TABLE) || t.state().getBlock() instanceof DoorBlock),
				"furniture or the door is part of the shell");
		// The crafting table's cell is inside too: furniture never keeps the house closed.
		helper.assertTrue(capture.interior().size() == 4, "the inside is " + capture.interior());

		HouseCopyState state = HouseCopyState.captured(level.dimension(), capture, 21, 0);
		state = HouseCopier.placeSite(level, state, helper.absolutePos(new BlockPos(14, 5, 14)), SiteSink.collecting(new ArrayList<>()), 0);
		helper.assertTrue(state.phase() == HouseCopyState.Phase.BUILDING, "the copy does not start building");

		// A few at a time until the house has nothing more to give.
		int steps = 0;
		while (steps++ < 20) {
			HouseCopier.StepResult result = HouseCopier.step(level, state, 3, Services.traces());
			state = result.state();
			for (BlockPos pos : roof) {
				helper.assertTrue(level.getBlockState(pos).is(Blocks.SPRUCE_PLANKS), "the roof lost " + pos.toShortString());
			}
			helper.assertFalse(openToOutside(level, room, house), "the real house is open after step " + steps);
			if (result.moved() == 0) {
				break;
			}
			helper.assertTrue(result.moved() <= 3, "more than a few blocks in one step");
		}
		helper.assertTrue(state.movedFromHouse() >= 4, "only " + state.movedFromHouse() + " block(s) moved");
		helper.assertBlockPresent(Blocks.CRAFTING_TABLE, new BlockPos(3, 3, 3));
		helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(3, 3, 5))).getBlock() instanceof DoorBlock, "the door went");

		// Ending B: the copy gets finished, with the house still closed and its roof whole.
		for (int pass = 0; pass < 5 && !HouseCopier.openTargets(level, state).isEmpty(); pass++) {
			state = HouseCopier.finish(level, state, Services.traces(), config).state();
		}
		helper.assertTrue(HouseCopier.openTargets(level, state).isEmpty(), HouseCopier.openTargets(level, state).size() + " spots of the copy still open");
		for (BlockPos pos : roof) {
			helper.assertTrue(level.getBlockState(pos).is(Blocks.SPRUCE_PLANKS), "the finish took the roof at " + pos.toShortString());
		}
		helper.assertFalse(openToOutside(level, room, house), "the finish opened the real house");
		for (BlockPos rel : state.interior()) {
			helper.assertTrue(level.getBlockState(state.copyOrigin().orElseThrow().offset(rel)).isAir(), "the copy is not empty inside");
		}

		// Every block of the copy is a ledgered move (Ending D can put each back), none was left or removed.
		List<TraceLedger.Entry> house1 = ledger(helper, HouseCopier.CAUSE);
		List<TraceLedger.Entry> local = ledger(helper, HouseCopier.CAUSE_LOCAL);
		helper.assertTrue(house1.size() == state.movedFromHouse() && local.size() == state.moved().size() - state.movedFromHouse(),
				"ledger " + house1.size() + "+" + local.size() + " for " + state.moved().size() + " moves");
		for (TraceLedger.Entry entry : house1) {
			helper.assertTrue(entry.kind() == TraceLedger.Kind.MOVE, "the house copy ledgered a " + entry.kind());
		}
		for (TraceLedger.Entry entry : local) {
			helper.assertTrue(entry.kind() == TraceLedger.Kind.MOVE, "the finish ledgered a " + entry.kind());
		}
		for (HouseCopyState.Moved moved : state.moved()) {
			helper.assertTrue(level.getBlockState(moved.from()).isAir(), "a moved block is still at " + moved.from().toShortString());
			helper.assertTrue(level.getBlockState(moved.to()) == moved.state(), "the copy lacks " + moved.state() + " at " + moved.to().toShortString());
			helper.assertTrue(house1.stream().anyMatch(e -> e.pos().pos().equals(moved.from()) && e.to().equals(Optional.of(moved.to())))
					|| local.stream().anyMatch(e -> e.pos().pos().equals(moved.from()) && e.to().equals(Optional.of(moved.to()))),
					"no MOVE entry for " + moved.from().toShortString());
			if (moved.local()) {
				helper.assertTrue(moved.from().getY() <= helper.absolutePos(new BlockPos(0, 3, 0)).getY(), "a local block came from the surface");
			}
		}

		// The state survives a save.
		SignatureData saved = new SignatureData();
		saved.setHouseCopy(state);
		Tag tag = SignatureData.CODEC.encodeStart(NbtOps.INSTANCE, saved).getOrThrow();
		SignatureData loaded = SignatureData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(loaded.houseCopy().orElseThrow().equals(state), "the house copy does not survive a save");
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	// --- the row of crosses ---

	/**
	 * The row is his: Latin crosses (D-050) of moved local material only, the ledger shows a move for every block,
	 * and never glass (D-051), even with glass the nearest block under each old cross and around the fresh one.
	 */
	@GameTest(structure = BIG, maxTicks = 60)
	public void crossRowIsMovedMaterial(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		fill(helper, 0, 0, 0, 23, 4, 23, Blocks.STONE);
		fill(helper, 0, 5, 0, 23, 5, 23, Blocks.GRASS_BLOCK);
		List<BlockPos> glass = List.of(new BlockPos(2, 2, 12), new BlockPos(6, 2, 12), new BlockPos(10, 2, 12), new BlockPos(14, 2, 12),
				new BlockPos(18, 5, 11), new BlockPos(18, 5, 13), new BlockPos(17, 5, 11));
		glass.forEach(pos -> helper.setBlock(pos, Blocks.GLASS));
		WorldConfig config = new WorldConfig();
		config.crossRowMaterialRadius = 2;
		config.crossRowMaterialDepth = 4; // stay inside the test area
		BlockPos first = helper.absolutePos(new BlockPos(2, 6, 12));
		CrossRow.Plan plan = CrossRow.plan(level, first, Direction.EAST, 11L, config);
		helper.assertTrue(plan != null, "no row planned on flat ground: " + CrossRow.lastRefusal());
		helper.assertTrue(plan.crosses().size() == CrossRow.GONE.size() + 1 && plan.crosses().getLast().fresh(), "not one cross per gone name plus a fresh one");
		helper.assertTrue(plan.crosses().getLast().base().equals(helper.absolutePos(new BlockPos(18, 6, 12))), "the fresh cross is not where the glass is");
		for (CrossRow.Cross cross : plan.crosses()) {
			helper.assertTrue((cross.height() == 5 || cross.height() == 6)
					&& cross.blocks().equals(Builds.latinCross(cross.base(), cross.height(), Direction.Axis.X)), "not a Latin cross along the row: " + cross);
		}
		for (CrossRow.Move move : plan.moves()) {
			helper.assertFalse(level.getBlockState(move.from()).is(Blocks.GLASS), "the row takes glass from " + move.from().toShortString());
		}
		List<SiteRegistry.Site> sites = new ArrayList<>();
		helper.assertTrue(CrossRow.commit(level, plan, Services.traces(), SiteSink.collecting(sites)), "the out-of-view row was refused");

		List<TraceLedger.Entry> entries = ledger(helper, CrossRow.CAUSE);
		int blocks = plan.crosses().stream().mapToInt(c -> c.blocks().size()).sum();
		helper.assertTrue(entries.size() == blocks && entries.stream().allMatch(e -> e.kind() == TraceLedger.Kind.MOVE),
				entries.size() + " ledger entries for " + blocks + " cross blocks (not all moves)");
		int groundY = helper.absolutePos(new BlockPos(0, 5, 0)).getY();
		for (BlockPos pos : glass) {
			helper.assertBlockPresent(Blocks.GLASS, pos);
		}
		for (CrossRow.Cross cross : plan.crosses()) {
			BlockPos top = cross.base().above(cross.height() - 1);
			helper.assertTrue(level.getBlockState(top.east()).isAir() && level.getBlockState(top.west()).isAir()
					&& level.getBlockState(top.above()).isAir(), "the cross at " + cross.base().toShortString() + " is not 1 wide above its arms");
			for (BlockPos pos : cross.blocks()) {
				helper.assertFalse(level.getBlockState(pos).isAir(), "a cross lacks " + pos.toShortString());
				helper.assertFalse(level.getBlockState(pos).is(Blocks.GLASS), "the row has glass at " + pos.toShortString());
				TraceLedger.Entry move = entries.stream().filter(e -> e.to().equals(Optional.of(pos))).findFirst().orElse(null);
				helper.assertTrue(move != null, "no move into " + pos.toShortString());
				BlockPos from = move.pos().pos();
				helper.assertTrue(level.getBlockState(from).isAir(), "the source " + from.toShortString() + " was not emptied");
				if (cross.fresh()) {
					// The clue: the ground right around the fresh cross was dug out.
					helper.assertTrue(from.getY() == groundY && Math.abs(from.getX() - cross.base().getX()) <= 2 && Math.abs(from.getZ() - cross.base().getZ()) <= 2,
							"the fresh cross's block came from " + from.toShortString());
				} else {
					helper.assertTrue(from.getY() <= groundY - 3, "an old cross's block came from the surface at " + from.toShortString());
				}
			}
		}
		helper.assertTrue(sites.size() == plan.crosses().size() && sites.stream().allMatch(s -> s.type() == SiteType.CROSS
				&& s.claimedBy().equals(Optional.of(CrossRow.CLAIM))), "the crosses are not claimed CROSS sites: " + sites);
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	// --- the lone redstone torch (D-033) ---

	@GameTest
	public void loneRedstoneTorchKeepsItsCaps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldConfig config = new WorldConfig();
		GlobalPos somewhere = GlobalPos.of(level.dimension(), helper.absolutePos(BlockPos.ZERO));
		List<SignatureData.Spot> torches = new ArrayList<>();
		helper.assertTrue(LoneTorch.capRefusal(torches, 30, config) == null, "the first torch is refused");
		torches.add(new SignatureData.Spot(somewhere, 30));
		helper.assertTrue(LoneTorch.capRefusal(torches, 36, config) != null, "a torch 6 days after the last one");
		helper.assertTrue(LoneTorch.capRefusal(torches, 37, config) == null, "a torch 7 days after the last one is refused");
		torches.add(new SignatureData.Spot(somewhere, 37));
		torches.add(new SignatureData.Spot(somewhere, 44));
		helper.assertTrue(LoneTorch.capRefusal(torches, 400, config) != null, "a fourth torch");

		// Caves left a day ago (and not visited since), never near the base; tunnel ends too.
		long today = 50;
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		BlockPos farCave = base.offset(200, -30, 0);
		BlockPos freshCave = base.offset(-200, -30, 0);
		BlockPos homeCave = base.offset(20, -30, 0);
		BlockPos tunnel = base.offset(0, -20, 300);
		List<SignatureData.Spot> caves = List.of(new SignatureData.Spot(GlobalPos.of(level.dimension(), farCave), 40),
				new SignatureData.Spot(GlobalPos.of(level.dimension(), freshCave), 50), new SignatureData.Spot(GlobalPos.of(level.dimension(), homeCave), 10));
		List<SiteRegistry.Site> ends = List.of(new SiteRegistry.Site(1, SiteType.TUNNEL_END, level.dimension(), tunnel, 8, Optional.empty()));
		List<LoneTorch.Candidate> found = LoneTorch.candidates(level, caves, ends, base, today, config, (l, chunk) -> -1, RandomSource.create(1));
		helper.assertTrue(found.size() == 2 && found.stream().anyMatch(c -> c.pos().equals(farCave)) && found.stream().anyMatch(c -> c.pos().equals(tunnel)),
				"candidates " + found);
		ChunkPos farChunk = ChunkPos.containing(farCave);
		List<LoneTorch.Candidate> visited = LoneTorch.candidates(level, caves, List.of(), base, today, config,
				(l, chunk) -> chunk.equals(farChunk) ? today : -1, RandomSource.create(1));
		helper.assertTrue(visited.isEmpty(), "a cave visited today is a candidate");

		// The spots the subject stood in merge when close and stay capped.
		SignatureData data = new SignatureData();
		data.visitCave(GlobalPos.of(level.dimension(), farCave), 3, 12, 2);
		data.visitCave(GlobalPos.of(level.dimension(), farCave.offset(4, 0, 0)), 5, 12, 2);
		helper.assertTrue(data.caveSpots().size() == 1 && data.caveSpots().getFirst().day() == 5, "close cave spots did not merge");
		data.visitCave(GlobalPos.of(level.dimension(), freshCave), 6, 12, 2);
		data.visitCave(GlobalPos.of(level.dimension(), homeCave), 7, 12, 2);
		helper.assertTrue(data.caveSpots().size() == 2, "cave spots are not capped");

		// On dark cave floor it is left (never created); within the base radius it is not.
		fill(helper, 0, 0, 0, 6, 6, 6, Blocks.STONE);
		helper.setBlock(3, 1, 3, Blocks.AIR);
		helper.setBlock(3, 2, 3, Blocks.AIR);
		WorldConfig shallow = new WorldConfig();
		shallow.loneTorchMinDepth = 0;
		BlockPos pocket = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(LoneTorch.placeNear(level, pocket, 1, pocket.offset(10, 0, 0), shallow, Services.traces()) == null,
					"a torch within the base radius");
			BlockPos placed = LoneTorch.placeNear(level, pocket, 1, pocket.offset(500, 0, 0), shallow, Services.traces());
			helper.assertTrue(pocket.equals(placed), "no torch on the dark floor: " + placed);
			helper.assertBlockPresent(Blocks.REDSTONE_TORCH, new BlockPos(3, 1, 3));
			helper.assertTrue(ledger(helper, LoneTorch.CAUSE).isEmpty(), "the torch was ledgered (it is left by others)");
			helper.succeed();
		});
	}
}
