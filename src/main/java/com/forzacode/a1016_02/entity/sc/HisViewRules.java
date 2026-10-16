package com.forzacode.a1016_02.entity.sc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.entity.Variant;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 03's pure rules, free of client classes so the game tests reach them: when a sighting arms the host's client, the
 * camera from his eyes toward the host's, the crop vanilla's world icon makes, and the PNG the picture is written as.
 */
public final class HisViewRules {
	/** The world's own picture (vanilla's {@code LevelResource.ICON_FILE}), relative to the world's folder. */
	public static final String ICON = "icon.png";
	/** Where the picture from before is kept (once), relative to the world's folder. */
	public static final String BEFORE = "data/a1016_02/sc03_before.png";
	/**
	 * The world's own marker, relative to its folder: written "1" right before the picture replaces {@link #ICON} and "0"
	 * once the picture from before is back, so the picture is put back even when the server never heard it was taken.
	 */
	public static final String TAKEN = "data/a1016_02/sc03_taken";
	/** Night for the arm, in-game time of day: from... */
	public static final long NIGHT_FROM = 13000L;
	/** ...to (exclusive). */
	public static final long NIGHT_UNTIL = 23000L;

	private HisViewRules() {
	}

	/**
	 * Why this sighting does not arm the host's client, or empty if it does. An integrated server (singleplayer or a LAN
	 * host) only, for the host only, once per world; a card variant but the glimpse, the last one and the Nether and End
	 * ones, in the overworld; at night (13000 to 23000) or in a counting fog spell; from {@code fromStage}; the figure
	 * within {@code maxBaseDistance} of the host's base and the host within {@code hostBaseDistance} of it (both
	 * horizontal). {@code forced} (debug) skips the stage and the two distances.
	 *
	 * @param base the host's base in the overworld, or null if they have none there
	 */
	public static Optional<String> armRefusal(boolean integrated, boolean host, boolean done, Stage stage, Variant variant, boolean overworld,
			@Nullable BlockPos base, Vec3 figure, Vec3 hostFeet, long timeOfDay, boolean fogCounts, HisViewConfig config, boolean forced) {
		if (!integrated) {
			return Optional.of("not an integrated server");
		}
		if (!host) {
			return Optional.of("not the host");
		}
		if (done) {
			return Optional.of("already done in this world");
		}
		if (!variantCounts(variant)) {
			return Optional.of("not for the " + variant.shortName());
		}
		if (!overworld) {
			return Optional.of("not the overworld");
		}
		if (!forced && stage.level() < config.fromStage) {
			return Optional.of("before stage " + config.fromStage + " (" + stage + ")");
		}
		if (!night(timeOfDay) && !fogCounts) {
			return Optional.of("not night and no fog (time " + timeOfDay + ")");
		}
		if (forced) {
			return Optional.empty();
		}
		if (base == null) {
			return Optional.of("the host has no base in the overworld");
		}
		Vec3 b = Vec3.atBottomCenterOf(base);
		double figureOff = horizontal(figure, b);
		if (figureOff > config.maxBaseDistance) {
			return Optional.of(String.format(Locale.ROOT, "he is %.0f blocks from the base (at most %.0f)", figureOff, config.maxBaseDistance));
		}
		double hostOff = horizontal(hostFeet, b);
		if (hostOff > config.hostBaseDistance) {
			return Optional.of(String.format(Locale.ROOT, "the host is %.0f blocks from the base (at most %.0f)", hostOff, config.hostBaseDistance));
		}
		return Optional.empty();
	}

	/** A surface sighting card of the overworld: every card variant but the glimpse, the last one and the Nether and End ones. */
	public static boolean variantCounts(Variant variant) {
		return Variant.cards().contains(variant) && variant != Variant.GLIMPSE && variant != Variant.LAST_ONE && variant.dimension() == Level.OVERWORLD;
	}

	/** In-game night for the arm: {@link #NIGHT_FROM} to {@link #NIGHT_UNTIL}. */
	public static boolean night(long timeOfDay) {
		long t = Math.floorMod(timeOfDay, 24000L);
		return t >= NIGHT_FROM && t < NIGHT_UNTIL;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * The camera's yaw and pitch (degrees, Minecraft's convention: yaw 0 looks south, pitch positive looks down) from
	 * {@code from} toward {@code to}.
	 */
	public static float[] look(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float yaw = h < 1.0E-9 ? 0.0F : Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
		float pitch = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
		return new float[] {yaw, pitch};
	}

