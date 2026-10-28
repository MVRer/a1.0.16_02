package com.forzacode.a1016_02.world.sc2.s17.books;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * The world's day log (docs/SELF_CREATED_2_R3.md, "What the mod knows"): per in-game day (the books' days, 1 the first)
 * the weather, the moon's phase, a fog night, who was online and out at night (indices into the known players, the first
 * {@value #PLAYERS}), deaths (vanilla and marked, the cause's word, at night), what he took (a count and the most taken
 * word, and for whom), the places entered (kinds) and doors and chests used at another player's home, and the stage at
 * the day's end: {@value #BYTES} bytes a day (at most 48), the last {@value #DAYS} days. Beside the days: the words they
 * index, each player's first day in each kind of place, and a small statistics snapshot per player (kept at logout, read
 * for players who are offline). Its own saved data ({@code a1016_02:s2d}), read leniently: a broken entry is dropped, a
 * short day is padded. A day before the log existed reads as not kept ({@link #day} null). Server thread only;
 * {@link LogEvents} feeds it.
 */
public final class DayLog extends SavedData {
	public static final Codec<DayLog> CODEC = CompoundTag.CODEC.xmap(DayLog::load, DayLog::save);
	public static final SavedDataType<DayLog> TYPE = new SavedDataType<>(A1016_02.id("s2d"), DayLog::new, CODEC, null);

	/** Bytes a day. */
	public static final int BYTES = 44;
	/** Days kept: older ones are dropped as new ones come. */
	public static final int DAYS = 1024;
	/** Players a day can name (the online and night masks' bits). */
	public static final int PLAYERS = 32;
	/** Words the days can index. */
	public static final int WORDS = 255;
	/** Players with a statistics snapshot or place days. */
	public static final int SNAPSHOTS = 64;
	/** Deaths, takers and visits a day keeps. */
	public static final int DEATHS = 4;
	public static final int TAKERS = 3;
	public static final int VISITS = 2;
	/** Place kinds 16 to 20. */
	public static final int KINDS = 5;

	/** Weather by rank: the worst seen that day. */
	public static final int CLEAR = 0;
	public static final int RAIN = 1;
	public static final int SNOW = 2;
	public static final int THUNDER = 3;

	/** A death: whose (an index), the cause's word, marked (Ending B's list) or vanilla, at night. */
	public record Death(int player, String cause, boolean marked, boolean night) {
	}

	/** What he took that day near a player: whose, how many, the most taken block's or item's word. */
	public record Took(int player, int count, String word, boolean item) {
	}

	/** A door or chest used at another's home: who used it, whose home, the block's word. */
	public record Visit(int actor, int owner, String word) {
	}

	/** A player's statistics as last seen (vanilla's counters). */
	public record Snapshot(String mined, int minedCount, int walked, int jumps, int kills, int slept, int deaths, int sinceDeath) {
		public static final Snapshot NONE = new Snapshot("", 0, 0, 0, 0, 0, 0, 0);
	}

	/** One day as kept. */
	public record Day(int day, int weather, int moon, boolean moonSeen, boolean fog, boolean closed, int stage, int online, int nightOut,
			List<Death> deaths, int traces, @Nullable String traceWord, boolean traceItem, int places, List<Took> took, List<Visit> visits) {
		/** True if that player (an index) was online. */
		public boolean online(int player) {
			return player >= 0 && player < PLAYERS && (online & (1 << player)) != 0;
		}

		/** True if that player (an index) was out at night. */
		public boolean out(int player) {
			return player >= 0 && player < PLAYERS && (nightOut & (1 << player)) != 0;
		}

		/** The indices of everyone online, lowest first. */
		public List<Integer> who() {
			List<Integer> out = new ArrayList<>();
			for (int i = 0; i < PLAYERS; i++) {
				if (online(i)) {
					out.add(i);
				}
			}
			return out;
		}

		/** True if that place kind (16 to 20) was entered. */
		public boolean entered(int kind) {
			int bit = kind - 16;
			return bit >= 0 && bit < KINDS && (places & (1 << bit)) != 0;
		}
	}

	private final TreeMap<Integer, byte[]> days = new TreeMap<>();
	private final List<String> words = new ArrayList<>();
	private final Map<UUID, Snapshot> snapshots = new LinkedHashMap<>();
	private final Map<UUID, int[]> placeDays = new LinkedHashMap<>();
	private int current;
	// this session only
	private final Map<Integer, Long> lastDeath = new HashMap<>();
	private final Map<String, Integer> takenWords = new HashMap<>();
	final Map<UUID, Long> scanned = new HashMap<>();

	public DayLog() {
	}

	public static DayLog get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// --- reading ---

	/** The open day (the books' day number), 0 before the log's first day. */
	public int current() {
		return current;
	}

	/** The first day kept, 0 for none. */
	public int first() {
		return days.isEmpty() ? 0 : days.firstKey();
	}

	/** Days kept. */
	public int size() {
		return days.size();
	}

	/** That day as kept, or null if it was not (before the log, dropped, or a day skipped). */
	public @Nullable Day day(int day) {
		byte[] b = days.get(day);
		return b == null ? null : view(day, b);
	}

	/** Every day kept, oldest first. */
	public List<Day> all() {
		List<Day> out = new ArrayList<>(days.size());
		days.forEach((d, b) -> out.add(view(d, b)));
		return out;
	}

	/** That player's statistics as last kept. */
	public Snapshot snapshot(UUID player) {
		return snapshots.getOrDefault(player, Snapshot.NONE);
	}

	/** The day that player first entered each kind of place (16 to 20; 0 for never). */
	public int placeDay(UUID player, int kind) {
		int[] firsts = placeDays.get(player);
		int i = kind - 16;
		return firsts == null || i < 0 || i >= KINDS ? 0 : firsts[i];
	}

	// --- writing (the open day) ---

	/**
	 * Opens {@code day}: closes the open one with {@code stage} (the world's stage at its end) and keeps a new entry, the
	 * oldest dropped past {@link #DAYS}. A day already kept (the server restarted that day) is reopened as it was.
	 */
	public void open(int day, int stage) {
		if (day <= 0 || day == current) {
			return;
		}
		close(stage);
		current = day;
		days.computeIfAbsent(day, d -> {
			byte[] b = new byte[BYTES];
			putInt(b, 0, d);
			return b;
		});
		while (days.size() > DAYS) {
			days.pollFirstEntry();
		}
		takenWords.clear();
		setDirty();
	}

	/** Marks the open day ended, with the world's stage then. */
	public void close(int stage) {
		byte[] b = open();
		if (b != null) {
			b[6] |= 2;
			b[7] = (byte) Math.clamp(stage + 1, 0, 255);
			setDirty();
		}
	}

	public void weather(int rank) {
		byte[] b = open();
		if (b != null && rank > b[4]) {
			b[4] = (byte) Math.clamp(rank, CLEAR, THUNDER);
			setDirty();
		}
	}

	public void moon(int phase) {
		byte[] b = open();
		if (b != null && (b[5] & 0x80) == 0) {
			b[5] = (byte) (0x80 | (phase & 7));
			setDirty();
		}
	}

	public void fog() {
		byte[] b = open();
		if (b != null && (b[6] & 1) == 0) {
			b[6] |= 1;
			setDirty();
		}
	}

	public void online(int player) {
		bit(8, player);
	}

	public void nightOut(int player) {
		bit(12, player);
	}

	/**
	 * A death today. A death of the same player within 40 ticks of the last is the same death seen twice (the vanilla
	 * event and the mark): it becomes marked, its cause the marked one.
	 */
	public void death(int player, String cause, boolean marked, boolean night, long tick) {
		byte[] b = open();
		if (b == null || player < 0 || player >= PLAYERS) {
			return;
		}
		Long last = lastDeath.get(player);
		lastDeath.put(player, tick);
		int word = word(cause);
		if (last != null && Math.abs(tick - last) <= 40) {
			for (int i = DEATHS - 1; i >= 0; i--) {
				int at = 16 + i * 2;
				if ((b[at] & 0x80) != 0 && (b[at] & 0x1F) == player) {
					if (marked) {
						b[at] |= 0x40;
						b[at + 1] = (byte) word;
					}
					setDirty();
					return;
				}
			}
		}
		for (int i = 0; i < DEATHS; i++) {
			int at = 16 + i * 2;
			if ((b[at] & 0x80) == 0) {
				b[at] = (byte) (0x80 | (marked ? 0x40 : 0) | (night ? 0x20 : 0) | player);
				b[at + 1] = (byte) word;
				setDirty();
				return;
			}
		}
	}

	/** Something of a player's he took today (a block's or a stack's word), near {@code player} (-1 for nobody). */
	public void took(int player, String word, boolean item) {
		byte[] b = open();
		if (b == null) {
			return;
		}
		int total = Math.min(0xFFFF, (getShort(b, 24) + 1));
		b[24] = (byte) (total >> 8);
		b[25] = (byte) total;
		int seen = takenWords.merge(word, 1, Integer::sum);
		int top = b[26] & 0xFF;
		if (top == 0 || (top <= words.size() && !words.get(top - 1).equals(word) && seen > takenWords.getOrDefault(words.get(top - 1), 0))) {
			b[26] = (byte) word(word);
			b[6] = (byte) (item ? b[6] | 4 : b[6] & ~4);
		}
		if (player >= 0 && player < PLAYERS) {
			for (int i = 0; i < TAKERS; i++) {
				int at = 28 + i * 3;
				if ((b[at] & 0x80) == 0) {
					b[at] = (byte) (0x80 | (item ? 0x40 : 0) | player);
					b[at + 1] = 1;
					b[at + 2] = (byte) word(word);
					break;
				}
				if ((b[at] & 0x1F) == player) {
					b[at + 1] = (byte) Math.min(255, (b[at + 1] & 0xFF) + 1);
					break;
				}
			}
		}
		setDirty();
	}

	/** A player entered a place of that kind (16 to 20) today: the day's mask, and their first day in that kind. */
	public void entered(int player, UUID uuid, int kind) {
		byte[] b = open();
		int bit = kind - 16;
		if (b == null || bit < 0 || bit >= KINDS) {
			return;
		}
		b[27] |= (byte) (1 << bit);
		if (placeDays.containsKey(uuid) || placeDays.size() < SNAPSHOTS) {
			int[] firsts = placeDays.computeIfAbsent(uuid, u -> new int[KINDS]);
			if (firsts[bit] == 0) {
				firsts[bit] = current;
			}
		}
		setDirty();
	}

	/** A door or chest ({@code word}) {@code actor} used at {@code owner}'s home today; once a day a pair. */
	public void visit(int actor, int owner, String word) {
		byte[] b = open();
		if (b == null || actor < 0 || actor >= PLAYERS || owner < 0 || owner >= PLAYERS || actor == owner) {
			return;
		}
		for (int i = 0; i < VISITS; i++) {
			int at = 37 + i * 3;
			if ((b[at] & 0x80) == 0) {
				b[at] = (byte) (0x80 | actor);
				b[at + 1] = (byte) owner;
				b[at + 2] = (byte) word(word);
				setDirty();
				return;
			}
			if ((b[at] & 0x1F) == actor && b[at + 1] == owner) {
				return;
			}
		}
	}

	/** Keeps a player's statistics (at logout and once a day); at most {@link #SNAPSHOTS} players. */
	public void snapshot(UUID player, Snapshot snapshot) {
		if (snapshots.containsKey(player) || snapshots.size() < SNAPSHOTS) {
			snapshots.put(player, snapshot);
			setDirty();
		}
	}

	private void bit(int offset, int player) {
		byte[] b = open();
		if (b == null || player < 0 || player >= PLAYERS) {
			return;
		}
		int at = offset + player / 8;
		byte mask = (byte) (1 << (player % 8));
		if ((b[at] & mask) == 0) {
			b[at] |= mask;
			setDirty();
		}
	}

	private byte @Nullable [] open() {
		return current == 0 ? null : days.get(current);
	}

	/** The word's index + 1 (0 when the table is full and it is new). */
	private int word(String word) {
		int i = words.indexOf(word);
		if (i >= 0) {
			return i + 1;
		}
		if (words.size() >= WORDS) {
			return 0;
		}
		words.add(word);
		return words.size();
	}

	private @Nullable String wordAt(int index) {
		return index <= 0 || index > words.size() ? null : words.get(index - 1);
	}

	private Day view(int day, byte[] b) {
		List<Death> deaths = new ArrayList<>();
		for (int i = 0; i < DEATHS; i++) {
			int at = 16 + i * 2;
			String cause = wordAt(b[at + 1] & 0xFF);
			if ((b[at] & 0x80) != 0) {
				deaths.add(new Death(b[at] & 0x1F, cause == null ? "generic" : cause, (b[at] & 0x40) != 0, (b[at] & 0x20) != 0));
			}
		}
		List<Took> took = new ArrayList<>();
		for (int i = 0; i < TAKERS; i++) {
			int at = 28 + i * 3;
			String word = wordAt(b[at + 2] & 0xFF);
			if ((b[at] & 0x80) != 0 && word != null) {
				took.add(new Took(b[at] & 0x1F, b[at + 1] & 0xFF, word, (b[at] & 0x40) != 0));
			}
		}
		List<Visit> visits = new ArrayList<>();
		for (int i = 0; i < VISITS; i++) {
			int at = 37 + i * 3;
			String word = wordAt(b[at + 2] & 0xFF);
			if ((b[at] & 0x80) != 0 && word != null) {
				visits.add(new Visit(b[at] & 0x1F, b[at + 1] & 0x1F, word));
			}
		}
		return new Day(day, Math.clamp(b[4], CLEAR, THUNDER), b[5] & 7, (b[5] & 0x80) != 0, (b[6] & 1) != 0, (b[6] & 2) != 0,
				(b[7] & 0xFF) - 1, getInt(b, 8), getInt(b, 12), List.copyOf(deaths), getShort(b, 24), wordAt(b[26] & 0xFF), (b[6] & 4) != 0,
				b[27] & 0xFF, List.copyOf(took), List.copyOf(visits));
	}

	// little-endian bit masks, big-endian numbers; both only read back here
	private static int getInt(byte[] b, int at) {
		return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | (b[at + 3] & 0xFF) << 24;
	}

	private static void putInt(byte[] b, int at, int v) {
		b[at] = (byte) v;
		b[at + 1] = (byte) (v >> 8);
		b[at + 2] = (byte) (v >> 16);
		b[at + 3] = (byte) (v >> 24);
	}

	private static int getShort(byte[] b, int at) {
		return (b[at] & 0xFF) << 8 | (b[at + 1] & 0xFF);
	}

	// --- saved ---

	/** The saved form (the tests read it back). */
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("v", 1);
		tag.putInt("c", current);
		ListTag d = new ListTag();
		days.values().forEach(b -> d.add(new ByteArrayTag(b.clone())));
		tag.put("d", d);
		ListTag w = new ListTag();
		words.forEach(word -> w.add(StringTag.valueOf(word)));
		tag.put("w", w);
		ListTag s = new ListTag();
		snapshots.forEach((uuid, snap) -> {
			CompoundTag one = new CompoundTag();
			one.putString("u", uuid.toString());
			one.putString("m", snap.mined());
			one.putIntArray("n", new int[] {snap.minedCount(), snap.walked(), snap.jumps(), snap.kills(), snap.slept(), snap.deaths(), snap.sinceDeath()});
			s.add(one);
		});
		tag.put("s", s);
		ListTag p = new ListTag();
		placeDays.forEach((uuid, firsts) -> {
			CompoundTag one = new CompoundTag();
			one.putString("u", uuid.toString());
			one.putIntArray("k", firsts.clone());
			p.add(one);
		});
		tag.put("p", p);
		return tag;
	}

	/** A log from its saved form, leniently: what cannot be read is dropped. */
	public static DayLog load(CompoundTag tag) {
		DayLog log = new DayLog();
		ListTag w = tag.getListOrEmpty("w");
		for (int i = 0; i < w.size() && i < WORDS; i++) {
			log.words.add(w.getStringOr(i, ""));
		}
		ListTag d = tag.getListOrEmpty("d");
		for (int i = 0; i < d.size(); i++) {
			Tag one = d.get(i);
			if (one instanceof ByteArrayTag bytes && bytes.getAsByteArray().length >= 4) {
				byte[] b = new byte[BYTES];
				byte[] raw = bytes.getAsByteArray();
				System.arraycopy(raw, 0, b, 0, Math.min(raw.length, BYTES));
				int day = getInt(b, 0);
				if (day > 0) {
					log.days.put(day, b);
				}
			}
		}
		while (log.days.size() > DAYS) {
			log.days.pollFirstEntry();
		}
		int current = tag.getIntOr("c", 0);
		log.current = log.days.containsKey(current) ? current : 0;
		ListTag s = tag.getListOrEmpty("s");
		for (int i = 0; i < s.size() && log.snapshots.size() < SNAPSHOTS; i++) {
			CompoundTag one = s.getCompoundOrEmpty(i);
			UUID uuid = uuid(one.getStringOr("u", ""));
			int[] n = one.getIntArray("n").orElse(new int[0]);
			if (uuid != null && n.length >= 7) {
				log.snapshots.put(uuid, new Snapshot(one.getStringOr("m", ""), n[0], n[1], n[2], n[3], n[4], n[5], n[6]));
			}
		}
		ListTag p = tag.getListOrEmpty("p");
		for (int i = 0; i < p.size() && log.placeDays.size() < SNAPSHOTS; i++) {
			CompoundTag one = p.getCompoundOrEmpty(i);
			UUID uuid = uuid(one.getStringOr("u", ""));
			int[] k = one.getIntArray("k").orElse(new int[0]);
			if (uuid != null) {
				int[] firsts = new int[KINDS];
				System.arraycopy(k, 0, firsts, 0, Math.min(k.length, KINDS));
				log.placeDays.put(uuid, firsts);
			}
		}
		return log;
	}

	/** A copy through the save and load (the tests' round trip). */
	public DayLog reloaded() {
		return load(save());
	}

	private static @Nullable UUID uuid(String text) {
		try {
			return text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException broken) {
			return null;
		}
	}
}
