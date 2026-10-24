package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;


/**
 * FIX-SC2-REST, core (docs/SELF_CREATED_2.md, "Fixed in review (FIX-SC2-REST)"): the participation event always on the
 * server thread, a leave the network thread saw included (R12-4); the layer's WARN and ERROR lines for something that
 * threw carry no frames and name no class of the mod (R13-5); a mob taken from where it stood is ledgered apart and goes
 * back there through the ledger's undo (R13-7). Mob tests run far out (x about -14.3 million, z about 24.1 million).
 */
public class SelfCreated2ReviewCoreTests {
	private static final BlockPos FAR = new BlockPos(-14_300_000, 0, 24_100_000);

	/** A player who never joins the server. */
	static final class Mock extends ServerPlayer {
		Mock(ServerLevel level, String name) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	/** A test's own online list and state; each participation event with whether it came on the server thread. */
	static final class Threads implements PlayerWatch.Source {
		final List<ServerPlayer> online = Collections.synchronizedList(new ArrayList<>());
		final HerobrineState state = new HerobrineState();
		final List<String> events = Collections.synchronizedList(new ArrayList<>());
		final PlayerWatch watch = new PlayerWatch(this);

		@Override
		public List<ServerPlayer> online(MinecraftServer server) {
			return List.copyOf(online);
		}

		@Override
		public HerobrineState state(MinecraftServer server) {
			return state;
		}

		@Override
		public void participation(MinecraftServer server, UUID player, boolean participating) {
			boolean listed = online.stream().anyMatch(p -> p.getUUID().equals(player));
			events.add((participating ? "+" : "-") + (server.isSameThread() ? "" : " off the server thread") + (participating || listed ? "" : " (not listed)"));
		}

		@Override
		public Director director() {
			return new Director.Stub();
		}
	}

	private static void offThread(Runnable run) throws InterruptedException {
		Thread network = new Thread(run, "a1016-test-network");
		network.start();
		network.join();
	}

	/**
	 * R12-4: a client that quits reaches Fabric's DISCONNECT on the network thread. Nothing of the watch runs there: the
	 * leave waits for the server thread's next turn, and its PARTICIPATION false fires there, once, the player still listed.
	 */
	@GameTest(maxTicks = 40)
	public void aLeaveSeenOnTheNetworkThreadFiresOnceOnTheServerThread(GameTestHelper helper) throws InterruptedException {
		MinecraftServer server = helper.getLevel().getServer();
		Threads roster = new Threads();
		Mock a = new Mock(helper.getLevel(), "a1016_r4a");
		roster.watch.onJoin(a);
		roster.online.add(a);
		roster.watch.tickParticipation(server);
		helper.assertTrue(roster.events.equals(List.of("+")), "the start: " + roster.events);
		offThread(() -> roster.watch.onDisconnect(server, a));
		helper.assertTrue(roster.events.equals(List.of("+")), "the network thread ran the leave: " + roster.events);
		helper.startSequence().thenWaitUntil(() -> helper.assertTrue(roster.events.size() == 2, "the leave never came")).thenExecuteAfter(5, () -> {
			helper.assertTrue(roster.events.equals(List.of("+", "-")), "not once on the server thread with the player listed: " + roster.events);
			roster.online.remove(a);
			roster.watch.tickParticipation(server);
			helper.assertTrue(roster.events.size() == 2, "fired again at the end of a tick: " + roster.events);
		}).thenSucceed();
	}

	/**
	 * R12-4: vanilla takes the player off the list on the server thread ({@code PlayerList.remove}, core's hook); a leave
	 * the network thread saw and handed over is handled there if that comes first, the player still listed, and the
	 * handed-over turn then does nothing again.
	 */
	@GameTest(maxTicks = 40)
	public void takingThePlayerOffTheListHandlesALeaveTheNetworkThreadSawFirst(GameTestHelper helper) throws InterruptedException {
		MinecraftServer server = helper.getLevel().getServer();
		Threads roster = new Threads();
		Mock a = new Mock(helper.getLevel(), "a1016_r4b");
		roster.watch.onJoin(a);
		roster.online.add(a);
		roster.watch.tickParticipation(server);
		offThread(() -> roster.watch.onDisconnect(server, a));
		roster.watch.onRemoving(a);
		helper.assertTrue(roster.events.equals(List.of("+", "-")), "the removal did not end the session there: " + roster.events);
		roster.online.remove(a);
		helper.startSequence().thenExecuteAfter(3, () -> {
			roster.watch.tickParticipation(server);
			helper.assertTrue(roster.events.equals(List.of("+", "-")), "a second stop: " + roster.events);
			Mock b = new Mock(helper.getLevel(), "a1016_r4c");
			roster.watch.onRemoving(b);
			helper.assertTrue(roster.events.size() == 2, "a removal of someone never seen fired");
		}).thenSucceed();
	}

