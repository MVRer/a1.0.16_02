package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.TraceLedger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayer.RespawnConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * FIX-GLIMPSE, from Mariano's playtest of v1.3: rung 3 says why it has no spot (the figure's reasons, the ladder's own
 * light rule, the look back a forced fire skips), {@code /a1016 dig steps} lists every candidate, the arm waits for the
 * card's own moment; rung 3 makes the dark first (every light a player placed lighting the tunnel 6 to 24 back, out of
 * view, at most 8, ledgered), the steps are louder and still never reach a friend 48 away; {@code blind_torch} takes a
 * lone torch, guards only real beds and says why none may go. Each test has its own crew, memory and clock.
 */
public class GlimpseFeedbackTests {
	/** The first name stands at x 40 of a tunnel dug from x 2 to 41 on row z 4 (feet y 3), facing east: the run behind is 38 cells. */
	private static LadderRig tunnel(GameTestHelper helper, int slot, String... names) {
		DigGround g = DigGround.of(helper, slot, 48, 8, 9, Blocks.STONE);
		g.keepLoaded(true);
		LadderRig rig = new LadderRig(helper, g, names);
		rig.tunnelX(2, 41, 3, 4);
		rig.stand(names[0], 40, 3, 4, LadderRig.EAST);
		return rig;
	}

	private static ServerPlayer player(LadderRig rig, String name) {
		return rig.crew.online.stream().filter(p -> p.getName().getString().equals(name)).findFirst().orElseThrow();
	}

	/** The cells {@code from} to {@code to} back (x 40 - back) have settled at {@code light}. */
	private static void assertLight(GameTestHelper helper, DigGround g, int from, int to, int light) {
		for (int back = from; back <= to; back++) {
			int now = Ladder.light(g.level, g.at(40 - back, 3, 4));
			helper.assertTrue(now == light, "the light has not settled yet: " + now + " at " + back + " back");
		}
	}

	private static Ladder.Attempt forced(LadderRig rig, ServerPlayer player) {
		return Ladder.attempt(Ladder.Rung.GLIMPSE, new FireContext(player, player.level(), false, true, RandomSource.create(5L)), rig.env());
	}

	private static boolean ledgered(DigGround g, BlockPos pos, String cause) {
		return TraceLedger.get(g.level.getServer()).entries().stream().anyMatch(entry -> entry.cause().equals(cause)
				&& entry.pos().dimension().equals(g.level.dimension()) && entry.pos().pos().equals(pos));
	}

	/** Vanilla's loudness at the ear: full at most, fading linearly to nothing at {@code 16 * max(volume, 1)} blocks. */
	static double gain(float volume, double distance) {
		return Math.min(volume, 1.0) * Math.max(0.0, 1.0 - distance / (16.0 * Math.max(volume, 1.0)));
	}

	// --- the reasons (item 1) ---

