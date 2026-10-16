package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.Vec3;

/**
 * The screenshot (D-078), what the game tests reach: the card and its gates, arming and disarming (the time, leaving,
 * pausing, the fog flag), the report (once per world, the day, everyone disarmed), the record through a save, the
 * payloads, and the spot's pure math (the fog band, off the middle, inside the frame, ground or none) with the level's
 * ground and sight rules. The frame itself (him in the PNG, never on the screen) is the client's: see the dev client
 * check list in the report. Each test makes its own {@link TheScreenshot} on its own {@link Hooks} (a fresh
 * {@link EntityData}, a stage, a clock, the players online and a recorder of what was sent), so nothing here reads or
 * changes the shared test server's story. {@link EntityGameTests} extends this (through {@link TwoPlayerFigureGameTests}).
 */
public class ScreenshotGameTests {
	private static final long DAY = 24000L;

	/** What a test's screenshot reads, and every state it sent, in order. */
	static final class Hooks implements TheScreenshot.Hooks {
		record Sent(UUID player, ScreenshotStatePayload payload) {
		}

		final EntityData data = new EntityData();
		final Map<UUID, ServerPlayer> online = new HashMap<>();
		final List<Sent> sent = new ArrayList<>();
		Stage stage = Stage.TRACES;
		boolean lastMinute;
		boolean out;
		boolean fog;
		boolean canSend = true;
		long dayTicks = 3 * DAY + 1000;

		Hooks with(ServerPlayer... players) {
			for (ServerPlayer player : players) {
				online.put(player.getUUID(), player);
			}
			return this;
		}

		List<ScreenshotStatePayload> to(ServerPlayer player) {
			return sent.stream().filter(s -> s.player().equals(player.getUUID())).map(Sent::payload).toList();
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
		public boolean lastMinute(MinecraftServer server) {
			return lastMinute;
		}

		@Override
		public boolean heIsOut(MinecraftServer server) {
			return out;
		}

		@Override
		public long dayTicks(MinecraftServer server) {
			return dayTicks;
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(online.get(player));
		}

		@Override
		public boolean fogOn(ServerPlayer player) {
			return fog;
		}

		@Override
		public boolean send(ServerPlayer player, ScreenshotStatePayload payload) {
			if (!canSend) {
				return false;
			}
			sent.add(new Sent(player.getUUID(), payload));
			return true;
		}
	}

