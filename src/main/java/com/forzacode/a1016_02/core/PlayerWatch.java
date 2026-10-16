package com.forzacode.a1016_02.core;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Who takes part and what each player is doing and has done. Everyone takes part in the one story (D-058): a
 * participant is an online player who is not a spectator. A dead player on the death screen still takes part (so a death does not flap
 * sessions or clocks) but is not {@linkplain #isTargetable targetable}; a hardcore death makes them a spectator,
 * and then they stop. Live values (stillness, combat, join time, {@linkplain #recentActions recent actions} and
 * {@linkplain #blindTicks blind moments}, v1.3) are per player and per session; the footprint
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

		/** The director a blind moment's rechecks go to ({@link Director#recheck}, D-077). The live one by default. */
		default Director director() {
			return Services.director();
		}
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

	/** How many actions {@link #recentActions} keeps per player (D-076): a mining run of up to six breaks is one. */
	public static final int RECENT_ACTIONS = 64;
	/** A blind moment's first recheck comes this many ticks after it starts (D-077: 1.5 s, the blind cards' own wait). */
	public static final int BLIND_FIRST_RECHECK_TICKS = 30;
	/** Then one every this many ticks while it lasts. */
	public static final int BLIND_RECHECK_EVERY_TICKS = 20;
	/** At most this many rechecks per blind moment (the last at 210 ticks). */
	public static final int BLIND_MAX_RECHECKS = 10;

	private static final class Live {
		Vec3 lastPos = Vec3.ZERO;
		long stillTicks;
		long joinTick;
		long lastCombatTick = Long.MIN_VALUE;
	}

	/**
	 * One blind moment: the player object whose client started it (held weakly, only to tell it from a respawned one; the
	 * moment is kept by UUID, D-062.1), where it started, how long it has lasted (counted each tick), how many rechecks it
	 * had.
	 */
	private static final class Blind {
		final WeakReference<ServerPlayer> player;
		final ResourceKey<Level> dimension;
		long ticks;
		int rechecks;

		Blind(ServerPlayer player) {
			this.player = new WeakReference<>(player);
			this.dimension = player.level().dimension();
		}
	}

	private final Source source;
	private final Map<UUID, Live> live = new HashMap<>();
	/** Each participant's recent actions, newest first (session only). */
	private final Map<UUID, ArrayDeque<PlayerAction>> actions = new HashMap<>();
	/** Who is blind now, in the order their moments started (session only). */
	private final Map<UUID, Blind> blind = new LinkedHashMap<>();
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

	// --- recent actions (D-076, per player, session only) ---

	/**
	 * What this player did lately, newest first, at most {@link #RECENT_ACTIONS} (64): mining runs
	 * ({@link PlayerAction.Mining}: each block they broke, with its state, position and tick, grouped while each break comes
	 * less than 3 s after the last, up to six to an entry), doors, trapdoors and fence gates they opened or closed by hand
	 * ({@link PlayerAction.DoorUse}) and containers they opened ({@link PlayerAction.ContainerOpen}). Only a participant's
	 * actions are kept, and only for their session: forgotten when they stop taking part (PARTICIPATION false: they left or
	 * turned spectator) and when the server stops; a death keeps them. A new list each call; empty for someone with none.
	 */
	public List<PlayerAction> recentActions(ServerPlayer player) {
		ArrayDeque<PlayerAction> ring = actions.get(player.getUUID());
		return ring == null ? List.of() : List.copyOf(ring);
	}

	/** The mining bursts among {@link #recentActions} (2 to 6 breaks, {@link PlayerAction.Mining#isBurst}), newest first. */
	public List<PlayerAction.Mining> recentBursts(ServerPlayer player) {
		ArrayDeque<PlayerAction> ring = actions.get(player.getUUID());
		if (ring == null) {
			return List.of();
		}
		List<PlayerAction.Mining> bursts = new ArrayList<>();
		for (PlayerAction action : ring) {
			if (action instanceof PlayerAction.Mining mining && mining.isBurst()) {
				bursts.add(mining);
			}
		}
		return bursts;
	}

	/**
	 * Tests and debug: a break of {@code state} at {@code pos} at {@code tick}, kept as core's break hook keeps one: it goes
	 * on the newest entry if that is mining in the same level, less than {@link PlayerAction.Mining#GAP_TICKS} before and
	 * not full, else it starts a new one. Ignored for someone who is not a participant.
	 */
	public void addBreak(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, BlockState state, long tick) {
		if (!isParticipant(player)) {
			return;
		}
		ArrayDeque<PlayerAction> ring = actions.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
		PlayerAction.Break next = new PlayerAction.Break(pos, state, tick);
		if (ring.peekFirst() instanceof PlayerAction.Mining run && run.continuedBy(dimension, next)) {
			ring.pollFirst();
			ring.addFirst(run.with(next));
			return;
		}
		push(ring, new PlayerAction.Mining(dimension, List.of(next)));
	}

	/**
	 * Tests and debug: puts {@code action} in front of the player's recent actions as it is (a mining entry stays whole and
	 * a later break never joins it unless it continues it by the hook's rule), dropping the oldest past
	 * {@link #RECENT_ACTIONS}. Ignored for someone who is not a participant.
	 */
	public void addAction(ServerPlayer player, PlayerAction action) {
		if (isParticipant(player)) {
			push(actions.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()), action);
		}
	}

	private static void push(ArrayDeque<PlayerAction> ring, PlayerAction action) {
		ring.addFirst(action);
		while (ring.size() > RECENT_ACTIONS) {
			ring.pollLast();
		}
	}

	// --- blind moments (D-077, per player, session only) ---

	/**
	 * How long this player has been blind: ticks since their client said a counting screen opened (a container or the
	 * inventory, a book or sign, the advancements, the pause menu; core's client decides which, D-077), at least 1 from the
	 * moment it starts; 0 when not blind. It ends when their client says the screen closed, and is reset when they
	 * disconnect, stop taking part, die, respawn or change dimension (a screen still open then counts again only once the
	 * client says so again). A moment belongs to the player object that started it: a respawn lists a new object, so a
	 * death no tick saw (killed and respawned between two ticks) ends it too. The server takes the signal from
	 * participants who are alive only. Counted at the end of each server tick, before the director's tick.
	 */
	public long blindTicks(ServerPlayer player) {
		Blind moment = blind.get(player.getUUID());
		return moment == null || !stillBlind(player, moment) ? 0 : Math.max(1, moment.ticks);
	}

	/**
	 * Internal: core's {@link BlindState} receiver (the player's client opened or closed a counting screen); tests call it
	 * as the client would. A start is ignored from someone who is not a participant or is dead, and a start while already
	 * blind keeps the moment going (its ticks and rechecks); an end always ends it.
	 */
	public void onBlindState(ServerPlayer player, boolean nowBlind) {
		UUID uuid = player.getUUID();
		Blind moment = blind.get(uuid);
		if (!nowBlind) {
			blind.remove(uuid);
		} else if (moment == null || !stillBlind(player, moment)) {
			if (isParticipant(player) && player.isAlive()) {
				blind.put(uuid, new Blind(player));
			} else {
				blind.remove(uuid);
			}
		}
	}

	/**
	 * Tests and debug: makes the player blind as if their client said so {@code ticks} ago (the rechecks those ticks would
	 * have had count as done). Ignored, as the payload is, for someone who is not a participant or is dead.
	 */
	public void markBlind(ServerPlayer player, long ticks) {
		if (!isParticipant(player) || !player.isAlive()) {
			return;
		}
		Blind moment = new Blind(player);
		moment.ticks = Math.max(0, ticks);
		moment.rechecks = rechecksBy(moment.ticks);
		blind.put(player.getUUID(), moment);
	}

	/** True if a blind moment's {@code ticks}-th tick brings a recheck (30, 50, 70 ...; the cap is the caller's). */
	static boolean recheckDue(long ticks) {
		return ticks >= BLIND_FIRST_RECHECK_TICKS && (ticks - BLIND_FIRST_RECHECK_TICKS) % BLIND_RECHECK_EVERY_TICKS == 0;
	}

	/** How many rechecks a blind moment has had by its {@code ticks}-th tick. */
	static int rechecksBy(long ticks) {
		if (ticks < BLIND_FIRST_RECHECK_TICKS) {
			return 0;
		}
		return (int) Math.min(BLIND_MAX_RECHECKS, 1 + (ticks - BLIND_FIRST_RECHECK_TICKS) / BLIND_RECHECK_EVERY_TICKS);
	}

	/**
	 * True while {@code player} is the very object whose client started the moment (not one listed after a respawn), a
	 * participant, alive and in the moment's dimension.
	 */
	private boolean stillBlind(ServerPlayer player, Blind moment) {
		return moment.player.get() == player && isParticipant(player) && player.isAlive() && player.level().dimension().equals(moment.dimension);
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

	/**
	 * True if a player dug out the natural block that was at {@code pos} and no player's block stands there now. A block
	 * a player places there takes the mark away while it stands; breaking it gives the mark back.
	 */
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

	/**
	 * Fabric's DISCONNECT (the player is still listed while it runs). Ends that player's participation only, and forgets
	 * their recent actions and blind moment.
	 */
	void onLeave(ServerPlayer player) {
		UUID uuid = player.getUUID();
		live.remove(uuid);
		connected.remove(uuid);
		forgetSession(uuid);
		if (participating.remove(uuid)) {
			source.participation(player.level().getServer(), uuid, false);
		}
	}

	/** Server stop: forgets everything per session. No participation events. */
	void clear() {
		live.clear();
		connected.clear();
		participating.clear();
		actions.clear();
		blind.clear();
	}

	/** A player's session ended (they left, or stopped taking part): their recent actions and blind moment go. */
	private void forgetSession(UUID uuid) {
		actions.remove(uuid);
		blind.remove(uuid);
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
		stopped.forEach(this::forgetSession);
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
		tickBlind(server);
	}

	/**
	 * Counts each blind moment on, ends those whose player is gone, dead, no longer taking part, in another dimension or
	 * listed as another object than the one that started it (a respawn, even one after a death no tick saw), and asks the
	 * director to recheck (D-077) {@link #BLIND_FIRST_RECHECK_TICKS} after a moment starts and every
	 * {@link #BLIND_RECHECK_EVERY_TICKS} while it lasts, at most {@link #BLIND_MAX_RECHECKS} times. A few UUIDs a tick.
	 */
	void tickBlind(MinecraftServer server) {
		if (blind.isEmpty()) {
			return;
		}
		List<ServerPlayer> due = new ArrayList<>();
		Iterator<Map.Entry<UUID, Blind>> moments = blind.entrySet().iterator();
		while (moments.hasNext()) {
			Map.Entry<UUID, Blind> entry = moments.next();
			ServerPlayer player = source.online(server, entry.getKey());
			Blind moment = entry.getValue();
			if (player == null || !stillBlind(player, moment)) {
				moments.remove();
				continue;
			}
			moment.ticks++;
			if (recheckDue(moment.ticks) && moment.rechecks < BLIND_MAX_RECHECKS) {
				moment.rechecks++;
				due.add(player);
			}
		}
		Director director = source.director();
		due.forEach(player -> director.recheck(server, player));
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
	 * participant's also records their own first block, crafting table and chest. A block placed in a cell a player
	 * dug takes the dug mark away while it stands and gives it back when it is broken ({@link #onBroken}).
	 */
	public void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		WatchData data = WatchData.get(level.getServer());
		WatchData.Footprint footprint = data.footprint(level.dimension());
		int cap = ModConfig.pacing().footprintMaxPerDimension;
		if (footprint.dug.remove(pos.asLong())) {
			footprint.dugUnder.add(pos.asLong(), cap);
		}
		footprint.placed.add(pos.asLong(), cap);
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

	/**
	 * Internal: a player broke the block at {@code pos}. A natural block is dug now; a placed block is no longer placed,
	 * and its cell is dug again if a player had dug it before the block went in (a torch picked up again, cobble placed
	 * and mined: the cell stays part of the dug tunnel).
	 */
	void onBroken(ServerLevel level, BlockPos pos) {
		WatchData data = WatchData.get(level.getServer());
		WatchData.Footprint footprint = data.footprint(level.dimension());
		boolean wasPlaced = footprint.placed.remove(pos.asLong());
		boolean dugBefore = footprint.dugUnder.remove(pos.asLong());
		if (!wasPlaced || dugBefore) {
			footprint.dug.add(pos.asLong(), ModConfig.pacing().footprintMaxPerDimension);
		}
		data.setDirty();
	}

	/**
	 * Internal: core's break hook (Fabric's {@code PlayerBlockBreakEvents.AFTER}): the participant broke {@code state} at
	 * {@code pos} now. Goes into their recent actions ({@link #addBreak}); any block counts, a player-placed one too.
	 */
	public void onMined(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		addBreak(player, level.dimension(), pos, state, now(player));
	}

	/**
	 * Internal: core's use hook (a mixin on {@code ServerPlayerGameMode.useItemOn}), after the player used the block at
	 * {@code pos}; {@code before} is its state and {@code menuBefore} the player's open menu just before. A door, trapdoor
	 * or fence gate whose open state changed is a {@link PlayerAction.DoorUse} (a door at its lower half); a container whose
	 * menu opened is a {@link PlayerAction.ContainerOpen}. Anything else, a use that changed nothing (an iron door by hand)
	 * or a player who is not a participant, records nothing.
	 */
	public void onUsedBlock(ServerPlayer player, ServerLevel level, BlockPos pos, @Nullable BlockState before, @Nullable AbstractContainerMenu menuBefore) {
		if (before == null || !isParticipant(player)) {
			return;
		}
		BlockState after = level.getBlockState(pos);
		if (isDoorLike(after) && after.getBlock() == before.getBlock() && after.hasProperty(BlockStateProperties.OPEN)
				&& before.hasProperty(BlockStateProperties.OPEN)
				&& after.getValue(BlockStateProperties.OPEN) != before.getValue(BlockStateProperties.OPEN)) {
			BlockPos at = pos;
			if (after.getBlock() instanceof DoorBlock && after.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
				BlockState lower = level.getBlockState(pos.below());
				if (lower.is(after.getBlock())) {
					at = pos.below();
					after = lower;
				}
			}
			addAction(player, new PlayerAction.DoorUse(level.dimension(), at, after, now(player)));
			return;
		}
		AbstractContainerMenu menu = player.containerMenu;
		if (menu != menuBefore && menu != player.inventoryMenu && isContainer(level, pos, after)) {
			addAction(player, new PlayerAction.ContainerOpen(level.dimension(), pos, after, now(player)));
		}
	}

	/** A door, trapdoor or fence gate. */
	static boolean isDoorLike(BlockState state) {
		Block block = state.getBlock();
		return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock;
	}

	/** A block whose block entity is a container, or an ender chest. */
	static boolean isContainer(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getBlock() instanceof EnderChestBlock || level.getBlockEntity(pos) instanceof Container;
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
