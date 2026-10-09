package com.forzacode.a1016_02.accident;

import java.util.Collection;
import java.util.List;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * The out-of-view question the planner asks before it touches anything. In play it is
 * {@link TraceService#isOutOfView} (and every edit is checked again by core). Game tests use fixed viewpoints,
 * since a mock player in the level would join the shared test server as the subject.
 */
public interface ViewGate {
	boolean outOfView(ServerLevel level, Collection<BlockPos> positions);

	boolean outOfView(ServerLevel level, AABB box);

	/** Every player in the level, through core. */
	ViewGate TRACES = new ViewGate() {
		@Override
		public boolean outOfView(ServerLevel level, Collection<BlockPos> positions) {
			return Services.traces().isOutOfView(level, positions);
		}

		@Override
		public boolean outOfView(ServerLevel level, AABB box) {
			return Services.traces().isOutOfView(level, box);
		}
	};

	/** Fixed viewpoints, with the configured near distance and cone (tests). */
	static ViewGate of(List<TraceService.Viewer> viewers) {
		return new ViewGate() {
			@Override
			public boolean outOfView(ServerLevel level, Collection<BlockPos> positions) {
				Pacing pacing = ModConfig.pacing();
				return TraceService.positionsOutOfView(level, positions, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
			}

			@Override
			public boolean outOfView(ServerLevel level, AABB box) {
				Pacing pacing = ModConfig.pacing();
				return TraceService.isOutOfView(level, box, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
			}
		};
	}
}
