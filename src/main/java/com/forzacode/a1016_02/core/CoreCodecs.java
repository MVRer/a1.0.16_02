package com.forzacode.a1016_02.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;

/** Small codec helpers shared by core's saved data. */
public final class CoreCodecs {
	private CoreCodecs() {
	}

	/** Encodes an enum by its {@code name()}. */
	public static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
		return Codec.STRING.xmap(name -> Enum.valueOf(type, name), Enum::name);
	}

	/** A mutable set stored as a list. */
	public static <T> Codec<Set<T>> setOf(Codec<T> element) {
		return element.listOf().xmap(HashSet::new, List::copyOf);
	}
}
