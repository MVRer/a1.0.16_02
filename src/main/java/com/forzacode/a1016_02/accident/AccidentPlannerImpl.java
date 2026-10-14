package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.trap.DarkCornerTrap;
import com.forzacode.a1016_02.accident.trap.GroveLureTrap;
import com.forzacode.a1016_02.accident.trap.HouseFireTrap;
import com.forzacode.a1016_02.core.AccidentPlanner;
import com.forzacode.a1016_02.core.DeathMarker;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.TrapType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/**
 * The real {@link AccidentPlanner}, for one player or a group (D-058 to D-063).
 * <ul>
 * <li>One armed trap per player (D-059), found on that player's own routes and base (D-061) and paced by their own
 * sessions: at most {@code maxTrapsPerSession} armed per session of theirs ("never two deaths' worth of setup in the
 * same session"). Sessions come from {@code HerobrineEvents.PARTICIPATION}.</li>
 * <li>Two armed traps never share ground: a new trap's spot stays {@code trapSpacingBlocks} (the shared moment radius,
 * 48) from every other armed trap's spot in the same dimension, and its zone never overlaps theirs, so friends together
 * (D-060) never get two setups in one place.</li>
 * <li>A trap kills whoever sets it off (D-062.6); the death is the victim's (D-063: their cross, their cause on their
 * line) and the trap is spent. A trap that just killed still claims a friend's death in the same moment
 * ({@code spentGraceSeconds}). Only a {@linkplain TrapKind#personal() personal} trap (the bed) is its own player's alone.</li>
 * <li>When a trap's player leaves or dies it stays armed and deniable for whoever sets it off (D-062.5); live traps
 * spring for whichever participant is in place. A personal trap is dropped when its player turns spectator.</li>
 * </ul>
 * Every edit is out of view of every player, spectators included. Trap windows run on the world play clock. Players
 * are kept by UUID and looked up again each tick (D-062.1).
 */
public final class AccidentPlannerImpl implements AccidentPlanner {
	/** What an arm attempt did. */
	public enum Status { ARMED, BUSY, SESSION, BLOCKED, UNTARGETABLE, NO_SPOT, IN_VIEW }

	/** The outcome of an arm attempt, with the trap and spot when it worked. */
	public record ArmResult(Status status, @Nullable ArmedTrap trap, @Nullable Candidate candidate, String message) {
		public boolean armed() {
			return status == Status.ARMED;
		}
	}

	/** A death a trap claims: whose trap it is, the trap, and whether it had already killed someone a moment ago. */
	public record Claim(UUID owner, ArmedTrap trap, boolean spent) {
	}

	/** A trap that just killed, the server tick it did, and who died of it (never claimed twice for the same player). */
	private record Spent(UUID owner, ArmedTrap trap, long tick, Set<UUID> victims) {
	}

	private final Function<MinecraftServer, AccidentData> data;
	private final ViewGate view;
	private final PlayerWatch watch;
	private final Supplier<DeathMarker> marker;
	private final RouteSampler sampler = new RouteSampler();
	/** Per player: candidate counts from the last refresh, by trap id, for the cards' cheap context gate. */
	private final Map<UUID, Map<String, Integer>> cachedCandidates = new HashMap<>();
	private final Map<UUID, Integer> refreshIndex = new HashMap<>();
	/** Per player: traps armed in their current session. */
	private final Map<UUID, Integer> sessionArms = new HashMap<>();
	private final List<Spent> spent = new ArrayList<>();
	/** Per player: the server tick of their last marked death (one death is marked once). */
	private final Map<UUID, Long> markedAt = new HashMap<>();
	private @Nullable MinecraftServer server;
	/** Set by a chunk load that holds torches due back; the next tick handles it. */
	private boolean restoreDue;

	/** The planner of the live game: core's {@link PlayerWatch} and the installed {@link DeathMarker}. */
	public AccidentPlannerImpl(Function<MinecraftServer, AccidentData> data, ViewGate view) {
		this(data, view, Services.watch(), Services::deaths);
	}

	/** A planner over its own view of who takes part and its own death marker (two-player tests). */
	public AccidentPlannerImpl(Function<MinecraftServer, AccidentData> data, ViewGate view, PlayerWatch watch, Supplier<DeathMarker> marker) {
		this.data = data;
		this.view = view;
		this.watch = watch;
		this.marker = marker;
	}

