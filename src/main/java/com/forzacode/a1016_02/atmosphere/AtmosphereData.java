package com.forzacode.a1016_02.atmosphere;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Atmosphere's own per-world facts ({@code data/a1016_02/atmosphere.dat}): whether the first-night music cut
 * happened, which stage the dusk fog was last set for, and the mining-in-the-dark count for the current night.
 */
public final class AtmosphereData extends SavedData {
	public static final Codec<AtmosphereData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("musicOffDone", false).forGetter(d -> d.musicOffDone),
			Codec.INT.optionalFieldOf("duskStage", -1).forGetter(d -> d.duskStage),
			Codec.LONG.optionalFieldOf("miningNight", -1L).forGetter(d -> d.miningNight),
			Codec.INT.optionalFieldOf("miningCount", 0).forGetter(d -> d.miningCount)
	).apply(i, AtmosphereData::new));

	public static final SavedDataType<AtmosphereData> TYPE = new SavedDataType<>(A1016_02.id("atmosphere"), AtmosphereData::new, CODEC, null);

	private boolean musicOffDone;
	private int duskStage = -1;
	private long miningNight = -1;
	private int miningCount;

	public AtmosphereData() {
	}

	private AtmosphereData(boolean musicOffDone, int duskStage, long miningNight, int miningCount) {
		this.musicOffDone = musicOffDone;
		this.duskStage = duskStage;
		this.miningNight = miningNight;
		this.miningCount = miningCount;
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
}
