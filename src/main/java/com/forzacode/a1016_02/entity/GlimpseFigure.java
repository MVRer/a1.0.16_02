package com.forzacode.a1016_02.entity;

import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Entity's {@link Figure} (D-073), installed in {@link EntityInit}: the glimpse at the top of dig's tunnel ladder
 * (D-072). The caller gives the spot; he stands there only by the figure's own rules:
 * <ul>
 * <li>he is not out anywhere (one him, D-060.4);</li>
 * <li>the player may have one: alive, not spectating or asleep, in the overworld, and not during Ending D's last
 * minute (the ending owns him then); otherwise {@link FireResult#SKIPPED};</li>
 * <li>the spot is loaded and entity-ticking, with room around it ({@link HimEntity#EDGE_TICK_MARGIN}), so he never
 * freezes or goes on his first tick;</li>
 * <li>two cells of air on a solid floor, room for his body;</li>
 * <li>block light at his feet and at his eyes at most {@code glimpseMaxLight} (3): past the last torch;</li>
 * <li>at least {@code glimpseMinDistance} (14) from every player in the level (from their eyes to his feet, in 3D;
 * the surface sighting's minimum distance does not apply underground);</li>
 * <li>out of every player's view by the same rule as every spawn ({@link Watchers#sees}), spectators included.</li>
 * </ul>
 * {@link #glimpse} spawns {@link Variant#GLIMPSE} there facing {@code yaw} (the caller's; NaN faces the player), for
 * that player ({@link HimEntity#appearedFor}), and records the day in {@link EntityData#recordGlimpse}, never the
 * surface sightings' place, day or variant. What he does then is {@link HimEntity}'s: still, silent, eyes FLAT, gone
 * before anyone looks straight at him. Server thread.
 */
public final class GlimpseFigure implements Figure {
	public static final GlimpseFigure INSTANCE = new GlimpseFigure();

	/**
	 * Whether a glimpse can stand there: {@link FireResult#FIRED} if it can, else why not ({@link FireResult#SKIPPED}:
	 * the player cannot have one; {@link FireResult#NO_SPOT}: he is out, or the spot does not work now).
	 */
	public record Check(FireResult result, String why) {
		static final Check OK = new Check(FireResult.FIRED, "ok");

		public boolean ok() {
			return result == FireResult.FIRED;
		}

		static Check skipped(String why) {
			return new Check(FireResult.SKIPPED, why);
		}

		static Check noSpot(String why) {
			return new Check(FireResult.NO_SPOT, why);
		}
	}

	private GlimpseFigure() {
	}

	@Override
	public boolean canGlimpse(ServerPlayer player, Vec3 feet) {
		ServerLevel level = player.level();
		return check(player, feet, Watchers.of(level), FigureApi.anyOut(level.getServer()), ticking(level)).ok();
	}

	@Override
	public FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw) {
		ServerLevel level = player.level();
		return glimpse(player, feet, yaw, Watchers.of(level), FigureApi.anyOut(level.getServer()), ticking(level)).result();
	}

	/** The live rule for a spot's ticking: entity-ticking there, and {@link HimEntity#EDGE_TICK_MARGIN} blocks around it. */
	static Predicate<Vec3> ticking(ServerLevel level) {
		return feet -> level.isPositionEntityTicking(BlockPos.containing(feet)) && HimEntity.tickingAround(level, feet, HimEntity.EDGE_TICK_MARGIN);
	}

	/**
	 * {@link #canGlimpse} with what the world would say given here (game tests: mock players are in no level list, and
	 * other tests' figures are out at the same time).
	 *
	 * @param watchers everyone who could see him (every player in the level, spectators included)
	 * @param out      whether he is out anywhere
	 * @param ticking  whether the spot is far enough inside the entity-ticking range
	 */
	static Check check(ServerPlayer player, Vec3 feet, Watchers watchers, boolean out, Predicate<Vec3> ticking) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		if (!player.isAlive() || player.isSpectator() || player.isSleeping()) {
			return Check.skipped("player busy");
		}
		if (level.dimension() != Variant.GLIMPSE.dimension()) {
			return Check.skipped("not in the overworld");
		}
		if (HerobrineState.get(server).hasFlag(TraceService.LAST_MINUTE_FLAG)) {
			return Check.skipped("Ending D's last minute");
		}
		if (!Double.isFinite(feet.x) || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)) {
			return Check.noSpot("not a place");
		}
		if (out) {
			return Check.noSpot("he is already out");
		}
		if (!ticking.test(feet)) {
			return Check.noSpot("not loaded and ticking");
		}
		EntityConfig config = EntityConfig.get();
		if (!room(level, feet)) {
			return Check.noSpot("no room (two cells of air on a solid floor)");
		}
		if (!dark(level, feet, config.glimpseMaxLight)) {
			return Check.noSpot("too light");
		}
		if (!farFromEveryone(watchers, feet, config.glimpseMinDistance())) {
			return Check.noSpot("too close to someone");
		}
		if (watchers.sees(level, HimEntity.viewBox(feet))) {
			return Check.noSpot("in someone's view");
		}
		return Check.OK;
	}

	/**
	 * {@link #glimpse(ServerPlayer, Vec3, float)} with what the world would say given here, as {@link #check}. Spawns
	 * him if the check holds; the spawn itself asks the same view and distance rule again.
	 */
	static FigureApi.Spawned glimpse(ServerPlayer player, Vec3 feet, float yaw, Watchers watchers, boolean out, Predicate<Vec3> ticking) {
		Check check = check(player, feet, watchers, out, ticking);
		if (!check.ok()) {
			A1016_02.LOGGER.debug("[a1016] no glimpse for {} at {}: {}", player.getName().getString(), feet, check.why());
			return new FigureApi.Spawned(check.result(), null);
		}
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		double min = EntityConfig.get().glimpseMinDistance();
		float facing = Float.isNaN(yaw) ? HimEntity.yawToward(feet, player.position()) : yaw;
		Optional<HimEntity> spawned = FigureApi.spawn(level, Variant.GLIMPSE, feet, facing, null, watchers,
				box -> !watchers.sees(level, box) && farFromEveryone(watchers, feet, min));
		if (spawned.isEmpty()) {
			return new FigureApi.Spawned(FireResult.NO_SPOT, null);
		}
		HimEntity him = spawned.get();
		him.appearFor(player.getUUID());
		him.setSpawnDistance(SpotFinder.horizontal(player.position(), feet));
		EntityData.get(server).recordGlimpse(GameClock.day(server));
		A1016_02.LOGGER.debug("[a1016] glimpse for {} at {}, {} blocks back", player.getName().getString(), BlockPos.containing(feet).toShortString(),
				Math.round(player.position().distanceTo(feet)));
		return new FigureApi.Spawned(FireResult.FIRED, him);
	}

	/**
	 * Two cells of air (his feet and his head) over a floor solid on top, and nothing in the way of his body there (a
	 * spot at a cell's corner would put him into the wall).
	 */
	static boolean room(ServerLevel level, Vec3 feet) {
		BlockPos cell = BlockPos.containing(feet.x, feet.y + 1.0E-4, feet.z);
		if (!level.getBlockState(cell).isAir() || !level.getBlockState(cell.above()).isAir()) {
			return false;
		}
		BlockPos floor = cell.below();
		if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
			return false;
		}
		AABB body = ModEntities.HIM.getDimensions().makeBoundingBox(feet).deflate(1.0E-3);
		return level.noCollision(body);
	}

	/** Block light at his feet and at his eyes at most {@code max} ({@link GlimpseRules#darkEnough}). */
	static boolean dark(ServerLevel level, Vec3 feet, int max) {
		EntityDimensions size = ModEntities.HIM.getDimensions();
		int atFeet = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(feet.x, feet.y + 1.0E-4, feet.z));
		int atEyes = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(feet.x, feet.y + size.eyeHeight(), feet.z));
		return GlimpseRules.darkEnough(atFeet, atEyes, max);
	}

	/** True if every watcher's eyes are at least {@code min} from his feet, in 3D (D-073: 14 blocks from every player). */
	static boolean farFromEveryone(Watchers watchers, Vec3 feet, double min) {
		for (Vec3 eye : watchers.eyes()) {
			if (eye.distanceToSqr(feet) < min * min) {
				return false;
			}
		}
		return true;
	}
}
