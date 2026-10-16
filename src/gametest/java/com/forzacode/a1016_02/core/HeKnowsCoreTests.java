package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v1.3 core (D-076, D-077, D-079): each player's recent actions (mining grouped into bursts, doors, containers; capped,
 * newest first, session only), blind moments from the client's payload ({@code blindTicks}: participants only, reset on
 * leaving, death and a dimension change), the director's recheck schedule, and the stub's recheck doing nothing. Every
 * test brings its own {@link MultiplayerCoreTests.Roster}, so nothing here reads or changes the live server's players.
 */
public class HeKnowsCoreTests extends TraceGameTests {
	private static TestPlayer joined(GameTestHelper helper, Roster roster, String name, GameType mode) {
		TestPlayer player = new TestPlayer(helper.getLevel(), UUID.randomUUID(), name, mode);
		player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)), 0.0F, 0.0F);
		roster.join(player);
		roster.tick(helper.getLevel().getServer(), 1);
		return player;
	}

	private static List<Long> ticks(PlayerAction.Mining mining) {
		return mining.breaks().stream().map(PlayerAction.Break::tick).toList();
	}

	// --- recent actions (D-076) ---

	@GameTest
	public void miningIsGroupedIntoBurstsNewestFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roster r = new Roster();
		PlayerWatch w = r.watch;
		TestPlayer a = joined(helper, r, "A", GameType.SURVIVAL);
		ResourceKey<Level> here = level.dimension();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState stone = Blocks.STONE.defaultBlockState();
		long t0 = level.getServer().getTickCount() - 5000;

		// One run: each break less than 3 s (60 ticks) after the last, 59 included.
		w.addBreak(a, here, at, stone, t0);
		w.addBreak(a, here, at.east(), stone, t0 + 20);
		w.addBreak(a, here, at.east(2), stone, t0 + 79);
		// 60 ticks later is a new run: a lone break, mining but no burst.
		w.addBreak(a, here, at.east(3), Blocks.DIRT.defaultBlockState(), t0 + 139);
		// A run of eight, 10 ticks apart: six, then the next two.
		for (int i = 0; i < 8; i++) {
			w.addBreak(a, here, at.above().east(i), Blocks.DEEPSLATE.defaultBlockState(), t0 + 600 + 10L * i);
		}

		List<PlayerAction> recent = w.recentActions(a);
		helper.assertTrue(recent.size() == 4 && recent.stream().allMatch(PlayerAction.Mining.class::isInstance), "entries " + recent);
		PlayerAction.Mining two = (PlayerAction.Mining) recent.get(0);
		PlayerAction.Mining six = (PlayerAction.Mining) recent.get(1);
		PlayerAction.Mining lone = (PlayerAction.Mining) recent.get(2);
		PlayerAction.Mining three = (PlayerAction.Mining) recent.get(3);
		helper.assertTrue(ticks(two).equals(List.of(t0 + 660, t0 + 670)) && two.isBurst() && two.tick() == t0 + 670
				&& two.pos().equals(at.above().east(7)), "the newest run's last two " + two);
		helper.assertTrue(six.breaks().size() == PlayerAction.Mining.MAX_BREAKS && six.isBurst() && six.firstTick() == t0 + 600
				&& six.gaps().equals(List.of(10L, 10L, 10L, 10L, 10L)), "the run's first six " + six);
		helper.assertTrue(lone.breaks().size() == 1 && !lone.isBurst() && lone.gaps().isEmpty()
				&& lone.first().state().is(Blocks.DIRT), "a lone break " + lone);
		helper.assertTrue(ticks(three).equals(List.of(t0, t0 + 20, t0 + 79)) && three.gaps().equals(List.of(20L, 59L))
				&& three.first().pos().equals(at) && three.last().pos().equals(at.east(2)) && three.first().state().is(Blocks.STONE)
				&& three.dimension().equals(here), "the first run " + three);
		helper.assertTrue(w.recentBursts(a).equals(List.of(two, six, three)), "bursts " + w.recentBursts(a));

		// Anything else in between, or another level, ends a run even within 3 s.
		w.addAction(a, new PlayerAction.DoorUse(here, at, Blocks.OAK_DOOR.defaultBlockState(), t0 + 675));
		w.addBreak(a, here, at.below(), stone, t0 + 680);
		w.addBreak(a, Level.NETHER, at.below(), Blocks.NETHERRACK.defaultBlockState(), t0 + 685);
		recent = w.recentActions(a);
		helper.assertTrue(recent.size() == 7 && recent.get(0) instanceof PlayerAction.Mining nether && nether.dimension().equals(Level.NETHER)
				&& nether.breaks().size() == 1 && recent.get(1) instanceof PlayerAction.Mining after && after.breaks().size() == 1
				&& recent.get(2) instanceof PlayerAction.DoorUse, "a door or another level ended the run: " + recent);

		// The real hook: a player-placed block broken counts as mining too, at the server's tick.
		BlockPos placed = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.setBlock(3, 1, 3, Blocks.OAK_PLANKS);
		w.onPlaced(a, level, placed, Blocks.OAK_PLANKS.defaultBlockState());
		w.onBroken(level, placed);
		w.onMined(a, level, placed, Blocks.OAK_PLANKS.defaultBlockState());
		PlayerAction newest = w.recentActions(a).getFirst();
		helper.assertTrue(newest instanceof PlayerAction.Mining m && m.breaks().size() == 1 && m.pos().equals(placed)
				&& m.first().state().is(Blocks.OAK_PLANKS) && m.tick() == level.getServer().getTickCount(), "a placed block broken " + newest);
		helper.succeed();
	}

	@GameTest
	public void recentActionsAreCappedAndLastOneSession(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ResourceKey<Level> here = helper.getLevel().dimension();
		Roster r = new Roster();
		PlayerWatch w = r.watch;
		TestPlayer a = joined(helper, r, "A", GameType.SURVIVAL);
		TestPlayer b = joined(helper, r, "B", GameType.SURVIVAL);
		TestPlayer spectator = joined(helper, r, "S", GameType.SPECTATOR);
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState();
		long t0 = server.getTickCount() - 5000;

		// Capped at 64, newest first.
		for (int i = 0; i < 70; i++) {
			w.addAction(a, new PlayerAction.DoorUse(here, at, door.setValue(DoorBlock.OPEN, i % 2 == 0), t0 + i));
		}
		List<PlayerAction> recent = w.recentActions(a);
		helper.assertTrue(recent.size() == PlayerWatch.RECENT_ACTIONS && recent.getFirst().tick() == t0 + 69 && recent.getLast().tick() == t0 + 6,
				"the ring: " + recent.size() + " from " + recent.getFirst().tick() + " to " + recent.getLast().tick());
		for (int i = 1; i < recent.size(); i++) {
			helper.assertTrue(recent.get(i - 1).tick() > recent.get(i).tick(), "not newest first at " + i);
		}
		helper.assertTrue(((PlayerAction.DoorUse) recent.getFirst()).opened() == false && ((PlayerAction.DoorUse) recent.get(1)).opened(), "opened()");
		// Breaks: 70 more lone breaks keep it at 64 too, and the doors are gone.
		for (int i = 0; i < 70; i++) {
			w.addBreak(a, here, at, Blocks.STONE.defaultBlockState(), t0 + 100 + 100L * i);
		}
		helper.assertTrue(w.recentActions(a).size() == 64 && w.recentActions(a).stream().allMatch(PlayerAction.Mining.class::isInstance), "breaks fill the ring");

		// Per player; a spectator and a player who is not online keep nothing.
		w.addBreak(b, here, at, Blocks.STONE.defaultBlockState(), t0);
		w.addBreak(spectator, here, at, Blocks.STONE.defaultBlockState(), t0);
		w.addAction(spectator, new PlayerAction.ContainerOpen(here, at, Blocks.CHEST.defaultBlockState(), t0));
		ServerPlayer stranger = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		w.addBreak(stranger, here, at, Blocks.STONE.defaultBlockState(), t0);
		helper.assertTrue(w.recentActions(b).size() == 1 && w.recentActions(spectator).isEmpty() && w.recentActions(stranger).isEmpty(),
				"per player: B " + w.recentActions(b) + ", S " + w.recentActions(spectator));

		// A death and a respawn keep them (the same session).
		a.setHealth(0.0F);
		r.tick(server, 1);
		TestPlayer respawned = r.respawn(a, GameType.SURVIVAL);
		r.tick(server, 1);
		helper.assertTrue(w.recentActions(respawned).size() == 64, "a death lost them");

		// Turning spectator ends the session (PARTICIPATION false): forgotten, and coming back starts empty.
		respawned.mode = GameType.SPECTATOR;
		r.tick(server, 1);
		helper.assertTrue(w.recentActions(respawned).isEmpty() && r.events.contains("A-"), "spectator mode kept them: " + r.events);
		respawned.mode = GameType.SURVIVAL;
		r.tick(server, 1);
		helper.assertTrue(w.recentActions(respawned).isEmpty(), "back from spectator");

		// Leaving forgets them; B's go on until B leaves.
		w.addBreak(respawned, here, at, Blocks.STONE.defaultBlockState(), t0);
		r.leave(respawned);
		helper.assertTrue(w.recentActions(respawned).isEmpty() && w.recentActions(b).size() == 1, "leaving: A " + w.recentActions(respawned));
		r.join(respawned);
		r.tick(server, 1);
		helper.assertTrue(w.recentActions(respawned).isEmpty(), "a rejoin starts empty");
		// A server stop forgets everyone's.
		w.clear();
		helper.assertTrue(w.recentActions(b).isEmpty(), "server stop");
		helper.succeed();
	}

	@GameTest
	public void doorsAndContainersAreRecordedWhenUsed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roster r = new Roster();
		PlayerWatch w = r.watch;
		TestPlayer a = joined(helper, r, "A", GameType.SURVIVAL);
		BlockPos lower = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos upper = lower.above();
		level.setBlock(lower, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
		level.setBlock(upper, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

		// Opened by a click on its upper half (the game's own use): recorded at the lower half, open.
		use(a, level, upper);
		BlockState closedUpper = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
		helper.assertTrue(level.getBlockState(lower).getValue(DoorBlock.OPEN), "the door did not open");
		w.onUsedBlock(a, level, upper, closedUpper, a.containerMenu);
		PlayerAction first = w.recentActions(a).getFirst();
		helper.assertTrue(first instanceof PlayerAction.DoorUse use && use.pos().equals(lower) && use.opened()
				&& use.state().is(Blocks.OAK_DOOR) && use.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
				&& use.dimension().equals(level.dimension()) && use.tick() == level.getServer().getTickCount(), "the opening " + first);
		// Closed again: a second record, closed.
		BlockState openLower = level.getBlockState(lower);
		use(a, level, lower);
		w.onUsedBlock(a, level, lower, openLower, a.containerMenu);
		helper.assertTrue(w.recentActions(a).size() == 2 && w.recentActions(a).getFirst() instanceof PlayerAction.DoorUse closing
				&& !closing.opened() && closing.pos().equals(lower), "the closing " + w.recentActions(a));

		// A trapdoor and a fence gate count; an iron door clicked by hand changes nothing and records nothing.
		BlockPos trapdoor = helper.absolutePos(new BlockPos(4, 1, 2));
		level.setBlock(trapdoor, Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), 3);
		BlockState trapBefore = level.getBlockState(trapdoor);
		use(a, level, trapdoor);
		w.onUsedBlock(a, level, trapdoor, trapBefore, a.containerMenu);
		BlockPos gate = helper.absolutePos(new BlockPos(5, 1, 4));
		level.setBlock(gate, Blocks.BIRCH_FENCE_GATE.defaultBlockState(), 3);
		BlockState gateBefore = level.getBlockState(gate);
		use(a, level, gate);
		w.onUsedBlock(a, level, gate, gateBefore, a.containerMenu);
		BlockPos iron = helper.absolutePos(new BlockPos(6, 1, 2));
		level.setBlock(iron, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
		level.setBlock(iron.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
		BlockState ironBefore = level.getBlockState(iron);
		use(a, level, iron);
		w.onUsedBlock(a, level, iron, ironBefore, a.containerMenu);
		List<PlayerAction> recent = w.recentActions(a);
		helper.assertTrue(recent.size() == 4 && recent.get(0) instanceof PlayerAction.DoorUse g && g.pos().equals(gate) && g.opened()
				&& g.state().is(Blocks.BIRCH_FENCE_GATE) && recent.get(1) instanceof PlayerAction.DoorUse t && t.pos().equals(trapdoor)
				&& t.opened() && t.state().getValue(BlockStateProperties.OPEN), "trapdoor, gate, iron door: " + recent);

		// A container whose menu opened: recorded with its state. The same click without a menu, or a crafting table's menu
		// (no container), records nothing.
		BlockPos chest = helper.absolutePos(new BlockPos(1, 1, 5));
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
		BlockState chestState = level.getBlockState(chest);
		w.onUsedBlock(a, level, chest, chestState, a.containerMenu); // nothing opened
		helper.assertTrue(w.recentActions(a).size() == 4, "a chest whose menu never opened");
		a.containerMenu = ChestMenu.threeRows(1, a.getInventory());
		w.onUsedBlock(a, level, chest, chestState, a.inventoryMenu);
		helper.assertTrue(w.recentActions(a).getFirst() instanceof PlayerAction.ContainerOpen open && open.pos().equals(chest)
				&& open.state().is(Blocks.CHEST) && open.tick() == level.getServer().getTickCount(), "the chest " + w.recentActions(a).getFirst());
		BlockPos table = helper.absolutePos(new BlockPos(3, 1, 5));
		level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
		a.containerMenu = new CraftingMenu(2, a.getInventory(), ContainerLevelAccess.create(level, table));
		w.onUsedBlock(a, level, table, level.getBlockState(table), a.inventoryMenu);
		BlockPos ender = helper.absolutePos(new BlockPos(5, 1, 6));
		level.setBlock(ender, Blocks.ENDER_CHEST.defaultBlockState(), 3);
		a.containerMenu = ChestMenu.threeRows(3, a.getInventory());
		w.onUsedBlock(a, level, ender, level.getBlockState(ender), a.inventoryMenu);
		a.containerMenu = a.inventoryMenu;
		recent = w.recentActions(a);
		helper.assertTrue(recent.size() == 6 && recent.get(0) instanceof PlayerAction.ContainerOpen e && e.pos().equals(ender)
				&& recent.get(1) instanceof PlayerAction.ContainerOpen c && c.pos().equals(chest), "crafting table, ender chest: " + recent);

		// A spectator's clicks record nothing.
		TestPlayer spectator = joined(helper, r, "S", GameType.SPECTATOR);
		BlockState before = level.getBlockState(lower);
		use(a, level, lower);
		w.onUsedBlock(spectator, level, lower, before, spectator.containerMenu);
		helper.assertTrue(w.recentActions(spectator).isEmpty(), "a spectator's door");
		helper.succeed();
	}

	/** The game's own block use with an empty hand, as a click does (the live watch ignores a player who never joined). */
	private static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
	}

	// --- blind moments (D-077) ---

	@GameTest
	public void blindTicksComeFromParticipantsAndReset(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Roster r = new Roster();
		PlayerWatch w = r.watch;
		TestPlayer a = joined(helper, r, "A", GameType.SURVIVAL);
		TestPlayer b = joined(helper, r, "B", GameType.SURVIVAL);
		TestPlayer spectator = joined(helper, r, "S", GameType.SPECTATOR);

		// Open: at least 1 at once, then a tick at a time; a second "open" keeps it going; close: 0.
		helper.assertTrue(w.blindTicks(a) == 0, "blind before anything");
		w.onBlindState(a, true);
		helper.assertTrue(w.blindTicks(a) == 1 && w.blindTicks(b) == 0, "open: " + w.blindTicks(a) + ", B " + w.blindTicks(b));
		tickBlind(w, server, 5);
		w.onBlindState(a, true);
		helper.assertTrue(w.blindTicks(a) == 5, "a second open restarted it: " + w.blindTicks(a));
		w.onBlindState(a, false);
		helper.assertTrue(w.blindTicks(a) == 0, "close");
		w.onBlindState(a, false);
		helper.assertTrue(w.blindTicks(a) == 0, "a second close");

		// Ignored from a spectator, a player who is not online and a dead player.
		w.onBlindState(spectator, true);
		ServerPlayer stranger = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		w.onBlindState(stranger, true);
		tickBlind(w, server, 3);
		helper.assertTrue(w.blindTicks(spectator) == 0 && w.blindTicks(stranger) == 0, "a non-participant is blind");

		// Disconnect: reset, and a rejoin starts not blind.
		w.onBlindState(b, true);
		tickBlind(w, server, 4);
		r.leave(b);
		helper.assertTrue(w.blindTicks(b) == 0, "blind after leaving");
		r.join(b);
		r.tick(server, 1);
		helper.assertTrue(w.blindTicks(b) == 0, "blind after a rejoin");

		// Death: 0 at once and forgotten; the respawned player is not blind until the client says so again.
		w.onBlindState(a, true);
		tickBlind(w, server, 2);
		a.setHealth(0.0F);
		helper.assertTrue(w.blindTicks(a) == 0, "blind while dead");
		w.onBlindState(a, true);
		tickBlind(w, server, 1);
		TestPlayer respawned = r.respawn(a, GameType.SURVIVAL);
		helper.assertTrue(w.blindTicks(respawned) == 0, "blind after the respawn");
		w.onBlindState(respawned, true);
		tickBlind(w, server, 2);
		helper.assertTrue(w.blindTicks(respawned) == 2, "blind again after the respawn: " + w.blindTicks(respawned));

		// Dimension change: reset, and coming back does not bring it back.
		ServerLevel nether = server.getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "no nether");
		respawned.setServerLevel(nether);
		helper.assertTrue(w.blindTicks(respawned) == 0, "blind in another dimension");
		tickBlind(w, server, 1);
		respawned.setServerLevel(helper.getLevel());
		helper.assertTrue(w.blindTicks(respawned) == 0, "the old moment came back with the dimension");

		// Turning spectator (PARTICIPATION false) and a server stop reset it too.
		w.onBlindState(respawned, true);
		respawned.mode = GameType.SPECTATOR;
		r.tick(server, 1);
		respawned.mode = GameType.SURVIVAL;
		r.tick(server, 1);
		helper.assertTrue(w.blindTicks(respawned) == 0, "blind through spectator mode");
		w.onBlindState(respawned, true);
		w.clear();
		helper.assertTrue(w.blindTicks(respawned) == 0, "blind after a server stop");

		// Tests and debug: blind as if for a while; never for a spectator.
		w.markBlind(respawned, 40);
		w.markBlind(spectator, 40);
		helper.assertTrue(w.blindTicks(respawned) == 40 && w.blindTicks(spectator) == 0, "markBlind");

		// The payload: its id and a round trip.
		ByteBuf buf = Unpooled.buffer();
		BlindState.CODEC.encode(buf, new BlindState(true));
		BlindState.CODEC.encode(buf, new BlindState(false));
		helper.assertTrue(BlindState.CODEC.decode(buf).blind() && !BlindState.CODEC.decode(buf).blind()
				&& BlindState.TYPE.id().toString().equals("a1016_02:blind_state"), "the payload");
		helper.succeed();
	}

	/** A director that records each recheck as the player's name and blind ticks at that moment. */
	private static final class RecordingDirector implements Director {
		private final Director stub = new Director.Stub();
		final List<String> rechecks = new ArrayList<>();
		PlayerWatch watch;

		@Override
		public void tick(MinecraftServer server) {
		}

		@Override
		public FireResult fire(MinecraftServer server, ServerPlayer player, String cardId, boolean fake) {
			return stub.fire(server, player, cardId, fake);
		}

		@Override
		public List<String> timewarp(MinecraftServer server, int days) {
			return List.of();
		}

		@Override
		public long ticksSinceTag(MinecraftServer server, CardTag tag) {
			return Long.MAX_VALUE;
		}

		@Override
		public boolean inQuiet(MinecraftServer server) {
			return false;
		}

		@Override
		public List<String> debugLines(MinecraftServer server) {
			return List.of();
		}

		@Override
		public void recheck(MinecraftServer server, ServerPlayer player) {
			rechecks.add(player.getName().getString() + "@" + watch.blindTicks(player));
		}
	}

	private static List<String> at(String name, long... ticks) {
		List<String> list = new ArrayList<>();
		for (long tick : ticks) {
			list.add(name + "@" + tick);
		}
		return list;
	}

	private static void tickBlind(PlayerWatch watch, MinecraftServer server, int ticks) {
		for (int i = 0; i < ticks; i++) {
			watch.tickBlind(server);
		}
	}

	@GameTest
	public void blindMomentsAreRecheckedOnSchedule(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Roster r = new Roster();
		RecordingDirector director = new RecordingDirector();
		director.watch = r.watch;
		r.director = director;
		PlayerWatch w = r.watch;
		TestPlayer a = joined(helper, r, "A", GameType.SURVIVAL);
		TestPlayer b = joined(helper, r, "B", GameType.SURVIVAL);
		TestPlayer spectator = joined(helper, r, "S", GameType.SPECTATOR);

		// 30 ticks after it starts, then every 20 while it lasts, 10 at most; only for the blind player.
		w.onBlindState(a, true);
		w.onBlindState(spectator, true);
		tickBlind(w, server, 29);
		helper.assertTrue(director.rechecks.isEmpty(), "rechecked before 30 ticks: " + director.rechecks);
		tickBlind(w, server, 1);
		helper.assertTrue(director.rechecks.equals(at("A", 30)), "the first recheck: " + director.rechecks);
		tickBlind(w, server, 400);
		helper.assertTrue(director.rechecks.equals(at("A", 30, 50, 70, 90, 110, 130, 150, 170, 190, 210)),
				"ten rechecks: " + director.rechecks);
		helper.assertTrue(w.blindTicks(a) == 430, "still blind after the rechecks: " + w.blindTicks(a));

		// A new moment starts its own schedule; one that closes at 45 ticks had one recheck.
		director.rechecks.clear();
		w.onBlindState(a, false);
		w.onBlindState(a, true);
		w.onBlindState(b, true);
		tickBlind(w, server, 45);
		w.onBlindState(a, false);
		tickBlind(w, server, 10);
		helper.assertTrue(director.rechecks.equals(List.of("A@30", "B@30", "B@50")), "two moments: " + director.rechecks);

		// A death or leaving ends the schedule.
		director.rechecks.clear();
		w.onBlindState(b, false);
		w.onBlindState(a, true);
		w.onBlindState(b, true);
		tickBlind(w, server, 35);
		a.setHealth(0.0F);
		r.leave(b);
		tickBlind(w, server, 100);
		helper.assertTrue(director.rechecks.equals(List.of("A@30", "B@30")), "after a death and a leave: " + director.rechecks);

		// markBlind counts the skipped rechecks as done: blind 100 ticks, then 110, 130 ... 210.
		director.rechecks.clear();
		TestPlayer c = joined(helper, r, "C", GameType.SURVIVAL);
		w.markBlind(c, 100);
		tickBlind(w, server, 200);
		helper.assertTrue(director.rechecks.equals(at("C", 110, 130, 150, 170, 190, 210)), "after markBlind: " + director.rechecks);
		helper.assertTrue(PlayerWatch.rechecksBy(29) == 0 && PlayerWatch.rechecksBy(30) == 1 && PlayerWatch.rechecksBy(49) == 1
				&& PlayerWatch.rechecksBy(210) == 10 && PlayerWatch.rechecksBy(10_000) == 10, "rechecksBy");
		helper.succeed();
	}

	@GameTest
	public void theStubRecheckDoesNothing(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Director.Stub stub = new Director.Stub();
		List<String> before = stub.debugLines(server);
		stub.recheck(server, player);
		for (CardTag tag : CardTag.values()) {
			helper.assertTrue(stub.ticksSinceTag(server, tag) == Long.MAX_VALUE, "the stub's recheck fired a " + tag + " card");
		}
		helper.assertTrue(stub.debugLines(server).equals(before) && !stub.inQuiet(server), "the stub changed");
		// The interface's default is the same no-op for any director that does not implement it.
		RecordingDirector recording = new RecordingDirector();
		Director plain = new Director() {
			@Override
			public void tick(MinecraftServer s) {
				recording.rechecks.add("tick");
			}

			@Override
			public FireResult fire(MinecraftServer s, ServerPlayer p, String cardId, boolean fake) {
				recording.rechecks.add("fire");
				return FireResult.SKIPPED;
			}

			@Override
			public List<String> timewarp(MinecraftServer s, int days) {
				return List.of();
			}

			@Override
			public long ticksSinceTag(MinecraftServer s, CardTag tag) {
				return Long.MAX_VALUE;
			}

			@Override
			public boolean inQuiet(MinecraftServer s) {
				return false;
			}

			@Override
			public List<String> debugLines(MinecraftServer s) {
				return List.of();
			}
		};
		plain.recheck(server, player);
		helper.assertTrue(recording.rechecks.isEmpty(), "the default recheck did something: " + recording.rechecks);
		// The live watch sends its rechecks to the live director.
		helper.assertTrue(PlayerWatch.SERVER.director() == Services.director(), "the live source's director");
		helper.assertTrue(CardTag.valueOf("BLIND") == CardTag.BLIND, "CardTag.BLIND");
		helper.succeed();
	}
}
