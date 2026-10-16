package com.forzacode.a1016_02.atmosphere.mixin;

import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A trapdoor's own set type (its open and close sounds, whether a hand opens it), for the door echo (D-076). Read only. */
@Mixin(TrapDoorBlock.class)
public interface TrapDoorBlockAccessor {
	@Accessor("type")
	BlockSetType a1016_02$type();
}
