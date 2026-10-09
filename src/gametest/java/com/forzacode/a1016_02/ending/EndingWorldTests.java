package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.ending.EndingBeats.B;
import com.forzacode.a1016_02.ending.EndingTestSupport.Run;
import com.forzacode.a1016_02.lore.FragmentItems;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Ending B in a real house (the 24x20x24 {@code accident/yard}, empty air): the interior goes in one batch out of
 * view and the shell, signs, fragments and anything outside stay; existing mobs are moved to the doorway, frozen,
 * and let go when the player comes; nothing is spawned or dropped and the player is never touched. Also the cross
 * finder, fragments burned in lava, the saved data, and the "Missing first block" card.
 */
public class EndingWorldTests extends EndingBeatTests {
	static final String YARD = "a1016_02:accident/yard";

	/** A walled, roofed house with a north door at (11, 1, 8), furnished inside; its center is (11, 1, 11). */
	static final class House {
		final GameTestHelper helper;
		final ServerPlayer builder;

		House(GameTestHelper helper) {
			this.helper = helper;
			this.builder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			for (int x = 0; x < 24; x++) {
				for (int z = 0; z < 24; z++) {
					helper.setBlock(x, 0, z, Blocks.STONE);
				}
			}
			for (int x = 8; x <= 14; x++) {
				for (int z = 8; z <= 14; z++) {
					placed(x, 4, z, Blocks.OAK_PLANKS.defaultBlockState());
					if (x == 8 || x == 14 || z == 8 || z == 14) {
						for (int y = 1; y <= 3; y++) {
							if (!(x == 11 && z == 8 && y <= 2)) {
								placed(x, y, z, Blocks.COBBLESTONE.defaultBlockState());
							}
						}
					}
				}
			}
			BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
			helper.setBlock(11, 2, 8, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			placed(11, 1, 8, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		}

		BlockPos abs(int x, int y, int z) {
			return helper.absolutePos(new BlockPos(x, y, z));
		}

		/** A block "the player" placed (core's footprint). */
		void placed(int x, int y, int z, BlockState state) {
			helper.setBlock(x, y, z, state);
			Services.watch().onPlaced(builder, helper.getLevel(), abs(x, y, z), state);
		}

		void placed(int x, int y, int z, Block block) {
			placed(x, y, z, block.defaultBlockState());
		}

		Container container(int x, int y, int z) {
			return (Container) helper.getLevel().getBlockEntity(abs(x, y, z));
		}

		BlockState state(int x, int y, int z) {
			return helper.getLevel().getBlockState(abs(x, y, z));
		}
	}

	static long entities(GameTestHelper helper, Class<? extends Entity> type) {
		return helper.getLevel().getEntitiesOfClass(type, helper.getBounds().inflate(2)).size();
	}

	@GameTest(structure = YARD, maxTicks = 40)
	public void endingBEmptiesTheHouseAndTheShellStays(GameTestHelper helper) {
		House house = new House(helper);
		Run r = new Run(helper);
		r.cfg.houseRadius = 6;
		// Inside: a chest with things, a crafting table, a torch, a carpet, a bed.
		house.placed(9, 1, 9, Blocks.CHEST);
		house.container(9, 1, 9).setItem(0, new ItemStack(Items.COBBLESTONE, 12));
		house.placed(13, 1, 13, Blocks.CRAFTING_TABLE);
		house.placed(9, 1, 13, Blocks.TORCH);
		house.placed(12, 1, 11, Blocks.CARPET.white());
		BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		helper.setBlock(10, 1, 11, bed.setValue(BedBlock.PART, BedPart.HEAD));
		house.placed(9, 1, 11, bed.setValue(BedBlock.PART, BedPart.FOOT));
		// Kept: a sign (lore's), a chest holding a fragment, a fragment's own spot, and a torch outside.
		house.placed(13, 1, 9, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
		house.placed(10, 1, 13, Blocks.CHEST);
		house.container(10, 1, 13).setItem(3, FragmentItems.mark(new ItemStack(Items.WRITTEN_BOOK), "F10"));
		house.placed(12, 1, 13, Blocks.BARREL);
		r.state.setFragmentPlaced("F10", GlobalPos.of(helper.getLevel().dimension(), house.abs(12, 1, 13)));
		house.placed(5, 1, 5, Blocks.TORCH);

		List<BlockPos> inside = HouseScan.interior(helper.getLevel(), house.abs(11, 1, 11), r.cfg, Services.watch(),
				List.of(house.abs(12, 1, 13)));
		helper.assertTrue(inside.size() == 5, "the interior is not the 5 furnishings: " + inside);

		long items = entities(helper, ItemEntity.class);
		long mobs = entities(helper, Mob.class);
		r.commit(EndingPath.B);
		r.data.setHouse(GlobalPos.of(helper.getLevel().dimension(), house.abs(11, 1, 11)));
		r.data.setProgress(EndingPath.B, B.EMPTY_HOUSE.ordinal(), r.now);
		r.engine.bEmpty(r.ctx());

		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.FINISH_COPY, "the beat did not move on: " + r.data.log());
		for (int[] p : new int[][] {{9, 1, 9}, {13, 1, 13}, {9, 1, 13}, {12, 1, 11}, {9, 1, 11}, {10, 1, 11}}) {
			helper.assertTrue(house.state(p[0], p[1], p[2]).isAir(), "still in the house at " + p[0] + " " + p[1] + " " + p[2]);
		}
		helper.assertTrue(house.state(8, 2, 10).is(Blocks.COBBLESTONE) && house.state(11, 4, 11).is(Blocks.OAK_PLANKS)
				&& house.state(11, 1, 8).is(Blocks.OAK_DOOR), "the shell did not stay");
		helper.assertTrue(house.state(13, 1, 9).is(Blocks.OAK_SIGN), "the sign was taken");
		helper.assertTrue(house.state(10, 1, 13).is(Blocks.CHEST) && house.state(12, 1, 13).is(Blocks.BARREL), "a fragment's place was emptied");
		helper.assertTrue(house.state(5, 1, 5).is(Blocks.TORCH), "the torch outside was taken");
		helper.assertTrue(entities(helper, ItemEntity.class) == items && entities(helper, Mob.class) == mobs, "something dropped or spawned");
		BlockPos chest = house.abs(9, 1, 9);
		Optional<TraceLedger.Entry> entry = TraceService.ledger(r.server).entries().stream()
				.filter(e -> e.cause().equals(EndingEngine.CAUSE_HOUSE) && e.pos().pos().equals(chest)).findFirst();
		helper.assertTrue(entry.isPresent() && entry.get().kind() == TraceLedger.Kind.REMOVE && entry.get().blockEntity().isPresent(),
				"the chest's removal (with its things) is not in the ledger for Ending D");
		helper.succeed();
	}

	@GameTest(structure = YARD, maxTicks = 40)
	public void mobsWaitInTheDoorwayUntilThePlayerComes(GameTestHelper helper) {
		House house = new House(helper);
		Run r = new Run(helper);
		r.cfg.houseRadius = 6;
		r.cfg.bDoorwayMobs = 1;
		// Only this yard's husk: other tests' mobs nearby must never be taken.
		r.cfg.bDoorwayMobTypes = List.of("minecraft:husk");
		r.cfg.bDoorwayMobRadius = 10;
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(3, 1, 20));
		long mobs = entities(helper, Mob.class);
		r.player.snapTo(Vec3.atBottomCenterOf(house.abs(22, 1, 22)), 0, 0);
		EndingBeatTests.Untouched before = EndingBeatTests.Untouched.of(r);

		r.commit(EndingPath.B);
		r.data.setHouse(GlobalPos.of(helper.getLevel().dimension(), house.abs(11, 1, 11)));
		r.data.setProgress(EndingPath.B, B.DOORWAY.ordinal(), r.now);
		r.engine.bDoorway(r.ctx());

		BlockPos spot = house.abs(11, 1, 7);
		helper.assertTrue(r.data.waiters().size() == 1, "no mob waits in the doorway: " + r.data.log());
		helper.assertTrue(husk.blockPosition().equals(spot), "the husk is not outside the door: " + husk.blockPosition() + " vs " + spot);
		helper.assertTrue(Services.mobs().isTampered(husk), "the husk does not wait (not frozen)");
		helper.assertTrue(r.beat(B.class, EndingPath.B) == B.FINAL, "the doorway beat did not move on");
		helper.assertTrue(entities(helper, Mob.class) == mobs, "a mob was spawned");

		r.engine.tendWaiters(r.ctx());
		helper.assertTrue(Services.mobs().isTampered(husk) && r.data.waiters().size() == 1, "it stopped waiting while the player was away");
		before.check(helper, r);
		r.player.snapTo(Vec3.atBottomCenterOf(house.abs(11, 1, 4)), 0, 0);
		r.engine.tendWaiters(r.ctx());
		helper.assertFalse(Services.mobs().isTampered(husk), "it still waits with the player at the door");
		helper.assertTrue(r.data.waiters().isEmpty(), "the waiter was not let go");
		husk.discard();
		helper.succeed();
	}

