package com.forzacode.a1016_02.atmosphere.mixin;

import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.WoodType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A fence gate's own wood type (its open and close sounds), for the door echo (D-076). Read only. */
@Mixin(FenceGateBlock.class)
public interface FenceGateBlockAccessor {
	@Accessor("type")
	WoodType a1016_02$type();
}
