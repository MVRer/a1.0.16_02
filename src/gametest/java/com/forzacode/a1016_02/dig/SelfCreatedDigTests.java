package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.dig.sc.DigScMemory;
import com.forzacode.a1016_02.dig.sc.RestRefusal;
import com.forzacode.a1016_02.dig.sc.Sc12Config;
import com.forzacode.a1016_02.dig.sc.Sc13Config;
import com.forzacode.a1016_02.dig.sc.WayBack;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The self-created layer's dig part (docs/SELF_CREATED.md, 12 and 13). 12: a bed over a finished shaft is refused once
 * with vanilla's own NOT_SAFE through the real {@code startSleepInBed} (the mixin), then sleeps; a real monster is
 * vanilla's refusal and keeps the arm; no shaft, no card; a friend's bed within 8 is refused for 20 s; the arm runs out
 * after 3 nights; at most 3 a world; the revert; storage. 13: in a built 1-wide, 2-high natural corridor lit by the
 * player, 40 path blocks past the point it closes with the wall's own stone and leaves the hollow, ledgered as two
 * moves, and the undo opens it; refused in view, in a dug tunnel, without a pickaxe, beside a friend, in a 2-wide
 * passage, once the world had its one; the trail. Each test has its own data, crew, clocks and windows; the bed tests
 * hand their own env to the real path by their mock player's UUID only.
 */
public class SelfCreatedDigTests {
	private static final BedRule ANY_TIME = new BedRule(BedRule.Rule.ALWAYS, BedRule.Rule.ALWAYS, false, false, Optional.empty());

	// --- 12: not safe to rest ---

	/** A bedroom in the ground with a bed, a network whose shaft is done under it, and clocks the test moves. */
	private static final class Bedroom {
		final DigGround ground;
		final ServerLevel level;
		final BlockPos head;
		final BlockPos foot;
		final DigData data = new DigData();
		final Network net;
		final long[] day = {0};
		final long[] now = {1_000_000};
		final long[] sinceCombat = {Long.MAX_VALUE};
		final RestRefusal.Windows windows = new RestRefusal.Windows();
		final Crew crew;

		Bedroom(GameTestHelper helper, int slot) {
			ground = DigGround.of(helper, slot, 20, 8, 20, Blocks.STONE);
			level = ground.level;
			ground.hollow(2, 1, 2, 16, 3, 16);
			foot = ground.at(6, 1, 6);
			head = ground.at(6, 1, 7);
			bed(head, foot);
			net = network(head, foot, head.getY() - 3);
			data.networks.add(net);
			crew = new Crew(helper);
		}