	/**
	 * The capture's time budget on the host's client (the game waits through it once per world): the unpresented frames
	 * and the waits between them, then a normal frame and the wait for the section graph of the player's own camera, all
	 * within {@link #CAP_MS} of the start. Every wait is cut to what is left: between frames to {@link #framesUntil} (the
	 * cap less one graph wait, kept for the frame back), the last one to the cap. At least {@link #MIN_FRAMES} frames,
	 * at most {@link #MAX_FRAMES}; past the minimum a frame starts only before {@code framesUntil} and only while something
	 * is still to be drawn. So the capture ends within the cap while each frame renders in under half a graph wait
	 * (100 ms): the last frame starts before {@code framesUntil} and the frame back follows it. Slower frames add their own
	 * time (the minimum and the frame back are always drawn): at most {@code CAP_MS + (MIN_FRAMES + 1)} frames' time.
	 *
	 * @param start the capture's start (milliseconds)
	 */
	public record Settle(long start) {
		/** The whole capture's cap, milliseconds. */
		public static final long CAP_MS = 1500L;
		/** The longest wait for vanilla's section graph, milliseconds. */
		public static final long GRAPH_WAIT_MS = 200L;
		/** The longest wait for the sections in view to compile between frames, milliseconds. */
		public static final long SECTIONS_WAIT_MS = 40L;
		/** At least this many frames, so the section graph is rebuilt for his camera and its sections are drawn. */
		public static final int MIN_FRAMES = 3;
		/** At most this many; the last one is the picture. */
		public static final int MAX_FRAMES = 40;

		/** No frame starts past this, nor a wait between frames lasts past it: the cap less one graph wait. */
		public long framesUntil() {
			return start + CAP_MS - GRAPH_WAIT_MS;
		}

		/** A wait between frames at {@code now}: at most {@code bound}, cut to {@link #framesUntil} (0 once that passed). */
		public long frameWait(long now, long bound) {
			return Math.clamp(framesUntil() - now, 0L, Math.max(0L, bound));
		}

		/** The last wait (the graph for the player's camera) at {@code now}: at most {@code bound}, cut to the cap. */
		public long cleanWait(long now, long bound) {
			return Math.clamp(start + CAP_MS - now, 0L, Math.max(0L, bound));
		}

		/**
		 * After {@code done} frames, at {@code now}: another one? Up to {@link #MIN_FRAMES} always, never past
		 * {@link #MAX_FRAMES}, else only while something is still to be drawn ({@code drawn} false) and before
		 * {@link #framesUntil}.
		 */
		public boolean another(int done, boolean drawn, long now) {
			if (done >= MAX_FRAMES) {
				return false;
			}
			return done < MIN_FRAMES || !drawn && now < framesUntil();
		}
	}

	/**
	 * The square vanilla's world icon takes from a frame {@code width} x {@code height} ({@code GameRenderer.takeAutoScreenshot}):
	 * the middle of the longer side, as wide as the shorter one. {x, y, side}.
	 */
	public static int[] crop(int width, int height) {
		if (width > height) {
			return new int[] {(width - height) / 2, 0, height};
		}
		return new int[] {0, (height - width) / 2, width};
	}

	/** A PNG of {@code width} x {@code height} opaque pixels ({@code argb}, rows top first; the alpha is dropped): 8-bit RGB, no filter. */
	public static byte[] png(int width, int height, int[] argb) {
		if (width <= 0 || height <= 0 || argb.length < width * height) {
			throw new IllegalArgumentException("a " + width + "x" + height + " picture needs " + width * height + " pixels, not " + argb.length);
		}
		byte[] raw = new byte[height * (1 + width * 3)];
		int i = 0;
		for (int y = 0; y < height; y++) {
			raw[i++] = 0; // filter: none
			for (int x = 0; x < width; x++) {
				int c = argb[y * width + x];
				raw[i++] = (byte) (c >> 16);
				raw[i++] = (byte) (c >> 8);
				raw[i++] = (byte) c;
			}
		}
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		ByteArrayOutputStream zipped = new ByteArrayOutputStream();
		try {
			deflater.setInput(raw);
			deflater.finish();
			byte[] buffer = new byte[8192];
			while (!deflater.finished()) {
				int n = deflater.deflate(buffer);
				zipped.write(buffer, 0, n);
			}
		} finally {
			deflater.end();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
		byte[] header = new byte[13];
		putInt(header, 0, width);
		putInt(header, 4, height);
		header[8] = 8; // bit depth
		header[9] = 2; // colour type: RGB
		chunk(out, "IHDR", header);
		chunk(out, "IDAT", zipped.toByteArray());
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
		byte[] length = new byte[4];
		putInt(length, 0, data.length);
		out.writeBytes(length);
		byte[] name = type.getBytes(StandardCharsets.US_ASCII);
		out.writeBytes(name);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(name);
		crc.update(data);
		byte[] sum = new byte[4];
		putInt(sum, 0, (int) crc.getValue());
		out.writeBytes(sum);
	}

	private static void putInt(byte[] into, int at, int value) {
		into[at] = (byte) (value >>> 24);
		into[at + 1] = (byte) (value >>> 16);
		into[at + 2] = (byte) (value >>> 8);
		into[at + 3] = (byte) value;
	}
}
