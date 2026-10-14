package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.ParseResults;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Two players and one figure (MP-5, D-060.4, D-062). Mock players are never in the level or the player list, so
 * every figure here watches exactly the test's own players ({@link #watchedBy}) and every spawn check gets them
 * explicitly ({@link Watchers}); nothing here depends on who else is on the shared game test server. The yard is an
 * empty 24x20x24 structure; each test lays its own floor. {@link EntityGameTests} extends this (through
 * {@link FogAndRunGameTests}).
 */
public class TwoPlayerFigureGameTests {
	private static final String YARD = "a1016_02:accident/yard";
	/** Who stared him down, by UUID, as FigureApi.STARED reported it (registered once). */
	private static final List<UUID> STARES = new CopyOnWriteArrayList<>();
	private static boolean staresRegistered;

	/** A player whose game mode a test can switch (a mock's is fixed). In no level list, never a participant. */
	static final class TestPlayer extends ServerPlayer {
		GameType mode;

		TestPlayer(ServerLevel level, GameType mode) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-player"), ClientInformation.createDefault());
			this.mode = mode;
			mode.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode == null ? GameType.SURVIVAL : mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	/** A mock player (in no level list, never a participant) at {@code absolute}, looking along {@code yaw}. */
	static ServerPlayer player(GameTestHelper helper, GameType mode, Vec3 absolute, float yaw) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(mode);
		look(player, absolute, yaw);
		return player;
	}

	static void look(ServerPlayer player, Vec3 absolute, float yaw) {
		player.snapTo(absolute.x, absolute.y, absolute.z, yaw, 0.0F);
		player.setYHeadRot(yaw); // the view vector follows the head
	}

	/**
	 * A figure whose rules see exactly {@code everyone} (who may change while he ticks): the living non-spectators
	 * among them for what ends a sighting, all of them, spectators and the dead included, for the view rules. As in
	 * the level, where {@code observers} and {@code watchers} read {@code level.players()}.
	 */
	static HimEntity watchedBy(GameTestHelper helper, Variant variant, Vec3 relativeFeet, float yaw, List<ServerPlayer> everyone) {
		ServerLevel level = helper.getLevel();
		HimEntity him = new HimEntity(ModEntities.HIM, level) {
			@Override
			protected List<ServerPlayer> observers(ServerLevel serverLevel) {
				return everyone.stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
			}

			@Override
			protected Watchers watchers(ServerLevel serverLevel) {
				return Watchers.of(List.copyOf(everyone), serverLevel.getServer().getPlayerList().getViewDistance());
			}

			@Override
			protected boolean tickingAroundHim(ServerLevel serverLevel) {
				return true; // only the test's own chunks are loaded; in a world the players load them
			}
		};
		Vec3 feet = helper.absoluteVec(relativeFeet);
		him.snapTo(feet.x, feet.y, feet.z, yaw, 0.0F);
		him.setup(variant, yaw, null);
		helper.assertTrue(level.addFreshEntity(him), "the figure did not spawn");
		return him;
	}

