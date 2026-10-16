package com.forzacode.a1016_02.core.client;

import com.forzacode.a1016_02.core.BlindState;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.player.LocalPlayer;

/**
 * Tells the server when this player is blind (D-077): a counting screen ({@link #counts}) opened or closed. Checks the
 * open screen every client tick and sends {@link BlindState} only when the answer changes, and only to a server that
 * knows the payload. A new player object (a respawn, a dimension change) means the server has reset the moment, so a
 * screen still open then is sent again. Client thread only.
 */
public final class BlindReporter {
	/** What the server was last told. */
	private static boolean sent;
	/** The player object it was told for. */
	private static @Nullable LocalPlayer sentFor;

	private BlindReporter() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BlindReporter::tick);
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(BlindReporter::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(BlindReporter::reset));
	}

	/**
	 * True if this screen keeps the player from watching the world (D-077): the inventory and every container or block
	 * menu ({@link AbstractContainerScreen}: chests, crafting, furnaces, trading, brewing, anvils, the creative inventory
	 * and the like), a book or sign being edited, the advancements, and the pause menu with its own pages (options and
	 * statistics). Not chat (in bed too), the death screen, the debug options, the title screen or a loading screen, and
	 * not the menu-less pause (F3+Esc), where the world is still in view.
	 */
	public static boolean counts(@Nullable Screen screen) {
		if (screen instanceof PauseScreen pause) {
			return pause.showsPauseMenu();
		}
		return screen instanceof AbstractContainerScreen<?>
				|| screen instanceof BookEditScreen
				|| screen instanceof AbstractSignEditScreen
				|| screen instanceof AdvancementsScreen
				|| screen instanceof OptionsScreen
				|| screen instanceof OptionsSubScreen
				|| screen instanceof StatsScreen;
	}

	private static void reset() {
		sent = false;
		sentFor = null;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		if (player != sentFor) {
			sent = false;
			sentFor = player;
		}
		boolean blind = counts(minecraft.gui.screen());
		if (blind != sent && ClientPlayNetworking.canSend(BlindState.TYPE)) {
			ClientPlayNetworking.send(new BlindState(blind));
			sent = blind;
		}
	}
}
