package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntBinaryOperator;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.RaysPlacement;
import com.forzacode.a1016_02.world.sc2.kit.Recorder;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.s16.PyramidAnswers;
import com.forzacode.a1016_02.world.sc2.s16.PyramidShape;
import com.forzacode.a1016_02.world.sc2.s16.PyramidSiting;
import com.forzacode.a1016_02.world.sc2.s16.S16;
import com.forzacode.a1016_02.world.sc2.s16.Sc16Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-1, feature 16 (docs/SELF_CREATED_2.md): the siting (the kit's gates, the spread, dry land, the sea's height rule,
 * the size fallback, the biomes), the rays and every way in toward the origin, the shape built in the world (the shell,
 * the core, the pit's steps one down and one in with their lining, the rim, the 3 x 3 floor and its light, the seal,
 * nothing dropping more than one), worldgen equal to {@link Live#place} and recorded once, the inside, 16b (only with a
 * pickaxe on everyone inside, the health, the distance, nobody at the mouth, out of view, the caps; undone), 16c (only
 * placed lights, out of view, never the last one near anyone, capped, then the glimpse), the multiplayer rules (a
 * friend at the mouth, two closed in, a friend's light taken), the dig into a closed one, the sea form; and, when
 * {@code -Da1016_02.s16cost=true}, the cost of each size per chunk. Each test builds at its own far-off spot
 * (163 000 to 175 000 on both axes).
 */
public class SelfCreated2PyramidTests {
	private static final String ENV = "a1016_02:world/s16";
	private static final ResourceKey<Structure> S16_KEY = ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/s16"));
	private static final ResourceKey<Structure> S16D_KEY = ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/s16d"));
	private static final ResourceKey<StructureSet> SET_KEY = ResourceKey.create(Registries.STRUCTURE_SET, A1016_02.id("world/s16"));
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | 512;

	// --- helpers ---

	/** A terrain of the test's own: the ground and the water's top by column. */
	private static Terrain terrain(ServerLevel level, IntBinaryOperator ground, IntBinaryOperator surface) {
		Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
		return new Terrain() {
			@Override
			public int seaLevel() {
				return 63;
			}

			@Override
			public int minY() {
				return -64;
			}

			@Override
			public int ground(int x, int z) {
				return ground.applyAsInt(x, z);
			}

			@Override
			public int surface(int x, int z) {
				return Math.max(ground.applyAsInt(x, z), surface.applyAsInt(x, z));
			}

			@Override
			public Holder<Biome> biome(int x, int y, int z) {
				return plains;
			}

			@Override
			public BlockState block(int x, int y, int z) {
				return y <= ground.applyAsInt(x, z) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
			}
		};
	}

	private static Snapshot snapshot(boolean on, Stage stage, Sc16Config config) {
		return new Snapshot(on, stage, BlockPos.ZERO, 1L, 7L, Map.of(S16.ID, config, S16.SEA_ID, config));
	}

	private static Optional<Start> find(PyramidSiting siting, ChunkPos chunk, Terrain terrain, Sc16Config config) {
		return siting.find(new Siting.Query(chunk, terrain, snapshot(true, Stage.TRACES, config), 99L, RandomSource.create(1L), -64, 320));
	}

	private static void loadChunks(ServerLevel level, BoundingBox box) {
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}

	/** A start of this size and form whose lowest course is 40 over the flat ground at (x, z). */
	private static Start start(ServerLevel level, int x, int z, Direction facing, int variant, long seed) {
		int ground = Terrains.noise(level).ground(x, z);
		return new Start(new BlockPos(x, ground + 40, z), facing, variant, seed);
	}

	/**
	 * Stone under the footprint (the flat test world is thin), from the lowest course down past the pit's floor and its
	 * seal, so the pit is dug into rock as in a real world.
	 */
	private static void platform(ServerLevel level, Start start) {
		int size = PyramidShape.size(start.variant());
		int r = PyramidShape.halfWidth(size);
		BlockPos o = start.origin();
		int depth = PyramidShape.pitDepth(size) + 6;
		BoundingBox rock = new BoundingBox(o.getX() - r, o.getY() - depth, o.getZ() - r, o.getX() + r, o.getY() - 1, o.getZ() + r);
		loadChunks(level, rock.inflatedBy(PyramidShape.GROUNDS_HALF + r));
		for (BlockPos pos : BlockPos.betweenClosed(rock.minX(), rock.minY(), rock.minZ(), rock.maxX(), rock.maxY(), rock.maxZ())) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS);
		}
	}

	private static PyramidShape shape(ServerLevel level, Start start) {
		return (PyramidShape) S16.KIND.shapes().create(start, Terrains.noise(level));
	}

	private static ServerPlayer armed(ServerLevel level, String name) {
		ServerPlayer player = new SelfCreated2KitTests.TestPlayer(level, name, GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
		player.setHealth(20.0F);
		return player;
	}

	private static void stand(ServerPlayer player, BlockPos feet) {
		player.snapTo(Vec3.atBottomCenterOf(feet), 0.0F, 0.0F);
	}

	/** Takes this test's ledger entries out (those with this cause, inside this box). */
	private static void forget(MinecraftServer server, String cause, BoundingBox box) {
		TraceLedger ledger = TraceLedger.get(server);
		for (TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
			if (entry.cause().startsWith(cause) && box.isInside(entry.pos().pos())) {
				ledger.remove(entry);
			}
		}
	}

	/** The figure, for the tests: every spot may have him (or none), and every glimpse is kept. */
	private static final class Watcher implements Figure {
		final List<Vec3> glimpses = new ArrayList<>();
		volatile boolean allow = true;

		@Override
		public boolean canGlimpse(ServerPlayer player, Vec3 feet) {
			return allow;
		}

		@Override
		public FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw) {
			glimpses.add(feet);
			return FireResult.FIRED;
		}
	}

	/** A built pyramid with answers of its own: its data, who is inside and around, the view, the clock and what was answered. */
	private record Rig(PyramidAnswers pyramid, Answers answers, Sc2Data data, Remains.Place place, PyramidShape shape, List<ServerPlayer> inside,
			List<ServerPlayer> people, AtomicBoolean seen, AtomicLong day, AtomicLong now, List<String> answered, Sc16Config config, Watcher figure) {
		/** One look at the clock's time {@code ticks}, and the waiting answers tried. */
		void at(MinecraftServer server, long ticks) {
			now.set(ticks);
			pyramid.look(server, ticks);
			answers.tryAll(server);
		}
	}

	private static Rig rig(ServerLevel level, Start start, PyramidAnswers.Lights lights) {
		platform(level, start);
		Live.Result built = Live.place(level, S16.KIND, start);
		PyramidShape shape = (PyramidShape) built.shape();
		Sc2Data data = new Sc2Data();
		data.add(built.known());
		List<ServerPlayer> inside = new ArrayList<>();
		List<ServerPlayer> people = new ArrayList<>();
		AtomicBoolean seen = new AtomicBoolean(false);
		AtomicLong day = new AtomicLong(40);
		AtomicLong now = new AtomicLong(0);
		List<String> answered = Collections.synchronizedList(new ArrayList<>());
		Answers.Env env = new Answers.Env(s -> data, Services.traces().forced(), (l, cells) -> !seen.get(), (s, p) -> List.copyOf(inside), uuid -> false,
				uuid -> 200_000L, s -> null, day::get, now::get, (l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString()));
		Answers answers = Answers.over(env);
		Sc16Config config = new Sc16Config();
		Watcher figure = new Watcher();
		figure.allow = false;
		PyramidAnswers pyramid = new PyramidAnswers(new PyramidAnswers.Env(answers, server -> List.copyOf(people), lights, () -> figure, () -> config,
				l -> place -> shape));
		return new Rig(pyramid, answers, data, built.place(), shape, inside, people, seen, day, now, answered, config, figure);
	}

	private static boolean open(ServerLevel level, PyramidShape shape) {
		return shape.innerMouth().stream().allMatch(pos -> level.getBlockState(pos).isAir());
	}

	private static boolean closed(ServerLevel level, PyramidShape shape) {
		return shape.innerMouth().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.BRICKS));
	}

	// --- siting ---

	/**
	 * The kit's gates (no snapshot, off, under Traces, near the origin), then the kind's own siting over a terrain of the
	 * test's: on flat dry land the size the weights pick, the way in toward the origin, the lowest course over the median
	 * ground; a shelf too high for a 95 falls back to a 63; ground under water refuses every size; at sea the apex must
	 * stand 12 over the water (a 63 on a deep floor does not, a 95 does) and the form is closed; the chances; the biomes.
	 */
	@GameTest
	public void sitingKeepsTheGatesTheSpreadDryLandTheSeaRuleAndTheSizeFallback(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc16Config config = new Sc16Config();
		ChunkPos far = new ChunkPos(100, 0);
		ChunkPos near = new ChunkPos(10, 0);
		helper.assertTrue("no snapshot".equals(Snapshot.refusal(null, S16.KIND, far)), "no snapshot");
		helper.assertTrue("switched off".equals(Snapshot.refusal(snapshot(false, Stage.TRACES, config), S16.KIND, far)), "off");
		helper.assertTrue(String.valueOf(Snapshot.refusal(snapshot(true, Stage.ALONE, config), S16.KIND, far)).contains("stage"), "under Traces");
		helper.assertTrue(String.valueOf(Snapshot.refusal(snapshot(true, Stage.TRACES, config), S16.KIND, near)).contains("origin"), "near the origin");
		helper.assertTrue(Snapshot.refusal(snapshot(true, Stage.TRACES, config), S16.KIND, far) == null
				&& Snapshot.refusal(snapshot(true, Stage.TRACES, config), S16.SEA_KIND, far) == null, "far, at Traces");

		int cx = far.getMiddleBlockX();
		int cz = far.getMiddleBlockZ();
		config.sizeWeights = new double[] {0, 1, 0};
		config.sealedChance = 0;
		config.apexLightChance = 0;
		Optional<Start> flat = find(PyramidSiting.LAND, far, terrain(level, (x, z) -> 70, (x, z) -> 70), config);
		helper.assertTrue(flat.isPresent(), "nothing on flat dry land");
		Start s = flat.get();
		helper.assertTrue(PyramidShape.size(s.variant()) == 1 && !PyramidShape.sealed(s.variant()) && !PyramidShape.light(s.variant())
				&& !PyramidShape.sea(s.variant()), "the variant: " + s.variant());
		helper.assertTrue(s.origin().equals(new BlockPos(cx, 71, cz)) && s.facing() == Direction.WEST, "the start: " + s);

		Terrain shelf = terrain(level, (x, z) -> x > cx + 40 ? 90 : 70, (x, z) -> 0);
		helper.assertTrue(PyramidSiting.fits(shelf, cx, cz, 1, 12, false) == Integer.MIN_VALUE, "a 95 over the shelf");
		Optional<Start> fallback = find(PyramidSiting.LAND, far, shelf, config);
		helper.assertTrue(fallback.isPresent() && PyramidShape.size(fallback.get().variant()) == 0, "no fall back to a 63: " + fallback);

		// R10-3: a shore takes up to three of the nine samples under water; two thirds is too wet.
		Terrain shore = terrain(level, (x, z) -> 70, (x, z) -> z < cz - 20 ? 75 : 0);
		helper.assertTrue(find(PyramidSiting.LAND, far, shore, config).isPresent(), "refused on a shore, a third of it under water");
		Terrain lake = terrain(level, (x, z) -> 70, (x, z) -> z < cz + 20 ? 75 : 0);
		helper.assertTrue(find(PyramidSiting.LAND, far, lake, config).isEmpty(), "two thirds of it under water");

		Terrain deep = terrain(level, (x, z) -> 40, (x, z) -> 62);
		helper.assertTrue(PyramidSiting.fits(deep, cx, cz, 0, 12, true) == Integer.MIN_VALUE, "a 63's apex 12 over the water from a floor at 40");
		helper.assertTrue(PyramidSiting.fits(deep, cx, cz, 1, 12, true) == 41, "a 95 on that floor");
		config.sizeWeights = new double[] {1, 0, 0};
		Optional<Start> sea = find(PyramidSiting.SEA, far, deep, config);
		helper.assertTrue(sea.isPresent() && PyramidShape.size(sea.get().variant()) == 1 && PyramidShape.sea(sea.get().variant())
				&& PyramidShape.sealed(sea.get().variant()), "the sea form: " + sea);
		helper.assertTrue(find(PyramidSiting.SEA, far, terrain(level, (x, z) -> 40, (x, z) -> 0), config).isEmpty(), "the sea form on dry land");
		config.sealedChance = 1;
		config.apexLightChance = 1;
		Start sealed = find(PyramidSiting.LAND, far, terrain(level, (x, z) -> 70, (x, z) -> 0), config).orElseThrow();
		helper.assertTrue(PyramidShape.sealed(sealed.variant()) && PyramidShape.light(sealed.variant()), "the chances at 1");

		Holder<Structure> land = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S16_KEY);
		Holder<Structure> water = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S16D_KEY);
		var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		for (ResourceKey<Biome> key : List.of(Biomes.PLAINS, Biomes.DESERT, Biomes.SNOWY_PLAINS, Biomes.SAVANNA, Biomes.BADLANDS, Biomes.MEADOW,
				Biomes.FOREST, Biomes.TAIGA, Biomes.WINDSWEPT_SAVANNA)) {
			helper.assertTrue(land.value().biomes().contains(biomes.getOrThrow(key)) && !water.value().biomes().contains(biomes.getOrThrow(key)),
					"land biome " + key.identifier());
		}
		for (ResourceKey<Biome> key : List.of(Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN)) {
			helper.assertTrue(water.value().biomes().contains(biomes.getOrThrow(key)) && !land.value().biomes().contains(biomes.getOrThrow(key)),
					"sea biome " + key.identifier());
		}
		for (ResourceKey<Biome> key : List.of(Biomes.JUNGLE, Biomes.RIVER, Biomes.SWAMP)) {
			helper.assertFalse(land.value().biomes().contains(biomes.getOrThrow(key)), "not in " + key.identifier());
		}
		helper.succeed();
	}

	/**
	 * The structure set: both forms on four rays (640, then every 640, jittered by 8 degrees and 48 blocks: R10-3); a start in
	 * every slot of the first four on each ray faces the origin, and for every facing the way in is in the face nearest
	 * the origin's side: its mouth is the base's nearest point toward it.
	 */
	@GameTest
	public void slotsLieOnThreeRaysAndEveryWayInFacesTheOrigin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(SET_KEY).value();
		helper.assertTrue(set.placement() instanceof RaysPlacement, "the set is not on rays: " + set.placement());
		RaysPlacement rays = (RaysPlacement) set.placement();
		helper.assertTrue(rays.rays() == 4 && rays.first() == 640 && rays.step() == 640 && rays.jitterDegrees() == 8.0F && rays.jitterBlocks() == 48,
				"the rays' numbers");
		helper.assertTrue(set.structures().size() == 2 && set.structures().stream().anyMatch(e -> e.structure().is(S16_KEY))
				&& set.structures().stream().anyMatch(e -> e.structure().is(S16D_KEY)), "both forms in the set");
		Sc16Config config = new Sc16Config();
		Terrain flat = terrain(level, (x, z) -> 70, (x, z) -> 0);
		int checked = 0;
		for (RaysPlacement.Slot slot : rays.slots(level.getSeed(), 4)) {
			Start start = find(PyramidSiting.LAND, slot.chunk(), flat, config).orElseThrow();
			int mx = slot.chunk().getMiddleBlockX();
			int mz = slot.chunk().getMiddleBlockZ();
			helper.assertTrue(start.facing() == Siting.facingToOrigin(mx, mz), "slot " + slot + " faces " + start.facing());
			PyramidShape shape = shape(level, start);
			BlockPos mouth = shape.entrance();
			double toOrigin = Math.hypot(mouth.getX(), mouth.getZ());
			for (Direction other : Direction.Plane.HORIZONTAL) {
				BlockPos side = new BlockPos(mx + other.getStepX() * shape.halfWidth(), 0, mz + other.getStepZ() * shape.halfWidth());
				helper.assertTrue(toOrigin <= Math.hypot(side.getX(), side.getZ()) + 1.5, "slot " + slot + ": the way in is not the side nearest the origin");
			}
			checked++;
		}
		helper.assertTrue(checked == 16, "slots checked: " + checked);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			PyramidShape shape = shape(level, new Start(new BlockPos(500, 70, 500), facing, PyramidShape.variant(0, false, false, false), 3L));
			BlockPos expected = new BlockPos(500 + facing.getStepX() * shape.halfWidth(), 70, 500 + facing.getStepZ() * shape.halfWidth());
			helper.assertTrue(shape.entrance().equals(expected) && shape.entranceFacing() == facing, facing + ": the way in at " + shape.entrance());
			helper.assertTrue(shape.wayIn().stream().allMatch(cell -> shape.inMass(cell.getX(), cell.getY(), cell.getZ())), facing + ": the way in leaves the mass");
		}
		helper.succeed();
	}

	// --- the shape ---

	/**
	 * R12-3, R13-3, R11-1, R11-4, R11-16 and R12-2: a lit 63 built as version 2 across a slope (the land 10 lower on one side
	 * of the base, higher on the other, where its way in faces): from every walkable cell of its outer surface (its steps,
	 * its plinth) nothing drops more than 3 without water (the plinth steps down a block out each course to the land); the
	 * way in is reached by a cut exactly 2 wide with the land for its sides; its point is taken off, the top a 3 x 3 level
	 * of glowstone. The same start built as version 1 (a place half built before the change) keeps its old foot, with
	 * drops of more than 3 (what the review found), and its single lit apex block.
	 */
	@GameTest(maxTicks = 400)
	public void itsFootStepsDownToTheLandAndItsTopIsALevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int ox = 179_008;
		int oz = 179_008;
		int shift = 400;
		int r = PyramidShape.halfWidth(0);
		int g0 = -40;
		IntBinaryOperator ground = (x, z) -> g0 + Math.floorDiv(Math.floorMod(x - ox + 200, shift) - 200, 3);
		int reach = r + PyramidShape.PLINTH_MAX + 4;
		for (int dx : new int[] {0, shift}) {
			slope(level, new BoundingBox(ox + dx - reach, -61, oz - reach, ox + dx + reach, g0 + r + 40, oz + reach), ground);
		}
		Start start = new Start(new BlockPos(ox, g0 + 1, oz), Direction.EAST, PyramidShape.variant(0, false, true, false), 0x5107EL);
		PyramidShape now = (PyramidShape) Live.place(level, S16.KIND, start).shape();
		PyramidShape before = (PyramidShape) Live.place(level, S16.KIND, start.moved(shift, 0, 0).withVersion(1)).shape();
		helper.assertTrue(now.version() == PyramidShape.VERSION && now.plinth() && before.version() == 1 && !before.plinth(), "the versions");
		int y0 = now.baseY();
		List<String> drops = new ArrayList<>();
		List<String> old = new ArrayList<>();
		for (PyramidShape shape : List.of(now, before)) {
			int cx = shape.centerX();
			BoundingBox area = new BoundingBox(cx - reach, -64, oz - reach, cx + reach, 320, oz + reach);
			List<BlockPos> seeds = new ArrayList<>();
			// Every cell of its steps and its plinth (climbing them is the way up); the cut and its lips only by walking.
			for (int x = cx - r - PyramidShape.PLINTH_MAX; x <= cx + r + PyramidShape.PLINTH_MAX; x++) {
				for (int z = oz - r - PyramidShape.PLINTH_MAX; z <= oz + r + PyramidShape.PLINTH_MAX; z++) {
					if (!(x - cx >= r - 1 && x - cx <= r + PyramidShape.SLOT_MAX + 1 && z - oz >= -1 && z - oz <= 2)) {
						seeds.add(new BlockPos(x, 0, z));
					}
				}
			}
			java.util.function.BiPredicate<Integer, Integer> own = (x, z) -> {
				int ring = Math.max(Math.abs(x - cx), Math.abs(z - oz));
				if (ring <= r) {
					return true;
				}
				// The cut to the way in (it faces east: out is +x, right is +z) and its sides.
				if (x - cx >= r - 1 && x - cx <= r + PyramidShape.SLOT_MAX + 1 && z - oz >= -1 && z - oz <= 2) {
					return true;
				}
				int k = ring - r;
				int top = Edges.top(level, x, z);
				return k <= PyramidShape.PLINTH_MAX && top == y0 - k && !level.getBlockState(new BlockPos(x, top, z)).is(Blocks.DIRT)
						&& !level.getBlockState(new BlockPos(x, top, z)).is(Blocks.COARSE_DIRT) && !level.getBlockState(new BlockPos(x, top, z)).is(Blocks.GRASS_BLOCK);
			};
			(shape == now ? drops : old).addAll(Edges.unguardedDrops(level, area, seeds, own));
		}
		helper.assertTrue(drops.isEmpty(), "unguarded drops off version 2: " + drops);
		helper.assertTrue(!old.isEmpty(), "version 1 shows no drop: the slope does not test the foot");
		// The way in: a cut 2 wide through the higher land, its sides the land.
		for (int f = r + 1; f <= r + 4; f++) {
			helper.assertTrue(level.getBlockState(now.local(0, 0, f)).isAir() && level.getBlockState(now.local(1, 0, f)).isAir()
					&& level.getBlockState(now.local(0, 1, f)).isAir() && !level.getBlockState(now.local(-1, 0, f)).isAir()
					&& !level.getBlockState(now.local(2, 0, f)).isAir(), "the cut at " + f);
		}
		// The top: version 2 a 3 x 3 level of glowstone, nothing over its middle; version 1 its single lit block.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.assertTrue(level.getBlockState(new BlockPos(ox + dx, y0 + r - 1, oz + dz)).is(Blocks.GLOWSTONE), "the lit top at " + dx + " " + dz);
			}
		}
		helper.assertTrue(level.getBlockState(new BlockPos(ox, y0 + r, oz)).isAir() && now.apex().orElseThrow().equals(new BlockPos(ox, y0 + r - 1, oz)),
				"the point is not taken off");
		helper.assertTrue(level.getBlockState(new BlockPos(ox + shift, y0 + r, oz)).is(Blocks.GLOWSTONE)
				&& level.getBlockState(new BlockPos(ox + shift + 1, y0 + r - 1, oz)).is(Blocks.BRICKS), "version 1's apex");
		helper.succeed();
	}

	/**
	 * R13-8, what is kept: a closed pyramid dug into from outside at its foot, the way a player looks for a way in, opens
	 * onto the rim walkway or the pit's first tread: no fall over 3 for a tunnel dug at the lowest three courses, on any
	 * face, any size. (One dug in higher up opens over the vault, as a mine opens into a ravine: the player's own choice,
	 * written into D-097.7 by the review.)
	 */
	@GameTest
	public void diggingIntoAClosedOneAtItsFootOpensOntoTheRim(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (int size = 0; size < PyramidShape.BASES.length; size++) {
			PyramidShape shape = shape(helper.getLevel(), new Start(new BlockPos(181_008, 70, 181_008), Direction.NORTH, PyramidShape.variant(size, true, false,
					false), 7L + size));
			int r = shape.halfWidth();
			for (Direction face : Direction.Plane.HORIZONTAL) {
				for (int layer = 0; layer <= 2; layer++) {
					// The first cell of the core met tunnelling straight in at this course.
					int y = shape.baseY() + layer;
					int d = r;
					while (d > 0 && !shape.core(shape.centerX() + face.getStepX() * d, y, shape.centerZ() + face.getStepZ() * d)) {
						d--;
					}
					int x = shape.centerX() + face.getStepX() * d;
					int z = shape.centerZ() + face.getStepZ() * d;
					int floor = y - 1;
					while (shape.core(x, floor, z) || shape.pit(x, floor, z)) {
						floor--;
					}
					if (y - (floor + 1) > 3) {
						wrong.add("size " + size + ", " + face + ", course " + layer + ": " + (y - floor - 1));
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "falls over 3 from a breach at the foot: " + wrong);
		helper.succeed();
	}

	/**
	 * R11-17 (version 3): from the pit's 3 x 3 floor a dead end 2 wide and 2 high runs {@value PyramidShape#TUNNEL_LENGTH}
	 * under the steps toward the way in's side, built so (air, a floor and an end under it and past it, no light in it) and
	 * counted inside; its mouth takes the two lowest treads of its strip, a drop of 3 at most from any step beside it.
	 * Version 2 keeps its steps whole.
	 */
	@GameTest(maxTicks = 400)
	public void aDeadEndRunsOnFromThePitsFloor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int ox = 183_008;
		int oz = 183_008;
		int g0 = 0;
		int r = PyramidShape.halfWidth(0);
		for (int dx : new int[] {0, 400}) {
			slope(level, new BoundingBox(ox + dx - r - 4, -61, oz - r - 4, ox + dx + r + 4, g0 + r + 4, oz + r + 4), (x, z) -> g0);
		}
		Start start = new Start(new BlockPos(ox, g0 + 1, oz), Direction.SOUTH, PyramidShape.variant(0, false, false, false), 0x7E11L);
		PyramidShape now = (PyramidShape) Live.place(level, S16.KIND, start).shape();
		PyramidShape before = (PyramidShape) Live.place(level, S16.KIND, start.moved(400, 0, 0).withVersion(2)).shape();
		helper.assertTrue(now.version() == 3 && before.version() == 2 && before.tunnelCells().isEmpty(), "the versions");
		List<BlockPos> cells = now.tunnelCells();
		helper.assertTrue(cells.size() == 4 * PyramidShape.TUNNEL_LENGTH, "the tunnel's cells: " + cells.size());
		List<String> wrong = new ArrayList<>();
		Direction facing = now.facing();
		for (BlockPos cell : cells) {
			if (!level.getBlockState(cell).isAir() || !now.interior().contains(cell)) {
				wrong.add("not open or not inside at " + cell.toShortString() + ": " + level.getBlockState(cell));
			}
			if (level.getBlockState(cell).getLightEmission() > 0) {
				wrong.add("lit at " + cell.toShortString());
			}
			BlockPos under = cell.below();
			if (!now.tunnel(under.getX(), under.getY(), under.getZ()) && level.getBlockState(under).isAir()) {
				wrong.add("no floor under " + cell.toShortString());
			}
			BlockPos past = cell.relative(facing);
			if (!now.tunnel(past.getX(), past.getY(), past.getZ()) && level.getBlockState(past).isAir()) {
				wrong.add("open past " + cell.toShortString());
			}
		}
		// The drop into its mouth: from a feet cell of the pit (or the tunnel) beside a column of the strip, down that column.
		int deepest = 0;
		for (BlockPos cell : cells) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = cell.relative(d);
				for (int y = now.pitBottomY(); y <= now.pitBottomY() + 6; y++) {
					BlockPos feet = new BlockPos(side.getX(), y, side.getZ());
					if (level.getBlockState(feet).isAir() && !level.getBlockState(feet.below()).isAir()) {
						int land = y;
						while (level.getBlockState(new BlockPos(cell.getX(), land - 1, cell.getZ())).isAir()) {
							land--;
						}
						if (land < y && level.getBlockState(new BlockPos(cell.getX(), y, cell.getZ())).isAir()
								&& level.getBlockState(new BlockPos(cell.getX(), y + 1, cell.getZ())).isAir()) {
							deepest = Math.max(deepest, y - land);
						}
					}
				}
			}
		}
		helper.assertTrue(deepest <= 3, "a drop of " + deepest + " into the tunnel's mouth");
		BlockPos firstTread = before.local(0, before.pitBottomY() - before.baseY(), 2);
		helper.assertFalse(level.getBlockState(firstTread).isAir(), "version 2's lowest tread was taken: " + firstTread.toShortString());
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.subList(0, Math.min(8, wrong.size())));
		helper.succeed();
	}

	/** Rock to the land's top over the box, grass on it and air over it (only what differs is set). */
	private static void slope(ServerLevel level, BoundingBox box, IntBinaryOperator ground) {
		loadChunks(level, box);
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				int g = ground.applyAsInt(x, z);
				for (int y = box.minY(); y <= box.maxY(); y++) {
					BlockState want = y < g ? stone : y == g ? grass : air;
					if (level.getBlockState(at.set(x, y, z)) != want) {
						level.setBlock(at, want, FLAGS);
					}
				}
			}
		}
	}

	/**
	 * A 63 built over rock with three leaks dug next to its pit (water, a cave pocket, sand): the core is air, the shell
	 * under the faces is brick, the pit's every level is one down and one in with a brick lining, the 3 x 3 floor holds a
	 * cobblestone and a torch, the rim walkway is floored and under the core's air, the way in is open from outside to
	 * the core, the leaks are sealed and nothing within two of the pit leaks, and nowhere in the pit is a drop of more
	 * than one block between two neighbouring floors (fair danger 7).
	 */
	@GameTest(maxTicks = 200)
	public void theShapeHasItsShellCorePitLiningRimAndSeal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Start start = start(level, 163_008, 163_008, Direction.WEST, PyramidShape.variant(0, false, false, false), 0x16AL);
		PyramidShape shape = shape(level, start);
		int r = shape.halfWidth();
		int p0 = shape.pitOpening();
		int cx = shape.centerX();
		int cz = shape.centerZ();
		int y0 = shape.baseY();
		platform(level, start);
		List<BlockPos> leaks = List.of(new BlockPos(cx + p0 - 5 + 2, y0 - 1 - 5, cz), new BlockPos(cx, y0 - 3, cz + p0 + 2),
				new BlockPos(cx - (p0 - 8 + 2), y0 - 1 - 8, cz + 3));
		List<BlockState> leaking = List.of(Blocks.WATER.defaultBlockState(), Blocks.AIR.defaultBlockState(), Blocks.SAND.defaultBlockState());
		for (int i = 0; i < leaks.size(); i++) {
			level.setBlock(leaks.get(i), leaking.get(i), FLAGS);
		}
		Live.Result built = Live.place(level, S16.KIND, start);
		helper.assertTrue(built.box().equals(shape.box()), "the box");
		List<String> wrong = new ArrayList<>();
		int surfaceHoles = 0;
		int surfaceCells = 0;
		List<BlockPos> openings = new ArrayList<>(shape.wayIn());
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				int d = shape.ring(x, z);
				for (int layer = 0; layer <= r - d; layer++) {
					BlockPos pos = new BlockPos(x, y0 + layer, z);
					BlockState state = level.getBlockState(pos);
					boolean cut = shape.cutCells().stream().anyMatch(b -> b.isInside(pos)) || openings.contains(pos);
					if (shape.core(x, y0 + layer, z)) {
						if (!state.isAir() && wrong.size() < 10) {
							wrong.add("core " + pos.toShortString() + " " + state);
						}
					} else if (layer == r - d) {
						surfaceCells++;
						surfaceHoles += state.isAir() ? 1 : 0;
					} else if (!cut && !state.is(Blocks.BRICKS) && wrong.size() < 10) {
						wrong.add("shell " + pos.toShortString() + " " + state);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the mass: " + wrong);
		helper.assertTrue(surfaceHoles < surfaceCells / 12, "the faces have " + surfaceHoles + " holes in " + surfaceCells);
		// The pit: every level j is air out to p0 - j and lined just past it; one down and one in.
		for (int j = 0; j < p0; j++) {
			int y = y0 - 1 - j;
			int air = p0 - j;
			helper.assertTrue(shape.pitAirAt(y) == air, "level " + j + " is " + shape.pitAirAt(y) + " wide");
			for (int a = -air - 1; a <= air + 1; a++) {
				for (BlockPos pos : List.of(new BlockPos(cx + a, y, cz - air - 1), new BlockPos(cx + a, y, cz + air + 1), new BlockPos(cx - air - 1, y, cz + a),
						new BlockPos(cx + air + 1, y, cz + a))) {
					// Version 3's tunnel takes the two lowest treads of its strip (R11-17).
					if (!shape.tunnel(pos.getX(), pos.getY(), pos.getZ()) && !level.getBlockState(pos).is(Blocks.BRICKS) && wrong.size() < 10) {
						wrong.add("lining " + pos.toShortString() + " " + level.getBlockState(pos));
					}
				}
				BlockPos inside = new BlockPos(cx + a, y, cz + Math.min(air, Math.abs(a)) * (a < 0 ? -1 : 1));
				if (Math.abs(a) <= air && !level.getBlockState(inside).isAir() && !(inside.getX() == cx && inside.getZ() == cz) && wrong.size() < 10) {
					wrong.add("pit air " + inside.toShortString() + " " + level.getBlockState(inside));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the pit: " + wrong);
		int bottom = shape.pitBottomY();
		for (BlockPos pos : BlockPos.betweenClosed(cx - 1, bottom - 1, cz - 1, cx + 1, bottom - 1, cz + 1)) {
			helper.assertTrue(level.getBlockState(pos).is(Blocks.BRICKS), "the 3 x 3 floor at " + pos.toShortString());
		}
		helper.assertTrue(shape.pitDepth() == 24 && bottom == y0 - 25, "the depth: " + shape.pitDepth());
		helper.assertTrue(level.getBlockState(new BlockPos(cx, bottom, cz)).is(Blocks.COBBLESTONE)
				&& level.getBlockState(new BlockPos(cx, bottom + 1, cz)).is(Blocks.TORCH), "the cobblestone and its torch");
		// The rim: floored from the kerb to the walls, the core's air over it.
		for (int d = p0 + 1; d <= r - PyramidShape.SHELL; d++) {
			BlockPos floor = new BlockPos(cx + d, y0 - 1, cz);
			helper.assertTrue(level.getBlockState(floor).isSolid() && level.getBlockState(floor.above()).isAir() && shape.core(floor.getX(), y0, cz),
					"the rim at ring " + d + ": " + level.getBlockState(floor));
		}
		helper.assertTrue(level.getBlockState(new BlockPos(cx + p0 + 1, y0 - 1, cz)).is(Blocks.BRICKS), "the kerb");
		// The way in, open from outside into the core, on a floor.
		for (BlockPos cell : shape.wayIn()) {
			helper.assertTrue(level.getBlockState(cell).isAir(), "the way in at " + cell.toShortString());
		}
		helper.assertTrue(level.getBlockState(shape.insideMouth()).isAir() && level.getBlockState(shape.entrance().below()).isSolid(), "the mouth");
		// The seal: the three leaks are brick, and nothing within two of the pit's air leaks.
		for (BlockPos leak : leaks) {
			helper.assertTrue(level.getBlockState(leak).is(Blocks.BRICKS), "the seal left " + level.getBlockState(leak) + " at " + leak.toShortString());
		}
		for (BlockPos pos : BlockPos.betweenClosed(cx - p0 - 3, bottom - 3, cz - p0 - 3, cx + p0 + 3, y0 - 2, cz + p0 + 3)) {
			BlockState state = level.getBlockState(pos);
			boolean leaky = state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof FallingBlock;
			// The pit's air and version 3's tunnel (R11-17) are the inside the seal keeps.
			if (!leaky || shape.pit(pos.getX(), pos.getY(), pos.getZ()) || shape.tunnel(pos.getX(), pos.getY(), pos.getZ()) || pos.getX() == cx && pos.getZ() == cz) {
				continue;
			}
			boolean near = false;
			for (BlockPos n : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
				near |= shape.pit(n.getX(), n.getY(), n.getZ()) || shape.tunnel(n.getX(), n.getY(), n.getZ());
			}
			if (near && wrong.size() < 10) {
				wrong.add("leak " + pos.toShortString() + " " + state);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the seal: " + wrong);
		// No hidden falls: the floor of every column of the pit is within one of its neighbours'.
		int span = p0 + 1;
		int[][] floor = new int[2 * span + 1][2 * span + 1];
		for (int a = -span; a <= span; a++) {
			for (int b = -span; b <= span; b++) {
				int y = y0 - 1;
				while (y > bottom - 2 && !level.getBlockState(new BlockPos(cx + a, y, cz + b)).isSolid()) {
					y--;
				}
				floor[a + span][b + span] = y;
			}
		}
		for (int a = -span; a < span; a++) {
			for (int b = -span; b < span; b++) {
				int here = floor[a + span][b + span];
				// Version 3's tunnel (R11-17) has its own test: its mouth's strip drops 3 at most.
				int lowY = shape.pitBottomY();
				if (shape.tunnel(cx + a, lowY, cz + b) || shape.tunnel(cx + a + 1, lowY, cz + b) || shape.tunnel(cx + a, lowY, cz + b + 1)) {
					continue;
				}
				if ((Math.abs(here - floor[a + span + 1][b + span]) > 1 || Math.abs(here - floor[a + span][b + span + 1]) > 1) && wrong.size() < 10) {
					wrong.add("a drop at " + (cx + a) + " " + (cz + b));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the falls: " + wrong);
		helper.succeed();
	}

	/**
	 * Worldgen's own path (the start as the structure makes it, saved and loaded back, placed chunk by chunk and finished
	 * as vanilla finishes a chunk) builds a 63 exactly as {@link Live#place} does 256 blocks away, block for block; the
	 * recorder knows it once, as a pyramid with its apex.
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void builtByWorldgenEqualsLivePlaceAndIsRecordedOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Start start = start(level, 166_008, 166_008, Direction.SOUTH, PyramidShape.variant(0, false, false, false), 0x5EED16L);
		int shift = 256;
		platform(level, start);
		platform(level, start.moved(shift, 0, 0));
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S16_KEY);
		Sc2Data data = new Sc2Data();
		try {
			Recorder.drain(new Sc2Data());
			StructureStart made = Live.startOf(level, holder, start);
			StructureStart loaded = Live.reload(level, made);
			helper.assertTrue(loaded != null && loaded.isValid(), "the start did not load back");
			Live.placeStart(level, loaded);
			helper.assertTrue(Recorder.drain(data) == 1 && data.places().size() == 1, "recorded " + data.places().size());
			Remains.Place place = data.places().getFirst().place();
			PyramidShape shape = shape(level, start);
			helper.assertTrue(place.kind() == Remains.Kind.PYRAMID && place.apex().equals(shape.apex()) && place.entrance().equals(shape.entrance())
					&& place.entranceFacing() == Direction.SOUTH, "the recorded place: " + place);
			Live.Result live = Live.place(level, S16.KIND, start.moved(shift, 0, 0));
			BoundingBox box = made.getBoundingBox();
			helper.assertTrue(live.box().equals(box.moved(shift, 0, 0)), "the boxes: " + box + " / " + live.box());
			List<String> differ = new ArrayList<>();
			int bricks = 0;
			BoundingBox around = box.inflatedBy(1);
			for (BlockPos pos : BlockPos.betweenClosed(around.minX(), around.minY(), around.minZ(), around.maxX(), around.maxY(), around.maxZ())) {
				BlockState a = level.getBlockState(pos);
				BlockState b = level.getBlockState(pos.offset(shift, 0, 0));
				bricks += a.is(Blocks.BRICKS) ? 1 : 0;
				if (a != b && differ.size() < 8) {
					differ.add(pos.toShortString() + " " + a + " / " + b);
				}
			}
			helper.assertTrue(differ.isEmpty(), "worldgen and Live differ: " + differ);
			helper.assertTrue(bricks > 8_000, "only " + bricks + " bricks");
		} finally {
			Recorder.drain(new Sc2Data());
		}
		helper.succeed();
	}

	/**
	 * Inside is the core and the pit: the hall over the pit, the rim, a step of the pit; never the outer steps, the shell,
	 * the way in, a cut or the masonry round it. Through the kit's index too ({@code Remains.containing}).
	 */
	@GameTest
	public void theInsideIsTheCoreAndThePitNotTheShellTheStepsTheWayInOrTheCuts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Start start = new Start(new BlockPos(168_008, 80, 168_008), Direction.NORTH, PyramidShape.variant(1, false, false, false), 0xC0FFEEL);
		PyramidShape shape = shape(level, start);
		int r = shape.halfWidth();
		int cx = shape.centerX();
		int cz = shape.centerZ();
		int y0 = shape.baseY();
		List<BlockPos> in = List.of(new BlockPos(cx, y0 + 10, cz), new BlockPos(cx + r - 4, y0, cz), new BlockPos(cx + 5, y0 - 10, cz),
				new BlockPos(cx, shape.pitBottomY(), cz + 1), shape.insideMouth());
		List<BlockPos> out = new ArrayList<>(List.of(new BlockPos(cx + r, y0, cz), new BlockPos(cx + r, y0 + 1, cz), new BlockPos(cx + r - 1, y0, cz),
				new BlockPos(cx, y0 + r, cz), new BlockPos(cx, y0 + r - 2, cz), new BlockPos(cx + r - 2, y0 - 1, cz), new BlockPos(cx, shape.pitBottomY() - 2, cz)));
		out.addAll(shape.wayIn());
		for (BoundingBox cut : shape.cutKeepOuts()) {
			out.add(cut.getCenter());
		}
		for (BlockPos pos : in) {
			helper.assertTrue(shape.interior().contains(pos), "not inside: " + pos.toShortString());
		}
		for (BlockPos pos : out) {
			helper.assertFalse(shape.interior().contains(pos), "inside: " + pos.toShortString());
		}
		Sc2Data data = new Sc2Data();
		Remains.Place place = new Remains.Place(Remains.Kind.PYRAMID, level.dimension(), shape.box(), shape.anchor(), shape.entrance(), shape.entranceFacing(),
				shape.apex(), start.seed(), start.variant());
		data.add(Sc2Data.Known.of(place, S16.ID, start, shape.extra()));
		RemainsImpl remains = RemainsImpl.over(data);
		for (BlockPos pos : in) {
			helper.assertTrue(remains.containing(level, pos).isPresent(), "the index: not inside at " + pos.toShortString());
		}
		for (BlockPos pos : out) {
			helper.assertTrue(remains.containing(level, pos).isEmpty(), "the index: inside at " + pos.toShortString());
		}
		helper.succeed();
	}

	// --- 16b ---

	/**
	 * 16b with one player: nothing before 90 s inside; refused without a pickaxe and under 10 health; waits while she is
	 * within 20 of the mouth and while it would be seen; then four bricks beside the mouth move into it (their hollows
	 * left), ledgered, the answer counted for her; undone through the ledger; the place's day cap holds the next visit.
	 */
	@GameTest(maxTicks = 200)
	public void theWayInClosesOnlyWithAPickaxeTheHealthTheDistanceOutOfViewAndIsUndone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig rig = rig(level, start(level, 170_008, 163_008, Direction.EAST, PyramidShape.variant(0, false, false, false), 0xB16BL), PyramidAnswers.LIVE_LIGHTS);
		PyramidShape shape = rig.shape();
		int r = shape.halfWidth();
		ServerPlayer ana = armed(level, "ana");
		stand(ana, shape.local(0, 0, -(r - 4)));
		rig.inside().add(ana);
		rig.people().add(ana);
		try {
			rig.pyramid().entered(rig.place(), ana, 0);
			rig.at(server, 20 * 80);
			helper.assertTrue(open(level, shape) && rig.answered().isEmpty(), "closed before 90 s");
			ana.getInventory().clearContent();
			rig.at(server, 20 * 90);
			helper.assertTrue(open(level, shape), "closed without a pickaxe");
			ana.getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			ana.setHealth(9.0F);
			rig.at(server, 20 * 90 + PyramidAnswers.CLOSE_RETRY_TICKS);
			helper.assertTrue(open(level, shape), "closed under 10 health");
			ana.setHealth(20.0F);
			stand(ana, shape.local(-3, 0, r - 4));
			rig.at(server, 20 * 90 + 2 * PyramidAnswers.CLOSE_RETRY_TICKS);
			helper.assertTrue(open(level, shape) && rig.answers().waiting(rig.place(), "16b"), "not waiting while she is at the mouth");
			stand(ana, shape.local(0, 0, -(r - 4)));
			rig.seen().set(true);
			rig.at(server, 20 * 90 + 2 * PyramidAnswers.CLOSE_RETRY_TICKS + 100);
			helper.assertTrue(open(level, shape), "closed in view");
			List<BlockPos> beside = shape.besideMouth().subList(0, 4);
			rig.seen().set(false);
			rig.at(server, 20 * 90 + 2 * PyramidAnswers.CLOSE_RETRY_TICKS + 200);
			helper.assertTrue(closed(level, shape), "not closed out of view with every rule met");
			helper.assertTrue(beside.stream().allMatch(pos -> level.getBlockState(pos).isAir()), "no hollows beside the mouth");
			helper.assertTrue(rig.answered().equals(List.of("16b:ana")) && rig.data().spent(rig.place(), "16b").ever() == 1, "counted: " + rig.answered());
			List<TraceLedger.Entry> moves = TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(PyramidAnswers.CLOSE)).toList();
			helper.assertTrue(moves.size() == 4, "ledgered: " + moves.size());
			TraceService traces = Services.traces().forced();
			for (TraceLedger.Entry entry : moves) {
				helper.assertTrue(traces.undo(level, entry) == TraceService.UndoResult.DONE, "not undone: " + entry);
			}
			helper.assertTrue(open(level, shape) && beside.stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.BRICKS)), "the undo left it closed");
			// The same day, a new visit: the place had it today.
			rig.pyramid().entered(rig.place(), ana, 20 * 200);
			rig.pyramid().arm(ana, 20 * 200);
			rig.at(server, 20 * 200 + 20);
			helper.assertTrue(open(level, shape) && rig.answered().size() == 1, "closed twice in a day");
		} finally {
			forget(server, S16.CAUSE, shape.box());
			forget(server, TraceService.UNDO_CAUSE, shape.box());
		}
		helper.succeed();
	}

	/**
	 * R12-11, R12-24 and R12-26: a closing that waits (in view) is not done while a pet stands in the mouth, nor once the
	 * one inside long enough has left and only a newcomer is in (it waits for someone whose time inside has run); and a
	 * redstone torch is never a light 16c takes.
	 */
	@GameTest(maxTicks = 200)
	public void aWaitingClosingNeverShutsAPetInNorPassesToANewcomer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig rig = rig(level, start(level, 176_008, 163_008, Direction.EAST, PyramidShape.variant(0, false, false, false), 0xB17BL), PyramidAnswers.LIVE_LIGHTS);
		PyramidShape shape = rig.shape();
		int r = shape.halfWidth();
		ServerPlayer ana = armed(level, "ana");
		ServerPlayer ben = armed(level, "ben");
		stand(ana, shape.local(0, 0, -(r - 4)));
		stand(ben, shape.local(2, 0, -(r - 4)));
		rig.inside().add(ana);
		rig.people().add(ana);
		var wolf = net.minecraft.world.entity.EntityTypes.WOLF.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		try {
			rig.pyramid().entered(rig.place(), ana, 0);
			rig.seen().set(true);
			rig.at(server, 20 * 90);
			helper.assertTrue(open(level, shape) && rig.answers().waiting(rig.place(), "16b"), "not waiting in view");
			// A wolf sits in the mouth: out of view now, it still waits.
			wolf.setNoAi(true);
			wolf.snapTo(Vec3.atBottomCenterOf(shape.innerMouth().getFirst()), 0, 0);
			level.addFreshEntity(wolf);
			rig.seen().set(false);
			rig.at(server, 20 * 90 + 100);
			helper.assertTrue(open(level, shape) && wolf.isAlive(), "closed on the wolf");
			wolf.discard();
			// Ana goes; Ben, just in, does not inherit the closing that waited for her.
			rig.inside().remove(ana);
			rig.pyramid().left(rig.place(), ana);
			rig.inside().add(ben);
			rig.people().add(ben);
			rig.pyramid().entered(rig.place(), ben, 20 * 95);
			rig.at(server, 20 * 95 + 100);
			helper.assertTrue(open(level, shape), "closed on a newcomer by a closing that was not his");
			helper.assertFalse(PyramidAnswers.isLight(Blocks.REDSTONE_TORCH.defaultBlockState()) || PyramidAnswers.isLight(Blocks.REDSTONE_WALL_TORCH.defaultBlockState()),
					"a redstone torch is a light 16c takes");
			helper.assertTrue(PyramidAnswers.isLight(Blocks.TORCH.defaultBlockState()) && PyramidAnswers.isLight(Blocks.LANTERN.defaultBlockState()), "the lights");
		} finally {
			wolf.discard();
			forget(server, S16.CAUSE, shape.box());
		}
		helper.succeed();
	}

	/**
	 * Multiplayer: a friend outside within 16 of the mouth holds it open (it waits) until they go; on another day a friend
	 * inside without a pickaxe refuses it, and with one both are closed in together, the answer counted for both.
	 */
	@GameTest(maxTicks = 200)
	public void aFriendAtTheMouthHoldsTheWayInOpenAndTwoInsideAreClosedInTogether(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig rig = rig(level, start(level, 172_008, 163_008, Direction.NORTH, PyramidShape.variant(0, false, false, false), 0xF00DL), PyramidAnswers.LIVE_LIGHTS);
		PyramidShape shape = rig.shape();
		int r = shape.halfWidth();
		ServerPlayer ana = armed(level, "ana");
		ServerPlayer ben = armed(level, "ben");
		stand(ana, shape.local(0, 0, -(r - 4)));
		stand(ben, shape.local(1, 0, r + 4));
		rig.inside().add(ana);
		rig.people().addAll(List.of(ana, ben));
		try {
			rig.pyramid().entered(rig.place(), ana, 0);
			rig.at(server, 20 * 95);
			rig.at(server, 20 * 96);
			helper.assertTrue(open(level, shape) && rig.answers().waiting(rig.place(), "16b"), "closed on a friend at the mouth");
			stand(ben, shape.local(1, 0, r + 30));
			rig.at(server, 20 * 101);
			helper.assertTrue(closed(level, shape) && rig.answered().equals(List.of("16b:ana")), "not closed once the friend went: " + rig.answered());
			for (TraceLedger.Entry entry : TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(PyramidAnswers.CLOSE)).toList()) {
				Services.traces().forced().undo(level, entry);
			}
			helper.assertTrue(open(level, shape), "not undone");
			rig.day().set(41);
			stand(ben, shape.local(8, 0, -(r - 4)));
			rig.inside().add(ben);
			ben.getInventory().clearContent();
			rig.pyramid().entered(rig.place(), ana, 20 * 300);
			rig.pyramid().entered(rig.place(), ben, 20 * 300);
			rig.pyramid().arm(ana, 20 * 300);
			rig.at(server, 20 * 300 + 20);
			helper.assertTrue(open(level, shape), "closed with a friend inside who cannot dig");
			ben.getInventory().add(new ItemStack(Items.WOODEN_PICKAXE));
			rig.pyramid().arm(ben, 20 * 400);
			rig.at(server, 20 * 400 + 20);
			helper.assertTrue(closed(level, shape), "not closed on both");
			helper.assertTrue(rig.answered().containsAll(List.of("16b:ana", "16b:ben")) && rig.answered().size() == 3, "the events: " + rig.answered());
		} finally {
			forget(server, S16.CAUSE, shape.box());
			forget(server, TraceService.UNDO_CAUSE, shape.box());
		}
		helper.succeed();
	}

	// --- 16c ---

	/**
	 * 16c and the glimpse with three players: nothing while she is under 12 steps down or while it would be seen; then
	 * every 8 to 12 s one light a player set above her, the farthest first, taken without a drop and ledgered, at most 6
	 * a visit, a friend's light too; never a light nobody placed, never one within 3 of a friend, never a friend's last
	 * light within 6; after 4, deep in the pit's lower half, the glimpse once, on a step 14 to 20 above her.
	 */
	@GameTest(maxTicks = 200)
	public void yourLightsGoOnlyPlacedOutOfViewNeverTheLastNearAnyoneCappedThenTheGlimpse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig rig = rig(level, start(level, 174_008, 163_008, Direction.SOUTH, PyramidShape.variant(0, false, false, false), 0xDA4CL), PyramidAnswers.LIVE_LIGHTS);
		PyramidShape shape = rig.shape();
		int r = shape.halfWidth();
		int y0 = shape.baseY();
		int cx = shape.centerX();
		int cz = shape.centerZ();
		ServerPlayer ana = armed(level, "ana");
		ServerPlayer ben = armed(level, "ben");
		ServerPlayer cid = armed(level, "cid");
		BlockState torch = Blocks.TORCH.defaultBlockState();
		// Her trail down the steps (placed by her), and one of ben's on the rim.
		List<BlockPos> trail = new ArrayList<>();
		for (int steps : new int[] {1, 3, 5, 7, 9}) {
			int ring = shape.ringAtSteps(steps);
			trail.add(new BlockPos(cx - ring, y0 - steps, cz));
		}
		trail.add(shape.local(-8, 0, r - 4));
		trail.add(shape.local(8, 0, r - 4));
		BlockPos bens = shape.local(0, 0, -(r - 4));
		BlockPos nearBen = shape.local(-10, 0, -(r - 4));
		BlockPos cids = new BlockPos(cx + shape.ringAtSteps(2), y0 - 2, cz);
		BlockPos nobodys = shape.local(12, 0, r - 4);
		for (BlockPos pos : trail) {
			level.setBlock(pos, torch, FLAGS);
			Services.watch().onPlaced(ana, level, pos, torch);
		}
		for (BlockPos pos : List.of(bens, nearBen, cids)) {
			level.setBlock(pos, torch, FLAGS);
			Services.watch().onPlaced(ben, level, pos, torch);
		}
		level.setBlock(nobodys, torch, FLAGS);
		stand(ben, nearBen.offset(1, 0, 1));
		stand(cid, cids.offset(0, 0, 5));
		int deep = 22;
		stand(ana, new BlockPos(cx + shape.ringAtSteps(10), y0 - 10, cz));
		rig.inside().add(ana);
		rig.people().addAll(List.of(ana, ben, cid));
		rig.figure().allow = true;
		try {
			rig.pyramid().entered(rig.place(), ana, 0);
			for (long t = 20; t <= 20 * 40; t += 20) {
				rig.at(server, t);
			}
			helper.assertTrue(trail.stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.TORCH)), "a light taken while she was 10 steps down");
			stand(ana, new BlockPos(cx + shape.ringAtSteps(deep), y0 - deep, cz));
			rig.seen().set(true);
			for (long t = 20 * 41; t <= 20 * 70; t += 20) {
				rig.at(server, t);
			}
			helper.assertTrue(trail.stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.TORCH)), "a light taken in view");
			rig.seen().set(false);
			List<BlockPos> order = new ArrayList<>();
			List<BlockPos> watched = new ArrayList<>(trail);
			watched.add(bens);
			long first = -1;
			for (long t = 20 * 71; t <= 20 * 260; t += 20) {
				rig.at(server, t);
				for (BlockPos pos : List.copyOf(watched)) {
					if (level.getBlockState(pos).isAir()) {
						order.add(pos);
						watched.remove(pos);
						first = first < 0 ? t : first;
					}
				}
				if (order.size() < rig.config().glimpseAfter) {
					helper.assertTrue(rig.figure().glimpses.isEmpty(), "the glimpse came before " + rig.config().glimpseAfter + " lights");
				}
			}
			helper.assertTrue(order.size() == rig.config().darkPerVisit, "lights taken: " + order.size() + " " + order);
			helper.assertTrue(level.getBlockState(nobodys).is(Blocks.TORCH), "a light nobody placed was taken");
			helper.assertTrue(level.getBlockState(nearBen).is(Blocks.TORCH), "a light within 3 of ben was taken");
			helper.assertTrue(level.getBlockState(cids).is(Blocks.TORCH), "cid's last light was taken");
			helper.assertTrue(order.contains(bens), "ben's light (a friend's) was never taken: " + order);
			Vec3 at = ana.position();
			for (int i = 1; i < order.size(); i++) {
				helper.assertTrue(Vec3.atCenterOf(order.get(i - 1)).distanceTo(at) + 0.01 >= Vec3.atCenterOf(order.get(i)).distanceTo(at), "not the farthest first");
			}
			AABB around = AABB.of(shape.box());
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "a light dropped as an item");
			helper.assertTrue(TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(PyramidAnswers.DARK)).count() == order.size(),
					"not ledgered");
			helper.assertTrue(rig.answered().stream().filter(a -> a.startsWith("16c")).toList().equals(List.of("16c:ana")), "counted: " + rig.answered());
			helper.assertTrue(rig.figure().glimpses.size() == 1, "glimpses: " + rig.figure().glimpses);
			Vec3 spot = rig.figure().glimpses.getFirst();
			int up = (int) Math.round(spot.y) - (y0 - deep);
			int stepsDown = y0 - (int) Math.round(spot.y);
			helper.assertTrue(up >= PyramidAnswers.GLIMPSE_MIN && up <= PyramidAnswers.GLIMPSE_MAX
					&& shape.ring(block(spot.x), block(spot.z)) == shape.ringAtSteps(stepsDown), "the glimpse's spot: " + spot + ", " + up + " up");
		} finally {
			forget(server, S16.CAUSE, shape.box());
		}
		helper.succeed();
	}

	private static int block(double v) {
		return (int) Math.floor(v);
	}

	// --- the dig, the sea ---

	/** A block of a closed one's mass broken: marked once a place; not outside its mass, not for one with a way in. */
	@GameTest
	public void aClosedOneDugIntoIsMarkedOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Start closed = new Start(new BlockPos(175_008, 70, 165_008), Direction.WEST, PyramidShape.variant(0, true, false, false), 5L);
		Start open = new Start(new BlockPos(175_008, 70, 167_008), Direction.WEST, PyramidShape.variant(0, false, false, false), 6L);
		Sc2Data data = new Sc2Data();
		for (Start s : List.of(closed, open)) {
			PyramidShape shape = shape(level, s);
			Remains.Place place = new Remains.Place(Remains.Kind.PYRAMID, level.dimension(), shape.box(), shape.anchor(), shape.entrance(), shape.entranceFacing(),
					shape.apex(), s.seed(), s.variant());
			data.add(Sc2Data.Known.of(place, S16.ID, s, shape.extra()));
		}
		RemainsImpl remains = RemainsImpl.over(data);
		BlockPos mass = closed.origin().offset(10, 5, 0);
		BlockPos above = closed.origin().offset(30, 5, 0);
		helper.assertFalse(S16.dug(level, above, remains, data, place -> shape(level, closed)), "outside the mass");
		helper.assertTrue(S16.dug(level, mass, remains, data, place -> shape(level, closed)), "not marked");
		helper.assertFalse(S16.dug(level, mass.above(), remains, data, place -> shape(level, closed)), "marked twice");
		helper.assertFalse(S16.dug(level, open.origin().offset(10, 5, 0), remains, data, place -> shape(level, open)), "one with a way in");
		helper.succeed();
	}

	/**
	 * The sea form, built in a basin of water: closed (no way in), no pit, the core dry, and still dry once the water has
	 * had time to run; the water round it stays water; its apex over the water.
	 */
	@GameTest(maxTicks = 200)
	public void theSeaFormIsClosedWithoutAPitAndStaysDry(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 175_008;
		int z = 172_008;
		int ground = Terrains.noise(level).ground(x, z);
		int floor = ground + 20;
		int water = floor + 18;
		int r = PyramidShape.halfWidth(0);
		BoundingBox basin = new BoundingBox(x - r - 8, floor, z - r - 8, x + r + 8, water, z + r + 8);
		loadChunks(level, basin.inflatedBy(PyramidShape.GROUNDS_HALF + 2));
		for (BlockPos pos : BlockPos.betweenClosed(basin.minX(), ground + 1, basin.minZ(), basin.maxX(), water, basin.maxZ())) {
			level.setBlock(pos, pos.getY() <= floor ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState(), FLAGS);
		}
		Start start = new Start(new BlockPos(x, floor + 1, z), Direction.EAST, PyramidShape.variant(0, true, false, true), 0x5EAL);
		Live.Result built = Live.place(level, S16.SEA_KIND, start);
		PyramidShape shape = (PyramidShape) built.shape();
		helper.assertTrue(!shape.hasPit() && !shape.hasWayIn() && shape.wayIn().isEmpty() && shape.sea(), "the sea form's parts");
		helper.assertTrue(shape.apex().orElseThrow().getY() >= water + 12, "the apex is not over the water");
		// Every water cell touching the mass is made to flow now: a hole would let it in.
		int ticked = 0;
		for (BlockPos pos : BlockPos.betweenClosed(x - r - 1, floor + 1, z - r - 1, x + r + 1, water, z + r + 1)) {
			if (!level.getBlockState(pos).is(Blocks.WATER)) {
				continue;
			}
			for (Direction side : Direction.values()) {
				if (!level.getFluidState(pos.relative(side)).is(Fluids.WATER)) {
					level.scheduleTick(pos.immutable(), Fluids.WATER, 1);
					ticked++;
					break;
				}
			}
		}
		helper.assertTrue(ticked > 1000, "only " + ticked + " water cells touch it");
		helper.runAfterDelay(60, () -> {
			List<String> wet = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(x - r, floor + 1, z - r, x + r, floor + r, z + r)) {
				if (shape.core(pos.getX(), pos.getY(), pos.getZ()) && !level.getBlockState(pos).isAir() && wet.size() < 8) {
					wet.add(pos.toShortString() + " " + level.getBlockState(pos));
				}
			}
			helper.assertTrue(wet.isEmpty(), "the core is not dry: " + wet);
			helper.assertTrue(level.getBlockState(new BlockPos(x + r + 2, floor + 3, z)).is(Blocks.WATER), "the sea round it");
			helper.assertFalse(shape.interior().contains(new BlockPos(x, floor - 2, z)), "a pit at sea");
			helper.succeed();
		});
	}

	// --- cost ---

	/**
	 * The cost of each size per chunk on worldgen's own path (D-104: under 4 ms median, 15 worst on the dev Mac), over
	 * solid rock under the whole box as a real world has. Only with {@code -Da1016_02.s16cost=true} (it takes a while and
	 * a busy machine says little); prints the numbers.
	 */
	@GameTest(environment = ENV, maxTicks = 400)
	public void costPerChunkOfEachSize(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.s16cost")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S16_KEY);
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		StringBuilder report = new StringBuilder();
		for (int size = 0; size < PyramidShape.BASES.length; size++) {
			int x = 163_008 + size * 400;
			int z = 175_008;
			int ground = Terrains.noise(level).ground(x, z);
			Start start = new Start(new BlockPos(x, ground + 66, z), Direction.WEST, PyramidShape.variant(size, false, true, false), 77L + size);
			StructureStart made = Live.startOf(level, holder, start);
			BoundingBox box = made.getBoundingBox();
			loadChunks(level, box);
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), start.origin().getY() - 1, box.maxZ())) {
				level.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS);
			}
			List<Double> ms = new ArrayList<>();
			List<LevelChunk> chunks = new ArrayList<>();
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					ChunkPos chunk = new ChunkPos(cx, cz);
					LevelChunk levelChunk = level.getChunk(cx, cz);
					chunks.add(levelChunk);
					BoundingBox writable = new BoundingBox(chunk.getMinBlockX(), level.getMinY() + 1, chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(),
							chunk.getMaxBlockZ());
					WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(level.getSeed()));
					random.setDecorationSeed(level.getSeed(), chunk.getMinBlockX(), chunk.getMinBlockZ());
					long t0 = System.nanoTime();
					made.placeInChunk(level, level.structureManager(), generator, random, writable, chunk);
					ms.add((System.nanoTime() - t0) / 1_000_000.0);
				}
			}
			for (LevelChunk chunk : chunks) {
				chunk.postProcessGeneration(level);
			}
			Collections.sort(ms);
			report.append(String.format(Locale.ROOT, "base %d: %d chunks, median %.2f ms, worst %.2f ms; ", PyramidShape.BASES[size], ms.size(),
					ms.get(ms.size() / 2), ms.getLast()));
		}
		System.out.println("[a1016] s16 cost per chunk: " + report);
		helper.succeed();
	}
}
