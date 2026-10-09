package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * Areas his scars and edits must leave alone (the untouched grove, for example). Any workstream may protect an
 * area under its own namespaced id ({@code "lore:untouched_grove"}); world's new-scar placer skips them. Thread-safe
 * (worldgen may read it) and persisted as {@code data/a1016_02/protected.dat}. Areas protected before a world is
 * attached are kept and saved once it is. Reads are one volatile read of an immutable snapshot.
 */
public final class ProtectedAreas {
	/** One protected box, inclusive, in one dimension. */
	public record Area(String id, ResourceKey<Level> dimension, BoundingBox box) {
		static final Codec<Area> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Area::id),
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Area::dimension),
				BoundingBox.CODEC.fieldOf("box").forGetter(Area::box)
		).apply(i, Area::new));
	}

	static final class Data extends SavedData {
		static final Codec<Data> CODEC = Area.CODEC.listOf().fieldOf("areas").codec().xmap(Data::new, Data::snapshot);
		static final SavedDataType<Data> TYPE = new SavedDataType<>(A1016_02.id("protected"), Data::new, CODEC, null);

		final Map<String, Area> areas = new LinkedHashMap<>();
		/** Dirty tracking by version under the lock, as in {@link SiteRegistry}: a change made during a save is kept. */
		private long version;
		private long savedVersion;
		private long snapshotVersion;

		Data() {
		}

		private Data(List<Area> areas) {
			areas.forEach(a -> this.areas.put(a.id(), a));
		}

		List<Area> snapshot() {
			synchronized (LOCK) {
				snapshotVersion = version;
				return List.copyOf(areas.values());
			}
		}

		@Override
		public void setDirty(boolean dirty) {
			synchronized (LOCK) {
				if (dirty) {
					version++;
				} else {
					savedVersion = snapshotVersion;
				}
			}
		}

		@Override
		public boolean isDirty() {
			synchronized (LOCK) {
				return version != savedVersion;
			}
		}
	}

	private static final Object LOCK = new Object();
	private @Nullable Data data;
	private final Map<String, Area> pending = new LinkedHashMap<>();
	private volatile List<Area> snapshot = List.of();

	ProtectedAreas() {
	}

	/** Protects (or moves) the area with this id. Any thread. */
	public void protect(String id, ResourceKey<Level> dimension, BoundingBox box) {
		synchronized (LOCK) {
			Area area = new Area(id, dimension, box);
			if (data != null) {
				data.areas.put(id, area);
				data.setDirty();
			} else {
				pending.put(id, area);
			}
			publish();
		}
	}

	/** Lifts the protection with this id. False if there was none. Any thread. */
	public boolean unprotect(String id) {
		synchronized (LOCK) {
			boolean removed = pending.remove(id) != null;
			if (data != null && data.areas.remove(id) != null) {
				data.setDirty();
				removed = true;
			}
			publish();
			return removed;
		}
	}

	/** True if the block lies in any protected area of that dimension. Any thread, cheap. */
	public boolean isProtected(ResourceKey<Level> dimension, BlockPos pos) {
		for (Area area : snapshot) {
			if (area.dimension().equals(dimension) && area.box().isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	/** True if the box overlaps any protected area of that dimension. Any thread, cheap. */
	public boolean intersects(ResourceKey<Level> dimension, BoundingBox box) {
		for (Area area : snapshot) {
			if (area.dimension().equals(dimension) && area.box().intersects(box)) {
				return true;
			}
		}
		return false;
	}

	public Optional<Area> get(String id) {
		return snapshot.stream().filter(a -> a.id().equals(id)).findFirst();
	}

	/** Every protected area (a snapshot). */
	public List<Area> all() {
		return snapshot;
	}

	/** Called by core when a server starts. */
	void attach(MinecraftServer server) {
		synchronized (LOCK) {
			data = server.getDataStorage().computeIfAbsent(Data.TYPE);
			if (!pending.isEmpty()) {
				data.areas.putAll(pending);
				data.setDirty();
				pending.clear();
			}
			publish();
		}
	}

	/** Called by core when a server stops. */
	void detach() {
		synchronized (LOCK) {
			data = null;
			pending.clear();
			publish();
		}
	}

	private void publish() {
		List<Area> all = new ArrayList<>(data != null ? data.areas.values() : List.of());
		all.addAll(pending.values());
		snapshot = List.copyOf(all);
	}
}
