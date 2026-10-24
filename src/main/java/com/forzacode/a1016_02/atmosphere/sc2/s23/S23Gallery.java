package com.forzacode.a1016_02.atmosphere.sc2.s23;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's exhibit {@code 23} (development only): a field at nightfall with six cows grazing, three zombies coming
 * at the camera and two skeletons, around the camera; 23's own hold run on them two seconds in (staged: from the
 * camera's eye toward a point far ahead and to the right), for its eight seconds, then let go. One time-lapse from the
 * camera: before, during, after.
 */
final class S23Gallery {
	/** The hold starts this many ticks into the frames. */
	static final int DELAY = 40;
	static final int TIME = 13_000;
	/** Where the mobs stand (x to the camera's right, then ahead of it): six cows grazing, three zombies, two skeletons. */
	static final double[][] COWS = {{-6, 7}, {-9, 13}, {5, 8}, {10, 12}, {-2, 18}, {13, 19}};
	static final double[][] ZOMBIES = {{-3, 14}, {2, 16}, {7, 17}};
	static final double[][] SKELETONS = {{-8, 17}, {9, 15}};
	/** The bearing, degrees right of the camera's look. */
	static final double BEARING_DEGREES = 70.0;

	private S23Gallery() {
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();
		BoundingBox box = new BoundingBox(x0 - 20, y0 - 1, z0 - 30, x0 + 20, y0 + 8, z0 + 14);
		GalleryProps.clear(level, new BoundingBox(x0 - 48, y0, z0 - 60, x0 + 48, y0 + 20, z0 + 30));
		GalleryProps.tree(level, new BlockPos(x0 - 14, y0, z0 - 26), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 9, y0, z0 - 34), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 22, y0, z0 - 12), "minecraft:birch");
		RandomSource random = RandomSource.create(seed);
		Vec3 eye = new Vec3(x0 + 0.5, y0 + Gallery.EYE, z0 + 10.5);
		Vec3 look = new Vec3(x0 + 0.5, y0 + 0.9, z0 - 2.5);
		// Around the camera, in front of it (x right, then how far ahead): six cows grazing, three zombies coming at
		// the camera, two skeletons further back.
		place(level, EntityTypes.COW, COWS, eye, random);
		place(level, EntityTypes.ZOMBIE, ZOMBIES, eye, random);
		place(level, EntityTypes.SKELETON, SKELETONS, eye, random);
		BoundingBox pen = box.inflatedBy(24, 8, 24);
		Gallery.View camera = Gallery.View.looking("near", eye, look, TIME, Gallery.Sky.DUSK, false).noted("a field at nightfall, from the ground");
		Gallery.Frames frames = new Gallery.Frames("near", 26, 10, camera, l -> start(l, pen, eye))
				.noted("every 10 ticks: two seconds before, the eight seconds, three after");
		return new Gallery.Placed(box, List.of(), List.of(frames));
	}

	/** Mobs at these spots (x to the camera's right, then ahead of it; the camera looks north), each facing its own way. */
	private static <T extends Mob> void place(ServerLevel level, EntityType<T> type, double[][] spots, Vec3 eye, RandomSource random) {
		List<T> mobs = GalleryProps.summon(level, type, BlockPos.containing(eye.x, eye.y - Gallery.EYE, eye.z - 10), spots.length);
		for (int i = 0; i < mobs.size(); i++) {
			mobs.get(i).snapTo(eye.x + spots[i][0], eye.y - Gallery.EYE, eye.z - spots[i][1], random.nextFloat() * 360.0F, 0.0F);
		}
	}

	private static void start(ServerLevel level, BoundingBox pen, Vec3 eye) {
		List<Mob> all = new ArrayList<>();
		RandomSource random = RandomSource.create(23L);
		all.addAll(back(GalleryProps.staged(level, pen, EntityTypes.COW), COWS, eye, random));
		all.addAll(back(GalleryProps.staged(level, pen, EntityTypes.ZOMBIE), ZOMBIES, eye, random));
		all.addAll(back(GalleryProps.staged(level, pen, EntityTypes.SKELETON), SKELETONS, eye, random));
		// The zombies come at the camera until the hold (the staging's own walk).
		for (Mob zombie : GalleryProps.staged(level, pen, EntityTypes.ZOMBIE)) {
			MobTamperImpl.INSTANCE.steer(zombie, new Vec3(eye.x, eye.y - Gallery.EYE, eye.z), 1.0, DELAY);
		}
		// Far off to the right and a little away from the camera: they turn from it, every one the same way.
		double turn = Math.toRadians(BEARING_DEGREES);
		Vec3 point = HeldBreath.facePoint(eye, eye.add(Math.sin(turn) * 100, 0, -Math.cos(turn) * 100), Sc23Config.FACE_DISTANCE);
		HeldBreath.LIVE.holdLater(level, all, point, (int) Math.round(Sc23Config.get().seconds * 20), DELAY, eye);
	}

	/** The mobs found again, each put back on its spot (they wandered while the camera's chunks loaded). */
	private static <T extends Mob> List<T> back(List<T> mobs, double[][] spots, Vec3 eye, RandomSource random) {
		for (int i = 0; i < mobs.size() && i < spots.length; i++) {
			mobs.get(i).snapTo(eye.x + spots[i][0], eye.y - Gallery.EYE, eye.z - spots[i][1], random.nextFloat() * 360.0F, 0.0F);
		}
		return mobs;
	}
}
