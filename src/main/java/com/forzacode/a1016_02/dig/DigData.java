package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * The dig workstream's world state ({@code data/a1016_02/dig.dat}): the "Under you" networks (one per distinct base,
 * each with the players whose base it is under), the base each player was last seen with and since which night,
 * the last base dig saw for each player, the tunnel that grows, the card tunnels, the caves the players explored,
 * the saplings they planted, once-only flags and each player's tunnel ladder ({@link LadderMemory}, D-072).
 * Everything per player is keyed by UUID (D-062.2).
 * <p>
 * Old saves (D-062.8): a {@code dig.dat} from before multiplayer has one top-level {@code baseSeen} and networks
 * without owners. They load as is and become the first known player's on the first tick that knows one
 * ({@link #adoptLegacy}).
 */
public final class DigData extends SavedData {
	/** A card tunnel (for the ENTERED_TUNNEL trigger and the debug info). */
	public record CardTunnel(ResourceKey<Level> dimension, List<BlockPos> anchors, String kind, int siteId) {
	}

	/**
	 * The base dig last saw a player with: where, the night it was first seen there, and whether the player has
	 * been at home there since (a network never starts under a base its player never played at).
	 */
	public record BaseSeen(GlobalPos base, long night, boolean playedThere) {
	}

	public static final Codec<DigData> CODEC = CompoundTag.CODEC.xmap(DigData::load, DigData::save);
	public static final SavedDataType<DigData> TYPE = new SavedDataType<>(A1016_02.id("dig"), DigData::new, CODEC, null);

	final Map<ResourceKey<Level>, PosSet> explored = new HashMap<>();
	final Map<ResourceKey<Level>, PosSet> planted = new HashMap<>();
	/** Every network, oldest first. */
	final List<Network> networks = new ArrayList<>();
	@Nullable GrowingTunnel growing;
	final List<CardTunnel> tunnels = new ArrayList<>();
	/** Once-only keys, such as {@code mining_that_moves@PROXIMITY}. */
	final Set<String> once = new LinkedHashSet<>();
	/** Each player's base as dig last saw it while they took part, and the night it was first seen. */
	final Map<UUID, BaseSeen> baseSeen = new LinkedHashMap<>();
	/** The last base dig saw for each player (their live base while they take part; kept for when they are away). */
	final Map<UUID, GlobalPos> bases = new LinkedHashMap<>();
	/** Each player's tunnel ladder (D-072). A save from before v1.2 has none and loads with every player at rung 0. */
	final Map<UUID, LadderMemory> ladder = new LinkedHashMap<>();
	/** A save from before multiplayer: the one base the network waited for, until a first known player adopts it. */
	@Nullable GlobalPos legacyBaseSeen;
	long legacyBaseSeenNight = Long.MIN_VALUE;

	private final Map<ResourceKey<Level>, LongOpenHashSet> tunnelCells = new HashMap<>();
	private boolean cellsStale = true;

	public DigData() {
	}

	public static DigData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public PosSet explored(ResourceKey<Level> dimension) {
		return explored.computeIfAbsent(dimension, k -> new PosSet());
	}

	public PosSet planted(ResourceKey<Level> dimension) {
		return planted.computeIfAbsent(dimension, k -> new PosSet());
	}

	/** The newest network, if any (debug and old callers; each base has its own, see {@link #networkFor}). */
	public Optional<Network> network() {
		return networks.isEmpty() ? Optional.empty() : Optional.of(networks.getLast());
	}

	public List<Network> networks() {
		return List.copyOf(networks);
	}

	/** The network whose base is nearest {@code pos} in this dimension, within {@code distance} blocks; or null. */
	public @Nullable Network networkFor(ResourceKey<Level> dimension, BlockPos pos, int distance) {
		Network best = null;
		double bestDist = (double) distance * distance;
		for (Network network : networks) {
			double d = network.base.distSqr(pos);
			if (network.dimension.equals(dimension) && d <= bestDist) {
				best = network;
				bestDist = d;
			}
		}
		return best;
	}

	/** The base dig last saw this player with, if any. */
	public Optional<BaseSeen> baseSeen(UUID player) {
		return Optional.ofNullable(baseSeen.get(player));
	}

	/** The last base dig saw for this player while they took part. */
	public Optional<GlobalPos> base(UUID player) {
		return Optional.ofNullable(bases.get(player));
	}

	void setBase(UUID player, GlobalPos base) {
		if (!base.equals(bases.put(player, base))) {
			setDirty();
		}
	}

	/** The highest tunnel ladder rung this player heard (0 to 3; 0 if never), for the debug overlay (D-072). */
	public int ladderRung(UUID player) {
		LadderMemory memory = ladder.get(player);
		return memory == null ? 0 : memory.rung;
	}

	/** This player's tunnel ladder, if they ever had one. */
	Optional<LadderMemory> ladderOf(UUID player) {
		return Optional.ofNullable(ladder.get(player));
	}

	/** This player's tunnel ladder, made empty (rung 0, never) if they had none; mark dirty after changing it. */
	LadderMemory ladder(UUID player) {
		return ladder.computeIfAbsent(player, k -> new LadderMemory());
	}

	public Optional<GrowingTunnel> growing() {
		return Optional.ofNullable(growing);
	}

	public List<CardTunnel> tunnels() {
		return List.copyOf(tunnels);
	}

	void addNetwork(Network network) {
		networks.add(network);
		changed();
	}

	void setGrowing(@Nullable GrowingTunnel tunnel) {
		growing = tunnel;
		changed();
	}

	void addTunnel(CardTunnel tunnel) {
		tunnels.add(tunnel);
		changed();
	}

	boolean once(String key) {
		boolean added = once.add(key);
		if (added) {
			setDirty();
		}
		return added;
	}

	boolean hasOnce(String key) {
		return once.contains(key);
	}

	/** True while this holds what a save from before multiplayer kept for its one subject. */
	boolean hasLegacy() {
		return legacyBaseSeen != null || networks.stream().anyMatch(n -> n.owners.isEmpty());
	}

	/**
	 * Old saves (D-062.8): what dig kept for the one subject becomes {@code first}'s, the first known player: the
	 * base it waited for (as a base they played at) and every network without owners. Does nothing without a first
	 * known player or anything to adopt. True if something changed.
	 */
	boolean adoptLegacy(Optional<UUID> first) {
		if (first.isEmpty() || !hasLegacy()) {
			return false;
		}
		UUID player = first.get();
		if (legacyBaseSeen != null) {
			baseSeen.putIfAbsent(player, new BaseSeen(legacyBaseSeen, legacyBaseSeenNight, true));
			legacyBaseSeen = null;
			legacyBaseSeenNight = Long.MIN_VALUE;
		}
		for (Network network : networks) {
			if (network.owners.isEmpty()) {
				network.owners.add(player);
			}
		}
		setDirty();
		return true;
	}

	/** Call after carving: marks the data dirty and the tunnel index stale. */
	void changed() {
		cellsStale = true;
		setDirty();
	}

	/** Every cell of his tunnels in this dimension (networks, the tunnel that grows, card tunnels). */
	LongOpenHashSet tunnelCells(ResourceKey<Level> dimension) {
		if (cellsStale) {
			tunnelCells.clear();
			for (Network network : networks) {
				tunnelCells.computeIfAbsent(network.dimension, k -> new LongOpenHashSet()).addAll(network.cells);
			}
			if (growing != null) {
				tunnelCells.computeIfAbsent(growing.dimension, k -> new LongOpenHashSet()).addAll(Tunnels.cells(growing.anchors));
			}
			for (CardTunnel tunnel : tunnels) {
				tunnelCells.computeIfAbsent(tunnel.dimension(), k -> new LongOpenHashSet()).addAll(Tunnels.cells(tunnel.anchors()));
			}
			cellsStale = false;
		}
		return tunnelCells.getOrDefault(dimension, new LongOpenHashSet());
	}

	// --- NBT ---

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put("explored", saveSets(explored));
		tag.put("planted", saveSets(planted));
		ListTag networkList = new ListTag();
		networks.forEach(network -> networkList.add(network.save()));
		tag.put("networks", networkList);
		if (growing != null) {
			tag.put("growing", growing.save());
		}
		ListTag tunnelList = new ListTag();
		for (CardTunnel tunnel : tunnels) {
			CompoundTag t = new CompoundTag();
			t.putString("dimension", tunnel.dimension().identifier().toString());
			t.putLongArray("anchors", tunnel.anchors().stream().mapToLong(BlockPos::asLong).toArray());
			t.putString("kind", tunnel.kind());
			t.putInt("site", tunnel.siteId());
			tunnelList.add(t);
		}
		tag.put("tunnels", tunnelList);
		ListTag onceList = new ListTag();
		once.forEach(key -> onceList.add(net.minecraft.nbt.StringTag.valueOf(key)));
		tag.put("once", onceList);
		ListTag seenList = new ListTag();
		baseSeen.forEach((player, seen) -> {
			CompoundTag s = putGlobal(new CompoundTag(), seen.base());
			s.putString("player", player.toString());
			s.putLong("night", seen.night());
			s.putBoolean("playedThere", seen.playedThere());
			seenList.add(s);
		});
		tag.put("bases", seenList);
		ListTag lastList = new ListTag();
		bases.forEach((player, base) -> {
			CompoundTag b = putGlobal(new CompoundTag(), base);
			b.putString("player", player.toString());
			lastList.add(b);
		});
		tag.put("lastBases", lastList);
		ListTag ladderList = new ListTag();
		ladder.forEach((player, memory) -> {
			CompoundTag l = memory.save();
			l.putString("player", player.toString());
			ladderList.add(l);
		});
		tag.put("ladder", ladderList);
		if (legacyBaseSeen != null) {
			tag.putString("baseSeenDimension", legacyBaseSeen.dimension().identifier().toString());
			tag.putLong("baseSeen", legacyBaseSeen.pos().asLong());
			tag.putLong("baseSeenNight", legacyBaseSeenNight);
		}
		return tag;
	}

	private static DigData load(CompoundTag tag) {
		DigData data = new DigData();
		loadSets(tag.getCompoundOrEmpty("explored"), data.explored);
		loadSets(tag.getCompoundOrEmpty("planted"), data.planted);
		ListTag networkList = tag.getListOrEmpty("networks");
		for (int i = 0; i < networkList.size(); i++) {
			data.networks.add(Network.load(networkList.getCompoundOrEmpty(i)));
		}
		data.growing = tag.getCompound("growing").map(GrowingTunnel::load).orElse(null);
		ListTag tunnelList = tag.getListOrEmpty("tunnels");
		for (int i = 0; i < tunnelList.size(); i++) {
			CompoundTag t = tunnelList.getCompoundOrEmpty(i);
			List<BlockPos> anchors = new ArrayList<>();
			for (long packed : t.getLongArray("anchors").orElse(new long[0])) {
				anchors.add(BlockPos.of(packed));
			}
			data.tunnels.add(new CardTunnel(dimension(t.getStringOr("dimension", "minecraft:overworld")), anchors, t.getStringOr("kind", "tunnel"),
					t.getIntOr("site", -1)));
		}
		ListTag onceList = tag.getListOrEmpty("once");
		for (int i = 0; i < onceList.size(); i++) {
			onceList.getString(i).ifPresent(data.once::add);
		}
		ListTag seenList = tag.getListOrEmpty("bases");
		for (int i = 0; i < seenList.size(); i++) {
			CompoundTag s = seenList.getCompoundOrEmpty(i);
			Optional<UUID> player = Network.uuid(s.getStringOr("player", ""));
			player.ifPresent(uuid -> data.baseSeen.put(uuid, new BaseSeen(getGlobal(s), s.getLongOr("night", Long.MIN_VALUE),
					s.getBooleanOr("playedThere", false))));
		}
		ListTag lastList = tag.getListOrEmpty("lastBases");
		for (int i = 0; i < lastList.size(); i++) {
			CompoundTag b = lastList.getCompoundOrEmpty(i);
			Network.uuid(b.getStringOr("player", "")).ifPresent(uuid -> data.bases.put(uuid, getGlobal(b)));
		}
		ListTag ladderList = tag.getListOrEmpty("ladder");
		for (int i = 0; i < ladderList.size(); i++) {
			CompoundTag l = ladderList.getCompoundOrEmpty(i);
			Network.uuid(l.getStringOr("player", "")).ifPresent(uuid -> data.ladder.put(uuid, LadderMemory.load(l)));
		}
		// A save from before multiplayer: its one base waits for the first known player (adoptLegacy).
		if (tag.contains("baseSeen")) {
			data.legacyBaseSeen = GlobalPos.of(dimension(tag.getStringOr("baseSeenDimension", "minecraft:overworld")),
					BlockPos.of(tag.getLongOr("baseSeen", 0L)));
			data.legacyBaseSeenNight = tag.getLongOr("baseSeenNight", Long.MIN_VALUE);
		}
		return data;
	}

	private static CompoundTag putGlobal(CompoundTag tag, GlobalPos pos) {
		tag.putString("dimension", pos.dimension().identifier().toString());
		tag.putLong("pos", pos.pos().asLong());
		return tag;
	}

	private static GlobalPos getGlobal(CompoundTag tag) {
		return GlobalPos.of(dimension(tag.getStringOr("dimension", "minecraft:overworld")), BlockPos.of(tag.getLongOr("pos", 0L)));
	}

	private static CompoundTag saveSets(Map<ResourceKey<Level>, PosSet> sets) {
		CompoundTag tag = new CompoundTag();
		sets.forEach((dimension, set) -> tag.putLongArray(dimension.identifier().toString(), set.toArray()));
		return tag;
	}

	private static void loadSets(CompoundTag tag, Map<ResourceKey<Level>, PosSet> sets) {
		for (String key : tag.keySet()) {
			PosSet set = new PosSet();
			for (long packed : tag.getLongArray(key).orElse(new long[0])) {
				set.add(packed, Integer.MAX_VALUE);
			}
			sets.put(dimension(key), set);
		}
	}

	static ResourceKey<Level> dimension(String id) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
	}
}
