package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.world.SelfCreated2KitTests.TestPlayer;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Decided;
import com.forzacode.a1016_02.world.sc2.kit.DemoKind;
import com.forzacode.a1016_02.world.sc2.kit.Fault;
import com.forzacode.a1016_02.world.sc2.kit.FaultDemo;
import com.forzacode.a1016_02.world.sc2.kit.KitPlacement;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Plans;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Piece;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.kit.Visits;
import com.forzacode.a1016_02.world.sc2.s16.Sc16Config;
import com.forzacode.a1016_02.world.sc2.s17.Sc17Config;
import com.forzacode.a1016_02.world.sc2.s18.Sc18Config;
import com.forzacode.a1016_02.world.sc2.s19.LineMarker;
import com.forzacode.a1016_02.world.sc2.s19.S19;
import com.forzacode.a1016_02.world.sc2.s20.Sc20Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * R3-0's game tests (docs/SELF_CREATED_2_R3.md, "R3-0 sc3/kit"): the places from the world's creation and their gates
 * (D-108), asleep until Traces, creative never inside (D-116), {@code Decided} (once per candidate, kept, saved, worldgen
 * builds what was decided), the lighting fault measured (D-110), {@code /locate} through the kit's placements and
 * decisions (D-112), and each kind's current shape version pinned by a fill hash (R12-2). Own spots far out, so nothing
 * meets another test's.
 */
public class SelfCreated3KitTests {
	private static final ResourceKey<Structure> K0 = ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/k0"));
	private static final String CAUSE = "world:k0/r3";

	// --- helpers ---

	private static Snapshot live(MinecraftServer server, Stage stage) {
		Snapshot now = Snapshot.take(server);
		return new Snapshot(true, stage, now.spawn(), now.seed(), now.salt(), now.configs(), true);
	}

	private static void loadChunks(ServerLevel level, BoundingBox box) {
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.getChunkSource().addTicketWithRadius(com.forzacode.a1016_02.world.live.NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}

	private static Holder<Structure> structure(ServerLevel level, String id) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/" + id)));
	}

	/** What a visit's sink saw, in order. */
	private static final class Seen implements Visits.Sink {
		final List<String> events = new ArrayList<>();

		@Override
		public void entered(ServerLevel level, Remains.Place place, ServerPlayer player) {
			events.add("+" + player.getName().getString());
		}

		@Override
		public void left(ServerLevel level, Remains.Place place, ServerPlayer player) {
			events.add("-" + player.getName().getString());
		}

		@Override
		public void cameInThroughTheWay(MinecraftServer server, Remains.Place place, ServerPlayer player) {
			events.add("way:" + player.getName().getString());
		}
	}

	// --- D-108: from the world's creation ---

