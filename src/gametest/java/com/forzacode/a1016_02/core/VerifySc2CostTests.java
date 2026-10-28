package com.forzacode.a1016_02.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.sc2.HuntScene;
import com.forzacode.a1016_02.accident.sc2.Hunts;
import com.forzacode.a1016_02.accident.sc2.Prey;
import com.forzacode.a1016_02.accident.sc2.s25.Pack;
import com.forzacode.a1016_02.accident.sc2.s25.Sc25Config;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Rings;
import com.forzacode.a1016_02.atmosphere.sc2.s22.Processions;
import com.forzacode.a1016_02.atmosphere.sc2.s23.HeldBreath;
import com.forzacode.a1016_02.atmosphere.sc2.s24.Follows;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Payload;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Visits;
import com.forzacode.a1016_02.world.sc2.s16.PyramidAnswers;
import com.forzacode.a1016_02.world.sc2.s18.HollowAnswers;
import com.forzacode.a1016_02.world.sc2.s20.FloorAnswers;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-9 (docs/SELF_CREATED_2.md, "Performance budget", D-104): what the second wave costs on the server at runtime,
 * measured on the test server in a test environment of its own ({@code a1016_02:verify/sc2_cost}, so no other batch runs
 * beside it) and in far patches of the overworld of its own (x from 7.7 million, z -7.7 million). The numbers go to the
 * log ({@code [verify] sc2 cost ...}); the bounds are the budget's (idle) or many times what was measured (scenes), so
 * they catch a hook that stopped being a lookup or a scene that walks every mob, not a busy machine. The worldgen cost
 * per chunk and kind is SC2-H's (its own test class).
 * <ul>
 *   <li>Idle: every server tick hook of the wave once a tick for a minute of ticks, two real players taking part and
 *   nothing running: under 25 microseconds a tick in all (D-118); and the visits' look (every 20 ticks a participant) over 200
 *   known places, a lookup per player.</li>
 *   <li>Scenes: a ring of 14 steered, a held breath of 40, a pack of 5 stalking a stand-in, each scene's own work a
 *   tick (the mobs' walking is vanilla's own); 27's payload in bytes and microseconds.</li>
 * </ul>
 */
public class VerifySc2CostTests {
	private static final String ENV = "a1016_02:verify/sc2_cost";
	/** Each test's own patch, 512 apart along x. */
	private static final int BASE_X = 7_700_000;
	private static final int BASE_Z = -7_700_000;
	private static final AtomicInteger SLOTS = new AtomicInteger();
	/** The wave's idle budget a server tick, in microseconds (D-118, docs/SELF_CREATED_2_R3.md "Cost": "runtime idle"). */
	static final double IDLE_BUDGET_US = 25.0;

	/** One server tick hook of the wave, by name. */
	private record Hook(String name, Consumer<MinecraftServer> tick) {
	}

	/** Every server tick hook the second wave registers (docs/SELF_CREATED_2.md: no per-tick pass over all mobs anywhere). */
	static List<Hook> hooks() {
		List<Hook> hooks = new ArrayList<>();
		hooks.add(new Hook("kit", reflected("com.forzacode.a1016_02.world.sc2.WorldSc2", null)));
		hooks.add(new Hook("16b/c", PyramidAnswers.LIVE::tick));
		hooks.add(new Hook("17", reflected("com.forzacode.a1016_02.world.sc2.s17.ArchiveRules", "LIVE")));
		hooks.add(new Hook("18b", HollowAnswers.LIVE::tick));
		hooks.add(new Hook("20b", FloorAnswers.LIVE::tick));
		hooks.add(new Hook("21", reflected("com.forzacode.a1016_02.atmosphere.sc2.s21.S21", null)));
		hooks.add(new Hook("22", Processions.LIVE::tick));
		hooks.add(new Hook("23", HeldBreath.LIVE::tick));
		hooks.add(new Hook("24", Follows.LIVE::tick));
		hooks.add(new Hook("25/26", Hunts::tick));
		hooks.add(new Hook("27", Sc27.LIVE::tick));
		hooks.add(new Hook("28", reflected("com.forzacode.a1016_02.world.sc2.s28.S28", null)));
		hooks.add(new Hook("30", reflected("com.forzacode.a1016_02.lore.sc2.s30.S30", null)));
		// Round 3's own (R3-V): 16e and 16g's opening, 17's day log (its own every 20 ticks), 19b, 32, 33.
		hooks.add(new Hook("16g", com.forzacode.a1016_02.world.sc2.s16.PyramidOpening.LIVE::tick));
		Consumer<MinecraftServer> dayLog = reflected("com.forzacode.a1016_02.world.sc2.s17.books.LogEvents", null);
		hooks.add(new Hook("17 log", server -> {
			if (server.getTickCount() % 20 == 0) {
				dayLog.accept(server);
			}
		}));
		hooks.add(new Hook("19b", com.forzacode.a1016_02.world.sc2.s19.LineAnswers.LIVE::tick));
		hooks.add(new Hook("32", reflected("com.forzacode.a1016_02.world.sc2.s32.S32", null)));
		hooks.add(new Hook("33", com.forzacode.a1016_02.world.sc2.s16.deep.DepthsAnswers.LIVE::tick));
		return hooks;
	}

