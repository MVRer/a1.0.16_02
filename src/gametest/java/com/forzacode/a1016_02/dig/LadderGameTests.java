package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Someone behind you, the tunnel ladder (D-072): the detector, the rung order and spacing, the steps (they approach,
 * stop 4 to 6 back, stop at once when the player turns back or a friend comes close, and are never heard 60 blocks
 * away), the used runs, the glimpse through the figure (a recording one, {@link LadderRig}), and that nothing in the
 * world ever changes. Each test has its own crew, memory and clock; none of their players joins the server. The root
 * of the dig test classes ({@link DigMultiplayerTests} extends it).
 */
public class LadderGameTests {
	/**
	 * The detector finds a straight tunnel players dug (a torch in it, a 2-wide one, a strip mine with a branch every 2
	 * cells) behind the player along their heading, and refuses a natural cave, a bend, water in it or beside it, a run
	 * too short, and walls open to a cave along it.
	 */
	@GameTest(maxTicks = 100)
	public void detectorFindsADugTunnelAndRefusesCavesBendsWaterAndShortRuns(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 16, 48, 8, 56, Blocks.STONE);
		LadderRig rig = new LadderRig(helper, g, "Alex");
		DigConfig config = rig.config;
		int minRun = config.ladderMinRun;
		double share = config.ladderMinSealedShare;
		// A straight dug tunnel with a torch the player placed in it.
		rig.tunnelX(2, 31, 3, 3);
		g.place(g.at(20, 3, 3), Blocks.TORCH.defaultBlockState());
		Optional<TunnelRun> run = TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 3), Direction.EAST, minRun, share);
		helper.assertTrue(run.isPresent(), "a straight dug tunnel was not found");
		TunnelRun found = run.get();
		helper.assertTrue(found.length() == 29 && found.heading() == Direction.EAST && found.back() == Direction.WEST, "run " + found.length()
				+ " heading " + found.heading());
		helper.assertTrue(found.cell(1).equals(g.at(30, 3, 3)) && found.start().equals(g.at(2, 3, 3)) && found.sealed() == 29, "cells "
				+ found.cell(1) + " to " + found.start() + ", sealed " + found.sealed());
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 3), Direction.WEST, minRun, share).isEmpty(),
				"a run was found behind a player facing back down their tunnel (the face is behind them)");

		// A natural cave of the same shape: nobody dug it.
		g.hollow(2, 3, 9, 31, 4, 9);
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 9), Direction.EAST, minRun, share).isEmpty(), "a natural cave counted");

		// A bend: 22 cells dug, but only 11 straight behind the player.
		rig.tunnelX(30, 40, 3, 15);
		g.digBox(40, 3, 16, 40, 4, 25);
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(40, 3, 25), Direction.SOUTH, minRun, share).isEmpty(), "a bend counted");
		Optional<TunnelRun> leg = TunnelRun.detect(g.level, rig.crew.watch, g.at(40, 3, 25), Direction.SOUTH, 10, share);
		helper.assertTrue(leg.isPresent() && leg.get().length() == 10, "the bend's last leg is not 10 straight cells: " + leg.map(TunnelRun::length));

		// Water in the tunnel, and water beside it.
		rig.tunnelX(2, 31, 3, 21);
		g.level.setBlock(g.at(20, 3, 21), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 21), Direction.EAST, minRun, share).isEmpty(), "water in it counted");
		rig.tunnelX(2, 31, 3, 27);
		g.level.setBlock(g.at(24, 3, 28), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 27), Direction.EAST, minRun, share).isEmpty(), "water beside it counted");

		// A short run.
		rig.tunnelX(2, 13, 3, 33);
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(13, 3, 33), Direction.EAST, minRun, share).isEmpty(), "11 cells counted");
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(13, 3, 33), Direction.EAST, 10, share).map(TunnelRun::length).orElse(0) == 11,
				"a short run is not 11 cells");

		// A dug tunnel along a natural cave: its walls are open on every cell.
		rig.tunnelX(2, 31, 3, 39);
		g.hollow(2, 3, 40, 31, 4, 40);
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 39), Direction.EAST, minRun, share).isEmpty(),
				"a tunnel open to a cave along its whole length counted");

		// A 2-wide dug tunnel has sealed walls too.
		g.digBox(2, 3, 44, 31, 4, 45);
		Optional<TunnelRun> wide = TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 44), Direction.EAST, minRun, share);
		helper.assertTrue(wide.isPresent() && wide.get().length() == 29 && wide.get().sealed() == 29, "a 2-wide tunnel: " + wide.map(TunnelRun::sealed));

		// A strip mine: a branch off both sides every 2 cells, so about half its cells are open. It counts at the default
		// share (0.45), and would not have at 0.6.
		rig.tunnelX(2, 31, 3, 51);
		for (int x = 3; x <= 29; x += 2) {
			g.digBox(x, 3, 49, x, 4, 50);
			g.digBox(x, 3, 52, x, 4, 53);
		}
		Optional<TunnelRun> strip = TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 51), Direction.EAST, minRun, share);
		helper.assertTrue(strip.isPresent() && strip.get().length() == 29 && strip.get().sealed() == 15, "a strip mine with a branch every 2 cells: "
				+ strip.map(TunnelRun::sealed));
		helper.assertTrue(TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 51), Direction.EAST, minRun, 0.6).isEmpty(), "the strip mine at 0.6");
		helper.succeed();
	}

	/** The heading is the axis of the last 10 s of movement, else the look snapped to an axis. */
	@GameTest
	public void headingFollowsMovementElseTheLook(GameTestHelper helper) {
		List<Vec3> east = List.of(new Vec3(0, 64, 0), new Vec3(1.5, 64, 0.2), new Vec3(4, 64, 0.5));
		helper.assertTrue(TunnelRun.heading(east, LadderRig.NORTH) == Direction.EAST, "moving east, looking north");
		List<Vec3> north = List.of(new Vec3(0, 64, 0), new Vec3(0.3, 64, -3));
		helper.assertTrue(TunnelRun.heading(north, LadderRig.EAST) == Direction.NORTH, "moving north, looking east");
		List<Vec3> still = List.of(new Vec3(0, 64, 0), new Vec3(0.5, 64, 0.5));
		helper.assertTrue(TunnelRun.heading(still, LadderRig.WEST) == Direction.WEST, "barely moving: not the look");
		helper.assertTrue(TunnelRun.heading(List.of(), LadderRig.SOUTH) == Direction.SOUTH, "no samples: not the look");
		helper.succeed();
	}

	/**
	 * Rung order and spacing on the player's own clock: rung 1 once and first; rung 2 after it, at least 25 minutes
	 * after any ladder event; rung 3 after rung 2 (20 minutes after it at least), once per in-game day and 2 h apart;
	 * rungs 2 and 3 repeat. A forced fire skips the order and spacing (and does not count for them), never the gates.
	 */
	@GameTest(maxTicks = 100)
	public void rungsComeInOrderWithTheirSpacing(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 17, 112, 8, 9, Blocks.STONE);
		LadderRig rig = new LadderRig(helper, g, "Alex");
		rig.tunnelX(2, 105, 3, 4);
		ServerPlayer alex = rig.stand("Alex", 40, 3, 4, LadderRig.EAST);
		helper.assertTrue(rig.check(Ladder.Rung.ONCE, alex).fits(), "rung 1 does not fit a fresh player: " + rig.check(Ladder.Rung.ONCE, alex).why());
		helper.assertTrue(rig.check(Ladder.Rung.COMING, alex).why() == Ladder.Why.RUNG, "rung 2 before rung 1");
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).why() == Ladder.Why.RUNG, "rung 3 before rung 2");
		helper.assertTrue(rig.fire(Ladder.Rung.ONCE, alex, false, false, 1L) == FireResult.FIRED, "rung 1 did not fire");
		long once = rig.playTicks;
		helper.assertTrue(rig.memory(alex).rung == 1 && rig.memory(alex).lastEvent == once, "rung 1 not remembered");
		helper.assertTrue(rig.check(Ladder.Rung.ONCE, alex).why() == Ladder.Why.RUNG, "rung 1 fits twice");
		helper.assertTrue(rig.check(Ladder.Rung.COMING, alex).why() == Ladder.Why.SPACING, "rung 2 right after rung 1");
		rig.playTicks = once + LadderRig.minutes(24);
		helper.assertTrue(rig.check(Ladder.Rung.COMING, alex).why() == Ladder.Why.SPACING, "rung 2 24 minutes after rung 1");
		rig.playTicks = once + LadderRig.minutes(26);
		rig.memory(alex).usedRuns.clear(); // the used runs have their own test
		helper.assertTrue(rig.check(Ladder.Rung.COMING, alex).fits(), "rung 2 26 minutes after rung 1: " + rig.check(Ladder.Rung.COMING, alex).why());
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).why() == Ladder.Why.RUNG, "rung 3 after rung 1 only");
		helper.assertTrue(rig.fire(Ladder.Rung.COMING, alex, false, false, 2L) == FireResult.FIRED, "rung 2 did not fire");
		long coming = rig.playTicks;
		helper.assertTrue(rig.memory(alex).rung == 2 && rig.memory(alex).lastComing == coming, "rung 2 not remembered");
		rig.memory(alex).usedRuns.clear();
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).why() == Ladder.Why.SPACING, "rung 3 right after rung 2");
		rig.config.ladderGapMinutes = 10;
		rig.playTicks = coming + LadderRig.minutes(15);
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).why() == Ladder.Why.COMING_TOO_RECENT, "rung 3 15 minutes after rung 2");
		rig.config.ladderGapMinutes = new DigConfig().ladderGapMinutes;
		rig.playTicks = coming + LadderRig.minutes(25);
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).fits(), "rung 3 25 minutes after rung 2: " + rig.check(Ladder.Rung.GLIMPSE, alex).why());
		helper.assertTrue(rig.fire(Ladder.Rung.GLIMPSE, alex, false, false, 3L) == FireResult.FIRED, "rung 3 did not fire");
		long glimpse = rig.playTicks;
		helper.assertTrue(rig.memory(alex).rung == 3 && rig.memory(alex).lastGlimpse == glimpse && rig.memory(alex).glimpseDay == 0, "rung 3 not remembered");
		rig.playTicks = glimpse + LadderRig.minutes(30);
		rig.memory(alex).usedRuns.clear();
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).why() == Ladder.Why.GLIMPSE_TODAY, "two glimpses in one in-game day");
		helper.assertTrue(rig.check(Ladder.Rung.COMING, alex).fits(), "rung 2 does not repeat: " + rig.check(Ladder.Rung.COMING, alex).why());
		rig.day = 1;
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).why() == Ladder.Why.GLIMPSE_SPACING, "glimpses 30 minutes of play apart");
		rig.playTicks = glimpse + ModConfig.realTicks(rig.config.ladderGlimpseGapHours * 3600);
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).fits(), "rung 3 does not repeat: " + rig.check(Ladder.Rung.GLIMPSE, alex).why());

		// Forced: rung order and spacing skipped, nothing counted for them; the gates still hold.
		helper.assertTrue(rig.fire(Ladder.Rung.COMING, alex, false, false, 4L) == FireResult.FIRED, "rung 2 did not repeat");
		long last = rig.playTicks;
		rig.memory(alex).usedRuns.clear();
		helper.assertTrue(rig.check(Ladder.Rung.GLIMPSE, alex).why() == Ladder.Why.SPACING, "rung 3 right after rung 2 again");
		helper.assertTrue(rig.fire(Ladder.Rung.GLIMPSE, alex, false, true, 5L) == FireResult.FIRED, "a forced rung 3 waited for the spacing");
		helper.assertTrue(rig.memory(alex).lastEvent == last && rig.memory(alex).lastGlimpse == glimpse && rig.memory(alex).usedRuns.isEmpty(),
				"a forced fire counted for the spacing or the used runs");
		rig.underground = false;
		helper.assertTrue(rig.fire(Ladder.Rung.ONCE, alex, false, true, 6L) == FireResult.SKIPPED, "a forced fire skipped the underground rule");
		rig.underground = true;
		rig.stand("Alex", 10, 3, 4, LadderRig.EAST);
		helper.assertTrue(rig.fire(Ladder.Rung.ONCE, alex, false, true, 7L) == FireResult.SKIPPED, "a forced fire skipped the tunnel rule (8 cells behind)");
		LadderRig fresh = new LadderRig(helper, g, "Kim");
		ServerPlayer kim = fresh.stand("Kim", 90, 3, 4, LadderRig.EAST);
		helper.assertTrue(fresh.fire(Ladder.Rung.GLIMPSE, kim, false, true, 8L) == FireResult.FIRED, "a forced rung 3 waited for the rung order");
		helper.assertTrue(fresh.memory(kim).rung == 3 && fresh.memory(kim).lastEvent == Long.MIN_VALUE && fresh.memory(kim).glimpseDay == Long.MIN_VALUE,
				"a forced rung 3 was not remembered for the order only");
		// The debug command's lines (/a1016 dig steps): the memory, the detector, each rung's answer, rung 3's lights and
		// each of its candidates, the steps and the arm.
		List<String> lines = Ladder.debugLines(kim, fresh.env());
		helper.assertTrue(lines.size() == 10 && lines.get(2).startsWith("detector: a run of 32 cells") && lines.get(5).startsWith(Ladder.Rung.GLIMPSE.id)
				&& lines.get(6).startsWith("steps_behind_glimpse lights 6 to 24 back: ") && lines.get(7).startsWith("steps_behind_glimpse spots (14 to 20 back): 14 ")
				&& lines.get(9).equals("armed: nothing"), "debug lines: " + lines);
		helper.succeed();
	}

	/**
	 * Rung 2's steps: the floor block's own step sound (deepslate here), PLAYERS, one every 8 ticks, from 16 back closing
	 * to 4 to 6, louder as they near and never above volume 1. A friend 60 blocks away is not sharing the moment, and
	 * hears none of them. The sneaking variant: every 13 ticks, quieter.
	 */
	@GameTest(maxTicks = 400)
	public void stepsApproachAndStopFourToSixBackUnheardSixtyAway(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 18, 40, 8, 20, Blocks.STONE);
		g.keepLoaded(true); // nobody's there to keep the tunnel loaded while the steps come
		LadderRig walk = new LadderRig(helper, g, "Alex", "Sam");
		walk.config.ladderSneakChance = 0;
		walk.tunnelX(2, 35, 3, 4);
		for (int x = 2; x <= 35; x++) {
			g.level.setBlock(g.at(x, 2, 4), Blocks.DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		ServerPlayer alex = walk.stand("Alex", 34, 3, 4, LadderRig.EAST);
		ServerPlayer sam = walk.stand("Sam", 34 - 60, 3, 4, LadderRig.EAST);
		helper.assertTrue(walk.fire(Ladder.Rung.COMING, alex, false, true, 11L) == FireResult.FIRED, "rung 2 did not fire with a friend 60 away: "
				+ Ladder.check(Ladder.Rung.COMING, alex, walk.env(), true).why());

		LadderRig sneak = new LadderRig(helper, g, "Kim");
		sneak.config.ladderSneakChance = 1;
		sneak.tunnelX(2, 35, 3, 12);
		ServerPlayer kim = sneak.stand("Kim", 34, 3, 12, LadderRig.EAST);
		helper.assertTrue(sneak.fire(Ladder.Rung.COMING, kim, false, true, 11L) == FireResult.FIRED, "the sneaking rung 2 did not fire");

		helper.succeedWhen(() -> {
			helper.assertFalse(LadderRig.walking(alex) || LadderRig.walking(kim), "the steps are still coming");
			assertApproach(helper, walk, alex, walk.config.ladderStepTicks, 1.0F);
			assertApproach(helper, sneak, kim, sneak.config.ladderSneakStepTicks, sneak.config.ladderSneakVolumeFactor);
			for (LadderRig.Heard step : walk.heard) {
				helper.assertTrue(step.sound().value() == SoundEvents.DEEPSLATE_STEP, "not the floor's step sound: " + step.sound());
				helper.assertTrue(SoundCues.hearers(g.level, List.of(sam), step.pos(), step.volume()).isEmpty(), "the friend 60 away heard a step");
				helper.assertTrue(SoundCues.hearers(g.level, List.of(alex), step.pos(), step.volume()).contains(alex), "Alex could not hear a step");
			}
			g.keepLoaded(false);
		});
	}

	private static void assertApproach(GameTestHelper helper, LadderRig rig, ServerPlayer player, int cadence, float factor) {
		DigConfig config = rig.config;
		List<LadderRig.Heard> heard = rig.heard;
		helper.assertTrue(heard.size() >= config.ladderComingStepsMin && heard.size() <= config.ladderComingStepsMax, heard.size() + " steps");
		int previous = Integer.MAX_VALUE;
		float louder = 0;
		for (int i = 0; i < heard.size(); i++) {
			LadderRig.Heard step = heard.get(i);
			int back = LadderRig.backAlongX(player, step.pos());
			helper.assertTrue(step.pos().y == player.getY() && step.pos().z == player.getZ() && back > 0, "a step off the tunnel behind: " + step.pos());
			helper.assertTrue(back < previous, "the steps do not keep coming: " + back + " after " + previous);
			if (i > 0) {
				// The first step plays in the first tick of the walk (the fire's own, or the next); then one every cadence.
				long gap = step.tick() - heard.get(i - 1).tick();
				helper.assertTrue(i == 1 ? gap >= cadence - 1 && gap <= cadence : gap == cadence, "step " + i + " came " + gap + " ticks after the one before");
			}
			helper.assertTrue(step.source() == SoundSource.PLAYERS, "not a players' sound");
			double distance = step.pos().distanceTo(player.position());
			float expected = Ladder.stepVolume(distance, factor, config);
			helper.assertTrue(Math.abs(step.volume() - expected) < 1.0E-4, "volume " + step.volume() + ", expected " + expected);
			double ear = GlimpseFeedbackTests.gain(step.volume(), distance);
			helper.assertTrue(step.volume() > 0 && step.volume() <= Math.max(config.ladderVolumeFar, config.ladderVolumeNear) + 1.0E-6 && ear >= louder - 1.0E-9,
					"volume " + step.volume() + " (" + ear + " at the ear) after " + louder);
			previous = back;
			louder = (float) ear;
		}
		int first = LadderRig.backAlongX(player, heard.getFirst().pos());
		int last = LadderRig.backAlongX(player, heard.getLast().pos());
		helper.assertTrue(first == config.ladderComingFrom, "they start " + first + " back");
		helper.assertTrue(last >= config.ladderComingStopMin && last <= config.ladderComingStopMax, "they stop " + last + " back");
	}

	/**
	 * Turning to face back down the tunnel (within 70 degrees) stops the rest of the steps at once; looking to the side
	 * does not.
	 */
	@GameTest(maxTicks = 300)
	public void turningAroundStopsTheRestOfTheSteps(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 19, 40, 8, 20, Blocks.STONE);
		g.keepLoaded(true);
		LadderRig back = new LadderRig(helper, g, "Alex");
		back.config.ladderSneakChance = 0;
		back.tunnelX(2, 35, 3, 4);
		ServerPlayer alex = back.stand("Alex", 34, 3, 4, LadderRig.EAST);
		LadderRig side = new LadderRig(helper, g, "Bea");
		side.config.ladderSneakChance = 0;
		side.tunnelX(2, 35, 3, 12);
		ServerPlayer bea = side.stand("Bea", 34, 3, 12, LadderRig.EAST);
		helper.assertTrue(back.fire(Ladder.Rung.COMING, alex, false, true, 21L) == FireResult.FIRED, "rung 2 did not fire for Alex");
		helper.assertTrue(side.fire(Ladder.Rung.COMING, bea, false, true, 21L) == FireResult.FIRED, "rung 2 did not fire for Bea");
		long[] turned = {Long.MAX_VALUE};
		helper.runAfterDelay(10, () -> {
			LadderRig.look(alex, LadderRig.WEST + 60.0F); // 60 degrees off straight back
			LadderRig.look(bea, LadderRig.NORTH); // to the side, 90 degrees off
			turned[0] = g.level.getServer().getTickCount();
		});
		helper.runAfterDelay(200, () -> {
			helper.assertFalse(LadderRig.walking(alex) || LadderRig.walking(bea), "the steps never ended");
			helper.assertTrue(!back.heard.isEmpty() && back.heard.size() <= 2, "Alex turned after the second step and heard " + back.heard.size());
			helper.assertTrue(back.heard.stream().allMatch(step -> step.tick() <= turned[0]), "a step played after Alex turned");
			helper.assertTrue(side.heard.size() >= side.config.ladderComingStepsMin, "looking to the side stopped Bea's steps: " + side.heard.size());
			int last = LadderRig.backAlongX(bea, side.heard.getLast().pos());
			helper.assertTrue(last >= side.config.ladderComingStopMin && last <= side.config.ladderComingStopMax, "Bea's steps stop " + last + " back");
			g.keepLoaded(false);
			helper.succeed();
		});
	}

	/**
	 * Alone means alone: with a friend within 48 blocks no rung fits and even a forced one is skipped; a friend coming
	 * close while the steps come stops the rest.
	 */
	@GameTest(maxTicks = 300)
	public void aFriendWithinTheSharedRadiusMeansNoSteps(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 20, 40, 8, 10, Blocks.STONE);
		g.keepLoaded(true);
		LadderRig rig = new LadderRig(helper, g, "Alex", "Sam");
		rig.config.ladderSneakChance = 0;
		rig.tunnelX(2, 35, 3, 4);
		ServerPlayer alex = rig.stand("Alex", 34, 3, 4, LadderRig.EAST);
		ServerPlayer sam = rig.stand("Sam", 34, 3, 4 + 30, LadderRig.EAST);
		for (Ladder.Rung rung : Ladder.Rung.values()) {
			helper.assertTrue(rig.check(rung, alex).why() == Ladder.Why.NOT_ALONE, rung.id + " with a friend 30 away: " + rig.check(rung, alex).why());
			helper.assertTrue(rig.fire(rung, alex, false, true, 31L) == FireResult.SKIPPED, "a forced " + rung.id + " with a friend 30 away");
		}
		helper.assertTrue(!LadderRig.walking(alex) && rig.heard.isEmpty(), "steps with a friend 30 away");
		LadderRig.move(sam, g.at(34, 3, 4 + 60));
		helper.assertTrue(rig.check(Ladder.Rung.ONCE, alex).fits(), "rung 1 with the friend 60 away: " + rig.check(Ladder.Rung.ONCE, alex).why());
		helper.assertTrue(rig.fire(Ladder.Rung.COMING, alex, false, true, 32L) == FireResult.FIRED, "rung 2 with the friend 60 away");
		long[] came = {Long.MAX_VALUE};
		helper.runAfterDelay(10, () -> {
			LadderRig.move(sam, g.at(34, 3, 4 + 20));
			came[0] = g.level.getServer().getTickCount();
		});
		helper.runAfterDelay(200, () -> {
			helper.assertFalse(LadderRig.walking(alex), "the steps never ended");
			helper.assertTrue(!rig.heard.isEmpty() && rig.heard.stream().allMatch(step -> step.tick() <= came[0]), "a step played once the friend came close");
			g.keepLoaded(false);
			helper.succeed();
		});
	}

	/**
	 * A spectating friend shares no moment, but the steps reach everyone in range ({@link SoundCues#playAt}), so they
	 * would hear every one and could confirm it: with a spectator within 48 blocks no rung fits and even a forced one
	 * (or a fake) is skipped; a spectator coming that close while the steps come stops the rest. One 60 away changes
	 * nothing.
	 */
	@GameTest(maxTicks = 300)
	public void aSpectatorWithinTheSharedRadiusMeansNoSteps(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 27, 40, 8, 10, Blocks.STONE);
		g.keepLoaded(true);
		LadderRig rig = new LadderRig(helper, g, "Alex", "Sam");
		rig.config.ladderSneakChance = 0;
		rig.tunnelX(2, 35, 3, 4);
		ServerPlayer alex = rig.stand("Alex", 34, 3, 4, LadderRig.EAST);
		Crew.Member sam = (Crew.Member) rig.stand("Sam", 34, 3, 4 + 30, LadderRig.EAST);
		sam.setMode(GameType.SPECTATOR);
		helper.assertTrue(sam.isSpectator() && rig.crew.watch.sharingWith(alex).size() == 1, "the spectator shares the moment");
		for (Ladder.Rung rung : Ladder.Rung.values()) {
			helper.assertTrue(rig.check(rung, alex).why() == Ladder.Why.NOT_ALONE, rung.id + " with a spectator 30 away: " + rig.check(rung, alex).why());
			helper.assertTrue(rig.fire(rung, alex, false, true, 91L) == FireResult.SKIPPED, "a forced " + rung.id + " with a spectator 30 away");
			helper.assertTrue(rig.fire(rung, alex, true, true, 92L) == FireResult.SKIPPED, "a forced fake " + rung.id + " with a spectator 30 away");
		}
		helper.assertTrue(!LadderRig.walking(alex) && rig.heard.isEmpty(), "steps with a spectator 30 away");
		LadderRig.move(sam, g.at(34, 3, 4 + 60));
		helper.assertTrue(rig.check(Ladder.Rung.ONCE, alex).fits(), "rung 1 with the spectator 60 away: " + rig.check(Ladder.Rung.ONCE, alex).why());
		helper.assertTrue(rig.fire(Ladder.Rung.COMING, alex, false, true, 93L) == FireResult.FIRED, "rung 2 with the spectator 60 away");
		LadderSteps.Walk walk = DigTicker.INSTANCE.ladder().walk(alex.getUUID()).orElseThrow();
		long[] came = {Long.MAX_VALUE};
		helper.runAfterDelay(10, () -> {
			LadderRig.move(sam, g.at(34, 3, 4 + 20));
			came[0] = g.level.getServer().getTickCount();
		});
		helper.runAfterDelay(200, () -> {
			helper.assertFalse(LadderRig.walking(alex), "the steps never ended");
			helper.assertTrue(!rig.heard.isEmpty() && rig.heard.stream().allMatch(step -> step.tick() <= came[0]), "a step played once the spectator came close");
			helper.assertTrue("someone came close".equals(walk.stopped) && walk.played() < walk.steps.size(), "the steps did not stop for the spectator: "
					+ walk.describe());
			g.keepLoaded(false);
			helper.succeed();
		});
	}

	/** A run whose start is within 24 blocks of a used one never fires again; farther along the same tunnel it may. */
	@GameTest(maxTicks = 100)
	public void theSameRunNeverFiresTwice(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 21, 80, 8, 9, Blocks.STONE);
		LadderRig rig = new LadderRig(helper, g, "Alex");
		rig.tunnelX(2, 75, 3, 4);
		ServerPlayer alex = rig.stand("Alex", 40, 3, 4, LadderRig.EAST);
		helper.assertTrue(rig.fire(Ladder.Rung.ONCE, alex, false, false, 41L) == FireResult.FIRED, "rung 1 did not fire");
		helper.assertTrue(Ladder.usedRuns(rig.env(), alex.getUUID()).equals(List.of(GlobalPos.of(g.level.dimension(), g.at(40 - TunnelRun.MAX_BACK, 3, 4)))),
				"the used run is not where the run starts: " + Ladder.usedRuns(rig.env(), alex.getUUID()));
		rig.playTicks += LadderRig.minutes(30);
		helper.assertTrue(rig.check(Ladder.Rung.COMING, alex).why() == Ladder.Why.RUN_USED, "the same run fired twice");
		rig.stand("Alex", 50, 3, 4, LadderRig.EAST);
		helper.assertTrue(rig.check(Ladder.Rung.COMING, alex).why() == Ladder.Why.RUN_USED, "a run starting 10 blocks from a used one fired");
		rig.stand("Alex", 70, 3, 4, LadderRig.EAST);
		helper.assertTrue(rig.check(Ladder.Rung.COMING, alex).fits(), "a run 30 blocks on did not fit: " + rig.check(Ladder.Rung.COMING, alex).why());
		helper.assertTrue(rig.fire(Ladder.Rung.COMING, alex, false, false, 42L) == FireResult.FIRED, "rung 2 did not fire 30 blocks on");
		rig.stand("Alex", 40, 3, 4, LadderRig.EAST);
		helper.assertTrue(rig.fire(Ladder.Rung.COMING, alex, false, true, 43L) == FireResult.FIRED, "a forced fire waited for an unused run");
		helper.assertTrue(Ladder.usedRuns(rig.env(), alex.getUUID()).size() == 2, "a forced fire used a run");
		helper.succeed();
	}

	/**
	 * Rung 3: the figure is asked ({@code canGlimpse}) before any step; the torch the player placed 6 back goes first
	 * (FIX-GLIMPSE: he makes the dark), so the spot is the nearest of the cells that will be pitch dark, 14 back; the
	 * steps come up to it, the dark and the figure are checked again at the last step, and the figure is called
	 * ({@code glimpse}) at the last step's cell, facing the player, in the tick of the last step.
	 */
	@GameTest(maxTicks = 600)
	public void glimpseAsksTheFigureFirstAndCallsItAtTheLastStep(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 22, 48, 8, 9, Blocks.STONE);
		g.keepLoaded(true);
		LadderRig rig = new LadderRig(helper, g, "Alex");
		rig.tunnelX(2, 41, 3, 4);
		ServerPlayer alex = rig.stand("Alex", 40, 3, 4, LadderRig.EAST);
		BlockPos torch = g.at(34, 3, 4); // 6 back: its light reaches 3 at 17 back, until it goes
		g.place(torch, Blocks.TORCH.defaultBlockState());
		BlockPos spot = g.at(40 - 14, 3, 4);
		boolean[] fired = {false};
		helper.succeedWhen(() -> {
			if (!fired[0]) {
				helper.assertTrue(light(g, torch) == 14 && light(g, torch.west(6)) == 8 && light(g, g.at(40 - 17, 3, 4)) == 3,
						"the torchlight has not settled yet");
				helper.assertTrue(rig.fire(Ladder.Rung.GLIMPSE, alex, false, true, 51L) == FireResult.FIRED, "rung 3 did not fire: "
						+ Ladder.check(Ladder.Rung.GLIMPSE, alex, rig.env(), true).why());
				fired[0] = true;
				helper.assertTrue(!rig.asked.isEmpty() && rig.heard.isEmpty(), "the figure was not asked before the first step");
				helper.assertTrue(g.level.getBlockState(torch).isAir(), "the torch behind did not go");
			}
			helper.assertFalse(LadderRig.walking(alex), "the steps are still coming");
			LadderRig.Asked first = rig.asked.getFirst();
			helper.assertTrue(!first.spawn() && first.feet().equals(Vec3.atBottomCenterOf(spot)), "the first spot asked is not the nearest pitch dark one: "
					+ first);
			long lastTick = rig.heard.getLast().tick();
			helper.assertTrue(rig.asked.stream().filter(a -> !a.spawn()).allMatch(a -> a.tick() <= rig.heard.getFirst().tick() || a.tick() == lastTick),
					"asked while the steps came");
			helper.assertTrue(rig.heard.size() >= 3 && rig.heard.size() <= rig.config.ladderGlimpseStepsMax, rig.heard.size() + " steps");
			double previous = Double.MAX_VALUE;
			for (LadderRig.Heard step : rig.heard) {
				double back = LadderRig.exactBackAlongX(alex, step.pos());
				helper.assertTrue(back < previous && back >= 14 - 1.0E-6, "the steps do not come up to the spot: " + back + " after " + previous);
				helper.assertTrue(step.volume() <= rig.config.ladderGlimpseVolumeMax + 1.0E-6, "a reaching step at " + step.volume());
				previous = back;
			}
			LadderRig.Heard last = rig.heard.getLast();
			helper.assertTrue(last.pos().equals(Vec3.atBottomCenterOf(spot)), "the last step is not on the spot: " + last.pos());
			List<LadderRig.Asked> spawns = rig.spawns();
			helper.assertTrue(spawns.size() == 1, spawns.size() + " glimpses");
			LadderRig.Asked him = spawns.getFirst();
			helper.assertTrue(him.feet().equals(last.pos()) && him.tick() == last.tick(), "the glimpse is not at the last step: " + him + " vs " + last);
			Vec3 facing = new Vec3(-Math.sin(Math.toRadians(him.yaw())), 0, Math.cos(Math.toRadians(him.yaw())));
			Vec3 toAlex = alex.position().subtract(him.feet()).multiply(1, 0, 1).normalize();
			helper.assertTrue(facing.dot(toAlex) > 0.99, "he does not face Alex: yaw " + him.yaw());
			g.keepLoaded(false);
		});
	}

	/**
	 * Rung 3 must be heard: with his spot 19 back, at least three of the steps coming up to it reach the player (their
	 * volume, up to {@code ladderGlimpseVolumeMax} 1.8, carries about 29 blocks where the shared moment radius allows),
	 * the last one on the spot, and none reaches a friend 60 away.
	 */
	@GameTest(maxTicks = 300)
	public void aGlimpseNineteenBackIsHeardComingButNotSixtyAway(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 26, 48, 8, 9, Blocks.STONE);
		g.keepLoaded(true);
		LadderRig rig = new LadderRig(helper, g, "Alex", "Sam");
		rig.tunnelX(2, 41, 3, 4);
		ServerPlayer alex = rig.stand("Alex", 40, 3, 4, LadderRig.EAST);
		ServerPlayer sam = rig.stand("Sam", 40 - 60, 3, 4, LadderRig.EAST);
		rig.figureWhere = feet -> LadderRig.backAlongX(alex, feet) >= 19; // nearer spots are lit, say
		helper.assertTrue(rig.fire(Ladder.Rung.GLIMPSE, alex, false, true, 81L) == FireResult.FIRED, "rung 3 did not fire: "
				+ Ladder.check(Ladder.Rung.GLIMPSE, alex, rig.env(), true).why());
		BlockPos spot = g.at(40 - 19, 3, 4);
		helper.succeedWhen(() -> {
			helper.assertFalse(LadderRig.walking(alex), "the steps are still coming");
			helper.assertTrue(!rig.heard.isEmpty() && rig.heard.getLast().pos().equals(Vec3.atBottomCenterOf(spot)), "the last step is not on the spot 19 back");
			long heardByAlex = rig.heard.stream().filter(step -> SoundCues.hearers(g.level, List.of(alex), step.pos(), step.volume()).contains(alex)).count();
			helper.assertTrue(heardByAlex >= 3, "Alex heard " + heardByAlex + " of " + rig.heard.size() + " steps: " + rig.heard);
			helper.assertTrue(rig.heard.stream().allMatch(step -> SoundCues.hearers(g.level, List.of(sam), step.pos(), step.volume()).isEmpty()),
					"the friend 60 away heard a step");
			helper.assertTrue(rig.heard.stream().allMatch(step -> step.volume() <= rig.config.ladderGlimpseVolumeMax + 1.0E-6 && step.volume() > 1.0F),
					"the reaching steps are not above 1 and at most the cap: " + rig.heard);
			helper.assertTrue(rig.spawns().size() == 1 && rig.spawns().getFirst().feet().equals(Vec3.atBottomCenterOf(spot)), "no glimpse on the spot");
			g.keepLoaded(false);
		});
	}

	/**
	 * His spot is dark to the eye, not only past the torchlight: in a tunnel under a trench open to the sky over every
	 * cell 13 to 20 back no spot is offered and the figure is never even asked; under a single shaft 14 back a spot is
	 * offered only where the light a player would see (vanilla's raw brightness, the sky's darkening counted) is 3 or
	 * less at his feet and eyes, the darkest of those, never the shaft's own cell (by day none of 14 to 20 is: the shaft
	 * lights 11 cells along the tunnel); the same tunnel sealed offers the cell 14 back.
	 */
	@GameTest(maxTicks = 300)
	public void aSkyLitSpotIsNeverOffered(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 29, 48, 8, 13, Blocks.STONE);
		g.keepLoaded(true);
		LadderRig trench = new LadderRig(helper, g, "Alex");
		trench.tunnelX(2, 41, 3, 2);
		g.hollow(20, 5, 2, 27, 7, 2); // open to the sky from 13 to 20 back
		LadderRig shaft = new LadderRig(helper, g, "Bea");
		shaft.tunnelX(2, 41, 3, 6);
		g.hollow(26, 5, 6, 26, 7, 6); // a 1x1 shaft 14 back
		LadderRig sealed = new LadderRig(helper, g, "Cy");
		sealed.tunnelX(2, 41, 3, 10);
		ServerPlayer alex = trench.stand("Alex", 40, 3, 2, LadderRig.EAST);
		ServerPlayer bea = shaft.stand("Bea", 40, 3, 6, LadderRig.EAST);
		ServerPlayer cy = sealed.stand("Cy", 40, 3, 10, LadderRig.EAST);
		helper.succeedWhen(() -> {
			for (int x = 20; x <= 27; x++) {
				helper.assertTrue(sky(g, g.at(x, 3, 2)) == 15 && sky(g, g.at(x, 4, 2)) == 15, "the daylight has not settled in the trench yet");
			}
			helper.assertTrue(sky(g, g.at(26, 3, 6)) == 15 && sky(g, g.at(25, 3, 6)) == 14 && sky(g, g.at(26, 3, 10)) == 0,
					"the daylight has not settled under the shaft yet");
			Ladder.Check lit = Ladder.check(Ladder.Rung.GLIMPSE, alex, trench.env(), true);
			helper.assertTrue(lit.why() == Ladder.Why.NO_SPOT && trench.asked.isEmpty(), "a spot in the daylight was offered: " + lit.spot()
					+ " back, raw brightness " + g.level.getMaxLocalRawBrightness(g.at(40 - Math.max(lit.spot(), 14), 3, 2)) + ", sky darkening "
					+ g.level.getSkyDarken() + ", asked " + trench.asked);
			int expected = -1;
			int darkest = Integer.MAX_VALUE;
			for (int back = shaft.config.ladderGlimpseSpotMin; back <= shaft.config.ladderGlimpseSpotMax; back++) {
				BlockPos cell = g.at(40 - back, 3, 6);
				int raw = Math.max(g.level.getMaxLocalRawBrightness(cell), g.level.getMaxLocalRawBrightness(cell.above()));
				if (raw <= 3 && raw < darkest) {
					expected = back;
					darkest = raw;
				}
			}
			Ladder.Check under = Ladder.check(Ladder.Rung.GLIMPSE, bea, shaft.env(), true);
			helper.assertTrue(under.spot() == expected && under.spot() != 14 && (expected < 0 ? under.why() == Ladder.Why.NO_SPOT : under.fits()),
					"under the shaft the spot is " + under.spot() + " back (" + under.why() + "), the darkest is " + expected + ", sky darkening "
							+ g.level.getSkyDarken());
			helper.assertTrue(g.level.getSkyDarken() > 0 || expected < 0, "by day a spot within 11 of the shaft is dark: " + expected);
			Ladder.Check dark = Ladder.check(Ladder.Rung.GLIMPSE, cy, sealed.env(), true);
			helper.assertTrue(dark.fits() && dark.spot() == 14, "the sealed tunnel offers " + dark.spot() + " (" + dark.why() + ")");
			g.keepLoaded(false);
		});
	}

	private static int sky(DigGround g, BlockPos pos) {
		return g.level.getBrightness(LightLayer.SKY, pos);
	}

	private static int light(DigGround g, BlockPos pos) {
		return g.level.getBrightness(LightLayer.BLOCK, pos);
	}

	/**
	 * Rung 3 without a spot the figure accepts is NO_SPOT, with no step at all (the stub figure never shows him); and a
	 * player who turns before the last step gets no figure, the steps just stop.
	 */
	@GameTest(maxTicks = 300)
	public void noFigureWithoutASpotOrAfterTheyTurned(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 23, 40, 8, 9, Blocks.STONE);
		g.keepLoaded(true);
		LadderRig rig = new LadderRig(helper, g, "Bob");
		rig.tunnelX(2, 39, 3, 4);
		ServerPlayer bob = rig.stand("Bob", 38, 3, 4, LadderRig.EAST);
		rig.figureCan = false;
		helper.assertTrue(rig.fire(Ladder.Rung.GLIMPSE, bob, false, true, 61L) == FireResult.NO_SPOT, "rung 3 fired with no spot");
		helper.assertTrue(!rig.asked.isEmpty() && rig.spawns().isEmpty() && rig.heard.isEmpty() && !LadderRig.walking(bob), "steps without a spot");
		helper.assertTrue(fireWithTheStubFigure(rig, bob) == FireResult.NO_SPOT, "the stub figure gave a spot");
		rig.figureCan = true;
		helper.assertTrue(rig.fire(Ladder.Rung.GLIMPSE, bob, false, true, 62L) == FireResult.FIRED, "rung 3 did not fire");
		helper.runAfterDelay(10, () -> LadderRig.look(bob, LadderRig.WEST));
		helper.runAfterDelay(150, () -> {
			helper.assertFalse(LadderRig.walking(bob), "the steps never ended");
			helper.assertTrue(rig.spawns().isEmpty(), "he was there although Bob turned before the last step");
			helper.assertTrue(!rig.heard.isEmpty() && rig.heard.size() <= 2, "Bob heard " + rig.heard.size() + " steps");
			g.keepLoaded(false);
			helper.succeed();
		});
	}

	/** A forced rung 3 with the stub figure (the game's until entity installs the real one) and the rig's everything else. */
	private static FireResult fireWithTheStubFigure(LadderRig rig, ServerPlayer player) {
		Ladder.Env env = rig.env();
		Ladder.Env stub = new Ladder.Env(env.watch(), env.data(), new Figure.Stub(), env.config(), env.playTicks(), env.day(), env.underground(),
				env.sounds(), env.view(), env.traces());
		return Ladder.fire(Ladder.Rung.GLIMPSE, new FireContext(player, player.level(), false, true, RandomSource.create(1L)), stub);
	}

	/**
	 * D-072, no edits: every rung and both fakes play out and not one block changes, and nothing is ledgered in the
	 * ground (no light in these tunnels, so rung 3 has none to take). The fakes: rung 1's is the player's own step, late (at their feet, vanilla's volume); rung 2's goes away,
	 * fading.
	 */
	@GameTest(maxTicks = 400)
	public void theLadderNeverChangesABlock(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 24, 40, 8, 34, Blocks.STONE);
		g.keepLoaded(true);
		List<LadderRig> rigs = new ArrayList<>();
		List<ServerPlayer> players = new ArrayList<>();
		String[] names = {"Ana", "Ben", "Cy", "Dee", "Eli"};
		for (int i = 0; i < names.length; i++) {
			LadderRig rig = new LadderRig(helper, g, names[i]);
			rig.tunnelX(2, 35, 3, 4 + 6 * i);
			rigs.add(rig);
			players.add(rig.stand(names[i], 34, 3, 4 + 6 * i, LadderRig.EAST));
		}
		List<BlockState> before = snapshot(g);
		helper.assertTrue(rigs.get(0).fire(Ladder.Rung.ONCE, players.get(0), false, true, 71L) == FireResult.FIRED, "rung 1");
		helper.assertTrue(rigs.get(1).fire(Ladder.Rung.ONCE, players.get(1), true, true, 72L) == FireResult.FIRED, "rung 1 fake");
		helper.assertTrue(rigs.get(2).fire(Ladder.Rung.COMING, players.get(2), false, true, 73L) == FireResult.FIRED, "rung 2");
		helper.assertTrue(rigs.get(3).fire(Ladder.Rung.COMING, players.get(3), true, true, 74L) == FireResult.FIRED, "rung 2 fake");
		helper.assertTrue(rigs.get(4).fire(Ladder.Rung.GLIMPSE, players.get(4), false, true, 75L) == FireResult.FIRED, "rung 3");
		helper.succeedWhen(() -> {
			helper.assertTrue(players.stream().noneMatch(LadderRig::walking), "steps still coming");
			for (int i = 0; i < rigs.size(); i++) {
				helper.assertTrue(!rigs.get(i).heard.isEmpty(), names[i] + " heard nothing");
			}
			List<LadderRig.Heard> once = rigs.get(0).heard;
			helper.assertTrue(once.size() <= 2 && once.stream().allMatch(step -> {
				int back = LadderRig.backAlongX(players.get(0), step.pos());
				return back >= rigs.get(0).config.ladderOnceBackMin && back <= rigs.get(0).config.ladderOnceBackMax;
			}), "rung 1 is not one or two steps 10 to 12 back");
			LadderRig.Heard own = rigs.get(1).heard.getFirst();
			helper.assertTrue(rigs.get(1).heard.size() == 1 && own.pos().equals(players.get(1).position()) && Math.abs(own.volume() - 0.15F) < 1.0E-4,
					"rung 1's fake is not the player's own step: " + own);
			List<LadderRig.Heard> away = rigs.get(3).heard;
			for (int i = 1; i < away.size(); i++) {
				helper.assertTrue(LadderRig.exactBackAlongX(players.get(3), away.get(i).pos()) > LadderRig.exactBackAlongX(players.get(3), away.get(i - 1).pos())
						&& away.get(i).volume() < away.get(i - 1).volume(), "rung 2's fake does not go away fading");
			}
			helper.assertTrue(rigs.get(4).spawns().size() == 1, "no glimpse at the end of rung 3");
			helper.assertTrue(snapshot(g).equals(before), "a block changed");
			AABB box = new AABB(Vec3.atLowerCornerOf(g.origin), Vec3.atLowerCornerOf(g.origin.offset(g.sizeX, g.sizeY, g.sizeZ)));
			helper.assertTrue(TraceLedger.get(g.level.getServer()).entries().stream()
					.noneMatch(entry -> entry.pos().dimension().equals(g.level.dimension()) && box.contains(Vec3.atCenterOf(entry.pos().pos()))),
					"something was ledgered in the tunnels");
			g.keepLoaded(false);
		});
	}

	/**
	 * Rung 2's fake is heard: its steps start 10 to 13 back and go away fading, every one inside its hearing range and
	 * clearly heard (at least 5% of full loudness at the ear, FIX-GLIMPSE's louder steps; v1.3 held it to rung 2's first
	 * step at the very edge of hearing), none past the run's end. On the shortest runs (16 and 17 cells) and a long
	 * one, every step plays.
	 */
	@GameTest(maxTicks = 200)
	public void rungTwosFakeIsHeardGoingAwayInsideTheRun(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 28, 40, 8, 17, Blocks.STONE);
		g.keepLoaded(true);
		int[] lengths = {16, 17, 30};
		List<LadderRig> rigs = new ArrayList<>();
		List<ServerPlayer> players = new ArrayList<>();
		List<LadderSteps.Walk> walks = new ArrayList<>();
		for (int i = 0; i < lengths.length; i++) {
			String name = "Fay" + i;
			LadderRig rig = new LadderRig(helper, g, name);
			int z = 3 + 5 * i;
			int x = 2 + lengths[i];
			rig.tunnelX(2, x, 3, z);
			ServerPlayer player = rig.stand(name, x, 3, z, LadderRig.EAST);
			Ladder.Check check = Ladder.check(Ladder.Rung.COMING, player, rig.env(), true);
			helper.assertTrue(check.fits() && check.run() != null && check.run().length() == lengths[i], "no " + lengths[i] + "-cell run: " + check.why()
					+ " " + (check.run() == null ? "" : check.run().length()));
			helper.assertTrue(rig.fire(Ladder.Rung.COMING, player, true, true, 101L + i) == FireResult.FIRED, "rung 2's fake did not fire on " + lengths[i]);
			walks.add(DigTicker.INSTANCE.ladder().walk(player.getUUID()).orElseThrow());
			rigs.add(rig);
			players.add(player);
		}
		DigConfig config = new DigConfig();
		helper.succeedWhen(() -> {
			for (int i = 0; i < lengths.length; i++) {
				ServerPlayer player = players.get(i);
				LadderSteps.Walk walk = walks.get(i);
				List<LadderRig.Heard> heard = rigs.get(i).heard;
				helper.assertFalse(LadderRig.walking(player), "the fake's steps are still going");
				helper.assertTrue(walk.stopped == null && heard.size() == config.ladderComingFakeSteps, "on " + lengths[i] + " cells: " + walk.describe());
				double previous = 0;
				float louder = Float.MAX_VALUE;
				for (LadderRig.Heard step : heard) {
					double back = LadderRig.exactBackAlongX(player, step.pos());
					BlockPos cell = BlockPos.containing(step.pos());
					int cellBack = player.getBlockX() - cell.getX();
					helper.assertTrue(back > previous && cellBack >= 1 && cellBack <= lengths[i] && TunnelRun.tunnelCell(g.level, rigs.get(i).crew.watch, cell),
							"a fake step " + back + " back on a " + lengths[i] + "-cell run, after " + previous);
					double distance = step.pos().distanceTo(player.position());
					double gain = GlimpseFeedbackTests.gain(step.volume(), distance);
					helper.assertTrue(SoundCues.hearers(g.level, List.of(player), step.pos(), step.volume()).contains(player),
							"a fake step out of hearing: " + back + " back at " + step.volume());
					helper.assertTrue(step.volume() < louder && gain >= 0.05, "a fake step " + back + " back at " + step.volume() + " (" + gain
							+ " at the ear) after " + louder);
					previous = back;
					louder = step.volume();
				}
				double first = LadderRig.exactBackAlongX(player, heard.getFirst().pos());
				double last = LadderRig.exactBackAlongX(player, heard.getLast().pos());
				helper.assertTrue(first >= 10 && first <= 13 && last <= Ladder.FAKE_FARTHEST, "the fake goes from " + first + " to " + last + " back");
			}
			g.keepLoaded(false);
		});
	}

	private static List<BlockState> snapshot(DigGround g) {
		List<BlockState> states = new ArrayList<>(g.sizeX * g.sizeY * g.sizeZ);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < g.sizeX; x++) {
			for (int y = 0; y < g.sizeY; y++) {
				for (int z = 0; z < g.sizeZ; z++) {
					states.add(g.level.getBlockState(pos.setWithOffset(g.origin, x, y, z)));
				}
			}
		}
		return states;
	}

	/** The three cards are registered: tiers, stages, tags (SOUND and PERSONAL, the glimpse SIGHTING too), no habits, fakes. */
	@GameTest
	public void theLadderCardsAreRegistered(GameTestHelper helper) {
		record Expect(String id, Tier tier, Stage stage, Set<CardTag> tags, boolean fake) {
		}
		for (Expect expect : List.of(
				new Expect(LadderCards.StepsBehindOnce.ID, Tier.AMBIENT, Stage.ALONE, Set.of(CardTag.SOUND, CardTag.PERSONAL), true),
				new Expect(LadderCards.StepsBehindComing.ID, Tier.MINOR, Stage.TRACES, Set.of(CardTag.SOUND, CardTag.PERSONAL), true),
				new Expect(LadderCards.StepsBehindGlimpse.ID, Tier.MINOR, Stage.TRACES, Set.of(CardTag.SOUND, CardTag.PERSONAL, CardTag.SIGHTING), false))) {
			EventCard card = CardRegistry.get(expect.id()).orElse(null);
			helper.assertTrue(card != null, "not registered: " + expect.id());
			helper.assertTrue(card.tier() == expect.tier() && card.earliestStage() == expect.stage() && card.tags().equals(expect.tags())
					&& card.habits().isEmpty() && card.hasFake() == expect.fake(), expect.id() + " is " + card.tier() + " " + card.earliestStage() + " "
					+ card.tags() + " " + card.habits() + " fake " + card.hasFake());
		}
		// The live gates: a mock player standing on a stone block in the open is in no tunnel.
		DigGround g = DigGround.of(helper, 25, 8, 4, 8, Blocks.STONE);
		g.stand(g.at(4, 4, 4));
		for (Ladder.Rung rung : Ladder.Rung.values()) {
			EventCard card = CardRegistry.get(rung.id).orElseThrow();
			helper.assertFalse(card.contextFits(g.mock, g.level), rung.id + " fits in the open");
			helper.assertTrue(card.fire(new FireContext(g.mock, g.level, false, true, RandomSource.create(1L))) == FireResult.SKIPPED,
					"a forced " + rung.id + " fired in the open");
		}
		helper.assertTrue(Services.figure() != null, "no figure service");
		helper.succeed();
	}

	/** D-075: a v1.1 dig section (no version) reads as version 1 with the ladder's defaults, keeping what the player changed. */
	@GameTest
	public void anOldDigSectionUpgradesToVersionOne(GameTestHelper helper) {
		JsonObject sections = JsonParser.parseString("{\"dig\": {\"networkBlocksPerNight\": 9, \"homeRadius\": 24}}").getAsJsonObject();
		DigConfig old = ModConfig.readSection(sections, "dig", DigConfig.class, DigConfig::new);
		DigConfig defaults = new DigConfig();
		helper.assertTrue(old.version == DigConfig.CURRENT_VERSION && old.networkBlocksPerNight == 9 && old.homeRadius == defaults.homeRadius,
				"the old section: version " + old.version + ", networkBlocksPerNight " + old.networkBlocksPerNight);
		helper.assertTrue(old.ladderMinRun == 16 && old.ladderGapMinutes == 25 && old.ladderStepTicks == 8 && old.ladderSneakStepTicks == 13
				&& old.ladderGlimpseSpotMin == 14 && old.ladderGlimpseSpotMax == 20 && old.ladderLookBackDegrees == 70 && old.ladderUsedRunRadius == 24,
				"the ladder's fields are not their defaults");
		helper.assertFalse(old.upgrade(), "upgraded twice");
		DigConfig fresh = ModConfig.readSection(new JsonObject(), "dig", DigConfig.class, DigConfig::new);
		helper.assertTrue(fresh.version == DigConfig.CURRENT_VERSION && fresh.networkBlocksPerNight == defaults.networkBlocksPerNight, "a fresh section");
		helper.succeed();
	}

	/** The ladder's memory survives a save, and a dig.dat from before v1.2 (no ladder) loads with nobody on the ladder. */
	@GameTest
	public void ladderMemoryRoundTripsAndOldSavesLoad(GameTestHelper helper) {
		DigData data = new DigData();
		UUID alex = UUID.randomUUID();
		LadderMemory memory = data.ladder(alex);
		memory.rung = 2;
		memory.lastEvent = 1234;
		memory.lastComing = 1234;
		memory.glimpseDay = 3;
		memory.use(helper.getLevel().dimension(), new BlockPos(10, -40, 7));
		Tag saved = DigData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		DigData back = DigData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		LadderMemory loaded = back.ladderOf(alex).orElse(null);
		helper.assertTrue(loaded != null && loaded.rung == 2 && loaded.lastEvent == 1234 && loaded.lastComing == 1234 && loaded.lastGlimpse == Long.MIN_VALUE
				&& loaded.glimpseDay == 3 && loaded.usedRuns.equals(memory.usedRuns), "the ladder did not round-trip");
		CompoundTag old = (CompoundTag) saved;
		old.remove("ladder");
		DigData before = DigData.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(before.ladderOf(alex).isEmpty(), "an old save has a ladder");
		helper.succeed();
	}

	// --- review R3 (v1.2 bug hunt) ---

	/**
	 * R3: TunnelRun's contract is that "a cell a player placed something see-through in (a torch, a rail) still belongs
	 * to" the tunnel. A torch the player puts in their tunnel and later picks up leaves the cell dug again (before the
	 * fix {@code PlayerWatch.onPlaced} dropped the dug mark and {@code onBroken} only removed the placed one, so the dug
	 * air there counted as natural and the run was cut at that cell: a 29-cell tunnel with one moved torch 11 back was a
	 * 10-cell run, too short for any rung).
	 */
	@GameTest(maxTicks = 100)
	public void r3APickedUpTorchDoesNotCutTheTunnel(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 40, 40, 8, 9, Blocks.STONE);
		LadderRig rig = new LadderRig(helper, g, "Alex");
		rig.tunnelX(2, 31, 3, 4);
		BlockPos torch = g.at(20, 3, 4);
		g.place(torch, Blocks.TORCH.defaultBlockState());
		Optional<TunnelRun> lit = TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 4), Direction.EAST, rig.config.ladderMinRun, rig.config.ladderMinSealedShare);
		helper.assertTrue(lit.map(TunnelRun::length).orElse(0) == 29, "with the torch in it: " + lit.map(TunnelRun::length));
		helper.assertTrue(g.mock.gameMode.destroyBlock(torch) && g.level.getBlockState(torch).isAir(), "the player could not pick the torch up");
		Optional<TunnelRun> after = TunnelRun.detect(g.level, rig.crew.watch, g.at(31, 3, 4), Direction.EAST, rig.config.ladderMinRun,
				rig.config.ladderMinSealedShare);
		helper.assertTrue(after.map(TunnelRun::length).orElse(0) == 29, "picking the torch up cut the tunnel: run " + after.map(TunnelRun::length)
				+ ", the cell is dug " + Services.watch().wasDugByPlayer(g.level, torch) + ", placed " + Services.watch().wasPlacedByPlayer(g.level, torch));
		helper.succeed();
	}

	// --- review R4 (v1.3 bug hunt) ---

	/**
	 * R4, the ladder's side of {@code BlindGameTests.r4BlindStepOnSnowOrLeafLitterSoundsLikeIt}: a step in the tunnel is
	 * vanilla's step there ({@link SoundCards#stepSound}). One snow layer or leaf litter someone placed along their tunnel
	 * (no collision, so each cell is still the tunnel's) plays snow or leaves, not the stone under it, for rung 1's steps
	 * and for its fake (the player's own step, on the cover at their feet). Before the fix every one played stone.
	 */
	@GameTest(maxTicks = 200)
	public void r4LadderStepOnSnowOrLeafLitterSoundsLikeIt(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 91, 40, 8, 26, Blocks.STONE);
		g.keepLoaded(true);
		List<Block> covers = List.of(Blocks.SNOW, Blocks.SNOW, Blocks.LEAF_LITTER, Blocks.LEAF_LITTER);
		List<LadderRig> rigs = new ArrayList<>();
		List<ServerPlayer> players = new ArrayList<>();
		for (int i = 0; i < covers.size(); i++) {
			String name = "Lee" + i;
			LadderRig rig = new LadderRig(helper, g, name);
			int z = 3 + 6 * i;
			rig.tunnelX(2, 35, 3, z);
			for (int x = 2; x <= 35; x++) {
				g.place(g.at(x, 3, z), covers.get(i).defaultBlockState());
			}
			ServerPlayer player = rig.stand(name, 34, 3, z, LadderRig.EAST);
			boolean fake = i % 2 == 1;
			helper.assertTrue(rig.fire(Ladder.Rung.ONCE, player, fake, true, 41L + i) == FireResult.FIRED, "rung 1" + (fake ? "'s fake" : "")
					+ " did not fire on " + covers.get(i) + ": " + Ladder.check(Ladder.Rung.ONCE, player, rig.env(), true).why());
			rigs.add(rig);
			players.add(player);
		}
		helper.succeedWhen(() -> {
			for (int i = 0; i < covers.size(); i++) {
				helper.assertFalse(LadderRig.walking(players.get(i)), "the steps are still going");
				Block cover = covers.get(i);
				List<LadderRig.Heard> heard = rigs.get(i).heard;
				helper.assertTrue(!heard.isEmpty(), "no step on " + cover);
				for (LadderRig.Heard step : heard) {
					helper.assertTrue(step.sound().value() == cover.defaultBlockState().getSoundType().getStepSound(), "a step" + (i % 2 == 1 ? " (the fake)" : "")
							+ " on " + cover + " sounds like " + step.sound().value().location());
				}
			}
			g.keepLoaded(false);
		});
	}
}
