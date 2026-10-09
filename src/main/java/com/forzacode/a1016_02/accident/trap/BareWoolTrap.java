package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bare wool: one wool block, slab or carpet you laid to sneak through the deep dark. The warden heard you. The clue:
 * one gap in your wool line.
 */
public final class BareWoolTrap extends BaseTrap {
	private static final int MAX_SCULK_CHECKS = 12;

	public BareWoolTrap() {
		super("bare_wool", false, "heard", EnumSet.of(Habit.COLLECTOR));
	}

	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		return source.is(DamageTypes.SONIC_BOOM) || source.getEntity() instanceof Warden;
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		Set<BlockPos> wool = new HashSet<>(Services.watch().placedNear(level, ctx.center(), ctx.cfg().scanRadius, state -> state.is(BlockTags.DAMPENS_VIBRATIONS)));
		List<BlockPos> middles = new ArrayList<>();
		for (BlockPos pos : wool) {
			for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
				Direction plus = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
				if (wool.contains(pos.relative(plus)) && wool.contains(pos.relative(plus.getOpposite()))) {
					middles.add(pos);
					break;
				}
			}
		}
		middles.sort((a, b) -> Double.compare(a.distSqr(ctx.center()), b.distSqr(ctx.center())));
		List<Candidate> found = new ArrayList<>();
		int sculkChecks = 0;
		for (BlockPos pos : middles) {
			boolean deepDark = level.getBiome(pos).is(Biomes.DEEP_DARK);
			if (!deepDark) {
				if (sculkChecks++ >= MAX_SCULK_CHECKS || !sculkNear(level, pos, ctx.cfg().sculkSearchRadius)) {
					continue;
				}
			}
			found.add(Candidate.around(pos, List.of(TraceOp.remove(pos)), 24, 4, "One gap in your wool line at " + at(pos) + "."));
		}
		return found;
	}

	static boolean sculkNear(ServerLevel level, BlockPos pos, int radius) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
			BlockState state = level.getBlockState(p);
			if (state.is(Blocks.SCULK_SENSOR) || state.is(Blocks.CALIBRATED_SCULK_SENSOR) || state.is(Blocks.SCULK_SHRIEKER)) {
				return true;
			}
		}
		return false;
	}
}
