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
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

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
 * him walk away, see whether he is out. He is one being (D-060.4): at most one figure is out at a time, across every
 * player and dimension, and he only ever spawns out of view of every player in his level, spectators included. The
 * glimpse (D-073) comes through core's {@code Figure} contract ({@link GlimpseFigure}), never through the fog edge.
 * Server thread only.
 */
public final class FigureApi {
	/**
	 * Outcome of a fog-edge spawn.
	 *
	 * @param result {@link FireResult#FIRED} with the figure; {@link FireResult#NO_SPOT} when no out-of-view place
	 *               exists right now, or he is already out for someone (one him: the director keeps the card for
	 *               later); {@link FireResult#SKIPPED} when the variant does not run in the player's dimension
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
	 * @param edge     the band and fog end used (that player's own fog)
	 * @param forName  the name of the player he appeared for
	 */
	public record LastSpawn(Variant variant, double distance, FogEdge edge, String forName) {
	}

	/** Someone stared him down (STARED_AT_HIM): they looked straight at him for {@code stareSeconds}. */
	@FunctionalInterface
	public interface Stared {
		void onStared(ServerPlayer player, HimEntity him);
	}

	/**
	 * Fired on the server thread when STARED_AT_HIM fires, right after its attention: once for each player who stares
	 * him down during a sighting (anyone's stare counts, D-060.4; never twice for the same player in one sighting,
	 * never once per tick). The endings read it as "staring into the fog". Also sets the flag {@link #STARED_FLAG}.
	 */
	public static final Event<Stared> STARED = EventFactory.createArrayBacked(Stared.class, listeners -> (player, him) -> {
		for (Stared listener : listeners) {
			listener.onStared(player, him);
		}
	});
	/** {@code HerobrineState} flag: set the first time anyone stared him down in this world (never cleared). */
	public static final String STARED_FLAG = "entity:stared";

	private static volatile @Nullable LastSpawn lastSpawn;

	/** Among endermen or piglins he never stands closer than this (horizontally) to one of them. */
	static final double AMONG_MIN_GAP = 1.5;
	private static final int SWEEP_INTERVAL = 5;
	private static final int PENDING_DIG_INTERVAL = 20;
	private static final int FULL_SWEEP_INTERVAL = 100;
	/**
	 * Every figure seen alive (spawned here, or by /summon once he ticks or the full sweep finds him), for the one-him
	 * rule and the sweep. Pruned on read. A spawn adds him before it returns, so a second sighting asked for in the
	 * same tick (another player's card) already finds him out.
	 */
	private static final Set<HimEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

	private FigureApi() {
	}

	/**
	 * Spawns him with his feet at {@code feet}, facing {@code yaw}. Refused (empty) while he is already out anywhere
	 * (one him), if any part of his rendered model would be in view of a player in the level (spectators included) or
	 * closer than {@code EntityConfig.minDistance} (12, never under 8) to any player's eyes, or if the spot is not
	 * entity-ticking. He never pops in on screen and never appears close. His spawn distance (which scales his flee
	 * and approach distances) is the horizontal distance to the nearest player.
	 *
	 * <p>During Ending D's last minute ({@code ending:last_minute}) a figure still out from an earlier sighting is told
	 * to walk away, so a later try finds him gone.
	 *
	 * @param anchor the trunk or light he relates to, or null
	 */
	public static Optional<HimEntity> spawnAt(ServerLevel level, Variant variant, Vec3 feet, float yaw, @Nullable BlockPos anchor) {
		MinecraftServer server = level.getServer();
		if (anyOut(server)) {
			if (HerobrineState.get(server).hasFlag(TraceService.LAST_MINUTE_FLAG)) {
				walkAway(server);
			}
			return Optional.empty();
		}
		return spawn(level, variant, feet, yaw, anchor, Watchers.of(level));
	}

	public static Optional<HimEntity> spawnAt(ServerLevel level, Variant variant, Vec3 feet, float yaw) {
		return spawnAt(level, variant, feet, yaw, null);
	}

	/**
	 * The spawn itself, without the one-him check (callers make it; game tests, which spawn many figures at once in
	 * their own areas, call this directly): refused if any of {@code watchers} could see any part of him or is closer
	 * than the minimum distance ({@link Watchers#hides}), or the spot is not entity-ticking.
	 */
	static Optional<HimEntity> spawn(ServerLevel level, Variant variant, Vec3 feet, float yaw, @Nullable BlockPos anchor, Watchers watchers) {
		double minDistance = EntityConfig.get().minDistance();
		return spawn(level, variant, feet, yaw, anchor, watchers, view -> watchers.hides(level, view, minDistance));
	}

