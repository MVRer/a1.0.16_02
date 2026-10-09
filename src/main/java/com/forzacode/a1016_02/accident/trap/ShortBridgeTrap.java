package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Short bridge: the last block of your bridge over a ravine, at night. You misjudged it. The clue: you remember
 * placing it.
 */
public final class ShortBridgeTrap extends BaseTrap {
	public ShortBridgeTrap() {
		super("short_bridge", false, "fell", EnumSet.of(Habit.WATCHER), DamageTypes.FALL, DamageTypes.LAVA, DamageTypes.IN_FIRE,
				DamageTypes.ON_FIRE, DamageTypes.DROWN);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		return Scan.night(level, cfg);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		int minDrop = ctx.cfg().bridgeMinDrop;
		Set<BlockPos> deck = new HashSet<>();
		for (BlockPos pos : Services.watch().placedNear(level, ctx.center(), ctx.cfg().scanRadius, state -> !state.isAir())) {
			if (Scan.fullSolid(level, pos) && level.getBlockState(pos.above()).isAir() && Scan.depthBelow(level, pos.below(), minDrop) >= minDrop) {
				deck.add(pos);
			}
		}
		List<Candidate> found = new ArrayList<>();
		for (BlockPos end : deck) {
			Direction along = null;
			int neighbours = 0;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				if (deck.contains(end.relative(dir))) {
					neighbours++;
					along = dir;
				}
			}
			if (neighbours != 1) {
				continue;
			}
			BlockPos bank = end.relative(along.getOpposite());
			if (!Scan.fullSolid(level, bank) || deck.contains(bank)) {
				continue;
			}
			int drop = Scan.depthBelow(level, end.below(), 256);
			found.add(Candidate.of(end, List.of(TraceOp.remove(end)), end.offset(-2, -drop - 2, -2), end.offset(2, 3, 2),
					"The bridge at " + at(end) + " is one block short at the bank. You remember placing that block."));
		}
		found.sort((a, b) -> Double.compare(a.pos.distSqr(ctx.center()), b.pos.distSqr(ctx.center())));
		return found;
	}
}
