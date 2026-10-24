package com.forzacode.a1016_02.entity.sc2.s31;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.EntityData;
import com.forzacode.a1016_02.entity.FigureApi;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.SightingGates;
import com.forzacode.a1016_02.entity.SightingRules;
import com.forzacode.a1016_02.entity.Variant;
import com.forzacode.a1016_02.entity.Watchers;
import com.forzacode.a1016_02.entity.sc.ScGates;
import com.forzacode.a1016_02.entity.sc2.s27.Sight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 31 (docs/SELF_CREATED_2.md, {@code sc_31}): at dusk, he stands still on the top block of one of his places that is not a
 * light, {@code apexMin} to {@code apexMax} from the player, facing them ({@link Variant#APEX}: the ridge's stare back and
 * leave, down the far side, never going under). The spot comes from {@link Remains} (the place's apex), never the fog edge.
 * Every sighting rule holds: one figure, one sighting a day, never the same variant twice running, 300 blocks from the last,
 * never in combat, indoors or at the base. He appears out of everyone's view (the spawn rule), inside the entity-ticking
 * range so his rules run, where the player can see him once they turn (a clear line from their eyes). The fake: an existing
 * zombie (a husk any time, a zombie only once the sun no longer burns it) moved onto the top out of view and held there facing
 * the player, bound to them. Server thread.
 */
public final class Apex {
	public static final String CARD_ID = "sc_31";

	/** Where he stands: his feet, his yaw, the place. */
	public record Spot(Vec3 feet, float yaw, Remains.Place place, double distance) {
	}

	private Apex() {
	}

	/**
	 * Why 31 does not fit the player now, or empty (live): the common gates, the stage, the player busy, he is out, the dusk
	 * sky, combat (theirs or anyone's sharing the moment), indoors, the base, the day's sighting, the variant, the spacing, and
	 * a spot.
	 */
	public static Optional<String> fit(ServerPlayer player) {
		Optional<String> gates = gatesWithoutSpot(player);
		if (gates.isPresent()) {
			return gates;
		}
		ServerLevel level = player.level();
		return spot(player, Services.remains().known(level), Watchers.of(level), ticking(level), Sc31Config.get()).isPresent() ? Optional.empty()
				: Optional.of("no top in reach");
	}

	/** {@link #fit} without the spot. */
	static Optional<String> gatesWithoutSpot(ServerPlayer player) {
		Optional<String> common = ScGates.refusal(player);
		if (common.isPresent()) {
			return common;
		}
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Optional<String> gates = sightingGates(player, Variant.APEX, EntityData.get(server), GameClock.day(server));
		if (gates.isPresent()) {
			return gates;
		}
		if (!SightingRules.duskSky(SightingGates.timeOfDay(server), level.isRaining(), EntityConfig.get())) {
			return Optional.of("no dusk sky");
		}
		if (SightingGates.indoors(level, player)) {
			return Optional.of("indoors");
		}
		return Optional.empty();
	}

	/**
	 * The sighting rules both of 31's cards keep (SightingGates' own, for a variant whose spot is given): the stage, the
	 * player busy, he is out, Ending D's last minute, combat (theirs or a sharer's), the base, one a day, not the same
	 * variant twice running, the spacing from the last sighting. The day and the record are given (tests use their own).
	 */
	public static Optional<String> sightingGates(ServerPlayer player, Variant variant, EntityData data, long today) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		EntityConfig config = EntityConfig.get();
		Pacing pacing = ModConfig.pacing();
		if (!HerobrineState.get(server).stage().atLeast(variant.earliestStage())) {
			return Optional.of("before " + variant.earliestStage());
		}
		if (!player.isAlive() || player.isSpectator() || player.isSleeping()) {
			return Optional.of("player busy");
		}
		if (FigureApi.anyOut(server)) {
			return Optional.of("he is already out");
		}
		if (SightingGates.inCombat(Services.watch().sharingWith(player), ModConfig.realTicks(config.combatCooldownSeconds))) {
			return Optional.of("combat");
		}
		if (SightingGates.nearBase(player, player.blockPosition(), config.baseRadius)) {
			return Optional.of("near the base");
		}
		return dayRules(data, today, variant, GlobalPos.of(level.dimension(), player.blockPosition()), pacing);
	}

	/** One sighting a day, never the same variant twice running, the spacing from the last (the world's record). */
	public static Optional<String> dayRules(EntityData data, long today, Variant variant, GlobalPos at, Pacing pacing) {
		if (!data.dayAllows(today, pacing.sightingsPerDayMax)) {
			return Optional.of("already one today");
		}
		if (!data.variantAllows(variant)) {
			return Optional.of("same variant as last time");
		}
		if (!data.farEnough(at, pacing.sightingMinSpacing)) {
			return Optional.of("too close to the last sighting");
		}
		return Optional.empty();
	}

	/** The live rule for a spot's ticking: entity-ticking there, with {@link HimEntity#EDGE_TICK_MARGIN} around it. */
	static Predicate<Vec3> ticking(ServerLevel level) {
		return feet -> level.isPositionEntityTicking(BlockPos.containing(feet)) && HimEntity.tickingAround(level, feet, HimEntity.EDGE_TICK_MARGIN);
	}

	/**
	 * The nearest top he could stand on for this player now: of the places given, those of the player's level with an apex,
	 * its block loaded and giving no light, room for him on it, {@code apexMin} to {@code apexMax} off (horizontal), ticking
	 * there ({@code ticking}), in plain sight from the player's eyes ({@link Sight#body}), and hidden from every watcher and
	 * past the minimum distance from each (the spawn rule).
	 */
	public static Optional<Spot> spot(ServerPlayer player, List<Remains.Place> places, Watchers watchers, Predicate<Vec3> ticking, Sc31Config config) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		double minDistance = EntityConfig.get().minDistance();
		List<Spot> found = new ArrayList<>();
		for (Remains.Place place : places) {
			if (place.kind() != Remains.Kind.PYRAMID || place.dimension() != level.dimension() || place.apex().isEmpty()) {
				continue;
			}
			BlockPos apex = stand(level, place.apex().get(), player.getX(), player.getZ());
			Vec3 feet = Vec3.atBottomCenterOf(apex.above());
			double d = Math.hypot(feet.x - player.getX(), feet.z - player.getZ());
			if (d < config.apexMin || d > config.apexMax || !level.isLoaded(apex) || !ticking.test(feet) || !standsOn(level, apex)) {
				continue;
			}
			AABB view = HimEntity.viewBox(feet);
			// The line of sight reads every block on its way: never through a chunk that is not loaded (R12-39).
			if (!loadedAlong(level, eye, feet) || !Sight.body(level, eye, feet) || !watchers.hides(level, view, minDistance)) {
				continue;
			}
			found.add(new Spot(feet, HimEntity.yawToward(feet, player.position()), place, d));
		}
		return found.stream().max(Comparator.comparingDouble(s -> score(s.distance(), s.feet().x - player.getX(), s.feet().z - player.getZ())));
	}

	/** R11-4: the distances where he reads as a person on the top (blocks, horizontal): best within these. */
	public static final double READS_FROM = 64.0;
	public static final double READS_TO = 85.0;

	/**
	 * Pure (R11-4): how good a top is, {@code distance} off and {@code dx}, {@code dz} from the player: one {@link #READS_FROM}
	 * to {@link #READS_TO} off first (a person, not a nub); then one with the evening's bright band behind it (the top to
	 * the player's west, where the sun went down: a silhouette); then the nearest.
	 */
	public static double score(double distance, double dx, double dz) {
		double band = distance >= READS_FROM && distance <= READS_TO ? 4.0 : 0.0;
		double flat = Math.sqrt(dx * dx + dz * dz);
		double west = flat < 1.0E-6 ? 0.0 : -dx / flat;
		return band + Math.max(0.0, west) * 2.0 - distance / 1000.0;
	}

	/** Every chunk the segment from {@code from} to {@code to} crosses is loaded (sampled every 4 blocks, both ends too). */
	public static boolean loadedAlong(ServerLevel level, Vec3 from, Vec3 to) {
		double length = from.distanceTo(to);
		int steps = Math.max(1, (int) Math.ceil(length / 4.0));
		for (int i = 0; i <= steps; i++) {
			if (!level.isLoaded(BlockPos.containing(from.lerp(to, i / (double) steps)))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * R11-4, the figure's side: the block of the top he stands on for a player at ({@code x}, {@code z}). On a flat top
	 * (the 3 x 3 level) the one at its edge toward the player, so the top's own rim never hides his legs from below (from
	 * its middle, a third of him is behind the rim at the band's angles); else, or when that block will not do
	 * ({@link #standsOn}), the top's middle. A top whose middle will not do (a lit one) stays refused as before.
	 */
	static BlockPos stand(ServerLevel level, BlockPos apex, double x, double z) {
		if (!standsOn(level, apex)) {
			return apex;
		}
		BlockPos edge = towardEdge(apex, x, z);
		return standsOn(level, edge) ? edge : apex;
	}

	/** Pure: the neighbour of {@code apex} in its level toward ({@code x}, {@code z}), one of the eight round it. */
	public static BlockPos towardEdge(BlockPos apex, double x, double z) {
		double dx = x - (apex.getX() + 0.5);
		double dz = z - (apex.getZ() + 0.5);
		if (Math.hypot(dx, dz) < 1.0E-6) {
			return apex;
		}
		double a = Math.atan2(dz, dx);
		int step = (int) Math.round(a / (Math.PI / 4.0));
		double snapped = step * (Math.PI / 4.0);
		return apex.offset((int) Math.round(Math.cos(snapped)), 0, (int) Math.round(Math.sin(snapped)));
	}

	/** A top block giving no light (never a lit one), solid on top, with two cells of air over it. */
	static boolean standsOn(ServerLevel level, BlockPos apex) {
		BlockState top = level.getBlockState(apex);
		if (top.getLightEmission() > 0 || !top.isFaceSturdy(level, apex, Direction.UP)) {
			return false;
		}
		return level.getBlockState(apex.above()).isAir() && level.getBlockState(apex.above(2)).isAir();
	}

	/**
	 * Fires 31 for this player (live): {@link FireResult#FIRED} with him on the top (or the fake's zombie there);
	 * {@link FireResult#NO_SPOT} when no top works now or he is out (a forced fire tells him to walk away first);
	 * {@link FireResult#SKIPPED} when the story holds it or the gates do (unless forced).
	 */
	public static FireResult fire(ServerPlayer player, boolean fake, boolean forced) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		if (ScGates.storyRefusal(server).isPresent() || level.dimension() != Variant.APEX.dimension()) {
			return FireResult.SKIPPED;
		}
		if (FigureApi.anyOut(server) && !fake) {
			if (forced) {
				FigureApi.walkAway(server); // debug: he leaves where he is first; this try finds him still out
			}
			return FireResult.NO_SPOT; // one him: the director keeps the card
		}
		if (!forced && gatesWithoutSpot(player).isPresent()) {
			return FireResult.SKIPPED;
		}
		Optional<Spot> spot = spot(player, Services.remains().known(level), Watchers.of(level), ticking(level), Sc31Config.get());
		if (spot.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		Spot s = spot.get();
		if (fake) {
			return fake(player, s, Services.mobs(), EntityData.get(server)) ? FireResult.FIRED : FireResult.NO_SPOT;
		}
		FireResult result = FigureApi.spawnGiven(player, Variant.APEX, s.feet(), s.yaw(), null).result();
		if (result == FireResult.FIRED) {
			A1016_02.LOGGER.info("[a1016] {}: for {} ({} out)", CARD_ID, player.getName().getString(), Math.round(s.distance()));
		}
		return result;
	}

	/**
	 * The fake: the nearest existing husk (any time) or zombie (only once the sun no longer burns it) within 96 of the top,
	 * adult, unnamed, free, not held by anything, moved onto the top out of view, then held still facing the player and bound
	 * to them. False if none could be moved.
	 */
	public static boolean fake(ServerPlayer player, Spot spot, MobTamper mobs, @Nullable EntityData record) {
		ServerLevel level = player.level();
		boolean sunBurns = level.isBrightOutside();
		List<Mob> candidates = new ArrayList<>();
		for (EntityType<? extends Mob> type : List.of(EntityTypes.HUSK, EntityTypes.ZOMBIE)) {
			if (type == EntityTypes.ZOMBIE && sunBurns) {
				continue;
			}
			candidates.addAll(level.getEntities(type, mob -> mob.isAlive() && !mob.isBaby() && !mob.hasCustomName() && !mob.isLeashed() && !mob.isPassenger()
					&& !mob.isVehicle() && !mobs.isTampered(mob) && mob.position().distanceTo(spot.feet()) <= 96.0));
		}
		candidates.sort(Comparator.comparingDouble(mob -> mob.position().distanceToSqr(spot.feet())));
		int ticks = (int) ModConfig.realTicks(EntityConfig.get().fakeHoldSeconds);
		for (Mob mob : candidates) {
			if (!mobs.freeze(mob, ticks)) {
				continue;
			}
			if (!mobs.moveOutOfView(mob, BlockPos.containing(spot.feet()))) {
				mobs.release(mob); // held where it was for nothing: let go
				continue;
			}
			mobs.face(mob, player.getEyePosition(), ticks);
			mobs.bind(mob, player.getUUID());
			if (record != null) {
				record.recordFake(GlobalPos.of(level.dimension(), mob.blockPosition()), GameClock.day(level.getServer()));
			}
			return true;
		}
		return false;
	}
}
