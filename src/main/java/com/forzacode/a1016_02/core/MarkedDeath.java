package com.forzacode.a1016_02.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;

/** A death that counts toward Ending B: its listed cause, where it happened, and the in-game day. */
public record MarkedDeath(String cause, GlobalPos pos, long day) {
	public static final Codec<MarkedDeath> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("cause").forGetter(MarkedDeath::cause),
			GlobalPos.CODEC.fieldOf("pos").forGetter(MarkedDeath::pos),
			Codec.LONG.fieldOf("day").forGetter(MarkedDeath::day)
	).apply(i, MarkedDeath::new));
}
