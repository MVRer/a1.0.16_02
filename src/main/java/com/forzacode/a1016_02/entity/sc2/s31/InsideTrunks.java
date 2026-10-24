package com.forzacode.a1016_02.entity.sc2.s31;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.EntityData;
import com.forzacode.a1016_02.entity.FigureApi;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.Variant;
import com.forzacode.a1016_02.entity.Watchers;
import com.forzacode.a1016_02.entity.sc.ScGates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 31c (docs/SELF_CREATED_2.md, {@code sc_31c}): the player inside one of his places that has a stand of trunks in its middle
 * (the kind with a grove, its anchor the grove's centre), within {@code trunksMax} of it: half of him stands behind one of
 * those trunks {@code trunkMinDistance} to {@code trunkMaxDistance} (18 to 40) off, as seen from the player's eyes
 * ({@link FigureApi#behindGivenTrunk}), in the dark of the place where the player's own light just reaches him (raw light at his eyes
 * {@link #MIN_LIGHT} to {@link #MAX_LIGHT}: never in the full dark, where nobody could see him, never by a torch; the dim
 * edge of the player's torchlight scores best), eyes FLAT, facing them ({@link Variant#INSIDE_TRUNKS}: between the trunks'
 * hiding, gone when they round the trunk). No daylight or indoors gate: the place is the dark. Every sighting rule holds
 * ({@link Apex#sightingGates}). Server thread.
 */
public final class InsideTrunks {
	public static final String CARD_ID = "sc_31c";
	/** Never in light brighter than this at his eyes (a torch beside the trunk keeps him away). */
	public static final int MAX_LIGHT = 7;
	/** Never in light darker than this at his eyes (nobody would see him). */
	public static final int MIN_LIGHT = 3;
	/** The trunks: logs this far round the grove's centre (blocks, horizontal)... */
	static final int GROVE_REACH = 10;
	/** ...whose foot is this far over or under the centre at most. */
	static final int GROVE_DEPTH = 4;

	/** Where he stands: his feet, his yaw, the trunk, how far, the score. */
	public record Spot(Vec3 feet, float yaw, BlockPos trunk, double distance, double score) {
	}

	private InsideTrunks() {
	}

	/** The place the player is inside that has a grove, within {@code trunksMax} of its centre, or empty. */
	public static Optional<Remains.Place> place(ServerPlayer player, Remains remains, Sc31Config config) {
		ServerLevel level = player.level();
		return remains.containing(level, player.blockPosition()).filter(p -> p.kind() == Remains.Kind.HOLLOW)
				.filter(p -> Math.hypot(p.anchor().getX() + 0.5 - player.getX(), p.anchor().getZ() + 0.5 - player.getZ()) <= config.trunksMax);
	}

	/** Why 31c does not fit the player now, or empty (live): the common gates, the sighting rules, the place, a spot. */
	public static Optional<String> fit(ServerPlayer player) {
		Optional<String> common = ScGates.refusal(player);
		if (common.isPresent()) {
			return common;
		}
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Optional<String> gates = Apex.sightingGates(player, Variant.INSIDE_TRUNKS, EntityData.get(server), GameClock.day(server));
		if (gates.isPresent()) {
			return gates;
		}
		Optional<Remains.Place> place = place(player, Services.remains(), Sc31Config.get());
		if (place.isEmpty()) {
			return Optional.of("not among the trunks of a place");
		}
		return spot(player, place.get(), Watchers.of(level), Apex.ticking(level)).isPresent() ? Optional.empty() : Optional.of("no trunk for him");
	}

	/** The trunks of the grove round this centre: the lowest log of every log column standing on a floor. */
	public static List<BlockPos> trunks(ServerLevel level, BlockPos centre) {
		List<BlockPos> trunks = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -GROVE_REACH; dx <= GROVE_REACH; dx++) {
			for (int dz = -GROVE_REACH; dz <= GROVE_REACH; dz++) {
				for (int dy = -GROVE_DEPTH; dy <= GROVE_DEPTH; dy++) {
					m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (!level.isLoaded(m) || !level.getBlockState(m).is(BlockTags.LOGS)) {
						continue;
					}
					BlockPos below = m.below();
					BlockState floor = level.getBlockState(below);
					if (!floor.is(BlockTags.LOGS) && floor.isFaceSturdy(level, below, Direction.UP) && level.getBlockState(m.above()).is(BlockTags.LOGS)) {
						trunks.add(m.immutable());
					}
				}
			}
		}
		return trunks;
	}

	/**
	 * The best spot behind a trunk of this place's grove for the player now: trunks 18 to 40 off (the trunk band), half of him
	 * behind each as seen from their eyes, ticking there, his eyes {@link #MIN_LIGHT} to {@link #MAX_LIGHT}, hidden from every watcher and
	 * past the minimum distance from each (the spawn rule). Scored toward the band's middle and the light's edge (5 to 7 best).
	 */
	public static Optional<Spot> spot(ServerPlayer player, Remains.Place place, Watchers watchers, Predicate<Vec3> ticking) {
		ServerLevel level = player.level();
		EntityConfig config = EntityConfig.get();
		double inner = Double.isNaN(config.trunkMinDistance) ? 18.0 : config.trunkMinDistance;
		double outer = Double.isNaN(config.trunkMaxDistance) ? 40.0 : config.trunkMaxDistance;
		double middle = (inner + outer) / 2.0;
		Vec3 eye = player.getEyePosition();
		double minDistance = config.minDistance();
		List<Spot> found = new ArrayList<>();
		for (BlockPos trunk : trunks(level, place.anchor())) {
			double d = Math.hypot(trunk.getX() + 0.5 - player.getX(), trunk.getZ() + 0.5 - player.getZ());
			if (d < inner || d > outer) {
				continue;
			}
			for (Vec3 feet : FigureApi.behindGivenTrunk(level, eye, trunk)) {
				int light = level.getMaxLocalRawBrightness(BlockPos.containing(feet.x, feet.y + 1.62, feet.z));
				if (light > MAX_LIGHT || light < MIN_LIGHT || !ticking.test(feet) || !watchers.hides(level, HimEntity.viewBox(feet), minDistance)) {
					continue;
				}
				double dim = light >= 5 ? 1.0 : 0.6;
				double centred = 1.0 - Math.min(1.0, Math.abs(d - middle) / Math.max(1.0, (outer - inner) / 2.0));
				found.add(new Spot(feet, HimEntity.yawToward(feet, player.position()), trunk, d, dim + centred + level.getRandom().nextDouble() * 0.3));
			}
		}
		return found.stream().max(Comparator.comparingDouble(Spot::score));
	}

	/**
	 * Fires 31c for this player (live): {@link FireResult#FIRED} with him behind a trunk; {@link FireResult#NO_SPOT} while he
	 * is out (forced: told to walk away first) or no trunk works now; {@link FireResult#SKIPPED} when the story holds it, the
	 * player is not among the trunks of a place, or (unless forced) the gates do.
	 */
	public static FireResult fire(ServerPlayer player, boolean forced) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		if (ScGates.storyRefusal(server).isPresent() || level.dimension() != Variant.INSIDE_TRUNKS.dimension()) {
			return FireResult.SKIPPED;
		}
		if (FigureApi.anyOut(server)) {
			if (forced) {
				FigureApi.walkAway(server);
			}
			return FireResult.NO_SPOT;
		}
		if (!forced) {
			Optional<String> common = ScGates.refusal(player).or(() -> Apex.sightingGates(player, Variant.INSIDE_TRUNKS, EntityData.get(server),
					GameClock.day(server)));
			if (common.isPresent()) {
				return FireResult.SKIPPED;
			}
		}
		Optional<Remains.Place> place = place(player, Services.remains(), Sc31Config.get());
		if (place.isEmpty()) {
			return FireResult.SKIPPED;
		}
		Optional<Spot> spot = spot(player, place.get(), Watchers.of(level), Apex.ticking(level));
		if (spot.isEmpty()) {
			return FireResult.NO_SPOT;
		}
		Spot s = spot.get();
		FireResult result = FigureApi.spawnGiven(player, Variant.INSIDE_TRUNKS, s.feet(), s.yaw(), s.trunk()).result();
		if (result == FireResult.FIRED) {
			A1016_02.LOGGER.info("[a1016] {}: for {} ({} out)", CARD_ID, player.getName().getString(), Math.round(s.distance()));
		}
		return result;
	}
}
