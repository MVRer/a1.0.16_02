package com.forzacode.a1016_02.lore.mixin;

import com.forzacode.a1016_02.lore.Telling;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;

/** An item destroyed by damage (lava, fire, cactus, explosions): a burnt list, or a book a player wrote about him. */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
	@Inject(method = "hurtServer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V"))
	private void a1016$destroyed(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		Telling.onItemDestroyed((ItemEntity) (Object) this, level, source);
	}
}
