package com.forzacode.a1016_02.world.sig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.world.live.NewScarPlacer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Loads chunks in the background for the signatures (never synchronously): loading tickets that do not tick the
 * chunks (a furnace loaded this way does not burn) and expire on their own a minute later. Server thread only.
 */
public final class ChunkLoads {
	/** Tickets asked for per call, so a far camp does not load a whole area in one tick. */
	private static final int PER_CALL = 4;

	private ChunkLoads() {
	}

	/** The chunks a box touches, plus {@code margin} chunks around it. */
	public static List<ChunkPos> around(BoundingBox box, int margin) {
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		for (int cx = (box.minX() >> 4) - margin; cx <= (box.maxX() >> 4) + margin; cx++) {
			for (int cz = (box.minZ() >> 4) - margin; cz <= (box.maxZ() >> 4) + margin; cz++) {
				chunks.add(new ChunkPos(cx, cz));
			}
		}
		return new ArrayList<>(chunks);
	}

	/** True if every chunk is loaded now. */
	public static boolean ready(ServerLevel level, List<ChunkPos> chunks) {
		for (ChunkPos chunk : chunks) {
			if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
				return false;
			}
		}
		return true;
	}

	/** Asks for a few of the chunks that are not loaded yet; true if all of them already are. */
	public static boolean request(ServerLevel level, List<ChunkPos> chunks) {
		int asked = 0;
		boolean ready = true;
		for (ChunkPos chunk : chunks) {
			if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null) {
				continue;
			}
			ready = false;
			if (asked++ < PER_CALL) {
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, chunk, 0);
			}
		}
		return ready;
	}

	/** True if none of the chunks is loaded. */
	public static boolean noneLoaded(ServerLevel level, List<ChunkPos> chunks) {
		for (ChunkPos chunk : chunks) {
			if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null) {
				return false;
			}
		}
		return true;
	}
}
