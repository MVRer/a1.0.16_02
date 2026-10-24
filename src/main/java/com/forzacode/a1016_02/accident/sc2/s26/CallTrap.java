package com.forzacode.a1016_02.accident.sc2.s26;

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
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/**
 * Feature 26 as an accident trap kind, {@code sc26} (docs/SELF_CREATED_2.md, 26; D-096, D-097): a live trap, its card
 * the planner's own ({@code accident_sc26}), so the accident pacing holds.
 * <ul>
 * <li><b>When</b> ({@link #refusal}): the shared gate ({@link Hunts#gate}: the layer, the story, the overworld, no
 * combat, health 14, a way out, outdoors, on foot, 64 from their base, alone, no other scene within 128); a night with a
 * fog spell that counts; at most once in {@code g} in-game days for them and {@code h} in the world. Then at least
 * {@code b} zombies, husks, skeletons, strays or spiders ({@link #KINDS}: never a creeper or a witch) within {@code a},
 * adult, free, unnamed.</li>
 * <li><b>Setting it</b>: the nearest {@code c} of them; {@link Call} runs it (nobody is moved out of view: they walk).</li>
 * <li><b>Its deaths</b>: a death by one of them (an arrow counts as its shooter's) while it is set, anywhere; the list's
 * word by the killer's kind ({@link DeathCauses#byKiller}: shot, bitten, else killed).</li>
 * </ul>
 * A forced debug arm ({@code /a1016 sc 26 arm force}) relaxes the night, the fog, the base and the pacing, never the way
 * out, the health floor, the alone rule or the tells.
 */
