package com.forzacode.a1016_02.debug.client.gallery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.debug.gallery.Blueprint;
import com.forzacode.a1016_02.debug.gallery.GalleryRun;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The gallery's client run (docs/SELF_CREATED_2.md, "The client run"), one script for both drivers: the client game test
 * ({@code ./gradlew gallery}, {@code GalleryClientTest}) and Fallback A ({@code ./gradlew runDevGallery},
 * {@link GalleryAutopilot}). In an open singleplayer world: the plan's options, the world prepared, then for each exhibit
 * of the list: placed at {@code (i * 640, ground, 0)}; every view shot (sky and time, camera, chunks rendered, 40 ticks to
 * settle) to {@code build/gallery/<id>/<view>.png}; every time-lapse started and shot every {@code everyTicks} to
 * {@code build/gallery/<id>/<frames>/f00.png} ...; the blueprint renders; the index. Dev only.
 */
public final class GalleryScript {
	/** How long a view may wait for its chunks to render (ticks); a view that times out is still shot, and flagged. */
	public static final int CHUNK_TIMEOUT = 600;

	/** What the script needs from whoever drives the client. Every call from the driver's own (non-render) thread. */
	public interface Driver {
		/** Runs on the server thread and returns its answer. */
		<T> T server(Function<MinecraftServer, T> action);

		/** Runs on the client thread and returns its answer. */
		<T> T client(Function<Minecraft, T> action);

		/** Lets the game run this many ticks. */
		void waitTicks(int ticks);

		/** Waits until the chunks around the camera are downloaded and rendered; false if {@code timeoutTicks} ran out. */
		boolean waitForChunks(int timeoutTicks);

		/** A screenshot of the next frame at {@link GalleryRun#WIDTH} x {@link GalleryRun#HEIGHT} where it can, to {@code dir/name.png}. */
		Path screenshot(Path dir, String name);
	}

	private GalleryScript() {
	}

