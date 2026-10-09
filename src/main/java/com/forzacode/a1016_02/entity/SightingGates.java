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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The context gates of a sighting (DESIGN.md "Sightings"): fog or dusk, never in clear daylight, never during
 * combat, never indoors, never at the base, 300+ blocks from the last one, never two in one in-game day, never the
 * same variant twice in a row, one figure at a time. Debug fires skip all of this; never the out-of-view rule.
 *
 * <p>Each variant runs only in its own dimension (D-034), checked first and against the level the card is asked
 * about (the subject's own). In the End and the Nether time stands still, so the daylight, dusk and night gates do
 * not apply; the Nether's roof does not make it indoors; and he needs existing endermen or zombified piglins near the
 * edge to stand among.
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

		// The dimension first: each card only ever fits in its own (D-034), checked against the level it is asked about.
		if (!dimensionFits(variant, level)) {
			return Optional.of("not in " + dimensionName(variant.dimension()));
		}
		if (variant == Variant.LAST_ONE && (!state.hasFlag(LAST_SIGHTING_FLAG) || state.hasFlag(HimEntity.LAST_SIGHTING_SEEN_FLAG))) {
			return Optional.of("Ending A only");
		}
		if (!state.stage().atLeast(variant.earliestStage())) {
			return Optional.of("before " + variant.earliestStage());
		}
		if (!player.isAlive() || player.isSpectator() || player.isSleeping()) {
			return Optional.of("player busy");
		}
		if (FigureApi.anyOut(server)) {
			return Optional.of("he is already out");
		}
		// No daylight, dusk or night where time stands still (the End, the Nether): the time gates are the overworld's.
		if (!level.dimensionType().hasFixedTime()) {
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
		if (amongMobs(variant) && mobsAtEdge(player, variant, FigureApi.band(player, variant), config.amongMobsRadius).isEmpty()) {
			return Optional.of(variant == Variant.AMONG_ENDERMEN ? "no endermen at the edge" : "no zombified piglins at the edge of the fog");
		}
		return Optional.empty();
	}

	/** True if the card runs in this level's dimension (D-034): the End, the Nether, or else the overworld. */
	public static boolean dimensionFits(Variant variant, Level level) {
		return level.dimension() == variant.dimension();
	}

	private static String dimensionName(ResourceKey<Level> dimension) {
		if (dimension == Level.END) {
			return "the End";
		}
		return dimension == Level.NETHER ? "the Nether" : "the overworld";
	}

	/** True for the variants that stand among existing mobs (D-034). */
	static boolean amongMobs(Variant variant) {
		return variant.spot() == Variant.Spot.ENDERMEN || variant.spot() == Variant.Spot.PIGLINS;
	}

	/**
	 * The feet of the existing mobs he would stand among (endermen in the End, zombified piglins in the Nether), alive
	 * and within {@code radius} of the band around the player. Never spawns one; empty for the other variants.
	 */
	public static List<Vec3> mobsAtEdge(ServerPlayer player, Variant variant, FigureApi.Band band, double radius) {
		EntityType<? extends Mob> type = switch (variant.spot()) {
			case ENDERMEN -> EntityTypes.ENDERMAN;
			case PIGLINS -> EntityTypes.ZOMBIFIED_PIGLIN;
			default -> null;
		};
		if (type == null) {
			return List.of();
		}
		Vec3 at = player.position();
		return player.level().getEntities(type, mob -> {
			double d = SpotFinder.horizontal(at, mob.position());
			return mob.isAlive() && !mob.isPassenger() && d >= band.inner() - radius && d <= band.outer() + radius;
		}).stream().map(Entity::position).toList();
	}

	/**
	 * Something solid over the player's head (leaves don't count: a tree is not a roof). Under a ceiling (the
	 * Nether's bedrock roof) the whole dimension is roofed: only a block within {@code ceilingIndoorsBlocks} over the
	 * head counts, a low overhang or a tunnel.
	 */
	public static boolean indoors(ServerLevel level, ServerPlayer player) {
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		if (level.dimensionType().hasCeiling()) {
			int blocks = Math.max(1, EntityConfig.get().ceilingIndoorsBlocks);
			for (int dy = 1; dy <= blocks; dy++) {
				BlockPos over = eye.above(dy);
				if (!level.getBlockState(over).getCollisionShape(level, over).isEmpty()) {
					return true;
				}
			}
			return false;
		}
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
