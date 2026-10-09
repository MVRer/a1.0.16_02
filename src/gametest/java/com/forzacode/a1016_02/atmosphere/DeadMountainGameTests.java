package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Dead mountains: "no animals ever spawn there". Grass turned to dirt already stops every animal that needs grass,
 * but a dead mountain keeps the rest of its ground (podzol, coarse dirt, stone, gravel, snow where its circle reaches
 * a cold slope), and wolves, foxes, goats, rabbits and armadillos spawn on those. So natural passive spawns are
 * refused inside the recorded areas too ({@link DeadMountains#refusesSpawn}).
 */
public class DeadMountainGameTests {
	/** Ground a dead mountain can have once its grass is dirt. */
	private static final List<Block> KEPT_GROUND = List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT, Blocks.STONE,
			Blocks.GRAVEL, Blocks.SNOW_BLOCK, Blocks.SAND, Blocks.MOSS_BLOCK);
	private static final int ATTEMPTS = 8;

	@GameTest(skyAccess = true, maxTicks = 60)
	public void deadMountainDirtStopsGrassAnimals(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TamperGameTests.floor(helper);
		BlockPos rel = new BlockPos(3, 1, 3);
		helper.runAfterDelay(10, () -> {
			BlockPos pos = helper.absolutePos(rel);
			helper.assertTrue(DeadMountains.in(level.dimension()).stream().noneMatch(a -> a.inCircle(pos.getX(), pos.getZ())),
					"the test area lies in a recorded dead mountain");
			Set<EntityType<?>> passives = naturalPassives(level);
			helper.assertTrue(passives.contains(EntityTypes.COW) && passives.contains(EntityTypes.WOLF) && passives.contains(EntityTypes.GOAT),
					"overworld passive spawns not found: " + names(passives));

			helper.setBlock(rel.below(), Blocks.GRASS_BLOCK);
			helper.assertTrue(canSpawn(level, EntityTypes.COW, pos, EntitySpawnReason.NATURAL), "control: no cow on lit grass, so this test sees nothing");

			helper.setBlock(rel.below(), Blocks.DIRT);
			List<String> onDirt = new ArrayList<>();
			for (EntityType<?> type : passives) {
				if (canSpawn(level, type, pos, EntitySpawnReason.NATURAL) || canSpawn(level, type, pos, EntitySpawnReason.CHUNK_GENERATION)) {
					onDirt.add(name(type));
				}
			}
			helper.assertTrue(onDirt.isEmpty(), "passive mobs still spawn on a dead mountain's dirt: " + onDirt);

			// The rest of a dead mountain's ground: this is why the spawn rule exists.
			List<String> kept = new ArrayList<>();
			for (Block ground : KEPT_GROUND) {
				helper.setBlock(rel.below(), ground);
				for (EntityType<?> type : passives) {
					if (canSpawn(level, type, pos, EntitySpawnReason.NATURAL)) {
						kept.add(name(type) + " on " + BuiltInRegistries.BLOCK.getKey(ground).getPath());
					}
				}
			}
			helper.assertTrue(kept.contains("wolf on podzol") && kept.contains("goat on stone"), "kept ground no longer lets animals through: " + kept);
			A1016_02.LOGGER.info("[a1016] atmosphere test: without the dead mountain spawn rule these could spawn there: {}", kept);
			helper.succeed();
		});
	}

	/**
	 * The dead ground decides, not a height band: inside one site circle, a dead patch (dirt on a hill) is quiet and
	 * animal-free while a living patch of grass below it stays normal. End to end through
	 * {@code SpawnPlacements.checkSpawnRules}. Runs in the End (open void where the tests run, unlike the Nether's
	 * solid rock) so its recorded site never shows up in the overworld tests and cards that look for dead mountains.
	 * Glowstone beside each spot gives the light the End's sky does not; sky access keeps the barrier roof off the columns.
	 */
	@GameTest(dimension = "minecraft:the_end", skyAccess = true, maxTicks = 60)
	public void deadPatchRefusesSpawnsLivingPatchBelowDoesNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TamperGameTests.floor(helper);
		// One circle of radius 4 around column (3, 3). Dead patch: a hilltop at y 3 over column (1, 1). Living patch:
		// grass at y 0 over column (5, 5), lower down but inside the same circle. Outside: column (6, 6).
		BlockPos deadRel = new BlockPos(1, 4, 1);
		BlockPos livingRel = new BlockPos(5, 1, 5);
		BlockPos outsideRel = new BlockPos(6, 1, 6);
		helper.setBlock(1, 1, 1, Blocks.STONE);
		helper.setBlock(1, 2, 1, Blocks.STONE);
		helper.setBlock(deadRel.below(), Blocks.DIRT);
		helper.setBlock(livingRel.below(), Blocks.GRASS_BLOCK);
		for (BlockPos rel : List.of(deadRel, livingRel, outsideRel)) {
			helper.setBlock(rel, Blocks.AIR);
			helper.setBlock(rel.above(), Blocks.AIR);
		}
		helper.setBlock(deadRel.east(), Blocks.GLOWSTONE);
		helper.setBlock(livingRel.south(), Blocks.GLOWSTONE);
		Services.sites().record(SiteType.DEAD_MOUNTAIN, level.dimension(), helper.absolutePos(new BlockPos(3, 4, 3)), 4);
		DeadMountains.refresh();
		helper.runAfterDelay(10, () -> {
			BlockPos dead = helper.absolutePos(deadRel);
			BlockPos living = helper.absolutePos(livingRel);
			BlockPos outside = helper.absolutePos(outsideRel);
			List<DeadMountains.Area> areas = DeadMountains.in(level.dimension());

			// Membership, as the client and the spawn rule see it.
			helper.assertTrue(DeadMountains.contains(level, dead), "the dead hilltop is not dead mountain");
			helper.assertTrue(DeadMountains.contains(level, dead.below(3)) && DeadMountains.contains(level, dead.above(20)),
					"the dead column counts only at some heights");
			helper.assertFalse(DeadMountains.contains(level, living), "living grass below the dead line went dead");
			helper.assertFalse(DeadMountains.contains(level, outside), "outside the circle counts");
			helper.setBlock(livingRel.below(), Blocks.DIRT);
			helper.setBlock(livingRel, Blocks.SHORT_GRASS);
			helper.assertFalse(DeadMountains.inside(level, areas, living.getX(), living.getZ()), "a plant on top is not alive");
			helper.setBlock(livingRel, Blocks.AIR);
			helper.assertTrue(DeadMountains.inside(level, areas, living.getX(), living.getZ()), "bare dirt in the circle is not dead");
			helper.setBlock(livingRel.below(), Blocks.GRASS_BLOCK);
			helper.setBlock(deadRel, Blocks.SNOW);
			helper.assertTrue(DeadMountains.contains(level, dead), "a snow layer hides the dead ground");
			helper.setBlock(deadRel, Blocks.AIR);

			// Spawns: nothing passive on the dead patch, whatever its ground; the living patch and outside are normal.
			Set<EntityType<?>> passives = naturalPassives(level);
			int refused = 0;
			for (Block ground : List.of(Blocks.DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SNOW_BLOCK)) {
				helper.setBlock(deadRel.below(), ground);
				helper.setBlock(outsideRel.below(), ground);
				for (EntityType<?> type : passives) {
					String what = name(type) + " on " + BuiltInRegistries.BLOCK.getKey(ground).getPath();
					helper.assertFalse(canSpawn(level, type, dead, EntitySpawnReason.NATURAL), "natural spawn on a dead patch: " + what);
					helper.assertFalse(canSpawn(level, type, dead, EntitySpawnReason.CHUNK_GENERATION), "worldgen spawn on a dead patch: " + what);
					if (canSpawn(level, type, outside, EntitySpawnReason.NATURAL)) {
						refused++;
					}
				}
			}
			helper.assertTrue(refused > 0, "nothing could spawn outside either, so the test proved nothing");
			helper.setBlock(outsideRel.below(), Blocks.PODZOL);
			helper.assertTrue(canSpawn(level, EntityTypes.WOLF, outside, EntitySpawnReason.NATURAL), "control: no wolf on lit podzol outside");
			helper.assertTrue(canSpawn(level, EntityTypes.COW, living, EntitySpawnReason.NATURAL)
					&& canSpawn(level, EntityTypes.COW, living, EntitySpawnReason.CHUNK_GENERATION), "no cow on the living grass below the dead line");
			// Grass put back on the dead patch makes it alive again (the rule reads the ground, nothing is stored).
			helper.setBlock(deadRel.below(), Blocks.GRASS_BLOCK);
			helper.assertTrue(canSpawn(level, EntityTypes.COW, dead, EntitySpawnReason.NATURAL), "grass on the hilltop is still refused");
			helper.setBlock(deadRel.below(), Blocks.PODZOL);
			helper.assertTrue(canSpawn(level, EntityTypes.WOLF, dead, EntitySpawnReason.SPAWNER), "the rule touched a non-natural spawn");

			// Monsters, other dimensions and other reasons are never touched.
			helper.assertFalse(DeadMountains.refusesSpawn(EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, level, dead), "a monster spawn was refused");
			helper.assertFalse(DeadMountains.refusesSpawn(EntityTypes.WOLF, EntitySpawnReason.BREEDING, level, dead), "refused breeding");
			helper.assertTrue(DeadMountains.refusesSpawn(EntityTypes.BAT, EntitySpawnReason.NATURAL, level, dead)
					&& DeadMountains.refusesSpawn(EntityTypes.SQUID, EntitySpawnReason.NATURAL, level, dead), "bats or squid still allowed");
			helper.assertTrue(DeadMountains.in(Level.OVERWORLD).stream().noneMatch(a -> a.inCircle(dead.getX(), dead.getZ())),
					"the site leaked into the overworld");
			helper.succeed();
		});
	}

	@GameTest
	public void deadMountainAreaMatchesTheScarCircle(GameTestHelper helper) {
		DeadMountains.Area area = new DeadMountains.Area(100, -40, 20);
		// The world's dead mountain column test: dx*dx + dz*dz <= r*r.
		for (int x = 70; x <= 130; x++) {
			for (int z = -70; z <= -10; z++) {
				long dx = x - 100;
				long dz = z + 40;
				helper.assertTrue(area.inCircle(x, z) == (dx * dx + dz * dz <= 400), "edge differs from the scar's at " + x + " " + z);
			}
		}
		helper.assertTrue(Math.abs(area.edgeDistance(130.5, -39.5) - 10.0) < 1.0E-9 && area.edgeDistance(100.5, -39.5) == -20.0, "edge distance");

		// The quiet: eased fade out while inside, a slower return after leaving, no jumps.
		AtmosphereConfig cfg = AtmosphereConfig.get();
		double quiet = 0.0;
		double last = 1.0;
		int ticks = 0;
		while (quiet < 1.0) {
			quiet = Curves.quietStep(quiet, true, cfg.deadMountainFadeOutTicks, cfg.deadMountainRestoreTicks);
			double v = Curves.quietVolume(quiet);
			helper.assertTrue(v <= last && last - v < 0.05, "the quiet drops in a step at tick " + ticks);
			last = v;
			ticks++;
		}
		helper.assertTrue(Math.abs(ticks - Math.max(1, cfg.deadMountainFadeOutTicks)) <= 1 && last == 0.0, "fade out took " + ticks + " ticks");
		ticks = 0;
		while (quiet > 0.0) {
			quiet = Curves.quietStep(quiet, false, cfg.deadMountainFadeOutTicks, cfg.deadMountainRestoreTicks);
			double v = Curves.quietVolume(quiet);
			helper.assertTrue(v >= last && v - last < 0.05, "the sound comes back in a step at tick " + ticks);
			last = v;
			ticks++;
		}
		helper.assertTrue(Math.abs(ticks - Math.max(1, cfg.deadMountainRestoreTicks)) <= 1 && last == 1.0, "restore took " + ticks + " ticks");
		helper.assertTrue(cfg.deadMountainRestoreTicks >= cfg.deadMountainFadeOutTicks, "the sound comes back faster than it goes");
		helper.succeed();
	}

	/** Every passive mob some overworld biome spawns naturally. */
	static Set<EntityType<?>> naturalPassives(ServerLevel level) {
		Set<EntityType<?>> types = new LinkedHashSet<>();
		level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().filter(biome -> biome.is(BiomeTags.IS_OVERWORLD)).forEach(biome -> {
			MobSpawnSettings spawns = biome.value().getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
			for (MobCategory category : MobCategory.values()) {
				if (category.isFriendly() && category != MobCategory.MISC) {
					spawns.getMobsToSpawn(category).unwrap().forEach(entry -> types.add(entry.value().type()));
				}
			}
		});
		return types;
	}

	/** What the natural spawner asks of a spot, tried a few times for rules with a random part. */
	static boolean canSpawn(ServerLevel level, EntityType<?> type, BlockPos pos, EntitySpawnReason reason) {
		if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
			return false;
		}
		RandomSource random = RandomSource.create(pos.asLong());
		for (int n = 0; n < ATTEMPTS; n++) {
			if (SpawnPlacements.checkSpawnRules(type, level, reason, pos, random)) {
				return true;
			}
		}
		return false;
	}

	private static String name(EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
	}

	private static List<String> names(Set<EntityType<?>> types) {
		return types.stream().map(DeadMountainGameTests::name).toList();
	}
}
