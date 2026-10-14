package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What kind of world this is, rolled once from the world seed plus a hidden salt (D-008).
 *
 * @param habits    exactly two habits
 * @param density   how many old scars exist
 * @param tempo     scales stage times through {@link Tempo#paceFactor()}
 * @param fragments the story fragments this world has ("F01".."F30"); always contains {@link #FIXED_FRAGMENTS} (D-003),
 *                  also when an old save's list does not (a fragment fixed later reaches old worlds too). The clue
 *                  fragments ({@link #CLUE_FRAGMENTS}) are never listed: they are always enabled, see {@link #isEnabled}
 * @param signature the world's one signature moment
 */
public record WorldProfile(Set<Habit> habits, Density density, Tempo tempo, Set<String> fragments, Signature signature) {
	/**
	 * Always enabled: the core set (F01, F03, F06, F10), the Ending D chain (D-003), and F08, whose lines are the
	 * sentence Ending D's step 6 asks for (D-066).
	 */
	public static final Set<String> FIXED_FRAGMENTS = Set.of("F01", "F03", "F06", "F10", "F07", "F13", "F23", "F25", "F28", "F30", "F08");
	/**
	 * The story clues past F30 (D-066): Ending D's chamber plates and the team's note (F35, F36) and the late clues
	 * (F38 to F44). Never rolled: always enabled in every world, old saves included. Each one appears only once its
	 * step is reached.
	 */
	public static final Set<String> CLUE_FRAGMENTS = Set.of("F35", "F36", "F38", "F39", "F40", "F41", "F42", "F43", "F44");
	/** Rolled fragments need these to be enabled too (D-003). */
	public static final Map<String, List<String>> DEPENDENCIES = Map.of(
			"F16", List.of("F15"),
			"F22", List.of("F15"),
			"F29", List.of("F15", "F21")
	);
	/** About this many of the other 19 story fragments are rolled: 11, 12 or 13. */
	public static final int ROLLED_MIN = 11;
	public static final int ROLLED_MAX = 13;

	/** Every profile holds the fixed fragments: an old save gains the ones fixed since it was written. */
	public WorldProfile {
		if (!fragments.containsAll(FIXED_FRAGMENTS)) {
			Set<String> all = new TreeSet<>(fragments);
			all.addAll(FIXED_FRAGMENTS);
			fragments = Collections.unmodifiableSet(all);
		}
	}

	public static final Codec<WorldProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
			CoreCodecs.enumCodec(Habit.class).listOf().fieldOf("habits").forGetter(p -> List.copyOf(p.habits())),
			CoreCodecs.enumCodec(Density.class).fieldOf("density").forGetter(WorldProfile::density),
			CoreCodecs.enumCodec(Tempo.class).fieldOf("tempo").forGetter(WorldProfile::tempo),
			Codec.STRING.listOf().fieldOf("fragments").forGetter(p -> List.copyOf(p.fragments())),
			CoreCodecs.enumCodec(Signature.class).fieldOf("signature").forGetter(WorldProfile::signature)
	).apply(i, (habits, density, tempo, fragments, signature) ->
			new WorldProfile(Collections.unmodifiableSet(EnumSet.copyOf(habits)), density, tempo,
					Collections.unmodifiableSet(new TreeSet<>(fragments)), signature)));

	/** The 30 story fragment ids, "F01".."F30": the ones a world rolls. */
	public static List<String> storyFragmentIds() {
		List<String> ids = new ArrayList<>(30);
		for (int n = 1; n <= 30; n++) {
			ids.add(String.format(Locale.ROOT, "F%02d", n));
		}
		return ids;
	}

	/** Every fragment id, in order: the 30 story fragments, then the clue fragments ({@link #CLUE_FRAGMENTS}). */
	public static List<String> allFragmentIds() {
		List<String> ids = storyFragmentIds();
		ids.addAll(new TreeSet<>(CLUE_FRAGMENTS));
		return ids;
	}

	/** True if this fragment may exist in this world: rolled or fixed, or one of the always-on clues. */
	public boolean isEnabled(String id) {
		return fragments.contains(id) || CLUE_FRAGMENTS.contains(id);
	}

	/**
	 * Deterministic roll: the same seed and salt always give the same profile.
	 * Note: F20 only ever appears on the Ending B path and F04 is an event after "Stop.", not a placed
	 * fragment; being enabled here only means they may happen in this world.
	 */
	public static WorldProfile roll(long seed, long salt) {
		Random random = new Random(seed * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(salt, 29) ^ 0xA1016_02L);

		List<Habit> habitPool = new ArrayList<>(List.of(Habit.values()));
		Collections.shuffle(habitPool, random);
		Set<Habit> habits = EnumSet.of(habitPool.get(0), habitPool.get(1));

		Density density = pick(Density.values(), random);
		Tempo tempo = pick(Tempo.values(), random);
		Signature signature = pick(Signature.values(), random);

		List<String> optional = new ArrayList<>(storyFragmentIds());
		optional.removeAll(FIXED_FRAGMENTS);
		Collections.shuffle(optional, random);
		int target = ROLLED_MIN + random.nextInt(ROLLED_MAX - ROLLED_MIN + 1);

		Set<String> rolled = new LinkedHashSet<>();
		for (String id : optional) {
			if (rolled.size() >= target) {
				break;
			}
			Set<String> withDeps = new LinkedHashSet<>();
			withDeps.add(id);
			withDeps.addAll(DEPENDENCIES.getOrDefault(id, List.of()));
			withDeps.removeAll(rolled);
			if (rolled.size() + withDeps.size() <= target) {
				rolled.addAll(withDeps);
			}
		}

		Set<String> fragments = new TreeSet<>(FIXED_FRAGMENTS);
		fragments.addAll(rolled);
		return new WorldProfile(Collections.unmodifiableSet(habits), density, tempo, Collections.unmodifiableSet(fragments), signature);
	}

	public boolean hasHabit(Habit habit) {
		return habits.contains(habit);
	}

	/** D-004: still burning happens once, either as the signature or as F21's furnace. */
	public boolean hasStillBurning() {
		return signature == Signature.STILL_BURNING || fragments.contains("F21");
	}

	/** D-005: the copy of your house is built once, after day 20, either as the signature or for F27. */
	public boolean hasHouseCopy() {
		return signature == Signature.HOUSE_ELSEWHERE || fragments.contains("F27");
	}

	private static <T> T pick(T[] values, Random random) {
		return values[random.nextInt(values.length)];
	}
}
