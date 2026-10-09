package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.CoreCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * Ending D's own facts ({@code data/a1016_02/ending_d.dat}): the current step, the team's stair, what the player
 * carries out of the grove and the cairn (as placed blocks), small flags, and how far the last minute and the
 * afterward got. Change it only through its methods, which mark it dirty. Server thread only.
 */
public final class EndingDState extends SavedData {
	/** A placed block that came from a tracked item (the first block out of the cairn, grove planks). */
	public record Tracked(GlobalPos pos, String mark) {
		static final Codec<Tracked> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(Tracked::pos),
				Codec.STRING.fieldOf("mark").forGetter(Tracked::mark)
		).apply(i, Tracked::new));
	}

	/** Flag: the cairn lure was armed for step 3. */
	public static final String CAIRN_ARMED = "cairn_armed";
	/** Flag: the first block came out of the cairn with no offering left on that visit. */
	public static final String FIRST_TAKEN = "first_taken";
	/** Flag: the stairwell flood happened (step 4). */
	public static final String FLOODED = "flooded";
	/** Flag: the player named him (or said sorry) in the chamber; the next block they stand on goes. */
	public static final String NAMED = "named";
	/** Flag: one of the player's torches went with the stair. */
	public static final String STAIR_TORCH = "stair_torch";
	/** Flag: the last minute is a debug preview (no flags, no clock). */
	public static final String PREVIEW = "preview";

	public static final Codec<EndingDState> CODEC = RecordCodecBuilder.create(i -> i.group(
			CoreCodecs.enumCodec(Step.class).optionalFieldOf("step", Step.MAP).forGetter(s -> s.step),
			StairPlan.CODEC.optionalFieldOf("stair").forGetter(s -> Optional.ofNullable(s.stair)),
			Codec.INT.optionalFieldOf("groveLogs", 0).forGetter(s -> s.groveLogs),
			Tracked.CODEC.listOf().optionalFieldOf("tracked", List.of()).forGetter(s -> s.tracked),
			CoreCodecs.setOf(Codec.STRING).optionalFieldOf("flags", Set.of()).forGetter(s -> s.flags),
			Codec.INT.optionalFieldOf("lastMinutePhase", 0).forGetter(s -> s.lastMinutePhase),
			Codec.INT.optionalFieldOf("undone", 0).forGetter(s -> s.undone),
			Codec.INT.optionalFieldOf("undoPasses", 0).forGetter(s -> s.undoPasses),
			Codec.BOOL.optionalFieldOf("undoFinished", false).forGetter(s -> s.undoFinished),
			CoreCodecs.setOf(Codec.INT).optionalFieldOf("regrown", Set.of()).forGetter(s -> s.regrown),
			Codec.LONG.optionalFieldOf("completeDay", -1L).forGetter(s -> s.completeDay)
	).apply(i, EndingDState::new));

	public static final SavedDataType<EndingDState> TYPE = new SavedDataType<>(A1016_02.id("ending_d"), EndingDState::new, CODEC, null);

	private Step step = Step.MAP;
	private @Nullable StairPlan stair;
	private int groveLogs;
	private final List<Tracked> tracked = new ArrayList<>();
	private final Set<String> flags = new TreeSet<>();
	private int lastMinutePhase;
	private int undone;
	private int undoPasses;
	private boolean undoFinished;
	private final Set<Integer> regrown = new TreeSet<>();
	private long completeDay = -1;

	public EndingDState() {
	}

	private EndingDState(Step step, Optional<StairPlan> stair, int groveLogs, List<Tracked> tracked, Set<String> flags, int lastMinutePhase,
			int undone, int undoPasses, boolean undoFinished, Set<Integer> regrown, long completeDay) {
		this.step = step;
		this.stair = stair.orElse(null);
		this.groveLogs = groveLogs;
		this.tracked.addAll(tracked);
		this.flags.addAll(flags);
		this.lastMinutePhase = lastMinutePhase;
		this.undone = undone;
		this.undoPasses = undoPasses;
		this.undoFinished = undoFinished;
		this.regrown.addAll(regrown);
		this.completeDay = completeDay;
	}

	public static EndingDState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// --- step ---

	public Step step() {
		return step;
	}

	public void setStep(Step value) {
		if (step != value) {
			step = value;
			setDirty();
		}
	}

	// --- stair ---

	public Optional<StairPlan> stair() {
		return Optional.ofNullable(stair);
	}

	public void setStair(@Nullable StairPlan plan) {
		stair = plan;
		setDirty();
	}

	// --- grove wood, first block ---

	public int groveLogs() {
		return groveLogs;
	}

	public void addGroveLog() {
		groveLogs++;
		setDirty();
	}

	public List<Tracked> tracked() {
		return Collections.unmodifiableList(tracked);
	}

	public Optional<String> trackedAt(GlobalPos pos) {
		return tracked.stream().filter(t -> t.pos().equals(pos)).map(Tracked::mark).findFirst();
	}

	public void track(GlobalPos pos, String mark) {
		tracked.removeIf(t -> t.pos().equals(pos));
		tracked.add(new Tracked(pos, mark));
		setDirty();
	}

	public boolean untrack(GlobalPos pos) {
		boolean removed = tracked.removeIf(t -> t.pos().equals(pos));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	// --- flags ---

	public boolean has(String flag) {
		return flags.contains(flag);
	}

	public void set(String flag, boolean value) {
		if (value ? flags.add(flag) : flags.remove(flag)) {
			setDirty();
		}
	}

	public Set<String> flags() {
		return Collections.unmodifiableSet(flags);
	}

	// --- last minute ---

	public int lastMinutePhase() {
		return lastMinutePhase;
	}

	public void setLastMinutePhase(int phase) {
		if (lastMinutePhase != phase) {
			lastMinutePhase = phase;
			setDirty();
		}
	}

	public long completeDay() {
		return completeDay;
	}

	public void setCompleteDay(long day) {
		completeDay = day;
		setDirty();
	}

	// --- afterward ---

	public int undone() {
		return undone;
	}

	public void addUndone(int count) {
		if (count != 0) {
			undone += count;
			setDirty();
		}
	}

	public int undoPasses() {
		return undoPasses;
	}

	public void addUndoPass() {
		undoPasses++;
		setDirty();
	}

	public boolean undoFinished() {
		return undoFinished;
	}

	public void setUndoFinished(boolean value) {
		if (undoFinished != value) {
			undoFinished = value;
			setDirty();
		}
	}

	public Set<Integer> regrown() {
		return Collections.unmodifiableSet(regrown);
	}

	public void addRegrown(int siteId) {
		if (regrown.add(siteId)) {
			setDirty();
		}
	}

	/** Back to the start (debug). Keeps the stair, since its blocks are in the world. */
	public void resetChain() {
		step = Step.MAP;
		flags.clear();
		lastMinutePhase = 0;
		setDirty();
	}
}
