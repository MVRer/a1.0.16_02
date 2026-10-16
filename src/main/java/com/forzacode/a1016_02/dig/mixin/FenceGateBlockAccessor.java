package com.forzacode.a1016_02.dig.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.WoodType;

/** A fence gate's own wood type (its open and close sounds, D-077's blind door); the field is private. */
@Mixin(FenceGateBlock.class)
public interface FenceGateBlockAccessor {
	@Accessor("type")
	WoodType a1016_02$type();
}
