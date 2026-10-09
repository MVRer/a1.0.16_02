package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;

import net.minecraft.server.MinecraftServer;

/**
 * Atmosphere's small server-side timeline: delayed one-shot actions (a chest closing a moment after it opened) and
 * episodes that run every tick until they end (animals facing the fog until the player walks toward the point).
 * Everything is in memory and dropped when the server stops. Server thread only.
 */
public final class Tasks {
	/** Something that runs every server tick until it ends. */
	public interface Episode {
		/** Returns false when done. */
		boolean tick(MinecraftServer server);

		/** The server is stopping: undo whatever is still active. */
		default void stop(MinecraftServer server) {
		}
	}

	private record Delayed(long at, Runnable action) {
	}

	private static final List<Delayed> DELAYED = new ArrayList<>();
	private static final List<Episode> EPISODES = new ArrayList<>();
	private static long now;

	private Tasks() {
	}

	/** Runs {@code action} after {@code ticks} server ticks (0 = next tick). */
	public static void later(int ticks, Runnable action) {
		DELAYED.add(new Delayed(now + Math.max(1, ticks), action));
	}

	public static void start(Episode episode) {
		EPISODES.add(episode);
	}

	public static int episodeCount() {
		return EPISODES.size();
	}

	static void tick(MinecraftServer server) {
		now++;
		if (!DELAYED.isEmpty()) {
			List<Delayed> due = new ArrayList<>();
			for (Iterator<Delayed> it = DELAYED.iterator(); it.hasNext(); ) {
				Delayed delayed = it.next();
				if (delayed.at() <= now) {
					due.add(delayed);
					it.remove();
				}
			}
			for (Delayed delayed : due) {
				run(delayed.action());
			}
		}
		if (!EPISODES.isEmpty()) {
			for (Episode episode : new ArrayList<>(EPISODES)) {
				boolean keep;
				try {
					keep = episode.tick(server);
				} catch (RuntimeException e) {
					A1016_02.LOGGER.error("[a1016] atmosphere episode failed, stopping it", e);
					safeStop(episode, server);
					keep = false;
				}
				if (!keep) {
					EPISODES.remove(episode);
				}
			}
		}
	}

	static void clear(MinecraftServer server) {
		for (Episode episode : new ArrayList<>(EPISODES)) {
			safeStop(episode, server);
		}
		EPISODES.clear();
		DELAYED.clear();
	}

	private static void run(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] atmosphere delayed action failed", e);
		}
	}

	private static void safeStop(Episode episode, MinecraftServer server) {
		try {
			episode.stop(server);
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] atmosphere episode stop failed", e);
		}
	}
}
