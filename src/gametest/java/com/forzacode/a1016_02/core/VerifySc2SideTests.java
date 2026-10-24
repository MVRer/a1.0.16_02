package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * SC2-0a, the side rule (D-098, docs/SELF_CREATED_2.md, "Network and sync"): a dedicated server never loads a client
 * class, so (1) no class outside a client package ({@code *.client}, {@code *.mixin.client} and what is under them)
 * names a client-only class: {@code net.minecraft.client}, {@code com.mojang.blaze3d} or Fabric's client API, by import
 * or written out in its code (comments aside); (2) no mixin in a client package is listed on the common or server side
 * of its mixin config, and every one on the client side is in a client package; the {@code client} entrypoints are in
 * client packages and the {@code main} ones are not. And (3) the callers of {@code MobTamper.hunt} (D-096): only the hunts, {@code accident.sc2}. Read from
 * the mod's own sources ({@code src/main}: what a server runs; the game test sources run in development only).
 */
public class VerifySc2SideTests {
	/**
	 * What only a client has: the merged jar's packages a dedicated server's jar lacks (R12-42 added the last four: the
	 * renderer's {@code renderpearl}, Realms, and Fabric's client implementation).
	 */
	static final List<String> CLIENT_ONLY = List.of("net.minecraft.client.", "com.mojang.blaze3d.", "net.fabricmc.fabric.api.client.", "com.mojang.renderpearl.",
			"com.mojang.realmsclient.", "net.minecraft.realms.", "net.fabricmc.fabric.impl.client.");
	/** The mod's own client classes (R12-41): any of its packages with a {@code client} segment. */
	private static final Pattern MOD_CLIENT = Pattern.compile("com\\.forzacode\\.a1016_02\\.(?:[A-Za-z0-9_]+\\.)*client\\.");
	/** What {@link #clientNamed} calls a name of the mod's own client classes. */
	static final String MOD_CLIENT_NAME = "the mod's own client classes";
	/** Where {@code hunt} may be called from (and the two files that declare and implement it). */
	static final String HUNT_CALLERS = "com/forzacode/a1016_02/accident/sc2/";
	static final Set<String> HUNT_DECLARED = Set.of("com/forzacode/a1016_02/core/MobTamper.java", "com/forzacode/a1016_02/atmosphere/mob/MobTamperImpl.java");
	/** A call (or a declaration) of a method named {@code hunt}. */
	private static final Pattern HUNT_CALL = Pattern.compile("(?<![\\w$])hunt\\s*\\(");

	@GameTest
	public void noClassOutsideTheClientPackagesNamesAClientClass(GameTestHelper helper) throws IOException {
		Path sources = VerifyScSpoilerTests.sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found");
		// The rule catches what it should (and not a comment).
		helper.assertTrue(clientNamed("import net.minecraft.client.Minecraft;\nclass A {}").equals(List.of("net.minecraft.client.")), "an import");
		helper.assertTrue(clientNamed("class A { Object m = net.minecraft.client.Minecraft.getInstance(); }").size() == 1, "a name written out");
		helper.assertTrue(clientNamed("/** {@link net.minecraft.client.Minecraft} */ class A { String s = \"//\"; } // net.minecraft.client.X").isEmpty(),
				"a comment counted");
		helper.assertTrue(clientNamed("import com.forzacode.a1016_02.entity.client.sc2.s27.S27Client;\nclass A {}").equals(List.of(MOD_CLIENT_NAME)),
				"the mod's own client class");
		helper.assertTrue(clientNamed("import com.mojang.renderpearl.X;\nclass A {}").equals(List.of("com.mojang.renderpearl.")), "a client package of 26.3");
		helper.assertTrue(clientNamed("import com.forzacode.a1016_02.entity.sc2.s27.Sc27;\nclass A {}").isEmpty(), "a common class of the mod");
		helper.assertTrue(isClientPackage("com/forzacode/a1016_02/entity/client/sc2/EntitySc2Client.java")
				&& isClientPackage("com/forzacode/a1016_02/atmosphere/mixin/client/FogRendererMixin.java")
				&& !isClientPackage("com/forzacode/a1016_02/atmosphere/mob/MobTamperImpl.java")
				&& !isClientPackage("com/forzacode/a1016_02/debug/gallery/clientish/Shot.java"), "the client packages");

		List<String> found = new ArrayList<>();
		int files = 0;
		try (Stream<Path> walk = Files.walk(sources)) {
			for (Path file : walk.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
				String rel = rel(sources, file);
				files++;
				if (isClientPackage(rel)) {
					continue;
				}
				for (String name : clientNamed(Files.readString(file))) {
					found.add(rel + " names " + name);
				}
			}
		}
		helper.assertTrue(files > 300, "only " + files + " source files under " + sources);
		helper.assertTrue(found.isEmpty(), found.size() + " classes outside the client packages name a client class (a dedicated server would crash): " + found);
		helper.succeed();
	}

