package com.forzacode.a1016_02.entity.client;

import com.forzacode.a1016_02.entity.FogEndPayload;
import com.forzacode.a1016_02.entity.FogReport;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;

/**
 * Reports the fog end this client draws to the server (D-035), so he stands where the player can actually see him.
 * It reads the frame's final fog: vanilla's render-distance and environmental fog (water, lava, blindness, weather,
 * the Nether's), after atmosphere's dusk fog and surges have pulled it in. Sends about every half second, and at once
 * when it moves by more than {@link FogReport#CHANGE_BLOCKS}. Client thread only.
 */
final class FogEndReporter {
	private static float lastSent = Float.NaN;
	private static int ticksSinceSent;

	private FogEndReporter() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(FogEndReporter::tick);
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(FogEndReporter::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(FogEndReporter::reset));
	}

	private static void reset() {
		lastSent = Float.NaN;
		ticksSinceSent = 0;
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		ticksSinceSent++;
		// The fog the last frame was drawn with; atmosphere's FogRendererMixin has already applied the dusk fog to it.
		FogData fog = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
		if (fog == null) {
			return;
		}
		float end = FogReport.visibleEnd(fog.environmentalEnd, fog.renderDistanceEnd);
		if (!FogReport.shouldSend(lastSent, end, ticksSinceSent) || !ClientPlayNetworking.canSend(FogEndPayload.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new FogEndPayload(end));
		lastSent = end;
		ticksSinceSent = 0;
	}
}
