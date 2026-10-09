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
			helper.assertFalse(DeadMountains.contains(level.dimension(), pos), "the test area lies in a recorded dead mountain");
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
	 * The spawn rule, end to end through {@code SpawnPlacements.checkSpawnRules}. Runs in the End (open void where the
	 * tests run, unlike the Nether's solid rock) so its recorded site never shows up in the overworld tests and cards
	 * that look for dead mountains. Glowstone gives the light the End's sky does not.
	 */
	@GameTest(dimension = "minecraft:the_end", maxTicks = 60)
	public void deadMountainsRefuseNaturalPassiveSpawns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TamperGameTests.floor(helper);
		BlockPos insideRel = new BlockPos(1, 1, 1);
		BlockPos outsideRel = new BlockPos(6, 1, 6);
		for (BlockPos rel : List.of(insideRel, outsideRel)) {
			helper.setBlock(rel, Blocks.AIR);
			helper.setBlock(rel.above(), Blocks.AIR);
			helper.setBlock(rel.above(2), Blocks.GLOWSTONE);
		}
		Services.sites().record(SiteType.DEAD_MOUNTAIN, level.dimension(), helper.absolutePos(insideRel), 2);
		DeadMountains.refresh();
		helper.runAfterDelay(10, () -> {
			BlockPos inside = helper.absolutePos(insideRel);
			BlockPos outside = helper.absolutePos(outsideRel);
			helper.assertTrue(DeadMountains.contains(level.dimension(), inside) && !DeadMountains.contains(level.dimension(), outside),
					"area membership: inside " + DeadMountains.contains(level.dimension(), inside) + ", outside " + DeadMountains.contains(level.dimension(), outside));
			Set<EntityType<?>> passives = naturalPassives(level);
			int blocked = 0;
			for (Block ground : List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SNOW_BLOCK)) {
				helper.setBlock(insideRel.below(), ground);
				helper.setBlock(outsideRel.below(), ground);
				for (EntityType<?> type : passives) {
					String what = name(type) + " on " + BuiltInRegistries.BLOCK.getKey(ground).getPath();
					helper.assertFalse(canSpawn(level, type, inside, EntitySpawnReason.NATURAL), "natural spawn inside a dead mountain: " + what);
					helper.assertFalse(canSpawn(level, type, inside, EntitySpawnReason.CHUNK_GENERATION), "worldgen spawn inside a dead mountain: " + what);
					if (canSpawn(level, type, outside, EntitySpawnReason.NATURAL)) {
						blocked++;
					}
				}
				if (ground == Blocks.GRASS_BLOCK) {
					helper.assertTrue(canSpawn(level, EntityTypes.COW, outside, EntitySpawnReason.NATURAL), "control: no cow on lit grass just outside");
					helper.assertTrue(canSpawn(level, EntityTypes.COW, inside, EntitySpawnReason.SPAWNER), "the rule touched a non-natural spawn");
				}
				if (ground == Blocks.PODZOL) {
					helper.assertTrue(canSpawn(level, EntityTypes.WOLF, outside, EntitySpawnReason.NATURAL), "control: no wolf on lit podzol just outside");
				}
			}
			helper.assertTrue(blocked > 0, "nothing could spawn outside either, so the test proved nothing");

			// Monsters, other dimensions and other reasons are never touched.
			helper.assertFalse(DeadMountains.refusesSpawn(EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, level.dimension(), inside), "a monster spawn was refused");
			helper.assertFalse(DeadMountains.refusesSpawn(EntityTypes.COW, EntitySpawnReason.NATURAL, Level.OVERWORLD, inside), "refused in the wrong dimension");
			helper.assertFalse(DeadMountains.refusesSpawn(EntityTypes.COW, EntitySpawnReason.BREEDING, level.dimension(), inside), "refused breeding");
			helper.assertTrue(DeadMountains.refusesSpawn(EntityTypes.BAT, EntitySpawnReason.NATURAL, level.dimension(), inside)
					&& DeadMountains.refusesSpawn(EntityTypes.SQUID, EntitySpawnReason.NATURAL, level.dimension(), inside), "bats or squid still allowed");

			// The client gets the same area, and the same sharp edge.
			List<DeadMountains.Area> near = DeadMountains.near(level.dimension(), inside.getX() + 0.5, inside.getZ() + 0.5, 0);
			helper.assertTrue(near.size() >= 1 && near.getFirst().contains(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5)
					&& !near.getFirst().contains(inside.getX() + 3.5, inside.getY(), inside.getZ() + 0.5), "client area: " + near);
			helper.succeed();
		});
	}

	@GameTest
	public void deadMountainAreaMatchesTheScarCircle(GameTestHelper helper) {
		DeadMountains.Area area = new DeadMountains.Area(100, -40, 20, 52, 164);
		// The world's dead mountain column test: dx² + dz² <= r².
		for (int x = 70; x <= 130; x++) {
			for (int z = -70; z <= -10; z++) {
				long dx = x - 100;
				long dz = z + 40;
				boolean scar = dx * dx + dz * dz <= 400;
				helper.assertTrue(area.contains(x, 100, z) == scar, "edge differs from the scar's at " + x + " " + z);
			}
		}
		helper.assertTrue(area.contains(100, 52, -40) && area.contains(100, 164, -40) && !area.contains(100, 51, -40) && !area.contains(100, 165, -40),
				"vertical band");
		helper.assertTrue(area.contains(120.9, 70.0, -39.5) && !area.contains(121.0, 70.0, -39.5) && area.contains(80.0, 70.0, -40.0)
				&& !area.contains(79.99, 70.0, -40.0), "a player's block column");

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
