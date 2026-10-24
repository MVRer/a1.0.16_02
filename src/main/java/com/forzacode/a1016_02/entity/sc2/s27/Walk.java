package com.forzacode.a1016_02.entity.sc2.s27;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * 27's walk off (docs/SELF_CREATED_2.md, 27), on any {@link BlockGetter}: the server tries ways with it to pick the copy's
 * bearing, the viewer's client walks the copy with it over the blocks it has. A walk is what a player walking would do:
 * one block at a time along a bearing, up a block at most (a jump), down three at most, never into water or lava, room
 * for a player; turning a little when the way is blocked. Pure but for the block reads.
 */
public final class Walk {
	/** A player's walking speed, blocks a tick (4.317 blocks a second). */
	public static final double SPEED = 4.317 / 20.0;
	/** Up at most this much between two steps (a jump)... */
	public static final double MAX_UP = 1.0;
	/** ...and down at most this much. */
	public static final int MAX_DOWN = 3;
	/** The turns a step tries from its bearing when the way ahead is blocked, in order. */
	static final double[] TURNS = {0, 20, -20, 40, -40, 65, -65, 90, -90};

	/** One step: where the feet are, and the bearing that got there. */
	public record Step(Vec3 feet, double yaw) {
	}

	private Walk() {
	}

	/**
	 * The next step from {@code feet}, {@code length} blocks along {@code yaw} (Minecraft's yaw: south 0, west 90), or turned
	 * by the smallest of {@link #TURNS} that finds standing room; null if none does.
	 */
	public static @Nullable Step next(BlockGetter level, Vec3 feet, double yaw, double length) {
		for (double turn : TURNS) {
			double bearing = yaw + turn;
			double rad = Math.toRadians(bearing);
			double x = feet.x - Math.sin(rad) * length;
			double z = feet.z + Math.cos(rad) * length;
			Vec3 at = standing(level, x, z, feet.y, MAX_UP, MAX_DOWN);
			if (at != null && headroomBetween(level, feet, at)) {
				return new Step(at, bearing);
			}
		}
		return null;
	}

	/**
	 * Feet on the ground in the column at ({@code x}, {@code z}) near {@code aroundY}, from {@code up} above to {@code down}
	 * below (the nearest height first), or null: a floor with a top to stand on, two cells of room above it with nothing
	 * solid, no fluid in them or in the floor.
	 */
	public static @Nullable Vec3 standing(BlockGetter level, double x, double z, double aroundY, double up, int down) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int base = Mth.floor(aroundY + 1.0E-6);
		int ups = (int) Math.ceil(up);
		for (int k = 0; k <= 2 * Math.max(ups, down) + 1; k++) {
			int dy = k % 2 == 1 ? (k + 1) / 2 : -(k / 2);
			if (dy > ups || dy < -down) {
				continue;
			}
			int cell = base + dy;
			double top = floorTop(level, m.set(bx, cell - 1, bz));
			double feetY;
			if (!Double.isNaN(top)) {
				feetY = cell - 1 + top;
			} else {
				// A floor lower in the cell itself (a slab, a path): stand on it.
				double inCell = floorTop(level, m.set(bx, cell, bz));
				if (Double.isNaN(inCell) || inCell > 0.6) {
					continue;
				}
				feetY = cell + inCell;
			}
			if (feetY - aroundY > up + 1.0E-6 || aroundY - feetY > down + 1.0E-6) {
				continue;
			}
			if (room(level, bx, feetY, bz, m)) {
				return new Vec3(x, feetY, z);
			}
		}
		return null;
	}

	/** The top of the collision shape of the block there (0 to 1.5), NaN if it has none or holds a fluid. */
	static double floorTop(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.getFluidState().isEmpty()) {
			return Double.NaN;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return Double.NaN;
		}
		return shape.max(Direction.Axis.Y);
	}

	/** Two cells of nothing solid and no fluid from {@code feetY} up (a player is 1.8 tall), at the column. */
	static boolean room(BlockGetter level, int x, double feetY, int z, BlockPos.MutableBlockPos m) {
		int from = Mth.floor(feetY + 1.0E-4);
		int to = Mth.floor(feetY + 1.8);
		for (int y = from; y <= to; y++) {
			BlockState state = level.getBlockState(m.set(x, y, z));
			if (!state.getFluidState().isEmpty()) {
				return false;
			}
			VoxelShape shape = state.getCollisionShape(level, m);
			if (!shape.isEmpty() && shape.max(Direction.Axis.Y) + y > feetY + 1.0E-4 && shape.min(Direction.Axis.Y) + y < feetY + 1.8) {
				return false;
			}
		}
		return true;
	}

	/** A jump up needs the cell over the start free too (the head goes up before the feet go over). */
	static boolean headroomBetween(BlockGetter level, Vec3 from, Vec3 to) {
		if (to.y - from.y <= 0.6) {
			return true;
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		return room(level, Mth.floor(from.x), to.y, Mth.floor(from.z), m);
	}

	/** Where the ground is under ({@code x}, {@code z}) from {@code fromY} down at most {@code down} blocks, NaN if nowhere. */
	public static double groundBelow(BlockGetter level, double x, double z, double fromY, int down) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int start = Mth.floor(fromY + 1.0E-4);
		for (int y = start; y >= start - down; y--) {
			BlockState state = level.getBlockState(m.set(bx, y, bz));
			if (!state.getFluidState().isEmpty()) {
				return Double.NaN;
			}
			VoxelShape shape = state.getCollisionShape(level, m);
			if (!shape.isEmpty()) {
				double top = y + shape.max(Direction.Axis.Y);
				if (top <= fromY + 0.6) {
					return top;
				}
			}
		}
		return Double.NaN;
	}
}
