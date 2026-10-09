package com.forzacode.a1016_02.ending.d;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.HouseCopyApi;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The afterward's undo: what stays (the skip rules), and that every undone entry is undone exactly once, through core,
 * without creating anything.
 */
public class EndingDUndoTests extends EndingDStingTests {
	private static List<TraceLedger.Entry> entries(MinecraftServer server, String cause) {
		return TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(cause)).toList();
	}

	private static TraceLedger.Entry only(GameTestHelper helper, MinecraftServer server, String cause) {
		List<TraceLedger.Entry> found = entries(server, cause);
		helper.assertTrue(found.size() == 1, "expected one entry for " + cause + ", got " + found);
		return found.getFirst();
	}

	private static TraceLedger.Entry entry(ServerLevel level, BlockPos pos, String cause, TraceLedger.Kind kind) {
		return new TraceLedger.Entry(kind, cause, 0, GlobalPos.of(level.dimension(), pos), Optional.empty(), Optional.of(Blocks.STONE.defaultBlockState()),
				Optional.empty(), Optional.empty(), -1, -1);
	}

	private static String cause(GameTestHelper helper, String what) {
		return "test:ending_d/" + what + "/" + helper.absolutePos(BlockPos.ZERO).toShortString();
	}

	@GameTest(structure = EndingDSupport.YARD)
	public void undoKeepsWhatOthersLeftTheStairFragmentsAndCrosses(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
		String[] stays = {"lore:left/F30", Stair.CAUSE, Undo.CAUSE + "/dependent", "lore:his/F13", "lore:his/F03", "accident:cross",
				"accident:cross/dependent", "world:cross_row"};
		for (String cause : stays) {
			helper.assertTrue(Undo.skipReason(server, entry(level, at, cause, TraceLedger.Kind.REMOVE)).isPresent(), cause + " would be undone");
		}
		helper.assertTrue(Undo.skipReason(server, entry(level, at, "world:still_burning", TraceLedger.Kind.REMOVE)).isPresent(),
				"still burning's cleared ground would come back");
		helper.assertTrue(Undo.skipReason(server, entry(level, at, "world:still_burning", TraceLedger.Kind.REMOVE_STACK)).isEmpty(),
				"still burning's other edits stay too (only its REMOVE entries are kept)");
		helper.assertTrue(Undo.skipReason(server, entry(level, at, "accident:lava_floor", TraceLedger.Kind.EQUIP)).isPresent(), "a worn stack would be undone");
		// His removals are undone: the tunnels, the network under the house, the house copy (moved back), new scars, traps.
		for (String cause : new String[] {"dig:tunnel", "dig:under_you", HouseCopyApi.CAUSE, HouseCopyApi.CAUSE_LOCAL, "world:new_scar/bare", "accident:lava_floor",
				"lore:his/blank_sign", Grove.LEAF_CAUSE, Stair.LOSS_CAUSE, Stair.FLOOD_CAUSE, Chamber.NAMED_CAUSE}) {
			helper.assertTrue(Undo.skipReason(server, entry(level, at, cause, TraceLedger.Kind.REMOVE)).isEmpty(), cause + " would stay");
		}
		// Anything right next to a placed fragment stays: it needs its place.
		helper.assertTrue(Undo.nearFragment(java.util.Map.of("F99", GlobalPos.of(level.dimension(), at.above(2))), entry(level, at, "dig:tunnel",
				TraceLedger.Kind.REMOVE)), "a removal beside a fragment would be undone");
		helper.assertFalse(Undo.nearFragment(java.util.Map.of("F99", GlobalPos.of(level.dimension(), at.above(3))), entry(level, at, "dig:tunnel",
				TraceLedger.Kind.REMOVE)), "a removal three blocks from a fragment stays");

		// In action: a block others left and he took stays gone.
		helper.setBlock(3, 1, 3, Blocks.STONE);
		helper.assertTrue(Services.traces().forced().remove(level, at, "lore:left/F99"), "setup remove failed");
		TraceLedger.Entry left = only(helper, server, "lore:left/F99");
		helper.assertTrue(Undo.undo(server, left, Services.traces().forced(), null) == Undo.Result.SKIP, "a lore:left entry was not skipped");
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
		TraceLedger.get(server).remove(left);
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.YARD)
	public void undoPutsBlocksBackExactlyOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		TraceService traces = Services.traces().forced();
		TraceLedger ledger = TraceLedger.get(server);
		EndingDSupport.fill(helper, 0, 0, 0, 15, 0, 15, Blocks.STONE);

		// REMOVE: back once, then nothing left to do.
		helper.setBlock(2, 1, 2, Blocks.STONE);
		BlockPos removed = helper.absolutePos(new BlockPos(2, 1, 2));
		String removeCause = cause(helper, "remove");
		helper.assertTrue(traces.remove(level, removed, removeCause), "setup remove failed");
		TraceLedger.Entry entry = only(helper, server, removeCause);
		helper.assertTrue(Undo.undo(server, entry, traces, null) == Undo.Result.DONE, "the removal was not undone");
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 2));
		helper.assertTrue(entries(server, removeCause).isEmpty(), "the entry is still open");
		helper.assertTrue(Undo.undo(server, entry, traces, null) == Undo.Result.BLOCKED, "an undone entry was undone again");

		// REMOVE over something the player built: never overwritten, never forced.
		helper.setBlock(4, 1, 2, Blocks.STONE);
		BlockPos built = helper.absolutePos(new BlockPos(4, 1, 2));
		String builtCause = cause(helper, "built_over");
		helper.assertTrue(traces.remove(level, built, builtCause), "setup remove failed");
		helper.setBlock(4, 1, 2, Blocks.OAK_PLANKS);
		TraceLedger.Entry over = only(helper, server, builtCause);
		helper.assertTrue(Undo.undo(server, over, traces, null) == Undo.Result.BLOCKED, "a removal was put back over a built block");
		helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 2));
		ledger.remove(over);

		// MOVE: back where it came from, both entries gone.
		helper.setBlock(2, 1, 5, Blocks.GOLD_BLOCK);
		String moveCause = cause(helper, "move");
		helper.assertTrue(traces.move(level, helper.absolutePos(new BlockPos(2, 1, 5)), helper.absolutePos(new BlockPos(2, 1, 8)), moveCause), "setup move failed");
		TraceLedger.Entry move = only(helper, server, moveCause);
		int before = ledger.entries().size();
		helper.assertTrue(Undo.undo(server, move, traces, null) == Undo.Result.DONE, "the move was not undone");
		helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(2, 1, 5));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 8));
		helper.assertTrue(entries(server, moveCause).isEmpty() && ledger.entries().size() == before - 1, "the move left entries behind");
		helper.assertTrue(Undo.undo(server, move, traces, null) == Undo.Result.BLOCKED, "the move was undone twice");
		helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(2, 1, 5));

		// CONVERT: dirt back to grass.
		helper.setBlock(6, 1, 2, Blocks.GRASS_BLOCK);
		String convertCause = cause(helper, "convert");
		helper.assertTrue(traces.convert(level, helper.absolutePos(new BlockPos(6, 1, 2)), Blocks.DIRT.defaultBlockState(), convertCause), "setup convert failed");
		TraceLedger.Entry convert = only(helper, server, convertCause);
		helper.assertTrue(Undo.undo(server, convert, traces, null) == Undo.Result.DONE, "the conversion was not undone");
		helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(6, 1, 2));
		helper.assertTrue(entries(server, convertCause).isEmpty() && entries(server, Undo.CAUSE).stream().noneMatch(e -> e.pos().pos().equals(
				helper.absolutePos(new BlockPos(6, 1, 2)))), "the conversion left entries behind");

		// Sign text he blanked comes back.
		helper.setBlock(8, 1, 2, Blocks.OAK_SIGN);
		BlockPos signPos = helper.absolutePos(new BlockPos(8, 1, 2));
		SignBlockEntity sign = helper.getBlockEntity(new BlockPos(8, 1, 2), SignBlockEntity.class);
		sign.setText(new SignText(List.of(Component.literal("we were"), Component.literal("here"), Component.empty(), Component.empty()),
				List.of(Component.literal("we were"), Component.literal("here"), Component.empty(), Component.empty()),
				net.minecraft.world.item.DyeColor.BLACK, false), SignTextSlot.FRONT);
		String signCause = cause(helper, "sign");
		helper.assertTrue(traces.editSign(level, signPos, List.of(), List.of(), signCause), "setup blank failed");
		TraceLedger.Entry blank = only(helper, server, signCause);
		helper.assertTrue(Undo.undo(server, blank, traces, null) == Undo.Result.DONE, "the blank sign was not undone");
		String text = sign.getText(SignTextSlot.FRONT).getMessages(false).get(0).getString() + " " + sign.getText(SignTextSlot.FRONT).getMessages(false).get(1).getString();
		helper.assertTrue(text.equals("we were here"), "the sign says '" + text + "'");
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.YARD)
	public void undoReturnsStacksExactlyOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		TraceService traces = Services.traces().forced();
		EndingDSupport.fill(helper, 0, 0, 0, 15, 0, 15, Blocks.STONE);
		helper.setBlock(3, 1, 3, Blocks.CHEST);
		helper.setBlock(3, 1, 7, Blocks.CHEST);
		Container home = helper.getBlockEntity(new BlockPos(3, 1, 3), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
		Container dark = helper.getBlockEntity(new BlockPos(3, 1, 7), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
		home.setItem(0, new ItemStack(Items.DIAMOND, 5));
		home.setItem(1, new ItemStack(Items.EMERALD, 3));
		BlockPos homePos = helper.absolutePos(new BlockPos(3, 1, 3));
		BlockPos darkPos = helper.absolutePos(new BlockPos(3, 1, 7));

		String takeCause = cause(helper, "take");
		helper.assertTrue(traces.removeStack(level, homePos, 0, 5, takeCause), "setup take failed");
		TraceLedger.Entry take = only(helper, server, takeCause);
		helper.assertTrue(Undo.undo(server, take, traces, null) == Undo.Result.DONE, "the taken stack did not come back");
		helper.assertTrue(Undo.undo(server, take, traces, null) == Undo.Result.BLOCKED, "the taken stack came back twice");
		int diamonds = 0;
		for (int i = 0; i < home.getContainerSize(); i++) {
			if (home.getItem(i).is(Items.DIAMOND)) {
				diamonds += home.getItem(i).getCount();
			}
		}
		helper.assertTrue(diamonds == 5, "expected 5 diamonds back home, found " + diamonds);

		String moveCause = cause(helper, "move_stack");
		helper.assertTrue(traces.moveStack(level, homePos, 1, darkPos, moveCause), "setup move failed");
		TraceLedger.Entry moved = only(helper, server, moveCause);
		helper.assertTrue(Undo.undo(server, moved, traces, null) == Undo.Result.DONE, "the moved stack did not come back");
		helper.assertTrue(dark.isEmpty(), "the stack is still in the dark chest");
		int emeralds = 0;
		for (int i = 0; i < home.getContainerSize(); i++) {
			if (home.getItem(i).is(Items.EMERALD)) {
				emeralds += home.getItem(i).getCount();
			}
		}
		helper.assertTrue(emeralds == 3 && entries(server, moveCause).isEmpty(), "the emeralds came back " + emeralds + "x");
		home.clearContent();
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.YARD)
	public void afterwardPassesUndoNewestFirstAndNeverTwice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		TraceService traces = Services.traces().forced();
		EndingDSupport.fill(helper, 0, 0, 0, 15, 0, 15, Blocks.STONE);
		String prefix = cause(helper, "pass");
		// A wall with a torch on it, both taken: the torch only survives once its wall is back (newest first, again).
		helper.setBlock(5, 1, 5, Blocks.STONE);
		helper.setBlock(5, 1, 6, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING,
				net.minecraft.core.Direction.SOUTH));
		helper.assertTrue(traces.remove(level, helper.absolutePos(new BlockPos(5, 1, 5)), prefix + "/wall"), "setup failed");
		helper.setBlock(9, 1, 9, Blocks.IRON_BLOCK);
		helper.assertTrue(traces.move(level, helper.absolutePos(new BlockPos(9, 1, 9)), helper.absolutePos(new BlockPos(9, 1, 12)), prefix + "/move"),
				"setup failed");
		helper.setBlock(9, 1, 12, Blocks.AIR);
		helper.setBlock(12, 1, 3, Blocks.MOSS_BLOCK);
		helper.assertTrue(traces.remove(level, helper.absolutePos(new BlockPos(12, 1, 3)), prefix + "/remove"), "setup failed");

		EndingDState data = new EndingDState();
		EndingDConfig cfg = new EndingDConfig();
		Afterward.clear();
		for (int pass = 0; pass < 4 && !data.undoFinished(); pass++) {
			Afterward.step(server, data, cfg, traces, 1000, e -> e.cause().startsWith(prefix));
		}
		helper.assertTrue(data.undoFinished(), "the undo did not finish");
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 1, 5));
		helper.assertBlockPresent(Blocks.WALL_TORCH, new BlockPos(5, 1, 6));
		helper.assertBlockPresent(Blocks.MOSS_BLOCK, new BlockPos(12, 1, 3));
		// The moved block was taken by the player: nothing comes back (nothing is created).
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, 1, 9));
		helper.assertTrue(data.undone() == 3, "expected 3 undone, got " + data.undone());
		List<TraceLedger.Entry> left = TraceLedger.get(server).entries().stream().filter(e -> e.cause().startsWith(prefix)).toList();
		helper.assertTrue(left.size() == 1 && left.getFirst().kind() == TraceLedger.Kind.MOVE, "expected only the lost move left, got " + left);
		// Another pass changes nothing.
		Afterward.clear();
		EndingDState again = new EndingDState();
		Afterward.step(server, again, cfg, traces, 1000, e -> e.cause().startsWith(prefix));
		helper.assertTrue(again.undone() == 0, "a second pass undid " + again.undone() + " more");
		TraceLedger.get(server).remove(left.getFirst());
		Afterward.clear();
		BlockState torch = level.getBlockState(helper.absolutePos(new BlockPos(5, 1, 6)));
		helper.assertTrue(torch.is(Blocks.WALL_TORCH), "the torch is not on its wall");
		helper.succeed();
	}
}
