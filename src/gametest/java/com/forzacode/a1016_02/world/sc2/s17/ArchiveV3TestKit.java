package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.sc2.kit.Heights;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Recorder;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Piece;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Overlay;
import com.forzacode.a1016_02.world.sc2.kit.Shape;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R3-1's game test bodies that need the archive's package-private parts (docs/SELF_CREATED_2_R3.md, R3-1); the seam class
 * {@code world.SelfCreated3ArchiveTests} calls them.
 */
public final class ArchiveV3TestKit {
	/** What the archive builds with (its walls, floors and shelves): never to be seen from outside its openings. */
	static final Set<Block> BUILT = Set.of(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.INFESTED_COBBLESTONE, Blocks.BOOKSHELF,
			Blocks.CHISELED_BOOKSHELF, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.OAK_LOG);

	private ArchiveV3TestKit() {
	}

	/** True if this is an archive (any version) shape. */
	public static boolean isArchive(Shape shape) {
		return shape instanceof ArchiveShape;
	}

	/** Which parts of an archive a chunk holds (for the cost's report): the well's footprint, the hall, the cells. */
	public static String part(Shape shape, int cx, int cz) {
		ArchivePlan plan = ((ArchiveShape) shape).plan();
		BoundingBox chunk = new BoundingBox(cx << 4, -2048, cz << 4, (cx << 4) + 15, 2048, (cz << 4) + 15);
		List<String> out = new ArrayList<>();
		if (plan.well != null && chunk.intersects(BoundingBox.fromCorners(plan.well.at(0, 0, 0), plan.well.at(Well.SIDE - 1, 0, Well.SIDE - 1)))) {
			out.add("well");
		}
		if (chunk.intersects(plan.hallInside())) {
			out.add("hall");
		}
		if (chunk.intersects(plan.cellsInside())) {
			out.add("cells");
		}
		return String.join("+", out);
	}

	/** Where a world cell is in an archive's own terms (for reports): its r, u, f, and the well's i, j if near it. */
	public static String where(Shape shape, int x, int y, int z) {
		ArchivePlan plan = ((ArchiveShape) shape).plan();
		String out = "r " + plan.localR(x, z) + " u " + (y - plan.floorY()) + " f " + plan.localF(x, z);
		if (plan.well != null) {
			out += " (well i " + plan.well.iOf(plan.localR(x, z)) + " j " + (plan.localF(x, z) - plan.well.f0) + " y-floor " + (y - plan.well.floorY(plan.well.kGround))
					+ ", ground " + ((ArchiveShape) shape).plan().well.land + ")";
		}
		return out;
	}

	/** Tests only: collects why sitings refuse (null to stop). */
	public static void why(java.util.function.@org.jspecify.annotations.Nullable Consumer<String> sink) {
		ArchiveSiting.why = sink;
	}

	/** The shape version an archive shape is built as. */
	public static int version(Shape shape) {
		return ((ArchiveShape) shape).version();
	}

