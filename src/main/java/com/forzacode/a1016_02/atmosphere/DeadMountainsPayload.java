package com.forzacode.a1016_02.atmosphere;

import java.util.List;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The dead mountains near a player (D-018 payload {@code a1016_02:atmosphere/dead_mountains}), for {@code dimension}.
 * Replaces what the client had. While the player stands inside one, ambience and music fade out.
 */
public record DeadMountainsPayload(ResourceKey<Level> dimension, List<DeadMountains.Area> areas) implements CustomPacketPayload {
	public static final Type<DeadMountainsPayload> TYPE = new Type<>(A1016_02.id("atmosphere/dead_mountains"));
	public static final StreamCodec<ByteBuf, DeadMountainsPayload> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), DeadMountainsPayload::dimension,
			DeadMountains.Area.STREAM_CODEC.apply(ByteBufCodecs.list(DeadMountains.MAX_SENT)), DeadMountainsPayload::areas,
			DeadMountainsPayload::new);

	@Override
	public Type<DeadMountainsPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	static void send(ServerPlayer player, DeadMountainsPayload payload) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
