package com.forzacode.a1016_02.ending.mixin.d;

import com.forzacode.a1016_02.ending.d.Marks;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Ending D follows wood cut in the untouched grove: planks crafted from grove logs keep the grove mark (both the
 * inventory grid and the crafting table go through this method). Nothing else about crafting changes.
 */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {
	@ModifyArg(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"), index = 1)
	private static ItemStack a1016_02$groveWood(ItemStack result, @Local(argsOnly = true) CraftingContainer container) {
		return Marks.onCraft(container, result);
	}
}
