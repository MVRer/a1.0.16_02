package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plans the cross over a marked death: a post 1 wide and 3 to 4 tall with one arm on each side just under its top,
 * built only by moving blocks that are already there, taken from the ground around the spot (he cannot create).
 */
public final class CrossBuilder {
	/**
	 * A cross that can be built now.
	 *
	 * @param base  the bottom of the post
	 * @param cells post bottom to top, then the two arms
	 * @param ops   one move per cell
	 */
	public record Plan(BlockPos base, Direction.Axis axis, List<BlockPos> cells, List<TraceOp> ops) {
	}

	/** Standing spots looked at for material per plan (each reads a box of blocks). */
	private static final int MAX_BASES = 6;
	/** Material comes from the ground near the spot's own level, at most this far above or below the post's base. */
	private static final int SOURCE_DEPTH = 3;

	private CrossBuilder() {
	}

	/** The cells of a cross standing on {@code base}. */
	public static List<BlockPos> cells(BlockPos base, int height, Direction.Axis axis) {
		List<BlockPos> cells = new ArrayList<>();
		for (int y = 0; y < height; y++) {
			cells.add(base.above(y));
		}
		BlockPos armRow = base.above(height - 2);
		Direction side = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
		cells.add(armRow.relative(side));
		cells.add(armRow.relative(side.getOpposite()));
		return cells;
	}

	/** The nearest buildable cross at {@code death}, with its material taken from around it, or empty. */
	public static Optional<Plan> plan(ServerLevel level, BlockPos death, int height, AccidentConfig cfg) {
		int r = cfg.crossSearchRadius;
		List<BlockPos> bases = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(death.offset(-r, -4, -r), death.offset(r, 4, r))) {
			bases.add(pos.immutable());
		}
		bases.sort(Comparator.comparingDouble(p -> p.distSqr(death)));
		int tried = 0;
		for (BlockPos base : bases) {
			if (!level.isLoaded(base) || !level.getBlockState(base.below()).isFaceSturdy(level, base.below(), Direction.UP)) {
				continue;
			}
			for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
				List<BlockPos> cells = cells(base, height, axis);
				if (!cells.stream().allMatch(cell -> fillable(level, cell))) {
					continue;
				}
				if (++tried > MAX_BASES) {
					return Optional.empty();
				}
				List<BlockPos> sources = sources(level, base, cells, cfg.crossSourceRadius);
				if (sources.size() < cells.size()) {
					continue;
				}
				List<TraceOp> ops = new ArrayList<>();
				for (int i = 0; i < cells.size(); i++) {
					ops.add(TraceOp.move(sources.get(i), cells.get(i)));
				}
				return Optional.of(new Plan(base, axis, cells, ops));
			}
		}
		return Optional.empty();
	}

	/** Air, plants, snow layers or fluid, with no block entity. */
	static boolean fillable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return level.isLoaded(pos) && state.canBeReplaced() && !state.hasBlockEntity();
	}

	/**
	 * Natural blocks around the cross that can be taken without consequence: plain full blocks, not placed by a
	 * player, open on top, with nothing attached and no fluid they hold back. The most common kind comes first so the
	 * cross is of one material where it can be.
	 */
	static List<BlockPos> sources(ServerLevel level, BlockPos base, List<BlockPos> cells, int radius) {
		Set<BlockPos> keep = new LinkedHashSet<>(cells);
		keep.add(base.below());
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-radius, -SOURCE_DEPTH, -radius), base.offset(radius, SOURCE_DEPTH, radius))) {
			BlockPos p = pos.immutable();
			if (keep.contains(p) || !level.isLoaded(p) || !Scan.takeable(level, p) || Scan.ore(level.getBlockState(p))
					|| !level.getBlockState(p).isSolidRender() || Services.watch().wasPlacedByPlayer(level, p) || !safeToTake(level, p, keep)) {
				continue;
			}
			found.add(p);
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(base)));
		// The material is what most of the nearest ground is made of; that kind goes first, nearest first.
		Map<Block, Integer> kinds = new HashMap<>();
		for (BlockPos p : found.subList(0, Math.min(found.size(), cells.size() * 3))) {
			kinds.merge(level.getBlockState(p).getBlock(), 1, Integer::sum);
		}
		Block material = kinds.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
		List<BlockPos> ordered = new ArrayList<>(found.stream().filter(p -> level.getBlockState(p).is(material)).toList());
		found.stream().filter(p -> !level.getBlockState(p).is(material)).forEach(ordered::add);
		return ordered;
	}

	/** Open on top, nothing attached on any side, no lava beside it, and no water that would run into air. */
	static boolean safeToTake(ServerLevel level, BlockPos pos, Set<BlockPos> keep) {
		BlockState above = level.getBlockState(pos.above());
		if (!(above.isAir() || Scan.water(level, pos.above()) && above.getCollisionShape(level, pos.above()).isEmpty()) || keep.contains(pos.above())) {
			return false;
		}
		boolean water = false;
		boolean air = false;
		for (Direction dir : Direction.values()) {
			BlockPos n = pos.relative(dir);
			BlockState state = level.getBlockState(n);
			if (keep.contains(n) || Scan.lava(level, n)) {
				return false;
			}
			if (Scan.water(level, n)) {
				water = true;
			} else if (state.isAir()) {
				air = true;
			} else if (!state.isSolidRender()) {
				return false;
			}
		}
		return !(water && air);
	}
}
