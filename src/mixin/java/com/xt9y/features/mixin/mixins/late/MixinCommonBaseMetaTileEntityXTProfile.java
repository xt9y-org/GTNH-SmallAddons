package com.xt9y.features.mixin.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.xt9y.features.xtprofile.XTProfileTimingControl;

import gregtech.api.metatileentity.CommonBaseMetaTileEntity;

@Mixin(value = CommonBaseMetaTileEntity.class, remap = false)
public abstract class MixinCommonBaseMetaTileEntityXTProfile implements XTProfileTimingControl {

    @Shadow
    private boolean hasTimeStatisticsStarted;

    @Override
    public boolean xtprofile$isTimingEnabled() {
        return hasTimeStatisticsStarted;
    }

    @Override
    public void xtprofile$setTimingEnabled(boolean enabled) {
        hasTimeStatisticsStarted = enabled;
    }
}
