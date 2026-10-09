package com.forzacode.a1016_02.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;

/**
 * The world workstream's tunables, stored under {@code sections.world} in {@code config/a1016_02.json}.
 * Shared rules (old scars far from spawn, new scars only after days away, the mountain light distance) come from
 * {@link com.forzacode.a1016_02.core.Pacing}; only world-specific numbers live here.
 */
public final class WorldConfig {
	// --- old scars (worldgen) ---
	/** Side of the grid cell that can hold one small scar (a build, a cross, a light, a cut, a stair, a pyramid). */
	public int pointCellBlocks = 192;
	/** Side of the grid cell that can hold one large scar (a dead mountain or a bare forest). */
	public int areaCellBlocks = 768;
	/** Chance that a small-scar cell holds a scar, by density: SPARSE, NORMAL, HEAVY. */
	public double[] pointChance = {0.22, 0.40, 0.62};
	/** Chance that a large-scar cell holds a scar, by density: SPARSE, NORMAL, HEAVY. */
	public double[] areaChance = {0.30, 0.50, 0.75};
	/** Weight multiplier for scars that match one of the world's habits. */
	public double habitBoost = 5.0;
	/** Base weight of each scar kind (before the habit boost). Missing entries use the defaults. */
	public Map<String, Double> baseWeights = defaultWeights();
	/** The one ruined hut per world: distance band from world spawn. */
	public int ruinedHutMinBlocks = 300;
	public int ruinedHutMaxBlocks = 800;
	/** The largest ocean pyramid within this distance of spawn gets the air pocket at its core. */
	public int corePyramidRadius = 1500;
	public int deadMountainMinRadius = 40;
	public int deadMountainMaxRadius = 96;
	public int bareForestMinRadius = 48;
	public int bareForestMaxRadius = 160;
	/**
	 * D-051: chance that a hilltop cross is a glass memorial left by others instead of his cross, in Mourner worlds
	 * (about 1 in 6) and in every other world (about 1 in 15).
	 */
	public double glassCrossChanceMourner = 1.0 / 6;
	public double glassCrossChanceOther = 1.0 / 15;

	// --- live cards ---
	/** How far from the subject the new-scar placer looks for stale areas, in chunks. */
	public int newScarSearchChunks = 24;
	/** Radius of a new dead hill or bare grove, in blocks. */
	public int newScarRadius = 20;
	/** A new scar never changes more blocks than this (it keeps the ledger small). */
	public int newScarMaxBlocks = 3000;
	/** The lone light near base is placed this far from the base (blocks). */
	public int baseLightMinDistance = 16;
	public int baseLightMaxDistance = 56;
	/** The emptied house only happens while the subject is at least this far from the build (blocks). */
	public int emptiedHouseAwayBlocks = 64;
	/** Abandoned builds farther than this from the subject are not considered (blocks). */
	public int emptiedHouseSearchBlocks = 2000;
	/** Days after a first visit to a bare grove without replanting before LEFT_GROVES_ALONE applies. */
	public int leftGrovesDays = 3;
	/** How often the world watcher runs (seconds; a cost setting, not divided by devFastMode). */
	public double watchSeconds = 5;

	// --- signatures: still burning (D-004) ---
	/** The camp stands about 2000 blocks from the subject: this band, in unvisited, unloaded chunks. */
	public int stillBurningMinBlocks = 1800;
	public int stillBurningMaxBlocks = 2200;
	/**
	 * With F21 rolled the camp also gets F21's emptied house. Lore puts F21 there at any distance, so the camp sits
	 * about 2000 blocks out as well ("nobody for 2000 blocks"): within this distance of the base, at least
	 * {@link #stillBurningF21MinBlocks} from the subject, farthest first.
	 */
	public int stillBurningF21MaxFromBase = 2200;
	public int stillBurningF21MinBlocks = 1800;
	/** Ticks the furnace still burns once its chunk ticks (16000 is one coal block). Furnaces only tick while loaded. */
	public int stillBurningLitTicks = 16000;
	/** Items waiting in the furnace's input (64 take 12800 ticks to smelt). */
	public int stillBurningInputCount = 64;
	/** Camp candidates loaded and checked per search; after that the search waits {@link #stillBurningRetryMinutes}. */
	public int stillBurningCandidates = 6;
	/** Real minutes before a failed search tries new candidates (a cost setting). */
	public double stillBurningRetryMinutes = 5;

