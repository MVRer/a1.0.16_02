package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.block.BedBlock;

/**
 * No bed: your bed, taken while you are out. Phantoms after three sleepless nights, and death sends you to world
 * spawn. The clue: the bed is gone and no item dropped.
 */
public final class NoBedTrap extends BaseTrap {
	public NoBedTrap() {
		super("no_bed", false, "sleepless", EnumSet.of(Habit.COLLECTOR));
	}

	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		return source.getEntity() instanceof Phantom;
	}

	@Override
	public long window(AccidentConfig cfg) {
		return cfg.noBedWindowTicks();
	}

	@Override
	public boolean anywhere() {
		return true;
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		List<Candidate> found = new ArrayList<>();
		ServerPlayer player = ctx.player();
		ServerPlayer.RespawnConfig respawn = player == null ? null : player.getRespawnConfig();
		if (respawn == null) {
			return found;
		}
		GlobalPos spawn = respawn.respawnData().globalPos();
		ServerLevel bedLevel = ctx.level().getServer().getLevel(spawn.dimension());
		BlockPos bed = spawn.pos();
		if (bedLevel == null || !bedLevel.isLoaded(bed) || !(bedLevel.getBlockState(bed).getBlock() instanceof BedBlock)) {
			return found;
		}
		boolean away = player.level() != bedLevel || player.blockPosition().distSqr(bed) > (double) ctx.cfg().noBedAwayBlocks * ctx.cfg().noBedAwayBlocks;
		if (!away) {
			return found;
		}
		found.add(Candidate.around(bed, List.of(TraceOp.remove(bed)), 4, 0,
				"Your bed at " + at(bed) + " is gone, and no bed item dropped anywhere.").inDimension(bedLevel.dimension()));
		return found;
	}

	@Override
	public boolean setup(TrapContext ctx, Candidate candidate) {
		ServerLevel bedLevel = candidate.dimension == null ? ctx.level() : ctx.level().getServer().getLevel(candidate.dimension);
		return bedLevel != null && TraceOp.apply(bedLevel, ctx.view(), "accident:" + id(), candidate.ops);
	}
}
