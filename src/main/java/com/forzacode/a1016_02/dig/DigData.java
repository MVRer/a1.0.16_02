package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * The dig workstream's world state ({@code data/a1016_02/dig.dat}): the "Under you" networks, the tunnel that
 * grows, the card tunnels, the caves the player explored, the saplings they planted and once-only flags.
 */
public final class DigData extends SavedData {
	/** A card tunnel (for the ENTERED_TUNNEL trigger and the debug info). */
	public record CardTunnel(ResourceKey<Level> dimension, List<BlockPos> anchors, String kind, int siteId) {
	}

	public static final Codec<DigData> CODEC = CompoundTag.CODEC.xmap(DigData::load, DigData::save);
	public static final SavedDataType<DigData> TYPE = new SavedDataType<>(A1016_02.id("dig"), DigData::new, CODEC, null);

	final Map<ResourceKey<Level>, PosSet> explored = new HashMap<>();
	final Map<ResourceKey<Level>, PosSet> planted = new HashMap<>();
	/** Every network; the last one is the one that grows. */
	final List<Network> networks = new ArrayList<>();
	@Nullable GrowingTunnel growing;
	final List<CardTunnel> tunnels = new ArrayList<>();
	/** Once-only keys, such as {@code mining_that_moves@PROXIMITY}. */
	final Set<String> once = new LinkedHashSet<>();
	/** The base the network waits for, and the night it was first seen. */
	@Nullable GlobalPos baseSeen;
	long baseSeenNight = Long.MIN_VALUE;

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

	/** The network that grows now, if any. */
	public Optional<Network> network() {
		return networks.isEmpty() ? Optional.empty() : Optional.of(networks.getLast());
	}

	public List<Network> networks() {
		return List.copyOf(networks);
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
		if (baseSeen != null) {
			tag.putString("baseSeenDimension", baseSeen.dimension().identifier().toString());
			tag.putLong("baseSeen", baseSeen.pos().asLong());
			tag.putLong("baseSeenNight", baseSeenNight);
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
		if (tag.contains("baseSeen")) {
			data.baseSeen = GlobalPos.of(dimension(tag.getStringOr("baseSeenDimension", "minecraft:overworld")), BlockPos.of(tag.getLongOr("baseSeen", 0L)));
			data.baseSeenNight = tag.getLongOr("baseSeenNight", Long.MIN_VALUE);
		}
		return data;
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
