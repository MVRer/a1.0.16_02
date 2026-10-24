package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.nbt.CompoundTag;

import org.jspecify.annotations.Nullable;

/**
 * What a feature fixed the first time it built something in a world (R12-2), so a later change of its config or its
 * code never moves what it goes on building there: the line's (19) segments are laid out from the config's numbers on
 * every decoration, not saved as structure starts are, so each segment's whole layout and its shape's version are fixed
 * here on its first decoration and read back on every later one. Any thread reads and fixes (worldgen); the server
 * thread loads it from the kit's data ({@link Sc2Data}, key {@code plans}) before worldgen may run and drains what was
 * fixed into it every tick and at stop. Until it is loaded nothing that needs it builds ({@link #ready}).
 */
public final class Plans {
	private static final Map<String, CompoundTag> FIXED = new ConcurrentHashMap<>();
	private static final Queue<String> NEW = new ConcurrentLinkedQueue<>();
	private static volatile boolean ready;

	private Plans() {
	}

	/** Takes this world's fixed plans (server thread, at start, before worldgen may run). */
	public static void load(Sc2Data data) {
		FIXED.clear();
		NEW.clear();
		FIXED.putAll(data.plans());
		ready = true;
	}

	/** True once this world's plans are loaded (a feature that needs them builds nothing before). Any thread. */
	public static boolean ready() {
		return ready;
	}

	/** The plan fixed under this key, or null. Any thread. */
	public static @Nullable CompoundTag fixed(String key) {
		CompoundTag tag = FIXED.get(key);
		return tag == null ? null : tag.copy();
	}

	/** Every fixed plan whose key starts with {@code prefix} (a copy each). Any thread. */
	public static List<CompoundTag> fixedUnder(String prefix) {
		List<CompoundTag> out = new ArrayList<>();
		FIXED.forEach((key, tag) -> {
			if (key.startsWith(prefix)) {
				out.add(tag.copy());
			}
		});
		return out;
	}

	/**
	 * Fixes {@code plan} under {@code key} unless a plan is fixed there already; returns the one in force (the first
	 * fixed wins, on any thread). The server thread keeps it in the world's data at its next drain.
	 */
	public static CompoundTag fix(String key, CompoundTag plan) {
		CompoundTag before = FIXED.putIfAbsent(key, plan.copy());
		if (before != null) {
			return before.copy();
		}
		NEW.add(key);
		return plan.copy();
	}

	/** True while something fixed waits to be kept. Any thread. */
	public static boolean pending() {
		return !NEW.isEmpty();
	}

	/** Keeps everything fixed since the last drain in {@code data}; returns how many. Server thread. */
	public static int drain(Sc2Data data) {
		int kept = 0;
		for (String key = NEW.poll(); key != null; key = NEW.poll()) {
			CompoundTag tag = FIXED.get(key);
			if (tag != null && data.fixPlan(key, tag)) {
				kept++;
			}
		}
		return kept;
	}

	/** Tests: forgets the plans under this prefix here (not in any data). */
	public static void forget(String prefix) {
		FIXED.keySet().removeIf(key -> key.startsWith(prefix));
		NEW.removeIf(key -> key.startsWith(prefix));
	}

	/** Server stop: nothing is fixed until the next world's are loaded. */
	public static void clear() {
		ready = false;
		FIXED.clear();
		NEW.clear();
	}
}