	@GameTest
	public void everyClientMixinAndEntrypointIsOnTheClientSide(GameTestHelper helper) throws IOException {
		Path sources = VerifyScSpoilerTests.sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found");
		Path resources = sources.getParent().resolve("resources");
		List<String> wrong = new ArrayList<>();
		int configs = 0;
		try (Stream<Path> walk = Files.list(resources)) {
			for (Path json : walk.filter(p -> p.getFileName().toString().endsWith(".mixins.json")).sorted().toList()) {
				configs++;
				JsonObject config = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
				String pkg = config.get("package").getAsString();
				Set<String> client = names(config, "client");
				Set<String> common = names(config, "mixins");
				common.addAll(names(config, "server"));
				for (String mixin : common) {
					if (isClientPackage((pkg + "." + mixin).replace('.', '/') + ".java")) {
						wrong.add(json.getFileName() + ": " + mixin + " is in a client package but not on the client side");
					}
				}
				for (String mixin : client) {
					if (!isClientPackage((pkg + "." + mixin).replace('.', '/') + ".java")) {
						wrong.add(json.getFileName() + ": client mixin " + mixin + " is not in a client package");
					}
				}
			}
		}
		helper.assertTrue(configs >= 10, "only " + configs + " mixin configs in " + resources);

		JsonObject mod = JsonParser.parseString(Files.readString(resources.resolve("fabric.mod.json"))).getAsJsonObject();
		JsonObject entrypoints = mod.getAsJsonObject("entrypoints");
		for (String entry : names(entrypoints, "client")) {
			if (!isClientPackage(entry.replace('.', '/') + ".java")) {
				wrong.add("fabric.mod.json: client entrypoint " + entry + " is not in a client package");
			}
		}
		for (String entry : names(entrypoints, "main")) {
			if (isClientPackage(entry.replace('.', '/') + ".java")) {
				wrong.add("fabric.mod.json: main entrypoint " + entry + " is in a client package");
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	/**
	 * R12-43: a mixin that serves only the dev gallery never applies in a release (entity's config plugin), so a release
	 * client neither carries it nor breaks on it; every other mixin applies as listed.
	 */
	@GameTest
	public void theGallerysOwnMixinsApplyOnlyInDevelopment(GameTestHelper helper) throws IOException {
		Path sources = VerifyScSpoilerTests.sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found");
		JsonObject config = JsonParser.parseString(Files.readString(sources.getParent().resolve("resources/a1016_02.entity.mixins.json"))).getAsJsonObject();
		String plugin = com.forzacode.a1016_02.entity.EntityMixinPlugin.class.getName();
		helper.assertTrue(config.has("plugin") && config.get("plugin").getAsString().equals(plugin), "no plugin: " + config.get("plugin"));
		Set<String> listed = names(config, "client");
		listed.addAll(names(config, "mixins"));
		for (String devOnly : com.forzacode.a1016_02.entity.EntityMixinPlugin.DEV_ONLY) {
			helper.assertTrue(listed.stream().anyMatch(m -> m.endsWith(devOnly)), "not listed: " + devOnly);
		}
		for (String mixin : listed) {
			String name = config.get("package").getAsString() + "." + mixin;
			boolean devOnly = mixin.contains("StandIn");
			helper.assertTrue(com.forzacode.a1016_02.entity.EntityMixinPlugin.applies(name, false) == !devOnly, name + " in a release");
			helper.assertTrue(com.forzacode.a1016_02.entity.EntityMixinPlugin.applies(name, true), name + " in development");
		}
		helper.succeed();
	}

	@GameTest
	public void onlyTheHuntsCallHunt(GameTestHelper helper) throws IOException {
		Path sources = VerifyScSpoilerTests.sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found");
		helper.assertTrue(huntCalls("x.hunt(mob, p, 20); y.hunter(1); hunted(); /* tamper.hunt(a) */").size() == 1, "the rule's own check");
		List<String> found = new ArrayList<>();
		int declared = 0;
		try (Stream<Path> walk = Files.walk(sources)) {
			for (Path file : walk.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
				String rel = rel(sources, file);
				List<Integer> lines = huntCalls(Files.readString(file));
				if (HUNT_DECLARED.contains(rel)) {
					declared += lines.size();
				} else if (!rel.startsWith(HUNT_CALLERS)) {
					lines.forEach(line -> found.add(rel + ":" + line));
				}
			}
		}
		helper.assertTrue(declared >= 2, "MobTamper.hunt was not found where it is declared (" + declared + ")");
		helper.assertTrue(found.isEmpty(), "hunt is called outside accident.sc2 (D-096): " + found);
		helper.succeed();
	}

	// --- the rules, on text ---

	/** True if the file (a path under {@code src/main/java}) is in a client package: one of its packages is {@code client}. */
	static boolean isClientPackage(String rel) {
		String[] parts = rel.split("/");
		for (int i = 0; i < parts.length - 1; i++) {
			if (parts[i].equals("client")) {
				return true;
			}
		}
		return false;
	}

	/** Each client-only prefix the source's code (comments aside) names, once each. */
	static List<String> clientNamed(String source) {
		String code = code(source);
		Set<String> named = new LinkedHashSet<>();
		for (String prefix : CLIENT_ONLY) {
			if (code.contains(prefix)) {
				named.add(prefix);
			}
		}
		if (MOD_CLIENT.matcher(code).find()) {
			named.add(MOD_CLIENT_NAME);
		}
		return List.copyOf(named);
	}

	/** The lines of every call of a method named {@code hunt} in the source's code (comments and strings aside). */
	static List<Integer> huntCalls(String source) {
		String code = code(source);
		List<Integer> lines = new ArrayList<>();
		Matcher call = HUNT_CALL.matcher(code);
		while (call.find()) {
			lines.add((int) code.substring(0, call.start()).chars().filter(c -> c == '\n').count() + 1);
		}
		return lines;
	}

	/** The source with its comments blanked (line breaks kept) and its string and char literals emptied. */
	static String code(String source) {
		StringBuilder out = new StringBuilder(source.length());
		int i = 0;
		while (i < source.length()) {
			char c = source.charAt(i);
			char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
			if (c == '/' && next == '/') {
				while (i < source.length() && source.charAt(i) != '\n') {
					i++;
				}
			} else if (c == '/' && next == '*') {
				i += 2;
				while (i < source.length() && !(source.charAt(i) == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/')) {
					if (source.charAt(i) == '\n') {
						out.append('\n');
					}
					i++;
				}
				i += 2;
			} else if (c == '"' || c == '\'') {
				boolean block = c == '"' && source.startsWith("\"\"\"", i);
				String end = block ? "\"\"\"" : String.valueOf(c);
				i += block ? 3 : 1;
				out.append(c).append(c);
				while (i < source.length() && !source.startsWith(end, i)) {
					if (source.charAt(i) == '\\') {
						i++;
					} else if (source.charAt(i) == '\n') {
						out.append('\n');
					}
					i++;
				}
				i += end.length();
			} else {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	private static String rel(Path sources, Path file) {
		return sources.relativize(file).toString().replace('\\', '/');
	}

	private static Set<String> names(JsonObject object, String key) {
		Set<String> names = new LinkedHashSet<>();
		if (object != null && object.get(key) instanceof JsonArray array) {
			for (JsonElement element : array) {
				names.add(element.isJsonPrimitive() ? element.getAsString() : element.getAsJsonObject().get("value").getAsString());
			}
		}
		return names;
	}
}
