package com.forzacode.a1016_02.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.forzacode.a1016_02.accident.sc.Sc15Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc04Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc05Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc06Config;
import com.forzacode.a1016_02.atmosphere.sc.Sc07Config;
import com.forzacode.a1016_02.dig.sc.Sc12Config;
import com.forzacode.a1016_02.dig.sc.Sc13Config;
import com.forzacode.a1016_02.director.sc.Sc01Config;
import com.forzacode.a1016_02.entity.sc.HisViewConfig;
import com.forzacode.a1016_02.entity.sc.NamelessConfig;
import com.forzacode.a1016_02.lore.sc.RecordConfig;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc.Tracks;
import com.forzacode.a1016_02.world.sc.Turned;
import com.forzacode.a1016_02.world.sc.WhereHeStood;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * SC-7, the verifier: the spoiler rule D-080 for the config file. D-080: "Config: one section per feature,
 * {@code sections.scNN} ... Field names are numbers and plain rules ({@code minDistance}, {@code chance},
 * {@code maxPerWorld}), never the thing ({@code bed}, {@code moon}, {@code name})"; Mariano "reads /a1016 output, the
 * config and the log". Every section is written to {@code config/a1016_02.json} with its defaults the first time it is
 * read (the dev client's fresh run wrote them all). This holds the words of each section's field names (camelCase
 * words, a plural counts) to the layer's own list of words that name a feature ({@code SelfCreatedDebugTests.NAMES},
 * which the index and the overlay already pass), the schema's {@code version} aside. FAILS on {@code self-created} at dd4680f: for example {@code sc12.sharedBedRadius},
 * {@code sc03.iconSize}, {@code sc14.seedFromStage} and {@code versionFromStage}, {@code sc05.houseRadius}.
 */
public class VerifyScSpoilerTests {
	/** {@code SelfCreatedDebugTests.NAMES} (D-080), word for word. */
	static final List<String> NAMES = List.of("moon", "night", "name", "bed", "sleep", "rest", "torch", "step", "foot", "print", "track", "fog", "icon",
			"picture", "view", "seed", "version", "house", "home", "door", "chest", "pet", "animal", "friend", "cave", "lava", "shaft", "stat", "mined",
			"dropped", "turn", "trail", "brb", "record", "hole", "block", "wall", "way back", "stood", "stand");

	@GameTest
	public void noConfigFieldNamesTheThing(GameTestHelper helper) {
		Map<String, Class<?>> sections = new LinkedHashMap<>();
		sections.put("sc", SelfCreated.Config.class);
		sections.put("sc01", Sc01Config.class);
		sections.put("sc02", NamelessConfig.class);
		sections.put("sc03", HisViewConfig.class);
		sections.put("sc04", Sc04Config.class);
		sections.put("sc05", Sc05Config.class);
		sections.put("sc06", Sc06Config.class);
		sections.put("sc07", Sc07Config.class);
		sections.put("sc08", Turned.Config.class);
		sections.put("sc09", Tracks.Config.class);
		sections.put("sc10", WhereHeStood.Config.class);
		sections.put("sc11", TorchTrail.Config.class);
		sections.put("sc12", Sc12Config.class);
		sections.put("sc13", Sc13Config.class);
		sections.put("sc14", RecordConfig.class);
		sections.put("sc15", Sc15Config.class);
		List<String> named = new ArrayList<>();
		for (Map.Entry<String, Class<?>> section : sections.entrySet()) {
			for (Field field : section.getValue().getFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("version")) {
					continue;
				}
				String word = namedWord(field.getName());
				if (word != null) {
					named.add(section.getKey() + "." + field.getName() + " ('" + word + "')");
				}
			}
		}
		helper.assertTrue(named.isEmpty(), named.size() + " config fields name the thing (D-080): " + named);
		helper.succeed();
	}

	/**
	 * The SC-7 row's spoiler grep, on the command literals: every literal under {@code /a1016 sc} (what tab completion and
	 * {@code /help a1016} show) held to the same list. FAILS on {@code self-created} at dd4680f: {@code sc 04 tonight}
	 * ("night"), {@code sc 12 arm noshaft} ("shaft"), {@code sc 14 arm version} and {@code sc 14 seed}.
	 */
	@GameTest
	public void noCommandLiteralUnderScNamesAFeature(GameTestHelper helper) {
		com.mojang.brigadier.tree.CommandNode<net.minecraft.commands.CommandSourceStack> root = helper.getLevel().getServer().getCommands().getDispatcher()
				.getRoot().getChild("a1016");
		helper.assertTrue(root != null && root.getChild("sc") != null, "no /a1016 sc");
		List<String> named = new ArrayList<>();
		walk(root.getChild("sc"), "sc", named);
		helper.assertTrue(named.isEmpty(), named.size() + " literals under /a1016 sc name a feature (D-080): " + named);
		helper.succeed();
	}

	/** The first word of {@link #NAMES} that is one of this camelCase name's words (or its plural), or null. */
	static String namedWord(String camel) {
		for (String token : camel.split("(?<=[a-z0-9])(?=[A-Z])")) {
			String t = token.toLowerCase(Locale.ROOT);
			for (String word : NAMES) {
				if (t.equals(word) || t.equals(word + "s") || t.equals(word + "es")) {
					return word;
				}
			}
		}
		return null;
	}

	private static void walk(com.mojang.brigadier.tree.CommandNode<net.minecraft.commands.CommandSourceStack> node, String path, List<String> named) {
		for (com.mojang.brigadier.tree.CommandNode<net.minecraft.commands.CommandSourceStack> child : node.getChildren()) {
			String at = path + " " + child.getName();
			if (child instanceof com.mojang.brigadier.tree.LiteralCommandNode<?>) {
				String lower = child.getName().toLowerCase(Locale.ROOT);
				for (String word : NAMES) {
					if (lower.contains(word.replace(" ", ""))) {
						named.add("/a1016 " + at + " ('" + word + "')");
						break;
					}
				}
			}
			walk(child, at, named);
		}
	}
}
