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
	/**
	 * FIX-R3-17: {@code -Da1016_02.sc2real.only=s17,...} builds and shoots only these kinds' sites (and their /locate
	 * sites), without the line's crumbs: one window for one kind's fix instead of the whole round.
	 */
	public static final String ONLY = "a1016_02.sc2real.only";

	/** True if this run shoots {@code kind} ({@link #ONLY} empty: every kind). */
	static boolean shoots(String kind) {
		String only = System.getProperty(ONLY, "");
		return only.isEmpty() || java.util.Arrays.asList(only.split(",")).contains(kind);
	}

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
			List<Site> all = driver.server(server -> sites(server.overworld()));
			List<Site> sites = all.stream().filter(s -> shoots(s.kind())).toList();
			// Round 3 (D-112): /locate for every kind from the world spawn, against what the kit decided.
			Sc2RealNoise.log("real locate: " + driver.server(server -> locate(server.overworld())));
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
			// R3-3: the hollow and the floor /locate finds from the spawn (the kit's own decision, its spacing and avoid
			// list kept), built and shot as <kind>h_loc (the picked sites above may get no start).
			// FIX-R3-17: the archive's too (R3A-1: its sign judged from two real sites).
			for (String id : List.of("s17", "s18", "s20")) {
				if (!shoots(id)) {
					continue;
				}
				Site site = driver.server(server -> located(server.overworld(), id));
				if (site == null) {
					Sc2RealNoise.log("real " + id + " located: none");
					continue;
				}
				String report = driver.server(server -> generate(server.overworld(), site));
				Sc2RealNoise.log("real " + id + " located at " + site.chunk() + ": " + report);
				shoot(driver, site, id + "h_loc");
				if (id.equals("s20")) {
					// R3A-16 (FIX-R3-B): two in-game minutes on (a player there, not a spectator, so its chunks take random
					// ticks), the snow worldgen's freeze laid on the floor melted under its light.
					List<Gallery.View> late = driver.server(server -> lateViews(server.overworld(), site));
					driver.server(server -> {
						net.minecraft.server.level.ServerPlayer camera = GalleryRun.camera(server);
						GalleryRun.stage(camera, late.getFirst());
						camera.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
						return null;
					});
					driver.waitTicks(2400);
					shootViews(driver, id + "h_loc_late", late, "real world, " + site.kind() + " at " + site.chunk() + ", two in-game minutes after it was made");
				}
				driver.server(server -> {
					forceChunks(server.overworld(), site.box(), false);
					return null;
				});
			}
			// Round 3 (R3-C): each kind's first line of crumbs, built by real worldgen (FIX-R3-C: "s32" in the filter runs them
			// alone, from every kind's site, none of the sites built or shot but the kinds it names).
			if (System.getProperty(ONLY, "").isEmpty() || shoots("s32")) {
				SelfCreated3CrumbRealShots.shoot(driver, all);
			}
		}
	}

	/** The start /locate finds for this kind from the world spawn, as a site (the kit's decision), or null. */
	static @org.jspecify.annotations.Nullable Site located(ServerLevel level, String id) {
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, com.forzacode.a1016_02.A1016_02.id("world/" + id)));
		com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
				net.minecraft.core.HolderSet.direct(holder), level.getRespawnData().pos(), 100, false);
		if (found == null) {
			return null;
		}
		com.forzacode.a1016_02.world.sc2.kit.Decided.Decision d = com.forzacode.a1016_02.world.sc2.kit.Decided.get(id, ChunkPos.containing(found.getFirst()));
		if (d == null || !d.made() || d.box() == null) {
			return null;
		}
		return new Site(id, holder, d.chunk(), d.box(), d.entrance(), d.facing(), d.anchor());
	}

	// --- /locate (round 3) ---

	/** /locate's own search for each kind from the world spawn (radius 100 chunks), and whether it is the kit's nearest decision. */
	static String locate(ServerLevel level) {
		BlockPos spawn = level.getRespawnData().pos();
		List<String> out = new ArrayList<>();
		for (String id : List.of("s16", "s16d", "s17", "s18", "s19", "s20")) {
			Holder<Structure> holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(
					net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, com.forzacode.a1016_02.A1016_02.id("world/" + id)));
			long t = System.nanoTime();
			com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
					net.minecraft.core.HolderSet.direct(holder), spawn, 100, false);
			long ms = (System.nanoTime() - t) / 1_000_000;
			if (found == null) {
				out.add(id + " none (" + ms + " ms)");
				continue;
			}
			ChunkPos at = ChunkPos.containing(found.getFirst());
			String same;
			if (id.equals("s19")) {
				same = com.forzacode.a1016_02.world.sc2.kit.AxesPlacement.marked("s19", Snapshot.current()).contains(at) ? "a mark" : "not a mark";
			} else {
				com.forzacode.a1016_02.world.sc2.kit.Decided.Decision d = com.forzacode.a1016_02.world.sc2.kit.Decided.get(id, at);
				same = d != null && d.made() ? "decided made" : "not decided made";
			}
			out.add(String.format(Locale.ROOT, "%s at %d %d, %.0f out (%d ms, %s)", id, found.getFirst().getX(), found.getFirst().getZ(),
					Math.sqrt(found.getFirst().distSqr(new BlockPos(spawn.getX(), 0, spawn.getZ()))), ms, same));
		}
		return String.join("; ", out);
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
			out.addAll(made.subList(0, Math.min(e.getKey().equals("s18") || e.getKey().equals("s17") ? 2 : 1, made.size())));
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
		// Round 3 (D-110): the error sign's views from its sign point (the entrance until a kind says otherwise).
		com.forzacode.a1016_02.world.sc2.kit.Decided.Decision decided = com.forzacode.a1016_02.world.sc2.kit.Decided.get(site.kind(), site.chunk());
		BlockPos sign = decided != null && decided.made() ? decided.sign() : site.entrance();
		List<Gallery.View> signs = Gallery.signViews(level, sign, site.facing());
		if (Set.of("s18", "s19", "s20").contains(site.kind())) {
			// FIX-R3-B (R3A-2d): each sign camera raised, or slid along its bearing, to the first spot with a clear sight of
			// the sign point (leaves and logs included); the offset is in its note.
			// The hollow's mouth sits in a cut under its lintel: its cameras aim over the foot in front of it (the lit grey
			// square, its night sign), 4 out and 6 up, which a camera can see over the land round the cut.
			Vec3 target = site.kind().equals("s18")
					? Vec3.atBottomCenterOf(sign).add(site.facing().getStepX() * 4.0, 6.0, site.facing().getStepZ() * 4.0)
					: Vec3.atBottomCenterOf(sign).add(0, 2.0, 0);
			List<Gallery.View> cleared = new ArrayList<>();
			for (Gallery.View v : signs) {
				Vec3 eye = com.forzacode.a1016_02.world.sc2.s19.LineGallery.clearEye(level, v.eye(), target, 48);
				double slid = Math.hypot(eye.x - v.eye().x, eye.z - v.eye().z);
				cleared.add(Gallery.View.looking(v.name(), eye, target, v.dayTime(), v.sky(), v.survey()).noted(v.note() + String.format(java.util.Locale.ROOT,
						"; raised %.0f, slid %.0f for a clear sight%s", eye.y - v.eye().y, slid,
						com.forzacode.a1016_02.world.sc2.s19.LineGallery.clear(level, eye, target) ? "" : " (none clear: raised the most)")));
			}
			signs = cleared;
		}
		all.addAll(signs);
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

	/** R3A-16: a floor two in-game minutes after it was made: from on it at midnight, and from the air at noon. */
	static List<Gallery.View> lateViews(ServerLevel level, Site site) {
		List<Gallery.View> out = new ArrayList<>();
		for (Gallery.View v : Gallery.standardViews(level, site.box(), site.entrance(), site.facing())) {
			if (v.name().equals("near")) {
				out.addFirst(v.named("near_2min").at(Gallery.NIGHT, Gallery.Sky.CLEAR).noted("two in-game minutes after it was made, midnight"));
			} else if (v.name().equals("aerial")) {
				out.add(v.named("aerial_2min").noted("two in-game minutes after it was made, from the air at noon"));
			}
		}
		return out;
	}

	private static void shoot(ClientTestDriver driver, Site site, String id) {
		List<Gallery.View> views = driver.server(server -> cameras(server.overworld(), site));
		shootViews(driver, id, views, "real world, " + site.kind() + " at " + site.chunk());
	}

	private static void shootViews(ClientTestDriver driver, String id, List<Gallery.View> views, String title) {
		Path dir = GalleryRun.freshDir(id);
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
		GalleryRun.writeIndex(entries, title);
	}
}
