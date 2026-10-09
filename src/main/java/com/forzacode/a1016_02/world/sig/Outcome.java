package com.forzacode.a1016_02.world.sig;

import com.forzacode.a1016_02.core.FireResult;

/**
 * What a signature start did.
 *
 * @param done      it happened now
 * @param scheduled it was started and finishes in the background (chunks load first)
 * @param later     it could not happen now but may later (no spot out of view, chunks loading)
 */
public record Outcome(boolean done, boolean scheduled, boolean later, String message) {
	public static Outcome done(String message) {
		return new Outcome(true, false, false, message);
	}

	public static Outcome scheduled(String message) {
		return new Outcome(false, true, false, message);
	}

	/** Not now: no out-of-view spot, or its chunks are loading. */
	public static Outcome later(String message) {
		return new Outcome(false, false, true, message);
	}

	/** Not at all (it already happened, or it is not this world's). */
	public static Outcome failed(String message) {
		return new Outcome(false, false, false, message);
	}

	public boolean ok() {
		return done || scheduled;
	}

	/** The card's answer to the director. */
	public FireResult result() {
		return ok() ? FireResult.FIRED : later ? FireResult.NO_SPOT : FireResult.SKIPPED;
	}
}