	/** A tick method that is not public: {@code tick(MinecraftServer)} of the class, static or of its {@code instance} field. */
	private static Consumer<MinecraftServer> reflected(String type, String instance) {
		try {
			Class<?> c = Class.forName(type);
			Method tick = c.getDeclaredMethod("tick", MinecraftServer.class);
			tick.setAccessible(true);
			Object on = null;
			if (instance != null) {
				Field field = c.getDeclaredField(instance);
				field.setAccessible(true);
				on = field.get(null);
			}
			Object target = on;
			return server -> {
				try {
					tick.invoke(target, server);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(type + ".tick", e);
				}
			};
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("no tick in " + type, e);
		}
	}

	/**
	 * The wave idle: every hook above once each server tick for 1200 ticks with two real players taking part (in the
	 * server's player list, so the live visits, the 28 visitors and every per-participant look see them) and no scene
	 * running. The budget is under {@value #IDLE_BUDGET_US} microseconds a tick for the whole wave (D-104's 20, raised to 25 by
	 * round 3's D-118 for 32's sampler and the day log's rollover): the mean over the ticks, the slowest 0.5% left out (a busy
	 * machine preempts the thread now and then; every-20 and every-100-tick work stays in). It is wall time: 3 to 5 a tick
	 * on a quiet machine (R3-V), 21.9 once under a load of 12 (FIX-R3-C).
	 */
	@GameTest(environment = ENV, maxTicks = 1600)
	public void theWaveIdleCostsUnderItsBudgetATick(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<Hook> hooks = hooks();
		List<ServerPlayer> joined = new ArrayList<>();
		try {
			joined.add(join(helper, "a1016_sc2c_a"));
			joined.add(join(helper, "a1016_sc2c_b"));
		} catch (RuntimeException e) {
			joined.forEach(VerifySc2CostTests::leave);
			throw e;
		}
		Runnable cleanup = () -> joined.forEach(VerifySc2CostTests::leave);
		helper.runAtTickTime(1590, cleanup); // a test that never ends never leaves them online
		int ticks = 1200;
		long[] perTick = new long[ticks];
		long[][] perHook = new long[hooks.size()][1];
		int[] at = {0};
		helper.startSequence().thenIdle(20).thenExecuteFor(ticks, () -> {
			if (at[0] >= ticks) {
				return;
			}
			long total = 0;
			try {
				for (int i = 0; i < hooks.size(); i++) {
					long t0 = System.nanoTime();
					hooks.get(i).tick().accept(server);
					long spent = System.nanoTime() - t0;
					perHook[i][0] += spent;
					total += spent;
				}
			} catch (RuntimeException e) {
				cleanup.run();
				throw e;
			}
			perTick[at[0]++] = total;
		}).thenExecute(() -> {
			joined.forEach(VerifySc2CostTests::leave);
			long[] sorted = perTick.clone();
			Arrays.sort(sorted);
			int kept = sorted.length - sorted.length / 200;
			double mean = Arrays.stream(sorted, 0, kept).average().orElse(0) / 1000.0;
			StringBuilder each = new StringBuilder();
			for (int i = 0; i < hooks.size(); i++) {
				each.append(String.format(Locale.ROOT, "%s%s %.2f", i == 0 ? "" : ", ", hooks.get(i).name(), perHook[i][0] / (double) ticks / 1000.0));
			}
			A1016_02.LOGGER.info(String.format(Locale.ROOT, "[verify] sc2 cost idle, 2 taking part: %.2f us a tick (median %.2f, p99 %.2f, max %.2f); each hook us"
					+ " a tick: %s", mean, sorted[sorted.length / 2] / 1000.0, sorted[sorted.length * 99 / 100] / 1000.0, sorted[sorted.length - 1] / 1000.0, each));
			helper.assertTrue(mean < IDLE_BUDGET_US, String.format(Locale.ROOT, "the second wave idle costs %.2f us a tick (budget %.0f): %s", mean,
					IDLE_BUDGET_US, each));
		}).thenSucceed();
	}

