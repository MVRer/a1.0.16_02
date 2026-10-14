package com.forzacode.a1016_02.lore;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Whose records the one story uses, and where each known player lives (D-061). The story's first blocks (F13's
 * cairn, F10's "where your first crafting table stood") and its home (every fragment measured from "your base")
 * belong to one player, chosen once by core's fixed rule and stored under {@link #FIRST_BLOCKS}; the ending reads
 * the same key for the cairn's first block. A player's base is their live one while they take part
 * ({@link PlayerWatch#base}: respawn point, else their own first block), the last one lore saw while they are away
 * (kept in {@link LoreData} by UUID), else their own first block. Players are looked up by UUID each time, never
 * kept (D-062.1). Live, or a test's own state, watch and data.
 */
final class Homes {
	/** D-061, fixed for every workstream: whose first crafting table, first chest or first block the story uses. */
	static final String FIRST_BLOCKS = "story:first_blocks";

	private final MinecraftServer server;
	private final HerobrineState state;
	private final PlayerWatch watch;
	private final LoreData data;

	Homes(MinecraftServer server, HerobrineState state, PlayerWatch watch, LoreData data) {
		this.server = server;
		this.state = state;
		this.watch = watch;
		this.data = data;
	}

	/** The live world's. */
	static Homes live(MinecraftServer server) {
		return new Homes(server, HerobrineState.get(server), Services.watch(), LoreData.get(server));
	}

	MinecraftServer server() {
		return server;
	}

	HerobrineState state() {
		return state;
	}

	PlayerWatch watch() {
		return watch;
	}

	LoreData data() {
		return data;
	}

	/**
	 * The story's owner: the stored choice, or one chosen now among the participants who have placed a block (so F13
	 * never waits on someone with no first block) and stored for good (core's rule: most play ticks, ties to the
	 * earliest join). Empty while none is stored and no participant has placed a block yet.
	 */
	Optional<UUID> owner() {
		return watch.ownerFor(server, FIRST_BLOCKS, LoreApi.hasPlacedBlock(state));
	}

	/** The owner's oldest placed blocks, oldest first, as lore logged them (D-066 M11); empty with no owner. */
	List<PlacedBlock> ownerPlacements() {
		return owner().map(data::placements).orElse(List.of());
	}

	/** The stored owner, without choosing one. */
	Optional<UUID> storedOwner() {
		return watch.owner(server, FIRST_BLOCKS);
	}

	/** The owner's first blocks; {@link HerobrineState.FirstBlocks#NONE} while there is no owner. */
	HerobrineState.FirstBlocks ownerFirstBlocks() {
		return owner().map(state::firstBlocks).orElse(HerobrineState.FirstBlocks.NONE);
	}

	/** The owner's base in this dimension (else their first block there); empty if neither is there or there is no owner. */
	Optional<BlockPos> ownerBase(ResourceKey<Level> dimension) {
		return owner().flatMap(owner -> base(owner).filter(p -> p.dimension().equals(dimension))
				.or(() -> firstBlock(owner).filter(p -> p.dimension().equals(dimension)))).map(GlobalPos::pos);
	}

	/** Remembers the live base of every participant. Cheap; on every placement check and every start or stop. */
	void rememberAll() {
		for (ServerPlayer player : watch.participants(server)) {
			remember(player);
		}
	}

	/** Remembers this player's live base (a fresh object from the server, or a participant lookup). */
	void remember(ServerPlayer player) {
		watch.base(player).ifPresent(base -> data.setBase(player.getUUID(), base));
	}

	/**
	 * That player's base: their live one while they take part, else the last one lore saw, else their own first
	 * block. Empty for someone with none of these (never a participant, or never placed a block).
	 */
	Optional<GlobalPos> base(UUID player) {
		Optional<GlobalPos> live = watch.participant(server, player).flatMap(watch::base);
		return live.or(() -> data.base(player)).or(() -> firstBlock(player));
	}

	/** Every known player's base that lore knows, in first-join order (offline ones included). */
	List<GlobalPos> all() {
		List<GlobalPos> bases = new ArrayList<>();
		for (HerobrineState.Subject player : state.players()) {
			base(player.uuid()).filter(base -> !bases.contains(base)).ifPresent(bases::add);
		}
		return bases;
	}

	/** A known player's name (their latest), if they ever took part: nothing names someone who never joined (D-061). */
	Optional<String> name(UUID player) {
		return state.player(player).map(HerobrineState.Subject::name);
	}

	private Optional<GlobalPos> firstBlock(UUID player) {
		return Optional.ofNullable(state.firstBlocks(player).block()).map(PlacedBlock::pos);
	}
}
