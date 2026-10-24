package com.forzacode.a1016_02.lore.sc2.s30;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.debug.gallery.GalleryRun;
import com.forzacode.a1016_02.debug.gallery.Png;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;
import com.forzacode.a1016_02.world.sc2.s16.S16;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's exhibit {@code 30} (development only): a staged home (a cabin, a path, a field, a pond and a stream, a
 * hill, a wood) at the middle of a map drawn from the world as vanilla draws it, and a second, wider map of the same
 * home drawn only round home (vanilla's broken edge) with one of his places (a real {@code s16} built by {@code Live})
 * standing in its blank part. Each map hangs in a frame on a board south of the cabin (cameras close in front, one wider);
 * each is also written as it looks held, on its paper ({@code plan_held_1}, {@code plan_held_2}: before;
 * {@code plan_held_1b}, {@code plan_held_2b}: after), the way a player sees it in hand. Two time-lapses: the first map
 * losing the home, the second drawing his place. Staged through 30's own code with nobody to see (the frames are the
 * display).
 */
final class S30Gallery {
	private static final long SEED = 0x30L;
	/** The paper and its edge, as vanilla's map background is drawn. */
	private static final int PAPER = 0xFFD6BE96;
	private static final int EDGE = 0xFF99876C;
	/** Held-render units per map pixel and the paper's margin round the map (vanilla: 7 of 128). */
	private static final int PX = 4;
	private static final int MARGIN = 7;

	private S30Gallery() {
	}

