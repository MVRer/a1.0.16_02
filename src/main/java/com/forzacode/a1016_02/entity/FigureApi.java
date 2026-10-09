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
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;

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
	 * @param inner nearest horizontal distance, never under {@code EntityConfig.minDistance}
	 * @param outer farthest horizontal distance, inside the fog edge and the entity-ticking range
	 */
	public record Band(double inner, double outer) {
	}

	/**
	 * The last fog-edge spawn, for {@code /a1016 entity info}.
	 *
	 * @param distance horizontal distance from the player he appeared for
	 * @param edge     the band and fog end used
	 */
	public record LastSpawn(Variant variant, double distance, FogEdge edge) {
	}

	private static volatile @Nullable LastSpawn lastSpawn;

	/** Among endermen or piglins he never stands closer than this (horizontally) to one of them. */
	static final double AMONG_MIN_GAP = 1.5;
	private static final int SWEEP_INTERVAL = 5;
	private static final int PENDING_DIG_INTERVAL = 20;
	private static final int FULL_SWEEP_INTERVAL = 100;
	/** Every figure seen alive (spawned here or by /summon), for the cheap gate and the sweep. Pruned on read. */
	private static final Set<HimEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

	private FigureApi() {
	}

	/**
	 * Spawns him with his feet at {@code feet}, facing {@code yaw}. Refused (empty) if any part of his rendered model
	 * would be in view of a player or closer than {@code EntityConfig.minDistance} (12, never under 8) to any player's
	 * eyes, or if the spot is not entity-ticking. He never pops in on screen and never appears close. His spawn
	 * distance (which scales his flee and approach distances) is the horizontal distance to the nearest player.
	 *
	 * @param anchor the trunk or light he relates to, or null
	 */
	public static Optional<HimEntity> spawnAt(ServerLevel level, Variant variant, Vec3 feet, float yaw, @Nullable BlockPos anchor) {
		AABB view = HimEntity.viewBox(feet);
		List<Vec3> eyes = level.players().stream().map(p -> p.getEyePosition()).toList();
		if (tooClose(eyes, view, EntityConfig.get().minDistance()) || !level.isPositionEntityTicking(BlockPos.containing(feet))
				|| !Services.traces().isOutOfView(level, view)) {
			return Optional.empty();
		}
		HimEntity him = ModEntities.HIM.create(level, EntitySpawnReason.EVENT);
		if (him == null) {
			return Optional.empty();
		}
		him.snapTo(feet.x, feet.y, feet.z, yaw, 0.0F);
		him.setup(variant, yaw, anchor);
		him.setSpawnDistance(level.players().stream().mapToDouble(p -> SpotFinder.horizontal(p.position(), feet)).min().orElse(Double.NaN));
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
		if (level.dimension() != variant.dimension()) {
			return new Spawned(FireResult.SKIPPED, null); // each variant only in its own dimension (D-034), even forced
		}
		List<HimEntity> out = active(server);
		if (!out.isEmpty()) {
			if (!forced) {
				return new Spawned(FireResult.SKIPPED, null);
			}
			out.forEach(him -> him.walkAway(Variant.Gait.WALK));
		}
		FogEdge edge = FogEdge.of(player, variant == Variant.CLOSE);
		Optional<SpotFinder.Spot> spot = findSpot(player, variant, edge, random, forced);
		if (spot.isEmpty()) {
			return new Spawned(FireResult.NO_SPOT, null);
		}
		SpotFinder.Spot s = spot.get();
		Optional<HimEntity> him = spawnAt(level, variant, s.pos(), facing(variant, s, player.position()), s.anchor());
		if (him.isEmpty()) {
			return new Spawned(FireResult.NO_SPOT, null);
		}
		him.get().setSpawnDistance(s.distance());
		lastSpawn = new LastSpawn(variant, s.distance(), edge);
		EntityData.get(server).recordSighting(variant.shortName(), GlobalPos.of(level.dimension(), BlockPos.containing(s.pos())), GameClock.day(server));
		A1016_02.LOGGER.debug("[a1016] sighting {} at {}, {} blocks out (fog end {}, {})", variant.cardId(), BlockPos.containing(s.pos()).toShortString(),
				Math.round(s.distance()), Math.round(edge.limit()), edge.fromClient() ? "client" : "estimate");
		return new Spawned(FireResult.FIRED, him.get());
	}

	/** The last fog-edge spawn since the game started, or null. */
	public static @Nullable LastSpawn lastSpawn() {
		return lastSpawn;
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

	/**
	 * The "no run" option (Ending D's last minute: "He doesn't run"): with {@code noRun} he never breaks into a run,
	 * never rushes past a chaser (D-037) and never goes under (D-030); whatever ends his sighting, he walks away. His
	 * other rules stay (he leaves once seen, never despawns in view). Set it right after spawning him.
	 */
	public static void setNoRun(HimEntity him, boolean noRun) {
		him.setNoRun(noRun);
	}

	/** {@link #walkAway(HimEntity)}, and with {@code noRun} he keeps walking whatever happens ({@link #setNoRun}). */
	public static void walkAway(HimEntity him, boolean noRun) {
		if (noRun) {
			him.setNoRun(true);
		}
		walkAway(him);
	}

	/** Debug only: removes every figure at once, in view or not. Returns how many. */
	public static int clear(MinecraftServer server) {
		List<HimEntity> out = active(server);
		out.forEach(HimEntity::discard);
		return out.size();
	}

	/**
	 * Where he would stand right now in this band, without spawning him. Nowhere if the fog is too thick to see him
	 * past the minimum distance ({@link FogEdge#seeable}).
	 */
	static Optional<SpotFinder.Spot> findSpot(ServerPlayer player, Variant variant, FogEdge edge, RandomSource random, boolean forced) {
		if (!edge.seeable()) {
			return Optional.empty();
		}
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		EntityConfig config = EntityConfig.get();
		Pacing pacing = ModConfig.pacing();
		Band band = new Band(edge.inner(), edge.outer());
		EntityData data = EntityData.get(server);
		boolean spacing = !forced && variant != Variant.LAST_ONE;
		Predicate<BlockPos> allowed = pos -> HimEntity.tickingAround(level, Vec3.atBottomCenterOf(pos), HimEntity.SPAWN_TICK_MARGIN)
				&& !SightingGates.nearBase(player, pos, config.baseRadius)
				&& (!spacing || data.farEnough(GlobalPos.of(level.dimension(), pos), pacing.sightingMinSpacing));
		Predicate<AABB> hidden = box -> Services.traces().isOutOfView(level, box);
		SpotFinder.Query q = new SpotFinder.Query(level, player.position(), player.getEyePosition(), band.inner(), band.outer(),
				config.minDistance(), ModEntities.HIM.getDimensions(), hidden, allowed, random, config.spotSamples);
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
			case ENDERMEN, PIGLINS -> SpotFinder.among(q, SightingGates.mobsAtEdge(player, variant, band, config.amongMobsRadius), config.amongMobsRadius,
					AMONG_MIN_GAP);
		};
	}

	/**
	 * Where the variant may stand around this player (D-035, see {@link FogEdge}): {@code normalFractionMin..Max}
	 * (0.55 to 0.75) of the fog end the client draws, so he reads as a hazy but clear shape. The close variant stands
	 * {@code closeFractionMin..Max} (0.35 to 0.50) of it, clamped to 16 to 28 blocks. Never under
	 * {@code EntityConfig.minDistance}.
	 */
	public static Band band(ServerPlayer player, Variant variant) {
		FogEdge edge = FogEdge.of(player, variant == Variant.CLOSE);
		return new Band(edge.inner(), edge.outer());
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
	 * A figure whose chunk stopped ticking cannot run his own rules: he stands frozen, which reads as staring. Once
	 * he is out of view (or past everyone's full render distance) he is removed. Called every server tick by
	 * {@link EntityInit}.
	 */
	static void sweep(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % PENDING_DIG_INTERVAL == 0) {
			GoUnder.refillPending(server); // shafts left open by a figure that is gone, once out of view (D-030)
		}
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
			} else if (him.level() instanceof ServerLevel level
					&& sweepRemoves(him, level.isPositionEntityTicking(him.blockPosition()), Watchers.of(level))) {
				him.discard();
				LIVE.remove(him);
			}
		}
	}

	/** The sweep's rule: only a figure that is not ticking, and only out of view or past every full render distance. */
	public static boolean sweepRemoves(HimEntity him, boolean ticking, Watchers watchers) {
		return !ticking && watchers.mayRemove(him.level(), him.viewBox(), him.position());
	}
}
