package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.world.sig.HouseCopyState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;

import org.jspecify.annotations.Nullable;

/**
 * The world's signature moments and the lone redstone torches, kept in {@link WorldData}: what already happened
 * (each signature happens at most once per world), the house copy as it grows, and the cave spots the subject
 * explored. Server thread only; every change marks {@link WorldData} dirty.
 */
public final class SignatureData {
	/**
	 * The still-burning camp (D-004).
	 *
	 * @param emptiedHouse the emptied house built beside it when F21 is rolled
	 */
	public record Burning(GlobalPos furnace, GlobalPos build, Optional<GlobalPos> emptiedHouse, long day) {
		static final Codec<Burning> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("furnace").forGetter(Burning::furnace),
				GlobalPos.CODEC.fieldOf("build").forGetter(Burning::build),
				GlobalPos.CODEC.optionalFieldOf("emptiedHouse").forGetter(Burning::emptiedHouse),
				Codec.LONG.fieldOf("day").forGetter(Burning::day)
		).apply(i, Burning::new));
	}

	/** The row of crosses: their bases in order, the fresh one last. */
	public record CrossRow(List<GlobalPos> crosses, long day) {
		static final Codec<CrossRow> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.listOf().fieldOf("crosses").forGetter(CrossRow::crosses),
				Codec.LONG.fieldOf("day").forGetter(CrossRow::day)
		).apply(i, CrossRow::new));
	}

	/** One lone redstone torch (D-033) or one cave spot the subject stood in, with the in-game day. */
	public record Spot(GlobalPos pos, long day) {
		static final Codec<Spot> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(Spot::pos),
				Codec.LONG.fieldOf("day").forGetter(Spot::day)
		).apply(i, Spot::new));
	}

	static final Codec<SignatureData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Burning.CODEC.optionalFieldOf("stillBurning").forGetter(d -> Optional.ofNullable(d.stillBurning)),
			Codec.BOOL.optionalFieldOf("stillBurningPending", false).forGetter(d -> d.stillBurningPending),
			HouseCopyState.CODEC.optionalFieldOf("houseCopy").forGetter(d -> Optional.ofNullable(d.houseCopy)),
			CrossRow.CODEC.optionalFieldOf("crossRow").forGetter(d -> Optional.ofNullable(d.crossRow)),
			Spot.CODEC.listOf().optionalFieldOf("torches", List.of()).forGetter(d -> List.copyOf(d.torches)),
			Spot.CODEC.listOf().optionalFieldOf("caveSpots", List.of()).forGetter(d -> List.copyOf(d.caveSpots))
	).apply(i, SignatureData::new));

	private @Nullable Burning stillBurning;
	private boolean stillBurningPending;
	private @Nullable HouseCopyState houseCopy;
	private @Nullable CrossRow crossRow;
	private final List<Spot> torches = new ArrayList<>();
	private final List<Spot> caveSpots = new ArrayList<>();
	private Runnable dirty = () -> {
	};

	/** Empty: nothing happened yet (tests use this too). */
	public SignatureData() {
	}

	private SignatureData(Optional<Burning> stillBurning, boolean stillBurningPending, Optional<HouseCopyState> houseCopy, Optional<CrossRow> crossRow,
			List<Spot> torches, List<Spot> caveSpots) {
		this.stillBurning = stillBurning.orElse(null);
		this.stillBurningPending = stillBurningPending;
		this.houseCopy = houseCopy.orElse(null);
		this.crossRow = crossRow.orElse(null);
		this.torches.addAll(torches);
		this.caveSpots.addAll(caveSpots);
	}

	void onChange(Runnable markDirty) {
		this.dirty = markDirty;
	}

	// --- still burning ---

	public Optional<Burning> stillBurning() {
		return Optional.ofNullable(stillBurning);
	}

	/** Records the still-burning camp; it never happens again in this world. */
	public void setStillBurning(Burning burning) {
		stillBurning = burning;
		stillBurningPending = false;
		dirty.run();
	}

	/** True while the camp was asked for and is still being looked for (it survives a restart). */
	public boolean stillBurningPending() {
		return stillBurningPending;
	}

	public void setStillBurningPending(boolean pending) {
		stillBurningPending = pending;
		dirty.run();
	}

	// --- the house copy ---

	public Optional<HouseCopyState> houseCopy() {
		return Optional.ofNullable(houseCopy);
	}

	public void setHouseCopy(@Nullable HouseCopyState state) {
		houseCopy = state;
		dirty.run();
	}

	// --- the row of crosses ---

	public Optional<CrossRow> crossRow() {
		return Optional.ofNullable(crossRow);
	}

	public void setCrossRow(CrossRow row) {
		crossRow = row;
		dirty.run();
	}

	// --- lone redstone torches ---

	/** Oldest first. */
	public List<Spot> torches() {
		return List.copyOf(torches);
	}

	public void addTorch(Spot torch) {
		torches.add(torch);
		dirty.run();
	}

	/** Oldest first. */
	public List<Spot> caveSpots() {
		return List.copyOf(caveSpots);
	}

	/**
	 * Remembers that the subject stood at a cave spot today: a spot within {@code merge} blocks is moved to today,
	 * otherwise a new one is added and the oldest go past {@code max}.
	 */
	public void visitCave(GlobalPos pos, long day, int merge, int max) {
		for (int n = 0; n < caveSpots.size(); n++) {
			Spot spot = caveSpots.get(n);
			if (spot.pos().dimension().equals(pos.dimension()) && spot.pos().pos().closerThan(pos.pos(), merge)) {
				if (spot.day() != day) {
					caveSpots.remove(n);
					caveSpots.add(new Spot(spot.pos(), day));
					dirty.run();
				}
				return;
			}
		}
		caveSpots.add(new Spot(pos, day));
		while (caveSpots.size() > Math.max(1, max)) {
			caveSpots.removeFirst();
		}
		dirty.run();
	}
}
