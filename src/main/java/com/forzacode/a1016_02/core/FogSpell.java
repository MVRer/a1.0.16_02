package com.forzacode.a1016_02.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A fog spell (D-071): a stretch of fog weather for the whole world, a fog night, its morning carry-over or a fog day.
 * Times are on the overworld clock, absolute {@code getDefaultClockTime()} ticks (not the time of day), so client and
 * server agree and sleeping through a fog night lands in its morning. It eases in over the ramp from {@code fromClock}
 * and out over the fade before {@code untilClock} ({@link #amount}), with the dusk fog's ease, and its amount combines
 * with the dusk fog ({@link FogLimits#combine}). Stored in {@code HerobrineState.Effects.fogSpell} and sent to every
 * client ({@link ClientEffects#setFogSpell}); {@link FogWeatherRules} rolls them.
 *
 * <p>The constructor never lets {@code untilClock} fall before {@code fromClock} and puts {@code level} in 0 to 1 (0
 * if NaN).
 *
 * @param fromClock  the clock tick it starts easing in
 * @param untilClock the clock tick it is gone by (it has eased out by then)
 * @param level      how heavy it is at full, 0 to 1, on the dusk fog level's scale (at a 12-chunk view 0.45 ends the fog at
 *                   about 75 blocks, 0.8 at about 37)
 * @param quiet      a quiet spell gives the director no boost, so a fog night is never a sure sign
 */
public record FogSpell(long fromClock, long untilClock, float level, boolean quiet) {
	public static final Codec<FogSpell> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("from").forGetter(FogSpell::fromClock),
			Codec.LONG.fieldOf("until").forGetter(FogSpell::untilClock),
			Codec.FLOAT.fieldOf("level").forGetter(FogSpell::level),
			Codec.BOOL.optionalFieldOf("quiet", false).forGetter(FogSpell::quiet)
	).apply(i, FogSpell::new));
	public static final StreamCodec<ByteBuf, FogSpell> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, FogSpell::fromClock,
			ByteBufCodecs.VAR_LONG, FogSpell::untilClock,
			ByteBufCodecs.FLOAT, FogSpell::level,
			ByteBufCodecs.BOOL, FogSpell::quiet,
			FogSpell::new);

	public FogSpell {
		untilClock = Math.max(untilClock, fromClock);
		level = Float.isNaN(level) ? 0.0F : Math.clamp(level, 0.0F, 1.0F);
	}

	/** Whether the clock is inside the spell: from {@code fromClock} (included) to {@code untilClock} (excluded). */
	public boolean activeAt(long clockTicks) {
		return clockTicks >= fromClock && clockTicks < untilClock;
	}

	/** Whether the spell is over at this clock tick (nothing of it is left). */
	public boolean endedBy(long clockTicks) {
		return clockTicks >= untilClock;
	}

	/**
	 * How much fog the spell gives at this clock tick, 0 to {@link #level}: {@code level} times the smaller of the ease
	 * in ({@link FogLimits#ease} over {@code rampTicks} from {@code fromClock}) and the ease out (over
	 * {@code fadeTicks} back from {@code untilClock}); 0 outside the spell. Continuous everywhere, a spell shorter than
	 * its ramp and fade included (it never reaches full). {@code clockTicks} may carry the partial tick. A ramp or fade
	 * of 0 or less is a step.
	 */
	public double amount(double clockTicks, double rampTicks, double fadeTicks, double exponent) {
		if (clockTicks < fromClock || clockTicks >= untilClock) {
			return 0.0;
		}
		double in = rampTicks > 0.0 ? FogLimits.ease((clockTicks - fromClock) / rampTicks, exponent) : 1.0;
		double out = fadeTicks > 0.0 ? FogLimits.ease((untilClock - clockTicks) / fadeTicks, exponent) : 1.0;
		return level * Math.min(in, out);
	}

	/** {@link #amount(double, double, double, double)} with these settings' ramp, fade and ease exponent. */
	public double amount(double clockTicks, FogWeatherRules.Settings settings) {
		return amount(clockTicks, settings.rampTicks(), settings.fadeTicks(), settings.easeExponent());
	}

	/** {@link #amount(double, double, double, double)} with the installed settings ({@link FogWeatherRules#settings()}). */
	public double amount(double clockTicks) {
		return amount(clockTicks, FogWeatherRules.settings());
	}
}
