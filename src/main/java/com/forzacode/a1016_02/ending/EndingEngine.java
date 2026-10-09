package com.forzacode.a1016_02.ending;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.ending.EndingBeats.A;
import com.forzacode.a1016_02.ending.EndingBeats.B;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Endings A, B and C: when a path commits (Stage 4, D-006), the third-death rule, hardcore's one death, each path's
 * beats, C's reversal on naming, and the debug step. Everything it changes in the world goes through
 * {@link TraceService} and {@link MobTamper} (by way of {@link EndingPorts}) and only out of view; it never spawns a
 * mob and never damages, targets or touches the player. Accidents are the accident planner's own traps (deniable,
 * one clue each). Server thread only.
 */
public final class EndingEngine {
	/** Ledger cause of the house being emptied (Ending D's undo puts it back). */
	public static final String CAUSE_HOUSE = "ending:b/house";
	/** Set while Ending A's last sighting may happen (read by entity's gates). */
	public static final String LAST_SIGHTING_FLAG = "ending:last_sighting";
	/** Set by entity once the last sighting was seen and he is gone. */
	public static final String LAST_SIGHTING_SEEN_FLAG = "entity:last_sighting_seen";
	/** The path, mirrored for other workstreams: {@code ending:path=A}. */
	public static final String PATH_FLAG = "ending:path=";
	/** Set once the story is over. */
	public static final String ENDED_FLAG = "ending:ended";

	private final EndingPorts ports;
	/** Not saved: when each kind of attempt last ran ({@code dayTicks}), so trap scans stay cheap. */
	private final Map<String, Long> lastTry = new HashMap<>();

	public EndingEngine(EndingPorts ports) {
		this.ports = ports;
	}

	public EndingPorts ports() {
		return ports;
	}

	/** Forgets the attempt throttles (server stop). */
	void reset() {
		lastTry.clear();
	}

	/**
	 * One moment of the engine's work.
	 *
	 * @param player the subject, or null if offline
	 * @param now    {@code GameClock.dayTicks}
	 * @param force  debug: waits are skipped and edits use the forced trace service
	 */
	public record Ctx(MinecraftServer server, @Nullable ServerPlayer player, HerobrineState state, EndingState data, EndingConfig cfg, long now,
			RandomSource random, boolean force) {
		long today() {
			return Math.floorDiv(now, GameClock.TICKS_PER_DAY);
		}

		double daysSince(long at) {
			return at == EndingState.NEVER ? Double.POSITIVE_INFINITY : (now - at) / (double) GameClock.TICKS_PER_DAY;
		}

		boolean playerActive() {
			return player != null && player.isAlive() && !player.isSpectator();
		}

		Ctx forced() {
			return new Ctx(server, player, state, data, cfg, now, random, true);
		}
	}

	// --- the periodic step ---

	/** The third-death rule for deaths the event missed, the commits, then the path's beat. */
	public void tick(Ctx c, EndingFacts facts) {
		EndingState data = c.data();
		if (data.ended()) {
			finals(c);
			return;
		}
		if (facts.markedDeaths() > data.deathsSeen()) {
			data.setDeathsSeen(facts.markedDeaths());
			thirdDeath(c, facts.markedDeaths());
		}
		maybeCommit(c, facts);
		run(c);
		// A's last beat survives a switch to B (its ordinary accident was the third marked death).
		if (data.path() != EndingPath.A && data.progress(EndingPath.A) == A.SIGN.ordinal()) {
			aSign(c);
		}
	}

