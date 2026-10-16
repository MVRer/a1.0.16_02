package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.PlayerAction;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The echoes (D-076): your mining again from inside the rock and your door again, and chest opens preferring the
 * container you opened last. Each test feeds its own watch ({@link PlayerWatch.Source}: the live watch never records
 * mock players) with {@code addAction}, gives its own card instance a sound sink that hears by core's range rule, a view
 * check over its own players (mock players are no viewers of the live one) and its own episode list, which it steps
 * itself. {@link CardSoundGameTests} extends this.
 */
public class EchoGameTests extends RockSoundGameTests {
	private static final String YARD = "a1016_02:accident/yard";
	private static final String ROCK = "a1016_02:atmosphere/rock";
	/** The far-in-the-rock test's own environment: its rock's light work never holds up another batch. */
	private static final String ROCK_ENVIRONMENT = "a1016_02:atmosphere/rock";
	private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** A test's own online players and state; nobody's participation is reported anywhere. */
	private static final class Watched implements PlayerWatch.Source {
		final List<ServerPlayer> online = new ArrayList<>();
		final HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);

		Watched(ServerPlayer... players) {
			online.addAll(List.of(players));
		}

		@Override
		public List<ServerPlayer> online(MinecraftServer server) {
			return online;
		}

		@Override
		public HerobrineState state(MinecraftServer server) {
			return state;
		}

