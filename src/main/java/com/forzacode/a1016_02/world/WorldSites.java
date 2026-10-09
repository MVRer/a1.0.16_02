package com.forzacode.a1016_02.world;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * Records the scars' sites in {@link Services#sites()} (thread-safe, so worldgen threads call it) and remembers
 * the inside of every build in {@link WorldData} (handed to the server thread through a queue).
 */
public final class WorldSites {
	private record PendingBuild(GlobalPos site, BoundingBox interior) {
	}

	/** {@link WorldData#single} key of the one ruined hut. */
	public static final String HUT = "ruined_hut";
	/** {@link WorldData#single} key of the core pyramid. */
	public static final String CORE_PYRAMID = "core_pyramid";

	private static final Queue<PendingBuild> PENDING = new ConcurrentLinkedQueue<>();
	/** The one-per-world sites already recorded (from {@link WorldData}), readable by worldgen threads. */
	private static volatile Map<String, GlobalPos> singles = Map.of();

	private WorldSites() {
	}

	/**
	 * Records a site once (a chunk generated twice after a crash, or a site recorded early at server start, must not
	 * record it twice). Any thread; synchronized so the check and the record are one step.
	 */
	public static synchronized SiteRegistry.@Nullable Site record(SiteType type, ResourceKey<Level> dimension, BlockPos pos, int size, @Nullable BoundingBox interior) {
		GlobalPos global = GlobalPos.of(dimension, pos.immutable());
		if (!Services.sites().find(type, global, 0).isEmpty()) {
			return null;
		}
		SiteRegistry.Site site = Services.sites().record(type, dimension, pos, size);
		if (interior != null) {
			PENDING.add(new PendingBuild(global, interior));
		}
		A1016_02.LOGGER.debug("[a1016] world: site {} at {} size {}", type, pos.toShortString(), size);
		return site;
	}

	/** Moves build interiors recorded by worldgen into the saved data. Server thread. */
	public static void drain(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		WorldData data = WorldData.get(server);
		PendingBuild build;
		while ((build = PENDING.poll()) != null) {
			data.putBuild(build.site(), build.interior());
		}
	}

	/** Publishes the recorded one-per-world sites to worldgen threads. Server thread. */
	public static void setSingles(Map<String, GlobalPos> recorded) {
		singles = Map.copyOf(recorded);
	}

	/**
	 * Publishes the one-per-world sites this world already recorded, synchronously at server start, before any chunk
	 * generates: a hut moved by a profile reroll then never records a second site, even before the warm-up runs.
	 */
	public static void loadSingles(MinecraftServer server) {
		setSingles(WorldData.get(server).singles());
	}

	/** True if a different site was already recorded for this one-per-world key (after a profile reroll moved it). Any thread. */
	public static boolean isOtherSingle(String key, GlobalPos pos) {
		GlobalPos recorded = singles.get(key);
		return recorded != null && !recorded.equals(pos);
	}

	/** Called when a server stops. */
	static void clear() {
		PENDING.clear();
		singles = Map.of();
	}
}