	public void attach(@Nullable MinecraftServer newServer) {
		server = newServer;
		sessionArms.clear();
		cachedCandidates.clear();
		refreshIndex.clear();
		spent.clear();
		markedAt.clear();
		sampler.reset();
	}

	public AccidentData data(MinecraftServer forServer) {
		return data.apply(forServer);
	}

	public ViewGate view() {
		return view;
	}

	public PlayerWatch watch() {
		return watch;
	}

	// --- AccidentPlanner ---

	/** The newest armed trap, whoever's it is: present while any trap is armed (the view from before one per player). */
	@Override
	public Optional<TrapType> armed() {
		return server == null ? Optional.empty() : data(server).armed().map(t -> new TrapType(t.type()));
	}

	/** This player's armed trap. */
	public Optional<TrapType> armed(UUID player) {
		return server == null ? Optional.empty() : data(server).armed(player).map(t -> new TrapType(t.type()));
	}

	/** The newest armed trap with everything about it (right after an arm, the one just armed). */
	public Optional<ArmedTrap> armedTrap(MinecraftServer forServer) {
		return data(forServer).armed();
	}

	/** This player's armed trap with everything about it. */
	public Optional<ArmedTrap> armedTrap(MinecraftServer forServer, UUID player) {
		return data(forServer).armed(player);
	}

	/** Every armed trap by its player, in the order they were armed. */
	public Map<UUID, ArmedTrap> armedTraps(MinecraftServer forServer) {
		return data(forServer).traps();
	}

	@Override
	public boolean arm(ServerPlayer player, TrapType type) {
		return Traps.byId(type.id()).map(kind -> arm(player, kind, false).armed()).orElse(false);
	}

	@Override
	public boolean arm(ServerPlayer player, TrapType type, BlockPos center) {
		return Traps.byId(type.id()).map(kind -> arm(player, kind, false, center).armed()).orElse(false);
	}

	/** Drops every armed trap (the endings stop them all). */
	@Override
	public void disarm() {
		if (server != null) {
			disarmAll(server, "disarmed");
		}
	}

	/** Drops the newest armed trap: the one {@link #armedTrap(MinecraftServer)} gives, so right after an arm the one just armed. */
	public void disarm(MinecraftServer forServer, String why) {
		data(forServer).newest().ifPresent(newest -> disarm(forServer, newest.getKey(), why));
	}

	/** Drops every armed trap. */
	public void disarmAll(MinecraftServer forServer, String why) {
		for (UUID owner : List.copyOf(data(forServer).traps().keySet())) {
			disarm(forServer, owner, why);
		}
	}

	/** Drops this player's armed trap. A dark corner whose torches are still out goes on to be put back. */
	public void disarm(MinecraftServer forServer, UUID player, String why) {
		AccidentData d = data(forServer);
		ArmedTrap trap = d.armed(player).orElse(null);
		if (trap == null) {
			return;
		}
		if (trap.type().equals(Traps.DARK_CORNER.id()) && trap.phase() == ArmedTrap.Phase.SET) {
			d.addRestoring(trap);
		}
		d.setArmed(player, null);
		d.forgetRoom(player);
		String line = trap.type() + " (" + name(forServer, player) + "'s) " + why;
		d.log("day " + GameClock.day(forServer) + ": " + line);
		A1016_02.LOGGER.info("[a1016] accident: {}", line);
	}

	@Override
	public boolean causedBy(ServerPlayer player, DamageSource source) {
		return claim(player, source).isPresent();
	}

