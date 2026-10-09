package com.forzacode.a1016_02.world.gen;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Straight lines nature does not make: the stair down to bedrock, the level cut through a hill and the 2x2
 * tunnel with daylight at both ends. Each takes its terrain from a {@link Terrain}, so worldgen (noise) and live
 * placement (loaded blocks) share the geometry.
 */
public final class Carves {
	/** A carve, the place lore should use, and the far ends (for a cross at a tunnel mouth). */
	public record Carve(Blueprint blueprint, BlockPos site, int size, BlockPos endA, BlockPos endB, Direction axis) {
	}

	/** Head room above each step of the stair (feet, head, and one for walking down). */
	private static final int STAIR_HEIGHT = 3;

	private Carves() {
	}

	/** How strictly a stair's path is checked. */
	public enum PathCheck {
		/** Solid all the way: no cave, no water, no lava (old scars: it ends in nothing). */
		SOLID,
		/** No water or lava; caves are fine (debug placement in real terrain). */
		NO_FLUID
	}

	/**
	 * A 1x1 staircase cut into stone from the ground at (x, z) straight down to the bedrock layer. Returns null if
	 * the path fails the check.
	 */
	public static @Nullable Carve stair(Terrain terrain, int x, int z, Direction dir, PathCheck check) {
		int groundY = terrain.ground(x, z);
		int bottomFeet = terrain.minY() + 5;
		if (groundY <= terrain.seaLevel() || terrain.wet(x, z) || groundY - bottomFeet < 16) {
			return null;
		}
		Blueprint bp = new Blueprint();
		BlockPos last = null;
		int steps = groundY - bottomFeet + 1;
		for (int i = 0; i < steps; i++) {
			int sx = x + dir.getStepX() * i;
			int sz = z + dir.getStepZ() * i;
			int feet = groundY - i;
			if (i >= 4 && !pathClear(terrain, sx, feet, sz, check)) {
				return null;
			}
			for (int up = 0; up < STAIR_HEIGHT; up++) {
				bp.clear(new BlockPos(sx, feet + up, sz));
			}
			if (i >= 3) {
				Direction side = dir.getClockWise();
				for (int up = -1; up < STAIR_HEIGHT; up++) {
					bp.unore(new BlockPos(sx + side.getStepX(), feet + up, sz + side.getStepZ()));
					bp.unore(new BlockPos(sx - side.getStepX(), feet + up, sz - side.getStepZ()));
				}
				bp.unore(new BlockPos(sx, feet - 1, sz));
				bp.unore(new BlockPos(sx, feet + STAIR_HEIGHT, sz));
			}
			last = new BlockPos(sx, feet, sz);
		}
		if (last == null) {
			return null;
		}
		BlockPos top = new BlockPos(x, groundY, z);
		return new Carve(bp, last, 1, top, last, dir);
	}