	/**
	 * Mariano's NO_SPOT: every cell 14 to 20 back in his own view (a forced fire skips the look back, so he fired facing
	 * down the tunnel). The answer says so, with a hint; looking back down the tunnel it says to face away, as the real
	 * card waits for; mixed reasons come with their counts, the main one first, and a friend's view is named.
	 */
	@GameTest(maxTicks = 200)
	public void noSpotSaysInYourViewAndToFaceAway(GameTestHelper helper) {
		LadderRig rig = tunnel(helper, 100, "Alex");
		DigGround g = rig.ground;
		ServerPlayer alex = player(rig, "Alex");
		helper.succeedWhen(() -> {
			assertLight(helper, g, 14, 20, 0);
			LadderRig.look(alex, LadderRig.EAST);
			rig.figureWhy = feet -> Optional.of("in someone's view: Alex");
			Ladder.Attempt attempt = forced(rig, alex);
			helper.assertTrue(attempt.result() == FireResult.NO_SPOT && rig.spawns().isEmpty() && rig.heard.isEmpty(), "rung 3 " + attempt.result());
			String line = Ladder.forcedAnswer(Ladder.Rung.GLIMPSE, attempt, alex, rig.env());
			helper.assertTrue(line.equals("[a1016] dig: steps_behind_glimpse: no spot: 14-20 back are in your view (face down the tunnel away from them)"),
					line);

			// Walked east, then turned to look back west down the tunnel: the forced fire says to face away.
			LadderRig.walkedEast(alex);
			LadderRig.look(alex, LadderRig.WEST);
			helper.assertTrue(Ladder.check(Ladder.Rung.GLIMPSE, alex, rig.env(), true, true).why() == Ladder.Why.LOOKING_BACK, "the real card does not wait");
			line = Ladder.forcedAnswer(Ladder.Rung.GLIMPSE, forced(rig, alex), alex, rig.env());
			helper.assertTrue(line.equals("[a1016] dig: steps_behind_glimpse: no spot: 14-20 back are in your view; " + Ladder.FACE_AWAY), line);
			helper.assertTrue(Ladder.FACE_AWAY.endsWith("face away from the tunnel behind you, then fire (the real card waits for that)"), Ladder.FACE_AWAY);

			// Mixed: four in view, three too close; a friend's view by name.
			LadderRig.look(alex, LadderRig.EAST);
			rig.figureWhy = feet -> {
				int back = LadderRig.backAlongX(alex, feet);
				return Optional.of(back <= 17 ? "in someone's view: Alex" : "too close (" + (30 - back) + " blocks)");
			};
			Ladder.Plan plan = Ladder.check(Ladder.Rung.GLIMPSE, alex, rig.env(), true).plan();
			String summary = plan.summary(alex, true);
			helper.assertTrue(summary.equals("no spot: 14-20 back: 4 in your view (face down the tunnel away from them), 3 too close (10-12 blocks)"), summary);
			String spots = plan.spotsLine(alex, rig.config);
			helper.assertTrue(spots.startsWith("steps_behind_glimpse spots (14 to 20 back): 14 in your view (light 0); 15 in your view (light 0)")
					&& spots.contains("20 too close (10 blocks) (light 0)") && spots.endsWith("-> no spot"), spots);
			rig.figureWhy = feet -> Optional.of("in someone's view: Sam");
			summary = Ladder.check(Ladder.Rung.GLIMPSE, alex, rig.env(), true).plan().summary(alex, true);
			helper.assertTrue(summary.equals("no spot: 14-20 back are in Sam's view"), summary);
			g.keepLoaded(false);
		});
	}

