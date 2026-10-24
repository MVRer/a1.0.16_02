package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.jspecify.annotations.Nullable;

/**
 * Tests: every event of the mod's logger while it is open, as the log files would get it (level, the formatted message,
 * the throwable whose frames a file would print). Open it in a try-with-resources around the code under test.
 * <p>The mod's lines still reach the log files and the console while it is open and after it closes (SC2-V's V-3).
 * log4j's {@code Logger.addAppender} gives a logger with no config of its own a new one that copies the root's
 * additivity, which is false: on its own it cut the mod's logger off the root's appenders for the rest of the run. So
 * the first capture in sets that config additive and the last one out removes it again.
 */
public final class LogCapture implements AutoCloseable {
	/** One event. */
	public record Line(Level level, String message, @Nullable Throwable thrown) {
		/** At INFO or above (what the player's latest.log keeps). */
		public boolean shown() {
			return level.isMoreSpecificThan(Level.INFO);
		}
	}

	private static final Object LOCK = new Object();
	/** Captures open now (LOCK). */
	private static int open;
	/** Whether the mod's logger config is the captures' own, to be removed by the last one out (LOCK). */
	private static boolean ownConfig;

	private final List<Line> lines = Collections.synchronizedList(new ArrayList<>());
	private final Logger logger;
	private final AbstractAppender appender;

	private LogCapture() {
		logger = (Logger) LogManager.getLogger(A1016_02.MOD_ID);
		appender = new AbstractAppender("a1016-capture-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				lines.add(new Line(event.getLevel(), event.getMessage().getFormattedMessage(), event.getThrown()));
			}
		};
		appender.start();
		synchronized (LOCK) {
			Configuration config = logger.getContext().getConfiguration();
			boolean had = A1016_02.MOD_ID.equals(config.getLoggerConfig(A1016_02.MOD_ID).getName());
			logger.addAppender(appender);
			if (!had) {
				ownConfig = true;
			}
			LoggerConfig own = config.getLoggerConfig(A1016_02.MOD_ID);
			if (ownConfig && !own.isAdditive()) {
				own.setAdditive(true);
				logger.getContext().updateLoggers();
			}
			open++;
		}
	}

	public static LogCapture open() {
		return new LogCapture();
	}

	/** Every event so far, oldest first (a copy). */
	public List<Line> lines() {
		synchronized (lines) {
			return List.copyOf(lines);
		}
	}

	/** The events at INFO or above. */
	public List<Line> shown() {
		return lines().stream().filter(Line::shown).toList();
	}

	@Override
	public void close() {
		synchronized (LOCK) {
			logger.removeAppender(appender);
			if (--open == 0 && ownConfig) {
				ownConfig = false;
				logger.getContext().getConfiguration().removeLogger(A1016_02.MOD_ID);
				logger.getContext().updateLoggers();
			}
		}
		appender.stop();
	}
}
