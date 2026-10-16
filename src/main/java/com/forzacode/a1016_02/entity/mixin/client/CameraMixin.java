package com.forzacode.a1016_02.entity.mixin.client;

import com.forzacode.a1016_02.entity.client.HisViewClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * The self-created layer's 03: for the unpresented frames {@link HisViewClient} renders, the camera stands at his eyes looking
 * toward the host's and is detached (so the host's own model is drawn, as a third-person camera would). At the end of
 * vanilla's {@code alignWithEntity}, so {@code Camera.update} builds the frame's view matrix and cull frustum from it; every
 * other frame is vanilla's.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
	private void a1016_02$hisView(float partialTicks, CallbackInfo ci) {
		HisViewClient.View view = HisViewClient.cameraOverride();
		if (view != null) {
			setRotation(view.yaw(), view.pitch());
			setPosition(view.eye());
			detached = true;
		}
	}
}
