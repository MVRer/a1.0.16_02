package com.forzacode.a1016_02.atmosphere.sc2.s21;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc.Prints;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 21's running scenes (docs/SELF_CREATED_2.md): the animals walked to their slots round a circle (the assembly, at most
 * {@link #ASSEMBLY_TICKS}), then round it widdershins nose to tail, silent, each walked every {@link #EVERY} ticks to the
 * point ahead of its slot at the speed that keeps it there ({@link Ring}); anyone coming within the break distance
 * makes every one of them stop and face that player for a moment, and then they are let go; the time running out lets
 * them go where they are; an animal hurt, fed, leashed or taken by anything else drops out, and under {@link #MIN_LEFT}
 * left every one is let go. Afterwards, once out of everyone's view, the way round and its middle are bared to dirt in
 * one ledgered batch ({@link #CAUSE}), tried every {@link #PRINT_EVERY} ticks for {@link #PRINT_FOR} ticks. Every mob it
 * walks is bound to everyone who shared the moment ({@code MobTamperImpl.bind}). Server thread.
 */
public final class Rings {
	public static final Rings LIVE = new Rings();
	/** The ledger's cause of the bared ground. */
	public static final String CAUSE = "atmosphere:s21";
	/** A scene's step (ticks). */
	public static final int EVERY = 10;
	/** The assembly lasts at most this long (ticks, 30 s). */
	public static final int ASSEMBLY_TICKS = 600;
	/** Under this many left, every one is let go. */
	public static final int MIN_LEFT = 5;
	/** An animal within this of its slot (blocks) is in place for the assembly. */
	static final double IN_PLACE = 1.5;
	/** Each step's steer and silence last this long (ticks), so a scene that stops stepping lets them go by itself. */
	static final int HOLD = 40;
	/** The walk speed factor of the assembly. */
	static final double ASSEMBLY_SPEED = 0.9;
	/** A scene bares its ground only after it went round at least this long (ticks). */
	static final int PRINT_AFTER = 400;
	static final int PRINT_EVERY = 100;
	static final int PRINT_FOR = 12_000;

	public enum Phase {
		ASSEMBLY, ROUND, STARE, DONE
	}

	/** One animal of a scene and its slot. */
	static final class Member {
		final Mob mob;
		final int slot;
		float health;
		/** Walked at least once (from then on, not being steered means something else took it). */
		boolean walked;

		Member(Mob mob, int slot) {
			this.mob = mob;
			this.slot = slot;
			this.health = mob.getHealth();
		}
	}

	/** A running scene. */
	public static final class Scene {
		final ServerLevel level;
		final Vec3 middle;
		final double radius;
		final int feetY;
		final List<Member> members = new ArrayList<>();
		final int count;
		final List<UUID> sharers;
		final boolean staged;
		final int roundTicks;
		final long started;
		Phase phase = Phase.ASSEMBLY;
		double angle;
		long roundFrom = -1;
		long stareUntil;
		long nextStep;
		String ended = "";

		Scene(ServerLevel level, Vec3 middle, double radius, int feetY, List<UUID> sharers, boolean staged, int roundTicks, long now, int count) {
			this.level = level;
			this.middle = middle;
			this.radius = radius;
			this.feetY = feetY;
			this.sharers = List.copyOf(sharers);
			this.staged = staged;
			this.roundTicks = roundTicks;
			this.started = now;
			this.nextStep = now;
			this.count = count;
		}

		public Vec3 middle() {
			return middle;
		}

		public double radius() {
			return radius;
		}

		public Phase phase() {
			return phase;
		}

		public List<Mob> mobs() {
			return members.stream().map(m -> m.mob).toList();
		}

		/** Where slot {@code i} is now. */
		public Vec3 slotPos(int i) {
			return Ring.at(middle, radius, Ring.slot(angle, i, count), middle.y);
		}

		/** The slot of this mob, or -1. */
		public int slotOf(Mob mob) {
			for (Member m : members) {
				if (m.mob == mob) {
					return m.slot;
				}
			}
			return -1;
		}

		public int count() {
			return count;
		}

		public List<UUID> sharers() {
			return sharers;
		}
	}

	/** Bared ground still to come: a scene's way round, once out of view. */
	record Print(ResourceKey<Level> dimension, Vec3 middle, double radius, int feetY, int cells, long until) {
	}

	private final List<Scene> scenes = new ArrayList<>();
	private final List<Print> prints = new ArrayList<>();
	/** The fake's held animals (no scene of their own), so the switch lets them go too (R12-33); the oldest are forgotten past 64. */
	private final List<Mob> fakes = new ArrayList<>();

	/** The fake held this one: {@link #clear} (the switch, a stop) lets it go. */
	public void heldFake(Mob mob) {
		fakes.removeIf(m -> m.isRemoved() || !MobTamperImpl.INSTANCE.isTampered(m));
		if (fakes.size() >= 64) {
			fakes.removeFirst();
		}
		fakes.add(mob);
	}

	/** The fake's animals still held now (tests). */
	public int fakesHeld() {
		return (int) fakes.stream().filter(m -> !m.isRemoved() && MobTamperImpl.INSTANCE.isTampered(m)).count();
	}
	/** Who takes part, for the break: the live watch, or a test's. */
	@Nullable PlayerWatch watch;
	/** Whether a box is out of everyone's view (the bared ground waits for it too); a test swaps in its own viewpoints. */
	BiPredicate<ServerLevel, AABB> outOfView = (level, box) -> Services.traces().isOutOfView(level, box);
	/** The trace service the ground goes through; a test may swap in its own. */
	@Nullable TraceService traces;

	/** A separate set of scenes, ticked by its owner (tests); the game's are {@link #LIVE}. */
	public Rings() {
	}

	/** This set with its own view of who takes part and what is out of view (tests). */
	public Rings with(PlayerWatch watch, BiPredicate<ServerLevel, AABB> outOfView) {
		this.watch = watch;
		this.outOfView = outOfView;
		return this;
	}

	private PlayerWatch watch() {
		return watch != null ? watch : Services.watch();
	}

	private TraceService traces() {
		return traces != null ? traces : Services.traces();
	}

	// --- the place ---

	/** Where a scene can go: its middle (feet height, a cell's centre), its radius and the feet Y round it. */
	public record Site(Vec3 middle, double radius, int feetY) {
	}

	/**
	 * A site for {@code n} animals round {@code near} (the herd's middle): the middle on a grass cell if one is within 3,
	 * every point of the way round on ground within 1 of the middle's (no water, nothing over 1 high, two free cells over
	 * it), tried at the herd's middle and then up to 3 blocks off it. Empty if none.
	 */
	public static Optional<Site> site(ServerLevel level, Vec3 near, int n, double spacing) {
		double radius = Ring.radius(n, spacing);
		int bx = (int) Math.floor(near.x);
		int bz = (int) Math.floor(near.z);
		int by = (int) Math.floor(near.y);
		List<int[]> tries = new ArrayList<>();
		for (int d = 0; d <= 3; d++) {
			for (int dx = -d; dx <= d; dx++) {
				for (int dz = -d; dz <= d; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == d) {
						tries.add(new int[] {dx, dz});
					}
				}
			}
		}
		// A grass middle first (the bare patch in the middle), then any.
		for (boolean grassOnly : new boolean[] {true, false}) {
			for (int[] t : tries) {
				int x = bx + t[0];
				int z = bz + t[1];
				OptionalInt feet = Herds.feet(level, x, z, by, 3, 4);
				if (feet.isEmpty()) {
					continue;
				}
				if (grassOnly && !Prints.isGrass(level.getBlockState(new BlockPos(x, feet.getAsInt() - 1, z)))) {
					continue;
				}
				Vec3 middle = new Vec3(x + 0.5, feet.getAsInt(), z + 0.5);
				if (wayRoundClear(level, middle, radius, feet.getAsInt())) {
					return Optional.of(new Site(middle, radius, feet.getAsInt()));
				}
			}
		}
		return Optional.empty();
	}

	/** Every point of the way round (each half block, a little inside and outside too) stands within 1 of {@code feetY}, each step at most 1. */
	static boolean wayRoundClear(ServerLevel level, Vec3 middle, double radius, int feetY) {
		for (double r : new double[] {radius - 0.8, radius, radius + 0.8}) {
			int samples = Math.max(24, (int) Math.ceil(2.0 * Math.PI * r * 2.0));
			int last = feetY;
			for (int i = 0; i <= samples; i++) {
				double a = 2.0 * Math.PI * i / samples;
				int x = (int) Math.floor(middle.x + Math.cos(a) * r);
				int z = (int) Math.floor(middle.z + Math.sin(a) * r);
				OptionalInt feet = Herds.feet(level, x, z, feetY, 1, 1);
				if (feet.isEmpty() || Math.abs(feet.getAsInt() - feetY) > 1 || Math.abs(feet.getAsInt() - last) > 1) {
					return false;
				}
				last = feet.getAsInt();
			}
		}
		return true;
	}

	// --- starting ---

	/**
	 * Starts a scene of these animals at this site, bound to {@code sharers} (empty for the gallery's staging), going
	 * round for {@code roundTicks} after its assembly. {@code staged}: the gallery's (nobody breaks it, it bares nothing).
	 * The animals that cannot be walked (refused by the tamper) are left out; under {@link #MIN_LEFT} it does not start.
	 * Returns the scene, or empty.
	 */
	public Optional<Scene> start(ServerLevel level, Site site, List<? extends Mob> animals, List<UUID> sharers, int roundTicks, boolean staged) {
		long now = level.getServer().getTickCount();
		List<Mob> usable = new ArrayList<>();
		for (Mob mob : animals) {
			if (mob.isAlive() && !mob.isRemoved() && MobTamperImpl.INSTANCE.steerRefusal(mob, SelfCreated.enabled(),
					MobTamperImpl.INSTANCE.steeredCount() + usable.size()) == null) {
				usable.add(mob);
			}
		}
		if (usable.size() < Math.min(MIN_LEFT, animals.size()) || usable.isEmpty()) {
			return Optional.empty();
		}
		double[] angles = new double[usable.size()];
		for (int i = 0; i < usable.size(); i++) {
			angles[i] = Ring.angleOf(site.middle(), usable.get(i).position());
		}
		double phase = angles[0];
		int[] slots = Ring.assign(angles, phase);
		Scene scene = new Scene(level, site.middle(), site.radius(), site.feetY(), sharers, staged, roundTicks, now, usable.size());
		scene.angle = phase;
		for (int i = 0; i < usable.size(); i++) {
			scene.members.add(new Member(usable.get(i), slots[i]));
		}
		scenes.add(scene);
		step(scene, now);
		if (scene.members.size() < Math.min(MIN_LEFT, scene.count)) {
			end(scene, "too few could be walked", now);
			scenes.remove(scene);
			return Optional.empty();
		}
		return Optional.of(scene);
	}

	// --- the tick ---

	/** Every server tick: each scene steps every {@link #EVERY} ticks; bared ground is tried every {@link #PRINT_EVERY}. */
	public void tick(MinecraftServer server) {
		if (scenes.isEmpty() && prints.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Scene> it = scenes.iterator(); it.hasNext();) {
			Scene scene = it.next();
			if (now >= scene.nextStep) {
				scene.nextStep = now + EVERY;
				step(scene, now);
			}
			if (scene.phase == Phase.DONE) {
				it.remove();
			}
		}
		if (!prints.isEmpty() && now % PRINT_EVERY == 0) {
			tryPrints(server, now);
		}
	}

	/** One step of a scene. */
	void step(Scene scene, long now) {
		if (scene.phase == Phase.DONE) {
			return;
		}
		if (!SelfCreated.enabled()) {
			end(scene, "the layer is off", now);
			return;
		}
		if (scene.phase == Phase.STARE) {
			if (now >= scene.stareUntil) {
				end(scene, "the stare is over", now);
			}
			return;
		}
		drop(scene);
		if (scene.members.size() < Math.min(MIN_LEFT, scene.count)) {
			end(scene, "too few left", now);
			return;
		}
		if (!scene.staged) {
			ServerPlayer near = breaker(scene);
			if (near != null) {
				stare(scene, near, now);
				return;
			}
		}
		if (scene.phase == Phase.ASSEMBLY) {
			boolean inPlace = true;
			for (Member m : scene.members) {
				Vec3 slot = scene.slotPos(m.slot);
				if (Herds.flatSqr(m.mob.position(), slot) > IN_PLACE * IN_PLACE) {
					inPlace = false;
				}
			}
			if (inPlace || now - scene.started >= ASSEMBLY_TICKS) {
				scene.phase = Phase.ROUND;
				scene.roundFrom = now;
			}
		}
		if (scene.phase == Phase.ROUND) {
			if (now - scene.roundFrom >= scene.roundTicks) {
				end(scene, "its time ran out", now);
				return;
			}
			scene.angle += Ring.turn(scene.radius, EVERY);
		}
		for (Iterator<Member> it = scene.members.iterator(); it.hasNext();) {
			Member m = it.next();
			if (!walk(scene, m)) {
				it.remove();
			}
		}
	}

	/** Walks one animal this step: to its slot (assembly) or to the point ahead of it at its keeping speed. False: it drops out. */
	private boolean walk(Scene scene, Member m) {
		Mob mob = m.mob;
		double slot = Ring.slot(scene.angle, m.slot, scene.count);
		Vec3 to;
		double factor;
		double attribute = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
		if (scene.phase == Phase.ASSEMBLY) {
			to = Ring.at(scene.middle, scene.radius, slot, scene.middle.y);
			factor = ASSEMBLY_SPEED;
		} else {
			to = Ring.pointAhead(scene.middle, scene.radius, slot, scene.middle.y);
			double at = Ring.angleOf(scene.middle, mob.position());
			factor = Ring.factorFor(Ring.wantedSpeed(Ring.behindBy(scene.radius, at, slot)), attribute);
		}
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		tamper.silence(mob, HOLD);
		if (!tamper.steer(mob, to, factor, HOLD)) {
			tamper.release(mob);
			return false;
		}
		m.walked = true;
		if (!scene.sharers.isEmpty()) {
			tamper.bind(mob, scene.sharers);
		}
		return true;
	}

	/** Takes out every animal hurt, fed, leashed, ridden, gone or taken by anything else; those still held are let go. */
	private static void drop(Scene scene) {
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		for (Iterator<Member> it = scene.members.iterator(); it.hasNext();) {
			Member m = it.next();
			Mob mob = m.mob;
			boolean gone = !mob.isAlive() || mob.isRemoved() || mob.level() != scene.level;
			boolean hurt = mob.getHealth() < m.health || mob.hurtTime > 0;
			boolean fed = mob instanceof Animal animal && animal.isInLove();
			boolean held = mob.isLeashed() || mob.isPassenger() || mob.isVehicle();
			boolean lost = m.walked && scene.phase != Phase.STARE && !tamper.isSteered(mob);
			if (gone || hurt || fed || held || lost) {
				if (!gone && !lost) {
					tamper.release(mob);
				}
				it.remove();
			} else {
				m.health = mob.getHealth();
			}
		}
	}

	/** Anyone (a living participant in the scene's level) within the break distance of the middle, the nearest; or null. */
	private @Nullable ServerPlayer breaker(Scene scene) {
		double reach = Sc21Config.get().breakDistance;
		ServerPlayer nearest = null;
		double best = reach * reach;
		for (ServerPlayer player : Herds.here(watch(), scene.level)) {
			double d = Herds.flatSqr(player.position(), scene.middle) + Math.pow(player.getY() - scene.middle.y, 2);
			if (d <= best) {
				best = d;
				nearest = player;
			}
		}
		return nearest;
	}

	/** Every animal stops and faces {@code player} for the stare, silent; then they are let go ({@link #step}). */
	void stare(Scene scene, ServerPlayer player, long now) {
		int ticks = Math.max(1, (int) Math.round(Sc21Config.get().stareSeconds * 20.0));
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		Vec3 eye = player.getEyePosition();
		for (Member m : scene.members) {
			tamper.freeze(m.mob, ticks + EVERY);
			tamper.face(m.mob, eye, ticks + EVERY);
			tamper.silence(m.mob, ticks + EVERY);
			if (!scene.sharers.isEmpty()) {
				tamper.bind(m.mob, scene.sharers);
			}
		}
		scene.phase = Phase.STARE;
		scene.stareUntil = now + ticks;
	}

	/** Ends a scene: every animal still held is let go where it is; bared ground follows if it went round long enough. */
	void end(Scene scene, String why, long now) {
		for (Member m : scene.members) {
			MobTamperImpl.INSTANCE.release(m.mob);
		}
		boolean wentRound = scene.roundFrom >= 0 && now - scene.roundFrom >= PRINT_AFTER;
		if (wentRound && !scene.staged) {
			prints.add(new Print(scene.level.dimension(), scene.middle, scene.radius, scene.feetY, Sc21Config.get().printCells, now + PRINT_FOR));
		}
		scene.members.clear();
		scene.phase = Phase.DONE;
		scene.ended = why;
		A1016_02.LOGGER.debug("[a1016] sc_21: ended ({})", why);
	}

	// --- the bared ground ---

	/** Tries every bared ground still to come now (tests; the tick does it every {@link #PRINT_EVERY}). */
	public void tryPrintsNow(MinecraftServer server) {
		tryPrints(server, server.getTickCount());
	}

	private void tryPrints(MinecraftServer server, long now) {
		for (Iterator<Print> it = prints.iterator(); it.hasNext();) {
			Print print = it.next();
			ServerLevel level = server.getLevel(print.dimension());
			if (level == null || now > print.until() || !SelfCreated.enabled()) {
				it.remove();
				continue;
			}
			AABB area = new AABB(print.middle(), print.middle()).inflate(print.radius() + 1.0, 2.0, print.radius() + 1.0).move(0, -0.5, 0);
			Result result = outOfView.test(level, area) ? bare(level, print.middle(), print.radius(), print.feetY(), print.cells(), traces()) : Result.WAIT;
			if (result != Result.WAIT) {
				it.remove();
			}
		}
	}

	/** What a try at the bared ground did. */
	public enum Result {
		DONE, NOTHING, WAIT
	}

	/**
	 * Bares the way round ({@link Ring#cells}, at most {@code cells}) and the middle cell in one ledgered batch through
	 * {@code traces}: grass to dirt with whatever grew on it gone ({@code Prints.at}); nothing else is touched. WAIT when
	 * the batch was refused (in view), NOTHING when there is no grass to bare (or a column not loaded).
	 */
	public static Result bare(ServerLevel level, Vec3 middle, double radius, int feetY, int cells, TraceService traces) {
		List<Prints.Edit> edits = new ArrayList<>();
		Collection<BlockPos> ground = new LinkedHashSet<>();
		List<BlockPos> way = new ArrayList<>(Ring.cells(middle, radius, feetY - 1, cells));
		way.add(BlockPos.containing(middle.x, feetY - 1, middle.z));
		for (BlockPos cell : way) {
			if (!level.isLoaded(cell)) {
				return Result.WAIT;
			}
			BlockPos at = Prints.ground(level, cell.getX(), cell.getZ(), feetY - 1);
			if (at != null && Math.abs(at.getY() - (feetY - 1)) <= 1 && ground.add(at)) {
				Prints.addAll(edits, Prints.at(level, at));
			}
		}
		if (edits.isEmpty()) {
			return Result.NOTHING;
		}
		TraceBatch batch = traces.batch(level, CAUSE);
		Prints.queue(batch, edits);
		return batch.commit() ? Result.DONE : Result.WAIT;
	}

	// --- the switch, the stop, debug ---

	/** Every scene ends now (its animals let go) and nothing more is bared. */
	public void clear() {
		for (Scene scene : new ArrayList<>(scenes)) {
			for (Member m : scene.members) {
				MobTamperImpl.INSTANCE.release(m.mob);
			}
			scene.members.clear();
			scene.phase = Phase.DONE;
		}
		scenes.clear();
		prints.clear();
		for (Mob mob : fakes) {
			if (!mob.isRemoved() && MobTamperImpl.INSTANCE.isTampered(mob)) {
				MobTamperImpl.INSTANCE.release(mob);
			}
		}
		fakes.clear();
	}

	/** True while any scene runs. */
	public boolean running() {
		return !scenes.isEmpty();
	}

	/** The scenes now (a copy). */
	public List<Scene> scenes() {
		return List.copyOf(scenes);
	}

	/** How many bared rounds are still to come. */
	public int printsWaiting() {
		return prints.size();
	}

	/** The switch's revert: scenes end now; every bared cell goes back through the ledger. Returns how many wait (in view, not loaded). */
	public int revert(MinecraftServer server) {
		clear();
		return giveBack(server);
	}

	/** Gives back every ledgered edit of {@link #CAUSE} that is loaded and out of view (newest first). Returns how many wait. */
	public static int giveBack(MinecraftServer server) {
		int waiting = 0;
		for (int pass = 0; pass < 3; pass++) {
			List<TraceLedger.Entry> mine = TraceLedger.get(server).entries().stream()
					.filter(e -> e.cause().equals(CAUSE) || e.cause().startsWith(CAUSE + "/")).toList();
			int given = 0;
			int blocked = 0;
			waiting = 0;
			for (int i = mine.size() - 1; i >= 0; i--) {
				TraceLedger.Entry entry = mine.get(i);
				ServerLevel level = server.getLevel(entry.pos().dimension());
				if (level == null) {
					continue;
				}
				if (!level.isLoaded(entry.pos().pos())) {
					waiting++;
					continue;
				}
				switch (Services.traces().undo(level, entry)) {
					case DONE -> given++;
					case BLOCKED -> blocked++;
					case IN_VIEW, UNLOADED -> waiting++;
				}
			}
			if (given == 0 || blocked == 0) {
				break;
			}
		}
		return waiting;
	}

	/** Lines for {@code /a1016 sc 21}. */
	public List<String> describe() {
		List<String> lines = new ArrayList<>();
		for (Scene s : scenes) {
			lines.add(String.format(Locale.ROOT, "running at %.0f %.0f %.0f, radius %.1f, %s, %d of %d", s.middle.x, s.middle.y, s.middle.z, s.radius,
					s.phase.name().toLowerCase(Locale.ROOT), s.members.size(), s.count));
		}
		if (!prints.isEmpty()) {
			lines.add(prints.size() + " ground edit(s) waiting to be out of view");
		}
		return lines;
	}

	/** A block state is grass a scene bares (for tests). */
	public static boolean bares(BlockState state) {
		return Prints.isGrass(state);
	}
}
