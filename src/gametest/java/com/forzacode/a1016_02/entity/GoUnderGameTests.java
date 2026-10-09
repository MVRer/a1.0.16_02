package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Goes under (D-030): he digs only ground he may dig, puts back only blocks of his own dig and only into the top of
 * his own shaft, and is never removed while anyone can see him. {@link EntityGameTests} extends this so they run
 * under its registered entrypoint.
 */
public class GoUnderGameTests extends RushAndDimensionGameTests {
	/** Over the whole 8x8: {@code base} up to y 2, dirt 3 to 5, {@code top} at 6. He stands on it at y 7. */
	private static void ground(GameTestHelper helper, Block base, Block top) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = 0; y <= 6; y++) {
					helper.setBlock(x, y, z, y <= 2 ? base : y <= 5 ? Blocks.DIRT : top);
				}
			}
		}
	}

	private static final Vec3 SHAFT_FEET = new Vec3(3.5, 7.0, 3.5);

	@GameTest(skyAccess = true, maxTicks = 300, padding = 8)
	public void goesUnderDigsOnlyHisColumnAndCoversItselfFromItsOwnDig(GameTestHelper helper) {
		ground(helper, Blocks.STONE, Blocks.GRASS_BLOCK);
		ServerLevel level = helper.getLevel();
		BlockPos top = helper.absolutePos(new BlockPos(3, 6, 3));
		// Someone 7 blocks south, looking at him: the dig and the cover happen in view, the removal never does.
		ServerPlayer watcher = mockPlayer(helper, helper.absoluteVec(new Vec3(3.5, 7.0, 10.5)), 180.0F, 20.0F);
		List<ServerPlayer> players = List.of(watcher);
		HimEntity him = watchedFigure(helper, Variant.RIDGE, SHAFT_FEET, 0.0F, players);
		int serverChunks = level.getServer().getPlayerList().getViewDistance();
		List<String> problems = new ArrayList<>();
		boolean[] seenDigging = {false};
		Vec3[] last = {him.position()};
		helper.runAfterDelay(3, () -> {
			Optional<String> refusal = him.forceGoUnder();
			helper.assertTrue(refusal.isEmpty(), "he would not go under: " + refusal.orElse(""));
		});
		helper.onEachTick(() -> {
			GoUnder dig = him.goUnder();
			if (!him.isRemoved()) {
				last[0] = him.position();
				if (dig != null && dig.status() == GoUnder.Status.DIGGING && Watchers.of(players, serverChunks).sees(level, him.viewBox())) {
					seenDigging[0] = true;
				}
			} else if ((dig == null || dig.status() != GoUnder.Status.COVERED) && problems.isEmpty()) {
				problems.add("removed before he was covered: " + (dig == null ? "no dig" : dig.status()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(him.isRemoved(), "still out: " + him.phase() + " " + (him.goUnder() == null ? "" : him.goUnder().status()));
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.assertTrue(seenDigging[0], "the watcher never saw him dig (the test does not test the view)");
			helper.assertFalse(him.seenWhenRemoved(), "removed while in view");
			helper.assertTrue("went under, covered and out of view".equals(him.goneWhy()), "gone because " + him.goneWhy());
			helper.assertFalse(Watchers.of(players, serverChunks).sees(level, HimEntity.viewBox(last[0])), "in view where he was removed");

			GoUnder dig = him.goUnder();
			helper.assertTrue(dig != null && dig.status() == GoUnder.Status.COVERED, "not covered");
			GoUnder.Plan plan = dig.plan();
			helper.assertTrue(plan.top().equals(top), "dug at " + plan.top() + ", not under him at " + top);
			helper.assertTrue(plan.depth() >= 4 && plan.depth() <= 6, "depth " + plan.depth());
			helper.assertTrue(dig.dugCount() == plan.depth() && dig.filledCount() == GoUnder.COVER, "dug " + dig.dugCount() + " filled " + dig.filledCount());
			// Only allowed ground, only his own column.
			for (GoUnder.Dug taken : dig.taken()) {
				helper.assertTrue(plan.inShaft(taken.pos()) && GoUnder.diggable(taken.state()), "dug " + taken);
			}
			// The cover: bare dirt on top (it was grass), solid under it, his space open below.
			helper.assertTrue(level.getBlockState(top).is(Blocks.DIRT), "the top came back as " + level.getBlockState(top));
			BlockState under = level.getBlockState(top.below());
			helper.assertTrue(!under.isAir() && !GoUnder.falls(under), "under the top: " + under);
			for (int k = GoUnder.COVER; k < plan.depth(); k++) {
				helper.assertTrue(level.getBlockState(top.below(k)).isAir(), "the shaft under the cover is not open at " + k);
			}
			helper.assertTrue(!level.getBlockState(top.below(plan.depth())).isAir(), "the floor went");
			// The ledger: every entry of this dig is in his column; the 2 put back are moves into the top 2.
			String cause = dig.dig().ledgerCause();
			int moves = 0;
			int removes = 0;
			for (TraceLedger.Entry entry : TraceLedger.get(level.getServer()).entries()) {
				if (!entry.cause().startsWith(cause)) {
					continue;
				}
				if (entry.cause().equals(cause + "/dependent")) {
					continue; // a plant on the top, if any
				}
				helper.assertTrue(entry.cause().equals(cause) && plan.inShaft(entry.pos().pos()), "an entry outside his shaft: " + entry);
				if (entry.kind() == TraceLedger.Kind.MOVE) {
					moves++;
					helper.assertTrue(entry.to().isPresent() && plan.inCover(entry.to().get()), "put back outside the top of the shaft: " + entry);
				} else {
					removes++;
				}
			}
			helper.assertTrue(moves == GoUnder.COVER && removes == plan.depth() - GoUnder.COVER, "moves " + moves + " removes " + removes);
			// Another dig's session cannot use his blocks.
			TraceService traces = Services.traces();
			TraceService.FigureDig other = traces.startFigureDig(level, top, GoUnder.CAUSE);
			helper.assertTrue(traces.figureDug(level, other).isEmpty(), "another session sees his dug blocks");
			List<TraceLedger.Entry> stillDug = traces.figureDug(level, dig.dig());
			helper.assertTrue(stillDug.size() == plan.depth() - GoUnder.COVER, "still dug " + stillDug.size());
			helper.assertFalse(traces.figureFill(level, other, stillDug.getFirst(), top.below(GoUnder.COVER)), "another session filled with his block");
		});
	}

	@GameTest(skyAccess = true, maxTicks = 300, padding = 8)
	public void goesUnderWaitsCoveredWhileSomeoneIsNear(GameTestHelper helper) {
		// Obsidian from y 2 down: the shaft can only be 4 deep, so he stays within 3 blocks of someone standing beside it.
		ground(helper, Blocks.OBSIDIAN, Blocks.GRASS_BLOCK);
		ServerLevel level = helper.getLevel();
		ServerPlayer beside = mockPlayer(helper, helper.absoluteVec(new Vec3(5.5, 7.0, 3.5)), 90.0F, 30.0F);
		List<ServerPlayer> players = new ArrayList<>(List.of(beside));
		HimEntity him = watchedFigure(helper, Variant.CLOSE, SHAFT_FEET, 0.0F, players);
		int serverChunks = level.getServer().getPlayerList().getViewDistance();
		long[] coveredAt = {-1};
		boolean[] movedAway = {false};
		List<String> problems = new ArrayList<>();
		helper.runAfterDelay(3, () -> helper.assertTrue(him.forceGoUnder().isEmpty(), "he would not go under"));
		helper.onEachTick(() -> {
			GoUnder dig = him.goUnder();
			if (dig != null && dig.status() == GoUnder.Status.COVERED && coveredAt[0] < 0) {
				coveredAt[0] = helper.getTick();
			}
			if (him.isRemoved() && !movedAway[0] && problems.isEmpty()) {
				problems.add("removed while someone stood beside the shaft");
			}
			// 40 ticks after he is covered, the player walks off, looking away.
			if (coveredAt[0] >= 0 && !movedAway[0] && helper.getTick() >= coveredAt[0] + 40) {
				helper.assertFalse(Watchers.of(players, serverChunks).mayRemove(level, him.viewBox(), him.position()), "the test lets him go too early");
				place(beside, helper.absoluteVec(new Vec3(3.5, 7.0, -12.0)), 180.0F, 0.0F);
				movedAway[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.assertTrue(movedAway[0] && him.isRemoved(), "not gone after the player left");
			helper.assertTrue(him.goUnder().plan().depth() == 4 && him.goUnder().status() == GoUnder.Status.COVERED,
					"depth " + him.goUnder().plan().depth() + " " + him.goUnder().status());
			helper.assertFalse(him.seenWhenRemoved(), "removed while in view");
			helper.assertTrue("went under, covered and out of view".equals(him.goneWhy()), "gone because " + him.goneWhy());
		});
	}

	@GameTest
	public void goesUnderRefusesGroundHeMayNotDig(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = 0; y <= 6; y++) {
					helper.setBlock(x, y, z, y <= 2 ? Blocks.STONE : Blocks.DIRT);
				}
			}
		}
		helper.setBlock(1, 6, 1, Blocks.OAK_PLANKS); // not ground
		BlockPos placed = new BlockPos(3, 5, 1); // dirt a player put there
		Services.watch().onPlaced((ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL), level, helper.absolutePos(placed),
				Blocks.DIRT.defaultBlockState());
		helper.setBlock(6, 4, 1, Blocks.WATER); // water beside the shaft at (5, *, 1)
		helper.setBlock(1, 4, 5, Blocks.CHEST); // a block entity in the shaft
		for (int y = 3; y <= 6; y++) {
			helper.setBlock(3, y, 5, Blocks.NETHERRACK); // never netherrack
		}
		for (int y = 1; y <= 6; y++) {
			helper.setBlock(5, y, 6, Blocks.SAND); // all sand: nothing that stays put under the top
		}
		List<BlockState> before = new ArrayList<>();
		List<BlockPos> tops = List.of(new BlockPos(1, 6, 1), new BlockPos(3, 6, 1), new BlockPos(5, 6, 1), new BlockPos(1, 6, 5), new BlockPos(3, 6, 5),
				new BlockPos(5, 6, 6));
		for (BlockPos t : tops) {
			for (int k = 0; k <= 6; k++) {
				before.add(level.getBlockState(helper.absolutePos(t.below(k))));
			}
		}
		for (BlockPos t : tops) {
			GoUnder.Check check = GoUnder.check(level, helper.absolutePos(t), 4, 6);
			helper.assertFalse(check.ok(), "he may dig at " + t + " (relative)");
		}
		// The control: plain dirt over stone.
		GoUnder.Check fine = GoUnder.check(level, helper.absolutePos(new BlockPos(6, 6, 4)), 4, 6);
		helper.assertTrue(fine.ok() && fine.maxDepth() == 6, "plain ground refused: " + fine);
		// Checking changes nothing.
		int i = 0;
		for (BlockPos t : tops) {
			for (int k = 0; k <= 6; k++) {
				helper.assertTrue(level.getBlockState(helper.absolutePos(t.below(k))) == before.get(i++), "a check changed the world at " + t.below(k));
			}
		}
		// What he may dig at all.
		for (Block yes : List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.STONE, Blocks.GRANITE,
				Blocks.SANDSTONE)) {
			helper.assertTrue(GoUnder.diggable(yes.defaultBlockState()), yes + " is not diggable");
		}
		for (Block no : List.of(Blocks.NETHERRACK, Blocks.END_STONE, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.OBSIDIAN, Blocks.BEDROCK, Blocks.CHEST,
				Blocks.WATER, Blocks.GOLD_BLOCK, Blocks.SOUL_SAND)) {
			helper.assertFalse(GoUnder.diggable(no.defaultBlockState()), no + " is diggable");
		}
		// Never in the End or the Nether, and no dimension card ends this way.
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		ServerLevel end = level.getServer().getLevel(Level.END);
		helper.assertTrue(GoUnder.dimensionAllows(level), "not in the overworld");
		helper.assertTrue((nether == null || !GoUnder.dimensionAllows(nether)) && (end == null || !GoUnder.dimensionAllows(end)), "allowed outside the overworld");
		helper.assertFalse(Variant.AMONG_ENDERMEN.mayGoUnder() || Variant.AMONG_PIGLINS.mayGoUnder() || Variant.LAST_ONE.mayGoUnder(), "a card that must not go under");
		helper.assertTrue(Variant.RIDGE.mayGoUnder() && Variant.COW.mayGoUnder(), "the stare-back cards may go under");
		helper.succeed();
	}

	@GameTest
	public void theCoverComesOnlyFromHisOwnShaft(GameTestHelper helper) {
		BlockPos top = new BlockPos(100, 64, -40);
		GoUnder.Plan plan = new GoUnder.Plan(top, 5);
		String cause = GoUnder.CAUSE + "/abc";
		List<TraceLedger.Entry> dug = new ArrayList<>();
		dug.add(entry(cause, top, Blocks.GRASS_BLOCK));
		dug.add(entry(cause, top.below(1), Blocks.DIRT));
		dug.add(entry(cause, top.below(2), Blocks.DIRT));
		dug.add(entry(cause, top.below(3), Blocks.GRAVEL));
		dug.add(entry(cause, top.below(4), Blocks.STONE));
		// Not his shaft: above it, below it, the next column. (The ledger's own cause filter is core's figureDug.)
		dug.add(entry(cause, top.above(), Blocks.DIRT));
		dug.add(entry(cause, top.below(5), Blocks.DIRT));
		dug.add(entry(cause, top.east().below(1), Blocks.DIRT));
		List<GoUnder.Fill<TraceLedger.Entry>> fills = GoUnder.chooseEntries(plan, dug);
		helper.assertTrue(fills.size() == 2, "fills " + fills.size());
		helper.assertTrue(fills.get(0).target().equals(top.below(1)) && fills.get(1).target().equals(top), "targets " + fills);
		for (GoUnder.Fill<TraceLedger.Entry> fill : fills) {
			helper.assertTrue(plan.inShaft(fill.item().pos().pos()) && plan.inCover(fill.target()), "outside his shaft: " + fill);
		}
		// Bare dirt on top (the clue), from just under the grass; the next dirt under it, never the gravel.
		helper.assertTrue(fills.get(1).item().pos().pos().equals(top.below(1)) && fills.get(1).item().state().orElseThrow().is(Blocks.DIRT), "top " + fills.get(1));
		helper.assertTrue(fills.get(0).item().pos().pos().equals(top.below(2)), "under the top " + fills.get(0));
		// Stone ground comes back as itself; sand on top keeps sand on top, with something under it that stays put.
		List<TraceLedger.Entry> rock = List.of(entry(cause, top, Blocks.STONE), entry(cause, top.below(1), Blocks.STONE), entry(cause, top.below(2), Blocks.ANDESITE),
				entry(cause, top.below(3), Blocks.STONE));
		List<GoUnder.Fill<TraceLedger.Entry>> rockFills = GoUnder.chooseEntries(new GoUnder.Plan(top, 4), rock);
		helper.assertTrue(rockFills.get(1).item().pos().pos().equals(top) && rockFills.get(0).item().pos().pos().equals(top.below(1)), "rock " + rockFills);
		List<TraceLedger.Entry> beach = List.of(entry(cause, top, Blocks.SAND), entry(cause, top.below(1), Blocks.SAND), entry(cause, top.below(2), Blocks.SAND),
				entry(cause, top.below(3), Blocks.SANDSTONE));
		List<GoUnder.Fill<TraceLedger.Entry>> beachFills = GoUnder.chooseEntries(new GoUnder.Plan(top, 4), beach);
		helper.assertTrue(beachFills.get(1).item().state().orElseThrow().is(Blocks.SAND) && beachFills.get(0).item().state().orElseThrow().is(Blocks.SANDSTONE),
				"beach " + beachFills);
		// Nothing that stays put: no cover, so no going under there.
		List<TraceLedger.Entry> dunes = List.of(entry(cause, top, Blocks.SAND), entry(cause, top.below(1), Blocks.SAND), entry(cause, top.below(2), Blocks.GRAVEL),
				entry(cause, top.below(3), Blocks.SAND));
		helper.assertTrue(GoUnder.chooseEntries(new GoUnder.Plan(top, 4), dunes).isEmpty(), "covered with sand over an open shaft");
		// The plan's own geometry.
		helper.assertTrue(plan.inShaft(top) && plan.inShaft(top.below(4)) && !plan.inShaft(top.below(5)) && !plan.inShaft(top.above())
				&& !plan.inShaft(top.north()), "inShaft");
		helper.assertTrue(plan.inCover(top) && plan.inCover(top.below(1)) && !plan.inCover(top.below(2)), "inCover");
		helper.assertTrue(plan.bottom().equals(top.below(4)) && plan.cover().equals(List.of(top.below(1), top)), "bottom and cover");
		// The config: never shallower than 4, never more than 8, min under max; the chance between 0 and 1.
		EntityConfig config = new EntityConfig();
		helper.assertTrue(config.goUnderChance() == 0.25 && config.goUnderDepths()[0] == 4 && config.goUnderDepths()[1] == 6
				&& config.goUnderDigSeconds == 0.4, "D-030 defaults");
		config.goUnderMinDepth = 1;
		config.goUnderMaxDepth = 40;
		config.goUnderChance = 3;
		helper.assertTrue(config.goUnderDepths()[0] == GoUnder.MIN_DEPTH && config.goUnderDepths()[1] == GoUnder.MAX_DEPTH && config.goUnderChance() == 1.0,
				"bad values are not tamed");
		helper.succeed();
	}

	private static TraceLedger.Entry entry(String cause, BlockPos pos, Block block) {
		return new TraceLedger.Entry(TraceLedger.Kind.REMOVE, cause, 0, GlobalPos.of(Level.OVERWORLD, pos), Optional.empty(),
				Optional.of(block.defaultBlockState()), Optional.empty(), Optional.empty(), -1, -1);
	}
}
