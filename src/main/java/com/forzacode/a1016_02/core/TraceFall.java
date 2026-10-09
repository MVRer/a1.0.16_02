package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * What falls, by the game's own rules, when {@link TraceService#removeLettingFall} takes a block out: the gravel or
 * sand column resting on it, or the stalactite (pointed dripstone) hanging from it. Planned before anything changes
 * so vetoes and the safety checks see the whole fall: the cells that fall, every cell they pass through and where
 * they come to rest. The fall itself may be seen (D-038): the game does it (a block tick on the lowest falling block).
 *
 * <p>Sand and gravel land as blocks; the plan refuses any fall where one would break into an item instead (it would
 * come to rest in a torch, a rail, a flower, on a slab or a carpet) or would fall out of the world. A stalactite
 * always shatters where it lands, as in the game, hurting whatever stands there.
 *
 * @param falling cells whose block falls (empty while falling), lowest first
 * @param landing where sand and gravel come to rest, with the block that ends up there
 * @param path    every other cell the falling blocks pass through, including where a stalactite shatters
 * @param start   the cell whose block tick starts the fall, and its block
 */
record TraceFall(Set<BlockPos> falling, Map<BlockPos, BlockState> landing, Set<BlockPos> path, @Nullable BlockPos start,
		@Nullable Block startBlock) {
	/** Nothing rests on or hangs from the block: a plain removal. */
	static final TraceFall NONE = new TraceFall(Set.of(), Map.of(), Set.of(), null, null);
	/** The longest column or stalactite that may fall. */
	static final int MAX_FALLING = 64;
	/** Ticks before the first falling block starts, as when the game notices a missing support. */
	static final int START_DELAY = 2;

	boolean isEmpty() {
		return falling.isEmpty();
	}

	/**
	 * Plans what falls when the block at {@code pos} goes. Null (refuse) if something would fall in a way the
	 * game breaks into an item, a falling block is not sand, red sand or gravel, both a column and a stalactite
	 * rest on the block, or the fall reaches an unloaded cell or the bottom of the world.
	 */
	static @Nullable TraceFall plan(ServerLevel level, BlockPos pos) {
		List<BlockPos> column = new ArrayList<>();
		for (BlockPos c = pos.above(); level.isLoaded(c) && level.getBlockState(c).getBlock() instanceof FallingBlock; c = c.above()) {
			BlockState state = level.getBlockState(c);
			if (!isLooseSoil(state) || level.getBlockEntity(c) != null || column.size() >= MAX_FALLING) {
				return null;
			}
			column.add(c.immutable());
		}
		List<BlockPos> stalactite = new ArrayList<>();
		for (BlockPos c = pos.below(); level.isLoaded(c) && isStalactite(level.getBlockState(c)); c = c.below()) {
			if (stalactite.size() >= MAX_FALLING) {
				return null;
			}
			stalactite.add(c.immutable());
		}
		if (!column.isEmpty() && !stalactite.isEmpty()) {
			return null;
		}
		if (!column.isEmpty()) {
			return planColumn(level, pos, column);
		}
		if (!stalactite.isEmpty()) {
			return planStalactite(level, stalactite);
		}
		return NONE;
	}

	/** The column drops into the emptied cell and below, until a block with a full-height collision box stops it. */
	private static @Nullable TraceFall planColumn(ServerLevel level, BlockPos pos, List<BlockPos> column) {
		BlockPos rest = pos;
		Set<BlockPos> path = new LinkedHashSet<>();
		path.add(pos);
		while (true) {
			BlockPos below = rest.below();
			if (below.getY() < level.getMinY() || !level.isLoaded(below)) {
				return null;
			}
			BlockState state = level.getBlockState(below);
			VoxelShape shape = state.getCollisionShape(level, below);
			if (shape.isEmpty()) {
				if (!FallingBlock.isFree(state)) {
					return null; // a torch, rail or flower: the falling block would come to rest in it and break
				}
				rest = below;
				path.add(rest);
				continue;
			}
			if (shape.max(Direction.Axis.Y) < 1.0) {
				return null; // a slab, carpet or snow: it would rest inside that cell and break
			}
			break;
		}
		Map<BlockPos, BlockState> landing = new LinkedHashMap<>();
		for (int i = 0; i < column.size(); i++) {
			landing.put(rest.above(i), level.getBlockState(column.get(i)));
		}
		path.addAll(column);
		path.removeAll(landing.keySet());
		BlockPos start = column.getFirst();
		return new TraceFall(new LinkedHashSet<>(column), landing, path, start, level.getBlockState(start).getBlock());
	}

	/** The stalactite drops until anything with a collision box stops it, and shatters there. */
	private static @Nullable TraceFall planStalactite(ServerLevel level, List<BlockPos> pieces) {
		Set<BlockPos> path = new LinkedHashSet<>();
		BlockPos rest = pieces.getLast();
		while (true) {
			BlockPos below = rest.below();
			if (below.getY() < level.getMinY() || !level.isLoaded(below)) {
				return null;
			}
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				break;
			}
			rest = below;
			path.add(rest);
		}
		path.add(rest);
		BlockPos start = pieces.getFirst();
		return new TraceFall(new LinkedHashSet<>(pieces), Map.of(), path, start, level.getBlockState(start).getBlock());
	}

	/** Sand, red sand and gravel: the falling blocks the game drops as plain blocks. */
	static boolean isLooseSoil(BlockState state) {
		return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL);
	}

	static boolean isStalactite(BlockState state) {
		return state.getBlock() instanceof SpeleothemBlock && state.getValue(SpeleothemBlock.TIP_DIRECTION) == Direction.DOWN;
	}
}
