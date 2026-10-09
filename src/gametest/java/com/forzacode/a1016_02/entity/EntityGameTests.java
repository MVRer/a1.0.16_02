package com.forzacode.a1016_02.entity;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.TraceService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.ParseResults;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the entity workstream, registered in the gametest fabric.mod.json. The figure's physics here; the
 * sighting gates and spawn spots in {@link SightingRuleGameTests}; goes under in {@link GoUnderGameTests}; the rush and the
 * End and Nether cards in {@link RushAndDimensionGameTests}.
 */
public class EntityGameTests extends GoUnderGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static HimEntity figure(GameTestHelper helper, Variant variant, Vec3 relative) {
		// No players in the test level, so every spot is out of view.
		return FigureApi.spawnAt(helper.getLevel(), variant, helper.absoluteVec(relative), 0.0F)
				.orElseThrow(() -> helper.assertionException("the figure did not spawn"));
	}

	@GameTest
	public void figureNeverTargetsOrAttacks(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		HimEntity him = figure(helper, Variant.CLOSE, new Vec3(4.5, 1.0, 4.5));
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)), 0.0F, 0.0F);

		helper.assertTrue(him.goalCount() == 0, "he has goals: " + him.goalCount());
		him.setTarget(player);
		helper.assertTrue(him.getTarget() == null, "he took a target");
		helper.assertFalse(him.canAttack(player), "he can attack the player");
		helper.assertFalse(him.doHurtTarget(level, player), "he hurt the player");
		helper.assertFalse(him.canBeSeenAsEnemy(), "mobs can see him as an enemy");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(him.getTarget() == null && him.goalCount() == 0, "he picked a target or a goal while ticking");
			him.discard();
			helper.succeed();
		});
	}

	@GameTest
	public void figureCannotBeHitOrHurt(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		HimEntity him = figure(helper, Variant.RIDGE, new Vec3(4.5, 1.0, 4.5));
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		float health = him.getHealth();

		helper.assertFalse(him.isPickable(), "pickable (the crosshair can target him)");
		helper.assertFalse(him.isAttackable() || him.attackable(), "attackable");
		helper.assertTrue(him.skipAttackInteraction(player), "a punch reaches him");
		helper.assertFalse(him.canBeHitByProjectile(), "projectiles hit him");
		helper.assertTrue(him.isInvulnerable(), "not invulnerable");
		helper.assertFalse(him.hurtServer(level, level.damageSources().playerAttack(player), 10.0F), "a player attack hurt him");
		helper.assertFalse(him.hurtServer(level, level.damageSources().generic(), 10.0F), "generic damage hurt him");
		helper.assertTrue(him.getHealth() == health && him.isAlive(), "he lost health");
		helper.assertFalse(him.canBeLeashed() || him.shouldShowName() || him.isCurrentlyGlowing(), "leash, name or glow");
		him.discard();
		helper.succeed();
	}

	@GameTest
	public void figureCannotBePushedOrPush(GameTestHelper helper) {
		floor(helper);
		HimEntity him = figure(helper, Variant.RIDGE, new Vec3(3.5, 1.0, 3.5));
		Cow cow = helper.spawn(EntityTypes.COW, new Vec3(3.7, 1.0, 3.5));
		cow.setNoAi(true);

		helper.assertFalse(him.isPushable() || him.isPushedByFluid(), "pushable");
		helper.assertFalse(him.canBeCollidedWith(cow), "collidable");
		him.push(1.0, 0.5, 1.0);
		him.push(new Vec3(1.0, 0.0, 1.0));
		him.push(cow);
		helper.assertTrue(him.getDeltaMovement().horizontalDistanceSqr() < 1.0E-8, "a push moved him: " + him.getDeltaMovement());
		Vec3 himAt = him.position();
		Vec3 cowAt = cow.position();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(him.position().subtract(himAt).horizontalDistance() < 1.0E-3,
					"he was pushed: " + himAt + " -> " + him.position());
			helper.assertTrue(cow.position().subtract(cowAt).horizontalDistance() < 1.0E-3, "he pushed the cow: " + cowAt + " -> " + cow.position());
			him.discard();
			cow.discard();
			helper.succeed();
		});
	}

	@GameTest
	public void aFigureInViewIsNeverRemoved(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		HimEntity him = figure(helper, Variant.RIDGE, new Vec3(4.5, 1.0, 6.5));
		Player watcher = helper.makeMockServerPlayer(GameType.SURVIVAL);
		watcher.snapTo(him.position().add(0.0, 0.0, -6.0), 0.0F, 0.0F); // 6 blocks north (inside the test), looking south at him
		Player turned = helper.makeMockServerPlayer(GameType.SURVIVAL);
		turned.snapTo(him.position().add(0.0, 0.0, -6.0), 180.0F, 0.0F); // same place, looking away
		turned.setYHeadRot(180.0F); // the view vector follows the head
		Watchers watching = new Watchers(List.of(new Watchers.Watcher(TraceService.Viewer.of(watcher, 8), 128.0)));
		Watchers lookingAway = new Watchers(List.of(new Watchers.Watcher(TraceService.Viewer.of(turned, 8), 128.0)));
		Watchers watchingFromPastRender = new Watchers(List.of(new Watchers.Watcher(TraceService.Viewer.of(watcher, 8), 4.0)));

		helper.assertTrue(watching.sees(level, him.viewBox()), "the watcher does not see him: " + watcher.position() + " look " + watcher.getViewVector(1.0F)
				+ " him " + him.position() + " alive " + him.isAlive());
		helper.assertFalse(lookingAway.sees(level, him.viewBox()), "the turned watcher sees him");
		// Edge of the ticking range (his tick) and the frozen sweep: never while he is in view...
		helper.assertFalse(him.leavesAtTickingEdge(false, watching), "removed at the ticking edge while in view");
		helper.assertFalse(FigureApi.sweepRemoves(him, false, watching), "swept while frozen in view");
		helper.assertFalse(watching.beyondRenderDistance(him.position()), "in view counts as past the render distance");
		// ...but once out of view, or past the watcher's full render distance, he goes.
		helper.assertTrue(him.leavesAtTickingEdge(false, lookingAway) && FigureApi.sweepRemoves(him, false, lookingAway), "kept out of view");
		helper.assertTrue(FigureApi.sweepRemoves(him, false, watchingFromPastRender), "kept past the full render distance");
		// A ticking figure is never swept, and still ticking near the edge he stays.
		helper.assertFalse(FigureApi.sweepRemoves(him, true, lookingAway) || him.leavesAtTickingEdge(true, lookingAway), "removed while ticking");
		helper.assertTrue(him.isAlive(), "gone");
		him.discard();
		helper.succeed();
	}

	@GameTest
	public void figureIsNeverSavedNorCounted(GameTestHelper helper) {
		floor(helper);
		HimEntity him = figure(helper, Variant.COW, new Vec3(4.5, 1.0, 4.5));
		helper.assertFalse(him.shouldBeSaved(), "he would be saved");
		helper.assertFalse(ModEntities.HIM.canSerialize(), "his type can be saved");
		helper.assertFalse(him.save(TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING)), "he wrote himself to a chunk");
		helper.assertTrue(ModEntities.HIM.getCategory() == MobCategory.MISC, "he counts toward a mob cap");
		helper.assertTrue(him.isLow(), "the cow variant does not start low");
		him.discard();
		helper.succeed();
	}

	@GameTest
	public void eyeStyleDefaultsAndParsing(GameTestHelper helper) {
		EntityConfig fresh = new EntityConfig();
		helper.assertTrue(fresh.eyeStyle() == EyeStyle.BRIGHT, "the default eye style is " + fresh.eyeStyle());
		helper.assertTrue(fresh.eyeFogResistance() == 0.5, "the default fog resistance is " + fresh.eyeFogResistance());
		fresh.eyeStyle = null; // an unknown value in the file reads as null
		fresh.eyeFogResistance = 3.0;
		helper.assertTrue(fresh.eyeStyle() == EyeStyle.BRIGHT && fresh.eyeFogResistance() == 1.0, "bad values are not tamed");
		for (EyeStyle style : EyeStyle.values()) {
			helper.assertTrue(EyeStyle.byName(style.shortName()).orElse(null) == style, "does not round-trip: " + style);
		}
		helper.assertTrue(EyeStyle.byName("GLOW").orElse(null) == EyeStyle.GLOW && EyeStyle.byName("red").isEmpty(), "byName");
		helper.succeed();
	}

	@GameTest
	public void eyesCommandSetsSavesAndPrints(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		EntityConfig config = EntityConfig.get();
		EyeStyle oldStyle = config.eyeStyle();
		double oldResistance = config.eyeFogResistance();
		try {
			for (String command : List.of("a1016 entity eyes", "a1016 entity eyes flat", "a1016 entity eyes glow 0.25")) {
				ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, source);
				helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "does not parse: " + command);
			}
			server.getCommands().performPrefixedCommand(source, "a1016 entity eyes");
			server.getCommands().performPrefixedCommand(source, "a1016 entity eyes glow 0.25");
			helper.assertTrue(config.eyeStyle() == EyeStyle.GLOW && config.eyeFogResistance() == 0.25, "not set live: " + config.eyeStyle());
			JsonObject saved = savedEntitySection();
			helper.assertTrue("GLOW".equals(saved.get("eyeStyle").getAsString()) && saved.get("eyeFogResistance").getAsDouble() == 0.25,
					"not saved: " + saved);
			// The style alone keeps the resistance; out of range and unknown styles change nothing.
			server.getCommands().performPrefixedCommand(source, "a1016 entity eyes flat");
			server.getCommands().performPrefixedCommand(source, "a1016 entity eyes bright 1.5");
			server.getCommands().performPrefixedCommand(source, "a1016 entity eyes red");
			helper.assertTrue(config.eyeStyle() == EyeStyle.FLAT && config.eyeFogResistance() == 0.25, "changed by a bad command: " + config.eyeStyle()
					+ " " + config.eyeFogResistance());
			helper.assertTrue(config == EntityConfig.get(), "the renderer would read another instance");
		} finally {
			config.eyeStyle = oldStyle;
			config.eyeFogResistance = oldResistance;
			config.save();
		}
		helper.succeed();
	}

	@GameTest
	public void tuneCommandSetsSavesAndRefusesBadValues(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		EntityConfig config = EntityConfig.get();
		double oldApproach = config.approachBlocks;
		double oldFlee = config.fleeDistance;
		double oldNormalMin = config.normalFractionMin;
		double oldCloseMin = config.closeMinDistance;
		double oldMinDistance = config.minDistance;
		double oldOutrun = config.outrunFactor;
		double oldMaxRun = config.maxRunSpeed;
		try {
			for (EntityTuning.Key key : EntityTuning.KEYS) {
				String command = "a1016 entity tune " + key.name() + " " + EntityTuning.format(key.get().applyAsDouble(config));
				ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, source);
				helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "does not parse: " + command);
			}
			for (String name : List.of("minDistance", "closeFractionMin", "closeFractionMax", "normalFractionMin", "normalFractionMax", "outrunFactor",
					"maxRunSpeed", "goUnderChance", "goUnderDigSeconds", "goUnderMinDepth", "goUnderMaxDepth", "rushTriggerDistance", "rushPassOffset",
					"rushMaxSeconds", "amongMobsRadius")) {
				helper.assertTrue(EntityTuning.byName(name).isPresent(), name + " is not tunable");
			}
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune approachBlocks 12.5");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune normalFractionMin 0.5");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune minDistance 10");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune outrunFactor 1.25");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune maxRunSpeed 10");
			helper.assertTrue(config.approachBlocks == 12.5 && config.normalFractionMin == 0.5 && config.minDistance == 10 && config.outrunFactor == 1.25
					&& config.maxRunSpeed == 10, "not set live");
			JsonObject saved = savedEntitySection();
			helper.assertTrue(saved.get("approachBlocks").getAsDouble() == 12.5 && saved.get("normalFractionMin").getAsDouble() == 0.5
					&& saved.get("minDistance").getAsDouble() == 10 && saved.get("outrunFactor").getAsDouble() == 1.25, "not saved: " + saved);
			// Out of range (closer than 8 blocks, an outrun that lets you catch him) and unknown keys change nothing.
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune fleeDistance 100");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune minDistance 6");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune closeMinDistance 4");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune outrunFactor 0.9");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune runSpeed 3");
			// A pass closer than 1.5 blocks, a shaft too shallow to cover him: refused.
			double oldOffset = config.rushPassOffset;
			int oldMinDepth = config.goUnderMinDepth;
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune rushPassOffset 1");
			server.getCommands().performPrefixedCommand(source, "a1016 entity tune goUnderMinDepth 2");
			helper.assertTrue(config.rushPassOffset == oldOffset && config.goUnderMinDepth == oldMinDepth, "an unsafe rush or dig tuning was taken");
			helper.assertTrue(config.fleeDistance == oldFlee, "an out-of-range flee distance was taken: " + config.fleeDistance);
			helper.assertTrue(config.minDistance == 10, "a minimum distance under 8 blocks was taken: " + config.minDistance);
			helper.assertTrue(config.closeMinDistance == oldCloseMin, "a close clamp under 8 blocks was taken: " + config.closeMinDistance);
			helper.assertTrue(config.outrunFactor == 1.25, "an outrun factor under 1 was taken: " + config.outrunFactor);
			helper.assertTrue(EntityTuning.describe(config).contains("approachBlocks=12.50"), EntityTuning.describe(config));
		} finally {
			config.approachBlocks = oldApproach;
			config.fleeDistance = oldFlee;
			config.normalFractionMin = oldNormalMin;
			config.minDistance = oldMinDistance;
			config.outrunFactor = oldOutrun;
			config.maxRunSpeed = oldMaxRun;
			config.save();
		}
		helper.succeed();
	}

	@GameTest
	public void olderConfigSectionsGetTheNewDefaults(GameTestHelper helper) {
		EntityConfig defaults = new EntityConfig();
		helper.assertTrue(defaults.approachBlocks == 10 && defaults.stareConeDegrees == 8 && defaults.stareBackSeconds == 2 && defaults.stareSeconds == 3
				&& defaults.fleeDistance == 18 && defaults.minSeenSeconds == 3, "playtest defaults");
		helper.assertTrue(defaults.normalFractionMin == 0.55 && defaults.normalFractionMax == 0.75 && defaults.closeFractionMin == 0.35
				&& defaults.closeFractionMax == 0.50 && defaults.closeMinDistance == 16 && defaults.closeMaxDistance == 28 && defaults.minDistance == 12
				&& defaults.fleeSpawnFraction == 0.6, "D-035 defaults");
		helper.assertTrue(defaults.baseRunSpeed == 5.8 && defaults.outrunFactor == 1.1 && defaults.maxRunSpeed == 9.0, "D-036 defaults");
		// A section written before the playtest (no version): the old scaffold values give way, other values stay.
		EntityConfig old = new EntityConfig();
		old.approachBlocks = 6;
		old.stareConeDegrees = 6;
		old.stareBackSeconds = 1.0;
		old.closeMinDistance = 24;
		old.closeMaxDistance = 36;
		old.baseRadius = 80;
		helper.assertTrue(old.upgrade(), "an old section was not upgraded");
		helper.assertTrue(old.approachBlocks == 10 && old.stareConeDegrees == 8 && old.stareBackSeconds == 2 && old.baseRadius == 80,
				"upgrade: approach " + old.approachBlocks + " cone " + old.stareConeDegrees + " back " + old.stareBackSeconds + " base " + old.baseRadius);
		helper.assertTrue(old.closeMinDistance == 16 && old.closeMaxDistance == 28, "the old 24..36 close band stayed: " + old.closeMinDistance);
		// A version 2 section (after the first playtest) keeps its tunings but loses the 24-block close band.
		EntityConfig v2 = new EntityConfig();
		v2.version = 2;
		v2.approachBlocks = 7;
		v2.closeMinDistance = 24;
		v2.closeMaxDistance = 36;
		helper.assertTrue(v2.upgrade() && v2.approachBlocks == 7 && v2.closeMinDistance == 16 && v2.closeMaxDistance == 28, "version 2 upgrade");
		// Once upgraded, values tuned later are left alone.
		old.approachBlocks = 14;
		old.closeMinDistance = 20;
		helper.assertFalse(old.upgrade(), "upgraded twice");
		helper.assertTrue(old.approachBlocks == 14 && old.closeMinDistance == 20, "a later tuning was reset");
		helper.succeed();
	}

	private static JsonObject savedEntitySection() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(A1016_02.MOD_ID + ".json");
		try (Reader reader = Files.newBufferedReader(path)) {
			return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("sections").getAsJsonObject("entity");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
