package com.forzacode.a1016_02.lore;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the lore workstream, already registered in the gametest fabric.mod.json. Text and stages are in
 * {@link LoreTextTests}, placement in {@link LorePlacementTests}, the telling in {@link LoreTellingTests}; reading and
 * triggers here.
 */
public class LoreGameTests extends LoreTellingTests {
	@GameTest
	public void readingASignNeedsToBeCloseAndFacingIt(GameTestHelper helper) {
		helper.setBlock(new BlockPos(3, 1, 4), Placers.standingSign(Direction.NORTH));
		BlockPos sign = helper.absolutePos(new BlockPos(3, 1, 4));
		LoreConfig config = new LoreConfig();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);

		player.snapTo(helper.absoluteVec(new Vec3(3.5, 0.0, 1.5)), 0.0F, 5.0F);
		helper.assertTrue(ReadWatcher.looksAt(player, sign, config), "facing the sign from 3 blocks does not read it");
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 0.0, 1.5)), 180.0F, 0.0F);
		helper.assertFalse(ReadWatcher.looksAt(player, sign, config), "a sign behind the player was read");
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 0.0, -4.5)), 0.0F, 10.0F);
		helper.assertFalse(ReadWatcher.looksAt(player, sign, config), "a sign 8 blocks away was read");
		helper.setBlock(3, 1, 3, Blocks.STONE);
		helper.setBlock(3, 2, 3, Blocks.STONE);
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 0.0, 1.5)), 0.0F, 5.0F);
		helper.assertFalse(ReadWatcher.looksAt(player, sign, config), "a sign behind a wall was read");
		helper.succeed();
	}

	@GameTest
	public void triggersFindTheListAndTheRulesBook(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		helper.assertFalse(LoreTriggers.carries(player, "F06"), "carrying the list before having it");
		player.getInventory().add(FragmentItems.book(fragment("F06"), NAME));
		helper.assertTrue(LoreTriggers.carries(player, "F06"), "carrying the list is not detected");

		helper.setBlock(5, 1, 5, Blocks.CHEST);
		BlockPos chest = helper.absolutePos(new BlockPos(5, 1, 5));
		BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.assertFalse(LoreTriggers.storedNear(helper.getLevel(), base, 8, "F15"), "the rules book found before it is stored");
		((Container) helper.getLevel().getBlockEntity(chest)).setItem(3, FragmentItems.book(fragment("F15"), NAME));
		helper.assertTrue(LoreTriggers.storedNear(helper.getLevel(), base, 8, "F15"), "the rules book in a chest near the base is not found");
		helper.assertFalse(LoreTriggers.storedNear(helper.getLevel(), base, 2, "F15"), "the rules book found outside the radius");
		helper.succeed();
	}
}
