package com.forzacode.a1016_02.world.sc2.s18;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Feature 18's exhibits (dev only; docs/SELF_CREATED_2.md, "The gallery"): {@code 18} and {@code 18w} (with the high
 * opening), each in a mountain of the gallery's own (a massif with a second peak and a shoulder, a sheer side, rock with
 * the veins and blobs a real one has, grass and spruce low, bare stone and snow high, trees on the plain round it), so
 * the far views judge a mountain and the inside shows the stone a real one would. The way in looks east (the far
 * cameras look west, into the sunset), the scale figure stands beside its mouth, and the monsters the dark would hold
 * stand about inside (the gallery's world spawns none by itself). Views: the standard set; {@code survey_1} (from the
 * tunnel's inner end down the empty nave), {@code survey_2} (the square), {@code survey_3} (high behind the square, the
 * whole floor), {@code survey_4} (up into the stepped ceiling); the true-light views {@code in_1} (the tunnel's inner
 * end, a zombie at the edge of the light) and {@code in_2} (the square) are one-frame time-lapses that set down a
 * player's torch only after the outside views; {@code 18w} adds {@code in_3} and {@code survey_5} (the square of
 * daylight on the floor), {@code in_4} (straight up the shaft) and {@code far_2}, {@code far_3} (the hole from above);
 * {@code 18} adds a {@code section} (a second one 480 north, cut open along the way in).
 */
public final class Sc18Gallery {
	/** How far north the copy for the section stands. */
	static final int SECTION_AWAY = 480;

	private record Spec(String id, boolean window, long seed, boolean section) {
	}

	private static final List<Spec> SPECS = List.of(new Spec("18", false, 0x18A1L, true), new Spec("18w", true, 0x18B2L, false));

	private Sc18Gallery() {
	}

	static void register() {
		for (Spec spec : SPECS) {
			Gallery.register(new Gallery.Exhibit(spec.id(), "18", (level, origin, seed) -> place(level, origin, spec)));
		}
	}

	/**
	 * Exhibit {@code 18}'s mountain and the place in it at {@code origin} (the way in east), for another exhibit staged
	 * inside one (31's trunks, SC2-9). Dev only, as every exhibit.
	 */
	public static Live.Result eighteen(ServerLevel level, BlockPos origin) {
		return build(level, origin, Direction.EAST, SPECS.getFirst());
	}

	/** Builds the gallery's mountain at {@code origin} and the place in it; null if the shape found no way in. */
	static Live.Result build(ServerLevel level, BlockPos origin, Direction facing, Spec spec) {
		Mountain mountain = new Mountain(origin, facing, spec.seed());
		mountain.build(level);
		mountain.trees(level);
		Terrains.Frozen terrain = Live.terrain(level);
		int middle = terrain.ground(origin.getX(), origin.getZ());
		int[] grid = HollowSiting.grid(terrain, origin.getX(), origin.getZ(), middle);
		int floor = HollowSiting.floor(grid, facing, level.getSeaLevel());
		Sc18Config config = new Sc18Config();
		int variant = HollowShape.variant(spec.window(), true, config.shellTop, config.shellSide, config.pillarEvery, 11);
		Start start = new Start(new BlockPos(origin.getX(), floor, origin.getZ()), facing, variant, spec.seed());
		Live.Result built = Live.place(level, S18.KIND, start);
		HollowShape shape = (HollowShape) built.shape();
		A1016_02.LOGGER.info("[a1016] gallery 18: rise {}, base {}, valid {}, tallest {}, lane {}, length {}, opening {} {}",
				HollowSiting.rise(grid, floor, config.minRise), floor, shape.valid(), shape.maxTop() - floor, shape.lane(), shape.tunnelLength(), shape.hasWindow(),
				shape.windowCells().isEmpty() ? "-" : shape.windowCells().getFirst().toShortString() + " to " + shape.windowCells().getLast().toShortString());
		leaks(level, shape, built.box());
		return built;
	}

