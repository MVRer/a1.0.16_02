package com.forzacode.a1016_02.accident.sc2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.AccidentData;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.accident.ViewGate;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * One running scene of the second wave's trap kinds (docs/SELF_CREATED_2.md, 25 and 26): the instruments of one armed
 * trap, moved around its player by {@link MobTamper} and looked at every {@link Hunts#STEP} ticks. What both kinds
 * share lives here: the player looked up again each step (D-062.1), the trap it belongs to (it ends when that trap is
 * gone: disarmed, spent on a death, or over), the tells it played, when the target first saw an instrument, when it
 * first turned hostile, the trap marked set at that moment (so a death from then on is claimed at once) and run out at
 * the end, and letting its mobs go. A scene for the gallery has a fixed stand-in instead of a player and no trap.
 */
public abstract class HuntScene {
	/** A tell the scene played: when (server tick), what, where, how loud. */
	public record Tell(long tick, String what, Vec3 at, float volume) {
		/** Heard at 16 blocks or more. */
		public boolean audible() {
			return volume >= Hunts.AUDIBLE;
		}
	}

	/**
	 * What a scene is tied to: who takes part (and each player's base), the planner's saved data the trap lives in (null:
	 * the gallery, no trap), the view gate everything is hidden from, and the play clock the trap's window runs on.
	 */
	public record Links(PlayerWatch watch, @Nullable AccidentData data, ViewGate view, LongSupplier play) {
		/** The links of a trap armed through the planner, from its context. */
		public static Links of(TrapContext ctx) {
			return new Links(ctx.watch(), ctx.data(), ctx.view(), () -> GameClock.playTicks(ctx.level().getServer()));
		}

		/** The gallery's: no trap, everyone's view. */
		public static Links gallery() {
			return new Links(Services.watch(), null, ViewGate.TRACES, () -> 0L);
		}
	}

	private record Pending(long tick, Runnable run) {
	}

	protected final String kind;
	protected final UUID target;
	protected final ServerLevel level;
	protected final Links links;
	/** The trap's {@code armedAt} (play ticks), or -1 for the gallery. */
	protected final long armedAt;
	/** Server tick it began. */
	protected final long start;
	protected final @Nullable Prey standIn;
	/** Every instrument (the trap blames each). */
	protected final List<UUID> mobs;
	/** Instruments let go: released to vanilla, or loose (a friend's hit). Never steered again. */
	protected final Set<UUID> let = new HashSet<>();
	protected final List<Tell> tells = new ArrayList<>();
	/** The moments (their first sound's tick) of the long tells the target heard. */
	protected final Set<Long> heard = new java.util.TreeSet<>();
	private final List<Pending> pending = new ArrayList<>();
	protected long hostileAt = -1;
	protected long seenAt = -1;
	/** Server tick an instrument was first plainly seen ({@link Hunts#plain}: the middle of the view, within 20), or -1. */
	protected long plainAt = -1;
	/** The instrument plainly seen last, or null. */
	protected @Nullable UUID plainBy;
	protected long lastSeen;
	protected long nextStep;
	protected int dead;
	protected boolean done;
	/** Server tick at which it is stopped whatever it is doing (the gallery's staging), or never. */
	protected long until = Long.MAX_VALUE;
	protected String why = "";
	protected Vec3 where;
	/** A forced debug arm's scene: its target's base is relaxed at the turn too, as the arm relaxed it (never the rest). */
	protected boolean baseRelaxed;
	/** R10-4: the place a trespass called it from (its first tell comes from there), or null. */
	protected @Nullable Vec3 calledFrom;

	protected HuntScene(String kind, UUID target, ServerLevel level, Links links, long armedAt, Vec3 where, List<UUID> mobs, @Nullable Prey standIn) {
		this.kind = kind;
		this.target = target;
		this.level = level;
		this.links = links;
		this.armedAt = armedAt;
		this.start = level.getServer().getTickCount();
		this.lastSeen = start;
		this.nextStep = start;
		this.where = where;
		this.mobs = List.copyOf(mobs);
		this.standIn = standIn;
	}

	// --- what Hunts asks ---

	public String kind() {
		return kind;
	}

	public UUID target() {
		return target;
	}

	public ServerLevel level() {
		return level;
	}

	/** Where its target was last seen (feet). */
	public Vec3 where() {
		return where;
	}

	public long nextStep() {
		return nextStep;
	}

	public boolean done() {
		return done;
	}

	public long startedAt() {
		return start;
	}

	/** Server tick of its first hostile act, or -1. */
	public long hostileAt() {
		return hostileAt;
	}

	/** Server tick the target first saw one of its instruments, or -1. */
	public long seenAt() {
		return seenAt;
	}

	/** Server tick the target first plainly saw one ({@link Hunts#plain}: the middle of their view, within 20), or -1. */
	public long plainAt() {
		return plainAt;
	}

	public List<Tell> tells() {
		return List.copyOf(tells);
	}

	/** Tells heard at 16 or more that came before {@code tick}. */
	public int audibleBefore(long tick) {
		return (int) tells.stream().filter(t -> t.audible() && t.tick() < tick).map(Tell::tick).distinct().count();
	}

	public List<UUID> mobs() {
		return mobs;
	}

	/** Why it ended (empty while running). */
	public String why() {
		return why;
	}

	/** The scene of a forced debug arm relaxes its target's base as the arm did (never the health, the way out or the tells). */
	public HuntScene relaxBase(boolean on) {
		baseRelaxed = on;
		return this;
	}

	/**
	 * R10-4: a trespass called it ({@link Hunts#calledFrom}): its first tell comes from the bearing of that place (set before
	 * {@code begin}; null: none).
	 */
	public HuntScene calledFrom(@Nullable Vec3 place) {
		calledFrom = place;
		return this;
	}

	/** The place a trespass called it from, or null. */
	public @Nullable Vec3 calledFrom() {
		return calledFrom;
	}

	/** Stops it, whatever it is doing, {@code ticks} from now (the gallery's staging: it never outlives its frames). */
	public void lifetime(long ticks) {
		until = now() + ticks;
	}

	/** A short word for where it stands now (debug). */
	public abstract String phase();

	/** Debug lines for {@code /a1016 sc NN}. */
	public abstract List<String> describe(long now);

	/** One step: the player looked up again, the trap checked, then the kind's own step. */
	public final void step(long now) {
		nextStep = now + Hunts.STEP;
		if (done) {
			return;
		}
		if (!SelfCreated.enabled()) {
			stop(true, "switched off");
			return;
		}
		if (now >= until) {
			stop(true, "staged");
			return;
		}
		Prey prey = prey();
		if (prey != null) {
			where = prey.feet();
		}
		act(now, prey, trapHolds());
	}

	/** Every server tick: sounds waiting for their moment. */
	public final void flush(long now) {
		for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
			Pending p = it.next();
			if (now >= p.tick()) {
				it.remove();
				p.run().run();
			}
		}
	}

	/**
	 * The kind's step, with its target as they are now (null: gone, dead, out of the level or not a participant any more)
	 * and whether its trap still holds (false once it is disarmed or spent; always true for the gallery).
	 */
	protected abstract void act(long now, @Nullable Prey prey, boolean trapHolds);

	/** Ends it: {@code now} lets every mob go where it is at once (the switch, a stop), else the kind's own way out. */
	public abstract void stop(boolean now, String why);

	/** A mob died. */
	public void died(UUID id) {
		if (mobs.contains(id)) {
			dead++;
			let.add(id);
		}
	}

	// --- helpers ---

	/** The target as they are now, or the gallery's stand-in. */
	protected @Nullable Prey prey() {
		if (standIn != null) {
			return standIn;
		}
		Optional<ServerPlayer> player = links.watch().participant(level.getServer(), target).filter(links.watch()::isTargetable);
		return player.filter(p -> p.level() == level).map(Prey::of).orElse(null);
	}

	/** The trap this scene belongs to, while it is still armed for its target (never for the gallery). */
	protected Optional<ArmedTrap> trap() {
		AccidentData data = links.data();
		if (data == null) {
			return Optional.empty();
		}
		return data.armed(target).filter(t -> t.type().equals(kind) && t.armedAt() == armedAt);
	}

	/** The trap is still armed for its target (always, for the gallery). */
	protected boolean trapHolds() {
		return links.data() == null || trap().isPresent();
	}

	/** Its trap is set from now on for {@code span} ticks of play: a death by its instruments counts at once. */
	protected void setTrap(long span) {
		AccidentData data = links.data();
		trap().ifPresent(trap -> {
			long play = links.play().getAsLong();
			data.setArmed(target, trap.isSet() ? trap.withPhase(trap.phase(), Math.max(trap.until(), play + span)) : trap.set(play, span, List.of()));
			if (!trap.isSet()) {
				A1016_02.LOGGER.info("[a1016] accident: {} ({}) set", kind, target.toString().substring(0, 8));
			}
		});
	}

	/** Its trap runs out now (the planner drops it at its next step) or, with {@code grace}, that many ticks of play later. */
	protected void endTrap(long grace) {
		AccidentData data = links.data();
		trap().ifPresent(trap -> {
			long until = links.play().getAsLong() + grace - 1;
			if (until < trap.until()) {
				data.setArmed(target, trap.withPhase(trap.phase(), until));
			}
		});
	}

	/**
	 * Plays a tell at {@code at} for everyone in range, now or {@code delay} ticks later, and remembers it. A tell of a
	 * {@code group} (0 or more: one moment, however many sounds it has) counts as heard by the target if they were within
	 * vanilla's hearing range of any of its sounds when it played ({@link #heardBefore}); -1 never counts.
	 */
	protected void tell(String what, Holder<SoundEvent> sound, SoundSource source, Vec3 at, float volume, float pitch, int delay, long group) {
		long when = level.getServer().getTickCount() + Math.max(0, delay);
		Runnable play = () -> {
			SoundCues.playAt(level, sound, source, at, volume, pitch);
			tells.add(new Tell(level.getServer().getTickCount(), what, at, volume));
			if (group >= 0 && volume >= Hunts.AUDIBLE) {
				Prey prey = prey();
				double range = Math.max(volume, 1.0F) * 16.0;
				if (prey != null && prey.feet().distanceToSqr(at) < range * range) {
					heard.add(group);
				}
			}
		};
		if (delay <= 0) {
			play.run();
		} else {
			pending.add(new Pending(when, play));
		}
	}

	/** The long tells the target heard (each moment once, at 16 or more), that began before {@code tick}. */
	public int heardBefore(long tick) {
		return (int) heard.stream().filter(t -> t < tick).count();
	}

	/**
	 * Why it may not turn on its target now though it was told ({@link Hunts#turnRefusal}, R12-8): the start's floors asked
	 * again with the kind's own way out; null when it may.
	 */
	protected @Nullable String turnHeld(Prey prey, Predicate<ServerPlayer> wayOut) {
		return Hunts.turnRefusal(prey, links.watch(), wayOut, hostileAt < 0, baseRelaxed);
	}

	/**
	 * Told (D-097.1, R10-5): at least {@link Hunts#TOLD_TICKS} since the start, two long tells heard, an instrument seen (as
	 * the target perceives it, {@link Hunts#perceive}) and one plainly seen ({@link Hunts#plain}).
	 */
	public boolean told(long now) {
		return now - start >= Hunts.TOLD_TICKS && heardBefore(now + 1) >= 2 && seenAt >= 0 && plainAt >= 0;
	}

	/**
	 * Notes whether the target sees any of these instruments now: seen within what they perceive of each
	 * ({@link Hunts#perceive}, at most {@code cap}), plainly seen through the middle of their view within 20
	 * ({@link Hunts#plain}).
	 */
	protected void look(Prey prey, List<? extends Mob> active, double cap, long now) {
		boolean seenNow = false;
		boolean fog = Hunts.fog(level);
		double end = Hunts.fogEnd(prey);
		for (Mob mob : active) {
			if (prey.eye().distanceTo(mob.position()) > cap + 2.0) {
				continue;
			}
			double perceived = Hunts.sightFor(fog, Hunts.dim(mob), end, cap);
			if (!Hunts.seen(prey, mob, perceived)) {
				continue;
			}
			seenNow = true;
			if (Hunts.plain(prey, mob, perceived)) {
				plainBy = mob.getUUID();
				if (plainAt < 0) {
					plainAt = now;
				}
				break;
			}
		}
		if (seenNow) {
			lastSeen = now;
			if (seenAt < 0) {
				seenAt = now;
			}
		}
	}

	/** The instruments still with it: loaded, alive, not let go. */
	protected <T extends Mob> List<T> active(Class<T> type) {
		List<T> found = new ArrayList<>();
		for (T mob : Hunts.loaded(level, mobs, type)) {
			if (!let.contains(mob.getUUID())) {
				found.add(mob);
			}
		}
		return found;
	}

	/** Lets this mob go: calmed (nothing it was set on lingers) and released to its own AI. */
	protected void letGo(Mob mob) {
		MobTamper tamper = Services.mobs();
		tamper.calm(mob);
		tamper.release(mob);
		let.add(mob.getUUID());
	}

	/** Lets every mob of it go where it is, at once. */
	protected void letAllGo() {
		for (Mob mob : Hunts.loaded(level, mobs, Mob.class)) {
			if (!let.contains(mob.getUUID())) {
				letGo(mob);
			}
		}
		let.addAll(mobs);
	}

	/** Finished: let go by {@link Hunts}. */
	protected void finish(String reason) {
		if (!done) {
			done = true;
			if (why.isEmpty()) {
				why = reason;
			}
			pending.clear();
		}
	}

	protected long now() {
		return level.getServer().getTickCount();
	}
}
