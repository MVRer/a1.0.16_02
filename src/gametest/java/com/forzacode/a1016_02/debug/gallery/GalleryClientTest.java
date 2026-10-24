package com.forzacode.a1016_02.debug.gallery;

import com.forzacode.a1016_02.debug.client.gallery.ClientTestDriver;
import com.forzacode.a1016_02.debug.client.gallery.GalleryScript;
import com.forzacode.a1016_02.debug.client.gallery.GalleryWorlds;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The gallery's client run (docs/SELF_CREATED_2.md, "The client run"; {@code ./gradlew gallery -Pgallery=<ids>}, never
 * part of {@code build}): a fresh deep flat world ({@link GalleryWorlds}), then {@link GalleryScript} for the exhibits of
 * {@code -Da1016_02.gallery} ({@code 00} by default, {@code all} for every one) into {@code build/gallery}. The world is
 * closed at the end, so the game returns to the title screen and quits.
 */
public class GalleryClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(GalleryWorlds::adjust).create()) {
			ClientTestDriver driver = new ClientTestDriver(context, world);
			driver.waitForChunks(GalleryScript.CHUNK_TIMEOUT);
			GalleryScript.run(driver, GalleryRun.list(), GalleryRun.renderDistance());
		}
	}
}
