package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Decided;
import com.forzacode.a1016_02.world.sc2.kit.Fault;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Overlay;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.s16.PyramidAnswers;
import com.forzacode.a1016_02.world.sc2.s16.PyramidOpening;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;
import com.forzacode.a1016_02.world.sc2.s16.PyramidSiting;
import com.forzacode.a1016_02.world.sc2.s16.Sc16Config;
import com.forzacode.a1016_02.world.sc2.s16.S16;
import com.forzacode.a1016_02.world.sc2.s19.LinePlan;
import com.forzacode.a1016_02.world.sc2.s19.LineShape;
import com.forzacode.a1016_02.world.sc2.s19.S19;
import com.forzacode.a1016_02.world.sc2.s19.Sc19Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.Vec3;

/**
 * R3-2's game tests (docs/SELF_CREATED_2_R3.md, "R3-2"): 16 v4 (the 191, the strip and its fault, the apex light), 16d's
 * way in at the waterline, 19 v3's lit foot; and, when asked ({@code -Da1016_02.sc3pyr=true}), the land pyramid's siting
 * over the real noise, slot by slot, with why each refused (written to {@code build/sc3pyr.txt}).
 */
public class SelfCreated3PyramidTests {
	private static final String ENV = "a1016_02:world/sc2_heights";
	/** The survey's siting times (ms) by outcome. */
	private static final Map<String, List<Double>> TIMES = new java.util.concurrent.ConcurrentHashMap<>();
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | 512;
	/** The flat test world's grass. */
	private static final int GRASS = -61;
	/** This run's own patch of the test world (far from round 2's). */
	private static final int BASE_X = -16 * ThreadLocalRandom.current().nextInt(150_000, 170_000);
	private static final int BASE_Z = -16 * ThreadLocalRandom.current().nextInt(150_000, 170_000);

	/** Each test's own patch: {@code k} slots of 1024 along x from the run's base. */
	private static int slotX(int k) {
		return BASE_X + k * 1024;
	}

	// --- 19 v3: the lit foot ---

