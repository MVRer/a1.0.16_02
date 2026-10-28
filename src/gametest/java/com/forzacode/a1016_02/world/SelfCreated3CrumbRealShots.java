package com.forzacode.a1016_02.world;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.debug.client.gallery.ClientTestDriver;
import com.forzacode.a1016_02.debug.client.gallery.GalleryScript;
import com.forzacode.a1016_02.debug.gallery.GalleryRun;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Decided;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.s32.CrumbData;
import com.forzacode.a1016_02.world.sc2.s32.CrumbData.Crumb;
import com.forzacode.a1016_02.world.sc2.s32.CrumbIndex;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Line;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Tier;
import com.forzacode.a1016_02.world.sc2.s32.S32;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * R3-C's look at the crumbs as real worldgen makes them (dev only, part of {@link SelfCreated2RealWorldClientTest}'s run
 * with {@code -Da1016_02.sc2real}): for each kind's first real site, its line A's chunks generated for real (the feature
 * builds its crumbs in each as it decorates), then shots beside its far, mid and near tiers by day and night and one
 * from over its middle, into {@code build/gallery/32_<kind>r/}.
 */
final class SelfCreated3CrumbRealShots {
	private SelfCreated3CrumbRealShots() {
	}

	static void shoot(ClientTestDriver driver, List<SelfCreated2RealWorldClientTest.Site> sites) {
		Set<String> done = new HashSet<>();
		for (SelfCreated2RealWorldClientTest.Site site : sites) {
			if (site.structure() == null || !done.add(site.kind())) {
				continue;
			}
			List<Crumb> crumbs = driver.server(server -> build(server.overworld(), site));
			driver.waitTicks(4);
			List<Crumb> kept = driver.server(server -> {
				S32.drain(server);
				return crumbs;
			});
			Sc2RealNoise.log("real 32 " + site.kind() + ": " + kept.size() + " built on its first line " + kept.stream().map(c -> c.tier().name().charAt(0) + "" + c.index())
					.toList());
			if (kept.size() < 3) {
				continue;
			}
			List<Gallery.View> views = driver.server(server -> cameras(server.overworld(), kept));
			Path dir = GalleryRun.freshDir("32_" + site.kind().substring(1) + "r");
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
				entries.add(GalleryRun.entry("32_" + site.kind().substring(1) + "r", GalleryRun.relative(png), view, GalleryRun.check(png) + (loaded ? "" : " CHUNKS?")));
			}
			GalleryRun.writeIndex(entries, "real world, the first line of " + site.kind() + " at " + site.chunk());
		}
	}

	/** Generates the chunks of the site's first line (its crumbs built by worldgen) and returns them, nearest the place first. */
	private static List<Crumb> build(ServerLevel level, SelfCreated2RealWorldClientTest.Site site) {
		Decided.Decision d = Decided.get(site.kind(), site.chunk());
		Snapshot snapshot = Snapshot.current();
		if (d == null || !d.made() || snapshot == null || S32.config() == null) {
			return List.of();
		}
		List<Line> lines = CrumbIndex.ofPlace(level, d, snapshot, S32.config(), Terrains.noise(level));
		if (lines.isEmpty()) {
			return List.of();
		}
		List<Crumb> first = List.of();
		for (Line line : lines) {
			for (int t = 0; t <= line.length() + 32; t += 8) {
				int x = (int) Math.floor(line.x() + line.ux() * t);
				int z = (int) Math.floor(line.z() + line.uz() * t);
				for (int a = -16; a <= 16; a += 16) {
					ChunkPos chunk = new ChunkPos((x - (int) (line.uz() * a)) >> 4, (z + (int) (line.ux() * a)) >> 4);
					level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, chunk, 1);
					level.getChunk(chunk.x(), chunk.z());
				}
			}
			S32.drain(level.getServer());
			Map<Integer, Crumb> mine = CrumbData.get(level.getServer()).line(line.key());
			List<String> missing = new ArrayList<>();
			int last = 0;
			boolean followable = true;
			boolean any = false;
			for (com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Spot s : line.spots()) {
				if (!mine.containsKey(s.index())) {
					missing.add(s.tier().name().charAt(0) + "" + s.index() + " " + com.forzacode.a1016_02.world.sc2.s32.CrumbFeature.why(level, s, snapshot, S32.config()));
					continue;
				}
				int allowed = any ? line.maxGap(s.tier()) : com.forzacode.a1016_02.world.sc2.s32.CrumbLines.FIRST + line.maxGap(Tier.NEAR) + 200;
				followable &= s.d() - last <= allowed;
				last = s.d();
				any = true;
			}
			LIVE[0]++;
			LIVE[1] += followable && any ? 1 : 0;
			List<Crumb> ordered = new ArrayList<>(mine.values());
			ordered.sort(Comparator.comparingInt(Crumb::index));
			sight(level, line, ordered);
			Sc2RealNoise.log("real 32 " + site.kind() + " " + line.key().substring(line.key().lastIndexOf('/') + 1) + ": " + line.length() + " long, " + mine.size()
					+ " of " + line.spots().size() + " built, " + (followable && any ? "followable" : "a gap") + "; not built " + missing);
			if (first.isEmpty()) {
				first = new ArrayList<>(mine.values());
				first.sort(Comparator.comparingInt(Crumb::index));
			}
		}
		Sc2RealNoise.log("real 32 live T5 so far: " + LIVE[1] + " of " + LIVE[0] + " lines followable");
		StringBuilder seen = new StringBuilder("real 32 next crumb in view by day so far (R3A-7: mid 70 %, far 50 %):");
		for (Tier tier : Tier.values()) {
			int[] s = SIGHT[tier.ordinal()];
			seen.append(String.format(java.util.Locale.ROOT, " %s %d of %d (%.0f %%)", tier.name().toLowerCase(java.util.Locale.ROOT), s[0], s[1],
					s[1] == 0 ? 0.0 : 100.0 * s[0] / s[1]));
		}
		Sc2RealNoise.log(seen.toString());
		for (int t = 1; t < Tier.values().length; t++) {
			for (int h : HEIGHTS) {
				StringBuilder curve = new StringBuilder("real 32 sight curve so far, from beside a " + Tier.values()[t].name().toLowerCase(java.util.Locale.ROOT)
						+ " crumb, a thing " + h + " high that far toward the place:");
				for (int k = 0; k < DISTANCES.length; k++) {
					int[] c = CURVE[t][h][k];
					curve.append(String.format(java.util.Locale.ROOT, " %d %.0f %% (%d)", DISTANCES[k], c[1] == 0 ? 0.0 : 100.0 * c[0] / c[1], c[1]));
				}
				Sc2RealNoise.log(curve.toString());
			}
		}
		if (com.forzacode.a1016_02.world.sc2.s32.CrumbFeature.COSTS != null) {
			List<Long> ns = new ArrayList<>(com.forzacode.a1016_02.world.sc2.s32.CrumbFeature.COSTS);
			java.util.Collections.sort(ns);
			if (!ns.isEmpty()) {
				Sc2RealNoise.log(String.format(java.util.Locale.ROOT, "real 32 a chunk's build with lines, real worldgen: median %.3f ms, 99 %% %.3f, worst %.3f (%d chunks)",
						ns.get(ns.size() / 2) / 1e6, ns.get(ns.size() * 99 / 100) / 1e6, ns.getLast() / 1e6, ns.size()));
			}
		}
		return first;
	}

	/** Lines built by real worldgen so far, and of them followable (T5 live). */
	static final int[] LIVE = new int[2];

	/** Per tier (of the crumb looked from): the next crumb toward the place in view by day, and the crumbs asked. */
	static final int[][] SIGHT = new int[Tier.values().length][2];

	/**
	 * R3A-7's check: from beside each built crumb (3 toward the next one toward the place, a ground eye over the land
	 * there), is any cell of that next crumb in view (nothing of the land, a tree or a block between, leaves included)?
	 */
	private static void sight(ServerLevel level, Line line, List<Crumb> ordered) {
		for (int i = 1; i < ordered.size(); i++) {
			Crumb from = ordered.get(i);
			Crumb to = ordered.get(i - 1);
			int[] s = SIGHT[from.tier().ordinal()];
			s[1]++;
			s[0] += SelfCreated3CrumbTests.nextInView(level, from, to) ? 1 : 0;
		}
		// FIX-R3-C, the spacing's choice: from beside each mid and far crumb, a thing standing on the land that far toward
		// the place along the line (no block of it there: its cells' air seen), by its height.
		for (Crumb from : ordered) {
			int t = from.tier().ordinal();
			if (from.tier() == Tier.NEAR) {
				continue;
			}
			for (int k = 0; k < DISTANCES.length; k++) {
				int d = DISTANCES[k];
				double x = from.at().getX() + 0.5 - line.ux() * d;
				double z = from.at().getZ() + 0.5 - line.uz() * d;
				int bx = (int) Math.floor(x);
				int bz = (int) Math.floor(z);
				int ground = landTop(level, bx, bz);
				Vec3 eye = SelfCreated3CrumbTests.walkerEye(level, from, new Crumb(from.line(), -1, from.tier(), new BlockPos(bx, ground + 1, bz), 1, -1, false));
				for (int h : HEIGHTS) {
					boolean seen = false;
					for (int up = h; up >= 1 && !seen; up--) {
						Vec3 cell = new Vec3(bx + 0.5, ground + up + 0.5, bz + 0.5);
						seen = level.clip(new net.minecraft.world.level.ClipContext(eye, cell, net.minecraft.world.level.ClipContext.Block.VISUAL,
								net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getType()
								== net.minecraft.world.phys.HitResult.Type.MISS;
					}
					CURVE[t][h][k][1]++;
					CURVE[t][h][k][0] += seen ? 1 : 0;
				}
			}
		}
	}

	/** The distances and heights of the spacing's curve. */
	static final int[] DISTANCES = {16, 20, 24, 28, 32, 36, 40, 44, 48, 56};
	static final int[] HEIGHTS = {1, 2, 3, 4, 5};
	/** Per tier, height and distance: in view, asked. */
	static final int[][][][] CURVE = new int[Tier.values().length][6][DISTANCES.length][2];

	/** The land's top block of a column: under leaves, logs, plants and snow. */
	private static int landTop(ServerLevel level, int x, int z) {
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		while (y > level.getMinY()) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(new BlockPos(x, y, z));
			if (!state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.canBeReplaced()) {
				return y;
			}
			y--;
		}
		return y;
	}

	/**
	 * Real land hides a crumb from a ground eye at 50 or more (hills, trees): each tier's outermost crumb from 14 out on
	 * the line, 3 over the land, day and night; the line from 40 past its last crumb lifted 24 over the land; and from 60
	 * up over its near tier.
	 */
	private static List<Gallery.View> cameras(ServerLevel level, List<Crumb> crumbs) {
		List<Gallery.View> out = new ArrayList<>();
		Crumb first = crumbs.getFirst();
		Crumb last = crumbs.getLast();
		double dx = last.at().getX() - first.at().getX();
		double dz = last.at().getZ() - first.at().getZ();
		double len = Math.max(1, Math.hypot(dx, dz));
		double ux = dx / len;
		double uz = dz / len;
		for (Tier tier : Tier.values()) {
			Crumb outer = null;
			for (Crumb c : crumbs) {
				if (c.tier() == tier) {
					outer = c;
				}
			}
			if (outer == null) {
				continue;
			}
			String name = tier.name().toLowerCase(java.util.Locale.ROOT);
			double x = outer.at().getX() + 0.5 + ux * 14 - uz * 4;
			double z = outer.at().getZ() + 0.5 + uz * 14 + ux * 4;
			Vec3 eye = lifted(level, x, z).add(0, 1.3, 0);
			Vec3 at = Vec3.atCenterOf(outer.at());
			out.add(Gallery.View.looking("d_" + name, eye, at, Gallery.NOON, Gallery.Sky.CLEAR, false).noted("its outermost " + name + " crumb from 14, noon"));
			out.add(Gallery.View.looking("n_" + name, eye, at, Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("the same, midnight"));
		}
		// R3A-7: from beside a mid crumb, a walker's eye, toward the next one toward the place, noon.
		for (int i = 1; i < crumbs.size(); i++) {
			if (crumbs.get(i).tier() == Tier.MID) {
				Crumb from = crumbs.get(i);
				Crumb to = crumbs.get(i - 1);
				out.add(Gallery.View.looking("from_crumb", SelfCreated3CrumbTests.walkerEye(level, from, to), Vec3.atCenterOf(to.cells().getLast()), Gallery.NOON, Gallery.Sky.CLEAR, false)
						.noted("beside a mid crumb at a walker's eye, toward the next one, noon: " + (SelfCreated3CrumbTests.nextInView(level, from, to) ? "in view" : "hidden")));
				break;
			}
		}
		double x = last.at().getX() + 0.5 + ux * 40 - uz * 6;
		double z = last.at().getZ() + 0.5 + uz * 40 + ux * 6;
		Vec3 along = lifted(level, x, z).add(0, 22, 0);
		Vec3 middle = Vec3.atCenterOf(crumbs.get(crumbs.size() / 2).at());
		out.add(Gallery.View.looking("along_day", along, middle, Gallery.NOON, Gallery.Sky.CLEAR, false).noted("40 past its last, 24 up, along it, noon"));
		out.add(Gallery.View.looking("along_night", along, middle, Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("the same, midnight"));
		BlockPos near = first.at();
		Vec3 high = new Vec3(near.getX() + 0.5 + ux * 50 - uz * 40, near.getY() + 60, near.getZ() + 0.5 + uz * 50 + ux * 40);
		out.add(Gallery.View.looking("aerial", high, Vec3.atCenterOf(near), Gallery.NOON, Gallery.Sky.CLEAR, false).noted("60 over its near tier, noon"));
		out.add(Gallery.View.looking("aerial_night", high, Vec3.atCenterOf(near), Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("the same, midnight"));
		return out;
	}

	/** A ground eye at (x, z), lifted over any tree there. */
	private static Vec3 lifted(ServerLevel level, double x, double z) {
		return new Vec3(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z))
				+ Gallery.EYE, z);
	}
}
