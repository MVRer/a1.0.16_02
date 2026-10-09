package com.forzacode.a1016_02.core.client;

/** Client entrypoint of core: receives {@code ClientEffects} payloads. */
public final class CoreClientInit {
	private CoreClientInit() {
	}

	public static void init() {
		ClientEffectsClient.registerReceivers();
	}
}
