package com.forzacode.a1016_02.dig;

import java.util.function.LongConsumer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Block positions with a per-chunk index for "near" queries and an insertion order for a size cap (oldest dropped). */
public final class PosSet {
	private final LongLinkedOpenHashSet order = new LongLinkedOpenHashSet();
	private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();

	public boolean add(BlockPos pos, int cap) {
		return add(pos.asLong(), cap);
	}

	public boolean add(long pos, int cap) {
		if (!order.add(pos)) {
			return false;
		}
		byChunk.computeIfAbsent(chunkKey(pos), k -> new LongOpenHashSet()).add(pos);
		while (order.size() > cap) {
			unindex(order.removeFirstLong());
		}
		return true;
	}

	public boolean remove(long pos) {
		if (!order.remove(pos)) {
			return false;
		}
		unindex(pos);
		return true;
	}

	public boolean contains(BlockPos pos) {
		return order.contains(pos.asLong());
	}

	public int size() {
		return order.size();
	}

	public boolean isEmpty() {
		return order.isEmpty();
	}

	/** Visits every position within {@code radius} (cube) of {@code center}. */
	public void forEachNear(BlockPos center, int radius, LongConsumer action) {
		int minCx = (center.getX() - radius) >> 4;
		int maxCx = (center.getX() + radius) >> 4;
		int minCz = (center.getZ() - radius) >> 4;
		int maxCz = (center.getZ() + radius) >> 4;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				LongOpenHashSet set = byChunk.get(ChunkPos.pack(cx, cz));
				if (set == null) {
					continue;
				}
				set.forEach((long packed) -> {
					if (Math.abs(BlockPos.getX(packed) - center.getX()) <= radius && Math.abs(BlockPos.getY(packed) - center.getY()) <= radius
							&& Math.abs(BlockPos.getZ(packed) - center.getZ()) <= radius) {
						action.accept(packed);
					}
				});
			}
		}
	}

	/** True if any position is within {@code radius} (cube) of {@code center}. */
	public boolean anyWithin(BlockPos center, int radius) {
		boolean[] found = {false};
		forEachNear(center, radius, packed -> found[0] = true);
		return found[0];
	}

	public long[] toArray() {
		return order.toLongArray();
	}

	public void forEach(LongConsumer action) {
		order.forEach(action);
	}

	private void unindex(long pos) {
		long key = chunkKey(pos);
		LongOpenHashSet set = byChunk.get(key);
		if (set != null && set.remove(pos) && set.isEmpty()) {
			byChunk.remove(key);
		}
	}

	private static long chunkKey(long pos) {
		return ChunkPos.pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
	}
}
