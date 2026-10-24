package com.forzacode.a1016_02.accident.sc2.s26;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.forzacode.a1016_02.accident.sc2.HuntScene;
import com.forzacode.a1016_02.accident.sc2.Hunts;
import com.forzacode.a1016_02.accident.sc2.Prey;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Feature 26's scene (docs/SELF_CREATED_2.md, 26; D-096, D-097): the instruments of one armed {@code sc26} trap, the
 * monsters near its player on a fog night, looked at every {@link Hunts#STEP} ticks.
 * <ol>
 * <li>At most {@link #HARD_MAX} of them, whatever the config says (R10-19: never a horde).</li>
 * <li><b>Quiet</b> ({@code d} seconds): every one silenced, the player's (and anyone sharing the moment's) ambient sound
 * dropped ({@code ClientEffects.silenceShared}); each walks (its own walk speed) to the ring it waits on,
 * {@link #HOLD} to 24 from the player along the way it came, and faces them there; a spider never stands on the part of
 * the ring in front of them ({@link #SPIDER_CLEAR}, R11-15).</li>
 * <li><b>Loud</b>: at the end of the quiet, every one's own voice at once (volume 2: heard to 32), again 40 s in. From 25
 * s until one was plainly seen, one of them (never a spider) walks into the middle of the player's view, 14 to 16 out,
 * and stands there facing them (R10-12).</li>
 * <li><b>Close</b>: from 60 s on ({@link Hunts#TOLD_TICKS}), after at least {@link #RING_WAIT} on the ring, only if told
 * (two of those heard, one seen, one plainly seen; up to 30 s more,
 * else they go), each walks to the player, refreshed every step, and is let go to its own targeting
 * {@code e} from them (vanilla then: it may take whoever is nearest). The trap is set at that moment. Never more than
 * {@code i} of the walking dead toward one door the player stands by; the others wait on the ring.</li>
 * <li><b>Going</b>: {@code f} seconds after the start, at dawn, the player gone or the trap over, the player within 64
 * of their base at any point, or under a roof at the turn or after (home is safe, D-097.6; R12-8), or still held at the
 * turn after its 30 s (under 10 health, no pickaxe or shovel any more, fighting): the ones still walked are walked 40
 * away and let go out of view (at most a minute); the trap stays set a minute more for the ones already let go, and on
 * while one of them is still after a player ({@code CallTrap.tick}). The switch going off lets them all go at once.</li>
 * </ol>
 * Never a creeper or a witch (a silent creeper would be an unmarked death); never anything set on anyone.
 */
public final class Call extends HuntScene {
	public static final String KIND = "sc26";

	/** Where it stands. */
	public enum Phase { QUIET, LOUD, CLOSE, LEAVE }

	/** The ring they wait on (blocks from the player): 20, 22 or 24 by their place. */
	public static final double HOLD = 20.0;
	/**
	 * R10-19: never more instruments than this, whatever the config says ({@code c} is read as at most this): a few, silent
	 * first, standing on a ring; never a horde.
	 */
	public static final int HARD_MAX = 6;
	/** Their voices' volume (heard to 32). */
	static final float CHORUS = 2.0F;
	/** The second chorus, this long after the start. */
	static final int SECOND_TELL = 800;
	/**
	 * R10-12: from this long after the start (their voices came at 20 s) until one was plainly seen, and whenever none was
	 * seen for {@link #SHOW_AGAIN}, one of them (never a spider, R11-15) walks into the middle of the player's view
	 * {@link #SHOW_NEAR} to {@link #SHOW_FAR} out and stands there facing them; at most {@link #SHOW_FOR}, and
	 * {@link #SHOW_HOLD} once plainly seen, then it goes back to the ring.
	 */
	static final int SHOW_FROM = 500;
	static final int SHOW_AGAIN = 600;
	static final int SHOW_FOR = 300;
	static final int SHOW_HOLD = 60;
	public static final double SHOW_NEAR = 14.0;
	public static final double SHOW_FAR = 16.0;
	static final double SEEN = 32.0;
	/** R10-19: they stand on the ring at least this long (ticks) before they come in. */
	public static final int RING_WAIT = 400;
	/** On the ring: within this of its place there (blocks). */
	static final double ON_RING = 3.0;
	/**
	 * R11-15: a spider never holds on the ring inside this much of the player's view (degrees either side of their look):
	 * its red eyes are the cliché the layer leaves out. It waits behind them and comes in from there.
	 */
	public static final double SPIDER_CLEAR = 70.0;
	/** Not told at 60 s: up to this long more, then they go. */
	static final int EXTEND = 600;
	static final double LEAVE_TO = 40.0;
	static final double LEAVE_FAR = 32.0;
	static final int LEAVE_MAX = 1200;
	static final int STEER_TICKS = 40;
	/** Deaths by the ones let go still count this long after the end (ticks of play). */
	static final int GRACE = 1200;
	/** A door this near the player is "their door" for the cap. */
	static final int DOOR_NEAR = 4;
	/** One let go this near the player still counts toward the door's cap. */
	static final double DOOR_HELD = 12.0;

	private final Sc26Config cfg;
	private final BooleanSupplier dark;
	private final RandomSource random;
	private Phase phase = Phase.QUIET;
	private final long loudAt;
	private final long closeAt;
	private final long endAt;
	private boolean second;
	private long leaveAt = -1;
	private final Map<UUID, Double> angles = new HashMap<>();
	private final List<UUID> released = new ArrayList<>();
	private @Nullable UUID shown;
	private Vec3 shownAt = Vec3.ZERO;
	private long shownUntil;
	/** When every one stood on the ring (or the quiet was long over), server tick, or -1. */
	private long ringAt = -1;
	/** When the one walked into view stood at its spot, 14 to 16 out, plainly seen (R10-12), server tick, or -1. */
	private long shownThere = -1;

	/**
	 * Told (D-097.1, R10-12): the shared rule, and the one walked into the middle of the view stood there, 14 to 16 out, plainly
	 * seen (one on the ring 20 off, seen in passing, is not enough).
	 */
	@Override
	public boolean told(long now) {
		return super.told(now) && shownThere >= 0;
	}

	/** Server tick the one walked into view stood at its spot plainly seen, or -1. */
	public long shownThere() {
		return shownThere;
	}

	/**
	 * A scene for {@code target} (or the gallery's {@code standIn}) with these instruments, the first {@link #HARD_MAX} of
	 * them (the trap gives them nearest first).
	 */
	public Call(UUID target, ServerLevel level, Links links, long armedAt, Prey prey, List<? extends Mob> callees, Sc26Config cfg, BooleanSupplier dark,
			@Nullable Prey standIn) {
		super(KIND, target, level, links, armedAt, prey.feet(), callees.stream().limit(HARD_MAX).map(Mob::getUUID).toList(), standIn);
		this.cfg = cfg;
		this.dark = dark;
		this.random = level.getRandom();
		this.loudAt = start + Math.round(Math.max(0.0, cfg.silenceSeconds) * 20.0);
		this.closeAt = start + Math.max(Hunts.TOLD_TICKS, loudAt - start);
		this.endAt = Math.max(closeAt + 200, start + Math.round(cfg.secondsMax * 20.0));
		for (Mob mob : callees) {
			angles.put(mob.getUUID(), Hunts.angle(prey.feet(), mob.position()));
		}
	}

	/**
	 * The start: everyone silenced (the mobs, and the player's ambient sound); the first step comes with the next server
	 * tick (the planner stores the trap after {@code onArmed} returns, and a step checks that it holds).
	 */
	public void begin() {
		int quiet = (int) (loudAt - start);
		MobTamper tamper = Services.mobs();
		for (Mob mob : active(Mob.class)) {
			if (quiet > 0) {
				tamper.silence(mob, quiet);
			}
		}
		Prey prey = prey();
		ServerPlayer player = prey == null ? null : prey.player();
		if (quiet > 0 && player != null && player.connection != null) {
			ClientEffects.silenceShared(player, quiet, 40);
		}
	}

	public Phase phaseNow() {
		return phase;
	}

	@Override
	public String phase() {
		return phase.name().toLowerCase(Locale.ROOT);
	}

	/** The ones let go to their own targeting so far. */
	public List<UUID> released() {
		return List.copyOf(released);
	}

	public long closeAt() {
		return closeAt;
	}

	@Override
	protected void act(long now, @Nullable Prey prey, boolean trapHolds) {
		List<Mob> callees = active(Mob.class);
		if (phase == Phase.LEAVE) {
			leaving(now, prey, callees);
			return;
		}
		if (prey == null) {
			leave(now, null, callees, "gone");
			return;
		}
		if (!trapHolds) {
			leave(now, prey, callees, "over");
			return;
		}
		if (!dark.getAsBoolean()) {
			leave(now, prey, callees, "dawn");
			return;
		}
		if (now >= endAt) {
			leave(now, prey, callees, "time");
			return;
		}
		// Home is safe (D-097.6, R12-8): nothing of it walks to a player within 64 of their base, at any point.
		if (!baseRelaxed && prey.player() != null && Hunts.baseDistance(links.watch(), prey.player()) < Hunts.FROM_BASE) {
			leave(now, prey, callees, "home");
			return;
		}
		if (phase == Phase.CLOSE) {
			if (prey.player() != null && Hunts.covered(prey.player())) {
				leave(now, prey, callees, "sheltered"); // nothing hunts inside a house (D-097.6)
				return;
			}
			close(now, prey, callees);
			return;
		}
		if (callees.isEmpty()) {
			leave(now, prey, callees, "none left");
			return;
		}
		look(prey, callees, SEEN, now);
		if (shown != null && shownThere < 0 && now < shownUntil && arrived(shown) && level.getEntity(shown) instanceof Mob there
				&& Hunts.plain(prey, there, Hunts.perceive(prey, there, SEEN))) {
			shownThere = now;
			shownUntil = Math.min(shownUntil, now + SHOW_HOLD); // there and plainly seen: it stands a moment more, then goes back
		}
		hold(now, prey, callees);
		if (ringAt < 0 && (onRing(prey, callees) || now - start >= Hunts.TOLD_TICKS - RING_WAIT)) {
			ringAt = now;
		}
		if (phase == Phase.QUIET && now >= loudAt) {
			phase = Phase.LOUD;
			if (calledFrom != null) {
				chorusFrom(prey, callees, calledFrom); // R10-4: called by a trespass, the first voices come from the place's way
			} else {
				chorus(prey, callees);
			}
		}
		if (!second && now >= start + SECOND_TELL && phase == Phase.LOUD) {
			second = true;
			chorus(prey, callees);
		}
		boolean due = shownThere < 0 ? now - start >= SHOW_FROM : now - lastSeen >= SHOW_AGAIN;
		if (due && (shown == null || now >= shownUntil)) {
			show(now, prey, callees);
		}
		if (now < closeAt || ringAt < 0 || now < ringAt + RING_WAIT) {
			return; // R10-19: never before they stood on the ring a while, silent, facing the player
		}
		boolean told = told(now);
		if (told && prey.player() != null && Hunts.covered(prey.player())) {
			leave(now, prey, callees, "sheltered"); // under a roof at the turn: they go (D-097.6)
			return;
		}
		// The start's floors asked again at the turn (R12-8): under 10, no pickaxe or shovel any more, fighting.
		String held = told ? turnHeld(prey, Hunts::digOut) : null;
		if (told && held == null) {
			phase = Phase.CLOSE;
			if (hostileAt < 0) {
				hostileAt = now;
			}
			setTrap(endAt - now + GRACE);
			close(now, prey, callees);
			return;
		}
		if (now >= closeAt + EXTEND) {
			leave(now, prey, callees, told ? "held" : "never seen");
		} else if (!told && heardBefore(now + 1) < 2 && tells.stream().noneMatch(t -> t.tick() >= closeAt)) {
			chorus(prey, callees);
		}
	}

	/** Each to its place on the ring, facing the player once there (the one shown, to its spot in their view). */
	private void hold(long now, Prey prey, List<Mob> callees) {
		for (Mob mob : callees) {
			if (mob.getUUID().equals(shown) && now < shownUntil) {
				steer(mob, shownAt, prey);
				continue;
			}
			steer(mob, ring(prey, mob), prey);
		}
	}

	private Vec3 ring(Prey prey, Mob mob) {
		int place = Math.floorMod(mobs.indexOf(mob.getUUID()), 3);
		double angle = angles.getOrDefault(mob.getUUID(), Hunts.angle(prey.feet(), mob.position()));
		if (mob.getType() == EntityTypes.SPIDER) {
			angle = outOfLook(angle, Hunts.angle(prey.look()), Math.toRadians(SPIDER_CLEAR));
		}
		return Hunts.out(prey.feet(), angle, HOLD + 2.0 * place);
	}

	/** Pure (R11-15): {@code angle}, or, within {@code clear} of {@code look} either side, the nearer edge of that, 10 degrees beyond. */
	public static double outOfLook(double angle, double look, double clear) {
		double diff = Math.atan2(Math.sin(angle - look), Math.cos(angle - look));
		if (Math.abs(diff) >= clear) {
			return angle;
		}
		return look + (diff >= 0.0 ? 1.0 : -1.0) * (clear + Math.toRadians(10.0));
	}

	/** Every one but the one walked into view stands within {@link #ON_RING} of its place on the ring. */
	private boolean onRing(Prey prey, List<Mob> callees) {
		for (Mob mob : callees) {
			if (mob.getUUID().equals(shown)) {
				continue;
			}
			if (prey.across(mob.position()) > HOLD + 4.0 + ON_RING || Math.hypot(mob.getX() - ring(prey, mob).x, mob.getZ() - ring(prey, mob).z) > ON_RING) {
				return false;
			}
		}
		return true;
	}

	private void close(long now, Prey prey, List<Mob> callees) {
		BlockPos door = door(prey);
		// The walking dead already let go there count toward the door's cap too.
		int toDoor = door == null ? 0 : (int) Hunts.loaded(level, released, Mob.class).stream()
				.filter(m -> walkingDead(m) && m.position().distanceTo(prey.feet()) <= DOOR_HELD).count();
		MobTamper tamper = Services.mobs();
		for (Mob mob : callees) {
			double d = mob.position().distanceTo(prey.feet());
			if (d <= cfg.releaseAt) {
				tamper.release(mob); // its own targeting now (vanilla): it may take whoever is nearest
				let.add(mob.getUUID());
				released.add(mob.getUUID());
				if (door != null && walkingDead(mob)) {
					toDoor++;
				}
				continue;
			}
			boolean dead = walkingDead(mob);
			if (door != null && dead && toDoor >= cfg.perDoorMax) {
				steer(mob, ring(prey, mob), prey);
				continue;
			}
			if (door != null && dead) {
				toDoor++;
			}
			steer(mob, prey.feet(), prey);
		}
		if (active(Mob.class).isEmpty()) {
			finish("all let go");
		}
	}

	/** A zombie or a husk (they break doors on hard). */
	static boolean walkingDead(Mob mob) {
		return mob.getType() == EntityTypes.ZOMBIE || mob.getType() == EntityTypes.HUSK;
	}

	/** A wooden door within {@link #DOOR_NEAR} of the player, or null. */
	private @Nullable BlockPos door(Prey prey) {
		BlockPos feet = prey.block();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-DOOR_NEAR, -2, -DOOR_NEAR), feet.offset(DOOR_NEAR, 2, DOOR_NEAR))) {
			if (level.getBlockState(pos).is(BlockTags.WOODEN_DOORS)) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** Every one's own voice at once, staggered by a few ticks: one moment, heard if the player is within range of any. */
	void chorus(Prey prey, List<Mob> callees) {
		long group = now();
		for (int k = 0; k < callees.size(); k++) {
			Mob mob = callees.get(k);
			tell("low", BuiltInRegistries.SOUND_EVENT.wrapAsHolder(voice(mob.getType())), SoundSource.HOSTILE, mob.getEyePosition(), CHORUS,
					0.85F + random.nextFloat() * 0.15F, k * 3, group);
		}
	}

	/** R10-4: how far out along the place's bearing the called first voices sound (inside the chorus's 32). */
	static final double CALLED_VOICES = 26.0;

	/**
	 * R10-4: every one's own voice at once from the bearing of {@code place}, spread a little round it, {@link #CALLED_VOICES}
	 * out (the world went quiet; then they are heard from where the player was). One moment, as {@link #chorus}.
	 */
	void chorusFrom(Prey prey, List<Mob> callees, Vec3 place) {
		long group = now();
		for (int k = 0; k < callees.size(); k++) {
			Mob mob = callees.get(k);
			double spread = (k % 2 == 0 ? 1 : -1) * 7.0 * ((k + 1) / 2);
			Vec3 at = Hunts.toward(prey.feet(), place, spread, CALLED_VOICES - (k % 3), prey.look());
			tell("low", BuiltInRegistries.SOUND_EVENT.wrapAsHolder(voice(mob.getType())), SoundSource.HOSTILE, at, CHORUS,
					0.85F + random.nextFloat() * 0.15F, k * 3, group);
		}
	}

	/** A monster's own ambient voice. */
	static SoundEvent voice(EntityType<?> type) {
		if (type == EntityTypes.HUSK) {
			return SoundEvents.HUSK_AMBIENT;
		}
		if (type == EntityTypes.SKELETON) {
			return SoundEvents.SKELETON_AMBIENT;
		}
		if (type == EntityTypes.STRAY) {
			return SoundEvents.STRAY_AMBIENT;
		}
		if (type == EntityTypes.SPIDER) {
			return SoundEvents.SPIDER_AMBIENT;
		}
		return SoundEvents.ZOMBIE_AMBIENT;
	}

	/**
	 * One of them (a zombie, a husk, a skeleton or a stray: never a spider, R11-15) walks into the middle of the player's
	 * view, {@link #SHOW_NEAR} to {@link #SHOW_FAR} out (R10-12; nearer in a thick fog, inside what they perceive), where
	 * they have a clear line to it.
	 */
	private void show(long now, Prey prey, List<Mob> callees) {
		List<Mob> walkers = callees.stream().filter(m -> m.getType() != EntityTypes.SPIDER).toList();
		if (walkers.isEmpty()) {
			return;
		}
		double look = Hunts.angle(prey.look());
		double perceived = Hunts.sightFor(Hunts.fog(level), true, Hunts.fogEnd(prey), SEEN);
		for (double deg : new double[] {0.0, -8.0, 8.0, -15.0, 15.0}) {
			for (double want : new double[] {(SHOW_NEAR + SHOW_FAR) / 2.0, SHOW_NEAR, SHOW_FAR}) {
				double dist = Math.max(8.0, Math.min(want, perceived - 1.5));
				Vec3 p = Hunts.out(prey.feet(), look + Math.toRadians(deg), dist);
				BlockPos ground = Hunts.stand(level, p.x, p.z, prey.block().getY(), 6, 8, 2);
				if (ground == null) {
					continue;
				}
				Vec3 at = Vec3.atBottomCenterOf(ground);
				if (!Hunts.clear(level, prey.eye(), at.add(0.0, 1.0, 0.0))) {
					continue;
				}
				Mob mob = walkers.stream().min(Comparator.comparingDouble(m -> m.position().distanceTo(at))).orElseThrow();
				shown = mob.getUUID();
				shownAt = at;
				shownUntil = now + SHOW_FOR;
				return;
			}
		}
	}

	/** The one walked into the player's view now (while it is on its way or there), or null. */
	public @Nullable UUID shown() {
		return shown != null && now() < shownUntil ? shown : null;
	}

	/** The one walked into view stands at its spot (within 2). */
	private boolean arrived(UUID id) {
		return level.getEntity(id) instanceof Mob mob && Math.hypot(mob.getX() - shownAt.x, mob.getZ() - shownAt.z) <= 2.0;
	}

	private void steer(Mob mob, Vec3 to, @Nullable Prey facing) {
		BlockPos ground = Hunts.stand(level, to.x, to.z, (int) Math.floor(to.y), 6, 10, 2);
		Vec3 point = ground == null ? to : Vec3.atBottomCenterOf(ground);
		MobTamper tamper = Services.mobs();
		if (!tamper.steer(mob, point, 1.0, STEER_TICKS) && !tamper.isSteered(mob)) {
			// R12-37: refused (the world's steered mobs all in use, or something else holds it). Silenced but on its own AI
			// it could come before any tell: it is let go at once, loud, no instrument of this scene.
			tamper.release(mob);
			let.add(mob.getUUID());
			return;
		}
		if (facing != null && mob.position().distanceTo(point) < 6.0) {
			tamper.face(mob, facing.eye(), STEER_TICKS);
		}
	}

	private void leave(long now, @Nullable Prey prey, List<Mob> callees, String reason) {
		if (phase == Phase.LEAVE) {
			return;
		}
		phase = Phase.LEAVE;
		leaveAt = now;
		why = reason;
		endTrap(GRACE);
		leaving(now, prey, callees);
	}

	private void leaving(long now, @Nullable Prey prey, List<Mob> callees) {
		Vec3 from = prey != null ? prey.feet() : where;
		for (Mob mob : callees) {
			double across = Math.hypot(mob.getX() - from.x, mob.getZ() - from.z);
			if (across >= LEAVE_FAR && links.view().outOfView(level, mob.getBoundingBox()) || now - leaveAt >= LEAVE_MAX) {
				letGo(mob);
			} else {
				steer(mob, Hunts.out(from, Hunts.angle(from, mob.position()), LEAVE_TO), null);
			}
		}
		if (active(Mob.class).isEmpty()) {
			finish(why.isEmpty() ? "gone" : why);
		}
	}

	@Override
	public void stop(boolean now, String reason) {
		if (now) {
			if (why.isEmpty()) {
				why = reason;
			}
			letAllGo();
			endTrap(GRACE);
			finish(reason);
		} else {
			leave(now(), prey(), active(Mob.class), reason);
		}
	}

	@Override
	public List<String> describe(long now) {
		List<String> lines = new ArrayList<>();
		lines.add(KIND + " " + phase() + " for " + (now - start) / 20 + " s" + (why.isEmpty() ? "" : " (" + why + ")") + ", closes "
				+ Math.max(0, (closeAt - now) / 20) + " s from now, ends " + Math.max(0, (endAt - now) / 20) + " s from now");
		lines.add("tells " + tells.size() + " (" + heardBefore(now + 1) + " heard), seen " + (seenAt < 0 ? "never" : (now - seenAt) / 20 + " s ago")
				+ ", plainly " + (plainAt < 0 ? "never" : (now - plainAt) / 20 + " s ago") + (ringAt < 0 ? "" : ", standing " + (now - ringAt) / 20 + " s")
				+ ", let go " + released.size() + ", lost " + dead);
		Prey prey = prey();
		for (Mob mob : Hunts.loaded(level, mobs, Mob.class)) {
			lines.add("  " + mob.getType().getDescription().getString() + " at " + mob.blockPosition().toShortString()
					+ (prey == null ? "" : ", " + (int) prey.across(mob.position()) + " off") + (released.contains(mob.getUUID()) ? ", let go" : ""));
		}
		return lines;
	}
}
