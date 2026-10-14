package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Everything a trap needs to look for a spot or take its step: the level, the player it is for (looked up this tick,
 * null in some tests or while they are away), whose routes and records to use, the players who could set it off right
 * now, where to look, the view gate and the config.
 *
 * @param player  the trap's own player, this tick's object (D-062.1), or null
 * @param target  whose routes, base and records "your" means (D-061): the trap's player, even while they are away
 * @param players everyone in this level who could set it off now (targetable participants, the trap's player first):
 *                a live trap springs for whichever of them is in place (D-062.6, dangers hit whoever acts)
 * @param now     play ticks ({@link com.forzacode.a1016_02.core.GameClock#playTicks}, the world clock)
 * @param day     in-game day ({@link com.forzacode.a1016_02.core.GameClock#day})
 * @param watch   who takes part, and each player's base
 */
public record TrapContext(ServerLevel level, @Nullable ServerPlayer player, @Nullable UUID target, List<ServerPlayer> players, BlockPos center,
		AccidentData data, ViewGate view, AccidentConfig cfg, long now, long day, PlayerWatch watch) {
	public TrapContext {
		players = List.copyOf(players);
	}

	/** A context for one player (or none): their routes, and they alone could set it off. */
	public TrapContext(ServerLevel level, @Nullable ServerPlayer player, BlockPos center, AccidentData data, ViewGate view, AccidentConfig cfg, long now,
			long day) {
		this(level, player, player == null ? null : player.getUUID(), player == null ? List.of() : List.of(player), center, data, view, cfg, now, day,
				Services.watch());
	}

	/** The target's learned routes (empty with no target). */
	public RouteBook routes() {
		return target == null ? new RouteBook() : data.routes(target);
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

	/** On the target's routes. */
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
