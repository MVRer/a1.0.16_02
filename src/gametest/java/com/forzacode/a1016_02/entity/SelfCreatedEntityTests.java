package com.forzacode.a1016_02.entity;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.entity.sc.HisView;
import com.forzacode.a1016_02.entity.sc.HisViewConfig;
import com.forzacode.a1016_02.entity.sc.HisViewPayload;
import com.forzacode.a1016_02.entity.sc.HisViewRules;
import com.forzacode.a1016_02.entity.sc.Nameless;
import com.forzacode.a1016_02.entity.sc.NamelessConfig;
import com.forzacode.a1016_02.entity.sc.NamelessPayload;
import com.forzacode.a1016_02.entity.sc.NamelessRules;
import com.forzacode.a1016_02.entity.sc.ScGates;
import com.google.gson.JsonObject;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The self-created layer's entity parts (docs/SELF_CREATED.md): the figure's gone event with where he stood and his way out
 * (10's call), the friend without a name (02: the card's fit, the arms, the payload, the client's pure rule) and his view as
 * the world's picture (03: the arm rules, the report, the files and their putting back, the camera math, the crop, the PNG).
 * Each arm test makes its own {@link Nameless} or {@link HisView} on its own hooks, so nothing here reads or changes the
 * shared test server's story; the client's frames are the dev client's check list.
 */
public class SelfCreatedEntityTests {
	private static final String YARD = "a1016_02:accident/yard";

	/** One FIGURE_LEFT as a listener saw it. */
	private record Left(Vec3 stoodAt, float yaw, List<Vec3> path, String variant, @Nullable UUID appearedFor, boolean seen) {
	}

	/** The recorder, registered once for the run; each test reads only its own figures' events (by the UUID they appeared for). */
	private static final class Listen {
		static final List<Left> LEFT = Collections.synchronizedList(new ArrayList<>());

		static {
			HerobrineEvents.FIGURE_LEFT.register((level, stoodAt, yaw, path, variant, appearedFor, seen) -> {
				if (appearedFor != null) {
					LEFT.add(new Left(stoodAt, yaw, path, variant, appearedFor, seen));
				}
			});
		}

		/** Registers the recorder (class initialisation) before a test's figure can go. */
		static void start() {
		}

		static List<Left> of(UUID appearedFor) {
			synchronized (LEFT) {
				return LEFT.stream().filter(l -> appearedFor.equals(l.appearedFor())).toList();
			}
		}
	}

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y < 5; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		try {
			codec.encode(buf, value);
			return codec.decode(buf);
		} finally {
			buf.release();
		}
	}

	// --- 10: FIGURE_LEFT from the figure ---

	/**
	 * A ridge figure seen by A, then running off: once he is gone (out of A's view), FIGURE_LEFT fires once with his spawn
	 * feet and facing as where he stood, his way out sampled every 2 blocks from there (oldest first, at most 24), "ridge",
	 * whom he appeared for and seen.
	 */
	@GameTest(structure = YARD, maxTicks = 400)
	public void aFigureGoneFiresFigureLeftOnceWithWhereHeStoodAndHisWayOut(GameTestHelper helper) {
		Listen.start();
		floor(helper, 24);
		Vec3 himRel = new Vec3(6.5, 1.0, 12.5);
		Vec3 himFeet = helper.absoluteVec(himRel);
		Vec3 aFeet = himFeet.add(-4.0, 0.0, 0.0);
		ServerPlayer a = TwoPlayerFigureGameTests.player(helper, GameType.SURVIVAL, aFeet, HimEntity.yawToward(aFeet, himFeet) + 25.0F);
		HimEntity him = TwoPlayerFigureGameTests.watchedBy(helper, Variant.RIDGE, himRel, 90.0F, new CopyOnWriteArrayList<>(List.of(a)), true);
		him.setSpawnDistance(40.0);
		him.appearFor(a.getUUID());
		helper.startSequence()
				.thenExecuteAfter(10, () -> {
					helper.assertTrue(him.everSeen() && !him.isRemoved(), "not seen, or gone: " + him.goneWhy());
					helper.assertTrue(Listen.of(a.getUUID()).isEmpty(), "announced while still out");
					him.walkAway(Variant.Gait.RUN);
				})
				.thenExecuteAfter(60, () -> {
					helper.assertTrue(him.wayOut().size() >= 2, "he left no way out: " + him.wayOut() + " at " + him.position());
					TwoPlayerFigureGameTests.look(a, aFeet, HimEntity.yawToward(aFeet, himFeet) + 180.0F); // A turns away
				})
				.thenWaitUntil(() -> helper.assertTrue(him.isRemoved(), "still out: " + him.phase()))
				.thenExecute(() -> {
					List<Left> events = Listen.of(a.getUUID());
					helper.assertTrue(events.size() == 1, "announced " + events.size() + " times");
					Left left = events.getFirst();
					helper.assertTrue(left.stoodAt().distanceTo(himFeet) < 0.01, "stood at " + left.stoodAt() + ", spawned at " + himFeet);
					helper.assertTrue(Math.abs(left.yaw() - 90.0F) < 1.0E-3, "facing " + left.yaw());
					helper.assertTrue("ridge".equals(left.variant()) && left.seen(), "variant " + left.variant() + ", seen " + left.seen());
					List<Vec3> path = left.path();
					helper.assertTrue(!path.isEmpty() && path.size() <= HerobrineEvents.FIGURE_PATH_MAX, "path " + path);
					Vec3 last = left.stoodAt();
					for (Vec3 sample : path) {
						helper.assertTrue(SpotFinder.horizontal(sample, last) >= HimEntity.WAY_OUT_STEP - 1.0E-6, "samples closer than 2 blocks: " + path);
						last = sample;
					}
					helper.assertTrue(SpotFinder.horizontal(path.getLast(), himFeet) > SpotFinder.horizontal(path.getFirst(), himFeet), "not his way out: " + path);
					helper.assertTrue(him.leftAnnounced(), "the figure does not know it was announced");
				})
				.thenSucceed();
	}

	/** A figure that never moved and is swept (frozen, out of view) is announced once: where he stood, no way out, unseen. */
	@GameTest
	public void aSweptFigureIsAnnouncedOnceWithNoWayOut(GameTestHelper helper) {
		Listen.start();
		floor(helper, 8);
		HimEntity him = RushAndDimensionGameTests.watchedFigure(helper, Variant.CLOSE, new Vec3(3.5, 1.0, 3.5), 45.0F, List.of());
		UUID nobody = UUID.randomUUID();
		him.appearFor(nobody);
		helper.runAfterDelay(4, () -> {
			him.sweptAway(helper.getLevel());
			him.sweptAway(helper.getLevel());
			List<Left> events = Listen.of(nobody);
			helper.assertTrue(events.size() == 1, "announced " + events.size() + " times");
			Left left = events.getFirst();
			helper.assertTrue(left.path().isEmpty() && !left.seen() && "close".equals(left.variant()), "announced " + left);
			helper.assertTrue(left.stoodAt().distanceTo(helper.absoluteVec(new Vec3(3.5, 1.0, 3.5))) < 0.01 && Math.abs(left.yaw() - 45.0F) < 1.0E-3,
					"stood at " + left.stoodAt() + " facing " + left.yaw());
			helper.assertTrue(him.isRemoved(), "not removed");
			helper.succeed();
		});
	}

	/** A figure that goes under is announced with no way out (he never walked off), seen by the watcher. */
	@GameTest(skyAccess = true, maxTicks = 300, padding = 8)
	public void aFigureThatGoesUnderIsAnnouncedWithNoWayOut(GameTestHelper helper) {
		Listen.start();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = 0; y <= 6; y++) {
					Block block = y <= 2 ? Blocks.STONE : y <= 5 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
					helper.setBlock(x, y, z, block);
				}
			}
		}
		ServerPlayer watcher = RushAndDimensionGameTests.mockPlayer(helper, helper.absoluteVec(new Vec3(3.5, 7.0, 10.5)), 180.0F, 20.0F);
		HimEntity him = RushAndDimensionGameTests.watchedFigure(helper, Variant.RIDGE, new Vec3(3.5, 7.0, 3.5), 0.0F, List.of(watcher));
		him.appearFor(watcher.getUUID());
		helper.runAfterDelay(3, () -> {
			Optional<String> refusal = him.forceGoUnder();
			helper.assertTrue(refusal.isEmpty(), "he would not go under: " + refusal.orElse(""));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(him.isRemoved(), "still out: " + him.phase());
			List<Left> events = Listen.of(watcher.getUUID());
			helper.assertTrue(events.size() == 1, "announced " + events.size() + " times");
			helper.assertTrue(events.getFirst().path().isEmpty() && events.getFirst().seen(), "announced " + events.getFirst());
		});
	}

	// --- 02: nameless ---

	/** Hooks for a test's own arms: who is online, the clock, the gates, the fog, and every payload sent. */
	private static final class NameHooks implements Nameless.Hooks {
		final Map<UUID, ServerPlayer> online = new HashMap<>();
		final List<ServerPlayer> others = new ArrayList<>();
		final List<NamelessPayload> sent = new ArrayList<>();
		long play = 1000L;
		Optional<String> story = Optional.empty();
		Optional<String> common = Optional.empty();
		int sky = 15;
		double fog = 0.5;
		boolean canSend = true;

		NameHooks with(ServerPlayer... players) {
			for (ServerPlayer player : players) {
				online.put(player.getUUID(), player);
			}
			return this;
		}

		@Override
		public long playTicks(MinecraftServer server, UUID player) {
			return play;
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(online.get(player));
		}

		@Override
		public boolean send(ServerPlayer player, NamelessPayload payload) {
			if (!canSend) {
				return false;
			}
			sent.add(payload);
			return true;
		}

		@Override
		public Optional<String> storyRefusal(MinecraftServer server) {
			return story;
		}

		@Override
		public Optional<String> commonRefusal(ServerPlayer target) {
			return common;
		}

		@Override
		public List<ServerPlayer> others(ServerPlayer target) {
			return others;
		}

		@Override
		public int skyLight(ServerPlayer target) {
			return sky;
		}

		@Override
		public double fogAmount(ServerPlayer target) {
			return fog;
		}
	}

	/** The card fits only with another participant 48 to 64 blocks off, the target outdoors, a fog on; never past the gates. */
	@GameTest
	public void namelessFitsOnlyWithAFriend48To64InFogOutdoors(GameTestHelper helper) {
		NamelessConfig config = new NamelessConfig();
		helper.assertTrue(NamelessRules.fit(List.of(50.0), 15, 0.5, config).isEmpty(), "a friend at 50 in fog outdoors");
		helper.assertTrue(NamelessRules.fit(List.of(48.0), 10, 0.1, config).isEmpty() && NamelessRules.fit(List.of(64.0), 10, 0.1, config).isEmpty(),
				"the band's ends and the thresholds");
		helper.assertTrue(NamelessRules.fit(List.of(), 15, 0.5, config).isPresent(), "alone");
		helper.assertTrue(NamelessRules.fit(List.of(30.0), 15, 0.5, config).isPresent(), "a friend inside the shared radius");
		helper.assertTrue(NamelessRules.fit(List.of(70.0), 15, 0.5, config).isPresent(), "a friend past 64");
		helper.assertTrue(NamelessRules.fit(List.of(30.0, 55.0), 15, 0.5, config).isEmpty(), "one friend in the band is enough");
		helper.assertTrue(NamelessRules.fit(List.of(50.0), 9, 0.5, config).isPresent(), "indoors");
		helper.assertTrue(NamelessRules.fit(List.of(50.0), 15, 0.09, config).isPresent(), "no fog");
		helper.assertTrue(NamelessRules.fit(List.of(50.0), 15, Double.NaN, config).isPresent(), "an unknown fog");

		// The live shape with two real players: B 55 blocks east of A.
		ServerPlayer a = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer b = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		a.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)), 0.0F, 0.0F);
		b.snapTo(helper.absoluteVec(new Vec3(56.5, 1.0, 1.5)), 0.0F, 0.0F);
		NameHooks hooks = new NameHooks().with(a, b);
		hooks.others.add(b);
		Nameless nameless = new Nameless(hooks);
		helper.assertTrue(nameless.fit(a).isEmpty(), "B 55 off: " + nameless.fit(a));
		b.snapTo(helper.absoluteVec(new Vec3(31.5, 1.0, 1.5)), 0.0F, 0.0F);
		helper.assertTrue(nameless.fit(a).isPresent(), "B 30 off fits");
		b.snapTo(helper.absoluteVec(new Vec3(56.5, 1.0, 1.5)), 0.0F, 0.0F);
		hooks.common = Optional.of("the story holds it");
		helper.assertTrue(nameless.fit(a).isPresent(), "the common gates do not hold it");
		helper.succeed();
	}

	/** The client's rule: 24 to 64 blocks, past half the way through the fog, still 1.5 s; armed; any change and the name is back. */
	@GameTest
	public void namelessHidesOnlyStillFriendsInTheBandPastTheFogShare(GameTestHelper helper) {
		NamelessConfig config = new NamelessConfig();
		long still = config.stillTicks();
		helper.assertTrue(still == 30L, "1.5 s is " + still + " ticks");
		// A fog from 0 to 75 (a fog night): past 37.5, inside 24 to 64.
		helper.assertTrue(NamelessRules.hides(40.0, 0.0, 75.0, still, true, config), "40 blocks into a 75-block fog, still");
		helper.assertFalse(NamelessRules.hides(40.0, 0.0, 75.0, still, false, config), "not armed");
		helper.assertFalse(NamelessRules.hides(40.0, 0.0, 75.0, still - 1, true, config), "still for less than 1.5 s");
		helper.assertFalse(NamelessRules.hides(30.0, 0.0, 75.0, still, true, config), "before half the fog");
		helper.assertFalse(NamelessRules.hides(20.0, 0.0, 30.0, still, true, config), "inside 24");
		helper.assertFalse(NamelessRules.hides(66.0, 0.0, 75.0, still, true, config), "past 64");
		helper.assertTrue(NamelessRules.hides(64.0, 0.0, 75.0, still, true, config) && NamelessRules.hides(24.0, 0.0, 30.0, still, true, config),
				"the band's ends");
		// The fog share counts from the visible fog start: a fog from 40 to 80 hides from 60 on.
		helper.assertFalse(NamelessRules.hides(55.0, 40.0, 80.0, still, true, config), "before half of a fog that starts at 40");
		helper.assertTrue(NamelessRules.hides(61.0, 40.0, 80.0, still, true, config), "past half of a fog that starts at 40");
		// A clear night at 16 chunks (fog end ~142): half the fog is past 64, so nothing hides.
		helper.assertFalse(NamelessRules.hides(64.0, 0.0, 142.0, still, true, config), "a clear night hides a name");
		helper.assertFalse(NamelessRules.hides(40.0, Double.NaN, Double.NaN, still, true, config), "an unknown fog hides a name");
		helper.assertTrue(NamelessRules.moved(Vec3.ZERO, new Vec3(0.1, 0.0, 0.0)) && !NamelessRules.moved(Vec3.ZERO, new Vec3(0.09, 0.0, 0.0)),
				"moved means 0.1 blocks");
		helper.succeed();
	}

	/**
	 * An arm sends the time and the hide total; it runs out on the player's own play clock (OFF sent), ends on PARTICIPATION
	 * false, a spectator, the story, the switch's revert ({@code disarmAll}) and the client's spent report; a client that
	 * cannot take it is never armed.
	 */
	@GameTest
	public void namelessArmsRunOutAndEndOnLeave(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		NameHooks hooks = new NameHooks().with(a);
		Nameless nameless = new Nameless(hooks);
		NamelessConfig config = NamelessConfig.get();
		long ticks = config.armTicks();
		helper.assertTrue(ticks == ModConfig.realTicks(20 * 60), "20 minutes is " + ticks);

		helper.assertTrue(nameless.arm(a, ticks, false) == FireResult.FIRED && nameless.isArmed(a.getUUID()), "not armed");
		NamelessPayload armed = hooks.sent.getLast();
		helper.assertTrue(armed.armed() && armed.ticksLeft() == ticks && armed.hideTicks() == config.hideTicks() && armed.hideTicks() == 600,
				"sent " + armed);
		hooks.play += ticks - 1;
		nameless.check(server);
		helper.assertTrue(nameless.isArmed(a.getUUID()) && nameless.ticksLeft(server, a.getUUID()) == 1, "ran out early");
		hooks.play += 1;
		nameless.check(server);
		helper.assertTrue(!nameless.isArmed(a.getUUID()) && hooks.sent.getLast().equals(NamelessPayload.OFF), "did not run out");

		nameless.arm(a, ticks, false);
		nameless.onParticipation(server, a.getUUID(), false);
		helper.assertTrue(!nameless.isArmed(a.getUUID()) && hooks.sent.getLast().equals(NamelessPayload.OFF), "kept after leaving");

		nameless.arm(a, ticks, false);
		hooks.online.put(a.getUUID(), (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR)); // A turned spectator
		nameless.check(server);
		helper.assertFalse(nameless.isArmed(a.getUUID()), "kept for a spectator");
		hooks.online.put(a.getUUID(), a);

		nameless.arm(a, ticks, false);
		hooks.story = Optional.of("the story holds it");
		nameless.check(server);
		helper.assertFalse(nameless.isArmed(a.getUUID()), "kept once the story holds it");
		helper.assertTrue(nameless.arm(a, ticks, false) == FireResult.SKIPPED && !nameless.isArmed(a.getUUID()), "armed while the story holds it");
		hooks.story = Optional.empty();

		nameless.arm(a, ticks, true);
		nameless.disarmAll(server);
		nameless.disarmAll(server); // the switch's revert, idempotent
		helper.assertFalse(nameless.isArmed(a.getUUID()), "kept after the revert");

		nameless.arm(a, ticks, false);
		nameless.spent(a);
		helper.assertFalse(nameless.isArmed(a.getUUID()), "kept after the client spent it");

		hooks.canSend = false;
		helper.assertTrue(nameless.arm(a, ticks, false) == FireResult.SKIPPED && !nameless.isArmed(a.getUUID()), "armed a client that cannot take it");
		helper.succeed();
	}

	/** The payload round-trips; OFF is not armed; the card is sc_02, AMBIENT, Traces, FOG, WATCHER, no fake. */
	@GameTest
	public void namelessPayloadAndCard(GameTestHelper helper) {
		NamelessPayload payload = new NamelessPayload(24_000, 600);
		helper.assertTrue(roundTrip(NamelessPayload.CODEC, payload).equals(payload) && payload.armed(), "the payload");
		helper.assertTrue(!NamelessPayload.OFF.armed() && roundTrip(NamelessPayload.CODEC, NamelessPayload.OFF).equals(NamelessPayload.OFF), "OFF");
		helper.assertTrue(NamelessPayload.TYPE.id().toString().equals("a1016_02:entity/sc02"), "id " + NamelessPayload.TYPE.id());
		EventCard card = CardRegistry.get("sc_02").orElseThrow(() -> helper.assertionException("sc_02 is not registered"));
		helper.assertTrue(card.tier() == Tier.AMBIENT && card.earliestStage() == Stage.TRACES && card.tags().equals(java.util.Set.of(CardTag.FOG))
				&& card.habits().equals(java.util.Set.of(Habit.WATCHER)) && !card.hasFake(), "the card's shape");
		helper.succeed();
	}

	/** The sections read fresh as version 1 with the design's numbers. */
	@GameTest
	public void theSectionsReadFreshAtVersion1(GameTestHelper helper) {
		NamelessConfig n = ModConfig.readSection(new JsonObject(), NamelessConfig.SECTION, NamelessConfig.class, NamelessConfig::new);
		helper.assertTrue(n.version == 1 && n.friendMinAtFire == 48.0 && n.minDistance == 24.0 && n.maxDistance == 64.0 && n.fogShare == 0.5
				&& n.stillSeconds == 1.5 && n.armMinutes == 20.0 && n.hideSecondsTotal == 30.0, "sc02");
		HisViewConfig h = ModConfig.readSection(new JsonObject(), HisViewConfig.SECTION, HisViewConfig.class, HisViewConfig::new);
		helper.assertTrue(h.version == 1 && h.fromStage == 2 && h.maxBaseDistance == 96.0 && h.hostBaseDistance == 48.0 && h.standSeconds == 1.0
				&& h.iconSize == 64 && h.iconSize() == 64, "sc03");
		helper.assertTrue(HisViewConfig.SECTION.equals("sc03") && NamelessConfig.SECTION.equals("sc02"), "the section names");
		helper.succeed();
	}

	/** The common gates: the switch, the overworld, each story flag and 30 s of combat. */
	@GameTest
	public void theCommonGatesHoldTheStoryAndCombat(GameTestHelper helper) {
		long combat = ScGates.combatTicks();
		helper.assertTrue(ScGates.refusal(true, true, f -> false, combat, combat).isEmpty(), "nothing holds it");
		helper.assertTrue(ScGates.refusal(false, true, f -> false, combat, combat).isPresent(), "the layer off");
		helper.assertTrue(ScGates.refusal(true, false, f -> false, combat, combat).isPresent(), "the Nether");
		helper.assertTrue(ScGates.refusal(true, true, f -> false, combat - 1, combat).isPresent(), "combat 29 s ago");
		for (String flag : ScGates.STORY_FLAGS) {
			helper.assertTrue(ScGates.refusal(true, true, flag::equals, combat, combat).isPresent(), flag + " does not hold it");
		}
		helper.assertTrue(ScGates.STORY_FLAGS.containsAll(List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete",
				"ending:last_minute", "director:obeyed_after_stop")), "a common gate is missing");
		helper.succeed();
	}

	// --- 03: his view ---

	/** Hooks for a test's own 03: the server kind, the host, the clock, the base, the folder, and what was sent. */
	private static final class ViewHooks implements HisView.Hooks {
		final EntityData data = new EntityData();
		final Map<UUID, ServerPlayer> online = new HashMap<>();
		final List<HisViewPayload> sent = new ArrayList<>();
		boolean integrated = true;
		@Nullable UUID host;
		Stage stage = Stage.PROXIMITY;
		@Nullable BlockPos base;
		long time = 18000L;
		boolean fog;
		boolean dComplete;
		Optional<String> common = Optional.empty();
		Path folder;

		ViewHooks(Path folder) {
			this.folder = folder;
		}

		@Override
		public boolean integrated(MinecraftServer server) {
			return integrated;
		}

		@Override
		public boolean isHost(ServerPlayer player) {
			return player.getUUID().equals(host);
		}

		@Override
		public EntityData data(MinecraftServer server) {
			return data;
		}

		@Override
		public Stage stage(MinecraftServer server) {
			return stage;
		}

		@Override
		public Optional<String> commonRefusal(ServerPlayer host) {
			return common;
		}

		@Override
		public @Nullable BlockPos base(ServerPlayer host) {
			return base;
		}

		@Override
		public long timeOfDay(MinecraftServer server) {
			return time;
		}

		@Override
		public boolean fogCounts(MinecraftServer server) {
			return fog;
		}

		@Override
		public boolean send(ServerPlayer player, HisViewPayload payload) {
			sent.add(payload);
			return true;
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(online.get(player));
		}

		@Override
		public Path worldFolder(MinecraftServer server) {
			return folder;
		}

		@Override
		public boolean dComplete(MinecraftServer server) {
			return dComplete;
		}
	}

	/** A folder of its own for one test inside the test world's folder (the layer writes only inside a world's folder). */
	private static Path testFolder(GameTestHelper helper, String name) {
		return SelfCreated.worldFolder(helper.getLevel().getServer()).resolve("sc4_test").resolve(name + "_" + UUID.randomUUID());
	}

	private static void deleteTree(Path folder) {
		if (!Files.exists(folder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		} catch (Exception e) {
			// a test's own folder; nothing else is touched
		}
	}

	/**
	 * The arm rules: an integrated server, the host, once per world, a surface card variant (not the glimpse, the last one,
	 * the Nether and End ones), the overworld, from Proximity, night or fog, the figure within 96 of the base and the host
	 * within 48; the debug's forced arm skips the stage and the distances only.
	 */
	@GameTest
	public void hisViewArmsOnlyForTheHostNearTheBaseAtNightOrInFog(GameTestHelper helper) {
		HisViewConfig c = new HisViewConfig();
		BlockPos base = new BlockPos(0, 64, 0);
		Vec3 him = new Vec3(60.5, 64.0, 0.5);
		Vec3 host = new Vec3(10.5, 64.0, 0.5);
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.PROXIMITY, Variant.RIDGE, true, base, him, host, 18000L, false, c, false).isEmpty(),
				"a night ridge sighting near the base");
		helper.assertTrue(HisViewRules.armRefusal(false, true, false, Stage.PROXIMITY, Variant.RIDGE, true, base, him, host, 18000L, false, c, false).isPresent(),
				"a dedicated server");
		helper.assertTrue(HisViewRules.armRefusal(true, false, false, Stage.PROXIMITY, Variant.RIDGE, true, base, him, host, 18000L, false, c, false).isPresent(),
				"a guest");
		helper.assertTrue(HisViewRules.armRefusal(true, true, true, Stage.PROXIMITY, Variant.RIDGE, true, base, him, host, 18000L, false, c, false).isPresent(),
				"twice in a world");
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.TRACES, Variant.RIDGE, true, base, him, host, 18000L, false, c, false).isPresent(),
				"in Traces");
		for (Variant v : List.of(Variant.GLIMPSE, Variant.LAST_ONE, Variant.AMONG_ENDERMEN, Variant.AMONG_PIGLINS)) {
			helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.PROXIMITY, v, true, base, him, host, 18000L, false, c, true).isPresent(),
					"the " + v.shortName());
		}
		for (Variant v : List.of(Variant.COW, Variant.CLOSE, Variant.BETWEEN_TRUNKS, Variant.ACROSS_WATER, Variant.WALKS_AWAY, Variant.IN_THE_LIGHT)) {
			helper.assertTrue(HisViewRules.variantCounts(v), "the " + v.shortName() + " does not count");
		}
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.PROXIMITY, Variant.RIDGE, false, base, him, host, 18000L, false, c, true).isPresent(),
				"not the overworld");
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.PROXIMITY, Variant.RIDGE, true, base, him, host, 6000L, false, c, false).isPresent(),
				"midday, no fog");
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.PROXIMITY, Variant.RIDGE, true, base, him, host, 6000L, true, c, false).isEmpty(),
				"midday in a counting fog");
		helper.assertTrue(HisViewRules.night(13000L) && !HisViewRules.night(23000L) && !HisViewRules.night(12999L), "night is 13000 to 23000");
		Vec3 far = new Vec3(97.5, 64.0, 0.5);
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.PROXIMITY, Variant.RIDGE, true, base, far, host, 18000L, false, c, false).isPresent(),
				"he is 97 from the base");
		Vec3 away = new Vec3(49.5, 64.0, 0.5);
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.PROXIMITY, Variant.RIDGE, true, base, him, away, 18000L, false, c, false).isPresent(),
				"the host is 49 from the base");
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.PROXIMITY, Variant.RIDGE, true, null, him, host, 18000L, false, c, false).isPresent(),
				"no base");
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.TRACES, Variant.RIDGE, true, null, far, away, 18000L, false, c, true).isEmpty(),
				"forced: the stage and the distances skipped");
		helper.assertTrue(HisViewRules.armRefusal(true, true, false, Stage.TRACES, Variant.RIDGE, true, null, far, away, 6000L, false, c, true).isPresent(),
				"forced still needs night or fog");
		helper.succeed();
	}

	/**
	 * A figure's first sighting set up for the host arms the host's client with that figure; the report from the host sets the
	 * world's flag once; a report from anyone else, or before any arm, is ignored; the debug's forced next sighting skips the
	 * distances.
	 */
	@GameTest
	public void hisViewArmsTheHostAndTheReportSetsTheFlagOnce(GameTestHelper helper) {
		floor(helper, 8);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer host = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer guest = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		host.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)), 0.0F, 0.0F);
		ViewHooks hooks = new ViewHooks(testFolder(helper, "arm"));
		hooks.host = host.getUUID();
		hooks.online.put(host.getUUID(), host);
		hooks.online.put(guest.getUUID(), guest);
		hooks.base = helper.absolutePos(new BlockPos(1, 1, 1));
		HisView view = new HisView(hooks);
		HimEntity him = FigureApi.spawn(level, Variant.RIDGE, helper.absoluteVec(new Vec3(5.5, 1.0, 5.5)), 0.0F, null, Watchers.of(level))
				.orElseThrow(() -> helper.assertionException("the figure did not spawn"));
		try {
			helper.assertFalse(view.report(host), "a report before any arm counted");
			him.appearFor(guest.getUUID());
			view.onAppeared(level, him);
			helper.assertTrue(hooks.sent.isEmpty() && view.lastRefusal().orElse("").contains("host"), "armed for a guest: " + view.lastRefusal());
			him.appearFor(host.getUUID());
			view.onAppeared(level, him);
			helper.assertTrue(hooks.sent.equals(List.of(new HisViewPayload(him.getId()))) && view.armedFigure() == him.getId(), "not armed: "
					+ view.lastRefusal());
			helper.assertFalse(view.report(guest), "a guest's report counted");
			helper.assertTrue(view.report(host) && hooks.data.sc03Done(), "the host's report did not set the flag");
			helper.assertFalse(view.report(host), "counted twice");
			view.onAppeared(level, him);
			helper.assertTrue(hooks.sent.size() == 1 && view.lastRefusal().orElse("").contains("already"), "armed again once done");

			// The debug's next sighting: the host far from the base, in Traces.
			hooks.data.setSc03Done(false);
			hooks.stage = Stage.TRACES;
			host.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)).add(200.0, 0.0, 0.0), 0.0F, 0.0F);
			view.onAppeared(level, him);
			helper.assertTrue(hooks.sent.size() == 1, "armed in Traces far from the base");
			view.forceNext();
			view.onAppeared(level, him);
			helper.assertTrue(hooks.sent.size() == 2 && !view.forcing(), "the forced arm: " + view.lastRefusal());
			hooks.integrated = false;
			view.forceNext();
			view.onAppeared(level, him);
			helper.assertTrue(hooks.sent.size() == 2, "armed on a dedicated server");
		} finally {
			him.discard();
			deleteTree(hooks.folder);
		}
		helper.succeed();
	}

	/**
	 * The files: the picture is written only over an existing {@code icon.png}, the old one kept once as the copy (never
	 * replaced by a later picture), all inside the world's folder; putting it back writes the copy as {@code icon.png} once,
	 * only after the picture was taken; Ending D's afterward does it once; the reset puts it back and forgets.
	 */
	@GameTest
	public void hisViewKeepsTheOldPictureOnceAndPutsItBackOnce(GameTestHelper helper) throws Exception {
		MinecraftServer server = helper.getLevel().getServer();
		Path folder = testFolder(helper, "files");
		ViewHooks hooks = new ViewHooks(folder);
		HisView view = new HisView(hooks);
		byte[] old = {1, 2, 3, 4};
		byte[] first = HisViewRules.png(2, 2, new int[] {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF});
		byte[] second = HisViewRules.png(1, 1, new int[] {0x123456});
		Path icon = folder.resolve(HisViewRules.ICON);
		Path before = folder.resolve(HisViewRules.BEFORE);
		try {
			helper.assertTrue(HisView.writePicture(folder, first).isPresent() && !Files.exists(icon) && !Files.exists(before),
					"a picture written into a world with no icon.png");
			helper.assertTrue(SelfCreated.writeInWorld(folder, HisViewRules.ICON, old), "the test's own icon");
			helper.assertTrue(HisView.writePicture(folder, first).isEmpty(), "the picture was not written");
			helper.assertTrue(Arrays.equals(Files.readAllBytes(icon), first) && Arrays.equals(Files.readAllBytes(before), old), "icon or copy wrong");
			helper.assertTrue(HisView.writePicture(folder, second).isEmpty() && Arrays.equals(Files.readAllBytes(before), old)
					&& Arrays.equals(Files.readAllBytes(icon), second), "the copy was replaced by a later picture");

			helper.assertTrue(view.restore(server).equals("not taken") && Arrays.equals(Files.readAllBytes(icon), second), "put back before it was taken");
			hooks.data.setSc03Done(true);
			helper.assertFalse(view.afterward(server), "put back before Ending D's afterward");
			hooks.dComplete = true;
			helper.assertTrue(view.afterward(server) && Arrays.equals(Files.readAllBytes(icon), old) && hooks.data.sc03Restored(), "not put back");
			SelfCreated.writeInWorld(folder, HisViewRules.ICON, second); // a picture vanilla took since
			helper.assertFalse(view.afterward(server), "put back twice");
			helper.assertTrue(view.restore(server).equals("already put back") && Arrays.equals(Files.readAllBytes(icon), second), "the revert ran twice");

			helper.assertTrue(view.reset(server).equals("already put back") && !hooks.data.sc03Done() && !hooks.data.sc03Restored(), "the reset");
			hooks.data.setSc03Done(true);
			helper.assertTrue(view.restore(server).equals("put back") && Arrays.equals(Files.readAllBytes(icon), old), "after a reset");
			try (Stream<Path> files = Files.walk(folder)) {
				helper.assertTrue(files.allMatch(p -> p.startsWith(folder) && !p.getFileName().toString().endsWith(".tmp")), "a file outside or a temp left");
			}
		} finally {
			deleteTree(folder);
		}
		helper.succeed();
	}

	/** The camera from his eyes toward the host's, the world icon's crop, and the PNG (opaque RGB, the right pixels). */
	@GameTest
	public void hisViewCameraCropAndPicture(GameTestHelper helper) throws Exception {
		float[] south = HisViewRules.look(Vec3.ZERO, new Vec3(0.0, 0.0, 10.0));
		helper.assertTrue(Math.abs(south[0]) < 1.0E-3 && Math.abs(south[1]) < 1.0E-3, "south " + Arrays.toString(south));
		float[] east = HisViewRules.look(Vec3.ZERO, new Vec3(10.0, 0.0, 0.0));
		helper.assertTrue(Math.abs(east[0] + 90.0F) < 1.0E-3, "east " + Arrays.toString(east));
		float[] downWest = HisViewRules.look(new Vec3(0.0, 10.0, 0.0), new Vec3(-10.0, 0.0, 0.0));
		helper.assertTrue(Math.abs(downWest[0] - 90.0F) < 1.0E-3 && Math.abs(downWest[1] - 45.0F) < 1.0E-3, "down west " + Arrays.toString(downWest));
		// The same look the figure's own facing would have toward the host (his yaw rule).
		Vec3 eye = new Vec3(40.5, 66.62, -12.5);
		Vec3 hostEye = new Vec3(2.5, 65.62, 3.5);
		float[] look = HisViewRules.look(eye, hostEye);
		helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(look[0] - HimEntity.yawToward(eye, hostEye))) < 1.0E-3 && look[1] > 0.0F,
				"toward the host " + Arrays.toString(look));

		helper.assertTrue(Arrays.equals(HisViewRules.crop(1920, 1080), new int[] {420, 0, 1080}), "landscape crop");
		helper.assertTrue(Arrays.equals(HisViewRules.crop(800, 1200), new int[] {0, 200, 800}), "portrait crop");
		helper.assertTrue(Arrays.equals(HisViewRules.crop(64, 64), new int[] {0, 0, 64}), "square crop");

		int[] pixels = new int[64 * 64];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = 0x80000000 | (i * 0x10305) & 0xFFFFFF; // any colours; the alpha is dropped
		}
		byte[] png = HisViewRules.png(64, 64, pixels);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		helper.assertTrue(image != null && image.getWidth() == 64 && image.getHeight() == 64, "not a 64x64 PNG");
		for (int i = 0; i < pixels.length; i++) {
			int rgb = image.getRGB(i % 64, i / 64);
			helper.assertTrue(rgb == (0xFF000000 | pixels[i] & 0xFFFFFF), "pixel " + i + ": " + Integer.toHexString(rgb) + " for " + Integer.toHexString(pixels[i]));
		}
		helper.assertTrue(HisViewPayload.TYPE.id().toString().equals("a1016_02:entity/sc03")
				&& roundTrip(HisViewPayload.CODEC, new HisViewPayload(1234)).equals(new HisViewPayload(1234)), "the payload");
		helper.succeed();
	}
}
