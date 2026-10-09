package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (D-018, D-035): the fog end the client is drawing right now, in blocks, vanilla fog, atmosphere's
 * dusk fog and fog surges included. Sent by {@code entity.client.FogEndReporter} about every half second and whenever
 * it moves by more than {@link FogReport#CHANGE_BLOCKS}; kept per player by {@link ReportedFog}.
 */
public record FogEndPayload(float blocks) implements CustomPacketPayload {
	public static final Type<FogEndPayload> TYPE = new Type<>(A1016_02.id("entity/fog_end"));
	public static final StreamCodec<ByteBuf, FogEndPayload> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, FogEndPayload::blocks, FogEndPayload::new);

	@Override
	public Type<FogEndPayload> type() {
		return TYPE;
	}

	/** Registers the payload and its server receiver. Common init. */
	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> ReportedFog.report(context.player(), payload.blocks()));
	}
}
