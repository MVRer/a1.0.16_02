package com.forzacode.a1016_02.accident;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.trap.DarkCornerTrap;
import com.forzacode.a1016_02.accident.trap.GroveLureTrap;
import com.forzacode.a1016_02.core.AccidentPlanner;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.TrapType;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * The real {@link AccidentPlanner}. One armed trap at a time, at most {@code maxTrapsPerSession} armed per session
 * ("never two deaths' worth of setup in the same session"), every edit out of view. It learns the subject's routes,
 * watches the lures, springs live traps at their moment, puts the dark corner back before morning, and says whether a
 * death came from the armed trap (place, damage type, time window).
 */
public final class AccidentPlannerImpl implements AccidentPlanner {
	/** What an arm attempt did. */
	public enum Status { ARMED, BUSY, SESSION, BLOCKED, NO_SPOT, IN_VIEW }

	/** The outcome of an arm attempt, with the trap and spot when it worked. */
	public record ArmResult(Status status, @Nullable ArmedTrap trap, @Nullable Candidate candidate, String message) {
		public boolean armed() {
			return status == Status.ARMED;
		}
	}

	private final Function<MinecraftServer, AccidentData> data;
	private final ViewGate view;
	private final RouteSampler sampler = new RouteSampler();
	private final Map<String, Integer> cachedCandidates = new HashMap<>();
	private @Nullable MinecraftServer server;
	private int sessionArms;
	private int refreshIndex;

	public AccidentPlannerImpl(Function<MinecraftServer, AccidentData> data, ViewGate view) {
		this.data = data;
		this.view = view;
	}

	public void attach(@Nullable MinecraftServer newServer) {
		server = newServer;
		sessionArms = 0;
		cachedCandidates.clear();
		sampler.reset();
	}

	public AccidentData data(MinecraftServer forServer) {
		return data.apply(forServer);
	}

	public ViewGate view() {
		return view;
	}

	// --- AccidentPlanner ---

	@Override
	public Optional<TrapType> armed() {
		return server == null ? Optional.empty() : data(server).armed().map(t -> new TrapType(t.type()));
	}

	/** The armed trap with everything about it. */
	public Optional<ArmedTrap> armedTrap(MinecraftServer forServer) {
		return data(forServer).armed();
	}

	@Override
	public boolean arm(ServerPlayer player, TrapType type) {
		return Traps.byId(type.id()).map(kind -> arm(player, kind, false).armed()).orElse(false);
	}

	@Override
	public void disarm() {
		if (server != null) {
			disarm(server, "disarmed");
		}
	}

	/** Drops the armed trap. A dark corner whose torches are still out goes on to be put back. */
	public void disarm(MinecraftServer forServer, String why) {
		AccidentData d = data(forServer);
		d.armed().ifPresent(trap -> {
			if (trap.type().equals(Traps.DARK_CORNER.id()) && trap.phase() == ArmedTrap.Phase.SET) {
				d.setRestoring(trap);
			}
			d.log("day " + GameClock.day(forServer) + ": " + trap.type() + " " + why);
			A1016_02.LOGGER.info("[a1016] accident: {} {}", trap.type(), why);
		});
		d.setArmed(null);
	}

	@Override
	public boolean causedBy(ServerPlayer player, DamageSource source) {
		MinecraftServer forServer = player.level().getServer();
		ArmedTrap trap = data(forServer).armed().orElse(null);
		if (trap == null || !trap.isSet()) {
			return false;
		}
		TrapKind kind = Traps.byId(trap.type()).orElse(null);
		long now = GameClock.playTicks(forServer);
		if (kind == null || now < trap.setAt() || now > trap.until()) {
			return false;
		}
		if (!kind.anywhere() && (!player.level().dimension().equals(trap.dimension())
				|| !trap.zone(AccidentConfig.get().zoneSlack).contains(player.position()))) {
			return false;
		}
		return kind.matches(source, trap);
	}

	// --- arming ---

	/** Is there room for a trap now (none armed, session cap not reached)? */
	public boolean canArm(MinecraftServer forServer) {
		return data(forServer).armed().isEmpty() && sessionArms < AccidentConfig.get().maxTrapsPerSession;
	}

	public int sessionArms() {
		return sessionArms;
	}

	/** A context centred on the player. */
	public TrapContext context(ServerPlayer player) {
		ServerLevel level = player.level();
		MinecraftServer forServer = level.getServer();
		return new TrapContext(level, player, player.blockPosition(), data(forServer), view, AccidentConfig.get(), GameClock.playTicks(forServer),
				GameClock.day(forServer));
	}

