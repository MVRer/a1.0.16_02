package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.Lures;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The lure, sleeping near his places: you wake to the floor gone. A live trap: while you sleep near a pyramid or a
 * cross, the floor a few blocks from the bed (never within reach) is taken where it lies over a drop or lava. The
 * clue: the edge of the hole is cut clean and nothing dropped.
 */
public final class SleepLureTrap extends BaseTrap {
	public SleepLureTrap() {
		super("lure_sleep", true, "fell", EnumSet.of(Habit.MOURNER), DamageTypes.FALL, DamageTypes.LAVA, DamageTypes.IN_FIRE, DamageTypes.ON_FIRE,
				DamageTypes.DROWN);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		long t = Scan.timeOfDay(level);
		return (t >= cfg.nightStart - 1500 || t < 500)
				&& !Lures.hisPlaces(level.getServer(), GlobalPos.of(level.dimension(), player.blockPosition()), cfg.sleepSiteRadius).isEmpty();
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		List<Candidate> found = new ArrayList<>();
		int r = ctx.cfg().sleepSiteRadius;
		for (GlobalPos place : Lures.hisPlaces(ctx.level().getServer(), GlobalPos.of(ctx.level().dimension(), ctx.center()), r)) {
			found.add(Candidate.around(place.pos(), List.of(), r, 16, "The floor near your bed by " + at(place.pos()) + " is cut clean. Nothing dropped."));
		}
		return found;
	}

	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		if (armed.isSet() || player == null || player.level() != level || !player.isSleeping()) {
			return armed;
		}
		BlockPos bed = player.getSleepingPos().orElse(null);
		if (bed == null || !armed.zone(0).contains(Vec3.atCenterOf(bed))) {
			return armed;
		}
		AccidentConfig cfg = ctx.cfg();
		List<Scan.Hollow> holes = floor(level, bed, cfg);
		if (holes.isEmpty()) {
			return armed;
		}
		List<TraceOp> ops = new ArrayList<>();
		List<BlockPos> taken = new ArrayList<>();
		int bottom = bed.getY();
		for (Scan.Hollow hole : holes) {
			for (BlockPos pos : hole.dig()) {
				ops.add(TraceOp.remove(pos));
				taken.add(pos);
			}
			bottom = Math.min(bottom, hole.cavity().getY() - hole.depth() - 1);
		}
		if (!TraceOp.apply(level, ctx.view(), "accident:" + id(), ops)) {
			return armed;
		}
		int reach = cfg.sleepFloorMaxDistance + 2;
		return armed.set(ctx.now(), window(cfg), taken).withZone(new BlockPos(bed.getX() - reach, bottom, bed.getZ() - reach), bed.offset(reach, 3, reach));
	}

	/** Floor blocks a few steps from the bed that lie over a drop or lava, nearest first. */
	static List<Scan.Hollow> floor(ServerLevel level, BlockPos bed, AccidentConfig cfg) {
		List<Scan.Hollow> holes = new ArrayList<>();
		int max = cfg.sleepFloorMaxDistance;
		double minSqr = (double) cfg.sleepFloorMinDistance * cfg.sleepFloorMinDistance;
		for (int dx = -max; dx <= max; dx++) {
			for (int dz = -max; dz <= max; dz++) {
				double d = dx * dx + dz * dz;
				if (d < minSqr || d > (double) max * max) {
					continue;
				}
				BlockPos ground = bed.offset(dx, -1, dz);
				if (!Scan.open(level, ground.above()) || !Scan.open(level, ground.above(2))) {
					continue;
				}
				Scan.hollow(level, ground, cfg.hollowMaxDepth, cfg.dropMinDepth).ifPresent(holes::add);
			}
		}
		holes.sort(Comparator.comparingDouble(h -> h.dig().get(0).distSqr(bed)));
		return holes.size() > cfg.sleepFloorMaxColumns ? holes.subList(0, cfg.sleepFloorMaxColumns) : holes;
	}
}
