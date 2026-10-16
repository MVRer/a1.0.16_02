package com.forzacode.a1016_02.core;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.ending.d.Undo;
import com.forzacode.a1016_02.lore.sc.RecordRules;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc.Tracks;
import com.forzacode.a1016_02.world.sc.Turned;
import com.forzacode.a1016_02.world.sc.WhereHeStood;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SC-5, the self-created layer put together (docs/SELF_CREATED.md): core's CONVERT undo takes back a conversion of a
 * block a player placed as long as the block is still what the conversion left (so Ending D turns 08's blocks back),
 * and blocks it once the block changed since; an entry ledgered before the ledger kept that block keeps the old rule;
 * the ledger keeps it under a key of its own; and the statistics (14 d) never count an undo's edits or 08's turning
 * back.
 */
public class SelfCreatedIntegrationTests {
	private static BlockState furnace(Direction facing) {
		return Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, facing);
	}

	private static List<TraceLedger.Entry> entries(MinecraftServer server, String cause) {
		return TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(cause)).toList();
	}

	/** A furnace a player placed at {@code rel}, on a stone floor. */
	private static BlockPos placed(GameTestHelper helper, ServerPlayer player, BlockPos rel, Direction facing) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(rel.below(), Blocks.STONE);
		helper.setBlock(rel, furnace(facing));
		BlockPos at = helper.absolutePos(rel);
		Services.watch().onPlaced(player, level, at, furnace(facing));
		return at;
	}

	/**
	 * A conversion of a block a player placed (a furnace turned) keeps the block it left; its undo turns it back while it
	 * is still exactly that (DONE, both entries gone), and answers BLOCKED once the block changed since (the player turned
	 * it again); Ending D's afterward gives it back the same way. An entry from before the ledger kept that block keeps
	 * the old rule: a block a player placed is theirs (BLOCKED).
	 */
	@GameTest
	public void aConvertedPlacedBlockGoesBackUntilItChangesSince(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		TraceService traces = Services.traces().forced();
		String cause = "test:sc5/convert/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		String undo = "test:sc5/undo/" + helper.absolutePos(BlockPos.ZERO).toShortString();

		BlockPos turned = placed(helper, player, new BlockPos(1, 1, 1), Direction.NORTH);
		helper.assertTrue(Services.watch().wasPlacedByPlayer(level, turned), "setup: not marked placed");
		helper.assertTrue(traces.convert(level, turned, furnace(Direction.EAST), cause), "the conversion was refused");
		TraceLedger.Entry entry = entries(server, cause).getFirst();
		helper.assertTrue(entry.kind() == TraceLedger.Kind.CONVERT && entry.after().equals(Optional.of(furnace(Direction.EAST)))
				&& entry.state().equals(Optional.of(furnace(Direction.NORTH))), "the entry: " + entry);
		helper.assertTrue(traces.undo(level, entry, undo) == TraceService.UndoResult.DONE, "a placed block he turned was not turned back");
		helper.assertTrue(level.getBlockState(turned) == furnace(Direction.NORTH) && entries(server, cause).isEmpty() && entries(server, undo).isEmpty(),
				"not back, or the ledger kept an entry: " + level.getBlockState(turned));

		BlockPos changed = placed(helper, player, new BlockPos(3, 1, 1), Direction.NORTH);
		helper.assertTrue(traces.convert(level, changed, furnace(Direction.EAST), cause), "the second conversion was refused");
		TraceLedger.Entry second = entries(server, cause).getFirst();
		helper.setBlock(new BlockPos(3, 1, 1), furnace(Direction.SOUTH));
		Services.watch().onPlaced(player, level, changed, furnace(Direction.SOUTH));
		helper.assertTrue(traces.undo(level, second, undo) == TraceService.UndoResult.BLOCKED, "a block changed since was taken back");
		helper.assertTrue(level.getBlockState(changed) == furnace(Direction.SOUTH) && entries(server, cause).size() == 1, "the player's change was lost");
		TraceLedger.get(server).remove(second);

		BlockPos ending = placed(helper, player, new BlockPos(5, 1, 1), Direction.WEST);
		String turnedCause = Turned.CAUSE + "/test/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		helper.assertTrue(traces.convert(level, ending, furnace(Direction.SOUTH), turnedCause), "the third conversion was refused");
		TraceLedger.Entry third = entries(server, turnedCause).getFirst();
		helper.assertTrue(Undo.undo(server, third, traces) == Undo.Result.DONE && level.getBlockState(ending) == furnace(Direction.WEST)
				&& entries(server, turnedCause).isEmpty(), "Ending D did not turn it back: " + level.getBlockState(ending));

		BlockPos old = placed(helper, player, new BlockPos(1, 1, 3), Direction.EAST);
		TraceLedger.Entry before = TraceLedger.get(server).add(new TraceLedger.Entry(TraceLedger.Kind.CONVERT, cause, 0, GlobalPos.of(level.dimension(), old),
				Optional.empty(), Optional.of(furnace(Direction.NORTH)), Optional.empty(), Optional.empty(), -1, -1));
		helper.assertTrue(before.after().isEmpty() && traces.undo(level, before, undo) == TraceService.UndoResult.BLOCKED
				&& level.getBlockState(old) == furnace(Direction.EAST), "an entry from before took a placed block back");
		TraceLedger.get(server).remove(before);
		helper.succeed();
	}

	/** The block a conversion left round-trips under its own key; an entry saved without it (v1.3) loads with none. */
	@GameTest
	public void theLedgerKeepsWhatAConversionLeftUnderAKeyOfItsOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		TraceLedger.Entry entry = new TraceLedger.Entry(TraceLedger.Kind.CONVERT, "test:sc5/codec", 3, GlobalPos.of(level.dimension(), new BlockPos(1, 2, 3)),
				Optional.empty(), Optional.of(furnace(Direction.NORTH)), Optional.empty(), Optional.empty(), -1, -1, Optional.empty(),
				Optional.of(furnace(Direction.EAST)));
		Tag tag = TraceLedger.Entry.CODEC.encodeStart(ops, entry).getOrThrow();
		helper.assertTrue(tag instanceof net.minecraft.nbt.CompoundTag c && c.contains("after"), "no key of its own: " + tag);
		helper.assertTrue(TraceLedger.Entry.CODEC.parse(ops, tag).getOrThrow().equals(entry), "the codec lost something");
		net.minecraft.nbt.CompoundTag v13 = ((net.minecraft.nbt.CompoundTag) tag).copy();
		v13.remove("after");
		helper.assertTrue(TraceLedger.Entry.CODEC.parse(ops, v13).getOrThrow().after().isEmpty(), "an entry from before did not load");
		net.minecraft.nbt.CompoundTag broken = ((net.minecraft.nbt.CompoundTag) tag).copy();
		broken.putString("after", "not a block");
		helper.assertTrue(TraceLedger.Entry.CODEC.parse(ops, broken).result().map(e -> e.after().isEmpty()).orElse(false), "a broken key broke the entry");
		helper.succeed();
	}

	/** Every part of the layer put its revert in for the switch: core's and one per number, 01 to 15 (none run here). */
	@GameTest
	public void everyNumberRegisteredItsRevert(GameTestHelper helper) {
		List<String> ids = SelfCreated.registered().stream().map(java.util.Map.Entry::getKey).toList();
		helper.assertTrue(ids.contains("core"), "core's revert: " + ids);
		for (int n = 1; n <= 15; n++) {
			String id = String.format(java.util.Locale.ROOT, "sc%02d", n);
			helper.assertTrue(ids.contains(id), id + " has no revert: " + ids);
		}
		helper.succeed();
	}

	/**
	 * 14 (d) counts his traces only: never an undo's own edits or their dependents (core's, the switch's give-backs),
	 * 08's turning back, Ending D's, what others left (t0rch's shaft), a give-back, the figure's dig or an accident's (a
	 * trap's taken block is its one clue; 15's is natural anyway); the layer's own traces count.
	 */
	@GameTest
	public void theStatisticsCountHisTracesOnly(GameTestHelper helper) {
		for (String cause : List.of(TraceService.UNDO_CAUSE, TraceService.UNDO_CAUSE + "/dependent", Turned.CAUSE + "/back", TorchTrail.CAUSE,
				TorchTrail.CAUSE + "/dependent", "ending:d/undo", TraceService.GIVE_BACK_CAUSE, "entity:dig_under", "lore:left/F07", "accident:sc15",
				"accident:missing_rung")) {
			helper.assertFalse(RecordRules.causeCounts(cause), cause + " counted");
		}
		for (String cause : List.of(Turned.CAUSE, Tracks.CAUSE, Tracks.CAUSE + "/dependent", WhereHeStood.CAUSE, "dig:sc13", "world:scar")) {
			helper.assertTrue(RecordRules.causeCounts(cause), cause + " did not count");
		}
		helper.succeed();
	}
}
