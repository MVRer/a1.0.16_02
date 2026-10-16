package com.forzacode.a1016_02.entity.sc;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (02, {@code a1016_02:entity/sc02_spent}): the armed client has left names undrawn for its whole
 * {@code hideSecondsTotal}; the server ends that player's arm ({@link Nameless#spent}).
 */
public record NamelessSpentPayload() implements CustomPacketPayload {
	public static final NamelessSpentPayload INSTANCE = new NamelessSpentPayload();
	public static final Type<NamelessSpentPayload> TYPE = new Type<>(A1016_02.id("entity/sc02_spent"));
	public static final StreamCodec<ByteBuf, NamelessSpentPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<NamelessSpentPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> Nameless.LIVE.spent(context.player()));
	}
}
