package com.forzacode.a1016_02.entity.sc2.s27;

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
 * What entity keeps for the second wave (docs/SELF_CREATED_2.md, 27), in {@code data/a1016_02/entity_sc2.dat}: the in-game
 * day each viewer last had 27 and how many the world had. A world without the file has none; read leniently, every key
 * optional (layer rule 2).
 */
public final class Sc27Data extends SavedData {
	public static final Codec<Sc27Data> CODEC = CompoundTag.CODEC.xmap(Sc27Data::load, Sc27Data::save);
	public static final SavedDataType<Sc27Data> TYPE = new SavedDataType<>(A1016_02.id("entity_sc2"), Sc27Data::new, CODEC, null);

	private final Map<UUID, Long> days = new LinkedHashMap<>();
	private int count;

	public Sc27Data() {
	}

	public static Sc27Data get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The in-game day this viewer last had it, or -1. */
	public long lastDay(UUID viewer) {
		return days.getOrDefault(viewer, -1L);
	}

	public Map<UUID, Long> days() {
		return Collections.unmodifiableMap(days);
	}

	public int count() {
		return count;
	}

	/** It happened for this viewer on this day: saved now. */
	public void record(UUID viewer, long day) {
		days.put(viewer, day);
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
		days.forEach((viewer, day) -> {
			CompoundTag d = new CompoundTag();
			d.putString("p", viewer.toString());
			d.putLong("d", day);
			list.add(d);
		});
		tag.put("s27", list);
		tag.putInt("n27", count);
		return tag;
	}

	static Sc27Data load(CompoundTag tag) {
		Sc27Data data = new Sc27Data();
		ListTag list = tag.getListOrEmpty("s27");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag d = list.getCompoundOrEmpty(i);
			try {
				data.days.put(UUID.fromString(d.getStringOr("p", "")), d.getLongOr("d", -1L));
			} catch (IllegalArgumentException ignored) {
				// a broken uuid: that day is forgotten
			}
		}
		data.count = Math.max(0, tag.getIntOr("n27", 0));
		return data;
	}

	/** Tests: the save as it would be written, and read back. */
	public static Sc27Data roundTrip(Sc27Data data) {
		return load(data.save());
	}
}
