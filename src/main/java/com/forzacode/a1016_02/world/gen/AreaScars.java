package com.forzacode.a1016_02.world.gen;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.world.ScarKind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import org.jspecify.annotations.Nullable;

/**
 * The large scars, worked column by column: a dead mountain (grass to dirt, no trees, flowers or tall grass,
 * above a level line and inside a circle) and a bare forest (every leaf gone, trunks left). Membership of a
 * column depends only on the area and the column itself, so every chunk agrees and the edges are sharp lines.
 */
public final class AreaScars {
	/**
	 * One large scar.
	 *
	 * @param contourY dead mountains only: columns whose ground is below this stay alive
	 * @param dappled  a stripped Dappled Forest
	 */
	public record Area(ScarKind kind, int centerX, int centerZ, int radius, int contourY, boolean dappled) {
		public boolean inCircle(int x, int z) {
			long dx = x - centerX;
			long dz = z - centerZ;
			return dx * dx + dz * dz <= (long) radius * radius;
		}

		public boolean contains(int x, int z, int groundY) {
			return inCircle(x, z) && (kind != ScarKind.DEAD_MOUNTAIN || groundY >= contourY);
		}

		public boolean touches(ChunkPos chunk, int margin) {
			int nx = Math.clamp(centerX, chunk.getMinBlockX() - margin, chunk.getMaxBlockX() + margin);
			int nz = Math.clamp(centerZ, chunk.getMinBlockZ() - margin, chunk.getMaxBlockZ() + margin);
			return inCircle(nx, nz);
		}
	}

	/** How far around a removed trunk the leaves of its crown are taken too. */
	private static final int CROWN_REACH = 3;

	private AreaScars() {
	}

	/**
	 * Applies every area to the columns of {@code target}. {@code own} is the chunk being decorated: only there are
	 * whole trees taken (their crowns may reach into the neighbours). Worldgen only.
	 *
	 * @return the trunk bases left standing in a bare forest, nearest the area's center first (own chunk only)
	 */
	public static List<BlockPos> clean(WorldGenLevel level, ChunkPos target, List<Area> areas, boolean own) {
		ChunkAccess chunk = level.getChunk(target.x(), target.z());
		Heightmap.Types[] tops = surfaceTypes(chunk);
		int minY = level.getMinY();
		List<BlockPos> removedLogs = new ArrayList<>();
		List<BlockPos> trunks = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int x = target.getBlockX(lx);
				int z = target.getBlockZ(lz);
				int top = 0;
				for (Heightmap.Types type : tops) {
					top = Math.max(top, chunk.getHeight(type, lx, lz));
				}
				top = Math.min(top + 1, level.getMaxY());
				int ground = Vegetation.groundY(level, x, z, top, minY);
				Area area = first(areas, x, z, ground);
				if (area == null) {
					continue;
				}
				if (area.kind() == ScarKind.DEAD_MOUNTAIN) {
					killColumn(level, pos, x, z, top, ground, own ? removedLogs : null);
				} else {
					stripColumn(level, pos, x, z, top, ground, own ? trunks : null);
				}
			}
		}
		for (BlockPos log : removedLogs) {
			takeCrown(level, log);
		}
		return trunks;
	}

	private static @Nullable Area first(List<Area> areas, int x, int z, int ground) {
		for (Area area : areas) {
			if (area.contains(x, z, ground)) {
				return area;
			}
		}
		return null;
	}

	/** Which surface heightmaps the chunk keeps (worldgen ones before features, final ones after). */
	private static Heightmap.Types[] surfaceTypes(ChunkAccess chunk) {
		List<Heightmap.Types> types = new ArrayList<>(2);
		for (var entry : chunk.getHeightmaps()) {
			if (entry.getKey() == Heightmap.Types.WORLD_SURFACE || entry.getKey() == Heightmap.Types.WORLD_SURFACE_WG) {
				types.add(entry.getKey());
			}
		}
		if (types.isEmpty()) {
			types.add(Heightmap.Types.WORLD_SURFACE_WG);
		}
		return types.toArray(Heightmap.Types[]::new);
	}

	/** Dead mountain: everything that grows goes; grass becomes dirt. */
	private static void killColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, int ground,
			@Nullable List<BlockPos> removedLogs) {
		for (int y = top; y > ground; y--) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (Vegetation.dies(state) || Vegetation.isSnowLayer(state) && y > ground + 1) {
				if (removedLogs != null && Vegetation.isLog(state)) {
					removedLogs.add(pos.immutable());
				}
				level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS);
			}
		}
		pos.set(x, ground, z);
		if (Vegetation.isGrassGround(level.getBlockState(pos))) {
			level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Bare forest: every leaf and what hangs from it goes; trunks stay. */
	private static void stripColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, int ground,
			@Nullable List<BlockPos> trunks) {
		for (int y = top; y > ground; y--) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (Vegetation.isLeafy(state) || Vegetation.isSnowLayer(state) && isLeafyBelow(level, pos)) {
				level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS);
			}
		}
		// The ground scan skips trunks, so a log right above the ground is a trunk base.
		if (trunks != null) {
			pos.set(x, ground + 1, z);
			if (Vegetation.isLog(level.getBlockState(pos))) {
				trunks.add(pos.immutable());
			}
		}
	}

	private static boolean isLeafyBelow(WorldGenLevel level, BlockPos pos) {
		return Vegetation.isLeafy(level.getBlockState(pos.below()));
	}

	/** Takes the leaves around a removed trunk so no crown floats outside the dead line. */
	private static void takeCrown(WorldGenLevel level, BlockPos log) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -CROWN_REACH; dx <= CROWN_REACH; dx++) {
			for (int dy = -1; dy <= CROWN_REACH; dy++) {
				for (int dz = -CROWN_REACH; dz <= CROWN_REACH; dz++) {
					pos.set(log.getX() + dx, log.getY() + dy, log.getZ() + dz);
					if (!Blueprint.canWrite(level, pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (Vegetation.isLeafy(state) || Vegetation.isSnowLayer(state) && isLeafyBelow(level, pos)) {
						level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}
}
