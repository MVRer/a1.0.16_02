package com.forzacode.a1016_02.world.sc2.s32;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Spot;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Tier;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import org.jspecify.annotations.Nullable;

/**
 * Where a crumb finds its footing (docs/SELF_CREATED_2_R3.md, "Footing and fairness"): its point moves up to
 * {@value #SHIFT} along its line (and, for a thing wider than one block, up to its own reach across it, only so that it
 * fits in its chunk) to a spot with solid ground under it, no face over {@value #MAX_FACE} within 2 of it (D-097.7: one
 * never walks off a drop beside it), no lava within {@value #LAVA}, and no water deeper than 1 but where it stands on a
 * pillar. Over the noise ({@link #model}: a line's plan, and the find measurements) and over the live blocks of the
 * decoration region ({@link Ground#live}: worldgen, the gallery, tests). Any thread.
 */
public final class CrumbFooting {
	/** How far along its line a crumb may move for its footing. */
	public static final int SHIFT = 4;
	/** The highest face one may walk off beside a crumb (D-097.7). */
	public static final int MAX_FACE = 3;
	/** How far round the face rule looks, past the crumb's own reach. */
	public static final int FACE_REACH = 2;
	/** No lava this near. */
	public static final int LAVA = 8;
	/** The deepest a footprint column may lie under the crumb's ground (filled under). */
	public static final int MAX_FILL = 3;
	/** The deepest water a pillar stands in. */
	public static final int MAX_WATER = 40;
	/** The order the shifts are tried in. */
	static final int[] SHIFTS = {0, 1, -1, 2, -2, 3, -3, 4, -4};
	/**
	 * From this line version (FIX-R3-C, R3A-7: on real land a crumb hid from the next one): among the shifts that hold,
	 * the highest footing is taken (the first in {@link #SHIFTS}' order among equals), and a far crumb may move along up
	 * to {@value #FAR_SHIFT}. Over the noise (a line's cut asks only whether a crumb holds) the first that holds answers.
	 */
	public static final int RISES_FROM = 2;
	/** Version 2: how far along its line a far crumb may move for its footing. */
	public static final int FAR_SHIFT = 8;
	/** Version 2's far shifts, in the order tried. */
	static final int[] FAR_SHIFTS = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 7, -7, 8, -8};

	private CrumbFooting() {
	}

	/** The land a footing is read from. */
	public interface Ground {
		/** The top solid block of the column (under water, plants, leaves and logs). */
		int ground(int x, int z);

		/** The column's top block counting water (its ground on land). */
		int surface(int x, int z);

		/** True if lava lies within {@value #LAVA} of this spot near its ground (false over the noise: lakes are not in it). */
		boolean lavaNear(int x, int y, int z);

		/** True if each column costs much (the noise): the footprint is then read at its corners only. */
		default boolean dear() {
			return false;
		}

		/** Over the noise. */
		static Ground noise(Terrain terrain) {
			return new Ground() {
				@Override
				public int ground(int x, int z) {
					return terrain.ground(x, z);
				}

				@Override
				public int surface(int x, int z) {
					return terrain.surface(x, z);
				}

				@Override
				public boolean lavaNear(int x, int y, int z) {
					return false;
				}

				@Override
				public boolean dear() {
					return true;
				}
			};
		}

		/** Over the live blocks (a decoration region's, or a live level's: the gallery and tests). One thread, one call. */
		static Ground live(WorldGenLevel level) {
			return new Live(level);
		}
	}

	/** A spot that holds: where the crumb stands and whether it stands on a pillar. */
	public record Found(int x, int y, int z, int surface, int shift) {
		public boolean wet() {
			return surface > y;
		}
	}

	/** The model a line's plan is cut with: a crumb holds if some shift finds footing over the noise. */
	public static CrumbLines.Model model(Terrain terrain) {
		Ground ground = Ground.noise(terrain);
		return spot -> find(ground, spot, new ChunkPos(spot.x() >> 4, spot.z() >> 4)) != null;
	}

	/**
	 * The first shift that holds, or null. {@code chunk} non-null: the crumb's thing must fit in it (its point's own
	 * chunk: a crumb is built by that chunk alone); null: no chunk (the noise's model).
	 */
	public static @Nullable Found find(Ground g, Spot spot, @Nullable ChunkPos chunk) {
		return find(g, spot, chunk, f -> true);
	}

	/** {@link #find}, the first shift that holds and that {@code accept} takes too (the live cells' own checks). */
	public static @Nullable Found find(Ground g, Spot spot, @Nullable ChunkPos chunk, java.util.function.Predicate<Found> accept) {
		CrumbLines.Line line = spot.line();
		double ux = line.ux();
		double uz = line.uz();
		String kind = line.kind();
		boolean rises = line.version() >= RISES_FROM;
		Found best = null;
		for (int shift : rises && spot.tier() == Tier.FAR ? FAR_SHIFTS : SHIFTS) {
			int x = (int) Math.floor(spot.x() + 0.5 + ux * shift);
			int z = (int) Math.floor(spot.z() + 0.5 + uz * shift);
			int y = g.ground(x, z);
			int s = g.surface(x, z);
			boolean wet = s - y > 1;
			if (wet && (!CrumbShapes.allowsWater(kind, spot.tier()) || s - y > MAX_WATER)) {
				continue;
			}
			int core = CrumbShapes.core(kind, spot.tier(), wet);
			if (chunk != null) {
				// At least one in from its chunk's edge: a canopy's 3 x 3 over it is cut in its own chunk.
				int[] fit = fit(x, z, Math.max(1, core), spot.tier(), ux, uz, chunk);
				if (fit == null) {
					continue;
				}
				if (fit[0] != x || fit[1] != z) {
					x = fit[0];
					z = fit[1];
					y = g.ground(x, z);
					s = g.surface(x, z);
					if ((s - y > 1) != wet) {
						continue;
					}
				}
			}
			if (!wet && (s > y)) {
				// One block of water over the ground: it stands in it as on land.
				s = y;
			}
			// The face rule along the four rays out of it (live and over the noise alike, so a line's cut holds live).
			if (!wet && !rays(g, x, y, z, core + FACE_REACH)) {
				continue;
			}
			if (!wet && !footprint(g, x, y, z, kind, spot.tier(), g.dear())) {
				continue;
			}
			if (g.lavaNear(x, y, z)) {
				continue;
			}
			Found found = new Found(x, y, z, wet ? s : y, shift);
			if (accept.test(found)) {
				if (!rises || g.dear()) {
					return found;
				}
				if (best == null || found.surface() > best.surface()) {
					best = found;
				}
			}
		}
		return best;
	}

	/**
	 * The spot moved, if need be, so the thing (its core reach) lies in the chunk: along the line within the shift's
	 * room, else across it by at most the core's reach; null if it cannot. A one-block thing always fits.
	 */
	static int @Nullable [] fit(int x, int z, int core, Tier tier, double ux, double uz, ChunkPos chunk) {
		int minX = chunk.getMinBlockX() + core;
		int maxX = chunk.getMaxBlockX() - core;
		int minZ = chunk.getMinBlockZ() + core;
		int maxZ = chunk.getMaxBlockZ() - core;
		if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
			return new int[] {x, z};
		}
		if (x < chunk.getMinBlockX() || x > chunk.getMaxBlockX() || z < chunk.getMinBlockZ() || z > chunk.getMaxBlockZ()) {
			return null;
		}
		// Across the line by at most the core's reach (a near or mid thing stays on its line within a block or two).
		int fx = Math.clamp(x, minX, maxX);
		int fz = Math.clamp(z, minZ, maxZ);
		if (Math.abs(fx - x) > core || Math.abs(fz - z) > core) {
			return null;
		}
		return new int[] {fx, fz};
	}

	/** True if no step over {@value #MAX_FACE} lies along the four axis rays out of (x, z) within {@code reach}. */
	static boolean rays(Ground g, int x, int y, int z, int reach) {
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) {
			int prev = y;
			for (int i = 1; i <= reach; i++) {
				int h = g.ground(x + d[0] * i, z + d[1] * i);
				if (Math.abs(h - prev) > MAX_FACE) {
					return false;
				}
				prev = h;
			}
		}
		return true;
	}

	/**
	 * True if every footprint column lies no more than {@value #MAX_FILL} under the crumb's ground (and none over it by
	 * more); {@code corners} reads only the middle and the corners (the noise's model).
	 */
	static boolean footprint(Ground g, int x, int y, int z, String kind, Tier tier, boolean corners) {
		int[][] all = CrumbShapes.footprint(kind, tier, false);
		int lo = 0;
		int hi = 0;
		for (int[] d : all) {
			lo = Math.min(lo, d[0]);
			hi = Math.max(hi, d[0]);
		}
		int most = kind.equals("s17") && tier == Tier.NEAR ? 2 : MAX_FILL;
		for (int[] d : all) {
			boolean corner = (d[0] == lo || d[0] == hi) && (d[1] == lo || d[1] == hi) || d[0] == 0 && d[1] == 0;
			if (corners && !corner) {
				continue;
			}
			int h = g.ground(x + d[0], z + d[1]);
			if (h < y - most || h > y + most) {
				return false;
			}
		}
		return true;
	}

	// --- the live blocks ---

	/** True for a block a crumb's thing may take the place of: air, plants, snow, leaves, water, or the land's own. */
	public static boolean natural(BlockState s) {
		if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(Blocks.SNOW) || s.is(Blocks.WATER) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)
				|| s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS) || s.is(Blocks.SEA_PICKLE) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.MOSS_CARPET)
				|| s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(Blocks.COCOA)
				// R3-V: the land's flowers and ground cover are plants too (vanilla does not let a block replace them, so
				// canBeReplaced missed them: in a cherry grove or a meadow a crumb stepped aside or was left out).
				|| s.is(BlockTags.FLOWERS) || s.is(BlockTags.REPLACEABLE_BY_TREES)) {
			return true;
		}
		if (s.is(BlockTags.LOGS) || s.hasBlockEntity() || s.getFluidState().is(Fluids.LAVA) || s.getFluidState().is(Fluids.FLOWING_LAVA)) {
			return false;
		}
		return s.canBeReplaced() || s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA)
				|| s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE)
				|| s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CALCITE) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUD) || s.is(Blocks.SNOW_BLOCK)
				|| s.is(BlockTags.SUBSTRATE_OVERWORLD) || s.is(Blocks.DIRT_PATH);
	}

	/** True for what a column's ground is read through (not ground): air, plants, leaves, logs, snow, vines, water. */
	static boolean over(BlockState s) {
		return s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.SNOW) || !s.getFluidState().isEmpty() && !s.isSolid()
				|| s.canBeReplaced() || s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)
				|| s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.BEE_NEST) || s.is(Blocks.COCOA);
	}

	/** True for what a column's surface is read through: as {@link #over}, but water stops it. */
	static boolean overDry(BlockState s) {
		return over(s) && !s.is(Blocks.WATER) && !s.getFluidState().is(Fluids.WATER) && !s.getFluidState().is(Fluids.FLOWING_WATER);
	}

	/** The live blocks, each column read once a call. */
	static final class Live implements Ground {
		private final WorldGenLevel level;
		private final Long2LongOpenHashMap columns = new Long2LongOpenHashMap();
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		Live(WorldGenLevel level) {
			this.level = level;
			columns.defaultReturnValue(Long.MIN_VALUE);
		}

		/** Ground in the low half, surface in the high, both offset by the level's bottom. */
		private long column(int x, int z) {
			long key = ChunkPos.pack(x, z);
			long known = columns.get(key);
			if (known != Long.MIN_VALUE) {
				return known;
			}
			int minY = level.getMinY();
			int y = top(x, z);
			BlockState state = level.getBlockState(cursor.set(x, y, z));
			while (y > minY && overDry(state)) {
				state = level.getBlockState(cursor.set(x, --y, z));
			}
			int surface = y;
			while (y > minY && over(state)) {
				state = level.getBlockState(cursor.set(x, --y, z));
			}
			long packed = ((long) (surface - minY) << 32) | (y - minY);
			columns.put(key, packed);
			return packed;
		}

		/** The column's highest block's height (a finished chunk's own map; one being made, worldgen's). */
		private int top(int x, int z) {
			ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
			Heightmap.Types type = chunk instanceof LevelChunk ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
			return chunk.getHeight(type, x & 15, z & 15);
		}

		@Override
		public int ground(int x, int z) {
			return (int) (column(x, z) & 0xFFFFFFFFL) + level.getMinY();
		}

		@Override
		public int surface(int x, int z) {
			return (int) (column(x, z) >>> 32) + level.getMinY();
		}

		@Override
		public boolean lavaNear(int x, int y, int z) {
			for (int dx = -LAVA; dx <= LAVA; dx += 2) {
				for (int dz = -LAVA; dz <= LAVA; dz += 2) {
					int gy = dx == 0 && dz == 0 ? y : surface(x + dx, z + dz);
					for (int dy = -2; dy <= 1; dy++) {
						if (level.getFluidState(cursor.set(x + dx, gy + dy, z + dz)).is(Fluids.LAVA)) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}
}