	/** The entity's stare-down (FigureApi.STARED) is staring into the fog for Ending C; only the subject's counts. */
	@GameTest
	public void staringHimDownIsStaringIntoTheFog(GameTestHelper helper) {
		Run r = new Run(helper);
		helper.assertFalse(EndingWatch.onStared(r.player, r.data, r.now, p -> false), "another player's stare counted");
		helper.assertTrue(r.data.lastFogStareAt() == EndingState.NEVER, "another player's stare was recorded");
		helper.assertTrue(EndingWatch.onStared(r.player, r.data, r.now, p -> p == r.player) && r.data.lastFogStareAt() == r.now,
				"the subject's stare did not count");
		EndingFacts f = EndingTestSupport.facts(Stage.TELLING, true, r.now + GameClock.TICKS_PER_DAY, 5 * GameClock.TICKS_PER_DAY,
				5 * GameClock.TICKS_PER_DAY, 5 * GameClock.TICKS_PER_DAY, EndingState.NEVER, -1, r.data.lastFogStareAt(), 6, 0, 20, 0, 2, 0, false, 40, 8, 32);
		helper.assertTrue(EndingRules.cWhy(f, r.cfg).map(why -> why.contains("fog")).orElse(false), "C did not wait out the stare: " + EndingRules.cWhy(f, r.cfg));
		helper.succeed();
	}

