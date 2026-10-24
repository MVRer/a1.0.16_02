package com.forzacode.a1016_02.world.sc2.s17;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * 11+, t0rch went in (docs/SELF_CREATED_2.md, feature 17): when t0rch's walk passes within {@code t0rchRadius} of an
 * archive's cells, at {@code t0rchChance} the trail ends at a 1 x 2 hole t0rch dug from the walk through the cells' wall
 * into a lit cell, instead of at the shaft. The hole starts at a walk cell whose feet are level with a cell's floor (or
 * one above), runs straight at most {@value #DIG_MAX} cells of natural ground and the cells' walls, and opens into a cell.
 * Every rule of 11 is {@link TorchTrail}'s own. The chance is rolled only once such a way exists.
 */
final class ArchiveTrail implements TorchTrail.Breach {
	/** At most this many cells dug from the walk to the room. */
	static final int DIG_MAX = 6;

	static final ArchiveTrail LIVE = new ArchiveTrail(Sc17Config::get,
			(level, near) -> Services.remains().nearest(level, near, Set.of(Remains.Kind.ARCHIVE), 160)
					.map(place -> ArchiveRules.LIVE.plan(RemainsImpl.LIVE.data(), place)));

	private final Supplier<Sc17Config> config;
	private final BiFunction<ServerLevel, BlockPos, Optional<ArchivePlan>> nearest;

	ArchiveTrail(Supplier<Sc17Config> config, BiFunction<ServerLevel, BlockPos, Optional<ArchivePlan>> nearest) {
		this.config = config;
		this.nearest = nearest;
	}

	@Override
	public TorchTrail.@Nullable Way find(ServerLevel level, Map<BlockPos, Integer> walk, BlockPos start, TorchTrail.Config trail, RandomSource random) {
		Optional<ArchivePlan> found = nearest.apply(level, start);
		if (found.isEmpty() || found.get() == null) {
			return null;
		}
		ArchivePlan plan = found.get();
		Sc17Config cfg = config.get();
		TorchTrail.Way best = null;
		int bestSteps = -1;
		for (Map.Entry<BlockPos, Integer> cell : walk.entrySet()) {
			int steps = cell.getValue();
			if (steps < trail.pathMin || steps > trail.pathMax) {
				continue;
			}
			BlockPos feet = cell.getKey();
			int u = feet.getY() - plan.floorY();
			if (u < 1 || u > 2) {
				continue;
			}
			for (Direction toward : Direction.Plane.HORIZONTAL) {
				TorchTrail.Way way = way(level, plan, feet, toward, cfg.t0rchRadius);
				// The shortest hole, then the longest walk.
				if (way != null && (best == null || way.dig() < best.dig() || way.dig() == best.dig() && steps > bestSteps)) {
					best = way;
					bestSteps = steps;
				}
			}
		}
		if (best == null || random.nextDouble() >= cfg.t0rchChance) {
			return null;
		}
		return best;
	}

	/** A straight hole from {@code feet} along {@code toward} into a cell, or null. */
	static TorchTrail.@Nullable Way way(ServerLevel level, ArchivePlan plan, BlockPos feet, Direction toward, int radius) {
		for (int k = 1; k <= DIG_MAX + 1; k++) {
			BlockPos at = feet.relative(toward, k);
			if (!level.isLoaded(at) || !level.isLoaded(at.above())) {
				return null;
			}
			if (plan.cellOf(at) >= 0 && plan.cellOf(at.above()) >= 0 && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
				if (k < 2 || at.distSqr(feet) > (double) radius * radius) {
					return null;
				}
				return new TorchTrail.Way(feet.immutable(), toward, k - 1, at.immutable());
			}
			if (!diggable(level.getBlockState(at)) || !diggable(level.getBlockState(at.above())) || wetBeside(level, at) || wetBeside(level, at.above())) {
				return null;
			}
		}
		return null;
	}

	/** True if water or lava stands next to this cell, or a neighbour is not loaded (R12-22: a hole there would flood the walk). */
	static boolean wetBeside(ServerLevel level, BlockPos cell) {
		for (Direction d : Direction.values()) {
			BlockPos n = cell.relative(d);
			if (!level.isLoaded(n) || !level.getFluidState(n).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Ground t0rch would dig through: the base stones, dirt, gravel, ores and the cells' own cobblestone. */
	static boolean diggable(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
				|| state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
				|| state.is(Blocks.INFESTED_COBBLESTONE);
	}
}
