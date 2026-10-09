package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** TraceService game tests; {@link CoreGameTests} extends this so they run under core's registered entrypoint. */
public class TraceGameTests {
	private static final double NEAR = 3;
	private static final double CONE = 160;

	/** A player-shaped viewpoint at relative (1.5, 1, 0.5), looking south (+z). */
	private static List<TraceService.Viewer> viewer(GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 0.5)), 0.0F, 0.0F);
		return List.of(TraceService.Viewer.of(player, 8));
	}

	private static void wall(GameTestHelper helper, Block block) {
		for (int x = 0; x <= 2; x++) {
			for (int y = 0; y <= 7; y++) {
				helper.setBlock(x, y, 3, block);
			}
		}
	}

	@GameTest
	public void seeThroughBlocksDoNotHide(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<TraceService.Viewer> viewers = viewer(helper);
		AABB target = new AABB(helper.absolutePos(new BlockPos(1, 2, 6)));
		helper.setBlock(1, 2, 6, Blocks.DIRT);
		for (Block seeThrough : List.of(Blocks.GLASS, Blocks.OAK_LEAVES, Blocks.ICE, Blocks.SLIME_BLOCK)) {
			wall(helper, seeThrough);
			helper.assertFalse(TraceService.isOutOfView(level, target, viewers, NEAR, CONE), "a block behind " + seeThrough + " counts as hidden");
		}
		wall(helper, Blocks.STONE);
		helper.assertTrue(TraceService.isOutOfView(level, target, viewers, NEAR, CONE), "a block behind stone counts as seen");
		helper.succeed();
	}

	@GameTest
	public void batchChecksEachExposedBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<TraceService.Viewer> viewers = viewer(helper);
		List<BlockPos> behind = new ArrayList<>();
		for (int x = 0; x < 8; x++) {
			behind.add(helper.absolutePos(new BlockPos(x, 1, 0)).north(8));
		}
		helper.assertTrue(TraceService.positionsOutOfView(level, behind, viewers, NEAR, CONE), "blocks behind the viewer count as seen");

		// One visible block among many hidden ones, far from the batch's bounding-box grid points.
		List<BlockPos> withOneVisible = new ArrayList<>(behind);
		withOneVisible.add(helper.absolutePos(new BlockPos(6, 2, 6)));
		helper.assertFalse(TraceService.positionsOutOfView(level, withOneVisible, viewers, NEAR, CONE), "the one visible block was missed");

		// A block buried in stone on all six sides cannot be seen even straight ahead.
		BlockPos buried = helper.absolutePos(new BlockPos(5, 4, 5));
		helper.setBlock(5, 4, 5, Blocks.STONE);
		for (Direction dir : Direction.values()) {
			helper.setBlock(new BlockPos(5, 4, 5).relative(dir), Blocks.STONE);
		}
		helper.assertTrue(TraceService.positionsOutOfView(level, List.of(buried), viewers, NEAR, CONE), "a buried block counts as seen");
		helper.assertFalse(TraceService.positionsOutOfView(level, List.of(buried.north()), viewers, NEAR, CONE), "its open face counts as hidden");
		helper.succeed();
	}

	@GameTest
	public void removingSupportRemovesDependentsSilently(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(2, 1, 2, Blocks.STONE);
		helper.setBlock(2, 2, 2, Blocks.TORCH);
		helper.setBlock(5, 1, 5, Blocks.STONE);
		helper.setBlock(5, 2, 5, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(5, 3, 5, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(6, 1, 2, Blocks.STONE);
		helper.setBlock(6, 2, 2, Blocks.RAIL);
		helper.setBlock(1, 1, 6, Blocks.STONE);
		helper.setBlock(1, 2, 6, Blocks.OAK_SIGN);
		int before = TraceLedger.get(level.getServer()).entries().size();

		boolean done = Services.traces().batch(level, "test:support")
				.remove(helper.absolutePos(new BlockPos(2, 1, 2)))
				.remove(helper.absolutePos(new BlockPos(5, 1, 5)))
				.remove(helper.absolutePos(new BlockPos(6, 1, 2)))
				.remove(helper.absolutePos(new BlockPos(1, 1, 6)))
				.commit();
		helper.assertTrue(done, "batch refused");
		for (BlockPos rel : List.of(new BlockPos(2, 2, 2), new BlockPos(5, 2, 5), new BlockPos(5, 3, 5), new BlockPos(6, 2, 2), new BlockPos(1, 2, 6))) {
			helper.assertBlockPresent(Blocks.AIR, rel);
		}
		long dependents = TraceLedger.get(level.getServer()).entries().stream().skip(before)
				.filter(e -> e.cause().equals("test:support/dependent")).count();
		helper.assertTrue(dependents == 5, "expected 5 dependent ledger entries, got " + dependents);

		// A falling block on top refuses the edit rather than letting it drop.
		helper.setBlock(3, 1, 6, Blocks.STONE);
		helper.setBlock(3, 2, 6, Blocks.SAND);
		helper.assertFalse(Services.traces().remove(level, helper.absolutePos(new BlockPos(3, 1, 6)), "test:sand"), "removed under sand");
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 6));

		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.assertBlockPresent(Blocks.SAND, new BlockPos(3, 2, 6));
			helper.succeed();
		});
	}

	@GameTest
	public void leaveNeedsAReplaceableSpot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chest = helper.absolutePos(new BlockPos(6, 1, 6));
		helper.setBlock(6, 1, 6, Blocks.CHEST);
		((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 3));
		helper.setBlock(4, 1, 4, Blocks.STONE);
		helper.setBlock(2, 1, 4, Blocks.SHORT_GRASS);

		helper.assertFalse(Services.traces().leave(level, chest, Blocks.STONE.defaultBlockState(), "test:leave"), "overwrote a chest");
		helper.assertTrue(((Container) level.getBlockEntity(chest)).getItem(0).is(Items.DIAMOND), "chest lost its items");
		helper.assertFalse(Services.traces().leave(level, helper.absolutePos(new BlockPos(4, 1, 4)), Blocks.GLOWSTONE.defaultBlockState(), "test:leave"), "overwrote stone");
		helper.assertTrue(Services.traces().leave(level, helper.absolutePos(new BlockPos(2, 1, 4)), Blocks.GLOWSTONE.defaultBlockState(), "test:leave"), "grass is replaceable");
		helper.assertTrue(Services.traces().leave(level, helper.absolutePos(new BlockPos(3, 3, 3)), Blocks.GLOWSTONE.defaultBlockState(), "test:leave"), "air is replaceable");
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	@GameTest
	public void siteRecordedDuringSaveStaysDirty(GameTestHelper helper) {
		SiteRegistry.Data data = new SiteRegistry.Data();
		data.setDirty(); // as the storage does for new data
		data.snapshot(); // the storage encodes...
		data.sites.add(new SiteRegistry.Site(1, SiteType.CROSS, Level.OVERWORLD, BlockPos.ZERO, 2, Optional.empty()));
		data.setDirty(); // ...a worldgen thread records a site...
		data.setDirty(false); // ...then the storage marks the data clean
		helper.assertTrue(data.isDirty(), "a site recorded during a save would be lost");
		data.snapshot();
		data.setDirty(false);
		helper.assertFalse(data.isDirty(), "still dirty after a full save");
		helper.succeed();
	}
}
