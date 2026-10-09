package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * The entity workstream's own memory, {@code data/a1016_02/entity.dat}: where and when the last sighting was,
 * which variant it was, and how many there were that day. Fakes count for the place and the day (to the player
 * they were sightings too) but not for the variant rule.
 */
public final class EntityData extends SavedData {
	public static final Codec<EntityData> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.optionalFieldOf("lastPos").forGetter(d -> Optional.ofNullable(d.lastPos)),
			Codec.LONG.optionalFieldOf("lastDay", -1L).forGetter(d -> d.lastDay),
			Codec.INT.optionalFieldOf("countOnLastDay", 0).forGetter(d -> d.countOnLastDay),
			Codec.STRING.optionalFieldOf("lastVariant", "").forGetter(d -> d.lastVariant),
			Codec.INT.optionalFieldOf("sightings", 0).forGetter(d -> d.sightings),
			Codec.INT.optionalFieldOf("fakes", 0).forGetter(d -> d.fakes),
			Codec.INT.optionalFieldOf("stared", 0).forGetter(d -> d.stared),
			PendingDig.CODEC.listOf().optionalFieldOf("pendingDigs", List.of()).forGetter(d -> List.copyOf(d.pendingDigs))
	).apply(i, EntityData::new));

	public static final SavedDataType<EntityData> TYPE = new SavedDataType<>(A1016_02.id("entity"), EntityData::new, CODEC, null);

	private @Nullable GlobalPos lastPos;
	private long lastDay = -1;
	private int countOnLastDay;
	private String lastVariant = "";
	private int sightings;
	private int fakes;
	private int stared;
	/** Goes-under shafts dug but not covered or put back yet (D-030). */
	private final List<PendingDig> pendingDigs = new ArrayList<>();

	public EntityData() {
	}

	private EntityData(Optional<GlobalPos> lastPos, long lastDay, int countOnLastDay, String lastVariant, int sightings, int fakes, int stared,
			List<PendingDig> pendingDigs) {
		this.lastPos = lastPos.orElse(null);
		this.lastDay = lastDay;
		this.countOnLastDay = countOnLastDay;
		this.lastVariant = lastVariant;
		this.sightings = sightings;
		this.fakes = fakes;
		this.stared = stared;
		this.pendingDigs.addAll(pendingDigs);
	}

	/** Records (or updates, by dig id) a shaft that is open. */
	public void putPendingDig(PendingDig dig) {
		pendingDigs.removeIf(d -> d.id().equals(dig.id()));
		pendingDigs.add(dig);
		setDirty();
	}

	/** The shaft is covered or put back. */
	public void removePendingDig(String id) {
		if (pendingDigs.removeIf(d -> d.id().equals(id))) {
			setDirty();
		}
	}

	/** Open shafts, oldest first. */
	public List<PendingDig> pendingDigs() {
		return List.copyOf(pendingDigs);
	}

	public static EntityData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** A real sighting of {@code variant} (its short name) at {@code pos} on in-game day {@code day}. */
	public void recordSighting(String variant, GlobalPos pos, long day) {
		recordPlaceAndDay(pos, day);
		lastVariant = variant;
		sightings++;
		setDirty();
	}

	/** A false positive: counts for the place and the day, not for the variant rule. */
	public void recordFake(GlobalPos pos, long day) {
		recordPlaceAndDay(pos, day);
		fakes++;
		setDirty();
	}

	public void recordStared() {
		stared++;
		setDirty();
	}

	private void recordPlaceAndDay(GlobalPos pos, long day) {
		lastPos = pos;
		if (lastDay == day) {
			countOnLastDay++;
		} else {
			lastDay = day;
			countOnLastDay = 1;
		}
	}

	public boolean dayAllows(long today, int maxPerDay) {
		return SightingRules.dayAllows(lastDay, countOnLastDay, today, maxPerDay);
	}

	public boolean variantAllows(Variant variant) {
		return SightingRules.variantAllows(lastVariant, variant.shortName());
	}

	public boolean farEnough(GlobalPos pos, int spacing) {
		return SightingRules.farEnough(lastPos, pos, spacing);
	}

	public @Nullable GlobalPos lastPos() {
		return lastPos;
	}

	public long lastDay() {
		return lastDay;
	}

	public int countOnLastDay() {
		return countOnLastDay;
	}

	public String lastVariant() {
		return lastVariant;
	}

	public int sightings() {
		return sightings;
	}

	public int fakes() {
		return fakes;
	}

	public int stared() {
		return stared;
	}
}
