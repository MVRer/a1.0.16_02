package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.world.live.NewScarPlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Dev only: the kit's exhibit {@code 00f}, the error sign's grammar ({@link Fault}) on gallery land, judged as every
 * place's sign is (D-110: {@link Gallery#signViews} at 64, 128 and 200, noon and midnight): a 32-wide lighting fault on
 * level grass among trees (the sign point), a 16-wide one on a hill's slope, a 16-wide cut on the hilltop with its own
 * fault, and a 16-wide one on a shallow sea (waterlogged). Registered with the demo kind (development only).
 */
final class FaultGallery {
	static final long SEED = 0xF0F0L;

	private FaultGallery() {
	}

	static void register() {
		Gallery.register(new Gallery.Exhibit("00f", "00", FaultGallery::place));
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int ox = origin.getX();
		int oz = origin.getZ();
		int g = origin.getY();
		load(level, new BoundingBox(ox - 230, g, oz - 80, ox + 120, g, oz + 90));
		// A hill to the east, a sea to the south-west, trees round the level square.
		BlockPos hillTop = new BlockPos(ox + 48, g, oz);
		GalleryProps.hill(level, hillTop, 64, 24);
		BoundingBox sea = new BoundingBox(ox - 72, g - 6, oz + 20, ox - 24, g - 1, oz + 68);
		GalleryProps.sea(level, sea, 4);
		for (int i = 0; i < 26; i++) {
			double a = i * Math.PI * 2 / 26;
			int r = 24 + (int) (Draw.unit(SEED, i, 0, 0) * 10);
			BlockPos base = new BlockPos(ox + (int) Math.round(Math.cos(a) * r), g, oz + (int) Math.round(Math.sin(a) * r));
			if (!sea.isInside(base.getX(), sea.maxY(), base.getZ()) && Math.abs(base.getX() - hillTop.getX()) > 40) {
				GalleryProps.tree(level, base, i % 3 == 0 ? "minecraft:birch" : "minecraft:oak");
			}
		}

		BoundingBox level0 = Fault.square(ox, oz, 32, g - 8, g + 48);
		BoundingBox slope = Fault.square(ox + 30, oz + 30, 16, g - 8, g + 48);
		BoundingBox top = Fault.square(hillTop.getX(), hillTop.getZ(), 16, g - 8, g + 48);
		BoundingBox water = Fault.square(ox - 48, oz + 44, 16, g - 8, g + 48);
		int cutY = Fault.sectionFloor(g + 20);
		BoundingBox box = new BoundingBox(ox - 80, g - 8, oz - 40, ox + 80, g + 48, oz + 80);
		BlockPos sign = new BlockPos(level0.getCenter().getX(), g, level0.getCenter().getZ());
		FaultDemo demo = new FaultDemo(box, List.of(level0, slope, top, water), top, cutY, sign, Direction.WEST);
		Live.Result built = Live.place(level, DemoKind.KIND, demo, Live.terrain(level), new Start(sign, Direction.WEST, 0, SEED));
		Live.settleLight(level, built.box());
		GalleryProps.scaleFigure(level, sign.offset(-18, 0, 4), 90.0F);

		List<Gallery.View> views = new ArrayList<>(Gallery.signViews(level, sign, Direction.WEST));
		Vec3 above = new Vec3(ox - 110.5, g + 110, oz - 90.5);
		Vec3 at = new Vec3(ox + 0.5, g, oz + 10.5);
		views.add(Gallery.View.looking("aerial", above, at, Gallery.NOON, Gallery.Sky.CLEAR, false).noted("from the air to the north-west, noon"));
		views.add(Gallery.View.looking("aerial_night", above, at, Gallery.NIGHT, Gallery.Sky.CLEAR, false).noted("the same at midnight"));
		views.add(Gallery.View.looking("near_night", Gallery.groundEye(level, ox - 30.5, oz - 20.5), new Vec3(ox + 0.5, g + 1, oz + 0.5), Gallery.NIGHT,
				Gallery.Sky.CLEAR, false).noted("30 out at midnight: the lit square's edge"));
		views.add(Gallery.View.looking("fog_night", Gallery.groundEye(level, ox - 127.5, oz + 0.5), new Vec3(ox + 0.5, g + 2, oz + 0.5), Gallery.NIGHT,
				Gallery.Sky.FOG_NIGHT, false).noted("128 out, a fog night at full"));
		return new Gallery.Placed(box, views, List.of());
	}

	/** Loads (generates) every chunk of the box, so the props write into made land. */
	private static void load(ServerLevel level, BoundingBox box) {
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}
}
