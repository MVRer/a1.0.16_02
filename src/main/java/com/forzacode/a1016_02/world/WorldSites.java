package com.forzacode.a1016_02.world;

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

	private static final Queue<PendingBuild> PENDING = new ConcurrentLinkedQueue<>();

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

	/** Called when a server stops. */
	static void clear() {
		PENDING.clear();
	}
}
