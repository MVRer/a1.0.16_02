package com.forzacode.a1016_02.world.sc2.s17;

import java.util.Map;
import java.util.TreeMap;

import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;

/**
 * Dev (SC2-H's survey): why an archive's ways in do not fit a start, way by way, by the rules of
 * {@code ArchivePlan.measure}: no surface, no way out past the mouth, a wet mouth or exit, a drop out of the exit.
 */
public final class ArchiveWays {
	private ArchiveWays() {
	}

	/** Counts each way's verdict for this start into {@code tally}. */
	public static void why(Start start, Terrain terrain, Map<String, Integer> tally) {
		ArchivePlan probe = ArchivePlan.of(start, new int[0]);
		int y0 = probe.floorY();
		for (int t = 0; t < 3; t++) {
			int left = probe.tunnels[t].left();
			String why = verdict(probe, left, y0, terrain);
			tally.merge((t == 0 ? "open " : "closed ") + why, 1, Integer::sum);
		}
	}

	private static int ground(ArchivePlan plan, int left, int i, Terrain terrain) {
		BlockPos p = plan.frame.pos(left, 0, plan.tunnelF0 + i);
		return terrain.ground(p.getX(), p.getZ());
	}

	private static String verdict(ArchivePlan plan, int left, int y0, Terrain terrain) {
		int mouth = -1;
		for (int i = 0; i <= ArchivePlan.MAX_TUNNEL; i++) {
			if (y0 + ArchivePlan.foot(i) + 1 >= ground(plan, left, i, terrain)) {
				mouth = i;
				break;
			}
		}
		if (mouth < 0) {
			return "no surface";
		}
		mouth = Math.max(mouth, ArchivePlan.PLUG + 2);
		int exit = -1;
		for (int k = mouth; k <= mouth + ArchivePlan.MAX_NOTCH; k++) {
			if (y0 + ArchivePlan.foot(k) >= ground(plan, left, k, terrain) + 1) {
				exit = k;
				break;
			}
		}
		if (exit < 0) {
			return "no exit";
		}
		if (y0 + ArchivePlan.foot(exit) - 1 - ground(plan, left, exit, terrain) > ArchivePlan.EXIT_DROP) {
			return "drop";
		}
		for (int k : new int[] {mouth, exit}) {
			for (int c = 0; c <= 1; c++) {
				BlockPos p = plan.frame.pos(left + c, 0, plan.tunnelF0 + k);
				if (terrain.surface(p.getX(), p.getZ()) > terrain.ground(p.getX(), p.getZ())) {
					return k == mouth ? "wet mouth" : "wet exit";
				}
			}
		}
		return "fits";
	}

	/** The first try of the archive's siting in this query's chunk, taken apart into {@code tally} (or "no floor"). */
	public static void whyAt(com.forzacode.a1016_02.world.sc2.kit.Siting.Query query, Map<String, Integer> tally) {
		Sc17Config config = new Sc17Config();
		int cx = query.chunk().getMiddleBlockX();
		int cz = query.chunk().getMiddleBlockZ();
		net.minecraft.core.Direction facing = com.forzacode.a1016_02.world.sc2.kit.Siting.facingToOrigin(cx, cz);
		long seed = query.seed(S17.ID);
		boolean longHall = com.forzacode.a1016_02.world.sc2.kit.Draw.unit(seed, 20, 0, 0) < config.longHallChance;
		int length = longHall ? ArchivePlan.LONG : ArchivePlan.SHORT;
		int rows = longHall ? 5 : 3;
		int front = ArchivePlan.CELLS_BACK + ArchivePlan.CELL * rows + 1 + ArchiveSiting.EXPECTED_TUNNEL;
		int shift = (-(length + 3) + front) / 2;
		int ox = cx - facing.getStepX() * shift;
		int oz = cz - facing.getStepZ() * shift;
		java.util.Optional<Integer> floor = ArchiveSiting.floor(query, new BlockPos(ox, 0, oz), facing, length, longHall ? 7 : 5, rows, seed);
		if (floor.isEmpty()) {
			tally.merge("no floor", 1, Integer::sum);
			return;
		}
		why(new Start(new BlockPos(ox, floor.get(), oz), facing, ArchivePlan.variant(longHall, config.infestedShare), seed), query.terrain(), tally);
	}

	public static Map<String, Integer> tally() {
		return new TreeMap<>();
	}
}
