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
 * happened, which stage the dusk fog was last set for, the mining-in-the-dark count for the current night, and the
 * long transient effects (silence, compass drift) still running per player, timed in play ticks so they carry on
 * after a relog or a world reload.
 */
public final class AtmosphereData extends SavedData {
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
			Codec.LONG.optionalFieldOf("miningNight", -1L).forGetter(d -> d.miningNight),
			Codec.INT.optionalFieldOf("miningCount", 0).forGetter(d -> d.miningCount),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Transient.CODEC).optionalFieldOf("transients", Map.of()).forGetter(d -> d.transients)
	).apply(i, AtmosphereData::new));

	public static final SavedDataType<AtmosphereData> TYPE = new SavedDataType<>(A1016_02.id("atmosphere"), AtmosphereData::new, CODEC, null);

	private boolean musicOffDone;
	private int duskStage = -1;
	private long miningNight = -1;
	private int miningCount;
	private final Map<UUID, Transient> transients = new HashMap<>();

	public AtmosphereData() {
	}

	private AtmosphereData(boolean musicOffDone, int duskStage, long miningNight, int miningCount, Map<UUID, Transient> transients) {
		this.musicOffDone = musicOffDone;
		this.duskStage = duskStage;
		this.miningNight = miningNight;
		this.miningCount = miningCount;
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

	/** Mining sounds played on this night (the in-game day number). */
	public int miningOn(long night) {
		return miningNight == night ? miningCount : 0;
	}

	public void recordMining(long night) {
		if (miningNight != night) {
			miningNight = night;
			miningCount = 0;
		}
		miningCount++;
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
