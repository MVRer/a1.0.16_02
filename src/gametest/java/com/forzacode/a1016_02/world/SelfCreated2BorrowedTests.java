package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc.WorldSc;
import com.forzacode.a1016_02.world.sc2.s28.Moves;
import com.forzacode.a1016_02.world.sc2.s28.S28Card;
import com.forzacode.a1016_02.world.sc2.s28.S28Data;
import com.forzacode.a1016_02.world.sc2.s28.S28Env;
import com.forzacode.a1016_02.world.sc2.s28.Sc28Config;
import com.forzacode.a1016_02.world.sc2.s28.Visitors;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-7, feature 28 (docs/SELF_CREATED_2.md): a stack from one player's base into another's after a visit; the
 * refusals (no visit, someone near, in view, the richest container, a base with one, the stack's kind, the first chest,
 * home and awake, one shared base); the turns; the fake within one base; given back by the switch's give-back and by
 * the ledger's undo (Ending D's path); the caps; the multiplayer rules (a spectator near, a leave, a late joiner, another
 * dimension, three players); the visit tracker; the saved data. Every test runs in its own patch of the test server's
 * overworld far out (x from 21 000 000, z 3 200 000, a slot each), its chunks held loaded while it runs, with mock
 * players in a private list ({@link Friends}) and its own data and numbers; each runs within its first tick.
 */
public class SelfCreated2BorrowedTests {
	/** A player only a test's own list holds online, whose game mode the test can switch. */
	static final class Mock extends ServerPlayer {
		GameType mode = GameType.SURVIVAL;

		Mock(ServerLevel level, String name) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	/**
	 * A test's world: a patch far out with its chunks loaded, two or three players with bases, their containers, a private
	 * online list and state, its own 28 data and numbers, and the view rule over its own players.
	 */
	static final class Yard implements PlayerWatch.Source {
		private static final AtomicInteger SLOTS = new AtomicInteger();
		static final BlockPos BASE = new BlockPos(21_000_000, 0, 3_200_000);
		static final int SPACING = 1024;
		static final int REACH = 120;

		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		final List<ServerPlayer> online = new ArrayList<>();
		final List<ServerPlayer> others = new ArrayList<>();
		HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);
		final WorldData world = new WorldData();
		final S28Data data = new S28Data();
		final Sc28Config config = new Sc28Config();
		final Mock builder;
		private final List<long[]> forced = new ArrayList<>();

		Yard(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel().getServer().overworld();
			BlockPos column = BASE.offset(SLOTS.getAndIncrement() * SPACING, 0, 0);
			for (int cx = (column.getX() - REACH) >> 4; cx <= (column.getX() + REACH) >> 4; cx++) {
				for (int cz = (column.getZ() - REACH) >> 4; cz <= (column.getZ() + REACH) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
					forced.add(new long[] {cx, cz});
				}
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			origin = new BlockPos(column.getX(), ground, column.getZ());
			builder = new Mock(level, "builder");
		}

		void release() {
			for (long[] c : forced) {
				level.setChunkForced((int) c[0], (int) c[1], false);
			}
		}

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
		}

		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}

		/** A player online in this yard at (x, z) on the ground, with their first block (their base) at {@code base}. */
		Mock player(String name, int x, int z, BlockPos base) {
			Mock p = new Mock(level, name);
			p.snapTo(Vec3.atBottomCenterOf(at(x, 0, z)), 0.0F, 0.0F);
			online.add(p);
			state.addKnownPlayer(p.getUUID(), name, new HerobrineState.FirstBlocks(
					new com.forzacode.a1016_02.core.PlacedBlock(GlobalPos.of(level.dimension(), base), Blocks.COBBLESTONE.defaultBlockState()), null, null));
			return p;
		}

		/** A spectator (or any player in the level not taking part) who still sees and is near. */
		Mock spectator(String name, int x, int z) {
			Mock p = new Mock(level, name);
			p.mode = GameType.SPECTATOR;
			p.snapTo(Vec3.atBottomCenterOf(at(x, 0, z)), 0.0F, 0.0F);
			others.add(p);
			return p;
		}