	/**
	 * Too lit by a light nobody placed (glowstone in the wall): the ladder's own light rule refuses each cell with its
	 * light, the figure is never asked, and the glowstone stays. In daylight down an open trench the sky is never fought:
	 * a player's torch in the tunnel stays too, since there is no spot.
	 */
	@GameTest(maxTicks = 200)
	public void noSpotSaysTooLitAndNeverFightsTheSkyOrANaturalLight(GameTestHelper helper) {
		LadderRig rig = tunnel(helper, 101, "Bea");
		DigGround g = rig.ground;
		ServerPlayer bea = player(rig, "Bea");
		BlockPos glow = g.at(23, 3, 5); // in the wall beside 17 back
		g.level.setBlock(glow, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
		LadderRig sky = tunnel(helper, 102, "Cy");
		DigGround open = sky.ground;
		ServerPlayer cy = player(sky, "Cy");
		open.hollow(20, 5, 4, 27, 7, 4); // open to the sky over 13 to 20 back
		BlockPos torch = open.at(30, 3, 4); // 10 back
		open.place(torch, Blocks.TORCH.defaultBlockState());
		helper.succeedWhen(() -> {
			helper.assertTrue(g.level.getBrightness(LightLayer.BLOCK, g.at(23, 3, 4)) == 14 && g.level.getBrightness(LightLayer.BLOCK, g.at(26, 3, 4)) == 11,
					"the glowstone's light has not settled yet");
			helper.assertTrue(open.level.getBrightness(LightLayer.SKY, open.at(23, 3, 4)) == 15 && open.level.getBrightness(LightLayer.BLOCK, torch) == 14,
					"the daylight or the torch's light has not settled yet");
			rig.asked.clear();
			Ladder.Attempt lit = forced(rig, bea);
			String summary = lit.check().plan().summary(bea, true);
			helper.assertTrue(lit.result() == FireResult.NO_SPOT && rig.asked.isEmpty(), "a lit cell went to the figure: " + rig.asked);
			helper.assertTrue(summary.startsWith("no spot: 14-20 back are too lit (light 11-14): a light no player placed ("), summary);
			helper.assertTrue(Ladder.forcedAnswer(Ladder.Rung.GLIMPSE, lit, bea, rig.env()).equals("[a1016] dig: steps_behind_glimpse: " + summary),
					"the forced answer is not the summary");
			helper.assertTrue(g.level.getBlockState(glow).is(Blocks.GLOWSTONE) && lit.check().plan().lights().size() == 1
					&& lit.check().plan().lights().getFirst().stays() == Ladder.Stays.NOBODYS, "the glowstone: " + lit.check().plan().lights());

			Ladder.Attempt daylight = forced(sky, cy);
			String sunny = daylight.check().plan().summary(cy, true);
			helper.assertTrue(daylight.result() == FireResult.NO_SPOT && sunny.startsWith("no spot: 14-20 back are too lit by the sky (light ")
					&& sunny.endsWith("(daylight down a shaft or in from a mouth: he never stands in it)"), sunny);
			helper.assertTrue(open.level.getBlockState(torch).is(Blocks.TORCH) && !ledgered(open, torch, Ladder.CAUSE_GLIMPSE_TORCHES),
					"the player's torch went although there was no spot");
			g.keepLoaded(false);
			open.keepLoaded(false);
		});
	}

	// --- the dark first (items 2 and 4) ---

	/**
	 * Rung 3 makes the dark even when a dark cell is there already (17 back, light 3): the two torches a player placed
	 * that light the tunnel 6 to 24 back go in one silent edit, ledgered with {@code dig:glimpse_torches}, their floor
	 * stays and nothing drops; the torch within 3 blocks of the player (in view by the trace service's rule) stays, and
	 * one 38 back that lights nothing there stays. The spot is then the darkest cell, the nearest of those (16 back: the
	 * torch beside her still gives 14 and 15 back a little light); after the steps the dark is checked again and he is
	 * there.
	 */
	@GameTest(maxTicks = 300)
	public void glimpseTakesThePlayersLightsBehindEvenWithADarkCell(GameTestHelper helper) {
		LadderRig rig = tunnel(helper, 103, "Dee");
		DigGround g = rig.ground;
		ServerPlayer dee = player(rig, "Dee");
		BlockPos six = g.at(34, 3, 4);
		BlockPos twentyEight = g.at(12, 3, 4);
		BlockPos two = g.at(38, 3, 4);
		BlockPos far = g.at(2, 3, 4);
		for (BlockPos torch : List.of(six, twentyEight, two, far)) {
			g.place(torch, Blocks.TORCH.defaultBlockState());
		}
		BlockPos spot = g.at(40 - 16, 3, 4);
		helper.startSequence()
				.thenWaitUntil(() -> {
					for (BlockPos torch : List.of(six, twentyEight, two, far)) {
						helper.assertTrue(g.level.getBrightness(LightLayer.BLOCK, torch) == 14, "the torchlight has not settled yet");
					}
					helper.assertTrue(Ladder.light(g.level, g.at(40 - 17, 3, 4)) == 3 && Ladder.light(g.level, g.at(40 - 16, 3, 4)) == 4
							&& Ladder.light(g.level, g.at(40 - 18, 3, 4)) == 4, "17 back is not the one dark cell yet");
				})
				.thenExecute(() -> {
					Ladder.Check check = Ladder.check(Ladder.Rung.GLIMPSE, dee, rig.env(), true);
					helper.assertTrue(check.fits() && check.spot() == 16 && Set.copyOf(check.lights()).equals(Set.of(six, twentyEight)), "the plan: spot "
							+ check.spot() + ", lights " + check.plan().lights());
					helper.assertTrue(check.plan().lights().stream().anyMatch(light -> light.pos().equals(two) && light.stays() == Ladder.Stays.IN_VIEW)
							&& check.plan().lights().stream().noneMatch(light -> light.pos().equals(far)), "the torch beside her, or the far one: "
							+ check.plan().lights());
					helper.assertTrue(rig.fire(Ladder.Rung.GLIMPSE, dee, false, true, 91L) == FireResult.FIRED, "rung 3 did not fire");
					helper.assertTrue(g.level.getBlockState(six).isAir() && g.level.getBlockState(twentyEight).isAir(), "a torch behind her is still there");
					helper.assertTrue(g.level.getBlockState(two).is(Blocks.TORCH) && g.level.getBlockState(far).is(Blocks.TORCH), "a torch that stays went");
					helper.assertTrue(g.level.getBlockState(six.below()).is(Blocks.STONE) && g.level.getBlockState(twentyEight.below()).is(Blocks.STONE),
							"a torch's floor went");
					helper.assertTrue(ledgered(g, six, Ladder.CAUSE_GLIMPSE_TORCHES) && ledgered(g, twentyEight, Ladder.CAUSE_GLIMPSE_TORCHES),
							"not ledgered with dig:glimpse_torches");
					LadderSteps.Walk walk = DigTicker.INSTANCE.ladder().walk(dee.getUUID()).orElseThrow();
					helper.assertTrue(spot.equals(walk.spot) && walk.darkened == 2, "the walk: " + walk.describe() + " to " + walk.spot);
				})
				.thenWaitUntil(() -> helper.assertFalse(LadderRig.walking(dee), "the steps are still coming"))
				.thenExecute(() -> {
					LadderSteps.Walk walk = DigTicker.INSTANCE.ladder().walk(dee.getUUID()).orElse(null);
					helper.assertTrue(rig.spawns().size() == 1 && rig.spawns().getFirst().feet().equals(Vec3.atBottomCenterOf(spot)),
							"no glimpse on the spot after the dark: " + rig.spawns() + (walk == null ? "" : " " + walk.describe()));
					long last = rig.heard.getLast().tick();
					helper.assertTrue(rig.asked.stream().anyMatch(a -> !a.spawn() && a.tick() == last), "the spot was not checked again at the last step");
					AABB box = new AABB(Vec3.atLowerCornerOf(g.origin), Vec3.atLowerCornerOf(g.origin.offset(g.sizeX, g.sizeY, g.sizeZ)));
					helper.assertTrue(g.level.getEntitiesOfClass(ItemEntity.class, box).isEmpty(), "a torch dropped");
					g.keepLoaded(false);
				})
				.thenSucceed();
	}

	/**
	 * Never more than {@code ladderGlimpseMaxTorches} (8): with ten of a player's torches lighting the tunnel behind,
	 * the eight nearest his spot's cells go and the two nearest the player stay; a light in the player's view (they look
	 * back down the tunnel) stays, and rung 3 goes on with the dark it has.
	 */
	@GameTest(maxTicks = 200)
	public void glimpseTakesAtMostEightLightsAndNeverOneInView(GameTestHelper helper) {
		LadderRig rig = tunnel(helper, 104, "Eli");
		DigGround g = rig.ground;
		ServerPlayer eli = player(rig, "Eli");
		List<BlockPos> torches = new ArrayList<>();
		for (int back = 6; back <= 24; back += 2) {
			BlockPos torch = g.at(40 - back, 3, 4);
			g.place(torch, Blocks.TORCH.defaultBlockState());
			torches.add(torch);
		}
		LadderRig seen = tunnel(helper, 105, "Fay");
		DigGround h = seen.ground;
		ServerPlayer fay = player(seen, "Fay");
		BlockPos front = h.at(34, 3, 4); // 6 back, and she looks that way
		h.place(front, Blocks.TORCH.defaultBlockState());
		helper.succeedWhen(() -> {
			for (BlockPos torch : torches) {
				helper.assertTrue(g.level.getBrightness(LightLayer.BLOCK, torch) == 14, "the torchlight has not settled yet");
			}
			helper.assertTrue(h.level.getBrightness(LightLayer.BLOCK, front) == 14 && Ladder.light(h.level, h.at(20, 3, 4)) == 0, "Fay's tunnel has not settled");
			helper.assertTrue(rig.config.ladderGlimpseMaxTorches == 8, "the limit is " + rig.config.ladderGlimpseMaxTorches);
			Ladder.Check check = Ladder.check(Ladder.Rung.GLIMPSE, eli, rig.env(), true);
			Set<BlockPos> staying = check.plan().staying().stream().map(Ladder.Light::pos).collect(Collectors.toSet());
			helper.assertTrue(check.lights().size() == 8 && staying.equals(Set.of(g.at(34, 3, 4), g.at(32, 3, 4)))
					&& check.plan().staying().stream().allMatch(light -> light.stays() == Ladder.Stays.TOO_MANY), "lights " + check.plan().lights());
			helper.assertTrue(rig.fire(Ladder.Rung.GLIMPSE, eli, false, true, 92L) == FireResult.FIRED, "rung 3 did not fire");
			helper.assertTrue(torches.stream().filter(torch -> g.level.getBlockState(torch).isAir()).count() == 8 && staying.stream()
					.allMatch(torch -> g.level.getBlockState(torch).is(Blocks.TORCH)), "not the eight nearest his spot went");

			LadderRig.walkedEast(fay);
			LadderRig.look(fay, LadderRig.WEST);
			Ladder.Check looking = Ladder.check(Ladder.Rung.GLIMPSE, fay, seen.env(), true);
			helper.assertTrue(looking.plan().lights().size() == 1 && looking.plan().lights().getFirst().stays() == Ladder.Stays.IN_VIEW
					&& looking.lights().isEmpty() && looking.plan().lightsLine(seen.config).contains(": in view"), looking.plan().lightsLine(seen.config));
			helper.assertTrue(seen.fire(Ladder.Rung.GLIMPSE, fay, false, true, 93L) == FireResult.FIRED && h.level.getBlockState(front).is(Blocks.TORCH)
					&& !ledgered(h, front, Ladder.CAUSE_GLIMPSE_TORCHES), "the torch in view went");
			g.keepLoaded(false);
			h.keepLoaded(false);
		});
	}

	// --- the arm (item 1) ---

	/**
	 * {@code /a1016 dig steps arm glimpse}: while the player looks back down the tunnel the arm waits (the real card's
	 * look-back rule) and keeps that as its reason; once they face away it fires (forced) and tells them; an arm whose
	 * moment never comes runs out and says the last reason.
	 */
	@GameTest(maxTicks = 400)
	public void theArmFiresOnceThePlayerFacesAwayAndRunsOutOtherwise(GameTestHelper helper) {
		LadderRig rig = tunnel(helper, 106, "Gus");
		DigGround g = rig.ground;
		ServerPlayer gus = player(rig, "Gus");
		LadderRig.look(gus, LadderRig.WEST);
		List<String> told = new ArrayList<>();
		LadderSteps.Arm[] arm = {null};
		helper.onEachTick(() -> LadderRig.walkedEast(gus)); // he walked east to the end of his tunnel, then turned
		helper.startSequence()
				.thenWaitUntil(() -> assertLight(helper, g, 14, 20, 0))
				.thenExecute(() -> {
					LadderRig.walkedEast(gus);
					Ladder.Check now = Ladder.check(Ladder.Rung.GLIMPSE, gus, rig.env(), true, true);
					helper.assertTrue(now.why() == Ladder.Why.LOOKING_BACK, "looking back: " + now.why());
					String armed = DigCommands.armedLine(Ladder.Rung.GLIMPSE, false, gus, now, LadderSteps.ARM_TICKS);
					helper.assertTrue(armed.equals("[a1016] dig: steps_behind_glimpse armed for Gus for 120 s: face forward, away from the tunnel behind you, "
							+ "and it fires the moment its own moment comes (now: looking back down the tunnel (face away from it))"), armed);
					arm[0] = rig.arm(Ladder.Rung.GLIMPSE, gus, 600, told);
				})
				.thenExecuteAfter(40, () -> {
					helper.assertTrue(told.isEmpty() && !LadderRig.walking(gus) && DigTicker.INSTANCE.ladder().armed(gus.getUUID()).isPresent(),
							"the arm fired while he looked back: " + told);
					helper.assertTrue(arm[0].last.equals("looking back down the tunnel (face away from it)"), "the arm's reason: " + arm[0].last);
					LadderRig.look(gus, LadderRig.EAST);
				})
				.thenWaitUntil(() -> helper.assertFalse(told.isEmpty(), "the arm did not fire once he faced away"))
				.thenExecute(() -> {
					helper.assertTrue(told.size() == 1 && told.getFirst().equals(
							"[a1016] dig: the armed steps_behind_glimpse fired for Gus -> FIRED: the steps are coming; when they stop, turn around"), told.toString());
					helper.assertTrue(LadderRig.walking(gus) && DigTicker.INSTANCE.ladder().armed(gus.getUUID()).isEmpty(), "no steps, or still armed");
					LadderRig.look(gus, LadderRig.WEST); // the steps stop; and he keeps looking back
					told.clear();
					rig.arm(Ladder.Rung.GLIMPSE, gus, 30, told);
				})
				.thenWaitUntil(() -> helper.assertFalse(told.isEmpty(), "the second arm never ran out"))
				.thenExecute(() -> {
					helper.assertTrue(told.size() == 1 && told.getFirst().equals(
							"[a1016] dig: the armed steps_behind_glimpse waited 1 s and was dropped; last: looking back down the tunnel (face away from it)"),
							told.toString());
					helper.assertTrue(DigTicker.INSTANCE.ladder().armed(gus.getUUID()).isEmpty(), "still armed");
					g.keepLoaded(false);
				})
				.thenSucceed();
	}

	/** The commands parse: {@code /a1016 dig steps arm <once|coming|glimpse> [fake] [player]}. */
	@GameTest
	public void theArmCommandsParse(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var source = server.createCommandSourceStack().withSuppressedOutput();
		for (String command : List.of("a1016 dig steps arm once", "a1016 dig steps arm coming fake", "a1016 dig steps arm glimpse",
				"a1016 dig steps arm steps_behind_glimpse")) {
			var parsed = server.getCommands().getDispatcher().parse(command, source);
			helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "does not parse: " + command);
		}
		helper.assertTrue(Ladder.Rung.byName("glimpse").orElseThrow() == Ladder.Rung.GLIMPSE && Ladder.Rung.byName("steps_behind_once").orElseThrow()
				== Ladder.Rung.ONCE && Ladder.Rung.byName("twice").isEmpty(), "the rung names");
		helper.succeed();
	}

