package com.forzacode.a1016_02.director;

import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.director.sc.PlacesCommands;
import com.forzacode.a1016_02.director.sc.Sc01Config;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.server.level.ServerPlayer;

/** Common entrypoint of the director workstream. Called by the main entrypoint after {@code CoreInit}. */
public final class DirectorInit {
	private static final DirectorImpl DIRECTOR = new DirectorImpl();

	private DirectorInit() {
	}

	public static void init() {
		Services.installDirector(DIRECTOR);
		DirectorCommands.register(DIRECTOR);
		// docs/SELF_CREATED.md, 01: the places' section, its debug command and what the switch puts back.
		Sc01Config.get();
		PlacesCommands.register();
		SelfCreated.onSwitchedOff("sc01", PlacesCommands::revert);
		HerobrineEvents.TRACED.register((level, entry, placedByPlayer) -> DIRECTOR.onTraced(level, entry));
		// docs/SELF_CREATED_2.md, 01+: an answer of one of his places is a place the player remembers.
		HerobrineEvents.REMAINS_ANSWERED.register((level, place, answer, player) -> DIRECTOR.onAnswered(level, place, player));

		// Sessions are per player (D-062.4): one player starting or stopping touches only their own pacing.
		HerobrineEvents.PARTICIPATION.register((server, player, participating) -> {
			if (participating) {
				DIRECTOR.onJoin(server, player);
			} else {
				DIRECTOR.onLeave(server, player);
			}
		});
		HerobrineEvents.TELLING.register((player, text, pos, namesHim) -> DIRECTOR.onTelling(player.level().getServer(), namesHim));
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				DirectorTriggers.onStartSleeping(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DirectorTriggers.clear();
			DIRECTOR.clear();
		});
	}

	/** The installed director (tests and commands). */
	public static DirectorImpl director() {
		return DIRECTOR;
	}
}
