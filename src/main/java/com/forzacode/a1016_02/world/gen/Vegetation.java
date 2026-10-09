package com.forzacode.a1016_02.world.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** What counts as leaves, trees, plants and bare ground for the dead mountain and the bare forest. */
public final class Vegetation {
	private Vegetation() {
	}

	/** Everything a bare forest loses: leaves and what hangs from them or lies under them. */
	public static boolean isLeafy(BlockState state) {
		return state.is(BlockTags.LEAVES) || state.is(Blocks.VINE) || state.is(Blocks.LEAF_LITTER) || state.is(Blocks.PALE_HANGING_MOSS)
				|| state.is(Blocks.MANGROVE_PROPAGULE);
	}

	/** Natural tree parts: trunks, leaves, huge mushrooms and what grows on them. */
	public static boolean isTreePart(BlockState state) {
		return state.is(BlockTags.OVERWORLD_NATURAL_LOGS) || isLeafy(state) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
				|| state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.COCOA) || state.is(Blocks.BEE_NEST) || state.is(Blocks.SHELF_MUSHROOM)
				|| state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.CREAKING_HEART);
	}

	public static boolean isLog(BlockState state) {
		return state.is(BlockTags.OVERWORLD_NATURAL_LOGS);
	}

	/** Small plants: grass, flowers, saplings, bushes, mushrooms, carpets. Never a fluid source block. */
	public static boolean isPlant(BlockState state) {
		if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)) {
			return false;
		}
		return state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
				|| state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.SWEET_BERRY_BUSH)
				|| state.is(Blocks.RED_SHRUB) || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
				|| state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.CACTUS)
				|| state.is(Blocks.CACTUS_FLOWER) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN);
	}

	/** Everything that dies on a dead mountain. */
	public static boolean dies(BlockState state) {
		return isTreePart(state) || isPlant(state);
	}

	/** Grass-like ground that turns to plain dirt. */
	public static boolean isGrassGround(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK);
	}

	/** Snow layers ride on whatever is below them. */
	public static boolean isSnowLayer(BlockState state) {
		return state.is(Blocks.SNOW);
	}

	/**
	 * Y of the ground in a column: the first block below {@code topY} that is not air, a plant, a tree part or a
	 * snow layer. Water counts as ground (the scan stops there). Returns {@code minY - 1} if nothing is found.
	 */
	public static int groundY(BlockGetter level, int x, int z, int topY, int minY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, topY, z);
		for (int y = topY; y >= minY; y--) {
			pos.setY(y);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || isSnowLayer(state) || dies(state)) {
				continue;
			}
			return y;
		}
		return minY - 1;
	}
}
