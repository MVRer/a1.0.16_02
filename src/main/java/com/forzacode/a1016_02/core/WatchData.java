package com.forzacode.a1016_02.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * {@link PlayerWatch}'s persistent part: per-dimension footprint (blocks players placed and dug) and the
 * last in-game day each chunk was visited. Stored as {@code data/a1016_02/watch.dat}.
 */
final class WatchData extends SavedData {
	/** Block positions with insertion order (for the size cap) and a per-chunk index (for "near" queries). */
	static final class PosIndex {
		private final LongLinkedOpenHashSet order = new LongLinkedOpenHashSet();
		private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();

		boolean contains(BlockPos pos) {
			return order.contains(pos.asLong());
		}

		boolean add(long pos, int cap) {
			if (!order.add(pos)) {
				return false;
			}
			byChunk.computeIfAbsent(chunkKey(pos), k -> new LongOpenHashSet()).add(pos);
			while (order.size() > cap) {
				removeIndexed(order.removeFirstLong());
			}
			return true;
		}

		boolean remove(long pos) {
			if (!order.remove(pos)) {
				return false;
			}
			removeIndexed(pos);
			return true;
		}

		/** Visits every stored position within {@code radius} blocks (cube) of {@code center}. */
		void forEachNear(BlockPos center, int radius, LongConsumer action) {
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
						if (Math.abs(BlockPos.getX(packed) - center.getX()) <= radius
								&& Math.abs(BlockPos.getY(packed) - center.getY()) <= radius
								&& Math.abs(BlockPos.getZ(packed) - center.getZ()) <= radius) {
							action.accept(packed);
						}
					});
				}
			}
		}

		long[] toArray() {
			return order.toLongArray();
		}

		private void removeIndexed(long pos) {
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

	static final class Footprint {
		final PosIndex placed = new PosIndex();
		final PosIndex dug = new PosIndex();
		final Long2IntOpenHashMap visits = new Long2IntOpenHashMap();

		Footprint() {
			visits.defaultReturnValue(-1);
		}
	}

	private record Packed(long[] placed, long[] dug, long[] visitChunks, int[] visitDays) {
		private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);
		private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(java.util.stream.IntStream::toArray, Arrays::stream);
		static final Codec<Packed> CODEC = RecordCodecBuilder.create(i -> i.group(
				LONGS.optionalFieldOf("placed", new long[0]).forGetter(Packed::placed),
				LONGS.optionalFieldOf("dug", new long[0]).forGetter(Packed::dug),
				LONGS.optionalFieldOf("visitChunks", new long[0]).forGetter(Packed::visitChunks),
				INTS.optionalFieldOf("visitDays", new int[0]).forGetter(Packed::visitDays)
		).apply(i, Packed::new));
	}

	static final Codec<WatchData> CODEC = Codec.unboundedMap(Level.RESOURCE_KEY_CODEC, Packed.CODEC).fieldOf("dimensions").codec()
			.xmap(WatchData::unpack, WatchData::pack);
	static final SavedDataType<WatchData> TYPE = new SavedDataType<>(A1016_02.id("watch"), WatchData::new, CODEC, null);

	private final Map<ResourceKey<Level>, Footprint> dimensions = new HashMap<>();

	WatchData() {
	}

	static WatchData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	Footprint footprint(ResourceKey<Level> dimension) {
		return dimensions.computeIfAbsent(dimension, k -> new Footprint());
	}

	private static WatchData unpack(Map<ResourceKey<Level>, Packed> packed) {
		WatchData data = new WatchData();
		packed.forEach((dimension, p) -> {
			Footprint footprint = data.footprint(dimension);
			for (long pos : p.placed()) {
				footprint.placed.add(pos, Integer.MAX_VALUE);
			}
			for (long pos : p.dug()) {
				footprint.dug.add(pos, Integer.MAX_VALUE);
			}
			for (int n = 0; n < Math.min(p.visitChunks().length, p.visitDays().length); n++) {
				footprint.visits.put(p.visitChunks()[n], p.visitDays()[n]);
			}
		});
		return data;
	}

	private Map<ResourceKey<Level>, Packed> pack() {
		Map<ResourceKey<Level>, Packed> packed = new HashMap<>();
		dimensions.forEach((dimension, f) -> packed.put(dimension, new Packed(f.placed.toArray(), f.dug.toArray(),
				f.visits.keySet().toLongArray(), f.visits.values().toIntArray())));
		return packed;
	}
}
