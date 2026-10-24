package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.mob.TamperState;
import com.forzacode.a1016_02.atmosphere.mob.TamperedMob;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Cluster;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Ring;
import com.forzacode.a1016_02.atmosphere.sc2.s21.RingCard;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Rings;
import com.forzacode.a1016_02.atmosphere.sc2.s21.S21;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Sc21Config;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Sc2xData;
import com.forzacode.a1016_02.atmosphere.sc2.s22.Line;
import com.forzacode.a1016_02.atmosphere.sc2.s22.ProcessionCard;
import com.forzacode.a1016_02.atmosphere.sc2.s22.Processions;
import com.forzacode.a1016_02.atmosphere.sc2.s22.Sc22Config;
import com.forzacode.a1016_02.atmosphere.sc2.s23.HeldBreath;
import com.forzacode.a1016_02.atmosphere.sc2.s23.HeldBreathCard;
import com.forzacode.a1016_02.atmosphere.sc2.s23.Sc23Config;
import com.forzacode.a1016_02.atmosphere.sc2.s24.Followed;
import com.forzacode.a1016_02.atmosphere.sc2.s24.FollowerCard;
import com.forzacode.a1016_02.atmosphere.sc2.s24.Follows;
import com.forzacode.a1016_02.atmosphere.sc2.s24.Sc24Config;
import com.forzacode.a1016_02.atmosphere.sc2.s24.Trail;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * SC2-3, the instruments (docs/SELF_CREATED_2.md 21 to 24, 04+): the ring, the file, the held breath and the follower,
 * their cards' shapes and fits, their scenes on staged mobs, the switch and the multiplayer rules (two sharers bound to
 * one scene, either breaks it, a leave of both lets it go; 23's mobs near a second sharer and the silence for both; 24
 * bound to its one player, UNSHARED). Each scene runs in its own patch of the test server's End (or, for what the cards'
 * overworld gate must pass, of the overworld 200 up), far out on a band of its own, its chunks held loaded; every scene
 * runs in its own instance ({@code new Rings()} ...) ticked by its test, never the live one.
 */
public class SelfCreated2InstrumentTests {
	private static final MobTamperImpl TAMPER = MobTamperImpl.INSTANCE;
	/**
	 * The scenes that walk mobs run in two test environments of their own (two batches), so their walked mobs stay under
	 * the world's cap of 48 steered at once (D-104) while each batch runs side by side.
	 */
	private static final String RINGS = "a1016_02:atmosphere/sc2_a";
	private static final String WALKS = "a1016_02:atmosphere/sc2_b";

	// --- 21: the geometry, the herd, the gates ---

	/**
	 * The radius is the count times the spacing over 2 pi, clamped 4 to 9 (R11-10); the slots go round widdershins (a falling
	 * angle, counter-clockwise seen from above), the point ahead is ahead that way, an animal behind its slot walks faster;
	 * the cells of the way round are at most the cap and each is on the circle; the slots are handed out in the order the
	 * animals stand (nobody crosses).
	 */
	@GameTest
	public void theRingsGeometryGoesWiddershinsWithItsRadiusClampedAndItsSlotsInOrder(GameTestHelper helper) {
		helper.assertTrue(Ring.radius(7, 2.2) == 4.0 && Ring.radius(40, 3.2) == 9.0, "the clamp");
		helper.assertTrue(Math.abs(Ring.radius(14, 3.2) - 14 * 3.2 / (2 * Math.PI)) < 1.0E-9, "14 at 3.2: " + Ring.radius(14, 3.2));
		// R11-10: every cell of the widest ring is bared, one wide, at the default cap.
		List<BlockPos> widest = Ring.cells(new Vec3(0.5, 64, 0.5), Ring.MAX_RADIUS, 63, Integer.MAX_VALUE);
		helper.assertTrue(widest.size() <= new Sc21Config().printCells, "the widest ring has " + widest.size() + " cells, the cap " + new Sc21Config().printCells);
		Sc21Config old = new Sc21Config();
		old.version = 1;
		old.spacing = 2.2;
		old.printCells = 48;
		old.upgrade();
		helper.assertTrue(old.version == Sc21Config.VERSION && old.spacing == 3.2 && old.printCells == 72, "version 1's defaults become 2's");
		helper.assertTrue(Ring.turn(5.0, 10) < 0 && Ring.ahead(1.0, 0.3) < 1.0, "not widdershins (a falling angle)");
		// East (angle 0) to north (-pi/2) is counter-clockwise seen from above with north up.
		Vec3 middle = new Vec3(0.5, 64, 0.5);
		Vec3 east = Ring.at(middle, 5, 0, 64);
		Vec3 next = Ring.at(middle, 5, Ring.ahead(0, 0.5), 64);
		helper.assertTrue(east.x > middle.x && next.z < middle.z, "from the east the way round goes north: " + next);
		double slot = 0.8;
		Vec3 ahead = Ring.pointAhead(middle, 5, slot, 64);
		helper.assertTrue(Ring.angleOf(middle, ahead) < slot && Math.abs(Ring.diff(Ring.angleOf(middle, ahead), slot)) >= Math.toRadians(24.9),
				"the point ahead: " + Ring.angleOf(middle, ahead));
		helper.assertTrue(Ring.behindBy(5, 1.0, 0.8) > 0 && Ring.behindBy(5, 0.6, 0.8) < 0, "behind and ahead of the slot");
		helper.assertTrue(Ring.wantedSpeed(1.0) > Ring.SLOT_SPEED && Ring.wantedSpeed(-1.0) < Ring.SLOT_SPEED, "the keeping speed");
		helper.assertTrue(Ring.factorFor(0.08, 0.23) > 0.3 && Ring.factorFor(0.08, 0.23) < 1.2, "a walk, never a run: " + Ring.factorFor(0.08, 0.23));
		List<BlockPos> cells = Ring.cells(middle, 7.0, 63, 48);
		helper.assertTrue(cells.size() <= 48 && cells.size() >= 30, "cells: " + cells.size());
		for (BlockPos cell : cells) {
			double d = Math.sqrt(Herds.flatSqr(Vec3.atCenterOf(cell), middle));
			helper.assertTrue(Math.abs(d - 7.0) <= 1.0, "a cell off the way round: " + cell.toShortString() + " at " + d);
		}
		double[] angles = {0.1, 2.0, -1.5, 3.0, -2.6};
		int[] slots = Ring.assign(angles, 0.1);
		helper.assertTrue(java.util.Arrays.stream(slots).sorted().boxed().toList().equals(List.of(0, 1, 2, 3, 4)), "not one slot each: "
				+ java.util.Arrays.toString(slots));
		for (int i = 0; i < angles.length; i++) {
			helper.assertTrue(Math.abs(Ring.diff(angles[i], Ring.slot(0.1, slots[i], 5))) <= Math.PI * 2 / 5, "animal " + i + " walks past others");
		}
		helper.succeed();
	}

	/** The herd: 7 of one kind round a point 24 to 64 off; 6 is too few, two kinds of 4 are not one herd, too near or too far is none. */
	@GameTest
	public void aHerdFitsOnlyWithEnoughOfOneKindInTheBand(GameTestHelper helper) {
		Vec3 target = new Vec3(0, 64, 0);
		helper.assertTrue(Cluster.find(herd("sheep", 7, 40, 0), target, 7, 14, 24, 64, 24).isPresent(), "7 sheep 40 off");
		helper.assertTrue(Cluster.find(herd("sheep", 6, 40, 0), target, 7, 14, 24, 64, 24).isEmpty(), "6 sheep");
		List<Cluster.Candidate<Integer>> mixed = new ArrayList<>(herd("sheep", 4, 40, 0));
		mixed.addAll(herd("cow", 4, 40, 1));
		helper.assertTrue(Cluster.find(mixed, target, 7, 14, 24, 64, 24).isEmpty(), "4 sheep and 4 cows");
		helper.assertTrue(Cluster.find(herd("sheep", 9, 10, 0), target, 7, 14, 24, 64, 24).isEmpty(), "9 sheep 10 off");
		helper.assertTrue(Cluster.find(herd("sheep", 9, 80, 0), target, 7, 14, 24, 64, 24).isEmpty(), "9 sheep 80 off");
		Optional<Cluster<Integer>> big = Cluster.find(herd("sheep", 20, 40, 0), target, 7, 14, 24, 64, 24);
		helper.assertTrue(big.isPresent() && big.get().members().size() == 14, "at most 14: " + big.map(c -> c.members().size()));
		helper.succeed();
	}

