package com.forzacode.a1016_02.director.sc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.director.CardInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Places remember (docs/SELF_CREATED.md, 01; D-084), pure: the weights and their decay, the factor at a position and
 * the trusted chunk, over one player's {@link PlaceMemory} and {@link Sc01Config}.
 *
 * <ul>
 *   <li>A place weighs {@code weightAmbient}, {@code weightMinor} or {@code weightMajor} by its card's tier (a signature as
 *   a major), plus {@code weightSighting} for a SIGHTING card, halved every {@code halfLifeDays} in-game days. A place
 *   is remembered while it weighs at least {@link #FORGOTTEN}; a forgotten one counts nowhere.</li>
 *   <li>The factor at a position: {@code min(maxFactor, 1 + the weights of the remembered places within radius)} (3D,
 *   the same dimension). In the trusted chunk it is at least {@code trustedFactor}. Exactly 1 (nothing remembered near,
 *   not trusted) reads as {@code awayFactor}, for tuning.</li>
 *   <li>The trusted chunk: the chunk with the most dwell, at least {@code trustedMinHours} of the player's play, with no
 *   remembered place within {@code radius} of its centre (horizontally); a debug trust ({@link PlaceMemory.Trust})
 *   names the chunk instead while it lasts, the place rule still holding.</li>
 * </ul>
 * The director applies the factor only from Traces on, after the player's own first hour, to the ambient and minor
 * rates ({@code DirectorBrain}); this class knows nothing of that.
 */
public final class Places {
	/** A place that weighs less than this is forgotten: it adds nothing and keeps no chunk from being trusted. */
	public static final double FORGOTTEN = 0.05;
	private static final double DAY_TICKS = 24000.0;

	private Places() {
	}

	/**
	 * The layer's common gates for the factor (docs/SELF_CREATED.md, "Features"): true while the story is quiet for good
	 * ({@code director:silence_forever}, {@code ending:path=C}), over ({@code ending:ended}, {@code ending:d_complete}), in
	 * Ending D's last minute or Ending A's false peace ({@code director:obeyed_after_stop}). The live factor is 1 then.
	 */
	public static boolean storyQuiet(HerobrineState state) {
		return state.hasFlag("director:silence_forever") || state.hasFlag("ending:path=C") || state.hasFlag("ending:ended")
				|| state.hasFlag("ending:d_complete") || state.hasFlag(TraceService.LAST_MINUTE_FLAG) || state.hasFlag("director:obeyed_after_stop");
	}

	/** A place's weight when new. */
	public static double weight(Tier tier, boolean sighting, Sc01Config cfg) {
		double base = switch (tier) {
			case AMBIENT -> cfg.weightAmbient;
			case MINOR -> cfg.weightMinor;
			case MAJOR, SIGNATURE -> cfg.weightMajor;
		};
		return Math.max(0, base + (sighting ? cfg.weightSighting : 0));
	}

	/** A place's weight at these day ticks: halved every {@code halfLifeDays} since it happened (none before it). */
	public static double weightNow(PlaceMemory.Place place, long dayTicks, Sc01Config cfg) {
		double weight = weight(place.tier(), place.sighting(), cfg);
		if (cfg.halfLifeDays <= 0) {
			return weight;
		}
		double days = Math.max(0, dayTicks - place.dayTicks()) / DAY_TICKS;
		return weight * Math.pow(0.5, days / cfg.halfLifeDays);
	}

	/** True while the place still weighs at least {@link #FORGOTTEN}. */
	public static boolean remembered(PlaceMemory.Place place, long dayTicks, Sc01Config cfg) {
		return weightNow(place, dayTicks, cfg) >= FORGOTTEN;
	}

	/** True if the place is in this dimension within {@code radius} blocks (3D) of {@code pos}. */
	public static boolean near(PlaceMemory.Place place, ResourceKey<Level> dimension, BlockPos pos, Sc01Config cfg) {
		return place.dimension().equals(dimension) && place.pos().distSqr(pos) <= (double) cfg.radius * cfg.radius;
	}

	/** {@code min(maxFactor, 1 + the weights of the remembered places near pos)}: places alone, without the trusted chunk. */
	public static double dread(PlaceMemory memory, ResourceKey<Level> dimension, BlockPos pos, long dayTicks, Sc01Config cfg) {
		double sum = 0;
		for (PlaceMemory.Place place : memory.places) {
			if (near(place, dimension, pos, cfg)) {
				double weight = weightNow(place, dayTicks, cfg);
				if (weight >= FORGOTTEN) {
					sum += weight;
				}
			}
		}
		return Math.min(Math.max(1, cfg.maxFactor), 1 + sum);
	}

	/**
	 * The trusted chunk now, or empty: the debug trust while it lasts, else the chunk with the most dwell if it has at
	 * least {@code trustedMinHours} of play; either way only while no remembered place is within {@code radius} of its
	 * centre.
	 */
	public static Optional<PlaceMemory.Chunk> trusted(PlaceMemory memory, long playTicks, long dayTicks, long hourTicks, Sc01Config cfg) {
		PlaceMemory.Chunk chunk = candidate(memory, playTicks, hourTicks, cfg);
		return chunk == null || placeNearCentre(memory, chunk, dayTicks, cfg) != null ? Optional.empty() : Optional.of(chunk);
	}

	/** The chunk that would be trusted but for the place rule: the debug trust while it lasts, else the most dwelt chunk with enough dwell. */
	static PlaceMemory.Chunk candidate(PlaceMemory memory, long playTicks, long hourTicks, Sc01Config cfg) {
		if (memory.trust != null && playTicks < memory.trust.untilPlayTicks()) {
			return memory.trust.chunk();
		}
		Map.Entry<PlaceMemory.Chunk, Double> most = mostDwelt(memory);
		return most != null && most.getValue() >= cfg.trustedMinHours * hourTicks ? most.getKey() : null;
	}

	/** The chunk with the most dwell, or null. */
	public static Map.Entry<PlaceMemory.Chunk, Double> mostDwelt(PlaceMemory memory) {
		Map.Entry<PlaceMemory.Chunk, Double> most = null;
		for (Map.Entry<PlaceMemory.Chunk, Double> entry : memory.dwell.entrySet()) {
			if (most == null || entry.getValue() > most.getValue()) {
				most = entry;
			}
		}
		return most;
	}

	/** A remembered place within {@code radius} of the chunk's centre (horizontally), or null. */
	static PlaceMemory.Place placeNearCentre(PlaceMemory memory, PlaceMemory.Chunk chunk, long dayTicks, Sc01Config cfg) {
		for (PlaceMemory.Place place : memory.places) {
			if (!place.dimension().equals(chunk.dimension()) || !remembered(place, dayTicks, cfg)) {
				continue;
			}
			double dx = place.pos().getX() + 0.5 - chunk.centerX();
			double dz = place.pos().getZ() + 0.5 - chunk.centerZ();
			if (dx * dx + dz * dz <= (double) cfg.radius * cfg.radius) {
				return place;
			}
		}
		return null;
	}

	/**
	 * The factor at this position: the places' ({@link #dread}), at least {@code trustedFactor} in the trusted chunk,
	 * never above {@code maxFactor}; exactly 1 reads as {@code awayFactor}.
	 */
	public static double factor(PlaceMemory memory, ResourceKey<Level> dimension, BlockPos pos, long playTicks, long dayTicks, long hourTicks,
			Sc01Config cfg) {
		return factor(memory, dimension, pos, playTicks, dayTicks, hourTicks, false, cfg);
	}

	/**
	 * {@link #factor(PlaceMemory, ResourceKey, BlockPos, long, long, long, Sc01Config)}, and 01+ (docs/SELF_CREATED_2.md):
	 * {@code atRemains} (the position inside one of his places or within {@code radius} of its way in) makes it at least
	 * {@code remainsFactor}, never above {@code maxFactor}.
	 */
	public static double factor(PlaceMemory memory, ResourceKey<Level> dimension, BlockPos pos, long playTicks, long dayTicks, long hourTicks,
			boolean atRemains, Sc01Config cfg) {
		double factor = dread(memory, dimension, pos, dayTicks, cfg);
		double max = Math.max(1, cfg.maxFactor);
		Optional<PlaceMemory.Chunk> trusted = trusted(memory, playTicks, dayTicks, hourTicks, cfg);
		if (trusted.isPresent() && trusted.get().contains(dimension, pos)) {
			factor = Math.min(max, Math.max(factor, cfg.trustedFactor));
		}
		if (atRemains) {
			factor = Math.min(max, Math.max(factor, cfg.remainsFactor));
		}
		return factor == 1.0 ? Math.max(0, cfg.awayFactor) : factor;
	}

	/** 01+: an answer of one of his places ({@code REMAINS_ANSWERED}) weighs as a minor's place (it is not a card, and no sighting). */
	public static final Tier ANSWER_TIER = Tier.MINOR;

	/** 01+: remembers an answer of one of his places for the player, at this position and time ({@link #ANSWER_TIER}). */
	public static void rememberAnswer(PlaceMemory memory, ResourceKey<Level> dimension, BlockPos pos, long playTicks, long dayTicks, Sc01Config cfg) {
		memory.record(new PlaceMemory.Place(dimension, pos.immutable(), playTicks, dayTicks, ANSWER_TIER, false), cfg.maxPlaces);
	}

	/**
	 * The layer's cards whose moment is at the player's home, not where they stand when it fires or arms (they fire
	 * while the player is away from it, or arm now and play there later): 05 and 05b (the house), 07 (a named mob by the
	 * base), 08 and 09 (the base's blocks, the door), 12 (the bed). Remembered at the home ({@link #placeOf}).
	 */
	public static final Set<String> AT_HOME = Set.of("sc_05", "sc_05b", "sc_07", "sc_08", "sc_09", "sc_12");

	/**
	 * The layer's cards that arm now and play later at a spot they pick then, by the cause of the trace that plays
	 * them: 15 (the block on the player's route). Not remembered when they arm; remembered where that trace is ledgered,
	 * once, for the player it was armed for (the director's {@code TRACED} listener). An arm that runs out leaves no place.
	 */
	public static final Map<String, String> AT_TRACE = Map.of("sc_15", "accident:sc15");

	/**
	 * Where a fire of this card is remembered for a player standing at {@code stand}: for a card at home
	 * ({@link #AT_HOME}) their home, the base in this dimension if they have one, else the middle of the chunk they dwell
	 * in most here (01's own idea of home) at their height; for any other card where they stand.
	 */
	public static BlockPos placeOf(CardInfo card, PlaceMemory memory, ResourceKey<Level> dimension, BlockPos stand, Optional<GlobalPos> base) {
		if (!AT_HOME.contains(card.id())) {
			return stand;
		}
		if (base.isPresent() && base.get().dimension().equals(dimension)) {
			return base.get().pos();
		}
		PlaceMemory.Chunk home = null;
		double most = 0;
		for (Map.Entry<PlaceMemory.Chunk, Double> entry : memory.dwell.entrySet()) {
			if (entry.getKey().dimension().equals(dimension) && entry.getValue() > most) {
				home = entry.getKey();
				most = entry.getValue();
			}
		}
		return home == null ? stand : new BlockPos((home.x() << 4) + 8, stand.getY(), (home.z() << 4) + 8);
	}

	/** Remembers a fire (real or felt) of this card for the player, at this position and time. */
	public static void remember(PlaceMemory memory, ResourceKey<Level> dimension, BlockPos pos, CardInfo card, long playTicks, long dayTicks,
			Sc01Config cfg) {
		memory.record(new PlaceMemory.Place(dimension, pos.immutable(), playTicks, dayTicks, card.tier(), card.has(CardTag.SIGHTING)), cfg.maxPlaces);
	}

	/** {@code /a1016 sc 01}'s lines for one player, in plain words: the factor now, the nearest places and the trusted chunk. */
	public static List<String> describe(String name, PlaceMemory memory, ResourceKey<Level> dimension, BlockPos pos, long playTicks, long dayTicks,
			long hourTicks, boolean applies, Sc01Config cfg) {
		return describe(name, memory, dimension, pos, playTicks, dayTicks, hourTicks, applies, false, cfg);
	}

	/** {@link #describe(String, PlaceMemory, ResourceKey, BlockPos, long, long, long, boolean, Sc01Config)} with 01+'s {@code atRemains}. */
	public static List<String> describe(String name, PlaceMemory memory, ResourceKey<Level> dimension, BlockPos pos, long playTicks, long dayTicks,
			long hourTicks, boolean applies, boolean atRemains, Sc01Config cfg) {
		List<String> lines = new ArrayList<>();
		double dread = dread(memory, dimension, pos, dayTicks, cfg);
		Optional<PlaceMemory.Chunk> trusted = trusted(memory, playTicks, dayTicks, hourTicks, cfg);
		boolean inTrusted = trusted.isPresent() && trusted.get().contains(dimension, pos);
		lines.add(String.format(Locale.ROOT, "sc 01: %s: factor here x%.2f (places x%.2f%s%s)%s", name,
				factor(memory, dimension, pos, playTicks, dayTicks, hourTicks, atRemains, cfg), dread, inTrusted ? ", in the trusted chunk" : "",
				atRemains ? String.format(Locale.ROOT, ", at a place of 16 to 20: at least x%.2f", Math.min(Math.max(1, cfg.maxFactor), cfg.remainsFactor)) : "",
				applies ? "" : "; not applied now (only from Traces, after their first hour)"));
		List<PlaceMemory.Place> sorted = new ArrayList<>(memory.places);
		sorted.sort(Comparator.comparingDouble((PlaceMemory.Place p) -> p.dimension().equals(dimension) ? p.pos().distSqr(pos) : Double.MAX_VALUE));
		StringBuilder near = new StringBuilder();
		int shown = 0;
		for (PlaceMemory.Place place : sorted) {
			if (shown++ >= 8) {
				break;
			}
			String where = place.dimension().equals(dimension) ? String.format(Locale.ROOT, "%.0f blocks", Math.sqrt(place.pos().distSqr(pos)))
					: place.dimension().identifier().getPath();
			near.append(near.isEmpty() ? "" : "; ").append(String.format(Locale.ROOT, "%s at %s, %s%s day %d, weight %.2f%s", where,
					place.pos().toShortString(), place.tier().name().toLowerCase(Locale.ROOT), place.sighting() ? " sighting" : "", place.day(),
					weightNow(place, dayTicks, cfg), remembered(place, dayTicks, cfg) ? "" : " (forgotten)"));
		}
		lines.add(String.format(Locale.ROOT, "sc 01: %d of %d places%s", memory.places.size(), cfg.maxPlaces, near.isEmpty() ? "" : ": " + near));
		Map.Entry<PlaceMemory.Chunk, Double> most = mostDwelt(memory);
		String dwell = most == null ? "no dwell yet"
				: String.format(Locale.ROOT, "most dwell %s %.2f h of %.2f h", chunk(most.getKey()), most.getValue() / hourTicks, cfg.trustedMinHours);
		String trust = memory.trust != null && playTicks < memory.trust.untilPlayTicks()
				? String.format(Locale.ROOT, ", debug trust %s for %.0f more min", chunk(memory.trust.chunk()),
						(memory.trust.untilPlayTicks() - playTicks) * 60.0 / hourTicks)
				: "";
		String answer;
		if (trusted.isPresent()) {
			answer = "trusted chunk " + chunk(trusted.get()) + (inTrusted ? " (you are in it)" : "");
		} else {
			PlaceMemory.Chunk candidate = candidate(memory, playTicks, hourTicks, cfg);
			PlaceMemory.Place blocking = candidate == null ? null : placeNearCentre(memory, candidate, dayTicks, cfg);
			answer = blocking == null ? "no trusted chunk" : "no trusted chunk (" + chunk(candidate) + " has a place at " + blocking.pos().toShortString() + ")";
		}
		lines.add("sc 01: " + answer + " | " + dwell + trust);
		return lines;
	}

	private static String chunk(PlaceMemory.Chunk chunk) {
		return String.format(Locale.ROOT, "[%d, %d] (blocks %d %d)", chunk.x(), chunk.z(), chunk.x() << 4, chunk.z() << 4);
	}
}
