package com.forzacode.a1016_02.world.sc2.s20;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Visits;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 20b (docs/SELF_CREATED_2.md, 20; D-106): when a player inside a square comes within {@code d} of the little building in
 * its middle, every animal standing on the floor stops, falls silent and faces the building for {@code e} seconds, then
 * all are let go together (their holds run out in one tick). Through the kit's {@link Answers} (no block is touched, so
 * nothing waits on the view rule): never in anyone's quiet or first hour, one a player a day (everyone inside counts it),
 * the event and the small tension raise for everyone inside. Animals only (never a tamed, leashed, ridden or already held
 * one), only those on the floor; each held one is bound to the player it turned for (released once they are gone and
 * nobody sees it). A look once a second while someone is on a square, nothing otherwise. Session only; server thread.
 */
public final class FloorAnswers {
	/** The answer's id (its caps and the event). */
	public static final String FACE = "20b";
	/** Its cause (it ledgers nothing: it moves no block). */
	public static final String CAUSE = S20.CAUSE + "/face";
	/** How often the visits are looked at (ticks). */
	public static final int EVERY_TICKS = 20;
	/** An animal counts as on the floor up to this high over it. */
	public static final int ON_FLOOR = 3;
	/** A refused or dropped ask waits this long before it is asked again for that place (ticks). */
	public static final int ASK_AGAIN_TICKS = 200;

	/**
	 * What 20b reads: the kit's answers, who is online, the config, the mob tamper.
	 *
	 * @param people everyone online (spectators are skipped)
	 */
	public record Env(Answers answers, Visits.People people, Supplier<Sc20Config> config, Supplier<MobTamper> tamper) {
	}

	public static final FloorAnswers LIVE = new FloorAnswers(new Env(Answers.LIVE, server -> List.copyOf(server.getPlayerList().getPlayers()),
			Sc20Config::get, Services::mobs));

	private final Env env;
	private final Map<UUID, Remains.Place> visits = new LinkedHashMap<>();
	private final Map<Remains.Place, Long> askedAt = new LinkedHashMap<>();
	/** The animals held now and when their hold ends (server ticks), so a switch off lets them go at once. */
	private final Map<Animal, Long> held = new LinkedHashMap<>();

	/** The gallery's staged hold, waiting for its tick. */
	private record Later(Remains.Place place, List<Animal> animals, int ticks, long at) {
	}

	private final List<Later> later = new ArrayList<>();

	public FloorAnswers(Env env) {
		this.env = env;
	}

	/** A player came inside a place (only squares count). */
	public void entered(Remains.Place place, ServerPlayer player) {
		if (place.kind() == Remains.Kind.FLOOR) {
			visits.put(player.getUUID(), place);
		}
	}

	/** A player left a place. */
	public void left(Remains.Place place, ServerPlayer player) {
		visits.remove(player.getUUID(), place);
	}

	/** Every server tick: a look every {@value #EVERY_TICKS} ticks while someone is on a square. */
	public void tick(MinecraftServer server) {
		if (!later.isEmpty()) {
			long now = server.getTickCount();
			for (Later l : List.copyOf(later)) {
				if (now >= l.at()) {
					later.remove(l);
					hold(l.place(), l.animals(), l.ticks(), List.of(), now);
				}
			}
		}
		if (!held.isEmpty()) {
			long now = server.getTickCount();
			held.entrySet().removeIf(e -> now > e.getValue() || !e.getKey().isAlive());
		}
		if (visits.isEmpty() || server.getTickCount() % EVERY_TICKS != 3) {
			return;
		}
		look(server, server.getTickCount());
	}

	/** One look: each square with a player near its building asks for 20b (at most once every {@value #ASK_AGAIN_TICKS} ticks). */
	public void look(MinecraftServer server, long now) {
		if (!SelfCreated.enabled()) {
			return;
		}
		double radius = env.config().get().faceRadius;
		List<Remains.Place> near = new ArrayList<>();
		for (ServerPlayer player : env.people().online(server)) {
			Remains.Place place = visits.get(player.getUUID());
			if (place == null || player.isSpectator() || near.contains(place) || !player.level().dimension().equals(place.dimension())) {
				continue;
			}
			Vec3 middle = Vec3.atCenterOf(place.anchor());
			double dx = player.getX() - middle.x;
			double dz = player.getZ() - middle.z;
			if (dx * dx + dz * dz <= radius * radius) {
				near.add(place);
			}
		}
		for (Remains.Place place : near) {
			Long last = askedAt.get(place);
			if (last != null && now - last < ASK_AGAIN_TICKS) {
				continue;
			}
			ServerLevel level = server.getLevel(place.dimension());
			if (level != null) {
				askedAt.put(place, now);
				ask(level, place, false);
			}
		}
	}

