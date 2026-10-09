package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The figure, for other workstreams (the endings): spawn a variant at a position or at a player's fog edge, make
 * him walk away, see whether he is out. He only ever spawns out of view of every player. Server thread only.
 */
public final class FigureApi {
	/**
	 * Outcome of a fog-edge spawn.
	 *
	 * @param result {@link FireResult#FIRED} with the figure, {@link FireResult#NO_SPOT} when no out-of-view place
	 *               exists right now, {@link FireResult#SKIPPED} when one is already out
	 */
	public record Spawned(FireResult result, @Nullable HimEntity figure) {
	}

	private static final int SWEEP_INTERVAL = 100;

	private FigureApi() {
	}

	/**
	 * Spawns him with his feet at {@code feet}, facing {@code yaw}. Refused (empty) if any part of him would be in
	 * view of a player, so he never pops in on screen.
	 *
	 * @param anchor the trunk or light he relates to, or null
	 */
	public static Optional<HimEntity> spawnAt(ServerLevel level, Variant variant, Vec3 feet, float yaw, @Nullable BlockPos anchor) {
		HimEntity him = ModEntities.HIM.create(level, EntitySpawnReason.EVENT);
		if (him == null) {
			return Optional.empty();
		}
		him.snapTo(feet.x, feet.y, feet.z, yaw, 0.0F);
		him.setup(variant, yaw, anchor);
		if (!Services.traces().isOutOfView(level, him.getBoundingBox().inflate(0.1)) || !level.addFreshEntity(him)) {
			return Optional.empty();
		}
		A1016_02.LOGGER.debug("[a1016] figure ({}) at {}", variant.shortName(), BlockPos.containing(feet).toShortString());
		return Optional.of(him);
	}

	public static Optional<HimEntity> spawnAt(ServerLevel level, Variant variant, Vec3 feet, float yaw) {
		return spawnAt(level, variant, feet, yaw, null);
	}

