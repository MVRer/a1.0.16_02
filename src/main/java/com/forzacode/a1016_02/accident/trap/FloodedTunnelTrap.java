package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;

import org.jspecify.annotations.Nullable;

/**
 * Flooded tunnel: the block holding back the water over your underwater route. A live trap: it waits until you are
 * deep in the tunnel, with the block behind you and out of view. You drowned. The clue: the gap is exactly one block.
 */
public final class FloodedTunnelTrap extends BaseTrap {
	public FloodedTunnelTrap() {
		super("flooded_tunnel", true, "drowned", EnumSet.of(Habit.CARVER), DamageTypes.DROWN);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		int reach = ctx.cfg().floodSpringMax;
		List<Candidate> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos cell : ctx.tunnelCells()) {
			if (!Scan.open(level, cell)) {
				continue;
			}
			List<BlockPos[]> pairs = new ArrayList<>();
			BlockPos head = Scan.open(level, cell.above()) ? cell.above() : cell;
			pairs.add(new BlockPos[] {head.above(), head.above(2)});
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				pairs.add(new BlockPos[] {cell.relative(dir), cell.relative(dir, 2)});
				pairs.add(new BlockPos[] {head.relative(dir), head.relative(dir, 2)});
			}
			for (BlockPos[] pair : pairs) {
				BlockPos plug = pair[0];
				if (!seen.add(plug) || !Scan.takeable(level, plug) || !Scan.waterSource(level, pair[1]) || sources(level, pair[1]) < ctx.cfg().floodMinSources) {
					continue;
				}
				found.add(Candidate.of(plug, List.of(TraceOp.remove(plug)), plug.offset(-reach, -6, -reach), plug.offset(reach, 2, reach),
						"A gap in the tunnel at " + at(plug) + " where the water came in, exactly one block."));
			}
		}
		return found;
	}

	/** Water sources in a 5x3x5 box around {@code pos}: is this a body of water or a puddle? */
	static int sources(ServerLevel level, BlockPos pos) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
			if (Scan.waterSource(level, p)) {
				n++;
			}
		}
		return n;
	}

	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		if (armed.isSet()) {
			return armed;
		}
		ServerLevel level = ctx.level();
		BlockPos plug = armed.pos();
		if (!Scan.takeable(level, plug)) {
			return null;
		}
		ServerPlayer player = ctx.player();
		if (player == null || player.level() != level || level.canSeeSky(player.blockPosition())) {
			return armed;
		}
		double dist = Math.sqrt(player.blockPosition().distSqr(plug));
		if (dist < ctx.cfg().floodSpringMin || dist > ctx.cfg().floodSpringMax
				|| !ctx.walked(player.blockPosition()) && !ctx.dugByPlayer(player.blockPosition())) {
			return armed;
		}
		if (!TraceOp.apply(level, ctx.view(), "accident:" + id(), List.of(TraceOp.remove(plug)))) {
			return armed;
		}
		return armed.set(ctx.now(), window(ctx.cfg()), List.of(plug));
	}
}