	/** Asks for 20b at this square now; {@code waived} lifts the caps (the dev command). */
	public Answers.Result ask(ServerLevel level, Remains.Place place, boolean waived) {
		Answers.Rules rules = Answers.Rules.of(FACE, CAUSE).perPlaceDay(Integer.MAX_VALUE).limitSeconds(30);
		if (waived) {
			rules = rules.perPlayerDay(Integer.MAX_VALUE);
		}
		int ticks = (int) Math.round(Math.max(1.0, env.config().get().faceSeconds) * 20.0);
		return env.answers().submit(level, place, rules, (lvl, p, inside) -> {
			List<Animal> animals = onTheFloor(lvl, p);
			if (animals.isEmpty()) {
				return null;
			}
			return Answers.Edit.of(List.of(), traces -> hold(p, animals, ticks, inside, lvl.getServer().getTickCount()) > 0);
		});
	}

	/** The free animals standing on this square's floor. */
	public List<Animal> onTheFloor(ServerLevel level, Remains.Place place) {
		BoundingBox floor = FloorShape.floorOf(place);
		AABB area = new AABB(floor.minX(), floor.minY() + 0.5, floor.minZ(), floor.maxX() + 1.0, floor.minY() + 1.0 + ON_FLOOR, floor.maxZ() + 1.0);
		MobTamper tamper = env.tamper().get();
		return level.getEntitiesOfClass(Animal.class, area, animal -> free(animal) && !tamper.isTampered(animal));
	}

	/** An animal that may be turned: alive, nobody's, on no leash, riding nothing and carrying nobody. */
	public static boolean free(Animal animal) {
		return animal.isAlive() && !animal.isLeashed() && !animal.isPassenger() && !animal.isVehicle() && !(animal instanceof OwnableEntity owned && owned.getOwnerReference() != null)
				&& !(animal instanceof AbstractHorse horse && horse.isTamed());
	}

	/**
	 * Holds these animals now, all in this tick: frozen, silent, facing the building's middle, for {@code ticks}, each bound
	 * to the nearest of {@code players}. Returns how many were held.
	 */
	public int hold(Remains.Place place, List<Animal> animals, int ticks, List<ServerPlayer> players, long now) {
		MobTamper tamper = env.tamper().get();
		Vec3 point = Vec3.atCenterOf(place.anchor()).add(0.0, 0.7, 0.0);
		int count = 0;
		for (Animal animal : animals) {
			if (!tamper.freeze(animal, ticks)) {
				continue;
			}
			tamper.face(animal, point, ticks);
			tamper.silence(animal, ticks);
			ServerPlayer nearest = null;
			for (ServerPlayer player : players) {
				if (nearest == null || player.distanceToSqr(animal) < nearest.distanceToSqr(animal)) {
					nearest = player;
				}
			}
			if (nearest != null) {
				tamper.bind(animal, nearest.getUUID());
			}
			held.put(animal, now + ticks);
			count++;
		}
		return count;
	}

	/** The gallery's staging: holds these animals {@code delay} ticks from now, bound to nobody (dev only). */
	public void holdLater(MinecraftServer server, Remains.Place place, List<Animal> animals, int ticks, int delay) {
		later.add(new Later(place, List.copyOf(animals), ticks, server.getTickCount() + delay));
	}

	/** How many animals 20b holds now. */
	public int holding() {
		return held.size();
	}

	/** Forgets every visit (server stop). */
	public void reset() {
		visits.clear();
		askedAt.clear();
		held.clear();
		later.clear();
	}

	/** Switched off: every animal it holds is let go now, and every visit forgotten. */
	public void switchedOff() {
		MobTamper tamper = env.tamper().get();
		for (Animal animal : List.copyOf(held.keySet())) {
			tamper.release(animal);
		}
		reset();
	}
}