	public List<Candidate> candidates(ServerPlayer player, TrapKind kind) {
		return kind.candidates(context(player));
	}

	/**
	 * Arms a trap at the nearest spot that can be set now. {@code debug} skips the session cap and lets a live trap
	 * that cannot spring yet (a missing core opt-in) be armed as a watch; one-at-a-time and out-of-view always hold.
	 */
	public ArmResult arm(ServerPlayer player, TrapKind kind, boolean debug) {
		MinecraftServer forServer = player.level().getServer();
		AccidentData d = data(forServer);
		AccidentConfig cfg = AccidentConfig.get();
		if (d.armed().isPresent()) {
			return new ArmResult(Status.BUSY, d.armed().get(), null, "a trap is already armed: " + d.armed().get().type());
		}
		if (!debug && sessionArms >= cfg.maxTrapsPerSession) {
			return new ArmResult(Status.SESSION, null, null, "already armed one this session");
		}
		String blocked = kind.blocked();
		if (blocked != null && !(debug && kind.live())) {
			return new ArmResult(Status.BLOCKED, null, null, blocked);
		}
		TrapContext ctx = context(player);
		List<Candidate> candidates = kind.candidates(ctx);
		if (candidates.isEmpty()) {
			return new ArmResult(Status.NO_SPOT, null, null, "no spot for " + kind.id() + " near you");
		}
		int tries = 0;
		for (Candidate candidate : candidates) {
			if (tries++ >= cfg.maxSetupTries) {
				break;
			}
			if (kind.setup(ctx, candidate)) {
				ArmedTrap trap = build(kind, ctx, candidate, cfg);
				d.setArmed(trap);
				if (!debug) {
					sessionArms++;
				}
				String line = "day " + ctx.day() + ": " + kind.id() + (kind.live() ? " watching " : " set ") + Candidate.at(candidate.pos) + " / clue: " + candidate.clue;
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
		return new ArmedTrap(kind.id(), dimension, candidate.pos, live ? List.of() : candidate.taken(), candidate.saved, candidate.off(),
				Optional.ofNullable(candidate.mob).map(Entity::getUUID), live ? ArmedTrap.Phase.WATCHING : ArmedTrap.Phase.SET, now, live ? -1 : now,
				now + (live ? cfg.liveWatchTicks() : kind.window(cfg)), candidate.zoneMin, candidate.zoneMax, candidate.clue, 0);
	}

	// --- events ---

	/** The subject joined: a new session. */
	public void onJoin(ServerPlayer player) {
		if (Services.watch().isSubject(player)) {
			sessionArms = 0;
			sampler.reset();
		}
	}

	/** A player died. If the armed trap did it, the death is marked and the trap is spent. */
	public void onDeath(ServerPlayer player, DamageSource source) {
		if (!Services.watch().isSubject(player) || !causedBy(player, source)) {
			return;
		}
		MinecraftServer forServer = player.level().getServer();
		ArmedTrap trap = data(forServer).armed().orElseThrow();
		String word = DeathCauses.word(source, Traps.byId(trap.type()).orElse(null));
		Services.deaths().mark(player, word, player.blockPosition());
		disarm(forServer, "killed (" + word + ")");
	}

	// --- ticking ---

	public void tick(MinecraftServer forServer) {
		AccidentConfig cfg = AccidentConfig.get();
		long tick = forServer.getTickCount();
		ServerPlayer subject = Services.watch().subject(forServer).orElse(null);
		AccidentData d = data(forServer);
		long now = GameClock.playTicks(forServer);
		if (subject != null && tick % AccidentConfig.cadenceTicks(cfg.routeSampleSeconds) == 0) {
			sampler.sample(subject, d, cfg, now, GameClock.day(forServer));
		}
		if (tick % AccidentConfig.cadenceTicks(cfg.plannerTickSeconds) == 0) {
			step(forServer, d, subject, cfg, now);
		}
		if (subject != null && tick % AccidentConfig.cadenceTicks(cfg.candidateRefreshSeconds) == 0) {
			refreshOne(subject);
		}
	}

	/** One planner step: lures, a dark corner still out, and the armed trap (spring, put back, expire). */
	public void step(MinecraftServer forServer, AccidentData d, @Nullable ServerPlayer subject, AccidentConfig cfg, long now) {
		long day = GameClock.day(forServer);
		if (subject != null) {
			watchLures(subject, d, cfg, now);
		}
		ArmedTrap out = d.restoring().orElse(null);
		if (out != null) {
			ServerLevel level = forServer.getLevel(out.dimension());
			if (level == null || level.isLoaded(out.pos())
					&& DarkCornerTrap.restore(level, new TrapContext(level, subject, out.pos(), d, view, cfg, now, day), out)) {
				d.setRestoring(null);
				d.log("day " + day + ": dark corner torches back");
			}
		}
		ArmedTrap trap = d.armed().orElse(null);
		if (trap == null) {
			return;
		}
		TrapKind kind = Traps.byId(trap.type()).orElse(null);
		ServerLevel level = forServer.getLevel(trap.dimension());
		if (kind == null || level == null) {
			disarm(forServer, "unknown trap or level");
			return;
		}
		if (level.isLoaded(trap.pos())) {
			ArmedTrap next = kind.tick(new TrapContext(level, subject, trap.pos(), d, view, cfg, now, day), trap);
			if (next == null) {
				disarm(forServer, "spot gone");
				return;
			}
			if (next != trap) {
				if (next.phase() != trap.phase()) {
					String line = "day " + day + ": " + trap.type() + " " + next.phase().name().toLowerCase() + " at " + Candidate.at(next.pos());
					d.log(line);
					A1016_02.LOGGER.info("[a1016] accident: {}", line);
				}
				d.setArmed(next);
				trap = next;
			}
		}
		if (now > trap.until()) {
			disarm(forServer, trap.isSet() ? "window over" : "watch over");
		}
	}

	/** Cairn visits and grove leaves, for the lure traps. */
	void watchLures(ServerPlayer subject, AccidentData d, AccidentConfig cfg, long now) {
		ServerLevel level = subject.level();
		MinecraftServer forServer = level.getServer();
		GlobalPos here = GlobalPos.of(level.dimension(), subject.blockPosition());
		GlobalPos home = Services.watch().base(subject).orElse(here);
		Lures.cairn(forServer, home, cfg.cairnSearchRadius).ifPresent(cairn -> {
			d.setCairn(cairn);
			if (!cairn.dimension().equals(level.dimension())) {
				return;
			}
			double dist = Math.sqrt(Scan.horizontalDistSqr(cairn.pos(), subject.blockPosition()));
			if (dist <= cfg.cairnVisitRadius && !d.atCairn) {
				d.atCairn = true;
				d.addCairnVisit();
				d.log("day " + GameClock.day(forServer) + ": cairn visit " + d.cairnVisits());
			} else if (dist > cfg.cairnVisitRadius * 2.0) {
				d.atCairn = false;
			}
		});
		SiteRegistry.Site grove = Lures.grove(here, cfg.groveSearchRadius).orElse(null);
		if (grove != null) {
			int leaves = GroveLureTrap.leaves(level, grove);
			AccidentData.LureWatch watch = d.lure;
			if (grove.id() != watch.groveSite) {
				watch.groveSite = grove.id();
				watch.groveLeaves = leaves;
			} else if (leaves > watch.groveLeaves) {
				watch.groveLeaves = leaves;
				watch.groveLeavesTick = now;
			} else {
				watch.groveLeaves = leaves;
			}
		}
	}

	// --- the cards' context gate ---

	/** Candidate count from the last refresh, for a cheap context gate. */
	public int cachedCandidates(TrapKind kind) {
		return cachedCandidates.getOrDefault(kind.id(), 0);
	}

	/** Refreshes one trap's candidate count (round robin), so the cost is spread. */
	void refreshOne(ServerPlayer subject) {
		List<TrapKind> all = Traps.ALL;
		TrapKind kind = all.get(Math.floorMod(refreshIndex++, all.size()));
		refresh(subject, kind);
	}

	/** Stores a candidate count found elsewhere (the candidates command). */
	public void remember(TrapKind kind, int count) {
		cachedCandidates.put(kind.id(), count);
	}

	public int refresh(ServerPlayer subject, TrapKind kind) {
		int count;
		try {
			count = kind.blocked() != null ? 0 : kind.candidates(context(subject)).size();
		} catch (RuntimeException e) {
			A1016_02.LOGGER.error("[a1016] accident: candidate scan for {} failed", kind.id(), e);
			count = 0;
		}
		cachedCandidates.put(kind.id(), count);
		return count;
	}
}