	/**
	 * Whether the camera's surroundings are ready for a shot: every chunk within the render distance less one (the rim
	 * comes last and sits in the fog) by vanilla's own round rule ({@link ChunkTrackingView#isInViewDistance}, what the
	 * server sends; the test API's {@code waitForChunksRender} asks for the whole square, whose corners never come, so it
	 * waits out its timeout at any render distance above a few chunks) is in the client's level, and every section is
	 * built. Client thread.
	 */
	public static boolean chunksReady(Minecraft client) {
		if (client.level == null || client.player == null || !client.levelRenderer.hasRenderedAllSections()) {
			return false;
		}
		int distance = client.options.getEffectiveRenderDistance() - 1;
		ChunkPos center = client.player.chunkPosition();
		ClientChunkCache chunks = client.level.getChunkSource();
		for (int dx = -distance; dx <= distance; dx++) {
			for (int dz = -distance; dz <= distance; dz++) {
				int x = center.x() + dx;
				int z = center.z() + dz;
				if (ChunkTrackingView.isInViewDistance(center.x(), center.z(), distance, x, z) && chunks.getChunk(x, z, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}
		return true;
	}

	/** Why {@link #chunksReady} says no, for the log: the sections, the chunks still missing and the render distance. */
	public static String chunksReport(Minecraft client) {
		if (client.level == null || client.player == null) {
			return "no level";
		}
		int distance = client.options.getEffectiveRenderDistance() - 1;
		ChunkPos center = client.player.chunkPosition();
		ClientChunkCache chunks = client.level.getChunkSource();
		int wanted = 0;
		int missing = 0;
		for (int dx = -distance; dx <= distance; dx++) {
			for (int dz = -distance; dz <= distance; dz++) {
				int x = center.x() + dx;
				int z = center.z() + dz;
				if (ChunkTrackingView.isInViewDistance(center.x(), center.z(), distance, x, z)) {
					wanted++;
					if (chunks.getChunk(x, z, ChunkStatus.FULL, false) == null) {
						missing++;
					}
				}
			}
		}
		return String.format(Locale.ROOT, "sections built %s, %d of %d chunks missing, render distance %d around chunk %d %d, %d loaded",
				client.levelRenderer.hasRenderedAllSections(), missing, wanted, distance, center.x(), center.z(), chunks.getLoadedChunksCount());
	}

	/** The plan's options: render and simulation distance, field of view 70, the hud hidden, gamma 0.5, no clouds, no bobbing. */
	public static Void options(Minecraft client, int renderDistance) {
		Options options = client.options;
		options.renderDistance().set(renderDistance);
		options.simulationDistance().set(GalleryRun.SIMULATION_DISTANCE);
		options.fov().set(GalleryRun.FOV);
		options.gamma().set(0.5);
		options.cloudStatus().set(CloudStatus.OFF);
		options.bobView().set(false);
		if (!client.gui.hud.isHidden()) {
			client.gui.hud.toggle();
		}
		// The server sends chunks out to the view distance the client asked for, which only changes when told.
		options.broadcastOptions();
		return null;
	}

	/** Runs the gallery for the exhibits of {@code list} ({@code 00,16} or {@code all}). Returns the PNGs written. */
	public static List<Path> run(Driver driver, String list, int renderDistance) {
		long start = System.nanoTime();
		driver.client(client -> options(client, renderDistance));
		driver.server(server -> {
			GalleryRun.prepare(server);
			return null;
		});
		List<String> unknown = new ArrayList<>();
		List<Gallery.Exhibit> exhibits = Gallery.named(list, unknown);
		if (!unknown.isEmpty()) {
			A1016_02.LOGGER.warn("[a1016] gallery: no exhibit {} (known: {})", unknown, Gallery.all().stream().map(Gallery.Exhibit::id).toList());
		}
		String header = String.format(Locale.ROOT, "%s, render distance %d, %dx%d, fov %d", list, renderDistance, GalleryRun.WIDTH, GalleryRun.HEIGHT,
				GalleryRun.FOV);
		List<Path> written = new ArrayList<>();
		for (int i = 0; i < exhibits.size(); i++) {
			written.addAll(exhibit(driver, exhibits.get(i), i, header));
		}
		driver.server(server -> {
			GalleryRun.finish(server);
			return null;
		});
		A1016_02.LOGGER.info("[a1016] gallery: {} exhibits, {} files in {} s, in {}", exhibits.size(), written.size(), (System.nanoTime() - start) / 1_000_000_000L,
				GalleryRun.outDir());
		return written;
	}

	private static List<Path> exhibit(Driver driver, Gallery.Exhibit exhibit, int index, String header) {
		Path dir = GalleryRun.freshDir(exhibit.id());
		Gallery.Placed placed = driver.server(server -> GalleryRun.place(server.overworld(), exhibit, GalleryRun.origin(server.overworld(), index)));
		List<GalleryRun.Entry> entries = new ArrayList<>();
		List<Path> written = new ArrayList<>();
		for (Gallery.View view : placed.views()) {
			long started = System.nanoTime();
			String loaded = stage(driver, view);
			Path png = driver.screenshot(dir, view.name());
			written.add(png);
			String check = GalleryRun.check(png) + loaded;
			entries.add(GalleryRun.entry(exhibit.id(), GalleryRun.relative(png), view, check));
			A1016_02.LOGGER.info("[a1016] gallery: {} {} in {} ms: {}", exhibit.id(), view.name(), (System.nanoTime() - started) / 1_000_000L, check);
		}
		for (Gallery.Frames frames : placed.frames()) {
			String loaded = stage(driver, frames.camera());
			driver.server(server -> {
				frames.start().accept(server.overworld());
				return null;
			});
			Path folder = dir.resolve(frames.name());
			for (int k = 0; k < frames.count(); k++) {
				if (k > 0) {
					driver.waitTicks(frames.everyTicks());
				}
				Path png = driver.screenshot(folder, String.format(Locale.ROOT, "f%02d", k));
				written.add(png);
				entries.add(GalleryRun.entry(exhibit.id(), GalleryRun.relative(png), frames, k, GalleryRun.check(png) + (k == 0 ? loaded : "")));
			}
		}
		List<Path> plans = driver.server(server -> {
			try {
				return Blueprint.write(server.overworld(), placed.box(), dir);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		String box = Blueprint.describe(placed.box());
		for (Path plan : plans) {
			written.add(plan);
			entries.add(GalleryRun.blueprintEntry(exhibit.id(), GalleryRun.relative(plan), box));
		}
		GalleryRun.writeIndex(entries, header);
		A1016_02.LOGGER.info("[a1016] gallery: {} done, {} files in {}", exhibit.id(), written.size(), dir);
		return written;
	}

	/** Sets the camera and waits for it to settle; "" or a flag for the index when the chunks did not finish. */
	private static String stage(Driver driver, Gallery.View view) {
		driver.server(server -> {
			GalleryRun.stage(GalleryRun.camera(server), view);
			return null;
		});
		driver.waitTicks(2);
		boolean loaded = driver.waitForChunks(CHUNK_TIMEOUT);
		driver.waitTicks(GalleryRun.SETTLE_TICKS);
		loaded &= driver.waitForChunks(200);
		if (!loaded) {
			A1016_02.LOGGER.warn("[a1016] gallery: {} shot before its chunks were ready: {}", view.name(), driver.client(GalleryScript::chunksReport));
		}
		return loaded ? "" : " CHUNKS?";
	}
}
