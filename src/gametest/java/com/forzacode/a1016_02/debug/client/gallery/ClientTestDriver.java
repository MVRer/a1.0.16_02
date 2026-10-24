package com.forzacode.a1016_02.debug.client.gallery;

import java.nio.file.Path;
import java.util.function.Function;

import com.forzacode.a1016_02.debug.gallery.GalleryRun;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * Drives {@link GalleryScript} from Fabric's client game test (the gallery's main path, {@code ./gradlew gallery}): the
 * test context's waits, its server and client calls, and its screenshots (each frame drawn at 1280 x 720 whatever the
 * window's size, without the counter prefix, straight into the exhibit's folder).
 */
public final class ClientTestDriver implements GalleryScript.Driver {
	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;

	public ClientTestDriver(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
	}

	@Override
	public <T> T server(Function<MinecraftServer, T> action) {
		return world.getServer().computeOnServer(action::apply);
	}

	@Override
	public <T> T client(Function<Minecraft, T> action) {
		return context.computeOnClient(action::apply);
	}

	@Override
	public void waitTicks(int ticks) {
		if (ticks > 0) {
			context.waitTicks(ticks);
		}
	}

	/** {@link GalleryScript#chunksReady} through the test context's wait (the test API's own chunk wait asks for a square). */
	@Override
	public boolean waitForChunks(int timeoutTicks) {
		try {
			context.waitFor(GalleryScript::chunksReady, timeoutTicks);
			return true;
		} catch (AssertionError e) {
			return false;
		}
	}

	@Override
	public Path screenshot(Path dir, String name) {
		return context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(dir).withSize(GalleryRun.WIDTH, GalleryRun.HEIGHT)
				.disableCounterPrefix());
	}
}
