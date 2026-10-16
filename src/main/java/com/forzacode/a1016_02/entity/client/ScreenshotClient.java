package com.forzacode.a1016_02.entity.client;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.FogReport;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.ModEntities;
import com.forzacode.a1016_02.entity.ScreenshotRules;
import com.forzacode.a1016_02.entity.ScreenshotSpot;
import com.forzacode.a1016_02.entity.ScreenshotStatePayload;
import com.forzacode.a1016_02.entity.ScreenshotTakenPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * The screenshot on the client (D-078). The server arms this client ({@link ScreenshotStatePayload}); the next
 * screenshot (F2) while it is armed and the conditions hold ({@link ScreenshotRules#clientConditions}) shows him in the
 * saved image only, never on the screen, not for one frame.
 *
 * <p>How the frame never reaches the window: vanilla's F2 ({@code Minecraft.handleGlobalKeyPress}, read while the
 * window's events are polled, before the frame) calls {@code Screenshot.grab(Minecraft, boolean)}, which copies the
 * main render target, the last frame. Here ({@code ScreenshotMixin} at its head) the client instead renders one extra
 * frame into that main render target, the way vanilla's own panorama screenshot does: {@code GameRenderer.update},
 * {@code extract} and {@code render} with the same delta tracker, so the same partial tick as the frame on the screen,
 * plus his render state added to the level's entity states (a render-only copy of the figure: never added to the
 * level, nothing spawned), and grabs that target for the PNG through vanilla's own {@code Screenshot.grab} (its file
 * name, its chat line). Nothing is presented: the window keeps showing the frame it shows, and the next frame
 * renders that target again from scratch (it clears it before it draws) before it is blitted to the window, so he
 * is never on the screen. The client then tells the server ({@link ScreenshotTakenPayload}) and forgets its arm.
 *
 * <p>If a condition fails, no spot fits ({@link ScreenshotSpot#find}) or the level is not being drawn, vanilla takes
 * the screenshot as always and the arm waits for the next one. Render thread only.
 */
public final class ScreenshotClient {
	/** The render-only figure's entity id: negative, so it is never one the server hands out. */
	private static final int RENDER_ONLY_ID = -1_016_078;
	private static boolean armed;
	private static boolean fogOn;

	private ScreenshotClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ScreenshotStatePayload.TYPE, (payload, context) -> {
			armed = payload.armed();
			fogOn = payload.fogOn();
		});
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(ScreenshotClient::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(ScreenshotClient::reset));
	}

	/** True while the server has this client armed and not paused (for the dev overlay and checks). */
	public static boolean armed() {
		return armed;
	}

	private static void reset() {
		armed = false;
		fogOn = false;
	}

	/**
	 * {@code ScreenshotMixin}: the player pressed the screenshot key. True if this was the screenshot (him in the image,
	 * the server told): vanilla's grab is skipped. False: vanilla takes it as always.
	 */
	public static boolean onScreenshotKey(Minecraft minecraft, boolean debugPanoramaRequested) {
		if (!armed || debugPanoramaRequested && SharedConstants.DEBUG_PANORAMA_SCREENSHOT) {
			return false;
		}
		Optional<ScreenshotSpot.Spot> spot = spot(minecraft);
		if (spot.isEmpty() || !ClientPlayNetworking.canSend(ScreenshotTakenPayload.TYPE)) {
			return false;
		}
		try {
			if (!shoot(minecraft, spot.get())) {
				return false;
			}
		} catch (RuntimeException e) {
			// Never fall back to vanilla's grab here: the main target may hold half of his frame.
			A1016_02.LOGGER.warn("[a1016] the screenshot's frame failed; no screenshot this time", e);
			return true;
		}
		armed = false;
		ClientPlayNetworking.send(ScreenshotTakenPayload.INSTANCE);
		return true;
	}

	/** The conditions, then the spot; empty if either fails (the arm waits). */
	private static Optional<ScreenshotSpot.Spot> spot(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return Optional.empty();
		}
		EntityConfig config = EntityConfig.get();
		GameRenderer renderer = minecraft.gameRenderer;
		Camera camera = renderer.mainCamera();
		FogData fog = renderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
		double fogEnd = fog == null ? Double.NaN : FogReport.visibleEnd(fog.environmentalEnd, fog.renderDistanceEnd);
		FogType fluid = camera.getFluidInCamera();
		boolean inFluid = fluid == FogType.WATER || fluid == FogType.LAVA || fluid == FogType.POWDER_SNOW;
		int sky = level.getBrightness(LightLayer.SKY, BlockPos.containing(camera.position()));
		Optional<String> why = ScreenshotRules.clientConditions(level.dimension() == Level.OVERWORLD, minecraft.gui.screen() != null, inFluid,
				minecraft.options.getCameraType().isFirstPerson(), sky, fogEnd, fogOn, config.screenshotMinSkyLight, config.screenshotMaxFogEnd);
		if (why.isPresent()) {
			return Optional.empty();
		}
		double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
		ScreenshotSpot.View view = new ScreenshotSpot.View(camera.position(), camera.yRot(), camera.xRot(), camera.getFov(), aspect);
		return ScreenshotSpot.find(view, fogEnd, config.screenshotSpot(), (x, z) -> ScreenshotSpot.feetY(level, x, z),
				(from, to) -> ScreenshotSpot.clear(level, from, to), RandomSource.create());
	}

	/**
	 * Renders one frame with him into the main render target and grabs it for the PNG; nothing is presented. False if
	 * the level is not being drawn (vanilla then takes the screenshot).
	 */
	private static boolean shoot(Minecraft minecraft, ScreenshotSpot.Spot spot) {
		GameRenderer renderer = minecraft.gameRenderer;
		DeltaTracker delta = minecraft.getDeltaTracker();
		// The frame's extraction and render, inside the gizmo collectors vanilla's frame opens around them.
		try (Gizmos.TemporaryCollection ignored = minecraft.levelExtractor.collectPerFrameMainThreadGizmos()) {
			renderer.update(delta);
			renderer.extract(delta, true);
		}
		GameRenderState state = renderer.gameRenderState();
		if (!state.shouldRenderLevel || minecraft.level == null) {
			return false; // nothing drawn into the target: it still holds the frame on the screen
		}
		List<EntityRenderState> entities = state.levelRenderState.entityRenderStates;
		EntityRenderState him = minecraft.getEntityRenderDispatcher().extractEntity(figure(minecraft.level, spot), 1.0F);
		entities.add(him);
		try (Gizmos.TemporaryCollection ignored = minecraft.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			renderer.render();
		} finally {
			entities.remove(him);
		}
		Screenshot.grab(minecraft.gameDirectory, renderer.mainRenderTarget(), message -> minecraft.execute(() -> minecraft.showDebugChat(message)));
		return true;
	}

	/**
	 * The render-only copy of the figure: standing, facing the camera, the default skin, the configured eyes, no name.
	 * Never added to the level; it only lends the renderer its render state.
	 */
	private static HimEntity figure(ClientLevel level, ScreenshotSpot.Spot spot) {
		HimEntity him = new HimEntity(ModEntities.HIM, level);
		him.setId(RENDER_ONLY_ID); // the level gives ids to the entities it adds; this one is never added
		Vec3 feet = spot.feet();
		him.snapTo(feet.x, feet.y, feet.z, spot.yaw(), 0.0F);
		him.setOldPosAndRot();
		him.setYHeadRot(spot.yaw());
		him.setYBodyRot(spot.yaw());
		him.yHeadRotO = spot.yaw();
		him.yBodyRotO = spot.yaw();
		return him;
	}
}
