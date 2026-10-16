package com.forzacode.a1016_02.atmosphere.card;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.mob.TamperedMob;
import com.forzacode.a1016_02.atmosphere.sc.Home;
import com.forzacode.a1016_02.atmosphere.sc.InStep;
import com.forzacode.a1016_02.atmosphere.sc.Sc05Config;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The layer's atmosphere part (docs/SELF_CREATED.md), half two: 05 (the house, the time away, the arm, the steps inside
 * and the container's close, what stops them, the animals held facing the spot) and 06 (the rules, the follower's
 * answers behind on the ground's own sound, its last step, what ends it, the fake, the step mixin). Each test steps its
 * own instances on its own clock within one tick. Registered in the game test mod; half one is
 * {@link SelfCreatedAtmosphereNightTests}.
 */
public class SelfCreatedAtmosphereTests extends SelfCreatedAtmosphereNightTests {
	/** A spectating friend there: no participant, but a listener the alone rule counts. */
	static ServerPlayer spectator(GameTestHelper helper, Vec3 relative) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR);
		move(helper, player, relative, 0.0F);
		return player;
	}

	// --- 05: the house ---

	/**
	 * A stone house over a stone floor: walls on x and z 7 and 15 (y 1 to 3), a roof at y 4, an oak door at (11, 1, 7)
	 * facing south, a chest at (13, 1, 13); 49 covered cells inside at y 1. The door and the chest are marked placed only
	 * with {@code placed}.
	 */
	static void house(GameTestHelper helper, ServerPlayer placer, Watched watched, boolean placed) {
		floor(helper, Blocks.STONE);
		ServerLevel level = helper.getLevel();
		for (int x = 7; x <= 15; x++) {
			for (int z = 7; z <= 15; z++) {
				helper.setBlock(x, 4, z, Blocks.STONE);
				boolean wall = x == 7 || x == 15 || z == 7 || z == 15;
				for (int y = 1; y <= 3; y++) {
					if (wall && !(x == 11 && z == 7 && y <= 2)) {
						helper.setBlock(x, y, z, Blocks.STONE);
					}
				}
			}
		}
		BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		level.setBlock(helper.absolutePos(new BlockPos(11, 1, 7)), lower, SET_FLAGS);
		level.setBlock(helper.absolutePos(new BlockPos(11, 2, 7)), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), SET_FLAGS);
		helper.setBlock(13, 1, 13, Blocks.CHEST);
		if (placed) {
			markPlaced(helper, placer, watched);
		}
	}

	static void markPlaced(GameTestHelper helper, ServerPlayer placer, Watched watched) {
		ServerLevel level = helper.getLevel();
		BlockPos door = helper.absolutePos(new BlockPos(11, 1, 7));
		BlockPos chest = helper.absolutePos(new BlockPos(13, 1, 13));
		watched.watch.onPlaced(placer, level, door, level.getBlockState(door));
		watched.watch.onPlaced(placer, level, chest, level.getBlockState(chest));
	}

	static void openDoor(GameTestHelper helper, boolean open) {
		ServerLevel level = helper.getLevel();
		for (int y = 1; y <= 2; y++) {
			BlockPos pos = helper.absolutePos(new BlockPos(11, y, 7));
			level.setBlock(pos, level.getBlockState(pos).setValue(DoorBlock.OPEN, open), SET_FLAGS);
		}
	}

	/** A coming-home instance over the test's own players, sounds, view and clock. */
	static ComingHome comingHome(Watched watched, Ears ears, List<ServerPlayer> everyone, List<Tasks.Episode> episodes, long[] clock, ServerPlayer... viewers) {
		ComingHome home = new ComingHome();
		home.watch = () -> watched.watch;
		home.sounds = ears;
		home.outOfView = seenBy(viewers);
		home.everyone = level -> List.copyOf(everyone);
		home.episodes = episodes::add;
		home.playTicks = (server, player) -> clock[0];
		return home;
	}

	/** The house is its covered floor and a door a player placed; the walk goes in from the door and away from it. */
	@GameTest(structure = YARD)
	public void aHouseIsCoveredFloorAndADoorAPlayerPlaced(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = at(helper, new Vec3(11.5, 1, 40.5), 180);
		Watched watched = new Watched(player);
		house(helper, player, watched, false);
		BlockPos base = helper.absolutePos(new BlockPos(11, 1, 11));
		Sc05Config cfg = Sc05Config.get();
		helper.assertTrue(Home.why(level, base, watched.watch, cfg).orElse("").contains("no door"), "a house without a door a player placed");
		markPlaced(helper, player, watched);
		Home.House house = Home.find(level, base, watched.watch, cfg).orElse(null);
		helper.assertTrue(house != null && house.door().equals(helper.absolutePos(new BlockPos(11, 1, 7))) && house.inward() == Direction.SOUTH,
				"not the house: " + house);
		helper.assertTrue(house.floor().size() == 49 && house.floor().contains(helper.absolutePos(new BlockPos(8, 1, 8)))
				&& !house.floor().contains(helper.absolutePos(new BlockPos(11, 1, 6))), "the covered floor: " + house.floor().size());
		Sc05Config strict = new Sc05Config();
		strict.minCoveredCells = 500;
		helper.assertTrue(Home.why(level, base, watched.watch, strict).orElse("").contains("covered cells"), "a house too small");
		Set<BlockPos> inside = new HashSet<>(house.floor());
		Vec3 door = Vec3.atCenterOf(house.door());
		for (int seed = 0; seed < 24; seed++) {
			List<BlockPos> walk = Home.walk(house, RandomSource.create(seed), 3, 5);
			helper.assertTrue(walk.size() >= 3 && walk.size() <= 5 && inside.containsAll(walk) && new HashSet<>(walk).size() == walk.size(), "the walk " + walk);
			helper.assertTrue(walk.getFirst().equals(helper.absolutePos(new BlockPos(11, 1, 9))) || walk.getFirst().equals(helper.absolutePos(new BlockPos(11, 1, 10))),
					"the walk does not start 2 or 3 in: " + walk);
			for (int i = 1; i < walk.size(); i++) {
				helper.assertTrue(away(walk.get(i), door) >= away(walk.get(i - 1), door) - 1.0E-9, "a step toward the door: " + walk);
			}
			helper.assertTrue(Home.container(level, house, walk.getLast(), watched.watch, cfg.houseRadius).equals(Optional.of(helper.absolutePos(new BlockPos(13, 1, 13)))),
					"not the chest");
		}
		helper.assertTrue(Home.entry(Blocks.OAK_DOOR.defaultBlockState()) && !Home.entry(Blocks.IRON_DOOR.defaultBlockState())
				&& !Home.entry(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)) && Home.entry(Blocks.SPRUCE_TRAPDOOR.defaultBlockState())
				&& !Home.entry(Blocks.IRON_TRAPDOOR.defaultBlockState()) && Home.entry(Blocks.BIRCH_FENCE_GATE.defaultBlockState()), "the entries a hand opens");
		helper.succeed();
	}

	private static double away(BlockPos cell, Vec3 door) {
		return Math.hypot(cell.getX() + 0.5 - door.x, cell.getZ() + 0.5 - door.z);
	}

	/** Both cards fit only after ten minutes of the player's play away from their base; one arm a player, which runs out. */
	@GameTest(structure = YARD)
	public void comingHomeFitsOnlyAfterTenMinutesAwayAndArmsOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = at(helper, new Vec3(11.5, 1, 70.5), 180);
		Watched watched = new Watched(player);
		house(helper, player, watched, true);
		BlockPos base = helper.absolutePos(new BlockPos(11, 1, 11));
		home(player, base);
		List<Mob> cows = List.of(helper.spawn(EntityTypes.COW, new BlockPos(2, 1, 2)), helper.spawn(EntityTypes.COW, new BlockPos(3, 1, 2)),
				helper.spawn(EntityTypes.COW, new BlockPos(2, 1, 3)));
		try {
			long[] clock = {5_000};
			ComingHome home = comingHome(watched, new Ears(player), List.of(player), new ArrayList<>(), clock, player);
			helper.assertTrue(home.refusal(player, ComingHome.Kind.STEPS).orElse("").startsWith("away 0.0"), "fits before going away");
			home.tickAway(server);
			clock[0] += 6_000;
			helper.assertTrue(home.refusal(player, ComingHome.Kind.STEPS).orElse("").startsWith("away 5.0 of 10.0"), "fits after five minutes");
			clock[0] += 6_000;
			helper.assertTrue(home.refusal(player, ComingHome.Kind.STEPS).isEmpty() && home.refusal(player, ComingHome.Kind.WAITING).isEmpty(),
					"not after ten minutes: " + home.refusal(player, ComingHome.Kind.STEPS) + " / " + home.refusal(player, ComingHome.Kind.WAITING));
			SomeoneHomeCard steps = new SomeoneHomeCard();
			steps.home = home;
			TheyWereWaitingCard waiting = new TheyWereWaitingCard();
			waiting.home = home;
			helper.assertTrue(steps.contextFits(player, level) && waiting.contextFits(player, level), "the cards do not fit");
			helper.assertTrue(steps.fire(new FireContext(player, level, false, false, RandomSource.create(1))) == FireResult.FIRED && home.armed(player.getUUID()),
					"the fire did not arm");
			helper.assertTrue(!steps.contextFits(player, level) && !waiting.contextFits(player, level)
					&& home.refusal(player, ComingHome.Kind.WAITING).orElse("").contains("armed"), "a second card while armed");
			helper.assertTrue(waiting.fire(new FireContext(player, level, false, true, RandomSource.create(1))) == FireResult.SKIPPED, "a second arm");
			// The arm runs out after an hour of the player's play.
			clock[0] += 72_000 + 1;
			home.tickArms(server);
			helper.assertFalse(home.armed(player.getUUID()), "the arm did not run out");
			// Home again: the time away starts over; leaving the game forgets it.
			move(helper, player, new Vec3(11.5, 1, 20.5), 180);
			home.tickAway(server);
			helper.assertTrue(home.awayTicks(server, player.getUUID()) == 0, "still away at home");
			move(helper, player, new Vec3(11.5, 1, 70.5), 180);
			home.tickAway(server);
			clock[0] += 20_000;
			home.onLeave(player.getUUID());
			helper.assertTrue(home.awayTicks(server, player.getUUID()) == 0, "the time away outlived the session");
			// A forced fire skips the time away.
			helper.assertTrue(waiting.fire(new FireContext(player, level, true, true, RandomSource.create(1))) == FireResult.FIRED && home.armed(player.getUUID()),
					"a forced fire did not arm");
		} finally {
			cows.forEach(Entity::discard);
		}
		helper.succeed();
	}

	/** Coming back to the door: the steps inside on the floor's own sound, moving away from the door, then the chest's own close. */
	@GameTest(structure = YARD)
	public void someoneHomeStepsInsideOnTheFloorsOwnSoundThenTheChestCloses(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = at(helper, new Vec3(11.5, 1, 40.5), 180);
		Watched watched = new Watched(player);
		house(helper, player, watched, true);
		BlockPos base = helper.absolutePos(new BlockPos(11, 1, 11));
		home(player, base);
		Ears ears = new Ears(player);
		List<Tasks.Episode> episodes = new ArrayList<>();
		ComingHome home = comingHome(watched, ears, List.of(player), episodes, new long[] {0}, player);
		helper.assertTrue(home.arm(player, ComingHome.Kind.STEPS, false, null), "not armed");
		ComingHome.Arm arm = home.armOf(player.getUUID());
		helper.assertFalse(home.watchSteps(player, base, arm), "it played from 33 blocks");
		move(helper, player, new Vec3(11.5, 1, 26.5), 180);
		helper.assertFalse(home.watchSteps(player, base, arm), "it played from 19 blocks");
		move(helper, player, new Vec3(11.5, 1, 22.5), 180);
		helper.assertTrue(home.watchSteps(player, base, arm), "it did not play at 15 blocks coming closer: " + arm.waiting);
		helper.assertTrue(episodes.size() == 1 && episodes.getFirst() instanceof ComingHome.Steps, "no steps");
		ComingHome.Steps steps = (ComingHome.Steps) episodes.getFirst();
		run(server, steps, ears, 200);
		helper.assertTrue(steps.ended.equals("done"), "the steps ended: " + steps.ended);
		int n = steps.walk.size();
		helper.assertTrue(n >= 3 && n <= 5 && ears.log.size() == n + 1, "not 3 to 5 steps and a close: " + ears.log);
		Vec3 door = Vec3.atCenterOf(helper.absolutePos(new BlockPos(11, 1, 7)));
		for (int i = 0; i < n; i++) {
			Heard step = ears.log.get(i);
			helper.assertTrue(step.tick() == 8L * i && step.sound() == SoundType.STONE.getStepSound() && step.source() == SoundSource.PLAYERS
					&& step.volume() == 1.6F && step.pos().equals(Vec3.atBottomCenterOf(steps.walk.get(i))) && step.hearers().contains(player.getUUID()),
					"step " + i + ": " + step);
			if (i > 0) {
				helper.assertTrue(step.pos().distanceTo(door) >= ears.log.get(i - 1).pos().distanceTo(door) - 0.75, "a step back toward the door: " + ears.log);
			}
		}
		Heard close = ears.log.getLast();
		long after = close.tick() - ears.log.get(n - 1).tick();
		helper.assertTrue(close.sound() == SoundEvents.CHEST_CLOSE && close.source() == SoundSource.BLOCKS && close.volume() == 1.6F && after >= 15 && after <= 30
				&& close.pos().equals(Vec3.atCenterOf(helper.absolutePos(new BlockPos(13, 1, 13)))), "not the chest's close: " + close);
		helper.succeed();
	}

	private static void run(MinecraftServer server, Tasks.Episode episode, Ears ears, int max) {
		for (ears.tick = 0; ears.tick < max; ears.tick++) {
			if (!episode.tick(server)) {
				return;
			}
		}
	}

	/** The steps never start or go on with anyone within 48, the door open, or a cell inside in view; the fake is one quieter step. */
	@GameTest(structure = YARD)
	public void someoneHomeStopsForAFriendTheDoorOrALookInsideAndItsFakeIsOneQuieterStep(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = at(helper, new Vec3(11.5, 1, 22.5), 180);
		ServerPlayer friend = spectator(helper, new Vec3(11.5, 1, 60.5));
		Watched watched = new Watched(player);
		house(helper, player, watched, true);
		BlockPos base = helper.absolutePos(new BlockPos(11, 1, 11));
		home(player, base);
		Home.House house = Home.find(level, base, watched.watch, Sc05Config.get()).orElseThrow();
		List<ServerPlayer> everyone = new ArrayList<>(List.of(player));
		Ears ears = new Ears(player);
		List<Tasks.Episode> episodes = new ArrayList<>();
		ComingHome home = comingHome(watched, ears, everyone, episodes, new long[] {0}, player);
		home.everyone = l -> List.copyOf(everyone);
		RandomSource random = RandomSource.create(9);

		everyone.add(friend);
		move(helper, friend, new Vec3(11.5, 1, 50.5), 180);
		helper.assertTrue(home.startSteps(player, house, false, random).orElse("").equals("not alone"), "a spectator 28 away did not stop it");
		everyone.remove(friend);
		openDoor(helper, true);
		helper.assertTrue(home.startSteps(player, house, false, random).orElse("").contains("open"), "it started with the door open");
		openDoor(helper, false);
		home.outOfView = (l, box) -> false;
		helper.assertTrue(home.startSteps(player, house, false, random).orElse("").contains("in view"), "it started in view");
		home.outOfView = seenBy(player);

		// Under way: a friend comes, the door opens, a look inside.
		String[] ends = {"not alone", "the door opened", "looked in"};
		for (String end : ends) {
			ears.log.clear();
			episodes.clear();
			helper.assertTrue(home.startSteps(player, house, false, random).isEmpty(), "it did not start before: " + end);
			ComingHome.Steps steps = (ComingHome.Steps) episodes.getFirst();
			ears.tick = 0;
			helper.assertTrue(steps.tick(server), "it ended at once");
			switch (end) {
				case "not alone" -> everyone.add(friend);
				case "the door opened" -> openDoor(helper, true);
				default -> home.outOfView = (l, box) -> false;
			}
			ears.tick = 1;
			helper.assertFalse(steps.tick(server), "it went on: " + end);
			helper.assertTrue(steps.ended.equals(end) && ears.log.size() == 1, "not stopped by " + end + ": " + steps.ended + " " + ears.log);
			everyone.remove(friend);
			openDoor(helper, false);
			home.outOfView = seenBy(player);
		}
		// The fake: one step at half the volume, no close.
		ears.log.clear();
		episodes.clear();
		helper.assertTrue(home.startSteps(player, house, true, random).isEmpty(), "the fake did not start");
		run(server, episodes.getFirst(), ears, 100);
		helper.assertTrue(ears.log.size() == 1 && ears.log.getFirst().volume() == 0.8F && ears.log.getFirst().sound() == SoundType.STONE.getStepSound(),
				"the fake: " + ears.log);
		helper.succeed();
	}

	/** Coming within 32 of the base: every animal and sitting pet nothing holds faces the spot, bound to the group, until you come to it, look at it, or time runs out. */
	@GameTest(structure = YARD)
	public void theyWereWaitingHoldsEveryAnimalFacingTheSpotUntilYouCome(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = at(helper, new Vec3(11.5, 1, 60.5), 180);
		Watched watched = new Watched(player);
		BlockPos base = helper.absolutePos(new BlockPos(11, 1, 11));
		home(player, base);
		Cow a = helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 4));
		Cow b = helper.spawn(EntityTypes.COW, new BlockPos(18, 1, 4));
		Cow c = helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 18));
		Cow held = helper.spawn(EntityTypes.COW, new BlockPos(18, 1, 18));
		Wolf sitting = helper.spawn(EntityTypes.WOLF, new BlockPos(18, 1, 11));
		Wolf following = helper.spawn(EntityTypes.WOLF, new BlockPos(11, 1, 19));
		List<Mob> mobs = List.of(a, b, c, held, sitting, following);
		try {
			for (Wolf wolf : List.of(sitting, following)) {
				wolf.setTame(true, false);
				wolf.setOwnerReference(EntityReference.of(player.getUUID()));
			}
			sitting.setOrderedToSit(true);
			MobTamperImpl tamper = MobTamperImpl.INSTANCE;
			tamper.freeze(held, 2_000);
			List<Tasks.Episode> episodes = new ArrayList<>();
			ComingHome home = comingHome(watched, new Ears(player), List.of(player), episodes, new long[] {0}, player);
			helper.assertTrue(home.arm(player, ComingHome.Kind.WAITING, false, null), "not armed");
			ComingHome.Arm arm = home.armOf(player.getUUID());
			helper.assertFalse(home.watchWaiting(player, base, arm), "it played from 49 blocks");
			move(helper, player, new Vec3(11.5, 1, 40.5), 180);
			helper.assertTrue(home.watchWaiting(player, base, arm) && episodes.size() == 1, "it did not play at 29: " + arm.waiting);
			ComingHome.Waiting waiting = (ComingHome.Waiting) episodes.getFirst();
			Vec3 spot = Vec3.atCenterOf(base);
			helper.assertTrue(new HashSet<Animal>(waiting.animals).equals(Set.of(a, b, c, sitting)), "not every free animal and sitting pet: " + waiting.animals);
			for (Animal animal : waiting.animals) {
				helper.assertTrue(tamper.isFrozen(animal) && tamper.isFacing(animal) && spot.equals(((TamperedMob) animal).a1016_02$tamper().faceTarget())
						&& tamper.ownersOf(animal).equals(List.of(player.getUUID())), "not held facing the spot for the player: " + animal);
			}
			for (int i = 0; i < 10; i++) {
				helper.assertTrue(waiting.tick(server), "it ended early");
			}
			move(helper, player, new Vec3(11.5, 1, 13.5), 180);
			helper.assertFalse(waiting.tick(server), "not let go at the spot");
			helper.assertTrue(waiting.ended.equals("came to the spot") && waiting.animals.stream().noneMatch(tamper::isTampered), "not let go: " + waiting.ended);

			// Looking at the spot for two seconds lets them go; looking away, the time does.
			move(helper, player, new Vec3(11.5, 1, 30.5), 180);
			player.setXRot(3.0F);
			episodes.clear();
			helper.assertTrue(home.startWaiting(player, base, false).isEmpty(), "it did not start again");
			ComingHome.Waiting looked = (ComingHome.Waiting) episodes.getFirst();
			int ticks = 0;
			while (looked.tick(server) && ticks < 400) {
				ticks++;
			}
			helper.assertTrue(looked.ended.equals("looked at the spot") && ticks == 39, "a look of two seconds: " + looked.ended + " after " + ticks);
			move(helper, player, new Vec3(11.5, 1, 30.5), 0);
			episodes.clear();
			helper.assertTrue(home.startWaiting(player, base, false).isEmpty(), "it did not start a third time");
			ComingHome.Waiting timed = (ComingHome.Waiting) episodes.getFirst();
			ticks = 0;
			while (timed.tick(server) && ticks < 400) {
				ticks++;
			}
			helper.assertTrue(timed.ended.equals("time") && ticks == 240, "the twelve seconds: " + timed.ended + " after " + ticks);

			// The fake: they face the player, wherever the player goes.
			episodes.clear();
			helper.assertTrue(home.startWaiting(player, base, true).isEmpty(), "the fake did not start");
			ComingHome.Waiting fake = (ComingHome.Waiting) episodes.getFirst();
			helper.assertTrue(player.getEyePosition().equals(((TamperedMob) a).a1016_02$tamper().faceTarget()), "the fake does not face the player");
			move(helper, player, new Vec3(20.5, 1, 30.5), 0);
			fake.tick(server);
			helper.assertTrue(player.getEyePosition().equals(((TamperedMob) a).a1016_02$tamper().faceTarget()), "the fake does not follow the player");
			fake.stop(server);
			helper.assertTrue(waiting.animals.stream().noneMatch(tamper::isTampered), "the fake's stop did not let go");
			// The switch's revert lets the held animals go and ends the episode.
			episodes.clear();
			helper.assertTrue(home.startWaiting(player, base, false).isEmpty(), "it did not start before the revert");
			ComingHome.Waiting reverted = (ComingHome.Waiting) episodes.getFirst();
			home.clear(server);
			helper.assertTrue(reverted.animals.stream().noneMatch(tamper::isTampered) && !reverted.tick(server), "the revert left them held");
			// Fewer than three free animals: nothing.
			tamper.freeze(a, 2_000);
			tamper.freeze(b, 2_000);
			episodes.clear();
			helper.assertTrue(home.startWaiting(player, base, false).orElse("").contains("animals") && episodes.isEmpty(), "two animals held");
		} finally {
			mobs.forEach(m -> {
				MobTamperImpl.INSTANCE.release(m);
				m.discard();
			});
		}
		helper.succeed();
	}

	// --- 06 ---

	static InStep steps(Watched watched, Ears ears, List<ServerPlayer> listeners, long[] clock) {
		InStep steps = new InStep(() -> watched.watch, ears, level -> List.copyOf(listeners));
		steps.ticks = server -> clock[0];
		steps.skyLight = player -> 15;
		steps.timeOfDay = player -> 18_000L;
		steps.fog = player -> false;
		return steps;
	}

	/** The rules: the surface, night or fog, on foot, walking, alone, once a night, one at a time. */
	@GameTest(structure = YARD)
	public void inStepFitsOnlyAloneWalkingOnTheSurfaceAtNightOncePerNight(GameTestHelper helper) {
		ServerPlayer player = at(helper, new Vec3(4.5, 1, 4.5), 0);
		ServerPlayer friend = spectator(helper, new Vec3(4.5, 1, 30.5));
		Watched watched = new Watched(player);
		List<ServerPlayer> listeners = new ArrayList<>(List.of(player));
		long[] clock = {0};
		InStep steps = steps(watched, new Ears(player), listeners, clock);
		steps.sample(player, 0);
		move(helper, player, new Vec3(4.5, 1, 7.5), 0);
		clock[0] = 60;
		helper.assertTrue(steps.refusal(player, 60, false, false).isEmpty(), "it does not fit: " + steps.refusal(player, 60, false, false));
		steps.skyLight = p -> 9;
		helper.assertTrue(steps.refusal(player, 60, false, false).orElse("").contains("surface"), "underground");
		steps.skyLight = p -> 15;
		steps.timeOfDay = p -> 6_000L;
		helper.assertTrue(steps.refusal(player, 60, false, false).orElse("").contains("night"), "daylight without fog");
		helper.assertTrue(steps.refusal(player, 60, true, false).isEmpty(), "anytime");
		steps.fog = p -> true;
		helper.assertTrue(steps.refusal(player, 60, false, false).isEmpty(), "daylight in fog");
		steps.fog = p -> false;
		steps.timeOfDay = p -> 18_000L;
		player.setOnGround(false);
		helper.assertTrue(steps.refusal(player, 60, false, false).orElse("").contains("foot"), "off the ground");
		player.setOnGround(true);
		listeners.add(friend);
		helper.assertTrue(steps.refusal(player, 60, false, false).orElse("").contains("alone"), "a spectator 23 away");
		listeners.remove(friend);
		InStep still = steps(watched, new Ears(player), listeners, clock);
		still.sample(player, 0);
		helper.assertTrue(still.refusal(player, 60, false, false).orElse("").contains("walking"), "standing still");
		helper.assertTrue(steps.start(player, false, RandomSource.create(1)) && steps.refusal(player, 60, false, false).orElse("").contains("running"), "one at a time");
		steps.clear();
		move(helper, player, new Vec3(4.5, 1, 4.5), 0);
		steps.sample(player, 0);
		move(helper, player, new Vec3(4.5, 1, 7.5), 0);
		helper.assertTrue(steps.refusal(player, 60, false, false).orElse("").contains("tonight") && steps.refusal(player, 60, false, true).isEmpty(), "once a night: "
				+ steps.refusal(player, 60, false, false) + " / " + steps.refusal(player, 60, false, true));
		helper.assertTrue(InStepCard.ID.equals("sc_06"), "the card's id");
		helper.succeed();
	}

	/** One walk: the player's position at tick t, and their own steps every six ticks until {@code stopAt}. */
	private static void walk(GameTestHelper helper, ServerPlayer player, InStep steps, Ears ears, long[] clock, int stopAt, int max, List<Long> own,
			java.util.function.IntConsumer each) {
		MinecraftServer server = helper.getLevel().getServer();
		for (int t = 0; t <= max; t++) {
			clock[0] = t;
			ears.tick = t;
			if (t < stopAt) {
				move(helper, player, new Vec3(12.5, 1, 4.5 + 0.1 * t), player.getYRot());
				if (t % 6 == 0) {
					steps.step(player);
					own.add((long) t);
				}
			}
			each.accept(t);
			steps.tick(server);
			if (!steps.running(player.getUUID())) {
				return;
			}
		}
	}

	/** After three seconds more of walking, each own step is answered four to six ticks late, six to nine behind, on the ground's own sound. */
	@GameTest(structure = YARD, maxTicks = 100)
	public void inStepAnswersEachStepLateBehindOnTheGroundsOwnSound(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		ServerPlayer player = at(helper, new Vec3(12.5, 1, 4.5), 0);
		Watched watched = new Watched(player);
		Ears ears = new Ears(player);
		long[] clock = {0};
		InStep steps = steps(watched, ears, List.of(player), clock);
		helper.assertTrue(steps.start(player, false, RandomSource.create(5)), "no episode");
		List<Long> own = new ArrayList<>();
		walk(helper, player, steps, ears, clock, 200, 400, own, t -> {
		});
		helper.assertTrue(steps.ended(player.getUUID()).equals("done"), "it ended: " + steps.ended(player.getUUID()));
		int n = ears.log.size();
		helper.assertTrue(n >= 6 && n <= 14, "not 6 to 14 answers: " + n);
		Vec3 origin = helper.absoluteVec(Vec3.ZERO);
		for (Heard answer : ears.log) {
			long tick = answer.tick();
			helper.assertTrue(tick >= 63 && own.stream().anyMatch(o -> tick - o >= 4 && tick - o <= 6), "not 4 to 6 ticks after an own step: " + tick);
			Vec3 rel = answer.pos().subtract(origin);
			double playerZ = 4.5 + 0.1 * tick;
			helper.assertTrue(Math.abs(rel.x - 12.5) < 1.0E-6 && Math.abs(rel.y - 1.0) < 1.0E-6 && playerZ - rel.z >= 6.0 - 1.0E-6 && playerZ - rel.z <= 9.0 + 1.0E-6,
					"not behind on the ground: " + rel + " (player at " + playerZ + ")");
			helper.assertTrue(answer.sound() == SoundType.STONE.getStepSound() && answer.source() == SoundSource.PLAYERS && answer.volume() == 1.2F
					&& answer.hearers().contains(player.getUUID()), "not the ground's own step: " + answer);
		}
		helper.succeed();
	}

	/** Stop: one more step, then the end. A look back, anyone within 48, or leaving the ground ends it at once. */
	@GameTest(structure = YARD, maxTicks = 100)
	public void inStepTakesOneMoreStepWhenYouStopAndEndsOnALookBackAFriendOrLeavingTheGround(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		ServerPlayer player = at(helper, new Vec3(12.5, 1, 4.5), 0);
		ServerPlayer friend = spectator(helper, new Vec3(12.5, 1, 40.5));
		Watched watched = new Watched(player);
		long[] clock = {0};
		// Stop at 90: the last own step at 84, stopped at 95, one more four to six ticks later.
		Ears ears = new Ears(player);
		InStep steps = steps(watched, ears, List.of(player), clock);
		steps.start(player, false, RandomSource.create(7));
		List<Long> own = new ArrayList<>();
		walk(helper, player, steps, ears, clock, 90, 300, own, t -> {
		});
		long last = ears.log.getLast().tick();
		helper.assertTrue(steps.ended(player.getUUID()).equals("done") && last >= 99 && last <= 101 && ears.log.size() >= 5, "the step after the stop: " + ears.log);

		String[] ends = {"looked back", "not alone", "left the ground"};
		for (String end : ends) {
			Ears heard = new Ears(player);
			List<ServerPlayer> listeners = new ArrayList<>(List.of(player));
			InStep episode = steps(watched, heard, listeners, clock);
			move(helper, player, new Vec3(12.5, 1, 4.5), 0);
			player.setOnGround(true);
			episode.start(player, false, RandomSource.create(7));
			walk(helper, player, episode, heard, clock, 300, 300, new ArrayList<>(), t -> {
				if (t == 80) {
					switch (end) {
						case "looked back" -> player.setYRot(180.0F);
						case "not alone" -> listeners.add(friend);
						default -> player.setOnGround(false);
					}
					player.setYHeadRot(player.getYRot());
				}
			});
			helper.assertTrue(episode.ended(player.getUUID()).equals(end) && heard.log.stream().allMatch(h -> h.tick() <= 80), "not ended by " + end + ": "
					+ episode.ended(player.getUUID()) + " " + heard.log);
			player.setYRot(0.0F);
			player.setYHeadRot(0.0F);
		}
		helper.succeed();
	}

	/** The fake answers nothing: the player's own step once more at their feet, a beat after they stop. */
	@GameTest(structure = YARD, maxTicks = 100)
	public void inStepsFakeIsYourOwnStepOnceAfterYouStop(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		ServerPlayer player = at(helper, new Vec3(12.5, 1, 4.5), 0);
		Watched watched = new Watched(player);
		Ears ears = new Ears(player);
		long[] clock = {0};
		InStep steps = steps(watched, ears, List.of(player), clock);
		steps.start(player, true, RandomSource.create(2));
		walk(helper, player, steps, ears, clock, 90, 300, new ArrayList<>(), t -> {
		});
		helper.assertTrue(steps.ended(player.getUUID()).equals("done") && ears.log.size() == 1, "the fake: " + ears.log);
		Heard own = ears.log.getFirst();
		helper.assertTrue(own.tick() >= 99 && own.tick() <= 101 && own.pos().distanceTo(player.position()) < 1.0E-6
				&& own.volume() == SoundType.STONE.getVolume() * 0.15F && own.sound() == SoundType.STONE.getStepSound(), "not the player's own step late: " + own);
		helper.succeed();
	}

	/** A player's own step on the server reaches the live episodes through the step mixin (vanilla's walkingStepSound). */
	@GameTest(structure = YARD)
	public void aPlayersOwnStepReachesTheEpisodeThroughTheStepMixin(GameTestHelper helper) throws ReflectiveOperationException {
		floor(helper, Blocks.STONE);
		ServerPlayer player = at(helper, new Vec3(5.5, 1, 5.5), 0);
		InStep live = InStep.LIVE;
		UUID uuid = player.getUUID();
		try {
			helper.assertTrue(live.start(player, false, RandomSource.create(1)) && live.lastStep(uuid) == -1, "no live episode");
			Method walkingStepSound = Entity.class.getDeclaredMethod("walkingStepSound", BlockPos.class, BlockState.class);
			walkingStepSound.setAccessible(true);
			walkingStepSound.invoke(player, helper.absolutePos(new BlockPos(5, 0, 5)), Blocks.STONE.defaultBlockState());
			helper.assertTrue(live.lastStep(uuid) == helper.getLevel().getServer().getTickCount(), "the step did not reach the episode");
			// Sneaking on the ground has no movement emission in vanilla 26.3, so no step reaches the hook (the follower is silent too).
			Method emission = net.minecraft.world.entity.player.Player.class.getDeclaredMethod("getMovementEmission");
			emission.setAccessible(true);
			Object walking = emission.invoke(player);
			player.setShiftKeyDown(true);
			Object sneaking = emission.invoke(player);
			player.setShiftKeyDown(false);
			Method sounds = walking.getClass().getMethod("emitsSounds");
			sounds.setAccessible(true);
			helper.assertTrue((Boolean) sounds.invoke(walking) && !(Boolean) sounds.invoke(sneaking), "sneaking makes a step: " + walking + " / " + sneaking);
		} finally {
			live.onLeave(uuid);
		}
		helper.succeed();
	}

	/** The pure rules: the look back, the spot behind, the night's key, the fog, the surface. */
	@GameTest
	public void inStepsPureRules(GameTestHelper helper) {
		Vec3 behind = new Vec3(0, 0, -1);
		helper.assertTrue(InStep.lookingBack(new Vec3(0, -0.3, -2), behind, 70) && InStep.lookingBack(new Vec3(Math.sin(Math.toRadians(69)), 0, -Math.cos(Math.toRadians(69))), behind, 70)
				&& !InStep.lookingBack(new Vec3(Math.sin(Math.toRadians(71)), 0, -Math.cos(Math.toRadians(71))), behind, 70)
				&& !InStep.lookingBack(new Vec3(1, 0, 0), behind, 70), "the look back's 70 degrees");
		helper.assertTrue(InStep.behind(new Vec3(10, 64, 10), new Vec3(0, 0, 2), 7).equals(new Vec3(10, 64, 3)), "the spot behind");
		helper.assertTrue(InStep.nightKey(12_000) == 0 && InStep.nightKey(11_999) == -1 && InStep.nightKey(35_999) == 0 && InStep.nightKey(36_000) == 1,
				"the night's key");
		helper.assertTrue(InStep.night(13_000) && InStep.night(22_999) && !InStep.night(23_000) && !InStep.night(12_999), "the night");
		FogLimits.Shape shape = FogLimits.shape();
		helper.assertTrue(InStep.fogOn(0.6F, 13_000, Optional.empty(), shape) && !InStep.fogOn(0.6F, 6_000, Optional.empty(), shape)
				&& !InStep.fogOn(0.0F, 13_000, Optional.empty(), shape), "the dusk fog");
		FogSpell spell = new FogSpell(0, 20_000, 0.6F, false);
		helper.assertTrue(InStep.fogOn(0.0F, 6_000, Optional.of(spell), shape) && !InStep.fogOn(0.0F, 30_000, Optional.of(spell), shape), "a fog spell that counts");
		helper.assertTrue(InStep.surface(10) && !InStep.surface(9), "the surface");
		helper.succeed();
	}
}
