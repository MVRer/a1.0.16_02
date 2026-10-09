package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Spot finders for atmosphere's cards. They only read the world and never load chunks: area scans return nothing
 * unless every chunk they touch is already loaded ({@link #areaLoaded}). Every change still goes through
 * {@code TraceService} or {@code MobTamper}, which do the view checks.
 */
public final class WorldScan {
	private WorldScan() {
	}

	/** A wall block of the player's house that can go: removing it opens a 1x1 window from inside onto the open air. */
	public record WallSpot(BlockPos pos, Direction outward, boolean eyeLevel) {
	}

	/** A place to stand inside, next to a window, and which way the window looks out. */
	public record WindowSpot(BlockPos feet, BlockPos window, Direction outward) {
	}

	// --- basic predicates ---

	public static boolean loaded(Level level, int x, int z) {
		return level.hasChunk(x >> 4, z >> 4);
	}

	/** True if every chunk within {@code radius} blocks (horizontally) of {@code center} is loaded. */
	public static boolean areaLoaded(Level level, BlockPos center, int radius) {
		for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
			for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Nothing that blocks motion above this block (heightmap: updates at once, unlike sky light). */
	public static boolean openToSky(Level level, BlockPos pos) {
		return loaded(level, pos.getX(), pos.getZ()) && pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
	}

	/** Something with collision within {@code maxUp} blocks above. */
	public static boolean covered(Level level, BlockPos pos, int maxUp) {
		BlockPos.MutableBlockPos p = pos.mutable();
		for (int i = 1; i <= maxUp; i++) {
			p.setY(pos.getY() + i);
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** No collision and no fluid. */
	public static boolean passable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	/** A sturdy floor and {@code height} passable blocks from the feet up. */
	public static boolean standable(Level level, BlockPos feet, int height) {
		if (!level.isLoaded(feet)) {
			return false;
		}
		BlockPos below = feet.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
			return false;
		}
		for (int i = 0; i < height; i++) {
			if (!passable(level, feet.above(i))) {
				return false;
			}
		}
		return true;
	}

	/** The surface standing spot at this column, or null (unloaded, water, no room). */
	public static @Nullable BlockPos surfaceSpot(Level level, int x, int z, int height) {
		if (!loaded(level, x, z)) {
			return null;
		}
		BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		return standable(level, feet, height) ? feet : null;
	}

	/** A surface spot about {@code distance} from {@code origin} along {@code yawRadians}, trying small angle offsets. */
	public static @Nullable BlockPos surfaceSpotToward(Level level, Vec3 origin, double angle, double distance, int height) {
		double[] offsets = {0.0, 0.25, -0.25, 0.5, -0.5};
		for (double offset : offsets) {
			double a = angle + offset;
			int x = (int) Math.floor(origin.x + Math.cos(a) * distance);
			int z = (int) Math.floor(origin.z + Math.sin(a) * distance);
			BlockPos spot = surfaceSpot(level, x, z, height);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	public static boolean isWindow(BlockState state) {
		return state.is(BlockTags.IMPERMEABLE) && !state.is(Blocks.BARRIER) || state.getBlock() instanceof IronBarsBlock;
	}

	// --- finders ---

	/** Chests (any, including trapped) within {@code radius} of {@code center}, from loaded chunks only. */
	public static List<BlockPos> chestsNear(ServerLevel level, BlockPos center, int radius) {
		List<BlockPos> found = new ArrayList<>();
		double r2 = (double) radius * radius;
		for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
			for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (blockEntity instanceof ChestBlockEntity && blockEntity.getBlockPos().distSqr(center) <= r2) {
						found.add(blockEntity.getBlockPos().immutable());
					}
				}
			}
		}
		return found;
	}

	/** Every removable wall block of a house the player built near {@code base}. Empty if the area is not loaded. */
	public static List<WallSpot> houseWalls(ServerLevel level, BlockPos base, int radius) {
		PlayerWatch watch = Services.watch();
		List<WallSpot> spots = new ArrayList<>();
		if (!areaLoaded(level, base, radius + 1)) {
			return spots;
		}
		for (BlockPos pos : watch.placedNear(level, base, radius, BlockState::isSolidRender)) {
			WallSpot spot = wallSpot(level, pos, watch);
			if (spot != null) {
				spots.add(spot);
			}
		}
		return spots;
	}

	/**
	 * A player-built opaque block in the middle of a wall: the wall goes on above it (player-built) and below it, its
	 * neighbours along the wall are opaque, there is open air outside (nothing above) and a covered room inside. So
	 * it is never part of the roof and nothing hangs on it.
	 */
	public static @Nullable WallSpot wallSpot(Level level, BlockPos pos, PlayerWatch watch) {
		BlockState state = level.getBlockState(pos);
		if (!state.isSolidRender() || state.hasBlockEntity()) {
			return null;
		}
		BlockPos above = pos.above();
		if (!level.getBlockState(above).isSolidRender() || !watch.wasPlacedByPlayer((ServerLevel) level, above)
				|| !level.getBlockState(pos.below()).isSolidRender()) {
			return null;
		}
		for (Direction out : Direction.Plane.HORIZONTAL) {
			BlockPos outside = pos.relative(out);
			BlockPos inside = pos.relative(out.getOpposite());
			if (!level.getBlockState(outside).isAir() || !level.getBlockState(inside).isAir()) {
				continue;
			}
			if (!level.getBlockState(pos.relative(out.getClockWise())).isSolidRender()
					|| !level.getBlockState(pos.relative(out.getCounterClockWise())).isSolidRender()) {
				continue;
			}
			if (!openToSky(level, outside) || openToSky(level, inside) || !covered(level, inside, 8)) {
				continue;
			}
			BlockPos floor = inside.below(2);
			boolean eyeLevel = level.getBlockState(inside.below()).isAir() && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
			return new WallSpot(pos.immutable(), out, eyeLevel);
		}
		return null;
	}

	/**
	 * Flood-fills the space around {@code start} through every block that is not a full collision cube. Returns its
	 * cells if it is closed (no more than {@code maxCells}, nothing open to the sky, every neighbour loaded), else null.
	 * Doors, panes and fences count as openings; full glass counts as wall.
	 */
	public static @Nullable List<BlockPos> sealedRoom(Level level, BlockPos start, int maxCells) {
		if (!level.isLoaded(start) || level.getBlockState(start).isCollisionShapeFullBlock(level, start)) {
			return null;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start.immutable());
		queue.add(start.immutable());
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			if (openToSky(level, pos)) {
				return null;
			}
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (seen.contains(n)) {
					continue;
				}
				if (!level.isLoaded(n)) {
					return null;
				}
				if (level.getBlockState(n).isCollisionShapeFullBlock(level, n)) {
					continue;
				}
				if (seen.size() >= maxCells) {
					return null;
				}
				seen.add(n);
				queue.add(n);
			}
		}
		return new ArrayList<>(seen);
	}

	/** Sealed rooms of the player's house near {@code base} (distinct, at most {@code maxRooms}). Empty if the area is not loaded. */
	public static List<List<BlockPos>> sealedRoomsNear(ServerLevel level, BlockPos base, int radius, int maxCells, int maxRooms) {
		List<List<BlockPos>> rooms = new ArrayList<>();
		if (!areaLoaded(level, base, radius + 1)) {
			return rooms;
		}
		Set<BlockPos> visited = new HashSet<>();
		int starts = 0;
		for (BlockPos placed : Services.watch().placedNear(level, base, radius, state -> true)) {
			for (Direction dir : Direction.values()) {
				BlockPos start = placed.relative(dir);
				if (visited.contains(start) || !level.isLoaded(start) || !level.getBlockState(start).isAir() || !covered(level, start, 8)) {
					continue;
				}
				if (++starts > 48 || rooms.size() >= maxRooms) {
					return rooms;
				}
				List<BlockPos> room = sealedRoom(level, start, maxCells);
				if (room == null) {
					visited.add(start.immutable());
					continue;
				}
				visited.addAll(room);
				rooms.add(room);
			}
		}
		return rooms;
	}

	/** An indoor corner (two walls at a right angle) within {@code radius} of {@code center}, nearest first, away from {@code avoid}. */
	public static @Nullable BlockPos corner(Level level, BlockPos center, int radius, Vec3 avoid) {
		if (!areaLoaded(level, center, radius + 1)) {
			return null;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
			if (!level.getBlockState(pos).isAir() || !standable(level, pos, 1) || !covered(level, pos, 6)
					|| avoid.distanceToSqr(Vec3.atCenterOf(pos)) < 4.0) {
				continue;
			}
			boolean corner = false;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				if (level.getBlockState(pos.relative(dir)).isSolidRender() && level.getBlockState(pos.relative(dir.getClockWise())).isSolidRender()) {
					corner = true;
					break;
				}
			}
			double dist = pos.distSqr(center);
			if (corner && dist > 1.0 && dist < bestDist) {
				best = pos.immutable();
				bestDist = dist;
			}
		}
		return best;
	}

	/** The nearest indoor spot next to a window (glass or pane at head height) within {@code radius}, skipping {@code used}. */
	public static @Nullable WindowSpot windowSpot(Level level, BlockPos near, int radius, Set<BlockPos> used) {
		if (!areaLoaded(level, near, radius + 1)) {
			return null;
		}
		WindowSpot best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(near.offset(-radius, -3, -radius), near.offset(radius, 3, radius))) {
			double dist = pos.distSqr(near);
			if (dist >= bestDist || used.contains(pos) || !standable(level, pos, 2) || !covered(level, pos.above(), 6)) {
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos window = pos.above().relative(dir);
				if (isWindow(level.getBlockState(window)) && !openToSky(level, pos)) {
					best = new WindowSpot(pos.immutable(), window.immutable(), dir);
					bestDist = dist;
					break;
				}
			}
		}
		return best;
	}

	/** Open water (two deep) between {@code minDist} and {@code maxDist} from {@code center}: the block a fish swims in. */
	public static List<BlockPos> waterSpots(Level level, Vec3 center, int minDist, int maxDist, RandomSource random, int wanted) {
		List<BlockPos> spots = new ArrayList<>();
		for (int attempt = 0; attempt < 64 && spots.size() < wanted; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double dist = minDist + random.nextDouble() * Math.max(1, maxDist - minDist);
			int x = (int) Math.floor(center.x + Math.cos(angle) * dist);
			int z = (int) Math.floor(center.z + Math.sin(angle) * dist);
			if (!loaded(level, x, z)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockPos surface = new BlockPos(x, top - 1, z);
			BlockPos below = surface.below();
			if (level.getFluidState(surface).is(FluidTags.WATER) && level.getFluidState(below).is(FluidTags.WATER)
					&& level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				spots.add(below.immutable());
			}
		}
		return spots;
	}

	/** A natural solid block near {@code origin} (1 above to 12 below), out of every player's view: where mining would be. */
	public static @Nullable BlockPos miningSpot(ServerLevel level, BlockPos origin, int minDist, int maxDist, RandomSource random) {
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double dist = minDist + random.nextDouble() * Math.max(1, maxDist - minDist);
			BlockPos pos = origin.offset((int) Math.round(Math.cos(angle) * dist), 1 - random.nextInt(14), (int) Math.round(Math.sin(angle) * dist));
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			boolean natural = state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.MINEABLE_WITH_PICKAXE);
			if (state.isSolidRender() && natural && !Services.watch().wasPlacedByPlayer(level, pos) && Services.traces().isOutOfView(level, pos)) {
				return pos.immutable();
			}
		}
		return null;
	}
}
