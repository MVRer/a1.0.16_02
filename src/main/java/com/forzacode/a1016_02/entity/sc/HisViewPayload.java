package com.forzacode.a1016_02.entity.sc;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to host (03, {@code a1016_02:entity/sc03}): the figure with this entity id is the one to look from; 0 for none.
 * Sent by {@link HisView} when a sighting set up for the host arms it; the client's side is
 * {@code entity.client.HisViewClient}.
 */
public record HisViewPayload(int entityId) implements CustomPacketPayload {
	public static final Type<HisViewPayload> TYPE = new Type<>(A1016_02.id("entity/sc03"));
	public static final StreamCodec<ByteBuf, HisViewPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, HisViewPayload::entityId, HisViewPayload::new);
	public static final HisViewPayload OFF = new HisViewPayload(0);

	@Override
	public Type<HisViewPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
