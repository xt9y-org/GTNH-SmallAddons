package com.xt9y.features.mixin.mixins.late;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.XT9YConfig;
import com.xt9y.features.api.INonConsumablePatternTerminal;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.container.implementations.ContainerPatternTerm;

@Mixin(value = VirtualMEPhantomSlot.class, remap = false)
public abstract class MixinVirtualMEPhantomSlotNonConsumable {

    @Inject(method = "handleMouseClicked", at = @At("HEAD"), cancellable = true)
    private void xt9y$toggleNonConsumable(ItemStack itemStack, boolean isExtraAction, int mouseButton,
        CallbackInfo ci) {
        if (!XT9YConfig.cribNonConsumable) return;
        if (mouseButton != 1 || !GuiScreen.isShiftKeyDown()) return;
        if (!((Object) this instanceof VirtualMEPatternSlot slot)) return;
        if (slot.getStorageName() != StorageName.CRAFTING_INPUT) return;
        if (!(slot.getAEStack() instanceof IAEItemStack)) return;

        if (!(Minecraft.getMinecraft().thePlayer.openContainer instanceof ContainerPatternTerm container)) return;
        if (container.isCraftingMode()) return;
        if (!(container instanceof INonConsumablePatternTerminal terminal)) return;

        terminal.xt9y$requestToggleNonConsumable(slot.getSlotIndex());
        ci.cancel();
    }

    @Inject(method = "addTooltip", at = @At("TAIL"))
    private void xt9y$addNonConsumableTooltip(List<String> lines, CallbackInfo ci) {
        if (!XT9YConfig.cribNonConsumable) return;
        if (!((Object) this instanceof VirtualMEPatternSlot slot)) return;
        if (slot.getStorageName() != StorageName.CRAFTING_INPUT) return;
        if (!(slot.getAEStack() instanceof IAEItemStack)) return;

        if (!(Minecraft.getMinecraft().thePlayer.openContainer instanceof ContainerPatternTerm container)) return;
        if (container.isCraftingMode()) return;
        if (!(container instanceof INonConsumablePatternTerminal terminal)) return;

        if (terminal.xt9y$isNonConsumable(slot.getSlotIndex())) {
            lines.add(EnumChatFormatting.GOLD + "NC - Non-Consumable Input");
        }
        lines.add(EnumChatFormatting.GRAY + "Shift + Right Click: Toggle NC");
    }
}
