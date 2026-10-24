package com.forzacode.a1016_02.world.sc2.s17;

import java.util.Optional;

import com.forzacode.a1016_02.world.sc2.kit.Draw;
import com.forzacode.a1016_02.world.sc2.kit.Frame;
import com.forzacode.a1016_02.world.sc2.kit.Heights;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.biome.Biomes;

/**
 * Where an archive goes in a candidate chunk (docs/SELF_CREATED_2.md, feature 17, "Rules (structure)"): its way in faces
 * the origin (D-095); the hall's floor between Y -24 and 40, the box under it ({@value #BELOW_FLOOR}) at least the kit's margin
 * over the world's bottom (SC2-H, {@link Heights}: the level's own; it was 18 for the floor), its ceiling at least
 * 16 under the ground at every corner of the hall and of the cells; not in the deep dark; every tunnel finds the surface,
 * and the whole box stays within the kit's reach of the chunk (one try moved to centre it). The kit's gates and vanilla's
 * biome check (the JSON's {@code #a1016_02:world/s17}, at the origin column's ground) come before and after. About 80
 * ground samples per tried origin: 6 corners and three tunnels' coarse walks.
 */
final class ArchiveSiting implements Siting {
	/** The hall's floor's highest and lowest Y. */
	static final int FLOOR_MAX = 40;
	static final int FLOOR_MIN = -24;
	/** The ceiling's top (u 19) at least 16 under the ground: the floor at most this under the lowest corner. */
	static final int UNDER_GROUND = 36;
	/** The box reaches this far under the hall's floor (his tunnel from the bay, down one in two, and the seal under it). */
	static final int BELOW_FLOOR = ArchivePlan.BAY_LENGTH / 2 + 3;
	/** The tunnel the first try expects (cells), to place the origin so the whole stays in reach. */
	static final int EXPECTED_TUNNEL = 80;

	@Override
	public Optional<Start> find(Query query) {
		Sc17Config config = query.snapshot().config(S17.ID, Sc17Config.class);
		if (config == null) {
			return Optional.empty();
		}
		int cx = query.chunk().getMiddleBlockX();
		int cz = query.chunk().getMiddleBlockZ();
		Direction facing = Siting.facingToOrigin(cx, cz);
		long seed = query.seed(S17.ID);
		boolean longHall = Draw.unit(seed, 20, 0, 0) < config.longHallChance;
		int variant = ArchivePlan.variant(longHall, config.infestedShare);
		int length = longHall ? ArchivePlan.LONG : ArchivePlan.SHORT;
		int rows = longHall ? 5 : 3;
		int front = ArchivePlan.CELLS_BACK + ArchivePlan.CELL * rows + 1 + EXPECTED_TUNNEL;
		int shift = (-(length + 3) + front) / 2;
		for (int attempt = 0; attempt < 2; attempt++) {
			int ox = cx - facing.getStepX() * shift;
			int oz = cz - facing.getStepZ() * shift;
			Optional<Integer> floor = floor(query, new BlockPos(ox, 0, oz), facing, length, longHall ? 7 : 5, rows, seed);
			if (floor.isEmpty()) {
				return Optional.empty();
			}
			Start start = new Start(new BlockPos(ox, floor.get(), oz), facing, variant, seed);
			ArchivePlan plan = ArchivePlan.of(start, query.terrain());
			if (!plan.fits) {
				return Optional.empty();
			}
			if (Sc2Structure.withinReach(plan.box(), query.chunk())) {
				// The kit takes this plan's shape instead of measuring the three ways again (SC2-H: a third of the samples).
				Siting.handOver(start, new ArchiveShape(plan));
				return Optional.of(start);
			}
			// Centre the box on the chunk along the way in and try once more.
			BlockPos c = plan.box().getCenter();
			int off = (c.getX() - cx) * facing.getStepX() + (c.getZ() - cz) * facing.getStepZ();
			shift += off;
		}
		return Optional.empty();
	}

	/** The hall's floor for an origin column, or empty if it cannot stand there (depth, range, the deep dark). */
	static Optional<Integer> floor(Query query, BlockPos column, Direction facing, int length, int cols, int rows, long seed) {
		Frame frame = new Frame(column, facing);
		int outer = 4 * cols + 1;
		int front = ArchivePlan.CELLS_BACK + ArchivePlan.CELL * rows + 1;
		int[][] corners = {{-ArchivePlan.SHELL, 2}, {ArchivePlan.SHELL, 2}, {-ArchivePlan.SHELL, -length - 3}, {ArchivePlan.SHELL, -length - 3},
				{-outer, front}, {outer, front}};
		int lowest = Integer.MAX_VALUE;
		for (int[] corner : corners) {
			BlockPos p = frame.pos(corner[0], 0, corner[1]);
			lowest = Math.min(lowest, query.terrain().ground(p.getX(), p.getZ()));
		}
		int floor = Math.min(FLOOR_MAX, lowest - UNDER_GROUND) - Draw.between(seed, 21, 0, 6);
		if (floor < FLOOR_MIN || floor - BELOW_FLOOR < query.minY() + Heights.BOTTOM) {
			return Optional.empty();
		}
		BlockPos middle = frame.pos(0, 0, -length / 2);
		if (query.terrain().biome(middle.getX(), floor + 8, middle.getZ()).is(Biomes.DEEP_DARK)) {
			return Optional.empty();
		}
		return Optional.of(floor);
	}
}
