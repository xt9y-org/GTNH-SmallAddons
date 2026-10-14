package com.xt9y.features.mixin.mixins.late;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.api.IWildcardContainerState;
import com.xt9y.features.api.IWildcardToggleable;

import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerInterface;
import appeng.helpers.DualityInterface;

@Mixin(value = ContainerInterface.class, remap = false)
public abstract class MixinContainerInterfaceWildcard implements IWildcardContainerState {

    @Shadow
    @Final
    private DualityInterface myDuality;

    // AE2's GUI field synchronization sends this to the client when it changes.
    @Unique
    @GuiSync(50)
    public boolean xt9y$wildcardEnabled;

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"))
    private void xt9y$updateWildcardSync(CallbackInfo ci) {
        xt9y$wildcardEnabled = ((IWildcardToggleable) myDuality).isWildcardEnabled();
    }

    @Override
    public boolean xt9y$isWildcardEnabled() {
        return xt9y$wildcardEnabled;
    }
}
