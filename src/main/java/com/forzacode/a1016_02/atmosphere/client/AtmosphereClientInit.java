package com.forzacode.a1016_02.atmosphere.client;

import com.forzacode.a1016_02.atmosphere.CompassDriftPayload;
import com.forzacode.a1016_02.core.client.ClientEffectsClient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client entrypoint of the atmosphere workstream: fog, silence, music off and compass drift. */
public final class AtmosphereClientInit {
	private AtmosphereClientInit() {
	}

	public static void init() {
		ClientEffectsClient.install(ClientAtmosphere.INSTANCE);
		ClientPlayNetworking.registerGlobalReceiver(CompassDriftPayload.TYPE, (payload, context) -> ClientAtmosphere.INSTANCE.compassDrift(payload));
		ClientTickEvents.END_CLIENT_TICK.register(ClientAtmosphere.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(ClientAtmosphere.INSTANCE::reset));
	}
}
