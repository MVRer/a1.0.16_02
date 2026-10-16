package com.forzacode.a1016_02.dig.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** A trapdoor's own set type (its open and close sounds, D-077's blind door); {@code getType()} is protected. */
@Mixin(TrapDoorBlock.class)
public interface TrapDoorBlockAccessor {
	@Accessor("type")
	BlockSetType a1016_02$type();
}
