package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/**
 * What the subject player is doing and has done. Live values (stillness, combat, join time) are per session;
 * the footprint (blocks placed and dug by players) and per-chunk visits persist. Server thread only.
 */
public final class PlayerWatch {
	private static final class Live {
		Vec3 lastPos = Vec3.ZERO;
		long stillTicks;
		long joinTick;
		long lastCombatTick = Long.MIN_VALUE;
	}

	private final Map<UUID, Live> live = new HashMap<>();

	PlayerWatch() {
	}

	/** The subject (the first player who joined) if online. */
	public Optional<ServerPlayer> subject(MinecraftServer server) {
		return HerobrineState.get(server).subject().map(s -> server.getPlayerList().getPlayer(s.uuid()));
	}

	public boolean isSubject(ServerPlayer player) {
		return HerobrineState.get(player.level().getServer()).subject().map(s -> s.uuid().equals(player.getUUID())).orElse(false);
	}

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

	/** The player's base: their respawn point, otherwise (for the subject) the first block they placed. */
	public Optional<GlobalPos> base(ServerPlayer player) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn != null) {
			return Optional.of(respawn.respawnData().globalPos());
		}
		if (isSubject(player)) {
			PlacedBlock first = HerobrineState.get(player.level().getServer()).firstBlocks().block();
			if (first != null) {
				return Optional.of(first.pos());
			}
		}
		return Optional.empty();
	}

	/** The in-game day ({@link GameClock#day}) a player was last in or next to this chunk, or -1 if never. */
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

	void onJoin(ServerPlayer player) {
		Live data = live.computeIfAbsent(player.getUUID(), k -> new Live());
		data.joinTick = now(player);
		data.lastPos = player.position();
		data.stillTicks = 0;
		HerobrineState state = HerobrineState.get(player.level().getServer());
		if (state.subject().isEmpty() || state.subject().get().uuid().equals(player.getUUID())) {
			state.setSubject(player.getUUID(), player.getName().getString());
		}
	}

	void onLeave(ServerPlayer player) {
		live.remove(player.getUUID());
	}

	void clear() {
		live.clear();
	}

	void onCombat(ServerPlayer player) {
		live.computeIfAbsent(player.getUUID(), k -> new Live()).lastCombatTick = now(player);
	}

	void tick(MinecraftServer server) {
		boolean sample = server.getTickCount() % ModConfig.pacing().watchSampleTicks() == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Live data = live.computeIfAbsent(player.getUUID(), k -> new Live());
			Vec3 pos = player.position();
			if (pos.distanceToSqr(data.lastPos) > 1.0E-4) {
				data.stillTicks = 0;
				data.lastPos = pos;
			} else {
				data.stillTicks++;
			}
			if (sample) {
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

	/** Internal: called by core's block placement mixin. */
	public void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		WatchData data = WatchData.get(level.getServer());
		WatchData.Footprint footprint = data.footprint(level.dimension());
		footprint.dug.remove(pos.asLong());
		footprint.placed.add(pos.asLong(), ModConfig.pacing().footprintMaxPerDimension);
		data.setDirty();

		if (isSubject(player)) {
			HerobrineState herobrine = HerobrineState.get(level.getServer());
			PlacedBlock placed = new PlacedBlock(GlobalPos.of(level.dimension(), pos.immutable()), state);
			herobrine.recordFirst(HerobrineState.FirstKind.BLOCK, placed);
			if (state.is(Blocks.CRAFTING_TABLE)) {
				herobrine.recordFirst(HerobrineState.FirstKind.CRAFTING_TABLE, placed);
			}
			if (state.getBlock() instanceof ChestBlock) {
				herobrine.recordFirst(HerobrineState.FirstKind.CHEST, placed);
			}
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

	private WatchData.Footprint footprint(ServerLevel level) {
		return WatchData.get(level.getServer()).footprint(level.dimension());
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().getTickCount();
	}
}
