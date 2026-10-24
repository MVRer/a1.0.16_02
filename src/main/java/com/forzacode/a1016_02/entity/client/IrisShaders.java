package com.forzacode.a1016_02.entity.client;

import java.lang.reflect.Method;

import com.forzacode.a1016_02.A1016_02;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether an Iris shaderpack draws the frame (D-078: the screenshot waits then; {@link HimEyesLayer} draws the eyes
 * through vanilla's eyes render type then, SC2-V's V-1). A shaderpack's temporal effects (TAA's
 * history, motion blur) carry what the last frame held into the next one, so the extra frame with him could reach the
 * screen. Read through Iris's public API ({@code net.irisshaders.iris.api.v0.IrisApi}) by reflection, without a
 * compile dependency: false when Iris is not loaded; true when it is and a pack is in use, or when its API cannot be
 * read (unknown counts as in use: the arm waits, the eyes stay vanilla's). Render thread.
 */
final class IrisShaders {
	private static final String MOD_ID = "iris";
	private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
	private static Method getInstance;
	private static Method inUse;
	private static boolean broken;

	private IrisShaders() {
	}

	static boolean packInUse() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return false;
		}
		if (broken) {
			return true;
		}
		try {
			if (getInstance == null || inUse == null) {
				Class<?> api = Class.forName(API);
				getInstance = api.getMethod("getInstance");
				inUse = api.getMethod("isShaderPackInUse");
			}
			return Boolean.TRUE.equals(inUse.invoke(getInstance.invoke(null)));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			broken = true;
			A1016_02.LOGGER.warn("[a1016] Iris is loaded but its API cannot be read; the screenshot waits while it is", e);
			return true;
		}
	}
}
