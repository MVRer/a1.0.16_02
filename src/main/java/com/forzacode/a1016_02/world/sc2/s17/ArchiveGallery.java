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
 * The archive's gallery exhibits (development only; docs/SELF_CREATED_2.md, feature 17, "Gallery"; round 3,
 * docs/SELF_CREATED_2_R3.md, R3-1): {@code 17} (the short hall), {@code 17l} (the long hall), {@code 17n} (after its
 * lights went, staged as 17b leaves them), {@code 17w} (the well), {@code 17t} (the well's top: the sign at 64, 128 and
 * 200 by day and night) and {@code 17n2} (version 2's 17n, to compare). Each is built by {@link Live#place} in the
 * gallery's deep flat world (ground at Y 40): version 3's hall floor at Y {@value #FLOOR3} (its ceiling 8 under the
 * ground), version 2's at Y {@value #FLOOR}; the halls under a hill the notch way opens on.
 */
final class ArchiveGallery {
	/** Version 2's hall floor in the gallery world. */
	static final int FLOOR = 2;
	/** Version 3's hall floor in the gallery world. */
	static final int FLOOR3 = -14;
	/** The exhibits' own seed, so each looks the same every run. */
	static final long SEED = 0x17A2C4E5L;

	private enum Kind {
		HALL, DARK, WELL, SIGN
	}

	private ArchiveGallery() {
	}

	static void register() {
		// The current version (FIX-R3-17: 4, the well's top a piece of land); 17t3 is version 3's sign, to compare.
		int now = ArchiveShape.VERSION;
		Gallery.register(new Gallery.Exhibit("17", "17", (level, origin, seed) -> place(level, origin, false, Kind.HALL, now)));
		Gallery.register(new Gallery.Exhibit("17l", "17", (level, origin, seed) -> place(level, origin, true, Kind.HALL, now)));
		Gallery.register(new Gallery.Exhibit("17n", "17", (level, origin, seed) -> place(level, origin, false, Kind.DARK, now)));
		Gallery.register(new Gallery.Exhibit("17w", "17", (level, origin, seed) -> place(level, origin, false, Kind.WELL, now)));
		Gallery.register(new Gallery.Exhibit("17t", "17", (level, origin, seed) -> place(level, origin, false, Kind.SIGN, now)));
		Gallery.register(new Gallery.Exhibit("17t3", "17", (level, origin, seed) -> place(level, origin, false, Kind.SIGN, 3)));
		Gallery.register(new Gallery.Exhibit("17n2", "17", (level, origin, seed) -> place(level, origin, false, Kind.DARK, 2)));
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

	/** A camera point in the well's coordinates (cell centres at whole numbers + 0.5), at world {@code y}. */
	static Vec3 inWell(Well well, double i, double y, double j) {
		BlockPos a = well.at(0, 0, 0);
		BlockPos ii = well.at(1, 0, 0);
		BlockPos jj = well.at(0, 0, 1);
		double x = a.getX() + 0.5 + (ii.getX() - a.getX()) * i + (jj.getX() - a.getX()) * j;
		double z = a.getZ() + 0.5 + (ii.getZ() - a.getZ()) * i + (jj.getZ() - a.getZ()) * j;
		return new Vec3(x, y, z);
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, boolean longHall, Kind kind, int version) {
		Direction facing = Direction.NORTH;
		int floor = version >= 3 ? FLOOR3 : FLOOR;
		int variant = version >= 3 ? ArchivePlan.variant3(longHall, 0.08, false, 0, level.getMinY()) : ArchivePlan.variant(longHall, 0.08);
		Start start = new Start(new BlockPos(origin.getX(), floor, origin.getZ()), facing, variant, SEED).withVersion(version);
		// Where it would open on the flat: the hill stands over that, so the way in opens on its slope (not for the sign's
		// exhibit: its views run out over the land in front).
		ArchivePlan flat = ArchivePlan.of(start, Live.terrain(level));
		ArchivePlan.Tunnel way = flat.tunnels[0];
		if (kind != Kind.SIGN) {
			BlockPos hillAt = flat.at(way.left(), 0, flat.tunnelF0 + way.mouth() - 34);
			GalleryProps.hill(level, new BlockPos(hillAt.getX(), origin.getY(), hillAt.getZ()), 72, 46);
		}
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
		Well well = plan.well;
		if (well != null) {
			// A figure on the well's sill and one on a gallery far down; trees round the well's top.
			if (well.lifted()) {
				// R3A-12: beside the crack, not in front of it (on the land or the talus there).
				BlockPos beside = well.at(Well.CORE_I1 + 3, 0, Well.SIDE + 1);
				GalleryProps.scaleFigure(level, new BlockPos(beside.getX(), Gallery.ground(level, beside.getX(), beside.getZ()), beside.getZ()), 180f);
			} else {
				GalleryProps.scaleFigure(level, well.at(Well.SIDE / 2 + 3, well.floorY(well.kGround) + 1, Well.SIDE + 2), 180f);
			}
			GalleryProps.scaleFigure(level, well.at(5, well.floorY(well.kLure) + 1, 14), 0f);
			for (int[] t : new int[][] {{-14, 40}, {44, 46}, {52, 8}, {-20, -10}, {10, 70}, {38, 96}}) {
				BlockPos base = well.at(t[0], 0, t[1]);
				int y = Gallery.ground(level, base.getX(), base.getZ());
				GalleryProps.tree(level, new BlockPos(base.getX(), y, base.getZ()), t[0] < 10 ? "minecraft:oak" : "minecraft:birch");
			}
		}

		if (kind == Kind.DARK) {
			stageDark(level, plan);
		}

		List<Gallery.View> views = new ArrayList<>();
		List<Gallery.Frames> frames = new ArrayList<>();
		switch (kind) {
			case HALL -> hallViews(level, plan, views, frames);
			case DARK -> darkViews(plan, views);
			case WELL -> wellViews(level, plan, views, frames);
			case SIGN -> signViews(level, plan, views);
		}
		return new Gallery.Placed(plan.box(), views, frames);
	}

	private static void hallViews(ServerLevel level, ArchivePlan plan, List<Gallery.View> views, List<Gallery.Frames> frames) {
		int length = plan.length;
		Direction facing = plan.frame.facing();
		boolean v3 = plan.version >= 3;
		views.addAll(Gallery.standardViews(level, plan.box(), plan.entrance(), facing));
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
				.noted("from the hall's door down the nave, true light"));
		if (v3) {
			views.add(Gallery.View.looking("in_up", local(plan, -1.5, 1.62, -14), local(plan, 1.0, ArchivePlan.HEIGHT3, -length + 4),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the nave's floor up the canyon of shelves to the dark, true light"));
			views.add(Gallery.View.looking("in_hall_top", local(plan, -3.6, ArchivePlan.TOP_DECK3 + 1.62, -9.5), local(plan, 2.5, 1, -34),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the top deck's rail, down past the floating lights to the floor 27 under"));
			int k = Math.max(1, plan.stackRows / 2);
			double fa = -10.5 - ArchivePlan.ROW_PITCH * k;
			views.add(Gallery.View.looking("in_tier", local(plan, 10.5, 18 + 1.62, fa), local(plan, -6, 12, fa - 1),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from a second-tier aisle at its ladder, out over the nave"));
		} else {
			views.add(Gallery.View.looking("in_hall_top", local(plan, -6, ArchivePlan.DECK + 2.62, -8.5), local(plan, 3, 4, -length + 10),
					Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the gallery's rail by the first row, down the hall"));
		}
		views.add(Gallery.View.looking("in_3", local(plan, 0, ArchivePlan.DAIS_HEIGHT + 1 + 1.62, -length + 3.5), local(plan, 0, v3 ? 9.0 : 4.0, 0),
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
		Direction side = facing.getClockWise();
		BoundingBox hallAndCells = plan.hallInside().encapsulate(plan.cellsInside());
		BoundingBox cut = cutBox(plan);
		Gallery.View section = Gallery.sectionView(hallAndCells, side, v3 ? 80 : 52);
		frames.add(new Gallery.Frames("section", 2, 100, section, l -> GalleryProps.cutaway(l, cut, side, plan.start.origin().getX()))
				.noted("cut through the nave's middle: the hall and the cells, night vision (f01 once the chunks are drawn again)"));
	}

	private static void darkViews(ArchivePlan plan, List<Gallery.View> views) {
		int length = plan.length;
		views.add(Gallery.View.looking("in_hall", local(plan, 0, 1.62, -1.2), local(plan, 0, 5.5, -length), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("from the hall's door down the nave: the near half dark"));
		views.add(Gallery.View.looking("in_dark", local(plan, 0, ArchivePlan.DAIS_HEIGHT + 1 + 1.62, -length + 3.5), local(plan, 0, 3.0, 0),
				Gallery.NOON, Gallery.Sky.CLEAR, false).noted("the nave from the dais after the lights went: the far half black"));
		views.add(Gallery.View.looking("in_dark_2", local(plan, 2, 1.62, -length + 14), local(plan, -1, 2.5, -10), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("from the open floor before the dais toward the dark"));
		if (plan.version >= 3) {
			views.add(Gallery.View.looking("in_dark_up", local(plan, 1.5, 1.62, -length + 12), local(plan, -1, 22, -6), Gallery.NOON, Gallery.Sky.CLEAR, false)
					.noted("from before the dais up and back: the floating lights gone over the near half"));
		}
	}

	private static void wellViews(ServerLevel level, ArchivePlan plan, List<Gallery.View> views, List<Gallery.Frames> frames) {
		Well well = plan.well;
		if (well == null) {
			return;
		}
		int ground = well.floorY(well.kGround);
		int pool = well.floorY(well.kMin);
		double c = Well.SIDE / 2.0;
		views.add(Gallery.View.looking("sill", inWell(well, c, ground + 1.62, Well.SIDE + 6), inWell(well, c, ground + 3, 6), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("on the apron, 6 out from the open face, looking in, noon"));
		if (well.lifted()) {
			// R3A-12: 1.7 over the sill's level 8 out (over the talus), the figure beside the crack.
			double eye = ground + 1 + Gallery.EYE;
			views.add(Gallery.View.looking("sill_night", inWell(well, c, eye, Well.SIDE + 8), inWell(well, c, ground + 3, 6), Gallery.NIGHT, Gallery.Sky.CLEAR,
					false).noted("1.7 over the sill's level 8 out from the crack (over the talus), looking in, midnight"));
		} else {
			views.add(Gallery.View.looking("sill_night", inWell(well, c, ground + 1.62, Well.SIDE + 6), inWell(well, c, ground + 3, 6), Gallery.NIGHT,
					Gallery.Sky.CLEAR, false).noted("on the apron, 6 out from the open face, looking in, midnight"));
		}
		views.add(Gallery.View.looking("in_1", inWell(well, c - 3, ground + 2.4, Well.VOID1 - 0.3), inWell(well, c + 1, pool, 10), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("leaning over the ground gallery's rail, down the void, true light"));
		views.add(Gallery.View.looking("in_1b", inWell(well, Well.IN0 + 0.6, ground + 1.62, Well.IN0 + 0.6), inWell(well, Well.IN1, pool + 12, Well.IN1),
				Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the ground gallery's back corner across and down the void, true light"));
		views.add(Gallery.View.looking("in_2", inWell(well, c - 0.5, pool + 0.4, c - 0.5), inWell(well, c, well.top, c + 3),
				Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the water's surface straight up the shaft, true light"));
		views.add(Gallery.View.looking("in_3", inWell(well, Well.IN0 + 0.6, well.floorY(well.kLure + 1) + 1.62, Well.IN0 + 1.5),
				inWell(well, Well.IN1 - 2, well.floorY(well.kLure) + 1, Well.IN1 - 4), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("from the gallery over the lit one, across and down to it, true light"));
		views.add(Gallery.View.looking("in_hall_door", inWell(well, Well.IN0 + 0.6, well.floorY(0) + 1.62, well.doorJ()), inWell(well, Well.IN1, well.floorY(0) - 20, well.doorJ() + 3),
				Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the corridor's mouth on the hall's gallery, into the void, true light"));
		views.add(Gallery.View.looking("survey_1", inWell(well, c - 3, ground + 2.4, Well.VOID1 - 0.3), inWell(well, c + 1, pool, 10), Gallery.NOON,
				Gallery.Sky.CLEAR, true).noted("in_1 with night vision"));
		// 17f staged: the glowstone gone from the top down to the ground gallery, seen from three galleries under it.
		Vec3 below = inWell(well, Well.VOID0 + 0.7, well.floorY(well.kGround - 3) + 2.4, c);
		frames.add(new Gallery.Frames("in_4", 2, 60, Gallery.View.looking("in_4", below,
				inWell(well, Well.VOID1 - 1, well.top, c + 2), Gallery.NOON, Gallery.Sky.CLEAR, false), l -> {
					for (int k = well.kTop; k >= well.kGround; k--) {
						for (BlockPos p : well.lights(k)) {
							GalleryProps.set(l, p, Blocks.AIR.defaultBlockState());
						}
					}
				}).noted("17f forced: the shaft's lights gone from the top down, looking up from three galleries under the land"));
		Direction side = plan.frame.facing();
		BoundingBox shaft = well.insideBox().inflatedBy(4);
		BlockPos mid = shaft.getCenter();
		int plane = side.getAxis() == Direction.Axis.X ? mid.getX() : mid.getZ();
		Gallery.View section = Gallery.sectionView(shaft, side.getClockWise(), 120);
		frames.add(new Gallery.Frames("section", 2, 100, section, l -> GalleryProps.cutaway(l, shaft, side.getClockWise(),
				side.getClockWise().getAxis() == Direction.Axis.X ? mid.getX() : mid.getZ())).noted("the well cut through its middle, night vision"));
		views.add(Gallery.View.looking("aerial_well", inWell(well, -40, well.top + 70, Well.SIDE + 50), inWell(well, c, ground, c), Gallery.NOON,
				Gallery.Sky.CLEAR, false).noted("from high over the land in front: the cut top and the shaft, noon"));
	}

	private static void signViews(ServerLevel level, ArchivePlan plan, List<Gallery.View> views) {
		Well well = plan.well;
		if (well == null) {
			return;
		}
		Direction facing = plan.frame.facing();
		views.addAll(Gallery.signViews(level, well.sign(), facing));
		Vec3 c = Vec3.atBottomCenterOf(well.sign());
		Vec3 spot = Gallery.out(c, facing, -45, 180);
		views.add(Gallery.View.looking("aerial", new Vec3(spot.x, well.top + 120, spot.z), c, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("180 out, 45 degrees aside, high, noon"));
		views.add(Gallery.View.looking("d200_fog", Gallery.groundEye(level, Gallery.out(c, facing, 0, 200).x, Gallery.out(c, facing, 0, 200).z),
				c.add(0, 2, 0), Gallery.NIGHT, Gallery.Sky.FOG_NIGHT, false).noted("200 out, a fog night"));
	}

	/** The cut for the section: everything right of the nave's middle over the hall and the cells, up to the sky. */
	static BoundingBox cutBox(ArchivePlan plan) {
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
