package com.forzacode.a1016_02.world.sc2.s16.deep;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;
import com.forzacode.a1016_02.world.sc2.s16.S16;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Doorway;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.House;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Level;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Way;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * 33's exhibits (dev only; docs/SELF_CREATED_2_R3.md, "33", "Gallery"): {@code 33} (a 127 with its four levels),
 * {@code 33s} (a 63 with the rooms dropped) and {@code 33h} (a 191, its land raised so its depths fit the gallery's deep
 * flat). Views on the build as it generates ({@code in_1} to {@code in_6}, {@code in_8}); then frames that stage and cut:
 * {@code in_7} and {@code in_7_end} (33d and 33e forced: the first house's torches moved to the end house, the iron door
 * open), {@code in_9} (the first house dark from the pit's floor), and {@code plan_1} to {@code plan_4} (each level from
 * above, everything over its floor taken away). {@code 33} and {@code 33s} add a {@code section}: a copy built
 * {@value #SECTION_AWAY} north and cut along the facing through the pit (the end and his tunnel cut a step deeper).
 */
final class Sc33Gallery {
	static final int SECTION_AWAY = 480;
	/** A staged frame is shot this long after its staging (the client must have the changed blocks). */
	static final int STAGED = 80;

	private record Spec(String id, int size, long seed, boolean rooms, int raise, boolean section) {
	}

	private static final List<Spec> SPECS = List.of(
			new Spec("33", 2, 0x33E1L, true, 0, true),
			new Spec("33s", 0, 0x33E2L, false, 0, true),
			new Spec("33h", 3, 0x33E3L, true, 20, false));

	private Sc33Gallery() {
	}

	static void register() {
		for (Spec spec : SPECS) {
			Gallery.register(new Gallery.Exhibit(spec.id(), "33", (level, origin, seed) -> place(level, origin, spec)));
		}
	}

	private static Live.Result build(ServerLevel level, BlockPos base, Spec spec) {
		Start start = new Start(base, Direction.EAST, PyramidShape.variant(spec.size(), false, false, false, 1), spec.seed());
		return spec.rooms() ? Live.place(level, S16.KIND, start) : Depths.withoutRooms(() -> Live.place(level, S16.KIND, start));
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, Spec spec) {
		BlockPos base = new BlockPos(((origin.getX() >> 4) << 4) + 8, origin.getY(), ((origin.getZ() >> 4) << 4) + 8);
		if (spec.raise() > 0) {
			// The gallery's flat is too shallow for the 191's depths: its land raised (rock, three dirt, grass).
			int reach = PyramidShape.reach(spec.size());
			BoundingBox land = new BoundingBox(base.getX() - reach, base.getY() - 1, base.getZ() - reach, base.getX() + reach, base.getY() + spec.raise() - 1,
					base.getZ() + reach);
			GalleryProps.rock(level, new BoundingBox(land.minX(), land.minY(), land.minZ(), land.maxX(), land.maxY() - 4, land.maxZ()));
			GalleryProps.fill(level, new BoundingBox(land.minX(), land.maxY() - 3, land.minZ(), land.maxX(), land.maxY() - 1, land.maxZ()),
					Blocks.DIRT.defaultBlockState());
			GalleryProps.fill(level, new BoundingBox(land.minX(), land.maxY(), land.minZ(), land.maxX(), land.maxY(), land.maxZ()),
					Blocks.GRASS_BLOCK.defaultBlockState());
			base = base.above(spec.raise());
		}
		Live.Result built = build(level, base, spec);
		PyramidShape shape = (PyramidShape) built.shape();
		Depths depths = shape.depths();
		List<Gallery.View> views = new ArrayList<>();
		List<Gallery.Frames> frames = new ArrayList<>();
		if (!depths.present()) {
			views.add(Gallery.View.looking("none", at(shape, depths, 0.5, shape.pitBottomY() + 1.6, -1.2), at(shape, depths, 0.5, shape.pitBottomY() + 1.0,
					10), Gallery.NOON, Gallery.Sky.CLEAR, true).noted("no depths were made here (they did not fit)"));
			return new Gallery.Placed(built.box(), views, frames);
		}
		DepthsPlan plan = depths.plan();
		int p = plan.pitFeet();
		// in_1: from the pit's floor by its torch, down the tunnel to the door.
		Gallery.View in1 = Gallery.View.looking("in_1", at(shape, depths, 0.5, p + 1.62, -1.2), at(shape, depths, 0.4, p + 1.0, DepthsPlan.L1_FRONT),
				Gallery.NOON, Gallery.Sky.CLEAR, false).noted("on the pit's floor by its torch, looking down the tunnel at its end, true light: the door and its light");
		views.add(in1);
		views.add(in1.named("survey_1").withSurvey(true).noted("as in_1, night vision"));
		// in_2: inside the first house, its windows onto the rock.
		views.add(Gallery.View.looking("in_2", at(shape, depths, 1.6, p + 1.62, DepthsPlan.L1_FRONT + 1.3), at(shape, depths, -3.0, p + 1.4,
				DepthsPlan.L1_FRONT + 3.5), Gallery.NOON, Gallery.Sky.CLEAR, false).noted("inside the first house by its front door, true light: the house, its window onto stone, its back door"));
		views.add(Gallery.View.looking("in_2b", at(shape, depths, -1.5, p + 1.62, DepthsPlan.L1_FRONT + 4.8), at(shape, depths, 0.0, p + 1.0,
				DepthsPlan.L1_FRONT + 6.0), Gallery.NOON, Gallery.Sky.CLEAR, false).noted("inside the first house, at its back door, true light"));
		// in_3: the rooms through their panes.
		if (plan.rooms()) {
			int n = (plan.roomsGrid()[1] - 1) / 2 * DepthsPlan.PITCH;
			int feet2 = p + Level.L2.down;
			views.add(Gallery.View.looking("in_3", at(shape, depths, 1.0, feet2 + 1.62, n + 1.7), at(shape, depths, 1.0, feet2 + 1.3, -n - 2.0), Gallery.NOON,
					Gallery.Sky.CLEAR, false).noted("in the rooms' near house, looking through a pane down the row, true light: a lit room through a window, another past it"));
			views.add(Gallery.View.looking("in_3b", at(shape, depths, 5.5, feet2 + 1.62, n + 1.5), at(shape, depths, -9.0, feet2 + 1.0, n - 4.0), Gallery.NOON,
					Gallery.Sky.CLEAR, false).noted("where the stair comes into the rooms, true light: doors and panes across"));
		}
		// in_4: the warren, a stuffed doorway off the way.
		Doorway stuffed = null;
		for (Doorway d : plan.doorways()) {
			if (d.way() == Way.STUFFED && d.a() != plan.camp() && d.b() != plan.camp()) {
				stuffed = d;
				break;
			}
		}
		if (stuffed != null) {
			House from = plan.warrenWay().contains(stuffed.a()) || !plan.warrenWay().contains(stuffed.b()) ? plan.house(stuffed.a()) : plan.house(stuffed.b());
			views.add(facingDoorway("in_4", shape, depths, stuffed, from, 3.2, 1.2, true).noted("the warren, night vision: doors and a doorway stuffed shut"));
			views.add(facingDoorway("in_4t", shape, depths, stuffed, from, 3.2, 1.2, false).noted("as in_4, true light"));
		}
		// in_5: the camp through its broken doorway.
		if (plan.broken() >= 0) {
			Doorway broken = plan.doorways().get(plan.broken());
			House outside = plan.house(broken.a() == plan.camp() ? broken.b() : broken.a());
			views.add(facingDoorway("in_5", shape, depths, broken, outside, 3.4, 0.0, true).noted("the camp through its broken doorway, night vision"));
		}
		// in_6: the iron door from the stair's foot, a torch set down beside.
		Doorway iron = plan.doorway(Way.IRON);
		int feet4 = iron.feet();
		BlockPos torch = plan.pos(iron.s() + plan.mirror(), feet4 + 1, iron.f() - 3);
		GalleryProps.set(level, torch, Blocks.TORCH.defaultBlockState());
		views.add(Gallery.View.looking("in_6", at(shape, depths, iron.s(), feet4 + 1.62, iron.f() - 3.4), at(shape, depths, iron.s(), feet4 + 1.0, iron.f()),
				Gallery.NOON, Gallery.Sky.CLEAR, false).noted("at the foot of the last stair, a torch set down, true light: the iron door in its wall, nothing to open it"));
		// in_8: his tunnel from the end house's gap.
		House end = plan.l4();
		int[] span = plan.hisTunnelSpan();
		int[] sides = plan.hisTunnelSides();
		double mid = (sides[0] + sides[1]) / 2.0;
		views.add(Gallery.View.looking("in_8", at(shape, depths, mid, end.feet() + 1.62, end.f0() + 1.5), at(shape, depths, mid, end.feet() + 0.6, span[1]),
				Gallery.NOON, Gallery.Sky.CLEAR, true).noted("from inside the end house through its gone back wall down his tunnel, night vision: the cobblestone near the blind end"));
		if (spec.section()) {
			views.addAll(section(level, base, spec));
		}
		// Frames: 33d and 33e forced, then the cuts from above.
		Gallery.View in7 = Gallery.View.looking("in_7", at(shape, depths, iron.s(), feet4 + 1.62, iron.f() - 2.4), at(shape, depths, iron.s(), feet4 + 1.0,
				end.f0()), Gallery.NOON, Gallery.Sky.CLEAR, false);
		frames.add(new Gallery.Frames("in_7", 2, STAGED, in7, lvl -> stage(lvl, depths), "33d and 33e forced, true light: the iron door open, the end house's door with light round it"));
		Gallery.View in7end = Gallery.View.looking("in_7_end", at(shape, depths, end.s0() + 1.2, end.feet() + 1.62, end.f0() - 1.6), at(shape, depths, mid,
				end.feet() + 1.0, end.f0() + 6), Gallery.NOON, Gallery.Sky.CLEAR, false);
		frames.add(new Gallery.Frames("in_7_end", 2, STAGED, in7end, lvl -> openEndDoor(lvl, depths), "inside the end house lit as the first one was, true light: the gap and his tunnel"));
		frames.add(new Gallery.Frames("in_9", 2, STAGED, in1, lvl -> { }, "as in_1 after 33d, true light: the first house dark behind its door"));
		BoundingBox deep = depths.box().orElseThrow();
		for (Level lvl : plan.levels()) {
			int[] e = Depths.extent(plan, lvl);
			int feet = p + lvl.down;
			double cs = (e[0] + e[2]) / 2.0;
			double cf = (e[1] + e[3]) / 2.0;
			double span2 = Math.max(e[2] - e[0], e[3] - e[1]);
			Vec3 eye = at(shape, depths, cs, feet + 3 + span2 * 0.85 + 6, cf);
			Vec3 down = at(shape, depths, cs, feet, cf + 0.05);
			Gallery.View camera = Gallery.View.looking("plan_" + (lvl.ordinal() + 1), eye, down, Gallery.NOON, Gallery.Sky.CLEAR, false);
			int roof = feet + 3;
			int top = shape.baseY() + shape.height() + 2;
			frames.add(new Gallery.Frames("plan_" + (lvl.ordinal() + 1), 2, STAGED, camera,
					w -> GalleryProps.clear(w, new BoundingBox(deep.minX(), roof, deep.minZ(), deep.maxX(), top, deep.maxZ())),
					"level " + (lvl.ordinal() + 1) + " from above, everything over it taken away, noon"));
		}
		return new Gallery.Placed(built.box(), views, frames);
	}

	/** A camera in {@code from} looking at a doorway: {@code back} out from the wall, {@code aside} along it. */
	private static Gallery.View facingDoorway(String name, PyramidShape shape, Depths depths, Doorway d, House from, double back, double aside, boolean survey) {
		double out = d.acrossS() ? Math.signum(from.s0() - d.s()) : Math.signum(from.f0() - d.f());
		double s = d.acrossS() ? d.s() + out * back : d.s() + aside;
		double f = d.acrossS() ? d.f() + aside : d.f() + out * back;
		return Gallery.View.looking(name, at(shape, depths, s, d.feet() + 1.62, f), at(shape, depths, d.s(), d.feet() + 0.9, d.f()), Gallery.NOON, Gallery.Sky.CLEAR,
				survey);
	}

	/** 33d and 33e as they leave it: the rooms' torches taken, the first house's two in the end house, the iron door open. */
	static void stage(ServerLevel level, Depths depths) {
		DepthsPlan plan = depths.plan();
		for (House h : plan.houses(Level.L2)) {
			for (BlockPos t : depths.torches(h)) {
				GalleryProps.set(level, t, Blocks.AIR.defaultBlockState());
			}
		}
		List<BlockPos> from = depths.torches(plan.l1());
		List<BlockPos> to = depths.torches(plan.l4());
		for (int k = 0; k < 2; k++) {
			BlockState torch = level.getBlockState(from.get(k));
			GalleryProps.set(level, from.get(k), Blocks.AIR.defaultBlockState());
			GalleryProps.set(level, to.get(k), torch.isAir() ? depths.torchState(plan.l4(), to.get(k)) : torch);
		}
		open(level, depths, plan.doorway(Way.IRON));
	}

	private static void openEndDoor(ServerLevel level, Depths depths) {
		open(level, depths, depths.plan().doorway(Way.END_FRONT));
	}

	private static void open(ServerLevel level, Depths depths, Doorway d) {
		List<BlockPos> cells = depths.cells(d);
		for (BlockPos cell : cells) {
			BlockState state = level.getBlockState(cell);
			if (state.getBlock() instanceof DoorBlock) {
				GalleryProps.set(level, cell, state.setValue(DoorBlock.OPEN, true));
			}
		}
	}

	/**
	 * A copy {@value #SECTION_AWAY} north, cut along the facing through the pit's middle (everything on the side away from
	 * the end removed), and a step deeper at the end's level so the end house and his tunnel show; night vision.
	 */
	private static List<Gallery.View> section(ServerLevel level, BlockPos base, Spec spec) {
		Live.Result built = build(level, base.offset(0, 0, -SECTION_AWAY), spec);
		PyramidShape shape = (PyramidShape) built.shape();
		Depths depths = shape.depths();
		DepthsPlan plan = depths.plan();
		BoundingBox box = built.box();
		int m = plan.mirror();
		// The facing is east: the right is south. Remove the side away from the end (north for m = 1).
		Direction away = m > 0 ? Direction.NORTH : Direction.SOUTH;
		int cz = shape.centerZ();
		BoundingBox removed = GalleryProps.cutaway(level, box.inflatedBy(2), away, cz);
		House end = plan.l4();
		int deepPlane = plan.z(end.s0(), 0);
		int top = end.feet() + 4;
		BoundingBox step = m > 0 ? new BoundingBox(box.minX(), box.minY(), cz, box.maxX(), top, deepPlane - 1)
				: new BoundingBox(box.minX(), box.minY(), deepPlane + 1, box.maxX(), top, cz);
		GalleryProps.clear(level, step);
		int bottom = end.feet() - 4;
		int ceiling = shape.baseY() + shape.height() + 3;
		double distance = Math.max(110.0, (ceiling - bottom) * 0.8);
		double midY = (bottom + ceiling) / 2.0;
		double cx = shape.centerX() + 0.5;
		double eyeZ = m > 0 ? cz - distance : cz + distance;
		Gallery.View whole = Gallery.View.looking("section", new Vec3(cx, midY, eyeZ), new Vec3(cx, midY, cz + 0.5), Gallery.NOON, Gallery.Sky.CLEAR, true)
				.noted("a copy cut along the facing through the pit, the end a step deeper, night vision: the pyramid, its pit, the tunnel and the levels under it ("
						+ removed.getXSpan() + " wide cut)");
		// Closer: the depths alone, from the pit's floor down to his tunnel.
		double deepY = (shape.pitBottomY() + 4 + end.feet() - 2) / 2.0;
		double near = 58;
		Gallery.View deep = Gallery.View.looking("section_deep", new Vec3(cx, deepY, m > 0 ? cz - near : cz + near), new Vec3(cx, deepY, cz + 0.5), Gallery.NOON,
				Gallery.Sky.CLEAR, true).noted("as section, nearer: the house at the tunnel's end, the rooms, the warren and the end, night vision");
		return List.of(whole, deep);
	}

	/** A point in the depths' frame ({@code s}, {@code f} from the pit's middle, block centres; {@code y} absolute). */
	static Vec3 at(PyramidShape shape, Depths depths, double s, double y, double f) {
		Direction facing = shape.facing();
		Direction right = facing.getClockWise();
		return new Vec3(shape.centerX() + 0.5 + right.getStepX() * s + facing.getStepX() * f, y,
				shape.centerZ() + 0.5 + right.getStepZ() * s + facing.getStepZ() * f);
	}
}
