package com.forzacode.a1016_02.entity.mixin.client;

import java.nio.file.Path;

import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.entity.sc.HisView;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;

/**
 * The self-created layer's 03 in the world list (ARCHITECTURE, the layer's rule 3: what the client shows with no server
 * running asks the switch itself): right before vanilla's entry checks and loads its picture ({@code validateIconFile},
 * then {@code loadIcon}, both in its constructor), the entry's own path becomes {@link HisView#listedIcon}: the world's old
 * icon while this client's switch is off, so turning the layer off puts the old picture back in the list at once, before
 * that world is opened again (its server start then writes it back, {@code HisView.restore}). Vanilla's own checks still
 * run on it. Only the entry's copy of the path changes; the summary, and 14's reading of the world's folder through it,
 * keep vanilla's.
 */
@Mixin(WorldSelectionList.WorldListEntry.class)
abstract class WorldListEntryMixin {
	@Shadow
	private @Nullable Path iconFile;

	@Inject(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/worldselection/WorldSelectionList$WorldListEntry;validateIconFile()V"))
	private void a1016_02$hisView(CallbackInfo ci) {
		iconFile = HisView.listedIcon(iconFile, SelfCreated.enabled());
	}
}
