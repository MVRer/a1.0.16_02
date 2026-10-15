package com.forzacode.a1016_02.atmosphere.card;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Far in the rock (D-070) and the moved sound cards: the card's identity and gates (underground, nobody else within
 * 24), the underground rule on real blocks and light, and where its sound comes from: inside solid rock, 18 to 28
 * blocks off, below or to the side, the rock's own sound, faint, heard through {@link SoundOut}.
 * {@link CardSoundGameTests} extends this.
 */
public class RockSoundGameTests {
	private static final String YARD = "a1016_02:accident/yard";
	/** 48 x 32 x 48 of air, filled with rock by the test (the far sound needs 18 to 28 blocks of it all around). */
	private static final String ROCK = "a1016_02:atmosphere/rock";
	/**
	 * The rock test's own environment, so its batch has no other test: the light work its 20 000 blocks cause must
	 * never hold up another test waiting on far chunks to generate in its few hundred ticks (the house copy's site
	 * search, 300 blocks out).
	 */
	private static final String ROCK_ENVIRONMENT = "a1016_02:atmosphere/rock";
	private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	@GameTest
	public void theV12SoundCardsAreWhereD070PutsThem(GameTestHelper helper) {
		EventCard rock = CardRegistry.get(FarInTheRockCard.ID).orElseThrow();
		helper.assertTrue(rock.tier() == Tier.AMBIENT && rock.earliestStage() == Stage.ALONE && rock.habits().isEmpty() && rock.tags().equals(Set.of(CardTag.SOUND))
				&& rock.hasFake(), "far in the rock is not an Alone ambient sound with a fake");
		helper.assertTrue(CardRegistry.get(ChestOpensCard.ID).orElseThrow().earliestStage() == Stage.TRACES, "chest_opens is not from Traces");
		helper.assertTrue(CardRegistry.get(FootstepLateCard.ID).orElseThrow().earliestStage() == Stage.TRACES, "footstep_late is not from Traces");
		helper.assertTrue(CardRegistry.get(DistantCaveSoundCard.ID).orElseThrow().earliestStage() == Stage.ALONE, "the cave sound moved");
		AtmosphereConfig d = new AtmosphereConfig();
		helper.assertTrue(d.caveSoundAloneRadius == 24 && d.caveSoundStillSeconds == 8 && d.fogDriftFadeTicks == 160 && d.rockSoundAloneRadius == 24
				&& d.rockSoundMinDistance == 18 && d.rockSoundMaxDistance == 28, "D-070's atmosphere numbers");
		// Faint but heard: the hearing range reaches past the farthest spot, and the far end is quiet.
		helper.assertTrue(Math.max(d.rockSoundVolume, 1.0) * 16 > d.rockSoundMaxDistance && 1.0 - d.rockSoundMinDistance / (16 * d.rockSoundVolume) < 0.5,
				"the far sound is out of hearing range, or not faint");
		helper.assertTrue(FarInTheRockCard.soundOf(Blocks.GRAVEL.defaultBlockState(), true) == SoundEvents.GRAVEL_BREAK
				&& FarInTheRockCard.soundOf(Blocks.STONE.defaultBlockState(), true) == SoundEvents.STONE_HIT
				&& FarInTheRockCard.soundOf(Blocks.STONE.defaultBlockState(), false) == SoundEvents.STONE_BREAK
				&& FarInTheRockCard.soundOf(Blocks.DEEPSLATE.defaultBlockState(), true) == SoundEvents.DEEPSLATE_HIT
				&& FarInTheRockCard.soundOf(Blocks.DEEPSLATE.defaultBlockState(), false) == SoundEvents.DEEPSLATE_BREAK, "not the rock's own sounds");
		helper.assertTrue(WorldScan.isRock(Blocks.STONE.defaultBlockState()) && WorldScan.isRock(Blocks.DEEPSLATE.defaultBlockState())
				&& WorldScan.isRock(Blocks.GRAVEL.defaultBlockState()) && WorldScan.isRock(Blocks.TUFF.defaultBlockState())
				&& !WorldScan.isRock(Blocks.DIRT.defaultBlockState()) && !WorldScan.isRock(Blocks.COBBLESTONE.defaultBlockState())
				&& !WorldScan.isRock(Blocks.END_STONE.defaultBlockState()), "what counts as rock");
		helper.succeed();
	}

