package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Lore's private, per-world facts ({@code data/a1016_02/lore.dat}). Shared facts (placed, read) live in
 * {@code HerobrineState}.
 * <ul>
 * <li>anchors: named places lore built or chose ({@code "grove"} the untouched grove, {@code "F15/room"} the test
 * room's lower corner, {@code "F30/grove"} and {@code "F30/bedrock"} the twin signs)</li>
 * <li>read targets: blocks that count as reading a fragment when looked at up close (signs, the cairn's core)</li>
 * <li>site claims: which {@code SiteRegistry} site each fragment used</li>
 * <li>eligible since: play ticks when each fragment could first be placed (own builds wait a while after it)</li>
 * </ul>
 */
public final class LoreData extends SavedData {
	public static final String GROVE = "grove";

	public static final Codec<LoreData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC).optionalFieldOf("anchors", Map.of()).forGetter(d -> d.anchors),
			Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC.listOf()).optionalFieldOf("readTargets", Map.of()).forGetter(d -> d.readTargets),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("siteClaims", Map.of()).forGetter(d -> d.siteClaims),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("eligibleSince", Map.of()).forGetter(d -> d.eligibleSince)
	).apply(i, LoreData::new));

	public static final SavedDataType<LoreData> TYPE = new SavedDataType<>(A1016_02.id("lore"), LoreData::new, CODEC, null);

	private final Map<String, GlobalPos> anchors = new TreeMap<>();
	private final Map<String, List<GlobalPos>> readTargets = new TreeMap<>();
	private final Map<String, Integer> siteClaims = new TreeMap<>();
	private final Map<String, Long> eligibleSince = new TreeMap<>();

	public LoreData() {
	}

	private LoreData(Map<String, GlobalPos> anchors, Map<String, List<GlobalPos>> readTargets, Map<String, Integer> siteClaims,
			Map<String, Long> eligibleSince) {
		this.anchors.putAll(anchors);
		readTargets.forEach((id, list) -> this.readTargets.put(id, new ArrayList<>(list)));
		this.siteClaims.putAll(siteClaims);
		this.eligibleSince.putAll(eligibleSince);
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

	public void setSiteClaim(String id, int siteId) {
		siteClaims.put(id, siteId);
		setDirty();
	}
}
