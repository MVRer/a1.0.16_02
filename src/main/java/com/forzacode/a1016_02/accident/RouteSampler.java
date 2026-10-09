package com.forzacode.a1016_02.accident;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * Learns the subject's routes: every half second it records the cell their feet are in (walking, climbing or
 * swimming, never flying or riding), and fills short gaps between two samples with the open cells in between.
 */
public final class RouteSampler {
	private @Nullable ResourceKey<Level> lastDimension;
	private @Nullable BlockPos last;

	/** Forget the last sample (a new session, a teleport). */
	public void reset() {
		last = null;
		lastDimension = null;
	}

	public void sample(ServerPlayer player, AccidentData data, AccidentConfig cfg, long now, long day) {
		if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isPassenger() || player.isSleeping()) {
			reset();
			return;
		}
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		boolean grounded = player.onGround() || player.onClimbable() || player.isInWater();
		if (!grounded) {
			return;
		}
		boolean changed = record(level, data, cfg, feet, now, day);
		if (last != null && level.dimension().equals(lastDimension)) {
			int dx = feet.getX() - last.getX();
			int dy = feet.getY() - last.getY();
			int dz = feet.getZ() - last.getZ();
			int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
			if (steps > 1 && steps <= cfg.routeInterpolateMax) {
				for (int i = 1; i < steps; i++) {
					double t = (double) i / steps;
					BlockPos cell = new BlockPos(last.getX() + Mth.floor(dx * t + 0.5), last.getY() + Mth.floor(dy * t + 0.5), last.getZ() + Mth.floor(dz * t + 0.5));
					if (Scan.passable(level, cell) && (Scan.fullSolid(level, cell.below()) || level.getBlockState(cell).is(BlockTags.CLIMBABLE) || Scan.water(level, cell))) {
						changed |= record(level, data, cfg, cell, now, day);
					}
				}
			}
		}
		last = feet;
		lastDimension = level.dimension();
		if (changed) {
			data.setDirty();
		}
	}

	private static boolean record(ServerLevel level, AccidentData data, AccidentConfig cfg, BlockPos cell, long now, long day) {
		return data.routes().record(level.dimension(), cell, (int) day, flags(level, cell), now, cfg.routePassGapTicks(), cfg.routeMaxPointsPerDimension);
	}

	/** What kind of place a walked cell is. */
	public static int flags(ServerLevel level, BlockPos cell) {
		int flags = 0;
		if (level.getBlockState(cell).is(BlockTags.CLIMBABLE)) {
			flags |= RouteBook.LADDER;
		}
		if (Scan.covered(level, cell)) {
			flags |= RouteBook.UNDER;
		}
		if (Scan.water(level, cell)) {
			flags |= RouteBook.WATER;
		}
		if (Services.watch().wasPlacedByPlayer(level, cell.below())) {
			flags |= RouteBook.ON_PLACED;
		}
		return flags;
	}
}
