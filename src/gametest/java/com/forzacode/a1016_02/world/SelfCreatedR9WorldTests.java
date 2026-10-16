package com.forzacode.a1016_02.world;

import com.forzacode.a1016_02.world.sc.ScEnv;
import com.forzacode.a1016_02.world.sc.TorchTrail;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;

/**
 * Review R9 (docs/SELF_CREATED.md, 11; DESIGN guardrail 1 and "How death works"): t0rch's trail is a lure the player is
 * meant to follow to its end and read. Built on {@link SelfCreatedWorldTrailTests}' cave (a natural 1x2 corridor to a
 * dead end over a lava pocket); nothing is left in the world (only the plan is looked at).
 */
public class SelfCreatedR9WorldTests {
	/**
	 * The sign hangs on the wall right over the hole, so the only way to read "brb" up close is to step to the shaft's
	 * lip: one step more is an 8 to 30 block fall into lava, at Traces (before any accident may come), not claimed by any
	 * trap (no cross, no cause on the list; in hardcore the run ends there). The sign must be read from solid floor that is
	 * not the hole's own column.
	 */
	@GameTest(structure = SelfCreatedWorldTrailTests.CAVE, maxTicks = 40)
	public void t0rchsSignIsNeverReadFromTheLipOfTheShaft(GameTestHelper helper) {
		BlockPos spot = SelfCreatedWorldTrailTests.cave(helper, null, true);
		Friends friends = SelfCreatedWorldTrailTests.friends(helper, new BlockPos(20, SelfCreatedWorldTrailTests.FEET_Y, 400));
		ScEnv env = SelfCreatedWorldTrailTests.env(friends, false);
		TorchTrail.Result result = TorchTrail.search(env, helper.getLevel(), spot, SelfCreatedWorldTrailTests.caveConfig(),
				SelfCreatedWorldTrailTests.onlySpot(spot), RandomSource.create(3));
		helper.assertTrue(result.plan() != null, "setup: no plan: " + result.reason());
		TorchTrail.Plan plan = result.plan();
		BlockPos sign = plan.sign().pos();
		BlockPos hole = plan.hole();
		boolean overTheShaft = sign.getX() == hole.getX() && sign.getZ() == hole.getZ();
		helper.assertFalse(overTheShaft, "the sign at " + sign.toShortString() + " hangs over the hole at " + hole.toShortString() + " (a "
				+ plan.shaft().size() + "-block shaft to lava at " + plan.lava().toShortString() + "): walking up to read it is the step into the shaft");
		helper.succeed();
	}
}
