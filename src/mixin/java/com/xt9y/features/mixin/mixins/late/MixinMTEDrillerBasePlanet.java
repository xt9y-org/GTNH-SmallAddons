package com.xt9y.features.mixin.mixins.late;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.PlanetaryCover;
import com.xt9y.features.VirtualExtraction;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.multi.MTEDrillerBase;
import gregtech.common.tileentities.machines.multi.MTEOilDrillBase;

/** Reuse the real rig's EU and progress calculation, but never scan or drain world chunks. */
@Mixin(value = MTEDrillerBase.class, remap = false)
public abstract class MixinMTEDrillerBasePlanet {

    @Shadow
    protected abstract void setElectricityStats();

    @Shadow
    protected abstract int getMinTier();

    @Shadow
    private boolean isEnergyEnough() {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(method = "checkProcessing", at = @At("HEAD"), cancellable = true, remap = false)
    private void xt9y$virtualFluidOperation(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!((Object) this instanceof MTEOilDrillBase)) return;
        MTEOilDrillBase rig = (MTEOilDrillBase) (Object) this;
        PlanetaryCover cover = VirtualExtraction.cover(rig.getBaseMetaTileEntity());
        if (cover == null || cover.getSelectedDimension() == null) return;

        setElectricityStats();
        if (!isEnergyEnough()) {
            cir.setReturnValue(SimpleCheckRecipeResult.ofFailure("not_enough_energy"));
            return;
        }
        int tier = GTUtility.getTier(rig.getMaxInputVoltage());
        float speed = Math.max(0.25F, 0.5F + (tier - getMinTier()) * 0.25F);
        FluidStack fluid = VirtualExtraction.nextFluid(cover, speed);
        if (fluid != null) fluid = ((MixinMTEOilDrillPlanet) (Object) rig).xt9y$adjustFluid(fluid);

        if (fluid == null || fluid.amount <= rig.getTotalConfigValue()) {
            cir.setReturnValue(SimpleCheckRecipeResult.ofFailure("void_miner_no_ores"));
            return;
        }
        if (!((MixinGTMultiblockOutputAccess) (Object) this).xt9y$canOutputAllFluids(new FluidStack[] { fluid })) {
            cir.setReturnValue(SimpleCheckRecipeResult.ofFailure("no_output_space"));
            return;
        }

        ((MTEMultiBlockBase) (Object) this).mOutputFluids = new FluidStack[] { fluid };
        cir.setReturnValue(SimpleCheckRecipeResult.ofSuccess("drill_extracting_ores"));
    }
}
