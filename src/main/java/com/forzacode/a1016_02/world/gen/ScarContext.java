package com.forzacode.a1016_02.world.gen;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Density;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * An immutable snapshot of everything worldgen needs to decide scars: seed, salt, the profile's habits and
 * density, and the config. Taken on the server thread and published through a volatile field, so worldgen
 * threads never touch {@link HerobrineState}. Planners (and their caches) hang off the snapshot, so a new
 * profile ({@code /a1016 profile reroll}) starts fresh.
 */
public final class ScarContext {
	private static volatile @Nullable ScarContext current;

	private final long seed;
	private final long salt;
	private final WorldProfile profile;
	private final WorldConfig config;
	private final int minFromSpawn;
	private final long base;
	private final Map<ResourceKey<Level>, ScarPlanner> planners = new ConcurrentHashMap<>();

	public ScarContext(long seed, long salt, WorldProfile profile, WorldConfig config, int minFromSpawn) {
		this.seed = seed;
		this.salt = salt;
		this.profile = profile;
		this.config = config;
		this.minFromSpawn = minFromSpawn;
		this.base = Hash.of(seed, salt, profileHash(profile));
	}

	/** The snapshot in use, or null before a server has started. Any thread. */
	public static @Nullable ScarContext current() {
		return current;
	}

	/** Takes a new snapshot if the profile changed (or there is none). Server thread. */
	public static void refresh(MinecraftServer server) {
		WorldProfile profile = HerobrineState.get(server).profile();
		long salt = WorldData.get(server).salt();
		ScarContext now = current;
		if (now != null && now.profile == profile && now.salt == salt) {
			return;
		}
		long seed = server.getWorldGenSettings().options().seed();
		current = new ScarContext(seed, salt, profile, WorldConfig.get(), ModConfig.pacing().oldScarMinFromSpawn);
		A1016_02.LOGGER.info("[a1016] world: scar snapshot habits={} density={}", profile.habits(), profile.density());
	}

	/** Tests: use this snapshot. */
	public static void install(@Nullable ScarContext context) {
		current = context;
	}

	public static void clear() {
		current = null;
	}

	/** The overworld's planner; null for other dimensions (scars only grow in the overworld). */
	public @Nullable ScarPlanner planner(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		return planners.computeIfAbsent(level.dimension(), k -> new ScarPlanner(this, level));
	}

	public long seed() {
		return seed;
	}

	public long base() {
		return base;
	}

	public Set<Habit> habits() {
		return profile.habits();
	}

	public Density density() {
		return profile.density();
	}

	public WorldConfig config() {
		return config;
	}

	public int minFromSpawn() {
		return minFromSpawn;
	}

	/** A hash of the profile that is the same on every run (enum hash codes are not). */
	private static long profileHash(WorldProfile profile) {
		Set<String> habits = new TreeSet<>();
		profile.habits().forEach(h -> habits.add(h.name()));
		return (String.join(",", habits) + "/" + profile.density().name()).hashCode();
	}
}
