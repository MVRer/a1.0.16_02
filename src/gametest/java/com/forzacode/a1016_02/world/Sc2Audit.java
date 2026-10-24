package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Overlay;
import com.forzacode.a1016_02.world.sc2.kit.Shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What a place built over a terrain looks like where it meets it (SC2-H), read from an {@link Overlay} it was filled into
 * (written cells over the base's natural ones), kind by kind but the same rules for every kind:
 * <ul>
 *   <li>{@code fluid}: cells of the inside holding water or lava nobody wrote (flooded or burning);</li>
 *   <li>{@code leak}: open cells the shape wrote with water or lava nobody wrote beside or over them (it runs in once a
 *   block updates);</li>
 *   <li>{@code cave}: open cells of the inside next to natural open cells under the land's top (a cave opened into it),
 *   away from its mouth;</li>
 *   <li>{@code sky}: the same where the natural cell is out in the open (a hole to the outside; open kinds have them by
 *   design);</li>
 *   <li>{@code floating}: columns where a written solid cell has natural open space under it above the land's top (a part
 *   in mid air);</li>
 *   <li>{@code entrance}: the outer mouth cell open with headroom, something within two under it (a step down onto the
 *   land, never a fall), and a way to it in front ("closed" where the shape itself closes it).</li>
 * </ul>
 */
final class Sc2Audit {
	/** Natural cells this near the mouth (or a declared opening) may meet the inside: the way in itself. */
	static final int MOUTH_REACH = 8;

	private Sc2Audit() {
	}

	record Report(int fluid, int leak, int cave, int sky, int floating, String entrance, List<String> where) {
		boolean clean(boolean open) {
			return fluid == 0 && leak == 0 && cave == 0 && (open || sky == 0) && floating == 0 && (entrance.equals("ok") || entrance.equals("closed"));
		}

		@Override
		public String toString() {
			return "fluid " + fluid + ", leak " + leak + ", cave " + cave + ", sky " + sky + ", floating " + floating + ", entrance " + entrance
					+ (where.isEmpty() ? "" : " " + where);
		}
	}

	static boolean open(BlockState state) {
		return !state.isSolid();
	}

