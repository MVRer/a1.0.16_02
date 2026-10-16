package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * One thing a player did, as {@link PlayerWatch#recentActions} keeps it (D-076, D-079): mining (a run of blocks broken
 * one after another), a door, trapdoor or fence gate used, a container opened. Immutable and small.
 * <p>
 * Ticks are the server's tick count ({@code MinecraftServer.getTickCount()}, the clock of
 * {@link PlayerWatch#ticksSinceCombat}). A player's actions are kept for one session of theirs only, so the difference
 * between two of them, or between one and now, is time that player played.
 */
public sealed interface PlayerAction permits PlayerAction.Mining, PlayerAction.DoorUse, PlayerAction.ContainerOpen {
	/** The level it happened in. */
	ResourceKey<Level> dimension();

	/** Where: the last block broken for mining, a door's lower half, the container's block. */
	BlockPos pos();

	/** When it happened (server ticks); for mining, the last break. */
	long tick();

	/** One block the player broke: where, the state it had just before, and the tick it broke. */
	record Break(BlockPos pos, BlockState state, long tick) {
		public Break {
			pos = pos.immutable();
		}
	}

	/**
	 * Blocks the player broke one after another (D-076), oldest first: each less than {@link #GAP_TICKS} (3 s) after the
	 * one before, in one level, with nothing else the ring keeps in between (a door or a container ends the run), at most
	 * {@link #MAX_BREAKS} (6). A longer run goes on in a new entry, so strip mining is a string of entries of six. Every
	 * break counts, a player-placed block, a plant or a torch as much as stone: read {@link Break#state} to choose. One
	 * entry is one action in the ring however many breaks it has.
	 */
	record Mining(ResourceKey<Level> dimension, List<Break> breaks) implements PlayerAction {
		/** Breaks closer than this (3 s, real ticks: the player's own rhythm) belong to one run. */
		public static final long GAP_TICKS = 60;
		/** The fewest breaks that make a burst. */
		public static final int MIN_BURST = 2;
		/** The most breaks in one entry (and so in one burst). */
		public static final int MAX_BREAKS = 6;

		public Mining {
			breaks = List.copyOf(breaks);
			if (breaks.isEmpty() || breaks.size() > MAX_BREAKS) {
				throw new IllegalArgumentException("mining holds 1 to " + MAX_BREAKS + " breaks, not " + breaks.size());
			}
		}

		/** A burst (D-076): {@link #MIN_BURST} to {@link #MAX_BREAKS} breaks. A lone break is mining but no burst. */
		public boolean isBurst() {
			return breaks.size() >= MIN_BURST;
		}

		public Break first() {
			return breaks.getFirst();
		}

		public Break last() {
			return breaks.getLast();
		}

		@Override
		public BlockPos pos() {
			return last().pos();
		}

		@Override
		public long tick() {
			return last().tick();
		}

		/** The tick of the first break. */
		public long firstTick() {
			return first().tick();
		}

		/** The ticks from each break to the next, oldest first: one fewer than the breaks (empty for a lone break). */
		public List<Long> gaps() {
			List<Long> gaps = new ArrayList<>(breaks.size() - 1);
			for (int i = 1; i < breaks.size(); i++) {
				gaps.add(breaks.get(i).tick() - breaks.get(i - 1).tick());
			}
			return gaps;
		}

		/** True if {@code next}, in {@code dimension}, goes on this run (the same level, in time, room left). */
		boolean continuedBy(ResourceKey<Level> level, Break next) {
			return dimension.equals(level) && breaks.size() < MAX_BREAKS && next.tick() - tick() < GAP_TICKS && next.tick() >= tick();
		}

		/** This run with one more break. */
		Mining with(Break next) {
			List<Break> more = new ArrayList<>(breaks.size() + 1);
			more.addAll(breaks);
			more.add(next);
			return new Mining(dimension, more);
		}
	}

	/**
	 * A door, trapdoor or fence gate the player opened or closed by hand. {@code pos} is a door's lower half; {@code state}
	 * is the block's state right after the use, so its own sounds come from its block ({@code BlockSetType} or wood type).
	 */
	record DoorUse(ResourceKey<Level> dimension, BlockPos pos, BlockState state, long tick) implements PlayerAction {
		public DoorUse {
			pos = pos.immutable();
		}

		/** True if the use opened it, false if it closed it. */
		public boolean opened() {
			return state.getValueOrElse(BlockStateProperties.OPEN, false);
		}
	}

	/**
	 * A container the player opened: a block whose block entity is a container (a chest, barrel, shulker box, furnace,
	 * hopper and the like) or an ender chest, whose menu opened. {@code state} is the block's, so a card can tell a chest
	 * from a barrel. A double chest is the half they clicked.
	 */
	record ContainerOpen(ResourceKey<Level> dimension, BlockPos pos, BlockState state, long tick) implements PlayerAction {
		public ContainerOpen {
			pos = pos.immutable();
		}
	}
}
