package com.forzacode.a1016_02.atmosphere.sc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.card.SoundOut;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 06's episodes (docs/SELF_CREATED.md; card {@code sc_06}). The card fits on the surface at night or in fog, on foot,
 * walking, alone, once a night per player ({@link #refusal}); its fire starts an episode ({@link #start}). After
 * {@code walkSecondsBeforeStart} more of walking, each of the player's own steps (server side, {@link #onStep}, from
 * atmosphere's step mixin) is answered {@code lagTicksMin..Max} ticks later by one step {@code behindMin..Max} blocks
 * behind them along their way, on the ground there, that ground's own step ({@link SoundCues#stepType},
 * {@code SoundSource.PLAYERS}) at {@code stepVolume}, for {@code stepsMin..Max} steps. When they stop (no step for
 * {@link #STOP_TICKS}) one last step, then the end. It ends at once when they look within {@code lookBackDegrees} of
 * behind, anyone comes within {@link #ALONE_RADIUS}, or they are no longer on foot on the surface. The fake answers
 * nothing: their own step once more, at their feet, a beat after they stop. Sneaking makes no step (vanilla), so the
 * follower is silent too. Memory by UUID (D-062.1), in memory only. Server thread.
 */
public final class InStep {
	/** Alone: nobody else (spectators and the dead included) within this many blocks (the shared moment radius). */
	public static final double ALONE_RADIUS = 48.0;
	/** No step for this many ticks: they stopped. */
	public static final int STOP_TICKS = 10;
	/** Sky light at the feet for the surface. */
	public static final int SURFACE_SKY = 10;
	/** Night by the clock: from this time of day... */
	public static final long NIGHT_FROM = 13_000L;
	/** ...until this one. */
	public static final long NIGHT_UNTIL = 23_000L;
	/** A dusk fog this thick at the player counts as fog. */
	public static final double FOG_ON = 0.1;
	/** Walking now: at least this far (horizontal) over the last {@link #WALK_WINDOW_TICKS}. */
	public static final double WALK_BLOCKS = 2.0;
	public static final int WALK_WINDOW_TICKS = 60;
	/** Positions are sampled this often for the walking rule. */
	public static final int SAMPLE_TICKS = 10;
	/** A fired episode waits this long for its walk to start, then ends unheard (also the debug arm's time). */
	public static final int WAIT_TICKS = 2400;
	/** The way they walk is taken over about this many ticks. */
	public static final int TRAIL_TICKS = 20;

	/** The live one: the server's players, sounds and watch. */
	public static final InStep LIVE = new InStep(Services::watch, SoundOut.EVERYONE_IN_RANGE, ServerLevel::players);

	final Supplier<PlayerWatch> watch;
	final SoundOut sounds;
	/** Who could hear (the alone rule's listeners): the level's players, spectators and the dead included. */
	final Function<ServerLevel, List<ServerPlayer>> listeners;
	/** The clock episodes run on: the server's ticks. A test drives its own. */
	public ToLongFunction<MinecraftServer> ticks = MinecraftServer::getTickCount;
	/** Sky light at the player's feet (the surface rule). A test gives its own: a fresh test area is lit late. */
	public ToIntFunction<ServerPlayer> skyLight = player -> player.level().getBrightness(LightLayer.SKY, player.blockPosition());
	/** Time of day in the player's level (the night rule). */
	public ToLongFunction<ServerPlayer> timeOfDay = player -> Gates.timeOfDay(player.level());
	/** A dusk fog or a counting fog spell at the player ({@link #fogOn(ServerPlayer)}). */
	public Predicate<ServerPlayer> fog = InStep::fogOn;

	private final Map<UUID, Episode> episodes = new HashMap<>();
	private final Map<UUID, Long> nightUsed = new HashMap<>();
	private final Map<UUID, ArrayDeque<Sample>> samples = new HashMap<>();
	private final Map<UUID, Arm> arms = new HashMap<>();
	private final Map<UUID, String> endedWhy = new HashMap<>();

	private record Sample(long tick, Vec3 pos) {
	}

	/** A debug arm: within {@code deadline}, the first moment the card fits, it fires through the director. */
	private record Arm(long deadline, boolean fake, boolean anytime, @Nullable UUID op) {
	}

	/** One answer: what was played, where, and its floor. */
	public record Played(long tick, Vec3 pos, BlockPos floor, float volume, boolean own) {
	}

	/** One player's episode. */
	static final class Episode {
		final UUID player;
		final ServerLevel level;
		final boolean fake;
		final long deadline;
		final double behind;
		final int steps;
		final int walkNeeded;
		final RandomSource random;
		final ArrayDeque<Long> due = new ArrayDeque<>();
		final ArrayDeque<Sample> trail = new ArrayDeque<>();
		final List<Played> played = new ArrayList<>();
		Vec3 way;
		long lastStep = -1;
		int walked;
		boolean following;
		boolean stopped;
		int answered;
		String ended = "";

		Episode(UUID player, ServerLevel level, boolean fake, long deadline, double behind, int steps, int walkNeeded, RandomSource random, Vec3 way) {
			this.player = player;
			this.level = level;
			this.fake = fake;
			this.deadline = deadline;
			this.behind = behind;
			this.steps = steps;
			this.walkNeeded = walkNeeded;
			this.random = random;
			this.way = way;
		}
	}

	public InStep(Supplier<PlayerWatch> watch, SoundOut sounds, Function<ServerLevel, List<ServerPlayer>> listeners) {
		this.watch = watch;
		this.sounds = sounds;
		this.listeners = listeners;
	}

	// --- pure ---

	/** The night a fire is counted on: the clock's 24000-tick period from 12000 (a night, its morning and the day after). */
	public static long nightKey(long clock) {
		return Math.floorDiv(clock - 12_000L, Curves.DAY);
	}

	/** Night by the clock (13000 to 23000). */
	public static boolean night(long timeOfDay) {
		return Gates.inWindow(timeOfDay, NIGHT_FROM, NIGHT_UNTIL);
	}

	/** Whether {@code look} (any length) is within {@code degrees} of {@code behind} (horizontal). */
	public static boolean lookingBack(Vec3 look, Vec3 behind, double degrees) {
		double lx = look.x;
		double lz = look.z;
		double bx = behind.x;
		double bz = behind.z;
		double ll = Math.sqrt(lx * lx + lz * lz);
		double bl = Math.sqrt(bx * bx + bz * bz);
		if (ll < 1.0E-6 || bl < 1.0E-6) {
			return false;
		}
		double cos = (lx * bx + lz * bz) / (ll * bl);
		return cos >= Math.cos(Math.toRadians(degrees));
	}

	/** The spot behind: {@code behind} blocks back from {@code at} along {@code way} (horizontal, any length). */
	public static Vec3 behind(Vec3 at, Vec3 way, double behind) {
		double l = Math.sqrt(way.x * way.x + way.z * way.z);
		if (l < 1.0E-6) {
			return at;
		}
		return new Vec3(at.x - way.x / l * behind, at.y, at.z - way.z / l * behind);
	}

	// --- the rules, live ---

	/** On foot on the ground: not riding, swimming, in water, flying or gliding. */
	public static boolean onFoot(ServerPlayer player) {
		return player.onGround() && !player.isPassenger() && !player.isSwimming() && !player.isInWater() && !player.isFallFlying()
				&& !player.getAbilities().flying && !player.isSpectator();
	}

	/** The surface: sky light at the feet {@link #SURFACE_SKY} or more. */
	public static boolean surface(int skyLight) {
		return skyLight >= SURFACE_SKY;
	}

	/** Pure: a dusk fog of at least {@link #FOG_ON} (its level times its weight at this time), or a fog spell that counts. */
	public static boolean fogOn(float duskLevel, long clock, Optional<FogSpell> spell, FogLimits.Shape shape) {
		double dusk = Math.clamp(duskLevel, 0.0F, 1.0F) * FogLimits.duskWeight(Math.floorMod(clock, Curves.DAY), shape);
		return dusk >= FOG_ON || spell.map(s -> s.counts(clock)).orElse(false);
	}

	/** {@link #fogOn(float, long, Optional, FogLimits.Shape)} with the world's dusk fog and spell on the player's level clock; never where time is fixed. */
	public static boolean fogOn(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.dimensionType().hasFixedTime()) {
			return false;
		}
		HerobrineState.Effects effects = HerobrineState.get(level.getServer()).effects();
		return fogOn(effects.duskFogLevel(), level.getDefaultClockTime(), effects.fogSpell(), FogLimits.shape());
	}

	/** Alone ({@link SoundCues#alone}) among this instance's listeners. */
	boolean alone(ServerPlayer player) {
		return SoundCues.alone(player, ALONE_RADIUS, watch.get(), listeners.apply(player.level()));
	}

	/** Walking now: moved at least {@link #WALK_BLOCKS} (horizontal) over the last {@link #WALK_WINDOW_TICKS}, by the samples. */
	public boolean walking(ServerPlayer player, long now) {
		ArrayDeque<Sample> ring = samples.get(player.getUUID());
		if (ring == null || ring.isEmpty()) {
			return false;
		}
		Vec3 here = player.position();
		for (Sample s : ring) {
			if (now - s.tick() <= WALK_WINDOW_TICKS + SAMPLE_TICKS) {
				double dx = here.x - s.pos().x;
				double dz = here.z - s.pos().z;
				return dx * dx + dz * dz >= WALK_BLOCKS * WALK_BLOCKS;
			}
		}
		return false;
	}

	/** Records where the player is now (the walking rule's samples; tests feed their own). */
	public void sample(ServerPlayer player, long now) {
		ArrayDeque<Sample> ring = samples.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
		ring.addLast(new Sample(now, player.position()));
		while (ring.size() > WALK_WINDOW_TICKS / SAMPLE_TICKS + 2) {
			ring.removeFirst();
		}
	}

	/**
	 * Why the card does not fit this player now, or empty: the story ({@link ScGates}), the overworld, combat, the
	 * surface, night or fog (unless {@code anytime}), on foot, walking, alone, once a night (unless {@code anyNight}),
	 * one at a time.
	 */
	public Optional<String> refusal(ServerPlayer player, long now, boolean anytime, boolean anyNight) {
		ServerLevel level = player.level();
		Optional<String> story = ScGates.why(level.getServer());
		if (story.isPresent()) {
			return story;
		}
		if (!ScGates.overworld(level)) {
			return Optional.of("not in the overworld");
		}
		if (!ScGates.noCombat(watch.get().ticksSinceCombat(player))) {
			return Optional.of("in combat lately");
		}
		if (episodes.containsKey(player.getUUID())) {
			return Optional.of("one is running");
		}
		if (!anyNight && nightUsed.getOrDefault(player.getUUID(), Long.MIN_VALUE) == nightKey(level.getDefaultClockTime())) {
			return Optional.of("already tonight");
		}
		if (!surface(skyLight.applyAsInt(player))) {
			return Optional.of("not on the surface");
		}
		if (!anytime && !night(timeOfDay.applyAsLong(player)) && !fog.test(player)) {
			return Optional.of("not night and no fog");
		}
		if (!onFoot(player)) {
			return Optional.of("not on foot on the ground");
		}
		if (!walking(player, now)) {
			return Optional.of("not walking");
		}
		if (!alone(player)) {
			return Optional.of("not alone");
		}
		return Optional.empty();
	}

	/** The card's fire: an episode for the player (their night counted); false if one runs. */
	public boolean start(ServerPlayer player, boolean fake, RandomSource random) {
		UUID uuid = player.getUUID();
		if (episodes.containsKey(uuid)) {
			return false;
		}
		Sc06Config cfg = Sc06Config.get();
		ServerLevel level = player.level();
		long now = ticks.applyAsLong(level.getServer());
		double lo = Math.min(cfg.behindMin, cfg.behindMax);
		double behind = lo + random.nextDouble() * Math.max(0.0, Math.max(cfg.behindMin, cfg.behindMax) - lo);
		int sLo = Math.max(1, Math.min(cfg.stepsMin, cfg.stepsMax));
		int steps = sLo + random.nextInt(Math.max(cfg.stepsMin, cfg.stepsMax) - sLo + 1);
		Vec3 look = player.getViewVector(1.0F);
		endedWhy.remove(uuid);
		episodes.put(uuid, new Episode(uuid, level, fake, now + WAIT_TICKS, behind, steps, AtmosphereConfig.ticks(cfg.walkSecondsBeforeStart),
				RandomSource.create(random.nextLong()), new Vec3(look.x, 0.0, look.z)));
		nightUsed.put(uuid, nightKey(level.getDefaultClockTime()));
		return true;
	}

	public boolean running(UUID player) {
		return episodes.containsKey(player);
	}

	/** The tick of the player's last own step their episode saw (-1 for none or no episode). */
	public long lastStep(UUID player) {
		Episode e = episodes.get(player);
		return e == null ? -1L : e.lastStep;
	}

	/** Why the player's last episode ended, if it ended in this instance's memory (tests, debug); "" while it runs or never ran. */
	public String ended(UUID player) {
		return endedWhy.getOrDefault(player, "");
	}

	/** What the player's episode played so far (tests, debug). */
	public List<Played> played(UUID player) {
		Episode e = episodes.get(player);
		return e == null ? List.of() : List.copyOf(e.played);
	}

	/** The live hook ({@code EntityStepMixin}): one of the player's own steps. */
	public static void onStep(ServerPlayer player, BlockPos onPos, BlockState onState) {
		LIVE.step(player);
	}

	/** One of the player's own steps, now: answered later while following (nothing without an episode). */
	public void step(ServerPlayer player) {
		if (episodes.isEmpty()) {
			return;
		}
		Episode e = episodes.get(player.getUUID());
		if (e == null || e.level != player.level()) {
			return;
		}
		long now = ticks.applyAsLong(player.level().getServer());
		e.lastStep = now;
		if (e.following && !e.fake && !e.stopped) {
			e.due.addLast(now + lag(e.random));
		}
	}

	private static int lag(RandomSource random) {
		Sc06Config cfg = Sc06Config.get();
		int lo = Math.max(1, Math.min(cfg.lagTicksMin, cfg.lagTicksMax));
		return lo + random.nextInt(Math.max(cfg.lagTicksMin, cfg.lagTicksMax) - lo + 1);
	}

	/** Every server tick: samples (every {@link #SAMPLE_TICKS}), the debug arms, and each episode. */
	public void tick(MinecraftServer server) {
		long now = ticks.applyAsLong(server);
		if (now % SAMPLE_TICKS == 0) {
			for (ServerPlayer player : watch.get().participants(server)) {
				if (ScGates.overworld(player.level())) {
					sample(player, now);
				}
			}
			if (!arms.isEmpty()) {
				tickArms(server, now);
			}
		}
		if (episodes.isEmpty()) {
			return;
		}
		for (Iterator<Episode> it = episodes.values().iterator(); it.hasNext(); ) {
			Episode e = it.next();
			String end = tick(server, e, now);
			if (end != null) {
				e.ended = end;
				endedWhy.put(e.player, end);
				it.remove();
			}
		}
	}

	/** One episode's tick; the reason it ends, or null. */
	private @Nullable String tick(MinecraftServer server, Episode e, long now) {
		PlayerWatch w = watch.get();
		ServerPlayer player = w.participant(server, e.player).filter(p -> w.isTargetable(p) && p.level() == e.level).orElse(null);
		if (player == null) {
			return "gone";
		}
		if (!ScGates.storyAllows(server)) {
			return "the story holds none";
		}
		e.trail.addLast(new Sample(now, player.position()));
		while (e.trail.size() > TRAIL_TICKS + 1) {
			e.trail.removeFirst();
		}
		Sample oldest = e.trail.peekFirst();
		Vec3 moved = player.position().subtract(oldest.pos());
		if (moved.x * moved.x + moved.z * moved.z > 0.25) {
			e.way = new Vec3(moved.x, 0.0, moved.z);
		}
		if (!onFoot(player) || !surface(skyLight.applyAsInt(player))) {
			return "left the ground";
		}
		if (!alone(player)) {
			return "not alone";
		}
		boolean stepping = e.lastStep >= 0 && now - e.lastStep <= STOP_TICKS;
		if (!e.following) {
			if (now > e.deadline) {
				return "never walked";
			}
			e.walked = stepping ? e.walked + 1 : 0;
			if (e.walked >= e.walkNeeded) {
				e.following = true;
			}
			return null;
		}
		Sc06Config cfg = Sc06Config.get();
		if (!e.fake && lookingBack(player.getViewVector(1.0F), e.way.scale(-1.0), cfg.lookBackDegrees)) {
			return "looked back";
		}
		if (!stepping && !e.stopped) {
			e.stopped = true;
			e.due.addLast(now + lag(e.random));
		}
		while (!e.due.isEmpty() && e.due.peekFirst() <= now) {
			e.due.removeFirst();
			if (e.fake) {
				if (e.stopped) {
					own(player, e, now);
					return "done";
				}
				continue;
			}
			answer(player, e, now, (float) cfg.stepVolume);
			if (++e.answered >= e.steps) {
				return "done";
			}
		}
		return e.stopped && e.due.isEmpty() ? "done" : null;
	}

	/** One follower step behind the player on the ground there (none if there is no ground). */
	private void answer(ServerPlayer player, Episode e, long now, float volume) {
		ServerLevel level = player.level();
		Vec3 spot = behind(player.position(), e.way, e.behind);
		BlockPos floor = ground(level, BlockPos.containing(spot.x, player.getY(), spot.z));
		if (floor == null) {
			return;
		}
		SoundType type = SoundCues.stepType(level, floor);
		Vec3 at = new Vec3(spot.x, floor.getY() + 1.0, spot.z);
		sounds.play(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(type.getStepSound()), SoundSource.PLAYERS, at, volume, type.getPitch());
		e.played.add(new Played(now, at, floor, volume, false));
	}

	/** The fake: the player's own step once more, at their feet, as vanilla plays it. */
	@SuppressWarnings("deprecation")
	private void own(ServerPlayer player, Episode e, long now) {
		ServerLevel level = player.level();
		BlockPos floor = player.getOnPosLegacy();
		if (level.getBlockState(floor).isAir()) {
			return;
		}
		SoundType type = SoundCues.stepType(level, floor);
		float volume = type.getVolume() * 0.15F;
		sounds.play(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(type.getStepSound()), SoundSource.PLAYERS, player.position(), volume, type.getPitch());
		e.played.add(new Played(now, player.position(), floor, volume, true));
	}

	/**
	 * The ground in this column near {@code near}'s height: the highest cell from 2 above to 4 below whose block holds a
	 * walker (a collision shape) with two free cells over it; null if none or not loaded.
	 */
	public static @Nullable BlockPos ground(ServerLevel level, BlockPos near) {
		if (!level.isLoaded(near)) {
			return null;
		}
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos floor = near.offset(0, dy - 1, 0);
			BlockPos feet = floor.above();
			if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
				return floor;
			}
		}
		return null;
	}

	// --- debug arms ---

	/** {@code /a1016 sc 06 arm [fake] [anytime]}: within {@link #WAIT_TICKS}, the first moment it fits, it fires (forced, through the director). */
	public void arm(ServerPlayer player, boolean fake, boolean anytime, @Nullable UUID op) {
		arms.put(player.getUUID(), new Arm(ticks.applyAsLong(player.level().getServer()) + WAIT_TICKS, fake, anytime, op));
	}

	private void tickArms(MinecraftServer server, long now) {
		for (Iterator<Map.Entry<UUID, Arm>> it = arms.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Arm> entry = it.next();
			Arm arm = entry.getValue();
			ServerPlayer player = watch.get().participant(server, entry.getKey()).filter(watch.get()::isTargetable).orElse(null);
			if (player == null || now > arm.deadline()) {
				ScDebug.tell(server, arm.op(), "sc 06: the arm ran out");
				it.remove();
				continue;
			}
			if (refusal(player, now, arm.anytime(), true).isEmpty()) {
				it.remove();
				var result = Services.director().fire(server, player, "sc_06", arm.fake());
				ScDebug.tell(server, arm.op(), "sc 06: fired for " + player.getPlainTextName() + " (" + result + "), keep walking");
			}
		}
	}

	/** One line of state for the debug command. */
	public String describe(ServerPlayer player) {
		long now = ticks.applyAsLong(player.level().getServer());
		Episode e = episodes.get(player.getUUID());
		String episode = e == null ? "no episode" : String.format(Locale.ROOT, "episode%s: %s, %d of %d answered, %.1f behind", e.fake ? " (fake)" : "",
				e.following ? (e.stopped ? "stopped" : "following") : "waiting for the walk (" + e.walked + "/" + e.walkNeeded + ")", e.answered, e.steps, e.behind);
		Arm arm = arms.get(player.getUUID());
		String armed = arm == null ? "" : "; armed for " + Math.max(0, arm.deadline() - now) / 20 + " s" + (arm.anytime() ? " (anytime)" : "");
		return episode + armed + "; now: " + refusal(player, now, false, false).orElse("fits");
	}

	/** Server ticks the player's debug arm has left, or -1 if they have none. */
	public long armTicksLeft(MinecraftServer server, UUID player) {
		Arm arm = arms.get(player);
		return arm == null ? -1L : Math.max(0L, arm.deadline() - ticks.applyAsLong(server));
	}

	/** True if the player already had it in the night that holds this overworld clock. */
	public boolean spent(UUID player, long clock) {
		return nightUsed.getOrDefault(player, Long.MIN_VALUE) == nightKey(clock);
	}

	/** That player stopped taking part: their episode, samples and arm go (their night stays). */
	public void onLeave(UUID player) {
		episodes.remove(player);
		samples.remove(player);
		arms.remove(player);
		endedWhy.remove(player);
	}

	/** The switch's revert and the server's stop: nothing runs any more. */
	public void clear() {
		episodes.clear();
		samples.clear();
		arms.clear();
		endedWhy.clear();
	}

	/** The server stopped: also the nights. */
	public void reset() {
		clear();
		nightUsed.clear();
	}
}
