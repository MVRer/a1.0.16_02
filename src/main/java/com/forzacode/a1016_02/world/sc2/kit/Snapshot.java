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
 * this. While there is none, the switch is off, the world spawn is not known yet or the stage is under a kind's
 * {@code fromStage} (0 by default since round 3, D-108: the places generate from the world's creation), no start is made.
 *
 * @param configs    each kind's {@link Sc2Kind#config}, by id
 * @param spawnKnown false while the server is starting (before the overworld's spawn is set: the spawn-preparation
 *                   chunks get no start and no crumb), true once it runs (D-108)
 */
public record Snapshot(boolean enabled, Stage stage, BlockPos spawn, long seed, long salt, Map<String, Object> configs, boolean spawnKnown) {
	/** How often the live one is taken again (ticks). */
	public static final int REFRESH_TICKS = 100;

	private static volatile @Nullable Snapshot current;
	/** The live server's overworld generator (R12-15: no start in another dimension, whatever its biomes), or null. */
	private static volatile @Nullable Object overworld;
	/** The live server's overworld, bound with {@link #overworld}. */
	private static volatile @Nullable ServerLevel overworldLevel;

	public Snapshot {
		spawn = spawn.immutable();
		configs = Map.copyOf(configs);
	}

	/** A snapshot whose world spawn is known (tests, and every one taken once the server runs). */
	public Snapshot(boolean enabled, Stage stage, BlockPos spawn, long seed, long salt, Map<String, Object> configs) {
		this(enabled, stage, spawn, seed, salt, configs, true);
	}

	/** The snapshot in use, or null (no server, or not taken yet). Any thread. */
	public static @Nullable Snapshot current() {
		return current;
	}

	/** Takes the live one now, its spawn known. Server thread; throws if the server has no overworld or state yet. */
	public static Snapshot take(MinecraftServer server) {
		return take(server, true);
	}

	/** Takes the live one now. Server thread; throws if the server has no overworld or state yet. */
	public static Snapshot take(MinecraftServer server, boolean spawnKnown) {
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
				state.worldgenSalt(), configs, spawnKnown);
	}

	/** Publishes a fresh live one; if it cannot be taken, the last one stays (logged once). Server thread. */
	public static void refresh(MinecraftServer server) {
		try {
			current = take(server);
			bind(server);
			failed = false;
		} catch (RuntimeException e) {
			if (!failed) {
				failed = true;
				ScLog.error("[a1016] s2: the numbers could not be read", e);
			}
		}
	}

	private static volatile boolean failed;

	/**
	 * {@link #refresh} at {@code SERVER_STARTING}, its spawn not known yet (D-108: nothing is decided until the server
	 * runs and the next {@link #refresh} says it is), or nothing (a debug line) if the server is not ready yet.
	 */
	public static void refreshQuietly(MinecraftServer server) {
		try {
			current = take(server, false);
			bind(server);
		} catch (RuntimeException e) {
			A1016_02.LOGGER.debug("[a1016] s2: numbers not ready at start, taken when the server runs", e);
		}
	}

	/** The live overworld's generator (the dimension rule, {@link Decided}'s cache, the markers' height). */
	private static void bind(MinecraftServer server) {
		ServerLevel level = server.overworld();
		overworld = level.getChunkSource().getGenerator();
		overworldLevel = level;
		Decided.attach(server);
		AxesPlacement.height(level.getMinY(), level.getMaxY());
	}

	/**
	 * Tests: use this one (null for none); no dimension rule and nothing kept by {@link Decided} until the live one is
	 * taken again.
	 */
	public static void install(@Nullable Snapshot snapshot) {
		current = snapshot;
		overworld = null;
		overworldLevel = null;
	}

	/**
	 * Tests: use this one as if it were the live one (the dimension rule and {@link Decided}'s cache bound to the server's
	 * overworld), until the next refresh.
	 */
	public static void installLive(Snapshot snapshot, MinecraftServer server) {
		current = snapshot;
		bind(server);
	}

	/** The live overworld's generator while a live snapshot is in use, else null. Any thread. */
	public static @Nullable Object liveGenerator() {
		return overworld;
	}

	/**
	 * The live overworld while a live snapshot is in use, else null (FIX-R3-E: what the line asks {@link Decided} about
	 * from where it has no level of its own, its {@code /locate} marker). Any thread.
	 */
	public static @Nullable ServerLevel liveLevel() {
		return overworldLevel;
	}

	/** At server stop. */
	public static void clear() {
		current = null;
		overworld = null;
		overworldLevel = null;
		Decided.detach();
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
	 * Why the kit makes no start of {@code kind} in {@code chunk}, or null: no snapshot, the switch off, the world spawn
	 * not known yet, no config, the stage under its {@code fromStage} (0 by default: no stage gate, D-108; a value raised
	 * by hand is still honoured), or the chunk's middle nearer than its {@code minOriginDistance} (R0) to (0, 0) or to the
	 * world spawn. Asked before the kind's own siting and before {@link Decided} (these gates are never kept: the next
	 * asker meets them again). Any thread.
	 */
	public static @Nullable String refusal(@Nullable Snapshot snapshot, Sc2Kind kind, ChunkPos chunk) {
		if (snapshot == null) {
			return "no snapshot";
		}
		if (!snapshot.enabled()) {
			return "switched off";
		}
		if (!snapshot.spawnKnown()) {
			return "the world spawn not known yet";
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
