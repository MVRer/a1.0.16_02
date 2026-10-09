package com.forzacode.a1016_02.director;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Everything the director remembers: stage timers, deck cycles, held cards, pacing timestamps, quiet, the
 * current session, trigger bookkeeping and a short fire history. Plain data; {@link DirectorBrain} changes it,
 * {@link DirectorData} stores it. All times are play ticks unless the name says day ticks (in-game days * 24000
 * plus time of day, including timewarp).
 */
public final class DirectorMemory {
	/** "Never happened": far enough in the past that subtracting it from any play time cannot overflow. */
	public static final long NEVER = Long.MIN_VALUE / 4;

	/** One fire, newest last in {@link #history()}. */
	public record HistoryEntry(long playTicks, long day, String cardId, Tier tier, boolean fake, boolean forced, Stage stage) {
	}

	// --- stage timers ---
	long lastStepPlay = -1;
	/** Play ticks weighted by attention; stages move when it passes the rolled points. -1 = not started. */
	double stageClock = -1;
	long tracesAt = -1;
	long proximityAt = -1;
	String rolledTempo = "";
	long nextMajorDue = -1;

	// --- decks ---
	/** Cards fired in the current cycle of each deck; cleared when the deck runs out. */
	final Map<Tier, Set<String>> cycleFired = new EnumMap<>(Tier.class);
	final Map<Tier, String> lastFired = new EnumMap<>(Tier.class);
	final Map<Tier, String> held = new EnumMap<>(Tier.class);
	final Map<Tier, Long> heldSince = new EnumMap<>(Tier.class);
	final Map<Tier, String> lastGivenUp = new EnumMap<>(Tier.class);
	final Set<String> signaturesFired = new LinkedHashSet<>();

	// --- pacing ---
	long lastFireAny = NEVER;
	final Map<Tier, Long> lastFireTier = new EnumMap<>(Tier.class);
	long lastMajorOrSignature = NEVER;
	final Map<CardTag, Long> lastTag = new EnumMap<>(CardTag.class);
	int aloneAmbientCount;
	long sightingDay = -1;
	int sightingsThatDay;

	// --- quiet ---
	long quietUntilDayTicks = -1;
	long quietStartDayTicks = -1;
	int quietCount;

	// --- session ---
	long sessionStart = -1;
	boolean sessionEmpty;
	long sessionEmptyUntil = -1;
	double sessionMinorRate = -1;
	int sessionCount;
	int emptySessionCount;

	// --- stats and history ---
	int totalFires;
	int fakeFires;
	/** Fires of cards that could have been fakes (the denominator of the fake ratio). */
	int fakeableFires;
	final ArrayDeque<HistoryEntry> history = new ArrayDeque<>();

	// --- attention trigger bookkeeping ---
	long lowRenderAccum;
	long daylightAccum;
	long lastSleptDay = -1;
	long stopSeenDay = -1;
	long lastTellingDay = -1;
	boolean obeyCounted;
	long avoidCountedVisitDay = -1;
	long lastAvoidCheckDay = -1;
	long lastDisc13Play = NEVER;
	String lastWarpSummary = "";

	public DirectorMemory() {
		for (Tier tier : Tier.values()) {
			cycleFired.put(tier, new LinkedHashSet<>());
		}
	}

	// --- read access for other classes in this workstream and tests ---

	public List<HistoryEntry> history() {
		return Collections.unmodifiableList(new ArrayList<>(history));
	}

	public long quietUntilDayTicks() {
		return quietUntilDayTicks;
	}

	public long lastTagFire(CardTag tag) {
		return lastTag.getOrDefault(tag, NEVER);
	}

	public String held(Tier tier) {
		return held.get(tier);
	}

	public Set<String> cycleFired(Tier tier) {
		return Collections.unmodifiableSet(cycleFired.get(tier));
	}

	public Set<String> signaturesFired() {
		return Collections.unmodifiableSet(signaturesFired);
	}

	void addHistory(HistoryEntry entry, int max) {
		history.addLast(entry);
		while (history.size() > Math.max(1, max)) {
			history.removeFirst();
		}
	}

	/** A deep copy (dry runs work on copies). */
	public DirectorMemory copy() {
		return fromTag(toTag());
	}