	/** Ending A's sign finds the cross through accident's DeathMarker.lastCrossPos, only for that death. */
	@GameTest
	public void theCrossComesFromTheDeathMarker(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		com.forzacode.a1016_02.accident.AccidentData accident = com.forzacode.a1016_02.accident.AccidentData.get(server);
		Optional<GlobalPos> oldBase = accident.lastCross();
		Optional<GlobalPos> oldDeath = accident.lastCrossDeath();
		GlobalPos base = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 1)));
		GlobalPos death = GlobalPos.of(base.dimension(), base.pos().offset(2, 0, 3));
		try {
			accident.setLastCross(null, null);
			helper.assertTrue(EndingPorts.LIVE.findCross(server, death).isEmpty(), "a cross was found where none stands");
			accident.setLastCross(base, death);
			helper.assertTrue(Services.deaths().lastCrossPos(server).equals(Optional.of(base)), "DeathMarker.lastCrossPos does not give the newest cross");
			helper.assertTrue(EndingPorts.LIVE.findCross(server, death).equals(Optional.of(base)), "the live port did not use the death marker");
			helper.assertTrue(EndingPorts.LIVE.findCross(server, GlobalPos.of(base.dimension(), death.pos().offset(1, 0, 0))).isEmpty(),
					"another death's cross was taken for this one");
			helper.assertTrue(EndingPorts.LIVE.findCross(server, GlobalPos.of(Level.NETHER, death.pos())).isEmpty(), "a cross was found in another dimension");
		} finally {
			accident.setLastCross(oldBase.orElse(null), oldDeath.orElse(null));
		}
		helper.succeed();
	}

	@GameTest
	public void fragmentsThrownIntoLavaCountAndHeldOnesAreSeen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer thrower = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer other = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		EndingState data = new EndingState();
		// Not F06: a burned list has its own consequences in lore.
		ItemStack fragment = FragmentItems.mark(new ItemStack(Items.WRITTEN_BOOK), "F02");
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		helper.assertTrue(EndingWatch.onItemDestroyed(thrown(level, at, fragment, thrower), level.damageSources().lava(), data, p -> p == thrower),
				"a fragment thrown into lava did not count");
		helper.assertTrue(EndingWatch.onItemDestroyed(thrown(level, at, fragment, thrower), level.damageSources().inFire(), data, p -> p == thrower),
				"a fragment thrown into fire did not count");
		helper.assertFalse(EndingWatch.onItemDestroyed(thrown(level, at, fragment, thrower), level.damageSources().cactus(), data, p -> p == thrower),
				"a cactus counted");
		helper.assertFalse(EndingWatch.onItemDestroyed(thrown(level, at, new ItemStack(Items.WRITTEN_BOOK), thrower), level.damageSources().lava(),
				data, p -> p == thrower), "a plain book counted");
		helper.assertFalse(EndingWatch.onItemDestroyed(thrown(level, at, fragment, other), level.damageSources().lava(), data, p -> p == thrower),
				"someone else's throw counted");
		helper.assertTrue(data.fragmentsBurned() == 2 && data.burnedIds().equals(Set.of("F02")) && data.everHeld().equals(Set.of("F02")),
				"burned " + data.fragmentsBurned() + " " + data.burnedIds() + " held " + data.everHeld());
		// A shulker box thrown in burns the fragments in it too.
		helper.assertTrue(EndingWatch.onItemDestroyed(thrown(level, at, shulkerWith(fragment("F09")), thrower), level.damageSources().lava(), data,
				p -> p == thrower), "a shulker box with a fragment in it did not count");
		helper.assertTrue(data.burnedIds().contains("F09"), "the fragment in the shulker box did not burn");

		// The live path through the item mixin: the fragment burns (it is not the subject's, so nothing counts).
		ItemEntity live = thrown(level, at, fragment, thrower);
		live.hurtServer(level, level.damageSources().lava(), 10.0F);
		helper.assertTrue(live.isRemoved(), "the fragment did not burn");
		helper.succeed();
	}

	static ItemStack fragment(String id) {
		return FragmentItems.mark(new ItemStack(Items.WRITTEN_BOOK), id);
	}

	static ItemStack shulkerWith(ItemStack inside) {
		ItemStack box = new ItemStack(Items.SHULKER_BOX);
		box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(inside)));
		return box;
	}

	static ItemStack bundleWith(ItemStack inside) {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(inside))));
		return bundle;
	}

	/** Held anywhere: the inventory and nested shulker boxes and bundles, the ender chest, and containers at the base. */
	@GameTest(structure = YARD, maxTicks = 40)
	public void fragmentsAreFoundWhereverTheyAreKept(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		helper.assertTrue(FragmentHoldings.carried(player).isEmpty(), "an empty inventory holds a fragment");
		player.getInventory().setItem(5, fragment("F02"));
		helper.assertTrue(FragmentHoldings.carried(player).equals(Set.of("F02")), "a fragment in the inventory is not seen");
		player.getInventory().setItem(5, shulkerWith(bundleWith(fragment("F05"))));
		helper.assertTrue(FragmentHoldings.carried(player).equals(Set.of("F05")), "a fragment in a bundle in a shulker box is not seen");
		player.getInventory().setItem(5, ItemStack.EMPTY);
		player.getEnderChestInventory().setItem(0, bundleWith(fragment("F08")));
		helper.assertTrue(FragmentHoldings.carried(player).equals(Set.of("F08")), "a fragment in the ender chest is not seen");

		EndingState data = new EndingState();
		EndingWatch.sampleHeld(player, data);
		helper.assertTrue(data.everHeld().equals(Set.of("F08")) && data.unburned().equals(Set.of("F08")), "what they carry is not tracked as held");

		ServerLevel level = helper.getLevel();
		BlockPos home = helper.absolutePos(new BlockPos(12, 1, 12));
		helper.assertTrue(FragmentHoldings.stored(level, home, 10).isEmpty(), "an empty yard stores a fragment");
		helper.setBlock(14, 1, 12, Blocks.CHEST);
		((Container) level.getBlockEntity(helper.absolutePos(new BlockPos(14, 1, 12)))).setItem(4, shulkerWith(fragment("F11")));
		helper.setBlock(10, 1, 12, Blocks.LECTERN);
		LecternBlockEntity lectern = (LecternBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(10, 1, 12)));
		lectern.setBook(fragment("F12"));
		helper.assertTrue(FragmentHoldings.stored(level, home, 10).equals(Set.of("F11", "F12")),
				"fragments in a chest's shulker box and on a lectern at the base are not seen: " + FragmentHoldings.stored(level, home, 10));
		helper.assertTrue(FragmentHoldings.stored(level, home, 1).isEmpty(), "containers outside the radius count");
		helper.succeed();
	}

	/** Every read counts, not only the first: opening a fragment book or map, and looking at a fragment's sign. */
	@GameTest(structure = YARD, maxTicks = 40)
	public void everyReadOfAFragmentCounts(GameTestHelper helper) {
		helper.assertTrue(EndingReads.readable(fragment("F05")).equals(Optional.of("F05")), "a fragment book is not readable");
		helper.assertTrue(EndingReads.readable(new ItemStack(Items.WRITTEN_BOOK)).isEmpty(), "a plain book reads as a fragment");
		helper.assertTrue(EndingReads.readable(FragmentItems.mark(new ItemStack(Items.MUSIC_DISC_13), "F12")).isEmpty(), "a disc is read by opening it");
		EndingState data = new EndingState();
		EndingReads.noteRead(data, "F05", 1000);
		EndingReads.noteRead(data, "F05", 50000);
		helper.assertTrue(data.lastReadAt() == 50000, "reading it again did not count: " + data.lastReadAt());

		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		helper.setBlock(12, 0, 12, Blocks.STONE);
		helper.setBlock(12, 1, 12, Blocks.OAK_SIGN.defaultBlockState());
		BlockPos sign = helper.absolutePos(new BlockPos(12, 1, 12));
		player.snapTo(helper.absoluteVec(new Vec3(12.5, 1, 9.5)), 0, 20);
		helper.assertTrue(EndingReads.looksAt(player, sign, 4.5, 50), "looking at the sign up close is not reading it");
		player.snapTo(helper.absoluteVec(new Vec3(12.5, 1, 9.5)), 180, 0);
		helper.assertFalse(EndingReads.looksAt(player, sign, 4.5, 50), "turned away, it still counts as reading");
		player.snapTo(helper.absoluteVec(new Vec3(12.5, 1, 2.5)), 0, 0);
		helper.assertFalse(EndingReads.looksAt(player, sign, 4.5, 50), "from 10 blocks away, it still counts as reading");
		helper.succeed();
	}

	private static ItemEntity thrown(ServerLevel level, Vec3 at, ItemStack stack, ServerPlayer thrower) {
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack.copy());
		item.setThrower(thrower);
		return item;
	}

	@GameTest
	public void theEndingStateSurvivesASave(GameTestHelper helper) {
		EndingState d = new EndingState();
		GlobalPos home = GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, -20));
		d.setStopSeenAt(1000);
		d.recordTelling(2000, true);
		d.setHome(home);
		d.setHousePeak(40);
		d.addOwnBroken();
		d.addHeld("F05");
		d.addBurned("F02");
		d.setPath(EndingPath.B, 3000, "kept telling");
		d.setLastArmPlay(77000);
		d.setQuietUntil(123456);
		d.setFinalTrap(new EndingState.Trap("dark_corner", home));
		d.setProgress(EndingPath.B, B.DOORWAY.ordinal(), 4000);
		d.setProgress(EndingPath.A, EndingBeats.A.SIGN.ordinal(), 4000);
		d.setDeath(home, 3500);
		d.setHouse(home);
		d.setF20Placed(true);
		UUID mob = UUID.randomUUID();
		d.addWaiter(new EndingState.Waiter(mob, new BlockPos(1, 2, 3), new BlockPos(1, 2, 4), 4100));
		d.log("a line");
		d.setEnded(5000);
		Tag tag = EndingState.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
		EndingState back = EndingState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.path() == EndingPath.B && back.reason().equals("kept telling") && back.pathSince() == 3000, "the path was not kept");
		helper.assertTrue(back.progress(EndingPath.B) == B.DOORWAY.ordinal() && back.progress(EndingPath.A) == EndingBeats.A.SIGN.ordinal(),
				"the progress was not kept: " + back.allProgress());
		helper.assertTrue(back.stopSeenAt() == 1000 && back.lastNamedAt() == 2000 && back.tellingsSinceStop() == 1, "the watch was not kept");
		helper.assertTrue(back.home().equals(Optional.of(home)) && back.housePeak() == 40 && back.ownBroken() == 1 && back.fragmentsBurned() == 1
				&& back.everHeld().equals(Set.of("F02", "F05")) && back.unburned().equals(Set.of("F05")), "C's work was not kept");
		helper.assertTrue(back.lastArmPlay() == 77000 && back.quietUntil() == 123456
				&& back.finalTrap().equals(Optional.of(new EndingState.Trap("dark_corner", home))), "the arm memory was not kept");
		helper.assertTrue(back.deathPos().equals(Optional.of(home)) && back.house().equals(Optional.of(home)) && back.f20Placed(), "the beats were not kept");
		helper.assertTrue(back.waiters().size() == 1 && back.waiters().getFirst().mob().equals(mob) && back.waiters().getFirst().since() == 4100,
				"the waiters were not kept");
		helper.assertTrue(back.ended() && back.endedAt() == 5000 && back.log().contains("a line"), "the end was not kept");
		helper.succeed();
	}

	/** {@code /a1016 ending status} runs through the real dispatcher (read-only; path and step would change the world's run). */
	@GameTest
	public void theStatusCommandRuns(GameTestHelper helper) throws CommandSyntaxException {
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		int result = server.getCommands().getDispatcher().execute("a1016 ending status", source);
		helper.assertTrue(result == 1, "status returned " + result);
		List<String> lines = EndingCommands.status(EndingAbcInit.engine(), server);
		helper.assertTrue(lines.getFirst().startsWith("[a1016] ending: path "), "status: " + lines);
		helper.succeed();
	}

	@GameTest
	public void missingFirstBlockIsRegisteredAndLeavesItToF13(GameTestHelper helper) {
		Optional<EventCard> card = CardRegistry.get(MissingFirstBlockCard.ID);
		helper.assertTrue(card.isPresent(), "missing_first_block is not registered");
		helper.assertTrue(card.get().tier() == Tier.SIGNATURE && card.get().earliestStage() == Stage.REMOVAL, "wrong tier or stage");
		HerobrineState state = new HerobrineState();
		helper.assertTrue(MissingFirstBlockCard.takenByF13(state, helper.getLevel()), "no first block known, yet it is not taken");
		state.setFragmentPlaced("F13", GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BlockPos.ZERO)));
		helper.assertTrue(MissingFirstBlockCard.takenByF13(state, helper.getLevel()), "F13's cairn does not count as taking it");
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		FireResult result = card.get().fire(new FireContext(player, helper.getLevel(), false, true, RandomSource.create()));
		helper.assertTrue(result == FireResult.SKIPPED, "the card did something: " + result);
		helper.succeed();
	}
}
