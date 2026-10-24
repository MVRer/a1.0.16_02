package com.forzacode.a1016_02.atmosphere.sc2.s24;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.sc2.s21.Herds;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 24's follower (docs/SELF_CREATED_2.md; review R10-10, R11-14): one animal, at the edge of a player's view, for a short
 * while, and then at their window that night.
 * <ul>
 * <li><b>The follow</b> (at most {@link #FOLLOW_TICKS}, 5 minutes): walked after the player along the way they came
 * ({@link Trail}) {@code followMin} to {@code followMax} back (the edge of view), stopping where they stop, never nearer
 * than {@code nearest} (it backs off), standing still and facing them while they look at it; once out of everyone's view
 * and over {@code catchUpAt} behind, moved up out of view on their way, but never after a rider or a runner (a cow that
 * keeps up with a horse is a mod, not a cow: it is left behind). With the player underground it waits at the mouth they
 * went in by, facing in.</li>
 * <li><b>Away</b>: then it walks off and, out of everyone's view, goes back where it was taken (its own field, through the
 * ledger, when it was the player's own stock: R10-10) or 150 off, and is let go: an animal again.</li>
 * <li><b>The window</b>: that night, with the player indoors at home, it (or, if it is not to be found, another of their
 * own animals) stands {@link #WINDOW_NEAR} to {@link #WINDOW_FAR} out from their nearest window or door on its axis (R11-14:
 * its face fills the pane), frozen, facing in, until dawn or they go out.</li>
 * <li><b>The end</b> ({@code days} after the start, carried on to dawn when that falls at night; or they left; or the
 * switch): every animal it took from their home goes back there (the ledger's own undo), the others are let go.</li>
 * </ul>
 * Bound to its player only (UNSHARED). Server thread; one at a time.
 */
public final class Follows {
	/** The ledger's cause for a follower taken from where it stood (R13-7): the switch and Ending D put it back. */
	public static final String CAUSE = "atmosphere:s24";
	private static final BiPredicate<ServerLevel, AABB> LIVE_VIEW = (level, box) -> Services.traces().isOutOfView(level, box);
	public static final Follows LIVE = new Follows();
	/** A step every this many ticks. */
	public static final int EVERY = 10;
	/** Each step's steer, freeze and face last this long (ticks). */
	static final int HOLD = 30;
	/** How far back along the way it keeps (blocks): the middle of the band. */
	static final double KEEP_FRACTION = 0.5;
	/** Out of sight for this long underground (ticks), the player is in. */
	static final int UNDERGROUND_TICKS = 100;
	/** Home: within this of the base. */
	static final double HOME = 12.0;
	/** R10-10: the follow lasts at most this long (ticks, 5 minutes): a glimpse, never an escort. */
	public static final int FOLLOW_TICKS = 6000;
	/** R10-10: an animal within this of the player's base is their own stock (blocks, horizontal). */
	public static final double BASE_NEAR = 32.0;
	/** R11-14: at the window it stands this far out on the window's axis, so its face fills the pane. */
	public static final int WINDOW_NEAR = 3;
	public static final int WINDOW_FAR = 5;
	/** A player who moves this far in one step (blocks in {@link #EVERY} ticks: over a sprint) is riding or running. */
	static final double FAST_STEP = 3.0;
	/** Away: moved at least this far from the player when it was not theirs. */
	static final double GONE = 150.0;
	/** No door or window found, it looks again after this long (ticks). */
	static final int WINDOW_RETRY = 200;
	/** Going away is given up after this long (ticks): it is let go where it is, out of view if it can be. */
	static final int AWAY_TRIES_TICKS = 1200;
	/** The end is over after this long (ticks) whatever: seen all along, it is let go where it is (R12-35). */
	static final int END_HARD_TICKS = 6000;

	public enum Mode {
		FOLLOW, MOUTH, AWAY, WAITING, WINDOW, ENDING, DONE
	}

	/** The one running. */
	public static final class Follow {
		/** The animal now: the follower, or at the window another of the player's own. */
		UUID animal;
		/** The last loaded object of it (it may unload and come back as a new object). */
		Mob mob;
		final Followed target;
		final Trail trail = new Trail();
		final long untilClock;
		final boolean staged;
		final long started;
		Mode mode = Mode.FOLLOW;
		long nextStep;
		long indoorsSince = -1;
		long awaySince = -1;
		long endingSince = -1;
		/** No door or window was found: not looked for again before this tick. */
		long windowRetryAt;
		@Nullable Vec3 spot;
		@Nullable Vec3 faceAt;
		Vec3 lastSeenAt;
		String why = "";
		/** Where it stood when it was taken: its first move is ledgered from there (R13-7). */
		BlockPos origin;
		/** It was the player's own stock (within {@link #BASE_NEAR} of their base when it was taken). */
		boolean fromBase;
		/** The ledger's entries of the animals it took (each one's first move), given back at the end where they came from home. */
		final List<TraceLedger.Entry> taken = new ArrayList<>();
		TraceLedger.@Nullable Entry entry;

		Follow(Mob mob, Followed target, long untilClock, boolean staged, long now) {
			this.mob = mob;
			this.animal = mob.getUUID();
			this.origin = mob.blockPosition().immutable();
			this.target = target;
			this.untilClock = untilClock;
			this.staged = staged;
			this.started = now;
			this.nextStep = now;
			this.lastSeenAt = target.position();
			this.fromBase = target.base().map(b -> Herds.flatSqr(mob.position(), Vec3.atBottomCenterOf(b)) <= BASE_NEAR * BASE_NEAR).orElse(false);
		}

		public Mob mob() {
			return mob;
		}

		public Followed target() {
			return target;
		}

		public Mode mode() {
			return mode;
		}

		public Trail trail() {
			return trail;
		}

		public @Nullable Vec3 spot() {
			return spot;
		}

		/** The level clock at which its time ends (read-only, for the debug index). */
		public long untilClock() {
			return untilClock;
		}

		/** It was the player's own stock. */
		public boolean fromBase() {
			return fromBase;
		}
	}

	private @Nullable Follow follow;
	/** Whether a box is out of everyone's view; a test swaps in its own viewpoints. */
	BiPredicate<ServerLevel, AABB> outOfView = LIVE_VIEW;
	/** The clock a follow's day is counted on; a test may swap in its own. */
	java.util.function.ToLongFunction<ServerLevel> clock = ServerLevel::getDefaultClockTime;
	/** The trace service the ledger's give-backs go through; a test may swap in its own. */
	@Nullable TraceService traces;

	/** A separate follower, ticked by its owner (tests); the game's is {@link #LIVE}. */
	public Follows() {
	}

	/** This one with its own view rule and clock (tests). */
	public Follows with(BiPredicate<ServerLevel, AABB> outOfView, java.util.function.ToLongFunction<ServerLevel> clock) {
		this.outOfView = outOfView;
		this.clock = clock;
		return this;
	}

	/** This one with its own trace service for the give-backs (tests). */
	public Follows withTraces(TraceService traces) {
		this.traces = traces;
		return this;
	}

	/** How long a follow lasts before it goes away (ticks); a test shortens it. */
	int followTicks = FOLLOW_TICKS;

	/** This one with a follow of its own length (tests). */
	public Follows followFor(int ticks) {
		this.followTicks = ticks;
		return this;
	}

	private TraceService traces() {
		return traces != null ? traces : Services.traces();
	}

	public @Nullable Follow current() {
		return follow;
	}

	public boolean running() {
		return follow != null;
	}

	/**
	 * Pure: the level clock its time ends at: {@code days} after {@code now}, carried on to the next dawn (23000) when that
	 * falls in the night, so it is gone at dawn, never in the dark.
	 */
	public static long until(long now, double days) {
		long until = now + Math.round(days * 24_000.0);
		long tod = Math.floorMod(until, Herds.DAY);
		if (Herds.night(tod)) {
			until += Herds.NIGHT_UNTIL - tod;
		}
		return until;
	}

	/** Starts following {@code target} with {@code mob} for {@code days} in-game days (to dawn); false if one runs or it cannot be walked. */
	public boolean start(Mob mob, Followed target, double days, boolean staged) {
		if (follow != null) {
			return false;
		}
		ServerLevel level = target.level();
		long now = level.getServer().getTickCount();
		if (MobTamperImpl.INSTANCE.steerRefusal(mob, SelfCreated.enabled(), MobTamperImpl.INSTANCE.steeredCount()) != null) {
			return false;
		}
		Follow f = new Follow(mob, target, until(clock.applyAsLong(level), days), staged, now);
		f.trail.add(target.position(), target.outdoors());
		if (!staged && f.fromBase) {
			taken(f, level, mob); // their own: ledgered from the start, so it always goes back home (the end, the switch, Ending D)
		}
		follow = f;
		step(f, now);
		return follow != null;
	}

	/** Every server tick: the follow steps every {@link #EVERY} ticks. */
	public void tick(MinecraftServer server) {
		Follow f = follow;
		if (f == null) {
			return;
		}
		long now = server.getTickCount();
		if (now >= f.nextStep) {
			f.nextStep = now + EVERY;
			step(f, now);
		}
	}

	/** The animal now, loaded and alive in the player's level, or null. */
	private static @Nullable Mob resolve(Follow f, ServerLevel level) {
		if (f.mob != null && !f.mob.isRemoved() && f.mob.isAlive() && f.mob.level() == level) {
			return f.mob;
		}
		Entity found = level.getEntity(f.animal);
		if (found instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) {
			f.mob = mob;
			return mob;
		}
		return null;
	}

	/** One step. */
	void step(Follow f, long now) {
		if (!SelfCreated.enabled()) {
			stop(f, "the layer is off");
			return;
		}
		ServerLevel level = f.target.level();
		boolean present = f.target.present();
		if (f.mode != Mode.ENDING && (!present || clock.applyAsLong(level) >= f.untilClock)) {
			f.mode = Mode.ENDING;
			f.endingSince = now;
		}
		Mob mob = resolve(f, level);
		boolean held = mob == null || mob.isLeashed() || mob.isPassenger() || mob.isVehicle();
		if (f.mode == Mode.ENDING) {
			ending(f, level, now, held ? null : mob);
			return;
		}
		Vec3 at = f.target.position();
		Vec3 last = f.lastSeenAt;
		f.lastSeenAt = at;
		boolean outdoors = f.target.outdoors();
		f.trail.add(at, outdoors);
		f.indoorsSince = outdoors ? -1 : f.indoorsSince < 0 ? now : f.indoorsSince;
		Optional<BlockPos> base = f.target.base();
		long tod = Math.floorMod(clock.applyAsLong(level), Herds.DAY);
		boolean home = !outdoors && Herds.night(tod) && base.isPresent() && Herds.flatSqr(at, Vec3.atCenterOf(base.get())) <= HOME * HOME;
		Mode wanted = f.mode;
		if (home) {
			wanted = Mode.WINDOW;
		} else if (f.mode == Mode.WINDOW) {
			wanted = Mode.AWAY; // they went out: it is gone from the window
		} else if (f.mode == Mode.FOLLOW || f.mode == Mode.MOUTH) {
			boolean fast = f.target.riding() || at.distanceTo(last) > FAST_STEP;
			double dist = mob == null ? Double.MAX_VALUE : Math.sqrt(Herds.flatSqr(mob.position(), at));
			if (now - f.started >= followTicks || held || fast && dist > Sc24Config.get().catchUpAt) {
				wanted = Mode.AWAY; // its few minutes are over, or it was left behind: never an escort, never a cow that keeps up with a horse
			} else if (!outdoors && f.indoorsSince >= 0 && now - f.indoorsSince >= UNDERGROUND_TICKS && f.trail.mouth().isPresent()) {
				wanted = Mode.MOUTH;
			} else {
				wanted = Mode.FOLLOW;
			}
		}
		if (wanted != f.mode) {
			if (wanted == Mode.AWAY) {
				f.awaySince = now;
			}
			f.mode = wanted;
			f.spot = null;
			f.faceAt = null;
		}
		switch (f.mode) {
			case WINDOW -> window(f, level, at, base.orElseThrow(), now);
			case MOUTH -> {
				if (mob != null && !held) {
					mouth(f, level, mob);
				}
			}
			case AWAY -> away(f, level, now, held ? null : mob);
			case WAITING -> {
			}
			default -> {
				if (mob != null && !held) {
					follow(f, level, mob, at, Sc24Config.get());
				}
			}
		}
		if (follow == f && !f.staged && f.mob != null && MobTamperImpl.INSTANCE.isTampered(f.mob)) {
			MobTamperImpl.INSTANCE.bind(f.mob, List.of(f.target.id()));
		}
	}

	/** Following: back along the way, catching up out of view, backing off, facing them while they look. */
	private void follow(Follow f, ServerLevel level, Mob mob, Vec3 at, Sc24Config cfg) {
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		double dist = Math.sqrt(Herds.flatSqr(mob.position(), at));
		double keep = cfg.followMin + (cfg.followMax - cfg.followMin) * KEEP_FRACTION;
		if (dist > cfg.catchUpAt && outOfView.test(level, mob.getBoundingBox())) {
			Optional<Vec3> back = f.trail.back(at, keep);
			Vec3 dest = back.orElse(at.subtract(flat(f.target.look()).scale(keep)));
			if (moveTo(f, level, mob, dest)) {
				return;
			}
		}
		if (dist < cfg.nearest) {
			Vec3 away = flat(mob.position().subtract(at));
			if (away.lengthSqr() < 1.0E-6) {
				away = flat(f.target.look()).scale(-1.0);
			}
			Vec3 to = at.add(away.normalize().scale(cfg.followMin));
			steer(f, mob, to, 1.1);
			return;
		}
		// Back along their way; while their way is still shorter than that, the same distance off on its own side.
		Vec3 to;
		if (f.trail.length(at) >= keep) {
			to = f.trail.back(at, keep).orElse(mob.position());
		} else {
			Vec3 side = flat(mob.position().subtract(at));
			to = side.lengthSqr() < 1.0E-6 ? mob.position() : at.add(side.normalize().scale(Math.min(keep, Math.sqrt(side.lengthSqr()))));
		}
		f.spot = to;
		boolean seen = f.target.sees(mob.getBoundingBox());
		boolean standing = Herds.flatSqr(mob.position(), to) <= 2.25;
		if (seen && standing) {
			tamper.freeze(mob, HOLD);
			tamper.face(mob, f.target.eye(), HOLD);
			return;
		}
		double speed = dist > cfg.followMax ? 1.15 : dist > keep ? 1.0 : 0.8;
		steer(f, mob, to, speed);
		if (seen) {
			tamper.face(mob, f.target.eye(), HOLD);
		}
	}

	/** Underground: it waits a few blocks outside the mouth they went in by, facing in. */
	private void mouth(Follow f, ServerLevel level, Mob mob) {
		Optional<Trail.Point[]> mouth = f.trail.mouth();
		if (mouth.isEmpty()) {
			return;
		}
		Vec3 out = mouth.get()[0].pos();
		Vec3 in = mouth.get()[1].pos();
		if (f.spot == null) {
			Vec3 dir = flat(out.subtract(in));
			dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : dir.normalize();
			f.spot = ground(level, out.add(dir.scale(3.0))).orElse(out);
			f.faceAt = in.add(0, 1.0, 0);
		}
		hold(f, level, mob);
	}

	/**
	 * Away (R10-10): it walks off from the player and, once out of everyone's view, goes back where it was taken when it was
	 * their own (the ledger's undo: back in their field), else is moved {@link #GONE} off; then it is let go and waits for the
	 * night. After a while trying, it is let go where it is.
	 */
	private void away(Follow f, ServerLevel level, long now, @Nullable Mob mob) {
		if (mob == null) {
			f.mode = Mode.WAITING; // unloaded or taken: nothing to walk; at the window another of theirs may come
			return;
		}
		Vec3 from = f.lastSeenAt;
		Vec3 off = flat(mob.position().subtract(from));
		off = off.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : off.normalize();
		if (outOfView.test(level, mob.getBoundingBox())) {
			MobTamperImpl.INSTANCE.release(mob);
			TraceService.UndoResult home = f.fromBase && f.entry != null ? traces().undo(level, f.entry) : null;
			// DONE: back where it stood. BLOCKED: given back already (the switch) or someone's now (a name, a lead): theirs.
			if (home == TraceService.UndoResult.DONE || home == TraceService.UndoResult.BLOCKED) {
				f.taken.remove(f.entry);
				f.entry = null;
				f.mode = Mode.WAITING;
				return;
			}
			if (!f.fromBase && moveTo(f, level, mob, from.add(off.scale(GONE + 10.0)))) {
				MobTamperImpl.INSTANCE.release(mob);
				f.mode = Mode.WAITING;
				return;
			}
			if (f.fromBase && f.entry == null) {
				f.mode = Mode.WAITING; // never moved: it is where it was
				return;
			}
		}
		if (now - f.awaySince >= AWAY_TRIES_TICKS) {
			MobTamperImpl.INSTANCE.release(mob);
			f.mode = Mode.WAITING;
			return;
		}
		steer(f, mob, mob.position().add(off.scale(12.0)), 1.0);
	}

	/**
	 * At home at night (R10-10, R11-14): their own animal (the follower when it can be found, else another of theirs within
	 * {@link #BASE_NEAR} of their base) stands {@link #WINDOW_NEAR} to {@link #WINDOW_FAR} out from their nearest window or
	 * door, on its axis, frozen, facing in.
	 */
	private void window(Follow f, ServerLevel level, Vec3 at, BlockPos base, long now) {
		Mob mob = resolve(f, level);
		if (mob == null || mob.isLeashed() || mob.isPassenger() || mob.isVehicle()) {
			mob = ownAnimal(f, level, base);
			if (mob == null) {
				return;
			}
		}
		if (!f.staged && f.entry == null) {
			f.origin = mob.blockPosition().immutable();
			taken(f, level, mob); // taken to the window from where it stands: back there at dawn
		}
		if (f.spot == null) {
			Optional<Vec3[]> found = now >= f.windowRetryAt ? windowSpot(level, BlockPos.containing(at), base) : Optional.empty();
			if (found.isEmpty()) {
				f.windowRetryAt = Math.max(f.windowRetryAt, now + WINDOW_RETRY);
				return;
			}
			f.spot = found.get()[0];
			f.faceAt = found.get()[1];
		}
		hold(f, level, mob);
	}

	/** Another of the player's own animals (a free one of 24's kinds within {@link #BASE_NEAR} of their base, loaded), now the animal of this follow. */
	private @Nullable Mob ownAnimal(Follow f, ServerLevel level, BlockPos base) {
		Vec3 home = Vec3.atBottomCenterOf(base);
		List<Mob> own = level.getEntitiesOfClass(Mob.class, new AABB(home, home).inflate(BASE_NEAR, 16.0, BASE_NEAR),
				m -> Herds.FOLLOWER_KINDS.contains(m.getType()) && Herds.free(m) && Herds.flatSqr(m.position(), home) <= BASE_NEAR * BASE_NEAR);
		Mob best = FollowerCard.mostRecognisable(own, home);
		if (best == null) {
			return null;
		}
		f.mob = best;
		f.animal = best.getUUID();
		f.origin = best.blockPosition().immutable();
		f.fromBase = true;
		f.entry = null;
		if (!f.staged) {
			taken(f, level, best);
		}
		return best;
	}

	/** Ledgers the animal as taken from where it stands now (R13-7), once: the end, the switch and Ending D put it back there. */
	private static void taken(Follow f, ServerLevel level, Mob mob) {
		f.entry = TraceLedger.recordMobMove(level, mob, f.origin, mob.blockPosition(), CAUSE);
		f.taken.add(f.entry);
	}

	/** Walks to {@code f.spot} (moved there out of view when far) and stands frozen, facing {@code f.faceAt}. */
	private void hold(Follow f, ServerLevel level, Mob mob) {
		Vec3 spot = f.spot;
		if (spot == null) {
			return;
		}
		double d2 = Herds.flatSqr(mob.position(), spot);
		if (d2 > 24 * 24 && outOfView.test(level, mob.getBoundingBox()) && moveTo(f, level, mob, spot)) {
			d2 = Herds.flatSqr(mob.position(), spot);
		}
		if (d2 <= 2.25) {
			MobTamperImpl.INSTANCE.freeze(mob, HOLD);
			if (f.faceAt != null) {
				MobTamperImpl.INSTANCE.face(mob, f.faceAt, HOLD);
			}
		} else {
			steer(f, mob, spot, 1.0);
		}
	}

	/**
	 * The nearest window or door (glass, a glass pane, a door's lower half) within 10 of {@code at} with open sky on one
	 * side: a cell {@link #WINDOW_NEAR} to {@link #WINDOW_FAR} out on its axis on that side where an animal can stand (a
	 * window first: a face in the pane), and the block itself to face. Empty if none.
	 */
	static Optional<Vec3[]> windowSpot(ServerLevel level, BlockPos at, BlockPos base) {
		Vec3[] best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -10; dx <= 10; dx++) {
			for (int dz = -10; dz <= 10; dz++) {
				for (int dy = -3; dy <= 4; dy++) {
					pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					BlockState state = level.getBlockState(pos);
					if (!opening(state)) {
						continue;
					}
					// A window before a door: the door only when it is much nearer.
					double d = pos.distSqr(at) + (state.getBlock() instanceof DoorBlock ? 64.0 : 0.0);
					if (d >= bestD) {
						continue;
					}
					for (Direction side : Direction.Plane.HORIZONTAL) {
						BlockPos outside = pos.relative(side);
						if (!Herds.free(level, outside) || Herds.skyAt(level, outside) < Herds.OUTDOORS_SKY) {
							continue;
						}
						for (int k = WINDOW_NEAR; k <= WINDOW_FAR; k++) {
							BlockPos c = pos.relative(side, k);
							OptionalInt feet = Herds.feet(level, c.getX(), c.getZ(), pos.getY(), 3, 4);
							if (feet.isPresent() && Herds.skyAt(level, new BlockPos(c.getX(), feet.getAsInt(), c.getZ())) >= Herds.OUTDOORS_SKY) {
								best = new Vec3[] {new Vec3(c.getX() + 0.5, feet.getAsInt(), c.getZ() + 0.5), Vec3.atCenterOf(pos)};
								bestD = d;
								break;
							}
						}
						if (best != null && bestD == d) {
							break;
						}
					}
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** A door's lower half, glass or a glass pane (any colour). */
	static boolean opening(BlockState state) {
		if (state.getBlock() instanceof DoorBlock) {
			return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
		}
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.equals("glass") || path.equals("glass_pane") || path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane");
	}

	/**
	 * The end (at dawn, its time over, they left, the switch): every animal it took from their home goes back there out of
	 * view (the ledger's undo); one that was not theirs is moved off out of view; then all are let go. Retried until
	 * {@link #END_HARD_TICKS}, then whatever is left is let go where it is (R12-35).
	 */
	private void ending(Follow f, ServerLevel level, long now, @Nullable Mob mob) {
		if (mob != null) {
			MobTamperImpl.INSTANCE.release(mob);
		}
		boolean left = false;
		for (TraceLedger.Entry entry : List.copyOf(f.taken)) {
			if (!f.staged && isHome(f, entry)) {
				TraceService.UndoResult result = traces().undo(level, entry);
				if (result == TraceService.UndoResult.DONE || result == TraceService.UndoResult.BLOCKED) {
					f.taken.remove(entry);
				} else {
					left = true;
				}
			} else {
				f.taken.remove(entry);
			}
		}
		if (mob != null && !f.fromBase && Herds.flatSqr(mob.position(), f.lastSeenAt) < GONE * GONE && outOfView.test(level, mob.getBoundingBox())) {
			Vec3 off = flat(mob.position().subtract(f.lastSeenAt));
			off = off.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : off.normalize();
			if (!moveTo(f, level, mob, f.lastSeenAt.add(off.scale(GONE + 10.0))) && now - f.endingSince < END_HARD_TICKS) {
				left = true;
			}
			MobTamperImpl.INSTANCE.release(mob);
		}
		if (!left || now - f.endingSince >= END_HARD_TICKS) {
			stop(f, left ? "its time is over (watched)" : "its time is over");
		}
	}

	/** This entry took an animal from the player's home. */
	private static boolean isHome(Follow f, TraceLedger.Entry entry) {
		return f.target.base().map(b -> Herds.flatSqr(Vec3.atBottomCenterOf(entry.pos().pos()), Vec3.atBottomCenterOf(b)) <= BASE_NEAR * BASE_NEAR)
				.orElse(f.fromBase);
	}

	private void steer(Follow f, Mob mob, Vec3 to, double speed) {
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		if (tamper.isFrozen(mob)) {
			// Its own stand (a freeze wins over a walk): let it go first, then walk it.
			tamper.release(mob);
		}
		if (!tamper.steer(mob, to, speed, HOLD)) {
			f.mode = f.mode == Mode.FOLLOW || f.mode == Mode.MOUTH ? Mode.AWAY : f.mode;
			f.awaySince = mob.level().getServer().getTickCount();
		}
	}

	/**
	 * Moves the mob onto the ground at (or near) {@code dest}, out of everyone's view. Its first move is ledgered (R13-7,
	 * {@link #CAUSE}: from where it stood when it was taken), so the switch and Ending D put it back there.
	 */
	private boolean moveTo(Follow f, ServerLevel level, Mob mob, Vec3 dest) {
		if (!moveTo(level, mob, dest)) {
			return false;
		}
		if (!f.staged && f.entry == null && mob.getUUID().equals(f.animal)) {
			taken(f, level, mob);
		}
		return true;
	}

	private boolean moveTo(ServerLevel level, Mob mob, Vec3 dest) {
		Optional<Vec3> ground = ground(level, dest);
		if (ground.isEmpty()) {
			return false;
		}
		Vec3 g = ground.get();
		BlockPos cell = BlockPos.containing(g);
		AABB destBox = mob.getDimensions(mob.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(cell));
		if (!outOfView.test(level, mob.getBoundingBox()) || !outOfView.test(level, destBox)) {
			return false;
		}
		MobTamperImpl.INSTANCE.release(mob);
		return Services.mobs().moveOutOfView(mob, cell) || moveForTests(level, mob, cell);
	}

	/** {@code MobTamper.moveOutOfView} asks the live view; a test with its own view rule moves it itself (the same checks passed above). */
	private boolean moveForTests(ServerLevel level, Mob mob, BlockPos cell) {
		if (outOfView == LIVE_VIEW) {
			return false;
		}
		Vec3 dest = Vec3.atBottomCenterOf(cell);
		if (!level.noCollision(mob, mob.getDimensions(mob.getPose()).makeBoundingBox(dest))) {
			return false;
		}
		mob.getNavigation().stop();
		mob.teleportTo(dest.x, dest.y, dest.z);
		mob.setDeltaMovement(Vec3.ZERO);
		mob.setOldPosAndRot();
		return true;
	}

	/** The ground near {@code at}: a cell an animal can stand in, within 4 up or 6 down; empty if none or not loaded. */
	static Optional<Vec3> ground(ServerLevel level, Vec3 at) {
		int x = (int) Math.floor(at.x);
		int z = (int) Math.floor(at.z);
		OptionalInt feet = Herds.feet(level, x, z, (int) Math.floor(at.y), 4, 6);
		if (feet.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new Vec3(x + 0.5, feet.getAsInt(), z + 0.5));
	}

	private static Vec3 flat(Vec3 v) {
		return new Vec3(v.x, 0, v.z);
	}

	/** Ends the follow: the animal let go where it is. */
	void stop(Follow f, String why) {
		if (f.mob != null) {
			MobTamperImpl.INSTANCE.release(f.mob);
		}
		f.mode = Mode.DONE;
		f.why = why;
		if (follow == f) {
			follow = null;
		}
		A1016_02.LOGGER.debug("[a1016] sc_24: ended ({})", why);
	}

	/** The end now (the switch, a stop): let go where it is (the switch's give-back puts what it took back). */
	public void clear() {
		if (follow != null) {
			stop(follow, "cleared");
		}
		follow = null;
	}

	/** Lines for {@code /a1016 sc 24}. */
	public List<String> describe() {
		List<String> lines = new ArrayList<>();
		Follow f = follow;
		if (f == null) {
			lines.add("none running");
		} else {
			Mob mob = f.mob;
			lines.add(String.format(Locale.ROOT, "running: %s at %.0f %.0f %.0f, %s, %.0f off, %d points of way%s",
					mob == null ? "-" : mob.getType().getDescription().getString(), mob == null ? 0.0 : mob.getX(), mob == null ? 0.0 : mob.getY(),
					mob == null ? 0.0 : mob.getZ(), f.mode.name().toLowerCase(Locale.ROOT), mob == null ? 0.0 : Math.sqrt(Herds.flatSqr(mob.position(), f.lastSeenAt)),
					f.trail.size(), f.fromBase ? ", theirs" : ""));
		}
		return lines;
	}
}
