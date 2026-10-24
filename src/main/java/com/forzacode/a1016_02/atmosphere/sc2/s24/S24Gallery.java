package com.forzacode.a1016_02.atmosphere.sc2.s24;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery's exhibit {@code 24} (development only): a field at dusk, the scale figure (a stand-in for the player)
 * walking east, stopping, looking back, walking on, and a cow put behind it that 24's own follower walks after it
 * ({@link Follows}, with a staged {@link Followed}); and a hut at night with the stand-in inside, where the same follower
 * takes the cow to stand outside its window. Frames from the side of the walk, from behind the cow at the window, and
 * from inside looking out.
 */
final class S24Gallery {
	/** The figure's pace (blocks a tick) and its timeline (ticks from the start): walk, stand, look back, walk. */
	static final double PACE = 0.13;
	static final int WALK1 = 240;
	static final int STAND = 200;
	static final int LOOK_FROM = 60;
	static final int WALK2 = 300;
	/** The hut's middle, from the exhibit's origin. */
	static final int HUT_X = 0;
	static final int HUT_Z = 44;

	private static final List<Walker> WALKERS = new ArrayList<>();
	private static boolean ticking;

	private S24Gallery() {
	}

	static Gallery.Placed place(ServerLevel level, BlockPos origin, long seed) {
		int x0 = origin.getX();
		int y0 = origin.getY();
		int z0 = origin.getZ();
		BoundingBox box = new BoundingBox(x0 - 40, y0 - 1, z0 - 10, x0 + 30, y0 + 8, z0 + 52);
		GalleryProps.clear(level, new BoundingBox(x0 - 80, y0, z0 - 50, x0 + 70, y0 + 20, z0 + 90));
		GalleryProps.tree(level, new BlockPos(x0 - 20, y0, z0 - 9), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 4, y0, z0 - 13), "minecraft:birch");
		GalleryProps.tree(level, new BlockPos(x0 - 38, y0, z0 - 7), "minecraft:oak");
		GalleryProps.tree(level, new BlockPos(x0 + 18, y0, z0 - 6), "minecraft:oak");
		Vec3 from = new Vec3(x0 + 0.5 - 36, y0, z0 + 0.5);
		// R11-14: an animal the player knows again: a black sheep, the same one in the field and at the window.
		GalleryProps.summon(level, EntityTypes.SHEEP, BlockPos.containing(from.add(-30, 0, 4)), 1).forEach(s -> s.setColor(DyeColor.BLACK));
		BlockPos hut = new BlockPos(x0 + HUT_X, y0, z0 + HUT_Z);
		hut(level, hut);
		GalleryProps.tree(level, hut.offset(-9, 0, 8), "minecraft:oak");
		GalleryProps.summon(level, EntityTypes.SHEEP, hut.offset(18, 0, -10), 1).forEach(s -> s.setColor(DyeColor.BLACK));
		BoundingBox pen = new BoundingBox(x0 - 80, y0 - 4, z0 - 30, x0 + 70, y0 + 12, z0 + 20);
		BoundingBox yard = new BoundingBox(hut.getX() - 30, y0 - 4, hut.getZ() - 22, hut.getX() + 30, y0 + 12, hut.getZ() + 30);

