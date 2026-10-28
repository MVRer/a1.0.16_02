package com.forzacode.a1016_02.world;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.world.sc2.WorldSc2;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.DemoKind;
import com.forzacode.a1016_02.world.sc2.kit.Interior;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.RaysPlacement;
import com.forzacode.a1016_02.world.sc2.kit.Recorder;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Piece;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.forzacode.a1016_02.world.sc2.kit.Visits;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * SC2-0b, the second wave's structure kit (docs/SELF_CREATED_2.md): the rays, the kit's gates before siting, the demo
 * kind built by worldgen's own path and by {@link Live} block for block alike, the piece's NBT and a reload, the recorder,
 * the snapshot's stop, the remains' index against a brute-force search, the visits (two and three players, a spectator,
 * a leave inside), the answers (retries, the limit, the fair-danger refusals and caps), the stub, the callers of
 * {@link Live}, the demo kind only in development, and the saved data. Every test has its own data, players and sinks
 * (nothing reaches the live remains); the worldgen ones run in their own environment ({@code a1016_02:world/sc2_kit}).
 */
public class SelfCreated2KitTests {
	private static final String KIT = "a1016_02:world/sc2_kit";
	private static final String BIG = WorldSignatureTests.BIG;
	private static final ResourceKey<Structure> K0 = ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/k0"));
	private static final String CAUSE = "world:k0/test";

	/** A player of the test's own, in any game mode, never on the server's list. */
	static final class TestPlayer extends ServerPlayer {
		GameType mode;

		TestPlayer(ServerLevel level, String name, GameType mode) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
			this.mode = mode;
			mode.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode == null ? GameType.SURVIVAL : mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	// --- helpers ---

	private static RaysPlacement rays() {
		return new RaysPlacement(160721, 3, 768, 896, 8.0F, 48, 64, Optional.empty());
	}

	private static Snapshot snapshot(boolean enabled, Stage stage, BlockPos spawn, ServerLevel level) {
		return new Snapshot(enabled, stage, spawn, level.getSeed(), 7L, Map.of(DemoKind.ID, DemoKind.CONFIG));
	}

	private static Holder<Structure> k0(ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(K0);
	}

	/** The demo's shape for this start (it reads no terrain). */
	private static Shape house(ServerLevel level, Start start) {
		return DemoKind.KIND.shapes().create(start, Terrains.noise(level));
	}

	/** The demo's place for this start, as the recorder would keep it. */
	private static Sc2Data.Known known(ServerLevel level, Start start) {
		Shape shape = house(level, start);
		Remains.Place place = new Remains.Place(Remains.Kind.FLOOR, level.dimension(), shape.box(), shape.anchor(), shape.entrance(),
				shape.entranceFacing(), shape.apex(), start.seed(), start.variant());
		return Sc2Data.Known.of(place, DemoKind.ID, start, shape.extra());
	}

	private static void loadChunks(ServerLevel level, BoundingBox box) {
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.getChunkSource().addTicketWithRadius(com.forzacode.a1016_02.world.live.NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}

	/** What a visit's sink saw, in order: "+name", "-name", "way:name". */
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

	// --- rays ---

	/** The same seed gives the same slot chunks; another seed others. */
	@GameTest
	public void raysGiveTheSameChunksForTheSameSeed(GameTestHelper helper) {
		RaysPlacement rays = rays();
		List<RaysPlacement.Slot> a = rays.slots(12345L, 10);
		List<RaysPlacement.Slot> again = rays().slots(12345L, 10);
		List<RaysPlacement.Slot> other = rays.slots(54321L, 10);
		helper.assertTrue(a.size() == 30 && a.equals(again), "the same seed gave other slots: " + a + " / " + again);
		helper.assertFalse(a.equals(other), "another seed gave the same slots");
		for (RaysPlacement.Slot slot : a) {
			helper.assertTrue(rays.isSlotChunk(12345L, slot.chunk().x(), slot.chunk().z()), "slot " + slot + " is not a placement chunk");
		}
		helper.succeed();
	}

	/** Three rays, their neighbouring bearings 120 degrees apart within the jitter (8 here: never more than 10 off). */
	@GameTest
	public void raysHaveThreeBearingsEvenlyApartWithinTheJitter(GameTestHelper helper) {
		RaysPlacement rays = rays();
		Random random = new Random(3);
		for (int n = 0; n < 200; n++) {
			long seed = random.nextLong();
			double[] b = {rays.bearing(seed, 0), rays.bearing(seed, 1), rays.bearing(seed, 2)};
			for (int i = 0; i < 3; i++) {
				double gap = ((b[(i + 1) % 3] - b[i]) % 360.0 + 360.0) % 360.0;
				helper.assertTrue(Math.abs(gap - 120.0) <= 10.0, "seed " + seed + ": rays " + i + " and " + ((i + 1) % 3) + " are " + gap + " apart");
			}
		}
		helper.succeed();
	}

	/** Slot k sits first + step k along its ray, within the jitter along and across; only slot chunks are placement chunks. */
	@GameTest
	public void raySlotsSitAtTheirDistancesWithinTheJitter(GameTestHelper helper) {
		RaysPlacement rays = rays();
		long seed = 99L;
		Set<ChunkPos> slots = new HashSet<>();
		for (RaysPlacement.Slot slot : rays.slots(seed, 6)) {
			double rad = Math.toRadians(rays.bearing(seed, slot.ray()));
			double along = slot.pos().getX() * Math.cos(rad) + slot.pos().getZ() * Math.sin(rad);
			double across = -slot.pos().getX() * Math.sin(rad) + slot.pos().getZ() * Math.cos(rad);
			double expected = 768 + 896.0 * slot.k();
			helper.assertTrue(Math.abs(along - expected) <= 48 + 2, "slot " + slot + " is " + along + " along, not " + expected + " +- 48");
			helper.assertTrue(Math.abs(across) <= 48 + 2, "slot " + slot + " is " + across + " across");
			slots.add(slot.chunk());
		}
		// Around the first slots of each ray, nothing else is a placement chunk.
		for (RaysPlacement.Slot slot : rays.slots(seed, 2)) {
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					ChunkPos chunk = new ChunkPos(slot.chunk().x() + dx, slot.chunk().z() + dz);
					helper.assertTrue(rays.isSlotChunk(seed, chunk.x(), chunk.z()) == slots.contains(chunk), "chunk " + chunk + " is wrong");
				}
			}
		}
		helper.assertFalse(rays.isSlotChunk(seed, 0, 0), "the origin is a slot");
		helper.succeed();
	}

	/** An exclusion zone keeps a slot chunk out when the other set has a chunk in range; the JSON form reads. */
	@GameTest
	public void raysHonourAnExclusionZoneAndReadFromJson(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		long seed = state.getLevelSeed();
		RaysPlacement plain = rays();
		ChunkPos slot = plain.slotChunk(seed, 1, 0);
		helper.assertTrue(plain.isStructureChunk(state, slot.x(), slot.z()), "the slot is no placement chunk without a zone");
		StructurePlacement everywhere = new RandomSpreadStructurePlacement(2, 1, RandomSpreadType.LINEAR, 4242);
		Holder<StructureSet> other = Holder.direct(new StructureSet(List.of(), everywhere));
		@SuppressWarnings("deprecation")
		RaysPlacement zoned = new RaysPlacement(160721, 3, 768, 896, 8.0F, 48, 64,
				Optional.of(new AbstractSpreadingStructurePlacement.ExclusionZone(other, 2)));
		helper.assertTrue(zoned.isSlotChunk(seed, slot.x(), slot.z()), "the zone moved the slot");
		helper.assertFalse(zoned.isStructureChunk(state, slot.x(), slot.z()), "the exclusion zone did not keep the slot out");

		String json = "{\"type\":\"a1016_02:world/sc2r\",\"salt\":160721,\"rays\":3,\"first\":768,\"step\":896,\"jitter_degrees\":8,\"jitter_blocks\":48}";
		StructurePlacement read = StructurePlacement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
		helper.assertTrue(read instanceof RaysPlacement r && r.isSlotChunk(seed, slot.x(), slot.z()) && r.rays() == 3 && r.slots() == 64,
				"the JSON form read as " + read);
		helper.succeed();
	}

