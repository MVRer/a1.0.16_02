package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Everything a trap needs to look for a spot or take its step: the level, the subject (null in some tests), where
 * to look, the learned routes, the view gate and the config.
 *
 * @param now play ticks ({@link com.forzacode.a1016_02.core.GameClock#playTicks})
 * @param day in-game day ({@link com.forzacode.a1016_02.core.GameClock#day})
 */
public record TrapContext(ServerLevel level, @Nullable ServerPlayer player, BlockPos center, AccidentData data, ViewGate view,
		AccidentConfig cfg, long now, long day) {
	public RouteBook routes() {
		return data.routes();
	}

	/** Walked cells near the center, nearest first. */
	public List<RouteBook.Spot> routeSpots() {
		return routes().near(level.dimension(), center, cfg.scanRadius, cfg.scanMaxPoints);
	}

	/**
	 * Cells the player moved through near the center: walked cells plus blocks they dug out (their tunnels), as long
	 * as they are still open. Nearest first.
	 */
	public List<BlockPos> walkedAndDug() {
		Set<BlockPos> cells = new LinkedHashSet<>();
		for (RouteBook.Spot spot : routeSpots()) {
			cells.add(spot.pos());
		}
		for (BlockPos pos : Services.watch().dugNear(level, center, cfg.scanRadius)) {
			if (Scan.open(level, pos)) {
				cells.add(pos);
			}
		}
		List<BlockPos> sorted = new ArrayList<>(cells);
		sorted.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
		return sorted.size() > cfg.scanMaxPoints ? sorted.subList(0, cfg.scanMaxPoints) : sorted;
	}

	/** Mine cells: walked cells with no sky above, plus blocks the player dug out that are still open. Nearest first. */
	public List<BlockPos> tunnelCells() {
		Set<BlockPos> cells = new LinkedHashSet<>();
		for (RouteBook.Spot spot : routeSpots()) {
			if (spot.point().has(RouteBook.UNDER)) {
				cells.add(spot.pos());
			}
		}
		for (BlockPos pos : Services.watch().dugNear(level, center, cfg.scanRadius)) {
			if (Scan.open(level, pos)) {
				cells.add(pos);
			}
		}
		List<BlockPos> sorted = new ArrayList<>(cells);
		sorted.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
		return sorted.size() > cfg.scanMaxPoints ? sorted.subList(0, cfg.scanMaxPoints) : sorted;
	}

	public boolean walked(BlockPos pos) {
		return routes().contains(level.dimension(), pos);
	}

	public boolean placedByPlayer(BlockPos pos) {
		return Services.watch().wasPlacedByPlayer(level, pos);
	}

	public boolean dugByPlayer(BlockPos pos) {
		return Services.watch().wasDugByPlayer(level, pos);
	}
}
