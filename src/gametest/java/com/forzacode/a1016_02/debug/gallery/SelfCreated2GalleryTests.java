package com.forzacode.a1016_02.debug.gallery;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CommandHooks;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.core.VerifyScSpoilerTests;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-0c, the gallery (docs/SELF_CREATED_2.md, "The gallery"): exhibit {@code 00} places and every camera of it is in
 * loaded air; the blueprint renders see it; the PNG writer round-trips; {@code Gallery.register} does nothing outside a
 * development environment; the {@code /a1016 sc g} literals pass the spoiler words. And Fallback B: with
 * {@code -Da1016_02.gallery=<ids>} ({@code ./gradlew runGalleryPlans -Pgallery=<ids>}) the blueprint renders of those
 * exhibits are written to {@code build/gallery/<id>/}; without it that test passes at once. The placing tests run in a
 * test environment of their own ({@code a1016_02:debug/gallery}), far from every other test's ground.
 */
public class SelfCreated2GalleryTests {
	private static final String ENV = "a1016_02:debug/gallery";
	/** Far from the test grid: the exhibits' cameras reach 200 blocks out; a spot of its own (40 000 is shared by other tests). */
	private static final int FAR = -72_000;

	@GameTest(environment = ENV, maxTicks = 200)
	public void exhibit00PlacesAndEveryCameraIsInLoadedAir(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = new BlockPos(FAR, Gallery.ground(level, FAR, FAR), FAR);
		Gallery.Placed placed = GalleryRun.place(level, Gallery.get("00").orElseThrow(), origin);
		try {
			BoundingBox box = placed.box();
			helper.assertTrue(box.isInside(origin), "the box " + box + " does not hold the origin " + origin.toShortString());
			helper.assertTrue(placed.views().size() >= 11, "00 has " + placed.views().size() + " cameras");
			List<String> names = new ArrayList<>(placed.views().stream().map(Gallery.View::name).toList());
			for (String standard : List.of("far_day", "far_dusk", "far_night", "far_fog", "mid", "near", "mouth", "aerial", "in_1", "section")) {
				helper.assertTrue(names.contains(standard), "00 has no " + standard + ": " + names);
			}
			List<Gallery.View> cameras = new ArrayList<>(placed.views());
			placed.frames().forEach(f -> cameras.add(f.camera()));
			for (Gallery.View view : cameras) {
				BlockPos eye = BlockPos.containing(view.eye());
				helper.assertTrue(level.isLoaded(eye), view.name() + "'s eye " + eye.toShortString() + " is not in a loaded chunk");
				helper.assertTrue(!level.isOutsideBuildHeight(eye), view.name() + "'s eye " + eye.toShortString() + " is outside the world");
				BlockState at = level.getBlockState(eye);
				helper.assertTrue(at.getCollisionShape(level, eye).isEmpty(), view.name() + "'s eye " + eye.toShortString() + " is inside " + at);
			}
			Gallery.View far = placed.views().getFirst();
			helper.assertTrue(far.name().equals("far_day") && Math.abs(horizontal(far.eye(), Vec3.atBottomCenterOf(box.getCenter())) - 200) < 2,
					"far_day is not 200 out: " + far.eye());
			helper.assertTrue(placed.frames().size() == 1 && placed.frames().getFirst().count() == 12, "00's time-lapse: " + placed.frames());
			AABB area = AABB.of(box).inflate(2);
			helper.assertTrue(level.getEntitiesOfClass(Sheep.class, area, s -> true).size() == Exhibit00.SHEEP, "not six sheep");
			helper.assertTrue(level.getEntitiesOfClass(ArmorStand.class, area, s -> true).size() == 1, "no scale figure");
			// The plan sees the hut's roof at its centre; the north-south slice through the hut shows its door gap and floor.
			List<Png.Image> renders = Blueprint.render(level, box);
			helper.assertTrue(renders.size() == Blueprint.NAMES.size(), renders.size() + " renders");
			Png.Image top = renders.getFirst();
			int u = origin.getX() - box.minX();
			int v = origin.getZ() - box.minZ();
			helper.assertTrue(top.width() == box.getXSpan() && top.height() == box.getZSpan(), "the plan is " + top.width() + " x " + top.height());
			BlockPos roof = origin.above(5);
			helper.assertTrue(level.getBlockState(roof).getMapColor(level, roof) == MapColor.WOOD, "no plank roof at " + roof.toShortString());
			int expected = Blueprint.colour(level.getBlockState(roof), level, roof, (double) (box.maxY() - roof.getY()) / (box.getYSpan() - 1));
			helper.assertTrue(top.at(u, v) == expected, "the plan's centre is not the plank roof: " + Integer.toHexString(top.at(u, v)) + ", not "
					+ Integer.toHexString(expected));
			// The box's corner column is open down to the ground, its bottom layer: the deepest shade.
			BlockPos corner = new BlockPos(box.minX(), box.minY(), box.minZ());
			helper.assertTrue(top.at(0, 0) == Blueprint.colour(level.getBlockState(corner), level, corner, 1.0), "the plan's corner is not the ground");
			helper.assertTrue(Arrays.stream(renders.get(5).argb()).anyMatch(p -> (p >>> 24) == 0xFF), "the north-south slice is empty");
		} finally {
			GalleryProps.clear(level, placed.box().inflatedBy(2));
		}
		helper.succeed();
	}

