package com.forzacode.a1016_02.core;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (D-077): a counting screen opened ({@code blind} true) or closed (false) on the sender's client. Core's
 * client ({@code core.client.BlindReporter}) sends it when the answer changes; the server keeps it per player in
 * {@link PlayerWatch#blindTicks} and ignores it from anyone who is not a participant or is dead. The signal only ever
 * makes things happen to the player who sends it.
 */
public record BlindState(boolean blind) implements CustomPacketPayload {
	public static final Type<BlindState> TYPE = new Type<>(A1016_02.id("blind_state"));
	public static final StreamCodec<ByteBuf, BlindState> CODEC = ByteBufCodecs.BOOL.map(BlindState::new, BlindState::blind);

	@Override
	public Type<BlindState> type() {
		return TYPE;
	}

	/** Registers the payload and its server receiver (on the server thread). Common init, so it runs on both sides. */
	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> Services.watch().onBlindState(context.player(), payload.blind()));
	}
}
