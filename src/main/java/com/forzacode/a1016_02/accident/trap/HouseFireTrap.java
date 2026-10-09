package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.AccidentData;
import com.forzacode.a1016_02.accident.ArmedTrap;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * House fire: the stone between a lava pool and your wooden wall. Lava set your house alight. The clue: the stone
 * block is missing, not burned. Only fire or lava that traces back to that gap counts: lava that came through it, or
 * fire within a few blocks of it or of that lava, touched by the player shortly before the death.
 */
public final class HouseFireTrap extends BaseTrap {
	/** How many lava cells the trace follows from the gap. */
	static final int LAVA_TRACE_MAX = 64;
	/** "In fire" or "in lava" means within this many ticks of touching traced fire or lava. */
	static final long TOUCH_TICKS = 40;

	public HouseFireTrap() {
		super("house_fire", false, "burned", EnumSet.of(Habit.VISITOR), DamageTypes.IN_FIRE, DamageTypes.ON_FIRE, DamageTypes.LAVA);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		BlockPos base = base(ctx);
		int radius = ctx.cfg().baseRadius;
		List<Candidate> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> walls = Services.watch().placedNear(level, base, radius, state -> state.ignitedByLava());
		walls.sort((a, b) -> Double.compare(a.distSqr(ctx.center()), b.distSqr(ctx.center())));
		for (BlockPos wall : walls) {
			for (Direction dir : Direction.values()) {
				BlockPos stone = wall.relative(dir);
				if (!seen.add(stone) || !Scan.takeable(level, stone) || level.getBlockState(stone).ignitedByLava() || ctx.placedByPlayer(stone)
						|| !lavaPoolBeside(level, stone, dir.getOpposite())) {
					continue;
				}
				found.add(Candidate.of(stone, List.of(TraceOp.remove(stone)), base.offset(-radius, -8, -radius), base.offset(radius, 16, radius),
						"The stone between the lava and your wall at " + at(stone) + " is missing, not burned."));
			}
		}
		return found;
	}

	/** A lava source next to {@code stone}, on any side but the wall's. */
	private static boolean lavaPoolBeside(ServerLevel level, BlockPos stone, Direction towardWall) {
		for (Direction dir : Direction.values()) {
			if (dir == towardWall || dir == Direction.DOWN) {
				continue;
			}
			FluidState fluid = level.getFluidState(stone.relative(dir));
			if (fluid.is(FluidTags.LAVA) && fluid.isSource()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean claims(ServerPlayer player, DamageSource source, ArmedTrap armed, AccidentData data, long now) {
		ServerLevel level = player.level();
		AccidentConfig cfg = AccidentConfig.get();
		boolean touchedJustNow = data.tracedBurnTick >= now - TOUCH_TICKS || touchesTraced(level, armed, player.getBoundingBox(), cfg);
		if (source.is(DamageTypes.LAVA) || source.is(DamageTypes.IN_FIRE)) {
			return touchedJustNow;
		}
		if (source.is(DamageTypes.ON_FIRE)) {
			return touchedJustNow || data.tracedBurnTick >= now - cfg.fireBurnMemoryTicks();
		}
		return false;
	}

	/**
	 * True if the box touches lava that came through the gap, or fire within {@code fireTraceRadius} of the gap or of
	 * that lava. Fire elsewhere in the house is not his.
	 */
	public static boolean touchesTraced(ServerLevel level, ArmedTrap armed, AABB box, AccidentConfig cfg) {
		List<BlockPos> lava = new ArrayList<>();
		List<BlockPos> fire = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (Scan.lava(level, pos)) {
				lava.add(pos.immutable());
			} else if (level.getBlockState(pos).is(BlockTags.FIRE)) {
				fire.add(pos.immutable());
			}
		}
		if (lava.isEmpty() && fire.isEmpty()) {
			return false;
		}
		Set<BlockPos> traced = gapLava(level, armed.pos());
		for (BlockPos pos : lava) {
			if (traced.contains(pos)) {
				return true;
			}
		}
		int r = cfg.fireTraceRadius;
		for (BlockPos pos : fire) {
			if (chebyshev(pos, armed.pos()) <= r) {
				return true;
			}
			for (BlockPos cell : traced) {
				if (chebyshev(pos, cell) <= r) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The lava that came through the gap: the gap itself and the flowing (not source) lava connected to it, up to
	 * {@link #LAVA_TRACE_MAX} cells. The pool behind it was always there, so it is not traced.
	 */
	static Set<BlockPos> gapLava(ServerLevel level, BlockPos gap) {
		Set<BlockPos> found = new LinkedHashSet<>();
		if (!Scan.lava(level, gap)) {
			return found;
		}
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(gap);
		found.add(gap);
		while (!queue.isEmpty() && found.size() < LAVA_TRACE_MAX) {
			BlockPos pos = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				FluidState fluid = level.getFluidState(next);
				if (!found.contains(next) && fluid.is(FluidTags.LAVA) && !fluid.isSource()) {
					found.add(next);
					queue.add(next);
				}
			}
		}
		return found;
	}

	private static int chebyshev(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}
}