public final class CallTrap implements TrapKind {
	public static final String ID = Call.KIND;
	/** The one clue, in the accident history (D-080: plain words). */
	public static final String CLUE = "They all knew where to go.";
	/** The kinds it takes (never a creeper or a witch). */
	public static final Set<EntityType<?>> KINDS = Set.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.SPIDER);
	/** A trespass in one of his places calls a hunt for this long (game ticks: one in-game day; the director's call, R10-4). */
	public static final long CALL_TICKS = 24_000L;

	/** What the trap reads: the live game, or a test's own (by player, {@link #use}). */
	public record Env(Sc26Config cfg, HuntData data, Function<ServerLevel, List<ServerPlayer>> listeners, BooleanSupplier night, BooleanSupplier fog,
			LongSupplier day, BooleanSupplier enabled, Supplier<HerobrineState> state, boolean forced) {
		public static Env live(MinecraftServer server, boolean forced) {
			ServerLevel overworld = server.overworld();
			return new Env(Sc26Config.get(), HuntData.get(server), Hunts::listeners, () -> Hunts.night(overworld), () -> Hunts.fog(overworld),
					() -> Hunts.day(server), SelfCreated::enabled, () -> HerobrineState.get(server), forced);
		}

		public Env forced(boolean on) {
			return new Env(cfg, data, listeners, night, fog, day, enabled, state, on);
		}

		/** A night with a fog spell that counts. */
		public boolean fogNight() {
			return night.getAsBoolean() && fog.getAsBoolean();
		}
	}

	private static final Map<UUID, Env> OVERRIDES = new HashMap<>();
	private final Map<UUID, List<Mob>> chosen = new HashMap<>();

	/** This player's trap reads {@code env} instead of the live game until {@code use(player, null)} (tests, forced arms). */
	public static synchronized void use(UUID player, @Nullable Env env) {
		if (env == null) {
			OVERRIDES.remove(player);
		} else {
			OVERRIDES.put(player, env);
		}
	}

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
		return "killed";
	}

	@Override
	public @Nullable String blocked() {
		return Services.mobs() instanceof MobTamper.Stub ? "MobTamper is still the core stub" : null;
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		Env env = env(player);
		if (!env.enabled().getAsBoolean() || !level.dimension().equals(Level.OVERWORLD)) {
			return false;
		}
		return env.forced() || dark(player, env) && paced(env, player.getUUID()) == null;
	}

	/**
	 * Its night (FIX-SC2-PACE, R10-4): a fog night, or any night within one in-game day of the player's trespass in one of
	 * his places (that night needs no fog spell).
	 */
	public static boolean dark(ServerPlayer player, Env env) {
		if (env.fogNight()) {
			return true;
		}
		if (!env.night().getAsBoolean()) {
			return false;
		}
		return calls(Services.remains().trespassedAt(player.getUUID()), player.level().getServer().overworld().getGameTime());
	}

	/** True if a trespass at {@code trespassedAt} (game ticks, -1: never) calls a hunt at {@code gameTime}: within one in-game day. */
	public static boolean calls(long trespassedAt, long gameTime) {
		return trespassedAt >= 0 && gameTime >= trespassedAt && gameTime - trespassedAt <= CALL_TICKS;
	}

	/** Why it may not start for this player now (before the monsters are looked for), or null. */
	public static @Nullable String refusal(ServerPlayer player, TrapContext ctx, Env env) {
		String why = Hunts.gate(player, new Hunts.Gate(ctx.watch(), env.listeners(), env.state().get(), env.enabled().getAsBoolean(), Hunts.MIN_HEALTH,
				Hunts.FROM_BASE, env.forced()));
		if (why != null) {
			return why;
		}
		if (!Hunts.digOut(player)) {
			return "carries no pickaxe or shovel (its way out: dig in and cover)";
		}
		if (!env.forced()) {
			if (!dark(player, env)) {
				return "not a fog night";
			}
			return paced(env, player.getUUID());
		}
		return null;
	}

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

	/** A monster it may take: of {@link #KINDS}, adult, alive, free (no rider, no mount, no leash), unnamed, with AI, untouched. */
	public static boolean callable(Entity entity) {
		return entity instanceof Mob mob && KINDS.contains(mob.getType()) && mob.isAlive() && !mob.isRemoved() && !mob.isBaby() && !mob.isPassenger()
				&& !mob.isVehicle() && !mob.isLeashed() && !mob.hasCustomName() && !mob.isNoAi() && !Services.mobs().isTampered(mob);
	}

	/** The ones it may take within {@code a} of the player, nearest first. */
	public static List<Mob> callees(ServerPlayer player, TrapContext ctx, Sc26Config cfg) {
		double radius = cfg.radius;
		List<Mob> found = new ArrayList<>();
		for (Entity entity : ctx.level().getEntities((Entity) null, new AABB(player.blockPosition()).inflate(radius),
				e -> callable(e) && e.distanceTo(player) <= radius)) {
			found.add((Mob) entity);
		}
		found.sort(Comparator.comparingDouble(mob -> mob.distanceTo(player)));
		return found;
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null || player.level() != ctx.level()) {
			return List.of();
		}
		Env env = env(player);
		if (refusal(player, ctx, env) != null || callees(player, ctx, env.cfg()).size() < Math.max(1, env.cfg().minMobs)) {
			return List.of();
		}
		return List.of(Candidate.around(player.blockPosition(), List.of(), 16, 4, CLUE));
	}

	/** How many it takes at most: {@code c}, never over {@link Call#HARD_MAX} (R10-19: never a horde), at least 1. */
	public static int most(Sc26Config cfg) {
		return Math.max(1, Math.min(Call.HARD_MAX, cfg.maxMobs));
	}

	/** Chooses the nearest {@code c} (at most {@link Call#HARD_MAX}; nothing is moved). */
	@Override
	public boolean setup(TrapContext ctx, Candidate candidate) {
		ServerPlayer player = ctx.player();
		if (player == null || player.level() != ctx.level()) {
			return false;
		}
		Env env = env(player);
		if (refusal(player, ctx, env) != null) {
			return false;
		}
		List<Mob> found = callees(player, ctx, env.cfg());
		if (found.size() < Math.max(1, env.cfg().minMobs)) {
			return false;
		}
		chosen.put(player.getUUID(), new ArrayList<>(found.subList(0, Math.min(found.size(), most(env.cfg())))));
		return true;
	}

	@Override
	public ArmedTrap onArmed(TrapContext ctx, Candidate candidate, ArmedTrap armed) {
		ServerPlayer player = ctx.player();
		List<Mob> callees = player == null ? null : chosen.remove(player.getUUID());
		if (player == null || callees == null) {
			return armed;
		}
		ArmedTrap blamed = armed;
		for (Mob mob : callees) {
			blamed = blamed.blame(mob.getUUID());
		}
		Env env = env(player);
		env.data().record(ID, player.getUUID(), env.day().getAsLong());
		// It ends at dawn (a forced arm, never for the time).
		BooleanSupplier dark = env.forced() ? () -> true : env.night();
		Call call = new Call(player.getUUID(), ctx.level(), HuntScene.Links.of(ctx), armed.armedAt(), Prey.of(player), callees, env.cfg(), dark, null);
		call.relaxBase(env.forced());
		call.calledFrom(Hunts.calledFrom(player)); // R10-4: a trespass's call: the first voices from that place's way
		Hunts.start(call);
		call.begin();
		return blamed;
	}

	/**
	 * Its scene runs it; with none (a restart) a trap not yet set runs out. Once set, while one of its own that was let go to
	 * vanilla's targeting is still loaded, alive and after a player as its window is about to close, the window goes on
	 * ({@link #KEEP_STEP} at a time, at most {@link #KEEP_MAX} after it was set), so a death by one of them is never
	 * unmarked for being late (R12-36).
	 */
	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		UUID owner = ctx.target();
		boolean running = owner != null && Hunts.of(owner).filter(s -> s.kind().equals(ID) && !s.done()).isPresent();
		if (!running && !armed.isSet()) {
			return armed.withPhase(armed.phase(), Math.min(armed.until(), ctx.now() - 1));
		}
		if (armed.isSet() && armed.until() - ctx.now() < KEEP_STEP && ctx.now() - armed.setAt() < KEEP_MAX && stillOn(ctx.level(), armed)) {
			return armed.withPhase(armed.phase(), ctx.now() + KEEP_STEP);
		}
		return armed;
	}

	/** R12-36: the window goes on this long at a time (ticks of play) while one of its own is still after a player. */
	public static final long KEEP_STEP = 200;
	/** ... and at most this long after it was set (ticks of play: 10 minutes). */
	public static final long KEEP_MAX = 12_000;

	/** One of its own is loaded in that level, alive, and after a player. */
	static boolean stillOn(ServerLevel level, ArmedTrap armed) {
		for (Mob mob : Hunts.loaded(level, armed.mobs(), Mob.class)) {
			if (mob.getTarget() instanceof net.minecraft.world.entity.player.Player) {
				return true;
			}
		}
		return false;
	}

	/** A hit (or an arrow) of one of them. */
	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		Entity killer = source.getEntity();
		return killer != null && armed.blames(killer.getUUID());
	}

	/**
	 * A hit (or an arrow) of one of them, or (R12-6) a death no entity dealt right after one of them hurt the victim (a fall
	 * off a ledge it drove them over). Anyone's, so a friend who ran in is marked too (R13-13).
	 */
	@Override
	public boolean claims(ServerPlayer victim, DamageSource source, ArmedTrap armed, AccidentData data, long now) {
		return matches(source, armed) || Hunts.credited(victim, source, armed);
	}

	@Override
	public boolean anywhere() {
		return true;
	}

	/** The killer's word for one of theirs; for a death they only drove the player into, the damage's own ("fell"). */
	@Override
	public @Nullable String word(DamageSource source, ArmedTrap armed) {
		return source.getEntity() == null ? null : DeathCauses.byKiller(source, cause());
	}
}