	/**
	 * Finds a place for the variant at this player's fog edge and spawns him there. With {@code forced} (debug),
	 * the gates and the 300-block rule are skipped and a figure already out walks away first; the fog edge and
	 * the out-of-view rule always apply. Records the sighting.
	 */
	public static Spawned spawnAtFogEdge(ServerPlayer player, Variant variant, RandomSource random, boolean forced) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		List<HimEntity> out = active(server);
		if (!out.isEmpty()) {
			if (!forced) {
				return new Spawned(FireResult.SKIPPED, null);
			}
			out.forEach(him -> him.walkAway(Variant.Gait.WALK));
		}
		Optional<SpotFinder.Spot> spot = findSpot(player, variant, random, forced);
		if (spot.isEmpty()) {
			return new Spawned(FireResult.NO_SPOT, null);
		}
		SpotFinder.Spot s = spot.get();
		Optional<HimEntity> him = spawnAt(level, variant, s.pos(), facing(variant, s, player.position()), s.anchor());
		if (him.isEmpty()) {
			return new Spawned(FireResult.NO_SPOT, null);
		}
		EntityData.get(server).recordSighting(variant.shortName(), GlobalPos.of(level.dimension(), BlockPos.containing(s.pos())), GameClock.day(server));
		A1016_02.LOGGER.debug("[a1016] sighting {} at {}, {} blocks out", variant.cardId(), BlockPos.containing(s.pos()).toShortString(), Math.round(s.distance()));
		return new Spawned(FireResult.FIRED, him.get());
	}

	/** Every figure that is out, in every level. */
	public static List<HimEntity> active(MinecraftServer server) {
		List<HimEntity> found = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			found.addAll(level.getEntities(ModEntities.HIM, HimEntity::isAlive));
		}
		return found;
	}

	/** Makes every figure that is out turn and walk away. Returns how many. */
	public static int walkAway(MinecraftServer server) {
		List<HimEntity> out = active(server);
		out.forEach(him -> walkAway(him));
		return out.size();
	}

	/** Makes him turn and walk away into the fog (at his variant's pace if that is slower). */
	public static void walkAway(HimEntity him) {
		him.walkAway(him.variant().gait() == Variant.Gait.SLOW ? Variant.Gait.SLOW : Variant.Gait.WALK);
	}

	/** Debug only: removes every figure at once, in view or not. Returns how many. */
	public static int clear(MinecraftServer server) {
		List<HimEntity> out = active(server);
		out.forEach(HimEntity::discard);
		return out.size();
	}

	/** Where he would stand right now, without spawning him. */
	static Optional<SpotFinder.Spot> findSpot(ServerPlayer player, Variant variant, RandomSource random, boolean forced) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		EntityConfig config = EntityConfig.get();
		Pacing pacing = ModConfig.pacing();
		FogEdge edge = FogEdge.of(player, HerobrineState.get(server).stage() == Stage.ALONE);
		EntityData data = EntityData.get(server);
		boolean spacing = !forced && variant != Variant.LAST_ONE;
		Predicate<BlockPos> allowed = pos -> level.isPositionEntityTicking(pos)
				&& !SightingGates.nearBase(player, pos, config.baseRadius)
				&& (!spacing || data.farEnough(GlobalPos.of(level.dimension(), pos), pacing.sightingMinSpacing));
		Predicate<AABB> hidden = box -> Services.traces().isOutOfView(level, box);
		SpotFinder.Query q = new SpotFinder.Query(level, player.position(), player.getEyePosition(), edge.inner(), edge.outer(),
				pacing.sightingMinDistance, ModEntities.HIM.getDimensions(), hidden, allowed, random, config.spotSamples);
		return switch (variant.spot()) {
			case OPEN -> SpotFinder.open(q);
			case RIDGE -> SpotFinder.ridge(q, config.ridgeMinRise);
			case TRUNK -> SpotFinder.trunk(q, base -> Services.sites().find(SiteType.BARE_GROVE, GlobalPos.of(level.dimension(), base), 24).isEmpty() ? 0.0 : 2.0);
			case LIGHT -> SpotFinder.light(q, SightingGates.lights(player, edge.outer()), config.lightSearchRadius, config.lightEdgeMin, config.lightEdgeMax);
			case SHORE -> SpotFinder.shore(q, config.waterFractionMin);
			case KNOWN -> SpotFinder.scored(q, feet -> knownPlace(player, level, BlockPos.containing(feet), config));
		};
	}

	/**
	 * How well the player knows this place: 3 near blocks they placed or dug, 2 in a chunk they visited on an earlier
	 * day, 1 in a chunk visited today, 0 never there.
	 */
	static double knownPlace(ServerPlayer player, ServerLevel level, BlockPos pos, EntityConfig config) {
		PlayerWatch watch = Services.watch();
		int r = config.closeKnownRadius;
		if (!watch.placedNear(level, pos, r, state -> !state.isAir()).isEmpty() || !watch.dugNear(level, pos, r).isEmpty()) {
			return 3.0;
		}
		long visited = watch.lastVisitDay(level, ChunkPos.containing(pos));
		if (visited < 0) {
			return 0.0;
		}
		return visited < GameClock.day(level.getServer()) ? 2.0 : 1.0;
	}

	/** Which way he faces when he appears. */
	static float facing(Variant variant, SpotFinder.Spot spot, Vec3 player) {
		return switch (variant.facing()) {
			case PLAYER -> HimEntity.yawToward(spot.pos(), player);
			case AWAY -> HimEntity.yawToward(player, spot.pos());
			case ANCHOR -> spot.anchor() != null ? HimEntity.yawToward(spot.pos(), Vec3.atCenterOf(spot.anchor())) : HimEntity.yawToward(spot.pos(), player);
		};
	}

	/**
	 * A figure in a chunk that stopped ticking cannot run his own rules. Every few seconds, those past their unseen
	 * lifetime and out of view are removed. Called every server tick by {@link EntityInit}.
	 */
	static void sweep(MinecraftServer server) {
		if (server.getTickCount() % SWEEP_INTERVAL != 0) {
			return;
		}
		long lifetime = ModConfig.realTicks(EntityConfig.get().unseenLifetimeSeconds);
		for (ServerLevel level : server.getAllLevels()) {
			for (HimEntity him : level.getEntities(ModEntities.HIM, HimEntity::isAlive)) {
				boolean stale = him.bornTick() < 0 || server.getTickCount() - him.bornTick() > lifetime;
				if (stale && !level.isPositionEntityTicking(him.blockPosition()) && Services.traces().isOutOfView(level, him.getBoundingBox())) {
					him.discard();
				}
			}
		}
	}
}
