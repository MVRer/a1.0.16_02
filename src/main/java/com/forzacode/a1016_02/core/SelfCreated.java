package com.forzacode.a1016_02.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.forzacode.a1016_02.A1016_02;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The one switch for the layer on this branch (contracts and features in {@code docs/SELF_CREATED.md}), and the
 * rules every part of it keeps. Config: {@code sections.sc.enabled} (default true). Every part of the layer asks
 * {@link #enabled()} before it does anything, on either side (the client reads its own config; in singleplayer the
 * same file), and does nothing while it is false.
 *
 * <p>The rules (ARCHITECTURE, "Self-created layer"):
 * <ol>
 *   <li>Nothing writes outside the world's own folder: files go through {@link #writeInWorld}, which refuses any path
 *   that leaves it.</li>
 *   <li>Nothing can corrupt a save or stop it loading, with this mod (this version or v1.3) or in vanilla: new saved
 *   fields are optional and read leniently, new kinds of data are stored where an older reader skips them, a file is
 *   written whole or not at all ({@link #writeInWorld}), and nothing is ever deleted.</li>
 *   <li>Every change to the world's metadata or to what the UI shows is reversible, and is reverted when the layer is
 *   switched off: each part registers its revert with {@link #onSwitchedOff}, run at every server start while the
 *   switch is off and at once when {@link #setEnabled} turns it off. A revert must be idempotent and cheap. What the
 *   client shows without a server checks {@link #enabled()} there.</li>
 *   <li>Anything shown in vanilla UI uses vanilla's own strings only.</li>
 * </ol>
 */
public final class SelfCreated {
	/** The config section: {@code sections.sc}. */
	public static final String SECTION = "sc";

	/** {@code sections.sc}. Version 1. */
	public static final class Config implements ModConfig.Versioned {
		public static final int CURRENT = 1;

		public int version = 0;
		/** The whole layer: false turns every part off and reverts what can be seen of it. */
		public boolean enabled = true;

		@Override
		public boolean upgrade() {
			if (version >= CURRENT) {
				return false;
			}
			version = CURRENT;
			return true;
		}
	}

	private static final Map<String, Consumer<MinecraftServer>> REVERTS = new LinkedHashMap<>();

	private SelfCreated() {
	}

	/** The section as it is now (read once from the file, then live edits). Any thread. */
	public static Config config() {
		return ModConfig.section(SECTION, Config.class, Config::new);
	}

	/** Whether the layer is on. Any thread, cheap enough for every frame. */
	public static boolean enabled() {
		return config().enabled;
	}

	/**
	 * Switches the layer on or off for good (writes {@code sections.sc}); turning it off runs every registered revert
	 * now ({@link #onSwitchedOff}). Server thread.
	 */
	public static void setEnabled(MinecraftServer server, boolean enabled) {
		Config config = config();
		boolean was = config.enabled;
		Config changed = new Config();
		changed.version = config.version;
		changed.enabled = enabled;
		ModConfig.saveSection(SECTION, changed);
		if (was && !enabled) {
			runReverts(server);
		}
	}

	/**
	 * Registers what puts one part's visible changes back (its id, for example {@code "sc03"}, names it in the log): run
	 * at every server start while the layer is off, and when {@link #setEnabled} turns it off. A later registration with
	 * the same id replaces the earlier one. Call it in init. It must be idempotent, cheap and never load chunks.
	 */
	public static synchronized void onSwitchedOff(String id, Consumer<MinecraftServer> revert) {
		REVERTS.put(Objects.requireNonNull(id), Objects.requireNonNull(revert));
	}

	/** Core: at server start, every revert if the layer is off. */
	static void serverStarted(MinecraftServer server) {
		if (!enabled()) {
			runReverts(server);
		}
	}

	/** Runs every registered revert once ({@link #run}). */
	static List<String> runReverts(MinecraftServer server) {
		return run(server, registered());
	}

	/** The registered reverts, in registration order (a copy). */
	static synchronized List<Map.Entry<String, Consumer<MinecraftServer>>> registered() {
		return List.copyOf(REVERTS.entrySet());
	}

	/**
	 * Runs these reverts once each, in order; one that throws is logged and the rest still run.
	 *
	 * @return the ids of the reverts that ran without throwing
	 */
	static List<String> run(MinecraftServer server, List<Map.Entry<String, Consumer<MinecraftServer>>> reverts) {
		List<String> done = new ArrayList<>();
		for (Map.Entry<String, Consumer<MinecraftServer>> revert : reverts) {
			try {
				revert.getValue().accept(server);
				done.add(revert.getKey());
			} catch (RuntimeException e) {
				ScLog.error("[a1016] sc off: reverting {} failed", revert.getKey(), e);
			}
		}
		A1016_02.LOGGER.info("[a1016] sc off: reverted {}", done);
		return done;
	}

	/** The world's own folder (the integrated or dedicated server's level directory). */
	public static Path worldFolder(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
	}

	/**
	 * {@code relative} inside {@code worldFolder}, or empty if it is blank, absolute, or leaves the folder (through
	 * {@code ..}) or names the folder itself. The one way the layer names a file.
	 */
	public static Optional<Path> inWorld(Path worldFolder, String relative) {
		if (worldFolder == null || relative == null || relative.isBlank()) {
			return Optional.empty();
		}
		try {
			Path root = worldFolder.toAbsolutePath().normalize();
			Path rel = Path.of(relative);
			if (rel.isAbsolute() || rel.getRoot() != null) {
				return Optional.empty();
			}
			Path target = root.resolve(rel).normalize();
			return target.startsWith(root) && !target.equals(root) ? Optional.of(target) : Optional.empty();
		} catch (InvalidPathException e) {
			return Optional.empty();
		}
	}

	/**
	 * Writes {@code bytes} to {@code relative} inside the world's folder, whole or not at all: into a temporary file
	 * beside it, then moved over it (atomically where the file system can). Refused (false, nothing written, no folder
	 * made) for a path {@link #inWorld} refuses or whose folder resolves outside the world's folder through a link: the
	 * folders are made one at a time, each only once the one it goes in is known to be inside. Never throws, never
	 * deletes anything but its own temporary file. Any thread; the caller keeps two writers of one file apart.
	 */
	public static boolean writeInWorld(Path worldFolder, String relative, byte[] bytes) {
		Optional<Path> target = inWorld(worldFolder, relative);
		if (target.isEmpty() || bytes == null) {
			A1016_02.LOGGER.warn("[a1016] sc: refused to write '{}' outside the world's folder", relative);
			return false;
		}
		Path file = target.get();
		Path temp = null;
		try {
			Path parent = file.getParent();
			if (!foldersInWorld(worldFolder.toAbsolutePath().normalize(), parent)) {
				A1016_02.LOGGER.warn("[a1016] sc: refused to write '{}': its folder leaves the world's folder", relative);
				return false;
			}
			temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
			Files.write(temp, bytes);
			try {
				Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			temp = null;
			return true;
		} catch (IOException | RuntimeException e) {
			ScLog.warn("[a1016] sc: could not write '{}'", relative, e);
			return false;
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// Only our own temporary file; nothing else is touched.
				}
			}
		}
	}

	/**
	 * Walks from the world's folder ({@code root}, absolute and normal; made first if it is not there yet) down to
	 * {@code folder} (inside it), making each missing folder only after the one it goes in resolved inside the world's
	 * folder, so a link never gets a folder made outside. False, with nothing more made, at the first that resolves
	 * outside (or is not a folder).
	 */
	private static boolean foldersInWorld(Path root, Path folder) throws IOException {
		if (Files.notExists(root)) {
			Files.createDirectories(root);
		}
		Path realRoot = root.toRealPath();
		Path at = root;
		for (Path part : root.relativize(folder)) {
			at = at.resolve(part);
			if (Files.notExists(at, LinkOption.NOFOLLOW_LINKS)) {
				Files.createDirectory(at);
			}
			Path real = at.toRealPath();
			if (!real.startsWith(realRoot) || !Files.isDirectory(real)) {
				return false;
			}
		}
		return true;
	}
}
