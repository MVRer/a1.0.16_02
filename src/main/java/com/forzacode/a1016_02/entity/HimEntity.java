package com.forzacode.a1016_02.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HimEntity extends PathfinderMob {
	private static final double SPAWN_DISTANCE = 10.0;
	private static final int GROUND_SEARCH_RANGE = 8;

	public HimEntity(EntityType<? extends HimEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes();
	}

	@Override
	protected void registerGoals() {
		// Just stands there and stares at the nearest player
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 64.0F, 1.0F));
	}

	public static void spawnInFrontOf(ServerPlayer player) {
		ServerLevel level = player.level();
		HimEntity him = ModEntities.HIM.create(level, EntitySpawnReason.EVENT);
		if (him == null) {
			return;
		}

		// Horizontal look direction only, so looking up/down doesn't move the spawn point
		Vec3 forward = Entity.calculateViewVector(0.0F, player.getYRot());
		BlockPos pos = findStandingSpot(level, BlockPos.containing(player.position().add(forward.scale(SPAWN_DISTANCE))));
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		float yaw = (float) Mth.atan2(player.getZ() - z, player.getX() - x) * Mth.RAD_TO_DEG - 90.0F;

		him.snapTo(x, pos.getY(), z, yaw, 0.0F);
		him.setYHeadRot(yaw);
		him.setYBodyRot(yaw);
		him.setPersistenceRequired();
		level.addFreshEntity(him);
	}

	// Looks above and below the target for a two-block-tall gap with ground underneath
	private static BlockPos findStandingSpot(ServerLevel level, BlockPos start) {
		for (int i = 0; i <= GROUND_SEARCH_RANGE * 2; i++) {
			int dy = i % 2 == 0 ? i / 2 : -(i + 1) / 2; // 0, -1, 1, -2, 2, ...
			BlockPos pos = start.above(dy);
			if (isPassable(level, pos) && isPassable(level, pos.above()) && !isPassable(level, pos.below())) {
				return pos;
			}
		}
		return start;
	}

	private static boolean isPassable(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
