package com.forzacode.a1016_02.accident;

import com.forzacode.a1016_02.accident.sc.Sc15Config;
import com.forzacode.a1016_02.accident.sc.StoodOn;
import com.forzacode.a1016_02.accident.sc.StoodOnCommands;
import com.forzacode.a1016_02.core.AccidentPlanner;
import com.forzacode.a1016_02.core.DeathMarker;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * Common entrypoint of the accident workstream: installs the real {@link AccidentPlanner} and {@link DeathMarker},
 * registers one director card per trap, the route sampler and planner tick, sessions from
 * {@link HerobrineEvents#PARTICIPATION}, the death hook and {@code /a1016 accident}.
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
		// docs/SELF_CREATED.md, 15: its section, card, debug command, the standing ring for armed players, and what the
		// switch puts back (every arm).
		Sc15Config.get();
		Director.register(new StoodOn.Card());
		StoodOnCommands.register();
		PLANNER.sampler().ringFor(StoodOn::armed);
		SelfCreated.onSwitchedOff("sc15", server -> StoodOn.clear());
		HerobrineEvents.PARTICIPATION.register((server, player, participating) -> {
			if (!participating) {
				StoodOn.disarm(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> StoodOn.clear());

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PLANNER.attach(server);
			AccidentConfig.get();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PLANNER.attach(null));
		// Sessions are per player (D-059, D-062.4): a start is a new session of theirs, a stop is theirs alone.
		HerobrineEvents.PARTICIPATION.register(PLANNER::onParticipation);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> PLANNER.onChunkLoad(level, chunk.getPos()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PLANNER.tick(server);
			StoodOn.tick(server);
			if (server.getTickCount() % AccidentConfig.cadenceTicks(AccidentConfig.get().crossRetrySeconds) == 0) {
				MARKER.tick(server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				PLANNER.onDeath(player, source);
			} else {
				PLANNER.onMobDied(entity);
			}
		});
	}
}
