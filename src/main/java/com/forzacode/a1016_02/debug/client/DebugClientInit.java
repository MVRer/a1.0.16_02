package com.forzacode.a1016_02.debug.client;

/** Client entrypoint of the debug workstream. Client-only classes live in this package. */
public final class DebugClientInit {
	private DebugClientInit() {
	}

	public static void init() {
		DevWorld.init();
	}
}
