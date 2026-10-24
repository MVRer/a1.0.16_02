package com.forzacode.a1016_02.debug.gallery;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's demo exhibit {@code 00} (SC2-0c), which proves the pipeline: a 9 x 9 cobblestone hut with its door to
 * the west, a scale figure beside the door and two trees, six sheep that walk a staged loop round the hut, and a staged
 * block of rock (a tunnel into a lit room) cut away for a section view. Its views: the standard set ({@code far_*},
 * {@code mid}, {@code near}, {@code mouth}, {@code aerial}), {@code in_1} (inside, true light), {@code section} (the rock
 * cut open, night vision) and the time-lapse {@code top} (12 frames, the sheep going round).
 */
final class Exhibit00 {
	static final String ID = "00";
	/** The hut's half width: 9 x 9. */
	static final int HALF = 4;
	/** How far the sheep's loop is from the hut's centre. */
	static final int LOOP = 7;
	static final int SHEEP = 6;

	private Exhibit00() {
	}

	static Gallery.Exhibit exhibit() {
		return new Gallery.Exhibit(ID, "00", Exhibit00::place);
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();
		// Centred on the hut, so the blueprint's slices through the middle cut the hut and its door.
		BoundingBox box = new BoundingBox(x0 - 26, y0 - 1, z0 - 13, x0 + 26, y0 + 12, z0 + 13);
		GalleryProps.clear(level, new BoundingBox(box.minX(), y0, box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
		hut(level, x0, y0, z0);
		BlockPos entrance = new BlockPos(x0 - HALF, y0, z0);
		GalleryProps.scaleFigure(level, entrance.offset(-1, 0, 2), 90.0F);
		GalleryProps.tree(level, new BlockPos(x0 - 10, y0, z0 - 9), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 8, y0, z0 + 10), "minecraft:birch");
		BoundingBox rock = rockBlock(level, x0, y0, z0);
		List<Vec3> loop = GalleryProps.rectangle(x0 - LOOP + 0.5, z0 - LOOP + 0.5, x0 + LOOP + 0.5, z0 + LOOP + 0.5, y0, 2.0);
		List<Sheep> sheep = GalleryProps.summon(level, EntityTypes.SHEEP, new BlockPos(x0 - LOOP, y0, z0 - LOOP), SHEEP);
		for (int i = 0; i < sheep.size(); i++) {
			Vec3 spot = loop.get(i * loop.size() / sheep.size());
			sheep.get(i).snapTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
		}

		List<Gallery.View> views = new ArrayList<>(Gallery.standardViews(level, new BoundingBox(x0 - HALF, y0 - 1, z0 - HALF, x0 + HALF, y0 + 5, z0 + HALF),
				entrance, Direction.WEST));
		// The fog proof: the near camera on a fog night, where the fog has something to swallow.
		views.add(views.stream().filter(v -> v.name().equals("near")).findFirst().orElseThrow().named("near_fog").at(Gallery.NIGHT, Gallery.Sky.FOG_NIGHT)
				.noted("40 out from the entrance, a fog night at full"));
		Vec3 inside = new Vec3(x0 - HALF + 1.5, y0 + Gallery.EYE, z0 + 0.5);
		views.add(Gallery.View.looking("in_1", inside, new Vec3(x0 + HALF - 1 + 0.5, y0 + 0.5, z0 + 2.5), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("inside the door, toward the torch, true light"));
		views.add(Gallery.sectionView(rock, Direction.NORTH, 13).noted("the rock block cut open: the tunnel and the lit room, night vision"));
		Gallery.View top = Gallery.View.looking("top", new Vec3(x0 + 0.5, y0 + 30, z0 + 5.5), new Vec3(x0 + 0.5, y0, z0 + 0.5), Gallery.NOON,
				Gallery.Sky.CLEAR, false);
		BoundingBox pen = box.inflatedBy(24, 8, 24);
		Gallery.Frames frames = new Gallery.Frames("top", 12, 20, top, l -> walk(GalleryProps.staged(l, pen, EntityTypes.SHEEP), loop))
				.noted("six sheep walking the loop round the hut, from above");
		return new Gallery.Placed(box, views, List.of(frames));
	}

	/** The staged loop: each sheep (found again, see {@link GalleryProps#staged}) back on its own point of the loop, then all walk it nose to tail. */
	private static void walk(List<Sheep> sheep, List<Vec3> loop) {
		for (int i = 0; i < sheep.size(); i++) {
			Vec3 spot = loop.get(i * loop.size() / sheep.size());
			sheep.get(i).snapTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
		}
		GalleryProps.walk(sheep, loop, 1.0, 20 * 60 * 5, true);
	}

	/** The hut: cobblestone walls 4 high, a plank floor and a stepped plank roof, a door gap to the west, glass, a torch. */
	private static void hut(ServerLevel level, int x0, int y0, int z0) {
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				GalleryProps.set(level, new BlockPos(x0 + dx, y0 - 1, z0 + dz), planks);
				boolean wall = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
				for (int y = 0; y < 4; y++) {
					GalleryProps.set(level, new BlockPos(x0 + dx, y0 + y, z0 + dz), wall ? cobble : Blocks.AIR.defaultBlockState());
				}
				GalleryProps.set(level, new BlockPos(x0 + dx, y0 + 4, z0 + dz), planks);
				if (Math.abs(dx) < HALF && Math.abs(dz) < HALF) {
					GalleryProps.set(level, new BlockPos(x0 + dx, y0 + 5, z0 + dz), planks);
				}
			}
		}
		GalleryProps.set(level, new BlockPos(x0 - HALF, y0, z0), Blocks.AIR.defaultBlockState());
		GalleryProps.set(level, new BlockPos(x0 - HALF, y0 + 1, z0), Blocks.AIR.defaultBlockState());
		for (int dz : new int[] {-2, 2}) {
			GalleryProps.set(level, new BlockPos(x0 + dz, y0 + 1, z0 - HALF), Blocks.GLASS.defaultBlockState());
			GalleryProps.set(level, new BlockPos(x0 + dz, y0 + 1, z0 + HALF), Blocks.GLASS.defaultBlockState());
		}
		GalleryProps.set(level, new BlockPos(x0 + HALF - 1, y0, z0 + 2), Blocks.TORCH.defaultBlockState());
	}

	/**
	 * A staged block of rock east of the hut (12 x 10 x 12, standing on the ground), a 3 x 3 tunnel from its west face
	 * along its middle into a 5 x 4 x 5 room with a torch, then cut away on the north for the section. Returns the box.
	 */
	private static BoundingBox rockBlock(ServerLevel level, int x0, int y0, int z0) {
		BoundingBox rock = new BoundingBox(x0 + 13, y0, z0 - 6, x0 + 24, y0 + 9, z0 + 5);
		GalleryProps.rock(level, rock);
		int cz = rock.getCenter().getZ();
		GalleryProps.fill(level, new BoundingBox(rock.minX(), y0 + 1, cz - 1, rock.minX() + 6, y0 + 3, cz + 1), Blocks.AIR.defaultBlockState());
		GalleryProps.fill(level, new BoundingBox(rock.minX() + 6, y0 + 1, cz - 2, rock.minX() + 10, y0 + 4, cz + 2), Blocks.AIR.defaultBlockState());
		GalleryProps.set(level, new BlockPos(rock.minX() + 9, y0 + 1, cz + 1), Blocks.TORCH.defaultBlockState());
		GalleryProps.cutaway(level, rock, Direction.NORTH);
		return rock;
	}
}