	/**
	 * Every kind is decided in Alone (no stage gate by default), never before the world spawn is known, never within its
	 * R0 of the origin or the world spawn; a stage raised by hand is still honoured; the configs' new numbers.
	 */
	@GameTest
	public void theGatesDecideInAloneButNeverBeforeTheSpawnNorWithinR0(GameTestHelper helper) {
		Sc16Config c16 = new Sc16Config();
		Sc17Config c17 = new Sc17Config();
		Sc18Config c18 = new Sc18Config();
		Sc20Config c20 = new Sc20Config();
		helper.assertTrue(c16.fromStage == 0 && c17.fromStage == 0 && c18.fromStage == 0 && c20.fromStage == 0, "a stage gate by default");
		helper.assertTrue(c16.minOriginDistance == 352 && c17.minOriginDistance == 384 && c18.minOriginDistance == 416 && c20.minOriginDistance == 416,
				"R0: " + c16.minOriginDistance + " " + c17.minOriginDistance + " " + c18.minOriginDistance + " " + c20.minOriginDistance);
		Map<String, Object> configs = Map.of("s16", c16, "s17", c17, "s18", c18, "s20", c20);
		Snapshot alone = new Snapshot(true, Stage.ALONE, BlockPos.ZERO, 1L, 2L, configs, true);
		Snapshot unknown = new Snapshot(true, Stage.TRACES, BlockPos.ZERO, 1L, 2L, configs, false);
		Snapshot spawnFar = new Snapshot(true, Stage.ALONE, new BlockPos(5000, 70, 0), 1L, 2L, configs, true);
		for (String id : List.of("s16", "s17", "s18", "s20")) {
			Sc2Kind kind = Sc2Kind.get(id);
			int r0 = ((Sc2Kind.Gates) configs.get(id)).minOriginDistance();
			ChunkPos out = ChunkPos.containing(new BlockPos(r0 + 40, 0, 0));
			ChunkPos in = ChunkPos.containing(new BlockPos(r0 - 40, 0, 0));
			helper.assertTrue(Snapshot.refusal(alone, kind, out) == null, id + " refused in Alone: " + Snapshot.refusal(alone, kind, out));
			helper.assertTrue(Snapshot.refusal(alone, kind, in) != null, id + " within R0 of the origin");
			helper.assertTrue(Snapshot.refusal(unknown, kind, out) != null, id + " before the world spawn is known");
			ChunkPos nearSpawn = ChunkPos.containing(new BlockPos(5000 + r0 - 40, 0, 0));
			helper.assertTrue(Snapshot.refusal(spawnFar, kind, nearSpawn) != null, id + " within R0 of the world spawn");
		}
		c18.fromStage = 2;
		helper.assertTrue(String.valueOf(Snapshot.refusal(alone, Sc2Kind.get("s18"), ChunkPos.containing(new BlockPos(900, 0, 0)))).contains("stage"),
				"a raised stage is not honoured");
		helper.succeed();
	}

