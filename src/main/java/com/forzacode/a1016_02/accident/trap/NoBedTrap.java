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
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.block.BedBlock;

/**
 * No bed: your bed, taken while you are out, only if you slept in the last day. Phantoms after three sleepless
 * nights, and death sends you to world spawn. The clue: the bed is gone and no item dropped. Only a phantom that
 * appeared near you while you had not slept since the bed went counts.
 */
public final class NoBedTrap extends BaseTrap {
	/** TIME_SINCE_REST and play ticks can drift apart by a few ticks. */
	private static final int STAT_SLACK = 100;

	public NoBedTrap() {
		super("no_bed", false, "sleepless", EnumSet.of(Habit.COLLECTOR));
	}

	/** Ticks since the player last slept (the stat phantoms use). */
	public static int sinceRest(ServerPlayer player) {
		return player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
	}

	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		Entity attacker = source.getEntity();
		return attacker instanceof Phantom && armed.blames(attacker.getUUID());
	}

	@Override
	public long window(AccidentConfig cfg) {
		return cfg.noBedWindowTicks();
	}

	@Override
	public boolean anywhere() {
		return true;
	}

	/** A phantom appearing near the subject who has not slept since the bed was taken is one of the bed's phantoms. */
	@Override
	public ArmedTrap onSpawned(TrapContext ctx, Entity entity, ArmedTrap armed) {
		ServerPlayer player = ctx.player();
		if (!(entity instanceof Phantom) || !armed.isSet() || player == null || entity.level() != player.level()
				|| entity.distanceTo(player) > ctx.cfg().phantomNearBlocks || !sleeplessSinceTaken(player, armed, ctx.now())) {
			return armed;
		}
		return armed.blame(entity.getUUID());
	}

	/** True if the player's sleepless streak began before the bed was taken (they have not slept since). */
	public static boolean sleeplessSinceTaken(ServerPlayer player, ArmedTrap armed, long now) {
		return armed.setAt() >= 0 && sinceRest(player) + STAT_SLACK >= now - armed.setAt();
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		List<Candidate> found = new ArrayList<>();
		ServerPlayer player = ctx.player();
		ServerPlayer.RespawnConfig respawn = player == null ? null : player.getRespawnConfig();
		if (respawn == null || sinceRest(player) >= ctx.cfg().noBedRestedWithinTicks()) {
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
		ServerPlayer player = ctx.player();
		if (player == null || sinceRest(player) >= ctx.cfg().noBedRestedWithinTicks()) {
			return false;
		}
		ServerLevel bedLevel = candidate.dimension == null ? ctx.level() : ctx.level().getServer().getLevel(candidate.dimension);
		return bedLevel != null && TraceOp.apply(bedLevel, ctx.view(), "accident:" + id(), candidate.ops);
	}
}
