package com.forzacode.a1016_02.lore.sc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 14's pure rules (D-081), free of client classes so the game tests reach them: the two files the server and the world list
 * share, the fake last played time, when the world list shows each beat, (b)'s arm at a stop, and (d)'s credit. The game's own
 * records lie once each, with vanilla's strings and the canon numbers only.
 */
public final class RecordRules {
	/** The canon version (DESIGN guardrail 12, exactly). */
	public static final String CANON_VERSION = "a1.0.16_02";
	/** The canon seed (DESIGN guardrail 12, exactly). */
	public static final String CANON_SEED = "478868574082066804";
	/** The server's file, relative to the world's folder: the arms, the forever flag and the stop. */
	public static final String FILE = "data/a1016_02/sc14.dat";
	/** The world list's file, relative to the world's folder: the beats it showed. */
	public static final String SEEN_FILE = "data/a1016_02/sc14_seen.dat";
	/** The fake time stays at least this far inside the gap, milliseconds. */
	static final long MARGIN_MS = 60_000L;
	/** The latest nights looked at for the fake time. */
	static final int MAX_NIGHTS = 400;
	/** In-game night for (b)'s stop: from... */
	public static final long NIGHT_FROM = 13000L;
	/** ...to (exclusive). */
	public static final long NIGHT_UNTIL = 23000L;
	/** The seen file keeps at most this many beats (the newest). */
	static final int MAX_SEEN = 64;

	private RecordRules() {
	}

	/** One beat armed for the world list: its nonce, and whether a debug arm skipped the gap and the night. */
	public record Beat(long nonce, boolean nogap) {
		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("nonce", nonce);
			if (nogap) {
				tag.putBoolean("nogap", true);
			}
			return tag;
		}

