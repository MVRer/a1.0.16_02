package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.AccidentData;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

import org.jspecify.annotations.Nullable;

/**
 * The lure, disc 13 in the white eyes room: the track runs about three minutes in a dark room, the torches go out
 * one by one, each out of view, and the dark does the rest. A live, stepwise trap. The clue: every torch is gone and
 * none dropped.
 */
public final class WhiteEyesLureTrap extends BaseTrap {
	public WhiteEyesLureTrap() {
		super("lure_white_eyes", true, "dark", EnumSet.of(Habit.WATCHER));
	}

	/** Only a monster that spawned in the cells the taken torches used to light, while the room was going dark. */
	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		Entity attacker = source.getEntity();
		return attacker != null && armed.blames(attacker.getUUID());
	}

	@Override
	public ArmedTrap onSpawned(TrapContext ctx, Entity entity, ArmedTrap armed) {
		if (!(entity instanceof Enemy) || entity.level() != ctx.level() || !armed.zone(0).contains(entity.position())
				|| ctx.level().getBrightness(LightLayer.BLOCK, entity.blockPosition()) > 0) {
			return armed;
		}
		for (BlockPos torch : armed.targets()) {
			if (torch.distManhattan(entity.blockPosition()) <= DarkCornerTrap.TORCH_REACH) {
				return armed.blame(entity.getUUID());
			}
		}
		return armed;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		GlobalPos room = Lures.room(level.getServer()).orElse(null);
		return room != null && room.dimension().equals(level.dimension()) && room.pos().distSqr(player.blockPosition()) <= 128.0 * 128.0;
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		List<Candidate> found = new ArrayList<>();
		GlobalPos room = Lures.room(ctx.level().getServer()).orElse(null);
		if (room == null || !room.dimension().equals(ctx.level().dimension())) {
			return found;
		}
		int r = ctx.cfg().whiteEyesRoomRadius + 4;
		found.add(Candidate.around(room.pos(), List.of(), r, 0, "Every torch in the room at " + at(room.pos()) + " is gone, and none dropped."));
		return found;
	}

	/** A jukebox in the room playing disc 13. */
	static boolean disc13Playing(ServerLevel level, BlockPos room, int radius) {
		for (BlockPos pos : BlockPos.betweenClosed(room.offset(-radius, -radius, -radius), room.offset(radius, radius, radius))) {
			if (level.getBlockState(pos).is(Blocks.JUKEBOX) && level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox
					&& jukebox.getTheItem().is(Items.MUSIC_DISC_13) && jukebox.getSongPlayer().isPlaying()) {
				return true;
			}
		}
		return false;
	}

	static List<BlockPos> torches(ServerLevel level, BlockPos room, int radius, BlockPos from) {
		List<BlockPos> torches = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(room.offset(-radius, -radius, -radius), room.offset(radius, radius, radius))) {
			if (Scan.torch(level.getBlockState(pos))) {
				torches.add(pos.immutable());
			}
		}
		// Farthest from the player first: the ones behind and away go out first.
		torches.sort((a, b) -> Double.compare(b.distSqr(from), a.distSqr(from)));
		return torches;
	}

	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		AccidentData.LureWatch watch = ctx.data().lure;
		int radius = ctx.cfg().whiteEyesRoomRadius;
		if (player == null || player.level() != level || !armed.zone(0).contains(player.position()) || !disc13Playing(level, armed.pos(), radius)) {
			watch.playStart = -1;
			return armed;
		}
		if (watch.playStart < 0) {
			watch.playStart = ctx.now();
			watch.stepTick = Long.MIN_VALUE;
		}
		List<BlockPos> torches = torches(level, armed.pos(), radius, player.blockPosition());
		if (torches.isEmpty()) {
			return armed;
		}
		long interval = Math.max(1, ctx.cfg().whiteEyesTrackTicks() / (torches.size() + armed.step() + 1));
		long last = watch.stepTick == Long.MIN_VALUE ? watch.playStart : watch.stepTick;
		if (ctx.now() - last < interval) {
			return armed;
		}
		for (BlockPos torch : torches) {
			if (TraceOp.apply(level, ctx.view(), "accident:" + id(), List.of(TraceOp.remove(torch)))) {
				watch.stepTick = ctx.now();
				List<BlockPos> out = new ArrayList<>(armed.targets());
				out.add(torch);
				ArmedTrap next = armed.isSet() ? armed.withPhase(ArmedTrap.Phase.SET, ctx.now() + window(ctx.cfg())) : armed.set(ctx.now(), window(ctx.cfg()), out);
				return next.withStep(armed.step() + 1, out);
			}
		}
		return armed;
	}
}
