package com.forzacode.a1016_02.atmosphere.card;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * A zombie standing still at dusk ("False positives"): at the edge of sight, a zombie stands perfectly still and
 * silent, facing the player, then goes back to being a zombie. The false positive is only a zombie looking at you,
 * which zombies do anyway.
 */
public final class ZombieAtDuskCard extends AtmosphereCard {
	public static final String ID = "zombie_at_dusk";

	public ZombieAtDuskCard() {
		super(ID, Tier.AMBIENT, Stage.ALONE, Set.of(), Set.of(CardTag.MOB), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		AtmosphereConfig cfg = cfg();
		return Gates.hasDayCycle(world) && Gates.inWindow(Gates.timeOfDay(world), cfg.duskFrom, cfg.duskTo) && !zombies(world, player).isEmpty();
	}

	private static List<Zombie> zombies(ServerLevel level, ServerPlayer player) {
		AtmosphereConfig cfg = cfg();
		double min2 = (double) cfg.zombieMinDistance * cfg.zombieMinDistance;
		return untampered(level, Zombie.class, player.position(), cfg.zombieRadius,
				z -> z.distanceToSqr(player) >= min2 && !z.isPassenger() && !z.isVehicle() && z.getTarget() == null);
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		Zombie zombie = zombies(ctx.level(), player).stream().min(Comparator.comparingDouble(z -> z.distanceToSqr(player))).orElse(null);
		if (zombie == null) {
			return FireResult.NO_SPOT;
		}
		int ticks = AtmosphereConfig.ticks(cfg().zombieStillSeconds);
		if (ctx.fake()) {
			int look = Math.max(AtmosphereConfig.ticks(cfg().zombieFakeMinSeconds), ticks / 4);
			return mobs().face(zombie, player.getEyePosition(), look) ? FireResult.FIRED : FireResult.SKIPPED;
		}
		boolean ok = mobs().freeze(zombie, ticks) && mobs().face(zombie, player.getEyePosition(), ticks) && mobs().silence(zombie, ticks);
		return ok ? FireResult.FIRED : FireResult.SKIPPED;
	}
}
