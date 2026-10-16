package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * The entity workstream's own memory, {@code data/a1016_02/entity.dat}: where and when the last sighting was,
 * which variant it was, and how many there were that day. Fakes count for the place and the day (to the player
 * they were sightings too) but not for the variant rule. The glimpse (D-073) keeps its own record, the in-game day of
 * the last one and how many there were: it is underground at the caller's spot, so it never counts for the surface
 * sightings' place, day or variant rules (the director's one-sighting-a-day limit covers it through its card's tag).
 * The screenshot (D-078) happens once per world: the in-game day it happened and whose it was; it counts as a
 * sighting for that day (the day rule), never for the place or the variant rule.
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
			PendingDig.CODEC.listOf().optionalFieldOf("pendingDigs", List.of()).forGetter(d -> List.copyOf(d.pendingDigs)),
			Codec.LONG.optionalFieldOf("lastGlimpseDay", -1L).forGetter(d -> d.lastGlimpseDay),
			Codec.INT.optionalFieldOf("glimpses", 0).forGetter(d -> d.glimpses),
			Codec.LONG.optionalFieldOf("screenshotDay", -1L).forGetter(d -> d.screenshotDay),
			UUIDUtil.CODEC.optionalFieldOf("screenshotBy").forGetter(d -> Optional.ofNullable(d.screenshotBy)),
			Codec.BOOL.optionalFieldOf("sc03Done", false).forGetter(d -> d.sc03Done),
			Codec.BOOL.optionalFieldOf("sc03Restored", false).forGetter(d -> d.sc03Restored)
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
	/** The in-game day of the last glimpse (D-073), -1 if none yet, and how many there were. */
	private long lastGlimpseDay = -1;
	private int glimpses;
	/** The in-game day the world's one screenshot happened (D-078), -1 if not yet, and whose screenshot it was. */
	private long screenshotDay = -1;
	private @Nullable UUID screenshotBy;
	/** The self-created layer's 03: the world's picture was taken (once per world), and the old one put back. Old files load without. */
	private boolean sc03Done;
	private boolean sc03Restored;

	public EntityData() {
	}

	private EntityData(Optional<GlobalPos> lastPos, long lastDay, int countOnLastDay, String lastVariant, int sightings, int fakes, int stared,
			List<PendingDig> pendingDigs, long lastGlimpseDay, int glimpses, long screenshotDay, Optional<UUID> screenshotBy, boolean sc03Done,
			boolean sc03Restored) {
		this.lastPos = lastPos.orElse(null);
		this.lastDay = lastDay;
		this.countOnLastDay = countOnLastDay;
		this.lastVariant = lastVariant;
		this.sightings = sightings;
		this.fakes = fakes;
		this.stared = stared;
		this.pendingDigs.addAll(pendingDigs);
		this.lastGlimpseDay = lastGlimpseDay;
		this.glimpses = glimpses;
		this.screenshotDay = screenshotDay;
		this.screenshotBy = screenshotBy.orElse(null);
		this.sc03Done = sc03Done;
		this.sc03Restored = sc03Restored;
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

	/** A glimpse on in-game day {@code day} (D-073). Never the surface sightings' place, day or variant. */
	public void recordGlimpse(long day) {
		lastGlimpseDay = day;
		glimpses++;
		setDirty();
	}

	public void recordStared() {
		stared++;
		setDirty();
	}

	/**
	 * The world's one screenshot (D-078), by {@code player} on in-game day {@code day}: recorded only the first time
	 * (true then, false once there is one), and it counts as that day's sighting (the day rule), never for the place
	 * or the variant rule.
	 */
	public boolean recordScreenshot(UUID player, long day) {
		if (screenshotTaken()) {
			return false;
		}
		screenshotDay = Math.max(0L, day);
		screenshotBy = player;
		countDay(day);
		setDirty();
		return true;
	}

	/** Debug only ({@code /a1016 entity screenshot reset}): forgets the world's screenshot, so it can happen again. */
	public void forgetScreenshot() {
		if (screenshotTaken()) {
			screenshotDay = -1;
			screenshotBy = null;
			setDirty();
		}
	}

	/** True once the world's screenshot happened. */
	public boolean screenshotTaken() {
		return screenshotDay >= 0;
	}

	/** The in-game day it happened, -1 if not yet. */
	public long screenshotDay() {
		return screenshotDay;
	}

	/** Whose screenshot it was, empty if none yet. */
	public Optional<UUID> screenshotBy() {
		return Optional.ofNullable(screenshotBy);
	}

	private void recordPlaceAndDay(GlobalPos pos, long day) {
		lastPos = pos;
		countDay(day);
	}

	private void countDay(long day) {
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

	/** The in-game day of the last glimpse, -1 if there was none. */
	public long lastGlimpseDay() {
		return lastGlimpseDay;
	}

	public int glimpses() {
		return glimpses;
	}

	/** 03: the world's picture was taken (once per world). */
	public boolean sc03Done() {
		return sc03Done;
	}

	/** 03: the picture from before was put back. */
	public boolean sc03Restored() {
		return sc03Restored;
	}

	/** 03: the picture was taken (true) or forgotten (false, debug); forgetting also forgets the putting back. */
	public void setSc03Done(boolean done) {
		if (sc03Done != done || !done && sc03Restored) {
			sc03Done = done;
			if (!done) {
				sc03Restored = false;
			}
			setDirty();
		}
	}

	/** 03: the picture from before was put back (once). */
	public void setSc03Restored(boolean restored) {
		if (sc03Restored != restored) {
			sc03Restored = restored;
			setDirty();
		}
	}
}
