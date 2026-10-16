package com.forzacode.a1016_02.entity.sc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client to server (02, {@code a1016_02:entity/sc02_reach}): the nearest distance at which the fog this client draws
 * covers {@code fogShare} of a player ({@link NamelessRules#reach}), sent by {@code entity.client.NamelessClient} while
 * the layer is on and another player is in its level, every {@link NamelessRules#REACH_EVERY_TICKS} and at once on a
 * change. The server keeps the latest per player ({@link #fresh}) so {@link Nameless#fit} arms only a client whose own fog
 * leaves a band ({@link NamelessRules#bandRefusal}). Forgotten when that player's connection closes or the server stops.
 */
public record NamelessReachPayload(float blocks) implements CustomPacketPayload {
	public static final Type<NamelessReachPayload> TYPE = new Type<>(A1016_02.id("entity/sc02_reach"));
	public static final StreamCodec<ByteBuf, NamelessReachPayload> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, NamelessReachPayload::blocks,
			NamelessReachPayload::new);

	/** A report as it arrived: blocks and the server tick. */
	private record Entry(double blocks, int tick) {
	}

	private static final Map<UUID, Entry> LATEST = new ConcurrentHashMap<>();

	@Override
	public Type<NamelessReachPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> report(context.player(), payload.blocks()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LATEST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LATEST.clear());
	}

	/** Keeps a report that is a distance. */
	static void report(ServerPlayer player, float blocks) {
		if (Float.isFinite(blocks) && blocks > 0.0F) {
			LATEST.put(player.getUUID(), new Entry(blocks, player.level().getServer().getTickCount()));
		}
	}

	/** That player's client's latest report while it is fresh ({@link NamelessRules#freshReach}), else NaN. Server thread. */
	public static double fresh(ServerPlayer player) {
		Entry entry = LATEST.get(player.getUUID());
		if (entry == null) {
			return Double.NaN;
		}
		return NamelessRules.freshReach(entry.blocks(), player.level().getServer().getTickCount() - (long) entry.tick());
	}
}
