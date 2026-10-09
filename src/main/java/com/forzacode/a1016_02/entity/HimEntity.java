package com.forzacode.a1016_02.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The figure. Default Steve, blank white eyes, no glow, no name, no sound, no particles. He cannot be touched,
 * pushed, hit or attacked, he has no goals at all (so nothing to target or approach anyone with), he is never
 * saved, and he does not count toward mob caps. He stands at the fog edge for a few seconds and always leaves
 * first: stared at (about 2 s) or approached, he ends the sighting the way his {@link Variant} says, then
 * despawns once out of view or past the fog. Unseen, he despawns after a lifetime. He never vanishes in view.
 *
 * <p>All behaviour runs in {@link #customServerAiStep} from plain look and move controls; spawn him through
 * {@link FigureApi}.
 */
public class HimEntity extends PathfinderMob {
	/** Set when the Ending A figure was seen and is gone. */
	public static final String LAST_SIGHTING_SEEN_FLAG = "entity:last_sighting_seen";

	private static final EntityDataAccessor<Boolean> DATA_LOW = SynchedEntityData.defineId(HimEntity.class, EntityDataSerializers.BOOLEAN);
	private static final float CLIENT_RISE_STEP = 1.0F / 15.0F;
	private static final int VIEW_CHECK_INTERVAL = 2;
	private static final double BASE_SPEED = 0.25;

	public enum Phase { IDLE, RISING, STARE_BACK, HIDING, LEAVING }

	private Variant variant = Variant.RIDGE;
	private Phase phase = Phase.IDLE;
	private Variant.Gait gait = Variant.Gait.WALK;
	private float holdYaw;
	private @Nullable BlockPos anchor;
	private int bornTick = -1;
	private int age;
	private int phaseTicks;
	private int repathCooldown;
	private int seenTicks;
	private int unseenTicks;
	private int stareTicks;
	private boolean everSeen;
	private boolean triggered;
	private boolean stared;
	private @Nullable UUID triggeredBy;
	private final Map<UUID, Double> startDistance = new HashMap<>();

	// client: the low pose, blended
	private float low;
	private float lowO;
	private boolean lowInitialized;

	public HimEntity(EntityType<? extends HimEntity> type, Level level) {
		super(type, level);
		setSilent(true);
		setPersistenceRequired();
		setCanPickUpLoot(false);
		xpReward = 0;
		setPathfindingMalus(PathType.WATER, -1.0F);
		setPathfindingMalus(PathType.WATER_BORDER, 8.0F);
		setPathfindingMalus(PathType.LAVA, -1.0F);
		setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	/** Sets the variant and the way he faces. Call before adding him to the level. */
	void setup(Variant variant, float yaw, @Nullable BlockPos anchor) {
		this.variant = variant;
		this.holdYaw = yaw;
		this.anchor = anchor;
		this.gait = variant.gait();
		setLow(variant.pose() == Variant.Pose.LOW);
		setYRot(yaw);
		setYHeadRot(yaw);
		setYBodyRot(yaw);
		setXRot(0.0F);
		phase = variant.walksFromStart() ? Phase.LEAVING : Phase.IDLE;
		if (level() instanceof ServerLevel serverLevel) {
			bornTick = serverLevel.getServer().getTickCount();
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOW, false);
	}

	private void setLow(boolean value) {
		entityData.set(DATA_LOW, value);
	}

	public boolean isLow() {
		return entityData.get(DATA_LOW);
	}

	// --- behaviour (server) ---

	@Override
	protected void registerGoals() {
		// None. He never targets, follows or approaches anyone; everything he does is in customServerAiStep.
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (age == 0 && bornTick < 0) {
			holdYaw = getYRot(); // made without setup (/summon): keep the facing he was given
		}
		age++;
		phaseTicks++;
		EntityConfig config = EntityConfig.get();
		List<ServerPlayer> players = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();

		if (age % VIEW_CHECK_INTERVAL == 0) {
			if (Services.traces().isOutOfView(level, getBoundingBox())) {
				unseenTicks += VIEW_CHECK_INTERVAL;
			} else {
				everSeen = true;
				seenTicks += VIEW_CHECK_INTERVAL;
				unseenTicks = 0;
			}
		}
		if (age % 20 == 0 && pastFog(players)) {
			gone(level, "past the fog");
			return;
		}

		watch(level, players, config);

		long grace = ModConfig.realTicks(variant == Variant.COW && phase == Phase.IDLE ? config.cowGoneAfterUnseenSeconds : config.goneAfterUnseenSeconds);
		if ((everSeen || triggered) && unseenTicks >= grace) {
			gone(level, "out of view");
			return;
		}
		if (!everSeen && unseenTicks >= ModConfig.realTicks(config.unseenLifetimeSeconds)) {
			gone(level, "nobody saw him");
			return;
		}
		if (!triggered && age >= ModConfig.realTicks(config.maxLifetimeSeconds)) {
			walkAway(variant.gait() == Variant.Gait.SLOW ? Variant.Gait.SLOW : Variant.Gait.WALK);
		}

		ServerPlayer nearest = nearest(players);
		switch (phase) {
			case IDLE -> idle(nearest);
			case RISING -> {
				holdStill();
				if (phaseTicks >= ModConfig.realTicks(config.riseSeconds)) {
					setPhase(Phase.STARE_BACK);
				}
			}
			case STARE_BACK -> {
				ServerPlayer target = triggeredBy != null && level.getPlayerByUUID(triggeredBy) instanceof ServerPlayer p ? p : nearest;
				stareBack(target);
				if (phaseTicks >= ModConfig.realTicks(config.stareBackSeconds)) {
					setPhase(Phase.LEAVING);
				}
			}
			case HIDING -> hide(nearest, config);
			case LEAVING -> steerAway(level, nearest, speed(gait, config));
		}
	}

	/** Stare and approach: the two ways a sighting ends. */
	private void watch(ServerLevel level, List<ServerPlayer> players, EntityConfig config) {
		Pacing pacing = ModConfig.pacing();
		ServerPlayer looker = null;
		ServerPlayer approacher = null;
		double cosCone = Math.cos(Math.toRadians(config.stareConeDegrees));
		for (ServerPlayer player : players) {
			double d = SpotFinder.horizontal(player.position(), position());
			double start = startDistance.computeIfAbsent(player.getUUID(), k -> d);
			if (start - d >= config.approachBlocks || d < pacing.sightingMinDistance) {
				approacher = player;
			}
			if (looker == null && isLookedAtBy(level, player, cosCone)) {
				looker = player;
			}
		}
		stareTicks = looker != null ? stareTicks + 1 : Math.max(0, stareTicks - 2);
		if (!stared && looker != null && stareTicks >= pacing.stareTicks()) {
			stared = true;
			Attention.trigger(level.getServer(), AttentionTrigger.STARED_AT_HIM);
			EntityData.get(level.getServer()).recordStared();
			trigger(looker);
		} else if (approacher != null) {
			trigger(approacher);
		}
	}

	/** The player's crosshair is within the cone around him and nothing solid is in between. */
	private boolean isLookedAtBy(ServerLevel level, ServerPlayer player, double cosCone) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double reach = FogEdge.of(player, false).renderLimit();
		Vec3[] points = {
				new Vec3(getX(), getY() + getBbHeight() * 0.65, getZ()),
				new Vec3(getX(), getEyeY(), getZ()),
				new Vec3(getX(), getY() + 0.4, getZ())
		};
		for (Vec3 point : points) {
			Vec3 to = point.subtract(eye);
			double dist = to.length();
			if (dist < 1.0E-3 || dist > reach || to.dot(look) < cosCone * dist) {
				continue;
			}
			HitResult hit = level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	/** Ends the sighting the variant's way. Once only. */
	void trigger(@Nullable ServerPlayer by) {
		if (triggered) {
			return;
		}
		triggered = true;
		triggeredBy = by != null ? by.getUUID() : null;
		switch (variant.ending()) {
			case NONE -> {
				// He keeps doing what he was doing (walking away, never faster).
			}
			case STARE_LEAVE -> setPhase(Phase.STARE_BACK);
			case RISE_STARE_LEAVE -> {
				setLow(false);
				setPhase(Phase.RISING);
			}
			case HIDE -> setPhase(Phase.HIDING);
		}
	}

	/** Makes him turn and walk (or run) away into the fog now. He despawns once out of view or past the fog. */
	public void walkAway(Variant.Gait leaveGait) {
		triggered = true;
		gait = leaveGait;
		setLow(false);
		setPhase(Phase.LEAVING);
	}

	private void setPhase(Phase next) {
		if (phase != next) {
			phase = next;
			phaseTicks = 0;
			repathCooldown = 0;
			getNavigation().stop();
		}
	}

	private void idle(@Nullable ServerPlayer nearest) {
		getNavigation().stop();
		if (variant.facing() == Variant.Facing.ANCHOR && anchor != null) {
			holdBody();
			getLookControl().setLookAt(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5, 10.0F, 40.0F);
		} else if (variant.tracksPlayer() && nearest != null) {
			holdBody();
			getLookControl().setLookAt(nearest.getX(), nearest.getEyeY(), nearest.getZ(), 10.0F, 40.0F);
		} else {
			holdStill();
		}
	}

	private void holdBody() {
		setYRot(holdYaw);
		setYBodyRot(holdYaw);
	}

	/** Perfectly still, facing where he faced when he appeared. */
	private void holdStill() {
		getNavigation().stop();
		holdBody();
		setYHeadRot(holdYaw);
		setXRot(0.0F);
	}

	/** He turns to face the player and holds it, then leaves. */
	private void stareBack(@Nullable ServerPlayer target) {
		getNavigation().stop();
		if (target == null) {
			holdStill();
			return;
		}
		float toward = yawToward(position(), target.position());
		holdYaw = Mth.approachDegrees(holdYaw, toward, 18.0F);
		holdBody();
		getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ(), 30.0F, 40.0F);
	}

	/** Trunk variant: slip directly behind the trunk as seen from the player, then he is gone once hidden. */
	private void hide(@Nullable ServerPlayer nearest, EntityConfig config) {
		if (anchor == null || nearest == null || phaseTicks >= ModConfig.realTicks(config.hideMaxSeconds)) {
			setPhase(Phase.LEAVING);
			return;
		}
		if (--repathCooldown <= 0) {
			repathCooldown = 10;
			Vec3 center = new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
			Vec3 dir = center.subtract(nearest.position()).horizontal();
			if (dir.lengthSqr() > 1.0E-4) {
				Vec3 target = center.add(dir.normalize().scale(1.1));
				getNavigation().moveTo(target.x, target.y, target.z, speed(Variant.Gait.WALK, config));
			}
		}
	}

	/** Walks straight away from the player where it can, otherwise to any reachable spot away from him. */
	private void steerAway(ServerLevel level, @Nullable ServerPlayer from, double speed) {
		if (--repathCooldown > 0 && !getNavigation().isDone()) {
			return;
		}
		repathCooldown = 40;
		Vec3 origin = from != null ? from.position() : position().subtract(Entity.calculateViewVector(0.0F, holdYaw));
		Vec3 away = position().subtract(origin).horizontal();
		if (away.lengthSqr() < 1.0E-4) {
			away = Entity.calculateViewVector(0.0F, holdYaw).horizontal();
		}
		away = away.normalize();
		Vec3 ahead = position().add(away.scale(12.0));
		int x = Mth.floor(ahead.x);
		int z = Mth.floor(ahead.z);
		if (level.hasChunkAt(x, z)) {
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (getNavigation().moveTo(x + 0.5, y, z + 0.5, speed)) {
				return;
			}
		}
		Vec3 target = LandRandomPos.getPosAway(this, 16, 7, origin);
		if (target == null) {
			target = DefaultRandomPos.getPosAway(this, 16, 7, origin);
		}
		if (target != null && getNavigation().moveTo(target.x, target.y, target.z, speed)) {
			return;
		}
		getMoveControl().setWantedPosition(getX() + away.x * 6.0, getY(), getZ() + away.z * 6.0, speed);
	}

	private boolean pastFog(List<ServerPlayer> players) {
		if (players.isEmpty()) {
			return false;
		}
		for (ServerPlayer player : players) {
			if (SpotFinder.horizontal(player.position(), position()) <= FogEdge.of(player, false).pastFog() + 4.0) {
				return false;
			}
		}
		return true;
	}

	private void gone(ServerLevel level, String why) {
		if (variant == Variant.LAST_ONE && everSeen) {
			HerobrineState.get(level.getServer()).setFlag(LAST_SIGHTING_SEEN_FLAG, true);
		}
		A1016_02.LOGGER.debug("[a1016] figure ({}) gone after {} ticks: {}", variant.shortName(), age, why);
		discard();
	}

	private @Nullable ServerPlayer nearest(List<ServerPlayer> players) {
		ServerPlayer best = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayer player : players) {
			double d = player.distanceToSqr(this);
			if (d < bestDist) {
				bestDist = d;
				best = player;
			}
		}
		return best;
	}

	private static double speed(Variant.Gait gait, EntityConfig config) {
		return switch (gait) {
			case SLOW -> config.slowWalkSpeed;
			case WALK -> config.walkSpeed;
			case RUN -> config.runSpeed;
		};
	}

	/** Yaw (degrees, Minecraft convention) that faces from {@code from} toward {@code to}. */
	public static float yawToward(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	// --- read-only views (debug info, tests) ---

	public Variant variant() {
		return variant;
	}

	public Phase phase() {
		return phase;
	}

	public int age() {
		return age;
	}

	/** Server tick count when he was set up, or -1 (for example a /summon). */
	public int bornTick() {
		return bornTick;
	}

	public boolean everSeen() {
		return everSeen;
	}

	public boolean triggered() {
		return triggered;
	}

	public boolean stared() {
		return stared;
	}

	public int stareTicks() {
		return stareTicks;
	}

	public int seenTicks() {
		return seenTicks;
	}

	public int unseenTicks() {
		return unseenTicks;
	}

	public @Nullable BlockPos anchor() {
		return anchor;
	}

	/** Goals in both selectors. Always 0. */
	public int goalCount() {
		return goalSelector.getAvailableGoals().size() + targetSelector.getAvailableGoals().size();
	}

	// --- client: the low pose ---

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			float target = isLow() ? 1.0F : 0.0F;
			if (!lowInitialized) {
				low = target;
				lowInitialized = true;
			}
			lowO = low;
			low = Mth.approach(low, target, CLIENT_RISE_STEP);
		}
	}

	/** 1 = low on all fours, 0 = standing; interpolated for rendering. */
	public float lowAmount(float partialTick) {
		return Mth.lerp(partialTick, lowO, low);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		// Vanilla culls a player-sized mob at 64 blocks; he lives at the fog edge. The fog hides him, not a cutoff.
		return true;
	}

	// --- never despawned by vanilla, never saved ---

	@Override
	public void checkDespawn() {
		// Only his own rules remove him.
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	// --- untouchable: not pickable, pushable, collidable, attackable or hurtable ---

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity entity) {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public void push(double x, double y, double z) {
	}

	@Override
	public void push(Vec3 impulse) {
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public void knockback(double strength, double x, double z, DamageSource source, float amount, boolean blocked) {
	}

	@Override
	public void knockback(double strength, double x, double z, DamageSource source, float amount) {
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean attackable() {
		return false;
	}

	@Override
	public boolean skipAttackInteraction(Entity attacker) {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isInvulnerable() {
		return true;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public void thunderHit(ServerLevel level, LightningBolt lightning) {
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return false;
	}

	@Override
	public void kill(ServerLevel level) {
		// No death animation, no poof: he is simply not there any more.
		discard();
	}

	@Override
	protected void onBelowWorld() {
		discard();
	}

	// --- never targets, attacks or touches anyone ---

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// Never a target.
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return false;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		return false;
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		return InteractionResult.PASS;
	}

	// --- no name, no glow, no sound, no particles, no traces ---

	@Override
	public void setCustomName(@Nullable Component name) {
		// No name tag, ever.
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	public boolean isCustomNameVisible() {
		return false;
	}

	@Override
	public boolean isCurrentlyGlowing() {
		return false;
	}

	@Override
	protected MovementEmission getMovementEmission() {
		return MovementEmission.NONE;
	}

	@Override
	public boolean isSteppingCarefully() {
		return true;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean dampensVibrations() {
		return true;
	}

	@Override
	public boolean canSpawnSprintParticle() {
		return false;
	}

	@Override
	protected void doWaterSplashEffect() {
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
		// No landing particles, no trampled farmland.
		resetFallDistance();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	@Override
	public void playAmbientSound() {
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}

	// --- no drops ---

	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}
}
