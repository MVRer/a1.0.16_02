package com.forzacode.a1016_02.atmosphere.sc2.s22;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Ring;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 22's running file (docs/SELF_CREATED_2.md): animals of every kind walked in single file along a {@link Line}, the
 * leader along the line toward the destination, each next one after the one ahead of it * {@link Sc22Config#SPACING} behind the one ahead, at one slow pace the slowest of them can keep, silent, stopping for
 * nobody. After {@code walkSecondsMax} (or at the destination) each one that has been out of everyone's view for
 * {@link #GONE_AFTER} ticks is moved inside the destination and let go; the rest walk on into it. While someone follows
 * the file ({@link #FOLLOWED} of its last one) it walks on past its time (R10-6); while the destination is not loaded its
 * walkers walk on toward it, never let go in the field for it (R12-34); at 400 s whatever is left is let go where it is.
 * Every mob is bound to everyone who shared the moment. Server thread; one at a time.
 */
public final class Processions {
	public static final Processions LIVE = new Processions();
	public static final int EVERY = 10;
	static final int HOLD = 40;
	/** The leader is walked to the point this far ahead of it on the line (blocks). */
	static final double LEADER_AHEAD = 10.0;
	/** Nearer than this to the one ahead (blocks), an animal waits a step. */
	static final double TOO_CLOSE = 1.3;
	/** The pace (blocks a tick) at most, and the share of the slowest one's own walk it never passes. */
	static final double PACE_MAX = 0.095;
	static final double PACE_SHARE = 0.8;
	/** Out of everyone's view this long (ticks), an animal may be moved inside. */
	static final int GONE_AFTER = 200;
	/** Within this of the destination (blocks), the walk is over. */
	static final double ARRIVED = 6.0;
	/** Never longer than this (ticks) in all: whatever is left is let go where it is. */
	static final int MAX_TICKS = 20 * 400;

	public enum Phase {
		WALK, GOING, DONE
	}

	static final class Member {
		final Mob mob;
		long outOfViewSince = -1;

		Member(Mob mob) {
			this.mob = mob;
		}
	}

	/** A running file. */
	public static final class Procession {
		final ServerLevel level;
		final Line line;
		final @Nullable Vec3 inside;
		final List<Member> members = new ArrayList<>();
		final List<UUID> sharers;
		final int walkTicks;
		final boolean staged;
		final long started;
		final double pace;
		Phase phase = Phase.WALK;
		long nextStep;
		int moved;

		Procession(ServerLevel level, Line line, @Nullable Vec3 inside, List<UUID> sharers, int walkTicks, boolean staged, long now, double pace) {
			this.level = level;
			this.line = line;
			this.inside = inside;
			this.sharers = List.copyOf(sharers);
			this.walkTicks = walkTicks;
			this.staged = staged;
			this.started = now;
			this.nextStep = now;
			this.pace = pace;
		}

		public List<Mob> mobs() {
			return members.stream().map(m -> m.mob).toList();
		}

		public Line line() {
			return line;
		}

		public Phase phase() {
			return phase;
		}

		public int moved() {
			return moved;
		}

		/** Who it is for (read-only, for the debug index). */
		public List<UUID> sharers() {
			return sharers;
		}
	}

	private final List<Procession> running = new ArrayList<>();
	/** Whether a box is out of everyone's view; a test swaps in its own. */
	BiPredicate<ServerLevel, AABB> outOfView = (level, box) -> Services.traces().isOutOfView(level, box);
	/** R10-6: a file is followed when a living participant stands within this of its last animal (blocks). */
	public static final double FOLLOWED = 48.0;
	/** Whether anyone follows a file whose last animal is here; a test swaps in its own. */
	java.util.function.BiPredicate<ServerLevel, Vec3> followed = (level, at) -> Herds.here(level).stream()
			.anyMatch(player -> player.position().distanceToSqr(at) <= FOLLOWED * FOLLOWED);

	/** This set with its own rule for who follows (tests). */
	public Processions followedBy(java.util.function.BiPredicate<ServerLevel, Vec3> rule) {
		this.followed = rule;
		return this;
	}

	/** A separate set, ticked by its owner (tests); the game's is {@link #LIVE}. */
	public Processions() {
	}

	/** This set with its own view rule (tests). */
	public Processions with(BiPredicate<ServerLevel, AABB> outOfView) {
		this.outOfView = outOfView;
		return this;
	}

	/** The pace for these animals: {@link #PACE_SHARE} of the slowest one's own walk, at most {@link #PACE_MAX}. */
	static double pace(List<? extends Mob> mobs) {
		double slowest = PACE_MAX / PACE_SHARE;
		for (Mob mob : mobs) {
			double a = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
			slowest = Math.min(slowest, 2.92 * a * a);
		}
		return Math.min(PACE_MAX, slowest * PACE_SHARE);
	}

	/**
	 * Starts a file of these animals (in file order, the leader first, already standing on the line) walking {@code line},
	 * bound to {@code sharers}; {@code inside} is where they are moved once out of view (null: let go where they are).
	 * {@code staged}: the gallery's (they are never moved inside). Empty if one runs or too few can be walked.
	 */
	public Optional<Procession> start(ServerLevel level, Line line, List<? extends Mob> file, @Nullable Vec3 inside, List<UUID> sharers, int walkTicks,
			boolean staged) {
		if (!running.isEmpty()) {
			return Optional.empty();
		}
		long now = level.getServer().getTickCount();
		Procession p = new Procession(level, line, inside, sharers, walkTicks, staged, now, pace(file));
		for (Mob mob : file) {
			p.members.add(new Member(mob));
		}
		running.add(p);
		step(p, now);
		if (p.members.isEmpty()) {
			running.remove(p);
			return Optional.empty();
		}
		return Optional.of(p);
	}

	public void tick(MinecraftServer server) {
		if (running.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Procession> it = running.iterator(); it.hasNext();) {
			Procession p = it.next();
			if (now >= p.nextStep) {
				p.nextStep = now + EVERY;
				step(p, now);
			}
			if (p.phase == Phase.DONE) {
				it.remove();
			}
		}
	}

	void step(Procession p, long now) {
		if (!SelfCreated.enabled()) {
			end(p, "the layer is off");
			return;
		}
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		p.members.removeIf(m -> {
			boolean gone = !m.mob.isAlive() || m.mob.isRemoved() || m.mob.level() != p.level || m.mob.isLeashed() || m.mob.isPassenger() || m.mob.isVehicle();
			if (gone) {
				tamper.release(m.mob);
			}
			return gone;
		});
		if (p.members.isEmpty()) {
			end(p, "none left");
			return;
		}
		if (now - p.started >= MAX_TICKS) {
			end(p, "its time ran out");
			return;
		}
		Mob leader = p.members.getFirst().mob;
		double along = p.line.along(leader.position());
		boolean arrived = p.line.length() - along <= ARRIVED;
		// R10-6: while anyone follows the file (within FOLLOWED of its last one), it walks on to the place, up to its 400 s.
		if (p.phase == Phase.WALK && (arrived || now - p.started >= p.walkTicks && !followed.test(p.level, p.members.getLast().mob.position()))) {
			p.phase = Phase.GOING;
		}
		if (p.phase == Phase.GOING && !p.staged) {
			goInside(p, now);
			if (p.members.isEmpty()) {
				end(p, "all went in");
				return;
			}
			leader = p.members.getFirst().mob;
			along = p.line.along(leader.position());
		}
		// The leader: along the line, a little ahead of itself, toward the destination (into it at the end).
		double ahead = Math.min(along + LEADER_AHEAD, p.line.length() + 4.0);
		Vec3 to = ahead >= p.line.length() && p.inside != null ? p.inside : ground(p.level, p.line.at(Math.max(ahead, 0.0)));
		walk(p, p.members.getFirst(), to, Ring.factorFor(p.pace, leader.getAttributeValue(Attributes.MOVEMENT_SPEED)));
		// Each next one: after the one ahead of it, at the pace that keeps it the spacing behind; too close, it waits.
		for (int i = 1; i < p.members.size(); i++) {
			Member m = p.members.get(i);
			Mob ahead1 = p.members.get(i - 1).mob;
			double gap = Math.sqrt(Herds.flatSqr(m.mob.position(), ahead1.position()));
			if (gap < TOO_CLOSE) {
				walk(p, m, m.mob.position(), 0.3);
				continue;
			}
			double want = Math.clamp(p.pace * (1.0 + 0.8 * (gap - Sc22Config.SPACING)), p.pace * 0.25, p.pace * 1.8);
			walk(p, m, ahead1.position(), Ring.factorFor(want, m.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
		}
	}

	private void walk(Procession p, Member m, Vec3 to, double factor) {
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		tamper.silence(m.mob, HOLD);
		if (tamper.steer(m.mob, to, factor, HOLD) && !p.sharers.isEmpty()) {
			tamper.bind(m.mob, p.sharers);
		}
	}

	/** After the walk: each one out of everyone's view for {@link #GONE_AFTER} is moved inside the destination and let go. */
	private void goInside(Procession p, long now) {
		for (Iterator<Member> it = p.members.iterator(); it.hasNext();) {
			Member m = it.next();
			if (!outOfView.test(p.level, m.mob.getBoundingBox())) {
				m.outOfViewSince = -1;
				continue;
			}
			if (m.outOfViewSince < 0) {
				m.outOfViewSince = now;
			}
			if (now - m.outOfViewSince < GONE_AFTER || p.inside == null) {
				continue;
			}
			if (!p.level.isLoaded(BlockPos.containing(p.inside))) {
				continue; // R12-34: the place is not loaded yet: it walks on toward it (out of view) until it is, or the 400 s are up
			}
			Optional<BlockPos> cell = insideCell(p.level, p.inside, m.mob);
			MobTamperImpl.INSTANCE.release(m.mob);
			if (cell.isPresent() && Services.mobs().moveOutOfView(m.mob, cell.get())) {
				p.moved++;
			}
			it.remove();
		}
	}

	/** A cell an animal fits in near {@code inside} (within 4 across, 3 up and down). */
	static Optional<BlockPos> insideCell(ServerLevel level, Vec3 inside, Mob mob) {
		int bx = (int) Math.floor(inside.x);
		int by = (int) Math.floor(inside.y);
		int bz = (int) Math.floor(inside.z);
		for (int d = 0; d <= 4; d++) {
			for (int dx = -d; dx <= d; dx++) {
				for (int dz = -d; dz <= d; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
						continue;
					}
					OptionalInt feet = Herds.feet(level, bx + dx, bz + dz, by, 3, 3);
					if (feet.isEmpty()) {
						continue;
					}
					BlockPos cell = new BlockPos(bx + dx, feet.getAsInt(), bz + dz);
					if (level.noCollision(mob, mob.getDimensions(mob.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(cell)))) {
						return Optional.of(cell);
					}
				}
			}
		}
		return Optional.empty();
	}

	/** The point on the ground at {@code at}'s column (the surface, leaves aside), or {@code at} where it is not loaded. */
	static Vec3 ground(ServerLevel level, Vec3 at) {
		int x = (int) Math.floor(at.x);
		int z = (int) Math.floor(at.z);
		if (!level.isLoaded(new BlockPos(x, (int) at.y, z))) {
			return at;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new Vec3(at.x, y, at.z);
	}

	/** Ends a file: whatever is left is let go where it is. */
	void end(Procession p, String why) {
		for (Member m : p.members) {
			MobTamperImpl.INSTANCE.release(m.mob);
		}
		p.members.clear();
		p.phase = Phase.DONE;
		A1016_02.LOGGER.debug("[a1016] sc_22: ended ({})", why);
	}

	public boolean running() {
		return !running.isEmpty();
	}

	public List<Procession> processions() {
		return List.copyOf(running);
	}

	/** Everything ends now (the switch, a stop). */
	public void clear() {
		for (Procession p : new ArrayList<>(running)) {
			end(p, "cleared");
		}
		running.clear();
	}

	public List<String> describe() {
		List<String> lines = new ArrayList<>();
		for (Procession p : running) {
			Vec3 lead = p.members.isEmpty() ? p.line.start() : p.members.getFirst().mob.position();
			lines.add(String.format(Locale.ROOT, "running: %d walking, %s, %.0f of %.0f along, %d moved in, lead at %.0f %.0f %.0f", p.members.size(),
					p.phase.name().toLowerCase(Locale.ROOT), p.line.along(lead), p.line.length(), p.moved, lead.x, lead.y, lead.z));
		}
		if (lines.isEmpty()) {
			lines.add("none running");
		}
		return lines;
	}
}
