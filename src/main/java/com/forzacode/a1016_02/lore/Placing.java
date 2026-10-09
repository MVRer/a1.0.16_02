package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.HerobrineState;
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

		HerobrineState.FirstBlocks firstBlocks();

		/** The subject's base, or world spawn when there is none. */
		BlockPos base();

		/** World spawn. */
		BlockPos spawn();

		boolean enabled(String id);

		Set<String> read();
	}

	/**
	 * One attempt to place one fragment.
	 *
	 * @param origin      what distances are measured from (spawn, base, or the player for debug)
	 * @param minDistance own builds and sites at least this far (horizontal)
	 * @param maxDistance and at most this far
	 * @param tries       how many candidate spots an own build may try (each may load a chunk)
	 * @param traces      the trace service (forced in tests)
	 * @param playerName  replaces {@code [PLAYER NAME]}
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
