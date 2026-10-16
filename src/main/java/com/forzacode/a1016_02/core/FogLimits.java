package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Where the world fog ends for one player, computed the same way on both sides so the server (where he may stand)
 * and the client (what the fog hides) agree. Pure math plus one server-side convenience; no client classes.
 *
 * <p>The un-pulled render limit is vanilla's: the effective render distance (the smaller of the client's and the
 * server's view distance, at least 2 chunks) times 16; vanilla fog ends exactly there in the overworld. The dusk fog
 * ({@code HerobrineState.Effects.duskFogLevel}) pulls it in with atmosphere's client curve: weighted by the time of
 * day ({@link #duskWeight}: none by day, easing in slowly from the shape's start to full at its peak, easing to
 * {@code duskNightWeight} for the night, faded before sunrise), then geometric toward {@code duskMinFogBlocks}
 * ({@link #fogEnd}). A fog spell (D-071, {@link FogSpell}, the world's fog weather) adds its amount on the same clock:
 * the two combine as {@code 1 - (1 - dusk)(1 - spell)} ({@link #combine}) before the same pull, so the figure's band
 * follows a fog night even without a fresh client report. The spell's amount is always its own ease
 * ({@link FogSpell#amount(double)}: the ease it was made with travels with it), so both sides get the same number
 * whatever their configs; for a player the server weights it by the sky light at their eyes ({@link #skyFactor}). Fog
 * places (D-085, {@link FogPlace}: fog weather over one spot) are weather too: their amount at the eyes
 * ({@link #placeAmount}) is weighted by the same sky factor and combined with the spell's
 * ({@link #weatherAmount}) before the dusk fog, so with no places every result is bit for bit what it was; a place
 * can also be seen from outside it, along the view ({@link #placeViews}, {@link #seen}). Fog
 * surges (short, per player) and the client's easing between dusk levels (at most {@code duskLevelChangeSeconds} after
 * a change; the client's own fog report covers it) are not included. Fixed-time dimensions get neither. Atmosphere owns
 * the shape and the weather numbers: it installs its config with {@link #installShape} and
 * {@link FogWeatherRules#install}; until then {@link Shape#DEFAULT} and {@link FogWeatherRules.Settings#DEFAULT} match
 * atmosphere's defaults.
 */
public final class FogLimits {
	/**
	 * The dusk fog shape (atmosphere's {@code duskMinFogBlocks}, {@code duskNightWeight} and the {@code duskFog...}
	 * timing). Times are times of day (0 is sunrise, 12000 sunset): the fog starts building at {@code duskStart}, is
	 * full from {@code duskPeak} to {@code duskHoldUntil}, eases to the night weight by {@code duskNightFrom}, starts
	 * fading at {@code duskFadeFrom} and is gone at {@code duskEnd}. {@code duskEaseExponent} shapes every ease
	 * ({@link #ease}). The constructor puts the times in that order inside one day and the exponent in 1 to 8.
	 */
	public record Shape(double duskMinFogBlocks, double duskNightWeight, long duskStart, long duskPeak, long duskHoldUntil, long duskNightFrom,
			long duskFadeFrom, long duskEnd, double duskEaseExponent) {
		public static final Shape DEFAULT = new Shape(24.0, 0.55);

		public Shape {
			duskStart = Math.clamp(duskStart, 0L, DAY);
			duskPeak = Math.clamp(duskPeak, duskStart, DAY);
			duskHoldUntil = Math.clamp(duskHoldUntil, duskPeak, DAY);
			duskNightFrom = Math.clamp(duskNightFrom, duskHoldUntil, DAY);
			duskFadeFrom = Math.clamp(duskFadeFrom, duskNightFrom, DAY);
			duskEnd = Math.clamp(duskEnd, duskFadeFrom, DAY);
			duskEaseExponent = Double.isNaN(duskEaseExponent) ? 3.0 : Math.clamp(duskEaseExponent, 1.0, 8.0);
		}

		/** This fog end and night weight with the default timing (from 9000, full 13000 to 13800, night by 16000, fading 19500 to 23500, exponent 3). */
		public Shape(double duskMinFogBlocks, double duskNightWeight) {
			this(duskMinFogBlocks, duskNightWeight, 9000L, 13000L, 13800L, 16000L, 19500L, 23500L, 3.0);
		}
	}

	/**
	 * @param chunks      effective render distance in chunks
	 * @param renderLimit where vanilla fog is complete without any dusk fog, in blocks
	 * @param fogEnd      where the fog is complete now, with the dusk fog for this time of day, in blocks
	 */
	public record Result(int chunks, double renderLimit, double fogEnd) {
	}

	public static final long DAY = 24000L;
	/** Sky light at or above this gives a fog spell in full ({@link #skyFactor}). */
	public static final int SKY_FULL = 10;
	/** Sky light at or below this gives no fog spell ({@link #skyFactor}). */
	public static final int SKY_NONE = 4;
	/**
	 * How far from the middle of a view a fog place still counts as seen ({@link #placeViews}), in degrees: its best
	 * line of sight within this of the view's own.
	 */
	public static final double VIEW_CONE_DEGREES = 20.0;

	private static volatile Supplier<Shape> shape = () -> Shape.DEFAULT;

	private FogLimits() {
	}

	/** Atmosphere: where the current shape comes from (its config section). Any thread. */
	public static void installShape(Supplier<Shape> source) {
		shape = Objects.requireNonNull(source);
	}

	/** The installed shape, or {@link Shape#DEFAULT}. */
	public static Shape shape() {
		Shape current = shape.get();
		return current != null ? current : Shape.DEFAULT;
	}

	/** Vanilla's effective render distance: {@code min(client, server)} chunks, the server's if the client's is unknown (0 or less), at least 2. */
	public static int effectiveChunks(int clientChunks, int serverChunks) {
		int chunks = clientChunks > 0 ? Math.min(clientChunks, serverChunks) : serverChunks;
		return Math.max(chunks, 2);
	}

	/** The fog for these view distances, dusk fog level (0 to 1) and day time, with the installed shape and no fog spell. */
	public static Result of(int clientChunks, int serverChunks, float duskFogLevel, long dayTime) {
		return of(clientChunks, serverChunks, duskFogLevel, dayTime, shape());
	}

	/** The fog for these view distances, dusk fog level (0 to 1), day time (0 is sunrise) and shape, with no fog spell. */
	public static Result of(int clientChunks, int serverChunks, float duskFogLevel, long dayTime, Shape shape) {
		return of(clientChunks, serverChunks, duskFogLevel, dayTime, shape, 0.0);
	}

	/**
	 * The fog for these view distances, dusk fog level (0 to 1), clock time and fog spell (its own ease), with the installed
	 * shape. {@code clockTime} is the absolute clock ({@code getDefaultClockTime()}): its time of day
	 * weights the dusk fog, the whole of it places the spell.
	 */
	public static Result of(int clientChunks, int serverChunks, float duskFogLevel, long clockTime, Optional<FogSpell> spell) {
		return of(clientChunks, serverChunks, duskFogLevel, clockTime, spell, shape(), FogWeatherRules.settings());
	}

	/**
	 * {@link #of(int, int, float, long, Optional)} with this shape. The weather settings are not read (the spell's own
	 * ease counts, {@link #spellAmount(Optional, double)}); the form is kept so older calls compile.
	 */
	public static Result of(int clientChunks, int serverChunks, float duskFogLevel, long clockTime, Optional<FogSpell> spell, Shape shape,
			FogWeatherRules.Settings weather) {
		return of(clientChunks, serverChunks, duskFogLevel, clockTime, shape, spellAmount(spell, clockTime));
	}

	/**
	 * The pure form: the fog for these view distances, dusk fog level (0 to 1), day time (0 is sunrise), shape and a fog
	 * spell's amount now (0 to 1, {@link FogSpell#amount}). The dusk amount ({@code level * duskWeight}) and the spell's
	 * {@link #combine}, and the result pulls the render limit in ({@link #fogEnd} toward {@code duskMinFogBlocks}).
	 */
	public static Result of(int clientChunks, int serverChunks, float duskFogLevel, long dayTime, Shape shape, double spellAmount) {
		int chunks = effectiveChunks(clientChunks, serverChunks);
		double renderLimit = chunks * 16.0;
		double amount = combine(clamp01(duskFogLevel) * duskWeight(dayTime, shape), spellAmount);
		return new Result(chunks, renderLimit, fogEnd(renderLimit, shape.duskMinFogBlocks(), amount));
	}

	/**
	 * The fog one player sees now: their requested view distance, the server's, the world's dusk fog level, fog spell
	 * and fog places, and their level's clock (none of them where time is fixed). The spell and the places are
	 * weather, outside: their amounts (the places' at the player's eyes) are weighted by the sky light at the eyes
	 * ({@link #skyFactor}), as vanilla gates its rain fog on the sky, so a fog night or a fog place gives no fog in a
	 * cave or a closed house (the dusk fog is not weighted), and they combine as
	 * {@code combine(dusk, combine(spell, place))} ({@link #weatherAmount}). A fog place ahead along the player's yaw
	 * ({@link #placeViews} from the eyes) brings the fog end in as seen from there ({@link #seen}, the same sky weight),
	 * as the client draws it. With no places the result is bit for bit what it was without them. Server thread.
	 */
	public static Result of(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Level level = player.level();
		boolean fixed = level.dimensionType().hasFixedTime();
		HerobrineState.Effects effects = HerobrineState.get(server).effects();
		float dusk = fixed ? 0.0F : effects.duskFogLevel();
		long clock = level.getDefaultClockTime();
		Vec3 eyes = player.getEyePosition();
		double spell = fixed ? 0.0 : spellAmount(effects.fogSpell(), clock);
		double place = fixed ? 0.0 : placeAmount(effects.fogPlaces(), level.dimension(), eyes, clock);
		List<PlaceView> views = fixed ? List.of() : placeViews(effects.fogPlaces(), level.dimension(), eyes, player.getYRot(), clock);
		double sky = spell > 0.0 || place > 0.0 || !views.isEmpty() ? skyFactor(level.getBrightness(LightLayer.SKY, BlockPos.containing(eyes))) : 1.0;
		int clientChunks = player.requestedViewDistance();
		int serverChunks = server.getPlayerList().getViewDistance();
		Shape shape = shape();
		Result eye = of(clientChunks, serverChunks, dusk, clock, shape, weatherAmount(spell, place, sky));
		return views.isEmpty() ? eye : seen(eye, views, clientChunks, serverChunks, dusk, clock, shape, spell, sky);
	}

	/**
	 * A fog place seen along a view ({@link #placeViews}): the fog there begins {@code near} blocks out along the view
	 * (where it enters the place; 0 from inside it) and is as thick as the fog places' amount ({@link #placeAmount}, 0
	 * to 1, not weighted by the sky) at the point of the view nearest that place's centre.
	 */
	public record PlaceView(double near, double amount) {
	}

	/**
	 * The fog places seen from {@code eye} looking along {@code yawDegrees} (an entity's yaw: 0 looks toward +z, 90
	 * toward -x), one per place the view meets, horizontally like the places themselves (D-085: a place is weather over
	 * one spot, so it can be seen from outside it, a fog standing there). For each place in {@code dim} that gives fog
	 * at this clock time, the line of sight within {@link #VIEW_CONE_DEGREES} of the view that passes nearest its
	 * centre: if it passes inside the radius and that nearest point lies ahead of the eye, the view is
	 * {@link PlaceView}{@code (near, amount)}, {@code near} where the line enters the place (0 from inside) and
	 * {@code amount} all the places' amount at that nearest point. A place behind the eye or passed by gives none,
	 * and so does a view from inside a place away from its centre (nothing ahead is thicker than at the eye). Pure,
	 * the same on both sides from the same places, eye, yaw and clock; continuous in each (a view appears with
	 * {@code amount} 0 where the line of sight grazes the radius).
	 */
	public static List<PlaceView> placeViews(List<FogPlace> places, ResourceKey<Level> dim, Vec3 eye, double yawDegrees, double clockTime) {
		if (places.isEmpty() || !Double.isFinite(yawDegrees) || !Double.isFinite(eye.x) || !Double.isFinite(eye.z)) {
			return List.of();
		}
		double yaw = Math.toRadians(yawDegrees);
		double ux = -Math.sin(yaw);
		double uz = Math.cos(yaw);
		double cone = Math.toRadians(VIEW_CONE_DEGREES);
		List<PlaceView> views = null;
		for (FogPlace place : places) {
			double r = place.radius();
			if (!(r > 0.0) || !place.dimension().equals(dim) || !(place.timeAmount(clockTime) > 0.0)) {
				continue;
			}
			double vx = place.center().x - eye.x;
			double vz = place.center().z - eye.z;
			double d = Math.sqrt(vx * vx + vz * vz);
			double nx = d > 1.0E-9 ? vx / d : ux;
			double nz = d > 1.0E-9 ? vz / d : uz;
			// The angle from the view to the way to the centre, less the cone: the best line of sight's.
			double toCentre = Math.acos(Math.clamp(nx * ux + nz * uz, -1.0, 1.0));
			double off = Math.max(0.0, toCentre - cone);
			if (off >= Math.PI / 2.0) {
				continue;
			}
			double along = d * Math.cos(off);
			double side = d * Math.sin(off);
			if (side >= r) {
				continue;
			}
			// That line of sight: the way to the centre turned by off toward the view.
			double turn = nx * uz - nz * ux >= 0.0 ? off : -off;
			double cos = Math.cos(turn);
			double sin = Math.sin(turn);
			double wx = nx * cos - nz * sin;
			double wz = nx * sin + nz * cos;
			Vec3 nearest = new Vec3(eye.x + wx * along, eye.y, eye.z + wz * along);
			double amount = placeAmount(places, dim, nearest, clockTime);
			if (!(amount > 0.0)) {
				continue;
			}
			if (views == null) {
				views = new ArrayList<>(2);
			}
			views.add(new PlaceView(Math.max(0.0, along - Math.sqrt(r * r - side * side)), amount));
		}
		return views == null ? List.of() : views;
	}

	/**
	 * The fog end along a view: the eye's own ({@code eye}, {@link #of(int, int, float, long, Shape, double)} with the
	 * fog where the eye is) or, nearer, a seen place's: its {@code near} plus the fog end for the same dusk fog and
	 * weather with the place's amount there in place of the eye's ({@code weatherAmount(spell, view.amount, sky)}), as
	 * if one stood where the view enters it. The nearest of them; {@code eye} itself when no view is nearer. Atmosphere's
	 * client draws exactly this end for the frame ({@code WeatherFog.shape} with its seen places).
	 */
	public static Result seen(Result eye, List<PlaceView> views, int clientChunks, int serverChunks, float duskFogLevel, long dayTime, Shape shape,
			double spell, double sky) {
		double end = eye.fogEnd();
		for (PlaceView view : views) {
			double at = view.near() + of(clientChunks, serverChunks, duskFogLevel, dayTime, shape, weatherAmount(spell, view.amount(), sky)).fogEnd();
			if (at < end) {
				end = at;
			}
		}
		return end < eye.fogEnd() ? new Result(eye.chunks(), eye.renderLimit(), end) : eye;
	}

	/**
	 * The fog places' amount at {@code pos} in {@code dim} at this clock time (0 to 1, the partial tick allowed), each
	 * its own {@link FogPlace#amount}, together {@code 1 - Π(1 - a)}: never less than the thickest, never over 1, and
	 * exactly 0 with no places (or none here). The same on both sides from the same places and clock; the sky weight is
	 * the caller's ({@link #weatherAmount}).
	 */
	public static double placeAmount(List<FogPlace> places, ResourceKey<Level> dim, Vec3 pos, double clockTime) {
		double clear = 1.0;
		boolean any = false;
		for (FogPlace place : places) {
			double a = clamp01(place.amount(pos, dim, clockTime));
			if (a > 0.0) {
				clear *= 1.0 - a;
				any = true;
			}
		}
		return any ? clamp01(1.0 - clear) : 0.0;
	}

	/**
	 * The weather's fog amount, 0 to 1: a fog spell's amount and the fog places' (each 0 to 1, unweighted) times the sky
	 * factor where they are seen ({@link #skyFactor}, 0 to 1; the client's smoothed one), together
	 * ({@link #combine}). It goes into the pure {@link #of(int, int, float, long, Shape, double)} as its spell amount,
	 * which combines it with the dusk fog. With {@code place} 0 it is {@code spell * sky} exactly.
	 */
	public static double weatherAmount(double spell, double place, double sky) {
		double s = clamp01(sky);
		return combine(spell > 0.0 ? spell * s : 0.0, place > 0.0 ? place * s : 0.0);
	}

	/**
	 * How much of a fog spell reaches a place with this sky light (0 to 15, the raw sky light, not darkened by the time
	 * of day), 0 to 1: 1 at {@link #SKY_FULL} (10) or more (the open sky, under a tree, a doorway), 0 at {@link #SKY_NONE}
	 * (4) or less (a cave, a closed house), linear between, so walking out of a tunnel brings the fog in over six light
	 * levels. The spell's fog is weather, outside; the server applies it in {@link #of(ServerPlayer)} at the player's
	 * eyes, the client the same factor, smoothed.
	 */
	public static double skyFactor(int skyLight) {
		if (skyLight >= SKY_FULL) {
			return 1.0;
		}
		if (skyLight <= SKY_NONE) {
			return 0.0;
		}
		return (double) (skyLight - SKY_NONE) / (SKY_FULL - SKY_NONE);
	}

	/** A fog spell's amount at this clock time with its own ease ({@link FogSpell#amount(double)}); 0 for none. */
	public static double spellAmount(Optional<FogSpell> spell, double clockTime) {
		return spell.map(s -> s.amount(clockTime)).orElse(0.0);
	}

	/**
	 * {@link #spellAmount(Optional, double)}: the spell's own ease, the settings are not read (kept so older calls
	 * compile; client and server get the same number from the same spell whatever their configs).
	 */
	public static double spellAmount(Optional<FogSpell> spell, double clockTime, FogWeatherRules.Settings weather) {
		return spellAmount(spell, clockTime);
	}

	/**
	 * Two fog amounts (each 0 to 1) together: {@code 1 - (1 - a)(1 - b)}. Never less than either, never over 1, and
	 * either at 0 gives the other exactly, so a spell never changes a clear evening's dusk fog and vice versa.
	 */
	public static double combine(double a, double b) {
		double x = clamp01(a);
		double y = clamp01(b);
		if (y <= 0.0) {
			return x; // exactly, so the fog without a spell is bit for bit what it was
		}
		if (x <= 0.0) {
			return y;
		}
		return 1.0 - (1.0 - x) * (1.0 - y);
	}

	/** {@link #duskWeight(long, Shape)} with the default timing and this night weight. */
	public static double duskWeight(long timeOfDay, double nightWeight) {
		return duskWeight(timeOfDay, new Shape(Shape.DEFAULT.duskMinFogBlocks(), nightWeight));
	}

	/**
	 * How much of the dusk fog applies at a time of day (0 is sunrise, 12000 sunset), 0 to 1: none by day; from
	 * {@code duskStart} it eases in ({@link #ease}: barely anything for the first minutes) to 1 at {@code duskPeak};
	 * holds to {@code duskHoldUntil}; eases to the night weight by {@code duskNightFrom}; from {@code duskFadeFrom} it
	 * fades, the rise played backwards, to nothing at {@code duskEnd}. Continuous everywhere. The same curve as
	 * atmosphere's client fog ({@code Curves.duskWeight}).
	 */
	public static double duskWeight(long timeOfDay, Shape shape) {
		long t = Math.floorMod(timeOfDay, DAY);
		double night = clamp01(shape.duskNightWeight());
		double p = shape.duskEaseExponent();
		if (t < shape.duskStart() || t >= shape.duskEnd()) {
			return 0.0;
		}
		if (t < shape.duskPeak()) {
			return ease(span(t, shape.duskStart(), shape.duskPeak()), p);
		}
		if (t < shape.duskHoldUntil()) {
			return 1.0;
		}
		if (t < shape.duskNightFrom()) {
			return lerp(ease(1.0 - span(t, shape.duskHoldUntil(), shape.duskNightFrom()), p), night, 1.0);
		}
		if (t < shape.duskFadeFrom()) {
			return night;
		}
		return night * ease(1.0 - span(t, shape.duskFadeFrom(), shape.duskEnd()), p);
	}

	/**
	 * The dusk fog's ease, 0 to 1 over {@code x} 0 to 1: {@code x^p * (p + 1 - p * x)}. It starts like {@code x^p}
	 * (with {@code p} 3, a fifth of the way in it is at 3%) and lands flat on 1. {@code p} 2 is a smoothstep.
	 */
	public static double ease(double x, double p) {
		double v = clamp01(x);
		return clamp01(Math.pow(v, p) * (p + 1.0 - p * v));
	}

	private static double span(long t, long from, long to) {
		return to > from ? (double) (t - from) / (to - from) : 1.0;
	}

	/**
	 * Fog end distance for a fog amount (0 to 1): geometric between {@code base} and {@code minEnd}, never further
	 * out than {@code base}. The same curve as atmosphere's client fog.
	 */
	public static double fogEnd(double base, double minEnd, double amount) {
		if (base <= minEnd || amount <= 0.0) {
			return base;
		}
		return base * Math.pow(minEnd / base, clamp01(amount));
	}

	private static double clamp01(double value) {
		return value < 0.0 ? 0.0 : Math.min(1.0, value);
	}

	private static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}
}