	/** Reads one overlay; the base's land top by column is found once (the highest cell that blocks motion in its range). */
	static Report of(Shape shape, Overlay overlay, int baseTop, List<BlockPos> openings) {
		BoundingBox box = shape.box();
		Interior inside = shape.interior();
		Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
		List<BlockPos> mouths = new ArrayList<>(openings);
		mouths.add(shape.entrance());
		List<String> where = new ArrayList<>();
		int fluid = 0;
		int cave = 0;
		int sky = 0;
		int floating = 0;
		int leak = 0;
		int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		// Water or lava nobody wrote beside or over an open cell the shape wrote: it runs in once a block updates.
		int[][] wet = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<BlockState> e : overlay.cells().long2ObjectEntrySet()) {
			if (!open(e.getValue()) || !e.getValue().getFluidState().isEmpty()) {
				continue;
			}
			long pos = e.getLongKey();
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			for (int[] d : wet) {
				if (!overlay.written(x + d[0], y + d[1], z + d[2]) && !overlay.get(x + d[0], y + d[1], z + d[2]).getFluidState().isEmpty()) {
					leak++;
					note(where, "leak", x, y, z);
					break;
				}
			}
		}
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				boolean floats = false;
				for (int y = box.minY(); y <= box.maxY(); y++) {
					boolean written = overlay.written(x, y, z);
					if (written && !floats && y > box.minY() && !open(overlay.get(x, y, z)) && !overlay.written(x, y - 1, z)
							&& open(overlay.get(x, y - 1, z)) && y - 1 > top(overlay, tops, x, z, baseTop) && seen(overlay, box, x, y - 1, z)) {
						floats = true;
						note(where, "floating", x, y, z);
					}
					if (!inside.contains(x, y, z)) {
						continue;
					}
					BlockState state = overlay.get(x, y, z);
					if (!state.getFluidState().isEmpty() && !written) {
						fluid++;
						note(where, "fluid", x, y, z);
					}
					if (!open(state)) {
						continue;
					}
					for (int[] d : sides) {
						int nx = x + d[0];
						int ny = y + d[1];
						int nz = z + d[2];
						if (inside.contains(nx, ny, nz) || overlay.written(nx, ny, nz) || !open(overlay.get(nx, ny, nz)) || nearAny(mouths, nx, ny, nz)) {
							continue;
						}
						if (ny > top(overlay, tops, nx, nz, baseTop)) {
							sky++;
							note(where, "sky", nx, ny, nz);
						} else {
							cave++;
							note(where, "cave", nx, ny, nz);
						}
					}
				}
				floating += floats ? 1 : 0;
			}
		}
		return new Report(fluid, leak, cave, sky, floating, entrance(shape, overlay), where);
	}

	/**
	 * True if this natural open cell under a written one can be seen from outside: a walk through natural open cells
	 * (at most {@value #SEEN_STEPS}) reaches past the box's side or a cell with nothing solid over it to the box's top.
	 * A pocket the place itself covers (a crater under its mass) is not seen.
	 */
	static boolean seen(Overlay overlay, BoundingBox box, int x0, int y0, int z0) {
		it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue queue = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
		it.unimi.dsi.fastutil.longs.LongOpenHashSet visited = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		long first = BlockPos.asLong(x0, y0, z0);
		queue.enqueue(first);
		visited.add(first);
		int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty() && visited.size() < SEEN_STEPS) {
			long pos = queue.dequeueLong();
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			if (!box.isInside(x, y, z) || openAbove(overlay, box, x, y, z)) {
				return true;
			}
			for (int[] d : sides) {
				int nx = x + d[0];
				int ny = y + d[1];
				int nz = z + d[2];
				long next = BlockPos.asLong(nx, ny, nz);
				if (!visited.contains(next) && !overlay.written(nx, ny, nz) && open(overlay.get(nx, ny, nz))) {
					visited.add(next);
					queue.enqueue(next);
				}
			}
		}
		return false;
	}

	static final int SEEN_STEPS = 4000;

	private static boolean openAbove(Overlay overlay, BoundingBox box, int x, int y, int z) {
		for (int yy = y + 1; yy <= box.maxY(); yy++) {
			if (!open(overlay.get(x, yy, z))) {
				return false;
			}
		}
		return true;
	}

	private static void note(List<String> where, String what, int x, int y, int z) {
		if (where.size() < 6) {
			where.add(what + " " + x + " " + y + " " + z);
		}
	}

	private static boolean nearAny(List<BlockPos> mouths, int x, int y, int z) {
		for (BlockPos m : mouths) {
			if (Math.abs(m.getX() - x) <= MOUTH_REACH && Math.abs(m.getY() - y) <= MOUTH_REACH && Math.abs(m.getZ() - z) <= MOUTH_REACH) {
				return true;
			}
		}
		return false;
	}

	/** The base's land top in this column: the highest natural cell that blocks motion at or under {@code baseTop}. */
	private static int top(Overlay overlay, Long2IntOpenHashMap tops, int x, int z, int baseTop) {
		long key = BlockPos.asLong(x, 0, z);
		if (tops.containsKey(key)) {
			return tops.get(key);
		}
		int y = baseTop;
		while (y > baseTop - 400 && open(overlay.base(x, y, z))) {
			y--;
		}
		tops.put(key, y);
		return y;
	}

	/** The outer mouth cell: open, headroom, something under it, a way to it in front. */
	static String entrance(Shape shape, Overlay overlay) {
		BlockPos e = shape.entrance();
		Direction out = shape.entranceFacing();
		BlockState at = overlay.get(e.getX(), e.getY(), e.getZ());
		if (!open(at)) {
			return overlay.written(e.getX(), e.getY(), e.getZ()) ? "closed" : "blocked by " + at.getBlock().getDescriptionId();
		}
		if (!open(overlay.get(e.getX(), e.getY() + 1, e.getZ()))) {
			return "no headroom";
		}
		BlockState under = overlay.get(e.getX(), e.getY() - 1, e.getZ());
		if (open(under) && under.getFluidState().isEmpty() && open(overlay.get(e.getX(), e.getY() - 2, e.getZ()))
				&& open(overlay.get(e.getX(), e.getY() - 3, e.getZ()))) {
			int drop = 1;
			while (drop < 64 && open(overlay.get(e.getX(), e.getY() - drop - 1, e.getZ()))) {
				drop++;
			}
			return "in mid air (a drop of " + drop + ")";
		}
		BlockPos f = e.relative(out);
		boolean level = open(overlay.get(f.getX(), f.getY(), f.getZ())) && open(overlay.get(f.getX(), f.getY() + 1, f.getZ()));
		boolean up = open(overlay.get(f.getX(), f.getY() + 1, f.getZ())) && open(overlay.get(f.getX(), f.getY() + 2, f.getZ()));
		boolean down = open(overlay.get(f.getX(), f.getY() - 1, f.getZ())) && level;
		return level || up || down ? "ok" : "blocked in front";
	}
}
