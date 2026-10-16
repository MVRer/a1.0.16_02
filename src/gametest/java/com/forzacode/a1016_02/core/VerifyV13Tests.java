package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.dig.DigConfig;
import com.forzacode.a1016_02.director.DirectorConfig;
import com.forzacode.a1016_02.director.DirectorData;
import com.forzacode.a1016_02.director.PacingMemory;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.EntityData;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

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

/**
 * VERIFY-3 (v1.3): v1.2's config and saves load in v1.3.
 *
 * <p>The config fixtures are written by the release jars themselves (every section's defaults as
 * {@code ModConfig.section} writes them, read and upgraded): {@code review/a1016_02_v12.json} by v1.2's jar
 * ({@code dist/a1016_02-1.2.0-preview.2-26.3.jar}, b6873af) and {@code review/a1016_02_v13_preview1.json} by the v1.3
 * preview ({@code dist/a1016_02-1.3.0-preview.1-26.3.jar}, INT-2). The save fixtures are v1.2's verifier's (a real
 * v1.0 dev world), and v1.2's shape is v1.3's output without the keys only v1.3 writes.
 */
public class VerifyV13Tests {
	/** Keys only v1.3 writes in a world's files (PacingMemory's rechecks, EntityData's screenshot). */
	private static final Set<String> V13_KEYS = Set.of("rechecks", "recheckFires", "screenshotDay", "screenshotBy");

	private record Part(String name, Class<?> type, Supplier<?> fresh, int version) {
	}

	private static final List<Part> VERSIONED = List.of(new Part("director", DirectorConfig.class, DirectorConfig::new, 3),
			new Part("atmosphere", AtmosphereConfig.class, AtmosphereConfig::new, 1), new Part("dig", DigConfig.class, DigConfig::new, 1),
			new Part("entity", EntityConfig.class, EntityConfig::new, 4));

	private static JsonObject file(String name) throws IOException {
		Path path = FabricLoader.getInstance().getModContainer("a1016_02_gametest").orElseThrow().findPath("review/" + name).orElseThrow();
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	/** A section as the game reads it from this file's {@code sections}, as JSON. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static JsonObject read(JsonObject sections, Part part) {
		Object section = ModConfig.readSection(sections, part.name(), (Class) part.type(), (Supplier) part.fresh());
		return JsonParser.parseString(new Gson().toJson(section)).getAsJsonObject();
	}

	private static CompoundTag fixture(String name) {
		Path path = FabricLoader.getInstance().getModContainer("a1016_02_gametest").orElseThrow().findPath("review/v10world/" + name + ".dat").orElseThrow();
		try {
			return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("data");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** {@code tag} without any v1.3-only key, at any depth: what v1.2 would have written for the same data. */
	private static Tag v12(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			CompoundTag out = new CompoundTag();
			for (String key : compound.keySet()) {
				if (!V13_KEYS.contains(key)) {
					out.put(key, v12(compound.get(key)));
				}
			}
			return out;
		}
		if (tag instanceof ListTag list) {
			ListTag out = new ListTag();
			for (Tag element : list) {
				out.add(v12(element));
			}
			return out;
		}
		return tag.copy();
	}

