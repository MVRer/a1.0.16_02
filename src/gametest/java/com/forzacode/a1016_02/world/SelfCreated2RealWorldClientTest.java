package com.forzacode.a1016_02.world;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.debug.client.gallery.ClientTestDriver;
import com.forzacode.a1016_02.debug.client.gallery.GalleryScript;
import com.forzacode.a1016_02.debug.gallery.GalleryRun;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Overlay;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Piece;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.s19.LineFeature;
import com.forzacode.a1016_02.world.sc2.s19.LinePlan;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-H's look at the places as real worldgen makes them (dev only; never part of {@code build}): a world of the default
 * preset with the seed of {@code -Da1016_02.sc2real} ({@code true}: SC2-5's peaks seed), the gallery's stage and sky,
 * then for every kind a structure JSON names the first start the kit makes round the survey's centres (two for the
 * hollow), and a stretch of the line where it crosses the highest ground: their chunks generated for real (noise, carvers,
 * every feature of every neighbour, in the order the chunk system chooses), then each place filled again into an
 * {@link Overlay} over the generated world: whatever that would change is what later worldgen wrote into it (printed,
 * by block). Then shots from the standard cameras into {@code build/gallery/<kind>h/}. Does nothing without the property
 * (the gallery's own run is untouched). Run it as {@code ./gradlew gallery -Pgallery=none} with the property set on the
 * client run (a Gradle init script).
 */
public class SelfCreated2RealWorldClientTest implements FabricClientGameTest {
	public static final String PROPERTY = "a1016_02.sc2real";

	/** One place to make and look at. */
	record Site(String kind, @org.jspecify.annotations.Nullable Holder<Structure> structure, ChunkPos chunk, BoundingBox box, BlockPos entrance, Direction facing,
			BlockPos anchor) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String value = System.getProperty(PROPERTY, "");
		if (value.isEmpty()) {
			return;
		}
		long seed = value.equals("true") ? SelfCreated2HeightsTests.REAL_SEED : Long.parseLong(value);
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
			state.setAllowCommands(true);
			state.setSeed(Long.toString(seed));
			state.setGenerateStructures(true);
			state.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL);
			state.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.CREATIVE);
		}).create()) {
			ClientTestDriver driver = new ClientTestDriver(context, world);
			driver.waitForChunks(GalleryScript.CHUNK_TIMEOUT);
			driver.client(client -> GalleryScript.options(client, GalleryRun.renderDistance()));
			driver.server(server -> {
				GalleryRun.prepare(server);
				Snapshot.refresh(server);
				return null;
			});
			List<Site> sites = driver.server(server -> sites(server.overworld()));
			Sc2RealNoise.log("real world " + seed + ": " + sites.size() + " sites " + sites.stream().map(s -> s.kind() + " " + s.chunk()).toList());
			Map<String, Integer> shotsOf = new TreeMap<>();
			for (Site site : sites) {
				String report = driver.server(server -> generate(server.overworld(), site));
				Sc2RealNoise.log("real " + site.kind() + " at " + site.chunk() + " y " + site.box().minY() + ".." + site.box().maxY() + ": " + report);
				int n = shotsOf.merge(site.kind(), 1, Integer::sum);
				shoot(driver, site, site.kind() + "h" + (n > 1 ? n : ""));
				driver.server(server -> {
					forceChunks(server.overworld(), site.box(), false);
					return null;
				});
			}
		}
	}

	// --- the sites ---

	/**
	 * Of the first starts of each kind round the centres (six at most), the most extreme site: the most relief under its
	 * box, the more so with water in it (a coast); two of the hollow.
	 */
	static List<Site> sites(ServerLevel level) {
		Sc2RealNoise.World world = Sc2RealNoise.of(level);
		Snapshot snapshot = Snapshot.current();
		List<Site> out = new ArrayList<>();
		for (Map.Entry<String, Holder<Structure>> e : Sc2RealNoise.structures(level).entrySet()) {
			Sc2Kind kind = Sc2Kind.get(e.getKey());
			StructurePlacement placement = Sc2RealNoise.placement(level, e.getValue());
			if (kind == null || placement == null || SelfCreated2HeightsTests.NO_NATURAL_START.contains(e.getKey()) || snapshot == null) {
				continue;
			}
			List<Site> made = new ArrayList<>();
			Set<ChunkPos> seen = new HashSet<>();
			search:
			for (int[] c : SelfCreated2HeightsTests.CENTRES) {
				for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, 24)) {
					if (!seen.add(chunk)) {
						continue;
					}
					Sc2Structure.Sited sited = SelfCreated2HeightsTests.sited(world, e.getValue(), kind, chunk, snapshot);
					if (sited.made()) {
						Shape shape = sited.shape();
						made.add(new Site(e.getKey(), e.getValue(), chunk, shape.box(), shape.entrance(), shape.entranceFacing(), shape.anchor()));
						if (made.size() >= 6) {
							break search;
						}
					}
				}
			}
			made.sort((a, b) -> Integer.compare(extremity(world.terrain(), b.box()), extremity(world.terrain(), a.box())));
			out.addAll(made.subList(0, Math.min(e.getKey().equals("s18") ? 2 : 1, made.size())));
		}
		// The line: the stretch of a segment over the highest ground (every 16 along it), 96 either way.
		for (LinePlan.Segment segment : LineFeature.segments(snapshot, level.getMinY(), level.getMaxY())) {
			int best = segment.t0();
			int bestGround = Integer.MIN_VALUE;
			for (int t = segment.t0(); t <= segment.t1(); t += 16) {
				int g = world.terrain().ground(segment.x(t), segment.z(t));
				if (g > bestGround) {
					bestGround = g;
					best = t;
				}
			}
			int lo = Math.max(segment.t0(), best - 96);
			int hi = Math.min(segment.t1(), best + 96);
			BoundingBox box = BoundingBox.fromCorners(new BlockPos(segment.x(lo), segment.bottom(), segment.z(lo)),
					new BlockPos(segment.x(hi), segment.top(), segment.z(hi)));
			BlockPos at = new BlockPos(segment.x(best), bestGround + 1, segment.z(best));
			Direction side = segment.arm().alongX() ? Direction.SOUTH : Direction.EAST;
			out.add(new Site("s19", null, ChunkPos.containing(at), box, at, side, at));
			break;
		}
		return out;
	}

	/** How extreme a site is: the relief of the ground under its box (9 x 9), and 24 more with water in it. */
	static int extremity(Terrain terrain, BoundingBox box) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		boolean wet = false;
		for (int i = 0; i <= 8; i++) {
			for (int k = 0; k <= 8; k++) {
				int x = box.minX() + (box.maxX() - box.minX()) * i / 8;
				int z = box.minZ() + (box.maxZ() - box.minZ()) * k / 8;
				int g = terrain.ground(x, z);
				lo = Math.min(lo, g);
				hi = Math.max(hi, g);
				wet |= terrain.surface(x, z) > g;
			}
		}
		return hi - lo + (wet ? 24 : 0);
	}

	private static void forceChunks(ServerLevel level, BoundingBox box, boolean on) {
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, on);
			}
		}
	}

	// --- generation and the look at what later worldgen did ---

	/** Generates the site's chunks for real and fills its place again into an overlay: what would change, by block. */
	static String generate(ServerLevel level, Site site) {
		long t0 = System.nanoTime();
		forceChunks(level, site.box(), true);
		for (int cx = (site.box().minX() >> 4) - 1; cx <= (site.box().maxX() >> 4) + 1; cx++) {
			for (int cz = (site.box().minZ() >> 4) - 1; cz <= (site.box().maxZ() >> 4) + 1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		String took = String.format(Locale.ROOT, "generated in %.1f s", (System.nanoTime() - t0) / 1e9);
		if (site.structure() == null) {
			return took;
		}
		StructureStart start = level.getChunk(site.chunk().x(), site.chunk().z()).getStartForStructure(site.structure().value());
		if (start == null || !start.isValid()) {
			return took + "; NO START (land made before the stage, or another kind took the slot)";
		}
		Terrain terrain = Terrains.noise(level);
		Shape shape = ((Sc2Piece) start.getPieces().getFirst()).shape(terrain);
		Overlay overlay = new Overlay(Overlay.Base.of(level));
		overlay.fill(shape, terrain, level.registryAccess(), level.getMinY(), level.getMaxY(), Optional.empty());
		return took + "; " + damage(level, shape, overlay);
	}

	/** What filling the place again would change: by block now and block meant, inside, near the mouth, or elsewhere. */
	static String damage(ServerLevel level, Shape shape, Overlay overlay) {
		Interior inside = shape.interior();
		BlockPos mouth = shape.entrance();
		Map<String, Integer> pairs = new LinkedHashMap<>();
		int changed = 0;
		int in = 0;
		int atMouth = 0;
		List<String> where = new ArrayList<>();
		Set<Long> chunks = new HashSet<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (Long2ObjectMap.Entry<BlockState> e : overlay.cells().long2ObjectEntrySet()) {
			long pos = e.getLongKey();
			cursor.set(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
			BlockState now = level.getBlockState(cursor);
			BlockState meant = e.getValue();
			if (now.getBlock() == meant.getBlock()) {
				continue;
			}
			changed++;
			chunks.add(ChunkPos.pack(cursor.getX() >> 4, cursor.getZ() >> 4));
			boolean isIn = inside.contains(cursor);
			boolean isMouth = cursor.distManhattan(mouth) <= 12;
			in += isIn ? 1 : 0;
			atMouth += isMouth ? 1 : 0;
			pairs.merge(name(now) + ">" + name(meant), 1, Integer::sum);
			if (where.size() < 8 && (isIn || isMouth)) {
				where.add((isIn ? "in " : "mouth ") + cursor.toShortString() + " " + name(now) + ">" + name(meant));
			}
		}
		List<Map.Entry<String, Integer>> top = new ArrayList<>(pairs.entrySet());
		top.sort((a, b) -> b.getValue() - a.getValue());
		return changed + " cells changed after it was built, in " + chunks.size() + " chunks (" + in + " inside, " + atMouth + " near the mouth): "
				+ top.subList(0, Math.min(10, top.size())) + (where.isEmpty() ? "" : " " + where);
	}

	private static String name(BlockState state) {
		String id = state.getBlock().getDescriptionId();
		return id.substring(id.lastIndexOf('.') + 1);
	}

	// --- shots ---

	/**
	 * Cameras over real land: the standard near, mouth and aerial; the far and mid views lifted over the trees; four
	 * views from the diagonals, 110 out and over the land there (the sides a cut-off or a floating part would show on);
	 * and one inside over the anchor toward the way in (night vision). The line (19) is no building (R11-20): the cameras
	 * look at its panels' band, 56 over the land at its middle, and it has no mouth and no inside to look from.
	 */
	static List<Gallery.View> cameras(ServerLevel level, Site site) {
		List<Gallery.View> all = new ArrayList<>();
		BoundingBox box = site.box();
		BlockPos c = box.getCenter();
		int focusGround = Gallery.ground(level, c.getX(), c.getZ());
		boolean line = site.kind().equals("s19");
		Vec3 focus = new Vec3(c.getX() + 0.5, line ? focusGround + 56.0 : Math.max(focusGround + 1.0, Math.min(box.maxY() - 8, focusGround + 24.0)),
				c.getZ() + 0.5);
		for (Gallery.View v : Gallery.standardViews(level, box, site.entrance(), site.facing())) {
			if ((line ? Set.of("near", "aerial") : Set.of("near", "mouth", "aerial")).contains(v.name())) {
				all.add(v);
			}
		}
		Vec3 centre = Vec3.atBottomCenterOf(c);
		Vec3 far = Gallery.out(centre, site.facing(), 0, 200);
		all.add(Gallery.View.looking("far_day", lifted(level, far, 30), focus, Gallery.NOON, Gallery.Sky.CLEAR, false).noted("200 out, over the trees"));
		Vec3 mid = Gallery.out(centre, site.facing(), 30, 130);
		all.add(Gallery.View.looking("mid", lifted(level, mid, 20), focus, Gallery.NOON, Gallery.Sky.CLEAR, false).noted("130 out, 30 aside, over the trees"));
		int k = 1;
		for (double degrees : new double[] {45, 135, 225, 315}) {
			Vec3 side = Gallery.out(centre, site.facing(), degrees, 110);
			all.add(Gallery.View.looking("side_" + k++, lifted(level, side, 16), focus, Gallery.NOON, Gallery.Sky.CLEAR, false)
					.noted("110 out at " + (int) degrees + " degrees, over the land"));
		}
		if (line) {
			return all;
		}
		Vec3 eye = Vec3.atBottomCenterOf(site.anchor()).add(0, 2.6, 0);
		all.add(Gallery.View.looking("survey_1", eye, Vec3.atBottomCenterOf(site.entrance()).add(0, 1.5, 0), Gallery.NOON, Gallery.Sky.CLEAR, true)
				.noted("inside over the anchor toward the way in, night vision"));
		return all;
	}

	/** A camera this far over the highest land within 6 of the spot. */
	private static Vec3 lifted(ServerLevel level, Vec3 spot, double over) {
		int top = Integer.MIN_VALUE;
		for (int dx = -6; dx <= 6; dx += 3) {
			for (int dz = -6; dz <= 6; dz += 3) {
				top = Math.max(top, Gallery.ground(level, (int) Math.floor(spot.x) + dx, (int) Math.floor(spot.z) + dz));
			}
		}
		return new Vec3(spot.x, top + over, spot.z);
	}

	private static void shoot(ClientTestDriver driver, Site site, String id) {
		Path dir = GalleryRun.freshDir(id);
		List<Gallery.View> views = driver.server(server -> cameras(server.overworld(), site));
		List<GalleryRun.Entry> entries = new ArrayList<>();
		for (Gallery.View view : views) {
			driver.server(server -> {
				GalleryRun.stage(GalleryRun.camera(server), view);
				return null;
			});
			driver.waitTicks(2);
			boolean loaded = driver.waitForChunks(GalleryScript.CHUNK_TIMEOUT);
			driver.waitTicks(GalleryRun.SETTLE_TICKS);
			loaded &= driver.waitForChunks(200);
			Path png = driver.screenshot(dir, view.name());
			entries.add(GalleryRun.entry(id, GalleryRun.relative(png), view, GalleryRun.check(png) + (loaded ? "" : " CHUNKS?")));
		}
		GalleryRun.writeIndex(entries, "real world, " + site.kind() + " at " + site.chunk());
	}
}
