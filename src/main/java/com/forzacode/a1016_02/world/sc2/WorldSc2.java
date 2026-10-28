package com.forzacode.a1016_02.world.sc2;

import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.DemoKind;
import com.forzacode.a1016_02.world.sc2.kit.Guard;
import com.forzacode.a1016_02.world.sc2.kit.Plans;
import com.forzacode.a1016_02.world.sc2.kit.RaysPlacement;
import com.forzacode.a1016_02.world.sc2.kit.Recorder;
import com.forzacode.a1016_02.world.sc2.kit.RemainsImpl;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Piece;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Visits;
import com.forzacode.a1016_02.world.sc2.s16.S16;
import com.forzacode.a1016_02.world.sc2.s17.S17;
import com.forzacode.a1016_02.world.sc2.s18.S18;
import com.forzacode.a1016_02.world.sc2.s19.S19;
import com.forzacode.a1016_02.world.sc2.s20.S20;
import com.forzacode.a1016_02.world.sc2.s28.S28;
import com.forzacode.a1016_02.world.sc2.s32.S32;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

/**
 * The self-created layer's second wave, world's part (docs/SELF_CREATED_2.md), called from world's init: the kit's
 * registry types ({@code world/sc2} structure, {@code world/sc2p} piece, {@code world/sc2r} placement), the remains
 * service, the snapshot, the guard against later worldgen ({@link Guard}), the recorder's drain, the visits, the
 * answers and their switch, the dev-only demo kind, and
 * each feature's own init ({@code S16} to {@code S20}, {@code S28}, round 3's {@code S32}: one line each, so no two tasks
 * touch one file).
 */
public final class WorldSc2 {
	private WorldSc2() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, A1016_02.id("world/sc2"), Sc2Structure.TYPE);
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, A1016_02.id("world/sc2p"), Sc2Piece.TYPE);
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, A1016_02.id("world/sc2r"), RaysPlacement.CODEC);
		Services.installRemains(RemainsImpl.LIVE);
		for (Sc2Kind demo : demoKinds(FabricLoader.getInstance().isDevelopmentEnvironment())) {
			Sc2Kind.register(demo);
		}

		ServerLifecycleEvents.SERVER_STARTING.register(WorldSc2::starting);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RemainsImpl.LIVE.attach(server);
			// The world's fixed plans before the snapshot: nothing that needs them builds before (R12-2).
			Plans.load(WorldData.get(server).sc2());
			Snapshot.refresh(server);
		});
		ServerTickEvents.END_SERVER_TICK.register(WorldSc2::tick);
		// A chunk loaded whole has every neighbour decorated: nothing of worldgen writes into it any more (SC2-H).
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Guard.finished(level.dimension(), chunk.getPos()));
		HerobrineEvents.PARTICIPATION.register((server, player, participating) -> {
			if (!participating) {
				Visits.LIVE.stopped(server, player);
			}
		});
		// What worldgen recorded or fixed after the last tick's drain is kept before the last save (R12-14).
		ServerLifecycleEvents.SERVER_STOPPING.register(WorldSc2::drain);
		ServerLifecycleEvents.SERVER_STOPPED.register(WorldSc2::stopped);
		// Switched off: nothing waits and nobody is inside; each answer's own give-back is registered by its feature
		// (Answers.giveBackWhenOff). What generated stays (D-093).
		SelfCreated.onSwitchedOff("sc2", server -> {
			Answers.LIVE.clear();
			Visits.LIVE.clear();
		});

		S16.init();
		S17.init();
		S18.init();
		S19.init();
		S20.init();
		S28.init();
		S32.init();
	}

	/** The demo kinds to register: {@code k0} in a development environment, none otherwise. */
	public static List<Sc2Kind> demoKinds(boolean developmentEnvironment) {
		return developmentEnvironment ? List.of(DemoKind.KIND) : List.of();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % Snapshot.REFRESH_TICKS == 0) {
			Snapshot.refresh(server);
		}
		drain(server);
		Visits.LIVE.tick(server);
		Answers.LIVE.tick(server);
	}

	/** At {@code SERVER_STARTING}: the snapshot if the server can give it already, and only with the world's plans. */
	private static void starting(MinecraftServer server) {
		try {
			Plans.load(WorldData.get(server).sc2());
		} catch (RuntimeException e) {
			A1016_02.LOGGER.debug("[a1016] s2: data not ready at start, read when the server runs", e);
			return;
		}
		Snapshot.refreshQuietly(server);
	}

	/** What worldgen recorded and fixed, into the world's data. Server thread. */
	private static void drain(MinecraftServer server) {
		if (Recorder.pending() || Plans.pending()) {
			try {
				WorldData data = WorldData.get(server);
				Recorder.drain(data.sc2());
				Plans.drain(data.sc2());
			} catch (RuntimeException e) {
				A1016_02.LOGGER.debug("[a1016] s2: nothing kept now", e);
			}
		}
	}

	/** Server stop: the snapshot, the queue, the visits and the answers go, so a second server in the same JVM starts clean. */
	public static void stopped(MinecraftServer server) {
		Snapshot.clear();
		Guard.clear();
		Recorder.clear();
		Plans.clear();
		Visits.LIVE.clear();
		Answers.stopped();
		RemainsImpl.LIVE.attach(null);
	}
}
