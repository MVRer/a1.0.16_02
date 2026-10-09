package com.forzacode.a1016_02.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.state.BlockState;

/** A block the subject placed, as remembered in {@link HerobrineState} (first block, crafting table, chest). */
public record PlacedBlock(GlobalPos pos, BlockState state) {
	public static final Codec<PlacedBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("pos").forGetter(PlacedBlock::pos),
			BlockState.CODEC.fieldOf("state").forGetter(PlacedBlock::state)
	).apply(i, PlacedBlock::new));
}
