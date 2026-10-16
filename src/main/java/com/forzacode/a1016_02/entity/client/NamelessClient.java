package com.forzacode.a1016_02.entity.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.entity.ScreenshotSpot;
import com.forzacode.a1016_02.entity.sc.NamelessConfig;
import com.forzacode.a1016_02.entity.sc.NamelessPayload;
import com.forzacode.a1016_02.entity.sc.NamelessReachPayload;
import com.forzacode.a1016_02.entity.sc.NamelessRules;
import com.forzacode.a1016_02.entity.sc.NamelessSpentPayload;
import com.mojang.datafixers.util.Either;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;

/**
 * 02 on the client (D-082): while the server has this client armed ({@link NamelessPayload}), another player's name tag is
 * not drawn while they stand still at the fog edge ({@link NamelessRules#hides}, through {@code AvatarRendererMixin} on
 * vanilla's own name rule), and their dot is left off vanilla's locator bar for as long ({@code ClientWaypointManagerMixin}:
 * the bar draws as vanilla's does for a player it is not sent; with no other dot it gives way to the experience bar, as
 * vanilla's does). Never while someone else stands near this player ({@link NamelessRules#witnessed}: they would see the
 * name). Each remote player's stillness is kept by UUID (their client-side position, moved under
 * {@link NamelessRules#STILL_BLOCKS} since they last stood); the fog is the frame's drawn fog ({@link FogData}), read once
 * a tick. The hide time counts only ticks in which a name vanilla would have drawn on screen was left out; once it has
 * run out the client says so ({@link NamelessSpentPayload}) and draws every name again. While the layer is on and another
 * player is here, the client tells the server where its fog lets a band start ({@link NamelessReachPayload}), armed or
 * not, so the card fits only where this client can hide a name. Nothing the friend sees changes. Client thread only; the
 * rule reads this client's own config.
 */
