package com.forzacode.a1016_02.lore.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.lore.sc.RecordConfig;
import com.forzacode.a1016_02.lore.sc.RecordRules;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelSummary;

import org.jspecify.annotations.Nullable;

/**
 * 14 (a) and (b) in the world list (D-081), with no server running: {@code lore.mixin.client.LevelSummaryMixin} asks here
 * for a world's last played time, version name and backup status. A world's folder is its summary icon's parent; its
 * {@link RecordRules#FILE} says which beats the server armed, its {@link RecordRules#SEEN_FILE} which ones a list already
 * showed. A beat shown here for the first time is written into the seen file at once ({@link SelfCreated#writeInWorld}),
 * and keeps showing for the rest of this run of the game (the list never changes under the player) until the world is
 * opened, when the server reads the seen file and the beat is done. Only vanilla's strings and the canon version; nothing
 * at all while the layer is off ({@link SelfCreated#enabled}, this client's own config). Never throws: anything that
 * cannot be read shows vanilla's own values. Any thread (the list loads its summaries in the background).
 */
public final class WorldListMarks {
	/** A world's files are read again after this long, milliseconds. */
	static final long READ_AGAIN_MS = 2000L;
	/** The debug's arm ({@code nogap}) picks its time as if the host had been away this long, milliseconds (a day). */
	static final long DEBUG_AWAY_MS = 24L * 3_600_000L;

	private record Marks(RecordRules.ServerFile file, Set<Long> seen, long readAt) {
	}

	private static final Map<Path, Marks> MARKS = new HashMap<>();
	/** (a) beats shown in this run: the nonce and the time it showed. */
	private static final Map<Long, Long> SHOWN_LAST = new HashMap<>();
	/** (b) beats shown in this run. */
	private static final Set<Long> SHOWN_VERSION = new HashSet<>();

	private WorldListMarks() {
	}

	/** The last played time the list shows: (a)'s fake time while its beat shows, else {@code real}. */
	public static synchronized long lastPlayed(LevelSummary summary, long real) {
		try {
			Path folder = folder(summary);
			if (folder == null) {
				return real;
			}
			Marks marks = marks(folder);
			Optional<RecordRules.Beat> beat = marks.file().last();
			if (beat.isEmpty()) {
				return real;
			}
			long nonce = beat.get().nonce();
			Long shown = SHOWN_LAST.get(nonce);
			RecordConfig config = RecordConfig.get();
			long now = Util.getEpochMillis();
			long stop = marks.file().stopMillis() > 0L ? marks.file().stopMillis() : real;
			if (!RecordRules.showsLast(true, beat, marks.seen(), shown != null, stop, now, config.gapMillis())) {
				return real;
			}
			if (shown == null) {
				long quit = Math.max(real, 0L);
				if (beat.get().nogap()) {
					quit = Math.min(quit, now - DEBUG_AWAY_MS); // the debug's arm: as if away a whole day, so last night shows at once
				}
				shown = RecordRules.fakeLastPlayed(nonce, quit, now, config.nightFromTime(), config.nightToTime(), ZoneId.systemDefault());
				SHOWN_LAST.put(nonce, shown);
				markSeen(folder, marks, nonce);
			}
			return shown;
		} catch (RuntimeException e) {
			A1016_02.LOGGER.debug("[a1016] sc_14: the world list keeps vanilla's time", e);
			return real;
		}
	}

	/** The version name the list (and vanilla's backup question) shows: the canon one while (b) shows or for good, else null. */
	public static synchronized @Nullable MutableComponent versionName(LevelSummary summary) {
		try {
			Path folder = folder(summary);
			if (folder == null) {
				return null;
			}
			Marks marks = marks(folder);
			if (marks.file().forever() || versionShows(folder, marks)) {
				return Component.literal(RecordRules.CANON_VERSION);
			}
			return null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * The backup status: vanilla's "Do you really want to load this world?" ({@code UPGRADE_TO_SNAPSHOT}) while (b) shows and
	 * vanilla has nothing to say itself; never for the version for good (no question then). Else {@code real}.
	 */
	public static synchronized LevelSummary.BackupStatus backupStatus(LevelSummary summary, LevelSummary.BackupStatus real) {
		if (real != LevelSummary.BackupStatus.NONE) {
			return real;
		}
		try {
			Path folder = folder(summary);
			if (folder == null) {
				return real;
			}
			Marks marks = marks(folder);
			return versionShows(folder, marks) ? LevelSummary.BackupStatus.UPGRADE_TO_SNAPSHOT : real;
		} catch (RuntimeException e) {
			return real;
		}
	}

	private static boolean versionShows(Path folder, Marks marks) {
		Optional<RecordRules.Beat> beat = marks.file().version();
		if (beat.isEmpty()) {
			return false;
		}
		long nonce = beat.get().nonce();
		boolean shown = SHOWN_VERSION.contains(nonce);
		if (!RecordRules.showsVersion(true, beat, marks.seen(), shown)) {
			return false;
		}
		if (!shown) {
			SHOWN_VERSION.add(nonce);
			markSeen(folder, marks, nonce);
		}
		return true;
	}

	/** The world's folder (the summary icon's parent), or null: the layer off, a locked or linked world, no icon path. */
	private static @Nullable Path folder(LevelSummary summary) {
		if (!SelfCreated.enabled() || summary.isLocked() || summary instanceof LevelSummary.SymlinkLevelSummary) {
			return null;
		}
		Path icon = summary.getIcon();
		return icon == null ? null : icon.getParent();
	}

	private static Marks marks(Path folder) {
		long now = Util.getMillis();
		Marks marks = MARKS.get(folder);
		if (marks != null && now - marks.readAt() < READ_AGAIN_MS) {
			return marks;
		}
		RecordRules.ServerFile file = read(folder, RecordRules.FILE).map(RecordRules.ServerFile::load).orElse(RecordRules.ServerFile.EMPTY);
		Set<Long> seen = read(folder, RecordRules.SEEN_FILE).map(RecordRules::loadSeen).orElse(Set.of());
		marks = new Marks(file, seen, now);
		MARKS.put(folder, marks);
		return marks;
	}

	private static Optional<net.minecraft.nbt.CompoundTag> read(Path folder, String relative) {
		Optional<Path> path = SelfCreated.inWorld(folder, relative);
		if (path.isEmpty() || !Files.isRegularFile(path.get())) {
			return Optional.empty();
		}
		try {
			return RecordRules.tag(Files.readAllBytes(path.get()));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/** The beat is shown: its nonce goes into the world's seen file now (whole or not at all). */
	private static void markSeen(Path folder, Marks marks, long nonce) {
		Set<Long> seen = new LinkedHashSet<>(marks.seen());
		if (seen.add(nonce)) {
			if (SelfCreated.writeInWorld(folder, RecordRules.SEEN_FILE, RecordRules.bytes(RecordRules.saveSeen(seen)))) {
				MARKS.put(folder, new Marks(marks.file(), Set.copyOf(seen), marks.readAt()));
			}
		}
	}
}
