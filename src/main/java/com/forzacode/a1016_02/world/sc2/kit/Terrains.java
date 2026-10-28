package com.forzacode.a1016_02.world.sc2.kit;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.LiveTerrain;

import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * The two terrains a {@link Shape} is built over: the noise in worldgen (and for rebuilding a recorded place's shape),
 * the level's blocks as they were before the shape in the gallery and tests ({@link Frozen}). Both answer
 * {@link Terrain#ground} as the top solid block (under water, plants and trees) and {@link Terrain#surface} counting
 * water.
 */
public final class Terrains {
	private Terrains() {
	}

	/** Tests and the dev survey only: this thread's time in the noise's reads (ns), while {@link #timing} is on. */
	private static final ThreadLocal<long[]> SPENT = ThreadLocal.withInitial(() -> new long[4]);
	private static volatile boolean timing;

	/** Tests and the dev survey only: counts the time this thread spends reading the noise (off by default). */
	public static void timing(boolean on) {
		timing = on;
	}

	/** Tests and the dev survey only: this thread's time in the noise's reads so far (ns), while {@link #timing} was on. */
	public static long spent() {
		return SPENT.get()[0];
	}

	/** Tests and the dev survey only: this thread's reads so far while {@link #timing} was on (columns, 4 x 4s, chunks). */
	public static long[] reads() {
		long[] s = SPENT.get();
		return new long[] {s[1], s[2], s[3]};
	}

	/**
	 * A column's ground ({@code surface} false) or surface for a {@link Writer} filling {@code clipColumns} columns of this
	 * column's chunk: over the noise, a chunk the writer fills half of or more is read whole at its first unknown column
	 * (the fill will ask most of them); any other terrain answers as it does.
	 */
	static int height(Terrain terrain, int x, int z, boolean surface, int clipColumns) {
		if (terrain instanceof Noise noise) {
			return noise.height(x, z, surface ? 256 : 0, surface ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG, clipColumns >= 128);
		}
		return surface ? terrain.surface(x, z) : terrain.ground(x, z);
	}

	/** The noise of this generator (thread-safe; nothing is loaded or generated). */
	public static Terrain noise(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height) {
		return new Noise(generator, randomState, height.getMinY(), height.getMaxY(), height);
	}

	/** The noise of this level's generator. */
	public static Terrain noise(ServerLevel level) {
		return noise(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level);
	}

	/**
	 * What two terrains are the same land by (the noise of one generator and random state at one height), or null for a
	 * terrain that is only itself (the gallery's and tests'): a cache of shapes built over the noise may key on it.
	 */
	public static @Nullable Object identity(Terrain terrain) {
		return terrain instanceof Noise noise ? noise.owner : null;
	}

	/**
	 * The noise a terrain reads, for a kind that reads the noise's density itself (R3-3: the hollow's land top a volume at
	 * a time), or null for a terrain that is not the noise (the gallery's and tests' own, the live level).
	 *
	 * @param minY   the noise's lowest block in this level
	 * @param height the noise's height in this level (as the generator clamps it)
	 */
	public record NoiseLand(NoiseBasedChunkGenerator generator, RandomState random, int minY, int height) {
	}

	/** {@link NoiseLand} of this terrain, or null. */
	public static @Nullable NoiseLand noiseLand(Terrain terrain) {
		return terrain instanceof Noise noise && noise.noise != null ? new NoiseLand(noise.noise, noise.randomState, noise.noiseMinY, noise.noiseHeight) : null;
	}

	/** The level's blocks, each column remembered the first time it is asked (gallery and tests only, server thread). */
	public static Frozen frozen(ServerLevel level) {
		return new Frozen(level);
	}

	/**
	 * Terrain from the noise: the same answer on every thread and for every chunk, generated or not. A column is read as
	 * the generator reads it ({@code getBaseHeight}, {@code getBaseColumn}); a chunk asked about {@value #DENSE_AFTER}
	 * times on one thread (a shape filling it reads every column) is read whole at once, one volume of the noise
	 * ({@link NoiseVolume}: the same blocks, a tenth of the cost a column), and answered from that.
	 */
	private static final class Noise implements Terrain {
		/** A chunk asked about this often on a thread is read whole. */
		static final int DENSE_AFTER = 24;
		/** Chunks kept read whole per thread. */
		static final int DENSE_KEPT = 4;

		private record Column(int x, int z, NoiseColumn column) {
		}

		/** A chunk read whole: each column's ground and surface (as {@code getBaseHeight} - 1) and its blocks. */
		private record Dense(int[] ground, int[] surface, BlockState[] blocks) {
		}

		/** The land a noise terrain reads: its generator, random state and height (equal for every terrain of one level). */
		private record Owner(ChunkGenerator generator, RandomState randomState, int minY, int maxY) {
		}

		/**
		 * One thread's reads of one land: its biome resolver, the last column, the chunks read whole and how often each other
		 * chunk was asked about. One per thread for every terrain (R12-19: a terrain is made per candidate and per piece, so
		 * caches of their own lingered in the pool's threads), started again when the thread reads another land.
		 */
		private static final class Cache {
			@Nullable Owner owner;
			@Nullable BiomeResolver resolver;
			@Nullable Column last;
			final Long2ObjectLinkedOpenHashMap<Dense> dense = new Long2ObjectLinkedOpenHashMap<>();
			final Long2IntOpenHashMap asks = new Long2IntOpenHashMap();
			/** The shared heights last asked on this thread, and its chunk. */
			@Nullable Columns columns;
			long columnsKey;
		}

		/** A column not computed yet. */
		static final int UNKNOWN = Integer.MIN_VALUE;
		/**
		 * A quarter of a quarter's (4 x 4 columns) first columns computed one at a time (in CPU time a column's height costs
		 * about 0.06 ms, the 4 x 4's heights 0.6, the chunk's 5; FIX-R3-17's measure).
		 */
		static final int SINGLE = 6;
		/** A chunk with this many of its 16 quarters of quarters read is read whole. */
		static final int QUADS_WHOLE = 3;
		/** Chunks' heights kept per land (about 2 KB each), all threads together; past it the land starts again. */
		static final int LAND_KEPT = 4096;
		/** Lands kept (a level's noise, a test's); the oldest goes first. */
		static final int LANDS_KEPT = 6;

		/** One chunk's grounds (0 to 255) and surfaces (256 to 511) as far as computed, for every thread (FIX-R3-17). */
		static final class Columns {
			final java.util.concurrent.atomic.AtomicIntegerArray values = new java.util.concurrent.atomic.AtomicIntegerArray(512);
			/** The columns computed one at a time in each 4 x 4. */
			final java.util.concurrent.atomic.AtomicIntegerArray asked = new java.util.concurrent.atomic.AtomicIntegerArray(16);
			/** The 4 x 4 quarters read (a bit each). */
			final java.util.concurrent.atomic.AtomicInteger quads = new java.util.concurrent.atomic.AtomicInteger();

			Columns() {
				for (int i = 0; i < 512; i++) {
					values.set(i, UNKNOWN);
				}
			}
		}

		/** Every land's shared heights, by its owner (the last {@value #LANDS_KEPT}). */
		private static final java.util.Map<Owner, java.util.concurrent.ConcurrentHashMap<Long, Columns>> LANDS = new java.util.LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(java.util.Map.Entry<Owner, java.util.concurrent.ConcurrentHashMap<Long, Columns>> eldest) {
				return size() > LANDS_KEPT;
			}
		};

		private static java.util.concurrent.ConcurrentHashMap<Long, Columns> land(Owner owner) {
			synchronized (LANDS) {
				return LANDS.computeIfAbsent(owner, o -> new java.util.concurrent.ConcurrentHashMap<>());
			}
		}

		private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

		private final ChunkGenerator generator;
		private final RandomState randomState;
		private final int minY;
		private final int maxY;
		private final LevelHeightAccessor height;
		private final Owner owner;
		/** This land's shared heights. */
		private final java.util.concurrent.ConcurrentHashMap<Long, Columns> lands;
		private final @Nullable NoiseBasedChunkGenerator noise;
		/** The noise's own height in this level (as the generator clamps it to the level). */
		private final int noiseMinY;
		private final int noiseHeight;

		Noise(ChunkGenerator generator, RandomState randomState, int minY, int maxY, LevelHeightAccessor height) {
			this.generator = generator;
			this.randomState = randomState;
			this.minY = minY;
			this.maxY = maxY;
			this.height = LevelHeightAccessor.create(minY, height.getHeight());
			this.owner = new Owner(generator, randomState, minY, maxY);
			this.lands = land(owner);
			if (generator instanceof NoiseBasedChunkGenerator noiseBased) {
				NoiseSettings settings = noiseBased.generatorSettings().value().noiseSettings().clampToHeightAccessor(this.height);
				this.noise = settings.height() > 0 ? noiseBased : null;
				this.noiseMinY = settings.minY();
				this.noiseHeight = settings.height();
			} else {
				this.noise = null;
				this.noiseMinY = minY;
				this.noiseHeight = 0;
			}
		}

		/** This thread's reads of this terrain's land (started again if the thread last read another). */
		private Cache cache() {
			Cache c = CACHE.get();
			if (!owner.equals(c.owner)) {
				c.owner = owner;
				c.resolver = null;
				c.last = null;
				c.dense.clear();
				c.asks.clear();
				c.columns = null;
			}
			return c;
		}

		@Override
		public int seaLevel() {
			return generator.getSeaLevel();
		}

		@Override
		public int minY() {
			return minY;
		}

		/** The chunk of this column read whole, or null (asked too few times yet, or not the noise). */
		private @Nullable Dense dense(int x, int z) {
			if (noise == null) {
				return null;
			}
			Cache c = cache();
			long key = ChunkPos.pack(x >> 4, z >> 4);
			Dense d = c.dense.getAndMoveToFirst(key);
			if (d != null) {
				return d;
			}
			int asks = c.asks.addTo(key, 1) + 1;
			if (asks < DENSE_AFTER) {
				if (c.asks.size() > 4096) {
					c.asks.clear();
				}
				return null;
			}
			c.asks.remove(key);
			d = read(x & ~15, z & ~15);
			c.dense.putAndMoveToFirst(key, d);
			if (c.dense.size() > DENSE_KEPT) {
				c.dense.removeLast();
			}
			return d;
		}

		private Dense read(int x0, int z0) {
			int sy = noiseHeight;
			BlockState[] blocks = NoiseVolume.blocks(noise, randomState, x0, noiseMinY, z0, 16, sy, 16);
			int[] ground = new int[256];
			int[] surface = new int[256];
			Predicate<BlockState> solid = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
			Predicate<BlockState> filled = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
			for (int i = 0; i < 256; i++) {
				int g = minY - 1;
				int s = minY - 1;
				int base = i * sy;
				for (int y = sy - 1; y >= 0; y--) {
					BlockState state = blocks[base + y];
					if (s == minY - 1 && filled.test(state)) {
						s = noiseMinY + y;
					}
					if (solid.test(state)) {
						g = noiseMinY + y;
						break;
					}
				}
				ground[i] = g;
				surface[i] = s;
			}
			return new Dense(ground, surface, blocks);
		}

		@Override
		public int ground(int x, int z) {
			return height(x, z, 0, Heightmap.Types.OCEAN_FLOOR_WG, false);
		}

		@Override
		public int surface(int x, int z) {
			return height(x, z, 256, Heightmap.Types.WORLD_SURFACE_WG, false);
		}

		/**
		 * A column's ground ({@code at} 0) or surface (256) from the land's shared heights (FIX-R3-17): computed once for
		 * every thread and every start. In each quarter of a quarter (4 x 4 columns) the first {@value #SINGLE} as the
		 * generator reads them ({@code getBaseHeight}: a strip along a chunk's edge, as a neighbour's fill asks, stays a
		 * column at a time), then the quarter of a quarter at once; the whole chunk once {@value #QUADS_WHOLE} of those
		 * were read or when a writer fills half of it or more ({@code whole}); each read over the land only
		 * ({@link NoiseVolume#heights}). The same numbers every way (the test
		 * {@code theNoiseTerrainReadWholeAnswersAsColumnByColumn}).
		 */
		int height(int x, int z, int at, Heightmap.Types type, boolean whole) {
			if (!timing) {
				return heightNow(x, z, at, type, whole);
			}
			long t0 = System.nanoTime();
			try {
				return heightNow(x, z, at, type, whole);
			} finally {
				SPENT.get()[0] += System.nanoTime() - t0;
			}
		}

		private int heightNow(int x, int z, int at, Heightmap.Types type, boolean whole) {
			if (noise == null) {
				return generator.getBaseHeight(x, z, type, height, randomState) - 1;
			}
			Columns c = columns(x, z);
			int k = at + (x & 15) + (z & 15) * 16;
			int v = c.values.get(k);
			if (v != UNKNOWN) {
				return v;
			}
			if (whole || Integer.bitCount(c.quads.get()) >= QUADS_WHOLE) {
				count(3);
				read(c, x & ~15, z & ~15, 16);
				return c.values.get(k);
			}
			int quad = ((x & 15) >> 2) + ((z & 15) >> 2) * 4;
			if (c.asked.incrementAndGet(quad) > SINGLE) {
				count(2);
				read(c, x & ~3, z & ~3, 4);
				c.quads.getAndUpdate(q -> q | 1 << quad);
				return c.values.get(k);
			}
			count(1);
			v = generator.getBaseHeight(x, z, type, height, randomState) - 1;
			c.values.set(k, v);
			return v;
		}

		private static void count(int what) {
			if (timing) {
				SPENT.get()[what]++;
			}
		}

		/** Reads {@code side} by {@code side} columns from ({@code x0}, {@code z0}) into a chunk's heights. */
		private void read(Columns c, int x0, int z0, int side) {
			int[][] h = NoiseVolume.heights(noise, randomState, x0, z0, side, side, noiseMinY, noiseHeight);
			for (int dz = 0; dz < side; dz++) {
				for (int dx = 0; dx < side; dx++) {
					int k = ((x0 + dx) & 15) + ((z0 + dz) & 15) * 16;
					int g = h[0][dx + dz * side];
					int t = h[1][dx + dz * side];
					// None in the noise: the level's lowest less one, as getBaseHeight answers.
					c.values.set(k, g < noiseMinY ? minY - 1 : g);
					c.values.set(256 + k, t < noiseMinY ? minY - 1 : t);
				}
			}
		}

		/** The shared heights of this column's chunk (this thread's last one first). */
		private Columns columns(int x, int z) {
			Cache cache = cache();
			long key = ChunkPos.pack(x >> 4, z >> 4);
			if (cache.columns != null && cache.columnsKey == key) {
				return cache.columns;
			}
			Columns c = lands.get(key);
			if (c == null) {
				if (lands.size() > LAND_KEPT) {
					lands.clear();
				}
				c = lands.computeIfAbsent(key, k -> new Columns());
			}
			cache.columns = c;
			cache.columnsKey = key;
			return c;
		}


		@Override
		public Holder<Biome> biome(int x, int y, int z) {
			Cache c = cache();
			if (c.resolver == null) {
				c.resolver = generator.getBiomeSource().createUncachedResolver(randomState);
			}
			return c.resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		}

		@Override
		public BlockState block(int x, int y, int z) {
			if (!timing) {
				return blockNow(x, y, z);
			}
			long t0 = System.nanoTime();
			try {
				return blockNow(x, y, z);
			} finally {
				SPENT.get()[0] += System.nanoTime() - t0;
			}
		}

		private BlockState blockNow(int x, int y, int z) {
			if (y < minY || y > maxY) {
				return Blocks.AIR.defaultBlockState();
			}
			Cache c = cache();
			Column cached = c.last;
			if (cached == null || cached.x() != x || cached.z() != z) {
				Dense d = dense(x, z);
				if (d != null) {
					int index = y - noiseMinY;
					return index < 0 || index >= noiseHeight ? Blocks.AIR.defaultBlockState() : d.blocks()[index + ((x & 15) + (z & 15) * 16) * noiseHeight];
				}
				cached = new Column(x, z, generator.getBaseColumn(x, z, height, randomState));
				c.last = cached;
			}
			return cached.column().getBlock(y);
		}
	}

	/**
	 * The level's blocks as they were before a shape was placed: {@link #ground} and {@link #surface} of each column are
	 * remembered the first time they are asked, and {@link Live#place} asks for every column of the shape's box before it
	 * writes, so a column read after its chunk was written still answers what was there. Loads (generates) the chunk of a
	 * column it is asked about. {@link #block} and {@link #biome} read the level now. Gallery and tests only.
	 */
	public static final class Frozen implements Terrain {
		private final ServerLevel level;
		private final LiveTerrain live;
		private final Long2IntOpenHashMap ground = new Long2IntOpenHashMap();
		private final Long2IntOpenHashMap surface = new Long2IntOpenHashMap();

		Frozen(ServerLevel level) {
			this.level = level;
			this.live = new LiveTerrain(level);
		}

		/** Remembers every column of this box now. */
		public void freeze(BoundingBox box) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					ground(x, z);
					surface(x, z);
				}
			}
		}

		private void load(int x, int z) {
			if (!level.hasChunk(x >> 4, z >> 4)) {
				level.getChunk(x >> 4, z >> 4);
			}
		}

		@Override
		public int seaLevel() {
			return level.getSeaLevel();
		}

		@Override
		public int minY() {
			return level.getMinY();
		}

		@Override
		public int ground(int x, int z) {
			long key = ChunkPos.pack(x, z);
			if (!ground.containsKey(key)) {
				load(x, z);
				ground.put(key, live.ground(x, z));
			}
			return ground.get(key);
		}

		@Override
		public int surface(int x, int z) {
			long key = ChunkPos.pack(x, z);
			if (!surface.containsKey(key)) {
				load(x, z);
				surface.put(key, live.surface(x, z));
			}
			return surface.get(key);
		}

		@Override
		public Holder<Biome> biome(int x, int y, int z) {
			return live.biome(x, y, z);
		}

		@Override
		public BlockState block(int x, int y, int z) {
			load(x, z);
			return live.block(x, y, z);
		}
	}
}
