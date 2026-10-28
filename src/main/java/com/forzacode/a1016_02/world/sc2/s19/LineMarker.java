package com.forzacode.a1016_02.world.sc2.s19;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.world.sc2.kit.AxesPlacement;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

/**
 * The line's marker for {@code /locate} (docs/SELF_CREATED_2_R3.md, D-112): the structure {@code a1016_02:world/s19}
 * (the kit's structure type as a marker) on the placement {@code a1016_02:world/sc2a} ({@link AxesPlacement}), one
 * empty start at the ground of each planned segment's middle chunk, as {@link LineFeature#segments} lays them out (each
 * segment fixed in this world first, R12-2): none while the line builds nothing (no snapshot, the switch off, the
 * world's plans not read). It writes nothing and records nothing; the feature keeps its own id in the feature registry.
 * R3-2 keeps it in step with {@link LinePlan}.
 */
public final class LineMarker {
	private LineMarker() {
	}

	/** Called once from {@link S19#init}: the placement type and the line's marks. */
	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, A1016_02.id("world/sc2a"), AxesPlacement.CODEC);
		AxesPlacement.register(S19.ID, LineMarker::chunks);
	}

	/** Each planned segment's middle chunk for this snapshot (none while the line builds nothing). Any thread. */
	public static List<ChunkPos> chunks(@Nullable Snapshot snapshot, int minY, int maxY) {
		List<ChunkPos> out = new ArrayList<>();
		for (LinePlan.Segment s : LineFeature.segments(snapshot, minY, maxY)) {
			int t = (s.t0() + s.t1()) / 2;
			out.add(ChunkPos.containing(new BlockPos(s.x(t), 0, s.z(t))));
		}
		return out;
	}
}
