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
	/** Version 3: the ceiling's top (u 43) at least 8 under the ground at every corner. */
	static final int UNDER_GROUND3 = ArchivePlan.CEILING3 + 1 + 8;
	/** The box reaches this far under the hall's floor (his tunnel from the bay, down one in two, and the seal under it). */
	static final int BELOW_FLOOR = ArchivePlan.BAY_LENGTH / 2 + 3;
	/** The tunnel the first try expects (cells), to place the origin so the whole stays in reach. */
	static final int EXPECTED_TUNNEL = 80;
	/** Version 3: the hall 15 deeper, its ways about 30 cells longer. */
	static final int EXPECTED_TUNNEL3 = 110;

	/** Tests only: told why a siting refused (null: nobody asks). */
	static volatile java.util.function.@org.jspecify.annotations.Nullable Consumer<String> why;

	private static Optional<Start> refuse(String reason) {
		java.util.function.Consumer<String> sink = why;
		if (sink != null) {
			sink.accept(reason);
		}
		return Optional.empty();
	}

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
		int length = longHall ? ArchivePlan.LONG : ArchivePlan.SHORT;
		// Version 3 (D-113): mirrored once more (the well on the bay's side) when the well's land or the ways refuse it.
		boolean[] again = {false};
		Optional<Start> first = attempt(query, config, facing, seed, longHall, length, cx, cz, false, again);
		if (first.isPresent() || !again[0]) {
			return first;
		}
		return attempt(query, config, facing, seed, longHall, length, cx, cz, true, again);
	}

	/**
	 * One siting, mirrored or not; {@code again} set when it was refused for a reason a mirrored one may not share (the
	 * well's land, the ways, the reach).
	 */
	private static Optional<Start> attempt(Query query, Sc17Config config, Direction facing, long seed, boolean longHall, int length, int cx,
			int cz, boolean mirror, boolean[] again) {
		again[0] = false;
		// If the whole does not stay in reach, the long hall tries once more with the short hall's cells.
		for (int annex = 0; annex < (longHall ? 2 : 1); annex++) {
			boolean small = annex == 1;
			int variant = ArchivePlan.variant3(longHall, config.infestedShare, small, config.wellLitGalleries, query.minY())
					| (mirror ? ArchivePlan.MIRROR : 0);
			int rows = longHall && !small ? 5 : 3;
			int cols = longHall && !small ? 7 : 5;
			int front = ArchivePlan.CELLS_BACK + ArchivePlan.CELL * rows + 1 + EXPECTED_TUNNEL3;
			int shift = (-(length + 3) + front) / 2;
			for (int attempt = 0; attempt < 2; attempt++) {
				int ox = cx - facing.getStepX() * shift;
				int oz = cz - facing.getStepZ() * shift;
				Optional<Integer> floor = floor3(query, new BlockPos(ox, 0, oz), facing, length, cols, rows, seed, mirror);
				if (floor.isEmpty()) {
					return refuse("floor");
				}
				Start start = new Start(new BlockPos(ox, floor.get(), oz), facing, variant, seed);
				ArchivePlan plan = ArchivePlan.of(start, query.terrain());
				if (!plan.fits) {
					again[0] = true;
					return refuse(why == null ? "ways" : plan.well != null && Well.measure(plan, query.terrain())[2] == 0 ? "apron" : waysWhy(plan, query));
				}
				if (plan.well != null && deepDark(query, plan.well)) {
					again[0] = true;
					return refuse("deep dark");
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
		}
		again[0] = true;
		return refuse("reach");
	}

	/** Version 3: the grid the land is read on over the body and round it (blocks). */
	static final int COVER_STEP = 8;
	/** Version 3: the ring round the body read too (blocks out from its shell): a cliff or a valley beside it. */
	static final int COVER_RING = 6;

	/**
	 * Version 3's floor (Mariano's report on 1.5.0: never a wall of it in sight): the land read every {@value #COVER_STEP}
	 * (and on every edge) over the hall, the cells and the corridor to the well and in a ring {@value #COVER_RING} round
	 * them, the floor {@link #UNDER_GROUND3} under the lowest of it all, so the ceiling's top is at least 8 under the land
	 * over and beside every part of the body, a valley, a river bed, a cliff or a crevasse wider than the grid included
	 * (a narrower one is jacketed by the shape); empty if that puts it under {@link #FLOOR_MIN}, or in the deep dark. About
	 * 100 (short hall) to 150 (long) samples.
	 */
	static Optional<Integer> floor3(Query query, BlockPos column, Direction facing, int length, int cols, int rows, long seed) {
		return floor3(query, column, facing, length, cols, rows, seed, false);
	}

	/** {@link #floor3}, mirrored or not (the well's side). */
	static Optional<Integer> floor3(Query query, BlockPos column, Direction facing, int length, int cols, int rows, long seed, boolean mirror) {
		Frame frame = new Frame(column, facing);
		int wellSide = (Draw.between(seed, 14, 0, 1) == 0 ? 1 : -1) * (mirror ? -1 : 1); // the side away from the bay (ArchivePlan.baySide)
		int outer = 4 * cols + 1;
		int front = ArchivePlan.CELLS_BACK + ArchivePlan.CELL * rows + 1;
		int ring = COVER_RING;
		// Each part: its rectangle (r0, r1, f0, f1) and how far its top is over the floor with 8 of cover; then the same
		// with its ring, its top with 3 (the jacket's own cover: a cliff or a valley beside it).
		int[][] rects = {
				{-ArchivePlan.SHELL, ArchivePlan.SHELL, -length - 3, ArchivePlan.CELLS_BACK - 1, UNDER_GROUND3},
				{-outer, outer, ArchivePlan.CELLS_BACK - 1, front, ArchivePlan.CELL_HEIGHT + 2 + 8},
				// the corridor's strip to the well (its far end is at most 4 cols + 3 + 15 out)
				{wellSide > 0 ? ArchivePlan.SHELL : -(outer + 2 + 16), wellSide > 0 ? outer + 2 + 16 : -ArchivePlan.SHELL,
						Well.DOOR_F - 2, Well.DOOR_F + 2, Well.DOOR_HIGH + 1 + 8},
				{-ArchivePlan.SHELL - ring, ArchivePlan.SHELL + ring, -length - 3 - ring, ArchivePlan.CELLS_BACK - 1, UNDER_GROUND3 - 5},
				{-outer - ring, outer + ring, ArchivePlan.CELLS_BACK - 1, front + ring, ArchivePlan.CELL_HEIGHT + 2 + 3}};
		int highestFloor = Integer.MAX_VALUE;
		for (int[] rect : rects) {
			int lowest = Integer.MAX_VALUE;
			for (int r = rect[0]; ; r = Math.min(r + COVER_STEP, rect[1])) {
				for (int f = rect[2]; ; f = Math.min(f + COVER_STEP, rect[3])) {
					BlockPos p = frame.pos(r, 0, f);
					lowest = Math.min(lowest, query.terrain().ground(p.getX(), p.getZ()));
					if (f >= rect[3]) {
						break;
					}
				}
				if (r >= rect[1]) {
					break;
				}
			}
			highestFloor = Math.min(highestFloor, lowest - rect[4]);
		}
		int floor = Math.min(FLOOR_MAX, highestFloor) - Draw.between(seed, 21, 0, 6);
		if (floor < FLOOR_MIN || floor - BELOW_FLOOR < query.minY() + Heights.BOTTOM) {
			return Optional.empty();
		}
		BlockPos middle = frame.pos(0, 0, -length / 2);
		if (query.terrain().biome(middle.getX(), floor + 8, middle.getZ()).is(Biomes.DEEP_DARK)) {
			return Optional.empty();
		}
		return Optional.of(floor);
	}

	/** Tests only: which of the ways failed (each closed: a mouth; the open one: its exit). */
	private static String waysWhy(ArchivePlan plan, Query query) {
		StringBuilder out = new StringBuilder("ways");
		for (int t = 0; t < 3; t++) {
			boolean mouth = plan.measure(plan.tunnels[t].left(), query.terrain(), false)[0] > 0;
			boolean exit = t != 0 || plan.measure(plan.tunnels[t].left(), query.terrain(), true)[0] > 0;
			out.append(mouth ? exit ? "+" : "e" : "m");
		}
		return out.toString();
	}

	/** Version 3: the well's column in the deep dark at its pool, halfway up or at the land line (three biome reads). */
	static boolean deepDark(Query query, Well well) {
		BlockPos c = well.at(Well.SIDE / 2, 0, Well.SIDE / 2);
		int pool = well.floorY(well.kMin);
		for (int y : new int[] {pool, (pool + well.land) / 2, well.land}) {
			if (query.terrain().biome(c.getX(), y, c.getZ()).is(Biomes.DEEP_DARK)) {
				return true;
			}
		}
		return false;
	}

	/** The hall's floor for an origin column, or empty if it cannot stand there (depth, range, the deep dark). */
	static Optional<Integer> floor(Query query, BlockPos column, Direction facing, int length, int cols, int rows, long seed) {
		return floor(query, column, facing, length, cols, rows, seed, UNDER_GROUND);
	}

	/** {@link #floor}, the ceiling's top {@code underGround} under the lowest corner at least (version 3: 51). */
	static Optional<Integer> floor(Query query, BlockPos column, Direction facing, int length, int cols, int rows, long seed, int underGround) {
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
		int floor = Math.min(FLOOR_MAX, lowest - underGround) - Draw.between(seed, 21, 0, 6);
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