	// --- the kit's gates before siting ---

	/** No start with no snapshot, the switch off, under the kind's stage, or nearer than its distance to the origin or spawn. */
	@GameTest(environment = KIT)
	public void sitingRefusesWithoutSnapshotSwitchOffUnderTheStageOrNearTheOrigin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Snapshot before = Snapshot.current();
		ChunkPos far = new ChunkPos(3000, 3000);
		ChunkPos near = new ChunkPos(2, 2);
		Start start = new Start(new BlockPos(far.getMinBlockX() + 2, -50, far.getMinBlockZ() + 2), Direction.NORTH, 0, 1L);
		Start nearStart = new Start(new BlockPos(near.getMinBlockX() + 2, -50, near.getMinBlockZ() + 2), Direction.NORTH, 0, 1L);
		DemoKind.arm(far, start);
		DemoKind.arm(near, nearStart);
		try {
			Snapshot.install(null);
			helper.assertFalse(Live.generate(level, k0(level), far).isValid(), "a start with no snapshot");
			helper.assertTrue("no snapshot".equals(Snapshot.refusal(null, DemoKind.KIND, far)), "no snapshot's reason");
			Snapshot.install(snapshot(false, Stage.TRACES, BlockPos.ZERO, level));
			helper.assertFalse(Live.generate(level, k0(level), far).isValid(), "a start with the switch off");
			// Round 3 (D-108): the places generate from the world's creation; a stage raised by hand is still honoured.
			Snapshot.install(snapshot(true, Stage.ALONE, BlockPos.ZERO, level));
			helper.assertTrue(Live.generate(level, k0(level), far).isValid(), "no start in Alone");
			DemoKind.CONFIG.fromStage = 2;
			helper.assertFalse(Live.generate(level, k0(level), near).isValid(), "a start under a stage raised by hand");
			DemoKind.CONFIG.fromStage = 0;
			Snapshot.install(snapshot(true, Stage.TRACES, BlockPos.ZERO, level));
			helper.assertTrue(Live.generate(level, k0(level), far).isValid(), "no start with everything allowed");
			DemoKind.CONFIG.minOriginDistance = 500;
			helper.assertFalse(Live.generate(level, k0(level), near).isValid(), "a start 40 from the origin");
			helper.assertTrue(Live.generate(level, k0(level), far).isValid(), "no start far away");
			Snapshot.install(snapshot(true, Stage.TRACES, new BlockPos(far.getMiddleBlockX() + 100, 0, far.getMiddleBlockZ()), level));
			helper.assertFalse(Live.generate(level, k0(level), far).isValid(), "a start 100 from the world spawn");
			// A start whose box reaches past 8 chunks is refused too.
			BoundingBox wide = new BoundingBox(far.getMinBlockX() - 200, 0, far.getMinBlockZ(), far.getMinBlockX() + 10, 10, far.getMinBlockZ() + 10);
			helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Sc2Structure.withinReach(wide, far), "a box 13 chunks wide is within reach");
		} finally {
			DemoKind.CONFIG.fromStage = 0;
			DemoKind.CONFIG.minOriginDistance = 0;
			DemoKind.disarm(far);
			DemoKind.disarm(near);
			Snapshot.install(before);
		}
		helper.succeed();
	}

	// --- worldgen equals Live ---

	/**
	 * The demo (20 x 12 x 20, across four chunks, turned EAST, floating one over the ground so its foundation fills, over
	 * water, sand and a cave pocket by its cellar) built by worldgen's own path (the structure's real start, saved and loaded
	 * back, placed chunk by chunk and finished as vanilla finishes a chunk) and by {@link Live#place} 256 blocks away: block
	 * for block and block entity for block entity the same; the seal took the pockets; the recorder knew the start once,
	 * a second load of the same start included.
	 */
	@GameTest(environment = KIT, maxTicks = 200)
	public void theDemoBuiltByWorldgenEqualsLivePlace(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Snapshot before = Snapshot.current();
		int x0 = 40_000 + 6;
		int z0 = 40_000 + 6;
		int shift = 256;
		BoundingBox area = new BoundingBox(x0 - 4, level.getMinY(), z0 - 4, x0 + shift + DemoKind.SIDE + 4, level.getMaxY(), z0 + DemoKind.SIDE + 4);
		loadChunks(level, area);
		int ground = Terrains.noise(level).ground(x0, z0);
		helper.assertTrue(ground == new com.forzacode.a1016_02.world.live.LiveTerrain(level).ground(x0 + 1, z0 + 1),
				"the noise and the level disagree on the ground: " + ground);
		// The flat world is thin: a stone platform 6 high under both sites, the house's floor 2 over it (its foundation
		// fills the gap), so its cellar (heights y0-3..y0-1) is cut into stone.
		for (BlockPos pos : BlockPos.betweenClosed(x0 - 2, ground + 1, z0 - 2, x0 + DemoKind.SIDE + 1, ground + 6, z0 + DemoKind.SIDE + 1)) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2 | 16 | 512);
			level.setBlock(pos.offset(shift, 0, 0), Blocks.STONE.defaultBlockState(), 2 | 16 | 512);
		}
		int y0 = ground + 8;
		long seed = 0x5EEDL;
		Direction facing = Direction.EAST;
		Start start = new Start(new BlockPos(x0, y0, z0), facing, 0, seed);
		// The cellar (EAST: x = x0+19-b for b 8..11, z = z0+r for r 8..11): a cave pocket 2 under it, water and sand 2 beside
		// it, all in the stone, the same at both sites; the seal must fill all three.
		List<BlockPos> pockets = List.of(new BlockPos(x0 + 19 - 9, y0 - 5, z0 + 9), new BlockPos(x0 + 19 - 9, y0 - 4, z0 + 6),
				new BlockPos(x0 + 19 - 10, y0 - 4, z0 + 13));
		List<BlockState> leaks = List.of(Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.SAND.defaultBlockState());
		for (int i = 0; i < pockets.size(); i++) {
			level.setBlock(pockets.get(i), leaks.get(i), 2 | 16 | 512);
			level.setBlock(pockets.get(i).offset(shift, 0, 0), leaks.get(i), 2 | 16 | 512);
		}
		ChunkPos chunk = ChunkPos.containing(start.origin());
		Sc2Data data = new Sc2Data();
		try {
			Recorder.drain(new Sc2Data());
			Snapshot.install(snapshot(true, Stage.TRACES, BlockPos.ZERO, level));
			DemoKind.arm(chunk, start);
			StructureStart made = Live.generate(level, k0(level), chunk);
			helper.assertTrue(made.isValid(), "worldgen made no start");
			StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
			CompoundTag saved = made.createTag(context, chunk);
			StructureStart loaded = StructureStart.loadStaticStart(context, saved, level.getSeed());
			helper.assertTrue(loaded != null && loaded.isValid() && loaded.getPieces().getFirst() instanceof Sc2Piece, "the saved start did not load");
			Live.placeStart(level, loaded);
			helper.assertTrue(Recorder.drain(data) == 1 && data.places().size() == 1, "the recorder knew " + data.places().size());
			// The same start loaded again and placed again (a chunk saved before the rest was placed): still known once.
			StructureStart again = StructureStart.loadStaticStart(context, saved, level.getSeed());
			Live.placeStart(level, again);
			helper.assertTrue(Recorder.drain(data) == 0 && data.places().size() == 1, "a second load was known again");
			Sc2Data.Known known = data.places().getFirst();
			helper.assertTrue(known.start().equals(DemoKind.KIND.resolve(start)) && known.kind().equals(DemoKind.ID) && known.place().kind() == Remains.Kind.FLOOR,
					"the recorded place: " + known);

			Live.Result live = Live.place(level, DemoKind.KIND, start.moved(shift, 0, 0));
			BoundingBox box = made.getBoundingBox();
			helper.assertTrue(live.box().equals(box.moved(shift, 0, 0)) && box.getXSpan() == 20 && box.getYSpan() == 12 && box.getZSpan() == 20,
					"the boxes: " + box + " / " + live.box());
			int compared = 0;
			int structureBlocks = 0;
			List<String> differ = new ArrayList<>();
			BoundingBox around = box.inflatedBy(1);
			for (BlockPos pos : BlockPos.betweenClosed(around.minX(), around.minY(), around.minZ(), around.maxX(), around.maxY(), around.maxZ())) {
				BlockPos other = pos.offset(shift, 0, 0);
				BlockState a = level.getBlockState(pos);
				BlockState b = level.getBlockState(other);
				compared++;
				if (a.is(Blocks.COBBLESTONE) || a.is(Blocks.MOSSY_COBBLESTONE) || a.is(Blocks.OAK_PLANKS)) {
					structureBlocks++;
				}
				if (a != b && differ.size() < 8) {
					differ.add(pos.toShortString() + " " + a + " / " + b);
				}
				BlockEntity ea = level.getBlockEntity(pos);
				BlockEntity eb = level.getBlockEntity(other);
				if ((ea == null) != (eb == null) || ea != null && !ea.saveCustomOnly(level.registryAccess()).equals(eb.saveCustomOnly(level.registryAccess()))) {
					differ.add(pos.toShortString() + " block entity " + (ea == null ? null : ea.saveCustomOnly(level.registryAccess())) + " / "
							+ (eb == null ? null : eb.saveCustomOnly(level.registryAccess())));
				}
			}
			helper.assertTrue(differ.isEmpty(), "worldgen and Live differ (" + compared + " compared): " + differ);
			// Lit alike once the light engine took both in: the roof's glowstone lights the floor.
			helper.assertTrue(Live.settleLight(level, box) && Live.settleLight(level, live.box()), "the light engine did not settle");
			for (BlockPos pos : BlockPos.betweenClosed(x0 + 1, y0 + 1, z0 + 1, x0 + 18, y0 + 4, z0 + 18)) {
				int a = level.getBrightness(LightLayer.BLOCK, pos);
				int b = level.getBrightness(LightLayer.BLOCK, pos.offset(shift, 0, 0));
				if (a != b && differ.size() < 8) {
					differ.add(pos.toShortString() + " light " + a + " / " + b);
				}
			}
			helper.assertTrue(differ.isEmpty(), "worldgen and Live are lit differently: " + differ);
			helper.assertTrue(level.getBrightness(LightLayer.BLOCK, new BlockPos(x0 + 14, y0 + 1, z0 + 5)) >= 8, "the floor under a light is dark");
			helper.assertTrue(structureBlocks > 1000, "only " + structureBlocks + " blocks of the house");
			// It was really built: the door faces EAST, the chest holds its stacks, the panes joined, the pockets sealed.
			BlockPos door = new BlockPos(x0 + 19, y0 + 1, z0 + 9);
			helper.assertTrue(level.getBlockState(door).isAir() && level.getBlockState(door.above()).isAir() && level.getBlockState(door.above(2)).isSolid(),
					"no doorway in the east wall");
			helper.assertTrue(known.place().entrance().equals(door) && known.place().entranceFacing() == Direction.EAST, "the entrance: " + known.place());
			BlockEntity chest = level.getBlockEntity(new BlockPos(x0 + 19 - 18, y0 + 1, z0 + 2));
			helper.assertTrue(chest instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c && !c.isEmpty(), "the chest is empty or missing");
			BlockState pane = level.getBlockState(new BlockPos(x0 + 19 - 5, y0 + 2, z0));
			helper.assertTrue(pane.is(Blocks.GLASS_PANE) && (pane.getValue(net.minecraft.world.level.block.CrossCollisionBlock.EAST)
					|| pane.getValue(net.minecraft.world.level.block.CrossCollisionBlock.WEST)), "the pane did not join its neighbours: " + pane);
			for (BlockPos pocket : pockets) {
				helper.assertTrue(level.getBlockState(pocket).is(Blocks.COBBLESTONE), "the seal left " + level.getBlockState(pocket) + " at " + pocket.toShortString());
			}
			BlockPos foundation = new BlockPos(x0 + 3, y0 - 1, z0 + 3);
			helper.assertTrue(level.getBlockState(foundation).is(Blocks.COBBLESTONE), "no foundation under the floor: " + level.getBlockState(foundation));
		} finally {
			DemoKind.disarm(chunk);
			Recorder.drain(new Sc2Data());
			Snapshot.install(before);
		}
		helper.succeed();
	}

	/**
	 * {@link com.forzacode.a1016_02.world.sc2.kit.Draw#tunnel}: a 1 x 2 tunnel rising one in two (and one falling one in
	 * two) through stone: the floor steps as asked, and at every step the higher ceiling is carved over both cells (3 of
	 * air), so it is walked without a jump into the ceiling; elsewhere 2 high.
	 */
	@GameTest(environment = KIT)
	public void aSlopedTunnelKeepsHeadroomAtEveryStep(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = new BlockPos(41_000, Terrains.noise(level).ground(41_000, 41_000) + 1, 41_000);
		BoundingBox stone = new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 23, o.getY() + 15, o.getZ() + 6);
		BlockPos up = o.offset(1, 1, 1);
		BlockPos down = o.offset(1, 13, 4);
		Shape shape = new Shape() {
			@Override
			public BoundingBox box() {
				return new BoundingBox(stone.minX(), stone.minY(), stone.minZ(), stone.maxX(), stone.maxY(), stone.maxZ());
			}

			@Override
			public void fill(com.forzacode.a1016_02.world.sc2.kit.Writer w, BoundingBox clip) {
				com.forzacode.a1016_02.world.sc2.kit.Draw.fill(w, stone, Blocks.STONE.defaultBlockState());
				com.forzacode.a1016_02.world.sc2.kit.Draw.tunnel(w, up, Direction.EAST, 20, 1, 2, 1, 2, Blocks.AIR.defaultBlockState());
				com.forzacode.a1016_02.world.sc2.kit.Draw.tunnel(w, down, Direction.EAST, 20, 1, 2, -1, 2, Blocks.AIR.defaultBlockState());
			}

			@Override
			public BlockPos anchor() {
				return o;
			}

			@Override
			public BlockPos entrance() {
				return up;
			}

			@Override
			public Direction entranceFacing() {
				return Direction.WEST;
			}
		};
		Live.place(level, DemoKind.KIND, shape, Terrains.frozen(level), new Start(o, Direction.WEST, 0, 0L));
		for (int i = 0; i < 20; i++) {
			BlockPos floorUp = up.offset(i, i / 2, 0);
			boolean beforeRise = i % 2 == 1 && i < 19;
			helper.assertTrue(level.getBlockState(floorUp.below()).is(Blocks.STONE) && level.getBlockState(floorUp).isAir()
					&& level.getBlockState(floorUp.above()).isAir() && level.getBlockState(floorUp.above(2)).isAir() == beforeRise,
					"rising, cell " + i + " at " + floorUp.toShortString());
			BlockPos floorDown = down.offset(i, -(i / 2), 0);
			boolean afterDrop = i % 2 == 0 && i > 0;
			helper.assertTrue(level.getBlockState(floorDown.below()).is(Blocks.STONE) && level.getBlockState(floorDown).isAir()
					&& level.getBlockState(floorDown.above()).isAir() && level.getBlockState(floorDown.above(2)).isAir() == afterDrop,
					"falling, cell " + i + " at " + floorDown.toShortString());
		}
		helper.succeed();
	}

	/** A piece saves only its parameters (under 200 bytes); a saved start reloads to the same start and shape. */
	@GameTest(environment = KIT)
	public void aPiecesNbtHoldsOnlyItsParametersAndReloadsToTheSameShape(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		Snapshot before = Snapshot.current();
		ChunkPos chunk = new ChunkPos(3100, 3100);
		Start start = new Start(new BlockPos(chunk.getMinBlockX() + 6, -40, chunk.getMinBlockZ() + 6), Direction.SOUTH, 3, 0xABCDEF12345L);
		try {
			Snapshot.install(snapshot(true, Stage.PROXIMITY, BlockPos.ZERO, level));
			DemoKind.arm(chunk, start);
			StructureStart made = Live.generate(level, k0(level), chunk);
			helper.assertTrue(made.isValid(), "no start");
			StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
			CompoundTag saved = made.createTag(context, chunk);
			ListTag children = saved.getListOrEmpty("Children");
			helper.assertTrue(children.size() == 1, "pieces: " + children.size());
			CompoundTag piece = children.getCompoundOrEmpty(0);
			helper.assertTrue(piece.keySet().equals(Set.of("id", "BB", "O", "GD", "k", "v", "s", "o", "f", "g")), "the piece saves " + piece.keySet());
			helper.assertTrue("a1016_02:world/sc2p".equals(piece.getStringOr("id", "")), "the piece type: " + piece.getStringOr("id", ""));
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			NbtIo.write(piece, new DataOutputStream(bytes));
			helper.assertTrue(bytes.size() < 200, "the piece is " + bytes.size() + " bytes");
			StructureStart loaded = StructureStart.loadStaticStart(context, saved, level.getSeed());
			helper.assertTrue(loaded != null && loaded.getPieces().getFirst() instanceof Sc2Piece, "did not load");
			Sc2Piece back = (Sc2Piece) loaded.getPieces().getFirst();
			Shape shape = back.shape(Terrains.noise(level));
			Shape original = house(level, start);
			helper.assertTrue(back.kind().equals(DemoKind.ID) && back.start().equals(DemoKind.KIND.resolve(start)), "the loaded start: " + back.start());
			helper.assertTrue(shape != null && shape.box().equals(original.box()) && shape.anchor().equals(original.anchor())
					&& shape.entrance().equals(original.entrance()) && back.getBoundingBox().equals(original.box()), "the loaded shape differs");
			// Without siting (a test's own start): the same piece; and Live's reload gives a fresh piece of the same start.
			StructureStart direct = Live.startOf(level, k0(level), start);
			StructureStart reloaded = Live.reload(level, direct);
			helper.assertTrue(direct.isValid() && reloaded != null && reloaded.getPieces().getFirst() instanceof Sc2Piece p && p != direct.getPieces().getFirst()
					&& p.start().equals(DemoKind.KIND.resolve(start)) && reloaded.getBoundingBox().equals(original.box()), "startOf and reload");
			// A piece of a kind this jar does not know loads and places nothing.
			CompoundTag unknown = piece.copy();
			unknown.putString("k", "zz");
			Sc2Piece stray = new Sc2Piece(unknown);
			helper.assertTrue(stray.shape(Terrains.noise(level)) == null, "an unknown kind made a shape");
		} finally {
			DemoKind.disarm(chunk);
			Snapshot.install(before);
		}
		helper.succeed();
	}

	/**
	 * R12-2, a shape's version kept with its start: a kind of the test's own whose shape changed (its version 2 stands a
	 * block higher than its version 1). A new start is made with version 2 and its piece saves it ({@code g}); a piece
	 * saved before versions ({@code g} absent) and one saved with version 1 both rebuild version 1, whatever the kind's
	 * current version, so a place half built when its shape changed is finished as it was begun; the kit's data keeps the
	 * version with a place's start (absent: 1) and its inside rebuilds that version; a start not tied yet builds the
	 * current one.
	 */
	@GameTest
	public void aShapesVersionIsKeptWithItsStartAcrossAChange(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String id = "k9";
		Sc2Kind.Shapes changed = (start, terrain) -> DemoKind.KIND.shapes().create(start.versionOr(2) >= 2 ? start.moved(0, 1, 0) : start, terrain);
		Sc2Kind kind = new Sc2Kind(id, Remains.Kind.FLOOR, () -> DemoKind.CONFIG, query -> Optional.empty(), changed, Sc2Kind.Interiors.FROM_SHAPE, 2);
		Sc2Kind.register(kind);
		try {
			Start start = new Start(new BlockPos(52_000, -40, 52_000), Direction.EAST, 3, 0x2E2L);
			Shape v1 = changed.create(start.withVersion(1), Terrains.noise(level));
			Shape v2 = changed.create(start.withVersion(2), Terrains.noise(level));
			helper.assertTrue(v1.box().minY() + 1 == v2.box().minY(), "the two versions are not told apart");
			helper.assertTrue(kind.resolve(start).version() == 2 && kind.shape(start, Terrains.noise(level)).box().equals(v2.box()), "a new start is not made with version 2");
			Sc2Piece made = new Sc2Piece(id, start, v2);
			helper.assertTrue(made.start().version() == 2, "the new piece's start: " + made.start());
			StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
			CompoundTag saved = made.createTag(context);
			helper.assertTrue(saved.getIntOr("g", -1) == 2, "the piece did not save its version: " + saved);
			Sc2Piece again = new Sc2Piece(saved);
			helper.assertTrue(again.start().equals(made.start()) && again.shape(Terrains.noise(level)).box().equals(v2.box()), "version 2 did not load as 2");
			CompoundTag old = saved.copy();
			old.remove("g");
			Sc2Piece before = new Sc2Piece(old);
			helper.assertTrue(before.start().version() == Start.FIRST && before.shape(Terrains.noise(level)).box().equals(v1.box()),
					"a piece saved before versions does not rebuild version 1: " + before.start());
			CompoundTag first = saved.copy();
			first.putInt("g", 1);
			Sc2Piece half = new Sc2Piece(first);
			helper.assertTrue(half.shape(Terrains.noise(level)).box().equals(v1.box()) && new Sc2Piece(half.createTag(context)).start().version() == 1,
					"a version 1 piece is rebuilt with the current version");
			// The kit's data: the place's start keeps its version; one kept before versions is version 1; its inside is that version's.
			Remains.Place place = new Remains.Place(Remains.Kind.FLOOR, level.dimension(), v1.box(), v1.anchor(), v1.entrance(), v1.entranceFacing(),
					Optional.empty(), start.seed(), start.variant());
			Sc2Data data = new Sc2Data();
			data.add(Sc2Data.Known.of(place, id, start.withVersion(1), new CompoundTag()));
			Remains.Place other = new Remains.Place(Remains.Kind.FLOOR, level.dimension(), v2.box().moved(0, 0, 64), v2.anchor().south(64),
					v2.entrance().south(64), v2.entranceFacing(), Optional.empty(), start.seed(), start.variant());
			data.add(Sc2Data.Known.of(other, id, start.moved(0, 0, 64), new CompoundTag()));
			Tag tag = Sc2Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
			Sc2Data back = Sc2Data.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
			helper.assertTrue(back.places().get(0).start().version() == 1 && back.places().get(1).start().version() == 2, "the data lost the versions");
			CompoundTag stripped = (CompoundTag) tag.copy();
			stripped.getListOrEmpty("places").getCompoundOrEmpty(1).getCompoundOrEmpty("start").remove("g");
			Sc2Data oldData = Sc2Data.CODEC.parse(NbtOps.INSTANCE, stripped).getOrThrow();
			helper.assertTrue(oldData.places().get(1).start().version() == Start.FIRST, "a start kept before versions is not version 1");
			Interior inside = kind.interiors().of(level, kind, place, back.places().get(0).start(), new CompoundTag());
			BlockPos cellar = v1.anchor();
			helper.assertTrue(inside.contains(cellar.getX(), cellar.getY(), cellar.getZ()) == v1.interior().contains(cellar.getX(), cellar.getY(), cellar.getZ()),
					"the inside is not version 1's");
		} finally {
			Sc2Kind.unregister(id);
		}
		helper.succeed();
	}

	// --- the recorder and the snapshot ---

	/** The recorder keeps a start once (offered twice) and the remains then know it. */
	@GameTest
	public void theRecorderKeepsEachStartOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Start start = new Start(helper.absolutePos(new BlockPos(2, 2, 2)), Direction.WEST, 0, 5L);
		Shape shape = house(level, start);
		Sc2Data data = new Sc2Data();
		Recorder.drain(new Sc2Data());
		Recorder.offer(DemoKind.ID, start, level.dimension(), Remains.Kind.FLOOR, shape);
		Recorder.offer(DemoKind.ID, start, level.dimension(), Remains.Kind.FLOOR, shape);
		helper.assertTrue(Recorder.drain(data) == 1 && !Recorder.pending(), "the recorder kept it more than once");
		RemainsImpl remains = RemainsImpl.over(data);
		helper.assertTrue(remains.known(level).size() == 1 && remains.known(level).getFirst().anchor().equals(shape.anchor()), "not known");
		helper.assertTrue(remains.containing(level, shape.anchor().above()).isPresent(), "its inside is not inside");
		helper.succeed();
	}

	/** The server stop clears the snapshot and the queues, so a second server in the same JVM starts with none of the first's. */
	@GameTest
	public void theSnapshotAndTheQueuesAreClearedAtServerStop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Snapshot before = Snapshot.current();
		helper.assertTrue(before != null && before.seed() == level.getSeed(), "no live snapshot: " + before);
		helper.assertTrue(before.configs().containsKey(DemoKind.ID), "the snapshot has no demo config: " + before.configs());
		Snapshot stale = new Snapshot(true, Stage.REMOVAL, new BlockPos(1, 2, 3), 1L, 2L, Map.of());
		Snapshot.install(stale);
		Start start = new Start(helper.absolutePos(BlockPos.ZERO), Direction.NORTH, 0, 1L);
		Recorder.offer(DemoKind.ID, start, level.dimension(), Remains.Kind.FLOOR, house(level, start));
		try {
			WorldSc2.stopped(server);
			helper.assertTrue(Snapshot.current() == null, "the snapshot outlived the stop");
			helper.assertFalse(Recorder.pending(), "the queue outlived the stop");
			helper.assertTrue(RemainsImpl.LIVE.known(level).isEmpty() && RemainsImpl.LIVE.data().places().isEmpty(), "the live remains outlived the stop");
			helper.assertFalse(com.forzacode.a1016_02.world.sc2.kit.Plans.ready(), "the plans outlived the stop");
		} finally {
			// The next start's own: this server's, not the stale one (its plans first, as SERVER_STARTED reads them).
			RemainsImpl.LIVE.attach(server);
			com.forzacode.a1016_02.world.sc2.kit.Plans.load(com.forzacode.a1016_02.world.WorldData.get(server).sc2());
			Snapshot.refresh(server);
		}
		Snapshot now = Snapshot.current();
		helper.assertTrue(now != null && now != stale && now.seed() == level.getSeed()
				&& now.stage() == com.forzacode.a1016_02.core.HerobrineState.get(server).stage(), "the new snapshot: " + now);
		helper.succeed();
	}

	/**
	 * R12-15: a start is decided only with the live overworld's generator; a data pack's or a mod's dimension with the
	 * overworld's biomes gets none (the nether's generator stands in for one here). A test's own snapshot has no rule.
	 */
	@GameTest
	public void noStartInAnotherDimension(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		net.minecraft.server.level.ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
		helper.assertTrue(nether != null, "no nether in the test server");
		Snapshot before = Snapshot.current();
		try {
			Snapshot.refresh(server);
			helper.assertTrue(Snapshot.inOverworld(server.overworld().getChunkSource().getGenerator()), "the overworld's own generator refused");
			helper.assertFalse(Snapshot.inOverworld(nether.getChunkSource().getGenerator()), "another dimension's generator allowed");
			Snapshot.install(before);
			helper.assertTrue(Snapshot.inOverworld(nether.getChunkSource().getGenerator()), "a test's own snapshot keeps a dimension rule");
		} finally {
			Snapshot.refresh(server);
		}
		helper.succeed();
	}

	// --- the remains' index ---

	/** Through the chunk-grid index, containing matches a brute-force search over every place's interior at 200 points. */
	@GameTest
	public void remainsContainingMatchesABruteForceSearch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Random random = new Random(11);
		Sc2Data data = new Sc2Data();
		List<Sc2Data.Known> all = new ArrayList<>();
		BlockPos base = new BlockPos(70_000, 0, 70_000);
		for (int i = 0; i < 14; i++) {
			Start start = new Start(base.offset(random.nextInt(120), random.nextInt(20), random.nextInt(120)), Direction.from2DDataValue(random.nextInt(4)), 0,
					random.nextLong());
			Sc2Data.Known known = known(level, start);
			if (data.add(known)) {
				all.add(known);
			}
		}
		RemainsImpl remains = RemainsImpl.over(data);
		int inside = 0;
		for (int n = 0; n < 200; n++) {
			BlockPos pos = n % 2 == 0 ? base.offset(random.nextInt(150), random.nextInt(35) - 5, random.nextInt(150))
					: all.get(random.nextInt(all.size())).place().anchor().offset(random.nextInt(9) - 4, random.nextInt(5) - 2, random.nextInt(9) - 4);
			Optional<Remains.Place> brute = Optional.empty();
			for (Sc2Data.Known known : all) {
				Interior interior = house(level, known.start()).interior();
				if (known.place().boxHolds(pos) && interior.contains(pos)) {
					brute = Optional.of(known.place());
					break;
				}
			}
			Optional<Remains.Place> indexed = remains.containing(level, pos);
			helper.assertTrue(indexed.equals(brute), "at " + pos.toShortString() + " the index said " + indexed + ", the search " + brute);
			if (brute.isPresent()) {
				inside++;
			}
		}
		helper.assertTrue(inside >= 40, "only " + inside + " of 200 points were inside a place");
		// nearest: the place whose box is nearest, within the radius, of the asked kinds only.
		Remains.Place first = all.getFirst().place();
		helper.assertTrue(remains.nearest(level, first.anchor(), Set.of(Remains.Kind.FLOOR), 4).isPresent(), "nearest missed the place it stands in");
		helper.assertTrue(remains.nearest(level, first.anchor(), Set.of(Remains.Kind.PYRAMID), 1000).isEmpty(), "nearest found another kind");
		helper.assertTrue(remains.nearest(level, base.offset(-500, 0, -500), Set.of(Remains.Kind.FLOOR), 100).isEmpty(), "nearest found one 500 off");
		helper.succeed();
	}

	/**
	 * R12-21: a place's inside is made once: a new place recorded (the index rebuilt) keeps every inside already made, and
	 * only the new place's is made when asked.
	 */
	@GameTest
	public void anInsideIsKeptAcrossTheIndexsRebuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String id = "k8";
		int[] made = {0};
		Sc2Kind.Interiors counting = (lvl, kind, place, start, extra) -> {
			made[0]++;
			return Interior.box(place.box());
		};
		Sc2Kind.register(new Sc2Kind(id, Remains.Kind.FLOOR, () -> DemoKind.CONFIG, query -> Optional.empty(), DemoKind.KIND.shapes(), counting, 1));
		try {
			Sc2Data data = new Sc2Data();
			Sc2Data.Known a = known(level, new Start(new BlockPos(61_000, -40, 61_000), Direction.NORTH, 0, 3L));
			data.add(Sc2Data.Known.of(a.place(), id, a.start(), a.extra()));
			RemainsImpl remains = RemainsImpl.over(data);
			helper.assertTrue(remains.containing(level, a.place().anchor()).isPresent(), "the first place's anchor is not inside it");
			helper.assertTrue(made[0] == 1, "insides made: " + made[0]);
			Sc2Data.Known b = known(level, new Start(new BlockPos(61_400, -40, 61_000), Direction.NORTH, 0, 4L));
			data.add(Sc2Data.Known.of(b.place(), id, b.start(), b.extra()));
			helper.assertTrue(remains.containing(level, a.place().anchor()).isPresent() && made[0] == 1, "the rebuild made the first inside again: " + made[0]);
			helper.assertTrue(remains.containing(level, b.place().anchor()).isPresent() && made[0] == 2, "the new place's inside: " + made[0]);
		} finally {
			Sc2Kind.unregister(id);
		}
		helper.succeed();
	}

	// --- visits ---

	/**
	 * Two players: one walks in through the door (enter, the way in counted once, visited, the trespass time), one walks in
	 * through a wall's gap elsewhere (enter, no way in); out (leave); visited stays per player.
	 */
	@GameTest(structure = BIG)
	public void visitsEnterLeaveVisitedAndTheTrespassTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Start start = new Start(helper.absolutePos(new BlockPos(2, 2, 2)), Direction.NORTH, 0, 9L);
		Sc2Data data = new Sc2Data();
		Sc2Data.Known known = known(level, start);
		data.add(known);
		Remains.Place place = known.place();
		RemainsImpl remains = RemainsImpl.over(data);
		List<ServerPlayer> online = new ArrayList<>();
		Seen seen = new Seen();
		Visits visits = new Visits(remains, seen, s -> List.copyOf(online), () -> true);
		TestPlayer ana = new TestPlayer(level, "ana", GameType.SURVIVAL);
		TestPlayer ben = new TestPlayer(level, "ben", GameType.SURVIVAL);
		online.add(ana);
		online.add(ben);
		BlockPos door = place.entrance();
		ana.snapTo(door.getX() + 0.5, door.getY(), door.getZ() - 1.5, 0, 0);
		ben.snapTo(start.origin().getX() + 15.5, start.origin().getY() + 1, start.origin().getZ() - 6.5, 0, 0);
		visits.scan(server, 100, 1000);
		helper.assertTrue(seen.events.isEmpty() && !data.visited(ana.getUUID(), place), "outside, something happened: " + seen.events);
		BlockPos in = place.anchor();
		ana.snapTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0, 0);
		ben.snapTo(start.origin().getX() + 15.5, start.origin().getY() + 1, start.origin().getZ() + 15.5, 0, 0);
		visits.scan(server, 120, 1020);
		helper.assertTrue(seen.events.equals(List.of("+ana", "way:ana", "+ben")), "the enters: " + seen.events);
		helper.assertTrue(data.visited(ana.getUUID(), place) && data.visited(ben.getUUID(), place) && remains.visited(ana.getUUID(), place),
				"visited is not kept");
		helper.assertTrue(remains.trespassedAt(ana.getUUID()) == 1020 && visits.placeOf(ana.getUUID()).equals(Optional.of(place)), "the trespass time");
		helper.assertTrue(visits.inside(server, place).size() == 2, "inside: " + visits.inside(server, place));
		visits.scan(server, 140, 1040);
		helper.assertTrue(seen.events.size() == 3 && remains.trespassedAt(ana.getUUID()) == 1040, "staying inside fired " + seen.events);
		ana.snapTo(door.getX() + 0.5, door.getY(), door.getZ() - 4.5, 0, 0);
		visits.scan(server, 160, 1060);
		helper.assertTrue(seen.events.get(3).equals("-ana") && seen.events.size() == 4 && remains.trespassedAt(ana.getUUID()) == 1040, "the leave: " + seen.events);
		helper.assertTrue(data.visited(ana.getUUID(), place) && visits.placeOf(ana.getUUID()).isEmpty(), "visited forgot her");
		// Back in through a wall's gap, the door long ago: no way in this time.
		ana.snapTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0, 0);
		visits.scan(server, 2000, 3000);
		helper.assertTrue(seen.events.subList(4, seen.events.size()).equals(List.of("+ana")), "a second visit: " + seen.events);
		// Switched off: nothing is looked at, and every visit is forgotten without events.
		Visits off = new Visits(remains, seen, s -> List.copyOf(online), () -> false);
		off.scan(server, 2020, 3020);
		helper.assertTrue(seen.events.size() == 5 && off.placeOf(ana.getUUID()).isEmpty(), "the switch off: " + seen.events);
		helper.succeed();
	}

	/**
	 * Three players, one a spectator: the spectator standing inside is never inside nor visited; one leaves the game
	 * inside (her leave fires while she is listed); one becomes a spectator inside (his leave too).
	 */
	@GameTest(structure = BIG)
	public void visitsWithThreePlayersOneASpectatorOneLeavingInside(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Start start = new Start(helper.absolutePos(new BlockPos(2, 2, 2)), Direction.NORTH, 0, 9L);
		Sc2Data data = new Sc2Data();
		Sc2Data.Known known = known(level, start);
		data.add(known);
		Remains.Place place = known.place();
		RemainsImpl remains = RemainsImpl.over(data);
		List<ServerPlayer> online = new ArrayList<>();
		Seen seen = new Seen();
		Visits visits = new Visits(remains, seen, s -> List.copyOf(online), () -> true);
		TestPlayer ana = new TestPlayer(level, "ana", GameType.SURVIVAL);
		TestPlayer ben = new TestPlayer(level, "ben", GameType.SURVIVAL);
		TestPlayer cid = new TestPlayer(level, "cid", GameType.SPECTATOR);
		online.addAll(List.of(ana, ben, cid));
		BlockPos in = place.anchor();
		for (TestPlayer p : List.of(ana, ben, cid)) {
			p.snapTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0, 0);
		}
		visits.scan(server, 20, 500);
		helper.assertTrue(seen.events.equals(List.of("+ana", "+ben")), "the enters: " + seen.events);
		helper.assertFalse(data.visited(cid.getUUID(), place) || remains.trespassedAt(cid.getUUID()) != -1, "the spectator was visited");
		helper.assertTrue(visits.inside(server, place).size() == 2, "inside: " + visits.inside(server, place));
		// Ana leaves the game inside: her leave, while she is still listed.
		visits.stopped(server, ana.getUUID());
		online.remove(ana);
		helper.assertTrue(seen.events.equals(List.of("+ana", "+ben", "-ana")), "her leave: " + seen.events);
		// Ben becomes a spectator inside: his leave at the next look.
		ben.mode = GameType.SPECTATOR;
		visits.scan(server, 40, 520);
		helper.assertTrue(seen.events.equals(List.of("+ana", "+ben", "-ana", "-ben")), "his leave: " + seen.events);
		helper.assertTrue(visits.inside(server, place).isEmpty() && data.visited(ana.getUUID(), place) && data.visited(ben.getUUID(), place),
				"after: inside " + visits.inside(server, place));
		// Back, Ben plays again inside: a new visit.
		ben.mode = GameType.SURVIVAL;
		visits.scan(server, 60, 540);
		helper.assertTrue(seen.events.getLast().equals("+ben"), "his return: " + seen.events);
		helper.succeed();
	}

	// --- answers ---

	private record AnswerRig(Answers answers, Sc2Data data, Remains.Place place, List<ServerPlayer> inside, AtomicBoolean seen, Set<UUID> quiet,
			AtomicLong day, AtomicLong now, List<String> answered, BlockPos stone) {
	}

	private static AnswerRig rig(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Start start = new Start(helper.absolutePos(new BlockPos(2, 2, 2)), Direction.NORTH, 0, 21L);
		Sc2Data data = new Sc2Data();
		Sc2Data.Known known = known(level, start);
		data.add(known);
		List<ServerPlayer> inside = new ArrayList<>();
		AtomicBoolean seen = new AtomicBoolean(true);
		Set<UUID> quiet = new HashSet<>();
		AtomicLong day = new AtomicLong(40);
		AtomicLong now = new AtomicLong(10_000);
		List<String> answered = new ArrayList<>();
		Answers.Env env = new Answers.Env(s -> data, Services.traces().forced(), (l, cells) -> !seen.get(), (s, p) -> List.copyOf(inside),
				uuid -> quiet.contains(uuid), uuid -> 200_000L, s -> null, day::get, now::get,
				(l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString()));
		BlockPos stone = helper.absolutePos(new BlockPos(1, 2, 23));
		helper.setBlock(new BlockPos(1, 2, 23), Blocks.COBBLESTONE);
		return new AnswerRig(Answers.over(env), data, known.place(), inside, seen, quiet, day, now, answered, stone);
	}

	private static Answers.Planner removing(ServerLevel level, BlockPos stone) {
		return (l, place, inside) -> l.getBlockState(stone).isAir() ? null
				: Answers.Edit.of(List.of(stone), traces -> traces.batch(l, CAUSE).remove(stone).commit());
	}

	private static TestPlayer armed(ServerLevel level, String name) {
		TestPlayer player = new TestPlayer(level, name, GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
		player.setHealth(20.0F);
		return player;
	}

	private static void forget(MinecraftServer server) {
		TraceLedger ledger = TraceLedger.get(server);
		for (TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
			if (entry.cause().equals(CAUSE)) {
				ledger.remove(entry);
			}
		}
	}

	/** An answer waits while it would be seen and is done once it is not (counts, events, two players); dropped at its limit. */
	@GameTest(structure = BIG)
	public void answersRetryUntilOutOfViewAndDropAfterTheirLimit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		AnswerRig rig = rig(helper);
		rig.inside().addAll(List.of(armed(level, "ana"), armed(level, "ben")));
		Answers.Rules rules = Answers.Rules.of("k0b", CAUSE).perPlaceEver(2).health(10).tool(Answers::pickaxe).limitSeconds(30);
		try {
			Answers.Result first = rig.answers().submit(level, rig.place(), rules, removing(level, rig.stone()));
			helper.assertTrue(first.status() == Answers.Status.WAITING && "in view".equals(first.reason()), "seen, it did not wait: " + first);
			helper.assertTrue(rig.answers().submit(level, rig.place(), rules, removing(level, rig.stone())).status() == Answers.Status.REFUSED,
					"a second ask while it waits");
			rig.now().addAndGet(100);
			rig.answers().tryAll(server);
			helper.assertTrue(rig.answers().waiting(rig.place(), "k0b") && level.getBlockState(rig.stone()).is(Blocks.COBBLESTONE), "done in view");
			rig.seen().set(false);
			rig.now().addAndGet(100);
			rig.answers().tryAll(server);
			helper.assertFalse(rig.answers().waiting(rig.place(), "k0b"), "still waiting out of view: " + rig.answers().whyWaiting(rig.place(), "k0b"));
			helper.assertTrue(level.getBlockState(rig.stone()).isAir(), "the edit was not made");
			helper.assertTrue(TraceLedger.get(server).entries().stream().anyMatch(e -> e.cause().equals(CAUSE) && e.pos().pos().equals(rig.stone())),
					"not ledgered");
			helper.assertTrue(rig.answered().equals(List.of("k0b:ana", "k0b:ben")), "the events: " + rig.answered());
			helper.assertTrue(rig.data().spent(rig.place(), "k0b").ever() == 1 && rig.data().spent(rig.inside().getFirst().getUUID(), "k0b").on(40) == 1,
					"the counts");

			// The limit: in view until it passes, then dropped (never done after).
			helper.setBlock(new BlockPos(1, 2, 23), Blocks.COBBLESTONE);
			rig.seen().set(true);
			rig.day().set(41);
			Answers.Result second = rig.answers().submit(level, rig.place(), rules, removing(level, rig.stone()));
			helper.assertTrue(second.status() == Answers.Status.WAITING, "the next day: " + second);
			rig.now().addAndGet(30 * 20 + 1);
			rig.answers().tryAll(server);
			helper.assertFalse(rig.answers().waiting(rig.place(), "k0b"), "not dropped at its limit");
			rig.seen().set(false);
			rig.answers().tryAll(server);
			helper.assertTrue(level.getBlockState(rig.stone()).is(Blocks.COBBLESTONE) && rig.answered().size() == 2, "done after its limit");
		} finally {
			forget(server);
		}
		helper.succeed();
	}

	/**
	 * Refused for anyone inside in their quiet, under the health floor, without the tool or in their first hour; for
	 * nobody inside; then the caps: a player once a day, a place once a day, a place twice ever. A later part counts
	 * nothing.
	 */
	@GameTest(structure = BIG)
	public void answersRefuseQuietHealthToolFirstHourAndTheirCaps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		AnswerRig rig = rig(helper);
		rig.seen().set(false);
		Answers.Rules rules = Answers.Rules.of("k0c", CAUSE).perPlaceEver(2).health(10).tool(Answers::pickaxe);
		try {
			helper.assertTrue(rig.answers().submit(level, rig.place(), rules, removing(level, rig.stone())).reason().equals("nobody inside"), "nobody inside");
			TestPlayer ana = armed(level, "ana");
			TestPlayer ben = armed(level, "ben");
			rig.inside().addAll(List.of(ana, ben));
			rig.quiet().add(ben.getUUID());
			// Ben has had it once before (elsewhere, another day): his quiet holds now (his first time would come, R10-7).
			Remains.Place elsewhere = new Remains.Place(Remains.Kind.FLOOR, level.dimension(), new BoundingBox(0, 0, 0, 1, 1, 1), BlockPos.ZERO, BlockPos.ZERO,
					Direction.NORTH, Optional.empty(), 0L, 0);
			rig.data().spend(elsewhere, "k0c", List.of(ben.getUUID()), 30);
			helper.assertTrue(refused(rig, level, rules, "quiet"), "ben in his quiet");
			rig.quiet().clear();
			ben.setHealth(8.0F);
			helper.assertTrue(refused(rig, level, rules, "health"), "ben under the health floor");
			ben.setHealth(20.0F);
			ben.getInventory().clearContent();
			helper.assertTrue(refused(rig, level, rules, "tool"), "ben without a pickaxe");
			ben.getInventory().add(new ItemStack(Items.WOODEN_PICKAXE));
			Answers young = Answers.over(new Answers.Env(s -> rig.data(), Services.traces().forced(), (l, c) -> true, (s, p) -> List.copyOf(rig.inside()),
					u -> false, u -> u.equals(ana.getUUID()) ? 1000L : 200_000L, s -> null, rig.day()::get, rig.now()::get, (l, p, a, pl) -> {
					}));
			Answers.Result first = young.submit(level, rig.place(), rules, removing(level, rig.stone()));
			helper.assertTrue(first.status() == Answers.Status.REFUSED && first.reason().contains("first hour"), "ana in her first hour: " + first);
			helper.assertTrue(level.getBlockState(rig.stone()).is(Blocks.COBBLESTONE), "a refusal changed something");

			helper.assertTrue(rig.answers().submit(level, rig.place(), rules, removing(level, rig.stone())).status() == Answers.Status.DONE, "with every rule met");
			helper.setBlock(new BlockPos(1, 2, 23), Blocks.COBBLESTONE);
			helper.assertTrue(refused(rig, level, rules, "had it today"), "twice a day for a player");
			rig.inside().remove(ana);
			rig.inside().remove(ben);
			TestPlayer cid = armed(level, "cid");
			rig.inside().add(cid);
			helper.assertTrue(refused(rig, level, rules, "the place had it today"), "twice a day for a place");
			rig.day().set(41);
			helper.assertTrue(rig.answers().submit(level, rig.place(), rules, removing(level, rig.stone())).status() == Answers.Status.DONE, "the next day");
			helper.setBlock(new BlockPos(1, 2, 23), Blocks.COBBLESTONE);
			rig.day().set(42);
			helper.assertTrue(refused(rig, level, rules, "times"), "a third time ever");
			// A later part of an answer: no caps, nothing counted, no event.
			int events = rig.answered().size();
			helper.assertTrue(rig.answers().submit(level, rig.place(), rules.part(), removing(level, rig.stone())).status() == Answers.Status.DONE, "a part");
			helper.assertTrue(rig.answered().size() == events && rig.data().spent(rig.place(), "k0c").ever() == 2, "a part counted");
		} finally {
			forget(server);
		}
		helper.succeed();
	}

	/**
	 * R10-7 and R12-25: a player's first time of an answer comes even in their quiet (they chose to go in), the next one
	 * keeps the quiet; a tool is no way out for one who may not break blocks (adventure mode), so nothing that needs one
	 * happens to them; and a cell a living thing stands in is taken (R12-11: no block is moved into it).
	 */
	@GameTest(structure = BIG)
	public void aFirstTimeComesInTheQuietAndNothingNeedingAToolToOneWhoMayNotDig(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		AnswerRig rig = rig(helper);
		rig.seen().set(false);
		Answers.Rules rules = Answers.Rules.of("k0q", CAUSE).perPlaceDay(2).health(10).tool(Answers::pickaxe);
		try {
			TestPlayer ana = armed(level, "ana");
			rig.inside().add(ana);
			rig.quiet().add(ana.getUUID());
			helper.assertTrue(rig.answers().submit(level, rig.place(), rules, removing(level, rig.stone())).status() == Answers.Status.DONE,
					"her first time in her quiet");
			helper.setBlock(new BlockPos(1, 2, 23), Blocks.COBBLESTONE);
			rig.day().set(41);
			helper.assertTrue(refused(rig, level, rules, "quiet"), "her second time in her quiet");
			helper.assertTrue(refused(rig, level, rules.part(), "quiet"), "a later part in her quiet");
			rig.quiet().clear();
			ana.mode = GameType.ADVENTURE;
			GameType.ADVENTURE.updatePlayerAbilities(ana.getAbilities());
			helper.assertTrue(refused(rig, level, rules, "may not dig"), "adventure with a pickaxe");
			helper.assertTrue(level.getBlockState(rig.stone()).is(Blocks.COBBLESTONE), "a refusal changed something");
			// A cell a cow stands in is taken.
			BlockPos cell = helper.absolutePos(new BlockPos(5, 2, 5));
			helper.assertFalse(Answers.occupied(level, List.of(cell)), "an empty cell is taken");
			var cow = net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			cow.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(cell), 0, 0);
			level.addFreshEntity(cow);
			helper.assertTrue(Answers.occupied(level, List.of(cell)), "a cow's cell is free");
			cow.discard();
		} finally {
			forget(server);
		}
		helper.succeed();
	}

	private static boolean refused(AnswerRig rig, ServerLevel level, Answers.Rules rules, String why) {
		Answers.Result result = rig.answers().submit(level, rig.place(), rules, removing(level, rig.stone()));
		return result.status() == Answers.Status.REFUSED && result.reason().contains(why) && !rig.answers().waiting(rig.place(), rules.answer());
	}

	// --- the contract ---

	/** The stub knows nothing; the live service is world's. */
	@GameTest
	public void theStubKnowsNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Remains stub = new Remains.Stub();
		helper.assertTrue(stub.known(level).isEmpty() && stub.containing(level, BlockPos.ZERO).isEmpty()
				&& stub.nearest(level, BlockPos.ZERO, Set.of(Remains.Kind.values()), 100_000).isEmpty(), "the stub knows something");
		Remains.Place place = new Remains.Place(Remains.Kind.PYRAMID, level.dimension(), new BoundingBox(0, 0, 0, 1, 1, 1), BlockPos.ZERO, BlockPos.ZERO,
				Direction.NORTH, Optional.empty(), 0, 0);
		helper.assertTrue(!stub.visited(UUID.randomUUID(), place) && stub.trespassedAt(UUID.randomUUID()) == -1, "the stub remembers someone");
		helper.assertTrue(Services.remains() == RemainsImpl.LIVE, "world did not install its remains");
		helper.assertTrue(Remains.Kind.PYRAMID.number() == 16 && Remains.Kind.FLOOR.number() == 20, "the numbers");
		// A place's box is its own: changing the copy changes nothing.
		place.box().move(5, 5, 5);
		helper.assertTrue(place.box().minX() == 0, "a place's box changed from outside");
		helper.succeed();
	}

	/** {@code Live} is called only from the game tests, the gallery and its dev command (never game code, D-093). */
	@GameTest
	public void liveIsCalledOnlyFromTestsAndTheGallery(GameTestHelper helper) throws IOException {
		Path sources = sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found from " + Paths.get("").toAbsolutePath());
		Pattern call = Pattern.compile("\\bLive\\s*\\.\\s*(place|placeStart|generate|terrain)\\s*\\(");
		List<String> callers = new ArrayList<>();
		try (Stream<Path> files = Files.walk(sources)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
				String rel = sources.relativize(file).toString().replace('\\', '/');
				String name = file.getFileName().toString();
				if (rel.endsWith("world/sc2/kit/Live.java") || rel.contains("/debug/gallery/") || rel.contains("/debug/client/gallery/")
						|| name.contains("Exhibit") || name.contains("Gallery")) {
					continue;
				}
				Matcher m = call.matcher(Files.readString(file));
				if (m.find()) {
					callers.add(rel);
				}
			}
		}
		helper.assertTrue(callers.isEmpty(), "game code calls Live: " + callers);
		helper.succeed();
	}

	/** The demo kind is registered only in a development environment (this test run is one). */
	@GameTest
	public void theDemoKindIsRegisteredOnlyInDevelopment(GameTestHelper helper) {
		helper.assertTrue(WorldSc2.demoKinds(false).isEmpty(), "outside development a demo kind would be registered");
		helper.assertTrue(WorldSc2.demoKinds(true).contains(DemoKind.KIND), "in development the demo is not listed");
		helper.assertTrue(FabricLoader.getInstance().isDevelopmentEnvironment() == (Sc2Kind.get(DemoKind.ID) != null), "the demo's registration");
		helper.succeed();
	}

	/** The saved data round-trips; an entry that cannot be read (an unknown kind) is left out, the rest kept. */
	@GameTest
	public void theSavedDataRoundTripsAndSkipsWhatItCannotRead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Sc2Data data = new Sc2Data();
		Sc2Data.Known a = known(level, new Start(new BlockPos(100, 64, 100), Direction.EAST, 2, 77L));
		Sc2Data.Known b = known(level, new Start(new BlockPos(900, 64, 100), Direction.WEST, 0, 78L));
		data.add(a);
		data.add(b);
		UUID ana = UUID.randomUUID();
		data.visit(ana, a.place());
		data.trespass(ana, 5000L);
		data.spend(a.place(), "16b", List.of(ana), 12L);
		Tag tag = Sc2Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		Sc2Data back = Sc2Data.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.places().size() == 2 && back.visited(ana, a.place()) && !back.visited(ana, b.place()) && back.trespassedAt(ana) == 5000L
				&& back.spent(a.place(), "16b").ever() == 1 && back.spent(ana, "16b").on(12L) == 1, "the round trip lost something");
		helper.assertTrue(back.known(a.place()).map(k -> k.start().equals(a.start())).orElse(false), "the start was not kept");
		// One place of a kind this jar does not know: left out, the other kept.
		CompoundTag broken = (CompoundTag) tag.copy();
		ListTag places = broken.getListOrEmpty("places");
		places.getCompoundOrEmpty(0).getCompoundOrEmpty("place").putString("kind", "MOON");
		Sc2Data lenient = Sc2Data.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
		helper.assertTrue(lenient.places().size() == 1 && lenient.trespassedAt(ana) == 5000L, "a broken entry broke the rest: " + lenient.places().size());
		// World's data carries it under sc2: absent when empty, kept, and an unreadable sc2 loads as nothing.
		var ops = Friends.ops(helper);
		WorldData fresh = new WorldData();
		CompoundTag empty = (CompoundTag) WorldData.CODEC.encodeStart(ops, fresh).getOrThrow();
		helper.assertTrue(fresh.sc2().isEmpty() && !empty.contains("sc2"), "an empty sc2 was written");
		fresh.sc2().add(a);
		CompoundTag written = (CompoundTag) WorldData.CODEC.encodeStart(ops, fresh).getOrThrow();
		helper.assertTrue(written.contains("sc2") && WorldData.CODEC.parse(ops, written).getOrThrow().sc2().places().size() == 1, "sc2 was not kept");
		written.putString("sc2", "not places");
		WorldData lenientWorld = WorldData.CODEC.parse(ops, written).getOrThrow();
		helper.assertTrue(lenientWorld.sc2().isEmpty() && lenientWorld.salt() == fresh.salt(), "an unreadable sc2 failed the file");
		helper.succeed();
	}

	/** The mod's {@code src/main/java}: found from the working directory or the mod's own classes, upward. */
	private static Path sourceRoot() {
		List<Path> starts = new ArrayList<>();
		starts.add(Paths.get("").toAbsolutePath());
		FabricLoader.getInstance().getModContainer("a1016_02").ifPresent(mod -> starts.addAll(mod.getRootPaths().stream()
				.filter(p -> p.getFileSystem() == Paths.get("").getFileSystem()).toList()));
		for (Path start : starts) {
			for (Path at = start; at != null; at = at.getParent()) {
				Path src = at.resolve("src/main/java");
				if (Files.isDirectory(src.resolve("com/forzacode/a1016_02"))) {
					return src;
				}
			}
		}
		return null;
	}
}