		/** A chest a player placed at {@code pos} (absolute), holding these stacks from slot 0. */
		BlockPos chest(BlockPos pos, ItemStack... stacks) {
			return container(pos, Blocks.CHEST.defaultBlockState(), stacks);
		}

		BlockPos container(BlockPos pos, BlockState state, ItemStack... stacks) {
			level.setBlock(pos, state, 3);
			watch.onPlaced(builder, level, pos, state);
			Container container = (Container) level.getBlockEntity(pos);
			for (int i = 0; i < stacks.length; i++) {
				container.setItem(i, stacks[i]);
			}
			return pos;
		}

		Container box(BlockPos pos) {
			return (Container) level.getBlockEntity(pos);
		}

		Bases bases() {
			return new Bases(level.getServer(), state, watch, world);
		}

		/** Everyone in the level for the "nobody within" rule: the players online and the others. */
		List<ServerPlayer> people() {
			List<ServerPlayer> all = new ArrayList<>(online);
			all.addAll(others);
			return all;
		}

		S28Env env() {
			Pacing pacing = ModConfig.pacing();
			return new S28Env(bases(), Services.traces(), (l, cells) -> {
				List<TraceService.Viewer> viewers = new ArrayList<>();
				for (ServerPlayer p : people()) {
					viewers.add(TraceService.Viewer.of(p, 10));
				}
				return TraceService.positionsOutOfView(l, cells, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
			}, l -> people(), data, config, Services.protectedAreas());
		}

		/** {@code visitor} came by {@code host}'s base now. */
		void visited(ServerPlayer visitor, ServerPlayer host) {
			data.visit(visitor.getUUID(), host.getUUID(), GameClock.dayTicks(level.getServer()));
		}

		/** Moves a player to (x, z) on the ground, looking along {@code yaw} (0 south) and slightly down. */
		void move(ServerPlayer p, int x, int z, float yaw) {
			p.snapTo(Vec3.atBottomCenterOf(at(x, 0, z)), yaw, 10.0F);
			p.setYHeadRot(yaw);
		}
	}

	private static ItemStack stack(net.minecraft.world.item.Item item, int count) {
		return new ItemStack(item, count);
	}

	/**
	 * Two homes 120 apart: A's base with a container worth the most (diamonds) and a second with iron, B's with an empty
	 * chest; B came by A's base; both 40 from home, facing away. Returns {A, B} with A's rich, A's other and B's chest.
	 */
	private record Two(Yard yard, Mock a, Mock b, BlockPos rich, BlockPos other, BlockPos theirs) {
	}

	private static Two two(GameTestHelper helper) {
		Yard yard = new Yard(helper);
		BlockPos baseA = yard.at(-60, 0, 0);
		BlockPos baseB = yard.at(60, 0, 0);
		Mock a = yard.player("ana", -60, 40, baseA);
		Mock b = yard.player("ben", 60, 40, baseB);
		yard.move(a, -60, 42, 0.0F);
		yard.move(b, 60, 42, 0.0F);
		BlockPos rich = yard.chest(baseA.offset(3, 0, 0), stack(Items.DIAMOND, 12), stack(Items.IRON_INGOT, 30));
		BlockPos other = yard.chest(baseA.offset(-3, 0, 2), stack(Items.IRON_INGOT, 20));
		BlockPos theirs = yard.chest(baseB.offset(2, 0, -2), stack(Items.SPRUCE_LOG, 64));
		yard.visited(b, a);
		return new Two(yard, a, b, rich, other, theirs);
	}