	/** True if the stair cell, its floor and its ceiling pass the check in the terrain. */
	private static boolean pathClear(Terrain terrain, int x, int feet, int z, PathCheck check) {
		for (int up = -1; up <= STAIR_HEIGHT; up++) {
			BlockState state = terrain.block(x, feet + up, z);
			if (!state.getFluidState().isEmpty() || check == PathCheck.SOLID && state.isAir()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A perfectly level slice through a hill: a straight trench {@code width} wide along {@code axis} through
	 * (x, z), its floor {@code depth} blocks below the top, open at both ends. Null if the hill is not a hill.
	 */
	public static @Nullable Carve cut(Terrain terrain, int x, int z, Direction axis, int depth, int width, int maxHalfLength) {
		int top = terrain.ground(x, z);
		int floor = top - depth;
		if (floor <= terrain.seaLevel() + 1 || terrain.wet(x, z)) {
			return null;
		}
		int[] ends = ends(terrain, x, z, axis, floor, maxHalfLength);
		if (ends == null || ends[1] - ends[0] < 12) {
			return null;
		}
		Direction side = axis.getClockWise();
		Blueprint bp = new Blueprint();
		for (int t = ends[0]; t <= ends[1]; t++) {
			int cx = x + axis.getStepX() * t;
			int cz = z + axis.getStepZ() * t;
			for (int w = 0; w < width; w++) {
				int wx = cx + side.getStepX() * (w - width / 2);
				int wz = cz + side.getStepZ() * (w - width / 2);
				int ground = terrain.ground(wx, wz);
				if (terrain.wet(wx, wz)) {
					return null;
				}
				bp.add(new Blueprint.Column(new BlockPos(wx, floor + 1, wz), Math.max(ground, floor) + 32, false));
			}
		}
		int mid = (ends[0] + ends[1]) / 2;
		BlockPos site = new BlockPos(x + axis.getStepX() * mid, floor + 1, z + axis.getStepZ() * mid);
		return new Carve(bp, site, (ends[1] - ends[0]) / 2 + 1, at(x, floor, z, axis, ends[0] - 1), at(x, floor, z, axis, ends[1] + 1), axis);
	}

	/**
	 * A straight 2x2 tunnel through a mountain at floor level {@code floor}, with daylight at both ends. Null if
	 * the mountain does not stand over the whole tunnel or the ends are not open.
	 */
	public static @Nullable Carve tunnel(Terrain terrain, int x, int z, Direction axis, int floor, int maxHalfLength) {
		if (floor <= terrain.seaLevel() + 1 || terrain.ground(x, z) < floor + 6) {
			return null;
		}
		int[] ends = ends(terrain, x, z, axis, floor + 1, maxHalfLength);
		if (ends == null || ends[1] - ends[0] < 20) {
			return null;
		}
		Direction side = axis.getClockWise();
		Blueprint bp = new Blueprint();
		int covered = 0;
		for (int t = ends[0]; t <= ends[1]; t++) {
			int cx = x + axis.getStepX() * t;
			int cz = z + axis.getStepZ() * t;
			for (int w = 0; w < 2; w++) {
				int wx = cx + side.getStepX() * w;
				int wz = cz + side.getStepZ() * w;
				if (terrain.wet(wx, wz)) {
					return null;
				}
				if (w == 0 && terrain.ground(wx, wz) >= floor + 4) {
					covered++;
				}
				bp.clear(new BlockPos(wx, floor + 1, wz));
				bp.clear(new BlockPos(wx, floor + 2, wz));
			}
		}
		if (covered < (ends[1] - ends[0]) * 2 / 3) {
			return null;
		}
		int mid = (ends[0] + ends[1]) / 2;
		BlockPos site = new BlockPos(x + axis.getStepX() * mid, floor + 1, z + axis.getStepZ() * mid);
		return new Carve(bp, site, (ends[1] - ends[0]) / 2 + 1, at(x, floor, z, axis, ends[0] - 1), at(x, floor, z, axis, ends[1] + 1), axis);
	}

	private static BlockPos at(int x, int y, int z, Direction axis, int t) {
		return new BlockPos(x + axis.getStepX() * t, y, z + axis.getStepZ() * t);
	}

	/**
	 * Walks out from (x, z) both ways along the axis until the ground drops to {@code level} or below. Returns the
	 * offsets of the last blocks above that level on each side, or null if either side never drops.
	 */
	private static int @Nullable [] ends(Terrain terrain, int x, int z, Direction axis, int level, int maxHalfLength) {
		int lo = 0;
		int hi = 0;
		boolean loFound = false;
		boolean hiFound = false;
		for (int t = 1; t <= maxHalfLength && !(loFound && hiFound); t++) {
			if (!hiFound) {
				if (terrain.ground(x + axis.getStepX() * t, z + axis.getStepZ() * t) <= level) {
					hiFound = true;
				} else {
					hi = t;
				}
			}
			if (!loFound) {
				if (terrain.ground(x - axis.getStepX() * t, z - axis.getStepZ() * t) <= level) {
					loFound = true;
				} else {
					lo = -t;
				}
			}
		}
		return loFound && hiFound ? new int[] {lo, hi} : null;
	}

}
