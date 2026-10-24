package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.world.sc.ScMarks;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * The world workstream's private state ({@code data/a1016_02/world.dat}): the scar salt, the inside of every
 * build (for the emptied house), the light on the mountain while it burns, what the players did at pyramids and
 * bare groves, where the one-per-world sites (the hut, the core pyramid) were recorded, the signature moments
 * ({@link SignatureData}), the last base world saw for each player (by UUID, D-061) and the day of the last real new
 * scar (the world-wide rate), the self-created layer's world marks ({@link ScMarks}, optional) and its second wave's
 * places ({@link Sc2Data}, optional key {@code sc2}). A save from before
 * multiplayer has no bases and no new-scar day: both start empty, and every other field was already about the world,
 * not one player. Server thread only.
 */
public final class WorldData extends SavedData {
	/** The inside of a build, keyed by its site position. */
	record Build(GlobalPos site, BoundingBox interior) {
		static final Codec<Build> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("site").forGetter(Build::site),
				BoundingBox.CODEC.fieldOf("interior").forGetter(Build::interior)
		).apply(i, Build::new));
	}

	/** The light on the mountain, while it stands. */
	public record Light(GlobalPos pos, long day) {
		static final Codec<Light> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(Light::pos),
				Codec.LONG.fieldOf("day").forGetter(Light::day)
		).apply(i, Light::new));
	}

	/**
	 * What the players did at one bare grove (one record per grove, whoever did it).
	 *
	 * @param firstVisitDay the in-game day someone first stood in it
	 * @param replanted     leaves and saplings players placed in it so far
	 * @param leftAlone     LEFT_GROVES_ALONE already applied for it
	 */
	public record Grove(GlobalPos site, long firstVisitDay, int replanted, long lastReplantDay, boolean leftAlone) {
		static final Codec<Grove> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("site").forGetter(Grove::site),
				Codec.LONG.fieldOf("firstVisitDay").forGetter(Grove::firstVisitDay),
				Codec.INT.fieldOf("replanted").forGetter(Grove::replanted),
				Codec.LONG.optionalFieldOf("lastReplantDay", -1L).forGetter(Grove::lastReplantDay),
				Codec.BOOL.fieldOf("leftAlone").forGetter(Grove::leftAlone)
		).apply(i, Grove::new));
	}

	/** The last base world saw for one player while they took part. */
	record PlayerBase(UUID player, GlobalPos base) {
		static final Codec<PlayerBase> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerBase::player),
				GlobalPos.CODEC.fieldOf("base").forGetter(PlayerBase::base)
		).apply(i, PlayerBase::new));
	}

	static final Codec<WorldData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("salt").forGetter(d -> d.salt),
			Build.CODEC.listOf().optionalFieldOf("builds", List.of()).forGetter(d -> List.copyOf(d.builds.values())),
			GlobalPos.CODEC.listOf().optionalFieldOf("emptied", List.of()).forGetter(d -> List.copyOf(d.emptied)),
			Light.CODEC.optionalFieldOf("light").forGetter(d -> Optional.ofNullable(d.light)),
			GlobalPos.CODEC.listOf().optionalFieldOf("pyramidsDug", List.of()).forGetter(d -> List.copyOf(d.pyramidsDug)),
			Grove.CODEC.listOf().optionalFieldOf("groves", List.of()).forGetter(d -> List.copyOf(d.groves.values())),
			Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC).optionalFieldOf("singles", Map.of()).forGetter(d -> Map.copyOf(d.singles)),
			SignatureData.CODEC.optionalFieldOf("signatures").forGetter(d -> Optional.of(d.signatures)),
			PlayerBase.CODEC.listOf().optionalFieldOf("bases", List.of()).forGetter(d -> d.bases.entrySet().stream()
					.map(e -> new PlayerBase(e.getKey(), e.getValue())).toList()),
			Codec.LONG.optionalFieldOf("lastNewScarDay", Long.MIN_VALUE).forGetter(d -> d.lastNewScarDay),
			ScMarks.CODEC.lenientOptionalFieldOf("sc").forGetter(d -> d.sc.isEmpty() ? Optional.empty() : Optional.of(d.sc)),
			Sc2Data.CODEC.lenientOptionalFieldOf("sc2").forGetter(d -> d.sc2.isEmpty() ? Optional.empty() : Optional.of(d.sc2))
	).apply(i, WorldData::new));

	static final SavedDataType<WorldData> TYPE = new SavedDataType<>(A1016_02.id("world"), WorldData::new, CODEC, null);

	private final long salt;
	private final Map<GlobalPos, Build> builds = new HashMap<>();
	private final Set<GlobalPos> emptied = new HashSet<>();
	private @Nullable Light light;
	private final Set<GlobalPos> pyramidsDug = new HashSet<>();
	private final Map<GlobalPos, Grove> groves = new HashMap<>();
	private final Map<String, GlobalPos> singles = new HashMap<>();
	private final SignatureData signatures;
	private final Map<UUID, GlobalPos> bases = new HashMap<>();
	private long lastNewScarDay = Long.MIN_VALUE;
	private final ScMarks sc;
	private final Sc2Data sc2;

	/** A fresh world's data (tests make their own to keep the shared one untouched). */
	public WorldData() {
		this.salt = ThreadLocalRandom.current().nextLong();
		this.signatures = new SignatureData();
		this.signatures.onChange(this::setDirty);
		this.sc = new ScMarks();
		this.sc.onChange(this::setDirty);
		this.sc2 = new Sc2Data();
		this.sc2.onChange(this::setDirty);
		setDirty();
	}

	private WorldData(long salt, List<Build> builds, List<GlobalPos> emptied, Optional<Light> light, List<GlobalPos> pyramidsDug, List<Grove> groves,
			Map<String, GlobalPos> singles, Optional<SignatureData> signatures, List<PlayerBase> bases, long lastNewScarDay, Optional<ScMarks> sc,
			Optional<Sc2Data> sc2) {
		this.salt = salt;
		this.sc = sc.orElseGet(ScMarks::new);
		this.sc.onChange(this::setDirty);
		this.sc2 = sc2.orElseGet(Sc2Data::new);
		this.sc2.onChange(this::setDirty);
		this.signatures = signatures.orElseGet(SignatureData::new);
		this.signatures.onChange(this::setDirty);
		builds.forEach(b -> this.builds.put(b.site(), b));
		this.emptied.addAll(emptied);
		this.light = light.orElse(null);
		this.pyramidsDug.addAll(pyramidsDug);
		groves.forEach(g -> this.groves.put(g.site(), g));
		this.singles.putAll(singles);
		bases.forEach(b -> this.bases.put(b.player(), b.base()));
		this.lastNewScarDay = lastNewScarDay;
	}

	public static WorldData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The signature moments and lone redstone torches of this world. */
	public SignatureData signatures() {
		return signatures;
	}

	/**
	 * What the self-created layer's world features remember (optional key {@code sc}: absent in older saves, skipped by
	 * older readers, read leniently).
	 */
	public ScMarks sc() {
		return sc;
	}

	/**
	 * The second wave's places: every one generated so far, who went in, what each answered (optional key {@code sc2}:
	 * absent in older saves, skipped by older readers, read leniently; docs/SELF_CREATED_2.md, SC2-0b).
	 */
	public Sc2Data sc2() {
		return sc2;
	}

	/** Random per world, made on the first load: where old scars are depends on it (with the seed and profile). */
	public long salt() {
		return salt;
	}

	// --- players' bases (D-061) ---

	/** The last base world saw for this player while they took part, if any. */
	public Optional<GlobalPos> base(UUID player) {
		return Optional.ofNullable(bases.get(player));
	}

	/** Remembers a player's live base. */
	public void setBase(UUID player, GlobalPos base) {
		if (!base.equals(bases.put(player, base))) {
			setDirty();
		}
	}

	// --- new scars, world-wide ---

	/** The in-game day of the last real new scar (a dead hill or a bare grove) anywhere, or {@link Long#MIN_VALUE}. */
	public long lastNewScarDay() {
		return lastNewScarDay;
	}

	public void setLastNewScarDay(long day) {
		lastNewScarDay = day;
		setDirty();
	}

	// --- one-per-world sites ---

	/** Where a one-per-world site ({@link WorldSites#HUT}, {@link WorldSites#CORE_PYRAMID}) was recorded, if it was. */
	public Optional<GlobalPos> single(String key) {
		return Optional.ofNullable(singles.get(key));
	}

	/** Remembers where a one-per-world site was recorded; it is never recorded again for this world. */
	public void putSingle(String key, GlobalPos pos) {
		singles.put(key, pos);
		setDirty();
	}

	public Map<String, GlobalPos> singles() {
		return Map.copyOf(singles);
	}

	// --- builds ---

	public void putBuild(GlobalPos site, BoundingBox interior) {
		builds.put(site, new Build(site, interior));
		setDirty();
	}

	public Optional<BoundingBox> buildInterior(GlobalPos site) {
		return Optional.ofNullable(builds.get(site)).map(Build::interior);
	}

	public boolean isEmptied(GlobalPos site) {
		return emptied.contains(site);
	}

	public void markEmptied(GlobalPos site) {
		if (emptied.add(site)) {
			setDirty();
		}
	}

	// --- the light on the mountain ---

	public Optional<Light> light() {
		return Optional.ofNullable(light);
	}

	public void setLight(@Nullable Light value) {
		light = value;
		setDirty();
	}

	// --- triggers ---

	/** @return true the first time this pyramid is dug into */
	public boolean markPyramidDug(GlobalPos site) {
		boolean added = pyramidsDug.add(site);
		if (added) {
			setDirty();
		}
		return added;
	}

	public Optional<Grove> grove(GlobalPos site) {
		return Optional.ofNullable(groves.get(site));
	}

	public void putGrove(Grove grove) {
		groves.put(grove.site(), grove);
		setDirty();
	}

	public List<Grove> groves() {
		return new ArrayList<>(groves.values());
	}
}
