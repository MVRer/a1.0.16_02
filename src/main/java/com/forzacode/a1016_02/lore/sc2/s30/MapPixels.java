package com.forzacode.a1016_02.lore.sc2.s30;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import org.jspecify.annotations.Nullable;

/**
 * 30's map arithmetic (docs/SELF_CREATED_2.md, 30): where a block is on a map, the square around a base and the edge
 * vanilla leaves round what it has not drawn, and vanilla's own colour for a pixel ({@code MapItem.update}'s rule, read
 * from loaded chunks only: nothing here loads one). A map is 128 x 128 pixels; pixel {@code (x, y)} covers the blocks from
 * {@code (centerX / s + x - 64) * s} for {@code s = 2^scale}, the same for z. Byte 0 is a pixel never drawn. Heights come
 * from the level (its floor and the chunk's heightmap), never a constant.
 */
public final class MapPixels {
	public static final int SIZE = 128;

	private MapPixels() {
	}

	/** Blocks per pixel. */
	public static int blocks(MapItemSavedData data) {
		return 1 << data.scale;
	}

	/** The pixel column of block x on this map (may be outside 0 to 127). */
	public static int pixelX(MapItemSavedData data, int blockX) {
		int s = blocks(data);
		return Math.floorDiv(blockX, s) - data.centerX / s + 64;
	}

	/** The pixel row of block z on this map (may be outside 0 to 127). */
	public static int pixelY(MapItemSavedData data, int blockZ) {
		int s = blocks(data);
		return Math.floorDiv(blockZ, s) - data.centerZ / s + 64;
	}

	/** The lowest block x of pixel column {@code px}. */
	public static int blockX(MapItemSavedData data, int px) {
		int s = blocks(data);
		return (data.centerX / s + px - 64) * s;
	}

	/** The lowest block z of pixel row {@code py}. */
	public static int blockZ(MapItemSavedData data, int py) {
		int s = blocks(data);
		return (data.centerZ / s + py - 64) * s;
	}

	public static boolean inside(int px, int py) {
		return px >= 0 && py >= 0 && px < SIZE && py < SIZE;
	}

	/** True if this block's column is drawn on this map's area (its pixel is on the map). */
	public static boolean holds(MapItemSavedData data, BlockPos pos) {
		return inside(pixelX(data, pos.getX()), pixelY(data, pos.getZ()));
	}

	public static int index(int px, int py) {
		return px + py * SIZE;
	}

	/** One pixel to change: where, what it holds now, what it becomes. */
	public record Change(int index, byte before, byte after) {
	}

	/**
	 * The square of {@code size} pixels centred on {@code (cx, cy)} made blank, and round it a ring one pixel wide left
	 * every other pixel blank, as vanilla leaves the edge of what it has not drawn: only pixels on the map that are drawn
	 * now change.
	 */
	public static List<Change> blankSquare(byte[] colors, int cx, int cy, int size) {
		List<Change> changes = new ArrayList<>();
		int lo = -(size / 2);
		int hi = lo + size - 1;
		for (int dy = lo - 1; dy <= hi + 1; dy++) {
			for (int dx = lo - 1; dx <= hi + 1; dx++) {
				int px = cx + dx;
				int py = cy + dy;
				if (!inside(px, py)) {
					continue;
				}
				boolean ring = dx < lo || dx > hi || dy < lo || dy > hi;
				if (ring && ((px + py) & 1) != 0) {
					continue;
				}
				byte now = colors[index(px, py)];
				if (now != 0) {
					changes.add(new Change(index(px, py), now, (byte) 0));
				}
			}
		}
		return changes;
	}

	/** How many of the square's pixels (the inner square, on the map) are drawn now, and how many it has. */
	public static int[] drawnIn(byte[] colors, int cx, int cy, int size) {
		int lo = -(size / 2);
		int drawn = 0;
		int all = 0;
		for (int dy = lo; dy < lo + size; dy++) {
			for (int dx = lo; dx < lo + size; dx++) {
				if (inside(cx + dx, cy + dy)) {
					all++;
					if (colors[index(cx + dx, cy + dy)] != 0) {
						drawn++;
					}
				}
			}
		}
		return new int[] {drawn, all};
	}

	/** A place's pixels on this map: its box's columns, clipped to the map, as {@code [minX, minY, maxX, maxY]}; null if none is on it. */
	public static int @Nullable [] pixelsOf(MapItemSavedData data, BoundingBox box) {
		int minX = Math.max(0, pixelX(data, box.minX()));
		int minY = Math.max(0, pixelY(data, box.minZ()));
		int maxX = Math.min(SIZE - 1, pixelX(data, box.maxX()));
		int maxY = Math.min(SIZE - 1, pixelY(data, box.maxZ()));
		return minX > maxX || minY > maxY ? null : new int[] {minX, minY, maxX, maxY};
	}

