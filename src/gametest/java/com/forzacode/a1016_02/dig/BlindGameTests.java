package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.ParseResults;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.server.level.ServerPlayer.RespawnConfig;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Blind moments (D-077): the three cards fire only while their player has been blind (a menu open) 1.5 s or more,
 * alone (no friend and no spectator within 48), never within 30 s of combat; the step lands 3 to 5 behind against the
 * facing, the floor's own step sound; the torch rules (a player's, out of everyone's view, 6 to 20 away, another light
 * within 8, never the bed's last light, its support kept, ledgered with its cause); the door plays its own sounds and
 * nothing moves. Each test has its own crew, watch and view ({@link BlindRig}); none of their players joins the server.
 */
public class BlindGameTests extends LadderGameTests {
	/** Spreads test seeds apart (a legacy random's first draw is nearly the same for neighbouring small seeds). */
	private static final long SPREAD = 0x9E3779B97F4A7C15L;

	/** The three cards are registered: tiers, stages, tags (BLIND and PERSONAL on all), no habits, the step's fake only. */
	@GameTest
	public void theBlindCardsAreRegistered(GameTestHelper helper) {
		record Expect(String id, Tier tier, Stage stage, Set<CardTag> tags, boolean fake) {
		}
		for (Expect expect : List.of(
				new Expect(BlindCards.BlindStep.ID, Tier.AMBIENT, Stage.ALONE, Set.of(CardTag.SOUND, CardTag.PERSONAL, CardTag.BLIND), true),
				new Expect(BlindCards.BlindTorch.ID, Tier.MINOR, Stage.TRACES, Set.of(CardTag.LIGHT, CardTag.PERSONAL, CardTag.BLIND), false),
				new Expect(BlindCards.BlindDoor.ID, Tier.MINOR, Stage.TRACES, Set.of(CardTag.SOUND, CardTag.PERSONAL, CardTag.BLIND), false))) {
			EventCard card = CardRegistry.get(expect.id()).orElse(null);
			helper.assertTrue(card != null, "not registered: " + expect.id());
			helper.assertTrue(card.tier() == expect.tier() && card.earliestStage() == expect.stage() && card.tags().equals(expect.tags())
					&& card.habits().isEmpty() && card.hasFake() == expect.fake(), expect.id() + " is " + card.tier() + " " + card.earliestStage() + " "
					+ card.tags() + " " + card.habits() + " fake " + card.hasFake());
		}
		// The live gates: a mock player is never blind on the live watch, so nothing fits and even a forced fire is skipped.
		DigGround g = DigGround.of(helper, 60, 8, 4, 8, Blocks.STONE);
		g.stand(g.at(4, 4, 4));
		for (Blind.Kind kind : Blind.Kind.values()) {
			EventCard card = CardRegistry.get(kind.id).orElseThrow();
			helper.assertFalse(card.contextFits(g.mock, g.level), kind.id + " fits a player who is not blind");
			for (boolean fake : List.of(false, true)) {
				helper.assertTrue(card.fire(new FireContext(g.mock, g.level, fake, true, RandomSource.create(1L))) == FireResult.SKIPPED,
						"a forced " + kind.id + (fake ? " (fake)" : "") + " fired for a player who is not blind");
			}
		}
		helper.succeed();
	}

