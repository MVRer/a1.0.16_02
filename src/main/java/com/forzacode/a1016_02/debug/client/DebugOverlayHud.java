package com.forzacode.a1016_02.debug.client;

import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.debug.OverlayPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * Dev only: the director overlay, a small panel in the top-left corner fed by {@link OverlayPayload} once per second.
 * Hidden while the F3 screen is open, when the server turns it off, and when no update came for a few seconds.
 */
final class DebugOverlayHud {
	private static final long STALE_MS = 3500;
	private static final int PAD = 3;
	private static final int GAP = 6;
	private static final int MARGIN = 2;
	private static final int BACKGROUND = 0xA0101014;
	private static final int TITLE = 0xFF8C7F99;
	private static final int LABEL = 0xFF9A9AA2;
	private static final int VALUE = 0xFFE6E6E6;

	private static List<String> rows = List.of();
	private static long receivedAt;
	private static boolean on;

	private DebugOverlayHud() {
	}

	static void init() {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return;
		}
		ClientPlayNetworking.registerGlobalReceiver(OverlayPayload.TYPE, (payload, context) -> {
			on = payload.on();
			rows = payload.rows();
			receivedAt = Util.getMillis();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(() -> {
			on = false;
			rows = List.of();
		}));
		HudElementRegistry.addLast(A1016_02.id("debug/overlay"), DebugOverlayHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!on || rows.isEmpty() || Util.getMillis() - receivedAt > STALE_MS) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = client.font;
		// Smaller than the HUD text but still a whole number of screen pixels per font pixel, so it stays sharp.
		int guiScale = Math.max(1, client.getWindow().getGuiScale());
		float scale = guiScale >= 3 ? (guiScale - 1) / (float) guiScale : 1.0F;

		String title = "a1016 director";
		int labelWidth = 0;
		int valueWidth = font.width(title);
		for (String row : rows) {
			int cut = row.indexOf(OverlayPayload.SEPARATOR);
			labelWidth = Math.max(labelWidth, font.width(cut < 0 ? "" : row.substring(0, cut)));
			valueWidth = Math.max(valueWidth, font.width(cut < 0 ? row : row.substring(cut + 1)));
		}
		int lineHeight = font.lineHeight + 1;
		int width = PAD * 2 + labelWidth + GAP + valueWidth;
		int height = PAD * 2 + lineHeight * (rows.size() + 1) - 1;

		graphics.pose().pushMatrix();
		graphics.pose().translate(MARGIN, MARGIN);
		graphics.pose().scale(scale, scale);
		graphics.fill(0, 0, width, height, BACKGROUND);
		graphics.text(font, title, PAD, PAD, TITLE, false);
		int y = PAD + lineHeight;
		for (String row : rows) {
			int cut = row.indexOf(OverlayPayload.SEPARATOR);
			if (cut >= 0) {
				graphics.text(font, row.substring(0, cut), PAD, y, LABEL, false);
				graphics.text(font, row.substring(cut + 1), PAD + labelWidth + GAP, y, VALUE, false);
			} else {
				graphics.text(font, row, PAD, y, VALUE, false);
			}
			y += lineHeight;
		}
		graphics.pose().popMatrix();
	}
}