	/**
	 * R12-4: the live event's own source hands a call from any other thread to the server thread, and a DISCONNECT
	 * listener's work ({@code PlayerWatch.afterDisconnect}) runs there too.
	 */
	@GameTest(maxTicks = 40)
	public void theLiveEventAndADisconnectListenersWorkRunOnTheServerThread(GameTestHelper helper) throws InterruptedException {
		MinecraftServer server = helper.getLevel().getServer();
		UUID nobody = UUID.randomUUID();
		List<String> heard = Collections.synchronizedList(new ArrayList<>());
		HerobrineEvents.PARTICIPATION.register((s, player, participating) -> {
			if (player.equals(nobody)) {
				heard.add(s.isSameThread() ? "server" : "other");
			}
		});
		Mock gone = new Mock(helper.getLevel(), "a1016_r4d");
		AtomicBoolean onServer = new AtomicBoolean();
		AtomicInteger ran = new AtomicInteger();
		offThread(() -> {
			PlayerWatch.SERVER.participation(server, nobody, false);
			PlayerWatch.afterDisconnect(server, gone, () -> {
				ran.incrementAndGet();
				onServer.set(server.isSameThread());
			});
		});
		helper.assertTrue(heard.isEmpty() && ran.get() == 0, "ran on the other thread: " + heard + ", " + ran.get());
		helper.startSequence().thenWaitUntil(() -> helper.assertTrue(heard.size() == 1 && ran.get() == 1, "not run yet")).thenExecute(() -> {
			helper.assertTrue(heard.equals(List.of("server")) && onServer.get(), "not on the server thread: " + heard + ", " + onServer.get());
			AtomicInteger here = new AtomicInteger();
			PlayerWatch.afterDisconnect(server, gone, here::incrementAndGet);
			helper.assertTrue(here.get() == 1, "on the server thread it waited");
		}).thenSucceed();
	}

