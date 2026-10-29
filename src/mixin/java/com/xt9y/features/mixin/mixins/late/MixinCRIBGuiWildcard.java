package com.xt9y.features.mixin.mixins.late;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.xt9y.features.XT9YConfig;
import com.xt9y.features.api.IWildcardToggleable;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.hatch.MTEHatchCraftingInputMEGui;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

@Mixin(value = MTEHatchCraftingInputMEGui.class, remap = false)
public abstract class MixinCRIBGuiWildcard {

    @Inject(method = "createBottomLeftCornerFlow", at = @At("RETURN"), remap = false, cancellable = true)
    private void gt5u$appendWildcardButton(ModularPanel panel, PanelSyncManager syncManager,
        CallbackInfoReturnable<Flow> cir) {
        if (!XT9YConfig.cribWildcard) return;
        cir.setReturnValue(
            cir.getReturnValue()
                .child(createWildcardButton(syncManager)));
    }

    @Unique
    private ToggleButton createWildcardButton(PanelSyncManager syncManager) {
        MTEHatchCraftingInputME machine = (MTEHatchCraftingInputME) ((MixinMTETieredMachineBlockBaseGui) (Object) this)
            .getMachine();
        IWildcardToggleable toggleable = (IWildcardToggleable) machine;
        BooleanSyncValue wildcardSync = new BooleanSyncValue(() -> toggleable.isWildcardEnabled(), val -> {
            if (toggleable.isWildcardEnabled() == val) return;
            toggleable.setWildcardEnabled(val);
            EntityPlayer player = syncManager.getPlayer();
            if (player != null) {
                player.addChatMessage(
                    new ChatComponentTranslation(
                        "ggfab.wildcard.toggle",
                        val ? EnumChatFormatting.GREEN + "ON" + EnumChatFormatting.RESET
                            : EnumChatFormatting.RED + "OFF" + EnumChatFormatting.RESET));
            }
        }).allowC2S();

        String hint = StatCollector.translateToLocal("xt9yfeatures.tooltip.wildcard.mallet");
        return new ToggleButton().value(wildcardSync)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_BATCH_MODE_ON)
            .addTooltip(false, "Wildcard Expansion:\n§7Disabled\n§7" + hint)
            .addTooltip(true, "Wildcard Expansion:\n§7Enabled\n§7" + hint);
    }
}
