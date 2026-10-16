package com.forzacode.a1016_02.atmosphere.sc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.AtmosphereData;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.FogSpell;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 04 (docs/SELF_CREATED.md; no card, a night roll like fog weather, {@code sections.sc04}). Rolled once a night right
 * after fog weather's night roll ({@code FogWeather.check}), only before the moon rises (by {@link #SKY_FROM}), for a
 * night with no fog spell over {@link #FOG_FROM} to {@link #FOG_UNTIL} and, with {@code brightPhasesOnly}, a full or
 * gibbous moon of its own; never in Alone, never within {@code minNightsApart} nights of the last one, at most
 * {@code maxPerWorld} in a world, at the stage's chance. Such a night has three parts, each from its own window on the
 * overworld clock:
 * <ul>
 * <li>the sky ({@link #sky}, from {@link #SKY_FROM} of that day to {@link #SKY_UNTIL} of the next, the moon below the
 * horizon at both ends): clients draw {@link MoonPhase#NEW_MOON} in the overworld ({@link #moonFor}); the window goes to
 * everyone, spectators included, on every change and on join ({@link EmptyNightPayload});</li>
 * <li>the spawns ({@link #QUIET_FROM} to {@link #QUIET_UNTIL}): no natural spawn of a {@link MobCategory#MONSTER} mob
 * within {@code spawnRadius} (horizontal) of any participant in the overworld ({@link #refusesSpawn}, through
 * atmosphere's spawn rules mixin), and no phantom or village siege spawner runs in the overworld
 * ({@link #holdsSpawner});</li>
 * <li>the quiet (the same window): animals ({@link MobCategory#CREATURE}) within {@code quietRadius} of a participant
 * are silenced ({@code MobTamper.silence}, renewed every {@link #SILENCE_EVERY} ticks, never past the window's end).
 * Monsters alive at dusk are left as they are and never silenced.</li>
 * </ul>
 * A clock set back past the roll undoes it ({@link #settle}, as fog weather's undo does): the night rolls again when it
 * comes. Memory ({@link Memory}) in {@code atmosphere.dat}; a file from before loads with none. Server thread, except
 * {@link #refusesSpawn} and {@link #holdsSpawner}, which read a volatile snapshot.
 */
public final class EmptyNight {
	public static final long DAY = 24_000L;
	public static final long NEVER = Long.MIN_VALUE;
	/** The sky window starts at this time of the night's own day... */
	public static final long SKY_FROM = 11_000L;
	/** ...and ends at this time of the next day. */
	public static final long SKY_UNTIL = 1_000L;
	/** No spawns and quiet animals from this time of the night's day... */
	public static final long QUIET_FROM = 12_000L;
	/** ...until this one. */
	public static final long QUIET_UNTIL = 23_500L;
	/** A fog spell giving any fog between these times of the night's day rules the night out. */
	public static final long FOG_FROM = 13_000L;
	public static final long FOG_UNTIL = 23_000L;
	/** The quiet is renewed this often (server ticks)... */
	public static final int SILENCE_EVERY = 200;
	/** ...for this long each time (never past the window's end). */
	public static final int SILENCE_TICKS = 220;

	/** A window on the overworld clock: {@code from} included, {@code until} excluded. */
	public record Window(long from, long until) {
		public static final StreamCodec<ByteBuf, Window> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Window::from,
				ByteBufCodecs.VAR_LONG, Window::until,
				Window::new);

		public boolean contains(long clock) {
			return clock >= from && clock < until;
		}
	}

	/** What one roll changed, to put back when the clock is set back before it ({@code at}). */
	public record Undo(long at, long rolled, long last, int count, long night) {
		public static final Codec<Undo> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("at").forGetter(Undo::at),
				Codec.LONG.optionalFieldOf("rolled", NEVER).forGetter(Undo::rolled),
				Codec.LONG.optionalFieldOf("last", NEVER).forGetter(Undo::last),
				Codec.INT.optionalFieldOf("count", 0).forGetter(Undo::count),
				Codec.LONG.optionalFieldOf("night", NEVER).forGetter(Undo::night)
		).apply(i, Undo::new));
	}

	/**
	 * The world's memory: the last night the roll ran for ({@code rolled}, a night index: the overworld clock day of its
	 * evening, as fog weather's {@code nightIndex}), the last such night ({@code last}), how many this world had, the
	 * one set now ({@code night}, {@link #NEVER} for none; kept until its sky window ends) and the last roll's undo.
	 */
	public record Memory(long rolled, long last, int count, long night, Optional<Undo> undo) {
		public static final Memory START = new Memory(NEVER, NEVER, 0, NEVER, Optional.empty());
		public static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("rolled", NEVER).forGetter(Memory::rolled),
				Codec.LONG.optionalFieldOf("last", NEVER).forGetter(Memory::last),
				Codec.INT.optionalFieldOf("count", 0).forGetter(Memory::count),
				Codec.LONG.optionalFieldOf("night", NEVER).forGetter(Memory::night),
				Undo.CODEC.optionalFieldOf("undo").forGetter(Memory::undo)
		).apply(i, Memory::new));

		public Memory {
			undo = undo != null ? undo : Optional.empty();
			count = Math.max(0, count);
		}

		/** The sky window of the night set now, if any. */
		public Optional<Window> window() {
			return night == NEVER ? Optional.empty() : Optional.of(sky(night));
		}

		Memory withNight(long newNight) {
			return new Memory(rolled, last, count, newNight, undo);
		}
	}

	/** What the spawn rules read off the server thread: the quiet window and where the participants are. */
	public record Snapshot(Optional<Window> quiet, List<Vec3> participants, double radius) {
		public static final Snapshot NONE = new Snapshot(Optional.empty(), List.of(), 0.0);

		public Snapshot {
			quiet = quiet != null ? quiet : Optional.empty();
			participants = List.copyOf(participants);
		}
	}

	private static volatile Snapshot snapshot = Snapshot.NONE;
	/** The animals this night silenced (so the switch's revert lets only them go). Server thread. */
	private static final Set<Mob> SILENCED = Collections.newSetFromMap(new WeakHashMap<>());

	private EmptyNight() {
	}

	// --- pure ---

	/** The sky window of night {@code night}: {@link #SKY_FROM} of its day to {@link #SKY_UNTIL} of the next. */
	public static Window sky(long night) {
		return new Window(night * DAY + SKY_FROM, (night + 1) * DAY + SKY_UNTIL);
	}

	/** The spawn and quiet window of night {@code night}: {@link #QUIET_FROM} to {@link #QUIET_UNTIL} of its day. */
	public static Window quiet(long night) {
		return new Window(night * DAY + QUIET_FROM, night * DAY + QUIET_UNTIL);
	}

	/** A full or gibbous moon. */
	public static boolean bright(MoonPhase phase) {
		return phase == MoonPhase.FULL_MOON || phase == MoonPhase.WANING_GIBBOUS || phase == MoonPhase.WAXING_GIBBOUS;
	}

	/** The phase vanilla's default moon timeline gives night {@code night}: one phase a day, full on day 0. */
	public static MoonPhase defaultPhase(long night) {
		MoonPhase[] phases = MoonPhase.values();
		return phases[(int) Math.floorMod(night, (long) phases.length)];
	}

	/** The moon drawn at this overworld clock tick: new while the window holds it, else vanilla's. */
	public static MoonPhase moonFor(long clock, Optional<Window> window, MoonPhase vanilla) {
		return window.isPresent() && window.get().contains(clock) ? MoonPhase.NEW_MOON : vanilla;
	}

	/**
	 * {@link #moonFor(long, Optional, MoonPhase)}, or vanilla's moon while a shaderpack draws the frame
	 * ({@code shaderPack}): a pack lights and draws the night from the game's own phase, which this does not change, so
	 * a new moon in the sky would hang over a full moon's light. That client sees the night's quiet only.
	 */
	public static MoonPhase moonFor(long clock, Optional<Window> window, MoonPhase vanilla, boolean shaderPack) {
		return shaderPack ? vanilla : moonFor(clock, window, vanilla);
	}

	/** Whether this spell gives any fog over {@link #FOG_FROM} to {@link #FOG_UNTIL} of night {@code night}. */
	public static boolean fogOver(Optional<FogSpell> spell, long night) {
		if (spell.isEmpty() || !(spell.get().level() > 0.0F)) {
			return false;
		}
		FogSpell s = spell.get();
		return s.fromClock() < night * DAY + FOG_UNTIL && s.untilClock() > night * DAY + FOG_FROM;
	}

	/** Whether the roll for night {@code night} may run now: from fog weather's night roll time until the moon rises. */
	public static boolean rollTime(long now, long night, long rollTimeOfDay) {
		return now >= night * DAY + rollTimeOfDay && now < night * DAY + SKY_FROM;
	}

	/**
	 * Why night {@code night} cannot be one, or empty if it can (then the stage's chance decides): Alone, a fog spell
	 * over it, a dark phase ({@code brightPhasesOnly}), too soon after the last one, or the world's cap.
	 */
	public static Optional<String> refusal(Memory memory, long night, Stage stage, MoonPhase phase, boolean fog, Sc04Config cfg) {
		if (stage == Stage.ALONE) {
			return Optional.of("Alone");
		}
		if (cfg.chance(stage) <= 0.0) {
			return Optional.of("no chance at " + stage);
		}
		if (fog) {
			return Optional.of("a fog spell over the night");
		}
		if (cfg.brightPhasesOnly && !bright(phase)) {
			return Optional.of("its moon is " + phase.getSerializedName());
		}
		if (memory.last() != NEVER && night - memory.last() < cfg.minNightsApart) {
			return Optional.of((night - memory.last()) + " nights after the last (at least " + cfg.minNightsApart + ")");
		}
		if (memory.count() >= cfg.maxPerWorld) {
			return Optional.of(memory.count() + " of " + cfg.maxPerWorld + " in this world");
		}
		return Optional.empty();
	}

	/**
	 * The memory at clock {@code now} (whose fog night index is {@code night}) before anything rolls: a clock set back
	 * before the last roll puts back what it changed ({@link Undo}); set back further than that, the nights ahead roll
	 * again as new ones (the count and the last one stay) and a night set ahead of the clock is dropped; a night whose sky
	 * window is over is dropped.
	 */
	public static Memory settle(Memory memory, long now, long night) {
		Memory m = memory;
		if (m.undo().isPresent() && now < m.undo().get().at()) {
			Undo u = m.undo().get();
			m = new Memory(u.rolled(), u.last(), u.count(), u.night(), Optional.empty());
		}
		if (m.rolled() != NEVER && m.rolled() > night) {
			m = new Memory(night - 1, m.last(), m.count(), m.night(), m.undo());
		}
		if (m.night() != NEVER && (now < sky(m.night()).from() - DAY || now >= sky(m.night()).until())) {
			m = m.withNight(NEVER);
		}
		return m;
	}

	/**
	 * Night {@code night}'s roll at clock {@code now} (once: nothing if it already rolled): an empty night when nothing
	 * refuses it ({@link #refusal}) and the stage's chance comes up, kept with what it changed ({@link Undo}).
	 */
	public static Memory roll(Memory memory, long now, long night, Stage stage, MoonPhase phase, boolean fog, Sc04Config cfg, RandomSource random) {
		if (memory.rolled() != NEVER && memory.rolled() >= night) {
			return memory;
		}
		Undo undo = new Undo(now, memory.rolled(), memory.last(), memory.count(), memory.night());
		boolean empty = refusal(memory, night, stage, phase, fog, cfg).isEmpty() && random.nextDouble() < cfg.chance(stage);
		return empty ? new Memory(night, night, memory.count() + 1, night, Optional.of(undo))
				: new Memory(night, memory.last(), memory.count(), memory.night(), Optional.of(undo));
	}

	/**
	 * Night {@code night} set by hand at clock {@code now}: counted like a rolled one (its own roll, if still to come,
	 * finds it too soon after the last and keeps it); a clock set back before now undoes it.
	 */
	public static Memory forced(Memory memory, long now, long night) {
		Undo undo = new Undo(now, memory.rolled(), memory.last(), memory.count(), memory.night());
		return new Memory(memory.rolled(), night, memory.count() + 1, night, Optional.of(undo));
	}

	/**
	 * Whether a spawn is refused by this snapshot: a natural spawn of a monster in the overworld inside the quiet window
	 * within the radius (horizontal) of a participant.
	 */
	public static boolean refuses(Snapshot s, EntityType<?> type, EntitySpawnReason reason, ResourceKey<Level> dimension, long clock, double x, double z) {
		if (reason != EntitySpawnReason.NATURAL || type.getCategory() != MobCategory.MONSTER || dimension != Level.OVERWORLD) {
			return false;
		}
		if (s.quiet().isEmpty() || !s.quiet().get().contains(clock)) {
			return false;
		}
		double r2 = s.radius() * s.radius();
		for (Vec3 p : s.participants()) {
			double dx = p.x - x;
			double dz = p.z - z;
			if (dx * dx + dz * dz <= r2) {
				return true;
			}
		}
		return false;
	}

	/** The first night on or after {@code from} that nothing refuses by the default moon timeline, at this stage; NEVER if none in 64. */
	public static long nextEligible(Memory memory, long from, Stage stage, Sc04Config cfg) {
		for (long night = from; night < from + 64; night++) {
			if (refusal(memory, night, stage, defaultPhase(night), false, cfg).isEmpty()) {
				return night;
			}
		}
		return NEVER;
	}

	// --- live ---

	/** The spawn rules (atmosphere's {@code SpawnPlacementsMixin}): a natural monster spawn refused tonight. Any thread. */
	public static boolean refusesSpawn(EntityType<?> type, EntitySpawnReason reason, ServerLevel level, double x, double z) {
		Snapshot s = snapshot;
		if (s.quiet().isEmpty() || reason != EntitySpawnReason.NATURAL || !SelfCreated.enabled()) {
			return false;
		}
		return refuses(s, type, reason, level.dimension(), level.getDefaultClockTime(), x, z);
	}

	/** The phantom and village siege spawners hold off in the overworld during the quiet window ({@code noPhantoms}). */
	public static boolean holdsSpawner(ServerLevel level) {
		Snapshot s = snapshot;
		return s.quiet().isPresent() && level.dimension() == Level.OVERWORLD && s.quiet().get().contains(level.getDefaultClockTime())
				&& SelfCreated.enabled() && Sc04Config.get().noPhantoms;
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	/** Replaces the snapshot (the live check every second; tests set their own and put the old one back). */
	public static void snapshot(Snapshot s) {
		snapshot = s != null ? s : Snapshot.NONE;
	}

	/**
	 * Every second, right after fog weather's check ({@code FogWeather.check}) with the night index it rolled for and
	 * whether tonight's fog roll happened: settles the memory, rolls tonight once in its time, sends the sky window on a
	 * change, refreshes the spawn snapshot and, every {@link #SILENCE_EVERY} ticks inside the quiet window, the quiet.
	 */
	public static void check(MinecraftServer server, long night, boolean fogRolledTonight, long rollTimeOfDay) {
		ServerLevel overworld = server.overworld();
		if (overworld.dimensionType().hasFixedTime()) {
			return;
		}
		long now = overworld.getDefaultClockTime();
		AtmosphereData data = AtmosphereData.get(server);
		Memory before = data.emptyNight();
		Memory m = settle(before, now, night);
		Sc04Config cfg = Sc04Config.get();
		boolean allowed = ScGates.storyAllows(server);
		if (allowed && fogRolledTonight && rollTime(now, night, rollTimeOfDay) && (m.rolled() == NEVER || m.rolled() < night)) {
			HerobrineState state = HerobrineState.get(server);
			MoonPhase phase = overworld.environmentAttributes().getDimensionValue(EnvironmentAttributes.MOON_PHASE);
			m = roll(m, now, night, state.stage(), phase, fogOver(state.effects().fogSpell(), night), cfg, overworld.getRandom());
			if (m.night() == night) {
				A1016_02.LOGGER.info("[a1016] sc_04: night {}", night);
			}
		}
		if (!SelfCreated.enabled() && m.night() != NEVER) {
			m = m.withNight(NEVER);
		}
		data.setEmptyNight(m);
		if (!m.window().equals(before.window())) {
			sendAll(server, m.window());
		}
		Optional<Window> quiet = m.night() == NEVER ? Optional.empty() : Optional.of(quiet(m.night()));
		List<Vec3> positions = new ArrayList<>();
		List<ServerPlayer> here = new ArrayList<>();
		if (quiet.isPresent()) {
			for (ServerPlayer player : Services.watch().participants(server)) {
				if (player.level() == overworld) {
					positions.add(player.position());
					here.add(player);
				}
			}
		}
		snapshot(new Snapshot(quiet, positions, cfg.spawnRadius));
		if (quiet.isPresent() && cfg.quietAnimals && server.getTickCount() % SILENCE_EVERY == 0) {
			silence(overworld, here, cfg.quietRadius, silenceTicks(now, quiet.get()));
		}
	}

	/** How long a renewal of the quiet lasts at clock {@code now}: {@link #SILENCE_TICKS}, never past the window's end; 0 outside it. */
	public static int silenceTicks(long now, Window quiet) {
		return quiet.contains(now) ? (int) Math.min(SILENCE_TICKS, quiet.until() - now) : 0;
	}

	/** Silences every animal within {@code radius} of any of {@code players} for {@code ticks}; returns how many. */
	public static int silence(ServerLevel level, List<ServerPlayer> players, double radius, int ticks) {
		if (ticks <= 0) {
			return 0;
		}
		int silenced = 0;
		double r2 = radius * radius;
		for (ServerPlayer player : players) {
			Vec3 at = player.position();
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius), m -> m.isAlive()
					&& m.getType().getCategory() == MobCategory.CREATURE && m.distanceToSqr(at) <= r2
					&& (!MobTamperImpl.INSTANCE.isTampered(m) || SILENCED.contains(m)))) {
				if (Services.mobs().silence(mob, ticks)) {
					SILENCED.add(mob);
					silenced++;
				}
			}
		}
		return silenced;
	}

	/** Whether this night silenced the mob (tests, debug). */
	public static boolean silencedByIt(Mob mob) {
		return SILENCED.contains(mob);
	}

	/**
	 * The switch's revert (idempotent, no chunk loads): the night set now is dropped and everyone is told, the spawn
	 * snapshot is cleared, and the animals it silenced (and nothing else holds) are let go.
	 */
	public static void revert(MinecraftServer server) {
		AtmosphereData data = AtmosphereData.get(server);
		Memory m = data.emptyNight();
		if (m.night() != NEVER) {
			data.setEmptyNight(m.withNight(NEVER));
		}
		sendAll(server, Optional.empty());
		snapshot(Snapshot.NONE);
		releaseSilenced();
	}

	/** Lets go the animals it silenced that nothing else holds (frozen or facing). */
	static void releaseSilenced() {
		for (Mob mob : new ArrayList<>(SILENCED)) {
			if (!MobTamperImpl.INSTANCE.isFrozen(mob) && !MobTamperImpl.INSTANCE.isFacing(mob)) {
				MobTamperImpl.INSTANCE.release(mob);
			}
		}
		SILENCED.clear();
	}

	/** The server stopped. */
	public static void clear() {
		snapshot(Snapshot.NONE);
		SILENCED.clear();
	}

	/** The sky window for a client that joins (none while the layer is off). */
	public static Optional<Window> windowNow(MinecraftServer server) {
		return SelfCreated.enabled() ? AtmosphereData.get(server).emptyNight().window() : Optional.empty();
	}

	/** Sends the sky window to everyone online, spectators included. */
	public static void sendAll(MinecraftServer server, Optional<Window> window) {
		EmptyNightPayload payload = new EmptyNightPayload(window);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			EmptyNightPayload.send(player, payload);
		}
	}

	/** {@code /a1016 sc 04 tonight [force]}: sets tonight (or now, if it is night); without force, refused for fog or a dark moon. */
	public static Optional<String> setTonight(MinecraftServer server, boolean force) {
		Optional<String> story = ScGates.why(server);
		if (story.isPresent()) {
			return story;
		}
		ServerLevel overworld = server.overworld();
		long now = overworld.getDefaultClockTime();
		long night = tonight(now);
		if (!force) {
			if (fogOver(HerobrineState.get(server).effects().fogSpell(), night)) {
				return Optional.of("a fog spell over the night (use force)");
			}
			MoonPhase phase = defaultPhase(night);
			if (Sc04Config.get().brightPhasesOnly && !bright(phase)) {
				return Optional.of("its moon is " + phase.getSerializedName() + " (use force)");
			}
		}
		AtmosphereData data = AtmosphereData.get(server);
		Memory m = forced(data.emptyNight(), now, night);
		data.setEmptyNight(m);
		sendAll(server, m.window());
		A1016_02.LOGGER.info("[a1016] sc_04: night {} (debug)", night);
		return Optional.empty();
	}

	/** {@code /a1016 sc 04 clear}: no night now (the count stays). */
	public static void clearNight(MinecraftServer server) {
		AtmosphereData data = AtmosphereData.get(server);
		data.setEmptyNight(data.emptyNight().withNight(NEVER));
		sendAll(server, Optional.empty());
		releaseSilenced();
	}

	/** The night a command means by tonight: this clock day's, or the next day's once this one's quiet is over. */
	public static long tonight(long now) {
		long day = Math.floorDiv(now, DAY);
		return Math.floorMod(now, DAY) < QUIET_UNTIL ? day : day + 1;
	}

	/** {@code /a1016 sc 04}: the memory, the night set now and its windows, tonight's answer and the next eligible night. */
	public static List<String> status(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getDefaultClockTime();
		Memory m = AtmosphereData.get(server).emptyNight();
		Sc04Config cfg = Sc04Config.get();
		Stage stage = HerobrineState.get(server).stage();
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "now: day %d %d; %d of %d in this world; last on night %s; rolled through night %s", Math.floorDiv(now, DAY),
				Math.floorMod(now, DAY), m.count(), cfg.maxPerWorld, night(m.last()), night(m.rolled())));
		lines.add(m.night() == NEVER ? "set now: none"
				: String.format(Locale.ROOT, "set now: night %d (sky %d to %d, quiet %d to %d)%s", m.night(), sky(m.night()).from(), sky(m.night()).until(),
						quiet(m.night()).from(), quiet(m.night()).until(), quiet(m.night()).contains(now) ? ", in the quiet now" : ""));
		long tonight = tonight(now);
		MoonPhase phase = defaultPhase(tonight);
		Optional<String> why = ScGates.why(server).or(() -> refusal(m, tonight, stage, phase, fogOver(HerobrineState.get(server).effects().fogSpell(), tonight), cfg));
		lines.add(String.format(Locale.ROOT, "tonight (night %d, moon %s): %s", tonight, phase.getSerializedName(),
				why.map(w -> "no: " + w).orElse("may roll (chance " + cfg.chance(stage) + ")")));
		long next = nextEligible(m, tonight, stage, cfg);
		lines.add("next eligible night: " + (next == NEVER ? "none soon" : String.valueOf(next)));
		return lines;
	}

	private static String night(long night) {
		return night == NEVER ? "none" : String.valueOf(night);
	}
}
