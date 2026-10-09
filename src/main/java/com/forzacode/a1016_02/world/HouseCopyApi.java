package com.forzacode.a1016_02.world;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.world.sig.HouseCopier;
import com.forzacode.a1016_02.world.sig.HouseCopyState;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

/**
 * The copy of the subject's first shelter ("Your house, elsewhere", D-005), for the ending workstream. Every block
 * in the copy was moved there through {@code TraceService.move}: from the real house (ledger cause
 * {@value #CAUSE}) or, once {@link #finish} ran, buried blocks from the ground near the copy ({@value #CAUSE_LOCAL}).
 * Ending D's undo moves them back from the ledger; {@link #moved} lists the same moves. Server thread only.
 */
public final class HouseCopyApi {
	/** Ledger cause of the blocks moved out of the real house. */
	public static final String CAUSE = HouseCopier.CAUSE;
	/** Ledger cause of the buried blocks the finish moved from near the copy. */
	public static final String CAUSE_LOCAL = HouseCopier.CAUSE_LOCAL;

	private HouseCopyApi() {
	}

	/** True if this world has a house copy (begun, growing or finished). */
	public static boolean exists(MinecraftServer server) {
		return WorldData.get(server).signatures().houseCopy().isPresent();
	}

	/**
	 * Ending B: "the copy elsewhere gets finished". The copy completes in the background as soon as it is out of view
	 * (the real house keeps its roof and stays closed; what it cannot give comes from buried ground near the copy).
	 * False if this world has no copy (D-005: only with the signature or F27, after day 20).
	 */
	public static boolean finish(MinecraftServer server) {
		return HouseCopier.requestFinish(server);
	}

	/** True once nothing is left to move into the copy. */
	public static boolean finished(MinecraftServer server) {
		return WorldData.get(server).signatures().houseCopy().map(s -> s.phase() == HouseCopyState.Phase.FINISHED).orElse(false);
	}

	/** The middle of the copy (where Ending B's final death happens), once its site is picked. */
	public static Optional<GlobalPos> site(MinecraftServer server) {
		return HouseCopier.site(server);
	}

	/** Every block moved into the copy so far, oldest first: from, to, the block, and whether it came from near the copy. */
	public static List<HouseCopyState.Moved> moved(MinecraftServer server) {
		return HouseCopier.moved(server);
	}
}
