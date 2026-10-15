package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the ending offers other workstreams, first of all Ending D ({@code ending.d}): read the path, commit D, keep
 * D's own progress, end the story, and who is still in it. The path is also mirrored in {@code HerobrineState} flags
 * as {@code ending:path=<A|B|C|D>} (none while NONE), {@code ending:ended} is set once the story is over, and each
 * finished line as {@code ending:line_finished=<uuid>} (D-063). Server thread only.
 */
public final class EndingApi {
	/**
	 * D-061 owner key: whose first crafting table, first chest or first block the story uses (lore's F13 cairn and F10,
	 * the cairn's first block in Ending D, "Missing first block"). Whoever needs it first chooses it.
	 */
	public static final String FIRST_BLOCKS_OWNER = "story:first_blocks";
	/**
	 * D-061 owner key, chosen by world when the copy starts: whose house the copy elsewhere copies. Ending B empties the
	 * copied house itself ({@code HouseCopyApi.realHouse}); the key only matters while a copy exists. Read only here.
	 */
	public static final String HOUSE_COPY_OWNER = "world:house_copy";
	/**
	 * D-061 owner key, chosen by the ending among the participants who have a base: whose base is "your house" for the
	 * group (Ending C's house taken apart, and Ending B's house when there is no house copy). While its owner is away,
	 * anyone's bigger house far from it becomes the home (D-064).
	 */
	public static final String HOME_OWNER = "ending:home";
	/**
	 * {@code ending:line_finished=<uuid>}: that player's line was finished, by a hardcore marked death (D-063) or by
	 * Ending B's final death inside the copy (the record that ends the story). F10's "* removed" lines name exactly these.
	 */
	public static final String LINE_FINISHED_FLAG = "ending:line_finished=";
	/**
	 * {@code ending:d_step=<n>}: the step Ending D's chain is on (1 to 7, then 8 and 9), published on every advance for
	 * lore's late clues (D-066). Step 1 is published once F23 has been read.
	 */
	public static final String D_STEP_FLAG = "ending:d_step=";
	/**
	 * {@code ending:d_step_day=<day>}: the in-game day (the world clock, {@code GameClock.day}) the chain reached that
	 * step; step 1's clock starts on the day F23 is first read.
	 */
	public static final String D_STEP_DAY_FLAG = "ending:d_step_day=";

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

	/**
	 * True once the story is over, for everyone (D-058): A or B finished, {@link #endStory}, or in hardcore the marked
	 * death that left nobody in it (D-063). One friend's death while others still play never ends it.
	 */
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

	// --- who is in it (D-058, D-063) ---

	/**
	 * Who is in the story right now: every online participant who is alive (targetable) and whose line no hardcore
	 * marked death finished, in first-join order. Their actions count for every ending, and only they can be acted on.
	 * A new list each call: keep UUIDs, never these objects (D-062.1).
	 */
	public static List<ServerPlayer> players(MinecraftServer server) {
		return players(server, Services.watch(), EndingState.get(server));
	}

	/** {@link #players(MinecraftServer)} over a given watch and state (tests). */
	public static List<ServerPlayer> players(MinecraftServer server, PlayerWatch watch, EndingState data) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : watch.participants(server)) {
			if (watch.isTargetable(player) && !data.lineFinished(player.getUUID())) {
				list.add(player);
			}
		}
		return list;
	}

	/**
	 * True if what this player does counts for the story now (any participant whose line is still open, D-058, D-064):
	 * a telling, a read, a throw into lava, a block broken, an ending's step. Dead or alive.
	 */
	public static boolean takesPart(ServerPlayer player) {
		return Services.watch().isParticipant(player) && !EndingState.get(player.level().getServer()).lineFinished(player.getUUID());
	}

	/** True if this player's line is finished: a hardcore marked death (D-063), or Ending B's final death. */
	public static boolean lineFinished(MinecraftServer server, UUID player) {
		return EndingState.get(server).lineFinished(player);
	}

	/** Every player whose line is finished ({@link #lineFinished}), in the order it happened. */
	public static Set<UUID> finishedLines(MinecraftServer server) {
		return EndingState.get(server).finishedLines();
	}
}
