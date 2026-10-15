package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.PlayerWatch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The tunnel ladder's detector (D-072): a straight, level, 2-high run of player-dug air behind a player along their
 * heading. Any player's dug footprint counts ({@link PlayerWatch#wasDugByPlayer}: the dug air of a friend's tunnel is a
 * tunnel too), and a cell a player placed something see-through in (a torch, a rail) still belongs to it. Each cell of
 * the run has a solid floor and no fluid in or next to it; enough cells have sealed walls ({@code ladderMinSealedShare}:
 * a strip mine with a branch every 2 cells counts, a tunnel open to a natural cave along it does not). Reads the world
 * and the footprint only, never loads a chunk,
 * walks back at most {@link #MAX_BACK} cells.
 *
 * @param feet    the player's feet cell
 * @param heading where the player is heading; the run lies the other way
 * @param cells   the run's feet cells, nearest first: {@code cells.get(k - 1)} is {@code k} cells back
 * @param sealed  how many of them have sealed walls
 */
record TunnelRun(BlockPos feet, Direction heading, List<BlockPos> cells, int sealed) {
	/** Cost: the detector walks back at most this many cells (D-072). */
	static final int MAX_BACK = 32;
	/** A heading from movement needs at least this much horizontal movement over the samples (blocks), else the look. */
	static final double MIN_MOVE = 2.0;

	TunnelRun {
		cells = List.copyOf(cells);
	}

	/** The way back along the tunnel, from the player toward the steps. */
	Direction back() {
		return heading.getOpposite();
	}

	int length() {
		return cells.size();
	}

	/** The cell {@code k} cells back (1 to {@link #length()}). */
	BlockPos cell(int k) {
		return cells.get(k - 1);
	}

	/** Where the run starts: its far end behind the player (what "a used run" remembers). */
	BlockPos start() {
		return cells.getLast();
	}

	/**
	 * The run behind a player at {@code feet} heading {@code heading}, if it is at least {@code minRun} cells long and
	 * at least {@code minSealedShare} of its cells have sealed walls. Tries the cell under {@code feet} too (a player in
	 * the middle of a jump).
	 */
	static Optional<TunnelRun> detect(ServerLevel level, PlayerWatch watch, BlockPos feet, Direction heading, int minRun, double minSealedShare) {
		Optional<TunnelRun> run = walk(level, watch, feet, heading, minRun, minSealedShare);
		if (run.isEmpty() && level.isLoaded(feet.below()) && passable(level, feet.below())) {
			run = walk(level, watch, feet.below(), heading, minRun, minSealedShare);
		}
		return run;
	}

	private static Optional<TunnelRun> walk(ServerLevel level, PlayerWatch watch, BlockPos feet, Direction heading, int minRun, double minSealedShare) {
		if (!heading.getAxis().isHorizontal() || !level.isLoaded(feet) || !passable(level, feet) || !passable(level, feet.above())) {
			return Optional.empty();
		}
		Direction back = heading.getOpposite();
		List<BlockPos> cells = new ArrayList<>();
		int sealed = 0;
		for (int k = 1; k <= MAX_BACK; k++) {
			BlockPos cell = feet.relative(back, k);
			if (!tunnelCell(level, watch, cell) || fluidNear(level, cell, back)) {
				break;
			}
			cells.add(cell);
			if (walled(level, watch, cell, back)) {
				sealed++;
			}
		}
		if (cells.size() < Math.max(1, Math.min(minRun, MAX_BACK)) || sealed < Math.ceil(minSealedShare * cells.size() - 1.0E-9)) {
			return Optional.empty();
		}
		return Optional.of(new TunnelRun(feet.immutable(), heading, cells, sealed));
	}

	/**
	 * A cell of a player-dug tunnel: feet and head see-through (nothing to bump into, no fluid) and a player's space
	 * (dug out, or something see-through a player placed), on a solid floor. Unloaded is never a tunnel cell.
	 */
	static boolean tunnelCell(ServerLevel level, PlayerWatch watch, BlockPos feet) {
		if (!level.isLoaded(feet) || !level.isLoaded(feet.above()) || !level.isLoaded(feet.below())) {
			return false;
		}
		return playerSpace(level, watch, feet) && playerSpace(level, watch, feet.above()) && floor(level, feet.below());
	}

	/** See-through and a player's: dug out, or placed by a player (a torch, a rail). */
	static boolean playerSpace(ServerLevel level, PlayerWatch watch, BlockPos pos) {
		return passable(level, pos) && (watch.wasDugByPlayer(level, pos) || watch.wasPlacedByPlayer(level, pos));
	}

	/** Nothing to bump into and no fluid. */
	static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
	}

	/** A floor to walk on: its top face is sturdy, and it holds no fluid. */
	static boolean floor(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
	}

	/** Fluid beside the cell (either side, feet or head) or over it: not a dry tunnel. */
	private static boolean fluidNear(ServerLevel level, BlockPos cell, Direction back) {
		for (Direction side : List.of(back.getClockWise(), back.getCounterClockWise())) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos n = cell.above(dy).relative(side);
				if (level.isLoaded(n) && !level.getFluidState(n).isEmpty()) {
					return true;
				}
			}
		}
		BlockPos over = cell.above(2);
		return level.isLoaded(over) && !level.getFluidState(over).isEmpty();
	}

	/**
	 * Both walls closed at the feet and the head: a sealing block right beside the cell, or (a 2-wide tunnel) the
	 * other lane of player space and a sealing block past it.
	 */
	static boolean walled(ServerLevel level, PlayerWatch watch, BlockPos cell, Direction back) {
		for (Direction side : List.of(back.getClockWise(), back.getCounterClockWise())) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos n = cell.above(dy).relative(side);
				if (!level.isLoaded(n) || !level.isLoaded(n.relative(side))) {
					return false;
				}
				if (Tunnels.seals(level.getBlockState(n))) {
					continue;
				}
				if (playerSpace(level, watch, n) && Tunnels.seals(level.getBlockState(n.relative(side)))) {
					continue;
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * The player's heading: the horizontal axis of their movement over {@code samples} (oldest first), if they moved at
	 * least {@link #MIN_MOVE} blocks, else their look ({@code yaw}) snapped to the nearest horizontal direction.
	 */
	static Direction heading(List<Vec3> samples, float yaw) {
		if (samples.size() >= 2) {
			Vec3 moved = samples.getLast().subtract(samples.getFirst());
			double dx = moved.x;
			double dz = moved.z;
			if (dx * dx + dz * dz >= MIN_MOVE * MIN_MOVE) {
				return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			}
		}
		return Direction.fromYRot(yaw);
	}
}
