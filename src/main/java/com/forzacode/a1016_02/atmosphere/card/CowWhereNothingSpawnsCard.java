package com.forzacode.a1016_02.atmosphere.card;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.cow.Cow;

/**
 * A cow where nothing spawns: one existing cow, moved out of view onto a dead mountain, where no animal ever spawns.
 * It stands there, quiet. It's just a cow. Needs a {@code DEAD_MOUNTAIN} site in range, otherwise {@code NO_SPOT}.
 */
public final class CowWhereNothingSpawnsCard extends AtmosphereCard {
	public static final String ID = "cow_where_nothing_spawns";

	public CowWhereNothingSpawnsCard() {
		super(ID, Tier.MINOR, Stage.TRACES, Set.of(Habit.WATCHER), Set.of(CardTag.MOB), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return !cows(world, player).isEmpty();
	}

	private static List<Cow> cows(ServerLevel level, ServerPlayer player) {
		return untampered(level, Cow.class, player.position(), cfg().cowSearchRadius, cow -> !cow.isLeashed() && !cow.isPassenger() && !cow.isVehicle());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		AtmosphereConfig cfg = cfg();
		List<SiteRegistry.Site> sites = Services.sites().find(SiteType.DEAD_MOUNTAIN, GlobalPos.of(level.dimension(), player.blockPosition()), cfg.deadMountainSearchRadius);
		if (sites.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		List<Cow> cows = cows(level, player).stream()
				.filter(cow -> Services.traces().isOutOfView(level, cow.getBoundingBox()))
				.sorted(Comparator.comparingDouble(cow -> cow.distanceToSqr(player)))
				.toList();
		if (cows.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		int minDistance = cfg.cowMinDistance;
		for (SiteRegistry.Site site : sites) {
			BlockPos spot = spotOn(level, site, player, minDistance, ctx.random());
			if (spot == null) {
				continue;
			}
			for (Cow cow : cows) {
				if (mobs().moveOutOfView(cow, spot)) {
					int ticks = AtmosphereConfig.ticks(cfg.cowStandSeconds);
					mobs().freeze(cow, ticks);
					mobs().silence(cow, ticks);
					return FireResult.FIRED;
				}
			}
		}
		return FireResult.NO_SPOT;
	}

	/** A surface standing spot on the site, at least {@code minDistance} from the player and out of view. */
	private static BlockPos spotOn(ServerLevel level, SiteRegistry.Site site, ServerPlayer player, int minDistance, RandomSource random) {
		int size = Math.max(2, site.size());
		double min2 = (double) minDistance * minDistance;
		for (int attempt = 0; attempt < 24; attempt++) {
			int x = site.pos().getX() + random.nextInt(size * 2 + 1) - size;
			int z = site.pos().getZ() + random.nextInt(size * 2 + 1) - size;
			BlockPos spot = WorldScan.surfaceSpot(level, x, z, 2);
			if (spot != null && spot.distToCenterSqr(player.position()) >= min2 && Services.traces().isOutOfView(level, spot)) {
				return spot;
			}
		}
		return null;
	}
}