	/**
	 * Asleep under Traces (D-108): the live story says so and every answer is refused; awake from Traces. Visits while asleep
	 * keep visited but fire nothing and write no trespass time; a leave fires only for an enter that fired.
	 */
	@GameTest(structure = WorldSignatureTests.BIG)
	public void asleepUnderTracesHoldsTheAnswersAndTheVisitsAreSilent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		HerobrineState state = HerobrineState.get(server);
		Stage before = state.stage();
		try {
			state.setStage(server, Stage.ALONE);
			helper.assertTrue(Answers.ASLEEP.equals(Answers.story(server)), "Alone is not asleep: " + Answers.story(server));
			helper.assertTrue(Answers.ASLEEP.equals(Answers.LIVE.held(server)) && !Remains.awake(server), "held in Alone: " + Answers.LIVE.held(server));
			state.setStage(server, Stage.TRACES);
			helper.assertTrue(Answers.story(server) == null || !Answers.ASLEEP.equals(Answers.story(server)), "Traces is asleep");
		} finally {
			state.setStage(server, before);
		}
		Start start = new Start(helper.absolutePos(new BlockPos(2, 2, 2)), Direction.NORTH, 0, 31L);
		Shape shape = DemoKind.KIND.shapes().create(start, Terrains.noise(level));
		Remains.Place place = new Remains.Place(Remains.Kind.FLOOR, level.dimension(), shape.box(), shape.anchor(), shape.entrance(), shape.entranceFacing(),
				shape.apex(), start.seed(), start.variant());
		Sc2Data data = new Sc2Data();
		data.add(Sc2Data.Known.of(place, DemoKind.ID, start, shape.extra()));
		RemainsImpl remains = RemainsImpl.over(data);
		List<ServerPlayer> online = new ArrayList<>();
		Seen seen = new Seen();
		AtomicBoolean awake = new AtomicBoolean(false);
		Visits visits = new Visits(remains, seen, s -> List.copyOf(online), () -> true, s -> awake.get());
		TestPlayer ana = new TestPlayer(level, "ana", GameType.SURVIVAL);
		online.add(ana);
		BlockPos door = place.entrance();
		BlockPos in = place.anchor();
		ana.snapTo(door.getX() + 0.5, door.getY(), door.getZ() - 1.5, 0, 0);
		visits.scan(server, 100, 1000);
		ana.snapTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0, 0);
		visits.scan(server, 120, 1020);
		helper.assertTrue(seen.events.isEmpty(), "asleep, a visit fired: " + seen.events);
		helper.assertTrue(data.visited(ana.getUUID(), place) && remains.trespassedAt(ana.getUUID()) == -1, "asleep: visited and no trespass time");
		helper.assertTrue(visits.inside(server, place).size() == 1, "asleep, she is not followed inside");
		awake.set(true);
		ana.snapTo(door.getX() + 0.5, door.getY(), door.getZ() - 4.5, 0, 0);
		visits.scan(server, 140, 1040);
		helper.assertTrue(seen.events.isEmpty(), "a leave for an enter that never fired: " + seen.events);
		ana.snapTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0, 0);
		visits.scan(server, 160, 1060);
		helper.assertTrue(seen.events.equals(List.of("+ana")) && remains.trespassedAt(ana.getUUID()) == 1060, "awake, the enter: " + seen.events);
		helper.succeed();
	}

	/** D-116: a creative player standing inside is never inside (no enter, not visited, nobody inside for an answer); a survival one is. */
	@GameTest(structure = WorldSignatureTests.BIG)
	public void aCreativePlayerIsPresentButNeverInside(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Start start = new Start(helper.absolutePos(new BlockPos(2, 2, 2)), Direction.NORTH, 0, 32L);
		Shape shape = DemoKind.KIND.shapes().create(start, Terrains.noise(level));
		Remains.Place place = new Remains.Place(Remains.Kind.FLOOR, level.dimension(), shape.box(), shape.anchor(), shape.entrance(), shape.entranceFacing(),
				shape.apex(), start.seed(), start.variant());
		Sc2Data data = new Sc2Data();
		data.add(Sc2Data.Known.of(place, DemoKind.ID, start, shape.extra()));
		RemainsImpl remains = RemainsImpl.over(data);
		List<ServerPlayer> online = new ArrayList<>();
		Seen seen = new Seen();
		Visits visits = new Visits(remains, seen, s -> List.copyOf(online), () -> true, s -> true);
		TestPlayer cleo = new TestPlayer(level, "cleo", GameType.CREATIVE);
		TestPlayer dan = new TestPlayer(level, "dan", GameType.SURVIVAL);
		online.add(cleo);
		online.add(dan);
		BlockPos in = place.anchor();
		cleo.snapTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0, 0);
		dan.snapTo(in.getX() + 1.5, in.getY(), in.getZ() + 0.5, 0, 0);
		visits.scan(server, 100, 1000);
		helper.assertTrue(Visits.neverInside(cleo) && !Visits.neverInside(dan), "who is never inside");
		helper.assertTrue(seen.events.equals(List.of("+dan")), "the enters: " + seen.events);
		helper.assertTrue(!data.visited(cleo.getUUID(), place) && remains.trespassedAt(cleo.getUUID()) == -1, "creative was inside");
		helper.assertTrue(visits.inside(server, place).equals(List.of(dan)), "inside: " + visits.inside(server, place));
		// Dan turns creative inside: his leave.
		dan.mode = GameType.CREATIVE;
		GameType.CREATIVE.updatePlayerAbilities(dan.getAbilities());
		visits.scan(server, 120, 1020);
		helper.assertTrue(seen.events.equals(List.of("+dan", "-dan")) && visits.inside(server, place).isEmpty(), "turned creative: " + seen.events);
		helper.succeed();
	}

	// --- Decided ---

	/**
	 * One siting per candidate whatever the threads; kept over a change of what siting would say; drained, saved and
	 * loaded (a refusal 8 bytes, a made record whole); worldgen's start is the decision.
	 */
	@GameTest(maxTicks = 400)
	public void decidedSitesEachCandidateOnceKeepsItAndWorldgenBuildsIt(GameTestHelper helper) throws InterruptedException {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Snapshot.installLive(new Snapshot(true, Stage.ALONE, BlockPos.ZERO, level.getSeed(), 7L, Map.of(DemoKind.ID, DemoKind.CONFIG), true), server);
		ChunkPos chunk = new ChunkPos(170_011, -170_013);
		ChunkPos other = new ChunkPos(170_011, -170_017);
		Holder<Structure> k0 = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(K0);
		try {
			helper.assertTrue(Decided.keeps(level.getChunkSource().getGenerator(), level.getSeed()), "the live generator is not kept");
			// Eight threads, one siting.
			AtomicInteger sited = new AtomicInteger();
			CountDownLatch go = new CountDownLatch(1);
			List<Thread> threads = new ArrayList<>();
			List<Decided.Decision> answers = java.util.Collections.synchronizedList(new ArrayList<>());
			for (int i = 0; i < 8; i++) {
				Thread t = new Thread(() -> {
					try {
						go.await();
					} catch (InterruptedException e) {
						return;
					}
					answers.add(Decided.decide("t3", chunk, level.getChunkSource().getGenerator(), level.getSeed(), () -> {
						sited.incrementAndGet();
						try {
							Thread.sleep(30);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						return Decided.Decision.refused("t3", chunk, "siting");
					}));
				});
				threads.add(t);
				t.start();
			}
			go.countDown();
			for (Thread t : threads) {
				t.join(5000);
			}
			helper.assertTrue(sited.get() == 1 && answers.size() == 8 && answers.stream().allMatch(d -> d == answers.getFirst()),
					"sited " + sited.get() + " times, " + answers.size() + " answers");
			Decided.Decision again = Decided.decide("t3", chunk, level.getChunkSource().getGenerator(), level.getSeed(),
					() -> Decided.Decision.refused("t3", chunk, "a later siting"));
			helper.assertTrue("siting".equals(again.why()), "a kept decision changed: " + again.why());

			// The demo's start in an armed chunk: decided once, built as decided, kept when siting would say otherwise.
			Start first = new Start(new BlockPos(chunk.getMinBlockX() + 2, -50, chunk.getMinBlockZ() + 2), Direction.NORTH, 0, 41L);
			Start second = new Start(new BlockPos(other.getMinBlockX() + 2, -50, other.getMinBlockZ() + 2), Direction.EAST, 0, 42L);
			DemoKind.arm(other, second);
			StructureStart built = Live.generate(level, k0, other);
			Decided.Decision made = Decided.get(DemoKind.ID, other);
			helper.assertTrue(built.isValid() && made != null && made.made(), "no decision kept for the demo: " + made);
			Start piece = ((Sc2Piece) built.getPieces().getFirst()).start();
			helper.assertTrue(piece.equals(made.start()) && built.getBoundingBox().equals(made.box()), "worldgen built " + piece + " for " + made.start());
			DemoKind.arm(other, first.at(new BlockPos(other.getMinBlockX() + 5, -50, other.getMinBlockZ() + 5)));
			StructureStart later = Live.generate(level, k0, other);
			helper.assertTrue(((Sc2Piece) later.getPieces().getFirst()).start().equals(made.start()), "a kept decision was sited again");
			helper.assertTrue(made.place().isPresent() && made.sign().equals(made.entrance()),
					"the sign point is not the entrance by default");

			// Drained into the world's data, through its codec and back: the same decisions.
			Sc2Data data = com.forzacode.a1016_02.world.WorldData.get(server).sc2();
			Plans.drain(data);
			Tag saved = Sc2Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
			Sc2Data loaded = Sc2Data.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			long[] refusals = loaded.decidedArrays().get("t3");
			helper.assertTrue(refusals != null && refusals.length >= 1 && java.util.Arrays.stream(refusals).anyMatch(c -> c == chunk.pack()),
					"the refusal was not saved as its chunk");
			Plans.load(loaded);
			Decided.Decision back = Decided.get(DemoKind.ID, other);
			helper.assertTrue(back != null && back.start().equals(made.start()) && back.box().equals(made.box()) && back.entrance().equals(made.entrance())
					&& back.sign().equals(made.sign()) && back.probeY() == made.probeY() && back.facing() == made.facing(), "loaded: " + back + " / " + made);
			helper.assertTrue(Decided.get("t3", chunk) != null && !Decided.get("t3", chunk).made(), "the refusal was not loaded");
		} finally {
			DemoKind.disarm(other);
			Decided.forget("t3");
			Decided.forget(DemoKind.ID);
			Snapshot.refresh(server);
		}
		helper.succeed();
	}

	// --- the lighting fault ---

	/**
	 * The lighting fault on a slope and a sea (D-110): every top cell of the square at 10 or more (on 95 % at least),
	 * 15 at the lights; outside on level land 11 three out, at most 6 eight out, none fifteen out; waterlogged in water; no
	 * monster may spawn on it; found again, taken through the trace service and given back.
	 */
	@GameTest(maxTicks = 600)
	public void theLightingFaultLightsItsSquareAndItsEdgeFallsOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		int x0 = 3_100_000;
		int z0 = -3_100_000;
		BoundingBox area = new BoundingBox(x0 - 48, level.getMinY(), z0 - 48, x0 + 96, level.getMaxY(), z0 + 64);
		loadChunks(level, area);
		int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 - 20, z0);
		GalleryProps.hill(level, new BlockPos(x0 + 40, g, z0 + 8), 40, 18);
		BoundingBox sea = new BoundingBox(x0 + 32, g - 5, z0 + 32, x0 + 63, g - 1, z0 + 63);
		GalleryProps.sea(level, sea, 3);
		BoundingBox slope = Fault.square(x0, z0, 32, g - 8, g + 40);
		BoundingBox water = Fault.square(x0 + 40, z0 + 40, 16, g - 8, g + 40);
		helper.assertTrue(Fault.chunkAligned(slope) && Fault.chunkAligned(water) && slope.getXSpan() == 32 && water.getXSpan() == 16, "the squares");
		BoundingBox box = new BoundingBox(slope.minX(), g - 8, slope.minZ(), water.maxX(), g + 40, water.maxZ());
		FaultDemo demo = new FaultDemo(box, List.of(slope, water), null, 0, new BlockPos(x0 + 16, g, z0 + 16), Direction.WEST);
		Live.Result built = Live.place(level, DemoKind.KIND, demo, Live.terrain(level), new Start(new BlockPos(x0 + 16, g, z0 + 16), Direction.WEST, 0, 3L));
		Live.settleLight(level, built.box());
		// The top cells: the cell over each column's highest motion-blocking block.
		int cells = 0;
		int lit = 0;
		int spawnable = 0;
		for (int x = slope.minX(); x <= slope.maxX(); x++) {
			for (int z = slope.minZ(); z <= slope.maxZ(); z++) {
				BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				cells++;
				if (level.getBrightness(LightLayer.BLOCK, top) >= Fault.MIN_LIT) {
					lit++;
				}
				if (level.getBrightness(LightLayer.BLOCK, top) == 0 || Monster.isDarkEnoughToSpawn(level, top, level.getRandom())) {
					spawnable++;
				}
			}
		}
		helper.assertTrue(lit >= cells * 0.95, "lit at 10 or more: " + lit + " of " + cells);
		helper.assertTrue(spawnable == 0, "a monster may spawn on " + spawnable + " cells");
		// Outside, on the level land to the west of the square.
		int zMid = slope.minZ() + 15;
		int[] at = {3, 8, 15};
		int[] most = {11, 6, 0};
		for (int i = 0; i < at.length; i++) {
			int x = slope.minX() - at[i];
			BlockPos cell = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, zMid), zMid);
			int light = level.getBrightness(LightLayer.BLOCK, cell);
			helper.assertTrue(light <= most[i], at[i] + " out the light is " + light);
		}
		// The lights: on the grid, full, found again; waterlogged in the sea.
		List<BlockPos> found = Fault.cells(level, slope);
		helper.assertTrue(found.size() == 100, "lights in the 32 square: " + found.size());
		for (BlockPos pos : found) {
			BlockState s = level.getBlockState(pos);
			helper.assertTrue(s.getValue(LightBlock.LEVEL) == Fault.LEVEL && (pos.getX() - slope.minX()) % Fault.GRID == Fault.INSET % Fault.GRID
					&& (pos.getZ() - slope.minZ()) % Fault.GRID == Fault.INSET % Fault.GRID, "a light off the grid or dim at " + pos + ": " + s);
		}
		List<BlockPos> wet = Fault.cells(level, water);
		helper.assertTrue(!wet.isEmpty() && wet.stream().filter(p -> sea.isInside(p))
				.allMatch(p -> level.getBlockState(p).getValue(BlockStateProperties.WATERLOGGED)), "the sea's lights are not waterlogged");
		helper.assertTrue(wet.stream().anyMatch(sea::isInside), "no light in the sea");
		// Taken (ledgered) and given back.
		BlockPos one = found.getFirst();
		helper.assertTrue(Services.traces().forced().remove(level, one, CAUSE) && level.getBlockState(one).isAir(), "the light was not taken");
		TraceLedger ledger = TraceLedger.get(server);
		boolean back = false;
		for (TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
			if (entry.cause().equals(CAUSE)) {
				back |= Services.traces().forced().restoreBlock(level, entry, entry.pos().pos());
				ledger.remove(entry);
			}
		}
		helper.assertTrue(back && level.getBlockState(one).is(Blocks.LIGHT), "the light was not given back");
		helper.succeed();
	}

	// --- /locate ---

	/**
	 * {@code /locate} (D-112) as far as a world without structures goes (the game tests' flat world does not generate
	 * them, so vanilla's search and the mixin stop before asking; the whole path is checked in the real world's client run,
	 * {@code -Da1016_02.sc2real}): vanilla's structure check, which {@code /locate} asks for every candidate, sees the
	 * line's marker in each planned segment's middle chunk and nowhere else, and reads the kit's kept decisions (a made
	 * one is there to load, a refused one is not) for the rays (the mixin's placement) and the random spreads.
	 */
	@GameTest(maxTicks = 1200)
	public void locatesStructureCheckSeesTheLinesMarkersAndTheKeptDecisions(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Snapshot snapshot = live(server, Stage.TRACES);
		Snapshot.installLive(snapshot, server);
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		List<String> checked = new ArrayList<>();
		try {
			Holder<Structure> s19 = structure(level, S19.ID);
			StructurePlacement axes = state.getPlacementsForStructure(s19).getFirst();
			List<ChunkPos> marks = LineMarker.chunks(snapshot, level.getMinY(), level.getMaxY());
			helper.assertTrue(marks.size() == 4 && axes instanceof KitPlacement kit && kit.chunks(state.getLevelSeed()).equals(marks), "the line's marks: " + marks);
			for (ChunkPos mark : marks) {
				helper.assertTrue(axes.isStructureChunk(state, mark.x(), mark.z()), "not a placement chunk: " + mark);
				helper.assertTrue(level.structureManager().checkStructurePresence(mark, s19.value(), axes, false) == StructureCheckResult.CHUNK_LOAD_NEEDED,
						"no marker at " + mark);
				ChunkPos beside = new ChunkPos(mark.x() + 3, mark.z() + 3);
				helper.assertTrue(!axes.isStructureChunk(state, beside.x(), beside.z())
						&& level.structureManager().checkStructurePresence(beside, s19.value(), axes, false) == StructureCheckResult.START_NOT_PRESENT,
						"a marker beside " + mark);
			}
			checked.add("s19");
			for (String id : List.of("s16", "s16d", "s17", "s18", "s20")) {
				Holder<Structure> holder = structure(level, id);
				if (state.getPlacementsForStructure(holder).isEmpty()) {
					// The flat world's one biome holds none of this set's structures: vanilla never asks it here.
					continue;
				}
				StructurePlacement placement = state.getPlacementsForStructure(holder).getFirst();
				// Two placement chunks far out (rays: past 14 000; a random spread: near 2 000 000), never generated.
				List<ChunkPos> chunks = new ArrayList<>();
				if (placement instanceof KitPlacement kit) {
					for (ChunkPos c : kit.chunks(level.getSeed())) {
						long d = (long) c.getMiddleBlockX() * c.getMiddleBlockX() + (long) c.getMiddleBlockZ() * c.getMiddleBlockZ();
						if (d > 14_000L * 14_000L && placement.isStructureChunk(state, c.x(), c.z()) && chunks.size() < 2) {
							chunks.add(c);
						}
					}
				} else {
					int base = 2_000_000 >> 4;
					for (ChunkPos c : Decided.candidates(placement, level.getSeed(), base, base, base + 80, base + 80)) {
						if (placement.isStructureChunk(state, c.x(), c.z()) && chunks.size() < 2) {
							chunks.add(c);
						}
					}
				}
				helper.assertTrue(chunks.size() == 2, id + ": placement chunks " + chunks);
				ChunkPos chunk = chunks.get(0);
				ChunkPos refused = chunks.get(1);
				helper.assertTrue(!level.getChunkSource().hasChunk(chunk.x(), chunk.z()), id + ": its chunk was generated before");
				int ground = level.getChunkSource().getGenerator().getBaseHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
						Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, level.getChunkSource().randomState());
				BlockPos probe = new BlockPos(chunk.getMiddleBlockX(), ground, chunk.getMiddleBlockZ());
				if (!holder.value().biomes().contains(level.getUncachedNoiseBiome(probe.getX() >> 2, probe.getY() >> 2, probe.getZ() >> 2))) {
					continue;
				}
				Start start = new Start(probe, Direction.NORTH, 0, 7_000L + id.hashCode(), Sc2Kind.get(id).version());
				BoundingBox box = new BoundingBox(probe.getX() - 3, ground - 3, probe.getZ() - 3, probe.getX() + 3, ground + 3, probe.getZ() + 3);
				Decided.put(new Decided.Decision(id, chunk, start, box, probe, probe.north(4), Direction.NORTH, probe.north(4), ground, "made"));
				Decided.put(Decided.Decision.refused(id, refused, "siting"));
				helper.assertTrue(level.structureManager().checkStructurePresence(chunk, holder.value(), placement, false) == StructureCheckResult.CHUNK_LOAD_NEEDED,
						id + ": a kept start is not seen at " + chunk);
				helper.assertTrue(level.structureManager().checkStructurePresence(refused, holder.value(), placement, false) == StructureCheckResult.START_NOT_PRESENT,
						id + ": a kept refusal is seen at " + refused);
				checked.add(id);
			}
		} finally {
			for (String id : List.of("s16", "s16d", "s17", "s18", "s20")) {
				Decided.forget(id);
			}
			Snapshot.refresh(server);
		}
		helper.assertTrue(checked.contains("s19") && checked.size() >= 3, "kinds checked: " + checked);
		helper.succeed();
	}

	// --- R12-2: each kind's current version pinned ---

	/**
	 * One start of each kind, found once on the real noise (SC2-H's seed) and pinned with its version and its fill's hash:
	 * {@code kind -> origin, facing, variant, seed, version, hash}. A later version of a kind (round 3's wave 2) leaves
	 * these building exactly as before. {@code -Da1016_02.sc3pin=true} prints new pins (for a kind added later).
	 */
	static final Map<String, long[]> PINS = Map.of(
			"s16", new long[] {609404315336772L, 1, 1, -5499663055901374790L, 3, 5605145985084429620L},
			"s16d", new long[] {72567768842290L, 2, 20, -894454392563526236L, 3, -481562670366344157L},
			"s17", new long[] {302915450404900L, 1, 16, -8846477180019437603L, 2, -9215507516176333980L},
			"s18", new long[] {261958638862447L, 0, 316767, 7343962044577670763L, 2, 8065851210582212743L},
			"s20", new long[] {255361572536422L, 1, 8, 592104747072678437L, 2, 1669673954493617957L});
	/**
	 * FIX-R3-17: every kind's version at round 3's merge (self-created at 07d6426), pinned the same way, so a kit change
	 * that makes the fill cheaper (and a kind's next version) leaves what round 3 built as it was.
	 */
	static final Map<String, long[]> PINS_R3 = Map.of(
			"s16", new long[] {231172315644007L, 0, 43, 557681789001998997L, 5, -752813772399043592L},
			"s16d", new long[] {345246652268583L, 1, 58, -1863543000281370275L, 5, 2808011588050196825L},
			"s17", new long[] {355417131024410L, 1, 33033232, -3133087234388295187L, 3, 1146560708600094843L},
			"s18", new long[] {279550827724908L, 1, 15095132, 5896614968915553466L, 3, 8112008707839784304L},
			"s20", new long[] {211381105852511L, 0, 12, 1251766057126750657L, 3, 7281246904061496928L});

	@GameTest(environment = "a1016_02:world/sc2_heights", maxTicks = 6000)
	public void everyKindsCurrentVersionStillBuildsAsBefore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc2RealNoise.World world = Sc2RealNoise.normal(level, SelfCreated2HeightsTests.REAL_SEED);
		boolean pin = Boolean.getBoolean("a1016_02.sc3pin");
		List<String> wrong = new ArrayList<>();
		Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
		try {
			for (Map.Entry<String, Holder<Structure>> e : Sc2RealNoise.structures(level).entrySet()) {
				Sc2Kind kind = Sc2Kind.get(e.getKey());
				if (kind == null || SelfCreated2HeightsTests.NO_NATURAL_START.contains(e.getKey())) {
					continue;
				}
				long[] p = PINS.get(e.getKey());
				if (pin) {
					Start found = firstStart(level, world, e.getValue(), kind, snapshot);
					if (found != null) {
						long hash = fillHash(level, world, kind.shape(found, world.terrain()));
						Sc2RealNoise.log("PIN \"" + e.getKey() + "\", new long[] {" + found.origin().asLong() + "L, " + found.facing().get2DDataValue() + ", "
								+ found.variant() + ", " + found.seed() + "L, " + found.version() + ", " + hash + "L}");
					}
					continue;
				}
				if (p == null) {
					wrong.add(e.getKey() + " has no pin");
					continue;
				}
				for (long[] q : new long[][] {p, PINS_R3.get(e.getKey())}) {
					if (q == null) {
						continue;
					}
					Start start = new Start(BlockPos.of(q[0]), Direction.from2DDataValue((int) q[1]), (int) q[2], q[3], (int) q[4]);
					long hash = fillHash(level, world, kind.shape(start, world.terrain()));
					if (hash != q[5]) {
						wrong.add(e.getKey() + " v" + q[4] + " fills to " + hash + ", pinned " + q[5]);
					}
				}
			}
		} finally {
			Snapshot.install(null);
			Snapshot.refresh(level.getServer());
		}
		helper.assertTrue(wrong.isEmpty(), "pinned versions changed: " + wrong);
		helper.succeed();
	}

	/** The first start the kit makes round SC2-H's centres (tied to the kind's version now). */
	private static Start firstStart(ServerLevel level, Sc2RealNoise.World world, Holder<Structure> structure, Sc2Kind kind, Snapshot snapshot) {
		StructurePlacement placement = Sc2RealNoise.placement(level, structure);
		if (placement == null) {
			return null;
		}
		for (int[] c : SelfCreated2HeightsTests.CENTRES) {
			for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, 40)) {
				com.forzacode.a1016_02.world.sc2.kit.Sc2Structure.Sited sited = SelfCreated2HeightsTests.sited(world, structure, kind, chunk, snapshot);
				if (sited.made()) {
					return kind.resolve(sited.start());
				}
			}
		}
		return null;
	}

	/** A shape's fill into an overlay over the noise's own blocks, hashed cell by cell in order (stable across runs). */
	static long fillHash(ServerLevel level, Sc2RealNoise.World world, Shape shape) {
		BoundingBox box = shape.box();
		int lowest = com.forzacode.a1016_02.world.sc2.kit.Heights.lowest(world.height(), world.generator());
		com.forzacode.a1016_02.world.sc2.kit.Overlay.Base base = com.forzacode.a1016_02.world.sc2.kit.NoiseVolume.base(world.generator(), world.random(),
				Math.max(lowest, box.minY() - 8), Math.min(world.maxY(), box.maxY() + 8));
		com.forzacode.a1016_02.world.sc2.kit.Overlay overlay = new com.forzacode.a1016_02.world.sc2.kit.Overlay(base, true);
		overlay.fill(shape, world.terrain(), level.registryAccess(), lowest, world.maxY(), Optional.empty());
		long[] keys = overlay.cells().keySet().toLongArray();
		java.util.Arrays.sort(keys);
		long hash = 1125899906842597L;
		for (long key : keys) {
			hash = hash * 31 + key;
			hash = hash * 31 + overlay.cells().get(key).toString().hashCode();
		}
		return hash;
	}
}
