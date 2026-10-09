package com.forzacode.a1016_02.atmosphere;

import java.util.List;

import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** MobTamper game tests: freeze, face, silence, expiry, release, unload and the out-of-view move. */
public class TamperGameTests extends DeadMountainGameTests {
	static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest(maxTicks = 200)
	public void tamperFreezeHoldsStillAndReleaseResumes(GameTestHelper helper) {
		floor(helper);
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		helper.assertTrue(Services.mobs() == tamper, "the real MobTamper is not installed");
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
		Vec3 goal = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
		helper.assertTrue(tamper.freeze(cow, 400), "freeze refused");
		Vec3[] start = {cow.position()};
		cow.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.2);
		helper.startSequence()
				.thenExecuteAfter(40, () -> {
					helper.assertTrue(tamper.isFrozen(cow), "not frozen any more");
					helper.assertTrue(horizontal(cow.position(), start[0]) < 0.3, "a frozen cow walked " + horizontal(cow.position(), start[0]));
					tamper.release(cow);
					helper.assertFalse(tamper.isTampered(cow) || tamper.isFrozen(cow), "release left state behind");
					helper.assertFalse(tamper.tracked().contains(cow), "release left the cow tracked");
					start[0] = cow.position();
					cow.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.2);
				})
				.thenExecuteAfter(60, () -> helper.assertTrue(horizontal(cow.position(), start[0]) > 0.75,
						"a released cow did not walk again (" + horizontal(cow.position(), start[0]) + ")"))
				.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void tamperFaceTurnsTowardThePoint(GameTestHelper helper) {
		floor(helper);
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		Cow frozen = helper.spawn(EntityTypes.COW, new BlockPos(2, 1, 2));
		frozen.snapTo(frozen.getX(), frozen.getY(), frozen.getZ(), 0.0F, 0.0F);
		frozen.setYHeadRot(0.0F);
		frozen.setYBodyRot(0.0F);
		Vec3 east = frozen.position().add(20.0, 1.0, 0.0);
		float wanted = MobTamperImpl.yawToward(frozen.position(), east);
		helper.assertTrue(tamper.freeze(frozen, 200) && tamper.face(frozen, east, 200), "freeze/face refused");

		Cow free = helper.spawn(EntityTypes.COW, new BlockPos(5, 1, 5));
		Vec3 north = free.position().add(0.0, 1.0, -20.0);
		helper.assertTrue(tamper.face(free, north, 200), "face refused");

		helper.startSequence()
				.thenExecuteAfter(40, () -> {
					helper.assertTrue(Math.abs(Mth.wrapDegrees(frozen.getYHeadRot() - wanted)) < 5.0F, "head yaw " + frozen.getYHeadRot() + ", wanted " + wanted);
					helper.assertTrue(Math.abs(Mth.wrapDegrees(frozen.yBodyRot - wanted)) < 5.0F, "body yaw " + frozen.yBodyRot + ", wanted " + wanted);
					helper.assertTrue(tamper.isFacing(free) && !tamper.isFrozen(free), "free cow state");
					helper.assertTrue(Math.abs(free.getLookControl().getWantedX() - north.x) < 1.0E-3
							&& Math.abs(free.getLookControl().getWantedZ() - north.z) < 1.0E-3, "a facing cow looks elsewhere");
					tamper.release(frozen);
					tamper.release(free);
					helper.assertFalse(tamper.isFacing(frozen) || tamper.isFacing(free), "release kept facing");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 60)
	public void tamperExpiresAndUnloadReleases(GameTestHelper helper) {
		floor(helper);
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		Cow brief = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
		Cow gone = helper.spawn(EntityTypes.COW, new BlockPos(5, 1, 5));
		helper.assertTrue(tamper.freeze(brief, 10) && tamper.silence(brief, 10), "freeze/silence refused");
		helper.assertTrue(tamper.isFrozen(brief) && tamper.isSilenced(brief), "effects not active");
		helper.assertTrue(tamper.freeze(gone, 1000), "freeze refused");
		helper.startSequence()
				.thenExecuteAfter(15, () -> {
					helper.assertFalse(tamper.isFrozen(brief) || tamper.isSilenced(brief), "effects outlived their ticks");
					helper.assertFalse(tamper.isTampered(brief) || tamper.tracked().contains(brief), "expired state not cleaned up");
					gone.discard();
				})
				.thenExecuteAfter(2, () -> helper.assertFalse(tamper.isTampered(gone) || tamper.tracked().contains(gone), "an unloaded mob kept its state"))
				.thenSucceed();
	}

	@GameTest
	public void tamperRefusesBadRequests(GameTestHelper helper) {
		floor(helper);
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 1, 3));
		helper.assertFalse(tamper.freeze(cow, 0), "freeze for 0 ticks");
		helper.assertFalse(tamper.face(cow, cow.position(), -5), "face for negative ticks");
		helper.assertFalse(tamper.silence(cow, 0), "silence for 0 ticks");
		helper.assertFalse(tamper.isTampered(cow), "refused calls left state");
		cow.discard();
		helper.assertFalse(tamper.freeze(cow, 100), "froze a removed mob");
		helper.assertFalse(tamper.moveOutOfView(cow, helper.absolutePos(new BlockPos(5, 1, 5))), "moved a removed mob");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void tamperMovesOnlyWhenBothEndsAreOutOfView(GameTestHelper helper) {
		floor(helper);
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		// No real players are in the test level, so every place is out of view.
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
		BlockPos dest = helper.absolutePos(new BlockPos(6, 1, 6));
		helper.assertTrue(tamper.moveOutOfView(cow, dest), "move refused with nobody looking");
		helper.assertTrue(cow.blockPosition().equals(dest), "cow is at " + cow.blockPosition() + ", not " + dest);

		helper.setBlock(2, 1, 6, Blocks.STONE);
		helper.setBlock(2, 2, 6, Blocks.STONE);
		helper.assertFalse(tamper.moveOutOfView(cow, helper.absolutePos(new BlockPos(2, 1, 6))), "moved into a solid block");
		helper.assertTrue(cow.blockPosition().equals(dest), "a refused move moved the cow");

		// Geometry: a wall at z=3, a viewer at z=0.5 looking south toward it.
		for (int x = 0; x < 8; x++) {
			for (int y = 1; y < 8; y++) {
				helper.setBlock(x, y, 3, Blocks.STONE);
			}
		}
		Player viewer = helper.makeMockServerPlayer(GameType.SURVIVAL);
		viewer.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 0.5)), 0.0F, 0.0F);
		List<TraceService.Viewer> viewers = List.of(TraceService.Viewer.of(viewer, 8));
		AABB seen = cowBox(helper, new Vec3(6.5, 1.0, 2.0));
		AABB hiddenA = cowBox(helper, new Vec3(1.5, 1.0, 6.0));
		AABB hiddenB = cowBox(helper, new Vec3(5.5, 1.0, 6.0));
		helper.assertFalse(MobTamperImpl.bothOutOfView(helper.getLevel(), seen, hiddenA, viewers), "moved away from under the viewer's eyes");
		helper.assertFalse(MobTamperImpl.bothOutOfView(helper.getLevel(), hiddenA, seen, viewers), "moved into the viewer's sight");
		helper.assertTrue(MobTamperImpl.bothOutOfView(helper.getLevel(), hiddenA, hiddenB, viewers), "refused a move behind the wall");
		helper.succeed();
	}

	private static AABB cowBox(GameTestHelper helper, Vec3 relativeFeet) {
		Vec3 feet = helper.absoluteVec(relativeFeet);
		return new AABB(feet.x - 0.45, feet.y, feet.z - 0.45, feet.x + 0.45, feet.y + 1.4, feet.z + 0.45);
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
