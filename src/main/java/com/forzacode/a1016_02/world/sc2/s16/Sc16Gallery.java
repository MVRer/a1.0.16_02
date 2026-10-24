package com.forzacode.a1016_02.world.sc2.s16;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Start;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Feature 16's exhibits (dev only; docs/SELF_CREATED_2.md, "The gallery"): {@code 16} (base 95), {@code 16x} (127),
 * {@code 16s} (no way in), {@code 16l} (a light on top, for the night) and {@code 16d} (at sea, in a basin dug into the
 * flat). Each stands on the flat with its way in to the east (the far cameras look west, into the sunset), trees round
 * it for scale (bare inside the dead ring), the scale figure beside the way in. {@code 16} and {@code 16x} add the
 * inside (true light from the way in with one torch a player might have set down, and the same with night vision: the
 * rim, across the pit to its floor, up from the floor, up into the vault) and a {@code section}: a second one built
 * 480 to the north and cut open through its middle.
 */
final class Sc16Gallery {
	/** How far north the copy for the section stands. */
	static final int SECTION_AWAY = 480;

	private record Spec(String id, int size, boolean sealed, boolean light, boolean sea, long seed, boolean inside) {
	}

	private static final List<Spec> SPECS = List.of(
			new Spec("16", 1, false, false, false, 0x16A1L, true),
			new Spec("16x", 2, false, false, false, 0x16B2L, true),
			new Spec("16s", 1, true, false, false, 0x16C3L, false),
			new Spec("16l", 1, false, true, false, 0x16D4L, false),
			new Spec("16d", 1, true, false, true, 0x16E5L, false));

	private Sc16Gallery() {
	}

	static void register() {
		for (Spec spec : SPECS) {
			Gallery.register(new Gallery.Exhibit(spec.id(), "16", (level, origin, seed) -> place(level, origin, spec)));
		}
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, Spec spec) {
		Direction facing = Direction.EAST;
		int variant = PyramidShape.variant(spec.size(), spec.sealed(), spec.light(), spec.sea());
		int r = PyramidShape.halfWidth(spec.size());
		BlockPos base = origin;
		if (spec.sea()) {
			int basin = r + 70;
			int depth = 24;
			int top = origin.getY() - 1;
			GalleryProps.sea(level, new BoundingBox(origin.getX() - basin, top - depth, origin.getZ() - basin, origin.getX() + basin, top, origin.getZ() + basin),
					depth);
			base = origin.below(depth);
		}
		trees(level, base, r, facing, spec.sea());
		Sc2Kind kind = spec.sea() ? S16.SEA_KIND : S16.KIND;
		Live.Result built = Live.place(level, kind, new Start(base, facing, variant, spec.seed()));
		PyramidShape shape = (PyramidShape) built.shape();
		BlockPos entrance = shape.entrance();
		GalleryProps.scaleFigure(level, groundAt(level, shape.local(-2, 0, r + 2)), -90.0F);

		BoundingBox mass = new BoundingBox(base.getX() - r, base.getY() - 1, base.getZ() - r, base.getX() + r, base.getY() + r, base.getZ() + r);
		List<Gallery.View> views = new ArrayList<>(Gallery.standardViews(level, mass, entrance, facing));
		views.stream().filter(v -> v.name().equals("near")).findFirst().ifPresent(near -> views.add(near.named("near_fog")
				.at(Gallery.NIGHT, Gallery.Sky.FOG_NIGHT).noted("40 out from the entrance, a fog night at full")));
		if (spec.light()) {
			// The mod's night haze swallows 200 at Traces (a shaderpack's does not): the light from nearer too.
			views.stream().filter(v -> v.name().equals("mid")).findFirst().ifPresent(mid -> views.add(mid.named("mid_night").at(Gallery.NIGHT, Gallery.Sky.CLEAR)
					.noted("140 out, 30 degrees aside, midnight: the light on top")));
			views.stream().filter(v -> v.name().equals("near")).findFirst().ifPresent(near -> views.add(near.named("near_night")
					.at(Gallery.NIGHT, Gallery.Sky.CLEAR).noted("40 out from the entrance, midnight: the light on top")));
		}
		if (spec.inside()) {
			views.addAll(inside(shape));
			views.add(section(level, base, spec, kind, variant, facing));
		} else if (spec.sea()) {
			Vec3 eye = at(shape, 0.5, 1.7, r - 5);
			views.add(Gallery.View.looking("survey_1", eye, at(shape, 0.5, r / 3.0, -r / 2.0), Gallery.NOON, Gallery.Sky.CLEAR, true)
					.noted("inside the dry core, toward the far wall, night vision"));
		}
		return new Gallery.Placed(built.box(), views, List.of());
	}

