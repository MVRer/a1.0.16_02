package com.forzacode.a1016_02.world.sc2.s32;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Fault;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.s32.CrumbData.Crumb;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Line;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Feature 32's exhibits (dev only; docs/SELF_CREATED_2_R3.md, "32", "Gallery"): {@code 32_16}, {@code 32_16d},
 * {@code 32_17}, {@code 32_18}, {@code 32_19}, {@code 32_20}, one line of each place's crumbs, built by the feature's own
 * path ({@link CrumbFeature#live}), running {@value #LENGTH} east from a stub of its place's sign over land made first:
 * a stream the near tier crosses, a hill the mid tier goes over, a wood the far tier runs through (16d's starts at
 * sea). Views (the camera beside the line, looking along it toward the place): {@code d48} / {@code n48} 48 out from the
 * last far crumb, {@code d96} / {@code n96} 96 out from the mid tier's last, {@code d160} / {@code n160} 160 out from
 * the near tier's last, {@code n200} the near tier from 200 at night, {@code along_day} / {@code along_night} from beside
 * a far crumb, {@code aerial} the tiers from 160 up, {@code canopy} the wood's crumb from above at night.
 */
final class CrumbGallery {
	static final int LENGTH = 640;
	static final List<String> KINDS = List.of("s16", "s16d", "s17", "s18", "s19", "s20");
	/** The stream's near edge, the hill's middle and the wood's span (blocks along the line). */
	static final int STREAM = 124;
	static final int HILL = 472;
	static final int WOOD_FROM = 522;
	static final int WOOD_TO = 600;

	private CrumbGallery() {
	}

	static void register() {
		for (String kind : KINDS) {
			Gallery.register(new Gallery.Exhibit("32_" + kind.substring(1), "32", (level, origin, seed) -> place(level, origin, kind)));
		}
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, String kind) {
		int ox = origin.getX();
		int oz = origin.getZ();
		int g = origin.getY();
		load(level, new BoundingBox(ox - 48, g, oz - 72, ox + LENGTH + 260, g, oz + 72));
		boolean sea = kind.equals("s16d");
		// The land, made before the crumbs.
		if (sea) {
			GalleryProps.sea(level, new BoundingBox(ox - 40, g - 9, oz - 70, ox + 300, g - 1, oz + 70), 8);
		} else {
			GalleryProps.sea(level, new BoundingBox(ox + STREAM, g - 4, oz - 70, ox + STREAM + 9, g - 1, oz + 70), 3);
		}
		if (kind.equals("s18")) {
			GalleryProps.hill(level, new BlockPos(ox - 30, g, oz), 150, 44);
		}
		GalleryProps.hill(level, new BlockPos(ox + HILL, g, oz + 10), 40, 12);
		wood(level, ox, oz);
		BoundingBox own = stub(level, kind, ox, g, oz);
		Sc32Config config = new Sc32Config();
		Line line = new Line("gallery/" + kind, kind, ox, oz, 0.0, LENGTH, CrumbLines.VERSION, 0x32C0FFEEL ^ kind.hashCode(),
				new int[] {own.minX(), own.minZ(), own.maxX(), own.maxZ()},
				new int[] {config.nearTo, config.midTo, config.nearSpacing, config.midSpacing, config.farSpacing});
		Snapshot snapshot = new Snapshot(true, Stage.TRACES, new BlockPos(ox - 200_000, g, oz), 0L, 0L, Map.of(), true);
		List<Crumb> built = CrumbFeature.live(level, line, snapshot, config, false);
		CrumbData.dropPending();

		int lastFar = 0;
		int lastMid = 0;
		int lastNear = 0;
		Crumb woodCrumb = null;
		Crumb farOne = null;
		for (Crumb c : built) {
			int d = c.at().getX() - ox;
			switch (c.tier()) {
				case FAR -> {
					lastFar = Math.max(lastFar, d);
					if (d >= WOOD_FROM && d <= WOOD_TO && woodCrumb == null) {
						woodCrumb = c;
					}
					// The one beside the along views: a lit one (its torch) nearest the hill.
					boolean lit = c.torch() != -1 || kind.equals("s19");
					if (lit && (farOne == null || Math.abs(d - 420) < Math.abs(farOne.at().getX() - ox - 420))) {
						farOne = c;
					}
				}
				case MID -> lastMid = Math.max(lastMid, d);
				case NEAR -> lastNear = Math.max(lastNear, d);
			}
		}
		if (farOne == null) {
			farOne = built.stream().filter(c -> c.tier() == Tier.FAR).findFirst().orElse(null);
		}
		Crumb mid = built.stream().filter(c -> c.tier() == Tier.MID).findFirst().orElse(null);
		if (mid != null) {
			GalleryProps.scaleFigure(level, new BlockPos(mid.at().getX() + 2, Gallery.ground(level, mid.at().getX() + 2, oz + 3), oz + 3), 90.0F);
		}

		List<Gallery.View> views = new ArrayList<>();
		// Beside the line (a few blocks aside, so its things recede in a row), looking along it toward the place.
		views.addAll(pair(level, "48", ox + lastFar + 48, oz + 6.5, new Vec3(ox + lastFar + 0.5, g + 1, oz + 0.5),
				"48 out from the last far crumb, 6 aside, looking at it"));
		views.addAll(pair(level, "96", ox + lastMid + 96, oz + 10.5, new Vec3(ox + 240, g + 1, oz + 0.5),
				"96 out from the mid tier's last, 10 aside, looking along the mid tier"));
		views.addAll(pair(level, "160", ox + lastNear + 160, oz + 12.5, new Vec3(ox + 110, g + 1, oz + 0.5),
				"160 out from the near tier's last, 12 aside, looking along the near tier"));
		Vec3 far200 = Gallery.groundEye(level, ox + lastNear + 200.5, oz + 14.5);
		views.add(Gallery.View.looking("n200", far200, new Vec3(ox + 110, g + 1, oz + 0.5), Gallery.NIGHT, Gallery.Sky.CLEAR, false)
				.noted("200 out from the near tier's last, 14 aside, midnight"));
		if (farOne != null) {
			Vec3 eye = Gallery.groundEye(level, farOne.at().getX() + 6.5, oz + 2.5);
			Vec3 at = new Vec3(ox + 200, g + 2, oz + 0.5);
			views.add(Gallery.View.looking("along_day", eye, at, Gallery.NOON, Gallery.Sky.CLEAR, false).noted("beside a far crumb, looking along, noon"));
			views.add(Gallery.View.looking("along_night", eye, at, Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("the same at midnight"));
		}
		Vec3 high = new Vec3(ox + 330.5, g + 150, oz - 170.5);
		views.add(Gallery.View.looking("aerial", high, new Vec3(ox + 300, g, oz), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("the tiers from 150 up and 170 aside, noon (judge with -Pgallery.rd=24)"));
		views.add(Gallery.View.looking("aerial_night", new Vec3(ox + 250.5, g + 60, oz - 70.5), new Vec3(ox + 150, g, oz), Gallery.NIGHT, Gallery.Sky.CLEAR,
				false).noted("the near and mid tiers from 60 up and 70 aside, midnight"));
		if (woodCrumb != null) {
			views.add(Gallery.View.looking("canopy", new Vec3(woodCrumb.at().getX() + 6.5, woodCrumb.at().getY() + 34, woodCrumb.at().getZ() + 10.5),
					Vec3.atCenterOf(woodCrumb.at()), Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("the wood's crumb from above, midnight"));
		}
		return new Gallery.Placed(new BoundingBox(ox - 40, g - 10, oz - 70, ox + LENGTH + 40, g + 60, oz + 70), views, List.of());
	}

	/** A day and a night view from a ground eye at (x, z) looking at {@code at}. */
	private static List<Gallery.View> pair(ServerLevel level, String name, double x, double z, Vec3 at, String note) {
		Vec3 eye = Gallery.groundEye(level, x, z);
		return List.of(Gallery.View.looking("d" + name, eye, at, Gallery.NOON, Gallery.Sky.CLEAR, false).noted(note + ", noon"),
				Gallery.View.looking("n" + name, eye, at, Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted(note + ", midnight"));
	}

	/** A stub of the place's sign: a block of its stuff with its dark way in, lit as the faults are. Returns its box. */
	private static BoundingBox stub(ServerLevel level, String kind, int ox, int g, int oz) {
		BlockState stuff = switch (kind) {
			case "s16", "s16d" -> Blocks.BRICKS.defaultBlockState();
			case "s17" -> Blocks.COBBLESTONE.defaultBlockState();
			case "s18" -> Blocks.STONE.defaultBlockState();
			case "s19" -> Blocks.OBSIDIAN.defaultBlockState();
			default -> Blocks.GRASS_BLOCK.defaultBlockState();
		};
		int base = Gallery.ground(level, ox, oz);
		int high = kind.equals("s19") ? 40 : kind.equals("s20") ? 1 : 12;
		BoundingBox box = new BoundingBox(ox - 24, base - 1, oz - 8, ox - 9, base + high, oz + 7);
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			GalleryProps.set(level, p, stuff);
		}
		// The way in, two by two, on the face toward the line.
		for (int dy = 0; dy < 2; dy++) {
			for (int dz = 0; dz < 2; dz++) {
				GalleryProps.set(level, new BlockPos(box.maxX(), base + dy, oz + dz), Blocks.AIR.defaultBlockState());
				GalleryProps.set(level, new BlockPos(box.maxX() - 1, base + dy, oz + dz), Blocks.AIR.defaultBlockState());
			}
		}
		// Its lighting fault: the chunk-wide square before it lit as at noon.
		for (int x = box.maxX() + 2; x <= box.maxX() + 16; x += 3) {
			for (int z = oz - 7; z <= oz + 7; z += 3) {
				int y = Gallery.ground(level, x, z);
				boolean wet = level.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.WATER);
				GalleryProps.set(level, new BlockPos(x, wet ? y - 1 : y, z), Fault.lightOf(Fault.LEVEL, wet));
			}
		}
		return box;
	}

	/** A wood across the line's far tier, with a few trees right on it. */
	private static void wood(ServerLevel level, int ox, int oz) {
		String[] kinds = {"minecraft:oak", "minecraft:birch", "minecraft:fancy_oak", "minecraft:oak"};
		int i = 0;
		for (int x = ox + WOOD_FROM; x <= ox + WOOD_TO; x += 7) {
			for (int z = oz - 40; z <= oz + 40; z += 7) {
				double a = Math.sin(x * 12.9898 + z * 78.233) * 43758.5453;
				int jx = (int) ((a - Math.floor(a)) * 5) - 2;
				int jz = (int) ((a * 7 - Math.floor(a * 7)) * 5) - 2;
				int tx = x + jx;
				int tz = z + jz;
				GalleryProps.tree(level, new BlockPos(tx, Gallery.ground(level, tx, tz), tz), kinds[i++ % kinds.length]);
			}
		}
	}

	/** Loads (generates) every chunk of the box, so the props write into made land. */
	private static void load(ServerLevel level, BoundingBox box) {
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}
}
