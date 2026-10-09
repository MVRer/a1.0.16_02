package com.forzacode.a1016_02.lore;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Placement only ever reads loaded chunks. A rule asks whether the area it is about to probe is loaded; if not,
 * the missing chunks are queued and loaded by ticket, at most {@link LoreConfig#chunkLoadsPerTick} per tick (the
 * chunk system loads or generates them off the server thread), and the fragment is tried again a moment later.
 */
final class ChunkGate {
	/** Keeps a requested chunk loaded long enough to be probed and edited. */
	static final TicketType TICKET = new TicketType(600L, TicketType.FLAG_LOADING);
	private static final int MAX_QUEUED = 128;

	private record Wanted(ResourceKey<Level> dimension, int x, int z) {
	}

	private static final Deque<Wanted> QUEUE = new ArrayDeque<>();
	private static final Set<Wanted> QUEUED = new HashSet<>();

	private ChunkGate() {
	}

	static boolean loaded(ServerLevel level, int chunkX, int chunkZ) {
		return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
	}

	static boolean loaded(ServerLevel level, BlockPos pos) {
		return loaded(level, pos.getX() >> 4, pos.getZ() >> 4);
	}

	/**
	 * True if every chunk under the horizontal box is loaded. Otherwise queues the missing ones, marks the request
	 * as waiting for chunks, and returns false: the caller must not read the area now.
	 */
	static boolean ready(Placing.Request req, int minX, int minZ, int maxX, int maxZ) {
		ServerLevel level = req.level();
		boolean ready = true;
		for (int cx = Math.min(minX, maxX) >> 4; cx <= Math.max(minX, maxX) >> 4; cx++) {
			for (int cz = Math.min(minZ, maxZ) >> 4; cz <= Math.max(minZ, maxZ) >> 4; cz++) {
				if (!loaded(level, cx, cz)) {
					ready = false;
					want(level, cx, cz);
				}
			}
		}
		if (!ready) {
			req.loads().waiting = true;
		}
		return ready;
	}

	/** {@link #ready} for the square of {@code radius} blocks around {@code center}. */
	static boolean ready(Placing.Request req, BlockPos center, int radius) {
		return ready(req, center.getX() - radius, center.getZ() - radius, center.getX() + radius, center.getZ() + radius);
	}

	/** {@link #ready} for the box between two corners, grown by {@code margin}. */
	static boolean ready(Placing.Request req, BlockPos a, BlockPos b, int margin) {
		return ready(req, Math.min(a.getX(), b.getX()) - margin, Math.min(a.getZ(), b.getZ()) - margin,
				Math.max(a.getX(), b.getX()) + margin, Math.max(a.getZ(), b.getZ()) + margin);
	}

	private static synchronized void want(ServerLevel level, int x, int z) {
		Wanted wanted = new Wanted(level.dimension(), x, z);
		if (QUEUED.size() < MAX_QUEUED && QUEUED.add(wanted)) {
			QUEUE.add(wanted);
		}
	}

	/** Every tick: asks for the next few queued chunks by ticket. */
	static synchronized void tick(MinecraftServer server) {
		int budget = Math.max(1, LoreConfig.get().chunkLoadsPerTick);
		while (budget > 0 && !QUEUE.isEmpty()) {
			Wanted wanted = QUEUE.poll();
			QUEUED.remove(wanted);
			ServerLevel level = server.getLevel(wanted.dimension());
			if (level == null || loaded(level, wanted.x(), wanted.z())) {
				continue;
			}
			level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(wanted.x(), wanted.z()), 0);
			budget--;
		}
	}

	static synchronized void clear() {
		QUEUE.clear();
		QUEUED.clear();
	}

	/** Chunks waiting for a ticket. Tests and debug. */
	static synchronized int queued() {
		return QUEUE.size();
	}
}