	/** The inside: true light, and the same cameras with night vision. */
	private static List<Gallery.View> inside(PyramidShape shape) {
		int r = shape.halfWidth();
		double bottom = shape.pitBottomY() - shape.baseY();
		List<Gallery.View> views = new ArrayList<>();
		Vec3 mouth = at(shape, 0.5, 1.7, r - 5);
		Vec3 across = at(shape, 0.5, -6.0, -(r - 6));
		Gallery.View in1 = Gallery.View.looking("in_1", mouth, across, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("two steps in from the way in, on the rim's edge, true light: the daylight from behind on the floor, then nothing");
		Vec3 corner = at(shape, -(r - 4), 1.7, r - 4);
		Vec3 floor = at(shape, 0, bottom + 0.5, 0);
		Gallery.View in2 = Gallery.View.looking("in_2", corner, floor, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("from the rim's front corner across the pit to its floor, true light: the one light far down");
		// On the first step, 2.5 off the bottom's torch (R11-20: the camera stood on it).
		Vec3 low = at(shape, 0.5, bottom + 2.7, -2.5);
		Vec3 up = at(shape, 0.5, 1.0, r - 3);
		Gallery.View in3 = Gallery.View.looking("in_3", low, up, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("from the pit's floor up the steps toward the way in, true light");
		Vec3 vault = at(shape, 0.5, r - 3, -(r / 4.0));
		Gallery.View in4 = Gallery.View.looking("in_4", mouth, vault, Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("from the rim's edge by the way in, up into the vault, true light");
		views.add(in1);
		views.add(in1.named("survey_1").withSurvey(true).noted("as in_1, night vision"));
		views.add(in2);
		views.add(in2.named("survey_2").withSurvey(true).noted("as in_2, night vision"));
		views.add(in3);
		views.add(in3.named("survey_3").withSurvey(true).noted("as in_3, night vision"));
		views.add(in4.named("survey_4").withSurvey(true).noted("from the rim's edge by the way in, up into the vault, night vision"));
		if (!shape.tunnelCells().isEmpty()) {
			// R11-17: from the floor's back row into the dead end under the steps.
			Vec3 back = at(shape, 0.5, bottom + 1.6, -1.0);
			Vec3 end = at(shape, 0.5, bottom + 0.8, PyramidShape.TUNNEL_LENGTH + 1);
			Gallery.View in5 = Gallery.View.looking("in_5", back, end, Gallery.NOON, Gallery.Sky.CLEAR, false)
					.noted("from the pit's floor into the way on under the steps, true light");
			views.add(in5);
			views.add(in5.named("survey_5").withSurvey(true).noted("as in_5, night vision"));
		}
		return views;
	}

	/**
	 * A second one, {@value #SECTION_AWAY} north of the first, cut open through its middle (the plane through its centre,
	 * east to west, the way in's row kept): everything north of the plane is removed down to under the pit's floor and
	 * out past the camera, which looks at the cut face from the north with night vision.
	 */
	private static Gallery.View section(ServerLevel level, BlockPos base, Spec spec, Sc2Kind kind, int variant, Direction facing) {
		Live.Result built = Live.place(level, kind, new Start(base.offset(0, 0, -SECTION_AWAY), facing, variant, spec.seed()));
		PyramidShape shape = (PyramidShape) built.shape();
		int r = shape.halfWidth();
		int cx = shape.centerX();
		int cz = shape.centerZ();
		int bottom = (shape.hasPit() ? shape.pitBottomY() : shape.baseY()) - 5;
		int top = shape.baseY() + r + 3;
		double distance = Math.max(92.0, (top - bottom) * 0.78);
		BoundingBox cut = new BoundingBox(cx - r - 8, bottom, cz - (int) distance - 12, cx + r + 8, top, cz);
		GalleryProps.cutaway(level, cut, Direction.NORTH, cz);
		double mid = (bottom + top) / 2.0;
		return Gallery.View.looking("section", new Vec3(cx + 0.5, mid, cz - distance), new Vec3(cx + 0.5, mid, cz + 0.5), Gallery.NOON,
				Gallery.Sky.CLEAR, true).noted("a second one cut open through its middle, from the north, night vision: the mass, the core, the pit");
	}

	/** A ring of trees: bare ones inside the dead ring (the build strips them), live ones beyond; none on the cameras' lines. */
	private static void trees(ServerLevel level, BlockPos base, int r, Direction facing, boolean sea) {
		double front = Math.atan2(facing.getStepZ(), facing.getStepX());
		String[] kinds = {"minecraft:oak", "minecraft:fancy_oak", "minecraft:birch", "minecraft:oak", "minecraft:oak", "minecraft:birch"};
		double edge = r + 0.5 + PyramidShape.GROUNDS_FULL;
		// Bands: inside the dead ring (bare), on its edge (cut by it), and beyond (alive), each with its own count.
		double[][] bands = sea ? new double[][] {{r + 74, 60, 30}, {r + 120, 60, 24}}
				: new double[][] {{r + 4, edge - r - 7, 18}, {edge - 1, 3, 7}, {edge + 6, 26, 26}, {edge + 34, 60, 30}};
		int i = 0;
		for (double[] band : bands) {
			int count = (int) band[2];
			for (int k = 0; k < count; k++) {
				double jitter = Math.sin(k * 12.9898 + band[0] * 78.233) * 0.5;
				double turn = 2 * Math.PI * (k + 0.5 + 0.45 * jitter) / count;
				double off = Math.toDegrees(Math.IEEEremainder(turn, 2 * Math.PI));
				if (Math.abs(off) < 20 || Math.abs(off - 30) < 8 && band[0] > edge) {
					continue;
				}
				double radius = band[0] + band[1] * (0.5 + 0.5 * Math.sin(k * 4.1 + band[0]));
				int x = base.getX() + (int) Math.round(Math.cos(front + turn) * radius);
				int z = base.getZ() + (int) Math.round(Math.sin(front + turn) * radius);
				// Loads the tree's chunk and its neighbours first (vanilla's place refuses a feature near an unloaded one).
				ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, chunk, 1);
				for (int cx = chunk.x() - 1; cx <= chunk.x() + 1; cx++) {
					for (int cz = chunk.z() - 1; cz <= chunk.z() + 1; cz++) {
						level.getChunk(cx, cz);
					}
				}
				int y = Gallery.ground(level, x, z);
				GalleryProps.tree(level, new BlockPos(x, y, z), kinds[i++ % kinds.length]);
			}
		}
	}

	/** The first air cell over the ground at this column. */
	private static BlockPos groundAt(ServerLevel level, BlockPos pos) {
		return new BlockPos(pos.getX(), Gallery.ground(level, pos.getX(), pos.getZ()), pos.getZ());
	}

	/** A point in the way in's frame (the block's centre at {@code side}, {@code f}; {@code up} over the lowest course's bottom). */
	static Vec3 at(PyramidShape shape, double side, double up, double f) {
		Direction facing = shape.facing();
		Direction right = facing.getClockWise();
		return new Vec3(shape.centerX() + 0.5 + right.getStepX() * side + facing.getStepX() * f, shape.baseY() + up,
				shape.centerZ() + 0.5 + right.getStepZ() * side + facing.getStepZ() * f);
	}
}
