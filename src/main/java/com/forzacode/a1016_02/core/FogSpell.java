package com.forzacode.a1016_02.core;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A fog spell (D-071): a stretch of fog weather for the whole world, a fog night, its morning carry-over or a fog day.
 * Times are on the overworld clock, absolute {@code getDefaultClockTime()} ticks (not the time of day), so client and
 * server agree and sleeping through a fog night lands in its morning. It eases in over its ramp from {@code fromClock}
 * and out over its fade before {@code untilClock} ({@link #amount}), with the dusk fog's ease, and its amount combines
 * with the dusk fog ({@link FogLimits#combine}). Stored in {@code HerobrineState.Effects.fogSpell} and sent to every
 * client ({@link ClientEffects#setFogSpell}); {@link FogWeatherRules} rolls them.
 *
 * <p>The ease is the spell's own ({@code rampTicks}, {@code fadeTicks}, {@code exponent}): set from the fog weather
 * settings when the spell is made (the four-argument constructor takes the installed ones,
 * {@link FogWeatherRules#settings()}) and carried with it to every client and into the save, so the client draws
 * exactly the fog the server computes even when the two sides' configs differ. A spell saved without them (v1.2.0)
 * takes the installed settings when it loads.
 *
 * <p>The constructor never lets {@code untilClock} fall before {@code fromClock}, puts {@code level} in 0 to 1 (0 if
 * NaN), the ramp and fade at 0 or more and the exponent in 1 to 8 (3 if NaN), as {@link FogWeatherRules.Settings} does.
 *
 * @param fromClock  the clock tick it starts easing in
 * @param untilClock the clock tick it is gone by (it has eased out by then)
 * @param level      how heavy it is at full, 0 to 1, on the dusk fog level's scale (at a 12-chunk view 0.45 ends the fog at
 *                   about 75 blocks, 0.8 at about 37)
 * @param quiet      a quiet spell gives the director no boost, so a fog night is never a sure sign
 * @param rampTicks  it eases in over this many ticks from {@code fromClock}
 * @param fadeTicks  and out over this many before {@code untilClock}
 * @param exponent   the ease's exponent ({@link FogLimits#ease}, the dusk fog's 3)
 */
public record FogSpell(long fromClock, long untilClock, float level, boolean quiet, long rampTicks, long fadeTicks, double exponent) {
	public static final Codec<FogSpell> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("from").forGetter(FogSpell::fromClock),
			Codec.LONG.fieldOf("until").forGetter(FogSpell::untilClock),
			Codec.FLOAT.fieldOf("level").forGetter(FogSpell::level),
			Codec.BOOL.optionalFieldOf("quiet", false).forGetter(FogSpell::quiet),
			Codec.LONG.optionalFieldOf("ramp").forGetter(s -> Optional.of(s.rampTicks())),
			Codec.LONG.optionalFieldOf("fade").forGetter(s -> Optional.of(s.fadeTicks())),
			Codec.DOUBLE.optionalFieldOf("exponent").forGetter(s -> Optional.of(s.exponent()))
	).apply(i, FogSpell::loaded));
	/** Every field, the ease included, so the client draws the server's ease. */
	public static final StreamCodec<ByteBuf, FogSpell> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, FogSpell::fromClock,
			ByteBufCodecs.VAR_LONG, FogSpell::untilClock,
			ByteBufCodecs.FLOAT, FogSpell::level,
			ByteBufCodecs.BOOL, FogSpell::quiet,
			ByteBufCodecs.VAR_LONG, FogSpell::rampTicks,
			ByteBufCodecs.VAR_LONG, FogSpell::fadeTicks,
			ByteBufCodecs.DOUBLE, FogSpell::exponent,
			FogSpell::new);

	public FogSpell {
		untilClock = Math.max(untilClock, fromClock);
		level = Float.isNaN(level) ? 0.0F : Math.clamp(level, 0.0F, 1.0F);
		rampTicks = Math.max(0L, rampTicks);
		fadeTicks = Math.max(0L, fadeTicks);
		exponent = Double.isNaN(exponent) ? 3.0 : Math.clamp(exponent, 1.0, 8.0);
	}

	/** A spell with these settings' ramp, fade and ease exponent. */
	public FogSpell(long fromClock, long untilClock, float level, boolean quiet, FogWeatherRules.Settings settings) {
		this(fromClock, untilClock, level, quiet, settings.rampTicks(), settings.fadeTicks(), settings.easeExponent());
	}

	/** A spell with the installed settings' ramp, fade and ease exponent ({@link FogWeatherRules#settings()}). */
	public FogSpell(long fromClock, long untilClock, float level, boolean quiet) {
		this(fromClock, untilClock, level, quiet, FogWeatherRules.settings());
	}

	/** The codec's: a spell saved without its ease (v1.2.0) takes the installed settings' now. */
	private static FogSpell loaded(long fromClock, long untilClock, float level, boolean quiet, Optional<Long> ramp, Optional<Long> fade,
			Optional<Double> exponent) {
		if (ramp.isPresent() && fade.isPresent() && exponent.isPresent()) {
			return new FogSpell(fromClock, untilClock, level, quiet, ramp.get(), fade.get(), exponent.get());
		}
		FogWeatherRules.Settings settings = FogWeatherRules.settings();
		return new FogSpell(fromClock, untilClock, level, quiet, ramp.orElse(settings.rampTicks()), fade.orElse(settings.fadeTicks()),
				exponent.orElse(settings.easeExponent()));
	}

	/** This spell over another stretch of the clock (the same level, quiet and ease). */
	public FogSpell withClocks(long from, long until) {
		return new FogSpell(from, until, level, quiet, rampTicks, fadeTicks, exponent);
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
	 * How much fog the spell gives at this clock tick, 0 to {@link #level}, with its own ease: {@code level} times the
	 * smaller of the ease in ({@link FogLimits#ease} over {@code rampTicks} from {@code fromClock}) and the ease out (over
	 * {@code fadeTicks} back from {@code untilClock}); 0 outside the spell. Continuous everywhere, a spell shorter than
	 * its ramp and fade included (it never reaches full). {@code clockTicks} may carry the partial tick. A ramp or fade of
	 * 0 is a step. Client and server get the same number from the same spell.
	 */
	public double amount(double clockTicks) {
		return amount(clockTicks, rampTicks, fadeTicks, exponent);
	}

	/**
	 * {@link #amount(double)}: the spell's own ease. The settings are not read: a spell carries the ease it was made
	 * with, so every caller computes the same fog (the form is kept so older calls compile and agree).
	 */
	public double amount(double clockTicks, FogWeatherRules.Settings settings) {
		return amount(clockTicks);
	}

	/** The pure ease: the amount with this ramp, fade and exponent instead of the spell's own (a ramp or fade of 0 or less is a step). */
	public double amount(double clockTicks, double rampTicks, double fadeTicks, double exponent) {
		if (clockTicks < fromClock || clockTicks >= untilClock) {
			return 0.0;
		}
		double in = rampTicks > 0.0 ? FogLimits.ease((clockTicks - fromClock) / rampTicks, exponent) : 1.0;
		double out = fadeTicks > 0.0 ? FogLimits.ease((untilClock - clockTicks) / fadeTicks, exponent) : 1.0;
		return level * Math.min(in, out);
	}

	/**
	 * The one rule for "is there fog now" (D-071) for everyone who gates on it (the director's boost, daytime sightings):
	 * true while the spell gives fog and its amount is at least the installed {@link FogWeatherRules.Settings#fogGateShare}
	 * (0.5) of its level, so a spell's first ticks, its last ones and the tail of a carry-over do not count.
	 */
	public boolean counts(long clockTicks) {
		return counts(clockTicks, FogWeatherRules.settings().fogGateShare());
	}

	/** {@link #counts(long)} with this share of the level (0 to 1). */
	public boolean counts(long clockTicks, double share) {
		double fog = amount(clockTicks);
		return fog > 0.0 && fog >= Math.clamp(Double.isNaN(share) ? 0.0 : share, 0.0, 1.0) * level;
	}
}
