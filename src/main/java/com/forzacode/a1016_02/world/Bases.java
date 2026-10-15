package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Where each known player lives, for the world workstream (D-061): a participant's live base
 * ({@link PlayerWatch#base}: respawn point, else their own first block), else the last base world saw while they
 * took part (kept in {@link WorldData} by UUID), else their own first block. "Never near the base" means never near
 * any of these. The story's home is the base of the story's first-blocks owner ({@value #STORY_OWNER}, the home lore
 * measures "your base" from). Players are looked up by UUID each time, never kept (D-062.1). Live, or a test's own
 * state, watch and data. Server thread only.
 */
public final class Bases {
	/** D-061, shared with lore and the ending: whose first blocks and base the one story uses. */
	public static final String STORY_OWNER = "story:first_blocks";

	private final MinecraftServer server;
	private final HerobrineState state;
	private final PlayerWatch watch;
	private final WorldData data;

	public Bases(MinecraftServer server, HerobrineState state, PlayerWatch watch, WorldData data) {
		this.server = server;
		this.state = state;
		this.watch = watch;
		this.data = data;
	}

	/** The live world's. */
	public static Bases live(MinecraftServer server) {
		return new Bases(server, HerobrineState.get(server), Services.watch(), WorldData.get(server));
	}

	public MinecraftServer server() {
		return server;
	}

	public HerobrineState state() {
		return state;
	}

	public PlayerWatch watch() {
		return watch;
	}

	public WorldData data() {
		return data;
	}

	/** Every participant now, in first-join order (a fresh list). */
	public List<ServerPlayer> participants() {
		return watch.participants(server);
	}

	/** Every participant in this level now. */
	public List<ServerPlayer> participantsIn(ServerLevel level) {
		List<ServerPlayer> here = new ArrayList<>();
		for (ServerPlayer player : participants()) {
			if (player.level() == level) {
				here.add(player);
			}
		}
		return here;
	}

	/** Remembers every participant's live base (cheap; on every watch pass and every start or stop). */
	public void rememberAll() {
		for (ServerPlayer player : participants()) {
			remember(player);
		}
	}

	/** Remembers this player's live base (a fresh object from the server or a participant lookup). */
	public void remember(ServerPlayer player) {
		watch.base(player).ifPresent(base -> data.setBase(player.getUUID(), base));
	}

	/** That player's base: their live one while they take part, else the last one world saw, else their own first block. */
	public Optional<GlobalPos> base(UUID player) {
		Optional<GlobalPos> live = watch.participant(server, player).flatMap(watch::base);
		return live.or(() -> data.base(player)).or(() -> Optional.ofNullable(state.firstBlocks(player).block()).map(PlacedBlock::pos));
	}

	/**
	 * Every base world knows in this dimension: each known player's ({@link #base}), then any participant's not known
	 * yet, in first-join order and without repeats. Offline players' bases count too.
	 */
	public List<BlockPos> all(ResourceKey<Level> dimension) {
		List<UUID> players = new ArrayList<>();
		state.players().forEach(p -> players.add(p.uuid()));
		for (ServerPlayer player : participants()) {
			if (!players.contains(player.getUUID())) {
				players.add(player.getUUID());
			}
		}
		List<BlockPos> bases = new ArrayList<>();
		for (UUID player : players) {
			base(player).filter(b -> b.dimension().equals(dimension)).map(GlobalPos::pos).filter(b -> !bases.contains(b)).ifPresent(bases::add);
		}
		return bases;
	}

	/** True if any base in this dimension ({@link #all}) is within {@code radius} blocks of {@code pos}, horizontally. */
	public boolean nearAny(ResourceKey<Level> dimension, BlockPos pos, double radius) {
		return near(all(dimension), pos, radius);
	}

	/** True if any of {@code bases} is within {@code radius} blocks of {@code pos}, horizontally. */
	public static boolean near(List<BlockPos> bases, BlockPos pos, double radius) {
		for (BlockPos base : bases) {
			double dx = pos.getX() - base.getX();
			double dz = pos.getZ() - base.getZ();
			if (dx * dx + dz * dz < radius * radius) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The story's home in this dimension: the story's first-blocks owner's base (if nobody was chosen yet, chosen now
	 * by core's fixed rule among the participants who have placed a block, {@link HerobrineState#hasPlacedBlock}, the
	 * one rule lore and the ending choose it by too, and stored), else the first known player's; empty if neither has a
	 * base there.
	 */
	public Optional<BlockPos> storyHome(ResourceKey<Level> dimension) {
		Optional<UUID> owner = watch.ownerFor(server, STORY_OWNER, state::hasPlacedBlock)
				.or(() -> state.players().stream().findFirst().map(HerobrineState.Subject::uuid));
		return owner.flatMap(this::base).filter(b -> b.dimension().equals(dimension)).map(GlobalPos::pos);
	}

	/** Where every participant in this level stands now (spectators are not participants). */
	public List<BlockPos> positionsIn(ServerLevel level) {
		List<BlockPos> positions = new ArrayList<>();
		for (ServerPlayer player : participantsIn(level)) {
			positions.add(player.blockPosition());
		}
		return positions;
	}

	/** True if every one of {@code positions} is at least {@code distance} blocks from {@code pos}, horizontally. */
	public static boolean farFromAll(List<BlockPos> positions, BlockPos pos, double distance) {
		return !near(positions, pos, distance);
	}
}
