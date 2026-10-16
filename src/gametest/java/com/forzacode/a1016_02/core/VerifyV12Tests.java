package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.AtmosphereData;
import com.forzacode.a1016_02.atmosphere.FogWeather;
import com.forzacode.a1016_02.director.DirectorData;
import com.forzacode.a1016_02.director.DirectorMemory;
import com.forzacode.a1016_02.director.PacingMemory;
import com.forzacode.a1016_02.dig.DigData;
import com.forzacode.a1016_02.entity.EntityData;
import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.Level;

/**
 * VERIFY (v1.2): old worlds load. The fixtures are a real v1.0 dev world's {@code data/a1016_02} files (user Dev,
 * written by the v1.0 jar). Each loads through v1.2's own codecs (what {@code DataStorage} does with them); then the
 * same data in v1.1's shape (v1.2's output without the keys only v1.2 writes, which v1.1 never knew) loads and keeps
 * every value, with v1.2's new parts at their "never" defaults.
 */
public class VerifyV12Tests {
	/** Keys only v1.2 writes in these six files (none of them existed in v1.1's code). */
	private static final Set<String> V12_KEYS = Set.of("fogSpell", "rulesVersion", "unfitLooks", "parkedUntil", "firstHourFires", "ladder", "dugUnder",
			"fogWeather", "lastGlimpseDay", "glimpses");