		@Override
		public void participation(MinecraftServer server, UUID player, boolean participating) {
		}
	}

	/** One sound played: on which tick of the episode, what, where, how, and who heard it. */
	private record Played(int tick, SoundEvent sound, Vec3 pos, float volume, float pitch, List<UUID> hearers) {
	}

	/** A sound sink on the episode's own clock that hears among the test's players by core's range rule. */
	private static final class Clocked implements SoundOut {
		final List<ServerPlayer> candidates;
		final List<Played> log = new ArrayList<>();
		int tick;

		Clocked(ServerPlayer... candidates) {
			this.candidates = List.of(candidates);
		}

		@Override
		public List<ServerPlayer> play(ServerLevel level, net.minecraft.core.Holder<SoundEvent> sound, net.minecraft.sounds.SoundSource source, Vec3 pos,
				float volume, float pitch) {
			List<ServerPlayer> hearers = SoundCues.hearers(level, candidates, pos, volume);
			log.add(new Played(tick, sound.value(), pos, volume, pitch, hearers.stream().map(ServerPlayer::getUUID).toList()));
			return hearers;
		}
	}

	private static ServerPlayer at(GameTestHelper helper, Vec3 relative, float yaw) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		move(helper, player, relative, yaw);
		return player;
	}

	/** Puts the player there, looking along {@code yaw} (0: south, +Z; 180: north): the view vector follows the head. */
	private static void move(GameTestHelper helper, ServerPlayer player, Vec3 relative, float yaw) {
		Vec3 pos = helper.absoluteVec(relative);
		player.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		player.setYHeadRot(yaw);
	}

	/** The live view rule's geometry over these players' own viewpoints (what {@code TraceService.isOutOfView} does with real players). */
	private static BiPredicate<ServerLevel, AABB> seenBy(ServerPlayer... players) {
		Pacing pacing = ModConfig.pacing();
		return (level, box) -> {
			List<TraceService.Viewer> viewers = new ArrayList<>();
			for (ServerPlayer player : players) {
				viewers.add(TraceService.Viewer.of(player, 10));
			}
			return TraceService.isOutOfView(level, box, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
		};
	}

	/** Steps an episode until it ends, with the sink's clock as the episode's tick; fails past {@code max} ticks. */
	private static void run(GameTestHelper helper, Tasks.Episode episode, Clocked out, int max) {
		MinecraftServer server = helper.getLevel().getServer();
		for (out.tick = 0; out.tick < max; out.tick++) {
			if (!episode.tick(server)) {
				return;
			}
		}
		helper.assertTrue(false, "the episode did not end in " + max + " ticks");
	}

	/** A mining run of {@code state}s (one per tick in {@code ticks}), at made-up places beside {@code near}. */
	private static PlayerAction.Mining burst(ResourceKey<Level> dimension, BlockPos near, List<BlockState> states, long... ticks) {
		List<PlayerAction.Break> breaks = new ArrayList<>();
		for (int i = 0; i < ticks.length; i++) {
			breaks.add(new PlayerAction.Break(near.offset(i, 0, 0), states.get(Math.min(i, states.size() - 1)), ticks[i]));
		}
		return new PlayerAction.Mining(dimension, breaks);
	}

	private static List<UUID> uuids(ServerPlayer... players) {
		List<UUID> list = new ArrayList<>();
		for (ServerPlayer player : players) {
			list.add(player.getUUID());
		}
		return list;
	}

	/** The cards, their numbers, the door sounds by kind, what counts as mined, the hit beat, the gates and the door's gap. */
	@GameTest
	public void theEchoCardsAreWhereD076PutsThem(GameTestHelper helper) {
		EventCard mining = CardRegistry.get(EchoMiningCard.ID).orElseThrow();
		helper.assertTrue(mining.tier() == Tier.AMBIENT && mining.earliestStage() == Stage.ALONE && mining.tags().equals(Set.of(CardTag.SOUND))
				&& mining.habits().isEmpty() && mining.hasFake(), "echo_mining is not an Alone ambient sound with a fake");
		EventCard door = CardRegistry.get(EchoDoorCard.ID).orElseThrow();
		helper.assertTrue(door.tier() == Tier.MINOR && door.earliestStage() == Stage.TRACES && door.tags().equals(Set.of(CardTag.SOUND))
				&& door.habits().isEmpty() && !door.hasFake(), "echo_door is not a Traces minor sound without a fake");
		AtmosphereConfig d = new AtmosphereConfig();
		helper.assertTrue(d.echoNoCombatSeconds == 30 && d.echoMiningMinAgeMinutes == 5 && d.echoMiningMaxAgeMinutes == 30 && d.echoMiningMinDistance == 14
				&& d.echoMiningMaxDistance == 24 && d.echoDoorMinAgeMinutes == 5 && d.echoDoorMaxAgeMinutes == 60 && d.echoDoorMinDistance == 6
				&& d.echoDoorMaxDistance == 16, "D-076's numbers");
		// Heard across the whole band: the hearing range of a break and of a hit reaches past the farthest spot.
		helper.assertTrue(Math.max(d.echoMiningBreakVolume, 1.0) * 16 > d.echoMiningMaxDistance && Math.max(d.echoMiningHitVolume, 1.0) * 16 > d.echoMiningMaxDistance
				&& d.echoMiningHitVolume <= d.echoMiningBreakVolume, "the echo's hits or breaks fall short of the band, or the hits are louder");

		BlockState oak = Blocks.OAK_DOOR.defaultBlockState();
		EchoDoorCard.DoorSounds oakSounds = EchoDoorCard.sounds(oak);
		helper.assertTrue(oakSounds != null && oakSounds.open() == BlockSetType.OAK.doorOpen() && oakSounds.close() == BlockSetType.OAK.doorClose(),
				"an oak door's own sounds: " + oakSounds);
		helper.assertTrue(EchoDoorCard.sounds(oak.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)) == null, "a door's upper half has sounds of its own");
		helper.assertTrue(EchoDoorCard.sounds(Blocks.IRON_DOOR.defaultBlockState()) == null && EchoDoorCard.sounds(Blocks.IRON_TRAPDOOR.defaultBlockState()) == null,
				"an iron door or trapdoor, which no hand opens");
		EchoDoorCard.DoorSounds trapdoor = EchoDoorCard.sounds(Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
		helper.assertTrue(trapdoor != null && trapdoor.open() == BlockSetType.SPRUCE.trapdoorOpen() && trapdoor.close() == BlockSetType.SPRUCE.trapdoorClose(),
				"a spruce trapdoor's own sounds: " + trapdoor);
		EchoDoorCard.DoorSounds bamboo = EchoDoorCard.sounds(Blocks.BAMBOO_TRAPDOOR.defaultBlockState());
		helper.assertTrue(bamboo != null && bamboo.open() == BlockSetType.BAMBOO.trapdoorOpen(), "a bamboo trapdoor's own sounds: " + bamboo);
		EchoDoorCard.DoorSounds gate = EchoDoorCard.sounds(Blocks.BIRCH_FENCE_GATE.defaultBlockState());
		helper.assertTrue(gate != null && gate.open() == WoodType.BIRCH.fenceGateOpen() && gate.close() == WoodType.BIRCH.fenceGateClose(),
				"a birch gate's own sounds: " + gate);
		helper.assertTrue(EchoDoorCard.sounds(Blocks.STONE.defaultBlockState()) == null, "stone has door sounds");

		for (Block block : List.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.COAL_ORE, Blocks.DIRT, Blocks.GRAVEL, Blocks.GRASS_BLOCK, Blocks.COBBLESTONE)) {
			helper.assertTrue(EchoMiningCard.mined(block.defaultBlockState()), block + " is not mining");
		}
		for (Block block : List.of(Blocks.OAK_LOG, Blocks.TORCH, Blocks.SHORT_GRASS, Blocks.OAK_LEAVES, Blocks.BEDROCK)) {
			helper.assertFalse(EchoMiningCard.mined(block.defaultBlockState()), block + " counts as mining");
		}
		helper.assertTrue(EchoMiningCard.hits(0) == 0 && EchoMiningCard.hits(1) == 1 && EchoMiningCard.hits(4) == 1 && EchoMiningCard.hits(5) == 2
				&& EchoMiningCard.hits(8) == 2 && EchoMiningCard.hits(15) == 4, "a hit every 4 ticks of a dig, the first as it starts");

		long noCombat = AtmosphereConfig.ticks(d.echoNoCombatSeconds);
		helper.assertTrue(Gates.echo(true, noCombat, noCombat) && Gates.echo(true, Long.MAX_VALUE, noCombat), "an echo after the combat window");
		helper.assertFalse(Gates.echo(true, noCombat - 1, noCombat), "an echo within 30 s of combat");
		helper.assertFalse(Gates.echo(false, Long.MAX_VALUE, noCombat), "an echo of nothing");

		// The gap the player left: the newest open then close, else any opposite pair close together.
		ResourceKey<Level> here = helper.getLevel().dimension();
		BlockPos pos = new BlockPos(1, 2, 3);
		BlockState open = oak.setValue(DoorBlock.OPEN, true);
		PlayerAction.DoorUse open100 = new PlayerAction.DoorUse(here, pos, open, 100);
		PlayerAction.DoorUse close130 = new PlayerAction.DoorUse(here, pos, oak, 130);
		PlayerAction.DoorUse open200 = new PlayerAction.DoorUse(here, pos, open, 200);
		helper.assertTrue(EchoDoorCard.gap(List.of(close130, open100), 100) == 30, "open then close 30 ticks apart");
		helper.assertTrue(EchoDoorCard.gap(List.of(open200, close130, open100), 100) == 30, "the open then close before a later open");
		helper.assertTrue(EchoDoorCard.gap(List.of(new PlayerAction.DoorUse(here, pos, oak, 500), open100), 100) == -1, "a close 400 ticks later is a gap");
		helper.assertTrue(EchoDoorCard.gap(List.of(new PlayerAction.DoorUse(here, pos, open, 160), close130), 100) == 30, "close then open is a gap too");
		helper.assertTrue(EchoDoorCard.gap(List.of(open100), 100) == -1, "a lone use has a gap");
		helper.succeed();
	}

	/**
	 * The replay is the burst's own: one break per recorded break with each block's own break sound, at the recorded gaps;
	 * before each, the block's own hit sound on vanilla's 4-tick beat over its dig time (the gap less vanilla's delay, the
	 * first by the tool speed the others imply), never into the break before; a pause is quiet. The fake is a burst of the
	 * same size in a rock that sounds like none of them, with another rhythm, still a burst.
	 */
	@GameTest
	public void theReplayKeepsTheBurstsRhythmAndSounds(GameTestHelper helper) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		ResourceKey<Level> here = helper.getLevel().dimension();
		List<BlockState> blocks = List.of(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
				Blocks.COAL_ORE.defaultBlockState());
		PlayerAction.Mining burst = burst(here, BlockPos.ZERO, blocks, 1000, 1014, 1025, 1045);
		int[] dig = EchoMiningCard.digTicks(burst.breaks(), cfg);
		helper.assertTrue(dig[1] == 8 && dig[2] == 5 && dig[3] == 14, "dig times from the gaps less vanilla's delay: " + java.util.Arrays.toString(dig));
		helper.assertTrue(dig[0] >= 1 && dig[0] <= 8, "the first block's dig from the implied tool speed: " + dig[0]);
		List<EchoCard.Beat> beats = EchoMiningCard.replay(burst.breaks(), cfg);
		List<Integer> breakTicks = new ArrayList<>();
		int index = 0;
		for (int i = 0; i < blocks.size(); i++) {
			SoundType type = blocks.get(i).getSoundType();
			int expectedHits = EchoMiningCard.hits(dig[i]);
			for (int k = 0; k < expectedHits; k++) {
				EchoCard.Beat hit = beats.get(index++);
				helper.assertTrue(hit.sound().value() == type.getHitSound() && hit.volume() == (float) cfg.echoMiningHitVolume
						&& hit.pitch() == type.getPitch() * 0.5F, "not block " + i + "'s own hit: " + hit);
				helper.assertTrue(breakTicks.isEmpty() || hit.at() > breakTicks.getLast(), "a hit of block " + i + " before the break before it");
			}
			EchoCard.Beat hit = beats.get(index++);
			helper.assertTrue(hit.sound().value() == type.getBreakSound() && hit.volume() == (float) cfg.echoMiningBreakVolume
					&& hit.pitch() == type.getPitch() * 0.8F, "not block " + i + "'s own break: " + hit);
			breakTicks.add(hit.at());
		}
		helper.assertTrue(index == beats.size(), "more sounds than the hits and breaks: " + beats.size());
		helper.assertTrue(breakTicks.getFirst() == dig[0], "the first break is not its dig after the first hit: " + breakTicks);
		List<Long> gaps = new ArrayList<>();
		for (int i = 1; i < breakTicks.size(); i++) {
			gaps.add((long) (breakTicks.get(i) - breakTicks.get(i - 1)));
		}
		helper.assertTrue(gaps.equals(burst.gaps()), "the breaks' gaps " + gaps + " are not the burst's " + burst.gaps());
		helper.assertTrue(beats.stream().filter(b -> b.sound().value() == SoundEvents.STONE_HIT).count() == EchoMiningCard.hits(dig[0])
				+ EchoMiningCard.hits(dig[1]) + EchoMiningCard.hits(dig[3]) && beats.stream().filter(b -> b.sound().value() == SoundEvents.DEEPSLATE_HIT).count() == 2,
				"hit counts");

		// A pause: two stones 59 ticks apart. The second is dug no longer than with a wooden pickaxe (23 ticks, 6 hits);
		// the rest of the gap is quiet.
		List<EchoCard.Beat> pause = EchoMiningCard.replay(burst(here, BlockPos.ZERO, List.of(Blocks.STONE.defaultBlockState()), 0, 59).breaks(), cfg);
		List<Integer> hitsAfterFirst = new ArrayList<>();
		int firstBreak = -1;
		for (EchoCard.Beat beat : pause) {
			if (beat.sound().value() == SoundEvents.STONE_BREAK && firstBreak < 0) {
				firstBreak = beat.at();
			} else if (firstBreak >= 0 && beat.sound().value() == SoundEvents.STONE_HIT) {
				hitsAfterFirst.add(beat.at());
			}
		}
		helper.assertTrue(hitsAfterFirst.size() == 6 && hitsAfterFirst.getFirst() - firstBreak >= 59 - 23, "a pause is hammered: " + hitsAfterFirst);

		// The fake: same size, another rock (stone, deepslate and coal ore all sound like stone or deepslate), another
		// rhythm, still a burst.
		Set<SoundType> heard = Set.of(SoundType.STONE, SoundType.DEEPSLATE);
		for (long seed = 1; seed <= 30; seed++) {
			List<PlayerAction.Break> fake = EchoMiningCard.fakeBreaks(burst, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, RandomSource.create(seed), cfg);
			PlayerAction.Mining run = new PlayerAction.Mining(here, fake);
			helper.assertTrue(fake.size() == burst.breaks().size() && run.isBurst(), "seed " + seed + ": the fake's size " + fake.size());
			helper.assertTrue(fake.stream().noneMatch(b -> heard.contains(b.state().getSoundType())) && WorldScan.isRock(fake.getFirst().state()),
					"seed " + seed + ": the fake sounds like the burst: " + fake.getFirst().state());
			double mean = run.gaps().stream().mapToLong(Long::longValue).average().orElseThrow();
			double recorded = burst.gaps().stream().mapToLong(Long::longValue).average().orElseThrow();
			helper.assertTrue(Math.abs(mean - recorded) >= 4 && run.gaps().stream().allMatch(g -> g >= 1 && g < PlayerAction.Mining.GAP_TICKS),
					"seed " + seed + ": the fake's rhythm " + run.gaps() + " is the burst's " + burst.gaps());
		}
		// The spot's own rock, when it sounds like none of the burst's blocks; with no burst, 2 to 6 breaks.
		helper.assertTrue(EchoMiningCard.fakeBlock(Blocks.TUFF.defaultBlockState(), heard).is(Blocks.TUFF), "the spot's own tuff");
		Set<Integer> sizes = new HashSet<>();
		for (long seed = 1; seed <= 40; seed++) {
			sizes.add(EchoMiningCard.fakeBreaks(null, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, RandomSource.create(seed), cfg).size());
		}
		helper.assertTrue(sizes.stream().allMatch(n -> n >= 2 && n <= 6) && sizes.size() >= 3, "fake sizes with no burst: " + sizes);
		helper.succeed();
	}

	/**
	 * echo_mining fits only with a burst of the player's own, all mined, in their level, 5 to 30 minutes of play ago, no
	 * combat in the last 30 s, and rock around; a forced fire takes their newest burst whatever its age, and with none it
	 * skips.
	 */
	@GameTest(dimension = "minecraft:the_end", structure = YARD)
	public void echoMiningWaitsForItsMoment(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Level> here = level.dimension();
		AtmosphereConfig cfg = AtmosphereConfig.get();
		long now = level.getServer().getTickCount();
		List<BlockState> stone = List.of(Blocks.STONE.defaultBlockState());
		BlockPos near = helper.absolutePos(new BlockPos(2, 1, 2));
		record Case(String what, PlayerAction action, boolean fits) {
		}
		long tenAgo = now - EchoCard.minutes(10);
		List<Case> cases = List.of(
				new Case("nothing", null, false),
				new Case("a burst 2 minutes ago", burst(here, near, stone, now - EchoCard.minutes(2), now - EchoCard.minutes(2) + 20), false),
				new Case("a burst 10 minutes ago", burst(here, near, stone, tenAgo, tenAgo + 20), true),
				new Case("a burst 40 minutes ago", burst(here, near, stone, now - EchoCard.minutes(40), now - EchoCard.minutes(40) + 20), false),
				new Case("a lone break", burst(here, near, stone, tenAgo), false),
				new Case("logs chopped", burst(here, near, List.of(Blocks.OAK_LOG.defaultBlockState()), tenAgo, tenAgo + 20), false),
				new Case("a torch in the run", burst(here, near, List.of(Blocks.STONE.defaultBlockState(), Blocks.TORCH.defaultBlockState()), tenAgo, tenAgo + 20), false),
				new Case("a burst in another level", burst(Level.OVERWORLD, near, stone, tenAgo, tenAgo + 20), false));
		for (Case c : cases) {
			ServerPlayer a = at(helper, new Vec3(12.5, 1.0, 12.5), 0.0F);
			Watched watched = new Watched(a);
			if (c.action() != null) {
				watched.watch.addAction(a, c.action());
			}
			EchoMiningCard card = new EchoMiningCard();
			card.watch = () -> watched.watch;
			card.rockAround = (l, pos) -> true;
			card.sinceCombat = p -> Long.MAX_VALUE;
			helper.assertTrue(card.contextFits(a, level) == c.fits(), c.what() + ": fits " + !c.fits());
			if (c.fits()) {
				card.sinceCombat = p -> AtmosphereConfig.ticks(cfg.echoNoCombatSeconds) - 1;
				helper.assertFalse(card.contextFits(a, level), c.what() + ": fits within 30 s of combat");
				card.sinceCombat = p -> AtmosphereConfig.ticks(cfg.echoNoCombatSeconds);
				helper.assertTrue(card.contextFits(a, level), c.what() + ": does not fit 30 s after combat");
				card.rockAround = (l, pos) -> false;
				helper.assertFalse(card.contextFits(a, level), c.what() + ": fits with no rock around");
			}
		}
		// Forced (debug): the newest burst whatever its age; none at all skips.
		ServerPlayer a = at(helper, new Vec3(12.5, 1.0, 12.5), 0.0F);
		Watched watched = new Watched(a);
		EchoMiningCard card = new EchoMiningCard();
		card.watch = () -> watched.watch;
		helper.assertTrue(card.fire(new FireContext(a, level, false, true, RandomSource.create(1))) == FireResult.SKIPPED, "a forced echo of nothing");
		PlayerAction.Mining fresh = burst(here, near, stone, now - 40, now - 20);
		watched.watch.addAction(a, fresh);
		helper.assertTrue(card.bursts(a, level, false).isEmpty() && card.bursts(a, level, true).equals(List.of(fresh)), "a forced fire's burst");
		helper.succeed();
	}

	/**
	 * Fired for A over a deepslate slab: A's stone-and-coal burst of 10 minutes ago plays again from one block of the
	 * rock, buried, 14 to 24 blocks from A, below or beside them and out of everyone's view: the burst's own sounds (not
	 * the deepslate's) at its gaps, every hit and break heard by A and by B beside A, never by C far off. The fake plays
	 * the deepslate at another rhythm. With A gone the rest is dropped; in view, no spot.
	 */
	@GameTest(environment = ROCK_ENVIRONMENT, dimension = "minecraft:the_end", structure = ROCK, maxTicks = 40, padding = 24)
	public void echoMiningReplaysTheBurstFromInsideTheRock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
		// A slab of deepslate 14 to 19 blocks under A's feet (24, 29, 24): every spot the card may find is in it.
		for (int x = 0; x < 48; x++) {
			for (int z = 0; z < 48; z++) {
				for (int y = 10; y <= 15; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), deepslate, SET_FLAGS);
				}
			}
		}
		ServerPlayer a = at(helper, new Vec3(24.5, 29.0, 24.5), 0.0F);
		ServerPlayer b = at(helper, new Vec3(24.5, 29.0, 27.5), 180.0F);
		ServerPlayer c = at(helper, new Vec3(24.5, 29.0, 24.5 + 120), 0.0F);
		Watched watched = new Watched(a, b, c);
		long now = level.getServer().getTickCount();
		long t0 = now - EchoCard.minutes(10);
		List<BlockState> blocks = List.of(Blocks.STONE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(), Blocks.STONE.defaultBlockState());
		BlockPos dug = a.blockPosition().offset(3, 0, 0);
		for (int i = 0; i < blocks.size(); i++) {
			watched.watch.addBreak(a, level.dimension(), dug.offset(0, 0, i), blocks.get(i), t0 + new long[] {0, 17, 41}[i]);
		}
		PlayerAction.Mining burst = watched.watch.recentBursts(a).getFirst();
		helper.assertTrue(burst.gaps().equals(List.of(17L, 24L)), "the burst as recorded: " + burst.gaps());

		EchoMiningCard card = new EchoMiningCard();
		List<Tasks.Episode> started = new ArrayList<>();
		Clocked out = new Clocked(a, b, c);
		card.watch = () -> watched.watch;
		card.episodes = started::add;
		card.sounds = out;
		card.outOfView = seenBy(a, b, c);
		card.sinceCombat = p -> Long.MAX_VALUE;
		helper.assertTrue(card.contextFits(a, level), "no echo over the slab");
		AtmosphereConfig cfg = AtmosphereConfig.get();
		List<EchoCard.Beat> expected = EchoMiningCard.replay(burst.breaks(), cfg);
		BlockPos feet = a.blockPosition();
		int fired = 0;
		for (long seed = 1; seed <= 20; seed++) {
			FireResult result = card.fire(new FireContext(a, level, false, false, RandomSource.create(seed)));
			if (result != FireResult.FIRED) {
				helper.assertTrue(result == FireResult.NO_SPOT, "seed " + seed + ": " + result);
				continue;
			}
			fired++;
			helper.assertTrue(started.size() == fired && started.getLast() instanceof EchoCard.Replay, "seed " + seed + ": no replay started");
			EchoCard.Replay replay = (EchoCard.Replay) started.getLast();
			BlockPos spot = BlockPos.containing(replay.pos());
			double distance = Math.sqrt(spot.distSqr(feet));
			int dy = spot.getY() - feet.getY();
			String where = "seed " + seed + ": " + spot.subtract(feet).toShortString() + String.format(java.util.Locale.ROOT, " (%.1f blocks)", distance);
			helper.assertTrue(distance >= cfg.echoMiningMinDistance && distance <= cfg.echoMiningMaxDistance, "not 14 to 24 blocks off: " + where);
			helper.assertTrue(dy >= -cfg.echoMiningMaxBelow && dy <= cfg.echoMiningMaxAbove, "overhead or too deep: " + where);
			helper.assertTrue(WorldScan.isRock(level.getBlockState(spot)) && WorldScan.buried(level, spot) && replay.pos().equals(Vec3.atCenterOf(spot)),
					"not from inside the rock: " + where);
			helper.assertTrue(seenBy(a, b, c).test(level, new AABB(spot)), "the spot is in view: " + where);
			out.log.clear();
			run(helper, replay, out, 400);
			helper.assertTrue(out.log.size() == expected.size(), where + ": " + out.log.size() + " sounds, not " + expected.size());
			for (int i = 0; i < expected.size(); i++) {
				Played played = out.log.get(i);
				EchoCard.Beat beat = expected.get(i);
				helper.assertTrue(played.tick() == beat.at() && played.sound() == beat.sound().value() && played.volume() == beat.volume()
						&& played.pos().equals(replay.pos()), where + ": sound " + i + " " + played + " is not " + beat);
				helper.assertTrue(played.hearers().equals(uuids(a, b)), where + ": " + played.sound() + " heard by " + played.hearers() + ", not A and B");
			}
			List<Played> breaks = out.log.stream().filter(p -> p.sound() == SoundEvents.STONE_BREAK).toList();
			helper.assertTrue(breaks.size() == 3 && breaks.get(1).tick() - breaks.get(0).tick() == 17 && breaks.get(2).tick() - breaks.get(1).tick() == 24,
					where + ": the breaks are not at the burst's gaps: " + breaks);
			helper.assertTrue(out.log.stream().noneMatch(p -> p.sound() == SoundEvents.DEEPSLATE_BREAK || p.sound() == SoundEvents.DEEPSLATE_HIT),
					where + ": the rock's sound, not the burst's");
		}
		helper.assertTrue(fired >= 12, "only " + fired + " of 20 fires found the rock");

		// The fake: the deepslate around, at another rhythm.
		started.clear();
		helper.assertTrue(card.fire(new FireContext(a, level, true, false, RandomSource.create(7))) == FireResult.FIRED, "the fake found no rock");
		out.log.clear();
		run(helper, started.getFirst(), out, 400);
		List<Played> fakeBreaks = out.log.stream().filter(p -> p.sound() == SoundEvents.DEEPSLATE_BREAK).toList();
		helper.assertTrue(fakeBreaks.size() == 3 && out.log.stream().noneMatch(p -> p.sound() == SoundEvents.STONE_BREAK || p.sound() == SoundEvents.STONE_HIT),
				"the fake is not three deepslate breaks: " + out.log);
		List<Integer> fakeGaps = List.of(fakeBreaks.get(1).tick() - fakeBreaks.get(0).tick(), fakeBreaks.get(2).tick() - fakeBreaks.get(1).tick());
		helper.assertFalse(fakeGaps.equals(List.of(17, 24)), "the fake keeps the burst's rhythm");

		// A gone (left): the rest of the replay is dropped.
		started.clear();
		helper.assertTrue(card.fire(new FireContext(a, level, false, false, RandomSource.create(3))) == FireResult.FIRED, "no echo to cut short");
		out.log.clear();
		out.tick = 0;
		Tasks.Episode replay = started.getFirst();
		helper.assertTrue(replay.tick(level.getServer()), "the replay ended at once");
		watched.online.remove(a);
		int before = out.log.size();
		out.tick = 1;
		helper.assertFalse(replay.tick(level.getServer()), "the replay goes on with A gone");
		helper.assertTrue(out.log.size() == before && before < expected.size(), "sounds after A left");
		watched.online.addFirst(a);

		// In everyone's view: no spot.
		card.outOfView = (l, box) -> false;
		helper.assertTrue(card.fire(new FireContext(a, level, false, false, RandomSource.create(1))) == FireResult.NO_SPOT, "an echo from a seen spot");
		helper.succeed();
	}

	/**
	 * echo_door, fired for A 10 blocks from the oak door A opened and closed 23 ticks apart 10 minutes ago: with the door
	 * behind A, its own open sound, then its close 23 ticks later, at the door, heard by A and B beside A, not by C; the
	 * door never changes. Facing it: no spot. Too near, too far, too new, too old or after combat: no fit. A door left open
	 * closes then opens; with no gap of the player's, a plausible one; a trapdoor and a gate sound like themselves.
	 */
	@GameTest(dimension = "minecraft:the_end", structure = YARD)
	public void echoDoorPlaysTheDoorsOwnSoundsAndNeverMovesIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		BlockPos lower = helper.absolutePos(new BlockPos(12, 1, 12));
		BlockState closed = Blocks.OAK_DOOR.defaultBlockState();
		BlockState upperState = closed.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
		level.setBlock(lower, closed, SET_FLAGS);
		level.setBlock(lower.above(), upperState, SET_FLAGS);
		// A 10 blocks north of the door, facing north (away from it); B beside A; C far off.
		ServerPlayer a = at(helper, new Vec3(12.5, 1.0, 2.5), 180.0F);
		ServerPlayer b = at(helper, new Vec3(14.5, 1.0, 2.5), 180.0F);
		ServerPlayer c = at(helper, new Vec3(12.5, 1.0, 92.5), 0.0F);
		Watched watched = new Watched(a, b, c);
		long now = level.getServer().getTickCount();
		long t0 = now - EchoCard.minutes(10);
		watched.watch.addAction(a, new PlayerAction.DoorUse(level.dimension(), lower, closed.setValue(DoorBlock.OPEN, true), t0));
		watched.watch.addAction(a, new PlayerAction.DoorUse(level.dimension(), lower, closed, t0 + 23));

		EchoDoorCard card = new EchoDoorCard();
		List<Tasks.Episode> started = new ArrayList<>();
		Clocked out = new Clocked(a, b, c);
		card.watch = () -> watched.watch;
		card.episodes = started::add;
		card.sounds = out;
		card.outOfView = seenBy(a, b, c);
		card.sinceCombat = p -> Long.MAX_VALUE;
		helper.assertTrue(card.contextFits(a, level), "no door echo 10 blocks from a door used 10 minutes ago");
		helper.assertTrue(card.fire(new FireContext(a, level, false, false, RandomSource.create(1))) == FireResult.FIRED, "the door echo did not fire");
		run(helper, started.getFirst(), out, 200);
		Vec3 door = Vec3.atCenterOf(lower);
		helper.assertTrue(out.log.size() == 2, "not two sounds: " + out.log);
		Played open = out.log.get(0);
		Played close = out.log.get(1);
		helper.assertTrue(open.tick() == 0 && open.sound() == BlockSetType.OAK.doorOpen() && open.pos().equals(door) && open.volume() == 1.0F,
				"not the oak door's own open at the door: " + open);
		helper.assertTrue(close.tick() == 23 && close.sound() == BlockSetType.OAK.doorClose() && close.pos().equals(door),
				"not the oak door's own close 23 ticks later: " + close);
		helper.assertTrue(open.hearers().equals(uuids(a, b)) && close.hearers().equals(uuids(a, b)), "heard by " + open.hearers() + ", not A and B");
		helper.assertTrue(level.getBlockState(lower) == closed && level.getBlockState(lower.above()) == upperState, "the door moved");

		// Facing the door: in view, no spot (the moment still fits: the director keeps the card).
		move(helper, a, new Vec3(12.5, 1.0, 2.5), 0.0F);
		helper.assertTrue(card.contextFits(a, level) && card.fire(new FireContext(a, level, false, false, RandomSource.create(1))) == FireResult.NO_SPOT,
				"an echo from a door A looks at");
		move(helper, a, new Vec3(12.5, 1.0, 2.5), 180.0F);
		// The band: 4 and 20 blocks off do not fit.
		move(helper, a, new Vec3(12.5, 1.0, 8.5), 180.0F);
		helper.assertFalse(card.contextFits(a, level), "fits 4 blocks from the door");
		move(helper, a, new Vec3(12.5, 1.0, -7.5), 180.0F);
		helper.assertFalse(card.contextFits(a, level), "fits 20 blocks from the door");
		move(helper, a, new Vec3(12.5, 1.0, 2.5), 180.0F);
		// Combat within 30 s.
		card.sinceCombat = p -> 100;
		helper.assertFalse(card.contextFits(a, level), "fits right after combat");
		card.sinceCombat = p -> Long.MAX_VALUE;
		// Too new, too old.
		for (int minutesAgo : new int[] {2, 70}) {
			Watched other = new Watched(a);
			other.watch.addAction(a, new PlayerAction.DoorUse(level.dimension(), lower, closed, now - EchoCard.minutes(minutesAgo)));
			card.watch = () -> other.watch;
			helper.assertFalse(card.contextFits(a, level), "fits a door used " + minutesAgo + " minutes ago");
		}
		card.watch = () -> watched.watch;

		// The door left open: it closes, then opens again, and stays open. No gap of the player's: a plausible one.
		BlockState openLower = closed.setValue(DoorBlock.OPEN, true);
		BlockState openUpper = upperState.setValue(DoorBlock.OPEN, true);
		level.setBlock(lower, openLower, SET_FLAGS);
		level.setBlock(lower.above(), openUpper, SET_FLAGS);
		Watched lone = new Watched(a, b, c);
		lone.watch.addAction(a, new PlayerAction.DoorUse(level.dimension(), lower, openLower, t0));
		card.watch = () -> lone.watch;
		started.clear();
		out.log.clear();
		helper.assertTrue(card.fire(new FireContext(a, level, false, false, RandomSource.create(5))) == FireResult.FIRED, "no echo of the open door");
		run(helper, started.getFirst(), out, 200);
		AtmosphereConfig cfg = AtmosphereConfig.get();
		helper.assertTrue(out.log.size() == 2 && out.log.get(0).sound() == BlockSetType.OAK.doorClose() && out.log.get(1).sound() == BlockSetType.OAK.doorOpen(),
				"the open door does not close then open: " + out.log);
		int gap = out.log.get(1).tick() - out.log.get(0).tick();
		helper.assertTrue(gap >= cfg.echoDoorGapMinTicks && gap <= cfg.echoDoorGapMaxTicks, "not a plausible gap: " + gap);
		helper.assertTrue(level.getBlockState(lower) == openLower && level.getBlockState(lower.above()) == openUpper, "the open door moved");
		// Another block there now: nothing to echo.
		level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), SET_FLAGS);
		level.setBlock(lower, Blocks.STONE.defaultBlockState(), SET_FLAGS);
		helper.assertFalse(card.contextFits(a, level), "an echo of a door that is gone");

		// A trapdoor and a gate sound like themselves, at the gap given.
		RandomSource random = RandomSource.create(9);
		List<EchoCard.Beat> trapdoor = EchoDoorCard.beats(new EchoDoorCard.Candidate(lower, Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), 17), random, cfg);
		helper.assertTrue(trapdoor.size() == 2 && trapdoor.get(0).sound().value() == BlockSetType.SPRUCE.trapdoorOpen() && trapdoor.get(1).at() == 17
				&& trapdoor.get(1).sound().value() == BlockSetType.SPRUCE.trapdoorClose(), "the spruce trapdoor: " + trapdoor);
		List<EchoCard.Beat> gate = EchoDoorCard.beats(new EchoDoorCard.Candidate(lower, Blocks.BIRCH_FENCE_GATE.defaultBlockState(), 30), random, cfg);
		helper.assertTrue(gate.size() == 2 && gate.get(0).sound().value() == WoodType.BIRCH.fenceGateOpen() && gate.get(1).at() == 30
				&& gate.get(1).sound().value() == WoodType.BIRCH.fenceGateClose(), "the birch gate: " + gate);
		List<EchoCard.Beat> openGate = EchoDoorCard.beats(new EchoDoorCard.Candidate(lower,
				Blocks.BIRCH_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true), 30), random, cfg);
		helper.assertTrue(openGate.get(0).sound().value() == WoodType.BIRCH.fenceGateClose(), "an open gate opens first");
		helper.succeed();
	}

	/**
	 * chest_opens prefers the container A opened last when it is a chest in range and out of sight; it falls back as
	 * before (any of them, here none placed by a player) when that chest is in view, when the last container was a
	 * barrel, or when it was in another level.
	 */
	@GameTest(dimension = "minecraft:the_end", structure = YARD, maxTicks = 100)
	public void chestOpensPrefersTheLastContainer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		helper.setBlock(6, 1, 12, Blocks.CHEST);
		helper.setBlock(18, 1, 12, Blocks.CHEST);
		helper.setBlock(12, 1, 18, Blocks.BARREL);
		BlockPos first = helper.absolutePos(new BlockPos(6, 1, 12));
		BlockPos last = helper.absolutePos(new BlockPos(18, 1, 12));
		BlockPos barrel = helper.absolutePos(new BlockPos(12, 1, 18));
		ServerPlayer a = at(helper, new Vec3(12.5, 1.0, 12.5), 0.0F);
		long now = level.getServer().getTickCount();
		record Case(String what, List<PlayerAction> actions, BiPredicate<ServerLevel, AABB> view, Set<BlockPos> chosen) {
		}
		BiPredicate<ServerLevel, AABB> unseen = (l, box) -> true;
		BiPredicate<ServerLevel, AABB> lastSeen = (l, box) -> !box.contains(Vec3.atCenterOf(last));
		PlayerAction.ContainerOpen openedLast = new PlayerAction.ContainerOpen(level.dimension(), last, level.getBlockState(last), now - 200);
		List<Case> cases = List.of(
				new Case("the last chest", List.of(openedLast), unseen, Set.of(last)),
				new Case("the last chest in view", List.of(openedLast), lastSeen, Set.of(first)),
				new Case("a barrel after the chest", List.of(openedLast,
						new PlayerAction.ContainerOpen(level.dimension(), barrel, level.getBlockState(barrel), now - 100)), unseen, Set.of(first, last)),
				new Case("the last chest in another level", List.of(new PlayerAction.ContainerOpen(Level.OVERWORLD, last, level.getBlockState(last), now - 100)),
						unseen, Set.of(first, last)),
				new Case("nothing opened", List.of(), unseen, Set.of(first, last)));
		for (Case c : cases) {
			Watched watched = new Watched(a);
			c.actions().forEach(action -> watched.watch.addAction(a, action));
			ChestOpensCard card = new ChestOpensCard();
			List<CardSoundGameTests.Heard> log = new ArrayList<>();
			card.sounds = CardSoundGameTests.recorder(log, List.of(a));
			card.watch = () -> watched.watch;
			card.outOfView = c.view();
			Set<BlockPos> chosen = new HashSet<>();
			// One random for every fire: the first draw of fresh randoms with neighbouring seeds hardly differs.
			RandomSource random = RandomSource.create(42);
			for (int fire = 0; fire < 24; fire++) {
				log.clear();
				helper.assertTrue(card.fire(new FireContext(a, level, true, true, random)) == FireResult.FIRED, c.what() + ": did not fire");
				chosen.add(BlockPos.containing(log.getFirst().pos()));
			}
			helper.assertTrue(chosen.equals(c.chosen()), c.what() + ": chose " + chosen + ", not " + c.chosen());
		}
		helper.succeed();
	}
}
