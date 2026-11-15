package com.xt9y.features.mixin.mixins.late;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.xt9y.features.PlanetaryCover;
import com.xt9y.features.VirtualExtraction;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTEOilDrillBase;

/** Fluid drilling rigs use the planet's fluid deposits rather than draining real chunks. */
@Mixin(value = MTEOilDrillBase.class, remap = false)
public abstract class MixinMTEOilDrillPlanet {

    @Shadow
    protected abstract float computeSpeed();

    @Shadow
    protected abstract FluidStack adjustPumpedOil(FluidStack stack);

    @Inject(method = "workingAtBottom", at = @At("HEAD"), cancellable = true, remap = false)
    private void xt9y$fluidFromPlanet(ItemStack stack, int xDrill, int yDrill, int zDrill, int xPipe,
        int zPipe, int yHead, int oldYHead, CallbackInfoReturnable<Boolean> cir) {
        MTEOilDrillBase rig = (MTEOilDrillBase) (Object) this;
        PlanetaryCover cover = VirtualExtraction.cover(rig.getBaseMetaTileEntity());
        if (cover == null || cover.getSelectedDimension() == null) return;

        FluidStack fluid = VirtualExtraction.nextFluid(cover, computeSpeed());
        if (fluid == null) {
            cir.setReturnValue(false);
            return;
        }
        fluid = adjustPumpedOil(fluid);
        if (fluid == null || fluid.amount <= rig.getTotalConfigValue()
            || !((MixinGTMultiblockOutputAccess) (Object) this).xt9y$canOutputAllFluids(
                new FluidStack[] { fluid })) {
            cir.setReturnValue(false);
            return;
        }
        ((MTEMultiBlockBase) (Object) this).mOutputFluids = new FluidStack[] { fluid };
        cir.setReturnValue(true);
    }
}
