package com.forzacode.a1016_02.world.sc2.s28;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * What 28 keeps ({@code data/a1016_02/world_sc28.dat}): each visit of one player to another's base (the visitor, the
 * host, the world's day tick of the latest, {@code GameClock.dayTicks}), how many moves the world had, the in-game day of
 * the last and which way it went (from whose base to whose; a fake leaves the way as it was). A world without the file
 * has none; read leniently, entry by entry. Visits older than {@link #FORGET_DAYS} in-game days are dropped as new ones
 * come. Server thread.
 */
public final class S28Data extends SavedData {
	public static final Codec<S28Data> CODEC = CompoundTag.CODEC.xmap(S28Data::load, S28Data::save);
	public static final SavedDataType<S28Data> TYPE = new SavedDataType<>(A1016_02.id("world_sc28"), S28Data::new, CODEC, null);
	/** Visits are forgotten after this many in-game days (only the last few ever count). */
	public static final long FORGET_DAYS = 16;

	/** A visit: who came, to whose base. */
	public record Pair(UUID visitor, UUID host) {
	}

	private final Map<Pair, Long> visits = new LinkedHashMap<>();
	private int count;
	private long lastDay = -1;
	private @Nullable UUID lastFrom;
	private @Nullable UUID lastTo;

	public S28Data() {
	}

	public static S28Data get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** {@code visitor} was within the visit radius of {@code host}'s base at {@code dayTicks}. */
	public void visit(UUID visitor, UUID host, long dayTicks) {
		Long was = visits.put(new Pair(visitor, host), dayTicks);
		if (was == null || was != dayTicks) {
			visits.values().removeIf(at -> at < dayTicks - FORGET_DAYS * 24000L);
			setDirty();
		}
	}

	/** The day tick of {@code visitor}'s latest visit to {@code host}'s base, or -1 if none is kept. */
	public long lastVisit(UUID visitor, UUID host) {
		return visits.getOrDefault(new Pair(visitor, host), -1L);
	}

	public Map<Pair, Long> visits() {
		return Map.copyOf(visits);
	}

	/** How many moves (fakes included) the world had. */
	public int count() {
		return count;
	}

	/** The in-game day of the last move, or -1. */
	public long lastDay() {
		return lastDay;
	}

	/** Whose base the last real move took from, or null. */
	public @Nullable UUID lastFrom() {
		return lastFrom;
	}

	/** Whose base the last real move put into, or null. */
	public @Nullable UUID lastTo() {
		return lastTo;
	}

	/** A move happened on {@code day}: counted; a real one (not {@code fake}) also sets the way. Saved now. */
	public void moved(UUID from, UUID to, long day, boolean fake) {
		count++;
		lastDay = day;
		if (!fake) {
			lastFrom = from;
			lastTo = to;
		}
		setDirty();
	}

	/** Debug: forgets the count, the day and the way (the visits stay). */
	public void reset() {
		count = 0;
		lastDay = -1;
		lastFrom = null;
		lastTo = null;
		setDirty();
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		visits.forEach((pair, at) -> {
			CompoundTag v = new CompoundTag();
			v.putString("v", pair.visitor().toString());
			v.putString("h", pair.host().toString());
			v.putLong("t", at);
			list.add(v);
		});
		tag.put("visits", list);
		tag.putInt("count", count);
		tag.putLong("day", lastDay);
		if (lastFrom != null && lastTo != null) {
			tag.putString("from", lastFrom.toString());
			tag.putString("to", lastTo.toString());
		}
		return tag;
	}

	private static S28Data load(CompoundTag tag) {
		S28Data data = new S28Data();
		ListTag list = tag.getListOrEmpty("visits");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag v = list.getCompoundOrEmpty(i);
			UUID visitor = uuid(v.getStringOr("v", ""));
			UUID host = uuid(v.getStringOr("h", ""));
			if (visitor != null && host != null) {
				data.visits.put(new Pair(visitor, host), v.getLongOr("t", -1L));
			}
		}
		data.count = Math.max(0, tag.getIntOr("count", 0));
		data.lastDay = tag.getLongOr("day", -1L);
		data.lastFrom = uuid(tag.getStringOr("from", ""));
		data.lastTo = uuid(tag.getStringOr("to", ""));
		if (data.lastFrom == null || data.lastTo == null) {
			data.lastFrom = null;
			data.lastTo = null;
		}
		return data;
	}

	private static @Nullable UUID uuid(String text) {
		try {
			return text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException broken) {
			return null;
		}
	}
}
