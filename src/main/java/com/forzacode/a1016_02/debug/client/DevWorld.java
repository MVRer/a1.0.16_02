package com.forzacode.a1016_02.debug.client;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.debug.client.gallery.GalleryAutopilot;
import com.forzacode.a1016_02.debug.client.gallery.GalleryWorlds;
import com.forzacode.a1016_02.debug.gallery.GalleryRun;

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
 *
 * <p>The gallery's Fallback A ({@code ./gradlew runDevGallery}, docs/SELF_CREATED_2.md SC2-0c): with
 * {@code -Da1016_02.devFlat=deep} a new world is the gallery's deep flat ({@link GalleryWorlds}, no structures) instead,
 * and with {@code -Da1016_02.gallery=<ids>} the {@link GalleryAutopilot} takes the gallery's screenshots once the world
 * is open, then quits.
 */
final class DevWorld {
	static final String WORLD_PROPERTY = "a1016_02.devWorld";
	static final String SEED_PROPERTY = "a1016_02.devSeed";
	static final String FLAT_PROPERTY = "a1016_02.devFlat";

	private static boolean started;

	private DevWorld() {
	}

	static void init() {
		String folder = System.getProperty(WORLD_PROPERTY);
		if (!FabricLoader.getInstance().isDevelopmentEnvironment() || folder == null || folder.isBlank()) {
			return;
		}
		if (System.getProperty(GalleryRun.LIST_PROPERTY) != null) {
			GalleryAutopilot.init();
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
		boolean deep = "deep".equals(System.getProperty(FLAT_PROPERTY));
		A1016_02.LOGGER.info("[a1016] dev: creating world '{}' with seed {}{}", folder, seed, deep ? " (deep flat)" : "");
		LevelSettings settings = new LevelSettings(folder, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
		if (deep) {
			client.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(seed, false, false), GalleryWorlds::dimensions,
					new TitleScreen());
		} else {
			client.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(seed, true, false), WorldPresets::createNormalWorldDimensions,
					new TitleScreen());
		}
	}

	private static long parseSeed(String value) {
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return value.hashCode();
		}
	}
}
