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
import java.util.UUID;

import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.MobTamper;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.PlayerWatch;
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
 * <p>
 * With friends (D-058, D-063, D-064): one ending per world, and its beats are the world's (B's escalation, the pace,
 * the house, the copy). A beat that needs someone to happen around (a sighting, an accident, the final trap) picks
 * from {@link Ctx#players}, who are in it this very moment, and keeps nobody across ticks (D-062.1). Each player's
 * own marked deaths count for the third-death rule; in hardcore a marked death finishes that player's line, and the
 * story ends only once nobody is left in it.
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

	/**
	 * A doorway mob's hold lasts three checks plus this slack, so it never lapses between two renewals (a renewal
	 * margin, not pacing; the hold ends sooner whenever the player comes close or its time is up).
	 */
	private static final int HOLD_SLACK_TICKS = 40;

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
	 * One moment of the engine's work. Made fresh for each moment and never kept (D-062.1).
	 *
	 * @param players  who is in it now ({@link EndingApi#players}): online participants, alive, whose line is open, in
	 *                 first-join order; empty while nobody is
	 * @param now      {@code GameClock.dayTicks}
	 * @param play     {@code GameClock.playTicks(server)}, the world clock (real play time, for the pacing floors)
	 * @param force    debug: waits are skipped and edits use the forced trace service
	 * @param hardcore the world is hardcore: a marked death finishes that player's line (D-063)
	 */
	public record Ctx(MinecraftServer server, List<ServerPlayer> players, HerobrineState state, EndingState data, EndingConfig cfg, long now,
			long play, RandomSource random, boolean force, boolean hardcore) {
		public Ctx {
			players = List.copyOf(players);
		}

		long today() {
			return Math.floorDiv(now, GameClock.TICKS_PER_DAY);
		}

		double daysSince(long at) {
			return at == EndingState.NEVER ? Double.POSITIVE_INFINITY : (now - at) / (double) GameClock.TICKS_PER_DAY;
		}

		/** The first one in it (join order), if anyone is. */
		Optional<ServerPlayer> first() {
			return players.stream().findFirst();
		}

		Ctx forced() {
			return new Ctx(server, players, state, data, cfg, now, play, random, true, hardcore);
		}

		String name(@Nullable UUID player) {
			return player == null ? "someone" : state.player(player).map(HerobrineState.Subject::name).orElse(player.toString());
		}
	}

	// --- the periodic step ---

	/** Marked deaths the event missed, the commits, then the path's beat. */
	public void tick(Ctx c, EndingFacts facts) {
		EndingState data = c.data();
		onMarkedDeath(c);
		if (data.ended()) {
			finals(c);
			return;
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
			cancelCopy(c);
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
		cancelCopy(c);
		ports.disarmTraps();
		data.log("the story ends: " + why);
	}

	/** B's request to finish the copy elsewhere ends with B (or with the story): nothing more leaves the house for it. */
	private void cancelCopy(Ctx c) {
		if (c.data().copyRequested() && ports.cancelCopyFinish(c.server())) {
			c.data().log("B: the copy elsewhere is no longer being finished");
		}
	}

	// --- events ---

	/**
	 * Every marked death not handled yet, oldest first: the event's (the death is recorded before it fires) and any it
	 * missed (the tick calls this too). The world's list is append-only, so the new ones are its tail past
	 * {@link EndingState#deathsSeen}; each is handled exactly once, whoever's it is (D-063).
	 */
	public void onMarkedDeath(Ctx c) {
		EndingState data = c.data();
		List<MarkedDeath> all = c.state().markedDeaths();
		if (data.deathsSeen() > all.size()) {
			// The world's list shrank (a debug reset): start counting from what is there now.
			data.setDeathsSeen(all.size());
		}
		if (all.size() == data.deathsSeen()) {
			return;
		}
		List<MarkedDeath> fresh = List.copyOf(all.subList(data.deathsSeen(), all.size()));
		data.setDeathsSeen(all.size());
		for (MarkedDeath death : fresh) {
			onMarkedDeath(c, death);
		}
	}

	/**
	 * One marked death, that player's own (D-063). In hardcore it finishes their line, whatever else happens; it ends
	 * the story only if nobody is left in it (then A's sign, or B's record, or B's record as the end it now is), and
	 * otherwise only through the path's own last beat, as in normal mode (A's ordinary accident, B's final death in the
	 * copy). In normal mode it may be one of those beats, and that player's own third one commits B whatever the path.
	 */
	private void onMarkedDeath(Ctx c, MarkedDeath death) {
		EndingState data = c.data();
		UUID player = death.player();
		GlobalPos pos = death.pos();
		if (c.hardcore() && player != null && data.finishLine(player)) {
			c.state().setFlag(EndingApi.LINE_FINISHED_FLAG + player, true);
			data.log(c.name(player) + "'s line is finished (a marked death in hardcore)");
		}
		if (data.ended()) {
			return;
		}
		EndingPath path = data.path();
		boolean accident = path == EndingPath.A && data.progress(EndingPath.A) == A.ACCIDENT.ordinal();
		boolean finalDeath = path == EndingPath.B && data.progress(EndingPath.B) == B.FINAL.ordinal() && isFinalDeath(c, pos);
		if (c.hardcore()) {
			boolean nobodyLeft = c.players().stream().noneMatch(p -> !p.getUUID().equals(player) && !data.lineFinished(p.getUUID()));
			if (path == EndingPath.A && (accident || nobodyLeft)) {
				data.setDeath(pos, c.now());
				setBeat(c, EndingPath.A, A.SIGN, accident ? "the ordinary accident" : "hardcore: this death was the ending");
			} else if (path == EndingPath.B && (finalDeath || nobodyLeft)) {
				toRecord(c, finalDeath ? "the final death, inside the copy" : "hardcore: nobody is left in it", player);
			} else if (path == EndingPath.B && data.progress(EndingPath.B) == B.RECORD.ordinal()) {
				// B's record is being written (F20 is not down yet): a line finished now is on F10 too, after the ones before.
				nameFinishedLines(c);
			} else if (nobodyLeft && path != EndingPath.A && path != EndingPath.B) {
				commit(c, EndingPath.B, "hardcore: " + c.name(player) + " was the last one in it");
				toRecord(c, "hardcore: nobody is left in it", player);
			}
			if (nobodyLeft) {
				endStory(c, "hardcore: nobody is left in it");
			} else {
				data.log(c.name(player) + " is out; the story goes on for the others");
			}
			return;
		}
		if (accident) {
			data.setDeath(pos, c.now());
			setBeat(c, EndingPath.A, A.SIGN, "the ordinary accident");
		} else if (finalDeath) {
			toRecord(c, "the final death, inside the copy", player);
		}
		if (player != null) {
			thirdDeath(c, player, c.state().markedDeaths(player).size());
		}
	}

	/** A player's own third marked death commits B, whatever the path (D-063: each player's own count, the solo pace). */
	private void thirdDeath(Ctx c, UUID player, int own) {
		if (own >= ModConfig.pacing().endingBMarkedDeaths && c.data().path() != EndingPath.B && !c.data().ended()) {
			commit(c, EndingPath.B, c.name(player) + "'s marked death " + own + ", the third-death rule");
		}
	}

	/**
	 * A telling by anyone who takes part (already recorded): the group's, so one friend naming him during C undoes it
	 * for all (D-064): "say his name once and it all starts again".
	 */
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

	/**
	 * He is seen once, far off, walking away into the fog: {@code sighting_last_one}, through its own gates, for the
	 * first one in it whose context fits (one sighting for the world; the figure is one entity everyone can see).
	 */
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
		if (c.players().isEmpty() || ports.figureOut(c.server())) {
			return;
		}
		if (data.sightingTries() >= cfg.aSightingTries) {
			toQuiet(c, "never seen after " + data.sightingTries() + " tries");
			return;
		}
		if (!mayTry(c, "a/sighting")) {
			return;
		}
		for (ServerPlayer player : c.players()) {
			if (ports.lastSighting(player) == FireResult.FIRED) {
				data.recordSighting(c.now());
				data.log("A: he walks away into the fog, far off, for " + player.getName().getString() + " (try " + data.sightingTries() + ")");
				return;
			}
		}
	}

	/**
	 * Five to seven full in-game days of real quiet ({@code pacing.endingAQuietMinDays..MaxDays}), counted from now.
	 * The director's flag is day-granular, so it stays silent until the first day boundary after the quiet ends.
	 */
	private void toQuiet(Ctx c, String why) {
		Pacing pacing = ModConfig.pacing();
		int min = Math.max(0, pacing.endingAQuietMinDays);
		int max = Math.max(min, pacing.endingAQuietMaxDays);
		long until = c.now() + (min + c.random().nextInt(max - min + 1)) * GameClock.TICKS_PER_DAY;
		c.data().setQuietUntil(until);
		DirectorHooks.silenceUntil(c.state(), Math.floorDiv(until + GameClock.TICKS_PER_DAY - 1, GameClock.TICKS_PER_DAY));
		setBeat(c, EndingPath.A, A.QUIET, why + String.format(Locale.ROOT, "; quiet for %.1f days", (until - c.now()) / (double) GameClock.TICKS_PER_DAY));
	}

	void aQuiet(Ctx c) {
		if (c.force() || c.now() >= c.data().quietUntil()) {
			// Only the one accident comes now.
			DirectorHooks.silenceForever(c.state());
			setBeat(c, EndingPath.A, A.ACCIDENT, "the quiet is over");
		}
	}

	/**
	 * One ordinary accident in a place they trusted: a lava floor on someone's own mine route (the planner looks around
	 * them). One for the world: the first one in it, past their join grace, around whom a trap can be set.
	 */
	void aAccident(Ctx c) {
		EndingState data = c.data();
		// Intended to stay group-wide (unlike B's traps): A's quiet disarmed everything and the director is silent, so the
		// only trap left can be this one accident's own; while any trap is armed for anyone, A's one accident waits.
		if (c.players().isEmpty() || ports.trapArmed()) {
			return;
		}
		if (!c.force() && (data.lastArmAt() != EndingState.NEVER && c.daysSince(data.lastArmAt()) < c.cfg().aRearmDays || !mayArmNow(c, 0)
				|| !mayTry(c, "a/accident"))) {
			return;
		}
		for (ServerPlayer player : c.players()) {
			if (!c.force() && !pastJoinGrace(player)) {
				continue;
			}
			Optional<String> armed = armFirst(player, c.cfg().aTraps);
			if (armed.isPresent()) {
				data.setLastArmAt(c.now());
				data.setLastArmPlay(c.play());
				data.log("A: one ordinary accident in a place " + player.getName().getString() + " trusted (" + armed.get() + ")");
				return;
			}
		}
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
		c.data().setHouse(bHouse(c).orElse(null));
	}

	/**
	 * "Your house" for B (D-061, never flips once B stored it): the house the copy elsewhere copies, fixed since world
	 * captured it ({@code HouseCopyApi.realHouse}), even if its owner moved their base since; for a copy whose house
	 * cannot be read, the house copy owner's ({@code HouseCopyApi.owner}: world's {@code world:house_copy} choice,
	 * else an old copy's first known player) base while they are online, else their first crafting table, chest or
	 * block. With no copy (whoever world may have chosen), or a copy owner who is the home's, the group's home
	 * ({@code ending:home}, see {@link EndingWatch#sampleHome}); else the base of the first player still in it.
	 */
	Optional<GlobalPos> bHouse(Ctx c) {
		Optional<GlobalPos> copied = ports.copiedHouse(c.server());
		if (copied.isPresent()) {
			return copied;
		}
		PlayerWatch watch = ports.watch();
		Optional<UUID> copyOwner = ports.copyExists(c.server()) ? ports.copyOwner(c.server()) : Optional.empty();
		if (copyOwner.isPresent() && (c.data().home().isEmpty() || !copyOwner.equals(watch.owner(c.server(), EndingApi.HOME_OWNER)))) {
			Optional<GlobalPos> base = watch.participant(c.server(), copyOwner.get()).flatMap(watch::base);
			if (base.isPresent()) {
				return base;
			}
			HerobrineState.FirstBlocks first = c.state().firstBlocks(copyOwner.get());
			for (PlacedBlock block : new PlacedBlock[] {first.craftingTable(), first.chest(), first.block()}) {
				if (block != null) {
					return Optional.of(block.pos());
				}
			}
		}
		if (c.data().home().isPresent()) {
			return c.data().home();
		}
		return c.first().flatMap(watch::base);
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
		if (data.house().isEmpty()) {
			bHouse(c).ifPresent(data::setHouse);
		}
		Optional<GlobalPos> house = data.house();
		ServerLevel level = house.map(h -> c.server().getLevel(h.dimension())).orElse(null);
		if (house.isEmpty() || level == null) {
			setBeat(c, EndingPath.B, B.FINISH_COPY, "no house to empty");
			return;
		}
		BlockPos home = house.get().pos();
		int r = cfg.houseRadius;
		if (!HouseScan.loaded(level, home.offset(-r, 0, -r), home.offset(r, 0, r))) {
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
		int ticks = (int) Math.min(Integer.MAX_VALUE, cfg.checkTicks() * 3 + HOLD_SLACK_TICKS);
		MobTamper mobs = ports.mobs();
		mobs.freeze(mob, ticks);
		mobs.silence(mob, ticks);
		mobs.face(mob, Vec3.atCenterOf(door), ticks);
	}

	/** The doorway's mobs keep waiting until anyone in it comes close (then the game takes over) or their time is up. */
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
			Vec3 spot = Vec3.atBottomCenterOf(waiter.spot());
			boolean near = c.players().stream().anyMatch(p -> p.level() == level && p.position().distanceTo(spot) <= c.cfg().bDoorwayReleaseBlocks);
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

	/**
	 * Accidents closer to home: while anyone in it is near the house, the ending arms one there itself, around them,
	 * paced like a director major for the world (B's escalation is the world's, D-064; see {@link #mayArmNow}): at least
	 * {@code bHomeArmMinutes} of real play after the ending's last trap (never under D-045's hour, persisted across
	 * sessions), never in that player's join grace or the director's quiet.
	 */
	void homeAccident(Ctx c) {
		EndingState data = c.data();
		EndingConfig cfg = c.cfg();
		Optional<GlobalPos> house = data.house();
		// One trap per player (accident's rule): a friend's trap elsewhere never holds B's escalation up for the group;
		// whoever it is armed around must have none of their own armed.
		if (c.players().isEmpty() || house.isEmpty()) {
			return;
		}
		List<ServerPlayer> near = c.players().stream().filter(p -> p.level().dimension().equals(house.get().dimension())
				&& horizontal(p.blockPosition(), house.get().pos()) <= cfg.bHomeRadius).toList();
		if (near.isEmpty()) {
			return;
		}
		if (!c.force() && (ports.directorQuiet(c.server()) || !mayArmNow(c, ModConfig.realTicks(cfg.bHomeArmMinutes * 60)) || !mayTry(c, "b/home"))) {
			return;
		}
		List<String> order = new ArrayList<>(cfg.bHomeTraps);
		if (!order.isEmpty()) {
			Collections.rotate(order, c.random().nextInt(order.size()));
		}
		for (ServerPlayer player : near) {
			if (!c.force() && !pastJoinGrace(player) || ports.trapArmed(c.server(), player.getUUID())) {
				continue;
			}
			Optional<String> armed = armFirst(player, order);
			if (armed.isPresent()) {
				data.setLastHomeArmPlay(c.play());
				data.setLastArmPlay(c.play());
				data.log("B: an accident close to home (" + armed.get() + ", around " + player.getName().getString() + ")");
				return;
			}
		}
	}

	/**
	 * The ending may arm a trap now, for the world: at least {@code max(pacing.majorGapMinutes, minGap)} of real play
	 * (the world clock) since the ending's last trap (kept in {@link EndingState}, so a rejoin does not reset it), and at
	 * least {@code pacing.majorGapMinutes} since the director's own last accident (D-045's floor between majors). Who it
	 * arms around must also be past their own join grace ({@link #pastJoinGrace}).
	 */
	boolean mayArmNow(Ctx c, long minGap) {
		if (c.force()) {
			return true;
		}
		Pacing pacing = ModConfig.pacing();
		long last = c.data().lastArmPlay();
		if (last != EndingState.NEVER && c.play() >= last && c.play() - last < Math.max(pacing.majorGapTicks(), minGap)) {
			return false;
		}
		return ports.ticksSinceDirectorAccident(c.server()) >= pacing.majorGapTicks();
	}

	/** Nothing is armed around a player in their first minutes of a session (the join grace, per player). */
	boolean pastJoinGrace(ServerPlayer player) {
		return ports.ticksSinceJoin(player) >= ModConfig.pacing().joinGraceTicks();
	}

	/**
	 * The final death happens inside the copy: someone in it is inside it, and the trap is armed around them only where
	 * every spot the planner could pick lies inside it too. Anyone's death there by that trap is the final one (an
	 * offline friend never holds it up, D-064). Without a copy, the emptied house's own shell takes its place.
	 */
	void bFinal(Ctx c) {
		EndingState data = c.data();
		EndingConfig cfg = c.cfg();
		List<EndingState.Trap> armed = ports.armedTraps(c.server());
		if (data.finalTrap().isPresent() && !armed.contains(data.finalTrap().get())) {
			data.setFinalTrap(null);
			data.log("B: the final trap's time ran out; it waits for them again");
		}
		// One trap per player: a friend's trap elsewhere does not hold the final one up; whoever it is set for must have
		// none of their own armed (accident's planner keeps it off ground another trap holds).
		if (data.finalTrap().isPresent() || c.players().isEmpty()) {
			return;
		}
		Optional<Bounds> bounds = finalBounds(c);
		if (bounds.isEmpty()) {
			return;
		}
		List<ServerPlayer> inside = c.players().stream().filter(p -> bounds.get().contains(p.level().dimension(), p.blockPosition())).toList();
		if (!c.force() && (inside.isEmpty() || !mayArmNow(c, 0) || !mayTry(c, "b/final"))) {
			return;
		}
		for (ServerPlayer player : inside.isEmpty() ? c.players() : inside) {
			if (!c.force() && !pastJoinGrace(player) || ports.trapArmed(c.server(), player.getUUID())) {
				continue;
			}
			for (String id : cfg.bFinalTraps) {
				if (ports.armTrapInside(player, id, bounds.get())) {
					data.setFinalTrap(ports.armedTrap(c.server(), player.getUUID())
							.orElse(new EndingState.Trap(id, GlobalPos.of(player.level().dimension(), player.blockPosition()))));
					data.setLastArmAt(c.now());
					data.setLastArmPlay(c.play());
					data.log("B: the final trap, inside the copy (" + id + " at " + data.finalTrap().get().pos().pos().toShortString() + ")");
					return;
				}
			}
		}
	}

	/** The copy's box (every block moved into it); without a copy, the box of the players' own blocks at the house. */
	Optional<Bounds> finalBounds(Ctx c) {
		if (ports.copyExists(c.server())) {
			Optional<Bounds> copy = ports.copyBounds(c.server());
			if (copy.isPresent()) {
				return copy;
			}
		}
		Optional<GlobalPos> house = c.data().house();
		ServerLevel level = house.map(h -> c.server().getLevel(h.dimension())).orElse(null);
		if (level == null) {
			return Optional.empty();
		}
		return Bounds.around(level.dimension(), ports.watch().placedNear(level, house.get().pos(), c.cfg().houseRadius, s -> !s.isAir()));
	}

	/**
	 * B's final death: the planner's own final trap did it (it is still armed when the death is marked, which the
	 * planner does only for a death it attributes to that trap; other players' traps may be armed too) and the death is
	 * inside the copy.
	 */
	private boolean isFinalDeath(Ctx c, GlobalPos pos) {
		Optional<EndingState.Trap> trap = c.data().finalTrap();
		if (trap.isEmpty() || !ports.armedTraps(c.server()).contains(trap.get())) {
			return false;
		}
		Optional<Bounds> bounds = finalBounds(c);
		return bounds.isPresent() && bounds.get().contains(pos);
	}

	/**
	 * Ending B's record, once for the world (the beat it sets is the guard): F10 gains one "* removed [PLAYER NAME]" line
	 * for every player whose line is finished, in the order they were finished (lore's {@code finishF10(server, UUID)},
	 * once each). Only finished lines are ever named, never a living friend: in hardcore each marked death finished its
	 * player's line; B's final death inside the copy finishes the line of whoever died there ({@code victim}, normal mode
	 * too: "your name is not erased; it is finished"). A friend's line finished in hardcore while others play is only
	 * {@code ending:line_finished=<uuid>} until then; one finished while the record is being written is named then.
	 */
	private void toRecord(Ctx c, String why, @Nullable UUID victim) {
		EndingState data = c.data();
		if (victim != null && data.finishLine(victim)) {
			c.state().setFlag(EndingApi.LINE_FINISHED_FLAG + victim, true);
			data.log(c.name(victim) + "'s line is finished (B's final death)");
		}
		List<UUID> finished = nameFinishedLines(c);
		setBeat(c, EndingPath.B, B.RECORD, why + (finished.isEmpty() ? "; F10 names nobody (no line is finished)" : "; F10 is finished"));
	}

	/**
	 * F10 names every finished line, in the order they were finished (lore's {@code finishF10} keeps one already named
	 * in its place, so naming them all again only adds the new ones). Returns them.
	 */
	private List<UUID> nameFinishedLines(Ctx c) {
		List<UUID> finished = List.copyOf(c.data().finishedLines());
		for (UUID player : finished) {
			ports.finishF10(c.server(), player);
		}
		return finished;
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
				GlobalPos at = f.first().map(p -> GlobalPos.of(p.level().dimension(), p.blockPosition())).orElse(f.data().home().orElse(null));
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
				toRecord(f, "debug: as if the final death happened", f.first().map(ServerPlayer::getUUID).orElse(null));
			}
			case RECORD -> bRecord(f);
			case DONE -> {
			}
		}
	}

	// --- helpers ---

	/** The first of these traps the planner can arm around this player. */
	private Optional<String> armFirst(ServerPlayer player, List<String> ids) {
		for (String id : ids) {
			if (ports.armTrap(player, id)) {
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
