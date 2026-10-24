package com.xt9y.features.mixin.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.xtprofile.XTProfileClientOverlay;

import appeng.client.gui.AEBaseGui;

@Mixin(value = AEBaseGui.class, remap = false)
public abstract class MixinAEBaseGuiXTProfile {

    @Inject(
        method = "drawScreen",
        at = @At(value = "INVOKE", target = "Lappeng/client/gui/AEBaseGui$AEGuiTooltip;draw()V"))
    private void xtprofile$renderBeforeTooltip(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        XTProfileClientOverlay.renderBeforeTooltip((AEBaseGui) (Object) this, mouseX, mouseY);
    }
}
