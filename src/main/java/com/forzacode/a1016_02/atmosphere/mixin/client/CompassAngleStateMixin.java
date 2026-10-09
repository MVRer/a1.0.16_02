package com.forzacode.a1016_02.atmosphere.mixin.client;

import com.forzacode.a1016_02.atmosphere.client.ClientAtmosphere;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Compass drift: while a drift runs, the local player's spawn compass points at the drift target instead. */
@Mixin(CompassAngleState.class)
abstract class CompassAngleStateMixin {
	@Shadow
	@Final
	private CompassAngleState.CompassTarget compassTarget;

	@ModifyExpressionValue(method = "calculate", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/properties/numeric/CompassAngleState$CompassTarget;get(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ItemOwner;)Lnet/minecraft/core/GlobalPos;"))
	private @Nullable GlobalPos a1016_02$drift(@Nullable GlobalPos original, ItemStack stack, ClientLevel level, int seed, ItemOwner owner) {
		if (compassTarget != CompassAngleState.CompassTarget.SPAWN) {
			return original;
		}
		return ClientAtmosphere.compassTarget(level, owner, original);
	}
}