	/**
	 * R12-11: no edit puts a block with a body into a cell a living mob stands in (a pet told to stay where a way is
	 * closed would smother unseen): a move into it and a block left there are refused, forced or not; a torch passes, and an
	 * empty cell takes the block.
	 */
	@GameTest(maxTicks = 200)
	public void noEditPutsABlockIntoACellAMobStandsIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		BlockPos column = FAR.offset(160, 0, 0);
		level.setChunkForced(column.getX() >> 4, column.getZ() >> 4, true);
		level.getChunk(column.getX() >> 4, column.getZ() >> 4);
		helper.startSequence().thenExecute(() -> {
			long until = net.minecraft.util.Util.getMillis() + 30_000L;
			level.getServer().managedBlock(() -> level.isPositionEntityTicking(column) || net.minecraft.util.Util.getMillis() > until);
		}).thenWaitUntil(() -> helper.assertTrue(level.areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(column.getX() >> 4,
				column.getZ() >> 4)), "loading")).thenExecute(() -> {
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			BlockPos floor = new BlockPos(column.getX(), y, column.getZ());
			BlockPos from = floor.offset(0, 0, 3);
			BlockPos stay = floor.offset(2, 0, 0);
			BlockPos empty = floor.offset(-2, 0, 0);
			TraceService traces = Services.traces().forced();
			Cow pet = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			try {
				level.setBlock(from, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 3);
				pet.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(stay), 0.0F, 0.0F);
				helper.assertTrue(level.addFreshEntity(pet), "setup: the cow");
				helper.assertFalse(traces.move(level, from, stay, "test:r12-11"), "a block moved into the cow");
				helper.assertTrue(level.getBlockState(stay).isAir() && level.getBlockState(from).is(net.minecraft.world.level.block.Blocks.COBBLESTONE),
						"the refused move changed something");
				helper.assertFalse(traces.leave(level, stay, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), "test:r12-11"),
						"a block left in the cow");
				helper.assertTrue(traces.leave(level, stay, net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(), "test:r12-11"),
						"a torch refused");
				helper.assertTrue(traces.move(level, from, empty, "test:r12-11"), "a move into an empty cell refused");
			} finally {
				pet.discard();
				for (BlockPos pos : List.of(from, stay, empty)) {
					level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				}
				TraceLedger ledger = TraceLedger.get(level.getServer());
				for (TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
					if (entry.cause().equals("test:r12-11")) {
						ledger.remove(entry);
					}
				}
				level.setChunkForced(column.getX() >> 4, column.getZ() >> 4, false);
			}
		}).thenSucceed();
	}

	/** One of the mod's own exception classes: its name could name something. */
	static final class SomethingNamedException extends RuntimeException {
		SomethingNamedException(String message) {
			super(message);
		}
	}

	/**
	 * R13-5: a WARN or ERROR of the layer for something that threw is the caller's own neutral line and the exception's
	 * simple name (the JDK's or the game's) or "error" (the mod's own), never a frame or the exception's own message (a
	 * helpful NullPointerException names classes and fields); the trace goes to DEBUG under the same message.
	 */
	@GameTest
	public void aLayerErrorLineHasNoFramesAndNamesNoClassOfTheMod(GameTestHelper helper) {
		helper.assertTrue(ScLog.line("[a1016] s2: a {} failed in chunk {}", "s16", "[1, 2]", new IllegalStateException("x"))
				.equals("[a1016] s2: a s16 failed in chunk [1, 2] (IllegalStateException)"), "the line: " + ScLog.line("[a1016] s2: a {} failed", "s16",
				new IllegalStateException("x")));
		helper.assertTrue(ScLog.kind(new SomethingNamedException("y")).equals(ScLog.NEUTRAL), "the mod's own exception named");
		helper.assertTrue(ScLog.line("[a1016] s2: plain").equals("[a1016] s2: plain"), "a line without a throwable");
		String npe = "Cannot invoke \"com.forzacode.a1016_02.world.sc2.Something.fill()\" because \"shape\" is null";
		try (LogCapture log = LogCapture.open()) {
			ScLog.error("[a1016] s2: a listener failed (v)", new NullPointerException(npe));
			ScLog.warn("[a1016] sc: could not write '{}'", "x.dat", new SomethingNamedException("named"));
			List<LogCapture.Line> shown = log.shown();
			helper.assertTrue(shown.size() == 2, "lines at INFO or above: " + shown);
			helper.assertTrue(shown.get(0).level() == org.apache.logging.log4j.Level.ERROR && shown.get(0).message().equals("[a1016] s2: a listener failed (v) (NullPointerException)"),
					"the error line: " + shown.get(0));
			helper.assertTrue(shown.get(1).level() == org.apache.logging.log4j.Level.WARN && shown.get(1).message().equals("[a1016] sc: could not write 'x.dat' (error)"),
					"the warn line: " + shown.get(1));
			for (LogCapture.Line line : shown) {
				helper.assertTrue(line.thrown() == null && !line.message().contains("com.forzacode") && !line.message().contains("named"),
						"a frame or a name at " + line.level() + ": " + line);
			}
			List<LogCapture.Line> traces = log.lines().stream().filter(l -> l.level() == org.apache.logging.log4j.Level.DEBUG && l.thrown() != null).toList();
			helper.assertTrue(traces.size() == 2 && traces.get(0).thrown() instanceof NullPointerException, "the traces at DEBUG: " + traces);
		}
		helper.succeed();
	}

	/**
	 * R13-7: a mob taken from where it stood is a ledger entry of its own kind, stored apart (v1.3 and the older layer jars
	 * read {@code entries} only, and skip the key), in its place in the order when it loads.
	 */
	@GameTest
	public void aTakenMobIsLedgeredApartAndLoadsInItsPlace(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		TraceLedger ledger = new TraceLedger();
		GlobalPos at = GlobalPos.of(Level.OVERWORLD, FAR);
		TraceLedger.Entry first = ledger.add(new TraceLedger.Entry(TraceLedger.Kind.REMOVE, "test:a", 1, at, Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), -1, -1));
		TraceLedger.Entry taken = ledger.add(new TraceLedger.Entry(TraceLedger.Kind.MOVE_MOB, "test:b", 2, at, Optional.of(FAR.east(40)), Optional.empty(),
				Optional.empty(), Optional.empty(), -1, -1, Optional.of(UUID.randomUUID())));
		TraceLedger.Entry last = ledger.add(new TraceLedger.Entry(TraceLedger.Kind.REMOVE, "test:c", 3, at, Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), -1, -1));
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag saved = (CompoundTag) TraceLedger.CODEC.encodeStart(ops, ledger).getOrThrow();
		ListTag entries = saved.getListOrEmpty("entries");
		helper.assertTrue(entries.size() == 2, "entries holds " + entries.size() + " (an older reader would meet the new kind)");
		helper.assertTrue(saved.getListOrEmpty(TraceLedger.MOBS_KEY).size() == 1, "not under " + TraceLedger.MOBS_KEY + ": " + saved.keySet());
		TraceLedger loaded = TraceLedger.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(loaded.entries().equals(List.of(first, taken, last)), "not in its place: " + loaded.entries());
		saved.remove(TraceLedger.MOBS_KEY);
		helper.assertTrue(TraceLedger.CODEC.parse(ops, saved).getOrThrow().entries().size() == 2, "a file without the key");
		helper.succeed();
	}

	/**
	 * R13-7: its undo puts the mob back where it stood, out of view (DONE, the entry closed); one named since is someone's
	 * now (BLOCKED, kept); one not in the level waits while the entities where it was put are not loaded.
	 */
	@GameTest(maxTicks = 200)
	public void aTakenMobGoesBackWhereItStoodUnlessItIsSomeonesNow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		BlockPos column = FAR.offset(64, 0, 0);
		level.setChunkForced(column.getX() >> 4, column.getZ() >> 4, true);
		level.getChunk(column.getX() >> 4, column.getZ() >> 4);
		helper.startSequence().thenExecute(() -> {
			// In real time (at most 30 s): the test server ticks faster than its chunks load (as the hunts' patches wait).
			long until = net.minecraft.util.Util.getMillis() + 30_000L;
			level.getServer().managedBlock(() -> level.isPositionEntityTicking(column) || net.minecraft.util.Util.getMillis() > until);
		}).thenWaitUntil(() -> helper.assertTrue(level.areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(column.getX() >> 4, column.getZ() >> 4)),
				"loading")).thenExecute(() -> {
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			BlockPos home = new BlockPos(column.getX(), y, column.getZ());
			BlockPos put = home.offset(6, 0, 3);
			TraceService traces = Services.traces().forced();
			Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			Cow named = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			try {
				cow.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(put), 0.0F, 0.0F);
				named.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(put.east()), 0.0F, 0.0F);
				helper.assertTrue(level.addFreshEntity(cow) && level.addFreshEntity(named), "setup: the cows");
				TraceLedger.Entry entry = TraceLedger.recordMobMove(level, cow, home, put, "test:r13-7");
				TraceLedger.Entry other = TraceLedger.recordMobMove(level, named, home.north(2), put.east(), "test:r13-7");
				named.setCustomName(Component.literal("Daisy"));
				helper.assertTrue(traces.undo(level, entry) == TraceService.UndoResult.DONE, "not given back");
				helper.assertTrue(cow.blockPosition().equals(home) && !TraceLedger.get(level.getServer()).entries().contains(entry), "not back where it stood: "
						+ cow.blockPosition() + " (home " + home + ")");
				helper.assertTrue(traces.undo(level, other) == TraceService.UndoResult.BLOCKED && named.blockPosition().equals(put.east()), "a named one moved");
				TraceLedger.get(level.getServer()).remove(other);
				TraceLedger.Entry lost = TraceLedger.get(level.getServer()).add(new TraceLedger.Entry(TraceLedger.Kind.MOVE_MOB, "test:r13-7", 0,
						GlobalPos.of(Level.OVERWORLD, home), Optional.of(FAR.offset(4_000, 0, 0)), Optional.empty(), Optional.empty(), Optional.empty(), -1,
						-1, Optional.of(UUID.randomUUID())));
				helper.assertTrue(traces.undo(level, lost) == TraceService.UndoResult.UNLOADED, "a mob not found where it was put, not loaded, did not wait");
				TraceLedger.get(level.getServer()).remove(lost);
			} finally {
				cow.discard();
				named.discard();
				level.setChunkForced(column.getX() >> 4, column.getZ() >> 4, false);
			}
		}).thenSucceed();
	}
}
