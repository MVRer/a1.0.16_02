package com.forzacode.a1016_02.atmosphere;

import com.forzacode.a1016_02.atmosphere.card.AtmosphereCards;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.Services;

/** Common entrypoint of the atmosphere workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class AtmosphereInit {
	private AtmosphereInit() {
	}

	public static void init() {
		AtmosphereConfig.get();
		CompassDriftPayload.register();
		Services.installMobTamper(MobTamperImpl.INSTANCE);
		AtmosphereCards.all().forEach(Director::register);
		AtmosphereServer.init();
		AtmosphereCommands.register();
	}
}
