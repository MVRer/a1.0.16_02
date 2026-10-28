package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntBinaryOperator;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Frame;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Recorder;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.s19.LineFeature;
import com.forzacode.a1016_02.world.sc2.s19.LinePlan;
import com.forzacode.a1016_02.world.sc2.s19.LineShape;
import com.forzacode.a1016_02.world.sc2.s19.S19;
import com.forzacode.a1016_02.world.sc2.s19.Sc19Config;
import com.forzacode.a1016_02.world.sc2.s20.FloorAnswers;
import com.forzacode.a1016_02.world.sc2.s20.FloorShape;
import com.forzacode.a1016_02.world.sc2.s20.FloorSiting;
import com.forzacode.a1016_02.world.sc2.s20.S20;
import com.forzacode.a1016_02.world.sc2.s20.Sc20Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-8, features 19 and 20 (docs/SELF_CREATED_2.md). 19: the segments per seed on their half-axes and laid out on the
 * chunk grid; the height from the level's own (six above its bottom, six under its top; a short level has none; it stands
 * on land lower than its bottom, never on the bedrock); every column built through land, a cave, lava and water, its
 * openings at the ground or the water's top, its cut and its bare ends; the feature's own pass equal to the gallery's
 * build and recorded once, its inside the plane; it writes only on the axis and only in the chunks it may; nothing while
 * switched off, before Traces, without a snapshot or off the overworld. 20: the siting (hills, dry, the median floor, the
 * doorway toward the origin, refused rather than clipped at either end of the level's height, at most 81 samples, the
 * kit's gates); the square over a slope (flat, its four courses, cut straight, built up to the ground with no float,
 * sealed on the cut sides); the building; worldgen equal to the gallery's build and recorded once; the cows its
 * override gives; 20b on every animal on the floor and none off it, then let go, once a day, for two players alike, a
 * look only near the building, the switch letting go. Each test works in its own patch far out and random for the run
 * (the test world is kept between runs); with {@code -Da1016_02.s19cost=true} the cost per chunk is printed.
 */
public class SelfCreated2LineTests {
	private static final String ENV19 = "a1016_02:world/s19";
	private static final String ENV20 = "a1016_02:world/s20";
	private static final ResourceKey<Structure> S20_KEY = ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/s20"));
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** This run's patches start here (chunk-aligned, far out, random: the test world is kept between runs). */
	private static final int BASE_X = 16 * ThreadLocalRandom.current().nextInt(100_000, 140_000);
	private static final int BASE_Z = 16 * ThreadLocalRandom.current().nextInt(100_000, 140_000);
	/** The flat test world's grass. */
	private static final int GRASS = -61;

	/** Each test's own patch: {@code k} slots of 2048 along x from the run's base. */
	private static int slotX(int k) {
		return BASE_X + k * 2048;
	}

	// --- helpers ---

	private static void load(ServerLevel level, BoundingBox box) {
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}

