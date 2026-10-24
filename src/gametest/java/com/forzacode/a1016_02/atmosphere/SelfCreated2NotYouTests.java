package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.atmosphere.card.SoundOut;
import com.forzacode.a1016_02.atmosphere.sc2.s29.S29Card;
import com.forzacode.a1016_02.atmosphere.sc2.s29.Sc29Config;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.PlayerAction;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-7, feature 29 (docs/SELF_CREATED_2.md): a friend's own run of breaks (their blocks, hits and gaps) replayed from
 * inside the rock 18 to 28 from the target at a bearing at least 100 degrees from where the friend is, heard by the
 * target alone; the refusals (a spectator within 48, the friend too near, too far or in another level, an old run, a run
 * that is not mining, the dark, combat); the replay dropped when the target goes; never within 6 of the world's floor or at
 * its top. Every test builds its own rock in its own patch of the overworld far out (x from -21 000 000, z 6 400 000), its
 * chunks held while it runs, in a test environment of its own (the rock's light work holds up no other batch); mock
 * players in a private list, the card's own sinks for sounds, episodes, view and listeners.
 */
public class SelfCreated2NotYouTests {
	private static final String ENV = "a1016_02:atmosphere/sc2_notyou";
	private static final int SET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** A player only a test's own list holds online, whose game mode the test can switch. */
	static final class Mock extends ServerPlayer {
		GameType mode = GameType.SURVIVAL;

		Mock(ServerLevel level, String name) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	/** One sound played: on which tick, what, where, how loud, and who heard it. */
	record Played(int tick, SoundEvent sound, Vec3 pos, float volume, List<UUID> hearers) {
	}

	/** A test's world: a patch far out, its own players and watch, a card wired to them, a sound sink on the episode's clock. */
	static final class Rock implements PlayerWatch.Source {
		private static final AtomicInteger SLOTS = new AtomicInteger();
		static final BlockPos BASE = new BlockPos(-21_000_000, 0, 6_400_000);
		static final int SPACING = 512;
		static final int REACH = 40;

		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos column;
		final List<ServerPlayer> online = new ArrayList<>();
		final List<ServerPlayer> others = new ArrayList<>();
		final HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);
		final S29Card card = new S29Card();
		final List<Tasks.Episode> started = new ArrayList<>();
		final List<Played> log = new ArrayList<>();
		int tick;
		private final List<int[]> forced = new ArrayList<>();

		Rock(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel().getServer().overworld();
			this.column = BASE.offset(SLOTS.getAndIncrement() * SPACING, 0, 0);
			for (int cx = (column.getX() - REACH) >> 4; cx <= (column.getX() + REACH) >> 4; cx++) {
				for (int cz = (column.getZ() - REACH) >> 4; cz <= (column.getZ() + REACH) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
					forced.add(new int[] {cx, cz});
				}
			}
			card.watch = () -> watch;
			card.episodes = started::add;
			card.everyone = l -> everyone(l);
			card.sinceCombat = p -> Long.MAX_VALUE;
			card.dark = p -> true;
			card.outOfView = seenBy();
			card.sounds = new SoundOut() {
				@Override
				public List<ServerPlayer> play(ServerLevel l, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
					List<ServerPlayer> heard = SoundCues.hearers(l, everyone(l), pos, volume);
					log.add(new Played(tick, sound.value(), pos, volume, heard.stream().map(ServerPlayer::getUUID).toList()));
					return heard;
				}
			};
		}

		void release() {
			for (int[] c : forced) {
				level.setChunkForced(c[0], c[1], false);
			}
		}

		@Override
		public List<ServerPlayer> online(MinecraftServer server) {
			return List.copyOf(online);
		}

		@Override
		public HerobrineState state(MinecraftServer server) {
			return state;
		}

		@Override
		public void participation(MinecraftServer server, UUID player, boolean participating) {
		}

		List<ServerPlayer> everyone(Level l) {
			List<ServerPlayer> all = new ArrayList<>();
			for (ServerPlayer p : online) {
				if (p.level() == l) {
					all.add(p);
				}
			}
			for (ServerPlayer p : others) {
				if (p.level() == l) {
					all.add(p);
				}
			}
			return all;
		}

		BiPredicate<ServerLevel, AABB> seenBy() {
			Pacing pacing = ModConfig.pacing();
			return (l, box) -> {
				List<TraceService.Viewer> viewers = new ArrayList<>();
				for (ServerPlayer p : everyone(l)) {
					viewers.add(TraceService.Viewer.of(p, 10));
				}
				return TraceService.isOutOfView(l, box, viewers, pacing.viewNearBlocks, pacing.viewConeDegrees);
			};
		}

		/**
		 * A slab of deepslate {@code thick} high whose top is {@code top} (absolute y), out to {@code reach} round the column,
		 * and a stone pillar from it up to the target's feet at {@code feet} (so the target stands on something).
		 */
		void slab(int bottom, int top, int reach) {
			BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					for (int y = bottom; y <= top; y++) {
						level.setBlock(new BlockPos(column.getX() + dx, y, column.getZ() + dz), deepslate, SET);
					}
				}
			}
		}

		Mock player(String name, double dx, int y, double dz) {
			Mock p = new Mock(level, name);
			p.snapTo(column.getX() + 0.5 + dx, y, column.getZ() + 0.5 + dz, 0.0F, 0.0F);
			online.add(p);
			state.addKnownPlayer(p.getUUID(), name);
			return p;
		}

		/** The friend's run: stone, coal ore, stone at gaps 17 and 24, its last break {@code agoTicks} ago. */
		PlayerAction.Mining run(ServerPlayer friend, long agoTicks) {
			long now = level.getServer().getTickCount();
			long t0 = now - agoTicks - 41;
			BlockPos dug = friend.blockPosition().below();
			List<BlockState> blocks = List.of(Blocks.STONE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(), Blocks.STONE.defaultBlockState());
			long[] at = {0, 17, 41};
			for (int i = 0; i < blocks.size(); i++) {
				watch.addBreak(friend, friend.level().dimension(), dug.offset(i, 0, 0), blocks.get(i), t0 + at[i]);
			}
			return watch.recentBursts(friend).getFirst();
		}

		/** Steps an episode to its end on the sink's clock; fails past {@code max} ticks. */
		void run(Tasks.Episode episode, int max) {
			MinecraftServer server = level.getServer();
			for (tick = 0; tick < max; tick++) {
				if (!episode.tick(server)) {
					return;
				}
			}
			helper.assertTrue(false, "the replay did not end in " + max + " ticks");
		}
	}

	/** The card is the design's row, registered; the numbers are the plan's. */
	@GameTest
	public void theCardAndItsNumbersAreThePlans(GameTestHelper helper) {
		EventCard card = CardRegistry.get(S29Card.ID).orElseThrow();
		helper.assertTrue(card.tier() == Tier.AMBIENT && card.earliestStage() == Stage.TRACES && card.tags().equals(Set.of(CardTag.SOUND, CardTag.PERSONAL))
				&& card.habits().equals(Set.of(Habit.WATCHER)) && !card.hasFake(), "sc_29 is not the plan's row");
		Sc29Config c = new Sc29Config();
		helper.assertTrue(c.burstMinutes == 5 && c.distanceMin == 18 && c.distanceMax == 28 && c.bearingOff == 100 && c.friendMin == 40 && c.friendMax == 200,
				"the plan's numbers");
		helper.assertTrue(S29Card.apart(170, -170) == 20 && S29Card.apart(0, 100) == 100 && S29Card.apart(350, 10) == 20, "the angle between bearings");
		helper.assertTrue(S29Card.night(18000) && !S29Card.night(6000) && S29Card.night(13000) && !S29Card.night(23000), "night");
		helper.succeed();
	}

	/**
	 * The friend's own run, replayed: every fire that finds rock plays from a deepslate block buried on all six sides, 18 to
	 * 28 from the target, at most 14 below and 2 above, at least 100 degrees from the friend's bearing; its sounds are the
	 * friend's blocks (stone and coal ore, never the deepslate), with the friend's gaps (17 and 24 between the breaks), and
	 * only the target hears them; a third player 50 off on the far side never does.
	 */
	@GameTest(environment = ENV, maxTicks = 100)
	public void theFriendsOwnRunPlaysFromTheWrongSide(GameTestHelper helper) {
		Rock rock = new Rock(helper);
		try {
			int ground = rock.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rock.column.getX(), rock.column.getZ());
			int feet = ground + 16;
			rock.slab(feet - 14, feet - 9, 30);
			Mock target = rock.player("ben", 0, feet, 0);
			Mock friend = rock.player("ana", 60, feet, 0);
			Mock third = new Mock(rock.level, "cai");
			third.snapTo(rock.column.getX() + 0.5 - 50, feet, rock.column.getZ() + 0.5, 0.0F, 0.0F);
			rock.others.add(third);
			PlayerAction.Mining run = rock.run(friend, 60 * 20);
			helper.assertTrue(rock.card.contextFits(target, rock.level), "does not fit: " + rock.card.refusal(target, false).orElse("?"));
			List<S29Card.Beat> expected = S29Card.beats(run.breaks(), AtmosphereConfig.get());
			Vec3 at = target.position();
			double friendBearing = S29Card.bearing(at, friend.position());
			int fired = 0;
			for (long seed = 1; seed <= 16; seed++) {
				rock.started.clear();
				FireResult result = rock.card.fire(new FireContext(target, rock.level, false, false, RandomSource.create(seed)));
				if (result != FireResult.FIRED) {
					helper.assertTrue(result == FireResult.NO_SPOT, "seed " + seed + ": " + result);
					continue;
				}
				fired++;
				S29Card.Replay replay = (S29Card.Replay) rock.started.getFirst();
				BlockPos spot = BlockPos.containing(replay.pos());
				double d = Math.sqrt(spot.distSqr(target.blockPosition()));
				int dy = spot.getY() - target.blockPosition().getY();
				String where = "seed " + seed + ": " + spot.subtract(target.blockPosition()).toShortString();
				helper.assertTrue(d >= 18 && d <= 28 && dy >= -14 && dy <= 2, "not 18 to 28 off: " + where);
				helper.assertTrue(S29Card.apart(S29Card.bearing(at, replay.pos()), friendBearing) >= 100, "toward the friend: " + where);
				helper.assertTrue(WorldScan.isRock(rock.level.getBlockState(spot)) && WorldScan.buried(rock.level, spot), "not inside the rock: " + where);
				rock.log.clear();
				rock.run(replay, 200);
				helper.assertTrue(rock.log.size() == expected.size(), where + ": " + rock.log.size() + " sounds, not " + expected.size());
				for (int i = 0; i < expected.size(); i++) {
					Played played = rock.log.get(i);
					helper.assertTrue(played.tick() == expected.get(i).at() && played.sound() == expected.get(i).sound().value(), where + ": sound " + i);
					helper.assertTrue(played.hearers().equals(List.of(target.getUUID())), where + ": heard by " + played.hearers());
				}
				List<Played> breaks = rock.log.stream().filter(p -> p.sound() == SoundEvents.STONE_BREAK).toList();
				helper.assertTrue(breaks.size() == 3 && breaks.get(1).tick() - breaks.get(0).tick() == 17 && breaks.get(2).tick() - breaks.get(1).tick() == 24,
						where + ": not the friend's gaps: " + breaks);
				helper.assertTrue(rock.log.stream().noneMatch(p -> p.sound() == SoundEvents.DEEPSLATE_BREAK || p.sound() == SoundEvents.DEEPSLATE_HIT),
						where + ": the rock's own sound");
			}
			helper.assertTrue(fired >= 10, "only " + fired + " of 16 found the rock");
		} finally {
			rock.release();
		}
		helper.succeed();
	}

	/**
	 * The refusals, one by one: a spectator within 48 (alone counts them), the friend 30 off, 250 off, in the Nether, a run
	 * older than 5 minutes (forced takes it), a run of logs, not dark (forced skips it), combat; and with no friend at all.
	 */
	@GameTest(environment = ENV, maxTicks = 40)
	public void itNeedsTheTargetAloneAndAFriendFarWithARecentRun(GameTestHelper helper) {
		Rock rock = new Rock(helper);
		try {
			int ground = rock.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rock.column.getX(), rock.column.getZ());
			int feet = ground + 2;
			Mock target = rock.player("ben", 0, feet, 0);
			helper.assertTrue(rock.card.refusal(target, false).orElse("").startsWith("nobody"), "no friend: " + rock.card.refusal(target, false));
			Mock friend = rock.player("ana", 60, feet, 0);
			rock.run(friend, 20 * 60);
			helper.assertTrue(rock.card.refusal(target, false).isEmpty(), "does not fit: " + rock.card.refusal(target, false));

			Mock spectator = new Mock(rock.level, "spec");
			spectator.mode = GameType.SPECTATOR;
			spectator.snapTo(target.getX() + 30, feet, target.getZ(), 0.0F, 0.0F);
			rock.others.add(spectator);
			helper.assertTrue(rock.card.refusal(target, false).orElse("").startsWith("not alone"), "a spectator 30 off: " + rock.card.refusal(target, false));
			helper.assertTrue(rock.card.refusal(target, true).isPresent(), "forced skips being alone");
			rock.others.clear();

			friend.snapTo(target.getX() + 30, feet, target.getZ(), 0.0F, 0.0F);
			helper.assertTrue(rock.card.refusal(target, true).isPresent(), "the friend 30 off");
			friend.snapTo(target.getX() + 250, feet, target.getZ(), 0.0F, 0.0F);
			helper.assertTrue(rock.card.refusal(target, true).isPresent(), "the friend 250 off");
			friend.snapTo(target.getX() + 60, feet, target.getZ(), 0.0F, 0.0F);
			friend.setServerLevel(rock.level.getServer().getLevel(Level.NETHER));
			helper.assertTrue(rock.card.refusal(target, true).isPresent(), "the friend in the Nether");
			friend.setServerLevel(rock.level);
			helper.assertTrue(rock.card.refusal(target, false).isEmpty(), "back: " + rock.card.refusal(target, false));

			Mock old = rock.player("old", -60, feet, 0);
			rock.online.remove(friend);
			rock.run(old, 20 * 60 * 6);
			helper.assertTrue(rock.card.refusal(target, false).isPresent(), "a run 6 minutes old");
			helper.assertTrue(rock.card.refusal(target, true).isEmpty(), "forced does not take the older run");
			rock.online.remove(old);
			Mock logs = rock.player("logs", 0, feet, 60);
			long now = rock.level.getServer().getTickCount();
			for (int i = 0; i < 3; i++) {
				rock.watch.addBreak(logs, rock.level.dimension(), logs.blockPosition().offset(i, 0, 0), Blocks.OAK_LOG.defaultBlockState(), now - 200 + i * 20);
			}
			helper.assertTrue(rock.card.refusal(target, true).isPresent(), "a run of logs is no mining");

			rock.online.add(friend);
			rock.card.dark = p -> false;
			helper.assertTrue(rock.card.refusal(target, false).orElse("").contains("underground"), "on the surface by day");
			helper.assertTrue(rock.card.refusal(target, true).isEmpty(), "forced skips the dark");
			rock.card.dark = p -> true;
			rock.card.sinceCombat = p -> 100;
			helper.assertTrue(rock.card.refusal(target, false).orElse("").contains("combat"), "in combat");
			rock.card.sinceCombat = p -> Long.MAX_VALUE;
			helper.assertFalse(rock.card.fire(new FireContext(target, rock.level, true, false, RandomSource.create(1))) == FireResult.FIRED, "a fake fired");
		} finally {
			rock.release();
		}
		helper.succeed();
	}

	/** The replay ends once the target is gone: left (off the list), turned spectator or in another level; the rest is dropped. */
	@GameTest(environment = ENV, maxTicks = 40)
	public void theReplayStopsWhenTheTargetGoes(GameTestHelper helper) {
		Rock rock = new Rock(helper);
		try {
			int ground = rock.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rock.column.getX(), rock.column.getZ());
			int feet = ground + 16;
			rock.slab(feet - 14, feet - 9, 30);
			Mock target = rock.player("ben", 0, feet, 0);
			Mock friend = rock.player("ana", 60, feet, 0);
			rock.run(friend, 20);
			MinecraftServer server = rock.level.getServer();
			for (int way = 0; way < 3; way++) {
				rock.started.clear();
				FireResult result = FireResult.NO_SPOT;
				for (long seed = 1; seed < 20 && result != FireResult.FIRED; seed++) {
					result = rock.card.fire(new FireContext(target, rock.level, false, false, RandomSource.create(seed)));
				}
				helper.assertTrue(result == FireResult.FIRED, "no replay");
				Tasks.Episode replay = rock.started.getFirst();
				helper.assertTrue(replay.tick(server), "the replay ended at once");
				switch (way) {
					case 0 -> rock.online.remove(target);
					case 1 -> target.mode = GameType.SPECTATOR;
					default -> target.setServerLevel(server.getLevel(Level.NETHER));
				}
				helper.assertFalse(replay.tick(server), "the replay went on with the target gone (" + way + ")");
				switch (way) {
					case 0 -> rock.online.add(target);
					case 1 -> target.mode = GameType.SURVIVAL;
					default -> target.setServerLevel(rock.level);
				}
			}
		} finally {
			rock.release();
		}
		helper.succeed();
	}

	/**
	 * The world's ends (SC2-H): with the target 9 above the floor in a block of rock, no spot is ever within 6 of the
	 * floor; 2 under the top, no spot is at the top or past it.
	 */
	@GameTest(environment = ENV, maxTicks = 100)
	public void neverInTheFloorLayersNorAtTheTop(GameTestHelper helper) {
		Rock rock = new Rock(helper);
		try {
			ServerLevel level = rock.level;
			int floor = level.getMinY();
			int top = level.getMaxY();
			rock.slab(floor, floor + 20, 30);
			Mock target = rock.player("ben", 0, floor + 9, 0);
			level.setBlock(target.blockPosition(), Blocks.AIR.defaultBlockState(), SET);
			level.setBlock(target.blockPosition().above(), Blocks.AIR.defaultBlockState(), SET);
			Mock friend = rock.player("ana", 0, floor + 9, 80);
			int found = 0;
			for (long seed = 1; seed <= 30; seed++) {
				BlockPos spot = rock.card.spot(level, target, friend, RandomSource.create(seed));
				if (spot != null) {
					found++;
					helper.assertTrue(spot.getY() >= floor + S29Card.FLOOR_MARGIN, "a spot in the floor layers: " + spot.getY() + " (floor " + floor + ")");
				}
			}
			helper.assertTrue(found >= 5, "only " + found + " spots near the floor");
			rock.slab(top - 16, top, 30);
			target.snapTo(target.getX(), top - 2, target.getZ(), 0.0F, 0.0F);
			level.setBlock(target.blockPosition(), Blocks.AIR.defaultBlockState(), SET);
			level.setBlock(target.blockPosition().above(), Blocks.AIR.defaultBlockState(), SET);
			friend.snapTo(friend.getX(), top - 4, friend.getZ(), 0.0F, 0.0F);
			found = 0;
			for (long seed = 1; seed <= 30; seed++) {
				BlockPos spot = rock.card.spot(level, target, friend, RandomSource.create(seed));
				if (spot != null) {
					found++;
					helper.assertTrue(spot.getY() < top - 1 && level.isInsideBuildHeight(spot.above()), "a spot at the top: " + spot.getY() + " (top " + top + ")");
				}
			}
			helper.assertTrue(found >= 5, "only " + found + " spots near the top");
		} finally {
			rock.release();
		}
		helper.succeed();
	}
}
