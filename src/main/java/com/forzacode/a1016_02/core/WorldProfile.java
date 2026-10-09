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
 * @param fragments enabled fragment ids ("F01".."F30"); always contains {@link #FIXED_FRAGMENTS} (D-003)
 * @param signature the world's one signature moment
 */
public record WorldProfile(Set<Habit> habits, Density density, Tempo tempo, Set<String> fragments, Signature signature) {
	/** Always enabled: the core set (F01, F03, F06, F10) plus the Ending D chain (D-003). */
	public static final Set<String> FIXED_FRAGMENTS = Set.of("F01", "F03", "F06", "F10", "F07", "F13", "F23", "F25", "F28", "F30");
	/** Rolled fragments need these to be enabled too (D-003). */
	public static final Map<String, List<String>> DEPENDENCIES = Map.of(
			"F16", List.of("F15"),
			"F22", List.of("F15"),
			"F29", List.of("F15", "F21")
	);
	/** About this many of the other 20 fragments are rolled: 11, 12 or 13. */
	public static final int ROLLED_MIN = 11;
	public static final int ROLLED_MAX = 13;

	public static final Codec<WorldProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
			CoreCodecs.enumCodec(Habit.class).listOf().fieldOf("habits").forGetter(p -> List.copyOf(p.habits())),
			CoreCodecs.enumCodec(Density.class).fieldOf("density").forGetter(WorldProfile::density),
			CoreCodecs.enumCodec(Tempo.class).fieldOf("tempo").forGetter(WorldProfile::tempo),
			Codec.STRING.listOf().fieldOf("fragments").forGetter(p -> List.copyOf(p.fragments())),
			CoreCodecs.enumCodec(Signature.class).fieldOf("signature").forGetter(WorldProfile::signature)
	).apply(i, (habits, density, tempo, fragments, signature) ->
			new WorldProfile(Collections.unmodifiableSet(EnumSet.copyOf(habits)), density, tempo,
					Collections.unmodifiableSet(new TreeSet<>(fragments)), signature)));

	/** All 30 fragment ids, "F01".."F30". */
	public static List<String> allFragmentIds() {
		List<String> ids = new ArrayList<>(30);
		for (int n = 1; n <= 30; n++) {
			ids.add(String.format(Locale.ROOT, "F%02d", n));
		}
		return ids;
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

		List<String> optional = new ArrayList<>(allFragmentIds());
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
