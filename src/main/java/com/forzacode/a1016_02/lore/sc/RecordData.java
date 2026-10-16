package com.forzacode.a1016_02.lore.sc;

import java.util.ArrayList;
import java.util.List;
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
 * 14's memory on the server, {@code data/a1016_02/lore_sc.dat} (every field optional; a world without the file starts with
 * none): (a) and (b)'s beats (armed, done, the nonce, the debug's skip, the in-game day the server learned (a) was shown),
 * (c) done, and (d)'s statistics given, per player, so the switch can take them back.
 */
public final class RecordData extends SavedData {
	/** Statistics (d) gave one player: the stat's type ({@link #MINED} or {@link #DROPPED}), the block or item id, how many. */
	public record Award(UUID player, String stat, String id, int count) {
		static final Codec<Award> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(Award::player),
				Codec.STRING.fieldOf("stat").forGetter(Award::stat),
				Codec.STRING.fieldOf("id").forGetter(Award::id),
				Codec.INT.fieldOf("count").forGetter(Award::count)
		).apply(i, Award::new));
	}

	public static final String MINED = "mined";
	public static final String DROPPED = "dropped";
	/** At most this many award lines are kept (each player's, each block or item, summed). */
	static final int MAX_AWARDS = 4096;

	public static final Codec<RecordData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("aNonce", 0L).forGetter(d -> d.lastNonce),
			Codec.BOOL.optionalFieldOf("aArmed", false).forGetter(d -> d.lastArmed),
			Codec.BOOL.optionalFieldOf("aNogap", false).forGetter(d -> d.lastNogap),
			Codec.BOOL.optionalFieldOf("aDone", false).forGetter(d -> d.lastDone),
			Codec.LONG.optionalFieldOf("aDay", -1L).forGetter(d -> d.lastShownDay),
			Codec.LONG.optionalFieldOf("bNonce", 0L).forGetter(d -> d.verNonce),
			Codec.BOOL.optionalFieldOf("bArmed", false).forGetter(d -> d.verArmed),
			Codec.BOOL.optionalFieldOf("bNogap", false).forGetter(d -> d.verNogap),
			Codec.BOOL.optionalFieldOf("bDone", false).forGetter(d -> d.verDone),
			Codec.BOOL.optionalFieldOf("cDone", false).forGetter(d -> d.seedDone),
			Award.CODEC.listOf().optionalFieldOf("d", List.of()).forGetter(d -> List.copyOf(d.awards))
	).apply(i, RecordData::new));

	public static final SavedDataType<RecordData> TYPE = new SavedDataType<>(A1016_02.id("lore_sc"), RecordData::new, CODEC, null);

	long lastNonce;
	boolean lastArmed;
	boolean lastNogap;
	boolean lastDone;
	long lastShownDay = -1L;
	long verNonce;
	boolean verArmed;
	boolean verNogap;
	boolean verDone;
	boolean seedDone;
	final List<Award> awards = new ArrayList<>();

	public RecordData() {
	}

	private RecordData(long lastNonce, boolean lastArmed, boolean lastNogap, boolean lastDone, long lastShownDay, long verNonce, boolean verArmed,
			boolean verNogap, boolean verDone, boolean seedDone, List<Award> awards) {
		this.lastNonce = lastNonce;
		this.lastArmed = lastArmed;
		this.lastNogap = lastNogap;
		this.lastDone = lastDone;
		this.lastShownDay = lastShownDay;
		this.verNonce = verNonce;
		this.verArmed = verArmed;
		this.verNogap = verNogap;
		this.verDone = verDone;
		this.seedDone = seedDone;
		this.awards.addAll(awards);
	}

	public static RecordData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** (a) armed now with this nonce. */
	void armLast(long nonce, boolean nogap) {
		lastNonce = nonce;
		lastArmed = true;
		lastNogap = nogap;
		lastDone = false;
		setDirty();
	}

	/** (a) was shown: done, and the in-game day the server learned it. */
	void lastShown(long day) {
		lastArmed = false;
		lastDone = true;
		lastShownDay = day;
		setDirty();
	}

	/** (b) armed now with this nonce. */
	void armVersion(long nonce, boolean nogap) {
		verNonce = nonce;
		verArmed = true;
		verNogap = nogap;
		verDone = false;
		setDirty();
	}

	/** (b) was shown: done. */
	void versionShown() {
		verArmed = false;
		verDone = true;
		setDirty();
	}

	void seedDone() {
		seedDone = true;
		setDirty();
	}

	/** Debug: forgets every beat and the seed (the statistics stay). */
	void forgetBeats() {
		lastArmed = false;
		lastDone = false;
		lastShownDay = -1L;
		verArmed = false;
		verDone = false;
		seedDone = false;
		setDirty();
	}

	/** (a) as it stands for the file: the beat while armed. */
	Optional<RecordRules.Beat> lastBeat() {
		return lastArmed ? Optional.of(new RecordRules.Beat(lastNonce, lastNogap)) : Optional.empty();
	}

	/** (b) as it stands for the file: the beat while armed. */
	Optional<RecordRules.Beat> versionBeat() {
		return verArmed ? Optional.of(new RecordRules.Beat(verNonce, verNogap)) : Optional.empty();
	}

	/** (d): {@code count} more of that stat given to that player (summed per player, stat and id). */
	void award(UUID player, String stat, String id, int count) {
		for (int i = 0; i < awards.size(); i++) {
			Award a = awards.get(i);
			if (a.player().equals(player) && a.stat().equals(stat) && a.id().equals(id)) {
				awards.set(i, new Award(player, stat, id, a.count() + count));
				setDirty();
				return;
			}
		}
		if (awards.size() < MAX_AWARDS) {
			awards.add(new Award(player, stat, id, count));
			setDirty();
		}
	}

	/** (d): what was given to that player, and forgets it. */
	List<Award> takeAwards(UUID player) {
		List<Award> taken = new ArrayList<>();
		awards.removeIf(a -> {
			if (a.player().equals(player)) {
				taken.add(a);
				return true;
			}
			return false;
		});
		if (!taken.isEmpty()) {
			setDirty();
		}
		return taken;
	}

	public boolean lastArmed() {
		return lastArmed;
	}

	/** (a)'s nonce (0 before any arm). */
	public long lastNonce() {
		return lastNonce;
	}

	/** (b)'s nonce (0 before any arm). */
	public long versionNonce() {
		return verNonce;
	}

	public boolean lastDone() {
		return lastDone;
	}

	public long lastShownDay() {
		return lastShownDay;
	}

	public boolean versionArmed() {
		return verArmed;
	}

	public boolean versionDone() {
		return verDone;
	}

	public boolean seedIsDone() {
		return seedDone;
	}

	/** Every award line kept (a copy). */
	public List<Award> awards() {
		return List.copyOf(awards);
	}
}