	/**
	 * v1.2's file with hand edits in every versioned section: each section reaches v1.3's version (only director's
	 * changes, 2 to 3, which only adds two fields), every value the file had is kept (defaults and edits alike: no v1.2
	 * default changed in v1.3), and every field new in v1.3 is at its default.
	 */
	@GameTest
	public void verifyV12ConfigLoadsInV13(GameTestHelper helper) throws IOException {
		JsonObject file = file("a1016_02_v12.json");
		JsonObject sections = file.getAsJsonObject("sections");
		Map<String, Map<String, JsonPrimitive>> edits = Map.of("director", Map.of("unfitParkMinutes", new JsonPrimitive(45.0)), "atmosphere",
				Map.of("fogGateShare", new JsonPrimitive(0.6), "caveSoundAloneRadius", new JsonPrimitive(30)), "dig",
				Map.of("ladderMinRun", new JsonPrimitive(20)), "entity", Map.of("glimpseMinDistance", new JsonPrimitive(16.0)));
		edits.forEach((section, values) -> values.forEach((key, value) -> {
			helper.assertTrue(sections.getAsJsonObject(section).has(key), "the v1.2 file has no " + section + "." + key);
			sections.getAsJsonObject(section).add(key, value);
		}));
		ModConfig loaded = ModConfig.read(file.toString());
		List<String> wrong = new ArrayList<>();
		if (loaded.configVersion != 1) {
			wrong.add("configVersion " + loaded.configVersion);
		}
		for (Part part : VERSIONED) {
			JsonObject before = sections.getAsJsonObject(part.name());
			JsonObject after = read(loaded.sections, part);
			JsonObject defaults = read(new JsonObject(), part);
			if (after.get("version").getAsInt() != part.version()) {
				wrong.add(part.name() + ".version " + after.get("version"));
			}
			for (Map.Entry<String, JsonElement> e : before.entrySet()) {
				if (!e.getKey().equals("version") && !numberEquals(e.getValue(), after.get(e.getKey()))) {
					wrong.add(part.name() + "." + e.getKey() + " was " + e.getValue() + ", now " + after.get(e.getKey()));
				}
			}
			for (String key : defaults.keySet()) {
				if (!before.has(key) && !numberEquals(defaults.get(key), after.get(key))) {
					wrong.add(part.name() + "." + key + " (new in v1.3) " + after.get(key) + ", default " + defaults.get(key));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static boolean numberEquals(JsonElement a, JsonElement b) {
		if (a == null || b == null) {
			return a == b;
		}
		if (a.isJsonPrimitive() && b.isJsonPrimitive() && a.getAsJsonPrimitive().isNumber() && b.getAsJsonPrimitive().isNumber()) {
			return a.getAsDouble() == b.getAsDouble();
		}
		return a.equals(b);
	}

	/**
	 * The v1.3 preview's file (INT-2's jar in {@code dist/}, written with {@code echoDoorVolume} 1.0 and
	 * {@code echoMiningFakeGapMinTicks} 8, the defaults FIX-ECHO changed to 1.6 and 10 after it): the door echo must reach
	 * R4's 1.6 (at 1.0 a door at the band's far end, 16 blocks, is silent), as D-075 steps every later default change.
	 * The fake's 8 needs no step: {@code EchoMiningCard} never plays a period under {@code FAKE_MIN_PERIOD} (10).
	 * {@code screenshotMaxFogEnd} is skipped and the new screenshot fields are at their defaults.
	 */
	@GameTest
	public void verifyV13PreviewConfigGetsTheAudibleDoorEcho(GameTestHelper helper) throws IOException {
		JsonObject file = file("a1016_02_v13_preview1.json");
		helper.assertTrue(file.getAsJsonObject("sections").getAsJsonObject("atmosphere").get("echoDoorVolume").getAsDouble() == 1.0,
				"the fixture is the preview's (echoDoorVolume 1.0)");
		ModConfig loaded = ModConfig.read(file.toString());
		AtmosphereConfig atmosphere = ModConfig.readSection(loaded.sections, "atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
		EntityConfig entity = ModConfig.readSection(loaded.sections, "entity", EntityConfig.class, EntityConfig::new);
		EntityConfig fresh = new EntityConfig();
		helper.assertTrue(entity.screenshotMinPixels == fresh.screenshotMinPixels && entity.screenshotMinFog == fresh.screenshotMinFog,
				"the new screenshot fields: " + entity.screenshotMinPixels + ", " + entity.screenshotMinFog);
		helper.assertTrue(Math.abs(atmosphere.echoDoorVolume - new AtmosphereConfig().echoDoorVolume) < 1.0E-6,
				"a v1.3.0-preview.1 config keeps echoDoorVolume " + atmosphere.echoDoorVolume + " (atmosphere section version "
						+ atmosphere.version + ", no step): the door echo stays silent at 16 blocks, R4's finding, for that install");
		helper.succeed();
	}

	/**
	 * v1.2's saves load in v1.3: the v1.0 dev world's files through v1.3's codecs, then the same data in v1.2's shape
	 * (v1.3's output without the v1.3-only keys) keeps every value with v1.3's parts at none (no rechecks, no
	 * screenshot); and v1.3's own parts survive a save.
	 */
	@GameTest
	public void verifyV12SavesLoadInV13(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<String> wrong = new ArrayList<>();
		HerobrineState state = HerobrineState.CODEC.parse(ops, fixture("state")).getOrThrow();
		UUID dev = state.players().get(0).uuid();

		// director.dat: the v1.0 pacing adopted by its player, written by v1.3, read back in v1.2's shape.
		DirectorData director = DirectorData.CODEC.parse(ops, fixture("director")).getOrThrow();
		helper.assertTrue(director.memory().adoptUnowned(dev), "the v1.0 pacing was not adopted");
		CompoundTag written = (CompoundTag) DirectorData.CODEC.encodeStart(ops, director).getOrThrow();
		CompoundTag shape12 = (CompoundTag) v12(written);
		DirectorData director12 = DirectorData.CODEC.parse(ops, shape12).getOrThrow();
		PacingMemory pacing = director12.memory().find(dev).orElse(null);
		helper.assertTrue(pacing != null && pacing.rechecks() == 0 && pacing.recheckFires() == 0, "v1.2 pacing's rechecks " + pacing);
		Tag again = v12(DirectorData.CODEC.encodeStart(ops, director12).getOrThrow());
		if (!again.equals(shape12)) {
			wrong.add("director.dat lost or changed a value: " + shape12 + " -> " + again);
		}
		// v1.3's own: a pacing's rechecks survive a save.
		CompoundTag withRechecks = written.copy();
		boolean[] put = {false};
		putRechecks(withRechecks, put);
		helper.assertTrue(put[0], "no pacing tag with rechecks in " + written);
		PacingMemory rechecked = DirectorData.CODEC.parse(ops, withRechecks).getOrThrow().memory().find(dev).orElseThrow();
		helper.assertTrue(rechecked.rechecks() == 7 && rechecked.recheckFires() == 2, "rechecks after a save " + rechecked.rechecks());

		// entity.dat: no screenshot in v1.2's shape; one recorded survives a save, and v1.2's shape drops only it.
		EntityData entity = EntityData.CODEC.parse(ops, fixture("entity")).getOrThrow();
		helper.assertTrue(!entity.screenshotTaken() && entity.screenshotBy().isEmpty() && entity.screenshotDay() == -1L, "v1.0 entity screenshot");
		helper.assertTrue(entity.recordScreenshot(dev, 3L), "the screenshot was not recorded");
		Tag entityTag = EntityData.CODEC.encodeStart(ops, entity).getOrThrow();
		EntityData entityAgain = EntityData.CODEC.parse(ops, entityTag).getOrThrow();
		helper.assertTrue(entityAgain.screenshotTaken() && entityAgain.screenshotDay() == 3L && entityAgain.screenshotBy().equals(java.util.Optional.of(dev)),
				"the screenshot after a save: day " + entityAgain.screenshotDay() + " by " + entityAgain.screenshotBy());
		Tag entity12 = v12(entityTag);
		EntityData entityV12 = EntityData.CODEC.parse(ops, entity12).getOrThrow();
		helper.assertTrue(!entityV12.screenshotTaken() && entityV12.lastGlimpseDay() == entity.lastGlimpseDay() && entityV12.glimpses() == entity.glimpses(),
				"v1.2 entity: screenshot " + entityV12.screenshotTaken());
		Tag entity12Again = v12(EntityData.CODEC.encodeStart(ops, entityV12).getOrThrow());
		if (!entity12Again.equals(entity12)) {
			wrong.add("entity.dat lost or changed a value: " + entity12 + " -> " + entity12Again);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** Sets rechecks 7 and recheckFires 2 in every compound that has a {@code rechecks} key. */
	private static void putRechecks(Tag tag, boolean[] put) {
		if (tag instanceof CompoundTag compound) {
			if (compound.contains("rechecks")) {
				compound.putInt("rechecks", 7);
				compound.putInt("recheckFires", 2);
				put[0] = true;
			}
			for (String key : compound.keySet()) {
				putRechecks(compound.get(key), put);
			}
		} else if (tag instanceof ListTag list) {
			for (Tag element : list) {
				putRechecks(element, put);
			}
		}
	}
}
