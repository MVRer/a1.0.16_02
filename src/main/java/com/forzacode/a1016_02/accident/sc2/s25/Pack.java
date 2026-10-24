package com.forzacode.a1016_02.accident.sc2.s25;

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
import com.forzacode.a1016_02.accident.sc2.Reach;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Feature 25's scene (docs/SELF_CREATED_2.md, 25; D-096, D-097): the instruments of one armed {@code sc25} trap around
 * its player, looked at every {@link Hunts#STEP} ticks.
 * <ol>
 * <li><b>First part</b> ({@code f} to {@code g} seconds, never under {@link Hunts#TOLD_TICKS}): steered to keep a band
 * where they are perceived (R10-5: at night 16 to 24; in a fog 0.55 to 0.7 of the fog end the player's client draws;
 * {@link #band}), within 40 degrees of straight behind the player ({@link #OFFSETS}, so one turn shows two or three of
 * them), along a bearing that follows behind them only while they move (they stop when the player stops), backing off
 * when the player comes nearer than the band, always facing them. The long tell ({@link #howl}: three of them, each its
 * own sound variant's whine, low and slow, then the nearest one's growl, volume {@code h}, heard to 48) at the start, again
 * 30 to 45 s in, and a third as the closing begins; a growl (volume 2, heard to 32) from one the player looks at within 32.
 * From 15 s in until one was plainly seen (the middle of the player's view, within 20), and whenever none was seen for 30
 * s, one walks into the middle of their view 17 to 20 out, against the sky where it can ({@link #show}). The
 * <b>closing</b>, the last 20 s: in to 12 to 16, each growling once as it steps in.</li>
 * <li>At its end, only if it was told (60 s, two long tells heard by the player, one seen and one plainly seen; up to 30 s more to get there,
 * else they go) and the start's floors hold again (R12-8: health 10, a way out, not within 64 of their base, not
 * fighting something else; held up to the same 30 s, else they go), where the player is: in the open, the <b>close</b> (they run in to 10 blocks, each growling, at most 3 s:
 * every one is then within a wolf's follow range, 16, whose double the hunt keeps), then the <b>last part</b>: each
 * {@code MobTamper.hunt} for at most {@code i} seconds, vanilla angry wolves, the trap set at that moment. Out of reach
 * (2+ above every spot they can reach, {@link Reach}) they <b>wait</b>: calmed, circling below, growling, for at most
 * {@code n} seconds (they go on at once if the player comes down). Covered, in deep water or riding, they go.</li>
 * <li><b>Going</b>: at dawn, the last part's time over, the player covered, in deep water or riding, two of them dead,
 * the player lost (all over 32 off for 5 s), gone, or the trap over. Calmed, a last long tell, steered 60 away and
 * released once out of everyone's view (at most a minute). The switch going off lets them all go at once.</li>
 * </ol>
 * The player hitting one in the first part sets them all on at once (their own doing); a friend hitting one lets that
 * one go to vanilla's anger (it turns on the friend); in the last part a friend's hit does the same through the hunt's
 * own rule. Nothing is ever set on anyone but the trap's player.
 */
public final class Pack extends HuntScene {
	public static final String KIND = "sc25";

	/** Where it stands. */
	public enum Phase { STALK, CLOSE, ATTACK, SIEGE, LEAVE }

	/**
	 * R10-5: the band kept in the first part at night without a fog (blocks from the player, horizontal): where a wolf reads
	 * as a wolf at night at render distance 12 under the mod's haze (at 28 to 40, the first build's band, it was a few grey
	 * pixels: fair on paper, unseen in play).
	 */
	public static final double NIGHT_MIN = 16.0;
	public static final double NIGHT_MAX = 24.0;
	/** In a fog that counts: this share of the fog end the player's client draws (R11-3), at night never past the night band. */
	public static final double FOG_MIN = 0.55;
	public static final double FOG_MAX = 0.7;
	/** The first part's band is never nearer than this, nor (a fog by day) farther than {@link #BAND_MOST}. */
	public static final double BAND_FLOOR = 12.0;
	static final double BAND_MOST = 30.0;
	/**
	 * R10-5, the closing: the last {@link #CLOSING_TICKS} of the first part (and any time it is held on past it) the band is
	 * {@link #CLOSING_MIN} to {@link #CLOSING_MAX}; each one growls once as it steps in (volume 1.5) and the third long tell
	 * plays as it begins. The danger comes nearer, heard and seen, before anything turns.
	 */
	public static final int CLOSING_TICKS = 400;
	public static final double CLOSING_MIN = 12.0;
	public static final double CLOSING_MAX = 16.0;
	static final float STEP_IN_VOLUME = 1.5F;
	/** Slot bearings, degrees from straight behind the player, one per instrument (R11-3: within 40 either side, one turn shows them). */
	static final double[] OFFSETS = {0.0, -22.0, 22.0, -40.0, 40.0};
	/** Where in the band each slot stands (0 its inner edge, 1 its outer). */
	static final double[] SHARES = {0.5, 0.2, 0.8, 0.35, 0.65};
	/** A growl when the player looks at one this near (R10-5: any in the band, and the band's way in), heard to 32. */
	static final double GROWL_NEAR = 32.0;
	static final int GROWL_EVERY = 60;
	static final float GROWL_VOLUME = 2.0F;
	/**
	 * One walks into the player's view first this long after the start (after the first long tell), and again whenever none
	 * was seen for {@link #SHOW_AFTER} or none was plainly seen yet; it holds there at most {@link #SHOW_FOR}, and
	 * {@link #SHOW_HOLD} once plainly seen, then goes back to its place.
	 */
	static final int SHOW_FIRST = 300;
	static final int SHOW_AFTER = 600;
	static final int SHOW_FOR = 400;
	static final int SHOW_HOLD = 60;
	/** Where it stands in their view (R10-5: within 20; R11-3: 18 to 24): its distances and its bearings off their look. */
	static final double[] SHOW_DISTANCES = {19.0, 17.5, 20.0};
	static final double[] SHOW_DEGREES = {0.0, -8.0, 8.0, -15.0, 15.0};
	/** How far past its head the line from the player's eyes must meet nothing for it to stand against the sky. */
	static final double SKYLINE_BEYOND = 48.0;
	/** The long tell's growl (R10-5): this long after its last whine (ticks), at this pitch, as loud as the whines. */
	static final int TELL_GROWL_AFTER = 10;
	static final float TELL_GROWL_PITCH = 0.6F;
	/** How far "seen" can reach at all (blocks): the perceived distance is never more ({@link Hunts#perceive}). */
	static final double SEEN_CAP = 44.0;
	/** The second long tell, this long after the start (30 s, plus up to 15). */
	static final int SECOND_TELL = 600;
	static final int SECOND_TELL_SPREAD = 300;
	/** Not told at the end of the first part: up to this long more, then they go. */
	static final int EXTEND = 600;
	/** The close: in to this distance, at this speed, for at most this long. */
	static final double CLOSE_TO = 10.0;
	static final double CLOSE_SPEED = 1.4;
	static final int CLOSE_TICKS = 60;
	/** Twice a wolf's follow range: past it the hunt lets go of its player. */
	static final double LOST = 32.0;
	static final int LOST_TICKS = 100;
	/** The wait: circling this far out, turning this much each step. */
	static final double CIRCLE = 4.5;
	static final double CIRCLE_TURN = Math.toRadians(12.0);
	/** Going: this far, released out of view, at most this long. */
	static final double LEAVE_TO = 60.0;
	static final double LEAVE_FAR = 40.0;
	static final int LEAVE_MAX = 1200;
	/** A steer lasts this long; each step renews it, so a scene that stops leaves nothing walking for long. */
	static final int STEER_TICKS = 40;
	/** The trap stays set this long after the last part could end (ticks): a death a moment after still counts. */
	static final int GRACE = 600;

	private final Sc25Config cfg;
	private final BooleanSupplier dark;
	private final RandomSource random;
	private Phase phase;
	private final long stalkUntil;
	private final long secondTellAt;
	private boolean secondTell;
	private double bearing;
	private Vec3 lastFeet;
	private long closeAt = -1;
	private long attackUntil = -1;
	private long siegeTicks;
	private long leaveAt = -1;
	private long farFrom = -1;
	private double circle;
	private int growlTurn;
	private boolean provoked;
	private @Nullable UUID shown;
	private Vec3 shownAt = Vec3.ZERO;
	private long shownUntil;
	private final Map<UUID, Long> growled = new HashMap<>();
	private final Map<UUID, Long> hurtSeen = new HashMap<>();
	/** When the closing began (server tick), or -1. */
	private long closingAt = -1;
	/** The ones that growled as they stepped in for the closing. */
	private final java.util.Set<UUID> steppedIn = new java.util.HashSet<>();

	/**
	 * A scene for {@code target} (or the gallery's {@code standIn}) with these instruments, starting at {@code begin}
	 * (the first part for a trap; the gallery may start at the wait).
	 */
	public Pack(UUID target, ServerLevel level, Links links, long armedAt, Prey prey, List<Wolf> wolves, Sc25Config cfg, BooleanSupplier dark,
			@Nullable Prey standIn, Phase begin) {
		super(KIND, target, level, links, armedAt, prey.feet(), wolves.stream().map(Wolf::getUUID).toList(), standIn);
		this.cfg = cfg;
		this.dark = dark;
		this.random = level.getRandom();
		this.phase = begin;
		double min = Math.max(cfg.stalkSecondsMin, Hunts.TOLD_TICKS / 20.0);
		double max = Math.max(min, cfg.stalkSecondsMax);
		long length = Math.round((min + random.nextDouble() * (max - min)) * 20.0);
		this.stalkUntil = start + Math.max(Hunts.TOLD_TICKS, length);
		// The second long tell comes before the closing's own (the third), never with it.
		this.secondTellAt = Math.min(start + SECOND_TELL + random.nextInt(SECOND_TELL_SPREAD + 1), stalkUntil - CLOSING_TICKS - 100);
		this.lastFeet = prey.feet();
		Vec3 middle = Vec3.ZERO;
		for (Wolf wolf : wolves) {
			middle = middle.add(wolf.position());
			hurtSeen.put(wolf.getUUID(), hurtMark(wolf));
		}
		this.bearing = wolves.isEmpty() ? Hunts.angle(prey.look()) + Math.PI : Hunts.angle(prey.feet(), middle.scale(1.0 / wolves.size()));
	}

	/**
	 * The start: the first long tell now; the first step comes with the next server tick (the planner stores the trap
	 * after {@code onArmed} returns, and a step checks that it holds).
	 */
	public void begin() {
		Prey prey = prey();
		List<Wolf> pack = active(Wolf.class);
		if (prey != null && phase == Phase.STALK) {
			if (calledFrom != null) {
				// R10-4: called by a trespass, the first long tell comes from the place they were in, and they come from there.
				bearing = Hunts.angle(prey.feet(), calledFrom);
				howlFrom(prey, pack, calledFrom);
			} else {
				howl(prey, pack, 0);
			}
		}
		if (phase == Phase.SIEGE) {
			siegeTicks = 0;
		}
	}

	public Phase phaseNow() {
		return phase;
	}

	@Override
	public String phase() {
		return phase.name().toLowerCase(Locale.ROOT);
	}

	/** Server tick the first part ends (unless extended). */
	public long stalkUntil() {
		return stalkUntil;
	}

	public boolean provoked() {
		return provoked;
	}

	/** The one walked into the player's view to be seen (while it is on its way or there), or null. */
	public @Nullable UUID shown() {
		return shown != null && now() < shownUntil ? shown : null;
	}

	@Override
	protected void act(long now, @Nullable Prey prey, boolean trapHolds) {
		List<Wolf> pack = active(Wolf.class);
		if (phase == Phase.LEAVE) {
			leaving(now, prey, pack);
			return;
		}
		if (prey == null) {
			leave(now, null, pack, "gone");
			return;
		}
		if (!trapHolds) {
			leave(now, prey, pack, "over");
			return;
		}
		if (pack.isEmpty()) {
			why = "none left";
			endTrap(0);
			finish("none left");
			return;
		}
		if (!dark.getAsBoolean()) {
			leave(now, prey, pack, "dawn");
			return;
		}
		if (dead >= 2) {
			leave(now, prey, pack, "two lost");
			return;
		}
		switch (phase) {
			case STALK -> stalk(now, prey, pack);
			case CLOSE -> close(now, prey, pack);
			case ATTACK -> attack(now, prey, pack);
			case SIEGE -> siege(now, prey, pack);
			default -> {
			}
		}
	}

	// --- the first part ---

	private void stalk(long now, Prey prey, List<Wolf> pack) {
		if (hits(now, prey, pack)) {
			return;
		}
		look(prey, pack, SEEN_CAP, now);
		if (shown != null && now < shownUntil && shownUntil - now > SHOW_HOLD && level.getEntity(shown) instanceof Wolf at
				&& Math.hypot(at.getX() - shownAt.x, at.getZ() - shownAt.z) <= 2.0 && Hunts.plain(prey, at, Hunts.perceive(prey, at, SEEN_CAP))) {
			shownUntil = Math.min(shownUntil, now + SHOW_HOLD); // there and plainly seen: it stays a moment, then goes back to its place
		}
		Vec3 moved = prey.feet().subtract(lastFeet);
		if (moved.horizontalDistance() > 0.4) {
			bearing = Hunts.turn(bearing, Hunts.angle(new Vec3(-moved.x, 0.0, -moved.z)), Math.toRadians(4.0));
			lastFeet = prey.feet();
		}
		boolean closing = closing(now);
		if (closing && closingAt < 0) {
			closingAt = now;
			howl(prey, pack, 0); // the third long tell: they are coming in
		}
		double[] band = band(prey, now);
		for (int i = 0; i < pack.size(); i++) {
			Wolf wolf = pack.get(i);
			double d = prey.across(wolf.position());
			if (wolf.getUUID().equals(shown) && now < shownUntil) {
				// Round the player at the band's outer edge, never across: in 45 degree legs until it is within 50 of its spot.
				double at = Hunts.angle(prey.feet(), wolf.position());
				double want = Hunts.angle(prey.feet(), shownAt);
				double diff = Math.atan2(Math.sin(want - at), Math.cos(want - at));
				Vec3 to = Math.abs(diff) > Math.toRadians(50.0) ? Hunts.out(prey.feet(), at + Math.signum(diff) * Math.toRadians(45.0), band[1]) : shownAt;
				steer(wolf, to, 1.2, prey);
			} else {
				int slot = slot(wolf);
				double keep = band[0] + SHARES[slot] * (band[1] - band[0]);
				double at = Hunts.angle(prey.feet(), wolf.position());
				double want = bearing + Math.toRadians(OFFSETS[slot]);
				double diff = Math.atan2(Math.sin(want - at), Math.cos(want - at));
				if (d < band[0] - 1.0) {
					steer(wolf, Hunts.out(prey.feet(), at, keep), 1.3, prey);
				} else if (Math.abs(diff) > Math.toRadians(60.0)) {
					// Its place is round the other side (back from showing itself): round the player at the band, never across.
					steer(wolf, Hunts.out(prey.feet(), at + Math.signum(diff) * Math.toRadians(45.0), band[1]), 1.2, prey);
				} else {
					Vec3 to = Hunts.out(prey.feet(), want, keep);
					double behind = wolf.position().distanceTo(to);
					steer(wolf, to, behind > 20.0 ? 1.5 : behind > 8.0 ? 1.2 : 0.85, prey);
				}
			}
			if (closing && d <= CLOSING_MAX + 1.5 && steppedIn.add(wolf.getUUID())) {
				growl(wolf, 0, STEP_IN_VOLUME); // each one, once, as it steps in
			} else if (d <= GROWL_NEAR && now - growled.getOrDefault(wolf.getUUID(), Long.MIN_VALUE / 2) >= GROWL_EVERY
					&& Hunts.seen(prey, wolf, GROWL_NEAR)) {
				growl(wolf, 0, GROWL_VOLUME);
			}
		}
		boolean due = plainAt < 0 ? now - start >= SHOW_FIRST : !closing && now - lastSeen >= SHOW_AFTER;
		if ((shown == null || now >= shownUntil) && due) {
			show(now, prey, pack);
		}
		if (!secondTell && now >= secondTellAt) {
			secondTell = true;
			howl(prey, pack, 0);
		}
		if (now < stalkUntil) {
			return;
		}
		if (!told(now)) {
			if (now < stalkUntil + EXTEND) {
				if (heardBefore(now + 1) < 2 && now >= stalkUntil + EXTEND / 2 && tells.stream().noneMatch(t -> t.tick() >= stalkUntil)) {
					howl(prey, pack, 0);
				}
				return;
			}
			leave(now, prey, pack, "never seen");
			return;
		}
		switch (shelter(prey, pack)) {
			case OPEN -> {
				// The start's floors asked again at the turn (R12-8): held off up to the same 30 s, then they go.
				String held = turnHeld(prey, Hunts::wayOut);
				if (held == null) {
					startClose(now, prey, pack);
				} else if (now >= stalkUntil + EXTEND) {
					leave(now, prey, pack, "held");
				}
			}
			case UP -> startSiege(now, prey, pack);
			default -> leave(now, prey, pack, "sheltered");
		}
	}

	/**
	 * The player hit one: in the first part or the close, the player hitting one sets all on (their own doing); a friend
	 * hitting one lets that one go to vanilla's anger. True if the phase changed.
	 */
	private boolean hits(long now, Prey prey, List<Wolf> pack) {
		for (Wolf wolf : pack) {
			long mark = hurtMark(wolf);
			if (mark == hurtSeen.getOrDefault(wolf.getUUID(), mark)) {
				continue;
			}
			hurtSeen.put(wolf.getUUID(), mark);
			LivingEntity by = wolf.getLastHurtByMob();
			if (!(by instanceof Player hitter)) {
				continue;
			}
			if (hitter.getUUID().equals(target)) {
				if (prey.player() != null) {
					provoked = true;
					startAttack(now, prey, pack);
					return true;
				}
			} else {
				// A friend's hit: that one is theirs now (vanilla's anger, uncalmed).
				Services.mobs().release(wolf);
				let.add(wolf.getUUID());
			}
		}
		return false;
	}

	/** Who last hurt it and when (a change is a new hit, even on its first tick), or {@link Long#MIN_VALUE} if nobody. */
	private static long hurtMark(Wolf wolf) {
		LivingEntity by = wolf.getLastHurtByMob();
		return by == null ? Long.MIN_VALUE : (long) by.getId() << 32 | wolf.getLastHurtByMobTimestamp() & 0xFFFFFFFFL;
	}

	// --- the close and the last part ---

	private void startClose(long now, Prey prey, List<Wolf> pack) {
		phase = Phase.CLOSE;
		closeAt = now;
		for (int i = 0; i < pack.size(); i++) {
			growl(pack.get(i), i * 5, STEP_IN_VOLUME);
		}
		close(now, prey, pack);
	}

	private void close(long now, Prey prey, List<Wolf> pack) {
		if (hits(now, prey, pack)) {
			return;
		}
		Shelter shelter = shelter(prey, pack);
		if (shelter == Shelter.UP) {
			startSiege(now, prey, pack);
			return;
		}
		if (shelter != Shelter.OPEN) {
			leave(now, prey, pack, "sheltered");
			return;
		}
		boolean near = false;
		for (Wolf wolf : pack) {
			steer(wolf, Hunts.out(prey.feet(), Hunts.angle(prey.feet(), wolf.position()), CLOSE_TO), CLOSE_SPEED, prey);
			near |= prey.across(wolf.position()) <= 16.0;
		}
		if (near || now - closeAt >= CLOSE_TICKS) {
			startAttack(now, prey, pack);
		}
	}

	private void startAttack(long now, Prey prey, List<Wolf> pack) {
		ServerPlayer player = prey.player();
		if (player == null) {
			startSiege(now, prey, pack);
			return;
		}
		if (!provoked && !told(now)) {
			return; // never before it was told (D-097.1)
		}
		if (!provoked && turnHeld(prey, Hunts::wayOut) != null) {
			leave(now, prey, pack, "held"); // the floors asked again at the turn (R12-8): under 10, no way out, home, fighting
			return;
		}
		if (attackUntil < 0) {
			attackUntil = now + Math.round(cfg.attackSecondsMax * 20.0);
		}
		long left = attackUntil - now;
		if (left <= 0) {
			leave(now, prey, pack, "time");
			return;
		}
		MobTamper tamper = Services.mobs();
		boolean any = false;
		for (Wolf wolf : pack) {
			any |= tamper.hunt(wolf, player, (int) left);
		}
		if (!any) {
			leave(now, prey, pack, "refused");
			return;
		}
		if (hostileAt < 0) {
			hostileAt = now;
		}
		phase = Phase.ATTACK;
		farFrom = -1;
		setTrap(left + Math.round(cfg.siegeSeconds * 20.0) + GRACE);
	}

	private void attack(long now, Prey prey, List<Wolf> pack) {
		if (now >= attackUntil) {
			leave(now, prey, pack, "time");
			return;
		}
		MobTamper tamper = Services.mobs();
		List<Wolf> on = new ArrayList<>();
		for (Wolf wolf : pack) {
			if (tamper.isHunting(wolf)) {
				on.add(wolf);
			} else {
				let.add(wolf.getUUID()); // a friend's hit ended its hunt: vanilla's now
			}
		}
		if (on.isEmpty()) {
			leave(now, prey, pack, "none on");
			return;
		}
		Shelter shelter = shelter(prey, on);
		if (shelter == Shelter.UP) {
			startSiege(now, prey, on);
			return;
		}
		if (shelter != Shelter.OPEN) {
			leave(now, prey, on, "sheltered");
			return;
		}
		if (now >= attackUntil) {
			leave(now, prey, on, "time");
			return;
		}
		boolean allFar = on.stream().allMatch(w -> w.position().distanceTo(prey.feet()) > LOST);
		if (!allFar) {
			farFrom = -1;
		} else if (farFrom < 0) {
			farFrom = now;
		} else if (now - farFrom >= LOST_TICKS) {
			leave(now, prey, on, "lost");
		}
	}

	// --- the wait ---

	private void startSiege(long now, Prey prey, List<Wolf> pack) {
		phase = Phase.SIEGE;
		MobTamper tamper = Services.mobs();
		for (Wolf wolf : pack) {
			tamper.calm(wolf);
		}
		circle = Hunts.angle(prey.feet(), pack.isEmpty() ? prey.feet().add(0, 0, 1) : pack.getFirst().position());
		siege(now, prey, pack);
	}

	private void siege(long now, Prey prey, List<Wolf> pack) {
		for (Wolf wolf : pack) {
			hurtSeen.put(wolf.getUUID(), hurtMark(wolf)); // shots from up there: they take them
		}
		Shelter shelter = shelter(prey, pack);
		if (shelter == Shelter.OPEN && prey.player() != null) {
			if (attackUntil < 0 || now < attackUntil) {
				startAttack(now, prey, pack);
			} else {
				leave(now, prey, pack, "time");
			}
			return;
		}
		if (shelter != Shelter.OPEN && shelter != Shelter.UP) {
			leave(now, prey, pack, "sheltered");
			return;
		}
		siegeTicks += Hunts.STEP;
		if (siegeTicks > Math.round(cfg.siegeSeconds * 20.0)) {
			leave(now, prey, pack, "waited");
			return;
		}
		circle += CIRCLE_TURN;
		int n = Math.max(1, pack.size());
		int floor = (int) Math.floor(prey.feet().y) - 2;
		for (int i = 0; i < pack.size(); i++) {
			double radius = CIRCLE + (i % 2 == 0 ? 0.0 : 1.5);
			Vec3 to = Hunts.out(prey.feet(), circle + i * 2.0 * Math.PI / n, radius);
			BlockPos ground = Hunts.stand(level, to.x, to.z, floor, 2, 8, 1);
			steer(pack.get(i), ground == null ? new Vec3(to.x, floor, to.z) : Vec3.atBottomCenterOf(ground), 0.9, prey);
		}
		if (!pack.isEmpty() && (now / Hunts.STEP) % 3 == 0) {
			growl(pack.get(Math.floorMod(growlTurn++, pack.size())), 0, STEP_IN_VOLUME);
		}
	}

	// --- going ---

	private void leave(long now, @Nullable Prey prey, List<Wolf> pack, String reason) {
		if (phase == Phase.LEAVE) {
			return;
		}
		phase = Phase.LEAVE;
		leaveAt = now;
		why = reason;
		endTrap(0);
		MobTamper tamper = Services.mobs();
		for (Wolf wolf : pack) {
			tamper.calm(wolf);
		}
		if (prey != null && !pack.isEmpty() && !reason.equals("over")) {
			howl(prey, pack, 50);
		}
		leaving(now, prey, pack);
	}

	private void leaving(long now, @Nullable Prey prey, List<Wolf> pack) {
		Vec3 from = prey != null ? prey.feet() : where;
		for (Wolf wolf : pack) {
			double away = Hunts.angle(from, wolf.position());
			Vec3 to = Hunts.out(from, away, LEAVE_TO);
			double across = Math.hypot(wolf.getX() - from.x, wolf.getZ() - from.z);
			boolean far = across >= LEAVE_FAR;
			if (far && links.view().outOfView(level, wolf.getBoundingBox()) || now - leaveAt >= LEAVE_MAX) {
				letGo(wolf);
			} else {
				steer(wolf, to, 1.0, null);
			}
		}
		if (active(Wolf.class).isEmpty()) {
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
			endTrap(0);
			finish(reason);
		} else {
			leave(now(), prey(), active(Wolf.class), reason);
		}
	}

	// --- pieces ---

	/** Where the player is: in the open, out of reach above them, or sheltered (covered, deep water, riding). */
	enum Shelter { OPEN, UP, COVERED, WATER, RIDING }

	Shelter shelter(Prey prey, List<Wolf> pack) {
		ServerPlayer player = prey.player();
		if (player != null) {
			if (player.isPassenger()) {
				return Shelter.RIDING;
			}
			if (Hunts.deepWater(player)) {
				return Shelter.WATER;
			}
			if (Hunts.covered(player)) {
				return Shelter.COVERED;
			}
		}
		return Reach.outOfReach(level, prey.feet(), pack.stream().map(Wolf::position).toList()) ? Shelter.UP : Shelter.OPEN;
	}

	/** The instrument's slot (its place in the scene's own list, so slots never swap when one is lost). */
	private int slot(Wolf wolf) {
		int i = mobs.indexOf(wolf.getUUID());
		return Math.floorMod(i, OFFSETS.length);
	}

	/** The closing: the last {@link #CLOSING_TICKS} of the first part, and any time it is held on past its end. */
	boolean closing(long now) {
		return phase == Phase.STALK && now >= stalkUntil - CLOSING_TICKS;
	}

	/** Server tick the closing began, or -1. */
	public long closingAt() {
		return closingAt;
	}

	/** The band kept now, {inner, outer} (blocks from the player, horizontal): the closing's, else {@link #band(boolean, boolean, double)}. */
	public double[] band(Prey prey, long now) {
		if (closing(now)) {
			return new double[] {CLOSING_MIN, CLOSING_MAX};
		}
		boolean fog = Hunts.fog(level);
		return band(fog, !fog || Hunts.night(level), fog ? Hunts.fogEnd(prey) : Double.NaN);
	}

	/**
	 * Pure (R10-5, R11-3): the first part's band for a fog that counts or not, at night or not, and the fog end the player's
	 * client draws. No fog: {@link #NIGHT_MIN} to {@link #NIGHT_MAX}. A fog at night: the nearer of that and {@link #FOG_MIN}
	 * to {@link #FOG_MAX} of the fog end; a fog by day: the fog's share alone, at most {@link #BAND_MOST}. Never nearer than
	 * {@link #BAND_FLOOR}, at least 3 deep.
	 */
	public static double[] band(boolean fog, boolean night, double fogEnd) {
		double inner = NIGHT_MIN;
		double outer = NIGHT_MAX;
		if (fog && Double.isFinite(fogEnd)) {
			double fogInner = fogEnd * FOG_MIN;
			double fogOuter = fogEnd * FOG_MAX;
			if (night) {
				inner = Math.min(inner, fogInner);
				outer = Math.min(outer, fogOuter);
			} else {
				inner = Math.min(fogInner, BAND_MOST - 3.0);
				outer = Math.min(fogOuter, BAND_MOST);
			}
		}
		inner = Math.max(BAND_FLOOR, inner);
		outer = Math.max(inner + 3.0, outer);
		return new double[] {inner, outer};
	}

	/**
	 * One of them walks into the middle of the player's view, 17 to 20 out (R10-5; nearer in a fog, inside what they perceive
	 * of it), where it stands against the sky if any such spot is near (R11-3: higher ground with the land beyond it lower, a
	 * silhouette), else on the highest ground they have a clear line to.
	 */
	private void show(long now, Prey prey, List<Wolf> pack) {
		double look = Hunts.angle(prey.look());
		double perceived = Hunts.sightFor(Hunts.fog(level), true, Hunts.fogEnd(prey), SEEN_CAP);
		Vec3 best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (double deg : SHOW_DEGREES) {
			for (double want : SHOW_DISTANCES) {
				double dist = Math.max(BAND_FLOOR - 2.0, Math.min(want, perceived - 1.5));
				Vec3 p = Hunts.out(prey.feet(), look + Math.toRadians(deg), dist);
				BlockPos ground = Hunts.stand(level, p.x, p.z, prey.block().getY(), 10, 10, 1);
				if (ground == null) {
					continue;
				}
				Vec3 at = Vec3.atBottomCenterOf(ground);
				if (!Hunts.clear(level, prey.eye(), at.add(0.0, 0.6, 0.0))) {
					continue;
				}
				double score = (Hunts.skyline(level, prey.eye(), at, 0.85, SKYLINE_BEYOND) ? 100.0 : 0.0)
						+ Math.min(8, ground.getY() - prey.block().getY()) - Math.abs(deg) / 10.0;
				if (score > bestScore) {
					bestScore = score;
					best = at;
				}
			}
		}
		if (best == null || pack.isEmpty()) {
			return;
		}
		Vec3 spot = best;
		Wolf wolf = pack.stream().min(Comparator.comparingDouble(w -> w.position().distanceTo(spot))).orElseThrow();
		shown = wolf.getUUID();
		shownAt = spot;
		shownUntil = now + SHOW_FOR;
	}

	private void steer(Wolf wolf, Vec3 to, double speed, @Nullable Prey facing) {
		BlockPos ground = Hunts.stand(level, to.x, to.z, (int) Math.floor(to.y), 6, 10, 1);
		Vec3 point = ground == null ? to : Vec3.atBottomCenterOf(ground);
		MobTamper tamper = Services.mobs();
		if (!tamper.steer(wolf, point, speed, STEER_TICKS) && !tamper.isSteered(wolf)) {
			// R12-37: refused (the world's steered mobs all in use, or something else holds it): no instrument of this scene.
			tamper.release(wolf);
			let.add(wolf.getUUID());
			return;
		}
		if (facing != null) {
			tamper.face(wolf, facing.eye(), STEER_TICKS);
		}
	}

	/**
	 * The long tell: up to three of them, each with its own sound variant's whine, low and slow, one after another, at
	 * volume {@code h} (never under 1.0); the first from the nearest, moved in along its line to within hearing of the
	 * player if it is farther than that. One moment: heard if the player was within range of any of them.
	 */
	void howl(Prey prey, List<Wolf> pack, int delay) {
		if (pack.isEmpty()) {
			return;
		}
		List<Wolf> sorted = new ArrayList<>(pack);
		sorted.sort(Comparator.comparingDouble(w -> w.position().distanceTo(prey.feet())));
		float volume = Math.max(cfg.howlVolume, Hunts.AUDIBLE);
		double range = Math.max(volume, 1.0F) * 16.0 - 2.0;
		float[] pitches = {0.56F, 0.63F, 0.5F};
		long group = now() + delay;
		for (int k = 0; k < Math.min(3, sorted.size()); k++) {
			Wolf wolf = sorted.get(k);
			Vec3 at = wolf.position().add(0.0, 0.6, 0.0);
			if (k == 0 && at.distanceTo(prey.feet()) > range) {
				at = prey.feet().add(at.subtract(prey.feet()).normalize().scale(range));
			}
			tell("long", sounds(wolf).whineSound(), SoundSource.NEUTRAL, at, volume, pitches[k], delay + k * 9, group);
		}
		// R10-5: then the nearest one's growl, low, as loud as the whines: unmistakably hostile, never a dog whimpering.
		Wolf first = sorted.getFirst();
		Vec3 from = first.position().add(0.0, 0.6, 0.0);
		if (from.distanceTo(prey.feet()) > range) {
			from = prey.feet().add(from.subtract(prey.feet()).normalize().scale(range));
		}
		int last = (Math.min(3, sorted.size()) - 1) * 9;
		tell("long", sounds(first).growlSound(), SoundSource.NEUTRAL, from, volume, TELL_GROWL_PITCH, delay + last + TELL_GROWL_AFTER, group);
	}

	/** R10-4: how far out along the place's bearing the called first tell sounds (inside the long tell's 48). */
	static final double CALLED_TELL = 40.0;

	/**
	 * R10-4: the long tell from the bearing of {@code place}: the three whines a little apart round that bearing,
	 * {@link #CALLED_TELL} out, then the growl from the same way, as loud. One moment, as {@link #howl}.
	 */
	void howlFrom(Prey prey, List<Wolf> pack, Vec3 place) {
		if (pack.isEmpty()) {
			return;
		}
		float volume = Math.max(cfg.howlVolume, Hunts.AUDIBLE);
		double out = Math.min(CALLED_TELL, Math.max(volume, 1.0F) * 16.0 - 4.0);
		float[] pitches = {0.56F, 0.63F, 0.5F};
		double[] spread = {0.0, -9.0, 9.0};
		long group = now();
		int n = Math.min(3, pack.size());
		for (int k = 0; k < n; k++) {
			Vec3 at = Hunts.toward(prey.feet(), place, spread[k], out - k * 2.0, prey.look());
			tell("long", sounds(pack.get(k)).whineSound(), SoundSource.NEUTRAL, at, volume, pitches[k], k * 9, group);
		}
		tell("long", sounds(pack.getFirst()).growlSound(), SoundSource.NEUTRAL, Hunts.toward(prey.feet(), place, 0.0, out, prey.look()), volume,
				TELL_GROWL_PITCH, (n - 1) * 9 + TELL_GROWL_AFTER, group);
	}

	/** A growl from this one, this loud (1.5: heard to 24; 2.0: to 32). */
	void growl(Wolf wolf, int delay, float volume) {
		growled.put(wolf.getUUID(), now() + delay);
		tell("low", sounds(wolf).growlSound(), SoundSource.NEUTRAL, wolf.position().add(0.0, 0.5, 0.0), volume, 0.75F + random.nextFloat() * 0.2F, delay, -1);
	}

	/** The wolf's own adult sound set (its sound variant), the classic one if it has none. */
	static WolfSoundVariant.WolfSoundSet sounds(Wolf wolf) {
		Holder<WolfSoundVariant> variant = wolf.get(DataComponents.WOLF_SOUND_VARIANT);
		if (variant == null) {
			variant = wolf.level().registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT).getOrThrow(WolfSoundVariants.CLASSIC);
		}
		return variant.value().adultSounds();
	}

	@Override
	public List<String> describe(long now) {
		List<String> lines = new ArrayList<>();
		lines.add(KIND + " " + phase() + " for " + (now - start) / 20 + " s" + (why.isEmpty() ? "" : " (" + why + ")") + ", first part until "
				+ Math.max(0, (stalkUntil - now) / 20) + " s from now");
		lines.add("tells " + tells.size() + " (" + heardBefore(now + 1) + " long heard), seen " + (seenAt < 0 ? "never" : (now - seenAt) / 20 + " s ago")
				+ ", plainly " + (plainAt < 0 ? "never" : (now - plainAt) / 20 + " s ago") + (closingAt < 0 ? "" : ", closing " + (now - closingAt) / 20 + " s")
				+ ", hostile " + (hostileAt < 0 ? "not yet" : (hostileAt - start) / 20 + " s in") + (provoked ? " (provoked)" : "") + ", lost " + dead);
		Prey prey = prey();
		for (Wolf wolf : Hunts.loaded(level, mobs, Wolf.class)) {
			lines.add("  " + wolf.getUUID().toString().substring(0, 8) + " at " + wolf.blockPosition().toShortString()
					+ (prey == null ? "" : ", " + (int) prey.across(wolf.position()) + " off") + (let.contains(wolf.getUUID()) ? ", let go" : "")
					+ (Services.mobs().isHunting(wolf) ? ", on" : ""));
		}
		return lines;
	}
}