	/**
	 * The staging's walk, as {@code 00} stages it: six summoned sheep, found again by their tag, go round a 9 x 9 block
	 * on a loop 7 out by their own navigation, nose to tail, each at least a third of the way round in 200 ticks (half in
	 * practice), never back the way it came.
	 */
	@GameTest(environment = ENV, maxTicks = 500)
	public void aStagedWalkTakesItsMobsRoundTheLoop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// The middle of a chunk, so the loop (7 out) and its mobs stay in that one chunk.
		int x = FAR + 2000 + 8;
		int z = FAR + 8;
		BlockPos at = new BlockPos(x, Gallery.ground(level, x, z), z);
		BoundingBox area = new BoundingBox(at).inflatedBy(16);
		// Nobody stands there: the chunks are forced so their mobs tick (as the camera's presence does in a gallery run),
		// and the test waits for that in real time (a test server's ticks race the chunk system; ARCHITECTURE).
		forced(level, at, true);
		long until = Util.getMillis() + 30_000L;
		level.getServer().managedBlock(() -> level.isPositionEntityTicking(at) || Util.getMillis() > until);
		helper.assertTrue(level.isPositionEntityTicking(at), "the forced chunks did not tick within 30 s");
		GalleryProps.clear(level, new BoundingBox(at.getX() - 8, at.getY(), at.getZ() - 8, at.getX() + 8, at.getY() + 4, at.getZ() + 8));
		BoundingBox block = new BoundingBox(at.getX() - 4, at.getY(), at.getZ() - 4, at.getX() + 4, at.getY() + 3, at.getZ() + 4);
		GalleryProps.fill(level, block, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
		List<Sheep> summoned = GalleryProps.summon(level, net.minecraft.world.entity.EntityTypes.SHEEP, at.above(4), 6);
		List<Vec3> loop = GalleryProps.rectangle(at.getX() - 7 + 0.5, at.getZ() - 7 + 0.5, at.getX() + 7 + 0.5, at.getZ() + 7 + 0.5, at.getY(), 2.0);
		Vec3 centre = Vec3.atBottomCenterOf(at);
		List<Sheep> sheep = new ArrayList<>();
		double[] lastAngle = new double[6];
		double[] turned = new double[6];
		long[] walkingSince = {-1};
		// This walk's own stop: the other tests of the batch run beside it, so nothing here ends every walk.
		Runnable[] stop = {() -> { }};
		// A forced chunk's mobs can be looked up once its entities are loaded and ticking: wait for that, then walk.
		helper.onEachTick(() -> {
			long now = helper.getTick();
			if (walkingSince[0] < 0) {
				List<Sheep> found = GalleryProps.staged(level, area, net.minecraft.world.entity.EntityTypes.SHEEP);
				if (found.size() < summoned.size() || !level.isPositionEntityTicking(at)) {
					helper.assertTrue(now < 150, "staged finds " + found.size() + " of " + summoned.size() + " after " + now + " ticks (ticking: "
							+ level.isPositionEntityTicking(at) + ")");
					return;
				}
				sheep.addAll(found);
				for (int i = 0; i < sheep.size(); i++) {
					Vec3 spot = loop.get(i * loop.size() / sheep.size());
					sheep.get(i).snapTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
					lastAngle[i] = Math.atan2(spot.z - centre.z, spot.x - centre.x);
				}
				stop[0] = GalleryProps.walk(sheep, loop, 1.0, 400, true);
				walkingSince[0] = now;
				return;
			}
			// How far round each has gone (clockwise from above is a growing angle: z points south).
			for (int i = 0; i < sheep.size(); i++) {
				Vec3 p = sheep.get(i).position();
				double angle = Math.atan2(p.z - centre.z, p.x - centre.x);
				turned[i] += Mth.wrapDegrees(Math.toDegrees(angle - lastAngle[i]));
				lastAngle[i] = angle;
			}
			if (now - walkingSince[0] == 200) {
				try {
					for (int i = 0; i < sheep.size(); i++) {
						helper.assertTrue(turned[i] > 120.0, "sheep " + i + " went " + String.format(java.util.Locale.ROOT, "%.0f", turned[i])
								+ " degrees round in 200 ticks, now at " + sheep.get(i).position() + " (path " + sheep.get(i).getNavigation().getPath() + ")");
						helper.assertFalse(block.isInside(sheep.get(i).blockPosition()), "sheep " + i + " is in the block");
					}
				} finally {
					stop[0].run();
					summoned.forEach(net.minecraft.world.entity.Entity::discard);
					GalleryProps.clear(level, block);
					forced(level, at, false);
				}
				helper.succeed();
			}
		});
	}

