package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
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
		int maxTicks = AtmosphereConfig.ticks(cfg.animalsMaxSeconds);
		for (Animal animal : animals) {
			int delay = random.nextInt(Math.max(1, cfg.animalsStaggerTicks));
			Tasks.later(delay, () -> {
				if (animal.isAlive()) {
					mobs().freeze(animal, maxTicks);
					mobs().face(animal, point, maxTicks);
					mobs().silence(animal, maxTicks);
				}
			});
		}
		Tasks.start(new Facing(player.getUUID(), level, point, horizontalDistance(player.position(), point), new ArrayList<>(animals), maxTicks, cfg));
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

	/** Holds the animals until the player walks toward the point. */
	private static final class Facing implements Tasks.Episode {
		private final UUID player;
		private final ServerLevel level;
		private final Vec3 point;
		private final double startDistance;
		private final List<Animal> animals;
		private final double releaseBlocks;
		private final int stagger;
		private int ticksLeft;

		Facing(UUID player, ServerLevel level, Vec3 point, double startDistance, List<Animal> animals, int maxTicks, AtmosphereConfig cfg) {
			this.player = player;
			this.level = level;
			this.point = point;
			this.startDistance = startDistance;
			this.animals = animals;
			this.releaseBlocks = cfg.animalsReleaseBlocks;
			this.stagger = Math.max(1, cfg.animalsStaggerTicks / 2);
			this.ticksLeft = maxTicks + cfg.animalsStaggerTicks;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			ServerPlayer subject = server.getPlayerList().getPlayer(player);
			boolean walkedToward = subject != null && subject.level() == level
					&& startDistance - horizontalDistance(subject.position(), point) >= releaseBlocks;
			if (--ticksLeft <= 0 || subject == null || walkedToward) {
				releaseAll(level.getRandom());
				return false;
			}
			return true;
		}

		@Override
		public void stop(MinecraftServer server) {
			animals.forEach(mobs()::release);
		}

		private void releaseAll(RandomSource random) {
			for (Animal animal : animals) {
				Tasks.later(random.nextInt(stagger), () -> mobs().release(animal));
			}
		}
	}
}
