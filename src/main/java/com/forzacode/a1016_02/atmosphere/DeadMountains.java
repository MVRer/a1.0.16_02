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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The recorded dead mountains ({@code SiteType.DEAD_MOUNTAIN} sites): "no animals ever spawn there" and "it is quieter
 * than anywhere else". A position counts as dead mountain when its column is inside a site's circle (radius = the
 * site's size) AND that column's surface is dead ground ({@link #deadGround}): the world workstream turned its grass
 * to dirt and stripped its plants, so a column still topped by grass or anything growing is alive. Living grass below
 * the dead line inside the same circle is therefore normal, and the edge stays the world's sharp line. The column
 * counts at any height (a cave under the dead hill is quiet too).
 *
 * <p>The circles are rebuilt from the site registry on the server thread every second and published as an immutable
 * snapshot for natural spawns and the client sync; world-generation spawns read the registry live instead
 * ({@link #refusesSpawn}). Each player's client gets the circles near them ({@link DeadMountainsPayload}) when they
 * enter a new chunk or the circles change, and does the same ground check under the player.
 */
public final class DeadMountains {
	/** One dead mountain's circle: columns within {@code radius} of ({@code x}, {@code z}). */
	public record Area(int x, int z, int radius) {
		public static final StreamCodec<ByteBuf, Area> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, Area::x,
				ByteBufCodecs.INT, Area::z,
				ByteBufCodecs.VAR_INT, Area::radius,
				Area::new);

		/** Block column test, the same as the world's: {@code dx² + dz² <= radius²}. */
		public boolean inCircle(int bx, int bz) {
			long dx = bx - x;
			long dz = bz - z;
			return dx * dx + dz * dz <= (long) radius * radius;
		}

		/** Horizontal distance from a point to the edge (0 or less inside the circle). */
		public double edgeDistance(double px, double pz) {
			double dx = px - (x + 0.5);
			double dz = pz - (z + 0.5);
			return Math.sqrt(dx * dx + dz * dz) - radius;
		}

		static Area of(SiteRegistry.Site site) {
			return new Area(site.pos().getX(), site.pos().getZ(), Math.max(0, site.size()));
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

	// --- the ground (both sides, any thread) ---

	/**
	 * Whether a column's surface is dead ground: the heightmap top, looking through snow layers, is neither grass-like
	 * ground (grass, moss, mycelium) nor anything growing (plants, leaves, logs). Dirt, stone, gravel, sand, snow,
	 * water and what the player built all count as dead. A column that is not loaded counts as alive.
	 */
	public static boolean deadGround(LevelReader level, int x, int z) {
		int minY = level.getMinY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
		BlockState state = level.getBlockState(pos);
		while (state.is(Blocks.SNOW) && pos.getY() > minY) {
			pos.move(0, -1, 0);
			state = level.getBlockState(pos);
		}
		return pos.getY() >= minY && !state.isAir() && !living(state);
	}

	/** Grass-like ground and anything growing: what a dead mountain never has on top. */
	static boolean living(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PALE_MOSS_BLOCK) || state.is(Blocks.MYCELIUM)
				|| state.is(Blocks.MOSS_CARPET) || state.is(Blocks.VINE) || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)
				|| state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.SAPLINGS) || state.getBlock() instanceof VegetationBlock;
	}

	/** Whether this column is dead mountain: inside one of {@code areas} and on dead ground. The client's quiet and the spawn rule both use this. */
	public static boolean inside(LevelReader level, List<Area> areas, int x, int z) {
		for (Area area : areas) {
			if (area.inCircle(x, z)) {
				return deadGround(level, x, z);
			}
		}
		return false;
	}

	// --- areas (any thread) ---

	/** The recorded dead mountains' circles in a dimension (refreshed every second). */
	public static List<Area> in(ResourceKey<Level> dimension) {
		return snapshot.in(dimension);
	}

	/** Whether this position is dead mountain (by the refreshed circles). */
	public static boolean contains(Level level, BlockPos pos) {
		return inside(level, in(level.dimension()), pos.getX(), pos.getZ());
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
	 * The spawn rule: a natural or world-generation spawn of a passive mob (any friendly category: animals, bats,
	 * fish, squid, axolotls) on dead mountain is refused. Monsters, spawners, eggs, breeding and commands are never
	 * touched, and no mob is ever removed. Natural spawns use the circles refreshed every second; world generation
	 * reads the site registry live, so a site world records while generating the mountain covers its own chunks.
	 * Remaining gap: creatures spawned by a chunk generated before world recorded its mountain's site are not refused.
	 * Any thread (worldgen asks it too).
	 */
	public static boolean refusesSpawn(EntityType<?> type, EntitySpawnReason reason, ServerLevelAccessor level, BlockPos pos) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return false;
		}
		MobCategory category = type.getCategory();
		if (!category.isFriendly() || category == MobCategory.MISC || !snapshot.noAnimals()) {
			return false;
		}
		ResourceKey<Level> dimension = level.getLevel().dimension();
		List<Area> areas = reason == EntitySpawnReason.CHUNK_GENERATION ? liveAreas(dimension, pos) : snapshot.in(dimension);
		return inside(level, areas, pos.getX(), pos.getZ());
	}

	/** The circles that contain this column, straight from the site registry (thread-safe). */
	private static List<Area> liveAreas(ResourceKey<Level> dimension, BlockPos pos) {
		List<Area> found = new ArrayList<>(1);
		for (SiteRegistry.Site site : Services.sites().all()) {
			if (site.type() == SiteType.DEAD_MOUNTAIN && site.dimension().equals(dimension)) {
				Area area = Area.of(site);
				if (area.inCircle(pos.getX(), pos.getZ())) {
					found.add(area);
				}
			}
		}
		return found;
	}

	/** Rebuilds the circles from the site registry now. Server thread (also tests, right after recording a site). */
	public static void refresh() {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		Map<ResourceKey<Level>, List<Area>> byDimension = new HashMap<>();
		for (SiteRegistry.Site site : Services.sites().all()) {
			if (site.type() == SiteType.DEAD_MOUNTAIN) {
				byDimension.computeIfAbsent(site.dimension(), d -> new ArrayList<>()).add(Area.of(site));
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

	/** Sends this player the dead mountains near them if they changed chunk or dimension, or the circles changed. */
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
