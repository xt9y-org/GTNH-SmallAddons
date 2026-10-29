package com.xt9y.features.mixin.mixins.late;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.XT9YConfig;
import com.xt9y.features.api.INonConsumablePatternTerminal;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.container.implementations.ContainerPatternTerm;

@Mixin(value = VirtualMESlot.class, remap = false)
public abstract class MixinVirtualMESlotNonConsumable {

    @Inject(method = "drawStackAndOverlay", at = @At("RETURN"))
    private void xt9y$drawNcMarker(Minecraft mc, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (!XT9YConfig.cribNonConsumable) return;
        if (!((Object) this instanceof VirtualMEPatternSlot slot)) return;
        if (slot.getStorageName() != StorageName.CRAFTING_INPUT) return;
        if (!(slot.getAEStack() instanceof IAEItemStack)) return;

        if (!(mc.thePlayer.openContainer instanceof ContainerPatternTerm container)) return;
        if (container.isCraftingMode()) return;
        if (!(container instanceof INonConsumablePatternTerminal terminal)) return;
        if (!terminal.xt9y$isNonConsumable(slot.getSlotIndex())) return;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glTranslatef(slot.getX(), slot.getY(), 300.0F);
        GL11.glScalef(0.5F, 0.5F, 1.0F);
        mc.fontRenderer.drawStringWithShadow("NC", 1, 1, 0xFFFF55);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }
}
