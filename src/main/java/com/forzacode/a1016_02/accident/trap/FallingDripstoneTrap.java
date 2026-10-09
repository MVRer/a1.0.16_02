package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.CoreGaps;
import com.forzacode.a1016_02.accident.RouteBook;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.jspecify.annotations.Nullable;

/**
 * Falling dripstone: the block holding a stalactite over your path in a dripstone cave. A live trap: when you walk
 * under it and the block is out of view, it goes and the stalactite falls. Dripstone falls, it happens. The clue:
 * the block above it is gone. Needs the core falling-block opt-in ({@link CoreGaps}); today core would remove the
 * stalactite as a silent dependent instead.
 */
public final class FallingDripstoneTrap extends BaseTrap {
	public FallingDripstoneTrap() {
		super("falling_dripstone", true, "crushed", EnumSet.of(Habit.WATCHER), DamageTypes.FALLING_STALACTITE);
	}

	@Override
	public @Nullable String blocked() {
		return CoreGaps.FALLING_OPT_IN ? null : "needs the core opt-in that lets a stalactite fall";
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		List<Candidate> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (RouteBook.Spot spot : ctx.routeSpots()) {
			BlockPos path = spot.pos();
			if (!Scan.open(level, path)) {
				continue;
			}
			BlockPos tip = null;
			for (int dy = 2; dy <= ctx.cfg().dripstoneMaxHeight; dy++) {
				BlockPos up = path.above(dy);
				if (!Scan.open(level, up)) {
					tip = stalactite(level.getBlockState(up)) ? up : null;
					break;
				}
			}
			if (tip == null || tip.getY() - path.getY() < ctx.cfg().dripstoneMinHeight) {
				continue;
			}
			BlockPos root = tip;
			while (stalactite(level.getBlockState(root.above()))) {
				root = root.above();
			}
			BlockPos holder = root.above();
			if (!seen.add(holder) || !Scan.takeable(level, holder)) {
				continue;
			}
			found.add(Candidate.of(holder, List.of(TraceOp.remove(holder)), new BlockPos(path.getX(), path.getY() - 1, path.getZ()), holder,
					"The block above the stalactite over your path at " + at(holder) + " is gone."));
		}
		return found;
	}

	static boolean stalactite(BlockState state) {
		return state.is(Blocks.POINTED_DRIPSTONE) && state.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.DOWN;
	}

	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		ServerLevel level = ctx.level();
		BlockPos holder = armed.pos();
		if (!Scan.takeable(level, holder) || !stalactite(level.getBlockState(holder.below()))) {
			return null;
		}
		ServerPlayer player = ctx.player();
		if (armed.isSet() || player == null || player.level() != level) {
			return armed;
		}
		BlockPos feet = player.blockPosition();
		if (feet.getX() != holder.getX() || feet.getZ() != holder.getZ() || feet.getY() > armed.zoneMin().getY() + 2) {
			return armed;
		}
		List<BlockPos> watched = new ArrayList<>();
		watched.add(holder);
		for (BlockPos down = holder.below(); stalactite(level.getBlockState(down)); down = down.below()) {
			watched.add(down);
		}
		if (!ctx.view().outOfView(level, watched) || !CoreGaps.removeLettingFall(level, holder, "accident:" + id())) {
			return armed;
		}
		return armed.set(ctx.now(), window(ctx.cfg()), List.of(holder));
	}
}
