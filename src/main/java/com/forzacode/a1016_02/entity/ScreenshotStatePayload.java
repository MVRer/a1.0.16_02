package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (D-078): whether this client's next screenshot may show him ({@code armed}), and whether a dusk
 * fog or fog spell is on at the player now ({@code fogOn}, the server's own math, {@link TheScreenshot#fogOn}). Sent
 * by {@link TheScreenshot} when {@code the_screenshot} arms the player, whenever either value changes while the arm
 * lasts (an arm pauses while he is out or in Ending D's last minute), and {@link #OFF} when it ends: it ran out, the
 * player left or the screenshot happened for anyone. The client keeps the latest ({@code entity.client.ScreenshotClient}).
 */
public record ScreenshotStatePayload(boolean armed, boolean fogOn) implements CustomPacketPayload {
	public static final Type<ScreenshotStatePayload> TYPE = new Type<>(A1016_02.id("entity/screenshot_state"));
	public static final StreamCodec<ByteBuf, ScreenshotStatePayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, ScreenshotStatePayload::armed,
			ByteBufCodecs.BOOL, ScreenshotStatePayload::fogOn, ScreenshotStatePayload::new);
	/** Not armed (any more). */
	public static final ScreenshotStatePayload OFF = new ScreenshotStatePayload(false, false);

	@Override
	public Type<ScreenshotStatePayload> type() {
		return TYPE;
	}

	/** Registers the payload. Common init; the client's receiver is {@code entity.client.ScreenshotClient}'s. */
	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
