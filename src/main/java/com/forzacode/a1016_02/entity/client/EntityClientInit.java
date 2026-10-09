package com.forzacode.a1016_02.entity.client;

import com.forzacode.a1016_02.entity.ModEntities;

import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client entrypoint of the entity workstream. Client-only classes live in this package. */
public final class EntityClientInit {
	private EntityClientInit() {
	}

	public static void init() {
		EntityRenderers.register(ModEntities.HIM, HimRenderer::new);
	}
}