	/** Dev check: cells of the inside that daylight reaches, away from the way in and the opening's shaft (there should be none). */
	private static void leaks(ServerLevel level, HollowShape shape, BoundingBox box) {
		Live.settleLight(level, box);
		List<BlockPos> lit = new ArrayList<>();
		int looked = 0;
		BlockPos mouth = shape.entrance();
		BlockPos shaft = shape.windowFoot();
		for (int x = box.minX(); x <= box.maxX(); x += 3) {
			for (int z = box.minZ(); z <= box.maxZ(); z += 3) {
				long range = shape.column(x, z);
				if (range == com.forzacode.a1016_02.world.sc2.kit.Seal.NONE) {
					continue;
				}
				for (int y = com.forzacode.a1016_02.world.sc2.kit.Seal.lo(range); y <= com.forzacode.a1016_02.world.sc2.kit.Seal.hi(range); y += 2) {
					BlockPos pos = new BlockPos(x, y, z);
					looked++;
					if (pos.distManhattan(mouth) < 40 || shaft != null && Math.max(Math.abs(x - shaft.getX()), Math.abs(z - shaft.getZ())) < 18) {
						continue;
					}
					if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) > 0 && lit.size() < 12) {
						lit.add(pos);
					}
				}
			}
		}
		A1016_02.LOGGER.info("[a1016] gallery 18: {} cells looked at, daylight in {}: {}", looked, lit.size(), lit);
	}

	private static Gallery.Placed place(ServerLevel level, BlockPos origin, Spec spec) {
		Direction facing = Direction.EAST;
		Live.Result built = build(level, origin, facing, spec);
		HollowShape shape = (HollowShape) built.shape();
		int yf = shape.floorY();
		BlockPos entrance = shape.entrance();
		List<Gallery.View> views = new ArrayList<>();
		if (!shape.valid()) {
			views.addAll(Gallery.standardViews(level, built.box(), entrance, facing));
			return new Gallery.Placed(built.box(), views, List.of());
		}
		Direction right = facing.getClockWise();
		GalleryProps.scaleFigure(level, groundAt(level, entrance.relative(facing, 3).relative(right, -2)), -90.0F);
		monsters(level, shape);

		BoundingBox mass = built.box();
		views.addAll(Gallery.standardViews(level, mass, entrance, facing));
		views.addAll(inside(shape));
		if (spec.window() && shape.hasWindow()) {
			views.addAll(window(level, shape));
		}
		if (spec.section()) {
			views.add(section(level, origin, facing, spec));
		}
		return new Gallery.Placed(built.box(), views, torchlit(shape));
	}

	/** The inside: true light where a player would stand with their torch, and night vision for the shape of it. */
	private static List<Gallery.View> inside(HollowShape shape) {
		int yf = shape.floorY();
		List<Gallery.View> views = new ArrayList<>();
		List<BlockPos> mouth = shape.innerMouth();
		int lane = shape.lane();
		int inner = shape.along(mouth.getFirst().getX(), mouth.getFirst().getZ());
		Vec3 eye = point(shape, lane + 1.0, yf + 1 + Gallery.EYE, inner - 1.5);
		Vec3 ahead = point(shape, 0.5, yf + 12, -4);
		views.add(Gallery.View.looking("survey_1", eye, ahead, Gallery.NOON, Gallery.Sky.CLEAR, true)
				.noted("at the tunnel's inner end, night vision: the columns, the stepped ceiling, the square far off"));
		Vec3 wide = point(shape, -9.5, yf + 6, HollowShape.GROVE + 15);
		views.add(Gallery.View.looking("survey_2", wide, point(shape, 0.5, yf + 5, -2), Gallery.NOON, Gallery.Sky.CLEAR, true)
				.noted("the square from the aisle, night vision: the stems under the tallest part, the columns round it"));
		int highB = -36;
		double highY = Math.min(yf + 64, highTop(shape, highB) - 6);
		views.add(Gallery.View.looking("survey_3", point(shape, 0.5, highY, highB), point(shape, 0.5, yf + 2, inner * 0.6), Gallery.NOON, Gallery.Sky.CLEAR,
				true).noted("high behind the square looking toward the way in, night vision: the square far below, the empty nave, the columns' rows"));
		views.add(Gallery.View.looking("survey_4", point(shape, 4.5, yf + 1 + Gallery.EYE, 9.5), point(shape, -6, shape.maxTop() - 4, -24), Gallery.NOON,
				Gallery.Sky.CLEAR, true).noted("from the square up into the ceiling, night vision: the terraces, the columns widening into it"));
		return views;
	}

	/**
	 * The true-light views, as one-frame time-lapses so a player's torch is set down only after the outside views (it would
	 * glow at the end of the tunnel): {@code in_1} at the tunnel's inner end, a zombie standing at the edge of its light;
	 * {@code in_2} at the front of the square.
	 */
	private static List<Gallery.Frames> torchlit(HollowShape shape) {
		int yf = shape.floorY();
		List<BlockPos> mouth = shape.innerMouth();
		int lane = shape.lane();
		int inner = shape.along(mouth.getFirst().getX(), mouth.getFirst().getZ());
		Vec3 eye = point(shape, lane + 1.0, yf + 1 + Gallery.EYE, inner - 1.5);
		Vec3 ahead = point(shape, 0.5, yf + 12, -4);
		BlockPos torch = shape.at(lane, yf + 1, inner - 2);
		BlockPos lurker = shape.at(lane + 3, yf + 1, inner - 13);
		Gallery.View in1 = Gallery.View.looking("in_1", eye, ahead, Gallery.NOON, Gallery.Sky.CLEAR, false);
		Vec3 front = point(shape, 1.5, yf + 1 + Gallery.EYE, HollowShape.GROVE + 4.5);
		Vec3 stems = point(shape, 0.5, yf + 4, -1);
		BlockPos groveTorch = shape.at(1, yf + 1, HollowShape.GROVE + 2);
		Gallery.View in2 = Gallery.View.looking("in_2", front, stems, Gallery.NOON, Gallery.Sky.CLEAR, false);
		return List.of(new Gallery.Frames("in_1", 2, 30, in1, level -> {
			GalleryProps.set(level, torch, Blocks.TORCH.defaultBlockState());
			if (shape.taken(lurker.getX(), lurker.getY(), lurker.getZ())) {
				for (Mob zombie : GalleryProps.summon(level, EntityTypes.ZOMBIE, lurker, 1)) {
					zombie.setNoAi(true);
					float[] look = Gallery.look(Vec3.atBottomCenterOf(lurker), eye);
					zombie.snapTo(Vec3.atBottomCenterOf(lurker), look[0], 0.0F);
					zombie.setYHeadRot(look[0]);
					zombie.setYBodyRot(look[0]);
				}
			}
		}).noted("at the tunnel's inner end with a torch set down, true light: down the middle toward the square, into the black; one stands at the edge of the light"),
				new Gallery.Frames("in_2", 2, 30, in2, level -> GalleryProps.set(level, groveTorch, Blocks.TORCH.defaultBlockState()))
						.noted("at the front of the square beside a torch set down, true light: the bare stems, the dark past them"));
	}

	/** The highest cell taken over the middle line at {@code b} along. */
	private static int highTop(HollowShape shape, int b) {
		BlockPos c = shape.at(0, 0, b);
		long range = shape.column(c.getX(), c.getZ());
		return range == com.forzacode.a1016_02.world.sc2.kit.Seal.NONE ? shape.floorY() + 10 : com.forzacode.a1016_02.world.sc2.kit.Seal.hi(range);
	}

	/**
	 * {@code 18w}: the square of daylight on the floor from 24 off (true light and night vision), straight up the shaft
	 * from the pool's edge (true light), and the hole on the steep side from outside (noon and dusk).
	 */
	private static List<Gallery.View> window(ServerLevel level, HollowShape shape) {
		List<Gallery.View> views = new ArrayList<>();
		BlockPos foot = shape.windowFoot();
		BlockPos outer = shape.windowOuter();
		Direction downhill = shape.windowFacing();
		if (foot == null || outer == null || downhill == null) {
			return views;
		}
		Vec3 pool = Vec3.atBottomCenterOf(foot);
		// Stand 28 off on the open floor, the first way round that is clear of the square and of the columns.
		List<Vec3> ways = new ArrayList<>();
		for (int k = 0; k < 16; k++) {
			double angle = Math.PI * 2 * k / 16;
			ways.add(new Vec3(Math.cos(angle), 0, Math.sin(angle)));
		}
		Vec3 stand = null;
		BlockPos square = shape.groveCenter();
		for (Vec3 way : ways) {
			Vec3 at = pool.add(way.scale(28));
			BlockPos cell = BlockPos.containing(at.x, shape.floorY() + 1, at.z);
			if (Math.max(Math.abs(cell.getX() - square.getX()), Math.abs(cell.getZ() - square.getZ())) <= HollowShape.GROVE + 3) {
				continue;
			}
			boolean clear = true;
			for (double d = 2; d <= 28 && clear; d += 1) {
				Vec3 on = pool.add(way.scale(d));
				clear = shape.taken((int) Math.floor(on.x), shape.floorY() + 2, (int) Math.floor(on.z));
			}
			if (clear && shape.taken(cell.getX(), cell.getY(), cell.getZ()) && shape.taken(cell.getX(), cell.getY() + 2, cell.getZ())) {
				stand = at;
				break;
			}
		}
		if (stand != null) {
			Vec3 eye = new Vec3(stand.x, shape.floorY() + 1 + Gallery.EYE, stand.z);
			Gallery.View in3 = Gallery.View.looking("in_3", eye, pool.add(0, 6, 0), Gallery.NOON, Gallery.Sky.CLEAR, false)
					.noted("28 off on the floor, true light: the one square of daylight on the floor, falling from the shaft above");
			views.add(in3);
			views.add(in3.named("survey_5").withSurvey(true).noted("as in_3, night vision: the pool, the shaft over it"));
		}
		Vec3 edge = pool.add(downhill.getStepX() * 2.6, Gallery.EYE, downhill.getStepZ() * 2.6);
		views.add(Gallery.View.looking("in_4", edge, Vec3.atCenterOf(outer).add(0, 4, 0), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("at the pool's edge looking straight up, true light: a square of sky at the top of the black"));
		Vec3 farSpot = Vec3.atCenterOf(outer).add(downhill.getStepX() * 34.0, 30.0, downhill.getStepZ() * 34.0);
		Vec3 far = new Vec3(farSpot.x, Math.max(farSpot.y, Gallery.ground(level, (int) Math.floor(farSpot.x), (int) Math.floor(farSpot.z)) + 3.0), farSpot.z);
		views.add(Gallery.View.looking("far_2", far, Vec3.atCenterOf(outer), Gallery.NOON, Gallery.Sky.CLEAR, false)
				.noted("34 out from the hole and 30 over it (as from a peak nearby), noon: a square black hole in the mountain's side"));
		views.add(Gallery.View.looking("far_3", far, Vec3.atCenterOf(outer), Gallery.DUSK, Gallery.Sky.DUSK, false)
				.noted("the same at dusk"));
		return views;
	}

	/**
	 * A second one, {@value #SECTION_AWAY} north, cut open on the way in's own line: everything north of it removed, the
	 * camera north of the cut face with night vision.
	 */
	private static Gallery.View section(ServerLevel level, BlockPos origin, Direction facing, Spec spec) {
		BlockPos at = origin.offset(0, 0, -SECTION_AWAY);
		Live.Result built = build(level, at, facing, spec);
		HollowShape shape = (HollowShape) built.shape();
		int plane = shape.groveCenter().getZ();
		int bottom = shape.floorY() - 6;
		int top = shape.maxTop() + 24;
		BoundingBox cut = new BoundingBox(at.getX() - 175, bottom, plane - 175, at.getX() + 175, top, plane);
		GalleryProps.cutaway(level, cut, Direction.NORTH, plane);
		double mid = (bottom + top) / 2.0;
		double cx = at.getX() - 18;
		return Gallery.View.looking("section", new Vec3(cx, mid, plane - 150.0), new Vec3(cx, mid, plane + 0.5), Gallery.NOON, Gallery.Sky.CLEAR, true)
				.noted("a second one cut open along the way in, from the north, night vision: the mountain, its shell, the stepped inside, the columns, the square");
	}

	/** The monsters the dark would hold by now, standing about the floor (the gallery's world spawns none by itself). */
	private static void monsters(ServerLevel level, HollowShape shape) {
		int yf = shape.floorY();
		// No spider or enderman: their eyes glow in the dark, the cliché this wave keeps out of its frames.
		List<EntityType<? extends Mob>> kinds = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.CREEPER,
				EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER,
				EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SKELETON, EntityTypes.ZOMBIE);
		int[][] spots = {{-4, 30}, {11, 22}, {-17, 12}, {4, 44}, {24, 2}, {-26, -6}, {14, -18}, {-10, -30}, {30, -24}, {-36, 20}, {40, 16},
				{-3, -46}, {20, 38}, {-22, 40}};
		for (int k = 0; k < spots.length; k++) {
			BlockPos feet = shape.at(spots[k][0], yf + 1, spots[k][1]);
			if (!shape.taken(feet.getX(), feet.getY(), feet.getZ()) || !shape.taken(feet.getX(), feet.getY() + 2, feet.getZ())) {
				continue;
			}
			for (Mob mob : GalleryProps.summon(level, kinds.get(k % kinds.size()), feet, 1)) {
				mob.setYRot((float) (k * 47 % 360));
				mob.setYHeadRot(mob.getYRot());
			}
		}
	}

	/** A point in the place's frame: {@code a} across, {@code b} along from the square's middle (block centres at .5). */
	static Vec3 point(HollowShape shape, double a, double y, double b) {
		BlockPos origin = shape.at(0, 0, 0);
		Direction facing = shape.facing();
		Direction right = facing.getClockWise();
		return new Vec3(origin.getX() + 0.5 + right.getStepX() * (a - 0.5) + facing.getStepX() * (b - 0.5), y,
				origin.getZ() + 0.5 + right.getStepZ() * (a - 0.5) + facing.getStepZ() * (b - 0.5));
	}

	private static BlockPos groundAt(ServerLevel level, BlockPos pos) {
		return new BlockPos(pos.getX(), Gallery.ground(level, pos.getX(), pos.getZ()), pos.getZ());
	}

	// --- the gallery's mountain ---

	/**
	 * A mountain for the gallery: a main massif behind the middle (112 over the plain, its front foot about 70 out toward
	 * the facing, its back 150 out), a second peak behind and to the left, a shoulder in front to the right, a sheer side to
	 * the right (a cliff down to a bench), the top roughened; stone inside with granite, diorite and andesite in blobs, tuff
	 * low down, gravel and dirt in pockets and a few ores; grass on three dirt where it is gentle and low, bare stone where
	 * it is steep or high, snow on top. Dev only.
	 */
	static final class Mountain {
		private final int cx;
		private final int cz;
		private final int base;
		private final Direction facing;
		private final Direction right;
		private final long seed;
		private final int reach = 170;

		Mountain(BlockPos origin, Direction facing, long seed) {
			this.cx = origin.getX();
			this.cz = origin.getZ();
			this.base = origin.getY() - 1;
			this.facing = facing;
			this.right = facing.getClockWise();
			this.seed = seed;
		}

		private double u(int x, int z) {
			return (x - cx) * facing.getStepX() + (z - cz) * facing.getStepZ();
		}

		private double v(int x, int z) {
			return (x - cx) * right.getStepX() + (z - cz) * right.getStepZ();
		}

		private static double peak(double u, double v, double u0, double v0, double h, double front, double back, double side) {
			double du = u - u0;
			double r = du > 0 ? front : back;
			double d2 = (du / r) * (du / r) + ((v - v0) / side) * ((v - v0) / side);
			return d2 >= 1 ? 0 : h * Math.pow(1 - d2, 1.3);
		}

		/** Its height over the plain here (0: the plain). */
		double height(int x, int z) {
			double u = u(x, z);
			double v = v(x, z);
			double h = Math.max(peak(u, v, -40, 0, 104, 112, 150, 140), Math.max(peak(u, v, -70, -78, 94, 70, 80, 74), peak(u, v, 0, 62, 58, 58, 58, 58)));
			h = Math.max(h, peak(u, v, -20, -150, 62, 60, 70, 60));
			// Ridges and gullies running down it, then a rougher skin.
			double ridge = 1 - Math.abs(2 * noise(seed ^ 0x13L, x / 38.0, z / 38.0) - 1);
			h *= 0.84 + 0.26 * ridge;
			double s = Math.clamp(h / 24.0, 0.0, 1.0);
			h += s * (10 * (noise(seed, x / 29.0, z / 29.0) - 0.5) + 5 * (noise(seed ^ 0x11L, x / 13.0, z / 13.0) - 0.5)
					+ 2 * (noise(seed ^ 0x22L, x / 5.0, z / 5.0) - 0.5));
			// The sheer side: past a wandering line to the right the land drops to a bench a third as high.
			double line = 40 + 5 * Math.sin(u / 19.0) + 3 * Math.sin(u / 7.0 + 1.3);
			if (v > line) {
				double t = Math.clamp((v - line) / 2.0, 0.0, 1.0);
				double bench = h * 0.3 + 4 * (noise(seed ^ 0x33L, x / 9.0, z / 9.0) - 0.5);
				h = h + (bench - h) * t;
			}
			return Math.max(0, h);
		}

		/** Builds it (synchronously; a few seconds). */
		void build(ServerLevel level) {
			int x0 = cx - reach;
			int z0 = cz - reach;
			int side = 2 * reach + 1;
			int[] tops = new int[side * side];
			for (int i = 0; i < side; i++) {
				for (int j = 0; j < side; j++) {
					tops[i * side + j] = (int) Math.round(height(x0 + i, z0 + j));
				}
			}
			for (int cxChunk = x0 >> 4; cxChunk <= (x0 + side) >> 4; cxChunk++) {
				for (int czChunk = z0 >> 4; czChunk <= (z0 + side) >> 4; czChunk++) {
					level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, new ChunkPos(cxChunk, czChunk), 0);
					level.getChunk(cxChunk, czChunk);
				}
			}
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int i = 1; i < side - 1; i++) {
				for (int j = 1; j < side - 1; j++) {
					int h = tops[i * side + j];
					if (h <= 0) {
						continue;
					}
					int x = x0 + i;
					int z = z0 + j;
					double slope = Math.max(Math.abs(tops[(i + 1) * side + j] - tops[(i - 1) * side + j]), Math.abs(tops[i * side + j + 1] - tops[i * side + j - 1]))
							/ 2.0;
					int top = base + h;
					BlockState skin = skin(x, z, h, slope);
					int soil = skin.is(Blocks.GRASS_BLOCK) || skin.is(Blocks.COARSE_DIRT) || skin.is(Blocks.PODZOL) ? 3 : skin.is(Blocks.SNOW_BLOCK) ? 2 : 0;
					for (int y = base; y <= top; y++) {
						BlockState state;
						if (y == top) {
							state = skin;
						} else if (top - y <= soil) {
							state = skin.is(Blocks.SNOW_BLOCK) ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
						} else {
							state = rock(x, y, z);
						}
						GalleryProps.set(level, at.set(x, y, z), state);
					}
					if (skin.is(Blocks.SNOW_BLOCK) && slope < 1.0 && Draw01.unit(seed, x, 9, z) < 0.6 || h > 80 && skin.is(Blocks.STONE) && slope < 1.3
							&& Draw01.unit(seed, x, 10, z) < 0.25) {
						GalleryProps.set(level, at.set(x, top + 1, z), Blocks.SNOW.defaultBlockState());
					}
				}
			}
		}

		/** The top block: grass low and gentle, bare stone steep or high with gravel runs, snow on the gentle top. */
		private BlockState skin(int x, int z, int h, double slope) {
			double patch = noise(seed ^ 0x44L, x / 7.0, z / 7.0);
			if (slope > 1.5) {
				return patch > 0.72 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
			}
			if (h > 86) {
				return slope < 1.2 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
			}
			if (h > 60) {
				if (patch > 0.55 && slope < 1.0) {
					return Blocks.GRASS_BLOCK.defaultBlockState();
				}
				return patch < 0.12 ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState();
			}
			if (slope > 1.25) {
				return patch < 0.4 ? Blocks.STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
			}
			return Blocks.GRASS_BLOCK.defaultBlockState();
		}

		/** The rock inside: stone with blobs, low tuff, pockets and a few ores. */
		private BlockState rock(int x, int y, int z) {
			if (noise3(seed ^ 0x55L, x / 4.5, y / 4.0, z / 4.5) > 0.86) {
				return Blocks.GRAVEL.defaultBlockState();
			}
			if (noise3(seed ^ 0x66L, x / 5.0, y / 5.0, z / 5.0) > 0.88) {
				return Blocks.DIRT.defaultBlockState();
			}
			double blob = noise3(seed ^ 0x77L, x / 9.0, y / 7.0, z / 9.0);
			if (blob > 0.79) {
				return Blocks.GRANITE.defaultBlockState();
			}
			if (blob < 0.17) {
				return Blocks.DIORITE.defaultBlockState();
			}
			if (noise3(seed ^ 0x88L, x / 11.0, y / 6.0, z / 11.0) > 0.8) {
				return Blocks.ANDESITE.defaultBlockState();
			}
			double ore = Draw01.unit(seed ^ 0xAAL, x, y, z);
			if (ore < 1.0 / 110) {
				return Blocks.COAL_ORE.defaultBlockState();
			}
			if (ore < 1.0 / 110 + 1.0 / 380) {
				return Blocks.IRON_ORE.defaultBlockState();
			}
			if (ore < 1.0 / 110 + 1.0 / 380 + 1.0 / 600) {
				return Blocks.COPPER_ORE.defaultBlockState();
			}
			return Blocks.STONE.defaultBlockState();
		}

		/**
		 * Trees: spruce on its gentle lower slopes, oak, birch and spruce on the plain round it, none in the far cameras'
		 * lines (straight out in front, and 30 degrees aside) nor in front of the sheer side's camera.
		 */
		void trees(ServerLevel level) {
			int placed = 0;
			for (int x = cx - reach; x <= cx + reach; x += 6) {
				for (int z = cz - reach; z <= cz + reach; z += 6) {
					int tx = x + (int) Math.floor(Draw01.unit(seed, x, 1, z) * 5);
					int tz = z + (int) Math.floor(Draw01.unit(seed, x, 2, z) * 5);
					double u = u(tx, tz);
					double v = v(tx, tz);
					double h = height(tx, tz);
					double r = Math.sqrt(u * u + v * v);
					String kind;
					if (h > 2) {
						if (h > 64 || Draw01.unit(seed, tx, 3, tz) > (h < 44 ? 0.8 : 0.4)) {
							continue;
						}
						double slope = Math.abs(height(tx + 2, tz) - height(tx - 2, tz)) / 4.0 + Math.abs(height(tx, tz + 2) - height(tx, tz - 2)) / 4.0;
						if (slope > 1.35) {
							continue;
						}
						// The bench under the sheer side stays open in front of it (its camera looks up at the cliff).
						if (v > 40 && Math.abs(u) < 50) {
							continue;
						}
						kind = "minecraft:spruce";
					} else {
						if (r > reach || Draw01.unit(seed, tx, 4, tz) > 0.42) {
							continue;
						}
						// The far cameras' lines: straight out in front, 30 degrees to the right, and the sheer side's.
						double bearing = Math.toDegrees(Math.atan2(v, u));
						if (u > 0 && Math.abs(v) < 22 + u * 0.12 || Math.abs(bearing - 30) < 9 || v > 60 && Math.abs(u) < 40) {
							continue;
						}
						double pick = Draw01.unit(seed, tx, 5, tz);
						kind = pick < 0.45 ? "minecraft:oak" : pick < 0.7 ? "minecraft:birch" : pick < 0.85 ? "minecraft:fancy_oak" : "minecraft:spruce";
					}
					ChunkPos chunk = new ChunkPos(tx >> 4, tz >> 4);
					level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, chunk, 1);
					for (int a = chunk.x() - 1; a <= chunk.x() + 1; a++) {
						for (int b = chunk.z() - 1; b <= chunk.z() + 1; b++) {
							level.getChunk(a, b);
						}
					}
					int y = Gallery.ground(level, tx, tz);
					GalleryProps.tree(level, new BlockPos(tx, y, tz), kind);
					placed++;
				}
			}
			A1016_02.LOGGER.debug("[a1016] gallery 18: {} trees", placed);
		}

		private static double noise(long seed, double x, double y) {
			int x0 = (int) Math.floor(x);
			int y0 = (int) Math.floor(y);
			double fx = x - x0;
			double fy = y - y0;
			double sx = fx * fx * (3 - 2 * fx);
			double sy = fy * fy * (3 - 2 * fy);
			double a = Hash.unit(Hash.of(seed, x0, y0));
			double b = Hash.unit(Hash.of(seed, x0 + 1, y0));
			double c = Hash.unit(Hash.of(seed, x0, y0 + 1));
			double e = Hash.unit(Hash.of(seed, x0 + 1, y0 + 1));
			double top = a + (b - a) * sx;
			double bottom = c + (e - c) * sx;
			return top + (bottom - top) * sy;
		}

		private static double noise3(long seed, double x, double y, double z) {
			int z0 = (int) Math.floor(z);
			double fz = z - z0;
			double sz = fz * fz * (3 - 2 * fz);
			double a = noise(Hash.of(seed, z0), x, y);
			double b = noise(Hash.of(seed, z0 + 1), x, y);
			return a + (b - a) * sz;
		}
	}

	/** Per-cell randomness for the props (the kit's, by another name so the prop reads plainly). */
	private static final class Draw01 {
		static double unit(long seed, int x, int y, int z) {
			return com.forzacode.a1016_02.world.sc2.kit.Draw.unit(seed, x, y, z);
		}
	}
}