	private static CompoundTag fixture(String name) {
		Path path = FabricLoader.getInstance().getModContainer("a1016_02_gametest").orElseThrow().findPath("review/v10world/" + name + ".dat").orElseThrow();
		try {
			return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("data");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** {@code tag} without any v1.2-only key, at any depth: what v1.1 would have written for the same data. */
	private static Tag v11(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			CompoundTag out = new CompoundTag();
			for (String key : compound.keySet()) {
				if (!V12_KEYS.contains(key)) {
					out.put(key, v11(compound.get(key)));
				}
			}
			return out;
		}
		if (tag instanceof ListTag list) {
			ListTag out = new ListTag();
			for (Tag element : list) {
				out.add(v11(element));
			}
			return out;
		}
		return tag.copy();
	}

	private static List<Long> sorted(long[] values) {
		return java.util.Arrays.stream(values).sorted().boxed().toList();
	}

	/** Loads {@code saved} (v1.1's shape) and checks it writes back the same v1.1 values. */
	private static <T> T roundTripV11(RegistryOps<Tag> ops, Codec<T> codec, Tag v11Shape, String what, List<String> wrong) {
		T loaded = codec.parse(ops, v11Shape).getOrThrow(message -> new IllegalStateException(what + ": " + message));
		Tag again = v11(codec.encodeStart(ops, loaded).getOrThrow());
		if (!again.equals(v11Shape)) {
			wrong.add(what + " lost or changed a value: " + v11Shape + " -> " + again);
		}
		return loaded;
	}

	/**
	 * Mariano's real v1.1 file (the R1 fixture, byte for byte his {@code config/a1016_02.json}): every section reaches
	 * its current version (root 1, director 3 since v1.3, atmosphere 2 since v1.3, dig 1, entity 5 since the trunk fix), and every field the v1.1 file did not have
	 * is at v1.2's default. (The R1 test checks the values the file had; this checks the ones it did not.)
	 */
	@GameTest
	public void verifyRealV11ConfigReachesEverySectionVersion(GameTestHelper helper) throws IOException {
		Path path = FabricLoader.getInstance().getModContainer("a1016_02_gametest").orElseThrow().findPath("review/a1016_02_v11.json").orElseThrow();
		com.google.gson.JsonObject file = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
		ModConfig loaded = ModConfig.read(file.toString());
		List<String> wrong = new ArrayList<>();
		if (loaded.configVersion != 1) {
			wrong.add("configVersion " + loaded.configVersion);
		}
		com.google.gson.Gson gson = new com.google.gson.Gson();
		record Expect(String name, Class<?> type, java.util.function.Supplier<?> fresh, int version) {
		}
		List<Expect> expected = List.of(new Expect("director", com.forzacode.a1016_02.director.DirectorConfig.class,
				com.forzacode.a1016_02.director.DirectorConfig::new, 3),
				new Expect("atmosphere", com.forzacode.a1016_02.atmosphere.AtmosphereConfig.class, com.forzacode.a1016_02.atmosphere.AtmosphereConfig::new, 2),
				new Expect("dig", com.forzacode.a1016_02.dig.DigConfig.class, com.forzacode.a1016_02.dig.DigConfig::new, 1),
				new Expect("entity", com.forzacode.a1016_02.entity.EntityConfig.class, com.forzacode.a1016_02.entity.EntityConfig::new, 5));
		for (Expect e : expected) {
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object section = ModConfig.readSection(loaded.sections, e.name(), (Class) e.type(), (java.util.function.Supplier) e.fresh());
			com.google.gson.JsonObject after = com.google.gson.JsonParser.parseString(gson.toJson(section)).getAsJsonObject();
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object fresh = ModConfig.readSection(new com.google.gson.JsonObject(), e.name(), (Class) e.type(), (java.util.function.Supplier) e.fresh());
			com.google.gson.JsonObject defaults = com.google.gson.JsonParser.parseString(gson.toJson(fresh)).getAsJsonObject();
			if (after.get("version").getAsInt() != e.version()) {
				wrong.add(e.name() + ".version " + after.get("version"));
			}
			com.google.gson.JsonObject before = file.getAsJsonObject("sections").getAsJsonObject(e.name());
			for (String key : defaults.keySet()) {
				if (!before.has(key) && !defaults.get(key).equals(after.get(key))) {
					wrong.add(e.name() + "." + key + " (new in v1.2) " + after.get(key) + ", default " + defaults.get(key));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void verifyOldWorldSavesLoadInV12(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<String> wrong = new ArrayList<>();

		// state.dat: v1.0's subject becomes the one known player; no fog spell.
		HerobrineState state = HerobrineState.CODEC.parse(ops, fixture("state")).getOrThrow();
		helper.assertTrue(state.players().size() == 1 && state.players().get(0).name().equals("Dev"), "v1.0 state players " + state.players());
		helper.assertTrue(state.effects().fogSpell().isEmpty(), "v1.0 state has a fog spell");
		UUID dev = state.players().get(0).uuid();
		HerobrineState stateV11 = roundTripV11(ops, HerobrineState.CODEC, v11(HerobrineState.CODEC.encodeStart(ops, state).getOrThrow()), "state.dat", wrong);
		helper.assertTrue(stateV11.effects().fogSpell().isEmpty() && stateV11.player(dev).isPresent(), "v1.1 state " + stateV11.players());

		// director.dat: v1.0's single memory (format 1), then v1.1's per-player one (format 2): rules version 0 both ways.
		DirectorData director = DirectorData.CODEC.parse(ops, fixture("director")).getOrThrow();
		DirectorMemory memory = director.memory();
		PacingMemory unowned = memory.unowned();
		helper.assertTrue(memory.world().rulesVersion() == 0 && unowned != null && unowned.rulesVersion() == 0 && memory.world().tracesAt() > 0,
				"v1.0 director: world rules " + memory.world().rulesVersion() + ", unowned " + unowned);
		helper.assertTrue(memory.adoptUnowned(dev), "the v1.0 pacing was not adopted");
		CompoundTag directorV11 = (CompoundTag) v11(DirectorData.CODEC.encodeStart(ops, director).getOrThrow());
		DirectorData director11 = roundTripV11(ops, DirectorData.CODEC, directorV11, "director.dat", wrong);
		PacingMemory pacing = director11.memory().find(dev).orElse(null);
		helper.assertTrue(director11.memory().world().rulesVersion() == 0 && pacing != null && pacing.rulesVersion() == 0
				&& pacing.parkedUntil("steps_behind_once") == -1L && director11.memory().world().tracesAt() == memory.world().tracesAt(),
				"v1.1 director: world rules " + director11.memory().world().rulesVersion() + ", pacing " + pacing);

		// dig.dat: nobody on the ladder.
		DigData dig = DigData.CODEC.parse(ops, fixture("dig")).getOrThrow();
		helper.assertTrue(dig.ladderRung(dev) == 0, "v1.0 dig ladder rung");
		DigData dig11 = roundTripV11(ops, DigData.CODEC, v11(DigData.CODEC.encodeStart(ops, dig).getOrThrow()), "dig.dat", wrong);
		helper.assertTrue(dig11.ladderRung(dev) == 0, "v1.1 dig ladder rung");

		// atmosphere.dat: the fog weather starts from the first night.
		AtmosphereData atmosphere = AtmosphereData.CODEC.parse(ops, fixture("atmosphere")).getOrThrow();
		helper.assertTrue(atmosphere.fogWeather().equals(FogWeather.Memory.START), "v1.0 atmosphere fog weather " + atmosphere.fogWeather());
		AtmosphereData atmosphere11 = roundTripV11(ops, AtmosphereData.CODEC, v11(AtmosphereData.CODEC.encodeStart(ops, atmosphere).getOrThrow()),
				"atmosphere.dat", wrong);
		helper.assertTrue(atmosphere11.fogWeather().equals(FogWeather.Memory.START), "v1.1 atmosphere fog weather " + atmosphere11.fogWeather());

		// watch.dat: dug and placed marks kept, no dug-under marks.
		WatchData watch = WatchData.CODEC.parse(ops, fixture("watch")).getOrThrow();
		WatchData.Footprint overworld = watch.footprint(Level.OVERWORLD);
		helper.assertTrue(overworld.dug.toArray().length > 0 && overworld.placed.toArray().length > 0 && overworld.dugUnder.toArray().length == 0,
				"v1.0 watch: dug " + overworld.dug.toArray().length + ", placed " + overworld.placed.toArray().length);
		// Its visits are a hash map written in hash order, so it compares by content, not as a tag.
		WatchData watch11 = WatchData.CODEC.parse(ops, v11(WatchData.CODEC.encodeStart(ops, watch).getOrThrow())).getOrThrow();
		WatchData.Footprint overworld11 = watch11.footprint(Level.OVERWORLD);
		helper.assertTrue(sorted(overworld11.dug.toArray()).equals(sorted(overworld.dug.toArray()))
				&& sorted(overworld11.placed.toArray()).equals(sorted(overworld.placed.toArray())) && overworld11.visits.equals(overworld.visits)
				&& overworld11.dugUnder.toArray().length == 0, "v1.1 watch lost or changed a mark or a visit");

		// entity.dat: no glimpse yet.
		EntityData entity = EntityData.CODEC.parse(ops, fixture("entity")).getOrThrow();
		helper.assertTrue(entity.lastGlimpseDay() == -1L && entity.glimpses() == 0, "v1.0 entity glimpses");
		EntityData entity11 = roundTripV11(ops, EntityData.CODEC, v11(EntityData.CODEC.encodeStart(ops, entity).getOrThrow()), "entity.dat", wrong);
		helper.assertTrue(entity11.lastGlimpseDay() == -1L && entity11.glimpses() == 0, "v1.1 entity glimpses");

		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
