package com.forzacode.a1016_02.entity.sc;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (02, {@code a1016_02:entity/sc02}): this client is armed for {@code ticksLeft} more ticks of play and may
 * leave names undrawn for {@code hideTicks} ticks in all; {@link #OFF} (0) when the arm ends. Sent by {@link Nameless}; the
 * client's side is {@code entity.client.NamelessClient}.
 */
public record NamelessPayload(int ticksLeft, int hideTicks) implements CustomPacketPayload {
	public static final Type<NamelessPayload> TYPE = new Type<>(A1016_02.id("entity/sc02"));
	public static final StreamCodec<ByteBuf, NamelessPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, NamelessPayload::ticksLeft,
			ByteBufCodecs.VAR_INT, NamelessPayload::hideTicks, NamelessPayload::new);
	public static final NamelessPayload OFF = new NamelessPayload(0, 0);

	public NamelessPayload {
		ticksLeft = Math.max(0, ticksLeft);
		hideTicks = Math.max(0, hideTicks);
	}

	/** True while it arms the client. */
	public boolean armed() {
		return ticksLeft > 0 && hideTicks > 0;
	}

	@Override
	public Type<NamelessPayload> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
