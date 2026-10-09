package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.forzacode.a1016_02.core.TraceLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * The undo's far entries, grouped by chunk. An entry whose chunk is not loaded joins its chunk's cluster; the cluster
 * holds a loading ticket (renewed before it runs out) until every entry in it is undone or can never be, or until a
 * bounded timeout, and its entries are tried every tick while the chunk is loaded. So a far chunk is loaded once for
 * all the entries in it, and stays loaded while they are worked through. Server thread only.
 */
final class ChunkClusters {
	/** Loads a chunk for the undo. Its own timeout, so it is never mistaken for another workstream's ticket. */
	static final TicketType TICKET = new TicketType(1201L, TicketType.FLAG_LOADING);

	/** Where the clusters' chunks are loaded (the server; fixed answers in tests). */
	interface Chunks {
		boolean loaded(ResourceKey<Level> dimension, ChunkPos chunk);

		void hold(ResourceKey<Level> dimension, ChunkPos chunk);

		void release(ResourceKey<Level> dimension, ChunkPos chunk);

		static Chunks of(MinecraftServer server) {
			return new Chunks() {
				@Override
				public boolean loaded(ResourceKey<Level> dimension, ChunkPos chunk) {
					ServerLevel level = server.getLevel(dimension);
					return level != null && level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null;
				}

				@Override
				public void hold(ResourceKey<Level> dimension, ChunkPos chunk) {
					ServerLevel level = server.getLevel(dimension);
					if (level != null) {
						level.getChunkSource().addTicketWithRadius(TICKET, chunk, 0);
					}
				}

				@Override
				public void release(ResourceKey<Level> dimension, ChunkPos chunk) {
					ServerLevel level = server.getLevel(dimension);
					if (level != null) {
						level.getChunkSource().removeTicketWithRadius(TICKET, chunk, 0);
					}
				}
			};
		}
	}

	private record Key(ResourceKey<Level> dimension, ChunkPos chunk) {
	}

	private static final class Cluster {
		final Key key;
		final Set<ChunkPos> chunks = new LinkedHashSet<>();
		final List<TraceLedger.Entry> entries = new ArrayList<>();
		final long since;
		long heldAt = -1;

		Cluster(Key key, long since) {
			this.key = key;
			this.since = since;
		}
	}

	/** What one tick did. */
	record Tick(int undone, int dropped, int timedOut) {
	}

	private final Map<Key, Cluster> clusters = new LinkedHashMap<>();
	private final Set<TraceLedger.Entry> pending = new HashSet<>();
	private final int maxClusters;

	ChunkClusters(int maxClusters) {
		this.maxClusters = maxClusters;
	}

	boolean contains(TraceLedger.Entry entry) {
		return pending.contains(entry);
	}

	boolean isEmpty() {
		return clusters.isEmpty();
	}

	int size() {
		return clusters.size();
	}

	int entries() {
		return pending.size();
	}

	/** The chunks an entry touches: where it happened, and where a move went. */
	static List<ChunkPos> chunksOf(TraceLedger.Entry entry) {
		List<ChunkPos> chunks = new ArrayList<>();
		chunks.add(ChunkPos.containing(entry.pos().pos()));
		entry.to().map(ChunkPos::containing).filter(c -> !chunks.contains(c)).ifPresent(chunks::add);
		return chunks;
	}

	/** Adds an entry that waits for its chunk. False if it is already waiting or there are too many clusters. */
	boolean add(TraceLedger.Entry entry, long now) {
		if (pending.contains(entry)) {
			return false;
		}
		List<ChunkPos> chunks = chunksOf(entry);
		Key key = new Key(entry.pos().dimension(), chunks.getFirst());
		Cluster cluster = clusters.get(key);
		if (cluster == null) {
			if (clusters.size() >= maxClusters) {
				return false;
			}
			cluster = new Cluster(key, now);
			clusters.put(key, cluster);
		}
		cluster.chunks.addAll(chunks);
		cluster.entries.add(entry);
		pending.add(entry);
		return true;
	}

	/**
	 * One tick: new clusters get their tickets (at most {@code newHolds}), held ones are renewed every
	 * {@code renewTicks}, every loaded cluster's entries are tried (at most {@code budget}), and a cluster is let go
	 * (its tickets removed) once it is empty or older than {@code timeoutTicks}. Entries of a cluster that timed out are
	 * left in the ledger for a later pass.
	 */
	Tick tick(long now, int newHolds, long renewTicks, long timeoutTicks, int budget, Chunks chunks, Function<TraceLedger.Entry, Undo.Result> undo) {
		int undone = 0;
		int dropped = 0;
		int timedOut = 0;
		int holds = newHolds;
		for (Iterator<Cluster> it = clusters.values().iterator(); it.hasNext();) {
			Cluster cluster = it.next();
			if (now - cluster.since > timeoutTicks) {
				let(cluster, chunks);
				it.remove();
				timedOut++;
				continue;
			}
			if (cluster.heldAt < 0) {
				if (holds <= 0) {
					continue;
				}
				holds--;
				hold(cluster, chunks, now);
			} else if (now - cluster.heldAt >= renewTicks) {
				hold(cluster, chunks, now);
			}
			boolean ready = cluster.chunks.stream().allMatch(c -> chunks.loaded(cluster.key.dimension(), c));
			if (!ready) {
				continue;
			}
			for (Iterator<TraceLedger.Entry> entries = cluster.entries.iterator(); entries.hasNext() && budget > 0;) {
				TraceLedger.Entry entry = entries.next();
				budget--;
				Undo.Result result = undo.apply(entry);
				if (result == Undo.Result.DONE || result == Undo.Result.SKIP || result == Undo.Result.BLOCKED) {
					entries.remove();
					pending.remove(entry);
					if (result == Undo.Result.DONE) {
						undone++;
					} else {
						dropped++;
					}
				}
			}
			if (cluster.entries.isEmpty()) {
				let(cluster, chunks);
				it.remove();
			}
		}
		return new Tick(undone, dropped, timedOut);
	}

	private static void hold(Cluster cluster, Chunks chunks, long now) {
		cluster.chunks.forEach(c -> chunks.hold(cluster.key.dimension(), c));
		cluster.heldAt = now;
	}

	private void let(Cluster cluster, Chunks chunks) {
		if (cluster.heldAt >= 0) {
			cluster.chunks.forEach(c -> chunks.release(cluster.key.dimension(), c));
		}
		cluster.entries.forEach(pending::remove);
	}

	/** Lets every cluster go (server stop). */
	void clear(Chunks chunks) {
		clusters.values().forEach(c -> let(c, chunks));
		clusters.clear();
		pending.clear();
	}

	/** Chunk of a position (for callers that only have a block). */
	static ChunkPos chunk(BlockPos pos) {
		return ChunkPos.containing(pos);
	}
}
