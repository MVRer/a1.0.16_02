package com.forzacode.a1016_02.director;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;

import net.minecraft.nbt.CompoundTag;

/**
 * The director's memory of the world, shared by everyone (D-058, D-059): the stage clock and its rolled points (one
 * stage for everyone, moved on the world clock {@code GameClock.playTicks(server)}), the once-per-world cards that
 * fired, when each card tag last fired for anyone, and the attention triggers that count once per occasion rather
 * than once per witness. Everything a player's own pacing needs is in their {@link PacingMemory}. Plain data;
 * {@link DirectorBrain} and {@link DirectorTriggers} change it, {@link DirectorMemory} stores it. Times are world play
 * ticks unless the name says day ticks or day.
 */
public final class WorldMemory {
	// --- stage timers (world clock) ---
	long lastStepPlay = -1;
	/** World play ticks weighted by attention; the stage moves when it passes the rolled points. -1 = not started. */
	double stageClock = -1;
	long tracesAt = -1;
	long proximityAt = -1;
	String rolledTempo = "";

	// --- once per world ---
	/** Signature and profile-signature cards that fired, for anyone (D-059: whoever gets there first). */
	final Set<String> signaturesFired = new LinkedHashSet<>();
	/** World play tick at which a card with each tag last fired, for anyone ({@code Director.ticksSinceTag}). */
	final Map<CardTag, Long> lastTag = new EnumMap<>(CardTag.class);

	// --- attention triggers that count once per occasion (see DirectorTriggers) ---
	/** DAYLIGHT_OPEN_AREAS: world play ticks with someone in open daylight since it last counted. */
	long daylightAccum;
	/** SLEPT: the in-game day whose night was last counted (one per night, however many sleep). */
	long lastSleptDay = -1;
	/** OBEYED_AFTER_STOP: the group's obeying (D-064). */
	long stopSeenDay = -1;
	long lastTellingDay = -1;
	boolean obeyCounted;
	/** AVOIDED_TRACES: the shared footprint's last visit day that already counted, and the last day checked. */
	long avoidCountedVisitDay = -1;
	long lastAvoidCheckDay = -1;
	/** DISC_13_UNDERGROUND: world play tick of the last jukebox that counted (one per jukebox, then the cooldown). */
	long lastDisc13Play = DirectorMemory.NEVER;
	String lastWarpSummary = "";

	public WorldMemory() {
	}

	// --- read access for other classes in this workstream and tests ---

	public Set<String> signaturesFired() {
		return Collections.unmodifiableSet(signaturesFired);
	}

	/** World play tick at which a card with this tag last fired for anyone, or {@link DirectorMemory#NEVER}. */
	public long lastTagFire(CardTag tag) {
		return lastTag.getOrDefault(tag, DirectorMemory.NEVER);
	}

	/** A deep copy (dry runs work on copies). */
	public WorldMemory copy() {
		return fromTag(toTag());
	}

	// --- storage (the same keys a v1 director.dat used at its top level, so the old file reads as it is) ---

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("lastStepPlay", lastStepPlay);
		tag.putDouble("stageClock", stageClock);
		tag.putLong("tracesAt", tracesAt);
		tag.putLong("proximityAt", proximityAt);
		tag.putString("rolledTempo", rolledTempo);
		tag.putString("signaturesFired", String.join(",", signaturesFired));
		tag.put("lastTag", DirectorMemory.longs(lastTag));
		tag.putLong("daylightAccum", daylightAccum);
		tag.putLong("lastSleptDay", lastSleptDay);
		tag.putLong("stopSeenDay", stopSeenDay);
		tag.putLong("lastTellingDay", lastTellingDay);
		tag.putBoolean("obeyCounted", obeyCounted);
		tag.putLong("avoidCountedVisitDay", avoidCountedVisitDay);
		tag.putLong("lastAvoidCheckDay", lastAvoidCheckDay);
		tag.putLong("lastDisc13Play", lastDisc13Play);
		tag.putString("lastWarpSummary", lastWarpSummary);
		return tag;
	}

	/** Reads a world part, or the world fields at the top level of a v1 {@code director.dat}. */
	public static WorldMemory fromTag(CompoundTag tag) {
		WorldMemory w = new WorldMemory();
		w.lastStepPlay = tag.getLongOr("lastStepPlay", -1);
		w.stageClock = tag.getDoubleOr("stageClock", -1);
		w.tracesAt = tag.getLongOr("tracesAt", -1);
		w.proximityAt = tag.getLongOr("proximityAt", -1);
		w.rolledTempo = tag.getStringOr("rolledTempo", "");
		w.signaturesFired.addAll(DirectorMemory.split(tag.getStringOr("signaturesFired", "")));
		DirectorMemory.readLongs(tag.getCompoundOrEmpty("lastTag"), w.lastTag, CardTag::valueOf);
		w.daylightAccum = tag.getLongOr("daylightAccum", 0);
		w.lastSleptDay = tag.getLongOr("lastSleptDay", -1);
		w.stopSeenDay = tag.getLongOr("stopSeenDay", -1);
		w.lastTellingDay = tag.getLongOr("lastTellingDay", -1);
		w.obeyCounted = tag.getBooleanOr("obeyCounted", false);
		w.avoidCountedVisitDay = tag.getLongOr("avoidCountedVisitDay", -1);
		w.lastAvoidCheckDay = tag.getLongOr("lastAvoidCheckDay", -1);
		w.lastDisc13Play = tag.getLongOr("lastDisc13Play", DirectorMemory.NEVER);
		w.lastWarpSummary = tag.getStringOr("lastWarpSummary", "");
		return w;
	}
}
