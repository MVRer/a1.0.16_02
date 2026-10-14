package com.forzacode.a1016_02.world;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.world.sig.HouseCopier;
import com.forzacode.a1016_02.world.sig.HouseCopyState;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

/**
 * The copy of one player's first shelter ("Your house, elsewhere", D-005), for the ending workstream. Whose house it
 * is (D-061) is the {@value #OWNER_KEY} owner, chosen once by world and never changed ({@link #owner}). Every block
 * in the copy was moved there through {@code TraceService.move}: from the real house (ledger cause
 * {@value #CAUSE}) or, once {@link #finish} ran, buried blocks from the ground near the copy ({@value #CAUSE_LOCAL}).
 * Ending D's undo moves them back from the ledger; {@link #moved} lists the same moves. Server thread only.
 */
public final class HouseCopyApi {
	/** Ledger cause of the blocks moved out of the real house. */
	public static final String CAUSE = HouseCopier.CAUSE;
	/** Ledger cause of the buried blocks the finish moved from near the copy. */
	public static final String CAUSE_LOCAL = HouseCopier.CAUSE_LOCAL;
	/** The stored-owner key (D-061) of the house the copy copies; read it with {@code PlayerWatch.owner}. */
	public static final String OWNER_KEY = HouseCopier.OWNER_KEY;

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

	/**
	 * Ending B left, or the story ended: a finish asked for with {@link #finish} is cancelled (the copy goes back to
	 * growing slowly). Once {@code ending:d_complete} or {@code ending:ended} is set the copy stops for good on its
	 * own. True if a finish was pending.
	 */
	public static boolean cancelFinish(MinecraftServer server) {
		return HouseCopier.cancelFinish(server);
	}

	/** True once nothing is left to move into the copy. */
	public static boolean finished(MinecraftServer server) {
		return WorldData.get(server).signatures().houseCopy().map(s -> s.phase() == HouseCopyState.Phase.FINISHED).orElse(false);
	}

	/** The middle of the copy (where Ending B's final death happens), once its site is picked. */
	public static Optional<GlobalPos> site(MinecraftServer server) {
		return HouseCopier.site(server);
	}

	/**
	 * The house the copy copies: a cell inside the real house (its lowest floor cell nearest the middle), in its
	 * dimension, from the moment the house is captured (before the copy site is picked). Empty while there is no copy.
	 * The house stays where it was captured, even after its owner leaves or moves their base.
	 */
	public static Optional<GlobalPos> realHouse(MinecraftServer server) {
		return HouseCopier.realHouse(server);
	}

	/**
	 * Whose house the copy copies: the stored {@value #OWNER_KEY} owner, else (a copy from before multiplayer, whose
	 * owner is stored once that player is the only one online) its first known player. Empty before anything is chosen.
	 */
	public static Optional<UUID> owner(MinecraftServer server) {
		return HouseCopier.owner(server);
	}

	/** Every block moved into the copy so far, oldest first: from, to, the block, and whether it came from near the copy. */
	public static List<HouseCopyState.Moved> moved(MinecraftServer server) {
		return HouseCopier.moved(server);
	}
}