	private static List<Cluster.Candidate<Integer>> herd(String kind, int n, double off, int salt) {
		List<Cluster.Candidate<Integer>> list = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double a = i * 2.4 + salt;
			list.add(new Cluster.Candidate<>(i, kind, new Vec3(off + Math.cos(a) * 3, 64, Math.sin(a) * 3)));
		}
		return list;
	}

	/**
	 * The caps: at most 6, 2 days apart, one a night; the night runs from dusk to dusk. A card fired outside the overworld
	 * is refused; the card's shape is the pacing table's.
	 */
	@GameTest
	public void theRingsCapsAndGatesHold(GameTestHelper helper) {
		Sc21Config cfg = new Sc21Config();
		helper.assertTrue(RingCard.capsRefusal(Sc2xData.Caps.START, 5, 4, cfg).isEmpty(), "never yet");
		helper.assertTrue(RingCard.capsRefusal(new Sc2xData.Caps(6, 1, 1), 9, 9, cfg).isPresent(), "6 already");
		helper.assertTrue(RingCard.capsRefusal(new Sc2xData.Caps(1, 4, 4), 5, 5, cfg).isPresent(), "a day apart");
		helper.assertTrue(RingCard.capsRefusal(new Sc2xData.Caps(1, 4, 4), 6, 6, cfg).isEmpty(), "two days apart");
		helper.assertTrue(RingCard.capsRefusal(new Sc2xData.Caps(1, 2, 7), 9, 7, cfg).isPresent(), "one a night");
		helper.assertTrue(RingCard.nightOf(12_000) == 0 && RingCard.nightOf(23_999) == 0 && RingCard.nightOf(35_999) == 0 && RingCard.nightOf(36_000) == 1
				&& RingCard.nightOf(11_999) == -1, "the night's span");
		helper.assertTrue(Herds.duskOrNight(12_500) && Herds.duskOrNight(18_000) && !Herds.duskOrNight(6_000) && !Herds.duskOrNight(23_500), "dusk and night");
		Prey end = new Prey(helper.getLevel().getServer().getLevel(Level.END));
		RingCard card = new RingCard();
		card.outdoors = p -> true;
		card.sinceCombat = p -> Long.MAX_VALUE;
		helper.assertTrue(card.refusal(end, true).orElse("").contains("overworld"), "fired in the End: " + card.refusal(end, true));
		helper.succeed();
	}

	/** Every card's tier, stage, tags, habits and fake are the pacing table's (docs/SELF_CREATED_2.md, "Pacing"). */
	@GameTest
	public void theFourCardsHaveThePacingTablesShapes(GameTestHelper helper) {
		shape(helper, "sc_21", Tier.MINOR, Stage.TRACES, Set.of(CardTag.MOB), Set.of(Habit.WATCHER, Habit.VISITOR), true);
		shape(helper, "sc_22", Tier.MINOR, Stage.TRACES, Set.of(CardTag.MOB), Set.of(Habit.WATCHER, Habit.MOURNER), false);
		shape(helper, "sc_23", Tier.MINOR, Stage.PROXIMITY, Set.of(CardTag.MOB), Set.of(Habit.WATCHER), true);
		shape(helper, "sc_24", Tier.AMBIENT, Stage.TRACES, Set.of(CardTag.MOB, CardTag.UNSHARED), Set.of(Habit.WATCHER, Habit.VISITOR), false);
		helper.succeed();
	}

	private static void shape(GameTestHelper helper, String id, Tier tier, Stage stage, Set<CardTag> tags, Set<Habit> habits, boolean fake) {
		Optional<EventCard> card = CardRegistry.get(id);
		helper.assertTrue(card.isPresent(), id + " is not registered");
		EventCard c = card.get();
		helper.assertTrue(c.tier() == tier && c.earliestStage() == stage && c.tags().equals(tags) && c.habits().equals(habits) && c.hasFake() == fake,
				id + ": " + c.tier() + " " + c.earliestStage() + " " + c.tags() + " " + c.habits() + " fake " + c.hasFake());
		// A shared moment is neither PERSONAL nor UNSHARED (D-060): the director makes every sharer feel it.
		helper.assertFalse(c.tags().contains(CardTag.PERSONAL), id + " is PERSONAL");
	}

	// --- 21: the scene ---

	/**
	 * Eight sheep of a staged herd in a test patch: they gather onto a circle and go round it widdershins, each within a
	 * block of the circle once gathered, silent, steered (no goal of theirs runs), bound to nobody (staged).
	 */
	@GameTest(environment = RINGS, maxTicks = 2100)
	public void aStagedHerdOfEightSheepFormsARingAndGoesRoundWiddershinsSilently(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 32);
		Rings rings = new Rings().with(new Online().watch, (l, b) -> true);
		List<Sheep> sheep = new ArrayList<>();
		Rings.Scene[] scene = new Rings.Scene[1];
		double[][] before = new double[1][];
		f.whenReady(() -> {
			f.floor(16, Blocks.GRASS_BLOCK);
			sheep.addAll(f.herd(EntityTypes.SHEEP, 8, 3.0, 2.0, 1.0));
			Optional<Rings.Site> site = Rings.site(f.level, f.vec(0.5, 0, 0.5), 8, 2.2);
			helper.assertTrue(site.isPresent(), "no site on open grass");
			scene[0] = rings.start(f.level, site.get(), sheep, List.of(), 20 * 60, true).orElse(null);
			helper.assertTrue(scene[0] != null, "the scene did not start");
			helper.onEachTick(() -> rings.tick(f.level.getServer()));
		}).thenWaitUntil(() -> helper.assertTrue(scene[0].phase() == Rings.Phase.ROUND, "still gathering"))
				.thenIdle(100)
				.thenExecute(() -> {
					Rings.Scene s = scene[0];
					helper.assertTrue(s.mobs().size() == 8, "only " + s.mobs().size() + " go round");
					before[0] = new double[8];
					for (int i = 0; i < 8; i++) {
						Sheep one = sheep.get(i);
						double d = Math.sqrt(Herds.flatSqr(one.position(), s.middle()));
						TamperState st = ((TamperedMob) one).a1016_02$tamper();
						helper.assertTrue(Math.abs(d - s.radius()) <= 1.0, "a sheep off the circle: " + d + " (radius " + s.radius() + ", at " + one.position()
								+ ", middle " + s.middle() + ", steered " + TAMPER.isSteered(one) + " to " + (st == null ? null : st.steerTarget()) + ", slot "
								+ s.slotPos(s.slotOf(one)) + ", baby " + one.isBaby() + ")");
						helper.assertTrue(TAMPER.isSteered(one) && TAMPER.isSilenced(one) && noGoalRuns(one), "a sheep not steered, not silent, or grazing");
						before[0][i] = Ring.angleOf(s.middle(), one.position());
					}
				}).thenIdle(40).thenExecute(() -> {
					double sum = 0;
					int falling = 0;
					for (int i = 0; i < 8; i++) {
						double moved = Ring.diff(Ring.angleOf(scene[0].middle(), sheep.get(i).position()), before[0][i]);
						sum += moved;
						falling += moved < 0 ? 1 : 0;
					}
					helper.assertTrue(falling >= 7 && sum / 8 < -0.15, "not widdershins: " + falling + " of 8, mean " + sum / 8);
					rings.clear();
					for (Sheep one : sheep) {
						helper.assertFalse(TAMPER.isTampered(one), "a sheep still held after the end");
					}
					f.done();
				}).thenSucceed();
	}

	/**
	 * A ring for A with B sharing the moment: each animal is bound to both, A first. B coming within 12 of the middle
	 * stops it: every animal frozen and facing B for the stare, then let go.
	 */
	@GameTest(environment = RINGS, maxTicks = 1800)
	public void aRingIsBoundToBothSharersAndEitherBreaksItEveryAnimalStaringAtThem(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Online online = new Online();
		Rings rings = new Rings().with(online.watch, (l, b) -> true);
		List<Sheep> sheep = new ArrayList<>();
		Rings.Scene[] scene = new Rings.Scene[1];
		Prey[] b = new Prey[1];
		f.whenReady(() -> {
			f.floor(20, Blocks.GRASS_BLOCK);
			Prey a = f.prey(-30.5, 0.5);
			b[0] = f.prey(-28.5, 4.5);
			online.add(a, b[0]);
			sheep.addAll(f.herd(EntityTypes.SHEEP, 7, 3.0, 0, 0));
			Rings.Site site = must(helper, Rings.site(f.level, f.vec(0.5, 0, 0.5), 7, 2.2), "a site");
			List<UUID> sharers = online.watch.sharingWith(a).stream().map(ServerPlayer::getUUID).toList();
			helper.assertTrue(sharers.equals(List.of(a.getUUID(), b[0].getUUID())), "setup: B shares A's moment: " + sharers);
			// Bound to both, A first, from its first step (checked in this tick: the live tamper lets go of mobs bound to
			// players the live server does not know, every second).
			Rings.Scene bound = must(helper, rings.start(f.level, site, sheep, sharers, 20 * 60, false), "the scene");
			for (Sheep one : sheep) {
				helper.assertTrue(TAMPER.ownersOf(one).equals(sharers), "a sheep is not bound to both, A first: " + TAMPER.ownersOf(one));
			}
			rings.clear();
			scene[0] = must(helper, rings.start(f.level, site, sheep, List.of(), 20 * 60, false), "the scene again");
			helper.assertTrue(bound.phase() == Rings.Phase.DONE, "the first scene runs on");
			helper.onEachTick(() -> rings.tick(f.level.getServer()));
		}).thenWaitUntil(() -> helper.assertTrue(scene[0].phase() == Rings.Phase.ROUND, "still gathering")).thenExecute(() -> {
			b[0].snapTo(f.vec(9.5, 0, 0.5), 0, 0);
		}).thenWaitUntil(() -> helper.assertTrue(scene[0].phase() == Rings.Phase.STARE, "B came within 12 and it goes on")).thenIdle(25).thenExecute(() -> {
			Vec3 eye = b[0].getEyePosition();
			for (Sheep one : sheep) {
				TamperState state = ((TamperedMob) one).a1016_02$tamper();
				helper.assertTrue(TAMPER.isFrozen(one) && state != null && state.faceTarget() != null && state.faceTarget().distanceTo(eye) < 0.5,
						"a sheep does not stare at B");
			}
		}).thenWaitUntil(() -> helper.assertTrue(scene[0].phase() == Rings.Phase.DONE, "the stare does not end")).thenExecute(() -> {
			for (Sheep one : sheep) {
				helper.assertFalse(TAMPER.isTampered(one), "a sheep still held after the stare");
			}
			f.done();
		}).thenSucceed();
	}

	/** An animal hurt drops out at once (let go); under five left, every one is let go and the scene ends. */
	@GameTest(environment = RINGS, maxTicks = 1800)
	public void aRingAnimalHurtDropsOutAndUnderFiveEveryOneIsLetGo(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 32);
		Rings rings = new Rings().with(new Online().watch, (l, b) -> true);
		List<Sheep> sheep = new ArrayList<>();
		Rings.Scene[] scene = new Rings.Scene[1];
		f.whenReady(() -> {
			f.floor(16, Blocks.GRASS_BLOCK);
			sheep.addAll(f.herd(EntityTypes.SHEEP, 6, 3.0, 0, 0));
			scene[0] = must(helper, rings.start(f.level, must(helper, Rings.site(f.level, f.vec(0.5, 0, 0.5), 6, 2.2), "a site"), sheep, List.of(), 20 * 60, true),
					"the scene");
			helper.onEachTick(() -> rings.tick(f.level.getServer()));
		}).thenIdle(30).thenExecute(() -> {
			Prey hitter = f.prey(-12.5, 0.5);
			helper.assertTrue(sheep.getFirst().hurtServer(f.level, f.level.damageSources().playerAttack(hitter), 1.0F), "setup: the hit");
		}).thenIdle(Rings.EVERY + 1).thenExecute(() -> {
			helper.assertFalse(TAMPER.isTampered(sheep.getFirst()), "the hurt sheep is still held");
			helper.assertTrue(scene[0].mobs().size() == 5 && scene[0].phase() != Rings.Phase.DONE, "five go on: " + scene[0].mobs().size());
			Prey hitter = f.prey(-12.5, 0.5);
			sheep.get(1).hurtServer(f.level, f.level.damageSources().playerAttack(hitter), 1.0F);
		}).thenIdle(Rings.EVERY + 1).thenExecute(() -> {
			helper.assertTrue(scene[0].phase() == Rings.Phase.DONE, "four left and it goes on");
			for (Sheep one : sheep) {
				helper.assertFalse(TAMPER.isTampered(one), "a sheep still held with four left");
			}
			f.done();
		}).thenSucceed();
	}

	/** Both players it is for leave: the tamper lets each animal go once nobody sees it, and the scene ends (bound, D-062.5). */
	@GameTest(environment = RINGS, maxTicks = 1800)
	public void aRingIsLetGoOnceBothItsPlayersLeaveAndNobodySeesIt(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Online online = new Online();
		Rings rings = new Rings().with(online.watch, (l, b) -> true);
		List<Sheep> sheep = new ArrayList<>();
		Rings.Scene[] scene = new Rings.Scene[1];
		f.whenReady(() -> {
			f.floor(20, Blocks.GRASS_BLOCK);
			Prey a = f.prey(-30.5, 0.5);
			Prey b = f.prey(-30.5, 6.5);
			online.add(a, b);
			sheep.addAll(f.herd(EntityTypes.SHEEP, 7, 3.0, 0, 0));
			scene[0] = must(helper, rings.start(f.level, must(helper, Rings.site(f.level, f.vec(0.5, 0, 0.5), 7, 2.2), "a site"), sheep,
					List.of(a.getUUID(), b.getUUID()), 20 * 60, false), "the scene");
			helper.onEachTick(() -> rings.tick(f.level.getServer()));
			// In this tick (the live tamper lets go of mobs bound to players the live server does not know, every second).
			MinecraftServer server = f.level.getServer();
			helper.assertTrue(TAMPER.releaseOrphans(server, online.watch, mob -> true) == 0, "let go with both here");
			online.online.removeFirst();
			helper.assertTrue(TAMPER.releaseOrphans(server, online.watch, mob -> true) == 0, "let go with B still here");
			online.online.clear();
			helper.assertTrue(TAMPER.releaseOrphans(server, online.watch, mob -> false) == 0, "let go in someone's view");
			helper.assertTrue(TAMPER.releaseOrphans(server, online.watch, mob -> true) >= 7, "not let go with both gone");
		}).thenIdle(Rings.EVERY + 1).thenExecute(() -> {
			helper.assertTrue(scene[0].phase() == Rings.Phase.DONE, "the scene goes on with its animals let go");
			f.done();
		}).thenSucceed();
	}

	/**
	 * A ring that went round long enough bares its way round and its middle to dirt, in one ledgered batch, only once out
	 * of everyone's view; the switch's give-back puts the grass back through the ledger.
	 */
	@GameTest(environment = RINGS, maxTicks = 2400)
	public void theRingsGroundIsBaredOnlyOutOfViewAndGivenBackByTheSwitch(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 32);
		// In view until the test looks: the ring's own tick tries its prints every 100 ticks, and one that came on the tick
		// the scene ended (or the next) bared it before the test could see it waiting.
		boolean[] out = {false};
		Rings rings = new Rings().with(new Online().watch, (l, b) -> out[0]);
		List<Sheep> sheep = new ArrayList<>();
		Rings.Scene[] scene = new Rings.Scene[1];
		f.whenReady(() -> {
			f.floor(16, Blocks.GRASS_BLOCK);
			sheep.addAll(f.herd(EntityTypes.SHEEP, 7, 3.0, 0, 0));
			scene[0] = must(helper, rings.start(f.level, must(helper, Rings.site(f.level, f.vec(0.5, 0, 0.5), 7, 2.2), "a site"), sheep, List.of(), 420, false),
					"the scene");
			helper.onEachTick(() -> rings.tick(f.level.getServer()));
		}).thenWaitUntil(() -> helper.assertTrue(scene[0].phase() == Rings.Phase.DONE, "it still goes round")).thenExecute(() -> {
			helper.assertTrue(rings.printsWaiting() == 1, "no ground to bare after going round");
			rings.tryPrintsNow(f.level.getServer());
			helper.assertTrue(rings.printsWaiting() == 1 && grassCells(f, scene[0]) == cellsOf(scene[0]).size(), "bared in view");
			out[0] = true;
			rings.tryPrintsNow(f.level.getServer());
			helper.assertTrue(rings.printsWaiting() == 0, "not bared out of view");
			helper.assertTrue(grassCells(f, scene[0]) == 0, "grass left on the way round: " + grassCells(f, scene[0]));
			BlockPos mid = BlockPos.containing(scene[0].middle()).below();
			helper.assertTrue(f.level.getBlockState(mid).is(Blocks.DIRT), "the middle is not bare");
			long ledgered = TraceLedger.get(f.level.getServer()).entries().stream().filter(e -> e.cause().equals(Rings.CAUSE)
					&& f.level.dimension() == e.pos().dimension() && e.pos().pos().closerThan(mid, 12)).count();
			helper.assertTrue(ledgered >= cellsOf(scene[0]).size(), "not ledgered: " + ledgered);
			helper.assertTrue(Rings.giveBack(f.level.getServer()) == 0, "something of it waits");
			helper.assertTrue(grassCells(f, scene[0]) == cellsOf(scene[0]).size() && f.level.getBlockState(mid).is(Blocks.GRASS_BLOCK),
					"the grass is not back");
			f.done();
		}).thenSucceed();
	}

	private static List<BlockPos> cellsOf(Rings.Scene scene) {
		return Ring.cells(scene.middle(), scene.radius(), (int) scene.middle().y - 1, Sc21Config.get().printCells);
	}

	private static int grassCells(Field f, Rings.Scene scene) {
		int n = 0;
		for (BlockPos cell : cellsOf(scene)) {
			n += f.level.getBlockState(cell).is(Blocks.GRASS_BLOCK) ? 1 : 0;
		}
		return n;
	}

	/** The fake: the herd stands still, every one facing the same far point, bound to the sharers; nothing walks. */
	@GameTest(maxTicks = 1500)
	public void theRingsFakeHoldsTheHerdFacingOneFarPoint(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 32);
		List<Sheep> sheep = new ArrayList<>();
		f.whenReady(() -> {
			f.floor(16, Blocks.GRASS_BLOCK);
			sheep.addAll(f.herd(EntityTypes.SHEEP, 7, 3.0, 0, 0));
			Prey a = f.prey(-30.5, 0.5);
			RingCard.Plan plan = new RingCard.Plan(f.vec(0.5, 0, 0.5), must(helper, Rings.site(f.level, f.vec(0.5, 0, 0.5), 7, 2.2), "a site"), List.copyOf(sheep));
			helper.assertTrue(RingCard.fake(f.level, plan, List.of(a.getUUID()), RandomSource.create(3)), "the fake held nothing");
			Vec3 point = null;
			for (Sheep one : sheep) {
				TamperState state = ((TamperedMob) one).a1016_02$tamper();
				helper.assertTrue(TAMPER.isFrozen(one) && !TAMPER.isSteered(one) && state != null && state.faceTarget() != null, "a sheep not held facing");
				point = point == null ? state.faceTarget() : point;
				helper.assertTrue(state.faceTarget().equals(point) && point.distanceTo(f.vec(0.5, 0, 0.5)) > 40, "not one far point");
				helper.assertTrue(TAMPER.ownersOf(one).equals(List.of(a.getUUID())), "not bound");
			}
			f.done();
		}).thenSucceed();
	}

	/**
	 * 04+: on an empty night a ring forms without a card round a herd near a participant, once that night (a won roll);
	 * another look the same night forms none; a lost roll the next night forms none.
	 */
	@GameTest(environment = WALKS, maxTicks = 1500)
	public void anEmptyNightFormsOneRingWithoutACardOnceThatNight(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Online online = new Online();
		Rings rings = new Rings().with(online.watch, (l, b) -> true);
		Sc2xData data = new Sc2xData();
		f.whenReady(() -> {
			f.floor(20, Blocks.GRASS_BLOCK);
			List<Sheep> sheep = f.herd(EntityTypes.SHEEP, 7, 3.0, 0, 0);
			Prey a = f.prey(-34.5, 0.5);
			online.add(a);
			long night = 5 * 24_000L + 18_000L;
			Optional<Rings.Scene> scene = S21.emptyNight(f.level, List.of(a), night, data, 0.0, true, rings, online.watch);
			helper.assertTrue(scene.isPresent() && scene.get().mobs().size() == 7, "no ring on a won empty night");
			helper.assertTrue(data.emptyRoll().formed() == RingCard.nightOf(night) && data.caps("21").lastNight() == RingCard.nightOf(night),
					"the night is not kept: " + data.emptyRoll());
			helper.assertTrue(data.caps("21").count() == 0, "04+ counted as a card's");
			rings.clear();
			helper.assertTrue(S21.emptyNight(f.level, List.of(a), night + 400, data, 0.0, true, rings, online.watch).isEmpty(), "a second the same night");
			helper.assertTrue(S21.emptyNight(f.level, List.of(a), night + 24_000, data, 0.99, true, rings, online.watch).isEmpty(), "a lost roll formed one");
			helper.assertTrue(S21.emptyNight(f.level, List.of(a), 6 * 24_000L + 6_000L, data, 0.0, true, rings, online.watch).isEmpty(), "one by day");
			for (Sheep one : sheep) {
				TAMPER.release(one);
			}
			f.done();
		}).thenSucceed();
	}

	// --- 22 ---

	/** The way ends at the place, passes the target at its distance, and starts 48 to 64 from them on the far side. */
	@GameTest
	public void theFilesWayPassesTheTargetAtItsDistanceFromTheFarSide(GameTestHelper helper) {
		Vec3 target = new Vec3(100, 64, -40);
		Vec3 place = new Vec3(-250, 64, 160);
		for (int side : new int[] {1, -1}) {
			for (double pass : new double[] {24, 32, 40}) {
				Line line = Line.of(target, place, pass, 56, side).orElseThrow();
				helper.assertTrue(Math.abs(line.across(target) - pass) < 1.0E-6, "passes at " + line.across(target) + ", not " + pass);
				double start = Math.sqrt(Herds.flatSqr(line.start(), target));
				helper.assertTrue(Math.abs(start - 56) < 1.0E-6, "starts " + start + " off");
				helper.assertTrue(Herds.flatSqr(line.start(), place) > Herds.flatSqr(target, place), "the start is not on the far side");
				Vec3 toPlace = place.subtract(line.start()).normalize();
				helper.assertTrue(toPlace.x * line.dir().x + toPlace.z * line.dir().z > 0.9999, "it does not head for the place");
				helper.assertTrue(line.along(target) > 0 && line.along(target) < line.length(), "it does not pass the target on the way");
			}
		}
		helper.assertTrue(Line.of(target, target.add(30, 0, 0), 30, 56, 1).isEmpty(), "a place nearer than the start");
		List<Vec3> file = Line.of(target, place, 30, 56, 1).orElseThrow().file(5, 2.0);
		helper.assertTrue(Math.abs(file.get(1).distanceTo(file.get(0)) - 2.0) < 1.0E-6, "the file's spacing");
		helper.succeed();
	}

	/**
	 * A staged file of five animals of three kinds walks its line: each on the line (within 2 across), each 1 to 3.5 behind
	 * the one ahead, the leader well along toward the place, all silent and steered, bound to both who share it.
	 */
	@GameTest(environment = WALKS, maxTicks = 1700)
	public void aFileOfAnimalsWalksInSingleFileAlongItsLine(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Processions processions = new Processions().with((l, b) -> true);
		List<Mob> file = new ArrayList<>();
		Processions.Procession[] p = new Processions.Procession[1];
		UUID[] two = {UUID.randomUUID(), UUID.randomUUID()};
		Line line = new Line(f.vec(-20.5, 0, 0.5), f.vec(300.5, 0, 0.5), new Vec3(1, 0, 0));
		f.whenReady(() -> {
			f.floorRect(-40, -8, 40, 8, Blocks.GRASS_BLOCK);
			List<EntityType<? extends Mob>> kinds = List.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.PIG, EntityTypes.SHEEP, EntityTypes.COW);
			List<Vec3> spots = line.file(kinds.size(), Sc22Config.SPACING);
			for (int i = 0; i < kinds.size(); i++) {
				file.add(f.spawn(kinds.get(i), spots.get(i).x - f.origin.getX(), spots.get(i).z - f.origin.getZ()));
			}
			Processions.Procession bound = must(helper, processions.start(f.level, line, file, null, List.of(two), 20 * 120, true), "the file");
			for (Mob mob : file) {
				helper.assertTrue(TAMPER.ownersOf(mob).equals(List.of(two)), "an animal is not bound to both");
			}
			processions.clear();
			helper.assertTrue(bound.phase() == Processions.Phase.DONE, "the first file walks on");
			p[0] = must(helper, processions.start(f.level, line, file, null, List.of(), 20 * 120, true), "the file again");
			helper.onEachTick(() -> processions.tick(f.level.getServer()));
		}).thenIdle(220).thenExecute(() -> {
			Mob leader = file.getFirst();
			helper.assertTrue(line.along(leader.position()) >= 12, "the leader barely moved: " + line.along(leader.position()));
			for (int i = 0; i < file.size(); i++) {
				Mob mob = file.get(i);
				helper.assertTrue(line.across(mob.position()) <= 2.0, "animal " + i + " is " + line.across(mob.position()) + " off the line");
				helper.assertTrue(TAMPER.isSteered(mob) && TAMPER.isSilenced(mob), "animal " + i + " is not walked silently");
				if (i > 0) {
					double gap = file.get(i - 1).position().distanceTo(mob.position());
					helper.assertTrue(gap >= 2.0 && gap <= 5.0, "the gap behind animal " + (i - 1) + " is " + gap + " (R11-13: about 3.5)");
					helper.assertTrue(line.along(mob.position()) < line.along(file.get(i - 1).position()), "animal " + i + " is not behind");
				}
			}
			processions.clear();
			f.done();
		}).thenSucceed();
	}

	/**
	 * The card's plan: a place 140 off, six animals out of view: the file's cells are on the far side of the target on
	 * the way to the place, which passes the target at the asked distance (the start grounded on a cell's centre), the
	 * kinds taking turns.
	 */
	@GameTest(maxTicks = 1500)
	public void theFilesPlanStartsOutOfViewOnTheFarSideAndPassesAtItsDistance(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 96);
		f.whenReady(() -> {
			f.floor(90, Blocks.GRASS_BLOCK);
			Prey a = f.prey(0.5, 0.5);
			for (int i = 0; i < 3; i++) {
				f.spawn(EntityTypes.COW, 20.5 + i * 3, 30.5);
				f.spawn(EntityTypes.SHEEP, -20.5 - i * 3, 30.5);
			}
			Remains.Place place = new Remains.Place(Remains.Kind.PYRAMID, Level.END, new BoundingBox(f.at(130, -5, -10).getX(), f.origin.getY() - 5,
					f.at(130, 0, -10).getZ(), f.at(150, 0, 10).getX(), f.origin.getY() + 30, f.at(150, 0, 10).getZ()), f.at(140, 0, 0), f.at(130, 0, 0),
					Direction.WEST, Optional.empty(), 0L, 0);
			ProcessionCard card = new ProcessionCard();
			card.remains = () -> stubRemains(place);
			card.outOfView = (l, b) -> true;
			Optional<ProcessionCard.Plan> plan = card.plan(a, 32.0);
			helper.assertTrue(plan.isPresent(), "no plan: " + card.refusal(a, true));
			Line line = plan.get().line();
			helper.assertTrue(Math.abs(line.across(a.position()) - 32.0) <= 1.0, "passes at " + line.across(a.position()));
			double start = Math.sqrt(Herds.flatSqr(line.start(), a.position()));
			helper.assertTrue(start >= 48 && start <= 64, "starts " + start + " off");
			helper.assertTrue(Herds.flatSqr(line.start(), Vec3.atCenterOf(place.entrance())) > Herds.flatSqr(a.position(), Vec3.atCenterOf(place.entrance())),
					"the start is not on the far side");
			helper.assertTrue(plan.get().file().size() == 6 && plan.get().file().get(0).getType() != plan.get().file().get(1).getType(),
					"the kinds do not take turns: " + plan.get().file().stream().map(m -> m.getType().toShortString()).toList());
			helper.assertTrue(plan.get().inside().distanceTo(Vec3.atBottomCenterOf(place.anchor())) < 0.01, "not into the anchor");
			card.outOfView = (l, b) -> false;
			helper.assertTrue(card.plan(a, 32.0).isEmpty(), "a plan with every animal in view");
			f.done();
		}).thenSucceed();
	}

	private static Remains stubRemains(Remains.Place place) {
		return new Remains() {
			@Override
			public List<Remains.Place> known(ServerLevel level) {
				return List.of(place);
			}

			@Override
			public Optional<Remains.Place> containing(ServerLevel level, BlockPos pos) {
				return Optional.empty();
			}

			@Override
			public Optional<Remains.Place> nearest(ServerLevel level, BlockPos pos, Set<Remains.Kind> kinds, int radius) {
				return kinds.contains(place.kind()) && place.distanceTo(pos) <= radius ? Optional.of(place) : Optional.empty();
			}

			@Override
			public boolean visited(UUID player, Remains.Place p) {
				return false;
			}

			@Override
			public long trespassedAt(UUID player) {
				return -1;
			}
		};
	}

	/** After its walk, each animal out of everyone's view for 10 s is moved inside the place and let go; one in view is not. */
	@GameTest(environment = WALKS, maxTicks = 1900)
	public void afterItsWalkEachAnimalOutOfViewIsMovedInsideAndLetGo(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		boolean[] out = {false};
		Processions processions = new Processions().with((l, b) -> out[0]);
		List<Mob> file = new ArrayList<>();
		Processions.Procession[] p = new Processions.Procession[1];
		Line line = new Line(f.vec(-20.5, 0, 0.5), f.vec(200.5, 0, 0.5), new Vec3(1, 0, 0));
		Vec3 inside = f.vec(10.5, 0, 20.5);
		f.whenReady(() -> {
			f.floor(32, Blocks.GRASS_BLOCK);
			f.cellAt(10, 20);
			List<Vec3> spots = line.file(5, Sc22Config.SPACING);
			for (int i = 0; i < 5; i++) {
				EntityType<? extends Mob> kind = i % 2 == 0 ? EntityTypes.COW : EntityTypes.SHEEP;
				file.add(f.spawn(kind, spots.get(i).x - f.origin.getX(), spots.get(i).z - f.origin.getZ()));
			}
			p[0] = must(helper, processions.start(f.level, line, file, inside, List.of(), 20, false), "the file");
			helper.onEachTick(() -> processions.tick(f.level.getServer()));
		}).thenIdle(260).thenExecute(() -> {
			helper.assertTrue(p[0].phase() == Processions.Phase.GOING && p[0].moved() == 0, "moved inside while in view");
			out[0] = true;
		}).thenWaitUntil(() -> helper.assertTrue(p[0].phase() == Processions.Phase.DONE, "still walking")).thenExecute(() -> {
			helper.assertTrue(p[0].moved() >= 1, "nobody was moved inside");
			for (Mob mob : file) {
				helper.assertFalse(TAMPER.isTampered(mob), "an animal still held");
			}
			helper.assertTrue(file.stream().filter(m -> m.position().distanceTo(inside) < 3).count() == p[0].moved(), "the moved ones are not inside");
			f.done();
		}).thenSucceed();
	}

	// --- 23 ---

	/** The bearing: his place if one is known, else where he last stood, else the origin; one under 4 off is passed over. */
	@GameTest
	public void theHeldBreathsBearingIsHisPlaceThenWhereHeStoodThenTheOrigin(GameTestHelper helper) {
		Vec3 from = new Vec3(500, 70, 500);
		Remains.Place place = new Remains.Place(Remains.Kind.HOLLOW, Level.OVERWORLD, new BoundingBox(900, 60, 900, 960, 120, 960), new BlockPos(930, 70, 930),
				new BlockPos(900, 70, 930), Direction.WEST, Optional.empty(), 0L, 0);
		Vec3 stood = new Vec3(100, 70, 800);
		helper.assertTrue(HeldBreath.bearing(Optional.of(place), Optional.of(stood), from).from() == HeldBreath.From.PLACE, "the place first");
		helper.assertTrue(HeldBreath.bearing(Optional.empty(), Optional.of(stood), from).toward().equals(stood), "then where he stood");
		HeldBreath.Bearing origin = HeldBreath.bearing(Optional.empty(), Optional.empty(), from);
		helper.assertTrue(origin.from() == HeldBreath.From.ORIGIN && Math.abs(origin.toward().x) < 1 && Math.abs(origin.toward().z) < 1, "then the origin");
		helper.assertTrue(HeldBreath.bearing(Optional.empty(), Optional.of(from.add(1, 0, 1)), from).from() == HeldBreath.From.ORIGIN, "a spot underfoot");
		Vec3 point = HeldBreath.facePoint(new Vec3(0, 65, 0), new Vec3(30, 0, 40), 200);
		// R11-11: 80 over the eyes, so every head tilts up at once (about 22 degrees).
		helper.assertTrue(Math.abs(point.x - 120) < 1.0E-6 && Math.abs(point.z - 160) < 1.0E-6 && point.y == 65 + HeldBreath.LOOK_UP,
				"200 along it, looking up: " + point);
		double pitch = Math.toDegrees(Math.atan2(HeldBreath.LOOK_UP, 200.0));
		helper.assertTrue(pitch >= 15.0 && pitch <= 30.0, "the heads tilt " + pitch);
		helper.succeed();
	}

	/**
	 * Fired for A in an overworld patch (eight mobs near, a zombie mid-attack on A among them): every one frozen in the
	 * same tick, facing the point on the bearing (within 10 degrees once turned), silent, the zombie never landing a hit;
	 * A gets the client silence; all let go together in one tick at the end; A's day is kept by a live card.
	 */
	@GameTest(maxTicks = 1700)
	public void everyMobNearIsHeldFacingTheBearingSilentAndLetGoTogether(GameTestHelper helper) {
		Field f = new Field(helper, Level.OVERWORLD, 32, 200);
		Online online = new Online();
		HeldBreath breaths = new HeldBreath();
		List<Mob> mobs = new ArrayList<>();
		List<UUID> silenced = new ArrayList<>();
		Prey[] a = new Prey[1];
		Vec3[] point = new Vec3[1];
		long[] until = new long[1];
		f.whenReady(() -> {
			f.floor(16, Blocks.GRASS_BLOCK);
			a[0] = f.prey(0.5, 0.5);
			online.add(a[0]);
			for (int i = 0; i < 6; i++) {
				mobs.add(f.spawn(EntityTypes.COW, -8.5 + i * 3, 9.5));
			}
			mobs.add(f.spawn(EntityTypes.SKELETON, 7.5, -8.5));
			Zombie zombie = f.spawn(EntityTypes.ZOMBIE, 1.5, 1.5);
			zombie.setTarget(a[0]);
			mobs.add(zombie);
			HeldBreathCard card = card23(online, silenced, breaths);
			helper.assertTrue(card.refusal(a[0], false).isEmpty(), "does not fit: " + card.refusal(a[0], false));
			helper.assertTrue(card.fire(new FireContext(a[0], f.level, false, true, RandomSource.create(1))) == FireResult.FIRED, "did not fire");
			for (Mob mob : mobs) {
				helper.assertTrue(TAMPER.isFrozen(mob) && TAMPER.isSilenced(mob), "a " + mob.getType().toShortString() + " not held");
			}
			point[0] = HeldBreath.facePoint(a[0].getEyePosition(), card.bearing(a[0]).toward(), Sc23Config.FACE_DISTANCE);
			until[0] = f.level.getServer().getTickCount() + Math.round(Sc23Config.get().seconds * 20);
			helper.assertTrue(silenced.equals(List.of(a[0].getUUID())), "the silence went to " + silenced);
			for (Mob mob : mobs) {
				helper.assertTrue(TAMPER.ownersOf(mob).equals(List.of(a[0].getUUID())), "a " + mob.getType().toShortString() + " not held for A");
				// From here on bound to nobody: the live tamper lets go of mobs held for players the live server does not know.
				TAMPER.bind(mob, List.of());
			}
			helper.onEachTick(() -> breaths.tick(f.level.getServer()));
		}).thenIdle(60).thenExecute(() -> {
			for (Mob mob : mobs) {
				// R11-11: the nearest monster (the zombie on A) shows A its back fully; every other faces the point on the bearing.
				Vec3 at = mob instanceof Zombie ? HeldBreath.awayFrom(mob.position(), a[0].getEyePosition(), point[0]) : point[0];
				float want = MobTamperImpl.yawToward(mob.position(), at);
				TamperState state = ((TamperedMob) mob).a1016_02$tamper();
				helper.assertTrue(Math.abs(Mth.wrapDegrees(mob.getYHeadRot() - want)) <= 10.0F && Math.abs(Mth.wrapDegrees(mob.yBodyRot - want)) <= 10.0F,
						"a " + mob.getType().toShortString() + " faces " + mob.getYHeadRot() + " (body " + mob.yBodyRot + "), not " + want + "; frozen "
								+ TAMPER.isFrozen(mob) + ", facing " + (state == null ? null : state.faceTarget()) + " from " + mob.position() + ", point "
								+ point[0] + ", ticks " + mob.tickCount);
			}
			helper.assertTrue(a[0].getHealth() == a[0].getMaxHealth(), "the zombie hit A while held");
			Mob zombie = mobs.getLast();
			helper.assertTrue(zombie.isAggressive(), "the zombie's arms are not up while held (R11-11)");
			helper.assertTrue(zombie.getXRot() < -8.0F, "the zombie does not look up: " + zombie.getXRot());
			for (Mob mob : mobs) {
				if (mob instanceof net.minecraft.world.entity.animal.cow.Cow) {
					helper.assertTrue(mob.getXRot() < -8.0F, "a cow does not look up: " + mob.getXRot());
				}
			}
		}).thenWaitUntil(() -> helper.assertTrue(f.level.getServer().getTickCount() >= until[0], "the deadline")).thenExecute(() -> {
			long held = mobs.stream().filter(TAMPER::isTampered).count();
			helper.assertTrue(held == 0 || held == mobs.size(), "let go apart: " + held + " of " + mobs.size());
		}).thenIdle(2).thenExecute(() -> {
			for (Mob mob : mobs) {
				helper.assertFalse(TAMPER.isTampered(mob), "a " + mob.getType().toShortString() + " still held");
			}
			f.done();
		}).thenSucceed();
	}

	private static HeldBreathCard card23(Online online, List<UUID> silenced, HeldBreath breaths) {
		HeldBreathCard card = new HeldBreathCard();
		card.watch = () -> online.watch;
		card.sees = (p, m) -> true;
		card.silence = (p, ticks) -> silenced.add(p.getUUID());
		card.breaths = breaths;
		card.keepsDays = false;
		card.remains = () -> new Remains.Stub();
		return card;
	}

	/**
	 * The fit: 8 mobs near with 3 in view; 7 is too few; none in view does not fit; it fits mid-fight (D-101). A creeper
	 * already swelling and a pet ordered to sit are never held.
	 */
	@GameTest(maxTicks = 1500)
	public void theHeldBreathFitsWithEnoughMobsInViewEvenMidFight(GameTestHelper helper) {
		Field f = new Field(helper, Level.OVERWORLD, 32, 200);
		Online online = new Online();
		f.whenReady(() -> {
			f.floor(16, Blocks.GRASS_BLOCK);
			Prey a = f.prey(0.5, 0.5);
			online.add(a);
			for (int i = 0; i < 7; i++) {
				f.spawn(EntityTypes.CHICKEN, -9.5 + i * 3, 8.5);
			}
			HeldBreathCard card = card23(online, new ArrayList<>(), new HeldBreath());
			helper.assertTrue(card.refusal(a, false).orElse("").contains("7 near"), "7 mobs: " + card.refusal(a, false));
			Creeper creeper = f.spawn(EntityTypes.CREEPER, 5.5, -6.5);
			helper.assertTrue(card.refusal(a, false).isEmpty(), "8 mobs: " + card.refusal(a, false));
			card.sees = (p, m) -> false;
			helper.assertTrue(card.refusal(a, false).orElse("").contains("in view"), "none in view: " + card.refusal(a, false));
			card.sees = (p, m) -> true;
			card.sinceCombat = p -> 0L;
			helper.assertTrue(card.refusal(a, false).isEmpty(), "mid-fight: " + card.refusal(a, false));
			creeper.setSwellDir(1);
			helper.assertFalse(HeldBreath.holdable(creeper, false), "a swelling creeper is held");
			helper.assertTrue(card.refusal(a, false).orElse("").contains("7 near"), "a swelling creeper counted");
			f.done();
		}).thenSucceed();
	}

	/**
	 * Two sharers 40 apart: the mobs near B (out of A's 48) are held too, both get the silence; at most 40 are held,
	 * nearest first. The fake holds the animals only, and no silence.
	 */
	@GameTest(maxTicks = 1500)
	public void aSecondSharersMobsAreHeldBothGetTheSilenceAndTheFakeTurnsAnimalsOnly(GameTestHelper helper) {
		Field f = new Field(helper, Level.OVERWORLD, 64, 200);
		Online online = new Online();
		HeldBreath breaths = new HeldBreath();
		List<UUID> silenced = new ArrayList<>();
		f.whenReady(() -> {
			f.floor(60, Blocks.GRASS_BLOCK);
			Prey a = f.prey(-20.5, 0.5);
			Prey b = f.prey(19.5, 0.5);
			online.add(a, b);
			List<Mob> nearB = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				nearB.add(f.spawn(EntityTypes.COW, 50.5, -6.5 + i * 4));
			}
			Zombie zombie = f.spawn(EntityTypes.ZOMBIE, -20.5, 12.5);
			for (int i = 0; i < 6; i++) {
				f.spawn(EntityTypes.SHEEP, -26.5 + i * 2, -10.5);
			}
			HeldBreathCard card = card23(online, silenced, breaths);
			helper.assertTrue(card.fire(new FireContext(a, f.level, false, true, RandomSource.create(2))) == FireResult.FIRED, "did not fire");
			for (Mob mob : nearB) {
				helper.assertTrue(TAMPER.isFrozen(mob), "a mob near B (" + Math.sqrt(mob.distanceToSqr(a)) + " from A) is not held");
				helper.assertTrue(TAMPER.ownersOf(mob).equals(List.of(a.getUUID(), b.getUUID())), "not held for both");
			}
			helper.assertTrue(silenced.equals(List.of(a.getUUID(), b.getUUID())), "the silence went to " + silenced);
			breaths.clear();
			for (int i = 0; i < 34; i++) {
				f.spawn(EntityTypes.CHICKEN, -20.5 + (i % 6) * 2 - 5, 20.5 + (i / 6) * 2.0);
			}
			List<Mob> gathered = HeldBreath.gather(f.level, List.of(a.position()), 48, 40, false);
			helper.assertTrue(gathered.size() == 40, "held " + gathered.size() + " of more than 40");
			double last = 0;
			for (Mob mob : gathered) {
				double d = mob.distanceToSqr(a);
				helper.assertTrue(d >= last - 1.0E-6, "not nearest first");
				last = d;
			}
			silenced.clear();
			helper.assertTrue(card.fire(new FireContext(a, f.level, true, true, RandomSource.create(4))) == FireResult.FIRED, "the fake did not fire");
			helper.assertFalse(TAMPER.isTampered(zombie), "the fake held a zombie");
			helper.assertTrue(silenced.isEmpty(), "the fake silenced " + silenced);
			breaths.clear();
			f.done();
		}).thenSucceed();
	}

	// --- 24 ---

	/** A staged player for the follower: moved by the test; looks where the test says. */
	static final class Walker implements Followed {
		final ServerLevel level;
		final UUID id = UUID.randomUUID();
		Vec3 pos;
		Vec3 look = new Vec3(1, 0, 0);
		boolean outdoors = true;
		boolean present = true;
		boolean riding;
		@Nullable BlockPos base;
		@Nullable Mob looksAt;

		@Override
		public boolean riding() {
			return riding;
		}

		Walker(ServerLevel level, Vec3 pos) {
			this.level = level;
			this.pos = pos;
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public boolean present() {
			return present;
		}

		@Override
		public ServerLevel level() {
			return level;
		}

		@Override
		public Vec3 position() {
			return pos;
		}

		@Override
		public Vec3 eye() {
			return pos.add(0, 1.62, 0);
		}

		@Override
		public Vec3 look() {
			return looksAt != null ? looksAt.position().subtract(pos).normalize() : look;
		}

		@Override
		public boolean outdoors() {
			return outdoors;
		}

		@Override
		public boolean sees(AABB box) {
			return looksAt != null && looksAt.getBoundingBox().intersects(box);
		}

		@Override
		public Optional<BlockPos> base() {
			return Optional.ofNullable(base);
		}
	}

	/**
	 * The walker goes east 45 and stops: the cow keeps back along the way (28 to 40 off once they stop: the edge of their
	 * view, R10-10), stops where it is, and faces them, frozen, while they look at it.
	 */
	@GameTest(environment = WALKS, maxTicks = 2400)
	public void theFollowerKeepsBackAlongTheWayStopsWhenTheyStopAndFacesThemWhenLooked(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 64);
		Follows follows = new Follows().with((l, b) -> false, l -> 0L);
		Walker w = new Walker(f.level, f.vec(-34.5, 0, 0.5));
		Cow[] cow = new Cow[1];
		f.whenReady(() -> {
			f.floor(56, Blocks.GRASS_BLOCK);
			cow[0] = f.spawn(EntityTypes.COW, -50.5, 4.5);
			helper.assertTrue(follows.start(cow[0], w, 1.0, true), "the follow did not start");
			helper.onEachTick(() -> {
				if (w.pos.x < f.vec(10.5, 0, 0).x) {
					w.pos = w.pos.add(0.12, 0, 0);
				}
				follows.tick(f.level.getServer());
			});
		}).thenWaitUntil(() -> helper.assertTrue(w.pos.x >= f.vec(10.5, 0, 0).x, "walking")).thenIdle(260).thenExecute(() -> {
			double d = Math.sqrt(Herds.flatSqr(cow[0].position(), w.pos));
			helper.assertTrue(d >= Sc24Config.get().followMin - 2 && d <= Sc24Config.get().followMax + 2, "the cow is " + d + " off once they stopped");
			helper.assertTrue(cow[0].getX() < w.pos.x, "the cow is not behind them");
		}).thenExecuteFor(40, () -> helper.assertTrue(cow[0].getDeltaMovement().horizontalDistance() < 0.05, "the cow walks on while they stand"))
				.thenExecute(() -> w.looksAt = cow[0]).thenIdle(40).thenExecute(() -> {
					TamperState state = ((TamperedMob) cow[0]).a1016_02$tamper();
					helper.assertTrue(TAMPER.isFrozen(cow[0]) && state != null && state.faceTarget() != null && state.faceTarget().distanceTo(w.eye()) < 0.5,
							"the cow does not face them while they look");
					float want = MobTamperImpl.yawToward(cow[0].position(), w.eye());
					helper.assertTrue(Math.abs(Mth.wrapDegrees(cow[0].yBodyRot - want)) <= 15.0F, "the cow is not turned to them: " + cow[0].yBodyRot + ", not " + want);
					follows.clear();
					helper.assertFalse(TAMPER.isTampered(cow[0]), "held after the end");
					f.done();
				}).thenSucceed();
	}

	/** The walker comes at the cow: it backs off and is never under its nearest (20) for long (it keeps 17 or more). */
	@GameTest(environment = WALKS, maxTicks = 1700)
	public void theFollowerNeverLetsThemNearerThanItsNearest(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 64);
		Follows follows = new Follows().with((l, b) -> false, l -> 0L);
		Walker w = new Walker(f.level, f.vec(14.5, 0, 0.5));
		Cow[] cow = new Cow[1];
		f.whenReady(() -> {
			f.floor(56, Blocks.GRASS_BLOCK);
			cow[0] = f.spawn(EntityTypes.COW, -14.5, 0.5);
			helper.assertTrue(follows.start(cow[0], w, 1.0, true), "the follow did not start");
			helper.onEachTick(() -> {
				w.pos = w.pos.add(-0.08, 0, 0);
				follows.tick(f.level.getServer());
			});
		}).thenIdle(160).thenExecuteFor(60, () -> {
			double d = Math.sqrt(Herds.flatSqr(cow[0].position(), w.pos));
			helper.assertTrue(d >= Sc24Config.get().nearest - 3.0, "the cow let them within " + d);
		}).thenExecute(() -> {
			follows.clear();
			f.done();
		}).thenSucceed();
	}

	/**
	 * Far behind (they walked on quickly, on foot), the cow is moved up only once out of everyone's view, to about 34 back
	 * along their way; never behind a rider (R10-10: a cow that keeps up with a horse is a mod): then it is left behind and
	 * goes.
	 */
	@GameTest(environment = WALKS, maxTicks = 1800)
	public void theFollowerCatchesUpOnlyOutOfViewAndNeverAfterARider(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 96);
		boolean[] out = {false};
		Follows follows = new Follows().with((l, b) -> out[0], l -> 0L);
		Walker w = new Walker(f.level, f.vec(-60.5, 0, 0.5));
		Cow[] cow = new Cow[1];
		f.whenReady(() -> {
			f.floor(90, Blocks.GRASS_BLOCK);
			cow[0] = f.spawn(EntityTypes.COW, -80.5, 0.5);
			helper.assertTrue(follows.start(cow[0], w, 1.0, true), "the follow did not start");
			// Their way: east along z 0.5 at a run (under the speed of a rider), until they are far ahead of a cow's walk.
			helper.onEachTick(() -> {
				if (w.pos.x < f.vec(60.5, 0, 0).x) {
					w.pos = w.pos.add(0.28, 0, 0);
				}
				follows.tick(f.level.getServer());
			});
		}).thenWaitUntil(() -> helper.assertTrue(w.pos.x >= f.vec(60.5, 0, 0).x, "walking")).thenExecute(() -> {
			double d = Math.sqrt(Herds.flatSqr(cow[0].position(), w.pos));
			helper.assertTrue(d > Sc24Config.get().catchUpAt, "moved up in view: " + d);
			out[0] = true;
		}).thenIdle(Follows.EVERY + 1).thenExecute(() -> {
			double d = Math.sqrt(Herds.flatSqr(cow[0].position(), w.pos));
			helper.assertTrue(d >= 28 && d <= 40, "moved up to " + d + " back");
			helper.assertTrue(follows.current() != null && follows.current().mode() == Follows.Mode.FOLLOW, "not following");
			// Now they ride off: it is left behind, never moved up after them.
			w.riding = true;
			out[0] = false;
		}).thenExecuteFor(100, () -> w.pos = w.pos.add(0.6, 0, 0)).thenExecute(() -> {
			out[0] = true;
			follows.tick(f.level.getServer());
		}).thenIdle(Follows.EVERY * 2).thenExecute(() -> {
			double d = Math.sqrt(Herds.flatSqr(cow[0].position(), w.pos));
			Follows.Mode mode = follows.current() == null ? Follows.Mode.DONE : follows.current().mode();
			helper.assertTrue(d > Sc24Config.get().catchUpAt && mode != Follows.Mode.FOLLOW, "kept up with a rider: " + d + " off, " + mode);
			follows.clear();
			f.done();
		}).thenSucceed();
	}

	/** They go in (no sky) for 5 s: the cow waits a few blocks outside the last spot they were outdoors, frozen, facing in. */
	@GameTest(environment = WALKS, maxTicks = 1900)
	public void theFollowerWaitsAtTheMouthFacingInWhenTheyGoUnder(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Follows follows = new Follows().with((l, b) -> false, l -> 0L);
		Walker w = new Walker(f.level, f.vec(-20.5, 0, 0.5));
		Cow[] cow = new Cow[1];
		f.whenReady(() -> {
			f.floor(40, Blocks.GRASS_BLOCK);
			cow[0] = f.spawn(EntityTypes.COW, -34.5, 0.5);
			helper.assertTrue(follows.start(cow[0], w, 1.0, true), "the follow did not start");
			helper.onEachTick(() -> {
				if (w.pos.x < f.vec(10.5, 0, 0).x) {
					w.pos = w.pos.add(0.15, 0, 0);
					w.outdoors = w.pos.x < f.vec(0.5, 0, 0).x;
				}
				follows.tick(f.level.getServer());
			});
		}).thenWaitUntil(() -> helper.assertTrue(follows.current() != null && follows.current().mode() == Follows.Mode.MOUTH, "not at the mouth yet"))
				.thenWaitUntil(() -> helper.assertTrue(TAMPER.isFrozen(cow[0]), "the cow does not stand at the mouth")).thenExecute(() -> {
					double fromMouth = Math.sqrt(Herds.flatSqr(cow[0].position(), f.vec(0.5, 0, 0.5)));
					helper.assertTrue(fromMouth <= 6.0 && cow[0].getX() < f.vec(0.5, 0, 0).x, "the cow waits " + fromMouth + " from the mouth, on the wrong side?");
					TamperState state = ((TamperedMob) cow[0]).a1016_02$tamper();
					helper.assertTrue(state != null && state.faceTarget() != null && state.faceTarget().x > cow[0].getX(), "the cow does not face in");
					follows.clear();
					f.done();
				}).thenSucceed();
	}

	/** At home at night (indoors, by the base): the cow stands 3 to 5 outside the window on its axis (R11-14), frozen, facing it. */
	@GameTest(environment = WALKS, maxTicks = 1900)
	public void theFollowerStandsOutsideTheWindowAtNight(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Follows follows = new Follows().with((l, b) -> false, l -> 18_000L);
		Walker w = new Walker(f.level, f.vec(0.5, 0, 0.5));
		Cow[] cow = new Cow[1];
		f.whenReady(() -> {
			f.floor(40, Blocks.GRASS_BLOCK);
			// A 5 x 5 plank hut round (0, 0), a glass pane in its east wall.
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					for (int y = 0; y < 3; y++) {
						boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
						f.set(f.at(x, y, z), wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
					}
					f.set(f.at(x, 3, z), Blocks.OAK_PLANKS.defaultBlockState());
				}
			}
			f.set(f.at(2, 1, 0), Blocks.GLASS_PANE.defaultBlockState());
			w.base = f.at(0, 0, 0);
			w.outdoors = false;
			cow[0] = f.spawn(EntityTypes.COW, 14.5, -6.5);
			helper.assertTrue(follows.start(cow[0], w, 1.0, true), "the follow did not start");
			helper.onEachTick(() -> follows.tick(f.level.getServer()));
		}).thenWaitUntil(() -> helper.assertTrue(follows.current() != null && follows.current().mode() == Follows.Mode.WINDOW, "not at the window"))
				.thenWaitUntil(() -> helper.assertTrue(TAMPER.isFrozen(cow[0]), "the cow does not stand there")).thenExecute(() -> {
					double fromPane = Math.sqrt(Herds.flatSqr(cow[0].position(), Vec3.atCenterOf(f.at(2, 1, 0))));
					helper.assertTrue(fromPane >= 2.5 && fromPane <= 5.8 && cow[0].getX() > f.vec(2, 0, 0).x && Math.abs(cow[0].getZ() - f.vec(0, 0, 0.5).z) < 1.0,
							"the cow stands " + fromPane + " from the pane, at z " + cow[0].getZ());
					TamperState state = ((TamperedMob) cow[0]).a1016_02$tamper();
					helper.assertTrue(state != null && state.faceTarget() != null && state.faceTarget().distanceTo(Vec3.atCenterOf(f.at(2, 1, 0))) < 0.5,
							"the cow does not face the window");
					follows.clear();
					f.done();
				}).thenSucceed();
	}

	/** They leave: the cow walks away and, out of view, is moved 150 or more off, then let go; the follow is over. */
	@GameTest(environment = WALKS, maxTicks = 2800)
	public void theFollowerIsMovedFarOffAndLetGoWhenTheyLeave(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 150, 40, 16);
		Follows follows = new Follows().with((l, b) -> true, l -> 0L);
		Walker w = new Walker(f.level, f.vec(-30.5, 0, 0.5));
		Cow[] cow = new Cow[1];
		f.whenReady(() -> {
			f.floorRect(-40, -10, 148, 10, Blocks.GRASS_BLOCK);
			cow[0] = f.spawn(EntityTypes.COW, -12.5, 0.5);
			helper.assertTrue(follows.start(cow[0], w, 1.0, true), "the follow did not start");
			w.present = false;
			helper.onEachTick(() -> follows.tick(f.level.getServer()));
		}).thenWaitUntil(() -> helper.assertTrue(follows.current() == null, "still following")).thenExecute(() -> {
			double d = Math.sqrt(Herds.flatSqr(cow[0].position(), w.pos));
			helper.assertTrue(d >= 150, "only " + d + " off");
			helper.assertFalse(TAMPER.isTampered(cow[0]), "still held");
			f.done();
		}).thenSucceed();
	}

	/**
	 * A friend beside the cow changes nothing: it follows its own player (looked up by UUID), bound to them alone; the card
	 * is UNSHARED, so only that player feels it.
	 */
	@GameTest(environment = WALKS, maxTicks = 1500)
	public void aFriendNearDoesNotChangeWhomItFollowsAndOnlyItsPlayerFeelsIt(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Online online = new Online();
		Follows follows = new Follows().with((l, b) -> false, l -> 0L);
		f.whenReady(() -> {
			f.floor(40, Blocks.GRASS_BLOCK);
			Prey a = f.prey(10.5, 0.5);
			Prey friend = f.prey(-14.5, 2.5);
			online.add(a, friend);
			Cow cow = f.spawn(EntityTypes.COW, -16.5, 0.5);
			helper.assertTrue(follows.start(cow, Followed.player(f.level.getServer(), a.getUUID(), f.level, () -> online.watch), 1.0, false), "no start");
			helper.assertTrue(TAMPER.ownersOf(cow).equals(List.of(a.getUUID())), "bound to " + TAMPER.ownersOf(cow));
			helper.assertTrue(follows.current().target().id().equals(a.getUUID()), "it follows someone else");
			helper.assertTrue(new FollowerCard().tags().contains(CardTag.UNSHARED), "not UNSHARED");
			online.online.remove(a);
			follows.tick(f.level.getServer());
			helper.assertTrue(follows.current() == null || follows.current().mode() == Follows.Mode.ENDING || follows.current().mode() == Follows.Mode.DONE,
					"it went on after its player left, with a friend beside it");
			follows.clear();
			f.done();
		}).thenSucceed();
	}

	/** The way back along a trail: by length of way, from where they are now, the oldest point past its end. */
	@GameTest
	public void theTrailWalksBackByLengthOfWay(GameTestHelper helper) {
		Trail trail = new Trail();
		for (int i = 0; i <= 20; i++) {
			trail.add(new Vec3(i, 64, 0), true);
		}
		for (int i = 1; i <= 10; i++) {
			trail.add(new Vec3(20, 64, i), i < 4);
		}
		Vec3 back = trail.back(new Vec3(20, 64, 10), 15).orElseThrow();
		helper.assertTrue(back.distanceTo(new Vec3(15, 64, 0)) < 1.0E-6, "15 back is " + back);
		helper.assertTrue(trail.back(new Vec3(20, 64, 10), 500).orElseThrow().equals(new Vec3(0, 64, 0)), "past its end");
		Trail.Point[] mouth = trail.mouth().orElseThrow();
		helper.assertTrue(mouth[0].pos().equals(new Vec3(20, 64, 3)) && mouth[1].pos().equals(new Vec3(20, 64, 4)), "the mouth: " + mouth[0].pos());
		helper.succeed();
	}

	// --- the switch ---

	/** The switch off: a ring, a file and a follower each let go of their animals at their next step; a hold is let go by its clear. */
	@GameTest(environment = WALKS, maxTicks = 1500)
	public void theSwitchOffLetsEveryInstrumentGo(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Rings rings = new Rings().with(new Online().watch, (l, b) -> true);
		Processions processions = new Processions().with((l, b) -> true);
		Follows follows = new Follows().with((l, b) -> false, l -> 0L);
		f.whenReady(() -> {
			f.floor(40, Blocks.GRASS_BLOCK);
			List<Sheep> sheep = f.herd(EntityTypes.SHEEP, 6, 3.0, -20, 0);
			must(helper, rings.start(f.level, must(helper, Rings.site(f.level, f.vec(-19.5, 0, 0.5), 6, 2.2), "a site"), sheep, List.of(), 1200, true), "the ring");
			Line line = new Line(f.vec(5.5, 0, 20.5), f.vec(300.5, 0, 20.5), new Vec3(1, 0, 0));
			List<Mob> file = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				file.add(f.spawn(EntityTypes.PIG, 5.5 - i * 2, 20.5));
			}
			must(helper, processions.start(f.level, line, file, null, List.of(), 2400, true), "the file");
			Cow cow = f.spawn(EntityTypes.COW, 20.5, -20.5);
			follows.start(cow, new Walker(f.level, f.vec(30.5, 0, -20.5)), 1.0, true);
			List<Mob> all = new ArrayList<>(sheep);
			all.addAll(file);
			all.add(cow);
			for (Mob mob : all) {
				helper.assertTrue(TAMPER.isSteered(mob), "setup: a " + mob.getType().toShortString() + " not walked");
			}
			SelfCreated.Config sc = SelfCreated.config();
			sc.enabled = false;
			try {
				MinecraftServer server = f.level.getServer();
				// Their next steps, at once (each steps every 10 ticks).
				for (int t = 0; t < 12; t++) {
					rings.tick(server);
					processions.tick(server);
					follows.tick(server);
				}
				rings.clear();
				processions.clear();
				follows.clear();
			} finally {
				sc.enabled = true;
			}
			for (Mob mob : all) {
				helper.assertFalse(TAMPER.isTampered(mob), "a " + mob.getType().toShortString() + " still held with the switch off");
			}
			helper.assertFalse(rings.running() || processions.running() || follows.running(), "something still runs");
			f.done();
		}).thenSucceed();
	}

	// --- fixed in review (FIX-SC2-REST) ---

	/** The review's scenes that walk mobs, a batch of their own (the world's 48 steered). */
	private static final String REVIEW = "a1016_02:atmosphere/sc2_c";

	/**
	 * R13-7: the file's animals are ledgered as taken from where each stood (one entry each, {@code atmosphere:s22}); the
	 * switch's give-back puts each one back there.
	 */
	@GameTest(environment = REVIEW, maxTicks = 1500)
	public void theFilesAnimalsAreLedgeredFromWhereTheyStoodAndTheSwitchPutsThemBack(GameTestHelper helper) {
		Field f = new Field(helper, Level.OVERWORLD, 96, 200);
		Online online = new Online();
		Processions processions = new Processions().with((l, b) -> true);
		f.whenReady(() -> {
			f.floor(90, Blocks.GRASS_BLOCK);
			Prey a = f.prey(0.5, 0.5);
			online.add(a);
			List<Mob> herd = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				herd.add(f.spawn(EntityTypes.COW, 20.5 + i * 3, 30.5));
				herd.add(f.spawn(EntityTypes.SHEEP, -20.5 - i * 3, 30.5));
			}
			java.util.Map<UUID, BlockPos> stood = new java.util.HashMap<>();
			herd.forEach(m -> stood.put(m.getUUID(), m.blockPosition()));
			Remains.Place place = new Remains.Place(Remains.Kind.PYRAMID, Level.OVERWORLD, new BoundingBox(f.at(130, -5, -10).getX(), f.origin.getY() - 5,
					f.at(130, 0, -10).getZ(), f.at(150, 0, 10).getX(), f.origin.getY() + 30, f.at(150, 0, 10).getZ()), f.at(140, 0, 0), f.at(130, 0, 0),
					Direction.WEST, Optional.empty(), 0L, 0);
			ProcessionCard card = new ProcessionCard();
			card.remains = () -> stubRemains(place);
			card.outOfView = (l, b) -> true;
			card.outdoors = p -> true;
			card.sinceCombat = p -> Long.MAX_VALUE;
			card.watch = () -> online.watch;
			card.processions = processions;
			card.keepsCaps = false;
			card.move = (mob, cell) -> {
				mob.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
				return true;
			};
			MinecraftServer server = f.level.getServer();
			boolean[] plans = {card.plan(a, 24.0).isPresent(), card.plan(a, 40.0).isPresent()};
			// The pass at its nearest: the staged floor holds the near line only.
			double passMax = Sc22Config.get().passMax;
			Sc22Config.get().passMax = Sc22Config.get().passMin;
			FireResult fired;
			try {
				fired = card.fire(new FireContext(a, f.level, false, true, RandomSource.create(5)));
			} finally {
				Sc22Config.get().passMax = passMax;
			}
			helper.assertTrue(fired == FireResult.FIRED, "did not fire: " + fired + ", plans at 24 and 40: " + plans[0] + " " + plans[1] + ", running "
					+ processions.running());
			List<TraceLedger.Entry> taken = TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(ProcessionCard.CAUSE)
					&& e.entity().map(stood::containsKey).orElse(false)).toList();
			helper.assertTrue(taken.size() == herd.size() && taken.stream().allMatch(e -> e.kind() == TraceLedger.Kind.MOVE_MOB
					&& e.pos().pos().equals(stood.get(e.entity().orElseThrow()))), "not one entry each from where it stood: " + taken);
			processions.clear();
			helper.assertTrue(Herds.giveBack(server, ProcessionCard.CAUSE) == 0, "some still to give back");
			for (Mob mob : herd) {
				helper.assertTrue(mob.blockPosition().equals(stood.get(mob.getUUID())), "a " + mob.getType().toShortString() + " not put back: "
						+ mob.blockPosition() + ", stood at " + stood.get(mob.getUUID()));
			}
			f.done();
		}).thenSucceed();
	}

	/**
	 * R13-7: the follower's animal is ledgered as taken from where it stood on its first move (out of view, catching up);
	 * once, however often it is moved; the switch's give-back puts it back there.
	 */
	@GameTest(environment = REVIEW, maxTicks = 1500)
	public void theFollowersAnimalIsLedgeredOnceAndTheSwitchPutsItBack(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 96);
		boolean[] out = {false};
		Follows follows = new Follows().with((l, b) -> out[0], l -> 0L);
		Walker w = new Walker(f.level, f.vec(-40.5, 0, 0.5));
		Cow[] cow = new Cow[1];
		BlockPos[] stood = new BlockPos[1];
		f.whenReady(() -> {
			f.floor(90, Blocks.GRASS_BLOCK);
			cow[0] = f.spawn(EntityTypes.COW, -60.5, 0.5);
			stood[0] = cow[0].blockPosition();
			helper.assertTrue(follows.start(cow[0], w, 1.0, false), "the follow did not start");
			for (int i = 0; i <= 80; i++) {
				w.pos = f.vec(-40.5 + i, 0, 0.5);
				follows.tick(f.level.getServer());
			}
			out[0] = true;
			helper.onEachTick(() -> follows.tick(f.level.getServer()));
		}).thenIdle(Follows.EVERY + 1).thenExecute(() -> {
			w.pos = f.vec(80.5, 0, 0.5);
		}).thenIdle(Follows.EVERY * 3).thenExecute(() -> {
			MinecraftServer server = f.level.getServer();
			List<TraceLedger.Entry> taken = TraceLedger.get(server).entries().stream()
					.filter(e -> e.cause().equals(Follows.CAUSE) && e.entity().equals(Optional.of(cow[0].getUUID()))).toList();
			helper.assertTrue(taken.size() == 1 && taken.getFirst().pos().pos().equals(stood[0]), "not once from where it stood: " + taken);
			follows.clear();
			helper.assertTrue(Herds.giveBack(server, Follows.CAUSE) == 0 && cow[0].blockPosition().equals(stood[0]), "not put back: " + cow[0].blockPosition());
			f.done();
		}).thenSucceed();
	}

	/** R12-35: its end is over after its hard cap even when someone keeps it in view all along: let go where it is. */
	@GameTest(environment = REVIEW, maxTicks = 6600)
	public void theFollowersEndIsOverAfterItsCapEvenInView(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		Follows follows = new Follows().with((l, b) -> false, l -> 0L);
		Walker w = new Walker(f.level, f.vec(-20.5, 0, 0.5));
		Cow[] cow = new Cow[1];
		f.whenReady(() -> {
			f.floor(40, Blocks.GRASS_BLOCK);
			cow[0] = f.spawn(EntityTypes.COW, -12.5, 0.5);
			helper.assertTrue(follows.start(cow[0], w, 1.0, true), "the follow did not start");
			w.present = false;
			helper.onEachTick(() -> follows.tick(f.level.getServer()));
		}).thenWaitUntil(() -> helper.assertTrue(follows.current() == null, "still on its end")).thenExecute(() -> {
			helper.assertFalse(TAMPER.isTampered(cow[0]), "still held");
			f.done();
		}).thenSucceed();
	}

	/**
	 * R12-33: the switch ends what no scene tracks: the ring's fake (its held animals) and 29's replay (no beat once off).
	 */
	@GameTest(maxTicks = 1500)
	public void theSwitchEndsTheRingsFakeAndTheReplay(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 32);
		f.whenReady(() -> {
			f.floor(16, Blocks.GRASS_BLOCK);
			List<Sheep> sheep = f.herd(EntityTypes.SHEEP, 7, 3.0, 0, 0);
			Prey a = f.prey(-30.5, 0.5);
			RingCard.Plan plan = new RingCard.Plan(f.vec(0.5, 0, 0.5), must(helper, Rings.site(f.level, f.vec(0.5, 0, 0.5), 7, 2.2), "a site"), List.copyOf(sheep));
			helper.assertTrue(RingCard.fake(f.level, plan, List.of(a.getUUID()), RandomSource.create(3)) && Rings.LIVE.fakesHeld() >= 7, "the fake held nothing");
			Rings.LIVE.clear();
			for (Sheep one : sheep) {
				helper.assertFalse(TAMPER.isTampered(one), "a sheep of the fake still held");
			}
			Online online = new Online();
			online.add(a);
			List<String> played = new ArrayList<>();
			com.forzacode.a1016_02.atmosphere.sc2.s29.S29Card.Replay replay = new com.forzacode.a1016_02.atmosphere.sc2.s29.S29Card.Replay(online.watch,
					a.getUUID(), f.level, f.vec(0.5, -3, 0.5), List.of(new com.forzacode.a1016_02.atmosphere.sc2.s29.S29Card.Beat(0,
					net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(net.minecraft.sounds.SoundEvents.STONE_HIT), 1.0F, 1.0F)),
					(level, sound, source, pos, volume, pitch) -> {
						played.add(sound.getRegisteredName());
						return List.of();
					});
			SelfCreated.Config sc = SelfCreated.config();
			sc.enabled = false;
			try {
				helper.assertFalse(replay.tick(f.level.getServer()), "the replay went on with the switch off");
			} finally {
				sc.enabled = true;
			}
			helper.assertTrue(played.isEmpty(), "a beat played with the switch off: " + played);
			f.done();
		}).thenSucceed();
	}

	/**
	 * R12-29: the fits look at a bounded number however many animals stand near: 23 asks for a line of sight among the
	 * nearest 48 at most, 22 asks its view rule for the nearest 64 at most. R12-31: 22 with a minimum of 0 never plans a
	 * file of none (it threw).
	 */
	@GameTest(maxTicks = 1500)
	public void theFitsLookAtABoundedNumberAndAMinimumOfNoneNeverThrows(GameTestHelper helper) {
		Field f = new Field(helper, Level.OVERWORLD, 64, 200);
		Online online = new Online();
		f.whenReady(() -> {
			f.floor(60, Blocks.GRASS_BLOCK);
			Prey a = f.prey(0.5, 0.5);
			online.add(a);
			HeldBreathCard card = card23(online, new ArrayList<>(), new HeldBreath());
			for (int i = 0; i < 80; i++) {
				f.spawn(EntityTypes.CHICKEN, -30.5 + (i % 20) * 3, -12.5 + (i / 20) * 3);
			}
			int[] looked = {0};
			card.sees = (p, m) -> {
				looked[0]++;
				return false;
			};
			String why = card.refusal(a, false).orElse("");
			int asked = looked[0];
			helper.assertTrue(why.contains("in view"), "none in view: " + why);
			helper.assertTrue(asked <= 48, "a line of sight for " + asked);

			ProcessionCard file = new ProcessionCard();
			Remains.Place place = new Remains.Place(Remains.Kind.PYRAMID, Level.OVERWORLD, new BoundingBox(f.at(300, -5, -10).getX(), f.origin.getY() - 5,
					f.at(300, 0, -10).getZ(), f.at(320, 0, 10).getX(), f.origin.getY() + 30, f.at(320, 0, 10).getZ()), f.at(310, 0, 0), f.at(300, 0, 0),
					Direction.WEST, Optional.empty(), 0L, 0);
			file.remains = () -> stubRemains(place);
			int[] views = {0};
			file.outOfView = (l, b) -> {
				views[0]++;
				return false;
			};
			file.plan(a, 32.0);
			helper.assertTrue(views[0] <= 64, "the view rule asked " + views[0] + " times");
			for (Entity chicken : f.level.getEntitiesOfClass(net.minecraft.world.entity.animal.chicken.Chicken.class, new AABB(f.origin).inflate(64))) {
				chicken.discard();
			}
			int min = com.forzacode.a1016_02.atmosphere.sc2.s22.Sc22Config.get().minMobs;
			com.forzacode.a1016_02.atmosphere.sc2.s22.Sc22Config.get().minMobs = 0;
			try {
				file.outOfView = (l, b) -> true;
				helper.assertTrue(file.plan(a, 32.0).isEmpty(), "a file of none");
			} finally {
				com.forzacode.a1016_02.atmosphere.sc2.s22.Sc22Config.get().minMobs = min;
			}
			f.done();
		}).thenSucceed();
	}

	// --- fixed in review (FIX-SC2-ACTORS) ---

	/** The actors' review tests that walk or ledger animals: a batch of their own (another batch's give-back of a cause takes every entry of it). */
	private static final String ACTORS = "a1016_02:atmosphere/sc2_d";

	/**
	 * R10-10, R11-14: their own animal (a black sheep from their field) follows a few minutes and goes home (the ledger's
	 * undo: where it stood); that night, with them indoors at home, it stands 3 to 5 out from their window on its axis,
	 * facing in; at dawn it is back in its field and nothing of it is left in the ledger. The follow's time runs to dawn.
	 */
	@GameTest(environment = ACTORS, maxTicks = 2400)
	public void theirOwnAnimalFollowsAWhileGoesHomeAndStandsAtTheirWindowThatNight(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 64);
		long[] clock = {6_000L};
		Follows follows = new Follows().with((l, b) -> true, l -> clock[0]).followFor(200);
		Walker w = new Walker(f.level, f.vec(40.5, 0, 0.5));
		Sheep[] sheep = new Sheep[1];
		BlockPos[] stood = new BlockPos[1];
		f.whenReady(() -> {
			f.floor(56, Blocks.GRASS_BLOCK);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					for (int y = 0; y < 3; y++) {
						boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
						f.set(f.at(x, y, z), wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
					}
					f.set(f.at(x, 3, z), Blocks.OAK_PLANKS.defaultBlockState());
				}
			}
			f.set(f.at(2, 1, 0), Blocks.GLASS_PANE.defaultBlockState());
			w.base = f.at(0, 0, 0);
			sheep[0] = f.spawn(EntityTypes.SHEEP, -18.5, 14.5);
			sheep[0].setColor(net.minecraft.world.item.DyeColor.BLACK);
			stood[0] = sheep[0].blockPosition();
			helper.assertTrue(Follows.until(6_000L, 0.5) == 23_000L && Follows.until(1_000L, 0.5) == 13_000L + 10_000L && Follows.until(20_000L, 0.5) == 32_000L,
					"its time runs to dawn: " + Follows.until(6_000L, 0.5));
			helper.assertTrue(follows.start(sheep[0], w, 0.5, false), "the follow did not start");
			helper.assertTrue(follows.current().fromBase(), "their own animal was not known as theirs");
			helper.onEachTick(() -> follows.tick(f.level.getServer()));
		}).thenWaitUntil(() -> helper.assertTrue(follows.current().mode() == Follows.Mode.WAITING, "not gone home yet: " + follows.current().mode()))
				.thenExecute(() -> {
					helper.assertTrue(sheep[0].blockPosition().equals(stood[0]) && !TAMPER.isTampered(sheep[0]), "not back home: " + sheep[0].blockPosition());
					// Night, and they are home, indoors.
					clock[0] = 18_000L;
					w.pos = f.vec(0.5, 0, 0.5);
					w.outdoors = false;
				}).thenWaitUntil(() -> helper.assertTrue(TAMPER.isFrozen(sheep[0]), "not standing at the window")).thenExecute(() -> {
					double fromPane = Math.sqrt(Herds.flatSqr(sheep[0].position(), Vec3.atCenterOf(f.at(2, 1, 0))));
					helper.assertTrue(fromPane >= 2.5 && fromPane <= 5.8 && sheep[0].getX() > f.vec(2, 0, 0).x, "it stands " + fromPane + " from the pane");
					TamperState state = ((TamperedMob) sheep[0]).a1016_02$tamper();
					helper.assertTrue(state != null && state.faceTarget() != null && state.faceTarget().distanceTo(Vec3.atCenterOf(f.at(2, 1, 0))) < 0.5,
							"it does not face the window");
					clock[0] = 23_000L; // dawn
				}).thenWaitUntil(() -> helper.assertTrue(follows.current() == null, "still on at dawn")).thenExecute(() -> {
					helper.assertTrue(sheep[0].blockPosition().equals(stood[0]) && !TAMPER.isTampered(sheep[0]), "not back home at dawn: " + sheep[0].blockPosition());
					long left = TraceLedger.get(f.level.getServer()).entries().stream()
							.filter(e -> e.cause().equals(Follows.CAUSE) && e.entity().equals(Optional.of(sheep[0].getUUID()))).count();
					helper.assertTrue(left == 0, left + " ledger entries left for it");
					f.done();
				}).thenSucceed();
	}

	/** R11-14: the follower is the one easiest to know again: a sheep of a colour, else the odd one out of its kind. */
	@GameTest(maxTicks = 1500)
	public void theFollowerIsTheAnimalEasiestToKnowAgain(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 24);
		f.whenReady(() -> {
			f.floor(20, Blocks.GRASS_BLOCK);
			List<Mob> herd = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				herd.add(f.spawn(EntityTypes.SHEEP, -6.5 + i * 3, 4.5));
			}
			Sheep black = f.spawn(EntityTypes.SHEEP, 6.5, 4.5);
			black.setColor(net.minecraft.world.item.DyeColor.BLACK);
			herd.add(black);
			helper.assertTrue(FollowerCard.recognisable(black, herd) == 2 && FollowerCard.recognisable(herd.getFirst(), herd) == 0, "the black one");
			List<Mob> dark = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				Sheep s = f.spawn(EntityTypes.SHEEP, -6.5 + i * 3, -6.5);
				s.setColor(net.minecraft.world.item.DyeColor.BLACK);
				dark.add(s);
			}
			Sheep white = f.spawn(EntityTypes.SHEEP, 6.5, -6.5);
			dark.add(white);
			helper.assertTrue(FollowerCard.recognisable(white, dark) == 1, "a white one among black ones is the odd one: " + FollowerCard.recognisable(white, dark));
			f.done();
		}).thenSucceed();
	}

	/**
	 * R10-9: from Proximity at night, a herd in the target's own field (within 48 of their base) comes before a bigger herd
	 * elsewhere; before Proximity, or by day, the herd as before.
	 */
	@GameTest(maxTicks = 1500)
	public void fromProximityAtNightTheRingIsInTheirOwnField(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 96);
		Online online = new Online();
		f.whenReady(() -> {
			f.floor(90, Blocks.GRASS_BLOCK);
			Prey a = f.prey(0.5, 0.5);
			online.add(a);
			a.setRespawnPosition(new ServerPlayer.RespawnConfig(net.minecraft.world.level.storage.LevelData.RespawnData.of(Level.END, f.at(40, 0, 0), 0.0F, 0.0F),
					true), false);
			f.herd(EntityTypes.SHEEP, 8, 3.0, 40, 22);
			f.herd(EntityTypes.SHEEP, 12, 3.0, -42, 0);
			RingCard card = new RingCard();
			card.watch = () -> online.watch;
			card.timeOfDay = l -> 18_000L;
			card.stage = s -> Stage.PROXIMITY;
			Vec3 home = f.vec(40.5, 0, 0.5);
			helper.assertTrue(online.watch.base(a).isPresent(), "no base");
			RingCard.Plan plan = must(helper, card.plan(a, false), "a plan");
			helper.assertTrue(Herds.flatSqr(plan.middle(), home) <= RingCard.NEAR_BASE * RingCard.NEAR_BASE, "not their own field: " + plan.middle());
			card.stage = s -> Stage.TRACES;
			RingCard.Plan before = must(helper, card.plan(a, false), "a plan before Proximity");
			helper.assertTrue(Herds.flatSqr(before.middle(), home) > RingCard.NEAR_BASE * RingCard.NEAR_BASE, "the bigger herd before Proximity: " + before.middle());
			f.done();
		}).thenSucceed();
	}

	/**
	 * R11-13, R10-6, R12-34: a file is big and small in turn and passes in front of the target first; one followed walks on
	 * past its time; one whose place is not loaded keeps its walkers (out of view) instead of letting them go in the field.
	 */
	@GameTest(environment = ACTORS, maxTicks = 1500)
	public void aFileAlternatesBigAndSmallWalksOnWhileFollowedAndWaitsForItsPlace(GameTestHelper helper) {
		Field f = new Field(helper, Level.END, 48);
		boolean[] followed = {true};
		Processions processions = new Processions().with((l, b) -> true).followedBy((l, at) -> followed[0]);
		List<Mob> file = new ArrayList<>();
		Processions.Procession[] p = new Processions.Procession[1];
		Line line = new Line(f.vec(-30.5, 0, 0.5), f.vec(400.5, 0, 0.5), new Vec3(1, 0, 0));
		Vec3 far = f.vec(30_000.5, 0, 0.5);
		f.whenReady(() -> {
			f.floorRect(-40, -8, 40, 8, Blocks.GRASS_BLOCK);
			List<Mob> mixed = new ArrayList<>(List.of(f.spawn(EntityTypes.CHICKEN, -30.5, 0.5), f.spawn(EntityTypes.CHICKEN, -33.5, 0.5),
					f.spawn(EntityTypes.COW, -36.5, 0.5), f.spawn(EntityTypes.COW, -39.5, 0.5)));
			List<Mob> order = ProcessionCard.bigAndSmall(mixed);
			helper.assertTrue(order.get(0).getType() == EntityTypes.COW && order.get(1).getType() == EntityTypes.CHICKEN && order.get(2).getType() == EntityTypes.COW,
					"not big and small in turn: " + order.stream().map(m -> m.getType().toShortString()).toList());
			// The tangent that passes in front of the target first.
			Vec3 target = new Vec3(0, 64, 0);
			Vec3 dest = new Vec3(-300, 64, 300);
			Vec3 look = new Vec3(1, 0, 0);
			int first = ProcessionCard.sides(target, look, dest, 32, 56)[0];
			Line chosen = Line.of(target, dest, 32, 56, first).orElseThrow();
			Vec3 nearest = chosen.at(chosen.along(target));
			helper.assertTrue(nearest.x - target.x > 0, "the file passes behind: " + nearest);
			for (Mob mob : order) {
				file.add(mob);
			}
			p[0] = must(helper, processions.start(f.level, line, file, far, List.of(), 20, false), "the file");
			helper.onEachTick(() -> processions.tick(f.level.getServer()));
		}).thenIdle(100).thenExecute(() -> {
			helper.assertTrue(p[0].phase() == Processions.Phase.WALK, "it stopped walking while followed: " + p[0].phase());
			followed[0] = false;
		}).thenIdle(Processions.EVERY * 2).thenExecute(() -> {
			helper.assertTrue(p[0].phase() == Processions.Phase.GOING, "not going once nobody follows: " + p[0].phase());
		}).thenIdle(300).thenExecute(() -> {
			// Out of view for 10 s and more, but the place is not loaded: they walk on, held, never let go in the field.
			helper.assertTrue(p[0].mobs().size() == file.size() && p[0].moved() == 0, "let go before the place loaded: " + p[0].mobs().size());
			for (Mob mob : file) {
				helper.assertTrue(TAMPER.isSteered(mob), "a walker was let go");
			}
			processions.clear();
			f.done();
		}).thenSucceed();
	}

	/**
	 * R10-6, R10-9: 23 pointing at a place 300 or more off is remembered for the target, and their next file walks there
	 * (over a nearer place); from Telling, a target 200 or more from home is looked past toward their home.
	 */
	@GameTest(maxTicks = 1500)
	public void theWorldKeepsPointingAndFromTellingItLooksTowardHome(GameTestHelper helper) {
		Field f = new Field(helper, Level.OVERWORLD, 96);
		Online online = new Online();
		f.whenReady(() -> {
			f.floor(90, Blocks.GRASS_BLOCK);
			Prey a = f.prey(0.5, 0.5);
			online.add(a);
			for (int i = 0; i < 3; i++) {
				f.spawn(EntityTypes.COW, -20.5 - i * 3, 30.5);
				f.spawn(EntityTypes.SHEEP, -20.5 - i * 3, -30.5);
			}
			for (int i = 0; i < 8; i++) {
				f.spawn(EntityTypes.CHICKEN, 6.5 + i, 8.5);
			}
			Remains.Place near = new Remains.Place(Remains.Kind.PYRAMID, Level.OVERWORLD, new BoundingBox(f.at(-150, -5, -10).getX(), f.origin.getY() - 5,
					f.at(-150, 0, -10).getZ(), f.at(-130, 0, 10).getX(), f.origin.getY() + 30, f.at(-130, 0, 10).getZ()), f.at(-140, 0, 0), f.at(-130, 0, 0),
					Direction.EAST, Optional.empty(), 0L, 0);
			Remains.Place pointed = new Remains.Place(Remains.Kind.HOLLOW, Level.OVERWORLD, new BoundingBox(f.at(390, -5, -10).getX(), f.origin.getY() - 5,
					f.at(390, 0, -10).getZ(), f.at(410, 0, 10).getX(), f.origin.getY() + 30, f.at(410, 0, 10).getZ()), f.at(400, 0, 0), f.at(390, 0, 0),
					Direction.WEST, Optional.empty(), 0L, 0);
			Remains both = new Remains() {
				@Override
				public List<Remains.Place> known(ServerLevel level) {
					return List.of(near, pointed);
				}

				@Override
				public Optional<Remains.Place> containing(ServerLevel level, BlockPos pos) {
					return Optional.empty();
				}

				@Override
				public Optional<Remains.Place> nearest(ServerLevel level, BlockPos pos, Set<Remains.Kind> kinds, int radius) {
					return List.of(near, pointed).stream().filter(p -> kinds.contains(p.kind()) && p.distanceTo(pos) <= radius)
							.min(java.util.Comparator.comparingDouble(p -> p.distanceTo(pos)));
				}

				@Override
				public boolean visited(UUID player, Remains.Place p) {
					return false;
				}

				@Override
				public long trespassedAt(UUID player) {
					return -1;
				}
			};
			Sc2xData data = Sc2xData.get(f.level.getServer());
			data.clearPointed(a.getUUID());
			// 23 points at the hollow 400 off (only it is in the kinds' reach of its search here: the stub's nearest of all).
			HeldBreathCard held = card23(online, new ArrayList<>(), new HeldBreath());
			held.remains = () -> new Remains() {
				@Override
				public List<Remains.Place> known(ServerLevel level) {
					return List.of(pointed);
				}

				@Override
				public Optional<Remains.Place> containing(ServerLevel level, BlockPos pos) {
					return Optional.empty();
				}

				@Override
				public Optional<Remains.Place> nearest(ServerLevel level, BlockPos pos, Set<Remains.Kind> kinds, int radius) {
					return Optional.of(pointed);
				}

				@Override
				public boolean visited(UUID player, Remains.Place p) {
					return false;
				}

				@Override
				public long trespassedAt(UUID player) {
					return -1;
				}
			};
			held.stage = s -> Stage.PROXIMITY;
			helper.assertTrue(held.fire(new FireContext(a, f.level, false, true, RandomSource.create(3))) == FireResult.FIRED, "23 did not fire");
			helper.assertTrue(data.pointed(a.getUUID()).map(v -> v.distanceTo(Vec3.atCenterOf(pointed.anchor())) < 1.0).orElse(false),
					"not remembered: " + data.pointed(a.getUUID()));
			held.breaths.clear(); // the animals it held are free again for the file
			// The next file walks to the place it pointed at, not the nearer one.
			ProcessionCard card = new ProcessionCard();
			card.remains = () -> both;
			card.outOfView = (l, b) -> true;
			Optional<ProcessionCard.Plan> plan = card.plan(a, 32.0);
			helper.assertTrue(plan.isPresent() && plan.get().place() == pointed, "the file goes elsewhere: " + plan.map(ProcessionCard.Plan::place));
			data.clearPointed(a.getUUID());
			Optional<ProcessionCard.Plan> plain = card.plan(a, 32.0);
			helper.assertTrue(plain.isPresent() && plain.get().place() == near, "without a pointing, the nearest");
			// From Telling, 300 from home: the bearing is home.
			a.setRespawnPosition(new ServerPlayer.RespawnConfig(net.minecraft.world.level.storage.LevelData.RespawnData.of(Level.OVERWORLD, f.at(-300, 0, 0), 0.0F, 0.0F),
					true), false);
			held.stage = s -> Stage.TELLING;
			helper.assertTrue(held.bearing(a).from() == HeldBreath.From.HOME, "not toward home from Telling: " + held.bearing(a).from());
			held.stage = s -> Stage.PROXIMITY;
			helper.assertTrue(held.bearing(a).from() != HeldBreath.From.HOME, "toward home before Telling");
			held.breaths.clear();
			f.done();
		}).thenSucceed();
	}

	// --- helpers ---

	/** The value, asserted present (an exception other than a failed assertion would stop the test server). */
	private static <T> T must(GameTestHelper helper, Optional<T> value, String what) {
		helper.assertTrue(value.isPresent(), "no " + what);
		return value.get();
	}

	private static boolean noGoalRuns(Mob mob) {
		for (WrappedGoal goal : mob.getGoalSelector().getAvailableGoals()) {
			if (goal.isRunning()) {
				return false;
			}
		}
		return true;
	}

	/** A test's own players and state (nobody's participation is reported anywhere). */
	static final class Online implements PlayerWatch.Source {
		final List<ServerPlayer> online = new ArrayList<>();
		final HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);

		void add(ServerPlayer... players) {
			online.addAll(List.of(players));
		}

		@Override
		public List<ServerPlayer> online(MinecraftServer server) {
			return online;
		}

		@Override
		public HerobrineState state(MinecraftServer server) {
			return state;
		}

		@Override
		public void participation(MinecraftServer server, UUID player, boolean participating) {
		}
	}

	/** A player standing in a level, never joined, in survival. */
	static final class Prey extends ServerPlayer {
		Prey(ServerLevel level) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player"), ClientInformation.createDefault());
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

		@Override
		public void onEnterCombat() {
		}

		@Override
		public void onLeaveCombat() {
		}

		@Override
		public void indicateDamage(double xd, double zd) {
		}

		// Vanilla asks the client connection whether it has loaded; there is none.
		@Override
		public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
			return isSpectator() || isCreative();
		}
	}

	/**
	 * A patch of a level far out on this class's own band (x from -18 million, z from +17.6 million, each test its own
	 * slot 1024 apart along x), its chunks held loaded while it runs (an x reach and a z reach), a floor at its origin's y,
	 * mobs it made discarded at the end.
	 */
	static final class Field {
		private static final int SPACING = 1024;
		private static final AtomicInteger SLOTS = new AtomicInteger();
		private static final int BASE_X = 16 * ThreadLocalRandom.current().nextInt(-1_200_000, -1_130_000);
		private static final int BASE_Z = 16 * ThreadLocalRandom.current().nextInt(1_100_000, 1_150_000);

		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		private final int reachX;
		private final int reachMinX;
		private final int reachZ;
		private final List<Entity> made = new ArrayList<>();

		Field(GameTestHelper helper, ResourceKey<Level> dimension, int reach) {
			this(helper, dimension, reach, dimension == Level.OVERWORLD ? 200 : 60);
		}

		Field(GameTestHelper helper, ResourceKey<Level> dimension, int reach, int y) {
			this(helper, dimension, reach, reach, reach, y);
		}

		/** Reaching {@code reachX} east, {@code reachMinX} west and {@code reachZ} either way. */
		Field(GameTestHelper helper, ResourceKey<Level> dimension, int reachX, int reachMinX, int reachZ) {
			this(helper, dimension, reachX, reachMinX, reachZ, 60);
		}

		private Field(GameTestHelper helper, ResourceKey<Level> dimension, int reachX, int reachMinX, int reachZ, int y) {
			this.helper = helper;
			this.level = helper.getLevel().getServer().getLevel(dimension);
			this.origin = new BlockPos(BASE_X + SLOTS.getAndIncrement() * SPACING, y, BASE_Z);
			this.reachX = reachX;
			this.reachMinX = reachMinX;
			this.reachZ = reachZ;
			force(true);
		}

		private void force(boolean on) {
			for (int cx = (origin.getX() - reachMinX) >> 4; cx <= (origin.getX() + reachX) >> 4; cx++) {
				for (int cz = (origin.getZ() - reachZ) >> 4; cz <= (origin.getZ() + reachZ) >> 4; cz++) {
					level.setChunkForced(cx, cz, on);
					if (on) {
						level.getChunk(cx, cz);
					}
				}
			}
		}

		boolean ready() {
			for (int cx = (origin.getX() - reachMinX) >> 4; cx <= (origin.getX() + reachX) >> 4; cx++) {
				for (int cz = (origin.getZ() - reachZ) >> 4; cz <= (origin.getZ() + reachZ) >> 4; cz++) {
					if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
						return false;
					}
				}
			}
			return true;
		}

		/**
		 * Runs {@code then} on the first tick every chunk of the patch ticks its entities. First waits in real time (at most
		 * 30 s) until its chunks are entity-ticking, as the gallery's own staged walk does: a test server's ticks race the
		 * chunk system, so a big patch is otherwise not ready within a test's ticks in a full run; then the entities load,
		 * waited for in real time too ({@code core.FarPatches}, SC2-9: on a busy machine they took longer than the test's
		 * ticks).
		 */
		GameTestSequence whenReady(Runnable then) {
			return helper.startSequence().thenExecute(() -> {
				long until = Util.getMillis() + 30_000L;
				level.getServer().managedBlock(() -> ticking() || Util.getMillis() > until);
			}).thenWaitUntil(com.forzacode.a1016_02.core.FarPatches.until(helper, this::ready, "the patch")).thenExecute(then);
		}

		/** Every chunk of the patch is entity-ticking (by its tickets; its entities may still be loading). */
		boolean ticking() {
			for (int cx = (origin.getX() - reachMinX) >> 4; cx <= (origin.getX() + reachX) >> 4; cx++) {
				for (int cz = (origin.getZ() - reachZ) >> 4; cz <= (origin.getZ() + reachZ) >> 4; cz++) {
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

		/** A floor of {@code top} over dirt round the origin, {@code r} either way, with 4 of air over it. */
		void floor(int r, Block top) {
			floorRect(-r, -r, r, r, top);
		}

		void floorRect(int x1, int z1, int x2, int z2, Block top) {
			BlockState air = Blocks.AIR.defaultBlockState();
			for (int x = x1; x <= x2; x++) {
				for (int z = z1; z <= z2; z++) {
					set(at(x, -2, z), Blocks.DIRT.defaultBlockState());
					set(at(x, -1, z), top.defaultBlockState());
					for (int y = 0; y < 4; y++) {
						set(at(x, y, z), air);
					}
				}
			}
		}

		/** Nothing over the cell at (x, z) but air (the floor is there): where the file goes in. */
		void cellAt(int x, int z) {
			set(at(x, 0, z), Blocks.AIR.defaultBlockState());
		}

		<T extends Mob> T spawn(EntityType<T> type, double x, double z) {
			T mob = type.create(level, EntitySpawnReason.COMMAND);
			helper.assertTrue(mob != null, "could not make a " + type);
			mob.snapTo(vec(x, 0, z), ThreadLocalRandom.current().nextFloat() * 360.0F, 0.0F);
			mob.setPersistenceRequired();
			helper.assertTrue(level.addFreshEntity(mob), "could not add a " + type);
			made.add(mob);
			return mob;
		}

		/** {@code n} of a kind, loosely round (x, z) within {@code spread}. */
		<T extends Mob> List<T> herd(EntityType<T> type, int n, double spread, double x, double z) {
			List<T> list = new ArrayList<>();
			RandomSource random = RandomSource.create(n * 31L + (long) x);
			for (int i = 0; i < n; i++) {
				list.add(spawn(type, x + 0.5 + (random.nextDouble() * 2 - 1) * spread, z + 0.5 + (random.nextDouble() * 2 - 1) * spread));
			}
			return list;
		}

		Prey prey(double x, double z) {
			Prey prey = new Prey(level);
			prey.snapTo(vec(x, 0, z), 0.0F, 0.0F);
			return prey;
		}

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
