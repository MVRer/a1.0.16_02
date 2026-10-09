package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Terrain queries for placement. Reading a block in an unloaded chunk loads (or generates) that chunk. */
final class Terrain {
	private Terrain() {
	}

	/**
	 * Random columns (y = 0) between {@code min} and {@code max} blocks (horizontal) from {@code origin}, unloaded
	 * chunks first, then chunks no player has visited, then the rest. That keeps placement where nobody looks.
	 */
	static List<BlockPos> candidates(ServerLevel level, BlockPos origin, int min, int max, int count, RandomSource random) {
		List<BlockPos> list = new ArrayList<>(count);
		for (int n = 0; n < count; n++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * Math.max(0, max - min);
			list.add(new BlockPos(origin.getX() + Mth.floor(Math.cos(angle) * distance), 0, origin.getZ() + Mth.floor(Math.sin(angle) * distance)));
		}
		list.sort(Comparator.comparingInt(pos -> seenRank(level, pos)));
		return list;
	}

	/** 0: chunk not loaded, 1: loaded but never visited, 2: visited. */
	static int seenRank(ServerLevel level, BlockPos pos) {
		ChunkPos chunk = ChunkPos.containing(pos);
		if (!level.hasChunk(chunk.x(), chunk.z())) {
			return 0;
		}
		return Services.watch().lastVisitDay(level, chunk) < 0 ? 1 : 2;
	}

	/** The topmost solid, non-leaf block of a column (loads the chunk). */
	static BlockPos ground(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
	}

	/** The topmost block of the sea floor under water (loads the chunk). */
	static BlockPos seaFloor(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1, z);
	}

	/** Water depth above the sea floor at this column, 0 if dry. */
	static int waterDepth(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
	}

	static boolean isAirOrReplaceable(BlockState state) {
		return state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	/** Natural ground he can carve or take: stone, deepslate, ores, dirt, sand, gravel. Never a block entity. */
	static boolean isNaturalSolid(BlockState state) {
		return state.isSolidRender() && !state.hasBlockEntity()
				&& (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(ORES)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || isSoil(state));
	}

	private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("ores"));

	/** Ground a tree can stand on: dirt, grass, podzol, mycelium, mud. */
	static boolean isSoil(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
				|| state.is(Blocks.MUD);
	}

	/** A spot where something can stand: replaceable and dry, with solid ground below. */
	static boolean isFloor(ServerLevel level, BlockPos pos) {
		return isAirOrReplaceable(level.getBlockState(pos)) && level.getBlockState(pos.below()).isSolid()
				&& level.getBlockEntity(pos) == null;
	}

	/** Dark: no sky light and no block light. */
	static boolean isDark(ServerLevel level, BlockPos pos) {
		return level.getBrightness(LightLayer.SKY, pos) == 0 && level.getBrightness(LightLayer.BLOCK, pos) == 0;
	}

	/** True if any of the six neighbours (or the block itself) holds a fluid. */
	static boolean touchesFluid(ServerLevel level, BlockPos pos) {
		if (!level.getFluidState(pos).isEmpty()) {
			return true;
		}
		for (Direction dir : Direction.values()) {
			if (!level.getFluidState(pos.relative(dir)).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A cave floor in this column between {@code yMax} and {@code yMin}: dry air with 2 blocks of headroom, solid
	 * ground, no sky light, at least 12 blocks under the surface.
	 */
	static Optional<BlockPos> caveFloor(ServerLevel level, int x, int z, int yMax, int yMin) {
		int top = Math.min(yMax, ground(level, x, z).getY() - 12);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
		for (int y = top; y > Math.max(yMin, level.getMinY() + 1); y--) {
			pos.setY(y);
			if (isFloor(level, pos) && level.getBlockState(pos.below()).isSolidRender() && isAirOrReplaceable(level.getBlockState(pos.above()))
					&& level.getBrightness(LightLayer.SKY, pos) == 0 && !touchesFluid(level, pos)) {
				return Optional.of(pos.immutable());
			}
		}
		return Optional.empty();
	}

	/** Horizontal directions in a random order. */
	static List<Direction> shuffledHorizontal(RandomSource random) {
		List<Direction> dirs = new ArrayList<>(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
		for (int n = dirs.size() - 1; n > 0; n--) {
			int k = random.nextInt(n + 1);
			Direction swap = dirs.get(n);
			dirs.set(n, dirs.get(k));
			dirs.set(k, swap);
		}
		return dirs;
	}

	/** A free floor spot within {@code radius} of {@code center} (closest first), not in {@code exclude}. */
	static Optional<BlockPos> floorNear(ServerLevel level, BlockPos center, int radius, int dy, List<BlockPos> exclude) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -dy, -radius), center.offset(radius, dy, radius))) {
			if (exclude.contains(pos) || !isFloor(level, pos) || !level.getFluidState(pos).isEmpty()) {
				continue;
			}
			double dist = pos.distSqr(center);
			if (dist < bestDist) {
				bestDist = dist;
				best = pos.immutable();
			}
		}
		return Optional.ofNullable(best);
	}

	/** A horizontal direction from {@code pos} toward open, replaceable space (for chests and signs to face). */
	static Direction openSide(ServerLevel level, BlockPos pos, Direction fallback) {
		for (Direction dir : List.of(fallback, fallback.getClockWise(), fallback.getCounterClockWise(), fallback.getOpposite())) {
			if (isAirOrReplaceable(level.getBlockState(pos.relative(dir)))) {
				return dir;
			}
		}
		return fallback;
	}

	/** Flat, dry ground for a {@code size}x{@code size} footprint centered on the column: returns the floor y (ground + 1). */
	static Optional<Integer> flatFloor(ServerLevel level, BlockPos column, int size, int tolerance) {
		int half = size / 2;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				BlockPos ground = ground(level, column.getX() + dx, column.getZ() + dz);
				BlockState state = level.getBlockState(ground);
				if (!state.isSolidRender() || !level.getFluidState(ground.above()).isEmpty() || state.is(BlockTags.LOGS)) {
					return Optional.empty();
				}
				min = Math.min(min, ground.getY());
				max = Math.max(max, ground.getY());
			}
		}
		return max - min <= tolerance ? Optional.of(max + 1) : Optional.empty();
	}
}
