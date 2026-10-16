package com.forzacode.a1016_02.world.sc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;

/**
 * What the layer's world features remember, kept in {@code WorldData} under the optional key {@code sc}: per base
 * (its position, as {@code PlayerWatch.base} gives it) how many of its blocks turned (08) and tracks came to it
 * (09) with the day of the last of each, and the trails left in the world (11, once per world by default). Recorded
 * in the tick it happens, so two players' cards in one tick see it. Every field is optional and read leniently: a
 * save from before the layer loads with nothing, a v1.3 jar skips the key, and a part that cannot be read loads as
 * nothing rather than failing the file. Server thread only; every change marks the data dirty.
 */
public final class ScMarks {
	/** No day yet. */
	public static final long NEVER = Long.MIN_VALUE;

	/** One base's marks. */
	public record Base(int turned, long turnedDay, int tracks, long tracksDay) {
		public static final Base NONE = new Base(0, NEVER, 0, NEVER);

		static final Codec<Base> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("turned", 0).forGetter(Base::turned),
				Codec.LONG.optionalFieldOf("turnedDay", NEVER).forGetter(Base::turnedDay),
				Codec.INT.optionalFieldOf("tracks", 0).forGetter(Base::tracks),
				Codec.LONG.optionalFieldOf("tracksDay", NEVER).forGetter(Base::tracksDay)
		).apply(i, Base::new));
	}

	/** Where a trail ends (its shaft) and the in-game day it was left. */
	public record Trail(GlobalPos pos, long day) {
		static final Codec<Trail> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(Trail::pos),
				Codec.LONG.fieldOf("day").forGetter(Trail::day)
		).apply(i, Trail::new));
	}

	private record Entry(GlobalPos base, Base marks) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("base").forGetter(Entry::base),
				Base.CODEC.fieldOf("marks").forGetter(Entry::marks)
		).apply(i, Entry::new));
	}

	public static final Codec<ScMarks> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().lenientOptionalFieldOf("bases", List.of()).forGetter(m -> m.bases.entrySet().stream()
					.map(e -> new Entry(e.getKey(), e.getValue())).toList()),
			Trail.CODEC.listOf().lenientOptionalFieldOf("trails", List.of()).forGetter(m -> List.copyOf(m.trails))
	).apply(i, ScMarks::new));

	private final Map<GlobalPos, Base> bases = new LinkedHashMap<>();
	private final List<Trail> trails = new ArrayList<>();
	private Runnable dirty = () -> {
	};

	/** Nothing remembered yet (tests use this too). */
	public ScMarks() {
	}

	private ScMarks(List<Entry> bases, List<Trail> trails) {
		bases.forEach(e -> this.bases.put(e.base(), e.marks()));
		this.trails.addAll(trails);
	}

	/** Called by {@code WorldData}: what marks it dirty. */
	public void onChange(Runnable markDirty) {
		this.dirty = markDirty;
	}

	/** True if nothing is remembered (the key is then not written). */
	public boolean isEmpty() {
		return bases.isEmpty() && trails.isEmpty();
	}

	/** This base's marks ({@link Base#NONE} if none). */
	public Base base(GlobalPos base) {
		return bases.getOrDefault(base, Base.NONE);
	}

	/** {@code count} more of this base's blocks turned today. */
	public void turned(GlobalPos base, long day, int count) {
		Base now = base(base);
		bases.put(base, new Base(now.turned() + count, day, now.tracks(), now.tracksDay()));
		dirty.run();
	}

	/** Tracks came to this base today. */
	public void tracked(GlobalPos base, long day) {
		Base now = base(base);
		bases.put(base, new Base(now.turned(), now.turnedDay(), now.tracks() + 1, day));
		dirty.run();
	}

	/** The trails left in this world, oldest first. */
	public List<Trail> trails() {
		return List.copyOf(trails);
	}

	public void addTrail(Trail trail) {
		trails.add(trail);
		dirty.run();
	}

	/** Debug: forgets every trail (what was left stays in the world). */
	public void forgetTrails() {
		if (!trails.isEmpty()) {
			trails.clear();
			dirty.run();
		}
	}

	/** Debug: forgets this base's marks. */
	public void forget(GlobalPos base) {
		if (bases.remove(base) != null) {
			dirty.run();
		}
	}
}
