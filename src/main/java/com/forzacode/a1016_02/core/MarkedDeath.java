package com.forzacode.a1016_02.core;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

import org.jspecify.annotations.Nullable;

/**
 * A death that counts toward Ending B: its listed cause, where it happened, the in-game day, and whose death it was
 * (D-063). {@code player} is null only on a record not yet added to the state: {@link HerobrineState} fills it in,
 * and a v1.0 save's deaths (which did not record it) belong to the first known player.
 */
public record MarkedDeath(String cause, GlobalPos pos, long day, @Nullable UUID player) {
	public static final Codec<MarkedDeath> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("cause").forGetter(MarkedDeath::cause),
			GlobalPos.CODEC.fieldOf("pos").forGetter(MarkedDeath::pos),
			Codec.LONG.fieldOf("day").forGetter(MarkedDeath::day),
			UUIDUtil.CODEC.optionalFieldOf("player").forGetter(d -> Optional.ofNullable(d.player()))
	).apply(i, (cause, pos, day, player) -> new MarkedDeath(cause, pos, day, player.orElse(null))));

	/** A death whose player {@link HerobrineState#addMarkedDeath(UUID, MarkedDeath)} sets. */
	public MarkedDeath(String cause, GlobalPos pos, long day) {
		this(cause, pos, day, null);
	}

	/** The same death, as {@code player}'s. */
	public MarkedDeath withPlayer(UUID player) {
		return player.equals(this.player) ? this : new MarkedDeath(cause, pos, day, player);
	}
}
