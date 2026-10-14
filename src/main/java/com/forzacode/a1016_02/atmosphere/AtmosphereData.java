package com.forzacode.a1016_02.atmosphere;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Atmosphere's own per-world facts ({@code data/a1016_02/atmosphere.dat}): whether the first-night music cut
 * happened (once per world), which stage the dusk fog was last set for, each player's mining-in-the-dark count for
 * the night (by UUID: everyone who heard one has had theirs, D-059/D-060), and the long transient effects (silence,
 * compass drift) still running per player, timed on the world play clock so they carry on after a relog or a world
 * reload.
 *
 * <p>Old saves (D-062.8): a v1.0 file kept one world-level mining count ({@code miningNight}, {@code miningCount}).
 * It loads as everyone's count for that night, and is dropped (no longer written) once any player's mining is
 * recorded on another night.
 */
public final class AtmosphereData extends SavedData {
	/** One player's mining sounds on one night (the in-game day number). */
	public record Mining(long night, int count) {
		static final Codec<Mining> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("night").forGetter(Mining::night),
				Codec.INT.fieldOf("count").forGetter(Mining::count)
		).apply(i, Mining::new));
	}

	/** Running silence and compass drift of one player. Starts are {@code GameClock.playTicks}; a length of 0 means none. */
	public record Transient(long silenceStart, int silenceTicks, int silenceFade, long compassStart, int compassTicks, int compassX, int compassZ,
			int compassSettle) {
		static final Transient NONE = new Transient(0, 0, 0, 0, 0, 0, 0, 0);
		static final Codec<Transient> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("silenceStart", 0L).forGetter(Transient::silenceStart),
				Codec.INT.optionalFieldOf("silenceTicks", 0).forGetter(Transient::silenceTicks),
				Codec.INT.optionalFieldOf("silenceFade", 0).forGetter(Transient::silenceFade),
				Codec.LONG.optionalFieldOf("compassStart", 0L).forGetter(Transient::compassStart),
				Codec.INT.optionalFieldOf("compassTicks", 0).forGetter(Transient::compassTicks),
				Codec.INT.optionalFieldOf("compassX", 0).forGetter(Transient::compassX),
				Codec.INT.optionalFieldOf("compassZ", 0).forGetter(Transient::compassZ),
				Codec.INT.optionalFieldOf("compassSettle", 0).forGetter(Transient::compassSettle)
		).apply(i, Transient::new));

		/** Silence ticks still to come, not counting the fade; 0 if over. */
		public long silenceLeft(long now) {
			return Math.max(0, silenceStart + silenceTicks - now);
		}

		public long compassLeft(long now) {
			return Math.max(0, compassStart + compassTicks - now);
		}

		boolean over(long now) {
			return silenceLeft(now) == 0 && compassLeft(now) == 0;
		}
	}

	public static final Codec<AtmosphereData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("musicOffDone", false).forGetter(d -> d.musicOffDone),
			Codec.INT.optionalFieldOf("duskStage", -1).forGetter(d -> d.duskStage),
			// v1.0's one world-level count; written only while it still covers a night (defaults are left out).
			Codec.LONG.optionalFieldOf("miningNight", -1L).forGetter(d -> d.legacyMiningNight),
			Codec.INT.optionalFieldOf("miningCount", 0).forGetter(d -> d.legacyMiningCount),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Mining.CODEC).optionalFieldOf("mining", Map.of()).forGetter(d -> d.mining),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Transient.CODEC).optionalFieldOf("transients", Map.of()).forGetter(d -> d.transients)
	).apply(i, AtmosphereData::new));

	public static final SavedDataType<AtmosphereData> TYPE = new SavedDataType<>(A1016_02.id("atmosphere"), AtmosphereData::new, CODEC, null);

	private boolean musicOffDone;
	private int duskStage = -1;
	/** A v1.0 save's world-level mining night and count: everyone's for that night. -1 when there is none. */
	private long legacyMiningNight = -1;
	private int legacyMiningCount;
	private final Map<UUID, Mining> mining = new HashMap<>();
	private final Map<UUID, Transient> transients = new HashMap<>();

	public AtmosphereData() {
	}

	private AtmosphereData(boolean musicOffDone, int duskStage, long legacyMiningNight, int legacyMiningCount, Map<UUID, Mining> mining,
			Map<UUID, Transient> transients) {
		this.musicOffDone = musicOffDone;
		this.duskStage = duskStage;
		this.legacyMiningNight = legacyMiningCount > 0 ? legacyMiningNight : -1;
		this.legacyMiningCount = legacyMiningCount > 0 ? legacyMiningCount : 0;
		this.mining.putAll(mining);
		this.transients.putAll(transients);
	}

	public static AtmosphereData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean musicOffDone() {
		return musicOffDone;
	}

	public void setMusicOffDone() {
		musicOffDone = true;
		setDirty();
	}

	/** The stage the dusk fog level was last set for, or -1. */
	public int duskStage() {
		return duskStage;
	}

	public void setDuskStage(int stage) {
		duskStage = stage;
		setDirty();
	}

	/**
	 * Mining sounds this player had on this night (the in-game day number): their own, or the old world-level count
	 * of a v1.0 save if it is for this night.
	 */
	public int miningOn(UUID player, long night) {
		Mining own = mining.get(player);
		int count = own != null && own.night() == night ? own.count() : 0;
		return legacyMiningNight == night ? Math.max(count, legacyMiningCount) : count;
	}

	/** One more mining sound on this night for this player (the target, and everyone who heard it). */
	public void recordMining(UUID player, long night) {
		mining.put(player, new Mining(night, miningOn(player, night) + 1));
		mining.values().removeIf(m -> m.night() < night); // last nights are over
		if (legacyMiningNight != night) {
			legacyMiningNight = -1;
			legacyMiningCount = 0;
		}
		setDirty();
	}

	public Optional<Transient> transientOf(UUID player) {
		return Optional.ofNullable(transients.get(player));
	}

	void setSilence(UUID player, long now, int ticks, int fade) {
		Transient t = transients.getOrDefault(player, Transient.NONE);
		put(player, new Transient(now, ticks, fade, t.compassStart(), t.compassTicks(), t.compassX(), t.compassZ(), t.compassSettle()), now);
	}

	void setCompass(UUID player, long now, int ticks, int x, int z, int settle) {
		Transient t = transients.getOrDefault(player, Transient.NONE);
		put(player, new Transient(t.silenceStart(), t.silenceTicks(), t.silenceFade(), now, ticks, x, z, settle), now);
	}

	private void put(UUID player, Transient value, long now) {
		if (value.over(now)) {
			transients.remove(player);
		} else {
			transients.put(player, value);
		}
		transients.values().removeIf(t -> t.over(now));
		setDirty();
	}
}
