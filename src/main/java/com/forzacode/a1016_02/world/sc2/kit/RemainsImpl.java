package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.forzacode.a1016_02.core.ScLog;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.world.WorldData;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * {@link Remains} over a {@link Sc2Data}: the live one reads the running server's {@code WorldData} ({@link #LIVE},
 * installed by world), a test's reads its own. {@link #containing} goes through a chunk-grid index per level (rebuilt
 * when a place is added): the places whose box touches the position's chunk, each with its interior built once from its
 * kind ({@link Sc2Kind.Interiors}; a kind no longer registered counts its whole box). Server thread only.
 */
public final class RemainsImpl implements Remains {
	/** The live one: the running server's places (nothing before a server starts). */
	public static final RemainsImpl LIVE = new RemainsImpl(null);

	/** One place in the index, its interior built on first use. */
	private static final class Entry {
		final Sc2Data.Known known;
		final BoundingBox box;
		@Nullable Interior interior;

		Entry(Sc2Data.Known known) {
			this.known = known;
			this.box = known.place().box();
		}
	}

	/** One level's places by chunk. */
	private static final class Index {
		final List<Entry> all = new ArrayList<>();
		final Long2ObjectOpenHashMap<List<Entry>> byChunk = new Long2ObjectOpenHashMap<>();
	}

	private final @Nullable Supplier<Sc2Data> fixed;
	private @Nullable MinecraftServer server;
	private @Nullable Sc2Data indexed;
	private int indexedVersion = -1;
	private final Map<ResourceKey<Level>, Index> indexes = new HashMap<>();

	private RemainsImpl(@Nullable Supplier<Sc2Data> fixed) {
		this.fixed = fixed;
	}

	/** A test's own: over this data. */
	public static RemainsImpl over(Sc2Data data) {
		return new RemainsImpl(() -> data);
	}

	/** The live one follows this server (null at stop: nothing is known). */
	public void attach(@Nullable MinecraftServer server) {
		this.server = server;
		this.indexed = null;
		this.indexedVersion = -1;
		this.indexes.clear();
	}

	/** The data this reads now (a fresh empty one with no server: nothing written to it is kept). */
	public Sc2Data data() {
		if (fixed != null) {
			return fixed.get();
		}
		MinecraftServer now = server;
		return now == null ? new Sc2Data() : WorldData.get(now).sc2();
	}

	private Index index(ServerLevel level) {
		Sc2Data data = data();
		if (data != indexed || data.placesVersion() != indexedVersion) {
			// R12-21: an inside already made is kept across a rebuild (a new place does not make every other one again).
			java.util.Map<String, Interior> kept = new java.util.HashMap<>();
			for (Index old : indexes.values()) {
				for (Entry e : old.all) {
					if (e.interior != null) {
						kept.put(e.known.key(), e.interior);
					}
				}
			}
			indexes.clear();
			for (Sc2Data.Known known : data.places()) {
				Index index = indexes.computeIfAbsent(known.place().dimension(), k -> new Index());
				Entry entry = new Entry(known);
				entry.interior = data == indexed ? kept.get(known.key()) : null;
				index.all.add(entry);
				BoundingBox box = entry.box;
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
					for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
						index.byChunk.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(entry);
					}
				}
			}
			indexed = data;
			indexedVersion = data.placesVersion();
		}
		Index index = indexes.get(level.dimension());
		return index == null ? EMPTY_INDEX : index;
	}

	private static final Index EMPTY_INDEX = new Index();

	private Interior interior(ServerLevel level, Entry entry) {
		Interior interior = entry.interior;
		if (interior == null) {
			Sc2Kind kind = Sc2Kind.get(entry.known.kind());
			try {
				interior = kind == null ? Interior.box(entry.box)
						: kind.interiors().of(level, kind, entry.known.place(), entry.known.start(), entry.known.extra());
			} catch (RuntimeException e) {
				ScLog.error("[a1016] s2: {} (i) failed, using its box", entry.known.kind(), e);
				interior = Interior.box(entry.box);
			}
			entry.interior = interior;
		}
		return interior;
	}

	@Override
	public List<Place> known(ServerLevel level) {
		List<Place> out = new ArrayList<>();
		for (Entry entry : index(level).all) {
			out.add(entry.known.place());
		}
		return out;
	}

	/** The record of a known place (its start, extra, visitors and answers), if known. */
	public Optional<Sc2Data.Known> record(Place place) {
		return data().known(place);
	}

	@Override
	public Optional<Place> containing(ServerLevel level, BlockPos pos) {
		List<Entry> here = index(level).byChunk.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
		if (here != null) {
			for (Entry entry : here) {
				if (entry.box.isInside(pos) && interior(level, entry).contains(pos)) {
					return Optional.of(entry.known.place());
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * The place whose open way in is within {@code reach} blocks (horizontally, and 3 up or down) of {@code pos}, the
	 * oldest first; for the visit's way-in rule. Cheap (the chunks around {@code pos}).
	 */
	public Optional<Place> atEntrance(ServerLevel level, BlockPos pos, int reach) {
		Index index = index(level);
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				List<Entry> here = index.byChunk.get(ChunkPos.pack(cx + dx, cz + dz));
				if (here == null) {
					continue;
				}
				for (Entry entry : here) {
					BlockPos mouth = entry.known.place().entrance();
					if (Math.abs(mouth.getX() - pos.getX()) <= reach && Math.abs(mouth.getZ() - pos.getZ()) <= reach && Math.abs(mouth.getY() - pos.getY()) <= 3) {
						return Optional.of(entry.known.place());
					}
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<Place> nearest(ServerLevel level, BlockPos pos, Set<Kind> kinds, int radius) {
		Place best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entry entry : index(level).all) {
			Place place = entry.known.place();
			if (!kinds.contains(place.kind())) {
				continue;
			}
			double distance = place.distanceTo(pos);
			if (distance <= radius && distance < bestDistance) {
				best = place;
				bestDistance = distance;
			}
		}
		return Optional.ofNullable(best);
	}

	@Override
	public boolean visited(UUID player, Place place) {
		return data().visited(player, place);
	}

	@Override
	public long trespassedAt(UUID player) {
		return data().trespassedAt(player);
	}
}