	private static void fill(ServerLevel level, BoundingBox box, BlockState state) {
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			level.setBlock(pos, state, FLAGS);
		}
	}

	/**
	 * Back to the flat test world in this box (air over the grass), so a run never meets what an earlier one left; only
	 * what differs is set (a clean patch costs reads alone, and the server stays free for the tests beside this one).
	 */
	private static void flat(ServerLevel level, BoundingBox box) {
		load(level, box);
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = GRASS - 2; y <= box.maxY(); y++) {
					BlockState want = y > GRASS ? air : y == GRASS ? grass : dirt;
					if (level.getBlockState(at.set(x, y, z)) != want) {
						level.setBlock(at, want, FLAGS);
					}
				}
			}
		}
	}

	/** A terrain of the test's own: the ground and the water's top by column. */
	private static Terrain terrain(ServerLevel level, IntBinaryOperator ground, IntBinaryOperator surface, AtomicInteger samples) {
		Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
		return new Terrain() {
			@Override
			public int seaLevel() {
				return 63;
			}

			@Override
			public int minY() {
				return -64;
			}

			@Override
			public int ground(int x, int z) {
				samples.incrementAndGet();
				return ground.applyAsInt(x, z);
			}

			@Override
			public int surface(int x, int z) {
				samples.incrementAndGet();
				return Math.max(ground.applyAsInt(x, z), surface.applyAsInt(x, z));
			}

			@Override
			public Holder<Biome> biome(int x, int y, int z) {
				return plains;
			}

			@Override
			public BlockState block(int x, int y, int z) {
				return y <= ground.applyAsInt(x, z) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
			}
		};
	}

	private static Snapshot snapshot(boolean on, Stage stage, Sc19Config c19, Sc20Config c20) {
		return new Snapshot(on, stage, BlockPos.ZERO, 0x19A1L, 0x2207L, Map.of(S19.ID, c19, S20.ID, c20));
	}

	/** A config whose segments begin {@code from} out exactly and are {@code length} long. */
	private static Sc19Config far(int from, int length) {
		Sc19Config config = new Sc19Config();
		config.startMin = from;
		config.startSpread = 0;
		config.lengthMin = length;
		config.lengthMax = length;
		return config;
	}

	private static LinePlan.Segment arm(List<LinePlan.Segment> segments, LinePlan.Arm arm) {
		return segments.stream().filter(s -> s.arm() == arm).findFirst().orElseThrow();
	}

	private static String at(BlockPos pos, BlockState state) {
		return pos.toShortString() + " " + state.getBlock().getName().getString();
	}

	/** The opening covering {@code t} (its first cell), or {@code Integer.MIN_VALUE}. */
	private static int holeAt(LinePlan.Segment s, int t) {
		for (int hole : s.holes()) {
			if (t == hole || t == hole + 1) {
				return hole;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static boolean solid(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(state);
	}

	// --- 19 ---

	/**
	 * One segment per half-axis, on the axis, from {@code b} plus up to {@code c} out, {@code d} to {@code e} long, the same
	 * for the same seed and salt and not for another salt; laid out on the chunk grid: ribs on every multiple of 16 (heavy
	 * on 128), two to four bare bays at each end, the openings in the middle of every third bay and never near the cut,
	 * the cut nine wide centred on a rib in the middle half; its start remembers its length and height.
	 */
	@GameTest
	public void theSegmentsLieOnTheirHalfAxesLaidOutOnTheChunkGridByTheSeed(GameTestHelper helper) {
		Sc19Config c = new Sc19Config();
		List<LinePlan.Segment> a = LinePlan.of(42L, 7L, c, -64, 319);
		helper.assertTrue(a.equals(LinePlan.of(42L, 7L, c, -64, 319)), "the same seed gave another plan");
		helper.assertTrue(!a.equals(LinePlan.of(42L, 8L, c, -64, 319)), "another salt gave the same plan");
		helper.assertTrue(a.size() == 4 && a.stream().map(LinePlan.Segment::arm).distinct().count() == 4, "not one a half-axis: " + a);
		Set<Integer> lengths = new HashSet<>();
		for (long seed = 1; seed <= 40; seed++) {
			for (LinePlan.Segment s : LinePlan.of(seed, 3L, c, -64, 319)) {
				String id = s.arm() + " of seed " + seed;
				helper.assertTrue(s.plane() == 0, id + " off its axis");
				int from = s.arm().sign() > 0 ? s.t0() : -s.t1();
				helper.assertTrue(from >= c.startMin && from <= c.startMin + c.startSpread + 15, id + " begins " + from + " out");
				helper.assertTrue(s.length() >= c.lengthMin - 31 && s.length() <= c.lengthMax, id + " is " + s.length() + " long");
				lengths.add(s.length());
				helper.assertTrue(Math.floorMod(s.t0(), 16) == 0 && Math.floorMod(s.t1(), 16) == 0, id + " does not end on ribs");
				int bare0 = s.p0() - s.t0();
				int bare1 = s.t1() - s.p1();
				helper.assertTrue(bare0 >= 32 && bare0 <= 64 && bare1 >= 32 && bare1 <= 64 && bare0 % 16 == 0 && bare1 % 16 == 0,
						id + " bare ends " + bare0 + ", " + bare1);
				helper.assertTrue(s.ribReach(s.t0()) >= 1 && s.ribReach(s.t0() + 1) == 0 && s.ribReach(s.t0() + 8) == 0, id + " ribs");
				helper.assertTrue(s.gapWidth() == 9 && Math.floorMod(s.gapStart() + 4, 16) == 0, id + " cut " + s.gapStart() + " / " + s.gapWidth());
				int quarter = (s.p1() - s.p0()) / 4;
				helper.assertTrue(s.gapStart() + 4 >= s.p0() + quarter - 16 && s.gapStart() + 4 <= s.p1() - quarter + 16, id + " cut out of the middle half");
				int last = Integer.MIN_VALUE;
				for (int hole : s.holes()) {
					helper.assertTrue(Math.floorMod(hole, 16) == 7 && hole > s.p0() && hole + 1 < s.p1(), id + " opening at " + hole);
					helper.assertTrue(hole + 1 < s.gapStart() - LinePlan.CLEAR || hole > s.gapStart() + s.gapWidth() - 1 + LinePlan.CLEAR, id + " opening by the cut");
					helper.assertTrue(last == Integer.MIN_VALUE || hole - last == 48 || hole - last == 96, id + " openings " + last + " then " + hole);
					last = hole;
				}
				helper.assertTrue(!s.holes().isEmpty(), id + " has no opening");
				BoundingBox box = s.box();
				boolean onX = s.arm().alongX();
				helper.assertTrue(onX ? box.minZ() == 0 && box.maxZ() == 0 : box.minX() == 0 && box.maxX() == 0, id + " box " + box);
				Start start = s.start();
				helper.assertTrue(start.facing() == s.arm().out && (start.variant() & 0xFFFF) == s.length() && (start.variant() >>> 16) == s.height()
						&& start.origin().getY() == s.bottom(), id + " start " + start);
				helper.assertTrue(s.bottom() == -58 && s.top() == 313, id + " from " + s.bottom() + " to " + s.top());
			}
		}
		helper.assertTrue(lengths.size() > 10, "the lengths hardly vary: " + lengths);
		helper.succeed();
	}

	/**
	 * Its height is the level's: six above its lowest block to six under its highest, for the default overworld, a short
	 * custom height and a tall one; a level too short for 64 of it gets none; and the lowest cell it may ever write is one
	 * above the level's lowest (the bedrock's bottom layer is never in reach).
	 */
	@GameTest
	public void itStandsFromSixAboveTheBottomToSixUnderTheTopOfTheLevelsOwnHeight(GameTestHelper helper) {
		Sc19Config c = new Sc19Config();
		int[][] levels = {{-64, 319}, {0, 255}, {-2032, 2031}, {64, 191}};
		for (int[] h : levels) {
			List<LinePlan.Segment> segments = LinePlan.of(9L, 9L, c, h[0], h[1]);
			helper.assertTrue(segments.size() == 4, "none in " + h[0] + ".." + h[1]);
			for (LinePlan.Segment s : segments) {
				helper.assertTrue(s.bottom() == h[0] + 6 && s.top() == h[1] - 6, "in " + h[0] + ".." + h[1] + ": " + s.bottom() + ".." + s.top());
				LineShape shape = new LineShape(s, terrain(helper.getLevel(), (x, z) -> h[0] + 40, (x, z) -> h[0], new AtomicInteger()));
				helper.assertTrue(shape.box().minY() == h[0] + 1 && shape.box().maxY() == h[1] - 6, "the box " + shape.box() + " in " + h[0] + ".." + h[1]);
			}
		}
		helper.assertTrue(LinePlan.of(9L, 9L, c, 0, 62).isEmpty(), "a level 63 high got some");
		helper.assertTrue(LinePlan.of(9L, 9L, c, 0, 75).size() == 4, "a level 76 high got none");
		helper.succeed();
	}

	/**
	 * Built through land: every panel cell obsidian from its bottom to its top through rock, a cave, lava and a pond (the
	 * cave, lava and water beside it left as they were), but its openings (at the ground, or at the water's top with the lower
	 * cell water); every rib crying obsidian from 20 over the ground, obsidian under that; the cut and the bare bays'
	 * panels left as they were; where the land lies lower than its bottom it stands on it, the bedrock untouched; the ground
	 * beside the panels dead two out (coarse: grass never takes it back), not three.
	 */
	@GameTest(maxTicks = 400)
	public void everyColumnGoesThroughLandCaveLavaAndWaterButItsOpeningsAndItsCut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int px = slotX(1) + 8;
		int z0 = BASE_Z;
		LinePlan.Segment s = LinePlan.cut(LinePlan.Arm.SOUTH, px, z0, 320, 0x5EEDL, 48, 9, level.getMinY() + 6, level.getMaxY() - 6);
		flat(level, new BoundingBox(px - 8, GRASS - 2, s.t0() - 4, px + 8, s.top(), s.t1() + 4));
		// Rock up to -40 over the first 160 of it, with a cave, a lava pocket and a pond in it.
		int rockEnd = z0 + 160;
		fill(level, new BoundingBox(px - 6, GRASS, z0, px + 6, -41, rockEnd), Blocks.STONE.defaultBlockState());
		fill(level, new BoundingBox(px - 6, -40, z0, px + 6, -40, rockEnd), Blocks.GRASS_BLOCK.defaultBlockState());
		fill(level, new BoundingBox(px - 3, -56, z0 + 100, px + 3, -50, z0 + 106), Blocks.AIR.defaultBlockState());
		fill(level, new BoundingBox(px - 3, -50, z0 + 60, px + 3, -48, z0 + 63), Blocks.LAVA.defaultBlockState());
		fill(level, new BoundingBox(px - 5, -41, z0 + 120, px + 5, -40, z0 + 140), Blocks.WATER.defaultBlockState());
		Terrains.Frozen terrain = Live.terrain(level);
		LineShape shape = new LineShape(s, terrain, 3);
		Live.place(level, S19.KIND, shape, terrain, s.start());

		List<String> wrong = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int t = s.t0(); t <= s.t1() && wrong.size() < 12; t++) {
			boolean rock = t <= rockEnd;
			boolean pond = t >= z0 + 120 && t <= z0 + 140;
			int ground = !rock ? GRASS : pond ? -42 : -40;
			for (int y = level.getMinY(); y <= s.top(); y += y < 0 ? 1 : 7) {
				BlockState state = level.getBlockState(at.set(px, y, t));
				if (y == level.getMinY() && !state.is(Blocks.BEDROCK)) {
					wrong.add("bedrock " + at(at, state));
				}
			}
			if (s.inGap(t)) {
				BlockState state = level.getBlockState(at.set(px, 0, t));
				BlockState low = level.getBlockState(at.set(px, ground, t));
				if (!state.isAir() || low.is(Blocks.OBSIDIAN) || low.is(Blocks.CRYING_OBSIDIAN)) {
					wrong.add("in the cut " + at(at, low) + " / " + state);
				}
				continue;
			}
			int reach = s.ribReach(t);
			if (reach > 0) {
				for (int a = -reach; a <= reach; a++) {
					BlockState high = level.getBlockState(at.set(px + a, ground + 25, t));
					BlockState low = level.getBlockState(at.set(px + a, ground + 3, t));
					BlockState sky = level.getBlockState(at.set(px + a, s.top(), t));
					// Version 2: plain obsidian from the land to the top (R10-17, R11-2).
					if (!high.is(Blocks.OBSIDIAN) || !low.is(Blocks.OBSIDIAN) || !sky.is(Blocks.OBSIDIAN)) {
						wrong.add("rib " + t + " " + a + ": " + low.getBlock().getName().getString() + " / " + high.getBlock().getName().getString());
					}
				}
				continue;
			}
			if (!s.panel(t)) {
				if (!level.getBlockState(at.set(px, 0, t)).isAir()) {
					wrong.add("a panel in a bare bay " + at(at, level.getBlockState(at)));
				}
				continue;
			}
			int hole = holeAt(s, t);
			boolean wet = hole != Integer.MIN_VALUE && hole >= z0 + 120 && hole <= z0 + 140;
			int holeGround = hole == Integer.MIN_VALUE || hole > rockEnd ? GRASS : -40;
			int lo = hole == Integer.MIN_VALUE ? Integer.MAX_VALUE : wet ? -40 : holeGround + 1;
			// Version 2: the panel stops in the horizon's band, 64 to 86 over the land under its bay, never over 192 (R11-2).
			int top = shape.panelTop(terrain, t);
			if (top > LineShape.PANELS_UNDER || top < GRASS + 64 - LinePlan.BAY || top > -40 + 86) {
				wrong.add("the panel's top at " + t + ": " + top);
			}
			if (top < s.top() && !level.getBlockState(at.set(px, top + 1, t)).isAir()) {
				wrong.add("over the panel " + at(at, level.getBlockState(at)));
			}
			for (int y = Math.max(s.bottom(), ground + 1 < s.bottom() ? ground + 1 : s.bottom()); y <= top; y += y < 0 ? 1 : 3) {
				BlockState state = level.getBlockState(at.set(px, y, t));
				boolean open = y >= lo && y <= lo + 1;
				BlockState want = !open ? Blocks.OBSIDIAN.defaultBlockState() : wet && y == lo ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
				if (!state.is(want.getBlock())) {
					wrong.add("panel " + at(at, state) + ", wanted " + want.getBlock().getName().getString());
					break;
				}
			}
			if (!rock) {
				// It stands on the flat ground (lower than its bottom): from the grass up, the grass and the bedrock untouched.
				BlockState foot = level.getBlockState(at.set(px, GRASS + 1, t));
				if (hole == Integer.MIN_VALUE && !foot.is(Blocks.OBSIDIAN) || !level.getBlockState(at.set(px, GRASS, t)).is(Blocks.GRASS_BLOCK)) {
					wrong.add("its foot " + at(at.set(px, GRASS + 1, t), foot));
				}
				if (reach == 0) {
					BlockState dead = level.getBlockState(at.set(px + 2, GRASS, t));
					BlockState alive = level.getBlockState(at.set(px + 3, GRASS, t));
					if (!dead.is(Blocks.COARSE_DIRT) || !alive.is(Blocks.GRASS_BLOCK)) {
						wrong.add("the dead ground at " + t + ": " + dead.getBlock().getName().getString() + " / " + alive.getBlock().getName().getString());
					}
				}
			}
		}
		// The cave, the lava and the pond beside the plane are as they were.
		helper.assertTrue(level.getBlockState(new BlockPos(px + 3, -53, z0 + 103)).isAir(), "the cave beside it was filled");
		helper.assertTrue(level.getBlockState(new BlockPos(px + 3, -49, z0 + 61)).is(Blocks.LAVA), "the lava beside it went");
		helper.assertTrue(level.getBlockState(new BlockPos(px + 4, -40, z0 + 130)).is(Blocks.WATER), "the pond beside it went");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * The feature's own pass over every chunk of a segment equals the gallery's build of the same segment block for block,
	 * and records it once: a {@code LINE} place whose box is the plane, its mouth toward the origin, its inside the plane
	 * (an opening is in it, the ground beside it is not).
	 */
	@GameTest(environment = ENV19, maxTicks = 400)
	public void theFeatureEqualsTheGallerysBuildAndRecordsItOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc19Config config = far(slotX(2), 320);
		Snapshot snapshot = snapshot(true, Stage.TRACES, config, new Sc20Config());
		// The tests share one world seed: each lays its segments out from its own config (nothing fixed by another).
		LineFeature.forgetFixed(snapshot);
		LinePlan.Segment s = arm(LineFeature.segments(snapshot, level.getMinY(), level.getMaxY()), LinePlan.Arm.EAST);
		int moved = 256;
		flat(level, new BoundingBox(s.t0() - 4, GRASS - 2, -4, s.t1() + 4, s.top(), 4));
		flat(level, new BoundingBox(s.t0() - 4, GRASS - 2, moved - 4, s.t1() + 4, s.top(), moved + 4));
		Sc2Data data = new Sc2Data();
		Recorder.drain(new Sc2Data());
		try {
			ChunkGenerator generator = level.getChunkSource().getGenerator();
			int written = 0;
			for (int cx = (s.t0() - 2) >> 4; cx <= (s.t1() + 2) >> 4; cx++) {
				for (int cz = -1; cz <= 0; cz++) {
					written += LineFeature.decorate(level, generator, new ChunkPos(cx, cz), snapshot);
				}
			}
			helper.assertTrue(written > 20_000, "only " + written + " blocks");
			helper.assertTrue(Recorder.drain(data) == 1 && data.places().size() == 1, "recorded " + data.places().size());
			Remains.Place place = data.places().getFirst().place();
			helper.assertTrue(place.kind() == Remains.Kind.LINE && place.box().equals(s.box()) && place.entranceFacing() == Direction.WEST,
					"the place: " + place);
			LinePlan.Segment copy = LinePlan.cut(s.arm(), moved, s.t0(), s.length(), s.seed(), config.holeEvery, config.gapWidth, s.bottom(), s.top());
			helper.assertTrue(copy.holes().equals(s.holes()) && copy.gapStart() == s.gapStart() && copy.p0() == s.p0(), "the copy is laid out differently");
			// The same terrain as worldgen's (the noise): the flat test world's noise is not its blocks.
			Terrain terrain = Terrains.noise(level);
			Live.place(level, S19.KIND, new LineShape(copy, terrain), terrain, copy.start());
			List<String> differ = new ArrayList<>();
			int obsidian = 0;
			for (int t = s.t0() - 2; t <= s.t1() + 2; t++) {
				for (int a = -3; a <= 3; a++) {
					for (int y = level.getMinY(); y <= s.top(); y++) {
						BlockState one = level.getBlockState(new BlockPos(t, y, a));
						BlockState two = level.getBlockState(new BlockPos(t, y, moved + a));
						obsidian += one.is(Blocks.OBSIDIAN) ? 1 : 0;
						if (one != two && differ.size() < 8) {
							differ.add(t + " " + y + " " + a + ": " + one + " / " + two);
						}
					}
				}
			}
			helper.assertTrue(differ.isEmpty(), "the feature and the gallery differ: " + differ);
			helper.assertTrue(obsidian > 15_000, "only " + obsidian + " obsidian");
			RemainsImpl remains = RemainsImpl.over(data);
			BlockPos mouth = place.entrance();
			helper.assertTrue(mouth.getX() == s.holes().getFirst() && mouth.getZ() == 0, "its mouth " + mouth + " is not the opening nearest the origin");
			helper.assertTrue(remains.containing(level, mouth).isPresent(), "an opening is not inside it");
			helper.assertTrue(remains.containing(level, mouth.south()).isEmpty(), "beside it is inside it");
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.s19.LineAnswers.open(level.getBlockState(mouth)) && com.forzacode.a1016_02.world.sc2.s19.LineAnswers.open(level.getBlockState(mouth.above().east())) && level.getBlockState(mouth.above(2))
					.isSolidRender(), "the opening is not open two by two");
		} finally {
			Recorder.drain(new Sc2Data());
		}
		helper.succeed();
	}

	/**
	 * The feature writes only on its axis and only in the chunk being made and its unfinished neighbours (a finished
	 * chunk is never touched: here every neighbour is finished, so only the chunk itself changes); a chunk away from every
	 * segment costs nothing; switched off, before Traces, with no snapshot or off the overworld, nothing at all.
	 */
	@GameTest(environment = ENV19, maxTicks = 400)
	public void itWritesOnlyOnTheAxisOnlyInItsChunksAndOnlyWhenAllowed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc19Config config = far(slotX(3), 192);
		Sc20Config c20 = new Sc20Config();
		Snapshot on = snapshot(true, Stage.TRACES, config, c20);
		LineFeature.forgetFixed(on);
		LinePlan.Segment s = arm(LineFeature.segments(on, level.getMinY(), level.getMaxY()), LinePlan.Arm.EAST);
		flat(level, new BoundingBox(s.t0() - 20, GRASS - 2, -20, s.t1() + 20, 124, 20));
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		int cx = (s.p0() >> 4) + 2;
		ChunkPos chunk = new ChunkPos(cx, 0);
		Recorder.drain(new Sc2Data());
		try {
			helper.assertTrue(LineFeature.decorate(level, generator, chunk, null) == 0, "built with no snapshot");
			helper.assertTrue(LineFeature.decorate(level, generator, chunk, snapshot(false, Stage.TRACES, config, c20)) == 0, "built switched off");
			// Round 3 (D-108): the line is there from the world's creation.
			helper.assertTrue(LineFeature.refusal(snapshot(true, Stage.ALONE, config, c20)) == null, "refused in Alone");
			Sc19Config later = far(slotX(3), 192);
			later.fromStage = 2;
			helper.assertTrue(LineFeature.decorate(level, generator, chunk, snapshot(true, Stage.TRACES, later, c20)) == 0, "built under its stage");
			ServerLevel nether = level.getServer().getLevel(Level.NETHER);
			if (nether != null) {
				helper.assertTrue(LineFeature.decorate(nether, nether.getChunkSource().getGenerator(), chunk, on) == 0, "built in the nether");
			}
			helper.assertTrue(level.getBlockState(new BlockPos(chunk.getMinBlockX() + 3, 0, 0)).isAir(), "something was built while refused");
			helper.assertTrue(LineFeature.decorate(level, generator, new ChunkPos(cx, 3), on) == 0, "a chunk 48 off the axis built something");
			int written = LineFeature.decorate(level, generator, chunk, on);
			helper.assertTrue(written > 1_000, "only " + written);
			List<String> outside = new ArrayList<>();
			for (int x = s.t0() - 20; x <= s.t1() + 20; x++) {
				for (int z = -20; z <= 20; z++) {
					for (int y = GRASS; y <= 120; y += 4) {
						BlockState state = level.getBlockState(new BlockPos(x, y, z));
						boolean built = state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || (y == GRASS && (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)));
						if (built && ((x >> 4) != cx || (z >> 4) != 0 || Math.abs(z) > 2) && outside.size() < 6) {
							outside.add(x + " " + y + " " + z + " " + state.getBlock().getName().getString());
						}
					}
				}
			}
			helper.assertTrue(outside.isEmpty(), "written outside its chunk or off its axis: " + outside);
			// The panels stand exactly on the axis.
			for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
				if (s.panel(x) && !s.inGap(x) && s.ribReach(x) == 0 && holeAt(s, x) == Integer.MIN_VALUE) {
					helper.assertTrue(level.getBlockState(new BlockPos(x, 10, 0)).is(Blocks.OBSIDIAN) && level.getBlockState(new BlockPos(x, 10, 1)).isAir(),
							"not on the axis at " + x);
				}
			}
		} finally {
			Recorder.drain(new Sc2Data());
		}
		helper.succeed();
	}

	/**
	 * R12-2, a segment fixed on its first decoration: once it has built something in a world, a later config (another
	 * opening rhythm, another cut) lays it out the same: the next decorations build the fixed openings and cut, the
	 * recorded start's shape is the fixed one, the fixed layout round-trips through its tag and the kit's data; a world
	 * (another salt) whose segment never built anything takes the config as it is now.
	 */
	@GameTest(environment = ENV19, maxTicks = 400)
	public void aSegmentKeepsItsLayoutOnceItHasBuiltWhateverTheConfigSaysLater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		Sc19Config config = far(slotX(12), 320);
		Snapshot snapshot = snapshot(true, Stage.TRACES, config, new Sc20Config());
		LineFeature.forgetFixed(snapshot);
		Recorder.drain(new Sc2Data());
		try {
			LinePlan.Segment planned = arm(LineFeature.segments(snapshot, minY, maxY), LinePlan.Arm.EAST);
			Terrain noise = Terrains.noise(level);
			int oy = new LineShape(planned, noise).opening(noise, planned.holes().getFirst())[0];
			flat(level, new BoundingBox(planned.t0() - 4, GRASS - 2, -3, planned.t1() + 4, oy + 3, 3));
			ChunkGenerator generator = level.getChunkSource().getGenerator();
			int first = planned.p0() >> 4;
			helper.assertTrue(LineFeature.decorate(level, generator, new ChunkPos(first, 0), snapshot) > 0, "the first chunk built nothing");
			LinePlan.Segment fixed = LineFeature.fixed(snapshot.seed(), snapshot.salt(), LinePlan.Arm.EAST);
			helper.assertTrue(planned.equals(fixed), "not fixed on its first build: " + fixed);
			Sc19Config later = far(slotX(12), 320);
			later.holeEvery = 32;
			later.gapWidth = 5;
			Snapshot changed = snapshot(true, Stage.TRACES, later, new Sc20Config());
			LinePlan.Segment fresh = arm(LinePlan.of(changed.seed(), changed.salt(), later, minY, maxY), LinePlan.Arm.EAST);
			helper.assertTrue(arm(LineFeature.segments(changed, minY, maxY), LinePlan.Arm.EAST).equals(planned), "the new config moved the fixed segment");
			for (int cx = planned.t0() >> 4; cx <= planned.t1() >> 4; cx++) {
				if (cx != first) {
					LineFeature.decorate(level, generator, new ChunkPos(cx, 0), changed);
				}
			}
			List<String> wrong = new ArrayList<>();
			boolean differs = false;
			for (int t = planned.t0(); t <= planned.t1(); t++) {
				boolean open = !solid(level.getBlockState(new BlockPos(t, oy, 0)));
				differs |= openAt(fresh, t) != openAt(planned, t);
				if (open != openAt(planned, t) && wrong.size() < 8) {
					wrong.add(t + (open ? " open" : " shut"));
				}
			}
			helper.assertTrue(differs, "the changed config lays the segment out the same: the test proves nothing");
			helper.assertTrue(wrong.isEmpty(), "built otherwise than its fixed layout: " + wrong);
			// The recorded start rebuilds the fixed layout, whatever the live config says.
			helper.assertTrue(LineShape.fromStart(planned.start(), noise).segment().equals(planned), "the recorded start is laid out anew");
			// The fixed layout round-trips through its tag and the kit's data.
			CompoundTag tag = LinePlan.toTag(planned, LineShape.VERSION);
			helper.assertTrue(planned.equals(LinePlan.fromTag(tag)) && tag.getIntOr("v", 0) == LineShape.VERSION, "the tag lost the layout");
			Sc2Data data = new Sc2Data();
			String key = LineFeature.planKey(snapshot.seed(), snapshot.salt(), LinePlan.Arm.EAST);
			helper.assertTrue(data.fixPlan(key, tag) && !data.fixPlan(key, LinePlan.toTag(fresh, LineShape.VERSION)), "the data's first plan does not stay");
			Sc2Data back = Sc2Data.CODEC.parse(NbtOps.INSTANCE, Sc2Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
			helper.assertTrue(planned.equals(LinePlan.fromTag(back.plans().get(key))), "the data lost the plan");
			// Another world (another salt), nothing built: the config as it is now.
			Snapshot other = new Snapshot(true, Stage.TRACES, BlockPos.ZERO, snapshot.seed(), snapshot.salt() + 1, Map.of(S19.ID, later, S20.ID, new Sc20Config()));
			LineFeature.forgetFixed(other);
			helper.assertTrue(arm(LineFeature.segments(other, minY, maxY), LinePlan.Arm.EAST)
					.equals(arm(LinePlan.of(other.seed(), other.salt(), later, minY, maxY), LinePlan.Arm.EAST)), "an unbuilt world's segment is not the config's");
		} finally {
			LineFeature.forgetFixed(snapshot);
			Recorder.drain(new Sc2Data());
		}
		helper.succeed();
	}

	// --- FIX-R3-E: clear of the places ---

	/**
	 * A place's box beside a segment: {@code along} long centred {@code t} cells out along its axis, across it from
	 * {@code from} to {@code to} off its plane.
	 */
	private static BoundingBox placeBox(LinePlan.Segment s, int t, int along, int from, int to) {
		int t0 = t - along / 2;
		int t1 = t0 + along - 1;
		int a0 = s.plane() + from;
		int a1 = s.plane() + to;
		return s.arm().alongX() ? new BoundingBox(t0, 60, a0, t1, 120, a1) : new BoundingBox(a0, 60, t0, a1, 120, t1);
	}

	/** True if {@code box}, with the clearance round it, meets {@code footprint} across x and z. */
	private static boolean meets(BoundingBox footprint, BoundingBox box) {
		int c = LinePlan.CLEARANCE;
		return box.minX() - c <= footprint.maxX() && box.maxX() + c >= footprint.minX() && box.minZ() - c <= footprint.maxZ() && box.maxZ() + c >= footprint.minZ();
	}

	/**
	 * What is wrong with {@code clear}, the planned {@code s} kept clear of {@code boxes}, or null: within the plan's own
	 * stretch, on the grid, at least eight bays, its openings and cut on its panels, the same plane, height and seed, and no
	 * box (with the clearance) meeting its footprint.
	 */
	private static @org.jspecify.annotations.Nullable String unclear(LinePlan.Segment s, LinePlan.Segment clear, List<BoundingBox> boxes) {
		if (clear.arm() != s.arm() || clear.plane() != s.plane() || clear.bottom() != s.bottom() || clear.top() != s.top() || clear.seed() != s.seed()) {
			return "not the same line: " + clear;
		}
		if (clear.t0() < s.t0() || clear.t1() > s.t1() || Math.floorMod(clear.t0(), LinePlan.BAY) != 0 || Math.floorMod(clear.t1(), LinePlan.BAY) != 0
				|| clear.t1() - clear.t0() < LinePlan.MIN_LENGTH - LinePlan.BAY) {
			return "its stretch " + clear.t0() + ".." + clear.t1() + " in " + s.t0() + ".." + s.t1();
		}
		for (int hole : clear.holes()) {
			if (!clear.panel(hole) || !clear.panel(hole + 1)) {
				return "an opening off its panels at " + hole;
			}
		}
		if (clear.gapWidth() > 0 && (clear.gapStart() < clear.p0() || clear.gapStart() + clear.gapWidth() - 1 > clear.p1())) {
			return "its cut off its panels";
		}
		BoundingBox foot = LinePlan.footprint(clear);
		for (BoundingBox b : boxes) {
			if (meets(foot, b)) {
				return "within " + LinePlan.CLEARANCE + " of a place at " + b;
			}
		}
		return null;
	}

	/**
	 * FIX-R3-E (the line ran through the real seed's archive): a planned segment no place comes near is the plan itself (a
	 * place beside it farther than the clearance changes nothing); one with a place on its axis (or beside it within the
	 * clearance) keeps its near end and ends on a rib short of the place, its openings and cut laid out again on what is
	 * left; one with a place at its near end begins past it; one with places all along has none; a stretch shorter than
	 * {@code LinePlan.preferred} before a place gives way to a longer one past it, and a stretch too short for a segment
	 * between two places to the one past both. Both ways out on both axes; a segment fixed in a world stays as fixed whatever
	 * stands round it ({@code LineFeature.laidOut}, R12-2), an unfixed one with no stretch left is gone from the layout.
	 */
	@GameTest
	public void theLineEndsShortOfAPlaceInItsWayAndKeepsClearOfIt(GameTestHelper helper) {
		Sc19Config c = new Sc19Config();
		int keep = LinePlan.CLEARANCE + LineShape.LIT_REACH;
		List<String> wrong = new ArrayList<>();
		int cases = 0;
		for (long seed : new long[] {3L, 19L, 0x19E5L, -42L}) {
			for (LinePlan.Segment s : LinePlan.of(seed, 7L, c, -64, 319)) {
				int out = s.arm().sign();
				int near = s.near();
				String at = s.arm() + " of " + seed + ": ";
				// Beside it, just past the clearance on either side: the plan itself.
				List<BoundingBox> beside = List.of(placeBox(s, near + out * 300, 96, keep + 1, keep + 96), placeBox(s, near + out * 500, 96, -keep - 96, -keep - 1));
				if (LinePlan.clear(s, beside, c) != s) {
					wrong.add(at + "moved by places beside it");
				}
				// On its axis 400 out (and one beside it within the clearance, 600 out): ends short of the first, its near end kept.
				for (BoundingBox b : List.of(placeBox(s, near + out * 400, 80, -40, 40), placeBox(s, near + out * 400, 80, keep - 2, keep + 60))) {
					LinePlan.Segment clear = LinePlan.clear(s, List.of(b, placeBox(s, near + out * 600, 64, -20, 20)), c);
					String why = clear == null ? "none" : unclear(s, clear, List.of(b));
					if (why == null && clear.near() != near) {
						why = "its near end moved to " + clear.near();
					}
					if (why == null && Math.abs(near - (out > 0 ? clear.t1() : clear.t0())) > 400 - 40 - LinePlan.CLEARANCE) {
						why = "it runs into the place";
					}
					if (why == null && Math.abs(near - (out > 0 ? clear.t1() : clear.t0())) < 400 - 40 - LinePlan.CLEARANCE - LinePlan.BAY) {
						why = "it stops a bay or more short of where it may";
					}
					if (why != null) {
						wrong.add(at + "a place 400 out: " + why);
					}
					cases++;
				}
				// At its near end: it begins past the place.
				BoundingBox first = placeBox(s, near + out * 40, 120, -60, 60);
				LinePlan.Segment past = LinePlan.clear(s, List.of(first), c);
				String why = past == null ? "none" : unclear(s, past, List.of(first));
				if (why == null && (out > 0 ? past.t0() <= near : past.t1() >= near)) {
					why = "it did not begin past the place";
				}
				if (why != null) {
					wrong.add(at + "a place at its near end: " + why);
				}
				// Two places with a short stretch between them near the start and a long one past them: the long one.
				int gap0 = near + out * 220;
				List<BoundingBox> two = List.of(placeBox(s, near + out * 40, 120, -60, 60), placeBox(s, gap0 + out * 30, 60, -30, 30));
				LinePlan.Segment between = LinePlan.clear(s, two, c);
				why = between == null ? "none" : unclear(s, between, two);
				if (why == null && Math.abs(between.near() - near) < 220) {
					why = "it took the stretch too short between the two";
				}
				if (why != null) {
					wrong.add(at + "two places: " + why);
				}
				// A place 200 out leaves eight bays before it, under the preferred length: the longer stretch past it.
				BoundingBox short0 = placeBox(s, near + out * 200, 60, -30, 30);
				LinePlan.Segment longer = LinePlan.clear(s, List.of(short0), c);
				why = longer == null ? "none" : unclear(s, longer, List.of(short0));
				boolean x = s.arm().alongX();
				int beyond = out > 0 ? (x ? short0.maxX() : short0.maxZ()) : (x ? short0.minX() : short0.minZ());
				if (why == null && (out > 0 ? longer.t0() <= beyond : longer.t1() >= beyond)) {
					why = "it kept the short stretch before the place";
				}
				if (why != null) {
					wrong.add(at + "a place 200 out: " + why);
				}
				// Places all along it: none.
				List<BoundingBox> all = new ArrayList<>();
				for (int t = s.t0(); t <= s.t1(); t += 100) {
					all.add(placeBox(s, t, 40, -10, 10));
				}
				if (LinePlan.clear(s, all, c) != null) {
					wrong.add(at + "a line with places all along it");
				}
				cases += 5;
			}
		}
		// A segment fixed in a world stays as fixed; an unfixed one with no stretch left is gone from the layout.
		long seed = 0x19E5L;
		long salt = 0x2207L;
		List<LinePlan.Segment> planned = LinePlan.of(seed, salt, c, -64, 319);
		LinePlan.Places everywhere = (c0x, c0z, c1x, c1z) -> {
			List<BoundingBox> boxes = new ArrayList<>();
			for (int cx = c0x; cx <= c1x; cx += 4) {
				for (int cz = c0z; cz <= c1z; cz += 4) {
					boxes.add(new BoundingBox(cx << 4, 60, cz << 4, (cx << 4) + 15, 120, (cz << 4) + 15));
				}
			}
			return boxes;
		};
		com.forzacode.a1016_02.world.sc2.kit.Plans.forget(LineFeature.planPrefix(seed, salt));
		try {
			LinePlan.Segment east = arm(planned, LinePlan.Arm.EAST);
			com.forzacode.a1016_02.world.sc2.kit.Plans.fix(LineFeature.planKey(seed, salt, LinePlan.Arm.EAST), LinePlan.toTag(east, LineShape.VERSION));
			List<LinePlan.Segment> laid = LineFeature.laidOut(seed, salt, planned, -64, 319, c, everywhere);
			helper.assertTrue(laid.size() == 1 && laid.getFirst().equals(east), "the fixed segment moved or another stayed: " + laid);
			helper.assertTrue(LineFeature.laidOut(seed, salt, planned, -64, 319, c, LinePlan.Places.NONE).equals(planned), "no places changed the layout");
		} finally {
			com.forzacode.a1016_02.world.sc2.kit.Plans.forget(LineFeature.planPrefix(seed, salt));
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + cases + ": " + wrong.subList(0, Math.min(6, wrong.size())));
		helper.succeed();
	}

	/**
	 * FIX-R3-E: a world's planned segments kept clear of these places (the find run's: worldgen's decisions over the noise),
	 * as the live world keeps them clear of {@code Decided}'s ({@link LineFeature#laidOut}): an arm with no stretch left
	 * has none.
	 */
	static List<LinePlan.Segment> clearOf(List<LinePlan.Segment> planned, List<SelfCreated3FindTests.Place> places, Sc19Config config) {
		LinePlan.Places of = placesOf(places);
		List<LinePlan.Segment> out = new ArrayList<>(planned.size());
		for (LinePlan.Segment s : planned) {
			LinePlan.Segment clear = LinePlan.clear(s, of, config);
			if (clear != null) {
				out.add(clear);
			}
		}
		return out;
	}

	/** The boxes of these places by their start chunk, as {@code Decided.within} answers the line. */
	static LinePlan.Places placesOf(List<SelfCreated3FindTests.Place> places) {
		return (c0x, c0z, c1x, c1z) -> {
			List<BoundingBox> out = new ArrayList<>();
			for (SelfCreated3FindTests.Place p : places) {
				if (p.chunk().x() >= c0x && p.chunk().x() <= c1x && p.chunk().z() >= c0z && p.chunk().z() <= c1z) {
					out.add(p.box());
				}
			}
			return out;
		};
	}

	/**
	 * FIX-R3-E over the real noise ({@code -Da1016_02.sc3line=N} worlds, 3 by default: SC2-H's seed, seed 1 and one more):
	 * every place worldgen decides (round 3's numbers, the spacing rule) whose start lies in a band {@code PLACE_REACH} +
	 * the clearance + 256 round each half-axis's planned segment (wide, so the spacing rule sees the places' neighbours);
	 * the segment as the live world lays it out (asked as {@code Decided.within} asks: by start chunk within
	 * {@code PLACE_REACH} and the clearance of its
	 * footprint) comes within the clearance of none of them, is the plan's own where none comes near the plan, and no
	 * place's box reaches farther than {@code PLACE_REACH} from its start's chunk. Prints how many segments each way.
	 */
	@GameTest(maxTicks = 4000)
	public void theLineKeepsClearOfEveryPlaceOverTheRealNoise(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int worlds = Integer.getInteger("a1016_02.sc3line", 3);
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		SelfCreated3FindTests.Era era = SelfCreated3FindTests.after(level, structures);
		Sc19Config c = new Sc19Config();
		java.util.Random seeds = new java.util.Random(0x19C1EAL);
		List<Long> worldSeeds = new ArrayList<>();
		for (int w = 0; w < worlds; w++) {
			worldSeeds.add(w == 0 ? SelfCreated2HeightsTests.REAL_SEED : w == 1 ? 1L : seeds.nextLong());
		}
		int band = LinePlan.CLEARANCE + LinePlan.PLACE_REACH + LineShape.LIT_REACH + 256;
		List<String> wrong = Collections.synchronizedList(new ArrayList<>());
		// segments, met a place as planned, kept their near end, begun past a place, none left; places; the farthest reach;
		// candidates the live line asks about; the band's sitings' nanoseconds and count.
		long[] tally = new long[10];
		int[] next = {0};
		helper.succeedWhen(() -> {
			if (next[0] < worldSeeds.size()) {
				long seed = worldSeeds.get(next[0]++);
				Sc2RealNoise.World world = Sc2RealNoise.normal(level, seed);
				Snapshot snapshot = Sc2RealNoise.snapshot(seed);
				List<LinePlan.Segment> planned = LinePlan.of(seed, 7L, c, world.minY(), world.maxY());
				Map<String, List<Long>> nanos = new java.util.concurrent.ConcurrentHashMap<>();
				List<SelfCreated3FindTests.Place> places = SelfCreated3FindTests.placesIn(level, world, structures, era, BlockPos.ZERO, snapshot,
						new java.util.concurrent.ConcurrentHashMap<>(), nanos, placement -> {
							Set<ChunkPos> chunks = new java.util.LinkedHashSet<>();
							for (LinePlan.Segment s : planned) {
								BoundingBox b = LinePlan.footprint(s).inflatedBy(band, 0, band);
								BoundingBox look = LinePlan.footprint(s).inflatedBy(LinePlan.CLEARANCE + LinePlan.PLACE_REACH, 0,
										LinePlan.CLEARANCE + LinePlan.PLACE_REACH);
								for (ChunkPos chunk : com.forzacode.a1016_02.world.sc2.kit.Decided.candidates(placement, seed, b.minX() >> 4, b.minZ() >> 4,
										b.maxX() >> 4, b.maxZ() >> 4)) {
									if (placement.isStructureChunk(world.structures(), chunk.x(), chunk.z())) {
										chunks.add(chunk);
										if (chunk.x() >= look.minX() >> 4 && chunk.x() <= look.maxX() >> 4 && chunk.z() >= look.minZ() >> 4
												&& chunk.z() <= look.maxZ() >> 4) {
											tally[7]++;
										}
									}
								}
							}
							return new ArrayList<>(chunks);
						});
				for (List<Long> list : nanos.values()) {
					for (long n : list) {
						tally[8] += n;
						tally[9]++;
					}
				}
				tally[5] += places.size();
				List<BoundingBox> boxes = new ArrayList<>();
				for (SelfCreated3FindTests.Place p : places) {
					boxes.add(p.box());
					ChunkPos k = p.chunk();
					int reach = Math.max(Math.max(k.getMinBlockX() - p.box().minX(), p.box().maxX() - k.getMaxBlockX()),
							Math.max(k.getMinBlockZ() - p.box().minZ(), p.box().maxZ() - k.getMaxBlockZ()));
					tally[6] = Math.max(tally[6], reach);
					if (reach > LinePlan.PLACE_REACH) {
						wrong.add(seed + ": " + p.kind() + " at " + k + " reaches " + reach + " from its chunk");
					}
				}
				LinePlan.Places of = placesOf(places);
				for (LinePlan.Segment s : planned) {
					tally[0]++;
					LinePlan.Segment clear = LinePlan.clear(s, of, c);
					boolean met = boxes.stream().anyMatch(b -> meets(LinePlan.footprint(s), b));
					if (met) {
						tally[1]++;
						String near = boxes.stream().filter(b -> meets(LinePlan.footprint(s), b)).map(BoundingBox::toString).findFirst().orElse("");
						Sc2RealNoise.log("sc3line " + seed + " " + s.arm() + " " + s.t0() + ".." + s.t1() + " met a place (" + near + "): now "
								+ (clear == null ? "none" : clear.t0() + ".." + clear.t1()));
					} else if (clear != s) {
						wrong.add(seed + " " + s.arm() + ": moved with no place near it");
					}
					if (clear == null) {
						tally[4]++;
						continue;
					}
					if (met && clear.near() == s.near()) {
						tally[2]++;
					} else if (met) {
						tally[3]++;
					}
					String why = unclear(s, clear, boxes);
					if (why != null) {
						wrong.add(seed + " " + s.arm() + ": " + why);
					}
				}
				throw helper.assertionException("worlds: " + next[0] + " of " + worldSeeds.size());
			}
			Sc2RealNoise.log(String.format(Locale.ROOT, "sc3line: %d worlds, %d segments, %d met a place as planned: %d end short of it, %d begin past it, %d none; "
					+ "%d places in the bands, the farthest box %d from its start's chunk; the live line sites %.1f candidates a world, %.1f ms a siting on average",
					worldSeeds.size(), tally[0], tally[1], tally[2], tally[3], tally[4], tally[5], tally[6], tally[7] / (double) worldSeeds.size(),
					tally[8] / 1e6 / Math.max(1, tally[9])));
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.subList(0, Math.min(6, wrong.size())));
		});
	}

	/** True if the segment leaves {@code t} open at an opening's height (its cut, an opening, or a bare end between ribs). */
	private static boolean openAt(LinePlan.Segment s, int t) {
		if (s.inGap(t) || holeAt(s, t) != Integer.MIN_VALUE) {
			return true;
		}
		return !s.panel(t) && !s.rib(t);
	}

	/** An opening's lowest cell: one over the highest ground of its six columns when dry; at the water's top when wet. */
	@GameTest
	public void anOpeningIsAtTheGroundOrAtTheWatersTop(GameTestHelper helper) {
		LinePlan.Segment s = LinePlan.cut(LinePlan.Arm.SOUTH, 0, 4096, 256, 11L, 48, 9, -58, 313);
		int hole = s.holes().getFirst();
		AtomicInteger samples = new AtomicInteger();
		LineShape dry = new LineShape(s, terrain(helper.getLevel(), (x, z) -> z == hole + 1 && x == 1 ? 74 : 70, (x, z) -> 0, samples));
		helper.assertTrue(dry.opening(terrain(helper.getLevel(), (x, z) -> z == hole + 1 && x == 1 ? 74 : 70, (x, z) -> 0, samples), hole)[0] == 75,
				"dry: " + dry.opening(null, hole)[0]);
		LineShape wet = new LineShape(s, terrain(helper.getLevel(), (x, z) -> 50, (x, z) -> 62, samples));
		int[] w = wet.opening(null, hole);
		helper.assertTrue(w[0] == 62 && w[1] == 62, "wet: " + w[0] + ", water to " + w[1]);
		helper.succeed();
	}

	/** With {@code -Da1016_02.s19cost=true}: the feature's pass per chunk along a segment (each chunk's own pass). */
	@GameTest(environment = ENV19, maxTicks = 400)
	public void costPerChunk(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.s19cost")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Sc19Config config = far(slotX(4), 768);
		Snapshot snapshot = snapshot(true, Stage.TRACES, config, new Sc20Config());
		LineFeature.forgetFixed(snapshot);
		LinePlan.Segment s = arm(LineFeature.segments(snapshot, level.getMinY(), level.getMaxY()), LinePlan.Arm.EAST);
		flat(level, new BoundingBox(s.t0() - 4, GRASS - 2, -4, s.t1() + 4, s.top(), 4));
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		List<Double> first = new ArrayList<>();
		List<Double> again = new ArrayList<>();
		try {
			// Warm up on another arm first (class loading, the first shape), so the numbers are the pass's own.
			LinePlan.Segment west = arm(LineFeature.segments(snapshot, level.getMinY(), level.getMaxY()), LinePlan.Arm.WEST);
			load(level, new BoundingBox(west.p0(), 0, -1, west.p0() + 32, 0, 1));
			for (int cx = west.p0() >> 4; cx <= (west.p0() >> 4) + 2; cx++) {
				LineFeature.decorate(level, generator, new ChunkPos(cx, 0), snapshot);
			}
			for (int pass = 0; pass < 2; pass++) {
				for (int cx = s.t0() >> 4; cx <= s.t1() >> 4; cx++) {
					long t0 = System.nanoTime();
					LineFeature.decorate(level, generator, new ChunkPos(cx, 0), snapshot);
					(pass == 0 ? first : again).add((System.nanoTime() - t0) / 1_000_000.0);
				}
			}
			long t0 = System.nanoTime();
			for (int i = 0; i < 1000; i++) {
				LineFeature.decorate(level, generator, new ChunkPos(10_000 + i, 10_000), snapshot);
			}
			double away = (System.nanoTime() - t0) / 1_000_000.0 / 1000;
			Collections.sort(first);
			Collections.sort(again);
			System.out.println(String.format(Locale.ROOT, "[a1016] s19 cost per chunk: %d chunks, first pass median %.2f ms, worst %.2f ms; again median %.2f ms,"
					+ " worst %.2f ms; a chunk away %.4f ms", first.size(), first.get(first.size() / 2), first.getLast(), again.get(again.size() / 2), again.getLast(),
					away));
		} finally {
			Recorder.drain(new Sc2Data());
		}
		helper.succeed();
	}

	// --- 20 ---

	private static Siting.Query query(ServerLevel level, ChunkPos chunk, Terrain terrain, Snapshot snapshot, int minY, int maxY) {
		return new Siting.Query(chunk, terrain, snapshot, 99L, RandomSource.create(1L), minY, maxY);
	}

	/**
	 * The square goes only into hills (8 to 32 of relief), only on dry land (at most one wet sample, never its middle), at
	 * the median of its samples, its doorway toward the origin; refused rather than clipped where its box would come
	 * within six of either end of the level's height (the default overworld and a custom one); at most 81 samples; the
	 * kit's gates (the stage, 900 from the origin) hold first.
	 */
	@GameTest
	public void theSquareGoesIntoDryHillsAtTheMedianFacingTheOriginAndIsRefusedNearEitherEndOfTheHeight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc20Config config = new Sc20Config();
		config.side = 64;
		// Round 2's sizes (two chunks wider, then the side); version 3's 192 to 64 are SelfCreated3HollowFloorTests'.
		config.sideMax = 96;
		Snapshot snapshot = snapshot(true, Stage.TRACES, new Sc19Config(), config);
		ChunkPos chunk = new ChunkPos(2_000, 3_000);
		int cx = chunk.getMinBlockX();
		int cz = chunk.getMinBlockZ();
		int minX = cx - 32;
		AtomicInteger samples = new AtomicInteger();
		IntBinaryOperator dry = (x, z) -> Integer.MIN_VALUE;
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 70, dry, samples), snapshot, -64, 319)).isEmpty(),
				"on a plain");
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 70 + (x - minX), dry, samples), snapshot, -64, 319)).isEmpty(),
				"on a cliff");
		samples.set(0);
		Optional<Start> slope = FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 70 + (x - minX) / 4, dry, samples), snapshot, -64, 319));
		helper.assertTrue(samples.get() <= 81, samples.get() + " samples");
		helper.assertTrue(slope.isPresent(), "not on a slope");
		Start start = slope.get();
		// Two chunks wider than the config's side first, where the land allows (R11-5).
		int wider = 64 + FloorSiting.WIDER;
		FloorSiting.Fit fit = FloorSiting.fit(terrain(level, (x, z) -> 70 + (x - minX) / 4, dry, new AtomicInteger()), cx, cz, wider);
		helper.assertTrue(start.origin().equals(new BlockPos(cx, fit.floor(), cz)) && fit.floor() >= 74 && fit.floor() <= 81, "the floor " + start.origin());
		helper.assertTrue(start.facing() == Direction.NORTH && FloorShape.sideOf(start.variant()) == wider, "facing " + start.facing() + ", side "
				+ FloorShape.sideOf(start.variant()));
		ChunkPos west = new ChunkPos(-3_000, 100);
		Optional<Start> westward = FloorSiting.INSTANCE.find(query(level, west, terrain(level, (x, z) -> 70 + Math.floorMod(x, 64) / 4, dry, samples), snapshot,
				-64, 319));
		helper.assertTrue(westward.isPresent() && westward.get().facing() == Direction.EAST, "far west, facing " + westward.map(Start::facing));
		// Wet: the middle, or two samples.
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 70 + (x - minX) / 4, (x, z) -> x == cx && z == cz ? 200
				: Integer.MIN_VALUE, samples), snapshot, -64, 319)).isEmpty(), "with its middle wet");
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 70 + (x - minX) / 4, (x, z) -> (z == cz - 32 || z == cz - 48) && x < cx
				? 200 : Integer.MIN_VALUE, samples), snapshot, -64, 319)).isEmpty(), "with two wet samples");
		// The ends of the height: the top (trees over the highest ground), the bottom (the bedrock course and the footing).
		IntBinaryOperator high = (x, z) -> 268 + (x - minX) / 4;
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, high, dry, samples), snapshot, -64, 319)).isEmpty(), "under the build limit");
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 250 + (x - minX) / 4, dry, samples), snapshot, -64, 319))
				.isPresent(), "refused well under the limit");
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> -48 + (x - minX) / 4, dry, samples), snapshot, -64, 319))
				.isEmpty(), "near the bedrock");
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 200 + (x - minX) / 4, dry, samples), snapshot, 0, 255))
				.isEmpty(), "under a custom height's top");
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 150 + (x - minX) / 4, dry, samples), snapshot, 0, 255))
				.isPresent(), "refused well inside a custom height");
		helper.assertTrue(FloorSiting.INSTANCE.find(query(level, chunk, terrain(level, (x, z) -> 12 + (x - minX) / 4, dry, samples), snapshot, 0, 255))
				.isEmpty(), "near a custom height's bottom");
		// The kit's gates come first.
		helper.assertTrue(Snapshot.refusal(snapshot, S20.KIND, new ChunkPos(10, 10)) != null, "near the origin");
		helper.assertTrue(Snapshot.refusal(snapshot(true, Stage.ALONE, new Sc19Config(), config), S20.KIND, chunk) == null, "refused in Alone (D-108)");
		helper.assertTrue(Snapshot.refusal(snapshot(false, Stage.TRACES, new Sc19Config(), config), S20.KIND, chunk) != null, "switched off");
		helper.assertTrue(Snapshot.refusal(snapshot, S20.KIND, chunk) == null, "refused far out at Traces");
		helper.succeed();
	}

	/** The east-rising slope the square tests are cut into: rock with a grass top, 20 of relief over 80. */
	private static int slope(int x, int west) {
		return -50 + Math.max(0, Math.min(79, x - west)) / 4;
	}

	/**
	 * Over a slope: the floor flat (grass on two dirt on bedrock) over the whole square and nothing over it but the
	 * building; where the land fell away it is built up to the ground with no air under it, its sides straight; the cut
	 * side sealed two deep (a cave and water in the cliff filled), the rock past that untouched; a tree on the square gone.
	 */
	@GameTest(maxTicks = 400)
	public void theSquareIsFlatCutStraightBuiltUpToTheGroundAndSealed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int cx = slotX(5);
		int cz = BASE_Z;
		int west = cx - 40;
		flat(level, new BoundingBox(cx - 44, GRASS - 2, cz - 44, cx + 44, 40, cz + 44));
		for (int x = cx - 42; x <= cx + 42; x++) {
			int top = slope(x, west);
			fill(level, new BoundingBox(x, GRASS, cz - 42, x, top - 1, cz + 42), Blocks.STONE.defaultBlockState());
			fill(level, new BoundingBox(x, top, cz - 42, x, top, cz + 42), Blocks.GRASS_BLOCK.defaultBlockState());
		}
		FloorSiting.Fit fit = FloorSiting.fit(Live.terrain(level), cx, cz, 64);
		helper.assertTrue(fit != null, "the slope does not fit");
		int f = fit.floor();
		// In the cliff, within reach of the cut (east of the square, its edge at cx + 31): a cave and water.
		fill(level, new BoundingBox(cx + 32, f + 2, cz - 3, cx + 33, f + 3, cz + 3), Blocks.AIR.defaultBlockState());
		fill(level, new BoundingBox(cx + 32, f + 2, cz + 10, cx + 33, f + 3, cz + 12), Blocks.WATER.defaultBlockState());
		fill(level, new BoundingBox(cx + 35, f + 2, cz - 3, cx + 35, f + 3, cz + 3), Blocks.AIR.defaultBlockState());
		// A tree on the square.
		int tg = slope(cx - 12, west);
		fill(level, new BoundingBox(cx - 12, tg + 1, cz + 12, cx - 12, tg + 5, cz + 12), Blocks.OAK_LOG.defaultBlockState());
		fill(level, new BoundingBox(cx - 14, tg + 4, cz + 10, cx - 10, tg + 6, cz + 14), Blocks.OAK_LEAVES.defaultBlockState());
		// The crown of a tree on its edge, reaching out over the cliff: it would float once the trunk is taken.
		int crown = slope(cx + 33, west) + 3;
		fill(level, new BoundingBox(cx + 30, crown, cz + 18, cx + 33, crown + 1, cz + 21), Blocks.OAK_LEAVES.defaultBlockState());
		Live.Result built = Live.place(level, S20.KIND, new Start(new BlockPos(cx, f, cz), Direction.WEST, FloorShape.variant(64), 0x20AL).withVersion(2));
		FloorShape shape = (FloorShape) built.shape();
		BoundingBox house = shape.house().box(0, 0, 0, FloorShape.HOUSE - 1, 5, -(FloorShape.HOUSE - 1));
		List<String> wrong = new ArrayList<>();
		for (int x = cx - 32; x <= cx + 31 && wrong.size() < 10; x++) {
			for (int z = cz - 32; z <= cz + 31 && wrong.size() < 10; z++) {
				boolean inHouse = house.isInside(x, f, z);
				if (x == cx - 32 || x == cx + 31 || z == cz - 32 || z == cz + 31) {
					// The edges: a kerb where the land falls away (theEdgesOfTheSquareAreFair).
					continue;
				}
				if (!inHouse && !level.getBlockState(new BlockPos(x, f, z)).is(Blocks.GRASS_BLOCK)) {
					wrong.add("floor " + at(new BlockPos(x, f, z), level.getBlockState(new BlockPos(x, f, z))));
				}
				if (!level.getBlockState(new BlockPos(x, f - 1, z)).is(Blocks.DIRT) || !level.getBlockState(new BlockPos(x, f - 2, z)).is(Blocks.DIRT)
						|| !level.getBlockState(new BlockPos(x, f - 3, z)).is(Blocks.BEDROCK)) {
					wrong.add("courses under " + x + " " + z);
				}
				for (int y = f + 1; y <= f + 30; y++) {
					if (!house.isInside(x, y, z) && !level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						wrong.add("over the floor " + at(new BlockPos(x, y, z), level.getBlockState(new BlockPos(x, y, z))));
						break;
					}
				}
				// Built up to the ground: no air or water between the bedrock course and the rock under it.
				int ground = slope(x, west);
				for (int y = Math.min(ground, f - 4); y <= f - 4; y++) {
					if (!solid(level.getBlockState(new BlockPos(x, y, z)))) {
						wrong.add("under the floor " + at(new BlockPos(x, y, z), level.getBlockState(new BlockPos(x, y, z))));
						break;
					}
				}
			}
		}
		// The built-up west side stands straight: air just outside it from its ground up to the floor.
		int outside = cx - 33;
		for (int y = slope(outside, west) + 1; y <= f; y++) {
			if (!level.getBlockState(new BlockPos(outside, y, cz)).isAir()) {
				wrong.add("the west side slopes at " + y + ": " + level.getBlockState(new BlockPos(outside, y, cz)));
				break;
			}
		}
		helper.assertTrue(slope(outside, west) < f - 4, "the west side is not built up at all here");
		// The cut side: the cave and the water within reach sealed, the cave past it left.
		for (BlockPos pos : BlockPos.betweenClosed(cx + 32, f + 2, cz - 3, cx + 33, f + 3, cz + 12)) {
			if (pos.getZ() <= cz + 3 || pos.getZ() >= cz + 10) {
				if (!solid(level.getBlockState(pos))) {
					wrong.add("not sealed " + at(pos.immutable(), level.getBlockState(pos)));
					break;
				}
			}
		}
		helper.assertTrue(level.getBlockState(new BlockPos(cx + 35, f + 2, cz)).isAir(), "the cave past its reach was filled");
		helper.assertTrue(level.getBlockState(new BlockPos(cx + 31, f + 2, cz)).isAir() && solid(level.getBlockState(new BlockPos(cx + 32, f + 6, cz))),
				"the cut is not a straight cliff");
		helper.assertTrue(level.getBlockState(new BlockPos(cx + 33, crown, cz + 20)).isAir() && level.getBlockState(new BlockPos(cx + 32, crown + 1, cz + 18)).isAir(),
				"a crown over the cliff floats");
		helper.assertTrue(level.getBlockState(new BlockPos(cx - 12, f + 2, cz + 12)).isAir() && level.getBlockState(new BlockPos(cx - 12, tg + 5, cz + 12)).isAir()
				&& level.getBlockState(new BlockPos(cx - 12, tg + 5, cz + 13)).isAir(), "the tree on the square stands");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * R12-3, R13-3 and R12-2: a 64 square cut into a slope that rises 40 over 80 (cliffs over 3 on the uphill side, a
	 * built-up face over 3 on the downhill one), as version 2: walking on the floor nobody goes over its built-up edge (a
	 * kerb of turf a block high along it) and walking down the hill nobody goes over the cliff (a lip a block over the land
	 * coming down to it); nothing unguarded drops more than 3. The same start as version 1 has those drops.
	 */
	@GameTest(maxTicks = 400)
	public void theEdgesOfTheSquareAreFair(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int shift = 256;
		int cz = BASE_Z;
		List<FloorShape> shapes = new ArrayList<>();
		for (int k = 0; k < 2; k++) {
			int cx = slotX(13) + k * shift;
			int west = cx - 40;
			flat(level, new BoundingBox(cx - 44, GRASS - 2, cz - 44, cx + 44, 40, cz + 44));
			for (int x = cx - 42; x <= cx + 42; x++) {
				int top = -55 + Math.max(0, Math.min(79, x - west)) / 2;
				fill(level, new BoundingBox(x, GRASS, cz - 42, x, top - 1, cz + 42), Blocks.STONE.defaultBlockState());
				fill(level, new BoundingBox(x, top, cz - 42, x, top, cz + 42), Blocks.GRASS_BLOCK.defaultBlockState());
			}
			FloorSiting.Fit fit = FloorSiting.fit(Live.terrain(level), cx, cz, 64);
			helper.assertTrue(fit != null, "the slope does not fit");
			Start start = new Start(new BlockPos(cx, fit.floor(), cz), Direction.WEST, FloorShape.variant(64), 0x20FL);
			shapes.add((FloorShape) Live.place(level, S20.KIND, k == 0 ? start.withVersion(2) : start.withVersion(1)).shape());
		}
		List<List<String>> drops = new ArrayList<>();
		for (FloorShape shape : shapes) {
			BoundingBox square = shape.square();
			int f = shape.floorY();
			BoundingBox area = new BoundingBox(square.minX() - 4, -64, square.minZ() - 4, square.maxX() + 4, 320, square.maxZ() + 4);
			List<BlockPos> seeds = new ArrayList<>();
			for (int x = area.minX(); x <= area.maxX(); x++) {
				for (int z = area.minZ(); z <= area.maxZ(); z++) {
					int ring = shape.ringOf(x, z);
					// The floor (not the building's roof, never walked onto) and the land two and more out.
					if (ring == 0 && Edges.top(level, x, z) == f || ring >= 2) {
						seeds.add(new BlockPos(x, 0, z));
					}
				}
			}
			drops.add(Edges.unguardedDrops(level, area, seeds, (x, z) -> shape.ringOf(x, z) <= 1));
		}
		helper.assertTrue(drops.get(0).isEmpty(), "unguarded drops at version 2's square: " + drops.get(0));
		helper.assertTrue(!drops.get(1).isEmpty(), "version 1's square shows no drop: the slope does not test its edges");
		helper.assertTrue(shapes.get(0).version() == 2 && shapes.get(1).version() == 1, "the versions");
		helper.succeed();
	}

	/**
	 * The building in the middle, version 2 (R11-5): a cobblestone course on a cobblestone ring, a mossy cobblestone floor,
	 * two oak plank courses over it and the flat plank roof on them (inside 3 high), a window of two glass blocks at eye
	 * height in each wall, a doorway 1 x 2 with no door on the side its start faces, the two wall torches on the side walls
	 * a block in from it: lit inside, and the light spilling out of the doorway.
	 */
	@GameTest(maxTicks = 400)
	public void theBuildingHasItsCourseWallsPanesRoofDoorwayAndTwoTorches(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int cx = slotX(6);
		int cz = BASE_Z;
		int f = GRASS + 5;
		flat(level, new BoundingBox(cx - 36, GRASS - 2, cz - 36, cx + 36, f + 50, cz + 36));
		Live.Result built = Live.place(level, S20.KIND, new Start(new BlockPos(cx, f, cz), Direction.SOUTH, FloorShape.variant(64), 0x20BL).withVersion(2));
		FloorShape shape = (FloorShape) built.shape();
		Frame h = shape.house();
		int n = FloorShape.HOUSE;
		int roof = shape.roof();
		helper.assertTrue(shape.version() == 2 && roof == 4, "the version's roof: " + roof);
		List<String> wrong = new ArrayList<>();
		int panes = 0;
		for (int r = 0; r < n; r++) {
			for (int fw = 0; fw > -n; fw--) {
				boolean ring = r == 0 || r == n - 1 || fw == 0 || fw == -(n - 1);
				BlockState floor = level.getBlockState(h.pos(r, 0, fw));
				if (!floor.is(ring ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE)) {
					wrong.add("floor " + r + " " + fw + " " + floor.getBlock().getName().getString());
				}
				if (!level.getBlockState(h.pos(r, roof, fw)).is(Blocks.OAK_PLANKS) || !level.getBlockState(h.pos(r, roof + 1, fw)).isAir()) {
					wrong.add("roof " + r + " " + fw);
				}
				for (int u = 1; u < roof; u++) {
					BlockState state = level.getBlockState(h.pos(r, u, fw));
					boolean door = r == n / 2 && fw == 0 && u <= 2;
					if (state.is(Blocks.GLASS)) {
						panes++;
						if (u != 2 || !ring) {
							wrong.add("glass off eye height at " + r + " " + u + " " + fw);
						}
						continue;
					}
					if (ring && !door && !state.is(u == 1 ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS)) {
						wrong.add("wall " + r + " " + u + " " + fw + " " + state.getBlock().getName().getString());
					}
					if ((door || !ring) && !state.isAir() && !state.is(Blocks.WALL_TORCH)) {
						wrong.add("inside " + r + " " + u + " " + fw + " " + state.getBlock().getName().getString());
					}
				}
			}
		}
		helper.assertTrue(panes == 8, panes + " glass blocks, not two a wall");
		Direction facing = Direction.SOUTH;
		for (BlockPos at : shape.torches()) {
			BlockState torch = level.getBlockState(at);
			helper.assertTrue(torch.is(Blocks.WALL_TORCH) && torch.getValue(WallTorchBlock.FACING).getAxis() != facing.getAxis()
					&& at.equals(h.pos(at.equals(h.pos(1, 2, -1)) ? 1 : n - 2, 2, -1)), "a torch at " + at.toShortString() + ": " + torch);
		}
		helper.assertTrue(shape.entranceFacing() == facing && shape.entrance().equals(h.pos(n / 2, 1, 1)) && level.getBlockState(shape.entrance()).isAir(),
				"the doorway: " + shape.entrance());
		BlockPos doorway = h.pos(n / 2, 1, 0);
		helper.assertTrue(doorway.relative(facing).equals(shape.entrance()), "the doorway does not open toward its facing");
		Live.settleLight(level, built.box());
		// Lit inside, brightest by the doorway, and the light out of it onto the grass in front.
		helper.assertTrue(level.getBrightness(LightLayer.BLOCK, shape.anchor()) >= 8 && level.getBrightness(LightLayer.BLOCK, h.pos(n / 2, 1, -1)) >= 11,
				"dark inside: " + level.getBrightness(LightLayer.BLOCK, shape.anchor()));
		helper.assertTrue(level.getBrightness(LightLayer.BLOCK, shape.entrance()) >= 8, "no light out of the doorway: "
				+ level.getBrightness(LightLayer.BLOCK, shape.entrance()));
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * Worldgen's own path (the start saved and loaded, placed chunk by chunk and finished) equals the gallery's build block
	 * for block and records one {@code FLOOR} place whose inside is the air over the square, not the floor or high over it.
	 */
	@GameTest(environment = ENV20, maxTicks = 400)
	public void theSquareBuiltByWorldgenEqualsTheGallerysAndIsRecordedOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int cx = slotX(7);
		int cz = BASE_Z;
		int shift = 256;
		int f = GRASS + 5;
		Start start = new Start(new BlockPos(cx, f, cz), Direction.EAST, FloorShape.variant(64), 0x20CL);
		flat(level, new BoundingBox(cx - 40, GRASS - 2, cz - 40, cx + 40, f + 60, cz + 40));
		flat(level, new BoundingBox(cx - 40 + shift, GRASS - 2, cz - 40, cx + 40 + shift, f + 60, cz + 40));
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S20_KEY);
		Sc2Data data = new Sc2Data();
		Recorder.drain(new Sc2Data());
		try {
			StructureStart made = Live.startOf(level, holder, start);
			StructureStart loaded = Live.reload(level, made);
			helper.assertTrue(loaded != null && loaded.isValid(), "the start did not load back");
			Live.placeStart(level, loaded);
			helper.assertTrue(Recorder.drain(data) == 1 && data.places().size() == 1, "recorded " + data.places().size());
			Remains.Place place = data.places().getFirst().place();
			// The same terrain as worldgen's (the noise): the flat test world's noise is not its blocks.
			Terrain noise = Terrains.noise(level);
			Start moved = start.moved(shift, 0, 0);
			Live.Result live = Live.place(level, S20.KIND, S20.KIND.shapes().create(moved, noise), noise, moved);
			BoundingBox box = made.getBoundingBox();
			helper.assertTrue(live.box().equals(box.moved(shift, 0, 0)), "the boxes: " + box + " / " + live.box());
			List<String> differ = new ArrayList<>();
			BoundingBox around = box.inflatedBy(1);
			for (BlockPos pos : BlockPos.betweenClosed(around.minX(), Math.max(around.minY(), level.getMinY()), around.minZ(), around.maxX(), around.maxY(),
					around.maxZ())) {
				BlockState a = level.getBlockState(pos);
				BlockState b = level.getBlockState(pos.offset(shift, 0, 0));
				if (a != b && differ.size() < 8) {
					differ.add(pos.toShortString() + " " + a + " / " + b);
				}
			}
			helper.assertTrue(differ.isEmpty(), "worldgen and Live differ: " + differ);
			helper.assertTrue(place.kind() == Remains.Kind.FLOOR && place.entranceFacing() == Direction.EAST, "the place: " + place);
			RemainsImpl remains = RemainsImpl.over(data);
			helper.assertTrue(remains.containing(level, new BlockPos(cx - 20, f + 1, cz + 20)).isPresent(), "on the floor is not inside");
			helper.assertTrue(remains.containing(level, new BlockPos(cx - 20, f, cz + 20)).isEmpty(), "the floor itself is inside");
			helper.assertTrue(remains.containing(level, new BlockPos(cx - 20, f + 17, cz + 20)).isEmpty(), "17 over the floor is inside");
		} finally {
			Recorder.drain(new Sc2Data());
		}
		helper.succeed();
	}

	/** With {@code -Da1016_02.s19cost=true}: worldgen's own pass of a 96 square per chunk (the piece's {@code postProcess}). */
	@GameTest(environment = ENV20, maxTicks = 400)
	public void costPerChunkOfTheSquare(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.s19cost")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S20_KEY);
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		StringBuilder report = new StringBuilder();
		try {
			// Under the noise's ground (this test world's noise stands at 3, its blocks at -61): the whole square is cut.
			Start start = new Start(new BlockPos(slotX(11), -20, BASE_Z), Direction.NORTH, FloorShape.variant(96), 0x20FL);
			StructureStart made = Live.startOf(level, holder, start);
			BoundingBox box = made.getBoundingBox();
			load(level, box);
			fill(level, new BoundingBox(box.minX(), GRASS, box.minZ(), box.maxX(), 3, box.maxZ()), Blocks.STONE.defaultBlockState());
			List<Double> ms = new ArrayList<>();
			List<LevelChunk> chunks = new ArrayList<>();
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					ChunkPos chunk = new ChunkPos(cx, cz);
					LevelChunk levelChunk = level.getChunk(cx, cz);
					chunks.add(levelChunk);
					BoundingBox writable = new BoundingBox(chunk.getMinBlockX(), level.getMinY() + 1, chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(),
							chunk.getMaxBlockZ());
					net.minecraft.world.level.levelgen.WorldgenRandom random = new net.minecraft.world.level.levelgen.WorldgenRandom(
							new net.minecraft.world.level.levelgen.XoroshiroRandomSource(level.getSeed()));
					long t0 = System.nanoTime();
					made.placeInChunk(level, level.structureManager(), generator, random, writable, chunk);
					ms.add((System.nanoTime() - t0) / 1_000_000.0);
				}
			}
			for (LevelChunk chunk : chunks) {
				chunk.postProcessGeneration(level);
			}
			Collections.sort(ms);
			report.append(String.format(Locale.ROOT, "side 96: %d chunks, median %.2f ms, worst %.2f ms", ms.size(), ms.get(ms.size() / 2), ms.getLast()));
		} finally {
			Recorder.drain(new Sc2Data());
		}
		System.out.println("[a1016] s20 cost per chunk: " + report);
		helper.succeed();
	}

	/** The structure's override: a natural spawn of a creature anywhere in its box picks cows (and nothing else); outside it, the biome's. */
	@GameTest(maxTicks = 200)
	public void creaturesThatSpawnOnItAreCows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int cx = slotX(8);
		int cz = BASE_Z;
		int f = GRASS + 5;
		Start start = new Start(new BlockPos(cx, f, cz), Direction.EAST, FloorShape.variant(64), 0x20DL);
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S20_KEY);
		StructureStart made = Live.startOf(level, holder, start);
		BoundingBox box = made.getBoundingBox();
		load(level, box);
		ChunkPos home = made.getChunkPos();
		LevelChunk startChunk = level.getChunk(home.x(), home.z());
		startChunk.setStartForStructure(holder.value(), made);
		for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
			for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
				level.getChunk(x, z).addReferenceForStructure(holder.value(), home.pack());
			}
		}
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		WeightedList<MobSpawnSettings.SpawnerData> on = generator.getMobsAt(level, level.structureManager(), MobCategory.CREATURE, new BlockPos(cx - 10, f + 1, cz + 7));
		helper.assertTrue(!on.isEmpty(), "nothing spawns on it");
		for (Weighted<MobSpawnSettings.SpawnerData> entry : on.unwrap()) {
			helper.assertTrue(entry.value().type() == EntityTypes.COW, "a " + entry.value().type().toShortString() + " spawns on it");
		}
		WeightedList<MobSpawnSettings.SpawnerData> off = generator.getMobsAt(level, level.structureManager(), MobCategory.CREATURE,
				new BlockPos(box.maxX() + 40, f + 1, cz));
		helper.assertTrue(off.unwrap().stream().anyMatch(e -> e.value().type() != EntityTypes.COW), "only cows off it too: " + off.unwrap());
		helper.succeed();
	}

	// --- 20b ---

	/** A built square in a patch whose entities tick, with its answers' own rig. */
	private static final class Floor {
		final GameTestHelper helper;
		final ServerLevel level;
		final int cx;
		final int cz;
		final int f;
		final Live.Result built;
		final Remains.Place place;
		final Sc2Data data = new Sc2Data();
		final List<ServerPlayer> inside = new ArrayList<>();
		final List<ServerPlayer> online = new ArrayList<>();
		final List<String> answered = new ArrayList<>();
		final AtomicLong day = new AtomicLong(5);
		final Set<java.util.UUID> young = new HashSet<>();
		final Sc20Config config = new Sc20Config();
		final FloorAnswers answers;
		final List<Animal> on = new ArrayList<>();
		final List<Animal> off = new ArrayList<>();

		Floor(GameTestHelper helper, int slot) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.cx = slotX(slot);
			this.cz = BASE_Z;
			this.f = GRASS + 5;
			flat(level, new BoundingBox(cx - 48, GRASS - 2, cz - 48, cx + 48, f + 50, cz + 48));
			this.built = Live.place(level, S20.KIND, new Start(new BlockPos(cx, f, cz), Direction.WEST, FloorShape.variant(64), 0x20EL + slot).withVersion(2));
			this.place = built.place();
			data.add(built.known());
			config.faceSeconds = 3;
			Answers.Env env = new Answers.Env(s -> data, Services.traces().forced(), (l, cells) -> true, (s, p) -> List.copyOf(inside),
					uuid -> false, uuid -> young.contains(uuid) ? 1000L : 200_000L, s -> null, day::get, () -> level.getServer().getTickCount(),
					(l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString()));
			this.answers = new FloorAnswers(new FloorAnswers.Env(Answers.over(env), server -> List.copyOf(online), () -> config, Services::mobs));
			for (int cx2 = (cx - 48) >> 4; cx2 <= (cx + 48) >> 4; cx2++) {
				for (int cz2 = (cz - 48) >> 4; cz2 <= (cz + 48) >> 4; cz2++) {
					level.setChunkForced(cx2, cz2, true);
				}
			}
		}

		boolean ready() {
			for (int x = (cx - 48) >> 4; x <= (cx + 48) >> 4; x++) {
				for (int z = (cz - 48) >> 4; z <= (cz + 48) >> 4; z++) {
					if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, z))) {
						return false;
					}
				}
			}
			return true;
		}

		/** True once the entities of every chunk under the square are in place (found by a query), ticking or not. */
		boolean loaded() {
			for (int x = (cx - 48) >> 4; x <= (cx + 48) >> 4; x++) {
				for (int z = (cz - 48) >> 4; z <= (cz + 48) >> 4; z++) {
					if (!level.areEntitiesLoaded(new ChunkPos(x, z).pack())) {
						return false;
					}
				}
			}
			return true;
		}

		String why() {
			int all = 0;
			int loaded = 0;
			int ticking = 0;
			for (int x = (cx - 48) >> 4; x <= (cx + 48) >> 4; x++) {
				for (int z = (cz - 48) >> 4; z <= (cz + 48) >> 4; z++) {
					ChunkPos pos = new ChunkPos(x, z);
					all++;
					loaded += level.areEntitiesLoaded(pos.pack()) ? 1 : 0;
					ticking += level.areEntitiesActuallyLoadedAndTicking(pos) ? 1 : 0;
				}
			}
			return all + " chunks, " + loaded + " with entities loaded, " + ticking + " ticking, forced " + level.getForceLoadedChunks().size();
		}

		<T extends Animal> T spawn(EntityType<T> type, double x, double y, double z) {
			T mob = type.create(level, EntitySpawnReason.COMMAND);
			mob.snapTo(new Vec3(x, y, z), 0.0F, 0.0F);
			mob.setPersistenceRequired();
			level.addFreshEntity(mob);
			return mob;
		}

		/** Four cows and a pig on the floor, a cow just off it (below the built-up side) and one far off. */
		void herd() {
			on.add(spawn(EntityTypes.COW, cx - 15.5, f + 1, cz - 9.5));
			on.add(spawn(EntityTypes.COW, cx + 12.5, f + 1, cz + 14.5));
			on.add(spawn(EntityTypes.COW, cx - 25.5, f + 1, cz + 20.5));
			on.add(spawn(EntityTypes.COW, cx + 20.5, f + 1, cz - 22.5));
			on.add(spawn(EntityTypes.PIG, cx + 3.5, f + 1, cz - 18.5));
			off.add(spawn(EntityTypes.COW, cx - 35.5, GRASS + 1, cz + 3.5));
			off.add(spawn(EntityTypes.COW, cx + 44.5, GRASS + 1, cz - 40.5));
		}

		ServerPlayer player(String name, double x, double z) {
			ServerPlayer player = new SelfCreated2KitTests.TestPlayer(level, name, GameType.SURVIVAL);
			player.snapTo(new Vec3(x, f + 1, z), 0.0F, 0.0F);
			return player;
		}

		/** The live tamper lets go of mobs held for players the live server does not know: bound to nobody from here. */
		void unbind() {
			for (Animal animal : on) {
				MobTamperImpl.INSTANCE.bind(animal, List.of());
			}
		}

		void done() {
			for (Animal animal : on) {
				animal.discard();
			}
			for (Animal animal : off) {
				animal.discard();
			}
			for (int x = (cx - 48) >> 4; x <= (cx + 48) >> 4; x++) {
				for (int z = (cz - 48) >> 4; z <= (cz + 48) >> 4; z++) {
					level.setChunkForced(x, z, false);
				}
			}
		}
	}

	/**
	 * 20b: every free animal on the floor (cows and a pig) stops, falls silent and turns to the building's middle, none off
	 * it; all let go together when the time is up; a second ask the same day is refused.
	 */
	@GameTest(maxTicks = 1600)
	public void everyAnimalOnTheFloorFacesTheBuildingAndNoneOffIt(GameTestHelper helper) {
		Floor rig = new Floor(helper, 9);
		long[] until = new long[1];
		Vec3 middle = Vec3.atCenterOf(rig.place.anchor()).add(0.0, 0.7, 0.0);
		helper.startSequence().thenWaitUntil(com.forzacode.a1016_02.core.FarPatches.until(helper, rig::ready, () -> "the patch is not ticking yet: " + rig.why())).thenExecute(() -> {
			rig.herd();
			rig.inside.add(rig.player("ana", rig.cx - 6.5, rig.cz + 3.5));
		}).thenIdle(5).thenExecute(() -> {
			Answers.Result result = rig.answers.ask(rig.level, rig.place, false);
			helper.assertTrue(result.status() == Answers.Status.DONE, "20b: " + result);
			for (Animal animal : rig.on) {
				helper.assertTrue(MobTamperImpl.INSTANCE.isFrozen(animal) && MobTamperImpl.INSTANCE.isSilenced(animal), "a " + animal.getType().toShortString()
						+ " on the floor not held");
			}
			for (Animal animal : rig.off) {
				helper.assertTrue(!Services.mobs().isTampered(animal), "a cow off the floor held");
			}
			helper.assertTrue(rig.answered.equals(List.of("20b:ana")), "answered " + rig.answered);
			rig.unbind();
			until[0] = rig.level.getServer().getTickCount() + Math.round(rig.config.faceSeconds * 20);
			helper.onEachTick(() -> rig.answers.tick(rig.level.getServer()));
		}).thenIdle(40).thenExecute(() -> {
			for (Animal animal : rig.on) {
				float want = MobTamperImpl.yawToward(animal.position(), middle);
				helper.assertTrue(Math.abs(Mth.wrapDegrees(animal.getYHeadRot() - want)) <= 15.0F, "a " + animal.getType().toShortString() + " faces "
						+ animal.getYHeadRot() + ", not " + want);
			}
			Answers.Result again = rig.answers.ask(rig.level, rig.place, false);
			helper.assertTrue(again.status() == Answers.Status.REFUSED && again.reason().contains("today"), "again the same day: " + again);
		}).thenWaitUntil(() -> helper.assertTrue(rig.level.getServer().getTickCount() > until[0] + 2, "the time")).thenExecute(() -> {
			for (Animal animal : rig.on) {
				helper.assertTrue(!Services.mobs().isTampered(animal), "a " + animal.getType().toShortString() + " still held");
			}
			helper.assertTrue(rig.answers.holding() == 0, "still holding " + rig.answers.holding());
			rig.done();
		}).thenSucceed();
	}

	/**
	 * Two players on it: one ask turns them for both and counts it for both (and the event for each); a look asks only for a
	 * player near the building (not one in a far corner); the next day it comes again; anyone in their first hour holds it
	 * off for everyone; switched off, every held animal is let go at once.
	 */
	@GameTest(maxTicks = 1600)
	public void twoOnTheFloorShareItOnceADayAndTheSwitchLetsGo(GameTestHelper helper) {
		Floor rig = new Floor(helper, 10);
		// Nothing here needs the animals to move: their chunks only need their entities in place to be found.
		// The wait is in real time (core.FarPatches, SC2-9), as every far patch's: counted in ticks it races the chunk system.
		helper.startSequence().thenWaitUntil(com.forzacode.a1016_02.core.FarPatches.until(helper, rig::loaded, () -> "the patch (" + rig.why() + ")")).thenExecute(() -> {
			rig.herd();
		}).thenIdle(5).thenExecute(() -> {
			ServerPlayer ana = rig.player("ana", rig.cx - 30.5, rig.cz + 29.5);
			ServerPlayer bob = rig.player("bob", rig.cx + 29.5, rig.cz - 30.5);
			rig.online.add(ana);
			rig.online.add(bob);
			rig.inside.add(ana);
			rig.inside.add(bob);
			rig.answers.entered(rig.place, ana);
			rig.answers.entered(rig.place, bob);
			long now = rig.level.getServer().getTickCount();
			rig.answers.look(rig.level.getServer(), now);
			helper.assertTrue(rig.answered.isEmpty(), "asked with both in far corners: " + rig.answered);
			bob.snapTo(new Vec3(rig.cx + 10.5, rig.f + 1, rig.cz - 12.5), 0.0F, 0.0F);
			rig.answers.look(rig.level.getServer(), now + FloorAnswers.ASK_AGAIN_TICKS);
			helper.assertTrue(rig.answered.size() == 2 && rig.answered.containsAll(List.of("20b:ana", "20b:bob")), "answered " + rig.answered);
			helper.assertTrue(rig.data.spent(ana.getUUID(), FloorAnswers.FACE).on(5) == 1 && rig.data.spent(bob.getUUID(), FloorAnswers.FACE).on(5) == 1,
					"not counted for both");
			helper.assertTrue(rig.answers.holding() == rig.on.size(), "holding " + rig.answers.holding());
			rig.unbind();
			// The switch lets go of every one at once.
			rig.answers.switchedOff();
			for (Animal animal : rig.on) {
				helper.assertTrue(!Services.mobs().isTampered(animal), "a " + animal.getType().toShortString() + " held after the switch");
			}
			helper.assertTrue(rig.answers.ask(rig.level, rig.place, false).status() == Answers.Status.REFUSED, "again the same day");
			rig.day.set(6);
			ServerPlayer cat = rig.player("cat", rig.cx + 2.5, rig.cz + 2.5);
			rig.young.add(cat.getUUID());
			rig.inside.add(cat);
			Answers.Result young = rig.answers.ask(rig.level, rig.place, false);
			helper.assertTrue(young.status() == Answers.Status.REFUSED && young.reason().contains("first hour"), "with a newcomer in: " + young);
			rig.inside.remove(cat);
			Answers.Result next = rig.answers.ask(rig.level, rig.place, false);
			helper.assertTrue(next.status() == Answers.Status.DONE, "the next day: " + next);
			rig.unbind();
			rig.answers.switchedOff();
			rig.done();
		}).thenSucceed();
	}

	// --- 19 v4 and 19b (FIX-R3-B) ---

	/**
	 * 19 v4 (FIX-R3-B, R3A-4): every opening is the light: a light block in each of its four cells (waterlogged in its
	 * water) and on its sill cells either side of the wall; the lit foot's land pale (gravel or stone, never grass or
	 * coarse dirt). The same segment built as version 3 has no light in its openings.
	 */
	@GameTest(maxTicks = 600)
	public void version4LightsEveryOpeningAndPalesItsFoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int px = slotX(14) + 8;
		LinePlan.Segment s = LinePlan.cut(LinePlan.Arm.SOUTH, px, BASE_Z, 256, 0x5EEDL, 48, 9, level.getMinY() + 6, level.getMaxY() - 6);
		int pond = s.holes().get(1);
		flat(level, new BoundingBox(px - 6, GRASS, s.t0() - 4, px + 6, s.top(), s.t1() + 4));
		fill(level, new BoundingBox(px - 6, GRASS, pond - 6, px + 6, GRASS, pond + 7), Blocks.WATER.defaultBlockState());
		Terrains.Frozen terrain = Live.terrain(level);
		LineShape shape = new LineShape(s, terrain);
		helper.assertTrue(shape.version() == 4, "version " + shape.version());
		Live.place(level, S19.KIND, shape, terrain, s.start());
		Live.settleLight(level, shape.box());
		List<String> wrong = new ArrayList<>();
		for (int hole : s.holes()) {
			boolean wet = hole == pond;
			int low = wet ? GRASS : GRASS + 1;
			for (int t = hole; t <= hole + 1; t++) {
				for (int y = low; y <= low + 1; y++) {
					BlockState at = level.getBlockState(new BlockPos(px, y, t));
					if (!com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(at)
							|| at.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) != (wet && y == low)) {
						wrong.add("opening " + t + " y " + y + ": " + at);
					}
				}
				for (int dx : new int[] {-1, 1}) {
					if (!com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(level.getBlockState(new BlockPos(px + dx, low, t)))) {
						wrong.add("sill " + dx + " at " + t);
					}
				}
			}
			// The ring of stone round it in the wall, lit on both faces (12 or more in front of it over the sill row).
			for (BlockPos cell : shape.ring(hole, low)) {
				// Where the wall stood: on the flat test world it stands on the grass, so the sill row is the land's.
				if (cell.getY() >= low && !level.getBlockState(cell).is(Blocks.STONE)) {
					wrong.add("ring " + cell.toShortString() + ": " + level.getBlockState(cell));
				}
				for (int dx : new int[] {-1, 1}) {
					BlockPos front = cell.offset(dx, 0, 0);
					if (cell.getY() >= low && level.getBlockState(front).isAir() && level.getBrightness(LightLayer.BLOCK, front) < 12) {
						wrong.add("ring unlit " + front.toShortString() + " " + level.getBrightness(LightLayer.BLOCK, front));
					}
				}
			}
			for (BoundingBox square : wet ? List.<BoundingBox>of() : shape.litSquares(hole)) {
				for (int x = square.minX(); x <= square.maxX(); x++) {
					for (int z = square.minZ(); z <= square.maxZ(); z++) {
						BlockState top = level.getBlockState(new BlockPos(x, GRASS, z));
						if (!level.getBlockState(new BlockPos(x, GRASS + 1, z)).is(Blocks.OBSIDIAN) && !top.is(Blocks.GRAVEL) && !top.is(Blocks.STONE)
								&& wrong.size() < 12) {
							wrong.add("the foot " + x + " " + z + ": " + top);
						}
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "version 4: " + wrong);
		// Version 3 of the same segment, 64 east: nothing lit in its openings.
		LinePlan.Segment s3 = LinePlan.cut(LinePlan.Arm.SOUTH, px + 64, BASE_Z, 256, 0x5EEDL, 48, 9, level.getMinY() + 6, level.getMaxY() - 6);
		flat(level, new BoundingBox(px + 58, GRASS, s3.t0() - 4, px + 70, s3.top(), s3.t1() + 4));
		Terrains.Frozen terrain3 = Live.terrain(level);
		Live.place(level, S19.KIND, new LineShape(s3, terrain3, 3), terrain3, s3.start());
		for (int hole : s3.holes()) {
			for (int t = hole; t <= hole + 1; t++) {
				for (int y = GRASS; y <= GRASS + 3; y++) {
					if (com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(level.getBlockState(new BlockPos(px + 64, y, t)))) {
						wrong.add("version 3 lit its opening at " + t);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "version 3: " + wrong);
		helper.succeed();
	}

	/** The lights round an opening of a line on the flat test world (its squares, sills and wall foot). */
	private static int lightsRound(ServerLevel level, int px, int hole) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(px - LineShape.LIT_REACH, GRASS - 1, hole - 5, px + LineShape.LIT_REACH, GRASS + 12, hole + 6)) {
			n += com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(level.getBlockState(pos)) ? 1 : 0;
		}
		return n;
	}

	/** True if every cell of the opening at {@code hole} on the flat test world is open (no wall in it). */
	private static boolean openAt(ServerLevel level, int px, int hole) {
		for (int t = hole; t <= hole + 1; t++) {
			for (int y = GRASS + 1; y <= GRASS + 2; y++) {
				if (!com.forzacode.a1016_02.world.sc2.s19.LineAnswers.open(level.getBlockState(new BlockPos(px, y, t)))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 19b (FIX-R3-B): one standing in an opening: out of view, the openings next to theirs along the wall, one each way,
	 * are wall again (obsidian in their four cells, the lights round them gone, ledgered); theirs stays open; nothing
	 * while asleep or while seen; never an opening with anyone within 16 of it; never under 10 health; once a line a day;
	 * open again on a later day (the ledger undone), and given back.
	 */
	@GameTest(maxTicks = 600)
	public void theOpeningsBesideTheOneYouStandInMendOutOfView(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.MinecraftServer server = level.getServer();
		int px = slotX(15) + 8;
		LinePlan.Segment s = LinePlan.cut(LinePlan.Arm.SOUTH, px, BASE_Z, 512, 0x19B0L, 48, 9, level.getMinY() + 6, level.getMaxY() - 6);
		flat(level, new BoundingBox(px - 6, GRASS, s.t0() - 4, px + 6, s.top(), s.t1() + 4));
		Terrains.Frozen terrain = Live.terrain(level);
		LineShape shape = new LineShape(s, terrain);
		Live.Result built = Live.place(level, S19.KIND, shape, terrain, s.start());
		Remains.Place place = built.place();
		List<Integer> holes = s.holes();
		helper.assertTrue(holes.size() >= 5, "openings " + holes);
		Sc2Data data = new Sc2Data();
		data.add(built.known());
		java.util.concurrent.atomic.AtomicBoolean seen = new java.util.concurrent.atomic.AtomicBoolean(false);
		java.util.concurrent.atomic.AtomicReference<String> story = new java.util.concurrent.atomic.AtomicReference<>("asleep");
		long today = com.forzacode.a1016_02.core.GameClock.day(server);
		AtomicLong day = new AtomicLong(today);
		AtomicLong now = new AtomicLong(0);
		List<String> answered = Collections.synchronizedList(new ArrayList<>());
		ServerPlayer ana = new SelfCreated2KitTests.TestPlayer(level, "ana", GameType.SURVIVAL);
		ana.setHealth(20.0F);
		ana.snapTo(new Vec3(px + 0.5, GRASS + 1, holes.get(2) + 1.0), 0.0F, 0.0F);
		List<ServerPlayer> inside = new ArrayList<>(List.of(ana));
		List<ServerPlayer> people = new ArrayList<>(List.of(ana));
		Answers.Env env = new Answers.Env(k -> data, Services.traces().forced(), (l, cells) -> !seen.get(), (k, p) -> List.copyOf(inside), uuid -> false,
				uuid -> 200_000L, k -> story.get(), day::get, now::get, (l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString()));
		Answers answers = Answers.over(env);
		com.forzacode.a1016_02.world.sc2.s19.LineAnswers line = new com.forzacode.a1016_02.world.sc2.s19.LineAnswers(
				new com.forzacode.a1016_02.world.sc2.s19.LineAnswers.Env(answers, k -> List.copyOf(people), p -> s, k -> day.get(), () -> Services.traces().forced()));
		line.entered(place, ana);
		int lit = lightsRound(level, px, holes.get(1));
		helper.assertTrue(lit > 4, "lights round the opening " + lit);
		// Asleep: nothing.
		long t = 1000;
		now.set(t);
		line.look(server, t);
		answers.tryAll(server);
		for (int hole : holes) {
			helper.assertTrue(openAt(level, px, hole), "asleep, the opening at " + hole + " mended");
		}
		// Awake but seen: it waits; out of view, done.
		story.set(null);
		seen.set(true);
		t += 400;
		now.set(t);
		line.look(server, t);
		helper.assertTrue(openAt(level, px, holes.get(1)) && answers.waiting(place, "19b"), "seen: " + answers.whyWaiting(place, "19b"));
		seen.set(false);
		answers.tryAll(server);
		helper.assertTrue(!openAt(level, px, holes.get(1)) && !openAt(level, px, holes.get(3)), "the openings beside not mended");
		helper.assertTrue(level.getBlockState(new BlockPos(px, GRASS + 1, holes.get(1))).is(Blocks.OBSIDIAN), "not wall again");
		for (BlockPos cell : shape.ring(holes.get(1), GRASS + 1)) {
			helper.assertTrue(cell.getY() <= GRASS || level.getBlockState(cell).is(Blocks.OBSIDIAN), "its ring of stone left at " + cell.toShortString());
		}
		helper.assertTrue(level.getBlockState(shape.ring(holes.get(2), GRASS + 1).get(1)).is(Blocks.STONE), "the ring of the one stood in mended");
		helper.assertTrue(openAt(level, px, holes.get(2)) && openAt(level, px, holes.get(0)) && openAt(level, px, holes.get(4)), "another opening mended");
		helper.assertTrue(lightsRound(level, px, holes.get(1)) == 0 && lightsRound(level, px, holes.get(3)) == 0 && lightsRound(level, px, holes.get(2)) > 4,
				"the lights round the mended openings");
		helper.assertTrue(answered.equals(List.of("19b:ana")), "answered " + answered);
		// Once a line a day: another in the fourth opening the same day mends nothing more.
		ServerPlayer bo = new SelfCreated2KitTests.TestPlayer(level, "bo", GameType.SURVIVAL);
		bo.setHealth(20.0F);
		bo.snapTo(new Vec3(px + 0.5, GRASS + 1, holes.get(4) + 1.0), 0.0F, 0.0F);
		inside.clear();
		inside.add(bo);
		people.add(bo);
		line.left(place, ana);
		line.entered(place, bo);
		t += 400;
		now.set(t);
		line.look(server, t);
		answers.tryAll(server);
		helper.assertTrue(holes.size() < 6 || openAt(level, px, holes.get(5)), "a second the same day");
		// The same day nothing opens again; a later day the mended ones open (the ledger undone).
		line.reopen(server);
		helper.assertTrue(!openAt(level, px, holes.get(1)), "opened the same day");
		day.set(today + 1);
		line.reopen(server);
		helper.assertTrue(openAt(level, px, holes.get(1)) && openAt(level, px, holes.get(3)) && lightsRound(level, px, holes.get(1)) == lit,
				"not open again: " + lightsRound(level, px, holes.get(1)) + " of " + lit + " lights");
		for (BlockPos cell : shape.ring(holes.get(1), GRASS + 1)) {
			helper.assertTrue(cell.getY() <= GRASS || level.getBlockState(cell).is(Blocks.STONE), "its ring not back at " + cell.toShortString());
		}
		// The next day: bo under 10 health, nothing; healed, but ana near the third opening: only the fifth (bo's other side) mends.
		bo.setHealth(8.0F);
		t += 400;
		now.set(t);
		line.look(server, t);
		answers.tryAll(server);
		helper.assertTrue(openAt(level, px, holes.get(3)), "under 10 health");
		bo.setHealth(20.0F);
		ana.snapTo(new Vec3(px + 6.5, GRASS + 1, holes.get(3) + 9.0), 0.0F, 0.0F);
		t += 400;
		now.set(t);
		line.look(server, t);
		answers.tryAll(server);
		helper.assertTrue(openAt(level, px, holes.get(3)) && openAt(level, px, holes.get(4)), "mended one with someone near it, or the one stood in");
		helper.assertTrue(holes.size() < 6 || !openAt(level, px, holes.get(5)), "the far side not mended");
		// Given back: every mended cell open again through the ledger.
		for (com.forzacode.a1016_02.core.TraceLedger.Entry entry : List.copyOf(com.forzacode.a1016_02.core.TraceLedger.get(server).entries())) {
			if (entry.cause().equals(com.forzacode.a1016_02.world.sc2.s19.LineAnswers.CLOSE) && built.box().inflatedBy(LineShape.LIT_REACH).isInside(entry.pos().pos())) {
				Services.traces().forced().undo(level, entry);
			}
		}
		for (int hole : holes) {
			helper.assertTrue(openAt(level, px, hole), "not given back at " + hole);
		}
		helper.succeed();
	}
}