	static void register() {
		if (Gallery.active()) {
			Gallery.register(new Gallery.Exhibit("30", "30", S30Gallery::place));
		}
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int y0 = origin.getY();
		BlockPos home = home(origin);
		GalleryProps.clear(level, new BoundingBox(home.getX() - 60, y0, home.getZ() - 60, home.getX() + 60, y0 + 30, home.getZ() + 60));
		homestead(level, home);

		ItemStack near = MapItem.create(level, home.getX(), home.getZ(), (byte) 0, true, false);
		MapItemSavedData one = MapItem.getSavedData(near, level);
		ItemStack wide = MapItem.create(level, home.getX(), home.getZ(), (byte) 2, true, false);
		MapItemSavedData two = MapItem.getSavedData(wide, level);
		BlockPos pyramid = home.offset(186, 0, 172);
		Live.Result built = Live.place(level, S16.KIND, new Start(pyramid, Direction.WEST, PyramidShape.variant(0, false, false, false), SEED));

		// Both maps as a player who walked round home with them would have them.
		load(level, home, 64 + 16);
		paint(one, level, home, 200);
		load(level, home, 120 + 16);
		paint(two, level, home, 26);
		held(one, "plan_held_1");
		held(two, "plan_held_2");

		// Two boards facing south, south of the cabin, each with a map at eye height.
		BlockPos boardA = home.offset(-3, 0, 16);
		BlockPos boardB = home.offset(4, 0, 16);
		Vec3 frameA = board(level, boardA, near);
		Vec3 frameB = board(level, boardB, wide);

		Gallery.View closeA = Gallery.View.looking("near", frameA.add(0, 0, 1.0), frameA, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("the first map up close in its frame: home in the middle");
		Gallery.View closeB = Gallery.View.looking("near_2", frameB.add(0, 0, 1.0), frameB, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("the second, wider map up close: drawn round home only");
		Gallery.View mid = Gallery.View.looking("mid", new Vec3(boardA.getX() - 4.5, y0 + 1.7, boardA.getZ() + 9.5), Vec3.atCenterOf(home.above(2)),
				Gallery.NOON, Gallery.Sky.CLEAR, false).noted("the two boards and the cabin behind them");
		Gallery.View aerial = Gallery.View.looking("aerial", Vec3.atCenterOf(home).add(-40, 70, 70), Vec3.atCenterOf(home), Gallery.NOON, Gallery.Sky.CLEAR,
				false).noted("home from above: what the first map draws");
		Gallery.Frames blank = new Gallery.Frames("f_1", 3, 20, closeA, l -> blankNow(l, near, one, home))
				.noted("the first map: the square round home blank (form 1), every 20 ticks; plan_held_1b is it held");
		Gallery.Frames drawn = new Gallery.Frames("f_2", 3, 20, closeB, l -> drawNow(l, wide, two, home, built.place()))
				.noted("the second map: his place drawn in where nobody went (form 2), every 20 ticks; plan_held_2b is it held");
		BoundingBox box = new BoundingBox(home.getX() - 30, y0 - 2, home.getZ() - 30, home.getX() + 30, y0 + 14, home.getZ() + 30);
		return new Gallery.Placed(box, List.of(closeA, closeB, mid, aerial), List.of(blank, drawn));
	}

	/**
	 * Where home goes near the exhibit's origin: in the middle of its first map (a scale 0 map is centred on a multiple of
	 * 128), and as near the second map's pixel (40, 40) as such a spot gets, so his place 200 east and 180 south is on it.
	 */
	static BlockPos home(BlockPos origin) {
		BlockPos best = origin;
		int bestScore = Integer.MAX_VALUE;
		for (int j = -3; j <= 3; j++) {
			for (int k = -3; k <= 3; k++) {
				int x = Math.floorDiv(origin.getX(), 128) * 128 + j * 128;
				int z = Math.floorDiv(origin.getZ(), 128) * 128 + k * 128;
				MapItemSavedData wide = MapItemSavedData.createFresh(x, z, (byte) 2, true, false, net.minecraft.world.level.Level.OVERWORLD);
				int score = Math.abs(MapPixels.pixelX(wide, x) - 40) + Math.abs(MapPixels.pixelY(wide, z) - 40) + Math.abs(j) + Math.abs(k);
				if (score < bestScore) {
					bestScore = score;
					best = new BlockPos(x, origin.getY(), z);
				}
			}
		}
		return best;
	}

	/** A small home: a cabin with a chimney, a path south, a field with its water, a pond and its stream, a pen, a hill, a wood. */
	private static void homestead(ServerLevel level, BlockPos home) {
		int x0 = home.getX();
		int y0 = home.getY();
		int z0 = home.getZ();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		BlockState roof = Blocks.SPRUCE_PLANKS.defaultBlockState();
		GalleryProps.hill(level, home.offset(34, 0, -30), 26, 14);
		// The cabin, 9 x 7, its door facing south.
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 3;
				boolean corner = Math.abs(dx) == 4 && Math.abs(dz) == 3;
				GalleryProps.set(level, new BlockPos(x0 + dx, y0 - 1, z0 + dz), Blocks.COBBLESTONE.defaultBlockState());
				for (int y = 0; y < 4; y++) {
					BlockPos at = new BlockPos(x0 + dx, y0 + y, z0 + dz);
					if (edge) {
						boolean window = y == 1 && !corner && (Math.abs(dx) == 2 || dz == 0 && Math.abs(dx) == 4);
						boolean door = dz == 3 && dx == 0 && y < 2;
						GalleryProps.set(level, at, door ? Blocks.AIR.defaultBlockState() : window ? Blocks.GLASS_PANE.defaultBlockState() : corner ? log : planks);
					}
				}
				GalleryProps.set(level, new BlockPos(x0 + dx, y0 + 4, z0 + dz), roof);
			}
		}
		for (int y = 0; y <= 6; y++) {
			GalleryProps.set(level, new BlockPos(x0 + 3, y0 + y, z0 - 2), Blocks.COBBLESTONE.defaultBlockState());
		}
		// The path south past the boards, a field to the west with its water, a pond east and its stream, a pen north.
		for (int dz = 4; dz <= 44; dz++) {
			int bend = (int) Math.round(Math.sin(dz / 9.0) * 2.0);
			for (int dx = -1; dx <= 0; dx++) {
				GalleryProps.set(level, new BlockPos(x0 + dx + bend, y0 - 1, z0 + dz), Blocks.DIRT_PATH.defaultBlockState());
			}
		}
		for (int dx = -19; dx <= -8; dx++) {
			for (int dz = -5; dz <= 7; dz++) {
				boolean water = dx == -13 && (dz == -1 || dz == 3);
				GalleryProps.set(level, new BlockPos(x0 + dx, y0 - 1, z0 + dz), water ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState());
				if (!water) {
					GalleryProps.set(level, new BlockPos(x0 + dx, y0, z0 + dz), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
				}
			}
		}
		for (int dx = 9; dx <= 20; dx++) {
			for (int dz = 2; dz <= 10; dz++) {
				double ex = (dx - 14.5) / 6.0;
				double ez = (dz - 6.0) / 4.5;
				if (ex * ex + ez * ez <= 1.0) {
					water(level, new BlockPos(x0 + dx, y0 - 1, z0 + dz));
				}
			}
		}
		int last = 15;
		for (int dz = 10; dz <= 70; dz++) {
			int x = 15 + (int) Math.round(Math.sin(dz / 7.0) * 3.0 + dz / 6.0);
			for (int across = Math.min(last, x); across <= Math.max(last, x) + 1; across++) {
				water(level, new BlockPos(x0 + across, y0 - 1, z0 + dz));
			}
			last = x;
		}
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -16; dz <= -8; dz++) {
				if (Math.abs(dx) == 6 || dz == -16 || dz == -8) {
					GalleryProps.set(level, new BlockPos(x0 + dx, y0, z0 + dz), Blocks.OAK_FENCE.defaultBlockState());
				}
			}
		}
		RandomSource random = RandomSource.create(SEED);
		for (int i = 0; i < 26; i++) {
			int dx = -54 + random.nextInt(26);
			int dz = -40 + random.nextInt(70);
			GalleryProps.tree(level, new BlockPos(x0 + dx, y0, z0 + dz), random.nextInt(4) == 0 ? "minecraft:birch" : "minecraft:oak");
		}
		int[][] trees = {{-20, 18}, {26, 16}, {12, -30}, {-10, 34}, {32, 4}, {6, 28}};
		for (int[] t : trees) {
			GalleryProps.tree(level, new BlockPos(x0 + t[0], y0, z0 + t[1]), "minecraft:oak");
		}
	}

	private static void water(ServerLevel level, BlockPos at) {
		GalleryProps.set(level, at, Blocks.WATER.defaultBlockState());
		GalleryProps.set(level, at.below(), Blocks.WATER.defaultBlockState());
	}

	/** Loads every chunk within {@code radius} of {@code center} (the gallery only: the maps are drawn from them). */
	private static void load(ServerLevel level, BlockPos center, int radius) {
		for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
			for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
	}

	/** The map drawn as vanilla draws it within {@code radius} pixels of home, with vanilla's broken edge. */
	private static void paint(MapItemSavedData data, ServerLevel level, BlockPos home, int radius) {
		int hx = MapPixels.pixelX(data, home.getX());
		int hy = MapPixels.pixelY(data, home.getZ());
		for (int py = 0; py < MapPixels.SIZE; py++) {
			for (int px = 0; px < MapPixels.SIZE; px++) {
				int d2 = (px - hx) * (px - hx) + (py - hy) * (py - hy);
				if (d2 >= radius * radius || d2 > (radius - 2) * (radius - 2) && ((px + py) & 1) == 0) {
					continue;
				}
				Byte colour = MapPixels.colour(level, data, px, py);
				if (colour != null) {
					data.setColor(px, py, colour);
				}
			}
		}
	}

	/** A board of three by three spruce planks with the map in a frame on its middle, facing south; the frame's middle. */
	private static Vec3 board(ServerLevel level, BlockPos at, ItemStack map) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int y = 0; y < 3; y++) {
				GalleryProps.set(level, at.offset(dx, y, 0), Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
		}
		GalleryProps.set(level, at.offset(0, 3, 0), Blocks.SPRUCE_SLAB.defaultBlockState());
		BlockPos framePos = at.offset(0, 1, 1);
		ItemFrame frame = new ItemFrame(level, framePos, Direction.SOUTH);
		frame.setItem(map.copy(), false);
		frame.addTag(GalleryProps.TAG);
		level.addFreshEntity(frame);
		return new Vec3(framePos.getX() + 0.5, framePos.getY() + 0.5, framePos.getZ() + 0.07);
	}

	/** 30's own code with nobody to see the map (the gallery's frames are the display) and his place known. */
	private static Maps.Env env(ServerLevel level, Remains remains) {
		Maps.Env live = Maps.Env.live(level.getServer());
		return new Maps.Env(live.server(), live.watch(), s -> List.of(), live.base(), () -> Stage.TELLING, remains, live.data(), live.config(),
				live.sinceCombat());
	}

	/** Form 1 on the first map, through 30's own code; then it held. */
	private static void blankNow(ServerLevel level, ItemStack map, MapItemSavedData data, BlockPos home) {
		Maps.blank(env(level, new KnownPlaces(List.of())), new Maps.Candidate(map, map.get(DataComponents.MAP_ID), data, home), RandomSource.create(SEED));
		held(data, "plan_held_1b");
	}

	/** Form 2 on the second map, through 30's own code, its place's chunks loaded first; then it held. */
	private static void drawNow(ServerLevel level, ItemStack map, MapItemSavedData data, BlockPos home, Remains.Place place) {
		BoundingBox box = place.box();
		load(level, box.getCenter(), Math.max(box.getXSpan(), box.getZSpan()) / 2 + 64);
		Maps.draw(env(level, new KnownPlaces(List.of(place))), new Maps.Candidate(map, map.get(DataComponents.MAP_ID), data, home), place);
		held(data, "plan_held_2b");
	}

	/**
	 * The map as it looks held: vanilla's paper (its colour, a darker edge), the map's pixels on it {@value #MARGIN} in,
	 * a pixel never drawn showing the paper; {@value #PX} image pixels a map pixel. Written beside the exhibit's PNGs.
	 */
	static void held(MapItemSavedData data, String name) {
		int units = MapPixels.SIZE + 2 * MARGIN;
		int size = units * PX;
		int[] argb = new int[size * size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int u = x / PX;
				int v = y / PX;
				boolean edge = u < 2 || v < 2 || u >= units - 2 || v >= units - 2;
				argb[y * size + x] = edge ? EDGE : PAPER;
			}
		}
		for (int py = 0; py < MapPixels.SIZE; py++) {
			for (int px = 0; px < MapPixels.SIZE; px++) {
				byte packed = data.colors[MapPixels.index(px, py)];
				if (packed == 0) {
					continue;
				}
				int colour = MapColor.getColorFromPackedId(packed);
				for (int dy = 0; dy < PX; dy++) {
					for (int dx = 0; dx < PX; dx++) {
						argb[((py + MARGIN) * PX + dy) * size + (px + MARGIN) * PX + dx] = colour | 0xFF000000;
					}
				}
			}
		}
		try {
			Path dir = GalleryRun.dir("30");
			Files.createDirectories(dir);
			Files.write(dir.resolve(name + ".png"), Png.write(new Png.Image(size, size, argb)));
		} catch (IOException | RuntimeException e) {
			A1016_02.LOGGER.warn("[a1016] gallery: 30 could not write {}: {}", name, e.toString());
		}
	}
}
