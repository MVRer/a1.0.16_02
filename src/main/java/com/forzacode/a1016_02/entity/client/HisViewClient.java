package com.forzacode.a1016_02.entity.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.entity.mixin.client.SectionOcclusionGraphAccessor;
import com.forzacode.a1016_02.entity.sc.HisView;
import com.forzacode.a1016_02.entity.sc.HisViewConfig;
import com.forzacode.a1016_02.entity.sc.HisViewPayload;
import com.forzacode.a1016_02.entity.sc.HisViewRules;
import com.forzacode.a1016_02.entity.sc.HisViewTakenPayload;
import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 03 on the host's client (D-081). The server arms it with a figure ({@link HisViewPayload}); once that figure has stood
 * still {@code standSeconds} here, the client renders extra frames into the main render target, never presented, the way
 * vanilla's panorama does ({@code GameRenderer.update}, {@code extract} and {@code renderLevel}, so no GUI), from his eyes
 * toward the host's eyes ({@code CameraMixin}: detached, so the host's own model is drawn), his own model left out
 * ({@code HimRenderer} asks {@link #hiding}), no hand or HUD ({@code GuiRenderState.isHudHidden}), no block outline, the
 * normal fog. Sections the player never saw from where he stands are not compiled yet and a culled graph from his camera
 * leaves them out (the dev client showed holes after 3 frames), so these frames draw without occlusion culling
 * ({@code Minecraft.smartCull} off for them), every section whole (no fade-in), and go on, waiting between them for vanilla's
 * section graph to be rebuilt for that camera ({@link SectionOcclusionGraphAccessor}) and for the sections in view to compile,
 * from {@link #MIN_FRAMES} until nothing is left to compile, at most {@link #FRAMES} or {@link #SETTLE_MS} (the dev client:
 * 8 frames, about 0.7 s, a pause the game makes once per world). It grabs the last one ({@code Screenshot.takeScreenshot}),
 * then renders a normal frame again and waits for the graph once more, so the next frame on the screen is the player's own. The grab is cropped and scaled as vanilla's world icon (the middle square, 64 x
 * 64, {@code NativeImage.resizeSubRectTo}); off the render thread the world's current {@code icon.png} is copied to
 * {@link HisViewRules#BEFORE} (only if that copy does not exist), the picture written as {@code icon.png}, both through
 * {@link SelfCreated#writeInWorld} (whole or not at all), and the server told ({@link HisViewTakenPayload}). A world with no
 * {@code icon.png} yet, a shaderpack (Iris), a server that cannot take the report or a frame that fails end the arm (the
 * next such sighting arms it again); a screen open waits while he stands. Render thread only.
 */
public final class HisViewClient {
	/** At most this many unpresented frames are rendered from his eyes; the last one is the picture. */
	public static final int FRAMES = 40;
	/** At least this many, so the section graph is rebuilt for his camera and its sections are drawn. */
	static final int MIN_FRAMES = 3;
	/** The frames stop once this long has passed (milliseconds), whatever is still compiling: the game waits that long at most. */
	static final long SETTLE_MS = 1500L;
	/** The longest wait for vanilla's section graph between frames, milliseconds. */
	static final long GRAPH_WAIT_MS = 200L;
	/** The longest wait for the sections in view to compile between frames, milliseconds. */
	static final long SECTIONS_WAIT_MS = 40L;
	/** A figure not on this client this many ticks after the arm is not coming: the arm ends. */
	static final int FIGURE_WAIT_TICKS = 600;
	/** He stands still while he moved less than this (blocks) since the anchor. */
	static final double STILL_BLOCKS = 0.05;

	/** The camera for the unpresented frames: his eyes, and the look toward the host's. */
	public record View(Vec3 eye, float yaw, float pitch) {
	}

	private static int armedId;
	private static int armedTicks;
	private static @Nullable Vec3 anchor;
	private static long stillTicks;
	/** A picture is being taken (the grab or the files are pending): nothing else is tried meanwhile. */
	private static boolean busy;
	private static @Nullable View override;
	private static int hidingId;
	/** The last picture's frames and milliseconds (the log line). */
	private static int lastFrames;
	private static long lastMillis;

	private HisViewClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(HisViewPayload.TYPE, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(HisViewClient::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(HisViewClient::reset));
		ClientTickEvents.END_CLIENT_TICK.register(HisViewClient::tick);
	}

	/** {@code CameraMixin}: the camera while an unpresented frame is rendered, else null (every frame on the screen). */
	public static @Nullable View cameraOverride() {
		return override;
	}

	/** {@code HimRenderer}: true for the figure the unpresented frames look from (his own model is left out of them). */
	public static boolean hiding(HimEntity him) {
		return override != null && him.getId() == hidingId;
	}

	/** The figure this client is armed with (0: none), for debug. */
	public static int armed() {
		return armedId;
	}

	private static void receive(HisViewPayload payload) {
		if (busy) {
			return; // a picture is being written
		}
		armedId = payload.entityId();
		armedTicks = 0;
		anchor = null;
		stillTicks = 0;
	}

	private static void reset() {
		armedId = 0;
		armedTicks = 0;
		anchor = null;
		stillTicks = 0;
		override = null;
		hidingId = 0;
		busy = false;
	}

	private static void forget(String why) {
		A1016_02.LOGGER.debug("[a1016] sc_03: not now ({})", why);
		armedId = 0;
		anchor = null;
		stillTicks = 0;
	}

	private static void tick(Minecraft minecraft) {
		if (armedId == 0 || busy) {
			return;
		}
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (!SelfCreated.enabled()) {
			forget("the layer is off");
			return;
		}
		if (level == null || player == null) {
			return;
		}
		armedTicks++;
		Entity entity = level.getEntity(armedId);
		if (!(entity instanceof HimEntity him) || him.isRemoved()) {
			if (armedTicks > FIGURE_WAIT_TICKS || entity != null) {
				forget("he is not here");
			}
			return;
		}
		Vec3 at = him.position();
		if (anchor == null || anchor.distanceToSqr(at) >= STILL_BLOCKS * STILL_BLOCKS) {
			anchor = at;
			stillTicks = 0;
			return;
		}
		if (++stillTicks < HisViewConfig.get().standTicks()) {
			return;
		}
		IntegratedServer server = minecraft.getSingleplayerServer();
		Optional<String> ends = ends(minecraft, level, server);
		if (ends.isPresent()) {
			forget(ends.get());
			return;
		}
		if (minecraft.gui.screen() != null || !player.isAlive()) {
			return; // waits while he stands
		}
		take(minecraft, him, player, SelfCreated.worldFolder(server));
	}

	/** What ends the arm without a picture: no host's server, not the overworld, a shaderpack, no report, no picture yet. */
	private static Optional<String> ends(Minecraft minecraft, ClientLevel level, @Nullable IntegratedServer server) {
		if (server == null) {
			return Optional.of("not the host");
		}
		if (level.dimension() != Level.OVERWORLD) {
			return Optional.of("not the overworld");
		}
		if (IrisShaders.packInUse()) {
			return Optional.of("a shaderpack draws the frame");
		}
		if (!ClientPlayNetworking.canSend(HisViewTakenPayload.TYPE)) {
			return Optional.of("the server cannot take the report");
		}
		Optional<Path> icon = SelfCreated.inWorld(SelfCreated.worldFolder(server), HisViewRules.ICON);
		if (icon.isEmpty() || !Files.isRegularFile(icon.get())) {
			return Optional.of("the world has no picture yet");
		}
		return Optional.empty();
	}

	private static void take(Minecraft minecraft, HimEntity him, LocalPlayer host, Path folder) {
		Vec3 eye = him.getEyePosition();
		float[] look = HisViewRules.look(eye, host.getEyePosition());
		View view = new View(eye, look[0], look[1]);
		busy = true;
		boolean shot;
		try {
			shot = shoot(minecraft, view, him.getId(), image -> written(minecraft, folder, image));
		} catch (RuntimeException e) {
			A1016_02.LOGGER.warn("[a1016] sc_03: the frame failed", e);
			busy = false;
			forget("the frame failed");
			return;
		}
		if (!shot) {
			busy = false;
			forget("the level is not being drawn");
		}
	}

	/**
	 * The unpresented frames from {@code view}, the grab handed to {@code out} once the GPU has it, then a normal frame and the
	 * graph's rebuild for the player's camera. False if the level is not being drawn (nothing grabbed).
	 */
	private static boolean shoot(Minecraft minecraft, View view, int figureId, Consumer<NativeImage> out) {
		GameRenderer renderer = minecraft.gameRenderer;
		boolean smartCull = minecraft.smartCull;
		long start = Util.getMillis();
		override = view;
		hidingId = figureId;
		try {
			renderer.setRenderBlockOutline(false);
			// No occlusion culling for these frames: the sections the player never saw from where he stands have no compiled
			// visibility yet, and a culled graph would leave them out (the dev client showed holes).
			minecraft.smartCull = false;
			for (int frame = 0; frame < FRAMES; frame++) {
				lastFrames = frame + 1;
				if (frame > 0) {
					waitForGraph(minecraft);
					waitForSections(minecraft);
				}
				if (!frameFromHim(minecraft)) {
					return false;
				}
				if (frame + 1 >= MIN_FRAMES && (graphDone(minecraft) && minecraft.levelRenderer.hasRenderedAllSections()
						|| Util.getMillis() - start >= SETTLE_MS)) {
					break;
				}
			}
			lastMillis = Util.getMillis() - start;
			Screenshot.takeScreenshot(renderer.mainRenderTarget(), out);
			return true;
		} finally {
			override = null;
			hidingId = 0;
			minecraft.smartCull = smartCull;
			renderer.setRenderBlockOutline(true); // as vanilla's panorama leaves it
			clean(minecraft);
		}
	}

	/** One unpresented frame from his eyes: no hand or HUD, every section drawn whole (no fade-in). False if the level is not drawn. */
	private static boolean frameFromHim(Minecraft minecraft) {
		if (!extract(minecraft)) {
			return false;
		}
		GameRenderer renderer = minecraft.gameRenderer;
		renderer.gameRenderState().guiRenderState.isHudHidden = true;
		renderer.gameRenderState().optionsRenderState.chunkSectionFadeInTime = 0.0;
		renderLevel(minecraft);
		return true;
	}

	private static boolean graphDone(Minecraft minecraft) {
		Future<?> task = ((SectionOcclusionGraphAccessor) minecraft.levelRenderer.sectionOcclusionGraph()).a1016_02$fullUpdateTask();
		return task == null || task.isDone();
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

	private static void renderLevel(Minecraft minecraft) {
		try (Gizmos.TemporaryCollection ignored = minecraft.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			minecraft.gameRenderer.renderLevel();
		}
	}

	/** A normal frame into the main target, then the graph rebuilt for the player's camera before the next frame is shown. */
	private static void clean(Minecraft minecraft) {
		try {
			extract(minecraft);
			try (Gizmos.TemporaryCollection ignored = minecraft.levelRenderer.collectPerFrameRenderThreadGizmos()) {
				minecraft.gameRenderer.render();
			}
			waitForGraph(minecraft);
		} catch (RuntimeException e) {
			A1016_02.LOGGER.warn("[a1016] sc_03: the normal frame after it failed", e);
		}
	}

	/** Waits (at most {@link #GRAPH_WAIT_MS}) for vanilla's background rebuild of the section graph, if one runs. */
	private static void waitForGraph(Minecraft minecraft) {
		Future<?> task = ((SectionOcclusionGraphAccessor) minecraft.levelRenderer.sectionOcclusionGraph()).a1016_02$fullUpdateTask();
		if (task == null || task.isDone()) {
			return;
		}
		try {
			task.get(GRAPH_WAIT_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException | ExecutionException ignored) {
			// The frame goes on with the graph there is; the wait is only for a cleaner picture.
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Waits (at most {@link #SECTIONS_WAIT_MS}) for the sections waiting to compile; the next frame uploads them. */
	private static void waitForSections(Minecraft minecraft) {
		long until = Util.getMillis() + SECTIONS_WAIT_MS;
		while (!minecraft.levelRenderer.hasRenderedAllSections() && Util.getMillis() < until) {
			try {
				Thread.sleep(5L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/**
	 * The grab (render thread, once the GPU has it): cropped and scaled as vanilla's world icon, then written off the render
	 * thread ({@link HisView#writePicture}); the server is told on success. Any failure ends the arm.
	 */
	private static void written(Minecraft minecraft, Path folder, NativeImage frame) {
		int side = HisViewConfig.get().iconSize();
		int[] pixels;
		try (frame; NativeImage icon = new NativeImage(side, side, false)) {
			int[] crop = HisViewRules.crop(frame.getWidth(), frame.getHeight());
			frame.resizeSubRectTo(crop[0], crop[1], crop[2], crop[2], icon);
			pixels = new int[side * side];
			for (int y = 0; y < side; y++) {
				for (int x = 0; x < side; x++) {
					pixels[y * side + x] = icon.getPixel(x, y);
				}
			}
		} catch (RuntimeException e) {
			A1016_02.LOGGER.warn("[a1016] sc_03: the grab could not be read", e);
			minecraft.execute(() -> {
				busy = false;
				forget("the grab could not be read");
			});
			return;
		}
		byte[] png = HisViewRules.png(side, side, pixels);
		Util.ioPool().execute(() -> {
			Optional<String> failed = HisView.writePicture(folder, png);
			minecraft.execute(() -> {
				busy = false;
				if (failed.isPresent()) {
					forget(failed.get());
					return;
				}
				armedId = 0;
				anchor = null;
				stillTicks = 0;
				if (ClientPlayNetworking.canSend(HisViewTakenPayload.TYPE)) {
					ClientPlayNetworking.send(HisViewTakenPayload.INSTANCE);
				}
				A1016_02.LOGGER.info("[a1016] sc_03: done ({} frames, {} ms)", lastFrames, lastMillis);
			});
		});
	}
}
