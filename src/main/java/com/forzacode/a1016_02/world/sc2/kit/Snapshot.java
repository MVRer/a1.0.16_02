package com.forzacode.a1016_02.world.sc2.kit;

import java.util.HashMap;
import java.util.Map;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ScLog;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Stage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

/**
 * Everything worldgen reads of the server for the second wave's structures, immutable: the switch, the stage, the
 * world spawn, the seed, the world's worldgen salt ({@code HerobrineState.worldgenSalt}) and each registered kind's
 * config. Taken on the server thread (at {@code SERVER_STARTING} when it can, at {@code SERVER_STARTED} and every 100
 * ticks) and published through a volatile field, cleared at stop (as {@code ScarContext}); worldgen threads read only
 * this. While there is none, the switch is off or the stage is under a kind's {@code fromStage}, no start is made.
 *
 * @param configs each kind's {@link Sc2Kind#config}, by id
 */
public record Snapshot(boolean enabled, Stage stage, BlockPos spawn, long seed, long salt, Map<String, Object> configs) {
	/** How often the live one is taken again (ticks). */
	public static final int REFRESH_TICKS = 100;

	private static volatile @Nullable Snapshot current;
	/** The live server's overworld generator (R12-15: no start in another dimension, whatever its biomes), or null. */
	private static volatile @Nullable Object overworld;

	public Snapshot {
		spawn = spawn.immutable();
		configs = Map.copyOf(configs);
	}

	/** The snapshot in use, or null (no server, or not taken yet). Any thread. */
	public static @Nullable Snapshot current() {
		return current;
	}

	/** Takes the live one now. Server thread; throws if the server has no overworld or state yet. */
	public static Snapshot take(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		HerobrineState state = HerobrineState.get(server);
		Map<String, Object> configs = new HashMap<>();
		for (Sc2Kind kind : Sc2Kind.all()) {
			Object config = kind.gates();
			if (config != null) {
				configs.put(kind.id(), config);
			}
		}
		return new Snapshot(SelfCreated.enabled(), state.stage(), overworld.getRespawnData().pos(), server.getWorldGenSettings().options().seed(),
				state.worldgenSalt(), configs);
	}

	/** Publishes a fresh live one; if it cannot be taken, the last one stays (logged once). Server thread. */
	public static void refresh(MinecraftServer server) {
		try {
			current = take(server);
			overworld = server.overworld().getChunkSource().getGenerator();
			failed = false;
		} catch (RuntimeException e) {
			if (!failed) {
				failed = true;
				ScLog.error("[a1016] s2: the numbers could not be read", e);
			}
		}
	}

	private static volatile boolean failed;

	/** {@link #refresh}, or nothing (a debug line) if the server is not ready yet ({@code SERVER_STARTING}). */
	public static void refreshQuietly(MinecraftServer server) {
		try {
			current = take(server);
			overworld = server.overworld().getChunkSource().getGenerator();
		} catch (RuntimeException e) {
			A1016_02.LOGGER.debug("[a1016] s2: numbers not ready at start, taken when the server runs", e);
		}
	}

	/** Tests: use this one (null for none); no dimension rule until the live one is taken again. */
	public static void install(@Nullable Snapshot snapshot) {
		current = snapshot;
		overworld = null;
	}

	/** At server stop. */
	public static void clear() {
		current = null;
		overworld = null;
	}

	/**
	 * True if a structure start may be decided with this generator (R12-15): the live server's overworld's, the only
	 * dimension the remains belong to (a data pack's or a mod's dimension with the overworld's biomes gets none). Any
	 * thread; always true under a test's own snapshot.
	 */
	public static boolean inOverworld(Object generator) {
		Object live = overworld;
		return live == null || live == generator;
	}

	/** A kind's config of this type, or null. */
	public <T> @Nullable T config(String kind, Class<T> type) {
		Object config = configs.get(kind);
		return type.isInstance(config) ? type.cast(config) : null;
	}

	/**
	 * Why the kit makes no start of {@code kind} in {@code chunk}, or null: no snapshot, the switch off, no config, the
	 * stage under its {@code fromStage}, or the chunk's middle nearer than its {@code minOriginDistance} to (0, 0) or to the
	 * world spawn. Asked before the kind's own siting. Any thread.
	 */
	public static @Nullable String refusal(@Nullable Snapshot snapshot, Sc2Kind kind, ChunkPos chunk) {
		if (snapshot == null) {
			return "no snapshot";
		}
		if (!snapshot.enabled()) {
			return "switched off";
		}
		Sc2Kind.Gates gates = snapshot.config(kind.id(), Sc2Kind.Gates.class);
		if (gates == null) {
			return "no config for " + kind.id();
		}
		if (snapshot.stage().level() < gates.fromStage()) {
			return "stage " + snapshot.stage().level() + " under " + gates.fromStage();
		}
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		double min = gates.minOriginDistance();
		if (Siting.fromOrigin(x, z) < min) {
			return "nearer than " + gates.minOriginDistance() + " to the origin";
		}
		double dx = x - snapshot.spawn().getX();
		double dz = z - snapshot.spawn().getZ();
		if (dx * dx + dz * dz < min * min) {
			return "nearer than " + gates.minOriginDistance() + " to the world spawn";
		}
		return null;
	}
}