	// --- signatures: your house, elsewhere (D-005) ---
	/** The copy stands this far from the base (blocks). Lore puts F27 in it, within 1500 of the base. */
	public int houseCopyMinDistance = 220;
	public int houseCopyMaxDistance = 480;
	/** Player-placed blocks this far (cube) around the first block and the base make up the first shelter. */
	public int houseCopyCaptureRadius = 12;
	/** A first shelter needs at least this many shell blocks to be copied, and is cut at the maximum. */
	public int houseCopyMinShellBlocks = 12;
	public int houseCopyMaxShellBlocks = 400;
	/** In-game days between two steps of moving blocks out of the real house. */
	public double houseCopyStepDays = 1.0;
	/** Blocks moved per step: a few at a time. */
	public int houseCopyMovesPerStepMin = 2;
	public int houseCopyMovesPerStepMax = 5;
	/** Ending B's finish takes buried local blocks this far (horizontally) around the copy for what the house cannot give. */
	public int houseCopyFinishSearch = 12;
	/** ...and from at most this deep under the copy's lowest block (the holes stay sealed underground). */
	public int houseCopyFinishDepth = 16;
	/** Copy site candidates checked per search. */
	public int houseCopyCandidates = 8;
	/** Real seconds between tries while a step waits for chunks or is in view (a cost setting). */
	public double houseCopyRetrySeconds = 30;

	// --- signatures: the row of crosses ---
	/** The hilltop is this far from the subject (blocks), inside loaded land and out of view. */
	public int crossRowMinDistance = 48;
	public int crossRowMaxDistance = 200;
	/** Blocks between two crosses of the row. */
	public int crossRowSpacing = 4;
	/** The row keeps this far from the base (blocks). */
	public int crossRowBaseClearance = 48;
	/** Buried blocks for the old crosses are taken at most this far around each cross (blocks). */
	public int crossRowMaterialRadius = 6;
	/** ...from 3 (always sealed under ground) down to this many blocks under the cross's ground. */
	public int crossRowMaterialDepth = 10;

	// --- the lone redstone torch (D-033) ---
	/** At most this many per world, at least this many in-game days apart. */
	public int loneTorchMax = 3;
	public int loneTorchMinDays = 7;
	/** A cave counts once the player has been away from it this many in-game days. */
	public int loneTorchAwayDays = 1;
	/** Never within this distance of the base (blocks). */
	public int loneTorchBaseRadius = 64;
	/** "Deep": at least this many blocks under the surface. */
	public int loneTorchMinDepth = 8;
	/** Dark floor is looked for this far around a remembered cave spot (blocks). */
	public int loneTorchSearch = 8;
	/** Cave spots remembered (the oldest are dropped). */
	public int caveSpotsMax = 128;

	// --- debug ---
	/** {@code /a1016 world place}: distance band from the player, in blocks. */
	public int placeMinDistance = 24;
	public int placeMaxDistance = 72;

	public WorldConfig() {
	}

	public static WorldConfig get() {
		return ModConfig.section("world", WorldConfig.class, WorldConfig::new);
	}

	public double baseWeight(ScarKind kind) {
		Double weight = baseWeights == null ? null : baseWeights.get(kind.id());
		return weight != null ? weight : kind.defaultWeight();
	}

	public double pointChance(int densityOrdinal) {
		return pick(pointChance, densityOrdinal, 0.4);
	}

	public double areaChance(int densityOrdinal) {
		return pick(areaChance, densityOrdinal, 0.5);
	}

	/** The glass memorial chance of a hilltop cross in a world with these habits (D-051). */
	public double glassCrossChance(Set<Habit> habits) {
		return Math.clamp(habits.contains(Habit.MOURNER) ? glassCrossChanceMourner : glassCrossChanceOther, 0.0, 1.0);
	}

	private static double pick(double[] values, int index, double fallback) {
		return values != null && index >= 0 && index < values.length ? values[index] : fallback;
	}

	private static Map<String, Double> defaultWeights() {
		Map<String, Double> weights = new LinkedHashMap<>();
		for (ScarKind kind : ScarKind.values()) {
			if (kind.worldgen()) {
				weights.put(kind.id(), kind.defaultWeight());
			}
		}
		return weights;
	}
}
