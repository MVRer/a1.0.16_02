package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceLedger;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/** "Under you" network game tests. {@link DigGameTests} extends this so they run under dig's registered entrypoint. */
public class NetworkGameTests {
	static DigConfig testConfig() {
		DigConfig config = new DigConfig();
		config.networkShaftAfterNights = 2;
		config.networkChestAfterNights = 2;
		config.networkRadius = 22;
		return config;
	}

	static NetworkGrower.Ctx ctx(DigGround g, Network net, PosSet explored, DigConfig config, RandomSource random, long night) {
		return new NetworkGrower.Ctx(g.level, net, explored, config, ModConfig.pacing().digBelow, random, Services.traces(), night, null);
	}

	/** One night: credit it, refresh the targets, grow until the budget is spent or nothing fits. Returns new anchors. */
	static int night(DigGround g, Network net, PosSet explored, DigConfig config, RandomSource random, long night) {
		int before = net.anchors.size();
		net.creditNights(night, config);
		NetworkGrower.Ctx ctx = ctx(g, net, explored, config, random, night);
		if (!net.anchors.isEmpty()) {
			NetworkGrower.refreshTargets(ctx);
		}
		UnderYou.growNow(ctx, new DigData(), 1000, true);
		return net.anchors.size() - before;
	}

	static int minCheb(BlockPos pos, Iterable<BlockPos> others) {
		int best = Integer.MAX_VALUE;
		for (BlockPos other : others) {
			best = Math.min(best, Tunnels.cheb(pos, other));
		}
		return best;
	}

	/**
	 * The player's digs or blocks a cell must keep its distance from: all of them, except, for the shaft, the bedroom
	 * it stops under (digs at the bed's height and above; blocks from the floor under the bed up).
	 */
	static List<BlockPos> outsideBedroom(Network net, boolean shaft, List<BlockPos> spaces, boolean placed) {
		if (!shaft || net.bedHead == null) {
			return spaces;
		}
		int lowest = placed ? net.bedHead.getY() - 1 : net.bedHead.getY();
		return spaces.stream().filter(pos -> pos.getY() < lowest).toList();
	}

	static List<BlockPos> cells(Network net, boolean shaft) {
		List<BlockPos> cells = new ArrayList<>();
		for (long packed : net.cells) {
			BlockPos cell = BlockPos.of(packed);
			if (net.isShaftCell(cell) == shaft) {
				cells.add(cell);
			}
		}
		return cells;
	}

	/** Every network cell is carved, keeps its distance from the player's digs and builds, and is sealed. */
	static void assertClear(DigGround g, Network net, PosSet explored, DigConfig config) {
		GameTestHelper helper = g.helper;
		int digBelow = ModConfig.pacing().digBelow;
		for (long packed : net.cells) {
			BlockPos cell = BlockPos.of(packed);
			helper.assertTrue(g.level.getBlockState(cell).isAir(), "network cell not carved at " + cell);
			boolean shaft = net.isShaftCell(cell);
			// digBelow solid blocks between every cell and any dig or build. Only the shaft's stop under the bed may
			// come closer, and only to the bedroom: digs at the bed's height and above, and the floor under it.
			int digGap = minCheb(cell, outsideBedroom(net, shaft, g.dug, false));
			helper.assertTrue(digGap > digBelow, "cell " + cell + " is " + digGap + " from a player dig");
			helper.assertTrue(minCheb(cell, outsideBedroom(net, shaft, g.placed, true)) > digBelow, "cell " + cell + " is too close to a player block");
			if (!shaft) {
				helper.assertFalse(explored.anyWithin(cell, config.networkExploredClearance), "cell " + cell + " is in an explored cave");
			}
			for (Direction dir : Direction.values()) {
				BlockPos n = cell.relative(dir);
				if (!net.cells.contains(n.asLong())) {
					helper.assertTrue(Tunnels.seals(g.level.getBlockState(n)), "the network opens into " + n + " (" + g.level.getBlockState(n) + ")");
				}
			}
		}
	}