		Gallery.View side = Gallery.View.looking("side", new Vec3(x0 + 0.5 - 22, y0 + 4.0, z0 + 0.5 + 30), new Vec3(x0 + 0.5 - 22, y0 + 0.5, z0 + 0.5),
				Gallery.DUSK, Gallery.Sky.DUSK, false).noted("from the side 30 off at dusk, a little above: the figure and, at the edge of its view, the sheep");
		// The window is in the hut's east wall; the sheep comes to stand 3 to 5 out from it, on its axis (R11-14).
		Vec3 pane = Vec3.atCenterOf(hut.offset(3, 1, 0));
		Gallery.View out = Gallery.View.looking("out_1", pane.add(14, 1.8, 2.5), pane, Gallery.NIGHT, Gallery.Sky.CLEAR, false)
				.noted("at night from behind the sheep, toward the lit window");
		Gallery.View in = Gallery.View.looking("in_1", Vec3.atBottomCenterOf(hut).add(-1.0, 1.6, 0.0), pane.add(8, -0.4, 0), Gallery.NIGHT,
				Gallery.Sky.CLEAR, false).noted("from inside the hut at night, out through the window: its face in the pane");
		List<Gallery.Frames> frames = List.of(
				new Gallery.Frames("side", 40, 20, side, l -> follow(l, pen, from)).noted(
						"a stand-in walks east, stops, looks back, walks on; a black sheep keeps after it at the edge of its view"),
				new Gallery.Frames("out_1", 16, 20, out, l -> window(l, yard, hut)).noted("the same sheep comes to the window and stands, facing in"),
				new Gallery.Frames("in_1", 3, 20, in, l -> { }).noted("the same from inside"));
		return new Gallery.Placed(box, List.of(), frames);
	}

	/** A 7 x 7 plank hut round {@code middle} (cobblestone corners, a plank roof), a door to the south, a pane east, a torch. */
	private static void hut(ServerLevel level, BlockPos middle) {
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				GalleryProps.set(level, middle.offset(dx, -1, dz), planks);
				boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
				boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
				for (int y = 0; y < 3; y++) {
					GalleryProps.set(level, middle.offset(dx, y, dz), wall ? corner ? cobble : planks : Blocks.AIR.defaultBlockState());
				}
				GalleryProps.set(level, middle.offset(dx, 3, dz), planks);
			}
		}
		GalleryProps.set(level, middle.offset(3, 1, 0), Blocks.GLASS_PANE.defaultBlockState());
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		GalleryProps.set(level, middle.offset(-2, 0, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		GalleryProps.set(level, middle.offset(-2, 1, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		GalleryProps.set(level, middle.offset(-2, 1, -2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
	}

	private static void follow(ServerLevel level, BoundingBox pen, Vec3 from) {
		Follows.LIVE.clear();
		WALKERS.forEach(w -> w.stand.discard());
		WALKERS.clear();
		List<Sheep> sheep = GalleryProps.staged(level, pen, EntityTypes.SHEEP);
		if (sheep.isEmpty()) {
			return;
		}
		Sheep one = sheep.getFirst();
		one.snapTo(from.x - 30, from.y, from.z + 4, 90.0F, 0.0F);
		ArmorStand stand = GalleryProps.scaleFigure(level, BlockPos.containing(from), -90.0F);
		Walker walker = new Walker(level, stand, from, level.getServer().getTickCount(), true);
		WALKERS.add(walker);
		ticking();
		Follows.LIVE.start(one, walker, 1.0, true);
	}

	private static void window(ServerLevel level, BoundingBox yard, BlockPos hut) {
		Follows.LIVE.clear();
		WALKERS.forEach(w -> w.stand.discard());
		WALKERS.clear();
		List<Sheep> sheep = GalleryProps.staged(level, yard, EntityTypes.SHEEP);
		if (sheep.isEmpty()) {
			return;
		}
		Sheep cow = sheep.getFirst();
		cow.snapTo(hut.getX() + 18.5, hut.getY(), hut.getZ() - 9.5, 0.0F, 0.0F);
		ArmorStand stand = GalleryProps.scaleFigure(level, hut.offset(-1, 0, -2), 90.0F);
		Walker inside = new Walker(level, stand, Vec3.atBottomCenterOf(hut.offset(-1, 0, -2)), level.getServer().getTickCount(), false);
		WALKERS.add(inside);
		ticking();
		Follows.LIVE.start(cow, inside, 1.0, true);
	}

	private static void ticking() {
		if (!ticking) {
			ticking = true;
			ServerTickEvents.END_SERVER_TICK.register(S24Gallery::tick);
		}
	}

	private static void tick(MinecraftServer server) {
		for (Walker w : WALKERS) {
			if (w.walks) {
				Vec3 at = w.position();
				w.stand.snapTo(at.x, at.y, at.z, w.yaw(), 0.0F);
				w.stand.setYHeadRot(w.yaw());
			}
		}
	}

	/** The staged walker: where it is at each tick of its timeline (or standing at home, indoors, its base under it). */
	static final class Walker implements Followed {
		final ServerLevel level;
		final ArmorStand stand;
		final Vec3 from;
		final long t0;
		final boolean walks;
		final UUID id = UUID.randomUUID();

		Walker(ServerLevel level, ArmorStand stand, Vec3 from, long t0, boolean walks) {
			this.level = level;
			this.stand = stand;
			this.from = from;
			this.t0 = t0;
			this.walks = walks;
		}

		private long t() {
			return level.getServer().getTickCount() - t0;
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public boolean present() {
			return true;
		}

		@Override
		public ServerLevel level() {
			return level;
		}

		@Override
		public Vec3 position() {
			if (!walks) {
				return from;
			}
			long t = t();
			double walked = Math.min(t, WALK1) * PACE + Math.max(0, Math.min(t - WALK1 - STAND, WALK2)) * PACE;
			return from.add(walked, 0, 0);
		}

		float yaw() {
			long t = t();
			return t > WALK1 + LOOK_FROM && t < WALK1 + STAND ? 90.0F : -90.0F;
		}

		@Override
		public Vec3 eye() {
			return position().add(0, 1.62, 0);
		}

		@Override
		public Vec3 look() {
			return yaw() > 0 ? new Vec3(-1, 0, 0) : new Vec3(1, 0, 0);
		}

		@Override
		public boolean outdoors() {
			return walks;
		}

		@Override
		public boolean sees(AABB box) {
			if (!walks) {
				return false;
			}
			Vec3 to = box.getCenter().subtract(eye());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			if (flat < 1.0E-6 || flat > 64) {
				return false;
			}
			Vec3 look = look();
			return (to.x * look.x + to.z * look.z) / flat >= Math.cos(Math.toRadians(35));
		}

		@Override
		public Optional<BlockPos> base() {
			return walks ? Optional.empty() : Optional.of(BlockPos.containing(from));
		}
	}
}
