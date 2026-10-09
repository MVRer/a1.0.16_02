package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Everyone who could see him: each player's viewpoint (built like {@link TraceService#viewers}) and that player's
 * full, un-pulled render distance. Holds the one despawn rule: he is never removed while in view, unless he is past
 * every watcher's full render distance (where the client fogs him out completely).
 */
public record Watchers(List<Watcher> all) {
	/**
	 * @param renderLimit the client's full render distance in blocks (the smaller of its requested view distance and
	 *                    the server's), where vanilla fog is complete. Never the dusk-pulled value.
	 */
	public record Watcher(TraceService.Viewer viewer, double renderLimit) {
	}

	public static Watchers of(ServerLevel level) {
		int serverChunks = level.getServer().getPlayerList().getViewDistance();
		List<Watcher> list = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
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

	/** The one rule for every despawn: out of view, or past everyone's full render distance. */
	public boolean mayRemove(Level level, AABB viewBox, Vec3 pos) {
		return beyondRenderDistance(pos) || !sees(level, viewBox);
	}
}
