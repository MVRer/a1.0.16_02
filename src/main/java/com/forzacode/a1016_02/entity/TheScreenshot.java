package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * The screenshot (D-078), server side: "only one screenshot came out. just fog." {@code the_screenshot} arms the
 * player's client for {@link EntityConfig#screenshotArmDays} in-game days or until they leave; the client's next
 * screenshot with the conditions met shows him in the saved image only ({@code entity.client.ScreenshotClient}), and
 * reports it ({@link ScreenshotTakenPayload}); the server then records the world's one screenshot
 * ({@link EntityData#recordScreenshot}: once per world, and it counts as that day's sighting) and disarms everyone.
 * Nothing is spawned and nobody else is told: no message, no sound.
 *
 * <p>Arms live in memory by UUID (an arm ends when its player leaves, so none outlives the server), one in the world at
 * a time: the gate refuses anyone while someone is armed, and a forced arm ends the one before. While an arm lasts the
 * client is told whether it may show him now ({@link ScreenshotStatePayload}: paused while he is out anywhere, since
 * he is one being), whether a dusk fog or fog spell is on at the player ({@link #fogOn}) and the weather's fog end for
 * them ({@link #weatherFogEnd}); they are looked at every {@link #TICK_EVERY} ticks and sent on a change. Every arm
 * ends, and none is made, during Ending D's last minute and once the story is over or quiet for good
 * ({@link ScreenshotRules#storyOver}). An arm also ends when it runs out ({@link ScreenshotStatePayload#OFF} is sent),
 * when its player stops taking part (PARTICIPATION false: they left or turned spectator) and when the screenshot
 * happens for anyone. A report counts only from an arm that is ready at that tick (not run out, not paused); any other
 * report is ignored, and ends the reporter's arm if they had one. Server thread only. {@link #LIVE} is the game's;
 * tests make their own with their own {@link Hooks}.
 */
public final class TheScreenshot {
	public static final String CARD_ID = "the_screenshot";
	/** The arms are looked at this often (ticks). */
	public static final int TICK_EVERY = 10;
	/** The weather's fog end is sent again once it moved by more than this many blocks. */
	public static final float FOG_END_STEP = 2.0F;
	static final long DAY_TICKS = 24000L;

	/** What the screenshot reads and sends, so tests can stand in for the live world. */
	public interface Hooks {
		EntityData data(MinecraftServer server);

		Stage stage(MinecraftServer server);

		/** Ending D's last minute ({@code ending:last_minute}): no new arm, and every arm ends. */
		boolean lastMinute(MinecraftServer server);

		/**
		 * The story is over or quiet for good ({@link ScreenshotRules#storyOver}): no new arm, and every arm ends. The live
		 * world's flags unless a test answers for itself.
		 */
		default boolean storyOver(MinecraftServer server) {
			return TheScreenshot.storyOver(server);
		}

		/** He is out somewhere (one being): an arm pauses. */
		boolean heIsOut(MinecraftServer server);

		/** In-game time ({@link GameClock#dayTicks}): sleeping and timewarp count. */
		long dayTicks(MinecraftServer server);

		/** The player online now (any game mode), or empty. */
		Optional<ServerPlayer> online(MinecraftServer server, UUID player);

		/** A dusk fog or fog spell on at the player ({@link TheScreenshot#fogOn}). */
		boolean fogOn(ServerPlayer player);

		/** The weather's fog end for the player ({@link TheScreenshot#weatherFogEnd}); the live world's unless a test answers. */
		default float weatherFogEnd(ServerPlayer player) {
			return TheScreenshot.weatherFogEnd(player);
		}

		/** Sends the state to that player's client; false if their client cannot take it. */
		boolean send(ServerPlayer player, ScreenshotStatePayload payload);
	}

	/** The live world's hooks. */
	static final Hooks LIVE_HOOKS = new Hooks() {
		@Override
		public EntityData data(MinecraftServer server) {
			return EntityData.get(server);
		}

		@Override
		public Stage stage(MinecraftServer server) {
			return HerobrineState.get(server).stage();
		}

		@Override
		public boolean lastMinute(MinecraftServer server) {
			return HerobrineState.get(server).hasFlag(TraceService.LAST_MINUTE_FLAG);
		}

		@Override
		public boolean heIsOut(MinecraftServer server) {
			return FigureApi.anyOut(server);
		}

		@Override
		public long dayTicks(MinecraftServer server) {
			return GameClock.dayTicks(server);
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return Optional.ofNullable(server.getPlayerList().getPlayer(player));
		}

		@Override
		public boolean fogOn(ServerPlayer player) {
			return TheScreenshot.fogOn(player);
		}

		@Override
		public boolean send(ServerPlayer player, ScreenshotStatePayload payload) {
			if (!ServerPlayNetworking.canSend(player, ScreenshotStatePayload.TYPE)) {
				return false;
			}
			ServerPlayNetworking.send(player, payload);
			return true;
		}
	};

	/** The game's screenshot. */
	public static final TheScreenshot LIVE = new TheScreenshot(LIVE_HOOKS);

	/** How a report from a client went. */
	public enum Report {
		/** The player was not armed, or their arm was not ready (paused or run out): nothing is recorded. */
		IGNORED,
		/** The world's screenshot, recorded now; everyone disarmed. */
		RECORDED,
		/** Armed again by a debug fire after it happened: the first record stays; everyone disarmed. */
		ALREADY_RECORDED
	}

	/** One player's arm: in-game time it runs out at, its number, and the state their client was told last. */
	private record Arm(long untilDayTicks, int id, ScreenshotStatePayload sent) {
	}

	private final Hooks hooks;
	private final Map<UUID, Arm> arms = new LinkedHashMap<>();
	private int lastArmId;

	public TheScreenshot(Hooks hooks) {
		this.hooks = hooks;
	}

	/** Why {@code the_screenshot} cannot arm this player now ({@link ScreenshotRules#gate}), or empty. */
	public Optional<String> gate(ServerPlayer player, ServerLevel level) {
		MinecraftServer server = level.getServer();
		return ScreenshotRules.gate(level.dimension() == Level.OVERWORLD, hooks.stage(server), hooks.data(server).screenshotTaken(), !arms.isEmpty(),
				player.isAlive(), player.isSpectator(), hooks.lastMinute(server), hooks.storyOver(server));
	}

	/**
	 * Arms the player's client for {@link EntityConfig#screenshotArmTicks} of in-game time, the only arm in the world (a
	 * new arm ends anyone else's, a debug fire's included, and replaces theirs): {@link FireResult#FIRED}, or
	 * {@link FireResult#SKIPPED} without arming if the story holds no screenshot now (Ending D's last minute, or over for
	 * good) or their client cannot take the payload (it could never show him).
	 */
	public FireResult arm(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (storyHolds(server)) {
			return FireResult.SKIPPED;
		}
		int id = lastArmId + 1;
		ScreenshotStatePayload state = state(server, player, id);
		if (!hooks.send(player, state)) {
			return FireResult.SKIPPED;
		}
		lastArmId = id;
		for (UUID other : new ArrayList<>(arms.keySet())) {
			if (!other.equals(player.getUUID())) {
				disarm(server, other);
			}
		}
		arms.put(player.getUUID(), new Arm(hooks.dayTicks(server) + EntityConfig.get().screenshotArmTicks(), id, state));
		return FireResult.FIRED;
	}

	public boolean isArmed(UUID player) {
		return arms.containsKey(player);
	}

	/** Everyone armed now (at most one), in the order they were armed. */
	public List<UUID> armed() {
		return List.copyOf(arms.keySet());
	}

	/** In-game ticks left on that player's arm, or -1 if they are not armed. */
	public long ticksLeft(MinecraftServer server, UUID player) {
		Arm arm = arms.get(player);
		return arm == null ? -1L : Math.max(0L, arm.untilDayTicks() - hooks.dayTicks(server));
	}

	/** The state that player's client was told last, or empty if they are not armed. */
	public Optional<ScreenshotStatePayload> told(UUID player) {
		Arm arm = arms.get(player);
		return arm == null ? Optional.empty() : Optional.of(arm.sent());
	}

	/** Ends that player's arm; their client is told if they are still online. */
	public void disarm(MinecraftServer server, UUID player) {
		if (arms.remove(player) != null) {
			hooks.online(server, player).ifPresent(p -> hooks.send(p, ScreenshotStatePayload.OFF));
		}
	}

	/** Ends every arm (the screenshot happened, the story holds none, or debug). */
	public void disarmAll(MinecraftServer server) {
		for (UUID player : new ArrayList<>(arms.keySet())) {
			disarm(server, player);
		}
	}

	/** PARTICIPATION: a player who stops taking part (left, or turned spectator) loses their arm. */
	public void onParticipation(MinecraftServer server, UUID player, boolean participating) {
		if (!participating) {
			disarm(server, player);
		}
	}

	/** Every server tick; looks at the arms every {@link #TICK_EVERY} ticks. */
	public void tick(MinecraftServer server) {
		if (!arms.isEmpty() && server.getTickCount() % TICK_EVERY == 0) {
			check(server);
		}
	}

	/**
	 * Looks at every arm now: during Ending D's last minute or once the story is over, every arm ends; one whose player
	 * is gone or spectating ends; one that ran out ends and its client is told; for the rest, the client is told again
	 * whenever what it may do changed (paused or not, fog on or not, the weather's fog end by more than
	 * {@link #FOG_END_STEP} blocks).
	 */
	public void check(MinecraftServer server) {
		if (arms.isEmpty()) {
			return;
		}
		if (storyHolds(server)) {
			disarmAll(server);
			return;
		}
		long now = hooks.dayTicks(server);
		for (UUID uuid : new ArrayList<>(arms.keySet())) {
			Arm arm = arms.get(uuid);
			Optional<ServerPlayer> player = hooks.online(server, uuid);
			if (player.isEmpty() || player.get().isSpectator()) {
				disarm(server, uuid);
				continue;
			}
			if (now >= arm.untilDayTicks()) {
				disarm(server, uuid);
				continue;
			}
			ScreenshotStatePayload state = state(server, player.get(), arm.id());
			if (changed(arm.sent(), state) && hooks.send(player.get(), state)) {
				arms.put(uuid, new Arm(arm.untilDayTicks(), arm.id(), state));
			}
		}
	}

	/**
	 * A client says the screenshot happened ({@link ScreenshotTakenPayload}). Only from a player whose arm is ready at
	 * this tick (armed, not run out, not paused: he is not out anywhere, and the story still holds one): the world's one
	 * screenshot is recorded (the day, the player; it counts as that day's sighting) and everyone is disarmed. A report
	 * from an arm that is not ready is ignored and ends that arm (their client forgot it when it reported). No message,
	 * no sound.
	 */
	public Report report(ServerPlayer player) {
		Arm arm = arms.get(player.getUUID());
		if (arm == null) {
			return Report.IGNORED;
		}
		MinecraftServer server = player.level().getServer();
		long now = hooks.dayTicks(server);
		boolean ready = now < arm.untilDayTicks() && !player.isSpectator() && !storyHolds(server) && !hooks.heIsOut(server);
		if (!ready) {
			disarm(server, player.getUUID());
			return Report.IGNORED;
		}
		long day = Math.floorDiv(now, DAY_TICKS);
		boolean recorded = hooks.data(server).recordScreenshot(player.getUUID(), day);
		disarmAll(server);
		return recorded ? Report.RECORDED : Report.ALREADY_RECORDED;
	}

	/** Forgets every arm without telling anyone (server stop). */
	public void clear() {
		arms.clear();
	}

	/** No arm is kept or made now: Ending D's last minute, or the story is over or quiet for good. */
	private boolean storyHolds(MinecraftServer server) {
		return hooks.lastMinute(server) || hooks.storyOver(server);
	}

	private ScreenshotStatePayload state(MinecraftServer server, ServerPlayer player, int id) {
		return new ScreenshotStatePayload(!hooks.heIsOut(server), hooks.fogOn(player), hooks.weatherFogEnd(player), id);
	}

	/** True if the client should be told {@code now} after {@code sent}. */
	static boolean changed(ScreenshotStatePayload sent, ScreenshotStatePayload now) {
		if (sent.armed() != now.armed() || sent.fogOn() != now.fogOn() || sent.arm() != now.arm()) {
			return true;
		}
		float a = sent.fogEnd();
		float b = now.fogEnd();
		if (Float.isNaN(a) || Float.isNaN(b)) {
			return Float.isNaN(a) != Float.isNaN(b);
		}
		return Math.abs(a - b) > FOG_END_STEP;
	}

	/** Live: the story is over or quiet for good for the screenshot ({@link ScreenshotRules#storyOver} on the world's flags). */
	public static boolean storyOver(MinecraftServer server) {
		return ScreenshotRules.storyOver(HerobrineState.get(server)::hasFlag);
	}

	/**
	 * Live: the weather's fog end for this player in blocks ({@code FogLimits.of(player)}: their render distance pulled
	 * in by the dusk fog and a fog spell, the spell weighted by the sky light at their eyes), never a surge or an effect.
	 * The client draws the same fog under open sky; a nearer one is pulled in by something else.
	 */
	public static float weatherFogEnd(ServerPlayer player) {
		return (float) FogLimits.of(player).fogEnd();
	}

	/**
	 * Live: a dusk fog or fog spell on at this player now ({@link ScreenshotRules#fogAmount} at least
	 * {@link EntityConfig#screenshotFogOnAmount}): the world's dusk fog level and spell, on their level's clock, the spell
	 * weighted by the sky light at their eyes, as {@code FogLimits.of(player)} has them; never where time stands still.
	 */
	public static boolean fogOn(ServerPlayer player) {
		Level level = player.level();
		if (level.dimensionType().hasFixedTime()) {
			return false;
		}
		HerobrineState.Effects effects = HerobrineState.get(level.getServer()).effects();
		long clock = level.getDefaultClockTime();
		int sky = level.getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition()));
		double amount = ScreenshotRules.fogAmount(effects.duskFogLevel(), clock, FogLimits.shape(), FogLimits.spellAmount(effects.fogSpell(), clock), sky);
		return ScreenshotRules.fogOn(amount, EntityConfig.get().screenshotFogOnAmount);
	}
}
