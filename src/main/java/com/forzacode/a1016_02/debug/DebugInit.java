package com.forzacode.a1016_02.debug;

import com.forzacode.a1016_02.core.Director;

/** Common entrypoint of the debug workstream: the core {@code /a1016} commands and the {@code debug_ping} card. */
public final class DebugInit {
	private DebugInit() {
	}

	public static void init() {
		Director.register(new DebugPingCard());
		DebugCommands.register();
	}
}
