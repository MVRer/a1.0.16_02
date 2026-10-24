package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-0a, the instruments (D-096; docs/SELF_CREATED_2.md, "SC2-0a core contracts: instruments"): {@code MobTamper.steer}
 * (a walk by the mob's own navigation, no goal or target selector while it lasts, a freeze wins, refused for brains,
 * leashes, riders, owners near, bosses, past the cap and with the layer off) and {@code hunt} and {@code calm} (a mob set
 * on one player: vanilla's anger and target, kept out to twice the follow range, bound to that player, a friend's hit
 * turns it on the friend, refused for tamed mobs, villagers, golems, creative or spectator targets and with the layer
 * off). Every scene runs in its own patch of the test server's End, far from every other test, its chunks held loaded.
 */
public class SelfCreated2CoreTests {
	private static final MobTamperImpl TAMPER = MobTamperImpl.INSTANCE;

	// --- steer ---

	/**
	 * A cow steered to a point 10 blocks off walks there by its own path within 200 ticks and stands there while steered,
	 * none of its goals running (a goal that would run every tick counts nothing); at the deadline the steer ends, the
	 * mob is let go and its goals run again.
	 */
	@GameTest(maxTicks = 500)
	public void aSteeredCowWalksToItsPointAndStopsAtTheDeadline(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		Cow[] cow = new Cow[1];
		Counting[] goal = new Counting[1];
		Vec3 to = p.vec(10.5, 0, 0.5);
		long[] steeredAt = new long[1];
		p.whenReady(() -> {
			p.box(-2, -3, 14, 3);
			cow[0] = p.spawn(EntityTypes.COW, 0.5, 0.5);
			goal[0] = new Counting();
			cow[0].getGoalSelector().addGoal(0, goal[0]);
		}).thenExecuteAfter(5, () -> {
			helper.assertTrue(goal[0].ticks > 0, "setup: the counting goal does not run");
			helper.assertTrue(TAMPER.steer(cow[0], to, 1.0, 240), "steer refused: " + TAMPER.steerRefusal(cow[0], SelfCreated.enabled(), 0));
			helper.assertTrue(TAMPER.isSteered(cow[0]) && TAMPER.isTampered(cow[0]) && Services.mobs().isSteered(cow[0]), "not steered");
			helper.assertTrue(noGoalRuns(cow[0]), "a goal still runs on a steered cow");
			goal[0].ticks = 0;
			steeredAt[0] = helper.getTick();
		}).thenWaitUntil(() -> {
			helper.assertTrue(noGoalRuns(cow[0]) && goal[0].ticks == 0, "a goal ran while steered (" + goal[0].ticks + " ticks)");
			helper.assertTrue(horizontal(cow[0].position(), to) <= 1.0, "not there yet: " + horizontal(cow[0].position(), to));
		}).thenExecute(() -> {
			long took = helper.getTick() - steeredAt[0];
			helper.assertTrue(took <= 200, "the walk of 10 blocks took " + took + " ticks");
		}).thenExecuteFor(40, () -> {
			helper.assertTrue(TAMPER.isSteered(cow[0]) && noGoalRuns(cow[0]) && goal[0].ticks == 0, "a goal ran while it stood there");
			helper.assertTrue(horizontal(cow[0].position(), to) <= 1.5, "it wandered off its point: " + horizontal(cow[0].position(), to));
		}).thenWaitUntil(() -> helper.assertTrue(helper.getTick() >= steeredAt[0] + 242, "the deadline")).thenExecute(() -> {
			helper.assertFalse(TAMPER.isSteered(cow[0]) || TAMPER.isTampered(cow[0]) || TAMPER.tracked().contains(cow[0]), "the steer outlived its deadline");
		}).thenWaitUntil(() -> helper.assertTrue(goal[0].ticks > 0, "its goals did not run again after the deadline")).thenExecute(p::done).thenSucceed();
	}

