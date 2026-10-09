package com.forzacode.a1016_02.client;

import com.forzacode.a1016_02.accident.client.AccidentClientInit;
import com.forzacode.a1016_02.atmosphere.client.AtmosphereClientInit;
import com.forzacode.a1016_02.core.client.CoreClientInit;
import com.forzacode.a1016_02.debug.client.DebugClientInit;
import com.forzacode.a1016_02.dig.client.DigClientInit;
import com.forzacode.a1016_02.director.client.DirectorClientInit;
import com.forzacode.a1016_02.ending.client.EndingClientInit;
import com.forzacode.a1016_02.entity.client.EntityClientInit;
import com.forzacode.a1016_02.lore.client.LoreClientInit;
import com.forzacode.a1016_02.world.client.WorldClientInit;

import net.fabricmc.api.ClientModInitializer;

public class A1016_02Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CoreClientInit.init();
		DirectorClientInit.init();
		EntityClientInit.init();
		AtmosphereClientInit.init();
		WorldClientInit.init();
		DigClientInit.init();
		LoreClientInit.init();
		AccidentClientInit.init();
		EndingClientInit.init();
		DebugClientInit.init();
	}
}
