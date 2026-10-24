package com.forzacode.a1016_02.accident.sc2.s26;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.accident.sc2.HuntScene;
import com.forzacode.a1016_02.accident.sc2.Hunts;
import com.forzacode.a1016_02.accident.sc2.Prey;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's exhibit {@code 26} (docs/SELF_CREATED_2.md, 26; development only): a dark plain, a stand-in (the scale
 * figure) in the middle and six monsters (the most it takes) scattered 44 to 60 out, then {@link Call} itself around the stand-in (nothing
 * is ever set on anyone): from above at night with night vision ({@code f_1}, to judge the formation) they walk in from
 * every side to the ring they wait on, stand there facing the figure, and from 60 s come in; {@code f_2} is the same from
 * the figure's own place on a fog night, true light. View and frame names are camera words or numbers (D-103).
 */
final class S26Gallery {
	static final String ID = "26";
	/** Where the monsters start (from the middle), and what they are: six at most (R10-19), never a horde. */
	static final double[][] STARTS = {{0, -56}, {48, -26}, {44, 34}, {-6, 60}, {-48, 28}, {-42, -36}};
	static final List<EntityType<? extends Mob>> KINDS = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.SKELETON,
			EntityTypes.HUSK, EntityTypes.SPIDER);

	private S26Gallery() {
	}

	static Gallery.Exhibit exhibit() {
		return new Gallery.Exhibit(ID, "26", S26Gallery::place);
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();
		BoundingBox box = new BoundingBox(x0 - 72, y0 - 1, z0 - 72, x0 + 72, y0 + 16, z0 + 72);
		GalleryProps.clear(level, new BoundingBox(box.minX(), y0, box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
		BlockState coarse = Blocks.COARSE_DIRT.defaultBlockState();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				long h = (x * 341873128712L) ^ (z * 132897987541L);
				int r = (int) Math.floorMod(h >> 9, 100);
				if (r < 18) {
					GalleryProps.set(level, new BlockPos(x, y0 - 1, z), coarse);
				} else if (r >= 94) {
					GalleryProps.set(level, new BlockPos(x, y0, z), Blocks.SHORT_GRASS.defaultBlockState());
				}
			}
		}
		int[][] trees = {{-30, -20}, {24, -28}, {30, 26}, {-22, 34}, {-62, 50}, {60, -54}, {8, -68}, {-66, -6}};
		for (int[] t : trees) {
			GalleryProps.tree(level, new BlockPos(x0 + t[0], y0, z0 + t[1]), "minecraft:oak");
		}
		for (int i = 0; i < STARTS.length; i++) {
			GalleryProps.summon(level, KINDS.get(i), new BlockPos(x0 + (int) STARTS[i][0], y0, z0 + (int) STARTS[i][1]), 1);
		}

		Vec3 middle = Vec3.atBottomCenterOf(origin);
		Gallery.View ground = Gallery.View.looking("f_1", middle.add(0.0, Gallery.EYE, 0.0), middle.add(0.0, 1.0, 20.0), Gallery.NIGHT, Gallery.Sky.FOG_NIGHT,
				false);
		Gallery.View top = Gallery.View.looking("f_2", new Vec3(x0 + 0.5, y0 + 26.0, z0 + 22.0), middle, Gallery.NIGHT, Gallery.Sky.CLEAR, true);
		List<Gallery.View> views = new ArrayList<>();
		// Never a day view: the walking dead would burn before the frames.
		views.add(top.named("aerial").noted("the plain from above at night, night vision: the six out there"));
		List<Gallery.Frames> frames = new ArrayList<>();
		// First from the stand-in's own place (nothing stands there yet), then from above with a figure put there.
		frames.add(new Gallery.Frames("f_1", 40, 40, ground, l -> call(l, box, middle))
				.noted("the player's place on a fog night, true light, looking south: they come out of the fog to the ring, stand facing you, then come in"));
		frames.add(new Gallery.Frames("f_2", 40, 40, top, l -> {
			GalleryProps.scaleFigure(l, origin, 0.0F);
			call(l, box, middle);
		}).noted("from above at night, night vision to judge it: they walk in from every side, wait on the ring facing the figure, then come in"));
		return new Gallery.Placed(box, views, frames);
	}

	/** The monsters back at their starts, then the scene around a stand-in at the figure (one staged scene at a time). */
	private static void call(ServerLevel level, BoundingBox area, Vec3 middle) {
		List<Mob> callees = new ArrayList<>();
		for (EntityType<? extends Mob> type : List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.HUSK)) {
			callees.addAll(GalleryProps.staged(level, area, type));
		}
		for (int i = 0; i < callees.size(); i++) {
			double[] s = STARTS[i % STARTS.length];
			callees.get(i).snapTo(middle.x + s[0], middle.y, middle.z + s[1], 0.0F, 0.0F);
		}
		Prey prey = Prey.standIn(level, middle, 0.0F, 0.0F);
		Call call = new Call(UUID.nameUUIDFromBytes("gallery 26".getBytes(StandardCharsets.UTF_8)), level, HuntScene.Links.gallery(), -1, prey, callees,
				new Sc26Config(), () -> true, prey);
		Hunts.of(call.target()).ifPresent(old -> old.stop(true, "staged again"));
		Hunts.start(call);
		call.lifetime(20 * 90);
		call.begin();
	}
}