	// --- storage ---

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("lastStepPlay", lastStepPlay);
		tag.putDouble("stageClock", stageClock);
		tag.putLong("tracesAt", tracesAt);
		tag.putLong("proximityAt", proximityAt);
		tag.putString("rolledTempo", rolledTempo);
		tag.putLong("nextMajorDue", nextMajorDue);

		CompoundTag cycles = new CompoundTag();
		cycleFired.forEach((tier, ids) -> cycles.putString(tier.name(), String.join(",", ids)));
		tag.put("cycleFired", cycles);
		tag.put("lastFired", strings(lastFired));
		tag.put("held", strings(held));
		tag.put("heldSince", longs(heldSince));
		tag.put("lastGivenUp", strings(lastGivenUp));
		tag.putString("signaturesFired", String.join(",", signaturesFired));

		tag.putLong("lastFireAny", lastFireAny);
		tag.put("lastFireTier", longs(lastFireTier));
		tag.putLong("lastMajorOrSignature", lastMajorOrSignature);
		tag.put("lastTag", longs(lastTag));
		tag.putInt("aloneAmbientCount", aloneAmbientCount);
		tag.putLong("sightingDay", sightingDay);
		tag.putInt("sightingsThatDay", sightingsThatDay);

		tag.putLong("quietUntilDayTicks", quietUntilDayTicks);
		tag.putLong("quietStartDayTicks", quietStartDayTicks);
		tag.putInt("quietCount", quietCount);

		tag.putLong("sessionStart", sessionStart);
		tag.putBoolean("sessionEmpty", sessionEmpty);
		tag.putLong("sessionEmptyUntil", sessionEmptyUntil);
		tag.putDouble("sessionMinorRate", sessionMinorRate);
		tag.putInt("sessionCount", sessionCount);
		tag.putInt("emptySessionCount", emptySessionCount);

		tag.putInt("totalFires", totalFires);
		tag.putInt("fakeFires", fakeFires);
		tag.putInt("fakeableFires", fakeableFires);
		ListTag list = new ListTag();
		for (HistoryEntry entry : history) {
			CompoundTag e = new CompoundTag();
			e.putLong("play", entry.playTicks());
			e.putLong("day", entry.day());
			e.putString("card", entry.cardId());
			e.putString("tier", entry.tier().name());
			e.putBoolean("fake", entry.fake());
			e.putBoolean("forced", entry.forced());
			e.putString("stage", entry.stage().name());
			list.add(e);
		}
		tag.put("history", list);

		tag.putLong("lowRenderAccum", lowRenderAccum);
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

