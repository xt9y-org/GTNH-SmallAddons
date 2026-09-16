package com.xt9y.features.mixin.mixins.late;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.xt9y.features.xtprofile.XTProfileLoadingScreenCompat;

@Pseudo
@Mixin(targets = "alexiil.mods.load.MinecraftDisplayer", remap = false)
public abstract class MixinBetterLoadingScreenXTProfile {

    @Redirect(
        method = "fontRenderer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_110436_a()V"),
        require = 0)
    private void xtprofile$suppressBackgroundRefresh(Minecraft minecraft) {
        if (XTProfileLoadingScreenCompat.shouldSuppressBackgroundResourceRefresh(
            Thread.currentThread()
                .getName()))
            return;
        minecraft.refreshResources();
    }
}
