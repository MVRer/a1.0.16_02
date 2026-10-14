package com.forzacode.a1016_02.director;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * One player's own pacing (D-059): their session and join grace, empty sessions, the gaps and hard limits, the major
 * schedule, the forced quiet, pity, the decks with their held cards and no-repeat cycles, the fake counts, the
 * history, and the per-player trigger memory. Measured on that player's own clock
 * ({@code GameClock.playTicks(server, uuid)}); quiets and "in-game days" on the world's day ticks. Tension itself
 * lives in {@code HerobrineState.tension(uuid)}. A moment felt from someone else's card (D-060.3) is recorded here too.
 * Fixed in size whatever happens (the history is capped), and nothing in it grows or runs while the player is away.
 * Plain data; {@link DirectorBrain} changes it, {@link DirectorMemory} stores it.
 */
public final class PacingMemory {
	/** Day ticks when this player first took part: their own first in-game day (D-011's "no major on day 0"). */
	long firstDayTicks;
	/** This player's clock at their last time step (tension decay). */
	long lastStepPlay = -1;
	long nextMajorDue = -1;

	// --- decks (per player: their own no-repeat cycle, and cards held for their own moment) ---
	/** Cards fired or felt in the current cycle of each deck; cleared when the deck runs out. */
	final Map<Tier, Set<String>> cycleFired = new EnumMap<>(Tier.class);
	final Map<Tier, String> lastFired = new EnumMap<>(Tier.class);
	final Map<Tier, String> held = new EnumMap<>(Tier.class);
	final Map<Tier, Long> heldSince = new EnumMap<>(Tier.class);
	final Map<Tier, String> lastGivenUp = new EnumMap<>(Tier.class);
	/** Day ticks at which a deck first found nothing to draw while only gated cards held its reshuffle back. */
	final Map<Tier, Long> deckStuckSince = new EnumMap<>(Tier.class);

	// --- pacing (own fires and felt ones) ---
	long lastFireAny = DirectorMemory.NEVER;
	final Map<Tier, Long> lastFireTier = new EnumMap<>(Tier.class);
	/** This player's clock when a card with each tag last fired for them or was felt by them. */
	final Map<CardTag, Long> lastTag = new EnumMap<>(CardTag.class);
	long lastMajorOrSignature = DirectorMemory.NEVER;
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
	/** This player's own fires (felt moments are {@link #feltFires}). */
	int totalFires;
	int fakeFires;
	/** Own fires of cards that could have been fakes (the denominator of the fake ratio). */
	int fakeableFires;
	/** Someone else's cards felt here (D-060.3). */
	int feltFires;
	final ArrayDeque<DirectorMemory.HistoryEntry> history = new ArrayDeque<>();

	// --- per-player trigger bookkeeping ---
	/** LOW_RENDER_DISTANCE: this session's play ticks at a low view distance, and whether it counted this session. */
	long lowRenderAccum;
	boolean lowRenderCounted;

	/** A fresh memory whose first in-game day began at day tick 0. */
	public PacingMemory() {
		this(0);
	}

	/** A fresh memory for a player who first takes part at these day ticks. */
	public PacingMemory(long firstDayTicks) {
		this.firstDayTicks = firstDayTicks;
		for (Tier tier : Tier.values()) {
			cycleFired.put(tier, new LinkedHashSet<>());
		}
	}

	// --- read access for other classes in this workstream and tests ---

	public List<DirectorMemory.HistoryEntry> history() {
		return Collections.unmodifiableList(new ArrayList<>(history));
	}

	public long quietUntilDayTicks() {
		return quietUntilDayTicks;
	}

	public long firstDayTicks() {
		return firstDayTicks;
	}

	/** This player's clock when a card with this tag last fired for them or was felt by them, or {@link DirectorMemory#NEVER}. */
	public long lastTagFire(CardTag tag) {
		return lastTag.getOrDefault(tag, DirectorMemory.NEVER);
	}

	public String held(Tier tier) {
		return held.get(tier);
	}

	public Set<String> cycleFired(Tier tier) {
		return Collections.unmodifiableSet(cycleFired.get(tier));
	}

	void addHistory(DirectorMemory.HistoryEntry entry, int max) {
		history.addLast(entry);
		while (history.size() > Math.max(1, max)) {
			history.removeFirst();
		}
	}

	/** A deep copy (dry runs work on copies). */
	public PacingMemory copy() {
		return fromTag(toTag());
	}

	// --- storage (the same keys a v1 director.dat used at its top level, so the old file reads as it is) ---

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("firstDayTicks", firstDayTicks);
		tag.putLong("lastStepPlay", lastStepPlay);
		tag.putLong("nextMajorDue", nextMajorDue);

