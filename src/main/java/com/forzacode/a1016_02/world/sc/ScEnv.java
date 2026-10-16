package com.forzacode.a1016_02.world.sc;

import java.util.Collection;
import java.util.List;

import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogPlace;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.ProtectedAreas;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.Bases;
import com.forzacode.a1016_02.world.WorldData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the layer's world features read and change the world through: the live world's, or a test's own (its own
 * players, story state and {@link WorldData} through {@link Bases}, its own view and fog). Every edit still goes
 * through {@link #traces}, which checks the view again when it commits.
 *
 * @param bases     who lives where, the participants, the story state and world's data
 * @param traces    every world edit
 * @param view      the out-of-view rule asked before an edit is planned (live: {@link TraceService#isOutOfView})
 * @param people    everyone in a level, spectators and the dead included (live: the level's players), for the
 *                  "nobody within" rules
 * @param fog       where a fog place goes (live: {@link ClientEffects#addFogPlace})
 * @param protect   the areas his edits leave alone
 */
public record ScEnv(Bases bases, TraceService traces, View view, People people, FogSink fog, ProtectedAreas protect) {
	/** True if nobody can see any of these cells. */
	@FunctionalInterface
	public interface View {
		boolean outOfView(ServerLevel level, Collection<BlockPos> cells);
	}

	/** Everyone in a level who could be near: spectators and the dead included. */
	@FunctionalInterface
	public interface People {
		List<ServerPlayer> in(ServerLevel level);
	}

	/** Adds a fog place for the world, at most {@code max} at full at once; false if it was refused. */
	@FunctionalInterface
	public interface FogSink {
		boolean add(MinecraftServer server, FogPlace place, int max);
	}

	/** The live world's. */
	public static ScEnv live(MinecraftServer server) {
		TraceService traces = Services.traces();
		return new ScEnv(Bases.live(server), traces, traces::isOutOfView, level -> List.copyOf(level.players()), ClientEffects::addFogPlace,
				Services.protectedAreas());
	}

	public MinecraftServer server() {
		return bases.server();
	}

	public HerobrineState state() {
		return bases.state();
	}

	public PlayerWatch watch() {
		return bases.watch();
	}

	public WorldData data() {
		return bases.data();
	}

	public ScMarks marks() {
		return bases.data().sc();
	}

	/** True if any of these cells is in an area his edits leave alone. */
	public boolean anyProtected(ServerLevel level, Collection<BlockPos> cells) {
		for (BlockPos cell : cells) {
			if (protect.isProtected(level.dimension(), cell)) {
				return true;
			}
		}
		return false;
	}

	/** True if every one of these cells is in a loaded chunk (nothing here ever loads one). */
	public static boolean loaded(ServerLevel level, Collection<BlockPos> cells) {
		for (BlockPos cell : cells) {
			if (!level.isLoaded(cell)) {
				return false;
			}
		}
		return true;
	}
}
