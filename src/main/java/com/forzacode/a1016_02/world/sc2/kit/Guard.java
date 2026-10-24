package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Keeps later worldgen out of a place (SC2-H, "later worldgen"). A place fills each chunk at that chunk's last
 * decoration step, so the chunk's own trees, lakes, ores and the rest are in before it; but a neighbour decorated later
 * still writes a few blocks into it (its features reach a chunk out): leaves and grass into a pyramid's core and onto a
 * floor's square, pointed dripstone into a hollow, gravel blobs into a seal. Every cell a place's fill read or set in a
 * chunk ({@link Writer}, worldgen only) is guarded until that chunk is finished (by then every neighbour is decorated):
 * a write from another chunk's decoration into a guarded cell is refused ({@code WorldGenRegion.ensureCanWrite}, which
 * vanilla's block writes and ore blobs ask), as vanilla refuses a write out of reach. The place's own writes (its fill,
 * the line's rebuild of a neighbour) pass. A feature cut by a guarded cell keeps the rest of itself, as a tree cut by a
 * clearing does. A chunk saved half made and loaded again in a later run is no longer guarded (as before).
 */
public final class Guard {
	/** At most this many chunks guarded at once; past it the oldest go first (long finished in practice). R12-13: 4 096 before. */
	static final int MAX_CHUNKS = 2048;

	private record Key(ResourceKey<Level> dimension, long chunk) {
	}

	/**
	 * One chunk's guarded cells, a bit a cell, kept a section (16 high) at a time and only for the sections a place
	 * touches (R12-13: a whole column's bits for every chunk was up to 12 KB a chunk at the default height, 130 KB at a
	 * data pack's 4 064).
	 */
	static final class Mask {
		private final int minY;
		private final int height;
		private final long[][] sections;

		Mask(int minY, int height) {
			this.minY = minY;
			this.height = height;
			this.sections = new long[(height + 15) >> 4][];
		}

		/** Guards a cell (one writer thread a chunk: its decoration). */
		void add(int x, int y, int z) {
			int dy = y - minY;
			if (dy < 0 || dy >= height) {
				return;
			}
			long[] bits = sections[dy >> 4];
			if (bits == null) {
				bits = new long[64];
				sections[dy >> 4] = bits;
			}
			int i = (x & 15) | (z & 15) << 4 | (dy & 15) << 8;
			bits[i >> 6] |= 1L << i;
		}

		boolean has(int x, int y, int z) {
			int dy = y - minY;
			if (dy < 0 || dy >= height) {
				return false;
			}
			long[] bits = sections[dy >> 4];
			int i = (x & 15) | (z & 15) << 4 | (dy & 15) << 8;
			return bits != null && (bits[i >> 6] & 1L << i) != 0;
		}

		int sections() {
			int n = 0;
			for (long[] bits : sections) {
				n += bits == null ? 0 : 1;
			}
			return n;
		}

		int count() {
			int n = 0;
			for (long[] bits : sections) {
				if (bits != null) {
					for (long b : bits) {
						n += Long.bitCount(b);
					}
				}
			}
			return n;
		}
	}

	private static final Map<Key, Mask> MASKS = new ConcurrentHashMap<>();
	/** The guarded chunks, oldest first (guarded by itself). */
	private static final Set<Key> ORDER = new LinkedHashSet<>();
	/** How deep this thread is in a place's own write (its writes pass). */
	private static final ThreadLocal<int[]> OWN = ThreadLocal.withInitial(() -> new int[1]);

	private Guard() {
	}

	/** The mask of this chunk, made if new (a place's fill in worldgen). */
	static Mask of(ResourceKey<Level> dimension, ChunkPos chunk, int minY, int height) {
		Key key = new Key(dimension, chunk.pack());
		Mask mask = MASKS.get(key);
		if (mask != null) {
			return mask;
		}
		Mask made = new Mask(minY, height);
		mask = MASKS.putIfAbsent(key, made);
		if (mask == null) {
			synchronized (ORDER) {
				ORDER.add(key);
				Iterator<Key> oldest = ORDER.iterator();
				while (ORDER.size() > MAX_CHUNKS && oldest.hasNext()) {
					MASKS.remove(oldest.next());
					oldest.remove();
				}
			}
			return made;
		}
		return mask;
	}

	/** A place's own write begins on this thread (it passes the guard until {@link #exit}). */
	static void enter() {
		OWN.get()[0]++;
	}

	static void exit() {
		OWN.get()[0]--;
	}

	static boolean owning() {
		return OWN.get()[0] > 0;
	}

	/**
	 * True if this region's decoration may not write here: a cell of another chunk a place guards. Asked by
	 * {@code WorldGenRegion.ensureCanWrite} for every worldgen write (the centre chunk's own and an empty guard cost two
	 * comparisons).
	 */
	public static boolean refuses(WorldGenRegion region, BlockPos pos) {
		if (MASKS.isEmpty()) {
			return false;
		}
		return refuses(region.getLevel().dimension(), region.getCenter(), pos);
	}

	/** {@link #refuses(WorldGenRegion, BlockPos)} for a decoration centred on {@code center} in {@code dimension}. */
	public static boolean refuses(ResourceKey<Level> dimension, ChunkPos center, BlockPos pos) {
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		if (cx == center.x() && cz == center.z()) {
			return false;
		}
		// R12-13: the thread's own depth is asked only for a guarded cell (most writes meet no mask at all).
		Mask mask = MASKS.get(new Key(dimension, ChunkPos.pack(cx, cz)));
		return mask != null && mask.has(pos.getX(), pos.getY(), pos.getZ()) && !owning();
	}

	/** Tests: guards one cell as a place's fill would. */
	public static void guard(ResourceKey<Level> dimension, BlockPos pos, int minY, int height) {
		of(dimension, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), minY, height).add(pos.getX(), pos.getY(), pos.getZ());
	}

	/** Tests: runs {@code write} as a place's own write (it passes). */
	public static void asOwn(Runnable write) {
		enter();
		try {
			write.run();
		} finally {
			exit();
		}
	}

	/** A chunk is finished (every neighbour decorated): its guard goes. Any thread. */
	public static void finished(ResourceKey<Level> dimension, ChunkPos chunk) {
		if (MASKS.isEmpty()) {
			return;
		}
		Key key = new Key(dimension, chunk.pack());
		if (MASKS.remove(key) != null) {
			synchronized (ORDER) {
				ORDER.remove(key);
			}
		}
	}

	/** How many cells of this chunk are guarded now (tests), 0 if none. */
	public static int guarded(ResourceKey<Level> dimension, ChunkPos chunk) {
		Mask mask = MASKS.get(new Key(dimension, chunk.pack()));
		return mask == null ? 0 : mask.count();
	}

	/** How many 16-high sections of this chunk's mask hold bits now (tests, R12-13), 0 if none. */
	public static int sections(ResourceKey<Level> dimension, ChunkPos chunk) {
		Mask mask = MASKS.get(new Key(dimension, chunk.pack()));
		return mask == null ? 0 : mask.sections();
	}

	/** True if this cell is guarded now (tests). */
	public static boolean guards(ResourceKey<Level> dimension, BlockPos pos) {
		Mask mask = MASKS.get(new Key(dimension, ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4)));
		return mask != null && mask.has(pos.getX(), pos.getY(), pos.getZ());
	}

	/** Server stop. */
	public static void clear() {
		synchronized (ORDER) {
			MASKS.clear();
			ORDER.clear();
		}
	}
}
