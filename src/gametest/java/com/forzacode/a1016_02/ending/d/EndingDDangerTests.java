package com.forzacode.a1016_02.ending.d;

import java.util.List;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Every danger of the chain goes through core (TraceService, MobTamper) and only out of view: the leaf under a climber
 * (only while they look up, D-027), spiders in the canopy, the flood, the stair losing blocks, the fall after naming
 * him. Each test shows the danger happening unseen and not happening seen.
 */
public class EndingDDangerTests extends EndingDUndoTests {
	@GameTest(structure = EndingDSupport.YARD)
	public void leafGoesOnlyWhileTheClimberLooksUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EndingDSupport.fill(helper, 0, 0, 0, 15, 0, 15, Blocks.DIRT);
		helper.setBlock(8, 6, 8, Blocks.RED_POPLAR_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
		EndingDConfig cfg = new EndingDConfig();

		ServerPlayer level0 = EndingDSupport.player(helper, new Vec3(8.5, 7.0, 8.5), 0, 0);
		level0.setOnGround(true);
		helper.assertFalse(Grove.leafUnderClimber(level0, EndingDSupport.seenBy(level0), cfg), "the leaf went while the climber looked ahead");
		helper.assertBlockPresent(Blocks.RED_POPLAR_LEAVES, new BlockPos(8, 6, 8));

		ServerPlayer up = EndingDSupport.player(helper, new Vec3(8.5, 7.0, 8.5), 0, -60);
		up.setOnGround(true);
		helper.assertTrue(Grove.leafUnderClimber(up, EndingDSupport.seenBy(up), cfg), "the leaf stayed while the climber looked up");
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, 6, 8));
		List<TraceLedger.Entry> taken = TraceLedger.get(level.getServer()).entries().stream()
				.filter(e -> e.cause().equals(Grove.LEAF_CAUSE) && e.pos().pos().equals(helper.absolutePos(new BlockPos(8, 6, 8)))).toList();
		helper.assertTrue(taken.size() == 1, "the leaf was not ledgered: " + taken);
		TraceLedger.get(level.getServer()).remove(taken.getFirst());

		// Low leaves (a bush) never go.
		helper.setBlock(3, 1, 3, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
		ServerPlayer bush = EndingDSupport.player(helper, new Vec3(3.5, 2.0, 3.5), 0, -60);
		bush.setOnGround(true);
		helper.assertFalse(Grove.leafUnderClimber(bush, EndingDSupport.NOBODY, cfg), "a leaf one block up went");
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.YARD, maxTicks = 40)
	public void spidersGoIntoTheCanopyOnlyUnseen(GameTestHelper helper) {
		EndingDSupport.fill(helper, 0, 0, 0, 15, 0, 15, Blocks.DIRT);
		EndingDSupport.fill(helper, 0, 5, 0, 15, 5, 15, Blocks.OAK_LEAVES);
		Spider spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(2, 1, 2));
		ServerPlayer player = EndingDSupport.player(helper, new Vec3(8.5, 6.0, 8.5), 0, 0);
		EndingDConfig cfg = new EndingDConfig();
		Vec3 start = spider.position();
		helper.assertFalse(Grove.spiderIntoCanopy(player, EndingDSupport.EVERYONE, cfg, RandomSource.create(1)), "a spider was moved in view");
		helper.assertTrue(spider.position().equals(start), "the spider moved in view");
		helper.assertTrue(Grove.spiderIntoCanopy(player, EndingDSupport.NOBODY, cfg, RandomSource.create(1)), "no spider went into the canopy");
		helper.assertTrue(spider.getY() >= helper.absoluteVec(new Vec3(0, 6, 0)).y - 0.01, "the spider is not up in the canopy: " + spider.position());
		helper.assertTrue(Services.mobs().isTampered(spider), "the spider is not held still");
		Services.mobs().release(spider);
		spider.discard();
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void stairwellFloodsOnlyUnseen(GameTestHelper helper) {
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		StairPlan plan = EndingDSupport.buildStair(helper, data);
		// The hole the player dug from the pyramid's core down to the stair (top down, so no sand falls in).
		for (int y = 23; y >= 19; y--) {
			helper.setBlock(EndingDSupport.AX, y, EndingDSupport.AZ, Blocks.AIR);
		}
		helper.setBlock(EndingDSupport.AX + 2, 23, EndingDSupport.AZ, Blocks.WATER);
		ServerPlayer player = EndingDSupport.player(helper, new Vec3(EndingDSupport.AX + 0.5, 10, EndingDSupport.AZ - 0.5), 0, 60);
		helper.assertFalse(Stair.flood(player, plan, EndingDSupport.EVERYONE), "the sea got in while it was watched");
		helper.assertBlockPresent(Blocks.SAND, new BlockPos(EndingDSupport.AX + 1, 23, EndingDSupport.AZ));
		helper.assertTrue(Stair.flood(player, plan, EndingDSupport.NOBODY), "the sea did not get in");
		BlockState barrier = helper.getBlockState(new BlockPos(EndingDSupport.AX + 1, 23, EndingDSupport.AZ));
		helper.assertTrue(barrier.isAir() || barrier.is(Blocks.WATER), "the barrier is still there: " + barrier);
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void stairLosesBlocksOnlyUnseenAndTheyComeBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		StairPlan plan = EndingDSupport.buildStair(helper, data);
		ServerPlayer player = EndingDSupport.player(helper, new Vec3(6.5, 2, 6.5), 0, 0);
		int standing = Stair.standing(level, plan);
		helper.assertTrue(standing > 4, "too few stairs above the chamber: " + standing);
		// One of their torches on the stairwell wall (on the pillar).
		BlockPos torch = helper.absolutePos(new BlockPos(EndingDSupport.AX, 10, EndingDSupport.AZ - 1));
		BlockState torchState = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH);
		level.setBlockAndUpdate(torch, torchState);
		Services.watch().onPlaced(player, level, torch, torchState);

		helper.assertFalse(Stair.loseBlock(player, data, plan, EndingDSupport.EVERYONE), "the stair lost a block in view");
		helper.assertTrue(Stair.standing(level, plan) == standing && level.getBlockState(torch).is(Blocks.WALL_TORCH), "something went in view");

		helper.assertTrue(Stair.loseBlock(player, data, plan, EndingDSupport.NOBODY), "the torch did not go");
		helper.assertTrue(level.getBlockState(torch).isAir() && data.has(EndingDState.STAIR_TORCH), "the stairwell torch is still there");
		helper.assertTrue(Stair.loseBlock(player, data, plan, EndingDSupport.NOBODY), "no stair went");
		helper.assertTrue(Stair.standing(level, plan) == standing - 1, "expected one stair fewer");
		BlockPos lowest = plan.stairs().stream().filter(s -> s.getY() >= plan.chamberCeil()).min(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
		helper.assertTrue(level.getBlockState(lowest).isAir(), "the lowest stair above the chamber should go first");

		// The last minute puts back exactly those blocks: the stair (climbing) and the lost torch.
		List<TraceLedger.Entry> steps = LastMinute.footstepEntries(server, data).stream().filter(e -> e.pos().pos().equals(lowest)).toList();
		helper.assertTrue(steps.size() == 1 && steps.getFirst().state().orElseThrow().getBlock() instanceof StairBlock, "the stair is not ledgered: " + steps);
		helper.assertTrue(Services.traces().forced().restoreBlock(level, steps.getFirst(), lowest), "the stair did not come back");
		helper.assertTrue(level.getBlockState(lowest).getBlock() instanceof StairBlock, "the stair is not back");
		TraceLedger.Entry lost = LastMinute.lostTorch(server, data).filter(e -> e.pos().pos().equals(torch)).orElse(null);
		helper.assertTrue(lost != null, "the lost torch is not found");
		helper.assertTrue(Services.traces().forced().restoreBlock(level, lost, torch), "the torch did not come back");
		helper.assertTrue(level.getBlockState(torch).is(Blocks.WALL_TORCH), "the torch is not back on the wall");
		helper.assertTrue(LastMinute.lostTorch(server, data).filter(e -> e.pos().pos().equals(torch)).isEmpty(), "the torch could come back twice");
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.YARD)
	public void namingTakesTheNextBlockOnlyWhileTheyLookUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EndingDSupport.fill(helper, 0, 0, 0, 15, 0, 15, Blocks.BEDROCK);
		helper.setBlock(8, 3, 8, Blocks.STONE);
		EndingDState data = new EndingDState();
		ServerPlayer ahead = EndingDSupport.player(helper, new Vec3(8.5, 4.0, 8.5), 0, 0);
		ahead.setOnGround(true);
		helper.assertFalse(Chamber.namedFall(ahead, data, EndingDSupport.NOBODY), "a block went without naming him");
		data.set(EndingDState.NAMED, true);
		helper.assertFalse(Chamber.namedFall(ahead, data, EndingDSupport.seenBy(ahead)), "the block went while they looked ahead");
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, 3, 8));
		ServerPlayer onBedrock = EndingDSupport.player(helper, new Vec3(3.5, 1.0, 3.5), 0, -60);
		onBedrock.setOnGround(true);
		helper.assertFalse(Chamber.namedFall(onBedrock, data, EndingDSupport.NOBODY), "bedrock went");
		ServerPlayer up = EndingDSupport.player(helper, new Vec3(8.5, 4.0, 8.5), 0, -60);
		up.setOnGround(true);
		helper.assertTrue(Chamber.namedFall(up, data, EndingDSupport.seenBy(up)), "the block stayed while they looked up");
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, 3, 8));
		helper.assertFalse(data.has(EndingDState.NAMED), "naming took more than the next block");
		TraceLedger.get(level.getServer()).entries().stream().filter(e -> e.cause().equals(Chamber.NAMED_CAUSE)
				&& e.pos().pos().equals(helper.absolutePos(new BlockPos(8, 3, 8)))).toList().forEach(TraceLedger.get(level.getServer())::remove);
		helper.succeed();
	}
}
