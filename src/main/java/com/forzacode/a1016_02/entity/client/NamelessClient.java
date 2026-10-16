package com.forzacode.a1016_02.entity.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.entity.ScreenshotSpot;
import com.forzacode.a1016_02.entity.sc.NamelessConfig;
import com.forzacode.a1016_02.entity.sc.NamelessPayload;
import com.forzacode.a1016_02.entity.sc.NamelessRules;
import com.forzacode.a1016_02.entity.sc.NamelessSpentPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

/**
 * 02 on the client (D-082): while the server has this client armed ({@link NamelessPayload}), another player's name tag is
 * not drawn while they stand still at the fog edge ({@link NamelessRules#hides}, through {@code AvatarRendererMixin} on
 * vanilla's own name rule). Each remote player's stillness is kept by UUID (their client-side position, moved under
 * {@link NamelessRules#STILL_BLOCKS} since they last stood); the fog is the frame's drawn fog ({@link FogData}), read once a
 * tick. Once names were left undrawn for the arm's whole hide time the client says so ({@link NamelessSpentPayload}) and
 * draws every name again. Nothing the friend sees changes. Client thread only; the rule reads this client's own config.
 */
public final class NamelessClient {
	private static boolean armed;
	private static int hideTicksLeft;
	private static double fogStart = Double.NaN;
	private static double fogEnd = Double.NaN;
	private static final Map<UUID, Still> STILL = new HashMap<>();

	/** Where a remote player last stood still and for how many ticks. */
	private static final class Still {
		Vec3 anchor;
		long ticks;

		Still(Vec3 anchor) {
			this.anchor = anchor;
		}
	}

	private NamelessClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(NamelessPayload.TYPE, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(NamelessClient::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(NamelessClient::reset));
		ClientTickEvents.END_CLIENT_TICK.register(NamelessClient::tick);
	}

	/** True while armed (debug). */
	public static boolean armed() {
		return armed;
	}

	private static void receive(NamelessPayload payload) {
		armed = payload.armed();
		hideTicksLeft = payload.hideTicks();
		if (!armed) {
			STILL.clear();
		}
	}

	private static void reset() {
		armed = false;
		hideTicksLeft = 0;
		fogStart = Double.NaN;
		fogEnd = Double.NaN;
		STILL.clear();
	}

	private static void tick(Minecraft minecraft) {
		if (!armed) {
			return;
		}
		ClientLevel level = minecraft.level;
		LocalPlayer me = minecraft.player;
		if (!SelfCreated.enabled() || level == null || me == null) {
			STILL.clear();
			return;
		}
		readFog(minecraft);
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		NamelessConfig config = NamelessConfig.get();
		Set<UUID> here = new HashSet<>();
		boolean hiding = false;
		for (AbstractClientPlayer other : level.players()) {
			if (other == me) {
				continue;
			}
			UUID uuid = other.getUUID();
			here.add(uuid);
			Vec3 at = other.position();
			Still still = STILL.computeIfAbsent(uuid, k -> new Still(at));
			if (NamelessRules.moved(still.anchor, at)) {
				still.anchor = at;
				still.ticks = 0;
			} else {
				still.ticks++;
			}
			hiding |= NamelessRules.hides(camera.distanceTo(at), fogStart, fogEnd, still.ticks, true, config);
		}
		STILL.keySet().retainAll(here);
		if (hiding && --hideTicksLeft <= 0) {
			armed = false;
			STILL.clear();
			if (ClientPlayNetworking.canSend(NamelessSpentPayload.TYPE)) {
				ClientPlayNetworking.send(NamelessSpentPayload.INSTANCE);
			}
			A1016_02.LOGGER.debug("[a1016] sc_02: spent");
		}
	}

	private static void readFog(Minecraft minecraft) {
		FogData data = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
		if (data == null) {
			fogStart = Double.NaN;
			fogEnd = Double.NaN;
			return;
		}
		ScreenshotSpot.Fog fog = new ScreenshotSpot.Fog(data.environmentalStart, data.environmentalEnd, data.renderDistanceStart, data.renderDistanceEnd);
		fogStart = fog.visibleStart();
		fogEnd = fog.visibleEnd();
	}

	/**
	 * {@code AvatarRendererMixin}: true to leave this avatar's name undrawn now: another player (never this one), armed, the
	 * layer on, standing still since their anchor, in the band past the fog share ({@link NamelessRules#hides}). Cheap: a
	 * flag, then one map lookup.
	 */
	public static boolean hides(Avatar avatar, double distanceToCameraSq) {
		if (!armed || !(avatar instanceof AbstractClientPlayer) || avatar instanceof LocalPlayer || !SelfCreated.enabled()) {
			return false;
		}
		Still still = STILL.get(avatar.getUUID());
		if (still == null || NamelessRules.moved(still.anchor, avatar.position())) {
			return false; // they move: the name is back at once
		}
		return NamelessRules.hides(Math.sqrt(distanceToCameraSq), fogStart, fogEnd, still.ticks, true, NamelessConfig.get());
	}
}
