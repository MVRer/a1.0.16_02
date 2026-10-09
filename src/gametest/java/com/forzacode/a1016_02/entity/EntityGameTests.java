package com.forzacode.a1016_02.entity;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the entity workstream, registered in the gametest fabric.mod.json. The figure's physics here; the
 * sighting gates and spawn spots in {@link SightingRuleGameTests}.
 */
public class EntityGameTests extends SightingRuleGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static HimEntity figure(GameTestHelper helper, Variant variant, Vec3 relative) {
		// No players in the test level, so every spot is out of view.
		return FigureApi.spawnAt(helper.getLevel(), variant, helper.absoluteVec(relative), 0.0F)
				.orElseThrow(() -> helper.assertionException("the figure did not spawn"));
	}

	@GameTest
	public void figureNeverTargetsOrAttacks(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		HimEntity him = figure(helper, Variant.CLOSE, new Vec3(4.5, 1.0, 4.5));
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)), 0.0F, 0.0F);

		helper.assertTrue(him.goalCount() == 0, "he has goals: " + him.goalCount());
		him.setTarget(player);
		helper.assertTrue(him.getTarget() == null, "he took a target");
		helper.assertFalse(him.canAttack(player), "he can attack the player");
		helper.assertFalse(him.doHurtTarget(level, player), "he hurt the player");
		helper.assertFalse(him.canBeSeenAsEnemy(), "mobs can see him as an enemy");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(him.getTarget() == null && him.goalCount() == 0, "he picked a target or a goal while ticking");
			him.discard();
			helper.succeed();
		});
	}

	@GameTest
	public void figureCannotBeHitOrHurt(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		HimEntity him = figure(helper, Variant.RIDGE, new Vec3(4.5, 1.0, 4.5));
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		float health = him.getHealth();

		helper.assertFalse(him.isPickable(), "pickable (the crosshair can target him)");
		helper.assertFalse(him.isAttackable() || him.attackable(), "attackable");
		helper.assertTrue(him.skipAttackInteraction(player), "a punch reaches him");
		helper.assertFalse(him.canBeHitByProjectile(), "projectiles hit him");
		helper.assertTrue(him.isInvulnerable(), "not invulnerable");
		helper.assertFalse(him.hurtServer(level, level.damageSources().playerAttack(player), 10.0F), "a player attack hurt him");
		helper.assertFalse(him.hurtServer(level, level.damageSources().generic(), 10.0F), "generic damage hurt him");
		helper.assertTrue(him.getHealth() == health && him.isAlive(), "he lost health");
		helper.assertFalse(him.canBeLeashed() || him.shouldShowName() || him.isCurrentlyGlowing(), "leash, name or glow");
		him.discard();
		helper.succeed();
	}

	@GameTest
	public void figureCannotBePushedOrPush(GameTestHelper helper) {
		floor(helper);
		HimEntity him = figure(helper, Variant.RIDGE, new Vec3(3.5, 1.0, 3.5));
		Cow cow = helper.spawn(EntityTypes.COW, new Vec3(3.7, 1.0, 3.5));
		cow.setNoAi(true);

		helper.assertFalse(him.isPushable() || him.isPushedByFluid(), "pushable");
		helper.assertFalse(him.canBeCollidedWith(cow), "collidable");
		him.push(1.0, 0.5, 1.0);
		him.push(new Vec3(1.0, 0.0, 1.0));
		him.push(cow);
		helper.assertTrue(him.getDeltaMovement().horizontalDistanceSqr() < 1.0E-8, "a push moved him: " + him.getDeltaMovement());
		Vec3 himAt = him.position();
		Vec3 cowAt = cow.position();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(him.position().subtract(himAt).horizontalDistance() < 1.0E-3,
					"he was pushed: " + himAt + " -> " + him.position());
			helper.assertTrue(cow.position().subtract(cowAt).horizontalDistance() < 1.0E-3, "he pushed the cow: " + cowAt + " -> " + cow.position());
			him.discard();
			cow.discard();
			helper.succeed();
		});
	}

	@GameTest
	public void figureIsNeverSavedNorCounted(GameTestHelper helper) {
		floor(helper);
		HimEntity him = figure(helper, Variant.COW, new Vec3(4.5, 1.0, 4.5));
		helper.assertFalse(him.shouldBeSaved(), "he would be saved");
		helper.assertFalse(ModEntities.HIM.canSerialize(), "his type can be saved");
		helper.assertFalse(him.save(TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING)), "he wrote himself to a chunk");
		helper.assertTrue(ModEntities.HIM.getCategory() == MobCategory.MISC, "he counts toward a mob cap");
		helper.assertTrue(him.isLow(), "the cow variant does not start low");
		him.discard();
		helper.succeed();
	}
}
