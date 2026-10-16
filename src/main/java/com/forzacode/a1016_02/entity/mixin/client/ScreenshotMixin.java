package com.forzacode.a1016_02.entity.mixin.client;

import com.forzacode.a1016_02.entity.client.ScreenshotClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * The screenshot key (F2) while the screenshot is armed (D-078): {@link ScreenshotClient#onScreenshotKey} renders one
 * frame with him into the main render target and grabs it, and vanilla's grab of the last frame is skipped. Only
 * {@code Minecraft.handleGlobalKeyPress} calls this form of {@code grab}.
 */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
	@Inject(method = "grab(Lnet/minecraft/client/Minecraft;Z)V", at = @At("HEAD"), cancellable = true)
	private static void a1016_02$theScreenshot(Minecraft minecraft, boolean debugPanoramaRequested, CallbackInfo ci) {
		if (ScreenshotClient.onScreenshotKey(minecraft, debugPanoramaRequested)) {
			ci.cancel();
		}
	}
}
