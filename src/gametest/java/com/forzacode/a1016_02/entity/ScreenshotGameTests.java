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
 * pausing, the fog flag and the weather's fog end, one arm at a time, the story's end), the report (once per world, the
 * day, everyone disarmed, only from a ready arm), the record through a save, the payloads, the client's conditions
 * (weather fog only), and the spot's pure math (the fog band with every sighting's floor and cap, his height in the
 * saved image, past the visible fog start, off the middle, inside the frame, ground or none) with the level's ground
 * and sight rules. The frame itself (him in the PNG, never on the screen, the clean frame after it) is the client's:
 * see the dev client check list in the report. Each test makes its own {@link TheScreenshot} on its own {@link Hooks}
 * (a fresh {@link EntityData}, a stage, a clock, the story's end, the players online and a recorder of what was sent),
 * so nothing here reads or changes the shared test server's story. {@link EntityGameTests} extends this (through
 * {@link TwoPlayerFigureGameTests}).
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
		boolean storyOver;
		boolean out;
		boolean fog;
		float fogEnd = FOG_END;
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
		public boolean storyOver(MinecraftServer server) {
			return storyOver;
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
		public float weatherFogEnd(ServerPlayer player) {
			return fogEnd;
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

	/** The weather's fog end a test's hooks send unless it sets another. */
	private static final float FOG_END = 60.0F;

	/** What the client is told: ready or paused, the fog flag, the weather's fog end ({@link #FOG_END}) and the arm's number. */
	private static ScreenshotStatePayload told(boolean ready, boolean fog, int arm) {
		return new ScreenshotStatePayload(ready, fog, FOG_END, arm);
	}

	/** The first arm of a test's screenshot, ready, without fog. */
	private static final ScreenshotStatePayload READY = told(true, false, 1);

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

	/**
	 * The gate: the overworld, from Traces, while the story goes on, once per world, nobody armed (one arm in the world
	 * at a time), alive and playing, not in Ending D's last minute.
	 */
	@GameTest
	public void theGateFitsOnlyInTheOverworldFromTracesOncePerWorld(GameTestHelper helper) {
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, true, false, false, false).isEmpty(), "the plain case is refused");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.REMOVAL, false, false, true, false, false, false).isEmpty(), "Removal is refused");
		helper.assertTrue(ScreenshotRules.gate(false, Stage.TRACES, false, false, true, false, false, false).orElse("").equals("not in the overworld"),
				"the Nether or the End fit");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.ALONE, false, false, true, false, false, false).orElse("").startsWith("before"), "Alone fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, true, false, true, false, false, false).orElse("").contains("already happened"),
				"a second screenshot in the world fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, true, true, false, false, false).orElse("").equals("someone is already armed"),
				"a second arm in the world fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, false, false, false, false).isPresent(), "a dead player fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, true, true, false, false).isPresent(), "a spectator fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, true, false, true, false).isPresent(), "Ending D's last minute fits");
		helper.assertTrue(ScreenshotRules.gate(true, Stage.TRACES, false, false, true, false, false, true).orElse("").equals("the story is over"),
				"a world quiet for good fits");
		helper.succeed();
	}

	/** The story is over for the screenshot once the world is quiet for good or an ending is done: each flag alone, and none. */
	@GameTest
	public void theStoryIsOverWithEveryEndingsFlag(GameTestHelper helper) {
		helper.assertFalse(ScreenshotRules.storyOver(flag -> false), "no flag ends the story");
		for (String flag : List.of("director:silence_forever", "ending:path=C", "ending:ended", "ending:d_complete")) {
			helper.assertTrue(ScreenshotRules.storyOver(flag::equals), flag + " does not end the story");
		}
		helper.assertFalse(ScreenshotRules.storyOver(flag -> flag.equals("ending:path=A") || flag.equals("ending:last_minute")),
				"a path under way or the last minute ends the story for good");
		helper.succeed();
	}

	/**
	 * Firing arms the player's client (FIRED, the arm sent) and the card stops fitting, for them and for everyone else
	 * (one arm in the world at a time); it fits for nobody at Alone. A client that cannot take the payload is never
	 * armed (SKIPPED).
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
		helper.assertTrue(hooks.to(a).equals(List.of(told(true, true, 1))) && hooks.to(b).isEmpty(), "sent " + hooks.sent);
		helper.assertTrue(shot.ticksLeft(level.getServer(), a.getUUID()) == EntityConfig.get().screenshotArmTicks()
				&& EntityConfig.get().screenshotArmTicks() == 2 * DAY, "the arm is not 2 in-game days: " + shot.ticksLeft(level.getServer(), a.getUUID()));
		helper.assertFalse(card.contextFits(a, level), "the card fits A again while armed");
		helper.assertTrue(shot.gate(b, level).orElse("").equals("someone is already armed"), "the card fits B while A is armed: " + shot.gate(b, level));
		helper.assertTrue(fire(card, b, level, false) == FireResult.SKIPPED && !shot.isArmed(b.getUUID()), "B was armed while A is");
		helper.assertTrue(card.fire(new FireContext(b, level, true, false, RandomSource.create(1))) == FireResult.SKIPPED, "a fake fired");

		hooks.stage = Stage.ALONE;
		shot.disarm(level.getServer(), a.getUUID());
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
		shot.onParticipation(server, b.getUUID(), false);
		shot.onParticipation(server, a.getUUID(), true);
		helper.assertTrue(shot.isArmed(a.getUUID()), "taking part, or someone else leaving, disarmed");
		shot.onParticipation(server, a.getUUID(), false);
		helper.assertFalse(shot.isArmed(a.getUUID()), "leaving kept the arm");

		shot.arm(b);
		hooks.online.remove(b.getUUID()); // gone without a PARTICIPATION event reaching us
		int sentBefore = hooks.sent.size();
		shot.check(server);
		helper.assertFalse(shot.isArmed(b.getUUID()), "a player gone from the server kept the arm");
		helper.assertTrue(hooks.sent.size() == sentBefore, "something was sent to a player who is gone");

		shot.arm(c);
		c.mode = GameType.SPECTATOR;
		shot.check(server);
		helper.assertFalse(shot.isArmed(c.getUUID()), "a spectator kept the arm");
		helper.assertTrue(hooks.to(c).getLast().equals(ScreenshotStatePayload.OFF), "the spectator's client was not told");
		helper.succeed();
	}

	/**
	 * While armed, the client is told again only when what it may do changes: paused while he is out, the fog flag, and
	 * the weather's fog end once it moved by more than 2 blocks. Nothing is sent when nothing changed.
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
		shot.check(server);
		hooks.out = false;
		hooks.fog = false;
		shot.check(server);
		hooks.fogEnd = FOG_END + 1.9F;
		shot.check(server);
		hooks.fogEnd = FOG_END - 2.1F;
		shot.check(server);
		// The fog came, he went out (paused; nothing new on the second look), then he was gone with the fog; the fog end
		// moved 1.9 blocks (nothing sent), then 2.1 from what the client was told.
		List<ScreenshotStatePayload> told = hooks.to(a);
		ScreenshotStatePayload out = new ScreenshotStatePayload(false, true, FOG_END, 1, ScreenshotRules.paused(true, 3, -1, 0, 1).orElseThrow());
		helper.assertTrue(told.equals(List.of(READY, told(true, true, 1), out, READY, new ScreenshotStatePayload(true, false, FOG_END - 2.1F, 1))),
				"told " + told);
		helper.assertTrue(shot.isArmed(a.getUUID()), "a pause ended the arm");
		helper.assertTrue(shot.gate(a, helper.getLevel()).orElse("").equals("someone is already armed"), "a paused arm can be armed again");
		helper.succeed();
	}

	/**
	 * The story holds no screenshot during Ending D's last minute and once it is over or quiet for good: every arm ends
	 * at the next look (its client told), the gate refuses everyone, and even a debug arm is SKIPPED.
	 */
	@GameTest
	public void everyArmEndsWithTheStory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		for (boolean lastMinute : new boolean[] {true, false}) {
			Hooks hooks = new Hooks().with(a, b);
			TheScreenshot shot = new TheScreenshot(hooks);
			ScreenshotCard card = new ScreenshotCard(shot);
			helper.assertTrue(fire(card, a, level, false) == FireResult.FIRED, "not armed");
			hooks.lastMinute = lastMinute;
			hooks.storyOver = !lastMinute;
			String why = lastMinute ? "Ending D's last minute" : "the story is over";
			shot.check(server);
			helper.assertTrue(shot.armed().isEmpty() && hooks.to(a).getLast().equals(ScreenshotStatePayload.OFF), why + ": the arm stayed, told " + hooks.sent);
			helper.assertTrue(shot.gate(b, level).orElse("").equals(why), why + ": the gate says " + shot.gate(b, level));
			helper.assertTrue(fire(card, b, level, true) == FireResult.SKIPPED && shot.armed().isEmpty(), why + ": a debug fire armed");
			helper.assertTrue(shot.report(a) == TheScreenshot.Report.IGNORED && !hooks.data.screenshotTaken(), why + ": a report counted");
		}
		helper.succeed();
	}

	/**
	 * One arm in the world at a time: a debug arm for B ends A's (A's client told), with a new arm number; a report
	 * from A is then ignored.
	 */
	@GameTest
	public void aDebugArmEndsTheArmBefore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer a = mock(helper);
		ServerPlayer b = mock(helper);
		Hooks hooks = new Hooks().with(a, b);
		TheScreenshot shot = new TheScreenshot(hooks);
		ScreenshotCard card = new ScreenshotCard(shot);
		helper.assertTrue(fire(card, a, level, false) == FireResult.FIRED, "A not armed");
		helper.assertTrue(fire(card, b, level, true) == FireResult.FIRED, "the debug arm for B");
		helper.assertTrue(shot.armed().equals(List.of(b.getUUID())), "armed " + shot.armed());
		helper.assertTrue(hooks.to(a).equals(List.of(READY, ScreenshotStatePayload.OFF)) && hooks.to(b).equals(List.of(told(true, false, 2))),
				"sent " + hooks.sent);
		helper.assertTrue(shot.report(a) == TheScreenshot.Report.IGNORED && !hooks.data.screenshotTaken(), "A's report counted");
		helper.assertTrue(shot.report(b) == TheScreenshot.Report.RECORDED, "B's report");
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
		helper.assertTrue(fire(card, b, level, false) == FireResult.FIRED, "not armed");
		helper.assertTrue(shot.report(stranger) == TheScreenshot.Report.IGNORED, "a report from a client that was not armed counted");
		helper.assertFalse(hooks.data.screenshotTaken(), "an unarmed report recorded the screenshot");
		helper.assertTrue(hooks.data.dayAllows(today, 1), "an unarmed report counted the day");
		helper.assertTrue(shot.isArmed(b.getUUID()), "an unarmed report disarmed someone");

		helper.assertTrue(shot.report(b) == TheScreenshot.Report.RECORDED, "B's report was not recorded");
		helper.assertTrue(hooks.data.screenshotTaken() && hooks.data.screenshotDay() == today && hooks.data.screenshotBy().equals(Optional.of(b.getUUID())),
				"recorded day " + hooks.data.screenshotDay() + " by " + hooks.data.screenshotBy());
		helper.assertFalse(hooks.data.dayAllows(today, 1), "the screenshot did not count as the day's sighting");
		helper.assertTrue(hooks.data.dayAllows(today + 1, 1), "it counted for the next day");
		helper.assertTrue(hooks.data.lastPos() == null && hooks.data.lastVariant().isEmpty() && hooks.data.sightings() == 0,
				"it counted for the place or the variant rule");
		helper.assertTrue(shot.armed().isEmpty(), "someone is still armed: " + shot.armed());
		helper.assertTrue(hooks.to(b).getLast().equals(ScreenshotStatePayload.OFF), "B was not told: " + hooks.sent);
		helper.assertTrue(shot.report(b) == TheScreenshot.Report.IGNORED, "a second report from B counted");
		helper.assertTrue(shot.gate(a, level).orElse("").contains("already happened") && shot.gate(stranger, level).orElse("").contains("already happened"),
				"the card fits again after the world's screenshot");
		helper.assertTrue(fire(card, a, level, false) == FireResult.SKIPPED, "a real fire armed again");

		helper.assertTrue(fire(card, a, level, true) == FireResult.FIRED && shot.isArmed(a.getUUID()), "a debug fire did not arm");
		hooks.dayTicks += DAY + 500; // a later day, the arm still running
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
				for (float end : new float[] {0.0F, 37.5F, Float.NaN}) {
					ScreenshotStatePayload state = new ScreenshotStatePayload(armed, fog, end, armed ? 300 : 0);
					helper.assertTrue(roundTrip(ScreenshotStatePayload.CODEC, state).equals(state), "state " + state);
				}
			}
		}
		helper.assertTrue(roundTrip(ScreenshotTakenPayload.CODEC, ScreenshotTakenPayload.INSTANCE).equals(ScreenshotTakenPayload.INSTANCE), "taken");
		helper.assertFalse(ScreenshotStatePayload.TYPE.id().equals(ScreenshotTakenPayload.TYPE.id()), "one id for both");
		helper.succeed();
	}

	// --- the client's conditions and the fog flag ---

	/** The client's conditions for this camera, the plain case (overworld, no screen, in the air, first person, sky 15) changed by the test. */
	private static Optional<String> conditions(boolean overworld, boolean screen, boolean fluid, boolean firstPerson, int sky, boolean shaders,
			boolean blindOrDark, boolean fogOn, double drawn, double weather) {
		return ScreenshotRules.clientConditions(overworld, screen, fluid, firstPerson, sky, 10, shaders, blindOrDark, fogOn, drawn, weather);
	}

	/**
	 * Weather fog only (D-078, FIX-SHOT): the server's fog flag on and the drawn fog no nearer than the weather's (a
	 * surge or an effect pulls it in), never the render distance alone, never Blindness, Darkness or a shaderpack; and
	 * the camera's own conditions.
	 */
	@GameTest
	public void theClientConditions(GameTestHelper helper) {
		helper.assertTrue(conditions(true, false, false, true, 15, false, false, true, 60.0, 60.0).isEmpty(), "a fog night refused");
		helper.assertTrue(conditions(true, false, false, true, 10, false, false, true, 300.0, 300.0).isEmpty(), "sky 10, a thin dusk fog far out refused");
		helper.assertTrue(conditions(true, false, false, true, 15, false, false, false, 90.0, 90.0).orElse("").equals("no fog weather"),
				"a short render distance alone passed");
		helper.assertTrue(conditions(true, false, false, true, 15, false, false, true, 30.0, 60.0).orElse("").startsWith("the fog is pulled in"),
				"a surge passed");
		helper.assertTrue(conditions(true, false, false, true, 15, false, false, true, 52.0, 60.0).isEmpty(), "the client's easing refused");
		helper.assertTrue(conditions(true, false, false, true, 15, false, false, true, 60.0, Double.NaN).isPresent(), "an unknown weather fog end passed");
		helper.assertTrue(conditions(true, false, false, true, 15, false, false, true, Double.NaN, 60.0).isPresent(), "an unknown drawn fog end passed");
		helper.assertTrue(conditions(true, false, false, true, 15, false, true, true, 60.0, 60.0).orElse("").equals("Blindness or Darkness"),
				"Blindness or Darkness passed");
		helper.assertTrue(conditions(true, false, false, true, 15, true, false, true, 60.0, 60.0).orElse("").equals("a shaderpack is in use"),
				"a shaderpack passed");
		helper.assertTrue(conditions(false, false, false, true, 15, false, false, true, 60.0, 60.0).isPresent(), "the Nether passed");
		helper.assertTrue(conditions(true, true, false, true, 15, false, false, true, 60.0, 60.0).isPresent(), "an open screen passed");
		helper.assertTrue(conditions(true, false, true, true, 15, false, false, true, 60.0, 60.0).isPresent(), "under water passed");
		helper.assertTrue(conditions(true, false, false, false, 15, false, false, true, 60.0, 60.0).isPresent(), "third person passed");
		helper.assertTrue(conditions(true, false, false, true, 9, false, false, true, 60.0, 60.0).isPresent(), "sky light 9 passed");
		helper.assertTrue(!ScreenshotRules.pulledIn(51.1, 60.0) && ScreenshotRules.pulledIn(50.9, 60.0), "the pulled-in share is not 0.85");
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

	/** Looking south (yaw 0) and level, a 70-degree 16:9 frame, saved 1080 pixels tall. */
	private static ScreenshotSpot.View view(float yaw, float pitch) {
		return view(yaw, pitch, 1080);
	}

	private static ScreenshotSpot.View view(float yaw, float pitch, int imageHeight) {
		return new ScreenshotSpot.View(EYE, yaw, pitch, 70.0, 16.0 / 9.0, imageHeight);
	}

	private static final ScreenshotSpot.Ground FLAT = (x, z) -> OptionalDouble.of(64.0);

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
		helper.assertTrue(d >= 0.6 * FOG - 1.0E-6 && d <= 0.75 * FOG + 1.0E-6 && flat < d - 1.0, "the valley spot is measured flat: 3D " + d + ", level " + flat);
		helper.succeed();
	}

	@GameTest
	public void theBandsAreInclusive(GameTestHelper helper) {
		ScreenshotSpot.Band band = ScreenshotSpot.band(view(0.0F, 0.0F), ScreenshotSpot.Fog.fromCamera(60.0), S);
		helper.assertTrue(band.contains(36.0) && band.contains(45.0), "the band's ends: " + band);
		helper.assertFalse(band.contains(35.9) || band.contains(45.1), "outside the band: " + band);
		helper.assertTrue(ScreenshotSpot.offCentre(0.15, S) && ScreenshotSpot.offCentre(0.35, S) && ScreenshotSpot.offCentre(0.65, S)
				&& ScreenshotSpot.offCentre(0.85, S), "the edge bands' ends");
		helper.assertFalse(ScreenshotSpot.offCentre(0.5, S) || ScreenshotSpot.offCentre(0.4, S) || ScreenshotSpot.offCentre(0.1, S)
				|| ScreenshotSpot.offCentre(0.9, S) || ScreenshotSpot.offCentre(-0.2, S) || ScreenshotSpot.offCentre(Double.NaN, S), "the middle or an edge");
		ScreenshotSpot.Settings odd = new ScreenshotSpot.Settings(0.9, 0.2, 0.4, 0.1, Double.NaN, 0, 50.0, 20.0, Double.NaN, 3.0);
		helper.assertTrue(odd.farShare() >= odd.nearShare() && odd.edgeMax() >= odd.edgeMin() && odd.frameMargin() == 0.03 && odd.tries() == 1
				&& odd.maxDistance() == 50.0 && odd.minPixels() == 24.0 && odd.minFog() == 1.0, "settings not normalized: " + odd);
		ScreenshotSpot.Settings configured = EntityConfig.get().screenshotSpot();
		helper.assertTrue(configured.equals(S), "the config's defaults are not D-078's: " + configured);
		helper.assertTrue(S.minDistance() == EntityConfig.get().minDistance() && S.maxDistance() == EntityConfig.get().maxSpawnDistance,
				"the floor and cap are not every sighting's: " + S);
		helper.succeed();
	}

	/**
	 * Never nearer than any sighting (minDistance 12) nor farther (maxSpawnDistance 192): a drawn fog ending at 5, 10 or
	 * 15 blocks has no spot; at 20 every spot is 12 or more away. A thin dusk fog at render distance 32 ends about 310
	 * out: at 1080 pixels he stands nearer, where he is still 24 pixels tall (never a speck), and in an image tall
	 * enough for him far out, never past 192 (FIX-PLAIN: the band ends at the cap and starts at 0.8 of it).
	 */
	@GameTest
	public void theSpotKeepsEverySightingsFloorAndCap(GameTestHelper helper) {
		ScreenshotSpot.View level = view(0.0F, 0.0F);
		for (double end : new double[] {5.0, 10.0, 15.0}) {
			helper.assertTrue(ScreenshotSpot.band(level, ScreenshotSpot.Fog.fromCamera(end), S).isEmpty(), "a band inside the floor at fog end " + end);
		}
		for (int seed = 0; seed < 20; seed++) {
			Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(level, 20.0, S, FLAT, OPEN, RandomSource.create(seed));
			helper.assertTrue(spot.map(s -> s.distance() >= S.minDistance() - 1.0E-9).orElse(true), "nearer than the floor: " + spot);
		}
		for (int seed = 0; seed < 20; seed++) {
			Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(level, 310.0, S, FLAT, OPEN, RandomSource.create(seed));
			helper.assertTrue(spot.isPresent() && spot.get().pixels() >= S.minPixels() && spot.get().distance() < 0.6 * 310.0,
					"at 1080 pixels he is a speck or missing: " + spot);
		}
		ScreenshotSpot.View tall = view(0.0F, 0.0F, 4320);
		ScreenshotSpot.Band band = ScreenshotSpot.band(tall, ScreenshotSpot.Fog.fromCamera(310.0), S);
		helper.assertTrue(Math.abs(band.near() - 0.8 * S.maxDistance()) < 1.0E-9 && band.far() == S.maxDistance(), "the band at 4320 pixels: " + band);
		boolean found = false;
		for (int seed = 0; seed < 20; seed++) {
			Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(tall, 310.0, S, FLAT, OPEN, RandomSource.create(seed));
			helper.assertTrue(spot.map(s -> s.distance() <= S.maxDistance() + 1.0E-9 && s.pixels() >= S.minPixels()).orElse(true), "past the cap: " + spot);
			found |= spot.isPresent();
		}
		helper.assertTrue(found, "no spot at 4320 pixels with a fog end of 310");
		helper.succeed();
	}

	/**
	 * At least {@code screenshotMinPixels} (24) tall in the saved image: a fog end of 100 puts him 60 to 75 blocks out,
	 * too small in a 720-pixel image, so there he stands nearer (FIX-PLAIN: under 60 blocks, still 24 pixels, still in
	 * the fog), and in a 2160-pixel one every spot is 24 pixels or more, as projected.
	 */
	@GameTest
	public void theSpotIsTallEnoughInTheSavedImage(GameTestHelper helper) {
		ScreenshotSpot.View small = view(0.0F, 0.0F, 720);
		for (int seed = 0; seed < 20; seed++) {
			Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(small, 100.0, S, FLAT, OPEN, RandomSource.create(seed));
			helper.assertTrue(spot.isPresent() && spot.get().distance() < 60.0 && small.pixelsTall(spot.get().feet()) >= S.minPixels()
					&& spot.get().fog() >= S.minFog(), "at 720 pixels: " + spot);
		}
		ScreenshotSpot.View big = view(15.0F, 3.0F, 2160);
		boolean found = false;
		for (int seed = 0; seed < 20; seed++) {
			Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(big, 100.0, S, FLAT, OPEN, RandomSource.create(seed));
			if (spot.isPresent()) {
				double pixels = big.pixelsTall(spot.get().feet());
				helper.assertTrue(pixels >= S.minPixels() && Math.abs(pixels - spot.get().pixels()) < 1.0E-9, "only " + pixels + " pixels tall");
				found = true;
			}
		}
		helper.assertTrue(found, "no spot at 2160 pixels");
		helper.succeed();
	}

	/**
	 * Fogged: he stands past the visible fog start, the drawn fog covering at least {@code screenshotMinFog} (0.1) of him
	 * as the shader draws it (the environmental fog at the spherical distance, the render-distance fog at the
	 * cylindrical one, the larger counts). Vanilla's clear fog at 8 chunks (the render-distance fog from 115 to 128) has
	 * no spot: 0.6 to 0.75 of it is before its start. A fog night's has one, fogged.
	 */
	@GameTest
	public void heStandsPastTheVisibleFogStart(GameTestHelper helper) {
		ScreenshotSpot.Fog thin = new ScreenshotSpot.Fog(1000.0, 2000.0, 10.0, 20.0);
		helper.assertTrue(Math.abs(thin.value(new Vec3(15.0, 0.0, 0.0)) - 0.5) < 1.0E-9 && Math.abs(thin.value(new Vec3(0.0, 15.0, 3.0)) - 0.5) < 1.0E-9
				&& thin.value(new Vec3(5.0, 0.0, 0.0)) == 0.0 && thin.value(new Vec3(25.0, 0.0, 0.0)) == 1.0, "the render-distance fog");
		helper.assertTrue(Math.abs(ScreenshotSpot.Fog.fromCamera(60.0).value(new Vec3(0.0, 18.0, 24.0)) - 0.5) < 1.0E-9, "the spherical fog");

		ScreenshotSpot.View level = view(0.0F, 0.0F);
		ScreenshotSpot.Fog clear = new ScreenshotSpot.Fog(0.0, 1024.0, 115.2, 128.0);
		helper.assertTrue(clear.visibleEnd() == 128.0 && clear.visibleStart() == 115.2, "the visible pair: " + clear);
		helper.assertTrue(ScreenshotSpot.find(level, clear, S, FLAT, OPEN, RandomSource.create(2)).isEmpty(), "a spot before vanilla's fog start");
		ScreenshotSpot.Fog late = new ScreenshotSpot.Fog(0.0, 2000.0, 70.0, 80.0);
		helper.assertTrue(ScreenshotSpot.band(level, late, S).isEmpty(), "a band before the fog start: " + ScreenshotSpot.band(level, late, S));

		ScreenshotSpot.Fog night = new ScreenshotSpot.Fog(0.0, 400.0, 22.6, 52.4);
		boolean found = false;
		for (int seed = 0; seed < 20; seed++) {
			Optional<ScreenshotSpot.Spot> spot = ScreenshotSpot.find(level, night, S, FLAT, OPEN, RandomSource.create(seed));
			if (spot.isPresent()) {
				Vec3 offset = spot.get().feet().add(0.0, ScreenshotSpot.MIDDLE, 0.0).subtract(EYE);
				double cylindrical = Math.max(Math.hypot(offset.x, offset.z), Math.abs(offset.y));
				helper.assertTrue(cylindrical > night.visibleStart() && spot.get().fog() >= S.minFog() && night.value(offset) == spot.get().fog(),
						"not fogged: " + spot.get());
				found = true;
			}
		}
		helper.assertTrue(found, "no spot in a fog night's fog");
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