		void bed(BlockPos headAt, BlockPos footAt) {
			BlockState state = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
			level.setBlock(footAt, state.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
			level.setBlock(headAt, state.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
		}

		/** A network under this bed whose shaft is done, its top anchor at {@code topAnchorY} (the bed's Y - 3: one block of floor between). */
		Network network(BlockPos headAt, BlockPos footAt, int topAnchorY) {
			Network network = new Network(level.dimension(), headAt);
			network.bedHead = headAt;
			network.bedFoot = footAt;
			network.depth = headAt.getY() - 12;
			BlockPos column = new BlockPos(Math.min(headAt.getX(), footAt.getX()), network.depth, Math.min(headAt.getZ(), footAt.getZ()));
			network.shaftFoot = column;
			for (int y = network.depth; y <= topAnchorY; y++) {
				network.addAnchor(new BlockPos(column.getX(), y, column.getZ()), true);
			}
			network.shaftDone = true;
			return network;
		}

		RestRefusal.Env env() {
			return new RestRefusal.Env(data, p -> sinceCombat[0], new Sc12Config(), () -> day[0], () -> now[0], () -> true, () -> crew.state, windows);
		}

		/** A survival player beside this bed whose respawn point it is; their attempts read this room's env. */
		ServerPlayer sleeper(BlockPos headAt) {
			ServerPlayer player = ground.helper.makeMockServerPlayer(GameType.SURVIVAL) instanceof ServerPlayer p ? p : null;
			player.snapTo(headAt.getX() + 0.5, headAt.getY(), headAt.getZ() + 1.5, 0, 0);
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), headAt, 0, 0), false), false);
			RestRefusal.useFor(player.getUUID(), env());
			return player;
		}

		Either<Player.BedSleepingProblem, Unit> sleep(ServerPlayer player, BlockPos headAt) {
			BlockState state = level.getBlockState(headAt);
			Either<Player.BedSleepingProblem, Unit> result = player.startSleepInBed((AbstractBedBlock) state.getBlock(), state, ANY_TIME, headAt);
			if (player.isSleeping()) {
				player.stopSleepInBed(false, false);
			}
			return result;
		}
	}

	private static boolean notSafe(Either<Player.BedSleepingProblem, Unit> result) {
		return result.left().filter(Player.BedSleepingProblem.NOT_SAFE::equals).isPresent();
	}

	/**
	 * Armed over a finished shaft: a real monster is vanilla's refusal and the arm is kept; with it gone the next attempt
	 * is refused once with vanilla's NOT_SAFE (the real path, through the mixin), and the one after sleeps.
	 */
	@GameTest(maxTicks = 200)
	public void aBedOverTheShaftIsRefusedOnceThenSleeps(GameTestHelper helper) {
		Bedroom room = new Bedroom(helper, 141);
		// Held loaded for a few ticks first, so the room's chunk takes entities (the zombie) as a ticking chunk does.
		room.ground.keepLoaded(true);
		helper.runAfterDelay(5, () -> {
			ServerPlayer player = room.sleeper(room.head);
			try {
				helper.assertTrue(RestRefusal.why(player, room.env(), false) == null, "the card does not fit: " + RestRefusal.why(player, room.env(), false));
				helper.assertTrue(RestRefusal.arm(player, room.env(), false) != null, "not armed");
				Zombie zombie = EntityTypes.ZOMBIE.create(room.level, EntitySpawnReason.COMMAND);
				zombie.snapTo(room.head.getX() + 4.5, room.head.getY(), room.head.getZ() + 4.5, 0, 0);
				boolean added = room.level.addFreshEntity(zombie);
				try {
					helper.assertFalse(room.level.getEntitiesOfClass(Monster.class, new AABB(room.head).inflate(8, 5, 8)).isEmpty(),
							"the zombie is not there (added " + added + ")");
					helper.assertTrue(notSafe(room.sleep(player, room.head)), "a real monster did not refuse");
					helper.assertTrue(room.data.sc().restArm(player.getUUID()).isPresent() && room.data.sc().restRefusals() == 0,
							"a real monster spent the arm");
				} finally {
					zombie.discard();
				}
				helper.assertTrue(notSafe(room.sleep(player, room.head)), "the armed bed was not refused");
				helper.assertTrue(room.data.sc().restArm(player.getUUID()).isEmpty() && room.data.sc().restRefusals() == 1, "the arm was not spent");
				Either<Player.BedSleepingProblem, Unit> next = room.sleep(player, room.head);
				helper.assertTrue(next.right().isPresent(), "the next attempt did not sleep: " + next.left().map(p -> p.message().getString()).orElse(""));
			} finally {
				RestRefusal.useFor(player.getUUID(), null);
				room.ground.keepLoaded(false);
			}
			helper.succeed();
		});
	}

	/** No finished shaft (or its top too far under the bed): the card does not fit; a debug arm without the shaft does. */
	@GameTest
	public void withoutTheShaftUnderTheBedTheCardDoesNotFit(GameTestHelper helper) {
		Bedroom room = new Bedroom(helper, 142);
		ServerPlayer player = room.sleeper(room.head);
		try {
			room.net.shaftDone = false;
			helper.assertTrue(String.valueOf(RestRefusal.why(player, room.env(), false)).startsWith("no finished shaft"), "a climbing shaft fit");
			room.data.networks.clear();
			room.data.networks.add(room.network(room.head, room.foot, room.head.getY() - 6));
			helper.assertTrue(String.valueOf(RestRefusal.why(player, room.env(), false)).startsWith("no finished shaft"), "a shaft 5 under fit");
			helper.assertTrue(RestRefusal.why(player, room.env(), true) == null, "the debug arm without a shaft did not fit");
			DigScMemory.RestArm arm = RestRefusal.arm(player, room.env(), true);
			helper.assertTrue(arm != null && arm.noShaft(), "no debug arm");
			helper.assertTrue(RestRefusal.refuse(player, room.head, room.env()), "the debug arm did not refuse");
		} finally {
			RestRefusal.useFor(player.getUUID(), null);
		}
		helper.succeed();
	}

	/**
	 * A friend whose bed is within 8 of the refused one is refused the same way for 20 s, a bed farther off is not, and
	 * the refused player's own next attempt is vanilla's.
	 */
	@GameTest
	public void aFriendsBedWithinEightIsRefusedForTwentySeconds(GameTestHelper helper) {
		Bedroom room = new Bedroom(helper, 143);
		ServerPlayer ana = room.sleeper(room.head);
		BlockPos benHead = room.ground.at(12, 1, 7);
		BlockPos benFoot = room.ground.at(12, 1, 6);
		room.bed(benHead, benFoot);
		BlockPos farHead = room.ground.at(15, 1, 15);
		room.bed(farHead, room.ground.at(15, 1, 14));
		ServerPlayer ben = room.sleeper(benHead);
		try {
			RestRefusal.arm(ana, room.env(), false);
			helper.assertFalse(RestRefusal.refuse(ben, benHead, room.env()), "a friend was refused before any refusal");
			helper.assertTrue(RestRefusal.refuse(ana, room.head, room.env()), "the armed bed was not refused");
			helper.assertTrue(RestRefusal.refuse(ben, benHead, room.env()), "a friend's bed 6 blocks off was not refused");
			helper.assertFalse(RestRefusal.refuse(ben, farHead, room.env()), "a bed 12 blocks off was refused");
			helper.assertFalse(RestRefusal.refuse(ana, room.head, room.env()), "the refused player's next attempt was refused");
			room.now[0] += ModConfig.realTicks(19);
			helper.assertTrue(RestRefusal.refuse(ben, benHead, room.env()), "the friend's bed was not refused at 19 s");
			room.now[0] += ModConfig.realTicks(2);
			helper.assertFalse(RestRefusal.refuse(ben, benHead, room.env()), "the friend's bed was still refused at 21 s");
		} finally {
			RestRefusal.useFor(ana.getUUID(), null);
			RestRefusal.useFor(ben.getUUID(), null);
		}
		helper.succeed();
	}

	/** The arm runs out after 3 in-game nights; at most 3 refusals in a world; the gates hold; the revert drops every arm. */
	@GameTest
	public void theArmRunsOutAfterThreeNightsAndTheWorldHasThree(GameTestHelper helper) {
		Bedroom room = new Bedroom(helper, 144);
		ServerPlayer player = room.sleeper(room.head);
		try {
			RestRefusal.arm(player, room.env(), false);
			room.day[0] += 3 * 24000L - 1;
			helper.assertTrue(room.data.sc().restArm(player.getUUID()).isPresent(), "the arm went early");
			room.day[0] += 1;
			helper.assertFalse(RestRefusal.refuse(player, room.head, room.env()), "refused after 3 nights");
			helper.assertTrue(room.data.sc().restArm(player.getUUID()).isEmpty(), "the arm outlived its nights");
			for (int i = 0; i < 3; i++) {
				room.data.sc().countRestRefusal();
			}
			helper.assertTrue(String.valueOf(RestRefusal.why(player, room.env(), false)).startsWith("the world had its 3"), "a fourth fit");
			room.data.sc().reset();
			room.crew.state.setFlag("ending:ended", true);
			helper.assertTrue("the story is over".equals(RestRefusal.why(player, room.env(), false)), "fit after the story ended");
			room.crew.state.setFlag("ending:ended", false);
			RestRefusal.arm(player, room.env(), false);
			RestRefusal.revert(room.data, room.windows);
			helper.assertTrue(room.data.sc().restArms().isEmpty() && room.windows.all().isEmpty(), "the revert left an arm");
			RestRefusal.Env off = new RestRefusal.Env(room.data, p -> Long.MAX_VALUE, new Sc12Config(), () -> 0L, () -> 0L, () -> false,
					() -> room.crew.state, room.windows);
			helper.assertTrue("the layer is switched off".equals(RestRefusal.why(player, off, false)), "fit with the layer off");
			helper.assertFalse(RestRefusal.refuse(player, room.head, off), "refused with the layer off");
			room.sinceCombat[0] = ModConfig.realTicks(10);
			helper.assertTrue("combat in the last 30 s".equals(RestRefusal.why(player, room.env(), true)), "fit 10 s after combat");
			room.sinceCombat[0] = Long.MAX_VALUE;
			RestRefusal.arm(player, room.env(), true);
			room.sinceCombat[0] = ModConfig.realTicks(10);
			helper.assertFalse(RestRefusal.refuse(player, room.head, room.env()), "refused 10 s after combat");
			helper.assertTrue(room.data.sc().restArm(player.getUUID()).isPresent(), "combat spent the arm");
		} finally {
			RestRefusal.useFor(player.getUUID(), null);
		}
		helper.succeed();
	}

	/** dig.dat keeps the layer's part only when there is one, and an old file loads with none; the sections are version 1. */
	@GameTest
	public void theLayersPartOfDigDataRoundTripsAndOldFilesLoadWithNone(GameTestHelper helper) {
		DigData data = new DigData();
		CompoundTag empty = DigData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data).getOrThrow() instanceof CompoundTag t ? t : null;
		helper.assertFalse(empty.contains("sc"), "an empty part was written");
		data.sc().setRestArm(new UUID(3, 4), new DigScMemory.RestArm(net.minecraft.core.GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3)), 77_000, true));
		data.sc().countRestRefusal();
		data.sc().countWayBack();
		CompoundTag tag = (CompoundTag) DigData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data).getOrThrow();
		DigData back = DigData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.sc().restArms().equals(data.sc().restArms()) && back.sc().restRefusals() == 1 && back.sc().wayBacks() == 1,
				"the part did not round-trip: " + back.sc().restArms());
		DigData old = DigData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
		helper.assertTrue(old.sc().isEmpty(), "an old file loaded a part");
		Sc12Config sc12 = ModConfig.readSection(new JsonObject(), Sc12Config.SECTION, Sc12Config.class, Sc12Config::new);
		Sc13Config sc13 = ModConfig.readSection(new JsonObject(), Sc13Config.SECTION, Sc13Config.class, Sc13Config::new);
		helper.assertTrue(sc12.version == 1 && sc12.shaftBelowMax == 2 && sc12.armNights == 3 && sc12.sharedBedRadius == 8 && sc12.sharedSeconds == 20
				&& sc12.maxPerWorld == 3, "sc12's numbers");
		helper.assertTrue(sc13.version == 1 && sc13.pastBlocks == 40 && sc13.trailSampleTicks == 20 && sc13.trailLength == 96 && sc13.nextTorchWithin == 6
				&& sc13.nobodyWithin == 16 && sc13.maxPerWorld == 1, "sc13's numbers");
		helper.succeed();
	}

	// --- 13: the way back ---

	/**
	 * A natural corridor along x (1 wide, 2 high, feet at y 2, z 8, from x 2 to 72) in a block of stone, a wall torch the
	 * player placed at x 22 and one at x 40, the player's trail every 4 blocks from x 2 to x 66, where they stand facing
	 * further in (east) with a pickaxe.
	 */
	private static final class Cave {
		final DigGround ground;
		final ServerLevel level;
		final Crew crew;
		final ServerPlayer player;
		final Sc13Config config = new Sc13Config();

		Cave(GameTestHelper helper, int slot, boolean dugNearThePoint, boolean wide) {
			ground = DigGround.of(helper, slot, 80, 8, 17, Blocks.STONE);
			level = ground.level;
			ground.hollow(2, 2, 8, 72, 3, 8);
			if (wide) {
				ground.hollow(2, 2, 9, 72, 3, 9);
			}
			if (dugNearThePoint) {
				for (int x = 24; x <= 28; x++) {
					ground.level.setBlock(ground.at(x, 2, 8), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					ground.level.setBlock(ground.at(x, 3, 8), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					ground.dig(ground.at(x, 2, 8));
					ground.dig(ground.at(x, 3, 8));
				}
			}
			BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH);
			ground.place(ground.at(22, 3, 8), torch);
			ground.place(ground.at(40, 3, 8), torch);
			crew = new Crew(helper, "Ana", "Ben");
			player = crew.join("Ana", Vec3.atBottomCenterOf(ground.at(66, 2, 8)));
			look(player, -90);
			player.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
			List<BlockPos> trail = new ArrayList<>();
			for (int x = 2; x <= 66; x += 4) {
				trail.add(ground.at(x, 2, 8));
			}
			WayBack.samples(player.getUUID(), level.dimension(), trail);
		}

		static void look(ServerPlayer player, float yaw) {
			player.setYRot(yaw);
			player.setYHeadRot(yaw);
			player.setXRot(0);
		}

		WayBack.Env env() {
			Pacing pacing = ModConfig.pacing();
			// The test server's own view distance may be short; a player sees 40 blocks down this corridor at 12 chunks.
			int chunks = Math.max(12, level.getServer().getPlayerList().getViewDistance());
			return new WayBack.Env(crew.watch, crew.data, config, l -> crew.online.stream().filter(p -> p.level() == l).toList(),
					(l, cells) -> TraceService.positionsOutOfView(l, cells, crew.online.stream().filter(p -> p.level() == l)
							.map(p -> TraceService.Viewer.of(p, chunks)).toList(), pacing.viewNearBlocks, pacing.viewConeDegrees),
					Services.traces(), p -> true, p -> Long.MAX_VALUE, () -> true, () -> crew.state);
		}
	}

	private static List<TraceLedger.Entry> entries(ServerLevel level, List<BlockPos> cells) {
		return TraceLedger.get(level.getServer()).entries().stream()
				.filter(e -> WayBack.CAUSE.equals(e.cause()) && cells.contains(e.pos().pos()) || WayBack.CAUSE.equals(e.cause()) && e.to().filter(cells::contains).isPresent())
				.toList();
	}

	/**
	 * 40 path blocks past the point (x 26), with a torch of theirs further in and one 4 blocks on the way out, the passage
	 * closes with the wall's own two blocks and leaves the hollow beside the next cell, in one batch of two ledgered moves;
	 * the world's one is spent; the undo opens it again.
	 */
	@GameTest(maxTicks = 200)
	public void theWayBackClosesWithTheWallsOwnStoneAndTheUndoOpensIt(GameTestHelper helper) {
		Cave cave = new Cave(helper, 145, false, false);
		try {
			WayBack.Plan plan = WayBack.plan(cave.player, cave.env());
			helper.assertTrue(plan.fits(), "no point: " + plan.summary());
			WayBack.Point point = plan.point();
			helper.assertTrue(point.at().equals(cave.ground.at(26, 2, 8)) && point.deep() == Direction.EAST && point.past() >= 40, "the point: " + plan.summary());
			List<BlockPos> cells = point.cells();
			BlockState stone = cave.level.getBlockState(point.from());
			helper.assertTrue(WayBack.fire(cave.player, cave.env()) == FireResult.FIRED, "not fired");
			helper.assertTrue(cave.level.getBlockState(point.at()).equals(stone) && cave.level.getBlockState(point.at().above()).equals(stone),
					"the passage is not closed with the wall's stone");
			helper.assertTrue(cave.level.getBlockState(point.from()).isAir() && cave.level.getBlockState(point.from().above()).isAir(), "no hollow left");
			List<TraceLedger.Entry> moves = entries(cave.level, cells);
			helper.assertTrue(moves.size() == 2 && moves.stream().allMatch(e -> e.kind() == TraceLedger.Kind.MOVE), "not two ledgered moves: " + moves);
			helper.assertTrue(cave.crew.data.sc().wayBacks() == 1 && "the world had its 1".equals(WayBack.plan(cave.player, cave.env()).gate()),
					"the world's one was not spent");
			for (TraceLedger.Entry entry : moves) {
				helper.assertTrue(Services.traces().forced().undo(cave.level, entry) == TraceService.UndoResult.DONE, "the undo failed");
			}
			helper.assertTrue(cave.level.getBlockState(point.at()).isAir() && cave.level.getBlockState(point.at().above()).isAir()
					&& cave.level.getBlockState(point.from()).equals(stone), "the undo did not open it");
		} finally {
			WayBack.forget(cave.player.getUUID());
			for (TraceLedger.Entry entry : entries(cave.level, List.of(cave.ground.at(26, 2, 8), cave.ground.at(26, 3, 8)))) {
				TraceLedger.get(cave.level.getServer()).remove(entry);
			}
		}
		helper.succeed();
	}

	/** Refused in view (looking back down the corridor), without a pickaxe, beside a friend, with too short a walk. */
	@GameTest(maxTicks = 200)
	public void theWayBackIsRefusedInViewWithoutAPickaxeAndBesideAFriend(GameTestHelper helper) {
		Cave cave = new Cave(helper, 146, false, false);
		try {
			Cave.look(cave.player, 90);
			WayBack.Plan seen = WayBack.plan(cave.player, cave.env());
			helper.assertTrue(!seen.fits() && seen.refusals().containsKey("in view"), "closed in view: " + seen.summary());
			Cave.look(cave.player, -90);
			cave.player.getInventory().clearContent();
			helper.assertTrue("no pickaxe on them".equals(WayBack.plan(cave.player, cave.env()).gate()), "closed without a pickaxe");
			cave.player.getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE));
			ServerPlayer ben = cave.crew.join("Ben", Vec3.atBottomCenterOf(cave.ground.at(70, 2, 8)));
			helper.assertTrue("not alone".equals(WayBack.plan(cave.player, cave.env()).gate()), "closed beside a friend");
			cave.crew.leave(ben);
			cave.config.pastBlocks = 80;
			WayBack.Plan shortWalk = WayBack.plan(cave.player, cave.env());
			helper.assertTrue(!shortWalk.fits() && shortWalk.candidates() == 0, "a point less than 80 back: " + shortWalk.summary());
			cave.config.pastBlocks = 40;
			helper.assertTrue(WayBack.plan(cave.player, cave.env()).fits(), "no point once it was all right again");
			BlockState torch = cave.level.getBlockState(cave.ground.at(22, 3, 8));
			cave.level.setBlock(cave.ground.at(22, 3, 8), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			WayBack.Plan unlitOut = WayBack.plan(cave.player, cave.env());
			helper.assertTrue(!unlitOut.fits() && unlitOut.refusals().keySet().stream().anyMatch(k -> k.startsWith("no torch of theirs within 6")),
					"closed with no torch of theirs on the way out: " + unlitOut.summary());
			cave.level.setBlock(cave.ground.at(22, 3, 8), torch, Block.UPDATE_CLIENTS);
			cave.level.setBlock(cave.ground.at(40, 3, 8), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			WayBack.Plan unlitIn = WayBack.plan(cave.player, cave.env());
			helper.assertTrue(!unlitIn.fits() && unlitIn.refusals().containsKey("no torch of theirs on the way further in"),
					"closed with no torch of theirs further in: " + unlitIn.summary());
			cave.level.setBlock(cave.ground.at(40, 3, 8), torch, Block.UPDATE_CLIENTS);
			ServerPlayer watcher = cave.crew.join("Ben", Vec3.atBottomCenterOf(cave.ground.at(12, 2, 8)));
			Cave.look(watcher, 90);
			WayBack.Plan near = WayBack.plan(cave.player, cave.env());
			helper.assertTrue(!near.fits() && near.refusals().keySet().stream().anyMatch(k -> k.startsWith("someone within 16")),
					"closed with someone 14 blocks from it: " + near.summary());
			cave.crew.leave(watcher);
			cave.crew.state.setFlag("director:obeyed_after_stop", true);
			helper.assertTrue("the quiet after Stop.".equals(WayBack.plan(cave.player, cave.env()).gate()), "closed in Ending A's quiet");
			cave.crew.state.setFlag("director:obeyed_after_stop", false);
			WayBack.Env env = cave.env();
			WayBack.Env underground = new WayBack.Env(env.watch(), env.data(), env.config(), env.listeners(), env.view(), env.traces(), p -> false,
					env.sinceCombat(), env.enabled(), env.state());
			helper.assertTrue("not underground".equals(WayBack.plan(cave.player, underground).gate()), "closed above ground");
			WayBack.Env off = new WayBack.Env(env.watch(), env.data(), env.config(), env.listeners(), env.view(), env.traces(), env.underground(),
					env.sinceCombat(), () -> false, env.state());
			helper.assertTrue("the layer is switched off".equals(WayBack.plan(cave.player, off).gate()), "closed with the layer off");
			WayBack.Env fought = new WayBack.Env(env.watch(), env.data(), env.config(), env.listeners(), env.view(), env.traces(), env.underground(),
					p -> ModConfig.realTicks(20), env.enabled(), env.state());
			helper.assertTrue("combat in the last 30 s".equals(WayBack.plan(cave.player, fought).gate()), "closed after combat");
		} finally {
			WayBack.forget(cave.player.getUUID());
		}
		helper.succeed();
	}

	/** Refused in a tunnel the player dug, and in a passage 2 wide. */
	@GameTest(maxTicks = 200)
	public void theWayBackIsRefusedInADugTunnelAndInAWidePassage(GameTestHelper helper) {
		Cave dug = new Cave(helper, 147, true, false);
		Cave wide = new Cave(helper, 148, false, true);
		try {
			WayBack.Plan inDug = WayBack.plan(dug.player, dug.env());
			helper.assertTrue(!inDug.fits() && inDug.refusals().keySet().stream().anyMatch(k -> k.startsWith("a dug tunnel")), "closed in a dug tunnel: "
					+ inDug.summary());
			WayBack.Plan inWide = WayBack.plan(wide.player, wide.env());
			helper.assertTrue(!inWide.fits() && inWide.refusals().keySet().stream().anyMatch(k -> k.startsWith("wider than 1")), "closed a wide passage: "
					+ inWide.summary());
		} finally {
			WayBack.forget(dug.player.getUUID());
			WayBack.forget(wide.player.getUUID());
		}
		helper.succeed();
	}

	/** The trail keeps a sample per block moved, starts again after a jump or in another dimension, and keeps at most its length. */
	@GameTest
	public void theTrailKeepsMovesAndStartsAgainAfterAJump(GameTestHelper helper) {
		UUID player = UUID.randomUUID();
		Sc13Config config = new Sc13Config();
		try {
			WayBack.add(player, Level.OVERWORLD, new BlockPos(0, 10, 0), 0, config);
			WayBack.add(player, Level.OVERWORLD, new BlockPos(0, 10, 0), 20, config);
			WayBack.add(player, Level.OVERWORLD, new BlockPos(3, 10, 0), 40, config);
			helper.assertTrue(WayBack.trail(player, Level.OVERWORLD).size() == 2, "standing still added samples");
			WayBack.add(player, Level.OVERWORLD, new BlockPos(30, 10, 0), 60, config);
			helper.assertTrue(WayBack.trail(player, Level.OVERWORLD).size() == 1, "a jump of 27 blocks kept the trail");
			WayBack.add(player, Level.NETHER, new BlockPos(31, 10, 0), 80, config);
			helper.assertTrue(WayBack.trail(player, Level.OVERWORLD).isEmpty() && WayBack.trail(player, Level.NETHER).size() == 1, "another dimension kept it");
			for (int i = 0; i < 200; i++) {
				WayBack.add(player, Level.NETHER, new BlockPos(31 + i, 10, 0), 100 + i, config);
			}
			helper.assertTrue(WayBack.trail(player, Level.NETHER).size() == config.trailLength, "kept " + WayBack.trail(player, Level.NETHER).size());
		} finally {
			WayBack.forget(player);
		}
		helper.succeed();
	}
}