	/** Stone floor at y 0 and air above, x 0..23, z from..to (relative). */
	private static void yard(GameTestHelper helper, int fromZ, int toZ) {
		for (int x = 0; x < 24; x++) {
			for (int z = fromZ; z <= toZ; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y < 5; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	private static Watchers watchers(GameTestHelper helper, ServerPlayer... players) {
		return Watchers.of(List.of(players), helper.getLevel().getServer().getPlayerList().getViewDistance());
	}

	/** Yaw from a player's feet toward a point, turned {@code off} degrees (looking at it, but not staring). */
	private static float yawAt(Vec3 from, Vec3 at, float off) {
		return HimEntity.yawToward(from, at) + off;
	}

	private static void discard(HimEntity him) {
		if (!him.isRemoved()) {
			him.discard();
		}
	}

	// --- out of view of everyone (D-060.4, D-012) ---

	/**
	 * A sighting set up for A spawns out of view of every player in the level: A looks away, but B (or a spectator)
	 * sees the spot, so he does not spawn there; with A alone he does. Nobody may be closer than the minimum distance
	 * either, even someone looking away. The spot finder uses the same rule.
	 */
	@GameTest(structure = YARD)
	public void aSightingForAIsRefusedWhileBCouldSeeTheSpot(GameTestHelper helper) {
		yard(helper, 8, 13);
		ServerLevel level = helper.getLevel();
		Vec3 spot = helper.absoluteVec(new Vec3(18.5, 1.0, 10.5));
		ServerPlayer a = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(4.5, 1.0, 10.5)), 90.0F); // looking -x, away
		Vec3 bFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 12.5));
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, spot, 20.0F));
		ServerPlayer spectator = player(helper, GameType.SPECTATOR, bFeet, yawAt(bFeet, spot, -20.0F));
		Vec3 nearFeet = spot.add(-7.0, 0.0, 0.0);
		ServerPlayer near = player(helper, GameType.SURVIVAL, nearFeet, 90.0F); // 7 blocks off, looking away from him
		double min = EntityConfig.get().minDistance();

		helper.assertTrue(watchers(helper, a).hides(level, HimEntity.viewBox(spot), min), "A alone sees the spot (the check is vacuous)");
		helper.assertFalse(watchers(helper, a, b).hides(level, HimEntity.viewBox(spot), min), "B sees the spot, yet it counts as hidden");
		helper.assertFalse(watchers(helper, a, spectator).hides(level, HimEntity.viewBox(spot), min), "a spectator's view does not count");
		helper.assertFalse(watchers(helper, a, near).hides(level, HimEntity.viewBox(spot), min), "someone within the minimum distance does not count");

		helper.assertTrue(FigureApi.spawn(level, Variant.RIDGE, spot, 90.0F, null, watchers(helper, a, b)).isEmpty(), "spawned in B's view");
		helper.assertTrue(FigureApi.spawn(level, Variant.RIDGE, spot, 90.0F, null, watchers(helper, a, spectator)).isEmpty(), "spawned in a spectator's view");
		helper.assertTrue(FigureApi.spawn(level, Variant.RIDGE, spot, 90.0F, null, watchers(helper, a, near)).isEmpty(), "spawned close to someone");
		Optional<HimEntity> alone = FigureApi.spawn(level, Variant.RIDGE, spot, 90.0F, null, watchers(helper, a));
		helper.assertTrue(alone.isPresent(), "refused with only A there, looking away");
		alone.ifPresent(TwoPlayerFigureGameTests::discard);

		// The spot finder's own rule is the same: B's sight hides nothing.
		SpotFinder.Query q = new SpotFinder.Query(level, a.position(), a.getEyePosition(), 13.0, 15.0, min, ModEntities.HIM.getDimensions(),
				box -> watchers(helper, a, b).hides(level, box, min), pos -> level.isLoaded(pos), RandomSource.create(3), 48);
		SpotFinder.open(q).ifPresent(found -> helper.assertTrue(watchers(helper, a, b).hides(level, HimEntity.viewBox(found.pos()), min),
				"the spot finder chose a place B can see: " + found.pos()));
		helper.succeed();
	}

	/** He never vanishes while B looks, even after A (who he was seen by too) turns away; once nobody looks, he goes. */
	@GameTest(structure = YARD, maxTicks = 260)
	public void noDespawnWhileBLooksEvenAfterATurnsAway(GameTestHelper helper) {
		yard(helper, 8, 13);
		Vec3 himFeet = helper.absoluteVec(new Vec3(18.5, 1.0, 10.5));
		Vec3 aFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 9.5));
		Vec3 bFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 11.5));
		// Both look at him 25 degrees off the crosshair: in view, never a stare (the stare cone is 8 degrees).
		ServerPlayer a = player(helper, GameType.SURVIVAL, aFeet, yawAt(aFeet, himFeet, 25.0F));
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, himFeet, -25.0F));
		List<ServerPlayer> everyone = new CopyOnWriteArrayList<>(List.of(a, b));
		HimEntity him = watchedBy(helper, Variant.RIDGE, new Vec3(18.5, 1.0, 10.5), 90.0F, everyone);
		long grace = Mth.ceil(EntityConfig.get().goneAfterUnseenSeconds * 20.0);
		boolean[] removedWhileBLooked = {false};
		boolean[] bTurned = {false};
		helper.onEachTick(() -> {
			if (him.isRemoved() && !bTurned[0]) {
				removedWhileBLooked[0] = true;
			}
		});
		helper.startSequence()
				.thenExecuteAfter(80, () -> {
					helper.assertTrue(him.everSeen() && !him.isRemoved() && !him.stared(), "not seen, gone or stared at: " + him.goneWhy());
					look(a, aFeet, yawAt(aFeet, himFeet, 180.0F)); // A turns away
				})
				.thenExecuteAfter((int) grace * 3 + 20, () -> {
					helper.assertFalse(removedWhileBLooked[0], "he vanished while B was looking: " + him.goneWhy());
					helper.assertTrue(him.unseenTicks() == 0, "B looks, yet he counts as unseen for " + him.unseenTicks());
					bTurned[0] = true;
					look(b, bFeet, yawAt(bFeet, himFeet, 180.0F)); // now nobody looks
				})
				.thenWaitUntil(() -> helper.assertTrue(him.isRemoved(), "still there with nobody looking"))
				.thenExecute(() -> {
					helper.assertFalse(removedWhileBLooked[0] || him.seenWhenRemoved(), "removed in view: " + him.goneWhy());
					discard(him);
				})
				.thenSucceed();
	}

	/**
	 * Anyone's stare counts (D-060.4): B stares him down while A looks away. STARED_AT_HIM fires for B exactly once,
	 * however long B keeps staring, sets entity:stared, and never fires for A.
	 */
	@GameTest(structure = YARD, maxTicks = 260)
	public void bStareCountsOnce(GameTestHelper helper) {
		if (!staresRegistered) {
			staresRegistered = true;
			FigureApi.STARED.register((player, him) -> STARES.add(player.getUUID()));
		}
		yard(helper, 8, 13);
		Vec3 himFeet = helper.absoluteVec(new Vec3(18.5, 1.0, 10.5));
		ServerPlayer a = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(4.5, 1.0, 9.5)), 90.0F); // looking away
		Vec3 bFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 11.5));
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, himFeet, 0.0F)); // straight at him
		HimEntity him = watchedBy(helper, Variant.RIDGE, new Vec3(18.5, 1.0, 10.5), 90.0F, new CopyOnWriteArrayList<>(List.of(a, b)));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(him.stared(), "B's stare did not count: stare " + him.stareTicks(b.getUUID()) + ", seen " + him.seenTicks()))
				.thenExecuteAfter(60, () -> {
					helper.assertTrue(him.staredBy().equals(Set.of(b.getUUID())), "stared by " + him.staredBy());
					long bCount = STARES.stream().filter(b.getUUID()::equals).count();
					helper.assertTrue(bCount == 1, "STARED fired " + bCount + " times for B");
					helper.assertFalse(STARES.contains(a.getUUID()), "STARED fired for A, who never looked");
					helper.assertTrue(HerobrineState.get(helper.getLevel().getServer()).hasFlag(FigureApi.STARED_FLAG), "entity:stared is not set");
					helper.assertTrue(him.triggered(), "B's stare did not end the sighting");
					discard(him);
				})
				.thenSucceed();
	}

	/** Two players stare at once: each stare counts, once each. Two looks are never added up into one stare. */
	@GameTest(structure = YARD, maxTicks = 260)
	public void twoStaresCountOnceEach(GameTestHelper helper) {
		if (!staresRegistered) {
			staresRegistered = true;
			FigureApi.STARED.register((player, him) -> STARES.add(player.getUUID()));
		}
		yard(helper, 8, 13);
		Vec3 himFeet = helper.absoluteVec(new Vec3(18.5, 1.0, 10.5));
		Vec3 aFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 9.5));
		Vec3 bFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 11.5));
		ServerPlayer a = player(helper, GameType.SURVIVAL, aFeet, yawAt(aFeet, himFeet, 0.0F));
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, himFeet, 0.0F));
		HimEntity him = watchedBy(helper, Variant.RIDGE, new Vec3(18.5, 1.0, 10.5), 90.0F, new CopyOnWriteArrayList<>(List.of(a, b)));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(him.staredBy().size() == 2, "stared by " + him.staredBy()))
				.thenExecuteAfter(40, () -> {
					helper.assertTrue(STARES.stream().filter(a.getUUID()::equals).count() == 1 && STARES.stream().filter(b.getUUID()::equals).count() == 1,
							"each stare should count once");
					discard(him);
				})
				.thenSucceed();
	}

	/** Neither A nor B looks long enough, though their looks add up past a stare: nothing counts. */
	@GameTest(structure = YARD, maxTicks = 300)
	public void twoShortLooksAreNotAStare(GameTestHelper helper) {
		yard(helper, 8, 13);
		Vec3 himFeet = helper.absoluteVec(new Vec3(18.5, 1.0, 10.5));
		Vec3 aFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 9.5));
		Vec3 bFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 11.5));
		// Both in view of him, neither staring, until the minimum time on screen is over.
		ServerPlayer a = player(helper, GameType.SURVIVAL, aFeet, yawAt(aFeet, himFeet, 40.0F));
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, himFeet, -40.0F));
		HimEntity him = watchedBy(helper, Variant.RIDGE, new Vec3(18.5, 1.0, 10.5), 90.0F, new CopyOnWriteArrayList<>(List.of(a, b)));
		int stare = Mth.ceil(EntityConfig.get().stareSeconds * 20.0);
		int half = stare / 2 + 4;
		helper.startSequence()
				.thenExecuteAfter(Mth.ceil(EntityConfig.get().minSeenSeconds * 20.0) + 5, () -> look(a, aFeet, yawAt(aFeet, himFeet, 0.0F)))
				.thenExecuteAfter(half, () -> {
					look(a, aFeet, yawAt(aFeet, himFeet, 40.0F));
					look(b, bFeet, yawAt(bFeet, himFeet, 0.0F));
				})
				.thenExecuteAfter(half, () -> {
					helper.assertFalse(him.stared(), "two half looks counted as a stare: " + him.staredBy());
					helper.assertTrue(him.stareTicks(a.getUUID()) < stare && him.stareTicks(b.getUUID()) < stare, "a stare ran past its length");
					discard(him);
				})
				.thenSucceed();
	}

	// --- one him ---

	/**
	 * He is one being: while one figure is out (here, this test's), a sighting for B is refused with NO_SPOT, by the
	 * card and by the fog-edge spawn, so the director keeps it; and Ending D's spawnAt is refused too.
	 */
	@GameTest
	public void aSecondSightingIsRefusedWhileHeIsOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		HimEntity first = FigureApi.spawn(level, Variant.RIDGE, helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)), 0.0F, null, Watchers.of(List.of(), 8))
				.orElseThrow(() -> helper.assertionException("the first figure did not spawn"));
		try {
			ServerPlayer b = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)), 0.0F);
			helper.assertTrue(FigureApi.anyOut(server), "the figure that is out does not count");
			helper.assertTrue(FigureApi.spawnAtFogEdge(b, Variant.RIDGE, RandomSource.create(1), false).result() == FireResult.NO_SPOT,
					"a fog-edge sighting for B while he is out");
			EventCard card = CardRegistry.get(Variant.RIDGE.cardId()).orElseThrow();
			helper.assertTrue(card.fire(new FireContext(b, level, false, false, RandomSource.create(2))) == FireResult.NO_SPOT,
					"the card for B did not wait for him (NO_SPOT)");
			helper.assertTrue(FigureApi.spawnAt(level, Variant.ACROSS_WATER, helper.absoluteVec(new Vec3(6.5, 1.0, 6.5)), 0.0F).isEmpty(),
					"spawnAt made a second figure");
			helper.assertTrue(SightingGates.check(b, level, Variant.RIDGE).isPresent(), "the gates let a second sighting through");
			helper.assertFalse(first.isRemoved() || first.triggered(), "a refused sighting touched the figure that is out");
		} finally {
			discard(first);
		}
		helper.succeed();
	}

	// --- the one he appeared for goes (D-062.5) ---

	/**
	 * A leaves mid-sighting: he walks away at once, never vanishes while B watches him go, and is gone once nobody
	 * can see him.
	 */
	@GameTest(structure = YARD, maxTicks = 300)
	public void whenTheOneHeAppearedForLeavesHeGoesOutOfEveryonesView(GameTestHelper helper) {
		yard(helper, 8, 13);
		Vec3 himFeet = helper.absoluteVec(new Vec3(18.5, 1.0, 10.5));
		Vec3 aFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 9.5));
		Vec3 bFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 11.5));
		ServerPlayer a = player(helper, GameType.SURVIVAL, aFeet, yawAt(aFeet, himFeet, 25.0F));
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, himFeet, -25.0F));
		List<ServerPlayer> everyone = new CopyOnWriteArrayList<>(List.of(a, b));
		HimEntity him = watchedBy(helper, Variant.RIDGE, new Vec3(18.5, 1.0, 10.5), 90.0F, everyone);
		him.appearFor(a.getUUID());
		helper.startSequence()
				.thenExecuteAfter(10, () -> {
					helper.assertTrue(him.everSeen() && !him.triggered(), "not seen, or already ending");
					everyone.remove(a); // A leaves the game
				})
				.thenExecuteAfter(2, () -> helper.assertTrue(him.triggered() && him.phase() == HimEntity.Phase.LEAVING && him.gait() != Variant.Gait.RUN,
						"A left, yet he is " + him.phase() + " (" + him.gait() + ")"))
				.thenExecuteAfter(30, () -> {
					// B saw him less than the minimum time on screen: he walks, and stays while B watches.
					helper.assertFalse(him.isRemoved(), "he vanished while B watched: " + him.goneWhy());
					look(b, bFeet, yawAt(bFeet, himFeet, 180.0F));
				})
				.thenWaitUntil(() -> helper.assertTrue(him.isRemoved(), "still there with nobody looking"))
				.thenExecute(() -> helper.assertFalse(him.seenWhenRemoved(), "removed in someone's view: " + him.goneWhy()))
				.thenSucceed();
	}

	/** The one he appeared for dies or turns spectator mid-sighting: he leaves the same way. Someone else's going changes nothing. */
	@GameTest(structure = YARD, maxTicks = 100)
	public void whenTheOneHeAppearedForDiesOrSpectatesHeLeaves(GameTestHelper helper) {
		yard(helper, 8, 13);
		Vec3 himFeet = helper.absoluteVec(new Vec3(18.5, 1.0, 10.5));
		Vec3 aFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 9.5));
		Vec3 bFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 11.5));
		ServerPlayer a = player(helper, GameType.SURVIVAL, aFeet, yawAt(aFeet, himFeet, 25.0F));
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, himFeet, -25.0F));
		ServerPlayer c = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, himFeet, -30.0F));
		List<ServerPlayer> forDeath = new CopyOnWriteArrayList<>(List.of(a, b, c));
		HimEntity dies = watchedBy(helper, Variant.RIDGE, new Vec3(18.5, 1.0, 10.5), 90.0F, forDeath);
		dies.appearFor(a.getUUID());
		// The same, with A turning spectator: a spectator still watches, never takes part.
		TestPlayer turns = new TestPlayer(helper.getLevel(), GameType.SURVIVAL);
		look(turns, aFeet, yawAt(aFeet, himFeet, 25.0F));
		List<ServerPlayer> forSpectator = new CopyOnWriteArrayList<>(List.of(turns, b));
		HimEntity spectates = watchedBy(helper, Variant.RIDGE, new Vec3(18.5, 1.0, 12.5), 90.0F, forSpectator);
		spectates.appearFor(turns.getUUID());
		helper.startSequence()
				.thenExecuteAfter(10, () -> {
					forDeath.remove(c); // someone else leaving changes nothing
					helper.assertFalse(dies.triggered() || spectates.triggered(), "someone else leaving ended A's sighting");
					a.setHealth(0.0F);
					turns.mode = GameType.SPECTATOR;
					helper.assertTrue(turns.isSpectator(), "the test player did not turn spectator");
				})
				.thenExecuteAfter(2, () -> {
					helper.assertTrue(dies.triggered() && (dies.phase() == HimEntity.Phase.LEAVING || dies.isRemoved()), "A died, yet he is " + dies.phase());
					helper.assertTrue(spectates.triggered() && (spectates.phase() == HimEntity.Phase.LEAVING || spectates.isRemoved()),
							"A turned spectator, yet he is " + spectates.phase());
					discard(dies);
					discard(spectates);
				})
				.thenSucceed();
	}

	// --- the run and the rush (D-036, D-037) ---

	/** His run beats the fastest chaser, not just the nearest: a slow near walker and a fast far sprinter. */
	@GameTest
	public void heOutrunsTheFastestChaser(GameTestHelper helper) {
		Chase walker = new Chase();
		Chase sprinter = new Chase();
		Chase leaving = new Chase();
		for (int t = 0; t <= Chase.WINDOW; t++) {
			walker.update(t * 0.2, 0.0, 15.0 - t * 0.2); // 4 blocks/s, closing
			sprinter.update(t * 0.4, 5.0, 30.0 - t * 0.4); // 8 blocks/s, closing
			leaving.update(t * 0.45, 9.0, 20.0 + t * 0.45); // 9 blocks/s, running off
		}
		double fastest = SightingRules.chaserSpeed(List.of(walker, sprinter, leaving), walker);
		helper.assertTrue(Math.abs(fastest - sprinter.speed()) < 1.0E-9, "chaser speed " + fastest + ", the sprinter runs " + sprinter.speed());
		EntityConfig config = new EntityConfig();
		double run = SightingRules.runSpeed(config.baseRunSpeed, fastest, config.outrunFactor, config.maxRunSpeed);
		helper.assertTrue(run > sprinter.speed(), "he runs " + run + ", the sprinter " + sprinter.speed());
		helper.assertTrue(SightingRules.chaserSpeed(List.of(leaving), null) == 0.0, "someone running away counts as a chaser");
		helper.assertTrue(SightingRules.chaserSpeed(List.of(), walker) == walker.speed(), "the nearest player's speed is not the floor");

		// He leaves away from everyone, never toward the other player.
		Vec3 him = Vec3.ZERO;
		Vec3 a = new Vec3(-5.0, 0.0, 0.0);
		Vec3 b = new Vec3(0.0, 0.0, -20.0);
		Vec3 away = HimEntity.awayFrom(him, List.of(a, b), new Vec3(1, 0, 0));
		helper.assertTrue(away.dot(him.subtract(a).normalize()) > 0.5 && away.dot(him.subtract(b).normalize()) > 0.0, "he leaves toward someone: " + away);
		helper.succeed();
	}

	/** The rush passes the chaser, and never within the offset of anyone else: a bystander in his line stops the pass. */
	@GameTest
	public void theRushNeverPassesAnyoneElse(GameTestHelper helper) {
		double offset = 2.0;
		Vec3 him = new Vec3(0.0, 64.0, 12.0);
		Vec3 chaser = Vec3.ZERO.add(0.0, 64.0, 0.0);
		Vec3 velocity = new Vec3(0.0, 0.0, 1.0); // flying straight at him
		Rush rush = Rush.toward(him, chaser, Rush.sideAwayFrom(him, chaser, velocity), offset);
		helper.assertTrue(rush != null, "no rush");
		List<Rush.Step> path = Rush.toward(him, chaser, rush.side(), offset).simulate(him, chaser, velocity, 0.45, offset + 4.0);
		helper.assertTrue(path.size() > 4, "no pass simulated");
		helper.assertTrue(Rush.passesClear(path, List.of(), offset), "a pass with nobody else is not clear");
		Rush.Step middle = path.get(path.size() / 2);
		helper.assertFalse(Rush.passesClear(path, List.of(new Rush.Bystander(middle.him(), Vec3.ZERO)), offset), "a pass through someone standing in his line");
		helper.assertFalse(Rush.passesClear(path, List.of(new Rush.Bystander(middle.him().add(1.0, 0.0, 0.0), Vec3.ZERO)), offset),
				"a pass a block from someone");
		Vec3 aside = middle.him().add(middle.outward().scale(12.0));
		helper.assertTrue(Rush.passesClear(path, List.of(new Rush.Bystander(aside, Vec3.ZERO)), offset), "someone well clear of the line stops the pass");
		helper.assertFalse(Rush.clearOf(middle.him(), List.of(new Rush.Bystander(middle.him().add(0.0, 0.0, -1.0), new Vec3(0.0, 0.0, 0.5))), offset),
				"a step into someone walking into his line");
		helper.assertTrue(Rush.clearOf(middle.him(), List.of(new Rush.Bystander(aside, Vec3.ZERO)), offset), "a clear step refused");
		helper.succeed();
	}

	// --- fog per player (D-035) ---

	/** Each player's client fog is theirs: kept by UUID, used for their own band, forgotten when only they leave. */
	@GameTest
	public void eachPlayerHasTheirOwnFog(GameTestHelper helper) {
		ServerPlayer a = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)), 0.0F);
		ServerPlayer b = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(3.5, 1.0, 1.5)), 0.0F);
		try {
			ReportedFog.set(a, 20.0);
			ReportedFog.set(b, 28.0);
			double renderA = FogEdge.of(a).renderLimit();
			helper.assertTrue(renderA >= 28.0, "the mock's render limit is too short for this test: " + renderA);
			helper.assertTrue(FogEdge.of(a).limit() == 20.0 && FogEdge.of(b).limit() == 28.0,
					"fog ends " + FogEdge.of(a).limit() + " / " + FogEdge.of(b).limit() + ": one player's report used for the other");
			helper.assertTrue(FogEdge.of(a).fromClient() && FogEdge.of(b).fromClient(), "a report was not used");
			ReportedFog.forget(a.getUUID());
			helper.assertFalse(FogEdge.of(a).fromClient(), "A's report outlived A");
			helper.assertTrue(FogEdge.of(b).limit() == 28.0, "A leaving changed B's fog");
		} finally {
			ReportedFog.forget(a.getUUID());
			ReportedFog.forget(b.getUUID());
		}
		helper.succeed();
	}

	// --- other dimensions (D-034) ---

	/**
	 * Each player's sightings are asked in their own level: B in the Nether gets the Nether's card while A is in the
	 * overworld, and neither gets the other dimension's.
	 */
	@GameTest
	public void dimensionSightingsFollowEachPlayer(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel();
		ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "no Nether");
		ServerPlayer a = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)), 0.0F);
		ServerPlayer b = new ServerPlayer(nether.getServer(), nether, new GameProfile(UUID.randomUUID(), "b-in-the-nether"), ClientInformation.createDefault());
		b.snapTo(0.5, 70.0, 0.5, 0.0F, 0.0F);
		helper.assertTrue(b.level() == nether, "B is not in the Nether");

		helper.assertFalse(SightingGates.check(b, nether, Variant.AMONG_PIGLINS).orElse("").startsWith("not in "), "the Nether card does not fit B there");
		helper.assertTrue(SightingGates.check(a, overworld, Variant.AMONG_PIGLINS).orElse("").startsWith("not in "), "the Nether card fits A in the overworld");
		helper.assertTrue(SightingGates.check(b, nether, Variant.RIDGE).orElse("").startsWith("not in "), "an overworld card fits B in the Nether");
		helper.assertFalse(SightingGates.check(a, overworld, Variant.RIDGE).orElse("").startsWith("not in "), "an overworld card does not fit A");
		FigureApi.Spawned forB = FigureApi.spawnAtFogEdge(b, Variant.AMONG_PIGLINS, RandomSource.create(5), false, Watchers.of(List.of(b), 8));
		if (forB.figure() != null) {
			forB.figure().discard();
		}
		helper.assertTrue(forB.result() != FireResult.SKIPPED, "the Nether sighting was refused for B, who is there");
		helper.assertTrue(FigureApi.spawnAtFogEdge(a, Variant.AMONG_PIGLINS, RandomSource.create(5), false).result() == FireResult.SKIPPED,
				"the Nether sighting ran for A in the overworld");
		helper.succeed();
	}

	// --- commands (D-062.9) ---

	/** The commands take a named player; from the console they act on the first participant. */
	@GameTest
	public void entityCommandsTakeAPlayer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
		for (String command : List.of("a1016 entity spawn ridge @p", "a1016 entity info @p", "a1016 entity info", "a1016 entity spawn cow")) {
			ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, console);
			helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "does not parse: " + command);
		}
		ServerPlayer first = Services.watch().participants(server).stream().findFirst().orElse(null);
		helper.assertTrue(EntityCommands.target(console) == first, "the console's player is not the first participant");
		helper.succeed();
	}
}
