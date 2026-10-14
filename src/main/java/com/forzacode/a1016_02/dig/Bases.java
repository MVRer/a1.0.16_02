package com.forzacode.a1016_02.dig;

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
 * Who takes part and where each known player lives, for dig (D-061): a participant's live base
 * ({@link PlayerWatch#base}), else the last base dig saw for them (kept in {@link DigData} by UUID), else their own
 * first block. "Base lights never count" and "never near the base" mean any of these. Players are looked up by UUID
 * each time, never kept (D-062.1). Live, or a test's own watch, state and data. Server thread only.
 */
final class Bases {
	private final MinecraftServer server;
	private final PlayerWatch watch;
	private final HerobrineState state;
	private final DigData data;

	Bases(MinecraftServer server, PlayerWatch watch, HerobrineState state, DigData data) {
		this.server = server;
		this.watch = watch;
		this.state = state;
		this.data = data;
	}

	static Bases live(MinecraftServer server) {
		return new Bases(server, Services.watch(), HerobrineState.get(server), DigData.get(server));
	}

	PlayerWatch watch() {
		return watch;
	}

	HerobrineState state() {
		return state;
	}

	DigData data() {
		return data;
	}

	List<ServerPlayer> participants() {
		return watch.participants(server);
	}

	/** Every participant in this level now. */
	List<ServerPlayer> participantsIn(ServerLevel level) {
		List<ServerPlayer> here = new ArrayList<>();
		for (ServerPlayer player : participants()) {
			if (player.level() == level) {
				here.add(player);
			}
		}
		return here;
	}

	/** Where every participant in this level stands now. */
	List<BlockPos> positionsIn(ServerLevel level) {
		List<BlockPos> positions = new ArrayList<>();
		participantsIn(level).forEach(p -> positions.add(p.blockPosition()));
		return positions;
	}

	/** Remembers this participant's live base. */
	void remember(ServerPlayer player) {
		watch.base(player).ifPresent(base -> data.setBase(player.getUUID(), base));
	}

	/** That player's base: live while they take part, else the last one dig saw, else their own first block. */
	Optional<GlobalPos> base(UUID player) {
		Optional<GlobalPos> live = watch.participant(server, player).flatMap(watch::base);
		return live.or(() -> data.base(player)).or(() -> Optional.ofNullable(state.firstBlocks(player).block()).map(PlacedBlock::pos));
	}

	/** Every base dig knows in this dimension: each known player's, then any participant's not known yet; no repeats. */
	List<BlockPos> all(ResourceKey<Level> dimension) {
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

	/** True if any of {@code positions} is within {@code radius} blocks of {@code pos}, horizontally. */
	static boolean near(List<BlockPos> positions, BlockPos pos, double radius) {
		for (BlockPos other : positions) {
			if (DigTicker.horizontalDistSqr(pos, other) < radius * radius) {
				return true;
			}
		}
		return false;
	}
}