		CompoundTag cycles = new CompoundTag();
		cycleFired.forEach((tier, ids) -> cycles.putString(tier.name(), String.join(",", ids)));
		tag.put("cycleFired", cycles);
		tag.put("lastFired", DirectorMemory.strings(lastFired));
		tag.put("held", DirectorMemory.strings(held));
		tag.put("heldSince", DirectorMemory.longs(heldSince));
		tag.put("lastGivenUp", DirectorMemory.strings(lastGivenUp));
		tag.put("deckStuckSince", DirectorMemory.longs(deckStuckSince));

		tag.putLong("lastFireAny", lastFireAny);
		tag.put("lastFireTier", DirectorMemory.longs(lastFireTier));
		tag.put("lastTag", DirectorMemory.longs(lastTag));
		tag.putLong("lastMajorOrSignature", lastMajorOrSignature);
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
		tag.putInt("feltFires", feltFires);
		ListTag list = new ListTag();
		for (DirectorMemory.HistoryEntry entry : history) {
			CompoundTag e = new CompoundTag();
			e.putLong("play", entry.playTicks());
			e.putLong("day", entry.day());
			e.putString("card", entry.cardId());
			e.putString("tier", entry.tier().name());
			e.putBoolean("fake", entry.fake());
			e.putBoolean("forced", entry.forced());
			e.putString("stage", entry.stage().name());
			e.putBoolean("felt", entry.felt());
			list.add(e);
		}
		tag.put("history", list);

		tag.putLong("lowRenderAccum", lowRenderAccum);
		tag.putBoolean("lowRenderCounted", lowRenderCounted);
		return tag;
	}

	/**
	 * Reads one player's part, or the pacing fields at the top level of a v1 {@code director.dat} (whose subject had
	 * been there since day 0, so a missing {@code firstDayTicks} is 0).
	 */
	public static PacingMemory fromTag(CompoundTag tag) {
		PacingMemory m = new PacingMemory(tag.getLongOr("firstDayTicks", 0));
		m.lastStepPlay = tag.getLongOr("lastStepPlay", -1);
		m.nextMajorDue = tag.getLongOr("nextMajorDue", -1);

		CompoundTag cycles = tag.getCompoundOrEmpty("cycleFired");
		for (Tier tier : Tier.values()) {
			m.cycleFired.get(tier).addAll(DirectorMemory.split(cycles.getStringOr(tier.name(), "")));
		}
		DirectorMemory.readStrings(tag.getCompoundOrEmpty("lastFired"), m.lastFired);
		DirectorMemory.readStrings(tag.getCompoundOrEmpty("held"), m.held);
		DirectorMemory.readLongs(tag.getCompoundOrEmpty("heldSince"), m.heldSince, Tier::valueOf);
		DirectorMemory.readStrings(tag.getCompoundOrEmpty("lastGivenUp"), m.lastGivenUp);
		DirectorMemory.readLongs(tag.getCompoundOrEmpty("deckStuckSince"), m.deckStuckSince, Tier::valueOf);

		m.lastFireAny = tag.getLongOr("lastFireAny", DirectorMemory.NEVER);
		DirectorMemory.readLongs(tag.getCompoundOrEmpty("lastFireTier"), m.lastFireTier, Tier::valueOf);
		// A v1 file's world-level tag times were its subject's: the same clock.
		DirectorMemory.readLongs(tag.getCompoundOrEmpty("lastTag"), m.lastTag, CardTag::valueOf);
		m.lastMajorOrSignature = tag.getLongOr("lastMajorOrSignature", DirectorMemory.NEVER);
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
		m.feltFires = tag.getIntOr("feltFires", 0);
		ListTag list = tag.getListOrEmpty("history");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompoundOrEmpty(i);
			try {
				m.history.addLast(new DirectorMemory.HistoryEntry(e.getLongOr("play", 0), e.getLongOr("day", 0), e.getStringOr("card", "?"),
						Tier.valueOf(e.getStringOr("tier", "AMBIENT")), e.getBooleanOr("fake", false), e.getBooleanOr("forced", false),
						Stage.valueOf(e.getStringOr("stage", "ALONE")), e.getBooleanOr("felt", false)));
			} catch (IllegalArgumentException ignored) {
				// unknown enum name from an older version: drop the entry
			}
		}

		m.lowRenderAccum = tag.getLongOr("lowRenderAccum", 0);
		m.lowRenderCounted = tag.getBooleanOr("lowRenderCounted", false);
		return m;
	}
}
