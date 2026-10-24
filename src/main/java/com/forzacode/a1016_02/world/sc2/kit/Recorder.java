package com.forzacode.a1016_02.world.sc2.kit;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Remains;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Hands places from worldgen threads to the server thread: the first piece of a start to be placed (and a feature's
 * segment) offers its place; the server thread drains the queue into {@code WorldData}'s {@code sc2} ({@link Sc2Data})
 * every tick, where it becomes known ({@code Remains.known}). A start offered again (its chunk saved and loaded before
 * all of it was placed) is known by its level, kind and origin and added once.
 */
public final class Recorder {
	/** One place as worldgen saw it (its start tied to its kind's shape version). */
	public record Entry(String kind, Start start, Remains.Place place, CompoundTag extra) {
		public Entry {
			extra = extra.copy();
			start = Sc2Kind.resolved(kind, start);
		}

		public ResourceKey<Level> dimension() {
			return place.dimension();
		}
	}

	private static final Queue<Entry> QUEUE = new ConcurrentLinkedQueue<>();

	private Recorder() {
	}

	/** A shape's place, as its first piece is placed. Any thread. */
	public static void offer(String kind, Start start, ResourceKey<Level> dimension, Remains.Kind remains, Shape shape) {
		Remains.Place place = new Remains.Place(remains, dimension, shape.box(), shape.anchor(), shape.entrance(), shape.entranceFacing(), shape.apex(),
				start.seed(), start.variant());
		offer(new Entry(kind, start, place, shape.extra()));
	}

	/** Any place (a feature's segment). Any thread. */
	public static void offer(Entry entry) {
		QUEUE.add(entry);
	}

	/** True while something waits. Any thread. */
	public static boolean pending() {
		return !QUEUE.isEmpty();
	}

	/** Moves everything waiting into {@code data}; returns how many places were new. Server thread. */
	public static int drain(Sc2Data data) {
		int added = 0;
		for (Entry entry = QUEUE.poll(); entry != null; entry = QUEUE.poll()) {
			if (data.add(Sc2Data.Known.of(entry.place(), entry.kind(), entry.start(), entry.extra()))) {
				added++;
				BlockPos at = entry.place().anchor();
				// Debug only: where they are is not for the log a player reads.
				A1016_02.LOGGER.debug("[a1016] s2: s{} kept at {} {} {}", entry.place().kind().number(), at.getX(), at.getY(), at.getZ());
			}
		}
		return added;
	}

	/** Forgets everything waiting (server stop). */
	public static void clear() {
		QUEUE.clear();
	}
}