	/** Commits a path if its rules hold: from NONE any of them; during A's false peace, B (or C before the accident). */
	void maybeCommit(Ctx c, EndingFacts f) {
		EndingState data = c.data();
		switch (data.path()) {
			case NONE -> EndingRules.decide(f, c.cfg(), ModConfig.pacing().obeyDays).ifPresent(d -> commit(c, d.path(), d.reason()));
			case A -> {
				int beat = data.progress(EndingPath.A);
				Optional<String> b = EndingRules.bReason(f, c.cfg());
				if (beat < A.SIGN.ordinal() && b.isPresent()) {
					commit(c, EndingPath.B, b.get() + ", during the false peace");
				} else if (beat <= A.QUIET.ordinal() && EndingRules.cWhy(f, c.cfg()).isEmpty()) {
					commit(c, EndingPath.C, "did what the team did, during the false peace");
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Commits a path: the old one's effects end, the new one starts at its first beat, and Stage 4 begins (D-006).
	 * NONE goes back to Stage 3 if Stage 4 had begun. D only gets the path and the stage; ending.d does the rest.
	 */
	public void commit(Ctx c, EndingPath path, String reason) {
		EndingState data = c.data();
		HerobrineState state = c.state();
		EndingPath old = data.path();
		leave(c, old, path);
		data.setPath(path, c.now(), reason);
		mirror(state, path);
		data.log("path " + old + " -> " + path + " (" + reason + ")");
		if (path == EndingPath.NONE) {
			if (state.stage() == Stage.REMOVAL) {
				state.setStage(c.server(), Stage.TELLING);
			}
			return;
		}
		state.setStage(c.server(), Stage.REMOVAL);
		switch (path) {
			case A -> enterA(c);
			case B -> enterB(c);
			case C -> enterC(c);
			default -> {
			}
		}
	}

	/** The old path's effects end before another starts. */
	private void leave(Ctx c, EndingPath old, EndingPath next) {
		HerobrineState state = c.state();
		if (old == EndingPath.A && !state.hasFlag(LAST_SIGHTING_SEEN_FLAG)) {
			state.setFlag(LAST_SIGHTING_FLAG, false);
		}
		if (old == EndingPath.B) {
			releaseWaiters(c);
		}
		if (old == EndingPath.C && next != EndingPath.NONE && next != EndingPath.C) {
			// Stage 4 stays, so atmosphere will not set its fog again: put back Stage 4's dusk fog.
			ports.setDuskFog(c.server(), ports.stageDuskFog(Stage.REMOVAL));
		}
		DirectorHooks.clearSilence(state);
		DirectorHooks.clearPace(state);
	}

	static void mirror(HerobrineState state, EndingPath path) {
		for (String flag : List.copyOf(state.flags())) {
			if (flag.startsWith(PATH_FLAG)) {
				state.setFlag(flag, false);
			}
		}
		if (path != EndingPath.NONE) {
			state.setFlag(PATH_FLAG + path.name(), true);
		}
	}

	private void run(Ctx c) {
		switch (c.data().path()) {
			case A -> runA(c);
			case B -> runB(c);
			case C -> runC(c);
			default -> {
			}
		}
	}

	/** After the story ended: only the last beats still waiting (the sign to the cross, F20) run, and waiters go. */
	private void finals(Ctx c) {
		if (c.data().progress(EndingPath.A) == A.SIGN.ordinal()) {
			aSign(c);
		}
		if (c.data().path() == EndingPath.B && c.data().progress(EndingPath.B) == B.RECORD.ordinal()) {
			bRecord(c);
		}
		if (!c.data().waiters().isEmpty()) {
			releaseWaiters(c);
		}
	}

	private void setBeat(Ctx c, EndingPath path, Enum<?> beat, String why) {
		c.data().setProgress(path, beat.ordinal(), c.now());
		c.data().log(path + ": " + beat.name().toLowerCase(Locale.ROOT) + (why.isEmpty() ? "" : " (" + why + ")"));
	}

	/**
	 * The story is over: nothing more is armed or moved, the doorway's mobs are let go, the pace multiplier ends and
	 * (by default) the director stays silent for good.
	 */
	void endStory(Ctx c, String why) {
		EndingState data = c.data();
		HerobrineState state = c.state();
		data.setEnded(c.now());
		state.setFlag(ENDED_FLAG, true);
		DirectorHooks.clearPace(state);
		if (c.cfg().silenceAfterEnd) {
			DirectorHooks.silenceForever(state);
		}
		releaseWaiters(c);
		ports.disarmTraps();
		data.log("the story ends: " + why);
	}

	// --- events ---

	/**
	 * A marked death of the subject. Hardcore: it ends the story (A's sign and B's record still follow). Otherwise it
	 * may be A's ordinary accident or B's final death, and the third one commits B whatever the path.
	 */
	public void onMarkedDeath(Ctx c, GlobalPos pos, int count, boolean hardcore) {
		EndingState data = c.data();
		boolean fresh = count > data.deathsSeen();
		data.setDeathsSeen(Math.max(data.deathsSeen(), count));
		if (data.ended()) {
			return;
		}
		EndingPath path = data.path();
		if (hardcore) {
			if (path == EndingPath.A) {
				data.setDeath(pos, c.now());
				setBeat(c, EndingPath.A, A.SIGN, "hardcore: this death was the ending");
			} else if (path == EndingPath.B) {
				toRecord(c, "hardcore: this death was the final one");
			}
			endStory(c, "hardcore: one marked death ends the story");
			return;
		}
		if (path == EndingPath.A && data.progress(EndingPath.A) == A.ACCIDENT.ordinal()) {
			data.setDeath(pos, c.now());
			setBeat(c, EndingPath.A, A.SIGN, "the ordinary accident");
		} else if (path == EndingPath.B && data.progress(EndingPath.B) == B.FINAL.ordinal() && (data.finalArmed() || insideFinal(c, pos))) {
			toRecord(c, "the final death, inside the copy");
		}
		if (fresh) {
			thirdDeath(c, count);
		}
	}

	/** The third marked death commits B, whatever the path. */
	private void thirdDeath(Ctx c, int count) {
		if (count >= c.cfg().thirdDeath && c.data().path() != EndingPath.B && !c.data().ended()) {
			commit(c, EndingPath.B, "marked death " + count + ", the third-death rule");
		}
	}

	/** A telling by the subject (already recorded). Naming him during C undoes it: "say his name once and it all starts again". */
	public void onTelling(Ctx c, boolean namesHim) {
		EndingState data = c.data();
		if (!namesHim || data.path() != EndingPath.C || data.ended()) {
			return;
		}
		DirectorHooks.clearSilence(c.state());
		data.resetSilenceWork();
		data.setPath(EndingPath.NONE, c.now(), "named him during C");
		mirror(c.state(), EndingPath.NONE);
		data.log("C: he was named once; it all starts again");
		if (c.state().stage() == Stage.REMOVAL) {
			c.state().setStage(c.server(), Stage.TELLING);
		}
	}

	// --- Ending A: "Stop." ---

	private void enterA(Ctx c) {
		ports.disarmTraps();
		c.state().setFlag(LAST_SIGHTING_FLAG, true);
		// The world goes quiet and vanilla again.
		DirectorHooks.silenceForever(c.state());
	}

	private void runA(Ctx c) {
		switch (EndingBeats.of(A.class, c.data().progress(EndingPath.A))) {
			case SIGHTING -> aSighting(c);
			case QUIET -> aQuiet(c);
			case ACCIDENT -> aAccident(c);
			case SIGN -> aSign(c);
			case DONE -> {
			}
		}
	}

	/** He is seen once, far off, walking away into the fog: {@code sighting_last_one}, through its own gates. */
	void aSighting(Ctx c) {
		EndingState data = c.data();
		EndingConfig cfg = c.cfg();
		if (c.state().hasFlag(LAST_SIGHTING_SEEN_FLAG)) {
			toQuiet(c, "he was seen walking away");
			return;
		}
		if (c.force()) {
			toQuiet(c, "debug");
			return;
		}
		if (c.daysSince(data.pathSince()) >= cfg.aSightingMaxDays) {
			toQuiet(c, "he was never seen; the quiet starts anyway");
			return;
		}
		if (!c.playerActive() || ports.figureOut(c.server())) {
			return;
		}
		if (data.sightingTries() >= cfg.aSightingTries) {
			toQuiet(c, "never seen after " + data.sightingTries() + " tries");
			return;
		}
		if (!mayTry(c, "a/sighting")) {
			return;
		}
		if (ports.lastSighting(c.player()) == FireResult.FIRED) {
			data.recordSighting(c.now());
			data.log("A: he walks away into the fog, far off (try " + data.sightingTries() + ")");
		}
	}

	/** Five to seven in-game days of real quiet. */
	private void toQuiet(Ctx c, String why) {
		EndingConfig cfg = c.cfg();
		int min = Math.max(0, cfg.aSilenceMinDays);
		int max = Math.max(min, cfg.aSilenceMaxDays);
		long until = c.today() + min + c.random().nextInt(max - min + 1);
		c.data().setSilenceUntilDay(until);
		DirectorHooks.silenceUntil(c.state(), until);
		setBeat(c, EndingPath.A, A.QUIET, why + "; quiet until day " + until);
	}

	void aQuiet(Ctx c) {
		if (c.force() || c.today() >= c.data().silenceUntilDay()) {
			// Only the one accident comes now.
			DirectorHooks.silenceForever(c.state());
			setBeat(c, EndingPath.A, A.ACCIDENT, "the quiet is over");
		}
	}

	/** One ordinary accident in a place they trusted: a lava floor on their own mine route (the planner looks around them). */
	void aAccident(Ctx c) {
		EndingState data = c.data();
		if (!c.playerActive() || ports.trapArmed()) {
			return;
		}
		if (!c.force() && (data.lastArmAt() != EndingState.NEVER && c.daysSince(data.lastArmAt()) < c.cfg().aRearmDays || !mayTry(c, "a/accident"))) {
			return;
		}
		armFirst(c, c.cfg().aTraps).ifPresent(id -> {
			data.setLastArmAt(c.now());
			data.log("A: one ordinary accident in a place they trusted (" + id + ")");
		});
	}

	/** On that marked death: the "Stop." sign stands in front of the cross. */
	void aSign(Ctx c) {
		EndingState data = c.data();
		Optional<GlobalPos> death = data.deathPos();
		if (death.isEmpty()) {
			finishA(c, "no death to mark");
			return;
		}
		if (!ports.stopSignExists(c.server())) {
			finishA(c, "there is no \"Stop.\" sign to move");
			return;
		}
		Optional<GlobalPos> cross = ports.findCross(c.server(), death.get());
		if (cross.isPresent() && ports.moveStopSignToCross(c.server(), cross.get())) {
			finishA(c, "the \"Stop.\" sign stands in front of the cross at " + cross.get().pos().toShortString());
			return;
		}
		if (c.force() || c.daysSince(data.deathAt()) >= c.cfg().aSignMaxDays) {
			finishA(c, cross.isEmpty() ? "no cross was found" : "the sign could not be moved unseen");
		}
	}

	private void finishA(Ctx c, String why) {
		setBeat(c, EndingPath.A, A.DONE, why);
		if (c.data().path() == EndingPath.A && !c.data().ended()) {
			endStory(c, "Ending A");
		}
	}

	// --- Ending B: "Removed" ---

	private void enterB(Ctx c) {
		DirectorHooks.pace(c.state(), c.cfg().bPaceMultiplier);
		GlobalPos house = c.player() != null ? ports.watch().base(c.player()).orElse(null) : null;
		c.data().setHouse(house != null ? house : c.data().home().orElse(null));
	}

	private void runB(Ctx c) {
		B beat = EndingBeats.of(B.class, c.data().progress(EndingPath.B));
		if (beat.ordinal() < B.DONE.ordinal()) {
			placeF20(c);
		}
		if (beat.ordinal() < B.FINAL.ordinal()) {
			homeAccident(c);
		}
		if (beat.ordinal() >= B.DOORWAY.ordinal()) {
			tendWaiters(c);
		}
		switch (beat) {
			case ESCALATE -> bEscalate(c);
			case EMPTY_HOUSE -> bEmpty(c);
			case FINISH_COPY -> bCopy(c);
			case DOORWAY -> bDoorway(c);
			case FINAL -> bFinal(c);
			case RECORD -> bRecord(c);
			case DONE -> {
			}
		}
	}

	void bEscalate(Ctx c) {
		if (c.force() || c.daysSince(c.data().beatSince()) >= c.cfg().bEscalateDays) {
			setBeat(c, EndingPath.B, B.EMPTY_HOUSE, "the accidents came closer");
		}
	}

	/** The real house is emptied out of view: its furnishings go in one batch; the shell stays. */
	void bEmpty(Ctx c) {
		EndingState data = c.data();
		EndingConfig cfg = c.cfg();
		if (data.house().isEmpty() && c.player() != null) {
			ports.watch().base(c.player()).ifPresent(data::setHouse);
		}
		Optional<GlobalPos> house = data.house();
		ServerLevel level = house.map(h -> c.server().getLevel(h.dimension())).orElse(null);
		if (house.isEmpty() || level == null) {
			setBeat(c, EndingPath.B, B.FINISH_COPY, "no house to empty");
			return;
		}
		BlockPos home = house.get().pos();
		int r = cfg.houseRadius;
		if (!level.hasChunksAt(home.offset(-r, 0, -r), home.offset(r, 0, r))) {
			return;
		}
		List<BlockPos> inside = HouseScan.interior(level, home, cfg, ports.watch(), keep(c.state(), level));
		if (inside.isEmpty()) {
			setBeat(c, EndingPath.B, B.FINISH_COPY, "the house is empty");
			return;
		}
		TraceService traces = c.force() ? ports.traces().forced() : ports.traces();
		if (c.force() || c.daysSince(data.beatSince()) < cfg.bEmptyBatchDays) {
			TraceBatch batch = traces.batch(level, CAUSE_HOUSE);
			inside.forEach(batch::remove);
			if (batch.commit()) {
				setBeat(c, EndingPath.B, B.FINISH_COPY, "the house was emptied out of view (" + inside.size() + " things); the shell stays");
				return;
			}
			if (!c.force()) {
				return;
			}
		}
		// After a day of trying it whole: whatever is out of view now, a piece at a time.
		int taken = 0;
		for (BlockPos pos : inside) {
			if (traces.remove(level, pos, CAUSE_HOUSE)) {
				taken++;
			}
		}
		if (taken == inside.size()) {
			setBeat(c, EndingPath.B, B.FINISH_COPY, "the house was emptied piece by piece (" + taken + " things)");
		} else if (c.force() || c.daysSince(data.beatSince()) >= cfg.bEmptyMaxDays) {
			setBeat(c, EndingPath.B, B.FINISH_COPY, (inside.size() - taken) + " things could never be taken unseen");
		} else if (taken > 0) {
			data.log("B: " + taken + " more things gone from the house");
		}
	}

	/** Fragments' spots: lore's, never emptied. */
	private static Set<BlockPos> keep(HerobrineState state, ServerLevel level) {
		Set<BlockPos> keep = new HashSet<>();
		state.fragmentsPlaced().values().stream().filter(p -> p.dimension().equals(level.dimension())).forEach(p -> keep.add(p.pos()));
		return keep;
	}

	/** The copy elsewhere gets finished. */
	void bCopy(Ctx c) {
		EndingState data = c.data();
		if (!ports.copyExists(c.server())) {
			setBeat(c, EndingPath.B, B.DOORWAY, "there is no copy elsewhere; the end comes home");
			return;
		}
		if (!data.copyRequested()) {
			boolean asked = ports.finishCopy(c.server());
			data.setCopyRequested(true);
			data.log("B: the copy elsewhere is being finished" + (asked ? "" : " (refused)"));
		}
		if (ports.copyFinished(c.server()) || c.force() || c.daysSince(data.beatSince()) >= c.cfg().bCopyMaxDays) {
			setBeat(c, EndingPath.B, B.DOORWAY, ports.copyFinished(c.server()) ? "the copy is finished" : "the copy is as finished as it gets");
		}
	}

	/** Mobs wait in the doorway: existing ones, moved there out of view, frozen, silent, facing the door. */
	void bDoorway(Ctx c) {
		EndingState data = c.data();
		EndingConfig cfg = c.cfg();
		Optional<GlobalPos> house = data.house();
		ServerLevel level = house.map(h -> c.server().getLevel(h.dimension())).orElse(null);
		if (house.isPresent() && level != null && level.isLoaded(house.get().pos()) && data.waiters().size() < cfg.bDoorwayMobs) {
			Set<BlockPos> taken = new HashSet<>();
			data.waiters().forEach(w -> taken.add(w.spot()));
			List<HouseScan.Doorway> spots = new ArrayList<>(HouseScan.doorways(level, house.get().pos(), cfg, ports.watch()));
			spots.removeIf(d -> taken.contains(d.spot()));
			for (Mob mob : spots.isEmpty() ? List.<Mob>of() : doorwayCandidates(level, house.get().pos(), cfg)) {
				if (data.waiters().size() >= cfg.bDoorwayMobs || spots.isEmpty()) {
					break;
				}
				for (HouseScan.Doorway spot : List.copyOf(spots)) {
					if (ports.mobs().moveOutOfView(mob, spot.spot())) {
						hold(mob, spot.door(), cfg);
						data.addWaiter(new EndingState.Waiter(mob.getUUID(), spot.spot(), spot.door(), c.now()));
						data.log("B: a " + BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath() + " waits in the doorway at "
								+ spot.spot().toShortString());
						spots.remove(spot);
						break;
					}
				}
			}
		}
		if (data.waiters().size() >= cfg.bDoorwayMobs || c.force() || c.daysSince(data.beatSince()) >= cfg.bDoorwayMaxDays) {
			setBeat(c, EndingPath.B, B.FINAL, data.waiters().size() + " waiting in the doorway");
		}
	}

	/** Existing mobs of the configured kinds around the house, nearest first. Never spawned; only moved. */
	List<Mob> doorwayCandidates(ServerLevel level, BlockPos home, EndingConfig cfg) {
		Set<String> types = Set.copyOf(cfg.bDoorwayMobTypes);
		List<Mob> found = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(home).inflate(cfg.bDoorwayMobRadius),
				m -> m.isAlive() && !m.hasCustomName() && !m.isPassenger() && !m.isVehicle() && !m.isLeashed()
						&& types.contains(BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString()) && !ports.mobs().isTampered(m)));
		found.sort((a, b) -> Double.compare(a.distanceToSqr(Vec3.atCenterOf(home)), b.distanceToSqr(Vec3.atCenterOf(home))));
		return found;
	}

	private void hold(Mob mob, BlockPos door, EndingConfig cfg) {
		int ticks = (int) Math.min(Integer.MAX_VALUE, cfg.checkTicks() * 3 + 40);
		MobTamper mobs = ports.mobs();
		mobs.freeze(mob, ticks);
		mobs.silence(mob, ticks);
		mobs.face(mob, Vec3.atCenterOf(door), ticks);
	}

	/** The doorway's mobs keep waiting until the player comes close (then the game takes over) or their time is up. */
	void tendWaiters(Ctx c) {
		EndingState data = c.data();
		if (data.waiters().isEmpty()) {
			return;
		}
		ServerLevel level = data.house().map(h -> c.server().getLevel(h.dimension())).orElse(null);
		for (EndingState.Waiter waiter : List.copyOf(data.waiters())) {
			Entity entity = level == null ? null : level.getEntity(waiter.mob());
			if (!(entity instanceof Mob mob) || !mob.isAlive()) {
				if (level == null || level.isLoaded(waiter.spot()) || c.daysSince(waiter.since()) >= c.cfg().bDoorwayHoldDays) {
					data.removeWaiter(waiter);
				}
				continue;
			}
			boolean near = c.player() != null && c.player().level() == level
					&& c.player().position().distanceTo(Vec3.atBottomCenterOf(waiter.spot())) <= c.cfg().bDoorwayReleaseBlocks;
			if (near || c.daysSince(waiter.since()) >= c.cfg().bDoorwayHoldDays) {
				ports.mobs().release(mob);
				data.removeWaiter(waiter);
				data.log("B: the one in the doorway " + (near ? "stops waiting" : "wanders off"));
			} else {
				hold(mob, waiter.door(), c.cfg());
			}
		}
	}

	private void releaseWaiters(Ctx c) {
		EndingState data = c.data();
		ServerLevel level = data.house().map(h -> c.server().getLevel(h.dimension())).orElse(null);
		for (EndingState.Waiter waiter : List.copyOf(data.waiters())) {
			if (level != null && level.getEntity(waiter.mob()) instanceof Mob mob) {
				ports.mobs().release(mob);
			}
			data.removeWaiter(waiter);
		}
	}

	/** Accidents closer to home: while the player is near their base, the ending arms one there itself. */
	void homeAccident(Ctx c) {
		EndingState data = c.data();
		EndingConfig cfg = c.cfg();
		Optional<GlobalPos> house = data.house();
		if (!c.playerActive() || house.isEmpty() || ports.trapArmed()) {
			return;
		}
		if (!c.player().level().dimension().equals(house.get().dimension()) || horizontal(c.player().blockPosition(), house.get().pos()) > cfg.bHomeRadius) {
			return;
		}
		if (data.lastHomeArmAt() != EndingState.NEVER && c.daysSince(data.lastHomeArmAt()) < cfg.bHomeArmDays) {
			return;
		}
		if (!c.force() && !mayTry(c, "b/home")) {
			return;
		}
		List<String> order = new ArrayList<>(cfg.bHomeTraps);
		if (!order.isEmpty()) {
			Collections.rotate(order, c.random().nextInt(order.size()));
		}
		armFirst(c, order).ifPresent(id -> {
			data.setLastHomeArmAt(c.now());
			data.log("B: an accident close to home (" + id + ")");
		});
	}

	/** The final death happens inside the copy (or in the emptied house if there is no copy). */
	void bFinal(Ctx c) {
		EndingState data = c.data();
		EndingConfig cfg = c.cfg();
		if (data.finalArmed() && !ports.trapArmed()) {
			data.setFinalArmed(false);
			data.log("B: the final trap's time ran out; it waits for them again");
		}
		if (data.finalArmed() || !c.playerActive() || ports.trapArmed()) {
			return;
		}
		Optional<GlobalPos> target = finalTarget(c);
		if (target.isEmpty()) {
			return;
		}
		if (!c.force()) {
			BlockPos at = c.player().blockPosition();
			if (!c.player().level().dimension().equals(target.get().dimension()) || horizontal(at, target.get().pos()) > cfg.bFinalRadius
					|| Math.abs(at.getY() - target.get().pos().getY()) > cfg.bFinalRadius || !mayTry(c, "b/final")) {
				return;
			}
		}
		armFirst(c, cfg.bFinalTraps).ifPresent(id -> {
			data.setFinalArmed(true);
			data.setLastArmAt(c.now());
			data.log("B: the final trap, inside the copy (" + id + ")");
		});
	}

	/** Within {@code bFinalRadius} of the copy (or the house): any marked death there is the final one. */
	private boolean insideFinal(Ctx c, GlobalPos pos) {
		Optional<GlobalPos> target = finalTarget(c);
		int r = c.cfg().bFinalRadius;
		return target.isPresent() && target.get().dimension().equals(pos.dimension()) && horizontal(target.get().pos(), pos.pos()) <= r
				&& Math.abs(target.get().pos().getY() - pos.pos().getY()) <= r;
	}

	/** The copy's middle if there is a copy, else the house. */
	Optional<GlobalPos> finalTarget(Ctx c) {
		if (ports.copyExists(c.server())) {
			Optional<GlobalPos> site = ports.copySite(c.server());
			if (site.isPresent()) {
				return site;
			}
		}
		return c.data().house();
	}

	/** F10 gains its last line, "* removed [PLAYER NAME]". */
	private void toRecord(Ctx c, String why) {
		ports.finishF10(c.server());
		setBeat(c, EndingPath.B, B.RECORD, why + "; F10 is finished");
	}

	/** F20 lies under F10. Then B is done. */
	void bRecord(Ctx c) {
		placeF20(c);
		EndingState data = c.data();
		if (data.f20Placed() || c.force() || c.daysSince(data.beatSince()) >= c.cfg().bRecordMaxDays) {
			setBeat(c, EndingPath.B, B.DONE, data.f20Placed() ? "F20 lies under F10" : "F20 could not be placed");
			if (!data.ended()) {
				endStory(c, "Ending B");
			}
		}
	}

	/** F20 is for the Ending B path only: placed under F10 as soon as F10 is there. */
	private void placeF20(Ctx c) {
		EndingState data = c.data();
		if (!data.f20Placed() && ports.placeF20(c.server())) {
			data.setF20Placed(true);
			data.log("B: F20 lies under F10");
		}
	}

	// --- Ending C: "For the record" ---

	private void enterC(Ctx c) {
		ports.disarmTraps();
		DirectorHooks.silenceForever(c.state());
		ports.setDuskFog(c.server(), 0.0F);
	}

	/** The world stays quiet: the silence and the clear dusk hold until he is named. */
	private void runC(Ctx c) {
		if (DirectorHooks.silence(c.state()).orElse(0L) != DirectorHooks.FOREVER) {
			DirectorHooks.silenceForever(c.state());
		}
		if (c.state().effects().duskFogLevel() > 0.0F) {
			ports.setDuskFog(c.server(), 0.0F);
		}
	}

	// --- debug ---

	/** {@code /a1016 ending step}: the current path's beat runs now, waits skipped, and moves on one beat. */
	public List<String> step(Ctx c) {
		Ctx f = c.forced();
		EndingState data = f.data();
		EndingPath path = data.path();
		boolean pendingSign = path != EndingPath.A && data.progress(EndingPath.A) == A.SIGN.ordinal();
		if (pendingSign) {
			aSign(f);
			return List.of("[a1016] ending: A's sign beat ran (" + EndingBeats.name(EndingPath.A, data.progress(EndingPath.A)) + ")");
		}
		if (path == EndingPath.NONE) {
			return List.of("[a1016] ending: no path yet; use /a1016 ending path <A|B|C>");
		}
		if (path == EndingPath.D) {
			return List.of("[a1016] ending: Ending D is run by ending.d");
		}
		if (path == EndingPath.C) {
			return List.of("[a1016] ending: C is silent until he is named; nothing to step");
		}
		String before = EndingBeats.name(path, data.progress(path));
		if (path == EndingPath.A) {
			stepA(f);
		} else {
			stepB(f);
		}
		return List.of("[a1016] ending: " + path + " " + before + " -> " + EndingBeats.name(path, data.progress(path))
				+ (data.ended() ? " (the story has ended)" : ""));
	}

	private void stepA(Ctx f) {
		switch (EndingBeats.of(A.class, f.data().progress(EndingPath.A))) {
			case SIGHTING -> aSighting(f);
			case QUIET -> aQuiet(f);
			case ACCIDENT -> {
				aAccident(f);
				GlobalPos at = f.player() != null ? GlobalPos.of(f.player().level().dimension(), f.player().blockPosition())
						: f.data().home().orElse(null);
				if (at != null) {
					f.data().setDeath(at, f.now());
				}
				setBeat(f, EndingPath.A, A.SIGN, "debug: as if the accident happened here");
			}
			case SIGN -> aSign(f);
			case DONE -> {
			}
		}
	}

	private void stepB(Ctx f) {
		switch (EndingBeats.of(B.class, f.data().progress(EndingPath.B))) {
			case ESCALATE -> bEscalate(f);
			case EMPTY_HOUSE -> bEmpty(f);
			case FINISH_COPY -> bCopy(f);
			case DOORWAY -> bDoorway(f);
			case FINAL -> {
				bFinal(f);
				toRecord(f, "debug: as if the final death happened");
			}
			case RECORD -> bRecord(f);
			case DONE -> {
			}
		}
	}

	// --- helpers ---

	private Optional<String> armFirst(Ctx c, List<String> ids) {
		for (String id : ids) {
			if (ports.armTrap(c.player(), id)) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}

	/** At most one attempt of this kind per {@code armRetrySeconds}. */
	private boolean mayTry(Ctx c, String key) {
		long gap = ModConfig.realTicks(c.cfg().armRetrySeconds);
		Long last = lastTry.get(key);
		if (last != null && c.now() >= last && c.now() - last < gap) {
			return false;
		}
		lastTry.put(key, c.now());
		return true;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
