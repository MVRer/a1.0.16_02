package com.forzacode.a1016_02.atmosphere.sc2.s23;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.core.Remains;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 23's held breath (docs/SELF_CREATED_2.md): every mob near the players sharing the moment, monsters mid-attack
 * included, frozen in the same tick, every one turned to face one point on the bearing ({@link #bearing}: the nearest of
 * his places, else where he last stood, else the world's origin) and up ({@link #LOOK_UP}: every head tilted at once at
 * something over the horizon), zombies with their arms up, the nearest monster with its back to the player (R11-11),
 * silent; all let go together in one tick. Never a boss,
 * the figure, a pet ordered to sit, a mob something else holds, or a creeper already swelling (frozen it would still go
 * off). Server thread.
 */
public final class HeldBreath {
	public static final HeldBreath LIVE = new HeldBreath();
	/** The figure's entity type id (entity's, read by name so atmosphere does not depend on entity). */
	static final Identifier FIGURE = A1016_02.id("him");

	/** Where the bearing comes from. */
	public enum From {
		PLACE, STOOD, ORIGIN, NORTH, HOME
	}

	/** The bearing: the point they turn toward (far off) and where it came from. */
	public record Bearing(Vec3 toward, From from) {
	}

	/** One held breath: its mobs, the tick it ends, who it is for. */
	public record Breath(List<Mob> mobs, long until, List<UUID> players, Vec3 point, boolean fake) {
	}

	/** A start the gallery asked for later (its staging, at a tick): seen from {@code from} (its camera's eyes). */
	private record Later(ServerLevel level, List<Mob> mobs, Vec3 point, int ticks, long at, @Nullable Vec3 from) {
	}

	/** R11-11: every held mob looks this far up over the target's eyes at the point 200 out (15 to 30 degrees of pitch at once). */
	public static final double LOOK_UP = 80.0;
	/** R11-11: the nearest monster within this of the target turns its back on them fully. */
	public static final double BACK_NEAR = 24.0;

	private final List<Breath> breaths = new ArrayList<>();
	private final List<Later> later = new ArrayList<>();

	/** A separate set, ticked by its owner (tests); the game's is {@link #LIVE}. */
	public HeldBreath() {
	}

	/** A mob 23 may hold: alive, not a boss, not the figure, not a pet ordered to sit, held by nothing else, no creeper already swelling; {@code animalsOnly}: an animal. */
	public static boolean holdable(Mob mob, boolean animalsOnly) {
		if (!mob.isAlive() || mob.isRemoved() || mob instanceof EnderDragon || mob instanceof WitherBoss || mob.isNoAi()) {
			return false;
		}
		if (FIGURE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))) {
			return false;
		}
		if (mob instanceof TamableAnimal pet && pet.isTame() && pet.isOrderedToSit()) {
			return false;
		}
		if (mob instanceof Creeper creeper && (creeper.getSwellDir() > 0 || creeper.isIgnited())) {
			return false;
		}
		if (animalsOnly && !(mob instanceof Animal && mob.getType().getCategory() == MobCategory.CREATURE)) {
			return false;
		}
		return !Herds.tampered(mob);
	}

	/**
	 * Every holdable mob within {@code radius} of any of {@code around} (each player sharing the moment), nearest to any of
	 * them first, at most {@code max}.
	 */
	public static List<Mob> gather(ServerLevel level, List<Vec3> around, double radius, int max, boolean animalsOnly) {
		Map<Mob, Double> nearest = new IdentityHashMap<>();
		double r2 = radius * radius;
		for (Vec3 at : around) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius), m -> holdable(m, animalsOnly))) {
				double d = mob.distanceToSqr(at);
				if (d <= r2) {
					nearest.merge(mob, d, Math::min);
				}
			}
		}
		List<Mob> list = new ArrayList<>(nearest.keySet());
		list.sort(Comparator.comparingDouble(nearest::get));
		return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
	}

	/**
	 * Pure: the bearing from {@code from}: the place's anchor if there is one, else where he last stood, else the
	 * origin (0, 0); one within 4 blocks of {@code from} (across) is passed over for the next; if all are, north.
	 */
	public static Bearing bearing(Optional<Remains.Place> place, Optional<Vec3> stood, Vec3 from) {
		if (place.isPresent()) {
			Vec3 anchor = Vec3.atCenterOf(place.get().anchor());
			if (Herds.flatSqr(anchor, from) > 16.0) {
				return new Bearing(anchor, From.PLACE);
			}
		}
		if (stood.isPresent() && Herds.flatSqr(stood.get(), from) > 16.0) {
			return new Bearing(stood.get(), From.STOOD);
		}
		Vec3 origin = new Vec3(0.5, from.y, 0.5);
		if (Herds.flatSqr(origin, from) > 16.0) {
			return new Bearing(origin, From.ORIGIN);
		}
		return new Bearing(from.add(0, 0, -100), From.NORTH);
	}

	/**
	 * The point every mob faces: {@code distance} along the bearing from {@code eye}, {@link #LOOK_UP} over the eye's
	 * height (R11-11: every head tilted up at once, toward something on the horizon that is not there).
	 */
	public static Vec3 facePoint(Vec3 eye, Vec3 toward, double distance) {
		double dx = toward.x - eye.x;
		double dz = toward.z - eye.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-6) {
			return eye.add(0, LOOK_UP, -distance);
		}
		return new Vec3(eye.x + dx / len * distance, eye.y + LOOK_UP, eye.z + dz / len * distance);
	}

	/**
	 * Pure (R11-11): the point the nearest monster faces: straight away from {@code from} (the target's eyes), as far and as
	 * high as the others' point, so it shows the target its back, fully.
	 */
	public static Vec3 awayFrom(Vec3 mob, Vec3 from, Vec3 point) {
		double dx = mob.x - from.x;
		double dz = mob.z - from.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		double far = Math.hypot(point.x - mob.x, point.z - mob.z);
		if (len < 1.0E-6) {
			return point;
		}
		return new Vec3(mob.x + dx / len * far, point.y, mob.z + dz / len * far);
	}

	/** The nearest monster within {@link #BACK_NEAR} of {@code from}, or null. */
	static @Nullable Mob nearestMonster(List<? extends Mob> mobs, Vec3 from) {
		Mob best = null;
		double bestD = BACK_NEAR * BACK_NEAR;
		for (Mob mob : mobs) {
			double d = mob.position().distanceToSqr(from);
			if (mob instanceof Enemy && d <= bestD) {
				best = mob;
				bestD = d;
			}
		}
		return best;
	}

	/** {@link #hold(ServerLevel, List, Vec3, int, List, boolean, Vec3)} with no one to turn a back on. */
	public int hold(ServerLevel level, List<? extends Mob> mobs, Vec3 point, int ticks, List<UUID> players, boolean fake) {
		return hold(level, mobs, point, ticks, players, fake, null);
	}

	/**
	 * Holds these mobs now, all in this tick: frozen, facing {@code point}, silent (not the fake's), for {@code ticks},
	 * bound to {@code players}; a zombie with its arms up; the nearest monster to {@code from} (the target's eyes; null: none)
	 * with its back fully to them (R11-11). Returns how many were held.
	 */
	public int hold(ServerLevel level, List<? extends Mob> mobs, Vec3 point, int ticks, List<UUID> players, boolean fake, @Nullable Vec3 from) {
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		List<Mob> held = new ArrayList<>();
		Mob back = fake || from == null ? null : nearestMonster(mobs, from);
		for (Mob mob : mobs) {
			if (!tamper.freeze(mob, ticks + 2)) {
				continue;
			}
			tamper.face(mob, mob == back ? awayFrom(mob.position(), from, point) : point, ticks + 2);
			if (!fake) {
				tamper.silence(mob, ticks + 2);
				if (mob instanceof Zombie) {
					tamper.raiseArms(mob);
				}
			}
			if (!players.isEmpty()) {
				tamper.bind(mob, players);
			}
			held.add(mob);
		}
		if (!held.isEmpty()) {
			breaths.add(new Breath(List.copyOf(held), level.getServer().getTickCount() + ticks, List.copyOf(players), point, fake));
		}
		return held.size();
	}

	/** The gallery's staging: holds these mobs {@code delay} ticks from now, as seen from {@code from} (dev only). */
	public void holdLater(ServerLevel level, List<? extends Mob> mobs, Vec3 point, int ticks, int delay, @Nullable Vec3 from) {
		later.add(new Later(level, List.copyOf(mobs), point, ticks, level.getServer().getTickCount() + delay, from));
	}

	/** Every server tick: every breath whose time is up lets all its mobs go in this one tick. */
	public void tick(MinecraftServer server) {
		if (breaths.isEmpty() && later.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Later> it = later.iterator(); it.hasNext();) {
			Later l = it.next();
			if (now >= l.at()) {
				it.remove();
				hold(l.level(), l.mobs(), l.point(), l.ticks(), List.of(), false, l.from());
			}
		}
		for (Iterator<Breath> it = breaths.iterator(); it.hasNext();) {
			Breath breath = it.next();
			if (now >= breath.until()) {
				it.remove();
				for (Mob mob : breath.mobs()) {
					MobTamperImpl.INSTANCE.release(mob);
				}
			}
		}
	}

	/** True while a breath is held for this player. */
	public boolean holding(UUID player) {
		for (Breath breath : breaths) {
			if (breath.players().contains(player)) {
				return true;
			}
		}
		return false;
	}

	/** The breaths now (a copy). */
	public List<Breath> breaths() {
		return List.copyOf(breaths);
	}

	/** Everything held is let go now. */
	public void clear() {
		for (Breath breath : breaths) {
			for (Mob mob : breath.mobs()) {
				MobTamperImpl.INSTANCE.release(mob);
			}
		}
		breaths.clear();
		later.clear();
	}
}
