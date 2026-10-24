package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Heights;
import com.forzacode.a1016_02.world.sc2.kit.NoiseVolume;
import com.forzacode.a1016_02.world.sc2.kit.Overlay;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.s19.LinePlan;
import com.forzacode.a1016_02.world.sc2.s19.S19;
import com.forzacode.a1016_02.world.sc2.s19.Sc19Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * SC2-H's game tests (docs/SELF_CREATED_2.md, "Orchestrator decision ... height limits (SC2-H)"): every registered kind
 * is sited through the kit (or is a feature with its own height tests), and the kit refuses, never clips, any start whose
 * box comes within {@link Heights#BOTTOM} of the lowest block or {@link Heights#TOP} of the highest, whatever the level's
 * height: every kind a structure JSON names, found on the real overworld noise, tried again with the level's bottom and
 * top moved to its box; and on worldgen's own path over the real noise of the default world, a short one (0 to 255), a
 * raised floor, a low top, the amplified and the large-biome presets, no start of any kind leaves the height. Kinds are
 * read from the registry, so a kind added later is tested without a change here. Also: the guard against later
 * worldgen, the noise read in volumes equal to the generator's own columns, and every shape the same whatever the order
 * of its terrain's reads. With {@code -Da1016_02.sc2survey=true} a survey builds the starts of every kind over the real
 * noise of several seeds and presets into an {@link Overlay} (caves, aquifers, lava) and reports, per kind, what meets
 * the terrain badly ({@link Sc2Audit}) and what a start and a chunk cost; {@code -Da1016_02.sc2look} takes one start
 * apart; {@code -Da1016_02.sc2cost=true} prints what a column of the noise costs. Nothing here builds in the world.
 */
public class SelfCreated2HeightsTests {
	private static final String ENV = "a1016_02:world/sc2_heights";
	/** SC2-5's seed: stony and jagged peaks near (1100, -900). */
	static final long REAL_SEED = -4180345853331630785L;
	/** Kinds no structure JSON names, each a feature with its own height tests (the line: SelfCreated2LineTests). */
	static final Set<String> FEATURE_KINDS = Set.of(S19.ID);
	/** Kinds whose siting answers only a test's armed chunks (the kit's demo): no natural start to find. */
	static final Set<String> NO_NATURAL_START = Set.of("k0");
	/** Kinds open to the sky by design (the floor's square): their inside meets the open air everywhere. */
	static final Set<String> OPEN_KINDS = Set.of("s20");
	/** Where starts are looked for: SC2-5's peaks first, then land and sea all round. */
	static final int[][] CENTRES = {{1100, -900}, {0, 0}, {3200, 2600}, {-3000, 2200}, {2600, -3600}, {-2600, -3000}, {6000, 400}};

	// --- the registry ---

	/**
	 * Every registered kind is sited through the kit (a structure JSON names it, so {@link Sc2Structure#site} and its
	 * height rule decide its starts) or is a feature whose own tests hold the same heights; every structure of the kit's
	 * type names a registered kind; the line keeps the kit's margins for any level height.
	 */
	@GameTest(environment = ENV)
	public void everyKindIsSitedThroughTheKitOrIsAFeatureKeepingTheSameHeights(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		for (Sc2Kind kind : Sc2Kind.all()) {
			helper.assertTrue(structures.containsKey(kind.id()) || FEATURE_KINDS.contains(kind.id()),
					"kind " + kind.id() + " has no structure and is no known feature: nothing holds its height");
		}
		helper.assertTrue(structures.keySet().containsAll(List.of("s16", "s16d", "s17", "s18", "s20")), "the structures: " + structures.keySet());
		helper.assertTrue(LinePlan.ABOVE_MIN >= Heights.BOTTOM && LinePlan.BELOW_MAX >= Heights.TOP, "the line's margins");
		Sc19Config config = new Sc19Config();
		for (int[] h : new int[][] {{-64, 319}, {0, 255}, {-2032, 2031}, {-64, 127}, {64, 191}}) {
			for (long seed : new long[] {1L, REAL_SEED, 77L}) {
				for (LinePlan.Segment segment : LinePlan.of(seed, 7L, config, h[0], h[1])) {
					helper.assertTrue(Heights.within(segment.box(), h[0], h[1]), "a segment of the line in " + h[0] + ".." + h[1] + ": " + segment.box());
				}
			}
		}
		helper.succeed();
	}

	// --- the kit's rule ---

	/** The margins themselves, and the level's own numbers. */
	@GameTest(environment = ENV)
	public void theKitsMarginsAreTheLevelsOwnNumbers(GameTestHelper helper) {
		helper.assertTrue(Heights.within(new BoundingBox(0, -58, 0, 1, 313, 1), -64, 319), "inside");
		helper.assertFalse(Heights.within(new BoundingBox(0, -59, 0, 1, 313, 1), -64, 319), "the bottom");
		helper.assertFalse(Heights.within(new BoundingBox(0, -58, 0, 1, 314, 1), -64, 319), "the top");
		helper.assertTrue(Heights.within(new BoundingBox(0, 6, 0, 1, 249, 1), 0, 255) && !Heights.within(new BoundingBox(0, 5, 0, 1, 249, 1), 0, 255),
				"a short world");
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World normal = Sc2RealNoise.normal(level, REAL_SEED);
		helper.assertTrue(Heights.lowest(normal.height(), normal.generator()) == -64 && Heights.highest(normal.height()) == 319, "the default world");
		Sc2RealNoise.World raised = normal.withHeight(level, 0, 320);
		helper.assertTrue(Heights.lowest(raised.height(), raised.generator()) == 0, "a level starting at 0");
		// A level deeper than its noise: nothing is filled under the noise's bottom, so that is the bottom.
		Sc2RealNoise.World deep = normal.withHeight(level, -128, 448);
		helper.assertTrue(Heights.lowest(deep.height(), deep.generator()) == -64, "a level deeper than its noise");
		helper.assertTrue(Heights.lowest(level, level.getChunkSource().getGenerator()) == level.getMinY(), "the test world");
		helper.succeed();
	}

	/**
	 * For every kind a structure JSON names: a real start on the default world's noise, asked again with the level's bottom
	 * one block too near its box and its top one block too near (the kit refuses it, whatever its siting said), and at
	 * exactly the margins (the kit takes it, unless the kind's own stricter rule refuses); then through worldgen's own path
	 * with the level's height moved the same way, no start leaves it.
	 */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void everyKindIsRefusedNearTheBottomAndTheTopNeverClipped(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World world = Sc2RealNoise.normal(level, REAL_SEED);
		Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
		// R10-3's denser sets: more candidates round each centre cover the same land.
		Map<String, Found> found = firstStarts(level, world, snapshot, 120);
		List<String> report = new ArrayList<>();
		for (Map.Entry<String, Holder<Structure>> e : Sc2RealNoise.structures(level).entrySet()) {
			String id = e.getKey();
			if (NO_NATURAL_START.contains(id)) {
				continue;
			}
			Found f = found.get(id);
			helper.assertTrue(f != null, "no start of " + id + " found on the real noise");
			Sc2Kind kind = Sc2Kind.get(id);
			BoundingBox box = f.shape().box();
			helper.assertTrue(Heights.within(box, world.minY(), world.maxY()), id + "'s start leaves the default world: " + box);
			int bottom = box.minY() - Heights.BOTTOM;
			int top = box.maxY() + Heights.TOP;
			Sc2Structure.Sited nearBottom = Sc2Structure.site(kind, query(world, f.chunk(), snapshot, bottom + 1, world.maxY()));
			Sc2Structure.Sited nearTop = Sc2Structure.site(kind, query(world, f.chunk(), snapshot, world.minY(), top - 1));
			Sc2Structure.Sited atBottom = Sc2Structure.site(kind, query(world, f.chunk(), snapshot, bottom, world.maxY()));
			Sc2Structure.Sited atTop = Sc2Structure.site(kind, query(world, f.chunk(), snapshot, world.minY(), top));
			// Refused, or moved (a kind's own smaller size), never clipped (SC2-H).
			helper.assertTrue(!nearBottom.made() || Heights.within(nearBottom.shape().box(), bottom + 1, world.maxY()),
					id + " made with its box " + (Heights.BOTTOM - 1) + " over the bottom");
			helper.assertTrue(!nearTop.made() || Heights.within(nearTop.shape().box(), world.minY(), top - 1),
					id + " made with its box " + (Heights.TOP - 1) + " under the top");
			helper.assertFalse(atBottom.why().startsWith("height") || atTop.why().startsWith("height"), id + " refused at the margins: " + atBottom.why()
					+ " / " + atTop.why());
			// Worldgen's own path, the level's height itself moved: never a start past it.
			for (int[] h : new int[][] {{bottom + 1, world.maxY()}, {world.minY(), top - 1}, {bottom, world.maxY()}, {world.minY(), top}}) {
				Sc2RealNoise.World moved = world.withHeight(level, h[0], h[1] - h[0] + 1);
				StructureStart start = Sc2RealNoise.generate(level, moved, e.getValue(), f.chunk(), snapshot);
				helper.assertTrue(start == null || Heights.within(start.getBoundingBox(), h[0], h[1]),
						id + " made past a level of " + h[0] + ".." + h[1] + ": " + (start == null ? "" : start.getBoundingBox()));
			}
			report.add(String.format(Locale.ROOT, "%s at %s box y %d..%d: near %s / %s, at %s / %s", id, f.chunk(), box.minY(), box.maxY(), nearBottom.why(),
					nearTop.why(), atBottom.why(), atTop.why()));
		}
		Snapshot.install(null);
		report.forEach(Sc2RealNoise::log);
		helper.succeed();
	}

	/**
	 * On worldgen's own path (the kit's gates and siting, its height rule, vanilla's biome check) over the real noise of
	 * the default world, a short world (0 to 255), a raised floor (0 to 319), a low top (-64 to 191), and the amplified and
	 * large-biome presets: no start of any kind comes within the margins of either end. Prints how many each world made.
	 */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void noStartOfAnyKindLeavesTheHeightOfAnyWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		List<Sc2RealNoise.World> worlds = new ArrayList<>();
		Sc2RealNoise.World normal = Sc2RealNoise.normal(level, REAL_SEED);
		worlds.add(normal);
		worlds.add(normal.withHeight(level, 0, 256));
		worlds.add(normal.withHeight(level, 0, 320));
		worlds.add(normal.withHeight(level, -64, 256));
		worlds.add(Sc2RealNoise.of(level, "amplified", Sc2RealNoise.PRESETS.get("amplified"), REAL_SEED, -64, 384));
		worlds.add(Sc2RealNoise.of(level, "large", Sc2RealNoise.PRESETS.get("large"), REAL_SEED, -64, 384));
		Snapshot snapshot = Sc2RealNoise.snapshot(REAL_SEED);
		List<String> report = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		for (Sc2RealNoise.World world : worlds) {
			Map<String, Integer> made = new TreeMap<>();
			for (Map.Entry<String, Holder<Structure>> e : structures.entrySet()) {
				StructurePlacement placement = Sc2RealNoise.placement(level, e.getValue());
				if (placement == null || NO_NATURAL_START.contains(e.getKey())) {
					continue;
				}
				int count = 0;
				for (int[] c : new int[][] {CENTRES[0], CENTRES[2], CENTRES[4]}) {
					for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 1600, 6)) {
						StructureStart start = Sc2RealNoise.generate(level, world, e.getValue(), chunk, snapshot);
						if (start == null) {
							continue;
						}
						count++;
						if (!Heights.within(start.getBoundingBox(), Heights.lowest(world.height(), world.generator()), world.maxY()) && wrong.size() < 10) {
							wrong.add(e.getKey() + " in " + world.name() + " " + world.minY() + ".." + world.maxY() + " at " + chunk + ": " + start.getBoundingBox());
						}
					}
				}
				made.put(e.getKey(), count);
			}
			report.add(world.name() + " " + world.minY() + ".." + world.maxY() + ": " + made);
		}
		Snapshot.install(null);
		report.forEach(Sc2RealNoise::log);
		helper.assertTrue(wrong.isEmpty(), "starts past the height: " + wrong);
		helper.succeed();
	}

	// --- later worldgen ---

	/**
	 * A cell a place's fill read or set in a chunk is kept from every other chunk's decoration until its chunk is
	 * finished: a neighbour's write is refused, the chunk's own decoration and the place's own writes pass, other cells
	 * and other dimensions are untouched, and a finished chunk is free again. (The client run with
	 * {@code -Da1016_02.sc2real} generates real places with every feature round them: nothing written into them after.)
	 */
	@GameTest(environment = ENV)
	public void laterWorldgenCannotWriteIntoAPlacesCells(GameTestHelper helper) {
		net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = net.minecraft.world.level.Level.OVERWORLD;
		BlockPos cell = new BlockPos(-2_600_000 + 5, 70, 2_600_000 + 7);
		ChunkPos home = new ChunkPos(cell.getX() >> 4, cell.getZ() >> 4);
		ChunkPos neighbour = new ChunkPos(home.x() + 1, home.z());
		com.forzacode.a1016_02.world.sc2.kit.Guard.guard(dim, cell, -64, 384);
		try {
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, cell), "a neighbour's decoration wrote into it");
			helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, home, cell), "its own chunk's decoration was refused");
			helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, cell.above()), "a cell nobody guards");
			helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(net.minecraft.world.level.Level.NETHER, neighbour, cell), "another dimension");
			boolean[] own = {true};
			com.forzacode.a1016_02.world.sc2.kit.Guard.asOwn(() -> own[0] = com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, cell));
			helper.assertFalse(own[0], "the place's own write was refused");
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Guard.guarded(dim, home) == 1, "one cell guarded");
		} finally {
			com.forzacode.a1016_02.world.sc2.kit.Guard.finished(dim, home);
		}
		helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, cell), "a finished chunk is still guarded");
		helper.succeed();
	}

	/**
	 * R12-13: a chunk's guard holds bits only for the 16-high sections a place touched, so a data pack's 4 064-high level
	 * costs no more than the default one: a cell at the very bottom and one at the very top are two sections, both kept,
	 * the cells between them unguarded.
	 */
	@GameTest(environment = ENV)
	public void aGuardHoldsOnlyTheSectionsAPlaceTouched(GameTestHelper helper) {
		net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = net.minecraft.world.level.Level.OVERWORLD;
		BlockPos low = new BlockPos(-2_600_112 + 3, -2032, 2_600_096 + 13);
		BlockPos high = new BlockPos(low.getX() + 9, 2031, low.getZ() - 6);
		ChunkPos home = new ChunkPos(low.getX() >> 4, low.getZ() >> 4);
		ChunkPos neighbour = new ChunkPos(home.x(), home.z() + 1);
		com.forzacode.a1016_02.world.sc2.kit.Guard.guard(dim, low, -2032, 4064);
		com.forzacode.a1016_02.world.sc2.kit.Guard.guard(dim, high, -2032, 4064);
		try {
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Guard.sections(dim, home) == 2, "sections held: "
					+ com.forzacode.a1016_02.world.sc2.kit.Guard.sections(dim, home));
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Guard.guarded(dim, home) == 2, "two cells guarded");
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, low), "the bottom cell");
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, high), "the top cell");
			helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, low.above()), "a cell of the bottom section");
			helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, new BlockPos(low.getX(), 0, low.getZ())),
					"a cell between them");
			helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Guard.refuses(dim, neighbour, high.above()), "over the level's top");
		} finally {
			com.forzacode.a1016_02.world.sc2.kit.Guard.finished(dim, home);
		}
		helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Guard.sections(dim, home) == 0, "a finished chunk keeps its sections");
		helper.succeed();
	}

	// --- the noise read in volumes ---

	/**
	 * The kit's volume read of the noise ({@link NoiseVolume}) gives every block the generator's own column read gives,
	 * and so the same ground and surface: over the default world's mountains, sea and plains, the amplified and the
	 * large-biome presets and a short world.
	 */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void theNoiseReadInVolumesIsTheGeneratorsOwnColumns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Sc2RealNoise.World> worlds = List.of(Sc2RealNoise.normal(level, REAL_SEED), Sc2RealNoise.normal(level, 1L),
				Sc2RealNoise.of(level, "amplified", Sc2RealNoise.PRESETS.get("amplified"), REAL_SEED, -64, 384),
				Sc2RealNoise.of(level, "large", Sc2RealNoise.PRESETS.get("large"), 7L, -64, 384), Sc2RealNoise.normal(level, REAL_SEED).withHeight(level, 0, 256));
		List<String> wrong = new ArrayList<>();
		int columns = 0;
		long columnNanos = 0;
		long volumeNanos = 0;
		for (Sc2RealNoise.World world : worlds) {
			int minY = Heights.lowest(world.height(), world.generator());
			int sy = world.maxY() - minY + 1;
			for (int[] c : new int[][] {{1100, -900}, {1180, -760}, {-40, 3000}, {2600, -3600}, {5000, 5000}, {-6000, 1200}}) {
				int x0 = c[0] & ~15;
				int z0 = c[1] & ~15;
				long v0 = System.nanoTime();
				net.minecraft.world.level.block.state.BlockState[] volume = NoiseVolume.blocks(world.generator(), world.random(), x0, minY, z0, 16, sy, 16);
				volumeNanos += System.nanoTime() - v0;
				for (int i = 0; i < 16; i += 5) {
					for (int k = 0; k < 16; k += 3) {
						int x = x0 + i;
						int z = z0 + k;
						long t0 = System.nanoTime();
						net.minecraft.world.level.NoiseColumn column = world.generator().getBaseColumn(x, z, world.height(), world.random());
						int ground = world.generator().getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, world.height(),
								world.random());
						columnNanos += System.nanoTime() - t0;
						columns++;
						int top = Integer.MIN_VALUE;
						for (int y = world.maxY(); y >= minY; y--) {
							net.minecraft.world.level.block.state.BlockState mine = volume[(y - minY) + (i + k * 16) * sy];
							if (mine != column.getBlock(y) && wrong.size() < 10) {
								wrong.add(world.name() + " " + world.seed() + " " + x + " " + y + " " + z + ": " + mine + " / " + column.getBlock(y));
							}
							if (top == Integer.MIN_VALUE && net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(mine)) {
								top = y + 1;
							}
						}
						if (top != ground && top != Integer.MIN_VALUE && wrong.size() < 10) {
							wrong.add(world.name() + " ground at " + x + " " + z + ": " + top + " / " + ground);
						}
					}
				}
			}
		}
		Sc2RealNoise.log(String.format(Locale.ROOT, "noise: %d columns by the generator %.1f us each (a column and its ground), a chunk's volume %.2f ms", columns,
				columnNanos / 1e3 / columns, volumeNanos / 1e6 / (worlds.size() * 6)));
		helper.assertTrue(wrong.isEmpty(), "the volume differs from the columns: " + wrong);
		helper.succeed();
	}

	/**
	 * The kit's noise terrain answers a chunk read whole (once a thread asks about it often) exactly as column by column:
	 * ground, surface and blocks, over 120 chunks of five worlds (mountains, sea, rivers, amplified, large biomes, short).
	 * A shape's terrain must be the same whatever order a thread asks in, or two chunks of one place would disagree.
	 */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void theNoiseTerrainReadWholeAnswersAsColumnByColumn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Sc2RealNoise.World> worlds = List.of(Sc2RealNoise.normal(level, REAL_SEED), Sc2RealNoise.normal(level, 20261024L),
				Sc2RealNoise.of(level, "amplified", Sc2RealNoise.PRESETS.get("amplified"), 1L, -64, 384),
				Sc2RealNoise.of(level, "large", Sc2RealNoise.PRESETS.get("large"), REAL_SEED, -64, 384), Sc2RealNoise.normal(level, 1L).withHeight(level, 0, 256));
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		long wholeNanos = 0;
		long columnNanos = 0;
		java.util.Random random = new java.util.Random(4);
		for (Sc2RealNoise.World world : worlds) {
			for (int k = 0; k < 24; k++) {
				int x0 = (random.nextInt(12000) - 6000) & ~15;
				int z0 = (random.nextInt(12000) - 6000) & ~15;
				Terrain whole = com.forzacode.a1016_02.world.sc2.kit.Terrains.noise(world.generator(), world.random(), world.height());
				Terrain columns = com.forzacode.a1016_02.world.sc2.kit.Terrains.noise(world.generator(), world.random(), world.height());
				int[] g = new int[256];
				int[] s = new int[256];
				long t0 = System.nanoTime();
				for (int i = 0; i < 256; i++) {
					g[i] = whole.ground(x0 + (i & 15), z0 + (i >> 4));
					s[i] = whole.surface(x0 + (i & 15), z0 + (i >> 4));
				}
				wholeNanos += System.nanoTime() - t0;
				for (int i = 0; i < 256; i += 37) {
					int x = x0 + (i & 15);
					int z = z0 + (i >> 4);
					long c0 = System.nanoTime();
					int cg = columns.ground(x, z);
					int cs = columns.surface(x, z);
					columnNanos += System.nanoTime() - c0;
					checked++;
					if ((cg != g[i] || cs != s[i]) && wrong.size() < 10) {
						wrong.add(world.name() + " " + x + " " + z + ": whole " + g[i] + "/" + s[i] + ", columns " + cg + "/" + cs);
					}
					for (int y = world.minY(); y <= world.maxY(); y += 7) {
						if (whole.block(x, y, z) != columns.block(x, y, z) && wrong.size() < 10) {
							wrong.add(world.name() + " block " + x + " " + y + " " + z + ": " + whole.block(x, y, z) + " / " + columns.block(x, y, z));
						}
					}
				}
			}
		}
		Sc2RealNoise.log(String.format(Locale.ROOT, "noise terrain: a chunk's 256 grounds and surfaces whole %.2f ms; a column's ground and surface %.1f us; %d "
				+ "columns checked", wholeNanos / 1e6 / (worlds.size() * 24), columnNanos / 1e3 / checked, checked));
		helper.assertTrue(wrong.isEmpty(), "read whole differs: " + wrong);
		helper.succeed();
	}

	/** Dev: one start of the survey taken apart (-Da1016_02.sc2look=kind,seed,chunkX,chunkZ,x,y,z: prints the columns round x z). */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void oneStartTakenApartWhenAsked(GameTestHelper helper) {
		String look = System.getProperty("a1016_02.sc2look", "");
		if (look.isEmpty()) {
			helper.succeed();
			return;
		}
		String[] a = look.split(",");
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World world = Sc2RealNoise.normal(level, Long.parseLong(a[1]));
		Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
		Holder<Structure> structure = Sc2RealNoise.structures(level).get(a[0]);
		Sc2Structure.Sited sited = sited(world, structure, Sc2Kind.get(a[0]), new ChunkPos(Integer.parseInt(a[2]), Integer.parseInt(a[3])), snapshot);
		Shape shape = sited.shape();
		Sc2RealNoise.log("look: " + sited.why() + " start " + (sited.start() == null ? "-" : sited.start().origin().toShortString() + " " + sited.start().facing())
				+ " box " + (shape == null ? "-" : shape.box()) + " entrance " + (shape == null ? "-" : shape.entrance().toShortString() + " " + shape.entranceFacing()));
		if (shape == null) {
			helper.succeed();
			return;
		}
		BoundingBox box = shape.box();
		int lowest = Heights.lowest(world.height(), world.generator());
		Overlay overlay = new Overlay(NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8), Math.min(world.maxY(), box.maxY() + 8)),
				true);
		overlay.fill(shape, world.terrain(), level.registryAccess(), lowest, world.maxY(), Optional.empty());
		int x = Integer.parseInt(a[4]);
		int y = Integer.parseInt(a[5]);
		int z = Integer.parseInt(a[6]);
		for (int dx = -1; dx <= 1; dx++) {
			StringBuilder line = new StringBuilder("look column " + (x + dx) + " " + z + " ground " + world.terrain().ground(x + dx, z) + " surface "
					+ world.terrain().surface(x + dx, z) + ":");
			for (int yy = y + 12; yy >= y - 12; yy--) {
				net.minecraft.world.level.block.state.BlockState s = overlay.get(x + dx, yy, z);
				String id = s.getBlock().getDescriptionId();
				line.append(' ').append(yy).append(overlay.written(x + dx, yy, z) ? "w" : "").append('=').append(id.substring(id.lastIndexOf('.') + 1));
			}
			Sc2RealNoise.log(line.toString());
		}
		helper.succeed();
	}

	/** The generator's own column reads, one by one (what the kit's noise terrain must equal whatever it caches). */
	static Terrain columnByColumn(Sc2RealNoise.World world) {
		net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator g = world.generator();
		Terrain biomes = world.terrain();
		return new Terrain() {
			@Override
			public int seaLevel() {
				return g.getSeaLevel();
			}

			@Override
			public int minY() {
				return world.minY();
			}

			@Override
			public int ground(int x, int z) {
				return g.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, world.height(), world.random()) - 1;
			}

			@Override
			public int surface(int x, int z) {
				return g.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, world.height(), world.random()) - 1;
			}

			@Override
			public Holder<net.minecraft.world.level.biome.Biome> biome(int x, int y, int z) {
				return biomes.biome(x, y, z);
			}

			@Override
			public net.minecraft.world.level.block.state.BlockState block(int x, int y, int z) {
				if (y < world.minY() || y > world.maxY()) {
					return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
				}
				return g.getBaseColumn(x, z, world.height(), world.random()).getBlock(y);
			}
		};
	}

	/**
	 * Every kind's shape made over the kit's noise terrain (which reads a chunk whole once it is asked often, in whatever
	 * order a thread asks) is the shape made over the generator's own column reads: the same box, the same place, every
	 * cell alike. A shape that depended on the order would build two halves of different places.
	 */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void everyShapeIsTheSameWhateverOrderItsTerrainIsRead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World world = Sc2RealNoise.normal(level, REAL_SEED);
		Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
		Map<String, Found> found = firstStarts(level, world, snapshot, 120);
		List<String> wrong = new ArrayList<>();
		Terrain plain = columnByColumn(world);
		for (Found f : found.values()) {
			Sc2Kind kind = Sc2Kind.get(f.kind());
			Shape cached = f.shape();
			com.forzacode.a1016_02.world.sc2.kit.Start start = sited(world, f.structure(), kind, f.chunk(), snapshot).start();
			Shape again = kind.shapes().create(start, plain);
			if (!cached.box().equals(again.box()) || !cached.entrance().equals(again.entrance()) || !cached.anchor().equals(again.anchor())
					|| !cached.extra().equals(again.extra())) {
				wrong.add(f.kind() + ": " + cached.box() + " " + cached.entrance() + " / " + again.box() + " " + again.entrance());
				continue;
			}
			BoundingBox box = cached.box();
			int lowest = Heights.lowest(world.height(), world.generator());
			Overlay.Base base = NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8), Math.min(world.maxY(), box.maxY() + 8));
			Overlay a = new Overlay(base, true);
			Overlay b = new Overlay(base, true);
			a.fill(cached, world.terrain(), level.registryAccess(), lowest, world.maxY(), Optional.empty());
			b.fill(again, plain, level.registryAccess(), lowest, world.maxY(), Optional.empty());
			if (!a.cells().equals(b.cells())) {
				wrong.add(f.kind() + ": " + a.cells().size() + " cells / " + b.cells().size());
			}
		}
		Snapshot.install(null);
		helper.assertTrue(found.size() >= 5, "starts found: " + found.keySet());
		helper.assertTrue(wrong.isEmpty(), "shapes differ with the order of the reads: " + wrong);
		helper.succeed();
	}

	/** Dev: what a column read costs, whole and in a band round its ground (prints; with -Da1016_02.sc2cost=true). */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void theNoiseColumnsCostWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc2cost")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World world = Sc2RealNoise.normal(level, REAL_SEED);
		int n = 0;
		long full = 0;
		long one = 0;
		long band = 0;
		long mine = 0;
		int same = 0;
		for (int i = 0; i < 300; i++) {
			int x = 1000 + (i % 20) * 12;
			int z = -1000 + (i / 20) * 12;
			long t0 = System.nanoTime();
			int ground = world.terrain().ground(x, z);
			long t1 = System.nanoTime();
			NoiseVolume.blocks(world.generator(), world.random(), x, 0, z, 1, 1, 1);
			long t2 = System.nanoTime();
			int lo = Math.floorDiv(ground - 12, 8) * 8;
			net.minecraft.world.level.block.state.BlockState[] b = NoiseVolume.blocks(world.generator(), world.random(), x, lo, z, 1, 24, 1);
			long t3 = System.nanoTime();
			NoiseVolume.blocks(world.generator(), world.random(), x, -64, z, 1, 384, 1);
			long t4 = System.nanoTime();
			full += t1 - t0;
			one += t2 - t1;
			band += t3 - t2;
			mine += t4 - t3;
			n++;
			int top = Integer.MIN_VALUE;
			for (int y = 23; y >= 0 && top == Integer.MIN_VALUE; y--) {
				if (net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(b[y])) {
					top = lo + y;
				}
			}
			same += top == ground ? 1 : 0;
		}
		Sc2RealNoise.log(String.format(Locale.ROOT, "column cost: ground %.1f us, an empty chunk %.1f us, a band of 24 %.1f us, the whole column %.1f us; band right %d of %d",
				full / 1e3 / n, one / 1e3 / n, band / 1e3 / n, mine / 1e3 / n, same, n));
		helper.succeed();
	}

	// --- the survey and the cost (only when asked) ---

	/** One kind's numbers over a survey. */
	static final class Tally {
		int candidates;
		int made;
		final Map<String, Integer> refused = new TreeMap<>();
		final List<Double> refusedMillis = new ArrayList<>();
		final List<Double> madeMillis = new ArrayList<>();
		final List<Integer> refusedSamples = new ArrayList<>();
		final List<Integer> madeSamples = new ArrayList<>();
		final List<Double> chunkMillis = new ArrayList<>();
		int clean;
		String kinds = "";
		final Map<String, Integer> ways = new TreeMap<>();
		final List<String> problems = new ArrayList<>();

		void refuse(String why) {
			String key = why.contains(":") ? why.substring(0, why.indexOf(':')) : why;
			refused.merge(key, 1, Integer::sum);
		}
	}

	private static String stats(List<? extends Number> values) {
		if (values.isEmpty()) {
			return "-";
		}
		List<Double> sorted = new ArrayList<>();
		for (Number n : values) {
			sorted.add(n.doubleValue());
		}
		sorted.sort(null);
		return String.format(Locale.ROOT, "median %.2f, worst %.2f (%d)", sorted.get(sorted.size() / 2), sorted.getLast(), sorted.size());
	}

	/**
	 * Every kind over the real noise of several seeds and the three presets: each candidate the placements pick round the
	 * centres asked as worldgen asks it (timed, its samples counted), each start built chunk by chunk into an overlay over
	 * the noise's own blocks (timed per chunk) and read by {@link Sc2Audit}. Prints a table per kind; fails on a start past
	 * the height. Only with {@code -Da1016_02.sc2survey=true} (minutes). Spread over ticks.
	 */
	/**
	 * R10-3, when asked ({@code -Da1016_02.sc2presence=true}): how near the origin (the default spawn) each kind's first
	 * place stands in 20 default worlds, with the numbers R10 reviewed (before) and the review's (after): the rays or the
	 * random spread, the gates and the siting's numbers in the config, the pyramid's biomes; an open land pyramid only (not
	 * closed, not the sea form). Worldgen's own path over the real noise for every candidate, nearest first. Prints, per
	 * kind, the median distance and how many worlds have one within its target (16: 900, 17: 700, 18: 1 500, 20: 1 500).
	 */
	@GameTest(environment = ENV, maxTicks = 72000)
	public void presenceNearTheOriginWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc2presence")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		Map<String, Integer> targets = Map.of("s16", 900, "s17", 700, "s18", 1500, "s20", 1500);
		int radius = 2400;
		var biomes = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
		Set<Holder<net.minecraft.world.level.biome.Biome>> added16 = new HashSet<>();
		for (var key : List.of(net.minecraft.world.level.biome.Biomes.FOREST, net.minecraft.world.level.biome.Biomes.FLOWER_FOREST,
				net.minecraft.world.level.biome.Biomes.BIRCH_FOREST, net.minecraft.world.level.biome.Biomes.TAIGA, net.minecraft.world.level.biome.Biomes.SNOWY_TAIGA,
				net.minecraft.world.level.biome.Biomes.OLD_GROWTH_PINE_TAIGA, net.minecraft.world.level.biome.Biomes.OLD_GROWTH_SPRUCE_TAIGA,
				net.minecraft.world.level.biome.Biomes.WINDSWEPT_SAVANNA)) {
			added16.add(biomes.getOrThrow(key));
		}
		for (boolean after : new boolean[] {false, true}) {
			for (String kind : List.of("s16", "s17", "s18", "s20")) {
				Holder<Structure> structure = structures.get(kind);
				List<Integer> found = new ArrayList<>();
				int within = 0;
				long t0 = System.nanoTime();
				for (int k = 0; k < 20; k++) {
					long seed = 0x5EED_0000L + k * 7919L * 104729L;
					Sc2RealNoise.World world = Sc2RealNoise.normal(level, seed);
					Snapshot snapshot = after ? Sc2RealNoise.snapshot(seed) : before(seed);
					StructurePlacement placement = after ? Sc2RealNoise.placement(level, structure) : beforePlacement(kind);
					java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> fits = after || !kind.equals("s16") ? structure.value().biomes()::contains
							: b -> structure.value().biomes().contains(b) && !added16.contains(b);
					int distance = -1;
					for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, 0, 0, radius, 400)) {
						Snapshot.install(snapshot);
						StructureStart start = structure.value().generate(structure, net.minecraft.world.level.Level.OVERWORLD, level.registryAccess(),
								world.generator(), world.generator().getBiomeSource(),
								world.random().createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()),
								world.random(), level.getServer().getStructureTemplateManager(), world.seed(), chunk, 0, world.height(), fits);
						if (!start.isValid()) {
							continue;
						}
						var st = ((com.forzacode.a1016_02.world.sc2.kit.Sc2Piece) start.getPieces().getFirst()).start();
						if (kind.equals("s16") && (com.forzacode.a1016_02.world.sc2.s16.PyramidShape.sealed(st.variant())
								|| com.forzacode.a1016_02.world.sc2.s16.PyramidShape.sea(st.variant()))) {
							continue;
						}
						distance = (int) Math.round(Math.hypot(st.origin().getX(), st.origin().getZ()));
						break;
					}
					found.add(distance);
					within += distance >= 0 && distance <= targets.get(kind) ? 1 : 0;
				}
				Snapshot.install(null);
				List<Integer> sorted = new ArrayList<>(found);
				sorted.replaceAll(d -> d < 0 ? Integer.MAX_VALUE : d);
				java.util.Collections.sort(sorted);
				int median = sorted.get(sorted.size() / 2);
				Sc2RealNoise.log(String.format(Locale.ROOT, "presence %s %s: median %s, within %d of 20 worlds at %d (%.0f s): %s", after ? "after" : "before", kind,
						median == Integer.MAX_VALUE ? "none within " + radius : String.valueOf(median), within, targets.get(kind),
						(System.nanoTime() - t0) / 1e9, found));
			}
		}
		helper.succeed();
	}

	/** The snapshot with the numbers R10 reviewed (the configs' version 1). */
	private static Snapshot before(long seed) {
		Snapshot now = Sc2RealNoise.snapshot(seed);
		Map<String, Object> configs = new LinkedHashMap<>(now.configs());
		var c16 = new com.forzacode.a1016_02.world.sc2.s16.Sc16Config();
		c16.minOriginDistance = 640;
		c16.maxGroundSpread = 12;
		c16.sizeWeights = new double[] {0.5, 0.35, 0.15};
		c16.sealedChance = 0.25;
		c16.apexLightChance = 0.35;
		configs.put("s16", c16);
		configs.put("s16d", c16);
		var c18 = new com.forzacode.a1016_02.world.sc2.s18.Sc18Config();
		c18.minRise = 36;
		c18.highWindowChance = 0.5;
		configs.put("s18", c18);
		var c20 = new com.forzacode.a1016_02.world.sc2.s20.Sc20Config();
		c20.minOriginDistance = 900;
		configs.put("s20", c20);
		return new Snapshot(true, now.stage(), now.spawn(), now.seed(), now.salt(), configs);
	}

	/** The placements R10 reviewed (without their exclusion zones: a little kinder than they were). */
	private static StructurePlacement beforePlacement(String kind) {
		var linear = net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR;
		return switch (kind) {
			case "s16" -> new com.forzacode.a1016_02.world.sc2.kit.RaysPlacement(160721, 3, 768, 896, 8.0F, 48, 64, Optional.empty());
			case "s17" -> new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(31, 14, linear, 1716203);
			case "s18" -> new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(48, 24, linear, 180457);
			default -> new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(56, 28, linear, 2016220);
		};
	}

	@GameTest(environment = ENV, maxTicks = 72000)
	public void surveyOfEveryKindOverTheRealNoise(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc2survey")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		List<Sc2RealNoise.World> worlds = new ArrayList<>();
		for (long seed : new long[] {REAL_SEED, 1L, 20261024L, -77L}) {
			worlds.add(Sc2RealNoise.normal(level, seed));
		}
		worlds.add(Sc2RealNoise.of(level, "amplified", Sc2RealNoise.PRESETS.get("amplified"), REAL_SEED, -64, 384));
		worlds.add(Sc2RealNoise.of(level, "amplified", Sc2RealNoise.PRESETS.get("amplified"), 1L, -64, 384));
		worlds.add(Sc2RealNoise.of(level, "large", Sc2RealNoise.PRESETS.get("large"), REAL_SEED, -64, 384));
		worlds.add(Sc2RealNoise.normal(level, REAL_SEED).withHeight(level, 0, 256));
		int perCentre = Integer.getInteger("a1016_02.sc2survey.per", 8);
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		Map<String, Tally> tallies = new TreeMap<>();
		List<Runnable> jobs = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		for (Sc2RealNoise.World world : worlds) {
			Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
			int lowest = Heights.lowest(world.height(), world.generator());
			for (Map.Entry<String, Holder<Structure>> e : structures.entrySet()) {
				Sc2Kind kind = Sc2Kind.get(e.getKey());
				StructurePlacement placement = Sc2RealNoise.placement(level, e.getValue());
				if (kind == null || placement == null || NO_NATURAL_START.contains(e.getKey())) {
					continue;
				}
				Tally tally = tallies.computeIfAbsent(e.getKey(), k -> new Tally());
				Set<ChunkPos> seen = new HashSet<>();
				for (int[] c : CENTRES) {
					// Mountain land is rarer (that siting asks the biome first, so its refusals cost one sample).
					int per = e.getKey().equals("s18") ? perCentre * 5 : perCentre;
					for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, per)) {
						if (!seen.add(chunk)) {
							continue;
						}
						jobs.add(() -> {
							tally.candidates++;
							Sc2RealNoise.Counted counted = new Sc2RealNoise.Counted(world.terrain());
							long t0 = System.nanoTime();
							Sc2Structure.Sited sited = Snapshot.refusal(snapshot, kind, chunk) != null ? new Sc2Structure.Sited(null, null, "gates")
									: Sc2Structure.site(kind, new Siting.Query(chunk, counted, snapshot, world.seed(), query(world, chunk, snapshot, lowest, world.maxY())
											.random(), lowest, world.maxY()));
							double ms = (System.nanoTime() - t0) / 1e6;
							if (sited.made() && !Sc2RealNoise.biomeFits(world, e.getValue(), sited.start().origin())) {
								sited = new Sc2Structure.Sited(null, sited.shape(), "biome");
							}
							if (!sited.made()) {
								if (e.getKey().equals("s17") && sited.why().equals("siting")) {
									com.forzacode.a1016_02.world.sc2.s17.ArchiveWays.whyAt(query(world, chunk, snapshot, lowest, world.maxY()), tally.ways);
								}
								tally.refuse(sited.why());
								if (sited.shape() != null) {
									tally.refuse("with a shape");
								}
								if (ms > 50) {
									tally.refuse("over 50 ms");
								}
								tally.refusedMillis.add(ms);
								tally.refusedSamples.add(counted.samples());
								return;
							}
							tally.made++;
							tally.madeMillis.add(ms);
							tally.madeSamples.add(counted.samples());
							tally.kinds = "ground " + counted.ground + ", surface " + counted.surface + ", block " + counted.blocks + ", biome " + counted.biomes;
							Shape shape = sited.shape();
							BoundingBox box = shape.box();
							String where = e.getKey() + " " + world.name() + " " + world.seed() + " " + world.minY() + ".." + world.maxY() + " at " + chunk + " "
									+ sited.start().origin().toShortString();
							if (!Heights.within(box, lowest, world.maxY())) {
								wrong.add(where + ": " + box);
							}
							int baseTop = Math.min(world.maxY(), box.maxY() + 8);
							Overlay overlay = new Overlay(NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8), baseTop), true);
							for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
								for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
									BoundingBox column = new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15, world.maxY(), (cz << 4) + 15);
									Optional<BoundingBox> clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(column, box);
									if (clip.isEmpty()) {
										continue;
									}
									// The chunk's noise first, so the time is the shape's own.
									overlay.get(cx << 4, box.minY(), cz << 4);
									long c0 = System.nanoTime();
									shape.fill(overlay.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
									tally.chunkMillis.add((System.nanoTime() - c0) / 1e6);
								}
							}
							Sc2Audit.Report report = Sc2Audit.of(shape, overlay, baseTop, List.of());
							if (report.clean(OPEN_KINDS.contains(e.getKey()))) {
								tally.clean++;
							} else if (tally.problems.size() < 12) {
								tally.problems.add(where + " y " + box.minY() + ".." + box.maxY() + ": " + report);
							}
						});
					}
				}
			}
		}
		int[] next = {0};
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			if (!done[0]) {
				long until = System.nanoTime() + 400_000_000L;
				while (next[0] < jobs.size() && System.nanoTime() < until) {
					jobs.get(next[0]++).run();
				}
				if (next[0] < jobs.size()) {
					throw helper.assertionException("surveying: " + next[0] + " of " + jobs.size());
				}
				done[0] = true;
				Snapshot.install(null);
				for (Map.Entry<String, Tally> e : tallies.entrySet()) {
					Tally t = e.getValue();
					Sc2RealNoise.log(String.format(Locale.ROOT, "survey %s: %d candidates, %d made, %d clean, refused %s", e.getKey(), t.candidates, t.made, t.clean,
							t.refused));
					Sc2RealNoise.log("  start ms made " + stats(t.madeMillis) + "; refused " + stats(t.refusedMillis));
					Sc2RealNoise.log("  samples made " + stats(t.madeSamples) + "; refused " + stats(t.refusedSamples) + "; the last made: " + t.kinds);
					Sc2RealNoise.log("  fill ms a chunk " + stats(t.chunkMillis) + (t.ways.isEmpty() ? "" : "; ways of the refused " + t.ways));
					t.problems.forEach(p -> Sc2RealNoise.log("  problem " + p));
				}
				wrong.forEach(w -> Sc2RealNoise.log("  past the height " + w));
			}
			helper.assertTrue(wrong.isEmpty(), "starts past the height: " + wrong);
		});
	}

	// --- helpers ---

	/** A start the kit made (and vanilla's biome check would take) in this chunk, with its query and shape. */
	record Found(String kind, Holder<Structure> structure, ChunkPos chunk, Shape shape, BoundingBox box) {
	}

	/** The query worldgen would ask in this chunk of this world, with this height. */
	static Siting.Query query(Sc2RealNoise.World world, ChunkPos chunk, Snapshot snapshot, int minY, int maxY) {
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(world.seed(), chunk.x(), chunk.z());
		return new Siting.Query(chunk, world.terrain(), snapshot, world.seed(), random, minY, maxY);
	}

	/** The kit's verdict in this chunk, with vanilla's biome check after it (a start only if both take it). */
	static Sc2Structure.Sited sited(Sc2RealNoise.World world, Holder<Structure> structure, Sc2Kind kind, ChunkPos chunk, Snapshot snapshot) {
		if (Snapshot.refusal(snapshot, kind, chunk) != null) {
			return new Sc2Structure.Sited(null, null, "gates");
		}
		Sc2Structure.Sited sited = Sc2Structure.site(kind, query(world, chunk, snapshot, Heights.lowest(world.height(), world.generator()), world.maxY()));
		if (sited.made() && !Sc2RealNoise.biomeFits(world, structure, sited.start().origin())) {
			return new Sc2Structure.Sited(null, sited.shape(), "biome");
		}
		return sited;
	}

	/** The nearest start of each kind round the centres, at most {@code perCentre} candidates a centre. */
	static Map<String, Found> firstStarts(ServerLevel level, Sc2RealNoise.World world, Snapshot snapshot, int perCentre) {
		Map<String, Found> out = new LinkedHashMap<>();
		for (Map.Entry<String, Holder<Structure>> e : Sc2RealNoise.structures(level).entrySet()) {
			Sc2Kind kind = Sc2Kind.get(e.getKey());
			StructurePlacement placement = Sc2RealNoise.placement(level, e.getValue());
			if (kind == null || placement == null || NO_NATURAL_START.contains(e.getKey())) {
				continue;
			}
			search:
			for (int[] c : CENTRES) {
				for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, perCentre)) {
					Sc2Structure.Sited sited = sited(world, e.getValue(), kind, chunk, snapshot);
					if (sited.made()) {
						out.put(e.getKey(), new Found(e.getKey(), e.getValue(), chunk, sited.shape(), sited.shape().box()));
						break search;
					}
				}
			}
		}
		return out;
	}
}