	public static DirectorMemory fromTag(CompoundTag tag) {
		DirectorMemory m = new DirectorMemory();
		m.lastStepPlay = tag.getLongOr("lastStepPlay", -1);
		m.stageClock = tag.getDoubleOr("stageClock", -1);
		m.tracesAt = tag.getLongOr("tracesAt", -1);
		m.proximityAt = tag.getLongOr("proximityAt", -1);
		m.rolledTempo = tag.getStringOr("rolledTempo", "");
		m.nextMajorDue = tag.getLongOr("nextMajorDue", -1);

		CompoundTag cycles = tag.getCompoundOrEmpty("cycleFired");
		for (Tier tier : Tier.values()) {
			m.cycleFired.get(tier).addAll(split(cycles.getStringOr(tier.name(), "")));
		}
		readStrings(tag.getCompoundOrEmpty("lastFired"), m.lastFired);
		readStrings(tag.getCompoundOrEmpty("held"), m.held);
		readLongs(tag.getCompoundOrEmpty("heldSince"), m.heldSince, Tier::valueOf);
		readStrings(tag.getCompoundOrEmpty("lastGivenUp"), m.lastGivenUp);
		m.signaturesFired.addAll(split(tag.getStringOr("signaturesFired", "")));

		m.lastFireAny = tag.getLongOr("lastFireAny", NEVER);
		readLongs(tag.getCompoundOrEmpty("lastFireTier"), m.lastFireTier, Tier::valueOf);
		m.lastMajorOrSignature = tag.getLongOr("lastMajorOrSignature", NEVER);
		readLongs(tag.getCompoundOrEmpty("lastTag"), m.lastTag, CardTag::valueOf);
		m.aloneAmbientCount = tag.getIntOr("aloneAmbientCount", 0);
		m.sightingDay = tag.getLongOr("sightingDay", -1);
		m.sightingsThatDay = tag.getIntOr("sightingsThatDay", 0);

		m.quietUntilDayTicks = tag.getLongOr("quietUntilDayTicks", -1);
		m.quietStartDayTicks = tag.getLongOr("quietStartDayTicks", -1);
		m.quietCount = tag.getIntOr("quietCount", 0);

		m.sessionStart = tag.getLongOr("sessionStart", -1);
		m.sessionEmpty = tag.getBooleanOr("sessionEmpty", false);
		m.sessionEmptyUntil = tag.getLongOr("sessionEmptyUntil", -1);
		m.sessionMinorRate = tag.getDoubleOr("sessionMinorRate", -1);
		m.sessionCount = tag.getIntOr("sessionCount", 0);
		m.emptySessionCount = tag.getIntOr("emptySessionCount", 0);

		m.totalFires = tag.getIntOr("totalFires", 0);
		m.fakeFires = tag.getIntOr("fakeFires", 0);
		m.fakeableFires = tag.getIntOr("fakeableFires", 0);
		ListTag list = tag.getListOrEmpty("history");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompoundOrEmpty(i);
			try {
				m.history.addLast(new HistoryEntry(e.getLongOr("play", 0), e.getLongOr("day", 0), e.getStringOr("card", "?"),
						Tier.valueOf(e.getStringOr("tier", "AMBIENT")), e.getBooleanOr("fake", false), e.getBooleanOr("forced", false),
						Stage.valueOf(e.getStringOr("stage", "ALONE"))));
			} catch (IllegalArgumentException ignored) {
				// unknown enum name from an older version: drop the entry
			}
		}

		m.lowRenderAccum = tag.getLongOr("lowRenderAccum", 0);
		m.daylightAccum = tag.getLongOr("daylightAccum", 0);
		m.lastSleptDay = tag.getLongOr("lastSleptDay", -1);
		m.stopSeenDay = tag.getLongOr("stopSeenDay", -1);
		m.lastTellingDay = tag.getLongOr("lastTellingDay", -1);
		m.obeyCounted = tag.getBooleanOr("obeyCounted", false);
		m.avoidCountedVisitDay = tag.getLongOr("avoidCountedVisitDay", -1);
		m.lastAvoidCheckDay = tag.getLongOr("lastAvoidCheckDay", -1);
		m.lastDisc13Play = tag.getLongOr("lastDisc13Play", NEVER);
		m.lastWarpSummary = tag.getStringOr("lastWarpSummary", "");
		return m;
	}

	private static CompoundTag strings(Map<Tier, String> map) {
		CompoundTag tag = new CompoundTag();
		map.forEach((tier, value) -> {
			if (value != null) {
				tag.putString(tier.name(), value);
			}
		});
		return tag;
	}

	private static <E extends Enum<E>> CompoundTag longs(Map<E, Long> map) {
		CompoundTag tag = new CompoundTag();
		map.forEach((key, value) -> tag.putLong(key.name(), value));
		return tag;
	}

	private static void readStrings(CompoundTag tag, Map<Tier, String> into) {
		for (Tier tier : Tier.values()) {
			tag.getString(tier.name()).filter(s -> !s.isEmpty()).ifPresent(s -> into.put(tier, s));
		}
	}

	private static <E extends Enum<E>> void readLongs(CompoundTag tag, Map<E, Long> into, Function<String, E> parse) {
		for (String key : tag.keySet()) {
			try {
				E e = parse.apply(key);
				tag.getLong(key).ifPresent(v -> into.put(e, v));
			} catch (IllegalArgumentException ignored) {
				// unknown enum name from an older version
			}
		}
	}

	private static List<String> split(String joined) {
		List<String> out = new ArrayList<>();
		for (String part : joined.split(",")) {
			if (!part.isBlank()) {
				out.add(part.trim());
			}
		}
		return out;
	}
}
