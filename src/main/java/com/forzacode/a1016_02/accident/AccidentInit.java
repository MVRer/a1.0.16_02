package com.forzacode.a1016_02.accident;

import com.forzacode.a1016_02.core.AccidentPlanner;
import com.forzacode.a1016_02.core.DeathMarker;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * Common entrypoint of the accident workstream: installs the real {@link AccidentPlanner} and {@link DeathMarker},
 * registers one director card per trap, the route sampler and planner tick, the death hook and
 * {@code /a1016 accident}.
 */
public final class AccidentInit {
	private static final AccidentPlannerImpl PLANNER = new AccidentPlannerImpl(AccidentData::get, ViewGate.TRACES);
	private static final DeathMarkerImpl MARKER = new DeathMarkerImpl(AccidentData::get, ViewGate.TRACES);

	private AccidentInit() {
	}

	public static AccidentPlannerImpl planner() {
		return PLANNER;
	}

	public static DeathMarkerImpl marker() {
		return MARKER;
	}

	public static void init() {
		Services.installAccidents(PLANNER);
		Services.installDeathMarker(MARKER);
		for (TrapKind trap : Traps.ALL) {
			Director.register(new AccidentCard(trap, PLANNER));
		}
		AccidentCommands.register(PLANNER, MARKER);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PLANNER.attach(server);
			AccidentConfig.get();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PLANNER.attach(null));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PLANNER.onJoin(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PLANNER.tick(server);
			if (server.getTickCount() % AccidentConfig.cadenceTicks(AccidentConfig.get().crossRetrySeconds) == 0) {
				MARKER.tick(server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				PLANNER.onDeath(player, source);
			}
		});
	}
}
