package com.forzacode.a1016_02.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A block-level veto on every {@link TraceService} edit (lore's F30 signs, Ending D's chamber): registered with
 * {@link TraceService#addVeto}, it is asked about every position an edit would change, including broken
 * dependents, reshaped neighbours, falling cells, container slots, sign text, {@code figureDig/figureFill} and
 * {@code restoreBlock}. One {@code true} refuses the whole edit. Called once per affected block, on the server
 * thread, so keep it cheap (a set lookup) and never load chunks or change the world in it.
 */
@FunctionalInterface
public interface TraceVeto {
	boolean vetoes(ServerLevel level, BlockPos pos);

	/**
	 * The same question with the edit's cause, which TraceService always asks: the ledger cause of the edit
	 * ({@code "<ws>:<what>"}; {@code :left/} for what others left, {@link TraceService#GIVE_BACK_CAUSE} for an edit that
	 * only gives back what a ledger entry took). Default: {@link #vetoes(ServerLevel, BlockPos)}, whatever the cause.
	 */
	default boolean vetoes(ServerLevel level, BlockPos pos, String cause) {
		return vetoes(level, pos);
	}
}