	/**
	 * The visits' look (the kit, every 20 ticks for each participant) over 200 known places spread over 4 000 blocks: eight
	 * players away from all of them and one inside a place, each a lookup in the places' chunk index, not a walk over
	 * them. Per participant a look, and per tick (a look every 20 ticks).
	 */
	@GameTest(environment = ENV, maxTicks = 200)
	public void theVisitsLookIsALookupPerParticipant(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Sc2Data data = new Sc2Data();
		BlockPos base = new BlockPos(BASE_X - 6_000, 64, BASE_Z - 6_000);
		Remains.Place inside = null;
		for (int i = 0; i < 200; i++) {
			int x = base.getX() + (i % 20) * 200;
			int z = base.getZ() + (i / 20) * 400;
			Remains.Place place = new Remains.Place(Remains.Kind.LINE, level.dimension(), new BoundingBox(x, 0, z, x, 250, z + 160), new BlockPos(x, 64, z + 80),
					new BlockPos(x, 64, z), Direction.NORTH, Optional.empty(), i, 0);
			data.add(Sc2Data.Known.of(place, "s19", new Start(place.anchor(), Direction.NORTH, 0, i), new CompoundTag()));
			if (i == 77) {
				inside = place;
			}
		}
		List<ServerPlayer> players = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			Vec3 at = i < 8 ? new Vec3(base.getX() + 100.5 + i * 37, 70, base.getZ() + 290.5 + i * 11) : Vec3.atBottomCenterOf(inside.anchor());
			p.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
			players.add(p);
		}
		int[] events = {0};
		Visits.Sink sink = new Visits.Sink() {
			@Override
			public void entered(ServerLevel l, Remains.Place place, ServerPlayer player) {
				events[0]++;
			}

			@Override
			public void left(ServerLevel l, Remains.Place place, ServerPlayer player) {
				events[0]++;
			}

			@Override
			public void cameInThroughTheWay(MinecraftServer s, Remains.Place place, ServerPlayer player) {
			}
		};
		Visits visits = new Visits(RemainsImpl.over(data), sink, s -> players, () -> true);
		visits.scan(server, 0, 0);
		helper.assertTrue(visits.placeOf(players.get(8).getUUID()).isPresent() && visits.placeOf(players.getFirst().getUUID()).isEmpty(),
				"setup: the one inside is not inside, or one away is");
		long[] tick = {1};
		double perScan = perCall(() -> visits.scan(server, tick[0]++ * 20, tick[0] * 20), 20_000);
		double perPlayer = perScan / players.size();
		A1016_02.LOGGER.info(String.format(Locale.ROOT, "[verify] sc2 cost visits: 200 known places, 9 taking part: %.0f ns a look, %.0f ns a participant, %.0f ns"
				+ " a server tick (a look every 20)", perScan, perPlayer, perScan / Visits.EVERY_TICKS));
		helper.assertTrue(perPlayer < 5_000, String.format(Locale.ROOT, "a visit's look costs %.0f ns a participant over 200 places: not a lookup", perPlayer));
		helper.assertTrue(events[0] == 1, "the looks fired " + events[0] + " events (one enter expected)");
		helper.succeed();
	}

	/**
	 * A ring of 14 sheep (21) on a patch of grass, its own scene manager stepped every tick for 400 ticks (it walks its
	 * animals every 10): the scene's own work a tick, the mobs' walking being vanilla's. And a held breath (23) of 40:
	 * gathering them (the entity query at a fire), holding all in one tick, the hold's tick, and letting them go.
	 */
	@GameTest(environment = ENV, maxTicks = 1200)
	public void aRingOfFourteenAndAHeldBreathOfFortyAreCheapScenes(GameTestHelper helper) {
		Patch p = new Patch(helper, 40);
		Rings rings = new Rings().with(new VerifyScCostTests.Watched(List.of()).watch, (level, box) -> true);
		HeldBreath held = new HeldBreath();
		List<Mob> sheep = new ArrayList<>();
		List<Mob> herd = new ArrayList<>();
		long[] ringNanos = {0};
		int[] ringTicks = {0};
		double[] breath = new double[4];
		p.whenReady(() -> {
			p.floor(-30, -30, 30, 30, Blocks.GRASS_BLOCK.defaultBlockState());
			for (int i = 0; i < 14; i++) {
				sheep.add(p.spawn(EntityTypes.SHEEP, -4.5 + (i % 5) * 2, -4.5 + (i / 5) * 2));
			}
			for (int i = 0; i < 40; i++) {
				herd.add(p.spawn(EntityTypes.COW, 14.5 + (i % 8) * 1.5, -14.5 + (i / 8) * 3));
			}
		}).thenIdle(5).thenExecute(() -> {
			Optional<Rings.Site> site = Rings.site(p.level, p.vec(0.5, 0, 0.5), sheep.size(), 2.2);
			helper.assertTrue(site.isPresent(), "setup: no site for the ring");
			Optional<Rings.Scene> scene = rings.start(p.level, site.get(), sheep, List.of(), 20 * 60, true);
			helper.assertTrue(scene.isPresent(), "setup: the ring did not start");
			long t0 = System.nanoTime();
			List<Mob> gathered = HeldBreath.gather(p.level, List.of(p.vec(17.5, 1.6, -8.5)), 48, 40, false);
			breath[0] = (System.nanoTime() - t0) / 1000.0;
			helper.assertTrue(gathered.size() >= 40, "setup: gathered " + gathered.size());
			t0 = System.nanoTime();
			int n = held.hold(p.level, gathered, p.vec(17.5, 2, 200), 160, List.of(), false);
			breath[1] = (System.nanoTime() - t0) / 1000.0;
			helper.assertTrue(n >= 40, "held " + n);
		}).thenExecuteFor(400, () -> {
			long t0 = System.nanoTime();
			rings.tick(p.level.getServer());
			ringNanos[0] += System.nanoTime() - t0;
			ringTicks[0]++;
			t0 = System.nanoTime();
			held.tick(p.level.getServer());
			breath[2] += System.nanoTime() - t0;
		}).thenExecute(() -> {
			long t0 = System.nanoTime();
			held.clear();
			breath[3] = (System.nanoTime() - t0) / 1000.0;
			int steered = (int) sheep.stream().filter(MobTamperImpl.INSTANCE::isSteered).count();
			double ring = ringNanos[0] / (double) ringTicks[0] / 1000.0;
			double hold = breath[2] / 400.0 / 1000.0;
			A1016_02.LOGGER.info(String.format(Locale.ROOT, "[verify] sc2 cost scenes: a ring of 14 (%d steered at the end) %.2f us a tick; a held breath of 40:"
					+ " gathered %.0f us, held in one tick %.0f us, %.2f us a tick, let go %.0f us", steered, ring, breath[0], breath[1], hold, breath[3]));
			rings.clear();
			helper.assertTrue(ring < 500 && hold < 50 && breath[0] < 20_000 && breath[1] < 20_000,
					String.format(Locale.ROOT, "a scene is not cheap: ring %.2f us a tick, hold %.2f us a tick, gather %.0f us, hold %.0f us", ring, hold, breath[0],
							breath[1]));
			p.done();
		}).thenSucceed();
	}

	/**
	 * A pack of five (25) stalking a stand-in for 400 ticks, stepped as the hunts' own tick steps it (its pending tells,
	 * then a step when one is due, every 10 ticks) but by the test, outside the live list, so nothing else steps it first:
	 * its work a tick and a step.
	 */
	@GameTest(environment = ENV, maxTicks = 1200)
	public void aPackOfFiveStalkingIsACheapScene(GameTestHelper helper) {
		Patch p = new Patch(helper, 56);
		List<Wolf> wolves = new ArrayList<>();
		Pack[] pack = new Pack[1];
		long[] nanos = {0};
		int[] steps = {0};
		p.whenReady(() -> {
			p.floor(-50, -50, 50, 50, Blocks.GRASS_BLOCK.defaultBlockState());
			for (int i = 0; i < 5; i++) {
				wolves.add((Wolf) p.spawn(EntityTypes.WOLF, -10.5 + i * 3, 42.5));
			}
		}).thenIdle(5).thenExecute(() -> {
			Vec3 feet = p.vec(0.5, 0, 0.5);
			Prey prey = Prey.standIn(p.level, feet, 0.0F, 0.0F);
			Sc25Config cfg = new Sc25Config();
			cfg.stalkSecondsMin = 600;
			cfg.stalkSecondsMax = 600;
			UUID target = UUID.nameUUIDFromBytes(("verify sc2 cost " + p.origin).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			pack[0] = new Pack(target, p.level, HuntScene.Links.gallery(), -1, prey, wolves, cfg, () -> true, prey, Pack.Phase.STALK);
			pack[0].lifetime(600);
			pack[0].begin();
		}).thenExecuteFor(400, () -> {
			long now = p.level.getServer().getTickCount();
			long t0 = System.nanoTime();
			pack[0].flush(now);
			if (!pack[0].done() && now >= pack[0].nextStep()) {
				pack[0].step(now);
				steps[0]++;
			}
			nanos[0] += System.nanoTime() - t0;
		}).thenExecute(() -> {
			pack[0].stop(true, "measured");
			double perTick = nanos[0] / 400.0 / 1000.0;
			double perStep = steps[0] == 0 ? 0 : nanos[0] / (double) steps[0] / 1000.0;
			A1016_02.LOGGER.info(String.format(Locale.ROOT, "[verify] sc2 cost hunts: a pack of 5 stalking, %d steps in 400 ticks: %.2f us a tick, %.0f us a step",
					steps[0], perTick, perStep));
			helper.assertTrue(steps[0] >= 30, "the pack stepped " + steps[0] + " times in 400 ticks (every 10 expected)");
			helper.assertTrue(perTick < 500 && perStep < 5_000, String.format(Locale.ROOT, "the pack is not cheap: %.2f us a tick, %.0f us a step", perTick,
					perStep));
			p.done();
		}).thenSucceed();
	}

	/** 27's payload (the copy of the friend, to the viewer only): its size with a full hand and armour, and an encode and a decode. */
	@GameTest(environment = ENV)
	public void theFriendsPayloadIsSmall(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<ItemStack> items = List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.TORCH, 64), new ItemStack(Items.IRON_HELMET),
				new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS));
		Sc27Payload payload = new Sc27Payload(7, UUID.randomUUID(), new Vec3(1234.5, 70, -987.5), 135.0F, 90.0F, 500, 60, 28.0F, items, (byte) 0x7F, false, false);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
		Sc27Payload.CODEC.encode(buf, payload);
		int bytes = buf.readableBytes();
		Sc27Payload back = Sc27Payload.CODEC.decode(buf);
		helper.assertTrue(back.key() == 7 && back.other().equals(payload.other()) && back.items().size() == Sc27Payload.ITEMS
				&& ItemStack.matches(back.items().getFirst(), items.getFirst()), "the payload did not come back: " + back);
		double encode = perCall(() -> {
			RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(Unpooled.buffer(256), server.registryAccess());
			Sc27Payload.CODEC.encode(b, payload);
			b.release();
		}, 20_000);
		A1016_02.LOGGER.info(String.format(Locale.ROOT, "[verify] sc2 cost 27's payload: %d bytes with a full hand and armour, %.1f us an encode", bytes,
				encode / 1000.0));
		helper.assertTrue(bytes < 1_024 && encode < 100_000, "27's payload: " + bytes + " bytes, " + encode + " ns an encode");
		helper.succeed();
	}

	// --- helpers ---

	/** Mean ns per call after a warm-up of a tenth as many. */
	private static double perCall(Runnable r, int n) {
		for (int i = 0; i < n / 10; i++) {
			r.run();
		}
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			r.run();
		}
		return (System.nanoTime() - start) / (double) n;
	}

	/** A player really in the server's list (Fabric's join runs), creative, at the world's spawn (as {@code MultiplayerCoreTests}' own). */
	private static ServerPlayer join(GameTestHelper helper, String name) {
		MinecraftServer server = helper.getLevel().getServer();
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
				new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.CREATIVE;
			}

			@Override
			public boolean isClientAuthoritative() {
				return false;
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	private static void leave(ServerPlayer player) {
		if (!player.hasDisconnected()) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("a1016 test done"));
		}
	}

	/** A patch of the overworld far out, this class's own (a slot 512 apart), a floor at y 200 in the air, its chunks held while it runs. */
	static final class Patch {
		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		private final int reach;
		private final List<Mob> made = new ArrayList<>();

		Patch(GameTestHelper helper, int reach) {
			this.helper = helper;
			this.level = helper.getLevel().getServer().overworld();
			this.origin = new BlockPos(BASE_X + SLOTS.getAndIncrement() * 512, 200, BASE_Z);
			this.reach = reach;
			force(true);
		}

		private void force(boolean on) {
			for (int cx = (origin.getX() - reach) >> 4; cx <= (origin.getX() + reach) >> 4; cx++) {
				for (int cz = (origin.getZ() - reach) >> 4; cz <= (origin.getZ() + reach) >> 4; cz++) {
					level.setChunkForced(cx, cz, on);
				}
			}
		}

		boolean ready() {
			for (int cx = (origin.getX() - reach) >> 4; cx <= (origin.getX() + reach) >> 4; cx++) {
				for (int cz = (origin.getZ() - reach) >> 4; cz <= (origin.getZ() + reach) >> 4; cz++) {
					if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
						return false;
					}
				}
			}
			return true;
		}

		net.minecraft.gametest.framework.GameTestSequence whenReady(Runnable then) {
			return FarPatches.whenReady(helper, this::ready, "the cost patch", then);
		}

		Vec3 vec(double x, double y, double z) {
			return Vec3.atLowerCornerOf(origin).add(x, y, z);
		}

		/** A floor of this block one under the origin, air 4 over it. */
		void floor(int x1, int z1, int x2, int z2, BlockState top) {
			for (int x = x1; x <= x2; x++) {
				for (int z = z1; z <= z2; z++) {
					level.setBlock(origin.offset(x, -1, z), top, Block.UPDATE_CLIENTS);
					for (int y = 0; y < 4; y++) {
						level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		Mob spawn(EntityType<? extends Mob> type, double x, double z) {
			Mob mob = type.create(level, EntitySpawnReason.COMMAND);
			if (mob == null) {
				throw new IllegalStateException("no " + type);
			}
			mob.snapTo(origin.getX() + x, origin.getY(), origin.getZ() + z, 0.0F, 0.0F);
			mob.setPersistenceRequired();
			level.addFreshEntity(mob);
			made.add(mob);
			return mob;
		}

		void done() {
			for (Mob mob : made) {
				MobTamperImpl.INSTANCE.release(mob);
				mob.discard();
			}
			force(false);
		}
	}
}
