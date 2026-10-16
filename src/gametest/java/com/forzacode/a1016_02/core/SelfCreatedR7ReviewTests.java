package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.forzacode.a1016_02.ending.d.Undo;
import com.forzacode.a1016_02.world.sc.Turned;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Bug hunt R7 on the self-created layer (docs/SELF_CREATED.md): core's seams and the safety rules. Each test states the
 * rule it holds the code to; every one of them failed on {@code self-created} at 5348ede.
 */
public class SelfCreatedR7ReviewTests {
	private static BlockState furnace(Direction facing) {
		return Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, facing);
	}

	private static List<TraceLedger.Entry> entries(MinecraftServer server, String cause) {
		return TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(cause)).toList();
	}

	/**
	 * 08 and Ending D (SC-5's core ask): Ending D's afterward turns 08's blocks back, and only a block the player changed
	 * since stays theirs. A turned furnace that vanilla lit since (the player smelts in it: {@code lit} true, its facing
	 * still the one 08 left) is not the player turning it again, yet core's CONVERT undo answers BLOCKED because the block
	 * is not exactly {@code after}; the afterward never comes back to it once its pass ends, so it stays turned for good.
	 * The same holds for a beehive whose honey rose, a lectern given a book, a chest that got waterlogged.
	 */
	@GameTest
	public void aTurnedFurnaceLitSinceStillTurnsBackInEndingD(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		TraceService traces = Services.traces().forced();
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel.below(), Blocks.STONE);
		helper.setBlock(rel, furnace(Direction.NORTH));
		BlockPos at = helper.absolutePos(rel);
		Services.watch().onPlaced(player, level, at, furnace(Direction.NORTH));
		String cause = Turned.CAUSE + "/r7/" + at.toShortString();
		helper.assertTrue(traces.convert(level, at, furnace(Direction.EAST), cause), "08's turn was refused");
		TraceLedger.Entry entry = entries(server, cause).getFirst();
		// The player smelts: vanilla lights the furnace (only LIT changes; the facing is still 08's).
		level.setBlock(at, level.getBlockState(at).setValue(FurnaceBlock.LIT, true), Block.UPDATE_ALL);
		Undo.Result result = Undo.undo(server, entry, traces);
		BlockState now = level.getBlockState(at);
		TraceLedger.get(server).remove(entry);
		helper.assertTrue(result == Undo.Result.DONE && now.getValue(FurnaceBlock.FACING) == Direction.NORTH,
				"Ending D left a lit furnace turned (the player never turned it): " + result + ", " + now);
		helper.succeed();
	}

	/**
	 * v1.3's door left open (a door a player placed, opened while they were away) under SC-5's CONVERT rule: v1.3's Ending D
	 * never touched a player's door (a placed block is theirs). Now the undo takes a conversion back whenever the block is
	 * exactly what it left, so a door the player closed and later opened again themselves (exactly the state the card left)
	 * is closed by Ending D's afterward; the same for a turned block (08) the player broke and placed again the same way.
	 * Core's rule is not behind the switch, so this holds with the layer off too.
	 */
	@GameTest
	public void endingDLeavesADoorThePlayerOpenedAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		TraceService traces = Services.traces().forced();
		BlockPos lowerRel = new BlockPos(2, 1, 2);
		helper.setBlock(lowerRel.below(), Blocks.STONE);
		BlockState lower = Blocks.OAK_DOOR.defaultBlockState();
		BlockState upper = lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.UPPER);
		helper.setBlock(lowerRel, lower);
		helper.setBlock(lowerRel.above(), upper);
		BlockPos low = helper.absolutePos(lowerRel);
		Services.watch().onPlaced(player, level, low, lower);
		Services.watch().onPlaced(player, level, low.above(), upper);
		String cause = "atmosphere:door_left_open/r7/" + low.toShortString();
		BlockState lowerOpen = lower.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true);
		BlockState upperOpen = upper.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true);
		helper.assertTrue(traces.batch(level, cause).convert(low, lowerOpen).convert(low.above(), upperOpen).commit(), "the card's open was refused");
		List<TraceLedger.Entry> mine = entries(server, cause);
		// The player closes it, and some days later opens it again, by hand.
		level.setBlock(low, lower, Block.UPDATE_CLIENTS);
		level.setBlock(low.above(), upper, Block.UPDATE_CLIENTS);
		level.setBlock(low, lowerOpen, Block.UPDATE_CLIENTS);
		level.setBlock(low.above(), upperOpen, Block.UPDATE_CLIENTS);
		for (int i = mine.size() - 1; i >= 0; i--) {
			Undo.undo(server, mine.get(i), traces);
		}
		boolean open = level.getBlockState(low).getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
		entries(server, cause).forEach(TraceLedger.get(server)::remove);
		helper.assertTrue(open, "Ending D closed the door the player opened again: " + level.getBlockState(low));
		helper.succeed();
	}

	/**
	 * Rule 1 (ARCHITECTURE, "Self-created layer"): nothing writes outside the world's own folder, and "a folder that
	 * resolves outside it through a link is refused". {@code writeInWorld} creates the target's folders first and checks
	 * after, so a link inside the world folder gets a new folder made outside it before the write is refused.
	 */
	@GameTest
	public void writingInTheWorldNeverMakesAFolderOutsideIt(GameTestHelper helper) {
		Path root = null;
		try {
			root = Files.createTempDirectory("a1016-r7");
			Path world = Files.createDirectory(root.resolve("world"));
			Path outside = Files.createDirectory(root.resolve("outside"));
			Files.createSymbolicLink(world.resolve("data"), outside);
			boolean wrote = SelfCreated.writeInWorld(world, "data/a1016_02/sc14.dat", new byte[] { 1, 2, 3 });
			boolean madeOutside = Files.exists(outside.resolve("a1016_02"));
			helper.assertFalse(wrote, "a write through a link out of the world's folder was let through");
			helper.assertFalse(madeOutside, "a folder was made outside the world's folder: " + outside.resolve("a1016_02"));
		} catch (IOException e) {
			helper.assertTrue(false, "setup: " + e);
		} finally {
			delete(root);
		}
		helper.succeed();
	}

	private static void delete(Path root) {
		if (root == null) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// a test's own temporary files
				}
			});
		} catch (IOException ignored) {
			// a test's own temporary files
		}
	}
}