public final class NamelessClient {
	private static boolean armed;
	private static int hideTicksLeft;
	private static ScreenshotSpot.@Nullable Fog fog;
	private static final Map<UUID, Still> STILL = new HashMap<>();
	/** Remote players within {@code friendMinAtFire} of the camera this tick, and their distance. */
	private static final Map<UUID, Double> NEAR = new HashMap<>();
	/** Remote players the rule hides this tick, on screen or not (the locator bar). */
	private static final Set<UUID> HIDDEN = new HashSet<>();
	/** A name vanilla would have drawn on screen was left out since the last tick ({@code AvatarRendererMixin}). */
	private static boolean hidOnScreen;
	private static float lastReach = Float.NaN;
	private static int ticksSinceReach;

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
			clearHides();
		}
	}

	private static void reset() {
		armed = false;
		hideTicksLeft = 0;
		fog = null;
		lastReach = Float.NaN;
		ticksSinceReach = 0;
		clearHides();
	}

	private static void clearHides() {
		STILL.clear();
		NEAR.clear();
		HIDDEN.clear();
		hidOnScreen = false;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer me = minecraft.player;
		if (level == null || me == null) {
			return;
		}
		boolean on = SelfCreated.enabled();
		NamelessConfig config = NamelessConfig.get();
		readFog(minecraft);
		if (on) {
			report(level, config);
		}
		if (!armed) {
			return;
		}
		if (!on) {
			clearHides();
			return;
		}
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		Set<UUID> here = new HashSet<>();
		NEAR.clear();
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
			double distance = camera.distanceTo(at);
			if (distance < config.friendMinAtFire) {
				NEAR.put(uuid, distance);
			}
		}
		STILL.keySet().retainAll(here);
		HIDDEN.clear();
		for (AbstractClientPlayer other : level.players()) {
			if (other != me && hidesNow(other, camera, config)) {
				HIDDEN.add(other.getUUID());
			}
		}
		boolean counted = hidOnScreen && !minecraft.isPaused();
		hidOnScreen = false;
		if (counted && --hideTicksLeft <= 0) {
			armed = false;
			clearHides();
			if (ClientPlayNetworking.canSend(NamelessSpentPayload.TYPE)) {
				ClientPlayNetworking.send(NamelessSpentPayload.INSTANCE);
			}
			A1016_02.LOGGER.debug("[a1016] sc_02: spent");
		}
	}

	/** The rule for one remote player now: still since their anchor, nobody else near this player, in the band ({@link NamelessRules#hides}). */
	private static boolean hidesNow(Entity other, Vec3 camera, NamelessConfig config) {
		Still still = STILL.get(other.getUUID());
		if (still == null || NamelessRules.moved(still.anchor, other.position())) {
			return false; // they move: the name is back at once
		}
		if (NamelessRules.witnessed(nearestOther(other.getUUID()), config)) {
			return false;
		}
		Vec3 middle = other.position().add(0.0, other.getBbHeight() / 2.0, 0.0);
		return NamelessRules.hides(middle.subtract(camera), fog, still.ticks, true, config);
	}

	private static double nearestOther(UUID who) {
		double nearest = Double.POSITIVE_INFINITY;
		for (Map.Entry<UUID, Double> near : NEAR.entrySet()) {
			if (!near.getKey().equals(who)) {
				nearest = Math.min(nearest, near.getValue());
			}
		}
		return nearest;
	}

	private static void readFog(Minecraft minecraft) {
		FogData data = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
		fog = data == null ? null : new ScreenshotSpot.Fog(data.environmentalStart, data.environmentalEnd, data.renderDistanceStart, data.renderDistanceEnd);
	}

	/** Tells the server where this client's fog lets a band start, while another player is here (alone, nothing is sent). */
	private static void report(ClientLevel level, NamelessConfig config) {
		ticksSinceReach++;
		if (fog == null || level.players().size() < 2) {
			return;
		}
		float reach = (float) NamelessRules.reach(fog, config);
		if (!NamelessRules.sendReach(lastReach, reach, ticksSinceReach) || !ClientPlayNetworking.canSend(NamelessReachPayload.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new NamelessReachPayload(reach));
		lastReach = reach;
		ticksSinceReach = 0;
	}

	/**
	 * {@code AvatarRendererMixin}: true to leave this avatar's name undrawn now: another player (never this one), armed, the
	 * layer on, standing still since their anchor, nobody else near this player, in the band ({@link NamelessRules#hides}).
	 * Cheap: a flag, then a map lookup or two.
	 */
	public static boolean hides(Avatar avatar, double distanceToCameraSq) {
		if (!armed || !(avatar instanceof AbstractClientPlayer) || avatar instanceof LocalPlayer || !SelfCreated.enabled()) {
			return false;
		}
		return hidesNow(avatar, Minecraft.getInstance().gameRenderer.mainCamera().position(), NamelessConfig.get());
	}

	/** {@code AvatarRendererMixin}: a name vanilla would have drawn on screen was left out; this tick counts toward the hide time. */
	public static void hiddenOnScreen() {
		hidOnScreen = true;
	}

	/** {@code ClientWaypointManagerMixin}: true if this waypoint is a player the rule hides now (their dot stays off the bar). */
	public static boolean offLocator(Either<UUID, String> id) {
		return armed && !HIDDEN.isEmpty() && SelfCreated.enabled() && id.left().map(HIDDEN::contains).orElse(false);
	}

	/** {@code ClientWaypointManagerMixin}: true if every one of these waypoints is off the bar now (none would be drawn). */
	public static boolean allOffLocator(Collection<Either<UUID, String>> ids) {
		if (!armed || HIDDEN.isEmpty() || ids.isEmpty()) {
			return false;
		}
		for (Either<UUID, String> id : ids) {
			if (!offLocator(id)) {
				return false;
			}
		}
		return true;
	}

	/** {@code ClientWaypointManagerMixin}: the bar's draw, without the dots of the players the rule hides now. */
	public static Consumer<TrackedWaypoint> locator(Consumer<TrackedWaypoint> draw) {
		if (!armed || HIDDEN.isEmpty()) {
			return draw;
		}
		return waypoint -> {
			if (!offLocator(waypoint.id())) {
				draw.accept(waypoint);
			}
		};
	}
}
