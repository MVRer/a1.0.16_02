package com.forzacode.a1016_02.atmosphere.sc2.s29;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.atmosphere.card.EchoMiningCard;
import com.forzacode.a1016_02.atmosphere.card.SoundOut;
import com.forzacode.a1016_02.atmosphere.sc.ScGates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerAction;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * {@code sc_29} (docs/SELF_CREATED_2.md 29; AMBIENT, Traces, SOUND + PERSONAL, WATCHER, no fake): the target, alone (no
 * other player, spectators and the dead included, within {@value #ALONE}), underground or at night, hears another
 * player's own run of breaks again ({@code PlayerWatch.recentBursts}: their blocks, their hits, their gaps, through
 * echo mining's player, {@link EchoMiningCard#digTicks}) from inside solid rock {@code distanceMin} to
 * {@code distanceMax} from them, at a bearing at least {@code bearingOff} degrees from where that player really is. That
 * player is in the same level, {@code friendMin} to {@code friendMax} away, and the run is at most {@code burstMinutes}
 * old and all rock or earth dug with a pickaxe or shovel ({@link EchoMiningCard#mined}). Only sounds, from a rock block
 * buried on all six sides, out of everyone's view, never within 6 of the world's floor, and only where nobody but the
 * target is within hearing range of it (the sounds go to everyone in range, D-060; PERSONAL: the director never draws it
 * beside a sharer, and it checks being alone itself). The replay stops once the target is gone. A forced fire skips the
 * dark, combat and the run's age (the newest run then), never being alone, the distances, the bearing or the view.
 */
public final class S29Card implements EventCard {
	public static final String ID = "sc_29";
	/** Nobody else within this many blocks of the target (D-098's alone rule). */
	public static final double ALONE = 48.0;
	/** The spot is at most this far below the target's feet (so it comes from a side, with a bearing). */
	public static final int MAX_BELOW = 14;
	/** And at most this far above. */
	public static final int MAX_ABOVE = 2;
	/** Never this close to the world's floor (the bedrock layers). */
	public static final int FLOOR_MARGIN = 6;
	/** Tries for a spot. */
	static final int TRIES = 64;

	/** Who takes part and what they did. A game test swaps in its own. */
	public Supplier<PlayerWatch> watch = Services::watch;
	/** Where the sounds go (everyone in range). */
	public SoundOut sounds = SoundOut.EVERYONE_IN_RANGE;
	/** Where the replay runs. */
	public Consumer<Tasks.Episode> episodes = Tasks::start;
	/** Everyone in a level who could hear (spectators and the dead included). */
	public Function<ServerLevel, List<ServerPlayer>> everyone = ServerLevel::players;
	/** The out-of-view rule (live: {@code TraceService.isOutOfView}). */
	public BiPredicate<ServerLevel, AABB> outOfView = (level, box) -> Services.traces().isOutOfView(level, box);
	/** Ticks since the target's own combat. */
	public ToLongFunction<ServerPlayer> sinceCombat = player -> Services.watch().ticksSinceCombat(player);
	/** Underground or night for the target. */
	public Predicate<ServerPlayer> dark = S29Card::darkFor;
	/** The numbers. */
	public Supplier<Sc29Config> config = Sc29Config::get;

	/** Whose run, which one. */
	public record Pick(ServerPlayer friend, PlayerAction.Mining burst) {
	}

	/** One sound of the replay: {@code at} ticks after it starts. */
	public record Beat(int at, Holder<SoundEvent> sound, float volume, float pitch) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Tier tier() {
		return Tier.AMBIENT;
	}

	@Override
	public Stage earliestStage() {
		return Stage.TRACES;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of(Habit.WATCHER);
	}

	@Override
	public Set<CardTag> tags() {
		return Set.of(CardTag.SOUND, CardTag.PERSONAL);
	}

	@Override
	public boolean hasFake() {
		return false;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return refusal(player, false).isEmpty();
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer target = ctx.player();
		ServerLevel level = ctx.level();
		if (ctx.fake() || refusal(target, ctx.forced()).isPresent()) {
			return FireResult.SKIPPED;
		}
		List<Pick> picks = picks(target, ctx.forced());
		if (picks.isEmpty()) {
			return FireResult.SKIPPED;
		}
		Pick pick = picks.get(ctx.random().nextInt(picks.size()));
		BlockPos spot = spot(level, target, pick.friend(), ctx.random());
		if (spot == null) {
			return FireResult.NO_SPOT;
		}
		List<Beat> beats = beats(pick.burst().breaks(), AtmosphereConfig.get());
		episodes.accept(new Replay(watch.get(), target.getUUID(), level, Vec3.atCenterOf(spot), beats, sounds));
		A1016_02.LOGGER.info("[a1016] atmosphere: {} ({})", ID, beats.size());
		A1016_02.LOGGER.debug("[a1016] atmosphere: {} at {}", ID, spot.toShortString());
		return FireResult.FIRED;
	}

	// --- the moment ---

	/** Underground (the dig's rule) or night (13000 to 23000 of the day) at the target. */
	static boolean darkFor(ServerPlayer player) {
		ServerLevel level = player.level();
		return WorldScan.underground(level, player.blockPosition()) || night(level.getServer().overworld().getDefaultClockTime());
	}

	/** Night: 13000 to 23000 of the day. */
	public static boolean night(long timeOfDay) {
		long t = Math.floorMod(timeOfDay, 24000L);
		return t >= 13000L && t < 23000L;
	}

	/**
	 * Why it does not fit for the target now, or empty: the switch and the story, the overworld, combat (unless forced),
	 * alone, the dark (unless forced), and another player with a run to give.
	 */
	public Optional<String> refusal(ServerPlayer target, boolean forced) {
		ServerLevel level = target.level();
		MinecraftServer server = level.getServer();
		Optional<String> story = ScGates.why(server);
		if (story.isPresent()) {
			return story;
		}
		if (!ScGates.overworld(level)) {
			return Optional.of("not in the overworld");
		}
		if (!forced && !ScGates.noCombat(sinceCombat.applyAsLong(target))) {
			return Optional.of("in combat lately");
		}
		if (!SoundCues.alone(target, ALONE, watch.get(), everyone.apply(level))) {
			return Optional.of("not alone (someone within " + (int) ALONE + ")");
		}
		if (!forced && !dark.test(target)) {
			return Optional.of("neither underground nor night");
		}
		if (picks(target, forced).isEmpty()) {
			Sc29Config cfg = config.get();
			return Optional.of(String.format(java.util.Locale.ROOT, "nobody %.0f to %.0f away in this level with a run of rock %s", cfg.friendMin,
					cfg.friendMax, forced ? "at all" : "in the last " + cfg.burstMinutes + " minutes"));
		}
		return Optional.empty();
	}

	/**
	 * Each other participant in the target's level {@code friendMin} to {@code friendMax} away with a run to give: their
	 * newest burst in this level whose every block is mined rock or earth, at most {@code burstMinutes} old (forced: any
	 * age).
	 */
	public List<Pick> picks(ServerPlayer target, boolean forced) {
		ServerLevel level = target.level();
		Sc29Config cfg = config.get();
		PlayerWatch w = watch.get();
		long now = level.getServer().getTickCount();
		long maxAge = ModConfig.realTicks(cfg.burstMinutes * 60.0);
		List<Pick> picks = new ArrayList<>();
		for (ServerPlayer friend : w.participants(level.getServer())) {
			if (friend.getUUID().equals(target.getUUID()) || friend.level() != level || !friend.isAlive()) {
				continue;
			}
			double d = friend.distanceTo(target);
			if (d < cfg.friendMin || d > cfg.friendMax) {
				continue;
			}
			for (PlayerAction.Mining burst : w.recentBursts(friend)) {
				if (!burst.dimension().equals(level.dimension()) || !burst.breaks().stream().allMatch(b -> EchoMiningCard.mined(b.state()))) {
					continue;
				}
				long age = now - burst.tick();
				if (forced || (age >= 0 && age <= maxAge)) {
					picks.add(new Pick(friend, burst));
					break; // the newest of theirs
				}
			}
		}
		return picks;
	}

	// --- where ---

	/** The bearing from {@code from} to {@code to} on the ground, in degrees (0 along +X, counter-clockwise toward +Z). */
	public static double bearing(Vec3 from, Vec3 to) {
		return Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x));
	}

	/** The smallest angle between two bearings, 0 to 180 degrees. */
	public static double apart(double a, double b) {
		double d = Math.abs(Mth.wrapDegrees(a - b));
		return Math.min(d, 360.0 - d);
	}

	/**
	 * Where it plays from: a rock block ({@link WorldScan#isRock}) buried on all six sides, never placed by a player,
	 * loaded, {@code distanceMin} to {@code distanceMax} from the target's feet (3D), from {@link #MAX_BELOW} under them to
	 * {@link #MAX_ABOVE} over, never within {@link #FLOOR_MARGIN} of the level's floor nor at its top, at least
	 * {@code bearingOff} degrees from the other player's bearing, out of everyone's view, and heard by nobody but the
	 * target at the replay's loudest. Null if {@value #TRIES} tries find none.
	 */
	public @Nullable BlockPos spot(ServerLevel level, ServerPlayer target, ServerPlayer friend, RandomSource random) {
		Sc29Config cfg = config.get();
		AtmosphereConfig sound = AtmosphereConfig.get();
		float loudest = (float) Math.max(sound.echoMiningBreakVolume, sound.echoMiningHitVolume);
		double min = Math.max(1, cfg.distanceMin);
		double max = Math.max(min, cfg.distanceMax);
		double off = Mth.clamp(cfg.bearingOff, 0.0, 179.0);
		Vec3 feet = target.position();
		double toFriend = bearing(feet, friend.position());
		BlockPos origin = target.blockPosition();
		int floor = level.getMinY() + FLOOR_MARGIN;
		int top = level.getMaxY() - 1;
		List<ServerPlayer> listeners = everyone.apply(level);
		for (int attempt = 0; attempt < TRIES; attempt++) {
			int dy = random.nextInt(MAX_BELOW + MAX_ABOVE + 1) - MAX_BELOW;
			double dist = min + random.nextDouble() * (max - min);
			if (Math.abs(dy) >= dist) {
				continue;
			}
			double across = Math.sqrt(dist * dist - (double) dy * dy);
			double angle = Math.toRadians(toFriend + off + random.nextDouble() * (360.0 - 2.0 * off));
			BlockPos pos = origin.offset((int) Math.round(Math.cos(angle) * across), dy, (int) Math.round(Math.sin(angle) * across));
			if (!fits(level, pos, origin, feet, toFriend, min, max, off, floor, top)) {
				continue;
			}
			if (!outOfView.test(level, new AABB(pos))) {
				continue;
			}
			List<ServerPlayer> hear = SoundCues.hearers(level, listeners, Vec3.atCenterOf(pos), loudest);
			if (hear.stream().anyMatch(p -> !p.getUUID().equals(target.getUUID()))) {
				continue;
			}
			return pos.immutable();
		}
		return null;
	}

	/** The spot's own rules (no view, no hearing): band, height, floor and top, bearing, rock buried and never placed. */
	static boolean fits(ServerLevel level, BlockPos pos, BlockPos origin, Vec3 feet, double toFriend, double min, double max, double off, int floor,
			int top) {
		double d2 = pos.distSqr(origin);
		int dy = pos.getY() - origin.getY();
		if (d2 < min * min || d2 > max * max || dy < -MAX_BELOW || dy > MAX_ABOVE || pos.getY() < floor || pos.getY() >= top) {
			return false;
		}
		if (apart(bearing(feet, Vec3.atCenterOf(pos)), toFriend) < off) {
			return false;
		}
		if (!level.isLoaded(pos) || !WorldScan.isRock(level.getBlockState(pos)) || !WorldScan.buried(level, pos)) {
			return false;
		}
		return !Services.watch().wasPlacedByPlayer(level, pos);
	}

	// --- what ---

	/**
	 * The replay of {@code breaks} (oldest first), echo mining's player: each block's hits on vanilla's 4-tick beat over its
	 * dig time ({@link EchoMiningCard#digTicks}), then its break, at the recorded ticks; tick 0 is the first sound; each
	 * block's own sounds (the hit at half its pitch, the break at 0.8, as vanilla plays them) at echo mining's volumes.
	 */
	public static List<Beat> beats(List<PlayerAction.Break> breaks, AtmosphereConfig cfg) {
		int[] dig = EchoMiningCard.digTicks(breaks, cfg);
		long origin = breaks.getFirst().tick() - dig[0];
		float hitVolume = (float) cfg.echoMiningHitVolume;
		float breakVolume = (float) cfg.echoMiningBreakVolume;
		List<Beat> beats = new ArrayList<>();
		for (int i = 0; i < breaks.size(); i++) {
			PlayerAction.Break each = breaks.get(i);
			SoundType type = each.state().getSoundType();
			long start = each.tick() - dig[i] - origin;
			for (int k = 0; k < EchoMiningCard.hits(dig[i]); k++) {
				beats.add(new Beat((int) (start + (long) k * EchoMiningCard.HIT_EVERY), holder(type.getHitSound()), hitVolume, type.getPitch() * 0.5F));
			}
			beats.add(new Beat((int) (each.tick() - origin), holder(type.getBreakSound()), breakVolume, type.getPitch() * 0.8F));
		}
		beats.sort(Comparator.comparingInt(Beat::at));
		return beats;
	}

	private static Holder<SoundEvent> holder(SoundEvent sound) {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
	}

	/**
	 * Plays the beats at one place on their own ticks, {@code SoundSource.BLOCKS}, for everyone in range. The target is
	 * looked up again every tick (D-062.1): once they are gone (left, dead, a spectator, another level) the rest is dropped.
	 */
	public static final class Replay implements Tasks.Episode {
		private final PlayerWatch watch;
		private final UUID target;
		private final ServerLevel level;
		private final Vec3 pos;
		private final List<Beat> beats;
		private final SoundOut sounds;
		private int age;
		private int next;

		public Replay(PlayerWatch watch, UUID target, ServerLevel level, Vec3 pos, List<Beat> beats, SoundOut sounds) {
			this.watch = watch;
			this.target = target;
			this.level = level;
			this.pos = pos;
			this.beats = List.copyOf(beats);
			this.sounds = sounds;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			if (!SelfCreated.enabled()) {
				return false; // the switch ends the rest at once (R12-33)
			}
			Optional<ServerPlayer> here = watch.participant(server, target).filter(p -> watch.isTargetable(p) && p.level() == level);
			if (here.isEmpty()) {
				return false;
			}
			while (next < beats.size() && beats.get(next).at() <= age) {
				Beat beat = beats.get(next++);
				sounds.play(level, beat.sound(), SoundSource.BLOCKS, pos, beat.volume(), beat.pitch());
			}
			age++;
			return next < beats.size();
		}

		public Vec3 pos() {
			return pos;
		}

		public List<Beat> beats() {
			return beats;
		}

		public UUID target() {
			return target;
		}
	}
}