	/**
	 * 19 v3 (D-110): at every opening, on each side of the wall, the lighting fault over the 8 x 4 beside it at the ground
	 * (level 15 on its grid, one over the grass), waterlogged where the foot is water; none in the openings themselves. The
	 * same segment fixed as version 2 (R12-2) builds no light, its box two out as before, and its wall cell for cell the
	 * same as version 3's.
	 */
	@GameTest(maxTicks = 600)
	public void theLineHasALitFootAtEveryOpening(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int px = slotX(0) + 8;
		LinePlan.Segment s = LinePlan.cut(LinePlan.Arm.SOUTH, px, BASE_Z, 256, 0x5EEDL, 48, 9, level.getMinY() + 6, level.getMaxY() - 6);
		int pond = s.holes().get(1);
		flat(level, new BoundingBox(px - 6, GRASS, s.t0() - 4, px + 6, s.top(), s.t1() + 4));
		fill(level, new BoundingBox(px - 6, GRASS, pond - 6, px + 6, GRASS, pond + 7), Blocks.WATER.defaultBlockState());
		Terrains.Frozen terrain = Live.terrain(level);
		LineShape shape = new LineShape(s, terrain, 3);
		Live.place(level, S19.KIND, shape, terrain, s.start());
		List<String> wrong = new ArrayList<>();
		for (int hole : s.holes()) {
			for (BoundingBox square : shape.litSquares(hole)) {
				int lights = 0;
				for (BlockPos pos : BlockPos.betweenClosed(square.minX(), GRASS - 1, square.minZ(), square.maxX(), GRASS + 3 + LineShape.WALL_LIGHT_UP, square.maxZ())) {
					BlockState state = level.getBlockState(pos);
					if (Fault.isLight(state)) {
						lights++;
						boolean wet = hole == pond;
						boolean ground = pos.getY() == (wet ? GRASS : GRASS + 1) && state.getValue(BlockStateProperties.WATERLOGGED) == wet;
						// Or one of the lights up the wall's foot, against its face, over the water's top.
						boolean face = Math.abs(pos.getX() - px) == 1 && pos.getY() > GRASS && !state.getValue(BlockStateProperties.WATERLOGGED);
						if (!ground && !face || state.getValue(LightBlock.LEVEL) != Fault.LEVEL) {
							wrong.add("light " + pos.toShortString() + " " + state);
						}
					}
				}
				if (lights < 2) {
					wrong.add(lights + " lights beside the opening at " + hole + " (square " + square + ", ground " + terrain.ground(square.minX(), square.minZ())
							+ ", surface " + terrain.surface(square.minX(), square.minZ()) + ", box " + shape.box() + ")");
				}
			}
			for (int t = hole; t <= hole + 1; t++) {
				for (int y = GRASS; y <= GRASS + 3; y++) {
					if (Fault.isLight(level.getBlockState(new BlockPos(px, y, t)))) {
						wrong.add("a light in the opening at " + t);
					}
				}
			}
		}
		// Version 2 of the same segment, 64 east: no light, its box two out, its wall the same.
		LinePlan.Segment s2 = LinePlan.cut(LinePlan.Arm.SOUTH, px + 64, BASE_Z, 256, 0x5EEDL, 48, 9, level.getMinY() + 6, level.getMaxY() - 6);
		flat(level, new BoundingBox(px + 58, GRASS, s2.t0() - 4, px + 70, s2.top(), s2.t1() + 4));
		fill(level, new BoundingBox(px + 58, GRASS, pond - 6, px + 70, GRASS, pond + 7), Blocks.WATER.defaultBlockState());
		Terrains.Frozen terrain2 = Live.terrain(level);
		LineShape old = new LineShape(s2, terrain2, 2);
		Live.place(level, S19.KIND, old, terrain2, s2.start());
		helper.assertTrue(old.box().maxX() - old.box().minX() == 2 * LineShape.REACH && shape.box().maxX() - shape.box().minX() == 2 * LineShape.LIT_REACH,
				"the boxes: " + old.box() + " / " + shape.box());
		for (int z = s.t0(); z <= s.t1() && wrong.size() < 16; z++) {
			for (int dx = -LineShape.LIT_REACH; dx <= LineShape.LIT_REACH; dx++) {
				for (int y = GRASS - 1; y <= GRASS + 40; y++) {
					BlockState now = level.getBlockState(new BlockPos(px + dx, y, z));
					BlockState before = level.getBlockState(new BlockPos(px + 64 + dx, y, z));
					if (Fault.isLight(before) || !Fault.isLight(now) && now != before) {
						wrong.add("version 2 differs at " + dx + ", " + y + ", " + z + ": " + before + " / " + now);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the lit foot: " + wrong);
		helper.succeed();
	}

	// --- 16 v4 ---

	/**
	 * The 191 (D-115): base 191, 96 high, its pit 88 deep, its grounds 32 past it; on a chunk's middle its box stays within
	 * the kit's reach; on flat land the weights' 191 is made at the slot's middle; in a world too short for its pit the
	 * siting takes the 127 there instead (SC2-H: never a clipped one); weights and keys of config version 4.
	 */
	@GameTest
	public void theLargestStandsWithinReachAndHeight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(PyramidShape.BASES[3] == 191 && PyramidShape.halfWidth(3) == 95 && PyramidShape.height(3) == 96 && PyramidShape.pitDepth(3) == 88
				&& PyramidShape.reach(3) == 95 + 32 + 1, "the 191's numbers");
		ChunkPos far = new ChunkPos(160, 40);
		BlockPos centre = new BlockPos(far.getMiddleBlockX(), 71, far.getMiddleBlockZ());
		PyramidShape big = shape(level, centre, PyramidShape.variant(3, false, true, false, 1), 0x191L, 70, Integer.MIN_VALUE);
		helper.assertTrue(Sc2Structure.withinReach(big.box(), far) && big.pitDepth() == 88 && big.topCourse() == 94, "the 191's box: " + big.box());
		Sc16Config config = new Sc16Config();
		config.sizeWeights = new double[] {0, 0, 0, 1};
		config.sealedChance = 0;
		Terrain flat = flatTerrain(level, (x, z) -> 70, (x, z) -> 70, false);
		Optional<com.forzacode.a1016_02.world.sc2.kit.Start> made = PyramidSiting.LAND.find(query(far, flat, -64, 320), config, new PyramidSiting.Samples(flat));
		helper.assertTrue(made.isPresent() && PyramidShape.size(made.get().variant()) == 3 && made.get().origin().equals(new BlockPos(centre.getX(), 71,
				centre.getZ())) && PyramidShape.stripDepth(made.get().variant()) == 1, "the 191 on flat land: " + made);
		Optional<com.forzacode.a1016_02.world.sc2.kit.Start> shorter = PyramidSiting.LAND.find(query(far, flat, 0, 256), config, new PyramidSiting.Samples(flat));
		helper.assertTrue(shorter.isPresent() && PyramidShape.size(shorter.get().variant()) < 3, "a short world takes a smaller one: " + shorter);
		helper.assertTrue(Sc16Config.CURRENT == 4 && config.weight(3) == 1 && new Sc16Config().spread(3) == 40 && new Sc16Config().spread(2) == 24
				&& new Sc16Config().spread(1) == 16, "config version 4");
		Sc16Config old = new Sc16Config();
		old.version = 3;
		old.sizeWeights = new double[] {0.1, 0.45, 0.45};
		old.apexLightChance = 0.6;
		old.closeAfterSeconds = 90;
		old.closeMinDistance = 20;
		old.darkStepsMin = 12;
		old.darkEveryMin = 8;
		old.darkEveryMax = 12;
		old.glimpseAfter = 4;
		old.upgrade();
		helper.assertTrue(old.sizeWeights.length == 4 && old.apexLightChance == 0.75 && old.version == 4 && old.closeAfterSeconds == 40
				&& old.closeMinDistance == 12 && old.darkStepsMin == 6 && old.darkEveryMin == 5 && old.darkEveryMax == 8 && old.glimpseAfter == 3,
				"the upgrade from 3");
		Sc16Config mine = new Sc16Config();
		mine.version = 3;
		mine.closeAfterSeconds = 120;
		mine.upgrade();
		helper.assertTrue(mine.closeAfterSeconds == 120 && mine.trailAfterSeconds == 6 && mine.openAfterSeconds == 20, "a changed value kept");
		helper.succeed();
	}

	/**
	 * 16 v4's sign (D-110) on an open 95 and a sealed one: the chunk-aligned 16-wide strip of the way in's face holding the
	 * mouth stands one back and is laid fresh (plain brick a course under where the face was); the lighting fault over the
	 * strip and the chunk at its foot (lights on the surface, never in the mass, none within 3 of the mouth, which stays
	 * open); outside the strip the face where it was. The sealed one: the same strip and light, no mouth.
	 */
	@GameTest
	public void theStripStandsBackAndIsLitOnOpenAndSealed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (boolean sealed : new boolean[] {false, true}) {
			BlockPos centre = new BlockPos(16 * 300 + 8, 71, 16 * (sealed ? 60 : 40) + 8);
			Overlay overlay = new Overlay(flatBase(70, Integer.MIN_VALUE));
			Terrain flat = flatTerrain(level, (x, z) -> 70, (x, z) -> 70, false);
			PyramidShape shape = new PyramidShape(new com.forzacode.a1016_02.world.sc2.kit.Start(centre, net.minecraft.core.Direction.EAST,
					PyramidShape.variant(1, sealed, false, false, 1), 0x5791L, 4), flat);
			overlay.fill(shape, flat, level.registryAccess(), -64, 320, Optional.empty());
			int r = shape.halfWidth();
			int y0 = shape.baseY();
			BoundingBox band = shape.litBand();
			// The strip: one back, fresh brick under; just outside it, the face where it was (but for his wear).
			int outside = 0;
			int standing = 0;
			for (int f = 12; f <= r - 4; f++) {
				for (int side = -6; side <= 7; side += 13) {
					BlockPos old = shape.local(side, r - f, f);
					if (!shape.inStrip(old.getX(), old.getZ())) {
						wrong.add((sealed ? "sealed: " : "") + "not in the strip " + side + ", " + f);
						continue;
					}
					BlockState was = overlay.get(old.getX(), old.getY(), old.getZ());
					BlockState under = overlay.get(old.getX(), old.getY() - 1, old.getZ());
					if (!(was.isAir() || Fault.isLight(was)) || !under.is(Blocks.BRICKS)) {
						wrong.add((sealed ? "sealed: " : "") + "the strip at " + side + ", " + f + ": " + was + " over " + under);
					}
					for (int more = 2; more <= 4; more++) {
						BlockPos beside = shape.local(side < 0 ? side - more : side + more, r - f, f);
						if (!shape.inStrip(beside.getX(), beside.getZ())) {
							outside++;
							standing += overlay.get(beside.getX(), beside.getY(), beside.getZ()).isAir() ? 0 : 1;
						}
					}
				}
			}
			if (outside == 0 || standing < outside * 0.8) {
				wrong.add((sealed ? "sealed: " : "") + "beside the strip " + standing + " of " + outside + " where the face was");
			}
			int lights = 0;
			List<BlockPos> mouth = shape.wayIn();
			for (BlockPos pos : BlockPos.betweenClosed(band.minX(), y0 - 2, band.minZ(), band.maxX(), y0 + r + 2, band.maxZ())) {
				if (!Fault.isLight(overlay.get(pos.getX(), pos.getY(), pos.getZ()))) {
					continue;
				}
				lights++;
				if (shape.inMass(pos.getX(), pos.getY(), pos.getZ()) && !shape.inStrip(pos.getX(), pos.getZ())) {
					wrong.add("a light in the mass " + pos.toShortString());
				}
				for (BlockPos m : mouth) {
					if (m.distManhattan(pos) <= PyramidShape.MOUTH_DARK) {
						wrong.add("a light by the mouth " + pos.toShortString());
					}
				}
			}
			int columns = (band.maxX() - band.minX() + 1) * (band.maxZ() - band.minZ() + 1);
			if (lights < columns / 9 / 2) {
				wrong.add((sealed ? "sealed: " : "") + lights + " lights over " + columns + " columns");
			}
			for (BlockPos m : mouth) {
				if (!overlay.get(m.getX(), m.getY(), m.getZ()).isAir()) {
					wrong.add("the mouth shut at " + m.toShortString());
				}
			}
			if (sealed != mouth.isEmpty()) {
				wrong.add("the way in: " + mouth.size());
			}
			if (!Fault.chunkAligned(new BoundingBox(band.minX(), 0, band.minZ(), band.maxX(), 0, band.maxZ())) && band.maxZ() - band.minZ() != 15) {
				wrong.add("the band across is not a chunk: " + band);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the strip: " + wrong);
		helper.succeed();
	}

	/**
	 * 16d v4 (R11-18): its way in at the waterline, a 2 x 2 whose sill is one over the water, into the dry core; from it a
	 * flight 2 wide to the core's floor, every step one down and one in, solid under each, kerbed both sides one higher;
	 * no water anywhere inside; 16b there (the inner mouth open, the bricks beside it whole, none of them opening the core
	 * to the sea if moved); the apex lit; the water at the strip's foot lit (waterlogged lights on the water's top).
	 */
	@GameTest
	public void theSeaFormHasAWayInAtTheWaterline(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int floor = 40;
		int water = 62;
		BlockPos centre = new BlockPos(16 * 320 + 8, floor + 1, 16 * 40 + 8);
		Overlay overlay = new Overlay(flatBase(floor, water));
		Terrain sea = flatTerrain(level, (x, z) -> floor, (x, z) -> water, true);
		PyramidShape shape = new PyramidShape(new com.forzacode.a1016_02.world.sc2.kit.Start(centre, net.minecraft.core.Direction.EAST,
				PyramidShape.variant(1, false, true, true, 1), 0x5EA1L, 4), sea);
		overlay.fill(shape, sea, level.registryAccess(), -64, 320, Optional.empty());
		List<String> wrong = new ArrayList<>();
		int m = shape.wayLayer();
		int r = shape.halfWidth();
		if (m != water + 2 - (floor + 1) || !shape.hasWayIn() || shape.entrance().getY() != water + 2) {
			wrong.add("the way in's course " + m + ", entrance " + shape.entrance());
		}
		for (int side = 0; side <= 1; side++) {
			BlockPos sill = shape.local(side, m - 1, r - m);
			if (sill.getY() != water + 1 || !overlay.get(sill.getX(), sill.getY(), sill.getZ()).is(Blocks.BRICKS)) {
				wrong.add("the sill " + sill.toShortString() + " " + overlay.get(sill.getX(), sill.getY(), sill.getZ()));
			}
		}
		for (BlockPos cell : shape.wayIn()) {
			if (!overlay.get(cell.getX(), cell.getY(), cell.getZ()).isAir()) {
				wrong.add("the way in at " + cell.toShortString());
			}
		}
		// The flight: feet and head open, solid under, the kerbs one higher.
		for (int j = 0; j <= m; j++) {
			int feet = m - j;
			int f = r - m - 2 - j;
			for (int side = 0; side <= 1; side++) {
				BlockPos at = shape.local(side, feet, f);
				if (!overlay.get(at.getX(), at.getY(), at.getZ()).isAir() || !overlay.get(at.getX(), at.getY() + 1, at.getZ()).isAir()) {
					wrong.add("step " + j + " shut at " + at.toShortString());
				}
				BlockState under = overlay.get(at.getX(), at.getY() - 1, at.getZ());
				if (!under.is(Blocks.BRICKS) && !(feet == 0 && under.is(Blocks.SAND))) {
					wrong.add("step " + j + " stands on " + under);
				}
			}
			for (int side : new int[] {-1, 2}) {
				BlockPos kerb = shape.local(side, feet, f);
				if (feet > 0 && j > 0 && !overlay.get(kerb.getX(), kerb.getY(), kerb.getZ()).is(Blocks.BRICKS)) {
					wrong.add("no kerb at step " + j + " side " + side);
				}
			}
		}
		// Dry inside.
		BoundingBox box = shape.box();
		for (int x = centre.getX() - r; x <= centre.getX() + r; x += 2) {
			for (int z = centre.getZ() - r; z <= centre.getZ() + r; z += 2) {
				for (int y = floor; y <= floor + r; y++) {
					if (shape.core(x, y, z) && !overlay.get(x, y, z).getFluidState().isEmpty()) {
						wrong.add("water inside at " + x + ", " + y + ", " + z);
						break;
					}
				}
			}
		}
		// 16b there: the inner mouth open, the bricks beside it whole and each closed to the outside but for the core.
		for (BlockPos cell : shape.innerMouth()) {
			if (!overlay.get(cell.getX(), cell.getY(), cell.getZ()).isAir()) {
				wrong.add("the inner mouth shut");
			}
		}
		for (BlockPos cell : shape.besideMouth()) {
			if (!overlay.get(cell.getX(), cell.getY(), cell.getZ()).is(Blocks.BRICKS)) {
				wrong.add("beside the mouth " + cell.toShortString() + " " + overlay.get(cell.getX(), cell.getY(), cell.getZ()));
			}
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
				BlockPos n = cell.relative(d);
				BlockState state = overlay.get(n.getX(), n.getY(), n.getZ());
				if (!shape.core(n.getX(), n.getY(), n.getZ()) && !state.is(Blocks.BRICKS) && !shape.innerMouth().contains(n)) {
					wrong.add("moving " + cell.toShortString() + " opens to " + state + " at " + n.toShortString());
				}
			}
		}
		BlockPos apex = shape.apex().orElseThrow();
		int wetLights = 0;
		BoundingBox band = shape.litBand();
		for (BlockPos pos : BlockPos.betweenClosed(band.minX(), water, band.minZ(), band.maxX(), water, band.maxZ())) {
			BlockState state = overlay.get(pos.getX(), pos.getY(), pos.getZ());
			wetLights += Fault.isLight(state) && state.getValue(BlockStateProperties.WATERLOGGED) ? 1 : 0;
		}
		if (!overlay.get(apex.getX(), apex.getY(), apex.getZ()).is(Blocks.GLOWSTONE) || wetLights < 4) {
			wrong.add("the lights: apex " + overlay.get(apex.getX(), apex.getY(), apex.getZ()) + ", " + wetLights + " on the water");
		}
		helper.assertTrue(box.minY() <= floor && wrong.isEmpty(), "the sea form's way in: " + wrong);
		helper.succeed();
	}

	/**
	 * FIX-R3-C (R3A-6), 16d version 6: a torch on the flight's right kerb every {@value PyramidShape#TRAIL_EVERY} steps
	 * from {@value PyramidShape#TRAIL_FROM}, each on brick, off the walked cells; last the floor's torch on its cobblestone
	 * on the core's sand floor, clear of the flight; 16c counts the steps down the flight (the pit's rule at sea), its
	 * glimpse on the floor by that light; the lighting fault over the top courses round the apex. Version 5 at sea has
	 * none of it and builds as version 4.
	 */
	@GameTest
	public void theSeaFormHasATrailDownItsFlightAndALitTopFromVersion6(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int floor = 40;
		int water = 62;
		BlockPos centre = new BlockPos(16 * 330 + 8, floor + 1, 16 * 40 + 8);
		Terrain sea = flatTerrain(level, (x, z) -> floor, (x, z) -> water, true);
		List<String> wrong = new ArrayList<>();
		Overlay[] built = new Overlay[3];
		PyramidShape[] shapes = new PyramidShape[3];
		int[] versions = {4, 5, 6};
		for (int i = 0; i < 3; i++) {
			shapes[i] = new PyramidShape(new com.forzacode.a1016_02.world.sc2.kit.Start(centre, net.minecraft.core.Direction.EAST, PyramidShape.variant(1, false, true,
					true, 1), 0x5EA1L, versions[i]), sea);
			built[i] = new Overlay(flatBase(floor, water));
			built[i].fill(shapes[i], sea, level.registryAccess(), -64, 320, Optional.empty());
		}
		PyramidShape shape = shapes[2];
		Overlay overlay = built[2];
		int m = shape.wayLayer();
		List<BlockPos> trail = shape.trail();
		if (!shape.seaTrail() || trail.size() != (m - 1 - PyramidShape.TRAIL_FROM) / PyramidShape.TRAIL_EVERY + 2) {
			wrong.add("a trail of " + trail.size() + " for a flight of " + m);
		}
		java.util.Set<BlockPos> walked = new java.util.HashSet<>(shape.flight());
		for (BlockPos cell : trail.subList(0, trail.size() - 1)) {
			if (!overlay.get(cell.getX(), cell.getY(), cell.getZ()).is(Blocks.TORCH) || !overlay.get(cell.getX(), cell.getY() - 1, cell.getZ()).is(Blocks.BRICKS)
					|| walked.contains(cell) || walked.contains(cell.below())) {
				wrong.add("the flight's torch at " + cell.toShortString() + ": " + overlay.get(cell.getX(), cell.getY(), cell.getZ()));
			}
		}
		BlockPos light = shape.floorLight();
		if (!trail.getLast().equals(light.above()) || !overlay.get(light.getX(), light.getY(), light.getZ()).is(Blocks.COBBLESTONE)
				|| !overlay.get(light.getX(), light.getY() + 1, light.getZ()).is(Blocks.TORCH) || !overlay.get(light.getX(), light.getY() - 1, light.getZ()).is(Blocks.SAND)
				|| light.getY() != shape.baseY() || !shape.core(light.getX(), light.getY(), light.getZ())) {
			wrong.add("the floor's light at " + light.toShortString() + ": " + overlay.get(light.getX(), light.getY(), light.getZ()) + " on "
					+ overlay.get(light.getX(), light.getY() - 1, light.getZ()));
		}
		// 16c's steps: the flight's first step in the core is 1 down, its foot the descent's last.
		BlockPos top = shape.flight().get(2);
		BlockPos foot = shape.flight().getLast();
		if (!shape.hasDescent() || shape.descentSteps() != m + 1 || shape.stepsDown(top.getX(), top.getY(), top.getZ()) != 1
				|| shape.stepsDown(foot.getX(), foot.getY(), foot.getZ()) != m || shape.stepsDown(centre.getX(), centre.getY() + m + 40, centre.getZ()) != -1) {
			wrong.add("16c's steps: " + shape.descentSteps() + ", top " + shape.stepsDown(top.getX(), top.getY(), top.getZ()) + ", foot "
					+ shape.stepsDown(foot.getX(), foot.getY(), foot.getZ()));
		}
		ServerPlayer ana = new SelfCreated2KitTests.TestPlayer(level, "ana", net.minecraft.world.level.GameType.SURVIVAL);
		ana.snapTo(Vec3.atBottomCenterOf(foot), 0.0F, 0.0F);
		for (Vec3 spot : PyramidAnswers.floorSpots(shape, ana)) {
			BlockPos at = BlockPos.containing(spot);
			if (at.getY() != shape.baseY() || at.distManhattan(light) > 4 || !overlay.get(at.getX(), at.getY(), at.getZ()).isAir()
					|| !overlay.get(at.getX(), at.getY() + 1, at.getZ()).isAir()) {
				wrong.add("a glimpse spot at " + at.toShortString());
			}
		}
		// The lit top: lights over the courses round the apex, none on version 5.
		BoundingBox cap = shape.capBand();
		int lit = 0;
		int litFive = 0;
		for (BlockPos pos : BlockPos.betweenClosed(cap.minX(), cap.minY(), cap.minZ(), cap.maxX(), cap.maxY(), cap.maxZ())) {
			lit += Fault.isLight(overlay.get(pos.getX(), pos.getY(), pos.getZ())) ? 1 : 0;
			litFive += Fault.isLight(built[1].get(pos.getX(), pos.getY(), pos.getZ())) ? 1 : 0;
		}
		if (lit - litFive < 25) {
			wrong.add("the lit top: " + lit + " lights, version 5 " + litFive);
		}
		// Version 5 at sea: as version 4, no trail.
		if (shapes[1].seaTrail() || !shapes[1].trail().isEmpty() || shapes[1].hasDescent() || shapes[1].capBand() != null) {
			wrong.add("version 5 at sea has the trail");
		}
		BoundingBox box = shape.box();
		int differ = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			differ += built[0].get(pos.getX(), pos.getY(), pos.getZ()).equals(built[1].get(pos.getX(), pos.getY(), pos.getZ())) ? 0 : 1;
		}
		if (differ > 0) {
			wrong.add("version 5 at sea differs from version 4 in " + differ + " cells");
		}
		helper.assertTrue(wrong.isEmpty(), "16d version 6: " + wrong);
		helper.succeed();
	}

	/**
	 * FIX-R3-C: 16d's lit water at the strip's foot stays under a fill over itself (what the real run's check does: the
	 * shape filled again over the world it was built into). Every wet light stays waterlogged in the water's top cell and
	 * no light goes into the air over one: with the fault reading its own wet light as a solid top, a second light stood
	 * over each, the real run's "air>light" cells over 16d's water.
	 */
	@GameTest
	public void theSeaFormsLitWaterStaysUnderAFillOverItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int floor = 40;
		int water = 62;
		BlockPos centre = new BlockPos(16 * 350 + 8, floor + 1, 16 * 40 + 8);
		Terrain sea = flatTerrain(level, (x, z) -> floor, (x, z) -> water, true);
		PyramidShape shape = new PyramidShape(new com.forzacode.a1016_02.world.sc2.kit.Start(centre, net.minecraft.core.Direction.EAST, PyramidShape.variant(1, false, true,
				true, 1), 0x5EA1L, PyramidShape.VERSION), sea);
		Overlay first = new Overlay(flatBase(floor, water));
		first.fill(shape, sea, level.registryAccess(), -64, 320, Optional.empty());
		Overlay again = new Overlay((x, y, z) -> first.get(x, y, z));
		again.fill(shape, sea, level.registryAccess(), -64, 320, Optional.empty());
		BoundingBox band = shape.litBand();
		int wet = 0;
		int lost = 0;
		int over = 0;
		for (BlockPos pos : BlockPos.betweenClosed(band.minX(), water, band.minZ(), band.maxX(), water + 1, band.maxZ())) {
			BlockState a = first.get(pos.getX(), pos.getY(), pos.getZ());
			BlockState b = again.get(pos.getX(), pos.getY(), pos.getZ());
			if (pos.getY() == water && Fault.isLight(a) && a.getValue(BlockStateProperties.WATERLOGGED)) {
				wet++;
				lost += b.equals(a) ? 0 : 1;
			}
			if (pos.getY() == water + 1 && Fault.isLight(b) && !Fault.isLight(a)) {
				over++;
			}
		}
		helper.assertTrue(wet >= 4 && lost == 0 && over == 0, "the lit water under a second fill: " + wet + " wet lights, " + lost + " changed, " + over
				+ " new lights over the water");
		helper.succeed();
	}

	/**
	 * R12-2: a start tied to version 3 (land and sea) still builds version 3: no strip, no light over the face, the sea form
	 * closed with its entrance at its foot (the kit's {@code everyKindsCurrentVersionStillBuildsAsBefore} pins their fills).
	 */
	@GameTest
	public void aVersion3StartStillBuildsVersion3(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		BlockPos centre = new BlockPos(16 * 340 + 8, 71, 16 * 40 + 8);
		Terrain flat = flatTerrain(level, (x, z) -> 70, (x, z) -> 70, false);
		Overlay overlay = new Overlay(flatBase(70, Integer.MIN_VALUE));
		PyramidShape v3 = new PyramidShape(new com.forzacode.a1016_02.world.sc2.kit.Start(centre, net.minecraft.core.Direction.EAST,
				PyramidShape.variant(1, false, false, false, 1), 0x5791L, 3), flat);
		overlay.fill(v3, flat, level.registryAccess(), -64, 320, Optional.empty());
		if (v3.litBand() != null || v3.inStrip(centre.getX() + 20, centre.getZ())) {
			wrong.add("a strip on version 3");
		}
		for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<BlockState> e : overlay.cells().long2ObjectEntrySet()) {
			if (Fault.isLight(e.getValue())) {
				wrong.add("a light at " + BlockPos.of(e.getLongKey()).toShortString());
				break;
			}
		}
		Terrain sea = flatTerrain(level, (x, z) -> 40, (x, z) -> 62, true);
		PyramidShape sea3 = new PyramidShape(new com.forzacode.a1016_02.world.sc2.kit.Start(centre.atY(41), net.minecraft.core.Direction.EAST,
				PyramidShape.variant(1, true, true, true), 0x5EA1L, 3), sea);
		if (sea3.hasWayIn() || sea3.wayLayer() != 0 || !sea3.entrance().equals(sea3.local(0, 0, sea3.halfWidth()))) {
			wrong.add("the sea form of version 3: " + sea3.entrance());
		}
		helper.assertTrue(wrong.isEmpty(), "version 3: " + wrong);
		helper.succeed();
	}

