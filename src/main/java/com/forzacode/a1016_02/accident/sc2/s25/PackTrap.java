package com.forzacode.a1016_02.accident.sc2.s25;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.AccidentData;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.DeathCauses;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.accident.TrapKind;
import com.forzacode.a1016_02.accident.sc2.HuntData;
import com.forzacode.a1016_02.accident.sc2.HuntScene;
import com.forzacode.a1016_02.accident.sc2.Hunts;
import com.forzacode.a1016_02.accident.sc2.Prey;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Feature 25 as an accident trap kind, {@code sc25} (docs/SELF_CREATED_2.md, 25; D-096, D-097): a live trap. Its card
 * is the planner's own ({@code accident_sc25}: MAJOR, Proximity, never fake), so the accident pacing holds (the first
 * accident's time, the major gaps, one trap per player and per session of theirs).
 * <ul>
 * <li><b>When</b> ({@link #refusal}): the shared gate ({@link Hunts#gate}: the layer, the story, the overworld, no combat,
 * health {@code b} (never under 14), a way out, outdoors, on foot, {@code a} from their base (never under 64), alone,
 * no other scene within 128); dark (night, or a fog that counts); at most once in {@code j} in-game days for them and
 * {@code k} in the world; and tonight's roll ({@code m}, {@code l} times as likely for a player inside one of his places
 * within the last 2 in-game days). Then at least {@code c} wild adult wolves loaded within {@code d}, out of everyone's
 * view.</li>
 * <li><b>Setting it</b> ({@link #setup}): 3 of them, one more at armor 10 and one more at 15 (at most {@code e}), moved
 * out of view to 40 to 56 behind the player (the first 40 to 44), then {@link Pack} runs it.</li>
 * <li><b>Its deaths</b>: a death by one of its wolves while it is set ({@link Pack} sets it when they turn), anywhere,
 * whoever it is; the list's word is {@link DeathCauses#WOLVES}. Also a death no entity dealt within
 * {@link Hunts#CREDIT_TICKS} of a bite (R12-6: knocked off a ridge, into lava or water), with the damage's own word.</li>
 * </ul>
 * A forced debug arm ({@code /a1016 sc 25 arm force}) relaxes the time, the base and the pacing, never the way out, the
 * health floor, the alone rule or the tells.
 */
public final class PackTrap implements TrapKind {
	public static final String ID = Pack.KIND;
	/** The one clue, in the accident history (D-080: plain words). */
	public static final String CLUE = "Nobody hit one.";
	/** A player counts as having been inside one of his places this recently (game ticks: 2 in-game days). */
	public static final long TRESPASS_TICKS = 48_000L;

	/** What the trap reads: the live game, or a test's own (by player, {@link #use}). */
	public record Env(Sc25Config cfg, HuntData data, Function<ServerLevel, List<ServerPlayer>> listeners, BooleanSupplier dark, LongSupplier day,
			LongSupplier gameTime, BooleanSupplier enabled, Supplier<HerobrineState> state, ToLongFunction<UUID> trespassedAt, long seed, boolean forced) {
		public static Env live(MinecraftServer server, boolean forced) {
			ServerLevel overworld = server.overworld();
			return new Env(Sc25Config.get(), HuntData.get(server), Hunts::listeners, () -> Hunts.dark(overworld), () -> Hunts.day(server),
					overworld::getGameTime, SelfCreated::enabled, () -> HerobrineState.get(server), Services.remains()::trespassedAt,
					overworld.getSeed(), forced);
		}

		public Env forced(boolean on) {
			return new Env(cfg, data, listeners, dark, day, gameTime, enabled, state, trespassedAt, seed, on);
		}
	}

	private static final Map<UUID, Env> OVERRIDES = new HashMap<>();
	/** The wolves {@link #setup} moved, handed to {@link #onArmed} in the same tick. */
	private final Map<UUID, List<Wolf>> gathered = new HashMap<>();

	/** This player's trap reads {@code env} instead of the live game until {@code use(player, null)} (tests, forced arms). */
	public static synchronized void use(UUID player, @Nullable Env env) {
		if (env == null) {
			OVERRIDES.remove(player);
		} else {
			OVERRIDES.put(player, env);
		}
	}

	/** What this player's trap reads now. */
	public static synchronized Env env(ServerPlayer player) {
		Env env = OVERRIDES.get(player.getUUID());
		return env != null ? env : Env.live(player.level().getServer(), false);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Set<Habit> habits() {
		return Set.of();
	}

	@Override
	public boolean live() {
		return true;
	}

	@Override
	public String cause() {
		return DeathCauses.WOLVES;
	}

	@Override
	public @Nullable String blocked() {
		return Services.mobs() instanceof MobTamper.Stub ? "MobTamper is still the core stub" : null;
	}

	/** Cheap: the layer, the overworld, dark, the pacing and tonight's roll (all relaxed but the layer when forced). */
	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		Env env = env(player);
		if (!env.enabled().getAsBoolean() || !level.dimension().equals(Level.OVERWORLD)) {
			return false;
		}
		return env.forced() || env.dark().getAsBoolean() && paced(env, player.getUUID()) == null && tonight(env, player.getUUID());
	}

	/** Why it may not start for this player now (before the wolves are looked for), or null. */
	public static @Nullable String refusal(ServerPlayer player, TrapContext ctx, Env env) {
		String why = Hunts.gate(player, new Hunts.Gate(ctx.watch(), env.listeners(), env.state().get(), env.enabled().getAsBoolean(), env.cfg().minHealth,
				env.cfg().fromBase, env.forced()));
		if (why != null) {
			return why;
		}
		if (!env.forced()) {
			if (!env.dark().getAsBoolean()) {
				return "not dark (night or a fog)";
			}
			String paced = paced(env, player.getUUID());
			if (paced != null) {
				return paced;
			}
			if (!tonight(env, player.getUUID())) {
				return "not tonight (the day's roll)";
			}
		}
		return null;
	}

	/** Why the pacing holds it back for this player, or null: the days apart, the world's count. */
	static @Nullable String paced(Env env, UUID player) {
		long last = env.data().lastDay(ID, player);
		long today = env.day().getAsLong();
		if (last >= 0 && today - last < env.cfg().daysApart) {
			return "had one on day " + last + " (" + env.cfg().daysApart + " days apart)";
		}
		if (env.data().count(ID) >= env.cfg().maxPerWorld) {
			return "the world had its " + env.cfg().maxPerWorld;
		}
		return null;
	}

	/** Today's roll for this player: {@code m}, times {@code l} if they were inside one of his places lately. */
	public static boolean tonight(Env env, UUID player) {
		return roll(env, player) < chance(env, player);
	}

	/** The chance today's roll must beat. */
	public static double chance(Env env, UUID player) {
		long at = env.trespassedAt().applyAsLong(player);
		boolean trespassed = at >= 0 && env.gameTime().getAsLong() - at <= TRESPASS_TICKS;
		return Math.min(1.0, Math.max(0.0, env.cfg().nightChance) * (trespassed ? Math.max(1.0, env.cfg().trespassFactor) : 1.0));
	}

	/** Today's roll for this player, 0 to 1: the same all day, a new one each in-game day. */
	public static double roll(Env env, UUID player) {
		long h = env.seed() * 0x9E3779B97F4A7C15L ^ player.getMostSignificantBits() * 31 ^ player.getLeastSignificantBits() ^ env.day().getAsLong() * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** How many: 3, one more at armor 10, one more at armor 15, at most {@code e} (never under {@code c}). */
	public static int size(ServerPlayer player, Sc25Config cfg) {
		int armor = player.getArmorValue();
		int n = 3 + (armor >= 10 ? 1 : 0) + (armor >= 15 ? 1 : 0);
		return Math.max(cfg.minWolves, Math.min(n, cfg.packMax));
	}

	/** A wolf it may take: wild, adult, calm, nobody's, free, loaded, alive. */
	public static boolean wild(Wolf wolf) {
		return wolf.isAlive() && !wolf.isRemoved() && !wolf.isBaby() && !wolf.isTame() && wolf.getOwnerReference() == null && !wolf.isAngry()
				&& !wolf.isLeashed() && !wolf.isPassenger() && !wolf.isVehicle() && !wolf.hasCustomName() && !wolf.isNoAi() && !Services.mobs().isTampered(wolf);
	}

	/** The wild wolves within {@code d} of the player and out of everyone's view, nearest first. */
	public static List<Wolf> wolves(ServerPlayer player, TrapContext ctx, Sc25Config cfg) {
		ServerLevel level = ctx.level();
		double radius = cfg.gatherRadius;
		List<Wolf> found = new ArrayList<>(level.getEntitiesOfClass(Wolf.class, new AABB(player.blockPosition()).inflate(radius),
				wolf -> wild(wolf) && wolf.distanceTo(player) <= radius && ctx.view().outOfView(level, wolf.getBoundingBox())));
		found.sort(Comparator.comparingDouble(wolf -> wolf.distanceTo(player)));
		return found;
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null || player.level() != ctx.level()) {
			return List.of();
		}
		Env env = env(player);
		if (refusal(player, ctx, env) != null || wolves(player, ctx, env.cfg()).size() < Math.max(3, env.cfg().minWolves)) {
			return List.of();
		}
		return List.of(Candidate.around(player.blockPosition(), List.of(), 16, 4, CLUE));
	}

	/**
	 * Moves the wolves out of view to 40 to 56 behind the player (the first within 44, so its long tell is heard), each
	 * at least 3 from the others. False, and nothing armed, unless at least {@code c} of them got there.
	 */
	@Override
	public boolean setup(TrapContext ctx, Candidate candidate) {
		ServerPlayer player = ctx.player();
		if (player == null || player.level() != ctx.level()) {
			return false;
		}
		Env env = env(player);
		Sc25Config cfg = env.cfg();
		int least = Math.max(3, cfg.minWolves);
		if (refusal(player, ctx, env) != null) {
			return false;
		}
		List<Wolf> wolves = wolves(player, ctx, cfg);
		if (wolves.size() < least) {
			return false;
		}
		int n = Math.min(size(player, cfg), wolves.size());
		ServerLevel level = ctx.level();
		double behind = Hunts.angle(player.getViewVector(1.0F)) + Math.PI;
		List<Wolf> placed = new ArrayList<>();
		List<BlockPos> spots = new ArrayList<>();
		MobTamper tamper = Services.mobs();
		for (Wolf wolf : wolves) {
			if (placed.size() >= n) {
				break;
			}
			for (int attempt = 0; attempt < 12; attempt++) {
				double angle = behind + Math.toRadians(level.getRandom().nextInt(61) - 30);
				double distance = placed.isEmpty() ? 40.0 + level.getRandom().nextDouble() * 4.0 : 40.0 + level.getRandom().nextDouble() * 16.0;
				Vec3 at = Hunts.out(player.position(), angle, distance);
				BlockPos spot = Hunts.stand(level, at.x, at.z, player.getBlockY(), 8, 12, 1);
				if (spot == null || spots.stream().anyMatch(s -> s.distSqr(spot) < 9.0)) {
					continue;
				}
				AABB box = wolf.getDimensions(wolf.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(spot));
				if (!ctx.view().outOfView(level, box) || !ctx.view().outOfView(level, wolf.getBoundingBox())) {
					continue;
				}
				if (tamper.moveOutOfView(wolf, spot)) {
					placed.add(wolf);
					spots.add(spot);
					break;
				}
			}
		}
		if (placed.size() < least) {
			return false;
		}
		gathered.put(player.getUUID(), placed);
		return true;
	}

	/** Blames its wolves, saves the day and the count, and starts its scene with the first long tell. */
	@Override
	public ArmedTrap onArmed(TrapContext ctx, Candidate candidate, ArmedTrap armed) {
		ServerPlayer player = ctx.player();
		List<Wolf> wolves = player == null ? null : gathered.remove(player.getUUID());
		if (player == null || wolves == null) {
			return armed;
		}
		ArmedTrap blamed = armed;
		for (Wolf wolf : wolves) {
			blamed = blamed.blame(wolf.getUUID());
		}
		Env env = env(player);
		env.data().record(ID, player.getUUID(), env.day().getAsLong());
		// A forced arm relaxes the time for the scene too: it never ends for the dawn.
		BooleanSupplier dark = env.forced() ? () -> true : env.dark();
		Pack pack = new Pack(player.getUUID(), ctx.level(), HuntScene.Links.of(ctx), armed.armedAt(), Prey.of(player), wolves, env.cfg(), dark, null,
				Pack.Phase.STALK);
		pack.relaxBase(env.forced());
		pack.calledFrom(Hunts.calledFrom(player)); // R10-4: a trespass's call: the first tell from that place's way
		Hunts.start(pack);
		pack.begin();
		return blamed;
	}

	/** Its scene runs it; with none (a restart) a trap not yet set runs out. */
	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		UUID owner = ctx.target();
		boolean running = owner != null && Hunts.of(owner).filter(s -> s.kind().equals(ID) && !s.done()).isPresent();
		if (!running && !armed.isSet()) {
			return armed.withPhase(armed.phase(), Math.min(armed.until(), ctx.now() - 1));
		}
		return armed;
	}

	/** A bite of one of its wolves. */
	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		return source.getEntity() instanceof Wolf wolf && armed.blames(wolf.getUUID());
	}

	/**
	 * A bite of one of its wolves, or (R12-6) a death no entity dealt right after one bit them: knocked off a ridge, into
	 * lava or deep water by the pack. Anyone's, so a friend who ran in is marked too (R13-13).
	 */
	@Override
	public boolean claims(ServerPlayer victim, DamageSource source, ArmedTrap armed, AccidentData data, long now) {
		return matches(source, armed) || Hunts.credited(victim, source, armed);
	}

	/** Wherever they catch you. */
	@Override
	public boolean anywhere() {
		return true;
	}

	/** The killer's word for a bite; for a death they only drove the player into, the damage's own ("fell"). */
	@Override
	public @Nullable String word(DamageSource source, ArmedTrap armed) {
		return source.getEntity() == null ? null : DeathCauses.byKiller(source, DeathCauses.WOLVES);
	}
}