	/**
	 * Every card waits for a blind moment 1.5 s old (not 1.45 s), keeps out of combat for 30 s, and stops fitting the
	 * moment the menu closes; with all of that, each fires.
	 */
	@GameTest(maxTicks = 100)
	public void everyBlindCardWaitsForOneAndAHalfSecondsBlindOutOfCombat(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 61, 40, 10, 40, Blocks.STONE);
		g.hollow(8, 2, 8, 37, 7, 37);
		BlindRig rig = new BlindRig(helper, g, "Alex");
		ServerPlayer alex = rig.stand("Alex", 20, 2, 20, LadderRig.EAST);
		// A torch the player placed behind them with a lamp near it, and a closed door behind them.
		BlockPos torch = g.at(10, 2, 20);
		g.place(torch, Blocks.TORCH.defaultBlockState());
		g.place(g.at(10, 2, 16), Blocks.GLOWSTONE.defaultBlockState());
		BlockPos door = door(g, 14, 2, 23, Blocks.OAK_DOOR);
		long wait = BlindRig.seconds(rig.config.blindMinSeconds);
		helper.assertTrue(wait == 30, "1.5 s is " + wait + " ticks");
		for (Blind.Kind kind : Blind.Kind.values()) {
			helper.assertTrue(rig.check(kind, alex) == Blind.Why.NOT_BLIND, kind.id + " fits a player who is not blind: " + rig.check(kind, alex));
			helper.assertTrue(rig.fire(kind, alex, false, true, 1L) == FireResult.SKIPPED, "a forced " + kind.id + " fired with no menu open");
		}
		rig.blind(alex, wait - 1);
		for (Blind.Kind kind : Blind.Kind.values()) {
			helper.assertTrue(rig.check(kind, alex) == Blind.Why.NOT_BLIND, kind.id + " fits after 1.45 s blind: " + rig.check(kind, alex));
			helper.assertTrue(rig.fire(kind, alex, false, false, 2L) == FireResult.SKIPPED, kind.id + " fired after 1.45 s blind");
		}
		helper.assertTrue(rig.heard.isEmpty() && g.level.getBlockState(torch).is(Blocks.TORCH), "something happened before 1.5 s");
		rig.blind(alex, wait);
		for (Blind.Kind kind : Blind.Kind.values()) {
			helper.assertTrue(rig.check(kind, alex) == null, kind.id + " does not fit after 1.5 s blind: " + rig.check(kind, alex));
		}
		long combat = ModConfig.realTicks(rig.config.blindNoCombatSeconds);
		rig.sinceCombat = combat;
		for (Blind.Kind kind : Blind.Kind.values()) {
			helper.assertTrue(rig.check(kind, alex) == Blind.Why.COMBAT, kind.id + " 30 s after combat: " + rig.check(kind, alex));
			helper.assertTrue(rig.fire(kind, alex, false, true, 3L) == FireResult.SKIPPED, "a forced " + kind.id + " 30 s after combat");
		}
		rig.sinceCombat = combat + 1;
		helper.assertTrue(rig.check(Blind.Kind.DOOR, alex) == null, "the door does not fit past 30 s after combat");
		rig.crew.watch.onBlindState(alex, false);
		for (Blind.Kind kind : Blind.Kind.values()) {
			helper.assertTrue(rig.check(kind, alex) == Blind.Why.NOT_BLIND, kind.id + " fits once the menu closed: " + rig.check(kind, alex));
		}
		helper.assertTrue(rig.heard.isEmpty() && g.level.getBlockState(torch).is(Blocks.TORCH), "something happened while it did not fit");
		rig.blind(alex, wait + 40);
		helper.assertTrue(rig.fire(Blind.Kind.STEP, alex, false, false, 4L) == FireResult.FIRED && rig.heard.size() == 1, "the step did not fire");
		helper.assertTrue(rig.fire(Blind.Kind.DOOR, alex, false, false, 5L) == FireResult.FIRED && rig.heard.size() == 2
				&& rig.heard.getLast().pos().equals(Vec3.atCenterOf(door)), "the door did not fire");
		helper.assertTrue(rig.fire(Blind.Kind.TORCH, alex, false, false, 6L) == FireResult.FIRED && g.level.getBlockState(torch).isAir(),
				"the torch did not go");
		rig.crew.watch.onBlindState(alex, false);
		helper.succeed();
	}

	/**
	 * The step: 3 to 5 blocks behind the player, straight against their facing (along an axis or not), on the floor, the
	 * floor block's own step sound (deepslate here, a slab's own too), PLAYERS, at the step volume (at most 1) and the
	 * sound type's pitch; two steps come a walking step apart, the second a block closer. With a wall at their back there
	 * is no step (no spot). The fake: their own step at their feet, at vanilla's step volume, a walking step late.
	 */
	@GameTest(maxTicks = 200)
	public void blindStepLandsThreeToFiveBehindAgainstTheFacing(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 62, 40, 10, 40, Blocks.STONE);
		g.keepLoaded(true);
		g.hollow(8, 2, 8, 37, 7, 37);
		for (int x = 8; x <= 37; x++) {
			for (int z = 8; z <= 37; z++) {
				g.level.setBlock(g.at(x, 1, z), Blocks.DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		BlindRig rig = new BlindRig(helper, g, "Alex");
		DigConfig config = rig.config;
		ServerPlayer alex = rig.stand("Alex", 20, 2, 20, LadderRig.EAST);
		rig.blind(alex, 60);
		int twos = 0;
		for (long i = 0; i < 40; i++) {
			long seed = i * SPREAD;
			rig.heard.clear();
			helper.assertTrue(rig.fire(Blind.Kind.STEP, alex, false, false, seed) == FireResult.FIRED, "the step did not fire (seed " + seed + ")");
			helper.assertTrue(rig.heard.size() == 1, "not one step at once: " + rig.heard.size());
			BlindRig.Heard step = rig.heard.getFirst();
			double back = alex.getX() - step.pos().x;
			helper.assertTrue(back >= config.blindStepBackMin - 1.0E-6 && back <= config.blindStepBackMax + 1.0E-6 && Math.abs(back - Math.round(back)) < 1.0E-6
					&& Math.abs(step.pos().z - alex.getZ()) < 1.0E-6 && step.pos().y == alex.getY(), "a step not 3 to 5 straight behind on the floor: "
					+ step.pos());
			helper.assertTrue(step.sound().value() == SoundEvents.DEEPSLATE_STEP && step.source() == SoundSource.PLAYERS
					&& step.volume() == config.blindStepVolume && step.volume() <= 1.0F && step.pitch() == SoundType.DEEPSLATE.getPitch(),
					"not the floor's own step: " + step);
			BlindCues.Sequence walk = BlindRig.sequence(alex);
			if (walk != null) {
				twos++;
				helper.assertTrue(walk.cues.size() == 2 && walk.cues.get(1).at() == config.blindStepTicks, "the second step: " + walk.cues);
				helper.assertTrue(Math.abs(alex.getX() - walk.cues.get(1).pos().x - (back - 1)) < 1.0E-6, "the second step is not a block closer");
			}
		}
		helper.assertTrue(twos > 5 && twos < 35, "two steps in " + twos + " of 40");

		// Facing off the axes: straight against the facing, 3 to 5 away.
		LadderRig.look(alex, 30.0F);
		Vec3 facing = Blind.facing(alex);
		for (long i = 40; i < 50; i++) {
			long seed = i * SPREAD;
			rig.heard.clear();
			helper.assertTrue(rig.fire(Blind.Kind.STEP, alex, false, false, seed) == FireResult.FIRED, "the step did not fire looking at 30 degrees");
			Vec3 away = rig.heard.getFirst().pos().subtract(alex.position());
			double flat = Math.sqrt(away.x * away.x + away.z * away.z);
			helper.assertTrue(flat >= config.blindStepBackMin - 1.0E-6 && flat <= config.blindStepBackMax + 1.0E-6
					&& (away.x * facing.x + away.z * facing.z) / flat < -0.9999, "a step not straight behind: " + away + " facing " + facing);
		}

		// A floor of slabs behind them: the slab's own step (wood), not the stone the ladder takes for a tunnel's floor.
		LadderRig.look(alex, LadderRig.EAST);
		for (int x = 14; x <= 18; x++) {
			g.level.setBlock(g.at(x, 1, 20), Blocks.OAK_SLAB.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		rig.heard.clear();
		helper.assertTrue(rig.fire(Blind.Kind.STEP, alex, false, false, SPREAD) == FireResult.FIRED
				&& rig.heard.getFirst().sound().value() == SoundType.WOOD.getStepSound(), "not the slab's own step: " + rig.heard);

		// A wall at their back: no step (the director keeps the card), nothing heard.
		LadderRig.move(alex, g.at(9, 2, 20));
		LadderRig.look(alex, LadderRig.EAST);
		rig.heard.clear();
		helper.assertTrue(rig.check(Blind.Kind.STEP, alex) == Blind.Why.NO_FLOOR, "a step behind a wall: " + rig.check(Blind.Kind.STEP, alex));
		helper.assertTrue(rig.fire(Blind.Kind.STEP, alex, false, false, 1L) == FireResult.NO_SPOT && rig.heard.isEmpty(), "a step in the wall");

		// Two steps, a walking step apart, and the fake (another crew's player): their own step, late.
		LadderRig.move(alex, g.at(20, 2, 20));
		long two = SPREAD;
		while (Blind.planSteps(alex, config, RandomSource.create(two)).size() != 2) {
			two += SPREAD;
		}
		BlindRig own = new BlindRig(helper, g, "Bea");
		ServerPlayer bea = own.stand("Bea", 30, 2, 30, LadderRig.EAST);
		own.blind(bea, 60);
		rig.heard.clear();
		long start = g.level.getServer().getTickCount();
		helper.assertTrue(rig.fire(Blind.Kind.STEP, alex, false, false, two) == FireResult.FIRED, "two steps did not fire");
		helper.assertTrue(own.fire(Blind.Kind.STEP, bea, true, false, 1L) == FireResult.FIRED && own.heard.isEmpty(), "the fake was not late");
		helper.succeedWhen(() -> {
			helper.assertTrue(BlindRig.sequence(alex) == null && BlindRig.sequence(bea) == null, "the steps are still coming");
			helper.assertTrue(rig.heard.size() == 2 && rig.heard.get(0).tick() == start && rig.heard.get(1).tick() == start + config.blindStepTicks,
					"two steps at " + rig.heard.stream().map(h -> h.tick() - start).toList());
			helper.assertTrue(Math.abs(rig.heard.get(0).pos().x - rig.heard.get(1).pos().x + 1) < 1.0E-6, "the second step is not a block closer");
			helper.assertTrue(own.heard.size() == 1, "the fake: " + own.heard.size() + " steps");
			BlindRig.Heard step = own.heard.getFirst();
			helper.assertTrue(step.tick() == start + config.blindStepTicks && step.pos().equals(bea.position()) && step.sound().value() == SoundEvents.DEEPSLATE_STEP
					&& Math.abs(step.volume() - SoundType.DEEPSLATE.getVolume() * 0.15F) < 1.0E-6, "the fake is not their own step, late: " + step);
			rig.crew.watch.onBlindState(alex, false);
			own.crew.watch.onBlindState(bea, false);
			g.keepLoaded(false);
		});
	}

	/**
	 * The torch rules: only a light a player placed (a torch, wall torch or lantern, never a lamp or a torch nobody
	 * placed), 6 to 20 blocks away, out of everyone's view, with another light within 8 of it, never the last light
	 * within 6 of a bed; the one that goes keeps its support, goes silently and is ledgered with its cause. Each light
	 * here fails exactly one rule until it is mended.
	 */
	@GameTest(maxTicks = 100)
	public void blindTorchTakesOnlyALightThatMayGo(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 63, 48, 10, 48, Blocks.STONE);
		g.hollow(2, 2, 2, 45, 7, 45);
		BlindRig rig = new BlindRig(helper, g, "Alex");
		ServerPlayer alex = rig.stand("Alex", 24, 2, 24, LadderRig.EAST);
		rig.blind(alex, 60);
		BlockPos bed = g.at(10, 2, 10);
		g.level.setBlock(bed, Blocks.BED.red().defaultBlockState(), Block.UPDATE_CLIENTS);
		alex.setRespawnPosition(new RespawnConfig(LevelData.RespawnData.of(g.level.dimension(), bed, 0.0F, 0.0F), true), false);
		helper.assertTrue(rig.crew.bases().all(g.level.dimension()).contains(bed), "the bed is not the player's base");

		BlockPos near = g.at(20, 2, 24); // 4 away
		g.place(near, Blocks.TORCH.defaultBlockState());
		g.place(g.at(20, 2, 20), Blocks.GLOWSTONE.defaultBlockState());
		BlockPos far = g.at(2, 2, 24); // 22 away
		g.place(far, Blocks.TORCH.defaultBlockState());
		g.place(g.at(2, 2, 20), Blocks.GLOWSTONE.defaultBlockState());
		BlockPos natural = g.at(14, 2, 24); // nobody placed it
		g.level.setBlock(natural, Blocks.TORCH.defaultBlockState(), Block.UPDATE_CLIENTS);
		g.level.setBlock(g.at(14, 2, 20), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockPos lone = g.at(14, 2, 40); // no other light within 8
		g.place(lone, Blocks.SOUL_LANTERN.defaultBlockState());
		BlockPos seen = g.at(34, 2, 24); // in front of the player
		g.place(seen, Blocks.TORCH.defaultBlockState());
		g.place(g.at(34, 2, 20), Blocks.GLOWSTONE.defaultBlockState());
		BlockPos byBed = g.at(12, 2, 12); // the bed's only light; a lamp 7 away from it, 9 from the bed
		BlockPos support = byBed.west();
		g.level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		g.place(byBed, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
		g.level.setBlock(g.at(12, 2, 19), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		List<BlockPos> lights = List.of(near, far, natural, lone, seen, byBed);
		List<BlockState> before = lights.stream().map(g.level::getBlockState).toList();

		// What counts as a light it may take.
		for (BlockState light : List.of(Blocks.TORCH.defaultBlockState(), Blocks.WALL_TORCH.defaultBlockState(), Blocks.SOUL_TORCH.defaultBlockState(),
				Blocks.SOUL_WALL_TORCH.defaultBlockState(), Blocks.COPPER_TORCH.defaultBlockState(), Blocks.LANTERN.defaultBlockState(),
				Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true))) {
			helper.assertTrue(Blind.isBlindLight(light), "not a light it may take: " + light);
		}
		for (BlockState other : List.of(Blocks.REDSTONE_TORCH.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState(),
				Blocks.JACK_O_LANTERN.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState())) {
			helper.assertFalse(Blind.isBlindLight(other), "a light it may take: " + other);
		}
		List<BlockPos> beds = rig.crew.bases().all(g.level.dimension());
		helper.assertFalse(Blind.torchMayGo(g.level, lone, beds, rig.config), "the only light around it may go");
		helper.assertFalse(Blind.torchMayGo(g.level, byBed, beds, rig.config), "the bed's last light may go");
		helper.assertTrue(Blind.torchMayGo(g.level, seen, beds, rig.config) && !rig.outOfView(g.level, List.of(seen)), "the torch in front is not just in view");
		helper.assertTrue(rig.outOfView(g.level, List.of(byBed)) && rig.outOfView(g.level, List.of(lone)), "the lights behind are in view");

		helper.assertTrue(rig.check(Blind.Kind.TORCH, alex) == Blind.Why.NO_TORCH, "a light that may not go fits: " + Blind.torch(alex, rig.env(), null));
		helper.assertTrue(rig.fire(Blind.Kind.TORCH, alex, false, false, 1L) == FireResult.NO_SPOT, "a light that may not go went");
		helper.assertTrue(lights.stream().map(g.level::getBlockState).toList().equals(before), "a light changed");

		// A second light by the bed: the torch by it may go now, and only it. Its support stays; it is ledgered.
		g.level.setBlock(g.at(8, 2, 10), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(rig.check(Blind.Kind.TORCH, alex) == null && byBed.equals(Blind.torch(alex, rig.env(), null)), "the torch by the bed may not go: "
				+ Blind.torch(alex, rig.env(), null));
		helper.assertTrue(rig.fire(Blind.Kind.TORCH, alex, false, false, 2L) == FireResult.FIRED, "the torch by the bed did not go");
		helper.assertTrue(g.level.getBlockState(byBed).isAir() && g.level.getBlockState(support).is(Blocks.STONE), "the torch is there, or its support went");
		helper.assertTrue(TraceLedger.get(g.level.getServer()).entries().stream().anyMatch(entry -> entry.cause().equals(Blind.CAUSE_TORCH)
				&& entry.pos().dimension().equals(g.level.dimension()) && entry.pos().pos().equals(byBed)), "not ledgered with its cause");
		helper.assertTrue(rig.heard.isEmpty(), "the torch went with a sound");
		for (int i = 0; i < lights.size() - 1; i++) {
			helper.assertTrue(g.level.getBlockState(lights.get(i)).equals(before.get(i)), "another light went: " + lights.get(i));
		}

		// Turned around: the torch that was in front is behind now and goes; the ones behind before are in view.
		LadderRig.look(alex, LadderRig.WEST);
		helper.assertTrue(seen.equals(Blind.torch(alex, rig.env(), null)), "the torch behind them now: " + Blind.torch(alex, rig.env(), null));
		helper.assertTrue(rig.fire(Blind.Kind.TORCH, alex, false, false, 3L) == FireResult.FIRED && g.level.getBlockState(seen).isAir()
				&& g.level.getBlockState(seen.below()).is(Blocks.STONE), "the torch that was in front did not go");

		// The lantern with a lamp near it at last, behind them again: it goes, its floor stays.
		g.place(g.at(14, 2, 36), Blocks.GLOWSTONE.defaultBlockState());
		LadderRig.look(alex, LadderRig.EAST);
		helper.assertTrue(rig.fire(Blind.Kind.TORCH, alex, false, false, 4L) == FireResult.FIRED && g.level.getBlockState(lone).isAir()
				&& g.level.getBlockState(lone.below()).is(Blocks.STONE), "the lantern did not go");
		helper.assertTrue(rig.check(Blind.Kind.TORCH, alex) == Blind.Why.NO_TORCH && g.level.getBlockState(near).is(Blocks.TORCH)
				&& g.level.getBlockState(far).is(Blocks.TORCH) && g.level.getBlockState(natural).is(Blocks.TORCH), "a torch too near, too far or nobody's");
		rig.crew.watch.onBlindState(alex, false);
		helper.succeed();
	}

	/**
	 * The door: a closed door, trapdoor or fence gate 4 to 12 blocks away and out of view plays its own open sound, then
	 * its close 1 to 2 s later, at its centre; never an iron door, an open one, one too near or one in view. Nothing in
	 * the world changes and nothing is ledgered.
	 */
	@GameTest(maxTicks = 200)
	public void blindDoorPlaysItsOwnSoundsAndNothingMoves(GameTestHelper helper) {
		record Room(DigGround ground, BlindRig rig, ServerPlayer player, BlockPos door, Block block) {
		}
		List<Room> rooms = new ArrayList<>();
		String[] names = {"Ana", "Ben", "Cy"};
		Block[] blocks = {Blocks.OAK_DOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.CHERRY_FENCE_GATE};
		for (int i = 0; i < names.length; i++) {
			DigGround g = DigGround.of(helper, 64 + i, 24, 8, 24, Blocks.STONE);
			g.keepLoaded(true);
			g.hollow(2, 2, 2, 21, 5, 21);
			BlindRig rig = new BlindRig(helper, g, names[i]);
			ServerPlayer player = rig.stand(names[i], 14, 2, 12, LadderRig.EAST);
			BlockPos door = i == 0 ? door(g, 6, 2, 12, blocks[i]) : g.at(6, 2, 12);
			if (i > 0) {
				g.level.setBlock(door, blocks[i].defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			rooms.add(new Room(g, rig, player, door, blocks[i]));
		}
		// In Ana's room, doors it never sounds: an iron door, an open door, one too near, one in front.
		DigGround first = rooms.getFirst().ground();
		door(first, 8, 2, 8, Blocks.IRON_DOOR);
		BlockPos open = door(first, 8, 2, 16, Blocks.SPRUCE_DOOR);
		first.level.setBlock(open, first.level.getBlockState(open).setValue(DoorBlock.OPEN, true), Block.UPDATE_CLIENTS);
		first.level.setBlock(open.above(), first.level.getBlockState(open.above()).setValue(DoorBlock.OPEN, true), Block.UPDATE_CLIENTS);
		door(first, 12, 2, 13, Blocks.BIRCH_DOOR);
		door(first, 19, 2, 12, Blocks.JUNGLE_DOOR);
		helper.assertFalse(Blind.isBlindDoor(Blocks.IRON_DOOR.defaultBlockState()) || Blind.isBlindDoor(Blocks.IRON_TRAPDOOR.defaultBlockState()),
				"an iron door or trapdoor counts");
		helper.assertFalse(Blind.isBlindDoor(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)), "a door's upper half counts");
		helper.assertFalse(Blind.isBlindDoor(Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true))
				|| Blind.isBlindDoor(Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)), "an open gate or trapdoor counts");

		List<List<BlockState>> before = new ArrayList<>();
		long start = first.level.getServer().getTickCount();
		for (Room room : rooms) {
			before.add(snapshot(room.ground()));
			room.rig().blind(room.player(), 60);
			for (long seed = 0; seed < 8; seed++) {
				helper.assertTrue(room.door().equals(Blind.door(room.player(), room.rig().env(), RandomSource.create(seed))), room.player().getName().getString()
						+ " gets another door: " + Blind.door(room.player(), room.rig().env(), RandomSource.create(seed)));
			}
			helper.assertTrue(room.rig().fire(Blind.Kind.DOOR, room.player(), false, false, 9L) == FireResult.FIRED, "the door did not fire for "
					+ room.player().getName().getString());
		}
		helper.succeedWhen(() -> {
			for (int i = 0; i < rooms.size(); i++) {
				Room room = rooms.get(i);
				String who = room.player().getName().getString();
				helper.assertTrue(BlindRig.sequence(room.player()) == null, who + "'s door is still sounding");
				List<BlindRig.Heard> heard = room.rig().heard;
				helper.assertTrue(heard.size() == 2, who + " heard " + heard.size());
				SoundsOf expected = SoundsOf.of(room.block());
				helper.assertTrue(heard.get(0).sound().value() == expected.open() && heard.get(1).sound().value() == expected.close(), who + " heard "
						+ heard.get(0).sound() + " then " + heard.get(1).sound());
				long gap = heard.get(1).tick() - heard.get(0).tick();
				DigConfig config = room.rig().config;
				helper.assertTrue(heard.get(0).tick() == start && gap >= config.blindDoorGapTicksMin && gap <= config.blindDoorGapTicksMax, who + "'s gap " + gap);
				for (BlindRig.Heard sound : heard) {
					helper.assertTrue(sound.pos().equals(Vec3.atCenterOf(room.door())) && sound.source() == SoundSource.BLOCKS && sound.volume() == config.blindDoorVolume
							&& sound.pitch() >= 0.9F && sound.pitch() <= 1.0F, who + " heard " + sound);
				}
				helper.assertTrue(snapshot(room.ground()).equals(before.get(i)), "a block changed in " + who + "'s room");
				DigGround g = room.ground();
				AABB box = new AABB(Vec3.atLowerCornerOf(g.origin), Vec3.atLowerCornerOf(g.origin.offset(g.sizeX, g.sizeY, g.sizeZ)));
				helper.assertTrue(TraceLedger.get(g.level.getServer()).entries().stream()
						.noneMatch(entry -> entry.pos().dimension().equals(g.level.dimension()) && box.contains(Vec3.atCenterOf(entry.pos().pos()))),
						"something was ledgered in " + who + "'s room");
			}
			for (Room room : rooms) {
				room.rig().crew.watch.onBlindState(room.player(), false);
				room.ground().keepLoaded(false);
			}
		});
	}

	/** The open and close sounds a door, trapdoor or gate of this block has, by vanilla's own types. */
	private record SoundsOf(net.minecraft.sounds.SoundEvent open, net.minecraft.sounds.SoundEvent close) {
		static SoundsOf of(Block block) {
			if (block == Blocks.OAK_DOOR) {
				return new SoundsOf(BlockSetType.OAK.doorOpen(), BlockSetType.OAK.doorClose());
			}
			if (block == Blocks.SPRUCE_TRAPDOOR) {
				return new SoundsOf(BlockSetType.SPRUCE.trapdoorOpen(), BlockSetType.SPRUCE.trapdoorClose());
			}
			return new SoundsOf(WoodType.CHERRY.fenceGateOpen(), WoodType.CHERRY.fenceGateClose());
		}
	}

	/**
	 * Alone means alone: a friend or a spectator within 48 blocks means no blind card fits and even a forced one (or the
	 * step's fake) is skipped; 60 away changes nothing. A door already sounding stops before its close when someone comes
	 * that close, or when the player closes the menu (they are looking again).
	 */
	@GameTest(maxTicks = 300)
	public void aFriendOrSpectatorWithinFortyEightMeansNoBlindCard(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 67, 40, 10, 40, Blocks.STONE);
		g.keepLoaded(true);
		g.hollow(8, 2, 8, 37, 7, 37);
		BlindRig rig = new BlindRig(helper, g, "Alex", "Sam");
		ServerPlayer alex = rig.stand("Alex", 20, 2, 20, LadderRig.EAST);
		BlockPos torch = g.at(10, 2, 20);
		g.place(torch, Blocks.TORCH.defaultBlockState());
		g.place(g.at(10, 2, 16), Blocks.GLOWSTONE.defaultBlockState());
		door(g, 14, 2, 23, Blocks.OAK_DOOR);
		Crew.Member sam = (Crew.Member) rig.stand("Sam", 20, 2, 20 + 30, LadderRig.EAST);
		rig.blind(alex, 60);
		helper.assertTrue(rig.crew.watch.sharingWith(alex).size() == 2, "the friend 30 away does not share the moment");
		for (Blind.Kind kind : Blind.Kind.values()) {
			helper.assertTrue(rig.check(kind, alex) == Blind.Why.NOT_ALONE, kind.id + " with a friend 30 away: " + rig.check(kind, alex));
		}
		sam.setMode(GameType.SPECTATOR);
		helper.assertTrue(sam.isSpectator() && rig.crew.watch.sharingWith(alex).size() == 1, "the spectator shares the moment");
		for (Blind.Kind kind : Blind.Kind.values()) {
			helper.assertTrue(rig.check(kind, alex) == Blind.Why.NOT_ALONE, kind.id + " with a spectator 30 away: " + rig.check(kind, alex));
			for (boolean fake : List.of(false, true)) {
				helper.assertTrue(rig.fire(kind, alex, fake, true, 1L) == FireResult.SKIPPED, "a forced " + kind.id + (fake ? " (fake)" : "")
						+ " with a spectator 30 away");
			}
		}
		helper.assertTrue(rig.heard.isEmpty() && g.level.getBlockState(torch).is(Blocks.TORCH), "something happened with a spectator 30 away");
		LadderRig.move(sam, g.at(20, 2, 20 + 60));
		for (Blind.Kind kind : Blind.Kind.values()) {
			helper.assertTrue(rig.check(kind, alex) == null, kind.id + " with the spectator 60 away: " + rig.check(kind, alex));
		}
		// A door sounding: the spectator comes within 20 before its close.
		helper.assertTrue(rig.fire(Blind.Kind.DOOR, alex, false, false, 2L) == FireResult.FIRED && rig.heard.size() == 1, "the door did not open");
		BlindCues.Sequence came = BlindRig.sequence(alex);
		helper.assertTrue(came != null, "no close to come");
		helper.runAfterDelay(5, () -> LadderRig.move(sam, g.at(20, 2, 20 + 20)));
		BlindCues.Sequence[] looked = {null};
		helper.runAfterDelay(60, () -> {
			helper.assertTrue("someone came close".equals(came.stopped) && rig.heard.size() == 1, "the door closed with the spectator 20 away: "
					+ came.describe());
			LadderRig.move(sam, g.at(20, 2, 20 + 60));
			rig.heard.clear();
			helper.assertTrue(rig.fire(Blind.Kind.DOOR, alex, false, false, 3L) == FireResult.FIRED && rig.heard.size() == 1, "the door did not open again");
			looked[0] = BlindRig.sequence(alex);
		});
		helper.runAfterDelay(65, () -> rig.crew.watch.onBlindState(alex, false));
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(looked[0] != null && "they are looking again".equals(looked[0].stopped) && rig.heard.size() == 1,
					"the door closed after the menu did: " + (looked[0] == null ? "none" : looked[0].describe()));
			helper.assertTrue(SoundCues.hearers(g.level, List.of(sam), Vec3.atCenterOf(g.at(14, 2, 23)), rig.config.blindDoorVolume).isEmpty(),
					"the spectator 60 away could hear the door");
			g.keepLoaded(false);
			helper.succeed();
		});
	}

	/** {@code /a1016 dig blind}: the lines (the moment, each card's answer, the sounds, the arm) and the commands parse. */
	@GameTest
	public void blindDebugLinesAndCommands(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 68, 24, 8, 24, Blocks.STONE);
		g.hollow(2, 2, 2, 21, 5, 21);
		BlindRig rig = new BlindRig(helper, g, "Alex");
		ServerPlayer alex = rig.stand("Alex", 12, 2, 12, LadderRig.EAST);
		List<String> lines = Blind.debugLines(alex, rig.env());
		helper.assertTrue(lines.size() == 6 && lines.get(0).contains("not blind") && lines.get(1).startsWith("blind_step: no (not blind"), "lines " + lines);
		rig.blind(alex, 40);
		lines = Blind.debugLines(alex, rig.env());
		helper.assertTrue(lines.get(0).contains("blind for 2.0 s") && lines.get(1).equals("blind_step: fits now") && lines.get(2).startsWith("blind_torch: no (")
				&& lines.get(3).startsWith("blind_door: no ("), "lines " + lines);
		rig.crew.watch.onBlindState(alex, false);
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		for (String command : List.of("a1016 dig blind", "a1016 dig blind arm blind_step", "a1016 dig blind arm blind_step fake", "a1016 dig blind arm blind_door")) {
			ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, source);
			helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "does not parse: " + command);
		}
		server.getCommands().performPrefixedCommand(source, "a1016 dig blind");
		helper.succeed();
	}

	/** D-075: the blind fields are new fields without a step: a version 1 section without them reads their defaults and stays version 1. */
	@GameTest
	public void aDigSectionWithoutTheBlindFieldsReadsTheirDefaults(GameTestHelper helper) {
		JsonObject sections = JsonParser.parseString("{\"dig\": {\"version\": 1, \"ladderMinRun\": 20, \"homeRadius\": 24}}").getAsJsonObject();
		DigConfig read = ModConfig.readSection(sections, "dig", DigConfig.class, DigConfig::new);
		DigConfig defaults = new DigConfig();
		helper.assertTrue(read.version == 1 && DigConfig.CURRENT_VERSION == 1 && read.ladderMinRun == 20, "version " + read.version);
		helper.assertTrue(read.blindMinSeconds == 1.5 && read.blindNoCombatSeconds == 30 && read.blindStepBackMin == 3 && read.blindStepBackMax == 5
				&& read.blindTorchMinDistance == 6 && read.blindTorchMaxDistance == 20 && read.blindTorchOtherLightRadius == 8 && read.blindTorchBedRadius == 6
				&& read.blindDoorMinDistance == 4 && read.blindDoorMaxDistance == 12 && read.blindStepVolume == defaults.blindStepVolume
				&& read.blindDoorVolume == defaults.blindDoorVolume, "the blind fields are not their defaults");
		helper.assertFalse(read.upgrade(), "a version 1 section upgraded");
		helper.succeed();
	}

	/**
	 * R4 (v1.3 review): blind_step and its fake are "the floor's own step", but a step on natural snow cover (one snow
	 * layer, no collision) or on leaf litter plays the block under it. Vanilla 26.3's step
	 * ({@code Entity.getPrimaryStepSoundBlockPos}) takes the block above the floor when it is in
	 * {@code #inside_step_sound_blocks} (leaf litter, pink petals, glow lichen ...) or {@code #combination_step_sound_blocks}
	 * (snow, carpets ...): a player walking there hears snow or leaves, and the step behind them (and their own step, the
	 * fake) is grass.
	 */
	@GameTest
	public void r4BlindStepOnSnowOrLeafLitterSoundsLikeIt(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 90, 30, 8, 12, Blocks.STONE);
		g.hollow(2, 2, 2, 27, 5, 9);
		BlindRig rig = new BlindRig(helper, g, "Alex");
		for (Block cover : List.of(Blocks.SNOW, Blocks.LEAF_LITTER)) {
			for (int x = 2; x <= 27; x++) {
				for (int z = 2; z <= 9; z++) {
					g.level.setBlock(g.at(x, 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					g.level.setBlock(g.at(x, 2, z), cover.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			ServerPlayer alex = rig.stand("Alex", 20, 2, 5, LadderRig.EAST);
			rig.blind(alex, 60);
			SoundType own = cover.defaultBlockState().getSoundType();
			for (long seed = 1; seed <= 8; seed++) {
				List<BlindCues.Cue> steps = Blind.planSteps(alex, rig.config, RandomSource.create(seed * SPREAD));
				helper.assertTrue(!steps.isEmpty(), "no step on " + cover);
				for (BlindCues.Cue step : steps) {
					helper.assertTrue(step.sound().value() == own.getStepSound(), "a step on " + cover + " sounds like "
							+ step.sound().value().location() + ", not " + own.getStepSound().location());
				}
			}
			BlindCues.Cue fake = Blind.step(g.level, 0, alex.position(), 0.0F, true);
			helper.assertTrue(fake.sound().value() == own.getStepSound(), "the player's own step on " + cover + " (the fake) sounds like "
					+ fake.sound().value().location());
			rig.crew.watch.onBlindState(alex, false);
		}
		helper.succeed();
	}

	/** A closed door (both halves, facing east) at ground coordinates; its lower half. */
	private static BlockPos door(DigGround g, int x, int y, int z, Block block) {
		BlockPos lower = g.at(x, y, z);
		BlockState state = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
		g.level.setBlock(lower, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
		g.level.setBlock(lower.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
		return lower;
	}

	private static List<BlockState> snapshot(DigGround g) {
		List<BlockState> states = new ArrayList<>(g.sizeX * g.sizeY * g.sizeZ);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < g.sizeX; x++) {
			for (int y = 0; y < g.sizeY; y++) {
				for (int z = 0; z < g.sizeZ; z++) {
					states.add(g.level.getBlockState(pos.setWithOffset(g.origin, x, y, z)));
				}
			}
		}
		return states;
	}
}
