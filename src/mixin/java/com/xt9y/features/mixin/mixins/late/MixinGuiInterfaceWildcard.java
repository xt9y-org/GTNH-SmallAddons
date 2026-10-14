package com.xt9y.features.mixin.mixins.late;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;

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
        // Second left-side column avoids AE2FC's additional sideless-mode control.
        xt9y$wildcardButton = new GuiToggleButton(
            guiLeft - 36,
            guiTop + 170,
            178,
            194,
            "xt9yfeatures.gui.wildcard",
            "xt9yfeatures.gui.wildcard.hint");
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
