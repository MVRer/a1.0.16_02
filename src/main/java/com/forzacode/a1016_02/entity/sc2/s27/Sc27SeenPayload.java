package com.forzacode.a1016_02.entity.sc2.s27;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The viewer's client to the server (27, {@code a1016_02:entity/sc27_seen}; review R10-8): the copy of showing {@code key}
 * was seen ({@code CopyRules.seen}: on the screen, in plain sight or by its name, not lost in the dark or the fog). Only
 * then is the showing counted (the viewer's day, the world's count): one never seen may come again another dark. Sent
 * once a showing.
 */
public record Sc27SeenPayload(int key) implements CustomPacketPayload {
	public static final Type<Sc27SeenPayload> TYPE = new Type<>(A1016_02.id("entity/sc27_seen"));
	public static final StreamCodec<ByteBuf, Sc27SeenPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Sc27SeenPayload::key, Sc27SeenPayload::new);

	@Override
	public Type<Sc27SeenPayload> type() {
		return TYPE;
	}

	/** Registers the payload and its server receiver (the count, on the server thread). Common init. */
	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			ServerPlayer viewer = context.player();
			viewer.level().getServer().execute(() -> Sc27.LIVE.seen(viewer, payload.key()));
		});
	}
}
