package com.forzacode.a1016_02.core;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.forzacode.a1016_02.A1016_02;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * FIX-FINAL, after SC2-V (docs/SELF_CREATED_2.md, "Verified (SC2-V) and FIX-FINAL"): the mod's own render pipelines
 * step aside for an Iris shaderpack (V-1), and the tests' log capture keeps the mod's lines in the log files (V-3).
 */
public class VerifySc2FinalTests {
	/** What builds a pipeline or a render type of the mod's own, in code (comments and strings blanked). */
	private static final List<String> OWN_PIPELINE = List.of("RenderPipeline.builder(", "withFragmentShader(", "withVertexShader(", "RenderTypeInvoker.");
	/** What asks whether a shaderpack draws the frame. */
	private static final List<String> PACK_CHECK = List.of("IrisShaders.packInUse()", "ShaderPacks.inUse()");
	/** The mod's shaders as they were checked under Iris + Sodium + Complementary (SC2-V, FIX-FINAL). */
	private static final List<String> SHADERS_CHECKED = List.of("entity/him_eyes.fsh");

	/**
	 * V-1: Iris draws a pipeline it was never told about outside the pack's buffers (an ERROR in the log and a stray line
	 * on screen). Every class that builds a pipeline or render type of its own asks whether a pack is in use (and then
	 * draws through vanilla's), and the mod's shaders are the ones checked under a pack: a new one needs the same check
	 * ({@code ./gradlew runDevGallery -Pgallery.iris}, docs "How to use the gallery").
	 */
	@GameTest
	public void everyPipelineOfTheModsOwnStepsAsideForAShaderpack(GameTestHelper helper) throws Exception {
		Path sources = VerifyScSpoilerTests.sourceRoot();
		helper.assertTrue(sources != null, "the mod's sources (src/main/java) were not found");
		List<String> own = new ArrayList<>();
		List<String> unchecked = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(sources)) {
			for (Path file : walk.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
				String code = VerifySc2SideTests.code(Files.readString(file));
				if (file.getFileName().toString().equals("RenderTypeInvoker.java") || OWN_PIPELINE.stream().noneMatch(code::contains)) {
					continue;
				}
				String rel = sources.relativize(file).toString().replace('\\', '/');
				own.add(rel);
				if (PACK_CHECK.stream().noneMatch(code::contains)) {
					unchecked.add(rel);
				}
			}
		}
		helper.assertTrue(own.contains("com/forzacode/a1016_02/entity/client/HimEyesLayer.java"), "the scan missed the eyes' pipeline: " + own);
		helper.assertTrue(unchecked.isEmpty(), "a pipeline of the mod's own is drawn under a shaderpack too: " + unchecked);

		Path shaders = sources.getParent().resolve("resources/assets/a1016_02/shaders");
		List<String> found = new ArrayList<>();
		if (Files.isDirectory(shaders)) {
			try (Stream<Path> walk = Files.walk(shaders)) {
				walk.filter(Files::isRegularFile).forEach(p -> found.add(shaders.relativize(p).toString().replace('\\', '/')));
			}
		}
		Collections.sort(found);
		helper.assertTrue(found.equals(SHADERS_CHECKED), "the mod's shaders changed; check them under Iris (runDevGallery -Pgallery.iris) and update this list: "
				+ found);

		// Without Iris (the test server has none) no pack is in use, so the eyes keep their own pass.
		Class<?> iris = Class.forName("com.forzacode.a1016_02.entity.client.IrisShaders");
		Method inUse = iris.getDeclaredMethod("packInUse");
		inUse.setAccessible(true);
		helper.assertTrue(Boolean.FALSE.equals(inUse.invoke(null)), "a shaderpack in use without Iris");
		helper.succeed();
	}

	/**
	 * V-3: a line of the mod's logger reaches the root's appenders (the log files, the console) before, during and after
	 * a capture, nested ones too, and the logger's config is as it was once the last one closes.
	 */
	@GameTest
	public void theModsLinesStillReachTheLogFilesDuringAndAfterACapture(GameTestHelper helper) {
		LoggerContext context = ((Logger) LogManager.getLogger(A1016_02.MOD_ID)).getContext();
		LoggerConfig before = context.getConfiguration().getLoggerConfig(A1016_02.MOD_ID);
		String token = UUID.randomUUID().toString().substring(0, 8);
		List<String> atRoot = Collections.synchronizedList(new ArrayList<>());
		AbstractAppender probe = new AbstractAppender("a1016-root-probe-" + token, null, null, true, Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				String message = event.getMessage().getFormattedMessage();
				if (message.contains(token)) {
					atRoot.add(message.substring(message.indexOf(':') + 2, message.indexOf(token) - 1));
				}
			}
		};
		probe.start();
		LoggerConfig root = context.getConfiguration().getRootLogger();
		root.addAppender(probe, null, null);
		try {
			A1016_02.LOGGER.info("[a1016] probe: before {}", token);
			try (LogCapture capture = LogCapture.open()) {
				A1016_02.LOGGER.info("[a1016] probe: inside {}", token);
				helper.assertTrue(capture.shown().stream().anyMatch(l -> l.message().contains("inside " + token)), "the capture missed its line");
			}
			A1016_02.LOGGER.info("[a1016] probe: after {}", token);
			try (LogCapture outer = LogCapture.open()) {
				try (LogCapture inner = LogCapture.open()) {
					A1016_02.LOGGER.info("[a1016] probe: nested {}", token);
					helper.assertTrue(probes(inner, token) == 1 && probes(outer, token) == 1, "the nested captures: " + inner.shown() + " / " + outer.shown());
				}
				A1016_02.LOGGER.info("[a1016] probe: outer {}", token);
				helper.assertTrue(probes(outer, token) == 2, "the outer capture after the inner closed: " + outer.shown());
			}
			A1016_02.LOGGER.info("[a1016] probe: last {}", token);
		} finally {
			root.removeAppender(probe.getName());
			probe.stop();
		}
		helper.assertTrue(atRoot.equals(List.of("before", "inside", "after", "nested", "outer", "last")), "what reached the root's appenders: " + atRoot);
		LoggerConfig after = context.getConfiguration().getLoggerConfig(A1016_02.MOD_ID);
		helper.assertTrue(after.getName().equals(before.getName()) && after.isAdditive() == before.isAdditive(),
				"the logger's config was " + before.getName() + " (additive " + before.isAdditive() + "), is " + after.getName() + " (additive " + after.isAdditive() + ")");
		helper.succeed();
	}

	/** The capture's lines of this probe (other threads may log the mod's lines meanwhile). */
	private static long probes(LogCapture capture, String token) {
		return capture.shown().stream().filter(l -> l.message().contains(token)).count();
	}
}
