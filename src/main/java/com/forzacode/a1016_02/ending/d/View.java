package com.forzacode.a1016_02.ending.d;

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
 * The out-of-view question Ending D asks before it touches anything. In play it is core's
 * {@link TraceService#isOutOfView} (and every edit is checked again by core, with its dependents). Game tests use
 * fixed viewpoints, since a mock player in the level would join the shared test server as the subject.
 */
public interface View {
	boolean outOfView(ServerLevel level, Collection<BlockPos> positions);

	boolean outOfView(ServerLevel level, AABB box);

	default boolean outOfView(ServerLevel level, BlockPos pos) {
		return outOfView(level, List.of(pos));
	}

	/** Every player in the level, through core. */
	View TRACES = new View() {
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
	static View of(List<TraceService.Viewer> viewers) {
		return new View() {
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
