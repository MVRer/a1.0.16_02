package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CoreCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * The ending workstream's saved data ({@code data/a1016_02/ending.dat}): the path ({@link EndingPath}), each path's
 * progress (its beat), what the player did that the commit rules read, and each beat's own memory. Times are
 * {@code GameClock.dayTicks} ("-1" = never). Server thread only; change it through its methods, which mark it dirty.
 * Other workstreams read and change the path through {@link EndingApi}.
 */
public final class EndingState extends SavedData {
	/** "Never" for every time field. */
	public static final long NEVER = -1L;
	static final int LOG_MAX = 24;

	/** One mob waiting in the doorway: which mob, the spot it was moved to, the door it faces, and since when. */
	public record Waiter(UUID mob, BlockPos spot, BlockPos door, long since) {
		static final Codec<Waiter> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("mob").forGetter(Waiter::mob),
				BlockPos.CODEC.fieldOf("spot").forGetter(Waiter::spot),
				BlockPos.CODEC.fieldOf("door").forGetter(Waiter::door),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Waiter::since)
		).apply(i, Waiter::new));
	}

	record Run(EndingPath path, long pathSince, String reason, Map<String, Integer> progress, long beatSince, boolean ended, long endedAt,
			int deathsSeen) {
		static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
				CoreCodecs.enumCodec(EndingPath.class).optionalFieldOf("path", EndingPath.NONE).forGetter(Run::path),
				Codec.LONG.optionalFieldOf("pathSince", NEVER).forGetter(Run::pathSince),
				Codec.STRING.optionalFieldOf("reason", "").forGetter(Run::reason),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("progress", Map.of()).forGetter(Run::progress),
				Codec.LONG.optionalFieldOf("beatSince", NEVER).forGetter(Run::beatSince),
				Codec.BOOL.optionalFieldOf("ended", false).forGetter(Run::ended),
				Codec.LONG.optionalFieldOf("endedAt", NEVER).forGetter(Run::endedAt),
				Codec.INT.optionalFieldOf("deathsSeen", 0).forGetter(Run::deathsSeen)
		).apply(i, Run::new));
	}

	record Watch(long stopSeenAt, long lastTellingAt, long lastNamedAt, long lastReadAt, long lastFogStareAt, int tellingsSinceStop,
			int fragmentsBurned, int ownBroken, int housePeak, Optional<GlobalPos> home) {
		static final Codec<Watch> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("stopSeenAt", NEVER).forGetter(Watch::stopSeenAt),
				Codec.LONG.optionalFieldOf("lastTellingAt", NEVER).forGetter(Watch::lastTellingAt),
				Codec.LONG.optionalFieldOf("lastNamedAt", NEVER).forGetter(Watch::lastNamedAt),
				Codec.LONG.optionalFieldOf("lastReadAt", NEVER).forGetter(Watch::lastReadAt),
				Codec.LONG.optionalFieldOf("lastFogStareAt", NEVER).forGetter(Watch::lastFogStareAt),
				Codec.INT.optionalFieldOf("tellingsSinceStop", 0).forGetter(Watch::tellingsSinceStop),
				Codec.INT.optionalFieldOf("fragmentsBurned", 0).forGetter(Watch::fragmentsBurned),
				Codec.INT.optionalFieldOf("ownBroken", 0).forGetter(Watch::ownBroken),
				Codec.INT.optionalFieldOf("housePeak", 0).forGetter(Watch::housePeak),
				GlobalPos.CODEC.optionalFieldOf("home").forGetter(Watch::home)
		).apply(i, Watch::new));
	}

	record Beats(long silenceUntilDay, int sightingTries, long lastSightingAt, long lastArmAt, Optional<GlobalPos> deathPos, long deathAt,
			Optional<GlobalPos> house, boolean copyRequested, boolean finalArmed, long lastHomeArmAt, boolean f20Placed, List<Waiter> waiters) {
		static final Codec<Beats> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("silenceUntilDay", 0L).forGetter(Beats::silenceUntilDay),
				Codec.INT.optionalFieldOf("sightingTries", 0).forGetter(Beats::sightingTries),
				Codec.LONG.optionalFieldOf("lastSightingAt", NEVER).forGetter(Beats::lastSightingAt),
				Codec.LONG.optionalFieldOf("lastArmAt", NEVER).forGetter(Beats::lastArmAt),
				GlobalPos.CODEC.optionalFieldOf("deathPos").forGetter(Beats::deathPos),
				Codec.LONG.optionalFieldOf("deathAt", NEVER).forGetter(Beats::deathAt),
				GlobalPos.CODEC.optionalFieldOf("house").forGetter(Beats::house),
				Codec.BOOL.optionalFieldOf("copyRequested", false).forGetter(Beats::copyRequested),
				Codec.BOOL.optionalFieldOf("finalArmed", false).forGetter(Beats::finalArmed),
				Codec.LONG.optionalFieldOf("lastHomeArmAt", NEVER).forGetter(Beats::lastHomeArmAt),
				Codec.BOOL.optionalFieldOf("f20Placed", false).forGetter(Beats::f20Placed),
				Waiter.CODEC.listOf().optionalFieldOf("waiters", List.of()).forGetter(Beats::waiters)
		).apply(i, Beats::new));
	}

	public static final Codec<EndingState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Run.CODEC.optionalFieldOf("run").forGetter(s -> Optional.of(s.run())),
			Watch.CODEC.optionalFieldOf("watch").forGetter(s -> Optional.of(s.watch())),
			Beats.CODEC.optionalFieldOf("beats").forGetter(s -> Optional.of(s.beats())),
			Codec.STRING.listOf().optionalFieldOf("log", List.of()).forGetter(s -> s.log)
	).apply(i, EndingState::new));

	public static final SavedDataType<EndingState> TYPE = new SavedDataType<>(A1016_02.id("ending"), EndingState::new, CODEC, null);

	// --- the run ---
	private EndingPath path = EndingPath.NONE;
	private long pathSince = NEVER;
	private String reason = "";
	private final Map<EndingPath, Integer> progress = new EnumMap<>(EndingPath.class);
	private long beatSince = NEVER;
	private boolean ended;
	private long endedAt = NEVER;
	private int deathsSeen;

	// --- what the player did ---
	private long stopSeenAt = NEVER;
	private long lastTellingAt = NEVER;
	private long lastNamedAt = NEVER;
	private long lastReadAt = NEVER;
	private long lastFogStareAt = NEVER;
	private int tellingsSinceStop;
	private int fragmentsBurned;
	private int ownBroken;
	private int housePeak;
	private @Nullable GlobalPos home;

	// --- the beats' own memory ---
	private long silenceUntilDay;
	private int sightingTries;
	private long lastSightingAt = NEVER;
	private long lastArmAt = NEVER;
	private @Nullable GlobalPos deathPos;
	private long deathAt = NEVER;
	private @Nullable GlobalPos house;
	private boolean copyRequested;
	private boolean finalArmed;
	private long lastHomeArmAt = NEVER;
	private boolean f20Placed;
	private final List<Waiter> waiters = new ArrayList<>();

	private final List<String> log = new ArrayList<>();

	/** A fresh state: no path. Tests make their own. */
	public EndingState() {
	}

	private EndingState(Optional<Run> run, Optional<Watch> watch, Optional<Beats> beats, List<String> log) {
		run.ifPresent(r -> {
			path = r.path();
			pathSince = r.pathSince();
			reason = r.reason();
			r.progress().forEach((name, beat) -> EndingPath.parse(name).ifPresent(p -> progress.put(p, beat)));
			beatSince = r.beatSince();
			ended = r.ended();
			endedAt = r.endedAt();
			deathsSeen = r.deathsSeen();
		});
		watch.ifPresent(w -> {
			stopSeenAt = w.stopSeenAt();
			lastTellingAt = w.lastTellingAt();
			lastNamedAt = w.lastNamedAt();
			lastReadAt = w.lastReadAt();
			lastFogStareAt = w.lastFogStareAt();
			tellingsSinceStop = w.tellingsSinceStop();
			fragmentsBurned = w.fragmentsBurned();
			ownBroken = w.ownBroken();
			housePeak = w.housePeak();
			home = w.home().orElse(null);
		});
		beats.ifPresent(b -> {
			silenceUntilDay = b.silenceUntilDay();
			sightingTries = b.sightingTries();
			lastSightingAt = b.lastSightingAt();
			lastArmAt = b.lastArmAt();
			deathPos = b.deathPos().orElse(null);
			deathAt = b.deathAt();
			house = b.house().orElse(null);
			copyRequested = b.copyRequested();
			finalArmed = b.finalArmed();
			lastHomeArmAt = b.lastHomeArmAt();
			f20Placed = b.f20Placed();
			waiters.addAll(b.waiters());
		});
		this.log.addAll(log);
	}

	public static EndingState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	private Run run() {
		Map<String, Integer> named = new HashMap<>();
		progress.forEach((p, beat) -> named.put(p.name(), beat));
		return new Run(path, pathSince, reason, named, beatSince, ended, endedAt, deathsSeen);
	}

	private Watch watch() {
		return new Watch(stopSeenAt, lastTellingAt, lastNamedAt, lastReadAt, lastFogStareAt, tellingsSinceStop, fragmentsBurned, ownBroken,
				housePeak, Optional.ofNullable(home));
	}

	private Beats beats() {
		return new Beats(silenceUntilDay, sightingTries, lastSightingAt, lastArmAt, Optional.ofNullable(deathPos), deathAt,
				Optional.ofNullable(house), copyRequested, finalArmed, lastHomeArmAt, f20Placed, List.copyOf(waiters));
	}

	// --- path and progress ---

	public EndingPath path() {
		return path;
	}

	/** When the path committed ({@code dayTicks}), or {@link #NEVER}. */
	public long pathSince() {
		return pathSince;
	}

	/** Why the path committed ("third marked death", "debug", ...). */
	public String reason() {
		return reason;
	}

	/**
	 * Sets the path and starts it at beat 0 (its progress is reset). The beats' own memory is cleared so a path that
	 * starts again starts clean; what the player did is kept.
	 */
	void setPath(EndingPath newPath, long now, String why) {
		path = newPath;
		pathSince = newPath == EndingPath.NONE ? NEVER : now;
		reason = why;
		progress.put(newPath, 0);
		beatSince = now;
		clearBeats();
		setDirty();
	}

	private void clearBeats() {
		silenceUntilDay = 0;
		sightingTries = 0;
		lastSightingAt = NEVER;
		lastArmAt = NEVER;
		house = null;
		copyRequested = false;
		finalArmed = false;
		lastHomeArmAt = NEVER;
		waiters.clear();
	}

	/** The beat a path is at (0 if it never started). */
	public int progress(EndingPath p) {
		return progress.getOrDefault(p, 0);
	}

	/** Sets a path's beat; for the current path it also restarts the beat's clock. */
	void setProgress(EndingPath p, int beat, long now) {
		progress.put(p, beat);
		if (p == path) {
			beatSince = now;
		}
		setDirty();
	}

	/** Every path's beat. Unmodifiable. */
	public Map<EndingPath, Integer> allProgress() {
		return Collections.unmodifiableMap(progress);
	}

	/** When the current beat began ({@code dayTicks}). */
	public long beatSince() {
		return beatSince;
	}

	/** True once the story is over: A or B finished, or any marked death in hardcore. Nothing more is armed or moved. */
	public boolean ended() {
		return ended;
	}

	public long endedAt() {
		return endedAt;
	}

	void setEnded(long now) {
		if (!ended) {
			ended = true;
			endedAt = now;
			setDirty();
		}
	}

	/** Debug: the story is not over any more. */
	void clearEnded() {
		ended = false;
		endedAt = NEVER;
		setDirty();
	}

	/** Marked deaths already handled (for the third-death rule). */
	public int deathsSeen() {
		return deathsSeen;
	}

	void setDeathsSeen(int count) {
		deathsSeen = count;
		setDirty();
	}

	// --- what the player did ---

	public long stopSeenAt() {
		return stopSeenAt;
	}

	void setStopSeenAt(long at) {
		stopSeenAt = at;
		setDirty();
	}

	public long lastTellingAt() {
		return lastTellingAt;
	}

	public long lastNamedAt() {
		return lastNamedAt;
	}

	public int tellingsSinceStop() {
		return tellingsSinceStop;
	}

	/** A telling: counts after "Stop." too. */
	void recordTelling(long at, boolean namesHim) {
		lastTellingAt = at;
		if (namesHim) {
			lastNamedAt = at;
		}
		if (stopSeenAt != NEVER && at >= stopSeenAt) {
			tellingsSinceStop++;
		}
		setDirty();
	}

	public long lastReadAt() {
		return lastReadAt;
	}

	void setLastReadAt(long at) {
		lastReadAt = at;
		setDirty();
	}

	public long lastFogStareAt() {
		return lastFogStareAt;
	}

	void setLastFogStareAt(long at) {
		lastFogStareAt = at;
		setDirty();
	}

	public int fragmentsBurned() {
		return fragmentsBurned;
	}

	void addFragmentBurned() {
		fragmentsBurned++;
		setDirty();
	}

	/** Player-placed blocks around the home that the player broke themselves. */
	public int ownBroken() {
		return ownBroken;
	}

	void addOwnBroken() {
		ownBroken++;
		setDirty();
	}

	/** The most player-placed blocks ever seen around the home. */
	public int housePeak() {
		return housePeak;
	}

	void setHousePeak(int peak) {
		housePeak = peak;
		setDirty();
	}

	public Optional<GlobalPos> home() {
		return Optional.ofNullable(home);
	}

	/** A new home: the house counters start over. */
	void setHome(@Nullable GlobalPos pos) {
		home = pos;
		housePeak = 0;
		ownBroken = 0;
		setDirty();
	}

	/** Ending C's reversal: "say his name once and it all starts again". */
	void resetSilenceWork() {
		fragmentsBurned = 0;
		ownBroken = 0;
		housePeak = 0;
		setDirty();
	}

	// --- beats ---

	public long silenceUntilDay() {
		return silenceUntilDay;
	}

	void setSilenceUntilDay(long day) {
		silenceUntilDay = day;
		setDirty();
	}

	public int sightingTries() {
		return sightingTries;
	}

	public long lastSightingAt() {
		return lastSightingAt;
	}

	void recordSighting(long at) {
		sightingTries++;
		lastSightingAt = at;
		setDirty();
	}

	public long lastArmAt() {
		return lastArmAt;
	}

	void setLastArmAt(long at) {
		lastArmAt = at;
		setDirty();
	}

	/**
	 * Ending A: the marked death whose cross the "Stop." sign goes to. Kept when the path changes, so a third-death B
	 * does not lose A's last beat.
	 */
	public Optional<GlobalPos> deathPos() {
		return Optional.ofNullable(deathPos);
	}

	public long deathAt() {
		return deathAt;
	}

	void setDeath(GlobalPos pos, long at) {
		deathPos = pos;
		deathAt = at;
		setDirty();
	}

	/** Ending B: the house being emptied (the base when B committed). */
	public Optional<GlobalPos> house() {
		return Optional.ofNullable(house);
	}

	void setHouse(@Nullable GlobalPos pos) {
		house = pos;
		setDirty();
	}

	public boolean copyRequested() {
		return copyRequested;
	}

	void setCopyRequested(boolean value) {
		copyRequested = value;
		setDirty();
	}

	public boolean finalArmed() {
		return finalArmed;
	}

	void setFinalArmed(boolean value) {
		finalArmed = value;
		setDirty();
	}

	public long lastHomeArmAt() {
		return lastHomeArmAt;
	}

	void setLastHomeArmAt(long at) {
		lastHomeArmAt = at;
		setDirty();
	}

	public boolean f20Placed() {
		return f20Placed;
	}

	void setF20Placed(boolean value) {
		f20Placed = value;
		setDirty();
	}

	public List<Waiter> waiters() {
		return Collections.unmodifiableList(waiters);
	}

	void addWaiter(Waiter waiter) {
		waiters.add(waiter);
		setDirty();
	}

	void removeWaiter(Waiter waiter) {
		waiters.remove(waiter);
		setDirty();
	}

	// --- log ---

	/** The last few things the ending did, oldest first. */
	public List<String> log() {
		return Collections.unmodifiableList(log);
	}

	void log(String line) {
		log.add(line);
		while (log.size() > LOG_MAX) {
			log.removeFirst();
		}
		setDirty();
		A1016_02.LOGGER.info("[a1016] ending: {}", line);
	}
}
