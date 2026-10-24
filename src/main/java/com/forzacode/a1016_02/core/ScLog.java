package com.forzacode.a1016_02.core;

import com.forzacode.a1016_02.A1016_02;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

/**
 * The self-created layer's WARN and ERROR lines for something that threw (D-080, R13-5). A stack trace is a log line
 * too, and its frames name the layer's classes, so a WARN or ERROR line of the layer never carries one: it is the
 * caller's own message (neutral words and opaque ids, held by the spoiler test like every line of the layer), then, in
 * brackets, the exception's simple name when it is the JDK's or the game's, else the neutral word {@link #NEUTRAL}. The
 * full stack trace goes to DEBUG under the same message (a development run's {@code debug.log} keeps it; the launcher's
 * own log stops at INFO, so the player's {@code latest.log} never shows a frame).
 *
 * <p>Use it wherever the layer would hand a throwable to the logger: {@code ScLog.error("[a1016] s2: a {} failed in
 * chunk {}", kind, chunk, e)} instead of {@code A1016_02.LOGGER.error(...)}. The format is SLF4J's ({@code {}}
 * placeholders filled from {@code args}); a trailing throwable that no placeholder takes is the one logged. Any thread.
 */
public final class ScLog {
	/** The word that stands for an exception whose own name could name something (one of the mod's own classes). */
	public static final String NEUTRAL = "error";

	private ScLog() {
	}

	/** A WARN line that names nothing; the trace at DEBUG. */
	public static void warn(String format, @Nullable Object... args) {
		FormattingTuple tuple = MessageFormatter.arrayFormat(format, args);
		A1016_02.LOGGER.warn(line(tuple));
		trace(tuple);
	}

	/** An ERROR line that names nothing; the trace at DEBUG. */
	public static void error(String format, @Nullable Object... args) {
		FormattingTuple tuple = MessageFormatter.arrayFormat(format, args);
		A1016_02.LOGGER.error(line(tuple));
		trace(tuple);
	}

	/** The line {@link #warn} and {@link #error} write at their level, for these arguments. */
	public static String line(String format, @Nullable Object... args) {
		return line(MessageFormatter.arrayFormat(format, args));
	}

	/**
	 * How a thrown exception is named in a line: its simple name if its class is not one of the mod's own (the JDK's and
	 * the game's names say nothing about the layer), else {@link #NEUTRAL}; null for none.
	 */
	public static @Nullable String kind(@Nullable Throwable thrown) {
		if (thrown == null) {
			return null;
		}
		Class<?> type = thrown.getClass();
		String simple = type.getSimpleName();
		if (simple.isEmpty() || type.getName().startsWith("com.forzacode.")) {
			return NEUTRAL;
		}
		return simple;
	}

	private static String line(FormattingTuple tuple) {
		String message = tuple.getMessage() == null ? "" : tuple.getMessage();
		String kind = kind(tuple.getThrowable());
		return kind == null ? message : message + " (" + kind + ")";
	}

	private static void trace(FormattingTuple tuple) {
		if (tuple.getThrowable() != null && A1016_02.LOGGER.isDebugEnabled()) {
			A1016_02.LOGGER.debug(tuple.getMessage(), tuple.getThrowable());
		}
	}
}