	// --- louder steps (item 4) ---

	/**
	 * The new volumes (far 1.5, near 1.0, rung 3 at 1.8, the blind step 1.0) keep every range under 30 blocks; rung 2's
	 * and rung 3's steps are heard by their player, louder at the ear as they near, and a friend just past 48 blocks
	 * (alone: no sharer) hears none of them wherever they stand.
	 */
	@GameTest(maxTicks = 300)
	public void louderStepsNeverReachAFriendFortyEightAway(GameTestHelper helper) {
		DigConfig d = new DigConfig();
		helper.assertTrue(d.ladderVolumeFar == 1.5F && d.ladderVolumeNear == 1.0F && d.ladderGlimpseVolumeMax == 1.8F && d.blindStepVolume == 1.0F,
				"the volumes are not the new defaults");
		helper.assertTrue(Math.max(d.ladderVolumeFar, d.ladderGlimpseVolumeMax) * 16 < 30 && Ladder.reach(d) < 30, "a range of 30 or more");
		LadderRig coming = tunnel(helper, 107, "Hal", "Sam");
		coming.config.ladderSneakChance = 0;
		ServerPlayer hal = player(coming, "Hal");
		ServerPlayer sam = coming.stand("Sam", 40 - 49, 3, 4, LadderRig.EAST);
		LadderRig reaching = tunnel(helper, 111, "Ivy");
		ServerPlayer ivy = player(reaching, "Ivy");
		DigGround g = coming.ground;
		boolean[] fired = {false};
		helper.succeedWhen(() -> {
			if (!fired[0]) {
				assertLight(helper, reaching.ground, 14, 20, 0);
				helper.assertTrue(coming.fire(Ladder.Rung.COMING, hal, false, true, 94L) == FireResult.FIRED, "rung 2 with a friend 49 away");
				helper.assertTrue(reaching.fire(Ladder.Rung.GLIMPSE, ivy, false, true, 95L) == FireResult.FIRED, "rung 3");
				fired[0] = true;
			}
			helper.assertFalse(LadderRig.walking(hal) || LadderRig.walking(ivy), "the steps are still coming");
			double louder = 0;
			for (LadderRig.Heard step : coming.heard) {
				double distance = step.pos().distanceTo(hal.position());
				helper.assertTrue(SoundCues.hearers(g.level, List.of(hal), step.pos(), step.volume()).contains(hal), "Hal could not hear a step");
				helper.assertTrue(SoundCues.hearers(g.level, List.of(sam), step.pos(), step.volume()).isEmpty(), "Sam 49 away heard a step");
				double ear = gain(step.volume(), distance);
				helper.assertTrue(ear >= louder - 1.0E-9, "rung 2 grows quieter at the ear: " + ear + " after " + louder);
				louder = ear;
			}
			helper.assertTrue(coming.heard.size() >= coming.config.ladderComingStepsMin && gain(coming.heard.getFirst().volume(),
					coming.heard.getFirst().pos().distanceTo(hal.position())) > 0.3, "rung 2's first step is faint: " + coming.heard);
			long heardByIvy = reaching.heard.stream().filter(step -> SoundCues.hearers(g.level, List.of(ivy), step.pos(), step.volume()).contains(ivy)).count();
			helper.assertTrue(heardByIvy >= 3 && reaching.heard.stream().allMatch(step -> step.volume() <= 1.8F + 1.0E-6), "rung 3: " + reaching.heard);
			// The friend's worst place: just past 48 blocks from the player, on the line toward each step.
			Vec3 samWas = sam.position();
			for (LadderRig rig : List.of(coming, reaching)) {
				ServerPlayer player = rig == coming ? hal : ivy;
				for (LadderRig.Heard step : rig.heard) {
					Vec3 toward = step.pos().subtract(player.position()).normalize();
					Vec3 friend = player.position().add(toward.scale(48.01));
					sam.snapTo(friend.x, friend.y, friend.z, 0.0F, 0.0F);
					helper.assertTrue(SoundCues.hearers(g.level, List.of(sam), step.pos(), step.volume()).isEmpty(), "a friend 48.01 away heard a step at "
							+ step.volume() + ", " + step.pos().distanceTo(player.position()) + " from its player");
				}
			}
			sam.snapTo(samWas.x, samWas.y, samWas.z, 0.0F, 0.0F);
			g.keepLoaded(false);
			reaching.ground.keepLoaded(false);
		});
	}

