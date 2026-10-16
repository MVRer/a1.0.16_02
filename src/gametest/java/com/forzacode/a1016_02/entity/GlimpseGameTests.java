package com.forzacode.a1016_02.entity;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.FogWeatherRules;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The glimpse (D-073) and fog days for the sightings (D-071). Each tunnel test lays a sealed 1x2 stone tunnel along x
 * in the yard (cells x 1 to 22, y 1 and 2, z 10): A stands at x 3.5, the glimpse's spot is 16 blocks east at x 19.5,
 * past every torch. Mock players are in no level list, so every check gets them explicitly ({@link Watchers}), and the
 * figures watch exactly them ({@link #watchedBy}). {@link EntityGameTests} extends this (through
 * {@link FogAndRunGameTests}).
 */
public class GlimpseGameTests extends TwoPlayerFigureGameTests {
	private static final String YARD = "a1016_02:accident/yard";
	/** The glimpse's feet, relative: 16 blocks east of A, down the tunnel. */
	private static final Vec3 SPOT = new Vec3(19.5, 1.0, 10.5);
	private static final Vec3 A_FEET = new Vec3(3.5, 1.0, 10.5);
	/** Looking west (toward -x), away from the spot; and east, down the tunnel at it. */
	private static final float WEST = 90.0F;
	private static final float EAST = -90.0F;

	/** Stone from x 0 to 23, y 0 to 4, z 7 to 13, and the tunnel carved through it: x 1 to 22, y 1 and 2, z 10. */
	private static void tunnel(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int y = 0; y < 5; y++) {
				for (int z = 7; z <= 13; z++) {
					boolean air = x >= 1 && x <= 22 && (y == 1 || y == 2) && z == 10;
					helper.setBlock(x, y, z, air ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
	}

	/**
	 * A fresh tunnel keeps the yard's daylight until the light engine has run (the cells were open air): the sequence
	 * waits until the spot is dark under the rock, sky light included (the glimpse's darkness counts the sky, D-073).
	 */
	private static GameTestSequence afterTheLightSettles(GameTestHelper helper) {
		BlockPos feet = BlockPos.containing(helper.absoluteVec(SPOT));
		return helper.startSequence().thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBrightness(LightLayer.SKY, feet) == 0
				&& helper.getLevel().getBrightness(LightLayer.SKY, feet.above()) == 0, "the tunnel's light has not settled yet: sky "
				+ helper.getLevel().getBrightness(LightLayer.SKY, feet) + " at the spot"));
	}

	private static Watchers watching(GameTestHelper helper, ServerPlayer... players) {
		return Watchers.of(List.of(players), helper.getLevel().getServer().getPlayerList().getViewDistance());
	}

	/** In a test only the test's own chunks are loaded; in a world the players keep the spot ticking. */
	private static Predicate<Vec3> loaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		return feet -> level.isLoaded(BlockPos.containing(feet));
	}

	private static ServerPlayer a(GameTestHelper helper, float yaw) {
		return player(helper, GameType.SURVIVAL, helper.absoluteVec(A_FEET), yaw);
	}

	private static float yawAt(Vec3 from, Vec3 at, float off) {
		return HimEntity.yawToward(from, at) + off;
	}

	private static void discard(HimEntity him) {
		if (!him.isRemoved()) {
			him.discard();
		}
	}

	/** A glimpse for A at the spot, facing A, watching exactly {@code everyone}, as the real one would stand there. */
	private static HimEntity glimpseFor(GameTestHelper helper, ServerPlayer a, List<ServerPlayer> everyone) {
		Vec3 feet = helper.absoluteVec(SPOT);
		HimEntity him = watchedBy(helper, Variant.GLIMPSE, SPOT, HimEntity.yawToward(feet, a.position()), everyone);
		him.appearFor(a.getUUID());
		him.setSpawnDistance(SpotFinder.horizontal(a.position(), feet));
		return him;
	}

	// --- the variant (D-073) ---

	/** The glimpse is no card, stands only where its caller says, never rushes, goes under or fakes, and vanishes. */
	@GameTest
	public void theGlimpseIsAVariantOfItsOwn(GameTestHelper helper) {
		Variant g = Variant.GLIMPSE;
		helper.assertTrue(!g.isCard() && g.spot() == Variant.Spot.GIVEN && g.ending() == Variant.Ending.VANISH && g.fake() == Variant.Fake.NONE,
				"the glimpse's shape: card " + g.isCard() + " spot " + g.spot() + " ending " + g.ending() + " fake " + g.fake());
		helper.assertFalse(g.mayRush() || g.mayGoUnder() || g.walksFromStart(), "the glimpse may rush, go under or walk off");
		helper.assertTrue(g.vanishesInView() && g.eyeStyle().equals(Optional.of(EyeStyle.FLAT)) && g.dimension() == net.minecraft.world.level.Level.OVERWORLD,
				"the glimpse does not vanish, is not FLAT or not in the overworld");
		for (Variant other : Variant.cards()) {
			helper.assertTrue(!other.vanishesInView() && other.eyeStyle().isEmpty(), other + " vanishes in view or has its own eyes");
		}
		helper.assertTrue(Services.figure() == GlimpseFigure.INSTANCE, "entity's Figure is not installed: " + Services.figure());
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		helper.assertTrue(FigureApi.spawnAtFogEdge(player, g, RandomSource.create(1), true).result() == FireResult.SKIPPED,
				"the glimpse stood at a fog edge");
		helper.assertTrue(SightingGates.check(player, helper.getLevel(), g).isPresent(), "the glimpse passed the sighting gates");
		helper.succeed();
	}

	// --- canGlimpse ---

	/**
	 * The spot in the dark tunnel, 16 blocks behind A who looks the other way, works; it does not with him out, in A's
	 * view, too close to A, in the wall, for a spectator, or once a torch lights it.
	 */
	@GameTest(structure = YARD, maxTicks = 100)
	public void canGlimpseRefusesInLightInViewTooCloseAndWhileOut(GameTestHelper helper) {
		tunnel(helper);
		Vec3 spot = helper.absoluteVec(SPOT);
		ServerPlayer away = a(helper, WEST);
		ServerPlayer looking = a(helper, EAST);
		ServerPlayer near = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(8.5, 1.0, 10.5)), WEST); // 11 blocks off
		ServerPlayer spectator = player(helper, GameType.SPECTATOR, helper.absoluteVec(A_FEET), WEST);
		Predicate<Vec3> loaded = loaded(helper);
		BlockPos feet = BlockPos.containing(spot);

		afterTheLightSettles(helper)
				.thenExecute(() -> {
					GlimpseFigure.Check ok = GlimpseFigure.check(away, spot, watching(helper, away), false, loaded);
					helper.assertTrue(ok.ok(), "the dark spot behind A is refused: " + ok.why());
					helper.assertTrue(GlimpseFigure.check(away, spot, watching(helper, away), true, loaded).result() == FireResult.NO_SPOT, "a glimpse while he is out");
					GlimpseFigure.Check inView = GlimpseFigure.check(looking, spot, watching(helper, looking), false, loaded);
					helper.assertTrue(inView.result() == FireResult.NO_SPOT && inView.why().contains("view"), "a glimpse in A's view: " + inView.why());
					GlimpseFigure.Check close = GlimpseFigure.check(near, spot, watching(helper, near), false, loaded);
					helper.assertTrue(close.result() == FireResult.NO_SPOT && close.why().contains("close"), "a glimpse 11 blocks from A: " + close.why());
					GlimpseFigure.Check wall = GlimpseFigure.check(away, spot.add(0.0, 0.0, 1.0), watching(helper, away), false, loaded);
					helper.assertTrue(wall.result() == FireResult.NO_SPOT && wall.why().contains("room"), "a glimpse in the wall: " + wall.why());
					GlimpseFigure.Check air = GlimpseFigure.check(away, spot.add(0.0, 1.0, 0.0), watching(helper, away), false, loaded);
					helper.assertTrue(!air.ok(), "a glimpse with his head in the ceiling");
					helper.assertTrue(GlimpseFigure.check(spectator, spot, watching(helper, spectator), false, loaded).result() == FireResult.SKIPPED,
							"a glimpse for a spectator");
					// 14 blocks is enough (from the eyes to his feet), 13 is not.
					helper.assertTrue(GlimpseFigure.farFromEveryone(watching(helper, away), spot, 14.0) && !GlimpseFigure.farFromEveryone(watching(helper, near), spot, 14.0),
							"the 14-block rule");

					// A torch on the floor beside the spot: once its light is there, the spot is too light.
					helper.setBlock(18, 1, 10, Blocks.TORCH);
				})
				.thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBrightness(LightLayer.BLOCK, feet) > 3, "the torch's light is not there yet"))
				.thenExecute(() -> {
					GlimpseFigure.Check lit = GlimpseFigure.check(away, spot, watching(helper, away), false, loaded);
					helper.assertTrue(lit.result() == FireResult.NO_SPOT && lit.why().contains("light"), "a glimpse in the torchlight: " + lit.why());
					helper.assertTrue(GlimpseRules.darkEnough(3, 3, 3) && !GlimpseRules.darkEnough(4, 0, 3) && !GlimpseRules.darkEnough(0, 4, 3),
							"light 3 or less at the feet and the eyes");
				})
				.thenSucceed();
	}

	/**
	 * Two players: the glimpse's spot must be hidden from the friend too. B, a step past A in the tunnel looking along
	 * it, sees the spot (A, looking away, does not), so there is no glimpse; a spectator's view counts the same; B
	 * close to it, looking away, refuses it too; B looking away far enough off lets it through.
	 */
	@GameTest(structure = YARD, maxTicks = 100)
	public void theGlimpseSpotIsHiddenFromTheFriendToo(GameTestHelper helper) {
		tunnel(helper);
		Vec3 spot = helper.absoluteVec(SPOT);
		// 15 blocks from the spot: inside the test server's short view distance ((0 + 1) * 16 blocks), past the 14.
		Vec3 bFeet = helper.absoluteVec(new Vec3(4.5, 1.0, 10.5));
		ServerPlayer a = a(helper, WEST);
		ServerPlayer bSees = player(helper, GameType.SURVIVAL, bFeet, EAST);
		ServerPlayer bSpectates = player(helper, GameType.SPECTATOR, bFeet, EAST);
		ServerPlayer bAway = player(helper, GameType.SURVIVAL, bFeet, WEST);
		ServerPlayer bNear = player(helper, GameType.SURVIVAL, helper.absoluteVec(new Vec3(9.5, 1.0, 10.5)), WEST);
		Predicate<Vec3> loaded = loaded(helper);

		afterTheLightSettles(helper)
				.thenExecute(() -> {
					helper.assertTrue(GlimpseFigure.check(a, spot, watching(helper, a), false, loaded).ok(), "A alone: refused, so this checks nothing");
					GlimpseFigure.Check seen = GlimpseFigure.check(a, spot, watching(helper, a, bSees), false, loaded);
					helper.assertTrue(seen.result() == FireResult.NO_SPOT && seen.why().contains("view"), "B sees the spot, yet: " + seen.why());
					helper.assertFalse(GlimpseFigure.check(a, spot, watching(helper, a, bSpectates), false, loaded).ok(), "a spectator's view does not count");
					helper.assertFalse(GlimpseFigure.check(a, spot, watching(helper, a, bNear), false, loaded).ok(), "B 10 blocks from the spot does not count");
					helper.assertTrue(GlimpseFigure.check(a, spot, watching(helper, a, bAway), false, loaded).ok(), "B far off, looking away, refuses it");
					FigureApi.Spawned refused = GlimpseFigure.glimpse(a, spot, Float.NaN, watching(helper, a, bSees), false, loaded);
					helper.assertTrue(refused.result() == FireResult.NO_SPOT && refused.figure() == null, "a glimpse spawned in B's view");
				})
				.thenSucceed();
	}

	// --- glimpse ---

	/**
	 * The glimpse spawns at the spot for A: FLAT eyes, vanishing in view, facing A, A's (appearedFor), on the record as
	 * a glimpse only (no surface sighting's place or variant).
	 */
	@GameTest(structure = YARD, maxTicks = 100)
	public void theGlimpseSpawnsFlatForThePlayer(GameTestHelper helper) {
		tunnel(helper);
		MinecraftServer server = helper.getLevel().getServer();
		Vec3 spot = helper.absoluteVec(SPOT);
		ServerPlayer a = a(helper, WEST);
		EntityData data = EntityData.get(server);
		afterTheLightSettles(helper)
				.thenExecute(() -> {
					int glimpses = data.glimpses();
					String lastVariant = data.lastVariant();
					int sightings = data.sightings();
					FigureApi.Spawned spawned = GlimpseFigure.glimpse(a, spot, Float.NaN, watching(helper, a), false, loaded(helper));
					HimEntity him = spawned.figure();
					try {
						helper.assertTrue(spawned.result() == FireResult.FIRED && him != null, "no glimpse: " + spawned.result());
						helper.assertTrue(him.variant() == Variant.GLIMPSE && him.eyeStyle().equals(Optional.of(EyeStyle.FLAT)) && him.vanishesInView()
								&& him.mayVanishInView(), "not a FLAT glimpse that vanishes: " + him.variant() + " " + him.eyeStyle());
						helper.assertTrue(a.getUUID().equals(him.appearedFor()), "not A's glimpse");
						helper.assertTrue(him.position().distanceTo(spot) < 1.0E-6, "not at the spot: " + him.position());
						float toward = HimEntity.yawToward(spot, a.position());
						helper.assertTrue(Math.abs(Mth.wrapDegrees(him.getYRot() - toward)) < 1.0F, "he does not face A: " + him.getYRot() + " vs " + toward);
						helper.assertTrue(data.glimpses() == glimpses + 1 && data.lastGlimpseDay() == GameClock.day(server), "the glimpse is not on the record");
						helper.assertTrue(data.sightings() == sightings && data.lastVariant().equals(lastVariant), "the glimpse counted as a surface sighting");
						// The caller's yaw is kept.
						discard(him);
						FigureApi.Spawned turned = GlimpseFigure.glimpse(a, spot, 45.0F, watching(helper, a), false, loaded(helper));
						helper.assertTrue(turned.figure() != null && Math.abs(Mth.wrapDegrees(turned.figure().getYRot() - 45.0F)) < 1.0F, "the caller's yaw was not kept");
						discard(turned.figure());
					} finally {
						if (him != null) {
							discard(him);
						}
					}
				})
				.thenSucceed();
	}

	/**
	 * Gone the tick A looks within 35 degrees of him: 50 degrees off he is in A's view and stays (a glimpse at the edge of
	 * the eye), 30 off he is gone at once, in view (the one figure allowed to). No stare counts. He never moves.
	 */
	@GameTest(structure = YARD, maxTicks = 120)
	public void theGlimpseIsGoneWhenLookedToward(GameTestHelper helper) {
		tunnel(helper);
		Vec3 aFeet = helper.absoluteVec(A_FEET);
		Vec3 spot = helper.absoluteVec(SPOT);
		ServerPlayer a = a(helper, WEST);
		HimEntity him = glimpseFor(helper, a, new CopyOnWriteArrayList<>(List.of(a)));
		helper.startSequence()
				.thenExecuteAfter(10, () -> {
					helper.assertTrue(!him.isRemoved() && !him.everSeen(), "gone or seen while A looks away: " + him.goneWhy());
					look(a, aFeet, yawAt(aFeet, spot, 50.0F));
				})
				.thenExecuteAfter(10, () -> {
					helper.assertTrue(him.everSeen() && !him.isRemoved(), "50 degrees off: not seen, or gone: " + him.goneWhy());
					look(a, aFeet, yawAt(aFeet, spot, 30.0F));
				})
				.thenExecuteAfter(2, () -> {
					helper.assertTrue(him.isRemoved() && String.valueOf(him.goneWhy()).contains("looked toward"), "30 degrees off, yet " + him.goneWhy());
					helper.assertTrue(him.mayVanishInView(), "a glimpse may not vanish in view");
					helper.assertFalse(him.stared() || him.triggered(), "a glimpse counted a stare or ended the surface way");
					helper.assertTrue(him.position().distanceTo(spot) < 0.05, "he moved: " + him.position());
				})
				.thenSucceed();
	}

	/** Gone once A has closed 2 blocks on him, even looking away; 1.5 blocks is not enough. Out of A's view. */
	@GameTest(structure = YARD, maxTicks = 100)
	public void theGlimpseIsGoneWhenThePlayerStepsTowardHim(GameTestHelper helper) {
		tunnel(helper);
		Vec3 aFeet = helper.absoluteVec(A_FEET);
		ServerPlayer a = a(helper, WEST);
		HimEntity him = glimpseFor(helper, a, new CopyOnWriteArrayList<>(List.of(a)));
		helper.startSequence()
				.thenExecuteAfter(5, () -> look(a, aFeet.add(1.5, 0.0, 0.0), WEST))
				.thenExecuteAfter(5, () -> {
					helper.assertFalse(him.isRemoved(), "gone after 1.5 blocks: " + him.goneWhy());
					look(a, aFeet.add(2.5, 0.0, 0.0), WEST);
				})
				.thenExecuteAfter(2, () -> {
					helper.assertTrue(him.isRemoved() && String.valueOf(him.goneWhy()).contains("stepped toward"), "2.5 blocks closer, yet " + him.goneWhy());
					helper.assertFalse(him.seenWhenRemoved(), "removed in A's view, who looked away");
				})
				.thenSucceed();
	}

	/** Never looked at, he is gone out of view after glimpseWaitSeconds (10), not before. */
	@GameTest(structure = YARD, maxTicks = 300)
	public void theGlimpseIsGoneOutOfViewAfterTheWait(GameTestHelper helper) {
		tunnel(helper);
		ServerPlayer a = a(helper, WEST);
		HimEntity him = glimpseFor(helper, a, new CopyOnWriteArrayList<>(List.of(a)));
		int wait = (int) ModConfig.realTicks(EntityConfig.get().glimpseWaitSeconds);
		helper.assertTrue(wait >= 100, "the wait is too short for this test: " + wait);
		helper.startSequence()
				.thenExecuteAfter(wait - 30, () -> helper.assertFalse(him.isRemoved(), "gone before the wait: " + him.goneWhy()))
				.thenWaitUntil(() -> helper.assertTrue(him.isRemoved(), "still there after the wait"))
				.thenExecute(() -> {
					helper.assertTrue(String.valueOf(him.goneWhy()).contains("out of view") && !him.seenWhenRemoved(), "gone in view: " + him.goneWhy());
					helper.assertTrue(him.age() >= wait && him.age() <= wait + 3, "gone after " + him.age() + " ticks, the wait is " + wait);
				})
				.thenSucceed();
	}

	/**
	 * Seen at the edge of A's eye (60 degrees off, never within 35), he is gone once seen for glimpseSeenSeconds (3):
	 * a glimpse, never a long look.
	 */
	@GameTest(structure = YARD, maxTicks = 140)
	public void theGlimpseIsGoneOnceSeenForAMoment(GameTestHelper helper) {
		tunnel(helper);
		Vec3 aFeet = helper.absoluteVec(A_FEET);
		ServerPlayer a = player(helper, GameType.SURVIVAL, aFeet, yawAt(aFeet, helper.absoluteVec(SPOT), 60.0F));
		HimEntity him = glimpseFor(helper, a, new CopyOnWriteArrayList<>(List.of(a)));
		int seen = (int) ModConfig.realTicks(EntityConfig.get().glimpseSeenSeconds);
		helper.startSequence()
				.thenExecuteAfter(seen - 10, () -> helper.assertTrue(!him.isRemoved() && him.everSeen(), "gone early, or never seen: " + him.goneWhy()))
				.thenWaitUntil(() -> helper.assertTrue(him.isRemoved(), "still there, seen for " + him.seenTicks()))
				.thenExecute(() -> helper.assertTrue(String.valueOf(him.goneWhy()).contains("seen long enough") && him.seenTicks() <= seen + 1,
						"gone for " + him.goneWhy() + " after " + him.seenTicks() + " ticks seen"))
				.thenSucceed();
	}

	/**
	 * Someone else's look counts only if they can see him: B behind the rock, looking straight at him, changes nothing;
	 * once A (his) leaves he is gone at once. Told to walk away, he is simply gone too, never walking.
	 */
	@GameTest(structure = YARD, maxTicks = 100)
	public void theGlimpseGoesWhenHisPlayerLeavesAndOnlySeenLooksCount(GameTestHelper helper) {
		tunnel(helper);
		Vec3 spot = helper.absoluteVec(SPOT);
		ServerPlayer a = a(helper, WEST);
		Vec3 bFeet = helper.absoluteVec(new Vec3(19.5, 1.0, 1.5)); // outside the stone, 9 blocks off sideways, through rock
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, yawAt(bFeet, spot, 0.0F));
		List<ServerPlayer> everyone = new CopyOnWriteArrayList<>(List.of(a, b));
		HimEntity him = glimpseFor(helper, a, everyone);
		HimEntity told = glimpseFor(helper, a, new CopyOnWriteArrayList<>(List.of(a))); // a second test figure on the same spot
		helper.startSequence()
				.thenExecuteAfter(5, () -> {
					helper.assertFalse(him.isRemoved(), "B, behind the rock, looked him away: " + him.goneWhy());
					FigureApi.walkAway(told);
				})
				.thenExecuteAfter(2, () -> {
					helper.assertTrue(told.isRemoved() && String.valueOf(told.goneWhy()).contains("told to go") && told.phase() != HimEntity.Phase.LEAVING,
							"told to walk away, the glimpse " + told.phase() + ": " + told.goneWhy());
					everyone.remove(a); // A leaves
				})
				.thenExecuteAfter(2, () -> helper.assertTrue(him.isRemoved() && String.valueOf(him.goneWhy()).contains("appeared for is gone"),
						"A left, yet " + him.goneWhy()))
				.thenSucceed();
	}

	// --- the client's hide latch (pure) ---

	/**
	 * The renderer stops drawing him within 28 degrees of the camera's look or within 10 blocks, never otherwise: at the
	 * edge of a 70-degree screen (about 50 degrees off) he is drawn, near its middle never.
	 */
	@GameTest
	public void theClientHidesHimNearTheMiddleOfTheScreen(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		double hide = config.glimpseHideDegrees();
		double blocks = config.glimpseHideBlocks;
		helper.assertTrue(hide == 28.0 && blocks == 10.0 && config.glimpseServerDegrees() == 35.0, "D-073's numbers: " + hide + " " + blocks);
		Vec3 camera = new Vec3(0.0, 65.62, 0.0);
		Vec3 look = new Vec3(1.0, 0.0, 0.0);
		for (double degrees : new double[] {0.0, 10.0, 27.5, 28.0}) {
			Vec3 center = camera.add(turnedFromX(degrees).scale(16.0));
			helper.assertTrue(GlimpseRules.hiddenOnClient(camera, look, center, hide, blocks), "drawn " + degrees + " degrees off the look");
		}
		for (double degrees : new double[] {28.5, 35.0, 50.0, 90.0, 180.0}) {
			Vec3 center = camera.add(turnedFromX(degrees).scale(16.0));
			helper.assertFalse(GlimpseRules.hiddenOnClient(camera, look, center, hide, blocks), "hidden " + degrees + " degrees off the look");
		}
		helper.assertTrue(GlimpseRules.hiddenOnClient(camera, look, camera.add(turnedFromX(150.0).scale(9.5)), hide, blocks), "drawn 9.5 blocks away");
		helper.assertTrue(Math.abs(GlimpseRules.degreesOff(camera, look.scale(3.0), camera.add(0.0, 5.0, 5.0)) - 90.0) < 1.0E-9, "a look of any length");
		// The server's 35 degrees take him first: everything the client would hide, the server removes.
		helper.assertTrue(config.glimpseServerDegrees() > hide, "the server's angle is not wider than the client's");
		helper.assertTrue(GlimpseRules.closedOn(16.0, 14.0, 2.0) && !GlimpseRules.closedOn(16.0, 14.5, 2.0) && !GlimpseRules.closedOn(Double.NaN, 1.0, 2.0),
				"the 2-block approach");
		helper.succeed();
	}

	private static Vec3 turnedFromX(double degrees) {
		double r = Math.toRadians(degrees);
		return new Vec3(Math.cos(r), 0.0, Math.sin(r));
	}

	// --- the eye style per figure ---

	/** The eye style is the figure's own when he has one (synced data), else the configured one. */
	@GameTest(structure = YARD)
	public void eachFigureCarriesItsOwnEyeStyle(GameTestHelper helper) {
		tunnel(helper);
		ServerPlayer a = a(helper, WEST);
		HimEntity ridge = watchedBy(helper, Variant.RIDGE, new Vec3(12.5, 1.0, 10.5), 0.0F, List.of(a));
		HimEntity glimpse = glimpseFor(helper, a, List.of(a));
		try {
			helper.assertTrue(ridge.eyeStyle().isEmpty() && !ridge.vanishesInView() && !ridge.mayVanishInView(), "the ridge has its own eyes or vanishes");
			helper.assertTrue(glimpse.eyeStyle().equals(Optional.of(EyeStyle.FLAT)) && glimpse.vanishesInView(), "the glimpse is not FLAT");
			ridge.setEyeStyle(EyeStyle.GLOW);
			helper.assertTrue(ridge.eyeStyle().equals(Optional.of(EyeStyle.GLOW)), "the style did not stick");
			ridge.setEyeStyle(null);
			helper.assertTrue(ridge.eyeStyle().isEmpty(), "null is not the configured style");
		} finally {
			discard(ridge);
			discard(glimpse);
		}
		helper.succeed();
	}

	// --- fog days (D-071) ---

	/**
	 * A fog day or a morning carry-over is not clear daylight; in that daytime fog the cow and close come (and the others
	 * of the list) while the ridge and the light stay out. Without a spell clear daylight still blocks every one, and
	 * rain still lets every one through as before.
	 */
	@GameTest
	public void daytimeFogLetsSomeSightingsThrough(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		long day = 7;
		FogSpell fogDay = new FogSpell(FogWeatherRules.dayFrom(day), FogWeatherRules.dayUntil(day), 0.5F, false);
		FogSpell carryOver = new FogSpell(FogWeatherRules.nightFrom(day - 1), FogWeatherRules.nightUntil(day - 1, true), 0.6F, true);
		long noon = day * 24000L + 6000L;
		long morning = day * 24000L + 3000L;
		helper.assertTrue(SightingRules.fogWeather(Optional.of(fogDay), noon) && SightingRules.fogWeather(Optional.of(carryOver), morning),
				"the spells are not on");
		helper.assertFalse(SightingRules.fogWeather(Optional.of(carryOver), noon) || SightingRules.fogWeather(Optional.empty(), noon),
				"a spell that is over (or none) counts as fog");
		for (long clock : new long[] {noon, morning}) {
			long time = clock % 24000L;
			boolean fog = SightingRules.fogWeather(Optional.of(clock == noon ? fogDay : carryOver), clock);
			helper.assertFalse(SightingRules.clearDaylight(time, false, fog, config), "daytime fog counts as clear daylight at " + time);
			helper.assertTrue(SightingRules.daytimeFog(time, false, fog, config), "no daytime fog at " + time);
			for (Variant variant : Variant.cards()) {
				if (variant.dimension() != net.minecraft.world.level.Level.OVERWORLD || variant == Variant.LAST_ONE) {
					continue;
				}
				Optional<String> why = SightingRules.daylight(variant, time, false, fog, config);
				boolean listed = variant == Variant.COW || variant == Variant.WALKS_AWAY || variant == Variant.BETWEEN_TRUNKS
						|| variant == Variant.ACROSS_WATER || variant == Variant.CLOSE;
				helper.assertTrue(why.isEmpty() == listed, variant + " in daytime fog at " + time + ": " + why.orElse("may come"));
			}
		}
		helper.assertTrue(SightingRules.daylight(Variant.RIDGE, 6000, false, true, config).orElse("").equals("not in daytime fog"), "the ridge in daytime fog");
		for (Variant variant : Variant.cards()) {
			helper.assertTrue(SightingRules.daylight(variant, 6000, false, false, config).orElse("").equals("clear daylight"),
					variant + " in clear daylight without a spell");
			helper.assertTrue(SightingRules.daylight(variant, 6000, true, false, config).isEmpty(), variant + " refused in the rain at noon");
			helper.assertTrue(SightingRules.daylight(variant, 18000, false, true, config).isEmpty(), variant + " refused on a fog night");
		}
		helper.assertTrue(SightingRules.clearDaylight(6000, false, false, config) == SightingRules.clearDaylight(6000, false, config),
				"without a spell the rule changed");
		helper.succeed();
	}

	/**
	 * A sighting that came in daytime fog walks away once the fog stops counting and the day is clear (D-071; review R2:
	 * a morning carry-over lifting under him): unseen, he turns to leave and is gone out of view at once; seen, he turns
	 * and walks, never gone in view; one that came under another sky (dusk, rain) is left alone by the same clear day.
	 * The sky is each figure's own here, never the shared server's clock and spell.
	 */
	@GameTest(structure = YARD, maxTicks = 100)
	public void aDaytimeFogSightingWalksAwayAsTheFogLifts(GameTestHelper helper) {
		tunnel(helper);
		Vec3 aFeet = helper.absoluteVec(A_FEET);
		ServerPlayer away = a(helper, WEST);
		ServerPlayer looking = a(helper, EAST);
		AtomicReference<SightingRules.Sky> fogSky = new AtomicReference<>(SightingRules.Sky.DAYTIME_FOG);
		AtomicReference<SightingRules.Sky> duskSky = new AtomicReference<>(SightingRules.Sky.OTHER);
		Vec3 unseenAt = new Vec3(15.5, 1.0, 10.5);
		Vec3 seenAt = new Vec3(17.5, 1.0, 10.5);
		Vec3 duskAt = new Vec3(13.5, 1.0, 10.5);
		HimEntity unseen = watchedBy(helper, Variant.COW, unseenAt, HimEntity.yawToward(helper.absoluteVec(unseenAt), aFeet), List.of(away), false, fogSky::get);
		HimEntity seen = watchedBy(helper, Variant.COW, seenAt, HimEntity.yawToward(helper.absoluteVec(seenAt), aFeet), List.of(looking), false, fogSky::get);
		HimEntity dusk = watchedBy(helper, Variant.COW, duskAt, HimEntity.yawToward(helper.absoluteVec(duskAt), aFeet), List.of(away), false, duskSky::get);
		helper.assertTrue(SightingRules.fogLifted(SightingRules.Sky.DAYTIME_FOG, SightingRules.Sky.CLEAR_DAYLIGHT)
				&& !SightingRules.fogLifted(SightingRules.Sky.DAYTIME_FOG, SightingRules.Sky.DAYTIME_FOG)
				&& !SightingRules.fogLifted(SightingRules.Sky.DAYTIME_FOG, SightingRules.Sky.OTHER)
				&& !SightingRules.fogLifted(SightingRules.Sky.OTHER, SightingRules.Sky.CLEAR_DAYLIGHT), "the pure rule");
		helper.startSequence()
				.thenExecuteAfter(10, () -> {
					for (HimEntity him : List.of(unseen, seen, dusk)) {
						helper.assertTrue(!him.isRemoved() && !him.triggered(), "gone or leaving in the fog: " + him.goneWhy());
					}
					helper.assertTrue(unseen.cameUnder() == SightingRules.Sky.DAYTIME_FOG && seen.cameUnder() == SightingRules.Sky.DAYTIME_FOG
							&& dusk.cameUnder() == SightingRules.Sky.OTHER, "the skies they came under: " + unseen.cameUnder() + " " + dusk.cameUnder());
					helper.assertTrue(seen.everSeen() && !unseen.everSeen(), "the seen one is not seen, or the other is");
					fogSky.set(SightingRules.Sky.CLEAR_DAYLIGHT); // the carry-over lifts: the day is clear
					duskSky.set(SightingRules.Sky.CLEAR_DAYLIGHT);
				})
				.thenExecuteAfter(2, () -> {
					try {
						helper.assertTrue(unseen.isRemoved() && String.valueOf(unseen.goneWhy()).contains("out of view") && !unseen.seenWhenRemoved(),
								"unseen, the fog gone, yet " + unseen.goneWhy());
						helper.assertTrue(!seen.isRemoved() && seen.triggered() && seen.phase() == HimEntity.Phase.LEAVING,
								"seen, the fog gone: not walking away, or gone in view: " + seen.phase() + " " + seen.goneWhy());
						helper.assertTrue(!dusk.isRemoved() && !dusk.triggered() && dusk.phase() == HimEntity.Phase.IDLE,
								"one who came at dusk left with the daytime fog: " + dusk.phase() + " " + dusk.goneWhy());
					} finally {
						discard(unseen);
						discard(seen);
						discard(dusk);
					}
				})
				.thenSucceed();
	}

	/**
	 * The band's server estimate follows the fog spell (core's FogLimits with the spell): a fog day pulls the fog end in
	 * at noon, where the dusk fog alone gives nothing; the live estimate is FogLimits.of(player) exactly.
	 */
	@GameTest
	public void theBandFollowsTheFogSpellWithoutAReport(GameTestHelper helper) {
		EntityConfig config = new EntityConfig();
		long day = 4;
		long noon = day * 24000L + 6000L;
		FogSpell fogDay = new FogSpell(FogWeatherRules.dayFrom(day), FogWeatherRules.dayUntil(day), 0.6F, false);
		FogEdge clear = FogEdge.compute(FogLimits.of(12, 12, 0.15F, noon, Optional.empty()), 10, Double.NaN, false, config);
		FogEdge foggy = FogEdge.compute(FogLimits.of(12, 12, 0.15F, noon, Optional.of(fogDay)), 10, Double.NaN, false, config);
		helper.assertTrue(foggy.estimate() < clear.estimate() - 20.0 && foggy.limit() == foggy.estimate(), "the fog day did not pull the band in: " + clear
				+ " -> " + foggy);
		helper.assertTrue(foggy.outer() < clear.outer(), "the band did not come closer with the fog");
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ReportedFog.set(player, Double.NaN);
		helper.assertTrue(FogEdge.of(player).estimate() == FogLimits.of(player).fogEnd(), "the live estimate is not FogLimits.of(player)");
		helper.succeed();
	}

	// --- config (D-075) ---

	/**
	 * A version 3 section (v1.1) reads as the current version (4 for v1.2, 5 since the trunk fix): the glimpse's new values
	 * at their defaults, every value the player set kept; a fresh section changes nothing but its version.
	 */
	@GameTest
	public void aVersion3EntitySectionUpgradesToTheCurrentVersion(GameTestHelper helper) {
		JsonObject sections = JsonParser.parseString("{\"entity\": {\"version\": 3, \"approachBlocks\": 7.0, \"closeMinDistance\": 20.0,"
				+ " \"eyeStyle\": \"GLOW\", \"minDistance\": 10.0}}").getAsJsonObject();
		EntityConfig read = ModConfig.readSection(sections, "entity", EntityConfig.class, EntityConfig::new);
		EntityConfig defaults = new EntityConfig();
		helper.assertTrue(read.version == EntityConfig.CURRENT_VERSION && EntityConfig.CURRENT_VERSION == 5, "version " + read.version);
		helper.assertTrue(read.approachBlocks == 7.0 && read.closeMinDistance == 20.0 && read.eyeStyle() == EyeStyle.GLOW && read.minDistance == 10.0,
				"a value the player set was lost: " + read.approachBlocks + " " + read.closeMinDistance + " " + read.eyeStyle());
		helper.assertTrue(read.glimpseMinDistance == 14 && read.glimpseHideDegrees == 28 && read.glimpseHideBlocks == 10 && read.glimpseServerDegrees == 35
				&& read.glimpseApproachBlocks == 2 && read.glimpseSeenSeconds == 3 && read.glimpseWaitSeconds == 10 && read.glimpseMaxLight == 3,
				"the glimpse's defaults are missing");
		helper.assertTrue(read.glimpseMinDistance == defaults.glimpseMinDistance && read.stareSeconds == defaults.stareSeconds, "a default moved");
		EntityConfig fresh = new EntityConfig();
		helper.assertTrue(fresh.upgrade() && fresh.version == EntityConfig.CURRENT_VERSION && fresh.approachBlocks == defaults.approachBlocks
				&& fresh.closeMinDistance == defaults.closeMinDistance, "a fresh section changed");
		helper.assertFalse(read.upgrade(), "upgraded twice");
		// Never under 8 blocks, whatever the file says.
		EntityConfig bad = new EntityConfig();
		bad.glimpseMinDistance = 3;
		bad.glimpseHideDegrees = Double.NaN;
		helper.assertTrue(bad.glimpseMinDistance() == EntityConfig.MIN_DISTANCE_FLOOR && bad.glimpseHideDegrees() == 28.0, "bad glimpse values not tamed");
		helper.succeed();
	}

	// --- review R3 (v1.2 bug hunt): each fails on main ---

	/**
	 * R3: D-073 removes him when "the player he appeared for ... steps 2 blocks toward him". A friend 120 blocks away,
	 * who cannot see him and is nowhere near, walking 3 blocks his general way must not take the glimpse away before
	 * A (looking the other way) has even turned. On main {@code glimpseStep} applies the approach rule to every player
	 * in the level, so any moving friend anywhere in the overworld ends the glimpse within half a second.
	 */
	@GameTest(structure = YARD, maxTicks = 100)
	public void r3AFarFriendWalkingAboutLeavesTheGlimpseAlone(GameTestHelper helper) {
		tunnel(helper);
		ServerPlayer a = a(helper, WEST);
		Vec3 bFeet = helper.absoluteVec(new Vec3(19.5, 1.0, 130.5)); // 120 blocks south of the spot, looking south (away)
		ServerPlayer b = player(helper, GameType.SURVIVAL, bFeet, 0.0F);
		List<ServerPlayer> everyone = new CopyOnWriteArrayList<>(List.of(a, b));
		HimEntity him = glimpseFor(helper, a, everyone);
		helper.startSequence()
				.thenExecuteAfter(5, () -> {
					helper.assertFalse(him.isRemoved(), "gone before anyone moved: " + him.goneWhy());
					look(b, bFeet.add(0.0, 0.0, -3.0), 0.0F); // B walks 3 blocks north, still 117 away and looking away
				})
				.thenExecuteAfter(3, () -> {
					boolean removed = him.isRemoved();
					String why = String.valueOf(him.goneWhy());
					discard(him);
					helper.assertFalse(removed, "a friend 117 blocks away who never saw him stepped 3 blocks his way and the glimpse was gone: " + why);
				})
				.thenSucceed();
	}

	/**
	 * R3: D-073 "past the last torch he is a dim shape" ("underground the dark stands in for the fog"). The spot's
	 * darkness is only block light: a spot under the player's own shaft open to the sky (sky light 15, full daylight at
	 * noon) passes {@code canGlimpse}, so he can stand lit by the sun at the bottom of a shaft or near a tunnel mouth.
	 */
	@GameTest(structure = YARD, maxTicks = 100)
	public void r3TheGlimpseNeverStandsInDaylight(GameTestHelper helper) {
		tunnel(helper);
		helper.setBlock(19, 3, 10, Blocks.AIR); // a 1x1 shaft from the spot up to the sky
		helper.setBlock(19, 4, 10, Blocks.AIR);
		Vec3 spot = helper.absoluteVec(SPOT);
		BlockPos feet = BlockPos.containing(spot);
		ServerPlayer away = a(helper, WEST);
		BlockPos sealed = helper.absolutePos(new BlockPos(4, 1, 10)); // a tunnel cell 15 from the shaft: dark once the light has settled
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBrightness(LightLayer.SKY, feet) >= 14
						&& helper.getLevel().getBrightness(LightLayer.SKY, sealed) <= 3, "the light has not settled yet: sky "
						+ helper.getLevel().getBrightness(LightLayer.SKY, feet) + " at the spot, " + helper.getLevel().getBrightness(LightLayer.SKY, sealed)
						+ " under the rock"))
				.thenExecute(() -> {
					int sky = helper.getLevel().getBrightness(LightLayer.SKY, feet);
					int block = helper.getLevel().getBrightness(LightLayer.BLOCK, feet);
					GlimpseFigure.Check check = GlimpseFigure.check(away, spot, watching(helper, away), false, loaded(helper));
					helper.assertFalse(check.ok(), "the glimpse may stand in daylight: sky light " + sky + ", block light " + block + " at his feet");
				})
				.thenSucceed();
	}
}
