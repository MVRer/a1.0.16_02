package com.forzacode.a1016_02.world;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.s18.HollowShape;
import com.forzacode.a1016_02.world.sc2.s18.HollowSiting;
import com.forzacode.a1016_02.world.sc2.s18.S18;
import com.forzacode.a1016_02.world.sc2.s18.Sc18Config;
import com.forzacode.a1016_02.world.sc2.s20.FloorSiting;
import com.forzacode.a1016_02.world.sc2.s20.S20;
import com.forzacode.a1016_02.world.sc2.s20.Sc20Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/** Why the hollow or the floor refused a candidate (R3-3's find report; tests only). */
final class HollowFloorProbe {
	private HollowFloorProbe() {
	}

	/**
	 * What the land round a hollow candidate is (the find's diagnosis): its biome at the middle, and whether the rise holds
	 * facing the origin, facing the best of the four sides, and facing the best side with a lower bar (20 over the floor,
	 * 30 %).
	 */
	static String terrain(Sc2RealNoise.World world, ChunkPos chunk, Snapshot snapshot) {
		Terrain terrain = world.terrain();
		Sc18Config config = snapshot.config(S18.ID, Sc18Config.class);
		int cx = chunk.getMiddleBlockX();
		int cz = chunk.getMiddleBlockZ();
		int middle = terrain.ground(cx, cz);
		boolean tagged = terrain.biome(cx, middle, cz).is(HollowSiting.BIOMES);
		int[] grid = HollowSiting.grid(terrain, cx, cz, middle);
		boolean origin = HollowSiting.rise(grid, HollowSiting.floor(grid, Siting.facingToOrigin(cx, cz), terrain.seaLevel()), config.minRise) >= HollowSiting.RISE_SHARE;
		boolean best = false;
		boolean low = false;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int floor = HollowSiting.floor(grid, d, terrain.seaLevel());
			best |= HollowSiting.rise(grid, floor, config.minRise) >= HollowSiting.RISE_SHARE;
			low |= HollowSiting.rise(grid, floor, 20) >= 0.3;
		}
		if (!origin && !best && !low) {
			return (tagged ? "tagged" : "other") + " flat";
		}
		String biome = terrain.biome(cx, middle, cz).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
		return (tagged ? "tagged " : "other ") + biome + (origin ? " origin" : "") + (best ? " best" : "") + (low ? " low" : "");
	}

	/** The refusal's reason, read again step by step ({@code why} is the kit's own word for the verdict). */
	static String why(String kind, Sc2RealNoise.World world, ChunkPos chunk, Snapshot snapshot, String why) {
		if (!why.equals("siting")) {
			return why.startsWith("height") || why.contains("under") || why.contains("over") ? "height" : why;
		}
		Terrain terrain = world.terrain();
		if (kind.equals("s18")) {
			Sc18Config config = snapshot.config(S18.ID, Sc18Config.class);
			int cx = chunk.getMiddleBlockX();
			int cz = chunk.getMiddleBlockZ();
			int middle = terrain.ground(cx, cz);
			if (!terrain.biome(cx, middle, cz).is(HollowSiting.BIOMES)) {
				return "biome";
			}
			if (HollowSiting.flat(terrain, cx, cz, middle)) {
				return "flat";
			}
			int[] grid = HollowSiting.grid(terrain, cx, cz, middle);
			String verdict = "rise";
			for (Direction facing : HollowSiting.facings(cx, cz)) {
				int floor = HollowSiting.floor(grid, facing, terrain.seaLevel());
				if (HollowSiting.rise(grid, floor, config.minRise) < HollowSiting.RISE_SHARE) {
					continue;
				}
				Start start = new Start(new BlockPos(cx, floor, cz), facing, HollowSiting.variant(config, 1L, true), 1L);
				HollowShape shape = new HollowShape(start, terrain);
				String r = shape.refusal();
				verdict = r == null ? "height (siting)" : r;
			}
			return verdict;
		}
		Sc20Config config = snapshot.config(S20.ID, Sc20Config.class);
		int cx = chunk.getMinBlockX();
		int cz = chunk.getMinBlockZ();
		int side = config.sideBlocks();
		int[] ground = new int[25];
		int i = 0;
		for (int a = 0; a < 5; a++) {
			for (int b = 0; b < 5; b++) {
				ground[i++] = terrain.ground(cx - side / 2 + Math.round((side - 1) * a / 4.0F), cz - side / 2 + Math.round((side - 1) * b / 4.0F));
			}
		}
		int relief = java.util.Arrays.stream(ground).max().orElse(0) - java.util.Arrays.stream(ground).min().orElse(0);
		if (relief < side / FloorSiting.RELIEF_MIN_PER) {
			return "relief low";
		}
		if (relief > side / FloorSiting.RELIEF_MAX_PER) {
			return "relief high";
		}
		return FloorSiting.fit(terrain, cx, cz, side) == null ? "wet" : "height (siting)";
	}
}
