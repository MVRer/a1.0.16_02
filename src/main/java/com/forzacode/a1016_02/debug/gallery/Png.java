package com.forzacode.a1016_02.debug.gallery;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A plain PNG writer and reader for the gallery's blueprint renders (headless: no AWT, no client classes). Writes 8-bit
 * RGBA, no interlace, filter 0 on every row; reads back what it writes (and any 8-bit RGBA or RGB PNG without
 * interlace, every filter type), for the round-trip test and for checking a render.
 */
public final class Png {
	private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

	/** An image: {@code width} x {@code height} pixels, ARGB, rows top first. */
	public record Image(int width, int height, int[] argb) {
		public Image {
			if (width < 1 || height < 1 || argb.length != width * height) {
				throw new IllegalArgumentException(width + " x " + height + " with " + argb.length + " pixels");
			}
		}

		public int at(int x, int y) {
			return argb[y * width + x];
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Image other && other.width == width && other.height == height && Arrays.equals(other.argb, argb);
		}

		@Override
		public int hashCode() {
			return 31 * (31 * width + height) + Arrays.hashCode(argb);
		}
	}

	private Png() {
	}

	/** The PNG bytes of an ARGB image (8-bit RGBA). */
	public static byte[] write(Image image) {
		int w = image.width();
		int h = image.height();
		byte[] raw = new byte[h * (1 + w * 4)];
		int i = 0;
		for (int y = 0; y < h; y++) {
			raw[i++] = 0;
			for (int x = 0; x < w; x++) {
				int p = image.argb()[y * w + x];
				raw[i++] = (byte) (p >> 16);
				raw[i++] = (byte) (p >> 8);
				raw[i++] = (byte) p;
				raw[i++] = (byte) (p >>> 24);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(SIGNATURE);
		ByteBuffer header = ByteBuffer.allocate(13);
		header.putInt(w).putInt(h).put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);
		chunk(out, "IHDR", header.array());
		chunk(out, "IDAT", deflate(raw));
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	/** Reads an 8-bit RGBA or RGB PNG without interlace (RGB pixels come back opaque). */
	public static Image read(byte[] png) {
		if (png.length < 8 || !Arrays.equals(Arrays.copyOf(png, 8), SIGNATURE)) {
			throw new IllegalArgumentException("not a PNG");
		}
		ByteBuffer in = ByteBuffer.wrap(png, 8, png.length - 8);
		int w = 0;
		int h = 0;
		int channels = 0;
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		while (in.remaining() >= 12) {
			int length = in.getInt();
			byte[] type = new byte[4];
			in.get(type);
			byte[] body = new byte[length];
			in.get(body);
			int crc = in.getInt();
			CRC32 check = new CRC32();
			check.update(type);
			check.update(body);
			if ((int) check.getValue() != crc) {
				throw new IllegalArgumentException("bad CRC in " + new String(type, StandardCharsets.US_ASCII));
			}
			String name = new String(type, StandardCharsets.US_ASCII);
			if (name.equals("IHDR")) {
				ByteBuffer hdr = ByteBuffer.wrap(body);
				w = hdr.getInt();
				h = hdr.getInt();
				int depth = hdr.get();
				int color = hdr.get();
				hdr.get();
				hdr.get();
				int interlace = hdr.get();
				if (depth != 8 || (color != 6 && color != 2) || interlace != 0) {
					throw new IllegalArgumentException("only 8-bit RGBA or RGB without interlace (depth " + depth + ", colour " + color + ")");
				}
				channels = color == 6 ? 4 : 3;
			} else if (name.equals("IDAT")) {
				data.writeBytes(body);
			} else if (name.equals("IEND")) {
				break;
			}
		}
		if (channels == 0) {
			throw new IllegalArgumentException("no IHDR");
		}
		byte[] raw = inflate(data.toByteArray(), h * (1 + w * channels));
		int stride = w * channels;
		byte[] prev = new byte[stride];
		byte[] row = new byte[stride];
		int[] argb = new int[w * h];
		for (int y = 0; y < h; y++) {
			int at = y * (stride + 1);
			int filter = raw[at];
			System.arraycopy(raw, at + 1, row, 0, stride);
			unfilter(filter, row, prev, channels);
			for (int x = 0; x < w; x++) {
				int o = x * channels;
				int r = row[o] & 0xFF;
				int g = row[o + 1] & 0xFF;
				int b = row[o + 2] & 0xFF;
				int a = channels == 4 ? row[o + 3] & 0xFF : 0xFF;
				argb[y * w + x] = a << 24 | r << 16 | g << 8 | b;
			}
			byte[] t = prev;
			prev = row;
			row = t;
		}
		return new Image(w, h, argb);
	}

	private static void unfilter(int filter, byte[] row, byte[] prev, int bpp) {
		for (int i = 0; i < row.length; i++) {
			int a = i >= bpp ? row[i - bpp] & 0xFF : 0;
			int b = prev[i] & 0xFF;
			int c = i >= bpp ? prev[i - bpp] & 0xFF : 0;
			int add = switch (filter) {
				case 0 -> 0;
				case 1 -> a;
				case 2 -> b;
				case 3 -> (a + b) >>> 1;
				case 4 -> paeth(a, b, c);
				default -> throw new IllegalArgumentException("filter " + filter);
			};
			row[i] = (byte) (row[i] + add);
		}
	}

	private static int paeth(int a, int b, int c) {
		int p = a + b - c;
		int pa = Math.abs(p - a);
		int pb = Math.abs(p - b);
		int pc = Math.abs(p - c);
		return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
	}

	private static byte[] deflate(byte[] raw) {
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		try {
			deflater.setInput(raw);
			deflater.finish();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			while (!deflater.finished()) {
				out.write(buffer, 0, deflater.deflate(buffer));
			}
			return out.toByteArray();
		} finally {
			deflater.end();
		}
	}

	private static byte[] inflate(byte[] data, int size) {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(data);
			byte[] out = new byte[size];
			int n = 0;
			while (n < size && !inflater.finished()) {
				int got = inflater.inflate(out, n, size - n);
				if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					break;
				}
				n += got;
			}
			if (n != size) {
				throw new IllegalArgumentException("image data is " + n + " bytes, not " + size);
			}
			return out;
		} catch (DataFormatException e) {
			throw new IllegalArgumentException("bad image data", e);
		} finally {
			inflater.end();
		}
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
		byte[] name = type.getBytes(StandardCharsets.US_ASCII);
		ByteBuffer length = ByteBuffer.allocate(4).putInt(data.length);
		out.writeBytes(length.array());
		out.writeBytes(name);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(name);
		crc.update(data);
		out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
	}
}
