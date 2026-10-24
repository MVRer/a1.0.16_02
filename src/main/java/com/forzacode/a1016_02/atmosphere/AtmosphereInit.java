package com.forzacode.a1016_02.atmosphere;

import com.forzacode.a1016_02.atmosphere.card.AtmosphereCards;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc.AtmosphereSc;
import com.forzacode.a1016_02.atmosphere.sc2.AtmosphereSc2;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.Services;

/** Common entrypoint of the atmosphere workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class AtmosphereInit {
	private AtmosphereInit() {
	}

	public static void init() {
		AtmosphereConfig.get();
		// Core's server-side fog math (where he may stand) follows this config live, so it matches the client fog.
		FogLimits.installShape(() -> AtmosphereConfig.get().fogShape());
		// Fog weather's numbers (D-071): the live rolls, the spell's ease on both sides, and the director's sims.
		FogWeatherRules.install(() -> AtmosphereConfig.get().fogWeather());
		CompassDriftPayload.register();
		DeadMountainsPayload.register();
		Services.installMobTamper(MobTamperImpl.INSTANCE);
		AtmosphereCards.all().forEach(Director::register);
		AtmosphereServer.init();
		AtmosphereCommands.register();
		// The self-created layer's part (docs/SELF_CREATED.md): sections, payload, commands and the switch's reverts.
		AtmosphereSc.init();
		AtmosphereSc2.init(); // the self-created layer's second wave (docs/SELF_CREATED_2.md)
	}
}