	/**
	 * Which trap this death of {@code victim} came from, if any: their own trap first, then everyone else's in the
	 * order they were armed, then a trap that killed someone else a moment ago. Anyone can set off any trap but a
	 * personal one.
	 */
	public Optional<Claim> claim(ServerPlayer victim, DamageSource source) {
		MinecraftServer forServer = victim.level().getServer();
		AccidentData d = data(forServer);
		AccidentConfig cfg = AccidentConfig.get();
		long now = GameClock.playTicks(forServer);
		long clock = clock(forServer);
		UUID id = victim.getUUID();
		List<Map.Entry<UUID, ArmedTrap>> order = new ArrayList<>();
		d.armed(id).ifPresent(own -> order.add(Map.entry(id, own)));
		d.traps().forEach((owner, trap) -> {
			if (!owner.equals(id)) {
				order.add(Map.entry(owner, trap));
			}
		});
		for (Map.Entry<UUID, ArmedTrap> entry : order) {
			if (claims(victim, source, entry.getKey(), entry.getValue(), d, cfg, now, clock)) {
				return Optional.of(new Claim(entry.getKey(), entry.getValue(), false));
			}
		}
		long tick = forServer.getTickCount();
		for (Spent done : spent) {
			if (tick - done.tick() <= cfg.spentGraceTicks() && !done.victims().contains(id) && claims(victim, source, done.owner(), done.trap(), d, cfg, now, clock)) {
				return Optional.of(new Claim(done.owner(), done.trap(), true));
			}
		}
		return Optional.empty();
	}

	/** True if this trap of {@code owner}'s claims this death: set, in its window, in its place, its way of killing. */
	private static boolean claims(ServerPlayer victim, DamageSource source, UUID owner, ArmedTrap trap, AccidentData d, AccidentConfig cfg, long now,
			long clock) {
		TrapKind kind = Traps.byId(trap.type()).orElse(null);
		if (kind == null || !trap.isSet() || now < trap.setAt() || kind.expired(trap, now, clock, cfg)) {
			return false;
		}
		if (kind.personal() && !owner.equals(victim.getUUID())) {
			return false;
		}
		if (!kind.anywhere() && (!victim.level().dimension().equals(trap.dimension()) || !trap.zone(cfg.zoneSlack).contains(victim.position()))) {
			return false;
		}
		return kind.claims(victim, source, trap, d, now);
	}

	/** The overworld clock (day time), which the dark corner's morning is measured on. */
	static long clock(MinecraftServer forServer) {
		return forServer.overworld().getOverworldClockTime();
	}

	// --- arming ---

	/** True if nothing is armed for anyone (the one-trap view, for callers without a player; prefer the per-player one). */
	public boolean canArm(MinecraftServer forServer) {
		return data(forServer).traps().isEmpty();
	}

	/** Is there room for a trap for this player now (none of theirs armed, their session's share not used up)? */
	public boolean canArm(MinecraftServer forServer, UUID player) {
		return data(forServer).armed(player).isEmpty() && sessionArms(player) < AccidentConfig.get().maxTrapsPerSession;
	}

	/** Traps armed for this player in their current session. */
	public int sessionArms(UUID player) {
		return sessionArms.getOrDefault(player, 0);
	}

	/** A context centred on the player. */
	public TrapContext context(ServerPlayer player) {
		return context(player, player.blockPosition());
	}

	/** A context for this player (their routes, base and level), looking for spots around {@code center} in their level. */
	public TrapContext context(ServerPlayer player, BlockPos center) {
		ServerLevel level = player.level();
		MinecraftServer forServer = level.getServer();
		return new TrapContext(level, player, player.getUUID(), List.of(player), center.immutable(), data(forServer), view, AccidentConfig.get(),
				GameClock.playTicks(forServer), GameClock.day(forServer), watch);
	}

	/** The spots the planner would try for this player, nearest first (clear of everyone else's traps). */
	public List<Candidate> candidates(ServerPlayer player, TrapKind kind) {
		return candidates(player, kind, player.blockPosition());
	}

	/** The spots the planner would try around {@code center}, nearest first (clear of everyone else's traps). */
	public List<Candidate> candidates(ServerPlayer player, TrapKind kind, BlockPos center) {
		TrapContext ctx = context(player, center);
		return clearOfOthers(ctx.data(), player.getUUID(), kind, ctx, kind.candidates(ctx), AccidentConfig.get());
	}

	/**
	 * Arms a trap for this player at the nearest spot that can be set now. {@code debug} skips the session cap and lets a
	 * live trap that cannot spring yet (a missing core opt-in) be armed as a watch; one trap per player, the spacing
	 * from other traps and out-of-view always hold.
	 */
	public ArmResult arm(ServerPlayer player, TrapKind kind, boolean debug) {
		return arm(player, kind, debug, player.blockPosition());
	}

