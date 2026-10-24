package com.forzacode.a1016_02.debug.gallery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;

/**
 * The blueprint renders (docs/SELF_CREATED_2.md, "The gallery"): for a placed box, server side and headless, a top plan,
 * four elevations and two sections through the middle, each block one square of the image in its {@link MapColor} (air
 * transparent; in the plan and the elevations the colour darkens with depth into the box, so steps and cuts read), into
 * {@code plan_*.png}. Exact geometry for the art director, and the gallery's last fallback when no client can run.
 *
 * <p>Orientation: the plan has north up and east right; each elevation is the box seen from outside on that side (the
 * north one looks south, so east is on its left); {@code plan_ns} is the north-south slice through the middle seen from
 * the west (north left), {@code plan_ew} the east-west slice seen from the south (west left); up is up. A block is
 * {@link #scale} pixels square, so small exhibits stay readable.
 */
public final class Blueprint {
	/** The renders, in the order they are written. */
	public static final List<String> NAMES = List.of("plan_top", "plan_north", "plan_south", "plan_east", "plan_west", "plan_ns", "plan_ew");
	/** A box longer than this on a side is not rendered (a render that big helps nobody). */
	public static final int MAX_SPAN = 1024;
	/** A block with no map colour (glass, torches, plants): pale, half transparent. */
	private static final int NO_COLOUR = 0xA0C8D4DC;

	private Blueprint() {
	}

	/** Pixels per block: the longest side about 768 pixels, 1 to 8. */
	public static int scale(BoundingBox box) {
		int longest = Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan()));
		return Math.clamp(768 / Math.max(1, longest), 1, 8);
	}

	/** Every render of the box (block by block, before scaling), in {@link #NAMES}' order. Server thread. */
	public static List<Png.Image> render(ServerLevel level, BoundingBox box) {
		List<Png.Image> images = new ArrayList<>();
		int x0 = box.minX();
		int y0 = box.minY();
		int z0 = box.minZ();
		int w = box.getXSpan();
		int h = box.getYSpan();
		int d = box.getZSpan();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		// Top: north up, east right; the first block from the top, darker the deeper.
		images.add(project(w, d, h, (u, v, k) -> at.set(x0 + u, box.maxY() - k, z0 + v), level, at, true));
		// North: seen from the north (looking south), east on the left.
		images.add(project(w, h, d, (u, v, k) -> at.set(box.maxX() - u, box.maxY() - v, z0 + k), level, at, true));
		// South: seen from the south (looking north), west on the left.
		images.add(project(w, h, d, (u, v, k) -> at.set(x0 + u, box.maxY() - v, box.maxZ() - k), level, at, true));
		// East: seen from the east (looking west), south on the left.
		images.add(project(d, h, w, (u, v, k) -> at.set(box.maxX() - k, box.maxY() - v, box.maxZ() - u), level, at, true));
		// West: seen from the west (looking east), north on the left.
		images.add(project(d, h, w, (u, v, k) -> at.set(x0 + k, box.maxY() - v, z0 + u), level, at, true));
		// The north-south slice at the middle x, seen from the west (north on the left).
		int cx = box.getCenter().getX();
		images.add(project(d, h, 1, (u, v, k) -> at.set(cx, box.maxY() - v, z0 + u), level, at, false));
		// The east-west slice at the middle z, seen from the south (west on the left).
		int cz = box.getCenter().getZ();
		images.add(project(w, h, 1, (u, v, k) -> at.set(x0 + u, box.maxY() - v, cz), level, at, false));
		return images;
	}

	/**
	 * Writes every render into {@code dir} ({@code plan_top.png} ...), scaled by {@link #scale}. Returns the files
	 * written; none for a box over {@link #MAX_SPAN}.
	 */
	public static List<Path> write(ServerLevel level, BoundingBox box, Path dir) throws IOException {
		if (box.getXSpan() > MAX_SPAN || box.getYSpan() > MAX_SPAN || box.getZSpan() > MAX_SPAN) {
			return List.of();
		}
		Files.createDirectories(dir);
		List<Png.Image> images = render(level, box);
		int scale = scale(box);
		List<Path> files = new ArrayList<>();
		for (int i = 0; i < images.size(); i++) {
			Path file = dir.resolve(NAMES.get(i) + ".png");
			Files.write(file, Png.write(scaled(images.get(i), scale)));
			files.add(file);
		}
		return files;
	}

	/** One line for the index: the box's size and the scale. */
	public static String describe(BoundingBox box) {
		return String.format(Locale.ROOT, "%d x %d x %d blocks (x, y, z) from %d %d %d, %d px per block", box.getXSpan(), box.getYSpan(), box.getZSpan(),
				box.minX(), box.minY(), box.minZ(), scale(box));
	}

	/** Each pixel a {@code scale} x {@code scale} square. */
	public static Png.Image scaled(Png.Image image, int scale) {
		if (scale == 1) {
			return image;
		}
		int w = image.width() * scale;
		int h = image.height() * scale;
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				out[y * w + x] = image.at(x / scale, y / scale);
			}
		}
		return new Png.Image(w, h, out);
	}

	@FunctionalInterface
	private interface Cell {
		/** Moves the shared cursor to the cell at image column {@code u}, row {@code v}, {@code k} deep. */
		void to(int u, int v, int k);
	}

	private static Png.Image project(int width, int height, int depth, Cell cell, ServerLevel level, BlockPos.MutableBlockPos at, boolean shade) {
		int[] argb = new int[width * height];
		for (int v = 0; v < height; v++) {
			for (int u = 0; u < width; u++) {
				for (int k = 0; k < depth; k++) {
					cell.to(u, v, k);
					BlockState state = level.getBlockState(at);
					if (state.isAir()) {
						continue;
					}
					argb[v * width + u] = colour(state, level, at, shade && depth > 1 ? (double) k / (depth - 1) : 0.0);
					break;
				}
			}
		}
		return new Png.Image(width, height, argb);
	}

	/** The block's map colour, darkened by up to 65% with depth (0 to 1). */
	static int colour(BlockState state, ServerLevel level, BlockPos pos, double depth) {
		MapColor map = state.getMapColor(level, pos);
		if (map == MapColor.NONE) {
			return NO_COLOUR;
		}
		double f = 1.0 - 0.65 * depth;
		int r = (int) (((map.col >> 16) & 0xFF) * f);
		int g = (int) (((map.col >> 8) & 0xFF) * f);
		int b = (int) ((map.col & 0xFF) * f);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
