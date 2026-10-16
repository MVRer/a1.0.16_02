package com.forzacode.a1016_02.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A fog place (D-085): fog weather over one spot for a while, beside the world's {@link FogSpell}. It is thickest at
 * {@code center} and thins out to nothing at {@code radius} (horizontal distance, any height), and it eases in over its
 * ramp from {@code fromClock} and out over its fade before {@code untilClock}, with the dusk fog's ease. Times are on
 * the overworld clock, absolute {@code getDefaultClockTime()} ticks like the spell's, so client and server agree. Places
 * are weather: the server weights them by the sky light at the player's eyes as it does the spell
 * ({@link FogLimits#of(net.minecraft.server.level.ServerPlayer)}), so there is none in a tunnel. Stored in
 * {@code HerobrineState.Effects.fogPlaces} and sent to every client ({@link ClientEffects#addFogPlace}); several
 * combine as {@code 1 - Π(1 - a)} ({@link FogLimits#placeAmount}).
 *
 * <p>The constructor never lets {@code untilClock} fall before {@code fromClock}, puts {@code level} in 0 to 1 (0 if
 * NaN), the ramp and fade at 0 or more and the radius at 0 or more (0 if NaN); a place with no dimension is the
 * overworld's, and one whose center is missing or not finite gives no fog anywhere (its radius is 0). Every amount is
 * continuous in the position and the clock, so nothing pops at a place's edge, start or end.
 *
 * @param dimension  the level it is in; it gives nothing anywhere else
 * @param center     where it is thickest (only x and z count)
 * @param radius     it gives nothing this far (horizontally) from the center or farther, in blocks
 * @param level      how heavy it is at the center at full, 0 to 1, on the dusk fog level's scale
 * @param fromClock  the clock tick it starts easing in
 * @param untilClock the clock tick it is gone by (it has eased out by then)
 * @param rampTicks  it eases in over this many ticks from {@code fromClock}
 * @param fadeTicks  and out over this many before {@code untilClock}
 */
public record FogPlace(ResourceKey<Level> dimension, Vec3 center, float radius, float level, long fromClock, long untilClock, int rampTicks,
		int fadeTicks) {
	/** The time ease's exponent ({@link FogLimits#ease}): the dusk fog's and the spell's 3. */
	public static final double TIME_EXPONENT = 3.0;
	/** The space ease's exponent ({@link FogLimits#ease}): 2, a smoothstep, so the edge is soft. */
	public static final double SPACE_EXPONENT = 2.0;

	public static final Codec<FogPlace> CODEC = RecordCodecBuilder.create(i -> i.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(FogPlace::dimension),
			Vec3.CODEC.fieldOf("center").forGetter(FogPlace::center),
			Codec.FLOAT.fieldOf("radius").forGetter(FogPlace::radius),
			Codec.FLOAT.fieldOf("level").forGetter(FogPlace::level),
			Codec.LONG.fieldOf("from").forGetter(FogPlace::fromClock),
			Codec.LONG.fieldOf("until").forGetter(FogPlace::untilClock),
			Codec.INT.optionalFieldOf("ramp", 0).forGetter(FogPlace::rampTicks),
			Codec.INT.optionalFieldOf("fade", 0).forGetter(FogPlace::fadeTicks)
	).apply(i, FogPlace::new));
	/** Every field, so the client draws the server's place exactly. */
	public static final StreamCodec<ByteBuf, FogPlace> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), FogPlace::dimension,
			Vec3.STREAM_CODEC, FogPlace::center,
			ByteBufCodecs.FLOAT, FogPlace::radius,
			ByteBufCodecs.FLOAT, FogPlace::level,
			ByteBufCodecs.VAR_LONG, FogPlace::fromClock,
			ByteBufCodecs.VAR_LONG, FogPlace::untilClock,
			ByteBufCodecs.VAR_INT, FogPlace::rampTicks,
			ByteBufCodecs.VAR_INT, FogPlace::fadeTicks,
			FogPlace::new);

	public FogPlace {
		dimension = dimension != null ? dimension : Level.OVERWORLD;
		boolean finite = center != null && Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z);
		center = finite ? center : Vec3.ZERO;
		radius = !finite || Float.isNaN(radius) ? 0.0F : Math.max(0.0F, radius);
		level = Float.isNaN(level) ? 0.0F : Math.clamp(level, 0.0F, 1.0F);
		untilClock = Math.max(untilClock, fromClock);
		rampTicks = Math.max(0, rampTicks);
		fadeTicks = Math.max(0, fadeTicks);
	}

	/**
	 * The fog it gives at {@code at} in {@code dim} at this clock tick, 0 to {@link #level}: {@link #timeAmount} times
	 * {@link #spaceAmount}; 0 in another dimension. {@code clockTicks} may carry the partial tick.
	 */
	public double amount(Vec3 at, ResourceKey<Level> dim, double clockTicks) {
		if (!dimension.equals(dim)) {
			return 0.0;
		}
		double time = timeAmount(clockTicks);
		return time > 0.0 ? time * spaceAmount(at) : 0.0;
	}

	/**
	 * How much of it there is at this clock tick, anywhere: {@code level} times the smaller of the ease in
	 * ({@link FogLimits#ease} over {@code rampTicks} from {@code fromClock}) and the ease out (over {@code fadeTicks}
	 * back from {@code untilClock}), exponent {@link #TIME_EXPONENT}; 0 outside it. A ramp or fade of 0 is a step.
	 */
	public double timeAmount(double clockTicks) {
		if (clockTicks < fromClock || clockTicks >= untilClock) {
			return 0.0;
		}
		double in = rampTicks > 0 ? FogLimits.ease((clockTicks - fromClock) / rampTicks, TIME_EXPONENT) : 1.0;
		double out = fadeTicks > 0 ? FogLimits.ease((untilClock - clockTicks) / fadeTicks, TIME_EXPONENT) : 1.0;
		return level * Math.min(in, out);
	}

	/**
	 * How much of it reaches {@code at}, 0 to 1: {@code ease(1 - d / radius, 2)} on the horizontal distance {@code d}
	 * from the center ({@link #SPACE_EXPONENT}), 1 at the center, 0 at the radius and past it.
	 */
	public double spaceAmount(Vec3 at) {
		if (radius <= 0.0F) {
			return 0.0;
		}
		double dx = at.x - center.x;
		double dz = at.z - center.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		return d >= radius ? 0.0 : FogLimits.ease(1.0 - d / radius, SPACE_EXPONENT);
	}

	/** Whether the clock is inside it: from {@code fromClock} (included) to {@code untilClock} (excluded). */
	public boolean activeAt(long clockTicks) {
		return clockTicks >= fromClock && clockTicks < untilClock;
	}

	/** Whether it is over at this clock tick (nothing of it is left). */
	public boolean endedBy(long clockTicks) {
		return clockTicks >= untilClock;
	}

	/** Whether it is fading out at this clock tick, or over: within its last {@code fadeTicks}. */
	public boolean fadingBy(long clockTicks) {
		return clockTicks >= untilClock - fadeTicks;
	}

	/**
	 * This place ended early with its own fade from {@code clockTicks}: gone by {@code clockTicks + fadeTicks}, or as
	 * before if it ends sooner. The amount at {@code clockTicks} is unchanged (the ease out is still whole there), so
	 * nothing pops; one that has not started yet ends before it starts and never gives fog.
	 */
	public FogPlace endedEarly(long clockTicks) {
		long until = Math.min(untilClock, clockTicks + fadeTicks);
		return new FogPlace(dimension, center, radius, level, fromClock, until, rampTicks, fadeTicks);
	}
}
