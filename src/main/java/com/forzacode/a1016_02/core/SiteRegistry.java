package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * Places where world and dig built things that lore can fill. Thread-safe: worldgen features may call
 * {@link #record} from worker threads. Persisted as {@code data/a1016_02/sites.dat}; records made before the
 * world's data is attached are kept and saved once it is.
 */
public final class SiteRegistry {
	/**
	 * A recorded place.
	 *
	 * @param id        unique within the world
	 * @param size      rough half-extent in blocks
	 * @param claimedBy the fragment id that filled it, if any
	 */
	public record Site(int id, SiteType type, ResourceKey<Level> dimension, BlockPos pos, int size, Optional<String> claimedBy) {
		static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("id").forGetter(Site::id),
				CoreCodecs.enumCodec(SiteType.class).fieldOf("type").forGetter(Site::type),
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Site::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Site::pos),
				Codec.INT.fieldOf("size").forGetter(Site::size),
				Codec.STRING.optionalFieldOf("claimedBy").forGetter(Site::claimedBy)
		).apply(i, Site::new));

		public boolean claimed() {
			return claimedBy.isPresent();
		}

		public GlobalPos globalPos() {
			return GlobalPos.of(dimension, pos);
		}
	}

	static final class Data extends SavedData {
		static final Codec<Data> CODEC = Site.CODEC.listOf().fieldOf("sites").codec().xmap(Data::new, Data::snapshot);
		static final SavedDataType<Data> TYPE = new SavedDataType<>(A1016_02.id("sites"), Data::new, CODEC, null);

		final List<Site> sites = new ArrayList<>();

		Data() {
		}

		private Data(List<Site> sites) {
			this.sites.addAll(sites);
		}

		private List<Site> snapshot() {
			synchronized (LOCK) {
				return List.copyOf(sites);
			}
		}
	}

	private static final Object LOCK = new Object();
	private @Nullable Data data;
	private final List<Site> pending = new ArrayList<>();
	private int nextId = 1;

	SiteRegistry() {
	}

	/** Records a site. Safe from any thread. */
	public Site record(SiteType type, ResourceKey<Level> dimension, BlockPos pos, int size) {
		synchronized (LOCK) {
			List<Site> target = data != null ? data.sites : pending;
			Site site = new Site(nextId++, type, dimension, pos.immutable(), size, Optional.empty());
			target.add(site);
			if (data != null) {
				data.setDirty();
			}
			return site;
		}
	}

	/** Sites of this type within {@code radius} blocks (horizontal) of {@code near}, nearest first. */
	public List<Site> find(SiteType type, GlobalPos near, int radius) {
		long radiusSqr = (long) radius * radius;
		synchronized (LOCK) {
			return all().stream()
					.filter(s -> s.type() == type && s.dimension().equals(near.dimension()) && horizontalDistSqr(s.pos(), near.pos()) <= radiusSqr)
					.sorted(Comparator.comparingLong(s -> horizontalDistSqr(s.pos(), near.pos())))
					.toList();
		}
	}

	/** Like {@link #find} but only sites no fragment has claimed yet. */
	public List<Site> findUnclaimed(SiteType type, GlobalPos near, int radius) {
		return find(type, near, radius).stream().filter(s -> !s.claimed()).toList();
	}

	/** Marks a site as filled by a fragment. False if it is unknown or already claimed. */
	public boolean claim(Site site, String fragmentId) {
		synchronized (LOCK) {
			List<Site> list = data != null ? data.sites : pending;
			for (int n = 0; n < list.size(); n++) {
				Site current = list.get(n);
				if (current.id() == site.id()) {
					if (current.claimed()) {
						return false;
					}
					list.set(n, new Site(current.id(), current.type(), current.dimension(), current.pos(), current.size(), Optional.of(fragmentId)));
					if (data != null) {
						data.setDirty();
					}
					return true;
				}
			}
			return false;
		}
	}

	/** A snapshot of every site. */
	public List<Site> all() {
		synchronized (LOCK) {
			List<Site> copy = new ArrayList<>(data != null ? data.sites : List.of());
			copy.addAll(pending);
			return copy;
		}
	}

	/** Called by core when a server starts. */
	void attach(MinecraftServer server) {
		synchronized (LOCK) {
			data = server.getDataStorage().computeIfAbsent(Data.TYPE);
			nextId = data.sites.stream().mapToInt(Site::id).max().orElse(0) + 1;
			for (Site site : pending) {
				data.sites.add(new Site(nextId++, site.type(), site.dimension(), site.pos(), site.size(), site.claimedBy()));
			}
			if (!pending.isEmpty()) {
				data.setDirty();
			}
			pending.clear();
		}
	}

	/** Called by core when a server stops. */
	void detach() {
		synchronized (LOCK) {
			data = null;
			pending.clear();
			nextId = 1;
		}
	}

	private static long horizontalDistSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
