package com.forzacode.a1016_02.entity.sc;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Host to server (03, {@code a1016_02:entity/sc03_taken}): the world's picture was written. Taken only from the host after
 * an arm ({@link HisView#report}): the world's flag is set, once.
 */
public record HisViewTakenPayload() implements CustomPacketPayload {
	public static final HisViewTakenPayload INSTANCE = new HisViewTakenPayload();
	public static final Type<HisViewTakenPayload> TYPE = new Type<>(A1016_02.id("entity/sc03_taken"));
	public static final StreamCodec<ByteBuf, HisViewTakenPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<HisViewTakenPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> HisView.LIVE.report(context.player()));
	}
}
