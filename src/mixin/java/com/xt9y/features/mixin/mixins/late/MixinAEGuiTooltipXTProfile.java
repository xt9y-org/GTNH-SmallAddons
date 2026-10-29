package com.xt9y.features.mixin.mixins.late;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.XT9YConfig;
import com.xt9y.features.xtprofile.XTProfileClientOverlay;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiCraftingCPU;

// AE2's own tooltip draw method is not a Minecraft override, so its name
// remains stable across Forge runtime obfuscation and development builds.
@Mixin(targets = "appeng.client.gui.AEBaseGui$AEGuiTooltip", remap = false)
public abstract class MixinAEGuiTooltipXTProfile {

    @Inject(method = "draw", at = @At("HEAD"), require = 0)
    private void xtprofile$renderBeforeTooltip(CallbackInfo ci) {
        if (!XT9YConfig.xtProfile) return;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (!(screen instanceof GuiCraftingCPU) || mc.displayWidth <= 0 || mc.displayHeight <= 0) return;

        int mouseX = Mouse.getX() * screen.width / mc.displayWidth;
        int mouseY = screen.height - Mouse.getY() * screen.height / mc.displayHeight - 1;
        XTProfileClientOverlay.renderBeforeTooltip((AEBaseGui) screen, mouseX, mouseY);
    }
}
