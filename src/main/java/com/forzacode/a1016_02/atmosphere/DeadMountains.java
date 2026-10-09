package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * The recorded dead mountains ({@code SiteType.DEAD_MOUNTAIN} sites) as areas: "no animals ever spawn there" and "it
 * is quieter than anywhere else". Each area is the site's circle (radius = its size, the same circle the world
 * workstream killed) between {@code deadMountainDepthBlocks} below the site and {@code deadMountainHeightBlocks}
 * above it.
 *
 * <p>The areas are rebuilt from the site registry on the server thread every second and published as an immutable
 * snapshot, so worldgen threads can ask {@link #refusesSpawn} too. Each player's client gets the areas near them
 * ({@link DeadMountainsPayload}) when they enter a new chunk or the areas change; the client fades ambience and
 * music while standing inside one.
 */
public final class DeadMountains {
	/** One dead mountain: columns within {@code radius} of ({@code x}, {@code z}), from {@code minY} to {@code maxY}. */
	public record Area(int x, int z, int radius, int minY, int maxY) {
		public static final StreamCodec<ByteBuf, Area> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, Area::x,
				ByteBufCodecs.INT, Area::z,
				ByteBufCodecs.VAR_INT, Area::radius,
				ByteBufCodecs.INT, Area::minY,
				ByteBufCodecs.INT, Area::maxY,
				Area::new);

		/** Block column test, the same as the world's: {@code dx² + dz² <= radius²}, so the edge is the same sharp line. */
		public boolean contains(int bx, int by, int bz) {
			if (by < minY || by > maxY) {
				return false;
			}
			long dx = bx - x;
			long dz = bz - z;
			return dx * dx + dz * dz <= (long) radius * radius;
		}

		public boolean contains(BlockPos pos) {
			return contains(pos.getX(), pos.getY(), pos.getZ());
		}

		/** The block a player at this position stands in. */
		public boolean contains(double px, double py, double pz) {
			return contains(Mth.floor(px), Mth.floor(py), Mth.floor(pz));
		}

		/** Horizontal distance from a point to the edge (0 or less inside the circle). */
		public double edgeDistance(double px, double pz) {
			double dx = px - (x + 0.5);
			double dz = pz - (z + 0.5);
			return Math.sqrt(dx * dx + dz * dz) - radius;
		}

		static Area of(SiteRegistry.Site site, int depth, int height) {
			BlockPos pos = site.pos();
			return new Area(pos.getX(), pos.getZ(), Math.max(0, site.size()), pos.getY() - Math.max(0, depth), pos.getY() + Math.max(0, height));
		}
	}

	private record Snapshot(Map<ResourceKey<Level>, List<Area>> byDimension, boolean noAnimals, long version) {
		static final Snapshot EMPTY = new Snapshot(Map.of(), true, 0);

		List<Area> in(ResourceKey<Level> dimension) {
			return byDimension.getOrDefault(dimension, List.of());
		}
	}

	private record Sent(ResourceKey<Level> dimension, ChunkPos chunk, long version, List<Area> areas) {
	}

	/** At most this many areas go to a client, nearest first. */
	static final int MAX_SENT = 64;
	private static final int REFRESH_TICKS = 20;
	private static final int SYNC_TICKS = 10;

	private static volatile Snapshot snapshot = Snapshot.EMPTY;
	/** What each online player's client was last told. Server thread only. */
	private static final Map<UUID, Sent> SENT = new HashMap<>();

	private DeadMountains() {
	}

	// --- areas (any thread) ---

	/** The recorded dead mountains of a dimension. */
	public static List<Area> in(ResourceKey<Level> dimension) {
		return snapshot.in(dimension);
	}

	/** Whether this block is inside a recorded dead mountain. */
	public static boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
		for (Area area : snapshot.in(dimension)) {
			if (area.contains(pos)) {
				return true;
			}
		}
		return false;
	}

	/** The areas whose edge is within {@code reach} blocks of ({@code px}, {@code pz}), nearest first, at most {@link #MAX_SENT}. */
	public static List<Area> near(ResourceKey<Level> dimension, double px, double pz, double reach) {
		List<Area> found = new ArrayList<>();
		for (Area area : snapshot.in(dimension)) {
			if (area.edgeDistance(px, pz) <= reach) {
				found.add(area);
			}
		}
		found.sort(Comparator.comparingDouble(a -> a.edgeDistance(px, pz)));
		return found.size() > MAX_SENT ? List.copyOf(found.subList(0, MAX_SENT)) : List.copyOf(found);
	}

	/**
	 * The spawn rule: a natural (or world generation) spawn of a passive mob (any friendly category: animals, bats,
	 * fish, squid, axolotls) inside a dead mountain is refused. Monsters, spawners, eggs, breeding and commands are
	 * never touched, and no mob is ever removed. Cheap; any thread (worldgen asks it too).
	 */
	public static boolean refusesSpawn(EntityType<?> type, EntitySpawnReason reason, ResourceKey<Level> dimension, BlockPos pos) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return false;
		}
		MobCategory category = type.getCategory();
		if (!category.isFriendly() || category == MobCategory.MISC) {
			return false;
		}
		Snapshot current = snapshot;
		return current.noAnimals() && !current.byDimension().isEmpty() && contains(dimension, pos);
	}

	/** Rebuilds the areas from the site registry now. Server thread (also tests, right after recording a site). */
	public static void refresh() {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		Map<ResourceKey<Level>, List<Area>> byDimension = new HashMap<>();
		for (SiteRegistry.Site site : Services.sites().all()) {
			if (site.type() == SiteType.DEAD_MOUNTAIN) {
				byDimension.computeIfAbsent(site.dimension(), d -> new ArrayList<>())
						.add(Area.of(site, cfg.deadMountainDepthBlocks, cfg.deadMountainHeightBlocks));
			}
		}
		byDimension.replaceAll((dimension, areas) -> List.copyOf(areas));
		Snapshot old = snapshot;
		if (!old.byDimension().equals(byDimension) || old.noAnimals() != cfg.deadMountainNoAnimals) {
			snapshot = new Snapshot(Map.copyOf(byDimension), cfg.deadMountainNoAnimals, old.version() + 1);
		}
	}

	// --- client sync (server thread) ---

	static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % REFRESH_TICKS == 0) {
			refresh();
		}
		if (now % SYNC_TICKS == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				sync(player);
			}
		}
	}

	/** Sends this player the dead mountains near them if they changed chunk or dimension, or the areas changed. */
	static void sync(ServerPlayer player) {
		Snapshot current = snapshot;
		ResourceKey<Level> dimension = player.level().dimension();
		ChunkPos chunk = player.chunkPosition();
		Sent last = SENT.get(player.getUUID());
		if (last != null && last.version() == current.version() && last.dimension().equals(dimension) && last.chunk().equals(chunk)) {
			return;
		}
		List<Area> areas = near(dimension, player.getX(), player.getZ(), AtmosphereConfig.get().deadMountainSendBlocks);
		// The client starts empty after every join; tell it only what it does not know yet.
		boolean changed = last == null ? !areas.isEmpty() : !last.dimension().equals(dimension) || !last.areas().equals(areas);
		if (changed) {
			DeadMountainsPayload.send(player, new DeadMountainsPayload(dimension, areas));
		}
		SENT.put(player.getUUID(), new Sent(dimension, chunk, current.version(), areas));
	}

	/** The areas this player's client was last sent (for status). */
	static List<Area> sentTo(ServerPlayer player) {
		Sent last = SENT.get(player.getUUID());
		return last == null ? List.of() : last.areas();
	}

	static void onLeave(ServerPlayer player) {
		SENT.remove(player.getUUID());
	}

	static void clear() {
		SENT.clear();
		snapshot = Snapshot.EMPTY;
	}
}
