package com.forzacode.a1016_02.accident.sc;

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
 * What accident keeps for the layer (docs/SELF_CREATED.md, 15), in {@code data/a1016_02/accident_sc.dat}: the in-game
 * day each player last had it and how many the world had. A world without the file has none; read leniently.
 */
public final class StoodOnData extends SavedData {
	public static final Codec<StoodOnData> CODEC = CompoundTag.CODEC.xmap(StoodOnData::load, StoodOnData::save);
	public static final SavedDataType<StoodOnData> TYPE = new SavedDataType<>(A1016_02.id("accident_sc"), StoodOnData::new, CODEC, null);

	private final Map<UUID, Long> days = new LinkedHashMap<>();
	private int count;

	public StoodOnData() {
	}

	public static StoodOnData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The in-game day this player last had it, or -1. */
	public long lastDay(UUID player) {
		return days.getOrDefault(player, -1L);
	}

	public Map<UUID, Long> days() {
		return Collections.unmodifiableMap(days);
	}

	public int count() {
		return count;
	}

	/** It happened for this player on this day: saved now. */
	public void record(UUID player, long day) {
		days.put(player, day);
		count++;
		setDirty();
	}

	/** Debug: forgets every day and the count. */
	public void reset() {
		days.clear();
		count = 0;
		setDirty();
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		days.forEach((player, day) -> {
			CompoundTag d = new CompoundTag();
			d.putString("player", player.toString());
			d.putLong("day", day);
			list.add(d);
		});
		tag.put("days", list);
		tag.putInt("count", count);
		return tag;
	}

	private static StoodOnData load(CompoundTag tag) {
		StoodOnData data = new StoodOnData();
		ListTag list = tag.getListOrEmpty("days");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag d = list.getCompoundOrEmpty(i);
			try {
				data.days.put(UUID.fromString(d.getStringOr("player", "")), d.getLongOr("day", -1L));
			} catch (IllegalArgumentException ignored) {
				// a broken uuid: that day is forgotten
			}
		}
		data.count = Math.max(0, tag.getIntOr("count", 0));
		return data;
	}
}
