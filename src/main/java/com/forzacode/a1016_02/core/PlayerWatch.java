package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Who takes part and what each player is doing and has done. Everyone takes part in the one story (D-058): a
 * participant is an online player who is not a spectator. A dead player on the death screen still takes part (so a death does not flap
 * sessions or clocks) but is not {@linkplain #isTargetable targetable}; a hardcore death makes them a spectator,
 * and then they stop. Live values (stillness, combat, join time) are per player and per session; the footprint
 * (blocks placed and dug by players) and per-chunk visits persist; personal records live in
 * {@link HerobrineState} by UUID (D-061).
 * <p>
 * D-062.1: keep a player's UUID, never the {@link ServerPlayer} (it changes on respawn and rejoin), and look the
 * player up again with {@link #participant(MinecraftServer, UUID)}; skip quietly if that comes back empty.
 * Server thread only.
 */
public final class PlayerWatch {
	/**
	 * Where the online players, the state and the participation events go. Tests in any package make their own and
	 * pass {@code new PlayerWatch(source)} to the code under test, so two-player tests never depend on who else is
	 * online on the shared game test server.
	 */
	public interface Source {
		/** Every online player, spectators included, in the server's order. */
		List<ServerPlayer> online(MinecraftServer server);

		/** The online player with this UUID, or null. */
		default @Nullable ServerPlayer online(MinecraftServer server, UUID uuid) {
			for (ServerPlayer player : online(server)) {
				if (player.getUUID().equals(uuid)) {
					return player;
				}
			}
			return null;
		}

		HerobrineState state(MinecraftServer server);

		void participation(MinecraftServer server, UUID player, boolean participating);
	}

	/** The live server, the world's state and {@link HerobrineEvents#PARTICIPATION}. */
	static final Source SERVER = new Source() {
		@Override
		public List<ServerPlayer> online(MinecraftServer server) {
			return server.getPlayerList().getPlayers();
		}

		@Override
		public @Nullable ServerPlayer online(MinecraftServer server, UUID uuid) {
			return server.getPlayerList().getPlayer(uuid);
		}

		@Override
		public HerobrineState state(MinecraftServer server) {
			return HerobrineState.get(server);
		}

		@Override
		public void participation(MinecraftServer server, UUID player, boolean participating) {
			HerobrineEvents.PARTICIPATION.invoker().onParticipation(server, player, participating);
		}
	};

	private static final class Live {
		Vec3 lastPos = Vec3.ZERO;
		long stillTicks;
		long joinTick;
		long lastCombatTick = Long.MIN_VALUE;
	}

	private final Source source;
	private final Map<UUID, Live> live = new HashMap<>();
	/** Who core saw join and not leave yet. A joining player is not in the player list while Fabric's JOIN runs. */
	private final Set<UUID> connected = new HashSet<>();
	/** Who was a participant at the last {@link #tickParticipation}, in the order they started. */
	private final Set<UUID> participating = new LinkedHashSet<>();

	PlayerWatch() {
		this(SERVER);
	}

	/** A watch over a test's own online list and state (see {@link Source}). Production uses {@link Services#watch()}. */
	public PlayerWatch(Source source) {
		this.source = source;
	}

	// --- participants (D-058) ---

	/**
	 * Every participant (online and not a spectator, dead or alive), in first-join order: the order they first
	 * took part in this world, then by UUID for anyone not recorded yet. A new list each call.
	 */
	public List<ServerPlayer> participants(MinecraftServer server) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : source.online(server)) {
			if (!player.isSpectator()) {
				list.add(player);
			}
		}
		if (list.size() > 1) {
			HerobrineState state = source.state(server);
			Map<UUID, Integer> order = new HashMap<>();
			for (ServerPlayer player : list) {
				int index = state.joinOrder(player.getUUID());
				order.put(player.getUUID(), index < 0 ? Integer.MAX_VALUE : index);
			}
			list.sort(Comparator.<ServerPlayer>comparingInt(p -> order.get(p.getUUID())).thenComparing(ServerPlayer::getUUID));
		}
		return list;
	}

	/** True if this very player object is online and not a spectator. A stale object (before a respawn) is not. */
	public boolean isParticipant(ServerPlayer player) {
		return !player.isSpectator() && isOnline(player);
	}

	/** True if something may happen to this player right now: a participant who is alive (not dead, not removed). */
	public boolean isTargetable(ServerPlayer player) {
		return isParticipant(player) && player.isAlive();
	}

	/** A fresh lookup: the participant with this UUID, or empty if they are offline or not a participant (D-062.1). */
	public Optional<ServerPlayer> participant(MinecraftServer server, UUID uuid) {
		return Optional.ofNullable(source.online(server, uuid)).filter(this::isParticipant);
	}

	/**
	 * Who shares a moment with {@code target} (D-060): the target first (even if not a participant or not
	 * targetable), then every other targetable participant in the target's level within
	 * {@link Pacing#sharedMomentRadius} blocks of them, in first-join order. Fog surges, silences and fired cards
	 * reach all of them.
	 */
	public List<ServerPlayer> sharingWith(ServerPlayer target) {
		List<ServerPlayer> list = new ArrayList<>();
		list.add(target);
		double radius = ModConfig.pacing().sharedMomentRadius;
		for (ServerPlayer player : participants(target.level().getServer())) {
			if (!player.getUUID().equals(target.getUUID()) && player.isAlive() && player.level() == target.level()
					&& player.distanceToSqr(target) <= radius * radius) {
				list.add(player);
			}
		}
		return list;
	}

	// --- stored owners (D-061) ---

	/** The stored owner of a once-per-world thing ({@code "<ws>:<what>"}), or empty if none was chosen yet. */
	public Optional<UUID> owner(MinecraftServer server, String key) {
		return source.state(server).owner(key);
	}

	/**
	 * The owner of a once-per-world thing that needs one player's record (the house copy's house, "your" first
	 * chest, the cairn's first block). Returns the stored choice; if none is stored, chooses now among the online
	 * participants (the most play ticks of their own, ties to the earliest first join), stores it and returns it.
	 * Empty, storing nothing, when nobody takes part right now. A stored choice persists and never flips, even when
	 * that player is offline or another one has played longer since.
	 */
	public Optional<UUID> ownerFor(MinecraftServer server, String key) {
		return ownerFor(server, key, player -> true);
	}

	/**
	 * {@link #ownerFor(MinecraftServer, String)}, choosing only among the online participants {@code eligible} accepts
	 * (for example "has placed a block", so a story that needs that player's first block never waits on someone who
	 * has none). Empty, storing nothing, while no participant is eligible. The stored choice is returned as it is,
	 * whatever {@code eligible} says now: it persists and never flips.
	 */
	public Optional<UUID> ownerFor(MinecraftServer server, String key, Predicate<UUID> eligible) {
		HerobrineState state = source.state(server);
		Optional<UUID> stored = state.owner(key);
		if (stored.isPresent()) {
			return stored;
		}
		UUID best = null;
		long bestTicks = -1;
		for (ServerPlayer player : participants(server)) {
			if (!eligible.test(player.getUUID())) {
				continue;
			}
			long ticks = state.playTicks(player.getUUID());
			if (ticks > bestTicks) {
				best = player.getUUID();
				bestTicks = ticks;
			}
		}
		return best == null ? Optional.empty() : Optional.of(state.setOwner(key, best));
	}

	/**
	 * For migrations: stores {@code player} as the owner of {@code key} only if nothing is stored for it yet, online or
	 * not (an old save whose once-per-world thing already belongs to someone, such as a house copy from before
	 * multiplayer, keeps that player instead of waiting for {@link #ownerFor}'s rule to pick them). Only a known player
	 * is stored. Returns the stored owner: the one already there (never replaced), else {@code player}; empty, storing
	 * nothing, if nothing was stored and {@code player} never took part here.
	 */
	public Optional<UUID> adoptOwner(MinecraftServer server, String key, UUID player) {
		HerobrineState state = source.state(server);
		Optional<UUID> stored = state.owner(key);
		if (stored.isPresent() || state.player(player).isEmpty()) {
			return stored;
		}
		return Optional.of(state.setOwner(key, player));
	}

	// --- live values (per player, per session) ---

	/** Ticks the player has not moved (turning the head does not count as moving). */
	public long stillTicks(ServerPlayer player) {
		Live data = live.get(player.getUUID());
		return data == null ? 0 : data.stillTicks;
	}

	/** Ticks since the player hurt something or was hurt by something, or {@link Long#MAX_VALUE} this session. */
	public long ticksSinceCombat(ServerPlayer player) {
		Live data = live.get(player.getUUID());
		return data == null || data.lastCombatTick == Long.MIN_VALUE ? Long.MAX_VALUE : now(player) - data.lastCombatTick;
	}

	/** Ticks since the player joined this session. */
	public long ticksSinceJoin(ServerPlayer player) {
		Live data = live.get(player.getUUID());
		return data == null ? 0 : now(player) - data.joinTick;
	}

	public boolean isSleeping(ServerPlayer player) {
		return player.isSleeping();
	}

	/** The player's base: their respawn point, otherwise the first block that player placed (D-061). */
	public Optional<GlobalPos> base(ServerPlayer player) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn != null) {
			return Optional.of(respawn.respawnData().globalPos());
		}
		PlacedBlock first = source.state(player.level().getServer()).firstBlocks(player.getUUID()).block();
		return first == null ? Optional.empty() : Optional.of(first.pos());
	}

	// --- footprint (all players) ---

	/** The in-game day ({@link GameClock#day}) a participant was last in or next to this chunk, or -1 if never. */
	public long lastVisitDay(ServerLevel level, ChunkPos chunk) {
		return WatchData.get(level.getServer()).footprint(level.dimension()).visits.get(chunk.pack());
	}

	/** True if a player placed the block now at {@code pos} (and nobody broke it since). */
	public boolean wasPlacedByPlayer(ServerLevel level, BlockPos pos) {
		return footprint(level).placed.contains(pos);
	}

	/** True if a player dug out the natural block that was at {@code pos}. */
	public boolean wasDugByPlayer(ServerLevel level, BlockPos pos) {
		return footprint(level).dug.contains(pos);
	}

	/** Player-placed blocks within {@code radius} (cube) that are still this block. */
	public List<BlockPos> placedNear(ServerLevel level, BlockPos center, int radius, Block block) {
		return placedNear(level, center, radius, state -> state.is(block));
	}

	/** Player-placed blocks within {@code radius} (cube) that are still in this tag. */
	public List<BlockPos> placedNear(ServerLevel level, BlockPos center, int radius, TagKey<Block> tag) {
		return placedNear(level, center, radius, state -> state.is(tag));
	}

	public List<BlockPos> placedNear(ServerLevel level, BlockPos center, int radius, Predicate<BlockState> filter) {
		List<BlockPos> found = new ArrayList<>();
		footprint(level).placed.forEachNear(center, radius, packed -> {
			BlockPos pos = BlockPos.of(packed);
			if (filter.test(level.getBlockState(pos))) {
				found.add(pos);
			}
		});
		return found;
	}

	/** Positions within {@code radius} (cube) where players dug out natural blocks. */
	public List<BlockPos> dugNear(ServerLevel level, BlockPos center, int radius) {
		List<BlockPos> found = new ArrayList<>();
		footprint(level).dug.forEachNear(center, radius, packed -> found.add(BlockPos.of(packed)));
		return found;
	}

	// --- hooks, called by core ---

	/**
	 * Fabric's JOIN. The player is not in the player list yet, so it only remembers them as connected (they count
	 * as online from here on) and records them as a known player if they take part. The participation event comes
	 * from {@link #tickParticipation} at the end of the tick, once they are fully online.
	 */
	void onJoin(ServerPlayer player) {
		UUID uuid = player.getUUID();
		connected.add(uuid);
		Live data = live.computeIfAbsent(uuid, k -> new Live());
		data.joinTick = now(player);
		data.lastPos = player.position();
		data.stillTicks = 0;
		HerobrineState state = source.state(player.level().getServer());
		String name = player.getName().getString();
		if (isParticipant(player)) {
			state.recordPlayer(uuid, name);
		} else {
			state.updateName(uuid, name);
		}
	}

	/** Fabric's DISCONNECT (the player is still listed while it runs). Ends that player's participation only. */
	void onLeave(ServerPlayer player) {
		UUID uuid = player.getUUID();
		live.remove(uuid);
		connected.remove(uuid);
		if (participating.remove(uuid)) {
			source.participation(player.level().getServer(), uuid, false);
		}
	}

	/** Server stop: forgets everything per session. No participation events. */
	void clear() {
		live.clear();
		connected.clear();
		participating.clear();
	}

	void onCombat(ServerPlayer player) {
		live.computeIfAbsent(player.getUUID(), k -> new Live()).lastCombatTick = now(player);
	}

	/**
	 * Every server tick, first: compares the participants with the last tick's. Anyone new (joined, or left
	 * spectator mode) becomes a known player; then the clocks advance ({@link GameClock}); then
	 * {@link HerobrineEvents#PARTICIPATION} fires once for each change. A cheap diff of a few UUIDs.
	 */
	void tickParticipation(MinecraftServer server) {
		List<ServerPlayer> now = participants(server);
		HerobrineState state = source.state(server);
		Set<UUID> current = new HashSet<>();
		List<UUID> started = new ArrayList<>();
		List<UUID> uuids = new ArrayList<>(now.size());
		for (ServerPlayer player : now) {
			UUID uuid = player.getUUID();
			current.add(uuid);
			uuids.add(uuid);
			if (!participating.contains(uuid)) {
				state.recordPlayer(uuid, player.getName().getString());
				started.add(uuid);
			}
		}
		List<UUID> stopped = new ArrayList<>();
		for (UUID uuid : participating) {
			if (!current.contains(uuid)) {
				stopped.add(uuid);
			}
		}
		participating.removeAll(stopped);
		participating.addAll(started);
		GameClock.advance(state, uuids);
		stopped.forEach(uuid -> source.participation(server, uuid, false));
		started.forEach(uuid -> source.participation(server, uuid, true));
	}

	void tick(MinecraftServer server) {
		boolean sample = server.getTickCount() % ModConfig.pacing().watchSampleTicks() == 0;
		for (ServerPlayer player : source.online(server)) {
			Live data = live.computeIfAbsent(player.getUUID(), k -> new Live());
			Vec3 pos = player.position();
			if (pos.distanceToSqr(data.lastPos) > 1.0E-4) {
				data.stillTicks = 0;
				data.lastPos = pos;
			} else {
				data.stillTicks++;
			}
			if (sample && !player.isSpectator()) {
				recordVisit(player);
			}
		}
	}

	private void recordVisit(ServerPlayer player) {
		ServerLevel level = player.level();
		WatchData data = WatchData.get(level.getServer());
		WatchData.Footprint footprint = data.footprint(level.dimension());
		int day = (int) GameClock.day(level.getServer());
		int radius = ModConfig.pacing().visitRadiusChunks;
		ChunkPos center = player.chunkPosition();
		boolean changed = false;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				changed |= footprint.visits.put(ChunkPos.pack(center.x() + dx, center.z() + dz), day) != day;
			}
		}
		if (changed) {
			data.setDirty();
		}
	}

	/**
	 * Internal: called by core's block placement mixin. Every player's placement feeds the footprint; a
	 * participant's also records their own first block, crafting table and chest.
	 */
	public void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		WatchData data = WatchData.get(level.getServer());
		WatchData.Footprint footprint = data.footprint(level.dimension());
		footprint.dug.remove(pos.asLong());
		footprint.placed.add(pos.asLong(), ModConfig.pacing().footprintMaxPerDimension);
		data.setDirty();

		if (isParticipant(player)) {
			recordFirsts(source.state(level.getServer()), player.getUUID(), new PlacedBlock(GlobalPos.of(level.dimension(), pos.immutable()), state));
		}
	}

	/** Records a placement as one of that player's first blocks where the slot is still empty. */
	static void recordFirsts(HerobrineState herobrine, UUID player, PlacedBlock placed) {
		herobrine.recordFirst(player, HerobrineState.FirstKind.BLOCK, placed);
		if (placed.state().is(Blocks.CRAFTING_TABLE)) {
			herobrine.recordFirst(player, HerobrineState.FirstKind.CRAFTING_TABLE, placed);
		}
		if (placed.state().getBlock() instanceof ChestBlock) {
			herobrine.recordFirst(player, HerobrineState.FirstKind.CHEST, placed);
		}
	}

	void onBroken(ServerLevel level, BlockPos pos) {
		WatchData data = WatchData.get(level.getServer());
		WatchData.Footprint footprint = data.footprint(level.dimension());
		if (!footprint.placed.remove(pos.asLong())) {
			footprint.dug.add(pos.asLong(), ModConfig.pacing().footprintMaxPerDimension);
		}
		data.setDirty();
	}

	/**
	 * Online means listed as this very object, or (only while Fabric's JOIN runs, before the list has them) seen
	 * joining and not gone since. A player object from before a respawn, or one that never joined, is not online.
	 */
	private boolean isOnline(ServerPlayer player) {
		ServerPlayer listed = source.online(player.level().getServer(), player.getUUID());
		if (listed != null) {
			return listed == player;
		}
		return connected.contains(player.getUUID()) && !player.hasDisconnected() && !player.isRemoved();
	}

	private WatchData.Footprint footprint(ServerLevel level) {
		return WatchData.get(level.getServer()).footprint(level.dimension());
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().getTickCount();
	}
}
