package com.forzacode.a1016_02.director.sc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * One player's places (docs/SELF_CREATED.md, 01), kept in their {@code PacingMemory}: where their real fires and
 * the fires they felt happened, at most {@code maxPlaces}, the oldest out; and their dwell, the play ticks they spent in
 * each chunk, halved every hour of their play, at most {@link #DWELL_CHUNKS} chunks (the least dwelt one out). Plain
 * data; {@link Places} reads it. Saved inside the pacing (under {@code sc01}, written only when there is something to
 * keep); a pacing saved without it loads with none, and v1.3 skips the key.
 */
public final class PlaceMemory {
	/** At most this many chunks of dwell are kept (cost). */
	public static final int DWELL_CHUNKS = 64;

	/**
	 * Where something happened to the player.
	 *
	 * @param playTicks their own clock at the fire
	 * @param dayTicks  the in-game day ticks at the fire (the weight decays on them)
	 * @param sighting  the card had the SIGHTING tag
	 */
	public record Place(ResourceKey<Level> dimension, BlockPos pos, long playTicks, long dayTicks, Tier tier, boolean sighting) {
		public long day() {
			return Math.floorDiv(dayTicks, 24000L);
		}
	}

	/** A chunk of one dimension. */
	public record Chunk(ResourceKey<Level> dimension, int x, int z) {
		public static Chunk of(ResourceKey<Level> dimension, BlockPos pos) {
			return new Chunk(dimension, pos.getX() >> 4, pos.getZ() >> 4);
		}

		public boolean contains(ResourceKey<Level> level, BlockPos pos) {
			return dimension.equals(level) && pos.getX() >> 4 == x && pos.getZ() >> 4 == z;
		}

		public double centerX() {
			return (x << 4) + 8.0;
		}

		public double centerZ() {
			return (z << 4) + 8.0;
		}
	}

	/** A debug trust ({@code /a1016 sc 01 trust}): this chunk is the trusted one until this play tick of the player's. */
	public record Trust(Chunk chunk, long untilPlayTicks) {
	}

	final ArrayDeque<Place> places = new ArrayDeque<>();
	final LinkedHashMap<Chunk, Double> dwell = new LinkedHashMap<>();
	/** The player's play hour at the last halving, or -1 before the first sample. */
	long dwellHour = -1;
	/** The player's clock at the last dwell sample, or -1. */
	long dwellLast = -1;
	@Nullable Trust trust;

	/** The places, oldest first. */
	public List<Place> places() {
		return List.copyOf(places);
	}

	/** Dwell ticks per chunk (unmodifiable). */
	public Map<Chunk, Double> dwell() {
		return Collections.unmodifiableMap(dwell);
	}

	public Optional<Trust> trust() {
		return Optional.ofNullable(trust);
	}

	public void setTrust(@Nullable Trust trust) {
		this.trust = trust;
	}

	public boolean isEmpty() {
		return places.isEmpty() && dwell.isEmpty() && trust == null && dwellHour < 0;
	}

	/** Remembers a place, the oldest going once there are more than {@code max}. */
	public void record(Place place, int max) {
		places.addLast(place);
		while (places.size() > Math.max(1, max)) {
			places.removeFirst();
		}
	}

	/**
	 * One director tick of the player's play in this chunk: every chunk's dwell halves once per hour of their play that
	 * began since the last sample, then this chunk gains the play since the last sample (at most two intervals: a gap is
	 * play elsewhere or none). A new chunk past {@link #DWELL_CHUNKS} pushes out the least dwelt one.
	 */
	public void sampleDwell(Chunk chunk, long playTicks, long interval, long hourTicks) {
		long hour = Math.floorDiv(playTicks, Math.max(1, hourTicks));
		if (dwellHour >= 0 && hour > dwellHour) {
			double factor = Math.pow(0.5, Math.min(64, hour - dwellHour));
			dwell.replaceAll((c, ticks) -> ticks * factor);
			dwell.values().removeIf(ticks -> ticks < 1);
		}
		dwellHour = Math.max(dwellHour, hour);
		long dt = dwellLast < 0 || playTicks < dwellLast ? interval : Math.min(playTicks - dwellLast, 2 * interval);
		dwellLast = playTicks;
		if (dt <= 0) {
			return;
		}
		if (!dwell.containsKey(chunk) && dwell.size() >= DWELL_CHUNKS) {
			Chunk least = null;
			double leastTicks = Double.MAX_VALUE;
			for (Map.Entry<Chunk, Double> entry : dwell.entrySet()) {
				if (entry.getValue() < leastTicks) {
					least = entry.getKey();
					leastTicks = entry.getValue();
				}
			}
			dwell.remove(least);
		}
		dwell.merge(chunk, (double) dt, Double::sum);
	}

	/** A deep copy. */
	public PlaceMemory copy() {
		PlaceMemory copy = new PlaceMemory();
		copy.load(save());
		return copy;
	}

	// --- storage ---

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Place place : places) {
			CompoundTag p = new CompoundTag();
			p.putString("dim", place.dimension().identifier().toString());
			p.putLong("pos", place.pos().asLong());
			p.putLong("play", place.playTicks());
			p.putLong("day", place.dayTicks());
			p.putString("tier", place.tier().name());
			p.putBoolean("sighting", place.sighting());
			list.add(p);
		}
		tag.put("places", list);
		ListTag chunks = new ListTag();
		dwell.forEach((chunk, ticks) -> {
			CompoundTag c = new CompoundTag();
			c.putString("dim", chunk.dimension().identifier().toString());
			c.putInt("x", chunk.x());
			c.putInt("z", chunk.z());
			c.putDouble("ticks", ticks);
			chunks.add(c);
		});
		tag.put("dwell", chunks);
		tag.putLong("dwellHour", dwellHour);
		tag.putLong("dwellLast", dwellLast);
		if (trust != null) {
			CompoundTag t = new CompoundTag();
			t.putString("dim", trust.chunk().dimension().identifier().toString());
			t.putInt("x", trust.chunk().x());
			t.putInt("z", trust.chunk().z());
			t.putLong("until", trust.untilPlayTicks());
			tag.put("trust", t);
		}
		return tag;
	}

	/** Reads what {@link #save} wrote, leniently: an entry that cannot be read is skipped; a missing tag is nothing. */
	public void load(CompoundTag tag) {
		places.clear();
		dwell.clear();
		trust = null;
		ListTag list = tag.getListOrEmpty("places");
		List<Place> read = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag p = list.getCompoundOrEmpty(i);
			ResourceKey<Level> dimension = dimension(p.getStringOr("dim", ""));
			Tier tier = tier(p.getStringOr("tier", ""));
			if (dimension != null && tier != null && p.contains("pos")) {
				read.add(new Place(dimension, BlockPos.of(p.getLongOr("pos", 0L)), p.getLongOr("play", 0L), p.getLongOr("day", 0L), tier,
						p.getBooleanOr("sighting", false)));
			}
		}
		places.addAll(read);
		ListTag chunks = tag.getListOrEmpty("dwell");
		for (int i = 0; i < chunks.size() && dwell.size() < DWELL_CHUNKS; i++) {
			CompoundTag c = chunks.getCompoundOrEmpty(i);
			ResourceKey<Level> dimension = dimension(c.getStringOr("dim", ""));
			double ticks = c.getDoubleOr("ticks", 0);
			if (dimension != null && ticks > 0 && Double.isFinite(ticks)) {
				dwell.put(new Chunk(dimension, c.getIntOr("x", 0), c.getIntOr("z", 0)), ticks);
			}
		}
		dwellHour = tag.getLongOr("dwellHour", -1L);
		dwellLast = tag.getLongOr("dwellLast", -1L);
		tag.getCompound("trust").ifPresent(t -> {
			ResourceKey<Level> dimension = dimension(t.getStringOr("dim", ""));
			if (dimension != null) {
				trust = new Trust(new Chunk(dimension, t.getIntOr("x", 0), t.getIntOr("z", 0)), t.getLongOr("until", 0L));
			}
		});
	}

	private static @Nullable ResourceKey<Level> dimension(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null || id.isEmpty() ? null : ResourceKey.create(Registries.DIMENSION, parsed);
	}

	private static @Nullable Tier tier(String name) {
		try {
			return Tier.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
