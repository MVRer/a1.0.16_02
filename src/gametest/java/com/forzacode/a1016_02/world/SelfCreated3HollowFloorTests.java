package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Decided;
import com.forzacode.a1016_02.world.sc2.kit.Fault;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.NoiseVolume;
import com.forzacode.a1016_02.world.sc2.kit.Overlay;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.s18.HollowAnswers;
import com.forzacode.a1016_02.world.sc2.s18.HollowShape;
import com.forzacode.a1016_02.world.sc2.s18.S18;
import com.forzacode.a1016_02.world.sc2.s18.Sc18Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.Vec3;

/**
 * R3-3's game tests (docs/SELF_CREATED_2_R3.md, "R3-3 {@code sc3/hollow}"): the hollow's version 3 (the summit cut, the
 * piece hanging, the foot) and 18c, the floor's version 3 (fullbright, up to 192) and 20b v2; and, when asked, the two
 * kinds' find and siting cost over the real noise ({@code -Da1016_02.sc3hollow=N}, N worlds).
 */
public class SelfCreated3HollowFloorTests {
	private static final String HEIGHTS = "a1016_02:world/sc2_heights";

	// --- 18 v3: the summit, the piece, the foot ---

	/** A test mountain's start of the current version (siting's variant: the window off, spruce, the config's cut). */
	private static Start start18(SelfCreated2HollowTests.Hill hill, Direction facing, long seed) {
		Sc18Config config = new Sc18Config();
		int variant = HollowShape.variant(false, true, config.shellTop, config.shellSide, config.pillarEvery, 11, config.summitCutFor(seed));
		return new Start(hill.middle().above(2), facing, variant, seed);
	}

	/** What a built 18 v3 is: its shape, what stood over its cut before it was built (by column, from the cut up), its record. */
	private record Built18(HollowShape shape, Map<Long, List<BlockState>> above, Live.Result result) {
	}

	private static Built18 build18(ServerLevel level, int x, int z, Direction facing, long seed) {
		return build18(level, x, z, facing, seed, HollowShape.VERSION);
	}

	private static Built18 build18(ServerLevel level, int x, int z, Direction facing, long seed, int version) {
		SelfCreated2HollowTests.Hill hill = SelfCreated2HollowTests.hill(level, x, z, facing, 104, 112, 14, false);
		Start start = start18(hill, facing, seed).withVersion(version);
		com.forzacode.a1016_02.world.sc2.kit.Terrains.Frozen frozen = Live.terrain(level);
		HollowShape shape = new HollowShape(start, frozen);
		frozen.freeze(shape.box());
		Map<Long, List<BlockState>> above = new java.util.HashMap<>();
		HollowShape.Summit s = shape.summit();
		if (s != null) {
			for (int ax = s.minX(); ax < s.minX() + 16; ax++) {
				for (int az = s.minZ(); az < s.minZ() + 16; az++) {
					List<BlockState> column = new ArrayList<>();
					for (int y = s.cut() + 1; y <= s.top() + 30; y++) {
						column.add(level.getBlockState(new BlockPos(ax, y, az)));
					}
					above.put(BlockPos.asLong(ax, 0, az), column);
				}
			}
		}
		Live.Result result = Live.place(level, S18.KIND, shape, frozen, start);
		Live.settleLight(level, result.box());
		return new Built18(shape, above, result);
	}

