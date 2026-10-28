package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The gallery (docs/SELF_CREATED_2.md, "The gallery", SC2-0c, D-099): a development-only registry of exhibits, each a
 * thing placed in a flat test world with fixed cameras, so builders and reviewers can look at every structure and every
 * staged behaviour on its own, in day, dusk, night and fog. {@link #register} is ignored outside a development
 * environment ({@code FabricLoader.isDevelopmentEnvironment()}): nothing here exists in a release jar's game.
 *
 * <p>An exhibit's {@link Placer} builds it at an origin (the first air cell above the ground there, the cell a player's
 * feet stand in) and answers what it built: its {@link Placed#box() box}, the {@link View views} (a fixed camera each:
 * the eye, the look, the time of day, the {@link Sky sky} and whether the camera has night vision) and the
 * {@link Frames frames} (a time-lapse of a staged behaviour). {@link #standardViews} gives a structure the plan's
 * standard set. View and frame names are camera words or numbered ({@code far_day}, {@code in_1}, {@code f_1}); what a
 * view shows goes in its {@code note}, written only beside the PNG in {@code build/gallery/<id>/index.txt} (D-103).
 *
 * <p>The client run ({@code ./gradlew gallery -Pgallery=<ids>}), the {@code /a1016 sc g} command and the blueprint renders
 * live in {@code debug.gallery} and {@code debug.client.gallery}; {@link GalleryProps} holds the props every exhibit may use.
 */
public final class Gallery {
	/** Noon, the standard day views' time. */
	public static final long NOON = 6000L;
	/** Dusk, the standard {@code far_dusk} time. */
	public static final long DUSK = 12600L;
	/** Midnight, the standard night and fog views' time. */
	public static final long NIGHT = 18000L;
	/** A standing player's eye above the ground: the standard eye height of the ground views. */
	public static final double EYE = 1.7;
	/** Exhibit, view and frame names: lower case letters, digits and underscores (they are folder and file names). */
	private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

	/** The sky of a view: clear (the mod's own dusk fog as a player has it), dusk, a fog night or a fog day at full. */
	public enum Sky {
		CLEAR, DUSK, FOG_NIGHT, FOG_DAY
	}

	/**
	 * A fixed camera.
	 *
	 * @param name    a camera word or a number ({@code far_day}, {@code mouth}, {@code in_1}); the PNG's file name
	 * @param eye     where the eye is (the camera's own position, not the feet)
	 * @param yaw     Minecraft's yaw: 0 looks south (+Z), 90 west, 180 north, -90 east
	 * @param pitch   Minecraft's pitch: positive looks down
	 * @param dayTime the time of day (0 to 23999; 6000 noon, 12600 dusk, 18000 midnight)
	 * @param sky     the sky: {@link Sky#FOG_NIGHT} and {@link Sky#FOG_DAY} set a fog spell at full ({@link GalleryProps#fogNow})
	 * @param survey  night vision on the camera, to judge geometry in the dark (never the only shot of a dark place)
	 * @param note    what the view shows, in plain words (only ever written to the gallery's index files)
	 */
	public record View(String name, Vec3 eye, float yaw, float pitch, long dayTime, Sky sky, boolean survey, String note) {
		public View {
			checkName(name, "view");
			Objects.requireNonNull(eye, "eye");
			Objects.requireNonNull(sky, "sky");
			dayTime = Math.floorMod(dayTime, GameClock.TICKS_PER_DAY);
			note = note == null ? "" : note;
		}

		/** A view without a note. */
		public View(String name, Vec3 eye, float yaw, float pitch, long dayTime, Sky sky, boolean survey) {
			this(name, eye, yaw, pitch, dayTime, sky, survey, "");
		}

		/** A camera at {@code eye} looking at {@code at}. */
		public static View looking(String name, Vec3 eye, Vec3 at, long dayTime, Sky sky, boolean survey) {
			float[] look = look(eye, at);
			return new View(name, eye, look[0], look[1], dayTime, sky, survey, "");
		}

		/** This camera under another name. */
		public View named(String newName) {
			return new View(newName, eye, yaw, pitch, dayTime, sky, survey, note);
		}

		/** This camera at another time and sky. */
		public View at(long newDayTime, Sky newSky) {
			return new View(name, eye, yaw, pitch, newDayTime, newSky, survey, note);
		}

		/** This camera with or without night vision. */
		public View withSurvey(boolean on) {
			return new View(name, eye, yaw, pitch, dayTime, sky, on, note);
		}

		/** This camera with a note (what it shows, for the index). */
		public View noted(String newNote) {
			return new View(name, eye, yaw, pitch, dayTime, sky, survey, newNote);
		}
	}

	/**
	 * A time-lapse: the camera is set, {@code start} runs once on the server, then a screenshot is taken every
	 * {@code everyTicks} ticks, {@code count} in all ({@code build/gallery/<id>/<name>/f00.png} ...).
	 *
	 * @param start sets the staged behaviour going (on the server thread); the gallery's staging only (its test mobs)
	 */
	public record Frames(String name, int count, int everyTicks, View camera, Consumer<ServerLevel> start, String note) {
		public Frames {
			checkName(name, "frames");
			if (count < 1 || count > 200) {
				throw new IllegalArgumentException("frames " + name + ": count " + count + " (1 to 200)");
			}
			if (everyTicks < 1) {
				throw new IllegalArgumentException("frames " + name + ": every " + everyTicks + " ticks");
			}
			Objects.requireNonNull(camera, "camera");
			start = start == null ? level -> { } : start;
			note = note == null ? "" : note;
		}

		/** Frames without a note. */
		public Frames(String name, int count, int everyTicks, View camera, Consumer<ServerLevel> start) {
			this(name, count, everyTicks, camera, start, "");
		}

		/** These frames with a note (what they show, for the index). */
		public Frames noted(String newNote) {
			return new Frames(name, count, everyTicks, camera, start, newNote);
		}
	}

	/** What a placer built: its box (the blueprint renders cover it), its views and its frames, in shooting order. */
	public record Placed(BoundingBox box, List<View> views, List<Frames> frames) {
		public Placed {
			Objects.requireNonNull(box, "box");
			views = List.copyOf(views);
			frames = List.copyOf(frames);
			List<String> names = new ArrayList<>();
			for (View view : views) {
				names.add(view.name());
			}
			for (Frames f : frames) {
				names.add(f.name());
			}
			if (names.stream().distinct().count() != names.size()) {
				throw new IllegalArgumentException("two views or frames share a name: " + names);
			}
		}
	}

	/** Builds an exhibit. Server thread; dev only (the gallery, its tests and {@code /a1016 sc g}). */
	@FunctionalInterface
	public interface Placer {
		/**
		 * @param origin the first air cell above the ground where the exhibit stands (a player's feet there)
		 * @param seed   for anything the exhibit varies (the same seed builds the same thing)
		 */
		Placed place(ServerLevel level, BlockPos origin, long seed);
	}

	/**
	 * An exhibit.
	 *
	 * @param id      its id, the feature's number and a letter for a variant ({@code 16}, {@code 16s}); a folder name
	 * @param feature the feature's number ({@code 16}), for the index
	 */
	public record Exhibit(String id, String feature, Placer placer) {
		public Exhibit {
			checkName(id, "exhibit");
			Objects.requireNonNull(feature, "feature");
			Objects.requireNonNull(placer, "placer");
		}
	}

	private static final Map<String, Exhibit> EXHIBITS = new LinkedHashMap<>();

	private Gallery() {
	}

	/** Whether the gallery exists in this game: only in a development environment (the dev client and the game tests). */
	public static boolean active() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	/** Registers an exhibit (a later one with the same id replaces it); ignored outside a development environment. */
	public static void register(Exhibit exhibit) {
		register(exhibit, active());
	}

	/** {@link #register(Exhibit)} with the environment given: true if it was registered. For the tests. */
	public static synchronized boolean register(Exhibit exhibit, boolean development) {
		Objects.requireNonNull(exhibit, "exhibit");
		if (!development) {
			return false;
		}
		EXHIBITS.put(exhibit.id(), exhibit);
		return true;
	}

	/** Takes an exhibit out (tests only). */
	public static synchronized boolean unregister(String id) {
		return EXHIBITS.remove(id) != null;
	}

	public static synchronized Optional<Exhibit> get(String id) {
		return Optional.ofNullable(EXHIBITS.get(id));
	}

	/** Every exhibit, sorted by id ({@code 00} first). */
	public static synchronized List<Exhibit> all() {
		List<Exhibit> all = new ArrayList<>(EXHIBITS.values());
		all.sort((a, b) -> a.id().compareTo(b.id()));
		return Collections.unmodifiableList(all);
	}

	/** The exhibits a list names ({@code 00,16}; {@code all} for every one), in its order; unknown ids are skipped. */
	public static List<Exhibit> named(String list, List<String> unknown) {
		if (list == null || list.isBlank() || list.trim().equals("all")) {
			return all();
		}
		List<Exhibit> out = new ArrayList<>();
		for (String part : list.split("[,\\s]+")) {
			String id = part.trim();
			if (id.isEmpty()) {
				continue;
			}
			Optional<Exhibit> exhibit = get(id);
			if (exhibit.isPresent()) {
				if (!out.contains(exhibit.get())) {
					out.add(exhibit.get());
				}
			} else if (unknown != null) {
				unknown.add(id);
			}
		}
		return out;
	}

	static void checkName(String name, String what) {
		if (name == null || !NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("a gallery " + what + " name is lower case letters, digits and _: '" + name + "'");
		}
	}

	// --- cameras ---

	/** The yaw and pitch (Minecraft's) of a camera at {@code from} looking at {@code to}. */
	public static float[] look(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
		return new float[] {yaw, pitch};
	}

	/** The first air cell above the ground at this column (its chunk is loaded, generated if need be). Server thread. */
	public static int ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** A ground camera: at (x, z), {@link #EYE} above the ground there. */
	public static Vec3 groundEye(ServerLevel level, double x, double z) {
		return new Vec3(x, ground(level, Mth.floor(x), Mth.floor(z)) + EYE, z);
	}

	/** The point {@code distance} out from {@code from} along {@code facing} turned {@code degrees} (positive: clockwise from above). */
	public static Vec3 out(Vec3 from, Direction facing, double degrees, double distance) {
		double angle = Math.atan2(facing.getStepZ(), facing.getStepX()) + Math.toRadians(degrees);
		return new Vec3(from.x + Math.cos(angle) * distance, from.y, from.z + Math.sin(angle) * distance);
	}

	/**
	 * The plan's standard views of a structure (docs/SELF_CREATED_2.md, "Standard views for structures"), on the side its
	 * entrance faces: {@code far_day} (200 out from the box's centre, a ground eye, noon), {@code far_dusk} (12600),
	 * {@code far_night} (18000), {@code far_fog} (a fog night at full), {@code mid} (140 out, 30 degrees aside, noon),
	 * {@code near} (40 out from the entrance), {@code mouth} and {@code mouth_night} (6 in front of the entrance, at the
	 * mouth's own height, looking in) and {@code aerial} (180 out, 45 degrees aside, 160 up, looking at the centre). The
	 * far and mid cameras look at the middle of the box's height (never under the ground there). Loads the chunks it
	 * reads the ground from. Add {@code in_*}, {@code survey_*} and {@code section} views of your own.
	 *
	 * @param facing the way the entrance faces (out of the structure)
	 */
	public static List<View> standardViews(ServerLevel level, BoundingBox box, BlockPos entrance, Direction facing) {
		BlockPos c = box.getCenter();
		int focusGround = ground(level, c.getX(), c.getZ());
		Vec3 focus = new Vec3(c.getX() + 0.5, Math.max(focusGround + 1.0, (box.minY() + box.maxY() + 1) / 2.0), c.getZ() + 0.5);
		Vec3 centre = Vec3.atBottomCenterOf(c);
		Vec3 farSpot = out(centre, facing, 0, 200);
		Vec3 far = groundEye(level, farSpot.x, farSpot.z);
		Vec3 midSpot = out(centre, facing, 30, 140);
		Vec3 mid = groundEye(level, midSpot.x, midSpot.z);
		Vec3 door = Vec3.atBottomCenterOf(entrance);
		Vec3 nearSpot = out(door, facing, 0, 40);
		Vec3 near = groundEye(level, nearSpot.x, nearSpot.z);
		Vec3 nearAt = door.add(0, Math.min(12.0, box.getYSpan() / 3.0), 0);
		Vec3 mouth = out(door, facing, 0, 6).add(0, EYE, 0);
		Vec3 mouthAt = door.add(0, EYE, 0).add(-facing.getStepX() * 4.0, 0, -facing.getStepZ() * 4.0);
		Vec3 aerialSpot = out(centre, facing, -45, 180);
		int aerialGround = ground(level, Mth.floor(aerialSpot.x), Mth.floor(aerialSpot.z));
		Vec3 aerial = new Vec3(aerialSpot.x, Math.max(focusGround, aerialGround) + 160.0, aerialSpot.z);
		Vec3 aerialAt = new Vec3(focus.x, focusGround, focus.z);
		List<View> views = new ArrayList<>();
		View farDay = View.looking("far_day", far, focus, NOON, Sky.CLEAR, false).noted("200 out, noon");
		views.add(farDay);
		views.add(farDay.named("far_dusk").at(DUSK, Sky.DUSK).noted("200 out, dusk"));
		views.add(farDay.named("far_night").at(NIGHT, Sky.CLEAR).noted("200 out, midnight"));
		views.add(farDay.named("far_fog").at(NIGHT, Sky.FOG_NIGHT).noted("200 out, a fog night at full"));
		views.add(View.looking("mid", mid, focus, NOON, Sky.CLEAR, false).noted("140 out, 30 degrees aside, noon"));
		views.add(View.looking("near", near, nearAt, NOON, Sky.CLEAR, false).noted("40 out from the entrance, noon"));
		View mouthDay = View.looking("mouth", mouth, mouthAt, NOON, Sky.CLEAR, false).noted("6 in front of the entrance, noon");
		views.add(mouthDay);
		views.add(mouthDay.named("mouth_night").at(NIGHT, Sky.CLEAR).noted("6 in front of the entrance, midnight"));
		views.add(View.looking("aerial", aerial, aerialAt, NOON, Sky.CLEAR, false).noted("180 out, 160 up, noon"));
		return views;
	}

	/** The distances of the sign views (blocks from the sign point, D-110). */
	public static final int[] SIGN_DISTANCES = {64, 128, 200};

	/**
	 * The error sign's views (docs/SELF_CREATED_2_R3.md, D-110): from {@value #EYE} over the land at 64, 128 and 200 blocks
	 * from the sign point along {@code facing} (the side toward the origin: the way in's facing), looking at the sign point
	 * {@code lookUp} blocks up; {@code d64}, {@code d128}, {@code d200} at noon and {@code n64}, {@code n128}, {@code n200}
	 * at midnight (clear sky). Loads the chunks it reads the ground from. Every place task shoots its sign with these.
	 */
	public static List<View> signViews(ServerLevel level, BlockPos sign, Direction facing, double lookUp) {
		Vec3 at = Vec3.atBottomCenterOf(sign).add(0, lookUp, 0);
		List<View> day = new ArrayList<>();
		List<View> night = new ArrayList<>();
		for (int d : SIGN_DISTANCES) {
			Vec3 spot = out(Vec3.atBottomCenterOf(sign), facing, 0, d);
			Vec3 eye = groundEye(level, spot.x, spot.z);
			View v = View.looking("d" + d, eye, at, NOON, Sky.CLEAR, false).noted(d + " out from the sign point, noon");
			day.add(v);
			night.add(v.named("n" + d).at(NIGHT, Sky.CLEAR).noted(d + " out from the sign point, midnight"));
		}
		day.addAll(night);
		return day;
	}

	/** {@link #signViews(ServerLevel, BlockPos, Direction, double)} looking 2 blocks over the sign point. */
	public static List<View> signViews(ServerLevel level, BlockPos sign, Direction facing) {
		return signViews(level, sign, facing, 2.0);
	}

	/**
	 * A {@code section} camera for a box cut away through its middle on {@code side} ({@link GalleryProps#cutaway}):
	 * {@code distance} out from the cut face, level with the box's middle, looking straight at it, with night vision
	 * (survey), at noon. At 70 degrees a face {@code w} wide fills the frame's width from about {@code 0.8 w} out.
	 */
	public static View sectionView(BoundingBox box, Direction side, double distance) {
		BlockPos c = box.getCenter();
		Vec3 middle = new Vec3(c.getX() + 0.5, (box.minY() + box.maxY() + 1) / 2.0, c.getZ() + 0.5);
		Vec3 eye = middle.add(side.getStepX() * distance, 0, side.getStepZ() * distance);
		return View.looking("section", eye, middle, NOON, Sky.CLEAR, true).noted("the cut face, night vision");
	}
}
