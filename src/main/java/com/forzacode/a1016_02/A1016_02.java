package com.forzacode.a1016_02;

import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.ModEntities;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A1016_02 implements ModInitializer {
	public static final String MOD_ID = "a1016_02";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.register();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> HimEntity.spawnInFrontOf(handler.player));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
