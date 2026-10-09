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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Lava floor: one floor block of your mine, over a lava pocket. You dug into lava. The clue: the pocket was not
 * there when you mined that tunnel (the floor was solid then).
 */
public final class LavaFloorTrap extends BaseTrap {
	public LavaFloorTrap() {
		super("lava_floor", false, "lava", EnumSet.of(Habit.CARVER), DamageTypes.LAVA, DamageTypes.IN_FIRE, DamageTypes.ON_FIRE);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		List<Candidate> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos cell : ctx.tunnelCells()) {
			BlockPos floor = cell.below();
			if (!Scan.open(level, cell) || !seen.add(floor) || !Scan.takeable(level, floor) || !Scan.lava(level, floor.below())) {
				continue;
			}
			found.add(Candidate.around(floor, List.of(TraceOp.remove(floor)), 5, 3,
					"A lava pocket right under a floor block of a tunnel you mined, at " + at(floor) + ". The floor was solid when you dug it."));
		}
		return found;
	}
}
