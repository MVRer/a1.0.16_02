package com.forzacode.a1016_02.entity.sc2.s27;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to the viewer's client (27, {@code a1016_02:entity/sc27_end}): the showing {@code key} ends (0: every one). The
 * copy walks off if the viewer can see it, else it is gone at once; never gone in view. (The other player leaving is seen by
 * the client itself: their entry leaves its player list, and the copy goes as a player who leaves goes.)
 */
public record Sc27EndPayload(int key) implements CustomPacketPayload {
	public static final Type<Sc27EndPayload> TYPE = new Type<>(A1016_02.id("entity/sc27_end"));
	public static final StreamCodec<ByteBuf, Sc27EndPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Sc27EndPayload::key, Sc27EndPayload::new);
	/** Ends every showing on that client (the layer switched off). */
	public static final Sc27EndPayload ALL = new Sc27EndPayload(0);

	@Override
	public Type<Sc27EndPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
