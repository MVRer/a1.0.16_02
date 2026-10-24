package com.forzacode.a1016_02.world;

import java.util.List;

import com.forzacode.a1016_02.world.sc.ScEnv;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc2.s17.ArchiveTestKit;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * SC2-2's game tests (docs/SELF_CREATED_2.md, feature 17, and "Tasks"): the archive's plan, siting and shape (lit, no
 * fall over 3, its records, its ways in), worldgen equal to the gallery's build, its interior, and its rules and answers:
 * 17a records for three players, 17b the lights (order, fair danger, the span's dark, the glimpse, the day, the ledger),
 * the lights waiting out of view, the dark between two friends apart and in the cells, 17c the other door (newcomer and
 * finder, conserved, caps, given back), 17d a marker and a friend's, 17e a book of one's own, and 11+ t0rch's trail into
 * the cells. Each test builds at its own far-off spot (x 84 000 to 89 100, z 85 000; the trail at z 86 500); the bodies live beside the archive
 * ({@link ArchiveTestKit}) to reach its package's parts.
 */
public class SelfCreated2ArchiveTests {
	@GameTest
	public void archiveMazesReachEveryCellHaveLoopsAndRepeat(GameTestHelper helper) {
		ArchiveTestKit.mazes(helper);
	}

	@GameTest
	public void archiveSitingKeepsTheHallDeepFacingTheOriginWithinReach(GameTestHelper helper) {
		ArchiveTestKit.siting(helper);
	}

	@GameTest(maxTicks = 400)
	public void archiveIsLitHasNoFallOverThreeHoldsTheListAndItsWaysIn(GameTestHelper helper) {
		ArchiveTestKit.shape(helper);
	}

	@GameTest(maxTicks = 400)
	public void archiveLongHallIsLitAndHasNoFallOverThree(GameTestHelper helper) {
		ArchiveTestKit.shapeLong(helper);
	}

	@GameTest
	public void archiveInteriorIsTheHallAndTheCellsNotTheShellOrTheWayIn(GameTestHelper helper) {
		ArchiveTestKit.interior(helper);
	}

	@GameTest(environment = ArchiveTestKit.ENV, maxTicks = 400)
	public void archiveBuiltByWorldgenEqualsLivePlace(GameTestHelper helper) {
		ArchiveTestKit.worldgenEqualsLive(helper);
	}

	@GameTest(maxTicks = 400)
	public void recordsHoldTheListAndOneForEachOfThreeFriendsNeverTwice(GameTestHelper helper) {
		ArchiveTestKit.records(helper);
	}

	@GameTest(maxTicks = 400)
	public void lightsGoFromTheWayOutTowardThePlayerNeverOverThemAndAGlimpseFollows(GameTestHelper helper) {
		ArchiveTestKit.lightsOut(helper);
	}

	@GameTest(maxTicks = 400)
	public void lightsWaitWhileARowIsInView(GameTestHelper helper) {
		ArchiveTestKit.lightsWaitOutOfView(helper);
	}

	@GameTest(maxTicks = 400)
	public void lightsFallBetweenTwoFriendsApartAndOnTheCellsOutForOneInThem(GameTestHelper helper) {
		ArchiveTestKit.lightsBetweenFriends(helper);
	}

	@GameTest(maxTicks = 400)
	public void otherDoorMovesTwelveForANewcomerWithTheFinderFarWithinItsCaps(GameTestHelper helper) {
		ArchiveTestKit.otherDoor(helper);
	}

	@GameTest(maxTicks = 400)
	public void theRulesKeepTheirGatesOnEveryTry(GameTestHelper helper) {
		ArchiveTestKit.rulesKeepTheirGatesOnEveryTry(helper);
	}

	@GameTest(maxTicks = 400)
	public void markerMovesToACellThatLooksTheSameAFriendsToo(GameTestHelper helper) {
		ArchiveTestKit.markers(helper);
	}

	@GameTest(maxTicks = 400)
	public void ownBookGoesToTheDaisWallOnceAndComesBack(GameTestHelper helper) {
		ArchiveTestKit.ownBook(helper);
	}

	/**
	 * 11+: with an archive's cells 4 off a cave corridor nobody visited, t0rch's trail ends at a hole dug from the walk
	 * through the cells' wall into a lit cell: the torches on the right-hand wall, "brb" 2 to 5 cells before the walk's
	 * end, the hole open, left by others, once per world; every rule of 11's walk and view kept (its own tests stay).
	 */
	@GameTest(maxTicks = 400)
	public void t0rchsTrailCanEndAtAHoleIntoTheCells(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArchiveTestKit.TrailRig rig = ArchiveTestKit.trailRig(level, 86_600, 86_500);
		TorchTrail.Breach before = TorchTrail.breach();
		try {
			TorchTrail.setBreach(rig.breach());
			Friends friends = SelfCreatedWorldTrailTests.friends(helper, new BlockPos(20, SelfCreatedWorldTrailTests.FEET_Y, 400));
			ScEnv env = SelfCreatedWorldTrailTests.env(friends, false);
			TorchTrail.Config config = SelfCreatedWorldTrailTests.caveConfig();
			TorchTrail.Result result = TorchTrail.leaveFor(env, SelfCreatedWorldTrailTests.target(friends), List.of(rig.spot()),
					SelfCreatedWorldTrailTests.onlySpot(rig.spot()), config, RandomSource.create(5), false);
			helper.assertTrue(result.breach() != null && result.plan() == null, "no breach: " + result.reason());
			TorchTrail.BreachPlan plan = result.breach();
			helper.assertTrue(plan.path().getLast().equals(rig.end()), "the walk ends at " + plan.path().getLast().toShortString());
			helper.assertTrue(rig.inCell().test(plan.beyond()) && level.getBlockState(plan.beyond()).isAir(), "beyond the hole is no cell");
			for (BlockPos dug : plan.way().dug()) {
				helper.assertTrue(level.getBlockState(dug).isAir(), "the hole is shut at " + dug.toShortString());
			}
			int signAt = plan.path().indexOf(plan.sign().pos().below());
			int back = plan.path().size() - 1 - signAt;
			helper.assertTrue(signAt >= 0 && back >= 2 && back <= 5, "the sign is " + back + " cells before the end");
			helper.assertTrue(level.getBlockState(plan.sign().pos()).getBlock() instanceof WallSignBlock
					&& level.getBlockEntity(plan.sign().pos()) instanceof SignBlockEntity sign
					&& sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).getFirst().getString().equals("brb"), "no brb");
			helper.assertTrue(!plan.torches().isEmpty(), "no torches");
			for (TorchTrail.Left torch : plan.torches()) {
				helper.assertTrue(level.getBlockState(torch.pos()).getBlock() instanceof WallTorchBlock, "a torch is missing");
			}
			helper.assertTrue(env.marks().trails().size() == 1, "not kept once per world");
			TorchTrail.Result again = TorchTrail.leaveFor(env, SelfCreatedWorldTrailTests.target(friends), List.of(rig.spot()),
					SelfCreatedWorldTrailTests.onlySpot(rig.spot()), config, RandomSource.create(6), false);
			helper.assertTrue(!again.found(), "a second trail in one world");
		} finally {
			TorchTrail.setBreach(before);
		}
		helper.succeed();
	}
}
