package com.forzacode.a1016_02.world.sc2.s28;

import java.util.List;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ProtectedAreas;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.Bases;
import com.forzacode.a1016_02.world.sc.ScEnv;

import net.minecraft.server.MinecraftServer;

/**
 * What 28 reads and changes the world through: the live world's, or a test's own (its own players, story state and bases
 * through {@link Bases}, its own view, its own data and numbers). The move still goes through {@link #traces}, which
 * checks the view again when it commits.
 *
 * @param bases   who lives where, the participants, the story state
 * @param traces  the move
 * @param view    the out-of-view rule asked before the move is planned (live: {@code TraceService.isOutOfView})
 * @param people  everyone in a level, spectators and the dead included (live: the level's players)
 * @param data    the visits, the count and the way
 * @param config  the numbers
 * @param protect the areas his edits leave alone
 */
public record S28Env(Bases bases, TraceService traces, ScEnv.View view, ScEnv.People people, S28Data data, Sc28Config config, ProtectedAreas protect) {
	/** The live world's. */
	public static S28Env live(MinecraftServer server) {
		TraceService traces = Services.traces();
		return new S28Env(Bases.live(server), traces, traces::isOutOfView, level -> List.copyOf(level.players()), S28Data.get(server), Sc28Config.get(),
				Services.protectedAreas());
	}

	public MinecraftServer server() {
		return bases.server();
	}

	public HerobrineState state() {
		return bases.state();
	}
}
