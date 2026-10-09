package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.AccidentData;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.RouteBook;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * The lure, offerings at the cairn: you come back to check along the same route, and by the third visit the ground
 * on it is hollow over a drop or lava. Set while you are away from the cairn, after your second visit. The clue: a
 * clean hole exactly on the way you always walked.
 */
public final class CairnLureTrap extends BaseTrap {
	public CairnLureTrap() {
		super("lure_cairn", false, "fell", EnumSet.of(Habit.MOURNER, Habit.COLLECTOR), DamageTypes.FALL, DamageTypes.LAVA, DamageTypes.IN_FIRE,
				DamageTypes.ON_FIRE, DamageTypes.DROWN);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		AccidentData data = AccidentData.get(level.getServer());
		return data.cairn().isPresent() && data.cairnVisits() >= cfg.cairnVisitsBeforeTrap && !data.atCairn;
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		AccidentConfig cfg = ctx.cfg();
		List<Candidate> found = new ArrayList<>();
		GlobalPos cairn = ctx.data().cairn().orElse(null);
		if (cairn == null || !cairn.dimension().equals(level.dimension()) || ctx.data().cairnVisits() < cfg.cairnVisitsBeforeTrap || ctx.data().atCairn) {
			return found;
		}
		Set<BlockPos> seen = new HashSet<>();
		for (RouteBook.Spot spot : ctx.routes().near(level.dimension(), cairn.pos(), cfg.cairnRouteRadius, cfg.scanMaxPoints)) {
			BlockPos path = spot.pos();
			if (spot.point().passes < cfg.usualRoutePasses || Scan.horizontalDistSqr(path, cairn.pos()) < 36 || !Scan.passable(level, path)) {
				continue;
			}
			BlockPos ground = path.below();
			if (!seen.add(ground)) {
				continue;
			}
			Scan.Hollow hollow = Scan.hollow(level, ground, cfg.hollowMaxDepth, cfg.dropMinDepth).orElse(null);
			if (hollow == null) {
				continue;
			}
			List<TraceOp> ops = hollow.dig().stream().map(TraceOp::remove).toList();
			int bottom = hollow.cavity().getY() - hollow.depth() - 1;
			found.add(Candidate.of(ground, ops, new BlockPos(ground.getX() - 3, bottom, ground.getZ() - 3), ground.offset(3, 4, 3),
					"A clean hole at " + at(ground) + ", exactly on the way you always walk to the cairn, straight down to " + (hollow.lava() ? "lava." : "a drop.")));
		}
		found.sort((a, b) -> Double.compare(a.pos.distSqr(ctx.center()), b.pos.distSqr(ctx.center())));
		return found;
	}
}
