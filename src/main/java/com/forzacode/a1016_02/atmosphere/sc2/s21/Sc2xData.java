package com.forzacode.a1016_02.atmosphere.sc2.s21;

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
import net.minecraft.world.phys.Vec3;

/**
 * The world memory of the second wave's mob behaviours (21 to 24, docs/SELF_CREATED_2.md), in one small file of the
 * world's data ({@code a1016_02/sc2x}): each card's world caps (how many, the in-game day and the night of the last),
 * each player's last day of 23, where he last stood (23's bearing when no place is known) and 21's empty night roll
 * (04+). Every field optional and read leniently (layer rule 2): an old world loads with none.
 */
public final class Sc2xData extends SavedData {
	/** A card's world record: how many, the in-game day of the last and its night (-1 never). */
	public record Caps(int count, long lastDay, long lastNight) {
		public static final Caps START = new Caps(0, -1L, -1L);
		static final Codec<Caps> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("n", 0).forGetter(Caps::count),
				Codec.LONG.optionalFieldOf("d", -1L).forGetter(Caps::lastDay),
				Codec.LONG.optionalFieldOf("t", -1L).forGetter(Caps::lastNight)
		).apply(i, Caps::new));

		/** One more, on this day and night. */
		public Caps plus(long day, long night) {
			return new Caps(count + 1, day, night);
		}
	}

	/** 04+'s roll: the night it was rolled for, whether it won, the night one formed (-1 never). */
	public record EmptyRoll(long rolled, boolean won, long formed) {
		public static final EmptyRoll START = new EmptyRoll(-1L, false, -1L);
		static final Codec<EmptyRoll> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("r", -1L).forGetter(EmptyRoll::rolled),
				Codec.BOOL.optionalFieldOf("w", false).forGetter(EmptyRoll::won),
				Codec.LONG.optionalFieldOf("f", -1L).forGetter(EmptyRoll::formed)
		).apply(i, EmptyRoll::new));
	}

	public static final Codec<Sc2xData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Caps.CODEC).lenientOptionalFieldOf("c", Map.of()).forGetter(d -> d.caps),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).lenientOptionalFieldOf("b", Map.of()).forGetter(d -> d.breathDays),
			Vec3.CODEC.lenientOptionalFieldOf("s").forGetter(d -> d.stood),
			EmptyRoll.CODEC.lenientOptionalFieldOf("e", EmptyRoll.START).forGetter(d -> d.emptyRoll),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Vec3.CODEC).lenientOptionalFieldOf("p", Map.of()).forGetter(d -> d.pointed)
	).apply(i, Sc2xData::new));

	public static final SavedDataType<Sc2xData> TYPE = new SavedDataType<>(A1016_02.id("sc2x"), Sc2xData::new, CODEC, null);

	private final Map<String, Caps> caps = new HashMap<>();
	private final Map<UUID, Long> breathDays = new HashMap<>();
	private Optional<Vec3> stood = Optional.empty();
	private EmptyRoll emptyRoll = EmptyRoll.START;
	/** R10-6: the place 23 last pointed each player at (its anchor), until a file of 22 walks there for them. */
	private final Map<UUID, Vec3> pointed = new HashMap<>();

	public Sc2xData() {
	}

	private Sc2xData(Map<String, Caps> caps, Map<UUID, Long> breathDays, Optional<Vec3> stood, EmptyRoll emptyRoll, Map<UUID, Vec3> pointed) {
		this.caps.putAll(caps);
		this.breathDays.putAll(breathDays);
		this.stood = stood;
		this.emptyRoll = emptyRoll;
		this.pointed.putAll(pointed);
	}

	/** R10-6: where 23 last pointed this player (a place's anchor), if a file has not walked there for them since. */
	public Optional<Vec3> pointed(UUID player) {
		return Optional.ofNullable(pointed.get(player));
	}

	public void setPointed(UUID player, Vec3 anchor) {
		pointed.put(player, anchor);
		setDirty();
	}

	/** The file walked there for this player (or the place is gone): forgotten. */
	public void clearPointed(UUID player) {
		if (pointed.remove(player) != null) {
			setDirty();
		}
	}

	public static Sc2xData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The world record of card {@code id} ({@code "21"}, {@code "22"}, {@code "24"}). */
	public Caps caps(String id) {
		return caps.getOrDefault(id, Caps.START);
	}

	public void setCaps(String id, Caps value) {
		caps.put(id, value);
		setDirty();
	}

	/** Debug: forgets card {@code id}'s record. */
	public void resetCaps(String id) {
		if (caps.remove(id) != null) {
			setDirty();
		}
	}

	/** The in-game day of this player's last 23, or -1. */
	public long breathDay(UUID player) {
		return breathDays.getOrDefault(player, -1L);
	}

	public void setBreathDay(UUID player, long day) {
		breathDays.put(player, day);
		setDirty();
	}

	public void resetBreathDays() {
		if (!breathDays.isEmpty()) {
			breathDays.clear();
			setDirty();
		}
	}

	/** Where he last stood still (10's spot, from {@code FIGURE_LEFT} in the overworld), if ever. */
	public Optional<Vec3> stood() {
		return stood;
	}

	public void setStood(Vec3 at) {
		stood = Optional.of(at);
		setDirty();
	}

	public EmptyRoll emptyRoll() {
		return emptyRoll;
	}

	public void setEmptyRoll(EmptyRoll roll) {
		if (!roll.equals(emptyRoll)) {
			emptyRoll = roll;
			setDirty();
		}
	}
}
