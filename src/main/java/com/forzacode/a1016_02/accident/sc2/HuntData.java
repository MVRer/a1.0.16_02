package com.forzacode.a1016_02.accident.sc2;

import java.util.Collections;
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

/**
 * What accident keeps for the second wave's two trap kinds (docs/SELF_CREATED_2.md, 25 and 26), in
 * {@code data/a1016_02/accident_sc2.dat}: per kind ({@code sc25}, {@code sc26}), the in-game day each player last had
 * one and how many the world had. A world without the file has none; read leniently, entry by entry.
 */
public final class HuntData extends SavedData {
	public static final Codec<HuntData> CODEC = CompoundTag.CODEC.xmap(HuntData::load, HuntData::save);
	public static final SavedDataType<HuntData> TYPE = new SavedDataType<>(A1016_02.id("accident_sc2"), HuntData::new, CODEC, null);

	private final Map<String, Map<UUID, Long>> days = new LinkedHashMap<>();
	private final Map<String, Integer> counts = new LinkedHashMap<>();

	public HuntData() {
	}

	public static HuntData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The in-game day this player last had one of this kind, or -1. */
	public long lastDay(String kind, UUID player) {
		return days.getOrDefault(kind, Map.of()).getOrDefault(player, -1L);
	}

	/** How many of this kind the world had. */
	public int count(String kind) {
		return counts.getOrDefault(kind, 0);
	}

	public Map<UUID, Long> days(String kind) {
		return Collections.unmodifiableMap(days.getOrDefault(kind, Map.of()));
	}

	/** One of this kind began for this player on this day: saved now. */
	public void record(String kind, UUID player, long day) {
		days.computeIfAbsent(kind, k -> new LinkedHashMap<>()).put(player, day);
		counts.merge(kind, 1, Integer::sum);
		setDirty();
	}

	/** Debug: forgets every day and the count of this kind. */
	public void reset(String kind) {
		days.remove(kind);
		counts.remove(kind);
		setDirty();
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		days.forEach((kind, players) -> players.forEach((player, day) -> {
			CompoundTag d = new CompoundTag();
			d.putString("k", kind);
			d.putString("p", player.toString());
			d.putLong("d", day);
			list.add(d);
		}));
		tag.put("days", list);
		CompoundTag n = new CompoundTag();
		counts.forEach(n::putInt);
		tag.put("counts", n);
		return tag;
	}

	private static HuntData load(CompoundTag tag) {
		HuntData data = new HuntData();
		ListTag list = tag.getListOrEmpty("days");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag d = list.getCompoundOrEmpty(i);
			String kind = d.getStringOr("k", "");
			if (kind.isEmpty()) {
				continue;
			}
			try {
				data.days.computeIfAbsent(kind, k -> new LinkedHashMap<>()).put(UUID.fromString(d.getStringOr("p", "")), d.getLongOr("d", -1L));
			} catch (IllegalArgumentException ignored) {
				// a broken uuid: that day is forgotten
			}
		}
		CompoundTag n = tag.getCompoundOrEmpty("counts");
		for (String kind : n.keySet()) {
			data.counts.put(kind, Math.max(0, n.getIntOr(kind, 0)));
		}
		return data;
	}
}