	/** {@link #arm(ServerPlayer, TrapKind, boolean)} with the spots looked for around {@code center}. */
	public ArmResult arm(ServerPlayer player, TrapKind kind, boolean debug, BlockPos center) {
		MinecraftServer forServer = player.level().getServer();
		AccidentData d = data(forServer);
		AccidentConfig cfg = AccidentConfig.get();
		UUID id = player.getUUID();
		String who = player.getName().getString();
		// D-062.7: nothing is set for a spectator or the dead.
		if (!player.isAlive() || player.isRemoved() || player.isSpectator()) {
			return new ArmResult(Status.UNTARGETABLE, null, null, who + " is not someone a trap can be set for now");
		}
		ArmedTrap mine = d.armed(id).orElse(null);
		if (mine != null) {
			return new ArmResult(Status.BUSY, mine, null, "a trap is already armed for " + who + ": " + mine.type());
		}
		if (!debug && sessionArms(id) >= cfg.maxTrapsPerSession) {
			return new ArmResult(Status.SESSION, null, null, "already armed one for " + who + " this session");
		}
		String blocked = kind.blocked();
		if (blocked != null && !(debug && kind.live())) {
			return new ArmResult(Status.BLOCKED, null, null, blocked);
		}
		TrapContext ctx = context(player, center);
		List<Candidate> found = kind.candidates(ctx);
		if (found.isEmpty()) {
			return new ArmResult(Status.NO_SPOT, null, null, "no spot for " + kind.id() + " near " + who);
		}
		List<Candidate> candidates = clearOfOthers(d, id, kind, ctx, found, cfg);
		if (candidates.isEmpty()) {
			return new ArmResult(Status.NO_SPOT, null, found.get(0), "every spot for " + kind.id() + " near " + who + " is too close to another player's trap");
		}
		int tries = 0;
		for (Candidate candidate : candidates) {
			if (tries++ >= cfg.maxSetupTries) {
				break;
			}
			if (kind.setup(ctx, candidate)) {
				ArmedTrap trap = build(kind, ctx, candidate, cfg);
				d.setArmed(id, trap);
				if (!debug) {
					sessionArms.merge(id, 1, Integer::sum);
				}
				String line = "day " + ctx.day() + ": " + kind.id() + " (" + who + "'s)" + (kind.live() ? " watching " : " set ") + Candidate.at(candidate.pos)
						+ " / clue: " + candidate.clue;
				d.log(line);
				A1016_02.LOGGER.info("[a1016] accident: {}", line);
				return new ArmResult(Status.ARMED, trap, candidate, kind.live() ? "watching" : "set");
			}
		}
		return new ArmResult(Status.IN_VIEW, null, candidates.get(0), "every spot was in view or refused (" + Math.min(tries, candidates.size()) + " tried)");
	}

	static ArmedTrap build(TrapKind kind, TrapContext ctx, Candidate candidate, AccidentConfig cfg) {
		long now = ctx.now();
		boolean live = kind.live();
		ResourceKey<Level> dimension = candidate.dimension != null ? candidate.dimension : ctx.level().dimension();
		return kind.onArmed(ctx, candidate, new ArmedTrap(kind.id(), dimension, candidate.pos, live ? List.of() : candidate.taken(), candidate.saved, candidate.off(),
				Optional.ofNullable(candidate.mob).map(Entity::getUUID).stream().toList(), live ? ArmedTrap.Phase.WATCHING : ArmedTrap.Phase.SET, now,
				live ? -1 : now, now + (live ? cfg.liveWatchTicks() : kind.window(cfg)), kind.clockUntil(ctx.level(), cfg), candidate.zoneMin,
				candidate.zoneMax, candidate.clue, 0));
	}