	/**
	 * Every built block of an archive filled into {@code overlay} (over the real noise) that has a face on air or a fluid the
	 * plan did not open: neither set by the shape (its insides, its cleared notches, its pool) nor meant open by it (its air
	 * over open air: the well's open face, the void over the land), so the sky, a cave, a crevasse, a river or a cliff's air.
	 * Each entry: the block, the face and where.
	 */
	public static List<String> exposed(Shape shape, Overlay overlay, Terrain terrain, int minY, int maxY) {
		ArchiveShape archive = (ArchiveShape) shape;
		List<String> out = new ArrayList<>();
		for (Long2ObjectMap.Entry<BlockState> e : overlay.cells().long2ObjectEntrySet()) {
			BlockState state = e.getValue();
			if (!BUILT.contains(state.getBlock())) {
				continue;
			}
			BlockPos p = BlockPos.of(e.getLongKey());
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (n.getY() <= minY || n.getY() >= maxY) {
					continue;
				}
				BlockState there = overlay.get(n.getX(), n.getY(), n.getZ());
				if (!there.isAir() && there.getFluidState().isEmpty()) {
					continue;
				}
				if (overlay.written(n.getX(), n.getY(), n.getZ())) {
					continue;
				}
				if (archive.intended(n.getX(), n.getY(), n.getZ(), terrain) != null) {
					continue;
				}
				out.add(state.getBlock().getDescriptionId().replace("block.minecraft.", "") + " " + d.getName() + " at " + p.toShortString()
						+ (there.isAir() ? " (air)" : " (fluid)"));
				break;
			}
		}
		return out;
	}

	// --- the shape (R3-1) ---

	/** The shape tests' own spot (no other test class builds here). */
	static final int X = 430_000;
	static final int Z = -435_000;
	static final int FLOOR = -14;
	static final int ROCK_TOP = 60;

	/** A version 3 archive, facing north, its hall's floor at {@value #FLOOR}, all of it in rock up to {@value #ROCK_TOP}. */
	static Live.Result build3(ServerLevel level, int x, int z, boolean longHall) {
		return build(level, x, z, longHall, 3, false);
	}

	/** How far the land falls in front of the well in {@link #build} with {@code cliff}. */
	public static final int CLIFF = 12;

	/**
	 * An archive of {@code version}, facing north, its hall's floor at {@value #FLOOR}, all of it in rock up to
	 * {@value #ROCK_TOP}; with {@code cliff}, the land before the well falls {@value #CLIFF} two out from its face, and all
	 * along its near side from 8 in (a front that falls away ahead and beside: the apron's kerbs, the talus's slope).
	 */
	static Live.Result build(ServerLevel level, int x, int z, boolean longHall, int version, boolean cliff) {
		Start start = new Start(new BlockPos(x, FLOOR, z), Direction.NORTH, ArchivePlan.variant3(longHall, 0.08, false, 0, level.getMinY()),
				ArchiveTestKit.SEED).withVersion(version);
		ArchivePlan probe = ArchivePlan.of(start, new int[0]);
		BoundingBox box = probe.box();
		ArchiveTestKit.load(level, box.inflatedBy(8));
		ArchiveTestKit.rock(level, new BoundingBox(box.minX() - 6, level.getMinY() + 1, box.minZ() - 6, box.maxX() + 6, ROCK_TOP, box.maxZ() + 6));
		if (cliff) {
			Well well = probe.well;
			for (int i = -6; i < Well.SIDE + 6; i++) {
				for (int j = Well.SIDE; j <= Well.SIDE + Well.TALUS + 6; j++) {
					if (j < Well.SIDE + 2 && i > 8) {
						continue;
					}
					BlockPos c = well.at(i, 0, j);
					for (int y = ROCK_TOP - CLIFF + 1; y <= ROCK_TOP; y++) {
						level.setBlock(new BlockPos(c.getX(), y, c.getZ()), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		Live.Result built = Live.place(level, S17.KIND, start);
		ArchiveTestKit.load(level, built.box());
		return built;
	}

	/**
	 * The land before a well built with a cliff ({@link #build}), for the fair edge rule ({@code world.Edges}): where a walk
	 * starts (the columns before the crack), the area it may walk, the columns that are the place's own (the apron and its
	 * edge in version 3, the talus's reach in version 4), and what the place put there (kerbs, scree), as text.
	 */
	public record Front(ServerLevel level, List<BlockPos> seeds, BoundingBox area, java.util.function.BiPredicate<Integer, Integer> own, int kerbs, int scree,
			int version) {
	}

	/** {@link Front} of a version {@code version} archive built over a falling front. */
	public static Front front(GameTestHelper helper, int version) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 1600 + 900 * version, Z + 3000, false, version, true);
		ArchivePlan plan = ArchiveTestKit.plan(built);
		Well well = plan.well;
		int sill = well.floorY(well.kGround);
		List<BlockPos> seeds = new ArrayList<>();
		for (int i = Well.WAY_I0; i < Well.WAY_I0 + Well.WAY_WIDE; i++) {
			seeds.add(well.at(i, sill, Well.SIDE));
		}
		BoundingBox area = BoundingBox.fromCorners(well.at(-2, level.getMinY(), Well.SIDE), well.at(Well.SIDE + 1, level.getMaxY(), Well.SIDE + Well.TALUS + 4));
		java.util.function.BiPredicate<Integer, Integer> own = (x, z) -> {
			int[] ij = well.ijAnywhere(x, z);
			return well.lifted() ? Well.talusReach(ij[0], ij[1]) >= 0
					: ij[0] >= Well.APRON_I0 - 1 && ij[0] <= Well.APRON_I1 + 1 && ij[1] >= Well.SIDE && ij[1] <= Well.SIDE + Well.APRON;
		};
		int kerbs = 0;
		int scree = 0;
		for (int i = -2; i <= Well.SIDE + 1; i++) {
			for (int j = Well.SIDE; j <= Well.SIDE + Well.TALUS + 3; j++) {
				BlockPos c = well.at(i, 0, j);
				for (int y = ROCK_TOP - CLIFF + 1; y <= sill + 1; y++) {
					BlockState b = level.getBlockState(new BlockPos(c.getX(), y, c.getZ()));
					kerbs += b.is(Blocks.STONE) && level.getBlockState(new BlockPos(c.getX(), y + 1, c.getZ())).isAir()
							&& level.getBlockState(new BlockPos(c.getX(), y - 1, c.getZ())).isSolid() && y > ROCK_TOP ? 1 : 0;
					scree += b.is(Blocks.GRAVEL) || b.is(Blocks.ANDESITE) ? 1 : 0;
				}
			}
		}
		return new Front(level, seeds, area, own, kerbs, scree, plan.version);
	}

	/**
	 * Version 3's shape in a block of rock: the hall 41 high under its ceiling, its decks at 9, 18 and 27 with a rail on
	 * every walkable edge over a fall, the floating grid (single glowstone at 17 with air all round), nothing lit in its top
	 * 9; the well chunk-aligned, its cut on a section line, the pool 3 deep under every cell of the void with the lowest
	 * gallery's floor 1 over it, the lights by depth (lit from the top, dark under the land but for the lure two thirds
	 * down), the crack its only way in from the land; a walk from the crack's sill down every flight to the pool's rim and
	 * through the corridor to the hall's floor, every step at most 1, no fall anywhere (every void edge railed).
	 */
	public static void shape3(GameTestHelper helper) {
		shape(helper, 3);
	}

	/**
	 * {@link #shape3}'s checks for {@code version}; version 4 (R3A-1, R3A-11, FIX-R3-17) with its own instead of the cut
	 * on a section line: the piece 16 to 24 over its land, its top grass over 3 or 4 of dirt right to the faces, the faces
	 * the world's section (stone with andesite, diorite and granite in it, nothing built over the land, a cave cut into
	 * one at least), the lighting fault on all four faces; in the hall, no full bookshelf a hand could reach.
	 */
	public static void shape(GameTestHelper helper, int version) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + (version - 3) * 2400, Z, false, version, false);
		ArchivePlan plan = ArchiveTestKit.plan(built);
		Well well = plan.well;
		helper.assertTrue(plan.version == version && well != null && plan.height() == ArchivePlan.HEIGHT3, "not version " + version + ": " + plan.version);
		List<String> wrong = new ArrayList<>();
		// The hall.
		for (int f = -2; f >= -plan.length + ArchivePlan.DAIS_DEPTH; f -= 7) {
			if (!level.getBlockState(plan.at(0, ArchivePlan.HEIGHT3 - 1, f)).isAir() || level.getBlockState(plan.at(0, ArchivePlan.CEILING3, f)).isAir()) {
				wrong.add("the nave's height at f " + f);
			}
		}
		for (int k = 0; k < plan.stackRows - 1; k++) {
			for (int d : new int[] {9, 18, 27}) {
				int f = plan.rowBack(k) - 1;
				BlockState deck = level.getBlockState(plan.at(8, d, f));
				if (!deck.is(Blocks.OAK_PLANKS) && !plan.inBay(8, f)) {
					wrong.add("no deck at " + d + " in aisle " + k + ": " + deck);
				}
				if (!level.getBlockState(plan.at(ArchivePlan.STACK_IN, d + 1, f)).is(Blocks.OAK_FENCE) && !plan.inBay(ArchivePlan.STACK_IN, f)) {
					wrong.add("no rail over the nave at " + d + " in aisle " + k);
				}
			}
		}
		int floating = 0;
		for (int[] light : plan.hallLights()) {
			if (light[1] != ArchivePlan.FLOAT3) {
				continue;
			}
			BlockPos p = plan.at(light[0], light[1], light[2]);
			floating++;
			if (!level.getBlockState(p).is(Blocks.GLOWSTONE)) {
				wrong.add("no floating light at " + p.toShortString());
			}
			for (Direction d : Direction.values()) {
				if (!level.getBlockState(p.relative(d)).isAir()) {
					wrong.add("a floating light held at " + p.toShortString() + " " + d);
				}
			}
		}
		helper.assertTrue(floating >= 2 * plan.gridRows(), "only " + floating + " floating lights");
		// The well.
		helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Fault.chunkAligned(BoundingBox.fromCorners(well.at(0, 0, 0), well.at(Well.SIDE - 1, 0, Well.SIDE - 1))),
				"the well is not chunk-aligned");
		if (version < 4) {
			helper.assertTrue(well.top % 16 == 0 && !level.getBlockState(well.at(0, well.top - 1, 0)).isAir() && level.getBlockState(well.at(0, well.top, 0)).isAir(),
					"the cut is not on a section line: " + well.top);
		} else {
			helper.assertTrue(well.lifted(), "version 4's well is not lifted");
			pieceOfLand(helper, level, plan, well);
		}
		int water = well.waterTop();
		for (int i = Well.VOID0; i <= Well.VOID1; i++) {
			for (int j = Well.VOID0; j <= Well.VOID1; j++) {
				for (int y = water - 2; y <= water; y++) {
					if (!level.getBlockState(well.at(i, y, j)).getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)) {
						wrong.add("no water at " + well.at(i, y, j).toShortString());
					}
				}
				if (level.getBlockState(well.at(i, water - 3, j)).isAir()) {
					wrong.add("the pool leaks at " + i + " " + j);
				}
			}
		}
		helper.assertTrue(level.getBlockState(well.at(Well.IN0, well.floorY(well.kMin), Well.IN0 + 6)).is(Blocks.OAK_PLANKS), "the lowest gallery's floor");
		for (int k = well.kMin; k <= well.kTop; k++) {
			int glow = 0;
			for (BlockPos p : well.lights(k)) {
				glow += level.getBlockState(p).is(Blocks.GLOWSTONE) ? 1 : 0;
			}
			boolean shouldGlow = well.lit(k);
			if (shouldGlow != glow > 0) {
				wrong.add("gallery " + k + (shouldGlow ? " dark" : " lit") + " (" + glow + ")");
			}
			if (k < well.kGround && k != well.kLure && glow > 0) {
				wrong.add("gallery " + k + " under the land lit");
			}
		}
		helper.assertTrue(well.kLure < well.kGround - 2 && well.kLure > well.kMin && well.kLure != 0, "the lure at " + well.kLure);
		// The crack: open from the sill to the soil; the rest of the front face closed.
		int sill = well.floorY(well.kGround);
		for (int y = sill + 1; y < well.top - Well.SOIL; y++) {
			for (int i = 0; i < Well.SIDE; i++) {
				boolean open = level.getBlockState(well.at(i, y, Well.SIDE - 1)).isAir();
				if (open != well.way(i, y)) {
					wrong.add("the front at " + i + " " + y + (open ? " open" : " closed"));
					break;
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the shape: " + wrong.subList(0, Math.min(12, wrong.size())));
		// The walk: from the crack's sill everywhere, no fall; down to the pool's rim and into the hall.
		List<String> falls = new ArrayList<>();
		// Inside only (the crack's floor through the wall, the galleries, the corridor, the hall): this walk climbs any 1-high
		// block, the apron's kerb too (world.Edges' rule for the outside counts a kerb as a stop).
		BoundingBox walkBox = well.insideBox().encapsulate(plan.hallInside()).encapsulate(BoundingBox.fromCorners(well.at(Well.WAY_I0, sill, Well.IN1),
				well.at(Well.WAY_I0 + 1, sill + 4, Well.SIDE - 1))).encapsulate(BoundingBox.fromCorners(plan.at(well.side * ArchivePlan.FACE, 1, -6),
						well.at(0, plan.floorY() + 4, well.doorJ() + 1)));
		java.util.Set<BlockPos> reached = ArchiveTestKit.walk(level, well.at(Well.WAY_I0, sill + 1, Well.SIDE - 1), walkBox, falls);
		helper.assertTrue(falls.isEmpty(), "falls: " + falls.subList(0, Math.min(8, falls.size())));
		boolean rim = false;
		boolean hall = false;
		for (BlockPos p : reached) {
			rim |= p.getY() == well.floorY(well.kMin) + 1 && well.galleryOf(p) == well.kMin;
			hall |= plan.inHallAt(p);
		}
		helper.assertTrue(rim && hall, "the walk reached the pool's rim " + rim + ", the hall " + hall + " (" + reached.size() + " floors)");
		helper.assertTrue(Live.settleLight(level, built.box()), "the light did not settle");
		int litHigh = 0;
		for (int u = ArchivePlan.HEIGHT3 - 8; u <= ArchivePlan.HEIGHT3; u++) {
			for (int f = -1; f >= -plan.length; f--) {
				for (int r = -ArchivePlan.HALF; r <= ArchivePlan.HALF; r += 3) {
					litHigh += level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, plan.at(r, u, f)) > 1 ? 1 : 0;
				}
			}
		}
		helper.assertTrue(litHigh == 0, litHigh + " cells lit in the hall's top 9");
		if (version >= 4) {
			// R3A-11: full where nobody could reach, empty where a hand could.
			int reachable = 0;
			int above = 0;
			for (int f = 0; f >= -plan.length - 1; f--) {
				for (int r = -ArchivePlan.FACE; r <= ArchivePlan.FACE; r++) {
					for (int u = 1; u <= ArchivePlan.STACK3; u++) {
						if (!level.getBlockState(plan.at(r, u, f)).is(Blocks.BOOKSHELF)) {
							continue;
						}
						if (u <= ArchivePlan.REACH3 && f > -plan.length - 1) {
							reachable++;
						} else if (u > ArchivePlan.REACH3) {
							above++;
						}
					}
				}
			}
			helper.assertTrue(reachable == 0 && above > 1000, reachable + " full shelves a hand could reach, " + above + " above them");
		}
		helper.succeed();
	}

	/** Version 4's piece of land, read from the built blocks (the land is rock at {@value #ROCK_TOP} all round). */
	private static void pieceOfLand(GameTestHelper helper, ServerLevel level, ArchivePlan plan, Well well) {
		List<String> wrong = new ArrayList<>();
		java.util.Map<Block, Integer> faces = new java.util.HashMap<>();
		int caves = 0;
		int lights = 0;
		for (int i = 0; i < Well.SIDE; i++) {
			for (int j = 0; j < Well.SIDE; j++) {
				if (Well.layer(i, j) != 0) {
					continue;
				}
				BlockPos c = well.at(i, 0, j);
				int s = well.top - 1;
				while (!level.getBlockState(new BlockPos(c.getX(), s + 1, c.getZ())).isAir()
						&& !level.getBlockState(new BlockPos(c.getX(), s + 1, c.getZ())).canBeReplaced()) {
					s++;
				}
				int over = s - ROCK_TOP;
				if (over < Well.LIFT || over > Well.LIFT_MAX + 2) {
					wrong.add("the top " + over + " over the land at " + i + " " + j);
				}
				BlockState surface = level.getBlockState(new BlockPos(c.getX(), s, c.getZ()));
				BlockState soil = level.getBlockState(new BlockPos(c.getX(), s - 3, c.getZ()));
				if (!(surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.SAND) || surface.is(Blocks.RED_SAND)) || soil != Well.soil(surface)) {
					wrong.add("not the land's top at the face at " + i + " " + j + ": " + surface.getBlock() + " over " + soil.getBlock());
				}
				for (int y = ROCK_TOP + 1; y < s; y++) {
					BlockState b = level.getBlockState(new BlockPos(c.getX(), y, c.getZ()));
					if (BUILT.contains(b.getBlock())) {
						wrong.add("built " + b.getBlock() + " in the face at " + i + " " + y + " " + j);
					}
					faces.merge(b.getBlock(), 1, Integer::sum);
					if (b.isAir() && !well.way(i, y)) {
						caves++;
						// The cave's back: never the archive's own.
						int bi = Math.clamp(i + (i == 0 ? 2 : i == Well.SIDE - 1 ? -2 : 0), 0, Well.SIDE - 1);
						int bj = Math.clamp(j + (j == 0 ? 2 : j == Well.SIDE - 1 ? -2 : 0), 0, Well.SIDE - 1);
						BlockState back = level.getBlockState(well.at(bi, y, bj));
						if (BUILT.contains(back.getBlock())) {
							wrong.add("a cave shows " + back.getBlock() + " at " + i + " " + y + " " + j);
						}
					}
				}
			}
		}
		for (int i = -1; i <= Well.SIDE; i++) {
			for (int j = -1; j <= Well.SIDE; j++) {
				if (i >= 0 && i < Well.SIDE && j >= 0 && j < Well.SIDE) {
					continue;
				}
				BlockPos c = well.at(i, 0, j);
				for (int y = ROCK_TOP + 1; y <= well.crest; y++) {
					lights += level.getBlockState(new BlockPos(c.getX(), y, c.getZ())).is(Blocks.LIGHT) ? 1 : 0;
				}
			}
		}
		int kinds = (faces.getOrDefault(Blocks.ANDESITE, 0) > 0 ? 1 : 0) + (faces.getOrDefault(Blocks.DIORITE, 0) > 0 ? 1 : 0)
				+ (faces.getOrDefault(Blocks.GRANITE, 0) > 0 ? 1 : 0);
		helper.assertTrue(wrong.isEmpty(), "the piece of land: " + wrong.subList(0, Math.min(10, wrong.size())));
		helper.assertTrue(kinds >= 2 && faces.getOrDefault(Blocks.STONE, 0) > faces.size(), "the faces are not the world's section: " + faces);
		helper.assertTrue(caves > 0, "no cave in the faces");
		helper.assertTrue(lights > 40, "the faces' lighting fault: " + lights);
	}

	/**
	 * R12-2: a start tied to version 2 builds version 2 (17 high, no well) and one not tied builds version 3; version 3's kept
	 * numbers (the tunnels, the well's cut, front land and the world's lowest block) give back the same well.
	 */
	public static void versions(GameTestHelper helper) {
		Start start = new Start(new BlockPos(1000, FLOOR, 2000), Direction.EAST, ArchivePlan.variant3(true, 0.08, false, 0, -64), 9L);
		ArchivePlan old = ArchivePlan.of(start.withVersion(2), new int[0]);
		ArchivePlan now = ArchivePlan.of(start, new int[0]);
		helper.assertTrue(old.version == 2 && old.well == null && old.height() == ArchivePlan.HEIGHT && old.cols == 7, "version 2: " + old.version);
		helper.assertTrue(now.version == ArchiveShape.VERSION && now.well != null && now.height() == ArchivePlan.HEIGHT3, "version 3: " + now.version);
		ArchivePlan kept = ArchivePlan.of(start, now.lengths());
		helper.assertTrue(kept.well != null && kept.well.top == now.well.top && kept.well.land == now.well.land && kept.well.lowest == -64
				&& kept.box().equals(now.box()), "the kept numbers give another well");
		ArchivePlan small = ArchivePlan.of(new Start(start.origin(), start.facing(), ArchivePlan.variant3(true, 0.08, true, 0, -64), 9L), new int[0]);
		helper.assertTrue(small.length == ArchivePlan.LONG && small.cols == 5 && small.rows == 3, "the small annex: " + small.cols + " x " + small.rows);
		helper.succeed();
	}

	/**
	 * Version 3's siting: the floor 51 under the land (its ceiling's top 8 under); refused when the land over any part dips
	 * (a valley across the hall, a cliff beside it: the floor would go under -24); refused when the well's column is in the
	 * deep dark at its pool; in a world from 0 to 255 the well's pool keeps the kit's bottom.
	 */
	public static void siting3(GameTestHelper helper) {
		ArchiveSiting siting = new ArchiveSiting();
		ChunkPos chunk = new ChunkPos(60, 0);
		Optional<Start> flat = siting.find(ArchiveTestKit.query(helper, chunk, 70, net.minecraft.world.level.biome.Biomes.PLAINS));
		helper.assertTrue(flat.isPresent() && flat.get().origin().getY() <= 70 - ArchiveSiting.UNDER_GROUND3, "flat: " + flat);
		// A valley 40 deep, 12 wide, across the middle of where the hall goes: the floor follows it down or refuses.
		Start s = flat.get();
		ArchivePlan p = ArchivePlan.of(s, new int[0]);
		BlockPos mid = p.at(0, 0, -p.length / 2);
		Holder<Biome> plains = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
		Terrain valley = new Dipped(70, 30, mid, 6, plains, null);
		Optional<Start> over = siting.find(withTerrain(helper, chunk, valley));
		helper.assertTrue(over.isEmpty() || over.get().origin().getY() <= 30 - ArchiveSiting.UNDER_GROUND3, "over a valley: " + over);
		Terrain deepDark = new Dipped(70, 70, mid, 0, plains,
				helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.DEEP_DARK));
		helper.assertTrue(siting.find(withTerrain(helper, chunk, deepDark)).isEmpty(), "the well in the deep dark");
		Siting.Query low = new Siting.Query(chunk, new ArchiveTestKit.Flat(110, plains), ArchiveTestKit.query(helper, chunk, 110,
				net.minecraft.world.level.biome.Biomes.PLAINS).snapshot(), 42L, RandomSource.create(1), 0, 255);
		Optional<Start> shallow = siting.find(low);
		if (shallow.isPresent()) {
			ArchivePlan sp = ArchivePlan.of(shallow.get(), new ArchiveTestKit.Flat(110, plains));
			helper.assertTrue(sp.well != null && sp.well.lowest == 0 && sp.box().minY() >= Heights.BOTTOM, "a 0 to 255 world: " + sp.box());
		}
		helper.succeed();
	}

	/** A flat land {@code ground} high with a square dip to {@code low} round {@code at} ({@code half} out), biome {@code deep} under 0 if set. */
	record Dipped(int ground, int low, BlockPos at, int half, Holder<Biome> biome, @org.jspecify.annotations.Nullable Holder<Biome> deep) implements Terrain {
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
			return Math.abs(x - at.getX()) <= half && Math.abs(z - at.getZ()) <= half ? low : ground;
		}

		@Override
		public int surface(int x, int z) {
			return ground(x, z);
		}

		@Override
		public Holder<Biome> biome(int x, int y, int z) {
			return deep != null && y < 0 ? deep : biome;
		}

		@Override
		public BlockState block(int x, int y, int z) {
			return y <= ground(x, z) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}
	}

	static Siting.Query withTerrain(GameTestHelper helper, ChunkPos chunk, Terrain terrain) {
		Siting.Query q = ArchiveTestKit.query(helper, chunk, 70, net.minecraft.world.level.biome.Biomes.PLAINS);
		return new Siting.Query(chunk, terrain, q.snapshot(), q.worldSeed(), RandomSource.create(chunk.pack()), q.minY(), q.maxY());
	}

	/**
	 * Version 3's worldgen equals the gallery's: a start made by the structure, saved and loaded, placed chunk by chunk as
	 * worldgen does, and the same start built by {@link Live#place} 640 east: block for block and block entity for block
	 * entity the same (the six's written records included), the recorder knew it once.
	 */
	public static void worldgenEqualsLive3(GameTestHelper helper) {
		worldgenEqualsLive(helper, 3);
	}

	/** {@link #worldgenEqualsLive3} for {@code version}. */
	public static void worldgenEqualsLive(GameTestHelper helper, int version) {
		ServerLevel level = helper.getLevel();
		Snapshot before = Snapshot.current();
		int x0 = X + 4400 + (version - 3) * 3200;
		int z0 = Z;
		int shift = 640;
		Start start = new Start(new BlockPos(x0, FLOOR, z0), Direction.NORTH, ArchivePlan.variant3(false, 0.08, false, 0, level.getMinY()),
				ArchiveTestKit.SEED).withVersion(version);
		ArchivePlan probe = ArchivePlan.of(start, Terrains.noise(level));
		ArchiveTestKit.load(level, probe.box().inflatedBy(2));
		ArchiveTestKit.load(level, probe.box().inflatedBy(2).moved(shift, 0, 0));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/s17")));
		Sc2Data data = new Sc2Data();
		try {
			Recorder.drain(new Sc2Data());
			StructureStart made = Live.startOf(level, structure, start);
			helper.assertTrue(made.isValid() && made.getPieces().getFirst() instanceof Sc2Piece, "worldgen made no start");
			StructureStart loaded = Live.reload(level, made);
			helper.assertTrue(loaded != null && loaded.isValid(), "the saved start did not load");
			Live.placeStart(level, loaded);
			helper.assertTrue(Recorder.drain(data) == 1 && data.places().size() == 1 && data.places().getFirst().place().kind() == Remains.Kind.ARCHIVE,
					"the recorder knew " + data.places().size());
			Live.Result live = Live.place(level, S17.KIND, start.moved(shift, 0, 0));
			BoundingBox box = made.getBoundingBox();
			helper.assertTrue(live.box().equals(box.moved(shift, 0, 0)), "the boxes: " + box + " / " + live.box());
			List<String> differ = new ArrayList<>();
			int shelves = 0;
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState a = level.getBlockState(pos);
				BlockState b = level.getBlockState(pos.offset(shift, 0, 0));
				shelves += a.is(Blocks.BOOKSHELF) || a.is(Blocks.CHISELED_BOOKSHELF) ? 1 : 0;
				if (a != b && differ.size() < 8) {
					differ.add(pos.toShortString() + " " + a + " / " + b);
				}
				if (a.hasBlockEntity()) {
					BlockEntity ea = level.getBlockEntity(pos);
					BlockEntity eb = level.getBlockEntity(pos.offset(shift, 0, 0));
					if (ea != null && (eb == null || !ea.saveCustomOnly(level.registryAccess()).equals(eb.saveCustomOnly(level.registryAccess())))
							&& differ.size() < 8) {
						differ.add(pos.toShortString() + " block entity");
					}
				}
			}
			helper.assertTrue(differ.isEmpty(), "worldgen and Live differ: " + differ);
			helper.assertTrue(shelves > 15000, "only " + shelves + " bookshelves");
			ArchivePlan builtPlan = ArchivePlan.of(start, ArchiveShape.lengths(data.places().getFirst().extra()));
			helper.assertTrue(builtPlan.well != null && builtPlan.box().equals(probe.box()), "the kept numbers rebuild another plan");
			helper.assertTrue(Records.titles(level, builtPlan).containsAll(ArchivePlan.LIST_NAMES), "worldgen's wall: " + Records.titles(level, builtPlan));
			for (BlockPos shelf : builtPlan.listShelves()) {
				net.minecraft.world.item.ItemStack book = ((net.minecraft.world.Container) level.getBlockEntity(shelf)).getItem(1);
				helper.assertTrue(!com.forzacode.a1016_02.world.sc2.s17.books.Pages.text(book).stream().allMatch(String::isEmpty),
						"a record of the six is blank at " + shelf.toShortString());
			}
		} finally {
			Recorder.drain(new Sc2Data());
			Snapshot.install(before);
		}
		helper.succeed();
	}
}
