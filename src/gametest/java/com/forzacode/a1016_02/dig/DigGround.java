package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block of solid stone far away from the game-test grid (each test passes its own slot), so a network or a tunnel
 * has room to grow. Player digs go through a mock player's {@code destroyBlock} (the real break event, so
 * {@code PlayerWatch} records them); placements go through {@code PlayerWatch.onPlaced}.
 */
final class DigGround {
	final GameTestHelper helper;
	final ServerLevel level;
	final BlockPos origin;
	final int sizeX;
	final int sizeY;
	final int sizeZ;
	final ServerPlayer mock;
	final List<BlockPos> dug = new ArrayList<>();
	final List<BlockPos> placed = new ArrayList<>();

	private DigGround(GameTestHelper helper, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
		this.helper = helper;
		this.level = helper.getLevel();
		this.origin = origin;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.mock = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	/** Fills a {@code sizeX * sizeY * sizeZ} box with {@code block}, thousands of blocks away from every other test. */
	static DigGround of(GameTestHelper helper, int slot, int sizeX, int sizeY, int sizeZ, Block block) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(6000 + slot * 512, 60, 3000);
		DigGround ground = new DigGround(helper, origin, sizeX, sizeY, sizeZ);
		BlockState state = block.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < sizeX; x++) {
			for (int y = 0; y < sizeY; y++) {
				for (int z = 0; z < sizeZ; z++) {
					ground.level.setBlock(pos.setWithOffset(origin, x, y, z), state, Block.UPDATE_CLIENTS);
				}
			}
		}
		ground.mock.setPos(origin.getX() + sizeX / 2.0, origin.getY() + sizeY + 40, origin.getZ() + sizeZ / 2.0);
		return ground;
	}

	BlockPos at(int x, int y, int z) {
		return origin.offset(x, y, z);
	}

	/** The player digs this block out (recorded as dug by {@code PlayerWatch}). */
	void dig(BlockPos pos) {
		helper.assertTrue(!level.getBlockState(pos).isAir(), "nothing to dig at " + pos);
		helper.assertTrue(mock.gameMode.destroyBlock(pos), "the mock player could not dig " + pos);
		helper.assertTrue(Services.watch().wasDugByPlayer(level, pos), "dig not recorded at " + pos);
		dug.add(pos.immutable());
	}

	void digBox(int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					dig(at(x, y, z));
				}
			}
		}
	}

	/** The player places this block (recorded as placed by {@code PlayerWatch}). */
	void place(BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
		Services.watch().onPlaced(mock, level, pos, state);
		placed.add(pos.immutable());
	}

	/** Natural air (a cave), not a player space. */
	void hollow(int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					level.setBlock(at(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** Puts the mock player at a spot (it is not in the level, so it never counts as a viewer). */
	void stand(BlockPos pos) {
		mock.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}
}
