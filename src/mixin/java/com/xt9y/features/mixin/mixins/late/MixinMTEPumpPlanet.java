package com.xt9y.features.mixin.mixins.late;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.features.PlanetaryCover;
import com.xt9y.features.VirtualExtraction;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.basic.MTEPump;

/**
 * Replace only the physical pumping phase. GT's normal superclass tick still handles
 * tank output, automation, and inventory; no foreign chunks or fluids are drained.
 */
@Mixin(value = MTEPump.class, remap = false)
public abstract class MixinMTEPumpPlanet {

    @Inject(
        method = "onPostTick",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEBasicMachine;onPostTick(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;J)V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private void xt9y$virtualPump(IGregTechTileEntity base, long tick, CallbackInfo ci) {
        PlanetaryCover cover = VirtualExtraction.cover(base);
        if (cover == null || cover.getSelectedDimension() == null) return;
        ci.cancel();

        MTEPump pump = (MTEPump) (Object) this;
        if (!base.isAllowedToWork()) {
            base.setActive(false);
            return;
        }
        if (pump.mPumpTimer-- > 0) return;

        int tier = (int) Math.max(0, Math.min(7, pump.getInputTier()));
        pump.mPumpTimer = Math.max(1, 160 >> Math.min(7, tier));
        FluidStack fluid = VirtualExtraction.nextFluid(cover, 1);
        if (fluid == null) {
            base.setActive(false);
            return;
        }

        fluid.amount = Math.min(1000, fluid.amount);
        int accepted = pump.getFluidOutputTank()
            .fill(fluid, false);
        if (accepted <= 0 || !base.decreaseStoredEnergyUnits(MTEPump.getEuUsagePerTier(tier), false)) {
            base.setActive(false);
            return;
        }

        fluid.amount = accepted;
        pump.getFluidOutputTank()
            .fill(fluid, true);
        base.setActive(true);
        base.markDirty();
    }
}
