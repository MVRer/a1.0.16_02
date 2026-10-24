package com.forzacode.a1016_02.entity.sc2.s27;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.entity.FogEdge;
import com.forzacode.a1016_02.entity.SpotFinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * 27's exhibit (dev only; docs/SELF_CREATED_2.md, "The gallery"): a field, a low rise at the edge of a stand of trees, and the
 * camera on the field about 50 blocks off. Each time-lapse stages the copy through the feature's own payload and client
 * ({@link Sc27#stage}): the dev stand-in, the camera's own profile shown as "Friend". {@code dusk}, {@code night} and
 * {@code fog} (the copy at 0.65 of the drawn fog end) stand still; {@code walk} is looked at for a second, then turns and
 * walks off into the trees.
 */
final class Sc27Gallery {
	static final String ID = "27";
	/** Where the rise is from the exhibit's origin, along +x. */
	static final int RISE = 58;
	/** The copy's spot, along +x from the camera, for the still shots. */
	static final double STAND = 48.0;
	/** The near end of the band. */
	static final double NEAR = 40.0;

	private Sc27Gallery() {
	}

	static void register() {
		if (Gallery.active()) {
			Gallery.register(new Gallery.Exhibit(ID, "27", Sc27Gallery::place));
		}
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		BoundingBox box = new BoundingBox(origin.getX() - 8, origin.getY() - 1, origin.getZ() - 40, origin.getX() + 100, origin.getY() + 24,
				origin.getZ() + 40);
		GalleryProps.clear(level, new BoundingBox(box.minX(), origin.getY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
		GalleryProps.hill(level, origin.offset(RISE, 0, 0), 26, 5);
		String[] kinds = {"minecraft:oak", "minecraft:birch", "minecraft:oak", "minecraft:fancy_oak", "minecraft:oak", "minecraft:birch", "minecraft:oak",
				"minecraft:oak", "minecraft:birch"};
		int[][] trees = {{62, -9}, {66, 4}, {70, -3}, {64, 12}, {74, 8}, {72, -12}, {79, 0}, {78, 14}, {83, -7}};
		for (int i = 0; i < trees.length; i++) {
			int x = origin.getX() + trees[i][0];
			int z = origin.getZ() + trees[i][1];
			GalleryProps.tree(level, new BlockPos(x, Gallery.ground(level, x, z), z), kinds[i]);
		}
		Vec3 eye = Gallery.groundEye(level, origin.getX() + 0.5, origin.getZ() + 2.5);
		Vec3 standAt = stand(level, origin, STAND);
		Vec3 aim = standAt.add(0.0, 1.0, 0.0);
		Gallery.View dusk = Gallery.View.looking("dusk", eye, aim, Gallery.DUSK, Gallery.Sky.DUSK, false);
		Gallery.View night = Gallery.View.looking("night_far", eye, aim, Gallery.NIGHT, Gallery.Sky.CLEAR, false);
		Gallery.View fog = Gallery.View.looking("fog", eye, aim, Gallery.NIGHT, Gallery.Sky.FOG_NIGHT, false);
		Gallery.View walk = Gallery.View.looking("walk", eye, aim, Gallery.NIGHT, Gallery.Sky.CLEAR, false);
		Gallery.View day = Gallery.View.looking("near_day", eye.add(28.0, 0.0, 0.0), aim, Gallery.NOON, Gallery.Sky.CLEAR, false);
		Vec3 nearAt = stand(level, origin, NEAR);
		Gallery.View nightNear = Gallery.View.looking("night_near", eye, nearAt.add(0.0, 1.0, 0.0), Gallery.NIGHT, Gallery.Sky.CLEAR, false);
		Vec3 cameraFeet = eye.subtract(0.0, Gallery.EYE, 0.0);
		List<Gallery.Frames> frames = List.of(
				new Gallery.Frames("night_far", 3, 30, night, l -> show(l, cameraFeet, standAt, 900))
						.noted("midnight, clear: the copy 48 off on a rise at the edge of trees, standing, facing the camera"),
				new Gallery.Frames("night_near", 2, 30, nightNear, l -> show(l, cameraFeet, nearAt, 900))
						.noted("midnight, clear: the copy 40 off, the near end of the band"),
				new Gallery.Frames("dusk", 2, 30, dusk, l -> show(l, cameraFeet, standAt, 900)).noted("the same at dusk"),
				new Gallery.Frames("fog", 3, 30, fog, l -> show(l, cameraFeet, fogSpot(l, origin, cameraFeet), 900))
						.noted("a fog night at full: the copy at 0.65 of the drawn fog end"),
				new Gallery.Frames("walk", 18, 8, walk, l -> show(l, cameraFeet, standAt, 20))
						.noted("midnight: looked at for a second, it turns and walks off into the trees"),
				new Gallery.Frames("near_day", 2, 30, day, l -> show(l, cameraFeet, standAt, 900))
						.noted("noon, 22 off: the copy's skin, hat layer, held item and name in full light (a check, never a moment it has)"));
		return new Gallery.Placed(box, List.of(), frames);
	}

	/** The ground {@code distance} out along +x from the origin's column (2.5 to the side, as the camera). */
	private static Vec3 stand(ServerLevel level, BlockPos origin, double distance) {
		double x = origin.getX() + 0.5 + distance;
		double z = origin.getZ() + 2.5;
		Vec3 feet = SpotFinder.standAt(level, x, z, EntityTypes.PLAYER.getDimensions());
		return feet != null ? feet : new Vec3(x, Gallery.ground(level, (int) Math.floor(x), (int) Math.floor(z)), z);
	}

	/** At 0.65 of the camera player's drawn fog end (their client's report), between 36 and 60. */
	private static Vec3 fogSpot(ServerLevel level, BlockPos origin, Vec3 cameraFeet) {
		Optional<ServerPlayer> camera = camera(level);
		double end = camera.map(p -> FogEdge.of(p).limit()).orElse(60.0);
		double d = Math.clamp(0.65 * end, 36.0, 60.0);
		return stand(level, origin, d + (cameraFeet.x - (origin.getX() + 0.5)));
	}

	private static Optional<ServerPlayer> camera(ServerLevel level) {
		return level.players().stream().findFirst();
	}

	/** Stages the copy at {@code feet}, facing the camera, walking off away from it once looked at {@code lookTicks}. */
	private static void show(ServerLevel level, Vec3 cameraFeet, Vec3 feet, int lookTicks) {
		camera(level).ifPresent(camera -> {
			Vec3 eye = camera.getEyePosition();
			float yaw = Sc27Rules.yawToward(feet.x, feet.z, eye.x, eye.z);
			float walkYaw = (float) Sc27Spot.wayOff(level, eye, feet, Double.NaN);
			Sc27.LIVE.stage(camera, feet, yaw, walkYaw, 20 * 40, lookTicks, List.of(new ItemStack(Items.IRON_PICKAXE)));
		});
	}
}
