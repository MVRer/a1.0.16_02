package com.forzacode.a1016_02.debug;

import java.util.List;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The dev overlay (D-018 payload {@code a1016_02:debug/overlay}), sent once per second to op players while
 * {@code /a1016 debug overlay on}. Each row is {@code label + '\t' + value}, already formatted on the server with
 * {@code Locale.ROOT}. {@code on == false} hides the panel.
 */
public record OverlayPayload(boolean on, List<String> rows) implements CustomPacketPayload {
	public static final int MAX_ROWS = 32;
	public static final char SEPARATOR = '\t';
	public static final Type<OverlayPayload> TYPE = new Type<>(A1016_02.id("debug/overlay"));
	public static final StreamCodec<ByteBuf, OverlayPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, OverlayPayload::on,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_ROWS)), OverlayPayload::rows,
			OverlayPayload::new);

	public OverlayPayload {
		rows = List.copyOf(rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows);
	}

	public static final OverlayPayload OFF = new OverlayPayload(false, List.of());

	@Override
	public Type<OverlayPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	static void send(ServerPlayer player, OverlayPayload payload) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
