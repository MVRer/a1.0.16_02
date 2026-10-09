package com.forzacode.a1016_02.atmosphere;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Compass drift (D-018 payload {@code a1016_02:atmosphere/compass_drift}): for {@code ticks}, the player's spawn
 * compass points at ({@code x}, {@code z}) instead, and settles back once they get within {@code settleBlocks} of it.
 * {@code ticks == 0} ends it.
 */
public record CompassDriftPayload(int x, int z, int ticks, int settleBlocks) implements CustomPacketPayload {
	public static final Type<CompassDriftPayload> TYPE = new Type<>(A1016_02.id("atmosphere/compass_drift"));
	public static final StreamCodec<ByteBuf, CompassDriftPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, CompassDriftPayload::x,
			ByteBufCodecs.INT, CompassDriftPayload::z,
			ByteBufCodecs.VAR_INT, CompassDriftPayload::ticks,
			ByteBufCodecs.VAR_INT, CompassDriftPayload::settleBlocks,
			CompassDriftPayload::new);

	@Override
	public Type<CompassDriftPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer player, CompassDriftPayload payload) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