	/**
	 * The candidates on ground no other trap holds: none of everyone else's armed traps (nor a dark corner still waiting
	 * for its torches) in the same dimension has its spot within {@code trapSpacingBlocks} of the candidate's, or a zone
	 * overlapping its zone. So two players together never get two setups in one place, and two traps armed in one tick
	 * never share a spot (the second sees the first).
	 */
	static List<Candidate> clearOfOthers(AccidentData d, UUID owner, TrapKind kind, TrapContext ctx, List<Candidate> candidates, AccidentConfig cfg) {
		List<ArmedTrap> others = new ArrayList<>(d.restoring());
		d.traps().forEach((who, trap) -> {
			if (!who.equals(owner)) {
				others.add(trap);
			}
		});
		if (others.isEmpty()) {
			return candidates;
		}
		double spacing = (double) cfg.trapSpacingBlocks * cfg.trapSpacingBlocks;
		List<Candidate> clear = new ArrayList<>();
		for (Candidate candidate : candidates) {
			ResourceKey<Level> dimension = candidate.dimension != null ? candidate.dimension : ctx.level().dimension();
			AABB ground = ground(kind, candidate.pos, candidate.zoneMin, candidate.zoneMax, cfg.zoneSlack);
			boolean free = true;
			for (ArmedTrap other : others) {
				TrapKind otherKind = Traps.byId(other.type()).orElse(kind);
				if (other.dimension().equals(dimension) && (other.pos().distSqr(candidate.pos) < spacing
						|| ground(otherKind, other.pos(), other.zoneMin(), other.zoneMax(), cfg.zoneSlack).intersects(ground))) {
					free = false;
					break;
				}
			}
			if (free) {
				clear.add(candidate);
			}
		}
		return clear;
	}

	/** The ground a trap holds: where its death counts, grown by the slack; for a trap that counts anywhere, its spot. */
	static AABB ground(TrapKind kind, BlockPos pos, BlockPos zoneMin, BlockPos zoneMax, int slack) {
		AABB box = kind.anywhere() ? new AABB(pos) : AABB.encapsulatingFullBlocks(zoneMin, zoneMax);
		return box.inflate(slack);
	}

	// --- events ---

	/**
	 * {@code HerobrineEvents.PARTICIPATION}: a player's session starts or stops. A start is a new session of theirs (their
	 * own share of traps again). A stop never touches anyone else's session (D-062.4).
	 */
	public void onParticipation(MinecraftServer forServer, UUID player, boolean participating) {
		if (participating) {
			sessionArms.remove(player);
			cachedCandidates.remove(player);
			refreshIndex.remove(player);
			sampler.reset(player);
			return;
		}
		ServerPlayer still = forServer.getPlayerList().getPlayer(player);
		onStopped(forServer, player, still != null && still.isSpectator());
	}

	/**
	 * A player stopped taking part: they left, or (hardcore) turned spectator. D-062.5: whatever was running for them
	 * stops cleanly. Their trap stays armed and deniable for whoever sets it off, out there in the world, except a
	 * personal one (their bed) once they are a spectator: it could never count again, so it is dropped (the bed stays
	 * gone; that was the clue). Nothing is ever half built: setups are all or nothing, the dark corner's torches go back
	 * by morning on their own, and a live trap goes on watching for whoever comes.
	 */
	void onStopped(MinecraftServer forServer, UUID player, boolean spectator) {
		sampler.reset(player);
		cachedCandidates.remove(player);
		AccidentData d = data(forServer);
		AccidentData.PlayerData mine = d.players().get(player);
		if (mine != null) {
			mine.atCairn = false;
			mine.heading = null;
			mine.headingDimension = null;
		}
		ArmedTrap trap = d.armed(player).orElse(null);
		if (trap == null) {
			return;
		}
		boolean personal = Traps.byId(trap.type()).map(TrapKind::personal).orElse(false);
		if (spectator && personal) {
			disarm(forServer, player, "dropped: its player is out");
		} else {
			d.log("day " + GameClock.day(forServer) + ": " + trap.type() + " (" + name(forServer, player) + "'s) stays armed, " + (spectator ? "they are out" : "they left"));
		}
	}

	/**
	 * A player died. If an armed trap did it (anyone's: dangers hit whoever acts), the death is marked as that player's
	 * own (D-063) and the trap is spent; a second player killed by it in the same moment gets a marked death too.
	 */
	public void onDeath(ServerPlayer victim, DamageSource source) {
		if (!watch.isParticipant(victim)) {
			return;
		}
		Claim claim = claim(victim, source).orElse(null);
		if (claim == null) {
			return;
		}
		MinecraftServer forServer = victim.level().getServer();
		UUID id = victim.getUUID();
		long tick = forServer.getTickCount();
		Long last = markedAt.put(id, tick);
		if (last != null && last == tick) {
			return;
		}
		String word = listWord(source, claim.trap());
		String who = victim.getName().getString();
		marker.get().mark(victim, word, victim.blockPosition());
		if (claim.spent()) {
			for (Spent done : spent) {
				if (done.trap() == claim.trap()) {
					done.victims().add(id);
				}
			}
			data(forServer).log("day " + GameClock.day(forServer) + ": " + claim.trap().type() + " (" + name(forServer, claim.owner()) + "'s) also killed " + who + " ("
					+ word + ")");
			return;
		}
		disarm(forServer, claim.owner(), "killed " + who + " (" + word + ")");
		Set<UUID> victims = new HashSet<>();
		victims.add(id);
		spent.add(new Spent(claim.owner(), claim.trap(), tick, victims));
	}

