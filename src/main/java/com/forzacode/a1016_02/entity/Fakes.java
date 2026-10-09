package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/**
 * False positives: a cow at the fog edge that is only a cow, or a zombie standing still at dusk. They use mobs that
 * already exist, held still and facing the player through {@code MobTamper}, and only while they are out of view.
 * Never spawns anything.
 */
final class Fakes {
	private Fakes() {
	}

	static FireResult fire(ServerPlayer player, Variant.Fake kind) {
		if (kind == Variant.Fake.NONE) {
			return FireResult.SKIPPED;
		}
		Optional<Mob> mob = find(player, kind);
		if (mob.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		int ticks = (int) ModConfig.realTicks(EntityConfig.get().fakeHoldSeconds);
		if (!Services.mobs().freeze(mob.get(), ticks)) {
			// The MobTamper stub (until atmosphere installs the real one) does nothing.
			return FireResult.SKIPPED;
		}
		Services.mobs().face(mob.get(), player.getEyePosition(), ticks);
		ServerLevel level = player.level();
		EntityData.get(level.getServer()).recordFake(GlobalPos.of(level.dimension(), mob.get().blockPosition()), GameClock.day(level.getServer()));
		return FireResult.FIRED;
	}

	/** The existing mob of that kind nearest the fog edge, between the minimum distance and the edge, out of view. */
	static Optional<Mob> find(ServerPlayer player, Variant.Fake kind) {
		ServerLevel level = player.level();
		FogEdge edge = FogEdge.of(player, false);
		double min = Math.max(EntityConfig.get().minDistance(), edge.outer() * 0.6);
		double max = edge.outer() + 4.0;
		List<Mob> candidates = new ArrayList<>();
		for (EntityType<? extends Mob> type : types(kind)) {
			candidates.addAll(level.getEntities(type, mob -> {
				double d = SpotFinder.horizontal(player.position(), mob.position());
				return mob.isAlive() && !mob.isBaby() && !mob.hasCustomName() && !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle()
						&& d >= min && d <= max && Services.traces().isOutOfView(level, mob.getBoundingBox());
			}));
		}
		BlockPos at = player.blockPosition();
		return candidates.stream().max(Comparator.comparingDouble(mob -> mob.distanceToSqr(at.getX(), at.getY(), at.getZ())));
	}

	private static List<EntityType<? extends Mob>> types(Variant.Fake kind) {
		return switch (kind) {
			case COW -> List.of(EntityTypes.COW, EntityTypes.MOOSHROOM);
			case ZOMBIE -> List.of(EntityTypes.ZOMBIE, EntityTypes.HUSK);
			case NONE -> List.of();
		};
	}
}
