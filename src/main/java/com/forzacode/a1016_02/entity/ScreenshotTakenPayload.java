package com.forzacode.a1016_02.entity;

import com.forzacode.a1016_02.A1016_02;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server (D-078): the screenshot happened, he is in the saved image. The server takes it only from a player
 * it armed ({@link TheScreenshot#report}): it records the world's one screenshot, counts the day and disarms everyone.
 * Nothing else is told: no message, no sound.
 */
public record ScreenshotTakenPayload() implements CustomPacketPayload {
	public static final ScreenshotTakenPayload INSTANCE = new ScreenshotTakenPayload();
	public static final Type<ScreenshotTakenPayload> TYPE = new Type<>(A1016_02.id("entity/screenshot_taken"));
	public static final StreamCodec<ByteBuf, ScreenshotTakenPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ScreenshotTakenPayload> type() {
		return TYPE;
	}

	/** Registers the payload and its server receiver (on the server thread). Common init. */
	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> TheScreenshot.LIVE.report(context.player()));
	}
}