	/** A mob died: a trap that hangs on that mob (the zombie wearing the sword, the moved enderman) is over. */
	public void onMobDied(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer forServer = level.getServer();
		for (Map.Entry<UUID, ArmedTrap> entry : List.copyOf(data(forServer).traps().entrySet())) {
			ArmedTrap trap = entry.getValue();
			if (trap.blames(entity.getUUID()) && Traps.byId(trap.type()).map(TrapKind::endsWithMob).orElse(false)) {
				disarm(forServer, entry.getKey(), "its mob died");
			}
		}
	}

	/** The list word for a death this trap claimed: the trap's own ("own sword"), else the damage's ("fell"). */
	static String listWord(DamageSource source, ArmedTrap trap) {
		TrapKind kind = Traps.byId(trap.type()).orElse(null);
		String own = kind == null ? null : kind.word(source, trap);
		return own != null ? own : DeathCauses.word(source, kind);
	}

	/**
	 * A mob was just added to a level (spawned, summoned or bred; never loaded from disk). An armed trap may now blame
	 * it: a monster born in a darkened corner, a phantom of its player's sleepless nights (that player looked up now).
	 */
	public void onSpawned(ServerLevel level, Entity entity) {
		if (!(entity instanceof Mob)) {
			return;
		}
		MinecraftServer forServer = level.getServer();
		AccidentData d = data(forServer);
		if (d.traps().isEmpty()) {
			return;
		}
		AccidentConfig cfg = AccidentConfig.get();
		for (Map.Entry<UUID, ArmedTrap> entry : List.copyOf(d.traps().entrySet())) {
			ArmedTrap trap = entry.getValue();
			TrapKind kind = Traps.byId(trap.type()).orElse(null);
			if (kind == null || !trap.isSet() || !kind.anywhere() && !trap.dimension().equals(level.dimension())) {
				continue;
			}
			ServerPlayer owner = targetable(forServer, entry.getKey());
			TrapContext ctx = new TrapContext(level, owner, entry.getKey(), owner == null ? List.of() : List.of(owner), trap.pos(), d, view, cfg,
					GameClock.playTicks(forServer), GameClock.day(forServer), watch);
			ArmedTrap next = kind.onSpawned(ctx, entity, trap);
			if (next != trap) {
				d.setArmed(entry.getKey(), next);
			}
		}
	}

	/** A chunk loaded: if it holds torches that are due back, they go back on this tick, before anyone can see in. */
	public void onChunkLoad(ServerLevel level, ChunkPos chunk) {
		AccidentData d = data(level.getServer());
		List<ArmedTrap> corners = new ArrayList<>(d.restoring());
		d.traps().values().stream().filter(t -> t.type().equals(Traps.DARK_CORNER.id())).forEach(corners::add);
		for (ArmedTrap trap : corners) {
			if (trap.dimension().equals(level.dimension()) && trap.saved().stream().anyMatch(s -> ChunkPos.containing(s.pos()).equals(chunk))) {
				restoreDue = true;
			}
		}
	}

	// --- ticking ---

