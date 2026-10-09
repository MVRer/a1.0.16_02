package com.forzacode.a1016_02.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;

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
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The figure. Default Steve, blank white eyes, no name, no sound, no particles. He cannot be touched,
 * pushed, hit or attacked, he has no goals at all (so nothing to target or approach anyone with), he is never
 * saved, and he does not count toward mob caps. He stands well inside the fog for a few seconds and always leaves
 * first: stared at (3 s), approached (10 blocks closed since first seen) or come too close to (18 blocks), the last two
 * scaled down for a figure that appeared close (D-035), he ends the sighting the way his {@link Variant} says, then
 * despawns once out of view or past the fog. Once seen he stays at least {@code minSeenSeconds} unless he flees.
 * Unseen, he despawns after a lifetime. He never vanishes in view.
 *
 * <p>Leaving at a run he always outpaces whoever chases him (D-036): {@code outrunFactor} times the chaser's speed, at
 * least {@code baseRunSpeed}, at most {@code maxRunSpeed}. Walking away, he breaks into that run when a player closes
 * in fast or comes within the flee distance. He steps up full blocks without jumping, so he never stalls on a step.
 *
 * <p>Two more ways out. Going under (D-030, {@link GoUnder}): instead of walking or running off after the stare
 * back, he may dig straight down where he stands, cover the hole over himself and be gone once out of view. The
 * rush (D-037, {@link Rush}): a chaser he cannot outrun who closes in fast makes him turn and run straight past them,
 * never touching, and he is gone the instant they cannot see him; once per sighting.
 *
 * <p>All behaviour runs in {@link #customServerAiStep} from plain look and move controls; spawn him through
 * {@link FigureApi}.
 */
public class HimEntity extends PathfinderMob {
	/** Set when the Ending A figure was seen and is gone. */
	public static final String LAST_SIGHTING_SEEN_FLAG = "entity:last_sighting_seen";

	private static final EntityDataAccessor<Boolean> DATA_LOW = SynchedEntityData.defineId(HimEntity.class, EntityDataSerializers.BOOLEAN);
	private static final float CLIENT_RISE_STEP = 1.0F / 15.0F;
	static final double BASE_SPEED = 0.25;
	/** He steps up a full block without jumping, so a run never stalls on one-block terrain (horses do the same). */
	static final double STEP_HEIGHT = 1.0;
	/** Leaving: a fresh path at least this often, and the path he follows reaches this many seconds of travel ahead. */
	private static final int REPATH_TICKS = 40;
	private static final double LEAVE_AHEAD_SECONDS = 2.5;
	/** Hiding behind the trunk, he runs instead once a player would reach him within this long. */
	private static final double HIDE_REACH_SECONDS = 2.0;
	/**
	 * Half-width and height of the box the view checks use. The rendered model reaches about 0.49 to the sides
	 * (shoulders and sleeves), 0.74 behind in the low pose (0.78 at a corner, any yaw) and 1.91 up (hat layer).
	 */
	public static final double VIEW_HALF_WIDTH = 0.9;
	public static final double VIEW_HEIGHT = 2.1;
	/** He only appears this far inside the entity-ticking range, in blocks. */
	public static final int SPAWN_TICK_MARGIN = 24;
	/** A leave target has to be this far inside the entity-ticking range. */
	public static final int LEAVE_TICK_MARGIN = 20;
	/** Closer than this to the edge of the entity-ticking range, he is gone (he would freeze past it). */
	public static final int EDGE_TICK_MARGIN = 16;

	/**
	 * GOING_UNDER: digging down and covering the hole over himself (D-030). UNDER: covered, gone once out of view.
	 * RUSH: running past a chaser (D-037), gone the instant out of view.
	 */
	public enum Phase { IDLE, RISING, STARE_BACK, HIDING, LEAVING, GOING_UNDER, UNDER, RUSH }

	private Variant variant = Variant.RIDGE;
	private Phase phase = Phase.IDLE;
	private Variant.Gait gait = Variant.Gait.WALK;
	private float holdYaw;
	private @Nullable BlockPos anchor;
	private int bornTick = -1;
	private int age;
	private int phaseTicks;
	private int repathCooldown;
	/** Leaving: ticks since the last path, and whether that attempt found none. */
	private int ticksSinceRepath;
	private boolean repathFailed;
	private int seenTicks;
	private int unseenTicks;
	private int stareTicks;
	private boolean everSeen;
	/** {@link #age} when he was first seen, or -1. */
	private int firstSeenAge = -1;
	private boolean triggered;
	private boolean stared;
	private boolean fled;
	private @Nullable UUID triggeredBy;
	/** Distance each player has closed on him since he was first seen. */
	private final Map<UUID, Approach> approaches = new HashMap<>();
	/** Each player's recent speed and closing speed. */
	private final Map<UUID, Chase> chases = new HashMap<>();
	/** Horizontal distance to the player he appeared for, NaN until known (D-035). */
	private double spawnDistance = Double.NaN;
	/** True once he runs from a chaser at the adaptive speed (D-036). */
	private boolean outrunning;
	/** The speed he last moved off at, in blocks per second on flat ground. */
	private double moveSpeed;
	/** Running: the speed he holds through the air, blocks per tick (0 = a plain mob's air control). */
	private double airSpeedPerTick;
	/** His dig once he goes under (D-030), or null. */
	private @Nullable GoUnder goUnder;
	/** The chance to go under is rolled once per sighting. */
	private boolean goUnderRolled;
	/** The close-chase rush (D-037) while it runs, the player it is for, and whether this sighting had one. */
	private @Nullable Rush rush;
	private @Nullable UUID rushTarget;
	private boolean rushed;
	/** Whether a player could see him when his own rules removed him (they never should). */
	private boolean seenWhenRemoved;
	/** Why his own rules removed him, or null while he is out. */
	private @Nullable String goneWhy;

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
				.add(Attributes.STEP_HEIGHT, STEP_HEIGHT)
				// Run off a bank into a lake and water does not slow him down (as with Depth Strider III).
				.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0)
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
		phase = Phase.IDLE;
		if (level() instanceof ServerLevel serverLevel) {
			bornTick = serverLevel.getServer().getTickCount();
		}
	}

	/** The horizontal distance to the player he appeared for; it scales his flee and approach distances. */
	void setSpawnDistance(double distance) {
		spawnDistance = distance;
	}

	/** The box every view check uses: the rendered model in any pose and yaw, plus a margin. */
	public static AABB viewBox(Vec3 feet) {
		return new AABB(feet.x - VIEW_HALF_WIDTH, feet.y - 0.1, feet.z - VIEW_HALF_WIDTH, feet.x + VIEW_HALF_WIDTH, feet.y + VIEW_HEIGHT, feet.z + VIEW_HALF_WIDTH);
	}

	public AABB viewBox() {
		return viewBox(position());
	}

	/** True if {@code pos} and the points {@code margin} blocks from it along x and z are all entity-ticking. */
	public static boolean tickingAround(ServerLevel level, Vec3 pos, int margin) {
		BlockPos c = BlockPos.containing(pos);
		return level.isPositionEntityTicking(c) && level.isPositionEntityTicking(c.offset(margin, 0, 0)) && level.isPositionEntityTicking(c.offset(-margin, 0, 0))
				&& level.isPositionEntityTicking(c.offset(0, 0, margin)) && level.isPositionEntityTicking(c.offset(0, 0, -margin));
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
		EntityConfig config = EntityConfig.get();
		List<ServerPlayer> players = observers(level);
		if (age == 0) {
			FigureApi.track(this);
			if (bornTick < 0) {
				holdYaw = getYRot(); // made without setup (/summon): keep the facing he was given
			}
			if (Double.isNaN(spawnDistance)) {
				spawnDistance = players.stream().mapToDouble(p -> SpotFinder.horizontal(p.position(), position())).min().orElse(Double.NaN);
			}
		}
		age++;
		phaseTicks++;

		// One rule for every removal below: never while in view, unless past everyone's full render distance.
		Watchers watchers = watchers(level);
		boolean seen = watchers.sees(level, viewBox());
		if (seen) {
			if (!everSeen) {
				firstSeenAge = age;
			}
			everSeen = true;
			seenTicks++;
			unseenTicks = 0;
		} else {
			unseenTicks++;
		}
		if (watchers.beyondRenderDistance(position())) {
			gone(level, "past the render distance");
			return;
		}
		if (!players.isEmpty() && leavesAtTickingEdge(tickingAroundHim(level), watchers)) {
			gone(level, "at the edge of the ticking range, out of view");
			return;
		}

		if (phase == Phase.GOING_UNDER || phase == Phase.UNDER) {
			goingUnder(level, watchers, config); // nothing else ends it: he is gone once covered and out of view
			return;
		}

		watch(level, players, config);

		ServerPlayer nearest = nearest(players);
		double nearestDistance = nearest == null ? Double.MAX_VALUE : SpotFinder.horizontal(nearest.position(), position());
		Chase nearestChase = nearest == null ? null : chases.get(nearest.getUUID());
		double closing = nearestChase == null ? 0.0 : nearestChase.closingSpeed();
		if (phase == Phase.RUSH) {
			if (!seen) {
				gone(level, "rushed past, out of view"); // the instant nobody can see him
				return;
			}
			rushStep(level, players, config);
			return;
		}
		if (!rushed && variant.mayRush() && nearest != null && nearestChase != null
				&& SightingRules.rushes(nearest.position().distanceTo(position()), config.rushTriggerDistance, nearestChase.speed(), closing,
						config.outrunFactor, config.maxRunSpeed, config.closeInFastSpeed)
				&& startRush(level, nearest, nearestChase, config)) {
			rushStep(level, players, config);
			return;
		}

		// Once seen, he stays for minSeenSeconds whatever happens, unless he fled.
		boolean mayEnd = SightingRules.mayEndOutOfView(everSeen, seenFor(), ModConfig.realTicks(config.minSeenSeconds), fled);
		if (mayEnd && phase == Phase.LEAVING && (everSeen || triggered) && !seen) {
			gone(level, "left and out of view");
			return;
		}
		if (phase == Phase.IDLE && variant.walksFromStart() && everSeen) {
			setPhase(Phase.LEAVING); // his back to you, he starts walking once you have seen him
		}
		long grace = ModConfig.realTicks(variant == Variant.COW && phase == Phase.IDLE ? config.cowGoneAfterUnseenSeconds : config.goneAfterUnseenSeconds);
		if (mayEnd && (everSeen || triggered) && unseenTicks >= grace) {
			gone(level, "out of view");
			return;
		}
		if (!everSeen && unseenTicks >= ModConfig.realTicks(config.unseenLifetimeSeconds)) {
			gone(level, "nobody saw him");
			return;
		}
		if (mayEnd && !triggered && age >= ModConfig.realTicks(config.maxLifetimeSeconds)) {
			walkAway(variant.gait() == Variant.Gait.SLOW ? Variant.Gait.SLOW : Variant.Gait.WALK);
		}

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
				long stareBackTicks = ModConfig.realTicks(config.stareBackSeconds);
				if (SightingRules.wouldBeReached(nearestDistance, closing, Math.max(0, stareBackTicks - phaseTicks) / 20.0)) {
					breakIntoRun(); // the stare back never lets anyone reach him
				} else if (phaseTicks >= stareBackTicks) {
					leaveOrGoUnder(level, nearestDistance, closing, config);
				}
			}
			case HIDING -> {
				if (SightingRules.wouldBeReached(nearestDistance, closing, HIDE_REACH_SECONDS)) {
					breakIntoRun();
				} else {
					hide(nearest, config);
				}
			}
			case LEAVING -> {
				if (gait != Variant.Gait.RUN && SightingRules.breaksIntoRun(closing, config.closeInFastSpeed, nearestDistance < fleeDistance(config))) {
					breakIntoRun();
				}
				double blocksPerSecond = gait == Variant.Gait.RUN ? runSpeed(nearest, config) : SightingRules.groundSpeed(speed(gait, config), BASE_SPEED);
				double modifier = gait == Variant.Gait.RUN ? SightingRules.speedModifier(blocksPerSecond, BASE_SPEED) : speed(gait, config);
				moveSpeed = blocksPerSecond;
				holdSpeedInAir(gait == Variant.Gait.RUN ? blocksPerSecond : 0.0);
				steerAway(level, nearest, modifier, blocksPerSecond);
			}
			default -> {
			}
		}
	}

	/** The players his rules watch: everyone alive and not spectating in his level. Game tests override it. */
	protected List<ServerPlayer> observers(ServerLevel level) {
		return level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
	}

	/** Everyone who could see him, for the one despawn rule ({@link Watchers}). Game tests override it. */
	protected Watchers watchers(ServerLevel level) {
		return Watchers.of(level);
	}

	/**
	 * {@link #tickingAround} his position with {@link #EDGE_TICK_MARGIN}. Game tests override it: only their own
	 * chunks are loaded there, so every figure would be at the edge.
	 */
	protected boolean tickingAroundHim(ServerLevel level) {
		return tickingAround(level, position(), EDGE_TICK_MARGIN);
	}

	// --- goes under (D-030) ---

	/**
	 * The stare back is over and he would walk or run off. Once per sighting, with {@code goUnderChance}, he goes
	 * under instead where he may: a variant that ends this way, not fled or run down, nobody near enough to reach him
	 * before he is covered, and diggable ground under him ({@link GoUnder#plan}).
	 */
	private void leaveOrGoUnder(ServerLevel level, double nearestDistance, double closing, EntityConfig config) {
		if (!goUnderRolled) {
			goUnderRolled = true;
			if (variant.mayGoUnder() && !fled && !outrunning && onGround() && getRandom().nextDouble() < config.goUnderChance()) {
				int[] depths = config.goUnderDepths();
				double seconds = (depths[1] + GoUnder.COVER + 1) * Math.max(0.05, config.goUnderDigSeconds);
				Optional<GoUnder.Plan> plan = SightingRules.wouldBeReached(nearestDistance, closing, seconds) ? Optional.empty()
						: GoUnder.plan(level, groundUnder(), depths, getRandom());
				if (plan.isPresent()) {
					startGoUnder(level, plan.get());
					return;
				}
			}
		}
		setPhase(Phase.LEAVING);
	}

	/**
	 * Debug ({@code /a1016 entity goesunder}) and tests: he ends the sighting by going under now, whatever the chance
	 * and the variant, if the ground under him allows it. Returns why not, or empty if he started.
	 */
	public Optional<String> forceGoUnder() {
		if (!(level() instanceof ServerLevel level)) {
			return Optional.of("not on the server");
		}
		if (phase == Phase.GOING_UNDER || phase == Phase.UNDER) {
			return Optional.of("already going under");
		}
		if (phase == Phase.RUSH) {
			return Optional.of("rushing past a player");
		}
		if (!onGround()) {
			return Optional.of("not on the ground");
		}
		int[] depths = EntityConfig.get().goUnderDepths();
		GoUnder.Check check = GoUnder.check(level, groundUnder(), depths[0], depths[1]);
		if (!check.ok()) {
			return check.refusal();
		}
		Optional<GoUnder.Plan> plan = GoUnder.plan(level, groundUnder(), depths, getRandom());
		if (plan.isEmpty()) {
			return Optional.of("no shaft fits");
		}
		goUnderRolled = true;
		startGoUnder(level, plan.get());
		return Optional.empty();
	}

	/** The ground block under the middle of his feet. */
	BlockPos groundUnder() {
		return BlockPos.containing(getX(), getY() - 0.2, getZ());
	}

	private void startGoUnder(ServerLevel level, GoUnder.Plan plan) {
		goUnder = GoUnder.begin(level, plan);
		triggered = true;
		setLow(false);
		setPhase(Phase.GOING_UNDER);
		A1016_02.LOGGER.debug("[a1016] figure ({}) goes under at {}, {} deep", variant.shortName(), plan.top().toShortString(), plan.depth());
	}

	/** Digging, covering, then waiting under the ground until nobody can see him. */
	private void goingUnder(ServerLevel level, Watchers watchers, EntityConfig config) {
		GoUnder.Status status = goUnder.tick(this, level, config);
		switch (status) {
			case COVERED -> {
				if (phase != Phase.UNDER) {
					setPhase(Phase.UNDER);
				}
				if (watchers.mayRemove(level, viewBox(), position())) {
					gone(level, "went under, covered and out of view");
				}
			}
			case STUCK -> {
				// A block of the shaft changed under him: he waits in the hole, and once nobody can see him the shaft
				// is put back as it was (remove).
				if (watchers.mayRemove(level, viewBox(), position())) {
					gone(level, "could not finish going under, out of view");
				}
			}
			case ABANDONED -> {
				goUnder = null; // nothing was dug: he leaves the ordinary way
				setPhase(Phase.LEAVING);
			}
			default -> {
			}
		}
	}

	/**
	 * Going under: stands over {@code center} (his column's), nudged onto it, facing down at the ground he digs (or
	 * up at the blocks he puts back). Returns how far (horizontally) he still is from it.
	 */
	double holdInShaft(Vec3 center, boolean lookUp) {
		getNavigation().stop();
		getMoveControl().setWait();
		airSpeedPerTick = 0.0;
		double dx = center.x - getX();
		double dz = center.z - getZ();
		double off = Math.sqrt(dx * dx + dz * dz);
		double y = getDeltaMovement().y;
		if (off > 1.0E-4) {
			double step = Math.min(off, 0.08);
			setDeltaMovement(dx / off * step, y, dz / off * step);
		} else {
			setDeltaMovement(0.0, y, 0.0);
		}
		holdBody();
		Vec3 ahead = Entity.calculateViewVector(0.0F, holdYaw).scale(0.3);
		getLookControl().setLookAt(getX() + ahead.x, getEyeY() + (lookUp ? 10.0 : -10.0), getZ() + ahead.z, 10.0F, 80.0F);
		return off;
	}

	/** Puts him exactly over the column once he is within a hair of it (no visible jump). */
	void snapToColumn(Vec3 center) {
		setPos(center.x, getY(), center.z);
		setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
	}

	/** The arm swing of a player digging; nothing else (no sound, no particles). */
	void swingArm() {
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
	}

	// --- the close-chase rush (D-037) ---

	/**
	 * A chaser he cannot outrun came within {@code rushTriggerDistance}: plans a pass beside them. Once per sighting.
	 * If no side gives a safe, walkable pass, he just runs off. True if the rush started.
	 */
	private boolean startRush(ServerLevel level, ServerPlayer chaser, Chase chase, EntityConfig config) {
		rushed = true;
		double step = config.maxRunSpeed / 20.0;
		Optional<Rush> plan = Rush.plan(position(), chaser.position(), chase.velocity(), config.rushPassOffset(), step, path -> walkable(level, path));
		if (plan.isEmpty()) {
			A1016_02.LOGGER.debug("[a1016] figure ({}): no safe pass by the chaser, he runs", variant.shortName());
			if (phase != Phase.LEAVING || gait != Variant.Gait.RUN) {
				breakIntoRun();
			}
			return false;
		}
		rush = plan.get();
		rushTarget = chaser.getUUID();
		triggered = true;
		fled = true;
		outrunning = true;
		gait = Variant.Gait.RUN;
		setLow(false);
		setPhase(Phase.RUSH);
		A1016_02.LOGGER.debug("[a1016] figure ({}) rushes past {}", variant.shortName(), chaser.getName().getString());
		return true;
	}

	/** Every point of the path has ground he can run on, within a step up or a short drop of the one before. */
	private boolean walkable(ServerLevel level, List<Vec3> path) {
		double y = getY();
		for (Vec3 point : path) {
			Vec3 feet = SpotFinder.standNear(level, point.x, point.z, y, 1, 2, getDimensions(getPose()));
			if (feet == null) {
				return false;
			}
			y = feet.y;
		}
		return true;
	}

	/**
	 * One tick of the rush: straight at the player, past them on the planned side, never within the pass offset of
	 * them ({@link Rush#step}). He is moved directly (no navigation), so the pass is exactly what was planned. Out of
	 * time, out of ground, or the player gone: he runs off the ordinary way.
	 */
	private void rushStep(ServerLevel level, List<ServerPlayer> players, EntityConfig config) {
		ServerPlayer target = players.stream().filter(p -> p.getUUID().equals(rushTarget)).findFirst().orElse(null);
		Chase chase = target == null ? null : chases.get(target.getUUID());
		if (rush == null || target == null || chase == null || phaseTicks > ModConfig.realTicks(config.rushMaxSeconds)) {
			endRush();
			return;
		}
		double step = config.maxRunSpeed / 20.0;
		Vec3 next = rush.step(position(), target.position(), chase.velocity(), step);
		if (SpotFinder.standNear(level, next.x, next.z, getY(), 1, 3, getDimensions(getPose())) == null) {
			endRush(); // a wall, water or a drop ahead
			return;
		}
		getNavigation().stop();
		getMoveControl().setWait();
		airSpeedPerTick = 0.0;
		Vec3 move = next.subtract(position());
		setDeltaMovement(move.x, getDeltaMovement().y, move.z);
		if (move.horizontalDistanceSqr() > 1.0E-6) {
			holdYaw = yawToward(position(), next);
			holdBody();
		}
		if (!rush.passed()) {
			getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ(), 30.0F, 40.0F);
		} else {
			setYHeadRot(holdYaw);
		}
		moveSpeed = config.maxRunSpeed;
	}

	private void endRush() {
		rush = null;
		breakIntoRun();
	}

	/** The flee distance for him: the configured one, scaled down if he appeared close (D-035). */
	public double fleeDistance(EntityConfig config) {
		return SightingRules.fleeDistance(config.fleeDistance, config.fleeSpawnFraction, spawnDistance);
	}

	/** The approach that ends the sighting for him, scaled the same way. */
	public double approachBlocks(EntityConfig config) {
		return SightingRules.approachBlocks(config.approachBlocks, config.approachSpawnFraction, spawnDistance);
	}

	/** He leaves now, at the adaptive run. */
	private void breakIntoRun() {
		gait = Variant.Gait.RUN;
		outrunning = true;
		setLow(false);
		setPhase(Phase.LEAVING);
	}

	/** His run speed this tick in blocks per second (D-036), from the chasing player's recent speed. */
	private double runSpeed(@Nullable ServerPlayer chaser, EntityConfig config) {
		Chase chase = chaser == null ? null : chases.get(chaser.getUUID());
		double chaserSpeed = chase == null ? 0.0 : chase.speed();
		if (chaserSpeed * config.outrunFactor > config.baseRunSpeed) {
			outrunning = true;
		}
		return SightingRules.runSpeed(config.baseRunSpeed, chaserSpeed, config.outrunFactor, config.maxRunSpeed);
	}

	/**
	 * The three ways a sighting ends ({@link SightingRules#endCause}): coming within the flee distance (any time), and
	 * once he has been seen for {@code minSeenSeconds}, staring at him for {@code stareSeconds} or closing
	 * {@code approachBlocks} on him since he was first seen. A single step, strafing or turning never counts. The flee
	 * and approach distances shrink for a figure that appeared close ({@link #fleeDistance}). Also feeds each player's
	 * {@link Chase}.
	 */
	private void watch(ServerLevel level, List<ServerPlayer> players, EntityConfig config) {
		ServerPlayer looker = null;
		ServerPlayer approacher = null;
		ServerPlayer near = null;
		double cosCone = Math.cos(Math.toRadians(config.stareConeDegrees));
		double flee = fleeDistance(config);
		double approach = approachBlocks(config);
		for (ServerPlayer player : players) {
			double d = SpotFinder.horizontal(player.position(), position());
			chases.computeIfAbsent(player.getUUID(), k -> new Chase()).update(player.getX(), player.getZ(), d);
			if (d < flee) {
				near = player;
			}
			if (everSeen && approaches.computeIfAbsent(player.getUUID(), k -> new Approach()).update(d, config.approachStepBlocks) >= approach) {
				approacher = player;
			}
			if (looker == null && isLookedAtBy(level, player, cosCone)) {
				looker = player;
			}
		}
		stareTicks = looker != null ? stareTicks + 1 : Math.max(0, stareTicks - 2);
		if (triggered) {
			return;
		}
		boolean stareDone = !stared && looker != null && stareTicks >= ModConfig.realTicks(config.stareSeconds);
		switch (SightingRules.endCause(near != null, stareDone, approacher != null, seenFor(), ModConfig.realTicks(config.minSeenSeconds))) {
			case FLEE -> {
				fled = true;
				trigger(near);
			}
			case STARE -> {
				stared = true;
				Attention.trigger(level.getServer(), AttentionTrigger.STARED_AT_HIM);
				EntityData.get(level.getServer()).recordStared();
				trigger(looker);
			}
			case APPROACH -> trigger(approacher);
			case NONE -> {
			}
		}
	}

	/** Ticks since he was first seen, or -1. */
	public long seenFor() {
		return firstSeenAge < 0 ? -1 : age - firstSeenAge;
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

	/**
	 * Makes him turn and walk (or run) away into the fog now. He despawns once out of view or past the fog. Going
	 * under or rushing past someone, he is already on his way out: nothing changes.
	 */
	public void walkAway(Variant.Gait leaveGait) {
		if (phase == Phase.GOING_UNDER || phase == Phase.UNDER || phase == Phase.RUSH) {
			return;
		}
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
			ticksSinceRepath = 0;
			repathFailed = false;
			airSpeedPerTick = 0.0;
			getNavigation().stop();
		}
	}

	/**
	 * While running, he keeps {@code blocksPerSecond} through the air (off a ledge, over a jump) the way a
	 * sprint-jumping player does; a plain mob loses most of its speed in the air and would be caught there.
	 * 0 turns it off.
	 */
	void holdSpeedInAir(double blocksPerSecond) {
		airSpeedPerTick = Math.max(0.0, blocksPerSecond) / 20.0;
	}

	@Override
	protected float getFlyingSpeed() {
		double input = getSpeed();
		if (airSpeedPerTick > 0.0 && input > 1.0E-3) {
			// This tick in the air he moves by what he carries plus input * this: top it up to airSpeedPerTick. (On the
			// ground friction takes most of his speed each tick and his input gives it back, so off a ledge he would
			// carry only about half of it.)
			double carried = getDeltaMovement().horizontalDistance();
			return (float) (Math.max(0.0, airSpeedPerTick - carried) / input);
		}
		return super.getFlyingSpeed();
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

	/**
	 * Walks (or runs) away from the player toward a point that is inside the entity-ticking range and no farther than
	 * just past the fog edge (where he is gone). Tries straight away first, then turns up to 90 degrees. The path
	 * reaches {@link #LEAVE_AHEAD_SECONDS} of travel ahead and is renewed before he gets to its end, so he never slows
	 * down at a path's last node; the speed follows {@code modifier} every tick without a new path.
	 */
	private void steerAway(ServerLevel level, @Nullable ServerPlayer from, double modifier, double blocksPerSecond) {
		PathNavigation navigation = getNavigation();
		navigation.setSpeedModifier(modifier);
		ticksSinceRepath++;
		boolean moving = !navigation.isDone();
		boolean nearEnd = moving && ticksSinceRepath >= 5 && remainingPath(navigation) < Math.max(4.0, blocksPerSecond);
		boolean due = moving ? ticksSinceRepath >= REPATH_TICKS || nearEnd : !repathFailed || ticksSinceRepath >= 10;
		// No path can start in mid-air (off a ledge, over a step): he keeps the one he has and plans on landing.
		if (!due || !onGround() && !isInLiquid()) {
			return;
		}
		ticksSinceRepath = 0;
		repathFailed = !repath(level, from, modifier, Math.max(12.0, blocksPerSecond * LEAVE_AHEAD_SECONDS));
		if (repathFailed && !moving) {
			// Nowhere to go inside both limits: he stands, and is gone as soon as he is out of view.
			navigation.stop();
		}
	}

	/**
	 * Paths {@code ahead} blocks away from the player, straight away first. False if no direction works. A direction
	 * that fails leaves the current path alone, so he never stops while looking for the next one.
	 */
	private boolean repath(ServerLevel level, @Nullable ServerPlayer from, double modifier, double ahead) {
		Vec3 origin = from != null ? from.position() : position().subtract(Entity.calculateViewVector(0.0F, holdYaw));
		Vec3 away = position().subtract(origin).horizontal();
		if (away.lengthSqr() < 1.0) {
			// The player is (nearly) on top of him: keep running the way he is going rather than turning on the spot.
			away = Entity.calculateViewVector(0.0F, phase == Phase.LEAVING && getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 ? getYRot() : holdYaw)
					.horizontal();
		}
		away = away.normalize();
		double reach = from != null ? FogEdge.of(from, false).renderLimit() + 2.0 : Double.MAX_VALUE;
		for (double turn : new double[] {0, 30, -30, 60, -60, 90, -90}) {
			double r = Math.toRadians(turn);
			Vec3 dir = new Vec3(away.x * Math.cos(r) - away.z * Math.sin(r), 0.0, away.x * Math.sin(r) + away.z * Math.cos(r));
			Vec3 target = position().add(dir.scale(ahead));
			Vec3 rel = target.subtract(origin).horizontal();
			if (rel.length() > reach) {
				target = origin.add(rel.normalize().scale(reach));
			}
			if (!tickingAround(level, target, LEAVE_TICK_MARGIN)) {
				continue;
			}
			int x = Mth.floor(target.x);
			int z = Mth.floor(target.z);
			// Under a ceiling (the Nether) the heightmap is the roof: aim at his own height instead.
			int y = level.dimensionType().hasCeiling() ? Mth.floor(getY()) : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			Path path = getNavigation().createPath(x + 0.5, y, z + 0.5, 1);
			Node end = path == null ? null : path.getEndNode();
			if (end != null && path.getNodeCount() > 1 && horizontalTo(end) >= 2.0 && getNavigation().moveTo(path, modifier)) {
				return true;
			}
		}
		return false;
	}

	private double horizontalTo(Node node) {
		double dx = node.x + 0.5 - getX();
		double dz = node.z + 0.5 - getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Horizontal distance to the end of the current path, 0 if there is none. */
	private double remainingPath(PathNavigation navigation) {
		Path path = navigation.getPath();
		Node end = path == null ? null : path.getEndNode();
		return end == null ? 0.0 : horizontalTo(end);
	}

	/**
	 * Near the edge of the entity-ticking range his chunk may stop ticking and he would stand frozen. If nobody can see
	 * him (or he is past everyone's full render distance) he goes; in view he stays, frozen or not, which reads as
	 * staring, until he is out of view.
	 *
	 * @param tickingAround {@link #tickingAround} with {@link #EDGE_TICK_MARGIN} at his position
	 */
	public boolean leavesAtTickingEdge(boolean tickingAround, Watchers watchers) {
		return !tickingAround && watchers.mayRemove(level(), viewBox(), position());
	}

	private void gone(ServerLevel level, String why) {
		Watchers now = watchers(level);
		seenWhenRemoved = !now.beyondRenderDistance(position()) && now.sees(level, viewBox());
		goneWhy = why;
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

	/** The navigation speed modifier of a gait; the run's is the one for {@code baseRunSpeed} (it adapts while leaving). */
	private static double speed(Variant.Gait gait, EntityConfig config) {
		return switch (gait) {
			case SLOW -> config.slowWalkSpeed;
			case WALK -> config.walkSpeed;
			case RUN -> SightingRules.speedModifier(config.baseRunSpeed, BASE_SPEED);
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

	public boolean fled() {
		return fled;
	}

	/** Horizontal distance to the player he appeared for, NaN if unknown. */
	public double spawnDistance() {
		return spawnDistance;
	}

	/** True once he ran from a chaser at the adaptive speed (D-036). */
	public boolean outrunning() {
		return outrunning;
	}

	/** The speed he last moved off at while leaving, in blocks per second on flat ground (0 before). */
	public double moveSpeed() {
		return moveSpeed;
	}

	/** Distance this player has closed on him since he was first seen (0 before). */
	public double closedBy(UUID player) {
		Approach approach = approaches.get(player);
		return approach == null ? 0.0 : approach.closed();
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

	/** His dig while he goes under (or after), null if he never did. */
	public @Nullable GoUnder goUnder() {
		return goUnder;
	}

	/** True once this sighting had its rush (or tried to). */
	public boolean rushed() {
		return rushed;
	}

	/** The rush while it runs, else null. */
	public @Nullable Rush rush() {
		return phase == Phase.RUSH ? rush : null;
	}

	/** True if a player could see him when his own rules removed him. Never, by the rules; the tests check it. */
	public boolean seenWhenRemoved() {
		return seenWhenRemoved;
	}

	/** Why his own rules removed him ({@code "went under, covered and out of view"}...), or null. */
	public @Nullable String goneWhy() {
		return goneWhy;
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

	/**
	 * Removed while going under with the shaft open (past the render distance, a debug clear, or a dig he could not
	 * finish): the blocks he dug go back where they were first ({@link GoUnder#putBack}). Never on a chunk unload.
	 */
	@Override
	public void remove(RemovalReason reason) {
		if (goUnder != null && (reason == RemovalReason.DISCARDED || reason == RemovalReason.KILLED) && level() instanceof ServerLevel serverLevel) {
			goUnder.putBack(serverLevel);
		}
		super.remove(reason);
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
