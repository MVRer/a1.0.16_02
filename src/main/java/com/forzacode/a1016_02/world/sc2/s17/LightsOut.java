package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 17b's plan, pure (docs/SELF_CREATED_2.md, feature 17, "17b Lights out"): which unit of light goes next. A unit is a
 * hall row ({@code k >= 0}: its six in the ceiling and every other light in its span, {@link ArchivePlan#rowLights}) or
 * a cell ({@code -1 - index}: its one light). The dark comes from the way out toward the players: the hall's rows from
 * the door inward, behind the deepest player in the hall, until {@code darkRowsMin} are dark; never a unit with a light
 * within {@value #NEAR} of any player (so never the row a player stands under nor the next one); with players apart it
 * falls between them where it can. A player only in the cells has the cells on their way out darkened instead, from the
 * outer cell toward them, never their own cell nor the one before it, up to {@value #CELLS_MAX}.
 */
final class LightsOut {
	/** No light within this of any player goes (blocks). */
	static final double NEAR = 6.0;
	/** At most this many cells dark in one visit (a 3 x 3 block's worth). */
	static final int CELLS_MAX = 9;
	/** The glimpse stands this far from the player (blocks, horizontal). */
	static final double GLIMPSE_MIN = 20.0;
	static final double GLIMPSE_MAX = 40.0;
	/** No unit next. */
	static final int NONE = Integer.MIN_VALUE;

	private LightsOut() {
	}

	static int cellUnit(int index) {
		return -1 - index;
	}

	static int cellOfUnit(int unit) {
		return -1 - unit;
	}

	/** The lights of a unit (as generated). */
	static List<BlockPos> lights(ArchivePlan plan, int unit) {
		if (unit >= 0) {
			return plan.rowLights(unit);
		}
		int index = cellOfUnit(unit);
		return List.of(plan.cellLight(index % plan.cols, index / plan.cols));
	}

	/**
	 * The next unit to darken for these players' feet, or {@link #NONE}: done (the span is dark), nothing left that may
	 * go, or nobody where it applies.
	 *
	 * @param outer the cell of the open way in (where "the way out" is)
	 */
	static int next(ArchivePlan plan, List<BlockPos> feet, Set<Integer> dark, int outer, int rowsMin) {
		int deepestRow = -1;
		List<Integer> playerRows = new ArrayList<>();
		for (BlockPos p : feet) {
			if (plan.inHallAt(p)) {
				int row = ArchivePlan.rowAt(plan.localF(p.getX(), p.getZ()));
				playerRows.add(row);
				deepestRow = Math.max(deepestRow, row);
			}
		}
		if (deepestRow >= 0) {
			int darkRows = 0;
			for (int u : dark) {
				darkRows += u >= 0 ? 1 : 0;
			}
			if (darkRows >= rowsMin) {
				return NONE;
			}
			for (int k = 0; k <= deepestRow - 2 && k < plan.gridRows(); k++) {
				// Never the row a player stands under, nor the next one either way.
				boolean beside = false;
				for (int row : playerRows) {
					beside |= Math.abs(k - row) <= 1;
				}
				if (!beside && !dark.contains(k) && clear(plan, k, feet)) {
					return k;
				}
			}
			return NONE;
		}
		// Only in the cells: the target is the one deepest in (most steps from the way in).
		int[] steps = plan.cellSteps(outer);
		int target = -1;
		for (BlockPos p : feet) {
			int cell = plan.cellOf(p);
			if (cell >= 0 && (target < 0 || steps[cell] > steps[target])) {
				target = cell;
			}
		}
		if (target < 0) {
			return NONE;
		}
		int darkCells = 0;
		for (int u : dark) {
			darkCells += u < 0 ? 1 : 0;
		}
		if (darkCells >= CELLS_MAX) {
			return NONE;
		}
		List<Integer> path = path(plan, outer, target);
		for (int i = 0; i < path.size() - 2; i++) {
			int unit = cellUnit(path.get(i));
			if (!dark.contains(unit) && clear(plan, unit, feet)) {
				return unit;
			}
		}
		return NONE;
	}

	/** True if no light of this unit is within {@link #NEAR} of any player. */
	static boolean clear(ArchivePlan plan, int unit, List<BlockPos> feet) {
		for (BlockPos light : lights(plan, unit)) {
			for (BlockPos p : feet) {
				if (light.distSqr(p) <= NEAR * NEAR || light.distSqr(p.above()) <= NEAR * NEAR) {
					return false;
				}
			}
		}
		return true;
	}

	/** The cells from {@code from} to {@code to} through open doorways (both ends included), or empty. */
	static List<Integer> path(ArchivePlan plan, int from, int to) {
		int[] steps = plan.cellSteps(to);
		List<Integer> out = new ArrayList<>();
		if (steps[from] < 0) {
			return out;
		}
		int at = from;
		out.add(at);
		while (at != to) {
			int c = at % plan.cols;
			int j = at / plan.cols;
			int next = -1;
			int[][] around = {{c - 1, j, c - 1, j, 1}, {c + 1, j, c, j, 1}, {c, j - 1, c, j - 1, 0}, {c, j + 1, c, j, 0}};
			for (int[] n : around) {
				if (n[0] < 0 || n[0] >= plan.cols || n[1] < 0 || n[1] >= plan.rows || !plan.edgeOpen(n[2], n[3], n[4] == 1)) {
					continue;
				}
				int to2 = plan.cellIndex(n[0], n[1]);
				if (steps[to2] == steps[at] - 1) {
					next = to2;
					break;
				}
			}
			if (next < 0) {
				return List.of();
			}
			at = next;
			out.add(at);
		}
		return out;
	}

	/**
	 * Where 31b's glimpse stands (D-105): on the nave's floor at the far end of the dark rows from the player (toward the
	 * way out), {@value #GLIMPSE_MIN} to {@value #GLIMPSE_MAX} from them, as far as it can; empty if none.
	 */
	static Optional<Vec3> glimpseSpot(ArchivePlan plan, BlockPos player, Set<Integer> dark) {
		if (!plan.inHallAt(player)) {
			return Optional.empty();
		}
		BlockPos best = null;
		for (int f = -1; f >= -plan.length; f--) {
			if (!dark.contains(ArchivePlan.rowAt(f))) {
				continue;
			}
			BlockPos spot = plan.at(0, 1, f);
			double dx = spot.getX() + 0.5 - (player.getX() + 0.5);
			double dz = spot.getZ() + 0.5 - (player.getZ() + 0.5);
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d >= GLIMPSE_MIN && d <= GLIMPSE_MAX) {
				best = spot;
				break;
			}
		}
		return Optional.ofNullable(best).map(Vec3::atBottomCenterOf);
	}

	/**
	 * How far in a player is, walked: through the cells from the way in (8 a cell), then into the hall from its door; -1
	 * outside both.
	 */
	static int depth(ArchivePlan plan, BlockPos feet, int outer) {
		int[] steps = plan.cellSteps(outer);
		if (plan.inHallAt(feet)) {
			int door = steps[plan.doorCell()];
			return door < 0 ? -1 : (door + 1) * ArchivePlan.CELL - plan.localF(feet.getX(), feet.getZ());
		}
		int cell = plan.cellOf(feet);
		return cell < 0 || steps[cell] < 0 ? -1 : steps[cell] * ArchivePlan.CELL;
	}
}
