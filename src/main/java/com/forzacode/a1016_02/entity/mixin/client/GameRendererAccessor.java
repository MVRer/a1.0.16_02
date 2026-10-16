package com.forzacode.a1016_02.entity.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.GameRenderer;

/**
 * The screenshot (D-078): {@code GameRenderer.render} grabs the main render target as the world's {@code icon.png}
 * while a singleplayer world has none, at most once a second ({@code tryTakeScreenshotIfNeeded}). The frame with him
 * marks an attempt as made just before it renders, so that grab never takes him.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("lastScreenshotAttempt")
	void a1016_02$setLastScreenshotAttempt(long millis);
}