	/**
	 * 18 v3's summit (R3-3): a chunk-aligned square on the way in's side, every block over the cut gone, the land round it
	 * guarded where it stands over the cut (world.Edges), the inside's shell kept under the cut; the piece hanging under
	 * it is exactly what was cut, upside down (the loose things left out), floating (nothing round it or over it), lit
	 * from under (block light 13 or more under its lowest blocks on the fault's grid).
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 600)
	public void theSummitIsCutSquareAndFairAndItsPieceHangsUnderItLit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Built18 built = build18(level, 261_008, -261_008, Direction.EAST, 0x18C0L, 3);
		HollowShape shape = built.shape();
		HollowShape.Summit s = shape.summit();
		helper.assertTrue(shape.valid() && shape.version() == 3 && s != null, "no summit: " + s);
		helper.assertTrue(Math.floorMod(s.minX(), 16) == 0 && Math.floorMod(s.minZ(), 16) == 0, "not chunk-aligned: " + s);
		helper.assertTrue(s.top() - s.cut() >= 8 && s.top() - s.cut() <= 20, "the cut's depth " + (s.top() - s.cut()));
		helper.assertTrue((s.minX() + 8 - shape.groveCenter().getX()) * Direction.EAST.getStepX() >= 0, "not on the way in's side: " + s);
		List<String> wrong = new ArrayList<>();
		for (int x = s.minX(); x < s.minX() + 16; x++) {
			for (int z = s.minZ(); z < s.minZ() + 16; z++) {
				for (int y = s.cut() + 1; y <= s.top() + 30; y++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir() && wrong.size() < 6) {
						wrong.add("over the cut " + x + " " + y + " " + z);
					}
				}
				for (int y = s.cut() - new Sc18Config().shellTop + 1; y <= s.cut(); y++) {
					if (shape.taken(x, y, z) && wrong.size() < 6) {
						wrong.add("thin shell " + x + " " + y + " " + z);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the cut: " + wrong);
		// Fair: from the square and the land round it, no step down over 3 unguarded.
		BoundingBox area = new BoundingBox(s.minX() - 6, s.cut() - 40, s.minZ() - 6, s.minX() + 21, s.top() + 40, s.minZ() + 21);
		// Walkers from the cut's floor and from the land round it (3 to 6 out), never set down on the lip itself.
		List<BlockPos> seeds = new ArrayList<>();
		for (int x = s.minX() - 6; x <= s.minX() + 21; x++) {
			for (int z = s.minZ() - 6; z <= s.minZ() + 21; z++) {
				int ring = Math.max(Math.max(s.minX() - x, x - s.minX() - 15), Math.max(s.minZ() - z, z - s.minZ() - 15));
				if (ring <= 0 || ring >= 3) {
					seeds.add(new BlockPos(x, 0, z));
				}
			}
		}
		List<String> drops = Edges.unguardedDrops(level, area, seeds, (x, z) -> x >= s.minX() && x < s.minX() + 16 && z >= s.minZ() && z < s.minZ() + 16);
		if (!drops.isEmpty()) {
			StringBuilder column = new StringBuilder();
			int dx = s.minX() - 1;
			int dz = s.minZ() + 1;
			for (int y = s.cut() + 24; y >= s.cut() + 14; y--) {
				BlockState at = level.getBlockState(new BlockPos(dx, y, dz));
				column.append(y).append(at.isAir() ? "a" : at.getBlock().getDescriptionId()).append(' ');
			}
			helper.fail("unguarded: " + drops + "; summit " + s + ", floor " + shape.floorY() + ", column " + dx + " " + dz + ": " + column);
		}
		// The piece: what was cut, upside down, under the cut, floating, lit from under.
		helper.assertTrue(s.hasPiece() && s.pieceTop() - s.pieceHeight() + 1 - shape.floorY() >= 10, "the piece: " + s + ", the floor " + shape.floorY());
		int blocks = 0;
		int lit = 0;
		int dim = 0;
		for (int x = s.minX(); x < s.minX() + 16; x++) {
			for (int z = s.minZ(); z < s.minZ() + 16; z++) {
				List<BlockState> was = built.above().get(BlockPos.asLong(x, 0, z));
				int lowest = Integer.MAX_VALUE;
				for (int i = 0; i < s.pieceHeight(); i++) {
					int k = s.pieceSkip() + i;
					BlockState expected = k < was.size() ? HollowShape.pieceState(was.get(k)) : Blocks.AIR.defaultBlockState();
					BlockState now = level.getBlockState(new BlockPos(x, s.pieceTop() - i, z));
					if (!expected.isAir()) {
						blocks++;
						lowest = Math.min(lowest, s.pieceTop() - i);
						if (now != expected && wrong.size() < 6) {
							wrong.add("piece " + x + " " + (s.pieceTop() - i) + " " + z + ": " + now + " for " + expected);
						}
					} else if (!now.isAir() && !Fault.isLight(now) && wrong.size() < 6) {
						wrong.add("in the piece's air " + x + " " + (s.pieceTop() - i) + " " + z + ": " + now);
					}
				}
				if (!level.getBlockState(new BlockPos(x, s.pieceTop() + 1, z)).isAir() && wrong.size() < 6) {
					wrong.add("over the piece " + x + " " + z);
				}
				boolean onGrid = Math.floorMod(x - s.minX() - Fault.INSET, Fault.GRID) == 0 && Math.floorMod(z - s.minZ() - Fault.INSET, Fault.GRID) == 0;
				if (onGrid && lowest != Integer.MAX_VALUE) {
					int light = level.getBrightness(LightLayer.BLOCK, new BlockPos(x, lowest - 1, z));
					lit += light >= 13 ? 1 : 0;
					dim += light >= 13 ? 0 : 1;
				}
			}
		}
		for (int x = s.minX() - 1; x <= s.minX() + 16; x++) {
			for (int z = s.minZ() - 1; z <= s.minZ() + 16; z++) {
				boolean ring = x == s.minX() - 1 || x == s.minX() + 16 || z == s.minZ() - 1 || z == s.minZ() + 16;
				for (int y = s.pieceTop() - s.pieceHeight(); ring && y <= s.pieceTop(); y++) {
					BlockState beside = level.getBlockState(new BlockPos(x, y, z));
					if (!beside.isAir() && !Fault.isLight(beside) && wrong.size() < 6) {
						wrong.add("beside the piece " + x + " " + y + " " + z + ": " + beside);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the piece: " + wrong);
		helper.assertTrue(blocks > 64 && lit > 0 && dim == 0, "the piece: " + blocks + " blocks, " + lit + " lit under, " + dim + " dim");
		helper.succeed();
	}

	/**
	 * 18 v3's foot: two chunks by two, chunk-aligned, round the way in's mouth; its top dead slope (coarse dirt and
	 * gravel, no grass, plants or snow) and lit as at noon (the fault's lights on its grid; the mouth's cell 13 or more).
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 600)
	public void theFootIsDeadSlopeUnderTheLightingFault(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Built18 built = build18(level, 265_008, -265_008, Direction.SOUTH, 0x18C1L, 3);
		HollowShape shape = built.shape();
		BoundingBox foot = shape.foot();
		helper.assertTrue(foot != null && Math.floorMod(foot.minX(), 16) == 0 && Math.floorMod(foot.minZ(), 16) == 0 && foot.getXSpan() == 32 && foot.getZSpan() == 32,
				"the foot: " + foot);
		BlockPos mouth = shape.entrance();
		helper.assertTrue(mouth.getX() >= foot.minX() + 4 && mouth.getX() <= foot.maxX() - 4 && mouth.getZ() >= foot.minZ() + 4 && mouth.getZ() <= foot.maxZ() - 4,
				"the mouth " + mouth.toShortString() + " not in the foot's middle: " + foot);
		int columns = 0;
		int dead = 0;
		Map<String, Integer> kinds = new java.util.TreeMap<>();
		int lights = 0;
		List<String> wrong = new ArrayList<>();
		for (int x = foot.minX(); x <= foot.maxX(); x++) {
			for (int z = foot.minZ(); z <= foot.maxZ(); z++) {
				columns++;
				int top = Edges.top(level, x, z);
				BlockState state = level.getBlockState(new BlockPos(x, top, z));
				dead += state.is(Blocks.GRAVEL) || state.is(Blocks.COARSE_DIRT) ? 1 : 0;
				if (!state.is(Blocks.GRAVEL) && !state.is(Blocks.COARSE_DIRT)) {
					kinds.merge(state.getBlock().getDescriptionId() + "@" + (top - shape.floorY()), 1, Integer::sum);
				}
				if ((state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SNOW)) && wrong.size() < 6) {
					wrong.add(x + " " + top + " " + z + " " + state);
				}
				for (int y = top + 1; y <= top + 2; y++) {
					lights += Fault.isLight(level.getBlockState(new BlockPos(x, y, z))) ? 1 : 0;
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "living top in the foot: " + wrong);
		helper.assertTrue(dead * 10 >= columns * 6, "dead slope " + dead + " of " + columns + ": " + kinds);
		helper.assertTrue(lights >= 80, "the foot's lights: " + lights);
		helper.assertTrue(level.getBrightness(LightLayer.BLOCK, mouth.relative(shape.facing())) >= 13, "the mouth's light "
				+ level.getBrightness(LightLayer.BLOCK, mouth.relative(shape.facing())));
		helper.succeed();
	}

	/**
	 * 18 v4 (FIX-R3-B, R3A-2): the bite two chunks wide where the neighbour across the way in's line stands high enough,
	 * chunk-aligned, everything over its floor gone; lit as at noon on its floor (10 or more on nine in ten of its top
	 * cells) and up its faces (lights against the land outside, the face beside them lit 10 or more); fair (world.Edges);
	 * and the piece still hangs under the square.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 600)
	public void theBiteIsTwoChunksWideLitOnItsFloorAndUpItsFacesAndFair(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Built18 built = build18(level, 277_008, -277_008, Direction.EAST, 0x18C0L);
		HollowShape shape = built.shape();
		HollowShape.Summit s = shape.summit();
		helper.assertTrue(shape.valid() && shape.version() == 4 && s != null && s.hasPiece(), "no summit with its piece: " + s);
		BoundingBox b = s.bite();
		helper.assertTrue(s.wide() && Fault.chunkAligned(new BoundingBox(b.minX(), 0, b.minZ(), b.maxX(), 0, b.maxZ())) && b.getXSpan() * b.getZSpan() == 512
				&& (b.getXSpan() == 32 ? Direction.EAST.getAxis() == Direction.Axis.Z : Direction.EAST.getAxis() == Direction.Axis.X), "the bite: " + b + " of " + s);
		List<String> wrong = new ArrayList<>();
		int cells = 0;
		int lit = 0;
		int faceLights = 0;
		int faceLit = 0;
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				for (int y = s.cut() + 1; y <= s.top() + 30; y++) {
					BlockState at = level.getBlockState(new BlockPos(x, y, z));
					if (!at.isAir() && !Fault.isLight(at) && wrong.size() < 6) {
						wrong.add("over the cut " + x + " " + y + " " + z + ": " + at);
					}
					boolean edge = x == b.minX() || x == b.maxX() || z == b.minZ() || z == b.maxZ();
					if (edge && y >= s.cut() + 4 && Fault.isLight(at)) {
						faceLights++;
						int ox = x == b.minX() ? x - 1 : x == b.maxX() ? x + 1 : x;
						int oz = ox != x ? z : z == b.minZ() ? z - 1 : z + 1;
						// Against the land outside: the face it lights.
						faceLit += !level.getBlockState(new BlockPos(ox, y, oz)).isAir() ? 1 : 0;
					}
				}
				cells++;
				lit += level.getBrightness(LightLayer.BLOCK, new BlockPos(x, s.cut() + 1, z)) >= Fault.MIN_LIT ? 1 : 0;
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the bite: " + wrong);
		helper.assertTrue(lit * 10 >= cells * 9, "the bite's floor lit " + lit + " of " + cells);
		helper.assertTrue(faceLights >= 6 && faceLit * 10 >= faceLights * 9, "the bite's faces: " + faceLights + " lights, " + faceLit + " against the land");
		// Fair: from the bite's floor and the land round it, no step down over 3 unguarded.
		BoundingBox area = new BoundingBox(b.minX() - 6, s.cut() - 40, b.minZ() - 6, b.maxX() + 6, s.top() + 40, b.maxZ() + 6);
		List<BlockPos> seeds = new ArrayList<>();
		for (int x = area.minX(); x <= area.maxX(); x++) {
			for (int z = area.minZ(); z <= area.maxZ(); z++) {
				int ring = Math.max(Math.max(b.minX() - x, x - b.maxX()), Math.max(b.minZ() - z, z - b.maxZ()));
				if (ring <= 0 || ring >= 3) {
					seeds.add(new BlockPos(x, 0, z));
				}
			}
		}
		List<String> drops = Edges.unguardedDrops(level, area, seeds, (x, z) -> x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ());
		helper.assertTrue(drops.isEmpty(), "unguarded: " + drops + "; bite " + b + ", summit " + s);
		helper.succeed();
	}

	/**
	 * 18 v4's foot (FIX-R3-B, R3A-2): grey, gravel and the land's stone, never coarse dirt (block light reads it brown and
	 * dim); lights carried up the slope's faces toward the viewer (over the top light, under the land behind), none
	 * within 3 of the mouth.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 600)
	public void theFootIsGreyAndItsLightsClimbTheSlopeBesideTheMouth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Built18 built = build18(level, 281_008, -281_008, Direction.SOUTH, 0x18C1L);
		HollowShape shape = built.shape();
		BoundingBox foot = shape.foot();
		helper.assertTrue(shape.version() == 4 && foot != null, "the foot: " + foot);
		BlockPos mouth = shape.entrance();
		int columns = 0;
		int grey = 0;
		int climbing = 0;
		List<String> wrong = new ArrayList<>();
		for (int x = foot.minX(); x <= foot.maxX(); x++) {
			for (int z = foot.minZ(); z <= foot.maxZ(); z++) {
				columns++;
				int top = Edges.top(level, x, z);
				BlockState state = level.getBlockState(new BlockPos(x, top, z));
				grey += state.is(Blocks.GRAVEL) || state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)
						|| state.is(Blocks.TUFF) || state.is(Blocks.CALCITE) ? 1 : 0;
				if ((state.is(Blocks.COARSE_DIRT) || state.is(Blocks.GRASS_BLOCK)) && wrong.size() < 6) {
					wrong.add(x + " " + top + " " + z + " " + state);
				}
				for (int y = top + 3; y <= top + 8; y++) {
					if (Fault.isLight(level.getBlockState(new BlockPos(x, y, z)))) {
						climbing++;
						if (Math.max(Math.abs(x - mouth.getX()), Math.abs(z - mouth.getZ())) <= 3 && wrong.size() < 6) {
							wrong.add("a light up the face by the mouth " + x + " " + y + " " + z);
						}
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the foot: " + wrong);
		helper.assertTrue(grey * 10 >= columns * 6, "grey " + grey + " of " + columns);
		helper.assertTrue(climbing >= 4, "lights up the slope: " + climbing);
		helper.succeed();
	}

	/** R12-2: a start tied to version 2 builds version 2 (no summit, no piece, no foot, no lights); the floor's the same. */
	@GameTest(environment = HEIGHTS, maxTicks = 600)
	public void startsOfVersion2StillBuildVersion2(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SelfCreated2HollowTests.Hill hill = SelfCreated2HollowTests.hill(level, 269_008, -269_008, Direction.EAST, 104, 112, 14, false);
		Start start = start18(hill, Direction.EAST, 0x18C2L).withVersion(2);
		Live.Result built = Live.place(level, S18.KIND, start);
		HollowShape shape = (HollowShape) built.shape();
		helper.assertTrue(shape.version() == 2 && shape.summit() == null && shape.foot() == null, "version 2 built " + shape.version());
		helper.assertTrue(lights(level, built.box()) == 0, "lights in a version 2 hollow");
		helper.succeed();
	}