	private static ServerPlayer mock(GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	private static FireResult fire(EventCard card, ServerPlayer player, ServerLevel level, boolean forced) {
		return card.fire(new FireContext(player, level, false, forced, RandomSource.create(5)));
	}

	private static final ScreenshotStatePayload READY = new ScreenshotStatePayload(true, false);

	// --- the card ---

	/** {@code the_screenshot} is registered: MINOR, Traces, SIGHTING, WATCHER like every sighting, no fake. */
	@GameTest
	public void theScreenshotIsAMinorTracesSightingCard(GameTestHelper helper) {
		Optional<EventCard> registered = CardRegistry.get(TheScreenshot.CARD_ID);
		helper.assertTrue(registered.isPresent() && registered.get() instanceof ScreenshotCard, "the_screenshot is not registered");
		EventCard card = registered.get();
		helper.assertTrue(card.tier() == Tier.MINOR && card.earliestStage() == Stage.TRACES, "tier " + card.tier() + ", from " + card.earliestStage());
		helper.assertTrue(card.tags().equals(Set.of(CardTag.SIGHTING)) && card.habits().equals(Set.of(Habit.WATCHER)) && !card.hasFake(),
				"tags " + card.tags() + ", habits " + card.habits() + ", fake " + card.hasFake());
		helper.succeed();
	}

	/** The gate: the overworld, from Traces, once per world, not armed already, alive and playing, not in Ending D's last minute. */
	@GameTest
	public void theGateFitsOnlyInTheOverworldFromTracesOncePerWorld(GameTestHelper helper) {
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, true, false, false).isEmpty(), "the plain case is refused");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.REMOVAL, false, false, true, false, false).isEmpty(), "Removal is refused");
		helper.assertTrue(ScreenshotRules.gate(false, Stage.TRACES, false, false, true, false, false).orElse("").equals("not in the overworld"),
				"the Nether or the End fit");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.ALONE, false, false, true, false, false).orElse("").startsWith("before"), "Alone fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, true, false, true, false, false).orElse("").contains("already happened"),
				"a second screenshot in the world fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, true, true, false, false).orElse("").equals("already armed"), "re-arming fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, false, false, false).isPresent(), "a dead player fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, true, true, false).isPresent(), "a spectator fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, true, false, true).isPresent(), "Ending D's last minute fits");
		helper.succeed();
	}

	/**
	 * Firing arms the player's client (FIRED, the arm sent) and the card stops fitting for them; it fits for nobody at
	 * Alone. A client that cannot take the payload is never armed (SKIPPED).
	 */
	@GameTest
	public void firingArmsThatPlayersClientOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		Hooks hooks = new Hooks().with(a, b);
		hooks.fog = true;
		TheScreenshot shot = new TheScreenshot(hooks);
		ScreenshotCard card = new ScreenshotCard(shot);
		helper.assertTrue(card.contextFits(a, level), "the card does not fit A: " + shot.gate(a, level));
		helper.assertTrue(fire(card, a, level, false) == FireResult.FIRED, "the card did not fire");
		helper.assertTrue(shot.isArmed(a.getUUID()) && !shot.isArmed(b.getUUID()), "armed " + shot.armed());
		helper.assertTrue(hooks.to(a).equals(List.of(new ScreenshotStatePayload(true, true))) && hooks.to(b).isEmpty(), "sent " + hooks.sent);
		helper.assertTrue(shot.ticksLeft(level.getServer(), a.getUUID()) == EntityConfig.get().screenshotArmTicks()
				&& EntityConfig.get().screenshotArmTicks() == 2 * DAY, "the arm is not 2 in-game days: " + shot.ticksLeft(level.getServer(), a.getUUID()));
		helper.assertFalse(card.contextFits(a, level), "the card fits A again while armed");
		helper.assertTrue(card.contextFits(b, level), "the card does not fit B: " + shot.gate(b, level));
		helper.assertTrue(card.fire(new FireContext(b, level, true, false, RandomSource.create(1))) == FireResult.SKIPPED, "a fake fired");

		hooks.stage = Stage.ALONE;
		helper.assertFalse(card.contextFits(b, level), "the card fits in Alone");
		helper.assertTrue(fire(card, b, level, false) == FireResult.SKIPPED && !shot.isArmed(b.getUUID()), "the card armed B in Alone");

		Hooks deaf = new Hooks().with(a);
		deaf.canSend = false;
		TheScreenshot unheard = new TheScreenshot(deaf);
		helper.assertTrue(fire(new ScreenshotCard(unheard), a, level, false) == FireResult.SKIPPED && unheard.armed().isEmpty(),
				"a client that cannot take the payload was armed");
		helper.succeed();
	}

	/** The arm runs out after 2 in-game days (sleeping and timewarp count): the client is told, and the card fits again. */
	@GameTest
	public void theArmRunsOutAfterTwoInGameDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer a = mock(helper);
		Hooks hooks = new Hooks().with(a);
		TheScreenshot shot = new TheScreenshot(hooks);
		long from = hooks.dayTicks;
		helper.assertTrue(shot.arm(a) == FireResult.FIRED, "not armed");
		hooks.dayTicks = from + 2 * DAY - 1;
		shot.check(server);
		helper.assertTrue(shot.isArmed(a.getUUID()) && hooks.to(a).equals(List.of(READY)), "the arm ended early: " + hooks.sent);
		hooks.dayTicks = from + 2 * DAY;
		shot.check(server);
		helper.assertFalse(shot.isArmed(a.getUUID()), "the arm outlived 2 in-game days");
		helper.assertTrue(hooks.to(a).equals(List.of(READY, ScreenshotStatePayload.OFF)), "the client was not told: " + hooks.sent);
		helper.assertTrue(shot.gate(a, level).isEmpty(), "the card does not fit again: " + shot.gate(a, level));
		helper.assertTrue(hooks.data.screenshotTaken() == false, "running out recorded a screenshot");
		helper.succeed();
	}

	/** Leaving ends the arm (PARTICIPATION false, or gone from the server); turning spectator does, and that client is told. */
	@GameTest
	public void leavingOrSpectatingDisarms(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		TwoPlayerFigureGameTests.TestPlayer c = new TwoPlayerFigureGameTests.TestPlayer(helper.getLevel(), GameType.SURVIVAL);
		Hooks hooks = new Hooks().with(a, b, c);
		TheScreenshot shot = new TheScreenshot(hooks);
		shot.arm(a);
		shot.arm(b);
		shot.arm(c);
		shot.onParticipation(server, a.getUUID(), true);
		helper.assertTrue(shot.isArmed(a.getUUID()), "taking part disarmed");
		shot.onParticipation(server, a.getUUID(), false);
		helper.assertFalse(shot.isArmed(a.getUUID()), "leaving kept the arm");
		helper.assertTrue(shot.isArmed(b.getUUID()) && shot.isArmed(c.getUUID()), "one leaving disarmed the others");

		hooks.online.remove(b.getUUID()); // gone without a PARTICIPATION event reaching us
		int sentBefore = hooks.sent.size();
		shot.check(server);
		helper.assertFalse(shot.isArmed(b.getUUID()), "a player gone from the server kept the arm");
		helper.assertTrue(hooks.sent.size() == sentBefore, "something was sent to a player who is gone");

		c.mode = GameType.SPECTATOR;
		shot.check(server);
		helper.assertFalse(shot.isArmed(c.getUUID()), "a spectator kept the arm");
		helper.assertTrue(hooks.to(c).getLast().equals(ScreenshotStatePayload.OFF), "the spectator's client was not told");
		helper.succeed();
	}

	/**
	 * While armed, the client is told again only when what it may do changes: paused while he is out or in Ending D's
	 * last minute, and the fog flag. Nothing is sent when nothing changed.
	 */
	@GameTest
	public void theClientIsToldWhenHeIsOutOrTheFogChanges(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = mock(helper);
		Hooks hooks = new Hooks().with(a);
		TheScreenshot shot = new TheScreenshot(hooks);
		shot.arm(a);
		shot.check(server);
		helper.assertTrue(hooks.to(a).equals(List.of(READY)), "sent with nothing changed: " + hooks.sent);
		hooks.fog = true;
		shot.check(server);
		hooks.out = true;
		shot.check(server);
		hooks.out = false;
		hooks.lastMinute = true;
		shot.check(server);
		shot.check(server);
		hooks.lastMinute = false;
		hooks.fog = false;
		shot.check(server);
		// The fog came, he went out (paused), the last minute took over (still paused: nothing new), then both ended with the fog gone.
		List<ScreenshotStatePayload> told = hooks.to(a);
		helper.assertTrue(told.equals(List.of(READY, new ScreenshotStatePayload(true, true), new ScreenshotStatePayload(false, true), READY)),
				"told " + told);
		helper.assertTrue(shot.told(a.getUUID()).orElseThrow().equals(READY) && shot.isArmed(a.getUUID()), "a pause ended the arm");
		helper.assertTrue(shot.gate(a, helper.getLevel()).orElse("").equals("already armed"), "a paused arm can be armed again");
		hooks.lastMinute = true;
		ServerPlayer b = mock(helper);
		helper.assertTrue(shot.gate(b, helper.getLevel()).isPresent(), "a new arm in Ending D's last minute");
		helper.succeed();
	}

	/**
	 * The report: from a player who is not armed it is ignored; from an armed one it records the world's screenshot
	 * (the day, the player), counts the day as a sighting (no other sighting that day), and disarms everyone. Then the
	 * card fits for nobody; a debug fire arms again, and its report keeps the first record.
	 */
	@GameTest
	public void theReportRecordsOncePerWorldAndDisarmsEveryone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		ServerPlayer stranger = mock(helper);
		Hooks hooks = new Hooks().with(a, b, stranger);
		TheScreenshot shot = new TheScreenshot(hooks);
		ScreenshotCard card = new ScreenshotCard(shot);
		long today = hooks.dayTicks / DAY;
		helper.assertTrue(fire(card, a, level, false) == FireResult.FIRED && fire(card, b, level, false) == FireResult.FIRED, "not armed");
		helper.assertTrue(shot.report(stranger) == TheScreenshot.Report.IGNORED, "a report from a client that was not armed counted");
		helper.assertFalse(hooks.data.screenshotTaken(), "an unarmed report recorded the screenshot");
		helper.assertTrue(hooks.data.dayAllows(today, 1), "an unarmed report counted the day");
		helper.assertTrue(shot.isArmed(a.getUUID()) && shot.isArmed(b.getUUID()), "an unarmed report disarmed someone");

		helper.assertTrue(shot.report(b) == TheScreenshot.Report.RECORDED, "B's report was not recorded");
		helper.assertTrue(hooks.data.screenshotTaken() && hooks.data.screenshotDay() == today && hooks.data.screenshotBy().equals(Optional.of(b.getUUID())),
				"recorded day " + hooks.data.screenshotDay() + " by " + hooks.data.screenshotBy());
		helper.assertFalse(hooks.data.dayAllows(today, 1), "the screenshot did not count as the day's sighting");
		helper.assertTrue(hooks.data.dayAllows(today + 1, 1), "it counted for the next day");
		helper.assertTrue(hooks.data.lastPos() == null && hooks.data.lastVariant().isEmpty() && hooks.data.sightings() == 0,
				"it counted for the place or the variant rule");
		helper.assertTrue(shot.armed().isEmpty(), "someone is still armed: " + shot.armed());
		helper.assertTrue(hooks.to(a).getLast().equals(ScreenshotStatePayload.OFF) && hooks.to(b).getLast().equals(ScreenshotStatePayload.OFF),
				"not everyone was told: " + hooks.sent);
		helper.assertTrue(shot.report(b) == TheScreenshot.Report.IGNORED, "a second report from B counted");
		helper.assertTrue(shot.gate(a, level).orElse("").contains("already happened") && shot.gate(stranger, level).orElse("").contains("already happened"),
				"the card fits again after the world's screenshot");
		helper.assertTrue(fire(card, a, level, false) == FireResult.SKIPPED, "a real fire armed again");

		helper.assertTrue(fire(card, a, level, true) == FireResult.FIRED && shot.isArmed(a.getUUID()), "a debug fire did not arm");
		hooks.dayTicks += 5 * DAY;
		helper.assertTrue(shot.report(a) == TheScreenshot.Report.ALREADY_RECORDED, "the debug arm's report");
		helper.assertTrue(hooks.data.screenshotDay() == today && hooks.data.screenshotBy().equals(Optional.of(b.getUUID())), "the first record changed");
		helper.assertTrue(shot.armed().isEmpty(), "the debug arm stayed");
		helper.succeed();
	}

	/** The world's screenshot survives a save; a file from before v1.3 loads without one. */
	@GameTest
	public void theRecordSurvivesASave(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		EntityData data = new EntityData();
		UUID player = UUID.fromString("00000000-0000-0000-0000-00000000d078");
		data.recordGlimpse(4);
		Tag before = EntityData.CODEC.encodeStart(ops, data).getOrThrow();
		EntityData old = EntityData.CODEC.parse(ops, before).getOrThrow();
		helper.assertTrue(!old.screenshotTaken() && old.screenshotDay() == -1 && old.screenshotBy().isEmpty() && old.glimpses() == 1,
				"a save without the screenshot");
		helper.assertTrue(data.recordScreenshot(player, 7) && !data.recordScreenshot(UUID.randomUUID(), 9), "recorded twice");
		EntityData loaded = EntityData.CODEC.parse(ops, EntityData.CODEC.encodeStart(ops, data).getOrThrow()).getOrThrow();
		helper.assertTrue(loaded.screenshotTaken() && loaded.screenshotDay() == 7 && loaded.screenshotBy().equals(Optional.of(player)),
				"loaded day " + loaded.screenshotDay() + " by " + loaded.screenshotBy());
		helper.assertFalse(loaded.dayAllows(7, 1), "the loaded day count");
		loaded.forgetScreenshot();
		helper.assertTrue(!loaded.screenshotTaken() && loaded.screenshotBy().isEmpty(), "reset kept the record");
		helper.succeed();
	}

	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		try {
			codec.encode(buf, value);
			T decoded = codec.decode(buf);
			if (buf.readableBytes() != 0) {
				throw new IllegalStateException(buf.readableBytes() + " bytes left after decoding " + value);
			}
			return decoded;
		} finally {
			buf.release();
		}
	}

	@GameTest
	public void thePayloadsRoundTrip(GameTestHelper helper) {
		for (boolean armed : new boolean[] {false, true}) {
			for (boolean fog : new boolean[] {false, true}) {
				ScreenshotStatePayload state = new ScreenshotStatePayload(armed, fog);
				helper.assertTrue(roundTrip(ScreenshotStatePayload.CODEC, state).equals(state), "state " + state);
			}
		}
		helper.assertTrue(roundTrip(ScreenshotTakenPayload.CODEC, ScreenshotTakenPayload.INSTANCE).equals(ScreenshotTakenPayload.INSTANCE), "taken");
		helper.assertFalse(ScreenshotStatePayload.TYPE.id().equals(ScreenshotTakenPayload.TYPE.id()), "one id for both");
		helper.succeed();
	}

	// --- the client's conditions and the fog flag ---

	@GameTest
	public void theClientConditions(GameTestHelper helper) {
		int sky = 10;
		double max = 160.0;
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, true, 15, 90.0, false, sky, max).isEmpty(), "fog at 90 blocks refused");
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, true, 10, 159.0, false, sky, max).isEmpty(), "sky 10, fog at 159 refused");
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, true, 15, 300.0, true, sky, max).isEmpty(), "a far fog end with the fog on refused");
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, true, 15, 160.0, false, sky, max).orElse("").equals("no fog"),
				"a clear view at 160 passed");
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, true, 15, Double.NaN, false, sky, max).orElse("").equals("no fog"),
				"an unknown fog end passed");
		helper.assertTrue(ScreenshotRules.clientConditions(false, false, false, true, 15, 60.0, true, sky, max).isPresent(), "the Nether passed");
		helper.assertTrue(ScreenshotRules.clientConditions(true, true, false, true, 15, 60.0, true, sky, max).isPresent(), "an open screen passed");
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, true, true, 15, 60.0, true, sky, max).isPresent(), "under water passed");
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, false, 15, 60.0, true, sky, max).isPresent(), "third person passed");
		helper.assertTrue(ScreenshotRules.clientConditions(true, false, false, true, 9, 60.0, true, sky, max).isPresent(), "sky light 9 passed");
		helper.succeed();
	}

	/** The fog flag: the dusk fog at its weight for the time, and a spell weighted by the sky at the eyes, as FogLimits combines them. */
	@GameTest
	public void theFogIsOnWithADuskFogOrASpellOutside(GameTestHelper helper) {
		FogLimits.Shape shape = FogLimits.Shape.DEFAULT;
		double on = 0.1;
		double noon = ScreenshotRules.fogAmount(0.3F, 6000L, shape, 0.0, 15);
		double night = ScreenshotRules.fogAmount(0.3F, 18000L, shape, 0.0, 15);
		helper.assertTrue(noon == 0.0 && !ScreenshotRules.fogOn(noon, on), "a dusk fog at noon: " + noon);
		helper.assertTrue(Math.abs(night - 0.3F * FogLimits.duskWeight(18000L, shape)) < 1.0E-9 && ScreenshotRules.fogOn(night, on),
				"Traces' dusk fog at night: " + night);
		double spell = ScreenshotRules.fogAmount(0.0F, 6000L, shape, 0.5, 15);
		double cave = ScreenshotRules.fogAmount(0.0F, 6000L, shape, 0.5, 4);
		helper.assertTrue(spell == 0.5 && ScreenshotRules.fogOn(spell, on), "a fog day outside: " + spell);
		helper.assertTrue(cave == 0.0 && !ScreenshotRules.fogOn(cave, on), "a fog day in a cave: " + cave);
		double both = ScreenshotRules.fogAmount(0.3F, 18000L, shape, 0.5, 15);
		helper.assertTrue(Math.abs(both - FogLimits.combine(night, 0.5)) < 1.0E-9, "dusk and spell together: " + both);
		helper.assertFalse(ScreenshotRules.fogOn(0.05, on), "a trace of fog counts");
		helper.succeed();
	}

	// --- the spot ---

	private static final Vec3 EYE = new Vec3(0.5, 65.62, 0.5);
	private static final double FOG = 60.0;
	private static final ScreenshotSpot.Settings S = ScreenshotSpot.Settings.DEFAULT;

	/** Looking south (yaw 0) and level, a 70-degree 16:9 frame. */
	private static ScreenshotSpot.View view(float yaw, float pitch) {
		return new ScreenshotSpot.View(EYE, yaw, pitch, 70.0, 16.0 / 9.0);
	}

	private static final ScreenshotSpot.Sight OPEN = (from, to) -> true;

	/** The plain projection: ahead is the middle, east is on the left looking south, up is up, behind is nowhere. */
	@GameTest
	public void theViewProjectsAsTheCameraDoes(GameTestHelper helper) {
		ScreenshotSpot.View south = view(0.0F, 0.0F);
		ScreenshotSpot.Screen ahead = south.project(EYE.add(0, 0, 10));
		helper.assertTrue(ahead != null && Math.abs(ahead.x() - 0.5) < 1.0E-9 && Math.abs(ahead.y() - 0.5) < 1.0E-9, "ahead: " + ahead);
		ScreenshotSpot.Screen east = south.project(EYE.add(3, 0, 10));
		ScreenshotSpot.Screen above = south.project(EYE.add(0, 3, 10));
		helper.assertTrue(east != null && east.x() < 0.5 && above != null && above.y() < 0.5, "east " + east + ", above " + above);
		helper.assertTrue(south.project(EYE.add(0, 0, -10)) == null, "behind the camera is in the frame");
		double tanV = Math.tan(Math.toRadians(35.0));
		ScreenshotSpot.Screen top = south.project(EYE.add(0, 10 * tanV, 10));
		helper.assertTrue(top != null && Math.abs(top.y()) < 1.0E-6, "the top edge: " + top);
		ScreenshotSpot.View west = view(90.0F, 0.0F);
		helper.assertTrue(west.forward().distanceTo(new Vec3(-1, 0, 0)) < 1.0E-9, "yaw 90 looks " + west.forward());
		ScreenshotSpot.View down = view(0.0F, 30.0F);
		ScreenshotSpot.Screen ground = down.project(EYE.add(0, -10 * Math.tan(Math.toRadians(30.0)), 10));
		helper.assertTrue(ground != null && Math.abs(ground.y() - 0.5) < 1.0E-9, "looking 30 down, the ground ahead is not the middle: " + ground);
		helper.succeed();
	}

	/**
	 * On flat ground he always stands in the fog band (0.6 to 0.75 of the fog end to his middle), off the middle (15 to
	 * 35% from an edge), feet and head inside the frame, on the ground, facing the camera; and on both sides over many
	 * screenshots.
	 */
	@GameTest
	public void theSpotIsInTheFogBandAndOffTheMiddle(GameTestHelper helper) {
		ScreenshotSpot.Ground flat = (x, z) -> OptionalDouble.of(64.0);
		boolean left = false;
		boolean right = false;
		for (float yaw : new float[] {0.0F, 37.0F, -120.0F, 180.0F}) {
			ScreenshotSpot.View v = view(yaw, 5.0F);
			for (int seed = 0; seed < 40; seed++) {
				Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(v, FOG, S, flat, OPEN, RandomSource.create(seed));
				helper.assertTrue(spot.isPresent(), "no spot on flat ground, yaw " + yaw + " seed " + seed);
				ScreenshotSpot.Spot s = spot.get();
				Vec3 middle = s.feet().add(0, ScreenshotSpot.MIDDLE, 0);
				double d = middle.distanceTo(EYE);
				helper.assertTrue(d >= 0.6 * FOG - 1.0E-6 && d <= 0.75 * FOG + 1.0E-6 && Math.abs(d - s.distance()) < 1.0E-9, "distance " + d);
				ScreenshotSpot.Screen m = v.project(middle);
				double fromEdge = Math.min(m.x(), 1.0 - m.x());
				helper.assertTrue(fromEdge >= 0.15 - 1.0E-9 && fromEdge <= 0.35 + 1.0E-9 && Math.abs(m.x() - s.screenX()) < 1.0E-9, "screen x " + m.x());
				helper.assertTrue(v.project(s.feet()).inside(S.frameMargin()) && v.project(s.feet().add(0, ScreenshotSpot.BODY_HEIGHT, 0)).inside(S.frameMargin()),
						"he is cut by the frame at " + s.feet());
				helper.assertTrue(s.feet().y == 64.0 && s.feet().x == Math.floor(s.feet().x) + 0.5, "not on the ground at a block centre: " + s.feet());
				helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(s.yaw() - HimEntity.yawToward(s.feet(), EYE))) < 1.0E-3,
						"he does not face the camera: " + s.yaw());
				left |= m.x() < 0.5;
				right |= m.x() > 0.5;
			}
		}
		helper.assertTrue(left && right, "always the same side: left " + left + ", right " + right);
		helper.succeed();
	}

	/**
	 * Ground or none: no ground, ground only in the middle of the frame, nothing in sight, the sky in the whole frame, or
	 * no fog end: no spot. Ground only to the right: only right-side spots. A deep valley: measured to his middle in 3D.
	 */
	@GameTest
	public void noGroundNoSpot(GameTestHelper helper) {
		ScreenshotSpot.View south = view(0.0F, 5.0F);
		RandomSource random = RandomSource.create(3);
		helper.assertTrue(ScreenshotSpot.find(south, FOG, S, (x, z) -> OptionalDouble.empty(), OPEN, random).isEmpty(), "a spot without ground");
		helper.assertTrue(ScreenshotSpot.find(south, FOG, S, (x, z) -> OptionalDouble.of(64.0), (from, to) -> false, random).isEmpty(),
				"a spot nobody sees");
		helper.assertTrue(ScreenshotSpot.find(view(0.0F, -80.0F), FOG, S, (x, z) -> OptionalDouble.of(64.0), OPEN, random).isEmpty(),
				"a spot while looking at the sky");
		helper.assertTrue(ScreenshotSpot.find(south, Double.NaN, S, (x, z) -> OptionalDouble.of(64.0), OPEN, random).isEmpty(), "a spot without a fog end");
		// Looking south (+z), the frame's middle is the column x = 0; east (+x) is on the left.
		ScreenshotSpot.Ground middleOnly = (x, z) -> Math.abs(x + 0.5 - EYE.x) <= 2.0 ? OptionalDouble.of(64.0) : OptionalDouble.empty();
		helper.assertTrue(ScreenshotSpot.find(south, FOG, S, middleOnly, OPEN, random).isEmpty(), "a spot in the middle of the frame");
		ScreenshotSpot.Ground westOnly = (x, z) -> x < -2 ? OptionalDouble.of(64.0) : OptionalDouble.empty();
		for (int seed = 0; seed < 20; seed++) {
			Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(south, FOG, S, westOnly, OPEN, RandomSource.create(seed));
			helper.assertTrue(spot.isPresent() && spot.get().screenX() > 0.5, "west of the camera is not the right of the frame: " + spot);
		}
		ScreenshotSpot.Ground valley = (x, z) -> OptionalDouble.of(40.0);
		Optional<ScreenshotSpot.Spot> deep = ScreenshotSpot.find(view(0.0F, 25.0F), FOG, S, valley, OPEN, RandomSource.create(9));
		helper.assertTrue(deep.isPresent(), "no spot in the valley below");
		double d = deep.get().feet().add(0, ScreenshotSpot.MIDDLE, 0).distanceTo(EYE);
		double flat = Math.hypot(deep.get().feet().x - EYE.x, deep.get().feet().z - EYE.z);
		helper.assertTrue(ScreenshotSpot.inBand(d, FOG, S) && flat < d - 1.0, "the valley spot is measured flat: 3D " + d + ", level " + flat);
		helper.succeed();
	}

	@GameTest
	public void theBandsAreInclusive(GameTestHelper helper) {
		helper.assertTrue(ScreenshotSpot.inBand(36.0, 60.0, S) && ScreenshotSpot.inBand(45.0, 60.0, S), "the band's ends");
		helper.assertFalse(ScreenshotSpot.inBand(35.9, 60.0, S) || ScreenshotSpot.inBand(45.1, 60.0, S), "outside the band");
		helper.assertTrue(ScreenshotSpot.offCentre(0.15, S) && ScreenshotSpot.offCentre(0.35, S) && ScreenshotSpot.offCentre(0.65, S)
				&& ScreenshotSpot.offCentre(0.85, S), "the edge bands' ends");
		helper.assertFalse(ScreenshotSpot.offCentre(0.5, S) || ScreenshotSpot.offCentre(0.4, S) || ScreenshotSpot.offCentre(0.1, S)
				|| ScreenshotSpot.offCentre(0.9, S) || ScreenshotSpot.offCentre(-0.2, S) || ScreenshotSpot.offCentre(Double.NaN, S), "the middle or an edge");
		ScreenshotSpot.Settings odd = new ScreenshotSpot.Settings(0.9, 0.2, 0.4, 0.1, Double.NaN, 0);
		helper.assertTrue(odd.farShare() >= odd.nearShare() && odd.edgeMax() >= odd.edgeMin() && odd.frameMargin() == 0.03 && odd.tries() == 1,
				"settings not normalized: " + odd);
		ScreenshotSpot.Settings configured = EntityConfig.get().screenshotSpot();
		helper.assertTrue(configured.equals(S), "the config's defaults are not D-078's: " + configured);
		helper.succeed();
	}

	/**
	 * The level's ground: on a stone floor, his feet on it; on a snow layer, on the snow; never on water, under leaves,
	 * on a fence; and sight is blocked by stone and leaves, not by glass.
	 */
	@GameTest(skyAccess = true)
	public void theGroundIsTheTerrainAndSightIsTheView(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		helper.setBlock(1, 1, 1, Blocks.WATER);
		helper.setBlock(2, 2, 2, Blocks.OAK_LEAVES); // where his head would be
		helper.setBlock(6, 3, 6, Blocks.OAK_LEAVES); // over his head: fine
		helper.setBlock(3, 1, 3, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
		helper.setBlock(4, 1, 4, Blocks.OAK_FENCE);
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int floorTop = origin.getY() + 1;
		OptionalDouble stone = ScreenshotSpot.feetY(level, origin.getX() + 6, origin.getZ() + 6);
		helper.assertTrue(stone.isPresent() && stone.getAsDouble() == floorTop, "on stone: " + stone + ", expected " + floorTop);
		helper.assertTrue(ScreenshotSpot.feetY(level, origin.getX() + 1, origin.getZ() + 1).isEmpty(), "he stands on water");
		helper.assertTrue(ScreenshotSpot.feetY(level, origin.getX() + 2, origin.getZ() + 2).isEmpty(), "he stands in leaves");
		OptionalDouble snow = ScreenshotSpot.feetY(level, origin.getX() + 3, origin.getZ() + 3);
		helper.assertTrue(snow.isPresent() && Math.abs(snow.getAsDouble() - (floorTop + 0.25)) < 1.0E-6, "on snow: " + snow);
		helper.assertTrue(ScreenshotSpot.feetY(level, origin.getX() + 4, origin.getZ() + 4).isEmpty(), "he stands on a fence");

		helper.setBlock(5, 1, 2, Blocks.STONE);
		helper.setBlock(5, 2, 2, Blocks.STONE);
		helper.setBlock(5, 1, 4, Blocks.GLASS);
		helper.setBlock(5, 2, 4, Blocks.GLASS);
		helper.setBlock(5, 1, 6, Blocks.OAK_LEAVES);
		helper.setBlock(5, 2, 6, Blocks.OAK_LEAVES);
		for (int z : new int[] {2, 4, 6}) {
			Vec3 from = helper.absoluteVec(new Vec3(3.5, 2.5, z + 0.5));
			Vec3 to = helper.absoluteVec(new Vec3(7.5, 2.5, z + 0.5));
			boolean clear = ScreenshotSpot.clear(level, from, to);
			helper.assertTrue(clear == (z == 4), "sight through " + level.getBlockState(helper.absolutePos(new BlockPos(5, 2, z))) + ": " + clear);
		}
		helper.assertTrue(ScreenshotSpot.clear(level, helper.absoluteVec(new Vec3(0.5, 3.5, 0.5)), helper.absoluteVec(new Vec3(7.5, 3.5, 0.5))),
				"open air blocks the view");
		helper.succeed();
	}
}
