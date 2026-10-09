package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;

/**
 * House fire: the stone between a lava pool and your wooden wall. Lava set your house alight. The clue: the stone
 * block is missing, not burned.
 */
public final class HouseFireTrap extends BaseTrap {
	public HouseFireTrap() {
		super("house_fire", false, "burned", EnumSet.of(Habit.VISITOR), DamageTypes.IN_FIRE, DamageTypes.ON_FIRE, DamageTypes.LAVA);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		BlockPos base = base(ctx);
		int radius = ctx.cfg().baseRadius;
		List<Candidate> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> walls = Services.watch().placedNear(level, base, radius, state -> state.ignitedByLava());
		walls.sort((a, b) -> Double.compare(a.distSqr(ctx.center()), b.distSqr(ctx.center())));
		for (BlockPos wall : walls) {
			for (Direction dir : Direction.values()) {
				BlockPos stone = wall.relative(dir);
				if (!seen.add(stone) || !Scan.takeable(level, stone) || level.getBlockState(stone).ignitedByLava() || ctx.placedByPlayer(stone)
						|| !lavaPoolBeside(level, stone, dir.getOpposite())) {
					continue;
				}
				found.add(Candidate.of(stone, List.of(TraceOp.remove(stone)), base.offset(-radius, -8, -radius), base.offset(radius, 16, radius),
						"The stone between the lava and your wall at " + at(stone) + " is missing, not burned."));
			}
		}
		return found;
	}

	/** A lava source next to {@code stone}, on any side but the wall's. */
	private static boolean lavaPoolBeside(ServerLevel level, BlockPos stone, Direction towardWall) {
		for (Direction dir : Direction.values()) {
			if (dir == towardWall || dir == Direction.DOWN) {
				continue;
			}
			FluidState fluid = level.getFluidState(stone.relative(dir));
			if (fluid.is(FluidTags.LAVA) && fluid.isSource()) {
				return true;
			}
		}
		return false;
	}
}
