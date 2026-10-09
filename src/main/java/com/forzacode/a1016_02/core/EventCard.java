package com.forzacode.a1016_02.core;

import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One event in the director's decks. Register it in your {@code init()} with {@link Director#register(EventCard)}.
 * A card never damages, targets or touches the player and never spawns a mob; every world edit goes through
 * {@link TraceService}, every mob change through {@link MobTamper}.
 */
public interface EventCard {
	/** Unique snake_case id, for example {@code "fog_drift"} or {@code "sighting_cow"}. */
	String id();

	Tier tier();

	/** The card is never drawn before this stage. */
	Stage earliestStage();

	/** Habits that make this card more likely. Empty means neutral. */
	Set<Habit> habits();

	Set<CardTag> tags();

	/** True if the card can fire as a false positive (about 1 in 3 fired events). */
	boolean hasFake();

	/** Context gate: does the moment fit right now? Cheap; called often. */
	boolean contextFits(ServerPlayer player, ServerLevel world);

	/**
	 * Runs the event. Return {@link FireResult#NO_SPOT} when no out-of-view place exists right now; the director
	 * keeps the card for later.
	 */
	FireResult fire(FireContext ctx);
}
