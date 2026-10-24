package com.forzacode.a1016_02.entity.client;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.entity.ModEntities;
import com.forzacode.a1016_02.entity.client.sc2.EntitySc2Client;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Client entrypoint of the entity workstream. Client-only classes live in this package. */
public final class EntityClientInit {
	private static final Identifier EYE_SHADER_RELOAD = A1016_02.id("entity/eye_shader");

	private EntityClientInit() {
	}

	public static void init() {
		EntityRenderers.register(ModEntities.HIM, HimRenderer::new);
		FogEndReporter.init();
		GlimpseHide.init(); // the glimpse's hide latch forgets its figures when the connection changes (D-073)
		ScreenshotClient.init(); // the screenshot's arm (D-078); the key goes through ScreenshotMixin
		NamelessClient.init(); // the self-created layer's 02 (AvatarRendererMixin) and 03 (CameraMixin)
		HisViewClient.init();
		EntitySc2Client.init(); // the self-created layer's second wave (docs/SELF_CREATED_2.md)
		// After the shaders reload, the eyes try their own pipeline again (see HimEyesLayer#brightEyes).
		ResourceLoader resources = ResourceLoader.get(PackType.CLIENT_RESOURCES);
		resources.registerReloadListener(EYE_SHADER_RELOAD, (ResourceManagerReloadListener) manager -> HimEyesLayer.resetAfterReload());
		resources.addListenerOrdering(ResourceReloaderKeys.Client.SHADERS, EYE_SHADER_RELOAD);
	}
}
