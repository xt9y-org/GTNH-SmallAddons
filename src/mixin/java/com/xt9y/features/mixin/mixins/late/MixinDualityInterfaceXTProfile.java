package com.xt9y.features.mixin.mixins.late;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.xtprofile.XTProfileDispatchContext;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;

@Mixin(value = DualityInterface.class, remap = false)
public abstract class MixinDualityInterfaceXTProfile {

    @Inject(method = "onPushPatternSuccess", at = @At("HEAD"))
    private void xtprofile$target(TileEntity target, ForgeDirection side, ICraftingPatternDetails pattern,
        CallbackInfo ci) {
        XTProfileDispatchContext.recordTarget(target, pattern);
    }
}
