package com.forzacode.a1016_02.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntBinaryOperator;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Recorder;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Piece;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Seal;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.s18.HollowAnswers;
import com.forzacode.a1016_02.world.sc2.s18.HollowShape;
import com.forzacode.a1016_02.world.sc2.s18.HollowSiting;
import com.forzacode.a1016_02.world.sc2.s18.S18;
import com.forzacode.a1016_02.world.sc2.s18.Sc18Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import com.forzacode.a1016_02.core.GalleryProps;

/**
 * SC2-5, feature 18 (docs/SELF_CREATED_2.md): the siting (the kit's gates, the biome first, the floor from the side
 * facing the origin, the rise, the shape that must hold a square and a way in, the world's bottom and top kept clear, a
 * short custom height), the shape built in the world on a mountain of the test's own (the inside taken, the shell whole:
 * nothing reaches the open but the way in and the opening; the floor even, so nothing drops; the seal round a cave,
 * water, lava and gravel; the bare stems on their square; the columns and the empty nave; the opening's shaft, its pool
 * and the daylight on it), the inside and the index, worldgen equal to {@link Live#place} and recorded once, 18b (only
 * with a pickaxe on everyone inside, the health, the distance, nobody at either mouth, out of view, the caps; undone), its
 * multiplayer rules (a friend at the mouth, two closed in, one without a pickaxe refuses), the shell over a real mountain
 * of the real noise and, when {@code -Da1016_02.s18cost=true}, the cost per chunk there. Each test builds at its own
 * far-off spot (233 000 to 247 000 on x, -233 000 to -247 000 on z).
 */
public class SelfCreated2HollowTests {
	private static final String ENV = "a1016_02:world/s18";
	private static final ResourceKey<Structure> S18_KEY = ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/s18"));
	private static final ResourceKey<StructureSet> SET_KEY = ResourceKey.create(Registries.STRUCTURE_SET, A1016_02.id("world/s18"));
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | 512;
	/** The seed of the real noise the real mountain is read from (the same as the sting's tests: stony and jagged peaks near). */
	private static final long REAL_SEED = -4180345853331630785L;

	// --- helpers ---

