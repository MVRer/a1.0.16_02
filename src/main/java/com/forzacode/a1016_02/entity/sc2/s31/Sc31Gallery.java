package com.forzacode.a1016_02.entity.sc2.s31;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.entity.FigureApi;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.Variant;
import com.forzacode.a1016_02.entity.Watchers;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;
import com.forzacode.a1016_02.world.sc2.s16.S16;
import com.forzacode.a1016_02.world.sc2.s18.HollowShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * 31's exhibit (dev only; docs/SELF_CREATED_2.md, "The gallery"): a {@code 16} (base 95, the way in east, the far cameras
 * looking west into the sunset) with him on its top at dusk ({@code apex_dusk} from 180, {@code apex_mid} from 110,
 * {@code apex_near} from 96, the nearest 31 comes), and, {@link #HOLLOW_AWAY} to the south, a real {@code 18} (exhibit 18's
 * own mountain and place, {@code Sc18Gallery.eighteen}; SC2-9 moved the shot there from the carved dome SC2-6 staged while
 * the hollow was not merged): the camera in the nave 21 from the square's middle with a torch set down beside it and one
 * among the square's front stems, him half behind a stem where 31c's own spot finder puts him ({@code trunks} true light,
 * {@code trunks_survey} night vision). Each time-lapse puts him there through the feature's own spawn
 * ({@link FigureApi#spawnGiven}, nobody watching, for nobody). Should the hollow find no way in, the carved dome stands in.
 */
final class Sc31Gallery {
	static final String ID = "31";
	/** The hill with the carved dome, this far south of the exhibit's origin (the stand-in). */
	static final int HILL_AWAY = 260;
	/** Exhibit 18's mountain and place, this far south of the exhibit's origin (its reach is 170: clear of the {@code 16}). */
	static final int HOLLOW_AWAY = 320;
	/** The camera this far along the nave from the square's middle, toward the way in. */
	static final int NAVE_OUT = 21;
	/** The square's torch this far along from its middle, toward the camera (the square reaches {@code HollowShape.GROVE}). */
	static final int LIT_ROW = 3;
	/** The dome's radius and height, and how far from its middle the camera stands. */
	static final int DOME = 24;
	static final int DOME_HIGH = 26;
	static final int CAMERA_OUT = 21;
	/** Late dusk (R11-4's window): the sky behind the top still bright, the mass already dark. */
	static final long LATE_DUSK = 13_100L;

	private Sc31Gallery() {
	}

	static void register() {
		if (Gallery.active()) {
			Gallery.register(new Gallery.Exhibit(ID, "31", Sc31Gallery::place));
		}
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int variant = PyramidShape.variant(1, false, false, false);
		Live.Result built = Live.place(level, S16.KIND, new Start(origin, Direction.EAST, variant, 0x31A1L));
		BlockPos apex = built.place().apex().orElse(origin.above(PyramidShape.height(1)));
		// R11-4: the band he stands in now is 56 to 110 (64 to 85 best), the sunset behind the top (the cameras east of it, looking west).
		Vec3 farEye = Gallery.groundEye(level, origin.getX() + 110.5, origin.getZ() + 0.5);
		Vec3 midEye = Gallery.groundEye(level, origin.getX() + 88.5, origin.getZ() + 20.5);
		Vec3 nearEye = Gallery.groundEye(level, origin.getX() + 68.5, origin.getZ() - 16.5);
		// He stands where the live rule puts him for each camera: at the flat top's edge toward it (Apex.stand).
		Vec3 farFeet = Vec3.atBottomCenterOf(Apex.stand(level, apex, farEye.x, farEye.z).above());
		Vec3 midFeet = Vec3.atBottomCenterOf(Apex.stand(level, apex, midEye.x, midEye.z).above());
		Vec3 nearFeet = Vec3.atBottomCenterOf(Apex.stand(level, apex, nearEye.x, nearEye.z).above());
		Vec3 aim = Vec3.atBottomCenterOf(apex.above()).add(0.0, 1.0, 0.0);
		Gallery.View far = Gallery.View.looking("apex_dusk", farEye, aim, Gallery.DUSK, Gallery.Sky.DUSK, false);
		Gallery.View mid = Gallery.View.looking("apex_mid", midEye, aim, Gallery.DUSK, Gallery.Sky.DUSK, false);
		Gallery.View near = Gallery.View.looking("apex_near", nearEye, aim, Gallery.DUSK, Gallery.Sky.DUSK, false);
		Gallery.View late = Gallery.View.looking("apex_late", nearEye, aim, LATE_DUSK, Gallery.Sky.DUSK, false);
		Gallery.View lateMid = Gallery.View.looking("apex_late_mid", midEye, aim, LATE_DUSK, Gallery.Sky.DUSK, false);

		Room room = hollow(level, origin.offset(0, 0, HOLLOW_AWAY));
		if (room == null) {
			A1016_02.LOGGER.warn("[a1016] gallery 31: no way in for the 18; the carved dome is used");
			room = room(level, origin.offset(0, 0, HILL_AWAY));
		}
		Room finalRoom = room;
		Gallery.View trunks = Gallery.View.looking("trunks", room.eye(), room.aim(), Gallery.NIGHT, Gallery.Sky.CLEAR, false);
		Gallery.View survey = trunks.named("trunks_survey").withSurvey(true);
		List<Gallery.Frames> frames = List.of(
				new Gallery.Frames("apex_dusk", 3, 20, far, l -> top(l, farFeet, farEye)).noted("dusk, 110 off: him still on the top, facing the camera"),
				new Gallery.Frames("apex_mid", 2, 20, mid, l -> top(l, midFeet, midEye)).noted("dusk, 90 off and aside: the same"),
				new Gallery.Frames("apex_near", 2, 20, near, l -> top(l, nearFeet, nearEye)).noted("dusk, 70 off: where he reads best, at the top's edge toward the camera"),
				new Gallery.Frames("apex_near_middle", 2, 20, near.named("apex_near_middle"), l -> top(l, Vec3.atBottomCenterOf(apex.above()), nearEye))
						.noted("the same with him in the top's middle (before R11-4's edge): the rim hides his legs"),
				new Gallery.Frames("apex_late", 2, 20, late, l -> top(l, nearFeet, nearEye)).noted("late dusk (13100), 70 off: the sky behind brighter than the mass"),
				new Gallery.Frames("apex_late_mid", 2, 20, lateMid, l -> top(l, midFeet, midEye)).noted("late dusk (13100), 90 off and aside"),
				new Gallery.Frames("trunks", 3, 20, trunks, l -> behind(l, finalRoom)).noted("inside an 18, down the nave toward the square, true light"
						+ " with a torch set down beside the camera and one among the front stems: half of him behind a bare stem where the light gives out"),
				new Gallery.Frames("trunks_survey", 2, 20, survey, l -> behind(l, finalRoom)).noted("the same with night vision: the nave, the square's"
						+ " stems under the stepped ceiling, him"));
		return new Gallery.Placed(built.box(), List.of(), frames);
	}

	/** Him on the top, facing the camera; any figure out first goes (one him), and the one put there is for nobody. */
	private static void top(ServerLevel level, Vec3 feet, Vec3 eye) {
		FigureApi.clear(level.getServer());
		FireResult result = FigureApi.spawnGiven(level, null, Variant.APEX, feet, HimEntity.yawToward(feet, eye), null, new Watchers(List.of()), null)
				.result();
		if (result != FireResult.FIRED) {
			A1016_02.LOGGER.warn("[a1016] gallery 31: a ({})", result);
		}
	}

	/** Him half behind a trunk where 31c's spot finder puts him for the camera (the gallery's player, at the camera). */
	private static void behind(ServerLevel level, Room room) {
		FigureApi.clear(level.getServer());
		Optional<ServerPlayer> camera = level.players().stream().findFirst();
		Optional<InsideTrunks.Spot> spot = camera.flatMap(p -> InsideTrunks.spot(p, room.place(), new Watchers(List.of()), feet -> true));
		if (spot.isEmpty()) {
			List<BlockPos> trunks = InsideTrunks.trunks(level, room.place().anchor());
			A1016_02.LOGGER.warn("[a1016] gallery 31: c (none: {} columns, camera {})", trunks.size(), camera.map(p -> p.blockPosition().toShortString()).orElse("-"));
			return;
		}
		InsideTrunks.Spot s = spot.get();
		FigureApi.spawnGiven(level, null, Variant.INSIDE_TRUNKS, s.feet(), HimEntity.yawToward(s.feet(), room.eye()), s.trunk(), new Watchers(List.of()), null);
	}

	/** Where 31c is shot: the camera's eye, where it looks, the place as 31c reads it. */
	private record Room(Vec3 eye, Vec3 aim, Remains.Place place) {
	}

	/**
	 * Exhibit 18's mountain and place at {@code center}: the camera {@link #NAVE_OUT} along the nave from the square's
	 * middle (nearer if the nave is shorter), looking at the square; a torch set down beside the camera and one a few
	 * cells into the square from its front ({@link #LIT_ROW}). Null if the shape found no way in.
	 */
	private static Room hollow(ServerLevel level, BlockPos center) {
		Live.Result built = com.forzacode.a1016_02.world.sc2.s18.Sc18Gallery.eighteen(level, center);
		if (!(built.shape() instanceof HollowShape shape) || !shape.valid()) {
			return null;
		}
		int yf = shape.floorY();
		int out = NAVE_OUT;
		while (out > HollowShape.GROVE + 4 && !open(shape, yf, out)) {
			out--;
		}
		Vec3 eye = point(shape, 0.5, yf + 1 + 1.62, out + 0.5);
		Vec3 aim = point(shape, 0.5, yf + 3.0, 0.5);
		GalleryProps.set(level, shape.at(1, yf + 1, out), Blocks.TORCH.defaultBlockState());
		// The second torch a few cells into the square from its front (on the first free cell of the floor there), so the
		// light gives out among the stems behind it: where 31c's spot finder puts him (light 3 to 7 at his eyes).
		for (int a : new int[] {1, -1, 2, -2, 3, -3, 0}) {
			BlockPos torch = shape.at(a, yf + 1, LIT_ROW);
			if (level.getBlockState(torch).isAir() && level.getBlockState(torch.below()).isFaceSturdy(level, torch.below(), Direction.UP)) {
				GalleryProps.set(level, torch, Blocks.TORCH.defaultBlockState());
				break;
			}
		}
		Live.settleLight(level, BoundingBox.fromCorners(shape.at(-HollowShape.GROVE - 8, yf - 2, -HollowShape.GROVE - 8), shape.at(HollowShape.GROVE + 8,
				yf + 30, out + 4)));
		return new Room(eye, aim, built.place());
	}

	/** The cells a camera stands in, {@code b} along the nave: open, over the floor. */
	private static boolean open(HollowShape shape, int yf, int b) {
		for (int a = 0; a <= 1; a++) {
			BlockPos feet = shape.at(a, yf + 1, b);
			if (!shape.taken(feet.getX(), feet.getY(), feet.getZ()) || !shape.taken(feet.getX(), feet.getY() + 1, feet.getZ())) {
				return false;
			}
		}
		return true;
	}

	/** A point in the place's frame: {@code a} across, {@code b} along from the square's middle (as exhibit 18's own). */
	private static Vec3 point(HollowShape shape, double a, double y, double b) {
		BlockPos origin = shape.at(0, 0, 0);
		Direction facing = shape.facing();
		Direction right = facing.getClockWise();
		return new Vec3(origin.getX() + 0.5 + right.getStepX() * (a - 0.5) + facing.getStepX() * (b - 0.5), y,
				origin.getZ() + 0.5 + right.getStepZ() * (a - 0.5) + facing.getStepZ() * (b - 0.5));
	}

	/**
	 * A hill (radius 50, 36 high) with a dome of air carved in it ({@link #DOME} round, {@link #DOME_HIGH} high), a stone floor,
	 * a 15 x 15 patch of coarse dirt and podzol in the middle with eleven bare oak trunks 5 to 10 tall, a way in from the north,
	 * two torches on the floor where a player coming in would set them down, the camera {@link #CAMERA_OUT} from the middle.
	 */
	private static Room room(ServerLevel level, BlockPos center) {
		GalleryProps.hill(level, center, DOME + 26, DOME_HIGH + 10);
		int floor = center.getY() - 1;
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -DOME; dx <= DOME; dx++) {
			for (int dz = -DOME; dz <= DOME; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz) / DOME;
				if (r > 1.0) {
					continue;
				}
				int height = (int) Math.round(DOME_HIGH * Math.sqrt(1.0 - r * r));
				for (int y = 1; y <= Math.max(3, height); y++) {
					GalleryProps.set(level, new BlockPos(center.getX() + dx, floor + y, center.getZ() + dz), air);
				}
				GalleryProps.set(level, new BlockPos(center.getX() + dx, floor, center.getZ() + dz), Blocks.STONE.defaultBlockState());
			}
		}
		// The way in from the north: a 2 x 2 tunnel through the hill, unlit.
		GalleryProps.fill(level, new BoundingBox(center.getX(), floor + 1, center.getZ() - DOME - 28, center.getX() + 1, floor + 2, center.getZ() - DOME),
				air);
		RandomSource random = RandomSource.create(0x31C3L);
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				BlockState dirt = random.nextInt(3) == 0 ? Blocks.PODZOL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
				GalleryProps.set(level, new BlockPos(center.getX() + dx, floor, center.getZ() + dz), dirt);
			}
		}
		int[][] at = {{-5, -4}, {-1, -6}, {3, -5}, {6, -1}, {-6, 1}, {-2, -1}, {2, 1}, {5, 4}, {-4, 5}, {0, 5}, {1, -2}};
		for (int[] a : at) {
			int tall = 5 + random.nextInt(6);
			BlockPos base = new BlockPos(center.getX() + a[0], floor + 1, center.getZ() + a[1]);
			for (int y = 0; y < tall; y++) {
				GalleryProps.set(level, base.above(y), Blocks.OAK_LOG.defaultBlockState());
			}
		}
		Vec3 eye = new Vec3(center.getX() + 0.5, floor + 1 + 1.62, center.getZ() - CAMERA_OUT + 0.5);
		GalleryProps.set(level, new BlockPos(center.getX() + 1, floor + 1, center.getZ() - CAMERA_OUT + 1), Blocks.TORCH.defaultBlockState());
		GalleryProps.set(level, new BlockPos(center.getX() + 1, floor + 1, center.getZ() - 8), Blocks.TORCH.defaultBlockState());
		Vec3 aim = new Vec3(center.getX() + 0.5, floor + 2.5, center.getZ() + 0.5);
		BoundingBox box = new BoundingBox(center.getX() - DOME, floor, center.getZ() - DOME, center.getX() + DOME, floor + DOME_HIGH, center.getZ() + DOME);
		BlockPos anchor = new BlockPos(center.getX(), floor + 1, center.getZ());
		Remains.Place place = new Remains.Place(Remains.Kind.HOLLOW, Level.OVERWORLD, box, anchor, new BlockPos(center.getX(), floor + 1,
				center.getZ() - DOME - 28), Direction.NORTH, Optional.empty(), 0x31C3L, 0);
		Live.settleLight(level, box.inflatedBy(30));
		return new Room(eye, aim, place);
	}
}
