package com.forzacode.a1016_02.world.sc2.s20;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Feature 20's exhibit (dev only; docs/SELF_CREATED_2.md, "The gallery"): {@code 20}, a 64 square cut into the flank of
 * a gallery hill that rises to the east (cliffs on the east and north, its west and south sides built up over the falling
 * ground), the building's doorway to the west; trees on the hill and the flat (those that stood on the square are taken
 * by it), a rise far to the west-south-west for the long look, cows on the floor, the scale figure by the doorway. Views:
 * the square from the air, from the rise, the built-up side, the cliff, the building at dusk and at night, inside it,
 * and frames of 20b (the cows turning to face it).
 */
final class FloorGallery {
	static final long SEED = 0x20F1A7L;
	static final int SIDE = 96;

	private FloorGallery() {
	}

	static void register() {
		Gallery.register(new Gallery.Exhibit("20", "20", (level, origin, seed) -> place(level, origin, seed, false)));
		Gallery.register(new Gallery.Exhibit("20n", "20", (level, origin, seed) -> place(level, origin, seed, true)));
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed, boolean night) {
		int ox = origin.getX();
		int oz = origin.getZ();
		int g = origin.getY();
		load(level, new BoundingBox(ox - 260, g, oz - 220, ox + 280, g, oz + 240));
		GalleryProps.hill(level, new BlockPos(ox + 90, g, oz + 40), 180, 38);
		GalleryProps.hill(level, new BlockPos(ox - 190, g, oz - 70), 46, 26);
		trees(level, ox, oz);

		// Version 3's sizes in siting's order (R3-3): the widest the hill allows.
		int side = SIDE;
		int floor = g + 14;
		for (int tried : FloorSiting.sides(new Sc20Config())) {
			if (FloorSiting.fit(Live.terrain(level), ox, oz, tried, FloorShape.gridOf(FloorShape.VERSION, tried)) instanceof FloorSiting.Fit fit) {
				side = tried;
				floor = fit.floor();
				break;
			}
		}
		com.forzacode.a1016_02.A1016_02.LOGGER.info("[a1016] gallery 20: side {}, y {}", side, floor);
		Start start = new Start(new BlockPos(ox, floor, oz), Direction.WEST, FloorShape.variant(side), SEED);
		Live.Result built = Live.place(level, S20.KIND, start);
		// Worldgen lights a chunk after it is built; here the light engine is asked to catch up before any camera looks.
		Live.settleLight(level, built.box());
		FloorShape shape = (FloorShape) built.shape();
		BlockPos door = shape.entrance();
		GalleryProps.scaleFigure(level, door.offset(-2, 0, 2), 90.0F);
		int[][] spots = {{-20, -14}, {-14, 9}, {-24, 2}, {8, -20}, {13, 17}, {-6, 22}, {21, -6}, {-27, -25}, {18, 4}, {-10, -24}, {-36, 30}, {30, -33},
				{-38, -6}, {34, 26}, {-7, 12}, {6, 10}, {-8, -11}, {5, -12}, {-13, 15}};
		for (int[] spot : spots) {
			GalleryProps.summon(level, EntityTypes.COW, new BlockPos(ox + spot[0], floor + 1, oz + spot[1]), 1);
		}

		List<Gallery.View> views = new ArrayList<>();
		Vec3 middle = new Vec3(ox + 0.5, floor + 2, oz + 0.5);
		if (night) {
			return night(level, built, shape, side, floor);
		}
		views.add(Gallery.View.looking("aerial", new Vec3(ox - 150.5, floor + 150, oz - 130.5), new Vec3(ox + 0.5, floor, oz + 0.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("from the air to the north-west, noon"));
		Vec3 rise = Gallery.groundEye(level, ox - 189.5, oz - 69.5);
		Gallery.View far = Gallery.View.looking("far_day", rise, middle.add(0, 6, 0), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("from the top of a rise 200 off to the west-south-west, noon");
		views.add(far);
		views.add(far.named("far_dusk").at(Gallery.DUSK, Gallery.Sky.DUSK).noted("the same at dusk"));
		views.add(far.named("far_night").at(Gallery.NIGHT, Gallery.Sky.CLEAR).noted("the same at midnight"));
		views.add(far.named("far_fog").at(Gallery.NIGHT, Gallery.Sky.FOG_NIGHT).noted("the same, a fog night at full"));
		// R11-5: from low ground 150 out, where the slab's built-up edge stands on the skyline.
		views.add(Gallery.View.looking("far_2", Gallery.groundEye(level, ox - 150.5, oz + 30.5), new Vec3(ox - 40.5, floor + 2, oz + 20.5), Gallery.DUSK,
				Gallery.Sky.DUSK, false).noted("from low ground 150 off to the west at dusk: the square's built-up edge against the sky"));
		views.add(Gallery.View.looking("mid", Gallery.groundEye(level, ox - 110.5, oz + 75.5), middle, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("135 out to the south-west, noon"));
		views.add(Gallery.View.looking("near_1", Gallery.groundEye(level, ox - 66.5, oz + 26.5), new Vec3(ox - 48.5, floor - 3, oz + 36.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("below the built-up west side, 18 out, looking at its face"));
		views.add(Gallery.View.looking("near_2", new Vec3(ox + 8.5, floor + 1.7, oz - 18.5), new Vec3(ox + 48.5, floor + 8, oz + 47.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("on the floor, looking at the cut where the hill was"));
		Vec3 near = new Vec3(ox - 18.5, floor + 1.7, door.getZ() + 4.5);
		Vec3 doorAt = Vec3.atBottomCenterOf(door).add(1.0, 1.4, 0);
		views.add(Gallery.View.looking("near", near, doorAt, Gallery.NOON, Gallery.Sky.CLEAR, false).noted("on the floor 15 in front of the doorway, a little aside, noon"));
		views.add(Gallery.View.looking("near_dusk", near, doorAt, Gallery.DUSK, Gallery.Sky.DUSK, false).noted("the same at dusk"));
		views.add(Gallery.View.looking("near_night", near, doorAt, Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("the same at midnight"));
		views.add(Gallery.View.looking("near_fog", near, doorAt, Gallery.NIGHT, Gallery.Sky.FOG_NIGHT, false).noted("the same, a fog night at full"));
		Vec3 mouth = Vec3.atBottomCenterOf(door).add(-5.0, 1.7, 0);
		views.add(Gallery.View.looking("mouth", mouth, Vec3.atBottomCenterOf(door).add(4.0, 1.4, 0), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("5 in front of the doorway, looking in, noon"));
		views.add(Gallery.View.looking("mouth_night", mouth, Vec3.atBottomCenterOf(door).add(4.0, 1.4, 0), Gallery.NIGHT, Gallery.Sky.CLEAR, false)
				.noted("the same at midnight"));
		Vec3 inside = Vec3.atBottomCenterOf(door).add(0.0, 1.6, 0);
		views.add(Gallery.View.looking("in_1", inside, Vec3.atBottomCenterOf(door).add(6.0, 1.8, 0), Gallery.NIGHT, Gallery.Sky.CLEAR, false)
				.noted("in the doorway, looking at the back wall, midnight, true light"));
		BlockPos back = shape.house().pos(1, 1, -5);
		views.add(Gallery.View.looking("in_2", Vec3.atBottomCenterOf(back).add(0, 1.6, 0), Vec3.atBottomCenterOf(door).add(-6.0, 1.0, 0), Gallery.NIGHT,
				Gallery.Sky.CLEAR, false).noted("from the back corner, out through the doorway, midnight"));
		views.add(Gallery.View.looking("near_3", new Vec3(ox - 44.5, floor + 1.7, oz + 40.5), middle, Gallery.NIGHT, Gallery.Sky.CLEAR, false)
				.noted("from the floor's south-west corner at midnight: the cows on the dark floor, the lit building"));

		BoundingBox floorBox = new BoundingBox(ox - side / 2, floor, oz - side / 2, ox + side / 2 - 1, floor + 4, oz + side / 2 - 1);
		Gallery.View frameCam = Gallery.View.looking("f_1", new Vec3(ox - 30.5, floor + 2.4, oz - 22.5), middle, Gallery.DUSK, Gallery.Sky.DUSK, false);
		Gallery.Frames turn = new Gallery.Frames("f_1", 10, 10, frameCam, lvl -> {
			List<Animal> cows = new ArrayList<>(GalleryProps.staged(lvl, floorBox, EntityTypes.COW));
			FloorAnswers.LIVE.holdLater(lvl.getServer(), built.place(), cows, 400, 25);
		}).noted("from the floor at dusk: the cows wander, then all turn to face the building");
		Gallery.View nightCam = Gallery.View.looking("f_2", new Vec3(ox - 21.5, floor + 1.4, oz - 6.5), middle.add(0, 0.5, 0), Gallery.NIGHT,
				Gallery.Sky.CLEAR, false);
		Gallery.Frames turned = new Gallery.Frames("f_2", 3, 30, nightCam, lvl -> {
			List<Animal> cows = new ArrayList<>(GalleryProps.staged(lvl, floorBox, EntityTypes.COW));
			FloorAnswers.LIVE.holdLater(lvl.getServer(), built.place(), cows, 400, 1);
		}).noted("at midnight, low on the floor 20 off the doorway: every cow faces the lit building");
		// The error sign (D-110): 64, 128 and 200 out toward the origin from the doorway, noon and midnight.
		views.addAll(Gallery.signViews(level, shape.sign(), Direction.WEST, 4.0));
		return new Gallery.Placed(built.box(), views, List.of(turn, turned));
	}

	/**
	 * {@code 20n} (R3-3): the floor at midnight. {@code n200}, {@code n128} (the sign views), {@code near} (on the floor
	 * 15 off the doorway); the frames {@code 20b}: from beside the building looking across the floor, the torches gone,
	 * the cows turned to the dark doorway, the floor's light going chunk by chunk from the far edge toward the house (one
	 * every 2 s here, 4 s in the game).
	 */
	private static Gallery.Placed night(ServerLevel level, Live.Result built, FloorShape shape, int side, int floor) {
		List<Gallery.View> views = new ArrayList<>();
		for (Gallery.View v : Gallery.signViews(level, shape.sign(), Direction.WEST, 4.0)) {
			if (v.name().equals("n200") || v.name().equals("n128")) {
				views.add(v);
			}
		}
		BlockPos door = shape.entrance();
		Vec3 near = new Vec3(door.getX() - 15.5, floor + 1.7, door.getZ() + 4.5);
		Vec3 doorAt = Vec3.atBottomCenterOf(door).add(1.0, 1.4, 0);
		views.add(Gallery.View.looking("near", near, doorAt, Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("on the floor 15 in front of the doorway, midnight"));
		BoundingBox sq = shape.square();
		BoundingBox floorBox = new BoundingBox(sq.minX(), floor, sq.minZ(), sq.maxX(), floor + 4, sq.maxZ());
		// FIX-R3-B (R3A-3): at the game's pace (a ring every g = 7 s), frames 8 s apart from the doorway along the floor's
		// axis, and one from 40 out looking back at the house once the dark has come most of the way.
		Sc20Config config = new Sc20Config();
		int ring = (int) Math.round(config.darkRingSeconds * 20);
		Vec3 eye = Vec3.atBottomCenterOf(door).add(-1.5, 1.7, 0.5);
		Vec3 along = new Vec3(sq.minX() + 0.5, floor + 1.2, door.getZ() + 0.5);
		Gallery.View cam = Gallery.View.looking("20b", eye, along, Gallery.NIGHT, Gallery.Sky.CLEAR, false);
		Gallery.Frames dark = new Gallery.Frames("20b", 8, 160, cam, lvl -> {
			List<Animal> cows = new ArrayList<>(GalleryProps.staged(lvl, floorBox, EntityTypes.COW));
			FloorAnswers.stageDark(lvl, built.place(), cows, ring, BlockPos.containing(eye));
		}).noted("from the doorway along the floor at midnight, frames 8 s apart at the game's pace (a ring every " + config.darkRingSeconds
				+ " s): the torches gone, the cows turned to the dark doorway, the dark coming in from every edge a ring at a time (staged without the view rule: what a player sees each time they look along the floor)");
		Vec3 back = Vec3.atBottomCenterOf(door).add(-40.5, 1.7, 3.0);
		Gallery.View backCam = Gallery.View.looking("20b_back", back, Vec3.atBottomCenterOf(door).add(2.0, 1.5, 0), Gallery.NIGHT, Gallery.Sky.CLEAR, false);
		Gallery.Frames looking = new Gallery.Frames("20b_back", 3, 160, backCam, lvl -> {
			List<Animal> cows = new ArrayList<>(GalleryProps.staged(lvl, floorBox, EntityTypes.COW));
			FloorAnswers.stageDark(lvl, built.place(), cows, ring, BlockPos.containing(back));
		}).noted("from 40 out on the floor looking back at the house, frames 8 s apart at the game's pace: the rings behind the house going");
		return new Gallery.Placed(built.box(), views, List.of(dark, looking));
	}

	/** Trees on the hill and the flat; those standing where the square goes are taken by it. */
	private static void trees(ServerLevel level, int ox, int oz) {
		String[] kinds = {"minecraft:oak", "minecraft:birch", "minecraft:fancy_oak", "minecraft:oak", "minecraft:birch"};
		int i = 0;
		for (int k = 0; k < 90; k++) {
			double a = Math.sin(k * 12.9898 + 4.1) * 43758.5453;
			double b = Math.sin(k * 78.233 + 1.7) * 12345.6789;
			int x = ox - 110 + (int) ((a - Math.floor(a)) * 250);
			int z = oz - 110 + (int) ((b - Math.floor(b)) * 240);
			// Keep the cameras' lines clear: nothing between the far rise and the square's west side.
			if (x < ox - 50 && Math.abs(z - oz) < 56) {
				continue;
			}
			tree(level, x, z, kinds[i++ % kinds.length]);
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
