package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client (D-078): whether this client's next screenshot may show him ({@code armed}: armed and not paused),
 * whether a dusk fog or fog spell is on at the player now ({@code fogOn}, the server's own math,
 * {@link TheScreenshot#fogOn}), the weather's fog end for the player in blocks ({@code fogEnd},
 * {@link TheScreenshot#weatherFogEnd}: render distance, dusk fog and spell, never a surge or an effect; the client
 * compares the fog it draws with it, {@link ScreenshotRules#pulledIn}), and which arm this is ({@code arm}, a number
 * new with every arm, 0 for none). Sent by {@link TheScreenshot} when {@code the_screenshot} arms the player, whenever
 * one of them changes while the arm lasts (the fog end by more than {@link TheScreenshot#FOG_END_STEP} blocks), and
 * {@link #OFF} when it ends: it ran out, the player left, the story ended or the screenshot happened for anyone. The
 * client keeps the latest ({@code entity.client.ScreenshotClient}).
 */
public record ScreenshotStatePayload(boolean armed, boolean fogOn, float fogEnd, int arm) implements CustomPacketPayload {
	public static final Type<ScreenshotStatePayload> TYPE = new Type<>(A1016_02.id("entity/screenshot_state"));
	public static final StreamCodec<ByteBuf, ScreenshotStatePayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, ScreenshotStatePayload::armed,
			ByteBufCodecs.BOOL, ScreenshotStatePayload::fogOn, ByteBufCodecs.FLOAT, ScreenshotStatePayload::fogEnd, ByteBufCodecs.VAR_INT,
			ScreenshotStatePayload::arm, ScreenshotStatePayload::new);
	/** Not armed (any more). */
	public static final ScreenshotStatePayload OFF = new ScreenshotStatePayload(false, false, 0.0F, 0);

	@Override
	public Type<ScreenshotStatePayload> type() {
		return TYPE;
	}

	/** Registers the payload. Common init; the client's receiver is {@code entity.client.ScreenshotClient}'s. */
	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
