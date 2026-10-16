package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc.Home;
import com.forzacode.a1016_02.atmosphere.sc.Sc05Config;
import com.forzacode.a1016_02.atmosphere.sc.ScDebug;
import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 05, coming home (docs/SELF_CREATED.md; cards {@code sc_05} and {@code sc_05b}). Away timers: every
 * {@link #AWAY_TICKS} each participant's time away from their base ({@code PlayerWatch.base}: at least
 * {@code awayDistance}, or another dimension) on their own play clock. A card's fire arms its player for
 * {@code armMinutes} of their play ({@link #arm}; one arm a player at a time, so the other card waits). The approach
 * watcher, every {@link #WATCH_TICKS} for armed players only, plays the moment once they come back from out of its band:
 * <ul>
 * <li>{@link Kind#STEPS}: {@code minDistance..maxDistance} from the house's door ({@link Home}) and coming closer, alone
 * ({@link #ALONE_RADIUS}), every sound cell out of everyone's view: {@code stepsMin..stepsMax} steps {@code stepTicks}
 * apart on the floor inside ({@link Home#walk}), each the floor's own step ({@link SoundCues#stepType},
 * {@code PLAYERS}) at {@code stepVolume}, then after {@code closeDelayMin..Max} the close sound of the player-placed
 * container nearest the last step ({@link ChestOpensCard#sounds}) at {@code closeVolume}. The rest stop once anyone
 * comes within 48, the door opens, or a cell left comes into anyone's view. The fake: one step at half volume.</li>
 * <li>{@link Kind#WAITING}: within {@code approachDistance} of the base: every animal and sitting pet within
 * {@code mobRadius} of it nothing holds (at least {@code minMobs}) is frozen facing the base's bed (or the base point),
 * bound to the target and everyone sharing the moment, and let go once the target is within {@link #RELEASE_BLOCKS} of
 * the spot, has looked at it for {@code lookSeconds}, or {@code holdSecondsMax} passes. The fake: they face the target
 * instead.</li>
 * </ul>
 * Arms and timers by UUID in memory (D-062.1), dropped on PARTICIPATION false. Server thread.
 */
public final class ComingHome {
	public static final int AWAY_TICKS = 20;
	public static final int WATCH_TICKS = 10;
	/** Alone for the steps: nobody else (spectators and the dead included) within this many blocks. */
	public static final double ALONE_RADIUS = 48.0;
	/** The animals are let go once the target stands this close to the spot. */
	public static final double RELEASE_BLOCKS = 3.0;
	/** Looking at the spot: within this many degrees of it. */
	public static final double LOOK_DEGREES = 20.0;
	/** A base's survey (is there a house, how many animals) is taken again after this many ticks. */
	public static final int SURVEY_TICKS = 600;
	/** A distance closer by this much than the sample before is coming closer. */
	public static final double CLOSER = 0.2;

	public static final String STEPS_ID = "sc_05";
	public static final String WAITING_ID = "sc_05b";

	/** Which moment an arm waits for. */
	public enum Kind {
		STEPS, WAITING
	}

	/** One player's arm. {@code until} is on their play clock; {@code left} once they were out of the band. */
	static final class Arm {
		final Kind kind;
		final boolean fake;
		final long until;
		@Nullable UUID op;
		boolean left;
		double last = Double.MAX_VALUE;
		String waiting = "";

		Arm(Kind kind, boolean fake, long until, @Nullable UUID op) {
			this.kind = kind;
			this.fake = fake;
			this.until = until;
			this.op = op;
		}
	}

	/** A base's survey: a house there, how many animals nothing holds, when (server ticks). */
	record Survey(boolean house, int animals, long at) {
	}

	/** The live one. */
	public static final ComingHome LIVE = new ComingHome();

	Supplier<PlayerWatch> watch = Services::watch;
	SoundOut sounds = SoundOut.EVERYONE_IN_RANGE;
	BiPredicate<ServerLevel, AABB> outOfView = (level, box) -> Services.traces().isOutOfView(level, box);
	/** Everyone in a level (spectators and the dead included): the alone rule's listeners. */
	Function<ServerLevel, List<ServerPlayer>> everyone = ServerLevel::players;
	Consumer<Tasks.Episode> episodes = Tasks::start;
	/** A player's own play clock ({@code GameClock.playTicks}); tests use their own. */
	BiFunctionLong playTicks = (server, player) -> GameClock.playTicks(server, player);

	/** {@code GameClock.playTicks(server, uuid)} as a seam. */
	@FunctionalInterface
	interface BiFunctionLong {
		long apply(MinecraftServer server, UUID player);
	}

	private final Map<UUID, Long> awaySince = new HashMap<>();
	private final Map<UUID, Arm> arms = new HashMap<>();
	private final Map<BlockPos, Survey> surveys = new HashMap<>();
	private final List<Tasks.Episode> running = new ArrayList<>();

	ComingHome() {
	}

	// --- the cards' rules ---

	/** The player's base in the overworld, if any. */
	Optional<BlockPos> base(ServerPlayer player) {
		return watch.get().base(player).filter(b -> b.dimension() == net.minecraft.world.level.Level.OVERWORLD).map(GlobalPos::pos);
	}

	/** Ticks of the player's play they have been away from their base now (0 if home or without one). */
	public long awayTicks(MinecraftServer server, UUID player) {
		Long since = awaySince.get(player);
		return since == null ? 0L : Math.max(0L, playTicks.apply(server, player) - since);
	}

	public boolean armed(UUID player) {
		return arms.containsKey(player);
	}

	/** The player's arm (tests, debug), or null. */
	@Nullable Arm armOf(UUID player) {
		return arms.get(player);
	}

	/**
	 * Why {@code kind}'s card does not fit the player now, or empty: the story, the overworld, combat, an arm already, a
	 * base in the overworld, away long enough, and that base's survey (a house; enough animals) while it was last loaded.
	 */
	public Optional<String> refusal(ServerPlayer player, Kind kind) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Optional<String> story = ScGates.why(server);
		if (story.isPresent()) {
			return story;
		}
		if (!ScGates.overworld(level)) {
			return Optional.of("not in the overworld");
		}
		if (!ScGates.noCombat(watch.get().ticksSinceCombat(player))) {
			return Optional.of("in combat lately");
		}
		if (armed(player.getUUID())) {
			return Optional.of("already armed");
		}
		Optional<BlockPos> base = base(player);
		if (base.isEmpty()) {
			return Optional.of("no base in the overworld");
		}
		Sc05Config cfg = Sc05Config.get();
		long away = awayTicks(server, player.getUUID());
		long needed = AtmosphereConfig.ticks(cfg.awayMinutes * 60.0);
		if (away < needed) {
			return Optional.of(String.format(Locale.ROOT, "away %.1f of %.1f minutes", away / 1200.0, cfg.awayMinutes));
		}
		Survey survey = survey(level, base.get(), false);
		if (survey == null) {
			return Optional.of("the base was not seen loaded yet");
		}
		if (kind == Kind.STEPS && !survey.house()) {
			return Optional.of("no house at the base");
		}
		if (kind == Kind.WAITING && survey.animals() < cfg.minMobs) {
			return Optional.of(survey.animals() + " animals at the base (at least " + cfg.minMobs + ")");
		}
		return Optional.empty();
	}

	/** Arms the player for {@code kind}: false (nothing changed) while they already have an arm. */
	public boolean arm(ServerPlayer player, Kind kind, boolean fake, @Nullable UUID op) {
		if (armed(player.getUUID())) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		long until = playTicks.apply(server, player.getUUID()) + AtmosphereConfig.ticks(Sc05Config.get().armMinutes * 60.0);
		arms.put(player.getUUID(), new Arm(kind, fake, until, op));
		return true;
	}

	/** The debug command: what this player's arm does later is told to {@code op} (only them). */
	public void tellTo(UUID player, @Nullable UUID op) {
		Arm arm = arms.get(player);
		if (arm != null) {
			arm.op = op;
		}
	}

	/** A base's survey: taken now if the base is loaded and the last one is older than {@link #SURVEY_TICKS} (or {@code fresh}); null if never seen. */
	@Nullable Survey survey(ServerLevel level, BlockPos base, boolean fresh) {
		long now = level.getServer().getTickCount();
		Survey last = surveys.get(base);
		if ((last == null || fresh || now - last.at() >= SURVEY_TICKS) && level.isLoaded(base)) {
			Sc05Config cfg = Sc05Config.get();
			last = new Survey(Home.find(level, base, watch.get(), cfg).isPresent(), animals(level, base, cfg).size(), now);
			surveys.put(base.immutable(), last);
		}
		return last;
	}

	/** The animals and sitting pets within {@code mobRadius} of the base that nothing holds, nobody rides and none riding. */
	static List<Animal> animals(ServerLevel level, BlockPos base, Sc05Config cfg) {
		Vec3 center = Vec3.atCenterOf(base);
		double r = cfg.mobRadius;
		return AtmosphereCard.untampered(level, Animal.class, center, r, a -> !a.isPassenger() && !a.isVehicle()
				&& (!(a instanceof TamableAnimal pet) || !pet.isTame() || pet.isOrderedToSit()));
	}

	/**
	 * Where the animals look: the base's bed (a base that is a respawn point at a bed is the bed's block), else the base
	 * point; the middle of the block either way.
	 */
	static Vec3 spot(BlockPos base) {
		return Vec3.atCenterOf(base);
	}

	// --- the live tick ---

	/** Every server tick: the away timers and surveys, and the approach watcher for armed players. */
	public void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now % AWAY_TICKS == 0) {
			tickAway(server);
		}
		if (now % WATCH_TICKS == 0 && !arms.isEmpty()) {
			tickArms(server);
		}
		running.removeIf(ComingHome::over);
	}

	void tickAway(MinecraftServer server) {
		Sc05Config cfg = Sc05Config.get();
		PlayerWatch w = watch.get();
		for (ServerPlayer player : w.participants(server)) {
			UUID uuid = player.getUUID();
			Optional<BlockPos> base = base(player);
			if (base.isEmpty()) {
				awaySince.remove(uuid);
				continue;
			}
			boolean away = !ScGates.overworld(player.level()) || player.blockPosition().distSqr(base.get()) >= (double) cfg.awayDistance * cfg.awayDistance;
			if (away) {
				awaySince.putIfAbsent(uuid, playTicks.apply(server, uuid));
				if (ScGates.overworld(player.level())) {
					survey(player.level(), base.get(), false);
				}
			} else {
				awaySince.remove(uuid);
			}
		}
	}

	void tickArms(MinecraftServer server) {
		PlayerWatch w = watch.get();
		for (Iterator<Map.Entry<UUID, Arm>> it = arms.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Arm> entry = it.next();
			UUID uuid = entry.getKey();
			Arm arm = entry.getValue();
			if (playTicks.apply(server, uuid) > arm.until) {
				ScDebug.tell(server, arm.op, "sc 05: the arm ran out");
				it.remove();
				continue;
			}
			ServerPlayer player = w.participant(server, uuid).filter(w::isTargetable).orElse(null);
			if (player == null || !ScGates.overworld(player.level()) || !ScGates.storyAllows(server)) {
				continue;
			}
			Optional<BlockPos> base = base(player);
			if (base.isEmpty()) {
				continue;
			}
			boolean spent = arm.kind == Kind.STEPS ? watchSteps(player, base.get(), arm) : watchWaiting(player, base.get(), arm);
			if (spent) {
				ScDebug.tell(server, arm.op, "sc 05: it played for " + player.getPlainTextName());
				it.remove();
			}
		}
	}

	/** The steps' band: true once the episode started (the arm is spent). */
	boolean watchSteps(ServerPlayer player, BlockPos base, Arm arm) {
		ServerLevel level = player.level();
		Sc05Config cfg = Sc05Config.get();
		double toBase = Math.sqrt(player.blockPosition().distSqr(base));
		if (toBase > cfg.maxDistance + cfg.houseRadius + Home.DOOR_RADIUS) {
			arm.left = true;
			arm.last = Double.MAX_VALUE;
			arm.waiting = "far from home";
			return false;
		}
		Optional<Home.House> house = Home.find(level, base, watch.get(), cfg);
		if (house.isEmpty()) {
			arm.waiting = "no house at the base";
			return false;
		}
		double d = player.position().distanceTo(Vec3.atCenterOf(house.get().door()));
		double last = arm.last;
		arm.last = d;
		if (d > cfg.maxDistance) {
			arm.left = true;
			arm.waiting = "out of the band";
			return false;
		}
		if (d < cfg.minDistance) {
			arm.left = false;
			arm.waiting = "too close (walk away and come back)";
			return false;
		}
		if (!arm.left) {
			arm.waiting = "in the band without having left it";
			return false;
		}
		if (!(d < last - CLOSER)) {
			arm.waiting = "not coming closer";
			return false;
		}
		Optional<String> why = startSteps(player, house.get(), arm.fake, level.getRandom());
		arm.waiting = why.orElse("");
		return why.isEmpty();
	}

	/**
	 * Starts the steps for the player now if every rule holds (alone, no combat, the door shut, a walk inside, every cell
	 * out of view); why not, or empty once started.
	 */
	Optional<String> startSteps(ServerPlayer player, Home.House house, boolean fake, RandomSource random) {
		ServerLevel level = player.level();
		Sc05Config cfg = Sc05Config.get();
		if (!SoundCues.alone(player, ALONE_RADIUS, watch.get(), everyone.apply(level))) {
			return Optional.of("not alone");
		}
		if (!ScGates.noCombat(watch.get().ticksSinceCombat(player))) {
			return Optional.of("in combat lately");
		}
		if (Home.open(level.getBlockState(house.door()))) {
			return Optional.of("the door stands open");
		}
		List<BlockPos> walk = Home.walk(house, random, fake ? 1 : cfg.stepsMin, fake ? 1 : cfg.stepsMax);
		if (walk.isEmpty()) {
			return Optional.of("no walk inside");
		}
		BlockPos container = fake ? null : Home.container(level, house, walk.getLast(), watch.get(), cfg.houseRadius).orElse(null);
		List<BlockPos> cells = new ArrayList<>(walk);
		if (container != null) {
			cells.add(container);
		}
		for (BlockPos cell : cells) {
			if (!outOfView.test(level, new AABB(cell))) {
				return Optional.of("a cell inside is in view");
			}
		}
		int closeAt = -1;
		if (container != null) {
			int lo = Math.max(1, Math.min(cfg.closeDelayMin, cfg.closeDelayMax));
			closeAt = (walk.size() - 1) * Math.max(1, cfg.stepTicks) + lo + random.nextInt(Math.max(cfg.closeDelayMin, cfg.closeDelayMax) - lo + 1);
		}
		Steps steps = new Steps(this, player.getUUID(), level, house.door(), walk, container, closeAt, fake ? (float) (cfg.stepVolume * 0.5) : (float) cfg.stepVolume,
				(float) cfg.closeVolume, Math.max(1, cfg.stepTicks), random.nextFloat() * 0.1F + 0.9F);
		running.add(steps);
		episodes.accept(steps);
		return Optional.empty();
	}

	/** The animals' approach: true once the episode started (the arm is spent). */
	boolean watchWaiting(ServerPlayer player, BlockPos base, Arm arm) {
		ServerLevel level = player.level();
		Sc05Config cfg = Sc05Config.get();
		double d = player.position().distanceTo(Vec3.atCenterOf(base));
		if (d > cfg.approachDistance) {
			arm.left = true;
			arm.waiting = "out of the band";
			return false;
		}
		if (!arm.left) {
			arm.waiting = "inside the band without having left it";
			return false;
		}
		Optional<String> why = startWaiting(player, base, arm.fake);
		arm.waiting = why.orElse("");
		if (why.isPresent()) {
			arm.left = false;
		}
		return why.isEmpty();
	}

	/** Freezes the animals facing the spot (the fake: the target) now; why not, or empty once started. */
	Optional<String> startWaiting(ServerPlayer player, BlockPos base, boolean fake) {
		ServerLevel level = player.level();
		Sc05Config cfg = Sc05Config.get();
		List<Animal> animals = animals(level, base, cfg);
		if (animals.size() < cfg.minMobs) {
			return Optional.of(animals.size() + " animals (at least " + cfg.minMobs + ")");
		}
		List<UUID> sharers = watch.get().sharingWith(player).stream().map(ServerPlayer::getUUID).toList();
		Vec3 spot = spot(base);
		int hold = AtmosphereConfig.ticks(cfg.holdSecondsMax);
		List<Animal> held = new ArrayList<>();
		for (Animal animal : animals) {
			Vec3 at = fake ? player.getEyePosition() : spot;
			if (Services.mobs().freeze(animal, hold + 20) && Services.mobs().face(animal, at, hold + 20)) {
				MobTamperImpl.INSTANCE.bind(animal, sharers);
				held.add(animal);
			} else {
				Services.mobs().release(animal);
			}
		}
		if (held.isEmpty()) {
			return Optional.of("no animal could be held");
		}
		Waiting waiting = new Waiting(this, sharers, level, spot, held, fake, hold, AtmosphereConfig.ticks(cfg.lookSeconds));
		running.add(waiting);
		episodes.accept(waiting);
		return Optional.empty();
	}

	/** Whether one of these episodes has ended (dropped from {@link #running}). */
	private static boolean over(Tasks.Episode episode) {
		return episode instanceof Steps steps && steps.done || episode instanceof Waiting waiting && waiting.done;
	}

	/** The steps inside, then the container's close. */
	static final class Steps implements Tasks.Episode {
		final ComingHome home;
		final UUID player;
		final ServerLevel level;
		final BlockPos door;
		final List<BlockPos> walk;
		final @Nullable BlockPos container;
		final int closeAt;
		final float volume;
		final float closeVolume;
		final int every;
		final float closePitch;
		final List<Vec3> played = new ArrayList<>();
		int age;
		boolean done;
		String ended = "";

		Steps(ComingHome home, UUID player, ServerLevel level, BlockPos door, List<BlockPos> walk, @Nullable BlockPos container, int closeAt, float volume,
				float closeVolume, int every, float closePitch) {
			this.home = home;
			this.player = player;
			this.level = level;
			this.door = door;
			this.walk = List.copyOf(walk);
			this.container = container;
			this.closeAt = closeAt;
			this.volume = volume;
			this.closeVolume = closeVolume;
			this.every = every;
			this.closePitch = closePitch;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			if (done) {
				return false;
			}
			String end = step(server);
			if (end != null) {
				ended = end;
				done = true;
				return false;
			}
			age++;
			return true;
		}

		private @Nullable String step(MinecraftServer server) {
			PlayerWatch w = home.watch.get();
			ServerPlayer target = AtmosphereCard.target(w, server, player, level).orElse(null);
			if (target == null) {
				return "gone";
			}
			if (!SoundCues.alone(target, ALONE_RADIUS, w, home.everyone.apply(level))) {
				return "not alone";
			}
			if (Home.open(level.getBlockState(door))) {
				return "the door opened";
			}
			int next = age / every;
			for (int i = age % every == 0 ? next : next + 1; i < walk.size(); i++) {
				if (!home.outOfView.test(level, new AABB(walk.get(i)))) {
					return "looked in";
				}
			}
			if (age % every == 0 && next < walk.size()) {
				BlockPos cell = walk.get(next);
				SoundType type = SoundCues.stepType(level, cell.below());
				Vec3 at = Vec3.atBottomCenterOf(cell);
				home.sounds.play(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(type.getStepSound()), SoundSource.PLAYERS, at, volume, type.getPitch());
				played.add(at);
			}
			if (container != null && age == closeAt) {
				if (!home.outOfView.test(level, new AABB(container))) {
					return "looked in";
				}
				ChestOpensCard.ContainerSounds sounds = ChestOpensCard.sounds(level.getBlockState(container));
				Vec3 at = Vec3.atCenterOf(container);
				home.sounds.play(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sounds.close()), SoundSource.BLOCKS, at, closeVolume, closePitch);
				played.add(at);
				return "done";
			}
			if (container == null && next >= walk.size() - 1 && age % every == 0) {
				return "done";
			}
			return null;
		}
	}

	/** The animals held facing the spot until the target comes, looks, or the time is up. */
	static final class Waiting implements Tasks.Episode {
		final ComingHome home;
		final List<UUID> sharers;
		final ServerLevel level;
		final Vec3 spot;
		final List<Animal> animals;
		final boolean fake;
		final int hold;
		final int lookNeeded;
		int age;
		int looked;
		boolean done;
		String ended = "";

		Waiting(ComingHome home, List<UUID> sharers, ServerLevel level, Vec3 spot, List<Animal> animals, boolean fake, int hold, int lookNeeded) {
			this.home = home;
			this.sharers = List.copyOf(sharers);
			this.level = level;
			this.spot = spot;
			this.animals = List.copyOf(animals);
			this.fake = fake;
			this.hold = hold;
			this.lookNeeded = Math.max(1, lookNeeded);
		}

		@Override
		public boolean tick(MinecraftServer server) {
			if (done) {
				return false;
			}
			age++;
			ServerPlayer target = AtmosphereCard.firstHere(home.watch.get(), server, sharers, level).orElse(null);
			String end = null;
			if (target == null) {
				// Nobody it was for is here: the tamper lets each go once nobody can see it (bound).
				done = true;
				ended = "gone";
				return false;
			}
			if (age > hold) {
				end = "time";
			} else if (target.position().distanceTo(spot) <= RELEASE_BLOCKS) {
				end = "came to the spot";
			} else {
				Vec3 look = target.getViewVector(1.0F);
				Vec3 to = spot.subtract(target.getEyePosition());
				double cos = to.lengthSqr() < 1.0E-6 ? 1.0 : look.dot(to.normalize());
				looked = cos >= Math.cos(Math.toRadians(LOOK_DEGREES)) ? looked + 1 : 0;
				if (looked >= lookNeeded) {
					end = "looked at the spot";
				}
			}
			if (end != null) {
				stop(server);
				ended = end;
				return false;
			}
			if (fake) {
				Vec3 eyes = target.getEyePosition();
				for (Animal animal : animals) {
					if (MobTamperImpl.INSTANCE.isFacing(animal)) {
						Services.mobs().face(animal, eyes, Math.max(1, hold - age + 20));
					}
				}
			}
			return true;
		}

		@Override
		public void stop(MinecraftServer server) {
			animals.forEach(Services.mobs()::release);
			done = true;
		}
	}

	/** That player stopped taking part: their timer and arm go. */
	public void onLeave(UUID player) {
		awaySince.remove(player);
		arms.remove(player);
	}

	/** The switch's revert and the server's stop: every arm, timer and survey goes, and the held animals are let go. */
	public void clear(@Nullable MinecraftServer server) {
		arms.clear();
		awaySince.clear();
		surveys.clear();
		for (Tasks.Episode episode : running) {
			if (episode instanceof Waiting waiting && !waiting.done) {
				waiting.stop(server);
			} else if (episode instanceof Steps steps) {
				steps.done = true;
			}
		}
		running.clear();
	}

	/** One line of state for the debug command. */
	public String describe(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Arm arm = arms.get(player.getUUID());
		String armLine = arm == null ? "not armed"
				: String.format(Locale.ROOT, "armed (%s%s), %.1f minutes left, %s", arm.kind == Kind.STEPS ? STEPS_ID : WAITING_ID, arm.fake ? ", fake" : "",
						Math.max(0, arm.until - playTicks.apply(server, player.getUUID())) / 1200.0, arm.waiting.isEmpty() ? "waiting" : arm.waiting);
		return String.format(Locale.ROOT, "%s; away %.1f minutes; %s: %s; %s: %s", armLine, awayTicks(server, player.getUUID()) / 1200.0, STEPS_ID,
				refusal(player, Kind.STEPS).orElse("fits"), WAITING_ID, refusal(player, Kind.WAITING).orElse("fits"));
	}
}