	public void tick(MinecraftServer forServer) {
		AccidentConfig cfg = AccidentConfig.get();
		long tick = forServer.getTickCount();
		AccidentData d = data(forServer);
		long now = GameClock.playTicks(forServer);
		long day = GameClock.day(forServer);
		List<ServerPlayer> players = watch.participants(forServer);
		boolean sample = tick % AccidentConfig.cadenceTicks(cfg.routeSampleSeconds) == 0;
		for (ServerPlayer player : players) {
			if (sample) {
				sampler.sample(player, d, cfg, GameClock.playTicks(forServer, player), day);
			}
			if (watch.isTargetable(player)) {
				watchBurns(player, d, cfg, now);
				watchArmed(player, d, cfg, now);
			}
		}
		if (restoreDue || tick % AccidentConfig.cadenceTicks(cfg.plannerTickSeconds) == 0) {
			restoreDue = false;
			step(forServer, d, cfg, now);
		}
		if (tick % AccidentConfig.cadenceTicks(cfg.candidateRefreshSeconds) == 0) {
			for (ServerPlayer player : players) {
				if (watch.isTargetable(player)) {
					refreshOne(player);
				}
			}
		}
		spent.removeIf(done -> tick - done.tick() > cfg.spentGraceTicks());
	}

	/**
	 * One planner step: every participant's lures, the dark corners still out, and every armed trap (spring for whoever
	 * is in place, put back, expire), each in its own level.
	 */
	public void step(MinecraftServer forServer, AccidentData d, AccidentConfig cfg, long now) {
		long day = GameClock.day(forServer);
		List<ServerPlayer> players = watch.participants(forServer).stream().filter(watch::isTargetable).toList();
		for (ServerPlayer player : players) {
			watchLures(player, d, cfg, now);
		}
		for (ArmedTrap out : List.copyOf(d.restoring())) {
			ServerLevel level = forServer.getLevel(out.dimension());
			ArmedTrap back = level == null || !out.saved().stream().allMatch(s -> level.isLoaded(s.pos())) ? null : DarkCornerTrap.restore(level, view, out);
			if (level == null || back != null) {
				d.removeRestoring(out);
				d.log("day " + day + ": dark corner torches back" + (back != null && !back.clue().equals(out.clue()) ? "; " + back.clue() : ""));
			}
		}
		for (Map.Entry<UUID, ArmedTrap> entry : List.copyOf(d.traps().entrySet())) {
			stepTrap(forServer, d, entry.getKey(), entry.getValue(), players, cfg, now, day);
		}
	}

	/** One armed trap's step, with the players in its level who could set it off (its own player first). */
	private void stepTrap(MinecraftServer forServer, AccidentData d, UUID owner, ArmedTrap trap, List<ServerPlayer> players, AccidentConfig cfg, long now,
			long day) {
		TrapKind kind = Traps.byId(trap.type()).orElse(null);
		ServerLevel level = forServer.getLevel(trap.dimension());
		if (kind == null || level == null) {
			disarm(forServer, owner, "unknown trap or level");
			return;
		}
		if (level.isLoaded(trap.pos()) && trap.saved().stream().allMatch(s -> level.isLoaded(s.pos()))) {
			List<ServerPlayer> here = new ArrayList<>();
			ServerPlayer own = null;
			for (ServerPlayer player : players) {
				if (player.getUUID().equals(owner)) {
					own = player;
					if (player.level() == level) {
						here.addFirst(player);
					}
				} else if (player.level() == level) {
					here.add(player);
				}
			}
			ArmedTrap next = kind.tick(new TrapContext(level, own, owner, here, trap.pos(), d, view, cfg, now, day, watch), trap);
			if (next == null) {
				disarm(forServer, owner, "spot gone");
				return;
			}
			if (next != trap) {
				if (next.phase() != trap.phase()) {
					String line = "day " + day + ": " + trap.type() + " (" + name(forServer, owner) + "'s) " + next.phase().name().toLowerCase(Locale.ROOT) + " at "
							+ Candidate.at(next.pos());
					d.log(line);
					A1016_02.LOGGER.info("[a1016] accident: {}", line);
				}
				d.setArmed(owner, next);
				trap = next;
			}
		}
		if (kind.expired(trap, now, clock(forServer), cfg)) {
			disarm(forServer, owner, trap.isSet() ? "window over" : "watch over");
		}
	}

	/** House fire: every tick, notes when this player touches lava or fire that traces back to an armed gap. */
	void watchBurns(ServerPlayer player, AccidentData d, AccidentConfig cfg, long now) {
		for (ArmedTrap trap : d.traps().values()) {
			if (trap.phase() == ArmedTrap.Phase.SET && trap.type().equals(Traps.HOUSE_FIRE.id()) && player.level().dimension().equals(trap.dimension())
					&& trap.zone(cfg.zoneSlack).contains(player.position()) && HouseFireTrap.touchesTraced(player.level(), trap, player.getBoundingBox(), cfg)) {
				d.personal(player.getUUID()).tracedBurnTick = now;
			}
		}
	}

