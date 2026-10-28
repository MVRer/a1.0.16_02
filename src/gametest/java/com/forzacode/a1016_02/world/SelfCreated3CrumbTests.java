package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc.WorldSc;
import com.forzacode.a1016_02.world.sc2.kit.Plans;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.s32.CrumbData;
import com.forzacode.a1016_02.world.sc2.s32.CrumbData.Crumb;
import com.forzacode.a1016_02.world.sc2.s19.LinePlan;
import com.forzacode.a1016_02.world.sc2.s19.Sc19Config;
import com.forzacode.a1016_02.world.sc2.s32.CrumbFeature;
import com.forzacode.a1016_02.world.sc2.s32.CrumbFooting;
import com.forzacode.a1016_02.world.sc2.s32.CrumbIndex;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Box;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.From;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Line;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Spot;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Tier;
import com.forzacode.a1016_02.world.sc2.s32.CrumbRules;
import com.forzacode.a1016_02.world.sc2.s32.CrumbShapes;
import com.forzacode.a1016_02.world.sc2.s32.CrumbShapes.Cell;
import com.forzacode.a1016_02.world.sc2.s32.CrumbShapes.Foot;
import com.forzacode.a1016_02.world.sc2.s32.S32;
import com.forzacode.a1016_02.world.sc2.s32.Sc32Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * R3-C's game tests (docs/SELF_CREATED_2_R3.md, "32"): the lines (A toward the spawn, B and C turned, 19's two; their
 * stops at another place's box and the spawn's clearance; dropped when short), the tiers' spacing and straightness, each
 * kind's crumbs by tier in the original blocks (the pillar form in water), footing (no face over 3 within 2, no lava
 * within 8, no deep water but the pillar, the canopy's hole), never in a structure's box, near the spawn or outside its
 * own chunk, a line built chunk by chunk equal to the same line built live, a line fixed once, 32b (a newcomer near, the
 * seers away: taken once a line, once a day; the seer near: not; given back by the switch), 32c (moved toward the base,
 * every block kept, never by another base or a placed block, the spur's second step, its caps), asleep neither. Each
 * test works in its own patch far out (the test world is kept between runs).
 */
public class SelfCreated3CrumbTests {
	private static final String ENV = "a1016_02:world/s32";
	private static final int BASE_X = 16 * ThreadLocalRandom.current().nextInt(150_000, 190_000);
	private static final int BASE_Z = 16 * ThreadLocalRandom.current().nextInt(150_000, 190_000);
	/** The flat test world's grass. */
	private static final int GRASS = -61;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** How high a patch is cleared at least (and to the land's own top where that is higher). */
	private static final int HIGH = GRASS + 30;

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

	/** Back to the flat test world in this box (air over the grass), only what differs set. */
	private static void flat(ServerLevel level, BoundingBox box) {
		load(level, box);
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				int top = Math.max(box.maxY(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z));
				for (int y = GRASS - 6; y <= top; y++) {
					BlockState want = y > GRASS ? air : y == GRASS ? grass : dirt;
					if (level.getBlockState(at.set(x, y, z)) != want) {
						level.setBlock(at, want, FLAGS);
					}
				}
			}
		}
	}

	/** Flat, loaded land from {@code from} toward {@code toward}, {@code length} out and 10 either side. */
	private static void flatToward(ServerLevel level, BlockPos from, BlockPos toward, int length) {
		double len = Math.hypot(toward.getX() - from.getX(), toward.getZ() - from.getZ());
		double ux = (toward.getX() - from.getX()) / len;
		double uz = (toward.getZ() - from.getZ()) / len;
		for (int k = 0; k <= length; k += 12) {
			int x = (int) Math.floor(from.getX() + 0.5 + ux * k);
			int z = (int) Math.floor(from.getZ() + 0.5 + uz * k);
			flat(level, new BoundingBox(x - 10, GRASS - 6, z - 10, x + 10, HIGH, z + 10));
		}
	}

	private static void fill(ServerLevel level, BoundingBox box, BlockState state) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			level.setBlock(p, state, FLAGS);
		}
	}

	private static Snapshot snapshot(BlockPos spawn) {
		return new Snapshot(true, Stage.TRACES, spawn, 0x32A1L, 0x32B2L, Map.of(), true);
	}

	/** A line of this kind running east from (x, z), {@code length} long, its place's box a stub west of its start. */
	private static Line line(String key, String kind, int x, int z, double angle, int length) {
		Sc32Config c = new Sc32Config();
		return new Line(key, kind, x, z, angle, length, CrumbLines.VERSION, key.hashCode() * 31L, new int[] {x - 20, z - 8, x - 6, z + 8},
				new int[] {c.nearTo, c.midTo, c.nearSpacing, c.midSpacing, c.farSpacing});
	}

	private static From from(String kind, int x, int z) {
		return new From(kind, kind + "@test", x, z, new BoundingBox(x - 30, 0, z - 30, x + 30, 60, z + 30), 0x5EEDL, null);
	}

	private static double degrees(double a) {
		double d = Math.toDegrees(a) % 360;
		return d < 0 ? d + 360 : d;
	}

	private static double turn(double a, double b) {
		double d = Math.abs(degrees(a) - degrees(b)) % 360;
		return d > 180 ? 360 - d : d;
	}

	private static Map<BlockPos, BlockState> corridor(ServerLevel level, int x0, int x1, int z, int dz) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (int x = x0; x <= x1; x++) {
			for (int a = -8; a <= 8; a++) {
				for (int y = GRASS - 4; y <= GRASS + 10; y++) {
					BlockState s = level.getBlockState(new BlockPos(x, y, z + a));
					if (!s.isAir() && !s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.DIRT)) {
						out.put(new BlockPos(x, y, a + dz), s);
					}
				}
			}
		}
		return out;
	}

	// --- the lines ---

	/**
	 * Line A runs toward the world spawn and B and C turn 100 to 140 degrees from it, each at most {@code c} long; A
	 * stops short of the spawn's clearance and is dropped under 128; any line stops short of another place's box;
	 * {@code a} caps the count; 19's two run square to the wall, 512 long.
	 */
	@GameTest
	public void linesRunTowardTheSpawnAndTurnedAndStopShortOfPlacesAndTheSpawn(GameTestHelper helper) {
		Sc32Config c = new Sc32Config();
		List<Line> lines = CrumbLines.plan(from("s16", 10_000, 0), List.of(), 0, 0, c, 1L, 2L, null);
		helper.assertTrue(lines.size() == 3, "lines: " + lines.size());
		helper.assertTrue(turn(lines.get(0).angle(), Math.PI) < 0.5, "A does not run toward the spawn: " + degrees(lines.get(0).angle()));
		for (Line l : lines.subList(1, 3)) {
			double t = turn(l.angle(), lines.get(0).angle());
			helper.assertTrue(t >= 99.5 && t <= 140.5, "B or C turned " + t);
		}
		helper.assertTrue(turn(lines.get(1).angle(), lines.get(2).angle()) > 70, "B and C on the same side");
		for (Line l : lines) {
			helper.assertTrue(l.length() == c.lineMax, "a free line " + l.length() + " long");
			helper.assertTrue(l.key().endsWith("/" + (char) ('A' + lines.indexOf(l))), "key " + l.key());
		}
		// Near the spawn: A stops short of its clearance.
		List<Line> near = CrumbLines.plan(from("s17", 600, 0), List.of(), 0, 0, c, 1L, 2L, null);
		Line a = near.getFirst();
		helper.assertTrue(a.key().endsWith("/A") && a.length() <= 600 - c.spawnClear && a.length() >= 600 - c.spawnClear - 40, "A near the spawn: " + a.length());
		for (Line l : near) {
			for (Spot s : l.spots()) {
				helper.assertTrue(Math.hypot(s.x(), s.z()) >= c.spawnClear, "a crumb inside the spawn's clearance at " + s.x() + " " + s.z());
			}
		}
		// Too near: A under 128 is dropped.
		List<Line> tooNear = CrumbLines.plan(from("s17", 340, 0), List.of(), 0, 0, c, 1L, 2L, null);
		helper.assertTrue(tooNear.stream().noneMatch(l -> l.key().endsWith("/A")), "a short A kept");
		// Another place's box across A at 300: A stops short of it.
		List<Line> blocked = CrumbLines.plan(from("s18", 10_000, 0), List.of(new Box(9_690, -40, 9_710, 40)), 0, 0, c, 1L, 2L, null);
		Line ba = blocked.getFirst();
		helper.assertTrue(ba.key().endsWith("/A") && ba.length() <= 290 - CrumbLines.STOP_MARGIN && ba.length() >= 200, "A past a box: " + ba.length());
		for (Spot s : ba.spots()) {
			helper.assertTrue(s.x() > 9_710 + CrumbLines.STOP_MARGIN - 8, "a crumb at the other place: " + s.x());
		}
		// The count.
		c.linesPerPlace = 1;
		helper.assertTrue(CrumbLines.plan(from("s20", 10_000, 0), List.of(), 0, 0, c, 1L, 2L, null).size() == 1, "a = 1 not held");
		c.linesPerPlace = 3;
		// 19's: square to the wall, both ways, 512.
		From wall = new From("s19", "s19@EAST", 800, 0, new BoundingBox(600, -60, 0, 1400, 200, 0), 9L, new double[] {Math.PI / 2, -Math.PI / 2});
		List<Line> two = CrumbLines.plan(wall, List.of(), 0, 0, c, 1L, 2L, null);
		helper.assertTrue(two.size() == 2 && two.stream().allMatch(l -> l.length() == CrumbLines.WALL_LINE), "19's lines: " + two.size());
		helper.assertTrue(turn(two.get(0).angle(), two.get(1).angle()) > 179, "19's lines not opposite");
		helper.succeed();
	}

	/**
	 * The near tier every 16 from 64 to 192 and the mid every 24 to 384, dead straight; the far from 384 every 32 give or
	 * take 4 (FIX-R3-C: the defaults closer for the sight on real land; version 1 lines took 8), at most 6 aside; a light on
	 * every other far one, glowstone on every second mid (version 1: every third); none in its own place's box.
	 */
	@GameTest
	public void theTiersStandAtTheirSpacingNearAndMidDeadStraight(GameTestHelper helper) {
		Line l = line("t/tiers", "s17", 0, 0, Math.toRadians(30), 640);
		List<Spot> spots = l.spots();
		List<Integer> near = new ArrayList<>();
		List<Integer> mid = new ArrayList<>();
		List<Spot> far = new ArrayList<>();
		for (Spot s : spots) {
			switch (s.tier()) {
				case NEAR -> near.add(s.d());
				case MID -> mid.add(s.d());
				case FAR -> far.add(s);
			}
			double along = s.x() * l.ux() + s.z() * l.uz();
			double across = -s.x() * l.uz() + s.z() * l.ux();
			helper.assertTrue(Math.abs(along - s.d()) <= 1.5, "off its distance: " + s);
			helper.assertTrue(s.tier() == Tier.FAR ? Math.abs(across) <= CrumbLines.FAR_ACROSS + 1 : Math.abs(across) <= 1, "aside: " + s + " " + across);
		}
		helper.assertTrue(near.equals(List.of(64, 80, 96, 112, 128, 144, 160, 176)), "near " + near);
		helper.assertTrue(mid.equals(List.of(192, 216, 240, 264, 288, 312, 336, 360)), "mid " + mid);
		helper.assertTrue(far.size() >= 7 && far.getFirst().d() >= 384 && far.getLast().d() <= 640, "far " + far.size());
		for (int i = 1; i < far.size(); i++) {
			int gap = far.get(i).d() - far.get(i - 1).d();
			helper.assertTrue(gap >= 32 - 2 * CrumbLines.FAR_WOBBLE_V2 && gap <= 32 + 2 * CrumbLines.FAR_WOBBLE_V2, "far gap " + gap);
		}
		helper.assertTrue(far.get(0).lit() && !far.get(1).lit() && far.get(2).lit(), "the far lights are not every other one");
		// Version 2 (FIX-R3-C, R3A-7): every second mid crumb crowned; a line fixed at version 1 keeps every third.
		long crowned = spots.stream().filter(Spot::crowned).count();
		helper.assertTrue(crowned == 4 && spots.stream().filter(s -> s.tier() == Tier.MID).findFirst().get().crowned(), "crowned " + crowned);
		Line v1 = new Line(l.key(), l.kind(), l.x(), l.z(), l.angle(), l.length(), 1, l.seed(), l.own(), l.tiers());
		long crownedV1 = v1.spots().stream().filter(Spot::crowned).count();
		helper.assertTrue(crownedV1 == 3, "version 1 crowned " + crownedV1);
		for (int i = 1; i < spots.size(); i++) {
			helper.assertTrue(spots.get(i).index() > spots.get(i - 1).index() && spots.get(i).d() > spots.get(i - 1).d(), "not in order");
		}
		// Its own box: a line back over its place leaves those out.
		Line back = new Line("t/own", "s16", 0, 0, 0.0, 640, 1, 5L, new int[] {40, -30, 200, 30}, l.tiers());
		helper.assertTrue(back.spots().stream().noneMatch(s -> s.x() >= 36 && s.x() <= 204), "a crumb in its own box");
		helper.assertTrue(back.spots().getFirst().d() > 200, "the first crumb past its box is at " + back.spots().getFirst().d());
		helper.succeed();
	}

	// --- the things ---

	private static Spot spotOf(String kind, Tier tier, int k) {
		Line l = line("t/" + kind, kind, 0, 0, 0.0, 640);
		for (Spot s : l.spots()) {
			if (s.tier() == tier && s.k() == k) {
				return s;
			}
		}
		throw new IllegalStateException("no " + tier + " " + k);
	}

	private static Map<Block, Integer> own(List<Cell> cells) {
		Map<Block, Integer> out = new HashMap<>();
		for (Cell c : cells) {
			if (c.own()) {
				out.merge(c.state().getBlock(), 1, Integer::sum);
			}
		}
		return out;
	}

	/**
	 * Each place's crumbs by tier, in the first versions' blocks: 16 a brick, a brick step two high, a brick pyramid with
	 * glowstone at its top; 17 a bookshelf, a cobblestone pillar under a bookshelf, his 2 x 2 lit in a 4 x 4 of cobblestone;
	 * 18 a bare trunk, a pillar of the land's stone five high, a cube of it with glowstone in its top; 19 obsidian one, two,
	 * three high (glowstone on the last); 20 the first night's dirt pillar and torch, a raised grass floor with a cobblestone
	 * block and torch, a plank hut with one glass block, its doorway toward the place, a torch inside. Over deep water the
	 * mid and near stand on a cobblestone pillar from the floor; 16d's near lights the sea round it. No mossy cobblestone,
	 * no wall torch, anywhere.
	 */
	@GameTest
	public void eachKindsCrumbsAreItsOwnOldBlocksByTier(GameTestHelper helper) {
		int[] grounds9 = new int[9];
		java.util.Arrays.fill(grounds9, 10);
		int[] grounds25 = new int[25];
		java.util.Arrays.fill(grounds25, 10);
		BlockState stone = Blocks.ANDESITE.defaultBlockState();
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
		Foot dry = new Foot(0, 10, 0, 10, stone, log, grounds25);
		Map<String, Map<Tier, Map<Block, Integer>>> want = Map.of(
				"s16", Map.of(Tier.FAR, Map.of(Blocks.BRICKS, 2, Blocks.REDSTONE_TORCH, 1), Tier.MID, Map.of(Blocks.BRICKS, 9, Blocks.GLOWSTONE, 1), Tier.NEAR,
						Map.of(Blocks.BRICKS, 34, Blocks.GLOWSTONE, 1)),
				"s17", Map.of(Tier.FAR, Map.of(Blocks.BOOKSHELF, 2, Blocks.REDSTONE_TORCH, 1), Tier.MID, Map.of(Blocks.COBBLESTONE, 3, Blocks.BOOKSHELF, 1,
						Blocks.GLOWSTONE, 1), Tier.NEAR, Map.of(Blocks.COBBLESTONE, 12, Blocks.AIR, 8, Blocks.GLOWSTONE, 4)),
				"s18", Map.of(Tier.MID, Map.of(Blocks.ANDESITE, 5, Blocks.GLOWSTONE, 1), Tier.NEAR, Map.of(Blocks.ANDESITE, 26, Blocks.GLOWSTONE, 1)),
				"s19", Map.of(Tier.FAR, Map.of(Blocks.OBSIDIAN, 2), Tier.MID, Map.of(Blocks.OBSIDIAN, 2, Blocks.GLOWSTONE, 1), Tier.NEAR,
						Map.of(Blocks.OBSIDIAN, 3, Blocks.GLOWSTONE, 1)),
				"s20", Map.of(Tier.FAR, Map.of(Blocks.DIRT, 3, Blocks.TORCH, 1), Tier.MID, Map.of(Blocks.GRASS_BLOCK, 8, Blocks.COBBLESTONE, 1, Blocks.TORCH, 1)));
		for (Map.Entry<String, Map<Tier, Map<Block, Integer>>> e : want.entrySet()) {
			for (Map.Entry<Tier, Map<Block, Integer>> t : e.getValue().entrySet()) {
				List<Cell> cells = CrumbShapes.cells(spotOf(e.getKey(), t.getKey(), 0), dry);
				helper.assertTrue(own(cells).equals(t.getValue()), e.getKey() + " " + t.getKey() + ": " + own(cells));
			}
		}
		// 18's bare trunk: 3 to 5 of the biome's logs, its light on the ground beside it toward the place.
		Map<Block, Integer> trunk = own(CrumbShapes.cells(spotOf("s18", Tier.FAR, 0), dry));
		helper.assertTrue(trunk.getOrDefault(Blocks.SPRUCE_LOG, 0) >= 3 && trunk.getOrDefault(Blocks.SPRUCE_LOG, 0) <= 5 && trunk.get(Blocks.REDSTONE_TORCH) == 1,
				"18 far " + trunk);
		helper.assertTrue(CrumbShapes.cells(spotOf("s18", Tier.FAR, 0), dry).stream().anyMatch(c -> c.state().is(Blocks.REDSTONE_TORCH)
				&& c.pos().equals(new BlockPos(-1, 11, 0))), "18's light not beside it toward the place");
		// A far crumb without its light (every other one); version 2's one-block far things stand two (FIX-R3-C), version 1's one.
		helper.assertTrue(own(CrumbShapes.cells(spotOf("s16", Tier.FAR, 1), dry)).equals(Map.of(Blocks.BRICKS, 2)), "the second far one is lit");
		Spot old = spotOf("s17", Tier.FAR, 1);
		Line oldLine = new Line(old.line().key(), old.line().kind(), old.line().x(), old.line().z(), old.line().angle(), old.line().length(), 1, old.line().seed(),
				old.line().own(), old.line().tiers());
		Spot v1 = oldLine.spots().stream().filter(sp -> sp.tier() == Tier.FAR && sp.k() == 1).findFirst().orElseThrow();
		helper.assertTrue(own(CrumbShapes.cells(v1, dry)).equals(Map.of(Blocks.BOOKSHELF, 1)), "version 1's far bookshelf: " + own(CrumbShapes.cells(v1, dry)));
		// 20's hut: walls and roof of planks, one glass, its doorway toward the place (west), a torch inside.
		List<Cell> hut = CrumbShapes.cells(spotOf("s20", Tier.NEAR, 0), dry);
		Map<Block, Integer> h = own(hut);
		helper.assertTrue(h.get(Blocks.OAK_PLANKS) == 7 + 6 + 9 && h.get(Blocks.GLASS) == 1 && h.get(Blocks.TORCH) == 1, "20's hut " + h);
		helper.assertTrue(hut.stream().anyMatch(c -> c.own() && c.state().isAir() && c.pos().equals(new BlockPos(-1, 11, 0))), "the doorway not toward the place");
		helper.assertTrue(hut.stream().anyMatch(c -> c.own() && c.state().is(Blocks.GLASS) && c.pos().equals(new BlockPos(1, 12, 0))), "the glass not opposite");
		// Over deep water: the pillar from the floor.
		Foot wet = new Foot(0, 0, 0, 9, stone, log, grounds9);
		for (String kind : List.of("s16", "s17", "s18", "s19", "s20")) {
			for (Tier tier : List.of(Tier.MID, Tier.NEAR)) {
				List<Cell> cells = CrumbShapes.cells(spotOf(kind, tier, 1), wet);
				long cobble = cells.stream().filter(c -> c.own() && c.state().is(Blocks.COBBLESTONE) && c.pos().getX() == 0 && c.pos().getZ() == 0).count();
				helper.assertTrue(cobble >= 8 && CrumbShapes.pillar(kind, tier, true), kind + " " + tier + " in water: " + cobble + " cobblestone");
			}
			helper.assertTrue(!CrumbShapes.allowsWater(kind, Tier.FAR), kind + "'s far one allowed in water");
		}
		helper.assertTrue(CrumbShapes.allowsWater("s16d", Tier.FAR), "16d's far one not allowed in water");
		List<Cell> sea = CrumbShapes.cells(spotOf("s16d", Tier.NEAR, 0), wet);
		helper.assertTrue(sea.stream().filter(c -> c.state().is(Blocks.LIGHT) && c.state().getValue(LightBlock.WATERLOGGED)).count() == 8
				&& sea.stream().anyMatch(c -> c.state().is(Blocks.GLOWSTONE) && c.pos().getY() == 10), "16d's lit sea");
		// Never the shrine or t0rch's wall torch.
		for (String kind : CrumbGalleryKinds.ALL) {
			for (Tier tier : Tier.values()) {
				for (int k = 0; k < 3; k++) {
					for (Foot f : List.of(dry, wet)) {
						for (Cell c : CrumbShapes.cells(spotOf(kind, tier, Math.min(k, tier == Tier.NEAR ? 7 : 2)), f)) {
							helper.assertTrue(!c.state().is(Blocks.MOSSY_COBBLESTONE) && !(c.state().getBlock() instanceof WallTorchBlock), kind + " uses " + c.state());
						}
					}
				}
			}
		}
		helper.succeed();
	}

	/** The kinds (the gallery's list, for the tests). */
	static final class CrumbGalleryKinds {
		static final List<String> ALL = List.of("s16", "s16d", "s17", "s18", "s19", "s20");
	}

	// --- footing, live ---

	/**
	 * Built live over the land: a crumb by a face over 3 steps along its line away from it or is skipped; none within 8
	 * of lava; a far one over deep water is skipped and a near one stands on a cobblestone pillar; one under trees has the
	 * leaves over it taken off (a 3 x 3 to the sky); every crumb in its own point's chunk; none in a box kept out.
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void aCrumbFindsFootingOrStepsAsideNeverByAFaceLavaOrDeepWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x0 = slotX(1);
		int z0 = BASE_Z;
		flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 - 14, x0 + 680, HIGH, z0 + 14));
		Line l = line("t/footing/" + x0, "s17", x0, z0, 0.0, 640);
		Map<Integer, Spot> byD = new HashMap<>();
		for (Spot s : l.spots()) {
			byD.put(s.d(), s);
		}
		// A wall 6 high across the line at the near crumb at 96 (its west face 2 east of it).
		fill(level, new BoundingBox(x0 + 98, GRASS + 1, z0 - 10, x0 + 104, GRASS + 6, z0 + 10), Blocks.STONE.defaultBlockState());
		// Lava 5 off the mid crumb at 264.
		fill(level, new BoundingBox(x0 + 264, GRASS, z0 + 5, x0 + 265, GRASS, z0 + 6), Blocks.LAVA.defaultBlockState());
		// Deep water under the near crumb at 144 and the first far one.
		Spot far0 = l.spots().stream().filter(s -> s.tier() == Tier.FAR).findFirst().orElseThrow();
		fill(level, new BoundingBox(x0 + 138, GRASS - 3, z0 - 8, x0 + 150, GRASS, z0 + 8), Blocks.WATER.defaultBlockState());
		fill(level, new BoundingBox(far0.x() - 10, GRASS - 3, far0.z() - 10, far0.x() + 10, GRASS, far0.z() + 10), Blocks.WATER.defaultBlockState());
		// Trees over the mid crumb at 312: leaves from 4 to 8 over the ground, a 9 x 9.
		fill(level, new BoundingBox(x0 + 308, GRASS + 5, z0 - 4, x0 + 316, GRASS + 9, z0 + 4), Blocks.OAK_LEAVES.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
		// A box kept out round the mid crumb at 192.
		List<BoundingBox> keepOut = List.of(new BoundingBox(x0 + 186, -64, z0 - 6, x0 + 198, 100, z0 + 6));
		List<Crumb> built = CrumbFeature.live(level, l, snapshot(new BlockPos(x0 - 100_000, 0, z0)), new Sc32Config(), keepOut);
		CrumbData.dropPending();
		Map<Integer, Crumb> at = new HashMap<>();
		for (Crumb c : built) {
			at.put(c.index(), c);
			helper.assertTrue(c.at().getX() >> 4 == (byD.get(spotD(l, c.index())).x() >> 4), "a crumb outside its point's chunk: " + c);
		}
		Crumb byWall = at.get(byD.get(96).index());
		helper.assertTrue(byWall == null || byWall.at().getX() <= x0 + 98 - 3 - 1, "a crumb by the face: " + byWall);
		helper.assertTrue(!at.containsKey(byD.get(264).index()), "a crumb by lava");
		helper.assertTrue(!at.containsKey(far0.index()), "a far crumb in deep water");
		Crumb pillar = at.get(byD.get(144).index());
		helper.assertTrue(pillar != null, "no crumb over the water at 144");
		int cobble = 0;
		for (int y = GRASS - 3; y <= GRASS; y++) {
			cobble += level.getBlockState(new BlockPos(pillar.at().getX(), y, pillar.at().getZ())).is(Blocks.COBBLESTONE) ? 1 : 0;
		}
		helper.assertTrue(cobble >= 3, "no pillar in the water: " + cobble);
		helper.assertTrue(!at.containsKey(byD.get(192).index()), "a crumb in a box kept out");
		Crumb under = at.get(byD.get(312).index());
		helper.assertTrue(under != null, "no crumb under the trees");
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = GRASS + 5; y <= GRASS + 9; y++) {
					BlockPos p = new BlockPos(under.at().getX() + dx, y, under.at().getZ() + dz);
					helper.assertTrue(!level.getBlockState(p).is(BlockTags.LEAVES), "leaves over the crumb at " + p);
				}
			}
		}
		helper.assertTrue(level.getBlockState(new BlockPos(under.at().getX() + 3, GRASS + 7, under.at().getZ())).is(BlockTags.LEAVES),
				"the leaves round the hole went too");
		// A face rule held round every crumb built.
		for (Crumb c : built) {
			for (int dx = -2; dx <= 2; dx++) {
				helper.assertTrue(!level.getBlockState(new BlockPos(c.at().getX() + dx, GRASS + 4, c.at().getZ())).is(Blocks.STONE), "a crumb by the wall " + c);
			}
		}
		helper.succeed();
	}

	/**
	 * FIX-R3-C (R3A-7), line version 2: among the shifts that hold, a crumb takes the highest ground (a mid crumb steps
	 * onto a mound 2 high 2 to 5 along from its point), and a far one looks up to {@value CrumbFooting#FAR_SHIFT} along (it
	 * stands on a mound 6 to 8 along); the same line fixed at version 1 keeps the first that holds (both on the flat).
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void aCrumbTakesTheHighestFootingItMayFromVersion2(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x0 = slotX(7);
		List<String> wrong = new ArrayList<>();
		int[] rise = new int[2];
		for (int v = 1; v <= 2; v++) {
			int z0 = BASE_Z + 64 * v;
			flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 - 14, x0 + 680, HIGH, z0 + 14));
			Line base = line("t/rises/" + x0, "s17", x0, z0, 0.0, 640);
			Line l = new Line(base.key() + "/" + v, base.kind(), base.x(), base.z(), base.angle(), base.length(), v, base.seed(), base.own(), base.tiers());
			Spot mid = l.spots().stream().filter(s -> s.tier() == Tier.MID && Math.floorMod(s.x(), 16) <= 9).findFirst().orElseThrow();
			Spot far = l.spots().stream().filter(s -> s.tier() == Tier.FAR && (Math.floorMod(s.x(), 16) <= 6 || Math.floorMod(s.x(), 16) >= 9)).findFirst()
					.orElseThrow();
			fill(level, new BoundingBox(mid.x() + 2, GRASS + 1, mid.z() - 3, mid.x() + 5, GRASS + 2, mid.z() + 3), Blocks.DIRT.defaultBlockState());
			int dir = Math.floorMod(far.x(), 16) <= 6 ? 1 : -1;
			int a = far.x() + 6 * dir;
			int b = far.x() + 8 * dir;
			fill(level, new BoundingBox(Math.min(a, b), GRASS + 1, far.z() - 2, Math.max(a, b), GRASS + 2, far.z() + 2), Blocks.DIRT.defaultBlockState());
			List<Crumb> built = CrumbFeature.live(level, l, snapshot(new BlockPos(x0 - 100_000, 0, z0)), new Sc32Config(), List.of());
			CrumbData.dropPending();
			Crumb m = built.stream().filter(c -> c.index() == mid.index()).findFirst().orElse(null);
			Crumb f = built.stream().filter(c -> c.index() == far.index()).findFirst().orElse(null);
			if (m == null || f == null) {
				wrong.add("version " + v + ": a crumb not built (mid " + m + ", far " + f + ")");
				continue;
			}
			boolean midUp = m.at().getX() >= mid.x() + 2 && m.at().getX() <= mid.x() + 5;
			boolean farUp = Math.abs(f.at().getX() - far.x()) >= 6;
			if (v == 2 && (!midUp || !farUp)) {
				wrong.add("version 2 not on the rises: mid at " + m.at().toShortString() + " (point " + mid.x() + "), far at " + f.at().toShortString() + " (point "
						+ far.x() + ")");
			}
			if (v == 1 && (midUp || farUp)) {
				wrong.add("version 1 moved: mid at " + m.at().toShortString() + ", far at " + f.at().toShortString());
			}
			rise[v - 1] = m.at().getY() + f.at().getY();
		}
		if (rise[1] - rise[0] != 4) {
			wrong.add("the two rises: " + (rise[1] - rise[0]) + " higher, not 4");
		}
		helper.assertTrue(wrong.isEmpty(), "rises: " + wrong);
		helper.succeed();
	}

	/**
	 * R3A-7's sight check, on open land (it is what the real run counts): from beside every built crumb, at a walker's eye,
	 * the next one toward the place is in view; a wall 6 high across the line between two mid crumbs hides the one past it.
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void theNextCrumbIsInViewOnOpenLandAndAWallHidesIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x0 = slotX(5);
		int z0 = BASE_Z;
		flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 - 14, x0 + 680, HIGH, z0 + 14));
		List<String> wrong = new ArrayList<>();
		for (String kind : List.of("s20", "s16", "s17")) {
			Line l = line("t/sight/" + kind + x0, kind, x0, z0, 0.0, 640);
			flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 - 14, x0 + 680, HIGH, z0 + 14));
			List<Crumb> built = new ArrayList<>(CrumbFeature.live(level, l, snapshot(new BlockPos(x0 - 100_000, 0, z0)), new Sc32Config(), List.of()));
			CrumbData.dropPending();
			built.sort(java.util.Comparator.comparingInt(Crumb::index));
			for (int i = 1; i < built.size(); i++) {
				if (!nextInView(level, built.get(i), built.get(i - 1))) {
					wrong.add(kind + ": " + built.get(i).tier() + built.get(i).index() + " does not see " + built.get(i - 1).index());
				}
			}
			if (kind.equals("s20")) {
				Crumb a = built.stream().filter(c -> c.tier() == Tier.MID).skip(1).findFirst().orElseThrow();
				Crumb b = built.get(built.indexOf(a) - 1);
				int wall = (a.at().getX() + b.at().getX()) / 2;
				fill(level, new BoundingBox(wall, GRASS + 1, z0 - 10, wall, GRASS + 6, z0 + 10), Blocks.STONE.defaultBlockState());
				if (nextInView(level, a, b)) {
					wrong.add("seen through a wall: " + a.index() + " to " + b.index());
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "sight: " + wrong);
		helper.succeed();
	}

	/** A walker's eye beside {@code from}, 3 toward {@code to} (past its widest thing), on the land there (under a tree, not on it). */
	static Vec3 walkerEye(ServerLevel level, Crumb from, Crumb to) {
		double dx = to.at().getX() - from.at().getX();
		double dz = to.at().getZ() - from.at().getZ();
		double len = Math.max(1, Math.hypot(dx, dz));
		double x = from.at().getX() + 0.5 + dx / len * 3;
		double z = from.at().getZ() + 0.5 + dz / len * 3;
		return new Vec3(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z))
				+ com.forzacode.a1016_02.core.Gallery.EYE, z);
	}

	/**
	 * True if from a walker's eye beside {@code from} some cell of {@code to}'s post is in view: the sight line to it meets
	 * nothing, or meets {@code to}'s own thing first (anything within 2 of its foot across and from its ground to over its
	 * top: a thing is wider than its post).
	 */
	static boolean nextInView(ServerLevel level, Crumb from, Crumb to) {
		Vec3 eye = walkerEye(level, from, to);
		List<BlockPos> cells = new ArrayList<>(to.cells());
		java.util.Collections.reverse(cells);
		int top = to.at().getY() + Math.max(1, to.height());
		for (BlockPos cell : cells) {
			net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, Vec3.atCenterOf(cell),
					net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE,
					net.minecraft.world.phys.shapes.CollisionContext.empty()));
			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
				return true;
			}
			BlockPos at = hit.getBlockPos();
			if (Math.max(Math.abs(at.getX() - to.at().getX()), Math.abs(at.getZ() - to.at().getZ())) <= 2 && at.getY() >= to.at().getY() - 1 && at.getY() <= top + 1) {
				return true;
			}
		}
		return false;
	}

	private static int spotD(Line l, int index) {
		for (Spot s : l.spots()) {
			if (s.index() == index) {
				return s.d();
			}
		}
		return -1;
	}

	/**
	 * A line built chunk by chunk (worldgen's path, the chunks in a shuffled order) equals the same line built live,
	 * block for block; each chunk's pass writes only in that chunk; nothing within the spawn's clearance.
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void aLineBuiltChunkByChunkEqualsTheSameLineBuiltLiveAndEachChunkWritesOnlyInItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x0 = slotX(2);
		int z0 = BASE_Z;
		int moved = 256;
		flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 - 14, x0 + 680, HIGH, z0 + 14));
		flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 + moved - 14, x0 + 680, HIGH, z0 + moved + 14));
		Sc32Config c = new Sc32Config();
		Snapshot snap = snapshot(new BlockPos(x0 - 100_000, 0, z0));
		Line one = line("t/same/a/" + x0, "s16", x0, z0, 0.0, 640);
		Line two = new Line("t/same/b/" + x0, "s16", x0, z0 + moved, 0.0, 640, one.version(), one.seed(),
				new int[] {one.own()[0], one.own()[1] + moved, one.own()[2], one.own()[3] + moved}, one.tiers());
		List<ChunkPos> chunks = new ArrayList<>();
		for (int cx = (x0 - 8) >> 4; cx <= (x0 + 660) >> 4; cx++) {
			for (int cz = (z0 - 16) >> 4; cz <= (z0 + 16) >> 4; cz++) {
				chunks.add(new ChunkPos(cx, cz));
			}
		}
		Collections.shuffle(chunks, new Random(7));
		int built = 0;
		for (ChunkPos chunk : chunks) {
			Map<BlockPos, BlockState> before = corridor(level, x0 - 8, x0 + 660, z0, 0);
			int n = CrumbFeature.decorate(level, chunk, snap, c, List.of(one));
			built += n;
			if (n > 0) {
				Map<BlockPos, BlockState> after = corridor(level, x0 - 8, x0 + 660, z0, 0);
				for (Map.Entry<BlockPos, BlockState> e : after.entrySet()) {
					BlockPos p = e.getKey();
					if (!e.getValue().equals(before.get(p))) {
						helper.assertTrue(p.getX() >> 4 == chunk.x() && (p.getZ() + z0) >> 4 == chunk.z(), "chunk " + chunk + " wrote at " + p);
					}
				}
			}
		}
		helper.assertTrue(built >= 15, "only " + built + " crumbs");
		List<Crumb> live = CrumbFeature.live(level, two, snap, c, List.of());
		CrumbData.dropPending();
		helper.assertTrue(live.size() == built, built + " by chunks, " + live.size() + " live");
		Map<BlockPos, BlockState> a = corridor(level, x0 - 8, x0 + 660, z0, 0);
		Map<BlockPos, BlockState> b = corridor(level, x0 - 8, x0 + 660, z0 + moved, 0);
		List<String> differ = new ArrayList<>();
		for (BlockPos p : a.keySet()) {
			if (!a.get(p).equals(b.get(p)) && differ.size() < 6) {
				differ.add(p + " " + a.get(p) + " / " + b.get(p));
			}
		}
		helper.assertTrue(differ.isEmpty() && a.size() == b.size(), "they differ (" + a.size() + " / " + b.size() + "): " + differ);
		// The spawn's clearance: with the spawn 300 out on the line, nothing within 256 of it.
		BlockPos spawn = new BlockPos(x0 + 300, GRASS, z0 + 2 * moved);
		flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 + 2 * moved - 14, x0 + 680, HIGH, z0 + 2 * moved + 14));
		Line three = new Line("t/same/c/" + x0, "s16", x0, z0 + 2 * moved, 0.0, 640, one.version(), one.seed(), one.own(), one.tiers());
		for (Crumb crumb : CrumbFeature.live(level, three, snapshot(spawn), c, List.of())) {
			helper.assertTrue(crumb.at().distSqr(spawn) >= 255.0 * 255.0, "a crumb within the spawn's clearance: " + crumb.at());
		}
		CrumbData.dropPending();
		helper.succeed();
	}

	/**
	 * A line is fixed the first time it builds (R12-2): its tag round-trips; a later layout of the same line (another
	 * length, other spacings) is not taken, the fixed one is; the places' lines come back with the fixed one in place.
	 */
	@GameTest
	public void aLineIsFixedTheFirstTimeItBuildsAndKeepsItsLayout(GameTestHelper helper) {
		String prefix = CrumbLines.prefix(0x32F1L + BASE_X, 7L);
		String key = prefix + "s17@1,2/A";
		Line first = new Line(key, "s17", 100, 200, 1.25, 600, CrumbLines.VERSION, 77L, new int[] {1, 2, 3, 4}, new int[] {192, 384, 16, 32, 56});
		try {
			helper.assertTrue(Line.fromTag(key, first.toTag()).equals(first), "the tag does not round-trip");
			helper.assertTrue(CrumbIndex.fix(first).equals(first), "the first fix is not the one in force");
			Line later = new Line(key, "s17", 100, 200, 1.25, 400, CrumbLines.VERSION + 1, 77L, new int[] {1, 2, 3, 4}, new int[] {160, 320, 20, 40, 64});
			helper.assertTrue(CrumbIndex.fix(later).equals(first), "a later layout replaced the fixed one");
			helper.assertTrue(first.equals(CrumbIndex.fixed(key)), "the fixed one is not read back");
			Snapshot s = new Snapshot(true, Stage.TRACES, BlockPos.ZERO, 0x32F1L + BASE_X, 7L, Map.of(), true);
			Line b = new Line(prefix + "s17@1,2/B", "s17", 100, 200, 3.0, 640, CrumbLines.VERSION, 78L, new int[] {1, 2, 3, 4}, first.tiers());
			Method m = new Method();
			List<Line> lines = m.withFixed(List.of(later, b), s);
			helper.assertTrue(lines.size() == 2 && lines.get(0).equals(first) && lines.get(1).equals(b), "the fixed one not in its place: " + lines);
		} finally {
			Plans.forget(prefix);
		}
		helper.succeed();
	}

	/** Reaches the index's package-private merge through its public pieces (fixed, then planned). */
	private static final class Method {
		List<Line> withFixed(List<Line> planned, Snapshot s) {
			List<Line> out = new ArrayList<>();
			for (Line p : planned) {
				Line f = CrumbIndex.fixed(p.key());
				out.add(f != null ? f : p);
			}
			return out;
		}
	}

	// --- 32b and 32c ---

	private static ServerPlayer player(GameTestHelper helper, double x, double z, float yaw) {
		ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		p.snapTo(x, GRASS + 1, z, yaw, 0.0F);
		p.setYHeadRot(yaw);
		return p;
	}

	private static final CrumbRules.Homes NO_HOMES = new CrumbRules.Homes() {
		@Override
		public Optional<BlockPos> base(UUID player) {
			return Optional.empty();
		}

		@Override
		public boolean nearAny(BlockPos pos, double radius) {
			return false;
		}
	};

	/**
	 * 32b: a crumb seen by one who then walks away is taken when a newcomer comes within 64 of it, out of view, through
	 * the ledger; not while the seer is near; once a line, once a day a world; the switch's give-back puts it back.
	 * Sightings need the crumb in front of one within 32.
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void itWasRightHereTakesAPostOnceWhenTheSeersAreAwayAndGivesItBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x0 = slotX(3);
		int z0 = BASE_Z;
		flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 - 14, x0 + 680, HIGH, z0 + 14));
		Sc32Config c = new Sc32Config();
		Line l = line("t/gone/" + x0, "s17", x0, z0, 0.0, 640);
		List<Crumb> built = CrumbFeature.live(level, l, snapshot(new BlockPos(x0 - 100_000, 0, z0)), c, List.of());
		CrumbData.dropPending();
		CrumbData data = new CrumbData();
		built.forEach(data::add);
		Crumb post = built.stream().filter(cr -> cr.tier() == Tier.MID && cr.post()).findFirst().orElseThrow();
		helper.assertTrue(post.height() >= 4 && post.at().getY() == GRASS + 1 && level.getBlockState(post.at().above(3)).is(Blocks.BOOKSHELF),
				"17's mid post: " + post);
		// The seer looks at it from 20 east (yaw 90: west), then walks off.
		ServerPlayer seer = player(helper, post.at().getX() + 20.5, post.at().getZ() + 0.5, 90.0F);
		CrumbRules.LIVE.sample(level, data, seer, 1L, NO_HOMES);
		helper.assertTrue(data.hasSeen(seer.getUUID(), post.id()), "not seen from 20 in front");
		ServerPlayer blind = player(helper, post.at().getX() + 20.5, post.at().getZ() + 0.5, 180.0F);
		CrumbRules.LIVE.sample(level, data, blind, 1L, NO_HOMES);
		helper.assertTrue(!data.hasSeen(blind.getUUID(), post.id()), "seen with its back to it");
		ServerPlayer newcomer = player(helper, post.at().getX() + 50.5, post.at().getZ() + 0.5, 90.0F);
		// The seer near: not taken.
		String r0 = CrumbRules.LIVE.gone(level, data, List.of(seer, newcomer), 1L, c);
		helper.assertTrue(level.getBlockState(post.at().above(3)).is(Blocks.BOOKSHELF) && !data.goneFrom(l.key()),
				"taken with the seer near: " + r0 + ", seen by " + data.seers(post.id()).size() + ", seer " + seer.getUUID() + " newcomer " + newcomer.getUUID()
						+ " shelf " + level.getBlockState(post.at().above(3)));
		// The seer away: taken, every block, and the line marked.
		seer.snapTo(post.at().getX() + 150.5, GRASS + 1, post.at().getZ() + 0.5, 90.0F, 0.0F);
		data.see(seer.getUUID(), built.getLast().id());
		CrumbRules.LIVE.gone(level, data, List.of(seer, newcomer), 1L, c);
		helper.assertTrue(data.goneFrom(l.key()), "not taken with the seer away");
		Crumb taken = null;
		for (Crumb cr : built) {
			if (level.getBlockState(cr.at()).isAir() && cr.post()) {
				taken = cr;
			}
		}
		helper.assertTrue(taken != null, "nothing taken");
		for (BlockPos cell : taken.cells()) {
			helper.assertTrue(level.getBlockState(cell).isAir(), "a block of it left at " + cell);
		}
		// Once a line, once a day.
		int standing = 0;
		for (Crumb cr : built) {
			standing += cr.post() && !level.getBlockState(cr.at()).isAir() ? 1 : 0;
		}
		for (Crumb cr : built) {
			data.see(seer.getUUID(), cr.id());
		}
		CrumbRules.LIVE.gone(level, data, List.of(seer, newcomer), 2L, c);
		int after = 0;
		for (Crumb cr : built) {
			after += cr.post() && !level.getBlockState(cr.at()).isAir() ? 1 : 0;
		}
		helper.assertTrue(after == standing, "a second taken from the line");
		flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 + 50, x0 + 680, HIGH, z0 + 78));
		Line other = line("t/gone2/" + x0, "s19", x0, z0 + 64, 0.0, 640);
		List<Crumb> others = CrumbFeature.live(level, other, snapshot(new BlockPos(x0 - 100_000, 0, z0)), c, List.of());
		CrumbData.dropPending();
		others.forEach(data::add);
		for (Crumb cr : others) {
			data.see(seer.getUUID(), cr.id());
		}
		Crumb otherPost = others.stream().filter(Crumb::post).findFirst().orElseThrow();
		ServerPlayer second = player(helper, otherPost.at().getX() + 30.5, otherPost.at().getZ() + 0.5, 90.0F);
		CrumbRules.LIVE.gone(level, data, List.of(seer, newcomer, second), 1L, c);
		helper.assertTrue(!data.goneFrom(other.key()), "a second the same day");
		// The next day it may.
		CrumbRules.LIVE.gone(level, data, List.of(seer, newcomer, second), 2L, c);
		helper.assertTrue(data.goneFrom(other.key()), "not the next day either");
		// The switch gives it back.
		WorldSc.giveBackAll(level.getServer(), S32.CAUSE);
		helper.assertTrue(!level.getBlockState(taken.at()).isAir(), "not given back");
		helper.succeed();
	}

	/**
	 * 32c: a move owed tonight takes the line's farthest far post, every block of it, to 96 to 160 from the player's
	 * base on the line from it toward the nearest crumb they saw; never by another base or a block a player placed; the
	 * next night's goes 64 farther out; one a day a player, {@code m} a line; not by day.
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void itCameCloserMovesTheFarthestFarPostTowardTheBaseAndTheSpurGrows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x0 = slotX(4);
		int z0 = BASE_Z;
		flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 - 14, x0 + 680, HIGH, z0 + 14));
		BlockPos base = new BlockPos(x0 + 300, GRASS + 1, z0 + 400);
		flat(level, new BoundingBox(base.getX() - 64, GRASS - 6, base.getZ() - 190, base.getX() + 12, HIGH, base.getZ() + 12));
		Sc32Config c = new Sc32Config();
		c.pullPerLine = 2;
		String prefix = CrumbLines.prefix(0x32C2L + BASE_X, 1L);
		Line l = line(prefix + "s16@0,0/A", "s16", x0, z0, 0.0, 640);
		try {
			CrumbIndex.fix(l);
			List<Crumb> built = CrumbFeature.live(level, l, snapshot(new BlockPos(x0 - 100_000, 0, z0)), c, List.of());
			CrumbData.dropPending();
			CrumbData data = new CrumbData();
			built.forEach(data::add);
			List<Crumb> far = built.stream().filter(cr -> cr.tier() == Tier.FAR && cr.post()).toList();
			helper.assertTrue(far.size() >= 3, "far posts: " + far.size());
			Crumb last = far.getLast();
			BlockState was = level.getBlockState(last.at());
			BlockPos nearest = built.stream().filter(cr -> cr.tier() == Tier.MID).findFirst().orElseThrow().at();
			// FIX-R3-C: the moves land 96 to 160 out, then 64 farther (up to 224 and the footing's shifts), on the line
			// from the base toward the nearest crumb. A fixed id and a flattened, loaded corridor along that whole line keep
			// every step on footing (a random id and the base's box alone left the second step in unloaded land at times).
			UUID player = new UUID(0x32C2L, 0x32C2L);
			flatToward(level, base, nearest, c.pullMax + 64 + 16);
			CrumbRules.Homes homes = new CrumbRules.Homes() {
				@Override
				public Optional<BlockPos> base(UUID p) {
					return p.equals(player) ? Optional.of(base) : Optional.empty();
				}

				@Override
				public boolean nearAny(BlockPos pos, double radius) {
					return pos.distSqr(base) < radius * radius;
				}
			};
			CrumbRules.LIVE.owe(player, l.key(), nearest, 5L);
			helper.assertTrue(CrumbRules.LIVE.pull(level, data, 5L, c, homes, false).contains("night"), "moved by day");
			String r = CrumbRules.LIVE.pull(level, data, 5L, c, homes, true);
			helper.assertTrue(r.contains("moved"), "not moved: " + r);
			Crumb moved = data.line(l.key()).get(last.index());
			double d = Math.hypot(moved.at().getX() - base.getX(), moved.at().getZ() - base.getZ());
			helper.assertTrue(moved.moved() && d >= c.pullMin - 6 && d <= c.pullMax + 6, "moved to " + d + " from the base");
			double ax = nearest.getX() - base.getX();
			double az = nearest.getZ() - base.getZ();
			double off = Math.abs((moved.at().getX() - base.getX()) * az - (moved.at().getZ() - base.getZ()) * ax) / Math.hypot(ax, az);
			helper.assertTrue(off <= 2.0, "off the line toward the crumb by " + off);
			helper.assertTrue(level.getBlockState(last.at()).isAir() && level.getBlockState(moved.at()).equals(was), "not every block moved");
			for (BlockPos cell : moved.cells()) {
				helper.assertTrue(!level.getBlockState(cell).isAir(), "a block missing at " + cell);
			}
			// One a day: owed again the same day, nothing.
			CrumbRules.LIVE.owe(player, l.key(), nearest, 5L);
			CrumbRules.LIVE.pull(level, data, 5L, c, homes, true);
			helper.assertTrue(data.line(l.key()).values().stream().filter(Crumb::moved).count() == 1, "two in a day");
			// The next night: the next far post, 64 farther out; a placed block on its way pushes it along.
			ServerPlayer placer = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			CrumbRules.LIVE.owe(player, l.key(), nearest, 6L);
			r = CrumbRules.LIVE.pull(level, data, 6L, c, homes, true);
			helper.assertTrue(r.contains("moved"), "the spur's second step: " + r);
			Crumb second = data.line(l.key()).get(far.get(far.size() - 2).index());
			double d2 = Math.hypot(second.at().getX() - base.getX(), second.at().getZ() - base.getZ());
			helper.assertTrue(second.moved() && Math.abs(d2 - (d + 64)) <= 6, "the second step at " + d2 + ", the first at " + d);
			// The line's cap (m = 2 here).
			CrumbRules.LIVE.owe(player, l.key(), nearest, 7L);
			CrumbRules.LIVE.pull(level, data, 7L, c, homes, true);
			helper.assertTrue(data.line(l.key()).values().stream().filter(Crumb::moved).count() == 2, "past the line's cap");
			// Never by a placed block: a fresh line, a block a player placed right where its post would go.
			data.resetRules();
			flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 + 50, x0 + 680, HIGH, z0 + 78));
			Line l2 = line(prefix + "s16@0,1/A", "s16", x0, z0 + 64, 0.0, 640);
			CrumbIndex.fix(l2);
			List<Crumb> built2 = CrumbFeature.live(level, l2, snapshot(new BlockPos(x0 - 100_000, 0, z0)), c, List.of());
			CrumbData.dropPending();
			built2.forEach(data::add);
			double ux = (nearest.getX() - base.getX()) / Math.hypot(ax, az);
			double uz = (nearest.getZ() - base.getZ()) / Math.hypot(ax, az);
			for (int k = c.pullMin - 8; k <= c.pullMax + 8; k += 4) {
				BlockPos p = new BlockPos((int) Math.floor(base.getX() + 0.5 + ux * k), GRASS + 1, (int) Math.floor(base.getZ() + 0.5 + uz * k));
				level.setBlock(p.east(3), Blocks.COBBLESTONE.defaultBlockState(), FLAGS);
				Services.watch().onPlaced(placer, level, p.east(3), Blocks.COBBLESTONE.defaultBlockState());
			}
			CrumbRules.LIVE.owe(player, l2.key(), nearest, 9L);
			r = CrumbRules.LIVE.pull(level, data, 9L, c, homes, true);
			helper.assertTrue(!r.contains("moved") && data.line(l2.key()).values().stream().noneMatch(Crumb::moved), "moved by a placed block: " + r);
		} finally {
			Plans.forget(prefix);
		}
		helper.succeed();
	}

	/** Asleep (here: the test world under Traces, or the switch), neither rule runs. */
	@GameTest
	public void asleepNeitherRuleRuns(GameTestHelper helper) {
		boolean awake = Remains.awake(helper.getLevel().getServer());
		String r = CrumbRules.LIVE.run(helper.getLevel().getServer(), true);
		helper.assertTrue(awake || r.equals("asleep"), "ran asleep: " + r);
		helper.succeed();
	}

	/**
	 * R3-V: a crumb takes the place of the land's flowers and ground cover as of its grass (the real run's lines in a
	 * cherry grove lost crumbs to pink petals and wildflowers), never of a log, a made block or a torch.
	 */
	@GameTest
	public void aCrumbTakesThePlaceOfFlowersAsOfGrass(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (net.minecraft.world.level.block.Block plant : List.of(Blocks.SHORT_GRASS, Blocks.POPPY, Blocks.DANDELION, Blocks.PINK_PETALS, Blocks.WILDFLOWERS,
				Blocks.LEAF_LITTER, Blocks.BUSH, Blocks.FIREFLY_BUSH, Blocks.SHORT_DRY_GRASS, Blocks.SUNFLOWER, Blocks.CACTUS_FLOWER)) {
			if (!CrumbFooting.natural(plant.defaultBlockState())) {
				wrong.add(plant + " is not the land's");
			}
		}
		for (net.minecraft.world.level.block.Block made : List.of(Blocks.OAK_LOG, Blocks.COBBLESTONE, Blocks.TORCH, Blocks.CHEST, Blocks.OAK_PLANKS, Blocks.LAVA)) {
			if (CrumbFooting.natural(made.defaultBlockState())) {
				wrong.add(made + " taken as the land's");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** The feature refuses without a snapshot, switched off, before the spawn is known, without a config. */
	@GameTest
	public void theFeatureBuildsNothingWhenRefused(GameTestHelper helper) {
		Sc32Config c = new Sc32Config();
		helper.assertTrue(CrumbFeature.refusal(null, c) != null, "built with no snapshot");
		helper.assertTrue(CrumbFeature.refusal(new Snapshot(false, Stage.TRACES, BlockPos.ZERO, 1L, 1L, Map.of(), true), c) != null, "built switched off");
		helper.assertTrue(CrumbFeature.refusal(new Snapshot(true, Stage.TRACES, BlockPos.ZERO, 1L, 1L, Map.of(), false), c) != null, "built before the spawn");
		helper.assertTrue(CrumbFeature.refusal(new Snapshot(true, Stage.ALONE, BlockPos.ZERO, 1L, 1L, Map.of(), true), null) != null, "built with no config");
		helper.assertTrue(CrumbFeature.refusal(new Snapshot(true, Stage.ALONE, BlockPos.ZERO, 1L, 1L, Map.of(), true), c) == null == Plans.ready(),
				"refused in Alone (D-108: from the world's creation)");
		helper.succeed();
	}

	/**
	 * FIX-R3-C, when asked ({@code -Da1016_02.s32sight=<worlds>}): the land's own share of the sight from one crumb to the
	 * next, over the real noise (no trees: an upper bound the real run's check scales down): from a walker's eye on the
	 * land at random points of each world's land, is a thing {@code h} high standing on the land {@code d} away in a random
	 * direction in view over the heights between? By distance and height; printed. Asserts nothing.
	 */
	@GameTest(environment = "a1016_02:world/sc2_heights", maxTicks = 200)
	public void theLandsShareOfTheSightWhenAsked(GameTestHelper helper) {
		String asked = System.getProperty("a1016_02.s32sight", "");
		if (asked.isEmpty() || asked.equals("false")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		int worlds = asked.equals("true") ? 3 : Integer.parseInt(asked);
		long[] seeds = {SelfCreated2HeightsTests.REAL_SEED, 1L, 77L, 2024L, 42L, 9001L};
		int[] distances = {16, 20, 24, 28, 32, 36, 40, 44, 48, 56};
		int[] heights = {1, 2, 3, 4, 5};
		int[][][] seen = new int[heights.length][distances.length][2];
		Random random = new Random(0x5167L);
		for (int w = 0; w < Math.min(worlds, seeds.length); w++) {
			Sc2RealNoise.World world = Sc2RealNoise.normal(level, seeds[w]);
			com.forzacode.a1016_02.world.gen.Terrain land = world.terrain();
			int points = 0;
			for (int tries = 0; tries < 20_000 && points < 1500; tries++) {
				int x = random.nextInt(-3000, 3000);
				int z = random.nextInt(-3000, 3000);
				if (land.wet(x, z)) {
					continue;
				}
				points++;
				double a = random.nextDouble() * Math.PI * 2;
				double ux = Math.cos(a);
				double uz = Math.sin(a);
				// The eye 3 toward the thing from the crumb's foot, as the real check stands it.
				double ex = x + 0.5 + ux * 3;
				double ez = z + 0.5 + uz * 3;
				double eye = land.ground((int) Math.floor(ex), (int) Math.floor(ez)) + 1 + com.forzacode.a1016_02.core.Gallery.EYE;
				for (int k = 0; k < distances.length; k++) {
					double tx = x + 0.5 + ux * distances[k];
					double tz = z + 0.5 + uz * distances[k];
					int tg = land.ground((int) Math.floor(tx), (int) Math.floor(tz));
					if (land.wet((int) Math.floor(tx), (int) Math.floor(tz))) {
						continue;
					}
					for (int hi = 0; hi < heights.length; hi++) {
						double top = tg + heights[hi] + 0.5;
						boolean clear = true;
						double len = distances[k] - 3;
						for (double s = 1.0; s < len - 1.0 && clear; s += 0.5) {
							double t = s / len;
							double px = ex + (tx - ex) * t;
							double pz = ez + (tz - ez) * t;
							double py = eye + (top - eye) * t;
							clear = py >= land.ground((int) Math.floor(px), (int) Math.floor(pz)) + 1;
						}
						seen[hi][k][1]++;
						seen[hi][k][0] += clear ? 1 : 0;
					}
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (int hi = 0; hi < heights.length; hi++) {
			StringBuilder line = new StringBuilder("32 sight over the land alone, a thing " + heights[hi] + " high:");
			for (int k = 0; k < distances.length; k++) {
				int[] c = seen[hi][k];
				line.append(String.format(java.util.Locale.ROOT, " %d %.0f %%", distances[k], c[1] == 0 ? 0.0 : 100.0 * c[0] / c[1]));
			}
			out.add(line.toString());
		}
		out.forEach(Sc2RealNoise::log);
		helper.succeed();
	}

	/**
	 * The cost when asked ({@code -Da1016_02.s32cost=true}; D-118: a chunk 0.5 ms median, 25 ms worst, a candidate's first
	 * siting aside): worldgen's per-chunk pass over every chunk of six lines' corridors (one of each kind, chunks with no
	 * crumb included), and the runtime pass (sightings, 32b, 32c's watch) for two participants among them. Printed to
	 * {@code build/s32cost.txt}.
	 */
	@GameTest(environment = ENV, maxTicks = 1200)
	public void costWhenAsked(GameTestHelper helper) throws java.io.IOException {
		if (!Boolean.getBoolean("a1016_02.s32cost")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		int x0 = slotX(6);
		Sc32Config c = new Sc32Config();
		Snapshot snap = snapshot(new BlockPos(x0 - 100_000, 0, BASE_Z));
		List<Long> chunkNs = new ArrayList<>();
		List<Long> crumbNs = new ArrayList<>();
		CrumbData data = new CrumbData();
		int k = 0;
		for (String kind : CrumbGalleryKinds.ALL) {
			int z0 = BASE_Z + 64 * k++;
			flat(level, new BoundingBox(x0 - 24, GRASS - 6, z0 - 14, x0 + 680, HIGH, z0 + 14));
			Line l = line("t/cost/" + kind + x0, kind, x0, z0, 0.0, 640);
			for (int cx = (x0 - 8) >> 4; cx <= (x0 + 660) >> 4; cx++) {
				for (int cz = (z0 - 24) >> 4; cz <= (z0 + 24) >> 4; cz++) {
					long t0 = System.nanoTime();
					int n = CrumbFeature.decorate(level, new ChunkPos(cx, cz), snap, c, List.of(l));
					long dt = System.nanoTime() - t0;
					chunkNs.add(dt);
					if (n > 0) {
						crumbNs.add(dt);
					}
				}
			}
		}
		for (int i = 0; i < 64; i++) {
			// What a chunk no line crosses costs: the index's answer, as worldgen meets it (a map read and the reach test).
			long t0 = System.nanoTime();
			CrumbFeature.decorate(level, new ChunkPos((x0 >> 4) + i, (BASE_Z >> 4) + 40), snap, c, List.of());
			chunkNs.add(System.nanoTime() - t0);
		}
		data.drain();
		Collections.sort(chunkNs);
		Collections.sort(crumbNs);
		ServerPlayer a = player(helper, x0 + 300.5, BASE_Z + 2.5, 90.0F);
		ServerPlayer b = player(helper, x0 + 200.5, BASE_Z + 66.5, 90.0F);
		List<Long> ruleNs = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			long t0 = System.nanoTime();
			CrumbRules.LIVE.sample(level, data, a, 1L, NO_HOMES);
			CrumbRules.LIVE.sample(level, data, b, 1L, NO_HOMES);
			CrumbRules.LIVE.gone(level, data, List.of(a, b), 1L, c);
			ruleNs.add(System.nanoTime() - t0);
		}
		Collections.sort(ruleNs);
		List<String> out = List.of(String.format(java.util.Locale.ROOT,
				"32 a chunk: median %.3f ms, p95 %.3f ms, worst %.2f ms over %d chunks; a chunk with a crumb: median %.3f ms, worst %.2f ms over %d",
				chunkNs.get(chunkNs.size() / 2) / 1e6, chunkNs.get(chunkNs.size() * 95 / 100) / 1e6, chunkNs.getLast() / 1e6, chunkNs.size(),
				crumbNs.get(crumbNs.size() / 2) / 1e6, crumbNs.getLast() / 1e6, crumbNs.size()),
				String.format(java.util.Locale.ROOT, "32 the rules' pass for two participants (every 20 ticks): median %.1f us, worst %.1f us",
						ruleNs.get(ruleNs.size() / 2) / 1e3, ruleNs.getLast() / 1e3));
		java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
		java.nio.file.Files.write(java.nio.file.Path.of("build", "s32cost.txt"), out);
		Sc2RealNoise.log(String.join("\n", out));
		helper.succeed();
	}

	// --- the find run's real crumbs (T3, T4 with real lines; T5) ---

	/** T5's tallies over a find run, by era: [lines, lines followable, lines dropped short, crumbs]. */
	static final Map<String, long[]> T5 = new java.util.concurrent.ConcurrentHashMap<>();
	/** Each place's lines' planning times (ns), for the cost line. */
	static final List<Long> PLANNING = Collections.synchronizedList(new ArrayList<>());

	/**
	 * The real lines of a find world's places and of its 19 (their own planner, cut by the noise's model), each crumb
	 * where its footing over the noise puts it in its own chunk (worldgen's rule, the live blocks aside): added to
	 * {@code grid} for T3 and T4, and each line's gaps tallied for T5 under {@code era}.
	 */
	static void real(SelfCreated3FindTests.Grid grid, List<SelfCreated3FindTests.Place> places, Sc2RealNoise.World world, String era, BlockPos spawn,
			int lineFrom, int lineMin, int lineMax) {
		Sc32Config c = new Sc32Config();
		CrumbFooting.Ground ground = CrumbFooting.Ground.noise(world.terrain());
		Sc19Config c19 = new Sc19Config();
		c19.startMin = lineFrom;
		c19.lengthMin = lineMin;
		c19.lengthMax = lineMax;
		// FIX-R3-E: 19 as worldgen lays it out, kept clear of the places.
		List<LinePlan.Segment> segments = SelfCreated2LineTests.clearOf(LinePlan.of(world.seed(), 7L, c19, world.minY(), world.maxY()), places, c19);
		List<From> froms = new ArrayList<>();
		for (SelfCreated3FindTests.Place p : places) {
			froms.add(new From(p.kind(), p.kind() + "@" + p.chunk().x() + "," + p.chunk().z(), p.x(), p.z(), p.box(),
					CrumbLines.mix(world.seed(), p.chunk().pack()), null));
		}
		for (LinePlan.Segment s : segments) {
			if (s.gapWidth() > 0) {
				froms.add(CrumbIndex.wallFrom(s));
			}
		}
		long[] t = T5.computeIfAbsent(era, k -> new long[4]);
		// Each place's lines on the common pool (the noise is read per thread); one footing a spot, shared by the cut and T5.
		List<List<int[]>> found = froms.parallelStream().map(f -> {
			List<Box> others = new ArrayList<>();
			for (SelfCreated3FindTests.Place o : places) {
				if (!o.box().equals(f.own()) && Math.abs(o.x() - f.x()) < 1_000 && Math.abs(o.z() - f.z()) < 1_000) {
					others.add(Box.of(o.box()));
				}
			}
			if (!f.kind().equals("s19")) {
				for (LinePlan.Segment s : segments) {
					others.add(Box.of(s.box()));
				}
			}
			others.addAll(CrumbIndex.villages(world.terrain(), world.structures(), f.x(), f.z(), c.lineMax + CrumbIndex.VILLAGE_REACH));
			Map<Long, Optional<CrumbFooting.Found>> memo = new HashMap<>();
			java.util.function.Function<Spot, Optional<CrumbFooting.Found>> foot = s -> memo.computeIfAbsent(ChunkPos.pack(s.x(), s.z()) * 3 + s.tier().ordinal(),
					k -> Optional.ofNullable(CrumbFooting.find(ground, s, new ChunkPos(s.x() >> 4, s.z() >> 4))));
			long t0 = System.nanoTime();
			List<Line> lines = CrumbLines.plan(f, others, spawn.getX(), spawn.getZ(), c, world.seed(), 7L, s -> foot.apply(s).isPresent());
			PLANNING.add(System.nanoTime() - t0);
			int planned = f.fixed() != null ? f.fixed().length : 3;
			List<int[]> out = new ArrayList<>();
			// [0] a line's tally row: lines 1, followable, dropped, crumbs; then the crumbs' points.
			long dropped = planned - lines.size();
			out.add(new int[] {0, 0, (int) dropped, 0});
			for (Line l : lines) {
				List<int[]> placed = new ArrayList<>();
				for (Spot s : l.spots()) {
					Optional<CrumbFooting.Found> at = foot.apply(s);
					if (at.isPresent()) {
						placed.add(new int[] {s.d(), s.tier().ordinal()});
						out.add(new int[] {1, at.get().x(), at.get().z()});
					}
				}
				boolean ok = !placed.isEmpty();
				int last = 0;
				for (int i = 0; i < placed.size() && ok; i++) {
					Tier tier = Tier.values()[placed.get(i)[1]];
					int allowed = i == 0 ? CrumbLines.FIRST + l.maxGap(Tier.NEAR) + ownSpan(l) : l.maxGap(tier);
					ok = placed.get(i)[0] - last <= allowed;
					last = placed.get(i)[0];
				}
				out.add(new int[] {1, ok ? 1 : 0, 0, placed.size(), 0});
			}
			return out;
		}).toList();
		synchronized (t) {
			for (List<int[]> rows : found) {
				for (int[] r : rows) {
					if (r.length == 3) {
						grid.add(r[1], r[2]);
					} else {
						t[0] += r[0];
						t[1] += r[1];
						t[2] += r[2];
						t[3] += r[3];
					}
				}
			}
		}
	}

	/** How far the line runs in its own place's box before its first crumb (its place stands there). */
	private static int ownSpan(Line l) {
		int span = 0;
		for (int d = 0; d < l.length(); d += 4) {
			if (l.inOwn((int) Math.floor(l.x() + 0.5 + l.ux() * d), (int) Math.floor(l.z() + 0.5 + l.uz() * d), CrumbLines.OWN_MARGIN)) {
				span = d;
			}
		}
		return span;
	}

	/** T5's lines and the planning cost for the find run's report (and forgets them). */
	static List<String> t5Summary() {
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, long[]> e : new java.util.TreeMap<>(T5).entrySet()) {
			long[] t = e.getValue();
			out.add(String.format(java.util.Locale.ROOT, "T5 %s: %d lines, %.1f %% followable to their place, %d dropped short, %.1f crumbs a line",
					e.getKey(), t[0], t[0] == 0 ? 0 : 100.0 * t[1] / t[0], t[2], t[0] == 0 ? 0 : (double) t[3] / t[0]));
		}
		List<Long> ns = new ArrayList<>(PLANNING);
		Collections.sort(ns);
		if (!ns.isEmpty()) {
			out.add(String.format(java.util.Locale.ROOT, "32 planning one place's lines over the noise (once a world): median %.2f ms, worst %.1f ms over %d",
					ns.get(ns.size() / 2) / 1e6, ns.getLast() / 1e6, ns.size()));
		}
		T5.clear();
		PLANNING.clear();
		return out;
	}
}
