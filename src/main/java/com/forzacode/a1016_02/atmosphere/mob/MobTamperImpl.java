package com.forzacode.a1016_02.atmosphere.mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The real {@link MobTamper} (D-017). Effects are temporary and in memory only: the state sits in a field the mob
 * mixin adds ({@link TamperedMob}), and every effect ends on its own deadline, on {@link #release}, when the mob
 * unloads, or when the server stops. What vanilla itself saves of a hunt (a neutral mob's anger at its player) can reach
 * the disk in an autosave; such a save also carries the mark {@link #HUNT_SAVED_KEY}, and a mob that loads with it is
 * calmed ({@link #onLoad}, R12-28). Nothing else is written to a mob's save data.
 * <ul>
 * <li>freeze: the mob's whole AI step is skipped (goals, brain, navigation, move/look/jump controls), its inputs are
 * zeroed and its horizontal drift stopped. Gravity and pushing still apply.</li>
 * <li>face: frozen mobs turn their whole body toward the point at {@link AtmosphereConfig#tamperTurnDegreesPerTick};
 * mobs that are not frozen keep their head on the point (set right before the look control runs).</li>
 * <li>silence: the ambient sound timer still runs but the sound is skipped.</li>
 * <li>moveOutOfView: teleports only when both the mob's box and its box at the destination are out of view.</li>
 * <li>steer (D-096): the mob's AI step runs only its navigation, move, look and jump controls (no goal or target
 * selector, no mob-specific step), toward a point it walks to by its own path at its own walk speed times a factor;
 * whatever goals ran are stopped when it starts. It floats in water as its float goal would. A freeze ends it.</li>
 * <li>hunt (D-096): the mob's own AI runs; each AI step puts the player back as its target while they are within its
 * follow range, which is doubled for the hunt (a transient modifier, never saved), and lets go of them past it; a
 * neutral mob keeps its anger at them. Calmed when the player is gone, at its deadline or on {@link #calm}; a hurt from another player ends it
 * uncalmed (vanilla's anger then).</li>
 * </ul>
 * Everything happens to the mob on the server, so every player sees the same trick (D-060). Atmosphere's cards
 * {@link #bind} what they do to everyone it is for: the target and whoever shared the moment. While any of them is
 * still here the trick goes on (D-062.5: a scene keeps going if another participant is present); once none is (each
 * left, died, turned spectator or changed dimension) the mob is released as soon as nobody can see it
 * ({@link #releaseFor} on the participation event, and {@link #releaseOrphans} every second), never left frozen for
 * nobody, never let go in anyone's view. Other workstreams bind through {@code MobTamper.bind} (entity's false
 * positives do, for their one player); an effect nobody binds only expires.
 * Server thread only.
 */
public final class MobTamperImpl implements MobTamper {
	public static final MobTamperImpl INSTANCE = new MobTamperImpl();
	/** How often bound mobs are checked against their player (also released at once on the participation event). */
	private static final int OWNER_CHECK_TICKS = 20;
	/** At most this many mobs steered at once in the whole world (D-104); a new steer past it is refused. */
	public static final int MAX_STEERED = 48;
	/** A steered mob's path is made again at most this often (ticks) ... */
	static final int PATH_EVERY_TICKS = 10;
	/** ... and only when its target moved more than this (blocks) since, or its walk ended short of it. */
	static final double PATH_MOVE = 1.5;
	/** Within this of its target (blocks) a steered mob has arrived and stands still. */
	static final double ARRIVED = 1.0;
	/** A tamed or owned mob with its owner online within this (blocks) is never steered. */
	static final double OWNER_NEAR = 48.0;
	/** The hunt's doubling of the follow range (transient: never saved with the mob; an opaque id, R13-11). */
	private static final Identifier RANGE = A1016_02.id("sc_r");
	/**
	 * The one mark a mob's save data may carry (R12-28): it was saved (an autosave) while set on a player, so vanilla wrote
	 * its anger at them. Nothing of a hunt is running after a load, so a mob that loads with the mark is calmed then
	 * ({@link #onLoad}); a stop calms every hunt before the save anyway. Opaque, and vanilla skips it.
	 */
	public static final String HUNT_SAVED_KEY = "a1016_02:h";
	private static final AttributeModifier DOUBLE_RANGE = new AttributeModifier(RANGE, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

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
		// A freeze wins over a steer: the steer ends.
		endSteer(state);
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

	// --- instruments (D-096; docs/SELF_CREATED_2.md, "SC2-0a") ---

	/**
	 * Walks the mob to {@code to} by its own navigation (see {@link MobTamper#steer}). A new call replaces the target
	 * and the deadline; the first one stops whatever goals were running.
	 */
	@Override
	public boolean steer(Mob mob, Vec3 to, double speed, int ticks) {
		if (ticks <= 0 || to == null || !Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z) || !(speed > 0.0) || !Double.isFinite(speed)) {
			return false;
		}
		if (mob == null || steerRefusal(mob, SelfCreated.enabled(), steeredCount() - (isSteered(mob) ? 1 : 0)) != null) {
			return false;
		}
		TamperState state = stateFor(mob);
		long now = now(mob);
		boolean fresh = !state.steered(now);
		state.steerTarget = to;
		state.steerUntil = now + ticks;
		if (fresh) {
			state.steerSpeed = speed;
			state.steerPathTarget = null;
			state.steerNextPath = Long.MIN_VALUE;
			// Its own goals stop now (each its own stop: an eating sheep stops eating), and stay off while steered.
			((TamperedMob) mob).a1016_02$stopGoals();
			mob.getNavigation().stop();
		} else if (speed != state.steerSpeed) {
			state.steerSpeed = speed;
			mob.getNavigation().setSpeedModifier(speed);
		}
		return true;
	}

	/**
	 * Why {@code mob} may not be steered now, or null if it may: with the layer {@code on} or off and {@code steeredElsewhere}
	 * other mobs steered in the world right now (the cap, {@link #MAX_STEERED}). Plain words, never shown to players.
	 */
	public @Nullable String steerRefusal(Mob mob, boolean on, int steeredElsewhere) {
		if (!on) {
			return "the layer is switched off";
		}
		String common = refusal(mob);
		if (common != null) {
			return common;
		}
		if (mob.isLeashed()) {
			return "it is on a leash";
		}
		if (mob instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player owner && !owner.isRemoved() && owner.level() == mob.level()
				&& owner.distanceToSqr(mob) <= OWNER_NEAR * OWNER_NEAR) {
			return "its owner is within " + (int) OWNER_NEAR;
		}
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		long now = now(mob);
		if (state != null && state.frozen(now)) {
			return "it is frozen (a freeze wins)";
		}
		if (state != null && state.hunting(now)) {
			return "it is set on a player (calm it first)";
		}
		if (steeredElsewhere >= MAX_STEERED) {
			return MAX_STEERED + " mobs are steered already";
		}
		return null;
	}

	/** Refusals both instruments share: not a living mob here, ridden or riding, without AI, a boss, or driven by a brain. */
	private static @Nullable String refusal(Mob mob) {
		if (!canTamper(mob)) {
			return "not a living mob in a server level, or a player rides it";
		}
		if (mob.isPassenger() || mob.isVehicle()) {
			return "it rides or is ridden";
		}
		if (mob.isNoAi()) {
			return "it has no AI";
		}
		if (mob instanceof EnderDragon || mob instanceof WitherBoss) {
			return "a boss";
		}
		// Brain-driven mobs (villagers, goats, axolotls, frogs, camels, sniffers, armadillos, allays, piglins, hoglins,
		// wardens, breezes ...) are moved by their brain, not by goals; a goal-driven mob's brain is empty.
		if (!mob.getBrain().isBrainDead()) {
			return "its brain drives it, not goals";
		}
		return null;
	}

	@Override
	public boolean isSteered(Mob mob) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		return state != null && state.steered(now(mob));
	}

	/** How many mobs are steered in the world right now (at most {@link #MAX_STEERED}). */
	public int steeredCount() {
		int count = 0;
		for (Mob mob : tracked) {
			if (isSteered(mob)) {
				count++;
			}
		}
		return count;
	}

	/** Sets the mob on {@code target} (see {@link MobTamper#hunt}); it ends a steer. */
	@Override
	public boolean hunt(Mob mob, ServerPlayer target, int ticks) {
		if (ticks <= 0 || mob == null || huntRefusal(mob, target, SelfCreated.enabled()) != null) {
			return false;
		}
		TamperState state = stateFor(mob);
		long now = now(mob);
		endSteer(state);
		state.huntTarget = target;
		state.huntUntil = now + ticks;
		state.huntSince = mob.tickCount;
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null && !range.hasModifier(RANGE)) {
			range.addTransientModifier(DOUBLE_RANGE);
		}
		mob.setTarget(target);
		if (mob instanceof NeutralMob neutral) {
			neutral.setPersistentAngerTarget(EntityReference.of(target));
			neutral.setTimeToRemainAngry(ticks);
		}
		return true;
	}

	/**
	 * Why {@code mob} may not be set on {@code target} now, or null if it may, with the layer {@code on} or off. Plain
	 * words, never shown to players.
	 */
	public @Nullable String huntRefusal(Mob mob, @Nullable ServerPlayer target, boolean on) {
		if (!on) {
			return "the layer is switched off";
		}
		String common = refusal(mob);
		if (common != null) {
			return common;
		}
		if (!(mob instanceof NeutralMob) && !(mob instanceof Enemy)) {
			return "it cannot attack (neither neutral nor an enemy)";
		}
		if (mob instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
			return "it is tamed or owned";
		}
		if (mob instanceof AbstractVillager || mob instanceof AbstractGolem) {
			return "a villager or a golem";
		}
		if (target == null || target.isRemoved() || !target.isAlive() || target.level() != mob.level()) {
			return "the player is not a living player in the mob's level";
		}
		if (target.isCreative() || target.isSpectator()) {
			return "the player is in creative or spectator";
		}
		return null;
	}

	@Override
	public boolean isHunting(Mob mob) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		return state != null && state.hunting(now(mob));
	}

	/** The player the mob is set on right now, or null. */
	public @Nullable ServerPlayer huntTargetOf(Mob mob) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		return state != null && state.hunting(now(mob)) ? state.huntTarget : null;
	}

	/** Ends the mob's hunt if it has one and clears its anger, target, last attacker and running goals (see {@link MobTamper#calm}). */
	@Override
	public void calm(Mob mob) {
		if (mob == null) {
			return;
		}
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		if (state != null) {
			endHunt(mob, state, false);
		}
		pacify(mob);
		if (state != null && state.idle(now(mob))) {
			release(mob);
		}
	}

	/** Ends the steer on this state (nothing if none); the navigation is the caller's. */
	private static void endSteer(TamperState state) {
		state.steerTarget = null;
		state.steerUntil = Long.MIN_VALUE;
		state.steerPathTarget = null;
	}

	/** Ends the hunt on this state (nothing if none): the follow range back, and calmed if {@code calm}. */
	private static void endHunt(Mob mob, TamperState state, boolean calm) {
		if (state.huntTarget == null) {
			return;
		}
		state.huntTarget = null;
		state.huntUntil = Long.MIN_VALUE;
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null) {
			range.removeModifier(RANGE);
		}
		if (calm) {
			pacify(mob);
		}
	}

	/** No anger, no target, no memory of who hurt it, no running goal: its own AI starts over. */
	private static void pacify(Mob mob) {
		if (mob instanceof NeutralMob neutral) {
			neutral.stopBeingAngry();
		}
		mob.setTarget(null);
		mob.setLastHurtByMob(null);
		mob.setAggressive(false);
		((TamperedMob) mob).a1016_02$stopGoals();
		mob.getNavigation().stop();
	}

	/** A player a hunt may go on with: not gone, alive, in the mob's level, neither creative nor spectator. */
	static boolean validTarget(Mob mob, @Nullable ServerPlayer player) {
		return player != null && !player.isRemoved() && player.isAlive() && player.level() == mob.level() && !player.isCreative() && !player.isSpectator();
	}

	/**
	 * Takes the mob's custom name for good (D-086; docs/SELF_CREATED.md 07), out of everyone's view
	 * ({@code TraceService.isOutOfView}): see {@link #unname(Mob, String, BiPredicate)}.
	 */
	@Override
	public boolean unname(Mob mob, String cause) {
		return unname(mob, cause, (level, box) -> Services.traces().isOutOfView(level, box));
	}

	/**
	 * {@link #unname(Mob, String)} with {@code outOfView} as the view rule (a card's own, for tests): refused (false,
	 * nothing changed) while the layer is off ({@link SelfCreated#enabled}), for a mob without a custom name, one not
	 * alive or not in a server level, or whose box is in view. The name is recorded first
	 * ({@link TraceLedger#recordName}, which may refuse); only then are {@code CustomName} and {@code CustomNameVisible}
	 * cleared. Not an effect: {@link #isTampered} and {@link #release} never touch it; {@code TraceService.undo} gives it
	 * back.
	 */
	public boolean unname(Mob mob, String cause, BiPredicate<ServerLevel, AABB> outOfView) {
		if (!SelfCreated.enabled() || mob == null || cause == null || !mob.hasCustomName() || !mob.isAlive() || mob.isRemoved()
				|| !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		Component name = mob.getCustomName();
		if (name == null || !outOfView.test(level, mob.getBoundingBox()) || TraceLedger.recordName(level, mob, name, cause) == null) {
			return false;
		}
		mob.setCustomName(null);
		mob.setCustomNameVisible(false);
		return true;
	}

	/**
	 * 23 (R11-11): holds the mob's arms up (vanilla's own pose for a zombie about to strike: its aggressive flag) while it is
	 * held; {@link #release} puts them back as they were. False if it may not be touched.
	 */
	public boolean raiseArms(Mob mob) {
		if (!canTamper(mob)) {
			return false;
		}
		TamperState state = stateFor(mob);
		if (!state.armsRaised) {
			state.armsWere = mob.isAggressive();
			state.armsRaised = true;
		}
		mob.setAggressive(true);
		return true;
	}

	@Override
	public void release(Mob mob) {
		TamperedMob duck = (TamperedMob) mob;
		TamperState state = duck.a1016_02$tamper();
		if (state != null && state.armsRaised) {
			mob.setAggressive(state.armsWere || mob.getTarget() != null);
		}
		if (state != null) {
			// A hunt is calmed (nothing it was set on lingers), a steer stops where it is.
			endHunt(mob, state, true);
			duck.a1016_02$setTamper(null);
			mob.getNavigation().stop();
		}
		tracked.remove(mob);
	}

	// --- the player an effect is for ---

	/**
	 * Ties what is being done to {@code mob} right now to {@code player} (the card's target): it is released once that
	 * player is no longer a living participant in the mob's level and nobody can see it. Does nothing if the mob is not
	 * tampered. The latest bind wins.
	 */
	@Override
	public void bind(Mob mob, UUID player) {
		bind(mob, List.of(player));
	}

	/**
	 * Ties what is being done to {@code mob} right now to everyone in {@code players}: a card's target and everyone who
	 * shared the moment (D-060), the target first. It goes on while any of them is a living participant in the mob's
	 * level, and is released once none is and nobody can see it (D-062.5: a scene keeps going while another participant
	 * is there, and stops out of view). Does nothing if the mob is not tampered. The latest bind wins.
	 */
	public void bind(Mob mob, Collection<UUID> players) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		if (state != null) {
			state.owners = List.copyOf(new LinkedHashSet<>(players));
		}
	}

	/** The first player the mob's effect is for (the card's target), or null (unbound or not tampered). */
	public @Nullable UUID ownerOf(Mob mob) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		return state == null ? null : state.owner();
	}

	/** Everyone the mob's effect is for, the card's target first (empty if unbound or not tampered). */
	public List<UUID> ownersOf(Mob mob) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		return state == null ? List.of() : state.owners();
	}

	/**
	 * That player left or turned spectator ({@code PARTICIPATION}): releases every mob bound to them that nobody else
	 * it is for is still here for (by {@code watch}'s look-up; the one leaving never counts), once nobody can see it.
	 * One still in view is let go by {@link #releaseOrphans} later. Returns how many.
	 */
	public int releaseFor(MinecraftServer server, UUID player, PlayerWatch watch) {
		int released = 0;
		for (Mob mob : new ArrayList<>(tracked)) {
			TamperState state = ((TamperedMob) mob).a1016_02$tamper();
			// A hunt on them is calmed at once, in view or not: there is nobody to be set on any more.
			boolean calmed = state != null && state.huntTarget != null && state.huntTarget.getUUID().equals(player);
			if (calmed) {
				endHunt(mob, state, true);
			}
			List<UUID> owners = ownersOf(mob);
			if (owners.contains(player) && !anyHere(server, watch, mob, owners, player) && outOfView(mob)) {
				release(mob);
				released++;
			} else if (calmed) {
				if (state.idle(now(mob))) {
					release(mob);
				}
				released++;
			}
		}
		return released;
	}

	/**
	 * Releases every bound mob none of whose players is here by {@code watch}'s look-up (D-062.1): offline, a
	 * spectator, dead, or in another dimension than the mob; and only once nobody can see it (D-062.5): one still in
	 * view is let go on a later check, or when its time runs out. Returns how many.
	 */
	public int releaseOrphans(MinecraftServer server, PlayerWatch watch) {
		return releaseOrphans(server, watch, MobTamperImpl::outOfView);
	}

	/** {@link #releaseOrphans(MinecraftServer, PlayerWatch)} with {@code outOfView} as the view rule (tests). */
	public int releaseOrphans(MinecraftServer server, PlayerWatch watch, Predicate<Mob> outOfView) {
		int released = 0;
		for (Mob mob : new ArrayList<>(tracked)) {
			List<UUID> owners = ownersOf(mob);
			if (!owners.isEmpty() && !anyHere(server, watch, mob, owners, null) && outOfView.test(mob)) {
				release(mob);
				released++;
			}
		}
		return released;
	}

	/** True if any of {@code players} but {@code except} is a living participant in the mob's level. */
	private static boolean anyHere(MinecraftServer server, PlayerWatch watch, Mob mob, List<UUID> players, @Nullable UUID except) {
		for (UUID player : players) {
			if (!player.equals(except) && watch.participant(server, player).filter(p -> watch.isTargetable(p) && p.level() == mob.level()).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/** Nobody in the mob's level (spectators and the dead included) can see it. */
	private static boolean outOfView(Mob mob) {
		return mob.level() instanceof ServerLevel level && Services.traces().isOutOfView(level, mob.getBoundingBox());
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

	@Override
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

	/**
	 * Ends expired effects, forgets removed mobs, calms hunts whose player is gone, ends steers and hunts while the layer
	 * is off, and every second releases mobs whose player is gone. Every server tick.
	 */
	public void tick(MinecraftServer server) {
		if (tracked.isEmpty()) {
			return;
		}
		boolean on = SelfCreated.enabled();
		for (Mob mob : new ArrayList<>(tracked)) {
			expire(mob, on);
		}
		if (server.getTickCount() % OWNER_CHECK_TICKS == 0) {
			releaseOrphans(server, Services.watch());
		}
	}

	/**
	 * {@link #tick}'s step for one mob, with the layer {@code on} or off: a removed mob is forgotten; a steer past its
	 * deadline, or while the layer is off, ends; a hunt past its deadline, whose player is gone ({@link #validTarget}) or
	 * while the layer is off, is calmed; then a mob with nothing left on it is released. True if it was released.
	 */
	public boolean expire(Mob mob, boolean on) {
		TamperState state = ((TamperedMob) mob).a1016_02$tamper();
		if (mob.isRemoved() || state == null) {
			release(mob);
			return true;
		}
		long now = now(mob);
		if (state.steerTarget != null && (!on || !state.steered(now))) {
			endSteer(state);
			mob.getNavigation().stop();
		}
		if (state.huntTarget != null && (!on || !state.hunting(now) || !validTarget(mob, state.huntTarget))) {
			endHunt(mob, state, true);
		}
		if (state.idle(now)) {
			release(mob);
			return true;
		}
		return false;
	}

	/** Releases a mob that leaves its level (chunk unload, death, discard, dimension change). */
	public void onUnload(Entity entity) {
		if (entity instanceof Mob mob && (tracked.contains(mob) || isTampered(mob))) {
			release(mob);
		}
	}

	/**
	 * Fabric's ENTITY_LOAD (R12-28): a mob saved while set on a player (an autosave mid-hunt, then a crash) loads with the
	 * anger vanilla saved and no hunt running: it is calmed (anger, target and last attacker cleared), so nothing goes on
	 * without its scene, its tells and its ends.
	 */
	public void onLoad(Entity entity) {
		if (entity instanceof Mob mob && mob instanceof TamperedMob tampered && tampered.a1016_02$takeHuntSaved() && !isHunting(mob)) {
			if (mob instanceof NeutralMob neutral) {
				neutral.stopBeingAngry();
			}
			mob.setTarget(null);
			mob.setLastHurtByMob(null);
		}
	}

	/** The mob mixin's save hook: true while the mob is set on a player (its save then carries {@link #HUNT_SAVED_KEY}). */
	public static boolean savedHunting(Mob mob, @Nullable TamperState state) {
		return state != null && state.hunting(now(mob));
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

	/**
	 * At the start of the AI step of a mob that is not frozen: true if it is steered, and the mixin then runs only its
	 * navigation, move, look and jump controls (no goal or target selector). Makes the path again when the target moved
	 * more than {@link #PATH_MOVE} since the last one or the walk ended short of it, at most every
	 * {@link #PATH_EVERY_TICKS}; keeps it afloat in water as its float goal would.
	 */
	public static boolean onSteeredStep(Mob mob, TamperState state) {
		long now = now(mob);
		Vec3 to = state.steerTarget;
		if (to == null || !state.steered(now)) {
			return false;
		}
		PathNavigation navigation = mob.getNavigation();
		Vec3 last = state.steerPathTarget;
		boolean moved = last == null || last.distanceToSqr(to) > PATH_MOVE * PATH_MOVE;
		boolean endedShort = navigation.isDone() && !arrived(mob, to);
		if ((moved || endedShort) && now >= state.steerNextPath) {
			// To the target's own block (reach 0), so it ends on the point, not a block short of it.
			navigation.moveTo(to.x, to.y, to.z, 0, state.steerSpeed);
			state.steerPathTarget = to;
			state.steerNextPath = now + PATH_EVERY_TICKS;
		}
		if ((mob.isInFluidDeeperThan(mob.getFluidJumpThreshold(), FluidTags.ENTITY_FLOATABLE) || mob.isInLava()) && mob.getRandom().nextFloat() < 0.8F) {
			mob.getJumpControl().jump();
		}
		return true;
	}

	/** A steered mob is at its point: in the point's block, or within {@link #ARRIVED} of it across. */
	static boolean arrived(Mob mob, Vec3 to) {
		double dx = mob.getX() - to.x;
		double dz = mob.getZ() - to.z;
		return dx * dx + dz * dz <= ARRIVED * ARRIVED || mob.blockPosition().equals(BlockPos.containing(to));
	}

	/**
	 * At the start of the AI step of a mob that is neither frozen nor steered: a hunting mob gets its player back as its
	 * target while they are within its (doubled) follow range, and a neutral one its anger at them; past that range it lets
	 * go of them (the hunt goes on, and takes them back once they are within it). A hurt from another player since the
	 * hunt began ends the hunt uncalmed: vanilla's own anger then turns the mob on whoever hit it.
	 */
	public static void beforeTargeting(Mob mob, TamperState state) {
		ServerPlayer target = state.huntTarget;
		long now = now(mob);
		if (target == null || !state.hunting(now)) {
			return;
		}
		if (mob.getLastHurtByMobTimestamp() >= state.huntSince && mob.getLastHurtByMob() instanceof Player by && !by.getUUID().equals(target.getUUID())) {
			endHunt(mob, state, false);
			return;
		}
		if (!validTarget(mob, target)) {
			return; // calmed by the tick
		}
		double keep = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
		if (mob.distanceToSqr(target) > keep * keep) {
			// Past twice its follow range the player got away, for now: no mob tracks anyone across the map.
			if (mob.getTargetUnchecked() == target) {
				mob.setTarget(null);
			}
		} else {
			if (mob.getTargetUnchecked() != target) {
				mob.setTarget(target);
			}
			if (mob instanceof NeutralMob neutral) {
				EntityReference<LivingEntity> angry = neutral.getPersistentAngerTarget();
				if (angry == null || !angry.matches(target)) {
					neutral.setPersistentAngerTarget(EntityReference.of(target));
				}
				if (!neutral.isAngry()) {
					neutral.setTimeToRemainAngry(Math.max(1L, state.huntUntil - now));
				}
			}
		}
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
