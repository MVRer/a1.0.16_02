package com.forzacode.a1016_02.lore;

import com.forzacode.a1016_02.core.ModConfig;

/**
 * Lore's tunables, stored under {@code sections.lore} in {@code config/a1016_02.json}. Real-time values go through
 * {@link ModConfig#realTicks} (so {@code devFastMode} applies); nothing here is faster than the design.
 */
public final class LoreConfig {
	/** How often the engine looks for fragments to place (also right after every stage change). */
	public double placementCheckSeconds = 60;
	/** After a fragment could not be placed (nowhere out of view, no site), wait this long before trying it again. */
	public double retrySeconds = 180;
	/** Candidate spots an own build may try per attempt (each may load or generate a chunk). */
	public int candidatesPerAttempt = 3;
	/** Fragments attempted per check, so a stage change does not place everything in one tick. */
	public int attemptsPerCheck = 2;
	/**
	 * A fragment that fills a site world or dig record (hut, tunnel end, pyramid...) waits this many minutes of play
	 * after it could first be placed before lore builds a minimal site of its own: the real one may only exist once
	 * its chunk generates.
	 */
	public double ownBuildDelayMinutes = 60;
	/** Chunks placement may ask to load, by ticket, per server tick. Placement never loads a chunk itself. */
	public int chunkLoadsPerTick = 2;
	/** A fragment waiting for its chunks (or for the camp search) is tried again after this long. */
	public double loadWaitSeconds = 2;
	/** F28: how far (in chunks, from the base) the background search looks for an abandoned camp. */
	public int campSearchRadiusChunks = 48;
	/** F28: after a search found no camp, wait this long before searching again. */
	public double campRetryMinutes = 20;

	/** Reading a sign: the player stands this close (eye to the block's center, blocks)... */
	public double readDistance = 4.5;
	/** ...looks at it within this cone (full angle, degrees)... */
	public double readConeDegrees = 50;
	/** ...for this long. */
	public double readDwellSeconds = 1.0;

	/** CARRYING_LIST: how often the subject's inventory is checked for the list (F06). */
	public double carryingListCheckSeconds = 30;
	/** CARRYING_LIST: at most one trigger per this many minutes while it is carried. */
	public double carryingListIntervalMinutes = 20;
	/** RULES_BOOK_NEAR_BASE: how often containers near the base are checked for the rules book (F15). */
	public double rulesBookCheckSeconds = 120;
	/** RULES_BOOK_NEAR_BASE: at most one trigger per this many minutes while it is stored there. */
	public double rulesBookIntervalMinutes = 30;
	/** RULES_BOOK_NEAR_BASE: "near your base" radius, blocks. */
	public int rulesBookBaseRadius = 16;

	/** {@code /a1016 lore place}: distance band around the player. */
	public int debugMinDistance = 6;
	public int debugMaxDistance = 24;
	/** {@code /a1016 lore place}: sites within this many blocks of the player are used first. */
	public int debugSiteRadius = 64;

	public static LoreConfig get() {
		return ModConfig.section("lore", LoreConfig.class, LoreConfig::new);
	}
}
