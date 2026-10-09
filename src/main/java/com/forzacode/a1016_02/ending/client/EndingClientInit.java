package com.forzacode.a1016_02.ending.client;

import com.forzacode.a1016_02.ending.d.client.EndingDClientInit;

/** Client entrypoint of the ending workstream. Client-only classes live in this package. */
public final class EndingClientInit {
	private EndingClientInit() {
	}

	public static void init() {
		EndingDClientInit.init();
	}
}
