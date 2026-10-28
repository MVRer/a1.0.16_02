package com.forzacode.a1016_02.world.sc2.s19;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Feature 19's exhibit (dev only; docs/SELF_CREATED_2.md, "The gallery"): {@code 19}, a 400-block segment running north
 * to south through the exhibit's origin, built by the feature's own shape over land made first so it has something to
 * go through: a hill it cuts, a lake it crosses, a wood whose trees it splits, the flat between; the scale figure by an
 * opening. Judge its long views with {@code -Pgallery.rd=24}.
 */
public final class LineGallery {
	static final int LENGTH = 400;
	static final long SEED = 0x19C0FFEEL;

	private LineGallery() {
	}

	static void register() {
		Gallery.register(new Gallery.Exhibit("19", "19", LineGallery::place));
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int ox = origin.getX();
		int oz = origin.getZ();
		int g = origin.getY();
		Sc19Config config = new Sc19Config();
		LinePlan.Segment segment = LinePlan.cut(LinePlan.Arm.SOUTH, ox, oz - LENGTH / 2, LENGTH, SEED, config.holeEvery, config.gapWidth,
				level.getMinY() + LinePlan.ABOVE_MIN, level.getMaxY() - LinePlan.BELOW_MAX);
		load(level, new BoundingBox(ox - 300, g, oz - LENGTH / 2 - 40, ox + 300, g, oz + LENGTH / 2 + 40));
		// The land it goes through, made before it: a hill to the south it cuts, a lake to the north it crosses.
		GalleryProps.hill(level, new BlockPos(ox + 26, g, oz + 120), 80, 72);
		GalleryProps.sea(level, new BoundingBox(ox - 46, g - 7, oz - 160, ox + 46, g - 1, oz - 104), 6);
		// A viewing rise far to the east, for the long look from a height.
		GalleryProps.hill(level, new BlockPos(ox + 250, g, oz - 150), 46, 30);
		trees(level, ox, oz, g);

		Terrains.Frozen terrain = Live.terrain(level);
		LineShape shape = new LineShape(segment, terrain);
		Live.Result built = Live.place(level, S19.KIND, shape, terrain, segment.start());

		int hole = middleHole(segment);
		int holeY = shape.opening(terrain, hole)[0];
		GalleryProps.scaleFigure(level, new BlockPos(ox + 2, Gallery.ground(level, ox + 2, hole + 3), hole + 3), 90.0F);

		List<Gallery.View> views = new ArrayList<>();
		int gap = segment.gapMiddle();
		Vec3 sideAt = new Vec3(ox + 0.5, g + 70, oz + 0.5);
		Gallery.View side = Gallery.View.looking("far_day", Gallery.groundEye(level, ox + 200.5, oz + 0.5), sideAt, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("200 out to the east, square on, noon");
		views.add(side);
		views.add(side.named("far_dusk").at(Gallery.DUSK, Gallery.Sky.DUSK).noted("200 out, square on, dusk"));
		views.add(side.named("far_night").at(Gallery.NIGHT, Gallery.Sky.CLEAR).noted("200 out, square on, midnight"));
		views.add(side.named("far_fog").at(Gallery.NIGHT, Gallery.Sky.FOG_NIGHT).noted("200 out, square on, a fog night at full"));
		Vec3 rise = Gallery.groundEye(level, ox + 250.5, oz - 149.5);
		views.add(Gallery.View.looking("rise", rise, new Vec3(ox + 0.5, g + 40, oz + 40.5), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("from the top of a rise 300 off to the north-east, noon: the length of it across the land"));
		views.add(Gallery.View.looking("rise_dusk", rise, new Vec3(ox + 0.5, g + 40, oz + 40.5), Gallery.DUSK, Gallery.Sky.DUSK, false)
				.noted("the same at dusk"));
		views.add(Gallery.View.looking("rise_night", rise, new Vec3(ox + 0.5, g + 60, oz + 40.5), Gallery.NIGHT, Gallery.Sky.CLEAR, false)
				.noted("the same at midnight"));
		views.add(Gallery.View.looking("mid", Gallery.groundEye(level, ox + 120.5, oz - 70.5), new Vec3(ox + 0.5, g + 40, oz + 0.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("140 out, 30 degrees aside, noon"));
		views.add(Gallery.View.looking("along", Gallery.groundEye(level, ox + 9.5, oz - 60.5), new Vec3(ox + 1.5, g + 24, oz + 150.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("9 off its east face, looking down its length to the south"));
		int end = segment.p0() - 2 * LinePlan.BAY;
		views.add(Gallery.View.looking("near_1", Gallery.groundEye(level, ox + 70.5, end - 50.5), new Vec3(ox + 0.5, g + 50, end + 0.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("its north end from 85 off: where the panels stop and the ribs go on alone"));
		views.add(Gallery.View.looking("near_1_dusk", Gallery.groundEye(level, ox + 70.5, end - 50.5), new Vec3(ox + 0.5, g + 50, end + 0.5), Gallery.DUSK,
				Gallery.Sky.DUSK, false).noted("the same at dusk"));
		views.add(Gallery.View.looking("up", new Vec3(ox + 4.5, g + 1.7, oz + 14.5), new Vec3(ox + 0.5, g + 170, oz + 44.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("from 4 off its foot, up its face"));
		views.add(Gallery.View.looking("near_2", Gallery.groundEye(level, ox + 7.5, hole + 1.0), new Vec3(ox + 0.5, holeY + 1.0, hole + 1.0), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("7 in front of an opening at its foot, noon"));
		views.add(Gallery.View.looking("near_2_night", Gallery.groundEye(level, ox + 7.5, hole + 1.0), new Vec3(ox + 0.5, holeY + 1.0, hole + 1.0),
				Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("the same at midnight"));
		views.add(Gallery.View.looking("near_3", Gallery.groundEye(level, ox + 34.5, gap + 0.5), new Vec3(ox + 0.5, g + 26, gap + 0.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("34 out from the cut, noon"));
		views.add(Gallery.View.looking("near_4", Gallery.groundEye(level, ox + 62.5, oz + 206.5), new Vec3(ox + 0.5, g + 40, oz + 128.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("south-east of the hill, where it goes through it"));
		views.add(Gallery.View.looking("near_5", Gallery.groundEye(level, ox - 30.5, oz - 92.5), new Vec3(ox + 0.5, g + 8, oz - 140.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("from the lake's south shore, across the water where it crosses"));
		views.add(Gallery.View.looking("near_6", Gallery.groundEye(level, ox + 26.5, oz - 20.5), new Vec3(ox + 0.5, g + 9, oz - 58.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("from the edge of the wood beside it, where it splits the trees"));
		Vec3 aerial = new Vec3(ox + 170.5, g + 170, oz - 150.5);
		views.add(Gallery.View.looking("aerial", aerial, new Vec3(ox + 0.5, g, oz + 0.5), Gallery.NOON, Gallery.Sky.CLEAR, false).noted("170 out, 170 up, noon"));
		// Version 3's sign (R3-2, D-110): along the line from past its near end (the sign point is the opening nearest the
		// origin, the cameras out along the axis), and square on to an opening at 64 and 128 off its face.
		views.addAll(Gallery.signViews(level, shape.sign(), shape.entranceFacing()));
		// An opening with nothing standing between it and the cameras (the wood is north of the middle).
		int clear = segment.holes().getFirst();
		for (int h : segment.holes()) {
			if (Math.abs(h - (oz + 12)) < Math.abs(clear - (oz + 12))) {
				clear = h;
			}
		}
		int clearY = shape.opening(terrain, clear)[0];
		for (int d : new int[] {64, 128}) {
			Gallery.View h = Gallery.View.looking("hole_" + d, Gallery.groundEye(level, ox + d + 0.5, clear + 1.0), new Vec3(ox + 0.5, clearY + 1.0, clear + 1.0),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted(d + " out square on to an opening, noon");
			views.add(h);
			views.add(h.named("hole_n" + d).at(Gallery.NIGHT, Gallery.Sky.CLEAR).noted(d + " out square on to an opening, midnight"));
		}
		// FIX-R3-B (R3A-4): the lit holes at 45 degrees to the wall, from either side (each camera raised to the first
		// height with nothing between it and the hole, leaves and logs included), and square on from 85 out at midnight.
		Vec3 holeAt = new Vec3(ox + 0.5, clearY + 1.0, clear + 1.0);
		for (int d : new int[] {64, 128}) {
			double o = d / Math.sqrt(2.0);
			for (int face : new int[] {1, -1}) {
				Vec3 eye = clearEye(level, Gallery.groundEye(level, ox + 0.5 + face * o, clear + 1.0 + o), holeAt, 40);
				String name = "hole45" + (face > 0 ? "e" : "w") + "_n" + d;
				views.add(Gallery.View.looking(name, eye, holeAt, Gallery.NIGHT, Gallery.Sky.CLEAR, false)
						.noted(d + " out at 45 degrees to the " + (face > 0 ? "east" : "west") + " face, midnight, " + Math.round(eye.y - Gallery.groundEye(level,
								ox + 0.5 + face * o, clear + 1.0 + o).y) + " raised for a clear sight"));
			}
		}
		// 19b (FIX-R3-B): one steps out of an opening and walks along the wall toward the next: open, then wall again.
		List<Gallery.Frames> frames = new ArrayList<>();
		int at = segment.holes().indexOf(clear);
		if (at > 0 && at < segment.holes().size() - 1) {
			int next = segment.holes().get(at + 1);
			Vec3 nextAt = new Vec3(ox + 0.5, shape.opening(terrain, next)[0] + 1.0, next + 1.0);
			Vec3 out = clearEye(level, Gallery.groundEye(level, ox + 16.5, next - 19.0), nextAt, 30);
			Gallery.View cam = Gallery.View.looking("19b", out, nextAt, Gallery.NOON, Gallery.Sky.CLEAR, false);
			frames.add(new Gallery.Frames("19b", 3, 60, cam, lvl -> LineAnswers.stageMend(lvl, segment, List.of(segment.holes().get(at - 1), next), 70))
					.noted("16 off the east face, 20 short of the next opening, looking at it at noon: open, then wall again (staged"
							+ " after the first frame; in the game it happens while one stands in the opening behind the camera, out of view)"));
		}
		Vec3 offAxis = Gallery.groundEye(level, ox + 24.5, segment.t0() - 120.5);
		views.add(Gallery.View.looking("along_night", offAxis, new Vec3(ox + 0.5, g + 2, oz + 0.5), Gallery.NIGHT, Gallery.Sky.CLEAR, false)
				.noted("24 off its line, 120 past its north end, midnight: the lit feet running away"));
		views.add(Gallery.View.looking("air_night", new Vec3(ox + 40.5, g + 45, clear - 70.5), new Vec3(ox + 0.5, g, clear + 60.5), Gallery.NIGHT,
				Gallery.Sky.CLEAR, false).noted("a flyer 45 up, 40 off its east face, looking along it, midnight: the lit feet in pairs"));
		return new Gallery.Placed(built.box(), views, frames);
	}

	/**
	 * A camera with a clear sight of its target (FIX-R3-B, R3A-2d): {@code eye} raised a block at a time, up to
	 * {@code maxRaise}, to the first height where a ray from it to within 3 of {@code target} meets no block (leaves,
	 * logs and plants included); if none does, slid toward the target 8 at a time (to 24 from it) and raised again; the eye
	 * raised the most if nothing is clear (over the canopy at least). The real-terrain run's cameras use it too.
	 */
	public static Vec3 clearEye(ServerLevel level, Vec3 eye, Vec3 target, int maxRaise) {
		for (int slide = 0; slide <= 32; slide++) {
			Vec3 flat = new Vec3(target.x - eye.x, 0, target.z - eye.z);
			double far = flat.length();
			if (far - 8.0 * slide < 24) {
				break;
			}
			Vec3 base = eye.add(flat.normalize().scale(8.0 * slide));
			int ground = Gallery.ground(level, (int) Math.floor(base.x), (int) Math.floor(base.z));
			Vec3 foot = new Vec3(base.x, Math.max(base.y, ground + 1.7), base.z);
			for (int up = 0; up <= maxRaise; up++) {
				Vec3 at = foot.add(0, up, 0);
				if (clear(level, at, target)) {
					return at;
				}
			}
		}
		return eye.add(0, maxRaise, 0);
	}

	/** True if a ray from {@code eye} to within 3 of {@code target} passes through nothing but air (or light blocks). */
	public static boolean clear(ServerLevel level, Vec3 eye, Vec3 target) {
		Vec3 d = target.subtract(eye);
		double length = d.length();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (double s = 0.5; s < length - 3.0; s += 0.5) {
			Vec3 p = eye.add(d.scale(s / length));
			at.set(Math.floor(p.x), Math.floor(p.y), Math.floor(p.z));
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
			if (!state.isAir() && !com.forzacode.a1016_02.world.sc2.kit.Fault.isLight(state)) {
				return false;
			}
		}
		return true;
	}

	/** The opening nearest the middle of the segment. */
	private static int middleHole(LinePlan.Segment segment) {
		int middle = (segment.t0() + segment.t1()) / 2;
		int best = segment.holes().getFirst();
		for (int hole : segment.holes()) {
			if (Math.abs(hole - middle) < Math.abs(best - middle)) {
				best = hole;
			}
		}
		return best;
	}

	/** A wood on both sides of the plane north of the middle, three trees standing right against it, and a few loose ones. */
	private static void trees(ServerLevel level, int ox, int oz, int g) {
		String[] kinds = {"minecraft:oak", "minecraft:birch", "minecraft:fancy_oak", "minecraft:oak", "minecraft:birch"};
		int i = 0;
		for (int k = 0; k < 64; k++) {
			double a = Math.sin(k * 12.9898) * 43758.5453;
			double b = Math.sin(k * 78.233) * 12345.6789;
			double fx = a - Math.floor(a);
			double fz = b - Math.floor(b);
			int side = k % 2 == 0 ? 1 : -1;
			int x = ox + side * (3 + (int) (fx * 38));
			int z = oz - 96 + (int) (fz * 92);
			tree(level, x, z, kinds[i++ % kinds.length]);
		}
		for (int z : new int[] {oz - 80, oz - 52, oz - 18}) {
			tree(level, ox + (z % 2 == 0 ? 1 : -1), z, "minecraft:oak");
		}
		for (int k = 0; k < 24; k++) {
			double a = Math.sin(k * 3.17 + 1.3) * 9182.123;
			double b = Math.sin(k * 7.71 + 0.4) * 3311.917;
			int x = ox + (int) ((a - Math.floor(a)) * 220) - 110;
			int z = oz + 30 + (int) ((b - Math.floor(b)) * 60);
			if (Math.abs(x - ox) > 4) {
				tree(level, x, z, kinds[i++ % kinds.length]);
			}
		}
	}

	private static void tree(ServerLevel level, int x, int z, String kind) {
		ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
		level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, chunk, 1);
		for (int cx = chunk.x() - 1; cx <= chunk.x() + 1; cx++) {
			for (int cz = chunk.z() - 1; cz <= chunk.z() + 1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		GalleryProps.tree(level, new BlockPos(x, Gallery.ground(level, x, z), z), kind);
	}

	/** Loads (generates) every chunk of the box, so the props write into made land. */
	private static void load(ServerLevel level, BoundingBox box) {
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}
}
