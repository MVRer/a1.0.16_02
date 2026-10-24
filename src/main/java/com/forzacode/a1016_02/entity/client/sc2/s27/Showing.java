package com.forzacode.a1016_02.entity.client.sc2.s27;

import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.entity.ScreenshotSpot;
import com.forzacode.a1016_02.entity.sc2.s27.CopyRules;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Payload;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Rules;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27SeenPayload;
import com.forzacode.a1016_02.entity.sc2.s27.Sight;
import com.forzacode.a1016_02.entity.sc2.s27.Walk;
import com.mojang.blaze3d.platform.Window;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * One showing of 27's copy on this client (docs/SELF_CREATED_2.md, 27), from the server's payload: the copy waits off the
 * screen to be put there ({@link S27Client}), then stands facing the viewer, its head following them as a player watching
 * someone does; looked at long enough near the middle of the screen, or when its time is up, the server ends it, or the
 * viewer comes near while it is seen, it pauses a moment, turns as a player turns (a quick turn of the head, the body after
 * it) and walks off at walking speed, jumping up a block where the ground rises; it is gone the first tick nobody could see
 * it ({@link CopyRules#seen}), never while seen (chased, it sprints and sprint-jumps; caught up with, {@link CopyRules#ghost}, it
 * is gone as a desync ghost goes, never touched). The other player leaving the game takes it at once, as vanilla takes a
 * player who leaves. Client thread.
 */
final class Showing {
	enum Phase { WAITING, STANDING, PAUSE, TURNING, WALKING, GONE }

	/** The head follows the viewer this fast while it stands (degrees a tick). */
	static final float HEAD_FOLLOW = 4.0F;
	/** The turn: this fast (degrees a tick, a quick turn of the mouse). */
	static final float TURN = 32.0F;
	/** The pause before the turn, ticks, at least... */
	static final int PAUSE_MIN = 7;
	/** ...plus up to this many. */
	static final int PAUSE_SPREAD = 9;
	/** A walk this long without leaving sight keeps walking; past it, it simply stands until unseen. */
	static final int WALK_MAX_TICKS = 20 * 90;

	final Sc27Payload payload;
	final boolean standIn;
	/** The level the payload came in (null: none yet): a copy never stands in another one (R12-38). */
	final @Nullable ClientLevel level;
	Phase phase = Phase.WAITING;
	@Nullable FriendCopy copy;
	/** Ticks since the payload came, waiting to be put there. */
	int waited;
	/** Ticks since it was put there. */
	int age;
	/** Ticks it was looked at near the middle of the screen, in all. */
	int looked;
	/** Its age when it was first seen (R10-8: then the server is told, once), or -1. */
	int firstSeen = -1;
	boolean ended;
	private int pause;
	private float bodyYaw;
	private float headYaw;
	private float pitch;
	private double walkYaw;
	private double speed;
	private double vy;
	/** The viewer's distance last tick (NaN before), and how many more ticks it runs as chased. */
	private double lastDistance = Double.NaN;
	private int chased;
	private boolean onGround = true;
	private int walked;
	private @Nullable Vec3 next;
	private final RandomSource random = RandomSource.create();

	Showing(Sc27Payload payload, boolean standIn, @Nullable ClientLevel level) {
		this.payload = payload;
		this.standIn = standIn;
		this.level = level;
		this.bodyYaw = payload.yaw();
		this.headYaw = payload.yaw();
		this.walkYaw = payload.walkYaw();
	}

	/** It is put there now: standing at its spot, facing the viewer. */
	void placed(FriendCopy copy) {
		this.copy = copy;
		this.phase = Phase.STANDING;
		Vec3 spot = payload.spot();
		copy.snapTo(spot.x, spot.y, spot.z, bodyYaw, 0.0F);
		copy.setYHeadRot(headYaw);
		copy.setYBodyRot(bodyYaw);
		copy.yHeadRotO = headYaw;
		copy.yBodyRotO = bodyYaw;
		copy.setOldPosAndRot();
		copy.setOnGround(true);
	}

	/** What the viewer sees of the copy now. */
	record Vision(boolean onScreen, boolean seen, boolean middle, double distance) {
	}

	/** The viewer's camera as the copy's rules read it (the last frame's). */
	static ScreenshotSpot.View view(Minecraft minecraft) {
		Camera camera = minecraft.gameRenderer.mainCamera();
		Window window = minecraft.getWindow();
		int width = Math.max(1, window.getWidth());
		int height = Math.max(1, window.getHeight());
		return new ScreenshotSpot.View(camera.position(), camera.yRot(), camera.xRot(), camera.getFov(), width / (double) height, height);
	}

	/** The frame's drawn fog, or null. */
	static ScreenshotSpot.@Nullable Fog fog(Minecraft minecraft) {
		FogData data = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
		return data == null ? null
				: new ScreenshotSpot.Fog(data.environmentalStart, data.environmentalEnd, data.renderDistanceStart, data.renderDistanceEnd);
	}

	static Vision vision(Minecraft minecraft, ClientLevel level, Vec3 feet) {
		ScreenshotSpot.View view = view(minecraft);
		Vec3 eye = view.eye();
		boolean onScreen = CopyRules.onScreen(view, feet, CopyRules.ON_SCREEN_MARGIN);
		double distance = eye.distanceTo(feet);
		boolean inSight = Sight.body(level, eye, feet);
		ScreenshotSpot.Fog fog = fog(minecraft);
		double cover = fog == null ? 0.0 : fog.value(feet.add(0.0, Sight.CHEST, 0.0).subtract(eye));
		int light = level.getMaxLocalRawBrightness(BlockPos.containing(feet.x, feet.y + Sight.HEAD, feet.z));
		boolean seen = CopyRules.seen(onScreen, distance, inSight, cover, light, view.pixelsTall(feet));
		return new Vision(onScreen, seen, seen && CopyRules.middleThird(view, feet), distance);
	}

	/** One tick of the copy, run first in its own tick. */
	void step(FriendCopy c) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || c.level() != level || phase == Phase.GONE) {
			gone(c);
			return;
		}
		if (!standIn && (minecraft.getConnection() == null || minecraft.getConnection().getPlayerInfo(payload.other()) == null)) {
			gone(c); // they left the game: gone as a player who leaves is gone
			return;
		}
		if (!SelfCreated.enabled()) {
			ended = true;
		}
		age++;
		Vision vision = vision(minecraft, level, c.position());
		Vec3 eye = view(minecraft).eye();
		if (CopyRules.ghost(vision.distance())) {
			gone(c); // caught up with, seen or not: gone as a desync ghost goes, never touched
			return;
		}
		if (vision.seen() && firstSeen < 0) {
			firstSeen = age;
			if (!standIn && ClientPlayNetworking.canSend(Sc27SeenPayload.TYPE)) {
				ClientPlayNetworking.send(new Sc27SeenPayload(payload.key())); // R10-8: it counts now, not when it was sent
			}
		}
		switch (phase) {
			case STANDING -> {
				if (vision.middle()) {
					looked++;
				}
				faceViewer(c, eye);
				CopyRules.Next next = CopyRules.standing(vision.seen(), vision.distance(), payload.near(), looked, payload.lookTicks(), age,
						CopyRules.life(payload.lifeTicks(), payload.lookTicks(), firstSeen), ended);
				if (next == CopyRules.Next.GONE) {
					gone(c);
				} else if (next == CopyRules.Next.LEAVE) {
					phase = Phase.PAUSE;
					pause = vision.distance() < payload.near() ? 2 : PAUSE_MIN + random.nextInt(PAUSE_SPREAD + 1);
				}
			}
			case PAUSE -> {
				faceViewer(c, eye);
				if (--pause <= 0) {
					phase = Phase.TURNING;
				}
			}
			case TURNING -> {
				if (!vision.seen()) {
					gone(c);
					return;
				}
				headYaw = Mth.approachDegrees(headYaw, (float) walkYaw, TURN);
				pitch = Mth.approach(pitch, 4.0F, 6.0F);
				// The body follows the head once it has turned past a shoulder, as vanilla turns a player.
				if (Math.abs(Mth.wrapDegrees(headYaw - bodyYaw)) > 50.0F) {
					bodyYaw = Mth.approachDegrees(bodyYaw, headYaw, TURN * 0.6F);
				}
				apply(c);
				if (Math.abs(Mth.wrapDegrees(headYaw - (float) walkYaw)) < 4.0F) {
					phase = Phase.WALKING;
				}
			}
			case WALKING -> {
				if (!vision.seen()) {
					gone(c);
					return;
				}
				walk(c, level, vision.distance());
			}
			default -> {
			}
		}
	}

	/** Standing: the body still, the head following the viewer's eyes (within a shoulder's turn), a little slowly. */
	private void faceViewer(FriendCopy c, Vec3 eye) {
		Vec3 head = c.position().add(0.0, Sight.HEAD, 0.0);
		float toward = Sc27Rules.yawToward(head.x, head.z, eye.x, eye.z);
		float wanted = bodyYaw + Mth.clamp(Mth.wrapDegrees(toward - bodyYaw), -60.0F, 60.0F);
		headYaw = Mth.approachDegrees(headYaw, wanted, HEAD_FOLLOW);
		double flat = Math.hypot(eye.x - head.x, eye.z - head.z);
		float wantedPitch = (float) -Math.toDegrees(Math.atan2(eye.y - head.y, flat));
		pitch = Mth.approach(pitch, Mth.clamp(wantedPitch, -30.0F, 30.0F), 2.0F);
		apply(c);
	}

	private void apply(FriendCopy c) {
		c.setYRot(headYaw);
		c.setYHeadRot(headYaw);
		c.setYBodyRot(bodyYaw);
		c.setXRot(pitch);
	}

	/**
	 * Walking: toward the next step along the way off ({@link Walk#next}, turning a little round what blocks it), at a player's
	 * walking speed after a few ticks' start, jumping where the ground rises a block, falling where it drops; the head and the
	 * body along the way, a slight wander in the yaw. Chased (the viewer within the near limit and closing), it sprints as a
	 * player would, and sprint-jumps once they are within {@link CopyRules#SPRINT_JUMP_WITHIN}, for a while after.
	 */
	private void walk(FriendCopy c, ClientLevel level, double distance) {
		walked++;
		boolean closing = !Double.isNaN(lastDistance) && lastDistance - distance > CopyRules.CLOSING_PER_TICK;
		lastDistance = distance;
		if (closing && distance < payload.near()) {
			chased = CopyRules.CHASED_TICKS;
		} else if (chased > 0) {
			chased--;
		}
		c.setSprinting(chased > 0);
		Vec3 at = c.position();
		if (next == null || Math.hypot(next.x - at.x, next.z - at.z) < Walk.SPEED * 1.5) {
			Walk.Step step = walked > WALK_MAX_TICKS ? null : Walk.next(level, new Vec3(at.x, onGround ? at.y : groundOr(level, at), at.z), walkYaw, 1.0);
			next = step == null ? null : step.feet();
		}
		if (next == null) {
			speed = 0.0;
			c.setPos(at.x, at.y, at.z);
			return; // blocked: it stands where it is until nobody sees it
		}
		double top = chased > 0 ? CopyRules.SPRINT : Walk.SPEED;
		speed = onGround ? Math.min(top, speed + Walk.SPEED / 3.0) : speed;
		double dx = next.x - at.x;
		double dz = next.z - at.z;
		double flat = Math.hypot(dx, dz);
		double move = Math.min(speed, flat);
		double x = at.x + dx / flat * move;
		double z = at.z + dz / flat * move;
		float toward = Sc27Rules.yawToward(at.x, at.z, next.x, next.z);
		headYaw = Mth.approachDegrees(headYaw, toward + (float) (Math.sin(walked * 0.11) * 2.0), 12.0F);
		bodyYaw = Mth.approachDegrees(bodyYaw, toward, 12.0F);
		pitch = Mth.approach(pitch, 5.0F, 1.0F);
		double y = at.y;
		double ground = groundOr(level, new Vec3(x, y, z));
		if (onGround && ground - y > 0.6) {
			vy = 0.42; // a jump, as a player walking up a block jumps
			onGround = false;
		} else if (onGround && chased > 0 && distance < CopyRules.SPRINT_JUMP_WITHIN && Math.abs(ground - y) < 1.0E-3) {
			vy = 0.42; // a sprint-jump: a player running from someone
			onGround = false;
			speed = CopyRules.SPRINT_JUMP;
		} else if (onGround && ground > y) {
			y = ground; // a slab's or a path's half step
		}
		if (!onGround || ground < y - 1.0E-3) {
			onGround = false;
			y += vy;
			vy = (vy - 0.08) * 0.98;
			if (y <= ground && vy <= 0.0) {
				y = ground;
				vy = 0.0;
				onGround = true;
			}
		}
		c.setPos(x, y, z);
		c.setOnGround(onGround);
		apply(c);
	}

	/** The ground under a point (from a block over it down), or the point's own height if none is near. */
	private static double groundOr(ClientLevel level, Vec3 at) {
		double ground = Walk.groundBelow(level, at.x, at.z, at.y + Walk.MAX_UP + 0.01, Walk.MAX_DOWN + 3);
		return Double.isNaN(ground) ? at.y : ground;
	}

	void gone(FriendCopy c) {
		phase = Phase.GONE;
		if (!c.isRemoved()) {
			c.discard();
		}
	}
}
