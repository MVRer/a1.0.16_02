package com.forzacode.a1016_02.atmosphere.sc2.s21;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's exhibit {@code 21} (development only; docs/SELF_CREATED_2.md, "The gallery"): a grazing herd of sheep on
 * open grass with a few trees, and 21's own scene run on them ({@link Rings}, staged: nobody breaks it). Frames: from
 * above at dusk (the assembly and the way round), from the ground at night 30 off, and the next morning from the air
 * (the ground it bares, put down by the gallery out of turn).
 */
final class S21Gallery {
	static final int SHEEP = 13;
	/** The herd's loose spread (blocks either side) before it is used. */
	static final int SPREAD = 6;

	private S21Gallery() {
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();
		BoundingBox box = new BoundingBox(x0 - 14, y0 - 1, z0 - 14, x0 + 14, y0 + 8, z0 + 14);
		GalleryProps.clear(level, new BoundingBox(x0 - 40, y0, z0 - 40, x0 + 40, y0 + 20, z0 + 40));
		GalleryProps.tree(level, new BlockPos(x0 - 17, y0, z0 - 11), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 15, y0, z0 - 16), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 19, y0, z0 + 12), "minecraft:birch");
		GalleryProps.tree(level, new BlockPos(x0 - 12, y0, z0 + 20), "minecraft:oak");
		Vec3 herd = new Vec3(x0 + 2.5, y0, z0 - 1.5);
		List<Sheep> sheep = GalleryProps.summon(level, EntityTypes.SHEEP, BlockPos.containing(herd), SHEEP);
		scatter(sheep, herd, seed);
		Vec3 middle = new Vec3(x0 + 0.5, y0, z0 + 0.5);
		BoundingBox pen = box.inflatedBy(30, 8, 30);

		Gallery.View top = Gallery.View.looking("top", new Vec3(x0 + 0.5, y0 + 28, z0 + 4.5), middle, Gallery.DUSK, Gallery.Sky.DUSK, false)
				.noted("28 up over the middle at dusk");
		Gallery.View ground = Gallery.View.looking("ground", new Vec3(x0 + 0.5 - 30, y0 + Gallery.EYE, z0 + 0.5 + 6), middle.add(0, 0.6, 0),
				Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("from the ground 30 off at night");
		Gallery.View rise = Gallery.View.looking("rise", new Vec3(x0 + 0.5 - 13, y0 + 4.2, z0 + 0.5 + 7), middle.add(0, 0.4, 0),
				Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("from a small rise 15 off at night, a player's eye 3 up");
		Gallery.View dirt = Gallery.View.looking("aerial", new Vec3(x0 + 0.5 + 11, y0 + 13, z0 + 0.5 + 11), middle, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("the next morning from the air: the ground it leaves");
		List<Gallery.Frames> frames = List.of(
				new Gallery.Frames("top", 40, 20, top, l -> start(l, pen, herd, middle, seed)).noted("a grazing herd gathers and goes round, from above"),
				new Gallery.Frames("ground", 12, 20, ground, l -> keepGoing(l, pen, herd, middle, seed)).noted("the same, from the ground at night"),
				new Gallery.Frames("rise", 16, 20, rise, l -> keepGoing(l, pen, herd, middle, seed)).noted("the same, from a little above the ground at night"),
				new Gallery.Frames("aerial", 2, 20, dirt, l -> bare(l, pen, middle)).noted("the next morning"));
		return new Gallery.Placed(box, List.of(), frames);
	}

	/** The herd spread loosely round {@code herd} as if grazing, each facing its own way. */
	static void scatter(List<? extends net.minecraft.world.entity.Mob> mobs, Vec3 herd, long seed) {
		RandomSource random = RandomSource.create(seed);
		for (net.minecraft.world.entity.Mob mob : mobs) {
			double x = herd.x + (random.nextDouble() * 2 - 1) * SPREAD;
			double z = herd.z + (random.nextDouble() * 2 - 1) * SPREAD;
			mob.snapTo(x, herd.y, z, random.nextFloat() * 360.0F, 0.0F);
		}
	}

	private static void start(ServerLevel level, BoundingBox pen, Vec3 herd, Vec3 middle, long seed) {
		List<Sheep> sheep = GalleryProps.staged(level, pen, EntityTypes.SHEEP);
		scatter(sheep, herd, seed);
		Optional<Rings.Site> site = Rings.site(level, middle, sheep.size(), Sc21Config.get().spacing);
		site.ifPresent(s -> Rings.LIVE.start(level, s, sheep, List.of(), 180 * 20, true));
	}

	private static void keepGoing(ServerLevel level, BoundingBox pen, Vec3 herd, Vec3 middle, long seed) {
		if (!Rings.LIVE.running()) {
			start(level, pen, herd, middle, seed);
		}
	}

	private static void bare(ServerLevel level, BoundingBox pen, Vec3 middle) {
		Optional<Rings.Site> site = Rings.site(level, middle, SHEEP, Sc21Config.get().spacing);
		Rings.LIVE.clear();
		// By morning the herd has wandered off a way.
		scatter(GalleryProps.staged(level, pen, EntityTypes.SHEEP), middle.add(-19, 0, -14), 77L);
		// The staging's own grazing between its frames (the herd let go and set going again) is the gallery's, not the scene's:
		// the grass round the ring is whole again, so the shot shows exactly what the scene bares (R11-10).
		site.ifPresent(s -> {
			int r = (int) Math.ceil(s.radius()) + 6;
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					at.set(Math.floor(s.middle().x) + dx, s.feetY() - 1, Math.floor(s.middle().z) + dz);
					if (level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.DIRT)) {
						GalleryProps.set(level, at, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
			}
			Rings.bare(level, s.middle(), s.radius(), s.feetY(), Sc21Config.get().printCells, Services.traces().forced());
		});
	}
}
