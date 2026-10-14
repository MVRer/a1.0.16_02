package com.forzacode.a1016_02.atmosphere.card;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared plumbing for atmosphere's cards: identity fields, where sounds go, and a few world queries.
 *
 * <p>Multiplayer (D-058 to D-062): {@code contextFits} and {@code fire} work for the player the director picked, in
 * their own level ({@code FireContext.level()}). Anything that runs later keeps that player's UUID and looks them up
 * again ({@link #target}); if they are gone it stops cleanly. Sounds he makes go to everyone in hearing range
 * ({@link SoundOut}); mob tricks happen on the server, so everyone sees them, and are bound to the target
 * ({@link #forTarget}) so they end the moment the target leaves, dies or turns spectator.
 */
abstract class AtmosphereCard implements EventCard {
	protected static final String CAUSE_PREFIX = "atmosphere:";

	/** Where this card's sounds go: everyone in hearing range. A game test swaps in its own on its own card instance. */
	SoundOut sounds = SoundOut.EVERYONE_IN_RANGE;

	private final String id;
	private final Tier tier;
	private final Stage earliest;
	private final Set<Habit> habits;
	private final Set<CardTag> tags;
	private final boolean hasFake;

	AtmosphereCard(String id, Tier tier, Stage earliest, Set<Habit> habits, Set<CardTag> tags, boolean hasFake) {
		this.id = id;
		this.tier = tier;
		this.earliest = earliest;
		this.habits = Set.copyOf(habits);
		this.tags = Set.copyOf(tags);
		this.hasFake = hasFake;
	}

	@Override
	public final String id() {
		return id;
	}

	@Override
	public final Tier tier() {
		return tier;
	}

	@Override
	public final Stage earliestStage() {
		return earliest;
	}

	@Override
	public final Set<Habit> habits() {
		return habits;
	}

	@Override
	public final Set<CardTag> tags() {
		return tags;
	}

	@Override
	public final boolean hasFake() {
		return hasFake;
	}

	protected String cause() {
		return CAUSE_PREFIX + id;
	}

	protected static AtmosphereConfig cfg() {
		return AtmosphereConfig.get();
	}

	protected static MobTamper mobs() {
		return Services.mobs();
	}

	/** Mobs of a class around the player that nothing is tampering with yet. */
	protected static <T extends Mob> List<T> untampered(ServerLevel level, Class<T> type, Vec3 center, double radius, Predicate<T> filter) {
		AABB box = new AABB(center, center).inflate(radius);
		double r2 = radius * radius;
		return level.getEntitiesOfClass(type, box, mob -> mob.isAlive() && mob.distanceToSqr(center) <= r2
				&& !MobTamperImpl.INSTANCE.isTampered(mob) && filter.test(mob));
	}

	/** True if no other (non-spectator) player in the player's level is within {@code radius}. */
	protected static boolean alone(ServerPlayer player, int radius) {
		double r2 = (double) radius * radius;
		for (ServerPlayer other : player.level().players()) {
			if (!other.getUUID().equals(player.getUUID()) && !other.isSpectator() && other.distanceToSqr(player) <= r2) {
				return false;
			}
		}
		return true;
	}

	protected static boolean inCombat(ServerPlayer player, long noCombatTicks) {
		return Services.watch().ticksSinceCombat(player) < noCombatTicks;
	}

	/**
	 * The card's target, looked up again by UUID (D-062.1): present only while they are a living participant in
	 * {@code level}. Anything that runs after {@code fire} uses this and stops cleanly when it comes back empty.
	 */
	protected static Optional<ServerPlayer> target(MinecraftServer server, UUID player, Level level) {
		return Services.watch().participant(server, player).filter(p -> Services.watch().isTargetable(p) && p.level() == level);
	}

	/** Binds what is being done to the mob to the card's target: released as soon as they are gone ({@link MobTamperImpl#bind}). */
	protected static void forTarget(Mob mob, ServerPlayer target) {
		MobTamperImpl.INSTANCE.bind(mob, target.getUUID());
	}
}
