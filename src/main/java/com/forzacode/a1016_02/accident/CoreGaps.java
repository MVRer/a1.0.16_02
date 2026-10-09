package com.forzacode.a1016_02.accident;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Local stand-ins for core contracts that do not exist yet. Each names the core change the accident workstream
 * asked for; when core ships it, only this class changes.
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

	private CoreGaps() {
	}

	/**
	 * Removes {@code support} and lets the gravel or stalactite it held fall. Always false until core has the opt-in
	 * (then: {@code return Services.traces().removeLettingFall(level, support, cause);}). Never bypasses the refusal.
	 */
	public static boolean removeLettingFall(ServerLevel level, BlockPos support, String cause) {
		return false;
	}
}