	/**
	 * 19 v3's distances and marker (D-109, D-112): every segment of a world starts 512 to 768 out on its half-axis and is
	 * 512 to 1 024 long; the marker structure's chunks are each segment's middle chunk (in step with the plan).
	 */
	@GameTest
	public void theLineIsNearerAndLongerAndItsMarkerKeepsStep(GameTestHelper helper) {
		Sc19Config config = new Sc19Config();
		helper.assertTrue(config.startMin == 512 && config.startSpread == 256 && config.lengthMin == 512 && config.lengthMax == 1024, "the line's numbers");
		List<String> wrong = new ArrayList<>();
		for (long seed : new long[] {1L, 77L, -9_000_000_001L}) {
			Snapshot snapshot = Sc2RealNoise.snapshot(seed);
			List<LinePlan.Segment> segments = com.forzacode.a1016_02.world.sc2.s19.LineFeature.segments(snapshot, -64, 320);
			List<ChunkPos> marks = com.forzacode.a1016_02.world.sc2.s19.LineMarker.chunks(snapshot, -64, 320);
			if (segments.isEmpty() || marks.size() != segments.size()) {
				wrong.add(seed + ": " + segments.size() + " segments, " + marks.size() + " marks");
				continue;
			}
			for (int i = 0; i < segments.size(); i++) {
				LinePlan.Segment s = segments.get(i);
				int near = Math.min(Math.abs(s.t0()), Math.abs(s.t1()));
				if (near < 512 || near > 768 + LinePlan.BAY || s.length() < 512 - 2 * LinePlan.BAY || s.length() > 1024) {
					wrong.add(seed + ": " + s.arm() + " from " + near + ", " + s.length() + " long");
				}
				int t = (s.t0() + s.t1()) / 2;
				if (!marks.get(i).equals(ChunkPos.containing(new BlockPos(s.x(t), 0, s.z(t))))) {
					wrong.add(seed + ": the mark of " + s.arm());
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the line's plan: " + wrong);
		helper.succeed();
	}

	// --- the playtest: a first visit meets an answer, the inside a purpose, a sealed one an opening ---

	/**
	 * Version 4's trail (R3-2, after Mariano's "no purpose"): torches down the pit's near face on the way in's line, every
	 * {@value PyramidShape#TRAIL_EVERY} steps from {@value PyramidShape#TRAIL_FROM} under the rim, each standing on a tread
	 * in the pit's air (never in the dead end), the floor's torch last; built so. 16e on the place's first visit: nothing
	 * before {@code r} seconds on the rim, then the trail's lights go one by one out of view, the farthest first (the
	 * floor's), never within {@value PyramidAnswers#TRAIL_FAR} of her, one answer counted (the rest its parts); in view,
	 * none; a second visit, nothing more. The same place built as version 3 has no trail.
	 */
	@GameTest(maxTicks = 600)
	public void theFirstVisitMeetsTheTrailGoingOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Rig rig = rig(level, 200_008, 163_008, PyramidShape.variant(0, false, false, false, 1));
		PyramidShape shape = rig.shape();
		List<BlockPos> trail = shape.trail();
		List<String> wrong = new ArrayList<>();
		if (trail.size() < 4) {
			wrong.add("a trail of " + trail.size());
		}
		for (BlockPos cell : trail) {
			if (!level.getBlockState(cell).is(Blocks.TORCH) || !shape.pit(cell.getX(), cell.getY(), cell.getZ()) || shape.tunnel(cell.getX(), cell.getY(), cell.getZ())
					|| !level.getBlockState(cell.below()).isSolid()) {
				wrong.add("the trail at " + cell.toShortString() + ": " + level.getBlockState(cell) + " on " + level.getBlockState(cell.below()));
			}
		}
		if (!trail.isEmpty() && shape.baseY() - trail.getFirst().getY() != PyramidShape.TRAIL_FROM) {
			wrong.add("the trail starts " + (shape.baseY() - trail.getFirst().getY()) + " down");
		}
		ServerPlayer ana = new SelfCreated2KitTests.TestPlayer(level, "ana", net.minecraft.world.level.GameType.SURVIVAL);
		ana.setHealth(20.0F);
		BlockPos rim = shape.insideMouth();
		ana.snapTo(Vec3.atBottomCenterOf(rim), 0.0F, 0.0F);
		rig.inside().add(ana);
		rig.people().add(ana);
		rig.pyramid().entered(rig.place(), ana, 0);
		MinecraftServer server = level.getServer();
		rig.at(server, 20 * 5);
		if (trail.stream().anyMatch(cell -> !level.getBlockState(cell).is(Blocks.TORCH)) || !rig.answered().isEmpty()) {
			wrong.add("a light gone before 6 s: " + rig.answered());
		}
		// In view: nothing goes.
		rig.seen().set(true);
		for (long t = 20 * 7; t <= 20 * 20; t += 20) {
			rig.at(server, t);
		}
		if (trail.stream().anyMatch(cell -> !level.getBlockState(cell).is(Blocks.TORCH))) {
			wrong.add("a light gone in view");
		}
		rig.seen().set(false);
		for (long t = 20 * 21; t <= 20 * 120; t += 20) {
			rig.at(server, t);
		}
		BlockPos floor = trail.getLast();
		if (level.getBlockState(floor).is(Blocks.TORCH)) {
			wrong.add("the floor's light still lit");
		}
		for (BlockPos cell : trail) {
			boolean near = Vec3.atCenterOf(cell).distanceTo(ana.position()) < PyramidAnswers.TRAIL_FAR;
			if (near != level.getBlockState(cell).is(Blocks.TORCH)) {
				wrong.add("the light at " + cell.toShortString() + (near ? " gone near her" : " still lit"));
			}
		}
		if (!rig.answered().equals(List.of("16e:ana")) || rig.data().spent(rig.place(), "16e").ever() != 1) {
			wrong.add("answered " + rig.answered());
		}
		// A second visit: the place had it.
		rig.pyramid().left(rig.place(), ana);
		level.setBlock(floor, Blocks.TORCH.defaultBlockState(), FLAGS);
		rig.day().incrementAndGet();
		rig.pyramid().entered(rig.place(), ana, 20 * 200);
		for (long t = 20 * 201; t <= 20 * 240; t += 20) {
			rig.at(server, t);
		}
		if (!level.getBlockState(floor).is(Blocks.TORCH) || rig.answered().size() != 1) {
			wrong.add("again on a second visit: " + rig.answered());
		}
		PyramidShape v3 = new PyramidShape(new com.forzacode.a1016_02.world.sc2.kit.Start(shape.entrance(), shape.facing(), PyramidShape.variant(0, false, false,
				false), 1L, 3), Terrains.noise(level));
		if (!v3.trail().isEmpty()) {
			wrong.add("a trail on version 3");
		}
		forget(server, S16.CAUSE, shape.box());
		helper.assertTrue(wrong.isEmpty(), "16e: " + wrong);
		helper.succeed();
	}

	/**
	 * 16b sooner (the playtest): with the new defaults the way in closes once a player carrying a pickaxe has been inside
	 * 40 s and stands 12 or more from the inner mouth (round 2: 90 s, 20); not before 40 s.
	 */
	@GameTest(maxTicks = 400)
	public void theWayInClosesSooner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig rig = rig(level, 212_008, 163_008, PyramidShape.variant(0, false, false, false, 1));
		PyramidShape shape = rig.shape();
		ServerPlayer cy = new SelfCreated2KitTests.TestPlayer(level, "cy", net.minecraft.world.level.GameType.SURVIVAL);
		cy.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
		cy.setHealth(20.0F);
		// Twelve steps down the pit on the far side, 13 or more from the inner mouth.
		BlockPos feet = shape.local(0, -12, -(shape.pitOpening() - 11));
		cy.snapTo(Vec3.atBottomCenterOf(feet), 0.0F, 0.0F);
		rig.inside().add(cy);
		rig.people().add(cy);
		rig.pyramid().entered(rig.place(), cy, 0);
		List<String> wrong = new ArrayList<>();
		if (Vec3.atCenterOf(shape.innerMouth().getFirst()).distanceTo(cy.position()) < 12) {
			wrong.add("too near the mouth");
		}
		for (long t = 20; t <= 20 * 38; t += 20) {
			rig.at(server, t);
		}
		if (!shape.innerMouth().stream().allMatch(pos -> level.getBlockState(pos).isAir())) {
			wrong.add("closed before 40 s");
		}
		for (long t = 20 * 39; t <= 20 * 60; t += 20) {
			rig.at(server, t);
		}
		if (!shape.innerMouth().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.BRICKS)) || !rig.answered().contains("16b:cy")) {
			wrong.add("not closed by 60 s: " + rig.answered());
		}
		forget(server, S16.CAUSE, shape.box());
		helper.assertTrue(wrong.isEmpty(), "16b sooner: " + wrong);
		helper.succeed();
	}

	/**
	 * 16g: a sealed one (version 4) opens where its way in would be once a participant has stood {@code t} seconds within
	 * {@value PyramidOpening#NEAR} of it: the open one's two rows of three taken out, ledgered, once a place ever; not
	 * before, not for a spectator, not while the places are held (asleep), not on an open one.
	 */
	@GameTest(maxTicks = 400)
	public void aSealedOneOpensForWhoeverStaysAtItsFoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig rig = rig(level, 204_008, 163_008, PyramidShape.variant(0, true, false, false, 1));
		PyramidShape shape = rig.shape();
		// The open one's twelve cells; the strip's set-back already leaves the outer two of the lower row open air.
		List<BlockPos> mouth = shape.sealedMouth().stream().filter(cell -> !level.getBlockState(cell).isAir()).toList();
		List<String> wrong = new ArrayList<>();
		if (shape.sealedMouth().size() != 12 || mouth.size() != 10) {
			wrong.add("the sealed mouth " + mouth.size() + " of " + shape.sealedMouth().size());
		}
		java.util.concurrent.atomic.AtomicReference<String> held = new java.util.concurrent.atomic.AtomicReference<>("asleep");
		List<ServerPlayer> people = new ArrayList<>();
		PyramidOpening opening = new PyramidOpening(new PyramidOpening.Env(s -> held.get(), s -> List.copyOf(people),
				(l, pos) -> rig.place().box().inflatedBy(8).isInside(pos) ? Optional.of(rig.place()) : Optional.empty(), l -> place -> shape, rig::data,
				() -> com.forzacode.a1016_02.core.Services.traces().forced(), Sc16Config::new, s -> rig.day().get()));
		ServerPlayer eve = new SelfCreated2KitTests.TestPlayer(level, "eve", net.minecraft.world.level.GameType.SPECTATOR);
		ServerPlayer bo = new SelfCreated2KitTests.TestPlayer(level, "bo", net.minecraft.world.level.GameType.CREATIVE);
		BlockPos front = shape.local(0, 0, shape.halfWidth() + 6);
		eve.snapTo(Vec3.atBottomCenterOf(front), 0.0F, 0.0F);
		bo.snapTo(Vec3.atBottomCenterOf(front), 0.0F, 0.0F);
		people.add(eve);
		for (int s = 0; s < 40; s++) {
			opening.look(server);
		}
		people.add(bo);
		for (int s = 0; s < 40; s++) {
			opening.look(server);
		}
		if (mouth.stream().anyMatch(cell -> level.getBlockState(cell).isAir())) {
			wrong.add("opened while held or for a spectator");
		}
		held.set(null);
		for (int s = 0; s < 19; s++) {
			opening.look(server);
		}
		if (mouth.stream().anyMatch(cell -> level.getBlockState(cell).isAir())) {
			wrong.add("opened before 20 s");
		}
		opening.look(server);
		if (!mouth.stream().allMatch(cell -> level.getBlockState(cell).isAir()) || rig.data().spent(rig.place(), PyramidOpening.SPENT).ever() != 1) {
			wrong.add("not open after 20 s");
		}
		long ledgered = com.forzacode.a1016_02.core.TraceLedger.get(server).entries().stream()
				.filter(e -> e.cause().equals(PyramidOpening.OPEN) && shape.box().isInside(e.pos().pos())).count();
		if (ledgered != mouth.size()) {
			wrong.add(ledgered + " ledgered");
		}
		// Once ever: closed again by hand, it stays closed.
		for (BlockPos cell : mouth) {
			level.setBlock(cell, Blocks.BRICKS.defaultBlockState(), FLAGS);
		}
		for (int s = 0; s < 40; s++) {
			opening.look(server);
		}
		if (mouth.stream().anyMatch(cell -> level.getBlockState(cell).isAir())) {
			wrong.add("opened twice");
		}
		forget(server, S16.CAUSE, shape.box());
		helper.assertTrue(wrong.isEmpty(), "16g: " + wrong);
		helper.succeed();
	}

	/**
	 * D-116 as amended (R3-2): a creative player standing in a pyramid is inside (entered, visited, counted for its
	 * answers); in another kind's place they stay outside; a spectator is never inside.
	 */
	@GameTest
	public void aCreativePlayerIsInsideAPyramid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		List<String> wrong = new ArrayList<>();
		for (Remains.Kind kind : List.of(Remains.Kind.PYRAMID, Remains.Kind.FLOOR)) {
			com.forzacode.a1016_02.world.sc2.kit.Start start = new com.forzacode.a1016_02.world.sc2.kit.Start(new BlockPos(208_008 + kind.ordinal() * 64, 0,
					163_008), net.minecraft.core.Direction.NORTH, 0, 32L);
			com.forzacode.a1016_02.world.sc2.kit.Shape shape = com.forzacode.a1016_02.world.sc2.kit.DemoKind.KIND.shapes().create(start, Terrains.noise(level));
			Remains.Place place = new Remains.Place(kind, level.dimension(), shape.box(), shape.anchor(), shape.entrance(), shape.entranceFacing(), shape.apex(),
					start.seed(), start.variant());
			Sc2Data data = new Sc2Data();
			data.add(Sc2Data.Known.of(place, com.forzacode.a1016_02.world.sc2.kit.DemoKind.ID, start, shape.extra()));
			com.forzacode.a1016_02.world.sc2.kit.RemainsImpl remains = com.forzacode.a1016_02.world.sc2.kit.RemainsImpl.over(data);
			List<ServerPlayer> online = new ArrayList<>();
			List<String> events = new ArrayList<>();
			com.forzacode.a1016_02.world.sc2.kit.Visits visits = new com.forzacode.a1016_02.world.sc2.kit.Visits(remains,
					new com.forzacode.a1016_02.world.sc2.kit.Visits.Sink() {
						@Override
						public void entered(ServerLevel l, Remains.Place p, ServerPlayer player) {
							events.add("+" + player.getName().getString());
						}

						@Override
						public void left(ServerLevel l, Remains.Place p, ServerPlayer player) {
							events.add("-" + player.getName().getString());
						}

						@Override
						public void cameInThroughTheWay(MinecraftServer s, Remains.Place p, ServerPlayer player) {
						}
					}, s -> List.copyOf(online), () -> true, s -> true);
			ServerPlayer cleo = new SelfCreated2KitTests.TestPlayer(level, "cleo", net.minecraft.world.level.GameType.CREATIVE);
			ServerPlayer sam = new SelfCreated2KitTests.TestPlayer(level, "sam", net.minecraft.world.level.GameType.SPECTATOR);
			online.add(cleo);
			online.add(sam);
			BlockPos in = place.anchor();
			cleo.snapTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0, 0);
			sam.snapTo(in.getX() + 1.5, in.getY(), in.getZ() + 0.5, 0, 0);
			visits.scan(server, 100, 1000);
			boolean pyramid = kind == Remains.Kind.PYRAMID;
			if (events.equals(List.of("+cleo")) != pyramid || visits.inside(server, place).equals(List.of(cleo)) != pyramid
					|| data.visited(cleo.getUUID(), place) != pyramid || !visits.inside(server, place).stream().noneMatch(p -> p == sam)) {
				wrong.add(kind + ": " + events + ", inside " + visits.inside(server, place).size());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "creative inside: " + wrong);
		helper.succeed();
	}

	/** The figure for the tests: never a glimpse. */
	private static final Figure NO_FIGURE = new Figure() {
		@Override
		public boolean canGlimpse(ServerPlayer player, Vec3 feet) {
			return false;
		}

		@Override
		public FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw) {
			return FireResult.SKIPPED;
		}
	};

	/** A built version 4 pyramid with answers of its own (round 2's rig): its data, who is inside, the view, the clocks. */
	private record Rig(PyramidAnswers pyramid, Answers answers, Sc2Data data, Remains.Place place, PyramidShape shape, List<ServerPlayer> inside,
			List<ServerPlayer> people, java.util.concurrent.atomic.AtomicBoolean seen, java.util.concurrent.atomic.AtomicLong day,
			java.util.concurrent.atomic.AtomicLong now, List<String> answered) {
		void at(MinecraftServer server, long ticks) {
			now.set(ticks);
			pyramid.look(server, ticks);
			answers.tryAll(server);
		}
	}

	private static Rig rig(ServerLevel level, int x, int z, int variant) {
		int ground = Terrains.noise(level).ground(x, z);
		com.forzacode.a1016_02.world.sc2.kit.Start start = new com.forzacode.a1016_02.world.sc2.kit.Start(new BlockPos(x, ground + 40, z),
				net.minecraft.core.Direction.EAST, variant, 0x3E2L, 4);
		int r = PyramidShape.halfWidth(PyramidShape.size(variant));
		BlockPos o = start.origin();
		BoundingBox rock = new BoundingBox(o.getX() - r, o.getY() - PyramidShape.pitDepth(PyramidShape.size(variant)) - 6, o.getZ() - r, o.getX() + r, o.getY() - 1,
				o.getZ() + r);
		load(level, rock.inflatedBy(PyramidShape.GROUNDS_HALF + r));
		fill(level, rock, Blocks.STONE.defaultBlockState());
		Live.Result built = Live.place(level, S16.KIND, start);
		Sc2Data data = new Sc2Data();
		data.add(built.known());
		List<ServerPlayer> inside = new ArrayList<>();
		List<ServerPlayer> people = new ArrayList<>();
		java.util.concurrent.atomic.AtomicBoolean seen = new java.util.concurrent.atomic.AtomicBoolean(false);
		java.util.concurrent.atomic.AtomicLong day = new java.util.concurrent.atomic.AtomicLong(40);
		java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(0);
		List<String> answered = java.util.Collections.synchronizedList(new ArrayList<>());
		Answers.Env env = new Answers.Env(s -> data, com.forzacode.a1016_02.core.Services.traces().forced(), (l, cells) -> !seen.get(), (s, p) -> List.copyOf(inside),
				uuid -> false, uuid -> 200_000L, s -> null, day::get, now::get, (l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString()));
		Answers answers = Answers.over(env);
		PyramidShape shape = (PyramidShape) built.shape();
		PyramidAnswers pyramid = new PyramidAnswers(new PyramidAnswers.Env(answers, s -> List.copyOf(people), (l, c, rad) -> List.of(),
				() -> NO_FIGURE, Sc16Config::new, l -> place -> shape));
		return new Rig(pyramid, answers, data, built.place(), shape, inside, people, seen, day, now, answered);
	}

	/** Takes this test's ledger entries out (those with this cause, inside this box). */
	private static void forget(MinecraftServer server, String cause, BoundingBox box) {
		com.forzacode.a1016_02.core.TraceLedger ledger = com.forzacode.a1016_02.core.TraceLedger.get(server);
		for (com.forzacode.a1016_02.core.TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
			if (entry.cause().startsWith(cause) && box.isInside(entry.pos().pos())) {
				ledger.remove(entry);
			}
		}
	}

	// --- helpers ---

	/** A flat world's blocks: stone under the ground, grass (sand under water) on it, water up to {@code water}, air over. */
	private static Overlay.Base flatBase(int ground, int water) {
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState top = water == Integer.MIN_VALUE ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.SAND.defaultBlockState();
		BlockState wet = Blocks.WATER.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		return (x, y, z) -> y < ground ? stone : y == ground ? top : y <= water ? wet : air;
	}

	/** A terrain of the test's own: the ground and the water's top by column, plains (or an ocean). */
	private static Terrain flatTerrain(ServerLevel level, java.util.function.IntBinaryOperator ground, java.util.function.IntBinaryOperator surface,
			boolean ocean) {
		Holder<Biome> biome = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
				.getOrThrow(ocean ? net.minecraft.world.level.biome.Biomes.OCEAN : net.minecraft.world.level.biome.Biomes.PLAINS);
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
				return ground.applyAsInt(x, z);
			}

			@Override
			public int surface(int x, int z) {
				return Math.max(ground.applyAsInt(x, z), surface.applyAsInt(x, z));
			}

			@Override
			public Holder<Biome> biome(int x, int y, int z) {
				return biome;
			}

			@Override
			public BlockState block(int x, int y, int z) {
				int g = ground.applyAsInt(x, z);
				return y < g ? Blocks.STONE.defaultBlockState() : y == g ? Blocks.GRASS_BLOCK.defaultBlockState()
						: y <= surface.applyAsInt(x, z) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
			}
		};
	}

	/** A shape of the land kind over flat land at {@code ground} (water to {@code water}). */
	private static PyramidShape shape(ServerLevel level, BlockPos origin, int variant, long seed, int ground, int water) {
		Terrain terrain = flatTerrain(level, (x, z) -> ground, (x, z) -> water == Integer.MIN_VALUE ? ground : water, water != Integer.MIN_VALUE);
		return new PyramidShape(new com.forzacode.a1016_02.world.sc2.kit.Start(origin, net.minecraft.core.Direction.EAST, variant, seed, 4),
				terrain);
	}

	/** The query worldgen would ask in this chunk over this terrain and height. */
	private static Siting.Query query(ChunkPos chunk, Terrain terrain, int minY, int maxY) {
		Sc16Config config = new Sc16Config();
		Snapshot snapshot = new Snapshot(true, com.forzacode.a1016_02.core.Stage.TRACES, BlockPos.ZERO, 99L, 7L, Map.of(S16.ID, config, S16.SEA_ID, config),
				true);
		return new Siting.Query(chunk, terrain, snapshot, 99L, net.minecraft.util.RandomSource.create(1L), minY, maxY);
	}

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

	/** Back to the flat test world in this box (air over the grass up to its top, dirt under it). */
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

	/**
	 * When asked: the land kind's verdict in every ray slot within 800 of the spawn of 20 default worlds (the find test's
	 * seeds), and why each refused: the gates, the villages, the biome (by name), the spread, the water, the trench, the
	 * height or reach. Asserts nothing (a survey).
	 */
	@GameTest(environment = ENV, maxTicks = 200)
	public void theLandSitingWhenAsked(GameTestHelper helper) {
		String asked = System.getProperty("a1016_02.sc3pyr", "");
		if (asked.isEmpty() || asked.equals("false")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		int count = asked.equals("true") ? 20 : Integer.parseInt(asked);
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		Holder<Structure> land = structures.get(S16.ID);
		Holder<Structure> sea = structures.get(S16.SEA_ID);
		StructurePlacement placement = Sc2RealNoise.placement(level, land);
		Random seeds = new Random(0x5C3F1DL);
		Map<String, Integer> why = new TreeMap<>();
		Map<String, Integer> biomes = new TreeMap<>();
		List<String> out = new ArrayList<>();
		int worldsWithLand = 0;
		Snapshot keep = Snapshot.current();
		try {
			for (int i = 0; i < count; i++) {
				long seed = seeds.nextLong();
				Sc2RealNoise.World world = Sc2RealNoise.normal(level, seed);
				ChunkPos origin = world.generator().getOrigin(world.random());
				BlockPos spawn = new BlockPos(origin.getMinBlockX() + 8, 64, origin.getMinBlockZ() + 8);
				Snapshot snapshot = new Snapshot(true, com.forzacode.a1016_02.core.Stage.TRACES, spawn, seed, 7L, Sc2RealNoise.snapshot(seed).configs(), true);
				// The noise's first reads in a world are slow (its caches): warmed before anything is timed.
				for (int k = 0; k < 64; k++) {
					world.terrain().ground(spawn.getX() + k * 37, spawn.getZ() - k * 23);
					world.terrain().biome(spawn.getX() - k * 41, 64, spawn.getZ() + k * 29);
				}
				boolean any = false;
				for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, spawn.getX(), spawn.getZ(), 800, Integer.MAX_VALUE)) {
					if (Math.hypot(chunk.getMiddleBlockX() - spawn.getX(), chunk.getMiddleBlockZ() - spawn.getZ()) > 800) {
						continue;
					}
					String verdict = verdict(world, land, sea, chunk, snapshot, biomes);
					why.merge(verdict, 1, Integer::sum);
					any |= verdict.equals("made");
				}
				worldsWithLand += any ? 1 : 0;
			}
		} finally {
			Snapshot.install(keep);
		}
		out.add(String.format(Locale.ROOT, "land 16 within 800: %d of %d worlds", worldsWithLand, count));
		why.forEach((k, v) -> out.add("  " + k + ": " + v));
		TIMES.forEach((k, v) -> {
			List<Double> s = new ArrayList<>(v);
			java.util.Collections.sort(s);
			out.add(String.format(Locale.ROOT, "  siting ms, %s: median %.1f, 90 %% %.1f, worst %.1f (%d)", k, s.get(s.size() / 2), s.get(s.size() * 9 / 10),
					s.getLast(), s.size()));
		});
		TIMES.clear();
		out.add("biomes refused:");
		biomes.forEach((k, v) -> out.add("  " + k + ": " + v));
		out.forEach(Sc2RealNoise::log);
		try {
			java.nio.file.Files.write(java.nio.file.Path.of("build", "sc3pyr.txt"), out);
		} catch (java.io.IOException e) {
			Sc2RealNoise.log("sc3pyr: not written: " + e);
		}
		helper.succeed();
	}

	/**
	 * FIX-R3-C, when asked ({@code -Da1016_02.sc3fill=<worlds>}): each made start's fill a chunk on the real noise, as
	 * FIX-R3-17 measures it (each chunk's first fill into an overlay as worldgen clips it, in the thread's CPU time, the
	 * first start of each form left out), for the land form (16 with its depths), the sea form (16d) and the depths alone
	 * (33). D-118's class: 8 ms median, 25 ms worst. A chunk over 12 ms is filled again untimed to list who asked for
	 * heights or blocks outside it. Prints to {@code build/sc3fill.txt}; asserts nothing (a survey).
	 */
	@GameTest(environment = ENV, maxTicks = 200)
	public void theFillCostPerChunkWhenAsked(GameTestHelper helper) {
		String asked = System.getProperty("a1016_02.sc3fill", "");
		if (asked.isEmpty() || asked.equals("false")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		int count = asked.equals("true") ? 4 : Integer.parseInt(asked);
		Map<String, Holder<Structure>> structures = Sc2RealNoise.structures(level);
		long[] seeds = {SelfCreated2HeightsTests.REAL_SEED, 1L, 77L, 2024L, -4180345853331630785L, 123456789L, 42L, 9001L};
		Map<String, List<Double>> ms = new TreeMap<>();
		List<String> slow = new ArrayList<>();
		Snapshot keep = Snapshot.current();
		try {
			for (int i = 0; i < Math.min(count, seeds.length); i++) {
				Sc2RealNoise.World world = Sc2RealNoise.normal(level, seeds[i]);
				Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
				int lowest = com.forzacode.a1016_02.world.sc2.kit.Heights.lowest(world.height(), world.generator());
				for (String id : List.of(S16.ID, S16.SEA_ID)) {
					Holder<Structure> structure = structures.get(id);
					Sc2Kind kind = Sc2Kind.get(id);
					StructurePlacement placement = Sc2RealNoise.placement(level, structure);
					int made = 0;
					for (int[] c : SelfCreated2HeightsTests.CENTRES) {
						for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, 12)) {
							if (made >= 6) {
								break;
							}
							Sc2Structure.Sited sited = SelfCreated2HeightsTests.sited(world, structure, kind, chunk, snapshot);
							if (!sited.made() || !(sited.shape() instanceof PyramidShape shape)) {
								continue;
							}
							made++;
							BoundingBox box = shape.box();
							Overlay.Base base = com.forzacode.a1016_02.world.sc2.kit.NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8),
									Math.min(world.maxY(), box.maxY() + 8));
							// FIX-R3-17's method: each chunk's first fill into an overlay, in the thread's CPU time (the machine's other
							// load does not change it), the first start of each form left out (the code warm).
							Overlay timed = new Overlay(base, true);
							Overlay deep = new Overlay(base, true);
							String name = shape.sea() ? "16d" : "16";
							boolean counts = made > 1;
							for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
								for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
									BoundingBox column = new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15, world.maxY(), (cz << 4) + 15);
									Optional<BoundingBox> clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(column, box);
									if (clip.isEmpty()) {
										continue;
									}
									long c0 = CPU.getCurrentThreadCpuTime();
									shape.fill(timed.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
									double t = (CPU.getCurrentThreadCpuTime() - c0) / 1e6;
									if (counts) {
										ms.computeIfAbsent(name, k -> new ArrayList<>()).add(t);
									}
									if (counts && t > 12) {
										// Untimed: whose heights outside this chunk the fill asked for, by the shape's method asking.
										OutsideReads probe = new OutsideReads(world.terrain(), clip.get());
										shape.fill(new Overlay(base, true).writer(clip.get(), probe, level.registryAccess()), clip.get());
										slow.add(String.format(Locale.ROOT, "%s %.1f ms CPU chunk %d %d (base %d, origin %s) of %s; outside it: %s", name, t, cx, cz,
												PyramidShape.BASES[PyramidShape.size(sited.start().variant())], sited.start().origin().toShortString(), world.name(), probe.callers));
									}
									if (counts && shape.depths().present() && shape.depths().box().orElseThrow().intersects(clip.get())) {
										long d0 = CPU.getCurrentThreadCpuTime();
										shape.depths().fill(deep.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
										double d = (CPU.getCurrentThreadCpuTime() - d0) / 1e6;
										ms.computeIfAbsent("33", k -> new ArrayList<>()).add(d);
										if (d > 12) {
											slow.add(String.format(Locale.ROOT, "33 %.1f ms CPU chunk %d %d of %s", d, cx, cz, world.name()));
										}
									}
								}
							}
						}
					}
				}
			}
		} finally {
			Snapshot.install(keep);
		}
		List<String> out = new ArrayList<>();
		ms.forEach((k, v) -> {
			List<Double> sorted = new ArrayList<>(v);
			java.util.Collections.sort(sorted);
			out.add(String.format(Locale.ROOT, "%s a chunk's fill: median %.2f ms, 90 %% %.2f, 99 %% %.2f, worst %.2f (%d chunks)", k, sorted.get(sorted.size() / 2),
					sorted.get(sorted.size() * 9 / 10), sorted.get(sorted.size() * 99 / 100), sorted.getLast(), sorted.size()));
		});
		out.addAll(slow);
		out.forEach(Sc2RealNoise::log);
		try {
			java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
			java.nio.file.Files.write(java.nio.file.Path.of("build", "sc3fill.txt"), out);
		} catch (java.io.IOException e) {
			Sc2RealNoise.log("sc3fill: not written: " + e);
		}
		helper.succeed();
	}

	/**
	 * FIX-R3-C (the cost): the slot's length is read once from the land the shape is made over, and a chunk's fill no longer
	 * reads the land of the slot's columns outside it. Over real starts of the land form (versions 3, 5 and 6, the slot
	 * running out of the mouth's chunk in some), every fill hashes as the fill before the change (its land read in every
	 * chunk), and no chunk's fill asks for a height outside it on the slot's account.
	 */
	@GameTest(environment = ENV, maxTicks = 200)
	public void theSlotFillsAsBeforeWithoutReadingTheChunksInFront(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Structure> structure = Sc2RealNoise.structures(level).get(S16.ID);
		Sc2Kind kind = Sc2Kind.get(S16.ID);
		StructurePlacement placement = Sc2RealNoise.placement(level, structure);
		List<String> wrong = new ArrayList<>();
		int starts = 0;
		int slots = 0;
		int crossing = 0;
		Snapshot keep = Snapshot.current();
		try {
			for (long seed : new long[] {SelfCreated2HeightsTests.REAL_SEED, 77L, 2024L}) {
				if (slots >= 8) {
					break;
				}
				Sc2RealNoise.World world = Sc2RealNoise.normal(level, seed);
				Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
				int lowest = com.forzacode.a1016_02.world.sc2.kit.Heights.lowest(world.height(), world.generator());
				for (int[] c : SelfCreated2HeightsTests.CENTRES) {
					for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, 4)) {
						Sc2Structure.Sited sited = SelfCreated2HeightsTests.sited(world, structure, kind, chunk, snapshot);
						if (!sited.made() || slots >= 8) {
							continue;
						}
						for (int version : new int[] {3, 5, PyramidShape.VERSION}) {
							PyramidShape shape = (PyramidShape) kind.shape(sited.start().withVersion(version), world.terrain());
							if (!shape.hasWayIn()) {
								continue;
							}
							starts++;
							if (shape.slotLength() == 0) {
								// No slot: the fill never comes to it.
								continue;
							}
							slots++;
							BlockPos mouth = shape.local(0, -1, shape.halfWidth());
							BlockPos out = shape.local(0, -1, shape.halfWidth() + shape.slotLength());
							crossing += (mouth.getX() >> 4) != (out.getX() >> 4) || (mouth.getZ() >> 4) != (out.getZ() >> 4) ? 1 : 0;
							long now = SelfCreated3KitTests.fillHash(level, world, shape);
							long before = PyramidShape.slotAsBefore(() -> SelfCreated3KitTests.fillHash(level, world, shape));
							if (now != before) {
								wrong.add("v" + version + " at " + sited.start().origin().toShortString() + " fills to " + now + ", before " + before);
							}
							if (version != PyramidShape.VERSION) {
								continue;
							}
							// The chunks the slot runs through (with the mouth's): none asks for a height outside it for the slot.
							BoundingBox box = shape.box();
							Overlay.Base base = com.forzacode.a1016_02.world.sc2.kit.NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, box.minY() - 8),
									Math.min(world.maxY(), box.maxY() + 8));
							BoundingBox span = BoundingBox.fromCorners(shape.local(-1, -1, shape.halfWidth() - 1), shape.local(2, -1, shape.halfWidth() + shape.slotLength() + 1));
							for (int cx = span.minX() >> 4; cx <= span.maxX() >> 4; cx++) {
								for (int cz = span.minZ() >> 4; cz <= span.maxZ() >> 4; cz++) {
									BoundingBox column = new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15, world.maxY(), (cz << 4) + 15);
									Optional<BoundingBox> clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(column, box);
									if (clip.isEmpty()) {
										continue;
									}
									OutsideReads probe = new OutsideReads(world.terrain(), clip.get());
									shape.fill(new Overlay(base, true).writer(clip.get(), probe, level.registryAccess()), clip.get());
									probe.callers.keySet().stream().filter(k -> k.contains("PyramidShape.slot")).findFirst()
											.ifPresent(k -> wrong.add("v" + version + " chunk " + clip.get().minX() + " " + clip.get().minZ() + " reads outside it: " + k));
								}
							}
						}
					}
				}
			}
		} finally {
			Snapshot.install(keep);
		}
		helper.assertTrue(wrong.isEmpty() && slots >= 3 && crossing >= 1, "the slot (" + starts + " starts, " + slots + " with a slot, " + crossing
				+ " out of the mouth's chunk): " + wrong.subList(0, Math.min(6, wrong.size())));
		helper.succeed();
	}

	private static final java.lang.management.ThreadMXBean CPU = java.lang.management.ManagementFactory.getThreadMXBean();

	/** A terrain that tells who asks for heights or blocks outside one chunk's clip (the survey's slow chunks). */
	private static final class OutsideReads implements Terrain {
		private final Terrain inner;
		private final BoundingBox clip;
		final Map<String, Integer> callers = new TreeMap<>();

		OutsideReads(Terrain inner, BoundingBox clip) {
			this.inner = inner;
			this.clip = clip;
		}

		private void ask(String what, int x, int z) {
			if (x < clip.minX() || x > clip.maxX() || z < clip.minZ() || z > clip.maxZ()) {
				String by = StackWalker.getInstance().walk(frames -> frames.map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "."
						+ f.getMethodName()).filter(n -> !n.startsWith("OutsideReads") && !n.startsWith("Writer") && !n.startsWith("Terrain")).findFirst().orElse("?"));
				callers.merge(what + " " + by, 1, Integer::sum);
			}
		}

		@Override
		public int seaLevel() {
			return inner.seaLevel();
		}

		@Override
		public int minY() {
			return inner.minY();
		}

		@Override
		public int ground(int x, int z) {
			ask("ground", x, z);
			return inner.ground(x, z);
		}

		@Override
		public int surface(int x, int z) {
			ask("surface", x, z);
			return inner.surface(x, z);
		}

		@Override
		public Holder<Biome> biome(int x, int y, int z) {
			return inner.biome(x, y, z);
		}

		@Override
		public BlockState block(int x, int y, int z) {
			ask("block", x, z);
			return inner.block(x, y, z);
		}
	}

	/** Why the land kind refused this slot (or {@code made}); an ocean slot is {@code sea} (16d's). */
	private static String verdict(Sc2RealNoise.World world, Holder<Structure> land, Holder<Structure> sea, ChunkPos chunk, Snapshot snapshot,
			Map<String, Integer> biomes) {
		Sc2Kind kind = Sc2Kind.get(S16.ID);
		if (Snapshot.refusal(snapshot, kind, chunk) != null) {
			return "gates";
		}
		if (land.value() instanceof Sc2Structure sc2 && Decided.excluded(sc2, world.structures(), List.of(), chunk) != null) {
			return "village";
		}
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		Terrain terrain = world.terrain();
		Holder<Biome> biome = terrain.biome(x, terrain.ground(x, z), z);
		Sc2Structure.Sited sited = SelfCreated2HeightsTests.sited(world, land, kind, chunk, snapshot);
		// The siting alone, timed, its reads counted (a fresh terrain read each: the kit's own path above warmed nothing it keeps).
		PyramidSiting.Samples samples = new PyramidSiting.Samples(terrain);
		long t = System.nanoTime();
		PyramidSiting.LAND.find(SelfCreated2HeightsTests.query(world, chunk, snapshot, -64, 320), snapshot.config(S16.ID, Sc16Config.class), samples);
		String outcome = sited.made() ? "made" : biome.is(BiomeTags.IS_OCEAN) ? "refused at sea" : "refused on land";
		TIMES.computeIfAbsent(outcome, k -> new ArrayList<>()).add((System.nanoTime() - t) / 1e6);
		TIMES.computeIfAbsent(outcome + " (reads)", k -> new ArrayList<>()).add((double) samples.reads());
		if (sited.made()) {
			biomes.merge("~made base " + PyramidShape.BASES[PyramidShape.size(sited.start().variant())], 1, Integer::sum);
			return "made";
		}
		if (biome.is(BiomeTags.IS_OCEAN)) {
			return "sea";
		}
		if (sited.why().equals("biome")) {
			biomes.merge(biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?"), 1, Integer::sum);
			return "biome";
		}
		if (!sited.why().equals("siting")) {
			return "kit " + sited.why();
		}
		// The siting's own: the smallest spread of the sizes, the water, the trench.
		int best = Integer.MAX_VALUE;
		for (int size = 0; size < PyramidShape.BASES.length; size++) {
			int half = PyramidShape.halfWidth(size);
			int lo = Integer.MAX_VALUE;
			int hi = Integer.MIN_VALUE;
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					int g = terrain.ground(x + a * half, z + b * half);
					lo = Math.min(lo, g);
					hi = Math.max(hi, g);
				}
			}
			best = Math.min(best, hi - lo);
		}
		if (best > 16) {
			return best > 32 ? "spread over 32" : best > 24 ? "spread 25 to 32" : "spread 17 to 24";
		}
		return "siting other (water, trench, height)";
	}
}
