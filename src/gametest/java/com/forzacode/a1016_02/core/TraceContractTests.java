package com.forzacode.a1016_02.core;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Tests for the TraceService contract batch; {@link TraceGameTests} extends this. */
public class TraceContractTests extends CoreContractTests {
	/** A player-shaped viewpoint at a relative position; pitch below 0 looks up. */
	private static TraceService.Viewer viewerAt(GameTestHelper helper, Vec3 rel, float yaw, float pitch) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(rel), yaw, pitch);
		return TraceService.Viewer.of(player, 8);
	}

	private static void floor(GameTestHelper helper, Block block) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, block);
			}
		}
	}

	private static List<TraceLedger.Entry> entries(ServerLevel level, String cause) {
		return TraceLedger.get(level.getServer()).entries().stream().filter(e -> e.cause().equals(cause)).toList();
	}

	@GameTest(maxTicks = 100)
	public void removeLettingFallDropsTheGravelColumn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		helper.setBlock(2, 4, 2, Blocks.STONE);
		helper.setBlock(2, 5, 2, Blocks.GRAVEL);
		helper.setBlock(2, 6, 2, Blocks.GRAVEL);
		helper.setBlock(2, 7, 2, Blocks.STONE);
		BlockPos support = helper.absolutePos(new BlockPos(2, 4, 2));
		String cause = "test:gravel_ceiling/" + support.toShortString();

		helper.assertTrue(Services.traces().removeLettingFall(level, support, cause), "the out-of-view fall was refused");
		helper.assertTrue(level.getBlockState(support).isAir(), "the support is still there");
		List<TraceLedger.Entry> logged = entries(level, cause);
		helper.assertTrue(logged.size() == 1 && logged.getFirst().kind() == TraceLedger.Kind.REMOVE && logged.getFirst().pos().pos().equals(support)
				&& logged.getFirst().state().orElseThrow().is(Blocks.STONE), "the removed block is not ledgered alone: " + logged);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.GRAVEL, new BlockPos(2, 1, 2));
			helper.assertBlockPresent(Blocks.GRAVEL, new BlockPos(2, 2, 2));
			for (int y = 3; y <= 6; y++) {
				helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, y, 2));
			}
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 7, 2));
			helper.assertEntityNotPresent(EntityTypes.FALLING_BLOCK);
			helper.assertEntityNotPresent(EntityTypes.ITEM);
		});
	}

	@GameTest(maxTicks = 100)
	public void removeLettingFallDropsTheStalactite(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		helper.setBlock(2, 6, 2, Blocks.DRIPSTONE_BLOCK);
		helper.setBlock(2, 5, 2, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(SpeleothemBlock.TIP_DIRECTION, Direction.DOWN)
				.setValue(SpeleothemBlock.THICKNESS, SpeleothemThickness.TIP));
		BlockPos support = helper.absolutePos(new BlockPos(2, 6, 2));
		String cause = "test:dripstone/" + support.toShortString();

		helper.assertTrue(Services.traces().removeLettingFall(level, support, cause), "the out-of-view fall was refused");
		helper.assertTrue(entries(level, cause + "/dependent").isEmpty(), "the stalactite was removed instead of falling");
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 5, 2));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 2));
			helper.assertEntityNotPresent(EntityTypes.FALLING_BLOCK);
		});
	}

	@GameTest(maxTicks = 40)
	public void removeLettingFallRefusesBadFallsAndViewers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		TraceService traces = Services.traces();
		// Would come to rest in a torch, on a slab, with an anvil, or in front of someone: nothing happens.
		helper.setBlock(1, 1, 1, Blocks.TORCH);
		helper.setBlock(6, 1, 1, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
		int[][] columns = {{1, 1}, {6, 1}, {1, 6}, {6, 6}};
		for (int[] c : columns) {
			helper.setBlock(c[0], 6, c[1], Blocks.STONE);
			helper.setBlock(c[0], 7, c[1], c[0] == 1 && c[1] == 6 ? Blocks.ANVIL : Blocks.SAND);
		}
		helper.assertFalse(traces.removeLettingFall(level, helper.absolutePos(new BlockPos(1, 6, 1)), "test:fall"), "sand would land in a torch");
		helper.assertFalse(traces.removeLettingFall(level, helper.absolutePos(new BlockPos(6, 6, 1)), "test:fall"), "sand would land on a slab");
		helper.assertFalse(traces.removeLettingFall(level, helper.absolutePos(new BlockPos(1, 6, 6)), "test:fall"), "an anvil fell");
		TraceService.Viewer nearLanding = viewerAt(helper, new Vec3(4.5, 1.0, 6.5), 90.0F, 0.0F);
		helper.assertFalse(traces.watchedBy(List.of(nearLanding)).removeLettingFall(level, helper.absolutePos(new BlockPos(6, 6, 6)), "test:fall"),
				"fell where a player stands");
		for (int[] c : columns) {
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(c[0], 6, c[1]));
		}
		// Nothing resting on it: a plain removal.
		helper.setBlock(4, 3, 3, Blocks.STONE);
		helper.assertTrue(traces.removeLettingFall(level, helper.absolutePos(new BlockPos(4, 3, 3)), "test:fall"), "a free block was refused");
		helper.runAfterDelay(10, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.assertEntityNotPresent(EntityTypes.FALLING_BLOCK);
			helper.succeed();
		});
	}

	@GameTest
	public void blocksUnderYourFeetGoWhileYouLookUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 feet = new Vec3(3.5, 2.0, 3.5);
		TraceService.Viewer up = viewerAt(helper, feet, 0.0F, -45.0F);
		TraceService.Viewer ahead = viewerAt(helper, feet, 0.0F, 0.0F);
		TraceService.Viewer slightlyUp = viewerAt(helper, feet, 0.0F, -20.0F);
		BlockPos under = helper.absolutePos(new BlockPos(3, 1, 3));
		BlockPos beside = helper.absolutePos(new BlockPos(4, 2, 3));
		helper.setBlock(3, 1, 3, Blocks.OAK_LEAVES);

		helper.assertTrue(TraceService.positionsOutOfView(level, List.of(under), List.of(up), 3, 160), "looking up, the block underfoot counts as seen");
		helper.assertTrue(TraceService.isOutOfView(level, new AABB(under), List.of(up), 3, 160), "the box form disagrees");
		helper.assertFalse(TraceService.positionsOutOfView(level, List.of(under), List.of(ahead), 3, 160), "looking ahead, the block underfoot is hidden");
		helper.assertFalse(TraceService.positionsOutOfView(level, List.of(under), List.of(slightlyUp), 3, 160), "20 degrees up already hides it");
		helper.assertFalse(TraceService.positionsOutOfView(level, List.of(beside), List.of(up), 3, 160), "a block at feet level counts as under the feet");

		helper.assertTrue(Services.traces().watchedBy(List.of(up)).remove(level, under, "test:under_you"), "the block under you did not go");
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
		helper.succeed();
	}

	@GameTest
	public void vetoRefusesEveryKindOfEdit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		TraceService traces = Services.traces();
		helper.setBlock(2, 1, 2, Blocks.STONE);
		helper.setBlock(2, 2, 2, Blocks.TORCH);
		helper.setBlock(5, 1, 5, Blocks.CHEST);
		((Container) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 5)))).setItem(0, new ItemStack(Items.COAL));
		helper.setBlock(1, 1, 6, Blocks.OAK_SIGN);
		Set<BlockPos> untouchable = Set.of(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(5, 1, 5)),
				helper.absolutePos(new BlockPos(1, 1, 6)), helper.absolutePos(new BlockPos(6, 3, 6)));
		TraceVeto veto = (l, pos) -> untouchable.contains(pos);
		BlockPos support = helper.absolutePos(new BlockPos(2, 1, 2));
		traces.addVeto(veto);
		try {
			helper.assertFalse(traces.remove(level, support, "test:veto"), "removed the support of a vetoed torch");
			helper.assertFalse(traces.batch(level, "test:veto").remove(support).commit(), "a batch got past the veto");
			helper.assertFalse(traces.forced().remove(level, support, "test:veto"), "the forced service got past the veto");
			helper.assertFalse(traces.figureDig(level, support, "test:veto"), "figureDig got past the veto");
			helper.assertFalse(traces.removeStack(level, helper.absolutePos(new BlockPos(5, 1, 5)), 0, 1, "test:veto"), "removeStack got past the veto");
			helper.assertFalse(traces.editSign(level, helper.absolutePos(new BlockPos(1, 1, 6)), null, null, "test:veto"), "editSign got past the veto");
			helper.assertFalse(traces.leave(level, helper.absolutePos(new BlockPos(6, 3, 6)), Blocks.GLOWSTONE.defaultBlockState(), "test:veto"),
					"leave got past the veto");
		} finally {
			traces.removeVeto(veto);
		}
		helper.assertBlockPresent(Blocks.TORCH, new BlockPos(2, 2, 2));
		helper.assertTrue(traces.remove(level, support, "test:veto"), "refused without the veto");
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 2, 2));
		helper.succeed();
	}

	@GameTest
	public void leaveCarriesBlockEntityContentsAndStacks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		TraceService traces = Services.traces();
		String cause = "test:left/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		helper.setBlock(1, 1, 1, Blocks.CHEST);
		Container source = (Container) level.getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
		source.setItem(0, new ItemStack(Items.DIAMOND, 3));
		CompoundTag chestData = level.getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1))).saveCustomOnly(level.registryAccess());
		helper.setBlock(1, 1, 5, Blocks.OAK_SIGN);
		SignBlockEntity sourceSign = (SignBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 5)));
		List<Component> lines = List.of(Component.literal("left here"), Component.empty(), Component.empty(), Component.empty());
		sourceSign.setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.FRONT);
		CompoundTag signData = sourceSign.saveCustomOnly(level.registryAccess());

		BlockPos chest = helper.absolutePos(new BlockPos(5, 1, 1));
		BlockPos sign = helper.absolutePos(new BlockPos(5, 1, 5));
		helper.assertTrue(traces.leave(level, chest, Blocks.CHEST.defaultBlockState(), chestData, cause), "chest with contents refused");
		helper.assertTrue(traces.leave(level, sign, Blocks.OAK_SIGN.defaultBlockState(), signData, cause), "sign with text refused");
		helper.assertFalse(traces.leave(level, helper.absolutePos(new BlockPos(3, 1, 3)), Blocks.STONE.defaultBlockState(), chestData, cause),
				"block entity data on a block without one");
		helper.assertTrue(((Container) level.getBlockEntity(chest)).getItem(0).is(Items.DIAMOND)
				&& ((Container) level.getBlockEntity(chest)).getItem(0).getCount() == 3, "the chest's contents did not come along");
		helper.assertTrue(((SignBlockEntity) level.getBlockEntity(sign)).getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString()
				.equals("left here"), "the sign's text did not come along");

		helper.assertTrue(traces.leaveStack(level, chest, new ItemStack(Items.BOOK), cause), "leaveStack refused");
		helper.assertTrue(((Container) level.getBlockEntity(chest)).getItem(1).is(Items.BOOK), "the stack is not in the next empty slot");
		helper.assertFalse(traces.leaveStack(level, chest, ItemStack.EMPTY, cause), "left an empty stack");
		helper.assertFalse(traces.leaveStack(level, helper.absolutePos(new BlockPos(3, 3, 3)), new ItemStack(Items.BOOK), cause), "left a stack in the air");
		helper.assertTrue(entries(level, cause).isEmpty(), "what was left went into the ledger");
		helper.succeed();
	}

	@GameTest
	public void restoreStackMovesItAndClosesTheEntry(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TraceService traces = Services.traces();
		String cause = "test:taken/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos to = helper.absolutePos(new BlockPos(5, 1, 5));
		helper.setBlock(1, 1, 1, Blocks.CHEST);
		helper.setBlock(5, 1, 5, Blocks.CHEST);
		((Container) level.getBlockEntity(from)).setItem(0, new ItemStack(Items.COAL, 5));

		helper.assertTrue(traces.removeStack(level, from, 0, 5, cause), "removeStack failed");
		TraceLedger.Entry taken = entries(level, cause).getFirst();
		helper.assertTrue(traces.restoreStack(level, taken, to), "restoreStack failed");
		ItemStack moved = ((Container) level.getBlockEntity(to)).getItem(0);
		helper.assertTrue(moved.is(Items.COAL) && moved.getCount() == 5, "the stack is not in the chest");
		helper.assertTrue(entries(level, cause).isEmpty(), "the ledger entry is still open");
		helper.assertFalse(traces.restoreStack(level, taken, to), "restored twice");

		helper.setBlock(3, 1, 3, Blocks.STONE);
		helper.assertTrue(traces.remove(level, helper.absolutePos(new BlockPos(3, 1, 3)), cause + "/block"), "remove failed");
		helper.assertFalse(traces.restoreStack(level, entries(level, cause + "/block").getFirst(), to), "restored a block entry as a stack");
		helper.succeed();
	}

	@GameTest
	public void restoreBlockPutsItBackOrOneBlockOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TraceService traces = Services.traces();
		String cause = "test:dark_corner/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		for (int x = 1; x <= 5; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(x, y, 3, Blocks.STONE);
			}
		}
		helper.setBlock(3, 2, 4, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
		BlockPos torch = helper.absolutePos(new BlockPos(3, 2, 4));
		helper.assertTrue(traces.remove(level, torch, cause), "remove failed");
		TraceLedger.Entry removed = entries(level, cause).getFirst();

		helper.assertFalse(traces.restoreBlock(level, removed, torch.south(3)), "restored 3 blocks away");
		helper.assertFalse(traces.restoreBlock(level, removed, torch.north()), "restored into the wall");
		helper.assertFalse(traces.restoreBlock(level, removed, torch.south()), "restored a wall torch with no wall");
		BlockPos off = torch.east();
		helper.assertTrue(traces.restoreBlock(level, removed, off), "restoring one block off failed");
		helper.assertTrue(level.getBlockState(off).is(Blocks.WALL_TORCH) && level.getBlockState(torch).isAir(), "the torch is not one block off");
		List<TraceLedger.Entry> now = entries(level, cause);
		helper.assertTrue(now.size() == 1 && now.getFirst().kind() == TraceLedger.Kind.MOVE && now.getFirst().pos().pos().equals(torch)
				&& now.getFirst().to().orElseThrow().equals(off), "the entry was not rewritten as a move: " + now);
		helper.assertFalse(traces.restoreBlock(level, removed, torch), "the old entry was restored again");

		// Back in the same spot: the entry is closed.
		helper.assertTrue(traces.remove(level, off, cause + "/again"), "second remove failed");
		helper.assertTrue(traces.restoreBlock(level, entries(level, cause + "/again").getFirst(), off), "restoring in place failed");
		helper.assertTrue(level.getBlockState(off).is(Blocks.WALL_TORCH) && entries(level, cause + "/again").isEmpty(), "in-place restore");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void figureDigsAndFillsInView(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(x, 1, z, Blocks.DIRT);
				helper.setBlock(x, 2, z, Blocks.DIRT);
				helper.setBlock(x, 3, z, Blocks.GRASS_BLOCK);
			}
		}
		TraceService watched = Services.traces().watchedBy(List.of(viewerAt(helper, new Vec3(3.5, 4.0, 1.5), 0.0F, 30.0F)));
		String cause = "test:goes_under/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		BlockPos top = helper.absolutePos(new BlockPos(3, 3, 3));

		helper.assertFalse(watched.remove(level, top, "test:in_view"), "an in-view removal went through");
		for (int y = 3; y >= 1; y--) {
			helper.assertTrue(watched.figureDig(level, helper.absolutePos(new BlockPos(3, y, 3)), cause), "figureDig refused at y " + y);
		}
		helper.assertTrue(entries(level, cause).size() == 3, "the dig is not ledgered");

		helper.setBlock(5, 3, 5, Blocks.CHEST);
		helper.setBlock(5, 3, 2, Blocks.BEDROCK);
		helper.setBlock(2, 3, 5, Blocks.WATER);
		helper.assertFalse(watched.figureDig(level, helper.absolutePos(new BlockPos(5, 3, 5)), cause), "dug a chest");
		helper.assertFalse(watched.figureDig(level, helper.absolutePos(new BlockPos(5, 3, 2)), cause), "dug bedrock");
		helper.assertFalse(watched.figureDig(level, helper.absolutePos(new BlockPos(2, 3, 5)), cause), "dug water");
		helper.assertFalse(watched.figureDig(level, helper.absolutePos(new BlockPos(2, 3, 4)), cause), "dug beside water");
		BlockPos placed = helper.absolutePos(new BlockPos(6, 3, 6));
		Services.watch().onPlaced((ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL), level, placed, Blocks.GRASS_BLOCK.defaultBlockState());
		helper.assertFalse(watched.figureDig(level, placed, cause), "dug a block a player placed");

		helper.assertFalse(watched.figureFill(level, top, Blocks.STONE.defaultBlockState(), cause), "filled with a block that was never dug");
		helper.assertFalse(watched.figureFill(level, top, Blocks.DIRT.defaultBlockState(), "test:other_dig"), "filled from another dig");
		helper.assertTrue(watched.figureFill(level, top, Blocks.DIRT.defaultBlockState(), cause), "the hole was not covered");
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(3, 3, 3));
		List<TraceLedger.Entry> after = entries(level, cause);
		TraceLedger.Entry move = after.stream().filter(e -> e.kind() == TraceLedger.Kind.MOVE).findFirst().orElse(null);
		helper.assertTrue(after.size() == 3 && move != null && move.pos().pos().equals(helper.absolutePos(new BlockPos(3, 1, 3)))
				&& move.to().orElseThrow().equals(top), "the fill is not ledgered as a move from the newest dug dirt: " + after);
		helper.assertTrue(watched.figureFill(level, top.below(), Blocks.DIRT.defaultBlockState(), cause), "the second dirt was refused");
		helper.assertFalse(watched.figureFill(level, top.below(2), Blocks.DIRT.defaultBlockState(), cause), "filled with more dirt than was dug");
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	@GameTest
	public void editSignReplacesTextAndLedgersTheOld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		TraceService traces = Services.traces();
		String cause = "test:blank_sign/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		helper.setBlock(2, 1, 2, Blocks.OAK_SIGN);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(pos);
		List<Component> mine = List.of(Component.literal("my house"), Component.empty(), Component.empty(), Component.empty());
		sign.setText(new SignText(mine, mine, DyeColor.RED, true), SignTextSlot.FRONT);

		TraceService.Viewer near = viewerAt(helper, new Vec3(2.5, 1.0, 4.5), 180.0F, 0.0F);
		helper.assertFalse(traces.watchedBy(List.of(near)).editSign(level, pos, null, null, cause), "edited a sign in view");
		helper.assertTrue(traces.editSign(level, pos, null, null, cause), "blanking failed");
		SignText front = sign.getText(SignTextSlot.FRONT);
		helper.assertTrue(front.getMessages(false).stream().allMatch(c -> c.getString().isEmpty()) && front.getColor() == DyeColor.RED && front.hasGlowingText(),
				"the sign is not blank with its colour and glow");
		List<TraceLedger.Entry> logged = entries(level, cause);
		helper.assertTrue(logged.size() == 1 && logged.getFirst().kind() == TraceLedger.Kind.BLOCK_ENTITY
				&& logged.getFirst().blockEntity().orElseThrow().toString().contains("my house"), "the old text is not in the ledger");

		helper.assertTrue(traces.editSign(level, pos, List.of(Component.literal("Stop.")), null, cause), "writing failed");
		helper.assertTrue(sign.getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString().equals("Stop."), "the text is not there");
		List<Component> five = List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty(), Component.empty());
		helper.assertFalse(traces.editSign(level, pos, five, null, cause), "five lines accepted");
		sign.setWaxed(true);
		helper.assertFalse(traces.editSign(level, pos, null, null, cause), "edited a waxed sign");
		helper.assertTrue(traces.editSign(level, pos, null, null, TraceService.WAXED_SIGN_CAUSE), "lore's F30 placement could not edit its waxed sign");
		helper.setBlock(4, 1, 4, Blocks.STONE);
		helper.assertFalse(traces.editSign(level, helper.absolutePos(new BlockPos(4, 1, 4)), null, null, cause), "edited stone");
		helper.succeed();
	}
}
