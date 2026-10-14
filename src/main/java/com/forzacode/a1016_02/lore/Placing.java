package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/** The inputs and outputs of one placement attempt. */
final class Placing {
	private Placing() {
	}

	/** What a placement rule may know about the world's story so far. Live from the save, or made up in tests. */
	interface Facts {
		/** Where another fragment was placed. */
		Optional<GlobalPos> placed(String id);

		/** A place lore remembered ({@link LoreData} anchors). */
		Optional<GlobalPos> anchor(String key);

		/** Remembers a place right away (for fragments placed in parts, like F30's twin signs). */
		void remember(String key, GlobalPos pos);

		/** The untouched grove's center, chosen once per world. */
		Optional<GlobalPos> grove();

		/**
		 * The story's first blocks: the first crafting table, chest and block of the one player stored under
		 * {@link Homes#FIRST_BLOCKS} (D-061; F13's cairn, F10). Never flips once chosen, even while they are away.
		 */
		HerobrineState.FirstBlocks firstBlocks();

		/**
		 * The story's home, which every "near your base" placement measures from: the {@link Homes#FIRST_BLOCKS}
		 * owner's base (their last one while they are away, else their first block), or world spawn when there is none.
		 */
		BlockPos base();

		/** The list's player lines now (F06, F23): one per known player, with their own causes ({@link LiveBooks#lines}). */
		List<LiveBooks.Line> list();

		/** World spawn. */
		BlockPos spawn();

		boolean enabled(String id);

		Set<String> read();

		/** World built the still-burning camp ({@code lore:still_burning}), where F21 goes (D-004). */
		boolean stillBurning();

		/** Every known player's base lore knows, in first-join order (late clues keep away from all of them). */
		List<GlobalPos> bases();

		/**
		 * In-game days (the world clock) Ending D's chain has been on this step, from the shared flags
		 * ({@link LateClues}); -1 while it is on another step or its clock has not started (step 1's starts when F23 is
		 * first read).
		 */
		long daysStuck(int step);

		/**
		 * The story owner's oldest placed blocks, oldest first (lore's own log, D-066 M11); for a save from before the
		 * log, the player-placed blocks nearest the owner's base. Some may be gone since. Empty with no owner.
		 */
		List<PlacedBlock> ownerPlacements();

		/** The block F13 fell back on (M11), once chosen: it is kept, so the cairn's block never flips. */
		Optional<PlacedBlock> cairnFallback();

		void rememberCairnFallback(PlacedBlock block);
	}

	/**
	 * One attempt to place one fragment.
	 *
	 * @param origin      what distances are measured from (spawn, the story's home, or the player for debug)
	 * @param minDistance own builds and sites at least this far (horizontal)
	 * @param maxDistance and at most this far
	 * @param tries       how many candidate spots an own build may try (each may load a chunk)
	 * @param traces      the trace service (forced in tests)
	 * @param playerName  replaces {@code [PLAYER NAME]} outside the list until a reader opens it: the story owner's
	 *                    name, or the player a debug placement is for (books name their reader once opened)
	 * @param ownBuilds   may build its own site when world or dig recorded none ({@link LoreConfig#ownBuildDelayMinutes})
	 * @param loads       set when the attempt stopped to wait for chunks ({@link ChunkGate})
	 */
	record Request(ServerLevel level, Fragment fragment, BlockPos origin, int minDistance, int maxDistance, int tries,
			TraceService traces, String playerName, Facts facts, RandomSource random, boolean ownBuilds, Loads loads) {
		String id() {
			return fragment.id();
		}

		Fragment.Placement placement() {
			return fragment.placement();
		}

		Request withBand(BlockPos newOrigin, int min, int max) {
			return new Request(level, fragment, newOrigin, min, max, tries, traces, playerName, facts, random, ownBuilds, loads);
		}
	}

	/** Whether an attempt is waiting for chunks to load (try again in a moment, with the same candidates). */
	static final class Loads {
		boolean waiting;

		boolean waiting() {
			return waiting;
		}
	}

	/** A successful placement. */
	static final class Result {
		/** The fragment's main position (its chest, sign, jukebox or moved block). */
		final BlockPos pos;
		/** Blocks that count as reading it when looked at (signs, the cairn's core). */
		final List<BlockPos> readTargets = new ArrayList<>();
		/** Places to remember in {@link LoreData}. */
		final Map<String, BlockPos> anchors = new LinkedHashMap<>();
		/** The registry site it filled (claimed), if any. */
		Optional<SiteRegistry.Site> site = Optional.empty();

		Result(BlockPos pos) {
			this.pos = pos.immutable();
		}

		Result read(BlockPos target) {
			readTargets.add(target.immutable());
			return this;
		}

		Result anchor(String key, BlockPos at) {
			anchors.put(key, at.immutable());
			return this;
		}

		Result site(SiteRegistry.Site used) {
			site = Optional.of(used);
			return this;
		}
	}
}
