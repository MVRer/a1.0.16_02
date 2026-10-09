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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Lava in the wall: the block holding lava back beside your tunnel. A lava leak. The clue: a perfect 1x1 gap with
 * no ore around it, so nobody mined it.
 */
public final class LavaWallTrap extends BaseTrap {
	public LavaWallTrap() {
		super("lava_wall", false, "lava", EnumSet.of(Habit.CARVER), DamageTypes.LAVA, DamageTypes.IN_FIRE, DamageTypes.ON_FIRE);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		List<Candidate> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos cell : ctx.tunnelCells()) {
			for (BlockPos open : List.of(cell, cell.above())) {
				if (!Scan.open(level, open)) {
					continue;
				}
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					BlockPos wall = open.relative(dir);
					if (!seen.add(wall) || !Scan.takeable(level, wall) || ctx.placedByPlayer(wall) || Scan.oreAround(level, wall)
							|| !Scan.lava(level, wall.relative(dir))) {
						continue;
					}
					found.add(Candidate.around(wall, List.of(TraceOp.remove(wall)), 8, 2,
							"A perfect 1x1 gap in the tunnel wall at " + at(wall) + ", lava behind it and no ore around it."));
				}
			}
		}
		return found;
	}
}
