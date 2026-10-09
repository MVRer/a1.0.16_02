package com.forzacode.a1016_02.atmosphere.card;

import java.util.Set;

import com.forzacode.a1016_02.atmosphere.ActiveEffects;
import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Compass drift: for about a minute the player's compass points somewhere other than spawn (a recorded site if one
 * is in range, otherwise open ground well off the spawn line). Follow it and it settles back before you arrive.
 * Client side: payload {@code a1016_02:atmosphere/compass_drift} and the compass mixin.
 */
public final class CompassDriftCard extends AtmosphereCard {
	public static final String ID = "compass_drift";
	private static final double MIN_ANGLE_OFF_SPAWN = Math.toRadians(60);

	public CompassDriftCard() {
		super(ID, Tier.MINOR, Stage.PROXIMITY, Set.of(Habit.WATCHER), Set.of(CardTag.ITEM), false);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		GlobalPos spawn = world.getRespawnData().globalPos();
		return spawn.dimension() == world.dimension() && hasSpawnCompass(player) && !ActiveEffects.compassActive(player);
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		AtmosphereConfig cfg = cfg();
		Vec3 at = player.position();
		Vec3 spawn = Vec3.atCenterOf(level.getRespawnData().globalPos().pos());
		double spawnAngle = Math.atan2(spawn.z - at.z, spawn.x - at.x);

		Vec3 target = siteTarget(level, at, spawnAngle, cfg);
		if (target == null) {
			double off = MIN_ANGLE_OFF_SPAWN + ctx.random().nextDouble() * Math.toRadians(90);
			double angle = spawnAngle + (ctx.random().nextBoolean() ? off : -off);
			double dist = cfg.compassDriftMinBlocks + ctx.random().nextDouble() * Math.max(1, cfg.compassDriftMaxBlocks - cfg.compassDriftMinBlocks);
			target = at.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
		}
		ActiveEffects.compassDrift(player, Mth.floor(target.x), Mth.floor(target.z), AtmosphereConfig.ticks(cfg.compassDriftSeconds), cfg.compassSettleBlocks);
		return FireResult.FIRED;
	}

	/** The nearest recorded site in range and well off the spawn line, if any. */
	private static Vec3 siteTarget(ServerLevel level, Vec3 at, double spawnAngle, AtmosphereConfig cfg) {
		Vec3 best = null;
		double bestDist = Double.MAX_VALUE;
		double min = cfg.compassDriftMinBlocks;
		double max = cfg.compassDriftMaxBlocks * 1.5;
		for (SiteRegistry.Site site : Services.sites().all()) {
			if (site.dimension() != level.dimension()) {
				continue;
			}
			Vec3 pos = Vec3.atCenterOf(site.pos());
			double dist = Math.sqrt((pos.x - at.x) * (pos.x - at.x) + (pos.z - at.z) * (pos.z - at.z));
			double angle = Math.atan2(pos.z - at.z, pos.x - at.x);
			double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - spawnAngle)));
			if (dist >= min && dist <= max && off >= Math.toDegrees(MIN_ANGLE_OFF_SPAWN) && dist < bestDist) {
				best = pos;
				bestDist = dist;
			}
		}
		return best;
	}

	/** A plain compass (no lodestone) anywhere in the inventory. */
	static boolean hasSpawnCompass(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.COMPASS) && !stack.has(DataComponents.LODESTONE_TRACKER)) {
				return true;
			}
		}
		return false;
	}
}
