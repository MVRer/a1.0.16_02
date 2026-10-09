package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * The world workstream's private state ({@code data/a1016_02/world.dat}): the scar salt, the inside of every
 * build (for the emptied house), the light on the mountain while it burns, and what the player did at pyramids
 * and bare groves. Server thread only.
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
	 * What the player did at one bare grove.
	 *
	 * @param firstVisitDay the in-game day they first stood in it
	 * @param replanted     leaves and saplings they placed in it so far
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

	static final Codec<WorldData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("salt").forGetter(d -> d.salt),
			Build.CODEC.listOf().optionalFieldOf("builds", List.of()).forGetter(d -> List.copyOf(d.builds.values())),
			GlobalPos.CODEC.listOf().optionalFieldOf("emptied", List.of()).forGetter(d -> List.copyOf(d.emptied)),
			Light.CODEC.optionalFieldOf("light").forGetter(d -> Optional.ofNullable(d.light)),
			GlobalPos.CODEC.listOf().optionalFieldOf("pyramidsDug", List.of()).forGetter(d -> List.copyOf(d.pyramidsDug)),
			Grove.CODEC.listOf().optionalFieldOf("groves", List.of()).forGetter(d -> List.copyOf(d.groves.values()))
	).apply(i, WorldData::new));

	static final SavedDataType<WorldData> TYPE = new SavedDataType<>(A1016_02.id("world"), WorldData::new, CODEC, null);

	private final long salt;
	private final Map<GlobalPos, Build> builds = new HashMap<>();
	private final Set<GlobalPos> emptied = new HashSet<>();
	private @Nullable Light light;
	private final Set<GlobalPos> pyramidsDug = new HashSet<>();
	private final Map<GlobalPos, Grove> groves = new HashMap<>();

	WorldData() {
		this.salt = ThreadLocalRandom.current().nextLong();
		setDirty();
	}

	private WorldData(long salt, List<Build> builds, List<GlobalPos> emptied, Optional<Light> light, List<GlobalPos> pyramidsDug, List<Grove> groves) {
		this.salt = salt;
		builds.forEach(b -> this.builds.put(b.site(), b));
		this.emptied.addAll(emptied);
		this.light = light.orElse(null);
		this.pyramidsDug.addAll(pyramidsDug);
		groves.forEach(g -> this.groves.put(g.site(), g));
	}

	public static WorldData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Random per world, made on the first load: where old scars are depends on it (with the seed and profile). */
	public long salt() {
		return salt;
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
