package com.forzacode.a1016_02.debug.client;

import com.forzacode.a1016_02.A1016_02;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Dev only. With {@code -Da1016_02.devWorld=<folder>} (set by {@code ./gradlew runDevClient}) the client skips
 * the title screen and opens that singleplayer world. If it does not exist yet it is created first: creative,
 * cheats on, seed {@code -Da1016_02.devSeed} (spawn in a dappled forest, cold ocean and frozen peaks about 100
 * blocks away). Delete {@code run/saves/<folder>} to start over.
 */
final class DevWorld {
	static final String WORLD_PROPERTY = "a1016_02.devWorld";
	static final String SEED_PROPERTY = "a1016_02.devSeed";

	private static boolean started;

	private DevWorld() {
	}

	static void init() {
		String folder = System.getProperty(WORLD_PROPERTY);
		if (!FabricLoader.getInstance().isDevelopmentEnvironment() || folder == null || folder.isBlank()) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Screen screen = client.gui.screen();
			if (!started && (screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) {
				started = true;
				open(client, folder);
			}
		});
	}

	private static void open(Minecraft client, String folder) {
		if (client.getLevelSource().levelExists(folder)) {
			A1016_02.LOGGER.info("[a1016] dev: opening world '{}'", folder);
			client.createWorldOpenFlows().openWorld(folder, () -> client.gui.setScreen(new TitleScreen()));
			return;
		}
		long seed = parseSeed(System.getProperty(SEED_PROPERTY, "0"));
		A1016_02.LOGGER.info("[a1016] dev: creating world '{}' with seed {}", folder, seed);
		LevelSettings settings = new LevelSettings(folder, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
		client.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(seed, true, false),
				WorldPresets::createNormalWorldDimensions, new TitleScreen());
	}

	private static long parseSeed(String value) {
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return value.hashCode();
		}
	}
}
