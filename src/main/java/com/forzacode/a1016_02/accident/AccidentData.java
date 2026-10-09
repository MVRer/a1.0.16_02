package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The accident workstream's saved data ({@code data/a1016_02/accident.dat}): the armed trap, a dark corner still
 * waiting to be put back, crosses waiting to be built, the cairn visits, the learned routes and a short history.
 */
public final class AccidentData extends SavedData {
	/** A marked death whose cross is not built yet. */
	public record PendingCross(GlobalPos pos, String cause, int height, int attempts) {
		static final Codec<PendingCross> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(PendingCross::pos),
				Codec.STRING.fieldOf("cause").forGetter(PendingCross::cause),
				Codec.INT.fieldOf("height").forGetter(PendingCross::height),
				Codec.INT.optionalFieldOf("attempts", 0).forGetter(PendingCross::attempts)
		).apply(i, PendingCross::new));
	}

	static final int HISTORY_MAX = 12;

	public static final Codec<AccidentData> CODEC = RecordCodecBuilder.create(i -> i.group(
			ArmedTrap.CODEC.optionalFieldOf("armed").forGetter(d -> Optional.ofNullable(d.armed)),
			ArmedTrap.CODEC.optionalFieldOf("restoring").forGetter(d -> Optional.ofNullable(d.restoring)),
			PendingCross.CODEC.listOf().optionalFieldOf("crosses", List.of()).forGetter(d -> d.crosses),
			Codec.INT.optionalFieldOf("cairnVisits", 0).forGetter(d -> d.cairnVisits),
			GlobalPos.CODEC.optionalFieldOf("cairn").forGetter(d -> Optional.ofNullable(d.cairn)),
			Codec.STRING.listOf().optionalFieldOf("history", List.of()).forGetter(d -> d.history),
			RouteBook.CODEC.optionalFieldOf("routes").forGetter(d -> Optional.of(d.routes))
	).apply(i, AccidentData::new));

	public static final SavedDataType<AccidentData> TYPE = new SavedDataType<>(A1016_02.id("accident"), AccidentData::new, CODEC, null);

	private @Nullable ArmedTrap armed;
	private @Nullable ArmedTrap restoring;
	private final List<PendingCross> crosses = new ArrayList<>();
	private int cairnVisits;
	private @Nullable GlobalPos cairn;
	private final List<String> history = new ArrayList<>();
	private final RouteBook routes;
	/** Not saved: true while the subject is at the cairn. */
	public boolean atCairn;
	/** Not saved: lure tracking for this session. */
	public final LureWatch lure = new LureWatch();
	/** Not saved: the last play tick the subject touched fire or lava that traces back to the house fire's gap. */
	public long tracedBurnTick = Long.MIN_VALUE;
	/** Not saved: the last play tick the subject dropped through the armed bridge gap. */
	public long gapPassTick = Long.MIN_VALUE;
	/** Not saved: which way the subject was last walking (horizontal, unit length), and in which level. */
	public @Nullable Vec3 heading;
	public @Nullable ResourceKey<Level> headingDimension;

	/** What the planner sees the player doing at the lures this session. */
	public static final class LureWatch {
		/** The grove being watched, its count of player-placed leaves, and when that count last grew (play ticks). */
		public int groveSite = -1;
		public int groveLeaves = -1;
		public long groveLeavesTick = Long.MIN_VALUE;
		/** White eyes: when disc 13 started, and when the last torch went out. */
		public long playStart = -1;
		public long stepTick = Long.MIN_VALUE;
	}

	public AccidentData() {
		routes = new RouteBook();
	}

	private AccidentData(Optional<ArmedTrap> armed, Optional<ArmedTrap> restoring, List<PendingCross> crosses, int cairnVisits,
			Optional<GlobalPos> cairn, List<String> history, Optional<RouteBook> routes) {
		this.armed = armed.orElse(null);
		this.restoring = restoring.orElse(null);
		this.crosses.addAll(crosses);
		this.cairnVisits = cairnVisits;
		this.cairn = cairn.orElse(null);
		this.history.addAll(history);
		this.routes = routes.orElseGet(RouteBook::new);
	}

	public static AccidentData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Optional<ArmedTrap> armed() {
		return Optional.ofNullable(armed);
	}

	public void setArmed(@Nullable ArmedTrap trap) {
		armed = trap;
		setDirty();
	}

	/** A dark corner that was disarmed or expired before its torches went back. */
	public Optional<ArmedTrap> restoring() {
		return Optional.ofNullable(restoring);
	}

	public void setRestoring(@Nullable ArmedTrap trap) {
		restoring = trap;
		setDirty();
	}

	public List<PendingCross> crosses() {
		return Collections.unmodifiableList(crosses);
	}

	public void addCross(PendingCross cross) {
		crosses.add(cross);
		setDirty();
	}

	public void replaceCross(PendingCross old, @Nullable PendingCross updated) {
		int index = crosses.indexOf(old);
		if (index >= 0) {
			if (updated == null) {
				crosses.remove(index);
			} else {
				crosses.set(index, updated);
			}
			setDirty();
		}
	}

	public int cairnVisits() {
		return cairnVisits;
	}

	public Optional<GlobalPos> cairn() {
		return Optional.ofNullable(cairn);
	}

	/** Remembers the cairn; a different cairn starts the visit count again. */
	public void setCairn(@Nullable GlobalPos pos) {
		if (pos == null ? cairn != null : !pos.equals(cairn)) {
			cairn = pos;
			cairnVisits = 0;
			atCairn = false;
			setDirty();
		}
	}

	public void addCairnVisit() {
		cairnVisits++;
		setDirty();
	}

	public List<String> history() {
		return Collections.unmodifiableList(history);
	}

	public void log(String line) {
		history.add(line);
		while (history.size() > HISTORY_MAX) {
			history.remove(0);
		}
		setDirty();
	}

	public RouteBook routes() {
		return routes;
	}
}