	@GameTest(maxTicks = 200)
	public void networkGrowsEachNightAndKeepsClear(GameTestHelper helper) {
		DigConfig config = testConfig();
		Pacing pacing = ModConfig.pacing();
		DigGround g = DigGround.of(helper, 0, 48, 34, 48, Blocks.STONE);
		RandomSource random = RandomSource.create(1234L);

		// The house on top: a bed (head at x=24, facing east), a chest and a few walls.
		BlockPos head = g.at(24, 34, 24);
		BlockPos foot = g.at(23, 34, 24);
		BlockState bed = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		g.place(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		g.place(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
		for (BlockPos corner : List.of(g.at(19, 34, 19), g.at(29, 34, 19), g.at(19, 34, 29), g.at(29, 34, 29), g.at(27, 34, 27))) {
			g.place(corner, Blocks.OAK_PLANKS.defaultBlockState());
		}
		// A basement the player dug beside the house, and a mine: a shaft down to y=16 and a 2-high tunnel east.
		g.digBox(33, 29, 18, 36, 32, 21);
		g.digBox(8, 16, 8, 8, 33, 8);
		g.digBox(9, 16, 8, 40, 17, 8);
		// A cave the player explored.
		PosSet explored = new PosSet();
		explored.add(g.at(40, 12, 40), Integer.MAX_VALUE);

		Network net = new Network(g.level.dimension(), head);
		helper.assertTrue(NetworkGrower.refreshBed(g.level, net, head) && foot.equals(net.bedFoot), "bed not found");
		for (long n = 0; n < 12; n++) {
			int grown = night(g, net, explored, config, random, n);
			helper.assertTrue(grown > 0, "the network did not grow on night " + n);
			helper.assertTrue(net.budget >= 0, "overspent on night " + n);
			assertClear(g, net, explored, config);
		}
		int depth = net.depth - g.origin.getY();
		helper.assertTrue(depth <= 16 - (pacing.digBelow + 2), "corridors at relative y " + depth + " are not below the mine");

		// The shaft stops one block below the bed: the floor under the bed is the only thing left.
		helper.assertTrue(net.shaftDone, "shaft not finished");
		for (BlockPos half : List.of(head, foot)) {
			helper.assertTrue(net.cells.contains(half.below(2).asLong()), "the shaft does not reach " + half.below(2));
			helper.assertTrue(g.level.getBlockState(half.below()).is(Blocks.STONE), "the floor under the bed was touched");
		}
		helper.assertTrue(net.shaftSite >= 0 && net.underBaseSite >= 0 && net.alcove != null, "sites or dead end missing");
		SiteRegistry.Site under = site(net.underBaseSite);
		helper.assertTrue(under.type() == SiteType.UNDER_BASE && under.pos().equals(net.alcove), "UNDER_BASE site " + under);
		helper.assertTrue(TraceLedger.get(g.level.getServer()).entries().stream().anyMatch(e -> e.cause().equals(NetworkGrower.CAUSE)),
				"carving not in the ledger");

		// The player digs from the mine toward the nearest corridor. The network keeps growing between digs and never
		// comes closer; it can only be breached once a dig is within breachWithin blocks of it.
		List<BlockPos> mine = new ArrayList<>(g.dug.subList(g.dug.size() - 64, g.dug.size()));
		List<BlockPos> corridors = cells(net, false);
		BlockPos from = null;
		BlockPos to = null;
		int best = Integer.MAX_VALUE;
		for (BlockPos d : mine) {
			for (BlockPos c : corridors) {
				int dist = Tunnels.cheb(d, c);
				if (dist < best) {
					best = dist;
					from = d;
					to = c;
				}
			}
		}
		helper.assertTrue(best > pacing.digBelow, "the network is " + best + " from the mine before any digging");
		BlockPos cur = from;
		int prevGap = best;
		boolean breached = false;
		for (int step = 0; step < 24 && !breached; step++) {
			BlockPos next = stepToward(cur, to);
			g.dig(next);
			List<BlockPos> all = new ArrayList<>(cells(net, false));
			all.addAll(cells(net, true));
			int gap = minCheb(next, all);
			breached = touches(net, next);
			if (breached) {
				helper.assertTrue(prevGap <= pacing.breachWithin, "breached from " + prevGap + " blocks away");
			}
			LongOpenHashSet before = new LongOpenHashSet(net.cells);
			night(g, net, explored, config, random, 12 + step);
			for (long packed : net.cells) {
				BlockPos cell = BlockPos.of(packed);
				// Cells carved after the player came closer still keep their distance from every dig.
				if (!before.contains(packed)) {
					int dist = minCheb(cell, outsideBedroom(net, net.isShaftCell(cell), g.dug, false));
					helper.assertTrue(dist > pacing.digBelow, "the network grew to " + dist + " from a dig at " + cell);
				}
			}
			prevGap = gap;
			cur = next;
		}
		helper.assertTrue(breached, "digging toward the network never breached it");
		helper.succeed();
	}

	private static boolean touches(Network net, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (net.cells.contains(pos.relative(dir).asLong())) {
				return true;
			}
		}
		return false;
	}

	private static BlockPos stepToward(BlockPos from, BlockPos to) {
		if (from.getY() != to.getY()) {
			return from.offset(0, Integer.signum(to.getY() - from.getY()), 0);
		}
		if (from.getX() != to.getX()) {
			return from.offset(Integer.signum(to.getX() - from.getX()), 0, 0);
		}
		return from.offset(0, 0, Integer.signum(to.getZ() - from.getZ()));
	}

	static SiteRegistry.Site site(int id) {
		return Services.sites().all().stream().filter(s -> s.id() == id).findFirst().orElseThrow();
	}

	@GameTest(maxTicks = 100)
	public void shaftKeepsClearOfStairsBesideTheBed(GameTestHelper helper) {
		// Stairs the player dug down right beside the bed: the shaft keeps digBelow from them and stops short.
		DigConfig config = testConfig();
		Pacing pacing = ModConfig.pacing();
		DigGround g = DigGround.of(helper, 13, 48, 24, 48, Blocks.STONE);
		BlockPos head = g.at(24, 24, 24);
		BlockPos foot = g.at(23, 24, 24);
		BlockState bed = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		g.place(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		g.place(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
		for (int y = 23; y >= 18; y--) {
			g.dig(g.at(27, y, 24));
		}
		PosSet explored = new PosSet();
		Network net = new Network(g.level.dimension(), head);
		NetworkGrower.refreshBed(g.level, net, head);
		RandomSource random = RandomSource.create(77L);
		for (long n = 0; n < 10; n++) {
			night(g, net, explored, config, random, n);
		}
		helper.assertTrue(net.shaftFoot != null && !net.shaftDone, "the shaft " + (net.shaftDone ? "passed the stairs" : "never started"));
		helper.assertTrue(net.shaftTop().orElseThrow().getY() < g.at(0, 18 - pacing.digBelow, 0).getY(), "the shaft came within "
				+ pacing.digBelow + " of the stairs");
		assertClear(g, net, explored, config);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void networkKeepsAwayFromANewMineAtItsLevel(GameTestHelper helper) {
		// After the network starts, the player digs a mine right at its level: it grows around it, never within digBelow.
		DigConfig config = testConfig();
		DigGround g = DigGround.of(helper, 1, 48, 24, 48, Blocks.STONE);
		BlockPos base = g.at(24, 24, 24);
		g.place(base, Blocks.CRAFTING_TABLE.defaultBlockState());
		PosSet explored = new PosSet();
		Network net = new Network(g.level.dimension(), base);
		RandomSource random = RandomSource.create(99L);
		helper.assertTrue(night(g, net, explored, config, random, 0) > 0 && !net.anchors.isEmpty(), "no network founded");
		BlockPos[] bounds = net.bounds().orElseThrow();
		BlockPos min = bounds[0].subtract(g.origin);
		BlockPos max = bounds[1].subtract(g.origin);
		int z = max.getZ() + 5 < 46 ? max.getZ() + 5 : min.getZ() - 5;
		int x = max.getX() + 5 < 46 ? max.getX() + 5 : min.getX() - 5;
		int y = net.depth - g.origin.getY();
		g.digBox(1, y, z, 46, y + 1, z);
		g.digBox(x, y, 1, x, y + 1, z - 1);
		g.digBox(x, y, z + 1, x, y + 1, 46);
		assertClear(g, net, explored, config);
		for (long n = 1; n < 10; n++) {
			helper.assertTrue(night(g, net, explored, config, random, n) > 0, "no growth on night " + n);
		}
		assertClear(g, net, explored, config);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void networkChestComesFromAFarSite(GameTestHelper helper) {
		DigConfig config = testConfig();
		DigGround g = DigGround.of(helper, 2, 16, 10, 16, Blocks.STONE);
		Network net = new Network(g.level.dimension(), g.at(8, 10, 8));
		g.hollow(3, 3, 3, 4, 4, 4);
		net.addAnchor(g.at(3, 3, 3), false);
		net.alcove = g.at(3, 3, 3);

		// An abandoned build far away with a chest nobody placed.
		BlockPos far = g.at(8, 2, 8).east(300);
		g.level.setBlock(far.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		g.level.setBlock(far, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
		((Container) g.level.getBlockEntity(far)).setItem(4, new ItemStack(Items.BONE, 5));
		Services.sites().record(SiteType.ABANDONED_BUILD, g.level.dimension(), far, 4);

		helper.assertTrue(NetworkChest.tryFetch(ctx(g, net, new PosSet(), config, RandomSource.create(1L), 0)), "no chest moved in");
		helper.assertTrue(net.alcove.equals(net.chest) && g.level.getBlockState(net.alcove).is(Blocks.CHEST), "chest not in the dead end");
		helper.assertTrue(g.level.getBlockState(far).isAir(), "the chest is still at the site");
		helper.assertTrue(((Container) g.level.getBlockEntity(net.chest)).getItem(4).is(Items.BONE), "its contents did not move with it");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void networkChestNeverLoadsAChunkInTheTick(GameTestHelper helper) {
		DigConfig config = testConfig();
		DigGround g = DigGround.of(helper, 14, 16, 10, 16, Blocks.STONE);
		Network net = new Network(g.level.dimension(), g.at(8, 10, 8));
		g.hollow(3, 3, 3, 4, 4, 4);
		net.addAnchor(g.at(3, 3, 3), false);
		net.alcove = g.at(3, 3, 3);
		// A hut 700 blocks away in a chunk nobody has loaded.
		BlockPos spot = g.at(8, 2, 8).east(700);
		ChunkPos chunk = ChunkPos.containing(spot);
		BlockPos far = new BlockPos(chunk.getMiddleBlockX(), spot.getY(), chunk.getMiddleBlockZ());
		helper.assertTrue(g.level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null, "the far chunk is already loaded");
		Services.sites().record(SiteType.RUINED_HUT, g.level.dimension(), far, 4);

		helper.assertFalse(NetworkChest.tryFetch(ctx(g, net, new PosSet(), config, RandomSource.create(1L), 0)), "a chest from an unloaded chunk");
		helper.assertTrue(g.level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null, "the far chunk was loaded in the same tick");
		helper.assertTrue(net.chestRetryTick != Long.MAX_VALUE, "no later try planned");
		helper.succeedWhen(() -> helper.assertTrue(g.level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null, "the far chunk never loaded"));
	}

	@GameTest
	public void underYouStackGoesToTheLedgerThenToTheNetworkChest(GameTestHelper helper) {
		// Big enough that every chunk within chestRadius of the base is loaded.
		DigGround g = DigGround.of(helper, 3, 40, 6, 40, Blocks.STONE);
		BlockPos base = g.at(20, 6, 20);
		BlockPos chest = g.at(22, 6, 20);
		g.place(chest, Blocks.CHEST.defaultBlockState());
		Container container = (Container) g.level.getBlockEntity(chest);
		container.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		container.setItem(1, new ItemStack(Items.IRON_PICKAXE));
		Network net = new Network(g.level.dimension(), base);
		RandomSource random = RandomSource.create(5L);

		helper.assertTrue(UnderYouStackCard.takeStack(g.level, base, net, random, Services.traces()), "no stack taken");
		helper.assertTrue(container.getItem(0).isEmpty() && container.getItem(1).is(Items.IRON_PICKAXE), "wrong stack taken");
		TraceLedger.Entry last = TraceLedger.get(g.level.getServer()).entries().getLast();
		helper.assertTrue(last.kind() == TraceLedger.Kind.REMOVE_STACK && last.cause().equals(UnderYouStackCard.CAUSE)
				&& last.stack().map(s -> s.is(Items.COBBLESTONE) && s.getCount() == 16).orElse(false), "the stack is not in the ledger");
		helper.assertTrue(net.stacksLedgered == 1, "not counted");

		// Once the network has its chest, stacks go there.
		g.hollow(3, 1, 3, 3, 1, 3);
		g.level.setBlock(g.at(3, 1, 3), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
		net.chest = g.at(3, 1, 3);
		container.setItem(2, new ItemStack(Items.BONE, 8));
		helper.assertTrue(UnderYouStackCard.takeStack(g.level, base, net, random, Services.traces()), "no stack moved");
		Container below = (Container) g.level.getBlockEntity(net.chest);
		helper.assertTrue(container.getItem(2).isEmpty() && below.getItem(0).is(Items.BONE) && below.getItem(0).getCount() == 8,
				"the stack did not move into the network chest");
		helper.assertTrue(net.stacksMoved == 1 && container.getItem(1).is(Items.IRON_PICKAXE), "gear touched or not counted");
		helper.succeed();
	}
}
