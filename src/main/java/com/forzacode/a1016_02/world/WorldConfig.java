package com.forzacode.a1016_02.world;

import java.util.LinkedHashMap;
import java.util.Map;

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
