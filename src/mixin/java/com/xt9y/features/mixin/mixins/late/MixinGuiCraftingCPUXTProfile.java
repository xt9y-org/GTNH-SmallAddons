package com.xt9y.features.mixin.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.xtprofile.XTProfileClientOverlay;

import appeng.client.gui.implementations.GuiCraftingCPU;

@Mixin(value = GuiCraftingCPU.class, remap = false)
public abstract class MixinGuiCraftingCPUXTProfile {

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void xtprofile$keyTyped(char character, int key, CallbackInfo ci) {
        if (XTProfileClientOverlay.handleSearchKey(character, key)) ci.cancel();
    }
}
