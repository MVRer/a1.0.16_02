package com.forzacode.a1016_02.dig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/** "Your trees stripped": finds the tree that grew from a sapling the player planted and removes its leaves. */
public final class TreeStripper {
	public static final String CAUSE = "dig:trees_stripped";
	private static final int MAX_LOGS = 96;
	private static final int MAX_LEAVES = 500;
	private static final int REACH = 7;

	/** A grown tree: its logs (kept) and its natural leaves (removed). */
	public record Tree(BlockPos root, List<BlockPos> logs, List<BlockPos> leaves) {
	}

	private TreeStripper() {
	}

	/** The tree whose trunk stands where a sapling was planted, or null if it has not grown (or is gone). */
	public static @Nullable Tree find(ServerLevel level, BlockPos root) {
		if (!level.isLoaded(root) || !level.getBlockState(root).is(BlockTags.LOGS)) {
			return null;
		}
		List<BlockPos> logs = new ArrayList<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(root);
		seen.add(root.asLong());
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos pos = queue.poll();
			logs.add(pos);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = pos.offset(dx, dy, dz);
						if (inReach(root, n) && seen.add(n.asLong()) && level.isLoaded(n) && level.getBlockState(n).is(BlockTags.LOGS)) {
							queue.add(n);
						}
					}
				}
			}
		}
		List<BlockPos> leaves = new ArrayList<>();
		LongOpenHashSet seenLeaves = new LongOpenHashSet();
		for (BlockPos log : logs) {
			for (Direction dir : Direction.values()) {
				BlockPos n = log.relative(dir);
				if (seenLeaves.add(n.asLong()) && isNaturalLeaves(level, n) && inReach(root, n)) {
					queue.add(n);
				}
			}
		}
		while (!queue.isEmpty() && leaves.size() < MAX_LEAVES) {
			BlockPos pos = queue.poll();
			leaves.add(pos);
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (seenLeaves.add(n.asLong()) && inReach(root, n) && isNaturalLeaves(level, n)) {
					queue.add(n);
				}
			}
		}
		return new Tree(root, logs, leaves);
	}

	/** Removes every leaf of the tree in one out-of-view batch. False (nothing changed) if any of it is in view. */
	public static boolean strip(ServerLevel level, Tree tree, TraceService traces) {
		if (tree.leaves().isEmpty()) {
			return false;
		}
		TraceBatch batch = traces.batch(level, CAUSE);
		tree.leaves().forEach(batch::remove);
		return batch.commit();
	}

	private static boolean inReach(BlockPos root, BlockPos pos) {
		return Math.abs(pos.getX() - root.getX()) <= REACH && Math.abs(pos.getZ() - root.getZ()) <= REACH && pos.getY() >= root.getY() - 1
				&& pos.getY() <= root.getY() + 40;
	}

	private static boolean isNaturalLeaves(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.LEAVES) && !state.getValueOrElse(LeavesBlock.PERSISTENT, false);
	}
}
