package com.forzacode.a1016_02.director;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.jspecify.annotations.Nullable;

/**
 * Everything the director remembers (D-059): one {@link WorldMemory} shared by everyone, and one {@link PacingMemory}
 * per player who has taken part, keyed by UUID in the order they first did (D-062.2). {@link DirectorData} stores it.
 * <p>
 * Format 2 stores {@code format}, {@code world} and {@code players} (each with its {@code uuid}). A v1
 * {@code director.dat} (one memory for the single subject, every field at the top level) reads as a world part plus
 * one {@linkplain #unowned() unowned} pacing memory: {@link DirectorData#get} hands it to the first known player
 * ({@code HerobrineState.players()}, who is v1.0's subject), so the old timers keep running on that player's clock
 * (core gives them the old play ticks as their own). Until someone is known it stays unowned and is saved as such.
 */
public final class DirectorMemory {
	/** "Never happened": far enough in the past that subtracting it from any play time cannot overflow. */
	public static final long NEVER = Long.MIN_VALUE / 4;
	static final int FORMAT = 2;

	/**
	 * One fire, newest last in {@link PacingMemory#history()}.
	 *
	 * @param playTicks that player's own clock
	 * @param felt      someone else's card, felt here because they were close (D-060.3)
	 */
	public record HistoryEntry(long playTicks, long day, String cardId, Tier tier, boolean fake, boolean forced, Stage stage, boolean felt) {
	}

	private final WorldMemory world;
	private final Map<UUID, PacingMemory> players = new LinkedHashMap<>();
	/** A v1 subject's pacing whose owner is not known yet (see the class comment). */
	private @Nullable PacingMemory unowned;

	public DirectorMemory() {
		this(new WorldMemory());
	}

	/** A memory around this world part, with nobody's pacing yet (dry runs and tests). */
	DirectorMemory(WorldMemory world) {
		this.world = world;
	}

	/** Sets a player's pacing (dry runs and tests); the live director creates them through {@link #pacing}. */
	void put(UUID player, PacingMemory pacing) {
		players.put(player, pacing);
	}

	public WorldMemory world() {
		return world;
	}

	/** That player's pacing, created (their first in-game day starting at {@code dayTicksNow}) if they have none yet. */
	public PacingMemory pacing(UUID player, long dayTicksNow) {
		return players.computeIfAbsent(player, k -> new PacingMemory(dayTicksNow));
	}

	/** That player's pacing, or empty if they never had one. Never creates one (read-only callers). */
	public Optional<PacingMemory> find(UUID player) {
		return Optional.ofNullable(players.get(player));
	}

	/** Every player's pacing, in the order they first took part. Unmodifiable. */
	public Map<UUID, PacingMemory> players() {
		return Collections.unmodifiableMap(players);
	}

	/** A v1 subject's pacing still waiting for its owner, or null. */
	public @Nullable PacingMemory unowned() {
		return unowned;
	}

	/**
	 * Gives the unowned v1 pacing to this player (v1.0's subject: the first known player), unless they already have
	 * one of their own. Returns true if anything changed.
	 */
	public boolean adoptUnowned(UUID firstKnown) {
		if (unowned == null) {
			return false;
		}
		players.putIfAbsent(firstKnown, unowned);
		unowned = null;
		return true;
	}

	/** A deep copy (dry runs work on copies). */
	public DirectorMemory copy() {
		return fromTag(toTag());
	}

	// --- storage ---

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("format", FORMAT);
		tag.put("world", world.toTag());
		ListTag list = new ListTag();
		players.forEach((uuid, pacing) -> {
			CompoundTag entry = pacing.toTag();
			entry.putString("uuid", uuid.toString());
			list.add(entry);
		});
		tag.put("players", list);
		if (unowned != null) {
			tag.put("unowned", unowned.toTag());
		}
		return tag;
	}

	/** Reads format 2, or a v1 file (see the class comment). */
	public static DirectorMemory fromTag(CompoundTag tag) {
		if (tag.getIntOr("format", 1) < FORMAT) {
			DirectorMemory legacy = new DirectorMemory(WorldMemory.fromTag(tag));
			if (!tag.isEmpty()) {
				legacy.unowned = PacingMemory.fromTag(tag);
			}
			return legacy;
		}
		DirectorMemory m = new DirectorMemory(WorldMemory.fromTag(tag.getCompoundOrEmpty("world")));
		ListTag list = tag.getListOrEmpty("players");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			try {
				m.players.putIfAbsent(UUID.fromString(entry.getStringOr("uuid", "")), PacingMemory.fromTag(entry));
			} catch (IllegalArgumentException ignored) {
				// a broken uuid: that player starts over
			}
		}
		tag.getCompound("unowned").ifPresent(unowned -> m.unowned = PacingMemory.fromTag(unowned));
		return m;
	}

	// --- shared tag helpers ---

	static CompoundTag strings(Map<Tier, String> map) {
		CompoundTag tag = new CompoundTag();
		map.forEach((tier, value) -> {
			if (value != null) {
				tag.putString(tier.name(), value);
			}
		});
		return tag;
	}

	static <E extends Enum<E>> CompoundTag longs(Map<E, Long> map) {
		CompoundTag tag = new CompoundTag();
		map.forEach((key, value) -> tag.putLong(key.name(), value));
		return tag;
	}

	static void readStrings(CompoundTag tag, Map<Tier, String> into) {
		for (Tier tier : Tier.values()) {
			tag.getString(tier.name()).filter(s -> !s.isEmpty()).ifPresent(s -> into.put(tier, s));
		}
	}

	static <E extends Enum<E>> void readLongs(CompoundTag tag, Map<E, Long> into, Function<String, E> parse) {
		for (String key : tag.keySet()) {
			try {
				E e = parse.apply(key);
				tag.getLong(key).ifPresent(v -> into.put(e, v));
			} catch (IllegalArgumentException ignored) {
				// unknown enum name from an older version
			}
		}
	}

	static List<String> split(String joined) {
		List<String> out = new ArrayList<>();
		for (String part : joined.split(",")) {
			if (!part.isBlank()) {
				out.add(part.trim());
			}
		}
		return out;
	}
}
