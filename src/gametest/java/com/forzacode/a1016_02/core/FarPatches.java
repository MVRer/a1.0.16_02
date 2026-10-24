package com.forzacode.a1016_02.core;

import java.util.function.BooleanSupplier;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;

/**
 * Waiting for a far patch of chunks a test forced (SC2-9): the test server's ticks run as fast as they can, so a wait
 * counted in ticks races the chunk system, and on a machine busy with other builds a far patch (generated, then its
 * entities loaded from their storage) was not ready by tick 202 ("the patch is not loaded yet"). Here the wait is in
 * real time: the first {@link #FREE_TICKS} ticks of waiting cost nothing (the usual case, a patch is ready in a few);
 * from then on each tick it is still not ready, the server runs its own pending work (chunk tickets, generation, light)
 * for a while before the tick ends, growing from 50 ms to {@link #SLICE_MAX_MS}, so the entity storage on its worker
 * threads catches up while the test spends only a few of its ticks; past {@link #CAP_MS} of real waiting it stops
 * giving time and the test's own timeout ends it. A patch that is ready costs nothing at all.
 */
public final class FarPatches {
	/** Ticks of waiting that cost no real time. */
	public static final int FREE_TICKS = 20;
	/** The most real time one tick of waiting gives. */
	public static final long SLICE_MAX_MS = 1_000L;
	/** The most real time a patch is waited for. */
	public static final long CAP_MS = 120_000L;

	private FarPatches() {
	}

	/**
	 * A sequence that runs {@code then} on the first tick {@code ready} holds, waiting in real time as above (about 10 s in
	 * the first 20 slow ticks, then a second a tick, at most {@link #CAP_MS}); {@code what} names the patch in the failure.
	 */
	public static GameTestSequence whenReady(GameTestHelper helper, BooleanSupplier ready, String what, Runnable then) {
		return helper.startSequence().thenWaitUntil(until(helper, ready, what)).thenExecute(then);
	}

	/** The wait itself, for a sequence's {@code thenWaitUntil} (each call its own: it keeps its own count and clock). */
	public static Runnable until(GameTestHelper helper, BooleanSupplier ready, String what) {
		return until(helper, ready, () -> what);
	}

	/** {@link #until(GameTestHelper, BooleanSupplier, String)} naming the patch as it is now in the failure (its state). */
	public static Runnable until(GameTestHelper helper, BooleanSupplier ready, java.util.function.Supplier<String> what) {
		MinecraftServer server = helper.getLevel().getServer();
		long[] since = {-1L};
		int[] slow = {0};
		return () -> {
			if (ready.getAsBoolean()) {
				return;
			}
			long now = Util.getMillis();
			if (since[0] < 0) {
				since[0] = now;
			}
			slow[0]++;
			if (slow[0] > FREE_TICKS && now - since[0] < CAP_MS) {
				long until = now + Math.min(SLICE_MAX_MS, 50L * (slow[0] - FREE_TICKS));
				server.managedBlock(() -> ready.getAsBoolean() || Util.getMillis() >= until);
			}
			if (!ready.getAsBoolean()) {
				helper.assertTrue(false, what.get() + " is not loaded yet (" + (Util.getMillis() - since[0]) / 1000 + " s, " + slow[0] + " ticks)");
			}
		};
	}
}