	/** The fault's lights in a box. */
	static int lights(ServerLevel level, BoundingBox box) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			n += Fault.isLight(level.getBlockState(pos)) ? 1 : 0;
		}
		return n;
	}

	// --- 18c ---

	/** A built 18 v3 with an answer of its own: the kit's answers over a world of the test's own, who is inside, the view, the day. */
	private record Rig18(HollowAnswers hollow, Answers answers, Remains.Place place, HollowShape shape, BoundingBox piece, List<ServerPlayer> inside,
			List<ServerPlayer> people, AtomicBoolean seen, AtomicLong day, AtomicLong now, List<String> answered, Sc18Config config) {
		void at(MinecraftServer server, long ticks) {
			now.set(ticks);
			hollow.look(server, ticks);
			answers.tryAll(server);
		}
	}

	private static Rig18 rig18(ServerLevel level, int x, int z, long seed) {
		Built18 built = build18(level, x, z, Direction.EAST, seed);
		Sc2Data data = new Sc2Data();
		data.add(built.result().known());
		List<ServerPlayer> inside = new ArrayList<>();
		List<ServerPlayer> people = new ArrayList<>();
		AtomicBoolean seen = new AtomicBoolean(false);
		AtomicLong day = new AtomicLong(50);
		AtomicLong now = new AtomicLong(0);
		List<String> answered = Collections.synchronizedList(new ArrayList<>());
		Answers.Env env = new Answers.Env(s -> data, Services.traces().forced(), (l, cells) -> !seen.get(), (s, p) -> List.copyOf(inside), uuid -> false,
				uuid -> 200_000L, s -> null, day::get, now::get, (l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString()));
		Answers answers = Answers.over(env);
		Sc18Config config = new Sc18Config();
		BoundingBox piece = HollowShape.pieceOf(built.result().known().extra()).orElseThrow();
		HollowAnswers hollow = new HollowAnswers(new HollowAnswers.Env(answers, server -> List.copyOf(people), () -> config, place -> null, place -> piece, s -> day.get()));
		return new Rig18(hollow, answers, built.result().place(), built.shape(), piece, inside, people, seen, day, now, answered, config);
	}

	private static ServerPlayer player(ServerLevel level, String name, BlockPos feet) {
		ServerPlayer player = new SelfCreated2KitTests.TestPlayer(level, name, GameType.SURVIVAL);
		player.setHealth(20.0F);
		player.snapTo(Vec3.atBottomCenterOf(feet), 0.0F, 0.0F);
		return player;
	}

	/**
	 * 18c: nothing before 60 s within 48 of the square, nor while it would be seen; then the piece's lights go a quarter
	 * at a time, 5 s apart, ledgered under world:s18/dark, the answer counted once; not again the same day; undone through
	 * the ledger (the lights back); and a later day with nobody inside the piece is lit again by itself.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 800)
	public void thePieceGoesDarkInQuartersOutOfViewOnceADayAndIsLitAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig18 rig = rig18(level, 273_008, -273_008, 0x18C3L);
		int all = lights(level, rig.piece());
		helper.assertTrue(all >= 8, "the piece's lights: " + all);
		ServerPlayer ana = player(level, "ana", rig.shape().groveCenter().offset(3, 0, 3));
		rig.people().add(ana);
		rig.inside().add(ana);
		rig.hollow().entered(rig.place(), ana, 0);
		rig.at(server, 20);
		rig.at(server, 20 + 59 * 20);
		helper.assertTrue(lights(level, rig.piece()) == all, "dark before 60 s");
		rig.seen().set(true);
		rig.at(server, 20 + 61 * 20);
		rig.at(server, 20 + 66 * 20);
		helper.assertTrue(lights(level, rig.piece()) == all, "dark while seen");
		rig.seen().set(false);
		rig.at(server, 20 + 70 * 20);
		int left = lights(level, rig.piece());
		helper.assertTrue(left < all && left > 0 && all - left <= all / 2, "the first quarter: " + left + " of " + all);
		long t = 20 + 70 * 20;
		for (int q = 0; q < 6; q++) {
			t += 120;
			rig.at(server, t);
		}
		helper.assertTrue(lights(level, rig.piece()) == 0, "the piece still lit: " + lights(level, rig.piece()));
		helper.assertTrue(rig.answered().stream().filter(a -> a.startsWith("18c:")).count() == 1, "answered " + rig.answered());
		helper.assertTrue(TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(HollowAnswers.DARK)).count() == all, "the ledger");
		helper.assertTrue(rig.hollow().darkened(rig.place()), "not dark");
		// The next day, nobody inside: lit again.
		rig.hollow().left(rig.place(), ana);
		rig.inside().clear();
		rig.day().incrementAndGet();
		rig.at(server, t + 200);
		helper.assertTrue(lights(level, rig.piece()) == all && !rig.hollow().darkened(rig.place()), "not lit again: " + lights(level, rig.piece()));
		helper.succeed();
	}

	// --- 20 v3: fullbright, up to 192; 20b v2 ---

	/**
	 * Land of the test's own round ({@code cx}, {@code cz}): flat to the west, then a slope rising a block every
	 * {@code per} eastward, at most {@code rise} (stone under grass), {@code half} each way. Returns the flat's top.
	 */
	private static int slope(ServerLevel level, int cx, int cz, int half, int per, int rise) {
		for (int x = (cx - half - 16) >> 4; x <= (cx + half + 16) >> 4; x++) {
			for (int z = (cz - half - 16) >> 4; z <= (cz + half + 16) >> 4; z++) {
				level.getChunkSource().addTicketWithRadius(com.forzacode.a1016_02.world.live.NewScarPlacer.TICKET, new ChunkPos(x, z), 0);
				level.getChunk(x, z);
			}
		}
		int base = Edges.top(level, cx, cz);
		int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE | 512;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = cx - half; x <= cx + half; x++) {
			int h = Math.clamp((x - (cx - half / 2)) / per, 0, rise);
			for (int z = cz - half; z <= cz + half; z++) {
				for (int y = base + 1; y <= base + h; y++) {
					level.setBlock(at.set(x, y, z), y == base + h ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), flags);
				}
			}
		}
		return base;
	}

	private static Live.Result floor20(ServerLevel level, int cx, int cz, int side, long seed) {
		com.forzacode.a1016_02.world.sc2.s20.FloorSiting.Fit fit = com.forzacode.a1016_02.world.sc2.s20.FloorSiting.fit(Live.terrain(level), cx, cz, side,
				com.forzacode.a1016_02.world.sc2.s20.FloorShape.gridOf(3, side));
		if (fit == null) {
			throw new IllegalStateException("the land does not fit a " + side);
		}
		Live.Result built = Live.place(level, com.forzacode.a1016_02.world.sc2.s20.S20.KIND,
				new Start(new BlockPos(cx, fit.floor(), cz), Direction.WEST, com.forzacode.a1016_02.world.sc2.s20.FloorShape.variant(side), seed));
		Live.settleLight(level, built.box());
		return built;
	}

	/**
	 * 20 v3 (R3-3): the 192 where the land allows; its floor lit as at noon edge to edge (10 or more over 95 % of its
	 * cells, the building's inside aside), its cut cliff and built side lit with it; every edge fair (world.Edges).
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 1200)
	public void theFloorIs192LitEdgeToEdgeItsFacesWithItAndFair(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int cx = 285_008 & ~15;
		int cz = -285_008 & ~15;
		slope(level, cx, cz, 112, 3, 60);
		Live.Result built = floor20(level, cx, cz, 192, 0x20C0L);
		com.forzacode.a1016_02.world.sc2.s20.FloorShape shape = (com.forzacode.a1016_02.world.sc2.s20.FloorShape) built.shape();
		helper.assertTrue(shape.version() == 3 && shape.side() == 192, "version " + shape.version() + ", side " + shape.side());
		BoundingBox sq = shape.square();
		helper.assertTrue(Math.floorMod(sq.minX(), 16) == 0 && Math.floorMod(sq.minZ(), 16) == 0 && sq.getXSpan() == 192, "the square " + sq);
		int f = shape.floorY();
		BlockPos house = built.place().anchor();
		int cells = 0;
		int lit = 0;
		for (int x = sq.minX(); x <= sq.maxX(); x++) {
			for (int z = sq.minZ(); z <= sq.maxZ(); z++) {
				if (Math.abs(x - house.getX()) <= 4 && Math.abs(z - house.getZ()) <= 4) {
					continue;
				}
				BlockPos over = new BlockPos(x, f + 1, z);
				if (!level.getBlockState(over).isAir() && !Fault.isLight(level.getBlockState(over))) {
					over = over.above();
				}
				cells++;
				lit += level.getBrightness(LightLayer.BLOCK, over) >= Fault.MIN_LIT ? 1 : 0;
			}
		}
		helper.assertTrue(lit * 100 >= cells * 95, "lit " + lit + " of " + cells);
		int cliff = 0;
		int built2 = 0;
		for (int x = sq.minX() - 1; x <= sq.maxX() + 1; x++) {
			for (int z = sq.minZ() - 1; z <= sq.maxZ() + 1; z++) {
				int ring = shape.ringOf(x, z);
				boolean edge = ring == 0 && (x == sq.minX() || x == sq.maxX() || z == sq.minZ() || z == sq.maxZ());
				if (!edge && ring != 1) {
					continue;
				}
				for (int y = f - 40; y <= f + 70; y++) {
					if (Fault.isLight(level.getBlockState(new BlockPos(x, y, z)))) {
						cliff += edge && y > f + 3 ? 1 : 0;
						built2 += ring == 1 && y < f ? 1 : 0;
					}
				}
			}
		}
		helper.assertTrue(cliff > 20 && built2 > 20, "the faces' lights: cut " + cliff + ", built " + built2);
		BoundingBox area = new BoundingBox(sq.minX() - 8, f - 60, sq.minZ() - 8, sq.maxX() + 8, f + 80, sq.maxZ() + 8);
		List<BlockPos> seeds = new ArrayList<>();
		for (int x = area.minX(); x <= area.maxX(); x += 2) {
			for (int z = area.minZ(); z <= area.maxZ(); z += 2) {
				int ring = shape.ringOf(x, z);
				// The floor (not its kerb nor the building's roof) and the land two and more out (as round 2's).
				if (ring == 0 && Edges.top(level, x, z) == f || ring >= 2) {
					seeds.add(new BlockPos(x, 0, z));
				}
			}
		}
		List<String> drops = Edges.unguardedDrops(level, area, seeds, (x, z) -> shape.ringOf(x, z) <= 1);
		helper.assertTrue(drops.isEmpty(), "unguarded: " + drops);
		helper.succeed();
	}

	/** A figure of the test's own: it can always be seen, and counts its glimpses. */
	private static final class Glimpses implements com.forzacode.a1016_02.core.Figure {
		final List<Vec3> at = Collections.synchronizedList(new ArrayList<>());

		@Override
		public boolean canGlimpse(ServerPlayer player, Vec3 feet) {
			return true;
		}

		@Override
		public com.forzacode.a1016_02.core.FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw) {
			at.add(feet);
			return com.forzacode.a1016_02.core.FireResult.FIRED;
		}
	}

	/**
	 * 20b v2 (R3-3, R10-16) with two on the floor at night: nothing by day; then, out of view, the building's two torches
	 * go (ledgered), the free animals turn to the dark doorway; a chunk of the floor's lights goes every 4 s, the farthest
	 * from the building first, never the chunks they stand in nor the ones toward their nearest edges; after 4 chunks the
	 * glimpse in the doorway; not again that day; asleep, nothing; undone through the ledger (every light back).
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 1200)
	public void theFloorGoesDarkFromItsFarEdgesNeverWhereTheyStand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		int cx = 289_008 & ~15;
		int cz = -289_008 & ~15;
		slope(level, cx, cz, 72, 4, 30);
		Live.Result built = floor20(level, cx, cz, 128, 0x20C1L);
		com.forzacode.a1016_02.world.sc2.s20.FloorShape shape = (com.forzacode.a1016_02.world.sc2.s20.FloorShape) built.shape();
		Remains.Place place = built.place();
		BoundingBox sq = shape.square();
		int all = lights(level, built.box());
		helper.assertTrue(com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.torches(level, place).size() == 2 && all > 400, "torches, lights " + all);
		Sc2Data data = new Sc2Data();
		data.add(built.known());
		List<ServerPlayer> inside = new ArrayList<>();
		AtomicBoolean night = new AtomicBoolean(false);
		AtomicBoolean seen = new AtomicBoolean(false);
		AtomicLong day = new AtomicLong(60);
		AtomicLong now = new AtomicLong(0);
		java.util.concurrent.atomic.AtomicReference<String> story = new java.util.concurrent.atomic.AtomicReference<>(null);
		List<String> answered = Collections.synchronizedList(new ArrayList<>());
		Answers.Env env = new Answers.Env(s -> data, Services.traces().forced(), (l, cells) -> !seen.get(), (s, p) -> List.copyOf(inside), uuid -> false,
				uuid -> 200_000L, s -> story.get(), day::get, now::get, (l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString()));
		Answers answers = Answers.over(env);
		com.forzacode.a1016_02.world.sc2.s20.Sc20Config config = new com.forzacode.a1016_02.world.sc2.s20.Sc20Config();
		Glimpses figure = new Glimpses();
		// A chunk someone sees (its lights wait while it is).
		java.util.concurrent.atomic.AtomicReference<ChunkPos> hidden = new java.util.concurrent.atomic.AtomicReference<>(null);
		com.forzacode.a1016_02.world.sc2.s20.FloorAnswers floor = new com.forzacode.a1016_02.world.sc2.s20.FloorAnswers(
				new com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.Env(answers, s -> List.copyOf(inside), () -> config, Services::mobs, () -> figure,
						s -> day.get(), l -> night.get(), (l, cells) -> hidden.get() == null || cells.stream().noneMatch(c -> ChunkPos.containing(c).equals(hidden.get()))));
		// Two on the floor: one 26 from the doorway on its west side, one near the building's south-east.
		BlockPos door = place.entrance();
		ServerPlayer ana = player(level, "ana", new BlockPos(door.getX() - 26, shape.floorY() + 1, door.getZ() + 3));
		ServerPlayer bo = player(level, "bo", new BlockPos(house(place).getX() + 9, shape.floorY() + 1, house(place).getZ() + 20));
		inside.add(ana);
		inside.add(bo);
		floor.entered(place, ana);
		floor.entered(place, bo);
		List<net.minecraft.world.entity.animal.Animal> cows = new ArrayList<>();
		for (int k = 0; k < 4; k++) {
			cows.addAll(com.forzacode.a1016_02.core.GalleryProps.summon(level, net.minecraft.world.entity.EntityTypes.COW,
					new BlockPos(door.getX() - 10 - 4 * k, shape.floorY() + 1, door.getZ() - 8), 1));
		}
		// By day, nothing.
		long t = 3;
		for (int k = 0; k < 3; k++, t += 20) {
			now.set(t);
			floor.look(server, t);
			answers.tryAll(server);
		}
		helper.assertTrue(com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.torches(level, place).size() == 2, "dark by day");
		// Asleep: nothing.
		night.set(true);
		story.set("asleep");
		for (int k = 0; k < 3; k++, t += 220) {
			now.set(t);
			floor.look(server, t);
			answers.tryAll(server);
		}
		helper.assertTrue(com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.torches(level, place).size() == 2, "dark while asleep");
		story.set(null);
		t += 220;
		now.set(t);
		floor.look(server, t);
		answers.tryAll(server);
		helper.assertTrue(com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.torches(level, place).isEmpty(), "the torches stand");
		helper.assertTrue(answered.size() == 2 && answered.stream().allMatch(a -> a.startsWith("20b:")), "answered " + answered);
		int turned = 0;
		for (var cow : cows) {
			turned += Services.mobs().isTampered(cow) ? 1 : 0;
		}
		helper.assertTrue(turned == cows.size(), "turned " + turned + " of " + cows.size());
		java.util.Set<ChunkPos> kept = new java.util.HashSet<>();
		for (ServerPlayer p : List.of(ana, bo)) {
			kept.addAll(keptBy(sq, p));
		}
		// FIX-R3-B (R3A-3): the dark comes a ring at a time from the edge in, every chunk of a ring at once (but those
		// someone sees: here one corner of the edge ring waits in view), the next ring every g seconds.
		List<List<ChunkPos>> rings = com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.rings(place);
		helper.assertTrue(rings.size() == 4 && rings.get(0).size() == 28 && rings.get(3).size() == 4, "rings " + rings.stream().map(List::size).toList());
		ChunkPos corner = new ChunkPos(sq.minX() >> 4, sq.minZ() >> 4);
		int before = lights(level, built.box());
		long ringTicks = Math.round(config.darkRingSeconds * 20);
		t += ringTicks + 1;
		now.set(t);
		hidden.set(corner);
		floor.look(server, t);
		answers.tryAll(server);
		helper.assertTrue(floor.ringsReached(place) == 1, "rings reached after one step: " + floor.ringsReached(place));
		for (ChunkPos c : rings.get(0)) {
			int left = com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.chunkLights(level, place, c).size();
			boolean stays = kept.contains(c) || c.equals(corner);
			helper.assertTrue(stays ? left > 0 : left == 0, "edge ring chunk " + c + (stays ? " went dark (kept or in view)" : " still lit: " + left));
		}
		for (ChunkPos c : rings.get(1)) {
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.chunkLights(level, place, c).size() > 0, "the second ring went with the first: " + c);
		}
		helper.assertTrue(figure.at.isEmpty(), "a glimpse after one ring");
		// The corner out of view again: it goes with the next ring.
		hidden.set(null);
		int went = 0;
		for (int k = 0; k < 8; k++) {
			t += ringTicks + 1;
			now.set(t);
			floor.look(server, t);
			answers.tryAll(server);
			went = Math.max(went, floor.chunksGone(place));
		}
		int after = lights(level, built.box());
		helper.assertTrue(com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.chunkLights(level, place, corner).isEmpty(), "the corner waited for good");
		helper.assertTrue(after < before && before - after <= config.darkPerVisit && went >= 64 - kept.size() - 1,
				"chunks gone " + went + " of " + (64 - kept.size()) + ", lights " + before + " to " + after);
		for (ChunkPos c : kept) {
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.chunkLights(level, place, c).size() > 0, "a chunk they stand in went dark: " + c);
		}
		helper.assertTrue(figure.at.size() == 1, "glimpses " + figure.at.size());
		// Not again today.
		helper.assertTrue(floor.askDark(level, place, false).status() == Answers.Status.REFUSED, "again the same day");
		// Undone: every light and torch back through the ledger.
		for (TraceLedger.Entry entry : List.copyOf(TraceLedger.get(server).entries())) {
			if (entry.cause().equals(com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.DARK) && built.box().isInside(entry.pos().pos())) {
				Services.traces().forced().undo(level, entry);
			}
		}
		helper.assertTrue(lights(level, built.box()) == all && com.forzacode.a1016_02.world.sc2.s20.FloorAnswers.torches(level, place).size() == 2,
				"not undone: " + lights(level, built.box()) + " of " + all);
		for (var cow : cows) {
			cow.discard();
		}
		helper.succeed();
	}

	/**
	 * R3A-16 (FIX-R3-B): a floor in a cold biome. Worldgen's freeze lays snow on the floor after the structure is built;
	 * the fault's light melts it and keeps new snow off: every open cell over the floor stands at block light 12 or more
	 * (a snow layer melts over 11 at its next random tick, and snow falls only under 10), but for the square's own corner
	 * cells on a cut side (11: two and two from the nearest light; their snow stays, no new snow falls); a snow layer on
	 * every other one is gone after one random tick.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 1200)
	public void aColdFloorsSnowMeltsUnderItsLightAndStaysOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int cx = 293_008 & ~15;
		int cz = -293_008 & ~15;
		slope(level, cx, cz, 72, 4, 30);
		Live.Result built = floor20(level, cx, cz, 64, 0x20C3L);
		com.forzacode.a1016_02.world.sc2.s20.FloorShape shape = (com.forzacode.a1016_02.world.sc2.s20.FloorShape) built.shape();
		BoundingBox sq = shape.square();
		BlockPos house = built.place().anchor();
		int f = shape.floorY();
		BlockState snow = Blocks.SNOW.defaultBlockState();
		List<BlockPos> snowed = new ArrayList<>();
		List<String> dim = new ArrayList<>();
		int cornersKept = 0;
		for (int x = sq.minX(); x <= sq.maxX(); x++) {
			for (int z = sq.minZ(); z <= sq.maxZ(); z++) {
				if (Math.abs(x - house.getX()) <= 5 && Math.abs(z - house.getZ()) <= 5) {
					continue;
				}
				BlockPos cell = new BlockPos(x, f + 1, z);
				BlockState at = level.getBlockState(cell);
				if (!at.isAir() || !level.getBlockState(cell.below()).is(Blocks.GRASS_BLOCK)) {
					continue;
				}
				int light = level.getBrightness(LightLayer.BLOCK, cell);
				boolean corner = (x == sq.minX() || x == sq.maxX()) && (z == sq.minZ() || z == sq.maxZ());
				if (corner && light < 12) {
					// The square's own corner cell lies two and two from its nearest light: 11, so its snow stays (and no
					// new snow falls over 9).
					cornersKept++;
				}
				if ((corner ? light < 10 : light < 12) && dim.size() < 8) {
					dim.add(cell.toShortString() + " " + light);
				}
				level.setBlock(cell, snow, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				snowed.add(cell);
			}
		}
		helper.assertTrue(dim.isEmpty(), "cells under 12 (snow stays or falls): " + dim);
		helper.assertTrue(snowed.size() > 3000, "snowed " + snowed.size());
		int left = 0;
		for (BlockPos cell : snowed) {
			BlockState at = level.getBlockState(cell);
			if (at.is(Blocks.SNOW)) {
				at.randomTick(level, cell, level.getRandom());
			}
			left += level.getBlockState(cell).is(Blocks.SNOW) ? 1 : 0;
		}
		helper.assertTrue(left == cornersKept && cornersKept <= 4, "snow left after a random tick: " + left + " of " + snowed.size() + " (corners at 11: "
				+ cornersKept + ")");
		helper.succeed();
	}

	/** The two chunks a player on a floor keeps lit: theirs and the one toward the floor's nearest edge. */
	private static java.util.Set<ChunkPos> keptBy(BoundingBox floor, ServerPlayer player) {
		int x = player.getBlockX();
		int z = player.getBlockZ();
		int west = x - floor.minX();
		int east = floor.maxX() - x;
		int north = z - floor.minZ();
		int south = floor.maxZ() - z;
		int least = Math.min(Math.min(west, east), Math.min(north, south));
		int sx = least == west ? -1 : least == east ? 1 : 0;
		int sz = sx != 0 ? 0 : least == north ? -1 : 1;
		ChunkPos at = ChunkPos.containing(player.blockPosition());
		return java.util.Set.of(at, new ChunkPos(at.x() + sx, at.z() + sz));
	}

	/**
	 * FIX-R3-B: what one chunk of the hollow, the line and the floor costs to fill on the real noise, when asked
	 * ({@code -Da1016_02.sc3fill=true}): the made starts of the find's first worlds (and each world's four line segments),
	 * every chunk of each filled into an overlay over the noise (its own and its eight neighbours' noise made first, so
	 * the time is the shape's), twice (the second pass warm); per kind the median, 90 % and worst, and the worst chunks.
	 * Writes {@code build/sc3fill.txt}. The budget is 25 ms a chunk.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 200)
	public void theFillOfAChunkCostsWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc3fill")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		int worlds = Integer.getInteger("a1016_02.sc3fill.worlds", 4);
		Random seeds = new Random(0x5C3F1DL);
		Map<String, List<double[]>> ms = new java.util.TreeMap<>();
		Map<String, String> worstAt = new java.util.TreeMap<>();
		Snapshot keep = Snapshot.current();
		List<Shape> shapes = new ArrayList<>();
		List<Sc2RealNoise.World> of = new ArrayList<>();
		List<String> names = new ArrayList<>();
		try {
			for (int w = 0; w < worlds; w++) {
				long seed = w == 0 ? SelfCreated2HeightsTests.REAL_SEED : seeds.nextLong();
				Sc2RealNoise.World world = Sc2RealNoise.of(level, "normal", Sc2RealNoise.PRESETS.get("normal"), seed, -64, 384);
				ChunkPos origin = world.generator().getOrigin(world.random());
				BlockPos spawn = new BlockPos(origin.getMinBlockX() + 8, 64, origin.getMinBlockZ() + 8);
				Snapshot snapshot = new Snapshot(true, com.forzacode.a1016_02.core.Stage.TRACES, spawn, seed, 7L, Sc2RealNoise.snapshot(seed).configs(), true);
				for (String id : List.of("s18", "s20")) {
					Holder<Structure> structure = Sc2RealNoise.structures(level).get(id);
					StructurePlacement placement = Sc2RealNoise.placement(level, structure);
					int made = 0;
					for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, spawn.getX(), spawn.getZ(), 1600, Integer.MAX_VALUE)) {
						Sc2Structure.Sited s = SelfCreated2HeightsTests.sited(world, structure, Sc2Kind.get(id), chunk, snapshot);
						if (s.made() && made++ < 3) {
							shapes.add(s.shape());
							of.add(world);
							names.add(id + " " + seed + " " + s.start().origin().toShortString());
						}
					}
				}
				for (com.forzacode.a1016_02.world.sc2.s19.LinePlan.Segment segment : com.forzacode.a1016_02.world.sc2.s19.LinePlan.of(seed, 7L,
						new com.forzacode.a1016_02.world.sc2.s19.Sc19Config(), world.minY(), world.maxY())) {
					shapes.add(new com.forzacode.a1016_02.world.sc2.s19.LineShape(segment, world.terrain()));
					of.add(world);
					names.add("s19 " + seed + " " + segment.arm());
				}
			}
		} finally {
			Snapshot.install(keep);
		}
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 0; k < shapes.size(); k++) {
				Shape shape = shapes.get(k);
				Sc2RealNoise.World world = of.get(k);
				String kind = names.get(k).substring(0, 3);
				BoundingBox box = shape.box();
				int lowest = com.forzacode.a1016_02.world.sc2.kit.Heights.lowest(world.height(), world.generator());
				Overlay overlay = new Overlay(NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8), Math.min(world.maxY(),
						box.maxY() + 8)));
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
					for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
						BoundingBox column = new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15, world.maxY(), (cz << 4) + 15);
						java.util.Optional<BoundingBox> clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(column, box);
						if (clip.isEmpty()) {
							continue;
						}
						for (int nx = cx - 1; nx <= cx + 1; nx++) {
							for (int nz = cz - 1; nz <= cz + 1; nz++) {
								overlay.get(nx << 4, Math.max(lowest, box.minY() - 8), nz << 4);
							}
						}
						long t0 = System.nanoTime();
						shape.fill(overlay.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
						double t = (System.nanoTime() - t0) / 1e6;
						if (pass == 1) {
							ms.computeIfAbsent(kind, x -> new ArrayList<>()).add(new double[] {t, k, cx, cz});
						}
					}
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, List<double[]>> e : ms.entrySet()) {
			List<double[]> all = new ArrayList<>(e.getValue());
			all.sort(java.util.Comparator.comparingDouble(a -> a[0]));
			double[] worst = all.getLast();
			out.add(String.format(Locale.ROOT, "%s fill a chunk (warm): %d chunks, median %.2f ms, 90%% %.2f, worst %.2f (%s, chunk %d %d)", e.getKey(), all.size(),
					all.get(all.size() / 2)[0], all.get(all.size() * 9 / 10)[0], worst[0], names.get((int) worst[1]), (int) worst[2], (int) worst[3]));
			for (int i = Math.max(0, all.size() - 4); i < all.size() - 1; i++) {
				out.add(String.format(Locale.ROOT, "  next %.2f ms (%s, chunk %d %d)", all.get(i)[0], names.get((int) all.get(i)[1]), (int) all.get(i)[2], (int) all.get(i)[3]));
			}
		}
		out.forEach(line -> Sc2RealNoise.log("sc3fill " + line));
		try {
			java.nio.file.Files.write(projectBuild().resolve("sc3fill.txt"), out);
		} catch (java.io.IOException e) {
			Sc2RealNoise.log("sc3fill: not written");
		}
		helper.succeed();
	}

	/**
	 * FIX-R3-E, when asked ({@code -Da1016_02.sc3floor=true}): the floor's (20) fill on the real noise, every chunk of every
	 * made start of SC2-H's survey (its eight worlds, its centres, {@code a1016_02.sc3floor.per} candidates a centre, 8 by
	 * default), each chunk filled as the survey fills it (one overlay a start, every cell set kept) in the thread's CPU
	 * time, twice (the second pass counted), and once more into an overlay of its own (the chunk's cost without the
	 * start's other chunks' cells). The fill's hash: every cell each start sets with its block, and the cells whose block
	 * differs from the noise's (the result), summed over the starts, so two versions of the code that build the same
	 * blocks print the same hashes. With {@code a1016_02.sc3floor.repeat=N} the worst chunks are filled N times more (for
	 * a profiler). Writes {@code build/sc3floor.txt}.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 72000)
	public void theFloorsFillCostAndHashWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc3floor")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		List<Sc2RealNoise.World> worlds = new ArrayList<>();
		for (long seed : new long[] {SelfCreated2HeightsTests.REAL_SEED, 1L, 20261024L, -77L}) {
			worlds.add(Sc2RealNoise.normal(level, seed));
		}
		worlds.add(Sc2RealNoise.of(level, "amplified", Sc2RealNoise.PRESETS.get("amplified"), SelfCreated2HeightsTests.REAL_SEED, -64, 384));
		worlds.add(Sc2RealNoise.of(level, "amplified", Sc2RealNoise.PRESETS.get("amplified"), 1L, -64, 384));
		worlds.add(Sc2RealNoise.of(level, "large", Sc2RealNoise.PRESETS.get("large"), SelfCreated2HeightsTests.REAL_SEED, -64, 384));
		worlds.add(Sc2RealNoise.normal(level, SelfCreated2HeightsTests.REAL_SEED).withHeight(level, 0, 256));
		int per = Integer.getInteger("a1016_02.sc3floor.per", 8);
		int repeat = Integer.getInteger("a1016_02.sc3floor.repeat", 0);
		java.lang.management.ThreadMXBean cpu = java.lang.management.ManagementFactory.getThreadMXBean();
		Holder<Structure> structure = Sc2RealNoise.structures(level).get("s20");
		StructurePlacement placement = Sc2RealNoise.placement(level, structure);
		Sc2Kind kind = Sc2Kind.get("s20");
		List<Runnable> jobs = new ArrayList<>();
		List<double[]> cold = new ArrayList<>();
		List<double[]> survey = new ArrayList<>();
		List<double[]> alone = new ArrayList<>();
		List<String> names = new ArrayList<>();
		List<String> hashes = new ArrayList<>();
		List<Shape> shapes = new ArrayList<>();
		List<Sc2RealNoise.World> of = new ArrayList<>();
		long[] sum = new long[4];
		for (Sc2RealNoise.World world : worlds) {
			Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
			int lowest = com.forzacode.a1016_02.world.sc2.kit.Heights.lowest(world.height(), world.generator());
			java.util.Set<ChunkPos> seen = new java.util.HashSet<>();
			for (int[] c : SelfCreated2HeightsTests.CENTRES) {
				for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, per)) {
					if (!seen.add(chunk)) {
						continue;
					}
					jobs.add(() -> {
						Sc2Structure.Sited s = SelfCreated2HeightsTests.sited(world, structure, kind, chunk, snapshot);
						if (!s.made()) {
							return;
						}
						Shape shape = s.shape();
						int k = names.size();
						String name = world.name() + " " + world.seed() + " " + world.minY() + ".." + world.maxY() + " start " + chunk;
						names.add(name);
						shapes.add(shape);
						of.add(world);
						BoundingBox box = shape.box();
						Overlay.Base base = NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8), Math.min(world.maxY(), box.maxY() + 8));
						for (int pass = 0; pass < 2; pass++) {
							Overlay overlay = new Overlay(base, true);
							for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
								for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
									BoundingBox column = new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15, world.maxY(), (cz << 4) + 15);
									java.util.Optional<BoundingBox> clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(column, box);
									if (clip.isEmpty()) {
										continue;
									}
									overlay.get(cx << 4, box.minY(), cz << 4);
									long c0 = cpu.getCurrentThreadCpuTime();
									shape.fill(overlay.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
									double ms = (cpu.getCurrentThreadCpuTime() - c0) / 1e6;
									if (pass == 0) {
										cold.add(new double[] {ms, k, cx, cz});
									} else {
										survey.add(new double[] {ms, k, cx, cz});
										Overlay own = new Overlay(base, true);
										long a0 = cpu.getCurrentThreadCpuTime();
										shape.fill(own.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
										alone.add(new double[] {(cpu.getCurrentThreadCpuTime() - a0) / 1e6, k, cx, cz});
									}
								}
							}
							if (pass == 0) {
								long all = 0;
								long changed = 0;
								int n = 0;
								int m = 0;
								for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<BlockState> e : overlay.cells().long2ObjectEntrySet()) {
									long pos = e.getLongKey();
									long h = mix(pos * 0x9E3779B97F4A7C15L + net.minecraft.world.level.block.Block.getId(e.getValue()));
									all += h;
									n++;
									if (overlay.base(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos)) != e.getValue()) {
										changed += h;
										m++;
									}
								}
								sum[0] += all;
								sum[1] += changed;
								sum[2] += n;
								sum[3] += m;
								hashes.add(String.format(Locale.ROOT, "  %s at %s: set %d %016x, changed %d %016x", name, shape.box(), n, all, m, changed));
							}
						}
					});
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
					throw helper.assertionException("filling: " + next[0] + " of " + jobs.size());
				}
				done[0] = true;
				List<String> out = new ArrayList<>();
				out.add(String.format(Locale.ROOT, "s20 fill hash: %d starts, set %d cells %016x, changed %d cells %016x", names.size(), sum[2], sum[0], sum[3], sum[1]));
				for (List<double[]> list : List.of(cold, survey, alone)) {
					List<double[]> all = new ArrayList<>(list);
					all.sort(java.util.Comparator.comparingDouble(a -> a[0]));
					out.add(String.format(Locale.ROOT, "s20 fill a chunk in CPU ms, %s: %d chunks, median %.2f, 99%% %.2f, 99.9%% %.2f, worst %.2f", list == cold
							? "one overlay a start, the noise read in it (the survey's)" : list == survey ? "one overlay a start, warm" : "an overlay a chunk, warm",
							all.size(), all.get(all.size() / 2)[0], all.get(all.size() * 99 / 100)[0],
							all.get(Math.min(all.size() - 1, all.size() * 999 / 1000))[0], all.getLast()[0]));
					for (int i = all.size() - 1; i >= Math.max(0, all.size() - 6); i--) {
						double[] a = all.get(i);
						out.add(String.format(Locale.ROOT, "  %.2f ms: %s chunk %d %d", a[0], names.get((int) a[1]), (int) a[2], (int) a[3]));
					}
				}
				if (repeat > 0) {
					List<double[]> worst = new ArrayList<>(alone);
					worst.sort(java.util.Comparator.comparingDouble(a -> -a[0]));
					for (double[] a : worst.subList(0, Math.min(6, worst.size()))) {
						Shape shape = shapes.get((int) a[1]);
						Sc2RealNoise.World world = of.get((int) a[1]);
						BoundingBox box = shape.box();
						int lowest = com.forzacode.a1016_02.world.sc2.kit.Heights.lowest(world.height(), world.generator());
						Overlay.Base base = NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8), Math.min(world.maxY(), box.maxY() + 8));
						int cx = (int) a[2];
						int cz = (int) a[3];
						BoundingBox clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15,
								world.maxY(), (cz << 4) + 15), box).orElseThrow();
						long c0 = cpu.getCurrentThreadCpuTime();
						for (int r = 0; r < repeat; r++) {
							Overlay own = new Overlay(base, true);
							shape.fill(own.writer(clip, world.terrain(), level.registryAccess()), clip);
						}
						out.add(String.format(Locale.ROOT, "  repeated %d times: %.2f ms each (%s chunk %d %d)", repeat, (cpu.getCurrentThreadCpuTime() - c0) / 1e6 / repeat,
								names.get((int) a[1]), cx, cz));
					}
				}
				out.addAll(hashes);
				out.stream().limit(40).forEach(line -> Sc2RealNoise.log("sc3floor " + line));
				try {
					java.nio.file.Files.write(projectBuild().resolve("sc3floor.txt"), out);
				} catch (java.io.IOException e) {
					Sc2RealNoise.log("sc3floor: not written");
				}
			}
		});
	}

	/** SplitMix64's finaliser (the fill hash's cells). */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/**
	 * FIX-R3-B, when asked ({@code -Da1016_02.sc3why18=true}): why R3-3's /locate hollow on SC2-H's seed (chunk 30, -22,
	 * 595 from the spawn) is no longer the nearest: its siting now, as version 4 and as version 3. Prints.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 200)
	public void whyTheRealSeedsNearestHollowMovedWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc3why18")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World world = Sc2RealNoise.normal(level, SelfCreated2HeightsTests.REAL_SEED);
		Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
		Holder<Structure> structure = Sc2RealNoise.structures(level).get("s18");
		try {
			for (ChunkPos chunk : List.of(new ChunkPos(30, -22), new ChunkPos(60, -14))) {
				Sc2Structure.Sited s = SelfCreated2HeightsTests.sited(world, structure, Sc2Kind.get("s18"), chunk, snapshot);
				String line = "why18 " + chunk + ": made " + s.made() + ", why " + s.why();
				if (s.shape() instanceof HollowShape h) {
					HollowShape v3 = new HollowShape(h.start().withVersion(3), world.terrain());
					line += "; v" + h.version() + " valid " + h.valid() + " summit " + h.summit() + "; v3 valid " + v3.valid() + " summit " + v3.summit();
				}
				Sc2RealNoise.log(line);
			}
		} finally {
			Snapshot.install(null);
			Snapshot.refresh(level.getServer());
		}
		helper.succeed();
	}

	/**
	 * R12-2 (FIX-R3-B): the hollow's and the floor's version 3 starts (the first each kit made round SC2-H's centres when
	 * version 3 was current) still build cell for cell as before, their fill hashed as the kit's pins are
	 * ({@code SelfCreated3KitTests.fillHash}). {@code -Da1016_02.sc3pin3=true} prints the pins from the code at hand.
	 */
	static final Map<String, long[]> PINS3 = Map.of(
			"s18", new long[] {279550827724908L, 1, 15095132, 5896614968915553466L, 3, 8112008707839784304L},
			"s20", new long[] {211381105852511L, 0, 12, 1251766057126750657L, 3, 7281246904061496928L});
	/** The line's version 3 (its first segment in SC2-H's world, SOUTH 624..1216, as the default config lays it out). */
	static final long PIN3_LINE = 7841976446287065139L;

	@GameTest(environment = HEIGHTS, maxTicks = 6000)
	public void version3OfTheHollowAndTheFloorStillBuildsAsBefore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World world = Sc2RealNoise.normal(level, SelfCreated2HeightsTests.REAL_SEED);
		boolean pin = Boolean.getBoolean("a1016_02.sc3pin3");
		List<String> wrong = new ArrayList<>();
		Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
		try {
			for (String id : List.of("s18", "s20")) {
				Holder<Structure> structure = Sc2RealNoise.structures(level).get(id);
				Sc2Kind kind = Sc2Kind.get(id);
				if (pin) {
					Start found = null;
					StructurePlacement placement = Sc2RealNoise.placement(level, structure);
					for (int[] c : SelfCreated2HeightsTests.CENTRES) {
						for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, 40)) {
							Sc2Structure.Sited sited = SelfCreated2HeightsTests.sited(world, structure, kind, chunk, snapshot);
							if (found == null && sited.made()) {
								found = kind.resolve(sited.start());
							}
						}
						if (found != null) {
							break;
						}
					}
					if (found != null) {
						long hash = SelfCreated3KitTests.fillHash(level, world, kind.shape(found, world.terrain()));
						Sc2RealNoise.log("PIN3 \"" + id + "\", new long[] {" + found.origin().asLong() + "L, " + found.facing().get2DDataValue() + ", "
								+ found.variant() + ", " + found.seed() + "L, " + found.version() + ", " + hash + "L}");
					}
					continue;
				}
				long[] p = PINS3.get(id);
				if (p == null) {
					wrong.add(id + " has no pin");
					continue;
				}
				Start start = new Start(BlockPos.of(p[0]), Direction.from2DDataValue((int) p[1]), (int) p[2], p[3], (int) p[4]);
				long hash = SelfCreated3KitTests.fillHash(level, world, kind.shape(start, world.terrain()));
				if (hash != p[5]) {
					wrong.add(id + " v" + p[4] + " fills to " + hash + ", pinned " + p[5]);
				}
			}
			// The line's version 3: the first segment the default config lays out in this world, built as version 3.
			com.forzacode.a1016_02.world.sc2.s19.LinePlan.Segment segment = com.forzacode.a1016_02.world.sc2.s19.LinePlan.of(snapshot.seed(), snapshot.salt(),
					new com.forzacode.a1016_02.world.sc2.s19.Sc19Config(), world.minY(), world.maxY()).getFirst();
			long line = SelfCreated3KitTests.fillHash(level, world, new com.forzacode.a1016_02.world.sc2.s19.LineShape(segment, world.terrain(), 3));
			if (pin) {
				Sc2RealNoise.log("PIN3 \"s19\" " + segment.arm() + " " + segment.t0() + ".." + segment.t1() + ": " + line + "L");
			} else if (line != PIN3_LINE) {
				wrong.add("s19 v3 fills to " + line + ", pinned " + PIN3_LINE);
			}
		} finally {
			Snapshot.install(null);
			Snapshot.refresh(level.getServer());
		}
		helper.assertTrue(wrong.isEmpty(), "pinned versions changed: " + wrong);
		helper.succeed();
	}

	/** The building's middle (the place's anchor is inside it). */
	private static BlockPos house(Remains.Place place) {
		return place.anchor();
	}

	// --- the hollow's land ---

	/**
	 * The hollow's land top read a volume at a time ({@code HollowLand}) is the generator's own top
	 * ({@code Terrain.ground}, a column at a time) at every point, in mountains and hills of three real worlds, clamped
	 * the same way under its lowest height; and it is cheaper.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 400)
	public void theHollowsLandReadAVolumeAtATimeIsTheColumnsOwnTop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long fast = 0;
		long slow = 0;
		int points = 0;
		int differ = 0;
		String first = null;
		for (long seed : new long[] {3412118725528132917L, -967261395820773912L, -6861036433006232429L}) {
			Sc2RealNoise.World world = Sc2RealNoise.normal(level, seed);
			java.util.Random random = new java.util.Random(seed);
			for (int k = 0; k < 6; k++) {
				int x0 = (random.nextInt(400) - 200) * 16 + 4;
				int z0 = (random.nextInt(400) - 200) * 16 - 8;
				int n = 13;
				int yLow = 56 + random.nextInt(4) * 13;
				int clamp = Math.max(yLow, world.terrain().seaLevel() + 1) - 1;
				boolean[] want = new boolean[n * n];
				java.util.Arrays.fill(want, true);
				want[5] = false;
				long t = System.nanoTime();
				int[] tops = com.forzacode.a1016_02.world.sc2.s18.HollowLand.tops(world.terrain(), x0, z0, 4, n, n, yLow, want);
				fast += System.nanoTime() - t;
				for (int i = 0; i < n; i++) {
					for (int j = 0; j < n; j++) {
						if (!want[i * n + j]) {
							helper.assertTrue(tops[i * n + j] == clamp, "a point not asked reads the lowest");
							continue;
						}
						long u = System.nanoTime();
						int g = Math.max(clamp, world.terrain().ground(x0 + 4 * i, z0 + 4 * j));
						slow += System.nanoTime() - u;
						points++;
						if (g != tops[i * n + j]) {
							differ++;
							if (first == null) {
								first = "(" + (x0 + 4 * i) + ", " + (z0 + 4 * j) + ") column " + g + ", volume " + tops[i * n + j];
							}
						}
					}
				}
			}
		}
		Sc2RealNoise.log(String.format(Locale.ROOT, "hollow land: %d points, %d differ, volume %.1f us a point, columns %.1f us", points, differ, fast / 1e3 / points,
				slow / 1e3 / points));
		helper.assertTrue(differ == 0, "the volume's top is the column's: " + differ + " of " + points + " differ, first " + first);
		helper.succeed();
	}

	/**
	 * The hollow's shape cost, when asked ({@code -Da1016_02.sc3cost=true}), one at a time on the test's thread: the made
	 * starts of six of the find's worlds built as version 2 (round 2's reading) and as version 3 (its land read a volume at
	 * a time, and the same read a column at a time), interleaved, three times; and a whole siting of each. Writes
	 * {@code build/sc3hollowcost.txt}.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 200)
	public void theHollowsCostWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc3cost")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Holder<Structure> structure = Sc2RealNoise.structures(level).get("s18");
		StructurePlacement placement = Sc2RealNoise.placement(level, structure);
		Random seeds = new Random(0x5C3F1DL);
		List<long[]> v2 = new ArrayList<>();
		Map<String, List<Long>> nanos = new java.util.TreeMap<>();
		Snapshot keep = Snapshot.current();
		try {
			for (int w = 0; w < 6; w++) {
				long seed = seeds.nextLong();
				Sc2RealNoise.World world = Sc2RealNoise.of(level, "normal", Sc2RealNoise.PRESETS.get("normal"), seed, -64, 384);
				ChunkPos origin = world.generator().getOrigin(world.random());
				BlockPos spawn = new BlockPos(origin.getMinBlockX() + 8, 64, origin.getMinBlockZ() + 8);
				Snapshot snapshot = new Snapshot(true, com.forzacode.a1016_02.core.Stage.TRACES, spawn, seed, 7L, Sc2RealNoise.snapshot(seed).configs(), true);
				for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, spawn.getX(), spawn.getZ(), 1600, Integer.MAX_VALUE)) {
					long t = System.nanoTime();
					Sc2Structure.Sited s = SelfCreated2HeightsTests.sited(world, structure, Sc2Kind.get("s18"), chunk, snapshot);
					nanos.computeIfAbsent("siting " + (s.made() ? "made" : s.shape() != null ? "refused with a shape" : "refused"), k -> new ArrayList<>())
							.add(System.nanoTime() - t);
					if (!s.made()) {
						continue;
					}
					com.forzacode.a1016_02.world.sc2.kit.Start start = s.start();
					for (int round = 0; round < 3; round++) {
						t = System.nanoTime();
						new com.forzacode.a1016_02.world.sc2.s18.HollowShape(start.withVersion(2), world.terrain());
						nanos.computeIfAbsent("shape v2", k -> new ArrayList<>()).add(System.nanoTime() - t);
						t = System.nanoTime();
						new com.forzacode.a1016_02.world.sc2.s18.HollowShape(start.withVersion(3), world.terrain());
						nanos.computeIfAbsent("shape v3 (volumes)", k -> new ArrayList<>()).add(System.nanoTime() - t);
						com.forzacode.a1016_02.world.sc2.s18.HollowLand.volumes = false;
						try {
							t = System.nanoTime();
							new com.forzacode.a1016_02.world.sc2.s18.HollowShape(start.withVersion(3), world.terrain());
							nanos.computeIfAbsent("shape v3 (columns)", k -> new ArrayList<>()).add(System.nanoTime() - t);
						} finally {
							com.forzacode.a1016_02.world.sc2.s18.HollowLand.volumes = true;
						}
					}
				}
			}
		} finally {
			Snapshot.install(keep);
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, List<Long>> e : nanos.entrySet()) {
			List<Long> ns = new ArrayList<>(e.getValue());
			Collections.sort(ns);
			out.add(String.format(Locale.ROOT, "%s: %d, median %.1f ms, worst %.1f ms", e.getKey(), ns.size(), ns.get(ns.size() / 2) / 1e6, ns.getLast() / 1e6));
		}
		try {
			java.nio.file.Files.write(projectBuild().resolve("sc3hollowcost.txt"), out);
		} catch (java.io.IOException e) {
			Sc2RealNoise.log("sc3hollowcost: not written");
		}
		helper.succeed();
	}

	/**
	 * SC2-H's audit of the hollow's versions 2 and 3 on the same starts, when asked ({@code -Da1016_02.sc3audit=true}): the
	 * made starts of four of the find's worlds built each way into an overlay over the noise, {@code Sc2Audit}'s counts
	 * side by side (what version 3 adds, if anything). Writes {@code build/sc3hollowaudit.txt}.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 200)
	public void theHollowsAuditByVersionWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc3audit")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Holder<Structure> structure = Sc2RealNoise.structures(level).get("s18");
		StructurePlacement placement = Sc2RealNoise.placement(level, structure);
		List<String> out = new ArrayList<>();
		Snapshot keep = Snapshot.current();
		int[] sums = new int[4];
		try {
			for (long seed : new long[] {-4180345853331630785L, 1L, 20261024L, -77L}) {
				Sc2RealNoise.World world = Sc2RealNoise.of(level, "normal", Sc2RealNoise.PRESETS.get("normal"), seed, -64, 384);
				Snapshot snapshot = new Snapshot(true, com.forzacode.a1016_02.core.Stage.TRACES, BlockPos.ZERO, seed, 7L, Sc2RealNoise.snapshot(seed).configs(), true);
				int lowest = com.forzacode.a1016_02.world.sc2.kit.Heights.lowest(world.height(), world.generator());
				// -Da1016_02.sc3audit.only=seed:x:z;...: only these candidates, their findings listed.
				String only = System.getProperty("a1016_02.sc3audit.only", "");
				for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, 0, 0, 6000, Integer.MAX_VALUE)) {
					if (!only.isEmpty() && !java.util.Arrays.asList(only.split(";")).contains(seed + ":" + chunk.x() + ":" + chunk.z())) {
						continue;
					}
					Sc2Structure.Sited s = SelfCreated2HeightsTests.sited(world, structure, Sc2Kind.get("s18"), chunk, snapshot);
					if (!s.made()) {
						continue;
					}
					String[] counts = new String[2];
					for (int v = 2; v <= 3; v++) {
						HollowShape shape = new HollowShape(s.start().withVersion(v), world.terrain());
						BoundingBox box = shape.box();
						int baseTop = Math.min(world.maxY(), box.maxY() + 8);
						com.forzacode.a1016_02.world.sc2.kit.Overlay overlay = new com.forzacode.a1016_02.world.sc2.kit.Overlay(
								com.forzacode.a1016_02.world.sc2.kit.NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8), baseTop), true);
						for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
							for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
								BoundingBox column = new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15, world.maxY(), (cz << 4) + 15);
								java.util.Optional<BoundingBox> clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(column, box);
								if (clip.isPresent()) {
									shape.fill(overlay.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
								}
							}
						}
						Sc2Audit.Report report = Sc2Audit.of(shape, overlay, baseTop, List.of());
						counts[v - 2] = "floating " + report.floating() + ", leak " + report.leak() + ", fluid " + report.fluid() + ", sky " + report.sky()
								+ (only.isEmpty() ? "" : " " + report.where() + " summit " + shape.summit() + " foot " + shape.foot() + " floor " + shape.floorY());
						sums[(v - 2) * 2] += report.floating();
						sums[(v - 2) * 2 + 1] += report.leak() + report.fluid() + report.sky();
					}
					out.add(seed + " " + chunk + ": v2 " + counts[0] + " | v3 " + counts[1]);
				}
			}
		} finally {
			Snapshot.install(keep);
		}
		out.add(String.format(Locale.ROOT, "sum: v2 floating %d, leak+fluid+sky %d | v3 floating %d, leak+fluid+sky %d", sums[0], sums[1], sums[2], sums[3]));
		try {
			java.nio.file.Files.write(projectBuild().resolve("sc3hollowaudit.txt"), out);
		} catch (java.io.IOException e) {
			Sc2RealNoise.log("sc3hollowaudit: not written");
		}
		helper.succeed();
	}

	// --- the find, when asked ---

	/** The project's build directory (the test server runs in a folder of its own under it). */
	static java.nio.file.Path projectBuild() {
		java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
		for (java.nio.file.Path d = dir; d != null; d = d.getParent()) {
			if (java.nio.file.Files.exists(d.resolve("gradlew"))) {
				return d.resolve("build");
			}
		}
		return dir;
	}

	/**
	 * The hollow's and the floor's own find over the real noise, when asked ({@code -Da1016_02.sc3hollow=N}): the same
	 * worlds as {@code SelfCreated3FindTests} (its seeds, the default preset), each kind's candidates within its target's
	 * reach, worldgen's own verdict on each ({@code SelfCreated2HeightsTests.sited}), the nearest made sign, the share of
	 * candidates made, the refusals by reason and the siting's cost. Without the other kinds' spacing (the find test's
	 * whole run has it). Writes {@code build/sc3hollow.txt}.
	 */
	@GameTest(environment = HEIGHTS, maxTicks = 200)
	public void theHollowsAndTheFloorsFindWhenAsked(GameTestHelper helper) {
		String asked = System.getProperty("a1016_02.sc3hollow", "");
		if (asked.isEmpty() || asked.equals("false")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		int worlds = asked.equals("true") ? 20 : Integer.parseInt(asked);
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		Random seeds = new Random(0x5C3F1DL);
		List<String> out = new ArrayList<>();
		Map<String, AtomicInteger> reasons = new ConcurrentHashMap<>();
		Map<String, List<Long>> nanos = new ConcurrentHashMap<>();
		int mountains = 0;
		int k18 = 0;
		int k20 = 0;
		int[] made = new int[2];
		int[] candidates = new int[2];
		Snapshot keep = Snapshot.current();
		try {
			for (int i = 0; i < worlds; i++) {
				long seed = seeds.nextLong();
				Sc2RealNoise.World world = Sc2RealNoise.of(level, "normal", Sc2RealNoise.PRESETS.get("normal"), seed, -64, 384);
				ChunkPos origin = world.generator().getOrigin(world.random());
				BlockPos spawn = new BlockPos(origin.getMinBlockX() + 8, 64, origin.getMinBlockZ() + 8);
				Snapshot snapshot = new Snapshot(true, com.forzacode.a1016_02.core.Stage.TRACES, spawn, seed, 7L, Sc2RealNoise.snapshot(seed).configs(), true);
				boolean hills = SelfCreated3FindTests.mountainsWithin(level, world, spawn, 1200);
				double[] nearest = new double[2];
				int k = 0;
				for (String kind : List.of("s18", "s20")) {
					int reach = kind.equals("s18") ? 1200 : 900;
					Holder<Structure> structure = structures.get(kind);
					StructurePlacement placement = Sc2RealNoise.placement(level, structure);
					int r0 = Sc2Kind.get(kind).gates().minOriginDistance();
					List<ChunkPos> chunks = new ArrayList<>();
					for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, spawn.getX(), spawn.getZ(), reach + 160, Integer.MAX_VALUE)) {
						double mx = chunk.getMiddleBlockX();
						double mz = chunk.getMiddleBlockZ();
						if (Math.hypot(mx, mz) < r0 || Math.hypot(mx - spawn.getX(), mz - spawn.getZ()) < r0
								|| structure.value() instanceof Sc2Structure sc2 && Decided.excluded(sc2, world.structures(), List.of(), chunk) != null) {
							continue;
						}
						chunks.add(chunk);
					}
					List<Double> found = Collections.synchronizedList(new ArrayList<>());
					int index = k;
					AtomicInteger madeHere = new AtomicInteger();
					chunks.parallelStream().forEach(chunk -> {
						long t = System.nanoTime();
						Sc2Structure.Sited s = SelfCreated2HeightsTests.sited(world, structure, Sc2Kind.get(kind), chunk, snapshot);
						long dt = System.nanoTime() - t;
						String why = s.made() ? (s.shape() instanceof com.forzacode.a1016_02.world.sc2.s18.HollowShape h && h.summit() != null && !h.summit().hasPiece() ? "made, cut alone" : "made") : HollowFloorProbe.why(kind, world, chunk, snapshot, s.why());
						reasons.computeIfAbsent(kind + " " + why, x -> new AtomicInteger()).incrementAndGet();
						nanos.computeIfAbsent(kind + " " + why, x -> Collections.synchronizedList(new ArrayList<>())).add(dt);
						if (s.made()) {
							madeHere.incrementAndGet();
							BlockPos sign = s.shape().sign();
							found.add(Math.hypot(sign.getX() - spawn.getX(), sign.getZ() - spawn.getZ()));
						}
					});
					made[index] += madeHere.get();
					candidates[index] += chunks.size();
					nearest[index] = found.stream().mapToDouble(Double::doubleValue).min().orElse(99_999);
					if (kind.equals("s18") && Boolean.getBoolean("a1016_02.sc3hollow.why")) {
						chunks.parallelStream().forEach(chunk -> reasons.computeIfAbsent("s18 terrain " + HollowFloorProbe.terrain(world, chunk, snapshot),
								x -> new AtomicInteger()).incrementAndGet());
					}
					k++;
				}
				if (hills) {
					mountains++;
					k18 += nearest[0] <= 1200 ? 1 : 0;
				}
				k20 += nearest[1] <= 900 ? 1 : 0;
				out.add(String.format(Locale.ROOT, "world %d: 18 %.0f (mountains %s), 20 %.0f", seed, nearest[0], hills ? "yes" : "no", nearest[1]));
			}
		} finally {
			Snapshot.install(keep);
		}
		out.add(String.format(Locale.ROOT, "T2 18 within 1200: %d of %d worlds with mountains (%.0f %%); 20 within 900: %d of %d (%.0f %%)", k18, mountains,
				100.0 * k18 / Math.max(1, mountains), k20, worlds, 100.0 * k20 / worlds));
		out.add(String.format(Locale.ROOT, "made: 18 %d of %d candidates (%.1f %%); 20 %d of %d (%.1f %%)", made[0], candidates[0], 100.0 * made[0] / Math.max(1, candidates[0]),
				made[1], candidates[1], 100.0 * made[1] / Math.max(1, candidates[1])));
		for (Map.Entry<String, AtomicInteger> e : new java.util.TreeMap<>(reasons).entrySet()) {
			if (e.getKey().startsWith("s18 terrain")) {
				out.add(e.getKey() + ": " + e.getValue().get());
			}
		}
		for (Map.Entry<String, List<Long>> e : new java.util.TreeMap<>(nanos).entrySet()) {
			List<Long> ns = new ArrayList<>(e.getValue());
			Collections.sort(ns);
			out.add(String.format(Locale.ROOT, "%s: %d, median %.2f ms, worst %.1f ms", e.getKey(), ns.size(), ns.get(ns.size() / 2) / 1e6, ns.getLast() / 1e6));
		}
		try {
			java.nio.file.Files.write(projectBuild().resolve("sc3hollow.txt"), out);
		} catch (java.io.IOException e) {
			Sc2RealNoise.log("sc3hollow: not written");
		}
		helper.succeed();
	}
}