	/** The gates: underground, and no other participant (spectators do not count) within 24 blocks; nothing else. */
	@GameTest(dimension = "minecraft:the_end", structure = YARD)
	public void farInTheRockNeedsUndergroundAndAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer a = CardSoundGameTests.at(helper, new Vec3(12.5, 1.0, 12.5));
		ServerPlayer b = CardSoundGameTests.at(helper, new Vec3(12.5, 1.0, 12.5 + 20));
		ServerPlayer watcher = (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR);
		Vec3 near = helper.absoluteVec(new Vec3(13.5, 1.0, 12.5));
		watcher.snapTo(near.x, near.y, near.z, 0.0F, 0.0F);
		FarInTheRockCard card = new FarInTheRockCard();
		card.everyone = l -> List.of(a, b, watcher);
		card.underground = (l, pos) -> true;
		helper.assertFalse(card.contextFits(a, level), "fits with B 20 blocks away");
		Vec3 far = helper.absoluteVec(new Vec3(12.5, 1.0, 12.5 + 30));
		b.snapTo(far.x, far.y, far.z, 0.0F, 0.0F);
		helper.assertTrue(card.contextFits(a, level), "B 30 blocks away (and a spectator beside A) still stop it");
		card.underground = (l, pos) -> false;
		helper.assertFalse(card.contextFits(a, level), "fits on the surface");
		// The End has no sky light: never underground there.
		helper.assertFalse(WorldScan.underground(level, a.blockPosition()), "underground in the End");
		helper.succeed();
	}

	/**
	 * The underground rule on real blocks and light: a sealed pocket at the foot of a 14-high stone pillar is
	 * underground (no sky light, well below the column's top), the pillar's top in the open is not; far in the rock
	 * fits in the pocket, and the cave sound there needs no stillness.
	 */
	@GameTest(structure = YARD, skyAccess = true, maxTicks = 200)
	public void undergroundIsUnderRockAndOutOfTheSky(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 10; x <= 14; x++) {
			for (int z = 10; z <= 14; z++) {
				for (int y = 0; y <= 13; y++) {
					boolean pocket = x == 12 && z == 12 && (y == 1 || y == 2);
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), (pocket ? Blocks.AIR : Blocks.STONE).defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		BlockPos pocket = helper.absolutePos(new BlockPos(12, 1, 12));
		BlockPos top = helper.absolutePos(new BlockPos(12, 14, 12));
		ServerPlayer a = CardSoundGameTests.at(helper, new Vec3(12.5, 1.0, 12.5));
		FarInTheRockCard card = new FarInTheRockCard();
		card.everyone = l -> List.of(a);
		helper.succeedWhen(() -> {
			helper.assertTrue(WorldScan.underground(level, pocket), "the sealed pocket is not underground (sky light " + level.getBrightness(LightLayer.SKY, pocket) + ")");
			helper.assertFalse(WorldScan.underground(level, top), "the pillar's top in the open is underground");
			helper.assertTrue(card.contextFits(a, level), "far in the rock does not fit in the pocket");
			helper.assertTrue(Gates.caveSound(true, WorldScan.underground(level, a.blockPosition()), 0, 160), "the cave sound needs stillness underground");
		});
	}

	/**
	 * Where it comes from: every sound is the rock's own (a stone or deepslate hit or break), from a block of rock
	 * buried on all six sides, 18 to 28 blocks from the player and at most 14 below or 1 above, at its volume, heard by
	 * the player and nobody 120 blocks off; out of the rock, no spot.
	 */
	@GameTest(environment = ROCK_ENVIRONMENT, dimension = "minecraft:the_end", structure = ROCK, maxTicks = 40, padding = 24)
	public void farInTheRockSoundsFromInsideTheRock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
		// A shell of rock 16.5 to 29.5 blocks around the player's feet (24, 29, 24), from 14 to 31 high: every spot the
		// card may pick and each of its six sides. Deepslate below 20, stone above.
		for (int x = 0; x < 48; x++) {
			for (int z = 0; z < 48; z++) {
				for (int y = 14; y < 32; y++) {
					double d2 = (x - 24) * (x - 24) + (y - 29) * (y - 29) + (z - 24) * (z - 24);
					if (d2 >= 16.5 * 16.5 && d2 <= 29.5 * 29.5) {
						level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), y < 20 ? deepslate : stone, SET_FLAGS);
					}
				}
			}
		}
		ServerPlayer a = CardSoundGameTests.at(helper, new Vec3(24.5, 29.0, 24.5));
		ServerPlayer far = CardSoundGameTests.at(helper, new Vec3(24.5, 29.0, 24.5 + 120));
		List<CardSoundGameTests.Heard> log = new CopyOnWriteArrayList<>();
		FarInTheRockCard card = new FarInTheRockCard();
		card.sounds = CardSoundGameTests.recorder(log, List.of(a, far));
		card.everyone = l -> List.of(a, far);
		AtmosphereConfig cfg = AtmosphereConfig.get();
		BlockPos feet = a.blockPosition();
		int fired = 0;
		Set<SoundEvent> heard = new HashSet<>();
		for (long seed = 1; seed <= 60; seed++) {
			FireResult result = card.fire(new FireContext(a, level, false, true, RandomSource.create(seed)));
			if (result != FireResult.FIRED) {
				helper.assertTrue(result == FireResult.NO_SPOT, "seed " + seed + ": " + result);
				continue;
			}
			fired++;
			helper.assertTrue(log.size() == fired, "one sound a fire: " + log.size());
			CardSoundGameTests.Heard sound = log.getLast();
			BlockPos spot = BlockPos.containing(sound.pos());
			double distance = Math.sqrt(spot.distSqr(feet));
			int dy = spot.getY() - feet.getY();
			String where = "seed " + seed + ": " + spot.subtract(feet).toShortString() + " (" + String.format(java.util.Locale.ROOT, "%.1f", distance) + " blocks)";
			helper.assertTrue(distance >= cfg.rockSoundMinDistance && distance <= cfg.rockSoundMaxDistance, "not 18 to 28 blocks off: " + where);
			helper.assertTrue(dy >= -cfg.rockSoundMaxBelow && dy <= cfg.rockSoundMaxAbove, "overhead or too deep: " + where);
			BlockState state = level.getBlockState(spot);
			helper.assertTrue(WorldScan.isRock(state) && WorldScan.buried(level, spot) && sound.pos().equals(Vec3.atCenterOf(spot)),
					"not from inside the rock: " + where + " " + state);
			helper.assertTrue(sound.sound().value() == FarInTheRockCard.soundOf(state, true) || sound.sound().value() == FarInTheRockCard.soundOf(state, false),
					"not the rock's own sound: " + sound.sound().value() + " in " + state + " " + where);
			helper.assertTrue(sound.volume() == (float) cfg.rockSoundVolume, "volume " + sound.volume());
			helper.assertTrue(sound.hearers().equals(List.of(a.getUUID())), "heard by " + sound.hearers() + ", not the player alone " + where);
			heard.add(sound.sound().value());
		}
		helper.assertTrue(fired >= 30, "only " + fired + " of 60 fires found the rock");
		helper.assertTrue((heard.contains(SoundEvents.STONE_HIT) || heard.contains(SoundEvents.STONE_BREAK))
				&& (heard.contains(SoundEvents.DEEPSLATE_HIT) || heard.contains(SoundEvents.DEEPSLATE_BREAK)), "never stone and deepslate both: " + heard);

		// (The fake is the vanilla cave sound, played to the player alone with SoundCues.playTo: a mock player has no connection to send it on.)
		ServerPlayer outside = CardSoundGameTests.at(helper, new Vec3(24.5, 3.0, 24.5));
		helper.assertTrue(card.fire(new FireContext(outside, level, false, true, RandomSource.create(5))) == FireResult.NO_SPOT, "a spot with no rock around");
		helper.succeed();
	}
}
