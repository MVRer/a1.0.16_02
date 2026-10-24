package com.forzacode.a1016_02.atmosphere.sc2.s22;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's exhibit {@code 22} (development only): a field at dusk with a few trees, and 22's own file run on nine
 * animals of five kinds from 30 west of the middle toward a point far to the east (staged: nobody is passed, nothing is
 * moved inside). Frames: from the side 22 off the line, and from above.
 */
final class S22Gallery {
	/** The file, the leader first. */
	static final List<EntityType<? extends Mob>> FILE = List.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.PIG, EntityTypes.HORSE, EntityTypes.SHEEP,
			EntityTypes.CHICKEN, EntityTypes.COW, EntityTypes.PIG, EntityTypes.SHEEP);

	private S22Gallery() {
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();
		BoundingBox box = new BoundingBox(x0 - 40, y0 - 1, z0 - 12, x0 + 40, y0 + 8, z0 + 12);
		GalleryProps.clear(level, new BoundingBox(x0 - 70, y0, z0 - 50, x0 + 70, y0 + 20, z0 + 50));
		GalleryProps.tree(level, new BlockPos(x0 - 12, y0, z0 - 15), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 16, y0, z0 - 22), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 30, y0, z0 - 9), "minecraft:birch");
		GalleryProps.tree(level, new BlockPos(x0 - 34, y0, z0 - 6), "minecraft:oak");
		Line line = line(x0, y0, z0);
		List<Vec3> spots = line.file(FILE.size(), Sc22Config.SPACING);
		for (int i = 0; i < FILE.size(); i++) {
			List<? extends Mob> one = GalleryProps.summon(level, FILE.get(i), BlockPos.containing(spots.get(i)), 1);
			Vec3 s = spots.get(i);
			one.forEach(m -> m.snapTo(s.x, s.y, s.z + (m.getRandom().nextDouble() - 0.5) * 6.0, m.getRandom().nextFloat() * 360.0F, 0.0F));
		}
		BoundingBox pen = box.inflatedBy(60, 8, 40);
		Vec3 mid = new Vec3(x0 + 0.5, y0 + 0.8, z0 + 0.5);
		// R11-13: from where a player is passed (28 off the line, at eye height), the file coming at them from about 45 degrees
		// ahead, so the line recedes and reads as a line.
		Gallery.View side = Gallery.View.looking("side", new Vec3(x0 + 0.5 + 6, y0 + Gallery.EYE, z0 + 0.5 + 28), mid.add(-22, -0.4, 0), Gallery.DUSK,
				Gallery.Sky.DUSK, false).noted("from 28 off the line at dusk, at eye height: the file coming from 45 degrees ahead and passing");
		Gallery.View top = Gallery.View.looking("top", new Vec3(x0 + 0.5 - 6, y0 + 22, z0 + 0.5 + 5), new Vec3(x0 + 0.5 - 6, y0, z0 + 0.5),
				Gallery.DUSK, Gallery.Sky.DUSK, false).noted("from above the line at dusk");
		List<Gallery.Frames> frames = List.of(
				new Gallery.Frames("side", 26, 20, side, l -> start(l, pen, x0, y0, z0)).noted("nine animals of five kinds walk past in one file"),
				new Gallery.Frames("top", 16, 20, top, l -> start(l, pen, x0, y0, z0)).noted("the same from above"));
		return new Gallery.Placed(box, List.of(), frames);
	}

	/** The staged way: from 34 west of the middle toward a point 400 east of it, along z = the middle's. */
	static Line line(int x0, int y0, int z0) {
		Vec3 start = new Vec3(x0 + 0.5 - 26, y0, z0 + 0.5);
		Vec3 end = new Vec3(x0 + 0.5 + 400, y0, z0 + 0.5);
		return new Line(start, end, new Vec3(1, 0, 0));
	}

	/** The animals found again, put back on their spots in file order, and walked. */
	private static void start(ServerLevel level, BoundingBox pen, int x0, int y0, int z0) {
		Processions.LIVE.clear();
		Line line = line(x0, y0, z0);
		List<Vec3> spots = line.file(FILE.size(), Sc22Config.SPACING);
		List<Mob> file = new ArrayList<>();
		List<Mob> pool = new ArrayList<>();
		for (EntityType<? extends Mob> type : List.copyOf(new java.util.LinkedHashSet<>(FILE))) {
			pool.addAll(GalleryProps.staged(level, pen, type));
		}
		for (int i = 0; i < FILE.size(); i++) {
			EntityType<? extends Mob> type = FILE.get(i);
			Vec3 spot = spots.get(i);
			Mob mob = pool.stream().filter(m -> m.getType() == type).min(Comparator.comparingDouble(m -> m.position().distanceToSqr(spot))).orElse(null);
			if (mob == null) {
				continue;
			}
			pool.remove(mob);
			file.add(mob);
		}
		// The card's own order: the biggest and the smallest in turn (R11-13).
		file = ProcessionCard.bigAndSmall(file);
		for (int i = 0; i < file.size(); i++) {
			Vec3 spot = spots.get(i);
			file.get(i).snapTo(spot.x, spot.y, spot.z, -90.0F, 0.0F);
		}
		Processions.LIVE.start(level, line, file, null, List.of(), 20 * 120, true);
	}
}
