package com.forzacode.a1016_02.accident.sc2;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * D-097.2 for the pack (docs/SELF_CREATED_2.md, 25): is a player out of a wolf's reach, two or more blocks above every
 * spot a wolf can get to near them? A small walk over the ground around the player ({@link #RADIUS} out, a wolf's
 * moves: a step up of at most one block and a bit, a drop of at most three, swimming across water) from where the
 * wolves are and from the edge of the area (where they could come from), and the player is out of reach if every
 * spot it reaches within {@link #STRIKE} of the player's column has its floor at least {@link #HEIGHT} below the
 * floor they stand on. Floors are collision tops (a snow layer or a slab counts at its height), so a wolf's leap (about
 * 1.1 blocks up, its body 0.85 tall) never touches a player 2 up. Errs toward "in reach": a ledge it cannot tell from
 * a hill keeps the pack hunting, never the reverse.
 */
public final class Reach {
	/** How far out (blocks, each way) the walk looks. */
	public static final int RADIUS = 5;
	/** Spots this near the player's column (Chebyshev) count: a wolf's bite and leap reach. */
	public static final int STRIKE = 3;
	/** The player is out of reach this far above every such spot (blocks). */
	public static final double HEIGHT = 2.0;
	/** A wolf steps up this much at most (a jump). */
	static final double STEP_UP = 1.2;
	/** And drops this much at most. */
	static final double DROP = 3.0;
	private static final double EPS = 1.0E-6;

	private Reach() {
	}

	/**
	 * True if the player standing at {@code feet} is out of reach of anything coming from {@code wolves} or from the edge of
	 * the area: {@link #HEIGHT} or more above the floor of every spot within {@link #STRIKE} of their column. False if
	 * they stand on nothing (falling, flying).
	 */
	public static boolean outOfReach(ServerLevel level, Vec3 feet, Collection<Vec3> wolves) {
		BlockPos column = BlockPos.containing(feet);
		Double own = floorUnder(level, feet);
		if (own == null) {
			return false;
		}
		int minY = column.getY() - 8;
		int maxY = column.getY() + 2;
		Map<BlockPos, Double> floors = new HashMap<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		// Seeds: where the wolves stand, and every spot on the edge of the area.
		for (Vec3 wolf : wolves) {
			BlockPos at = BlockPos.containing(wolf);
			if (inArea(at, column, minY, maxY)) {
				seed(level, at, column, minY, maxY, floors, open);
			}
		}
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != RADIUS) {
					continue;
				}
				for (int y = maxY; y >= minY; y--) {
					seed(level, new BlockPos(column.getX() + dx, y, column.getZ() + dz), column, minY, maxY, floors, open);
				}
			}
		}
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			double floor = floors.get(at);
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				for (int dy = 2; dy >= -4; dy--) {
					BlockPos next = at.relative(dir).above(dy);
					if (floors.containsKey(next) || !inArea(next, column, minY, maxY) || isColumn(next, column)) {
						continue;
					}
					Double f = floor(level, next);
					if (f == null || f - floor > STEP_UP + EPS || floor - f > DROP + EPS) {
						continue;
					}
					floors.put(next, f);
					open.add(next);
				}
			}
		}
		for (Map.Entry<BlockPos, Double> entry : floors.entrySet()) {
			BlockPos at = entry.getKey();
			if (Math.max(Math.abs(at.getX() - column.getX()), Math.abs(at.getZ() - column.getZ())) <= STRIKE && own - entry.getValue() < HEIGHT - EPS) {
				return false;
			}
		}
		return true;
	}

	private static void seed(ServerLevel level, BlockPos at, BlockPos column, int minY, int maxY, Map<BlockPos, Double> floors, ArrayDeque<BlockPos> open) {
		if (floors.containsKey(at) || isColumn(at, column)) {
			return;
		}
		Double f = floor(level, at);
		if (f != null) {
			floors.put(at.immutable(), f);
			open.add(at.immutable());
		}
	}

	private static boolean inArea(BlockPos at, BlockPos column, int minY, int maxY) {
		return Math.abs(at.getX() - column.getX()) <= RADIUS && Math.abs(at.getZ() - column.getZ()) <= RADIUS && at.getY() >= minY && at.getY() <= maxY;
	}

	private static boolean isColumn(BlockPos at, BlockPos column) {
		return at.getX() == column.getX() && at.getZ() == column.getZ();
	}

	/**
	 * The floor (absolute height) a wolf standing with its feet in this cell stands on, or null if it cannot stand there:
	 * the cell's own low shape (a snow layer, a slab, a carpet: its top half at most), else the top of the block under it
	 * (up to a block and a half); water at the surface counts as a floor (it swims) half a block up; lava, fire and a
	 * body that does not fit (0.85 tall) do not.
	 */
	static @Nullable Double floor(ServerLevel level, BlockPos cell) {
		BlockState here = level.getBlockState(cell);
		if (here.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || here.is(BlockTags.FIRE)) {
			return null;
		}
		if (here.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
			BlockState above = level.getBlockState(cell.above());
			return above.getFluidState().isEmpty() && above.getCollisionShape(level, cell.above()).isEmpty() ? cell.getY() + 0.5 : null;
		}
		VoxelShape shape = here.getCollisionShape(level, cell);
		double floor;
		if (!shape.isEmpty()) {
			double top = shape.max(Direction.Axis.Y);
			if (top > 0.5 + EPS) {
				return null;
			}
			floor = cell.getY() + top;
		} else {
			BlockPos below = cell.below();
			BlockState under = level.getBlockState(below);
			VoxelShape ground = under.getCollisionShape(level, below);
			if (ground.isEmpty() || under.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
				return null;
			}
			double top = ground.max(Direction.Axis.Y);
			if (top < 1.0 - EPS) {
				return null; // a low block under an empty cell: the cell below is its own spot
			}
			floor = below.getY() + top;
		}
		// Room for a wolf's body (0.85) over the floor: nothing of any block between the floor and its head.
		double head = floor + 0.85;
		for (int y = (int) Math.floor(floor); y < head - EPS; y++) {
			BlockPos pos = new BlockPos(cell.getX(), y, cell.getZ());
			VoxelShape s = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!s.isEmpty() && y + s.max(Direction.Axis.Y) > floor + EPS && y + s.min(Direction.Axis.Y) < head - EPS) {
				return null;
			}
		}
		return floor;
	}

	/** The floor right under these feet (within 1.5 blocks down), or null if they stand on nothing. */
	static @Nullable Double floorUnder(ServerLevel level, Vec3 feet) {
		BlockPos at = BlockPos.containing(feet.x, feet.y + 0.01, feet.z);
		for (int dy = 0; dy >= -2; dy--) {
			BlockPos pos = at.above(dy);
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double top = pos.getY() + shape.max(Direction.Axis.Y);
				if (top <= feet.y + 0.01 && feet.y - top <= 1.5) {
					return top;
				}
			}
		}
		return null;
	}
}
