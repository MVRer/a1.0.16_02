package com.forzacode.a1016_02.core;

import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
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
import net.minecraft.world.level.block.state.BlockState;
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

	@GameTest(maxTicks = 80)
	public void removeLettingFallRefusesBadFallsAndViewers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		TraceService traces = Services.traces();
		// Would come to rest in a torch, on a slab, or with an anvil: nothing happens.
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
		// D-038: the removed block must be out of view, the fall and the landing need not be.
		BlockPos support = helper.absolutePos(new BlockPos(6, 6, 6));
		TraceService.Viewer nearSupport = viewerAt(helper, new Vec3(4.5, 5.0, 6.5), 90.0F, 0.0F);
		helper.assertFalse(traces.watchedBy(List.of(nearSupport)).removeLettingFall(level, support, "test:fall"), "removed a support in view");
		for (int[] c : columns) {
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(c[0], 6, c[1]));
		}
		TraceService.Viewer nearLanding = viewerAt(helper, new Vec3(4.5, 1.0, 6.5), 90.0F, 0.0F);
		helper.assertTrue(traces.watchedBy(List.of(nearLanding)).removeLettingFall(level, support, "test:fall"),
				"a fall seen only where it lands was refused");
		// Nothing resting on it: a plain removal.
		helper.setBlock(4, 3, 3, Blocks.STONE);
		helper.assertTrue(traces.removeLettingFall(level, helper.absolutePos(new BlockPos(4, 3, 3)), "test:fall"), "a free block was refused");
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.SAND, new BlockPos(6, 1, 6));
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.assertEntityNotPresent(EntityTypes.FALLING_BLOCK);
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
		BlockPos behind = helper.absolutePos(new BlockPos(3, 1, 1));
		helper.assertFalse(TraceService.positionsOutOfView(level, List.of(behind), List.of(up), 3, 160), "floor behind the player counts as under the feet");
		// Straddling two columns: the blocks under the hitbox go, the ring beyond it does not.
		TraceService.Viewer straddling = viewerAt(helper, new Vec3(4.0, 2.0, 3.5), 0.0F, -45.0F);
		helper.assertTrue(TraceService.positionsOutOfView(level, List.of(under, helper.absolutePos(new BlockPos(4, 1, 3))), List.of(straddling), 3, 160),
				"a block under the hitbox counts as seen");
		helper.assertFalse(TraceService.positionsOutOfView(level, List.of(helper.absolutePos(new BlockPos(5, 1, 3))), List.of(straddling), 3, 160),
				"a block beyond the hitbox counts as under the feet");

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
			helper.assertFalse(traces.figureDig(level, traces.startFigureDig(level, support, "test:veto"), support), "figureDig got past the veto");
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

	/** D-048: Ending D's last minute gives back in view, only while ending:last_minute is set, and never twice. */
	@GameTest
	public void restoreVisiblyGivesBackInViewOnlyInTheLastMinute(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HerobrineState state = HerobrineState.get(level.getServer());
		String cause = "test:last_minute/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		floor(helper, Blocks.STONE);
		helper.setBlock(3, 1, 3, Blocks.OAK_STAIRS);
		BlockPos stair = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.assertTrue(Services.traces().remove(level, stair, cause), "remove failed");
		TraceLedger.Entry removed = entries(level, cause).getFirst();
		helper.assertTrue(Services.traces().move(level, helper.absolutePos(new BlockPos(1, 0, 1)), helper.absolutePos(new BlockPos(1, 1, 1)), cause + "/move"),
				"move failed");
		TraceLedger.Entry moved = entries(level, cause + "/move").getFirst();
		// Someone stands right there, looking at it: every ordinary edit is refused.
		TraceService watched = Services.traces().watchedBy(List.of(viewerAt(helper, new Vec3(3.5, 1.0, 1.5), 0.0F, 20.0F)));
		BlockPos leafA = helper.absolutePos(new BlockPos(5, 3, 5));
		BlockPos leafB = helper.absolutePos(new BlockPos(5, 3, 4));
		Map<BlockPos, BlockState> crown = Map.of(leafA, Blocks.RED_POPLAR_LEAVES.defaultBlockState(), leafB, Blocks.ORANGE_POPLAR_LEAVES.defaultBlockState());
		boolean had = state.hasFlag(TraceService.LAST_MINUTE_FLAG);
		try {
			state.setFlag(TraceService.LAST_MINUTE_FLAG, false);
			helper.assertFalse(watched.restoreBlock(level, removed, stair), "restoreBlock gave back in view");
			helper.assertFalse(watched.restoreVisibly(level, removed, "test:giving"), "restoreVisibly worked outside the last minute");
			helper.assertFalse(watched.regrowVisibly(level, crown, "test:giving"), "regrowVisibly worked outside the last minute");

			state.setFlag(TraceService.LAST_MINUTE_FLAG, true);
			helper.assertFalse(watched.restoreVisibly(level, moved, "test:giving"), "restoreVisibly took a MOVE entry");
			helper.assertTrue(watched.restoreVisibly(level, removed, "test:giving"), "the stair did not come back in view");
			helper.assertTrue(level.getBlockState(stair).is(Blocks.OAK_STAIRS) && entries(level, cause).isEmpty(), "not back, or its entry is still open");
			helper.assertFalse(watched.restoreVisibly(level, removed, "test:giving"), "the stair came back twice");
			helper.assertFalse(watched.regrowVisibly(level, Map.of(leafA, Blocks.STONE.defaultBlockState()), "test:giving"), "grew stone back");
			helper.assertTrue(watched.regrowVisibly(level, crown, "test:giving"), "the leaves did not come back in view");
			helper.assertTrue(level.getBlockState(leafA).is(Blocks.RED_POPLAR_LEAVES) && level.getBlockState(leafB).is(Blocks.ORANGE_POPLAR_LEAVES),
					"the leaves are not there");
			helper.assertTrue(entries(level, "test:giving").isEmpty(), "giving back was ledgered");
			helper.assertFalse(watched.regrowVisibly(level, crown, "test:giving"), "leaves grew into leaves");
		} finally {
			state.setFlag(TraceService.LAST_MINUTE_FLAG, had);
		}
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
		BlockPos top = helper.absolutePos(new BlockPos(3, 3, 3));
		TraceService.FigureDig dig = watched.startFigureDig(level, top, "test:goes_under");

		helper.assertFalse(watched.remove(level, top, "test:in_view"), "an in-view removal went through");
		for (int y = 3; y >= 1; y--) {
			helper.assertTrue(watched.figureDig(level, dig, helper.absolutePos(new BlockPos(3, y, 3))), "figureDig refused at y " + y);
		}
		helper.assertTrue(entries(level, dig.ledgerCause()).size() == 3, "the dig is not ledgered under its own cause");

		helper.setBlock(5, 3, 5, Blocks.CHEST);
		helper.setBlock(5, 3, 2, Blocks.BEDROCK);
		helper.setBlock(2, 3, 5, Blocks.WATER);
		helper.setBlock(7, 3, 3, Blocks.DIRT);
		helper.assertFalse(watched.figureDig(level, dig, helper.absolutePos(new BlockPos(5, 3, 5))), "dug a chest");
		helper.assertFalse(watched.figureDig(level, dig, helper.absolutePos(new BlockPos(5, 3, 2))), "dug bedrock");
		helper.assertFalse(watched.figureDig(level, dig, helper.absolutePos(new BlockPos(2, 3, 5))), "dug water");
		helper.assertFalse(watched.figureDig(level, dig, helper.absolutePos(new BlockPos(2, 3, 4))), "dug beside water");
		helper.assertFalse(watched.figureDig(level, dig, helper.absolutePos(new BlockPos(7, 3, 3))), "dug 4 blocks from the column");
		BlockPos placed = helper.absolutePos(new BlockPos(6, 3, 6));
		Services.watch().onPlaced((ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL), level, placed, Blocks.GRASS_BLOCK.defaultBlockState());
		helper.assertFalse(watched.figureDig(level, dig, placed), "dug a block a player placed");

		List<TraceLedger.Entry> dug = watched.figureDug(level, dig);
		helper.assertTrue(dug.size() == 3 && dug.getFirst().pos().pos().equals(helper.absolutePos(new BlockPos(3, 1, 3))), "figureDug: " + dug);
		TraceService.FigureDig other = watched.startFigureDig(level, top, "test:goes_under");
		helper.assertFalse(other.id().equals(dig.id()), "two digs share an id");
		helper.assertFalse(watched.figureFill(level, other, dug.getFirst(), top), "filled from another dig");
		helper.assertFalse(watched.figureFill(level, dig, dug.getFirst(), helper.absolutePos(new BlockPos(7, 4, 3))), "filled 4 blocks from the column");
		helper.assertTrue(watched.figureFill(level, dig, dug.getFirst(), top), "the hole was not covered");
		helper.assertTrue(level.getBlockState(top) == dug.getFirst().state().orElseThrow(), "the fill is not the dug state");
		List<TraceLedger.Entry> after = entries(level, dig.ledgerCause());
		TraceLedger.Entry move = after.stream().filter(e -> e.kind() == TraceLedger.Kind.MOVE).findFirst().orElse(null);
		helper.assertTrue(after.size() == 3 && move != null && move.pos().pos().equals(helper.absolutePos(new BlockPos(3, 1, 3)))
				&& move.to().orElseThrow().equals(top), "the fill is not ledgered as a move from where it was dug: " + after);
		helper.assertFalse(watched.figureFill(level, dig, dug.getFirst(), top.below()), "the same block came back twice");
		helper.assertTrue(watched.figureFill(level, dig, dug.get(1), top.below()), "the second dirt was refused");

		// The exact dug state comes back: a bottom slab stays a bottom slab.
		BlockPos slabPos = helper.absolutePos(new BlockPos(5, 4, 4));
		helper.setBlock(5, 4, 4, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
		helper.assertTrue(watched.figureDig(level, dig, slabPos), "the slab was not dug");
		TraceLedger.Entry slab = watched.figureDug(level, dig).getFirst();
		helper.assertTrue(watched.figureFill(level, dig, slab, slabPos), "the slab did not come back");
		helper.assertTrue(level.getBlockState(slabPos).getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "the slab came back as another block");
		helper.runAfterDelay(5, () -> {
			helper.assertEntityNotPresent(EntityTypes.ITEM);
			helper.succeed();
		});
	}

	@GameTest
	public void equipFromLedgerGivesTheMobWhatHeTook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.STONE);
		TraceService traces = Services.traces();
		String cause = "test:zombie_sword/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		BlockPos chest = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos network = helper.absolutePos(new BlockPos(6, 1, 6));
		helper.setBlock(1, 1, 1, Blocks.CHEST);
		helper.setBlock(6, 1, 6, Blocks.CHEST);
		Container own = (Container) level.getBlockEntity(chest);
		own.setItem(0, new ItemStack(Items.IRON_SWORD));
		own.setItem(1, new ItemStack(Items.GOLDEN_SWORD));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // a spawn may roll a weapon
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

		helper.assertTrue(traces.removeStack(level, chest, 0, 1, cause + "/taken"), "removeStack failed");
		TraceLedger.Entry taken = entries(level, cause + "/taken").getFirst();
		TraceService.Viewer near = viewerAt(helper, new Vec3(2.5, 1.0, 4.5), -90.0F, 0.0F);
		helper.assertFalse(traces.watchedBy(List.of(near)).equipFromLedger(level, taken, zombie, EquipmentSlot.MAINHAND, cause), "equipped in view");
		helper.assertTrue(traces.equipFromLedger(level, taken, zombie, EquipmentSlot.MAINHAND, cause), "equipping the taken sword failed");
		helper.assertTrue(ItemStack.matches(zombie.getItemBySlot(EquipmentSlot.MAINHAND), taken.stack().orElseThrow())
				&& zombie.getDropChances().byEquipment(EquipmentSlot.MAINHAND) > 1.0F && zombie.getDropChances().isPreserved(EquipmentSlot.MAINHAND)
				&& zombie.isPersistenceRequired(), "the zombie does not hold exactly that sword, with a guaranteed drop, for good");
		List<TraceLedger.Entry> equipped = entries(level, cause);
		helper.assertTrue(entries(level, cause + "/taken").isEmpty() && equipped.size() == 1 && equipped.getFirst().kind() == TraceLedger.Kind.EQUIP
				&& equipped.getFirst().entity().orElseThrow().equals(zombie.getUUID()) && equipped.getFirst().pos().pos().equals(chest),
				"the entry was not rewritten as worn by the zombie: " + equipped);
		helper.assertFalse(traces.equipFromLedger(level, taken, zombie, EquipmentSlot.OFFHAND, cause), "the same stack was equipped twice");

		// Moved into the network chest first: it comes out of that chest.
		helper.assertTrue(traces.moveStack(level, chest, 1, network, cause + "/moved"), "moveStack failed");
		TraceLedger.Entry moved = entries(level, cause + "/moved").getFirst();
		helper.assertFalse(traces.equipFromLedger(level, moved, zombie, EquipmentSlot.MAINHAND, cause), "replaced what the zombie holds");
		helper.assertTrue(traces.equipFromLedger(level, moved, zombie, EquipmentSlot.OFFHAND, cause), "equipping from the network chest failed");
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.GOLDEN_SWORD)
				&& ((Container) level.getBlockEntity(network)).getItem(0).isEmpty(), "the sword did not leave the network chest");

		// Gone from where it was moved to: nothing to give, nothing created.
		own.setItem(2, new ItemStack(Items.BOW));
		helper.assertTrue(traces.moveStack(level, chest, 2, network, cause + "/gone"), "second moveStack failed");
		((Container) level.getBlockEntity(network)).setItem(0, ItemStack.EMPTY);
		Zombie other = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 2));
		other.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		helper.assertFalse(traces.equipFromLedger(level, entries(level, cause + "/gone").getFirst(), other, EquipmentSlot.MAINHAND, cause),
				"equipped a stack that is no longer there");
		helper.assertTrue(other.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "an item was created");
		zombie.discard();
		other.discard();
		helper.succeed();
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
