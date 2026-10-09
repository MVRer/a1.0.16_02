package com.forzacode.a1016_02.accident;

import com.forzacode.a1016_02.core.TraceLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Local stand-ins for core contracts that do not exist yet. Each names the core change the accident workstream
 * asked for; when core ships it, flip the flag and swap the one line in the method.
 */
public final class CoreGaps {
	/**
	 * Requested core opt-in: an edit that removes a support block and lets what it held fall by the game's own
	 * rules. Today {@code TraceService} refuses an edit that leaves a falling block (gravel, sand) without support, and
	 * removes a hanging stalactite as a silent dependent. The gravel ceiling and falling dripstone traps need, for
	 * example, {@code TraceService.removeLettingFall(level, pos, cause)}: the falling blocks above and the
	 * {@code Fallable} dependents (pointed dripstone) are not refused or removed but get their scheduled tick, and the
	 * falling column plus its landing cells are part of the view check. Until then both traps watch but never spring.
	 */
	public static final boolean FALLING_OPT_IN = false;

	/**
	 * Core is adding {@code TraceService.restoreBlock(level, ledgerEntry, toPos)} (feat/core-contracts): it puts a
	 * ledgered removed block back, at its own spot or another, out of view, and settles the ledger so Ending D does not
	 * put it back a second time. The dark corner uses it for its torches once this flag is true.
	 */
	public static final boolean RESTORE_BLOCK = false;

	private CoreGaps() {
	}

	/**
	 * Removes {@code support} and lets the gravel or stalactite it held fall. Always false until core has the opt-in
	 * (then: {@code return Services.traces().removeLettingFall(level, support, cause);}). Never bypasses the refusal.
	 */
	public static boolean removeLettingFall(ServerLevel level, BlockPos support, String cause) {
		return false;
	}

	/**
	 * Puts a ledgered removal back at {@code to}. Always false until core has it (then:
	 * {@code return Services.traces().restoreBlock(level, entry, to);}); callers keep their fallback for that case.
	 */
	public static boolean restoreBlock(ServerLevel level, TraceLedger.Entry entry, BlockPos to) {
		return false;
	}
}
