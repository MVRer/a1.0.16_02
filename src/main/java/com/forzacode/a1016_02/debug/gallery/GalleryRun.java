package com.forzacode.a1016_02.debug.gallery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Gallery;
import com.forzacode.a1016_02.core.GalleryProps;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of a gallery run (docs/SELF_CREATED_2.md, "The client run"), shared by the client game test
 * ({@code ./gradlew gallery}), Fallback A ({@code ./gradlew runDevGallery}), Fallback B and {@code /a1016 sc g}: where the
 * PNGs go, where exhibit {@code i} stands, placing it, setting a view's sky and camera, and the index files. Dev only.
 */
public final class GalleryRun {
	/** The exhibits of a run ({@code 00,16} or {@code all}). */
	public static final String LIST_PROPERTY = "a1016_02.gallery";
	/** Where the PNGs go (the project's {@code build/gallery}, set by Gradle). */
	public static final String OUT_PROPERTY = "a1016_02.galleryOut";
	/** The run's render distance in chunks (default {@link #RENDER_DISTANCE}). */
	public static final String RENDER_DISTANCE_PROPERTY = "a1016_02.galleryRenderDistance";
	/** The plan's options: render distance 16, simulation distance 12, field of view 70, 1280 x 720. */
	public static final int RENDER_DISTANCE = 16;
	public static final int SIMULATION_DISTANCE = 12;
	public static final int FOV = 70;
	public static final int WIDTH = 1280;
	public static final int HEIGHT = 720;
	/** Ticks a view settles (fog and light) after its chunks render, before the shot. */
	public static final int SETTLE_TICKS = 40;
	/** Exhibits stand along +X, this far apart (exhibit {@code i} at x = {@code i * SPACING}, z = 0). */
	public static final int SPACING = 640;
	/** The seed every exhibit is placed with. */
	public static final long SEED = 1016L;
	/** The gallery world's stage: Traces, the earliest stage this wave's places and behaviours exist in. */
	public static final Stage STAGE = Stage.TRACES;

	private GalleryRun() {
	}

	/** The exhibit list the run was started with ({@code 00} when none was given). */
	public static String list() {
		String list = System.getProperty(LIST_PROPERTY, "00");
		return list.isBlank() ? "00" : list.trim();
	}

	/** The run's render distance (2 to 32 chunks). */
	public static int renderDistance() {
		return Math.clamp(Integer.getInteger(RENDER_DISTANCE_PROPERTY, RENDER_DISTANCE), 2, 32);
	}

	/**
	 * The gallery's folder: {@code -Da1016_02.galleryOut}, else {@code build/gallery} of the first folder up from the game
	 * folder that holds a {@code build.gradle}, else {@code gallery} in the game folder.
	 */
	public static Path outDir() {
		String out = System.getProperty(OUT_PROPERTY);
		if (out != null && !out.isBlank()) {
			return Paths.get(out).toAbsolutePath().normalize();
		}
		Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
		for (Path at = game; at != null; at = at.getParent()) {
			if (Files.isRegularFile(at.resolve("build.gradle"))) {
				return at.resolve("build").resolve("gallery");
			}
		}
		return game.resolve("gallery");
	}

	/** An exhibit's folder, {@code build/gallery/<id>}. */
	public static Path dir(String id) {
		return outDir().resolve(id);
	}

