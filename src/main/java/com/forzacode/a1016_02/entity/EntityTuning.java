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
			new Key("spawnDistanceFractionMin", 0.05, 1.0, c -> c.spawnDistanceFractionMin, (c, v) -> c.spawnDistanceFractionMin = v),
			new Key("spawnDistanceFractionMax", 0.05, 1.0, c -> c.spawnDistanceFractionMax, (c, v) -> c.spawnDistanceFractionMax = v),
			// The close variant's band; never under the 24-block minimum (FogEdge also holds Pacing.sightingMinDistance).
			new Key("closeMinDistance", 24.0, 64.0, c -> c.closeMinDistance, (c, v) -> c.closeMinDistance = v),
			new Key("closeMaxDistance", 26.0, 96.0, c -> c.closeMaxDistance, (c, v) -> c.closeMaxDistance = v),
			new Key("approachBlocks", 1.0, 64.0, c -> c.approachBlocks, (c, v) -> c.approachBlocks = v),
			// Below the 24-block spawn minimum, so he never flees the moment he appears.
			new Key("fleeDistance", 0.0, 22.0, c -> c.fleeDistance, (c, v) -> c.fleeDistance = v),
			new Key("stareSeconds", 0.25, 30.0, c -> c.stareSeconds, (c, v) -> c.stareSeconds = v),
			new Key("stareBackSeconds", 0.0, 10.0, c -> c.stareBackSeconds, (c, v) -> c.stareBackSeconds = v),
			new Key("minSeenSeconds", 0.0, 30.0, c -> c.minSeenSeconds, (c, v) -> c.minSeenSeconds = v));

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
