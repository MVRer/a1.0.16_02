package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Animals facing the fog: every cow, sheep and pig nearby stops and turns to the same point in the fog. Nothing is
 * there. They stay like that until the player walks toward the point (or a few minutes pass). The false positive is a
 * single animal looking off into the distance for a moment.
 */
public final class AnimalsFaceFogCard extends AtmosphereCard {
	public static final String ID = "animals_face_fog";
	private static final Set<EntityType<?>> FARM_ANIMALS = Set.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.PIG, EntityTypes.MOOSHROOM);

	public AnimalsFaceFogCard() {
		super(ID, Tier.AMBIENT, Stage.ALONE, Set.of(Habit.WATCHER), Set.of(CardTag.MOB, CardTag.FOG), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		AtmosphereConfig cfg = cfg();
		return world.canSeeSky(player.blockPosition().above()) && animals(world, player, cfg.animalsRadius).size() >= cfg.animalsMin;
	}

	static List<Animal> animals(ServerLevel level, ServerPlayer player, int radius) {
		return untampered(level, Animal.class, player.position(), radius, a -> FARM_ANIMALS.contains(a.getType()) && !a.isPassenger());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		AtmosphereConfig cfg = cfg();
		List<Animal> animals = animals(level, player, cfg.animalsRadius);
		if (animals.isEmpty()) {
			return FireResult.SKIPPED;
		}
		Vec3 point = pointInFog(level, player, cfg.animalsPointDistance, ctx.random());
		RandomSource random = ctx.random();
		if (ctx.fake()) {
			Animal one = animals.get(random.nextInt(animals.size()));
			mobs().face(one, point, 60 + random.nextInt(60));
			return FireResult.FIRED;
		}
		Tasks.start(new Facing(player.getUUID(), level, point, horizontalDistance(player.position(), point), animals, random, cfg));
		return FireResult.FIRED;
	}

	/** A point at the fog's edge in a random direction, at about head height above the ground there. */
	static Vec3 pointInFog(ServerLevel level, ServerPlayer player, int distance, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double x = player.getX() + Math.cos(angle) * distance;
		double z = player.getZ() + Math.sin(angle) * distance;
		double y = player.getEyeY();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		if (WorldScan.loaded(level, bx, bz)) {
			y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + 1.5;
		}
		return new Vec3(x, y, z);
	}

	static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * The whole effect, from the staggered start to the staggered release. Each animal starts turning on its own
	 * tick; when the player walks toward the point (or time runs out, or they leave) the episode stops starting
	 * animals and releases every one it started, so a late starter can never be left frozen.
	 */
	private static final class Facing implements Tasks.Episode {
		private record Member(Animal animal, int startAge, int releaseOffset) {
		}

		private final UUID player;
		private final ServerLevel level;
		private final Vec3 point;
		private final double startDistance;
		private final List<Member> members = new ArrayList<>();
		private final Set<Animal> started = Collections.newSetFromMap(new IdentityHashMap<>());
		private final double releaseBlocks;
		private final int maxTicks;
		private final int holdTicks;
		private int age;
		private int endAge = -1;

		Facing(UUID player, ServerLevel level, Vec3 point, double startDistance, List<Animal> animals, RandomSource random, AtmosphereConfig cfg) {
			this.player = player;
			this.level = level;
			this.point = point;
			this.startDistance = startDistance;
			this.releaseBlocks = cfg.animalsReleaseBlocks;
			this.maxTicks = AtmosphereConfig.ticks(cfg.animalsMaxSeconds);
			int stagger = Math.max(1, cfg.animalsStaggerTicks);
			// Tamper deadlines outlast the episode: the episode is what ends the effect.
			this.holdTicks = maxTicks + 2 * stagger + 20;
			for (Animal animal : animals) {
				members.add(new Member(animal, 1 + random.nextInt(stagger), random.nextInt(Math.max(1, stagger / 2))));
			}
		}

		@Override
		public boolean tick(MinecraftServer server) {
			age++;
			if (endAge < 0) {
				ServerPlayer subject = server.getPlayerList().getPlayer(player);
				boolean walkedToward = subject != null && subject.level() == level
						&& startDistance - horizontalDistance(subject.position(), point) >= releaseBlocks;
				if (age > maxTicks || subject == null || subject.level() != level || walkedToward) {
					endAge = age;
				} else {
					for (Member member : members) {
						Animal animal = member.animal();
						if (member.startAge() == age && animal.isAlive() && mobs().freeze(animal, holdTicks)) {
							mobs().face(animal, point, holdTicks);
							mobs().silence(animal, holdTicks);
							started.add(animal);
						}
					}
					return true;
				}
			}
			for (Member member : members) {
				if (started.contains(member.animal()) && age - endAge >= member.releaseOffset()) {
					mobs().release(member.animal());
					started.remove(member.animal());
				}
			}
			return !started.isEmpty();
		}

		@Override
		public void stop(MinecraftServer server) {
			started.forEach(mobs()::release);
			started.clear();
		}
	}
}
