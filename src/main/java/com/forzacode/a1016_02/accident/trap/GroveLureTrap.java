package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
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
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * The lure, restoring his groves: he lets you finish. While you are up placing the last leaves, the blocks under you
 * go. A live trap: it springs while you are placing leaves high up and looking up at the canopy, and takes the pillar
 * you climbed, from the block under your feet to the ground. D-027: blocks strictly under your feet are out of view
 * while you look 30 degrees or more up, so nothing vanishes on camera. The clue: the whole pillar is gone and not one
 * block of it dropped.
 */
public final class GroveLureTrap extends BaseTrap {
	public GroveLureTrap() {
		super("lure_grove", true, "fell", EnumSet.of(Habit.STRIPPER), DamageTypes.FALL);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		SiteRegistry.Site grove = Lures.grove(GlobalPos.of(level.dimension(), player.blockPosition()), cfg.groveSearchRadius).orElse(null);
		return grove != null && leaves(level, grove) >= cfg.groveLeavesMin;
	}

	/** Leaves the player put back in this grove. */
	public static int leaves(ServerLevel level, SiteRegistry.Site grove) {
		return Services.watch().placedNear(level, grove.pos(), grove.size() + 4, BlockTags.LEAVES).size();
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		List<Candidate> found = new ArrayList<>();
		SiteRegistry.Site grove = Lures.grove(GlobalPos.of(level.dimension(), ctx.center()), ctx.cfg().groveSearchRadius).orElse(null);
		if (grove == null || leaves(level, grove) < ctx.cfg().groveLeavesMin) {
			return found;
		}
		int r = grove.size() + 8;
		found.add(Candidate.of(grove.pos(), List.of(), grove.pos().offset(-r, -24, -r), grove.pos().offset(r, 40, r),
				"The pillar you climbed in the grove at " + at(grove.pos()) + " is gone to the ground, and not one block of it dropped."));
		return found;
	}

	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		AccidentConfig cfg = ctx.cfg();
		if (armed.isSet() || player == null || player.level() != level || !armed.zone(0).contains(player.position()) || !player.onGround()) {
			return armed;
		}
		if (ctx.data().lure.groveLeavesTick < ctx.now() - cfg.groveRecentTicks() || !lookingUp(player)) {
			return armed;
		}
		BlockPos feet = player.blockPosition();
		List<BlockPos> column = new ArrayList<>();
		BlockPos cursor = feet.below();
		while (cursor.getY() > level.getMinY() && climbAid(ctx, cursor)) {
			column.add(cursor);
			cursor = cursor.below();
		}
		if (feet.getY() - (cursor.getY() + 1) < cfg.groveMinHeight || column.size() < 2) {
			return armed;
		}
		// Every block strictly under the feet, in their own column: the view check exempts them only while they look up.
		if (!TraceOp.apply(level, ctx.view(), "accident:" + id(), column.stream().map(TraceOp::remove).toList())) {
			return armed;
		}
		return armed.set(ctx.now(), window(cfg), column).withZone(new BlockPos(feet.getX() - 3, cursor.getY() - 2, feet.getZ() - 3), feet.offset(3, 3, 3));
	}

	/** Looking up far enough that the blocks under the feet are out of view (D-027). */
	static boolean lookingUp(ServerPlayer player) {
		return player.getViewVector(1.0F).y >= Math.sin(Math.toRadians(TraceService.UNDER_FEET_LOOK_UP_DEGREES));
	}

	/** A block the player put there to climb: their pillar, ladders, scaffolding. */
	private static boolean climbAid(TrapContext ctx, BlockPos pos) {
		BlockState state = ctx.level().getBlockState(pos);
		if (state.is(Blocks.LADDER) || state.is(Blocks.SCAFFOLDING)) {
			return true;
		}
		return ctx.placedByPlayer(pos) && !Scan.open(ctx.level(), pos) && !state.hasBlockEntity();
	}
}
