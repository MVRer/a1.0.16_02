package com.forzacode.a1016_02.lore.mixin;

import com.forzacode.a1016_02.lore.sc.TheRecord;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.commands.SeedCommand;

/**
 * The self-created layer's 14 (c): {@code /seed}'s answer is vanilla's own ("Seed: [...]", the number clickable to copy as
 * vanilla's), around the text {@link TheRecord#seedText} gives: the real seed, or once per world the canon one. Only the
 * shown component changes; the command's result stays the real seed's.
 */
@Mixin(SeedCommand.class)
public abstract class SeedCommandMixin {
	@WrapOperation(method = "lambda$register$0", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/chat/ComponentUtils;copyOnClickText(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
	private static MutableComponent a1016_02$seed(String seed, Operation<MutableComponent> original,
			@Local(argsOnly = true) CommandContext<CommandSourceStack> context) {
		return original.call(TheRecord.LIVE.seedText(context.getSource(), seed));
	}
}
