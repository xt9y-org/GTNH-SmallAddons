package com.xt9y.features.mixin.mixins.late;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.XT9YFeatures;
import com.xt9y.features.api.IWildcardContainerState;

import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;

@Mixin(value = GuiInterface.class, remap = false)
public abstract class MixinGuiInterfaceWildcard extends GuiUpgradeable {

    @Unique
    private GuiToggleButton xt9y$wildcardButton;

    protected MixinGuiInterfaceWildcard(ContainerUpgradeable container) {
        super(container);
    }

    @Inject(method = "addButtons", at = @At("TAIL"))
    private void xt9y$addWildcardButton(CallbackInfo ci) {
        // Reuse the standard left-side column. Find the first unoccupied row so
        // optional AE2/AE2FC controls can keep their existing positions.
        int x = guiLeft - 18;
        int y = guiTop + 98;
        for (; y < guiTop + 170; y += 18) {
            boolean occupied = false;
            for (Object entry : buttonList) {
                if (entry instanceof GuiButton button && button.visible
                    && button.xPosition < x + 16
                    && button.xPosition + button.width > x
                    && button.yPosition < y + 16
                    && button.yPosition + button.height > y) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) break;
        }
        xt9y$wildcardButton = new GuiToggleButton(
            x,
            y,
            178,
            194,
            StatCollector.translateToLocal("xt9yfeatures.gui.wildcard"),
            StatCollector.translateToLocal("xt9yfeatures.gui.wildcard.hint"));
        buttonList.add(xt9y$wildcardButton);
    }

    @Inject(method = "drawFG", at = @At("HEAD"))
    private void xt9y$drawWildcardState(int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        if (xt9y$wildcardButton != null) {
            xt9y$wildcardButton.setState(((IWildcardContainerState) cvb).xt9y$isWildcardEnabled());
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void xt9y$toggleWildcard(GuiButton button, CallbackInfo ci) {
        if (button != xt9y$wildcardButton) return;
        ci.cancel();
        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig("XT9Y.InterfaceWildcard", "toggle"));
        } catch (IOException e) {
            XT9YFeatures.LOGGER.error("Failed to send interface wildcard toggle", e);
        }
    }
}
