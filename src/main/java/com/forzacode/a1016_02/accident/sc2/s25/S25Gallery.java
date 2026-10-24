package com.forzacode.a1016_02.accident.sc2.s25;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.accident.sc2.HuntScene;
import com.forzacode.a1016_02.accident.sc2.Hunts;
import com.forzacode.a1016_02.accident.sc2.Prey;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's exhibit {@code 25} (docs/SELF_CREATED_2.md, 25; development only): a staged night taiga, the camera as
 * the player, and two time-lapses run by {@link Pack} itself around a stand-in (nothing is ever set on anyone): the first
 * part ({@code f_1} on a fog night, {@code f_3} at dusk to judge it: the pack walking in to the edge of what you can see
 * and holding there, facing you), and the wait ({@code f_2}: from the top of a two-high pillar, the pack circling below;
 * {@code f_4} the same from behind and above, a scale figure on the pillar).
 * View and frame names are camera words or numbers (D-103); what they show is in their notes.
 */
final class S25Gallery {
	static final String ID = "25";
	/** The pillar for {@code f_2}, from the origin. */
	static final int PILLAR_X = -36;
	static final int PILLAR_Z = 14;

	private S25Gallery() {
	}

	static Gallery.Exhibit exhibit() {
		return new Gallery.Exhibit(ID, "25", S25Gallery::place);
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();
		BoundingBox box = new BoundingBox(x0 - 56, y0 - 1, z0 - 72, x0 + 56, y0 + 20, z0 + 32);
		GalleryProps.clear(level, new BoundingBox(box.minX(), y0, box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
		taiga(level, box, y0);
		// A low rise out there, off to the side, and a small crest about 20 ahead of the camera: the one that walks into view
		// stands on it against the sky (R11-3).
		GalleryProps.hill(level, new BlockPos(x0 + 18, y0, z0 - 40), 17, 4);
		GalleryProps.hill(level, new BlockPos(x0 - 2, y0, z0 - 17), 5, 2);
		int[][] trees = {{-12, -6}, {-20, -18}, {13, -12}, {22, -24}, {-7, -44}, {-27, -40}, {27, -46}, {3, -60}, {-18, 10}, {17, 9}, {-36, -26}, {37, -10},
				{-44, -52}, {44, -56}, {-3, 18}, {30, 20}, {-30, -62}, {16, -66}, {-50, -8}, {50, -30}};
		for (int[] t : trees) {
			BlockPos base = new BlockPos(x0 + t[0], Gallery.ground(level, x0 + t[0], z0 + t[1]), z0 + t[1]);
			GalleryProps.tree(level, base, "minecraft:spruce");
		}
		BlockPos pillar = new BlockPos(x0 + PILLAR_X, y0, z0 + PILLAR_Z);
		GalleryProps.set(level, pillar, Blocks.COBBLESTONE.defaultBlockState());
		GalleryProps.set(level, pillar.above(), Blocks.COBBLESTONE.defaultBlockState());
		GalleryProps.summon(level, EntityTypes.WOLF, new BlockPos(x0, y0, z0 - 52), 4);
		GalleryProps.summon(level, EntityTypes.WOLF, pillar.offset(7, 0, 0), 4);

		BoundingBox west = new BoundingBox(pillar.getX() - 16, y0 - 4, pillar.getZ() - 16, pillar.getX() + 16, y0 + 16, pillar.getZ() + 16);
		Vec3 feet = new Vec3(x0 + 0.5, y0, z0 + 0.5);
		Vec3 eye = feet.add(0.0, Gallery.EYE, 0.0);
		Vec3 out = new Vec3(x0 + 0.5, y0 + 1.0, z0 - 30.0);
		Vec3 top = Vec3.atBottomCenterOf(pillar.above(2));
		List<Gallery.View> views = new ArrayList<>();
		views.add(Gallery.View.looking("aerial", new Vec3(x0 + 0.5, y0 + 46.0, z0 + 34.0), new Vec3(x0 + 0.5, y0, z0 - 26.0), Gallery.DUSK, Gallery.Sky.CLEAR,
				false).noted("the staged taiga from above at dusk: the camera's spot in front, the rise out north"));
		Gallery.View stalkCamera = Gallery.View.looking("f_1", eye, out, Gallery.NIGHT, Gallery.Sky.FOG_NIGHT, false);
		Gallery.View siegeCamera = Gallery.View.looking("f_2", top.add(0.0, 1.62, 0.0), top.add(5.0, -2.0, 0.0), Gallery.NIGHT, Gallery.Sky.CLEAR, false);
		Gallery.View behind = Gallery.View.looking("f_4", top.add(-6.5, 5.0, -6.5), top.add(1.0, -1.5, 1.0), Gallery.NIGHT, Gallery.Sky.CLEAR, false);
		List<Gallery.Frames> frames = new ArrayList<>();
		BoundingBox all = new BoundingBox(x0 - 24, y0 - 4, z0 - 72, x0 + 30, y0 + 16, z0 + 32); // not the pillar's four
		frames.add(new Gallery.Frames("f_1", 30, 20, stalkCamera, l -> stalk(l, all, feet, 180.0F, 0.0, 600))
				.noted("the first part on a fog night from the player's eyes, the pack behind them: one walks round into the middle of their view,"
						+ " nearer than the fog's edge, and stands there facing them"));
		frames.add(new Gallery.Frames("f_2", 24, 10, siegeCamera, l -> siege(l, west, top))
				.noted("the wait, from the top of a two-high pillar at night: the pack circling below"));
		frames.add(new Gallery.Frames("f_3", 30, 20, stalkCamera.named("f_3").at(Gallery.DUSK, Gallery.Sky.CLEAR), l -> stalk(l, all, feet, 180.0F, 0.0, 600))
				.noted("the same at dusk: the one in view against the sky on the crest ahead"));
		frames.add(new Gallery.Frames("f_4", 24, 10, behind, l -> {
			GalleryProps.scaleFigure(l, pillar.above(2), 45.0F);
			siege(l, west, top);
		}).noted("the wait from behind and above: the figure on its pillar, the pack circling below at night"));
		// R11-3: the player turns round to face them: the stand-in looks south, the camera north, onto the pack behind it.
		frames.add(new Gallery.Frames("f_5", 40, 30, stalkCamera.named("f_5").at(Gallery.NIGHT, Gallery.Sky.CLEAR),
				l -> stalk(l, all, feet, 0.0F, Math.PI, 60))
				.noted("a clear night, the player turned round onto the pack behind them: two or three together at 16 to 24, then the closing"
						+ " (the last 20 s of a 60 s first part) at 12 to 16"));
		frames.add(new Gallery.Frames("f_6", 20, 20, stalkCamera.named("f_6"), l -> stalk(l, all, feet, 0.0F, Math.PI, 600))
				.noted("the same turn on a fog night"));
		return new Gallery.Placed(box, views, frames);
	}

	/** Podzol, coarse dirt and grass, mixed per cell (the same anywhere for the same cells). */
	private static void taiga(ServerLevel level, BoundingBox box, int y0) {
		BlockState podzol = Blocks.PODZOL.defaultBlockState();
		BlockState coarse = Blocks.COARSE_DIRT.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				long h = (x * 341873128712L) ^ (z * 132897987541L);
				int r = (int) Math.floorMod(h >> 7, 100);
				GalleryProps.set(level, new BlockPos(x, y0 - 1, z), r < 34 ? podzol : r < 46 ? coarse : grass);
				if (r >= 95) {
					GalleryProps.set(level, new BlockPos(x, y0, z), (r & 1) == 0 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
				}
			}
		}
	}

	/**
	 * The first part around a stand-in at the camera looking along {@code yaw}: the wolves back out 46 off along
	 * {@code from} (radians, {@code Hunts.out}'s: 0 south, pi north: behind the stand-in), its first part {@code seconds}
	 * long.
	 */
	private static void stalk(ServerLevel level, BoundingBox area, Vec3 feet, float yaw, double from, int seconds) {
		List<Wolf> wolves = GalleryProps.staged(level, area, EntityTypes.WOLF);
		for (int i = 0; i < wolves.size(); i++) {
			Vec3 at = Hunts.out(feet, from + Math.toRadians(-30.0 + 20.0 * i), 46.0 + 3.0 * (i % 2));
			BlockPos ground = Hunts.stand(level, at.x, at.z, (int) feet.y, 8, 8, 1);
			Vec3 spot = ground == null ? at : Vec3.atBottomCenterOf(ground);
			wolves.get(i).snapTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
		}
		Prey prey = Prey.standIn(level, feet, yaw, 0.0F);
		Sc25Config cfg = new Sc25Config();
		cfg.stalkSecondsMin = seconds;
		cfg.stalkSecondsMax = seconds;
		run(new Pack(UUID.nameUUIDFromBytes("gallery 25 a".getBytes(java.nio.charset.StandardCharsets.UTF_8)), level, HuntScene.Links.gallery(), -1,
				prey, wolves, cfg, () -> true, prey, Pack.Phase.STALK), 20 * 70);
	}

	/** {@code f_2}: the wolves round the pillar, then the wait around a stand-in on its top. */
	private static void siege(ServerLevel level, BoundingBox area, Vec3 top) {
		List<Wolf> wolves = GalleryProps.staged(level, area, EntityTypes.WOLF);
		for (int i = 0; i < wolves.size(); i++) {
			Vec3 at = Hunts.out(top, i * Math.PI / 2.0, 6.0);
			wolves.get(i).snapTo(at.x, top.y - 2.0, at.z, 0.0F, 0.0F);
		}
		Prey prey = Prey.standIn(level, top, -90.0F, 50.0F);
		Sc25Config cfg = new Sc25Config();
		cfg.siegeSeconds = 600;
		run(new Pack(UUID.nameUUIDFromBytes("gallery 25 b".getBytes(java.nio.charset.StandardCharsets.UTF_8)), level, HuntScene.Links.gallery(), -1, prey,
				wolves, cfg, () -> true, prey, Pack.Phase.SIEGE), 20 * 20);
	}

	private static void run(Pack pack, long ticks) {
		Hunts.of(pack.target()).ifPresent(old -> old.stop(true, "staged again"));
		Hunts.start(pack);
		pack.lifetime(ticks);
		pack.begin();
	}
}
