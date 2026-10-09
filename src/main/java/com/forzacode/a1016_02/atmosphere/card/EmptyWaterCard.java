package com.forzacode.a1016_02.atmosphere.card;

import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.squid.Squid;

/**
 * Empty water: no fish or squid left in the water around the player. They are all moved, at once and only when every
 * one of them is out of view, into open water far away. The water is still.
 */
public final class EmptyWaterCard extends AtmosphereCard {
	public static final String ID = "empty_water";
	private static final int MIN_CREATURES = 2;

	public EmptyWaterCard() {
		super(ID, Tier.AMBIENT, Stage.TRACES, Set.of(Habit.COLLECTOR), Set.of(CardTag.MOB), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return creatures(world, player).size() >= MIN_CREATURES;
	}

	/** Wild fish and squid only: the player's own (bucketed into an aquarium, or named) are left alone. */
	private static List<Mob> creatures(ServerLevel level, ServerPlayer player) {
		return untampered(level, Mob.class, player.position(), cfg().waterRadius,
				mob -> (mob instanceof AbstractFish fish && !fish.fromBucket() || mob instanceof Squid) && !mob.isPersistenceRequired()
						&& mob.isInWater() && !mob.isLeashed() && !mob.isPassenger());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		List<Mob> creatures = creatures(level, player);
		if (creatures.isEmpty()) {
			return FireResult.SKIPPED;
		}
		for (Mob mob : creatures) {
			if (!Services.traces().isOutOfView(level, mob.getBoundingBox())) {
				return FireResult.NO_SPOT;
			}
		}
		AtmosphereConfig cfg = cfg();
		List<BlockPos> spots = WorldScan.waterSpots(level, player.position(), cfg.waterMoveMinBlocks, cfg.waterMoveMaxBlocks, ctx.random(), 6);
		if (spots.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		int moved = 0;
		for (int i = 0; i < creatures.size(); i++) {
			Mob mob = creatures.get(i);
			for (int t = 0; t < spots.size(); t++) {
				if (mobs().moveOutOfView(mob, spots.get((i + t) % spots.size()))) {
					moved++;
					break;
				}
			}
		}
		return moved > 0 ? FireResult.FIRED : FireResult.NO_SPOT;
	}
}
