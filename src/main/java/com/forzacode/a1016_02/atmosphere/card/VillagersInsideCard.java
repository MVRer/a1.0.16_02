package com.forzacode.a1016_02.atmosphere.card;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
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
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Villagers inside at noon: in broad daylight every villager nearby is indoors, at a window, looking out, still.
 * Villagers are moved only while both they and their spot are out of view; ones already inside just stop and look.
 */
public final class VillagersInsideCard extends AtmosphereCard {
	public static final String ID = "villagers_inside_at_noon";
	private static final int SEARCH = 12;

	public VillagersInsideCard() {
		super(ID, Tier.AMBIENT, Stage.TRACES, Set.of(Habit.WATCHER), Set.of(CardTag.MOB), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		AtmosphereConfig cfg = cfg();
		return Gates.hasDayCycle(world) && world.isBrightOutside() && Gates.inWindow(Gates.timeOfDay(world), cfg.noonFrom, cfg.noonTo)
				&& !villagers(world, player).isEmpty();
	}

	private static List<Villager> villagers(ServerLevel level, ServerPlayer player) {
		return untampered(level, Villager.class, player.position(), cfg().villagerRadius, v -> !v.isPassenger() && !v.isSleeping());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerLevel level = ctx.level();
		int ticks = AtmosphereConfig.ticks(cfg().villagerWindowSeconds);
		Set<BlockPos> used = new HashSet<>();
		int placed = 0;
		for (Villager villager : villagers(level, ctx.player())) {
			WorldScan.WindowSpot spot = WorldScan.windowSpot(level, villager.blockPosition(), SEARCH, used);
			if (spot == null) {
				continue;
			}
			boolean there = villager.blockPosition().equals(spot.feet());
			if (!there && !(Services.traces().isOutOfView(level, villager.getBoundingBox()) && mobs().moveOutOfView(villager, spot.feet()))) {
				continue;
			}
			used.add(spot.feet());
			Vec3 outside = Vec3.atCenterOf(spot.window()).add(spot.outward().getStepX() * 16.0, 0.0, spot.outward().getStepZ() * 16.0);
			mobs().freeze(villager, ticks);
			mobs().face(villager, outside, ticks);
			mobs().silence(villager, ticks);
			placed++;
		}
		return placed > 0 ? FireResult.FIRED : FireResult.NO_SPOT;
	}
}
