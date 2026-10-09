package com.forzacode.a1016_02.ending;

import net.minecraft.server.MinecraftServer;

/**
 * What the ending offers other workstreams, first of all Ending D ({@code ending.d}): read the path, commit D, keep
 * D's own progress, and end the story. The path is also mirrored in {@code HerobrineState} flags as
 * {@code ending:path=<A|B|C|D>} (none while NONE), and {@code ending:ended} is set once the story is over.
 * Server thread only.
 */
public final class EndingApi {
	private EndingApi() {
	}

	/** The path the run is on ({@link EndingPath#NONE} until one commits). */
	public static EndingPath path(MinecraftServer server) {
		return EndingState.get(server).path();
	}

	/** A path's beat (0 if it never started). For D: whatever D stores with {@link #setProgress}. */
	public static int progress(MinecraftServer server, EndingPath path) {
		return EndingState.get(server).progress(path);
	}

	/** Stores a path's beat. Meant for D's own steps; A, B and C keep their own. */
	public static void setProgress(MinecraftServer server, EndingPath path, int beat) {
		EndingState.get(server).setProgress(path, beat, EndingAbcInit.now(server));
	}

	/** True once the story is over (A or B finished, any marked death in hardcore, or {@link #endStory}). */
	public static boolean ended(MinecraftServer server) {
		return EndingState.get(server).ended();
	}

	/**
	 * Ending D commits: the path becomes D, Stage 4 begins (D-006), and whatever A, B or C was doing stops (their
	 * director flags are cleared, the doorway's mobs are let go, C's clear dusk goes back to Stage 4's). False if the
	 * story already ended or the path is already D. A third marked death still commits B whatever the path.
	 */
	public static boolean commitD(MinecraftServer server, String reason) {
		EndingState data = EndingState.get(server);
		if (data.ended() || data.path() == EndingPath.D) {
			return false;
		}
		EndingAbcInit.engine().commit(EndingAbcInit.ctx(server, false), EndingPath.D, reason);
		return true;
	}

	/**
	 * Ends the story (for D's afterward): nothing more is armed, the pace multiplier ends and, unless the config says
	 * otherwise, the director stays silent for good. Does nothing if it already ended.
	 */
	public static void endStory(MinecraftServer server, String why) {
		if (!EndingState.get(server).ended()) {
			EndingAbcInit.engine().endStory(EndingAbcInit.ctx(server, false), why);
		}
	}

	/** The ending's saved data (read it; change it through this API). */
	public static EndingState state(MinecraftServer server) {
		return EndingState.get(server);
	}
}
