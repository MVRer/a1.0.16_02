package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * Lore's private, per-world facts ({@code data/a1016_02/lore.dat}). Shared facts (placed, read) live in
 * {@code HerobrineState}.
 * <ul>
 * <li>anchors: named places lore built or chose ({@code "grove"} the untouched grove, {@code "F15/room"} the test
 * room's lower corner, {@code "F30/grove"} and {@code "F30/bedrock"} the twin signs)</li>
 * <li>read targets: blocks that count as reading a fragment when looked at up close (signs, the cairn's core)</li>
 * <li>site claims: which {@code SiteRegistry} site each fragment used</li>
 * <li>eligible since: play ticks when each fragment could first be placed (own builds wait a while after it)</li>
 * <li>own sites: registry sites lore built itself, which are never his traces (D-041)</li>
 * <li>bases: the last base lore saw for each player, by UUID, for when they are away ({@link Homes}, D-061)</li>
 * <li>placements: each player's oldest placed blocks, by UUID, oldest first (at most {@link LoreConfig#placementLogMax}):
 * F13's cairn falls back on the story owner's oldest one still standing at their base when the first table, chest
 * and block are all gone (D-066, M11)</li>
 * <li>cairn fallback: the block that fallback chose, stored so it never flips</li>
 * </ul>
 * No player is stored here as "the" player: a v1.0 {@code lore.dat} had none, so it loads as it was.
 */
public final class LoreData extends SavedData {
	public static final String GROVE = "grove";

	public static final Codec<LoreData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC).optionalFieldOf("anchors", Map.of()).forGetter(d -> d.anchors),
			Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC.listOf()).optionalFieldOf("readTargets", Map.of()).forGetter(d -> d.readTargets),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("siteClaims", Map.of()).forGetter(d -> d.siteClaims),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("eligibleSince", Map.of()).forGetter(d -> d.eligibleSince),
			Codec.INT.listOf().optionalFieldOf("ownSites", List.of()).forGetter(d -> List.copyOf(d.ownSites)),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, GlobalPos.CODEC).optionalFieldOf("bases", Map.of()).forGetter(d -> d.bases),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlacedBlock.CODEC.listOf()).optionalFieldOf("placements", Map.of()).forGetter(d -> d.placements),
			PlacedBlock.CODEC.optionalFieldOf("cairnFallback").forGetter(d -> Optional.ofNullable(d.cairnFallback))
	).apply(i, LoreData::new));

	public static final SavedDataType<LoreData> TYPE = new SavedDataType<>(A1016_02.id("lore"), LoreData::new, CODEC, null);

	private final Map<String, GlobalPos> anchors = new TreeMap<>();
	private final Map<String, List<GlobalPos>> readTargets = new TreeMap<>();
	private final Map<String, Integer> siteClaims = new TreeMap<>();
	private final Map<String, Long> eligibleSince = new TreeMap<>();
	/** Sites lore built itself (left by people, D-041): never "his traces". */
	private final Set<Integer> ownSites = new TreeSet<>();
	/** The last base lore saw for each player (D-062.2: by UUID). */
	private final Map<UUID, GlobalPos> bases = new TreeMap<>();
	/** Each player's oldest placed blocks, oldest first (D-062.2: by UUID). */
	private final Map<UUID, List<PlacedBlock>> placements = new TreeMap<>();
	private @Nullable PlacedBlock cairnFallback;

	public LoreData() {
	}

	private LoreData(Map<String, GlobalPos> anchors, Map<String, List<GlobalPos>> readTargets, Map<String, Integer> siteClaims,
			Map<String, Long> eligibleSince, List<Integer> ownSites, Map<UUID, GlobalPos> bases, Map<UUID, List<PlacedBlock>> placements,
			Optional<PlacedBlock> cairnFallback) {
		this.anchors.putAll(anchors);
		readTargets.forEach((id, list) -> this.readTargets.put(id, new ArrayList<>(list)));
		this.siteClaims.putAll(siteClaims);
		this.eligibleSince.putAll(eligibleSince);
		this.ownSites.addAll(ownSites);
		this.bases.putAll(bases);
		placements.forEach((player, list) -> this.placements.put(player, new ArrayList<>(list)));
		this.cairnFallback = cairnFallback.orElse(null);
	}

	public static LoreData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Optional<GlobalPos> anchor(String key) {
		return Optional.ofNullable(anchors.get(key));
	}

	public void setAnchor(String key, GlobalPos pos) {
		anchors.put(key, pos);
		setDirty();
	}

	public void removeAnchor(String key) {
		if (anchors.remove(key) != null) {
			setDirty();
		}
	}

	public Map<String, List<GlobalPos>> readTargets() {
		return Collections.unmodifiableMap(readTargets);
	}

	public void addReadTarget(String id, GlobalPos pos) {
		List<GlobalPos> list = readTargets.computeIfAbsent(id, k -> new ArrayList<>());
		if (!list.contains(pos)) {
			list.add(pos);
			setDirty();
		}
	}

	public void clearReadTargets(String id) {
		if (readTargets.remove(id) != null) {
			setDirty();
		}
	}

	public Optional<Integer> siteClaim(String id) {
		return Optional.ofNullable(siteClaims.get(id));
	}

	/** Play ticks when the fragment could first be placed; records {@code now} the first time it is asked. */
	public long eligibleSince(String id, long now) {
		Long since = eligibleSince.get(id);
		if (since == null) {
			eligibleSince.put(id, now);
			setDirty();
			return now;
		}
		return since;
	}

	/** True if lore built this site itself (a hut, a house, a tower: left by people). */
	public boolean isOwnSite(int siteId) {
		return ownSites.contains(siteId);
	}

	public void addOwnSite(int siteId) {
		if (ownSites.add(siteId)) {
			setDirty();
		}
	}

	public void setSiteClaim(String id, int siteId) {
		siteClaims.put(id, siteId);
		setDirty();
	}

	/** The last base lore saw for this player, while they are away. */
	public Optional<GlobalPos> base(UUID player) {
		return Optional.ofNullable(bases.get(player));
	}

	public void setBase(UUID player, GlobalPos base) {
		if (!base.equals(bases.put(player, base))) {
			setDirty();
		}
	}

	/** This player's oldest placed blocks, oldest first (some may be gone since). */
	public List<PlacedBlock> placements(UUID player) {
		return Collections.unmodifiableList(placements.getOrDefault(player, List.of()));
	}

	/**
	 * Logs a block this player placed, while their log has room ({@code max}): only the oldest are kept. A block placed
	 * again where one of theirs is logged keeps the older entry. True if it was logged.
	 */
	public boolean logPlacement(UUID player, PlacedBlock placed, int max) {
		List<PlacedBlock> list = placements.computeIfAbsent(player, k -> new ArrayList<>());
		if (list.size() >= max || list.stream().anyMatch(p -> p.pos().equals(placed.pos()))) {
			return false;
		}
		list.add(placed);
		setDirty();
		return true;
	}

	/** Forgets a logged block that was broken, whoever's log it is in, so that log has room for a newer one. */
	public void forgetPlacement(GlobalPos pos) {
		boolean changed = false;
		for (List<PlacedBlock> list : placements.values()) {
			changed |= list.removeIf(p -> p.pos().equals(pos));
		}
		if (changed) {
			setDirty();
		}
	}

	/** The block F13's fallback chose (M11), once it chose one. */
	public Optional<PlacedBlock> cairnFallback() {
		return Optional.ofNullable(cairnFallback);
	}

	public void setCairnFallback(@Nullable PlacedBlock block) {
		cairnFallback = block;
		setDirty();
	}
}
