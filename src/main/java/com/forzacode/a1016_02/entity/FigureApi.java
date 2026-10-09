package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
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

	/**
	 * The spawn band for a variant around a player.
	 *
	 * @param inner nearest horizontal distance, never under {@code Pacing.sightingMinDistance}
	 * @param outer farthest horizontal distance, inside the fog edge and the entity-ticking range
	 */
	public record Band(double inner, double outer) {
	}

	private static final int SWEEP_INTERVAL = 5;
	private static final int FULL_SWEEP_INTERVAL = 100;
	/** Every figure seen alive (spawned here or by /summon), for the cheap gate and the sweep. Pruned on read. */
	private static final Set<HimEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

	private FigureApi() {
	}

	/**
	 * Spawns him with his feet at {@code feet}, facing {@code yaw}. Refused (empty) if any part of his rendered model
	 * would be in view of a player or closer than {@code Pacing.sightingMinDistance} (24) to any player's eyes, or if
	 * the spot is not entity-ticking. He never pops in on screen and never appears close.
	 *
	 * @param anchor the trunk or light he relates to, or null
	 */
	public static Optional<HimEntity> spawnAt(ServerLevel level, Variant variant, Vec3 feet, float yaw, @Nullable BlockPos anchor) {
		AABB view = HimEntity.viewBox(feet);
		List<Vec3> eyes = level.players().stream().map(p -> p.getEyePosition()).toList();
		if (tooClose(eyes, view, ModConfig.pacing().sightingMinDistance) || !level.isPositionEntityTicking(BlockPos.containing(feet))
				|| !Services.traces().isOutOfView(level, view)) {
			return Optional.empty();
		}
		HimEntity him = ModEntities.HIM.create(level, EntitySpawnReason.EVENT);
		if (him == null) {
			return Optional.empty();
		}
		him.snapTo(feet.x, feet.y, feet.z, yaw, 0.0F);
		him.setup(variant, yaw, anchor);
		if (!level.addFreshEntity(him)) {
			return Optional.empty();
		}
		LIVE.add(him);
		A1016_02.LOGGER.debug("[a1016] figure ({}) at {}", variant.shortName(), BlockPos.containing(feet).toShortString());
		return Optional.of(him);
	}

	public static Optional<HimEntity> spawnAt(ServerLevel level, Variant variant, Vec3 feet, float yaw) {
		return spawnAt(level, variant, feet, yaw, null);
	}

	/** True if any of the eyes is closer than {@code min} to the box. */
	public static boolean tooClose(List<Vec3> eyes, AABB box, double min) {
		for (Vec3 eye : eyes) {
			if (box.distanceToSqr(eye) < min * min) {
				return true;
			}
		}
		return false;
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

	static void track(HimEntity him) {
		LIVE.add(him);
	}

	/** True if a figure is out. Cheap; used by the gates. */
	public static boolean anyOut(MinecraftServer server) {
		LIVE.removeIf(him -> him.isRemoved() || him.level().getServer() != server);
		return !LIVE.isEmpty();
	}

	/** Every figure that is out, in every level (including any made with /summon). */
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
		Band band = band(player, variant);
		EntityData data = EntityData.get(server);
		boolean spacing = !forced && variant != Variant.LAST_ONE;
		Predicate<BlockPos> allowed = pos -> HimEntity.tickingAround(level, Vec3.atBottomCenterOf(pos), HimEntity.SPAWN_TICK_MARGIN)
				&& !SightingGates.nearBase(player, pos, config.baseRadius)
				&& (!spacing || data.farEnough(GlobalPos.of(level.dimension(), pos), pacing.sightingMinSpacing));
		Predicate<AABB> hidden = box -> Services.traces().isOutOfView(level, box);
		SpotFinder.Query q = new SpotFinder.Query(level, player.position(), player.getEyePosition(), band.inner(), band.outer(),
				pacing.sightingMinDistance, ModEntities.HIM.getDimensions(), hidden, allowed, random, config.spotSamples);
		return switch (variant.spot()) {
			case OPEN -> SpotFinder.open(q);
			case RIDGE -> SpotFinder.ridge(q, config.ridgeMinRise);
			case TRUNK -> SpotFinder.trunk(q, base -> Services.sites().find(SiteType.BARE_GROVE, GlobalPos.of(level.dimension(), base), 24).isEmpty() ? 0.0 : 2.0);
			case LIGHT -> SpotFinder.light(q, SightingGates.lights(player, band, config.lightSearchRadius), config.lightSearchRadius,
					config.lightEdgeMin, config.lightEdgeMax);
			case SHORE -> SpotFinder.shore(q, config.waterFractionMin);
			case KNOWN -> {
				Optional<SpotFinder.Spot> known = SpotFinder.scored(q, feet -> knownPlace(player, level, BlockPos.containing(feet), config));
				// Debug in a fresh world: nowhere at the fog edge is known yet, so any spot there will do.
				yield known.isEmpty() && forced ? SpotFinder.open(q) : known;
			}
		};
	}

	/**
	 * Where the variant may stand around this player: just inside the fog edge. The cow (and everything in Alone)
	 * keeps to the narrow band at the edge; the others, which need the right terrain or room to walk off, may stand
	 * a little deeper in the fog band ({@code terrainBandFraction}).
	 */
	public static Band band(ServerPlayer player, Variant variant) {
		boolean alone = HerobrineState.get(player.level().getServer()).stage() == Stage.ALONE;
		FogEdge edge = FogEdge.of(player, alone);
		if (alone || variant == Variant.COW) {
			return new Band(edge.inner(), edge.outer());
		}
		double min = ModConfig.pacing().sightingMinDistance;
		double deeper = Math.max(min, edge.outer() * (1.0 - EntityConfig.get().terrainBandFraction));
		return new Band(Math.min(edge.inner(), deeper), edge.outer());
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
	 * A figure whose chunk stopped ticking cannot run his own rules and would stand frozen, so he is removed at once
	 * (his own tick already removes him before he walks out of the ticking range; this catches the player moving or
	 * teleporting away). Called every server tick by {@link EntityInit}.
	 */
	static void sweep(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % FULL_SWEEP_INTERVAL == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				LIVE.addAll(level.getEntities(ModEntities.HIM, HimEntity::isAlive));
			}
		}
		if (tick % SWEEP_INTERVAL != 0 || LIVE.isEmpty()) {
			return;
		}
		for (HimEntity him : List.copyOf(LIVE)) {
			if (him.isRemoved()) {
				LIVE.remove(him);
			} else if (him.level() instanceof ServerLevel level && !level.isPositionEntityTicking(him.blockPosition())) {
				him.discard();
				LIVE.remove(him);
			}
		}
	}
}