	/**
	 * D-075, FIX-GLIMPSE: dig section version 2 steps v1.3's volumes and the blind torch's other-light radius to the new
	 * defaults only where they still equal v1.3's (a hand edit stays), adds the dark fields at their defaults, and a
	 * version 2 section is left alone.
	 */
	@GameTest
	public void aV13DigSectionStepsToVersionTwo(GameTestHelper helper) {
		DigConfig d = new DigConfig();
		helper.assertTrue(DigConfig.CURRENT_VERSION == 2 && d.blindTorchOtherLightRadius == 0 && d.ladderDarkFrom == 6 && d.ladderDarkTo == 24
				&& d.ladderGlimpseMaxTorches == 8, "the defaults");
		JsonObject v13 = JsonParser.parseString("{\"dig\": {\"version\": 1, \"ladderVolumeFar\": 0.7, \"ladderVolumeNear\": 0.9, \"ladderGlimpseVolumeMax\": 1.3,"
				+ " \"blindStepVolume\": 0.5, \"blindTorchOtherLightRadius\": 8, \"ladderMinRun\": 20}}").getAsJsonObject();
		DigConfig stepped = ModConfig.readSection(v13, "dig", DigConfig.class, DigConfig::new);
		helper.assertTrue(stepped.version == 2 && stepped.ladderVolumeFar == 1.5F && stepped.ladderVolumeNear == 1.0F && stepped.ladderGlimpseVolumeMax == 1.8F
				&& stepped.blindStepVolume == 1.0F && stepped.blindTorchOtherLightRadius == 0 && stepped.ladderMinRun == 20 && stepped.ladderDarkTo == 24
				&& stepped.ladderGlimpseMaxTorches == 8, "v1.3's defaults were not stepped");
		JsonObject edited = JsonParser.parseString("{\"dig\": {\"version\": 1, \"ladderVolumeFar\": 0.8, \"ladderVolumeNear\": 0.95, \"ladderGlimpseVolumeMax\": 1.5,"
				+ " \"blindStepVolume\": 0.6, \"blindTorchOtherLightRadius\": 12}}").getAsJsonObject();
		DigConfig kept = ModConfig.readSection(edited, "dig", DigConfig.class, DigConfig::new);
		helper.assertTrue(kept.version == 2 && kept.ladderVolumeFar == 0.8F && kept.ladderVolumeNear == 0.95F && kept.ladderGlimpseVolumeMax == 1.5F
				&& kept.blindStepVolume == 0.6F && kept.blindTorchOtherLightRadius == 12, "a hand edit was stepped");
		JsonObject two = JsonParser.parseString("{\"dig\": {\"version\": 2, \"blindTorchOtherLightRadius\": 8, \"ladderVolumeFar\": 0.7}}").getAsJsonObject();
		DigConfig left = ModConfig.readSection(two, "dig", DigConfig.class, DigConfig::new);
		helper.assertTrue(left.blindTorchOtherLightRadius == 8 && left.ladderVolumeFar == 0.7F && !left.upgrade(), "a version 2 section was stepped");
		helper.succeed();
	}