	/** A terrain of the test's own: the ground by column (dry), one biome. */
	private static Terrain terrain(ServerLevel level, IntBinaryOperator ground, ResourceKey<Biome> biomeKey, int seaLevel) {
		Holder<Biome> biome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biomeKey);
		return new Terrain() {
			@Override
			public int seaLevel() {
				return seaLevel;
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
				return ground.applyAsInt(x, z);
			}

			@Override
			public Holder<Biome> biome(int x, int y, int z) {
				return biome;
			}

			@Override
			public BlockState block(int x, int y, int z) {
				return y <= ground.applyAsInt(x, z) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
			}
		};
	}

	/**
	 * A mountain's height over its foot at ({@code dx}, {@code dz}) from its middle: a rounded massif {@code height} high,
	 * its peak {@code back} behind the middle (away from the facing, so the side the way in faces comes down), {@code
	 * radius} out; with {@code cliff} a sheer side to the right of the facing, 30 out, down to a third of it.
	 */
	static int mountain(int dx, int dz, Direction facing, int height, int radius, int back, boolean cliff) {
		Direction right = facing.getClockWise();
		double u = dx * facing.getStepX() + dz * facing.getStepZ() + back;
		double v = dx * right.getStepX() + dz * right.getStepZ();
		double d2 = (u * u + v * v) / ((double) radius * radius);
		double h = d2 >= 1 ? 0 : height * Math.pow(1 - d2, 1.3);
		if (cliff && v > 30) {
			h *= 0.32;
		}
		return (int) Math.round(h);
	}

	private static Snapshot snapshot(boolean on, Stage stage, Sc18Config config) {
		return new Snapshot(on, stage, BlockPos.ZERO, 1L, 7L, Map.of(S18.ID, config));
	}

	private static Optional<Start> find(ChunkPos chunk, Terrain terrain, Sc18Config config, int minY, int maxY) {
		return HollowSiting.INSTANCE.find(new Siting.Query(chunk, terrain, snapshot(true, Stage.TRACES, config), 99L, RandomSource.create(1L), minY, maxY));
	}

	private static void loadChunks(ServerLevel level, BoundingBox box) {
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}

	/** A built mountain of the test's own: its middle, the flat ground under it, and its top by column. */
	record Hill(BlockPos middle, int foot, IntBinaryOperator top) {
	}

	/**
	 * Builds a mountain in the flat test world at ({@code x}, {@code z}): stone with a few granite and gravel cells near
	 * where the inside will be, three dirt under grass on top. The chunks round it are loaded first (Live builds over
	 * what the level has).
	 */
	static Hill hill(ServerLevel level, int x, int z, Direction facing, int height, int radius, int back, boolean cliff) {
		int foot = Terrains.noise(level).ground(x, z);
		int reach = HollowShape.EXTENT + 8;
		loadChunks(level, new BoundingBox(x - reach, foot, z - reach, x + reach, foot + height, z + reach));
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -radius - back; dx <= radius + back; dx++) {
			for (int dz = -radius - back; dz <= radius + back; dz++) {
				int h = mountain(dx, dz, facing, height, radius, back, cliff);
				if (h <= 0) {
					continue;
				}
				for (int y = foot + 1; y <= foot + h; y++) {
					int fromTop = foot + h - y;
					BlockState state = fromTop == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : fromTop <= 3 ? Blocks.DIRT.defaultBlockState()
							: Math.floorMod(dx * 7 + y * 3 + dz * 5, 97) == 0 ? Blocks.GRANITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
					level.setBlock(at.set(x + dx, y, z + dz), state, FLAGS);
				}
			}
		}
		IntBinaryOperator top = (ax, az) -> foot + mountain(ax - x, az - z, facing, height, radius, back, cliff);
		return new Hill(new BlockPos(x, foot, z), foot, top);
	}

	/** The start for a hill: the floor two over its foot, as siting would put it on flat land round a mountain. */
	private static Start start(Hill hill, Direction facing, boolean window, long seed) {
		Sc18Config config = new Sc18Config();
		int variant = HollowShape.variant(window, true, config.shellTop, config.shellSide, config.pillarEvery, 11);
		// Round 2's shape (R12-2): version 3 (R3-3) is tested in SelfCreated3HollowFloorTests.
		return new Start(hill.middle().above(2), facing, variant, seed).withVersion(2);
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

	private static void forget(MinecraftServer server, String cause, BoundingBox box) {
		TraceLedger ledger = TraceLedger.get(server);
		for (TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
			if (entry.cause().startsWith(cause) && box.isInside(entry.pos().pos())) {
				ledger.remove(entry);
			}
		}
	}

	/** A cell where something stands inside, 2 high, on the floor: {@code a} across and {@code b} along from the square. */
	private static BlockPos feet(HollowShape shape, int a, int b) {
		return shape.at(a, shape.floorY() + 1, b);
	}

	// --- siting ---

	/**
	 * The kit's gates (no snapshot, off, under Traces, near the origin), then the kind's own siting over terrains of the
	 * test's own: not in a biome off the list (checked first), not on flat land (the rise), on a mountain the floor two
	 * over the lowest sample of the side facing the origin and the way in toward it, the wood by the biome, the chances,
	 * the stems' count; the factory takes siting's own shape; the floor at least a block over the sea; the box kept 6 over
	 * the world's bottom and 8 under its top, else no start (never clipped), on a short custom height too; the biomes and
	 * the set's spacing.
	 */
	@GameTest
	public void sitingKeepsTheGatesTheBiomeTheRiseTheFloorAndTheWorldsHeight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc18Config config = new Sc18Config();
		ChunkPos far = new ChunkPos(100, 0);
		ChunkPos near = new ChunkPos(10, 0);
		helper.assertTrue("no snapshot".equals(Snapshot.refusal(null, S18.KIND, far)), "no snapshot");
		helper.assertTrue("switched off".equals(Snapshot.refusal(snapshot(false, Stage.TRACES, config), S18.KIND, far)), "off");
		// Round 3 (D-108): no stage gate by default; a stage raised by hand is still honoured.
		helper.assertTrue(Snapshot.refusal(snapshot(true, Stage.ALONE, config), S18.KIND, far) == null, "refused in Alone");
		config.fromStage = 1;
		helper.assertTrue(String.valueOf(Snapshot.refusal(snapshot(true, Stage.ALONE, config), S18.KIND, far)).contains("stage"), "under a raised stage");
		config.fromStage = 0;
		helper.assertTrue(String.valueOf(Snapshot.refusal(snapshot(true, Stage.TRACES, config), S18.KIND, near)).contains("origin"), "near the origin");
		helper.assertTrue(Snapshot.refusal(snapshot(true, Stage.TRACES, config), S18.KIND, far) == null, "far, at Traces");

		int cx = far.getMiddleBlockX();
		int cz = far.getMiddleBlockZ();
		Direction facing = Siting.facingToOrigin(cx, cz);
		helper.assertTrue(facing == Direction.WEST, "the facing: " + facing);
		IntBinaryOperator peak = (x, z) -> 70 + mountain(x - cx, z - cz, facing, 110, 100, 20, false);
		helper.assertTrue(find(far, terrain(level, peak, Biomes.DESERT, 63), config, -64, 319).isEmpty(), "a start in a desert");
		helper.assertTrue(find(far, terrain(level, (x, z) -> 70, Biomes.JAGGED_PEAKS, 63), config, -64, 319).isEmpty(), "a start on flat land");
		config.highWindowChance = 0;
		Terrain jagged = terrain(level, peak, Biomes.JAGGED_PEAKS, 63);
		Optional<Start> found = find(far, jagged, config, -64, 319);
		helper.assertTrue(found.isPresent(), "nothing on a mountain");
		Start s = found.get();
		int[] grid = HollowSiting.grid(jagged, cx, cz, jagged.ground(cx, cz));
		helper.assertTrue(s.origin().equals(new BlockPos(cx, HollowSiting.floor(grid, facing, 63), cz)) && s.origin().getY() == 72 && s.facing() == Direction.WEST,
				"the start: " + s);
		helper.assertTrue(HollowShape.spruce(s.variant()) && !HollowShape.window(s.variant()) && HollowShape.shellTop(s.variant()) == config.shellTop
				&& HollowShape.shellSide(s.variant()) == config.shellSide && HollowShape.pillarEvery(s.variant()) == config.pillarEvery
				&& HollowShape.stems(s.variant()) >= config.trunksMin && HollowShape.stems(s.variant()) <= config.trunksMax, "the variant: " + s.variant());
		// Siting's own shape goes to the factory once (the next ask makes a new one, the same).
		HollowShape made = (HollowShape) HollowSiting.create(s, jagged);
		HollowShape again = (HollowShape) HollowSiting.create(s, jagged);
		helper.assertTrue(made != again && made.valid() && made.start().equals(s) && made.entranceFacing() == Direction.WEST && again.box().equals(made.box())
				&& again.entrance().equals(made.entrance()), "the shape: " + made.start());
		config.highWindowChance = 1;
		Start withOpening = find(far, terrain(level, peak, Biomes.MEADOW, 63), config, -64, 319).orElseThrow();
		helper.assertTrue(HollowShape.window(withOpening.variant()) && !HollowShape.spruce(withOpening.variant()), "the chance at 1, oak in a meadow");
		config.highWindowChance = 0;
		// The floor is at least a block over the sea: a lower land on the side facing the origin is not dug under it.
		IntBinaryOperator low = (x, z) -> x < cx - 50 ? 40 : peak.applyAsInt(x, z);
		Optional<Start> sea = find(far, terrain(level, low, Biomes.JAGGED_PEAKS, 63), config, -64, 319);
		helper.assertTrue(sea.isEmpty() || sea.get().origin().getY() == 64, "under the sea: " + sea);
		// The world's height: the same mountain in a world whose bottom is 10 under its floor, or whose top is just over it.
		int bottom = s.origin().getY() - 10;
		helper.assertTrue(find(far, jagged, config, bottom, 319).isEmpty(), "a box into the world's bottom");
		BoundingBox box = made.box();
		helper.assertTrue(find(far, jagged, config, -64, box.maxY() + HollowSiting.TOP_MARGIN - 1).isEmpty(), "a box past the world's top");
		helper.assertTrue(find(far, jagged, config, -64, box.maxY() + HollowSiting.TOP_MARGIN).isPresent(), "the top's margin");
		helper.assertTrue(find(far, jagged, config, box.minY() - HollowSiting.BOTTOM_MARGIN, 319).isPresent(), "the bottom's margin");
		// A short custom height (a world from 0 to 255) with the mountain inside it.
		helper.assertTrue(find(far, jagged, config, 0, 255).isPresent(), "a short world");
		IntBinaryOperator tall = (x, z) -> 120 + mountain(x - cx, z - cz, facing, 150, 100, 20, false);
		helper.assertTrue(find(far, terrain(level, tall, Biomes.JAGGED_PEAKS, 63), config, 0, 255).isEmpty(), "a mountain to the top of a short world");
		helper.assertTrue(HollowSiting.withinHeight(new BoundingBox(0, -58, 0, 1, 311, 1), -64, 319)
				&& !HollowSiting.withinHeight(new BoundingBox(0, -59, 0, 1, 311, 1), -64, 319)
				&& !HollowSiting.withinHeight(new BoundingBox(0, -58, 0, 1, 312, 1), -64, 319), "the margins");

		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S18_KEY);
		var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		for (ResourceKey<Biome> key : List.of(Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.STONY_PEAKS, Biomes.SNOWY_SLOPES, Biomes.GROVE, Biomes.WINDSWEPT_HILLS,
				Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WOODED_BADLANDS, Biomes.MEADOW)) {
			helper.assertTrue(structure.value().biomes().contains(biomes.getOrThrow(key)), "not in " + key.identifier());
		}
		for (ResourceKey<Biome> key : List.of(Biomes.DESERT, Biomes.SWAMP, Biomes.OCEAN, Biomes.RIVER, Biomes.DEEP_DARK, Biomes.MUSHROOM_FIELDS)) {
			helper.assertFalse(structure.value().biomes().contains(biomes.getOrThrow(key)), "in " + key.identifier());
		}
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(SET_KEY).value();
		helper.assertTrue(set.placement() instanceof RandomSpreadStructurePlacement spread && spread.spacing() == 14 && spread.separation() == 5,
				"the set's spread: " + set.placement());
		helper.succeed();
	}

	// --- the shape ---

	/**
	 * A mountain built in the world with leaks planted where the inside will reach (a cave pocket and gravel over its
	 * ceiling, water and lava beside its wall): the inside is taken; nothing of it reaches the open air but through the way
	 * in (a search through every open cell from the square); the floor is even everywhere (no drop at all, fair danger 7);
	 * nothing leaks within 2 of it; the stems stand bare on their square; columns stand, none in the nave; the way in is a
	 * 2 x 2 on a floor with rock over its mouth; the inside and the index (its recorded extra) hold the inside and nothing
	 * else.
	 */
	@GameTest(maxTicks = 400)
	public void theShapeKeepsItsShellFloorSealSquareColumnsAndWayIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Direction facing = Direction.WEST;
		Hill hill = hill(level, 233_008, -233_008, facing, 76, 82, 14, false);
		Start start = start(hill, facing, false, 0x18A01L);
		HollowShape planned = new HollowShape(start, Live.terrain(level));
		helper.assertTrue(planned.valid(), "no square or no way in on the test mountain");
		int yf = planned.floorY();
		BlockPos square = planned.groveCenter();
		// Leaks where the inside will reach: over the ceiling over the square, and beside a wall 20 out to the side.
		long over = planned.column(square.getX() + 9, square.getZ() + 9);
		BlockPos gravel = new BlockPos(square.getX() + 9, Seal.hi(over) + 1, square.getZ() + 9);
		BlockPos cave = gravel.above();
		List<BlockPos> leaks = new ArrayList<>(List.of(cave, gravel));
		level.setBlock(cave, Blocks.AIR.defaultBlockState(), FLAGS);
		level.setBlock(gravel, Blocks.GRAVEL.defaultBlockState(), FLAGS);
		for (Direction side : List.of(facing.getClockWise(), facing.getCounterClockWise())) {
			BlockPos at = square.atY(yf + 4);
			while (planned.taken(at.getX(), at.getY(), at.getZ())) {
				at = at.relative(side);
			}
			BlockState fluid = side == facing.getClockWise() ? Blocks.WATER.defaultBlockState() : Blocks.LAVA.defaultBlockState();
			level.setBlock(at.relative(side), fluid, FLAGS);
			leaks.add(at.relative(side));
		}
		Live.Result built = Live.place(level, S18.KIND, start);
		HollowShape shape = (HollowShape) built.shape();
		helper.assertTrue(built.box().equals(planned.box()) && shape.groveCenter().equals(square), "the shape changed when built");
		BoundingBox box = shape.box();
		List<String> wrong = new ArrayList<>();
		// The inside is taken (but the stems on the square), the floor under it whole and even.
		Set<Long> stems = new HashSet<>();
		for (HollowShape.Stem stem : shape.stems()) {
			BlockPos foot = shape.stemFoot(stem);
			for (int up = 0; up < stem.height(); up++) {
				stems.add(foot.above(up).asLong());
			}
		}
		int columns = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				long range = shape.column(x, z);
				if (range == Seal.NONE) {
					continue;
				}
				columns++;
				if (Seal.lo(range) != yf + 1 && Seal.lo(range) != yf + 2 && wrong.size() < 10) {
					wrong.add("a floor at " + Seal.lo(range) + " at " + x + " " + z);
				}
				BlockState floor = level.getBlockState(new BlockPos(x, Seal.lo(range) - 1, z));
				if (!floor.isSolid() && floor.getFluidState().isEmpty() && wrong.size() < 10) {
					wrong.add("no floor at " + x + " " + z + ": " + floor);
				}
				if ((x + z) % 3 != 0) {
					continue;
				}
				for (int y = Seal.lo(range); y <= Seal.hi(range); y++) {
					BlockState state = level.getBlockState(new BlockPos(x, y, z));
					if (!state.isAir() && !stems.contains(BlockPos.asLong(x, y, z)) && !state.is(BlockTags.LOGS) && !state.is(Blocks.DEAD_BUSH) && wrong.size() < 10) {
						wrong.add("not taken at " + x + " " + y + " " + z + ": " + state);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the inside: " + wrong);
		helper.assertTrue(columns > 6_000, "only " + columns + " columns taken");
		helper.assertTrue(shape.maxTop() - yf > 50, "only " + (shape.maxTop() - yf) + " high");
		// Even: every taken column's floor within a block of its neighbours' (a column's foot is the one step up).
		for (int x = box.minX(); x < box.maxX(); x++) {
			for (int z = box.minZ(); z < box.maxZ(); z++) {
				long here = shape.column(x, z);
				long east = shape.column(x + 1, z);
				long south = shape.column(x, z + 1);
				if (here != Seal.NONE && (east != Seal.NONE && Math.abs(Seal.lo(here) - Seal.lo(east)) > 1 || south != Seal.NONE
						&& Math.abs(Seal.lo(here) - Seal.lo(south)) > 1) && wrong.size() < 10) {
					wrong.add("a drop at " + x + " " + z);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the floor: " + wrong);
		// The seal: every planted leak is stone now, and nothing within 2 over the ceilings or round the walls leaks.
		for (BlockPos leak : leaks) {
			BlockState state = level.getBlockState(leak);
			helper.assertTrue(state.is(Blocks.STONE), "the seal left " + state + " at " + leak.toShortString());
		}
		for (int x = box.minX(); x <= box.maxX(); x += 2) {
			for (int z = box.minZ(); z <= box.maxZ(); z += 2) {
				long range = shape.column(x, z);
				if (range == Seal.NONE) {
					continue;
				}
				for (int up = 1; up <= 2; up++) {
					BlockPos pos = new BlockPos(x, Seal.hi(range) + up, z);
					BlockState state = level.getBlockState(pos);
					if (!shape.taken(x, pos.getY(), z) && (!state.getFluidState().isEmpty() || state.getBlock() instanceof FallingBlock || state.isAir())
							&& wrong.size() < 10) {
						wrong.add("over the ceiling at " + pos.toShortString() + ": " + state);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the seal: " + wrong);
		// The shell: from the square, through every open cell, the open air is reached only through the way in.
		String escaped = escape(level, shape, hill, Set.copyOf(shape.wayIn()));
		helper.assertTrue(escaped == null, "the inside reaches the open: " + escaped);
		// The square and its bare stems.
		for (int a = -HollowShape.GROVE; a <= HollowShape.GROVE; a++) {
			for (int b = -HollowShape.GROVE; b <= HollowShape.GROVE; b++) {
				BlockState ground = level.getBlockState(shape.at(a, yf, b));
				if (!(ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.PODZOL) || ground.is(Blocks.ROOTED_DIRT)) && wrong.size() < 10) {
					wrong.add("the square at " + a + " " + b + ": " + ground);
				}
			}
		}
		helper.assertTrue(shape.stems().size() == 11, "stems: " + shape.stems().size());
		for (HollowShape.Stem stem : shape.stems()) {
			BlockPos foot = shape.stemFoot(stem);
			helper.assertTrue(level.getBlockState(foot).is(BlockTags.LOGS) && level.getBlockState(foot.above(stem.height() - 1)).is(BlockTags.LOGS),
					"a stem at " + foot.toShortString());
		}
		for (BlockPos pos : BlockPos.betweenClosed(square.offset(-10, 1, -10), square.offset(10, 20, 10))) {
			if (level.getBlockState(pos).is(BlockTags.LEAVES) && wrong.size() < 10) {
				wrong.add("leaves at " + pos.toShortString());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the square: " + wrong);
		// Columns stand (solid from the floor up), none in the nave between the square and the way in.
		int pillars = 0;
		BlockPos pillar = null;
		int every = HollowShape.pillarEvery(start.variant());
		for (int ka = -4; ka <= 4; ka++) {
			for (int kb = -4; kb <= 4; kb++) {
				BlockPos c = shape.at(every / 2 + ka * every, yf + 3, every / 2 + kb * every);
				boolean around = shape.taken(c.getX() + 5, yf + 3, c.getZ()) || shape.taken(c.getX() - 5, yf + 3, c.getZ());
				if (around && shape.column(c.getX(), c.getZ()) == Seal.NONE && level.getBlockState(c).isSolid()) {
					pillars++;
					pillar = c;
				}
			}
		}
		helper.assertTrue(pillars >= 4, "only " + pillars + " columns");
		List<BlockPos> mouth = shape.innerMouth();
		int inner = shape.along(mouth.getFirst().getX(), mouth.getFirst().getZ());
		for (int b = HollowShape.GROVE + 1; b < inner; b++) {
			for (int a = -3; a <= 4; a++) {
				BlockPos p = shape.at(a, yf + 3, b);
				if (!level.getBlockState(p).isAir() && wrong.size() < 10) {
					wrong.add("the nave at " + a + " " + b + ": " + level.getBlockState(p));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the nave: " + wrong);
		// The way in: open 2 x 2 on a floor, its mouth toward the facing with rock over it.
		for (BlockPos cell : shape.wayIn()) {
			helper.assertTrue(level.getBlockState(cell).isAir(), "the way in at " + cell.toShortString());
			if (cell.getY() == yf + 1) {
				helper.assertTrue(level.getBlockState(cell.below()).isSolid(), "no floor under the way in at " + cell.toShortString());
			}
		}
		BlockPos entrance = shape.entrance();
		helper.assertTrue(shape.entranceFacing() == facing && level.getBlockState(entrance.above(2)).isSolid(), "the mouth at " + entrance.toShortString());
		helper.assertTrue(shape.tunnelLength() >= 2 && shape.tunnelLength() <= HollowShape.TUNNEL_MAX, "the tunnel: " + shape.tunnelLength());
		for (BlockPos floor : shape.approach()) {
			helper.assertTrue(level.getBlockState(floor).isSolid() && level.getBlockState(floor.above()).isAir() && level.getBlockState(floor.above(2)).isAir(),
					"the way out at " + floor.toShortString());
		}
		// The inside: the open cells, never the rock, a column, the way in or the shell over it.
		Interior interior = shape.interior();
		BlockPos high = square.atY(yf + 20);
		helper.assertTrue(interior.contains(high) && interior.contains(feet(shape, shape.lane(), inner - 2)), "the inside");
		long ceiling = shape.column(square.getX() + 12, square.getZ() + 12);
		helper.assertFalse(interior.contains(new BlockPos(square.getX() + 12, Seal.hi(ceiling) + 2, square.getZ() + 12)), "the shell");
		helper.assertFalse(interior.contains(pillar), "a column at " + pillar.toShortString());
		for (BlockPos cell : shape.wayIn()) {
			helper.assertFalse(interior.contains(cell), "the way in at " + cell.toShortString());
		}
		// The index, from the record's extra (no terrain): the open cells in, the open air and the floor out.
		Sc2Data data = new Sc2Data();
		data.add(built.known());
		RemainsImpl remains = RemainsImpl.over(data);
		helper.assertTrue(remains.containing(level, high).isPresent() && remains.containing(level, feet(shape, 3, -9)).isPresent(), "the index: not inside");
		helper.assertTrue(remains.containing(level, square.atY(yf)).isEmpty() && remains.containing(level, new BlockPos(square.getX(), shape.maxTop() + 6,
				square.getZ())).isEmpty() && remains.containing(level, entrance.relative(facing, 20)).isEmpty(), "the index: inside where it is not");
		helper.assertTrue(HollowShape.innerMouthOf(built.known().extra()).orElseThrow().equals(mouth.getFirst()), "the mouth in the extra");
		helper.succeed();
	}

	/**
	 * Searches every open cell from inside (the square's middle, 20 up), through air, water and plants, never through
	 * {@code walls} (the way in, the opening); answers where it reached the open land (over the hill's own top), or null.
	 */
	static String escape(ServerLevel level, HollowShape shape, Hill hill, Set<BlockPos> walls) {
		BoundingBox box = shape.box().inflatedBy(1);
		int sx = box.getXSpan();
		int sy = box.getYSpan();
		int sz = box.getZSpan();
		BitSet seen = new BitSet(sx * sy * sz);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos from = shape.groveCenter().above(20);
		queue.add(from);
		seen.set(((from.getX() - box.minX()) * sy + from.getY() - box.minY()) * sz + from.getZ() - box.minZ());
		int visited = 0;
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			visited++;
			if (pos.getY() > hill.top().applyAsInt(pos.getX(), pos.getZ())) {
				return pos.toShortString() + " after " + visited;
			}
			for (Direction d : Direction.values()) {
				BlockPos next = pos.relative(d);
				if (!box.isInside(next) || walls.contains(next)) {
					continue;
				}
				int index = ((next.getX() - box.minX()) * sy + next.getY() - box.minY()) * sz + next.getZ() - box.minZ();
				if (seen.get(index)) {
					continue;
				}
				seen.set(index);
				BlockState state = level.getBlockState(next);
				if (state.getCollisionShape(level, next).isEmpty()) {
					queue.add(next);
				}
			}
		}
		return visited < 10_000 ? "only " + visited + " open cells inside" : null;
	}

	/**
	 * The high opening, on a mountain with a sheer side: a 3 x 3 shaft straight up from the inside to the land's top, the
	 * pool two deep under it on the floor's height, the daylight on the pool (and the sky light reaches the floor there,
	 * nowhere else away from the way in); nothing else of the inside reaches the open.
	 */
	@GameTest(maxTicks = 400)
	public void theHighOpeningIsAShaftOfDaylightOntoAPool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Direction facing = Direction.NORTH;
		Hill hill = hill(level, 237_008, -237_008, facing, 84, 86, 14, true);
		Start start = start(hill, facing, true, 0x18A02L);
		Live.Result built = Live.place(level, S18.KIND, start);
		HollowShape shape = (HollowShape) built.shape();
		helper.assertTrue(shape.valid() && shape.hasWindow(), "no opening on the test mountain");
		List<BlockPos> cells = shape.windowCells();
		for (BlockPos cell : cells) {
			helper.assertTrue(level.getBlockState(cell).isAir(), "the opening at " + cell.toShortString());
		}
		BlockPos foot = shape.windowFoot();
		BlockPos outer = shape.windowOuter();
		helper.assertTrue(foot != null && outer != null && outer.getY() - shape.floorY() >= HollowShape.WINDOW_MIN_UP, "the opening's height");
		helper.assertTrue(level.getBlockState(outer.above(2)).isAir() && outer.getY() >= hill.top().applyAsInt(outer.getX(), outer.getZ()) - 1, "the opening's top");
		int yf = shape.floorY();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				BlockPos water = new BlockPos(foot.getX() + a, yf, foot.getZ() + b);
				helper.assertTrue(level.getBlockState(water).is(Blocks.WATER) && level.getBlockState(water.below()).is(Blocks.WATER)
						&& level.getBlockState(water.below(2)).isSolid(), "the pool at " + water.toShortString());
			}
		}
		Live.settleLight(level, shape.box());
		helper.assertTrue(level.getBrightness(LightLayer.SKY, foot) == 15, "no daylight on the pool: " + level.getBrightness(LightLayer.SKY, foot));
		Set<BlockPos> walls = new HashSet<>(shape.wayIn());
		walls.addAll(cells);
		String escaped = escape(level, shape, hill, walls);
		helper.assertTrue(escaped == null, "the inside reaches the open: " + escaped);
		helper.succeed();
	}

	/**
	 * R12-1, R11-8 and R12-2: the landing under the high opening in a cold biome. Version 2 over frozen peaks: the opening
	 * 5 x 5, its landing a drift of powder snow on a floor of plain stone over 7 x 7; worldgen's own freeze step
	 * ({@code freeze_top_layer}) leaves it as it is, the weather can neither freeze it nor snow on it; and a cow stepping into
	 * the opening from the mountain falls the whole way and lands unhurt. The same start made with version 1 (a place half
	 * built before the change) is still a pool of water, which the freeze step turns to ice and a cow dies on: what the
	 * review found, and why a start keeps its version.
	 */
	@GameTest(maxTicks = 1200)
	public void aColdLandingIsPowderSnowThatNeverFreezesAndTakesTheFall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Direction facing = Direction.NORTH;
		Hill a = hill(level, 249_008, -249_008, facing, 84, 86, 14, true);
		Hill b = hill(level, 249_408, -249_008, facing, 84, 86, 14, true);
		Start now = start(a, facing, true, 0x18A02L);
		Start old = start(b, facing, true, 0x18A02L).withVersion(1);
		HollowShape shape = (HollowShape) Live.place(level, S18.KIND, cold(level, now)).shape();
		HollowShape first = (HollowShape) Live.place(level, S18.KIND, cold(level, old)).shape();
		helper.assertTrue(shape.hasWindow() && first.hasWindow(), "no opening on the test mountains");
		helper.assertTrue(shape.version() == 2 && shape.windowHalf() == 2 && shape.pool().size() == 49 && first.version() == 1 && first.windowHalf() == 1,
				"the versions' openings: " + shape.windowHalf() + " / " + first.windowHalf() + ", landing " + shape.pool().size());
		helper.assertTrue(shape.snowLanding() && !first.snowLanding(), "the cold landing: " + shape.snowLanding() + " / " + first.snowLanding());
		int yf = shape.floorY();
		for (BlockPos cell : shape.pool()) {
			helper.assertTrue(level.getBlockState(cell).is(Blocks.STONE) && level.getBlockState(cell.above()).is(Blocks.POWDER_SNOW),
					"the drift at " + cell.toShortString() + ": " + level.getBlockState(cell.above()));
		}
		for (BlockPos cell : first.pool()) {
			helper.assertTrue(level.getBlockState(cell).is(Blocks.WATER), "version 1's pool at " + cell.toShortString());
		}
		// The walking floor plain (the test mountain's granite gone from it), the square's dirt and the landing apart.
		int blots = 0;
		BoundingBox box = shape.box();
		for (int x = box.minX(); x <= box.maxX(); x += 2) {
			for (int z = box.minZ(); z <= box.maxZ(); z += 2) {
				long range = shape.column(x, z);
				BlockPos floor = new BlockPos(x, yf, z);
				if (range == Seal.NONE || Seal.lo(range) != yf + 1 || shape.pool().contains(floor)
						|| Math.max(Math.abs(shape.lateral(x, z)), Math.abs(shape.along(x, z))) <= HollowShape.GROVE) {
					continue;
				}
				blots += level.getBlockState(floor).is(Blocks.STONE) && level.getBlockState(floor.below()).is(Blocks.STONE) ? 0 : 1;
			}
		}
		helper.assertTrue(blots == 0, blots + " floor cells not plain stone");
		// Worldgen's freeze step over both landings, then the weather's rules.
		for (HollowShape s : List.of(shape, first)) {
			Set<ChunkPos> chunks = new HashSet<>();
			for (BlockPos cell : s.pool()) {
				chunks.add(ChunkPos.containing(cell));
			}
			for (ChunkPos chunk : chunks) {
				GalleryProps.command(level, Vec3.atCenterOf(s.windowFoot()), String.format(Locale.ROOT, "place feature minecraft:freeze_top_layer %d %d %d",
						chunk.getMinBlockX(), yf, chunk.getMinBlockZ()));
			}
		}
		int ice = 0;
		for (BlockPos cell : shape.pool()) {
			BlockPos drift = cell.above();
			Biome biome = level.getBiome(drift).value();
			helper.assertTrue(level.getBlockState(drift).is(Blocks.POWDER_SNOW) && !biome.shouldFreeze(level, drift) && !biome.shouldSnow(level, drift.above()),
					"the drift at " + drift.toShortString() + " froze or takes snow: " + level.getBlockState(drift));
			helper.assertTrue(biome.coldEnoughToSnow(drift, level.getSeaLevel()), "the landing is not in the cold biome");
		}
		for (BlockPos cell : first.pool()) {
			ice += level.getBlockState(cell).is(Blocks.ICE) ? 1 : 0;
		}
		helper.assertTrue(ice > 0, "version 1's pool did not freeze: the cold biome is not there");
		// A cow steps into each opening from the mountain.
		List<ChunkPos> forced = new ArrayList<>();
		for (HollowShape s : List.of(shape, first)) {
			ChunkPos chunk = ChunkPos.containing(s.windowFoot());
			for (int cx = chunk.x() - 1; cx <= chunk.x() + 1; cx++) {
				for (int cz = chunk.z() - 1; cz <= chunk.z() + 1; cz++) {
					level.setChunkForced(cx, cz, true);
					forced.add(new ChunkPos(cx, cz));
				}
			}
		}
		List<Cow> cows = new ArrayList<>();
		helper.startSequence().thenExecute(() -> {
			long until = net.minecraft.util.Util.getMillis() + 30_000L;
			level.getServer().managedBlock(() -> forced.stream().allMatch(c -> level.isPositionEntityTicking(c.getMiddleBlockPosition(yf)))
					|| net.minecraft.util.Util.getMillis() > until);
			for (HollowShape s : List.of(shape, first)) {
				Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
				BlockPos top = s.windowOuter().above();
				cow.snapTo(Vec3.atBottomCenterOf(top), 0.0F, 0.0F);
				level.addFreshEntity(cow);
				cows.add(cow);
			}
			helper.assertTrue(cows.get(0).getY() - yf > HollowShape.WINDOW_MIN_UP, "the fall is short: " + (cows.get(0).getY() - yf));
		}).thenIdle(100).thenExecute(() -> {
			Cow safe = cows.get(0);
			Cow lost = cows.get(1);
			try {
				helper.assertTrue(safe.isAlive() && safe.getHealth() == safe.getMaxHealth() && safe.getY() < yf + 3,
						"the cow on the drift: alive " + safe.isAlive() + ", health " + safe.getHealth() + ", at " + safe.blockPosition().toShortString());
				helper.assertTrue(!lost.isAlive() || lost.getHealth() < lost.getMaxHealth(), "the cow on version 1's ice was not hurt");
			} finally {
				cows.forEach(cow -> cow.discard());
				forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false));
			}
		}).thenSucceed();
	}

	/** This start's land made cold where its landing will be (frozen peaks round the opening's foot), then the start again. */
	private static Start cold(ServerLevel level, Start start) {
		HollowShape probe = new HollowShape(start, Live.terrain(level));
		BlockPos foot = probe.windowFoot();
		if (foot != null) {
			GalleryProps.command(level, Vec3.atCenterOf(foot), String.format(Locale.ROOT, "fillbiome %d %d %d %d %d %d minecraft:frozen_peaks", foot.getX() - 8,
					probe.floorY() - 6, foot.getZ() - 8, foot.getX() + 8, probe.floorY() + 10, foot.getZ() + 8));
		}
		return start;
	}

	/**
	 * R12-5: a start's shape read from fewer points of the land is the same shape as read from every point: over the real
	 * noise's starts and the test's own mountain (the box, the square and its stems, the way in, the opening and its landing,
	 * the extra, every column's inside), from fewer reads; a start loaded again over the same noise is not built again.
	 */
	@GameTest(maxTicks = 2400)
	public void aShapeReadFromFewerPointsIsTheSameShape(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RealStarts real = realStarts(level);
		helper.assertTrue(!real.starts().isEmpty(), "no start in the real mountains");
		Hill hill = hill(level, 249_808, -249_008, Direction.NORTH, 84, 86, 14, true);
		List<String> wrong = new ArrayList<>();
		long fewer = 0;
		long every = 0;
		List<Object[]> cases = new ArrayList<>();
		for (Start start : real.starts()) {
			cases.add(new Object[] {start.withVersion(2), real.terrain()});
		}
		cases.add(new Object[] {start(hill, Direction.NORTH, true, 0x18A05L), Live.terrain(level)});
		for (Object[] c : cases) {
			Start start = (Start) c[0];
			Terrain terrain = (Terrain) c[1];
			HollowShape a = new HollowShape(start, terrain);
			HollowShape b = HollowShape.readingEveryPoint(start, terrain);
			fewer += a.samples();
			every += b.samples();
			String at = start.origin().toShortString();
			if (!a.box().equals(b.box()) || a.valid() != b.valid() || !a.groveCenter().equals(b.groveCenter()) || !a.stems().equals(b.stems())
					|| !a.wayIn().equals(b.wayIn()) || !a.windowCells().equals(b.windowCells()) || !a.pool().equals(b.pool())
					|| a.snowLanding() != b.snowLanding() || !a.extra().equals(b.extra())) {
				wrong.add(at + ": the shape differs");
				continue;
			}
			BoundingBox box = a.box();
			for (int x = box.minX(); x <= box.maxX() && wrong.size() < 6; x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					if (a.column(x, z) != b.column(x, z)) {
						wrong.add(at + ": the column at " + x + " " + z);
						break;
					}
				}
			}
			helper.assertTrue(a.samples() < b.samples(), at + ": read " + a.samples() + " of " + b.samples());
		}
		helper.assertTrue(wrong.isEmpty(), "read from fewer points: " + wrong);
		Start start = real.starts().getFirst();
		helper.assertTrue(HollowSiting.create(start, real.terrain()) == HollowSiting.create(S18.KIND.resolve(start), real.terrain()),
				"a start loaded again over the same noise was built again");
		System.out.println(String.format(Locale.ROOT, "[a1016] s18 reads: %d of %d points (%.0f %%) over %d shapes", fewer, every, 100.0 * fewer / every,
				cases.size()));
		helper.succeed();
	}

	// --- worldgen ---

	/**
	 * Worldgen's own path (the piece placed chunk by chunk and finished as vanilla finishes a chunk) builds the shape
	 * exactly as {@link Live#place} does on the same mountain 320 blocks away, block for block; the recorder knows it
	 * once, as the hollow kind with its square, its mouth and its extra; the piece's NBT holds its start and nothing more,
	 * and the start loaded back makes the same shape over the same land.
	 */
	@GameTest(environment = ENV, maxTicks = 600)
	public void builtByWorldgenEqualsLivePlaceAndIsRecordedOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Direction facing = Direction.SOUTH;
		int shift = 320;
		Hill a = hill(level, 241_008, -241_008, facing, 70, 74, 12, false);
		Hill b = hill(level, 241_008 + shift, -241_008, facing, 70, 74, 12, false);
		Start start = start(a, facing, false, 0x5EED18L);
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S18_KEY);
		Sc2Structure structure = (Sc2Structure) holder.value();
		Terrains.Frozen frozen = Live.terrain(level);
		HollowShape shape = new HollowShape(start, frozen);
		frozen.freeze(shape.box());
		helper.assertTrue(shape.valid(), "no shape on the test mountain");
		Sc2Data data = new Sc2Data();
		try {
			Recorder.drain(new Sc2Data());
			StructureStart made = new StructureStart(structure, ChunkPos.containing(start.origin()), 0,
					new PiecesContainer(List.of(new Sc2Piece(S18.ID, start, shape))));
			StructureStart loaded = Live.reload(level, made);
			helper.assertTrue(loaded != null && loaded.isValid() && loaded.getPieces().getFirst() instanceof Sc2Piece piece && piece.start().equals(S18.KIND.resolve(start))
					&& S18.ID.equals(piece.kind()), "the start did not load back");
			HollowShape again = new HollowShape(((Sc2Piece) loaded.getPieces().getFirst()).start(), frozen);
			helper.assertTrue(again.box().equals(shape.box()) && again.entrance().equals(shape.entrance()) && again.groveCenter().equals(shape.groveCenter())
					&& again.stems().equals(shape.stems()), "the shape made again differs");
			Live.placeStart(level, made);
			helper.assertTrue(Recorder.drain(data) == 1 && data.places().size() == 1, "recorded " + data.places().size());
			Sc2Data.Known known = data.places().getFirst();
			Remains.Place place = known.place();
			helper.assertTrue(place.kind() == Remains.Kind.HOLLOW && place.anchor().equals(shape.anchor()) && place.entrance().equals(shape.entrance())
					&& place.entranceFacing() == facing && known.extra().getByteArray("c").orElseThrow().length == HollowShape.COARSE_SIDE * HollowShape.COARSE_SIDE
					&& known.extra().contains("m"), "the recorded place: " + place);
			Live.Result live = Live.place(level, S18.KIND, start.moved(shift, 0, 0));
			BoundingBox box = shape.box();
			helper.assertTrue(live.box().equals(box.moved(shift, 0, 0)), "the boxes: " + box + " / " + live.box());
			List<String> differ = new ArrayList<>();
			int air = 0;
			int minY = Math.max(box.minY(), level.getMinY() + 1);
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), minY, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState x = level.getBlockState(pos);
				BlockState y = level.getBlockState(pos.offset(shift, 0, 0));
				air += x.isAir() && shape.taken(pos.getX(), pos.getY(), pos.getZ()) ? 1 : 0;
				if (x != y && differ.size() < 8) {
					differ.add(pos.toShortString() + " " + x + " / " + y);
				}
			}
			helper.assertTrue(differ.isEmpty(), "worldgen and Live differ: " + differ);
			helper.assertTrue(air > 100_000, "only " + air + " cells taken");
			helper.assertTrue(b.foot() == a.foot(), "the two mountains differ");
		} finally {
			Recorder.drain(new Sc2Data());
		}
		helper.succeed();
	}

	// --- 18b ---

	/** A built hollow with an answer of its own: its data, who is inside and around, the view, the clock and what was answered. */
	private record Rig(HollowAnswers hollow, Answers answers, Sc2Data data, Remains.Place place, HollowShape shape, HollowAnswers.Mouth mouth,
			List<ServerPlayer> inside, List<ServerPlayer> people, AtomicBoolean seen, AtomicLong day, AtomicLong now, List<String> answered, Sc18Config config) {
		void at(MinecraftServer server, long ticks) {
			now.set(ticks);
			hollow.look(server, ticks);
			answers.tryAll(server);
		}

		boolean open(ServerLevel level) {
			return mouth.cells().stream().allMatch(pos -> level.getBlockState(pos).isAir());
		}

		boolean closed(ServerLevel level) {
			return mouth.cells().stream().noneMatch(pos -> level.getBlockState(pos).isAir());
		}
	}

	private static Rig rig(ServerLevel level, int x, int z, Direction facing) {
		Hill hill = hill(level, x, z, facing, 64, 70, 10, false);
		Live.Result built = Live.place(level, S18.KIND, start(hill, facing, false, x * 31L + z));
		HollowShape shape = (HollowShape) built.shape();
		Sc2Data data = new Sc2Data();
		data.add(built.known());
		List<ServerPlayer> inside = new ArrayList<>();
		List<ServerPlayer> people = new ArrayList<>();
		AtomicBoolean seen = new AtomicBoolean(false);
		AtomicLong day = new AtomicLong(50);
		AtomicLong now = new AtomicLong(0);
		List<String> answered = Collections.synchronizedList(new ArrayList<>());
		Answers.Env env = new Answers.Env(s -> data, Services.traces().forced(), (l, cells) -> !seen.get(), (s, p) -> List.copyOf(inside), uuid -> false,
				uuid -> 200_000L, s -> null, day::get, now::get, (l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString()));
		Answers answers = Answers.over(env);
		Sc18Config config = new Sc18Config();
		HollowAnswers.Mouth mouth = new HollowAnswers.Mouth(HollowShape.innerMouthOf(built.known().extra()).orElseThrow(), facing, shape.entrance());
		HollowAnswers hollow = new HollowAnswers(new HollowAnswers.Env(answers, server -> List.copyOf(people), () -> config, place -> mouth));
		return new Rig(hollow, answers, data, built.place(), shape, mouth, inside, people, seen, day, now, answered, config);
	}

	/**
	 * 18b with one player: nothing before 120 s inside; refused without a pickaxe and under 10 health; waits while she is
	 * within 24 of the inner mouth and while it would be seen; then four blocks of the wall beside the mouth move into it
	 * (their hollows left), ledgered, the answer counted for her; undone through the ledger; the place's day cap holds the
	 * same day; another day it closes again, and never a third time (2 a place).
	 */
	@GameTest(maxTicks = 400)
	public void theWayInClosesOnlyWithAPickaxeTheHealthTheDistanceOutOfViewAndIsUndone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig rig = rig(level, 245_008, -233_008, Direction.EAST);
		HollowShape shape = rig.shape();
		ServerPlayer ana = armed(level, "ana");
		stand(ana, feet(shape, 2, -2));
		rig.inside().add(ana);
		rig.people().add(ana);
		try {
			rig.hollow().entered(rig.place(), ana, 0);
			rig.at(server, 20 * 110);
			helper.assertTrue(rig.open(level) && rig.answered().isEmpty(), "closed before 120 s");
			ana.getInventory().clearContent();
			rig.at(server, 20 * 120);
			helper.assertTrue(rig.open(level), "closed without a pickaxe");
			ana.getInventory().add(new ItemStack(Items.WOODEN_PICKAXE));
			ana.setHealth(9.0F);
			rig.at(server, 20 * 120 + HollowAnswers.CLOSE_RETRY_TICKS);
			helper.assertTrue(rig.open(level), "closed under 10 health");
			ana.setHealth(20.0F);
			List<BlockPos> mouth = rig.mouth().cells();
			int inner = shape.along(mouth.getFirst().getX(), mouth.getFirst().getZ());
			stand(ana, feet(shape, shape.lane(), inner - 10));
			rig.at(server, 20 * 120 + 2 * HollowAnswers.CLOSE_RETRY_TICKS);
			helper.assertTrue(rig.open(level) && rig.answers().waiting(rig.place(), "18b"), "not waiting while she is near the mouth");
			stand(ana, feet(shape, 2, -2));
			rig.seen().set(true);
			rig.at(server, 20 * 120 + 2 * HollowAnswers.CLOSE_RETRY_TICKS + 100);
			helper.assertTrue(rig.open(level), "closed in view");
			List<BlockPos> beside = HollowAnswers.beside(level, rig.mouth()).subList(0, 4);
			List<BlockState> wall = beside.stream().map(level::getBlockState).toList();
			rig.seen().set(false);
			rig.at(server, 20 * 120 + 2 * HollowAnswers.CLOSE_RETRY_TICKS + 200);
			helper.assertTrue(rig.closed(level), "not closed out of view with every rule met");
			helper.assertTrue(beside.stream().allMatch(pos -> level.getBlockState(pos).isAir()), "no hollows beside the mouth");
			helper.assertTrue(rig.answered().equals(List.of("18b:ana")) && rig.data().spent(rig.place(), "18b").ever() == 1, "counted: " + rig.answered());
			List<TraceLedger.Entry> moves = TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(HollowAnswers.CLOSE)).toList();
			helper.assertTrue(moves.size() == 4, "ledgered: " + moves.size());
			TraceService traces = Services.traces().forced();
			for (TraceLedger.Entry entry : moves) {
				helper.assertTrue(traces.undo(level, entry) == TraceService.UndoResult.DONE, "not undone: " + entry);
			}
			helper.assertTrue(rig.open(level) && beside.stream().map(level::getBlockState).toList().equals(wall), "the undo left it closed");
			// The same day, a new visit: the place had it today.
			rig.hollow().entered(rig.place(), ana, 20 * 300);
			rig.hollow().arm(ana, 20 * 300);
			rig.at(server, 20 * 300 + 20);
			helper.assertTrue(rig.open(level) && rig.answered().size() == 1, "closed twice in a day");
			// Another day: again; then never a third time.
			rig.day().set(51);
			rig.hollow().entered(rig.place(), ana, 20 * 400);
			rig.hollow().arm(ana, 20 * 400);
			rig.at(server, 20 * 400 + 20);
			helper.assertTrue(rig.closed(level) && rig.answered().size() == 2, "not closed the second day: " + rig.answered());
			for (TraceLedger.Entry entry : TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(HollowAnswers.CLOSE)).toList()) {
				Services.traces().forced().undo(level, entry);
			}
			rig.day().set(52);
			rig.hollow().entered(rig.place(), ana, 20 * 500);
			rig.hollow().arm(ana, 20 * 500);
			rig.at(server, 20 * 500 + 20);
			rig.at(server, 20 * 500 + 20 + HollowAnswers.CLOSE_RETRY_TICKS);
			helper.assertTrue(rig.open(level) && rig.answered().size() == 2, "closed a third time");
		} finally {
			forget(server, S18.CAUSE, shape.box());
			forget(server, TraceService.UNDO_CAUSE, shape.box());
		}
		helper.succeed();
	}

	/**
	 * Multiplayer: a friend outside at the outer mouth holds the way in open (it waits) until they go; on another day a
	 * friend inside without a pickaxe refuses it, and with one both are closed in together, the answer counted for both.
	 */
	@GameTest(maxTicks = 400)
	public void aFriendAtTheMouthHoldsTheWayInOpenAndTwoInsideAreClosedInTogether(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Rig rig = rig(level, 245_008, -241_008, Direction.SOUTH);
		HollowShape shape = rig.shape();
		ServerPlayer ana = armed(level, "ana");
		ServerPlayer ben = armed(level, "ben");
		stand(ana, feet(shape, 2, -2));
		BlockPos outside = shape.entrance().relative(Direction.SOUTH, 3);
		stand(ben, outside.atY(groundTop(level, outside)));
		rig.inside().add(ana);
		rig.people().addAll(List.of(ana, ben));
		try {
			rig.hollow().entered(rig.place(), ana, 0);
			rig.at(server, 20 * 125);
			rig.at(server, 20 * 126);
			helper.assertTrue(rig.open(level) && rig.answers().waiting(rig.place(), "18b"), "closed on a friend at the mouth");
			stand(ben, outside.relative(Direction.SOUTH, 40).atY(groundTop(level, outside.relative(Direction.SOUTH, 40))));
			rig.at(server, 20 * 131);
			helper.assertTrue(rig.closed(level) && rig.answered().equals(List.of("18b:ana")), "not closed once the friend went: " + rig.answered());
			for (TraceLedger.Entry entry : TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(HollowAnswers.CLOSE)).toList()) {
				Services.traces().forced().undo(level, entry);
			}
			helper.assertTrue(rig.open(level), "not undone");
			rig.day().set(51);
			stand(ben, feet(shape, -6, -6));
			rig.inside().add(ben);
			ben.getInventory().clearContent();
			rig.hollow().entered(rig.place(), ana, 20 * 300);
			rig.hollow().entered(rig.place(), ben, 20 * 300);
			rig.hollow().arm(ana, 20 * 300);
			rig.at(server, 20 * 300 + 20);
			helper.assertTrue(rig.open(level), "closed with a friend inside who cannot dig");
			ben.getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			rig.hollow().arm(ben, 20 * 400);
			rig.hollow().arm(ana, 20 * 400);
			rig.at(server, 20 * 400 + 20);
			helper.assertTrue(rig.closed(level), "not closed on both");
			helper.assertTrue(rig.answered().containsAll(List.of("18b:ana", "18b:ben")) && rig.answered().size() == 3, "the events: " + rig.answered());
		} finally {
			forget(server, S18.CAUSE, shape.box());
			forget(server, TraceService.UNDO_CAUSE, shape.box());
		}
		helper.succeed();
	}

	private static int groundTop(ServerLevel level, BlockPos pos) {
		return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
	}

	// --- the real noise ---

	/** The overworld's own terrain for {@link #REAL_SEED} (nothing generated: the noise only). */
	private static Terrain realTerrain(ServerLevel level) {
		HolderGetter<NoiseGeneratorSettings> settingsLookup = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
		Holder<NoiseGeneratorSettings> settings = settingsLookup.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
		HolderGetter<MultiNoiseBiomeSourceParameterList> presets = level.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
		MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
		HolderGetter<NormalNoise> noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
		RandomState random = RandomState.create(noises, REAL_SEED, settings.value());
		return Terrains.noise(generator, random, level);
	}

	/** The starts siting makes in the mountains near the real seed's stony and jagged peaks, and how long each took. */
	private record RealStarts(Terrain terrain, List<Start> starts, List<Double> millis, int asked) {
	}

	private static RealStarts realStarts(ServerLevel level) {
		Terrain terrain = realTerrain(level);
		Sc18Config config = new Sc18Config();
		List<Start> starts = new ArrayList<>();
		List<Double> millis = new ArrayList<>();
		int asked = 0;
		for (int cx = 54; cx <= 84 && starts.size() < 3; cx += 3) {
			for (int cz = -72; cz <= -42 && starts.size() < 3; cz += 3) {
				asked++;
				long t0 = System.nanoTime();
				Optional<Start> found = find(new ChunkPos(cx, cz), terrain, config, level.getMinY(), level.getMaxY());
				double ms = (System.nanoTime() - t0) / 1_000_000.0;
				if (found.isPresent()) {
					starts.add(found.get());
					millis.add(ms);
					HollowSiting.create(found.get(), terrain);
				}
			}
		}
		return new RealStarts(terrain, starts, millis, asked);
	}

	/**
	 * Over the real noise's mountains siting finds starts; for each, every taken cell stays under the land's own top by
	 * the shell (at least 3 over it, and 5 or more but in one column in 200 or fewer: where a cave of the noise opens the
	 * land into a pit over it; the land within 3 to the side over its top: so 3 to a steep side; the seal's 2 on top of
	 * that), the way in runs through rock to land at the floor's height, and the box stays in the world. Prints how long a
	 * start took (the 81 samples and the shape).
	 */
	@GameTest(maxTicks = 2400)
	public void onTheRealNoiseItsMountainsKeepTheirShell(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RealStarts real = realStarts(level);
		helper.assertTrue(!real.starts().isEmpty(), "no start in the real mountains after " + real.asked() + " chunks");
		Terrain terrain = real.terrain();
		List<String> wrong = new ArrayList<>();
		int thinnest = Integer.MAX_VALUE;
		int thin = 0;
		int all = 0;
		for (Start start : real.starts()) {
			HollowShape shape = new HollowShape(start, terrain);
			helper.assertTrue(shape.valid() && HollowSiting.withinHeight(shape.box(), level.getMinY(), level.getMaxY()), "the start: " + start);
			BoundingBox box = shape.box();
			int checked = 0;
			for (int x = box.minX(); x <= box.maxX(); x += 3) {
				for (int z = box.minZ(); z <= box.maxZ(); z += 3) {
					long range = shape.column(x, z);
					if (range == Seal.NONE) {
						continue;
					}
					checked++;
					int hi = Seal.hi(range);
					int over = terrain.ground(x, z) - hi;
					thinnest = Math.min(thinnest, over);
					thin += over < 5 ? 1 : 0;
					all++;
					if (over < 3 && wrong.size() < 10) {
						wrong.add(start.origin().toShortString() + ": " + over + " over it at " + x + " " + z);
					}
					for (int dx = -3; dx <= 3; dx += 3) {
						for (int dz = -3; dz <= 3; dz += 3) {
							if ((dx != 0 || dz != 0) && terrain.ground(x + dx, z + dz) <= hi && wrong.size() < 10) {
								wrong.add(start.origin().toShortString() + ": the side at " + (x + dx) + " " + (z + dz) + " under the top " + hi);
							}
						}
					}
				}
			}
			helper.assertTrue(checked > 100, "only " + checked + " columns taken at " + start.origin().toShortString());
			BlockPos mouth = shape.entrance();
			helper.assertTrue(terrain.ground(mouth.getX(), mouth.getZ()) >= start.origin().getY() + 2 + HollowShape.LINTEL, "the mouth has no rock over it");
		}
		helper.assertTrue(wrong.isEmpty(), "the shell over the real noise: " + wrong);
		helper.assertTrue(thin * 200 <= all, thin + " of " + all + " columns under 5 of rock");
		System.out.println(String.format(Locale.ROOT, "[a1016] s18 on the real noise: %d starts in %d chunks, a start took %s ms, the thinnest rock over it %d, "
				+ "%d of %d columns under 5", real.starts().size(), real.asked(), real.millis(), thinnest, thin, all));
		helper.succeed();
	}

	/**
	 * The cost per chunk on worldgen's own path (D-104: under 4 ms median, 15 worst on the dev Mac) for a start of the real
	 * noise: its shape over the real noise, placed into the test world's chunks round its square filled with stone up to
	 * the noise's land (a real world's rock), the 7 x 7 chunks of its tallest part. Only with
	 * {@code -Da1016_02.s18cost=true}; prints the numbers.
	 */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void costPerChunkOnTheRealNoise(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.s18cost")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		RealStarts real = realStarts(level);
		helper.assertTrue(!real.starts().isEmpty(), "no start in the real mountains");
		Start start = real.starts().getFirst();
		Terrain terrain = real.terrain();
		long t0 = System.nanoTime();
		HollowShape shape = new HollowShape(start, terrain);
		double shapeMillis = (System.nanoTime() - t0) / 1_000_000.0;
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(S18_KEY);
		StructureStart made = new StructureStart((Sc2Structure) holder.value(), ChunkPos.containing(start.origin()), 0,
				new PiecesContainer(List.of(new Sc2Piece(S18.ID, start, shape))));
		ChunkPos middle = ChunkPos.containing(shape.groveCenter());
		BoundingBox area = new BoundingBox(middle.getMinBlockX() - 48, shape.floorY() - 20, middle.getMinBlockZ() - 48, middle.getMaxBlockX() + 48,
				shape.box().maxY(), middle.getMaxBlockZ() + 48);
		loadChunks(level, area);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = area.minX(); x <= area.maxX(); x++) {
			for (int z = area.minZ(); z <= area.maxZ(); z++) {
				int top = Math.min(area.maxY(), terrain.ground(x, z));
				for (int y = area.minY(); y <= top; y++) {
					level.setBlock(at.set(x, y, z), Blocks.STONE.defaultBlockState(), FLAGS);
				}
			}
		}
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		List<Double> ms = new ArrayList<>();
		List<LevelChunk> chunks = new ArrayList<>();
		for (int cx = middle.x() - 3; cx <= middle.x() + 3; cx++) {
			for (int cz = middle.z() - 3; cz <= middle.z() + 3; cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				LevelChunk levelChunk = level.getChunk(cx, cz);
				chunks.add(levelChunk);
				BoundingBox writable = new BoundingBox(chunk.getMinBlockX(), level.getMinY() + 1, chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(),
						chunk.getMaxBlockZ());
				WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(level.getSeed()));
				random.setDecorationSeed(level.getSeed(), chunk.getMinBlockX(), chunk.getMinBlockZ());
				long c0 = System.nanoTime();
				made.placeInChunk(level, level.structureManager(), generator, random, writable, chunk);
				ms.add((System.nanoTime() - c0) / 1_000_000.0);
			}
		}
		for (LevelChunk chunk : chunks) {
			chunk.postProcessGeneration(level);
		}
		Collections.sort(ms);
		System.out.println(String.format(Locale.ROOT, "[a1016] s18 cost: siting %s ms, the shape %.1f ms; per chunk %d chunks, median %.2f ms, worst %.2f ms",
				real.millis(), shapeMillis, ms.size(), ms.get(ms.size() / 2), ms.getLast()));
		helper.succeed();
	}
}
