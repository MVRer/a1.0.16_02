package com.forzacode.a1016_02.core.client;

import java.util.Objects;

import com.forzacode.a1016_02.core.ClientEffects;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client side of {@link ClientEffects}. Core receives the payloads (on the client thread) and hands them to the
 * installed {@link Handler}; atmosphere installs the real one in its client init.
 */
public final class ClientEffectsClient {
	/** What to do with each effect. Every method defaults to doing nothing. */
	public interface Handler {
		default void fogSurge(ClientEffects.FogSurge effect) {
		}

		default void silence(ClientEffects.Silence effect) {
		}

		default void musicOff(ClientEffects.MusicOff effect) {
		}

		default void duskFog(ClientEffects.DuskFog effect) {
		}

		/** Sent on join with every persistent effect. */
		default void sync(ClientEffects.Sync effect) {
		}
	}

	private static Handler handler = new Handler() {
	};

	private ClientEffectsClient() {
	}

	public static void install(Handler newHandler) {
		handler = Objects.requireNonNull(newHandler);
	}

	public static Handler handler() {
		return handler;
	}

	static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(ClientEffects.FogSurge.TYPE, (payload, context) -> handler.fogSurge(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientEffects.Silence.TYPE, (payload, context) -> handler.silence(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientEffects.MusicOff.TYPE, (payload, context) -> handler.musicOff(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientEffects.DuskFog.TYPE, (payload, context) -> handler.duskFog(payload));
		ClientPlayNetworking.registerGlobalReceiver(ClientEffects.Sync.TYPE, (payload, context) -> handler.sync(payload));
	}
}