	// --- blind_torch (item 3) ---

	/**
	 * A room 44 x 44 (every chunk within 20 blocks of its middle loaded, as blind_torch asks) with the player in its middle
	 * (24, 2, 24) facing east, blind for 3 s.
	 */
	private static BlindRig room(GameTestHelper helper, int slot, String name) {
		DigGround g = DigGround.of(helper, slot, 48, 10, 48, Blocks.STONE);
		g.hollow(2, 2, 2, 45, 7, 45);
		BlindRig rig = new BlindRig(helper, g, name);
		ServerPlayer player = rig.stand(name, 24, 2, 24, LadderRig.EAST);
		rig.blind(player, 60);
		return rig;
	}

	/**
	 * One torch is enough: a single torch Alex placed 10 blocks behind him, his first placed block (his base by
	 * {@code PlayerWatch.base} without a bed), with no other light anywhere, goes; it is not a bed.
	 */
	@GameTest
	public void aLoneTorchBehindTenBlocksAwayGoes(GameTestHelper helper) {
		BlindRig rig = room(helper, 108, "Alex");
		DigGround g = rig.ground;
		ServerPlayer alex = rig.crew.online.getFirst();
		BlockPos torch = g.at(14, 2, 24);
		g.level.setBlock(torch, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
		rig.crew.watch.onPlaced(alex, g.level, torch, Blocks.TORCH.defaultBlockState());
		PlacedBlock first = rig.crew.state.firstBlocks(alex.getUUID()).block();
		helper.assertTrue(first != null && first.pos().pos().equals(torch) && rig.crew.bases().all(g.level.dimension()).contains(torch),
				"the torch is not Alex's first block and base");
		helper.assertTrue(rig.crew.bases().beds(g.level).isEmpty() && rig.config.blindTorchOtherLightRadius == 0, "a bed, or the other-light rule");
		helper.assertTrue(rig.check(Blind.Kind.TORCH, alex) == null && torch.equals(Blind.torch(alex, rig.env(), null)), "the lone torch may not go: "
				+ Blind.reason(Blind.Kind.TORCH, alex, rig.env(), null));
		helper.assertTrue(rig.fire(Blind.Kind.TORCH, alex, false, false, 1L) == FireResult.FIRED && g.level.getBlockState(torch).isAir()
				&& g.level.getBlockState(torch.below()).is(Blocks.STONE) && ledgered(g, torch, Blind.CAUSE_TORCH), "the lone torch did not go");
		rig.crew.watch.onBlindState(alex, false);
		helper.succeed();
	}

	/**
	 * The reasons: none placed 6 to 20 away; a torch in view never goes, and the arm's answer and {@code /a1016 dig blind}
	 * name the nearest; the torch within 6 of the respawn bed stays while it is the bed's only light, and goes once
	 * another light is there.
	 */
	@GameTest
	public void blindTorchSaysWhyNoTorchMayGo(GameTestHelper helper) {
		BlindRig rig = room(helper, 109, "Bo");
		DigGround g = rig.ground;
		ServerPlayer bo = rig.crew.online.getFirst();
		helper.assertTrue(Blind.reason(Blind.Kind.TORCH, bo, rig.env(), null).equals(
				"no player's torch or lantern that may go: no torch or lantern placed by a player 6 to 20 blocks away"), Blind.reason(Blind.Kind.TORCH, bo,
						rig.env(), null));
		BlockPos ahead = g.at(33, 2, 24); // 9 blocks in front of him
		g.place(ahead, Blocks.TORCH.defaultBlockState());
		String inView = "no player's torch or lantern that may go: all 1 in view (the nearest: torch at " + ahead.toShortString() + ", 9 blocks)";
		helper.assertTrue(rig.check(Blind.Kind.TORCH, bo) == Blind.Why.NO_TORCH && Blind.reason(Blind.Kind.TORCH, bo, rig.env(), null).equals(inView),
				Blind.reason(Blind.Kind.TORCH, bo, rig.env(), null));
		helper.assertTrue(rig.fire(Blind.Kind.TORCH, bo, false, false, 1L) == FireResult.NO_SPOT && g.level.getBlockState(ahead).is(Blocks.TORCH),
				"the torch in view went");
		String fired = BlindCues.firedLine(new BlindCues.Armed(Blind.Kind.TORCH.id, false, 0L), FireResult.NO_SPOT, bo, rig.env());
		helper.assertTrue(fired.equals("[a1016] dig: the armed blind_torch fired in your blind moment -> NO_SPOT: " + inView), fired);
		List<String> lines = Blind.debugLines(bo, rig.env());
		helper.assertTrue(lines.get(2).equals("blind_torch: no (" + inView + ")") && lines.get(4).startsWith(
				"blind_torch's torch, the blind moment aside: none may go (all 1 in view"), "lines " + lines);

		// The respawn bed's only light, behind him: it stays; another light by the bed, and it goes.
		BlockPos bed = g.at(15, 2, 24);
		g.level.setBlock(bed, Blocks.BED.red().defaultBlockState(), Block.UPDATE_CLIENTS);
		bo.setRespawnPosition(new RespawnConfig(LevelData.RespawnData.of(g.level.dimension(), bed, 0.0F, 0.0F), true), false);
		BlockPos byBed = g.at(18, 2, 24); // 3 from the bed, 6.5 from him
		g.place(byBed, Blocks.TORCH.defaultBlockState());
		helper.assertTrue(rig.crew.bases().beds(g.level).equals(List.of(bed)), "the beds: " + rig.crew.bases().beds(g.level));
		String reason = Blind.reason(Blind.Kind.TORCH, bo, rig.env(), null);
		helper.assertTrue(rig.check(Blind.Kind.TORCH, bo) == Blind.Why.NO_TORCH && reason.contains("1 in view (the nearest: torch at " + ahead.toShortString())
				&& reason.endsWith(", 1 the last light near a bed"), reason);
		g.level.setBlock(g.at(15, 2, 27), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(byBed.equals(Blind.torch(bo, rig.env(), null)) && rig.fire(Blind.Kind.TORCH, bo, false, false, 2L) == FireResult.FIRED
				&& g.level.getBlockState(byBed).isAir(), "the torch by the bed did not go once another light was there");
		rig.crew.watch.onBlindState(bo, false);
		helper.succeed();
	}
}