	private static long countOf(Container container, net.minecraft.world.item.Item item) {
		long n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	private static List<TraceLedger.Entry> movesAt(MinecraftServer server, BlockPos from) {
		return TraceLedger.get(server).entries().stream()
				.filter(e -> e.cause().equals(Moves.CAUSE) && e.kind() == TraceLedger.Kind.MOVE_STACK && e.pos().pos().equals(from)).toList();
	}

	/** The card is the design's row, registered; the numbers are the plan's. */
	@GameTest
	public void theCardAndItsNumbersAreThePlans(GameTestHelper helper) {
		EventCard card = CardRegistry.get(Moves.ID).orElseThrow();
		helper.assertTrue(card.tier() == Tier.MINOR && card.earliestStage() == Stage.TRACES && card.tags().equals(java.util.Set.of(CardTag.ITEM))
				&& card.habits().equals(java.util.Set.of(Habit.COLLECTOR, Habit.VISITOR)) && card.hasFake(), "sc_28 is not the plan's row");
		Sc28Config c = new Sc28Config();
		helper.assertTrue(c.visitDays == 2 && c.visitRadius == 16 && c.nobodyWithin == 16 && c.awayDistance == 32 && c.maxPerWorld == 3
				&& c.minDaysApart == 3 && c.maxCount == 64, "the plan's numbers");
		helper.succeed();
	}

	/**
	 * After B came by A's base, a fire with both 32+ from home takes one stack from A's container that is not worth the
	 * most into B's empty slot, ledgered (MOVE_STACK, its cause), counted, the way kept; the diamonds stay.
	 */
	@GameTest(maxTicks = 40)
	public void aStackGoesFromOneBaseToTheOtherAfterAVisit(GameTestHelper helper) {
		Two t = two(helper);
		Yard yard = t.yard();
		try {
			Moves.Outcome outcome = Moves.fire(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(outcome.result() == FireResult.FIRED, "no move: " + outcome.reason());
			helper.assertTrue(countOf(yard.box(t.other()), Items.IRON_INGOT) == 0 && countOf(yard.box(t.theirs()), Items.IRON_INGOT) == 20,
					"the iron is not in B's chest");
			helper.assertTrue(countOf(yard.box(t.rich()), Items.DIAMOND) == 12 && countOf(yard.box(t.rich()), Items.IRON_INGOT) == 30,
					"the container worth the most was touched");
			helper.assertTrue(countOf(yard.box(t.theirs()), Items.SPRUCE_LOG) == 64, "B's own stack changed");
			helper.assertTrue(movesAt(yard.level.getServer(), t.other()).size() == 1, "not ledgered as one move of 28");
			helper.assertTrue(yard.data.count() == 1 && t.a().getUUID().equals(yard.data.lastFrom()) && t.b().getUUID().equals(yard.data.lastTo()),
					"not counted with its way");
			// The cap of days: the next one waits three days.
			Moves.Plan again = Moves.plan(yard.env(), t.b(), false, false, RandomSource.create(2));
			helper.assertFalse(again.found(), "a second one the same day");
			helper.assertTrue(again.reason().contains("days apart"), "the wrong refusal: " + again.reason());
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/** Each refusal on its own: no visit, a spectator near, in view, home and awake, one shared base, and the forced fire. */
	@GameTest(maxTicks = 40)
	public void itRefusesWithoutAVisitWithSomeoneNearInViewOrAtHome(GameTestHelper helper) {
		Two t = two(helper);
		Yard yard = t.yard();
		try {
			S28Data fresh = new S28Data();
			S28Env noVisit = new S28Env(yard.bases(), Services.traces(), yard.env().view(), yard.env().people(), fresh, yard.config, Services.protectedAreas());
			Moves.Plan plan = Moves.plan(noVisit, t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("did not come by"), "without a visit: " + plan.reason());
			helper.assertTrue(Moves.plan(noVisit, t.a(), true, false, RandomSource.create(1)).found(), "forced skips the visit");
			// An old visit (three days) does not count.
			fresh.visit(t.b().getUUID(), t.a().getUUID(), GameClock.dayTicks(yard.level.getServer()) - 3 * 24000L);
			helper.assertFalse(Moves.plan(noVisit, t.a(), false, false, RandomSource.create(1)).found(), "a visit three days old counts");

			// A spectator within 16 of A's giving chest.
			Mock near = yard.spectator("near", -63, 6);
			plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("within 16"), "a spectator beside the chest: " + plan.reason());
			yard.others.remove(near);
			// Seen: someone 20 off looking at B's chest (a viewer, outside the 16).
			Mock watcher = yard.spectator("watcher", 62, 18);
			yard.move(watcher, 62, 18, 180.0F);
			plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("in view"), "B's chest in someone's view: " + plan.reason());
			yard.others.remove(watcher);
			helper.assertTrue(Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1)).found(), "nothing in the way now");

			// Home and awake (even forced), then asleep is away.
			yard.move(t.b(), 60, 10, 0.0F);
			plan = Moves.plan(yard.env(), t.a(), true, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("their base and awake"), "B at home: " + plan.reason());
			yard.move(t.b(), 60, 42, 0.0F);
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/** The stacks that never go, and a base whose only container is the one worth the most; the first chest; one shared base. */
	@GameTest(maxTicks = 40)
	public void onlyPlainStacksGoAndNeverFromTheRichestOrTheFirstChest(GameTestHelper helper) {
		ItemStack named = stack(Items.IRON_INGOT, 5);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("mine"));
		ItemStack marked = stack(Items.COBBLESTONE, 5);
		CustomData.update(DataComponents.CUSTOM_DATA, marked, tag -> tag.putString("x", "y"));
		ItemStack enchantedBook = stack(Items.ENCHANTED_BOOK, 1);
		helper.assertFalse(Moves.movable(stack(Items.IRON_PICKAXE, 1), 64), "a tool");
		helper.assertFalse(Moves.movable(stack(Items.IRON_SWORD, 1), 64), "a weapon");
		helper.assertFalse(Moves.movable(stack(Items.IRON_CHESTPLATE, 1), 64), "armor");
		helper.assertFalse(Moves.movable(named, 64), "a named stack");
		helper.assertFalse(Moves.movable(marked, 64), "a marked stack (the story's own items)");
		helper.assertFalse(Moves.movable(enchantedBook, 64), "an enchanted book");
		helper.assertFalse(Moves.movable(stack(Items.WRITABLE_BOOK, 1), 64), "a book to write in");
		helper.assertFalse(Moves.movable(stack(Items.FILLED_MAP, 1), 64), "a map");
		helper.assertFalse(Moves.movable(stack(Items.GOLDEN_APPLE, 3), 64), "a rare thing");
		helper.assertFalse(Moves.movable(stack(Items.DIAMOND, 3), 64), "diamonds (precious, not an ingot)");
		helper.assertFalse(Moves.movable(stack(Items.CHEST, 3), 64), "a block with a block entity");
		helper.assertFalse(Moves.movable(stack(Items.TORCH, 3), 64), "a torch is no building block");
		helper.assertTrue(Moves.movable(stack(Items.IRON_INGOT, 64), 64) && Moves.movable(stack(Items.RAW_COPPER, 9), 64)
				&& Moves.movable(stack(Items.BREAD, 12), 64) && Moves.movable(stack(Items.SPRUCE_PLANKS, 64), 64)
				&& Moves.movable(stack(Items.COBBLESTONE, 64), 64) && Moves.movable(stack(Items.COAL, 30), 64), "the plain stacks");
		helper.assertFalse(Moves.movable(stack(Items.COBBLESTONE, 40), 32), "more than the most");
		helper.assertTrue(Moves.worth(List.of(stack(Items.DIAMOND, 1))) > Moves.worth(List.of(stack(Items.COBBLESTONE, 63))), "diamonds worth less than stone");

		Two t = two(helper);
		Yard yard = t.yard();
		try {
			// Only plain stacks in the second chest: none may go (a tool, a named ingot).
			yard.box(t.other()).setItem(0, stack(Items.IRON_PICKAXE, 1));
			yard.box(t.other()).setItem(1, named);
			Moves.Plan plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("nothing that may go"), "only a tool and a named stack: " + plan.reason());
			// A base with one container: the one worth the most, so nothing goes.
			yard.level.setBlock(t.other(), Blocks.AIR.defaultBlockState(), 3);
			plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("container(s)"), "one container: " + plan.reason());
			// A's first chest never gives, even as the second container.
			BlockPos first = yard.chest(t.rich().offset(-6, 0, -4), stack(Items.IRON_INGOT, 9));
			yard.state.addKnownPlayer(t.a().getUUID(), "ana", new HerobrineState.FirstBlocks(null, null,
					new com.forzacode.a1016_02.core.PlacedBlock(GlobalPos.of(yard.level.dimension(), first), Blocks.CHEST.defaultBlockState())));
			plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertFalse(plan.found(), "the first chest gave: " + plan.reason());
			helper.assertTrue(Moves.boxes(yard.env(), yard.level, yard.at(-60, 0, 0)).size() == 1, "the first chest counted as a container");
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/** Two bases within twice the visit radius are one home: nothing moves between them. */
	@GameTest(maxTicks = 40)
	public void twoBasesInOneHouseAreOneBase(GameTestHelper helper) {
		Yard yard = new Yard(helper);
		try {
			Mock a = yard.player("ana", 0, 50, yard.at(0, 0, 0));
			Mock b = yard.player("ben", 10, 50, yard.at(10, 0, 0));
			yard.chest(yard.at(2, 0, 2), stack(Items.DIAMOND, 3));
			yard.chest(yard.at(-2, 0, 2), stack(Items.IRON_INGOT, 3));
			yard.chest(yard.at(12, 0, 2));
			yard.visited(b, a);
			Moves.Plan plan = Moves.plan(yard.env(), a, true, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("are one"), "one house: " + plan.reason());
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/** Directions take turns: after A to B, nothing more comes from A's base; B to A goes once A came by B's. */
	@GameTest(maxTicks = 40)
	public void directionsTakeTurns(GameTestHelper helper) {
		Two t = two(helper);
		Yard yard = t.yard();
		try {
			yard.config.minDaysApart = 0;
			helper.assertTrue(Moves.fire(yard.env(), t.b(), false, false, RandomSource.create(3)).result() == FireResult.FIRED, "the first");
			helper.assertTrue(t.a().getUUID().equals(yard.data.lastFrom()), "the first went the wrong way");
			// More iron at A's: still nothing from A's base, and no visit by A to B's yet.
			yard.box(t.other()).setItem(5, stack(Items.IRON_INGOT, 7));
			Moves.Plan plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(4));
			helper.assertFalse(plan.found(), "from A's base twice in a row, or without A's visit: " + plan.reason());
			// B gets a second container (one with bread) so B can give; A comes by.
			yard.chest(yard.at(60, 0, 0).offset(-3, 0, 3), stack(Items.BREAD, 10));
			yard.visited(t.a(), t.b());
			Moves.Outcome back = Moves.fire(yard.env(), t.a(), false, false, RandomSource.create(5));
			helper.assertTrue(back.result() == FireResult.FIRED && back.move() != null && back.move().fromPlayer().equals(t.b().getUUID()),
					"B to A did not go: " + back.reason());
			helper.assertTrue(t.b().getUUID().equals(yard.data.lastFrom()) && yard.data.count() == 2, "the way did not turn");
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/** The fake: a stack between two of the target's own containers, never the richest giving; counted, the way kept. */
	@GameTest(maxTicks = 40)
	public void theFakeMovesWithinOneBase(GameTestHelper helper) {
		Two t = two(helper);
		Yard yard = t.yard();
		try {
			BlockPos third = yard.chest(yard.at(-60, 0, 0).offset(0, 0, -4));
			Moves.Outcome fake = Moves.fire(yard.env(), t.a(), true, true, RandomSource.create(1));
			helper.assertTrue(fake.result() == FireResult.FIRED && fake.move() != null && fake.move().fake(), "no fake: " + fake.reason());
			helper.assertTrue(countOf(yard.box(t.theirs()), Items.IRON_INGOT) == 0, "the fake left A's base");
			helper.assertTrue(countOf(yard.box(t.rich()), Items.DIAMOND) == 12, "the fake took from the richest");
			long iron = countOf(yard.box(t.rich()), Items.IRON_INGOT) + countOf(yard.box(t.other()), Items.IRON_INGOT) + countOf(yard.box(third), Items.IRON_INGOT);
			helper.assertTrue(iron == 50 && countOf(yard.box(t.other()), Items.IRON_INGOT) == 0, "the iron is not where the fake put it");
			helper.assertTrue(yard.data.count() == 1 && yard.data.lastFrom() == null, "the fake set the way");
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/**
	 * Given back: the ledger's undo (Ending D's afterward) puts the stack back in A's chest; the switch's give-back
	 * ({@code WorldSc.giveBackAll} over 28's cause) does too; once taken out of B's chest it is B's (blocked, kept).
	 */
	@GameTest(maxTicks = 40)
	public void givenBackThroughTheLedger(GameTestHelper helper) {
		Two t = two(helper);
		Yard yard = t.yard();
		MinecraftServer server = yard.level.getServer();
		try {
			yard.config.minDaysApart = 0;
			helper.assertTrue(Moves.fire(yard.env(), t.a(), false, false, RandomSource.create(1)).result() == FireResult.FIRED, "no move");
			TraceLedger.Entry entry = movesAt(server, t.other()).getFirst();
			helper.assertTrue(Services.traces().undo(yard.level, entry) == TraceService.UndoResult.DONE, "Ending D's undo did not give it back");
			helper.assertTrue(countOf(yard.box(t.other()), Items.IRON_INGOT) == 20 && countOf(yard.box(t.theirs()), Items.IRON_INGOT) == 0, "not back");
			// Again, given back by the switch's give-back.
			yard.data.reset();
			yard.visited(t.b(), t.a());
			helper.assertTrue(Moves.fire(yard.env(), t.a(), false, false, RandomSource.create(1)).result() == FireResult.FIRED, "no second move");
			WorldSc.Back back = WorldSc.giveBackAll(server, Moves.CAUSE);
			helper.assertTrue(back.done() >= 1 && countOf(yard.box(t.other()), Items.IRON_INGOT) == 20, "the switch's give-back did not give it back");
			helper.assertTrue(movesAt(server, t.other()).isEmpty(), "the move is still in the ledger");
			// A third, then B takes it out: it stays B's.
			yard.data.reset();
			yard.visited(t.b(), t.a());
			helper.assertTrue(Moves.fire(yard.env(), t.a(), false, false, RandomSource.create(1)).result() == FireResult.FIRED, "no third move");
			Container theirs = yard.box(t.theirs());
			for (int i = 0; i < theirs.getContainerSize(); i++) {
				if (theirs.getItem(i).is(Items.IRON_INGOT)) {
					theirs.setItem(i, ItemStack.EMPTY);
				}
			}
			entry = movesAt(server, t.other()).getFirst();
			helper.assertTrue(Services.traces().undo(yard.level, entry) == TraceService.UndoResult.BLOCKED, "a stack taken since was given back");
			TraceLedger.get(server).remove(entry);
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/** At most three in a world (fakes count), three days apart; forced skips both. */
	@GameTest(maxTicks = 40)
	public void atMostThreeAWorldThreeDaysApart(GameTestHelper helper) {
		Two t = two(helper);
		Yard yard = t.yard();
		try {
			for (int i = 0; i < 3; i++) {
				yard.data.moved(t.a().getUUID(), t.b().getUUID(), -10, true);
			}
			Moves.Plan plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("at most 3"), "a fourth: " + plan.reason());
			helper.assertTrue(Moves.plan(yard.env(), t.a(), true, false, RandomSource.create(1)).found(), "forced skips the cap");
			yard.data.reset();
			yard.data.moved(t.b().getUUID(), t.a().getUUID(), GameClock.day(yard.level.getServer()), false);
			plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("days apart"), "the same day: " + plan.reason());
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/**
	 * Multiplayer: a leave (B offline) leaves no pair, and B back later (a late join) finds the visit kept by UUID; B in
	 * another dimension counts as away; with three, a third player near B's chest holds it and C's own base can take.
	 */
	@GameTest(maxTicks = 40)
	public void friendsLeavingJoiningElsewhereAndAThird(GameTestHelper helper) {
		Two t = two(helper);
		Yard yard = t.yard();
		try {
			yard.online.remove(t.b());
			Moves.Plan plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertTrue(!plan.found() && plan.reason().contains("nobody else"), "a pair with B gone: " + plan.reason());
			// B joins again later (a new object, the same UUID): the visit is kept.
			Mock again = new Mock(yard.level, "ben");
			again.setUUID(t.b().getUUID());
			again.snapTo(Vec3.atBottomCenterOf(yard.at(60, 0, 42)), 0.0F, 0.0F);
			yard.online.add(again);
			helper.assertTrue(Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1)).found(), "the late join lost the visit");
			// In the Nether: away (B's base stays loaded here).
			ServerLevel nether = yard.level.getServer().getLevel(Level.NETHER);
			again.setServerLevel(nether);
			helper.assertTrue(Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1)).found(), "B in another dimension is not away");
			again.setServerLevel(yard.level);
			// A third: C at B's chest holds it (C takes part, 5 off); C's own base far off can take instead.
			Mock c = yard.player("cai", 61, 1, yard.at(0, 0, 100));
			plan = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(1));
			helper.assertFalse(plan.found() && plan.move().toPlayer().equals(again.getUUID()), "into B's chest with C beside it");
			yard.move(c, 0, 60, 0.0F);
			BlockPos cChest = yard.chest(yard.at(2, 0, 100));
			yard.visited(c, t.a());
			Moves.Plan toC = Moves.plan(yard.env(), t.a(), false, false, RandomSource.create(9));
			helper.assertTrue(toC.found(), "neither B nor C can take: " + toC.reason());
			helper.assertTrue(Moves.boxes(yard.env(), yard.level, yard.at(0, 0, 100)).stream().anyMatch(b -> b.halves().contains(cChest)), "C's chest unseen");
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/** A double chest is one container (its worth both halves'); a barrel counts; the visit tracker records within 16 only. */
	@GameTest(maxTicks = 40)
	public void doubleChestsBarrelsAndTheVisitTracker(GameTestHelper helper) {
		Yard yard = new Yard(helper);
		try {
			BlockPos base = yard.at(0, 0, 0);
			BlockState left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT);
			BlockState right = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT);
			BlockPos l = yard.container(base.offset(3, 0, 0), left, stack(Items.DIAMOND, 2));
			BlockPos r = yard.container(ChestBlock.getConnectedBlockPos(l, left), right, stack(Items.DIAMOND, 2));
			yard.container(base.offset(-4, 0, 0), Blocks.BARREL.defaultBlockState(), stack(Items.BREAD, 3));
			List<Moves.Box> boxes = Moves.boxes(yard.env(), yard.level, base);
			helper.assertTrue(boxes.size() == 2, "the double chest is not one container and the barrel another: " + boxes.size());
			Moves.Box pair = boxes.stream().filter(b -> b.halves().size() == 2).findFirst().orElse(null);
			helper.assertTrue(pair != null && pair.halves().contains(l) && pair.halves().contains(r), "the halves: " + boxes);
			helper.assertTrue(Moves.worth(Moves.contents(yard.level, pair)) == 2 * Moves.worth(List.of(stack(Items.DIAMOND, 2))), "the pair's worth is one half's");

			Mock a = yard.player("ana", 100, 100, base);
			Mock b = yard.player("ben", 10, 10, yard.at(200, 0, 0));
			Mock watcher = yard.spectator("spec", 1, 1);
			yard.online.add(watcher);
			S28Env env = yard.env();
			List<S28Data.Pair> seen = Visitors.look(env);
			helper.assertTrue(seen.contains(new S28Data.Pair(b.getUUID(), a.getUUID())), "B within 16 of A's base was not seen");
			helper.assertFalse(seen.stream().anyMatch(p -> p.visitor().equals(watcher.getUUID())), "a spectator visited");
			helper.assertFalse(seen.stream().anyMatch(p -> p.visitor().equals(a.getUUID())), "A visited B's base 141 off");
			yard.move(b, 17, 0, 0.0F);
			helper.assertTrue(Visitors.look(env).isEmpty(), "17 off counts as a visit");
			helper.assertTrue(env.data().lastVisit(b.getUUID(), a.getUUID()) >= 0, "the visit was not kept");
		} finally {
			yard.release();
		}
		helper.succeed();
	}

	/** The saved data round trip and leniency; the card's kept answer is forgotten after a fire. */
	@GameTest
	public void theDataSavesAndReadsLeniently(GameTestHelper helper) {
		S28Data data = new S28Data();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		data.visit(b, a, 12345L);
		data.moved(a, b, 7, false);
		data.moved(b, a, 9, true);
		CompoundTag tag = (CompoundTag) S28Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		S28Data read = S28Data.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(read.lastVisit(b, a) == 12345L && read.count() == 2 && read.lastDay() == 9 && a.equals(read.lastFrom()) && b.equals(read.lastTo()),
				"the round trip");
		CompoundTag broken = tag.copy();
		broken.putString("from", "not a uuid");
		broken.putString("count", "x");
		S28Data lenient = S28Data.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
		helper.assertTrue(lenient.lastFrom() == null && lenient.count() == 0 && lenient.lastVisit(b, a) == 12345L, "not lenient");
		helper.assertTrue(S28Data.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow().count() == 0, "an empty file");
		S28Card card = new S28Card();
		helper.assertTrue(card.id().equals(Moves.ID) && !card.tags().contains(CardTag.PERSONAL) && !card.tags().contains(CardTag.UNSHARED), "the card's tags");
		helper.succeed();
	}

	/** No host check: nothing of 28's code asks whether a player hosts (LAN host and guest alike). */
	@GameTest
	public void nothingAsksWhoHosts(GameTestHelper helper) throws java.io.IOException {
		java.nio.file.Path root = sourceRoot();
		helper.assertTrue(root != null, "no sources");
		for (String dir : List.of("com/forzacode/a1016_02/world/sc2/s28", "com/forzacode/a1016_02/atmosphere/sc2/s29", "com/forzacode/a1016_02/lore/sc2/s30")) {
			try (var files = java.nio.file.Files.walk(root.resolve(dir))) {
				for (java.nio.file.Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
					String text = java.nio.file.Files.readString(file);
					helper.assertFalse(text.contains("isSingleplayerOwner") || text.contains("getSingleplayerProfile") || text.contains("isDedicatedServer")
							|| text.contains("isPublished"), file.getFileName() + " asks who hosts");
				}
			}
		}
		helper.succeed();
	}

	/** The mod's {@code src/main/java}, found upward from the working directory. */
	static java.nio.file.Path sourceRoot() {
		for (java.nio.file.Path at = java.nio.file.Paths.get("").toAbsolutePath(); at != null; at = at.getParent()) {
			java.nio.file.Path src = at.resolve("src/main/java");
			if (java.nio.file.Files.isDirectory(src.resolve("com/forzacode/a1016_02"))) {
				return src;
			}
		}
		return null;
	}
}
