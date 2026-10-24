package com.forzacode.a1016_02.debug.client.gallery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.debug.gallery.GalleryRun;
import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;

/**
 * Fallback A of the gallery (docs/SELF_CREATED_2.md, "The client run"; {@code ./gradlew runDevGallery -Pgallery=<ids>}):
 * the dev client opens a fresh deep flat world ({@code DevWorld}), then this runs {@link GalleryScript} on a thread of
 * its own, driving the game through the client's and the integrated server's task queues and counting client ticks;
 * screenshots are vanilla's grab of the main frame, at the window's own size. Then the game quits. Dev only: installed
 * by {@code DevWorld} only when {@code -Da1016_02.gallery} is set.
 */
public final class GalleryAutopilot implements GalleryScript.Driver {
	private static final AtomicLong TICKS = new AtomicLong();
	private static boolean started;

	private final Minecraft client;

	private GalleryAutopilot(Minecraft client) {
		this.client = client;
	}

	/** Starts the script once the world is open and the player is in it. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			TICKS.incrementAndGet();
			if (!started && client.level != null && client.player != null && client.getSingleplayerServer() != null && client.gui.screen() == null) {
				started = true;
				Thread thread = new Thread(() -> run(client), "a1016 gallery");
				thread.setDaemon(true);
				thread.start();
			}
		});
	}

	private static void run(Minecraft client) {
		try {
			GalleryAutopilot driver = new GalleryAutopilot(client);
			driver.waitTicks(40);
			GalleryScript.run(driver, GalleryRun.list(), GalleryRun.renderDistance());
		} catch (Throwable e) {
			A1016_02.LOGGER.error("[a1016] gallery: the dev run failed", e);
		} finally {
			client.execute(client::stop);
		}
	}

	@Override
	public <T> T server(Function<MinecraftServer, T> action) {
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null) {
			throw new IllegalStateException("no integrated server");
		}
		return server.submit(() -> action.apply(server)).join();
	}

	@Override
	public <T> T client(Function<Minecraft, T> action) {
		return client.submit(() -> action.apply(client)).join();
	}

	@Override
	public void waitTicks(int ticks) {
		long until = TICKS.get() + ticks;
		while (TICKS.get() < until) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}
	}

	/** {@link GalleryScript#chunksReady}, checked once a tick. */
	@Override
	public boolean waitForChunks(int timeoutTicks) {
		long until = TICKS.get() + timeoutTicks;
		while (TICKS.get() < until) {
			if (client(GalleryScript::chunksReady)) {
				return true;
			}
			waitTicks(1);
		}
		return client(GalleryScript::chunksReady);
	}

	@Override
	public Path screenshot(Path dir, String name) {
		waitTicks(1);
		CompletableFuture<Path> done = new CompletableFuture<>();
		client.execute(() -> Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
			try (NativeImage shot = image) {
				Files.createDirectories(dir);
				Path file = dir.resolve(name + ".png");
				shot.writeToFile(file);
				done.complete(file);
			} catch (IOException | RuntimeException e) {
				done.completeExceptionally(e);
			}
		}));
		return done.join();
	}
}
