package com.forzacode.a1016_02.debug;

import com.forzacode.a1016_02.core.Director;

/**
 * Common entrypoint of the debug workstream: the core {@code /a1016} commands, the {@code debug_ping} card, the
 * scripted playthrough and the dev overlay.
 */
public final class DebugInit {
	private DebugInit() {
	}

	public static void init() {
		Director.register(new DebugPingCard());
		DebugCommands.register();
		PlaytestCommands.register();
		DebugOverlay.init();
	}
}
