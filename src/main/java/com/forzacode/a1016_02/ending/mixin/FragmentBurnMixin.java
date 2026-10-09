package com.forzacode.a1016_02.ending.mixin;

import com.forzacode.a1016_02.ending.EndingWatch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;

/** An item destroyed by damage: a fragment the subject threw into lava or fire counts toward Ending C. */
@Mixin(ItemEntity.class)
abstract class FragmentBurnMixin {
	@Inject(method = "hurtServer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V"))
	private void a1016$endingFragmentBurned(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		EndingWatch.onItemDestroyed((ItemEntity) (Object) this, level, source);
	}
}
