package com.forzacode.a1016_02.atmosphere.mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The real {@link MobTamper} (D-017). Effects are temporary and in memory only: the state sits in a field the mob
 * mixin adds ({@link TamperedMob}), so nothing is ever written to the mob's save data, and every effect ends on its
 * own deadline, on {@link #release}, when the mob unloads, or when the server stops.
 * <ul>
 * <li>freeze: the mob's whole AI step is skipped (goals, brain, navigation, move/look/jump controls), its inputs are
 * zeroed and its horizontal drift stopped. Gravity and pushing still apply.</li>
 * <li>face: frozen mobs turn their whole body toward the point at {@link AtmosphereConfig#tamperTurnDegreesPerTick};
 * mobs that are not frozen keep their head on the point (set right before the look control runs).</li>
 * <li>silence: the ambient sound timer still runs but the sound is skipped.</li>
 * <li>moveOutOfView: teleports only when both the mob's box and its box at the destination are out of view.</li>
 * </ul>
 * Server thread only.
 */
public final class MobTamperImpl implements MobTamper {
	public static final MobTamperImpl INSTANCE = new MobTamperImpl();

	private final Set<Mob> tracked = Collections.newSetFromMap(new IdentityHashMap<>());

	private MobTamperImpl() {
	}

	// --- MobTamper ---

	@Override
	public boolean freeze(Mob mob, int ticks) {
		if (ticks <= 0 || !canTamper(mob)) {
			return false;
		}
		TamperState state = stateFor(mob);
		state.freezeUntil = Math.max(state.freezeUntil, now(mob) + ticks);
		mob.getNavigation().stop();
		return true;
	}

	@Override
	public boolean face(Mob mob, Vec3 target, int ticks) {
		if (ticks <= 0 || target == null || !canTamper(mob)) {
			return false;
		}
		TamperState state = stateFor(mob);
		state.faceTarget = target;
		state.faceUntil = Math.max(state.faceUntil, now(mob) + ticks);
		return true;
	}

	@Override
	public boolean silence(Mob mob, int ticks) {
		if (ticks <= 0 || !canTamper(mob)) {
			return false;
		}
		TamperState state = stateFor(mob);
		state.silenceUntil = Math.max(state.silenceUntil, now(mob) + ticks);
		return true;
	}

	@Override
	public boolean moveOutOfView(Mob mob, BlockPos pos) {
		if (!canTamper(mob) || mob.isPassenger() || mob.isVehicle() || mob.isLeashed() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		Vec3 dest = Vec3.atBottomCenterOf(pos);
		AABB destBox = mob.getDimensions(mob.getPose()).makeBoundingBox(dest);
		if (!level.noCollision(mob, destBox)) {
			return false;
		}
		TraceService traces = Services.traces();
		if (!traces.isOutOfView(level, mob.getBoundingBox()) || !traces.isOutOfView(level, destBox)) {
			return false;
		}
		mob.getNavigation().stop();
		mob.teleportTo(dest.x, dest.y, dest.z);
		mob.setDeltaMovement(Vec3.ZERO);
		mob.setOldPosAndRot();
		return true;
	}

	@Override
	public void release(Mob mob) {
		TamperedMob duck = (TamperedMob) mob;
		if (duck.a1016_02$tamper() != null) {
			duck.a1016_02$setTamper(null);
			mob.getNavigation().stop();
		}
		tracked.remove(mob);
	}

	// --- queries ---

	public boolean isFrozen(Mob mob) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		return state != null && state.frozen(now(mob));
	}

	public boolean isFacing(Mob mob) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		return state != null && state.facing(now(mob));
	}

	public boolean isSilenced(Mob mob) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		return state != null && state.silenced(now(mob));
	}

	public boolean isTampered(Mob mob) {
		return ((TamperedMob) mob).a1016_02$tamper() != null;
	}

	/** Mobs with an effect right now (a copy). */
	public List<Mob> tracked() {
		return new ArrayList<>(tracked);
	}

	/**
	 * The geometry behind {@link #moveOutOfView}: true only if both boxes are out of view of every viewer. Usable
	 * with any viewpoints (tests).
	 */
	public static boolean bothOutOfView(Level level, AABB from, AABB to, List<TraceService.Viewer> viewers) {
		double near = ModConfig.pacing().viewNearBlocks;
		double cone = ModConfig.pacing().viewConeDegrees;
		return TraceService.isOutOfView(level, from, viewers, near, cone) && TraceService.isOutOfView(level, to, viewers, near, cone);
	}

	// --- lifecycle, called by AtmosphereServer ---

	/** Ends expired effects and forgets removed mobs. Every server tick. */
	public void tick() {
		if (tracked.isEmpty()) {
			return;
		}
		for (Mob mob : new ArrayList<>(tracked)) {
			TamperState state = ((TamperedMob) mob).a1016_02$tamper();
			if (mob.isRemoved() || state == null || state.idle(now(mob))) {
				release(mob);
			}
		}
	}

	/** Releases a mob that leaves its level (chunk unload, death, discard, dimension change). */
	public void onUnload(Entity entity) {
		if (entity instanceof Mob mob && (tracked.contains(mob) || isTampered(mob))) {
			release(mob);
		}
	}

	/** Releases everything. */
	public void clear() {
		for (Mob mob : new ArrayList<>(tracked)) {
			release(mob);
		}
		tracked.clear();
	}

	// --- hooks, called by the mob mixin (server side only) ---

	/** At the start of the AI step. True cancels the step (the mob is frozen). */
	public static boolean onAiStep(Mob mob, TamperState state) {
		long now = now(mob);
		if (!state.frozen(now)) {
			return false;
		}
		mob.getNavigation().stop();
		mob.xxa = 0.0F;
		mob.yya = 0.0F;
		mob.zza = 0.0F;
		mob.setJumping(false);
		Vec3 motion = mob.getDeltaMovement();
		mob.setDeltaMovement(0.0, motion.y, 0.0);
		Vec3 target = state.faceTarget;
		if (target != null && state.facing(now)) {
			turnToward(mob, target, AtmosphereConfig.get().tamperTurnDegreesPerTick);
		}
		return true;
	}

	/** Right before the look control runs, for mobs that face a point but are not frozen. */
	public static void beforeLook(Mob mob, TamperState state) {
		Vec3 target = state.faceTarget;
		if (target != null && state.facing(now(mob))) {
			mob.getLookControl().setLookAt(target.x, target.y, target.z);
		}
	}

	/** False skips the ambient sound. */
	public static boolean allowAmbientSound(Mob mob, @Nullable TamperState state) {
		return state == null || mob.level().isClientSide() || !state.silenced(now(mob));
	}

	/** Turns head, body and rotation together toward the point, at most {@code maxDegrees} per call. */
	static void turnToward(Mob mob, Vec3 target, float maxDegrees) {
		double dx = target.x - mob.getX();
		double dz = target.z - mob.getZ();
		double dy = target.y - mob.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < 1.0E-4 && Math.abs(dy) < 1.0E-4) {
			return;
		}
		float wantedYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float wantedPitch = Mth.clamp((float) (-(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG)), -mob.getMaxHeadXRot(), mob.getMaxHeadXRot());
		float yaw = Mth.approachDegrees(mob.getYHeadRot(), wantedYaw, maxDegrees);
		float pitch = Mth.approach(mob.getXRot(), wantedPitch, maxDegrees);
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setXRot(pitch);
	}

	/** The yaw (degrees) a mob at {@code from} needs to face {@code to}. */
	public static float yawToward(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	// --- helpers ---

	private TamperState stateFor(Mob mob) {
		TamperedMob duck = (TamperedMob) mob;
		TamperState state = duck.a1016_02$tamper();
		if (state == null) {
			state = new TamperState();
			duck.a1016_02$setTamper(state);
		}
		tracked.add(mob);
		return state;
	}

	private static boolean canTamper(@Nullable Mob mob) {
		if (mob == null || !mob.isAlive() || mob.isRemoved() || !(mob.level() instanceof ServerLevel)) {
			return false;
		}
		// Never anything a player rides or steers.
		if (mob.getControllingPassenger() instanceof Player) {
			return false;
		}
		for (Entity passenger : mob.getPassengers()) {
			if (passenger instanceof Player) {
				return false;
			}
		}
		return true;
	}

	static long now(Mob mob) {
		return mob.level() instanceof ServerLevel level ? level.getServer().getTickCount() : 0L;
	}
}
