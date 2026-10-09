package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The context gates of a sighting (DESIGN.md "Sightings"): fog or dusk, never in clear daylight, never during
 * combat, never indoors, never at the base, 300+ blocks from the last one, never two in one in-game day, never the
 * same variant twice in a row, one figure at a time. Debug fires skip all of this; never the out-of-view rule.
 */
public final class SightingGates {
	/** Set by the ending workstream when Ending A's last sighting may happen. */
	public static final String LAST_SIGHTING_FLAG = "ending:last_sighting";

	private SightingGates() {
	}

	/** Why the variant can't happen for this player right now, or empty if it can. */
	public static Optional<String> check(ServerPlayer player, ServerLevel level, Variant variant) {
		MinecraftServer server = level.getServer();
		EntityConfig config = EntityConfig.get();
		Pacing pacing = ModConfig.pacing();
		HerobrineState state = HerobrineState.get(server);

		if (variant == Variant.LAST_ONE && (!state.hasFlag(LAST_SIGHTING_FLAG) || state.hasFlag(HimEntity.LAST_SIGHTING_SEEN_FLAG))) {
			return Optional.of("Ending A only");
		}
		if (!state.stage().atLeast(variant.earliestStage())) {
			return Optional.of("before " + variant.earliestStage());
		}
		if (level.dimension() != Level.OVERWORLD) {
			return Optional.of("not in the overworld");
		}
		if (!player.isAlive() || player.isSpectator() || player.isSleeping()) {
			return Optional.of("player busy");
		}
		if (FigureApi.anyOut(server)) {
			return Optional.of("he is already out");
		}
		long time = timeOfDay(server);
		boolean raining = level.isRaining();
		if (SightingRules.clearDaylight(time, raining, config)) {
			return Optional.of("clear daylight");
		}
		if (variant == Variant.RIDGE && !SightingRules.duskSky(time, raining, config)) {
			return Optional.of("no dusk sky");
		}
		if (variant == Variant.IN_THE_LIGHT && !SightingRules.night(time, config)) {
			return Optional.of("not night");
		}
		if (Services.watch().ticksSinceCombat(player) < ModConfig.realTicks(config.combatCooldownSeconds)) {
			return Optional.of("combat");
		}
		if (indoors(level, player)) {
			return Optional.of("indoors");
		}
		if (nearBase(player, player.blockPosition(), config.baseRadius)) {
			return Optional.of("near the base");
		}
		if (variant != Variant.LAST_ONE) {
			EntityData data = EntityData.get(server);
			if (!data.dayAllows(GameClock.day(server), pacing.sightingsPerDayMax)) {
				return Optional.of("already one today");
			}
			if (!data.variantAllows(variant)) {
				return Optional.of("same variant as last time");
			}
			if (!data.farEnough(GlobalPos.of(level.dimension(), player.blockPosition()), pacing.sightingMinSpacing)) {
				return Optional.of("too close to the last sighting");
			}
		}
		if (variant == Variant.IN_THE_LIGHT && lights(player, FigureApi.band(player, variant), EntityConfig.get().lightSearchRadius).isEmpty()) {
			return Optional.of("no lone light at the fog edge");
		}
		return Optional.empty();
	}

	/** Something solid over the player's head (leaves don't count: a tree is not a roof). */
	public static boolean indoors(ServerLevel level, ServerPlayer player) {
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, eye.getX(), eye.getZ()) > eye.getY();
	}

	/** Time of day, 0 (sunrise) to 23999. */
	public static long timeOfDay(MinecraftServer server) {
		return Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
	}

	/** True if {@code pos} is within {@code radius} blocks (horizontal) of the player's base. */
	public static boolean nearBase(ServerPlayer player, BlockPos pos, int radius) {
		Optional<GlobalPos> base = Services.watch().base(player);
		if (base.isEmpty() || !base.get().dimension().equals(player.level().dimension())) {
			return false;
		}
		BlockPos b = base.get().pos();
		long dx = b.getX() - pos.getX();
		long dz = b.getZ() - pos.getZ();
		return dx * dx + dz * dz < (long) radius * radius;
	}

	/**
	 * LONE_LIGHT sites whose glow edge ({@code radius} blocks around them) can reach into the band, nearest first.
	 * The spot finder still checks that his feet are in the band.
	 */
	public static List<BlockPos> lights(ServerPlayer player, FigureApi.Band band, int radius) {
		GlobalPos here = GlobalPos.of(player.level().dimension(), player.blockPosition());
		return Services.sites().find(SiteType.LONE_LIGHT, here, (int) Math.ceil(band.outer()) + radius).stream()
				.map(site -> site.pos())
				.filter(pos -> SpotFinder.horizontal(player.position(), Vec3.atBottomCenterOf(pos)) >= band.inner() - radius)
				.toList();
	}
}
