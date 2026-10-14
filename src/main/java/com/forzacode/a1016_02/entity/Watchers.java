package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Everyone who could see him: each player's viewpoint (built like {@link TraceService#viewers}) and that player's
 * full, un-pulled render distance. Every player in the level counts, spectators and the dead included (D-060.4,
 * D-062.7): he appears out of view of all of them and never vanishes while any of them sees him. Holds the one
 * despawn rule: he is never removed while in view, unless he is past every watcher's full render distance (where the
 * client fogs him out completely), and the one spawn rule ({@link #hides}).
 */
public record Watchers(List<Watcher> all) {
	/**
	 * @param renderLimit the client's full render distance in blocks (the smaller of its requested view distance and
	 *                    the server's), where vanilla fog is complete. Never the dusk-pulled value.
	 */
	public record Watcher(TraceService.Viewer viewer, double renderLimit) {
	}

	/** Every player in the level, spectators and the dead included. */
	public static Watchers of(ServerLevel level) {
		return of(level.players(), level.getServer().getPlayerList().getViewDistance());
	}

	/** These players as watchers, with the server's view distance in chunks (game tests use mock players). */
	public static Watchers of(List<? extends ServerPlayer> players, int serverChunks) {
		List<Watcher> list = new ArrayList<>();
		for (ServerPlayer player : players) {
			list.add(new Watcher(TraceService.Viewer.of(player, serverChunks), FogEdge.of(player, false).renderLimit()));
		}
		return new Watchers(list);
	}

	/** The rule of {@code TraceService.isOutOfView(level, box)}: near, or in the cone with a line of sight. */
	public boolean sees(Level level, AABB box) {
		Pacing pacing = ModConfig.pacing();
		List<TraceService.Viewer> viewers = all.stream().map(Watcher::viewer).toList();
		return !TraceService.isOutOfView(level, box, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
	}

	/**
	 * The rule of {@code TraceService.isOutOfView(level, positions)} for blocks: true if any watcher is near any of
	 * them or can see one (an open shaft's cells, its opening included).
	 */
	public boolean seesAny(Level level, Collection<BlockPos> cells) {
		Pacing pacing = ModConfig.pacing();
		List<TraceService.Viewer> viewers = all.stream().map(Watcher::viewer).toList();
		return !TraceService.positionsOutOfView(level, cells, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
	}

	/** True if someone is there and he is farther (horizontally) than every watcher's full render distance. */
	public boolean beyondRenderDistance(Vec3 pos) {
		if (all.isEmpty()) {
			return false;
		}
		for (Watcher watcher : all) {
			if (SpotFinder.horizontal(watcher.viewer().eye(), pos) <= watcher.renderLimit()) {
				return false;
			}
		}
		return true;
	}

	/** Every watcher's eyes. */
	public List<Vec3> eyes() {
		return all.stream().map(w -> w.viewer().eye()).toList();
	}

	/**
	 * The one spawn rule: no watcher sees the box and none is closer than {@code minDistance} to it
	 * ({@link FigureApi#tooClose}). He never pops in on anyone's screen and never appears close to anyone.
	 */
	public boolean hides(Level level, AABB box, double minDistance) {
		return !FigureApi.tooClose(eyes(), box, minDistance) && !sees(level, box);
	}

	/** The one rule for every despawn: out of view, or past everyone's full render distance. */
	public boolean mayRemove(Level level, AABB viewBox, Vec3 pos) {
		return beyondRenderDistance(pos) || !sees(level, viewBox);
	}
}
