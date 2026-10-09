package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.RouteBook;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Powder snow: powder snow moved onto a path you walk on the snowy slopes, two blocks deep. You froze in powder
 * snow. The clue: it was not on your path yesterday.
 */
public final class PowderSnowTrap extends BaseTrap {
	/** Path spots checked for nearby powder snow per scan (each check reads a box of blocks). */
	private static final int MAX_SPOTS = 24;

	public PowderSnowTrap() {
		super("powder_snow", false, "froze", EnumSet.of(Habit.WATCHER), DamageTypes.FREEZE);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		List<Candidate> found = new ArrayList<>();
		int checked = 0;
		for (RouteBook.Spot spot : ctx.routeSpots()) {
			BlockPos path = spot.pos();
			if (checked >= MAX_SPOTS) {
				break;
			}
			if (spot.point().firstDay >= ctx.day() || spot.point().has(RouteBook.UNDER)
					|| !fillable(level, path) || !level.getBlockState(path.above()).isAir() || !Scan.fullSolid(level, path.below()) || !snowy(level, path)) {
				continue;
			}
			checked++;
			List<BlockPos> sources = sources(level, path, ctx.cfg().powderSourceRadius, ctx);
			if (sources.size() < 2) {
				continue;
			}
			List<TraceOp> ops = List.of(TraceOp.move(sources.get(0), path), TraceOp.move(sources.get(1), path.above()));
			found.add(Candidate.around(path, ops, 3, 1, "Powder snow on your path at " + at(path) + ". It wasn't there yesterday."));
		}
		return found;
	}

	private static boolean fillable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.is(Blocks.SNOW) && state.canBeReplaced();
	}

	static boolean snowy(ServerLevel level, BlockPos pos) {
		BlockState ground = level.getBlockState(pos.below());
		return ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.POWDER_SNOW) || level.getBlockState(pos).is(Blocks.SNOW)
				|| level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	/** Natural powder snow tops (open above) near the path, nearest first, never right next to it. */
	static List<BlockPos> sources(ServerLevel level, BlockPos path, int radius, TrapContext ctx) {
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(path.offset(-radius, -4, -radius), path.offset(radius, 4, radius))) {
			if (pos.distManhattan(path) <= 2 || !level.getBlockState(pos).is(Blocks.POWDER_SNOW) || !level.getBlockState(pos.above()).isAir()
					|| ctx.placedByPlayer(pos)) {
				continue;
			}
			found.add(pos.immutable());
		}
		found.sort((a, b) -> Double.compare(a.distSqr(path), b.distSqr(path)));
		return found;
	}
}
