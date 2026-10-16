package com.forzacode.a1016_02.entity.client;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.GlimpseRules;
import com.forzacode.a1016_02.entity.HimEntity;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3fc;

/**
 * The glimpse's hide latch (D-073): the renderer stops drawing a figure that vanishes in view, for good, the first
 * frame his center is within {@code glimpseHideDegrees} of the camera's look or within {@code glimpseHideBlocks} of
 * it ({@link GlimpseRules#hiddenOnClient}), whatever the server says after. Kept by UUID, so a figure sent again (he
 * left tracking and came back) stays hidden; forgotten when the connection changes. Render thread only.
 */
final class GlimpseHide {
	/** At most this many hidden figures are remembered (one is out at a time; this is only a bound). */
	private static final int MAX_REMEMBERED = 64;
	private static final Set<UUID> HIDDEN = new LinkedHashSet<>();

	private GlimpseHide() {
	}

	static void init() {
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(HIDDEN::clear));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(HIDDEN::clear));
	}

	/**
	 * True if this figure is not drawn this frame: it vanishes in view and the latch has closed, now or before. Asked
	 * every frame for every figure in the client's level, before the frustum, so the latch never misses a frame.
	 */
	static boolean hidden(HimEntity him, double camX, double camY, double camZ, float partialTick) {
		if (!him.vanishesInView()) {
			return false;
		}
		UUID id = him.getUUID();
		if (HIDDEN.contains(id)) {
			return true;
		}
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vector3fc forward = camera.forwardVector();
		Vec3 look = new Vec3(forward.x(), forward.y(), forward.z());
		Vec3 feet = him.getPosition(partialTick);
		Vec3 center = feet.add(0.0, him.getBbHeight() * 0.5, 0.0);
		EntityConfig config = EntityConfig.get();
		if (!GlimpseRules.hiddenOnClient(new Vec3(camX, camY, camZ), look, center, config.glimpseHideDegrees(), config.glimpseHideBlocks)) {
			return false;
		}
		if (HIDDEN.size() >= MAX_REMEMBERED) {
			HIDDEN.remove(HIDDEN.iterator().next());
		}
		HIDDEN.add(id);
		return true;
	}
}
