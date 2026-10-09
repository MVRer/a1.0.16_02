package com.forzacode.a1016_02.client;

import com.forzacode.a1016_02.entity.ModEntities;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.renderer.entity.EntityRenderers;

public class A1016_02Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.HIM, HimRenderer::new);
	}
}
