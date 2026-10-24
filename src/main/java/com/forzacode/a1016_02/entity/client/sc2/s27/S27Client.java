package com.forzacode.a1016_02.entity.client.sc2.s27;

import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.entity.ScreenshotSpot;
import com.forzacode.a1016_02.entity.sc2.s27.CopyRules;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27EndPayload;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Payload;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Rules;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Feature 27 of the self-created layer's second wave (docs/SELF_CREATED_2.md), entity's client part: the copy of another
 * player that the server shows this client alone ({@link Sc27Payload}). Nothing of it exists on the server, so nobody else
 * sees it and nothing can touch it. It is put into this client's level ({@link FriendCopy}) only once its spot is off the
 * screen (never popping in where the viewer looks; the dev gallery's stand-in at once), after the other player's skin had a
 * moment to load; it gives up if that does not happen within {@link #WAIT_MAX} ticks. Then {@link Showing} runs it.
 * {@link Sc27EndPayload} ends it the copy's way (walking off if seen). One at a time; everything is forgotten when the
 * connection changes. The same for a host and a guest. Client thread only.
 */
public final class S27Client {
	/** Waiting to be put there at most this long (ticks). */
	static final int WAIT_MAX = 20 * 12;
	/** Waiting this long at least for the other player's skin, unless it is in already (ticks). */
	static final int SKIN_WAIT = 12;
	/** The stand-in's name in the dev gallery. */
	static final String STAND_IN_NAME = "Friend";

	private static @Nullable Showing showing;

	private S27Client() {
	}

	/** Called once from its workstream's second-wave client init. */
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Sc27Payload.TYPE, (payload, context) -> receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(Sc27EndPayload.TYPE, (payload, context) -> end(payload.key()));
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(S27Client::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(S27Client::reset));
		ClientTickEvents.END_CLIENT_TICK.register(S27Client::tick);
	}

	/**
	 * The dev gallery's stand-in keeps its name while the HUD is hidden (the gallery shoots with it hidden; vanilla's F1 hides
	 * every name), within vanilla's name range. False for anything else.
	 */
	public static boolean standInName(Avatar avatar) {
		return avatar instanceof FriendCopy copy && copy.standIn() && Minecraft.getInstance().gui.hud.isHidden()
				&& Minecraft.getInstance().gameRenderer.mainCamera().position().distanceTo(copy.position()) < Sc27Rules.NAME_RANGE;
	}

	/** A showing now (debug, tests of the client by hand). */
	public static boolean showing() {
		return showing != null && showing.phase != Showing.Phase.GONE;
	}

	private static void receive(Sc27Payload payload) {
		boolean standIn = payload.staged() && FabricLoader.getInstance().isDevelopmentEnvironment();
		if (payload.staged() && !standIn) {
			return; // a stand-in outside a development environment: never
		}
		Showing current = showing;
		if (current != null && current.copy != null && !current.copy.isRemoved()) {
			if (!standIn) {
				return; // one at a time: the server sends no second while the first is on
			}
			current.copy.discard();
		}
		showing = new Showing(payload, standIn, Minecraft.getInstance().level);
	}

	private static void end(int key) {
		Showing current = showing;
		if (current != null && (key == 0 || key == current.payload.key())) {
			if (current.copy == null) {
				showing = null; // never put there: nothing to see go
			} else {
				current.ended = true;
			}
		}
	}

	private static void reset() {
		Showing current = showing;
		if (current != null && current.copy != null && !current.copy.isRemoved()) {
			current.copy.discard();
		}
		showing = null;
	}

	private static void tick(Minecraft minecraft) {
		Showing current = showing;
		if (current == null) {
			return;
		}
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			showing = null;
			return;
		}
		if (current.copy != null) {
			if (current.copy.isRemoved() || current.copy.level() != level || current.phase == Showing.Phase.GONE) {
				if (!current.copy.isRemoved()) {
					current.copy.discard();
				}
				showing = null;
			}
			return;
		}
		if (current.level != null && current.level != level) {
			showing = null; // the viewer changed level before it stood: it never stands in the new one (R12-38)
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		current.waited++;
		PlayerInfo info = info(minecraft, current);
		if (info == null || current.waited > WAIT_MAX || current.ended) {
			showing = null; // they left, it never found a moment off the screen, or it was ended first
			return;
		}
		boolean skinIn = info.getSkin() != DefaultPlayerSkin.get(info.getProfile()); // asking starts the skin's load
		Vec3 spot = current.payload.spot();
		if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(spot))) {
			return;
		}
		if (!current.standIn) {
			if (current.waited < SKIN_WAIT && !skinIn) {
				return;
			}
			ScreenshotSpot.View view = Showing.view(minecraft);
			if (CopyRules.onScreen(view, spot, CopyRules.APPEAR_MARGIN)) {
				return; // never popping in where the viewer looks: there when they turn
			}
		}
		put(level, current, info);
	}

	private static @Nullable PlayerInfo info(Minecraft minecraft, Showing current) {
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null) {
			return null;
		}
		UUID who = current.standIn && minecraft.player != null ? minecraft.player.getUUID() : current.payload.other();
		return connection.getPlayerInfo(who);
	}

	/** Puts the copy into the level, dressed as the other player is, standing at its spot facing the viewer. */
	private static void put(ClientLevel level, Showing current, PlayerInfo info) {
		Sc27Payload p = current.payload;
		String name = current.standIn ? STAND_IN_NAME : info.getProfile().name();
		FriendCopy copy = new FriendCopy(level, new GameProfile(UUID.randomUUID(), name), p.other(), current.standIn, current);
		List<ItemStack> items = p.items();
		EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < slots.length && i < items.size(); i++) {
			copy.setItemSlot(slots[i], items.get(i).copy());
		}
		copy.parts(p.parts());
		copy.setMainArm(p.leftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
		current.placed(copy);
		level.addEntity(copy);
	}
}
