package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.CoreCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The one armed trap, saved with the world.
 *
 * @param type    trap id ({@link TrapKind#id()})
 * @param pos     the trap's spot: the block he takes, or the place a live trap watches
 * @param targets the blocks he took (preset) or plans to take (live)
 * @param saved   blocks to put back later (the dark corner's torches)
 * @param offPos  the dark corner: where the one torch goes back, a block off
 * @param mobs    the mobs a death must come from: the moved mob, monsters that spawned in the dark corner, phantoms of
 *                the sleepless nights after the bed went
 * @param phase   watching for its moment, set, or (dark corner) set and put back
 * @param window  when it was armed and set, and when it ends (play time and game clock)
 * @param zoneMin one corner of the place a death counts in
 * @param zoneMax the other corner
 * @param clue    what a careful player can find afterward
 * @param step    progress of a stepwise trap (torches out so far)
 * @param lit     the dark corner: the cells the taken torches lit (flood-filled at arm time); only spawns there count
 */
public record ArmedTrap(String type, ResourceKey<Level> dimension, BlockPos pos, List<BlockPos> targets, List<SavedBlock> saved,
		Optional<BlockPos> offPos, List<UUID> mobs, Phase phase, Window window, BlockPos zoneMin, BlockPos zoneMax, String clue, int step,
		Set<BlockPos> lit) {
	public enum Phase { WATCHING, SET, RESTORED }

	/** {@link #clockUntil} when the trap has no game-clock deadline. */
	public static final long NO_CLOCK = Long.MIN_VALUE;

	/** A block and the state to put back. */
	public record SavedBlock(BlockPos pos, BlockState state) {
		public static final Codec<SavedBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(SavedBlock::pos),
				BlockState.CODEC.fieldOf("state").forGetter(SavedBlock::state)
		).apply(i, SavedBlock::new));
	}

	/**
	 * @param armedAt    play tick it was armed
	 * @param setAt      play tick the world changed, or -1 while watching
	 * @param until      play tick it ends: the end of the watch, or of the window in which a death counts
	 * @param clockUntil overworld clock time (day time) at which the change is undone (the dark corner's morning), or
	 *                   {@link #NO_CLOCK}
	 */
	public record Window(long armedAt, long setAt, long until, long clockUntil) {
		static final MapCodec<Window> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.LONG.fieldOf("armedAt").forGetter(Window::armedAt),
				Codec.LONG.optionalFieldOf("setAt", -1L).forGetter(Window::setAt),
				Codec.LONG.fieldOf("until").forGetter(Window::until),
				Codec.LONG.optionalFieldOf("clockUntil", NO_CLOCK).forGetter(Window::clockUntil)
		).apply(i, Window::new));
	}

	/** Many positions, packed as longs. */
	private static final Codec<Set<BlockPos>> PACKED = Codec.LONG_STREAM.xmap(
			s -> new HashSet<>(s.mapToObj(BlockPos::of).toList()), set -> set.stream().mapToLong(BlockPos::asLong));

	public static final Codec<ArmedTrap> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("type").forGetter(ArmedTrap::type),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ArmedTrap::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(ArmedTrap::pos),
			BlockPos.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(ArmedTrap::targets),
			SavedBlock.CODEC.listOf().optionalFieldOf("saved", List.of()).forGetter(ArmedTrap::saved),
			BlockPos.CODEC.optionalFieldOf("offPos").forGetter(ArmedTrap::offPos),
			UUIDUtil.CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(ArmedTrap::mobs),
			CoreCodecs.enumCodec(Phase.class).fieldOf("phase").forGetter(ArmedTrap::phase),
			Window.CODEC.forGetter(ArmedTrap::window),
			BlockPos.CODEC.fieldOf("zoneMin").forGetter(ArmedTrap::zoneMin),
			BlockPos.CODEC.fieldOf("zoneMax").forGetter(ArmedTrap::zoneMax),
			Codec.STRING.optionalFieldOf("clue", "").forGetter(ArmedTrap::clue),
			Codec.INT.optionalFieldOf("step", 0).forGetter(ArmedTrap::step),
			PACKED.optionalFieldOf("lit", Set.of()).forGetter(ArmedTrap::lit)
	).apply(i, ArmedTrap::new));

	public ArmedTrap {
		targets = List.copyOf(targets);
		saved = List.copyOf(saved);
		mobs = List.copyOf(mobs);
		lit = Set.copyOf(lit);
	}

	/** A trap with no lit cells, its window given field by field. */
	public ArmedTrap(String type, ResourceKey<Level> dimension, BlockPos pos, List<BlockPos> targets, List<SavedBlock> saved, Optional<BlockPos> offPos,
			List<UUID> mobs, Phase phase, long armedAt, long setAt, long until, long clockUntil, BlockPos zoneMin, BlockPos zoneMax, String clue, int step) {
		this(type, dimension, pos, targets, saved, offPos, mobs, phase, new Window(armedAt, setAt, until, clockUntil), zoneMin, zoneMax, clue, step, Set.of());
	}

	public long armedAt() {
		return window.armedAt();
	}

	public long setAt() {
		return window.setAt();
	}

	public long until() {
		return window.until();
	}

	public long clockUntil() {
		return window.clockUntil();
	}

	public boolean isSet() {
		return phase != Phase.WATCHING;
	}

	/** The place a death counts in, grown by {@code slack} blocks. */
	public AABB zone(int slack) {
		return AABB.encapsulatingFullBlocks(zoneMin, zoneMax).inflate(slack);
	}

	public boolean hasClock() {
		return clockUntil() != NO_CLOCK;
	}

	public boolean blames(UUID mob) {
		return mobs.contains(mob);
	}

	private ArmedTrap with(List<BlockPos> newTargets, List<UUID> newMobs, Phase newPhase, Window newWindow, BlockPos min, BlockPos max, int newStep,
			Set<BlockPos> newLit) {
		return new ArmedTrap(type, dimension, pos, newTargets, saved, offPos, newMobs, newPhase, newWindow, min, max, clue, newStep, newLit);
	}

	/** The world changed now: the death window starts. */
	public ArmedTrap set(long now, long span, List<BlockPos> done) {
		return with(done, mobs, Phase.SET, new Window(armedAt(), now, now + span, clockUntil()), zoneMin, zoneMax, step, lit);
	}

	public ArmedTrap withPhase(Phase newPhase, long newUntil) {
		return with(targets, mobs, newPhase, new Window(armedAt(), setAt(), newUntil, clockUntil()), zoneMin, zoneMax, step, lit);
	}

	public ArmedTrap withStep(int newStep, List<BlockPos> newTargets) {
		return with(newTargets, mobs, phase, window, zoneMin, zoneMax, newStep, lit);
	}

	public ArmedTrap withZone(BlockPos min, BlockPos max) {
		return with(targets, mobs, phase, window, min, max, step, lit);
	}

	public ArmedTrap withClockUntil(long clock) {
		return with(targets, mobs, phase, new Window(armedAt(), setAt(), until(), clock), zoneMin, zoneMax, step, lit);
	}

	public ArmedTrap withLit(Set<BlockPos> cells) {
		return with(targets, mobs, phase, window, zoneMin, zoneMax, step, cells);
	}

	/** Adds a mob whose damage now counts as this trap's. */
	public ArmedTrap blame(UUID mob) {
		if (mobs.contains(mob)) {
			return this;
		}
		List<UUID> more = new ArrayList<>(mobs);
		more.add(mob);
		return with(targets, more, phase, window, zoneMin, zoneMax, step, lit);
	}
}
