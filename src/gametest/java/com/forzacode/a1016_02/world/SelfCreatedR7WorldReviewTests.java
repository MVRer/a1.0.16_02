package com.forzacode.a1016_02.world;

import java.util.List;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceVeto;
import com.forzacode.a1016_02.world.sc.Prints;
import com.forzacode.a1016_02.world.sc.Tracks;
import com.forzacode.a1016_02.world.sc.WorldSc;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Bug hunt R7 on the self-created layer (docs/SELF_CREATED.md), the switch's give-back (L2). The half plant is
 * {@code review/sc-r7}'s {@code core.SelfCreatedR7ReviewTests} test as it stood (it failed on {@code self-created} at
 * 5348ede); the second test holds the fix's retry of what was in view.
 */
public class SelfCreatedR7WorldReviewTests {
	private static final String BIG = "a1016_02:world/signature";

	/**
	 * The switch's give-back of a print (09; 10's cleared circle is the same): a print on grass under tall grass removes
	 * the plant's lower half (its upper half goes with it as a dependent) and bares the grass. Giving it back must put
	 * both halves back. The give-back runs once, newest first: the upper half comes first and cannot stand on air
	 * (BLOCKED), then the lower half comes back alone; the next pass is the next server start, and any neighbour update
	 * before it pops the lone half, after which neither ever comes back.
	 */
	@GameTest(structure = BIG, skyAccess = true, maxTicks = 40)
	public void theSwitchGivesBothHalvesOfATallPlantBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 8, 1, 8)) {
			helper.setBlock(rel, Blocks.GRASS_BLOCK);
		}
		BlockState lower = Blocks.TALL_GRASS.defaultBlockState();
		helper.setBlock(new BlockPos(4, 2, 4), lower);
		helper.setBlock(new BlockPos(4, 3, 4), lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		String cause = Tracks.CAUSE + "/r7-" + helper.absolutePos(BlockPos.ZERO).toShortString();
		TraceBatch batch = Services.traces().batch(level, cause);
		Prints.queue(batch, Prints.at(level, helper.absolutePos(new BlockPos(4, 1, 4))));
		helper.assertTrue(batch.commit(), "the print was refused");
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(4, 1, 4));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 3, 4));
		WorldSc.giveBack(level.getServer(), cause);
		boolean lowerBack = helper.getBlockState(new BlockPos(4, 2, 4)).is(Blocks.TALL_GRASS);
		boolean upperBack = helper.getBlockState(new BlockPos(4, 3, 4)).is(Blocks.TALL_GRASS);
		List<TraceLedger.Entry> left = TraceLedger.get(level.getServer()).entries().stream()
				.filter(e -> e.cause().equals(cause) || e.cause().startsWith(cause + "/")).toList();
		left.forEach(TraceLedger.get(level.getServer())::remove);
		helper.assertTrue(lowerBack && upperBack, "the switch gave back half a plant: lower " + lowerBack + ", upper " + upperBack + "; left in the ledger: " + left);
		helper.succeed();
	}

	/**
	 * What could not go back because it was in view (a veto stands in for the view) is counted as waiting, so the switch
	 * tries it again while the layer stays off (not only at the next server start); out of view, it goes back.
	 */
	@GameTest(structure = BIG, skyAccess = true, maxTicks = 40)
	public void theSwitchTriesAgainWhatWasInView(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 8, 1, 8)) {
			helper.setBlock(rel, Blocks.GRASS_BLOCK);
		}
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.POPPY);
		String cause = Tracks.CAUSE + "/r7-view-" + helper.absolutePos(BlockPos.ZERO).toShortString();
		TraceBatch batch = Services.traces().batch(level, cause);
		Prints.queue(batch, Prints.at(level, helper.absolutePos(new BlockPos(5, 1, 5))));
		helper.assertTrue(batch.commit(), "the print was refused");
		BlockPos ground = helper.absolutePos(new BlockPos(5, 1, 5));
		TraceVeto inView = (vetoLevel, pos) -> pos.equals(ground) || pos.equals(ground.above());
		WorldSc.Back seen;
		Services.traces().addVeto(inView);
		try {
			seen = WorldSc.giveBackAll(level.getServer(), cause);
		} finally {
			Services.traces().removeVeto(inView);
		}
		WorldSc.Back later = WorldSc.giveBackAll(level.getServer(), cause);
		boolean back = helper.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.GRASS_BLOCK) && helper.getBlockState(new BlockPos(5, 2, 5)).is(Blocks.POPPY);
		List<TraceLedger.Entry> left = TraceLedger.get(level.getServer()).entries().stream()
				.filter(e -> e.cause().equals(cause) || e.cause().startsWith(cause + "/")).toList();
		left.forEach(TraceLedger.get(level.getServer())::remove);
		helper.assertTrue(seen.done() == 0 && seen.waiting() == 2, "in view: " + seen);
		helper.assertTrue(later.done() == 2 && later.waiting() == 0 && back, "out of view: " + later + ", back " + back);
		helper.succeed();
	}
}