	/**
	 * A steered lamb on grass: its goals never run (no grazing, the grass stays), and hit by a player it does not panic:
	 * no goal starts, and it goes on to its point.
	 */
	@GameTest(maxTicks = 400)
	public void aSteeredSheepNeitherGrazesNorPanicsWhenHit(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		Sheep[] sheep = new Sheep[1];
		Vec3 to = p.vec(6.5, 0, 0.5);
		p.whenReady(() -> {
			p.box(-2, -3, 9, 3);
			for (int x = -1; x <= 8; x++) {
				for (int z = -2; z <= 2; z++) {
					p.set(p.at(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
			sheep[0] = p.spawn(EntityTypes.SHEEP, 0.5, 0.5);
			sheep[0].setBaby(true);
			helper.assertTrue(TAMPER.steer(sheep[0], to, 0.6, 300), "steer refused");
		}).thenExecuteFor(20, () -> helper.assertTrue(noGoalRuns(sheep[0]), "a goal runs on a steered lamb")).thenExecute(() -> {
			ServerPlayer player = p.prey(3.5, 3.5);
			helper.assertTrue(sheep[0].hurtServer(p.level, p.level.damageSources().playerAttack(player), 1.0F), "setup: the hit did not land");
		}).thenExecuteFor(80, () -> {
			helper.assertTrue(noGoalRuns(sheep[0]), "a goal (panic?) started on a steered lamb that was hit");
			helper.assertTrue(TAMPER.isSteered(sheep[0]), "the hit ended the steer");
		}).thenWaitUntil(() -> helper.assertTrue(horizontal(sheep[0].position(), to) <= 1.0, "the lamb did not go on to its point")).thenExecute(() -> {
			helper.assertTrue(noGoalRuns(sheep[0]), "a goal runs");
			for (int x = -1; x <= 8; x++) {
				for (int z = -2; z <= 2; z++) {
					helper.assertTrue(p.level.getBlockState(p.at(x, -1, z)).is(Blocks.GRASS_BLOCK), "the steered lamb grazed at " + x + " " + z);
				}
			}
			p.done();
		}).thenSucceed();
	}

	/**
	 * Steering is refused for a villager (a brain), a leashed mob, a rider and its mount, a tamed wolf with its owner
	 * within 48 (not one whose owner is far), a mob without AI, a boss, past the world's cap, a frozen or hunting mob,
	 * and with the layer off; a refusal changes nothing. The stub refuses everything.
	 */
	@GameTest(maxTicks = 200)
	public void steerIsRefusedForBrainsLeashesRidersOwnersBossesTheCapAndTheSwitchOff(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		p.whenReady(() -> {
			p.box(-6, -6, 6, 6);
			Vec3 to = p.vec(3.5, 0, 3.5);
			Villager villager = p.spawn(EntityTypes.VILLAGER, -4.5, -4.5);
			helper.assertFalse(TAMPER.steer(villager, to, 1.0, 100), "steered a villager");
			helper.assertTrue(String.valueOf(TAMPER.steerRefusal(villager, true, 0)).contains("brain"), "villager: " + TAMPER.steerRefusal(villager, true, 0));

			Cow leashed = p.spawn(EntityTypes.COW, -2.5, -4.5);
			p.set(p.at(-2, 0, -2), Blocks.OAK_FENCE.defaultBlockState());
			leashed.setLeashedTo(p.own(LeashFenceKnotEntity.getOrCreateKnot(p.level, p.at(-2, 0, -2))), true);
			helper.assertTrue(leashed.isLeashed() && !TAMPER.steer(leashed, to, 1.0, 100), "steered a leashed cow");

			Cow rider = p.spawn(EntityTypes.COW, 0.5, -4.5);
			Cow mount = p.spawn(EntityTypes.COW, 2.5, -4.5);
			helper.assertTrue(rider.startRiding(mount, true, false), "setup: the ride");
			helper.assertFalse(TAMPER.steer(rider, to, 1.0, 100) || TAMPER.steer(mount, to, 1.0, 100), "steered a rider or its mount");

			Wolf pet = p.spawn(EntityTypes.WOLF, 4.5, -4.5);
			ServerPlayer owner = p.prey(4.5, 4.5);
			pet.tame(owner);
			helper.assertFalse(TAMPER.steer(pet, to, 1.0, 100), "steered a pet with its owner 9 blocks off");
			owner.snapTo(p.vec(4.5, 0, 100.5), 0.0F, 0.0F);
			helper.assertTrue(TAMPER.steer(pet, to, 1.0, 100), "a pet whose owner is 105 blocks off: " + TAMPER.steerRefusal(pet, true, 0));
			TAMPER.release(pet);

			Cow still = p.spawn(EntityTypes.COW, -4.5, 0.5);
			still.setNoAi(true);
			helper.assertFalse(TAMPER.steer(still, to, 1.0, 100), "steered a mob without AI");

			WitherBoss boss = EntityTypes.WITHER.create(p.level, EntitySpawnReason.COMMAND);
			helper.assertTrue(boss != null && "a boss".equals(TAMPER.steerRefusal(boss, true, 0)), "a boss: " + (boss == null ? null : TAMPER.steerRefusal(boss, true, 0)));

			Cow cow = p.spawn(EntityTypes.COW, -2.5, 2.5);
			helper.assertTrue(TAMPER.steerRefusal(cow, true, MobTamperImpl.MAX_STEERED - 1) == null, "refused under the cap");
			helper.assertTrue(String.valueOf(TAMPER.steerRefusal(cow, true, MobTamperImpl.MAX_STEERED)).contains("48"), "the cap of 48: "
					+ TAMPER.steerRefusal(cow, true, MobTamperImpl.MAX_STEERED));
			helper.assertTrue(String.valueOf(TAMPER.steerRefusal(cow, false, 0)).contains("switched off"), "with the layer off");
			SelfCreated.Config sc = SelfCreated.config();
			sc.enabled = false;
			try {
				helper.assertFalse(TAMPER.steer(cow, to, 1.0, 100), "steered with the layer off");
			} finally {
				sc.enabled = true;
			}
			helper.assertFalse(TAMPER.steer(cow, to, 1.0, 0) || TAMPER.steer(cow, to, 0.0, 100) || TAMPER.steer(cow, to, Double.NaN, 100)
					|| TAMPER.steer(cow, null, 1.0, 100), "a bad request was taken");
			helper.assertFalse(TAMPER.isTampered(cow) || TAMPER.isTampered(villager) || TAMPER.isTampered(leashed) || TAMPER.isTampered(still),
					"a refused steer left state");

			ServerPlayer prey = p.prey(0.5, 4.5);
			Wolf hunter = p.spawn(EntityTypes.WOLF, 2.5, 2.5);
			helper.assertTrue(TAMPER.hunt(hunter, prey, 100), "setup: the hunt");
			helper.assertFalse(TAMPER.steer(hunter, to, 1.0, 100), "steered a hunting wolf");
			TAMPER.calm(hunter);

			MobTamper stub = new MobTamper.Stub();
			helper.assertFalse(stub.steer(cow, to, 1.0, 100) || stub.isSteered(cow) || stub.hunt(hunter, prey, 100) || stub.isHunting(hunter), "the stub took it");
			stub.calm(hunter);
			p.done();
		}).thenSucceed();
	}

	/**
	 * Steer and freeze on one mob do not fight: the freeze wins (the steer ends, the mob holds still, and a steer is
	 * refused while it is frozen), the mob is tampered throughout, and when the freeze ends nothing is left on it.
	 */
	@GameTest(maxTicks = 200)
	public void aFreezeWinsOverASteerAndTheyDoNotFight(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		Cow[] cow = new Cow[1];
		Vec3[] held = new Vec3[1];
		Vec3 to = p.vec(10.5, 0, 0.5);
		p.whenReady(() -> {
			p.box(-2, -3, 14, 3);
			cow[0] = p.spawn(EntityTypes.COW, 0.5, 0.5);
			helper.assertTrue(TAMPER.steer(cow[0], to, 1.0, 200), "steer refused");
		}).thenExecuteAfter(15, () -> {
			helper.assertTrue(TAMPER.isSteered(cow[0]) && TAMPER.isTampered(cow[0]), "not steered");
			helper.assertTrue(TAMPER.freeze(cow[0], 40), "freeze refused on a steered cow");
			helper.assertTrue(TAMPER.isFrozen(cow[0]) && !TAMPER.isSteered(cow[0]) && TAMPER.isTampered(cow[0]), "the freeze did not win");
			helper.assertFalse(TAMPER.steer(cow[0], to, 1.0, 200), "steered a frozen cow");
			helper.assertTrue(String.valueOf(TAMPER.steerRefusal(cow[0], true, 0)).contains("frozen"), "why: " + TAMPER.steerRefusal(cow[0], true, 0));
		}).thenExecuteAfter(3, () -> held[0] = cow[0].position()).thenExecuteFor(30, () -> {
			helper.assertTrue(TAMPER.isFrozen(cow[0]) && !TAMPER.isSteered(cow[0]) && TAMPER.isTampered(cow[0]), "the effects fought");
			helper.assertTrue(horizontal(cow[0].position(), held[0]) < 0.3, "a frozen cow walked " + horizontal(cow[0].position(), held[0]));
		}).thenExecuteAfter(15, () -> {
			helper.assertFalse(TAMPER.isFrozen(cow[0]) || TAMPER.isSteered(cow[0]) || TAMPER.isTampered(cow[0]), "something was left after the freeze");
			p.done();
		}).thenSucceed();
	}

	/** {@code release} ends a steer (the cow stops where it is) and a hunt (the wolf calmed, its follow range back). */
	@GameTest(maxTicks = 200)
	public void releaseEndsASteerAndAHunt(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		Cow[] cow = new Cow[1];
		Wolf[] wolf = new Wolf[1];
		ServerPlayer[] prey = new ServerPlayer[1];
		double[] range = new double[1];
		p.whenReady(() -> {
			p.box(-2, -6, 14, 6);
			cow[0] = p.spawn(EntityTypes.COW, 0.5, -4.5);
			wolf[0] = p.spawn(EntityTypes.WOLF, 0.5, 4.5);
			prey[0] = p.prey(8.5, 4.5);
			range[0] = wolf[0].getAttributeValue(Attributes.FOLLOW_RANGE);
			helper.assertTrue(TAMPER.steer(cow[0], p.vec(10.5, 0, -4.5), 1.0, 200), "steer refused");
			helper.assertTrue(TAMPER.hunt(wolf[0], prey[0], 200), "hunt refused: " + TAMPER.huntRefusal(wolf[0], prey[0], true));
		}).thenExecuteAfter(10, () -> {
			helper.assertTrue(wolf[0].isAngry() && wolf[0].getTarget() == prey[0] && Math.abs(wolf[0].getAttributeValue(Attributes.FOLLOW_RANGE) - 2 * range[0]) < 1.0E-6,
					"the hunt is not on: angry " + wolf[0].isAngry() + ", target " + wolf[0].getTarget() + ", range " + wolf[0].getAttributeValue(Attributes.FOLLOW_RANGE));
			TAMPER.release(cow[0]);
			TAMPER.release(wolf[0]);
			helper.assertFalse(TAMPER.isSteered(cow[0]) || TAMPER.isTampered(cow[0]) || TAMPER.tracked().contains(cow[0]), "release left the steer");
			helper.assertTrue(cow[0].getNavigation().isDone(), "a released cow kept its path");
			helper.assertFalse(TAMPER.isHunting(wolf[0]) || TAMPER.isTampered(wolf[0]) || TAMPER.tracked().contains(wolf[0]), "release left the hunt");
			helper.assertTrue(!wolf[0].isAngry() && wolf[0].getTarget() == null && wolf[0].getPersistentAngerTarget() == null, "a released wolf is still angry");
			helper.assertTrue(Math.abs(wolf[0].getAttributeValue(Attributes.FOLLOW_RANGE) - range[0]) < 1.0E-6, "its follow range stayed doubled");
			p.done();
		}).thenSucceed();
	}

	// --- hunt and calm ---

	/**
	 * A wild wolf set on a player is angry at them (vanilla's angry wolf), and attacks: the player is hurt within 100
	 * ticks. Calmed, it is not angry, has no target and hurts nobody.
	 */
	@GameTest(maxTicks = 300)
	public void aHuntSetsAWildWolfOnItsPlayerUntilCalmed(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		Wolf[] wolf = new Wolf[1];
		Prey[] prey = new Prey[1];
		long[] at = new long[1];
		p.whenReady(() -> {
			p.box(-2, -3, 9, 3);
			wolf[0] = p.spawn(EntityTypes.WOLF, 0.5, 0.5);
			prey[0] = p.prey(5.5, 0.5);
			helper.assertTrue(TAMPER.hunt(wolf[0], prey[0], 400), "hunt refused: " + TAMPER.huntRefusal(wolf[0], prey[0], true));
			helper.assertTrue(TAMPER.isHunting(wolf[0]) && TAMPER.isTampered(wolf[0]) && Services.mobs().isHunting(wolf[0]) && TAMPER.huntTargetOf(wolf[0]) == prey[0],
					"not hunting");
			helper.assertTrue(wolf[0].isAngry() && wolf[0].getTarget() == prey[0], "not angry at its player");
			at[0] = helper.getTick();
		}).thenWaitUntil(() -> helper.assertTrue(prey[0].getHealth() < prey[0].getMaxHealth(), "the wolf has not hurt its player yet")).thenExecute(() -> {
			long took = helper.getTick() - at[0];
			helper.assertTrue(took <= 100, "the first bite came after " + took + " ticks");
			helper.assertTrue(prey[0].bitBy == wolf[0], "something else hurt the player: " + prey[0].bitBy);
			TAMPER.calm(wolf[0]);
			helper.assertFalse(TAMPER.isHunting(wolf[0]) || TAMPER.isTampered(wolf[0]), "calm left the hunt");
			helper.assertTrue(!wolf[0].isAngry() && wolf[0].getTarget() == null && wolf[0].getPersistentAngerTarget() == null, "a calmed wolf is angry");
			prey[0].setHealth(prey[0].getMaxHealth());
		}).thenExecuteFor(60, () -> {
			helper.assertTrue(prey[0].getHealth() == prey[0].getMaxHealth(), "a calmed wolf bit");
			helper.assertTrue(!wolf[0].isAngry() && wolf[0].getTarget() == null, "the calmed wolf turned angry again");
		}).thenExecute(p::done).thenSucceed();
	}

	/**
	 * A hunt is refused for a tamed wolf, a villager, an iron golem, a cow (it cannot attack), a target in creative or
	 * spectator, dead, gone or in another level, and with the layer off; a refusal changes nothing.
	 */
	@GameTest(maxTicks = 200)
	public void huntIsRefusedForPetsVillagersGolemsAndPlayersItMayNotReach(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		p.whenReady(() -> {
			p.box(-6, -6, 6, 6);
			Prey prey = p.prey(0.5, 0.5);
			Wolf pet = p.spawn(EntityTypes.WOLF, -4.5, -4.5);
			pet.tame(p.prey(-4.5, -2.5));
			helper.assertFalse(TAMPER.hunt(pet, prey, 100), "a tamed wolf was set on a player");
			Villager villager = p.spawn(EntityTypes.VILLAGER, -2.5, -4.5);
			helper.assertFalse(TAMPER.hunt(villager, prey, 100), "a villager was set on a player");
			IronGolem golem = p.spawn(EntityTypes.IRON_GOLEM, 1.5, -4.5);
			helper.assertFalse(TAMPER.hunt(golem, prey, 100), "a golem was set on a player");
			helper.assertTrue(String.valueOf(TAMPER.huntRefusal(golem, prey, true)).contains("golem"), "golem: " + TAMPER.huntRefusal(golem, prey, true));
			Cow cow = p.spawn(EntityTypes.COW, 4.5, -4.5);
			helper.assertFalse(TAMPER.hunt(cow, prey, 100), "a cow was set on a player");

			Wolf wolf = p.spawn(EntityTypes.WOLF, 4.5, 4.5);
			prey.mode = GameType.CREATIVE;
			helper.assertFalse(TAMPER.hunt(wolf, prey, 100), "set on a player in creative");
			prey.mode = GameType.SPECTATOR;
			helper.assertFalse(TAMPER.hunt(wolf, prey, 100), "set on a spectator");
			prey.mode = GameType.SURVIVAL;
			ServerPlayer elsewhere = new Prey(helper.getLevel());
			helper.assertFalse(TAMPER.hunt(wolf, elsewhere, 100), "set on a player in another level");
			Prey dead = p.prey(2.5, 2.5);
			dead.setHealth(0.0F);
			helper.assertFalse(TAMPER.hunt(wolf, dead, 100), "set on a dead player");
			Prey gone = p.prey(2.5, 2.5);
			gone.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
			helper.assertFalse(TAMPER.hunt(wolf, gone, 100) || TAMPER.hunt(wolf, null, 100), "set on a player who left");
			helper.assertFalse(TAMPER.hunt(wolf, prey, 0), "a hunt of 0 ticks");
			helper.assertTrue(String.valueOf(TAMPER.huntRefusal(wolf, prey, false)).contains("switched off"), "with the layer off");
			SelfCreated.Config sc = SelfCreated.config();
			sc.enabled = false;
			try {
				helper.assertFalse(TAMPER.hunt(wolf, prey, 100), "set on a player with the layer off");
			} finally {
				sc.enabled = true;
			}
			for (Mob mob : List.<Mob>of(pet, villager, golem, cow, wolf)) {
				helper.assertFalse(TAMPER.isTampered(mob) || mob.getTarget() != null, "a refused hunt left something on " + mob);
			}
			helper.assertTrue(TAMPER.hunt(wolf, prey, 100), "the wild wolf on a survival player: " + TAMPER.huntRefusal(wolf, prey, true));
			TAMPER.calm(wolf);
			p.done();
		}).thenSucceed();
	}

	/**
	 * A monster set on a player keeps them as its target beyond its follow range, out to twice it: a husk the player hurt
	 * from 1.5 follow ranges away drops them (vanilla), the hunting one does not; past twice the range the hunting one
	 * lets go too, and takes them back once they are within it again.
	 */
	@GameTest(maxTicks = 300)
	public void aHuntOnAMonsterKeepsItsTargetOutToTwiceItsFollowRange(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		Husk[] husks = new Husk[2];
		Prey[] prey = new Prey[1];
		double[] range = new double[1];
		p.whenReady(() -> {
			p.box(-2, -4, 2, 4);
			husks[0] = p.spawn(EntityTypes.HUSK, 0.5, -2.5);
			husks[1] = p.spawn(EntityTypes.HUSK, 0.5, 2.5);
			// Each in a closed cell, so neither walks off after a target far away.
			for (int i = 0; i < 2; i++) {
				p.cell(0, i == 0 ? -3 : 2);
			}
			range[0] = husks[0].getAttributeValue(Attributes.FOLLOW_RANGE);
			prey[0] = p.prey(0.5 + 1.5 * range[0], 0.5);
		}).thenExecuteAfter(3, () -> {
			// Hit by the player (vanilla's own targeting: its hurt-by goal takes the player as its target, then drops them
			// past its follow range); a hit on a mob's first tick is one its hurt-by goal never sees.
			for (Husk husk : husks) {
				helper.assertTrue(husk.hurtServer(p.level, p.level.damageSources().playerAttack(prey[0]), 1.0F), "setup: the player's hit");
			}
			helper.assertTrue(TAMPER.hunt(husks[0], prey[0], 400), "hunt refused: " + TAMPER.huntRefusal(husks[0], prey[0], true));
		}).thenExecuteAfter(40, () -> {
			helper.assertTrue(husks[0].getTarget() == prey[0], "the hunting husk let go of its player at 1.5 follow ranges");
			helper.assertTrue(husks[1].getTarget() == null && husks[1].getLastHurtByMob() == prey[0], "setup: vanilla kept a target 1.5 follow ranges off");
			prey[0].snapTo(p.vec(0.5 + 2.4 * range[0], 0, 0.5), 0.0F, 0.0F);
		}).thenExecuteAfter(20, () -> {
			helper.assertTrue(husks[0].getTarget() == null, "the hunting husk kept its player 2.4 follow ranges off");
			helper.assertTrue(TAMPER.isHunting(husks[0]), "the hunt ended when the player went out of range");
			prey[0].snapTo(p.vec(0.5 + 1.5 * range[0], 0, 0.5), 0.0F, 0.0F);
		}).thenExecuteAfter(5, () -> {
			helper.assertTrue(husks[0].getTarget() == prey[0], "the hunting husk did not take its player back within range");
			TAMPER.calm(husks[0]);
			helper.assertTrue(husks[0].getTarget() == null && !TAMPER.isHunting(husks[0]), "calm left the husk on its player");
			helper.assertTrue(Math.abs(husks[0].getAttributeValue(Attributes.FOLLOW_RANGE) - range[0]) < 1.0E-6, "its follow range stayed doubled");
			p.done();
		}).thenSucceed();
	}

	/**
	 * Multiplayer: two wolves, each set on one of two players. When B leaves (the participation event), B's wolf is
	 * calmed and A's is not; when A goes too (A's player object removed, as a disconnect does), A's wolf is calmed within
	 * a tick, in view or not.
	 */
	@GameTest(maxTicks = 200)
	public void aHuntBoundToOneOfTwoPlayersCalmsWhenThatOneLeaves(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		Wolf[] wolves = new Wolf[2];
		Prey[] players = new Prey[2];
		MultiplayerCoreTests.Roster roster = new MultiplayerCoreTests.Roster();
		p.whenReady(() -> {
			p.box(-2, -6, 12, 6);
			wolves[0] = p.spawn(EntityTypes.WOLF, 0.5, -4.5);
			wolves[1] = p.spawn(EntityTypes.WOLF, 0.5, 4.5);
			players[0] = p.prey(10.5, -4.5);
			players[1] = p.prey(10.5, 4.5);
			roster.online.addAll(List.of(players));
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(TAMPER.hunt(wolves[i], players[i], 600), "hunt refused");
			}
		}).thenExecuteAfter(5, () -> {
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(TAMPER.isHunting(wolves[i]) && wolves[i].isAngry() && TAMPER.huntTargetOf(wolves[i]) == players[i], "setup: hunt " + i + " is not on");
			}
			roster.online.remove(players[1]);
			helper.assertTrue(TAMPER.releaseFor(p.level.getServer(), players[1].getUUID(), roster.watch) >= 1, "B left and nothing was calmed");
			helper.assertTrue(!TAMPER.isHunting(wolves[1]) && !wolves[1].isAngry() && wolves[1].getTarget() == null, "B left, yet B's wolf is still on B");
			helper.assertTrue(TAMPER.isHunting(wolves[0]) && wolves[0].isAngry() && TAMPER.huntTargetOf(wolves[0]) == players[0], "B leaving calmed A's wolf");
			players[0].remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
		}).thenExecuteAfter(2, () -> {
			helper.assertFalse(TAMPER.isHunting(wolves[0]) || TAMPER.isTampered(wolves[0]), "A is gone, yet A's wolf still hunts");
			helper.assertTrue(!wolves[0].isAngry() && wolves[0].getTarget() == null, "A's wolf was not calmed");
			p.done();
		}).thenSucceed();
	}

	/**
	 * Multiplayer: a friend who hits a wolf set on someone else gets that wolf (vanilla's anger): the hunt ends there,
	 * uncalmed, and the wolf turns on the friend.
	 */
	@GameTest(maxTicks = 200)
	public void aFriendWhoHitsAHuntingWolfGetsThatWolf(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		Wolf[] wolf = new Wolf[1];
		Prey[] target = new Prey[1];
		Prey[] friend = new Prey[1];
		p.whenReady(() -> {
			p.box(-8, -3, 8, 3);
			wolf[0] = p.spawn(EntityTypes.WOLF, 0.5, 0.5);
			target[0] = p.prey(6.5, 0.5);
			friend[0] = p.prey(-3.5, 0.5);
			helper.assertTrue(TAMPER.hunt(wolf[0], target[0], 600), "hunt refused");
		}).thenExecuteAfter(5, () -> {
			helper.assertTrue(wolf[0].getTarget() == target[0], "setup: the wolf is not on its player");
			helper.assertTrue(wolf[0].hurtServer(p.level, p.level.damageSources().playerAttack(friend[0]), 1.0F), "setup: the friend's hit");
		}).thenWaitUntil(() -> {
			helper.assertFalse(TAMPER.isHunting(wolf[0]), "the friend's hit did not end the hunt");
			helper.assertTrue(wolf[0].getTarget() == friend[0], "the wolf did not turn on the friend: " + wolf[0].getTarget());
		}).thenExecute(() -> {
			helper.assertTrue(wolf[0].isAngry() && wolf[0].getPersistentAngerTarget() != null && wolf[0].getPersistentAngerTarget().matches(friend[0]),
					"the wolf is not angry at the friend");
			TAMPER.calm(wolf[0]);
			helper.assertTrue(!wolf[0].isAngry() && wolf[0].getTarget() == null, "calm left the wolf angry at the friend");
			p.done();
		}).thenSucceed();
	}

	/**
	 * With the layer switched off a steer ends and a hunt is calmed within a tick (the tick's own step, here with the
	 * switch given as off); with it on, both go on.
	 */
	@GameTest(maxTicks = 200)
	public void switchingTheLayerOffEndsEverySteerAndHunt(GameTestHelper helper) {
		Patch p = new Patch(helper, 16);
		p.whenReady(() -> {
			p.box(-2, -6, 14, 6);
			Cow cow = p.spawn(EntityTypes.COW, 0.5, -4.5);
			Wolf wolf = p.spawn(EntityTypes.WOLF, 0.5, 4.5);
			ServerPlayer prey = p.prey(10.5, 4.5);
			helper.assertTrue(TAMPER.steer(cow, p.vec(10.5, 0, -4.5), 1.0, 600) && TAMPER.hunt(wolf, prey, 600), "setup");
			helper.assertFalse(TAMPER.expire(cow, true) || TAMPER.expire(wolf, true), "the layer on, yet they ended");
			helper.assertTrue(TAMPER.isSteered(cow) && TAMPER.isHunting(wolf), "the layer on, yet they ended");
			helper.assertTrue(TAMPER.expire(cow, false) && TAMPER.expire(wolf, false), "the layer off, yet they went on");
			helper.assertFalse(TAMPER.isSteered(cow) || TAMPER.isHunting(wolf) || TAMPER.isTampered(cow) || TAMPER.isTampered(wolf), "something was left");
			helper.assertTrue(!wolf.isAngry() && wolf.getTarget() == null, "the wolf was not calmed");
			p.done();
		}).thenSucceed();
	}

	// --- helpers ---

	/** No goal of the mob's goal selector runs. */
	static boolean noGoalRuns(Mob mob) {
		for (WrappedGoal goal : mob.getGoalSelector().getAvailableGoals()) {
			if (goal.isRunning()) {
				return false;
			}
		}
		return true;
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** A goal that would run every tick, holding no flag, counting its ticks: it shows whether the goals run at all. */
	static final class Counting extends Goal {
		int ticks;

		Counting() {
			setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ticks++;
		}
	}

	/**
	 * A player who lives in a level without joining (never on the player list, so never a participant anywhere but a
	 * test's own roster), whose game mode a test can switch, and who takes damage (a mock player's connection is missing,
	 * which vanilla's invulnerability check reads). Remembers what last hurt it.
	 */
	static final class Prey extends ServerPlayer {
		GameType mode = GameType.SURVIVAL;
		Entity bitBy;

		Prey(ServerLevel level) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player"), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode == null ? GameType.SURVIVAL : mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}

		@Override
		public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
			return isSpectator() || isCreative();
		}

		// Vanilla tells the client a fight began or ended, and where a hit came from; there is no client.
		@Override
		public void onEnterCombat() {
		}

		@Override
		public void indicateDamage(double xd, double zd) {
		}

		@Override
		public void onLeaveCombat() {
		}

		@Override
		public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
			boolean hurt = super.hurtServer(level, source, damage);
			if (hurt) {
				bitBy = source.getEntity();
			}
			return hurt;
		}
	}

	/**
	 * A patch of the test server's End at y 40, far from every other test and unique to this one, its chunks held loaded
	 * while it runs: coordinates relative to {@link #origin}, a stone floor, mobs it made, discarded at the end.
	 */
	static final class Patch {
		private static final int SPACING = 256;
		private static final AtomicInteger SLOTS = new AtomicInteger();
		private static final BlockPos BASE = new BlockPos(16 * ThreadLocalRandom.current().nextInt(-400_000, 400_000), 40,
				16 * ThreadLocalRandom.current().nextInt(-400_000, 400_000));

		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		private final int reach;
		private final List<Entity> made = new ArrayList<>();

		Patch(GameTestHelper helper, int reach) {
			this.helper = helper;
			this.level = helper.getLevel().getServer().getLevel(Level.END);
			this.origin = BASE.offset(SLOTS.getAndIncrement() * SPACING, 0, 0);
			this.reach = reach;
			force(true);
		}

		private void force(boolean on) {
			for (int cx = (origin.getX() - reach) >> 4; cx <= (origin.getX() + reach) >> 4; cx++) {
				for (int cz = (origin.getZ() - reach) >> 4; cz <= (origin.getZ() + reach) >> 4; cz++) {
					level.setChunkForced(cx, cz, on);
					if (on) {
						level.getChunk(cx, cz);
					}
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

		/**
		 * A sequence that runs {@code then} on the first tick the patch is ready: first a wait in real time (at most 30 s)
		 * until its chunks are entity-ticking, as SC2-3's patches wait (a loaded machine's chunk system can take longer
		 * than a test's ticks; SC2-H), then the entities' loading, in real time too ({@link FarPatches}, SC2-9: counted in
		 * ticks it still raced the entity storage under load).
		 */
		net.minecraft.gametest.framework.GameTestSequence whenReady(Runnable then) {
			return helper.startSequence().thenExecute(() -> {
				long until = net.minecraft.util.Util.getMillis() + 30_000L;
				level.getServer().managedBlock(() -> ticking() || net.minecraft.util.Util.getMillis() > until);
			}).thenWaitUntil(FarPatches.until(helper, this::ready, "the patch")).thenExecute(then);
		}

		/** Every chunk of the patch is entity-ticking by its tickets (its entities may still be loading). */
		boolean ticking() {
			for (int cx = (origin.getX() - reach) >> 4; cx <= (origin.getX() + reach) >> 4; cx++) {
				for (int cz = (origin.getZ() - reach) >> 4; cz <= (origin.getZ() + reach) >> 4; cz++) {
					if (!level.isPositionEntityTicking(new BlockPos((cx << 4) + 8, origin.getY(), (cz << 4) + 8))) {
						return false;
					}
				}
			}
			return true;
		}

		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}

		Vec3 vec(double x, double y, double z) {
			return Vec3.atLowerCornerOf(origin).add(x, y, z);
		}

		void set(BlockPos pos, BlockState state) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}

		/** A stone floor from (x1, z1) to (x2, z2), air 4 high over it, and a stone wall 2 high around it. */
		void box(int x1, int z1, int x2, int z2) {
			for (int x = x1 - 1; x <= x2 + 1; x++) {
				for (int z = z1 - 1; z <= z2 + 1; z++) {
					boolean edge = x < x1 || x > x2 || z < z1 || z > z2;
					set(at(x, -1, z), Blocks.STONE.defaultBlockState());
					for (int y = 0; y < 4; y++) {
						set(at(x, y, z), edge && y < 2 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
					}
				}
			}
		}

		/** Walls 3 high and a roof around the one cell at (x, z), so a mob there stays. */
		void cell(int x, int z) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int y = 0; y < 3; y++) {
						if (dx != 0 || dz != 0) {
							set(at(x + dx, y, z + dz), Blocks.STONE.defaultBlockState());
						}
					}
					set(at(x + dx, 3, z + dz), Blocks.STONE.defaultBlockState());
				}
			}
		}

		/** A mob of that type with its feet on the floor at (x, z), kept from despawning. */
		<T extends Mob> T spawn(EntityType<T> type, double x, double z) {
			T mob = type.create(level, EntitySpawnReason.COMMAND);
			helper.assertTrue(mob != null, "could not make a " + type);
			mob.snapTo(vec(x, 0, z), 0.0F, 0.0F);
			mob.setPersistenceRequired();
			helper.assertTrue(level.addFreshEntity(mob), "could not add a " + type);
			made.add(mob);
			return mob;
		}

		/** Discards {@code entity} with the patch's mobs at the end. */
		<T extends Entity> T own(T entity) {
			made.add(entity);
			return entity;
		}

		/** A player standing at (x, z) on the floor, in this level, never joined. */
		Prey prey(double x, double z) {
			Prey prey = new Prey(level);
			prey.snapTo(vec(x, 0, z), 0.0F, 0.0F);
			return prey;
		}

		/** Releases and discards the mobs it made and lets its chunks go. */
		void done() {
			for (Entity entity : made) {
				if (entity instanceof Mob mob) {
					TAMPER.release(mob);
				}
				entity.discard();
			}
			made.clear();
			force(false);
		}
	}
}
