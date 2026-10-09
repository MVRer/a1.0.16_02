package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.CoreGaps;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.FallingBlock;

import org.jspecify.annotations.Nullable;

/**
 * Gravel ceiling: the block under a gravel or sand column above the shaft where you dig up. A live trap: it waits
 * until you are in the shaft, far enough below that the block is out of reach and out of view, then takes it and the
 * column comes down by the game's own rules. You suffocated. The clue: the hollow shaft above, where the gravel came
 * from. Needs the core falling-block opt-in ({@link CoreGaps}).
 */
public final class GravelCeilingTrap extends BaseTrap {
	public GravelCeilingTrap() {
		super("gravel_ceiling", true, "suffocated", EnumSet.of(Habit.CARVER), DamageTypes.IN_WALL, DamageTypes.FALLING_BLOCK);
	}

	@Override
	public @Nullable String blocked() {
		return CoreGaps.FALLING_OPT_IN ? null : "needs the core opt-in that lets a falling block drop";
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		List<Candidate> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos cell : ctx.walkedAndDug()) {
			BlockPos support = cell.above();
			if (!Scan.open(level, cell) || !seen.add(support) || !Scan.takeable(level, support)) {
				continue;
			}
			int column = column(level, support);
			if (column < ctx.cfg().gravelMinColumn) {
				continue;
			}
			int shaft = Scan.depthBelow(level, cell, 64);
			if (shaft < ctx.cfg().gravelMinShaft) {
				continue;
			}
			found.add(Candidate.of(support, List.of(TraceOp.remove(support)), support.offset(-1, -shaft - 1, -1), support.offset(1, column, 1),
					"A hollow shaft above " + at(support) + ", where the gravel came from."));
		}
		return found;
	}

	/** How many falling blocks stand on {@code support}. */
	static int column(ServerLevel level, BlockPos support) {
		int n = 0;
		while (n < 32 && level.getBlockState(support.above(n + 1)).getBlock() instanceof FallingBlock) {
			n++;
		}
		return n;
	}

	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		ServerLevel level = ctx.level();
		BlockPos support = armed.pos();
		if (!Scan.takeable(level, support) || column(level, support) < ctx.cfg().gravelMinColumn) {
			return null;
		}
		ServerPlayer player = ctx.player();
		if (armed.isSet() || player == null || player.level() != level) {
			return armed;
		}
		BlockPos feet = player.blockPosition();
		boolean underIt = feet.getX() == support.getX() && feet.getZ() == support.getZ() && feet.getY() <= support.getY() - 5
				&& feet.getY() >= armed.zoneMin().getY();
		if (!underIt) {
			return armed;
		}
		List<BlockPos> watched = new ArrayList<>();
		watched.add(support);
		for (int n = 1; n <= column(level, support); n++) {
			watched.add(support.above(n));
		}
		if (!ctx.view().outOfView(level, watched) || !CoreGaps.removeLettingFall(level, support, "accident:" + id())) {
			return armed;
		}
		return armed.set(ctx.now(), window(ctx.cfg()), List.of(support));
	}
}
