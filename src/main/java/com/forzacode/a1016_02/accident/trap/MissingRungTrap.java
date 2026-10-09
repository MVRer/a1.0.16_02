package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.RouteBook;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Missing rung: one ladder halfway up a long shaft you climb. You missed a ladder. The clue: the rung is gone, not
 * dropped as an item.
 */
public final class MissingRungTrap extends BaseTrap {
	public MissingRungTrap() {
		super("missing_rung", false, "fell", EnumSet.of(Habit.CARVER), DamageTypes.FALL);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		Set<BlockPos> ladders = new LinkedHashSet<>(Services.watch().placedNear(level, ctx.center(), ctx.cfg().scanRadius, Blocks.LADDER));
		for (RouteBook.Spot spot : ctx.routeSpots()) {
			if (spot.point().has(RouteBook.LADDER) && level.getBlockState(spot.pos()).is(Blocks.LADDER)) {
				ladders.add(spot.pos());
			}
		}
		List<Candidate> found = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		for (BlockPos ladder : ladders) {
			if (visited.contains(ladder)) {
				continue;
			}
			BlockState state = level.getBlockState(ladder);
			BlockPos bottom = ladder;
			while (sameLadder(level, bottom.below(), state)) {
				bottom = bottom.below();
			}
			BlockPos top = ladder;
			while (sameLadder(level, top.above(), state)) {
				top = top.above();
			}
			for (BlockPos p = bottom; p.getY() <= top.getY(); p = p.above()) {
				visited.add(p);
			}
			int length = top.getY() - bottom.getY() + 1;
			if (length < ctx.cfg().rungMinShaft) {
				continue;
			}
			BlockPos rung = bottom.above(length / 2);
			found.add(Candidate.of(rung, List.of(TraceOp.remove(rung)), bottom.offset(-2, -2, -2), top.offset(2, 2, 2),
					"One rung is missing halfway up the ladder at " + at(rung) + ", and no ladder dropped anywhere."));
		}
		found.sort((a, b) -> Double.compare(a.pos.distSqr(ctx.center()), b.pos.distSqr(ctx.center())));
		return found;
	}

	private static boolean sameLadder(ServerLevel level, BlockPos pos, BlockState ladder) {
		BlockState state = level.getBlockState(pos);
		return state.is(Blocks.LADDER) && state.getValue(LadderBlock.FACING) == ladder.getValue(LadderBlock.FACING);
	}
}