	/** Every tick: each armed trap's own watch over this player (a fall through a bridge gap), while it is set and they are in its level. */
	void watchArmed(ServerPlayer player, AccidentData d, AccidentConfig cfg, long now) {
		for (ArmedTrap trap : d.traps().values()) {
			if (trap.isSet() && player.level().dimension().equals(trap.dimension())) {
				Traps.byId(trap.type()).ifPresent(kind -> kind.watch(player, trap, d, cfg, now));
			}
		}
	}

	/** This player's visits to their own cairn, and the leaves put back in the grove they are at, for the lure traps. */
	void watchLures(ServerPlayer player, AccidentData d, AccidentConfig cfg, long now) {
		ServerLevel level = player.level();
		MinecraftServer forServer = level.getServer();
		UUID id = player.getUUID();
		GlobalPos here = GlobalPos.of(level.dimension(), player.blockPosition());
		GlobalPos home = watch.base(player).orElse(here);
		Lures.cairn(forServer, home, cfg.cairnSearchRadius).ifPresent(cairn -> {
			d.setCairn(id, cairn);
			if (!cairn.dimension().equals(level.dimension())) {
				return;
			}
			AccidentData.PlayerData mine = d.personal(id);
			double dist = Math.sqrt(Scan.horizontalDistSqr(cairn.pos(), player.blockPosition()));
			if (dist <= cfg.cairnVisitRadius && !mine.atCairn) {
				mine.atCairn = true;
				d.addCairnVisit(id);
				d.log("day " + GameClock.day(forServer) + ": " + player.getName().getString() + "'s cairn visit " + mine.cairnVisits());
			} else if (dist > cfg.cairnVisitRadius * 2.0) {
				mine.atCairn = false;
			}
		});
		SiteRegistry.Site grove = Lures.grove(here, cfg.groveSearchRadius).orElse(null);
		if (grove != null) {
			int leaves = GroveLureTrap.leaves(level, grove);
			AccidentData.GroveWatch seen = d.grove(GlobalPos.of(level.dimension(), grove.pos()));
			if (seen.leaves >= 0 && leaves > seen.leaves) {
				seen.leavesTick = now;
			}
			seen.leaves = leaves;
		}
	}

	// --- the cards' context gate ---

	/** This player's candidate count from the last refresh, for a cheap context gate. */
	public int cachedCandidates(UUID player, TrapKind kind) {
		Map<String, Integer> counts = cachedCandidates.get(player);
		return counts == null ? 0 : counts.getOrDefault(kind.id(), 0);
	}

	/** Refreshes one trap's candidate count for this player (round robin), so the cost is spread. */
	void refreshOne(ServerPlayer player) {
		int index = refreshIndex.merge(player.getUUID(), 1, Integer::sum) - 1;
		refresh(player, Traps.ALL.get(Math.floorMod(index, Traps.ALL.size())));
	}

	/** Stores a candidate count found elsewhere (the candidates command). */
	public void remember(UUID player, TrapKind kind, int count) {
		cachedCandidates.computeIfAbsent(player, k -> new HashMap<>()).put(kind.id(), count);
	}

	public int refresh(ServerPlayer player, TrapKind kind) {
		int count;
		try {
			count = kind.blocked() != null ? 0 : candidates(player, kind).size();
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] accident: candidate scan for {} failed", kind.id(), e);
			count = 0;
		}
		remember(player.getUUID(), kind, count);
		return count;
	}

	// --- players ---

	/** The participant with this UUID if something may happen to them now, looked up fresh (D-062.1), else null. */
	private @Nullable ServerPlayer targetable(MinecraftServer forServer, UUID player) {
		return watch.participant(forServer, player).filter(watch::isTargetable).orElse(null);
	}

	/** A player's name for the history and the log: online, else as last known, else the start of the UUID. */
	String name(MinecraftServer forServer, UUID player) {
		return watch.participant(forServer, player).map(p -> p.getName().getString())
				.or(() -> HerobrineState.get(forServer).player(player).map(HerobrineState.Subject::name))
				.orElse(player.toString().substring(0, 8));
	}
}
