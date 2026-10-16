package com.forzacode.a1016_02.atmosphere.sc;

import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code a1016_02:atmosphere/sc04}: the sky window of {@link EmptyNight} on the overworld clock, or none. Replaces what
 * the client had; sent on every change and on join, to everyone (spectators included).
 */
public record EmptyNightPayload(Optional<EmptyNight.Window> window) implements CustomPacketPayload {
	public static final Type<EmptyNightPayload> TYPE = new Type<>(A1016_02.id("atmosphere/sc04"));
	public static final StreamCodec<ByteBuf, EmptyNightPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(EmptyNight.Window.STREAM_CODEC), EmptyNightPayload::window,
			EmptyNightPayload::new);

	public EmptyNightPayload {
		window = window != null ? window : Optional.empty();
	}

	@Override
	public Type<EmptyNightPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	static void send(ServerPlayer player, EmptyNightPayload payload) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