	/**
	 * The pixels a drawn place covers: a disc round its box's middle reaching every pixel of the box and {@code margin}
	 * pixels more, its outer two pixels left every other one as vanilla's edge (its {@code ditherBlack}), on the map.
	 */
	public static List<int[]> disc(MapItemSavedData data, BoundingBox box, int margin) {
		int x0 = pixelX(data, box.minX());
		int y0 = pixelY(data, box.minZ());
		int x1 = pixelX(data, box.maxX());
		int y1 = pixelY(data, box.maxZ());
		double cx = (x0 + x1) / 2.0;
		double cy = (y0 + y1) / 2.0;
		double r = Math.hypot((x1 - x0) / 2.0, (y1 - y0) / 2.0) + Math.max(0, margin);
		List<int[]> pixels = new ArrayList<>();
		for (int py = (int) Math.floor(cy - r); py <= (int) Math.ceil(cy + r); py++) {
			for (int px = (int) Math.floor(cx - r); px <= (int) Math.ceil(cx + r); px++) {
				if (!inside(px, py)) {
					continue;
				}
				double d2 = (px - cx) * (px - cx) + (py - cy) * (py - cy);
				if (d2 > r * r) {
					continue;
				}
				boolean edge = d2 > (r - 2) * (r - 2);
				if (edge && ((px + py) & 1) == 0) {
					continue;
				}
				pixels.add(new int[] {px, py});
			}
		}
		return pixels;
	}

	/** One pixel's area as vanilla reads it: the colour that covers most of it, its average height and water depth. */
	public record Sample(MapColor color, double height, int waterDepth) {
	}

	/**
	 * Vanilla's reading of the {@code s x s} columns from {@code (minX, minZ)} ({@code MapItem.update}): each column's top
	 * block with a map colour (from the chunk's world-surface heightmap down to the level's floor; fluids by what lies
	 * under them), the colour most of them have, the average height and the water depth. Null if a chunk it needs is not
	 * loaded.
	 */
	public static @Nullable Sample sample(ServerLevel level, int s, int minX, int minZ) {
		Multiset<MapColor> colors = LinkedHashMultiset.create();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		int floor = level.getMinY();
		int waterDepth = 0;
		double height = 0.0;
		for (int dx = 0; dx < s; dx++) {
			for (int dz = 0; dz < s; dz++) {
				int x = minX + dx;
				int z = minZ + dz;
				LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
				if (chunk == null) {
					return null;
				}
				pos.set(x, 0, z);
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
				BlockState state;
				if (y <= floor) {
					state = Blocks.BEDROCK.defaultBlockState();
				} else {
					do {
						pos.setY(--y);
						state = chunk.getBlockState(pos);
					} while (state.getMapColor(level, pos) == MapColor.NONE && y > floor);
					if (y > floor && !state.getFluidState().isEmpty()) {
						int solid = y - 1;
						below.set(pos);
						BlockState under;
						do {
							below.setY(solid--);
							under = chunk.getBlockState(below);
							waterDepth++;
						} while (solid > floor && !under.getFluidState().isEmpty());
						state = fluidState(level, state, pos);
					}
				}
				height += (double) y / (s * s);
				colors.add(state.getMapColor(level, pos));
			}
		}
		waterDepth /= s * s;
		MapColor color = Iterables.getFirst(Multisets.copyHighestCountFirst(colors), MapColor.NONE);
		return new Sample(color, height, waterDepth);
	}

	private static BlockState fluidState(ServerLevel level, BlockState state, BlockPos pos) {
		FluidState fluid = state.getFluidState();
		return !fluid.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluid.createLegacyBlock() : state;
	}

	/** Vanilla's packed byte for a pixel from its sample and the height of the pixel north of it ({@code MapItem.update}). */
	public static byte packed(Sample sample, double northHeight, int px, int py, int s) {
		MapColor.Brightness brightness;
		if (sample.color() == MapColor.WATER) {
			double diff = sample.waterDepth() * 0.1 + ((px + py) & 1) * 0.2;
			brightness = diff < 0.5 ? MapColor.Brightness.HIGH : diff > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
		} else {
			double diff = (sample.height() - northHeight) * 4.0 / (s + 4) + (((px + py) & 1) - 0.5) * 0.4;
			brightness = diff > 0.6 ? MapColor.Brightness.HIGH : diff < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
		}
		return sample.color().getPackedId(brightness);
	}

	/**
	 * Vanilla's byte for pixel {@code (px, py)} of this map from the level as it stands, or null while a chunk it needs (its
	 * own area's, or the pixel north of it, which shades it) is not loaded.
	 */
	public static @Nullable Byte colour(ServerLevel level, MapItemSavedData data, int px, int py) {
		int s = blocks(data);
		Sample here = sample(level, s, blockX(data, px), blockZ(data, py));
		if (here == null) {
			return null;
		}
		Sample north = sample(level, s, blockX(data, px), blockZ(data, py - 1));
		if (north == null) {
			return null;
		}
		return packed(here, north.height(), px, py, s);
	}
}
