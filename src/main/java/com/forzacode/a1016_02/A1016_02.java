package com.forzacode.a1016_02;

import com.forzacode.a1016_02.accident.AccidentInit;
import com.forzacode.a1016_02.atmosphere.AtmosphereInit;
import com.forzacode.a1016_02.core.CoreInit;
import com.forzacode.a1016_02.debug.DebugInit;
import com.forzacode.a1016_02.dig.DigInit;
import com.forzacode.a1016_02.director.DirectorInit;
import com.forzacode.a1016_02.ending.EndingInit;
import com.forzacode.a1016_02.entity.EntityInit;
import com.forzacode.a1016_02.lore.LoreInit;
import com.forzacode.a1016_02.world.WorldInit;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A1016_02 implements ModInitializer {
	public static final String MOD_ID = "a1016_02";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CoreInit.init();
		DirectorInit.init();
		EntityInit.init();
		AtmosphereInit.init();
		WorldInit.init();
		DigInit.init();
		LoreInit.init();
		AccidentInit.init();
		EndingInit.init();
		DebugInit.init();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
