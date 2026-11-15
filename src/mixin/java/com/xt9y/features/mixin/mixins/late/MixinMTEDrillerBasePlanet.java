package com.xt9y.features.mixin.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.VirtualExtraction;

import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.tileentities.machines.multi.MTEDrillerBase;
import gregtech.common.tileentities.machines.multi.MTEDrillerBase.WorkState;
import gregtech.common.tileentities.machines.multi.MTEOilDrillBase;

/** Virtual fluid rigs use normal multiblock EU/progress, without mining pipes or chunk loading. */
@Mixin(value = MTEDrillerBase.class, remap = false)
public abstract class MixinMTEDrillerBasePlanet {

    @Unique
    private boolean xt9y$wasVirtualFluid;

    @Inject(method = "checkProcessing", at = @At("HEAD"), remap = false)
    private void xt9y$selectFluidOperation(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!((Object) this instanceof MTEOilDrillBase)) return;
        MTEDrillerBase drill = (MTEDrillerBase) (Object) this;
        boolean enabled = VirtualExtraction.isVirtual(drill.getBaseMetaTileEntity());
        if (enabled && drill.getWorkState() != WorkState.ABORT) {
            drill.setWorkState(WorkState.AT_BOTTOM);
        } else if (!enabled && xt9y$wasVirtualFluid && drill.getWorkState() == WorkState.AT_BOTTOM) {
            drill.setWorkState(WorkState.DOWNWARD);
        }
        xt9y$wasVirtualFluid = enabled;
    }

    @Inject(method = "checkPipesAndSetYHead", at = @At("HEAD"), cancellable = true, remap = false)
    private void xt9y$skipPhysicalPipes(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof MTEOilDrillBase)) return;
        MTEDrillerBase drill = (MTEDrillerBase) (Object) this;
        if (VirtualExtraction.isVirtual(drill.getBaseMetaTileEntity())) cir.setReturnValue(true);
    }
}
