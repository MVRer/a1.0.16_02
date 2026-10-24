package com.forzacode.a1016_02.debug;

import com.forzacode.a1016_02.core.Director;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Common entrypoint of the debug workstream: the core {@code /a1016} commands, the {@code debug_ping} card (in a
 * development environment only), the scripted playthrough, the dev overlay and the self-created layer's index
 * ({@code /a1016 sc}).
 */
public final class DebugInit {
	private DebugInit() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			// Its moment never fits: in a release jar it would only be drawn and parked again and again. The dev client
			// and the game tests (both development environments) keep it for /a1016 fire.
			Director.register(new DebugPingCard());
		}
		DebugCommands.register();
		PlaytestCommands.register();
		ScIndex.register();
		DebugOverlay.init();
		com.forzacode.a1016_02.debug.gallery.GalleryInit.init();
	}
}
