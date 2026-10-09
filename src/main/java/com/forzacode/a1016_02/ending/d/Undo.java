package com.forzacode.a1016_02.ending.d;

import java.util.Map;
import java.util.Optional;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Undoing one {@link TraceLedger} entry, for the afterward: which entries stay ({@link #skipReason}), then core's
 * {@code TraceService.undo}, which takes the entry back exactly once and out of view like any edit (REMOVE and
 * REMOVE_STACK through {@code restoreBlock}/{@code restoreStack}, MOVE, CONVERT, MOVE_STACK and sign text by the
 * reverse edit, the old entry closed either way). So nothing is ever put back twice, and nothing is created.
 *
 * <p>What stays: whatever others left (any cause with {@code :left/}, {@code TraceLedger.isLeftByOthers}: lore's
 * builds and the team's stair), still burning's cleared ground ({@code world:still_burning} REMOVE entries), the
 * fragments' own edits ({@code lore:his/F..}) and anything within {@value #FRAGMENT_REACH} blocks of a placed fragment,
 * the crosses, and stacks worn by mobs.
 */
public final class Undo {
	/** The cause of the reverse edits (dropped from the ledger right after; their broken neighbours are kept). */
	public static final String CAUSE = "ending:d/undo";
	/** Entries this close (on every axis) to a placed fragment stay: the fragments need their places. */
	public static final int FRAGMENT_REACH = 2;

	public enum Result {
		/** Undone (the entry is gone from the ledger). */
		DONE,
		/** Never undone (a skip rule). */
		SKIP,
		/** Not now: in view. Try again. */
		WAIT,
		/** Its chunk is not loaded: it waits in its chunk's cluster ({@link ChunkClusters}), which loads it. */
		UNLOADED,
		/** Cannot be undone as things stand (the block was taken or built over). Tried again next pass. */
		BLOCKED
	}

	private Undo() {
	}

	/** Why this entry is never undone, or empty if it should be. */
	public static Optional<String> skipReason(MinecraftServer server, TraceLedger.Entry entry) {
		String cause = entry.cause();
		if (TraceLedger.isLeftByOthers(cause)) {
			// What others left, the team's stair to bedrock included ({@link Stair#CAUSE}).
			return Optional.of("left by others");
		}
		if (cause.startsWith(CAUSE)) {
			return Optional.of("the undo's own");
		}
		if (cause.startsWith("world:still_burning") && entry.kind() == TraceLedger.Kind.REMOVE) {
			return Optional.of("still burning's ground");
		}
		if (cause.startsWith("lore:his/F")) {
			return Optional.of("a fragment's own");
		}
		if (cause.startsWith("accident:cross") || cause.startsWith("world:cross_row")) {
			return Optional.of("the crosses stay");
		}
		if (entry.kind() == TraceLedger.Kind.EQUIP) {
			return Optional.of("worn by a mob");
		}
		if (nearFragment(HerobrineState.get(server).fragmentsPlaced(), entry)) {
			return Optional.of("a fragment needs it");
		}
		return Optional.empty();
	}

	static boolean nearFragment(Map<String, GlobalPos> placed, TraceLedger.Entry entry) {
		for (GlobalPos fragment : placed.values()) {
			if (!fragment.dimension().equals(entry.pos().dimension())) {
				continue;
			}
			if (reach(fragment.pos(), entry.pos().pos()) <= FRAGMENT_REACH || entry.to().map(to -> reach(fragment.pos(), to) <= FRAGMENT_REACH).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	private static int reach(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}

	/**
	 * Undoes one entry if it can now, through core's {@code TraceService.undo} under {@link #CAUSE}. {@code traces} is
	 * core's service ({@code forced()} only in tests and debug). An entry whose chunk (or whose move's other end) is not
	 * loaded is {@link Result#UNLOADED}: nothing is loaded here.
	 */
	public static Result undo(MinecraftServer server, TraceLedger.Entry entry, TraceService traces) {
		if (skipReason(server, entry).isPresent()) {
			return Result.SKIP;
		}
		ServerLevel level = server.getLevel(entry.pos().dimension());
		if (level == null) {
			return Result.BLOCKED;
		}
		return switch (traces.undo(level, entry, CAUSE)) {
			case DONE -> Result.DONE;
			case IN_VIEW -> Result.WAIT;
			case UNLOADED -> Result.UNLOADED;
			case BLOCKED -> Result.BLOCKED;
		};
	}
}