	private static void forced(ServerLevel level, BlockPos at, boolean on) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, on);
				if (on) {
					level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
				}
			}
		}
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	@GameTest
	public void thePngWriterRoundTripsAndScales(GameTestHelper helper) {
		Random random = new Random(1016);
		for (int[] size : new int[][] {{1, 1}, {7, 3}, {16, 16}, {33, 9}}) {
			int[] argb = new int[size[0] * size[1]];
			for (int i = 0; i < argb.length; i++) {
				argb[i] = random.nextInt();
			}
			argb[0] = 0;
			Png.Image image = new Png.Image(size[0], size[1], argb);
			byte[] bytes = Png.write(image);
			helper.assertTrue(bytes.length > 8 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G', "not a PNG");
			helper.assertTrue(Png.read(bytes).equals(image), size[0] + " x " + size[1] + " did not come back the same");
		}
		Png.Image two = new Png.Image(2, 1, new int[] {0xFF112233, 0x80445566});
		Png.Image big = Blueprint.scaled(two, 3);
		helper.assertTrue(big.width() == 6 && big.height() == 3 && big.at(2, 2) == 0xFF112233 && big.at(3, 0) == 0x80445566, "scaling");
		helper.assertTrue(Png.read(Png.write(big)).equals(big), "a scaled render did not come back the same");
		helper.succeed();
	}

	@GameTest
	public void registerDoesNothingOutsideADevelopmentEnvironment(GameTestHelper helper) {
		Gallery.Exhibit probe = new Gallery.Exhibit("zz_probe", "zz", (level, origin, seed) -> new Gallery.Placed(new BoundingBox(origin), List.of(), List.of()));
		try {
			helper.assertFalse(Gallery.register(probe, false), "registered outside a development environment");
			helper.assertTrue(Gallery.get("zz_probe").isEmpty(), "an exhibit exists outside a development environment");
			helper.assertTrue(Gallery.active(), "the game tests are a development environment");
			helper.assertTrue(Gallery.get("00").isPresent(), "00 is not registered in a development environment");
			helper.assertTrue(Gallery.register(probe, true) && Gallery.get("zz_probe").isPresent(), "not registered in a development environment");
			List<String> unknown = new ArrayList<>();
			helper.assertTrue(Gallery.named("zz_probe, 00,nope", unknown).stream().map(Gallery.Exhibit::id).toList().equals(List.of("zz_probe", "00"))
					&& unknown.equals(List.of("nope")), "the list is not read in order: " + unknown);
		} finally {
			Gallery.unregister("zz_probe");
		}
		helper.succeed();
	}

	/** Every literal and argument under {@code /a1016 sc g} and its usage, held to the layer's spoiler words (D-103). */
	@GameTest
	public void theGalleryLiteralsPassTheSpoilerWords(GameTestHelper helper) throws ReflectiveOperationException {
		MinecraftServer server = helper.getLevel().getServer();
		CommandNode<CommandSourceStack> sc = server.getCommands().getDispatcher().getRoot().getChild(CommandHooks.ROOT).getChild("sc");
		helper.assertTrue(sc != null && sc.getChild("g") != null, "no /a1016 sc g");
		Method namedIn = VerifyScSpoilerTests.class.getDeclaredMethod("namedIn", String.class, boolean.class);
		namedIn.setAccessible(true);
		List<String> named = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		walk(sc.getChild("g"), "g", namedIn, named, seen);
		helper.assertTrue(seen.containsAll(List.of("g", "all", "id", "plan", "f", "frames", "shot")), "the tree is not the gallery's: " + seen);
		for (String line : server.getCommands().getDispatcher().getAllUsage(sc.getChild("g"), server.createCommandSourceStack(), false)) {
			Object word = namedIn.invoke(null, line, true);
			if (word != null) {
				named.add("usage 'g " + line + "' ('" + word + "')");
			}
		}
		for (Gallery.Exhibit exhibit : Gallery.all()) {
			Object word = namedIn.invoke(null, exhibit.id(), true);
			if (word != null) {
				named.add("exhibit " + exhibit.id() + " ('" + word + "')");
			}
		}
		helper.assertTrue(named.isEmpty(), "gallery literals name a feature: " + named);
		helper.succeed();
	}

	private static void walk(CommandNode<CommandSourceStack> node, String path, Method namedIn, List<String> named, List<String> seen)
			throws ReflectiveOperationException {
		seen.add(node.getName());
		Object word = namedIn.invoke(null, node.getName(), true);
		if (word != null) {
			named.add("/a1016 sc " + path + " ('" + word + "')");
		}
		for (CommandNode<CommandSourceStack> child : node.getChildren()) {
			if (child instanceof LiteralCommandNode<?> || child instanceof ArgumentCommandNode<?, ?>) {
				walk(child, path + " " + child.getName(), namedIn, named, seen);
			}
		}
	}

	/**
	 * Fallback B (docs/SELF_CREATED_2.md, "The client run"): with {@code -Da1016_02.gallery=<ids>} the blueprint renders
	 * of those exhibits, placed far out along +X, are written to {@code build/gallery/<id>/} with the index. Without it
	 * (every {@code ./gradlew build}) it passes at once.
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void blueprintsOfTheNamedExhibitsWhenAsked(GameTestHelper helper) throws java.io.IOException {
		String list = System.getProperty(GalleryRun.LIST_PROPERTY);
		if (list == null || list.isBlank()) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		List<String> unknown = new ArrayList<>();
		List<Gallery.Exhibit> exhibits = Gallery.named(list, unknown);
		helper.assertTrue(unknown.isEmpty(), "no exhibit " + unknown);
		List<GalleryRun.Entry> entries = new ArrayList<>();
		for (int i = 0; i < exhibits.size(); i++) {
			Gallery.Exhibit exhibit = exhibits.get(i);
			int x = 2 * FAR + i * GalleryRun.SPACING;
			Gallery.Placed placed = GalleryRun.place(level, exhibit, new BlockPos(x, Gallery.ground(level, x, 0), 0));
			// Only the renders are written again: a client run's screenshots in the folder stay.
			Path dir = GalleryRun.dir(exhibit.id());
			String box = Blueprint.describe(placed.box());
			for (Path file : Blueprint.write(level, placed.box(), dir)) {
				entries.add(GalleryRun.blueprintEntry(exhibit.id(), GalleryRun.relative(file), box));
			}
		}
		GalleryRun.writeIndex(entries, list + ", blueprints only (Fallback B)");
		A1016_02.LOGGER.info("[a1016] gallery: {} blueprint renders of {} in {}", entries.size(), list, GalleryRun.outDir());
		helper.assertTrue(!entries.isEmpty(), "no renders written");
		helper.succeed();
	}
}
