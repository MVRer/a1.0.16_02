package com.forzacode.a1016_02.ending.d;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The last minute's music (D-018 payload {@code a1016_02:ending/d_music}): the client starts one of the game's own
 * calm tracks now, instead of waiting for the music manager's next one. Music must already be back on.
 */
public record CalmMusicPayload() implements CustomPacketPayload {
	public static final CalmMusicPayload INSTANCE = new CalmMusicPayload();
	public static final Type<CalmMusicPayload> TYPE = new Type<>(A1016_02.id("ending/d_music"));
	public static final StreamCodec<ByteBuf, CalmMusicPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<CalmMusicPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, INSTANCE);
		}
	}
}
