package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The archive's gallery exhibits (development only; docs/SELF_CREATED_2.md, feature 17, "Gallery"): {@code 17} (the
 * short hall), {@code 17l} (the long hall) and {@code 17n} (after its lights went, staged as 17b leaves them). Each is
 * built by {@link Live#place} under a hill the way in opens on, in the gallery's deep flat world (ground at Y 40, the
 * hall's floor at Y 2: its ceiling 16 under the ground).
 */
final class ArchiveGallery {
	/** The hall's floor in the gallery world. */
	static final int FLOOR = 2;
	/** The exhibits' own seed, so each looks the same every run. */
	static final long SEED = 0x17A2C4E5L;

	private ArchiveGallery() {
	}

	static void register() {
		Gallery.register(new Gallery.Exhibit("17", "17", (level, origin, seed) -> place(level, origin, false, false)));
		Gallery.register(new Gallery.Exhibit("17l", "17", (level, origin, seed) -> place(level, origin, true, false)));
		Gallery.register(new Gallery.Exhibit("17n", "17", (level, origin, seed) -> place(level, origin, false, true)));
	}

	/** A camera point in the plan's local coordinates (cell corners at whole numbers, as {@link ArchivePlan#at}). */
	static Vec3 local(ArchivePlan plan, double r, double u, double f) {
		Direction right = plan.frame.right();
		Direction facing = plan.frame.facing();
		BlockPos o = plan.start.origin();
		// Cell (r, f)'s centre is at r + 0.5 along right and f + 0.5 along facing, whatever their signs.
		double x = o.getX() + 0.5 + right.getStepX() * r + facing.getStepX() * f;
		double z = o.getZ() + 0.5 + right.getStepZ() * r + facing.getStepZ() * f;
		return new Vec3(x, o.getY() + u, z);
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, boolean longHall, boolean dark) {
		Direction facing = Direction.NORTH;
		Start start = new Start(new BlockPos(origin.getX(), FLOOR, origin.getZ()), facing, ArchivePlan.variant(longHall, 0.08), SEED);
		// Where it would open on the flat: the hill stands over that, so the way in opens on its slope.
		ArchivePlan flat = ArchivePlan.of(start, Live.terrain(level));
		ArchivePlan.Tunnel way = flat.tunnels[0];
		BlockPos hillAt = flat.at(way.left(), 0, flat.tunnelF0 + way.mouth() - 34);
		GalleryProps.hill(level, new BlockPos(hillAt.getX(), origin.getY(), hillAt.getZ()), 72, 46);
		Live.Result built = Live.place(level, S17.KIND, start);
		ArchivePlan plan = ((ArchiveShape) built.shape()).plan();
		int length = plan.length;

		// Scale: a figure inside the hall's door, one on the dais step, one at the mouth; trees on the land.
		GalleryProps.scaleFigure(level, plan.at(1, 1, -22), yawToward(plan, 1, -22, 1, -length));
		GalleryProps.scaleFigure(level, plan.at(-7, 1, -length + 10), yawToward(plan, -7, -length + 10, 0, -length));
		BlockPos mouth = plan.entrance();
		GalleryProps.scaleFigure(level, mouth.relative(facing, 2).relative(facing.getClockWise(), 3), yawToward(plan, 0, 0, 0, -1000));
		for (int[] t : new int[][] {{-30, 40}, {26, 52}, {-44, 120}, {40, 136}, {-12, 170}}) {
			BlockPos base = plan.at(t[0], 0, plan.tunnelF0 + way.exit() + t[1] - 30);
			int y = Gallery.ground(level, base.getX(), base.getZ());
			GalleryProps.tree(level, new BlockPos(base.getX(), y, base.getZ()), t[0] < 0 ? "minecraft:oak" : "minecraft:birch");
		}

		if (dark) {
			stageDark(level, plan);
		}

		List<Gallery.View> views = new ArrayList<>();
		BoundingBox hallAndCells = plan.hallInside().encapsulate(plan.cellsInside());
		if (!dark) {
			views.addAll(Gallery.standardViews(level, plan.box(), mouth, facing));
		}
		ArchivePlan.Tunnel open = plan.tunnels[0];
		int ti = Math.max(2, open.mouth() - 5);
		views.add(Gallery.View.looking("in_tunnel", local(plan, open.left() + 0.5, ArchivePlan.foot(ti) + 1.6, plan.tunnelF0 + ti),
				local(plan, open.left() + 0.5, ArchivePlan.foot(0) + 0.8, plan.tunnelF0), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("inside the way in, looking down toward the light below, true light"));
		int jOut = plan.rows - 1;
		int left = plan.cellLeft(open.column());
		int back = plan.cellBack(jOut);
		views.add(Gallery.View.looking("in_annex", local(plan, left + 3.5, 1.62, back + 6.2), local(plan, left + 3.5, 1.4, back - 3),
				Gallery.NOON, Gallery.Sky.CLEAR, false).noted("the first cell from the way in, true light"));
		views.add(longView(plan));
		views.add(Gallery.View.looking("in_hall", local(plan, 0, 1.62, -1.2), local(plan, 0, 5.5, -length), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted(dark ? "from the hall's door down the nave: the near half dark" : "from the hall's door down the nave, true light"));
		if (!dark) {
			views.add(Gallery.View.looking("in_hall_top", local(plan, -6, ArchivePlan.DECK + 2.62, -8.5), local(plan, 3, 4, -length + 10),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the gallery's rail by the first row, down the hall"));
			views.add(Gallery.View.looking("in_3", local(plan, 0, ArchivePlan.DAIS_HEIGHT + 1 + 1.62, -length + 3.5), local(plan, 0, 4.0, 0),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("the nave from the dais back toward the door, true light"));
			views.add(Gallery.View.looking("in_dais", local(plan, 0, ArchivePlan.DAIS_HEIGHT + 1 + 1.62, -length + 6.5),
					local(plan, 0, ArchivePlan.DAIS_HEIGHT + 2.5, -length - 1), Gallery.NOON, Gallery.Sky.CLEAR, false).noted("the records wall from the dais"));
			int s = plan.baySide;
			views.add(Gallery.View.looking("in_1", local(plan, -s * 3, 1.62, plan.bayF() + 1), local(plan, s * 14, 2.5, plan.bayF() - 0.5),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the nave into the taken aisle and its tunnel"));
			views.add(Gallery.View.looking("in_2", local(plan, s * 0.5, 1.62, plan.rowBack(1) - 1.5), local(plan, s * 12, 3.5, plan.rowBack(1) - 2.5),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("down an aisle: the shelves, mostly empty"));
			views.add(Gallery.View.looking("survey_hall", local(plan, 0, 1.62, -1.2), local(plan, 0, 5.5, -length), Gallery.NOON, Gallery.Sky.CLEAR, true)
					.noted("the hall from the door, night vision"));
		} else {
			views.add(Gallery.View.looking("in_dark", local(plan, 0, ArchivePlan.DAIS_HEIGHT + 1 + 1.62, -length + 3.5), local(plan, 0, 3.0, 0),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("the nave from the dais after the lights went: the far half black"));
			views.add(Gallery.View.looking("in_dark_2", local(plan, 2, 1.62, -length + 14), local(plan, -1, 2.5, -10), Gallery.NOON, Gallery.Sky.CLEAR, false)
					.noted("from the open floor before the dais toward the dark"));
		}

		List<Gallery.Frames> frames = new ArrayList<>();
		if (!dark) {
			Direction side = facing.getClockWise();
			BoundingBox cut = cutBox(plan, side);
			Gallery.View section = Gallery.sectionView(hallAndCells, side, 52);
			frames.add(new Gallery.Frames("section", 2, 100, section, l -> GalleryProps.cutaway(l, cut, side, plan.start.origin().getX()))
					.noted("cut through the nave's middle: the hall and the cells, night vision (f01 once the chunks are drawn again)"));
			BlockPos c = hallAndCells.getCenter();
			Vec3 at = new Vec3(c.getX() + 0.5, FLOOR + 4, c.getZ() + 0.5);
			Vec3 eye = at.add(side.getStepX() * 34.0 - facing.getStepX() * 10.0, 52, side.getStepZ() * 34.0 - facing.getStepZ() * 10.0);
			frames.add(new Gallery.Frames("aerial_cut", 1, 10, Gallery.View.looking("aerial_cut", eye, at, Gallery.NOON, Gallery.Sky.CLEAR, true), l -> {
			}).noted("the cut from above, night vision"));
		}
		return new Gallery.Placed(plan.box(), views, frames);
	}

	/** The cut for the section: everything right of the nave's middle over the hall and the cells, up to the sky. */
	static BoundingBox cutBox(ArchivePlan plan, Direction side) {
		return plan.frame.box(0, -4, -plan.length - 4, 58, 120, plan.tunnelF0 + 2);
	}

	/** Through three doorways in a line: the longest straight run of open doors through the cells. */
	static Gallery.View longView(ArchivePlan plan) {
		int bestLen = -1;
		int[] best = {0, 0, 1};
		for (int j = 0; j < plan.rows; j++) {
			for (int c = 0; c < plan.cols; c++) {
				int run = 0;
				while (c + run < plan.cols - 1 && plan.edgeOpen(c + run, j, true)) {
					run++;
				}
				if (run > bestLen) {
					bestLen = run;
					best = new int[] {c, j, 1};
				}
				run = 0;
				while (j + run < plan.rows - 1 && plan.edgeOpen(c, j + run, false)) {
					run++;
				}
				if (run > bestLen) {
					bestLen = run;
					best = new int[] {c, j, 0};
				}
			}
		}
		int c = best[0];
		int j = best[1];
		double left = plan.cellLeft(c);
		double back = plan.cellBack(j);
		Vec3 eye;
		Vec3 at;
		if (best[2] == 1) {
			eye = local(plan, left + 0.2, 1.62, back + 3);
			at = local(plan, left + 40, 1.5, back + 3);
		} else {
			eye = local(plan, left + 3, 1.62, back + 0.2);
			at = local(plan, left + 3, 1.5, back + 40);
		}
		return Gallery.View.looking("in_annex_long", eye, at, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("through " + bestLen + " doorways in a line, true light");
	}

	/** The yaw of a figure at (r, f) facing (toR, toF). */
	static float yawToward(ArchivePlan plan, int r, int f, int toR, int toF) {
		float[] look = Gallery.look(local(plan, r, 0, f), local(plan, toR, 0, toF));
		return look[0];
	}

	/** 17n's staging: the lights gone over the first half of the hall from its door, as 17b leaves them. */
	static void stageDark(ServerLevel level, ArchivePlan plan) {
		int rows = plan.gridRows() / 2;
		for (int k = 0; k < rows; k++) {
			for (BlockPos pos : plan.rowLights(k)) {
				GalleryProps.set(level, pos, Blocks.AIR.defaultBlockState());
			}
		}
		for (int c = 0; c < plan.cols; c++) {
			for (int j = 0; j < plan.rows; j++) {
				if (c != plan.doorColumn() || j != 0) {
					continue;
				}
				GalleryProps.set(level, plan.cellLight(c, j), Blocks.AIR.defaultBlockState());
			}
		}
	}
}
