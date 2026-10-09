package com.forzacode.a1016_02.ending;

import com.forzacode.a1016_02.ending.d.EndingDInit;

/** Common entrypoint of the ending workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class EndingInit {
	private EndingInit() {
	}

	public static void init() {
		EndingAbcInit.init();
		EndingDInit.init();
	}
}
