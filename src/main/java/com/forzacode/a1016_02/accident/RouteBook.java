package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Where the subject actually walks: the cells their feet passed through, sampled by {@link RouteSampler}, with how
 * many separate passes, the first and last in-game day, and what kind of place it was. Capped per dimension (the
 * least recently walked spots go first). Part of {@link AccidentData}.
 */
public final class RouteBook {
	/** The player was climbing. */
	public static final int LADDER = 1;
	/** No sky above: a mine or a cave. */
	public static final int UNDER = 2;
	/** Feet in water. */
	public static final int WATER = 4;
	/** Standing on a block a player placed (a bridge, a floor, a pillar). */
	public static final int ON_PLACED = 8;

	/** One walked cell. */
	public static final class Point {
		public int passes;
		public int firstDay;
		public int lastDay;
		public int flags;

		Point(int passes, int firstDay, int lastDay, int flags) {
			this.passes = passes;
			this.firstDay = firstDay;
			this.lastDay = lastDay;
			this.flags = flags;
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	/** A walked cell with its position, for queries. */
	public record Spot(BlockPos pos, Point point) {
	}

	static final class Dimension {
		final Long2ObjectLinkedOpenHashMap<Point> points = new Long2ObjectLinkedOpenHashMap<>();
		final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();
		/** Play tick of the last sample per cell; not saved (a restart starts a new pass). */
		final Long2LongOpenHashMap lastTick = new Long2LongOpenHashMap();

		void index(long pos) {
			byChunk.computeIfAbsent(chunkKey(pos), k -> new LongOpenHashSet()).add(pos);
		}

		void unindex(long pos) {
			long key = chunkKey(pos);
			LongOpenHashSet set = byChunk.get(key);
			if (set != null && set.remove(pos) && set.isEmpty()) {
				byChunk.remove(key);
			}
			lastTick.remove(pos);
		}

		private static long chunkKey(long pos) {
			return ChunkPos.pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
		}
	}

	private record Packed(long[] pos, int[] passes, int[] firstDay, int[] lastDay, int[] flags) {
		private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);
		private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
		static final Codec<Packed> CODEC = RecordCodecBuilder.create(i -> i.group(
				LONGS.optionalFieldOf("pos", new long[0]).forGetter(Packed::pos),
				INTS.optionalFieldOf("passes", new int[0]).forGetter(Packed::passes),
				INTS.optionalFieldOf("firstDay", new int[0]).forGetter(Packed::firstDay),
				INTS.optionalFieldOf("lastDay", new int[0]).forGetter(Packed::lastDay),
				INTS.optionalFieldOf("flags", new int[0]).forGetter(Packed::flags)
		).apply(i, Packed::new));
	}

	static final Codec<RouteBook> CODEC = Codec.unboundedMap(Level.RESOURCE_KEY_CODEC, Packed.CODEC).xmap(RouteBook::unpack, RouteBook::pack);

	private final Map<ResourceKey<Level>, Dimension> dimensions = new HashMap<>();

	public RouteBook() {
	}

	/**
	 * Records the feet cell. A new pass counts when the cell was last seen more than {@code passGap} ticks ago.
	 *
	 * @return true if anything changed
	 */
	public boolean record(ResourceKey<Level> dimension, BlockPos feet, int day, int flags, long now, long passGap, int cap) {
		Dimension dim = dimensions.computeIfAbsent(dimension, k -> new Dimension());
		long key = feet.asLong();
		Point point = dim.points.getAndMoveToLast(key);
		long last = dim.lastTick.getOrDefault(key, Long.MIN_VALUE);
		dim.lastTick.put(key, now);
		if (point == null) {
			dim.points.putAndMoveToLast(key, new Point(1, day, day, flags));
			dim.index(key);
			while (dim.points.size() > cap) {
				long oldest = dim.points.firstLongKey();
				dim.points.removeFirst();
				dim.unindex(oldest);
			}
			return true;
		}
		boolean changed = (point.flags | flags) != point.flags || point.lastDay != day;
		point.flags |= flags;
		point.lastDay = day;
		if (last == Long.MIN_VALUE || now - last > passGap) {
			point.passes++;
			changed = true;
		}
		return changed;
	}

	/** The walked cell at {@code pos}, or null. */
	public Point get(ResourceKey<Level> dimension, BlockPos pos) {
		Dimension dim = dimensions.get(dimension);
		return dim == null ? null : dim.points.get(pos.asLong());
	}

	public boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
		return get(dimension, pos) != null;
	}

	/** Walked cells within {@code radius} (cube) of {@code center}, nearest first, at most {@code max}. */
	public List<Spot> near(ResourceKey<Level> dimension, BlockPos center, int radius, int max) {
		Dimension dim = dimensions.get(dimension);
		List<Spot> found = new ArrayList<>();
		if (dim == null) {
			return found;
		}
		int minCx = (center.getX() - radius) >> 4;
		int maxCx = (center.getX() + radius) >> 4;
		int minCz = (center.getZ() - radius) >> 4;
		int maxCz = (center.getZ() + radius) >> 4;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				LongOpenHashSet set = dim.byChunk.get(ChunkPos.pack(cx, cz));
				if (set == null) {
					continue;
				}
				set.forEach((long packed) -> {
					if (Math.abs(BlockPos.getX(packed) - center.getX()) <= radius && Math.abs(BlockPos.getY(packed) - center.getY()) <= radius
							&& Math.abs(BlockPos.getZ(packed) - center.getZ()) <= radius) {
						found.add(new Spot(BlockPos.of(packed), dim.points.get(packed)));
					}
				});
			}
		}
		found.sort(Comparator.comparingDouble(s -> s.pos().distSqr(center)));
		return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
	}

	/** How many cells are recorded in a dimension. */
	public int size(ResourceKey<Level> dimension) {
		Dimension dim = dimensions.get(dimension);
		return dim == null ? 0 : dim.points.size();
	}

	private static RouteBook unpack(Map<ResourceKey<Level>, Packed> packed) {
		RouteBook book = new RouteBook();
		packed.forEach((dimension, p) -> {
			Dimension dim = book.dimensions.computeIfAbsent(dimension, k -> new Dimension());
			int n = Math.min(p.pos().length, Math.min(p.passes().length, Math.min(p.firstDay().length, Math.min(p.lastDay().length, p.flags().length))));
			for (int i = 0; i < n; i++) {
				dim.points.putAndMoveToLast(p.pos()[i], new Point(p.passes()[i], p.firstDay()[i], p.lastDay()[i], p.flags()[i]));
				dim.index(p.pos()[i]);
			}
		});
		return book;
	}

	private Map<ResourceKey<Level>, Packed> pack() {
		Map<ResourceKey<Level>, Packed> packed = new HashMap<>();
		dimensions.forEach((dimension, dim) -> {
			int n = dim.points.size();
			long[] pos = new long[n];
			int[] passes = new int[n];
			int[] first = new int[n];
			int[] last = new int[n];
			int[] flags = new int[n];
			int i = 0;
			for (Long2ObjectMap.Entry<Point> e : dim.points.long2ObjectEntrySet()) {
				pos[i] = e.getLongKey();
				passes[i] = e.getValue().passes;
				first[i] = e.getValue().firstDay;
				last[i] = e.getValue().lastDay;
				flags[i] = e.getValue().flags;
				i++;
			}
			packed.put(dimension, new Packed(pos, passes, first, last, flags));
		});
		return packed;
	}
}