	/**
	 * {@link #spawn(ServerLevel, Variant, Vec3, float, BlockPos, Watchers)} with {@code hidden} as the spawn rule for his
	 * view box: the glimpse's (D-073) is the same out-of-view rule with its own distance ({@link GlimpseFigure}), since the
	 * surface sighting's minimum distance does not apply underground. Every caller's rule includes the out-of-view one.
	 */
	static Optional<HimEntity> spawn(ServerLevel level, Variant variant, Vec3 feet, float yaw, @Nullable BlockPos anchor, Watchers watchers,
			Predicate<AABB> hidden) {
		AABB view = HimEntity.viewBox(feet);
		if (!level.isPositionEntityTicking(BlockPos.containing(feet)) || !hidden.test(view)) {
			return Optional.empty();
		}
		HimEntity him = ModEntities.HIM.create(level, EntitySpawnReason.EVENT);
		if (him == null) {
			return Optional.empty();
		}
		him.snapTo(feet.x, feet.y, feet.z, yaw, 0.0F);
		him.setup(variant, yaw, anchor);
		him.setSpawnDistance(watchers.eyes().stream().mapToDouble(eye -> SpotFinder.horizontal(eye, feet)).min().orElse(Double.NaN));
		if (!level.addFreshEntity(him)) {
			return Optional.empty();
		}
		LIVE.add(him);
		A1016_02.LOGGER.debug("[a1016] figure ({}) at {}", variant.shortName(), BlockPos.containing(feet).toShortString());
		return Optional.of(him);
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
	 * Finds a place for the variant at this player's fog edge (their own fog, D-035) and spawns him there for them.
	 * The place is out of view of every player in that level, at least the minimum distance from each of them, and past
	 * his flee distance from everyone else who takes part (so nobody makes him flee the moment he appears).
	 * {@link FireResult#NO_SPOT} while he is already out for anyone (one him; the director keeps the card). With
	 * {@code forced} (debug), the gates and the 300-block rule are skipped, and a figure already out is told to walk
	 * away so a later try can spawn; the fog edge, one him and the out-of-view rule always apply. Records the sighting.
	 */
	public static Spawned spawnAtFogEdge(ServerPlayer player, Variant variant, RandomSource random, boolean forced) {
		return spawnAtFogEdge(player, variant, random, forced, Watchers.of(player.level()));
	}

	/** {@link #spawnAtFogEdge(ServerPlayer, Variant, RandomSource, boolean)} as seen by these watchers (game tests). */
	static Spawned spawnAtFogEdge(ServerPlayer player, Variant variant, RandomSource random, boolean forced, Watchers watchers) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		if (variant.spot() == Variant.Spot.GIVEN) {
			return new Spawned(FireResult.SKIPPED, null); // the glimpse never stands at the fog edge: only its caller's spot (GlimpseFigure)
		}
		if (level.dimension() != variant.dimension()) {
			return new Spawned(FireResult.SKIPPED, null); // each variant only in its own dimension (D-034), even forced
		}
		if (anyOut(server)) {
			if (forced) {
				walkAway(server); // debug: he leaves where he is first; he is one, so this try finds no place for him
			}
			return new Spawned(FireResult.NO_SPOT, null);
		}
		FogEdge edge = FogEdge.of(player, variant);
		Optional<SpotFinder.Spot> spot = findSpot(player, variant, edge, random, forced, watchers);
		if (spot.isEmpty()) {
			return new Spawned(FireResult.NO_SPOT, null);
		}
		SpotFinder.Spot s = spot.get();
		Optional<HimEntity> him = spawn(level, variant, s.pos(), facing(variant, s, player.position()), s.anchor(), watchers);
		if (him.isEmpty()) {
			return new Spawned(FireResult.NO_SPOT, null);
		}
		him.get().setSpawnDistance(s.distance());
		him.get().appearFor(player.getUUID());
		lastSpawn = new LastSpawn(variant, s.distance(), edge, player.getName().getString());
		EntityData.get(server).recordSighting(variant.shortName(), GlobalPos.of(level.dimension(), BlockPos.containing(s.pos())), GameClock.day(server));
		A1016_02.LOGGER.debug("[a1016] sighting {} for {} at {}, {} blocks out (fog end {}, {})", variant.cardId(), player.getName().getString(),
				BlockPos.containing(s.pos()).toShortString(), Math.round(s.distance()), Math.round(edge.limit()), edge.fromClient() ? "client" : "estimate");
		return new Spawned(FireResult.FIRED, him.get());
	}

	/** The last fog-edge spawn since the server started, or null. */
	public static @Nullable LastSpawn lastSpawn() {
		return lastSpawn;
	}

	/** Server stop: forgets the last spawn, so a singleplayer world reload never shows the previous world's. */
	static void clearLastSpawn() {
		lastSpawn = null;
	}

	static void track(HimEntity him) {
		LIVE.add(him);
	}

	/** True if a figure is out anywhere on this server, in any dimension (the one-him rule). Cheap; used by the gates. */
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

	/** Makes him turn and walk away into the fog (at his variant's pace if that is slower). The glimpse is simply gone (D-073). */
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
	 * Where he would stand right now in this player's band, without spawning him: hidden from every one of
	 * {@code watchers} and at least the minimum distance from each, past his flee distance (sideways) from every one of
	 * them who takes part, never near any participant's base. Nowhere if the fog is too thick to see him past the
	 * minimum distance ({@link FogEdge#seeable}).
	 */
	static Optional<SpotFinder.Spot> findSpot(ServerPlayer player, Variant variant, FogEdge edge, RandomSource random, boolean forced, Watchers watchers) {
		ServerLevel level = player.level();
		return findSpot(player, variant, edge, random, forced, watchers,
				pos -> HimEntity.tickingAround(level, Vec3.atBottomCenterOf(pos), HimEntity.SPAWN_TICK_MARGIN));
	}

	/**
	 * {@link #findSpot(ServerPlayer, Variant, FogEdge, RandomSource, boolean, Watchers)} with {@code ticking} as the
	 * rule that a feet block is far enough inside the entity-ticking range (game tests: only their own chunks tick).
	 */
	static Optional<SpotFinder.Spot> findSpot(ServerPlayer player, Variant variant, FogEdge edge, RandomSource random, boolean forced, Watchers watchers,
			Predicate<BlockPos> ticking) {
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
		// Never at anyone's base: the player's own, or any other participant's in this dimension.
		List<BlockPos> bases = SightingGates.basesIn(level, player);
		Predicate<BlockPos> allowed = pos -> ticking.test(pos)
				&& !SightingGates.near(bases, pos, config.baseRadius)
				&& (!spacing || data.farEnough(GlobalPos.of(level.dimension(), pos), pacing.sightingMinSpacing));
		// Out of view of every player in the level and never close to any of them, not only the one he is for. Nobody who
		// takes part within his flee distance there either, sideways (MP-7): a friend 15 blocks under the spot, out of
		// view and past the minimum distance, would make him flee, so end this sighting, the moment he appears. The flee
		// distance is the one he will have there (D-035, from his distance to this player); this player is always past it.
		double minDistance = config.minDistance();
		Predicate<AABB> hidden = box -> {
			Vec3 center = box.getCenter();
			double flee = SightingRules.fleeDistance(config.fleeDistance, config.fleeSpawnFraction, SpotFinder.horizontal(player.position(), center));
			return watchers.hides(level, box, minDistance) && watchers.nobodyWithin(center, flee);
		};
		SpotFinder.Query q = new SpotFinder.Query(level, player.position(), player.getEyePosition(), band.inner(), band.outer(),
				minDistance, ModEntities.HIM.getDimensions(), hidden, allowed, random, config.spotSamples);
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
			case GIVEN -> Optional.empty(); // the glimpse: its caller gives the spot, never the spot finder
		};
	}

	/**
	 * Where the variant may stand around this player (D-035, see {@link FogEdge}): {@code normalFractionMin..Max}
	 * (0.55 to 0.75) of the fog end the client draws, so he reads as a hazy but clear shape. The close variant stands
	 * {@code closeFractionMin..Max} (0.35 to 0.50) of it, clamped to 16 to 28 blocks. Between trunks stands
	 * {@code trunkMinDistance..trunkMaxDistance} (18 to 40) blocks away, inside the fog end's margin (the trunk fix).
	 * Never under {@code EntityConfig.minDistance}.
	 */
	public static Band band(ServerPlayer player, Variant variant) {
		FogEdge edge = FogEdge.of(player, variant);
		return new Band(edge.inner(), edge.outer());
	}

	/**
	 * How well the players know this place (the footprint is everyone's, D-058): 3 near blocks a player placed or dug,
	 * 2 in a chunk a participant visited on an earlier day, 1 in a chunk visited today, 0 never there.
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
	 * he is out of view of everyone in his level (or past everyone's full render distance) he is removed. Called every
	 * server tick by {@link EntityInit}.
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
				him.sweptAway(level); // gone by this rule, announced as any figure gone (FIGURE_LEFT)
				LIVE.remove(him);
			}
		}
	}

	/** The sweep's rule: only a figure that is not ticking, and only out of view or past every full render distance. */
	public static boolean sweepRemoves(HimEntity him, boolean ticking, Watchers watchers) {
		return !ticking && watchers.mayRemove(him.level(), him.viewBox(), him.position());
	}
}
