package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * The sighting values {@code /a1016 entity tune <key> <value>} sets live (the figure reads {@link EntityConfig} every
 * tick), each with the range it accepts. Keys are the config field names.
 */
final class EntityTuning {
	record Key(String name, double min, double max, ToDoubleFunction<EntityConfig> get, ObjDoubleConsumer<EntityConfig> set) {
		boolean accepts(double value) {
			return !Double.isNaN(value) && value >= min && value <= max;
		}
	}

	static final List<Key> KEYS = List.of(
			// Where he stands (D-035): shares of the fog end the client reports.
			new Key("normalFractionMin", 0.05, 1.0, c -> c.normalFractionMin, (c, v) -> c.normalFractionMin = v),
			new Key("normalFractionMax", 0.05, 1.0, c -> c.normalFractionMax, (c, v) -> c.normalFractionMax = v),
			new Key("closeFractionMin", 0.05, 1.0, c -> c.closeFractionMin, (c, v) -> c.closeFractionMin = v),
			new Key("closeFractionMax", 0.05, 1.0, c -> c.closeFractionMax, (c, v) -> c.closeFractionMax = v),
			// The close variant's clamp in blocks; the minimum distance still holds under it.
			new Key("closeMinDistance", EntityConfig.MIN_DISTANCE_FLOOR, 64.0, c -> c.closeMinDistance, (c, v) -> c.closeMinDistance = v),
			new Key("closeMaxDistance", EntityConfig.MIN_DISTANCE_FLOOR, 96.0, c -> c.closeMaxDistance, (c, v) -> c.closeMaxDistance = v),
			// Between trunks, in blocks (the trunk fix); the fog end's margin and the minimum distance still hold.
			new Key("trunkMinDistance", EntityConfig.MIN_DISTANCE_FLOOR, 96.0, c -> c.trunkMinDistance, (c, v) -> c.trunkMinDistance = v),
			new Key("trunkMaxDistance", EntityConfig.MIN_DISTANCE_FLOOR, 128.0, c -> c.trunkMaxDistance, (c, v) -> c.trunkMaxDistance = v),
			// Never under 8 blocks, whatever the file says.
			new Key("minDistance", EntityConfig.MIN_DISTANCE_FLOOR, 64.0, c -> c.minDistance, (c, v) -> c.minDistance = v),
			new Key("approachBlocks", 1.0, 64.0, c -> c.approachBlocks, (c, v) -> c.approachBlocks = v),
			new Key("approachSpawnFraction", 0.05, 0.9, c -> c.approachSpawnFraction, (c, v) -> c.approachSpawnFraction = v),
			// The flee distance is also held under fleeSpawnFraction of his spawn distance, so he never flees the moment he appears.
			new Key("fleeDistance", 0.0, 64.0, c -> c.fleeDistance, (c, v) -> c.fleeDistance = v),
			new Key("fleeSpawnFraction", 0.0, 0.9, c -> c.fleeSpawnFraction, (c, v) -> c.fleeSpawnFraction = v),
			new Key("stareSeconds", 0.25, 30.0, c -> c.stareSeconds, (c, v) -> c.stareSeconds = v),
			new Key("stareBackSeconds", 0.0, 10.0, c -> c.stareBackSeconds, (c, v) -> c.stareBackSeconds = v),
			new Key("minSeenSeconds", 0.0, 30.0, c -> c.minSeenSeconds, (c, v) -> c.minSeenSeconds = v),
			// The run (D-036), in blocks per second.
			new Key("baseRunSpeed", 2.0, 12.0, c -> c.baseRunSpeed, (c, v) -> c.baseRunSpeed = v),
			new Key("outrunFactor", 1.0, 2.0, c -> c.outrunFactor, (c, v) -> c.outrunFactor = v),
			new Key("maxRunSpeed", 4.0, 20.0, c -> c.maxRunSpeed, (c, v) -> c.maxRunSpeed = v),
			new Key("closeInFastSpeed", 0.5, 10.0, c -> c.closeInFastSpeed, (c, v) -> c.closeInFastSpeed = v),
			// Goes under (D-030): the share of leaving sightings that dig down instead, where the ground allows.
			new Key("goUnderChance", 0.0, 1.0, c -> c.goUnderChance, (c, v) -> c.goUnderChance = v),
			// Seconds per block dug or put back, and how deep he sinks (whole blocks, 4 to 8: he has to be under the cover).
			new Key("goUnderDigSeconds", 0.1, 2.0, c -> c.goUnderDigSeconds, (c, v) -> c.goUnderDigSeconds = v),
			new Key("goUnderMinDepth", GoUnder.MIN_DEPTH, GoUnder.MAX_DEPTH, c -> c.goUnderMinDepth, (c, v) -> c.goUnderMinDepth = (int) Math.round(v)),
			new Key("goUnderMaxDepth", GoUnder.MIN_DEPTH, GoUnder.MAX_DEPTH, c -> c.goUnderMaxDepth, (c, v) -> c.goUnderMaxDepth = (int) Math.round(v)),
			// The close-chase rush (D-037): how close a chaser he cannot outrun gets before he turns and runs past them,
			// how far beside them he passes (never under 1.5), and how long the rush may take before he just runs.
			new Key("rushTriggerDistance", 2.0, 32.0, c -> c.rushTriggerDistance, (c, v) -> c.rushTriggerDistance = v),
			new Key("rushPassOffset", Rush.MIN_OFFSET, 6.0, c -> c.rushPassOffset, (c, v) -> c.rushPassOffset = v),
			new Key("rushMaxSeconds", 0.5, 10.0, c -> c.rushMaxSeconds, (c, v) -> c.rushMaxSeconds = v),
			// The End and the Nether (D-034): an enderman or zombified piglin within this many blocks of his spot.
			new Key("amongMobsRadius", 2.0, 16.0, c -> c.amongMobsRadius, (c, v) -> c.amongMobsRadius = v),
			// The glimpse (D-073): how far from everyone he stands (never under 8), the client's hide angle and distance, the
			// server's look angle, the approach, the time seen and the wait unseen before he is gone.
			new Key("glimpseMinDistance", EntityConfig.MIN_DISTANCE_FLOOR, 32.0, c -> c.glimpseMinDistance, (c, v) -> c.glimpseMinDistance = v),
			new Key("glimpseHideDegrees", 5.0, 60.0, c -> c.glimpseHideDegrees, (c, v) -> c.glimpseHideDegrees = v),
			new Key("glimpseHideBlocks", 2.0, 14.0, c -> c.glimpseHideBlocks, (c, v) -> c.glimpseHideBlocks = v),
			new Key("glimpseServerDegrees", 5.0, 75.0, c -> c.glimpseServerDegrees, (c, v) -> c.glimpseServerDegrees = v),
			new Key("glimpseApproachBlocks", 0.5, 8.0, c -> c.glimpseApproachBlocks, (c, v) -> c.glimpseApproachBlocks = v),
			new Key("glimpseSeenSeconds", 0.5, 10.0, c -> c.glimpseSeenSeconds, (c, v) -> c.glimpseSeenSeconds = v),
			new Key("glimpseWaitSeconds", 1.0, 60.0, c -> c.glimpseWaitSeconds, (c, v) -> c.glimpseWaitSeconds = v));

	private EntityTuning() {
	}

	static Optional<Key> byName(String name) {
		return KEYS.stream().filter(k -> k.name().equalsIgnoreCase(name)).findFirst();
	}

	/** {@code key=value} for every key, on one line. */
	static String describe(EntityConfig config) {
		return KEYS.stream().map(k -> k.name() + "=" + format(k.get().applyAsDouble(config))).collect(Collectors.joining(" "));
	}

	static String format(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
	}
}