	/**
	 * Empties an exhibit's folder for a new run: its PNGs, frame folders and index go; anything else (a worker's
	 * {@code NOTES.txt}) stays.
	 */
	public static Path freshDir(String id) {
		Path dir = dir(id);
		try {
			Files.createDirectories(dir);
			try (Stream<Path> files = Files.list(dir)) {
				for (Path file : files.toList()) {
					String name = file.getFileName().toString();
					if (Files.isDirectory(file)) {
						deleteTree(file);
					} else if (name.endsWith(".png") || name.equals("index.txt")) {
						Files.delete(file);
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return dir;
	}

	private static void deleteTree(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(p);
			}
		}
	}

	/** Where exhibit {@code index} of a run stands: x = {@code index * SPACING}, z = 0, on the ground there. */
	public static BlockPos origin(ServerLevel level, int index) {
		int x = index * SPACING;
		return new BlockPos(x, Gallery.ground(level, x, 0), 0);
	}

	/** The gallery world as the plan sets it: the time and weather stand still, no natural spawns, normal difficulty, Traces. */
	public static void prepare(MinecraftServer server) {
		GameRules rules = server.getGameRules();
		rules.set(GameRules.ADVANCE_TIME, false, server);
		rules.set(GameRules.ADVANCE_WEATHER, false, server);
		rules.set(GameRules.SPAWN_MOBS, false, server);
		server.setDifficulty(Difficulty.NORMAL, true);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setGameMode(GameType.SPECTATOR);
		}
		HerobrineState state = HerobrineState.get(server);
		if (state.stage() != STAGE) {
			state.setStage(server, STAGE);
		}
		GalleryProps.clearSky(server, server.overworld());
	}

	/** Places an exhibit (timed, logged). Server thread. */
	public static Gallery.Placed place(ServerLevel level, Gallery.Exhibit exhibit, BlockPos origin) {
		long start = System.nanoTime();
		Gallery.Placed placed = exhibit.placer().place(level, origin, SEED);
		// Every camera's own chunk is loaded (generated if need be), so a view never starts in a chunk nobody made.
		for (Gallery.View view : placed.views()) {
			level.getChunk(Mth.floor(view.eye().x) >> 4, Mth.floor(view.eye().z) >> 4);
		}
		for (Gallery.Frames frames : placed.frames()) {
			level.getChunk(Mth.floor(frames.camera().eye().x) >> 4, Mth.floor(frames.camera().eye().z) >> 4);
		}
		long ms = (System.nanoTime() - start) / 1_000_000L;
		A1016_02.LOGGER.info("[a1016] gallery: {} placed at {} in {} ms ({} cameras, {} time-lapses)", exhibit.id(), origin.toShortString(), ms,
				placed.views().size(), placed.frames().size());
		return placed;
	}

	/**
	 * Sets a view: the sky and time, night vision for a survey view, and the camera (the player, a spectator) at the eye
	 * with the view's look. Server thread.
	 */
	public static void stage(ServerPlayer camera, Gallery.View view) {
		MinecraftServer server = camera.level().getServer();
		ServerLevel level = server.overworld();
		camera.setGameMode(GameType.SPECTATOR);
		GalleryProps.sky(server, level, view.sky(), view.dayTime());
		if (view.survey()) {
			camera.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false), null);
		} else {
			camera.removeEffect(MobEffects.NIGHT_VISION);
		}
		Vec3 eye = view.eye();
		camera.teleportTo(level, eye.x, eye.y - camera.getEyeHeight(), eye.z, Set.of(), view.yaw(), view.pitch(), true);
		camera.setYHeadRot(view.yaw());
	}

	/** The first player on the server (the camera of a run). */
	public static ServerPlayer camera(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			throw new IllegalStateException("no player to be the camera");
		}
		return players.getFirst();
	}

	/** Ends a run's staging: the walks stop, the sky clears, night vision goes. */
	public static void finish(MinecraftServer server) {
		GalleryProps.stopWalks();
		GalleryProps.clearSky(server, server.overworld());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	// --- the index ---

	/** One line of the index: a file of an exhibit and what it is. */
	public record Entry(String exhibit, String file, String what, long dayTime, Gallery.Sky sky, boolean survey, String check, String note) {
		String line() {
			return String.join("\t", file, exhibit, what, "time " + dayTime, "sky " + sky.name().toLowerCase(Locale.ROOT), survey ? "survey" : "true light",
					check, note);
		}
	}

	/** The index of a view's PNG. */
	public static Entry entry(String exhibit, String file, Gallery.View view, String check) {
		return new Entry(exhibit, file, "view " + view.name(), view.dayTime(), view.sky(), view.survey(), check, view.note());
	}

	/** The index of a frame of a time-lapse. */
	public static Entry entry(String exhibit, String file, Gallery.Frames frames, int frame, String check) {
		Gallery.View cam = frames.camera();
		return new Entry(exhibit, file, String.format(Locale.ROOT, "frames %s %d/%d every %d ticks", frames.name(), frame + 1, frames.count(),
				frames.everyTicks()), cam.dayTime(), cam.sky(), cam.survey(), check, frames.note());
	}

	/** The index of a blueprint render. */
	public static Entry blueprintEntry(String exhibit, String file, String box) {
		return new Entry(exhibit, file, "blueprint", Gallery.NOON, Gallery.Sky.CLEAR, false, "-", box);
	}

	/**
	 * Merges entries into {@code build/gallery/<id>/index.txt} of each of their exhibits and into
	 * {@code build/gallery/index.txt}: an earlier line stays while its file is still there and was not written again (so a
	 * full run, which emptied the exhibit's folder first, replaces it all, and the blueprints by hand add to it), in its
	 * order, the new lines after; the whole index grouped by exhibit. Tab-separated: file, exhibit, what, time, sky,
	 * light, the check of the PNG, the note.
	 */
	public static void writeIndex(List<Entry> entries, String header) {
		try {
			Path out = outDir();
			Files.createDirectories(out);
			List<String> exhibits = entries.stream().map(Entry::exhibit).distinct().toList();
			for (String id : exhibits) {
				List<String> lines = merged(dir(id).resolve("index.txt"), entries.stream().filter(e -> e.exhibit().equals(id)).toList());
				lines.addFirst("# " + id + ": " + header);
				Files.createDirectories(dir(id));
				Files.write(dir(id).resolve("index.txt"), lines, StandardCharsets.UTF_8);
			}
			Path index = out.resolve("index.txt");
			List<String> lines = merged(index, entries);
			lines.sort(Comparator.comparing((String l) -> l.split("\t", -1)[1]));
			lines.addFirst("# the gallery (docs/SELF_CREATED_2.md): file, exhibit, what, time, sky, light, check, note; last run: " + header);
			Files.write(index, lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The index's old lines whose file is still there and not among the new entries, then the new entries. */
	private static List<String> merged(Path index, List<Entry> entries) throws IOException {
		Set<String> written = new java.util.HashSet<>(entries.stream().map(Entry::file).toList());
		List<String> lines = new ArrayList<>();
		if (Files.isRegularFile(index)) {
			for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
				String[] cols = line.split("\t", -1);
				if (line.startsWith("#") || cols.length < 2 || written.contains(cols[0]) || !Files.isRegularFile(outDir().resolve(cols[0]))) {
					continue;
				}
				lines.add(line);
			}
		}
		entries.forEach(e -> lines.add(e.line()));
		return lines;
	}

	/** A file's path as the index names it: relative to {@code build/gallery}. */
	public static String relative(Path file) {
		return outDir().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
	}

	/**
	 * A quick check of a screenshot, for the index: its mean brightness (0 to 1), the darkest and brightest pixel and
	 * the share of near-black pixels, flagged {@code BLACK?} when nearly all of it is black and {@code FLAT?} when it is
	 * one colour (a camera inside a block, or nothing drawn).
	 */
	public static String check(Path png) {
		try {
			Png.Image image = Png.read(Files.readAllBytes(png));
			int[] p = image.argb();
			double sum = 0;
			double min = 1;
			double max = 0;
			int black = 0;
			for (int c : p) {
				double luma = (0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF)) / 255.0;
				sum += luma;
				min = Math.min(min, luma);
				max = Math.max(max, luma);
				if (luma < 0.03) {
					black++;
				}
			}
			double mean = sum / p.length;
			double blackShare = (double) black / p.length;
			String flag = blackShare > 0.97 ? " BLACK?" : max - min < 0.04 ? " FLAT?" : "";
			return String.format(Locale.ROOT, "%dx%d luma %.2f (%.2f-%.2f) black %.0f%%%s", image.width(), image.height(), mean, min, max, blackShare * 100,
					flag);
		} catch (IOException | RuntimeException e) {
			return "unread (" + e.getClass().getSimpleName() + ")";
		}
	}
}