		static Optional<Beat> load(CompoundTag parent, String key) {
			return parent.getCompound(key).flatMap(tag -> tag.getLong("nonce").map(nonce -> new Beat(nonce, tag.getBooleanOr("nogap", false))));
		}
	}

	/**
	 * {@link #FILE}: (a) the last played beat and (b) the version beat, if armed; the version for good (a finished hardcore
	 * line); the stop's real time (epoch milliseconds, 0 if none yet) and in-game time of day.
	 */
	public record ServerFile(Optional<Beat> last, Optional<Beat> version, boolean forever, long stopMillis, long stopTimeOfDay) {
		public static final ServerFile EMPTY = new ServerFile(Optional.empty(), Optional.empty(), false, 0L, 0L);

		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("v", 1);
			last.ifPresent(b -> tag.put("a", b.save()));
			version.ifPresent(b -> tag.put("b", b.save()));
			if (forever) {
				tag.putBoolean("f", true);
			}
			tag.putLong("stopMs", stopMillis);
			tag.putLong("stopTime", stopTimeOfDay);
			return tag;
		}

		/** Leniently: anything missing or unreadable reads as none. */
		public static ServerFile load(CompoundTag tag) {
			return new ServerFile(Beat.load(tag, "a"), Beat.load(tag, "b"), tag.getBooleanOr("f", false), tag.getLongOr("stopMs", 0L),
					tag.getLongOr("stopTime", 0L));
		}

		public ServerFile withStop(long millis, long timeOfDay) {
			return new ServerFile(last, version, forever, millis, timeOfDay);
		}
	}

	/** {@link #SEEN_FILE}: the nonces of the beats the world list showed, oldest first. */
	public static CompoundTag saveSeen(Set<Long> seen) {
		List<Long> list = new ArrayList<>(seen);
		long[] values = list.subList(Math.max(0, list.size() - MAX_SEEN), list.size()).stream().mapToLong(Long::longValue).toArray();
		CompoundTag tag = new CompoundTag();
		tag.putLongArray("seen", values);
		return tag;
	}

	/** Leniently: none if missing. */
	public static Set<Long> loadSeen(CompoundTag tag) {
		Set<Long> seen = new LinkedHashSet<>();
		tag.getLongArray("seen").ifPresent(values -> {
			for (long value : values) {
				seen.add(value);
			}
		});
		return seen;
	}

	/** A tag as the bytes of a compressed NBT file. */
	public static byte[] bytes(CompoundTag tag) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(tag, out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("could not write a tag", e);
		}
	}

	/** The tag in a compressed NBT file's bytes, or empty if they cannot be read (never throws). */
	public static Optional<CompoundTag> tag(byte @Nullable [] bytes) {
		if (bytes == null || bytes.length == 0) {
			return Optional.empty();
		}
		try {
			return Optional.of(NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(1L << 20)));
		} catch (IOException | RuntimeException e) {
			return Optional.empty();
		}
	}

	/**
	 * (a)'s fake last played time, a pure function of the nonce, the real last played ({@code quitMillis}) and now: a time
	 * inside the gap, in the latest local night window ({@code from} to {@code to}, past midnight when {@code from} is later
	 * than {@code to}) the gap holds, at the place the nonce picks; else the middle of the gap. Never outside the gap (at
	 * least {@link #MARGIN_MS} inside it when it is long enough); the real time when there is no gap.
	 */
	public static long fakeLastPlayed(long nonce, long quitMillis, long nowMillis, LocalTime from, LocalTime to, ZoneId zone) {
		if (nowMillis <= quitMillis) {
			return quitMillis;
		}
		long lo = quitMillis + MARGIN_MS;
		long hi = nowMillis - MARGIN_MS;
		if (lo < hi) {
			Optional<long[]> night = latestNight(lo, hi, from, to, zone);
			if (night.isPresent()) {
				long start = night.get()[0];
				long end = night.get()[1];
				double share = (mix(nonce) >>> 11) * 0x1.0p-53;
				return Math.clamp(start + (long) Math.floor(share * (end - start)), start, end);
			}
		}
		return quitMillis + (nowMillis - quitMillis) / 2;
	}

	/** The latest local night window ({@code from} to {@code to}) that overlaps {@code lo} to {@code hi}, cut to it: {start, end}. */
	static Optional<long[]> latestNight(long lo, long hi, LocalTime from, LocalTime to, ZoneId zone) {
		LocalDate last = Instant.ofEpochMilli(hi).atZone(zone).toLocalDate();
		LocalDate first = Instant.ofEpochMilli(lo).atZone(zone).toLocalDate().minusDays(1);
		LocalDate date = last;
		for (int i = 0; i < MAX_NIGHTS && !date.isBefore(first); i++, date = date.minusDays(1)) {
			long start = ZonedDateTime.of(date, from, zone).toInstant().toEpochMilli();
			LocalDate endDate = from.isAfter(to) ? date.plusDays(1) : date;
			long end = ZonedDateTime.of(endDate, to, zone).toInstant().toEpochMilli();
			long s = Math.max(start, lo);
			long e = Math.min(end, hi);
			if (e > s) {
				return Optional.of(new long[] {s, e});
			}
		}
		return Optional.empty();
	}

	/** A 64-bit mix of the nonce (SplitMix64's finalizer): the same nonce, the same place. */
	static long mix(long nonce) {
		long z = nonce + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/**
	 * Whether the world list shows (a) now: the layer on, the beat armed, not shown before (or shown in this run of the game,
	 * {@code shownThisRun}, so the list does not change under the player until the world is opened), and at least
	 * {@code gapMillis} since the stop ({@code nogap} skips it).
	 */
	public static boolean showsLast(boolean enabled, Optional<Beat> beat, Set<Long> seen, boolean shownThisRun, long stopMillis, long nowMillis,
			long gapMillis) {
		if (!enabled || beat.isEmpty()) {
			return false;
		}
		if (shownThisRun) {
			return true;
		}
		if (seen.contains(beat.get().nonce())) {
			return false;
		}
		return beat.get().nogap() || stopMillis > 0L && nowMillis - stopMillis >= gapMillis;
	}

	/** Whether the world list shows (b)'s version beat now (with vanilla's backup question): the layer on, armed, not shown before. */
	public static boolean showsVersion(boolean enabled, Optional<Beat> beat, Set<Long> seen, boolean shownThisRun) {
		return enabled && beat.isPresent() && (shownThisRun || !seen.contains(beat.get().nonce()));
	}

	/**
	 * (b)'s arm at a stop: the layer and the story on, not done or armed, (a) shown at least {@code afterDays} in-game days ago
	 * ({@code shownDay}, -1 if never), from {@code fromStage}, and the host quit at night (13000 to 23000).
	 */
	public static boolean versionArms(boolean storyOn, boolean done, boolean armed, long shownDay, long today, double afterDays, int stage,
			int fromStage, long stopTimeOfDay) {
		if (!storyOn || done || armed || shownDay < 0L || today - shownDay < afterDays || stage < fromStage) {
			return false;
		}
		long t = Math.floorMod(stopTimeOfDay, 24000L);
		return t >= NIGHT_FROM && t < NIGHT_UNTIL;
	}

	/**
	 * (d): whether this ledgered edit counts in anyone's statistics, by its cause: never what others left
	 * ({@link TraceLedger#isLeftByOthers}), the figure's own dig (entity's only ledgered edits, {@code entity:}), Ending D's
	 * ({@code ending:}) or a give-back ({@link TraceService#GIVE_BACK_CAUSE}); nor an undo's own edits and their dependents
	 * ({@link TraceService#UNDO_CAUSE}, the switch's give-backs) or 08's turning back ({@link #TURNED_BACK}). t0rch's shaft
	 * ({@code world:left/t0rch/shaft}) is what others left.
	 */
	public static boolean causeCounts(String cause) {
		return cause != null && !TraceLedger.isLeftByOthers(cause) && !cause.startsWith("entity:") && !cause.startsWith("ending:")
				&& !cause.startsWith(TraceService.GIVE_BACK_CAUSE) && !cause.startsWith(TraceService.UNDO_CAUSE) && !cause.startsWith(TURNED_BACK);
	}

	/** 08's own turning back when the layer is switched off (world's {@code WorldSc.turnBack}), never his trace. */
	public static final String TURNED_BACK = "world:sc08/back";

	/** One participant (d) may credit: who, where, and their base (in the same dimension), or null. */
	public record Candidate(UUID player, Vec3 at, @Nullable Vec3 base) {
	}

	/**
	 * (d): whose statistics: the participant whose base is nearest {@code pos} within {@code radius} (horizontal), else the
	 * participant nearest it within {@code radius}; empty if neither.
	 */
	public static Optional<UUID> credit(List<Candidate> candidates, Vec3 pos, double radius) {
		UUID best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Candidate c : candidates) {
			if (c.base() != null) {
				double d = horizontal(c.base(), pos);
				if (d <= radius && d < bestDistance) {
					best = c.player();
					bestDistance = d;
				}
			}
		}
		if (best != null) {
			return Optional.of(best);
		}
		for (Candidate c : candidates) {
			double d = horizontal(c.at(), pos);
			if (d <= radius && d < bestDistance) {
				best = c.player();
				bestDistance = d;
			}
		}
		return Optional.ofNullable(best);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
