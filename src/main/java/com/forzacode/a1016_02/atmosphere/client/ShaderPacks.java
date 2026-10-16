package com.forzacode.a1016_02.atmosphere.client;

import java.lang.reflect.Method;

import com.forzacode.a1016_02.A1016_02;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether an Iris shaderpack draws the frame. A pack draws the night from the game's own moon phase (its uniform reads
 * the {@code MOON_PHASE} environment attribute, not the sky's render state), so the sky's moon is left as vanilla's
 * while one is in use ({@link ClientAtmosphere#moonPhase}). Read through Iris's public API
 * ({@code net.irisshaders.iris.api.v0.IrisApi}) by reflection, without a compile dependency, as the entity's
 * screenshot does: false when Iris is not loaded; true when it is and a pack is in use, or when its API cannot be read
 * (unknown counts as in use). No client classes. Render thread.
 */
public final class ShaderPacks {
	private static final String MOD_ID = "iris";
	private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
	private static Method getInstance;
	private static Method inUse;
	private static boolean broken;

	private ShaderPacks() {
	}

	public static boolean inUse() {
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
			A1016_02.LOGGER.warn("[a1016] Iris is loaded but its API cannot be read; a shader pack counts as in use", e);
			return true;
		}
	}
}
