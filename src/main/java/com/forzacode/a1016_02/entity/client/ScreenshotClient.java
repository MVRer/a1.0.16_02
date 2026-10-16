package com.forzacode.a1016_02.entity.client;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.entity.EntityConfig;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.ModEntities;
import com.forzacode.a1016_02.entity.ScreenshotRules;
import com.forzacode.a1016_02.entity.ScreenshotSpot;
import com.forzacode.a1016_02.entity.ScreenshotStatePayload;
import com.forzacode.a1016_02.entity.ScreenshotTakenPayload;
import com.forzacode.a1016_02.entity.mixin.client.GameRendererAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * The screenshot on the client (D-078). The server arms this client ({@link ScreenshotStatePayload}); the next
 * screenshot (F2) while it is armed and the conditions hold ({@link ScreenshotRules#clientConditions}: weather fog only,
 * no Blindness or Darkness, no surge, no shaderpack) shows him in the saved image only, never on the screen, not for
 * one frame.
 *
 * <p>How the frame never reaches the window: vanilla's F2 ({@code Minecraft.handleGlobalKeyPress}, read while the
 * window's events are polled, after the last frame was shown and before the next one) calls
 * {@code Screenshot.grab(Minecraft, boolean)}, which copies the main render target, the last frame. Here
 * ({@code ScreenshotMixin} at its head) the client instead renders one extra frame into that main render target, the
 * way vanilla's own panorama screenshot does: {@code GameRenderer.update}, {@code extract} and {@code render} with the
 * same delta tracker, so the same partial tick as the frame on the screen, plus his render state added to the level's
 * entity states (a render-only copy of the figure: never added to the level, nothing spawned), and grabs that target
 * for the PNG through vanilla's own {@code Screenshot.grab} (its file name, its chat line). It then renders the normal
 * frame into the target again, so nothing that reads the target before the next frame (a second F2 in the same batch of
 * events) can find him; the world's {@code icon.png} grab, which {@code render} makes while a singleplayer world has
 * none, is held off for a second before the frame with him ({@link GameRendererAccessor}). Nothing is presented: the
 * window keeps showing the frame it shows. The client then tells the server ({@link ScreenshotTakenPayload}) and
 * forgets its arm.
 *
 * <p>If a condition fails, no spot fits ({@link ScreenshotSpot#find}) or the level is not being drawn, vanilla takes
 * the screenshot as always and the arm waits for the next one. If the frame fails, the target is made clean (the
 * normal frame again, else a plain clear) and vanilla takes a normal screenshot; should even that fail, nothing is
 * saved until the next frame. After {@link #MAX_FAILURES} failures in a row this client stops trying until its next
 * arm. Render thread only.
 */
public final class ScreenshotClient {
	/** Failures in a row after which this client stops trying until the server arms it again. */
	public static final int MAX_FAILURES = 3;
	/** The render-only figure's entity id: negative, so it is never one the server hands out. */
	private static final int RENDER_ONLY_ID = -1_016_078;
	private static final Vector4fc CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
	private static boolean armed;
	private static boolean fogOn;
	private static float weatherFogEnd = Float.NaN;
	private static int arm;
	private static int failures;
	private static int gaveUpArm;
	/** The main render target may still hold a frame with him: no screenshot is saved until the next client tick. */
	private static boolean dirty;

	private ScreenshotClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ScreenshotStatePayload.TYPE, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(ScreenshotClient::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(ScreenshotClient::reset));
		// The next frame clears and redraws the main target; a tick always comes before that frame (never between a key
		// press and it), so a target left holding him is clean again by then.
		ClientTickEvents.END_CLIENT_TICK.register(client -> dirty = false);
	}

	/** True while the server has this client armed and not paused, and it has not given up on this arm (for checks). */
	public static boolean armed() {
		return armed && arm != gaveUpArm;
	}

	private static void receive(ScreenshotStatePayload payload) {
		if (payload.arm() != arm) {
			failures = 0; // a new arm (or none): try again
		}
		armed = payload.armed();
		fogOn = payload.fogOn();
		weatherFogEnd = payload.fogEnd();
		arm = payload.arm();
	}

	private static void reset() {
		armed = false;
		fogOn = false;
		weatherFogEnd = Float.NaN;
		arm = 0;
		failures = 0;
		gaveUpArm = 0;
		dirty = false;
	}

	/**
	 * {@code ScreenshotMixin}: the player pressed the screenshot key. True if this was the screenshot (him in the image,
	 * the server told), or if the main target may still hold him: vanilla's grab is skipped. False: vanilla takes it as
	 * always.
	 */
	public static boolean onScreenshotKey(Minecraft minecraft, boolean debugPanoramaRequested) {
		if (dirty) {
			return true; // his frame may still be in the main target: nothing is saved until the next frame redraws it
		}
		if (!armed() || debugPanoramaRequested && SharedConstants.DEBUG_PANORAMA_SCREENSHOT) {
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
			failed(e);
			if (dirty) {
				clean(minecraft);
			}
			return dirty; // a clean target: vanilla takes a normal screenshot; one that may hold him: none this time
		}
		clean(minecraft);
		failures = 0;
		armed = false;
		ClientPlayNetworking.send(ScreenshotTakenPayload.INSTANCE);
		return true;
	}

	private static void failed(RuntimeException e) {
		failures++;
		if (failures >= MAX_FAILURES) {
			gaveUpArm = arm;
			A1016_02.LOGGER.warn("[a1016] the screenshot's frame failed {} times in a row; no more tries until the next arm", failures, e);
		} else {
			A1016_02.LOGGER.warn("[a1016] the screenshot's frame failed; this screenshot is vanilla's", e);
		}
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
		FogData data = renderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
		ScreenshotSpot.Fog fog = data == null ? new ScreenshotSpot.Fog(Double.NaN, Double.NaN, Double.NaN, Double.NaN)
				: new ScreenshotSpot.Fog(data.environmentalStart, data.environmentalEnd, data.renderDistanceStart, data.renderDistanceEnd);
		FogType fluid = camera.getFluidInCamera();
		boolean inFluid = fluid == FogType.WATER || fluid == FogType.LAVA || fluid == FogType.POWDER_SNOW;
		int sky = level.getBrightness(LightLayer.SKY, BlockPos.containing(camera.position()));
		boolean blindOrDark = player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS);
		Optional<String> why = ScreenshotRules.clientConditions(level.dimension() == Level.OVERWORLD, minecraft.gui.screen() != null, inFluid,
				minecraft.options.getCameraType().isFirstPerson(), sky, config.screenshotMinSkyLight, IrisShaders.packInUse(), blindOrDark, fogOn,
				fog.visibleEnd(), weatherFogEnd);
		if (why.isPresent()) {
			return Optional.empty();
		}
		RenderTarget target = renderer.mainRenderTarget();
		double aspect = (double) target.width / Math.max(1, target.height);
		ScreenshotSpot.View view = new ScreenshotSpot.View(camera.position(), camera.yRot(), camera.xRot(), camera.getFov(), aspect, target.height);
		return ScreenshotSpot.find(view, fog, config.screenshotSpot(), (x, z) -> ScreenshotSpot.feetY(level, x, z),
				(from, to) -> ScreenshotSpot.clear(level, from, to), RandomSource.create());
	}

	/**
	 * Renders one frame with him into the main render target and grabs it for the PNG; nothing is presented. False if
	 * the level is not being drawn (nothing was rendered: vanilla then takes the screenshot).
	 */
	private static boolean shoot(Minecraft minecraft, ScreenshotSpot.Spot spot) {
		GameRenderer renderer = minecraft.gameRenderer;
		if (!extract(minecraft)) {
			return false; // nothing drawn into the target: it still holds the frame on the screen
		}
		// The world icon is grabbed inside render() at most once a second while a singleplayer world has none: not him.
		((GameRendererAccessor) renderer).a1016_02$setLastScreenshotAttempt(Util.getMillis());
		GameRenderState state = renderer.gameRenderState();
		List<EntityRenderState> entities = state.levelRenderState.entityRenderStates;
		EntityRenderState him = minecraft.getEntityRenderDispatcher().extractEntity(figure(minecraft.level, spot), 1.0F);
		entities.add(him);
		dirty = true;
		try {
			render(minecraft);
		} finally {
			entities.remove(him);
		}
		Screenshot.grab(minecraft.gameDirectory, renderer.mainRenderTarget(), message -> minecraft.execute(() -> minecraft.showDebugChat(message)));
		return true;
	}

	/** The frame's update and extraction, inside the gizmo collector vanilla's frame opens around them; true if the level is drawn. */
	private static boolean extract(Minecraft minecraft) {
		GameRenderer renderer = minecraft.gameRenderer;
		DeltaTracker delta = minecraft.getDeltaTracker();
		try (Gizmos.TemporaryCollection ignored = minecraft.levelExtractor.collectPerFrameMainThreadGizmos()) {
			renderer.update(delta);
			renderer.extract(delta, true);
		}
		return renderer.gameRenderState().shouldRenderLevel && minecraft.level != null;
	}

	/** The frame's render into the main target, inside the gizmo collector vanilla's frame opens around it. */
	private static void render(Minecraft minecraft) {
		try (Gizmos.TemporaryCollection ignored = minecraft.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			minecraft.gameRenderer.render();
		}
	}

	/**
	 * After the frame with him: renders the normal frame into the main target again (the frame the screen shows), so
	 * nothing that reads the target before the next frame finds him; if that fails, clears the target. Only if both
	 * fail does the target stay {@link #dirty} (no screenshot until the next client tick).
	 */
	private static void clean(Minecraft minecraft) {
		try {
			extract(minecraft);
			render(minecraft);
			dirty = false;
			return;
		} catch (RuntimeException e) {
			A1016_02.LOGGER.warn("[a1016] the screenshot's clean frame failed; clearing the main render target", e);
		}
		try {
			RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			GpuTexture depth = target.getDepthTexture();
			if (depth != null) {
				encoder.clearColorAndDepthTextures(target.getColorTexture(), CLEAR, depth, 1.0);
			} else {
				encoder.clearColorTexture(target.getColorTexture(), CLEAR);
			}
			dirty = false;
		} catch (RuntimeException e) {
			A1016_02.LOGGER.warn("[a1016] the main render target could not be cleared; no screenshot until the next frame", e);
		}
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
